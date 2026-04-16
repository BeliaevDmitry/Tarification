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
    statsTable: document.getElementById('contingent-stats-table'),
    statsSummary: document.getElementById('contingent-stats-summary')
};

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');

function contingentPermissions() {
    const permissions = window.tarificationTabPermissions || {};
    if (window.tarificationAuth?.admin) {
        return { canImportView: true, canStatsView: true };
    }
    return {
        canImportView: Boolean(permissions.CONTINGENT_IMPORT?.canView),
        canStatsView: Boolean(permissions.CONTINGENT_STATS?.canView)
    };
}


async function waitForAuthContext() {
    for (let i = 0; i < 40; i += 1) {
        if (window.tarificationAuth) return;
        await new Promise((resolve) => setTimeout(resolve, 50));
    }
}

function applyTabAccess() {
    const { canImportView, canStatsView } = contingentPermissions();
    ui.tabs.forEach((tab) => {
        const tabName = tab.dataset.contingentTab;
        const allowed = tabName === 'import' ? canImportView : canStatsView;
        tab.style.display = allowed ? '' : 'none';
    });

    if (canImportView) return 'import';
    if (canStatsView) return 'stats';
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
    const columns = stats?.columns || [];
    const parallels = stats?.parallels || [];
    const totalByParallel = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalStudents]));

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

    const footerTotalRow = `<tr><th>ИТОГО</th><th>${esc(stats?.totalStudents || 0)}</th>${footerTotals}</tr>`;
    const footerClassRow = `<tr><th>Классов</th><th></th>${footerClasses}</tr>`;

    ui.statsTable.innerHTML = `${thead}<tbody>${tbodyRows.join('')}${footerTotalRow}${footerClassRow}</tbody>`;
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
    const stats = await api(`/api/contingent/stats${query}`);
    ui.statsSummary.textContent = `Данные по состоянию на ${stats.snapshotDate}. Всего учащихся: ${stats.totalStudents}.`;
    renderStatsTable(stats);
}

ui.tabs.forEach((tab) => tab.addEventListener('click', () => {
    const tabName = tab.dataset.contingentTab;
    showTab(tabName);
    window.location.hash = tabName === 'import' ? '#import' : '#stats';
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


ui.statsExportBtn?.addEventListener('click', async () => {
    try {
        const selectedDate = ui.snapshotDateSelect.value;
        const query = selectedDate ? `?snapshotDate=${encodeURIComponent(selectedDate)}` : '';
        const scopedPath = window.withAcademicYear ? window.withAcademicYear(`/api/contingent/stats/export${query}`) : `/api/contingent/stats/export${query}`;
        const response = await fetch(scopedPath);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const blob = await response.blob();
        const fileName = response.headers.get('Content-Disposition')?.split("filename*=UTF-8''")[1] || `contingent_${selectedDate || 'latest'}.xlsx`;
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = decodeURIComponent(fileName);
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
    } catch (error) {
        ui.statsSummary.textContent = `Ошибка экспорта: ${error.message}`;
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
        const requestedTab = hash === '#import' ? 'import' : (hash === '#stats' ? 'stats' : defaultTab);
        const finalTab = (requestedTab === 'import' && contingentPermissions().canImportView) || (requestedTab === 'stats' && contingentPermissions().canStatsView)
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
    } catch (error) {
        printImportResult({ error: error.message });
    }
})();
