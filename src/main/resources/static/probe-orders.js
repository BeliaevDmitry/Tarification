const probeState = {
    orders: [],
    references: { teachers: [], signers: [], students: [], defaultSignerTeacherId: null },
    settings: { approvalMode: 'ORGANIZATIONAL_BUILDING', approvalModeLabel: '',
        deputyDirectorTeacherId: null, deputyDirectorName: 'Власова Юлия Сергеевна', canEdit: false },
    selectedId: null,
    editParticipants: [],
    sortKey: 'eventDate',
    sortDirection: 'asc'
};

const probeUi = {
    importCard: document.getElementById('probe-import-card'),
    importFile: document.getElementById('probe-import-file'),
    importBtn: document.getElementById('probe-import-btn'),
    refreshBtn: document.getElementById('probe-refresh-btn'),
    importResult: document.getElementById('probe-import-result'),
    search: document.getElementById('probe-search'),
    statusFilter: document.getElementById('probe-status-filter'),
    settingsBtn: document.getElementById('probe-settings-btn'),
    settingsDialog: document.getElementById('probe-settings-dialog'),
    settingsForm: document.getElementById('probe-settings-form'),
    approvalMode: document.getElementById('probe-approval-mode'),
    deputyDirector: document.getElementById('probe-deputy-director'),
    settingsFeedback: document.getElementById('probe-settings-feedback'),
    summary: document.getElementById('probe-table-summary'),
    body: document.getElementById('probe-orders-body'),
    companionsDialog: document.getElementById('probe-companions-dialog'),
    companionsCaption: document.getElementById('probe-companions-caption'),
    companionsForm: document.getElementById('probe-companions-form'),
    requiredCompanions: document.getElementById('probe-required-companions'),
    additionalCompanions: document.getElementById('probe-additional-companions'),
    addCompanion: document.getElementById('probe-add-companion'),
    companionsRule: document.getElementById('probe-companions-rule'),
    editDialog: document.getElementById('probe-edit-dialog'),
    editCaption: document.getElementById('probe-edit-caption'),
    editForm: document.getElementById('probe-edit-form'),
    editName: document.getElementById('probe-edit-name'),
    editDate: document.getElementById('probe-edit-date'),
    editStart: document.getElementById('probe-edit-start'),
    editEnd: document.getElementById('probe-edit-end'),
    editVenue: document.getElementById('probe-edit-venue'),
    editAddress: document.getElementById('probe-edit-address'),
    editGatheringTime: document.getElementById('probe-edit-gathering-time'),
    editGatheringPlace: document.getElementById('probe-edit-gathering-place'),
    editReturn: document.getElementById('probe-edit-return'),
    editParticipants: document.getElementById('probe-edit-participants'),
    addStudent: document.getElementById('probe-add-student'),
    addStudentBtn: document.getElementById('probe-add-student-btn'),
    refreshContactsBtn: document.getElementById('probe-refresh-contacts-btn'),
    editFeedback: document.getElementById('probe-edit-feedback'),
    generateDialog: document.getElementById('probe-generate-dialog'),
    generateCaption: document.getElementById('probe-generate-caption'),
    generateForm: document.getElementById('probe-generate-form'),
    orderNumber: document.getElementById('probe-order-number'),
    orderDate: document.getElementById('probe-order-date'),
    orderSigner: document.getElementById('probe-order-signer'),
    orderSignerPosition: document.getElementById('probe-order-signer-position'),
    generateFeedback: document.getElementById('probe-generate-feedback'),
    scanDialog: document.getElementById('probe-scan-dialog'),
    scanCaption: document.getElementById('probe-scan-caption'),
    scanForm: document.getElementById('probe-scan-form'),
    scanFile: document.getElementById('probe-scan-file'),
    scanFeedback: document.getElementById('probe-scan-feedback')
};

function probeYear() {
    return typeof getStoredAcademicYear === 'function' ? getStoredAcademicYear() : '';
}

function probeUrl(path) {
    const year = probeYear();
    if (!year || path.includes('academicYear=')) return path;
    return `${path}${path.includes('?') ? '&' : '?'}academicYear=${encodeURIComponent(year)}`;
}

async function probeApi(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) throw new Error(body?.message || body?.error || text || `HTTP ${response.status}`);
    return body;
}

function probeEsc(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[char]);
}

function probeDate(value) {
    if (!value) return '—';
    const [year, month, day] = String(value).split('-');
    return year && month && day ? `${day}.${month}.${year}` : value;
}

function probeTime(value) {
    return value ? String(value).slice(0, 5) : '—';
}

function probeStatus(value) {
    return ({
        DRAFT: 'Черновик', BUILDING_APPROVED: 'Согласован', GENERATED: 'Word сформирован',
        RELEASED: 'Выпущен', CANCELLED: 'Отменён'
    })[value] || value || '—';
}

function selectedOrder() {
    return probeState.orders.find(item => Number(item.id) === Number(probeState.selectedId)) || null;
}

function staffOptions(selectedId, allowEmpty = false) {
    const rows = probeState.references.teachers || [];
    return `${allowEmpty ? '<option value="">Не назначен</option>' : '<option value="">Выберите сотрудника</option>'}`
        + rows.map(item => `<option value="${probeEsc(item.id)}" ${String(item.id) === String(selectedId || '') ? 'selected' : ''}>
            ${probeEsc(item.fullName)}${item.position ? ` — ${probeEsc(item.position)}` : ''}${item.buildingCode ? ` (${probeEsc(item.buildingCode)})` : ''}
        </option>`).join('');
}

function deputyDirectorOptions(selectedId, defaultName) {
    const rows = probeState.references.teachers || [];
    const fallback = `<option value="" ${selectedId ? '' : 'selected'}>${probeEsc(defaultName || 'Власова Юлия Сергеевна')} — по умолчанию</option>`;
    return fallback + rows.map(item => `<option value="${probeEsc(item.id)}" ${String(item.id) === String(selectedId || '') ? 'selected' : ''}>
        ${probeEsc(item.fullName)}${item.position ? ` — ${probeEsc(item.position)}` : ''}${item.buildingCode ? ` (${probeEsc(item.buildingCode)})` : ''}
    </option>`).join('');
}

function signerOptions(selectedId) {
    return '<option value="">Выберите подписанта</option>' + (probeState.references.signers || []).map(item =>
        `<option value="${probeEsc(item.id)}" ${String(item.id) === String(selectedId || '') ? 'selected' : ''}>${probeEsc(item.fullName)}${item.position ? ` — ${probeEsc(item.position)}` : ''}</option>`
    ).join('');
}

function companionsText(order) {
    return [order.primaryCompanion, order.secondaryCompanion, ...(order.additionalCompanions || [])]
        .map(item => item?.fullName).filter(Boolean).join(', ');
}

function sortValue(order, key) {
    if (key === 'eventDate') return `${order.eventDate || ''} ${order.startTime || ''}`;
    if (key === 'eventName') return order.eventName || '';
    if (key === 'buildingCode') return `${order.buildingCode || ''} ${(order.classNames || []).join(' ')}`;
    if (key === 'participantCount') return Number(order.participantCount || 0);
    if (key === 'companions') return companionsText(order);
    if (key === 'approval') return order.approvalComplete ? 1 : 0;
    if (key === 'status') return probeStatus(order.status);
    return '';
}

function compareOrders(left, right) {
    if (probeState.sortKey === 'eventDate' && probeState.sortDirection === 'asc') {
        const today = new Date().toISOString().slice(0, 10);
        const leftPast = String(left.eventDate || '') < today ? 1 : 0;
        const rightPast = String(right.eventDate || '') < today ? 1 : 0;
        if (leftPast !== rightPast) return leftPast - rightPast;
        if (leftPast) return String(right.eventDate || '').localeCompare(String(left.eventDate || ''));
    }
    const a = sortValue(left, probeState.sortKey);
    const b = sortValue(right, probeState.sortKey);
    const result = typeof a === 'number' && typeof b === 'number'
        ? a - b : String(a).localeCompare(String(b), 'ru', { numeric: true, sensitivity: 'base' });
    return probeState.sortDirection === 'asc' ? result : -result;
}

function visibleOrders() {
    const query = probeUi.search.value.trim().toLowerCase();
    const status = probeUi.statusFilter.value;
    return probeState.orders.filter(order => {
        if (status && order.status !== status) return false;
        if (!query) return true;
        const haystack = [order.eventName, order.venue, order.eventAddress, order.buildingCode,
            ...(order.classNames || []), companionsText(order), order.orderNumber].join(' ').toLowerCase();
        return haystack.includes(query);
    }).sort(compareOrders);
}

function renderProbeOrders() {
    const rows = visibleOrders();
    const released = probeState.orders.filter(item => item.status === 'RELEASED').length;
    const urgent = probeState.orders.filter(item => item.highlight === 'URGENT').length;
    probeUi.summary.textContent = `Всего: ${probeState.orders.length}. Выпущено: ${released}. Требуют внимания на ближайшей неделе: ${urgent}.`;
    if (!rows.length) {
        probeUi.body.innerHTML = '<tr><td colspan="8" class="muted">По выбранным условиям приказов нет.</td></tr>';
        return;
    }
    probeUi.body.innerHTML = rows.map(order => {
        const companionClass = order.companionsComplete ? 'probe-ok' : 'probe-error';
        const approvalRows = (order.approvals || []).map(item => item.approvedAt
            ? `<div class="probe-approval-item probe-ok">${probeEsc(item.scopeLabel)}<br><span class="muted">Согласовал: ${probeEsc(item.approvedBy || '')}</span></div>`
            : order.approvalComplete
                ? `<div class="probe-approval-item muted">${probeEsc(item.scopeLabel)}<br><span>Дополнительное согласование не требуется</span></div>`
                : `<div class="probe-approval-item probe-error">${probeEsc(item.scopeLabel)}<br><span>Не согласовано</span></div>`).join('');
        const approval = approvalRows || '<span class="probe-error">Не определён ответственный руководитель</span>';
        const warnings = (order.dataWarnings || []).length
            ? `<div class="probe-row-warnings">${order.dataWarnings.map(probeEsc).join('<br>')}</div>` : '';
        const actions = [];
        if (order.canEdit) {
            actions.push(`<button type="button" data-probe-action="companions" data-id="${order.id}">Сопровождающие</button>`);
            actions.push(`<button type="button" class="secondary" data-probe-action="edit" data-id="${order.id}">Редактировать</button>`);
        }
        if (order.canAcknowledge) {
            actions.push(`<button type="button" data-probe-action="ack" data-id="${order.id}" ${order.companionsComplete ? '' : 'disabled'}>Согласовать</button>`);
        }
        if (order.canGenerate) {
            actions.push(`<button type="button" data-probe-action="generate" data-id="${order.id}" ${order.approvalComplete && order.companionsComplete ? '' : 'disabled'}>Сформировать приказ</button>`);
        }
        if (order.generatedDocumentAvailable) {
            actions.push(`<a class="button-link secondary" href="/api/probe-orders/${order.id}/document">Скачать Word</a>`);
        }
        if (order.canRelease) {
            actions.push(`<button type="button" data-probe-action="release" data-id="${order.id}">Выпустить</button>`);
        }
        if (order.canUploadScan) {
            actions.push(`<button type="button" class="secondary" data-probe-action="scan" data-id="${order.id}">${order.signedScanAvailable ? 'Заменить скан' : 'Загрузить скан'}</button>`);
        }
        if (order.signedScanAvailable) {
            actions.push(`<a class="button-link secondary" href="/api/probe-orders/${order.id}/scan">Скачать скан</a>`);
        }
        if (!actions.length) actions.push('<span class="muted">Только информация</span>');
        return `<tr class="probe-highlight-${String(order.highlight || 'draft').toLowerCase()}">
            <td><strong>${probeEsc(probeDate(order.eventDate))}</strong><br>${probeEsc(probeTime(order.startTime))}–${probeEsc(probeTime(order.endTime))}</td>
            <td><strong>${probeEsc(order.eventName)}</strong><br><span class="muted">${probeEsc(order.venue || '')}<br>${probeEsc(order.eventAddress || '')}</span></td>
            <td><strong>${probeEsc(order.buildingCode || '—')}</strong><br>${probeEsc((order.classNames || []).join(', ') || '—')}</td>
            <td><strong>${probeEsc(order.participantCount)}</strong><br><span class="muted">Нужно сопровождающих: ${probeEsc(order.requiredCompanions)}</span></td>
            <td><span class="${companionClass}">${probeEsc(companionsText(order) || 'Не назначены')}</span></td>
            <td>${approval}</td>
            <td><strong>${probeEsc(probeStatus(order.status))}</strong><br>${order.orderNumber ? `№ ${probeEsc(order.orderNumber)} от ${probeEsc(probeDate(order.orderDate))}` : '<span class="muted">Без реквизитов</span>'}${warnings}</td>
            <td><div class="probe-row-actions">${actions.join('')}</div></td>
        </tr>`;
    }).join('');
    bindRowActions();
}

async function loadProbeData() {
    probeUi.body.innerHTML = '<tr><td colspan="8" class="muted">Загрузка…</td></tr>';
    const [orders, references, settings] = await Promise.all([
        probeApi(probeUrl('/api/probe-orders')),
        probeApi(probeUrl('/api/probe-orders/references')),
        probeApi('/api/probe-orders/settings')
    ]);
    probeState.orders = orders || [];
    probeState.references = references || probeState.references;
    probeState.settings = settings || probeState.settings;
    if (probeUi.settingsBtn) probeUi.settingsBtn.hidden = !probeState.settings.canEdit;
    renderProbeOrders();
}

async function importProbeRegistration() {
    const file = probeUi.importFile.files?.[0];
    if (!file) throw new Error('Выберите свежую выгрузку регистрации');
    const data = new FormData();
    data.append('file', file);
    probeUi.importResult.textContent = 'Анализируем мероприятия, детей, классы и корпуса…';
    const result = await probeApi(probeUrl('/api/probe-orders/import'), { method: 'POST', body: data });
    probeUi.importResult.innerHTML = `Мероприятий: ${probeEsc(result.eventsRead)}. Регистраций: ${probeEsc(result.applicationsRead)}.
        Создано приказов: ${probeEsc(result.ordersCreated)}, обновлено: ${probeEsc(result.ordersUpdated)}, выпущенных пропущено: ${probeEsc(result.releasedOrdersSkipped)}.
        Связано с карточками детей: ${probeEsc(result.participantsLinked)}, требуют проверки: ${probeEsc(result.unresolvedApplications)}.
        ${(result.warnings || []).length ? `<details><summary>Предупреждения (${result.warnings.length})</summary><ul>${result.warnings.map(item => `<li>${probeEsc(item)}</li>`).join('')}</ul></details>` : ''}`;
    await loadProbeData();
}

function openCompanions(order) {
    probeState.selectedId = order.id;
    probeUi.companionsCaption.textContent = `${probeDate(order.eventDate)} · ${order.eventName} · ${order.participantCount} детей`;
    const required = Math.max(1, Number(order.requiredCompanions || 1));
    const selectedRequired = [order.primaryCompanion?.id, required > 1 ? order.secondaryCompanion?.id : null];
    probeUi.requiredCompanions.innerHTML = Array.from({ length: required }, (_, index) => `
        <div class="probe-companion-row">
            <label>${index === 0 ? 'Основной сопровождающий' : `Обязательный сопровождающий ${index + 1}`}
                <select data-probe-required-companion required>${staffOptions(selectedRequired[index], false)}</select>
            </label>
        </div>`).join('');
    const additional = [...(order.additionalCompanions || [])];
    if (required === 1 && order.secondaryCompanion
        && !additional.some(item => String(item.id) === String(order.secondaryCompanion.id))) {
        additional.unshift(order.secondaryCompanion);
    }
    probeUi.additionalCompanions.innerHTML = '';
    additional.forEach(item => addAdditionalCompanion(item.id));
    probeUi.companionsRule.className = `probe-validation ${order.companionsComplete ? 'probe-ok' : 'probe-error'}`;
    probeUi.companionsRule.textContent = order.requiredCompanions > 1
        ? 'Для группы больше 10 детей обязательны два разных сопровождающих. Дополнительных можно добавить без ограничения.'
        : 'Для этой группы обязателен один сопровождающий. Дополнительных можно добавить без ограничения.';
    probeUi.companionsDialog.showModal();
}

function addAdditionalCompanion(selectedId = null) {
    const row = document.createElement('div');
    row.className = 'probe-companion-row';
    row.innerHTML = `<label>Дополнительный сопровождающий
        <select data-probe-additional-companion required>${staffOptions(selectedId, false)}</select>
        </label><button type="button" class="danger-btn" data-remove-companion>Убрать</button>`;
    probeUi.additionalCompanions.appendChild(row);
}

function syncEditParticipantsFromDom() {
    probeUi.editParticipants.querySelectorAll('tr[data-index]').forEach(row => {
        const item = probeState.editParticipants[Number(row.dataset.index)];
        if (!item) return;
        item.fullName = row.querySelector('[data-field="fullName"]')?.value.trim() || '';
        item.className = row.querySelector('[data-field="className"]')?.value.trim() || '';
        item.childPhone = row.querySelector('[data-field="childPhone"]')?.value.trim() || '';
        item.representativeName = row.querySelector('[data-field="representativeName"]')?.value.trim() || '';
        item.representativePhone = row.querySelector('[data-field="representativePhone"]')?.value.trim() || '';
    });
}

function renderEditParticipants() {
    probeUi.editParticipants.innerHTML = probeState.editParticipants.map((item, index) => `<tr data-index="${index}">
        <td><input data-field="fullName" value="${probeEsc(item.fullName || '')}" required></td>
        <td><input data-field="className" value="${probeEsc(item.className || '')}" required></td>
        <td><input data-field="childPhone" value="${probeEsc(item.childPhone || '')}" placeholder="Нет данных"></td>
        <td><textarea data-field="representativeName" rows="2" placeholder="Нет данных">${probeEsc(item.representativeName || '')}</textarea></td>
        <td><textarea data-field="representativePhone" rows="2" placeholder="Нет данных">${probeEsc(item.representativePhone || '')}</textarea></td>
        <td><button type="button" class="danger-btn" data-remove-participant="${index}">Убрать</button></td>
    </tr>`).join('') || '<tr><td colspan="6" class="probe-error">Добавьте хотя бы одного ребёнка.</td></tr>';
    probeUi.editParticipants.querySelectorAll('[data-remove-participant]').forEach(button => button.addEventListener('click', () => {
        syncEditParticipantsFromDom();
        probeState.editParticipants.splice(Number(button.dataset.removeParticipant), 1);
        renderEditParticipants();
    }));
}

async function refreshProbeContacts(orderId, quiet = false) {
    const result = await probeApi(`/api/probe-orders/${orderId}/refresh-contacts`, { method: 'POST' });
    if (result?.order) {
        probeState.orders = probeState.orders.map(item => Number(item.id) === Number(orderId) ? result.order : item);
        if (result.participantsUpdated) renderProbeOrders();
    }
    if (!quiet) {
        probeUi.editFeedback.textContent = `Проверено детей: ${result.participantsChecked}. Обновлено: ${result.participantsUpdated}.`
            + (result.participantsStillMissingContacts ? ` Без полных контактов: ${result.participantsStillMissingContacts}.` : ' Все контакты заполнены.');
    }
    return result;
}

async function openEdit(order) {
    probeState.selectedId = order.id;
    let current = order;
    let feedback = '';
    try {
        const refreshed = await refreshProbeContacts(order.id, true);
        current = refreshed.order || order;
        feedback = refreshed.participantsUpdated
            ? `Контакты автоматически обновлены: ${refreshed.participantsUpdated}.`
            : 'Контакты сверены с актуальным контингентом.';
    } catch (error) {
        feedback = `Не удалось обновить контакты: ${error.message}`;
    }
    probeState.editParticipants = (current.participants || []).map(item => ({ ...item }));
    probeUi.editCaption.textContent = `${current.buildingCode} · ${current.eventName}`;
    probeUi.editName.value = current.eventName || '';
    probeUi.editDate.value = current.eventDate || '';
    probeUi.editStart.value = probeTime(current.startTime) === '—' ? '' : probeTime(current.startTime);
    probeUi.editEnd.value = probeTime(current.endTime) === '—' ? '' : probeTime(current.endTime);
    probeUi.editVenue.value = current.venue || '';
    probeUi.editAddress.value = current.eventAddress || '';
    probeUi.editGatheringTime.value = probeTime(current.gatheringTime) === '—' ? '' : probeTime(current.gatheringTime);
    probeUi.editGatheringPlace.value = current.gatheringPlace || '';
    probeUi.editReturn.value = probeTime(current.returnTime) === '—' ? '' : probeTime(current.returnTime);
    probeUi.editFeedback.textContent = feedback;
    const existingIds = new Set(probeState.editParticipants.map(item => String(item.studentId || '')));
    probeUi.addStudent.innerHTML = '<option value="">Добавить ребёнка из контингента…</option>' + (probeState.references.students || [])
        .filter(item => !existingIds.has(String(item.id)))
        .map(item => `<option value="${probeEsc(item.id)}">${probeEsc(item.className)} — ${probeEsc(item.fullName)}</option>`).join('');
    renderEditParticipants();
    probeUi.editDialog.showModal();
}

function addStudentToEdit() {
    const student = (probeState.references.students || []).find(item => String(item.id) === probeUi.addStudent.value);
    if (!student) return;
    syncEditParticipantsFromDom();
    if (!probeState.editParticipants.some(item => String(item.studentId) === String(student.id))) {
        probeState.editParticipants.push({ studentId: student.id, fullName: student.fullName, className: student.className,
            childPhone: '', representativeName: '', representativePhone: '' });
    }
    renderEditParticipants();
    probeUi.addStudent.value = '';
}

function openGenerate(order) {
    probeState.selectedId = order.id;
    probeUi.generateCaption.textContent = `${probeDate(order.eventDate)} · ${order.eventName} · ${order.buildingCode}`;
    probeUi.orderNumber.value = order.orderNumber || '';
    probeUi.orderDate.value = order.orderDate || new Date().toISOString().slice(0, 10);
    const signerId = order.signer?.id || probeState.references.defaultSignerTeacherId;
    probeUi.orderSigner.innerHTML = signerOptions(signerId);
    const signer = (probeState.references.signers || []).find(item => String(item.id) === String(signerId || ''));
    probeUi.orderSignerPosition.value = order.signerPosition || signer?.position || '';
    probeUi.generateFeedback.textContent = '';
    probeUi.generateDialog.showModal();
}

function openScan(order) {
    probeState.selectedId = order.id;
    probeUi.scanCaption.textContent = `${probeDate(order.eventDate)} · ${order.eventName} · приказ № ${order.orderNumber || '—'}`;
    probeUi.scanFile.value = '';
    probeUi.scanFeedback.textContent = '';
    probeUi.scanDialog.showModal();
}

function bindRowActions() {
    document.querySelectorAll('[data-probe-action]').forEach(button => button.addEventListener('click', async () => {
        const order = probeState.orders.find(item => String(item.id) === button.dataset.id);
        if (!order) return;
        try {
            if (button.dataset.probeAction === 'companions') return openCompanions(order);
            if (button.dataset.probeAction === 'edit') {
                await openEdit(order);
                return;
            }
            if (button.dataset.probeAction === 'generate') return openGenerate(order);
            if (button.dataset.probeAction === 'scan') return openScan(order);
            if (button.dataset.probeAction === 'ack') {
                if (!window.confirm('Подтвердить согласование состава и условий приказа?')) return;
                await probeApi(`/api/probe-orders/${order.id}/acknowledge`, { method: 'POST' });
            }
            if (button.dataset.probeAction === 'release') {
                if (!window.confirm('Выпустить приказ? После выпуска он становится действующим и блокируется от редактирования.')) return;
                await probeApi(`/api/probe-orders/${order.id}/release`, { method: 'POST' });
            }
            await loadProbeData();
        } catch (error) {
            window.alert(error.message);
        }
    }));
}

probeUi.companionsForm.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        const requiredIds = [...probeUi.requiredCompanions.querySelectorAll('[data-probe-required-companion]')]
            .map(select => Number(select.value) || null);
        const additionalTeacherIds = [...probeUi.additionalCompanions.querySelectorAll('[data-probe-additional-companion]')]
            .map(select => Number(select.value) || null);
        if (requiredIds.some(id => id === null) || additionalTeacherIds.some(id => id === null)) {
            throw new Error('Выберите сотрудника в каждом добавленном поле');
        }
        const allIds = [...requiredIds, ...additionalTeacherIds];
        if (new Set(allIds).size !== allIds.length) {
            throw new Error('Один сотрудник не может быть выбран сопровождающим дважды');
        }
        await probeApi(`/api/probe-orders/${probeState.selectedId}/companions`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ primaryTeacherId: requiredIds[0] || null,
                secondaryTeacherId: requiredIds[1] || null, additionalTeacherIds })
        });
        probeUi.companionsDialog.close();
        await loadProbeData();
    } catch (error) { probeUi.companionsRule.className = 'probe-validation probe-error'; probeUi.companionsRule.textContent = error.message; }
});

probeUi.editForm.addEventListener('submit', async event => {
    event.preventDefault();
    syncEditParticipantsFromDom();
    probeUi.editFeedback.textContent = 'Сохраняем изменения…';
    try {
        await probeApi(`/api/probe-orders/${probeState.selectedId}`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                eventName: probeUi.editName.value.trim(), eventDate: probeUi.editDate.value,
                startTime: probeUi.editStart.value, endTime: probeUi.editEnd.value,
                venue: probeUi.editVenue.value.trim(), eventAddress: probeUi.editAddress.value.trim(),
                gatheringTime: probeUi.editGatheringTime.value, gatheringPlace: probeUi.editGatheringPlace.value.trim(),
                returnTime: probeUi.editReturn.value, participants: probeState.editParticipants
            })
        });
        probeUi.editDialog.close();
        await loadProbeData();
    } catch (error) { probeUi.editFeedback.textContent = error.message; }
});

probeUi.generateForm.addEventListener('submit', async event => {
    event.preventDefault();
    probeUi.generateFeedback.textContent = 'Формируем Word-приказ…';
    try {
        const id = probeState.selectedId;
        await probeApi(`/api/probe-orders/${id}/generate`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ orderNumber: probeUi.orderNumber.value.trim(), orderDate: probeUi.orderDate.value,
                signerTeacherId: Number(probeUi.orderSigner.value) || null,
                signerPosition: probeUi.orderSignerPosition.value.trim() })
        });
        probeUi.generateDialog.close();
        await loadProbeData();
        window.location.href = `/api/probe-orders/${id}/document`;
    } catch (error) { probeUi.generateFeedback.textContent = error.message; }
});

probeUi.scanForm.addEventListener('submit', async event => {
    event.preventDefault();
    const file = probeUi.scanFile.files?.[0];
    if (!file) return;
    probeUi.scanFeedback.textContent = 'Загружаем скан…';
    try {
        const data = new FormData(); data.append('file', file);
        await probeApi(`/api/probe-orders/${probeState.selectedId}/scan`, { method: 'POST', body: data });
        probeUi.scanDialog.close();
        await loadProbeData();
    } catch (error) { probeUi.scanFeedback.textContent = error.message; }
});

probeUi.settingsBtn?.addEventListener('click', () => {
    probeUi.approvalMode.value = probeState.settings.approvalMode || 'ORGANIZATIONAL_BUILDING';
    probeUi.deputyDirector.innerHTML = deputyDirectorOptions(
        probeState.settings.deputyDirectorTeacherId, probeState.settings.deputyDirectorName);
    probeUi.settingsFeedback.textContent = '';
    probeUi.settingsDialog.showModal();
});

probeUi.settingsForm?.addEventListener('submit', async event => {
    event.preventDefault();
    probeUi.settingsFeedback.textContent = 'Сохраняем настройку…';
    try {
        await probeApi('/api/probe-orders/settings', {
            method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                approvalMode: probeUi.approvalMode.value,
                deputyDirectorTeacherId: probeUi.deputyDirector.value
                    ? Number(probeUi.deputyDirector.value) : null
            })
        });
        probeUi.settingsDialog.close();
        await loadProbeData();
    } catch (error) {
        probeUi.settingsFeedback.textContent = error.message;
    }
});

document.querySelectorAll('[data-probe-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
document.querySelectorAll('[data-probe-sort]').forEach(header => header.addEventListener('click', () => {
    const key = header.dataset.probeSort;
    if (probeState.sortKey === key) probeState.sortDirection = probeState.sortDirection === 'asc' ? 'desc' : 'asc';
    else { probeState.sortKey = key; probeState.sortDirection = 'asc'; }
    document.querySelectorAll('[data-probe-sort]').forEach(item => item.classList.remove('sort-asc', 'sort-desc'));
    header.classList.add(probeState.sortDirection === 'asc' ? 'sort-asc' : 'sort-desc');
    renderProbeOrders();
}));
probeUi.search.addEventListener('input', renderProbeOrders);
probeUi.statusFilter.addEventListener('change', renderProbeOrders);
probeUi.importBtn.addEventListener('click', () => importProbeRegistration().catch(error => { probeUi.importResult.textContent = error.message; }));
probeUi.refreshBtn.addEventListener('click', () => loadProbeData().catch(error => { probeUi.importResult.textContent = error.message; }));
probeUi.addStudentBtn.addEventListener('click', addStudentToEdit);
probeUi.addCompanion.addEventListener('click', () => addAdditionalCompanion());
probeUi.additionalCompanions.addEventListener('click', event => {
    const button = event.target.closest('[data-remove-companion]');
    if (button) button.closest('.probe-companion-row')?.remove();
});
probeUi.refreshContactsBtn?.addEventListener('click', async () => {
    syncEditParticipantsFromDom();
    const localParticipants = probeState.editParticipants.map(item => ({ ...item }));
    probeUi.refreshContactsBtn.disabled = true;
    probeUi.editFeedback.textContent = 'Обновляем контакты из актуального контингента…';
    try {
        const result = await refreshProbeContacts(probeState.selectedId);
        const refreshed = (result.order?.participants || []).map(item => {
            const local = localParticipants.find(candidate =>
                (item.id && candidate.id && String(item.id) === String(candidate.id))
                || (item.studentId && candidate.studentId && String(item.studentId) === String(candidate.studentId))
                || String(item.fullName || '').trim().toLocaleLowerCase('ru-RU')
                    === String(candidate.fullName || '').trim().toLocaleLowerCase('ru-RU'));
            return {
                ...item,
                fullName: local?.fullName || item.fullName,
                className: local?.className || item.className,
                childPhone: item.childPhone || local?.childPhone || '',
                representativeName: item.representativeName || local?.representativeName || '',
                representativePhone: item.representativePhone || local?.representativePhone || ''
            };
        });
        const refreshedIds = new Set(refreshed.map(item => String(item.id || `student-${item.studentId || ''}`)));
        localParticipants.filter(item => !item.id && !refreshedIds.has(`student-${item.studentId || ''}`))
            .forEach(item => refreshed.push(item));
        probeState.editParticipants = refreshed;
        renderEditParticipants();
    } catch (error) {
        probeUi.editFeedback.textContent = error.message;
    } finally {
        probeUi.refreshContactsBtn.disabled = false;
    }
});
probeUi.orderSigner.addEventListener('change', () => {
    const signer = (probeState.references.signers || []).find(item => String(item.id) === probeUi.orderSigner.value);
    if (signer?.position) probeUi.orderSignerPosition.value = signer.position;
});

(async function initProbeOrders() {
    try {
        const currentUser = await probeApi('/api/auth/me');
        const leadership = currentUser.admin || currentUser.role === 'DIRECTOR' || currentUser.role === 'DEPUTY_DIRECTOR';
        if (!leadership && probeUi.importCard) probeUi.importCard.style.display = 'none';
        await loadProbeData();
        document.querySelector('[data-probe-sort="eventDate"]')?.classList.add('sort-asc');
    } catch (error) {
        probeUi.body.innerHTML = `<tr><td colspan="8" class="probe-error">${probeEsc(error.message)}</td></tr>`;
    }
})();
