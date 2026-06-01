const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("class-form"),
    building: document.getElementById("class-building"),
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
    editDeleteBtn: document.getElementById("class-edit-delete-btn"),
    editCloseBtn: document.getElementById("class-edit-close-btn")
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

function findBuildingChoice(code, address = "") {
    const normalizedCode = buildingGroupCode(code);
    const normalizedAddress = buildingAddressKey(address);
    return buildingChoices().find((b) => buildingGroupCode(b.code) === normalizedCode && (!normalizedAddress || buildingAddressKey(b.address) === normalizedAddress))
        || buildingChoices().find((b) => buildingGroupCode(b.code) === normalizedCode);
}

function buildingLabel(code, address = "") {
    const b = findBuildingChoice(code, address);
    return b ? `${b.name} — ${b.address}` : code;
}

function buildingChoices() {
    const map = new Map();
    (buildings || []).forEach((b) => {
        const code = buildingGroupCode(b.code);
        const address = norm(b.address);
        if (!code || !address) return;
        map.set(buildingChoiceKey(code, address), { code, name: norm(b.name) || code, address });
    });
    return Array.from(map.values()).sort((a, b) => (`${a.name}|${a.address}`).localeCompare(`${b.name}|${b.address}`, "ru"));
}

function buildingAddresses() {
    const map = new Map();
    (buildings || []).forEach((b) => {
        const address = norm(b.address);
        if (!address) return;
        map.set(buildingAddressKey(address), address);
    });
    return Array.from(map.values()).sort((a, b) => a.localeCompare(b, "ru"));
}

function displayCampusAddress(entry) {
    return norm(entry?.campusAddress) || norm(findBuildingChoice(entry?.numberSchoolBuilding)?.address) || "—";
}

function selectedBuildingChoice(selectEl) {
    const option = selectEl?.selectedOptions?.[0];
    if (!option) return null;
    return {
        code: buildingGroupCode(option.value),
        address: option.dataset.address || "",
        name: option.dataset.name || option.textContent || option.value
    };
}

function ensureAddressOption(selectEl, address) {
    const value = norm(address);
    if (!selectEl || !value) return;
    const exists = Array.from(selectEl.options).some((option) => option.value === value);
    if (exists) return;
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    selectEl.appendChild(option);
}

function setAddressValue(selectEl, address) {
    if (!selectEl) return;
    const value = norm(address);
    ensureAddressOption(selectEl, value);
    selectEl.value = value;
}

function fillCampusAddressOptions(selectEl, selectedAddress = "") {
    if (!selectEl) return;
    selectEl.innerHTML = `<option value="">Адрес выбранного корпуса</option>`;
    buildingAddresses().forEach((address) => {
        const option = document.createElement("option");
        option.value = address;
        option.textContent = address;
        selectEl.appendChild(option);
    });
    setAddressValue(selectEl, selectedAddress);
}

function applyBuildingAddress(selectEl, addressInput, force = false) {
    const choice = selectedBuildingChoice(selectEl);
    if (!choice || !addressInput) return;
    if (force || !norm(addressInput.value)) {
        setAddressValue(addressInput, choice.address || "");
    }
}

function renderTeachers() {
    ui.teacherList.innerHTML = teachers.map((fio) => `<option value="${esc(fio)}"></option>`).join("");
}

function fillBuildingOptions(selectEl, selectedValue = "", selectedAddress = "") {
    selectEl.innerHTML = `<option value="">Выберите корпус</option>`;
    buildingChoices().forEach((b) => {
        const option = document.createElement("option");
        option.value = buildingGroupCode(b.code);
        option.dataset.address = b.address;
        option.dataset.name = b.name;
        option.dataset.choiceKey = buildingChoiceKey(b.code, b.address);
        option.textContent = `${b.name} — ${b.address}`;
        selectEl.appendChild(option);
    });
    if (!selectedValue) return;
    const selectedKey = buildingChoiceKey(selectedValue, selectedAddress);
    const option = Array.from(selectEl.options).find((opt) => opt.dataset.choiceKey === selectedKey)
        || Array.from(selectEl.options).find((opt) => buildingGroupCode(opt.value) === buildingGroupCode(selectedValue));
    if (option) selectEl.selectedIndex = option.index;
}

function renderBuildings() {
    const currentBuildingAddress = ui.form?.elements?.campusAddress?.value || "";
    const currentEditAddress = ui.editForm?.elements?.campusAddress?.value || "";
    fillBuildingOptions(ui.building, ui.building.value, currentBuildingAddress);
    fillBuildingOptions(ui.editBuilding, ui.editBuilding.value, currentEditAddress);
    fillCampusAddressOptions(ui.form?.elements?.campusAddress, currentBuildingAddress);
    fillCampusAddressOptions(ui.editForm?.elements?.campusAddress, currentEditAddress);
}

function openEditDialog(entry) {
    editingOriginalKey = entryKey(entry);
    editingOriginalEntry = { ...entry };
    const normalizedEntryCode = buildingGroupCode(entry.numberSchoolBuilding);
    fillBuildingOptions(ui.editBuilding, normalizedEntryCode || entry.numberSchoolBuilding || "", entry.campusAddress || "");
    const campusAddress = entry.campusAddress || norm(selectedBuildingChoice(ui.editBuilding)?.address) || "";
    fillCampusAddressOptions(ui.editForm.elements.campusAddress, campusAddress);
    ui.editForm.elements.className.value = entry.className || "";
    ui.editForm.elements.classType.value = entry.classType || "NORMAL";
    ui.editForm.elements.classDirection.value = entry.classDirection || "";
    ui.editForm.elements.fioTeacher.value = entry.fioTeacher || "";
    setAddressValue(ui.editForm.elements.campusAddress, campusAddress);
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
    teachers = (teacherRows || []).map((r) => norm(r.fioTeacher)).filter(Boolean);
    renderTeachers();
    renderBuildings();
    applyBuildingAddress(ui.building, ui.form.elements.campusAddress);
    renderClasses(classRows);
}

async function classDependencySummary(building, className) {
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

async function updateEntry(entry) {
    if (!entry?.id) {
        return upsertEntry(entry, editingOriginalKey);
    }
    return api(`/api/classroom-leadership/${encodeURIComponent(entry.id)}`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify(entry)
    });
}

updateTemplateLink();

ui.building?.addEventListener("change", () => applyBuildingAddress(ui.building, ui.form.elements.campusAddress, true));
ui.editBuilding?.addEventListener("change", () => applyBuildingAddress(ui.editBuilding, ui.editForm.elements.campusAddress, true));

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    applyBuildingAddress(ui.building, ui.form.elements.campusAddress);
    const form = new FormData(ui.form);
    const entry = {
        numberSchoolBuilding: buildingGroupCode(form.get("numberSchoolBuilding")),
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        fioTeacher: norm(form.get("fioTeacher")),
        campusAddress: norm(form.get("campusAddress")),
        classType: norm(form.get("classType")) || "NORMAL"
    };

    if (!entry.numberSchoolBuilding || !entry.className || !entry.classDirection || !entry.fioTeacher) {
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
    applyBuildingAddress(ui.editBuilding, ui.editForm.elements.campusAddress);
    const form = new FormData(ui.editForm);
    const entry = {
        id: editingOriginalEntry?.id || null,
        numberSchoolBuilding: buildingGroupCode(form.get("numberSchoolBuilding")),
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        fioTeacher: norm(form.get("fioTeacher")),
        campusAddress: norm(form.get("campusAddress")),
        classType: norm(form.get("classType")) || "NORMAL"
    };

    if (!entry.numberSchoolBuilding || !entry.className || !entry.classDirection || !entry.fioTeacher) {
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
        const dependencies = await classDependencySummary(building, className);
        if (!window.confirm(classDeleteWarning(className, building, dependencies))) return;
        await api(`/api/classroom-leadership/one?numberSchoolBuilding=${encodeURIComponent(building)}&className=${encodeURIComponent(className)}`, { method: "DELETE" });
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
