const ui = {
    form: document.getElementById('subject-area-form'),
    name: document.getElementById('subject-area-name'),
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
        tr.innerHTML = `<td>${esc(row.name)}</td><td><button type="button" data-id="${row.id}">Удалить</button></td>`;
        tr.querySelector('button')?.addEventListener('click', async () => {
            if (!confirm('Удалить предметную область?')) return;
            try { await api(`/api/subject-areas/${row.id}`, { method: 'DELETE' }); await reload(); }
            catch (error) { print({ error: error.message }); }
        });
        ui.body.appendChild(tr);
    });
}

async function reload() {
    const rows = await api('/api/subject-areas');
    render(rows);
}

ui.form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const saved = await api('/api/subject-areas', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: ui.name.value.trim() })
        });
        print(saved);
        ui.form.reset();
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.refreshBtn?.addEventListener('click', () => reload().catch((error) => print({ error: error.message })));
reload().catch((error) => print({ error: error.message }));
