const ui = {
    tabs: Array.from(document.querySelectorAll('[data-contingent-tab]')),
    panes: Array.from(document.querySelectorAll('[data-contingent-pane]')),
    fileInput: document.getElementById('contingent-file'),
    importBtn: document.getElementById('contingent-import-btn'),
    importResult: document.getElementById('contingent-import-result'),
    problemsBody: document.getElementById('contingent-problems-body'),
    snapshotDateSelect: document.getElementById('contingent-snapshot-date'),
    statsRefreshBtn: document.getElementById('contingent-stats-refresh-btn'),
    statsExportBtn: document.getElementById('contingent-stats-export-btn'),
    statsViewMode: document.getElementById('contingent-stats-view-mode'),
    statsTable: document.getElementById('contingent-stats-table'),
    statsSummary: document.getElementById('contingent-stats-summary'),
    manualSourceSelect: document.getElementById('contingent-class-size-source'),
    manualSourceSaveBtn: document.getElementById('contingent-class-size-source-save-btn'),
    manualFileInput: document.getElementById('contingent-manual-file'),
    manualImportBtn: document.getElementById('contingent-manual-import-btn'),
    manualExportBtn: document.getElementById('contingent-manual-export-btn'),
    manualSaveBtn: document.getElementById('contingent-manual-save-btn'),
    manualRefreshBtn: document.getElementById('contingent-manual-refresh-btn'),
    manualSummary: document.getElementById('contingent-manual-summary'),
    manualTable: document.getElementById('contingent-manual-table'),
    supportAsOfDate: document.getElementById('support-as-of-date'),
    supportRefreshBtn: document.getElementById('support-refresh-btn'),
    supportExportBtn: document.getElementById('support-export-btn'),
    supportReconcileBtn: document.getElementById('support-reconcile-btn'),
    supportSummaryMessage: document.getElementById('support-summary-message'),
    supportDataPackageExportBtn: document.getElementById('support-data-package-export-btn'),
    supportDataPackageFile: document.getElementById('support-data-package-file'),
    supportDataPackageImportBtn: document.getElementById('support-data-package-import-btn'),
    supportDataPackageResult: document.getElementById('support-data-package-result'),
    supportReadinessBtn: document.getElementById('support-readiness-btn'),
    supportReadinessTable: document.getElementById('support-readiness-table'),
    supportDocumentSection: document.getElementById('support-document-section'),
    supportDocumentTable: document.getElementById('support-document-table'),
    supportDocumentMessage: document.getElementById('support-document-message'),
    supportDocumentId: document.getElementById('support-document-id'),
    supportDocumentStudent: document.getElementById('support-document-student'),
    supportDocumentType: document.getElementById('support-document-type'),
    supportDocumentForm: document.getElementById('support-document-form'),
    supportDocumentNumber: document.getElementById('support-document-number'),
    supportDocumentIssueDate: document.getElementById('support-document-issue-date'),
    supportDocumentValidFrom: document.getElementById('support-document-valid-from'),
    supportDocumentValidTo: document.getElementById('support-document-valid-to'),
    supportDocumentReceivedAt: document.getElementById('support-document-received-at'),
    supportDocumentOrganization: document.getElementById('support-document-organization'),
    supportDocumentResponsible: document.getElementById('support-document-responsible'),
    supportDocumentComment: document.getElementById('support-document-comment'),
    supportDocumentFile: document.getElementById('support-document-file'),
    supportDocumentSaveBtn: document.getElementById('support-document-save-btn'),
    supportDocumentClearBtn: document.getElementById('support-document-clear-btn'),
    supportClassTable: document.getElementById('support-class-table'),
    supportRegisterTable: document.getElementById('support-register-table'),
    supportStatusEditor: document.getElementById('support-status-editor'),
    supportStatusId: document.getElementById('support-status-id'),
    supportStatusStudent: document.getElementById('support-status-student'),
    supportStatusCategory: document.getElementById('support-status-category'),
    supportStatusFrom: document.getElementById('support-status-from'),
    supportStatusTo: document.getElementById('support-status-to'),
    supportStatusSaveBtn: document.getElementById('support-status-save-btn'),
    supportStatusClearBtn: document.getElementById('support-status-clear-btn'),
    supportIupEditor: document.getElementById('support-iup-editor'),
    supportIupId: document.getElementById('support-iup-id'),
    supportIupStudent: document.getElementById('support-iup-student'),
    supportIupStatus: document.getElementById('support-iup-status'),
    supportIupOrderNumber: document.getElementById('support-iup-order-number'),
    supportIupOrderDate: document.getElementById('support-iup-order-date'),
    supportIupFrom: document.getElementById('support-iup-from'),
    supportIupTo: document.getElementById('support-iup-to'),
    supportIupSubjectBody: document.getElementById('support-iup-subject-body'),
    supportIupAddSubjectBtn: document.getElementById('support-iup-add-subject-btn'),
    supportIupSaveBtn: document.getElementById('support-iup-save-btn'),
    supportIupClearBtn: document.getElementById('support-iup-clear-btn')
};

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
let currentStats = null;
let currentManualRows = [];
let currentSupportSummary = null;
let currentSupportDocuments = [];
let supportReferences = { students: [], curriculum: [], teachers: [] };

function stageClassSummary(stats) {
    return `НОО: ${Number(stats?.totalClassesNoo || 0)}; ООО: ${Number(stats?.totalClassesOoo || 0)}; СОО: ${Number(stats?.totalClassesSoo || 0)}`;
}

function contingentPermissions() {
    const permissions = window.tarificationTabPermissions || {};
    if (window.tarificationAuth?.admin) {
        return { canImportView: true, canStatsView: true, canManualView: true, canSupportView: true };
    }
    return {
        canImportView: Boolean(permissions.CONTINGENT_IMPORT?.canView),
        canStatsView: Boolean(permissions.CONTINGENT_STATS?.canView),
        canManualView: Boolean(permissions.CONTINGENT_STATS?.canView),
        canSupportView: Boolean(permissions.CONTINGENT_STATS?.canView)
    };
}


async function waitForAuthContext() {
    for (let i = 0; i < 40; i += 1) {
        if (window.tarificationAuth) return;
        await new Promise((resolve) => setTimeout(resolve, 50));
    }
}

function applyTabAccess() {
    const { canImportView, canStatsView, canManualView, canSupportView } = contingentPermissions();
    ui.tabs.forEach((tab) => {
        const tabName = tab.dataset.contingentTab;
        const allowed = tabName === 'import'
            ? canImportView
            : (tabName === 'manual'
                ? canManualView
                : (tabName === 'support' ? canSupportView : canStatsView));
        tab.style.display = allowed ? '' : 'none';
    });

    if (canStatsView) return 'stats';
    if (canSupportView) return 'support';
    if (canManualView) return 'manual';
    if (canImportView) return 'import';
    return null;
}

async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

async function downloadWorkbook(path, fallbackName) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();
    const fileName = response.headers.get('Content-Disposition')?.split("filename*=UTF-8''")[1] || fallbackName;
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = decodeURIComponent(fileName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
}

function showTab(name) {
    ui.tabs.forEach((tab) => tab.classList.toggle('active', tab.dataset.contingentTab === name));
    ui.panes.forEach((pane) => {
        pane.style.display = pane.dataset.contingentPane === name ? '' : 'none';
    });
}

function printImportResult(value) {
    ui.importResult.textContent = JSON.stringify(value, null, 2);
}

function renderProblems(problems) {
    if (!problems?.length) {
        ui.problemsBody.innerHTML = '<tr><td colspan="3" class="muted">Проблем нет ✅</td></tr>';
        return;
    }
    ui.problemsBody.innerHTML = problems.map((problem) => `
        <tr>
            <td>${esc(problem.className)}</td>
            <td>${esc(problem.studentsCount)}</td>
            <td>${esc(problem.description)}</td>
        </tr>
    `).join('');
}

function renderStatsTable(stats) {
    if (ui.statsViewMode?.value === 'address') {
        renderStatsAddressTable(stats);
        return;
    }
    const columns = stats?.columns || [];
    const parallels = stats?.parallels || [];
    const totalByParallel = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalStudents]));
    const classCountByParallel = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalClasses || 0]));
    const totalClasses = (stats?.parallelTotals || []).reduce((sum, x) => sum + Number(x.totalClasses || 0), 0);

    const buildingHeader = columns.map((building) => {
        const addressSpan = Math.max((building.addresses || []).length * 2, 0);
        return `<th colspan="${addressSpan + 1}">${esc(building.buildingName || building.buildingCode)}</th>`;
    }).join('');

    const addressHeader = columns.map((building) => {
        const addressHeaders = (building.addresses || []).map((address) => `<th colspan="2">${esc(address.address || 'Адрес не указан')}</th>`).join('');
        return `${addressHeaders}<th>Σ СП</th>`;
    }).join('');

    const thead = `
        <thead>
            <tr>
                <th rowspan="2">Параллель</th>
                <th rowspan="2">Всего детей</th>
                <th rowspan="2">Всего классов</th>
                ${buildingHeader}
            </tr>
            <tr>${addressHeader}</tr>
        </thead>`;

    const tbodyRows = [];
    parallels.forEach((parallel) => {
        const perBuilding = columns.map((building) => {
            const addressRows = (building.addresses || []).map((address) =>
                (address.classes || []).filter((item) => Number(item.parallel) === Number(parallel))
            );
            const total = addressRows.flat().reduce((sum, item) => sum + Number(item.students || 0), 0);
            return { addressRows, total };
        });

        const rowCount = Math.max(1, ...perBuilding.flatMap((b) => b.addressRows.map((rows) => rows.length)));
        for (let i = 0; i < rowCount; i += 1) {
            let row = '<tr>';
            if (i === 0) {
                row += `<th rowspan="${rowCount}">${esc(parallel)}</th>`;
                row += `<th rowspan="${rowCount}">${esc(totalByParallel[parallel] || 0)}</th>`;
                row += `<th rowspan="${rowCount}">${esc(classCountByParallel[parallel] || 0)}</th>`;
            }

            perBuilding.forEach((buildingData) => {
                buildingData.addressRows.forEach((rows) => {
                    const item = rows[i];
                    row += `<td>${esc(item?.className || '')}</td>`;
                    row += `<td>${esc(item?.students || '')}</td>`;
                });
                if (i === 0) {
                    row += `<th rowspan="${rowCount}">${esc(buildingData.total)}</th>`;
                }
            });

            row += '</tr>';
            tbodyRows.push(row);
        }
    });

    const footerTotals = columns.map((building) => {
        const addressCells = (building.addresses || []).map((address) => `<th></th><th>${esc(address.totalStudents || 0)}</th>`).join('');
        return `${addressCells}<th>${esc(building.totalStudents || 0)}</th>`;
    }).join('');

    const footerClasses = columns.map((building) => {
        const addressCells = (building.addresses || []).map((address) => `<th></th><th>${esc((address.classes || []).length)}</th>`).join('');
        const buildingClasses = (building.addresses || []).reduce((sum, address) => sum + (address.classes || []).length, 0);
        return `${addressCells}<th>${esc(buildingClasses)}</th>`;
    }).join('');
    const footerStageCells = columns.map((building) => {
        const addressCells = (building.addresses || []).map(() => '<th></th><th></th>').join('');
        return `${addressCells}<th></th>`;
    }).join('');

    const footerTotalRow = `<tr><th>ИТОГО</th><th>${esc(stats?.totalStudents || 0)}</th>${footerTotals}</tr>`;
    const footerClassRow = `<tr><th>Классов</th><th></th>${footerClasses}</tr>`;

    const footerTotalRowWithClasses = `<tr><th>ИТОГО</th><th>${esc(stats?.totalStudents || 0)}</th><th>${esc(totalClasses)}</th>${footerTotals}</tr>`;
    const footerClassRowWithClasses = `<tr><th>Классов</th><th></th><th>${esc(totalClasses)}</th>${footerClasses}</tr>`;
    const footerStageRow = `<tr><th>По уровням</th><th></th><th>${esc(stageClassSummary(stats))}</th>${footerStageCells}</tr>`;
    ui.statsTable.innerHTML = `${thead}<tbody>${tbodyRows.join('')}${footerTotalRowWithClasses}${footerClassRowWithClasses}${footerStageRow}</tbody>`;
}


function addressColumns(stats) {
    const byAddress = new Map();
    (stats?.columns || []).forEach((building) => {
        (building.addresses || []).forEach((address) => {
            const addressName = address.address || 'Адрес не указан';
            const key = addressName.toLocaleLowerCase('ru');
            if (!byAddress.has(key)) {
                byAddress.set(key, {
                    address: addressName,
                    classes: [],
                    totalStudents: 0
                });
            }
            const column = byAddress.get(key);
            column.classes.push(...(address.classes || []));
            column.totalStudents += Number(address.totalStudents || 0);
        });
    });
    return Array.from(byAddress.values())
        .map((address) => ({
            ...address,
            classes: address.classes.slice().sort((a, b) => Number(a.parallel || 0) - Number(b.parallel || 0)
                || String(a.className || '').localeCompare(String(b.className || ''), 'ru', { numeric: true }))
        }))
        .sort((a, b) => a.address.localeCompare(b.address, 'ru'));
}

function renderStatsAddressTable(stats) {
    const addresses = addressColumns(stats);
    const parallels = stats?.parallels || [];
    const totalByParallel = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalStudents]));
    const classCountByParallel = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalClasses || 0]));
    const totalClasses = (stats?.parallelTotals || []).reduce((sum, x) => sum + Number(x.totalClasses || 0), 0);

    const addressHeader = addresses.map((address) => `<th colspan="2">${esc(address.address)}</th>`).join('');

    const thead = `
        <thead>
            <tr>
                <th>Параллель</th>
                <th>Всего детей</th>
                <th>Всего классов</th>
                ${addressHeader}
            </tr>
        </thead>`;

    const tbodyRows = [];
    parallels.forEach((parallel) => {
        const perAddress = addresses.map((address) => (address.classes || []).filter((item) => Number(item.parallel) === Number(parallel)));
        const rowCount = Math.max(1, ...perAddress.map((rows) => rows.length));
        for (let i = 0; i < rowCount; i += 1) {
            let row = '<tr>';
            if (i === 0) {
                row += `<th rowspan="${rowCount}">${esc(parallel)}</th>`;
                row += `<th rowspan="${rowCount}">${esc(totalByParallel[parallel] || 0)}</th>`;
                row += `<th rowspan="${rowCount}">${esc(classCountByParallel[parallel] || 0)}</th>`;
            }
            perAddress.forEach((rows) => {
                const item = rows[i];
                row += `<td>${esc(item?.className || '')}</td>`;
                row += `<td>${esc(item?.students || '')}</td>`;
            });
            row += '</tr>';
            tbodyRows.push(row);
        }
    });

    const footerTotals = addresses.map((address) => `<th></th><th>${esc(address.totalStudents || 0)}</th>`).join('');
    const footerClasses = addresses.map((address) => `<th></th><th>${esc((address.classes || []).length)}</th>`).join('');
    const footerStageCells = addresses.map(() => '<th></th><th></th>').join('');
    const footerTotalRow = `<tr><th>ИТОГО</th><th>${esc(stats?.totalStudents || 0)}</th>${footerTotals}</tr>`;
    const footerClassRow = `<tr><th>Классов</th><th></th>${footerClasses}</tr>`;

    const footerTotalRowWithClasses = `<tr><th>ИТОГО</th><th>${esc(stats?.totalStudents || 0)}</th><th>${esc(totalClasses)}</th>${footerTotals}</tr>`;
    const footerClassRowWithClasses = `<tr><th>Классов</th><th></th><th>${esc(totalClasses)}</th>${footerClasses}</tr>`;
    const footerStageRow = `<tr><th>По уровням</th><th></th><th>${esc(stageClassSummary(stats))}</th>${footerStageCells}</tr>`;
    ui.statsTable.innerHTML = `${thead}<tbody>${tbodyRows.join('')}${footerTotalRowWithClasses}${footerClassRowWithClasses}${footerStageRow}</tbody>`;
}

function renderManualTable(response) {
    currentManualRows = response?.rows || [];
    if (ui.manualSourceSelect) ui.manualSourceSelect.value = response?.source || 'AIS';
    const sourceLabel = response?.source === 'MANUAL' ? 'Ручной ввод' : 'АИС';
    const changed = currentManualRows.filter((row) => !row.matches).length;
    ui.manualSummary.textContent = `Источник сейчас: ${sourceLabel}. Несовпадений АИС и ручного ввода: ${changed}.`;
    const rows = currentManualRows.map((row, index) => {
        const statusClass = row.matches ? 'manual-size-match' : 'manual-size-mismatch';
        const status = row.matches ? 'Совпадает' : 'Не совпадает';
        return `<tr>
            <td>${esc(row.className)}</td>
            <td>${esc(row.aisStudents ?? '')}</td>
            <td><input type="number" min="0" step="1" data-manual-size-index="${index}" value="${esc(row.manualStudents ?? '')}"></td>
            <td class="${statusClass}">${status}</td>
        </tr>`;
    }).join('');
    ui.manualTable.innerHTML = `
        <thead><tr><th>Класс</th><th>Численность по АИС</th><th>Ручной ввод</th><th>Статус</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="4" class="muted">Классы пока не найдены.</td></tr>'}</tbody>`;
}

async function refreshManualClassSizes() {
    const response = await api('/api/contingent/manual-class-sizes');
    renderManualTable(response);
}

async function saveManualClassSizes() {
    const rows = currentManualRows.map((row, index) => {
        const input = ui.manualTable.querySelector(`[data-manual-size-index="${index}"]`);
        const raw = String(input?.value || '').trim();
        return {
            className: row.className,
            manualStudents: raw === '' ? null : Number(raw)
        };
    });
    const response = await api('/api/contingent/manual-class-sizes', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rows })
    });
    renderManualTable(response);
}

async function saveClassSizeSource() {
    const response = await api('/api/contingent/class-size-source', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ source: ui.manualSourceSelect.value })
    });
    renderManualTable(response);
}

async function importManualClassSizes() {
    const file = ui.manualFileInput.files?.[0];
    if (!file) {
        ui.manualSummary.textContent = 'Выберите Excel-файл для импорта.';
        return;
    }
    const form = new FormData();
    form.append('file', file);
    const response = await api('/api/contingent/manual-class-sizes/import', { method: 'POST', body: form });
    ui.manualFileInput.value = '';
    renderManualTable(response);
}

async function loadSnapshots() {
    const snapshots = await api('/api/contingent/snapshots');
    ui.snapshotDateSelect.innerHTML = '';
    snapshots.forEach((snapshot) => {
        const option = document.createElement('option');
        option.value = snapshot.snapshotDate;
        option.textContent = `${snapshot.snapshotDate} (импорт: ${String(snapshot.importedAt || '').replace('T', ' ').slice(0, 16)})`;
        ui.snapshotDateSelect.appendChild(option);
    });
}

async function refreshProblems() {
    const problems = await api('/api/contingent/problems');
    renderProblems(problems);
}

async function refreshStats() {
    const selectedDate = ui.snapshotDateSelect.value;
    const query = selectedDate ? `?snapshotDate=${encodeURIComponent(selectedDate)}` : '';
    currentStats = await api(`/api/contingent/stats${query}`);
    const totalClasses = (currentStats?.parallelTotals || []).reduce((sum, x) => sum + Number(x.totalClasses || 0), 0);
    ui.statsSummary.textContent = `Данные по состоянию на ${currentStats.snapshotDate}. Всего учащихся: ${currentStats.totalStudents}. Всего классов: ${totalClasses} (${stageClassSummary(currentStats)}). Для классов без численности применяется значение 30 человек.`;
    renderStatsTable(currentStats);
}

const supportCategoryLabel = (value) => ({ NORMAL: 'Норма', K2: 'К2', K3: 'К3' }[value] || value || '');
const supportIupStatusLabel = (value) => ({
    DRAFT: 'Черновик',
    REVIEW: 'На согласовании',
    APPROVED: 'Утверждён',
    ACTIVE: 'Действует',
    CHANGED: 'Изменён',
    COMPLETED: 'Завершён',
    CANCELLED: 'Отменён'
}[value] || value || '');
const supportModeLabel = (value) => ({
    WITH_CLASS: 'С классом',
    INDIVIDUAL: 'Индивидуально',
    PARTIAL: 'Частично с классом',
    NOT_STUDIED: 'Не изучает'
}[value] || value || '');

function supportDateRange(from, to) {
    if (!from && !to) return '';
    return `${from || '…'} — ${to || 'бессрочно'}`;
}

function supportSelectedStudent() {
    const studentId = Number(ui.supportIupStudent?.value || 0);
    return (supportReferences.students || []).find((student) => Number(student.studentId) === studentId) || null;
}

function supportStudentOptions() {
    const options = (supportReferences.students || []).map((student) =>
        `<option value="${esc(student.studentId)}">${esc(student.className)} — ${esc(student.fullName)}</option>`
    ).join('');
    return `<option value="">Выберите ребёнка</option>${options}`;
}

function supportCurriculumForSelectedStudent() {
    const student = supportSelectedStudent();
    if (!student) return [];
    return (supportReferences.curriculum || []).filter((entry) =>
        String(entry.className || '').localeCompare(String(student.className || ''), 'ru', { sensitivity: 'base' }) === 0
    );
}

function supportCurriculumOptions(selectedId) {
    const entries = supportCurriculumForSelectedStudent();
    return `<option value="">Выберите предмет</option>${entries.map((entry) => `
        <option value="${esc(entry.curriculumEntryId)}" ${Number(entry.curriculumEntryId) === Number(selectedId) ? 'selected' : ''}>
            ${esc(entry.subjectName)}${entry.subgroupRequired ? ` · ${esc(entry.subgroupCount || 2)} группы` : ''}
        </option>
    `).join('')}`;
}

function supportTeacherOptions(selectedId) {
    return `<option value="">Не назначен</option>${(supportReferences.teachers || []).map((teacher) => `
        <option value="${esc(teacher.teacherId)}" ${Number(teacher.teacherId) === Number(selectedId) ? 'selected' : ''}>
            ${esc(teacher.fullName)}${teacher.archived ? ' (архив)' : ''}
        </option>
    `).join('')}`;
}

function supportGroupOptions(curriculumEntryId, selected) {
    const entry = (supportReferences.curriculum || []).find((item) =>
        Number(item.curriculumEntryId) === Number(curriculumEntryId)
    );
    if (!entry?.subgroupRequired) {
        return '<option value="">Не требуется</option>';
    }
    const count = Math.max(1, Number(entry.subgroupCount || 2));
    const options = [];
    for (let index = 1; index <= count; index += 1) {
        const name = `Группа ${index}`;
        options.push(`<option value="${name}" ${name === selected ? 'selected' : ''}>${name}</option>`);
    }
    return `<option value="">Выберите группу</option>${options.join('')}`;
}

function addSupportIupSubjectRow(subject = {}) {
    const assignment = subject.teachers?.[0] || {};
    const row = document.createElement('tr');
    row.innerHTML = `
        <td><select data-iup-field="curriculumEntryId">${supportCurriculumOptions(subject.curriculumEntryId)}</select></td>
        <td>
            <select data-iup-field="participationMode">
                ${['WITH_CLASS', 'INDIVIDUAL', 'PARTIAL', 'NOT_STUDIED'].map((mode) =>
                    `<option value="${mode}" ${(subject.participationMode || 'INDIVIDUAL') === mode ? 'selected' : ''}>${supportModeLabel(mode)}</option>`
                ).join('')}
            </select>
        </td>
        <td><input data-iup-field="classHours" type="number" min="0" step="0.25" value="${esc(subject.classHours ?? 0)}"></td>
        <td><input data-iup-field="individualHours" type="number" min="0" step="0.25" value="${esc(subject.individualHours ?? 0)}"></td>
        <td><select data-iup-field="groupNameEducationalPlan">${supportGroupOptions(subject.curriculumEntryId, subject.groupNameEducationalPlan)}</select></td>
        <td><select data-iup-field="teacherId">${supportTeacherOptions(assignment.teacherId)}</select></td>
        <td><input data-iup-field="teacherHours" type="number" min="0" step="0.25" value="${esc(assignment.hoursPerWeek ?? '')}"></td>
        <td>
            <select data-iup-field="deliveryForm">
                <option value="FACE_TO_FACE" ${(assignment.deliveryForm || 'FACE_TO_FACE') === 'FACE_TO_FACE' ? 'selected' : ''}>Очно</option>
                <option value="ELECTRONIC" ${assignment.deliveryForm === 'ELECTRONIC' ? 'selected' : ''}>Электронно</option>
                <option value="DISTANCE" ${assignment.deliveryForm === 'DISTANCE' ? 'selected' : ''}>Дистанционно</option>
                <option value="MIXED" ${assignment.deliveryForm === 'MIXED' ? 'selected' : ''}>Смешанно</option>
            </select>
        </td>
        <td><button type="button" class="secondary" data-iup-remove-subject>×</button></td>
    `;
    ui.supportIupSubjectBody.appendChild(row);
}

function updateSupportSubjectCurriculumOptions() {
    Array.from(ui.supportIupSubjectBody?.querySelectorAll('tr') || []).forEach((row) => {
        const select = row.querySelector('[data-iup-field="curriculumEntryId"]');
        const selectedId = select?.value;
        select.innerHTML = supportCurriculumOptions(selectedId);
        if (selectedId) select.value = selectedId;
        updateSupportGroupOptions(row);
    });
}

function updateSupportGroupOptions(row) {
    const curriculumId = row.querySelector('[data-iup-field="curriculumEntryId"]')?.value;
    const groupSelect = row.querySelector('[data-iup-field="groupNameEducationalPlan"]');
    const selected = groupSelect?.value;
    if (groupSelect) {
        groupSelect.innerHTML = supportGroupOptions(curriculumId, selected);
        if (selected) groupSelect.value = selected;
    }
}

function resetSupportStatusForm() {
    ui.supportStatusId.value = '';
    ui.supportStatusStudent.value = '';
    ui.supportStatusCategory.value = 'NORMAL';
    ui.supportStatusFrom.value = ui.supportAsOfDate.value || '';
    ui.supportStatusTo.value = '';
}

function resetSupportIupForm() {
    ui.supportIupId.value = '';
    ui.supportIupStudent.value = '';
    ui.supportIupStatus.value = 'DRAFT';
    ui.supportIupOrderNumber.value = '';
    ui.supportIupOrderDate.value = '';
    ui.supportIupFrom.value = ui.supportAsOfDate.value || '';
    ui.supportIupTo.value = '';
    ui.supportIupSubjectBody.innerHTML = '';
}

const supportDocumentTypeLabels = {
    MSE_CERTIFICATE: 'Справка МСЭ',
    IPR_IPRA: 'ИПР/ИПРА',
    CPMPC_CONCLUSION: 'Заключение ЦПМПК',
    INTERNAL_PPK_PROTOCOL: 'Протокол ППк',
    IOM: 'ИОМ',
    OTHER: 'Другой документ'
};

const supportDocumentFormLabels = {
    ORIGINAL: 'Оригинал',
    COPY: 'Копия',
    ELECTRONIC_COPY: 'Электронная копия'
};

function resetSupportDocumentForm() {
    ui.supportDocumentId.value = '';
    ui.supportDocumentStudent.value = '';
    ui.supportDocumentType.value = 'MSE_CERTIFICATE';
    ui.supportDocumentForm.value = 'COPY';
    ui.supportDocumentNumber.value = '';
    ui.supportDocumentIssueDate.value = '';
    ui.supportDocumentValidFrom.value = '';
    ui.supportDocumentValidTo.value = '';
    const today = new Date();
    today.setMinutes(today.getMinutes() - today.getTimezoneOffset());
    ui.supportDocumentReceivedAt.value = today.toISOString().slice(0, 10);
    ui.supportDocumentOrganization.value = '';
    ui.supportDocumentResponsible.value = '';
    ui.supportDocumentComment.value = '';
    ui.supportDocumentFile.value = '';
    ui.supportDocumentSaveBtn.textContent = 'Принять документ';
}

function supportAttachmentSize(value) {
    const bytes = Number(value || 0);
    if (bytes < 1024) return `${bytes} Б`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
}

function supportAttachmentLink(documentId, attachment) {
    const path = `/api/contingent/special-support/documents/${encodeURIComponent(documentId)}`
        + `/attachments/${encodeURIComponent(attachment.id)}`;
    const href = window.withAcademicYear ? window.withAcademicYear(path) : path;
    return `
        <span>
            <a href="${esc(href)}">${esc(attachment.fileName)}</a>
            <small class="muted">(${esc(supportAttachmentSize(attachment.fileSize))})</small>
            <button type="button" class="secondary" data-requires-edit
                    data-support-delete-attachment="${esc(attachment.id)}"
                    data-support-document-id="${esc(documentId)}">×</button>
        </span>`;
}

function renderSupportDocuments(documents) {
    const rows = (documents || []).map((document) => {
        const dates = [
            document.issueDate ? `выдан ${document.issueDate}` : '',
            (document.validFrom || document.validTo)
                ? `действует ${supportDateRange(document.validFrom, document.validTo)}`
                : ''
        ].filter(Boolean).join('; ');
        const files = (document.attachments || [])
            .map((attachment) => supportAttachmentLink(document.id, attachment))
            .join('<br>');
        return `
            <tr>
                <td>${esc(document.studentFullName)}</td>
                <td>${esc(document.className)}</td>
                <td>${esc(supportDocumentTypeLabels[document.documentType] || document.documentType)}</td>
                <td>${esc(document.documentNumber || '')}</td>
                <td>${esc(dates || '—')}</td>
                <td>${esc(supportDocumentFormLabels[document.acceptedForm] || document.acceptedForm)}</td>
                <td>${esc(document.receivedAt || '')}</td>
                <td>${esc(document.validityStatus || '')}</td>
                <td>${files || '<span class="muted">Нет копий</span>'}</td>
                <td data-requires-edit>
                    <button type="button" class="secondary"
                            data-support-edit-document="${esc(document.id)}">Изменить</button>
                    <button type="button" class="secondary"
                            data-support-delete-document="${esc(document.id)}">Удалить</button>
                </td>
            </tr>`;
    }).join('');
    ui.supportDocumentTable.innerHTML = `
        <thead>
            <tr>
                <th>ФИО</th><th>Класс</th><th>Документ</th><th>Номер</th><th>Даты</th>
                <th>Принято</th><th>Дата приёма</th><th>Состояние</th><th>Копии</th><th></th>
            </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="10" class="muted">Документы пока не приняты.</td></tr>'}</tbody>`;
}

async function refreshSupportDocuments() {
    const asOf = ui.supportAsOfDate?.value
        ? `?asOfDate=${encodeURIComponent(ui.supportAsOfDate.value)}`
        : '';
    currentSupportDocuments = await api(`/api/contingent/special-support/documents${asOf}`);
    renderSupportDocuments(currentSupportDocuments);
    ui.supportDocumentMessage.textContent = currentSupportDocuments.length
        ? `Принято документов: ${currentSupportDocuments.length}.`
        : 'Документы пока не приняты.';
}

async function saveSupportDocument() {
    if (!ui.supportDocumentStudent.value) {
        ui.supportDocumentMessage.textContent = 'Выберите ребёнка, для которого принят документ.';
        ui.supportDocumentStudent.focus();
        return;
    }
    const selectedFile = ui.supportDocumentFile.files?.[0];
    if (selectedFile && selectedFile.size > 15 * 1024 * 1024) {
        ui.supportDocumentMessage.textContent = 'Размер прикреплённой копии не должен превышать 15 МБ.';
        ui.supportDocumentFile.focus();
        return;
    }
    const payload = {
        id: ui.supportDocumentId.value ? Number(ui.supportDocumentId.value) : null,
        studentId: Number(ui.supportDocumentStudent.value || 0) || null,
        documentType: ui.supportDocumentType.value,
        acceptedForm: ui.supportDocumentForm.value,
        documentNumber: ui.supportDocumentNumber.value || null,
        issueDate: ui.supportDocumentIssueDate.value || null,
        validFrom: ui.supportDocumentValidFrom.value || null,
        validTo: ui.supportDocumentValidTo.value || null,
        receivedAt: ui.supportDocumentReceivedAt.value || null,
        issuingOrganization: ui.supportDocumentOrganization.value || null,
        responsibleEmployee: ui.supportDocumentResponsible.value || null,
        comment: ui.supportDocumentComment.value || null
    };
    const saved = await api('/api/contingent/special-support/documents', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    const file = selectedFile;
    if (file) {
        const form = new FormData();
        form.append('file', file);
        await api(`/api/contingent/special-support/documents/${encodeURIComponent(saved.id)}/attachments`, {
            method: 'POST',
            body: form
        });
    }
    resetSupportDocumentForm();
    await refreshSupportDocuments();
    ui.supportDocumentMessage.textContent = 'Документ принят и сохранён.';
}

function editSupportDocument(documentId) {
    const document = currentSupportDocuments.find((item) => Number(item.id) === Number(documentId));
    if (!document) return;
    ui.supportDocumentId.value = document.id || '';
    ui.supportDocumentStudent.value = document.studentId || '';
    ui.supportDocumentType.value = document.documentType || 'OTHER';
    ui.supportDocumentForm.value = document.acceptedForm || 'COPY';
    ui.supportDocumentNumber.value = document.documentNumber || '';
    ui.supportDocumentIssueDate.value = document.issueDate || '';
    ui.supportDocumentValidFrom.value = document.validFrom || '';
    ui.supportDocumentValidTo.value = document.validTo || '';
    ui.supportDocumentReceivedAt.value = document.receivedAt || '';
    ui.supportDocumentOrganization.value = document.issuingOrganization || '';
    ui.supportDocumentResponsible.value = document.responsibleEmployee || '';
    ui.supportDocumentComment.value = document.comment || '';
    ui.supportDocumentFile.value = '';
    ui.supportDocumentSaveBtn.textContent = 'Сохранить изменения';
    ui.supportDocumentSection.open = true;
    ui.supportDocumentSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderSupportClassTable(summary) {
    const rows = (summary?.classes || []).map((item) => `
        <tr>
            <td>${esc(item.className)}</td>
            <td><strong>${esc(item.total)}</strong></td>
            <td>${esc(item.normal)}</td>
            <td>${esc(item.k2)}</td>
            <td>${esc(item.k3)}</td>
            <td>${esc(item.iup)}</td>
        </tr>
    `).join('');
    const totals = (summary?.classes || []).reduce((acc, item) => ({
        total: acc.total + Number(item.total || 0),
        normal: acc.normal + Number(item.normal || 0),
        k2: acc.k2 + Number(item.k2 || 0),
        k3: acc.k3 + Number(item.k3 || 0),
        iup: acc.iup + Number(item.iup || 0)
    }), { total: 0, normal: 0, k2: 0, k3: 0, iup: 0 });
    ui.supportClassTable.innerHTML = `
        <thead><tr><th>Класс</th><th>Всего</th><th>Норма</th><th>К2</th><th>К3</th><th>ИУП</th></tr></thead>
        <tbody>
            ${rows || '<tr><td colspan="6" class="muted">Нет данных.</td></tr>'}
            <tr><th>ИТОГО</th><th>${totals.total}</th><th>${totals.normal}</th><th>${totals.k2}</th><th>${totals.k3}</th><th>${totals.iup}</th></tr>
        </tbody>`;
}

function renderSupportRegister(summary) {
    const rows = (summary?.registerRows || []).map((item) => `
        <tr>
            <td>${esc(item.fullName)}</td>
            <td>${esc(item.className)}</td>
            <td>${esc(supportCategoryLabel(item.underlyingCategory))}</td>
            <td>${esc(supportDateRange(item.categoryValidFrom, item.categoryValidTo))}</td>
            <td>${item.hasIup ? 'Да' : 'Нет'}</td>
            <td>${esc(supportIupStatusLabel(item.iupStatus))}</td>
            <td>${esc(supportDateRange(item.iupValidFrom, item.iupValidTo))}</td>
            <td>${esc(item.orderNumber || '')}${item.orderDate ? ` от ${esc(item.orderDate)}` : ''}</td>
            <td data-requires-edit>
                ${item.supportStatusId ? `<button type="button" class="secondary" data-support-edit-status="${esc(item.studentId)}">Статус</button>` : ''}
                ${item.iupPlanId ? `<button type="button" class="secondary" data-support-edit-iup="${esc(item.iupPlanId)}">ИУП</button>` : ''}
            </td>
        </tr>
    `).join('');
    ui.supportRegisterTable.innerHTML = `
        <thead>
            <tr>
                <th>ФИО</th><th>Класс</th><th>Категория</th><th>Срок категории</th>
                <th>ИУП</th><th>Статус ИУП</th><th>Срок ИУП</th><th>Приказ</th><th></th>
            </tr>
        </thead>
        <tbody>${rows || '<tr><td colspan="9" class="muted">Действующих К2, К3 и ИУП на эту дату нет.</td></tr>'}</tbody>`;
}

async function loadSupportReferences() {
    supportReferences = await api('/api/contingent/special-support/references');
    const options = supportStudentOptions();
    ui.supportStatusStudent.innerHTML = options;
    ui.supportIupStudent.innerHTML = options;
    ui.supportDocumentStudent.innerHTML = options;
}

function supportQuery() {
    const params = new URLSearchParams();
    if (ui.snapshotDateSelect?.value) params.set('snapshotDate', ui.snapshotDateSelect.value);
    if (ui.supportAsOfDate?.value) params.set('asOfDate', ui.supportAsOfDate.value);
    return params.toString() ? `?${params}` : '';
}

async function refreshSupport() {
    currentSupportSummary = await api(`/api/contingent/special-support/summary${supportQuery()}`);
    if (!ui.supportAsOfDate.value) {
        ui.supportAsOfDate.value = currentSupportSummary.asOfDate || currentSupportSummary.snapshotDate || '';
    }
    renderSupportClassTable(currentSupportSummary);
    renderSupportRegister(currentSupportSummary);
    if (ui.supportReconcileBtn) {
        ui.supportReconcileBtn.disabled = Number(currentSupportSummary.unlinkedStudents || 0) === 0;
    }
    const warning = (currentSupportSummary.warnings || []).join(' ');
    ui.supportSummaryMessage.textContent =
        `Снимок контингента: ${currentSupportSummary.snapshotDate}. Всего: ${currentSupportSummary.totalStudents}. `
        + `Формула каждой строки: Всего = Норма + К2 + К3 + ИУП.${warning ? ` ${warning}` : ''}`;
}

function renderSupportReadiness(readiness) {
    const rows = [
        ['Режим расчёта', readiness?.calculationMode || ''],
        ['Дата контингента', readiness?.snapshotDate || '—'],
        ['Всего детей', readiness?.totalStudents ?? 0],
        ['Связано карточек', readiness?.linkedStudents ?? 0],
        ['Не связано', readiness?.unlinkedStudents ?? 0],
        ['Нозологий в справочнике', readiness?.nosologies ?? 0],
        ['Активных ИУП', readiness?.activeIups ?? 0],
        ['Ожидается распределений', readiness?.expectedGroupAssignments ?? 0],
        ['Заполнено распределений', readiness?.completedGroupAssignments ?? 0],
        ['Не заполнено', readiness?.missingGroupAssignments ?? 0],
        ['Дубли', readiness?.duplicateGroupAssignments ?? 0],
        ['Фактическая численность применяется', readiness?.readyForStudentCountCutover ? 'Да' : 'Нет']
    ];
    const messages = [
        ...(readiness?.blockers || []).map((message) => ['Блокирующая проверка', message]),
        ...(readiness?.notes || []).map((message) => ['Примечание', message])
    ];
    ui.supportReadinessTable.innerHTML = `
        <thead><tr><th>Показатель</th><th>Значение</th></tr></thead>
        <tbody>${[...rows, ...messages].map(([label, value]) => `
            <tr><td>${esc(label)}</td><td>${esc(value)}</td></tr>
        `).join('')}</tbody>`;
}

async function refreshSupportReadiness() {
    const readiness = await api('/api/contingent/special-support/data-package/readiness');
    renderSupportReadiness(readiness);
    const blockers = readiness?.blockers || [];
    ui.supportDataPackageResult.textContent = readiness?.readyForStudentCountCutover
        ? 'Данные прошли контроль. Фактическая численность автоматически применяется в нагрузке и расчёте зарплаты.'
        : `Пока действует резервный расчёт. Для автоматического применения фактической численности нужно исправить: ${blockers.join(' ') || 'загрузить недостающие данные.'}`;
}

async function importSupportDataPackage() {
    const file = ui.supportDataPackageFile.files?.[0];
    if (!file) {
        ui.supportDataPackageResult.textContent = 'Выберите заполненный Excel-пакет.';
        return;
    }
    const form = new FormData();
    form.append('file', file);
    const result = await api('/api/contingent/special-support/data-package/import', {
        method: 'POST',
        body: form
    });
    const errors = result?.errors || [];
    ui.supportDataPackageResult.textContent =
        `Импортировано: ${Number(result?.imported || 0)}, удалено: ${Number(result?.deleted || 0)}, `
        + `пропущено: ${Number(result?.skipped || 0)}, ошибок: ${errors.length}.`
        + (errors.length
            ? ` Первые ошибки: ${errors.slice(0, 5).map((item) =>
                `${item.sheetName}, строка ${item.rowNumber}: ${item.message}`
            ).join(' | ')}`
            : '');
    ui.supportDataPackageFile.value = '';
    await loadSupportReferences();
    await refreshSupport();
    await refreshSupportDocuments();
    await refreshSupportReadiness();
}

async function saveSupportStatus() {
    const payload = {
        id: ui.supportStatusId.value ? Number(ui.supportStatusId.value) : null,
        studentId: Number(ui.supportStatusStudent.value || 0) || null,
        category: ui.supportStatusCategory.value,
        validFrom: ui.supportStatusFrom.value || null,
        validTo: ui.supportStatusTo.value || null
    };
    await api('/api/contingent/special-support/statuses', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    resetSupportStatusForm();
    await refreshSupport();
    ui.supportSummaryMessage.textContent = `Статус сохранён. ${ui.supportSummaryMessage.textContent}`;
}

function supportIupSubjectsPayload() {
    return Array.from(ui.supportIupSubjectBody.querySelectorAll('tr')).map((row) => {
        const value = (name) => row.querySelector(`[data-iup-field="${name}"]`)?.value || '';
        const curriculumEntryId = Number(value('curriculumEntryId') || 0) || null;
        const curriculum = (supportReferences.curriculum || []).find((item) =>
            Number(item.curriculumEntryId) === curriculumEntryId
        );
        const teacherId = Number(value('teacherId') || 0) || null;
        const teacherHours = value('teacherHours');
        return {
            curriculumEntryId,
            subjectName: curriculum?.subjectName || null,
            participationMode: value('participationMode'),
            classHours: Number(value('classHours') || 0),
            individualHours: Number(value('individualHours') || 0),
            groupNameEducationalPlan: value('groupNameEducationalPlan') || null,
            teachers: teacherId ? [{
                teacherId,
                hoursPerWeek: Number(teacherHours || 0),
                deliveryForm: value('deliveryForm')
            }] : []
        };
    });
}

async function saveSupportIup() {
    const payload = {
        id: ui.supportIupId.value ? Number(ui.supportIupId.value) : null,
        studentId: Number(ui.supportIupStudent.value || 0) || null,
        status: ui.supportIupStatus.value,
        orderNumber: ui.supportIupOrderNumber.value || null,
        orderDate: ui.supportIupOrderDate.value || null,
        validFrom: ui.supportIupFrom.value || null,
        validTo: ui.supportIupTo.value || null,
        subjects: supportIupSubjectsPayload()
    };
    await api('/api/contingent/special-support/iups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    resetSupportIupForm();
    await refreshSupport();
    ui.supportSummaryMessage.textContent = `ИУП сохранён. ${ui.supportSummaryMessage.textContent}`;
}

function editSupportStatus(studentId) {
    const row = (currentSupportSummary?.registerRows || []).find((item) => Number(item.studentId) === Number(studentId));
    if (!row) return;
    ui.supportStatusId.value = row.supportStatusId || '';
    ui.supportStatusStudent.value = row.studentId || '';
    ui.supportStatusCategory.value = row.underlyingCategory || 'NORMAL';
    ui.supportStatusFrom.value = row.categoryValidFrom || '';
    ui.supportStatusTo.value = row.categoryValidTo || '';
    ui.supportStatusEditor.open = true;
    ui.supportStatusEditor.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function editSupportIup(iupPlanId) {
    const plan = await api(`/api/contingent/special-support/iups/${encodeURIComponent(iupPlanId)}`);
    ui.supportIupId.value = plan.id || '';
    ui.supportIupStudent.value = plan.studentId || '';
    ui.supportIupStatus.value = plan.status || 'DRAFT';
    ui.supportIupOrderNumber.value = plan.orderNumber || '';
    ui.supportIupOrderDate.value = plan.orderDate || '';
    ui.supportIupFrom.value = plan.validFrom || '';
    ui.supportIupTo.value = plan.validTo || '';
    ui.supportIupSubjectBody.innerHTML = '';
    (plan.subjects || []).forEach(addSupportIupSubjectRow);
    ui.supportIupEditor.open = true;
    ui.supportIupEditor.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

ui.tabs.forEach((tab) => tab.addEventListener('click', () => {
    const tabName = tab.dataset.contingentTab;
    showTab(tabName);
    window.location.hash = `#${tabName}`;
    if (tabName === 'manual') {
        refreshManualClassSizes().catch((error) => {
            ui.manualSummary.textContent = `Ошибка: ${error.message}`;
        });
    }
    if (tabName === 'support') {
        Promise.all([
            loadSupportReferences(),
            refreshSupport(),
            refreshSupportDocuments(),
            refreshSupportReadiness()
        ]).catch((error) => {
            ui.supportSummaryMessage.textContent = `Ошибка: ${error.message}`;
        });
    }
}));

ui.importBtn.addEventListener('click', async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) {
        printImportResult({ error: 'Выберите Excel файл' });
        return;
    }
    try {
        const form = new FormData();
        form.append('file', file);
        const result = await api('/api/contingent/import', { method: 'POST', body: form });
        printImportResult(result);
        ui.fileInput.value = '';
        await loadSnapshots();
        await refreshProblems();
        await refreshStats();
    } catch (error) {
        printImportResult({ error: error.message });
    }
});

ui.statsRefreshBtn.addEventListener('click', () => refreshStats().catch((error) => {
    ui.statsSummary.textContent = `Ошибка: ${error.message}`;
}));

ui.snapshotDateSelect.addEventListener('change', () => refreshStats().catch((error) => {
    ui.statsSummary.textContent = `Ошибка: ${error.message}`;
}));

ui.statsViewMode?.addEventListener('change', () => {
    if (currentStats) {
        renderStatsTable(currentStats);
    }
});


ui.statsExportBtn?.addEventListener('click', async () => {
    try {
        const selectedDate = ui.snapshotDateSelect.value;
        const query = selectedDate ? `?snapshotDate=${encodeURIComponent(selectedDate)}` : '';
        await downloadWorkbook(`/api/contingent/stats/export${query}`, `contingent_${selectedDate || 'latest'}.xlsx`);
    } catch (error) {
        ui.statsSummary.textContent = `Ошибка экспорта: ${error.message}`;
    }
});

ui.manualRefreshBtn?.addEventListener('click', () => refreshManualClassSizes().catch((error) => {
    ui.manualSummary.textContent = `Ошибка: ${error.message}`;
}));

ui.manualSaveBtn?.addEventListener('click', () => saveManualClassSizes().catch((error) => {
    ui.manualSummary.textContent = `Ошибка сохранения: ${error.message}`;
}));

ui.manualSourceSaveBtn?.addEventListener('click', () => saveClassSizeSource().catch((error) => {
    ui.manualSummary.textContent = `Ошибка переключения источника: ${error.message}`;
}));

ui.manualImportBtn?.addEventListener('click', () => importManualClassSizes().catch((error) => {
    ui.manualSummary.textContent = `Ошибка импорта: ${error.message}`;
}));

ui.manualExportBtn?.addEventListener('click', () => downloadWorkbook('/api/contingent/manual-class-sizes/export', 'manual-class-sizes.xlsx').catch((error) => {
    ui.manualSummary.textContent = `Ошибка экспорта: ${error.message}`;
}));

ui.supportRefreshBtn?.addEventListener('click', () =>
    Promise.all([refreshSupport(), refreshSupportDocuments()]).catch((error) => {
        ui.supportSummaryMessage.textContent = `Ошибка: ${error.message}`;
    })
);

ui.supportExportBtn?.addEventListener('click', () =>
    downloadWorkbook(`/api/contingent/special-support/export${supportQuery()}`, 'student-statuses-iup.xlsx')
        .catch((error) => {
            ui.supportSummaryMessage.textContent = `Ошибка экспорта: ${error.message}`;
        })
);

ui.supportDataPackageExportBtn?.addEventListener('click', () =>
    downloadWorkbook(
        '/api/contingent/special-support/data-package/export',
        'student-data-package.xlsx'
    ).catch((error) => {
        ui.supportDataPackageResult.textContent = `Ошибка экспорта пакета: ${error.message}`;
    })
);

ui.supportDataPackageImportBtn?.addEventListener('click', () =>
    importSupportDataPackage().catch((error) => {
        ui.supportDataPackageResult.textContent = `Ошибка импорта пакета: ${error.message}`;
    })
);

ui.supportReadinessBtn?.addEventListener('click', () =>
    refreshSupportReadiness().catch((error) => {
        ui.supportDataPackageResult.textContent = `Ошибка проверки: ${error.message}`;
    })
);

ui.supportDocumentSaveBtn?.addEventListener('click', () =>
    saveSupportDocument().catch((error) => {
        ui.supportDocumentMessage.textContent = `Ошибка сохранения документа: ${error.message}`;
    })
);

ui.supportDocumentClearBtn?.addEventListener('click', resetSupportDocumentForm);

ui.supportDocumentTable?.addEventListener('click', async (event) => {
    const editButton = event.target.closest('[data-support-edit-document]');
    if (editButton) {
        editSupportDocument(editButton.dataset.supportEditDocument);
        return;
    }
    const deleteDocumentButton = event.target.closest('[data-support-delete-document]');
    if (deleteDocumentButton) {
        if (!window.confirm('Удалить запись о документе и все прикреплённые копии?')) return;
        try {
            await api(
                `/api/contingent/special-support/documents/${encodeURIComponent(deleteDocumentButton.dataset.supportDeleteDocument)}`,
                { method: 'DELETE' }
            );
            resetSupportDocumentForm();
            await refreshSupportDocuments();
            ui.supportDocumentMessage.textContent = 'Запись о документе удалена.';
        } catch (error) {
            ui.supportDocumentMessage.textContent = `Ошибка удаления документа: ${error.message}`;
        }
        return;
    }
    const deleteAttachmentButton = event.target.closest('[data-support-delete-attachment]');
    if (deleteAttachmentButton) {
        if (!window.confirm('Удалить прикреплённую копию?')) return;
        try {
            const documentId = encodeURIComponent(deleteAttachmentButton.dataset.supportDocumentId);
            const attachmentId = encodeURIComponent(deleteAttachmentButton.dataset.supportDeleteAttachment);
            await api(
                `/api/contingent/special-support/documents/${documentId}/attachments/${attachmentId}`,
                { method: 'DELETE' }
            );
            await refreshSupportDocuments();
            ui.supportDocumentMessage.textContent = 'Прикреплённая копия удалена.';
        } catch (error) {
            ui.supportDocumentMessage.textContent = `Ошибка удаления копии: ${error.message}`;
        }
    }
});

ui.supportReconcileBtn?.addEventListener('click', async () => {
    try {
        if (!currentSupportSummary?.snapshotId) {
            await refreshSupport();
        }
        const result = await api(`/api/contingent/special-support/reconcile/${encodeURIComponent(currentSupportSummary.snapshotId)}`, {
            method: 'POST'
        });
        await loadSupportReferences();
        await refreshSupport();
        ui.supportSummaryMessage.textContent =
            `Карточки связаны: ${Number(result.linked || 0)}, создано: ${Number(result.created || 0)}, неоднозначно: ${Number(result.ambiguous || 0)}. `
            + ui.supportSummaryMessage.textContent;
    } catch (error) {
        ui.supportSummaryMessage.textContent = `Ошибка сопоставления карточек: ${error.message}`;
    }
});

ui.supportStatusSaveBtn?.addEventListener('click', () => saveSupportStatus().catch((error) => {
    ui.supportSummaryMessage.textContent = `Ошибка сохранения статуса: ${error.message}`;
}));

ui.supportStatusClearBtn?.addEventListener('click', resetSupportStatusForm);

ui.supportIupStudent?.addEventListener('change', updateSupportSubjectCurriculumOptions);

ui.supportIupAddSubjectBtn?.addEventListener('click', () => {
    if (!ui.supportIupStudent.value) {
        ui.supportSummaryMessage.textContent = 'Сначала выберите ребёнка.';
        return;
    }
    addSupportIupSubjectRow();
});

ui.supportIupSubjectBody?.addEventListener('change', (event) => {
    if (event.target.matches('[data-iup-field="curriculumEntryId"]')) {
        updateSupportGroupOptions(event.target.closest('tr'));
    }
});

ui.supportIupSubjectBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-iup-remove-subject]');
    if (button) button.closest('tr')?.remove();
});

ui.supportIupSaveBtn?.addEventListener('click', () => saveSupportIup().catch((error) => {
    ui.supportSummaryMessage.textContent = `Ошибка сохранения ИУП: ${error.message}`;
}));

ui.supportIupClearBtn?.addEventListener('click', resetSupportIupForm);

ui.supportRegisterTable?.addEventListener('click', (event) => {
    const statusButton = event.target.closest('[data-support-edit-status]');
    if (statusButton) {
        editSupportStatus(statusButton.dataset.supportEditStatus);
        return;
    }
    const iupButton = event.target.closest('[data-support-edit-iup]');
    if (iupButton) {
        editSupportIup(iupButton.dataset.supportEditIup).catch((error) => {
            ui.supportSummaryMessage.textContent = `Ошибка загрузки ИУП: ${error.message}`;
        });
    }
});

(async function init() {
    try {
        await waitForAuthContext();
        const defaultTab = applyTabAccess();
        if (!defaultTab) {
            ui.statsSummary.textContent = 'Нет доступа к вкладкам контингента.';
            return;
        }

        const hash = String(window.location.hash || '').toLowerCase();
        const requestedTab = ['#import', '#manual', '#support', '#stats'].includes(hash)
            ? hash.slice(1)
            : defaultTab;
        const permissions = contingentPermissions();
        const finalTab = (requestedTab === 'import' && permissions.canImportView)
            || (requestedTab === 'manual' && permissions.canManualView)
            || (requestedTab === 'support' && permissions.canSupportView)
            || (requestedTab === 'stats' && permissions.canStatsView)
            ? requestedTab
            : defaultTab;
        showTab(finalTab);

        await loadSnapshots();
        if (contingentPermissions().canImportView) {
            await refreshProblems();
        } else {
            renderProblems([]);
        }

        if (contingentPermissions().canStatsView && ui.snapshotDateSelect.options.length) {
            await refreshStats();
        } else if (contingentPermissions().canStatsView) {
            ui.statsSummary.textContent = 'Данные контингента пока не загружены.';
            ui.statsTable.innerHTML = '';
        }
        if (finalTab === 'manual' || contingentPermissions().canManualView) {
            await refreshManualClassSizes();
        }
        if (finalTab === 'support') {
            await loadSupportReferences();
            await refreshSupport();
            await refreshSupportDocuments();
            await refreshSupportReadiness();
            resetSupportStatusForm();
            resetSupportIupForm();
            resetSupportDocumentForm();
        }
    } catch (error) {
        printImportResult({ error: error.message });
    }
})();
