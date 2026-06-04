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
    deleteBtn: document.getElementById("building-delete-btn"),
    managerDisplay: document.getElementById("building-manager-display"),
    fileInput: document.getElementById("buildings-file"),
    importBtn: document.getElementById("import-buildings-btn")
};

let buildings = [];
let buildingGroups = [];

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

function groupLabelById(id) {
    const group = buildingGroups.find((g) => String(g.id) === String(id));
    if (!group) return id ? `#${id}` : "—";
    const code = String(group.code || "").trim();
    const name = String(group.name || "").trim();
    return [code, name].filter(Boolean).join(" — ") || `#${group.id}`;
}

function fillBuildingGroupSelect(select, selectedId = "") {
    if (!select) return;
    select.innerHTML = '<option value="">Группа корпуса</option>';
    buildingGroups
        .slice()
        .sort((a, b) => String(a.code || a.name || "").localeCompare(String(b.code || b.name || ""), "ru"))
        .forEach((group) => {
            const option = document.createElement("option");
            option.value = String(group.id);
            option.textContent = groupLabelById(group.id);
            select.appendChild(option);
        });
    select.value = selectedId ? String(selectedId) : "";
}

function openEdit(item) {
    ui.editForm.elements.id.value = item.id || "";
    const siteCode = ui.editForm.elements.siteCode;
    if (siteCode) siteCode.value = item.code || "";
    fillBuildingGroupSelect(ui.editForm.elements.buildingGroupId, item.buildingGroupId);
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
        tr.innerHTML = `<td>${escapeHtml(groupLabelById(r.buildingGroupId))}</td><td>${escapeHtml(r.name)}</td><td>${escapeHtml(displayManagerFio(r))}</td><td>${escapeHtml(r.address)}</td><td><button type="button" class="inline-plus" data-edit-id="${escapeHtml(r.id)}" title="Редактировать">✏️</button></td>`;
        ui.body.appendChild(tr);
    });

    ui.body.querySelectorAll('button[data-edit-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const found = buildings.find((b) => String(b.id) === String(btn.dataset.editId));
            if (found) openEdit(found);
        });
    });
}

async function reload() {
    const [groups, rows] = await Promise.all([
        api("/api/building-groups"),
        api("/api/buildings")
    ]);
    buildingGroups = groups || [];
    fillBuildingGroupSelect(ui.form.elements.buildingGroupId, ui.form.elements.buildingGroupId?.value);
    render(rows);
}

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(ui.form);
    const payload = {
        buildingGroupId: Number(form.get("buildingGroupId") || 0) || null,
        name: String(form.get("name") || "").trim(),
        address: String(form.get("address") || "").trim()
    };

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
        id: Number(ui.editForm.elements.id.value || 0) || null,
        buildingGroupId: Number(ui.editForm.elements.buildingGroupId.value || 0) || null,
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
ui.deleteBtn?.addEventListener('click', async () => {
    const id = Number(ui.editForm.elements.id.value || 0) || null;
    if (!id) {
        print({ error: "ID корпуса не найден" });
        return;
    }
    if (!window.confirm("Удалить корпус? Действие необратимо.")) return;
    try {
        await api(`/api/buildings/one?id=${encodeURIComponent(id)}`, { method: "DELETE" });
        ui.editDialog.close();
        print({ status: "deleted", id });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.importBtn?.addEventListener('click', async () => {
    const file = ui.fileInput?.files?.[0];
    if (!file) {
        print({ error: 'Выберите файл для импорта' });
        return;
    }
    try {
        const form = new FormData();
        form.append('file', file);
        const result = await api('/api/buildings/import', { method: 'POST', body: form });
        print(result);
        ui.fileInput.value = '';
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.refreshBtn.addEventListener("click", () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener("click", async () => {
    try { await api("/api/buildings", { method: "DELETE" }); print({ status: "cleared" }); await reload(); }
    catch (error) { print({ error: error.message }); }
});

reload().catch((e) => print({ error: e.message }));
