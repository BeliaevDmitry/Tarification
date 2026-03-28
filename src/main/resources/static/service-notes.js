const ui = {
    tabs: document.querySelectorAll('[data-memo-tab]'),
    panels: document.querySelectorAll('[data-memo-panel]'),
    pendingBody: document.getElementById('pending-body'),
    processedBody: document.getElementById('processed-body'),
    archivedBody: document.getElementById('archived-body'),
    refreshPendingBtn: document.getElementById('refresh-pending-btn'),
    selectAllPendingBtn: document.getElementById('select-all-pending-btn'),
    generateBtn: document.getElementById('generate-memos-btn'),
    result: document.getElementById('memo-result')
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
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
        .map((el) => decodeURIComponent(el.dataset.pendingCheck));
}

function setAllPending(checked) {
    document.querySelectorAll('[data-pending-check]').forEach((checkbox) => {
        checkbox.checked = checked;
    });
}

function renderPending(rows) {
    ui.pendingBody.innerHTML = rows.map((row) => `
        <tr>
            <td><input type="checkbox" data-pending-check="${encodeURIComponent(row.fioTeacher)}"></td>
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
    return `${download}
        <button type="button" data-archive-id="${row.id}">Отправить в архив</button>
        <label class="file-upload-inline">Подгрузить файл с правками<input type="file" data-upload-id="${row.id}" accept=".doc,.docx"></label>`;
}

function renderProcessed(target, rows, archived = false) {
    target.innerHTML = rows.map((row) => `
        <tr>
            <td>${esc(row.fioTeacher)}</td>
            <td>${esc(row.startDate || '')}</td>
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
    const response = await fetch(`/api/service-memos/${id}/download`);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const cd = response.headers.get('Content-Disposition') || '';
    const fallbackName = `service-memo-${id}.docx`;
    const utf8Match = cd.match(/filename\\*=UTF-8''([^;]+)/i);
    const asciiMatch = cd.match(/filename=\"?([^\";]+)\"?/i);
    const fileNameRaw = utf8Match ? decodeURIComponent(utf8Match[1]) : (asciiMatch ? asciiMatch[1] : fallbackName);
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
    const fioTeachers = checkedTeachers();
    if (!fioTeachers.length) {
        print({ message: 'Выберите педагогов галочками перед формированием служебки.' });
        return;
    }
    const result = await api('/api/service-memos/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fioTeachers })
    });
    print(result);
    await refreshPending();
    await refreshProcessed();
    switchTab('processed');
}

function bindEvents() {
    ui.tabs.forEach((button) => button.addEventListener('click', () => switchTab(button.dataset.memoTab)));
    ui.refreshPendingBtn?.addEventListener('click', refreshPending);
    ui.selectAllPendingBtn?.addEventListener('click', () => setAllPending(true));
    ui.generateBtn?.addEventListener('click', generateMemos);
}

async function init() {
    bindEvents();
    try {
        await Promise.all([refreshPending(), refreshProcessed(), refreshArchived()]);
    } catch (error) {
        print({ error: error.message });
    }
}

init();
