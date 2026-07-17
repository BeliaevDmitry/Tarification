const ui = {
    tabs: document.querySelectorAll('[data-memo-tab]'),
    panels: document.querySelectorAll('[data-memo-panel]'),
    pendingBody: document.getElementById('pending-body'),
    processedBody: document.getElementById('processed-body'),
    archivedBody: document.getElementById('archived-body'),
    refreshPendingBtn: document.getElementById('refresh-pending-btn'),
    selectAllPendingBtn: document.getElementById('select-all-pending-btn'),
    generateBtn: document.getElementById('generate-memos-btn'),
    settingsTitle: document.getElementById('memo-director-title'),
    settingsName: document.getElementById('memo-director-name'),
    settingsSaveBtn: document.getElementById('memo-settings-save-btn'),
    result: document.getElementById('memo-result')
};

async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text ? { message: text } : null;
    }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(value) {
    ui.result.textContent = JSON.stringify(value, null, 2);
}

function esc(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function switchTab(tab) {
    ui.tabs.forEach((button) => button.classList.toggle('active', button.dataset.memoTab === tab));
    ui.panels.forEach((panel) => panel.style.display = panel.dataset.memoPanel === tab ? '' : 'none');
}

function checkedTeachers() {
    return Array.from(document.querySelectorAll('[data-pending-check]:checked'))
        .map((el) => decodeURIComponent(el.dataset.pendingCheck))
        .filter(Boolean);
}

function setAllPending(checked) {
    document.querySelectorAll('[data-pending-check]').forEach((checkbox) => {
        checkbox.checked = checked;
    });
}

function renderPending(rows) {
    ui.pendingBody.innerHTML = rows.map((row) => `
        <tr>
            <td><input type="checkbox" data-pending-check="${encodeURIComponent(row.teacherKey || row.fioTeacher)}"></td>
            <td>${esc(row.fioTeacher)}</td>
            <td>${esc(row.startDate || '')}</td>
            <td>${row.memoType === 'NEW' ? 'Назначение' : 'Изменение'}</td>
            <td>${row.totalHours}</td>
        </tr>
    `).join('');
}

function actionButtons(row, archived = false) {
    const download = `<button type="button" data-download-id="${row.id}">Скачать служебку</button>`;
    if (archived) return download;
    const archive = ['RECEIVED_BY_HR','EXECUTED'].includes(row.status) ? `<button type="button" data-archive-id="${row.id}">Отправить в архив</button>` : '';
    return `${download} ${archive}
        <label class="file-upload-inline">Подгрузить файл с правками<input type="file" data-upload-id="${row.id}" accept=".doc,.docx"></label>`;
}

function renderProcessed(target, rows, archived = false) {
    target.innerHTML = rows.map((row) => `
        <tr>
            <td>${esc(row.fioTeacher)}</td>
            <td>${esc(row.startDate || '')}</td>
            <td>${esc({PROCESSED:'Выпущена, ожидает кадров',RECEIVED_BY_HR:'Получена кадрами',EXECUTED:'Исполнена',ANNULLED:'Аннулирована',ARCHIVED:'Архив'}[row.status] || row.status)}</td>
            <td>${esc(row.createdBy)}</td>
            <td>${esc(String(row.createdAt || '').replace('T', ' ').slice(0, 16))}</td>
            <td>${actionButtons(row, archived)}</td>
        </tr>
    `).join('');

    target.querySelectorAll('[data-download-id]').forEach((btn) => {
        btn.addEventListener('click', async () => {
            try {
                await downloadMemo(btn.dataset.downloadId);
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    if (!archived) {
        target.querySelectorAll('[data-archive-id]').forEach((btn) => {
            btn.addEventListener('click', async () => {
                await api(`/api/service-memos/${btn.dataset.archiveId}/archive`, { method: 'POST' });
                await refreshProcessed();
                await refreshArchived();
            });
        });
        target.querySelectorAll('[data-upload-id]').forEach((input) => {
            input.addEventListener('change', async () => {
                if (!input.files?.length) return;
                const form = new FormData();
                form.append('file', input.files[0]);
                await api(`/api/service-memos/${input.dataset.uploadId}/upload`, { method: 'POST', body: form });
                await refreshProcessed();
            });
        });
    }
}

async function downloadMemo(id) {
    const path = window.withAcademicYear ? window.withAcademicYear(`/api/service-memos/${id}/download`) : `/api/service-memos/${id}/download`;
    const response = await fetch(path);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const cd = response.headers.get('Content-Disposition') || '';
    const headerFileName = response.headers.get('X-File-Name');
    const fallbackName = `служебка по нагрузке ${id}.docx`;
    const utf8Match = cd.match(/filename\\*=UTF-8''([^;]+)/i);
    const asciiMatch = cd.match(/filename=\"?([^\";]+)\"?/i);
    const fileNameRaw = headerFileName
        ? decodeURIComponent(headerFileName)
        : (utf8Match ? decodeURIComponent(utf8Match[1]) : (asciiMatch ? asciiMatch[1] : fallbackName));
    const fileName = fileNameRaw.toLowerCase().endsWith('.docx') ? fileNameRaw : `${fileNameRaw}.docx`;

    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

async function refreshPending() {
    const data = await api('/api/service-memos/pending');
    renderPending(data);
    return data;
}

async function refreshProcessed() {
    renderProcessed(ui.processedBody, await api('/api/service-memos/processed'), false);
}

async function refreshArchived() {
    renderProcessed(ui.archivedBody, await api('/api/service-memos/archived'), true);
}

async function generateMemos() {
    try {
        const fioTeachers = checkedTeachers();
        if (!fioTeachers.length) {
            print({ message: 'Выберите педагогов галочками перед формированием служебки.' });
            return;
        }

        ui.generateBtn.disabled = true;
        ui.generateBtn.textContent = 'Формируем...';

        const result = await api('/api/service-memos/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fioTeachers })
        });

        if (!Array.isArray(result) || result.length === 0) {
            print({ warning: 'Служебки не сформированы. Проверьте, что выбранные педагоги есть в списке «Не отработанные» и у них есть изменения нагрузки.' });
            return;
        }

        print(result);
        await refreshPending();
        await refreshProcessed();
        switchTab('processed');
    } catch (error) {
        print({ error: error.message });
    } finally {
        ui.generateBtn.disabled = false;
        ui.generateBtn.textContent = 'Сформировать служебку';
    }
}



async function loadMemoSettings() {
    const settings = await api('/api/service-memos/settings');
    if (ui.settingsTitle) ui.settingsTitle.value = settings?.directorTitle || '';
    if (ui.settingsName) ui.settingsName.value = settings?.directorName || '';
}

async function saveMemoSettings() {
    const payload = {
        directorTitle: ui.settingsTitle?.value || '',
        directorName: ui.settingsName?.value || ''
    };
    const saved = await api('/api/service-memos/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    print({ message: 'Настройки служебок сохранены', settings: saved });
}

function bindEvents() {
    ui.tabs.forEach((button) => button.addEventListener('click', () => switchTab(button.dataset.memoTab)));
    ui.refreshPendingBtn?.addEventListener('click', refreshPending);
    ui.selectAllPendingBtn?.addEventListener('click', () => setAllPending(true));
    ui.generateBtn?.addEventListener('click', generateMemos);
    ui.settingsSaveBtn?.addEventListener('click', async () => {
        try {
            await saveMemoSettings();
        } catch (error) {
            print({ error: error.message });
        }
    });
}

async function init() {
    bindEvents();
    try {
        await Promise.all([refreshPending(), refreshProcessed(), refreshArchived(), loadMemoSettings()]);
    } catch (error) {
        print({ error: error.message });
    }
}

init();
