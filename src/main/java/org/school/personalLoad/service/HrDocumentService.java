package org.school.personalLoad.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.school.personalLoad.config.SchoolCodeResolver;
import org.school.personalLoad.dto.HrDocumentDtos.*;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.impl.ClassNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class HrDocumentService {
    private static final String ANNUAL_LOAD_SUMMARY = "Нагрузка и должностной оклад";
    private static final String CLASSROOM_SUMMARY_SUFFIX = " · Классное руководство";
    private static final String INCENTIVE_SUMMARY_SUFFIX = " · Стимулирующая выплата";
    private static final Set<String> STANDARD_CONTRACT_CLAUSES = Set.of("2.1","2.4","2.5");
    private static final String ANNUAL_LOAD_PLACEHOLDER =
            "Изложить пункт 2.1 раздела «Оплата труда» в новой редакции согласно Приложению № 1.";
    private static final String LOAD_CHANGE_PLACEHOLDER =
            "Изложить условия оплаты труда и пункт 2.1 в новой редакции согласно актуальной нагрузке и Приложению № 1.";
    private static final String LEGACY_LOAD_PLACEHOLDER =
            "Изложить пункт 2.1 раздела «Оплата труда» в новой редакции.";
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
    private final ClassroomLeadershipRepository classroomLeadershipRepository;
    private final ClassSizeService classSizeService;
    private final LoadSalaryCalculationService loadSalaryCalculationService;
    private final HrIncentiveRepository incentiveRepository;
    private final LoadInRateRuleRepository loadInRateRuleRepository;
    private final ObjectMapper objectMapper;

    @Transactional public List<JournalRow> journal(String academicYear) {
        List<AdditionalAgreement> allAgreements=agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear);
        repairMisappliedRegistryIncentives(allAgreements);
        repairMisappliedRegistryFunctions(allAgreements);
        Map<Long,List<AdditionalAgreement>> byContract = allAgreements.stream().filter(a -> a.getContractId() != null)
                .collect(Collectors.groupingBy(AdditionalAgreement::getContractId));
        return contracts.findAllByActiveTrueOrderByTeacherIdAsc().stream().map(c -> {
            TeacherDirectoryEntry teacher = teachers.findById(c.getTeacherId()).orElse(null);
            boolean complete = personalData.findByTeacherId(c.getTeacherId()).map(this::isComplete).orElse(false);
            List<AgreementView> docs = byContract.getOrDefault(c.getId(), List.of()).stream()
                    .filter(this::isActiveAgreement).map(this::view).toList();
            String action = !complete ? "Заполнить данные" : docs.stream().anyMatch(a -> a.status().equals("REQUIRES_DECISION"))
                    ? "Требуется решение" : docs.stream().anyMatch(AgreementView::reissueRequired)
                    ? "Перевыпустить" : docs.stream().anyMatch(a -> a.status().equals("WAITING_FOR_MEMO"))
                    ? "Ожидает служебку" : docs.isEmpty() ? "Создать черновик" : "";
            return new JournalRow(c.getTeacherId(), teacher == null ? "" : teacher.getFioTeacher(), c.getId(),
                    c.getContractNumber(), c.getPositionName(), complete, docs, action);
        }).sorted(Comparator.comparing(JournalRow::fio, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional public List<AgreementListRow> agreementRows(String academicYear){
        List<AdditionalAgreement> allAgreements=agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear);
        repairMisappliedRegistryIncentives(allAgreements);
        repairMisappliedRegistryFunctions(allAgreements);
        return allAgreements.stream().map(agreement->{
            EmploymentContract contract=agreement.getContractId()==null?null:contracts.findById(agreement.getContractId()).orElse(null);
            Long teacherId=agreementTeacherId(agreement,contract);
            TeacherDirectoryEntry teacher=teacherId==null?null:teachers.findById(teacherId).orElse(null);
            boolean complete=teacherId!=null&&personalData.findByTeacherId(teacherId).map(this::isComplete).orElse(false);
            return new AgreementListRow(teacherId,teacher==null?"":teacher.getFioTeacher(),
                    agreement.getContractId(),contract==null?"":contract.getContractNumber(),contract==null?"":contract.getPositionName(),complete,view(agreement));
        }).toList();
    }

    @Transactional public EmploymentContract saveContract(Long id, ContractRequest r) {
        teachers.findById(r.teacherId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Педагог не найден"));
        EmploymentContract c = id == null ? new EmploymentContract() : contracts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        boolean previouslyIncluded=c.isLoadHoursMayBeIncludedInRate();
        c.setTeacherId(r.teacherId()); c.setContractNumber(required(r.contractNumber(), "Номер договора"));
        c.setContractDate(Objects.requireNonNull(r.contractDate(), "Дата договора обязательна"));
        c.setPositionName(required(r.positionName(), "Должность")); c.setStartDate(r.startDate()); c.setEndDate(r.endDate());
        c.setPrimaryContract(r.primaryContract() == null || r.primaryContract()); c.setActive(r.active() == null || r.active());
        LoadInRateRule inRateRule=ruleForPosition(c.getPositionName(),r.loadInRateRuleId());
        c.setLoadHoursMayBeIncludedInRate(inRateRule!=null);
        c.setLoadInRateRuleId(inRateRule==null?null:inRateRule.getId());
        c.setLoadInRateDocumentLabel(null);
        EmploymentContract saved = contracts.save(c);
        if(previouslyIncluded&&!saved.isLoadHoursMayBeIncludedInRate()){
            Map<String,Set<Long>> affected=new LinkedHashMap<>();
            List<ManualLoadEntry> rows=loadRepository.findByTeacherId(saved.getTeacherId()).stream()
                    .filter(row->Objects.equals(row.getEmploymentContractId(),saved.getId())).toList();
            rows.forEach(row->{
                row.setIncludedInRateHours(BigDecimal.ZERO);row.setInRateAllocationConfirmed(false);
                row.setInRateReason(null);row.setInRateUpdatedAt(LocalDateTime.now());
                affected.computeIfAbsent(row.getAcademicYear(),key->new LinkedHashSet<>()).add(row.getTeacherId());
            });
            loadRepository.saveAll(rows);
            affected.forEach(this::markAnnualLoadAgreementsChanged);
        }
        if (saved.getId() != null && saved.isActive() && saved.isPrimaryContract()) attachWaitingMemos(saved);
        return saved;
    }

    public List<EmploymentContract> contracts(Long teacherId) { return contracts.findAllByTeacherIdOrderByPrimaryContractDescContractDateDesc(teacherId); }
    @Transactional(readOnly = true)
    public List<ContractView> contractViews(Long teacherId) {
        return contracts(teacherId).stream().map(this::contractView).toList();
    }
    public ContractView contractView(EmploymentContract contract) {
        LoadInRateRule rule=ruleForPosition(contract.getPositionName(),contract.getLoadInRateRuleId());
        return new ContractView(contract.getId(), contract.getTeacherId(), contract.getContractNumber(),
                contract.getContractDate(), contract.getPositionName(), contract.getStartDate(), contract.getEndDate(),
                contract.isPrimaryContract(), contract.isActive(), rule!=null,
                rule==null?null:rule.getId(), null, contract.getCreatedAt());
    }
    public Optional<HrPersonalData> personal(Long teacherId) { return personalData.findByTeacherId(teacherId); }
    @Transactional(readOnly = true)
    public Optional<PersonalDataView> personalView(Long teacherId) {
        return personal(teacherId).map(this::personalView);
    }
    public PersonalDataView personalView(HrPersonalData data) {
        return new PersonalDataView(data.getTeacherId(), data.getBirthDate(), data.getPassportSeries(),
                data.getPassportNumber(), data.getPassportIssuedBy(), data.getPassportIssueDate(),
                data.getPassportDepartmentCode(), data.getRegistrationAddress(), data.getActualAddress(),
                data.getPhone(), data.getInn(), data.getSnils(), isComplete(data), data.getRevision(),
                data.getUpdatedAt(), data.getUpdatedBy());
    }
    @Transactional(readOnly = true)
    public List<PersonalDataRow> personalRows() {
        return teachers.findAll().stream().filter(teacher -> !teacher.isArchived())
                .sorted(Comparator.comparing(TeacherDirectoryEntry::getFioTeacher, String.CASE_INSENSITIVE_ORDER))
                .map(teacher -> new PersonalDataRow(teacher.getId(), teacher.getFioTeacher(),
                        personalData.findByTeacherId(teacher.getId()).map(this::personalView).orElse(null),
                        contracts(teacher.getId()).stream().map(this::contractView).toList()))
                .toList();
    }

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

    @Transactional
    public List<IncentiveRow> incentiveRows(String academicYear, String username) {
        String year=required(academicYear,"Учебный год");
        Set<Long> loadTeacherIds=loadTeacherIds(year);
        Map<Long,HrIncentive> stored=incentiveRepository.findAllByAcademicYear(year).stream()
                .collect(Collectors.toMap(HrIncentive::getTeacherId,item->item,(left,right)->left,LinkedHashMap::new));
        for(Long teacherId:loadTeacherIds){
            TeacherDirectoryEntry teacher=teachers.findById(teacherId).orElse(null);
            if(teacher==null||teacher.isArchived()||stored.containsKey(teacherId))continue;
            HrIncentive incentive=new HrIncentive();incentive.setAcademicYear(year);incentive.setTeacherId(teacherId);
            incentive.setAmount(BigDecimal.ZERO);incentive.setUpdatedAt(LocalDateTime.now());
            incentive.setUpdatedBy(firstPresent(username,"AUTO_LOAD"));stored.put(teacherId,incentiveRepository.save(incentive));
        }
        return stored.values().stream().map(incentive->{
            TeacherDirectoryEntry teacher=teachers.findById(incentive.getTeacherId()).orElse(null);
            return new IncentiveRow(incentive.getId(),incentive.getTeacherId(),
                    teacher==null||teacher.isArchived()?"":teacher.getFioTeacher(),normalizedAmount(incentive.getAmount()),
                    loadTeacherIds.contains(incentive.getTeacherId()),incentive.getUpdatedAt(),incentive.getUpdatedBy());
        }).filter(row->!row.fio().isBlank())
                .sorted(Comparator.comparing(IncentiveRow::fio,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional
    public IncentiveRow saveIncentive(String academicYear, Long teacherId, BigDecimal amount, String username) {
        String year=required(academicYear,"Учебный год");
        TeacherDirectoryEntry teacher=teachers.findById(Objects.requireNonNull(teacherId,"Выберите работника"))
                .filter(item->!item.isArchived())
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Работник не найден"));
        BigDecimal value=normalizedAmount(amount);
        if(value.signum()<0)throw new ResponseStatusException(BAD_REQUEST,"Стимулирующая выплата не может быть отрицательной");
        HrIncentive incentive=incentiveRepository.findByAcademicYearAndTeacherId(year,teacherId).orElseGet(HrIncentive::new);
        incentive.setAcademicYear(year);incentive.setTeacherId(teacherId);incentive.setAmount(value);
        incentive.setUpdatedAt(LocalDateTime.now());incentive.setUpdatedBy(username);
        HrIncentive saved=incentiveRepository.save(incentive);
        syncAnnualIncentiveDrafts(year,teacherId,value);
        return new IncentiveRow(saved.getId(),teacherId,teacher.getFioTeacher(),value,
                loadTeacherIds(year).contains(teacherId),saved.getUpdatedAt(),saved.getUpdatedBy());
    }

    @Transactional
    public byte[] exportIncentives(String academicYear, String username) throws IOException {
        List<IncentiveRow> rows=incentiveRows(academicYear,username);
        try(Workbook workbook=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sheet=workbook.createSheet("Стимул");
            String[] headers={"№","ID педагога","ФИО","Стимул, руб."};
            Row header=sheet.createRow(0);
            for(int i=0;i<headers.length;i++)header.createCell(i).setCellValue(headers[i]);
            CellStyle money=workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            for(int i=0;i<rows.size();i++){
                IncentiveRow source=rows.get(i);Row row=sheet.createRow(i+1);
                row.createCell(0).setCellValue(i+1);row.createCell(1).setCellValue(source.teacherId());
                row.createCell(2).setCellValue(source.fio());
                Cell amountCell=row.createCell(3);amountCell.setCellValue(source.amount().doubleValue());amountCell.setCellStyle(money);
            }
            sheet.setColumnWidth(0,8*256);sheet.setColumnWidth(1,16*256);
            sheet.setColumnWidth(2,42*256);sheet.setColumnWidth(3,18*256);
            sheet.createFreezePane(0,1);workbook.write(out);return out.toByteArray();
        }
    }

    @Transactional
    public IncentiveImportResult importIncentives(String academicYear, MultipartFile file, String username) throws IOException {
        if(file==null||file.isEmpty())throw new ResponseStatusException(BAD_REQUEST,"Выберите Excel-файл");
        int updated=0,skipped=0;
        try(Workbook workbook=WorkbookFactory.create(file.getInputStream())){
            if(workbook.getNumberOfSheets()==0)throw new ResponseStatusException(BAD_REQUEST,"В Excel-файле нет листов");
            Sheet sheet=workbook.getSheetAt(0);
            for(int index=1;index<=sheet.getLastRowNum();index++){
                Row row=sheet.getRow(index);if(row==null)continue;
                Long teacherId=excelLong(row.getCell(1));BigDecimal amount=excelAmount(row.getCell(3));
                if(teacherId==null||amount==null||teachers.findById(teacherId).filter(item->!item.isArchived()).isEmpty()){
                    skipped++;continue;
                }
                saveIncentive(academicYear,teacherId,amount,username);updated++;
            }
        }catch(ResponseStatusException error){throw error;}
        catch(Exception error){throw new ResponseStatusException(BAD_REQUEST,"Не удалось прочитать Excel-файл: "+error.getMessage(),error);}
        return new IncentiveImportResult(updated,skipped);
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
        String academicYear=required(r.academicYear(),"Учебный год");
        String clause = separate ? null
                : standardContractClause(r.contractClause(),catalog == null ? null : catalog.getContractClause());
        List<CompensationFunction> compensationFunctions = compensationFunctions(
                academicYear, teacher.getId(), clause, separate, assignmentName, amount, r.validFrom());
        boolean automaticClause24=!separate&&"2.4".equals(clause);
        String exactClause24=automaticClause24?clause24Text(compensationFunctions):null;
        String assignmentText = automaticClause24?exactClause24
                :firstPresent(r.assignmentText(), catalog == null ? null : catalog.getMemoText(),
                automaticMemoText(teacher, assignmentName, amount, r.validFrom(), r.validTo(), separate, clause,
                        compensationFunctions));
        String agreementText = automaticClause24?exactClause24
                :firstPresent(r.agreementText(), catalog == null ? null : catalog.getAgreementText(),
                automaticAgreementText(assignmentName, duties, clause, separate, amount, compensationFunctions));
        if (Boolean.TRUE.equals(r.saveAsTemplate()) && r.catalogItemId() == null) {
            HrCatalogItem saved = new HrCatalogItem(); saved.setSchoolCode(SchoolCodeResolver.resolve()); saved.setName(required(assignmentName, "Название основания"));
            saved.setCategory(separate ? HrCatalogItem.Category.ADDITIONAL_WORK : HrCatalogItem.Category.COMPENSATION);
            saved.setContractClause(clause); saved.setDefaultAmount(amount);
            saved.setMemoText(automaticClause24?null:present(r.assignmentText()) ? r.assignmentText().trim() : null);
            saved.setAgreementText(automaticClause24?null:present(r.agreementText()) ? r.agreementText().trim() : null);
            saved.setDutiesText(duties); saved.setSeparateAgreement(separate); catalog = catalogItems.save(saved);
        }
        HrServiceMemo m = new HrServiceMemo(); m.setAcademicYear(academicYear);
        m.setTeacherId(teacher.getId()); m.setContractId(contract == null ? null : contract.getId()); m.setCatalogItemId(catalog == null ? null : catalog.getId());
        m.setTitle(firstPresent(r.title(), assignmentName)); m.setDocumentDate(r.documentDate());
        m.setAssignmentName(required(assignmentName, "Основание или обязанность")); m.setAssignmentText(assignmentText);
        m.setAgreementText(agreementText); m.setContractClause(clause); m.setDutiesText(duties);
        m.setAmount(amount); m.setValidFrom(r.validFrom()); m.setValidTo(r.validTo()); m.setSeparateAgreement(separate);
        m.setItemsJson(json(Map.of("teacherId", teacher.getId(), "fio", teacher.getFioTeacher(), "assignment", assignmentName,
                "contractClause", Objects.toString(clause,""), "separateAgreement", separate,
                "amount", amount == null ? "" : amount.toPlainString())));
        m.setCreatedBy(username); m.setDocumentFilename("Служебная_записка_" + teacher.getFioTeacher().replace(' ', '_') + ".docx");
        m.setDocumentContent(generateMemo(m)); m = memos.save(m);
        ensureDutyAgreement(m, contract, username);
        return m;
    }

    @Transactional public HrServiceMemo editMemo(Long id,MemoRequest r,String username){
        HrServiceMemo memo=memo(id);
        if(!EnumSet.of(HrServiceMemo.Status.DRAFT,HrServiceMemo.Status.ISSUED,HrServiceMemo.Status.SIGNED)
                .contains(memo.getStatus()))
            throw new ResponseStatusException(CONFLICT,
                    "Редактировать служебную записку можно до передачи кадрам");
        if(r.teacherId()==null||!Objects.equals(r.teacherId(),memo.getTeacherId()))
            throw new ResponseStatusException(BAD_REQUEST,"Работника выпущенной служебной записки изменять нельзя");
        TeacherDirectoryEntry teacher=teachers.findById(r.teacherId())
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Педагог не найден"));
        EmploymentContract contract=r.contractId()==null?null:contracts.findById(r.contractId())
                .filter(item->Objects.equals(item.getTeacherId(),teacher.getId()))
                .orElseThrow(()->new ResponseStatusException(BAD_REQUEST,
                        "Трудовой договор не принадлежит выбранному педагогу"));
        HrCatalogItem catalog=r.catalogItemId()==null?null:catalogItems.findById(r.catalogItemId())
                .filter(item->item.isActive()&&Objects.equals(item.getSchoolCode(),SchoolCodeResolver.resolve()))
                .orElseThrow(()->new ResponseStatusException(BAD_REQUEST,"Элемент справочника недоступен"));
        boolean separate=Boolean.TRUE.equals(r.separateAgreement());
        String assignmentName=firstPresent(r.assignmentName(),catalog==null?null:catalog.getName());
        String duties=firstPresent(r.dutiesText(),catalog==null?null:catalog.getDutiesText());
        if(separate&&!present(duties))throw new ResponseStatusException(BAD_REQUEST,
                "Укажите дополнительные обязанности");
        BigDecimal amount=r.amount()!=null?r.amount():catalog==null?null:catalog.getDefaultAmount();
        if(r.validFrom()==null||r.validTo()==null)
            throw new ResponseStatusException(BAD_REQUEST,"Укажите период действия");
        String academicYear=required(r.academicYear(),"Учебный год");
        String clause=separate?null
                :standardContractClause(r.contractClause(),catalog==null?null:catalog.getContractClause());
        List<CompensationFunction> functions=compensationFunctions(academicYear,teacher.getId(),clause,separate,
                assignmentName,amount,r.validFrom());
        boolean automaticClause24=!separate&&"2.4".equals(clause);
        String exactClause24=automaticClause24?clause24Text(functions):null;
        String automaticMemo=automaticClause24?exactClause24:firstPresent(catalog==null?null:catalog.getMemoText(),
                automaticMemoText(teacher,assignmentName,amount,r.validFrom(),r.validTo(),separate,clause,functions));
        String automaticAgreement=automaticClause24?exactClause24:firstPresent(catalog==null?null:catalog.getAgreementText(),
                automaticAgreementText(assignmentName,duties,clause,separate,amount,functions));
        String assignmentText=automaticClause24?exactClause24:
                unchangedOrBlank(r.assignmentText(),memo.getAssignmentText())?automaticMemo:r.assignmentText().trim();
        String agreementText=automaticClause24?exactClause24:
                unchangedOrBlank(r.agreementText(),memo.getAgreementText())?automaticAgreement:r.agreementText().trim();
        if(Boolean.TRUE.equals(r.saveAsTemplate())&&r.catalogItemId()==null){
            HrCatalogItem saved=new HrCatalogItem();saved.setSchoolCode(SchoolCodeResolver.resolve());
            saved.setName(required(assignmentName,"Название основания"));
            saved.setCategory(separate?HrCatalogItem.Category.ADDITIONAL_WORK:HrCatalogItem.Category.COMPENSATION);
            saved.setContractClause(clause);saved.setDefaultAmount(amount);
            saved.setMemoText(automaticClause24?null:present(r.assignmentText())?r.assignmentText().trim():null);
            saved.setAgreementText(automaticClause24?null:present(r.agreementText())?r.agreementText().trim():null);
            saved.setDutiesText(duties);saved.setSeparateAgreement(separate);catalog=catalogItems.save(saved);
        }
        if(memo.getStatus()!=HrServiceMemo.Status.DRAFT&&memo.getDocumentContent()!=null)
            saveMemoVersion(memo,memo.getDocumentContent(),memo.getDocumentFilename(),"ISSUED_BEFORE_EDIT",username);
        memo.setAcademicYear(academicYear);memo.setContractId(contract==null?null:contract.getId());
        memo.setCatalogItemId(catalog==null?null:catalog.getId());memo.setTitle(firstPresent(r.title(),assignmentName));
        memo.setDocumentDate(r.documentDate());memo.setAssignmentName(required(assignmentName,"Основание или обязанность"));
        memo.setAssignmentText(assignmentText);memo.setAgreementText(agreementText);memo.setContractClause(clause);
        memo.setDutiesText(duties);memo.setAmount(amount);memo.setValidFrom(r.validFrom());memo.setValidTo(r.validTo());
        memo.setSeparateAgreement(separate);
        memo.setItemsJson(json(Map.of("teacherId",teacher.getId(),"fio",teacher.getFioTeacher(),
                "assignment",assignmentName,"contractClause",Objects.toString(clause,""),"separateAgreement",separate,
                "amount",amount==null?"":amount.toPlainString())));
        memo.setStatus(HrServiceMemo.Status.DRAFT);memo.setIssuedAt(null);memo.setIssuedBy(null);
        memo.setIssuedByFullName(null);memo.setIssuedByPosition(null);memo.setSignedAt(null);memo.setSignedBy(null);
        memo.setReceivedAt(null);memo.setReceivedBy(null);memo.setDocumentContent(generateMemo(memo));
        HrServiceMemo saved=memos.save(memo);
        syncLinkedDutyAgreements(saved,contract);
        return saved;
    }

    private boolean unchangedOrBlank(String requested,String current){
        return !present(requested)||Objects.equals(requested.trim(),Objects.toString(current,"").trim());
    }

    @Transactional public List<MemoView> memos(String year) {
        List<HrServiceMemo> result=memos.findAllByAcademicYearOrderByCreatedAtDesc(year);
        result.stream().filter(memo->memo.getStatus()!=HrServiceMemo.Status.ANNULLED
                        &&memo.getStatus()!=HrServiceMemo.Status.ARCHIVED)
                .filter(memo->agreements.findAllByServiceMemoId(memo.getId()).isEmpty())
                .forEach(memo->ensureDutyAgreement(memo,primaryContract(memo.getTeacherId()),firstPresent(memo.getCreatedBy(),"system")));
        return result.stream().filter(memo->memo.getStatus()!=HrServiceMemo.Status.ARCHIVED).map(this::memoView).toList();
    }

    @Transactional(readOnly = true)
    public List<MemoView> archivedMemos(String year){
        return memos.findAllByAcademicYearOrderByCreatedAtDesc(year).stream()
                .filter(memo->memo.getStatus()==HrServiceMemo.Status.ARCHIVED)
                .map(this::memoView).toList();
    }

    @Transactional(readOnly = true)
    public MemoView memoView(HrServiceMemo memo) {
        List<AdditionalAgreement> linked=memo.getId()==null?List.of():agreements.findAllByServiceMemoId(memo.getId());
        boolean deletable=memo.getStatus()==HrServiceMemo.Status.ANNULLED
                || (!linked.isEmpty()&&linked.stream().allMatch(a->a.getStatus()==AdditionalAgreement.Status.REJECTED));
        return new MemoView(memo.getId(),memo.getAcademicYear(),memo.getTeacherId(),memo.getContractId(),memo.getCatalogItemId(),
                memo.getTitle(),memo.getDocumentDate(),memo.getAssignmentName(),memo.getAmount(),memo.getValidFrom(),memo.getValidTo(),
                memo.isSeparateAgreement(),memo.getStatus().name(),memo.getAssignmentText(),memo.getAgreementText(),
                memo.getContractClause(),memo.getDutiesText(),memo.getCreatedAt(),memo.getCreatedBy(),memo.getIssuedAt(),
                memo.getSignedAt(),memo.getArchiveReason(),deletable);
    }

    @Transactional(readOnly = true) public List<HrCatalogItem> catalog() {
        return catalogItems.findAllBySchoolCodeAndActiveTrueOrderByName(SchoolCodeResolver.resolve());
    }

    @Transactional public HrServiceMemo memoStatus(Long id, HrServiceMemo.Status status, String username) {
        return memoStatus(id,status,username,username,"сотрудника");
    }

    @Transactional public HrServiceMemo memoStatus(Long id, HrServiceMemo.Status status, String username, String fullName, String position) {
        HrServiceMemo m = memo(id);
        if (m.getStatus() == HrServiceMemo.Status.ANNULLED||m.getStatus()==HrServiceMemo.Status.ARCHIVED)
            throw new ResponseStatusException(CONFLICT, "Служебная записка аннулирована или находится в архиве");
        if (status == HrServiceMemo.Status.ISSUED && m.getStatus() != HrServiceMemo.Status.DRAFT)
            throw new ResponseStatusException(CONFLICT,"Выпустить можно только черновик служебной записки");
        if(status==HrServiceMemo.Status.SIGNED&&m.getStatus()!=HrServiceMemo.Status.ISSUED
                &&m.getStatus()!=HrServiceMemo.Status.SIGNED)
            throw new ResponseStatusException(CONFLICT,"Подписать можно только выпущенную служебную записку");
        if (status == HrServiceMemo.Status.RECEIVED_BY_HR && m.getStatus() != HrServiceMemo.Status.SIGNED
                && m.getStatus() != HrServiceMemo.Status.RECEIVED_BY_HR)
            throw new ResponseStatusException(CONFLICT,"Сначала отметьте служебную записку как подписанную");
        if(status==HrServiceMemo.Status.ISSUED)refreshAutomaticClause24Memo(m);
        m.setStatus(status);
        if (status == HrServiceMemo.Status.ISSUED) {
            m.setIssuedAt(LocalDateTime.now()); m.setIssuedBy(username);
            m.setIssuedByFullName(firstPresent(fullName,username)); m.setIssuedByPosition(firstPresent(position,"сотрудника"));
            m.setDocumentContent(generateMemo(m));
        }
        if(status==HrServiceMemo.Status.SIGNED){
            m.setSignedAt(LocalDateTime.now());m.setSignedBy(username);
        }
        if (status == HrServiceMemo.Status.RECEIVED_BY_HR) {
            m.setReceivedAt(LocalDateTime.now()); m.setReceivedBy(username);
            ensureDutyAgreement(m,primaryContract(m.getTeacherId()),username);
            releaseWaiting(agreements.findAllByServiceMemoId(id));
        }
        return memos.save(m);
    }

    @Transactional public HrServiceMemo memoForDownload(Long id){
        HrServiceMemo memo=memo(id);
        if(memo.getStatus()==HrServiceMemo.Status.DRAFT){
            refreshAutomaticClause24Memo(memo);
            memo.setDocumentContent(generateMemo(memo));
            memo=memos.save(memo);
        }
        return memo;
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
        linked.forEach(this::deleteUnissuedAgreement);
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
        linked.forEach(this::deleteUnissuedAgreement);
        loadMemos.delete(memo);
    }

    @Transactional public void deleteAgreement(Long id) {
        AdditionalAgreement agreement=agreement(id);
        boolean annulled=agreement.getStatus()==AdditionalAgreement.Status.ANNULLED;
        if(!annulled&&(agreement.getIssuedAt()!=null||EnumSet.of(AdditionalAgreement.Status.ISSUED,
                AdditionalAgreement.Status.SIGNING,AdditionalAgreement.Status.SIGNED,
                AdditionalAgreement.Status.EXPIRED,AdditionalAgreement.Status.CANCELLED).contains(agreement.getStatus())))
            throw new ResponseStatusException(CONFLICT,"Выпущенное дополнительное соглашение нельзя удалить — его можно только аннулировать");
        if(agreement.getServiceMemoId()!=null){
            HrServiceMemo source=memos.findById(agreement.getServiceMemoId()).orElse(null);
            if(source!=null&&source.getStatus()!=HrServiceMemo.Status.ANNULLED
                    &&source.getStatus()!=HrServiceMemo.Status.ARCHIVED)
                throw new ResponseStatusException(CONFLICT,"Сначала аннулируйте или удалите связанную служебную записку");
        }
        if(agreement.getLoadServiceMemoId()!=null){
            ServiceMemo source=loadMemos.findById(agreement.getLoadServiceMemoId()).orElse(null);
            if(source!=null&&source.getStatus()!=ServiceMemo.Status.ANNULLED&&source.getStatus()!=ServiceMemo.Status.ARCHIVED)
                throw new ResponseStatusException(CONFLICT,"Сначала аннулируйте или удалите связанную служебную записку по нагрузке");
        }
        List<AdditionalAgreement> sameYear=agreements.findAllByAcademicYearOrderByCreatedAtDesc(agreement.getAcademicYear());
        List<AdditionalAgreement> referencing=sameYear.stream()
                .filter(candidate->Objects.equals(candidate.getReplacesAgreementId(),id)).toList();
        if(!annulled&&!referencing.isEmpty())
            throw new ResponseStatusException(CONFLICT,"Документ нельзя удалить: на него ссылается следующее дополнительное соглашение");
        referencing.forEach(candidate->{candidate.setReplacesAgreementId(null);agreements.save(candidate);});
        deleteUnissuedAgreement(agreement);
    }

    @Transactional public void deleteCatalogItem(Long id) {
        HrCatalogItem item=catalogItems.findById(id).filter(x->Objects.equals(x.getSchoolCode(),SchoolCodeResolver.resolve()))
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Позиция справочника не найдена"));
        item.setActive(false); item.setUpdatedAt(LocalDateTime.now()); catalogItems.save(item);
    }

    @Transactional public AdditionalAgreement createAgreement(AgreementRequest r, String username) {
        if(r.serviceMemoId()!=null)
            throw new ResponseStatusException(CONFLICT,"Дополнительное соглашение по служебной записке создаётся и связывается автоматически");
        if(r.kind()==AdditionalAgreement.Kind.ADDITIONAL_WORK)
            throw new ResponseStatusException(CONFLICT,"Сначала создайте служебную записку: черновик по дополнительной работе появится автоматически");
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
        applyAutomaticLoadClause(a,teacherId);
        if (old != null && old.getStatus() == AdditionalAgreement.Status.ANNULLED) {
            a.setInternalNumber(old.getInternalNumber()); a.setVisibleNumber(old.getVisibleNumber()); a.setRevision(old.getRevision() + 1); a.setReplacesAgreementId(old.getId());
        } else {
            a.setInternalNumber(c==null?temporaryNumber(teacherId,r.academicYear()):nextNumber(c, r.academicYear()));
            if (c!=null&&"1811".equals(SchoolCodeResolver.resolve())) a.setVisibleNumber(a.getInternalNumber());
        }
        HrPersonalData snapshotData = personalData.findByTeacherId(teacherId).orElse(null);
        if (snapshotData != null) a.setPersonalDataSnapshotJson(json(personalView(snapshotData)));
        Map<String,Object> source=new LinkedHashMap<>();source.put("teacherId",teacherId);source.put("contractId",c==null?null:c.getId());source.put("createdAt",LocalDateTime.now().toString());
        a.setSourceSnapshotJson(json(source));
        a.setCurrentFilename("Дополнительное_соглашение_" + a.getInternalNumber().replace('/','-') + ".docx");
        return agreements.save(a);
    }

    @Transactional public AdditionalAgreement createLoadChangeDraft(ServiceMemo memo, EmploymentContract contract, String username) {
        int startYear = Integer.parseInt(memo.getAcademicYear().substring(0, 4));
        AdditionalAgreement agreement = createAgreementForTeacher(new AgreementRequest(contract==null?null:contract.getId(), null, memo.getAcademicYear(),
                memo.getCreatedAt() == null ? LocalDate.now() : memo.getCreatedAt().toLocalDate(), memo.getChangeStartDate(),
                LocalDate.of(startYear + 1, 8, 31), AdditionalAgreement.Kind.PAY_TERMS,
                AdditionalAgreement.ChangeMode.AMEND, "Изменение учебной нагрузки с " + memo.getChangeStartDate(),
                LOAD_CHANGE_PLACEHOLDER,
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
            agreement.setCurrentFilename("Дополнительное_соглашение_"+agreement.getInternalNumber().replace('/','-')+".docx");
            agreements.save(agreement);
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
        Set<Long> loadTeacherIds=loadTeacherIds(r.academicYear());
        Set<Long> teacherIds=new LinkedHashSet<>(loadTeacherIds);
        classroomLeadershipRepository.findAllByAcademicYear(r.academicYear()).stream()
                .map(ClassroomLeadershipEntry::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);
        incentiveRepository.findAllByAcademicYear(r.academicYear()).stream()
                .filter(item->normalizedAmount(item.getAmount()).signum()>0)
                .map(HrIncentive::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);
        if(!requestedTeachers.isEmpty())teacherIds.retainAll(requestedTeachers);
        List<AdditionalAgreement> existing=new ArrayList<>(agreements.findAllByAcademicYearOrderByCreatedAtDesc(r.academicYear()));
        List<AdditionalAgreement> result=new ArrayList<>();
        for(Long teacherId:teacherIds){
            BigDecimal incentiveAmount=incentiveAmount(r.academicYear(),teacherId);
            Optional<AdditionalAgreement> existingAnnual=canonicalAnnualRegistryAgreement(existing,teacherId,from,to);
            syncAnnualIncentiveDrafts(existing,r.academicYear(),teacherId,incentiveAmount);
            EmploymentContract contract=primaryContract(teacherId);
            if(existingAnnual.isPresent()){
                AdditionalAgreement annual=existingAnnual.get();
                boolean sourceMarked=!annual.isRegistryManaged();
                annual.setRegistryManaged(true);
                if(EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,AdditionalAgreement.Status.DRAFT,
                        AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION,
                        AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING)
                        .contains(annual.getStatus())){
                    String oldConditions=annual.getConditionsJson();String oldSummary=annual.getSummary();
                    BigDecimal oldAmount=annual.getTotalAmount();
                    applyAutomaticLoadClause(annual,teacherId);
                    applyAutomaticAnnualClassroomLeadershipClause(annual,teacherId,true);
                    if(!Objects.equals(oldConditions,annual.getConditionsJson())
                            ||!Objects.equals(oldSummary,annual.getSummary())
                            ||!Objects.equals(oldAmount,annual.getTotalAmount())){
                        markAgreementContentChanged(annual);
                        agreements.save(annual);
                    }
                    else if(sourceMarked)agreements.save(annual);
                }
                continue;
            }
            boolean hasLoad=loadTeacherIds.contains(teacherId);
            List<CompensationFunction> classroomFunctions=classroomLeadershipFunctions(r.academicYear(),teacherId);
            String conditions=hasLoad?ANNUAL_LOAD_PLACEHOLDER:"";
            if(!classroomFunctions.isEmpty())
                conditions=upsertClause24Block(conditions,clause24Text(classroomFunctions));
            conditions=withIncentiveClause(conditions,incentiveAmount);
            String summary=annualSummary(hasLoad,!classroomFunctions.isEmpty(),incentiveAmount.signum()>0);
            Long replacesAgreementId=latestAnnulledAnnualRegistryAgreement(existing,teacherId,from,to)
                    .map(AdditionalAgreement::getId).orElse(null);
            AdditionalAgreement created=createAgreementForTeacher(new AgreementRequest(contract==null?null:contract.getId(),null,
                    r.academicYear(),r.documentDate(),from,to,AdditionalAgreement.Kind.PAY_TERMS,
                    AdditionalAgreement.ChangeMode.AMEND,summary,conditions,null,replacesAgreementId),
                    teacherId,contract,username);
            created.setRegistryManaged(true);agreements.save(created);
            result.add(created);existing.add(created);
        }return result;
    }

    private Optional<AdditionalAgreement> latestAnnulledAnnualRegistryAgreement(List<AdditionalAgreement> existing,
                                                                                 Long teacherId,LocalDate from,LocalDate to){
        return existing.stream()
                .filter(agreement->agreement.getStatus()==AdditionalAgreement.Status.ANNULLED)
                .filter(agreement->Objects.equals(agreementTeacherId(agreement,null),teacherId))
                .filter(agreement->agreement.getKind()==AdditionalAgreement.Kind.PAY_TERMS)
                .filter(agreement->Objects.equals(agreement.getValidFrom(),from)
                        &&Objects.equals(agreement.getValidTo(),to))
                .filter(this::isAnnualRegistryAgreement)
                .sorted(Comparator.comparing(AdditionalAgreement::getAnnulledAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdditionalAgreement::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdditionalAgreement::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    private Optional<AdditionalAgreement> canonicalAnnualRegistryAgreement(List<AdditionalAgreement> existing,
                                                                            Long teacherId,LocalDate from,LocalDate to){
        List<AdditionalAgreement> candidates=existing.stream()
                .filter(agreement->Objects.equals(agreementTeacherId(agreement,null),teacherId))
                .filter(agreement->agreement.getKind()==AdditionalAgreement.Kind.PAY_TERMS)
                .filter(this::isActiveAgreement)
                .filter(agreement->Objects.equals(agreement.getValidFrom(),from)
                        &&Objects.equals(agreement.getValidTo(),to))
                .filter(this::isAnnualRegistryAgreement)
                .sorted(Comparator.comparingInt(this::annualAgreementStatusRank)
                        .thenComparing(agreement->!isExplicitlyMergedAgreement(agreement))
                        .thenComparingInt(agreement->agreementNumber(agreement).orElse(Integer.MAX_VALUE))
                        .thenComparing(AdditionalAgreement::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AdditionalAgreement::getId,Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if(candidates.isEmpty())return Optional.empty();
        AdditionalAgreement canonical=candidates.get(0);
        candidates.stream().skip(1).filter(this::isSafeDuplicateAnnualDraft).forEach(duplicate->{
            deleteUnissuedAgreement(duplicate);
            existing.remove(duplicate);
        });
        return Optional.of(canonical);
    }

    private int annualAgreementStatusRank(AdditionalAgreement agreement){
        return switch(agreement.getStatus()){
            case SIGNED -> 0;
            case SIGNING,ISSUED -> 1;
            case READY -> 2;
            default -> 3;
        };
    }

    private boolean isExplicitlyMergedAgreement(AdditionalAgreement agreement){
        return Objects.toString(agreement.getSourceSnapshotJson(),"").contains("\"mergedAgreementIds\"");
    }

    private boolean isSafeDuplicateAnnualDraft(AdditionalAgreement agreement){
        return agreement.getIssuedAt()==null
                &&agreement.getServiceMemoId()==null&&agreement.getLoadServiceMemoId()==null
                &&!isExplicitlyMergedAgreement(agreement)
                &&EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,AdditionalAgreement.Status.DRAFT,
                AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION)
                .contains(agreement.getStatus());
    }

    @Transactional public AdditionalAgreement mergeAgreements(List<Long> requestedIds,String username){
        List<Long> ids=requestedIds==null?List.of():requestedIds.stream().filter(Objects::nonNull).distinct().toList();
        if(ids.size()<2)throw new ResponseStatusException(BAD_REQUEST,"Выберите не менее двух черновиков");
        List<AdditionalAgreement> selected=ids.stream().map(this::agreement).toList();
        AdditionalAgreement sample=selected.get(0);Long teacherId=agreementTeacherId(sample,null);
        if(selected.stream().anyMatch(item->item.getStatus()==AdditionalAgreement.Status.SIGNED))
            throw new ResponseStatusException(CONFLICT,"Подписанное дополнительное соглашение объединять и перевыпускать нельзя");
        EnumSet<AdditionalAgreement.Status> mergeable=EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION,
                AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING);
        if(selected.stream().anyMatch(item->!mergeable.contains(item.getStatus())))
            throw new ResponseStatusException(CONFLICT,"Объединять можно черновики либо один выпущенный, но ещё не подписанный документ");
        List<AdditionalAgreement> issued=selected.stream().filter(item->item.getStatus()==AdditionalAgreement.Status.ISSUED
                ||item.getStatus()==AdditionalAgreement.Status.SIGNING).toList();
        if(issued.size()>1)
            throw new ResponseStatusException(CONFLICT,"Для перевыпуска можно выбрать только один выпущенный неподписанный документ");
        boolean reissue=!issued.isEmpty();
        if(selected.stream().anyMatch(item->item.getKind()!=AdditionalAgreement.Kind.PAY_TERMS))
            throw new ResponseStatusException(CONFLICT,"Дополнительную работу с отдельным функционалом нужно оформлять отдельным соглашением");
        boolean differentOwner=selected.stream().anyMatch(item->!Objects.equals(teacherId,agreementTeacherId(item,null))
                ||!Objects.equals(sample.getContractId(),item.getContractId())
                ||!Objects.equals(sample.getAcademicYear(),item.getAcademicYear()));
        if(differentOwner)throw new ResponseStatusException(BAD_REQUEST,"Можно объединить документы только одного работника и одного трудового договора");
        boolean differentPeriod=selected.stream().anyMatch(item->!Objects.equals(sample.getValidFrom(),item.getValidFrom())
                ||!Objects.equals(sample.getValidTo(),item.getValidTo()));
        if(differentPeriod)throw new ResponseStatusException(BAD_REQUEST,"Для объединения периоды действия документов должны совпадать");
        Set<Long> dutyMemos=selected.stream().map(AdditionalAgreement::getServiceMemoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> changeMemos=selected.stream().map(AdditionalAgreement::getLoadServiceMemoId).filter(Objects::nonNull).collect(Collectors.toSet());
        boolean registryManaged=selected.stream().anyMatch(this::isAnnualRegistryAgreement);
        if(dutyMemos.size()>1||changeMemos.size()>1)
            throw new ResponseStatusException(CONFLICT,"В один документ пока можно объединить нагрузку и одну служебную записку по дополнительным функциям");

        AdditionalAgreement primary=reissue?issued.get(0):selected.stream().sorted(Comparator
                        .comparing((AdditionalAgreement item)->!isLoadAgreement(item))
                        .thenComparingInt(item->agreementNumber(item).orElse(Integer.MAX_VALUE))
                        .thenComparing(AdditionalAgreement::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst().orElseThrow();
        LocalDateTime previousIssuedAt=primary.getIssuedAt();String previousIssuedBy=primary.getIssuedBy();
        int previousRevision=primary.getRevision();
        List<AdditionalAgreement> ordered=selected.stream().sorted(Comparator
                .comparingInt(this::agreementClauseRank)
                .thenComparing(AdditionalAgreement::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        LinkedHashMap<String,String> conditionBlocks=new LinkedHashMap<>();
        ordered.stream().map(AdditionalAgreement::getConditionsJson).filter(this::present).forEach(text->
                conditionBlocks.putIfAbsent(text.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),text.trim()));
        LinkedHashSet<String> summaries=ordered.stream().map(AdditionalAgreement::getSummary).filter(this::present)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        BigDecimal loadAmount=ordered.stream().filter(this::isLoadAgreement).map(AdditionalAgreement::getTotalAmount)
                .filter(Objects::nonNull).findFirst().orElse(primary.getTotalAmount());

        primary.setConditionsJson(String.join("\n\n",conditionBlocks.values()));
        primary.setSummary(String.join(" · ",summaries));
        primary.setTotalAmount(loadAmount);
        primary.setServiceMemoId(dutyMemos.stream().findFirst().orElse(null));
        primary.setLoadServiceMemoId(changeMemos.stream().findFirst().orElse(null));
        primary.setRegistryManaged(registryManaged);
        applyAutomaticCompensationClause(primary,teacherId);
        if(!reissue)primary.setDocumentDate(ordered.stream().map(AdditionalAgreement::getDocumentDate).filter(Objects::nonNull)
                .min(LocalDate::compareTo).orElse(primary.getDocumentDate()));
        primary.setStatus(ordered.stream().anyMatch(item->item.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO)
                ?AdditionalAgreement.Status.WAITING_FOR_MEMO:AdditionalAgreement.Status.DRAFT);
        if(reissue&&primary.getCurrentDocument()!=null&&primary.getCurrentDocument().length>0){
            List<HrDocumentVersion> existingVersions=versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",primary.getId());
            boolean preserved=existingVersions.stream().anyMatch(version->Arrays.equals(version.getContent(),primary.getCurrentDocument()));
            if(!preserved)saveVersion(primary,primary.getCurrentDocument(),firstPresent(primary.getCurrentFilename(),
                    "Дополнительное_соглашение_"+primary.getInternalNumber().replace('/','-')+".docx"),
                    "ISSUED_BEFORE_REISSUE",username);
        }
        clearGenerated(primary);
        if(reissue){primary.setIssuedAt(null);primary.setIssuedBy(null);}
        else versions.deleteAll(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",primary.getId()));
        if(!reissue&&!"1811".equals(SchoolCodeResolver.resolve())){
            primary.setInternalNumber(sample.getContractId()==null
                    ?temporaryNumber(teacherId,sample.getAcademicYear(),new HashSet<>(ids))
                    :firstFreeNumber(sample.getContractId(),teacherId,sample.getAcademicYear(),new HashSet<>(ids)));
            primary.setVisibleNumber(null);
        }
        primary.setCurrentFilename("Дополнительное_соглашение_"+primary.getInternalNumber().replace('/','-')+".docx");
        Map<String,Object> mergedSource=new LinkedHashMap<>();mergedSource.put("teacherId",teacherId);
        mergedSource.put("contractId",sample.getContractId());mergedSource.put("mergedAgreementIds",ids);
        mergedSource.put("mergedAt",LocalDateTime.now().toString());mergedSource.put("mergedBy",username);
        mergedSource.put("reissue",reissue);
        if(reissue){mergedSource.put("previousRevision",previousRevision);mergedSource.put("previousIssuedAt",previousIssuedAt);
            mergedSource.put("previousIssuedBy",previousIssuedBy);}
        primary.setSourceSnapshotJson(json(mergedSource));
        selected.stream().filter(item->!Objects.equals(item.getId(),primary.getId())).forEach(this::deleteUnissuedAgreement);
        return agreements.save(primary);
    }

    private LocalDate firstWorkingDay(LocalDate d){while(d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY)d=d.plusDays(1);return d;}

    private Set<Long> loadTeacherIds(String academicYear){
        return loadRepository.findAllByAcademicYear(academicYear).stream()
                .filter(row->!row.isOrphaned())
                .filter(row->Optional.ofNullable(row.getGroupLoad()).orElse(Optional.ofNullable(row.getLoad()).orElse(0))>0)
                .map(ManualLoadEntry::getTeacherId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal incentiveAmount(String academicYear,Long teacherId){
        return incentiveRepository.findByAcademicYearAndTeacherId(academicYear,teacherId)
                .map(HrIncentive::getAmount).map(this::normalizedAmount).orElse(BigDecimal.ZERO);
    }

    private BigDecimal normalizedAmount(BigDecimal amount){
        return Optional.ofNullable(amount).orElse(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
    }

    private void syncAnnualIncentiveDrafts(String academicYear,Long teacherId,BigDecimal amount){
        syncAnnualIncentiveDrafts(agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear),
                academicYear,teacherId,amount);
    }

    @Transactional
    public void markAnnualLoadAgreementsChanged(String academicYear,Collection<Long> teacherIds){
        Set<Long> affected=Optional.ofNullable(teacherIds).orElse(List.of()).stream()
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if(affected.isEmpty())return;
        agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear).stream()
                .filter(this::isAnnualRegistryAgreement)
                .filter(agreement->affected.contains(agreementTeacherId(agreement,null)))
                .filter(agreement->EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                        AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,
                        AdditionalAgreement.Status.REQUIRES_DECISION,AdditionalAgreement.Status.ISSUED,
                        AdditionalAgreement.Status.SIGNING).contains(agreement.getStatus()))
                .forEach(agreement->{
                    Long teacherId=agreementTeacherId(agreement,null);
                    List<LoadDetail> details=loadDetails(teacherId,academicYear,agreement.getValidFrom());
                    agreement.setTotalAmount(calculatedLoadAmount(details));
                    markAgreementContentChanged(agreement);
                    agreements.save(agreement);
                });
    }

    private void syncAnnualIncentiveDrafts(List<AdditionalAgreement> candidates,String academicYear,
                                           Long teacherId,BigDecimal amount){
        candidates.stream().filter(agreement->Objects.equals(agreement.getAcademicYear(),academicYear))
                .filter(agreement->Objects.equals(agreementTeacherId(agreement,null),teacherId))
                .filter(this::isAnnualRegistryAgreement)
                .filter(agreement->EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                        AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,
                        AdditionalAgreement.Status.REQUIRES_DECISION,AdditionalAgreement.Status.ISSUED,
                        AdditionalAgreement.Status.SIGNING).contains(agreement.getStatus()))
                .forEach(agreement->{
                    if(applyIncentive(agreement,amount)){
                        if(normalizedAmount(amount).signum()==0&&!isLoadAgreement(agreement)
                                &&!present(agreement.getConditionsJson())){
                            if(!isIssuedUnsigned(agreement))deleteUnissuedAgreement(agreement);
                            else{agreement.setReissueRequired(true);agreements.save(agreement);}
                            return;
                        }
                        markAgreementContentChanged(agreement);
                        agreements.save(agreement);
                    }
                });
    }

    private void applyCurrentIncentive(AdditionalAgreement agreement){
        if(isAnnualRegistryAgreement(agreement))
            applyIncentive(agreement,incentiveAmount(agreement.getAcademicYear(),agreementTeacherId(agreement,null)));
    }

    private boolean applyIncentive(AdditionalAgreement agreement,BigDecimal requestedAmount){
        BigDecimal amount=normalizedAmount(requestedAmount);
        String oldConditions=Objects.toString(agreement.getConditionsJson(),"");
        String newConditions=withIncentiveClause(oldConditions,amount);
        String oldSummary=Objects.toString(agreement.getSummary(),"");
        String newSummary=oldSummary.replace(INCENTIVE_SUMMARY_SUFFIX,"").trim();
        if(amount.signum()==0&&"Стимулирующая выплата".equalsIgnoreCase(newSummary))newSummary="";
        if(amount.signum()>0&&!newSummary.toLowerCase(Locale.ROOT).contains("стимулиру"))
            newSummary+=INCENTIVE_SUMMARY_SUFFIX;
        boolean changed=!Objects.equals(oldConditions,newConditions)||!Objects.equals(oldSummary,newSummary);
        if(changed){agreement.setConditionsJson(newConditions);agreement.setSummary(newSummary);}
        return changed;
    }

    private boolean isAnnualRegistryAgreement(AdditionalAgreement agreement){
        if(!isAnnualPeriod(agreement))return false;
        if(agreement.isRegistryManaged())return true;
        String source=Objects.toString(agreement.getSourceSnapshotJson(),"");
        if(source.contains("\"mergedAgreementIds\"")&&isLoadAgreement(agreement))return true;
        if(agreement.getServiceMemoId()!=null||agreement.getLoadServiceMemoId()!=null)return false;
        String conditions=Objects.toString(agreement.getConditionsJson(),"").toLowerCase(Locale.ROOT);
        return isLoadAgreement(agreement)
                ||(conditions.contains("2.4")&&conditions.contains("классного руководителя"))
                ||(conditions.contains("2.5")&&conditions.contains("стимулиру"));
    }

    private void repairMisappliedRegistryIncentives(List<AdditionalAgreement> candidates){
        EnumSet<AdditionalAgreement.Status> repairable=EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,
                AdditionalAgreement.Status.REQUIRES_DECISION,AdditionalAgreement.Status.ISSUED,
                AdditionalAgreement.Status.SIGNING);
        candidates.stream()
                .filter(agreement->repairable.contains(agreement.getStatus()))
                .filter(agreement->!isAnnualRegistryAgreement(agreement))
                .filter(agreement->agreement.getServiceMemoId()!=null||agreement.getLoadServiceMemoId()!=null)
                .filter(agreement->!isExplicitIncentiveMemoAgreement(agreement))
                .forEach(agreement->{
                    if(applyIncentive(agreement,BigDecimal.ZERO)){
                        markAgreementContentChanged(agreement);
                        agreements.save(agreement);
                    }
                });
    }

    private boolean isExplicitIncentiveMemoAgreement(AdditionalAgreement agreement){
        if(agreement.getServiceMemoId()==null)return false;
        return memos.findById(agreement.getServiceMemoId())
                .map(memo->"2.5".equals(firstPresent(memo.getContractClause(),"2.4")))
                .orElse(false);
    }

    private boolean isAnnualPeriod(AdditionalAgreement agreement){
        if(agreement.getKind()!=AdditionalAgreement.Kind.PAY_TERMS
                ||agreement.getAcademicYear()==null||agreement.getAcademicYear().length()<4)return false;
        try{
            int startYear=Integer.parseInt(agreement.getAcademicYear().substring(0,4));
            return Objects.equals(agreement.getValidFrom(),firstWorkingDay(LocalDate.of(startYear,9,1)))
                    &&Objects.equals(agreement.getValidTo(),LocalDate.of(startYear+1,8,31));
        }catch(RuntimeException ignored){return false;}
    }

    private String withIncentiveClause(String conditions,BigDecimal requestedAmount){
        BigDecimal amount=normalizedAmount(requestedAmount);
        List<String> blocks=Arrays.stream(Objects.toString(conditions,"").replace("\r\n","\n")
                        .replace('\r','\n').trim().split("\\n\\s*\\n"))
                .map(String::trim).filter(this::present)
                .filter(block->{
                    String lower=block.toLowerCase(Locale.ROOT);
                    return !(lower.contains("2.5")&&lower.contains("стимулиру"));
                }).collect(Collectors.toCollection(ArrayList::new));
        if(amount.signum()>0)blocks.add(incentiveClause(amount));
        return String.join("\n\n",blocks);
    }

    private String incentiveClause(BigDecimal amount){
        return "Внести изменения в пункт 2.5. раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.5. Устанавливаются ежемесячные стимулирующие выплаты за результаты обучающихся "
                +"по итогам учебного года - "+moneyLegal(amount)+" ("+moneyWordsLegal(amount)+") в месяц».";
    }

    private Long excelLong(Cell cell){
        if(cell==null)return null;
        try{
            if(cell.getCellType()==CellType.NUMERIC)return (long)cell.getNumericCellValue();
            String value=new DataFormatter().formatCellValue(cell).trim().replaceAll("\\.0+$","");
            return value.isBlank()?null:Long.valueOf(value);
        }catch(RuntimeException error){return null;}
    }

    private BigDecimal excelAmount(Cell cell){
        if(cell==null)return null;
        try{
            if(cell.getCellType()==CellType.NUMERIC)return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2,RoundingMode.HALF_UP);
            String value=new DataFormatter().formatCellValue(cell).replace("\u00a0","")
                    .replace(" ","").replace(',','.').trim();
            BigDecimal amount=value.isBlank()?BigDecimal.ZERO:new BigDecimal(value);
            return amount.signum()<0?null:amount.setScale(2,RoundingMode.HALF_UP);
        }catch(RuntimeException error){return null;}
    }

    @Transactional public AdditionalAgreement editAgreement(Long id, AgreementEditRequest request, String username) {
        AdditionalAgreement agreement=agreement(id);
        if(!EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,AdditionalAgreement.Status.DRAFT,
                AdditionalAgreement.Status.READY,AdditionalAgreement.Status.REQUIRES_DECISION,
                AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING).contains(agreement.getStatus()))
            throw new ResponseStatusException(CONFLICT,"Подписанное, аннулированное или отклонённое дополнительное соглашение изменять нельзя");
        boolean issuedUnsigned=isIssuedUnsigned(agreement);
        Long teacherId=agreementTeacherId(agreement,null);
        if(request.contractId()!=null){
            EmploymentContract contract=contracts.findById(request.contractId())
                    .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Трудовой договор не найден"));
            if(!Objects.equals(contract.getTeacherId(),teacherId))
                throw new ResponseStatusException(BAD_REQUEST,"Трудовой договор принадлежит другому работнику");
            agreement.setContractId(contract.getId());
            if(agreement.getInternalNumber()!=null&&agreement.getInternalNumber().startsWith("БД-")){
                agreement.setInternalNumber(nextNumber(contract,agreement.getAcademicYear()));
                agreement.setVisibleNumber("1811".equals(SchoolCodeResolver.resolve())?agreement.getInternalNumber():null);
                agreement.setCurrentFilename("Дополнительное_соглашение_"+agreement.getInternalNumber().replace('/','-')+".docx");
            }
        }
        agreement.setDocumentDate(request.documentDate());
        agreement.setValidFrom(Objects.requireNonNull(request.validFrom(),"Начало периода обязательно"));
        agreement.setValidTo(Objects.requireNonNull(request.validTo(),"Окончание периода обязательно"));
        if(agreement.getValidTo().isBefore(agreement.getValidFrom()))
            throw new ResponseStatusException(BAD_REQUEST,"Окончание периода не может быть раньше начала");
        agreement.setSummary(request.summary()==null?null:request.summary().trim());
        agreement.setConditionsJson(request.conditionsJson()==null?null:request.conditionsJson().trim());
        agreement.setTotalAmount(request.totalAmount());
        if(agreement.getServiceMemoId()!=null){
            HrServiceMemo memo=memo(agreement.getServiceMemoId());
            memo.setAgreementText(agreement.getConditionsJson());memos.save(memo);
        }
        if(Boolean.TRUE.equals(request.saveAsTemplate()))saveAgreementTemplate(agreement,request.templateName());
        if(issuedUnsigned)agreement.setReissueRequired(true);
        else{
            clearGenerated(agreement);
            if(agreement.getStatus()!=AdditionalAgreement.Status.WAITING_FOR_MEMO)
                agreement.setStatus(AdditionalAgreement.Status.DRAFT);
        }
        return agreements.save(agreement);
    }

    @Transactional public AdditionalAgreement prepare(Long id,String username){
        AdditionalAgreement agreement=agreement(id);
        if(!EnumSet.of(AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,
                AdditionalAgreement.Status.REQUIRES_DECISION).contains(agreement.getStatus()))
            throw new ResponseStatusException(CONFLICT,agreement.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO
                    ?"Дополнительное соглашение ожидает получения служебной записки":"Документ нельзя сформировать в текущем статусе");
        applyCurrentIncentive(agreement);
        applyAutomaticLoadClause(agreement,agreementTeacherId(agreement,null));
        applyAutomaticAnnualClassroomLeadershipClause(agreement,agreementTeacherId(agreement,null),false);
        applyAutomaticCompensationClause(agreement,agreementTeacherId(agreement,null));
        EmploymentContract contract=validateAgreementData(agreement);
        validateInRateAllocation(agreement,contract);
        if(!present(agreement.getSummary()))throw new ResponseStatusException(CONFLICT,"Заполните краткое содержание дополнительного соглашения");
        if(!present(agreement.getConditionsJson()))throw new ResponseStatusException(CONFLICT,"Заполните юридическую формулировку дополнительного соглашения");
        if(agreement.getDocumentDate()==null)agreement.setDocumentDate(LocalDate.now());
        HrPersonalData snapshot=personalData.findByTeacherId(contract.getTeacherId()).orElseThrow();
        agreement.setPersonalDataSnapshotJson(json(personalView(snapshot)));
        byte[] document=generateAgreement(agreement,contract);
        agreement.setGeneratedDocument(document);agreement.setCurrentDocument(document);
        agreement.setReissueRequired(false);
        agreement.setRevision(agreement.getRevision()+1);agreement.setStatus(AdditionalAgreement.Status.READY);
        agreement.setCurrentFilename("Дополнительное_соглашение_"+agreement.getInternalNumber().replace('/','-')+".docx");
        AdditionalAgreement saved=agreements.save(agreement);
        saveVersion(saved,document,saved.getCurrentFilename(),"PREPARED",username);
        return saved;
    }

    @Transactional public AdditionalAgreement issue(Long id, String username) {
        AdditionalAgreement a = agreement(id);
        if (a.getStatus() == AdditionalAgreement.Status.ISSUED || a.getStatus() == AdditionalAgreement.Status.SIGNING || a.getStatus() == AdditionalAgreement.Status.SIGNED) return a;
        if (a.getStatus() == AdditionalAgreement.Status.WAITING_FOR_MEMO)
            throw new ResponseStatusException(CONFLICT,"Дополнительное соглашение ожидает получения служебной записки");
        if (a.getStatus() != AdditionalAgreement.Status.READY)
            throw new ResponseStatusException(CONFLICT,"Сначала отредактируйте и сформируйте дополнительное соглашение");
        EmploymentContract contract=validateAgreementData(a);
        validateInRateAllocation(a,contract);
        if(a.getCurrentDocument()==null||a.getCurrentDocument().length==0)
            throw new ResponseStatusException(CONFLICT,"Файл дополнительного соглашения ещё не сформирован");
        a.setReissueRequired(false);
        a.setStatus(AdditionalAgreement.Status.ISSUED); a.setIssuedAt(LocalDateTime.now()); a.setIssuedBy(username); return agreements.save(a);
    }

    private void validateInRateAllocation(AdditionalAgreement agreement, EmploymentContract contract) {
        if (agreement == null || contract == null
                || (!contract.isLoadHoursMayBeIncludedInRate()
                &&ruleForPosition(contract.getPositionName(),contract.getLoadInRateRuleId())==null)) return;
        List<ManualLoadEntry> rows = loadRepository.findAllByAcademicYear(agreement.getAcademicYear()).stream()
                .filter(row -> Objects.equals(row.getTeacherId(), contract.getTeacherId()))
                .filter(row -> loadSalaryCalculationService.totalHours(row).signum() > 0)
                .toList();
        long unresolved = rows.stream().filter(row -> !row.isInRateAllocationConfirmed()).count();
        if (unresolved > 0) {
            throw new ResponseStatusException(CONFLICT,
                    "Сначала распределите часы внутри ставки в разделе «Нагрузка → Часы в ставке». Не подтверждено строк: "
                            + unresolved);
        }
    }

    private LoadInRateRule ruleForPosition(String positionName,Long preferredRuleId){
        String position=Objects.toString(positionName,"").trim();
        if(position.isBlank())return null;
        if(preferredRuleId!=null){
            LoadInRateRule preferred=loadInRateRuleRepository.findById(preferredRuleId).orElse(null);
            if(preferred!=null&&preferred.isActive()&&preferred.getName().equalsIgnoreCase(position))return preferred;
        }
        return loadInRateRuleRepository.findAllByOrderByNameAsc().stream()
                .filter(LoadInRateRule::isActive)
                .filter(rule->rule.getName().equalsIgnoreCase(position))
                .findFirst().orElse(null);
    }

    @Transactional public AdditionalAgreement reopenIssuedAgreement(Long id,String username){
        AdditionalAgreement agreement=agreement(id);
        if(agreement.getStatus()==AdditionalAgreement.Status.SIGNED)
            throw new ResponseStatusException(CONFLICT,"Подписанное дополнительное соглашение исправлять и перевыпускать нельзя");
        if(agreement.getStatus()!=AdditionalAgreement.Status.ISSUED
                &&agreement.getStatus()!=AdditionalAgreement.Status.SIGNING)
            throw new ResponseStatusException(CONFLICT,"Исправить и перевыпустить можно только выпущенный, но ещё не подписанный документ");
        if(agreement.getCurrentDocument()!=null&&agreement.getCurrentDocument().length>0){
            List<HrDocumentVersion> history=versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",agreement.getId());
            boolean preserved=history.stream().anyMatch(version->Arrays.equals(version.getContent(),agreement.getCurrentDocument()));
            if(!preserved)saveVersion(agreement,agreement.getCurrentDocument(),
                    firstPresent(agreement.getCurrentFilename(),"Дополнительное_соглашение_"
                            +agreement.getInternalNumber().replace('/','-')+".docx"),
                    "ISSUED_BEFORE_REISSUE",username);
        }
        clearGenerated(agreement);
        agreement.setIssuedAt(null);agreement.setIssuedBy(null);
        agreement.setStatus(AdditionalAgreement.Status.DRAFT);
        applyCurrentIncentive(agreement);
        applyAutomaticLoadClause(agreement,agreementTeacherId(agreement,null));
        applyAutomaticAnnualClassroomLeadershipClause(agreement,agreementTeacherId(agreement,null),false);
        return agreements.save(agreement);
    }

    @Transactional public AdditionalAgreement upload(Long id, String filename, byte[] content, String username) {
        AdditionalAgreement a = agreement(id); if (content == null || content.length == 0) throw new ResponseStatusException(BAD_REQUEST,"Пустой файл");
        a.setRevision(a.getRevision() + 1); a.setCurrentDocument(content); a.setCurrentFilename(filename);
        a.setReissueRequired(false);agreements.save(a);
        saveVersion(a, content, filename, "UPLOADED", username); return a;
    }
    @Transactional public AdditionalAgreement annulAgreement(Long id, String reason, String username) {
        AdditionalAgreement a = agreement(id);String annulReason=required(reason,"Причина");
        a.setStatus(AdditionalAgreement.Status.ANNULLED); a.setAnnulReason(annulReason);
        a.setAnnulledAt(LocalDateTime.now()); a.setAnnulledBy(username);
        AdditionalAgreement saved=agreements.save(a);
        archiveSourceMemos(saved,annulReason,username);
        return saved;
    }

    private void archiveSourceMemos(AdditionalAgreement agreement,String reason,String username){
        String number=firstPresent(agreement.getVisibleNumber(),agreement.getInternalNumber(),"без номера");
        String archiveReason="Дополнительное соглашение № "+number+" аннулировано"
                +(present(reason)?": "+reason:"");
        if(agreement.getServiceMemoId()!=null)memos.findById(agreement.getServiceMemoId()).ifPresent(memo->{
            memo.setStatus(HrServiceMemo.Status.ARCHIVED);memo.setArchivedAt(LocalDateTime.now());
            memo.setArchivedBy(username);memo.setArchiveReason(archiveReason);memos.save(memo);
        });
        if(agreement.getLoadServiceMemoId()!=null)loadMemos.findById(agreement.getLoadServiceMemoId()).ifPresent(memo->{
            memo.setStatus(ServiceMemo.Status.ARCHIVED);memo.setArchivedAt(LocalDateTime.now());
            memo.setArchivedBy(username);memo.setArchiveReason(archiveReason);loadMemos.save(memo);
        });
    }
    @Transactional public AdditionalAgreement agreementStatus(Long id, AdditionalAgreement.Status status) {
        AdditionalAgreement a=agreement(id); if(a.getStatus()==AdditionalAgreement.Status.ANNULLED) throw new ResponseStatusException(CONFLICT,"Документ аннулирован");
        if(status==AdditionalAgreement.Status.REJECTED&&EnumSet.of(AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING,AdditionalAgreement.Status.SIGNED).contains(a.getStatus()))
            throw new ResponseStatusException(CONFLICT,"Выпущенное допсоглашение нужно аннулировать, а не отклонять");
        if(a.getStatus()==AdditionalAgreement.Status.WAITING_FOR_MEMO && status!=AdditionalAgreement.Status.REQUIRES_DECISION&&status!=AdditionalAgreement.Status.REJECTED)
            throw new ResponseStatusException(CONFLICT,"Сначала отметьте получение служебной записки кадрами");
        if(status==AdditionalAgreement.Status.SIGNED && a.getStatus()!=AdditionalAgreement.Status.ISSUED && a.getStatus()!=AdditionalAgreement.Status.SIGNING)
            throw new ResponseStatusException(CONFLICT,"Сначала выпустите дополнительное соглашение");
        if(status==AdditionalAgreement.Status.SIGNED&&a.isReissueRequired())
            throw new ResponseStatusException(CONFLICT,"Документ изменён после выпуска. Сначала перевыпустите актуальную редакцию");
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
        clearGenerated(a);if(a.getStatus()!=AdditionalAgreement.Status.WAITING_FOR_MEMO)a.setStatus(AdditionalAgreement.Status.DRAFT);
        return agreements.save(a);
    }

    @Transactional(readOnly = true)
    public String agreementDownloadFilename(AdditionalAgreement agreement){
        Long teacherId=agreementTeacherId(agreement,null);
        String fio=teacherId==null?"Работник":teachers.findById(teacherId)
                .map(TeacherDirectoryEntry::getFioTeacher).filter(this::present).orElse("Работник");
        String number=present(agreement.getVisibleNumber())?agreement.getVisibleNumber().trim()
                :agreementNumber(agreement).isPresent()?String.valueOf(agreementNumber(agreement).getAsInt())
                :firstPresent(agreement.getInternalNumber(),"б/н");
        String date=agreement.getDocumentDate()==null?"без даты":formatDate(agreement.getDocumentDate());
        return (fio+" доп согл № "+number+" от "+date+".docx")
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","-").replaceAll("\\s+"," ").trim();
    }

    private EmploymentContract validateAgreementData(AdditionalAgreement agreement){
        if(agreement.getContractId()==null)
            throw new ResponseStatusException(CONFLICT,"Заполните действующий трудовой договор работника перед формированием допсоглашения");
        EmploymentContract contract=contracts.findById(agreement.getContractId())
                .orElseThrow(()->new ResponseStatusException(CONFLICT,"Трудовой договор допсоглашения не найден"));
        if(!personalData.findByTeacherId(contract.getTeacherId()).map(this::isComplete).orElse(false))
            throw new ResponseStatusException(CONFLICT,"Заполните обязательные персональные данные работника");
        if(agreement.getServiceMemoId()!=null){
            HrServiceMemo memo=memo(agreement.getServiceMemoId());
            if(memo.getStatus()!=HrServiceMemo.Status.RECEIVED_BY_HR&&memo.getStatus()!=HrServiceMemo.Status.EXECUTED)
                throw new ResponseStatusException(CONFLICT,"Служебная записка не получена кадрами");
        }
        if(agreement.getLoadServiceMemoId()!=null){
            ServiceMemo memo=loadMemos.findById(agreement.getLoadServiceMemoId())
                    .orElseThrow(()->new ResponseStatusException(CONFLICT,"Служебная записка по нагрузке не найдена"));
            if(memo.getStatus()!=ServiceMemo.Status.RECEIVED_BY_HR&&memo.getStatus()!=ServiceMemo.Status.EXECUTED)
                throw new ResponseStatusException(CONFLICT,"Служебная записка по нагрузке не получена кадрами");
        }
        return contract;
    }

    private void clearGenerated(AdditionalAgreement agreement){
        agreement.setGeneratedDocument(null);agreement.setCurrentDocument(null);agreement.setReissueRequired(false);
    }

    private boolean isIssuedUnsigned(AdditionalAgreement agreement){
        return agreement.getStatus()==AdditionalAgreement.Status.ISSUED
                ||agreement.getStatus()==AdditionalAgreement.Status.SIGNING;
    }

    private void markAgreementContentChanged(AdditionalAgreement agreement){
        if(isIssuedUnsigned(agreement))agreement.setReissueRequired(true);
        else{
            clearGenerated(agreement);
            if(agreement.getStatus()==AdditionalAgreement.Status.READY)
                agreement.setStatus(AdditionalAgreement.Status.DRAFT);
        }
    }

    private void saveAgreementTemplate(AdditionalAgreement agreement,String requestedName){
        String name=firstPresent(requestedName,agreement.getSummary());
        if(!present(name))throw new ResponseStatusException(BAD_REQUEST,"Укажите название шаблона");
        String schoolCode=SchoolCodeResolver.resolve();HrServiceMemo memo=null;HrCatalogItem item=null;
        if(agreement.getServiceMemoId()!=null){
            memo=memo(agreement.getServiceMemoId());
            if(memo.getCatalogItemId()!=null)item=catalogItems.findById(memo.getCatalogItemId()).orElse(null);
        }
        if(item==null)item=catalogItems.findFirstBySchoolCodeAndNameIgnoreCase(schoolCode,name).orElseGet(HrCatalogItem::new);
        item.setSchoolCode(schoolCode);item.setName(name);
        item.setCategory(agreement.getKind()==AdditionalAgreement.Kind.ADDITIONAL_WORK
                ?HrCatalogItem.Category.ADDITIONAL_WORK:HrCatalogItem.Category.COMPENSATION);
        item.setDefaultAmount(agreement.getTotalAmount());item.setAgreementText(agreement.getConditionsJson());
        item.setSeparateAgreement(agreement.getKind()==AdditionalAgreement.Kind.ADDITIONAL_WORK);
        item.setActive(true);item.setUpdatedAt(LocalDateTime.now());item=catalogItems.save(item);
        if(memo!=null){memo.setCatalogItemId(item.getId());memo.setAgreementText(agreement.getConditionsJson());memos.save(memo);}
    }
    @Transactional(readOnly = true) public AdditionalAgreement agreement(Long id) { return agreements.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    @Transactional(readOnly = true) public HrServiceMemo memo(Long id) { return memos.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    @Transactional(readOnly = true) public List<HrDocumentVersion> versions(Long id) { return versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",id); }

    private void saveVersion(AdditionalAgreement a, byte[] content, String filename, String source, String user) {
        HrDocumentVersion v=new HrDocumentVersion(); v.setDocumentType("AGREEMENT"); v.setDocumentId(a.getId()); v.setRevision(a.getRevision());
        v.setContent(content); v.setFilename(filename); v.setSource(source); v.setCreatedBy(user); versions.save(v);
    }
    private void saveMemoVersion(HrServiceMemo memo,byte[] content,String filename,String source,String user){
        int revision=versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("SERVICE_MEMO",memo.getId())
                .stream().map(HrDocumentVersion::getRevision).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0)+1;
        HrDocumentVersion version=new HrDocumentVersion();version.setDocumentType("SERVICE_MEMO");
        version.setDocumentId(memo.getId());version.setRevision(revision);version.setContent(content);
        version.setFilename(filename);version.setSource(source);version.setCreatedBy(user);versions.save(version);
    }
    private void syncLinkedDutyAgreements(HrServiceMemo memo,EmploymentContract contract){
        agreements.findAllByServiceMemoId(memo.getId()).forEach(agreement->{
            if(EnumSet.of(AdditionalAgreement.Status.ISSUED,AdditionalAgreement.Status.SIGNING,
                    AdditionalAgreement.Status.SIGNED,AdditionalAgreement.Status.EXPIRED,
                    AdditionalAgreement.Status.CANCELLED).contains(agreement.getStatus()))
                throw new ResponseStatusException(CONFLICT,
                        "По служебной записке уже выпущено дополнительное соглашение; сначала исправьте его отдельно");
            if(EnumSet.of(AdditionalAgreement.Status.REJECTED,AdditionalAgreement.Status.ANNULLED)
                    .contains(agreement.getStatus()))return;
            agreement.setContractId(contract==null?null:contract.getId());
            agreement.setSummary(memo.getAssignmentName());agreement.setConditionsJson(memo.getAgreementText());
            agreement.setTotalAmount(memo.getAmount());agreement.setValidFrom(memo.getValidFrom());
            agreement.setValidTo(memo.getValidTo());agreement.setDocumentDate(memo.getDocumentDate());
            agreement.setKind(memo.isSeparateAgreement()?AdditionalAgreement.Kind.ADDITIONAL_WORK:
                    AdditionalAgreement.Kind.PAY_TERMS);
            clearGenerated(agreement);agreement.setStatus(AdditionalAgreement.Status.WAITING_FOR_MEMO);
            agreements.save(agreement);
        });
    }
    private void releaseWaiting(List<AdditionalAgreement> linked) {
        linked.stream().filter(a -> a.getStatus() == AdditionalAgreement.Status.WAITING_FOR_MEMO)
                .forEach(a -> { a.setStatus(AdditionalAgreement.Status.DRAFT); agreements.save(a); });
    }
    private String nextNumber(EmploymentContract c, String year) {
        if(!"1811".equals(SchoolCodeResolver.resolve()))return firstFreeNumber(c.getId(),c.getTeacherId(),year,Set.of());
        int next=agreements.findAllByContractIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(this::agreementNumber).flatMapToInt(OptionalInt::stream).max().orElse(0)+1;
        return String.valueOf(next);
    }
    private String temporaryNumber(Long teacherId,String year){
        return temporaryNumber(teacherId,year,Set.of());
    }
    private String temporaryNumber(Long teacherId,String year,Set<Long> excludedIds){
        Set<Integer> used=agreements.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(teacherId).stream()
                .filter(item->Objects.equals(item.getAcademicYear(),year)).filter(this::occupiesInternalNumber)
                .filter(item->item.getId()==null||!excludedIds.contains(item.getId()))
                .map(item->temporaryAgreementNumber(item,teacherId)).flatMapToInt(OptionalInt::stream)
                .boxed().collect(Collectors.toSet());
        int n=firstFreePositive(used);
        return "БД-"+teacherId+"-"+n;
    }
    private String firstFreeNumber(Long contractId,Long teacherId,String year,Set<Long> excludedIds){
        Collection<AdditionalAgreement> candidates=contractId==null
                ?agreements.findAllByTeacherIdAndContractIdIsNullOrderByCreatedAtDesc(teacherId)
                :agreements.findAllByContractIdOrderByCreatedAtDesc(contractId);
        Set<Integer> used=candidates.stream().filter(item->Objects.equals(item.getAcademicYear(),year))
                .filter(item->item.getId()==null||!excludedIds.contains(item.getId()))
                .filter(this::occupiesInternalNumber).map(this::agreementNumber).flatMapToInt(OptionalInt::stream)
                .boxed().collect(Collectors.toSet());
        return firstFreePositive(used)+" / "+year.replace('/','-');
    }
    private int firstFreePositive(Set<Integer> used){int value=1;while(used.contains(value))value++;return value;}
    private boolean occupiesInternalNumber(AdditionalAgreement agreement){
        return agreement.getStatus()!=AdditionalAgreement.Status.ANNULLED
                &&agreement.getStatus()!=AdditionalAgreement.Status.REJECTED;
    }
    private OptionalInt agreementNumber(AdditionalAgreement agreement){
        String value=Objects.toString(agreement.getInternalNumber(),"").trim();
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("^(\\d+)").matcher(value);
        return matcher.find()?OptionalInt.of(Integer.parseInt(matcher.group(1))):OptionalInt.empty();
    }
    private OptionalInt temporaryAgreementNumber(AdditionalAgreement agreement,Long teacherId){
        String prefix="БД-"+teacherId+"-";String value=Objects.toString(agreement.getInternalNumber(),"");
        if(!value.startsWith(prefix))return OptionalInt.empty();
        try{return OptionalInt.of(Integer.parseInt(value.substring(prefix.length())));}
        catch(NumberFormatException ignored){return OptionalInt.empty();}
    }
    private void deleteUnissuedAgreement(AdditionalAgreement agreement){
        versions.deleteAll(versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",agreement.getId()));
        agreements.delete(agreement);
    }
    private boolean isActiveAgreement(AdditionalAgreement agreement){
        return agreement.getStatus()!=AdditionalAgreement.Status.ANNULLED
                &&agreement.getStatus()!=AdditionalAgreement.Status.REJECTED
                &&agreement.getStatus()!=AdditionalAgreement.Status.CANCELLED;
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
    public AgreementView agreementView(AdditionalAgreement agreement) { return view(agreement); }
    private AgreementView view(AdditionalAgreement a) { return new AgreementView(a.getId(),a.getInternalNumber(),a.getVisibleNumber(),a.getRevision(),a.getKind().name(),a.getStatus().name(),a.getChangeMode().name(),a.getReplacesAgreementId(),a.getDocumentDate(),a.getValidFrom(),a.getValidTo(),a.getSummary(),a.getConditionsJson(),a.getTotalAmount(),a.getServiceMemoId(),a.getLoadServiceMemoId(),a.getIssuedAt(),isAnnualRegistryAgreement(a),a.isReissueRequired()); }
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
            enforceMemoFontSize(d,14);
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
        String replaced=original;
        if(original.contains("{DOCUMENT_DATE}")&&original.contains("{AUTHOR_NAME}")){
            replaced=replacements.getOrDefault("{DOCUMENT_DATE}","")+"\t\t\t"
                    +replacements.getOrDefault("{AUTHOR_NAME}","");
        }else for(Map.Entry<String,String> entry:replacements.entrySet())replaced=replaced.replace(entry.getKey(),entry.getValue());
        if(Objects.equals(original,replaced))return;
        if(original.contains("{MEMO_TEXT}")&&replaced.contains("\n"))paragraph.setAlignment(ParagraphAlignment.LEFT);
        if(original.contains("{DOCUMENT_DATE}")&&original.contains("{AUTHOR_NAME}"))paragraph.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun source=paragraph.getRuns().isEmpty()?null:paragraph.getRuns().get(0);
        String font=source==null?"Times New Roman":firstPresent(source.getFontFamily(),"Times New Roman");
        int size=Math.max(14,source==null||source.getFontSize()<=0?14:source.getFontSize());boolean bold=source!=null&&source.isBold();boolean italic=source!=null&&source.isItalic();
        while(!paragraph.getRuns().isEmpty())paragraph.removeRun(0);
        XWPFRun run=paragraph.createRun();run.setFontFamily(font);run.setFontSize(size);run.setBold(bold);run.setItalic(italic);appendRunText(run,replaced);
    }

    private void enforceMemoFontSize(XWPFDocument document,int minimumSize){
        allDocumentParagraphs(document).forEach(paragraph->paragraph.getRuns().forEach(run->{
            run.setFontFamily("Times New Roman");
            run.setFontFamily("Times New Roman",XWPFRun.FontCharRange.eastAsia);
            if(run.getFontSize()<minimumSize)run.setFontSize(minimumSize);
        }));
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
        String body=Objects.toString(memo.getAssignmentText(),"").replace("\r\n","\n").replace('\r','\n')
                .replaceAll("[ \\t]+\\n","\n").replaceAll("\\n{3,}","\n\n").trim();
        if(memo.isSeparateAgreement()&&present(memo.getDutiesText())){
            List<String> duties=Arrays.stream(memo.getDutiesText().trim().split("(?:\\R|;)+"))
                    .map(String::trim).filter(this::present).map(this::finishSentence).toList();
            if(!duties.isEmpty())body=body+"\nДополнительные обязанности:\n- "+String.join("\n- ",duties);
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

    private byte[] generateAgreement(AdditionalAgreement a,EmploymentContract c){
        Long teacherId=agreementTeacherId(a,c);TeacherDirectoryEntry teacher=teachers.findById(teacherId).orElseThrow();
        HrPersonalData personal=personalData.findByTeacherId(teacherId).orElse(null);
        try(XWPFDocument document=new XWPFDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            configureAgreementPage(document);String number=present(a.getVisibleNumber())?" № "+a.getVisibleNumber():"";
            agreementTitle(document,"ДОПОЛНИТЕЛЬНОЕ СОГЛАШЕНИЕ"+number,12);
            agreementCentered(document,"к Трудовому договору от "+contractDate(c)+" № "+contractNumber(c),true,11);
            if(a.getKind()==AdditionalAgreement.Kind.ADDITIONAL_WORK)agreementCentered(document,"об увеличении объема работ",true,11);
            addCityAndDate(document,a.getDocumentDate());
            agreementBody(document,employerIntro()+", "+employerAuthorityPhrase()+" на основании Устава, с одной стороны, и "
                    +teacher.getFioTeacher()+", именуемый(ая) в дальнейшем «Работник», с другой стороны (далее - стороны), "
                    +"заключили настоящее дополнительное соглашение к трудовому договору о нижеследующем:",false);
            if(a.getReplacesAgreementId()!=null){
                AdditionalAgreement previous=agreement(a.getReplacesAgreementId());String reference="дополнительное соглашение "+(present(previous.getVisibleNumber())?"№ "+previous.getVisibleNumber()+" ":"")+"от "+legalDate(previous.getDocumentDate());
                agreementBody(document,"1. "+(a.getChangeMode()==AdditionalAgreement.ChangeMode.CANCEL_AND_RESTATE?"Признать утратившим силу с "+legalDate(a.getValidFrom())+" "+reference+" и считать соответствующие условия трудового договора действующими в редакции настоящего дополнительного соглашения.":"Внести с "+legalDate(a.getValidFrom())+" изменения в "+reference+"."),false);
            }
            int point=a.getReplacesAgreementId()==null?1:2;
            point=a.getKind()==AdditionalAgreement.Kind.ADDITIONAL_WORK?appendAdditionalWorkTerms(document,a,point):appendPayTerms(document,a,point);
            agreementBody(document,(point++)+". Настоящее дополнительное соглашение является неотъемлемой частью Трудового договора от "
                    +contractDate(c)+" № "+contractNumber(c)+", составлено в двух экземплярах, имеющих одинаковую юридическую силу. Один экземпляр хранится Работодателем в личном деле Работника, второй - у Работника.",false);
            agreementBody(document,(point++)+". Другие положения Трудового договора от "+contractDate(c)+" № "
                    +contractNumber(c)+" остаются неизменными.",false);
            addAgreementSignatures(document,teacher,personal,a.getDocumentDate());
            if(hasLoadAnnex(a))appendLoadAnnex(document,teacherId,a.getAcademicYear(),a.getValidFrom(),c);
            document.write(out);return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("Не удалось сформировать дополнительное соглашение",e);}
    }

    private int appendAdditionalWorkTerms(XWPFDocument document,AdditionalAgreement agreement,int point){
        HrServiceMemo memo=agreement.getServiceMemoId()==null?null:memos.findById(agreement.getServiceMemoId()).orElse(null);
        String condition=firstPresent(agreement.getConditionsJson(),agreement.getSummary());String embeddedDuties="";int marker=condition.indexOf("Дополнительные обязанности:");
        if(marker>=0){embeddedDuties=condition.substring(marker+"Дополнительные обязанности:".length()).trim();condition=condition.substring(0,marker).trim();}
        String assignment=memo==null?agreement.getSummary():firstPresent(memo.getAssignmentName(),agreement.getSummary());
        if(!present(condition))condition="Работнику поручается без освобождения от работы, определенной трудовым договором, выполнение дополнительной работы «"+assignment+"» в порядке увеличения объема работ.";
        agreementBody(document,(point++)+". "+finishSentence(condition),false);
        String duties=memo==null?embeddedDuties:firstPresent(memo.getDutiesText(),embeddedDuties);
        if(present(duties)){agreementBody(document,(point++)+". При выполнении дополнительной работы Работник исполняет следующие обязанности:",false);for(String duty:duties.split("(?:\\R|;)+"))if(present(duty))agreementBody(document,"– "+finishSentence(duty.trim()),false,991);}
        agreementBody(document,(point++)+". Дополнительная работа выполняется без освобождения от основной работы в период с "+legalDate(agreement.getValidFrom())+" по "+legalDate(agreement.getValidTo())+".",false);
        if(agreement.getTotalAmount()!=null)agreementBody(document,(point++)+". За выполнение дополнительной работы Работнику устанавливается ежемесячная доплата в размере "+money(agreement.getTotalAmount())+" ("+moneyWords(agreement.getTotalAmount())+") до удержания налогов и иных обязательных платежей.",false);
        agreementBody(document,(point++)+". Работник вправе досрочно отказаться от выполнения дополнительной работы, а Работодатель — досрочно отменить поручение о ее выполнении, предупредив другую Сторону в письменной форме не позднее чем за три рабочих дня.",false);return point;
    }

    private int appendPayTerms(XWPFDocument document,AdditionalAgreement agreement,int point){
        String condition=firstPresent(agreement.getConditionsJson(),agreement.getSummary());
        point=appendConditionBlocks(document,condition,point);
        agreementBody(document,(point++)+". Срок действия настоящего дополнительного соглашения: с "
                +periodDate(agreement.getValidFrom())+" по "+periodDate(agreement.getValidTo())+".",false);return point;
    }

    private int appendCondition(XWPFDocument document,String condition,int point){
        String normalized=Objects.toString(condition,"").replace("\r\n","\n").replace('\r','\n').trim();
        String[] lines=normalized.split("\\n");
        if(lines.length==0)return point;
        String first=lines[0].trim();
        agreementBody(document,(point++)+". "+(lines.length>1&&first.endsWith(":")?first:finishSentence(first)),false);
        for(int i=1;i<lines.length;i++){
            String line=lines[i].trim();
            if(!line.isEmpty())agreementBody(document,line,false,line.startsWith("-")||line.startsWith("–")?991:708);
        }
        return point;
    }

    private int appendConditionBlocks(XWPFDocument document,String condition,int point){
        String normalized=Objects.toString(condition,"").replace("\r\n","\n").replace('\r','\n').trim();
        if(normalized.isEmpty())return point;
        for(String block:normalized.split("\\n\\s*\\n(?=(?:Внести|Изложить))"))
            if(present(block))point=appendCondition(document,block,point);
        return point;
    }
    private boolean hasLoadAnnex(AdditionalAgreement agreement){
        return agreement.getKind()==AdditionalAgreement.Kind.PAY_TERMS
                &&isLoadAgreement(agreement)
                &&!loadDetails(agreementTeacherId(agreement,null),agreement.getAcademicYear(),agreement.getValidFrom()).isEmpty();
    }
    private boolean isLoadAgreement(AdditionalAgreement agreement){
        String content=(firstPresent(agreement.getConditionsJson(),agreement.getSummary())+" "
                +firstPresent(agreement.getSummary())).toLowerCase(Locale.ROOT);
        return agreement.getLoadServiceMemoId()!=null
                ||content.contains("пункт 2.1")
                ||content.contains("пункта 2.1")
                ||content.contains("учебн")&&content.contains("нагруз");
    }
    private int agreementClauseRank(AdditionalAgreement agreement){
        String content=firstPresent(agreement.getConditionsJson(),agreement.getSummary());
        if(content.contains("2.1"))return 1;
        if(content.contains("2.4"))return 2;
        if(content.contains("2.5"))return 3;
        return 4;
    }

    private void configureAgreementPage(XWPFDocument document){
        CTSectPr section=document.getDocument().getBody().isSetSectPr()?document.getDocument().getBody().getSectPr():document.getDocument().getBody().addNewSectPr();CTPageSz size=section.isSetPgSz()?section.getPgSz():section.addNewPgSz();size.setW(BigInteger.valueOf(11906));size.setH(BigInteger.valueOf(16838));
        CTPageMar margin=section.isSetPgMar()?section.getPgMar():section.addNewPgMar();margin.setTop(BigInteger.valueOf(850));margin.setBottom(BigInteger.valueOf(850));margin.setLeft(BigInteger.valueOf(1134));margin.setRight(BigInteger.valueOf(850));margin.setHeader(BigInteger.valueOf(360));margin.setFooter(BigInteger.valueOf(360));margin.setGutter(BigInteger.ZERO);
    }
    private void agreementTitle(XWPFDocument document,String text,int size){XWPFParagraph p=document.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);p.setSpacingAfter(0);agreementRun(p,text,true,size);}
    private void agreementCentered(XWPFDocument document,String text,boolean bold,int size){XWPFParagraph p=document.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);p.setSpacingAfter(0);agreementRun(p,text,bold,size);}
    private void agreementBody(XWPFDocument document,String text,boolean bold){agreementBody(document,text,bold,708);}
    private void agreementBody(XWPFDocument document,String text,boolean bold,int indent){XWPFParagraph p=document.createParagraph();p.setAlignment(ParagraphAlignment.BOTH);p.setIndentationFirstLine(indent);p.setSpacingAfter(0);p.setSpacingBefore(0);p.setSpacingBetween(1.0);agreementRun(p,Objects.toString(text,""),bold,11);}
    private XWPFRun agreementRun(XWPFParagraph paragraph,String text,boolean bold,int size){XWPFRun run=paragraph.createRun();run.setBold(bold);run.setFontFamily("Times New Roman");run.setFontFamily("Times New Roman",XWPFRun.FontCharRange.eastAsia);run.setFontSize(size);appendRunText(run,Objects.toString(text,""));return run;}
    private void addCityAndDate(XWPFDocument document,LocalDate date){XWPFTable table=document.createTable(1,2);table.setWidth("100%");setTableBorders(table,STBorder.NIL);agreementCell(table.getRow(0).getCell(0),"г. Москва",false,ParagraphAlignment.LEFT,11);agreementCell(table.getRow(0).getCell(1),numericDate(date),false,ParagraphAlignment.RIGHT,11);}
    private void addAgreementSignatures(XWPFDocument document,TeacherDirectoryEntry teacher,HrPersonalData personal,LocalDate documentDate){
        XWPFTable table=document.createTable(6,2);configureEqualTwoColumnTable(table,9922);setTableBorders(table,STBorder.SINGLE);
        agreementCell(table.getRow(0).getCell(0),"РАБОТОДАТЕЛЬ",true,ParagraphAlignment.CENTER,10);agreementCell(table.getRow(0).getCell(1),"РАБОТНИК",true,ParagraphAlignment.CENTER,10);
        agreementCell(table.getRow(1).getCell(0),employerLegalName(),false,ParagraphAlignment.CENTER,9);
        agreementCell(table.getRow(1).getCell(1),teacher.getFioTeacher(),true,ParagraphAlignment.CENTER,9);
        agreementCell(table.getRow(2).getCell(0),employerAddresses(),false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(2).getCell(1),"Адрес регистрации: "+personalValue(personal==null?null:personal.getRegistrationAddress())
                +"\nАдрес фактического проживания: "+personalValue(personal==null?null:personal.getActualAddress())
                +"\nТелефон: "+personalValue(personal==null?null:personal.getPhone()),false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(3).getCell(0),employerTaxDetails(),false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(3).getCell(1),"Паспорт гражданина Российской Федерации:\nсерия "
                +personalValue(personal==null?null:personal.getPassportSeries())+" № "
                +personalValue(personal==null?null:personal.getPassportNumber())+" выдан: "
                +personalValue(personal==null?null:personal.getPassportIssuedBy())+" "+formatDate(personal==null?null:personal.getPassportIssueDate())
                +"\nКод подразд.: "+personalValue(personal==null?null:personal.getPassportDepartmentCode()),false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(4).getCell(0),"Директор\n________________ / "+directorInitials()+" /\nМ.П.",false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(4).getCell(1),"________________ / "+teacherInitials(teacher)+" /",false,ParagraphAlignment.CENTER,9);
        agreementCell(table.getRow(5).getCell(0),legalDate(documentDate),false,ParagraphAlignment.LEFT,9);
        agreementCell(table.getRow(5).getCell(1),"«____» ______________ "+(documentDate==null?"20___":documentDate.getYear())+" г.",false,ParagraphAlignment.LEFT,9);
        addAgreementReceiptLine(document);
    }
    private void configureEqualTwoColumnTable(XWPFTable table,int totalWidth){
        int columnWidth=totalWidth/2;
        table.setWidthType(TableWidthType.DXA);table.setWidth(String.valueOf(totalWidth));
        table.setCellMargins(90,90,90,90);
        CTTblPr properties=table.getCTTbl().getTblPr();
        CTTblLayoutType layout=properties.isSetTblLayout()?properties.getTblLayout():properties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        CTTblGrid grid=table.getCTTbl().getTblGrid();if(grid==null)grid=table.getCTTbl().addNewTblGrid();
        while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);
        grid.addNewGridCol().setW(BigInteger.valueOf(columnWidth));
        grid.addNewGridCol().setW(BigInteger.valueOf(columnWidth));
        table.getRows().forEach(row->{
            row.getTableCells().forEach(cell->cell.setWidth(String.valueOf(columnWidth)));
        });
    }
    private void addAgreementReceiptLine(XWPFDocument document){
        XWPFParagraph receipt=document.createParagraph();receipt.setAlignment(ParagraphAlignment.LEFT);
        receipt.setIndentationLeft(708);receipt.setIndentationRight(354);
        receipt.setSpacingBefore(180);receipt.setSpacingAfter(0);receipt.setSpacingBetween(1.0);
        agreementRun(receipt,"Экземпляр дополнительного соглашения получил(а)  ________________________________",false,11);
        XWPFParagraph caption=document.createParagraph();caption.setAlignment(ParagraphAlignment.RIGHT);
        caption.setIndentationRight(850);caption.setSpacingBefore(0);caption.setSpacingAfter(0);
        XWPFRun captionRun=agreementRun(caption,"(дата и подпись работника)",false,9);captionRun.setItalic(true);
    }
    private void agreementCell(XWPFTableCell cell,String text,boolean bold,ParagraphAlignment alignment,int size){cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);XWPFParagraph p=cell.getParagraphs().get(0);p.setAlignment(alignment);p.setSpacingAfter(0);agreementRun(p,text,bold,size);}
    private void setTableBorders(XWPFTable table,STBorder.Enum style){CTTblPr properties=table.getCTTbl().getTblPr();CTTblBorders borders=properties.isSetTblBorders()?properties.getTblBorders():properties.addNewTblBorders();for(CTBorder border:List.of(borders.addNewTop(),borders.addNewLeft(),borders.addNewBottom(),borders.addNewRight(),borders.addNewInsideH(),borders.addNewInsideV())){border.setVal(style);border.setSz(BigInteger.valueOf(style==STBorder.NIL?0:4));border.setColor("000000");}}
    private String teacherInitials(TeacherDirectoryEntry teacher){String[] parts=teacher.getFioTeacher().trim().split("\\s+");return (parts.length>1?parts[1].charAt(0)+".":"")+(parts.length>2?parts[2].charAt(0)+". ":" ")+parts[0];}
    private String directorInitials(){return "1811".equals(SchoolCodeResolver.resolve())?"В.А. Тихонов":"И.Д. Жданова";}
    private String finishSentence(String value){String text=Objects.toString(value,"").trim();return text.isEmpty()?text:text.matches(".*[.!?][»”\"]?$")?text:text+".";}
    private String legalDate(LocalDate date){if(date==null)return "«____» ____________ 20___ г.";String[] months={"января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"};return "«"+String.format("%02d",date.getDayOfMonth())+"» "+months[date.getMonthValue()-1]+" "+date.getYear()+" г.";}
    private String periodDate(LocalDate date){return date==null?"«____» ____________ 20___ года":legalDate(date).replace(" г."," года");}
    private String numericDate(LocalDate date){return date==null?"__.__.20__ г.":formatDate(date)+" г.";}
    private String contractDate(EmploymentContract contract){return contract==null||contract.getContractDate()==null?"__.__.20__ г.":formatDate(contract.getContractDate())+" г.";}
    private String contractNumber(EmploymentContract contract){return contract==null||!present(contract.getContractNumber())?"________________":contract.getContractNumber();}

    private byte[] generateAgreementLegacy(AdditionalAgreement a, EmploymentContract c) {
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
                                     LocalDate from, LocalDate to, boolean separate, String clause,
                                     List<CompensationFunction> compensationFunctions) {
        String period = " с " + formatDate(from) + " по " + formatDate(to);
        String payment = amount == null ? "" : " и установить ежемесячную доплату в размере "
                + money(amount) + " (" + moneyWords(amount) + ")";
        if (separate) {
            return "Прошу Вас согласовать поручение работнику " + teacher.getFioTeacher()
                    + " дополнительной работы «" + required(assignmentName, "Основание или обязанность") + "»"
                    + payment + period + ".";
        }
        if ("2.4".equals(clause)) return clause24Text(compensationFunctions);
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

    private String automaticAgreementText(String assignmentName, String duties, String clause, boolean separate,
                                          BigDecimal amount, List<CompensationFunction> compensationFunctions) {
        if (separate) {
            return "Работнику поручается выполнение дополнительной работы «" + assignmentName
                    + "» без освобождения от основной работы. Дополнительные обязанности: " + firstPresent(duties);
        }
        if ("2.4".equals(clause)) return clause24Text(compensationFunctions);
        return "Изложить пункт " + clause + " трудового договора в части выплаты за увеличение объема работ («"
                + assignmentName + "») в новой редакции.";
    }

    private List<CompensationFunction> compensationFunctions(String academicYear, Long teacherId, String clause,
                                                              boolean separate, String assignmentName, BigDecimal amount,
                                                              LocalDate effectiveDate) {
        if (separate || !"2.4".equals(clause)) return List.of();
        String current=required(assignmentName,"Основание или обязанность");
        return List.of(new CompensationFunction(current,amount,null));
    }

    private List<CompensationFunction> classroomLeadershipFunctions(String academicYear,Long teacherId){
        Map<String,CompensationFunction> functions=new LinkedHashMap<>();
        addClassroomLeadershipFunction(functions,academicYear,teacherId);
        return List.copyOf(functions.values());
    }

    private void addClassroomLeadershipFunction(Map<String,CompensationFunction> functions,String academicYear,Long teacherId){
        List<ClassroomLeadershipEntry> classes=classroomLeadershipRepository.findAllByAcademicYear(academicYear).stream()
                .filter(entry->Objects.equals(entry.getTeacherId(),teacherId))
                .collect(Collectors.toMap(entry->normalizeClass(entry.getClassName()),entry->entry,(left,right)->left,LinkedHashMap::new))
                .values().stream().toList();
        if(classes.isEmpty())return;
        Map<String,Integer> sizes=normalizedClassSizes(classSizeService.effectiveClassSizes(academicYear));
        int students=classes.stream().mapToInt(entry->sizes.getOrDefault(normalizeClass(entry.getClassName()),0)).sum();
        int fundedClasses=Math.min(classes.size(),2);
        BigDecimal federal=BigDecimal.valueOf(5000L*fundedClasses);
        BigDecimal city=BigDecimal.valueOf(500L*students);
        BigDecimal total=federal.add(city);
        String line="возложена функция классного руководителя, в размере 5 000 рублей 00 коп. за 1 класс "
                +"(но не более чем за 2 класса) за счет федерального бюджета и 500 рублей "
                +"за 1 обучающегося в классе за счет бюджета города Москвы - "+moneyLegal(total)
                +" ("+moneyWordsLegal(total)+") в месяц";
        functions.put("классное руководство",new CompensationFunction("Классное руководство",total,line));
    }

    private String clause24Text(List<CompensationFunction> functions) {
        List<CompensationFunction> actual=functions==null?List.of():functions;
        StringBuilder text=new StringBuilder("Внести изменения в пункт 2.4 раздела 2 «Оплата труда», изложив его в следующей редакции:\n")
                .append("«2.4. Работнику выплачиваются ежемесячные компенсационные выплаты при условии, если на Работника:");
        for(int i=0;i<actual.size();i++){
            CompensationFunction function=actual.get(i);
            text.append("\n- ");
            if(present(function.legalLine()))text.append(function.legalLine());
            else text.append("возложена функция «").append(lowercaseInitial(function.name())).append("», в размере ")
                        .append(function.amount()==null
                                ?"________ рублей __ коп. (________________ рублей __ коп.)"
                                :moneyLegal(function.amount())+" ("+moneyWordsLegal(function.amount())+")")
                        .append(" в месяц");
            text.append(i+1<actual.size()?";":"».");
        }
        return text.toString();
    }

    private void refreshAutomaticClause24Memo(HrServiceMemo memo){
        if(memo==null||memo.isSeparateAgreement()
                ||!"2.4".equals(memo.getContractClause()))return;
        String exactClause=clause24Text(compensationFunctions(memo.getAcademicYear(),memo.getTeacherId(),
                "2.4",false,memo.getAssignmentName(),memo.getAmount(),memo.getValidFrom()));
        memo.setAssignmentText(exactClause);
        memo.setAgreementText(exactClause);
    }

    private void applyAutomaticCompensationClause(AdditionalAgreement agreement,Long teacherId){
        if(agreement.getKind()!=AdditionalAgreement.Kind.PAY_TERMS||teacherId==null
                ||agreement.getServiceMemoId()==null)return;
        HrServiceMemo memo=memos.findById(agreement.getServiceMemoId()).orElse(null);
        if(memo==null||memo.isSeparateAgreement()
                ||!"2.4".equals(firstPresent(memo.getContractClause(),"2.4")))return;
        String assignment=firstPresent(memo.getAssignmentName(),agreement.getSummary());
        BigDecimal amount=memo.getAmount()==null?agreement.getTotalAmount():memo.getAmount();
        LocalDate effectiveDate=agreement.getValidFrom()==null?memo.getValidFrom():agreement.getValidFrom();
        Map<String,CompensationFunction> functions=new LinkedHashMap<>();
        if(isAnnualRegistryAgreement(agreement))
            classroomLeadershipFunctions(agreement.getAcademicYear(),teacherId)
                    .forEach(function->functions.put(normalizedFunctionName(function.name()),function));
        compensationFunctions(agreement.getAcademicYear(),teacherId,"2.4",false,assignment,amount,effectiveDate)
                .forEach(function->functions.putIfAbsent(normalizedFunctionName(function.name()),function));
        String exactClause=clause24Text(List.copyOf(functions.values()));
        agreement.setConditionsJson(replaceClause24Block(agreement.getConditionsJson(),exactClause));
    }

    private void repairMisappliedRegistryFunctions(List<AdditionalAgreement> candidates){
        EnumSet<AdditionalAgreement.Status> repairable=EnumSet.of(AdditionalAgreement.Status.WAITING_FOR_MEMO,
                AdditionalAgreement.Status.DRAFT,AdditionalAgreement.Status.READY,
                AdditionalAgreement.Status.REQUIRES_DECISION,AdditionalAgreement.Status.ISSUED,
                AdditionalAgreement.Status.SIGNING);
        candidates.stream()
                .filter(agreement->repairable.contains(agreement.getStatus()))
                .filter(agreement->!isAnnualRegistryAgreement(agreement)&&agreement.getServiceMemoId()!=null)
                .filter(agreement->Objects.toString(agreement.getConditionsJson(),"")
                        .toLowerCase(Locale.ROOT).contains("классного руководителя"))
                .forEach(agreement->{
                    String oldConditions=agreement.getConditionsJson();
                    applyAutomaticCompensationClause(agreement,agreementTeacherId(agreement,null));
                    if(!Objects.equals(oldConditions,agreement.getConditionsJson())){
                        markAgreementContentChanged(agreement);
                        agreements.save(agreement);
                    }
                });
    }

    private void applyAutomaticAnnualClassroomLeadershipClause(AdditionalAgreement agreement,Long teacherId,boolean force){
        if(agreement==null||teacherId==null||agreement.getServiceMemoId()!=null
                ||agreement.getKind()!=AdditionalAgreement.Kind.PAY_TERMS||!isAnnualPeriod(agreement))return;
        if(!force&&!Objects.toString(agreement.getSummary(),"").contains("Классное руководство"))return;
        List<CompensationFunction> functions=classroomLeadershipFunctions(agreement.getAcademicYear(),teacherId);
        String replacement=functions.isEmpty()?"":clause24Text(functions);
        agreement.setConditionsJson(upsertClause24Block(agreement.getConditionsJson(),replacement));
        agreement.setSummary(updateAnnualClassroomSummary(agreement.getSummary(),!functions.isEmpty()));
    }

    private String annualSummary(boolean hasLoad,boolean hasClassroomLeadership,boolean hasIncentive){
        List<String> parts=new ArrayList<>();
        if(hasLoad)parts.add(ANNUAL_LOAD_SUMMARY);
        if(hasClassroomLeadership)parts.add("Классное руководство");
        if(hasIncentive)parts.add("Стимулирующая выплата");
        return String.join(" · ",parts);
    }

    private String updateAnnualClassroomSummary(String current,boolean includeClassroomLeadership){
        String summary=Objects.toString(current,"").trim();
        boolean incentive=summary.contains("Стимулирующая выплата");
        summary=summary.replace(INCENTIVE_SUMMARY_SUFFIX,"").trim();
        summary=summary.replace(CLASSROOM_SUMMARY_SUFFIX,"").trim();
        if("Классное руководство".equals(summary))summary="";
        if("Стимулирующая выплата".equals(summary))summary="";
        if(includeClassroomLeadership)
            summary=summary.isBlank()?"Классное руководство":summary+CLASSROOM_SUMMARY_SUFFIX;
        if(incentive)
            summary=summary.isBlank()?"Стимулирующая выплата":summary+INCENTIVE_SUMMARY_SUFFIX;
        return summary;
    }

    private String upsertClause24Block(String conditions,String replacement){
        List<String> blocks=Arrays.stream(Objects.toString(conditions,"").replace("\r\n","\n")
                        .replace('\r','\n').trim().split("\\n\\s*\\n(?=(?:Внести|Изложить))"))
                .map(String::trim).filter(this::present).filter(block->!isClause24Block(block))
                .collect(Collectors.toCollection(ArrayList::new));
        if(present(replacement))blocks.add(replacement.trim());
        blocks.sort(Comparator.comparingInt(this::conditionClauseRank));
        return String.join("\n\n",blocks);
    }

    private int conditionClauseRank(String block){
        if(block.contains("2.1"))return 1;
        if(block.contains("2.4"))return 2;
        if(block.contains("2.5"))return 3;
        return 4;
    }

    private String replaceClause24Block(String conditions,String replacement){
        String normalized=Objects.toString(conditions,"").replace("\r\n","\n").replace('\r','\n').trim();
        if(normalized.isEmpty())return replacement;
        List<String> result=new ArrayList<>();boolean replaced=false;
        for(String block:normalized.split("\\n\\s*\\n(?=(?:Внести|Изложить))")){
            String current=block.trim();
            if(isClause24Block(current)){
                if(!replaced)result.add(replacement);
                replaced=true;
            }else if(present(current))result.add(current);
        }
        if(!replaced)result.add(replacement);
        return String.join("\n\n",result);
    }

    private boolean isClause24Block(String block){
        return block.contains("пункт 2.4")||block.contains("пункта 2.4")
                ||block.contains("\"2.4.")||block.contains("«2.4.");
    }

    private void applyAutomaticLoadClause(AdditionalAgreement agreement,Long teacherId){
        if(agreement.getKind()!=AdditionalAgreement.Kind.PAY_TERMS||teacherId==null||!isLoadAgreement(agreement))return;
        List<LoadDetail> details=loadDetails(teacherId,agreement.getAcademicYear(),agreement.getValidFrom());
        BigDecimal calculated=calculatedLoadAmount(details);
        if(!details.isEmpty())agreement.setTotalAmount(calculated);
        String conditions=Objects.toString(agreement.getConditionsJson(),"");
        String expanded=conditions;
        String clause=loadClause(details,details.isEmpty()?agreement.getTotalAmount():calculated);
        for(String placeholder:List.of(ANNUAL_LOAD_PLACEHOLDER,LOAD_CHANGE_PLACEHOLDER,LEGACY_LOAD_PLACEHOLDER))
            expanded=expanded.replace(placeholder,clause);
        if(Objects.equals(conditions,expanded)&&!details.isEmpty())
            expanded=updateAutomaticLoadClauseValues(expanded,details,calculated);
        if(!Objects.equals(conditions,expanded))agreement.setConditionsJson(expanded);
    }

    private BigDecimal calculatedLoadAmount(List<LoadDetail> details){
        return Optional.ofNullable(details).orElse(List.of()).stream().map(LoadDetail::amount)
                .reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
    }

    private String updateAutomaticLoadClauseValues(String conditions,List<LoadDetail> details,BigDecimal amount){
        String updated=Objects.toString(conditions,"");
        if(!updated.contains("2.1")||!updated.contains("должностного оклада в размере"))return updated;
        String salary=moneyLegal(amount)+" ("+moneyWordsLegal(amount)+")";
        updated=updated.replaceFirst(
                "(?s)(-\\s*должностного оклада в размере\\s+).*?(\\s+в месяц,\\s*определяемого исходя из учебной нагрузки)",
                "$1"+java.util.regex.Matcher.quoteReplacement(salary)+"$2");
        BigDecimal rate=details.stream().map(LoadDetail::rate).findFirst()
                .orElseGet(()->salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)
                        .map(SalarySettings::getStudentHourRate).orElse(SalarySettings.DEFAULT_STUDENT_HOUR_RATE));
        updated=updated.replaceFirst(
                "(?s)(«ученико-часа»\\s+в размере\\s+).*?(\\s+рубл(?:ь|я|ей))",
                "$1"+java.util.regex.Matcher.quoteReplacement(decimal(rate))+"$2");
        BigDecimal paidHours=details.stream().map(LoadDetail::paidHours).reduce(BigDecimal.ZERO,BigDecimal::add);
        String workload=paidHours.signum()<=0?"___ часов":formatHours(paidHours);
        updated=updated.replaceFirst(
                "(?s)(педагогической нагрузки в размере\\s+).*?(?=»\\.)",
                "$1"+java.util.regex.Matcher.quoteReplacement(workload));
        return withIncludedInRateParagraph(updated,details);
    }

    private String loadClause(List<LoadDetail> details,BigDecimal amount){
        BigDecimal rate=details.stream().map(LoadDetail::rate).findFirst()
                .orElseGet(()->salarySettingsRepository.findById(SalarySettings.DEFAULT_ID)
                        .map(SalarySettings::getStudentHourRate).orElse(SalarySettings.DEFAULT_STUDENT_HOUR_RATE));
        BigDecimal hours=details.stream().map(LoadDetail::paidHours).reduce(BigDecimal.ZERO,BigDecimal::add);
        String salary=amount==null||amount.signum()<=0
                ?"________ рублей __ коп. (________________ рублей __ коп.)"
                :moneyLegal(amount)+" ("+moneyWordsLegal(amount)+")";
        String workload=hours.signum()<=0?"___ часов":formatHours(hours);
        String clause="Внести изменения в пункт 2.1. раздела 2 «Оплата труда», изложив его в следующей редакции:\n"
                +"«2.1. За исполнение трудовых (должностных) обязанностей, предусмотренных должностной инструкцией "
                +"и настоящим Трудовым договором, Работнику выплачивается заработная плата, которая состоит из:\n"
                +"- должностного оклада в размере "+salary+" в месяц, определяемого исходя из учебной нагрузки "
                +"по формуле, установленной в п. 2.3. настоящего договора, на основании «ученико-часа» в размере "
                +shortMoneyLegal(rate)+" и педагогической нагрузки в размере "+workload+"».";
        return withIncludedInRateParagraph(clause,details);
    }

    private String withIncludedInRateParagraph(String clause,List<LoadDetail> details){
        String marker="В педагогическую нагрузку Работника также включено ";
        String cleaned=Objects.toString(clause,"").replaceAll(
                "(?s)\\n?В педагогическую нагрузку Работника также включено .*?отдельно по ученико-часу не оплачивается\\.","");
        Map<String,BigDecimal> byReason=new LinkedHashMap<>();
        Optional.ofNullable(details).orElse(List.of()).stream()
                .filter(detail->detail.includedHours().signum()>0)
                .forEach(detail->byReason.merge(firstPresent(detail.reason(),"внутри должностного оклада"),
                        detail.includedHours(),BigDecimal::add));
        if(byReason.isEmpty())return cleaned;
        String paragraphs=byReason.entrySet().stream().map(entry->
                marker+formatHours(entry.getValue())+", выполнение которых осуществляется в пределах должностного оклада "
                        +"по основанию «"+entry.getKey()+"» и отдельно по ученико-часу не оплачивается.")
                .collect(Collectors.joining("\n"));
        return cleaned+"\n"+paragraphs;
    }

    private String formatHours(BigDecimal value){
        BigDecimal normalized=Optional.ofNullable(value).orElse(BigDecimal.ZERO).stripTrailingZeros();
        String number=normalized.scale()<=0?normalized.toBigInteger().toString():normalized.toPlainString().replace('.',',');
        long integer=normalized.longValue();
        return number+" "+form(integer,"час","часа","часов");
    }

    private String shortMoneyLegal(BigDecimal value){
        BigDecimal amount=Optional.ofNullable(value).orElse(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
        long rubles=amount.longValue();int kopecks=amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String result=String.valueOf(Math.abs(rubles)).replaceAll("\\B(?=(\\d{3})+(?!\\d))"," ")
                +" "+form(rubles,"рубль","рубля","рублей");
        return (amount.signum()<0?"-":"")+result+(kopecks==0?"":" "+String.format("%02d",kopecks)+" коп.");
    }

    private String normalizedFunctionName(String value){
        String normalized=Objects.toString(value,"").trim().toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("\\s+"," ");
        return normalized.contains("классн")&&normalized.contains("руковод")?"классное руководство":normalized;
    }

    private String lowercaseInitial(String value){
        String text=Objects.toString(value,"").trim();
        return text.isEmpty()?text:Character.toLowerCase(text.charAt(0))+text.substring(1);
    }

    private record CompensationFunction(String name,BigDecimal amount,String legalLine){}

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private void appendLoadAnnex(XWPFDocument document,Long teacherId,String year,LocalDate date,
                                 EmploymentContract contract){
        List<LoadDetail> details=loadDetails(teacherId,year,date);
        if(details.isEmpty())return;
        XWPFParagraph pageBreak=document.createParagraph();pageBreak.createRun().addBreak(BreakType.PAGE);
        XWPFParagraph reference=document.createParagraph();reference.setAlignment(ParagraphAlignment.RIGHT);
        agreementRun(reference,"Приложение № 1\nк дополнительному соглашению\nк трудовому договору № "+
                (contract==null?"________":contract.getContractNumber())+" от "+
                (contract==null?"________":legalDate(contract.getContractDate())),false,10);
        appendLoadAnnex(document,details);
        BigDecimal total=calculatedLoadAmount(details);
        agreementBody(document,"Итоговая сумма: "+money(total)+" ("+moneyWords(total)+")",false,0);
    }

    private void appendLoadAnnex(XWPFDocument d, Long teacherId, String year, LocalDate date) {
        List<LoadDetail> rows = loadDetails(teacherId, year, date);
        if (rows.isEmpty()) return;
        appendLoadAnnex(d,rows);
    }

    private void appendLoadAnnex(XWPFDocument d,List<LoadDetail> rows){
        title(d, "Приложение № 1"); paragraph(d, "Расчёт должностного оклада по педагогической нагрузке", true);
        boolean showIncludedHours=rows.stream().anyMatch(x->x.includedHours().signum()>0);
        int[] widths=showIncludedHours
                ?new int[]{1250,1100,650,650,650,900,1000,900,1050,1850}
                :new int[]{1650,1450,800,800,1000,1200,1500,1500};
        XWPFTable table=d.createTable(1,widths.length);configureAnnexTable(table,widths);
        String[] headers=showIncludedHours
                ?new String[]{"Предмет","Класс/группа","Всего","В ставке","К оплате","Численность","Ученико-час","Коэффициенты","Сумма","Пояснение"}
                :new String[]{"Предмет","Класс/группа","Всего","К оплате","Численность","Ученико-час","Коэффициенты","Сумма"};
        for(int i=0;i<headers.length;i++) cell(table.getRow(0).getCell(i),headers[i],true,widths[i]);
        BigDecimal total=BigDecimal.ZERO,totalHours=BigDecimal.ZERO,includedHours=BigDecimal.ZERO,paidHours=BigDecimal.ZERO;
        for(LoadDetail x:rows){
            XWPFTableRow r=table.createRow();
            String note=x.includedHours().signum()>0?"Внутри ставки — "+firstPresent(x.reason(),"должностной оклад"):"";
            String coefficients=decimal(x.subjectCoefficient())+" / "+decimal(x.groupCoefficient().setScale(4,RoundingMode.HALF_UP));
            String[] v=showIncludedHours
                    ?new String[]{x.subject(),x.className(),decimal(x.totalHours()),decimal(x.includedHours()),decimal(x.paidHours()),
                    String.valueOf(x.children()),decimal(x.rate()),coefficients,money(x.amount()).replace(" руб.",""),note}
                    :new String[]{x.subject(),x.className(),decimal(x.totalHours()),decimal(x.paidHours()),
                    String.valueOf(x.children()),decimal(x.rate()),coefficients,money(x.amount()).replace(" руб.","")};
            for(int i=0;i<v.length;i++)cell(r.getCell(i),v[i],false,widths[i]);
            total=total.add(x.amount());totalHours=totalHours.add(x.totalHours());
            includedHours=includedHours.add(x.includedHours());paidHours=paidHours.add(x.paidHours());
        }
        XWPFTableRow tr=table.createRow();
        String[] totalValues=showIncludedHours
                ?new String[]{"Итого","",decimal(totalHours),decimal(includedHours),decimal(paidHours),"","","",
                money(total).replace(" руб.",""),""}
                :new String[]{"Итого","",decimal(totalHours),decimal(paidHours),"","","",
                money(total).replace(" руб.","")};
        for(int i=0;i<totalValues.length;i++){
            boolean bold=i==0||i==2||i==3||(showIncludedHours&&i==4)||i==(showIncludedHours?8:7);
            cell(tr.getCell(i),totalValues[i],bold,widths[i]);
        }
    }
    private void configureAnnexTable(XWPFTable table,int[] widths){
        int total=Arrays.stream(widths).sum();table.setWidthType(TableWidthType.DXA);table.setWidth(String.valueOf(total));table.setCellMargins(80,70,80,70);
        CTTblGrid grid=table.getCTTbl().getTblGrid();if(grid==null)grid=table.getCTTbl().addNewTblGrid();
        while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);
        for(int width:widths)grid.addNewGridCol().setW(BigInteger.valueOf(width));
    }
    private void cell(XWPFTableCell c,String s,boolean bold,int width){
        c.setWidth(String.valueOf(width));c.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph p=c.getParagraphs().get(0);p.setAlignment(ParagraphAlignment.CENTER);p.setSpacingBefore(0);p.setSpacingAfter(0);
        XWPFRun r=p.createRun();r.setFontFamily("Times New Roman");r.setFontFamily("Times New Roman",XWPFRun.FontCharRange.eastAsia);
        r.setFontSize(bold?8:9);r.setBold(bold);appendRunText(r,s);
    }
    private List<LoadDetail> loadDetails(Long teacherId,String year,LocalDate date){
        List<ManualLoadEntry> rows=loadRepository.findAllByAcademicYear(year).stream()
                .filter(row->Objects.equals(row.getTeacherId(),teacherId))
                .filter(row->date==null||row.getLoadFromDate()==null||!row.getLoadFromDate().isAfter(date))
                .filter(row->date==null||row.getLoadToDate()==null||!row.getLoadToDate().isBefore(date))
                .toList();
        Map<Long,LoadSalaryCalculationService.SalaryLine> calculated=loadSalaryCalculationService.calculate(year,rows);
        return rows.stream().map(row->{
            LoadSalaryCalculationService.SalaryLine line=calculated.get(row.getId());
            if(line==null)line=loadSalaryCalculationService.calculate(year,row);
            String group=Objects.toString(row.getGroupNameEducationalPlan(),"");
            return new LoadDetail(row.getSubjectName(),row.getClassName()+(!group.isBlank()?" "+group:""),
                    line.totalHours(),line.includedHours(),line.paidHours(),line.children(),line.studentHourRate(),
                    line.subjectCoefficient(),line.groupCoefficient(),line.amount(),firstPresent(row.getInRateReason(),"внутри ставки"));
        }).toList();
    }
    private Map<String,Integer> normalizedClassSizes(Map<String,Integer> sizes){
        Map<String,Integer> normalized=new LinkedHashMap<>();
        if(sizes!=null)sizes.forEach((className,count)->normalized.put(normalizeClass(className),count==null?0:count));
        return normalized;
    }
    private String normalizeClass(String s){return ClassNameNormalizer.normalize(s).toLowerCase(Locale.ROOT)
            .replace('ё','е').replaceAll("\\s+","").replace('–','-').replace('—','-');}
    private EducationStage stage(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+").matcher(Objects.toString(s,""));if(!m.find())return EducationStage.OOO;int g=Integer.parseInt(m.group());return g<=4?EducationStage.NOO:g<=9?EducationStage.OOO:EducationStage.SOO;}
    private record LoadDetail(String subject,String className,BigDecimal totalHours,BigDecimal includedHours,
                              BigDecimal paidHours,int children,BigDecimal rate,BigDecimal subjectCoefficient,
                              BigDecimal groupCoefficient,BigDecimal amount,String reason){}
    private String money(BigDecimal v){
        BigDecimal value=v.setScale(2,RoundingMode.HALF_UP);String[] parts=value.abs().toPlainString().split("\\.");
        String grouped=parts[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))"," ");
        return (value.signum()<0?"-":"")+grouped+","+parts[1]+" руб.";
    }
    private String moneyLegal(BigDecimal value){
        BigDecimal amount=value.setScale(2,RoundingMode.HALF_UP);long rubles=amount.longValue();
        int kopecks=amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String grouped=String.valueOf(Math.abs(rubles)).replaceAll("\\B(?=(\\d{3})+(?!\\d))"," ");
        return (amount.signum()<0?"-":"")+grouped+" "+form(rubles,"рубль","рубля","рублей")+" "
                +String.format("%02d",kopecks)+" коп.";
    }
    private String moneyWordsLegal(BigDecimal value){
        BigDecimal amount=value.setScale(2,RoundingMode.HALF_UP);long rubles=amount.longValue();
        int kopecks=amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        return numberWords(rubles)+" "+form(rubles,"рубль","рубля","рублей")+" "+String.format("%02d",kopecks)+" коп.";
    }
    private String decimal(BigDecimal value){return value.stripTrailingZeros().toPlainString().replace('.',',');}
    private String firstPresent(String... values){for(String value:values)if(present(value))return value.trim();return "";}
    private String standardContractClause(String requested,String catalogValue){
        String clause=firstPresent(requested,catalogValue,"2.4");
        if(!STANDARD_CONTRACT_CLAUSES.contains(clause))
            throw new ResponseStatusException(BAD_REQUEST,"Выберите пункт трудового договора: 2.1, 2.4 или 2.5");
        return clause;
    }
    private String employerIntro(){return employerLegalName()+", именуемое в дальнейшем «Работодатель», в лице директора "
            +("1811".equals(SchoolCodeResolver.resolve())?"Тихонова Валерия Анатольевича":"Ждановой Ирины Дмитриевны");}
    private String employerAuthorityPhrase(){return "1811".equals(SchoolCodeResolver.resolve())?"действующего":"действующей";}
    private String employerDetails(){return "1811".equals(SchoolCodeResolver.resolve())?"Работодатель: ГБОУ Школа № 1811 «Восточное Измайлово», 105203, г. Москва, ул. Первомайская, д. 109/2, ИНН/КПП 7719894832/771901001":"Работодатель: ГБОУ Школа № 7, 119331, г. Москва, ул. Крупской, д. 17, ИНН 7736050780";}
    private String employerLegalName(){return "1811".equals(SchoolCodeResolver.resolve())
            ?"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 1811 «Восточное Измайлово»» (ГБОУ Школа № 1811 «Восточное Измайлово»)"
            :"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 7» (ГБОУ Школа № 7)";}
    private String employerAddresses(){return "1811".equals(SchoolCodeResolver.resolve())
            ?"Юридический адрес: 105203, г. Москва, ул. Первомайская, д. 109/2\nФактический адрес: 105203, г. Москва, ул. Первомайская, д. 109/2"
            :"Юридический адрес: 119331, Москва, ул. Крупской, д. 17\nФактический адрес: 119331, Москва, ул. Крупской, д. 17";}
    private String employerTaxDetails(){return "1811".equals(SchoolCodeResolver.resolve())
            ?"ИНН: 7719894832\nКПП: 771901001":"ИНН: 7736050780";}
    private String personalValue(String value){return present(value)?value:"________________";}
    private String moneyWords(BigDecimal v){BigDecimal x=v.setScale(2,RoundingMode.HALF_UP); long rub=x.longValue(); int kop=x.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue(); return numberWords(rub)+" "+form(rub,"рубль","рубля","рублей")+" "+String.format("%02d",kop)+" "+form(kop,"копейка","копейки","копеек");}
    private String form(long n,String a,String b,String c){long m=Math.abs(n)%100;if(m>=11&&m<=14)return c;return switch((int)(Math.abs(n)%10)){case 1->a;case 2,3,4->b;default->c;};}
    private String numberWords(long n){if(n==0)return "ноль"; if(n<0)return "минус "+numberWords(-n); String[] ones={"","один","два","три","четыре","пять","шесть","семь","восемь","девять","десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать"};String[] tens={"","","двадцать","тридцать","сорок","пятьдесят","шестьдесят","семьдесят","восемьдесят","девяносто"};String[] hundreds={"","сто","двести","триста","четыреста","пятьсот","шестьсот","семьсот","восемьсот","девятьсот"};StringBuilder s=new StringBuilder();long[] div={1000000000,1000000,1000,1};String[][] forms={{"миллиард","миллиарда","миллиардов"},{"миллион","миллиона","миллионов"},{"тысяча","тысячи","тысяч"},{"","",""}};for(int i=0;i<div.length;i++){int g=(int)((n/div[i])%1000);if(g==0)continue;if(s.length()>0)s.append(' ');s.append(hundreds[g/100]);int r=g%100;if(r>0){if(s.length()>0&&s.charAt(s.length()-1)!=' ')s.append(' ');if(r<20)s.append(ones[r]);else{s.append(tens[r/10]);if(r%10>0)s.append(' ').append(i==2&&r%10==1?"одна":i==2&&r%10==2?"две":ones[r%10]);}}if(i<3)s.append(' ').append(form(g,forms[i][0],forms[i][1],forms[i][2]));}return s.toString().trim();}
}
