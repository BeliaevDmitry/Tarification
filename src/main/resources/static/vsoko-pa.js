const paState = {
    specifications: []
};

function paApi(path, options = {}) {
    const scoped = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(path) : path;
    return fetch(scoped, options).then(async (response) => {
        const text = await response.text();
        let body = null;
        try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
        if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
        return body;
    });
}

function setPaTab(tab) {
    document.querySelectorAll('#pa-main-tabs [data-tab]').forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === tab));
    document.getElementById('pa-tab-specs').classList.toggle('hidden', tab !== 'specs');
    document.getElementById('pa-tab-entry').classList.toggle('hidden', tab !== 'entry');
    document.getElementById('pa-tab-exit').classList.toggle('hidden', tab !== 'exit');
}

function statusIcon(cell) {
    if (!cell.participates) return '➖ Не участвует';
    return cell.hasSpecification ? '✅ Загружена' : '❌ Не загружена';
}

function renderSummary(bodyId, rows) {
    const body = document.getElementById(bodyId);
    body.innerHTML = (rows || []).map((row) => `
        <tr>
            <td>${row.subjectName || ''}</td>
            <td>${row.scopeValue || ''}</td>
            <td>${row.level === 'ADVANCED' ? 'Углублённый' : 'Базовый'}</td>
            <td>${statusIcon(row)}</td>
        </tr>
    `).join('') || '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
}

function renderSpecifications(rows) {
    paState.specifications = rows || [];
    const body = document.getElementById('pa-specifications-body');
    body.innerHTML = paState.specifications.map((row) => `
        <tr>
            <td>${row.subjectName || ''}</td>
            <td>${row.scopeType || ''} ${row.scopeValue || ''}</td>
            <td>${row.level === 'ADVANCED' ? 'Углублённый' : 'Базовый'}</td>
            <td>${row.workType || ''}</td>
            <td>${row.versionNo || ''}</td>
            <td>${row.sourceFileName || ''}</td>
        </tr>
    `).join('') || '<tr><td colspan="6" class="muted">Спецификации не загружены</td></tr>';
    fillSelectors('entry');
    fillSelectors('exit');
}

function fillSelectors(prefix) {
    const subjectSelect = document.getElementById(`pa-${prefix}-subject`);
    const scopeSelect = document.getElementById(`pa-${prefix}-scope`);
    const type = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    const filtered = paState.specifications.filter((item) => item.workType === type);
    const subjects = [...new Set(filtered.map((item) => item.subjectName).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'ru'));
    subjectSelect.innerHTML = subjects.map((s) => `<option value="${s}">${s}</option>`).join('');
    const selectedSubject = subjectSelect.value || subjects[0];
    const scopes = [...new Set(filtered.filter((item) => item.subjectName === selectedSubject).map((item) => item.scopeValue).filter(Boolean))];
    scopeSelect.innerHTML = scopes.map((s) => `<option value="${s}">${s}</option>`).join('');
}

function renderUploadLog(prefix, rows) {
    const body = document.getElementById(`pa-${prefix}-upload-log-body`);
    body.innerHTML = (rows || []).map((row) => `
        <tr>
            <td>${row.fileName || ''}</td>
            <td>${row.status || ''}</td>
            <td>${row.message || ''}</td>
            <td>${row.versionNo ?? '—'}</td>
        </tr>
    `).join('') || '<tr><td colspan="4" class="muted">Нет операций</td></tr>';
}

function renderVersions(prefix, rows) {
    const body = document.getElementById(`pa-${prefix}-versions-body`);
    body.innerHTML = (rows || []).map((row) => `
        <tr>
            <td>${row.versionNo ?? ''}</td>
            <td>${row.status || ''}</td>
            <td>${row.activeVersion ? 'Да' : 'Нет'}</td>
            <td>${row.sourceFileName || ''}</td>
            <td>${row.createdAt ? new Date(row.createdAt).toLocaleString('ru-RU') : ''}</td>
            <td>${row.validationMessage || ''}</td>
        </tr>
    `).join('') || '<tr><td colspan="6" class="muted">Версии не найдены</td></tr>';
}

async function reloadSummaryAndSpecs() {
    const [summary, specs] = await Promise.all([
        paApi('/api/pa/specifications/summary'),
        paApi('/api/pa/specifications')
    ]);
    renderSummary('pa-summary-primary-body', summary?.primary || []);
    renderSummary('pa-summary-secondary-body', summary?.secondary || []);
    renderSpecifications(specs || []);
}

async function uploadSpecifications() {
    const input = document.getElementById('pa-spec-files');
    const log = document.getElementById('pa-spec-import-log');
    if (!input.files.length) return;
    const form = new FormData();
    [...input.files].forEach((f) => form.append('files', f));
    try {
        const result = await paApi('/api/pa/specifications/import', { method: 'POST', body: form });
        log.textContent = JSON.stringify(result, null, 2);
        input.value = '';
        await reloadSummaryAndSpecs();
    } catch (e) {
        log.textContent = JSON.stringify({ error: e.message }, null, 2);
    }
}

async function uploadReports(prefix) {
    const input = document.getElementById(`pa-${prefix}-report-files`);
    if (!input.files.length) return;
    const form = new FormData();
    [...input.files].forEach((f) => form.append('files', f));
    try {
        const rows = await paApi('/api/pa/reports/upload', { method: 'POST', body: form });
        renderUploadLog(prefix, rows);
        input.value = '';
        await loadVersions(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: e.message, versionNo: null }]);
    }
}

async function loadVersions(prefix) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const scopeValue = document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || !scopeValue) {
        renderVersions(prefix, []);
        return;
    }
    const params = new URLSearchParams({
        subjectName: subject,
        scopeType: /^\d+$/.test(scopeValue) ? 'PARALLEL' : 'CLASS',
        scopeValue,
        level,
        workType
    });
    if (workDate) params.set('workDate', workDate);
    const rows = await paApi(`/api/pa/reports/versions?${params.toString()}`);
    renderVersions(prefix, rows);
}

document.querySelectorAll('#pa-main-tabs [data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => setPaTab(btn.dataset.tab));
});
document.getElementById('pa-spec-import-btn').addEventListener('click', uploadSpecifications);
document.getElementById('pa-spec-reload-btn').addEventListener('click', reloadSummaryAndSpecs);
document.getElementById('pa-entry-upload-btn').addEventListener('click', () => uploadReports('entry'));
document.getElementById('pa-exit-upload-btn').addEventListener('click', () => uploadReports('exit'));
document.getElementById('pa-entry-load-versions-btn').addEventListener('click', () => loadVersions('entry'));
document.getElementById('pa-exit-load-versions-btn').addEventListener('click', () => loadVersions('exit'));
document.getElementById('pa-entry-subject').addEventListener('change', () => fillSelectors('entry'));
document.getElementById('pa-exit-subject').addEventListener('change', () => fillSelectors('exit'));

reloadSummaryAndSpecs().catch((e) => {
    document.getElementById('pa-spec-import-log').textContent = JSON.stringify({ error: e.message }, null, 2);
});
