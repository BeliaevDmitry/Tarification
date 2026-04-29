const ui = {
    refreshBtn: document.getElementById('refresh-load-stats-btn'),
    exportBtn: document.getElementById('export-load-stats-btn'),
    summary: document.getElementById('load-stats-summary'),
    table: document.getElementById('load-stats-table'),
    result: document.getElementById('load-stats-result'),
    building: document.getElementById('load-stats-building'),
    page: document.getElementById('load-stats-page'),
    pageSize: document.getElementById('load-stats-page-size')
};

let statsRows = [];

async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const print = (v) => { ui.result.textContent = JSON.stringify(v, null, 2); };

function renderStatsView(stats) {
    statsRows = stats?.rows || [];
    if (!statsRows.length) {
        ui.summary.textContent = 'Нет данных для статистики.';
        ui.table.innerHTML = '<tbody><tr><td>Нет данных.</td></tr></tbody>';
        return;
    }

    ui.summary.textContent = `Предметов: ${stats.subjects}. Плановых часов: ${stats.totalPlanned}. Распределено: ${stats.totalAssigned}. Нераспределено: ${stats.totalUnassigned}. Показано: ${stats.rows?.length || 0} из ${stats.totalRows ?? 0} (стр. ${stats.page ?? 0}, размер ${stats.pageSize ?? 0}).`;

    const thead = `
      <thead>
        <tr>
          <th>Предметная область</th>
          <th>Предмет</th>
          <th>Часы по УП</th>
          <th>Распределено</th>
          <th>Не распределено</th>
        </tr>
      </thead>`;

    const tbody = statsRows.map((row) => `
      <tr>
        <td>${esc(row.subjectArea || 'Без области')}</td>
        <td>${esc(row.subjectName)}</td>
        <td>${esc(row.planned)}</td>
        <td>${esc(row.assigned)}</td>
        <td>${esc(row.unassigned)}</td>
      </tr>
    `).join('');

    ui.table.innerHTML = `${thead}<tbody>${tbody}</tbody>`;
}

async function refreshStats() {
    try {
        const params = new URLSearchParams();
        const building = String(ui.building?.value || '').trim();
        if (building) params.set('building', building);
        params.set('page', String(Math.max(Number(ui.page?.value || 0), 0)));
        params.set('pageSize', String(Math.min(Math.max(Number(ui.pageSize?.value || 100), 1), 500)));
        const stats = await api(`/api/manual-load/stats?${params.toString()}`);
        renderStatsView(stats || {});
        print({ status: 'ok', rows: (stats?.rows || []).length });
    } catch (error) {
        print({ error: error.message });
    }
}

function exportStatsCsv() {
    const rows = Array.from(ui.table.querySelectorAll('tr'));
    if (!rows.length) {
        print({ warning: 'Нет данных для экспорта статистики' });
        return;
    }
    const csvRows = rows.map((row) => {
        const cells = Array.from(row.querySelectorAll('th,td'));
        return cells.map((cell) => `"${String(cell.textContent || '').replaceAll('"', '""').trim()}"`).join(';');
    });
    const blob = new Blob(["\uFEFF" + csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    const yearPart = String(sessionStorage.getItem('tarification.academicYear') || '').replace('/', '-');
    link.download = `load-stats${yearPart ? `-${yearPart}` : ''}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
    print({ status: 'exported' });
}

ui.refreshBtn?.addEventListener('click', refreshStats);
ui.exportBtn?.addEventListener('click', exportStatsCsv);
refreshStats();
