const jsonHeaders = { "Content-Type": "application/json" };
const ui = {
    fileInput: document.getElementById("subject-file"),
    importBtn: document.getElementById("import-subjects-btn"),
    form: document.getElementById("subject-form"),
    name: document.getElementById("subject-name"),
    type: document.getElementById("subject-type"),
    refreshBtn: document.getElementById("refresh-subjects-btn"),
    clearBtn: document.getElementById("clear-subjects-btn"),
    result: document.getElementById("subjects-result"),
    body: document.getElementById("subjects-body"),
    editDialog: document.getElementById("subject-edit-dialog"),
    editForm: document.getElementById("subject-edit-form"),
    deleteBtn: document.getElementById("subject-delete-btn"),
    closeBtn: document.getElementById("subject-close-btn")
};

let subjects = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

const esc = (v) => String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
const print = (v) => { ui.result.textContent = JSON.stringify(v, null, 2); };
const typeLabel = (v) => v === "EXTRACURRICULAR" ? "2 тип: внеурочная" : "1 тип: основная/формируемая";

function render(rows) {
    ui.body.innerHTML = "";
    (rows || []).sort((a,b)=>String(a.subjectName).localeCompare(String(b.subjectName),"ru")).forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${esc(r.subjectName)}</td><td>${esc(typeLabel(r.subjectType))}</td>`;
        tr.addEventListener('click', () => openEdit(r));
        ui.body.appendChild(tr);
    });
}

function openEdit(subject) {
    ui.editForm.elements.id.value = String(subject.id);
    ui.editForm.elements.subjectName.value = subject.subjectName;
    ui.editForm.elements.subjectType.value = subject.subjectType;
    ui.editDialog.showModal();
}

async function reload() {
    subjects = await api('/api/subjects');
    render(subjects || []);
}

ui.importBtn.addEventListener('click', async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) return print({ error: 'Выберите файл' });
    const form = new FormData();
    form.append('file', file);
    try {
        const result = await api('/api/subjects/import', { method: 'POST', body: form });
        print(result);
        await reload();
    } catch (e) { print({ error: e.message }); }
});

ui.form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const result = await api('/api/subjects', {
            method: 'POST', headers: jsonHeaders,
            body: JSON.stringify({ subjectName: ui.name.value.trim(), subjectType: ui.type.value })
        });
        print(result);
        ui.form.reset();
        await reload();
    } catch (e) { print({ error: e.message }); }
});

ui.editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = Number(ui.editForm.elements.id.value);
    try {
        const result = await api(`/api/subjects/${id}`, {
            method: 'PATCH', headers: jsonHeaders,
            body: JSON.stringify({
                subjectName: ui.editForm.elements.subjectName.value.trim(),
                subjectType: ui.editForm.elements.subjectType.value
            })
        });
        ui.editDialog.close();
        print(result);
        await reload();
    } catch (e) { print({ error: e.message }); }
});

ui.deleteBtn.addEventListener('click', async () => {
    const id = Number(ui.editForm.elements.id.value);
    if (!confirm('Удалить предмет?')) return;
    try {
        await api(`/api/subjects/${id}`, { method: 'DELETE' });
        ui.editDialog.close();
        print({ status: 'deleted', id });
        await reload();
    } catch (e) { print({ error: e.message }); }
});

ui.closeBtn.addEventListener('click', () => ui.editDialog.close());
ui.refreshBtn.addEventListener('click', () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener('click', async () => {
    try { await api('/api/subjects', { method: 'DELETE' }); print({ status: 'cleared' }); await reload(); }
    catch (e) { print({ error: e.message }); }
});

reload().catch((e) => print({ error: e.message }));
