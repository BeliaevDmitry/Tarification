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
    importBtn: document.getElementById("import-classes-btn")
};

let teachers = [];
let buildings = [];
let classRows = [];

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

function buildingLabel(code) {
    const b = buildings.find((x) => x.code === code);
    return b ? `${b.name} (${b.address})` : code;
}

function renderTeachers() {
    ui.teacherList.innerHTML = teachers.map((fio) => `<option value="${esc(fio)}"></option>`).join("");
}

function renderBuildings() {
    const selected = ui.building.value;
    ui.building.innerHTML = `<option value="">Выберите корпус</option>`;
    buildings.sort((a, b) => String(a.name).localeCompare(String(b.name), "ru")).forEach((b) => {
        ui.building.innerHTML += `<option value="${esc(b.code)}">${esc(b.name)} — ${esc(b.address)}</option>`;
    });
    if (selected) ui.building.value = selected;
}

function fillForm(entry) {
    ui.form.elements.numberSchoolBuilding.value = entry.numberSchoolBuilding || "";
    ui.form.elements.className.value = entry.className || "";
    ui.form.elements.classDirection.value = entry.classDirection || "";
    ui.form.elements.fioTeacher.value = entry.fioTeacher || "";
    ui.form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function renderClasses(rows) {
    ui.body.innerHTML = "";
    classRows = (rows || []).slice();
    classRows.forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${esc(buildingLabel(r.numberSchoolBuilding))}</td><td>${esc(r.className)}</td><td>${esc(r.classDirection)}</td><td>${esc(r.fioTeacher)}</td>`;
        tr.addEventListener("click", () => fillForm(r));
        ui.body.appendChild(tr);
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

ui.form.addEventListener("submit", async (e) => {
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
        print({ error: "Заполните все поля" });
        return;
    }

    const exact = teachers.find((fio) => fio.toLowerCase() === entry.fioTeacher.toLowerCase());
    if (!exact) {
        print({ error: `Педагог «${entry.fioTeacher}» не найден` });
        return;
    }
    entry.fioTeacher = exact;

    const filtered = (current || []).filter((r) => !(norm(r.numberSchoolBuilding) === entry.numberSchoolBuilding && norm(r.className) === entry.className));
    filtered.push(entry);

    const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(filtered) });
    print({ status: "saved", total: saved.length });
    ui.form.reset();
    await reload();
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
