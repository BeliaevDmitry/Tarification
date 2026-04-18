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
const sortState = {
    field: "building",
    direction: "asc"
};
const ACADEMIC_YEAR_STORAGE_KEY = "tarification.academicYear";

function withAcademicYearScope(path) {
    if (typeof window.withAcademicYear === "function") {
        return window.withAcademicYear(path);
    }
    const selectedYear = sessionStorage.getItem(ACADEMIC_YEAR_STORAGE_KEY) || "";
    if (!selectedYear) return path;
    const separator = path.includes("?") ? "&" : "?";
    return `${path}${separator}academicYear=${encodeURIComponent(selectedYear)}`;
}

async function api(path, options = {}) {
    const scopedPath = withAcademicYearScope(path);
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
    return norm(value).replaceAll(" ", "");
}

function entryKey(entry) {
    return `${normalizeBuildingCode(entry.numberSchoolBuilding)}|${normalizeClassName(entry.className)}`;
}

function buildingLabel(code) {
    const b = buildings.find((x) => x.code === code);
    return b ? `${b.name} (${b.address})` : code;
}

function renderTeachers() {
    ui.teacherList.innerHTML = teachers.map((fio) => `<option value="${esc(fio)}"></option>`).join("");
}

function fillBuildingOptions(selectEl, selectedValue = "") {
    selectEl.innerHTML = `<option value="">Выберите корпус</option>`;
    buildings.sort((a, b) => String(a.name).localeCompare(String(b.name), "ru")).forEach((b) => {
        selectEl.innerHTML += `<option value="${esc(b.code)}">${esc(b.name)} — ${esc(b.address)}</option>`;
    });
    if (selectedValue) selectEl.value = selectedValue;
}

function renderBuildings() {
    fillBuildingOptions(ui.building, ui.building.value);
    fillBuildingOptions(ui.editBuilding, ui.editBuilding.value);
}

function openEditDialog(entry) {
    editingOriginalKey = entryKey(entry);
    editingOriginalEntry = { ...entry };
    const normalizedEntryCode = normalizeBuildingCode(entry.numberSchoolBuilding);
    const matchingBuilding = buildings.find((b) => normalizeBuildingCode(b.code) === normalizedEntryCode);
    ui.editForm.elements.numberSchoolBuilding.value = matchingBuilding?.code || entry.numberSchoolBuilding || "";
    ui.editForm.elements.className.value = entry.className || "";
    ui.editForm.elements.classDirection.value = entry.classDirection || "";
    ui.editForm.elements.fioTeacher.value = entry.fioTeacher || "";
    ui.editForm.elements.campusAddress.value = entry.campusAddress || "";
    ui.editDialog.showModal();
}

function renderClasses(rows) {
    ui.body.innerHTML = "";
    const sourceRows = (rows || []).slice();
    const collator = new Intl.Collator("ru", { numeric: true, sensitivity: "base" });
    const sortedRows = sourceRows.sort((a, b) => {
        const field = sortState.field;
        const dir = sortState.direction === "desc" ? -1 : 1;
        const aValue = field === "building"
            ? String(buildingLabel(a.numberSchoolBuilding || "")).trim()
            : String(a[field] || "").trim();
        const bValue = field === "building"
            ? String(buildingLabel(b.numberSchoolBuilding || "")).trim()
            : String(b[field] || "").trim();
        return collator.compare(aValue, bValue) * dir;
    });
    classRows = sortedRows;
    classRows.forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${esc(buildingLabel(r.numberSchoolBuilding))}</td>
            <td>${esc(r.className)}</td>
            <td>${esc(r.classDirection)}</td>
            <td>${esc(r.fioTeacher)}</td>
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

function updateSortButtons() {
    document.querySelectorAll('button[data-sort-key]').forEach((btn) => {
        const isActive = btn.dataset.sortKey === sortState.field;
        btn.classList.toggle("active", isActive);
        const label = btn.textContent.replace(/\s[↑↓]$/, "");
        btn.textContent = isActive
            ? `${label} ${sortState.direction === "asc" ? "↑" : "↓"}`
            : label;
    });
}

async function reload() {
    const [rows, buildingRows, teacherRows, curriculumRows] = await Promise.all([
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/teachers"),
        api("/api/curriculum")
    ]);
    const actualRows = rows || [];
    buildings = buildingRows || [];
    teachers = (teacherRows || []).map((r) => norm(r.fioTeacher)).filter(Boolean);
    const buildingAddressByCode = new Map(
        buildings.map((b) => [normalizeBuildingCode(b.code), norm(b.address) || "Не указан"])
    );
    const existingByKey = new Map((actualRows || []).map((row) => [entryKey(row), row]));
    (curriculumRows || [])
        .filter((row) => !row?.deprecated)
        .forEach((row) => {
            const candidate = {
                numberSchoolBuilding: normalizeBuildingCode(row.numberSchoolBuilding),
                className: normalizeClassName(row.className),
                classDirection: "Не указана",
                fioTeacher: "Класс не назначен",
                campusAddress: buildingAddressByCode.get(normalizeBuildingCode(row.numberSchoolBuilding)) || "Не указан"
            };
            const key = entryKey(candidate);
            if (!existingByKey.has(key)) {
                existingByKey.set(key, candidate);
            }
        });
    classRows = Array.from(existingByKey.values());
    const templateLink = document.getElementById("download-classes-template");
    if (templateLink) {
        templateLink.href = withAcademicYearScope("/api/classroom-leadership/template");
    }
    renderTeachers();
    renderBuildings();
    renderClasses(classRows);
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

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(ui.form);
    const entry = {
        numberSchoolBuilding: normalizeBuildingCode(form.get("numberSchoolBuilding")),
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        fioTeacher: norm(form.get("fioTeacher")),
        campusAddress: norm(form.get("campusAddress"))
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
    const form = new FormData(ui.editForm);
    const entry = {
        numberSchoolBuilding: normalizeBuildingCode(form.get("numberSchoolBuilding")),
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        fioTeacher: norm(form.get("fioTeacher")),
        campusAddress: norm(form.get("campusAddress"))
    };

    if (!entry.numberSchoolBuilding || !entry.className || !entry.classDirection || !entry.fioTeacher) {
        print({ error: "Заполните все поля" });
        return;
    }

    try {
        const saved = await upsertEntry(entry, editingOriginalKey);
        ui.editDialog.close();
        print({ status: "updated", total: saved.length });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.editCloseBtn.addEventListener('click', () => ui.editDialog.close());
document.querySelectorAll('button[data-sort-key]').forEach((button) => {
    button.addEventListener('click', () => {
        const nextField = button.dataset.sortKey;
        if (sortState.field === nextField) {
            sortState.direction = sortState.direction === "asc" ? "desc" : "asc";
        } else {
            sortState.field = nextField;
            sortState.direction = "asc";
        }
        updateSortButtons();
        renderClasses(classRows);
    });
});
ui.editDeleteBtn?.addEventListener('click', async () => {
    const building = normalizeBuildingCode(editingOriginalEntry?.numberSchoolBuilding || ui.editForm.elements.numberSchoolBuilding.value);
    const className = normalizeClassName(editingOriginalEntry?.className || ui.editForm.elements.className.value);
    if (!building || !className) {
        print({ error: "Выберите корпус и класс для удаления" });
        return;
    }
    if (!window.confirm(`Удалить класс ${className} в корпусе ${building}?`)) return;
    try {
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
    try {
        await api("/api/classroom-leadership", { method: "DELETE" });
        print({ status: "cleared" });
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

updateSortButtons();
reload().catch((error) => print({ error: error.message }));
