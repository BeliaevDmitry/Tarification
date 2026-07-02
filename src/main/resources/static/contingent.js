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
    manualTable: document.getElementById('contingent-manual-table')
};

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
let currentStats = null;
let currentManualRows = [];

function stageClassSummary(stats) {
    return `НОО: ${Number(stats?.totalClassesNoo || 0)}; ООО: ${Number(stats?.totalClassesOoo || 0)}; СОО: ${Number(stats?.totalClassesSoo || 0)}`;
}

function contingentPermissions() {
    const permissions = window.tarificationTabPermissions || {};
    if (window.tarificationAuth?.admin) {
        return { canImportView: true, canStatsView: true, canManualView: true };
    }
    return {
        canImportView: Boolean(permissions.CONTINGENT_IMPORT?.canView),
        canStatsView: Boolean(permissions.CONTINGENT_STATS?.canView),
        canManualView: Boolean(permissions.CONTINGENT_STATS?.canView)
    };
}


async function waitForAuthContext() {
    for (let i = 0; i < 40; i += 1) {
        if (window.tarificationAuth) return;
        await new Promise((resolve) => setTimeout(resolve, 50));
    }
}

function applyTabAccess() {
    const { canImportView, canStatsView, canManualView } = contingentPermissions();
    ui.tabs.forEach((tab) => {
        const tabName = tab.dataset.contingentTab;
        const allowed = tabName === 'import' ? canImportView : (tabName === 'manual' ? canManualView : canStatsView);
        tab.style.display = allowed ? '' : 'none';
    });

    if (canStatsView) return 'stats';
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

ui.tabs.forEach((tab) => tab.addEventListener('click', () => {
    const tabName = tab.dataset.contingentTab;
    showTab(tabName);
    window.location.hash = tabName === 'import' ? '#import' : (tabName === 'manual' ? '#manual' : '#stats');
    if (tabName === 'manual') {
        refreshManualClassSizes().catch((error) => {
            ui.manualSummary.textContent = `Ошибка: ${error.message}`;
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

(async function init() {
    try {
        await waitForAuthContext();
        const defaultTab = applyTabAccess();
        if (!defaultTab) {
            ui.statsSummary.textContent = 'Нет доступа к вкладкам контингента.';
            return;
        }

        const hash = String(window.location.hash || '').toLowerCase();
        const requestedTab = hash === '#import' ? 'import' : (hash === '#manual' ? 'manual' : (hash === '#stats' ? 'stats' : defaultTab));
        const permissions = contingentPermissions();
        const finalTab = (requestedTab === 'import' && permissions.canImportView)
            || (requestedTab === 'manual' && permissions.canManualView)
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
    } catch (error) {
        printImportResult({ error: error.message });
    }
})();
