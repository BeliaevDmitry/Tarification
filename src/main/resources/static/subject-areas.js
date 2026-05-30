const ui = {
    form: document.getElementById('subject-area-form'),
    name: document.getElementById('subject-area-name'),
    refreshBtn: document.getElementById('subject-area-refresh'),
    result: document.getElementById('subject-area-result'),
    body: document.getElementById('subject-area-body'),
    editDialog: document.getElementById('subject-area-edit-dialog'),
    editForm: document.getElementById('subject-area-edit-form'),
    editCancel: document.getElementById('subject-area-edit-cancel')
};

let rowsCache = [];

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

function openEdit(row) {
    if (!row || !ui.editDialog || !ui.editForm) return;
    ui.editForm.elements.id.value = row.id || '';
    ui.editForm.elements.name.value = row.name || '';
    ui.editDialog.showModal();
}

function render(rows) {
    rowsCache = rows || [];
    ui.body.innerHTML = '';
    rowsCache.forEach((row) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><button type="button" class="link-button" data-edit-id="${esc(row.id)}">${esc(row.name)}</button></td>
            <td class="row-actions">
                <button type="button" data-edit-id="${esc(row.id)}">Редактировать</button>
                <button type="button" data-delete-id="${esc(row.id)}">Удалить</button>
            </td>`;
        tr.querySelectorAll('button[data-edit-id]').forEach((button) => {
            button.addEventListener('click', () => openEdit(rowsCache.find((item) => String(item.id) === String(button.dataset.editId))));
        });
        tr.querySelector('button[data-delete-id]')?.addEventListener('click', async (event) => {
            if (!confirm('Удалить предметную область?')) return;
            try { await api(`/api/subject-areas/${event.currentTarget.dataset.deleteId}`, { method: 'DELETE' }); await reload(); }
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

ui.editForm?.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const saved = await api('/api/subject-areas', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: Number(ui.editForm.elements.id.value),
                name: ui.editForm.elements.name.value.trim()
            })
        });
        ui.editDialog.close();
        print(saved);
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.editCancel?.addEventListener('click', () => ui.editDialog?.close());
ui.refreshBtn?.addEventListener('click', () => reload().catch((error) => print({ error: error.message })));
reload().catch((error) => print({ error: error.message }));
