package org.school.personalLoad.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.HrDocumentDtos.*;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class HrDocumentService {
    private final EmploymentContractRepository contracts;
    private final HrPersonalDataRepository personalData;
    private final HrServiceMemoRepository memos;
    private final ServiceMemoRepository loadMemos;
    private final AdditionalAgreementRepository agreements;
    private final HrDocumentVersionRepository versions;
    private final HrCatalogItemRepository catalogItems;
    private final TeacherDirectoryRepository teachers;
    private final ManualLoadEntryRepository loadRepository;
    private final SalarySettingsRepository salarySettingsRepository;
    private final SubjectLevelCoefficientRepository coefficientRepository;
    private final SalaryGroupCoefficientSubjectRepository groupSubjectRepository;
    private final ClassSizeService classSizeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true) public List<JournalRow> journal(String academicYear) {
        Map<Long,List<AdditionalAgreement>> byContract = agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear)
                .stream().filter(a -> a.getContractId() != null).collect(Collectors.groupingBy(AdditionalAgreement::getContractId));
        return contracts.findAllByActiveTrueOrderByTeacherIdAsc().stream().map(c -> {
            TeacherDirectoryEntry teacher = teachers.findById(c.getTeacherId()).orElse(null);
            boolean complete = personalData.findByTeacherId(c.getTeacherId()).map(this::isComplete).orElse(false);
            List<AgreementView> docs = byContract.getOrDefault(c.getId(), List.of()).stream().map(this::view).toList();
            String action = !complete ? "Заполнить данные" : docs.stream().anyMatch(a -> a.status().equals("REQUIRES_DECISION"))
                    ? "Требуется решение" : docs.stream().anyMatch(a -> a.status().equals("WAITING_FOR_MEMO"))
                    ? "Ожидает служебку" : docs.isEmpty() ? "Создать черновик" : "";
            return new JournalRow(c.getTeacherId(), teacher == null ? "" : teacher.getFioTeacher(), c.getId(),
                    c.getContractNumber(), c.getPositionName(), complete, docs, action);
        }).sorted(Comparator.comparing(JournalRow::fio, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true) public List<AgreementListRow> agreementRows(String academicYear){
        return agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear).stream().map(agreement->{
            EmploymentContract contract=agreement.getContractId()==null?null:contracts.findById(agreement.getContractId()).orElse(null);
            Long teacherId=agreementTeacherId(agreement,contract);
            TeacherDirectoryEntry teacher=teacherId==null?null:teachers.findById(teacherId).orElse(null);
            return new AgreementListRow(teacherId,teacher==null?"":teacher.getFioTeacher(),
                    agreement.getContractId(),contract==null?"":contract.getContractNumber(),contract==null?"":contract.getPositionName(),view(agreement));
        }).toList();
    }

    @Transactional public EmploymentContract saveContract(Long id, ContractRequest r) {
        teachers.findById(r.teacherId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Педагог не найден"));
        EmploymentContract c = id == null ? new EmploymentContract() : contracts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        c.setTeacherId(r.teacherId()); c.setContractNumber(required(r.contractNumber(), "Номер договора"));
        c.setContractDate(Objects.requireNonNull(r.contractDate(), "Дата договора обязательна"));
        c.setPositionName(required(r.positionName(), "Должность")); c.setStartDate(r.startDate()); c.setEndDate(r.endDate());
        c.setPrimaryContract(r.primaryContract() == null || r.primaryContract()); c.setActive(r.active() == null || r.active());
        EmploymentContract saved = contracts.save(c);
        if (saved.getId() != null && saved.isActive() && saved.isPrimaryContract()) attachWaitingMemos(saved);
        return saved;
    }

    public List<EmploymentContract> contracts(Long teacherId) { return contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacherId); }
    public Optional<HrPersonalData> personal(Long teacherId) { return personalData.findByTeacherId(teacherId); }

    @Transactional public HrPersonalData savePersonal(PersonalDataRequest r, String username) {
        teachers.findById(r.teacherId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Педагог не найден"));
        HrPersonalData p = personalData.findByTeacherId(r.teacherId()).orElseGet(HrPersonalData::new);
        if (p.getId() != null) p.setRevision(p.getRevision() + 1);
        p.setTeacherId(r.teacherId()); p.setBirthDate(r.birthDate()); p.setPassportSeries(r.passportSeries());
        p.setPassportNumber(r.passportNumber()); p.setPassportIssuedBy(r.passportIssuedBy()); p.setPassportIssueDate(r.passportIssueDate());
        p.setPassportDepartmentCode(r.passportDepartmentCode()); p.setRegistrationAddress(r.registrationAddress());
        p.setActualAddress(r.actualAddress()); p.setPhone(r.phone()); p.setInn(r.inn()); p.setSnils(r.snils());
        p.setUpdatedAt(LocalDateTime.now()); p.setUpdatedBy(username); return personalData.save(p);
    }

    @Transactional public HrServiceMemo createMemo(MemoRequest r, String username) {
        if (r.teacherId() == null) throw new ResponseStatusException(BAD_REQUEST, "Выберите работника");
        TeacherDirectoryEntry teacher = teachers.findById(r.teacherId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Педагог не найден"));
        EmploymentContract contract = r.contractId() == null ? null : contracts.findById(r.contractId())
                .filter(c -> Objects.equals(c.getTeacherId(), teacher.getId()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Трудовой договор не принадлежит выбранному педагогу"));
        HrCatalogItem catalog = r.catalogItemId() == null ? null : catalogItems.findById(r.catalogItemId())
                .filter(item -> item.isActive() && Objects.equals(item.getSchoolCode(), SchoolCodeResolver.resolve()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Элемент справочника недоступен"));
        boolean separate = Boolean.TRUE.equals(r.separateAgreement());
        String assignmentName = firstPresent(r.assignmentName(), catalog == null ? null : catalog.getName());
        String duties = firstPresent(r.dutiesText(), catalog == null ? null : catalog.getDutiesText());
        if (separate && !present(duties)) throw new ResponseStatusException(BAD_REQUEST, "Укажите дополнительные обязанности");
        BigDecimal amount = r.amount() != null ? r.amount() : catalog == null ? null : catalog.getDefaultAmount();
        if (r.validFrom() == null || r.validTo() == null) throw new ResponseStatusException(BAD_REQUEST, "Укажите период действия");
        String clause = firstPresent(r.contractClause(), catalog == null ? null : catalog.getContractClause(), "2.4");
        String assignmentText = firstPresent(r.assignmentText(), catalog == null ? null : catalog.getMemoText(),
                automaticMemoText(teacher, assignmentName, amount, r.validFrom(), r.validTo(), separate));
        String agreementText = firstPresent(r.agreementText(), catalog == null ? null : catalog.getAgreementText(),
                automaticAgreementText(assignmentName, duties, clause, separate));
        if (Boolean.TRUE.equals(r.saveAsTemplate()) && r.catalogItemId() == null) {
            HrCatalogItem saved = new HrCatalogItem(); saved.setSchoolCode(SchoolCodeResolver.resolve()); saved.setName(required(assignmentName, "Название основания"));
            saved.setCategory(separate ? HrCatalogItem.Category.ADDITIONAL_WORK : HrCatalogItem.Category.COMPENSATION);
            saved.setContractClause(clause); saved.setDefaultAmount(amount); saved.setMemoText(assignmentText);
            saved.setAgreementText(agreementText); saved.setDutiesText(duties); saved.setSeparateAgreement(separate); catalog = catalogItems.save(saved);
        }
        HrServiceMemo m = new HrServiceMemo(); m.setAcademicYear(required(r.academicYear(), "Учебный год"));
        m.setTeacherId(teacher.getId()); m.setContractId(contract == null ? null : contract.getId()); m.setCatalogItemId(catalog == null ? null : catalog.getId());
        m.setTitle(firstPresent(r.title(), assignmentName)); m.setDocumentDate(r.documentDate());
        m.setAssignmentName(required(assignmentName, "Основание или обязанность")); m.setAssignmentText(assignmentText);
        m.setAgreementText(agreementText); m.setContractClause(clause); m.setDutiesText(duties);
        m.setAmount(amount); m.setValidFrom(r.validFrom()); m.setValidTo(r.validTo()); m.setSeparateAgreement(separate);
        m.setItemsJson(json(Map.of("teacherId", teacher.getId(), "fio", teacher.getFioTeacher(), "assignment", assignmentName,
                "contractClause", clause, "separateAgreement", separate, "amount", amount == null ? "" : amount.toPlainString())));
        m.setCreatedBy(username); m.setDocumentFilename("Служебная_записка_" + teacher.getFioTeacher().replace(' ', '_') + ".docx");
        m.setDocumentContent(generateMemo(m)); m = memos.save(m);
        ensureDutyAgreement(m, contract, username);
        return m;
    }
    @Transactional public List<MemoView> memos(String year) {
        List<HrServiceMemo> result=memos.findAllByAcademicYearOrderByCreatedAtDesc(year);
        result.stream().filter(memo->memo.getStatus()!=HrServiceMemo.Status.ANNULLED)
                .filter(memo->agreements.findAllByServiceMemoId(memo.getId()).isEmpty())
                .forEach(memo->ensureDutyAgreement(memo,primaryContract(memo.getTeacherId()),firstPresent(memo.getCreatedBy(),"system")));
        return result.stream().map(this::memoView).toList();
    }

    @Transactional(readOnly = true)
    public MemoView memoView(HrServiceMemo memo) {
        List<AdditionalAgreement> linked=memo.getId()==null?List.of():agreements.findAllByServiceMemoId(memo.getId());
        boolean deletable=memo.getStatus()==HrServiceMemo.Status.ANNULLED
                || (!linked.isEmpty()&&linked.stream().allMatch(a->a.getStatus()==AdditionalAgreement.Status.REJECTED));
        return new MemoView(memo.getId(),memo.getAcademicYear(),memo.getTeacherId(),memo.getContractId(),memo.getCatalogItemId(),
                memo.getTitle(),memo.getDocumentDate(),memo.getAssignmentName(),memo.getAmount(),memo.getValidFrom(),memo.getValidTo(),
                memo.isSeparateAgreement(),memo.getStatus().name(),memo.getCreatedAt(),memo.getCreatedBy(),deletable);
    }

    @Transactional(readOnly = true) public List<HrCatalogItem> catalog() {
        return catalogItems.findAllBySchoolCodeAndActiveTrueOrderByName(SchoolCodeResolver.resolve());
    }

    @Transactional public HrServiceMemo memoStatus(Long id, HrServiceMemo.Status status, String username) {
        return memoStatus(id,status,username,username,"сотрудника");
    }

    @Transactional public HrServiceMemo memoStatus(Long id, HrServiceMemo.Status status, String username, String fullName, String position) {
        HrServiceMemo m = memo(id);
        if (m.getStatus() == HrServiceMemo.Status.ANNULLED) throw new ResponseStatusException(CONFLICT, "Служебка аннулирована");
        if (status == HrServiceMemo.Status.ISSUED && m.getStatus() != HrServiceMemo.Status.DRAFT)
            throw new ResponseStatusException(CONFLICT,"Выпустить можно только черновик служебной записки");
        if (status == HrServiceMemo.Status.RECEIVED_BY_HR && m.getStatus() != HrServiceMemo.Status.ISSUED
                && m.getStatus() != HrServiceMemo.Status.RECEIVED_BY_HR)
            throw new ResponseStatusException(CONFLICT,"Сначала выпустите служебную записку");
        m.setStatus(status);
        if (status == HrServiceMemo.Status.ISSUED) {
            m.setIssuedAt(LocalDateTime.now()); m.setIssuedBy(username);
            m.setIssuedByFullName(firstPresent(fullName,username)); m.setIssuedByPosition(firstPresent(position,"сотрудника"));
            m.setDocumentContent(generateMemo(m));
        }
        if (status == HrServiceMemo.Status.RECEIVED_BY_HR) {
            m.setReceivedAt(LocalDateTime.now()); m.setReceivedBy(username);
            ensureDutyAgreement(m,primaryContract(m.getTeacherId()),username);
            releaseWaiting(agreements.findAllByServiceMemoId(id));
        }
        return memos.save(m);
    }
    @Transactional public HrServiceMemo annulMemo(Long id, String reason, String username) {
        HrServiceMemo m = memo(id); m.setStatus(HrServiceMemo.Status.ANNULLED); m.setAnnulReason(required(reason,"Причина"));
        m.setAnnulledAt(LocalDateTime.now()); m.setAnnulledBy(username); memos.save(m);
        agreements.findAllByServiceMemoId(id).stream()
                .filter(a -> a.getStatus() != AdditionalAgreement.Status.ANNULLED)
                .forEach(a -> { a.setStatus(AdditionalAgreement.Status.REQUIRES_DECISION); agreements.save(a); });
        return m;
    }

    @Transactional public void deleteAnnulledMemo(Long id, String username) {
        HrServiceMemo memo=memo(id);
        List<AdditionalAgreement> linked=agreements.findAllByServiceMemoId(id);
        boolean allRejected=!linked.isEmpty()&&linked.stream().allMatch(a->a.getStatus()==AdditionalAgreement.Status.REJECTED);
        if(memo.getStatus()!=HrServiceMemo.Status.ANNULLED&&!allRejected)
            throw new ResponseStatusException(CONFLICT,"Удалить можно аннулированную служебную записку либо служебку с отклонённым допсоглашением");
        boolean hasIssuedAgreement=linked.stream().anyMatch(a->!EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION,
                AdditionalAgreement.Status.REJECTED,AdditionalAgreement.Status.ANNULLED).contains(a.getStatus()));
        if(hasIssuedAgreement)throw new ResponseStatusException(CONFLICT,"Нельзя удалить служебную записку: по ней уже оформлено дополнительное соглашение");
        linked.forEach(a->{
            a.setServiceMemoId(null);
            if(a.getStatus()!=AdditionalAgreement.Status.REJECTED){
                a.setStatus(AdditionalAgreement.Status.ANNULLED);
                if(a.getAnnulledAt()==null){a.setAnnulledAt(LocalDateTime.now());a.setAnnulledBy(username);a.setAnnulReason("Удалена аннулированная служебная записка");}
            }
            agreements.save(a);
        });
        memos.delete(memo);
    }

    @Transactional public void deleteAnnulledLoadMemo(Long id,String username){
        ServiceMemo memo=loadMemos.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Служебная записка не найдена"));
        if(memo.getStatus()!=ServiceMemo.Status.ANNULLED)
            throw new ResponseStatusException(CONFLICT,"Удалить можно только аннулированную служебную записку");
        List<AdditionalAgreement> linked=agreements.findAllByLoadServiceMemoId(id);
        boolean hasIssuedAgreement=linked.stream().anyMatch(a->!EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION,
                AdditionalAgreement.Status.REJECTED,AdditionalAgreement.Status.ANNULLED).contains(a.getStatus()));
        if(hasIssuedAgreement)throw new ResponseStatusException(CONFLICT,"Нельзя удалить служебную записку: по ней уже оформлено дополнительное соглашение");
        linked.forEach(a->{
            a.setLoadServiceMemoId(null);
            if(a.getStatus()!=AdditionalAgreement.Status.REJECTED){
                a.setStatus(AdditionalAgreement.Status.ANNULLED);
                if(a.getAnnulledAt()==null){a.setAnnulledAt(LocalDateTime.now());a.setAnnulledBy(username);a.setAnnulReason("Удалена аннулированная служебная записка по нагрузке");}
            }
            agreements.save(a);
        });
        loadMemos.delete(memo);
    }

    @Transactional public void deleteCatalogItem(Long id) {
        HrCatalogItem item=catalogItems.findById(id).filter(x->Objects.equals(x.getSchoolCode(),SchoolCodeResolver.resolve()))
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Позиция справочника не найдена"));
        item.setActive(false); item.setUpdatedAt(LocalDateTime.now()); catalogItems.save(item);
    }

    @Transactional public AdditionalAgreement createAgreement(AgreementRequest r, String username) {
        if(r.contractId()==null)throw new ResponseStatusException(BAD_REQUEST,"Выберите трудовой договор");
        EmploymentContract c = contracts.findById(r.contractId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND,"Договор не найден"));
        return createAgreementForTeacher(r,c.getTeacherId(),c,username);
    }

    private AdditionalAgreement createAgreementForTeacher(AgreementRequest r,Long teacherId,EmploymentContract c,String username) {
        teachers.findById(teacherId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND,"Работник не найден"));
        AdditionalAgreement old = r.replacesAgreementId() == null ? null : agreement(r.replacesAgreementId());
        AdditionalAgreement a = new AdditionalAgreement(); a.setTeacherId(teacherId); a.setContractId(c==null?null:c.getId()); a.setServiceMemoId(r.serviceMemoId());
        a.setAcademicYear(required(r.academicYear(),"Учебный год")); a.setDocumentDate(r.documentDate());
        a.setValidFrom(Objects.requireNonNull(r.validFrom(),"Начало периода обязательно"));
        a.setValidTo(Objects.requireNonNull(r.validTo(),"Конец периода обязателен"));
        a.setKind(r.kind() == null ? AdditionalAgreement.Kind.PAY_TERMS : r.kind());
        a.setChangeMode(r.changeMode() == null ? AdditionalAgreement.ChangeMode.AMEND : r.changeMode());
        a.setSummary(r.summary()); a.setConditionsJson(r.conditionsJson()); a.setTotalAmount(r.totalAmount()); a.setCreatedBy(username);
        if (a.getKind() == AdditionalAgreement.Kind.PAY_TERMS && a.getTotalAmount() == null) {
            BigDecimal calculated = loadDetails(teacherId, a.getAcademicYear(), a.getValidFrom()).stream()
                    .map(LoadDetail::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            if (calculated.signum() > 0) a.setTotalAmount(calculated);
        }
        if (old != null && old.getStatus() == AdditionalAgreement.Status.ANNULLED) {
            a.setInternalNumber(old.getInternalNumber()); a.setVisibleNumber(old.getVisibleNumber()); a.setRevision(old.getRevision() + 1); a.setReplacesAgreementId(old.getId());
        } else {
            a.setInternalNumber(c==null?temporaryNumber(teacherId,r.academicYear()):nextNumber(c, r.academicYear()));
            if (c!=null&&"1811".equals(SchoolCodeResolver.resolve())) a.setVisibleNumber(a.getInternalNumber());
        }
        HrPersonalData snapshotData = personalData.findByTeacherId(teacherId).orElse(null);
        if (snapshotData != null) a.setPersonalDataSnapshotJson(json(snapshotData));
        Map<String,Object> source=new LinkedHashMap<>();source.put("teacherId",teacherId);source.put("contractId",c==null?null:c.getId());source.put("createdAt",LocalDateTime.now().toString());
        a.setSourceSnapshotJson(json(source));
        byte[] doc = generateAgreement(a, c); a.setGeneratedDocument(doc); a.setCurrentDocument(doc);
        a.setCurrentFilename("Дополнительное_соглашение_" + a.getInternalNumber().replace('/','-') + ".docx");
        a = agreements.save(a); saveVersion(a, doc, a.getCurrentFilename(), "GENERATED", username); return a;
    }

    @Transactional public AdditionalAgreement createLoadChangeDraft(ServiceMemo memo, EmploymentContract contract, String username) {
        int startYear = Integer.parseInt(memo.getAcademicYear().substring(0, 4));
        AdditionalAgreement agreement = createAgreementForTeacher(new AgreementRequest(contract==null?null:contract.getId(), null, memo.getAcademicYear(),
                memo.getCreatedAt() == null ? LocalDate.now() : memo.getCreatedAt().toLocalDate(), memo.getChangeStartDate(),
                LocalDate.of(startYear + 1, 8, 31), AdditionalAgreement.Kind.PAY_TERMS,
                AdditionalAgreement.ChangeMode.AMEND, "Изменение учебной нагрузки с " + memo.getChangeStartDate(),
                "Изложить условия оплаты труда и пункт 2.1 в новой редакции согласно актуальной нагрузке и Приложению № 1.",
                null, null),memo.getTeacherId(),contract,username);
        agreement.setLoadServiceMemoId(memo.getId());
        agreement.setStatus(AdditionalAgreement.Status.WAITING_FOR_MEMO);
        return agreements.save(agreement);
    }

    private AdditionalAgreement ensureDutyAgreement(HrServiceMemo memo, EmploymentContract contract, String username) {
        return agreements.findAllByServiceMemoId(memo.getId()).stream().findFirst().orElseGet(() -> {
            AdditionalAgreement agreement = createAgreementForTeacher(new AgreementRequest(contract==null?null:contract.getId(), memo.getId(), memo.getAcademicYear(),
                    memo.getDocumentDate(), memo.getValidFrom(), memo.getValidTo(),
                    memo.isSeparateAgreement() ? AdditionalAgreement.Kind.ADDITIONAL_WORK : AdditionalAgreement.Kind.PAY_TERMS,
                    AdditionalAgreement.ChangeMode.AMEND, memo.getAssignmentName(), memo.getAgreementText(), memo.getAmount(), null),memo.getTeacherId(),contract,username);
            boolean memoReceived = memo.getStatus() == HrServiceMemo.Status.RECEIVED_BY_HR || memo.getStatus() == HrServiceMemo.Status.EXECUTED;
            agreement.setStatus(memoReceived ? AdditionalAgreement.Status.DRAFT : AdditionalAgreement.Status.WAITING_FOR_MEMO);
            return agreements.save(agreement);
        });
    }

    private void attachWaitingMemos(EmploymentContract contract) {
        agreements.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(contract.getTeacherId()).forEach(agreement->{
            agreement.setContractId(contract.getId());
            if(agreement.getInternalNumber()!=null&&agreement.getInternalNumber().startsWith("БД-")){
                agreement.setInternalNumber(nextNumber(contract,agreement.getAcademicYear()));
                agreement.setVisibleNumber("1811".equals(SchoolCodeResolver.resolve())?agreement.getInternalNumber():null);
            }
            byte[] doc=generateAgreement(agreement,contract);agreement.setGeneratedDocument(doc);agreement.setCurrentDocument(doc);
            agreement.setRevision(agreement.getRevision()+1);
            agreement.setCurrentFilename("Дополнительное_соглашение_"+agreement.getInternalNumber().replace('/','-')+".docx");
            AdditionalAgreement saved=agreements.save(agreement);saveVersion(saved,doc,saved.getCurrentFilename(),"CONTRACT_ATTACHED",firstPresent(saved.getCreatedBy(),"system"));
        });
        memos.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(contract.getTeacherId()).stream()
                .filter(memo -> memo.getStatus() != HrServiceMemo.Status.ANNULLED)
                .forEach(memo -> {
                    memo.setContractId(contract.getId()); memos.save(memo);
                    ensureDutyAgreement(memo, contract, firstPresent(memo.getCreatedBy(), "system"));
                });
        loadMemos.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(contract.getTeacherId()).stream()
                .filter(memo -> memo.getStatus() != ServiceMemo.Status.ANNULLED && memo.getStatus() != ServiceMemo.Status.ARCHIVED)
                .forEach(memo -> {
                    memo.setContractId(contract.getId()); loadMemos.save(memo);
                    ensureLoadChangeDraft(memo, contract, firstPresent(memo.getCreatedBy(), "system"));
                    if (memo.getStatus() == ServiceMemo.Status.RECEIVED_BY_HR || memo.getStatus() == ServiceMemo.Status.EXECUTED)
                        onLoadMemoReceived(memo);
                });
    }

    @Transactional public AdditionalAgreement ensureLoadChangeDraft(ServiceMemo memo, EmploymentContract contract, String username) {
        return agreements.findAllByLoadServiceMemoId(memo.getId()).stream().findFirst()
                .orElseGet(() -> createLoadChangeDraft(memo, contract, username));
    }

    @Transactional public void onLoadMemoReceived(ServiceMemo memo) {
        releaseWaiting(agreements.findAllByLoadServiceMemoId(memo.getId()));
    }

    @Transactional public void onLoadMemoAnnulled(ServiceMemo memo) {
        agreements.findAllByLoadServiceMemoId(memo.getId()).stream()
                .filter(a -> a.getStatus() != AdditionalAgreement.Status.ANNULLED)
                .forEach(a -> { a.setStatus(AdditionalAgreement.Status.REQUIRES_DECISION); agreements.save(a); });
    }

    @Transactional public List<AdditionalAgreement> createAnnualDrafts(BatchAgreementRequest r, String username) {
        int startYear=Integer.parseInt(r.academicYear().substring(0,4));LocalDate from=r.validFrom()==null?firstWorkingDay(LocalDate.of(startYear,9,1)):r.validFrom();LocalDate to=LocalDate.of(startYear+1,8,31);
        Set<Long> requestedContracts=r.contractIds()==null?Set.of():new HashSet<>(r.contractIds());
        Set<Long> requestedTeachers=requestedContracts.stream().map(contracts::findById).flatMap(Optional::stream).map(EmploymentContract::getTeacherId).collect(Collectors.toSet());
        Set<Long> teacherIds=loadRepository.findAllByAcademicYear(r.academicYear()).stream().map(ManualLoadEntry::getTeacherId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if(!requestedTeachers.isEmpty())teacherIds.retainAll(requestedTeachers);
        List<AdditionalAgreement> existing=agreements.findAllByAcademicYearOrderByCreatedAtDesc(r.academicYear());List<AdditionalAgreement> result=new ArrayList<>();
        for(Long teacherId:teacherIds){
            EmploymentContract contract=primaryContract(teacherId);
            boolean alreadyExists=existing.stream().anyMatch(a->Objects.equals(agreementTeacherId(a,null),teacherId)
                    &&a.getKind()==AdditionalAgreement.Kind.PAY_TERMS&&a.getStatus()!=AdditionalAgreement.Status.ANNULLED
                    &&Objects.equals(a.getValidFrom(),from)&&Objects.equals(a.getSummary(),"Нагрузка и должностной оклад"));
            if(alreadyExists)continue;
            AdditionalAgreement created=createAgreementForTeacher(new AgreementRequest(contract==null?null:contract.getId(),r.serviceMemoId(),r.academicYear(),r.documentDate(),from,to,AdditionalAgreement.Kind.PAY_TERMS,AdditionalAgreement.ChangeMode.AMEND,"Нагрузка и должностной оклад","Изложить пункт 2.1 раздела «Оплата труда» в новой редакции согласно Приложению № 1.",null,null),teacherId,contract,username);
            result.add(created);existing.add(created);
        }return result;
    }
    private LocalDate firstWorkingDay(LocalDate d){while(d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY)d=d.plusDays(1);return d;}

    @Transactional public AdditionalAgreement issue(Long id, String username) {
        AdditionalAgreement a = agreement(id);
        if(a.getContractId()==null)throw new ResponseStatusException(CONFLICT,"Заполните действующий трудовой договор работника перед выпуском допсоглашения");
        EmploymentContract c = contracts.findById(a.getContractId()).orElseThrow(()->new ResponseStatusException(CONFLICT,"Трудовой договор допсоглашения не найден"));
        if (a.getStatus() == AdditionalAgreement.Status.ISSUED || a.getStatus() == AdditionalAgreement.Status.SIGNING || a.getStatus() == AdditionalAgreement.Status.SIGNED) return a;
        if (a.getStatus() == AdditionalAgreement.Status.WAITING_FOR_MEMO)
            throw new ResponseStatusException(CONFLICT,"Дополнительное соглашение ожидает получения служебной записки");
        if (a.getStatus() != AdditionalAgreement.Status.DRAFT && a.getStatus() != AdditionalAgreement.Status.READY)
            throw new ResponseStatusException(CONFLICT,"Документ нельзя выпустить в текущем статусе");
        if (!personalData.findByTeacherId(c.getTeacherId()).map(this::isComplete).orElse(false))
            throw new ResponseStatusException(CONFLICT,"Заполните обязательные персональные данные");
        if (a.getServiceMemoId() != null && memo(a.getServiceMemoId()).getStatus() != HrServiceMemo.Status.RECEIVED_BY_HR
                && memo(a.getServiceMemoId()).getStatus() != HrServiceMemo.Status.EXECUTED)
            throw new ResponseStatusException(CONFLICT,"Служебная записка не получена кадрами");
        if (a.getLoadServiceMemoId() != null) {
            ServiceMemo loadMemo = loadMemos.findById(a.getLoadServiceMemoId())
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT,"Служебная записка по нагрузке не найдена"));
            if (loadMemo.getStatus() != ServiceMemo.Status.RECEIVED_BY_HR && loadMemo.getStatus() != ServiceMemo.Status.EXECUTED)
                throw new ResponseStatusException(CONFLICT,"Служебная записка по нагрузке не получена кадрами");
        }
        a.setStatus(AdditionalAgreement.Status.ISSUED); a.setIssuedAt(LocalDateTime.now()); a.setIssuedBy(username); return agreements.save(a);
    }

    @Transactional public AdditionalAgreement upload(Long id, String filename, byte[] content, String username) {
        AdditionalAgreement a = agreement(id); if (content == null || content.length == 0) throw new ResponseStatusException(BAD_REQUEST,"Пустой файл");
        a.setRevision(a.getRevision() + 1); a.setCurrentDocument(content); a.setCurrentFilename(filename); agreements.save(a);
        saveVersion(a, content, filename, "UPLOADED", username); return a;
    }
    @Transactional public AdditionalAgreement annulAgreement(Long id, String reason, String username) {
        AdditionalAgreement a = agreement(id); a.setStatus(AdditionalAgreement.Status.ANNULLED); a.setAnnulReason(required(reason,"Причина"));
        a.setAnnulledAt(LocalDateTime.now()); a.setAnnulledBy(username); return agreements.save(a);
    }
    @Transactional public AdditionalAgreement agreementStatus(Long id, AdditionalAgreement.Status status) {
        AdditionalAgreement a=agreement(id); if(a.getStatus()==AdditionalAgreement.Status.ANNULLED) throw new ResponseStatusException(CONFLICT,"Документ аннулирован");
        if(status==AdditionalAgreement.Status.REJECTED&&EnumSet.of(AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING,AdditionalAgreement.Status.SIGNED).contains(a.getStatus()))
            throw new ResponseStatusException(CONFLICT,"Выпущенное допсоглашение нужно аннулировать, а не отклонять");
        if(a.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO && status!=AdditionalAgreement.Status.REQUIRES_DECISION&&status!=AdditionalAgreement.Status.REJECTED)
            throw new ResponseStatusException(CONFLICT,"Сначала отметьте получение служебной записки кадрами");
        if(status==AdditionalAgreement.Status.SIGNED && a.getStatus()!=AdditionalAgreement.Status.ISSUED && a.getStatus()!=AdditionalAgreement.Status.SIGNING)
            throw new ResponseStatusException(CONFLICT,"Сначала выпустите дополнительное соглашение");
        a.setStatus(status); return agreements.save(a);
    }
    @Transactional public AdditionalAgreement chooseChangeMode(Long id, AdditionalAgreement.ChangeMode mode, Long replacesId, String username) {
        AdditionalAgreement a = agreement(id);
        if (a.getStatus() == AdditionalAgreement.Status.ISSUED || a.getStatus() == AdditionalAgreement.Status.SIGNED || a.getStatus() == AdditionalAgreement.Status.ANNULLED)
            throw new ResponseStatusException(CONFLICT,"Способ изменения можно выбрать только до выпуска документа");
        AdditionalAgreement previous = replacesId == null ? null : agreement(replacesId);
        if (previous != null && (!Objects.equals(agreementTeacherId(previous,null),agreementTeacherId(a,null)) || Objects.equals(previous.getId(),a.getId())))
            throw new ResponseStatusException(BAD_REQUEST,"Выбран документ по другому трудовому договору");
        if (mode == AdditionalAgreement.ChangeMode.CANCEL_AND_RESTATE && previous == null)
            throw new ResponseStatusException(BAD_REQUEST,"Выберите отменяемое дополнительное соглашение");
        a.setChangeMode(mode == null ? AdditionalAgreement.ChangeMode.AMEND : mode);
        a.setReplacesAgreementId(previous == null ? null : previous.getId());
        EmploymentContract contract = a.getContractId()==null?null:contracts.findById(a.getContractId()).orElse(null);
        byte[] doc = generateAgreement(a, contract); a.setGeneratedDocument(doc); a.setCurrentDocument(doc);
        a = agreements.save(a); saveVersion(a,doc,a.getCurrentFilename(),"REGENERATED",username); return a;
    }
    @Transactional(readOnly = true) public AdditionalAgreement agreement(Long id) { return agreements.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    @Transactional(readOnly = true) public HrServiceMemo memo(Long id) { return memos.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    @Transactional(readOnly = true) public List<HrDocumentVersion> versions(Long id) { return versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",id); }

    private void saveVersion(AdditionalAgreement a, byte[] content, String filename, String source, String user) {
        HrDocumentVersion v=new HrDocumentVersion(); v.setDocumentType("AGREEMENT"); v.setDocumentId(a.getId()); v.setRevision(a.getRevision());
        v.setContent(content); v.setFilename(filename); v.setSource(source); v.setCreatedBy(user); versions.save(v);
    }
    private void releaseWaiting(List<AdditionalAgreement> linked) {
        linked.stream().filter(a -> a.getStatus() == AdditionalAgreement.Status.WAITING_FOR_MEMO)
                .forEach(a -> { a.setStatus(AdditionalAgreement.Status.DRAFT); agreements.save(a); });
    }
    private String nextNumber(EmploymentContract c, String year) {
        long n = agreements.countByContractIdAndAcademicYear(c.getId(), year) + 1;
        return "7".equals(SchoolCodeResolver.resolve()) ? n + " / " + year.replace('/','-') : String.valueOf(n);
    }
    private String temporaryNumber(Long teacherId,String year){
        long n=agreements.countByTeacherIdAndAcademicYear(teacherId,year)+1;
        return "БД-"+teacherId+"-"+n;
    }
    private EmploymentContract primaryContract(Long teacherId){
        if(teacherId==null)return null;
        return contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacherId).stream()
                .filter(EmploymentContract::isPrimaryContract).filter(EmploymentContract::isActive).findFirst().orElse(null);
    }
    private Long agreementTeacherId(AdditionalAgreement agreement,EmploymentContract knownContract){
        if(agreement.getTeacherId()!=null)return agreement.getTeacherId();
        if(knownContract!=null)return knownContract.getTeacherId();
        return agreement.getContractId()==null?null:contracts.findById(agreement.getContractId()).map(EmploymentContract::getTeacherId).orElse(null);
    }
    private AgreementView view(AdditionalAgreement a) { return new AgreementView(a.getId(),a.getInternalNumber(),a.getVisibleNumber(),a.getRevision(),a.getKind().name(),a.getStatus().name(),a.getChangeMode().name(),a.getReplacesAgreementId(),a.getDocumentDate(),a.getValidFrom(),a.getValidTo(),a.getSummary(),a.getTotalAmount(),a.getServiceMemoId(),a.getLoadServiceMemoId(),a.getIssuedAt()); }
    private boolean isComplete(HrPersonalData p) { return present(p.getPassportSeries()) && present(p.getPassportNumber()) && present(p.getPassportIssuedBy()) && p.getPassportIssueDate()!=null && present(p.getPassportDepartmentCode()) && present(p.getRegistrationAddress()); }
    private boolean present(String s){return s!=null&&!s.isBlank();} private String required(String s,String name){if(!present(s))throw new ResponseStatusException(BAD_REQUEST,name+" обязательно");return s.trim();}
    private String json(Object x){try{return objectMapper.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}

    private byte[] generateMemo(HrServiceMemo m) {
        String school=SchoolCodeResolver.resolve();
        String templatePath="templates/hr-service-memo/memo-"+(present(school)?school:"demo")+".docx";
        InputStream resource=getClass().getClassLoader().getResourceAsStream(templatePath);
        if(resource==null)resource=getClass().getClassLoader().getResourceAsStream("templates/hr-service-memo/memo-demo.docx");
        if(resource==null)return generateMemoFallback(m);
        try(InputStream template=resource;XWPFDocument d=new XWPFDocument(template);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            MemoRecipient recipient=memoRecipient();
            String body=memoBody(m);
            LocalDate date=m.getDocumentDate()!=null?m.getDocumentDate():m.getIssuedAt()==null?null:m.getIssuedAt().toLocalDate();
            Map<String,String> replacements=new LinkedHashMap<>();
            replacements.put("{RECIPIENT_TITLE}",recipient.title()); replacements.put("{RECIPIENT_NAME}",recipient.name());
            replacements.put("{AUTHOR_POSITION}",firstPresent(m.getIssuedByPosition(),"__________________"));
            replacements.put("{AUTHOR_NAME}",firstPresent(m.getIssuedByFullName(),"__________________"));
            replacements.put("{MEMO_TEXT}",body); replacements.put("{DOCUMENT_DATE}",formatDate(date));
            allDocumentParagraphs(d).forEach(p->replaceMemoTemplateParagraph(p,replacements));
            d.write(out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("Не удалось сформировать служебную записку по шаблону "+templatePath,e);}
    }

    private byte[] generateMemoFallback(HrServiceMemo m) {
        try(XWPFDocument d=new XWPFDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            MemoRecipient recipient=memoRecipient();
            memoParagraph(d,recipient.title(),ParagraphAlignment.RIGHT);memoParagraph(d,recipient.name(),ParagraphAlignment.RIGHT);
            memoParagraph(d,"от "+firstPresent(m.getIssuedByPosition(),"__________________"),ParagraphAlignment.RIGHT);
            memoParagraph(d,firstPresent(m.getIssuedByFullName(),"__________________"),ParagraphAlignment.RIGHT);
            memoParagraph(d,"служебная записка.",ParagraphAlignment.CENTER);
            String body=memoBody(m);
            memoParagraph(d,body,ParagraphAlignment.BOTH);
            LocalDate date=m.getDocumentDate()!=null?m.getDocumentDate():m.getIssuedAt()==null?null:m.getIssuedAt().toLocalDate();
            memoParagraph(d,formatDate(date)+"\t\t\t\t"+firstPresent(m.getIssuedByFullName(),"__________________"),ParagraphAlignment.LEFT);
            d.write(out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private List<XWPFParagraph> allDocumentParagraphs(XWPFDocument document){
        List<XWPFParagraph> result=new ArrayList<>(document.getParagraphs());
        document.getTables().forEach(table->table.getRows().forEach(row->row.getTableCells().forEach(cell->result.addAll(cell.getParagraphs()))));
        document.getHeaderList().forEach(header->result.addAll(header.getParagraphs()));
        document.getFooterList().forEach(footer->result.addAll(footer.getParagraphs()));
        return result;
    }

    private void replaceMemoTemplateParagraph(XWPFParagraph paragraph,Map<String,String> replacements){
        String original=paragraph.getText();if(original==null||original.isEmpty())return;
        String replaced=original;for(Map.Entry<String,String> entry:replacements.entrySet())replaced=replaced.replace(entry.getKey(),entry.getValue());
        if(Objects.equals(original,replaced))return;
        XWPFRun source=paragraph.getRuns().isEmpty()?null:paragraph.getRuns().get(0);
        String font=source==null?"Times New Roman":firstPresent(source.getFontFamily(),"Times New Roman");
        int size=source==null||source.getFontSize()<=0?14:source.getFontSize();boolean bold=source!=null&&source.isBold();boolean italic=source!=null&&source.isItalic();
        while(!paragraph.getRuns().isEmpty())paragraph.removeRun(0);
        XWPFRun run=paragraph.createRun();run.setFontFamily(font);run.setFontSize(size);run.setBold(bold);run.setItalic(italic);appendRunText(run,replaced);
    }

    private void appendRunText(XWPFRun run,String value){
        StringBuilder text=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);if(c!='\n'&&c!='\t'){text.append(c);continue;}
            if(text.length()>0){run.setText(text.toString());text.setLength(0);}if(c=='\n')run.addBreak();else run.addTab();
        }
        if(text.length()>0)run.setText(text.toString());
    }

    private void memoParagraph(XWPFDocument document,String value,ParagraphAlignment alignment){
        XWPFParagraph paragraph=document.createParagraph();paragraph.setAlignment(alignment);XWPFRun run=paragraph.createRun();
        run.setFontFamily("Times New Roman");run.setFontSize(14);appendRunText(run,Objects.toString(value,""));
    }

    private String memoBody(HrServiceMemo memo){
        String body=Objects.toString(memo.getAssignmentText(),"").replaceAll("\\R+"," ").trim();
        if(memo.isSeparateAgreement()&&present(memo.getDutiesText())){
            String duties=memo.getDutiesText().trim().replaceAll("\\s*\\R+\\s*","; ").replaceAll("[;.]?\\s*$","");
            body=body+" Дополнительные обязанности: "+duties+".";
        }
        return body;
    }

    private MemoRecipient memoRecipient(){
        return switch(SchoolCodeResolver.resolve()){
            case "1811" -> new MemoRecipient("Директору ГБОУ Школы №1811 «Восточное Измайлово»","Тихонову В.А.");
            case "7" -> new MemoRecipient("Директору ГБОУ Школы №7","Ждановой И.Д.");
            default -> new MemoRecipient("Директору образовательной организации","");
        };
    }

    private record MemoRecipient(String title,String name){}
    private byte[] generateAgreement(AdditionalAgreement a, EmploymentContract c) {
        Long teacherId=agreementTeacherId(a,c);
        TeacherDirectoryEntry t=teachers.findById(teacherId).orElseThrow(); HrPersonalData p=personalData.findByTeacherId(teacherId).orElse(null);
        try(XWPFDocument d=new XWPFDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            String number=a.getVisibleNumber()==null?"": " № "+a.getVisibleNumber(); title(d,"ДОПОЛНИТЕЛЬНОЕ СОГЛАШЕНИЕ"+number);
            paragraph(d,"к трудовому договору № "+(c==null?"________________":c.getContractNumber())+" от "+(c==null?"____________":c.getContractDate()),true);
            paragraph(d,"г. Москва                                      "+Objects.toString(a.getDocumentDate(),""),false);
            paragraph(d,employerIntro()+" и "+t.getFioTeacher()+", занимающий(ая) должность «"+(c==null?"________________":c.getPositionName())+"», заключили настоящее дополнительное соглашение.",false);
            if(a.getReplacesAgreementId()!=null) {
                AdditionalAgreement previous=agreement(a.getReplacesAgreementId());
                String reference="дополнительное соглашение "+(present(previous.getVisibleNumber())?"№ "+previous.getVisibleNumber()+" ":"")+"от "+Objects.toString(previous.getDocumentDate(),"даты выпуска");
                paragraph(d,a.getChangeMode()==AdditionalAgreement.ChangeMode.CANCEL_AND_RESTATE
                        ? "Признать утратившим силу с "+a.getValidFrom()+" "+reference+" и считать условия действующими в настоящей редакции."
                        : "Внести с "+a.getValidFrom()+" изменения в "+reference+".",false);
            }
            paragraph(d,Objects.toString(a.getConditionsJson(),a.getSummary()),false);
            if(a.getTotalAmount()!=null) paragraph(d,"Ежемесячная сумма: "+money(a.getTotalAmount())+" ("+moneyWords(a.getTotalAmount())+").",false);
            paragraph(d,"Срок действия: с "+a.getValidFrom()+" по "+a.getValidTo()+".",false);
            if (a.getKind() == AdditionalAgreement.Kind.PAY_TERMS) appendLoadAnnex(d, teacherId, a.getAcademicYear(), a.getValidFrom());
            paragraph(d,"Реквизиты работника: паспорт "+(p==null?"":Objects.toString(p.getPassportSeries(),"")+" "+Objects.toString(p.getPassportNumber(),""))+
                    ", выдан "+(p==null?"":Objects.toString(p.getPassportIssuedBy(),""))+", адрес регистрации: "+(p==null?"":Objects.toString(p.getRegistrationAddress(),"")),false);
            paragraph(d,employerDetails(),false); paragraph(d,"Работодатель ____________________     Работник ____________________",false); d.write(out); return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void title(XWPFDocument d,String s){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);XWPFRun r=p.createRun();r.setBold(true);r.setFontFamily("Times New Roman");r.setFontSize(14);r.setText(s);}
    private void paragraph(XWPFDocument d,String s,boolean bold){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.BOTH);XWPFRun r=p.createRun();r.setBold(bold);r.setFontFamily("Times New Roman");r.setFontSize(12);r.setText(Objects.toString(s,""));}

    private String automaticMemoText(TeacherDirectoryEntry teacher, String assignmentName, BigDecimal amount,
                                     LocalDate from, LocalDate to, boolean separate) {
        String period = " с " + formatDate(from) + " по " + formatDate(to);
        String payment = amount == null ? "" : " и установить ежемесячную доплату в размере "
                + money(amount) + " (" + moneyWords(amount) + ")";
        if (separate) {
            return "Прошу Вас согласовать поручение работнику " + teacher.getFioTeacher()
                    + " дополнительной работы «" + required(assignmentName, "Основание или обязанность") + "»"
                    + payment + period + ".";
        }
        if (amount != null) {
            return "Прошу Вас согласовать работнику " + teacher.getFioTeacher()
                    + " ежемесячную доплату в размере " + money(amount) + " (" + moneyWords(amount) + ")"
                    + " за увеличение объема работ («" + required(assignmentName, "Основание или обязанность") + "»)"
                    + period + ".";
        }
        return "Прошу Вас согласовать назначение работника " + teacher.getFioTeacher()
                + " для выполнения работы «" + required(assignmentName, "Основание или обязанность") + "»"
                + period + ".";
    }

    private String automaticAgreementText(String assignmentName, String duties, String clause, boolean separate) {
        if (separate) {
            return "Работнику поручается выполнение дополнительной работы «" + assignmentName
                    + "» без освобождения от основной работы. Дополнительные обязанности: " + firstPresent(duties);
        }
        return "Изложить пункт " + clause + " трудового договора в части выплаты за увеличение объема работ («"
                + assignmentName + "») в новой редакции.";
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private void appendLoadAnnex(XWPFDocument d, Long teacherId, String year, LocalDate date) {
        List<LoadDetail> rows = loadDetails(teacherId, year, date);
        if (rows.isEmpty()) return;
        title(d, "Приложение № 1"); paragraph(d, "Расчёт должностного оклада по педагогической нагрузке", true);
        XWPFTable table=d.createTable(1,8); String[] headers={"Предмет","Класс/группа","Часы","Численность","Ученико-час","Предм. коэф.","Коэф. группы","Сумма"};
        for(int i=0;i<headers.length;i++) cell(table.getRow(0).getCell(i),headers[i],true);
        BigDecimal total=BigDecimal.ZERO;
        for(LoadDetail x:rows){XWPFTableRow r=table.createRow();String[] v={x.subject(),x.className(),String.valueOf(x.hours()),String.valueOf(x.children()),x.rate().toPlainString(),x.subjectCoefficient().toPlainString(),x.groupCoefficient().setScale(4,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),x.amount().setScale(2,RoundingMode.HALF_UP).toPlainString()};for(int i=0;i<v.length;i++)cell(r.getCell(i),v[i],false);total=total.add(x.amount());}
        XWPFTableRow tr=table.createRow();cell(tr.getCell(0),"Итого",true);for(int i=1;i<7;i++)cell(tr.getCell(i),"",false);cell(tr.getCell(7),total.setScale(2,RoundingMode.HALF_UP).toPlainString(),true);
    }
    private void cell(XWPFTableCell c,String s,boolean bold){c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);XWPFParagraph p=c.getParagraphs().get(0);p.setAlignment(ParagraphAlignment.CENTER);XWPFRun r=p.createRun();r.setFontFamily("Times New Roman");r.setFontSize(9);r.setBold(bold);r.setText(s);}
    private List<LoadDetail> loadDetails(Long teacherId,String year,LocalDate date){
        Map<String,Integer> sizes=classSizeService.effectiveClassSizes(year);BigDecimal rate=salarySettingsRepository.findById(SalarySettings.DEFAULT_ID).map(SalarySettings::getStudentHourRate).orElse(SalarySettings.DEFAULT_STUDENT_HOUR_RATE);
        BigDecimal multiplier=BigDecimal.valueOf(34).divide(BigDecimal.valueOf(12),10,RoundingMode.HALF_UP);List<LoadDetail> result=new ArrayList<>();
        for(ManualLoadEntry row:loadRepository.findAllByAcademicYear(year)){if(!Objects.equals(row.getTeacherId(),teacherId)||row.getStudyPeriod()==StudyPeriod.H2)continue;if(row.getLoadFromDate()!=null&&row.getLoadFromDate().isAfter(date))continue;if(row.getLoadToDate()!=null&&row.getLoadToDate().isBefore(date))continue;
            int classSize=sizes.getOrDefault(normalizeClass(row.getClassName()),sizes.getOrDefault(row.getClassName(),0));String group=Objects.toString(row.getGroupNameEducationalPlan(),"").toLowerCase(Locale.ROOT);int first=(classSize+1)/2,second=classSize-first,children=group.contains("2")?second:group.contains("1")?first:classSize;children=Math.max(children,1);int hours=Optional.ofNullable(row.getGroupLoad()).orElse(Optional.ofNullable(row.getLoad()).orElse(0));
            EducationStage stage=stage(row.getClassName());BigDecimal subjectCoefficient=coefficientRepository.findBySubjectNameIgnoreCaseAndEducationStage(row.getSubjectName(),stage).map(SubjectLevelCoefficientEntry::getCoefficient).orElse(BigDecimal.ONE);boolean groupEnabled=row.getSubjectId()!=null?groupSubjectRepository.findBySubjectId(row.getSubjectId()).isPresent():groupSubjectRepository.findBySubjectNameIgnoreCase(row.getSubjectName()).isPresent();BigDecimal groupCoefficient=!group.isBlank()&&groupEnabled?BigDecimal.valueOf(25).divide(BigDecimal.valueOf(children),10,RoundingMode.HALF_UP):BigDecimal.ONE;
            BigDecimal base=rate.multiply(BigDecimal.valueOf(children)).multiply(BigDecimal.valueOf(hours)).multiply(multiplier);BigDecimal amount=base.add(base.multiply(subjectCoefficient.subtract(BigDecimal.ONE))).add(base.multiply(groupCoefficient.subtract(BigDecimal.ONE)));result.add(new LoadDetail(row.getSubjectName(),row.getClassName()+(!group.isBlank()?" "+row.getGroupNameEducationalPlan():""),hours,children,rate,subjectCoefficient,groupCoefficient,amount));}
        return result;
    }
    private String normalizeClass(String s){return Objects.toString(s,"").trim().toUpperCase(Locale.ROOT).replaceAll("\\s+","");}
    private EducationStage stage(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+").matcher(Objects.toString(s,""));if(!m.find())return EducationStage.OOO;int g=Integer.parseInt(m.group());return g<=4?EducationStage.NOO:g<=9?EducationStage.OOO:EducationStage.SOO;}
    private record LoadDetail(String subject,String className,int hours,int children,BigDecimal rate,BigDecimal subjectCoefficient,BigDecimal groupCoefficient,BigDecimal amount){}
    private String money(BigDecimal v){
        BigDecimal value=v.setScale(2,RoundingMode.HALF_UP);String[] parts=value.abs().toPlainString().split("\\.");
        String grouped=parts[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))"," ");
        return (value.signum()<0?"-":"")+grouped+","+parts[1]+" руб.";
    }
    private String firstPresent(String... values){for(String value:values)if(present(value))return value.trim();return "";}
    private String employerIntro(){return "1811".equals(SchoolCodeResolver.resolve())?"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 1811 «Восточное Измайлово», именуемое «Работодатель», в лице директора Тихонова Валерия Анатольевича":"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 7», именуемое «Работодатель», в лице директора Ждановой Ирины Дмитриевны";}
    private String employerDetails(){return "1811".equals(SchoolCodeResolver.resolve())?"Работодатель: ГБОУ Школа № 1811 «Восточное Измайлово», 105203, г. Москва, ул. Первомайская, д. 109/2, ИНН/КПП 7719894832/771901001":"Работодатель: ГБОУ Школа № 7, 119331, г. Москва, ул. Крупской, д. 17, ИНН 7736050780";}
    private String moneyWords(BigDecimal v){BigDecimal x=v.setScale(2,RoundingMode.HALF_UP); long rub=x.longValue(); int kop=x.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue(); return numberWords(rub)+" "+form(rub,"рубль","рубля","рублей")+" "+String.format("%02d",kop)+" "+form(kop,"копейка","копейки","копеек");}
    private String form(long n,String a,String b,String c){long m=Math.abs(n)%100;if(m>=11&&m<=14)return c;return switch((int)(Math.abs(n)%10)){case 1->a;case 2,3,4->b;default->c;};}
    private String numberWords(long n){if(n==0)return "ноль"; if(n<0)return "минус "+numberWords(-n); String[] ones={"","один","два","три","четыре","пять","шесть","семь","восемь","девять","десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать"};String[] tens={"","","двадцать","тридцать","сорок","пятьдесят","шестьдесят","семьдесят","восемьдесят","девяносто"};String[] hundreds={"","сто","двести","триста","четыреста","пятьсот","шестьсот","семьсот","восемьсот","девятьсот"};StringBuilder s=new StringBuilder();long[] div={1000000000,1000000,1000,1};String[][] forms={{"миллиард","миллиарда","миллиардов"},{"миллион","миллиона","миллионов"},{"тысяча","тысячи","тысяч"},{"","",""}};for(int i=0;i<div.length;i++){int g=(int)((n/div[i])%1000);if(g==0)continue;if(s.length()>0)s.append(' ');s.append(hundreds[g/100]);int r=g%100;if(r>0){if(s.length()>0&&s.charAt(s.length()-1)!=' ')s.append(' ');if(r<20)s.append(ones[r]);else{s.append(tens[r/10]);if(r%10>0)s.append(' ').append(i==2&&r%10==1?"одна":i==2&&r%10==2?"две":ones[r%10]);}}if(i<3)s.append(' ').append(form(g,forms[i][0],forms[i][1],forms[i][2]));}return s.toString().trim();}
}
