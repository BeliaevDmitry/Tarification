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
    body: document.getElementById("subjects-body")
};

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
        tr.innerHTML = `<td>${esc(r.subjectName)}</td><td>${esc(typeLabel(r.subjectType))}</td><td>${esc(r.createdAt)}</td>`;
        ui.body.appendChild(tr);
    });
}

async function reload() {
    const rows = await api('/api/subjects');
    render(rows || []);
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

ui.refreshBtn.addEventListener('click', () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener('click', async () => {
    try { await api('/api/subjects', { method: 'DELETE' }); print({ status: 'cleared' }); await reload(); }
    catch (e) { print({ error: e.message }); }
});

reload().catch((e) => print({ error: e.message }));
