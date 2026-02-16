const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    buildingTabs: document.getElementById("building-tabs"),
    refreshLoadBtn: document.getElementById("refresh-load-btn"),
    saveBuildingBtn: document.getElementById("save-building-btn"),
    loadResult: document.getElementById("load-result"),
    tableHead: document.getElementById("building-load-head"),
    tableBody: document.getElementById("building-load-body")
};

let curriculumRows = [];
let manualRows = [];
let teacherNames = [];
let buildings = [];
let selectedBuilding = "";
const state = {};

function buildingState(building) { if (!state[building]) state[building] = { teachers: {} }; return state[building]; }
async function api(path, options = {}) { const r = await fetch(path, options); const t = await r.text(); let b = null; try { b = t ? JSON.parse(t) : null; } catch { b = t ? { message: t } : null; } if (!r.ok) throw new Error(b?.message || b?.error || `HTTP ${r.status}`); return b; }
function print(v) { ui.loadResult.textContent = JSON.stringify(v, null, 2); }
function esc(v) { return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;"); }
function sortRu(a) { return [...a].sort((x, y) => String(x).localeCompare(String(y), "ru")); }
function keyOf(row) { return `${row.className}|${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}`; }
function partLabel(part) { if (part === "FORMABLE") return "Формируемая"; if (part === "EXTRACURRICULAR") return "Внеурочная"; return "Основная"; }
function educationLevelLabel(value) { if (value === "BASIC") return "Базовый"; if (value === "ADVANCED") return "Углублённый"; return String(value || ""); }

function rowsForSelectedBuilding() {
    return curriculumRows
        .filter((r) => String(r.numberSchoolBuilding || "").trim() === selectedBuilding)
        .sort((a, b) => `${a.className}${a.subjectName}`.localeCompare(`${b.className}${b.subjectName}`, "ru"));
}

function prefillFromManualLoad() {
    manualRows.forEach((entry) => {
        const building = String(entry.numberSchoolBuilding || "").trim();
        if (!building) return;
        const st = buildingState(building);
        const match = curriculumRows.find((r) =>
            String(r.numberSchoolBuilding || "").trim() === building
            && r.className === entry.className
            && r.subjectName === entry.subjectName
            && r.educationLevel === entry.educationLevel
        );
        if (!match) return;
        st.teachers[keyOf(match)] = entry.fioTeacher || "";
    });
}

function buildTeacherStats() {
    const stats = new Map();
    const ensure = (fio) => { if (!stats.has(fio)) stats.set(fio, { total: 0, byBuilding: {} }); return stats.get(fio); };

    buildings.forEach((b) => {
        const st = buildingState(b.code);
        curriculumRows.filter((r) => String(r.numberSchoolBuilding || "").trim() === b.code).forEach((row) => {
            const fio = String(st.teachers[keyOf(row)] || "").trim();
            if (!fio) return;
            const load = Number(row.plannedHours || 0);
            const item = ensure(fio);
            item.total += load;
            item.byBuilding[b.code] = (item.byBuilding[b.code] || 0) + load;
        });
    });

    return stats;
}

function formatHours(fio, stats) {
    const f = String(fio || "").trim();
    if (!f) return "";
    const item = stats.get(f);
    return `Корпус: ${item?.byBuilding?.[selectedBuilding] || 0} ч | Комплекс: ${item?.total || 0} ч`;
}

function makeSafeId(value) { return String(value).replace(/[^a-zA-Z0-9_-]/g, "_"); }
function updateDatalistOptions(listEl, query = "") {
    if (!listEl) return;
    const q = String(query || "").trim().toLowerCase();
    const rows = !q ? teacherNames.slice(0, 200) : teacherNames.filter((name) => name.toLowerCase().includes(q)).slice(0, 50);
    listEl.innerHTML = rows.map((name) => `<option value="${esc(name)}"></option>`).join("");
}

function updateHours(stats) {
    ui.tableBody.querySelectorAll(".teacher-hours").forEach((el) => {
        const key = el.dataset.key;
        const input = ui.tableBody.querySelector(`input[data-key="${key}"]`);
        el.textContent = formatHours(input?.value || "", stats);
    });
}

function renderBuildingTabs() {
    ui.buildingTabs.innerHTML = "";
    buildings.forEach((b) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `parallel-tab ${b.code === selectedBuilding ? "active" : ""}`;
        btn.textContent = `${b.code} — ${b.name}`;
        btn.addEventListener("click", () => { selectedBuilding = b.code; renderBuildingTabs(); renderTable(); });
        ui.buildingTabs.appendChild(btn);
    });
}

function renderTable() {
    const rows = rowsForSelectedBuilding();
    const st = buildingState(selectedBuilding);
    const stats = buildTeacherStats();

    ui.tableHead.innerHTML = "";
    ui.tableBody.innerHTML = "";

    const trHead = document.createElement("tr");
    trHead.innerHTML = `<th>Блок</th><th>Класс</th><th>Предмет</th><th>Уровень</th><th>Часы</th><th>Педагог</th>`;
    ui.tableHead.appendChild(trHead);

    rows.forEach((row) => {
        const key = keyOf(row);
        const val = st.teachers[key] || "";
        const safeId = makeSafeId(key);
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${partLabel(row.curriculumPart)}</td><td>${esc(row.className)}</td><td>${esc(row.subjectName)}</td><td>${esc(educationLevelLabel(row.educationLevel))}</td><td>${esc(row.plannedHours)}</td><td><input type="text" data-key="${esc(key)}" list="teacher-list-${safeId}" value="${esc(val)}" placeholder="Начните вводить ФИО"><datalist id="teacher-list-${safeId}"></datalist><div class="teacher-hours" data-key="${esc(key)}">${esc(formatHours(val, stats))}</div></td>`;
        ui.tableBody.appendChild(tr);
    });

    ui.tableBody.querySelectorAll("input[data-key]").forEach((input) => {
        const key = input.dataset.key;
        const listEl = document.getElementById(input.getAttribute("list"));
        updateDatalistOptions(listEl, input.value || "");
        input.addEventListener("input", () => {
            st.teachers[key] = String(input.value || "").trim();
            updateDatalistOptions(listEl, input.value || "");
            updateHours(buildTeacherStats());
        });
        input.addEventListener("blur", () => {
            const value = String(input.value || "").trim();
            if (!value) { st.teachers[key] = ""; updateHours(buildTeacherStats()); return; }
            const exact = teacherNames.find((name) => name.toLowerCase() === value.toLowerCase());
            if (!exact) { print({ warning: `Педагог «${value}» не найден в справочнике` }); input.value = ""; st.teachers[key] = ""; }
            else { input.value = exact; st.teachers[key] = exact; }
            updateHours(buildTeacherStats());
        });
    });
}

async function saveBuildingLoad() {
    const st = buildingState(selectedBuilding);
    const payload = rowsForSelectedBuilding().map((row) => {
        const fioTeacher = String(st.teachers[keyOf(row)] || "").trim();
        if (!fioTeacher) return null;
        return {
            fioTeacher,
            numberSchoolBuilding: selectedBuilding,
            subjectName: row.subjectName,
            className: row.className,
            load: Number(row.plannedHours || 0),
            groupNameEducationalPlan: null,
            groupLoad: null,
            educationLevel: row.educationLevel
        };
    }).filter(Boolean);

    if (!payload.length) return print({ warning: "Нет назначений для сохранения" });

    try {
        const result = await api("/api/manual-load/bulk", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
        print({ saved: result.length, building: selectedBuilding });
    } catch (error) { print({ error: error.message }); }
}

ui.saveBuildingBtn.addEventListener("click", saveBuildingLoad);

async function refreshSourceData() {
    const [curriculum, manual, teachers, buildingRows] = await Promise.all([
        api("/api/curriculum"), api("/api/manual-load"), api("/api/teachers"), api("/api/buildings")
    ]);
    curriculumRows = curriculum || [];
    manualRows = manual || [];
    teacherNames = sortRu(Array.from(new Set((teachers || []).map((t) => String(t.fioTeacher || "").trim()).filter(Boolean))));
    buildings = sortRu(buildingRows || []).sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));
    prefillFromManualLoad();
    if (!buildings.some((b) => b.code === selectedBuilding)) {
        selectedBuilding = buildings[0]?.code || "";
    }
    renderBuildingTabs();
    renderTable();
}

async function init() {
    try {
        await refreshSourceData();
        ui.refreshLoadBtn.addEventListener("click", () => {
            refreshSourceData().then(() => print({ status: "Синхронизировано с учебным планом" }))
                .catch((error) => print({ error: error.message }));
        });
        setInterval(() => { refreshSourceData().catch(() => {}); }, 30000);
    } catch (error) { print({ error: error.message }); }
}

init();
