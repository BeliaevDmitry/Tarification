package org.school.personalLoad.controller.api;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.auth.*;
import org.school.personalLoad.dto.HrDocumentDtos.*;
import org.school.personalLoad.model.*;
import org.school.personalLoad.repository.*;
import org.school.personalLoad.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/hr-documents")
@RequiredArgsConstructor
public class HrDocumentsController {
    private final HrDocumentService service;
    private final ServiceMemoService loadMemoService;
    private final HrPersonalDataRepository personalRepository;
    private final TeacherDirectoryRepository teacherRepository;
    private final HrCatalogItemRepository catalogRepository;
    private final UserActionLogService audit;

    @GetMapping("/teachers") public Object teachers(){return teacherRepository.findAll().stream().filter(t->!t.isArchived()).map(t->Map.of("id",t.getId(),"fio",t.getFioTeacher())).toList();}

    @GetMapping("/journal") public Object journal(@RequestParam String academicYear,HttpServletRequest req){boolean allowed=user(req).canViewTab(AppTab.HR_PERSONAL_DATA);return service.journal(academicYear).stream().map(x->allowed?x:new JournalRow(x.teacherId(),x.fio(),x.contractId(),x.contractNumber(),x.position(),true,x.agreements(),"Заполнить данные".equals(x.actionRequired())?"":x.actionRequired())).toList();}
    @GetMapping("/contracts") public Object contracts(@RequestParam Long teacherId){return service.contracts(teacherId);}
    @PostMapping("/contracts") public Object contract(@RequestBody ContractRequest r){return service.saveContract(null,r);}
    @PutMapping("/contracts/{id}") public Object contract(@PathVariable Long id,@RequestBody ContractRequest r){return service.saveContract(id,r);}

    @GetMapping("/personal-data/{teacherId}") public Object personal(@PathVariable Long teacherId){return service.personal(teacherId).orElse(null);}
    @PutMapping("/personal-data/{teacherId}") public Object personal(@PathVariable Long teacherId,@RequestBody PersonalDataRequest r,HttpServletRequest req){
        if(!Objects.equals(teacherId,r.teacherId())) throw new IllegalArgumentException("ID педагога не совпадает");
        return service.savePersonal(r,user(req).getUsername());
    }

    @GetMapping("/personal-data/template") public ResponseEntity<byte[]> personalTemplate(HttpServletRequest req)throws Exception{return excel(false,req);}
    @GetMapping("/personal-data/export") public ResponseEntity<byte[]> personalExport(HttpServletRequest req)throws Exception{return excel(true,req);}
    @PostMapping("/personal-data/import") public Map<String,Integer> personalImport(@RequestParam("file") MultipartFile file,HttpServletRequest req)throws Exception{
        int updated=0, skipped=0; String username=user(req).getUsername();
        try(Workbook wb=WorkbookFactory.create(file.getInputStream())){
            Sheet sh=wb.getSheetAt(0);
            for(int i=1;i<=sh.getLastRowNum();i++){
                Row row=sh.getRow(i); if(row==null)continue; Long teacherId=longCell(row.getCell(0)); if(teacherId==null){skipped++;continue;}
                if(teacherRepository.findById(teacherId).isEmpty()){skipped++;continue;}
                PersonalDataRequest p=new PersonalDataRequest(teacherId,null,str(row,2),str(row,3),str(row,4),date(row,5),str(row,6),str(row,7),str(row,8),str(row,9),str(row,10),str(row,11));
                service.savePersonal(p,username);updated++;
                String contractNumber=str(row,12), position=str(row,14);
                if(contractNumber!=null&&!contractNumber.isBlank()&&position!=null&&!position.isBlank()){
                    EmploymentContract existing=service.contracts(teacherId).stream().filter(c->contractNumber.equalsIgnoreCase(c.getContractNumber())).findFirst().orElse(null);
                    ContractRequest cr=new ContractRequest(teacherId,contractNumber,date(row,13),position,date(row,15),date(row,16),true,true);
                    service.saveContract(existing==null?null:existing.getId(),cr);
                }
            }
        }
        log(req,"IMPORT","HR_PERSONAL_DATA","Обновлено: "+updated+", пропущено: "+skipped,true); return Map.of("updated",updated,"skipped",skipped);
    }

    @GetMapping("/memos") public Object memos(@RequestParam String academicYear){return service.memos(academicYear);}
    @GetMapping("/load-memos") public Object loadMemos(@RequestParam String academicYear){return loadMemoService.findForHr(academicYear);}
    @PostMapping("/memos") public Object memo(@RequestBody MemoRequest r,HttpServletRequest req){return service.memoView(service.createMemo(r,user(req).getUsername()));}
    @PostMapping("/memos/{id}/status") public Object memoStatus(@PathVariable Long id,@RequestBody StatusRequest r,HttpServletRequest req){return service.memoView(service.memoStatus(id,HrServiceMemo.Status.valueOf(r.status()),user(req).getUsername()));}
    @PostMapping("/memos/{id}/annul") public Object memoAnnul(@PathVariable Long id,@RequestBody AnnulRequest r,HttpServletRequest req){return service.memoView(service.annulMemo(id,r.reason(),user(req).getUsername()));}
    @GetMapping("/memos/{id}/download") public ResponseEntity<byte[]> memoDownload(@PathVariable Long id){HrServiceMemo m=service.memo(id);return file(m.getDocumentContent(),m.getDocumentFilename());}
    @PostMapping("/load-memos/{id}/receive") public Object loadMemoReceive(@PathVariable Long id,HttpServletRequest req){ServiceMemo m=loadMemoService.receiveByHr(id,user(req).getUsername());return Map.of("id",m.getId(),"status",m.getStatus().name());}
    @PostMapping("/load-memos/{id}/annul") public Object loadMemoAnnul(@PathVariable Long id,@RequestBody AnnulRequest r,HttpServletRequest req){ServiceMemo m=loadMemoService.annul(id,r.reason(),user(req).getUsername());return Map.of("id",m.getId(),"status",m.getStatus().name());}
    @GetMapping("/load-memos/{id}/download") public ResponseEntity<byte[]> loadMemoDownload(@PathVariable Long id){ServiceMemo m=loadMemoService.getById(id);boolean corrected=m.getCorrectedDocument()!=null;return file(corrected?m.getCorrectedDocument():m.getGeneratedDocument(),corrected?m.getCorrectedFilename():m.getGeneratedFilename());}

    @PostMapping("/agreements") public Object agreement(@RequestBody AgreementRequest r,HttpServletRequest req){return service.createAgreement(r,user(req).getUsername());}
    @PostMapping("/agreements/batch-annual") public Object annual(@RequestBody BatchAgreementRequest r,HttpServletRequest req){return service.createAnnualDrafts(r,user(req).getUsername());}
    @PostMapping("/agreements/{id}/issue") public Object issue(@PathVariable Long id,HttpServletRequest req){return service.issue(id,user(req).getUsername());}
    @PostMapping("/agreements/{id}/status") public Object agreementStatus(@PathVariable Long id,@RequestBody StatusRequest r){return service.agreementStatus(id,AdditionalAgreement.Status.valueOf(r.status()));}
    @PostMapping("/agreements/{id}/change-mode") public Object agreementChangeMode(@PathVariable Long id,@RequestBody ChangeModeRequest r,HttpServletRequest req){return service.chooseChangeMode(id,AdditionalAgreement.ChangeMode.valueOf(r.changeMode()),r.replacesAgreementId(),user(req).getUsername());}
    @PostMapping("/agreements/{id}/annul") public Object agreementAnnul(@PathVariable Long id,@RequestBody AnnulRequest r,HttpServletRequest req){return service.annulAgreement(id,r.reason(),user(req).getUsername());}
    @PostMapping("/agreements/{id}/upload") public Object agreementUpload(@PathVariable Long id,@RequestParam("file") MultipartFile f,HttpServletRequest req)throws Exception{if(f.getOriginalFilename()==null||!f.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".docx"))throw new IllegalArgumentException("На первом этапе можно загрузить только DOCX");return service.upload(id,f.getOriginalFilename(),f.getBytes(),user(req).getUsername());}
    @GetMapping("/agreements/{id}/download") public ResponseEntity<byte[]> agreementDownload(@PathVariable Long id,HttpServletRequest req){AdditionalAgreement a=service.agreement(id);if(a.getStatus()!=AdditionalAgreement.Status.ISSUED&&a.getStatus()!=AdditionalAgreement.Status.SIGNING&&a.getStatus()!=AdditionalAgreement.Status.SIGNED)a=service.issue(id,user(req).getUsername());return file(a.getCurrentDocument(),a.getCurrentFilename());}
    @GetMapping("/agreements/{id}/versions") public Object versions(@PathVariable Long id){return service.versions(id).stream().map(v->Map.of("id",v.getId(),"revision",v.getRevision(),"filename",v.getFilename(),"source",v.getSource(),"createdAt",v.getCreatedAt(),"createdBy",v.getCreatedBy())).toList();}

    @GetMapping("/catalog") public Object catalog(){return service.catalog();}
    @PostMapping("/catalog") public Object catalog(@RequestBody HrCatalogItem x){x.setId(null);x.setSchoolCode(org.school.personalLoad.config.SchoolCodeResolver.resolve());return catalogRepository.save(x);}
    @PutMapping("/catalog/{id}") public Object catalog(@PathVariable Long id,@RequestBody HrCatalogItem x){HrCatalogItem old=catalogRepository.findById(id).orElseThrow();x.setId(old.getId());x.setSchoolCode(old.getSchoolCode());return catalogRepository.save(x);}

    private ResponseEntity<byte[]> excel(boolean includeData,HttpServletRequest req)throws Exception{
        boolean full=user(req).canExportTab(AppTab.HR_PERSONAL_DATA); try(Workbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sh=wb.createSheet("Персональные данные");String[] h={"ID педагога","ФИО","Серия паспорта","Номер паспорта","Кем выдан","Дата выдачи","Код подразделения","Регистрация","Фактический адрес","Телефон","ИНН","СНИЛС","Номер трудового договора","Дата договора","Должность","Начало договора","Окончание договора"};
            Row hr=sh.createRow(0);for(int i=0;i<h.length;i++)hr.createCell(i).setCellValue(h[i]);int n=1;
            for(TeacherDirectoryEntry t:teacherRepository.findAll()){
                Row r=sh.createRow(n++);r.createCell(0).setCellValue(t.getId());r.createCell(1).setCellValue(t.getFioTeacher());
                if(includeData&&full){HrPersonalData p=personalRepository.findByTeacherId(t.getId()).orElse(null);if(p!=null){r.createCell(2).setCellValue(z(p.getPassportSeries()));r.createCell(3).setCellValue(z(p.getPassportNumber()));r.createCell(4).setCellValue(z(p.getPassportIssuedBy()));r.createCell(5).setCellValue(p.getPassportIssueDate()==null?"":p.getPassportIssueDate().toString());r.createCell(6).setCellValue(z(p.getPassportDepartmentCode()));r.createCell(7).setCellValue(z(p.getRegistrationAddress()));r.createCell(8).setCellValue(z(p.getActualAddress()));r.createCell(9).setCellValue(z(p.getPhone()));r.createCell(10).setCellValue(z(p.getInn()));r.createCell(11).setCellValue(z(p.getSnils()));}}
                EmploymentContract c=service.contracts(t.getId()).stream().filter(EmploymentContract::isPrimaryContract).findFirst().orElse(null);if(c!=null){r.createCell(12).setCellValue(c.getContractNumber());r.createCell(13).setCellValue(c.getContractDate().toString());r.createCell(14).setCellValue(c.getPositionName());r.createCell(15).setCellValue(c.getStartDate()==null?"":c.getStartDate().toString());r.createCell(16).setCellValue(c.getEndDate()==null?"":c.getEndDate().toString());}
            }for(int i=0;i<h.length;i++)sh.setColumnWidth(i,Math.min(60,Math.max(14,h[i].length()+4))*256);wb.write(out);
            log(req,"EXPORT","HR_PERSONAL_DATA",full?"Полный экспорт":"Экспорт открытых данных",true);return file(out.toByteArray(),includeData?"персональные_данные.xlsx":"шаблон_персональных_данных.xlsx");
        }
    }
    private SessionUser user(HttpServletRequest r){return AuthSessionUtils.requiredUser(r);} private String z(String s){return s==null?"":s;}
    private String str(Row r,int i){Cell c=r.getCell(i);return c==null?null:new DataFormatter().formatCellValue(c).trim();}
    private Long longCell(Cell c){if(c==null)return null;try{return c.getCellType()==CellType.NUMERIC?(long)c.getNumericCellValue():Long.valueOf(new DataFormatter().formatCellValue(c).trim());}catch(Exception e){return null;}}
    private java.time.LocalDate date(Row r,int i){String s=str(r,i);try{return s==null||s.isBlank()?null:java.time.LocalDate.parse(s);}catch(Exception e){return null;}}
    private ResponseEntity<byte[]> file(byte[] b,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")).contentType(MediaType.APPLICATION_OCTET_STREAM).body(b);}
    private void log(HttpServletRequest req,String action,String entity,String details,boolean ok){SessionUser u=user(req);UserActionLog x=new UserActionLog();x.setUserId(u.getId());x.setUsername(u.getUsername());x.setFullName(u.getFullName());x.setRole(u.getRole().name());x.setActionType(action);x.setEntityType(entity);x.setDetails(details);x.setIp(req.getRemoteAddr());x.setUserAgent(req.getHeader("User-Agent"));x.setStatusCode(ok?200:500);x.setSuccess(ok);x.setCreatedAt(Instant.now());audit.save(x);}
}
