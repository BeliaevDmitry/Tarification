const ui = {
    refreshBtn: document.getElementById('subject-area-refresh'),
    result: document.getElementById('subject-area-result'),
    body: document.getElementById('subject-area-body')
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(v) { ui.result.textContent = JSON.stringify(v, null, 2); }
const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');

function render(rows) {
    ui.body.innerHTML = '';
    (rows || []).forEach((row) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${esc(row.name)}</td>`;
        ui.body.appendChild(tr);
    });
}

async function reload() {
    const rows = await api('/api/subject-areas');
    render(rows);
    print({ status: 'ok', count: rows.length, mode: 'fixed-base-areas' });
}

ui.refreshBtn?.addEventListener('click', () => reload().catch((error) => print({ error: error.message })));
reload().catch((error) => print({ error: error.message }));
