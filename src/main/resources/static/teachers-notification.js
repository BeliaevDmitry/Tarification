const $ = selector => document.querySelector(selector);
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
function formatDate(value, fallback = '—') {
    const raw=String(value||'').slice(0,10);
    const match=/^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
    return match?`${match[3]}.${match[2]}.${match[1]}`:(value||fallback);
}
const academicYear = () => window.getStoredAcademicYear ? window.getStoredAcademicYear() : '';
const canViewPersonal = () => Boolean(window.tarificationAuth?.admin || window.tarificationTabPermissions?.HR_PERSONAL_DATA?.canView);
const CATEGORY_LABELS = {COMPENSATION:'Компенсационная выплата', INCENTIVE:'Стимулирующая выплата', ADDITIONAL_WORK:'Дополнительная работа'};
const STATUS_LABELS = {WAITING_FOR_MEMO:'Ожидает служебку',DRAFT:'Черновик',READY:'Готов',PROCESSED:'Выпущена',ISSUED:'Выпущена',SIGNING:'На подписании',RECEIVED_BY_HR:'Получена кадрами',EXECUTED:'Исполнена',SIGNED:'Подписано',REJECTED:'Отклонено',REQUIRES_DECISION:'Требуется решение',ANNULLED:'Аннулирована',ARCHIVED:'В архиве',EXPIRED:'Срок завершён',CANCELLED:'Отменено'};

async function api(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const raw = await response.text();
        let message = raw;
        try { const parsed=JSON.parse(raw); message=parsed.error||parsed.message||raw; } catch (_) {
            if (/<(?:!doctype|html|body)\b/i.test(raw)) message=`Ошибка ${response.status} при обращении к ${url}`;
        }
        throw new Error(message || `Ошибка ${response.status}`);
    }
    return response.headers.get('content-type')?.includes('json') ? response.json() : response;
}
function json(method, body) { return {method, headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)}; }
function row(label, control, help = '') {
    return `<div class="field-label">${label}</div><div class="field-control">${control}${help ? `<span class="field-help">${help}</span>` : ''}</div>`;
}
function option(value, label, selected = false) { return `<option value="${esc(value)}"${selected ? ' selected' : ''}>${esc(label)}</option>`; }
function showNotice(message, error = false) {
    document.querySelector('.hr-toast')?.remove();
    const notice=document.createElement('div'); notice.className=`hr-toast${error?' error':''}`; notice.textContent=message;
    document.body.appendChild(notice); setTimeout(()=>notice.remove(),7000);
}
const STANDARD_CONTRACT_CLAUSES = ['2.1','2.4','2.5'];
const CONTRACT_CLAUSE_LABELS = {
    '2.1':'2.1 — учебная нагрузка',
    '2.4':'2.4 — дополнительные функции',
    '2.5':'2.5 — стимулирующие выплаты'
};
function clausePicker(prefix, value = '2.4') {
    const current = String(value || '2.4');
    const selected=STANDARD_CONTRACT_CLAUSES.includes(current)?current:'2.4';
    return `<div class="clause-picker"><select id="${prefix}-clause-choice" name="${prefix}ClauseChoice" required>${STANDARD_CONTRACT_CLAUSES.map(clause=>option(clause,CONTRACT_CLAUSE_LABELS[clause],clause===selected)).join('')}</select></div>`;
}
function setClausePicker(prefix, value) {
    const current=String(value||'2.4'),choice=$(`#${prefix}-clause-choice`);
    choice.value=STANDARD_CONTRACT_CLAUSES.includes(current)?current:'2.4';
}
function readClause(form, prefix) {
    return form.get(`${prefix}ClauseChoice`);
}
function contractClauseRow(prefix, value, help = '') {
    return `<div id="${prefix}-clause-label" class="field-label">Пункт трудового договора</div><div id="${prefix}-clause-control" class="field-control">${clausePicker(prefix,value)}${help?`<span class="field-help">${help}</span>`:''}</div>`;
}
function setContractClauseVisibility(prefix, separate) {
    $(`#${prefix}-clause-label`)?.classList.toggle('hidden',separate);
    $(`#${prefix}-clause-control`)?.classList.toggle('hidden',separate);
    const choice=$(`#${prefix}-clause-choice`);
    if(choice)choice.disabled=separate;
}
function documentTextOverrides(memoText = '', agreementText = '', prefix = 'memo') {
    const hasText=Boolean(memoText||agreementText);
    return `<details class="advanced-text"${hasText?' open':''}><summary>Проверить или изменить автоматический текст</summary><label for="${prefix}-text">Текст служебной записки (необязательно)</label><textarea id="${prefix}-text" name="${prefix==='memo'?'assignmentText':'memo'}" placeholder='Для пункта 2.4 система сформирует: Внести изменения в пункт 2.4 раздела 2 "Оплата труда", изложив его в следующей редакции. Ниже будут перечислены все актуальные функции работника и суммы цифрами и прописью.'>${esc(memoText)}</textarea><span class="field-help">Для обычного изменения пункта 2.4 без отдельного функционала ручной текст не применяется: система всегда собирает утверждённую полную редакцию по работнику, обязанности и сумме.</span><label for="${prefix}-agreement">Текст для дополнительного соглашения (необязательно)</label><textarea id="${prefix}-agreement" name="${prefix==='memo'?'agreementText':'agreement'}" placeholder='Пример строки: - возложена функция "заведование кабинетом технологии", в размере 15 000 рублей 00 коп. (пятнадцать тысяч рублей 00 коп.) в месяц.'>${esc(agreementText)}</textarea><span class="field-help">Для обычного пункта 2.4 этот блок также формируется автоматически и совпадает с текстом служебной записки.</span></details>`;
}

document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('.hr-panel').forEach(panel => panel.hidden = true);
    document.querySelectorAll('.hr-tabs [data-tab]').forEach(tab => tab.classList.toggle('active',tab===button));
    $('#' + button.dataset.tab).hidden = false;
    if (button.dataset.tab === 'agreements') loadAgreements().catch(error=>showNotice(error.message,true));
    if (button.dataset.tab === 'incentives') loadIncentives().catch(error=>showNotice(error.message,true));
    if (button.dataset.tab === 'memos') loadMemos().catch(error=>showNotice(error.message,true));
    if (button.dataset.tab === 'catalog') loadCatalog().catch(error=>showNotice(error.message,true));
    if (button.dataset.tab === 'personal') loadPersonalData().catch(error=>showNotice(error.message,true));
}));

let journal = [];
let agreementRows = [];
let personalRows = [];
let incentiveRows = [];
let dutyMemoRows = [];
let loadMemoRows = [];
async function loadJournal() {
    journal = await api(`/api/hr-documents/journal?academicYear=${encodeURIComponent(academicYear())}`);
    renderJournal();
}
async function loadAgreements() {
    agreementRows=await api(`/api/hr-documents/agreements?academicYear=${encodeURIComponent(academicYear())}`);
    renderAgreements();
    const dialog=$('#all-agreements-dialog');
    if(dialog?.open)renderAllAgreements(dialog.dataset.teacherId||null,dialog.dataset.contractId||null);
}
function renderJournal() {
    const query = $('#journal-search').value.toLowerCase();
    const status = $('#journal-status').value;
    $('#journal-body').innerHTML = journal
        .filter(item => (!query || JSON.stringify(item).toLowerCase().includes(query)) && (!status || item.actionRequired === status))
        .map(item => {
            const reissue=item.agreements.find(agreement=>agreement.reissueRequired&&['ISSUED','SIGNING'].includes(agreement.status));
            const required=reissue
                ?`<span class="error">В выпущенный документ добавлены изменения</span><br><button data-reopen-agreement="${reissue.id}">Перевыпустить</button>`
                :esc(item.actionRequired);
            return `<tr><td>${esc(item.fio)}</td><td>№ ${esc(item.contractNumber)}</td><td>${esc(item.position)}</td><td>${item.agreements.length ? item.agreements.map(renderAgreement).join('') : 'Нет действующих документов'}</td><td>${required}</td><td>${canViewPersonal() ? `<button data-personal="${item.teacherId}">Персональные данные</button> <button data-edit-contract="${item.contractId}" data-teacher="${item.teacherId}">Изменить договор</button>` : ''} <button data-open-agreements="${item.contractId}">Открыть соглашения</button></td></tr>`;
        }).join('');
}
function renderAgreement(agreement) {
    return `<div><b>${esc(agreement.visibleNumber||agreement.internalNumber)}</b> · ${esc(agreement.summary || agreement.kind)} · ${esc(STATUS_LABELS[agreement.status] || agreement.status)}</div>`;
}
function agreementTimelineLabel(agreement) {
    if(agreement.status==='ANNULLED')return 'Аннулировано';
    if(agreement.status==='REJECTED')return 'Отклонено';
    const today=new Date().toISOString().slice(0,10);
    if(agreement.validFrom&&agreement.validFrom>today)return `Действие начнётся ${formatDate(agreement.validFrom)}`;
    if(agreement.validTo&&agreement.validTo<today)return `Срок действия завершён ${formatDate(agreement.validTo)}`;
    return ['SIGNED','ISSUED','SIGNING'].includes(agreement.status)?'Действует':'Требует обработки';
}
function isHistoricalAgreement(row) {
    const agreement=row.agreement;
    if(['ANNULLED','REJECTED','CANCELLED','EXPIRED'].includes(agreement.status))return true;
    const today=new Date().toISOString().slice(0,10);
    return agreement.status==='SIGNED'&&agreement.validTo&&agreement.validTo<today;
}
function renderAgreementActions(agreement, rowInfo = {}) {
    const canDelete=agreement.status==='ANNULLED'
        ||(!agreement.issuedAt&&!agreement.serviceMemoId&&!agreement.loadServiceMemoId
            &&['WAITING_FOR_MEMO','DRAFT','READY','REQUIRES_DECISION','REJECTED'].includes(agreement.status));
    const deleteButton=canDelete?` <button data-delete-agreement="${agreement.id}" data-delete-agreement-status="${agreement.status}">Удалить</button>`:'';
    if (agreement.status === 'ANNULLED') return `<span class="muted">Документ аннулирован</span>${deleteButton}`;
    if (agreement.status === 'REJECTED') return `<span class="muted">Черновик отклонён; связанную служебку можно удалить</span>${deleteButton}`;
    const contractId=rowInfo.contractId;
    const teacherId=rowInfo.teacherId;
    const personalComplete=rowInfo.personalDataComplete !== false;
    const editable=['WAITING_FOR_MEMO','DRAFT','READY','REQUIRES_DECISION','ISSUED','SIGNING'].includes(agreement.status);
    const waiting = agreement.status === 'WAITING_FOR_MEMO';
    const changeMode = (agreement.serviceMemoId || agreement.loadServiceMemoId) && editable
        ? `<button data-change-mode="${agreement.id}" data-contract="${contractId}">Способ изменения</button>` : '';
    const edit=editable?`<button data-edit-agreement="${agreement.id}">Редактировать</button>`:'';
    let actions='';
    if(['ISSUED','SIGNING','SIGNED'].includes(agreement.status)){
        const downloadLabel=agreement.reissueRequired?'DOCX (старая версия)':'DOCX';
        const reopen=['ISSUED','SIGNING'].includes(agreement.status)
            ?` <button data-reopen-agreement="${agreement.id}">${agreement.reissueRequired?'Перевыпустить':'Исправить и перевыпустить'}</button>`:'';
        const signed=agreement.status!=='SIGNED'?` <button data-sign="${agreement.id}">Подписано</button>`:'';
        actions=`<button data-download="${agreement.id}">${downloadLabel}</button> <button data-upload="${agreement.id}">Заменить</button>${reopen}${signed}`;
    }
    else if(waiting)actions='<span class="muted">Выпуск заблокирован до получения служебки</span>';
    else if(!contractId)actions=`<button data-contract-missing="${teacherId}">Заполнить договор</button> <span class="muted">DOCX пока недоступен</span>`;
    else if(!personalComplete)actions=canViewPersonal()?`<button data-personal="${teacherId}">Заполнить данные</button> <span class="muted">DOCX пока недоступен</span>`:'<span class="muted">Кадрам нужно заполнить персональные данные</span>';
    else if(['DRAFT','REQUIRES_DECISION'].includes(agreement.status))actions=`<button data-prepare="${agreement.id}">Сформировать DOCX</button>`;
    else if(agreement.status==='READY')actions=`<button data-download="${agreement.id}">Выпустить и скачать</button>`;
    else actions='<span class="muted">Документ недоступен в текущем статусе</span>';
    const reject=editable?`<button data-reject="${agreement.id}">Отклонить</button>`:'';
    return `${edit} ${changeMode} ${actions} ${reject} <button data-annul="${agreement.id}">Аннулировать</button>${deleteButton}`;
}
function mergeCandidate(rows) {
    const mergeable=rows.filter(row=>row.agreement.kind==='PAY_TERMS'
        &&['WAITING_FOR_MEMO','DRAFT','READY','REQUIRES_DECISION','ISSUED','SIGNING'].includes(row.agreement.status));
    const load=mergeable.find(row=>/\b2\.1\b/.test(row.agreement.conditionsJson||''));
    const addition=mergeable.find(row=>row!==load&&/\b2\.(4|5)\b/.test(row.agreement.conditionsJson||'')
        &&row.agreement.validFrom===load?.agreement.validFrom&&row.agreement.validTo===load?.agreement.validTo);
    if(!load||!addition)return null;
    const selected=[load.agreement,addition.agreement];
    const issued=selected.filter(agreement=>['ISSUED','SIGNING'].includes(agreement.status));
    return issued.length<=1?{ids:selected.map(agreement=>agreement.id),reissue:issued.length===1}:null;
}
function renderAgreements() {
    const body=$('#agreement-body');if(!body)return;
    const query=$('#agreement-search').value.trim().toLowerCase(),status=$('#agreement-status').value;
    const rows=agreementRows.filter(row=>!isHistoricalAgreement(row)&&(!status||row.agreement.status===status)&&(!query||JSON.stringify(row).toLowerCase().includes(query)))
        .sort((a,b)=>String(b.agreement.documentDate||b.agreement.issuedAt||'').localeCompare(String(a.agreement.documentDate||a.agreement.issuedAt||'')));
    const groups=new Map();
    rows.forEach(row=>{const key=row.contractId?`contract-${row.contractId}`:`teacher-${row.teacherId}`;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(row);});
    body.innerHTML=groups.size?[...groups.values()].map(group=>{
        const first=group[0],contract=first.contractId?`№ ${esc(first.contractNumber||first.contractId)}`:'<span class="muted">Не заполнен</span>';
        const documents=group.map(row=>{
            const agreement=row.agreement;
            const source=agreement.registryManaged
                ?agreement.serviceMemoId||agreement.loadServiceMemoId?' · сводный + служебная записка':' · сводный из справочников'
                :agreement.serviceMemoId?' · из служебной записки':agreement.loadServiceMemoId?' · из изменения нагрузки':'';
            return `<div class="agreement-item"><b>${esc(agreement.visibleNumber||agreement.internalNumber)}</b> от ${esc(formatDate(agreement.documentDate))} · ${esc(STATUS_LABELS[agreement.status]||agreement.status)}<br>${esc(agreement.summary||agreement.kind)}<br><span class="muted">${esc(formatDate(agreement.validFrom))} — ${esc(formatDate(agreement.validTo))}${source}</span><div class="agreement-actions">${renderAgreementActions(agreement,row)}</div></div>`;
        }).join('');
        const merge=mergeCandidate(group);
        const reissue=group.find(row=>row.agreement.reissueRequired&&['ISSUED','SIGNING'].includes(row.agreement.status))?.agreement;
        const required=!first.personalDataComplete?'Заполнить персональные данные'
            :group.some(row=>row.agreement.status==='REQUIRES_DECISION')?'Требуется решение'
            :reissue?`<span class="error">В документ добавлены новые пункты</span><br><button data-reopen-agreement="${reissue.id}">Перевыпустить</button>`
            :group.some(row=>row.agreement.status==='WAITING_FOR_MEMO')?'Ожидает служебную записку'
            :merge?.reissue?'Можно объединить и перевыпустить':merge?'Можно объединить черновики':'';
        const contractAction=first.contractId?`<button data-edit-contract="${first.contractId}" data-teacher="${first.teacherId}">Изменить договор</button>`:`<button data-contract-missing="${first.teacherId}">Заполнить договор</button>`;
        const management=`${canViewPersonal()?`<button data-personal="${first.teacherId}">Персональные данные</button> ${contractAction}`:''} ${first.contractId?`<button data-agreement="${first.contractId}">Создать вне служебки</button>`:''} <button data-all-agreements-teacher="${first.teacherId}" data-all-agreements-contract="${first.contractId||''}">Все допники работника</button> ${merge?`<button data-merge-agreements="${merge.ids.join(',')}" data-merge-reissue="${merge.reissue}">${merge.reissue?'Объединить и перевыпустить':'Объединить пункты в один допник'}</button>`:''}`;
        return `<tr><td>${esc(first.fio||`ID ${first.teacherId}`)}</td><td>${contract}</td><td>${esc(first.position)}</td><td>${documents}</td><td>${required}</td><td>${management}</td></tr>`;
    }).join(''):'<tr><td colspan="6">Дополнительных соглашений по выбранным условиям нет</td></tr>';
}
function renderAllAgreements(teacherId = null, contractId = null) {
    const body=$('#all-agreement-body');if(!body)return;
    const query=$('#all-agreement-search').value.trim().toLowerCase();
    const rows=agreementRows
        .filter(row=>(!teacherId||String(row.teacherId)===String(teacherId))
            &&(!contractId||String(row.contractId)===String(contractId))
            &&(!query||JSON.stringify(row).toLowerCase().includes(query)))
        .sort((a,b)=>String(b.agreement.documentDate||b.agreement.issuedAt||'').localeCompare(String(a.agreement.documentDate||a.agreement.issuedAt||'')));
    body.innerHTML=rows.length?rows.map(row=>{
        const agreement=row.agreement;
        const source=agreement.registryManaged
            ?agreement.serviceMemoId||agreement.loadServiceMemoId?'Сводный из справочников + служебная записка':'Сводный из справочников'
            :agreement.serviceMemoId?`Служебная записка ID ${agreement.serviceMemoId}`:agreement.loadServiceMemoId?`Служебная записка по нагрузке ID ${agreement.loadServiceMemoId}`:'Без служебной записки';
        return `<tr><td>${esc(row.fio||`ID ${row.teacherId}`)}</td><td>${row.contractId?`№ ${esc(row.contractNumber||row.contractId)}`:'Не заполнен'}</td><td><b>${esc(agreement.visibleNumber||agreement.internalNumber)}</b><br>${esc(formatDate(agreement.documentDate,'Без даты'))}</td><td>${esc(formatDate(agreement.validFrom))} — ${esc(formatDate(agreement.validTo))}</td><td>${esc(agreement.summary||agreement.kind)}<br><span class="muted">${esc(source)}</span></td><td>${esc(STATUS_LABELS[agreement.status]||agreement.status)}<br><span class="muted">${esc(agreementTimelineLabel(agreement))}</span></td><td><div class="agreement-actions">${renderAgreementActions(agreement,row)}</div></td></tr>`;
    }).join(''):'<tr><td colspan="7">Дополнительные соглашения не найдены</td></tr>';
}
function openAllAgreements(teacherId = null, contractId = null) {
    const dialog=$('#all-agreements-dialog');
    dialog.dataset.teacherId=teacherId||'';
    dialog.dataset.contractId=contractId||'';
    $('#all-agreement-search').value='';
    renderAllAgreements(teacherId,contractId);
    dialog.showModal();
}
$('#journal-search').addEventListener('input', renderJournal);
$('#journal-status').addEventListener('change', renderJournal);
$('#reload-journal').addEventListener('click', loadJournal);
$('#agreement-search').addEventListener('input',renderAgreements);
$('#agreement-status').addEventListener('change',renderAgreements);
$('#reload-agreements').addEventListener('click',async()=>{
    try{await loadAgreements();showNotice('Список повторно загружен с сервера. Документы не создавались и не изменялись.');}
    catch(error){showNotice(error.message,true);}
});
$('#all-agreements').addEventListener('click',()=>openAllAgreements());
$('#close-all-agreements').addEventListener('click',()=>$('#all-agreements-dialog').close());
$('#all-agreement-search').addEventListener('input',()=>{
    const dialog=$('#all-agreements-dialog');
    renderAllAgreements(dialog.dataset.teacherId||null,dialog.dataset.contractId||null);
});

async function loadIncentives() {
    incentiveRows=await api(`/api/hr-documents/incentives?academicYear=${encodeURIComponent(academicYear())}`);
    renderIncentives();
}
function renderIncentives() {
    const body=$('#incentive-body');if(!body)return;
    const query=$('#incentive-search').value.trim().toLowerCase();
    const rows=incentiveRows.filter(item=>!query||item.fio.toLowerCase().includes(query));
    body.innerHTML=rows.length?rows.map((item,index)=>`<tr>
        <td>${index+1}</td>
        <td><b>${esc(item.fio)}</b>${item.hasLoad?'<br><span class="muted">Добавлен автоматически по нагрузке</span>':''}</td>
        <td><div class="hr-toolbar"><input data-incentive-amount="${item.teacherId}" type="number" min="0" step="0.01" value="${esc(item.amount)}"><button data-save-incentive="${item.teacherId}" type="button">Сохранить</button></div></td>
    </tr>`).join(''):'<tr><td colspan="3">Сотрудники не найдены</td></tr>';
}
async function openIncentiveEditor() {
    if(!teachersCache.length)teachersCache=await loadTeachersForDocuments();
    const existing=new Set(incentiveRows.map(item=>String(item.teacherId)));
    const available=teachersCache.filter(teacher=>!existing.has(String(teacher.id)));
    if(!available.length){showNotice('Все доступные сотрудники уже добавлены в таблицу «Стимул».');return;}
    openEditor('Добавить сотрудника в таблицу «Стимул»',
        row('Сотрудник',`<select name="teacherId" required><option value="">Выберите сотрудника</option>${available.map(teacher=>option(teacher.id,teacher.fio)).join('')}</select>`,'Связь сохраняется по постоянному ID педагога, а не по тексту ФИО.')+
        row('Стимул, руб.',`<input name="amount" type="number" min="0" step="0.01" value="0" required>`),
        async form=>{
            await api(`/api/hr-documents/incentives?academicYear=${encodeURIComponent(academicYear())}`,
                json('POST',{teacherId:+form.get('teacherId'),amount:form.get('amount')||0}));
            await Promise.all([loadIncentives(),loadAgreements()]);
            showNotice('Сотрудник добавлен в таблицу «Стимул».');
        });
}
$('#incentive-search').addEventListener('input',renderIncentives);
$('#reload-incentives').addEventListener('click',()=>loadIncentives().catch(error=>showNotice(error.message,true)));
$('#add-incentive').addEventListener('click',()=>openIncentiveEditor().catch(error=>showNotice(error.message,true)));
$('#incentive-body').addEventListener('click',async event=>{
    const teacherId=event.target.dataset.saveIncentive;if(!teacherId)return;
    const input=document.querySelector(`[data-incentive-amount="${teacherId}"]`);
    try{
        await api(`/api/hr-documents/incentives/${teacherId}?academicYear=${encodeURIComponent(academicYear())}`,
            json('PUT',{teacherId:+teacherId,amount:input.value||0}));
        await Promise.all([loadIncentives(),loadAgreements(),loadJournal()]);
        showNotice('Сумма сохранена. Черновик обновлён; для выпущенного неподписанного допсоглашения в колонке «Требуется» появилась кнопка «Перевыпустить».');
    }catch(error){showNotice(error.message,true);}
});
$('#export-incentives').addEventListener('click',()=>{
    location.href=`/api/hr-documents/incentives/export?academicYear=${encodeURIComponent(academicYear())}`;
});
$('#import-incentives-button').addEventListener('click',()=>$('#import-incentives').click());
$('#import-incentives').addEventListener('change',async event=>{
    if(!event.target.files[0])return;
    try{
        const form=new FormData();form.append('file',event.target.files[0]);
        const result=await api(`/api/hr-documents/incentives/import?academicYear=${encodeURIComponent(academicYear())}`,{method:'POST',body:form});
        await Promise.all([loadIncentives(),loadAgreements(),loadJournal()]);
        showNotice(`Импорт завершён: обновлено ${result.updated}, пропущено ${result.skipped}.`);
    }catch(error){showNotice(error.message,true);}
    finally{event.target.value='';}
});

function openEditor(title, fields, onSave, afterOpen) {
    $('#editor-title').textContent = title;
    $('#editor-fields').innerHTML = fields;
    $('#editor-error').textContent = '';
    const dialog = $('#editor');
    $('#editor-save').hidden = false;
    dialog.showModal();
    if (afterOpen) afterOpen();
    $('#editor-save').onclick = async event => {
        event.preventDefault();
        try {
            await onSave(new FormData($('#editor-form')));
            try { await loadJournal(); } catch (error) { console.error('Не удалось обновить журнал',error); }
            dialog.close();
        } catch (error) { $('#editor-error').textContent = error.message; }
    };
}

let teachersCache = [];
let catalogCache = [];
async function loadTeachersForDocuments() {
    try { return await api('/api/hr-documents/teachers'); }
    catch (primaryError) {
        try {
            const teachers = await api('/api/teachers');
            return teachers.filter(teacher=>!teacher.archived).map(teacher=>({id:teacher.id,fio:teacher.fioTeacher}));
        } catch (_) { throw primaryError; }
    }
}
async function loadReferenceData() {
    const [teachersResult, catalogResult] = await Promise.allSettled([loadTeachersForDocuments(), api('/api/hr-documents/catalog')]);
    if (teachersResult.status === 'rejected') throw new Error(`Не удалось загрузить список работников: ${teachersResult.reason?.message || teachersResult.reason}`);
    teachersCache = teachersResult.value;
    catalogCache = catalogResult.status === 'fulfilled' ? catalogResult.value : [];
    return catalogResult.status === 'rejected' ? 'Справочник обязанностей временно недоступен. Можно создать служебную записку вручную.' : '';
}

function showReferenceLoadError(error) {
    openEditor('Создание служебной записки',
        row('Не удалось открыть форму', `<div class="error">${esc(error.message || error)}</div>`,'Обновите страницу и повторите попытку. Если ошибка останется, передайте этот текст администратору.'),
        async () => {});
    $('#editor-save').hidden = true;
}

async function openContractEditor(selectedTeacherId = null, contractId = null) {
    if (!teachersCache.length) teachersCache = await loadTeachersForDocuments();
    const [contracts, inRateRules] = await Promise.all([
        selectedTeacherId ? api(`/api/hr-documents/contracts?teacherId=${selectedTeacherId}`) : Promise.resolve([]),
        api('/api/manual-load/in-rate/rules').catch(()=>[])
    ]);
    const current = contracts.find(item=>String(item.id)===String(contractId)) || null;
    openEditor(current ? 'Редактирование трудового договора' : 'Трудовой договор',
        row('Работник', `<select name="teacherId" required ${current?'disabled':''}><option value="">Выберите работника</option>${teachersCache.map(t => option(t.id, t.fio,String(t.id)===String(selectedTeacherId))).join('')}</select>`,'Договор всегда связан с постоянным ID работника.') +
        row('Номер договора', `<input name="number" value="${esc(current?.contractNumber)}" required>`) +
        row('Дата договора', `<input name="date" type="date" value="${esc(current?.contractDate)}" required>`) +
        row('Должность', `<input name="position" value="${esc(current?.positionName)}" required>`) +
        row('Начало работы', `<input name="start" type="date" value="${esc(current?.startDate)}">`) +
        row('Окончание', `<input name="end" type="date" value="${esc(current?.endDate)}">`) +
        row('Состояние', `<div class="inline-choice"><label><input name="primary" type="checkbox" ${current?.primaryContract!==false?'checked':''}> Основной договор</label><label><input name="active" type="checkbox" ${current?.active!==false?'checked':''}> Действует</label></div>`) +
        row('Учебные часы могут входить в ставку', `<select id="contract-in-rate-enabled" name="inRateEnabled"><option value="false" ${!current?.loadHoursMayBeIncludedInRate?'selected':''}>Нет — все часы оплачиваются отдельно</option><option value="true" ${current?.loadHoursMayBeIncludedInRate?'selected':''}>Да — часть часов входит в должностной оклад</option></select>`,'Распределение конкретных классов выполняется в разделе «Нагрузка по людям → Часы в ставке».') +
        `<div id="contract-in-rate-fields">`+
        row('Правило часов в ставке', `<select name="inRateRuleId"><option value="">Без автоматического правила</option>${inRateRules.filter(rule=>rule.active||String(rule.id)===String(current?.loadInRateRuleId)).map(rule=>option(rule.id,rule.name,String(rule.id)===String(current?.loadInRateRuleId))).join('')}</select>`,'Правило только предлагает распределение; пользователь подтверждает его вручную.')+
        row('Пояснение для документов', `<input name="inRateLabel" value="${esc(current?.loadInRateDocumentLabel)}" placeholder="преподаватель ОБЗР">`,'Эта формулировка попадёт в пункт 2.1 и приложение №1.')+
        `</div>`,
        async form => {
            const teacherId=current?.teacherId || +form.get('teacherId');
            await api(current ? `/api/hr-documents/contracts/${current.id}` : '/api/hr-documents/contracts',
                json(current ? 'PUT' : 'POST', {teacherId,contractNumber:form.get('number'),contractDate:form.get('date'),positionName:form.get('position'),startDate:form.get('start')||null,endDate:form.get('end')||null,primaryContract:form.get('primary')==='on',active:form.get('active')==='on',
                    loadHoursMayBeIncludedInRate:form.get('inRateEnabled')==='true',
                    loadInRateRuleId:form.get('inRateRuleId')?+form.get('inRateRuleId'):null,
                    loadInRateDocumentLabel:form.get('inRateLabel')}));
            await Promise.all([loadAgreements(),canViewPersonal()?loadPersonalData():Promise.resolve()]);
            showNotice('Трудовой договор сохранён и автоматически привязан к ожидающим документам.');
        },
        ()=>{
            const toggle=()=>{$('#contract-in-rate-fields').hidden=$('#contract-in-rate-enabled').value!=='true';};
            $('#contract-in-rate-enabled').addEventListener('change',toggle);toggle();
        }
    );
}
$('#add-contract').addEventListener('click',()=>openContractEditor());

async function editPersonal(teacherId) {
    const data = await api(`/api/hr-documents/personal-data/${teacherId}`) || {};
    openEditor('Персональные данные',
        row('Дата рождения', `<input name="birthDate" type="date" value="${esc(data.birthDate)}">`) +
        row('Серия паспорта', `<input name="series" value="${esc(data.passportSeries)}">`) + row('Номер паспорта', `<input name="number" value="${esc(data.passportNumber)}">`) +
        row('Кем выдан', `<input name="issuedBy" value="${esc(data.passportIssuedBy)}">`) + row('Дата выдачи', `<input name="issueDate" type="date" value="${esc(data.passportIssueDate)}">`) +
        row('Код подразделения', `<input name="code" value="${esc(data.passportDepartmentCode)}">`) + row('Адрес регистрации', `<textarea name="registration">${esc(data.registrationAddress)}</textarea>`) +
        row('Фактический адрес', `<textarea name="actual">${esc(data.actualAddress)}</textarea>`) + row('Телефон', `<input name="phone" value="${esc(data.phone)}">`) +
        row('ИНН', `<input name="inn" value="${esc(data.inn)}">`) + row('СНИЛС', `<input name="snils" value="${esc(data.snils)}">`),
        async form => {
            await api(`/api/hr-documents/personal-data/${teacherId}`, json('PUT', {teacherId,birthDate:form.get('birthDate')||null,passportSeries:form.get('series'),passportNumber:form.get('number'),passportIssuedBy:form.get('issuedBy'),passportIssueDate:form.get('issueDate')||null,passportDepartmentCode:form.get('code'),registrationAddress:form.get('registration'),actualAddress:form.get('actual'),phone:form.get('phone'),inn:form.get('inn'),snils:form.get('snils')}));
            await Promise.all([loadAgreements(),canViewPersonal()?loadPersonalData():Promise.resolve()]);
            showNotice('Персональные данные сохранены на сервере.');
        }
    );
}

function defaultPeriod() {
    const startYear = String(academicYear()).substring(0,4);
    const firstWorkingDay=new Date(Date.UTC(Number(startYear),8,1));
    while([0,6].includes(firstWorkingDay.getUTCDay()))firstWorkingDay.setUTCDate(firstWorkingDay.getUTCDate()+1);
    return {from:firstWorkingDay.toISOString().slice(0,10), to:`${Number(startYear)+1}-08-31`};
}
function editAgreement(contractId) {
    const period = defaultPeriod();
    openEditor('Дополнительное соглашение вне служебной записки',
        row('Когда использовать', '<div class="muted">Допсоглашение по дополнительной обязанности или изменению нагрузки создаётся автоматически. Эта форма нужна только для исключительного изменения условий оплаты, которому не требуется служебная записка.</div>') +
        row('Дата документа', '<input name="date" type="date">') + row('Начало действия', `<input name="from" type="date" value="${period.from}" required>`) + row('Окончание', `<input name="to" type="date" value="${period.to}" required>`) +
        row('Тип соглашения', '<input value="Условия оплаты труда" disabled>') +
        row('Способ изменения', '<select name="mode"><option value="AMEND">Внести изменение</option><option value="CANCEL_AND_RESTATE">Отменить и изложить заново</option></select>') +
        row('Краткое содержание', '<input name="summary">') + row('Сумма в месяц', '<input name="amount" type="number" step="0.01">') + row('Условия и обязанности', '<textarea name="conditions"></textarea>'),
        form => api('/api/hr-documents/agreements', json('POST', {contractId,serviceMemoId:null,academicYear:academicYear(),documentDate:form.get('date')||null,validFrom:form.get('from'),validTo:form.get('to'),kind:'PAY_TERMS',changeMode:form.get('mode'),summary:form.get('summary'),conditionsJson:form.get('conditions'),totalAmount:form.get('amount')||null}))
    );
}

async function editExistingAgreement(agreementId) {
    let found=agreementRows.find(row=>String(row.agreement.id)===String(agreementId));
    if(!found){
        const journalRow=journal.find(row=>row.agreements.some(agreement=>String(agreement.id)===String(agreementId)));
        const agreement=journalRow?.agreements.find(item=>String(item.id)===String(agreementId));
        if(agreement)found={teacherId:journalRow.teacherId,contractId:journalRow.contractId,contractNumber:journalRow.contractNumber,position:journalRow.position,personalDataComplete:journalRow.personalDataComplete,agreement};
    }
    if(!found)throw new Error('Дополнительное соглашение не найдено в текущем списке');
    const agreement=found.agreement;
    const issuedUnsigned=['ISSUED','SIGNING'].includes(agreement.status);
    const contracts=await api(`/api/hr-documents/contracts?teacherId=${found.teacherId}`);
    openEditor('Редактирование дополнительного соглашения',
        row('Работник', `<b>${esc(found.fio || journal.find(row=>row.teacherId===found.teacherId)?.fio || `ID ${found.teacherId}`)}</b>`,'Документ связан с постоянным ID работника.') +
        row('Трудовой договор', `<select name="contractId"><option value="">Не заполнен</option>${contracts.filter(item=>item.active).map(item=>option(item.id,`№ ${item.contractNumber} от ${item.contractDate} — ${item.positionName}`,String(item.id)===String(found.contractId))).join('')}</select>`,'Без договора можно сохранить формулировки, но сформировать DOCX нельзя.') +
        row('Вид документа', `<input value="${agreement.kind==='ADDITIONAL_WORK'?'Дополнительная работа':'Условия оплаты труда'}" disabled>`) +
        row('Дата соглашения', `<input name="date" type="date" value="${esc(agreement.documentDate)}">`) +
        row('Начало действия', `<input name="from" type="date" value="${esc(agreement.validFrom)}" required>`) +
        row('Окончание', `<input name="to" type="date" value="${esc(agreement.validTo)}" required>`) +
        row('Краткое содержание', `<input name="summary" value="${esc(agreement.summary)}" required placeholder="Например: нагрузка и должностной оклад">`,'Этот текст виден в общей таблице.') +
        row('Сумма в месяц', `<input name="amount" type="number" min="0" step="0.01" value="${esc(agreement.totalAmount)}">`,'В документе сумма будет записана цифрами и словами.') +
        row('Юридическая формулировка и обязанности', `<textarea name="conditions" required placeholder="Например: Внести изменения в пункт 2.1. раздела 2 «Оплата труда», изложив его в следующей редакции…">${esc(agreement.conditionsJson)}</textarea>`,'Автоматический текст сформирован по образцу допсоглашения школы. Его можно проверить и поправить до формирования DOCX.') +
        row('Сохранить как шаблон', '<label><input id="agreement-save-template" name="saveTemplate" type="checkbox"> Добавить эту формулировку в справочник</label>') +
        `<div id="agreement-template-label" class="field-label hidden">Название шаблона</div><div id="agreement-template-control" class="field-control hidden"><input name="templateName" value="${esc(agreement.summary)}" placeholder="Название для дальнейшего выбора"><span class="field-help">Если шаблон с таким названием уже есть, его формулировка и сумма обновятся.</span></div>`,
        async form => {await api(`/api/hr-documents/agreements/${agreementId}`,json('PUT',{contractId:form.get('contractId')?+form.get('contractId'):null,documentDate:form.get('date')||null,validFrom:form.get('from'),validTo:form.get('to'),summary:form.get('summary'),conditionsJson:form.get('conditions'),totalAmount:form.get('amount')||null,saveAsTemplate:form.get('saveTemplate')==='on',templateName:form.get('templateName')}));await loadAgreements();showNotice(issuedUnsigned?'Изменения сохранены. В колонке «Требуется» нажмите «Перевыпустить».':'Изменения сохранены. Теперь документ можно сформировать после заполнения обязательных данных.');},
        () => {
            const checkbox=$('#agreement-save-template'),label=$('#agreement-template-label'),control=$('#agreement-template-control');
            const update=()=>{label.classList.toggle('hidden',!checkbox.checked);control.classList.toggle('hidden',!checkbox.checked);};checkbox.addEventListener('change',update);update();
        }
    );
}

function editChangeMode(agreementId, contractId) {
    const contractRow = journal.find(item => String(item.contractId) === String(contractId));
    const currentListRow=agreementRows.find(item=>String(item.agreement.id)===String(agreementId));
    const allForContract=contractRow?.agreements||agreementRows.filter(item=>contractId
        ?String(item.contractId)===String(contractId)
        :String(item.teacherId)===String(currentListRow?.teacherId)).map(item=>item.agreement);
    const agreement = allForContract.find(item => String(item.id) === String(agreementId));
    const previous = allForContract.filter(item => String(item.id) !== String(agreementId) && item.status !== 'ANNULLED');
    openEditor('Способ оформления изменения',
        row('Юридическая формулировка', `<select id="change-mode" name="mode"><option value="AMEND" ${agreement?.changeMode==='AMEND'?'selected':''}>Внести изменение в предыдущее соглашение</option><option value="CANCEL_AND_RESTATE" ${agreement?.changeMode==='CANCEL_AND_RESTATE'?'selected':''}>Отменить предыдущее и считать условия действующими в новой редакции</option></select>`) +
        row('Предыдущее соглашение', `<select id="change-source" name="source"><option value="">Не выбрано — изменение трудового договора</option>${previous.map(item=>option(item.id,`№ ${item.internalNumber} от ${item.documentDate || 'без даты'} — ${item.summary || item.kind}`,String(item.id)===String(agreement?.replacesAgreementId))).join('')}</select>`,'Для отмены и новой редакции выбор предыдущего соглашения обязателен.'),
        async form => {await api(`/api/hr-documents/agreements/${agreementId}/change-mode`,json('POST',{changeMode:form.get('mode'),replacesAgreementId:form.get('source')?+form.get('source'):null}));await loadAgreements();},
        () => {
            const mode=$('#change-mode'),source=$('#change-source');
            const updateRequired=()=>source.required=mode.value==='CANCEL_AND_RESTATE';
            mode.addEventListener('change',updateRequired);updateRequired();
        }
    );
}

async function handleAgreementAction(event) {
    const target = event.target;
    try {
    if (target.dataset.download) { location.href = `/api/hr-documents/agreements/${target.dataset.download}/download`; setTimeout(()=>Promise.all([loadJournal(),loadAgreements()]),1200); }
    if (target.dataset.openAgreements) { const item=journal.find(row=>String(row.contractId)===target.dataset.openAgreements);document.querySelector('[data-tab="agreements"]').click();$('#agreement-search').value=item?.fio||'';renderAgreements(); }
    if (target.dataset.allAgreementsTeacher) openAllAgreements(target.dataset.allAgreementsTeacher,target.dataset.allAgreementsContract||null);
    if (target.dataset.personal) await editPersonal(+target.dataset.personal);
    if (target.dataset.contractMissing) await openContractEditor(+target.dataset.contractMissing);
    if (target.dataset.editContract) await openContractEditor(+target.dataset.teacher,+target.dataset.editContract);
    if (target.dataset.agreement) editAgreement(+target.dataset.agreement);
    if (target.dataset.editAgreement) editExistingAgreement(+target.dataset.editAgreement).catch(error=>showNotice(error.message,true));
    if (target.dataset.prepare) {
        try { await api(`/api/hr-documents/agreements/${target.dataset.prepare}/prepare`,{method:'POST'});await Promise.all([loadJournal(),loadAgreements()]);showNotice('DOCX сформирован и проверен системой. Нажмите «Выпустить и скачать».'); }
        catch (error) { showNotice(error.message,true); }
    }
    if (target.dataset.changeMode) { const rawContract=target.dataset.contract;editChangeMode(+target.dataset.changeMode,rawContract&&rawContract!=='null'&&rawContract!=='undefined'?+rawContract:null); }
    if (target.dataset.upload) {
        const input = document.createElement('input'); input.type = 'file'; input.accept = '.docx';
        input.onchange = async () => { const form = new FormData(); form.append('file', input.files[0]); await api(`/api/hr-documents/agreements/${target.dataset.upload}/upload`, {method:'POST',body:form}); await Promise.all([loadJournal(),loadAgreements()]); };
        input.click();
    }
    if(target.dataset.reopenAgreement){
        if(!confirm('Вернуть выпущенное, но неподписанное соглашение в черновик для исправления? Текущая версия останется в истории.'))return;
        await api(`/api/hr-documents/agreements/${target.dataset.reopenAgreement}/reopen`,
            json('POST',{}));
        await Promise.all([loadJournal(),loadAgreements()]);
        showNotice('Документ возвращён в черновик. Сумма нагрузки обновлена; проверьте текст и сформируйте DOCX заново.');
    }
    if (target.dataset.sign) { await api(`/api/hr-documents/agreements/${target.dataset.sign}/status`, json('POST',{status:'SIGNED'})); await Promise.all([loadJournal(),loadAgreements()]); }
    if (target.dataset.reject && confirm('Отклонить этот черновик дополнительного соглашения?')) { await api(`/api/hr-documents/agreements/${target.dataset.reject}/status`, json('POST',{status:'REJECTED'})); await Promise.all([loadJournal(),loadAgreements(),loadMemos()]); }
    if (target.dataset.annul) { const reason = prompt('Причина аннулирования'); if (reason) { await api(`/api/hr-documents/agreements/${target.dataset.annul}/annul`, json('POST',{reason})); await Promise.all([loadJournal(),loadAgreements(),loadMemos()]); showNotice('Дополнительное соглашение аннулировано. Связанная служебная записка перенесена в архив.'); } }
    if (target.dataset.deleteAgreement) {
        const annulled=target.dataset.deleteAgreementStatus==='ANNULLED';
        const documentName=annulled?'аннулированное дополнительное соглашение':'невыпущенный дополнительный документ';
        if(!confirm(`Удалить ${documentName} без возможности восстановления? Действие будет записано в журнал.`))return;
        const reason=prompt(annulled?'Укажите причину удаления аннулированного соглашения:':'Укажите причину удаления ошибочного или тестового документа:');
        if(reason===null)return;
        if(!reason.trim()){showNotice('Для удаления необходимо указать причину.',true);return;}
        const confirmation=prompt('Повторное подтверждение: введите слово УДАЛИТЬ заглавными буквами.');
        if(confirmation!=='УДАЛИТЬ'){showNotice('Удаление отменено: контрольное слово введено неверно.',true);return;}
        await api(`/api/hr-documents/agreements/${target.dataset.deleteAgreement}`,
            json('DELETE',{confirmation,reason:reason.trim()}));
        await Promise.all([loadJournal(),loadAgreements()]);
        showNotice(`${annulled?'Аннулированное соглашение':'Невыпущенный документ'} удалено, его номер снова свободен. Действие записано в журнал.`);
    }
    if (target.dataset.mergeAgreements) {
        const reissue=target.dataset.mergeReissue==='true';
        const question=reissue
            ?'Объединить новый черновик с выпущенным, но неподписанным соглашением? Выпущенная версия останется в истории, а документ вернётся в черновик для перевыпуска.'
            :'Объединить выбранные пункты в одно дополнительное соглашение?';
        if(!confirm(question))return;
        const agreementIds=target.dataset.mergeAgreements.split(',').map(Number);
        await api('/api/hr-documents/agreements/merge',json('POST',{agreementIds}));
        await Promise.all([loadJournal(),loadAgreements()]);
        showNotice(reissue
            ?'Документы объединены. Предыдущая выпущенная версия сохранена в истории; сформируйте и выпустите новую редакцию.'
            :'Пункты объединены в один черновик дополнительного соглашения.');
    }
    } catch (error) { showNotice(error.message,true); }
}
$('#journal-body').addEventListener('click',handleAgreementAction);
$('#agreement-body').addEventListener('click',handleAgreementAction);
$('#all-agreement-body').addEventListener('click',handleAgreementAction);

let annualGenerationInProgress=false;
async function createAnnualAgreements() {
    if(annualGenerationInProgress)return;
    if (!confirm('Сформировать или обновить годовые черновики: нагрузка (пункт 2.1), классное руководство (пункт 2.4) и стимул (пункт 2.5)?')) return;
    const buttons=[$('#batch-annual'),$('#batch-annual-agreements')];
    annualGenerationInProgress=true;
    buttons.forEach(button=>button.disabled=true);
    try{
        const result=await api('/api/hr-documents/agreements/batch-annual', json('POST',{academicYear:academicYear(),documentDate:new Date().toISOString().slice(0,10),contractIds:[]}));
        await Promise.all([loadJournal(),loadAgreements()]);
        $('#agreement-status').value='';
        renderAgreements();
        showNotice(result.created?`Создано годовых допсоглашений: ${result.created}. Нагрузка, классное руководство и стимул объединены по каждому работнику.`:'Годовые допсоглашения уже созданы; невыпущенные черновики обновлены актуальной нагрузкой, классным руководством и стимулом.');
    }catch(error){showNotice(error.message,true);}
    finally{
        annualGenerationInProgress=false;
        buttons.forEach(button=>button.disabled=false);
    }
}
$('#batch-annual').addEventListener('click',createAnnualAgreements);
$('#batch-annual-agreements').addEventListener('click',createAnnualAgreements);

async function loadMemos() {
    const [dutyMemos, loadMemos, teachers] = await Promise.all([
        api(`/api/hr-documents/memos?academicYear=${encodeURIComponent(academicYear())}`),
        api(`/api/hr-documents/load-memos?academicYear=${encodeURIComponent(academicYear())}`),
        loadTeachersForDocuments()
    ]);
    dutyMemoRows=dutyMemos;
    loadMemoRows=loadMemos;
    const teacherNames = new Map(teachers.map(t => [t.id,t.fio]));
    const dutyRows = dutyMemos.map(memo => {
        const edit=['DRAFT','ISSUED','SIGNED'].includes(memo.status)?` <button data-edit-memo="${memo.id}">Редактировать</button>`:'';
        const next=memo.status==='DRAFT'?` <button data-issue-memo="${memo.id}">Выпустить</button>`
            :memo.status==='ISSUED'?` <button data-sign-memo="${memo.id}">Подписана</button>`
            :memo.status==='SIGNED'?` <button data-receive-memo="${memo.id}">Получена кадрами</button>`:'';
        const remove=memo.deletable?` <button data-delete-memo="${memo.id}">Удалить</button>`
            :!['ANNULLED','ARCHIVED'].includes(memo.status)?` <button data-annul-memo="${memo.id}">Аннулировать</button>`:'';
        const statusLabel=memo.status==='SIGNED'?'Подписана':STATUS_LABELS[memo.status]||memo.status;
        return {sortDate:memo.documentDate||memo.createdAt,type:'Дополнительная обязанность',html:`<tr><td>${esc(formatDate(memo.documentDate))}</td><td>${esc(teacherNames.get(memo.teacherId) || memo.teacherId || 'Не указан')}</td><td>Дополнительная обязанность</td><td>${esc(memo.assignmentName || memo.title)}</td><td>${esc(statusLabel)}${memo.contractId?'':' · ожидает договор'}</td><td><a href="/api/hr-documents/memos/${memo.id}/download">DOCX</a>${edit}${next}${remove}</td></tr>`};
    });
    const loadRows = loadMemos.map(memo => {
        const next=memo.status==='PROCESSED'?` <button data-sign-load-memo="${memo.id}">Подписана</button>`
            :memo.status==='SIGNED'?` <button data-receive-load-memo="${memo.id}">Получена кадрами</button>`:'';
        const remove=!['ANNULLED','ARCHIVED'].includes(memo.status)?` <button data-annul-load-memo="${memo.id}">Аннулировать</button>`
            :memo.status==='ANNULLED'?` <button data-delete-load-memo="${memo.id}">Удалить</button>`:'';
        const statusLabel=memo.status==='SIGNED'?'Подписана':STATUS_LABELS[memo.status]||memo.status;
        return {sortDate:memo.startDate||memo.createdAt,type:'Изменение нагрузки',html:`<tr><td>${esc(formatDate(memo.startDate))}</td><td>${esc(teacherNames.get(memo.teacherId) || memo.fioTeacher || 'Не указан')}</td><td>Изменение нагрузки</td><td>Нагрузка с ${esc(formatDate(memo.startDate))}</td><td>${esc(statusLabel)}${memo.contractId?'':' · ожидает договор'}</td><td><a href="/api/hr-documents/load-memos/${memo.id}/download">DOCX</a>${next}${remove}</td></tr>`};
    });
    $('#memo-body').innerHTML = [...dutyRows,...loadRows].sort((a,b)=>String(b.sortDate).localeCompare(String(a.sortDate))).map(row=>row.html).join('');
}

async function editIssuedMemo(memoId) {
    const memo=dutyMemoRows.find(item=>String(item.id)===String(memoId));
    if(!memo)throw new Error('Служебная записка не найдена в текущем списке');
    await loadReferenceData();
    const automaticClause24=!memo.separateAgreement&&(memo.contractClause||'2.4')==='2.4';
    const teacher=teachersCache.find(item=>String(item.id)===String(memo.teacherId));
    const contracts=await api(`/api/hr-documents/contracts?teacherId=${memo.teacherId}`);
    openEditor('Редактирование служебной записки',
        row('Работник',`<b>${esc(teacher?.fio||`ID ${memo.teacherId}`)}</b>`,'Работник связан по постоянному ID и при редактировании не меняется.')+
        row('Трудовой договор',`<select name="contractId"><option value="">Можно заполнить позже</option>${contracts.filter(item=>item.active).map(item=>option(item.id,`№ ${item.contractNumber} от ${item.contractDate} — ${item.positionName}`,String(item.id)===String(memo.contractId))).join('')}</select>`)+
        row('Обязанность из справочника',`<select name="catalogItemId"><option value="">Ручная формулировка</option>${catalogCache.map(item=>option(item.id,`${item.name} — ${CATEGORY_LABELS[item.category]||item.category}`,String(item.id)===String(memo.catalogItemId))).join('')}</select>`)+
        row('Обязанность или работа',`<input name="assignmentName" value="${esc(memo.assignmentName)}" required>`)+
        contractClauseRow('memo-edit',memo.contractClause||'2.4')+
        row('Есть отдельный функционал?',`<div class="inline-choice"><label><input type="radio" name="separate" value="false"${memo.separateAgreement?'':' checked'}> Нет — изменить выбранный пункт</label><label><input type="radio" name="separate" value="true"${memo.separateAgreement?' checked':''}> Да — отдельное соглашение</label></div>`)+
        row('Дополнительные обязанности',`<textarea name="dutiesText">${esc(memo.dutiesText)}</textarea>`)+
        row('Сумма в месяц',`<input name="amount" type="number" min="0" step="0.01" value="${esc(memo.amount)}" required>`,'Например, если директор изменил сумму, укажите новую сумму и сохраните.')+
        row('Период',`<div class="hr-toolbar"><input name="validFrom" type="date" value="${esc(memo.validFrom)}" required><span>—</span><input name="validTo" type="date" value="${esc(memo.validTo)}" required></div>`)+
        row('Дата служебной записки',`<input name="documentDate" type="date" value="${esc(memo.documentDate)}">`)+
        row('Текст документов',documentTextOverrides(automaticClause24?'':memo.assignmentText,automaticClause24?'':memo.agreementText,'memo-edit'),automaticClause24?'Для пункта 2.4 текст будет заново собран строго по утверждённому образцу.':'Если автоматический текст не правили вручную, система пересоберёт его с новой суммой.')+
        row('Справочник','<label><input name="saveTemplate" type="checkbox"> Сохранить этот вариант как шаблон</label>'),
        async form=>{
            await api(`/api/hr-documents/memos/${memo.id}`,json('PUT',{
                academicYear:memo.academicYear,teacherId:memo.teacherId,
                contractId:form.get('contractId')?+form.get('contractId'):null,
                catalogItemId:form.get('catalogItemId')?+form.get('catalogItemId'):null,
                title:memo.title,documentDate:form.get('documentDate')||null,
                assignmentName:form.get('assignmentName'),assignmentText:form.get('memo'),
                agreementText:form.get('agreement'),contractClause:form.get('separate')==='true'?null:readClause(form,'memo-edit'),
                dutiesText:form.get('dutiesText'),amount:form.get('amount')||null,
                validFrom:form.get('validFrom'),validTo:form.get('validTo'),
                separateAgreement:form.get('separate')==='true',
                saveAsTemplate:form.get('saveTemplate')==='on',itemsJson:null
            }));
            await Promise.all([loadMemos(),loadAgreements(),loadJournal()]);
            showNotice('Изменения сохранены. Служебка возвращена в черновик: её нужно снова выпустить и подписать.');
        },
        ()=>{
            const update=()=>{
                const separate=document.querySelector('input[name="separate"]:checked')?.value==='true';
                setContractClauseVisibility('memo-edit',separate);
            };
            document.querySelectorAll('input[name="separate"]').forEach(radio=>radio.addEventListener('change',update));
            update();
        }
    );
}

async function openMemoArchive() {
    const [dutyArchive,loadArchive,teachers]=await Promise.all([
        api(`/api/hr-documents/memos/archive?academicYear=${encodeURIComponent(academicYear())}`),
        api(`/api/hr-documents/load-memos/archive?academicYear=${encodeURIComponent(academicYear())}`),
        loadTeachersForDocuments()
    ]);
    const names=new Map(teachers.map(item=>[item.id,item.fio]));
    const rows=[
        ...dutyArchive.map(memo=>({date:memo.documentDate||memo.createdAt,teacher:names.get(memo.teacherId)||`ID ${memo.teacherId}`,type:'Дополнительная обязанность',assignment:memo.assignmentName||memo.title,reason:memo.archiveReason,download:`/api/hr-documents/memos/${memo.id}/download`})),
        ...loadArchive.map(memo=>({date:memo.startDate||memo.createdAt,teacher:names.get(memo.teacherId)||memo.fioTeacher||`ID ${memo.teacherId}`,type:'Изменение нагрузки',assignment:`Нагрузка с ${formatDate(memo.startDate)}`,reason:memo.archiveReason,download:`/api/hr-documents/load-memos/${memo.id}/download`}))
    ].sort((a,b)=>String(b.date||'').localeCompare(String(a.date||'')));
    $('#memo-archive-body').innerHTML=rows.length?rows.map(item=>`<tr><td>${esc(formatDate(item.date))}</td><td>${esc(item.teacher)}</td><td>${esc(item.type)}</td><td>${esc(item.assignment)}</td><td>${esc(item.reason||'Дополнительное соглашение аннулировано')}</td><td><a href="${item.download}">DOCX</a></td></tr>`).join(''):'<tr><td colspan="6">В архиве пока нет служебных записок</td></tr>';
    $('#memo-archive-dialog').showModal();
}

$('#add-memo').addEventListener('click', async () => {
    const trigger = $('#add-memo'), originalText = trigger.textContent;
    trigger.disabled = true; trigger.textContent = 'Загрузка…';
    let referenceWarning = '';
    try { referenceWarning = await loadReferenceData(); }
    catch (error) { showReferenceLoadError(error); return; }
    finally { trigger.disabled = false; trigger.textContent = originalText; }
    const period = defaultPeriod();
    openEditor('Создание служебной записки',
        (referenceWarning ? row('Внимание', `<div class="muted">${esc(referenceWarning)}</div>`) : '') +
        row('Работник', `<select id="memo-teacher" name="teacherId" required><option value="">Выберите работника</option>${teachersCache.map(t=>option(t.id,t.fio)).join('')}</select>`,'Служебная записка будет связана с постоянным ID педагога.') +
        row('Трудовой договор', '<select id="memo-contract" name="contractId"><option value="">Можно заполнить позже</option></select>','Договор необязателен для служебной записки. После его заполнения система автоматически создаст и привяжет допсоглашение.') +
        row('Обязанность из справочника', `<div class="hr-toolbar"><select id="memo-catalog" name="catalogItemId"><option value="">Добавить вручную</option>${catalogCache.map(c=>option(c.id,`${c.name} — ${CATEGORY_LABELS[c.category]||c.category}`)).join('')}</select><button id="memo-manual" type="button">Добавить вручную</button></div>`,'Готовый вариант можно отредактировать для конкретного работника.') +
        row('Обязанность или работа', '<input id="memo-assignment" name="assignmentName" required placeholder="Например: заведование кабинетом">','Этого названия достаточно: текст служебной записки система сформирует автоматически.') +
        contractClauseRow('memo','2.4','2.1 формируется из нагрузки, 2.4 — из дополнительных функций, 2.5 будет формироваться из отдельной таблицы стимулирующих выплат.') +
        row('Есть отдельный функционал?', '<div class="inline-choice"><label><input type="radio" name="separate" value="false" checked> Нет — изменить выбранный пункт</label><label><input type="radio" name="separate" value="true"> Да — отдельное соглашение</label></div>','Выберите «Да», если кроме названия работы нужно закрепить отдельный перечень обязанностей.') +
        `<div id="duties-label" class="field-label hidden">Дополнительные обязанности</div><div id="duties-control" class="field-control hidden"><textarea id="memo-duties" name="dutiesText" placeholder="Перечислите обязанности отдельными строками"></textarea><span class="field-help">Текст попадёт в отдельное дополнительное соглашение и может быть сохранён как шаблон.</span></div>` +
        row('Сумма в месяц', '<input id="memo-amount" name="amount" type="number" min="0" step="0.01" required>') +
        row('Период', `<div class="hr-toolbar"><input name="validFrom" type="date" value="${period.from}" required><span>—</span><input name="validTo" type="date" value="${period.to}" required></div>`) +
        row('Дата служебной записки', `<input name="documentDate" type="date" value="${new Date().toISOString().slice(0,10)}">`) +
        row('Текст документов', documentTextOverrides('','','memo'),'Обычно этот раздел открывать не нужно: ниже находятся только ручные исправления и примеры.') +
        row('Справочник', '<label><input id="memo-save-template" name="saveTemplate" type="checkbox"> Сохранить ручной вариант для дальнейшего выбора</label>'),
        async form => {
            const separate=form.get('separate')==='true';
            const created=await api('/api/hr-documents/memos', json('POST',{academicYear:academicYear(),teacherId:+form.get('teacherId'),contractId:form.get('contractId')?+form.get('contractId'):null,catalogItemId:form.get('catalogItemId')?+form.get('catalogItemId'):null,title:null,documentDate:form.get('documentDate')||null,assignmentName:form.get('assignmentName'),assignmentText:form.get('assignmentText'),agreementText:form.get('agreementText'),contractClause:separate?null:readClause(form,'memo'),dutiesText:form.get('dutiesText'),amount:form.get('amount')||null,validFrom:form.get('validFrom'),validTo:form.get('validTo'),separateAgreement:separate,saveAsTemplate:form.get('saveTemplate')==='on',itemsJson:null}));
            try {
                await loadMemos();
                showNotice(`Служебная записка создана и добавлена в таблицу${created?.id ? ` (ID ${created.id})` : ''}.`);
            } catch (error) {
                showNotice(`Служебная записка создана${created?.id ? ` (ID ${created.id})` : ''}, но таблица не обновилась: ${error.message}`,true);
            }
        },
        () => {
            const teacherSelect = $('#memo-teacher'), contractSelect = $('#memo-contract'), catalogSelect = $('#memo-catalog');
            async function updateContracts() {
                const teacherId = teacherSelect.value; contractSelect.innerHTML = '<option value="">Можно заполнить позже</option>';
                if (!teacherId) return;
                const contracts = await api(`/api/hr-documents/contracts?teacherId=${teacherId}`);
                const activeContracts = contracts.filter(c=>c.active);
                contractSelect.innerHTML += activeContracts.map(c=>option(c.id,`№ ${c.contractNumber} от ${c.contractDate} — ${c.positionName}`,c.primaryContract)).join('');
                if (!activeContracts.length) contractSelect.innerHTML = '<option value="">Пока нет договора — служебка всё равно сформируется</option>';
            }
            function setSeparate(value) {
                const radio = document.querySelector(`input[name="separate"][value="${value}"]`); if (radio) radio.checked = true;
                const show = value === 'true'; $('#duties-label').classList.toggle('hidden',!show); $('#duties-control').classList.toggle('hidden',!show); $('#memo-duties').required = show;
                setContractClauseVisibility('memo',show);
            }
            function applyCatalog() {
                const item = catalogCache.find(c=>String(c.id)===catalogSelect.value);
                if (!item) return;
                const clause=item.contractClause||'2.4';
                const automaticClause24=clause==='2.4'&&!item.separateAgreement;
                $('#memo-assignment').value=item.name||'';
                $('#memo-text').value=automaticClause24?'':item.memoText||'';
                $('#memo-agreement').value=automaticClause24?'':item.agreementText||'';
                setClausePicker('memo',clause);
                $('#memo-duties').value=item.dutiesText||''; $('#memo-amount').value=item.defaultAmount??''; setSeparate(String(Boolean(item.separateAgreement)));
                $('#memo-save-template').checked=false;
            }
            teacherSelect.addEventListener('change',updateContracts); catalogSelect.addEventListener('change',applyCatalog);
            $('#memo-manual').addEventListener('click',()=>{catalogSelect.value='';$('#memo-assignment').value='';$('#memo-text').value='';$('#memo-agreement').value='';$('#memo-duties').value='';$('#memo-amount').value='';setClausePicker('memo','2.4');$('#memo-save-template').checked=true;$('#memo-assignment').focus();});
            document.querySelectorAll('input[name="separate"]').forEach(r=>r.addEventListener('change',()=>setSeparate(r.value)));
            setSeparate(document.querySelector('input[name="separate"]:checked')?.value||'false');
        }
    );
});

$('#memo-body').addEventListener('click', async event => {
    try{
        if (event.target.dataset.editMemo) await editIssuedMemo(event.target.dataset.editMemo);
        if (event.target.dataset.issueMemo) { await api(`/api/hr-documents/memos/${event.target.dataset.issueMemo}/status`,json('POST',{status:'ISSUED'})); await loadMemos(); showNotice('Служебная записка выпущена. Следующий этап — подпись директора.'); }
        if (event.target.dataset.signMemo) { await api(`/api/hr-documents/memos/${event.target.dataset.signMemo}/status`,json('POST',{status:'SIGNED'})); await loadMemos(); showNotice('Служебная записка отмечена подписанной. Теперь её можно передать кадрам.'); }
        if (event.target.dataset.receiveMemo) { await api(`/api/hr-documents/memos/${event.target.dataset.receiveMemo}/status`,json('POST',{status:'RECEIVED_BY_HR'})); await Promise.all([loadMemos(),loadJournal(),loadAgreements()]); }
        if (event.target.dataset.annulMemo) { const reason=prompt('Причина аннулирования'); if(reason){await api(`/api/hr-documents/memos/${event.target.dataset.annulMemo}/annul`,json('POST',{reason}));await loadMemos();} }
        if (event.target.dataset.deleteMemo && confirm('Удалить служебную записку без возможности восстановления?')) { await api(`/api/hr-documents/memos/${event.target.dataset.deleteMemo}`,{method:'DELETE'}); await Promise.all([loadMemos(),loadJournal(),loadAgreements()]); showNotice('Служебная записка удалена.'); }
        if (event.target.dataset.signLoadMemo) { await api(`/api/hr-documents/load-memos/${event.target.dataset.signLoadMemo}/sign`,{method:'POST'}); await loadMemos(); showNotice('Служебная записка по нагрузке отмечена подписанной.'); }
        if (event.target.dataset.receiveLoadMemo) { await api(`/api/hr-documents/load-memos/${event.target.dataset.receiveLoadMemo}/receive`,{method:'POST'}); await Promise.all([loadMemos(),loadJournal(),loadAgreements()]); }
        if (event.target.dataset.annulLoadMemo) { const reason=prompt('Причина аннулирования'); if(reason){await api(`/api/hr-documents/load-memos/${event.target.dataset.annulLoadMemo}/annul`,json('POST',{reason}));await Promise.all([loadMemos(),loadJournal()]);} }
        if (event.target.dataset.deleteLoadMemo && confirm('Удалить аннулированную служебную записку по нагрузке без возможности восстановления?')) { await api(`/api/hr-documents/load-memos/${event.target.dataset.deleteLoadMemo}`,{method:'DELETE'}); await Promise.all([loadMemos(),loadJournal()]); showNotice('Аннулированная служебная записка удалена.'); }
    }catch(error){showNotice(error.message,true);}
});
$('#memo-archive').addEventListener('click',()=>openMemoArchive().catch(error=>showNotice(error.message,true)));
$('#close-memo-archive').addEventListener('click',()=>$('#memo-archive-dialog').close());

async function loadCatalog() {
    catalogCache = await api('/api/hr-documents/catalog');
    $('#catalog-body').innerHTML = catalogCache.map(item=>`<tr><td>${esc(item.name)}</td><td>${esc(CATEGORY_LABELS[item.category]||item.category)}</td><td>${esc(item.contractClause)}</td><td>${esc(item.defaultAmount)}</td><td>${item.separateAgreement?'Да':'Нет'}</td><td><button data-edit-catalog="${item.id}">Изменить</button> <button data-delete-catalog="${item.id}">Удалить</button></td></tr>`).join('');
}
function editCatalog(item = null) {
    const automaticClause24=Boolean(item)&&!item.separateAgreement&&(item.contractClause||'2.4')==='2.4';
    openEditor(item ? 'Изменить выплату или работу' : 'Добавить выплату или работу',
        row('Название обязанности или выплаты', `<input name="name" value="${esc(item?.name)}" required placeholder="Например: заведование кабинетом">`) +
        row('Категория', `<select name="category"><option value="COMPENSATION" ${item?.category==='COMPENSATION'?'selected':''}>Компенсационная выплата</option><option value="INCENTIVE" ${item?.category==='INCENTIVE'?'selected':''}>Стимулирующая выплата</option><option value="ADDITIONAL_WORK" ${item?.category==='ADDITIONAL_WORK'?'selected':''}>Дополнительная работа</option></select>`) +
        contractClauseRow('catalog',item?.contractClause||'2.4','Источники разделены: 2.1 — нагрузка, 2.4 — дополнительные функции, 2.5 — стимулирующие выплаты из отдельной таблицы.') + row('Стандартная сумма', `<input name="amount" type="number" min="0" step="0.01" value="${esc(item?.defaultAmount)}">`) +
        row('Отдельное соглашение', `<label><input name="separate" type="checkbox" ${item?.separateAgreement?'checked':''}> Есть отдельный перечень дополнительных обязанностей</label>`,'Если флажок снят, выплата изменяет выбранный пункт договора. Если установлен — создаётся отдельный допник с функционалом.') +
        row('Дополнительные обязанности', `<textarea name="duties" placeholder="Например: контролировать состояние кабинета; вести журнал инструктажей; обеспечивать сохранность оборудования.">${esc(item?.dutiesText)}</textarea>`,'Заполняется, когда выбран отдельный функционал.') +
        row('Текст документов', documentTextOverrides(automaticClause24?'':item?.memoText,automaticClause24?'':item?.agreementText,'catalog'),'Для стандартного пункта 2.4 старые ручные формулировки не используются.'),
        async form => {
            const separate=form.get('separate')==='on';
            const clause=separate?null:readClause(form,'catalog');
            const automaticClause24=clause==='2.4'&&!separate;
            await api(item ? `/api/hr-documents/catalog/${item.id}` : '/api/hr-documents/catalog',json(item ? 'PUT' : 'POST',{name:form.get('name'),category:form.get('category'),contractClause:clause,defaultAmount:form.get('amount')||null,memoText:automaticClause24?null:form.get('memo'),agreementText:automaticClause24?null:form.get('agreement'),dutiesText:form.get('duties'),separateAgreement:separate,active:true}));
            await loadCatalog();
        },
        () => {
            const separate=document.querySelector('input[name="separate"]');
            const update=()=>setContractClauseVisibility('catalog',Boolean(separate?.checked));
            separate?.addEventListener('change',update);
            update();
        }
    );
}
$('#add-catalog').addEventListener('click', () => editCatalog());
$('#catalog-body').addEventListener('click', async event => {
    const item=catalogCache.find(x=>String(x.id)===event.target.dataset.editCatalog);if(item)editCatalog(item);
    if(event.target.dataset.deleteCatalog&&confirm('Удалить эту позицию из справочника?')){await api(`/api/hr-documents/catalog/${event.target.dataset.deleteCatalog}`,{method:'DELETE'});await loadCatalog();showNotice('Позиция удалена из справочника.');}
});

async function loadPersonalData() {
    if (!canViewPersonal() || !$('#personal-body')) return;
    personalRows = await api('/api/hr-documents/personal-data');
    renderPersonalData();
}
function renderPersonalData() {
    const body=$('#personal-body');if(!body)return;
    const query=$('#personal-search').value.trim().toLowerCase();
    const rows=personalRows.filter(item=>!query||JSON.stringify(item).toLowerCase().includes(query));
    body.innerHTML=rows.length?rows.map(item=>{
        const data=item.personalData;
        const passport=data
            ? `<b>${esc([data.passportSeries,data.passportNumber].filter(Boolean).join(' ')||'Не заполнен')}</b><br><span class="muted">${esc(data.passportIssuedBy||'')} ${data.passportIssueDate?`от ${esc(formatDate(data.passportIssueDate))}`:''}${data.passportDepartmentCode?` · код ${esc(data.passportDepartmentCode)}`:''}</span>`
            : '<span class="muted">Не заполнен</span>';
        const contacts=data
            ? `${esc(data.registrationAddress||'Адрес регистрации не заполнен')}<br><span class="muted">${esc(data.actualAddress||'Фактический адрес не заполнен')}${data.phone?` · ${esc(data.phone)}`:''}${data.inn?` · ИНН ${esc(data.inn)}`:''}${data.snils?` · СНИЛС ${esc(data.snils)}`:''}</span>`
            : '<span class="muted">Не заполнены</span>';
        const activeContracts=(item.contracts||[]).filter(contract=>contract.active);
        const contractHtml=activeContracts.length?activeContracts.map(contract=>`<div><b>№ ${esc(contract.contractNumber)}</b> от ${esc(formatDate(contract.contractDate))}<br><span class="muted">${esc(contract.positionName)}${contract.primaryContract?' · основной':''}${contract.loadHoursMayBeIncludedInRate?' · часы могут входить в ставку':''}</span></div>`).join(''):'<span class="muted">Не заполнен</span>';
        const contractButtons=activeContracts.map(contract=>`<button data-personal-contract="${contract.id}" data-teacher="${item.teacherId}">Изменить договор № ${esc(contract.contractNumber)}</button>`).join(' ');
        return `<tr><td><b>${esc(item.fio)}</b><br><span class="muted">ID ${item.teacherId}</span></td><td>${passport}</td><td>${contacts}</td><td>${contractHtml}</td><td>${data?.complete?'<span class="success">Достаточно для DOCX</span>':'Нужно заполнить обязательные данные'}${data?.updatedAt?`<br><span class="muted">Обновлено ${esc(data.updatedAt.replace('T',' '))}</span>`:''}</td><td><button data-personal-edit="${item.teacherId}">${data?'Изменить данные':'Заполнить данные'}</button> ${contractButtons||`<button data-personal-contract="" data-teacher="${item.teacherId}">Добавить договор</button>`}</td></tr>`;
    }).join(''):'<tr><td colspan="6">Работники не найдены</td></tr>';
}
$('#personal-search').addEventListener('input',renderPersonalData);
$('#reload-personal').addEventListener('click',()=>loadPersonalData().catch(error=>showNotice(error.message,true)));
$('#personal-body').addEventListener('click',event=>{
    if(event.target.dataset.personalEdit)editPersonal(+event.target.dataset.personalEdit).catch(error=>showNotice(error.message,true));
    if(Object.prototype.hasOwnProperty.call(event.target.dataset,'personalContract')){
        const contractId=event.target.dataset.personalContract?+event.target.dataset.personalContract:null;
        openContractEditor(+event.target.dataset.teacher,contractId).catch(error=>showNotice(error.message,true));
    }
});

$('#personal-import-button').addEventListener('click', () => $('#personal-import').click());
$('#personal-import').addEventListener('change', async event => {
    if (!event.target.files[0]) return;
    const form = new FormData(); form.append('file',event.target.files[0]);
    const result = await api('/api/hr-documents/personal-data/import',{method:'POST',body:form});
    showNotice(`Обновлено: ${result.updated}, пропущено: ${result.skipped}`); event.target.value='';
    await Promise.all([loadJournal(),loadPersonalData(),loadAgreements()]);
});

const notificationDate = $('#notification-date'), loadDate = $('#load-date'), notificationBody = $('#tb');
function defaultLoadDate(){const year=String(academicYear()).split('/')[0];return /^\d{4}$/.test(year)?`${year}-09-01`:new Date().toISOString().slice(0,10);}
notificationDate.value=new Date().toISOString().slice(0,10); loadDate.value=defaultLoadDate();
async function loadNotifications(){const items=await api(`/api/teachers-notification?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}`);notificationBody.innerHTML=items.map(item=>`<tr><td>${esc(item.fio)}</td><td>${item.generated?(item.changed?'Изменено':'Выпущено'):'Не выпущено'}</td><td><button data-notice="${esc(item.fio)}">Скачать</button></td></tr>`).join('');}
notificationBody.addEventListener('click',async event=>{if(!event.target.dataset.notice)return;const fio=event.target.dataset.notice;const response=await fetch(`/api/teachers-notification/download/${encodeURIComponent(fio)}?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}&notificationDate=${notificationDate.value}`,{method:'POST'});const link=document.createElement('a');link.href=URL.createObjectURL(await response.blob());link.download=`Уведомление_${fio}.docx`;link.click();loadNotifications();});
$('#all').addEventListener('click',async()=>{const response=await fetch(`/api/teachers-notification/download-all?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}&notificationDate=${notificationDate.value}`,{method:'POST'});const link=document.createElement('a');link.href=URL.createObjectURL(await response.blob());link.download=`Предварительная нагрузка_${loadDate.value}.zip`;link.click();loadNotifications();});

loadJournal(); loadNotifications();
const permissionPoll=setInterval(()=>{if(!window.tarificationAuth)return;clearInterval(permissionPoll);if(!canViewPersonal()){document.querySelector('[data-tab="personal"]')?.remove();$('#personal')?.remove();}renderJournal();},50);
