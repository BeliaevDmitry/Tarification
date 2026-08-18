const ui = {
    tabs: Array.from(document.querySelectorAll('[data-contingent-tab]')),
    panes: Array.from(document.querySelectorAll('[data-contingent-pane]')),
    fileInput: document.getElementById('contingent-file'),
    importBtn: document.getElementById('contingent-import-btn'),
    importResult: document.getElementById('contingent-import-result'),
    copyMesScriptBtn: document.getElementById('contingent-copy-mes-script-btn'),
    downloadMesScriptBtn: document.getElementById('contingent-download-mes-script-btn'),
    mesScriptResult: document.getElementById('contingent-mes-script-result'),
    openMismatchesBtn: document.getElementById('contingent-open-mismatches-btn'),
    mismatchTabCount: document.getElementById('contingent-mismatch-tab-count'),
    mismatchSnapshot: document.getElementById('contingent-mismatch-snapshot'),
    mismatchRefreshBtn: document.getElementById('contingent-mismatch-refresh-btn'),
    mismatchSummary: document.getElementById('contingent-mismatch-summary'),
    mismatchBody: document.getElementById('contingent-mismatch-body'),
    mismatchDialog: document.getElementById('contingent-mismatch-dialog'),
    mismatchForm: document.getElementById('contingent-mismatch-form'),
    mismatchDialogPerson: document.getElementById('contingent-mismatch-dialog-person'),
    mismatchDialogClose: document.getElementById('contingent-mismatch-dialog-close'),
    mismatchCancelBtn: document.getElementById('contingent-mismatch-cancel-btn'),
    mismatchRowId: document.getElementById('contingent-mismatch-row-id'),
    mismatchStudentField: document.getElementById('contingent-mismatch-student-field'),
    mismatchStudentSearch: document.getElementById('contingent-mismatch-student-search'),
    mismatchStudent: document.getElementById('contingent-mismatch-student'),
    mismatchStudentHint: document.getElementById('contingent-mismatch-student-hint'),
    mismatchPlacementField: document.getElementById('contingent-mismatch-placement-field'),
    mismatchPlacement: document.getElementById('contingent-mismatch-placement'),
    mismatchDialogMessage: document.getElementById('contingent-mismatch-dialog-message'),
    snapshotDateSelect: document.getElementById('contingent-snapshot-date'),
    statsRefreshBtn: document.getElementById('contingent-stats-refresh-btn'),
    statsExportBtn: document.getElementById('contingent-stats-export-btn'),
    statsViewMode: document.getElementById('contingent-stats-view-mode'),
    statsTable: document.getElementById('contingent-stats-table'),
    statsSummary: document.getElementById('contingent-stats-summary'),
    kindergartenSummary: document.getElementById('contingent-kindergarten-summary'),
    kindergartenTable: document.getElementById('contingent-kindergarten-table'),
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
    supportIupClearBtn: document.getElementById('support-iup-clear-btn'),
    supportIupOrderTemplate: document.getElementById('support-iup-order-template'),
    supportIupDocumentOrderNumber: document.getElementById('support-iup-document-order-number'),
    supportIupDocumentOrderDate: document.getElementById('support-iup-document-order-date'),
    supportIupOrderGender: document.getElementById('support-iup-order-gender'),
    supportIupOrderStudentName: document.getElementById('support-iup-order-student-name'),
    supportIupOrderEducationForm: document.getElementById('support-iup-order-education-form'),
    supportIupOrderMedicalNumber: document.getElementById('support-iup-order-medical-number'),
    supportIupOrderMedicalDate: document.getElementById('support-iup-order-medical-date'),
    supportIupOrderMedicalOrganization: document.getElementById('support-iup-order-medical-organization'),
    supportIupOrderPedNumber: document.getElementById('support-iup-order-ped-number'),
    supportIupOrderPedDate: document.getElementById('support-iup-order-ped-date'),
    supportIupOrderPpkNumber: document.getElementById('support-iup-order-ppk-number'),
    supportIupOrderPpkDate: document.getElementById('support-iup-order-ppk-date'),
    supportIupOrderPreviousNumber: document.getElementById('support-iup-order-previous-number'),
    supportIupOrderPreviousDate: document.getElementById('support-iup-order-previous-date'),
    supportIupOrderCoordinator: document.getElementById('support-iup-order-coordinator'),
    supportIupOrderEjournalAdmin: document.getElementById('support-iup-order-ejournal-admin'),
    supportIupOrderEnrollmentAdmin: document.getElementById('support-iup-order-enrollment-admin'),
    supportIupOrderControlOfficer: document.getElementById('support-iup-order-control-officer'),
    supportIupOrderExecutor: document.getElementById('support-iup-order-executor'),
    supportIupOrderDirector: document.getElementById('support-iup-order-director'),
    supportIupOrderDownloadBtn: document.getElementById('support-iup-order-download-btn'),
    supportIupOrderDownloadGroupBtn: document.getElementById('support-iup-order-download-group-btn'),
    supportIupOrderMessage: document.getElementById('support-iup-order-message')
};

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
let currentStats = null;
let currentManualRows = [];
let currentSupportSummary = null;
let currentSupportDocuments = [];
let supportReferences = { students: [], curriculum: [], teachers: [] };
let currentCertificates = [];
let certificateNosologies = [];
let certificateSpecialists = [];
let currentSnapshots = [];
let currentMismatchData = { rows: [], studentOptions: [], placementOptions: [] };
let currentMismatchRow = null;

const certificateUi = {
    editor: document.getElementById('certificate-editor'),
    id: document.getElementById('certificate-id'),
    student: document.getElementById('certificate-student'),
    type: document.getElementById('certificate-type'),
    formField: document.getElementById('certificate-form-field'),
    form: document.getElementById('certificate-form'),
    validFromField: document.getElementById('certificate-valid-from-field'),
    validFrom: document.getElementById('certificate-valid-from'),
    validToField: document.getElementById('certificate-valid-to-field'),
    validTo: document.getElementById('certificate-valid-to'),
    nosologyFields: document.getElementById('certificate-nosology-fields'),
    nosologyLetter: document.getElementById('certificate-nosology-letter'),
    nosologyMajor: document.getElementById('certificate-nosology-major'),
    nosologyMinor: document.getElementById('certificate-nosology-minor'),
    mseFields: document.getElementById('certificate-mse-fields'),
    cpmpcFields: document.getElementById('certificate-cpmpc-fields'),
    recommendationFields: document.getElementById('certificate-recommendation-fields'),
    recommendationStage: document.getElementById('certificate-recommendation-stage'),
    recommendationProgram: document.getElementById('certificate-recommendation-program'),
    correctionFields: document.getElementById('certificate-correction-fields'),
    number: document.getElementById('certificate-number'),
    educationStage: document.getElementById('certificate-education-stage'),
    educationProgram: document.getElementById('certificate-education-program'),
    prolongationAvailable: document.getElementById('certificate-prolongation-available'),
    prolongationPanel: document.getElementById('certificate-prolongation-panel'),
    prolongationUsed: document.getElementById('certificate-prolongation-used'),
    prolongedGrade: document.getElementById('certificate-prolonged-grade'),
    prolongedYear: document.getElementById('certificate-prolonged-year'),
    prolongationDetails: Array.from(document.querySelectorAll('[data-certificate-prolongation-details]')),
    ipraPresent: document.getElementById('certificate-ipra-present'),
    dateHint: document.getElementById('certificate-date-hint'),
    directionBody: document.getElementById('certificate-direction-body'),
    addDirectionBtn: document.getElementById('certificate-add-direction-btn'),
    openSpecialistsBtn: document.getElementById('certificate-open-specialists-btn'),
    attachmentField: document.getElementById('certificate-attachment-field'),
    file: document.getElementById('certificate-file'),
    saveBtn: document.getElementById('certificate-save-btn'),
    clearBtn: document.getElementById('certificate-clear-btn'),
    refreshBtn: document.getElementById('certificate-refresh-btn'),
    message: document.getElementById('certificate-message'),
    table: document.getElementById('certificate-table'),
    nosologyDirectoryBtn: document.getElementById('certificate-nosology-directory-btn'),
    nosologyBackBtn: document.getElementById('certificate-nosology-back-btn'),
    nosologyId: document.getElementById('certificate-nosology-id'),
    nosologyCode: document.getElementById('certificate-nosology-code'),
    nosologyActive: document.getElementById('certificate-nosology-active'),
    nosologySaveBtn: document.getElementById('certificate-nosology-save-btn'),
    nosologyTable: document.getElementById('certificate-nosology-table'),
    specialistDirectoryBtn: document.getElementById('certificate-specialist-directory-btn'),
    specialistDialog: document.getElementById('certificate-specialist-dialog'),
    specialistName: document.getElementById('certificate-specialist-name'),
    specialistSaveBtn: document.getElementById('certificate-specialist-save-btn'),
    specialistTable: document.getElementById('certificate-specialist-table')
};

function stageClassSummary(stats) {
    return `НОО: ${Number(stats?.totalClassesNoo || 0)}; ООО: ${Number(stats?.totalClassesOoo || 0)}; СОО: ${Number(stats?.totalClassesSoo || 0)}`;
}

function contingentPermissions() {
    const permissions = window.tarificationTabPermissions || {};
    if (window.tarificationAuth?.admin) {
        return { canImportView: true, canImportEdit: true, canStatsView: true, canManualView: true, canSupportView: true };
    }
    return {
        canImportView: Boolean(permissions.CONTINGENT_IMPORT?.canView),
        canImportEdit: Boolean(permissions.CONTINGENT_IMPORT?.canEdit || permissions.CONTINGENT_STATS?.canEdit),
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
        const allowed = (tabName === 'import' || tabName === 'mismatches')
            ? canImportView
            : (tabName === 'manual'
                ? canManualView
                : ((tabName === 'support' || tabName === 'nosologies') ? canSupportView : canStatsView));
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

async function downloadGeneratedDocument(path, payload, fallbackName) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        const text = await response.text();
        let error = null;
        try { error = text ? JSON.parse(text) : null; } catch { error = null; }
        throw new Error(error?.message || error?.error || text || `HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const encodedName = response.headers.get('Content-Disposition')?.split("filename*=UTF-8''")[1];
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = encodedName ? decodeURIComponent(encodedName) : fallbackName;
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
    if (value?.error) {
        ui.importResult.textContent = `Ошибка: ${value.error}`;
        ui.openMismatchesBtn.hidden = true;
        return;
    }
    const format = value?.importFormat === 'MES_EXTENDED_CSV'
        ? 'расширенная CSV-выгрузка МЭШ'
        : value?.importFormat === 'COMPACT'
            ? 'простой контингент (ФИО + класс/группа)'
            : 'выгрузка АИС';
    ui.importResult.textContent = [
        `Формат: ${format}`,
        `Загружено: ${Number(value?.importedStudents || 0)}`,
        `В школьных классах: ${Number(value?.schoolStudents || 0)}`,
        `В детском саду: ${Number(value?.kindergartenStudents || 0)}`,
        `Вне класса/детского сада: ${Number(value?.unassignedStudents || 0)}`,
        `Связано с существующими карточками: ${Number(value?.linkedStudents || 0)}`,
        `Создано новых карточек: ${Number(value?.createdStudentProfiles || 0)}`,
        `Требуют ручного сопоставления: ${Number(value?.ambiguousStudents || 0)}`,
        `Пропущено строк: ${Number(value?.skippedRows || 0)}`
    ].join('\n');
    const mismatches = Number(value?.mismatchCount || 0);
    ui.openMismatchesBtn.hidden = mismatches < 1;
    ui.openMismatchesBtn.textContent = `Открыть нестыковки импорта (${mismatches})`;
    ui.openMismatchesBtn.dataset.snapshotId = value?.snapshotId || '';
}

async function loadMesExportScript() {
    const response = await fetch('/mes-contingent-export.js', { cache: 'no-store' });
    if (!response.ok) throw new Error(`Не удалось получить скрипт (${response.status})`);
    return response.text();
}

async function copyMesExportScript() {
    const script = await loadMesExportScript();
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(script);
    } else {
        const field = document.createElement('textarea');
        field.value = script;
        field.setAttribute('readonly', '');
        field.style.position = 'fixed';
        field.style.opacity = '0';
        document.body.appendChild(field);
        field.select();
        const copied = document.execCommand('copy');
        field.remove();
        if (!copied) throw new Error('Браузер запретил копирование. Используйте кнопку «Скачать скрипт».');
    }
    ui.mesScriptResult.textContent = 'Скрипт скопирован. Перейдите в Console открытого МЭШ, вставьте его и нажмите Enter.';
}

async function downloadMesExportScript() {
    const script = await loadMesExportScript();
    const url = URL.createObjectURL(new Blob([script], { type: 'text/javascript;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'MES_расширенная_выгрузка_контингента.js';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    ui.mesScriptResult.textContent = 'Скрипт скачан. Откройте файл, скопируйте его содержимое и запустите в Console МЭШ.';
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

function renderKindergartenStats(stats) {
    const groups = stats?.kindergartenGroups || [];
    const total = Number(stats?.totalKindergartenChildren || 0);
    const unassigned = Number(stats?.totalUnassignedChildren || 0);
    ui.kindergartenSummary.textContent = `Групп/форм: ${groups.length}. Детей: ${total}.`
        + (unassigned > 0 ? ` Вне класса или детского сада: ${unassigned}.` : '');
    const rows = groups.map((group) => `
        <tr><td>${esc(group.groupName)}</td><td>${esc(group.students || 0)}</td></tr>
    `).join('');
    const unassignedRow = unassigned > 0
        ? `<tr><th>Вне класса/детского сада</th><th>${esc(unassigned)}</th></tr>`
        : '';
    ui.kindergartenTable.innerHTML = `
        <thead><tr><th>Группа / форма</th><th>Детей</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="2" class="muted">Дошкольных групп в снимке нет.</td></tr>'}
        <tr><th>ИТОГО ДЕТСКИЙ САД</th><th>${esc(total)}</th></tr>${unassignedRow}</tbody>`;
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
    const previousMismatchSnapshot = ui.mismatchSnapshot?.value || '';
    const snapshots = await api('/api/contingent/snapshots');
    currentSnapshots = snapshots || [];
    ui.snapshotDateSelect.innerHTML = '';
    snapshots.forEach((snapshot) => {
        const option = document.createElement('option');
        option.value = snapshot.snapshotDate;
        option.textContent = `${snapshot.snapshotDate} (импорт: ${String(snapshot.importedAt || '').replace('T', ' ').slice(0, 16)})`;
        ui.snapshotDateSelect.appendChild(option);
    });
    if (ui.mismatchSnapshot) {
        ui.mismatchSnapshot.innerHTML = snapshots.map((snapshot) => {
            const format = snapshot.importFormat === 'MES_EXTENDED_CSV'
                ? 'Расширенный МЭШ'
                : snapshot.importFormat === 'COMPACT'
                    ? 'Простой'
                    : 'АИС';
            return `<option value="${esc(snapshot.id)}">${esc(snapshot.snapshotDate)} · ${esc(format)} · ${esc(snapshot.sourceFileName)}</option>`;
        }).join('');
        if (previousMismatchSnapshot && snapshots.some((snapshot) => String(snapshot.id) === previousMismatchSnapshot)) {
            ui.mismatchSnapshot.value = previousMismatchSnapshot;
        }
    }
}

const mismatchTypeLabel = (type) => ({
    OUTSIDE_ORGANIZATION: 'Вне ОО',
    AMBIGUOUS_IDENTITY: 'Неясная карточка',
    UNKNOWN_CLASS: 'Неизвестный класс',
    SKIPPED_ROW: 'Строка пропущена'
}[type] || type || 'Проблема');

function renderMismatches(data) {
    currentMismatchData = data || { rows: [], studentOptions: [], placementOptions: [] };
    const rows = currentMismatchData.rows || [];
    const total = Number(currentMismatchData.total || rows.length);
    ui.mismatchTabCount.textContent = total ? `(${total})` : '';
    if (ui.openMismatchesBtn.dataset.snapshotId === String(currentMismatchData.snapshotId || '')) {
        ui.openMismatchesBtn.hidden = total < 1;
        ui.openMismatchesBtn.textContent = `Открыть нестыковки импорта (${total})`;
    }
    ui.mismatchSummary.textContent = currentMismatchData.snapshotId
        ? `Выгрузка от ${currentMismatchData.snapshotDate}: всего ${total}; «Вне ОО» — ${Number(currentMismatchData.outsideOrganization || 0)}; неясные карточки — ${Number(currentMismatchData.ambiguousIdentity || 0)}; пропущенные строки — ${Number(currentMismatchData.skippedRows || 0)}; неизвестные классы — ${Number(currentMismatchData.unknownClasses || 0)}.`
        : 'Выгрузки контингента пока нет.';
    if (!rows.length) {
        ui.mismatchBody.innerHTML = '<tr><td colspan="6" class="muted">Нестыковок нет ✅</td></tr>';
        return;
    }
    const canEdit = contingentPermissions().canImportEdit;
    ui.mismatchBody.innerHTML = rows.map((row) => {
        const birthDate = row.birthDate ? `<div class="muted">${esc(row.birthDate)}</div>` : '';
        const raw = row.rawPayload
            ? `<details class="contingent-mismatch-raw"><summary>Исходные данные</summary><pre>${esc(row.rawPayload)}</pre></details>`
            : '';
        const action = row.canResolve && canEdit
            ? `<button type="button" class="secondary" data-resolve-mismatch="${esc(row.contingentStudentId)}">Сопоставить</button>`
            : (row.canResolve ? '<span class="muted">Только просмотр</span>' : '<span class="muted">Исправить файл</span>');
        return `
            <tr class="contingent-mismatch-row contingent-mismatch-${esc(String(row.type || '').toLowerCase())}">
                <td><span class="table-badge">${esc(mismatchTypeLabel(row.type))}</span></td>
                <td>${row.sourceRowNumber ? esc(row.sourceRowNumber) : '—'}</td>
                <td><strong>${esc(row.fullName || 'ФИО не указано')}</strong>${birthDate}</td>
                <td>${esc(row.currentPlacement || 'Не указано')}</td>
                <td>${esc(row.message)}${raw}</td>
                <td>${action}</td>
            </tr>`;
    }).join('');
}

async function refreshMismatches(snapshotId = null) {
    const selectedId = snapshotId || ui.mismatchSnapshot?.value;
    const query = selectedId ? `?snapshotId=${encodeURIComponent(selectedId)}` : '';
    const data = await api(`/api/contingent/import-mismatches${query}`);
    renderMismatches(data);
    if (data?.snapshotId && ui.mismatchSnapshot) {
        ui.mismatchSnapshot.value = String(data.snapshotId);
    }
    return data;
}

function studentOptionLabel(option) {
    return [option.fullName, option.birthDate, option.currentPlacement].filter(Boolean).join(' · ');
}

function fillMismatchStudentOptions(search = '') {
    const selected = ui.mismatchStudent.value;
    const needle = String(search || '').trim().toLocaleLowerCase('ru-RU');
    const options = (currentMismatchData.studentOptions || []).filter((option) =>
        !needle || studentOptionLabel(option).toLocaleLowerCase('ru-RU').includes(needle)
    );
    const current = (currentMismatchData.studentOptions || []).find((option) =>
        String(option.id) === String(currentMismatchRow?.currentStudentId || '')
    );
    const visible = current && !options.some((option) => String(option.id) === String(current.id))
        ? [current, ...options]
        : options;
    ui.mismatchStudent.innerHTML = '<option value="">Выберите карточку ребёнка</option>'
        + visible.map((option) => `<option value="${esc(option.id)}">${esc(studentOptionLabel(option))}</option>`).join('');
    const target = selected || currentMismatchRow?.currentStudentId || '';
    ui.mismatchStudent.value = String(target);
}

function openMismatchDialog(rowId) {
    const row = (currentMismatchData.rows || []).find((item) =>
        Number(item.contingentStudentId) === Number(rowId)
    );
    if (!row) return;
    currentMismatchRow = row;
    ui.mismatchRowId.value = row.contingentStudentId || '';
    ui.mismatchDialogPerson.textContent = `${row.fullName || 'ФИО не указано'}${row.birthDate ? `, ${row.birthDate}` : ''}. Сейчас: ${row.currentPlacement || 'не указано'}.`;
    ui.mismatchDialogMessage.textContent = row.message || '';
    ui.mismatchStudentSearch.value = '';
    fillMismatchStudentOptions();
    const studentLocked = Boolean(row.currentStudentId);
    ui.mismatchStudent.disabled = studentLocked;
    ui.mismatchStudentSearch.disabled = studentLocked;
    ui.mismatchStudentField.style.display = row.requiresStudent || studentLocked ? '' : 'none';
    ui.mismatchStudentHint.textContent = studentLocked
        ? 'Карточка уже связана; здесь меняется только класс или группа.'
        : 'Выберите существующую постоянную карточку. Новая карточка из неоднозначной строки автоматически не создаётся.';

    const placementOptions = currentMismatchData.placementOptions || [];
    ui.mismatchPlacement.innerHTML = '<option value="">Выберите класс или группу</option>'
        + placementOptions.map((placement) => `<option value="${esc(placement)}">${esc(placement)}</option>`).join('');
    if (!row.requiresPlacement && row.currentPlacement) {
        if (!placementOptions.includes(row.currentPlacement)) {
            ui.mismatchPlacement.insertAdjacentHTML('beforeend', `<option value="${esc(row.currentPlacement)}">${esc(row.currentPlacement)}</option>`);
        }
        ui.mismatchPlacement.value = row.currentPlacement;
    }
    ui.mismatchPlacementField.style.display = row.requiresPlacement ? '' : 'none';
    ui.mismatchDialog.showModal();
}

async function saveMismatchResolution(event) {
    event.preventDefault();
    if (!currentMismatchRow) return;
    ui.mismatchDialogMessage.textContent = 'Сохраняю сопоставление…';
    try {
        const data = await api('/api/contingent/import-mismatches/resolve', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contingentStudentId: Number(ui.mismatchRowId.value),
                studentId: ui.mismatchStudent.value ? Number(ui.mismatchStudent.value) : null,
                className: ui.mismatchPlacement.value || currentMismatchRow.currentPlacement || ''
            })
        });
        ui.mismatchDialog.close();
        currentMismatchRow = null;
        renderMismatches(data);
        try {
            await refreshStats();
        } catch (statsError) {
            ui.mismatchSummary.textContent += ` Численность не обновилась автоматически: ${statsError.message}`;
        }
    } catch (error) {
        ui.mismatchDialogMessage.textContent = `Ошибка: ${error.message}`;
    }
}

async function refreshStats() {
    const selectedDate = ui.snapshotDateSelect.value;
    const query = selectedDate ? `?snapshotDate=${encodeURIComponent(selectedDate)}` : '';
    currentStats = await api(`/api/contingent/stats${query}`);
    const totalClasses = (currentStats?.parallelTotals || []).reduce((sum, x) => sum + Number(x.totalClasses || 0), 0);
    ui.statsSummary.textContent = `Данные по состоянию на ${currentStats.snapshotDate}. В файле: ${Number(currentStats.totalImportedChildren || 0)} детей; в школьных классах: ${Number(currentStats.totalSchoolChildren || 0)}; в детском саду: ${Number(currentStats.totalKindergartenChildren || 0)}. Всего классов: ${totalClasses} (${stageClassSummary(currentStats)}). Для классов без численности применяется значение 30 человек.`;
    renderStatsTable(currentStats);
    renderKindergartenStats(currentStats);
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
        <td><input data-iup-field="classHours" type="number" min="0" step="1" value="${esc(subject.classHours ?? 0)}"></td>
        <td><input data-iup-field="individualHours" type="number" min="0" step="1" value="${esc(subject.individualHours ?? 0)}"></td>
        <td><select data-iup-field="groupNameEducationalPlan">${supportGroupOptions(subject.curriculumEntryId, subject.groupNameEducationalPlan)}</select></td>
        <td><select data-iup-field="teacherId">${supportTeacherOptions(assignment.teacherId)}</select></td>
        <td><input data-iup-field="teacherHours" type="number" min="0" step="1" value="${esc(assignment.hoursPerWeek ?? '')}"></td>
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
    resetSupportIupOrderForm();
}

function resetSupportIupOrderForm() {
    if (!ui.supportIupOrderTemplate) return;
    ui.supportIupOrderTemplate.value = 'INDIVIDUAL_IUP';
    ui.supportIupDocumentOrderNumber.value = '';
    ui.supportIupDocumentOrderDate.value = '';
    ui.supportIupOrderGender.value = '';
    ui.supportIupOrderStudentName.value = '';
    ui.supportIupOrderStudentName.placeholder = 'Например: Иванова Ивана Ивановича';
    ui.supportIupOrderEducationForm.value = '';
    ui.supportIupOrderMedicalNumber.value = '';
    ui.supportIupOrderMedicalDate.value = '';
    ui.supportIupOrderMedicalOrganization.value = '';
    ui.supportIupOrderPedNumber.value = '';
    ui.supportIupOrderPedDate.value = '';
    ui.supportIupOrderPpkNumber.value = '';
    ui.supportIupOrderPpkDate.value = '';
    ui.supportIupOrderPreviousNumber.value = '';
    ui.supportIupOrderPreviousDate.value = '';
    ui.supportIupOrderCoordinator.value = '';
    ui.supportIupOrderEjournalAdmin.value = '';
    ui.supportIupOrderEnrollmentAdmin.value = '';
    ui.supportIupOrderControlOfficer.value = '';
    ui.supportIupOrderExecutor.value = '';
    ui.supportIupOrderDirector.value = '';
    ui.supportIupOrderMessage.textContent = '';
}

function fillSupportIupOrderDefaults(plan) {
    if (!ui.supportIupOrderTemplate) return;
    const student = (supportReferences.students || []).find((item) =>
        Number(item.studentId) === Number(plan.studentId)
    );
    ui.supportIupDocumentOrderNumber.value = plan.orderNumber || '';
    ui.supportIupDocumentOrderDate.value = plan.orderDate || '';
    ui.supportIupOrderStudentName.value = '';
    ui.supportIupOrderStudentName.placeholder = student?.fullName
        ? `ФИО из контингента: ${student.fullName}. Введите форму для приказа`
        : 'Введите ФИО в форме, необходимой для приказа';
}

function supportIupOrderPayload(planIds, templateType) {
    return {
        templateType,
        iupPlanIds: planIds,
        orderNumber: ui.supportIupDocumentOrderNumber.value || null,
        orderDate: ui.supportIupDocumentOrderDate.value || null,
        studentGender: templateType === 'OVZ_GROUP' ? null : (ui.supportIupOrderGender.value || null),
        studentNameForOrder: templateType === 'OVZ_GROUP' ? null : (ui.supportIupOrderStudentName.value || null),
        educationLevelAndForm: ui.supportIupOrderEducationForm.value || null,
        medicalConclusionNumber: ui.supportIupOrderMedicalNumber.value || null,
        medicalConclusionDate: ui.supportIupOrderMedicalDate.value || null,
        medicalOrganization: ui.supportIupOrderMedicalOrganization.value || null,
        pedagogicalCouncilProtocolNumber: ui.supportIupOrderPedNumber.value || null,
        pedagogicalCouncilProtocolDate: ui.supportIupOrderPedDate.value || null,
        ppkProtocolNumber: ui.supportIupOrderPpkNumber.value || null,
        ppkProtocolDate: ui.supportIupOrderPpkDate.value || null,
        previousOrderNumber: ui.supportIupOrderPreviousNumber.value || null,
        previousOrderDate: ui.supportIupOrderPreviousDate.value || null,
        responsibleCoordinator: ui.supportIupOrderCoordinator.value || null,
        electronicJournalAdministrator: ui.supportIupOrderEjournalAdmin.value || null,
        enrollmentAdministrator: ui.supportIupOrderEnrollmentAdmin.value || null,
        controlOfficer: ui.supportIupOrderControlOfficer.value || null,
        executor: ui.supportIupOrderExecutor.value || null,
        directorName: ui.supportIupOrderDirector.value || null
    };
}

async function generateSupportIupOrder(groupOrder) {
    let templateType = ui.supportIupOrderTemplate.value || 'INDIVIDUAL_IUP';
    let planIds;
    if (groupOrder) {
        templateType = 'OVZ_GROUP';
        ui.supportIupOrderTemplate.value = templateType;
        planIds = (currentSupportSummary?.registerRows || [])
            .filter((item) => item.hasIup && ['K2', 'K3'].includes(item.underlyingCategory))
            .map((item) => Number(item.iupPlanId))
            .filter((value) => Number.isFinite(value) && value > 0);
        if (!planIds.length) {
            throw new Error('На выбранную дату нет действующих ИУП у детей К2/К3.');
        }
    } else {
        const planId = Number(ui.supportIupId.value || 0);
        if (!planId) {
            throw new Error('Сначала сохраните ИУП или откройте существующий ИУП из реестра.');
        }
        planIds = [planId];
    }
    ui.supportIupOrderMessage.textContent = 'Формирую приказ Word…';
    await downloadGeneratedDocument(
        '/api/contingent/special-support/iup-orders/generate',
        supportIupOrderPayload(planIds, templateType),
        'Приказ_ИУП.docx'
    );
    ui.supportIupOrderMessage.textContent = 'Приказ сформирован. После подписания его можно принять в реестр документов как «Приказ по ИУП».';
}

const certificateTypeLabels = {
    MSE_CERTIFICATE: 'Справка МСЭ',
    CPMPC_CONCLUSION: 'Заключение ЦМПК',
    CPMPC_RECOMMENDATION: 'Рекомендация ЦМПК'
};

const certificateStageLabels = { DO: 'ДО', NOO: 'НОО', OOO: 'ООО', SOO: 'СОО' };
const recommendationProgramLabels = {
    DO: 'Основная образовательная программа дошкольного образования',
    NOO: 'Основная образовательная программа начального общего образования',
    OOO: 'Основная образовательная программа основного общего образования',
    SOO: 'Основная образовательная программа среднего общего образования'
};
function certificateStudentOptions() {
    const rows = supportReferences.students || [];
    return '<option value="">Выберите ребёнка</option>' + rows.map((student) => {
        const details = [student.birthDate ? `д.р. ${student.birthDate}` : '', student.recordNumber ? `ФК ${student.recordNumber}` : '']
            .filter(Boolean).join(', ');
        return `<option value="${esc(student.studentId)}">${esc(student.className)} — ${esc(student.fullName)}${details ? ` (${esc(details)})` : ''}</option>`;
    }).join('');
}

function certificateSpecialistOptions(selectedId) {
    return '<option value="">Выберите специалиста</option>' + certificateSpecialists
        .filter((item) => item.active)
        .map((item) => `<option value="${esc(item.id)}" ${Number(item.id) === Number(selectedId) ? 'selected' : ''}>${esc(item.name)}</option>`)
        .join('');
}

function addCertificateDirection(direction = {}) {
    if (!certificateUi.directionBody) return;
    const row = document.createElement('tr');
    row.innerHTML = `
        <td><select data-certificate-direction="specialistId">${certificateSpecialistOptions(direction.specialistId)}</select></td>
        <td><textarea data-certificate-direction="tasks" rows="2" placeholder="Задачи специалиста">${esc(direction.tasks || '')}</textarea></td>
        <td><button type="button" class="secondary" data-certificate-remove-direction>Удалить</button></td>`;
    certificateUi.directionBody.appendChild(row);
}

function defaultCertificateDirection() {
    if (!['CPMPC_CONCLUSION', 'CPMPC_RECOMMENDATION'].includes(certificateUi.type?.value)
        || certificateUi.directionBody?.children.length) return;
    const social = certificateSpecialists.find((item) => item.name === 'Социальный педагог');
    addCertificateDirection({ specialistId: social?.id });
}

function fillCertificateYears() {
    if (!certificateUi.prolongedGrade || !certificateUi.prolongedYear) return;
    if (certificateUi.prolongedGrade.options.length && certificateUi.prolongedYear.options.length) return;
    certificateUi.prolongedGrade.innerHTML = '<option value="">Выберите</option>'
        + Array.from({ length: 11 }, (_, index) => `<option value="${index + 1}">${index + 1} класс</option>`).join('');
    const selectedYear = sessionStorage.getItem('tarification.academicYear') || '';
    const start = Number(selectedYear.slice(0, 4)) || new Date().getFullYear();
    certificateUi.prolongedYear.innerHTML = '<option value="">Выберите</option>'
        + Array.from({ length: 8 }, (_, index) => {
            const year = start - 2 + index;
            const value = `${year}/${year + 1}`;
            return `<option value="${value}">${value}</option>`;
        }).join('');
}

function certificateNosologyCode() {
    const letter = certificateUi.nosologyLetter.value;
    const major = certificateUi.nosologyMajor.value;
    const minor = certificateUi.nosologyMinor.value;
    return major !== '' && minor !== '' ? `${letter}${major}.${minor}` : null;
}

function setCertificateNosologyCode(code) {
    const match = String(code || '').toUpperCase().match(/^([ИО])(\d)\.(\d)$/);
    certificateUi.nosologyLetter.value = match?.[1] || 'И';
    certificateUi.nosologyMajor.value = match?.[2] || '';
    certificateUi.nosologyMinor.value = match?.[3] || '';
}

function selectedCertificateStudent() {
    const id = Number(certificateUi.student?.value || 0);
    return (supportReferences.students || []).find((item) => Number(item.studentId) === id) || null;
}

function certificateExpectedEndDate() {
    const stage = certificateUi.educationStage?.value;
    if (!stage || stage === 'DO' || certificateUi.prolongationAvailable?.value === 'true') return null;
    const grade = Number(String(selectedCertificateStudent()?.className || '').match(/^\s*(\d{1,2})/)?.[1] || 0);
    const terminal = { NOO: 4, OOO: 9, SOO: 11 }[stage];
    const selectedYear = sessionStorage.getItem('tarification.academicYear') || '';
    const endYear = Number(selectedYear.slice(5, 9));
    if (!grade || !terminal || !endYear || grade > terminal) return null;
    return `${endYear + terminal - grade}-08-31`;
}

function updateCertificateDateHint() {
    if (!certificateUi.dateHint) return;
    const expected = certificateExpectedEndDate();
    if (certificateUi.educationStage?.value === 'DO') {
        certificateUi.dateHint.textContent = 'Для ДО автоматически проверить дату окончания нельзя.';
    } else if (certificateUi.prolongationAvailable?.value === 'true') {
        certificateUi.dateHint.textContent = 'Есть возможность пролонгирования — дата окончания может отличаться от стандартной.';
    } else if (expected) {
        certificateUi.dateHint.textContent = `Для выбранного уровня и текущего класса ожидаемая дата окончания: ${expected}.`;
    } else {
        certificateUi.dateHint.textContent = 'Выберите ребёнка и уровень образования для проверки даты окончания.';
    }
}

function updateCertificateAcceptedForms(cpmpc) {
    if (!certificateUi.form) return;
    const current = certificateUi.form.value;
    const forms = cpmpc
        ? [['ORIGINAL', 'Оригинал'], ['ELECTRONIC_COPY', 'Электронная копия']]
        : [['COPY', 'Копия']];
    certificateUi.form.innerHTML = forms
        .map(([value, label]) => `<option value="${value}">${label}</option>`)
        .join('');
    certificateUi.form.value = forms.some(([value]) => value === current)
        ? current
        : (cpmpc ? 'ORIGINAL' : 'COPY');
}

function updateRecommendationProgram() {
    if (!certificateUi.recommendationProgram) return;
    certificateUi.recommendationProgram.value =
        recommendationProgramLabels[certificateUi.recommendationStage?.value] || '';
}

function updateCertificateFormVisibility() {
    if (!certificateUi.type) return;
    const cpmpc = certificateUi.type.value === 'CPMPC_CONCLUSION';
    const recommendation = certificateUi.type.value === 'CPMPC_RECOMMENDATION';
    updateCertificateAcceptedForms(cpmpc);
    certificateUi.formField.hidden = recommendation;
    certificateUi.validFromField.hidden = recommendation;
    certificateUi.validToField.hidden = recommendation;
    certificateUi.attachmentField.hidden = recommendation;
    certificateUi.mseFields.hidden = cpmpc || recommendation;
    certificateUi.cpmpcFields.hidden = !cpmpc;
    certificateUi.recommendationFields.hidden = !recommendation;
    certificateUi.correctionFields.hidden = !(cpmpc || recommendation);
    certificateUi.nosologyFields.hidden = !cpmpc;
    if (!cpmpc) setCertificateNosologyCode(null);
    const prolongation = cpmpc && certificateUi.prolongationAvailable.value === 'true';
    certificateUi.prolongationPanel.hidden = !prolongation;
    if (!prolongation) certificateUi.prolongationUsed.value = 'false';
    const used = prolongation && certificateUi.prolongationUsed.value === 'true';
    certificateUi.prolongationDetails.forEach((label) => { label.hidden = !used; });
    if (recommendation) {
        certificateUi.validFrom.value = '';
        certificateUi.validTo.value = '';
        certificateUi.file.value = '';
        updateRecommendationProgram();
    }
    if (cpmpc || recommendation) defaultCertificateDirection();
    updateCertificateDateHint();
}

function resetCertificateForm() {
    if (!certificateUi.id) return;
    certificateUi.id.value = '';
    certificateUi.student.value = '';
    certificateUi.type.value = 'MSE_CERTIFICATE';
    certificateUi.form.value = 'COPY';
    certificateUi.validFrom.value = '';
    certificateUi.validTo.value = '';
    setCertificateNosologyCode(null);
    certificateUi.number.value = '';
    certificateUi.educationStage.value = '';
    certificateUi.educationProgram.value = '';
    certificateUi.recommendationStage.value = '';
    certificateUi.recommendationProgram.value = '';
    certificateUi.prolongationAvailable.value = 'false';
    certificateUi.prolongationUsed.value = 'false';
    certificateUi.prolongedGrade.value = '';
    certificateUi.prolongedYear.value = '';
    certificateUi.ipraPresent.value = 'false';
    certificateUi.directionBody.innerHTML = '';
    certificateUi.file.value = '';
    certificateUi.saveBtn.textContent = 'Сохранить документ';
    updateCertificateFormVisibility();
}

function certificateDirectionPayload() {
    return Array.from(certificateUi.directionBody.querySelectorAll('tr')).map((row) => ({
        specialistId: Number(row.querySelector('[data-certificate-direction="specialistId"]')?.value || 0) || null,
        tasks: row.querySelector('[data-certificate-direction="tasks"]')?.value?.trim() || null
    }));
}

async function loadCertificateReferences() {
    const [references, nosologies, specialists] = await Promise.all([
        api('/api/contingent/special-support/references'),
        api('/api/contingent/special-support/nosologies'),
        api('/api/contingent/special-support/correction-specialists')
    ]);
    supportReferences = references || { students: [], curriculum: [], teachers: [] };
    certificateNosologies = nosologies || [];
    certificateSpecialists = specialists || [];
    certificateUi.student.innerHTML = certificateStudentOptions();
    renderCertificateNosologies();
    renderCertificateSpecialists();
}

function certificateAttachmentLink(documentId, attachment) {
    const path = `/api/contingent/special-support/documents/${encodeURIComponent(documentId)}/attachments/${encodeURIComponent(attachment.id)}`;
    const href = window.withAcademicYear ? window.withAcademicYear(path) : path;
    return `<span><a href="${esc(href)}">${esc(attachment.fileName)}</a>
        <small class="muted">(${esc(supportAttachmentSize(attachment.fileSize))})</small>
        <button type="button" class="secondary" data-requires-edit data-certificate-delete-attachment="${esc(attachment.id)}" data-certificate-document-id="${esc(documentId)}">×</button></span>`;
}

function certificateDetails(document) {
    const details = [];
    if (document.documentType === 'MSE_CERTIFICATE') {
        details.push(`Категория: ${supportCategoryLabel(document.derivedCategory)}`);
        details.push(`ИПР/ИПРА: ${document.ipraPresent ? 'да' : 'нет'}`);
    } else {
        details.push(`Уровень: ${certificateStageLabels[document.educationStage] || '—'}`);
        details.push(`Программа: ${document.educationProgram || '—'}`);
        if (document.prolongationAvailable) details.push(`пролонгирование: ${document.prolongationUsed ? 'использовано' : 'возможно'}`);
    }
    if (document.documentType === 'CPMPC_CONCLUSION' && document.nosologyCode) {
        details.push(`нозология ${document.nosologyCode}`);
    }
    return details.join('; ');
}

function renderCertificates() {
    const rows = currentCertificates.map((document) => {
        const recommendation = document.documentType === 'CPMPC_RECOMMENDATION';
        const files = (document.attachments || []).map((item) => certificateAttachmentLink(document.id, item)).join('<br>');
        const directions = (document.correctionDirections || []).map((item) => `${item.specialistName}: ${item.tasks}`).join('; ');
        return `<tr>
            <td>${esc(document.className)}</td><td>${esc(document.studentFullName)}</td>
            <td>${esc(certificateTypeLabels[document.documentType] || document.documentType)}</td>
            <td>${esc(document.documentNumber || '—')}</td><td>${recommendation ? '—' : esc(document.acceptedForm === 'ORIGINAL' ? 'Оригинал' : document.acceptedForm === 'ELECTRONIC_COPY' ? 'Электронная копия' : 'Копия')}</td>
            <td>${recommendation ? '—' : esc(supportDateRange(document.validFrom, document.validTo))}</td><td>${esc(certificateDetails(document))}</td>
            <td>${esc(directions || '—')}</td><td>${esc(document.validityStatus || '')}</td>
            <td>${recommendation ? '—' : (files || '<span class="muted">Нет скана</span>')}</td>
            <td data-requires-edit><button type="button" class="secondary" data-certificate-edit="${esc(document.id)}">Изменить</button>
                <button type="button" class="secondary" data-certificate-delete="${esc(document.id)}">Удалить</button></td>
        </tr>`;
    }).join('');
    certificateUi.table.innerHTML = `<thead><tr><th>Класс</th><th>ФИО</th><th>Документ</th><th>Номер</th><th>Принято</th><th>Срок</th><th>Данные</th><th>Коррекционная работа</th><th>Состояние</th><th>Скан</th><th></th></tr></thead>
        <tbody>${rows || '<tr><td colspan="11" class="muted">Справки пока не внесены.</td></tr>'}</tbody>`;
}

async function refreshCertificates() {
    currentCertificates = await api('/api/contingent/special-support/documents');
    renderCertificates();
    certificateUi.message.textContent = currentCertificates.length ? `Справок: ${currentCertificates.length}.` : 'Справки пока не внесены.';
}

async function saveCertificate() {
    if (!certificateUi.student.value) throw new Error('Выберите ребёнка из контингента');
    const cpmpc = certificateUi.type.value === 'CPMPC_CONCLUSION';
    const recommendation = certificateUi.type.value === 'CPMPC_RECOMMENDATION';
    const mse = certificateUi.type.value === 'MSE_CERTIFICATE';
    if (!recommendation && (!certificateUi.validFrom.value || !certificateUi.validTo.value)) {
        throw new Error('Укажите дату установления и дату окончания');
    }
    if (recommendation && !certificateUi.recommendationStage.value) throw new Error('Выберите уровень образования');
    if (cpmpc && !certificateNosologyCode()) throw new Error('Для заключения ЦМПК обязательно укажите нозологию');
    const expected = certificateExpectedEndDate();
    if (expected && certificateUi.validTo.value !== expected) throw new Error(`Дата окончания для выбранного уровня должна быть ${expected}`);
    const file = certificateUi.file.files?.[0];
    if (file && file.size > 15 * 1024 * 1024) throw new Error('Размер скана не должен превышать 15 МБ');
    const payload = {
        id: Number(certificateUi.id.value || 0) || null,
        studentId: Number(certificateUi.student.value), documentType: certificateUi.type.value,
        acceptedForm: recommendation ? null : certificateUi.form.value,
        validFrom: recommendation ? null : certificateUi.validFrom.value,
        validTo: recommendation ? null : certificateUi.validTo.value,
        nosologyCode: cpmpc ? certificateNosologyCode() : null, documentNumber: cpmpc ? certificateUi.number.value.trim() || null : null,
        educationStage: cpmpc
            ? certificateUi.educationStage.value || null
            : (recommendation ? certificateUi.recommendationStage.value || null : null),
        educationProgram: cpmpc
            ? certificateUi.educationProgram.value || null
            : (recommendation ? certificateUi.recommendationProgram.value || null : null),
        prolongationAvailable: cpmpc && certificateUi.prolongationAvailable.value === 'true',
        prolongationUsed: cpmpc && certificateUi.prolongationUsed.value === 'true',
        prolongedGrade: cpmpc && certificateUi.prolongationUsed.value === 'true' ? Number(certificateUi.prolongedGrade.value || 0) || null : null,
        prolongedAcademicYear: cpmpc && certificateUi.prolongationUsed.value === 'true' ? certificateUi.prolongedYear.value || null : null,
        ipraPresent: mse && certificateUi.ipraPresent.value === 'true',
        correctionDirections: cpmpc || recommendation ? certificateDirectionPayload() : []
    };
    const saved = await api('/api/contingent/special-support/documents', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    if (file) {
        const form = new FormData(); form.append('file', file);
        await api(`/api/contingent/special-support/documents/${encodeURIComponent(saved.id)}/attachments`, { method: 'POST', body: form });
    }
    resetCertificateForm();
    await refreshCertificates();
    certificateUi.message.textContent = 'Документ сохранён.';
}

function editCertificate(id) {
    const document = currentCertificates.find((item) => Number(item.id) === Number(id));
    if (!document) return;
    certificateUi.id.value = document.id;
    certificateUi.student.value = document.studentId || '';
    certificateUi.type.value = document.documentType;
    certificateUi.form.value = document.acceptedForm || 'COPY';
    certificateUi.validFrom.value = document.validFrom || '';
    certificateUi.validTo.value = document.validTo || '';
    setCertificateNosologyCode(document.nosologyCode);
    certificateUi.number.value = document.documentNumber || '';
    certificateUi.educationStage.value = document.educationStage || '';
    certificateUi.educationProgram.value = document.educationProgram || '';
    certificateUi.recommendationStage.value = document.documentType === 'CPMPC_RECOMMENDATION'
        ? document.educationStage || '' : '';
    certificateUi.recommendationProgram.value = document.documentType === 'CPMPC_RECOMMENDATION'
        ? document.educationProgram || '' : '';
    certificateUi.prolongationAvailable.value = String(Boolean(document.prolongationAvailable));
    certificateUi.prolongationUsed.value = String(Boolean(document.prolongationUsed));
    certificateUi.prolongedGrade.value = document.prolongedGrade || '';
    certificateUi.prolongedYear.value = document.prolongedAcademicYear || '';
    certificateUi.ipraPresent.value = String(Boolean(document.ipraPresent));
    certificateUi.directionBody.innerHTML = '';
    (document.correctionDirections || []).forEach(addCertificateDirection);
    certificateUi.file.value = '';
    certificateUi.saveBtn.textContent = 'Сохранить изменения';
    updateCertificateFormVisibility();
    certificateUi.editor.open = true;
    certificateUi.editor.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderCertificateNosologies() {
    if (!certificateUi.nosologyTable) return;
    certificateUi.nosologyTable.innerHTML = `<thead><tr><th>Код К3</th><th>Действует</th><th></th></tr></thead><tbody>${certificateNosologies.map((item) =>
        `<tr><td>${esc(item.code)}</td><td>${item.active ? 'Да' : 'Нет'}</td><td><button type="button" class="secondary" data-certificate-edit-nosology="${esc(item.id)}">Изменить</button></td></tr>`
    ).join('') || '<tr><td colspan="3" class="muted">Справочник пока пуст.</td></tr>'}</tbody>`;
}

function renderCertificateSpecialists() {
    if (!certificateUi.specialistTable) return;
    certificateUi.specialistTable.innerHTML = `<thead><tr><th>Специалист</th><th>Тип</th></tr></thead><tbody>${certificateSpecialists.map((item) =>
        `<tr><td>${esc(item.name)}</td><td>${item.builtIn ? 'Основной' : 'Добавлен вручную'}</td></tr>`
    ).join('')}</tbody>`;
}

const supportDocumentTypeLabels = {
    MSE_CERTIFICATE: 'Справка МСЭ',
    IPR_IPRA: 'ИПР/ИПРА',
    CPMPC_CONCLUSION: 'Заключение ЦПМПК',
    INTERNAL_PPK_PROTOCOL: 'Протокол ППк',
    IOM: 'ИОМ',
    IUP_ORDER: 'Приказ по ИУП',
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
        const wholeHours = (name, label) => {
            const input = row.querySelector(`[data-iup-field="${name}"]`);
            const hours = Number(input?.value || 0);
            if (!Number.isInteger(hours)) {
                input?.focus();
                throw new Error(`${label} должны быть целым числом`);
            }
            return hours;
        };
        return {
            curriculumEntryId,
            subjectName: curriculum?.subjectName || null,
            participationMode: value('participationMode'),
            classHours: wholeHours('classHours', 'Часы с классом'),
            individualHours: wholeHours('individualHours', 'Индивидуальные часы'),
            groupNameEducationalPlan: value('groupNameEducationalPlan') || null,
            teachers: teacherId ? [{
                teacherId,
                hoursPerWeek: wholeHours('teacherHours', 'Часы учителя по ИУП'),
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
    resetSupportIupOrderForm();
    fillSupportIupOrderDefaults(plan);
    ui.supportIupEditor.open = true;
    ui.supportIupEditor.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

certificateUi.type?.addEventListener('change', updateCertificateFormVisibility);
certificateUi.prolongationAvailable?.addEventListener('change', updateCertificateFormVisibility);
certificateUi.prolongationUsed?.addEventListener('change', updateCertificateFormVisibility);
certificateUi.educationStage?.addEventListener('change', updateCertificateDateHint);
certificateUi.recommendationStage?.addEventListener('change', updateRecommendationProgram);
certificateUi.student?.addEventListener('change', updateCertificateDateHint);
certificateUi.addDirectionBtn?.addEventListener('click', () => addCertificateDirection());
certificateUi.openSpecialistsBtn?.addEventListener('click', () => certificateUi.specialistDialog?.showModal());
certificateUi.specialistDirectoryBtn?.addEventListener('click', () => certificateUi.specialistDialog?.showModal());
certificateUi.nosologyDirectoryBtn?.addEventListener('click', () => {
    showTab('nosologies');
    window.location.hash = '#nosologies';
});
certificateUi.nosologyBackBtn?.addEventListener('click', () => {
    showTab('support');
    window.location.hash = '#support';
});
certificateUi.clearBtn?.addEventListener('click', resetCertificateForm);
certificateUi.refreshBtn?.addEventListener('click', () => Promise.all([loadCertificateReferences(), refreshCertificates()])
    .catch((error) => { certificateUi.message.textContent = `Ошибка: ${error.message}`; }));
certificateUi.saveBtn?.addEventListener('click', () => saveCertificate().catch((error) => {
    certificateUi.message.textContent = `Ошибка сохранения: ${error.message}`;
}));
certificateUi.directionBody?.addEventListener('click', (event) => {
    event.target.closest('[data-certificate-remove-direction]')?.closest('tr')?.remove();
});
certificateUi.table?.addEventListener('click', async (event) => {
    const edit = event.target.closest('[data-certificate-edit]');
    if (edit) { editCertificate(edit.dataset.certificateEdit); return; }
    const remove = event.target.closest('[data-certificate-delete]');
    if (remove) {
        if (!window.confirm('Удалить справку, созданный ею статус и прикреплённые сканы?')) return;
        try {
            await api(`/api/contingent/special-support/documents/${encodeURIComponent(remove.dataset.certificateDelete)}`, { method: 'DELETE' });
            resetCertificateForm(); await refreshCertificates();
        } catch (error) { certificateUi.message.textContent = `Ошибка удаления: ${error.message}`; }
        return;
    }
    const attachment = event.target.closest('[data-certificate-delete-attachment]');
    if (attachment) {
        if (!window.confirm('Удалить прикреплённый скан?')) return;
        try {
            await api(`/api/contingent/special-support/documents/${encodeURIComponent(attachment.dataset.certificateDocumentId)}/attachments/${encodeURIComponent(attachment.dataset.certificateDeleteAttachment)}`, { method: 'DELETE' });
            await refreshCertificates();
        } catch (error) { certificateUi.message.textContent = `Ошибка удаления скана: ${error.message}`; }
    }
});
certificateUi.nosologyTable?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-certificate-edit-nosology]');
    if (!button) return;
    const item = certificateNosologies.find((row) => Number(row.id) === Number(button.dataset.certificateEditNosology));
    if (!item) return;
    certificateUi.nosologyId.value = item.id;
    certificateUi.nosologyCode.value = item.code;
    certificateUi.nosologyActive.checked = Boolean(item.active);
});
certificateUi.nosologySaveBtn?.addEventListener('click', async () => {
    try {
        await api('/api/contingent/special-support/nosologies', {
            method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
                id: Number(certificateUi.nosologyId.value || 0) || null,
                code: certificateUi.nosologyCode.value,
                active: certificateUi.nosologyActive.checked
            })
        });
        certificateUi.nosologyId.value = ''; certificateUi.nosologyCode.value = ''; certificateUi.nosologyActive.checked = true;
        certificateNosologies = await api('/api/contingent/special-support/nosologies');
        renderCertificateNosologies(); await refreshCertificates();
    } catch (error) { window.alert(`Ошибка сохранения нозологии: ${error.message}`); }
});
certificateUi.specialistSaveBtn?.addEventListener('click', async () => {
    try {
        await api('/api/contingent/special-support/correction-specialists', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: certificateUi.specialistName.value })
        });
        certificateUi.specialistName.value = '';
        certificateSpecialists = await api('/api/contingent/special-support/correction-specialists');
        renderCertificateSpecialists();
        certificateUi.directionBody.querySelectorAll('[data-certificate-direction="specialistId"]').forEach((select) => {
            const selected = select.value; select.innerHTML = certificateSpecialistOptions(selected); select.value = selected;
        });
    } catch (error) { window.alert(`Ошибка добавления специалиста: ${error.message}`); }
});

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
        fillCertificateYears();
        updateCertificateFormVisibility();
        Promise.all([
            loadCertificateReferences(),
            refreshCertificates()
        ]).catch((error) => {
            certificateUi.message.textContent = `Ошибка: ${error.message}`;
        });
    }
    if (tabName === 'nosologies') {
        loadCertificateReferences().catch((error) => {
            window.alert(`Ошибка загрузки справочника: ${error.message}`);
        });
    }
    if (tabName === 'mismatches') {
        refreshMismatches().catch((error) => {
            ui.mismatchSummary.textContent = `Ошибка: ${error.message}`;
        });
    }
}));

ui.openMismatchesBtn?.addEventListener('click', () => {
    const snapshotId = ui.openMismatchesBtn.dataset.snapshotId || '';
    if (snapshotId && ui.mismatchSnapshot) ui.mismatchSnapshot.value = snapshotId;
    showTab('mismatches');
    window.location.hash = '#mismatches';
    refreshMismatches(snapshotId).catch((error) => {
        ui.mismatchSummary.textContent = `Ошибка: ${error.message}`;
    });
});

ui.mismatchRefreshBtn?.addEventListener('click', () => refreshMismatches().catch((error) => {
    ui.mismatchSummary.textContent = `Ошибка: ${error.message}`;
}));
ui.mismatchSnapshot?.addEventListener('change', () => refreshMismatches().catch((error) => {
    ui.mismatchSummary.textContent = `Ошибка: ${error.message}`;
}));
ui.mismatchBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-resolve-mismatch]');
    if (button) openMismatchDialog(button.dataset.resolveMismatch);
});
ui.mismatchStudentSearch?.addEventListener('input', () => fillMismatchStudentOptions(ui.mismatchStudentSearch.value));
ui.mismatchForm?.addEventListener('submit', saveMismatchResolution);
ui.mismatchDialogClose?.addEventListener('click', () => ui.mismatchDialog.close());
ui.mismatchCancelBtn?.addEventListener('click', () => ui.mismatchDialog.close());

ui.copyMesScriptBtn?.addEventListener('click', () => {
    ui.mesScriptResult.textContent = 'Копирую скрипт…';
    copyMesExportScript().catch((error) => {
        ui.mesScriptResult.textContent = `Ошибка: ${error.message}`;
    });
});

ui.downloadMesScriptBtn?.addEventListener('click', () => {
    ui.mesScriptResult.textContent = 'Готовлю файл…';
    downloadMesExportScript().catch((error) => {
        ui.mesScriptResult.textContent = `Ошибка: ${error.message}`;
    });
});

ui.importBtn.addEventListener('click', async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) {
        printImportResult({ error: 'Выберите CSV- или Excel-файл' });
        return;
    }
    try {
        const form = new FormData();
        form.append('file', file);
        const result = await api('/api/contingent/import', { method: 'POST', body: form });
        printImportResult(result);
        ui.fileInput.value = '';
        await loadSnapshots();
        await refreshMismatches(result.snapshotId);
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

ui.supportIupOrderDownloadBtn?.addEventListener('click', () => {
    generateSupportIupOrder(false).catch((error) => {
        ui.supportIupOrderMessage.textContent = `Не удалось сформировать приказ: ${error.message}`;
    });
});

ui.supportIupOrderDownloadGroupBtn?.addEventListener('click', () => {
    generateSupportIupOrder(true).catch((error) => {
        ui.supportIupOrderMessage.textContent = `Не удалось сформировать сводный приказ: ${error.message}`;
    });
});

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
        const requestedTab = ['#import', '#mismatches', '#manual', '#support', '#nosologies', '#stats'].includes(hash)
            ? hash.slice(1)
            : defaultTab;
        const permissions = contingentPermissions();
        const finalTab = ((requestedTab === 'import' || requestedTab === 'mismatches') && permissions.canImportView)
            || (requestedTab === 'manual' && permissions.canManualView)
            || ((requestedTab === 'support' || requestedTab === 'nosologies') && permissions.canSupportView)
            || (requestedTab === 'stats' && permissions.canStatsView)
            ? requestedTab
            : defaultTab;
        showTab(finalTab);

        await loadSnapshots();
        if (contingentPermissions().canImportView) {
            await refreshMismatches();
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
            fillCertificateYears();
            await loadCertificateReferences();
            await refreshCertificates();
            resetCertificateForm();
        }
        if (finalTab === 'nosologies') {
            await loadCertificateReferences();
        }
    } catch (error) {
        printImportResult({ error: error.message });
    }
})();
