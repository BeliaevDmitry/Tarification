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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class HrDocumentService {
    private final EmploymentContractRepository contracts;
    private final HrPersonalDataRepository personalData;
    private final HrServiceMemoRepository memos;
    private final AdditionalAgreementRepository agreements;
    private final HrDocumentVersionRepository versions;
    private final TeacherDirectoryRepository teachers;
    private final ManualLoadEntryRepository loadRepository;
    private final SalarySettingsRepository salarySettingsRepository;
    private final SubjectLevelCoefficientRepository coefficientRepository;
    private final SalaryGroupCoefficientSubjectRepository groupSubjectRepository;
    private final ClassSizeService classSizeService;
    private final ObjectMapper objectMapper;

    public List<JournalRow> journal(String academicYear) {
        Map<Long,List<AdditionalAgreement>> byContract = agreements.findAllByAcademicYearOrderByCreatedAtDesc(academicYear)
                .stream().collect(Collectors.groupingBy(AdditionalAgreement::getContractId));
        return contracts.findAllByActiveTrueOrderByTeacherIdAsc().stream().map(c -> {
            TeacherDirectoryEntry teacher = teachers.findById(c.getTeacherId()).orElse(null);
            boolean complete = personalData.findByTeacherId(c.getTeacherId()).map(this::isComplete).orElse(false);
            List<AgreementView> docs = byContract.getOrDefault(c.getId(), List.of()).stream().map(this::view).toList();
            String action = !complete ? "Заполнить данные" : docs.stream().anyMatch(a -> a.status().equals("REQUIRES_DECISION"))
                    ? "Требуется решение" : docs.isEmpty() ? "Создать черновик" : "";
            return new JournalRow(c.getTeacherId(), teacher == null ? "" : teacher.getFioTeacher(), c.getId(),
                    c.getContractNumber(), c.getPositionName(), complete, docs, action);
        }).sorted(Comparator.comparing(JournalRow::fio, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional public EmploymentContract saveContract(Long id, ContractRequest r) {
        teachers.findById(r.teacherId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Педагог не найден"));
        EmploymentContract c = id == null ? new EmploymentContract() : contracts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        c.setTeacherId(r.teacherId()); c.setContractNumber(required(r.contractNumber(), "Номер договора"));
        c.setContractDate(Objects.requireNonNull(r.contractDate(), "Дата договора обязательна"));
        c.setPositionName(required(r.positionName(), "Должность")); c.setStartDate(r.startDate()); c.setEndDate(r.endDate());
        c.setPrimaryContract(r.primaryContract() == null || r.primaryContract()); c.setActive(r.active() == null || r.active());
        return contracts.save(c);
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
        HrServiceMemo m = new HrServiceMemo(); m.setAcademicYear(required(r.academicYear(), "Учебный год"));
        m.setTitle(required(r.title(), "Название")); m.setDocumentDate(r.documentDate()); m.setItemsJson(r.itemsJson());
        m.setCreatedBy(username); m.setDocumentFilename("Служебная_записка.docx"); m.setDocumentContent(generateMemo(m));
        return memos.save(m);
    }
    public List<HrServiceMemo> memos(String year) { return memos.findAllByAcademicYearOrderByCreatedAtDesc(year); }

    @Transactional public HrServiceMemo memoStatus(Long id, HrServiceMemo.Status status, String username) {
        HrServiceMemo m = memo(id);
        if (m.getStatus() == HrServiceMemo.Status.ANNULLED) throw new ResponseStatusException(CONFLICT, "Служебка аннулирована");
        m.setStatus(status);
        if (status == HrServiceMemo.Status.RECEIVED_BY_HR) { m.setReceivedAt(LocalDateTime.now()); m.setReceivedBy(username); }
        return memos.save(m);
    }
    @Transactional public HrServiceMemo annulMemo(Long id, String reason, String username) {
        HrServiceMemo m = memo(id); m.setStatus(HrServiceMemo.Status.ANNULLED); m.setAnnulReason(required(reason,"Причина"));
        m.setAnnulledAt(LocalDateTime.now()); m.setAnnulledBy(username); memos.save(m);
        agreements.findAll().stream().filter(a -> Objects.equals(a.getServiceMemoId(), id))
                .filter(a -> a.getStatus() != AdditionalAgreement.Status.ANNULLED)
                .forEach(a -> { a.setStatus(AdditionalAgreement.Status.REQUIRES_DECISION); agreements.save(a); });
        return m;
    }

    @Transactional public AdditionalAgreement createAgreement(AgreementRequest r, String username) {
        EmploymentContract c = contracts.findById(r.contractId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND,"Договор не найден"));
        AdditionalAgreement old = r.replacesAgreementId() == null ? null : agreement(r.replacesAgreementId());
        AdditionalAgreement a = new AdditionalAgreement(); a.setContractId(c.getId()); a.setServiceMemoId(r.serviceMemoId());
        a.setAcademicYear(required(r.academicYear(),"Учебный год")); a.setDocumentDate(r.documentDate());
        a.setValidFrom(Objects.requireNonNull(r.validFrom(),"Начало периода обязательно"));
        a.setValidTo(Objects.requireNonNull(r.validTo(),"Конец периода обязателен"));
        a.setKind(r.kind() == null ? AdditionalAgreement.Kind.PAY_TERMS : r.kind());
        a.setChangeMode(r.changeMode() == null ? AdditionalAgreement.ChangeMode.AMEND : r.changeMode());
        a.setSummary(r.summary()); a.setConditionsJson(r.conditionsJson()); a.setTotalAmount(r.totalAmount()); a.setCreatedBy(username);
        if (a.getKind() == AdditionalAgreement.Kind.PAY_TERMS && a.getTotalAmount() == null) {
            BigDecimal calculated = loadDetails(c.getTeacherId(), a.getAcademicYear(), a.getValidFrom()).stream()
                    .map(LoadDetail::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            if (calculated.signum() > 0) a.setTotalAmount(calculated);
        }
        if (old != null && old.getStatus() == AdditionalAgreement.Status.ANNULLED) {
            a.setInternalNumber(old.getInternalNumber()); a.setVisibleNumber(old.getVisibleNumber()); a.setRevision(old.getRevision() + 1); a.setReplacesAgreementId(old.getId());
        } else {
            a.setInternalNumber(nextNumber(c, r.academicYear()));
            if ("1811".equals(SchoolCodeResolver.resolve())) a.setVisibleNumber(a.getInternalNumber());
        }
        HrPersonalData snapshotData = personalData.findByTeacherId(c.getTeacherId()).orElse(null);
        if (snapshotData != null) a.setPersonalDataSnapshotJson(json(snapshotData));
        a.setSourceSnapshotJson(json(Map.of("contractId", c.getId(), "createdAt", LocalDateTime.now().toString())));
        byte[] doc = generateAgreement(a, c); a.setGeneratedDocument(doc); a.setCurrentDocument(doc);
        a.setCurrentFilename("Дополнительное_соглашение_" + a.getInternalNumber().replace('/','-') + ".docx");
        a = agreements.save(a); saveVersion(a, doc, a.getCurrentFilename(), "GENERATED", username); return a;
    }

    @Transactional public List<AdditionalAgreement> createAnnualDrafts(BatchAgreementRequest r, String username) {
        int startYear=Integer.parseInt(r.academicYear().substring(0,4));LocalDate from=r.validFrom()==null?firstWorkingDay(LocalDate.of(startYear,9,1)):r.validFrom();LocalDate to=LocalDate.of(startYear+1,8,31);
        Set<Long> requested=r.contractIds()==null?Set.of():new HashSet<>(r.contractIds());List<AdditionalAgreement> result=new ArrayList<>();
        for(EmploymentContract c:contracts.findAllByActiveTrueOrderByTeacherIdAsc()){
            if(!c.isPrimaryContract()||(!requested.isEmpty()&&!requested.contains(c.getId())))continue;
            boolean hasLoad=loadRepository.findAllByAcademicYear(r.academicYear()).stream().anyMatch(x->Objects.equals(x.getTeacherId(),c.getTeacherId()));if(!hasLoad)continue;
            result.add(createAgreement(new AgreementRequest(c.getId(),r.serviceMemoId(),r.academicYear(),r.documentDate(),from,to,AdditionalAgreement.Kind.PAY_TERMS,AdditionalAgreement.ChangeMode.AMEND,"Нагрузка и должностной оклад","Изложить пункт 2.1 раздела «Оплата труда» в новой редакции согласно Приложению № 1.",null,null),username));
        }return result;
    }
    private LocalDate firstWorkingDay(LocalDate d){while(d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY)d=d.plusDays(1);return d;}

    @Transactional public AdditionalAgreement issue(Long id, String username) {
        AdditionalAgreement a = agreement(id); EmploymentContract c = contracts.findById(a.getContractId()).orElseThrow();
        if (!personalData.findByTeacherId(c.getTeacherId()).map(this::isComplete).orElse(false))
            throw new ResponseStatusException(CONFLICT,"Заполните обязательные персональные данные");
        if (a.getServiceMemoId() != null && memo(a.getServiceMemoId()).getStatus() != HrServiceMemo.Status.RECEIVED_BY_HR
                && memo(a.getServiceMemoId()).getStatus() != HrServiceMemo.Status.EXECUTED)
            throw new ResponseStatusException(CONFLICT,"Служебная записка не получена кадрами");
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
        a.setStatus(status); return agreements.save(a);
    }
    public AdditionalAgreement agreement(Long id) { return agreements.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    public HrServiceMemo memo(Long id) { return memos.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
    public List<HrDocumentVersion> versions(Long id) { return versions.findAllByDocumentTypeAndDocumentIdOrderByRevisionDesc("AGREEMENT",id); }

    private void saveVersion(AdditionalAgreement a, byte[] content, String filename, String source, String user) {
        HrDocumentVersion v=new HrDocumentVersion(); v.setDocumentType("AGREEMENT"); v.setDocumentId(a.getId()); v.setRevision(a.getRevision());
        v.setContent(content); v.setFilename(filename); v.setSource(source); v.setCreatedBy(user); versions.save(v);
    }
    private String nextNumber(EmploymentContract c, String year) {
        long n = agreements.countByContractIdAndAcademicYear(c.getId(), year) + 1;
        return "7".equals(SchoolCodeResolver.resolve()) ? n + " / " + year.replace('/','-') : String.valueOf(n);
    }
    private AgreementView view(AdditionalAgreement a) { return new AgreementView(a.getId(),a.getInternalNumber(),a.getVisibleNumber(),a.getRevision(),a.getKind().name(),a.getStatus().name(),a.getValidFrom(),a.getValidTo(),a.getSummary(),a.getTotalAmount(),a.getServiceMemoId(),a.getIssuedAt()); }
    private boolean isComplete(HrPersonalData p) { return present(p.getPassportSeries()) && present(p.getPassportNumber()) && present(p.getPassportIssuedBy()) && p.getPassportIssueDate()!=null && present(p.getPassportDepartmentCode()) && present(p.getRegistrationAddress()); }
    private boolean present(String s){return s!=null&&!s.isBlank();} private String required(String s,String name){if(!present(s))throw new ResponseStatusException(BAD_REQUEST,name+" обязательно");return s.trim();}
    private String json(Object x){try{return objectMapper.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}

    private byte[] generateMemo(HrServiceMemo m) {
        try(XWPFDocument d=new XWPFDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            title(d,"СЛУЖЕБНАЯ ЗАПИСКА"); paragraph(d,m.getTitle(),true); paragraph(d,"Дата: "+String.valueOf(m.getDocumentDate()),false);
            paragraph(d,"Назначения:",true); paragraph(d,Objects.toString(m.getItemsJson(),""),false); d.write(out); return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private byte[] generateAgreement(AdditionalAgreement a, EmploymentContract c) {
        TeacherDirectoryEntry t=teachers.findById(c.getTeacherId()).orElseThrow(); HrPersonalData p=personalData.findByTeacherId(c.getTeacherId()).orElse(null);
        try(XWPFDocument d=new XWPFDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            String number=a.getVisibleNumber()==null?"": " № "+a.getVisibleNumber(); title(d,"ДОПОЛНИТЕЛЬНОЕ СОГЛАШЕНИЕ"+number);
            paragraph(d,"к трудовому договору № "+c.getContractNumber()+" от "+c.getContractDate(),true);
            paragraph(d,"г. Москва                                      "+Objects.toString(a.getDocumentDate(),""),false);
            paragraph(d,employerIntro()+" и "+t.getFioTeacher()+", занимающий(ая) должность «"+c.getPositionName()+"», заключили настоящее дополнительное соглашение.",false);
            if(a.getChangeMode()==AdditionalAgreement.ChangeMode.CANCEL_AND_RESTATE && a.getReplacesAgreementId()!=null) paragraph(d,"Отменить с "+a.getValidFrom()+" действие предыдущего дополнительного соглашения и считать условия действующими в настоящей редакции.",false);
            paragraph(d,Objects.toString(a.getConditionsJson(),a.getSummary()),false);
            if(a.getTotalAmount()!=null) paragraph(d,"Ежемесячная сумма: "+money(a.getTotalAmount())+" ("+moneyWords(a.getTotalAmount())+").",false);
            paragraph(d,"Срок действия: с "+a.getValidFrom()+" по "+a.getValidTo()+".",false);
            if (a.getKind() == AdditionalAgreement.Kind.PAY_TERMS) appendLoadAnnex(d, c.getTeacherId(), a.getAcademicYear(), a.getValidFrom());
            paragraph(d,"Реквизиты работника: паспорт "+(p==null?"":Objects.toString(p.getPassportSeries(),"")+" "+Objects.toString(p.getPassportNumber(),""))+
                    ", выдан "+(p==null?"":Objects.toString(p.getPassportIssuedBy(),""))+", адрес регистрации: "+(p==null?"":Objects.toString(p.getRegistrationAddress(),"")),false);
            paragraph(d,employerDetails(),false); paragraph(d,"Работодатель ____________________     Работник ____________________",false); d.write(out); return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void title(XWPFDocument d,String s){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);XWPFRun r=p.createRun();r.setBold(true);r.setFontFamily("Times New Roman");r.setFontSize(14);r.setText(s);}
    private void paragraph(XWPFDocument d,String s,boolean bold){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.BOTH);XWPFRun r=p.createRun();r.setBold(bold);r.setFontFamily("Times New Roman");r.setFontSize(12);r.setText(Objects.toString(s,""));}

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
    private String money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP).toPlainString()+" руб.";}
    private String employerIntro(){return "1811".equals(SchoolCodeResolver.resolve())?"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 1811 «Восточное Измайлово», именуемое «Работодатель», в лице директора Тихонова Валерия Анатольевича":"Государственное бюджетное общеобразовательное учреждение города Москвы «Школа № 7», именуемое «Работодатель», в лице директора Ждановой Ирины Дмитриевны";}
    private String employerDetails(){return "1811".equals(SchoolCodeResolver.resolve())?"Работодатель: ГБОУ Школа № 1811 «Восточное Измайлово», 105203, г. Москва, ул. Первомайская, д. 109/2, ИНН/КПП 7719894832/771901001":"Работодатель: ГБОУ Школа № 7, 119331, г. Москва, ул. Крупской, д. 17, ИНН 7736050780";}
    private String moneyWords(BigDecimal v){BigDecimal x=v.setScale(2,RoundingMode.HALF_UP); long rub=x.longValue(); int kop=x.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue(); return numberWords(rub)+" "+form(rub,"рубль","рубля","рублей")+" "+String.format("%02d",kop)+" "+form(kop,"копейка","копейки","копеек");}
    private String form(long n,String a,String b,String c){long m=Math.abs(n)%100;if(m>=11&&m<=14)return c;return switch((int)(Math.abs(n)%10)){case 1->a;case 2,3,4->b;default->c;};}
    private String numberWords(long n){if(n==0)return "ноль"; if(n<0)return "минус "+numberWords(-n); String[] ones={"","один","два","три","четыре","пять","шесть","семь","восемь","девять","десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать"};String[] tens={"","","двадцать","тридцать","сорок","пятьдесят","шестьдесят","семьдесят","восемьдесят","девяносто"};String[] hundreds={"","сто","двести","триста","четыреста","пятьсот","шестьсот","семьсот","восемьсот","девятьсот"};StringBuilder s=new StringBuilder();long[] div={1000000000,1000000,1000,1};String[][] forms={{"миллиард","миллиарда","миллиардов"},{"миллион","миллиона","миллионов"},{"тысяча","тысячи","тысяч"},{"","",""}};for(int i=0;i<div.length;i++){int g=(int)((n/div[i])%1000);if(g==0)continue;if(s.length()>0)s.append(' ');s.append(hundreds[g/100]);int r=g%100;if(r>0){if(s.charAt(s.length()-1)!=' ')s.append(' ');if(r<20)s.append(ones[r]);else{s.append(tens[r/10]);if(r%10>0)s.append(' ').append(i==2&&r%10==1?"одна":i==2&&r%10==2?"две":ones[r%10]);}}if(i<3)s.append(' ').append(form(g,forms[i][0],forms[i][1],forms[i][2]));}return s.toString().trim();}
}
