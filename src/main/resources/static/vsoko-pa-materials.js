const paMaterialsState={references:[],materials:[],subjects:[],editingId:null};
const html=value=>String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const academicYear=()=>sessionStorage.getItem('tarification.academicYear')||'';
const scoped=path=>{const year=academicYear();if(!year)return path;return `${path}${path.includes('?')?'&':'?'}academicYear=${encodeURIComponent(year)}`;};
async function materialApi(path,options={}){const response=await fetch(scoped(path),options);const text=await response.text();let body;try{body=text?JSON.parse(text):null}catch{body={message:text}}if(!response.ok)throw new Error(body?.message||body?.error||`Ошибка ${response.status}`);return body;}
const levelRu=value=>value==='ADVANCED'?'углублённый':'базовый';
const workRu=value=>value==='ENTRY'?'входная':value==='MID'?'промежуточная':'выходная';
const dateRu=value=>value?new Date(value).toLocaleString('ru-RU'):'—';
const rowParallel=row=>Number(row.parallel||String(row.scopeValue||'').match(/^\d{1,2}/)?.[0]||0);
const subjectArea=subject=>(paMaterialsState.subjects.find(row=>row.subjectName===subject)?.subjectAreaName||'Без предметной области');
const materialFiles=(row,kind)=>kind==='TEXT'?(row.textFiles||[]):(row.answerFiles||[]);
function materialDownload(row,file){return file.legacy?scoped(`/api/pa/materials/${row.id}/download?kind=${file.kind}`):scoped(`/api/pa/materials/files/${file.id}/download`);}

function setMaterialTab(tab){
    document.querySelectorAll('[data-material-tab]').forEach(button=>button.classList.toggle('active',button.dataset.materialTab===tab));
    ['upload','summary-5-11','summary-1-4','registry'].forEach(name=>{document.getElementById(`pa-material-${name}-panel`).hidden=name!==tab;});
}

function referenceSubjects(){
    const values=[...paMaterialsState.references.map(row=>row.subjectName),...paMaterialsState.materials.map(row=>row.subjectName)].filter(Boolean);
    return [...new Set(values)].sort((a,b)=>a.localeCompare(b,'ru'));
}

function fillSubjects(){
    const select=document.getElementById('pa-material-subject');const previous=select.value;
    const subjects=referenceSubjects();select.innerHTML=subjects.length?subjects.map(value=>`<option value="${html(value)}">${html(value)}</option>`).join(''):'<option value="">Нет предметов в учебном плане</option>';
    if(subjects.includes(previous))select.value=previous;
    fillScopes();
}

function fillScopes(){
    const subject=document.getElementById('pa-material-subject').value;
    const type=document.getElementById('pa-material-scope-type').value;
    const select=document.getElementById('pa-material-scope');const previous=select.value;
    document.getElementById('pa-material-scope-label').firstChild.textContent=type==='CLASS'?'Конкретный класс':'Параллель';
    const refs=paMaterialsState.references.filter(row=>row.subjectName===subject);
    let values=type==='CLASS'?refs.map(row=>row.className):refs.map(row=>String(row.parallel));
    paMaterialsState.materials.filter(row=>row.subjectName===subject&&row.scopeType===type).forEach(row=>values.push(String(row.scopeValue)));
    values=[...new Set(values.filter(Boolean))].sort((a,b)=>type==='CLASS'?a.localeCompare(b,'ru',{numeric:true}):Number(a)-Number(b));
    select.innerHTML=values.map(value=>`<option value="${html(value)}">${type==='CLASS'?html(value):`${html(value)} класс`}</option>`).join('');
    if(values.includes(previous))select.value=previous;
}

function materialCard(row){
    const texts=materialFiles(row,'TEXT');const answers=materialFiles(row,'ANSWERS');const complete=texts.length>0&&answers.length>0;
    const state=complete?'pa-material-complete':'pa-material-partial';
    const scope=row.scopeType==='CLASS'?row.scopeValue:`${row.scopeValue} параллель`;
    const textStatus=texts.length?`✅ тексты: ${texts.map(file=>`${html(file.fileName||'файл')} — ${html(file.uploadedByFio||'неизвестно')}`).join('; ')}`:'❌ нет текстов';
    const answerStatus=answers.length?`✅ ответы: ${answers.map(file=>`${html(file.fileName||'файл')} — ${html(file.uploadedByFio||'неизвестно')}`).join('; ')}`:'❌ нет ответов';
    const downloads=`<span class="pa-material-downloads">${[...texts,...answers].map(file=>`<a href="${materialDownload(row,file)}">${file.kind==='TEXT'?'Текст':'Ответы'}: ${html(file.fileName||'Скачать')}</a>`).join('')}</span>`;
    return `<span class="pa-material-card ${state}"><strong>${html(scope)} · ${html(workRu(row.workType))}</strong><small>${html(levelRu(row.level))} · вариантов: ${html(row.variantCount||1)}</small><small>${textStatus}</small><small>${answerStatus}</small>${downloads}</span>`;
}

function renderSummary(hostId,from,to){
    const host=document.getElementById(hostId);const parallels=Array.from({length:to-from+1},(_,index)=>from+index);
    const subjects=[...new Set([...paMaterialsState.references.filter(row=>row.parallel>=from&&row.parallel<=to).map(row=>row.subjectName),...paMaterialsState.materials.filter(row=>rowParallel(row)>=from&&rowParallel(row)<=to).map(row=>row.subjectName)])].sort((a,b)=>a.localeCompare(b,'ru'));
    if(!subjects.length){host.innerHTML='<p class="muted">В учебном плане и реестре материалов данных для этого диапазона нет.</p>';return;}
    const areas=[...new Set(subjects.map(subjectArea))].sort((a,b)=>a.localeCompare(b,'ru'));
    const rows=areas.map(area=>{const areaSubjects=subjects.filter(subject=>subjectArea(subject)===area);return areaSubjects.map((subject,index)=>`<tr>${index===0?`<td class="pa-material-area" rowspan="${areaSubjects.length}">${html(area)}</td>`:''}<th scope="row">${html(subject)}</th>${parallels.map(parallel=>{const items=paMaterialsState.materials.filter(row=>row.subjectName===subject&&rowParallel(row)===parallel);return items.length?`<td class="pa-material-cell">${items.map(materialCard).join('')}</td>`:'<td class="pa-material-cell pa-material-missing">Не загружено</td>';}).join('')}</tr>`).join('');}).join('');
    host.innerHTML=`<div class="pa-material-table-wrap"><table class="pa-material-table pa-material-summary-table"><thead><tr><th>Предметная область</th><th>Предмет</th>${parallels.map(value=>`<th>${value} класс</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table></div>`;
}

function renderRegistry(){
    const body=document.getElementById('pa-material-registry-body');const needle=document.getElementById('pa-material-search').value.trim().toLowerCase();
    const rows=paMaterialsState.materials.filter(row=>!needle||[row.subjectName,row.scopeValue,...materialFiles(row,'TEXT').flatMap(file=>[file.fileName,file.uploadedByFio]),...materialFiles(row,'ANSWERS').flatMap(file=>[file.fileName,file.uploadedByFio])].some(value=>String(value||'').toLowerCase().includes(needle)));
    body.innerHTML=rows.length?rows.map(row=>`<tr><td>${html(row.subjectName)}</td><td>${row.scopeType==='CLASS'?'Класс':'Параллель'} ${html(row.scopeValue)}</td><td>${html(levelRu(row.level))}</td><td>${html(workRu(row.workType))}</td><td>${html(row.variantCount||1)}</td><td>${fileCell(row,'TEXT')}</td><td>${fileCell(row,'ANSWERS')}</td><td>${dateRu(row.updatedAt)}</td><td><button type="button" class="secondary" data-material-edit="${html(row.id)}" data-requires-edit>Редактировать</button></td></tr>`).join(''):'<tr><td colspan="9" class="muted">Материалы не найдены</td></tr>';
}

function fileCell(row,kind){
    const files=materialFiles(row,kind);if(!files.length)return '<span class="pa-status-pill pa-no">Не загружены</span>';
    return files.map(file=>`<div style="margin-bottom:8px"><a href="${materialDownload(row,file)}">${html(file.fileName||'Скачать')}</a><small class="muted" style="display:block">${html(file.uploadedByFio||'неизвестно')}<br>${dateRu(file.uploadedAt)}</small></div>`).join('');
}

function renderAll(){renderSummary('pa-material-summary-5-11',5,11);renderSummary('pa-material-summary-1-4',1,4);renderRegistry();}
async function loadMaterials(){
    const [references,materials,subjects]=await Promise.all([materialApi('/api/pa/materials/references'),materialApi('/api/pa/materials'),materialApi('/api/subjects').catch(()=>[])]);
    paMaterialsState.references=references||[];paMaterialsState.materials=materials||[];paMaterialsState.subjects=subjects||[];fillSubjects();renderAll();
}

function materialUploadForm(){
    const form=new FormData();form.set('subjectName',document.getElementById('pa-material-subject').value);form.set('scopeType',document.getElementById('pa-material-scope-type').value);form.set('scopeValue',document.getElementById('pa-material-scope').value);form.set('level',document.getElementById('pa-material-level').value);form.set('workType',document.getElementById('pa-material-work-type').value);form.set('variantCount',document.getElementById('pa-material-variant-count').value);return form;
}

async function uploadMaterial(event){
    event.preventDefault();const feedback=document.getElementById('pa-material-feedback');const textFiles=[...document.getElementById('pa-material-text-files').files];const answerFiles=[...document.getElementById('pa-material-answer-files').files];
    if(!textFiles.length&&!answerFiles.length){feedback.textContent='Выберите хотя бы один файл с текстом работы или ответами.';return;}
    const total=textFiles.length+answerFiles.length;const form=materialUploadForm();textFiles.forEach(file=>form.append('textFiles',file));answerFiles.forEach(file=>form.append('answerFiles',file));document.getElementById('pa-material-upload').disabled=true;
    try{feedback.textContent=`Отправка комплекта: ${total} файлов…`;const result=await materialApi('/api/pa/materials',{method:'POST',body:form});document.getElementById('pa-material-text-files').value='';document.getElementById('pa-material-answer-files').value='';await loadMaterials();feedback.textContent=result?.message||`Все файлы загружены: ${total}.`;}
    catch(error){feedback.textContent=`Комплект не загружен: ${error.message}`;}
    finally{document.getElementById('pa-material-upload').disabled=false;}
}

function editFileList(hostId,files){
    const host=document.getElementById(hostId);
    host.innerHTML=files.length?files.map(file=>`<li>${html(file.fileName||'Файл')} <small class="muted">${html(file.uploadedByFio||'неизвестно')}</small></li>`).join(''):'<li class="muted">Не загружены</li>';
}

function openMaterialEdit(materialId){
    const row=paMaterialsState.materials.find(item=>Number(item.id)===Number(materialId));if(!row)return;
    paMaterialsState.editingId=row.id;
    document.getElementById('pa-material-edit-summary').textContent=`${row.subjectName} · ${row.scopeType==='CLASS'?'класс':'параллель'} ${row.scopeValue} · ${levelRu(row.level)} · ${workRu(row.workType)}`;
    document.getElementById('pa-material-edit-variant-count').value=row.variantCount||1;
    editFileList('pa-material-edit-current-texts',materialFiles(row,'TEXT'));
    editFileList('pa-material-edit-current-answers',materialFiles(row,'ANSWERS'));
    document.getElementById('pa-material-edit-text-files').value='';
    document.getElementById('pa-material-edit-answer-files').value='';
    document.getElementById('pa-material-edit-replace-texts').checked=false;
    document.getElementById('pa-material-edit-replace-answers').checked=false;
    document.getElementById('pa-material-edit-feedback').textContent='';
    document.getElementById('pa-material-edit-dialog').showModal();
}

async function saveMaterialEdit(event){
    event.preventDefault();const id=paMaterialsState.editingId;if(!id)return;
    const feedback=document.getElementById('pa-material-edit-feedback');const save=document.getElementById('pa-material-edit-save');
    const textFiles=[...document.getElementById('pa-material-edit-text-files').files];const answerFiles=[...document.getElementById('pa-material-edit-answer-files').files];
    let replaceTexts=document.getElementById('pa-material-edit-replace-texts').checked;let replaceAnswers=document.getElementById('pa-material-edit-replace-answers').checked;
    if(replaceTexts&&!textFiles.length){feedback.textContent='Для замены текстов выберите хотя бы один новый файл.';return;}
    if(replaceAnswers&&!answerFiles.length){feedback.textContent='Для замены ответов выберите хотя бы один новый файл.';return;}
    const total=textFiles.length+answerFiles.length;save.disabled=true;
    try{
        const form=new FormData();form.set('variantCount',document.getElementById('pa-material-edit-variant-count').value);form.set('replaceTextFiles',String(replaceTexts));form.set('replaceAnswerFiles',String(replaceAnswers));textFiles.forEach(file=>form.append('textFiles',file));answerFiles.forEach(file=>form.append('answerFiles',file));
        feedback.textContent=total?`Сохранение комплекта: ${total} файлов…`:'Сохранение количества вариантов…';
        await materialApi(`/api/pa/materials/${id}`,{method:'PUT',body:form});
        await loadMaterials();
        document.getElementById('pa-material-edit-dialog').close();
    }catch(error){feedback.textContent=`Изменения не сохранены: ${error.message}`;}
    finally{save.disabled=false;}
}

async function deleteMaterial(){
    const id=paMaterialsState.editingId;if(!id)return;if(!confirm('Удалить запись и все прикреплённые тексты и ответы? Восстановить её через реестр будет нельзя.'))return;
    const feedback=document.getElementById('pa-material-edit-feedback');const button=document.getElementById('pa-material-edit-delete');button.disabled=true;
    try{feedback.textContent='Удаление записи…';await materialApi(`/api/pa/materials/${id}`,{method:'DELETE'});document.getElementById('pa-material-edit-dialog').close();await loadMaterials();}
    catch(error){feedback.textContent=`Не удалось удалить запись: ${error.message}`;}
    finally{button.disabled=false;}
}

document.querySelectorAll('[data-material-tab]').forEach(button=>button.addEventListener('click',()=>setMaterialTab(button.dataset.materialTab)));
document.getElementById('pa-material-subject').addEventListener('change',fillScopes);
document.getElementById('pa-material-scope-type').addEventListener('change',fillScopes);
document.getElementById('pa-material-form').addEventListener('submit',uploadMaterial);
document.getElementById('pa-material-search').addEventListener('input',renderRegistry);
document.getElementById('pa-material-refresh').addEventListener('click',()=>loadMaterials().catch(error=>{document.getElementById('pa-material-registry-body').innerHTML=`<tr><td colspan="9">${html(error.message)}</td></tr>`;}));
document.getElementById('pa-material-registry-body').addEventListener('click',event=>{const button=event.target.closest('[data-material-edit]');if(button)openMaterialEdit(button.dataset.materialEdit);});
document.getElementById('pa-material-edit-form').addEventListener('submit',saveMaterialEdit);
document.getElementById('pa-material-edit-delete').addEventListener('click',deleteMaterial);
document.getElementById('pa-material-edit-cancel').addEventListener('click',()=>document.getElementById('pa-material-edit-dialog').close());
loadMaterials().catch(error=>{document.getElementById('pa-material-feedback').textContent=error.message;});
