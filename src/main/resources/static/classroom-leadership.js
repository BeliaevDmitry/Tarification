const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    tabs: document.getElementById("building-tabs"),
    saveBtn: document.getElementById("save-classroom-leadership-btn"),
    reloadBtn: document.getElementById("reload-classroom-leadership-btn"),
    clearBtn: document.getElementById("clear-classroom-leadership-btn"),
    result: document.getElementById("classroom-leadership-result"),
    body: document.getElementById("classroom-leadership-body")
};

let buildings = [];
let selectedBuilding = "";
let classes = [];
let teacherNames = [];
const state = {};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}
const print = (v) => { ui.result.textContent = JSON.stringify(v, null, 2); };
const esc = (v) => String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
const normalize = (v) => String(v || "").trim();
const sortRu = (arr) => [...arr].sort((a, b) => String(a).localeCompare(String(b), "ru"));
const keyOf = (building, className) => `${building}|${className}`;
const makeSafeId = (v) => String(v).replace(/[^a-zA-Z0-9_-]/g, "_");

function renderTabs() {
    ui.tabs.innerHTML = "";
    buildings.forEach((b) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `parallel-tab ${b.code === selectedBuilding ? "active" : ""}`;
        btn.textContent = `${b.code} — ${b.name}`;
        btn.addEventListener("click", () => { selectedBuilding = b.code; renderTabs(); renderTable(); });
        ui.tabs.appendChild(btn);
    });
}

function updateDatalistOptions(listEl, query = "") {
    if (!listEl) return;
    const q = normalize(query).toLowerCase();
    const options = !q ? teacherNames.slice(0, 200) : teacherNames.filter((fio) => fio.toLowerCase().includes(q)).slice(0, 50);
    listEl.innerHTML = options.map((fio) => `<option value="${esc(fio)}"></option>`).join("");
}

function renderTable() {
    ui.body.innerHTML = "";
    classes.filter((c) => c.numberSchoolBuilding === selectedBuilding).forEach((className) => {
        const key = keyOf(selectedBuilding, className.className);
        const value = state[key] || "";
        const safe = makeSafeId(key);
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${esc(className.className)}</td><td><input type="text" data-key="${esc(key)}" list="teachers-${safe}" value="${esc(value)}" placeholder="Начните вводить ФИО"><datalist id="teachers-${safe}"></datalist></td>`;
        ui.body.appendChild(tr);
    });

    ui.body.querySelectorAll("input[data-key]").forEach((input) => {
        const key = input.dataset.key;
        const listEl = document.getElementById(input.getAttribute("list"));
        updateDatalistOptions(listEl, input.value || "");
        input.addEventListener("input", () => { state[key] = normalize(input.value); updateDatalistOptions(listEl, input.value); });
        input.addEventListener("blur", () => {
            const val = normalize(input.value);
            if (!val) { state[key] = ""; return; }
            const exact = teacherNames.find((fio) => fio.toLowerCase() === val.toLowerCase());
            if (!exact) { print({ warning: `Педагог «${val}» не найден в справочнике` }); state[key] = ""; input.value = ""; return; }
            state[key] = exact;
            input.value = exact;
        });
    });
}

async function saveAssignments() {
    const payload = [];
    classes.forEach((c) => {
        const key = keyOf(c.numberSchoolBuilding, c.className);
        const fioTeacher = normalize(state[key]);
        if (!fioTeacher) return;
        payload.push({ numberSchoolBuilding: c.numberSchoolBuilding, className: c.className, fioTeacher });
    });
    try {
        const saved = await api("/api/classroom-leadership", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(payload) });
        print({ saved: saved.length });
    } catch (error) { print({ error: error.message }); }
}

async function clearAssignments() {
    try {
        await api("/api/classroom-leadership", { method: "DELETE" });
        Object.keys(state).forEach((k) => { state[k] = ""; });
        renderTable();
        print({ status: "cleared" });
    } catch (error) { print({ error: error.message }); }
}

async function reload() {
    try {
        const [curriculumRows, teachers, assignments, buildingRows] = await Promise.all([
            api("/api/curriculum"), api("/api/teachers"), api("/api/classroom-leadership"), api("/api/buildings")
        ]);
        buildings = sortRu(buildingRows || []).sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));
        selectedBuilding = selectedBuilding || buildings[0]?.code || "";
        teacherNames = sortRu(Array.from(new Set((teachers || []).map((r) => normalize(r.fioTeacher)).filter(Boolean))));
        classes = Array.from(new Map((curriculumRows || [])
            .filter((r) => normalize(r.numberSchoolBuilding) && normalize(r.className))
            .map((r) => [`${normalize(r.numberSchoolBuilding)}|${normalize(r.className)}`, { numberSchoolBuilding: normalize(r.numberSchoolBuilding), className: normalize(r.className) }])
        ).values()).sort((a, b) => `${a.numberSchoolBuilding}${a.className}`.localeCompare(`${b.numberSchoolBuilding}${b.className}`, "ru"));
        Object.keys(state).forEach((k) => delete state[k]);
        (assignments || []).forEach((a) => { state[keyOf(normalize(a.numberSchoolBuilding), normalize(a.className))] = normalize(a.fioTeacher); });
        renderTabs();
        renderTable();
    } catch (error) { print({ error: error.message }); }
}

ui.saveBtn.addEventListener("click", saveAssignments);
ui.reloadBtn.addEventListener("click", reload);
ui.clearBtn.addEventListener("click", clearAssignments);
reload();
