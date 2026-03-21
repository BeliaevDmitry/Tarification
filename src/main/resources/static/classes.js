const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("class-form"),
    building: document.getElementById("class-building"),
    teacher: document.getElementById("class-teacher"),
    teacherList: document.getElementById("teacher-list"),
    saveBtn: document.getElementById("save-class-btn"),
    refreshBtn: document.getElementById("refresh-classes-btn"),
    clearBtn: document.getElementById("clear-classes-btn"),
    cancelEditBtn: document.getElementById("cancel-class-edit-btn"),
    importFile: document.getElementById("class-import-file"),
    importBtn: document.getElementById("class-import-btn"),
    result: document.getElementById("classes-result"),
    body: document.getElementById("classes-body")
};

let teachers = [];
let currentRows = [];
let editingKey = null;

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function esc(v) {
    return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function sortRu(arr) { return [...arr].sort((a, b) => String(a).localeCompare(String(b), "ru")); }
function norm(v) { return String(v || "").trim(); }
function print(v) { ui.result.textContent = JSON.stringify(v, null, 2); }

function normalizeClassName(value) {
    const v = norm(value).toUpperCase().replace(/[–—]/g, "-");
    const m = v.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return m ? `${m[1]}-${m[2]}` : v;
}

function classKey(row) {
    return `${norm(row.numberSchoolBuilding)}|${normalizeClassName(row.className)}`;
}

function canEditClasses() {
    const role = window.getCurrentUser?.()?.role;
    return ["ADMIN", "DIRECTOR", "DEPUTY_DIRECTOR"].includes(role);
}

function resetFormState() {
    editingKey = null;
    ui.form.reset();
    ui.cancelEditBtn.hidden = true;
    if (ui.saveBtn) {
        ui.saveBtn.textContent = "Добавить / обновить класс";
    }
}

function renderTeachers() {
    ui.teacherList.innerHTML = sortRu(teachers).map((fio) => `<option value="${esc(fio)}"></option>`).join("");
}

function renderBuildings(rows) {
    const selected = ui.building.value;
    ui.building.innerHTML = `<option value="">Выберите корпус</option>`;
    (rows || []).sort((a, b) => String(a.code).localeCompare(String(b.code), "ru")).forEach((b) => {
        ui.building.innerHTML += `<option value="${esc(b.code)}">${esc(b.code)} — ${esc(b.name)}</option>`;
    });
    if (selected) ui.building.value = selected;
}

function renderClasses(rows) {
    ui.body.innerHTML = "";
    currentRows = [...(rows || [])];
    (rows || []).sort((a, b) => `${a.numberSchoolBuilding}${a.className}`.localeCompare(`${b.numberSchoolBuilding}${b.className}`, "ru"))
        .forEach((r) => {
            const tr = document.createElement("tr");
            const actions = canEditClasses()
                ? `<div class="row compact-row">
                        <button type="button" class="table-action-btn" data-edit-class="${esc(classKey(r))}">Редактировать</button>
                        <button type="button" class="danger-btn table-action-btn" data-delete-class="${esc(classKey(r))}">Удалить</button>
                   </div>`
                : "";
            tr.innerHTML = `<td>${esc(r.numberSchoolBuilding)}</td><td>${esc(r.className)}</td><td>${esc(r.classDirection)}</td><td>${esc(r.fioTeacher)}</td><td>${actions}</td>`;
            ui.body.appendChild(tr);
        });
}

async function reload() {
    const [classRows, buildingRows, teacherRows] = await Promise.all([
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/teachers")
    ]);
    teachers = (teacherRows || []).map((r) => norm(r.fioTeacher)).filter(Boolean);
    renderTeachers();
    renderBuildings(buildingRows || []);
    renderClasses(classRows || []);
    return classRows || [];
}

async function saveClass(e) {
    e.preventDefault();
    const current = await api("/api/classroom-leadership");

    const form = new FormData(ui.form);
    const entry = {
        numberSchoolBuilding: norm(form.get("numberSchoolBuilding")),
        className: normalizeClassName(form.get("className")),
        classDirection: norm(form.get("classDirection")),
        fioTeacher: norm(form.get("fioTeacher"))
    };

    if (!entry.numberSchoolBuilding || !entry.className || !entry.classDirection || !entry.fioTeacher) {
        print({ error: "Заполните корпус, класс, направление и классного руководителя" });
        return;
    }

    const exact = teachers.find((fio) => fio.toLowerCase() === entry.fioTeacher.toLowerCase());
    if (!exact) {
        print({ error: `Педагог «${entry.fioTeacher}» не найден в справочнике` });
        return;
    }
    entry.fioTeacher = exact;

    const targetKey = editingKey || classKey(entry);
    const filtered = (current || []).filter((r) => classKey(r) !== targetKey);
    filtered.push(entry);

    const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(filtered) });
    print({ status: "saved", total: saved.length });
    resetFormState();
    await reload();
}

async function clearAll() {
    await api("/api/classroom-leadership", { method: "DELETE" });
    print({ status: "cleared" });
    await reload();
}

async function importClassesFromExcel() {
    const file = ui.importFile?.files?.[0];
    if (!file) {
        print({ error: "Выберите Excel-файл классов" });
        return;
    }

    const form = new FormData();
    form.append("file", file);
    const result = await api("/api/classroom-leadership/import", { method: "POST", body: form });
    print(result);
    ui.importFile.value = "";
    await reload();
}

function startEditClass(key) {
    const row = currentRows.find((item) => classKey(item) === key);
    if (!row) {
        print({ error: "Класс для редактирования не найден" });
        return;
    }

    editingKey = key;
    ui.building.value = norm(row.numberSchoolBuilding);
    ui.form.elements.namedItem("className").value = norm(row.className);
    ui.form.elements.namedItem("classDirection").value = norm(row.classDirection);
    ui.form.elements.namedItem("fioTeacher").value = norm(row.fioTeacher);
    ui.cancelEditBtn.hidden = false;
    if (ui.saveBtn) {
        ui.saveBtn.textContent = "Сохранить изменения класса";
    }
}

async function deleteClass(key) {
    const filtered = currentRows.filter((row) => classKey(row) !== key);
    const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(filtered) });
    print({ status: "deleted", total: saved.length });
    if (editingKey === key) {
        resetFormState();
    }
    await reload();
}

ui.form.addEventListener("submit", (e) => saveClass(e).catch((error) => print({ error: error.message })));
ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
ui.clearBtn.addEventListener("click", () => clearAll().catch((error) => print({ error: error.message })));
ui.cancelEditBtn?.addEventListener("click", resetFormState);
ui.importBtn?.addEventListener("click", () => importClassesFromExcel().catch((error) => print({ error: error.message })));
ui.body.addEventListener("click", (event) => {
    const editKey = event.target.dataset.editClass;
    const deleteKey = event.target.dataset.deleteClass;
    if (editKey) {
        startEditClass(editKey);
    }
    if (deleteKey) {
        deleteClass(deleteKey).catch((error) => print({ error: error.message }));
    }
});

function startAfterAuth() {
    resetFormState();
    reload().catch((error) => print({ error: error.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
