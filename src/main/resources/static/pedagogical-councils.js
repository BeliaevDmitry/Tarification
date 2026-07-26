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
    chair: document.getElementById('protocol-chair'),
    secretary: document.getElementById('protocol-secretary'),
    addItem: document.getElementById('add-protocol-item'),
    items: document.getElementById('protocol-items'),
    itemTemplate: document.getElementById('protocol-item-template'),
    preview: document.getElementById('protocol-preview-paper'),
    archiveDialog: document.getElementById('archive-upload-dialog'),
    archiveForm: document.getElementById('archive-upload-form'),
    archiveYear: document.getElementById('archive-academic-year'),
    archiveFeedback: document.getElementById('archive-upload-feedback'),
    archiveClose: document.getElementById('archive-upload-close'),
    archiveCancel: document.getElementById('archive-upload-cancel'),
    extractDialog: document.getElementById('extract-dialog'),
    extractForm: document.getElementById('extract-form'),
    extractItems: document.getElementById('extract-items'),
    extractCertifiers: document.getElementById('extract-certifiers'),
    extractExternal: document.getElementById('extract-external'),
    extractStorageRow: document.getElementById('extract-storage-row'),
    extractStorage: document.getElementById('extract-storage'),
    extractApproval: document.getElementById('extract-approval'),
    extractApproverRow: document.getElementById('extract-approver-row'),
    extractApprover: document.getElementById('extract-approver'),
    extractFeedback: document.getElementById('extract-feedback'),
    extractClose: document.getElementById('extract-close'),
    extractCancel: document.getElementById('extract-cancel')
};

const pedState = {
    protocols: [],
    staff: [],
    certifiers: [],
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
        `<option value="${person.id}" data-position="${pedEsc(person.position)}" data-fio="${pedEsc(person.shortFio)}" ${String(person.id) === String(selectedId) ? 'selected' : ''}>${pedEsc(person.fio)} — ${pedEsc(person.position)}</option>`
    ).join('');
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
    pedUi.chair.innerHTML = staffOptions(protocol?.chairTeacherId);
    pedUi.secretary.innerHTML = staffOptions(protocol?.secretaryTeacherId);
}

function itemValues(node) {
    const value = (field) => node.querySelector(`[data-field="${field}"]`)?.value || '';
    return {
        id: value('id') ? Number(value('id')) : null,
        agendaTitle: value('agendaTitle').trim(),
        agendaTime: value('agendaTime') || null,
        speakerTeacherId: value('speakerTeacherId') ? Number(value('speakerTeacherId')) : null,
        speechContent: value('speechContent').trim(),
        decisionText: value('decisionText').trim(),
        votesFor: Number(value('votesFor') || 0),
        votesAgainst: Number(value('votesAgainst') || 0),
        votesAbstained: Number(value('votesAbstained') || 0)
    };
}

function collectItems() {
    return Array.from(pedUi.items.querySelectorAll('[data-item]')).map(itemValues);
}

function speakerDescription(itemNode) {
    const select = itemNode.querySelector('[data-field="speakerTeacherId"]');
    const selected = select?.options[select.selectedIndex];
    if (!selected?.value) return 'докладчик не выбран';
    return `${selected.dataset.position || ''} ${selected.dataset.fio || ''}`.trim();
}

function renderPreview() {
    const items = Array.from(pedUi.items.querySelectorAll('[data-item]'));
    const chair = pedUi.chair.options[pedUi.chair.selectedIndex];
    const secretary = pedUi.secretary.options[pedUi.secretary.selectedIndex];
    const agenda = items.map((itemNode, index) => {
        const item = itemValues(itemNode);
        const attachments = itemNode.querySelectorAll('[data-attachment-id]').length;
        const attachmentText = attachments
            ? `<p><em>Связано приложений: ${attachments}</em></p>`
            : '';
        return `
            <div class="ped-preview-item">
                <p><strong>${index + 1}. ${pedEsc(item.agendaTitle || 'Вопрос повестки')}</strong>${item.agendaTime ? ` — ${pedEsc(item.agendaTime)}` : ''}</p>
                <p><strong>Слушали:</strong> ${pedEsc(speakerDescription(itemNode))}${item.speechContent ? ` ${pedEsc(item.speechContent)}` : ''}</p>
                <p><strong>Решили:</strong> ${pedEsc(item.decisionText || 'Текст решения')}</p>
                ${attachmentText}
                <p><strong>Голосовали:</strong> за — ${item.votesFor}, против — ${item.votesAgainst}, воздержались — ${item.votesAbstained}.</p>
            </div>`;
    }).join('');
    pedUi.preview.innerHTML = `
        <div class="ped-preview-school">ГБОУ Школа · данные сервера</div>
        <h4>ПРОТОКОЛ № ${pedEsc(pedUi.number.value || '—')}</h4>
        <p class="ped-preview-center">заседания педагогического совета<br>от ${pedEsc(formatDate(pedUi.date.value))}</p>
        <p>Присутствовали: ${Number(pedUi.attendeeCount.value || 0)} чел.</p>
        <p>Председатель: ${chair?.value ? pedEsc(`${chair.dataset.position} ${chair.dataset.fio}`) : 'не выбран'}</p>
        <p>Секретарь: ${secretary?.value ? pedEsc(`${secretary.dataset.position} ${secretary.dataset.fio}`) : 'не выбран'}</p>
        <h4>Повестка педагогического совета</h4>
        ${agenda || '<p class="muted">Добавьте пункт протокола.</p>'}`;
}

function renderAttachments(node, item) {
    const list = node.querySelector('[data-attachment-list]');
    const attachments = item?.attachments || [];
    list.innerHTML = attachments.length
        ? attachments.map((attachment) => `
            <div class="pedagogical-attachment-row" data-attachment-id="${attachment.id}">
                <span><strong>Приложение № ${attachment.attachmentNumber}</strong> · ${pedEsc(attachment.originalFilename)}</span>
                <span>
                    <button type="button" data-download-attachment="${attachment.id}">Скачать</button>
                    ${pedState.permissions.canEdit ? `<button type="button" data-delete-attachment="${attachment.id}">Удалить</button>` : ''}
                </span>
            </div>`).join('')
        : '<span class="muted">Приложений пока нет.</span>';
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
    set('agendaTime', item.agendaTime);
    const speaker = node.querySelector('[data-field="speakerTeacherId"]');
    speaker.innerHTML = staffOptions(item.speakerTeacherId);
    set('speechContent', item.speechContent);
    set('decisionText', item.decisionText);
    set('votesFor', item.votesFor ?? 0);
    set('votesAgainst', item.votesAgainst ?? 0);
    set('votesAbstained', item.votesAbstained ?? 0);

    renderAttachments(node, item);
    node.querySelector('[data-remove-item]').addEventListener('click', () => {
        node.remove();
        renumberItems();
        renderPreview();
    });
    node.querySelectorAll('input, select, textarea').forEach((control) => {
        control.addEventListener('input', renderPreview);
        control.addEventListener('change', renderPreview);
    });
    node.querySelector('[data-attachment-upload]').addEventListener('change', async (event) => {
        const file = event.target.files?.[0];
        if (!file) return;
        const itemId = Number(node.dataset.itemId || 0);
        if (!pedState.editing?.id || !itemId) {
            event.target.value = '';
            pedUi.editorFeedback.textContent = 'Сначала сохраните протокол и пункт, затем загрузите приложение.';
            return;
        }
        try {
            const form = new FormData();
            form.append('file', file);
            await pedApi(`/api/pedagogical-councils/${pedState.editing.id}/items/${itemId}/attachments`, {
                method: 'POST',
                body: form
            });
            pedState.editing = await pedApi(`/api/pedagogical-councils/${pedState.editing.id}`);
            renderEditorItems(pedState.editing.items);
            pedUi.editorFeedback.textContent = 'Приложение загружено. Номер присвоен автоматически.';
            await reloadProtocols();
        } catch (error) {
            pedUi.editorFeedback.textContent = error.message;
        } finally {
            event.target.value = '';
        }
    });
    pedUi.items.appendChild(fragment);
    renumberItems();
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
        chairTeacherId: pedUi.chair.value ? Number(pedUi.chair.value) : null,
        secretaryTeacherId: pedUi.secretary.value ? Number(pedUi.secretary.value) : null,
        status: pedUi.status.value,
        version: pedState.editing?.version ?? null,
        items: collectItems()
    };
}

async function saveProtocol(event) {
    event.preventDefault();
    pedUi.editorFeedback.textContent = 'Сохраняем…';
    pedUi.editorSave.disabled = true;
    try {
        const payload = editorPayload();
        const created = !pedState.editing?.id;
        const path = created
            ? '/api/pedagogical-councils'
            : `/api/pedagogical-councils/${pedState.editing.id}`;
        pedState.editing = await pedApi(path, {
            method: created ? 'POST' : 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        setEditorHeader(pedState.editing);
        renderEditorItems(pedState.editing.items);
        pedUi.editorFeedback.textContent = 'Протокол сохранён. Теперь к пунктам можно добавлять Word-приложения.';
        await reloadProtocols();
    } catch (error) {
        pedUi.editorFeedback.textContent = error.message;
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
    pedUi.archiveYear.value = selectedAcademicYear();
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

async function openExtract(id) {
    try {
        pedState.extractProtocol = await pedApi(`/api/pedagogical-councils/${id}`);
        pedUi.extractItems.innerHTML = pedState.extractProtocol.items.map((item) => {
            const attachments = item.attachments?.length
                ? ` · приложения: ${item.attachments.map((a) => `№ ${a.attachmentNumber}`).join(', ')}`
                : '';
            return `<label class="pedagogical-check"><input type="checkbox" name="extractItem" value="${item.id}">Пункт ${item.itemOrder}. ${pedEsc(item.agendaTitle)}${pedEsc(attachments)}</label>`;
        }).join('');
        pedUi.extractCertifiers.innerHTML = pedState.certifiers.length
            ? pedState.certifiers.map((person) => `<label class="pedagogical-check"><input type="checkbox" name="certifier" value="${person.userId}">${pedEsc(person.fio)} — ${pedEsc(person.position)}</label>`).join('')
            : '<p class="text-destructive">В настройках пользователей никому не выдано право на скачивание и заверение документов.</p>';
        pedUi.extractApprover.innerHTML = '<option value="">Выберите сотрудника</option>' + pedState.staff
            .map((person) => `<option value="${person.id}">${pedEsc(person.fio)} — ${pedEsc(person.position)}</option>`)
            .join('');
        const directorOption = Array.from(pedUi.extractApprover.options)
            .find((option) => String(option.textContent || '').split('—').pop().trim().toLowerCase().startsWith('директор'));
        if (directorOption) pedUi.extractApprover.value = directorOption.value;
        pedUi.extractExternal.checked = false;
        pedUi.extractApproval.checked = false;
        pedUi.extractStorageRow.hidden = true;
        pedUi.extractApproverRow.hidden = true;
        pedUi.extractStorage.value = '';
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
    const certifierUserIds = Array.from(pedUi.extractCertifiers.querySelectorAll('input:checked')).map((input) => Number(input.value));
    const payload = {
        itemIds,
        certifierUserIds,
        externalRecipient: pedUi.extractExternal.checked,
        originalStorageLocation: pedUi.extractStorage.value.trim(),
        separateApproval: pedUi.extractApproval.checked,
        approverTeacherId: pedUi.extractApprover.value ? Number(pedUi.extractApprover.value) : null
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
pedUi.extractForm.addEventListener('submit', downloadExtract);
pedUi.editorClose.addEventListener('click', () => pedUi.editor.close());
pedUi.editorCancel.addEventListener('click', () => pedUi.editor.close());
pedUi.archiveClose.addEventListener('click', () => pedUi.archiveDialog.close());
pedUi.archiveCancel.addEventListener('click', () => pedUi.archiveDialog.close());
pedUi.extractClose.addEventListener('click', () => pedUi.extractDialog.close());
pedUi.extractCancel.addEventListener('click', () => pedUi.extractDialog.close());
pedUi.extractExternal.addEventListener('change', () => {
    pedUi.extractStorageRow.hidden = !pedUi.extractExternal.checked;
});
pedUi.extractApproval.addEventListener('change', () => {
    pedUi.extractApproverRow.hidden = !pedUi.extractApproval.checked;
});
[
    pedUi.number,
    pedUi.date,
    pedUi.attendeeCount,
    pedUi.chair,
    pedUi.secretary
].forEach((control) => {
    control.addEventListener('input', renderPreview);
    control.addEventListener('change', renderPreview);
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
});

(async function initPedagogicalCouncils() {
    try {
        const [user, staff, certifiers] = await Promise.all([
            pedApi('/api/auth/me'),
            pedApi('/api/pedagogical-councils/staff'),
            pedApi('/api/pedagogical-councils/certifiers')
        ]);
        pedState.permissions = currentPermissions(user);
        pedState.staff = staff || [];
        pedState.certifiers = certifiers || [];
        pedUi.newButton.hidden = !pedState.permissions.canEdit;
        pedUi.uploadArchiveButton.hidden = !pedState.permissions.canImport;
        await reloadProtocols();
    } catch (error) {
        pedUi.listFeedback.textContent = error.message;
    }
})();
