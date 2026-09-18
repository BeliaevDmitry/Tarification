const paMaterialsState={references:[],materials:[],subjects:[]};
const html=value=>String(value??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const academicYear=()=>sessionStorage.getItem('tarification.academicYear')||'';
const scoped=path=>{const year=academicYear();if(!year)return path;return `${path}${path.includes('?')?'&':'?'}academicYear=${encodeURIComponent(year)}`;};
async function materialApi(path,options={}){const response=await fetch(scoped(path),options);const text=await response.text();let body;try{body=text?JSON.parse(text):null}catch{body={message:text}}if(!response.ok)throw new Error(body?.message||body?.error||`Ошибка ${response.status}`);return body;}
const levelRu=value=>value==='ADVANCED'?'углублённый':'базовый';
const workRu=value=>value==='ENTRY'?'входная':value==='MID'?'промежуточная':'выходная';
const dateRu=value=>value?new Date(value).toLocaleString('ru-RU'):'—';
const rowParallel=row=>Number(row.parallel||String(row.scopeValue||'').match(/^\d{1,2}/)?.[0]||0);
const subjectArea=subject=>(paMaterialsState.subjects.find(row=>row.subjectName===subject)?.subjectAreaName||'Без предметной области');

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
    const complete=row.textAvailable&&row.answersAvailable;
    const state=complete?'pa-material-complete':'pa-material-partial';
    const scope=row.scopeType==='CLASS'?row.scopeValue:`${row.scopeValue} параллель`;
    const textStatus=row.textAvailable?`✅ текст · ${html(row.textUploadedByFio||'неизвестно')}`:'❌ нет текста';
    const answerStatus=row.answersAvailable?`✅ ответы · ${html(row.answersUploadedByFio||'неизвестно')}`:'❌ нет ответов';
    const downloads=`<span class="pa-material-downloads">${row.textAvailable?`<a href="${scoped(`/api/pa/materials/${row.id}/download?kind=TEXT`)}">Текст</a>`:''}${row.answersAvailable?`<a href="${scoped(`/api/pa/materials/${row.id}/download?kind=ANSWERS`)}">Ответы</a>`:''}</span>`;
    return `<span class="pa-material-card ${state}"><strong>${html(scope)} · ${html(workRu(row.workType))}</strong><small>${html(levelRu(row.level))} · вариантов: ${row.variantCount}</small><small>${textStatus}</small><small>${answerStatus}</small>${downloads}</span>`;
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
    const rows=paMaterialsState.materials.filter(row=>!needle||[row.subjectName,row.scopeValue,row.textUploadedByFio,row.answersUploadedByFio,row.textFileName,row.answersFileName].some(value=>String(value||'').toLowerCase().includes(needle)));
    body.innerHTML=rows.length?rows.map(row=>`<tr><td>${html(row.subjectName)}</td><td>${row.scopeType==='CLASS'?'Класс':'Параллель'} ${html(row.scopeValue)}</td><td>${html(levelRu(row.level))}</td><td>${html(workRu(row.workType))}</td><td>${row.variantCount}</td><td>${fileCell(row,'TEXT')}</td><td>${fileCell(row,'ANSWERS')}</td><td>${dateRu(row.updatedAt)}</td></tr>`).join(''):'<tr><td colspan="8" class="muted">Материалы не найдены</td></tr>';
}

function fileCell(row,kind){
    const text=kind==='TEXT';const available=text?row.textAvailable:row.answersAvailable;const name=text?row.textFileName:row.answersFileName;const uploader=text?row.textUploadedByFio:row.answersUploadedByFio;const at=text?row.textUploadedAt:row.answersUploadedAt;
    if(!available)return '<span class="pa-status-pill pa-no">Не загружен</span>';
    return `<a href="${scoped(`/api/pa/materials/${row.id}/download?kind=${kind}`)}">${html(name||'Скачать')}</a><small class="muted" style="display:block">${html(uploader||'неизвестно')}<br>${dateRu(at)}</small>`;
}

function renderAll(){renderSummary('pa-material-summary-5-11',5,11);renderSummary('pa-material-summary-1-4',1,4);renderRegistry();}
async function loadMaterials(){
    const [references,materials,subjects]=await Promise.all([materialApi('/api/pa/materials/references'),materialApi('/api/pa/materials'),materialApi('/api/subjects').catch(()=>[])]);
    paMaterialsState.references=references||[];paMaterialsState.materials=materials||[];paMaterialsState.subjects=subjects||[];fillSubjects();renderAll();
}

async function uploadMaterial(event){
    event.preventDefault();const feedback=document.getElementById('pa-material-feedback');const textFile=document.getElementById('pa-material-text-file').files[0];const answersFile=document.getElementById('pa-material-answers-file').files[0];
    if(!textFile&&!answersFile){feedback.textContent='Выберите текст работы или файл с ответами.';return;}
    const form=new FormData();form.set('subjectName',document.getElementById('pa-material-subject').value);form.set('scopeType',document.getElementById('pa-material-scope-type').value);form.set('scopeValue',document.getElementById('pa-material-scope').value);form.set('level',document.getElementById('pa-material-level').value);form.set('workType',document.getElementById('pa-material-work-type').value);form.set('variantCount',document.getElementById('pa-material-variant-mode').value==='many'?document.getElementById('pa-material-variant-count').value:'1');if(textFile)form.set('textFile',textFile);if(answersFile)form.set('answersFile',answersFile);
    feedback.textContent='Загрузка…';document.getElementById('pa-material-upload').disabled=true;
    try{const result=await materialApi('/api/pa/materials',{method:'POST',body:form});feedback.textContent=result.message;document.getElementById('pa-material-text-file').value='';document.getElementById('pa-material-answers-file').value='';await loadMaterials();}
    catch(error){feedback.textContent=error.message;}finally{document.getElementById('pa-material-upload').disabled=false;}
}

document.querySelectorAll('[data-material-tab]').forEach(button=>button.addEventListener('click',()=>setMaterialTab(button.dataset.materialTab)));
document.getElementById('pa-material-subject').addEventListener('change',fillScopes);
document.getElementById('pa-material-scope-type').addEventListener('change',fillScopes);
document.getElementById('pa-material-variant-mode').addEventListener('change',event=>{document.getElementById('pa-material-variant-count-wrap').hidden=event.target.value!=='many';});
document.getElementById('pa-material-form').addEventListener('submit',uploadMaterial);
document.getElementById('pa-material-search').addEventListener('input',renderRegistry);
document.getElementById('pa-material-refresh').addEventListener('click',()=>loadMaterials().catch(error=>{document.getElementById('pa-material-registry-body').innerHTML=`<tr><td colspan="8">${html(error.message)}</td></tr>`;}));
loadMaterials().catch(error=>{document.getElementById('pa-material-feedback').textContent=error.message;});
