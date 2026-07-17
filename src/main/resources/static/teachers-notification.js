const $ = selector => document.querySelector(selector);
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const academicYear = () => window.getStoredAcademicYear ? window.getStoredAcademicYear() : '';
const canViewPersonal = () => Boolean(window.tarificationAuth?.admin || window.tarificationTabPermissions?.HR_PERSONAL_DATA?.canView);
const CATEGORY_LABELS = {COMPENSATION:'Компенсационная выплата', INCENTIVE:'Стимулирующая выплата', ADDITIONAL_WORK:'Дополнительная работа'};
const STATUS_LABELS = {WAITING_FOR_MEMO:'Ожидает служебку',DRAFT:'Черновик',READY:'Готов',PROCESSED:'Выпущена',ISSUED:'Выпущена',RECEIVED_BY_HR:'Получена кадрами',EXECUTED:'Исполнена',SIGNED:'Подписано',REQUIRES_DECISION:'Требуется решение',ANNULLED:'Аннулирована'};

async function api(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(await response.text());
    return response.headers.get('content-type')?.includes('json') ? response.json() : response;
}
function json(method, body) { return {method, headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)}; }
function row(label, control, help = '') {
    return `<div class="field-label">${label}</div><div class="field-control">${control}${help ? `<span class="field-help">${help}</span>` : ''}</div>`;
}
function option(value, label, selected = false) { return `<option value="${esc(value)}"${selected ? ' selected' : ''}>${esc(label)}</option>`; }

document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('.hr-panel').forEach(panel => panel.hidden = true);
    $('#' + button.dataset.tab).hidden = false;
    if (button.dataset.tab === 'memos') loadMemos();
    if (button.dataset.tab === 'catalog') loadCatalog();
}));

let journal = [];
async function loadJournal() {
    journal = await api(`/api/hr-documents/journal?academicYear=${encodeURIComponent(academicYear())}`);
    renderJournal();
}
function renderJournal() {
    const query = $('#journal-search').value.toLowerCase();
    const status = $('#journal-status').value;
    $('#journal-body').innerHTML = journal
        .filter(item => (!query || JSON.stringify(item).toLowerCase().includes(query)) && (!status || item.actionRequired === status))
        .map(item => `<tr><td>${esc(item.fio)}</td><td>№ ${esc(item.contractNumber)}</td><td>${esc(item.position)}</td><td>${item.agreements.length ? item.agreements.map(agreement=>renderAgreement(agreement,item.contractId)).join('') : 'Нет'}</td><td>${esc(item.actionRequired)}</td><td>${canViewPersonal() ? `<button data-personal="${item.teacherId}">Данные</button>` : ''}<button data-agreement="${item.contractId}">Черновик допника</button></td></tr>`).join('');
}
function renderAgreement(agreement, contractId) {
    const waiting = agreement.status === 'WAITING_FOR_MEMO';
    const changeMode = (agreement.serviceMemoId || agreement.loadServiceMemoId) && ['WAITING_FOR_MEMO','DRAFT','READY','REQUIRES_DECISION'].includes(agreement.status)
        ? `<button data-change-mode="${agreement.id}" data-contract="${contractId}">Способ изменения</button>` : '';
    const actions = waiting
        ? '<span class="muted">Выпуск заблокирован до получения служебки</span>'
        : `<button data-download="${agreement.id}">DOCX</button> <button data-upload="${agreement.id}">Заменить</button> <button data-sign="${agreement.id}">Подписано</button>`;
    return `<div><b>${esc(agreement.internalNumber)}</b> · ${esc(agreement.summary || agreement.kind)} · ${esc(STATUS_LABELS[agreement.status] || agreement.status)} ${changeMode} ${actions} <button data-annul="${agreement.id}">Аннулировать</button></div>`;
}
$('#journal-search').addEventListener('input', renderJournal);
$('#journal-status').addEventListener('change', renderJournal);
$('#reload-journal').addEventListener('click', loadJournal);

function openEditor(title, fields, onSave, afterOpen) {
    $('#editor-title').textContent = title;
    $('#editor-fields').innerHTML = fields;
    $('#editor-error').textContent = '';
    const dialog = $('#editor');
    dialog.showModal();
    if (afterOpen) afterOpen();
    $('#editor-save').onclick = async event => {
        event.preventDefault();
        try {
            await onSave(new FormData($('#editor-form')));
            dialog.close();
            await loadJournal();
        } catch (error) { $('#editor-error').textContent = error.message; }
    };
}

let teachersCache = [];
let catalogCache = [];
async function loadReferenceData() {
    [teachersCache, catalogCache] = await Promise.all([api('/api/hr-documents/teachers'), api('/api/hr-documents/catalog')]);
}

$('#add-contract').addEventListener('click', async () => {
    await loadReferenceData();
    openEditor('Трудовой договор',
        row('Работник', `<select name="teacherId" required><option value="">Выберите работника</option>${teachersCache.map(t => option(t.id, t.fio)).join('')}</select>`) +
        row('Номер договора', '<input name="number" required>') + row('Дата договора', '<input name="date" type="date" required>') +
        row('Должность', '<input name="position" required>') + row('Начало работы', '<input name="start" type="date">') + row('Окончание', '<input name="end" type="date">'),
        form => api('/api/hr-documents/contracts', json('POST', {teacherId:+form.get('teacherId'),contractNumber:form.get('number'),contractDate:form.get('date'),positionName:form.get('position'),startDate:form.get('start')||null,endDate:form.get('end')||null,primaryContract:true,active:true}))
    );
});

async function editPersonal(teacherId) {
    const data = await api(`/api/hr-documents/personal-data/${teacherId}`) || {};
    openEditor('Персональные данные',
        row('Серия паспорта', `<input name="series" value="${esc(data.passportSeries)}">`) + row('Номер паспорта', `<input name="number" value="${esc(data.passportNumber)}">`) +
        row('Кем выдан', `<input name="issuedBy" value="${esc(data.passportIssuedBy)}">`) + row('Дата выдачи', `<input name="issueDate" type="date" value="${esc(data.passportIssueDate)}">`) +
        row('Код подразделения', `<input name="code" value="${esc(data.passportDepartmentCode)}">`) + row('Адрес регистрации', `<textarea name="registration">${esc(data.registrationAddress)}</textarea>`) +
        row('Фактический адрес', `<textarea name="actual">${esc(data.actualAddress)}</textarea>`) + row('Телефон', `<input name="phone" value="${esc(data.phone)}">`) +
        row('ИНН', `<input name="inn" value="${esc(data.inn)}">`) + row('СНИЛС', `<input name="snils" value="${esc(data.snils)}">`),
        form => api(`/api/hr-documents/personal-data/${teacherId}`, json('PUT', {teacherId,passportSeries:form.get('series'),passportNumber:form.get('number'),passportIssuedBy:form.get('issuedBy'),passportIssueDate:form.get('issueDate')||null,passportDepartmentCode:form.get('code'),registrationAddress:form.get('registration'),actualAddress:form.get('actual'),phone:form.get('phone'),inn:form.get('inn'),snils:form.get('snils')}))
    );
}

function defaultPeriod() {
    const startYear = String(academicYear()).substring(0,4);
    return {from:`${startYear}-09-01`, to:`${Number(startYear)+1}-08-31`};
}
function editAgreement(contractId) {
    const period = defaultPeriod();
    openEditor('Черновик дополнительного соглашения',
        row('ID полученной служебки', '<input name="memoId" type="number">','Без полученной служебки выпустить документ будет нельзя.') +
        row('Дата документа', '<input name="date" type="date">') + row('Начало действия', `<input name="from" type="date" value="${period.from}" required>`) + row('Окончание', `<input name="to" type="date" value="${period.to}" required>`) +
        row('Тип соглашения', '<select name="kind"><option value="PAY_TERMS">Условия оплаты труда</option><option value="ADDITIONAL_WORK">Дополнительная работа</option></select>') +
        row('Способ изменения', '<select name="mode"><option value="AMEND">Внести изменение</option><option value="CANCEL_AND_RESTATE">Отменить и изложить заново</option></select>') +
        row('Краткое содержание', '<input name="summary">') + row('Сумма в месяц', '<input name="amount" type="number" step="0.01">') + row('Условия и обязанности', '<textarea name="conditions"></textarea>'),
        form => api('/api/hr-documents/agreements', json('POST', {contractId,serviceMemoId:form.get('memoId')?+form.get('memoId'):null,academicYear:academicYear(),documentDate:form.get('date')||null,validFrom:form.get('from'),validTo:form.get('to'),kind:form.get('kind'),changeMode:form.get('mode'),summary:form.get('summary'),conditionsJson:form.get('conditions'),totalAmount:form.get('amount')||null}))
    );
}

function editChangeMode(agreementId, contractId) {
    const contractRow = journal.find(item => String(item.contractId) === String(contractId));
    const agreement = contractRow?.agreements.find(item => String(item.id) === String(agreementId));
    const previous = (contractRow?.agreements || []).filter(item => String(item.id) !== String(agreementId) && item.status !== 'ANNULLED');
    openEditor('Способ оформления изменения',
        row('Юридическая формулировка', `<select id="change-mode" name="mode"><option value="AMEND" ${agreement?.changeMode==='AMEND'?'selected':''}>Внести изменение в предыдущее соглашение</option><option value="CANCEL_AND_RESTATE" ${agreement?.changeMode==='CANCEL_AND_RESTATE'?'selected':''}>Отменить предыдущее и считать условия действующими в новой редакции</option></select>`) +
        row('Предыдущее соглашение', `<select id="change-source" name="source"><option value="">Не выбрано — изменение трудового договора</option>${previous.map(item=>option(item.id,`№ ${item.internalNumber} от ${item.documentDate || 'без даты'} — ${item.summary || item.kind}`,String(item.id)===String(agreement?.replacesAgreementId))).join('')}</select>`,'Для отмены и новой редакции выбор предыдущего соглашения обязателен.'),
        form => api(`/api/hr-documents/agreements/${agreementId}/change-mode`,json('POST',{changeMode:form.get('mode'),replacesAgreementId:form.get('source')?+form.get('source'):null})),
        () => {
            const mode=$('#change-mode'),source=$('#change-source');
            const updateRequired=()=>source.required=mode.value==='CANCEL_AND_RESTATE';
            mode.addEventListener('change',updateRequired);updateRequired();
        }
    );
}

$('#journal-body').addEventListener('click', async event => {
    const target = event.target;
    if (target.dataset.download) location.href = `/api/hr-documents/agreements/${target.dataset.download}/download`;
    if (target.dataset.personal) editPersonal(+target.dataset.personal);
    if (target.dataset.agreement) editAgreement(+target.dataset.agreement);
    if (target.dataset.changeMode) editChangeMode(+target.dataset.changeMode,+target.dataset.contract);
    if (target.dataset.upload) {
        const input = document.createElement('input'); input.type = 'file'; input.accept = '.docx';
        input.onchange = async () => { const form = new FormData(); form.append('file', input.files[0]); await api(`/api/hr-documents/agreements/${target.dataset.upload}/upload`, {method:'POST',body:form}); loadJournal(); };
        input.click();
    }
    if (target.dataset.sign) { await api(`/api/hr-documents/agreements/${target.dataset.sign}/status`, json('POST',{status:'SIGNED'})); loadJournal(); }
    if (target.dataset.annul) { const reason = prompt('Причина аннулирования'); if (reason) { await api(`/api/hr-documents/agreements/${target.dataset.annul}/annul`, json('POST',{reason})); loadJournal(); } }
});

$('#batch-annual').addEventListener('click', async () => {
    if (!confirm('Создать черновики всем сотрудникам с основной нагрузкой?')) return;
    await api('/api/hr-documents/agreements/batch-annual', json('POST',{academicYear:academicYear(),documentDate:new Date().toISOString().slice(0,10),contractIds:[]}));
    loadJournal();
});

async function loadMemos() {
    const [dutyMemos, loadMemos, teachers] = await Promise.all([
        api(`/api/hr-documents/memos?academicYear=${encodeURIComponent(academicYear())}`),
        api(`/api/hr-documents/load-memos?academicYear=${encodeURIComponent(academicYear())}`),
        api('/api/hr-documents/teachers')
    ]);
    const teacherNames = new Map(teachers.map(t => [t.id,t.fio]));
    const dutyRows = dutyMemos.map(memo => ({sortDate:memo.documentDate||memo.createdAt,type:'Дополнительная обязанность',html:`<tr><td>${esc(memo.documentDate)}</td><td>${esc(teacherNames.get(memo.teacherId) || memo.teacherId || 'Не указан')}</td><td>Дополнительная обязанность</td><td>${esc(memo.assignmentName || memo.title)}</td><td>${esc(STATUS_LABELS[memo.status] || memo.status)}</td><td><a href="/api/hr-documents/memos/${memo.id}/download">DOCX</a>${memo.status==='DRAFT'?` <button data-issue-memo="${memo.id}">Выпустить</button>`:''}${memo.status==='ISSUED'?` <button data-receive-memo="${memo.id}">Получена кадрами</button>`:''} ${memo.status!=='ANNULLED'?`<button data-annul-memo="${memo.id}">Аннулировать</button>`:''}</td></tr>`}));
    const loadRows = loadMemos.map(memo => ({sortDate:memo.startDate||memo.createdAt,type:'Изменение нагрузки',html:`<tr><td>${esc(memo.startDate)}</td><td>${esc(teacherNames.get(memo.teacherId) || memo.fioTeacher || 'Не указан')}</td><td>Изменение нагрузки</td><td>Нагрузка с ${esc(memo.startDate)}</td><td>${esc(STATUS_LABELS[memo.status] || memo.status)}</td><td><a href="/api/hr-documents/load-memos/${memo.id}/download">DOCX</a>${memo.status==='PROCESSED'?` <button data-receive-load-memo="${memo.id}">Получена кадрами</button>`:''} ${!['ANNULLED','ARCHIVED'].includes(memo.status)?`<button data-annul-load-memo="${memo.id}">Аннулировать</button>`:''}</td></tr>`}));
    $('#memo-body').innerHTML = [...dutyRows,...loadRows].sort((a,b)=>String(b.sortDate).localeCompare(String(a.sortDate))).map(row=>row.html).join('');
}

$('#add-memo').addEventListener('click', async () => {
    await loadReferenceData();
    const period = defaultPeriod();
    openEditor('Создание служебной записки',
        row('Работник', `<select id="memo-teacher" name="teacherId" required><option value="">Выберите работника</option>${teachersCache.map(t=>option(t.id,t.fio)).join('')}</select>`,'Служебная записка будет связана с постоянным ID педагога.') +
        row('Трудовой договор', '<select id="memo-contract" name="contractId" required><option value="">Сначала выберите работника</option></select>','Без действующего договора служебку и допсоглашение создать нельзя.') +
        row('Обязанность из справочника', `<div class="hr-toolbar"><select id="memo-catalog" name="catalogItemId"><option value="">Добавить вручную</option>${catalogCache.map(c=>option(c.id,`${c.name} — ${CATEGORY_LABELS[c.category]||c.category}`)).join('')}</select><button id="memo-manual" type="button">Добавить вручную</button></div>`,'Готовый вариант можно отредактировать для конкретного работника.') +
        row('Название назначения', '<input id="memo-assignment" name="assignmentName" required placeholder="Например: заведование кабинетом">') +
        row('Формулировка для служебки', '<textarea id="memo-text" name="assignmentText" required></textarea>') +
        row('Отдельный дополнительный функционал?', '<div class="inline-choice"><label><input type="radio" name="separate" value="false" checked> Нет — пункт 2.4</label><label><input type="radio" name="separate" value="true"> Да — отдельное соглашение</label></div>','При выборе «Да» появится перечень дополнительных обязанностей.') +
        `<div id="duties-label" class="field-label hidden">Дополнительные обязанности</div><div id="duties-control" class="field-control hidden"><textarea id="memo-duties" name="dutiesText" placeholder="Перечислите обязанности отдельными строками"></textarea><span class="field-help">Текст попадёт в отдельное дополнительное соглашение и может быть сохранён как шаблон.</span></div>` +
        row('Сумма в месяц', '<input id="memo-amount" name="amount" type="number" min="0" step="0.01" required>') +
        row('Период', `<div class="hr-toolbar"><input name="validFrom" type="date" value="${period.from}" required><span>—</span><input name="validTo" type="date" value="${period.to}" required></div>`) +
        row('Дата служебной записки', `<input name="documentDate" type="date" value="${new Date().toISOString().slice(0,10)}">`) +
        row('Справочник', '<label><input id="memo-save-template" name="saveTemplate" type="checkbox"> Сохранить ручной вариант для дальнейшего выбора</label>'),
        async form => {
            await api('/api/hr-documents/memos', json('POST',{academicYear:academicYear(),teacherId:+form.get('teacherId'),contractId:form.get('contractId')?+form.get('contractId'):null,catalogItemId:form.get('catalogItemId')?+form.get('catalogItemId'):null,title:null,documentDate:form.get('documentDate')||null,assignmentName:form.get('assignmentName'),assignmentText:form.get('assignmentText'),dutiesText:form.get('dutiesText'),amount:form.get('amount')||null,validFrom:form.get('validFrom'),validTo:form.get('validTo'),separateAgreement:form.get('separate')==='true',saveAsTemplate:form.get('saveTemplate')==='on',itemsJson:null}));
            loadMemos();
        },
        () => {
            const teacherSelect = $('#memo-teacher'), contractSelect = $('#memo-contract'), catalogSelect = $('#memo-catalog');
            async function updateContracts() {
                const teacherId = teacherSelect.value; contractSelect.innerHTML = '<option value="">Выберите действующий договор</option>';
                if (!teacherId) return;
                const contracts = await api(`/api/hr-documents/contracts?teacherId=${teacherId}`);
                const activeContracts = contracts.filter(c=>c.active);
                contractSelect.innerHTML += activeContracts.map(c=>option(c.id,`№ ${c.contractNumber} от ${c.contractDate} — ${c.positionName}`,c.primaryContract)).join('');
                if (!activeContracts.length) contractSelect.innerHTML = '<option value="">Нет действующего трудового договора</option>';
            }
            function setSeparate(value) {
                const radio = document.querySelector(`input[name="separate"][value="${value}"]`); if (radio) radio.checked = true;
                const show = value === 'true'; $('#duties-label').classList.toggle('hidden',!show); $('#duties-control').classList.toggle('hidden',!show); $('#memo-duties').required = show;
            }
            function applyCatalog() {
                const item = catalogCache.find(c=>String(c.id)===catalogSelect.value);
                if (!item) return;
                $('#memo-assignment').value=item.name||''; $('#memo-text').value=item.memoText||item.agreementText||item.name||'';
                $('#memo-duties').value=item.dutiesText||''; $('#memo-amount').value=item.defaultAmount??''; setSeparate(String(Boolean(item.separateAgreement)));
                $('#memo-save-template').checked=false;
            }
            teacherSelect.addEventListener('change',updateContracts); catalogSelect.addEventListener('change',applyCatalog);
            $('#memo-manual').addEventListener('click',()=>{catalogSelect.value='';$('#memo-assignment').value='';$('#memo-text').value='';$('#memo-duties').value='';$('#memo-amount').value='';$('#memo-save-template').checked=true;$('#memo-assignment').focus();});
            document.querySelectorAll('input[name="separate"]').forEach(r=>r.addEventListener('change',()=>setSeparate(r.value)));
        }
    );
});

$('#memo-body').addEventListener('click', async event => {
    if (event.target.dataset.issueMemo) { await api(`/api/hr-documents/memos/${event.target.dataset.issueMemo}/status`,json('POST',{status:'ISSUED'})); loadMemos(); }
    if (event.target.dataset.receiveMemo) { await api(`/api/hr-documents/memos/${event.target.dataset.receiveMemo}/status`,json('POST',{status:'RECEIVED_BY_HR'})); loadMemos(); }
    if (event.target.dataset.annulMemo) { const reason=prompt('Причина аннулирования'); if(reason){await api(`/api/hr-documents/memos/${event.target.dataset.annulMemo}/annul`,json('POST',{reason}));loadMemos();} }
    if (event.target.dataset.receiveLoadMemo) { await api(`/api/hr-documents/load-memos/${event.target.dataset.receiveLoadMemo}/receive`,{method:'POST'}); await Promise.all([loadMemos(),loadJournal()]); }
    if (event.target.dataset.annulLoadMemo) { const reason=prompt('Причина аннулирования'); if(reason){await api(`/api/hr-documents/load-memos/${event.target.dataset.annulLoadMemo}/annul`,json('POST',{reason}));await Promise.all([loadMemos(),loadJournal()]);} }
});

async function loadCatalog() {
    catalogCache = await api('/api/hr-documents/catalog');
    $('#catalog-body').innerHTML = catalogCache.map(item=>`<tr><td>${esc(item.name)}</td><td>${esc(CATEGORY_LABELS[item.category]||item.category)}</td><td>${esc(item.contractClause)}</td><td>${esc(item.defaultAmount)}</td><td>${item.separateAgreement?'Да':'Нет'}</td><td><button data-edit-catalog="${item.id}">Изменить</button></td></tr>`).join('');
}
function editCatalog(item = null) {
    openEditor(item ? 'Изменить выплату или работу' : 'Добавить выплату или работу',
        row('Название', `<input name="name" value="${esc(item?.name)}" required>`) +
        row('Категория', `<select name="category"><option value="COMPENSATION" ${item?.category==='COMPENSATION'?'selected':''}>Компенсационная выплата</option><option value="INCENTIVE" ${item?.category==='INCENTIVE'?'selected':''}>Стимулирующая выплата</option><option value="ADDITIONAL_WORK" ${item?.category==='ADDITIONAL_WORK'?'selected':''}>Дополнительная работа</option></select>`) +
        row('Пункт трудового договора', `<input name="clause" value="${esc(item?.contractClause)}" placeholder="Например: 2.4">`) + row('Стандартная сумма', `<input name="amount" type="number" min="0" step="0.01" value="${esc(item?.defaultAmount)}">`) +
        row('Текст служебной записки', `<textarea name="memo">${esc(item?.memoText)}</textarea>`) + row('Формулировка соглашения', `<textarea name="agreement">${esc(item?.agreementText)}</textarea>`) +
        row('Дополнительные обязанности', `<textarea name="duties">${esc(item?.dutiesText)}</textarea>`) + row('Отдельное соглашение', `<label><input name="separate" type="checkbox" ${item?.separateAgreement?'checked':''}> Формировать отдельный допник с обязанностями</label>`),
        async form => {
            await api(item ? `/api/hr-documents/catalog/${item.id}` : '/api/hr-documents/catalog',json(item ? 'PUT' : 'POST',{name:form.get('name'),category:form.get('category'),contractClause:form.get('clause'),defaultAmount:form.get('amount')||null,memoText:form.get('memo'),agreementText:form.get('agreement'),dutiesText:form.get('duties'),separateAgreement:form.get('separate')==='on',active:true}));
            await loadCatalog();
        }
    );
}
$('#add-catalog').addEventListener('click', () => editCatalog());
$('#catalog-body').addEventListener('click', event => { const item=catalogCache.find(x=>String(x.id)===event.target.dataset.editCatalog); if(item)editCatalog(item); });

$('#personal-import-button').addEventListener('click', () => $('#personal-import').click());
$('#personal-import').addEventListener('change', async event => {
    if (!event.target.files[0]) return;
    const form = new FormData(); form.append('file',event.target.files[0]);
    const result = await api('/api/hr-documents/personal-data/import',{method:'POST',body:form});
    alert(`Обновлено: ${result.updated}, пропущено: ${result.skipped}`); event.target.value=''; loadJournal();
});

const notificationDate = $('#notification-date'), loadDate = $('#load-date'), notificationBody = $('#tb');
function defaultLoadDate(){const year=String(academicYear()).split('/')[0];return /^\d{4}$/.test(year)?`${year}-09-01`:new Date().toISOString().slice(0,10);}
notificationDate.value=new Date().toISOString().slice(0,10); loadDate.value=defaultLoadDate();
async function loadNotifications(){const items=await api(`/api/teachers-notification?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}`);notificationBody.innerHTML=items.map(item=>`<tr><td>${esc(item.fio)}</td><td>${item.generated?(item.changed?'Изменено':'Выпущено'):'Не выпущено'}</td><td><button data-notice="${esc(item.fio)}">Скачать</button></td></tr>`).join('');}
notificationBody.addEventListener('click',async event=>{if(!event.target.dataset.notice)return;const fio=event.target.dataset.notice;const response=await fetch(`/api/teachers-notification/download/${encodeURIComponent(fio)}?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}&notificationDate=${notificationDate.value}`,{method:'POST'});const link=document.createElement('a');link.href=URL.createObjectURL(await response.blob());link.download=`Уведомление_${fio}.docx`;link.click();loadNotifications();});
$('#all').addEventListener('click',async()=>{const response=await fetch(`/api/teachers-notification/download-all?academicYear=${encodeURIComponent(academicYear())}&loadDate=${loadDate.value}&notificationDate=${notificationDate.value}`,{method:'POST'});const link=document.createElement('a');link.href=URL.createObjectURL(await response.blob());link.download=`Предварительная нагрузка_${loadDate.value}.zip`;link.click();loadNotifications();});

loadJournal(); loadNotifications();
const permissionPoll=setInterval(()=>{if(!window.tarificationAuth)return;clearInterval(permissionPoll);if(!canViewPersonal()){document.querySelector('[data-tab="personal"]')?.remove();$('#personal')?.remove();}renderJournal();},50);
