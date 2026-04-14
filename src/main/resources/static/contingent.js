const ui = {
    tabs: Array.from(document.querySelectorAll('[data-contingent-tab]')),
    panes: Array.from(document.querySelectorAll('[data-contingent-pane]')),
    fileInput: document.getElementById('contingent-file'),
    importBtn: document.getElementById('contingent-import-btn'),
    importResult: document.getElementById('contingent-import-result'),
    problemsBody: document.getElementById('contingent-problems-body'),
    snapshotDateSelect: document.getElementById('contingent-snapshot-date'),
    statsRefreshBtn: document.getElementById('contingent-stats-refresh-btn'),
    statsTable: document.getElementById('contingent-stats-table'),
    statsSummary: document.getElementById('contingent-stats-summary')
};

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');

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
    const parallelTotals = Object.fromEntries((stats?.parallelTotals || []).map((x) => [x.parallel, x.totalStudents]));

    const headerTop = `
        <tr>
            <th rowspan="2">Параллель</th>
            ${columns.map((column) => `<th colspan="${Math.max(column.classes.length, 1)}">${esc(column.buildingName)} (${esc(column.buildingCode)})</th>`).join('')}
            <th rowspan="2">Итого по параллели</th>
        </tr>`;

    const headerClasses = `
        <tr>
            ${columns.map((column) => column.classes.length
        ? column.classes.map((classTotal) => `<th>${esc(classTotal.className)}</th>`).join('')
        : '<th>—</th>').join('')}
        </tr>`;

    const rows = parallels.map((parallel) => {
        const cells = columns.map((column) => {
            if (!column.classes.length) return '<td>0</td>';
            return column.classes.map((classTotal) => {
                const classParallel = Number(String(classTotal.className).split('-')[0]);
                return `<td>${classParallel === parallel ? esc(classTotal.students) : '0'}</td>`;
            }).join('');
        }).join('');
        return `<tr><th>${parallel}</th>${cells}<th>${esc(parallelTotals[parallel] || 0)}</th></tr>`;
    }).join('');

    const footer = `
        <tr>
            <th>Итого по корпусу</th>
            ${columns.map((column) => column.classes.length
        ? column.classes.map((classTotal) => `<th>${esc(classTotal.students)}</th>`).join('')
        : '<th>0</th>').join('')}
            <th>${esc(stats?.totalStudents || 0)}</th>
        </tr>`;

    ui.statsTable.innerHTML = `<thead>${headerTop}${headerClasses}</thead><tbody>${rows}${footer}</tbody>`;
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

ui.tabs.forEach((tab) => tab.addEventListener('click', () => showTab(tab.dataset.contingentTab)));

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

(async function init() {
    try {
        await loadSnapshots();
        await refreshProblems();
        if (ui.snapshotDateSelect.options.length) {
            await refreshStats();
        } else {
            ui.statsSummary.textContent = 'Данные контингента пока не загружены.';
            renderProblems([]);
            ui.statsTable.innerHTML = '';
        }
    } catch (error) {
        printImportResult({ error: error.message });
    }
})();
