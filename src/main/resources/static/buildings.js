const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("building-form"),
    buildingGroupForm: document.getElementById("building-group-form"),
    refreshBtn: document.getElementById("refresh-buildings-btn"),
    clearBtn: document.getElementById("clear-buildings-btn"),
    result: document.getElementById("buildings-result"),
    buildingGroupsBody: document.getElementById("building-groups-body"),
    fileInput: document.getElementById("buildings-file"),
    importBtn: document.getElementById("import-buildings-btn"),
    buildingGroupModeInputs: Array.from(document.querySelectorAll('input[name="createInitialSite"]')),
    newSiteFields: Array.from(document.querySelectorAll("[data-new-site-field]")),
    baseSiteField: document.querySelector("[data-base-site-field]"),
    buildingGroupEditDialog: document.getElementById("building-group-edit-dialog"),
    buildingGroupEditForm: document.getElementById("building-group-edit-form"),
    buildingGroupCloseBtn: document.getElementById("building-group-close-btn"),
    buildingGroupDeleteBtn: document.getElementById("building-group-delete-btn"),
    buildingGroupManagerDisplay: document.getElementById("building-group-manager-display"),
    buildingGroupSitesDisplay: document.getElementById("building-group-sites-display")
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
    const value = String(item?.managerFio || item?.manager || item?.responsibleUserName || "").trim();
    return value || "Не назначен";
}

function groupLabelById(id) {
    const group = buildingGroups.find((g) => String(g.id) === String(id));
    if (!group) return id ? `#${id}` : "—";
    const code = String(group.code || "").trim();
    const name = String(group.name || "").trim();
    return [code, name].filter(Boolean).join(" — ") || `#${group.id}`;
}


function physicalSiteLabel(site) {
    const groupLabel = groupLabelById(site?.buildingGroupId);
    const siteName = String(site?.name || "").trim();
    const address = String(site?.address || "").trim();
    return [siteName || groupLabel, address].filter(Boolean).join(" — ") || `#${site?.id}`;
}

function fillBaseSiteSelect(selectedId = "") {
    if (!ui.baseSiteField) return;
    ui.baseSiteField.innerHTML = '<option value="">Базовая физическая площадка</option>';
    buildings
        .slice()
        .sort((a, b) => physicalSiteLabel(a).localeCompare(physicalSiteLabel(b), "ru", { numeric: true }))
        .forEach((site) => {
            const option = document.createElement("option");
            option.value = String(site.id);
            option.textContent = physicalSiteLabel(site);
            ui.baseSiteField.appendChild(option);
        });
    ui.baseSiteField.value = selectedId ? String(selectedId) : "";
}

function selectedCreateInitialSite() {
    const selected = ui.buildingGroupForm?.querySelector('input[name="createInitialSite"]:checked');
    return selected ? selected.value !== "false" : true;
}

function syncBuildingGroupCreateMode() {
    const createInitialSite = selectedCreateInitialSite();
    ui.newSiteFields.forEach((field) => {
        field.hidden = !createInitialSite;
        field.disabled = !createInitialSite;
        if (field.name === "initialAddress") field.required = createInitialSite;
    });
    if (ui.baseSiteField) {
        ui.baseSiteField.hidden = createInitialSite;
        ui.baseSiteField.disabled = createInitialSite;
        ui.baseSiteField.required = !createInitialSite;
    }
}

function fillBuildingGroupSelect(select, selectedId = "") {
    if (!select) return;
    select.innerHTML = '<option value="">Основной корпус</option>';
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


function sitesForGroup(groupId) {
    return buildings.filter((site) => String(site.buildingGroupId) === String(groupId));
}

function physicalSitesForGroupLabel(groupId) {
    const sites = sitesForGroup(groupId);
    if (!sites.length) {
        return "Собственной площадки нет. Классы используют выбранные существующие площадки.";
    }
    return sites
        .map((site) => [String(site.name || "").trim(), String(site.address || "").trim()].filter(Boolean).join(" — "))
        .filter(Boolean)
        .join("; ");
}

function physicalSitesForGroupHtml(groupId) {
    return escapeHtml(physicalSitesForGroupLabel(groupId)).replaceAll("; ", "<br>");
}

function renderPhysicalSitesForGroupEditor(groupId) {
    if (!ui.buildingGroupSitesDisplay) return;
    ui.buildingGroupSitesDisplay.innerHTML = "";
    const sites = sitesForGroup(groupId);
    if (!sites.length) {
        const empty = document.createElement("div");
        empty.className = "site-empty-note";
        empty.textContent = "Собственной площадки нет. Классы используют выбранные существующие площадки.";
        ui.buildingGroupSitesDisplay.appendChild(empty);
        return;
    }

    sites
        .slice()
        .sort((a, b) => physicalSiteLabel(a).localeCompare(physicalSiteLabel(b), "ru", { numeric: true }))
        .forEach((site) => {
            const item = document.createElement("div");
            item.className = "site-list-item";
            const details = document.createElement("div");
            details.className = "site-list-details";
            const title = document.createElement("strong");
            title.textContent = String(site.name || "Физическая площадка").trim() || "Физическая площадка";
            const address = document.createElement("span");
            address.textContent = String(site.address || "Адрес не указан").trim() || "Адрес не указан";
            details.append(title, address);

            const deleteBtn = document.createElement("button");
            deleteBtn.type = "button";
            deleteBtn.className = "danger-btn";
            deleteBtn.dataset.deleteSiteId = String(site.id);
            deleteBtn.textContent = "Удалить площадку";

            item.append(details, deleteBtn);
            ui.buildingGroupSitesDisplay.appendChild(item);
        });

    ui.buildingGroupSitesDisplay.querySelectorAll('button[data-delete-site-id]').forEach((btn) => {
        btn.addEventListener('click', () => deletePhysicalSite(btn.dataset.deleteSiteId));
    });
}

function refreshBuildingGroupEditState(group) {
    ui.buildingGroupEditForm.elements.id.value = group.id || "";
    ui.buildingGroupEditForm.elements.code.value = group.code || "";
    ui.buildingGroupEditForm.elements.name.value = group.name || "";
    if (ui.buildingGroupManagerDisplay) ui.buildingGroupManagerDisplay.textContent = displayManagerFio(group);
    renderPhysicalSitesForGroupEditor(group.id);
    const mode = sitesForGroup(group.id).length ? "own" : "shared";
    const modeInput = ui.buildingGroupEditForm.elements.physicalSiteMode;
    if (modeInput) {
        Array.from(ui.buildingGroupEditForm.querySelectorAll('input[name="physicalSiteMode"]'))
            .forEach((input) => { input.checked = input.value === mode; });
    }
}

function openBuildingGroupEdit(group) {
    if (!ui.buildingGroupEditForm || !ui.buildingGroupEditDialog) return;
    refreshBuildingGroupEditState(group);
    ui.buildingGroupEditDialog.showModal();
}

function renderBuildingGroups() {
    if (!ui.buildingGroupsBody) return;
    ui.buildingGroupsBody.innerHTML = "";
    buildingGroups
        .slice()
        .sort((a, b) => String(a.code || a.name || "").localeCompare(String(b.code || b.name || ""), "ru", { numeric: true }))
        .forEach((group) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${escapeHtml(group.code || "")}</td><td>${escapeHtml(group.name || "")}</td><td>${escapeHtml(displayManagerFio(group))}</td><td>${physicalSitesForGroupHtml(group.id)}</td><td><button type="button" class="inline-plus" data-edit-group-id="${escapeHtml(group.id)}" title="Редактировать корпус">✏️</button></td>`;
            ui.buildingGroupsBody.appendChild(tr);
        });

    ui.buildingGroupsBody.querySelectorAll('button[data-edit-group-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const found = buildingGroups.find((group) => String(group.id) === String(btn.dataset.editGroupId));
            if (found) openBuildingGroupEdit(found);
        });
    });
}

function render(rows) {
    buildings = rows || [];
    renderBuildingGroups();
}

async function loadBuildingGroups() {
    buildingGroups = await api("/api/building-groups") || [];
    fillBuildingGroupSelect(ui.form.elements.buildingGroupId, ui.form.elements.buildingGroupId?.value);
    renderBuildingGroups();
    return buildingGroups;
}

async function loadBuildings() {
    const rows = await api("/api/buildings");
    render(rows);
    fillBaseSiteSelect(ui.baseSiteField?.value);
    renderBuildingGroups();
    return rows;
}

async function reload() {
    await loadBuildingGroups();
    await loadBuildings();
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

ui.buildingGroupForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(ui.buildingGroupForm);
    const createInitialSite = form.get("createInitialSite") !== "false";
    const payload = {
        code: String(form.get("code") || "").trim(),
        name: String(form.get("name") || "").trim(),
        createInitialSite
    };
    if (createInitialSite) {
        payload.initialAddress = String(form.get("initialAddress") || "").trim();
        payload.initialSiteName = String(form.get("initialSiteName") || "").trim();
    } else {
        payload.baseSchoolBuildingId = Number(form.get("baseSchoolBuildingId") || 0) || null;
    }

    try {
        const saved = await api("/api/building-groups", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
        print(saved);
        ui.buildingGroupForm.reset();
        syncBuildingGroupCreateMode();
        await loadBuildingGroups();
        await loadBuildings();
    } catch (error) { print({ error: error.message }); }
});

ui.buildingGroupCloseBtn?.addEventListener('click', () => ui.buildingGroupEditDialog?.close());
ui.buildingGroupDeleteBtn?.addEventListener('click', () => deleteBuildingGroup());

async function refreshOpenBuildingGroupEditor(groupId) {
    await reload();
    if (!ui.buildingGroupEditDialog?.open) return;
    const group = buildingGroups.find((item) => String(item.id) === String(groupId));
    if (group) refreshBuildingGroupEditState(group);
}

async function deletePhysicalSite(siteId) {
    const site = buildings.find((item) => String(item.id) === String(siteId));
    const groupId = ui.buildingGroupEditForm?.elements.id.value;
    const label = physicalSiteLabel(site || { id: siteId });
    if (!confirm(`Удалить физическую площадку «${label}»? Удаление возможно только если к площадке не привязаны классы или метагруппы.`)) return;
    try {
        await api(`/api/buildings/one?id=${encodeURIComponent(siteId)}`, { method: 'DELETE' });
        print({ status: 'deleted', type: 'physicalSite', id: siteId });
        await refreshOpenBuildingGroupEditor(groupId);
    } catch (error) {
        print({ error: error.message });
    }
}

async function deleteBuildingGroup() {
    const id = ui.buildingGroupEditForm?.elements.id.value;
    if (!id) return;
    const group = buildingGroups.find((item) => String(item.id) === String(id));
    const label = group ? groupLabelById(group.id) : `#${id}`;
    if (!confirm(`Удалить корпус «${label}»? Удаление возможно только если нет площадок, классов, метагрупп, учебного плана, нагрузки и назначенного руководителя.`)) return;
    try {
        await api(`/api/building-groups/${encodeURIComponent(id)}`, { method: 'DELETE' });
        ui.buildingGroupEditDialog?.close();
        print({ status: 'deleted', type: 'buildingGroup', id });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
}

ui.buildingGroupEditForm?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = Number(ui.buildingGroupEditForm.elements.id.value || 0) || null;
    const payload = {
        name: String(ui.buildingGroupEditForm.elements.name.value || '').trim()
    };
    try {
        const saved = await api(`/api/building-groups/${encodeURIComponent(id)}`, { method: 'PATCH', headers: jsonHeaders, body: JSON.stringify(payload) });
        ui.buildingGroupEditDialog.close();
        print(saved);
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

ui.buildingGroupModeInputs.forEach((input) => input.addEventListener("change", syncBuildingGroupCreateMode));
syncBuildingGroupCreateMode();

ui.refreshBtn.addEventListener("click", () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener("click", async () => {
    try { await api("/api/buildings", { method: "DELETE" }); print({ status: "cleared" }); await reload(); }
    catch (error) { print({ error: error.message }); }
});

reload().catch((e) => print({ error: e.message }));
