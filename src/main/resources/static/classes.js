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

async function api(path, options = {}) {
    const response = await fetch(path, options);
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
    return norm(value).replaceAll(" ", "").toUpperCase();
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
    ui.editForm.elements.numberSchoolBuilding.value = normalizeBuildingCode(entry.numberSchoolBuilding);
    ui.editForm.elements.className.value = entry.className || "";
    ui.editForm.elements.classDirection.value = entry.classDirection || "";
    ui.editForm.elements.fioTeacher.value = entry.fioTeacher || "";
    ui.editDialog.showModal();
}

function renderClasses(rows) {
    ui.body.innerHTML = "";
    classRows = (rows || []).slice();
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

async function reload() {
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
        fioTeacher: norm(form.get("fioTeacher"))
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
        fioTeacher: norm(form.get("fioTeacher"))
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
ui.editDeleteBtn?.addEventListener('click', async () => {
    const building = normalizeBuildingCode(ui.editForm.elements.numberSchoolBuilding.value);
    const className = normalizeClassName(ui.editForm.elements.className.value);
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

reload().catch((error) => print({ error: error.message }));
