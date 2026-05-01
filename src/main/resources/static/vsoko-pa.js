const paState = {
    specifications: [],
    summary: { primary: [], secondary: [] },
    subjectAreas: [],
    curriculum: [],
    importLogHistory: [],
    workflowVersionCache: {
        entry: new Map(),
        exit: new Map()
    },
    workflowUi: {
        entry: { search: '', page: 1, pageSize: 20 },
        exit: { search: '', page: 1, pageSize: 20 }
    },
    workflowRange: {
        entry: '5-11',
        exit: '5-11'
    }
};
const PA_SUMMARY_STATUS_OVERRIDES_KEY = 'pa.summary.status.overrides';
const paQueryParams = new URLSearchParams(window.location.search);
let summaryStatusSelection = null;
let summaryStatusOverrides = {};

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
    const specsPanel = document.getElementById('pa-tab-specs');
    const entryPanel = document.getElementById('pa-tab-entry');
    const exitPanel = document.getElementById('pa-tab-exit');
    if (specsPanel) specsPanel.classList.toggle('hidden', tab !== 'specs');
    if (entryPanel) entryPanel.classList.toggle('hidden', tab !== 'entry');
    if (exitPanel) exitPanel.classList.toggle('hidden', tab !== 'exit');
    if (tab === 'entry' || tab === 'exit') {
        renderWorkflow(tab).catch(() => {});
    }
    if (tab === 'exit' && !document.getElementById('pa-exit-folders-panel').classList.contains('hidden')) {
        loadReportFolders('exit').catch(() => {});
    }
}

function setSpecTab(tab) {
    document.querySelectorAll('#pa-spec-tabs [data-spec-tab]').forEach((btn) => btn.classList.toggle('active', btn.dataset.specTab === tab));
    const p511 = document.getElementById('pa-spec-summary-5-11-panel');
    const p14 = document.getElementById('pa-spec-summary-1-4-panel');
    const preg = document.getElementById('pa-spec-registry-panel');
    const plog = document.getElementById('pa-spec-upload-log-panel');
    if (p511) p511.classList.toggle('hidden', tab !== 'summary-5-11');
    if (p14) p14.classList.toggle('hidden', tab !== 'summary-1-4');
    if (preg) preg.classList.toggle('hidden', tab !== 'registry');
    if (plog) plog.classList.toggle('hidden', tab !== 'upload-log');
}

function setExitTab(tab) {
    document.querySelectorAll('#pa-exit-tabs [data-exit-tab]').forEach((btn) => btn.classList.toggle('active', btn.dataset.exitTab === tab));
    document.getElementById('pa-exit-summary-panel').classList.toggle('hidden', tab !== 'summary');
    document.getElementById('pa-exit-folders-panel').classList.toggle('hidden', tab !== 'folders');
    if (tab === 'summary') {
        renderWorkflow('exit').catch(() => {});
    } else if (tab === 'folders') {
        loadReportFolders('exit').catch(() => {});
    }
}

function statusIcon(cell) {
    if (!cell.participates) return '➖ Не участвует';
    return cell.hasSpecification ? '✅ Загружена' : '❌ Не загружена';
}

function workTypeRu(workType) {
    if (workType === 'ENTRY') return 'Входная';
    if (workType === 'EXIT') return 'Выходная';
    return 'Промежуточная';
}

function matrixCellSymbol(cell) {
    if (!cell) return '❌/❌';
    const entry = cell.entryParticipates === false ? '⚪' : (cell.entry ? '✅' : '❌');
    const exit = cell.exitParticipates === false ? '⚪' : (cell.exit ? '✅' : '❌');
    return `${entry}/${exit}`;
}

function parseParallel(scope) {
    const m = String(scope || '').match(/^(\d{1,2})/);
    return m ? Number(m[1]) : null;
}

function workflowRangeStorageKey(prefix) {
    return `pa.workflow.range.${prefix}`;
}

function getWorkflowRange(prefix) {
    return paState.workflowRange[prefix] || '5-11';
}

function setWorkflowRange(prefix, range, syncQuery = true) {
    const normalized = range === '1-4' ? '1-4' : '5-11';
    paState.workflowRange[prefix] = normalized;
    try {
        localStorage.setItem(workflowRangeStorageKey(prefix), normalized);
    } catch (_) {
        // ignore
    }
    const tabs = document.getElementById(`pa-${prefix}-range-tabs`);
    if (tabs) {
        tabs.querySelectorAll('[data-workflow-range]').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.workflowRange === normalized);
        });
    }
    if (syncQuery) {
        const url = new URL(window.location.href);
        url.searchParams.set(`${prefix}Range`, normalized);
        history.replaceState({}, '', `${url.pathname}${url.search}`);
    }
}

function initWorkflowRange(prefix) {
    const fromQuery = paQueryParams.get(`${prefix}Range`);
    let fromStorage = null;
    try {
        fromStorage = localStorage.getItem(workflowRangeStorageKey(prefix));
    } catch (_) {
        fromStorage = null;
    }
    setWorkflowRange(prefix, fromQuery || fromStorage || '5-11', false);
}

function normalizeScopeValue(value) {
    return String(value || '').trim().toUpperCase().replace(/\s+/g, '');
}

function renderSummaryRange(headId, bodyId, fromParallel, toParallel) {
    const head = document.getElementById(headId);
    const body = document.getElementById(bodyId);
    if (!head || !body) return;
    const curriculumRows = (paState.curriculum || []).filter((row) => {
        const p = parseParallel(row.className);
        return p !== null && p >= fromParallel && p <= toParallel && row.subjectName;
    });
    const subjects = [...new Set(curriculumRows.map((row) => row.subjectName))]
        .sort((a, b) => a.localeCompare(b, 'ru'));
    const baseParallels = [...new Set(curriculumRows.map((row) => String(parseParallel(row.className)).trim()))]
        .sort((a, b) => Number(a) - Number(b));
    const classScopes = [...new Set((paState.specifications || [])
        .filter((s) => s.scopeType === 'CLASS')
        .filter((s) => {
            const p = parseParallel(s.scopeValue);
            return p !== null && p >= fromParallel && p <= toParallel;
        })
        .map((s) => s.scopeValue)
    )].sort((a, b) => {
        const pa = parseParallel(a) || 0;
        const pb = parseParallel(b) || 0;
        if (pa !== pb) return pa - pb;
        return String(a).localeCompare(String(b), 'ru');
    });
    const columns = [];
    baseParallels.forEach((p) => {
        columns.push(p);
        classScopes.filter((scope) => String(parseParallel(scope)) === String(p)).forEach((scope) => columns.push(scope));
    });

    head.innerHTML = `<tr><th>Предметная область</th><th>Предмет</th>${columns.map((c) => `<th>${c}</th>`).join('')}</tr>`;
    if (!subjects.length || !columns.length) {
        body.innerHTML = '<tr><td colspan="12" class="muted">Нет данных</td></tr>';
        return;
    }

    const classesBySubjectScope = new Map();
    curriculumRows.forEach((row) => {
        const subject = row.subjectName;
        const className = String(row.className || '').trim();
        if (!subject || !className) return;
        const parallel = parseParallel(className);
        if (parallel !== null) {
            const parallelKey = `${subject}|${parallel}`;
            if (!classesBySubjectScope.has(parallelKey)) classesBySubjectScope.set(parallelKey, new Set());
            classesBySubjectScope.get(parallelKey).add(className);
        }
        const classKey = `${subject}|${normalizeScopeValue(className)}`;
        if (!classesBySubjectScope.has(classKey)) classesBySubjectScope.set(classKey, new Set());
        classesBySubjectScope.get(classKey).add(className);
    });

    const classesForCell = (subject, scope) => {
        const parallel = parseParallel(scope);
        const key = parallel !== null ? `${subject}|${parallel}` : `${subject}|${normalizeScopeValue(scope)}`;
        return [...(classesBySubjectScope.get(key) || new Set())].sort((a, b) => a.localeCompare(b, 'ru'));
    };

    const participationMap = new Map(
        [...(paState.summary.primary || []), ...(paState.summary.secondary || [])]
            .map((row) => [`${row.subjectName}|${row.scopeValue}|${row.level}`, row.participates !== false])
    );

    let html = '';
    const areas = [...new Set(subjects.map((s) => subjectAreaByName(s)))].sort((a, b) => a.localeCompare(b, 'ru'));
    areas.forEach((area) => {
        const areaSubjects = subjects.filter((s) => subjectAreaByName(s) === area);
        const areaRows = [];
        areaSubjects.forEach((subject) => {
            const hasAdvanced = (paState.specifications || []).some((s) =>
                s.subjectName === subject
                && s.level === 'ADVANCED'
                && (() => {
                    const p = parseParallel(s.scopeValue);
                    return p !== null && p >= fromParallel && p <= toParallel;
                })()
            );
            areaRows.push({ subjectName: subject, level: 'BASIC', title: subject });
            if (hasAdvanced) areaRows.push({ subjectName: subject, level: 'ADVANCED', title: `${subject} (угл)` });
        });
        areaRows.forEach((row, idx) => {
            html += '<tr>';
            if (idx === 0) html += `<td rowspan="${areaRows.length}">${area}</td>`;
            html += `<td>${row.title}</td>`;
            columns.forEach((scope) => {
                const taughtClasses = classesForCell(row.subjectName, scope);
                if (!taughtClasses.length) {
                    html += '<td></td>';
                    return;
                }
                const specs = (paState.specifications || []).filter((s) =>
                    s.subjectName === row.subjectName
                    && s.level === row.level
                    && normalizeScopeValue(s.scopeValue) === normalizeScopeValue(scope)
                    && s.activeVersion
                );
                const entry = specs.some((s) => s.workType === 'ENTRY');
                const exit = specs.some((s) => s.workType === 'EXIT');
                const scopeAsParallel = parseParallel(scope);
                const directParticipationKey = `${row.subjectName}|${scope}|${row.level}`;
                const parallelParticipationKey = `${row.subjectName}|${scopeAsParallel ?? ''}|${row.level}`;
                const participates = participationMap.has(directParticipationKey)
                    ? participationMap.get(directParticipationKey)
                    : (scopeAsParallel !== null && participationMap.has(parallelParticipationKey)
                        ? participationMap.get(parallelParticipationKey)
                        : true);
                const overrideKey = `${row.subjectName}|${scope}|${row.level}`;
                const override = summaryStatusOverrides[overrideKey];
                const entryParticipates = typeof override?.entryParticipates === 'boolean' ? override.entryParticipates : true;
                const exitParticipates = typeof override?.exitParticipates === 'boolean' ? override.exitParticipates : true;
                html += `<td><button type="button" class="tab-btn summary-status-btn ${participates ? '' : 'inactive'}" data-summary-toggle-subject="${row.subjectName.replace(/"/g, '&quot;')}" data-summary-toggle-scope="${scope}" data-summary-toggle-level="${row.level}" data-summary-toggle-entry-participates="${entryParticipates ? 'true' : 'false'}" data-summary-toggle-exit-participates="${exitParticipates ? 'true' : 'false'}" data-summary-toggle-classes="${taughtClasses.join(', ').replace(/"/g, '&quot;')}">${matrixCellSymbol({ entry, exit, entryParticipates, exitParticipates, participates })}</button></td>`;
            });
            html += '</tr>';
        });
    });
    body.innerHTML = html;
    bindSummaryToggles();
}

function renderCompactMatrix() {
    renderSummaryRange('pa-matrix-head', 'pa-matrix-body', 5, 11);
    renderSummaryRange('pa-matrix-head-primary', 'pa-matrix-body-primary', 1, 4);
    bindSummaryToggles();
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
    if (!body) return;
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
                <td>${workTypeRu(row.workType)}</td>
                <td><button type="button" class="tab-btn" data-spec-versions-key="${row.subjectName}|${row.scopeValue}|${row.level}|${row.workType}">v${row.versionNo || 1}</button></td>
                <td>${row.sourceFileName ? `<button type="button" class="tab-btn" data-download-spec-id="${row.id}">${row.sourceFileName}</button>` : ''}</td>
                <td>
                    <label><input type="checkbox" data-participation-subject="${row.subjectName}" data-participation-scope-type="${row.scopeType}" data-participation-scope="${row.scopeValue}" data-participation-level="${row.level}" ${participationMap.get(`${row.subjectName}|${row.scopeValue}|${row.level}`) === false ? '' : 'checked'}> Да</label>
                </td>
            </tr>
        `;
    }).join('') || '<tr><td colspan="8" class="muted">Спецификации не загружены</td></tr>';
    bindParticipationToggles();
    bindSpecificationVersions();
    bindSpecificationDownloadButtons();
    fillSelectors('entry');
    fillSelectors('exit');
    renderCompactMatrix();
    bindSummaryToggles();
}

function subjectAreaByName(subjectName) {
    return (paState.subjectAreas.find((row) => row.subjectName === subjectName)?.subjectAreaName || 'Без области');
}

function fillSelectors(prefix) {
    const subjectSelect = document.getElementById(`pa-${prefix}-subject`);
    const scopeSelect = document.getElementById(`pa-${prefix}-scope`);
    const classSelect = document.getElementById(`pa-${prefix}-class`);
    if (!subjectSelect || !scopeSelect || !classSelect) return;
    const previousSubject = subjectSelect.value;
    const previousScope = scopeSelect.value;
    const previousClass = classSelect.value;
    const type = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    const filtered = paState.specifications.filter((item) => item.workType === type);
    const subjects = [...new Set(filtered.map((item) => item.subjectName).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'ru'));
    subjectSelect.innerHTML = ['<option value="ALL">Все предметы</option>', ...subjects.map((s) => `<option value="${s}">${s}</option>`)].join('');
    if (previousSubject && [...subjects, 'ALL'].includes(previousSubject)) {
        subjectSelect.value = previousSubject;
    }
    const selectedSubject = subjectSelect.value || 'ALL';
    const scopedItems = selectedSubject === 'ALL'
        ? filtered
        : filtered.filter((item) => item.subjectName === selectedSubject);
    const scopes = [...new Set(scopedItems.map((item) => item.scopeValue).filter(Boolean))];
    scopeSelect.innerHTML = scopes.map((s) => `<option value="${s}">${s}</option>`).join('');
    if (previousScope && scopes.includes(previousScope)) {
        scopeSelect.value = previousScope;
    }
    fillClassSelector(prefix, selectedSubject, scopeSelect.value || scopes[0], previousClass);
}

function fillClassSelector(prefix, selectedSubject, selectedScope, preferredClass = null) {
    const classSelect = document.getElementById(`pa-${prefix}-class`);
    const subjectFilter = selectedSubject === 'ALL' ? null : selectedSubject;
    const classes = [...new Set((paState.curriculum || [])
        .filter((row) => !subjectFilter || row.subjectName === subjectFilter)
        .map((row) => row.className)
        .filter(Boolean)
        .filter((className) => !selectedScope || String(className).startsWith(String(selectedScope)))
    )].sort((a, b) => String(a).localeCompare(String(b), 'ru'));
    classSelect.innerHTML = classes.map((c) => `<option value="${c}">${c}</option>`).join('');
    if (preferredClass && classes.includes(preferredClass)) {
        classSelect.value = preferredClass;
    }
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
    if (!body) return;
    body.innerHTML = (rows || []).map((row) => `
        <tr>
            <td>${row.versionNo ?? ''}</td>
            <td>${row.status || ''}</td>
            <td>${row.activeVersion ? 'Да' : 'Нет'}</td>
            <td>${row.sourceFileName ? `<button type="button" class="tab-btn" data-download-report-id="${row.id}">${row.sourceFileName}</button>` : ''}</td>
            <td>${row.createdAt ? new Date(row.createdAt).toLocaleString('ru-RU') : ''}</td>
            <td>${row.validationMessage || ''}</td>
        </tr>
    `).join('') || '<tr><td colspan="6" class="muted">Версии не найдены</td></tr>';
    bindReportDownloadButtons();
}

async function generateForClass(prefix, force = false) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const className = document.getElementById(`pa-${prefix}-class`).value || document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || subject === 'ALL') {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите предмет для генерации', versionNo: null }]);
        return;
    }
    if (!className) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите класс для генерации', versionNo: null }]);
        return;
    }
    const params = new URLSearchParams({ subjectName: subject, className, level, workType });
    if (workDate) params.set('workDate', workDate);
    try {
        if (force) params.set('force', 'true');
        const result = await paApi(`/api/pa/reports/generate?${params.toString()}`, { method: 'POST' });
        renderUploadLog(prefix, [result]);
        await loadVersions(prefix);
        await renderWorkflow(prefix);
        await loadReportFolders(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: `Генерация не выполнена: ${e.message}`, versionNo: null }]);
    }
}

async function generateForParallel(prefix, force = false) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const parallel = document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || subject === 'ALL') {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите предмет для генерации по параллели', versionNo: null }]);
        return;
    }
    if (!parallel) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите параллель для генерации', versionNo: null }]);
        return;
    }
    const params = new URLSearchParams({ subjectName: subject, parallel, level, workType });
    if (workDate) params.set('workDate', workDate);
    try {
        if (force) params.set('force', 'true');
        const result = await paApi(`/api/pa/reports/generate/parallel?${params.toString()}`, { method: 'POST' });
        renderUploadLog(prefix, result);
        await renderWorkflow(prefix);
        await loadReportFolders(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: `Генерация параллели не выполнена: ${e.message}`, versionNo: null }]);
    }
}

async function generateForAll(prefix, force = false) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || subject === 'ALL') {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите предмет для массовой генерации', versionNo: null }]);
        return;
    }
    const params = new URLSearchParams({ subjectName: subject, level, workType });
    if (workDate) params.set('workDate', workDate);
    try {
        if (force) params.set('force', 'true');
        const result = await paApi(`/api/pa/reports/generate/all?${params.toString()}`, { method: 'POST' });
        renderUploadLog(prefix, result);
        await renderWorkflow(prefix);
        await loadReportFolders(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: `Массовая генерация не выполнена: ${e.message}`, versionNo: null }]);
    }
}



async function deleteScopeGenerated(prefix) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const className = document.getElementById(`pa-${prefix}-class`).value;
    const parallel = document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const byParallel = !className && !!parallel;
    const scopeValue = byParallel ? parallel : className;
    if (!scopeValue) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: 'Выберите класс или параллель для удаления', versionNo: null }]);
        return;
    }
    if (!confirm(`Удалить сгенерированные отчёты (${byParallel ? 'параллель' : 'класс'}: ${scopeValue})?`)) return;
    const params = new URLSearchParams({ subjectName: subject || 'ALL', scopeValue, byParallel: String(byParallel), level, workType: prefix === 'entry' ? 'ENTRY' : 'EXIT' });
    if (workDate) params.set('workDate', workDate);
    try {
        const result = await paApi(`/api/pa/reports/generated?${params.toString()}`, { method: 'DELETE' });
        renderUploadLog(prefix, [{ fileName: '', status: 'ACCEPTED', message: `Удалено шаблонов: ${result.deleted ?? 0}`, versionNo: null }]);
        await loadVersions(prefix);
        await loadReportFolders(prefix);
        await renderWorkflow(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: `Удаление не выполнено: ${e.message}`, versionNo: null }]);
    }
}
async function reloadSummaryAndSpecs() {
    const [summary, specs, subjects, curriculum] = await Promise.all([
        paApi('/api/pa/specifications/summary'),
        paApi('/api/pa/specifications'),
        paApi('/api/subjects'),
        paApi('/api/curriculum')
    ]);
    paState.summary = summary || { primary: [], secondary: [] };
    paState.subjectAreas = subjects || [];
    paState.curriculum = curriculum || [];
    paState.workflowVersionCache.entry.clear();
    paState.workflowVersionCache.exit.clear();
    if (!Array.isArray(specs) || specs.length === 0) {
        paState.importLogHistory = [];
        renderSpecificationImportLog();
    }
    renderSpecifications(specs || []);
    if (paQueryParams.get('forceExitAll') === '1') {
        const exitSubject = document.getElementById('pa-exit-subject');
        if (exitSubject) {
            exitSubject.value = 'ALL';
            exitSubject.disabled = true;
        }
    }
    const activeMain = document.querySelector('#pa-main-tabs [data-tab].active')?.dataset.tab;
    if (activeMain === 'entry' || activeMain === 'exit') {
        await renderWorkflow(activeMain);
    } else if (document.getElementById('pa-entry-workflow-head')) {
        fillSelectors('entry');
        await renderWorkflow('entry');
    } else if (document.getElementById('pa-exit-workflow-head')) {
        fillSelectors('exit');
        await renderWorkflow('exit');
    }
    if (activeMain === 'exit' && !document.getElementById('pa-exit-folders-panel').classList.contains('hidden')) {
        await loadReportFolders('exit');
    }
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

function bindSummaryToggles() {
    document.querySelectorAll('[data-summary-toggle-subject]').forEach((btn) => {
        if (btn.dataset.summaryToggleBound === '1') return;
        btn.dataset.summaryToggleBound = '1';
        btn.addEventListener('click', async () => {
            openSummaryStatusDialog(btn.dataset);
        });
    });
}

function openSummaryStatusDialog(data) {
    const dialog = document.getElementById('pa-summary-status-dialog');
    if (!dialog) return;
    const subjectName = data.summaryToggleSubject || '';
    const scopeValue = data.summaryToggleScope || '';
    const level = data.summaryToggleLevel || 'BASIC';
    const entryParticipates = data.summaryToggleEntryParticipates !== 'false';
    const exitParticipates = data.summaryToggleExitParticipates !== 'false';
    summaryStatusSelection = { subjectName, scopeValue, level, entryParticipates, exitParticipates };

    document.getElementById('pa-summary-status-title').textContent = subjectName;
    document.getElementById('pa-summary-status-scope').textContent = `Охват: ${scopeValue} · Уровень: ${level === 'ADVANCED' ? 'углублённый' : 'базовый'}`;
    document.getElementById('pa-summary-status-entry-check').checked = entryParticipates;
    document.getElementById('pa-summary-status-exit-check').checked = exitParticipates;
    document.getElementById('pa-summary-status-classes').textContent = data.summaryToggleClasses || '—';
    if (typeof dialog.showModal === 'function') dialog.showModal();
}

function saveSummaryStatusOverrides() {
    try {
        localStorage.setItem(PA_SUMMARY_STATUS_OVERRIDES_KEY, JSON.stringify(summaryStatusOverrides));
    } catch (_) {
        // ignore
    }
}

function loadSummaryStatusOverrides() {
    try {
        const raw = localStorage.getItem(PA_SUMMARY_STATUS_OVERRIDES_KEY);
        summaryStatusOverrides = raw ? JSON.parse(raw) : {};
    } catch (_) {
        summaryStatusOverrides = {};
    }
}

async function saveSummaryStatusFromDialog() {
    if (!summaryStatusSelection) return;
    const { subjectName, scopeValue, level } = summaryStatusSelection;
    const entryParticipates = document.getElementById('pa-summary-status-entry-check').checked;
    const exitParticipates = document.getElementById('pa-summary-status-exit-check').checked;
    summaryStatusOverrides[`${subjectName}|${scopeValue}|${level}`] = { entryParticipates, exitParticipates };
    saveSummaryStatusOverrides();
    try {
        document.getElementById('pa-summary-status-dialog').close();
        summaryStatusSelection = null;
        renderCompactMatrix();
    } catch (e) {
        alert(`Ошибка сохранения: ${e.message}`);
    }
}

function bindSpecificationVersions() {
    document.querySelectorAll('[data-spec-versions-key]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const key = btn.dataset.specVersionsKey;
            const versions = paState.specifications
                .filter((row) => `${row.subjectName}|${row.scopeValue}|${row.level}|${row.workType}` === key)
                .sort((a, b) => (b.versionNo || 0) - (a.versionNo || 0))
                .map((row) => `v${row.versionNo} — ${row.sourceFileName || 'без файла'}`);
            alert(versions.length ? versions.join('\n') : 'Версии не найдены');
        });
    });
}

function bindSpecificationDownloadButtons() {
    document.querySelectorAll('[data-download-spec-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const id = btn.dataset.downloadSpecId;
            const raw = `/api/pa/specifications/${id}/download`;
            const url = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(raw) : raw;
            window.open(url, '_blank');
        });
    });
}

async function uploadSpecifications() {
    const input = document.getElementById('pa-spec-files');
    if (!input.files.length) return;
    setSpecTab('upload-log');
    const form = new FormData();
    [...input.files].forEach((f) => form.append('files', f));
    try {
        const result = await paApi('/api/pa/specifications/import', { method: 'POST', body: form });
        appendSpecificationImportLog(result);
        input.value = '';
        await reloadSummaryAndSpecs();
    } catch (e) {
        appendSpecificationImportLog([{ fileName: [...input.files].map((f) => f.name).join(', '), warnings: [`Ошибка: ${e.message}`], importedTasks: 0 }]);
    }
}

function appendSpecificationImportLog(result) {
    const rows = Array.isArray(result) ? result : [result];
    const timestamp = new Date().toLocaleString('ru-RU');
    rows.forEach((row) => {
        const warnings = Array.isArray(row?.warnings) ? row.warnings.filter(Boolean) : [];
        const hasError = warnings.some((w) => String(w).toLowerCase().startsWith('ошибка'));
        const status = hasError ? 'Ошибка' : 'Успешно';
        const message = warnings.length ? warnings.join('; ') : 'Импорт выполнен';
        const records = Number.isFinite(row?.importedTasks) ? row.importedTasks : 0;
        paState.importLogHistory.unshift({
            timestamp,
            fileName: row?.fileName || '—',
            status,
            message,
            records
        });
    });
    paState.importLogHistory = paState.importLogHistory.slice(0, 200);
    renderSpecificationImportLog();
}

function renderSpecificationImportLog() {
    const body = document.getElementById('pa-spec-import-log-body');
    if (!body) return;
    body.innerHTML = paState.importLogHistory.map((row) => `
        <tr>
            <td>${row.timestamp}</td>
            <td>${row.fileName}</td>
            <td>${row.status}</td>
            <td>${row.message}</td>
            <td>${row.records}</td>
        </tr>
    `).join('') || '<tr><td colspan="5" class="muted">История загрузок пуста</td></tr>';
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
        await loadReportFolders(prefix);
    } catch (e) {
        renderUploadLog(prefix, [{ fileName: '', status: 'REJECTED', message: e.message, versionNo: null }]);
    }
}

async function loadReportFolders(prefix) {
    if (prefix !== 'exit') return;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    const rows = await paApi(`/api/pa/reports/folders?workType=${workType}`);
    renderReportFolders(prefix, rows || []);
}

function renderReportFolders(prefix, rows) {
    const container = document.getElementById(`pa-${prefix}-folders-tree`);
    if (!container) return;
    if (!rows.length) {
        container.innerHTML = '<p class="muted">Нет сгенерированных шаблонов</p>';
        return;
    }
    const bySubject = new Map();
    rows.forEach((row) => {
        const subject = row.subjectName || 'Без предмета';
        const parallel = row.parallel || '—';
        const className = row.className || '—';
        if (!bySubject.has(subject)) bySubject.set(subject, new Map());
        const byParallel = bySubject.get(subject);
        if (!byParallel.has(parallel)) byParallel.set(parallel, new Map());
        const byClass = byParallel.get(parallel);
        if (!byClass.has(className)) byClass.set(className, []);
        byClass.get(className).push(row);
    });
    let html = '';
    html += '<details open><summary><strong>Выходные работы</strong></summary>';
    [...bySubject.keys()].sort((a, b) => a.localeCompare(b, 'ru')).forEach((subject) => {
        html += `<details style="margin-left:16px;"><summary><strong>${subject}</strong></summary>`;
        const byParallel = bySubject.get(subject);
        [...byParallel.keys()].sort((a, b) => Number(a) - Number(b)).forEach((parallel) => {
            html += `<details style="margin-left:16px;"><summary>Параллель ${parallel}</summary>`;
            const byClass = byParallel.get(parallel);
            [...byClass.keys()].sort((a, b) => String(a).localeCompare(String(b), 'ru')).forEach((className) => {
                html += `<details style="margin-left:16px;"><summary>Класс ${className}</summary><ul>`;
                byClass.get(className)
                    .sort((a, b) => String(a.sourceFileName || '').localeCompare(String(b.sourceFileName || ''), 'ru'))
                    .forEach((item) => {
                        const created = item.createdAt ? new Date(item.createdAt).toLocaleString('ru-RU') : '';
                        html += `<li><button type="button" class="tab-btn" data-folder-download-id="${item.reportVersionId}">${item.sourceFileName || `${subject} — ${className}`}</button> <span class="muted">(${item.level === 'ADVANCED' ? 'углублённый' : 'базовый'}, ${created})</span></li>`;
                    });
                html += '</ul></details>';
            });
            html += '</details>';
        });
        html += '</details>';
    });
    html += '</details>';
    container.innerHTML = html;
    bindFolderDownloadButtons(container);
}

function bindFolderDownloadButtons(container) {
    container.querySelectorAll('[data-folder-download-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const id = btn.dataset.folderDownloadId;
            const raw = `/api/pa/reports/${id}/download`;
            const url = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(raw) : raw;
            window.open(url, '_blank');
        });
    });
}

async function loadVersions(prefix) {
    const subject = document.getElementById(`pa-${prefix}-subject`).value;
    const scopeValue = document.getElementById(`pa-${prefix}-scope`).value;
    const level = document.getElementById(`pa-${prefix}-level`).value;
    const workDate = document.getElementById(`pa-${prefix}-work-date`).value;
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    if (!subject || subject === 'ALL' || !scopeValue) {
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
    const subjectSelect = document.getElementById(`pa-${prefix}-subject`);
    const levelSelect = document.getElementById(`pa-${prefix}-level`);
    const subject = (prefix === 'entry' || prefix === 'exit') ? 'ALL' : (subjectSelect?.value || 'ALL');
    const level = levelSelect?.value || 'BASIC';
    const workType = prefix === 'entry' ? 'ENTRY' : 'EXIT';
    const head = document.getElementById(`pa-${prefix}-workflow-head`);
    const body = document.getElementById(`pa-${prefix}-workflow-body`);
    if (!head || !body) return;
    const specs = paState.specifications.filter((s) =>
        s.level === level
        && s.workType === workType
        && (subject === 'ALL' || s.subjectName === subject)
    );
    if (!specs.length) {
        head.innerHTML = '<tr><th>Предметная область</th><th>Предмет</th><th>Статус</th></tr>';
        body.innerHTML = '<tr><td colspan="3" class="muted">Нет данных</td></tr>';
        return;
    }
    const curriculumRows = (paState.curriculum || []).filter((row) =>
        row?.subjectName
        && row?.className
        && (subject === 'ALL' || row.subjectName === subject)
    );
    const classes = [...new Set(curriculumRows.map((r) => String(r.className).trim()).filter(Boolean))]
        .sort((a, b) => String(a).localeCompare(String(b), 'ru'));
    const range = getWorkflowRange(prefix);
    const filteredClasses = classes.filter((className) => {
        const p = parseParallel(className);
        if (p === null) return false;
        return range === '1-4' ? (p >= 1 && p <= 4) : (p >= 5 && p <= 11);
    });
    if (!filteredClasses.length) {
        head.innerHTML = '<tr><th>Предметная область</th><th>Предмет</th><th>Статус</th></tr>';
        body.innerHTML = '<tr><td colspan="3" class="muted">Нет данных для выбранного диапазона классов</td></tr>';
        return;
    }
    head.innerHTML = `<tr><th class="workflow-col-area">Предметная область</th><th class="workflow-col-subject">Предмет</th><th class="workflow-col-status">Статус</th>${filteredClasses.map((s) => `<th>${s}</th>`).join('')}</tr>`;

    const subjectClassMap = new Map();
    curriculumRows.forEach((row) => {
        const key = `${row.subjectName}|${row.className}`;
        if (!subjectClassMap.has(key)) {
            subjectClassMap.set(key, { subjectName: row.subjectName, scopeValue: String(row.className).trim() });
        }
    });

    const versionMap = new Map();
    const summaryRows = await paApi(`/api/pa/reports/workflow-summary?${new URLSearchParams({
        level,
        workType,
        ...(subject !== 'ALL' ? { subjectName: subject } : {})
    }).toString()}`);
    (summaryRows || []).forEach((row) => {
        versionMap.set(`${row.subjectName}|${normalizeScopeValue(row.scopeValue)}`, {
            hasGenerated: Boolean(row.hasGenerated),
            hasDownloaded: Boolean(row.hasDownloaded),
            hasUploaded: Boolean(row.hasUploaded),
            latestGeneratedId: row.latestGeneratedId,
            latestUploadedId: row.latestUploadedId
        });
    });

    const grouped = new Map();
    specs.forEach((spec) => {
        const area = subjectAreaByName(spec.subjectName);
        if (!grouped.has(area)) grouped.set(area, new Set());
        grouped.get(area).add(spec.subjectName);
    });
    const areas = [...grouped.keys()].sort((a, b) => a.localeCompare(b, 'ru'));

    const ui = paState.workflowUi[prefix];
    const searchInput = document.getElementById(`pa-${prefix}-workflow-filter`);
    const pageSizeSelect = document.getElementById(`pa-${prefix}-workflow-page-size`);
    if (searchInput) ui.search = String(searchInput.value || '').trim().toLowerCase();
    if (pageSizeSelect) ui.pageSize = Math.max(5, Number(pageSizeSelect.value) || 20);

    const subjectItems = [];
    areas.forEach((area) => {
        [...grouped.get(area)].sort((a, b) => a.localeCompare(b, 'ru')).forEach((subjectName) => {
            subjectItems.push({ area, subjectName });
        });
    });
    const filteredItems = ui.search
        ? subjectItems.filter((item) =>
            item.subjectName.toLowerCase().includes(ui.search)
            || item.area.toLowerCase().includes(ui.search))
        : subjectItems;
    const totalPages = Math.max(1, Math.ceil(filteredItems.length / ui.pageSize));
    ui.page = Math.min(Math.max(1, ui.page), totalPages);
    const pageStart = (ui.page - 1) * ui.pageSize;
    const pageItems = filteredItems.slice(pageStart, pageStart + ui.pageSize);
    const groupedPage = new Map();
    pageItems.forEach(({ area, subjectName }) => {
        if (!groupedPage.has(area)) groupedPage.set(area, []);
        groupedPage.get(area).push(subjectName);
    });

    let html = '';
    [...groupedPage.keys()].forEach((area) => {
        const subjects = groupedPage.get(area);
        subjects.forEach((subjectName, idx) => {
            const subjectRows = ['Спецификация', 'Сгенерирован', 'Скачан', 'Сдан'];
            subjectRows.forEach((rowName, rowIdx) => {
                html += '<tr>';
                if (idx === 0 && rowIdx === 0) {
                    html += `<td rowspan="${subjects.length * 4}" class="workflow-col-area">${area}</td>`;
                }
                if (rowIdx === 0) {
                    html += `<td rowspan="4" class="workflow-col-subject">${subjectName}</td>`;
                }
                html += `<td class="workflow-col-status">${rowName}</td>`;
                filteredClasses.forEach((scopeValue) => {
                    const classParallel = parseParallel(scopeValue);
                    const hasSpec = specs.some((s) => {
                        if (s.subjectName !== subjectName) return false;
                        if (s.scopeType === 'CLASS') return normalizeScopeValue(s.scopeValue) === normalizeScopeValue(scopeValue);
                        if (s.scopeType === 'PARALLEL' && classParallel !== null) return String(s.scopeValue).trim() === String(classParallel);
                        return false;
                    });
                    const state = versionMap.get(`${subjectName}|${normalizeScopeValue(scopeValue)}`) || {};
                    if (rowName === 'Спецификация') {
                        html += `<td>${hasSpec ? '✅' : ''}</td>`;
                    } else if (rowName === 'Сгенерирован') {
                        html += `<td>${state.hasGenerated ? `✅ ${state.latestGeneratedId ? `<button type="button" class="tab-btn" data-download-report-id="${state.latestGeneratedId}">⬇</button>` : ''}` : (hasSpec ? '⚠️' : '')}</td>`;
                    } else if (rowName === 'Скачан') {
                        html += `<td>${state.hasDownloaded ? '✅' : (hasSpec ? '⚠️' : '')}</td>`;
                    } else {
                        html += `<td>${state.hasUploaded ? `✅ ${state.latestUploadedId ? `<button type="button" class="tab-btn" data-download-report-id="${state.latestUploadedId}">⬇</button>` : ''}` : (hasSpec ? '⚠️' : '')}</td>`;
                    }
                });
                html += '</tr>';
            });
        });
    });
    body.innerHTML = html;
    const pageInfo = document.getElementById(`pa-${prefix}-workflow-page-info`);
    if (pageInfo) pageInfo.textContent = `Страница ${ui.page} из ${totalPages} · записей: ${filteredItems.length}`;
    const prevBtn = document.getElementById(`pa-${prefix}-workflow-page-prev`);
    const nextBtn = document.getElementById(`pa-${prefix}-workflow-page-next`);
    if (prevBtn) prevBtn.disabled = ui.page <= 1;
    if (nextBtn) nextBtn.disabled = ui.page >= totalPages;
    bindReportDownloadButtons();
}

function bindWorkflowControls(prefix) {
    const searchInput = document.getElementById(`pa-${prefix}-workflow-filter`);
    const pageSizeSelect = document.getElementById(`pa-${prefix}-workflow-page-size`);
    const prevBtn = document.getElementById(`pa-${prefix}-workflow-page-prev`);
    const nextBtn = document.getElementById(`pa-${prefix}-workflow-page-next`);
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            paState.workflowUi[prefix].page = 1;
            renderWorkflow(prefix).catch(() => {});
        });
    }
    if (pageSizeSelect) {
        pageSizeSelect.addEventListener('change', () => {
            paState.workflowUi[prefix].page = 1;
            renderWorkflow(prefix).catch(() => {});
        });
    }
    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            paState.workflowUi[prefix].page = Math.max(1, paState.workflowUi[prefix].page - 1);
            renderWorkflow(prefix).catch(() => {});
        });
    }
    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            paState.workflowUi[prefix].page += 1;
            renderWorkflow(prefix).catch(() => {});
        });
    }
}

function bindWorkflowRangeTabs(prefix) {
    const tabs = document.getElementById(`pa-${prefix}-range-tabs`);
    if (!tabs) return;
    tabs.addEventListener('click', (event) => {
        const btn = event.target.closest('[data-workflow-range]');
        if (!btn) return;
        event.preventDefault();
        setWorkflowRange(prefix, btn.dataset.workflowRange);
        paState.workflowUi[prefix].page = 1;
        renderWorkflow(prefix).catch(() => {});
    });
}

function bindReportDownloadButtons() {
    document.querySelectorAll('[data-download-report-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const id = btn.dataset.downloadReportId;
            const raw = `/api/pa/reports/${id}/download`;
            const url = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(raw) : raw;
            window.open(url, '_blank');
        });
    });
}

function applySharedPaPageNav() {
    const search = window.location.search || '';
    document.querySelectorAll('.page-nav .nav-link, #pa-hub-grid a[href^="/vsoko-pa-"]').forEach((link) => {
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#')) return;
        const url = new URL(href, window.location.origin);
        url.search = search;
        link.setAttribute('href', `${url.pathname}${url.search}`);
    });
}

document.querySelectorAll('#pa-main-tabs [data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => setPaTab(btn.dataset.tab));
});
const specTabs = document.getElementById('pa-spec-tabs');
if (specTabs) {
    specTabs.addEventListener('click', (event) => {
        const btn = event.target.closest('[data-spec-tab]');
        if (!btn) return;
        event.preventDefault();
        setSpecTab(btn.dataset.specTab);
    });
}
const exitTabs = document.getElementById('pa-exit-tabs');
if (exitTabs) {
    exitTabs.addEventListener('click', (event) => {
        const btn = event.target.closest('[data-exit-tab]');
        if (!btn) return;
        event.preventDefault();
        setExitTab(btn.dataset.exitTab);
    });
}
const bindClick = (id, handler) => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('click', handler);
};
const bindChange = (id, handler) => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('change', handler);
};
bindClick('pa-spec-import-btn', uploadSpecifications);
bindClick('pa-spec-reload-btn', reloadSummaryAndSpecs);
bindClick('pa-entry-upload-btn', () => uploadReports('entry'));
bindClick('pa-exit-upload-btn', () => uploadReports('exit'));
bindClick('pa-entry-load-versions-btn', () => loadVersions('entry'));
bindClick('pa-exit-load-versions-btn', () => loadVersions('exit'));
bindClick('pa-entry-generate-btn', () => generateForClass('entry'));
bindClick('pa-exit-generate-btn', () => generateForClass('exit'));
bindClick('pa-exit-regenerate-all-btn', () => generateForAll('exit', true));
bindClick('pa-exit-delete-scope-btn', () => deleteScopeGenerated('exit'));
bindClick('pa-entry-generate-parallel-btn', () => generateForParallel('entry'));
bindClick('pa-exit-generate-parallel-btn', () => generateForParallel('exit'));
bindClick('pa-entry-generate-all-btn', () => generateForAll('entry'));
bindClick('pa-exit-generate-all-btn', () => generateForAll('exit'));
bindChange('pa-entry-subject', async () => { paState.workflowUi.entry.page = 1; fillSelectors('entry'); await renderWorkflow('entry'); });
bindChange('pa-exit-subject', async () => { paState.workflowUi.exit.page = 1; fillSelectors('exit'); await renderWorkflow('exit'); });
bindChange('pa-entry-level', async () => { paState.workflowUi.entry.page = 1; fillSelectors('entry'); await renderWorkflow('entry'); });
bindChange('pa-exit-level', async () => { paState.workflowUi.exit.page = 1; fillSelectors('exit'); await renderWorkflow('exit'); });
bindChange('pa-entry-scope', () => fillClassSelector('entry', document.getElementById('pa-entry-subject')?.value, document.getElementById('pa-entry-scope')?.value));
bindChange('pa-exit-scope', () => fillClassSelector('exit', document.getElementById('pa-exit-subject')?.value, document.getElementById('pa-exit-scope')?.value));
bindClick('pa-summary-status-close-btn', () => {
    const dialog = document.getElementById('pa-summary-status-dialog');
    if (dialog) dialog.close();
    summaryStatusSelection = null;
});
bindClick('pa-summary-status-save-btn', () => {
    saveSummaryStatusFromDialog().catch((e) => alert(`Ошибка: ${e.message}`));
});
bindWorkflowControls('entry');
bindWorkflowControls('exit');
initWorkflowRange('entry');
initWorkflowRange('exit');
bindWorkflowRangeTabs('entry');
bindWorkflowRangeTabs('exit');
applySharedPaPageNav();

loadSummaryStatusOverrides();
reloadSummaryAndSpecs().catch((e) => {
    appendSpecificationImportLog([{ fileName: '—', warnings: [`Ошибка: ${e.message}`], importedTasks: 0 }]);
});
renderSpecificationImportLog();
const startMainTab = paQueryParams.get('tab')
    || (window.location.pathname.includes('vsoko-pa-entry') ? 'entry'
        : window.location.pathname.includes('vsoko-pa-exit') ? 'exit'
            : 'specs');
const startSpecTab = paQueryParams.get('specTab') || 'summary-5-11';
const startExitTab = paQueryParams.get('exitTab')
    || (window.location.pathname.includes('vsoko-pa-folders') ? 'folders' : 'summary');
setPaTab(startMainTab);
setSpecTab(startSpecTab);
setExitTab(startExitTab);
