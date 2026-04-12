const ui = {
    snapshotSelect: document.getElementById('contingent-snapshot-select'),
    fileInput: document.getElementById('contingent-file-input'),
    importBtn: document.getElementById('contingent-import-btn'),
    recalcBtn: document.getElementById('contingent-recalc-btn'),
    filterBuilding: document.getElementById('contingent-filter-building'),
    filterParallel: document.getElementById('contingent-filter-parallel'),
    filterClass: document.getElementById('contingent-filter-class'),
    filterQuery: document.getElementById('contingent-filter-query'),
    applyFilterBtn: document.getElementById('contingent-apply-filter-btn'),
    tabs: Array.from(document.querySelectorAll('.tab-btn[data-tab]')),
    content: document.getElementById('contingent-content')
};

let activeTab = 'children';

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    const body = text ? JSON.parse(text) : null;
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function selectedSnapshotId() {
    return Number(ui.snapshotSelect.value || 0);
}

async function loadSnapshots() {
    const snapshots = await api('/api/contingent/snapshots');
    ui.snapshotSelect.innerHTML = snapshots.map((s) => `<option value="${s.id}">${s.snapshotDate} (${s.studentsCount} уч.)</option>`).join('');
    if (snapshots.length) {
        ui.snapshotSelect.value = String(snapshots[0].id);
    }
}

function renderTable(headers, rows) {
    ui.content.innerHTML = `
        <table class="sheet-table">
          <thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead>
          <tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c ?? ''}</td>`).join('')}</tr>`).join('')}</tbody>
        </table>`;
}

async function refreshTab() {
    const snapshotId = selectedSnapshotId();
    if (!snapshotId) {
        ui.content.innerHTML = '<p class="muted">Снимок не выбран.</p>';
        return;
    }
    if (activeTab === 'children') {
        const params = new URLSearchParams({ snapshotId: String(snapshotId) });
        if (ui.filterBuilding.value) params.set('buildingCode', ui.filterBuilding.value);
        if (ui.filterParallel.value) params.set('parallel', ui.filterParallel.value);
        if (ui.filterClass.value) params.set('className', ui.filterClass.value);
        if (ui.filterQuery.value) params.set('query', ui.filterQuery.value);
        const rows = await api(`/api/contingent/students?${params}`);
        renderTable(['ФИО', 'Дата рождения', 'Класс', 'Параллель', 'Корпус'], rows.map((r) => [r.fullName, r.birthDate || '', r.classNameNormalized, r.parallel, r.buildingCode || '']));
        return;
    }
    if (activeTab === 'classes') {
        const rows = await api(`/api/contingent/summary/classes?snapshotId=${snapshotId}`);
        renderTable(['Класс', 'Параллель', 'Корпус', 'Детей', 'Статус УП'], rows.map((r) => [r.className, r.parallel, r.buildingCode || '', r.studentsCount, r.curriculumMatched ? 'OK' : 'Нет в УП']));
        return;
    }
    if (activeTab === 'parallels') {
        const rows = await api(`/api/contingent/summary/parallels?snapshotId=${snapshotId}`);
        renderTable(['Параллель', 'Классов', 'Детей'], rows.map((r) => [r.parallel, r.classesCount, r.studentsCount]));
        return;
    }
    if (activeTab === 'buildings') {
        const rows = await api(`/api/contingent/summary/buildings?snapshotId=${snapshotId}`);
        renderTable(['Корпус', 'Классов', 'Детей'], rows.map((r) => [r.buildingCode || '—', r.classesCount, r.studentsCount]));
        return;
    }
    const warnings = await api(`/api/contingent/warnings?snapshotId=${snapshotId}`);
    renderTable(['Тип', 'Класс', 'Сообщение'], warnings.map((r) => [r.type, r.className || '', r.message]));
}

ui.importBtn?.addEventListener('click', async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    await api('/api/contingent/import', { method: 'POST', body: form });
    await loadSnapshots();
    await refreshTab();
});

ui.recalcBtn?.addEventListener('click', async () => {
    const snapshotId = selectedSnapshotId();
    if (!snapshotId) return;
    await api(`/api/contingent/${snapshotId}/recalculate-warnings`, { method: 'POST' });
    if (activeTab === 'warnings') await refreshTab();
});

ui.applyFilterBtn?.addEventListener('click', () => refreshTab().catch((e) => { ui.content.textContent = e.message; }));
ui.snapshotSelect?.addEventListener('change', () => refreshTab().catch((e) => { ui.content.textContent = e.message; }));
ui.tabs.forEach((tab) => tab.addEventListener('click', () => {
    ui.tabs.forEach((b) => b.classList.toggle('active', b === tab));
    activeTab = tab.dataset.tab;
    refreshTab().catch((e) => { ui.content.textContent = e.message; });
}));

loadSnapshots().then(refreshTab).catch((error) => {
    ui.content.textContent = error.message;
});

