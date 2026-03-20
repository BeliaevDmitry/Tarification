const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("class-form"),
    building: document.getElementById("class-building"),
    teacher: document.getElementById("class-teacher"),
    teacherList: document.getElementById("teacher-list"),
    refreshBtn: document.getElementById("refresh-classes-btn"),
    clearBtn: document.getElementById("clear-classes-btn"),
    bulkFile: document.getElementById("class-bulk-file"),
    bulkText: document.getElementById("class-bulk-json"),
    bulkBtn: document.getElementById("class-bulk-upload-btn"),
    result: document.getElementById("classes-result"),
    body: document.getElementById("classes-body")
};

let teachers = [];

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
    (rows || []).sort((a, b) => `${a.numberSchoolBuilding}${a.className}`.localeCompare(`${b.numberSchoolBuilding}${b.className}`, "ru"))
        .forEach((r) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${esc(r.numberSchoolBuilding)}</td><td>${esc(r.className)}</td><td>${esc(r.classDirection)}</td><td>${esc(r.fioTeacher)}</td>`;
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

    const filtered = (current || []).filter((r) => !(norm(r.numberSchoolBuilding) === entry.numberSchoolBuilding && norm(r.className) === entry.className));
    filtered.push(entry);

    const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(filtered) });
    print({ status: "saved", total: saved.length });
    ui.form.reset();
    await reload();
}

async function clearAll() {
    await api("/api/classroom-leadership", { method: "DELETE" });
    print({ status: "cleared" });
    await reload();
}

async function readTextInput(fileInput, textInput) {
    const file = fileInput?.files?.[0];
    if (file) return await file.text();
    return norm(textInput?.value);
}

async function bulkUploadClasses() {
    const raw = await readTextInput(ui.bulkFile, ui.bulkText);
    if (!raw) {
        print({ error: "Выберите JSON-файл или вставьте JSON-массив" });
        return;
    }

    let payload;
    try {
        payload = JSON.parse(raw);
    } catch (error) {
        print({ error: `Некорректный JSON: ${error.message}` });
        return;
    }

    if (!Array.isArray(payload)) {
        print({ error: "Ожидается JSON-массив классов" });
        return;
    }

    const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(payload) });
    print({ status: "bulk-loaded", total: saved.length });
    if (ui.bulkText) ui.bulkText.value = "";
    if (ui.bulkFile) ui.bulkFile.value = "";
    await reload();
}

ui.form.addEventListener("submit", (e) => saveClass(e).catch((error) => print({ error: error.message })));
ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
ui.clearBtn.addEventListener("click", () => clearAll().catch((error) => print({ error: error.message })));
ui.bulkBtn?.addEventListener("click", () => bulkUploadClasses().catch((error) => print({ error: error.message })));

function startAfterAuth() {
    reload().catch((error) => print({ error: error.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
