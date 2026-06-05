const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("class-form"),
    building: document.getElementById("class-building"),
    schoolBuilding: document.getElementById("class-school-building"),
    teacherList: document.getElementById("teacher-list"),
    refreshBtn: document.getElementById("refresh-classes-btn"),
    clearBtn: document.getElementById("clear-classes-btn"),
    result: document.getElementById("classes-result"),
    body: document.getElementById("classes-body"),
    fileInput: document.getElementById("classes-file"),
    importBtn: document.getElementById("import-classes-btn"),
    templateLink: document.getElementById("download-classes-template"),
    editDialog: document.getElementById("class-edit-dialog"),
    editForm: document.getElementById("class-edit-form"),
    editBuilding: document.getElementById("class-edit-building"),
    editSchoolBuilding: document.getElementById("class-edit-school-building"),
    editDeleteBtn: document.getElementById("class-edit-delete-btn"),
    editCloseBtn: document.getElementById("class-edit-close-btn"),
    transferWarning: document.getElementById("class-transfer-warning")
};

let teachers = [];
let buildings = [];
let classRows = [];
let editingOriginalKey = null;
let editingOriginalEntry = null;

function currentAcademicYear() {
    return String(
        (typeof window.getStoredAcademicYear === "function" ? window.getStoredAcademicYear() : "")
        || sessionStorage.getItem("tarification.academicYear")
        || ""
    ).trim();
}

function academicYearScopedPath(path) {
    if (typeof window.withAcademicYear === "function") {
        return window.withAcademicYear(path);
    }
    const selectedYear = currentAcademicYear();
    if (!selectedYear || String(path).includes("academicYear=")) return path;
    const separator = String(path).includes("?") ? "&" : "?";
    return `${path}${separator}academicYear=${encodeURIComponent(selectedYear)}`;
}

function updateTemplateLink() {
    if (ui.templateLink) {
        ui.templateLink.href = academicYearScopedPath("/api/classroom-leadership/template");
    }
}

async function api(path, options = {}) {
    const scopedPath = academicYearScopedPath(path);
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

const esc = (v) => String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
const norm = (v) => String(v || "").trim();
const print = (v) => { ui.result.textContent = JSON.stringify(v, null, 2); };

function normalizeClassName(value) {
    const v = norm(value).toUpperCase().replace(/[–—]/g, "-");
    const m = v.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return m ? `${m[1]}-${m[2]}` : v;
}

function normalizeBuildingCode(value) {
    return norm(value)
        .toUpperCase()
        .replace(/[–—]/g, "-")
        .replace(/[CС][ПPР]/g, "СП")
        .replace(/\s*\|\s*/g, "|")
        .replace(/\s+/g, "")
        .replace(/^СП-(\d+)$/, "СП$1");
}

function buildingGroupCode(value) {
    const normalized = normalizeBuildingCode(value);
    const separator = normalized.indexOf("|");
    return separator >= 0 ? normalized.slice(0, separator) : normalized;
}

function entryKey(entry) {
    if (entry?.id) return `id:${entry.id}`;
    return `${buildingGroupCode(entry.numberSchoolBuilding)}|${normalizeClassName(entry.className)}`;
}

function buildingAddressKey(address) {
    return norm(address).toLowerCase();
}

function classSortValue(className) {
    const normalized = normalizeClassName(className);
    const match = normalized.match(/^(\d{1,2})\s*[- ]?\s*(.*)$/);
    if (!match) {
        return { parallel: Number.MAX_SAFE_INTEGER, letter: normalized };
    }
    return { parallel: Number(match[1]), letter: match[2] || '' };
}

function compareClassRows(a, b) {
    const buildingCompare = buildingGroupCode(a?.numberSchoolBuilding)
        .localeCompare(buildingGroupCode(b?.numberSchoolBuilding), 'ru', { numeric: true });
    if (buildingCompare) return buildingCompare;
    const aClass = classSortValue(a?.className);
    const bClass = classSortValue(b?.className);
    if (aClass.parallel !== bClass.parallel) return aClass.parallel - bClass.parallel;
    return aClass.letter.localeCompare(bClass.letter, 'ru', { numeric: true });
}

function buildingChoiceKey(code, address) {
    return `${buildingGroupCode(code)}|${buildingAddressKey(address)}`;
}

function physicalSiteChoices() {
    return (buildings || [])
        .filter((b) => b?.id && norm(b.address))
        .map((b) => ({
            id: Number(b.id),
            code: buildingGroupCode(b.code),
            name: norm(b.name) || buildingGroupCode(b.code),
            address: norm(b.address)
        }))
        .sort((a, b) => (`${a.code}|${a.name}|${a.address}`).localeCompare(`${b.code}|${b.name}|${b.address}`, "ru", { numeric: true }));
}

function buildingGroupChoices() {
    const map = new Map();
    (buildings || []).forEach((b) => {
        const code = buildingGroupCode(b.code);
        if (!code) return;
        const name = norm(b.name) || code;
        if (!map.has(code)) map.set(code, { code, name });
    });
    return Array.from(map.values()).sort((a, b) => a.code.localeCompare(b.code, "ru", { numeric: true }));
}

function findPhysicalSiteById(id) {
    const numericId = Number(id);
    return physicalSiteChoices().find((b) => b.id === numericId) || null;
}

function findPhysicalSiteByAddress(address) {
    const key = buildingAddressKey(address);
    if (!key) return null;
    return physicalSiteChoices().find((b) => buildingAddressKey(b.address) === key) || null;
}

function findBuildingChoice(code, address = "") {
    const normalizedCode = buildingGroupCode(code);
    const normalizedAddress = buildingAddressKey(address);
    return physicalSiteChoices().find((b) => b.code === normalizedCode && (!normalizedAddress || buildingAddressKey(b.address) === normalizedAddress))
        || physicalSiteChoices().find((b) => b.code === normalizedCode);
}

function buildingLabel(code) {
    return code || "—";
}

function siteLabel(site) {
    return site ? `${site.name || site.code} — ${site.address}` : "";
}

function displayCampusAddress(entry) {
    const site = findPhysicalSiteById(entry?.schoolBuildingId) || findPhysicalSiteByAddress(entry?.campusAddress);
    return siteLabel(site) || norm(entry?.campusAddress) || "—";
}

function selectedPhysicalSite(selectEl) {
    return findPhysicalSiteById(selectEl?.value);
}

function syncCampusAddressFromSite(siteSelect, campusInput) {
    if (!campusInput) return;
    const site = selectedPhysicalSite(siteSelect);
    campusInput.value = site?.address || "";
}

function renderTeachers() {
    ui.teacherList.innerHTML = teachers
        .map((teacher) => `<option value="${esc(teacher.fioTeacher)}"></option>`)
        .join("");
}

function teacherIdForName(fioTeacher) {
    const value = norm(fioTeacher).toLowerCase();
    const matches = (teachers || []).filter(
        (teacher) => norm(teacher.fioTeacher).toLowerCase() === value
    );
    if (matches.length === 1) {
        return matches[0].id;
    }
    return null;
}

function fillBuildingOptions(selectEl, selectedValue = "") {
    if (!selectEl) return;
    selectEl.innerHTML = `<option value="">Выберите основной корпус</option>`;
    buildingGroupChoices().forEach((b) => {
        const option = document.createElement("option");
        option.value = b.code;
        option.textContent = b.code;
        option.title = b.name;
        selectEl.appendChild(option);
    });
    if (selectedValue) selectEl.value = buildingGroupCode(selectedValue);
}

function fillPhysicalSiteOptions(selectEl, selectedId = null, fallbackAddress = "", buildingCode = "") {
    if (!selectEl) return;
    const selectedGroup = buildingGroupCode(buildingCode);
    selectEl.innerHTML = `<option value="">Выберите физическую площадку / адрес</option>`;
    physicalSiteChoices()
        .filter((b) => !selectedGroup || b.code === selectedGroup)
        .forEach((b) => {
        const option = document.createElement("option");
        option.value = String(b.id);
        option.dataset.address = b.address;
        option.dataset.code = b.code;
        option.textContent = siteLabel(b);
        selectEl.appendChild(option);
    });
    const fallbackSite = selectedId ? null : findPhysicalSiteByAddress(fallbackAddress);
    const value = selectedId || fallbackSite?.id || "";
    if (value) selectEl.value = String(value);
}

function renderBuildings() {
    const currentBuilding = ui.building?.value || "";
    const currentEditBuilding = ui.editBuilding?.value || "";
    const currentSite = ui.schoolBuilding?.value || "";
    const currentEditSite = ui.editSchoolBuilding?.value || "";
    fillBuildingOptions(ui.building, currentBuilding);
    fillBuildingOptions(ui.editBuilding, currentEditBuilding);
    fillPhysicalSiteOptions(ui.schoolBuilding, currentSite, "", ui.building?.value || currentBuilding);
    fillPhysicalSiteOptions(ui.editSchoolBuilding, currentEditSite, "", ui.editBuilding?.value || currentEditBuilding);
    syncCampusAddressFromSite(ui.schoolBuilding, ui.form?.elements?.campusAddress);
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm?.elements?.campusAddress);
}

function openEditDialog(entry) {
    editingOriginalKey = entryKey(entry);
    editingOriginalEntry = { ...entry };
    const normalizedEntryCode = buildingGroupCode(entry.numberSchoolBuilding);
    fillBuildingOptions(ui.editBuilding, normalizedEntryCode || entry.numberSchoolBuilding || "");
    fillPhysicalSiteOptions(ui.editSchoolBuilding, entry.schoolBuildingId, entry.campusAddress || "", normalizedEntryCode);
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm.elements.campusAddress);
    ui.editForm.elements.className.value = entry.className || "";
    ui.editForm.elements.classType.value = entry.classType || "NORMAL";
    ui.editForm.elements.classDirection.value = entry.classDirection || "";
    ui.editForm.elements.fioTeacher.value = entry.fioTeacher || "";
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm.elements.campusAddress);
    updateTransferWarning();
    ui.editDialog.showModal();
}

function renderClasses(rows) {
    ui.body.innerHTML = "";
    classRows = (rows || []).slice().sort(compareClassRows);
    classRows.forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${esc(buildingLabel(r.numberSchoolBuilding, r.campusAddress))}</td>
            <td>${esc(r.className)}</td>
            <td>${esc((r.classType || "NORMAL") === "AOOP_UO" ? "АООП УО" : "Норма")}</td>
            <td>${esc(r.classDirection)}</td>
            <td>${esc(r.fioTeacher)}</td>
            <td>${esc(displayCampusAddress(r))}</td>
            <td><button type="button" class="inline-plus" title="Редактировать" data-edit-class="${esc(entryKey(r))}">✏️</button></td>
        `;
        ui.body.appendChild(tr);
    });

    ui.body.querySelectorAll('button[data-edit-class]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const item = classRows.find((x) => entryKey(x) === btn.dataset.editClass);
            if (item) openEditDialog(item);
        });
    });
}

async function reload() {
    updateTemplateLink();
    const [rows, buildingRows, teacherRows] = await Promise.all([
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/teachers")
    ]);
    classRows = rows || [];
    buildings = buildingRows || [];
    teachers = (teacherRows || [])
        .filter((r) => r?.id && norm(r.fioTeacher))
        .map((r) => ({
            id: Number(r.id),
            fioTeacher: norm(r.fioTeacher)
        }));
    renderTeachers();
    renderBuildings();
    syncCampusAddressFromSite(ui.schoolBuilding, ui.form.elements.campusAddress);
    renderClasses(classRows);
}

async function classDependencySummary(entry) {
    if (entry?.id) {
        return api(`/api/classroom-leadership/${encodeURIComponent(entry.id)}/dependencies`);
    }
    const building = buildingGroupCode(entry?.numberSchoolBuilding);
    const className = normalizeClassName(entry?.className);
    return api(`/api/classroom-leadership/one/dependencies?numberSchoolBuilding=${encodeURIComponent(building)}&className=${encodeURIComponent(className)}`);
}

function classDeleteWarning(className, building, dependencies) {
    const curriculumRows = Number(dependencies?.curriculumRows || 0);
    const manualLoadRows = Number(dependencies?.manualLoadRows || 0);
    const warnings = [];
    if (manualLoadRows > 0) warnings.push(`нагрузка: ${manualLoadRows} строк`);
    if (curriculumRows > 0) warnings.push(`учебный план: ${curriculumRows} строк`);
    const tailText = warnings.length
        ? `\n\nВНИМАНИЕ: вместе с классом будут удалены связанные хвосты (${warnings.join(", ")}).`
        : `\n\nСвязанной нагрузки и предметов учебного плана для этого класса не найдено.`;
    return `Удалить класс ${className} в корпусе ${building}?${tailText}`;
}

async function upsertEntry(entry, originalKey = null) {
    const current = await api("/api/classroom-leadership");
    const filtered = (current || []).filter((r) => {
        const key = entryKey(r);
        if (originalKey) return key !== originalKey;
        return key !== entryKey(entry);
    });
    filtered.push(entry);
    return api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(filtered) });
}

function buildingScopeChanged(entry) {
    if (!entry?.id || !editingOriginalEntry) return false;
    const originalSiteId = Number(editingOriginalEntry.schoolBuildingId) || null;
    const targetSiteId = Number(entry.schoolBuildingId) || null;
    return buildingGroupCode(editingOriginalEntry.numberSchoolBuilding) !== buildingGroupCode(entry.numberSchoolBuilding)
        || originalSiteId !== targetSiteId;
}

function updateTransferWarning() {
    if (!ui.transferWarning || !ui.editForm || !editingOriginalEntry) return;
    const targetBuilding = buildingGroupCode(ui.editForm.elements.numberSchoolBuilding.value);
    const originalBuilding = buildingGroupCode(editingOriginalEntry.numberSchoolBuilding);
    ui.transferWarning.hidden = !targetBuilding || targetBuilding === originalBuilding;
}

async function updateEntry(entry) {
    if (!entry?.id) {
        return upsertEntry(entry, editingOriginalKey);
    }
    const shouldTransferScope = buildingScopeChanged(entry);
    const ordinaryPatchEntry = shouldTransferScope
        ? {
            ...entry,
            numberSchoolBuilding: buildingGroupCode(editingOriginalEntry.numberSchoolBuilding),
            schoolBuildingId: Number(editingOriginalEntry.schoolBuildingId) || null,
            campusAddress: norm(editingOriginalEntry.campusAddress)
        }
        : entry;
    const saved = await api(`/api/classroom-leadership/${encodeURIComponent(entry.id)}`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify(ordinaryPatchEntry)
    });
    if (!shouldTransferScope) {
        return saved;
    }
    return api(`/api/classes/${encodeURIComponent(entry.id)}/building-scope`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify({ schoolBuildingId: entry.schoolBuildingId })
    });
}

updateTemplateLink();

ui.building?.addEventListener("change", () => {
    fillPhysicalSiteOptions(ui.schoolBuilding, null, "", ui.building.value);
    syncCampusAddressFromSite(ui.schoolBuilding, ui.form.elements.campusAddress);
});
ui.editBuilding?.addEventListener("change", () => {
    fillPhysicalSiteOptions(ui.editSchoolBuilding, null, "", ui.editBuilding.value);
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm.elements.campusAddress);
    updateTransferWarning();
});
ui.schoolBuilding?.addEventListener("change", () => syncCampusAddressFromSite(ui.schoolBuilding, ui.form.elements.campusAddress));
ui.editSchoolBuilding?.addEventListener("change", () => {
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm.elements.campusAddress);
    updateTransferWarning();
});

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    syncCampusAddressFromSite(ui.schoolBuilding, ui.form.elements.campusAddress);
    const form = new FormData(ui.form);
    const teacherName = norm(form.get("fioTeacher"));
    const teacherId = teacherIdForName(teacherName);
    if (!teacherId) {
        print({ error: "Выберите педагога из справочника" });
        return;
    }
    const entry = {
        numberSchoolBuilding: buildingGroupCode(form.get("numberSchoolBuilding")),
        schoolBuildingId: Number(form.get("schoolBuildingId")) || null,
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        teacherId,
        fioTeacher: teacherName,
        campusAddress: norm(form.get("campusAddress")),
        classType: norm(form.get("classType")) || "NORMAL"
    };

    if (!entry.numberSchoolBuilding || !entry.schoolBuildingId || !entry.className || !entry.classDirection || !entry.fioTeacher) {
        print({ error: "Заполните все поля" });
        return;
    }

    try {
        const saved = await upsertEntry(entry);
        print({ status: "saved", total: saved.length });
        ui.form.reset();
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    syncCampusAddressFromSite(ui.editSchoolBuilding, ui.editForm.elements.campusAddress);
    const form = new FormData(ui.editForm);
    const teacherName = norm(form.get("fioTeacher"));
    const teacherId = teacherIdForName(teacherName);
    if (!teacherId) {
        print({ error: "Выберите педагога из справочника" });
        return;
    }
    const entry = {
        id: editingOriginalEntry?.id || null,
        numberSchoolBuilding: buildingGroupCode(form.get("numberSchoolBuilding")),
        schoolBuildingId: Number(form.get("schoolBuildingId")) || null,
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        teacherId,
        fioTeacher: teacherName,
        campusAddress: norm(form.get("campusAddress")),
        classType: norm(form.get("classType")) || "NORMAL"
    };

    if (!entry.numberSchoolBuilding || !entry.schoolBuildingId || !entry.className || !entry.classDirection || !entry.fioTeacher) {
        print({ error: "Заполните все поля" });
        return;
    }

    try {
        const saved = await updateEntry(entry);
        ui.editDialog.close();
        print({ status: "updated", id: saved.id, numberSchoolBuilding: saved.numberSchoolBuilding, className: saved.className });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.editCloseBtn.addEventListener('click', () => ui.editDialog.close());
ui.editDeleteBtn?.addEventListener('click', async () => {
    const building = buildingGroupCode(editingOriginalEntry?.numberSchoolBuilding || ui.editForm.elements.numberSchoolBuilding.value);
    const className = normalizeClassName(editingOriginalEntry?.className || ui.editForm.elements.className.value);
    if (!building || !className) {
        print({ error: "Выберите корпус и класс для удаления" });
        return;
    }
    try {
        const dependencies = await classDependencySummary(editingOriginalEntry);
        if (!window.confirm(classDeleteWarning(className, building, dependencies))) return;
        const deleteUrl = editingOriginalEntry?.id
            ? `/api/classroom-leadership/${encodeURIComponent(editingOriginalEntry.id)}`
            : `/api/classroom-leadership/one?numberSchoolBuilding=${encodeURIComponent(building)}&className=${encodeURIComponent(className)}`;
        await api(deleteUrl, { method: "DELETE" });
        ui.editDialog.close();
        print({ status: "deleted", numberSchoolBuilding: building, className });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.importBtn.addEventListener("click", async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) return print({ error: "Выберите файл" });
    const form = new FormData();
    form.append("file", file);
    try {
        const result = await api("/api/classroom-leadership/import", { method: "POST", body: form });
        print(result);
        ui.fileInput.value = "";
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
ui.clearBtn.addEventListener("click", async () => {
    if (!window.confirm("Удалить все классы выбранного учебного года? Вместе с ними будут удалены вся нагрузка и весь учебный план этого года.")) return;
    try {
        await api("/api/classroom-leadership", { method: "DELETE" });
        print({ status: "cleared" });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

reload().catch((error) => print({ error: error.message }));
