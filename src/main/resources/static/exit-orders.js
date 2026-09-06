const exitState = {
    orders: [],
    references: { classes: [], teachers: [], signers: [], dictionaries: {}, suggestedClassIds: [] },
    selectedStudents: new Set(),
    editingId: null,
    actionId: null,
    autoGatheringPlace: ''
};

const exitUi = {
    newBtn: document.getElementById('exit-new-btn'), refreshBtn: document.getElementById('exit-refresh-btn'),
    constructorCard: document.getElementById('exit-constructor-card'), constructorTitle: document.getElementById('exit-constructor-title'),
    constructorClose: document.getElementById('exit-constructor-close'), form: document.getElementById('exit-order-form'),
    submitBtn: document.getElementById('exit-submit-btn'), feedback: document.getElementById('exit-form-feedback'),
    preamble: document.getElementById('exit-preamble'), eventName: document.getElementById('exit-event-name'),
    eventDate: document.getElementById('exit-event-date'), startTime: document.getElementById('exit-start-time'),
    endTime: document.getElementById('exit-end-time'), venue: document.getElementById('exit-venue'),
    eventAddress: document.getElementById('exit-event-address'), gatheringTime: document.getElementById('exit-gathering-time'),
    gatheringPlace: document.getElementById('exit-gathering-place'), returnTime: document.getElementById('exit-return-time'),
    classPicker: document.getElementById('exit-class-picker'), selectionSummary: document.getElementById('exit-selection-summary'),
    buildingSuggestion: document.getElementById('exit-building-suggestion'), primary: document.getElementById('exit-primary-companion'),
    secondary: document.getElementById('exit-secondary-companion'), additional: document.getElementById('exit-additional-companions'),
    companionRule: document.getElementById('exit-companion-rule'), listSummary: document.getElementById('exit-list-summary'),
    search: document.getElementById('exit-search'), status: document.getElementById('exit-status-filter'),
    body: document.getElementById('exit-orders-body'), generateDialog: document.getElementById('exit-generate-dialog'),
    generateForm: document.getElementById('exit-generate-form'), generateCaption: document.getElementById('exit-generate-caption'),
    orderNumber: document.getElementById('exit-order-number'), orderDate: document.getElementById('exit-order-date'),
    signer: document.getElementById('exit-order-signer'), signerPosition: document.getElementById('exit-order-signer-position'),
    generateFeedback: document.getElementById('exit-generate-feedback'), attendanceDialog: document.getElementById('exit-attendance-dialog'),
    attendanceForm: document.getElementById('exit-attendance-form'), attendanceList: document.getElementById('exit-attendance-list'),
    attendanceFeedback: document.getElementById('exit-attendance-feedback'), scanDialog: document.getElementById('exit-scan-dialog'),
    scanForm: document.getElementById('exit-scan-form'), scanFile: document.getElementById('exit-scan-file'),
    scanFeedback: document.getElementById('exit-scan-feedback')
};

function exitYear() { return typeof getStoredAcademicYear === 'function' ? getStoredAcademicYear() : ''; }
function exitUrl(path) {
    const year = exitYear();
    return !year || path.includes('academicYear=') ? path : `${path}${path.includes('?') ? '&' : '?'}academicYear=${encodeURIComponent(year)}`;
}
async function exitApi(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) throw new Error(body?.message || body?.error || text || `HTTP ${response.status}`);
    return body;
}
function exitEsc(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}
function exitDate(value) {
    if (!value) return '—';
    const [year, month, day] = String(value).split('-');
    return year && month && day ? `${day}.${month}.${year}` : value;
}
function exitTime(value) { return value ? String(value).slice(0, 5) : '—'; }
function exitStatus(value) {
    return ({DRAFT:'На согласовании', BUILDING_APPROVED:'Согласован руководителем корпуса',
        GENERATED:'Word сформирован', RELEASED:'Выпущен', CANCELLED:'Отменён'})[value] || value || '—';
}
function jsonOptions(body) { return { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body) }; }
function putOptions(body) { return { method: 'PATCH', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body) }; }

function dictionary(type) { return exitState.references.dictionaries?.[type] || []; }
function fillDatalist(id, values) {
    document.getElementById(id).innerHTML = values.map(value => `<option value="${exitEsc(value)}"></option>`).join('');
}
function staffOptions(selected = '', empty = 'Выберите сотрудника') {
    return `<option value="">${exitEsc(empty)}</option>` + (exitState.references.teachers || []).map(item =>
        `<option value="${item.id}" ${String(item.id) === String(selected || '') ? 'selected' : ''}>${exitEsc(item.fullName)}${item.position ? ` — ${exitEsc(item.position)}` : ''}${item.buildingCode ? ` (${exitEsc(item.buildingCode)})` : ''}</option>`).join('');
}
function signerOptions(selected = '') {
    return '<option value="">Выберите подписанта</option>' + (exitState.references.signers || []).map(item =>
        `<option value="${item.id}" ${String(item.id) === String(selected || '') ? 'selected' : ''}>${exitEsc(item.fullName)}${item.position ? ` — ${exitEsc(item.position)}` : ''}</option>`).join('');
}

function prepareReferences() {
    exitUi.preamble.innerHTML = dictionary('PREAMBLE').map((value, index) =>
        `<option value="${exitEsc(value)}" ${index === 0 ? 'selected' : ''}>${exitEsc(value)}</option>`).join('');
    fillDatalist('exit-event-name-options', dictionary('EVENT_NAME'));
    fillDatalist('exit-venue-options', dictionary('VENUE'));
    fillDatalist('exit-address-options', dictionary('EVENT_ADDRESS'));
    fillDatalist('exit-gathering-options', dictionary('GATHERING_PLACE'));
    exitUi.primary.innerHTML = staffOptions(exitState.references.defaultCompanionTeacherId);
    exitUi.secondary.innerHTML = staffOptions('', 'Не требуется');
    exitUi.additional.innerHTML = (exitState.references.teachers || []).map(item =>
        `<option value="${item.id}">${exitEsc(item.fullName)}${item.buildingCode ? ` (${exitEsc(item.buildingCode)})` : ''}</option>`).join('');
    exitUi.signer.innerHTML = signerOptions(exitState.references.defaultSignerTeacherId);
    const signer = (exitState.references.signers || []).find(item => String(item.id) === String(exitState.references.defaultSignerTeacherId));
    exitUi.signerPosition.value = signer?.position || 'Директор';
    renderClassPicker();
}

function groupedClasses() {
    const groups = new Map();
    (exitState.references.classes || []).forEach(item => {
        const key = item.parallel == null ? 'Без параллели' : `${item.parallel} классы`;
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(item);
    });
    return groups;
}

function renderClassPicker() {
    const groups = groupedClasses();
    if (!groups.size) {
        exitUi.classPicker.innerHTML = '<p class="muted">В выбранном учебном году классы не найдены.</p>';
        return;
    }
    exitUi.classPicker.innerHTML = [...groups.entries()].map(([parallel, classes]) => {
        const open = classes.some(item => item.suggested || item.students.some(student => exitState.selectedStudents.has(Number(student.id))));
        return `<details class="exit-parallel" ${open ? 'open' : ''}><summary>${exitEsc(parallel)} <span class="muted">(${classes.length})</span></summary>
          <div class="exit-parallel-classes">${classes.map(renderClassBlock).join('')}</div></details>`;
    }).join('');
    bindClassPicker();
    updateSelectionSummary();
}

function renderClassBlock(item) {
    const ids = item.students.map(student => Number(student.id));
    const all = ids.length > 0 && ids.every(id => exitState.selectedStudents.has(id));
    const some = ids.some(id => exitState.selectedStudents.has(id));
    return `<section class="exit-class-block ${item.suggested ? 'is-suggested' : ''}">
      <label class="exit-class-all"><input type="checkbox" data-exit-class="${item.id}" ${all ? 'checked' : ''} data-partial="${some && !all}">
        <strong>${exitEsc(item.className)}</strong><span>${exitEsc(item.buildingCode || '')}${item.suggested ? ' · ваш класс' : ''}</span></label>
      <div class="exit-student-list">${item.students.length ? item.students.map(student =>
        `<label><input type="checkbox" data-exit-student="${student.id}" data-class-id="${item.id}" ${exitState.selectedStudents.has(Number(student.id)) ? 'checked' : ''}>
          <span>${exitEsc(student.fullName)}</span></label>`).join('') : '<span class="muted">В классе нет действующего контингента</span>'}</div>
    </section>`;
}

function bindClassPicker() {
    exitUi.classPicker.querySelectorAll('[data-exit-class]').forEach(input => {
        input.indeterminate = input.dataset.partial === 'true';
        input.addEventListener('change', () => {
            const cls = exitState.references.classes.find(item => String(item.id) === input.dataset.exitClass);
            (cls?.students || []).forEach(student => input.checked
                ? exitState.selectedStudents.add(Number(student.id)) : exitState.selectedStudents.delete(Number(student.id)));
            renderClassPicker();
        });
    });
    exitUi.classPicker.querySelectorAll('[data-exit-student]').forEach(input => input.addEventListener('change', () => {
        const id = Number(input.dataset.exitStudent);
        input.checked ? exitState.selectedStudents.add(id) : exitState.selectedStudents.delete(id);
        renderClassPicker();
    }));
}

function selectedClassStats() {
    return (exitState.references.classes || []).map(cls => ({ cls,
        count: cls.students.filter(student => exitState.selectedStudents.has(Number(student.id))).length }))
        .filter(item => item.count > 0);
}

function updateSelectionSummary() {
    const stats = selectedClassStats();
    const count = exitState.selectedStudents.size;
    exitUi.selectionSummary.textContent = count ? `Выбрано: ${count} · классов: ${stats.length}` : 'Не выбрано';
    const buildings = new Map();
    stats.forEach(({cls, count: classCount}) => {
        const current = buildings.get(String(cls.schoolBuildingId)) || { count: 0, cls };
        current.count += classCount;
        buildings.set(String(cls.schoolBuildingId), current);
    });
    const majority = [...buildings.values()].sort((a, b) => b.count - a.count || Number(a.cls.schoolBuildingId) - Number(b.cls.schoolBuildingId))[0];
    if (majority) {
        exitUi.buildingSuggestion.textContent = `Корпус сбора будет предложен автоматически: ${majority.cls.buildingCode} — ${majority.cls.buildingAddress} (${majority.count} детей).`;
        if (!exitUi.gatheringPlace.value || exitUi.gatheringPlace.value === exitState.autoGatheringPlace) {
            exitState.autoGatheringPlace = majority.cls.buildingAddress || '';
            exitUi.gatheringPlace.value = exitState.autoGatheringPlace;
        }
    } else {
        exitUi.buildingSuggestion.textContent = 'После выбора детей система определит корпус, откуда собирается большинство.';
    }
    const needsSecond = count > 10;
    exitUi.secondary.required = needsSecond;
    exitUi.companionRule.textContent = needsSecond
        ? `Выбрано ${count} детей — обязательно назначьте двух сопровождающих.`
        : `Выбрано ${count} детей — достаточно одного сопровождающего.`;
}

function resetForm() {
    exitState.editingId = null;
    exitState.selectedStudents = new Set();
    exitState.autoGatheringPlace = '';
    exitUi.form.reset();
    exitUi.constructorTitle.textContent = 'Новая заявка';
    exitUi.submitBtn.textContent = 'Отправить заявку';
    exitUi.feedback.textContent = '';
    prepareReferences();
    (exitState.references.suggestedClassIds || []).forEach(classId => {
        const cls = exitState.references.classes.find(item => String(item.id) === String(classId));
        (cls?.students || []).forEach(student => exitState.selectedStudents.add(Number(student.id)));
    });
    renderClassPicker();
    exitUi.eventDate.value = new Date().toISOString().slice(0, 10);
    exitUi.gatheringPlace.value = exitState.references.suggestedGatheringPlace || exitUi.gatheringPlace.value;
    exitState.autoGatheringPlace = exitUi.gatheringPlace.value;
}

function openNew() {
    resetForm();
    exitUi.constructorCard.hidden = false;
    exitUi.constructorCard.scrollIntoView({behavior:'smooth', block:'start'});
}

function selectedOrder(id) { return exitState.orders.find(item => Number(item.id) === Number(id)); }
function openEdit(order) {
    resetForm();
    exitState.editingId = order.id;
    exitUi.constructorTitle.textContent = `Редактирование заявки · ${order.eventName}`;
    exitUi.submitBtn.textContent = 'Сохранить и повторно отправить';
    exitUi.preamble.value = order.preamble || '';
    exitUi.eventName.value = order.eventName || '';
    exitUi.eventDate.value = order.eventDate || '';
    exitUi.startTime.value = String(order.startTime || '').slice(0,5);
    exitUi.endTime.value = String(order.endTime || '').slice(0,5);
    exitUi.venue.value = order.venue || '';
    exitUi.eventAddress.value = order.eventAddress || '';
    exitUi.gatheringTime.value = String(order.gatheringTime || '').slice(0,5);
    exitUi.gatheringPlace.value = order.gatheringPlace || '';
    exitState.autoGatheringPlace = order.gatheringPlace || '';
    exitUi.returnTime.value = String(order.returnTime || '').slice(0,5);
    exitState.selectedStudents = new Set((order.participants || []).map(item => Number(item.studentId)));
    exitUi.primary.value = String(order.primaryCompanion?.id || '');
    exitUi.secondary.value = String(order.secondaryCompanion?.id || '');
    const additional = new Set((order.additionalCompanions || []).map(item => String(item.id)));
    [...exitUi.additional.options].forEach(option => option.selected = additional.has(option.value));
    renderClassPicker();
    exitUi.constructorCard.hidden = false;
    exitUi.constructorCard.scrollIntoView({behavior:'smooth', block:'start'});
}

function formPayload() {
    return {
        preamble: exitUi.preamble.value, eventName: exitUi.eventName.value.trim(), eventDate: exitUi.eventDate.value,
        startTime: exitUi.startTime.value, endTime: exitUi.endTime.value, venue: exitUi.venue.value.trim(),
        eventAddress: exitUi.eventAddress.value.trim(), gatheringTime: exitUi.gatheringTime.value,
        gatheringPlace: exitUi.gatheringPlace.value.trim(), returnTime: exitUi.returnTime.value,
        studentIds: [...exitState.selectedStudents], primaryCompanionTeacherId: Number(exitUi.primary.value) || null,
        secondaryCompanionTeacherId: Number(exitUi.secondary.value) || null,
        additionalCompanionTeacherIds: [...exitUi.additional.selectedOptions].map(option => Number(option.value))
    };
}

function companionsText(order) {
    return [order.primaryCompanion, order.secondaryCompanion, ...(order.additionalCompanions || [])]
        .map(item => item?.fullName).filter(Boolean).join(', ');
}

function visibleOrders() {
    const query = exitUi.search.value.trim().toLowerCase();
    return exitState.orders.filter(order => (!exitUi.status.value || order.status === exitUi.status.value)
        && (!query || [order.eventName, order.venue, order.eventAddress, order.requestedBy, companionsText(order),
            ...(order.classNames || [])].join(' ').toLowerCase().includes(query)));
}

function renderOrders() {
    const rows = visibleOrders();
    const approved = exitState.orders.filter(item => item.approvalComplete).length;
    const released = exitState.orders.filter(item => item.status === 'RELEASED').length;
    exitUi.listSummary.textContent = `Всего: ${exitState.orders.length}. Согласовано: ${approved}. Выпущено: ${released}.`;
    if (!rows.length) {
        exitUi.body.innerHTML = '<tr><td colspan="7" class="muted">Приказов по выбранным условиям нет.</td></tr>';
        return;
    }
    exitUi.body.innerHTML = rows.map(order => {
        const approvals = (order.approvals || []).map(item => item.approvedAt
            ? `<div class="probe-ok">${exitEsc(item.scopeLabel)}<br><span class="muted">${exitEsc(item.approvedBy || '')}</span></div>`
            : order.approvalComplete ? `<div class="muted">${exitEsc(item.scopeLabel)} · дополнительно не требуется</div>`
                : `<div class="probe-error">${exitEsc(item.scopeLabel)} · ожидается</div>`).join('') || '<span class="probe-error">Корпус не определён</span>';
        const actions = [];
        if (order.canEdit) actions.push(`<button type="button" class="secondary" data-exit-action="edit" data-id="${order.id}">Редактировать</button>`);
        if (order.canAcknowledge) actions.push(`<button type="button" data-exit-action="ack" data-id="${order.id}">Согласовать</button>`);
        if (order.canGenerate) actions.push(`<button type="button" data-exit-action="generate" data-id="${order.id}">Сформировать приказ</button>`);
        if (order.generatedDocumentAvailable) actions.push(`<a class="button-link secondary" href="/api/exit-orders/${order.id}/document">Скачать Word</a>`);
        if (order.canRelease) actions.push(`<button type="button" data-exit-action="release" data-id="${order.id}">Выпустить</button>`);
        if (order.canUploadScan) actions.push(`<button type="button" class="secondary" data-exit-action="scan" data-id="${order.id}">${order.signedScanAvailable ? 'Заменить скан' : 'Загрузить скан'}</button>`);
        if (order.signedScanAvailable) actions.push(`<a class="button-link secondary" href="/api/exit-orders/${order.id}/scan">Скачать скан</a>`);
        if (order.canMarkAttendance) actions.push(`<button type="button" class="secondary" data-exit-action="attendance" data-id="${order.id}">${order.attendanceMarkedAt ? 'Изменить посещаемость' : 'Отметить неявившихся'}</button>`);
        if (!actions.length) actions.push('<span class="muted">Только информация</span>');
        return `<tr><td><strong>${exitDate(order.eventDate)}</strong><br>${exitTime(order.startTime)}–${exitTime(order.endTime)}</td>
          <td><strong>${exitEsc(order.eventName)}</strong><br><span class="muted">${exitEsc(order.venue)}<br>${exitEsc(order.eventAddress)}</span></td>
          <td><strong>${exitEsc((order.classNames || []).join(', '))}</strong><br>${order.participantCount} детей${order.absentCount ? `<br><span class="probe-error">Не явились: ${order.absentCount}</span>` : ''}<br><span class="muted">Сбор: ${exitEsc(order.buildingCode)}</span></td>
          <td>${exitEsc(companionsText(order) || 'Не назначены')}</td><td>${approvals}</td>
          <td><strong>${exitEsc(exitStatus(order.status))}</strong><br><span class="muted">Заявитель: ${exitEsc(order.requestedBy)}</span>${order.orderNumber ? `<br>№ ${exitEsc(order.orderNumber)} от ${exitDate(order.orderDate)}` : ''}</td>
          <td><div class="probe-row-actions">${actions.join('')}</div></td></tr>`;
    }).join('');
    bindRowActions();
}

async function loadExitData() {
    exitUi.body.innerHTML = '<tr><td colspan="7" class="muted">Загрузка…</td></tr>';
    const [orders, references] = await Promise.all([
        exitApi(exitUrl('/api/exit-orders')), exitApi(exitUrl('/api/exit-orders/references'))
    ]);
    exitState.orders = orders || [];
    exitState.references = references || exitState.references;
    prepareReferences();
    renderOrders();
}

function bindRowActions() {
    exitUi.body.querySelectorAll('[data-exit-action]').forEach(button => button.addEventListener('click', async () => {
        const order = selectedOrder(button.dataset.id);
        if (!order) return;
        try {
            if (button.dataset.exitAction === 'edit') return openEdit(order);
            if (button.dataset.exitAction === 'generate') return openGenerate(order);
            if (button.dataset.exitAction === 'attendance') return openAttendance(order);
            if (button.dataset.exitAction === 'scan') return openScan(order);
            if (button.dataset.exitAction === 'ack') {
                if (!window.confirm(`Согласовать заявку «${order.eventName}»?`)) return;
                await exitApi(`/api/exit-orders/${order.id}/acknowledge`, {method:'POST'});
            }
            if (button.dataset.exitAction === 'release') {
                if (!window.confirm(`Выпустить приказ «${order.eventName}»?`)) return;
                await exitApi(`/api/exit-orders/${order.id}/release`, {method:'POST'});
            }
            await loadExitData();
        } catch (error) { window.alert(error.message); }
    }));
}

function openGenerate(order) {
    exitState.actionId = order.id;
    exitUi.generateCaption.textContent = `${order.eventName} · ${exitDate(order.eventDate)}`;
    exitUi.orderNumber.value = order.orderNumber || '';
    exitUi.orderDate.value = order.orderDate || new Date().toISOString().slice(0,10);
    exitUi.signer.innerHTML = signerOptions(order.signer?.id || exitState.references.defaultSignerTeacherId);
    const signer = (exitState.references.signers || []).find(item => String(item.id) === exitUi.signer.value);
    exitUi.signerPosition.value = order.signerPosition || signer?.position || 'Директор';
    exitUi.generateFeedback.textContent = '';
    exitUi.generateDialog.showModal();
}

function openAttendance(order) {
    exitState.actionId = order.id;
    exitUi.attendanceList.innerHTML = (order.participants || []).map(item => `<label class="exit-attendance-item">
      <input type="checkbox" value="${item.id}" ${item.absent ? 'checked' : ''}><span><strong>${exitEsc(item.fullName)}</strong><br><span class="muted">${exitEsc(item.className)}</span></span></label>`).join('');
    exitUi.attendanceFeedback.textContent = '';
    exitUi.attendanceDialog.showModal();
}

function openScan(order) {
    exitState.actionId = order.id;
    exitUi.scanForm.reset();
    exitUi.scanFeedback.textContent = '';
    exitUi.scanDialog.showModal();
}

exitUi.form.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        if (!exitState.selectedStudents.size) throw new Error('Выберите хотя бы одного ребёнка');
        exitUi.feedback.textContent = exitState.editingId ? 'Сохраняем заявку…' : 'Отправляем заявку…';
        const path = exitState.editingId ? `/api/exit-orders/${exitState.editingId}` : exitUrl('/api/exit-orders');
        await exitApi(path, exitState.editingId ? putOptions(formPayload()) : jsonOptions(formPayload()));
        exitUi.constructorCard.hidden = true;
        await loadExitData();
    } catch (error) { exitUi.feedback.textContent = error.message; }
});

exitUi.generateForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        exitUi.generateFeedback.textContent = 'Формируем документ…';
        await exitApi(`/api/exit-orders/${exitState.actionId}/generate`, jsonOptions({
            orderNumber: exitUi.orderNumber.value.trim(), orderDate: exitUi.orderDate.value,
            signerTeacherId: Number(exitUi.signer.value) || null, signerPosition: exitUi.signerPosition.value.trim()
        }));
        exitUi.generateDialog.close();
        await loadExitData();
    } catch (error) { exitUi.generateFeedback.textContent = error.message; }
});

exitUi.attendanceForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        exitUi.attendanceFeedback.textContent = 'Сохраняем…';
        const absentParticipantIds = [...exitUi.attendanceList.querySelectorAll('input:checked')].map(input => Number(input.value));
        await exitApi(`/api/exit-orders/${exitState.actionId}/attendance`, putOptions({absentParticipantIds}));
        exitUi.attendanceDialog.close();
        await loadExitData();
    } catch (error) { exitUi.attendanceFeedback.textContent = error.message; }
});

exitUi.scanForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        const file = exitUi.scanFile.files?.[0];
        if (!file) throw new Error('Выберите файл');
        const data = new FormData(); data.append('file', file);
        exitUi.scanFeedback.textContent = 'Загружаем…';
        await exitApi(`/api/exit-orders/${exitState.actionId}/scan`, {method:'POST', body:data});
        exitUi.scanDialog.close();
        await loadExitData();
    } catch (error) { exitUi.scanFeedback.textContent = error.message; }
});

exitUi.signer.addEventListener('change', () => {
    const signer = (exitState.references.signers || []).find(item => String(item.id) === exitUi.signer.value);
    if (signer?.position) exitUi.signerPosition.value = signer.position;
});
exitUi.gatheringPlace.addEventListener('input', () => { if (exitUi.gatheringPlace.value !== exitState.autoGatheringPlace) exitState.autoGatheringPlace = ''; });
exitUi.newBtn.addEventListener('click', openNew);
exitUi.refreshBtn.addEventListener('click', () => loadExitData().catch(error => window.alert(error.message)));
exitUi.constructorClose.addEventListener('click', () => { exitUi.constructorCard.hidden = true; });
exitUi.search.addEventListener('input', renderOrders);
exitUi.status.addEventListener('change', renderOrders);
document.querySelectorAll('[data-exit-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));

loadExitData().catch(error => { exitUi.body.innerHTML = `<tr><td colspan="7" class="probe-error">${exitEsc(error.message)}</td></tr>`; });
