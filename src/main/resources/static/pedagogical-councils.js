const pedUi = {
    listBody: document.getElementById('protocol-list-body'),
    listFeedback: document.getElementById('protocol-list-feedback'),
    newButton: document.getElementById('new-protocol-btn'),
    uploadArchiveButton: document.getElementById('upload-archive-btn'),
    editor: document.getElementById('protocol-editor-dialog'),
    editorForm: document.getElementById('protocol-editor-form'),
    editorTitle: document.getElementById('protocol-editor-title'),
    editorFeedback: document.getElementById('protocol-editor-feedback'),
    editorClose: document.getElementById('protocol-editor-close'),
    editorCancel: document.getElementById('protocol-editor-cancel'),
    editorSave: document.getElementById('protocol-editor-save'),
    academicYear: document.getElementById('protocol-academic-year'),
    number: document.getElementById('protocol-number'),
    date: document.getElementById('protocol-date'),
    time: document.getElementById('protocol-time'),
    attendeeCount: document.getElementById('protocol-attendee-count'),
    status: document.getElementById('protocol-status'),
    chairPosition: document.getElementById('protocol-chair-position'),
    chairFio: document.getElementById('protocol-chair-fio'),
    secretaryPosition: document.getElementById('protocol-secretary-position'),
    secretaryFio: document.getElementById('protocol-secretary-fio'),
    addItem: document.getElementById('add-protocol-item'),
    items: document.getElementById('protocol-items'),
    itemTemplate: document.getElementById('protocol-item-template'),
    preview: document.getElementById('protocol-preview-paper'),
    archiveDialog: document.getElementById('archive-upload-dialog'),
    archiveForm: document.getElementById('archive-upload-form'),
    archiveYear: document.getElementById('archive-academic-year'),
    archiveDate: document.getElementById('archive-meeting-date'),
    archiveYearDateHint: document.getElementById('archive-year-date-hint'),
    archiveFeedback: document.getElementById('archive-upload-feedback'),
    archiveClose: document.getElementById('archive-upload-close'),
    archiveCancel: document.getElementById('archive-upload-cancel'),
    extractDialog: document.getElementById('extract-dialog'),
    extractForm: document.getElementById('extract-form'),
    extractItems: document.getElementById('extract-items'),
    extractCertifiers: document.getElementById('extract-certifiers'),
    extractAddCertifier: document.getElementById('extract-add-certifier'),
    extractExternal: document.getElementById('extract-external'),
    extractSourceSigners: document.getElementById('extract-source-signers'),
    extractApproval: document.getElementById('extract-approval'),
    extractApproverRow: document.getElementById('extract-approver-row'),
    extractApprover: document.getElementById('extract-approver'),
    extractApproverPosition: document.getElementById('extract-approver-position'),
    extractFeedback: document.getElementById('extract-feedback'),
    extractClose: document.getElementById('extract-close'),
    extractCancel: document.getElementById('extract-cancel')
};

const pedState = {
    protocols: [],
    staff: [],
    certifiers: [],
    academicYears: [],
    branding: null,
    user: null,
    editing: null,
    extractProtocol: null,
    permissions: { canView: false, canEdit: false, canImport: false, canExport: false }
};

const pedStatusLabels = {
    DRAFT: 'Черновик',
    REVIEW: 'На проверке',
    REGISTERED: 'Зарегистрирован',
    CORRECTED: 'Исправленная версия'
};

function pedEsc(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

async function pedApi(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text;
    }
    if (!response.ok) {
        throw new Error(body?.message || body?.error || body || `HTTP ${response.status}`);
    }
    return body;
}

function selectedAcademicYear() {
    return sessionStorage.getItem('tarification.academicYear') || '';
}

function academicYearCodes(selectedYear = '') {
    const codes = (pedState.academicYears || [])
        .map((year) => String(year?.code || '').trim())
        .filter((year) => /^\d{4}\/\d{4}$/.test(year));
    if (selectedYear && !codes.includes(selectedYear)) {
        codes.push(selectedYear);
    }
    return [...new Set(codes)].sort((a, b) => a.localeCompare(b, 'ru'));
}

function fillAcademicYearSelect(select, selectedYear) {
    const codes = academicYearCodes(selectedYear);
    select.innerHTML = codes
        .map((year) => `<option value="${pedEsc(year)}">${pedEsc(year)}</option>`)
        .join('');
    select.value = codes.includes(selectedYear) ? selectedYear : (codes.at(-1) || '');
}

function updateArchiveYearBounds() {
    const match = /^(\d{4})\/(\d{4})$/.exec(pedUi.archiveYear.value);
    if (!match || Number(match[2]) !== Number(match[1]) + 1) {
        pedUi.archiveDate.removeAttribute('min');
        pedUi.archiveDate.removeAttribute('max');
        pedUi.archiveYearDateHint.textContent = '';
        return;
    }
    const start = Number(match[1]);
    const from = `${start}-08-01`;
    const to = `${start + 1}-07-31`;
    pedUi.archiveDate.min = from;
    pedUi.archiveDate.max = to;
    pedUi.archiveYearDateHint.textContent =
        `Для учебного года ${pedUi.archiveYear.value} допустимы даты с 01.08.${start} по 31.07.${start + 1}.`;
}

function statusClass(status) {
    return `ped-status-${String(status || '').toLowerCase()}`;
}

function sourceLabel(sourceType) {
    return sourceType === 'ARCHIVE_WORD' ? 'Архивный Word' : 'Конструктор';
}

function formatDate(value) {
    if (!value) return '—';
    const [year, month, day] = value.split('-');
    return `${day}.${month}.${year}`;
}

function currentPermissions(user) {
    if (user?.admin) {
        return { canView: true, canEdit: true, canImport: true, canExport: true };
    }
    const permission = (user?.tabPermissions || [])
        .find((row) => row.tab === 'DOCUMENTS_PEDAGOGICAL_COUNCILS');
    return {
        canView: Boolean(permission?.canView),
        canEdit: Boolean(permission?.canEdit),
        canImport: Boolean(permission?.canImport),
        canExport: Boolean(permission?.canExport)
    };
}

function renderList() {
    if (!pedState.protocols.length) {
        pedUi.listBody.innerHTML = '<tr><td colspan="7" class="muted">В выбранном учебном году протоколы пока не добавлены.</td></tr>';
        return;
    }
    pedUi.listBody.innerHTML = pedState.protocols.map((protocol) => {
        const editAction = protocol.sourceType === 'CONSTRUCTOR' && pedState.permissions.canEdit
            ? `<button type="button" data-edit-protocol="${protocol.id}">Открыть</button>`
            : '';
        const extractAction = protocol.sourceType === 'CONSTRUCTOR' && pedState.permissions.canExport
            ? `<button type="button" data-extract-protocol="${protocol.id}">Выписка</button>`
            : '';
        const downloadAction = pedState.permissions.canExport
            ? `<button type="button" data-download-protocol="${protocol.id}">Скачать Word</button>`
            : '';
        return `
            <tr>
                <td><strong>№ ${pedEsc(protocol.protocolNumber)}</strong><br><span class="muted">${formatDate(protocol.meetingDate)}</span></td>
                <td><span class="table-badge">${sourceLabel(protocol.sourceType)}</span>${protocol.fileName ? `<br><span class="muted">${pedEsc(protocol.fileName)}</span>` : ''}</td>
                <td><span class="table-badge ${statusClass(protocol.status)}">${pedStatusLabels[protocol.status] || pedEsc(protocol.status)}</span></td>
                <td>${protocol.sourceType === 'ARCHIVE_WORD' ? '—' : protocol.itemCount}</td>
                <td>${protocol.sourceType === 'ARCHIVE_WORD' ? '—' : protocol.attachmentCount}</td>
                <td>${pedEsc(protocol.createdByFio || '—')}</td>
                <td><div class="row compact-row pedagogical-actions">${editAction}${downloadAction}${extractAction}</div></td>
            </tr>`;
    }).join('');
}

function staffOptions(selectedId = null) {
    return '<option value="">Выберите сотрудника</option>' + pedState.staff.map((person) =>
        `<option value="${person.id}" data-position="${pedEsc(person.position)}" data-fio="${pedEsc(person.shortFio)}" ${String(person.id) === String(selectedId) ? 'selected' : ''}>${pedEsc(person.shortFio || person.fio)}</option>`
    ).join('');
}

function staffFioOptions(selectedFio = '') {
    const normalizedSelected = String(selectedFio || '').trim();
    const options = (pedState.staff || []).map((person) => ({
        value: String(person.shortFio || person.fio || '').trim(),
        fullFio: String(person.fio || '').trim(),
        position: String(person.position || '').trim()
    })).filter((person) => person.value);
    const selectedPerson = options.find((person) =>
        person.value.toLocaleLowerCase('ru') === normalizedSelected.toLocaleLowerCase('ru')
        || person.fullFio.toLocaleLowerCase('ru') === normalizedSelected.toLocaleLowerCase('ru')
    );
    const effectiveSelected = selectedPerson?.value || normalizedSelected;
    if (effectiveSelected && !options.some((person) => person.value === effectiveSelected)) {
        options.push({ value: effectiveSelected, fullFio: effectiveSelected });
    }
    const uniqueValues = [...new Map(options.map((person) => [person.value, person])).values()]
        .sort((a, b) => a.value.localeCompare(b.value, 'ru'));
    return '<option value="">Выберите сотрудника</option>' + uniqueValues.map((person) =>
        `<option value="${pedEsc(person.value)}" data-position="${pedEsc(person.position)}" ${person.value === effectiveSelected ? 'selected' : ''}>${pedEsc(person.value)}</option>`
    ).join('');
}

function positionOptions(selectedPosition = '') {
    const normalizedSelected = String(selectedPosition || '').trim();
    const positions = [
        'Директор',
        'Заместитель директора',
        'Методист',
        'Учитель',
        ...(pedState.staff || []).map((person) => person.position),
        ...(pedState.certifiers || []).map((person) => person.position)
    ].map((value) => String(value || '').trim()).filter(Boolean);
    if (normalizedSelected) positions.push(normalizedSelected);
    const unique = [...new Set(positions)].sort((a, b) => a.localeCompare(b, 'ru'));
    return '<option value="">Выберите должность</option>' + unique.map((position) =>
        `<option value="${pedEsc(position)}" ${position === normalizedSelected ? 'selected' : ''}>${pedEsc(position)}</option>`
    ).join('');
}

function selectedOptionPosition(select) {
    const option = select?.options?.[select.selectedIndex];
    return String(option?.dataset?.position || '').trim();
}

function fillPositionFromPerson(personSelect, positionSelect, roleOnly = false) {
    const position = selectedOptionPosition(personSelect);
    if (!position || !positionSelect) return;
    if (roleOnly) {
        const role = signerPositionForForm(position);
        if (role) positionSelect.value = role;
        return;
    }
    positionSelect.innerHTML = positionOptions(position);
    positionSelect.value = position;
}

function signerPositionForForm(position) {
    const normalized = String(position || '').trim().toLocaleLowerCase('ru');
    if (normalized.includes('замест') && normalized.includes('директор')) return 'Заместитель директора';
    if (normalized.includes('директор')) return 'Директор';
    if (normalized.includes('методист')) return 'Методист';
    if (normalized.includes('учитель')) return 'Учитель';
    return '';
}

function currentSchoolName() {
    if (pedState.editing?.schoolName) return pedState.editing.schoolName;
    const code = String(pedState.branding?.schoolCode || '').trim();
    if (code && code.toLowerCase() !== 'demo') return `ГБОУ Школа № ${code}`;
    return pedState.branding?.appTitle || 'ГБОУ Школа';
}

function setEditorHeader(protocol) {
    pedUi.editorTitle.textContent = protocol?.id
        ? `Протокол № ${protocol.protocolNumber}`
        : 'Новый протокол';
    pedUi.academicYear.value = protocol?.academicYear || selectedAcademicYear();
    pedUi.academicYear.readOnly = Boolean(protocol?.id);
    pedUi.number.value = protocol?.protocolNumber || '';
    pedUi.date.value = protocol?.meetingDate || '';
    pedUi.time.value = protocol?.agendaTime || '';
    pedUi.attendeeCount.value = protocol?.attendeeCount ?? 0;
    pedUi.status.value = protocol?.status || 'DRAFT';
    pedUi.status.disabled = !protocol?.id;
    pedUi.chairPosition.value = signerPositionForForm(protocol?.chairPosition);
    pedUi.chairFio.innerHTML = staffFioOptions(protocol?.chairFio);
    pedUi.secretaryPosition.value = signerPositionForForm(protocol?.secretaryPosition);
    pedUi.secretaryFio.innerHTML = staffFioOptions(protocol?.secretaryFio);
}

function itemValues(node) {
    const value = (field) => node.querySelector(`[data-field="${field}"]`)?.value || '';
    return {
        id: value('id') ? Number(value('id')) : null,
        agendaTitle: value('agendaTitle').trim(),
        agendaDurationMinutes: Number(value('agendaDurationMinutes') || 10),
        speakerTeacherId: value('speakerTeacherId') ? Number(value('speakerTeacherId')) : null,
        speakerPosition: value('speakerPosition').trim(),
        speechContent: value('speechContent').trim(),
        decisionText: value('decisionText').trim(),
        votesFor: Number(value('votesFor') || 0),
        votesAgainst: Number(value('votesAgainst') || 0),
        votesAbstained: Number(value('votesAbstained') || 0)
    };
}

function updateVoteHint(node) {
    const total = Math.max(0, Number(pedUi.attendeeCount.value || 0));
    const voteControls = [
        node.querySelector('[data-field="votesFor"]'),
        node.querySelector('[data-field="votesAgainst"]'),
        node.querySelector('[data-field="votesAbstained"]')
    ].filter(Boolean);
    const distributed = voteControls.reduce(
        (sum, control) => sum + Math.max(0, Number(control.value || 0)),
        0
    );
    const remaining = total - distributed;
    const hint = node.querySelector('[data-vote-hint]');
    if (!hint) return;

    hint.classList.remove('is-complete', 'is-over');
    voteControls.forEach((control) => control.setCustomValidity(''));
    if (total === 0 && distributed === 0) {
        hint.textContent = 'Укажите число присутствующих — система посчитает оставшиеся голоса.';
        return;
    }
    if (remaining > 0) {
        hint.textContent = `Всего: ${total}. Распределено: ${distributed}. Осталось: ${remaining}.`;
        return;
    }
    if (remaining === 0) {
        hint.textContent = `Всего: ${total}. Все ${distributed} голосов распределены.`;
        hint.classList.add('is-complete');
        return;
    }

    const exceeded = Math.abs(remaining);
    const message = `Голосов больше числа присутствующих на ${exceeded}. Проверьте значения.`;
    hint.textContent = `Всего: ${total}. Распределено: ${distributed}. Превышение: ${exceeded}.`;
    hint.classList.add('is-over');
    voteControls.forEach((control) => control.setCustomValidity(message));
}

function updateAllVoteHints() {
    pedUi.items.querySelectorAll('[data-item]').forEach(updateVoteHint);
}

function collectItems() {
    return Array.from(pedUi.items.querySelectorAll('[data-item]')).map(itemValues);
}

function speakerDescription(itemNode) {
    const personSelect = itemNode.querySelector('[data-field="speakerTeacherId"]');
    const selected = personSelect?.options[personSelect.selectedIndex];
    if (!selected?.value) return 'докладчик не выбран';
    const position = itemNode.querySelector('[data-field="speakerPosition"]')?.value || '';
    return `${position} ${selected.dataset.fio || ''}`.trim();
}

function renderPreview() {
    const items = Array.from(pedUi.items.querySelectorAll('[data-item]'));
    const chair = [pedUi.chairPosition.value, pedUi.chairFio.value.trim()].filter(Boolean).join(' ');
    const secretary = [pedUi.secretaryPosition.value, pedUi.secretaryFio.value.trim()].filter(Boolean).join(' ');
    const agenda = items.map((itemNode, index) => {
        const item = itemValues(itemNode);
        const attachmentNumbers = Array.from(itemNode.querySelectorAll(
            '[data-attachment-number], [data-pending-attachment-number]'
        )).map((element) => element.dataset.attachmentNumber
            || String(element.textContent || '').replace(/\D+/g, '')).filter(Boolean);
        const attachmentText = attachmentNumbers.length
            ? `<p class="ped-preview-attachment"><em>Приложения: № ${attachmentNumbers.join(', № ')}.</em></p>`
            : '';
        return `
            <div class="ped-preview-item">
                <p><strong>${index + 1}. ${pedEsc(item.agendaTitle || 'Вопрос повестки')}</strong> — ${item.agendaDurationMinutes} минут</p>
                <p><strong>Слушали:</strong> ${pedEsc(speakerDescription(itemNode))}${item.speechContent ? ` ${pedEsc(item.speechContent)}` : ''}</p>
                <p><strong>Решили:</strong> ${pedEsc(item.decisionText || 'Текст решения')}</p>
                ${attachmentText}
                <p><strong>Голосовали:</strong> за — ${item.votesFor}, против — ${item.votesAgainst}, воздержались — ${item.votesAbstained}.</p>
            </div>`;
    }).join('');
    pedUi.preview.innerHTML = `
        <div class="ped-preview-school">${pedEsc(currentSchoolName())}</div>
        <h4>ПРОТОКОЛ № ${pedEsc(pedUi.number.value || '—')}</h4>
        <p class="ped-preview-center">заседания педагогического совета<br>от ${pedEsc(formatDate(pedUi.date.value))}</p>
        <p>Присутствовали: ${Number(pedUi.attendeeCount.value || 0)} чел.</p>
        <p>Председатель: ${chair ? pedEsc(chair) : 'не указан'}</p>
        <p>Секретарь: ${secretary ? pedEsc(secretary) : 'не указан'}</p>
        <h4>Повестка педагогического совета</h4>
        ${agenda || '<p class="muted">Добавьте пункт протокола.</p>'}`;
}

function renderAttachments(node, item) {
    const list = node.querySelector('[data-attachment-list]');
    const attachments = item?.attachments || [];
    node._savedAttachments = attachments;
    const pending = node._pendingAttachments || [];
    const savedHtml = attachments.map((attachment) => `
            <div class="pedagogical-attachment-row" data-attachment-id="${attachment.id}" data-attachment-number="${attachment.attachmentNumber}">
                <span><strong>Приложение № ${attachment.attachmentNumber}</strong> · ${pedEsc(attachment.originalFilename)}</span>
                <span>
                    <button type="button" data-download-attachment="${attachment.id}">Скачать</button>
                    ${pedState.permissions.canEdit ? `<button type="button" data-delete-attachment="${attachment.id}">Удалить</button>` : ''}
                </span>
            </div>`).join('');
    const pendingHtml = pending.map((file, index) => `
            <div class="pedagogical-attachment-row is-pending" data-pending-attachment="${index}">
                <span><strong data-pending-attachment-number>Ожидает номера</strong> · ${pedEsc(file.name)}</span>
                <button type="button" data-remove-pending-attachment="${index}">Убрать</button>
            </div>`).join('');
    list.innerHTML = savedHtml + pendingHtml
        || '<span class="muted">Приложений пока нет.</span>';
    updateAttachmentNumbers();
}

function updateAttachmentNumbers() {
    const existingNumbers = Array.from(pedUi.items.querySelectorAll('[data-attachment-number]'))
        .map((row) => Number(row.dataset.attachmentNumber || 0))
        .filter((value) => value > 0);
    let nextNumber = Math.max(0, ...existingNumbers) + 1;
    pedUi.items.querySelectorAll('[data-item]').forEach((node) => {
        node.querySelectorAll('[data-pending-attachment-number]').forEach((label) => {
            label.textContent = `Приложение № ${nextNumber++}`;
        });
    });
    pedUi.items.querySelectorAll('[data-next-attachment-number]').forEach((label) => {
        label.textContent = `№ ${nextNumber}`;
    });
}

function addItemNode(item = {}) {
    const fragment = pedUi.itemTemplate.content.cloneNode(true);
    const node = fragment.querySelector('[data-item]');
    node.dataset.itemId = item.id || '';
    const set = (field, value) => {
        const control = node.querySelector(`[data-field="${field}"]`);
        if (control) control.value = value ?? '';
    };
    set('id', item.id);
    set('agendaTitle', item.agendaTitle);
    set('agendaDurationMinutes', item.agendaDurationMinutes ?? 10);
    const speaker = node.querySelector('[data-field="speakerTeacherId"]');
    speaker.innerHTML = staffOptions(item.speakerTeacherId);
    const speakerPosition = node.querySelector('[data-field="speakerPosition"]');
    speakerPosition.innerHTML = positionOptions(item.speakerPosition);
    speakerPosition.value = item.speakerPosition || '';
    speaker.addEventListener('change', () => {
        fillPositionFromPerson(speaker, speakerPosition);
        renderPreview();
    });
    set('speechContent', item.speechContent);
    set('decisionText', item.decisionText);
    set('votesFor', item.votesFor ?? 0);
    set('votesAgainst', item.votesAgainst ?? 0);
    set('votesAbstained', item.votesAbstained ?? 0);

    node._pendingAttachments = [];
    renderAttachments(node, item);
    node.querySelector('[data-remove-item]').addEventListener('click', () => {
        node.remove();
        renumberItems();
        renderPreview();
    });
    updateVoteHint(node);
    node.querySelectorAll('input, select, textarea').forEach((control) => {
        const refresh = () => {
            updateVoteHint(node);
            renderPreview();
        };
        control.addEventListener('input', refresh);
        control.addEventListener('change', refresh);
    });
    node.querySelector('[data-attachment-upload]').addEventListener('change', (event) => {
        const files = Array.from(event.target.files || []);
        const invalid = files.find((file) => !file.name.toLocaleLowerCase('ru').endsWith('.docx'));
        if (invalid) {
            pedUi.editorFeedback.textContent = `Файл «${invalid.name}» не является документом Word .docx.`;
            event.target.value = '';
            return;
        }
        node._pendingAttachments.push(...files);
        renderAttachments(node, { attachments: node._savedAttachments || [] });
        renderPreview();
        pedUi.editorFeedback.textContent = files.length
            ? 'Приложения готовы к загрузке и попадут на сервер при сохранении протокола.'
            : '';
        event.target.value = '';
    });
    pedUi.items.appendChild(fragment);
    renumberItems();
    updateAttachmentNumbers();
}

function renderEditorItems(items) {
    pedUi.items.innerHTML = '';
    (items || []).forEach(addItemNode);
    if (!items?.length) addItemNode();
    renderPreview();
}

function renumberItems() {
    pedUi.items.querySelectorAll('[data-item]').forEach((node, index) => {
        node.querySelector('[data-item-title]').textContent = `Пункт ${index + 1}`;
    });
}

function newProtocolDate(year) {
    if (!/^\d{4}\/\d{4}$/.test(year || '')) return '';
    const today = new Date();
    const iso = today.toISOString().slice(0, 10);
    const start = `${year.slice(0, 4)}-08-01`;
    const end = `${year.slice(5)}-07-31`;
    return iso >= start && iso <= end ? iso : start;
}

function openNewProtocol() {
    pedState.editing = null;
    const year = selectedAcademicYear();
    setEditorHeader({
        academicYear: year,
        meetingDate: newProtocolDate(year),
        attendeeCount: 0,
        status: 'DRAFT',
        items: []
    });
    renderEditorItems([]);
    pedUi.editorFeedback.textContent = '';
    pedUi.editor.showModal();
}

async function openProtocol(id) {
    try {
        pedState.editing = await pedApi(`/api/pedagogical-councils/${id}`);
        setEditorHeader(pedState.editing);
        renderEditorItems(pedState.editing.items);
        pedUi.editorFeedback.textContent = '';
        pedUi.editor.showModal();
    } catch (error) {
        pedUi.listFeedback.textContent = error.message;
    }
}

function editorPayload() {
    return {
        academicYear: pedUi.academicYear.value.trim(),
        protocolNumber: pedUi.number.value.trim(),
        meetingDate: pedUi.date.value,
        agendaTime: pedUi.time.value || null,
        attendeeCount: Number(pedUi.attendeeCount.value || 0),
        chairPosition: pedUi.chairPosition.value || null,
        chairFio: pedUi.chairFio.value.trim() || null,
        secretaryPosition: pedUi.secretaryPosition.value || null,
        secretaryFio: pedUi.secretaryFio.value.trim() || null,
        status: pedUi.status.value,
        version: pedState.editing?.version ?? null,
        items: collectItems()
    };
}

function pendingAttachmentGroups() {
    return Array.from(pedUi.items.querySelectorAll('[data-item]')).map((node, index) => ({
        index,
        node,
        files: [...(node._pendingAttachments || [])]
    })).filter((group) => group.files.length);
}

function syncSavedItemIds(items) {
    const nodes = Array.from(pedUi.items.querySelectorAll('[data-item]'));
    (items || []).forEach((item, index) => {
        const node = nodes[index];
        if (!node) return;
        node.dataset.itemId = item.id || '';
        const idField = node.querySelector('[data-field="id"]');
        if (idField) idField.value = item.id || '';
    });
}

async function uploadPendingAttachments(protocol, pendingGroups) {
    let uploaded = 0;
    for (const group of pendingGroups) {
        const savedItem = protocol.items?.[group.index];
        if (!savedItem?.id) {
            throw new Error(`Не удалось определить сохранённый пункт ${group.index + 1} для приложения.`);
        }
        for (const file of group.files) {
            const form = new FormData();
            form.append('file', file);
            await pedApi(`/api/pedagogical-councils/${protocol.id}/items/${savedItem.id}/attachments`, {
                method: 'POST',
                body: form
            });
            uploaded += 1;
        }
        group.node._pendingAttachments = [];
    }
    return uploaded;
}

async function saveProtocol(event) {
    event.preventDefault();
    pedUi.editorFeedback.textContent = 'Сохраняем…';
    pedUi.editorSave.disabled = true;
    let protocolSaved = false;
    try {
        const payload = editorPayload();
        const pendingGroups = pendingAttachmentGroups();
        const created = !pedState.editing?.id;
        const path = created
            ? '/api/pedagogical-councils'
            : `/api/pedagogical-councils/${pedState.editing.id}`;
        pedState.editing = await pedApi(path, {
            method: created ? 'POST' : 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        protocolSaved = true;
        syncSavedItemIds(pedState.editing.items);
        const uploadedCount = await uploadPendingAttachments(pedState.editing, pendingGroups);
        if (uploadedCount) {
            pedState.editing = await pedApi(`/api/pedagogical-councils/${pedState.editing.id}`);
        }
        setEditorHeader(pedState.editing);
        renderEditorItems(pedState.editing.items);
        pedUi.editorFeedback.textContent = uploadedCount
            ? `Протокол сохранён. Приложений загружено: ${uploadedCount}.`
            : 'Протокол сохранён.';
        await reloadProtocols();
    } catch (error) {
        pedUi.editorFeedback.textContent = protocolSaved
            ? `Протокол сохранён, но одно из приложений не загрузилось: ${error.message}`
            : error.message;
    } finally {
        pedUi.editorSave.disabled = false;
    }
}

async function uploadArchive(event) {
    event.preventDefault();
    pedUi.archiveFeedback.textContent = 'Загружаем документ…';
    const submit = pedUi.archiveForm.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
        const form = new FormData(pedUi.archiveForm);
        const result = await pedApi('/api/pedagogical-councils/archive', { method: 'POST', body: form });
        sessionStorage.setItem('tarification.academicYear', result.academicYear);
        const yearSelect = document.getElementById('academic-year-select');
        if (yearSelect && !Array.from(yearSelect.options).some((option) => option.value === result.academicYear)) {
            yearSelect.add(new Option(result.academicYear, result.academicYear));
        }
        if (yearSelect) yearSelect.value = result.academicYear;
        pedUi.archiveFeedback.textContent = 'Архивный протокол сохранён.';
        pedUi.archiveDialog.close();
        pedUi.archiveForm.reset();
        await reloadProtocols();
    } catch (error) {
        pedUi.archiveFeedback.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
}

function openArchiveDialog() {
    pedUi.archiveForm.reset();
    fillAcademicYearSelect(pedUi.archiveYear, selectedAcademicYear());
    updateArchiveYearBounds();
    pedUi.archiveFeedback.textContent = '';
    pedUi.archiveDialog.showModal();
}

function downloadName(response, fallback) {
    const raw = response.headers.get('X-File-Name') || '';
    if (!raw) return fallback;
    try {
        return decodeURIComponent(raw);
    } catch {
        return fallback;
    }
}

async function downloadWord(path, options = {}, fallback = 'Документ.docx') {
    const response = await fetch(path, options);
    if (!response.ok) {
        let message = `HTTP ${response.status}`;
        try {
            message = (await response.json())?.message || message;
        } catch {
            // keep HTTP message
        }
        throw new Error(message);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = downloadName(response, fallback);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

function certifierOptions(selectedUserId = null) {
    return '<option value="">Выберите сотрудника</option>' + (pedState.certifiers || []).map((person) =>
        `<option value="${person.userId}" data-position="${pedEsc(person.position)}" ${String(person.userId) === String(selectedUserId) ? 'selected' : ''}>${pedEsc(person.shortFio || person.fio)}</option>`
    ).join('');
}

function addCertifierRow(selectedUserId = null, selectedPosition = '', locked = false) {
    const row = document.createElement('div');
    row.className = 'pedagogical-signer-row';
    row.innerHTML = `
        <label>Должность
            <select data-certifier-position>${positionOptions(selectedPosition)}</select>
        </label>
        <label>ФИО
            <select data-certifier-user ${locked ? 'disabled' : ''}>${certifierOptions(selectedUserId)}</select>
        </label>
        ${locked
            ? '<span class="muted pedagogical-signer-note">Формирует выписку</span>'
            : '<button type="button" data-remove-certifier>Убрать</button>'}`;
    const userSelect = row.querySelector('[data-certifier-user]');
    const positionSelect = row.querySelector('[data-certifier-position]');
    userSelect.value = selectedUserId || '';
    positionSelect.value = selectedPosition || '';
    userSelect.addEventListener('change', () => fillPositionFromPerson(userSelect, positionSelect));
    row.querySelector('[data-remove-certifier]')?.addEventListener('click', () => row.remove());
    pedUi.extractCertifiers.appendChild(row);
}

function currentCertifier() {
    const currentId = pedState.user?.id;
    const fromAccounts = (pedState.certifiers || []).find((person) => String(person.userId) === String(currentId));
    if (fromAccounts) return fromAccounts;
    const currentFio = String(pedState.user?.fullName || '').trim().toLocaleLowerCase('ru');
    const staff = (pedState.staff || []).find((person) =>
        String(person.fio || '').trim().toLocaleLowerCase('ru') === currentFio);
    return {
        userId: currentId,
        teacherId: staff?.id || null,
        fio: staff?.fio || pedState.user?.fullName || '',
        shortFio: staff?.shortFio || pedState.user?.fullName || '',
        position: staff?.position || 'Уполномоченный сотрудник'
    };
}

async function openExtract(id) {
    try {
        pedState.extractProtocol = await pedApi(`/api/pedagogical-councils/${id}`);
        pedUi.extractItems.innerHTML = pedState.extractProtocol.items.map((item) => {
            const attachments = item.attachments?.length
                ? ` · приложения: ${item.attachments.map((a) => `№ ${a.attachmentNumber}`).join(', ')}`
                : '';
            return `<label class="pedagogical-check"><input type="checkbox" name="extractItem" value="${item.id}">Пункт ${item.itemOrder}. ${pedEsc(item.agendaTitle)}${pedEsc(attachments)}</label>`;
        }).join('');
        pedUi.extractCertifiers.innerHTML = '';
        const current = currentCertifier();
        addCertifierRow(current.userId, current.position, true);
        pedUi.extractApprover.innerHTML = staffOptions();
        pedUi.extractApproverPosition.innerHTML = positionOptions();
        const director = (pedState.staff || []).find((person) =>
            String(person.position || '').trim().toLocaleLowerCase('ru') === 'директор');
        if (director) {
            pedUi.extractApprover.value = director.id;
            fillPositionFromPerson(pedUi.extractApprover, pedUi.extractApproverPosition);
        }
        pedUi.extractExternal.checked = false;
        pedUi.extractSourceSigners.checked = false;
        pedUi.extractApproval.checked = false;
        pedUi.extractApproverRow.hidden = true;
        pedUi.extractFeedback.textContent = '';
        pedUi.extractDialog.showModal();
    } catch (error) {
        pedUi.listFeedback.textContent = error.message;
    }
}

async function downloadExtract(event) {
    event.preventDefault();
    if (!pedState.extractProtocol) return;
    const itemIds = Array.from(pedUi.extractItems.querySelectorAll('input:checked')).map((input) => Number(input.value));
    const certifiers = Array.from(pedUi.extractCertifiers.querySelectorAll('.pedagogical-signer-row'))
        .map((row) => ({
            userId: Number(row.querySelector('[data-certifier-user]')?.value || 0),
            position: row.querySelector('[data-certifier-position]')?.value || ''
        }))
        .filter((row) => row.userId);
    const payload = {
        itemIds,
        certifiers,
        externalRecipient: pedUi.extractExternal.checked,
        includeSourceSigners: pedUi.extractSourceSigners.checked,
        separateApproval: pedUi.extractApproval.checked,
        approverTeacherId: pedUi.extractApprover.value ? Number(pedUi.extractApprover.value) : null,
        approverPosition: pedUi.extractApproverPosition.value || null
    };
    pedUi.extractFeedback.textContent = 'Формируем выписку…';
    try {
        await downloadWord(`/api/pedagogical-councils/${pedState.extractProtocol.id}/extract`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        }, 'Выписка.docx');
        pedUi.extractFeedback.textContent = 'Выписка сформирована.';
    } catch (error) {
        pedUi.extractFeedback.textContent = error.message;
    }
}

async function reloadProtocols() {
    let year = selectedAcademicYear();
    if (!year) {
        const active = await pedApi('/api/academic-years/active');
        year = active.active;
        sessionStorage.setItem('tarification.academicYear', year);
    }
    pedUi.listFeedback.textContent = 'Загружаем протоколы…';
    pedState.protocols = await pedApi(`/api/pedagogical-councils?academicYear=${encodeURIComponent(year)}`);
    renderList();
    pedUi.listFeedback.textContent = '';
}

async function deleteAttachment(attachmentId) {
    if (!pedState.editing?.id) return;
    try {
        await pedApi(`/api/pedagogical-councils/${pedState.editing.id}/attachments/${attachmentId}`, { method: 'DELETE' });
        pedState.editing = await pedApi(`/api/pedagogical-councils/${pedState.editing.id}`);
        renderEditorItems(pedState.editing.items);
        pedUi.editorFeedback.textContent = 'Приложение удалено.';
        await reloadProtocols();
    } catch (error) {
        pedUi.editorFeedback.textContent = error.message;
    }
}

pedUi.newButton.addEventListener('click', openNewProtocol);
pedUi.uploadArchiveButton.addEventListener('click', openArchiveDialog);
pedUi.addItem.addEventListener('click', () => {
    addItemNode();
    renderPreview();
});
pedUi.editorForm.addEventListener('submit', saveProtocol);
pedUi.archiveForm.addEventListener('submit', uploadArchive);
pedUi.archiveYear.addEventListener('change', updateArchiveYearBounds);
pedUi.extractForm.addEventListener('submit', downloadExtract);
pedUi.editorClose.addEventListener('click', () => pedUi.editor.close());
pedUi.editorCancel.addEventListener('click', () => pedUi.editor.close());
pedUi.archiveClose.addEventListener('click', () => pedUi.archiveDialog.close());
pedUi.archiveCancel.addEventListener('click', () => pedUi.archiveDialog.close());
pedUi.extractClose.addEventListener('click', () => pedUi.extractDialog.close());
pedUi.extractCancel.addEventListener('click', () => pedUi.extractDialog.close());
pedUi.extractAddCertifier.addEventListener('click', () => addCertifierRow());
pedUi.extractApproval.addEventListener('change', () => {
    pedUi.extractApproverRow.hidden = !pedUi.extractApproval.checked;
});
pedUi.extractApprover.addEventListener('change', () => {
    fillPositionFromPerson(pedUi.extractApprover, pedUi.extractApproverPosition);
});
pedUi.chairFio.addEventListener('change', () => {
    fillPositionFromPerson(pedUi.chairFio, pedUi.chairPosition, true);
    renderPreview();
});
pedUi.secretaryFio.addEventListener('change', () => {
    fillPositionFromPerson(pedUi.secretaryFio, pedUi.secretaryPosition, true);
    renderPreview();
});
[
    pedUi.number,
    pedUi.date,
    pedUi.attendeeCount,
    pedUi.chairPosition,
    pedUi.chairFio,
    pedUi.secretaryPosition,
    pedUi.secretaryFio
].forEach((control) => {
    const refresh = () => {
        if (control === pedUi.attendeeCount) updateAllVoteHints();
        renderPreview();
    };
    control.addEventListener('input', refresh);
    control.addEventListener('change', refresh);
});

pedUi.listBody.addEventListener('click', async (event) => {
    const edit = event.target.closest('[data-edit-protocol]');
    const download = event.target.closest('[data-download-protocol]');
    const extract = event.target.closest('[data-extract-protocol]');
    if (edit) await openProtocol(Number(edit.dataset.editProtocol));
    if (extract) await openExtract(Number(extract.dataset.extractProtocol));
    if (download) {
        pedUi.listFeedback.textContent = 'Формируем Word…';
        try {
            await downloadWord(`/api/pedagogical-councils/${download.dataset.downloadProtocol}/download`);
            pedUi.listFeedback.textContent = '';
        } catch (error) {
            pedUi.listFeedback.textContent = error.message;
        }
    }
});

pedUi.items.addEventListener('click', async (event) => {
    const download = event.target.closest('[data-download-attachment]');
    const remove = event.target.closest('[data-delete-attachment]');
    const removePending = event.target.closest('[data-remove-pending-attachment]');
    if (download && pedState.editing?.id) {
        try {
            await downloadWord(`/api/pedagogical-councils/${pedState.editing.id}/attachments/${download.dataset.downloadAttachment}/download`);
        } catch (error) {
            pedUi.editorFeedback.textContent = error.message;
        }
    }
    if (remove) {
        await deleteAttachment(Number(remove.dataset.deleteAttachment));
    }
    if (removePending) {
        const node = removePending.closest('[data-item]');
        const index = Number(removePending.dataset.removePendingAttachment);
        node._pendingAttachments.splice(index, 1);
        renderAttachments(node, { attachments: node._savedAttachments || [] });
        renderPreview();
    }
});

(async function initPedagogicalCouncils() {
    try {
        const [user, staff, certifiers, academicYears, branding] = await Promise.all([
            pedApi('/api/auth/me'),
            pedApi('/api/pedagogical-councils/staff'),
            pedApi('/api/pedagogical-councils/certifiers'),
            pedApi('/api/academic-years'),
            pedApi('/api/public/branding')
        ]);
        pedState.user = user;
        pedState.permissions = currentPermissions(user);
        pedState.staff = staff || [];
        pedState.certifiers = certifiers || [];
        pedState.academicYears = academicYears || [];
        pedState.branding = branding || null;
        pedUi.newButton.hidden = !pedState.permissions.canEdit;
        pedUi.uploadArchiveButton.hidden = !pedState.permissions.canImport;
        await reloadProtocols();
    } catch (error) {
        pedUi.listFeedback.textContent = error.message;
    }
})();
