const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("building-form"),
    refreshBtn: document.getElementById("refresh-buildings-btn"),
    clearBtn: document.getElementById("clear-buildings-btn"),
    result: document.getElementById("buildings-result"),
    body: document.getElementById("buildings-body"),
    editDialog: document.getElementById("building-edit-dialog"),
    editForm: document.getElementById("building-edit-form"),
    closeBtn: document.getElementById("building-close-btn"),
    managerDisplay: document.getElementById("building-manager-display")
};

let buildings = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function escapeHtml(v) {
    return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function print(value) { ui.result.textContent = JSON.stringify(value, null, 2); }

function displayManagerFio(item) {
    const value = String(item?.managerFio || "").trim();
    return value || "Не назначен";
}

function openEdit(item) {
    ui.editForm.elements.code.value = item.code;
    ui.editForm.elements.name.value = item.name;
    ui.editForm.elements.address.value = item.address;
    if (ui.managerDisplay) ui.managerDisplay.textContent = displayManagerFio(item);
    ui.editDialog.showModal();
}

function render(rows) {
    ui.body.innerHTML = "";
    buildings = rows || [];
    buildings.sort((a, b) => (a.name || "").localeCompare(b.name || "", "ru")).forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${escapeHtml(r.name)}</td><td>${escapeHtml(displayManagerFio(r))}</td><td>${escapeHtml(r.address)}</td><td><button type="button" class="inline-plus" data-edit-code="${escapeHtml(r.code)}" title="Редактировать">✏️</button></td>`;
        ui.body.appendChild(tr);
    });

    ui.body.querySelectorAll('button[data-edit-code]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const found = buildings.find((b) => b.code === btn.dataset.editCode);
            if (found) openEdit(found);
        });
    });
}

async function reload() {
    const rows = await api("/api/buildings");
    render(rows);
}

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(ui.form);
    const payload = {
        name: String(form.get("name") || "").trim(),
        address: String(form.get("address") || "").trim()
    };
    payload.code = `${payload.name}|${payload.address}`.toLowerCase();

    try {
        const saved = await api("/api/buildings", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
        print(saved);
        ui.form.reset();
        await reload();
    } catch (error) { print({ error: error.message }); }
});

ui.editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        code: String(ui.editForm.elements.code.value || '').trim(),
        name: String(ui.editForm.elements.name.value || '').trim(),
        address: String(ui.editForm.elements.address.value || '').trim()
    };

    try {
        const saved = await api('/api/buildings', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) });
        ui.editDialog.close();
        print(saved);
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.closeBtn.addEventListener('click', () => ui.editDialog.close());
ui.refreshBtn.addEventListener("click", () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener("click", async () => {
    try { await api("/api/buildings", { method: "DELETE" }); print({ status: "cleared" }); await reload(); }
    catch (error) { print({ error: error.message }); }
});

reload().catch((e) => print({ error: e.message }));
