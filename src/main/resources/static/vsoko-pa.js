const paState = {
    specifications: [],
    summary: { primary: [], secondary: [] },
    subjectAreas: []
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

function setSpecTab(tab) {
    document.querySelectorAll('#pa-spec-tabs [data-spec-tab]').forEach((btn) => btn.classList.toggle('active', btn.dataset.specTab === tab));
    document.getElementById('pa-spec-summary-panel').classList.toggle('hidden', tab !== 'summary');
    document.getElementById('pa-spec-registry-panel').classList.toggle('hidden', tab !== 'registry');
}

function statusIcon(cell) {
    if (!cell.participates) return '➖ Не участвует';
    return cell.hasSpecification ? '✅ Загружена' : '❌ Не загружена';
}

function renderCompactMatrix() {
    const all = [...(paState.summary.primary || []), ...(paState.summary.secondary || [])];
    const preferredScopes = ['5', '6', '7', '8', '9', '9Б', '10', '11'];
    const presentScopes = [...new Set(all.map((row) => row.scopeValue).filter(Boolean))];
    const orderedPreferred = preferredScopes.filter((scope) => presentScopes.includes(scope));
    const otherScopes = presentScopes
        .filter((scope) => !preferredScopes.includes(scope))
        .sort((a, b) => String(a).localeCompare(String(b), 'ru'));
    const scopes = [...orderedPreferred, ...otherScopes];
    const head = document.getElementById('pa-matrix-head');
    const body = document.getElementById('pa-matrix-body');
    head.innerHTML = `<tr><th>Предметная область</th><th>Предмет</th>${scopes.map((s) => `<th>${s}</th>`).join('')}</tr>`;

    const areaMap = new Map();
    [...paState.specifications, ...all].forEach((row) => {
        const area = subjectAreaByName(row.subjectName);
        if (!areaMap.has(area)) areaMap.set(area, new Map());
        if (!areaMap.get(area).has(row.subjectName)) areaMap.get(area).set(row.subjectName, true);
    });
    const areas = [...areaMap.keys()].sort((a, b) => a.localeCompare(b, 'ru'));
    if (!areas.length) {
        body.innerHTML = '<tr><td colspan="12" class="muted">Нет данных</td></tr>';
        return;
    }

    let html = '';
    areas.forEach((area) => {
        const subjects = [...areaMap.get(area).keys()].sort((a, b) => a.localeCompare(b, 'ru'));
        subjects.forEach((subject, idx) => {
            html += '<tr>';
            if (idx === 0) html += `<td rowspan="${subjects.length}">${area}</td>`;
            html += `<td>${subject}</td>`;
            scopes.forEach((scope) => {
                const cell = all.find((row) => row.subjectName === subject && row.scopeValue === scope);
                html += `<td>${cell ? statusIcon(cell).split(' ')[0] : '❌'}</td>`;
            });
            html += '</tr>';
        });
    });
    body.innerHTML = html;
}

function renderSpecifications(rows) {
    paState.specifications = (rows || []).slice().sort((a, b) => {
        const areaCmp = subjectAreaByName(a.subjectName).localeCompare(subjectAreaByName(b.subjectName), 'ru');
        if (areaCmp !== 0) return areaCmp;
        const subjectCmp = (a.subjectName || '').localeCompare(b.subjectName || '', 'ru');
        if (subjectCmp !== 0) return subjectCmp;
        const scopeCmp = (a.scopeValue || '').localeCompare(b.scopeValue || '', 'ru');
        if (scopeCmp !== 0) return scopeCmp;
        return (a.level || '').localeCompare(b.level || '', 'ru');
    });
    const participationMap = new Map(
        [...(paState.summary.primary || []), ...(paState.summary.secondary || [])]
            .map((row) => [`${row.subjectName}|${row.scopeValue}|${row.level}`, Boolean(row.participates)])
    );
    const body = document.getElementById('pa-specifications-body');
    let currentArea = null;
    body.innerHTML = paState.specifications.map((row) => {
        const area = subjectAreaByName(row.subjectName);
        const areaRow = area !== currentArea
            ? `<tr><td colspan="8"><strong>${area}</strong></td></tr>`
            : '';
        currentArea = area;
        return `
            ${areaRow}
            <tr>
                <td>${area}</td>
                <td>${row.subjectName || ''}</td>
                <td>${row.scopeType || ''} ${row.scopeValue || ''}</td>
                <td>${row.level === 'ADVANCED' ? 'Углублённый' : 'Базовый'}</td>
                <td>${row.workType || ''}</td>
                <td>${row.versionNo || ''}</td>
                <td>${row.sourceFileName || ''}</td>
                <td>
                    <label><input type="checkbox" data-participation-subject="${row.subjectName}" data-participation-scope-type="${row.scopeType}" data-participation-scope="${row.scopeValue}" data-participation-level="${row.level}" ${participationMap.get(`${row.subjectName}|${row.scopeValue}|${row.level}`) === false ? '' : 'checked'}> Да</label>
                </td>
            </tr>
        `;
    }).join('') || '<tr><td colspan="8" class="muted">Спецификации не загружены</td></tr>';
    bindParticipationToggles();
    fillSelectors('entry');
    fillSelectors('exit');
    renderCompactMatrix();
}

function subjectAreaByName(subjectName) {
    return (paState.subjectAreas.find((row) => row.subjectName === subjectName)?.subjectAreaName || 'Без области');
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

async function generateForClass(prefix) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const className = document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || !className) return;
    const params = new URLSearchParams({ subjectName: subject, className, level, workType });
    if (workDate) params.set('workDate', workDate);
    const result = await paApi(`/api/pa/reports/generate?${params.toString()}`, { method: 'POST' });
    renderUploadLog(prefix, [result]);
    await loadVersions(prefix);
    await renderWorkflow(prefix);
}

async function reloadSummaryAndSpecs() {
    const [summary, specs, subjects] = await Promise.all([
        paApi('/api/pa/specifications/summary'),
        paApi('/api/pa/specifications'),
        paApi('/api/subjects')
    ]);
    paState.summary = summary || { primary: [], secondary: [] };
    paState.subjectAreas = subjects || [];
    renderSpecifications(specs || []);
    await renderWorkflow('entry');
    await renderWorkflow('exit');
}

function bindParticipationToggles() {
    document.querySelectorAll('[data-participation-subject]').forEach((checkbox) => {
        checkbox.addEventListener('change', async () => {
            try {
                await paApi('/api/pa/participation', {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        subjectName: checkbox.dataset.participationSubject,
                        scopeType: checkbox.dataset.participationScopeType,
                        scopeValue: checkbox.dataset.participationScope,
                        level: checkbox.dataset.participationLevel,
                        participates: checkbox.checked
                    })
                });
                await reloadSummaryAndSpecs();
            } catch (e) {
                checkbox.checked = !checkbox.checked;
                alert(`Ошибка обновления статуса участия: ${e.message}`);
            }
        });
    });
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
    await renderWorkflow(prefix, rows);
}

async function renderWorkflow(prefix, loadedVersions = null) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    const body = document.getElementById(`pa-${prefix}-workflow-body`);
    if (!subject) {
        body.innerHTML = '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
        return;
    }
    const specs = paState.specifications.filter((s) => s.subjectName === subject && s.level === level && s.workType === workType);
    const scopes = [...new Set(specs.map((s) => s.scopeValue))];
    const rows = [];
    for (const scopeValue of scopes) {
        const versions = loadedVersions && document.getElementById(`pa-${prefix}-scope`).value === scopeValue
            ? loadedVersions
            : await paApi(`/api/pa/reports/versions?${new URLSearchParams({
                subjectName: subject,
                scopeType: /^\d+$/.test(scopeValue) ? 'PARALLEL' : 'CLASS',
                scopeValue,
                level,
                workType
            }).toString()}`);
        const hasGenerated = (versions || []).some((v) => v.status === 'GENERATED');
        const hasUploaded = (versions || []).some((v) => v.status === 'ACCEPTED' && v.uploadedBackSuccess);
        rows.push({ scopeValue, hasSpec: true, hasGenerated, hasUploaded });
    }
    body.innerHTML = rows.map((row) => `
        <tr>
            <td>${row.scopeValue}</td>
            <td>${row.hasSpec ? '✅' : '❌'}</td>
            <td>${row.hasGenerated ? '✅' : '⚠️'}</td>
            <td>${row.hasUploaded ? '✅' : '⚠️'}</td>
        </tr>
    `).join('') || '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
}

document.querySelectorAll('#pa-main-tabs [data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => setPaTab(btn.dataset.tab));
});
document.querySelectorAll('#pa-spec-tabs [data-spec-tab]').forEach((btn) => {
    btn.addEventListener('click', () => setSpecTab(btn.dataset.specTab));
});
document.getElementById('pa-spec-import-btn').addEventListener('click', uploadSpecifications);
document.getElementById('pa-spec-reload-btn').addEventListener('click', reloadSummaryAndSpecs);
document.getElementById('pa-entry-upload-btn').addEventListener('click', () => uploadReports('entry'));
document.getElementById('pa-exit-upload-btn').addEventListener('click', () => uploadReports('exit'));
document.getElementById('pa-entry-load-versions-btn').addEventListener('click', () => loadVersions('entry'));
document.getElementById('pa-exit-load-versions-btn').addEventListener('click', () => loadVersions('exit'));
document.getElementById('pa-entry-generate-btn').addEventListener('click', () => generateForClass('entry'));
document.getElementById('pa-exit-generate-btn').addEventListener('click', () => generateForClass('exit'));
document.getElementById('pa-entry-subject').addEventListener('change', async () => { fillSelectors('entry'); await renderWorkflow('entry'); });
document.getElementById('pa-exit-subject').addEventListener('change', async () => { fillSelectors('exit'); await renderWorkflow('exit'); });

reloadSummaryAndSpecs().catch((e) => {
    document.getElementById('pa-spec-import-log').textContent = JSON.stringify({ error: e.message }, null, 2);
});
