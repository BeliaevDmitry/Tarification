const jsonHeaders = { "Content-Type": "application/json" };

const PART_META = {
    CORE: { label: "Основная часть", short: "основная", order: 1 },
    FORMABLE: { label: "Формируемая часть", short: "формируемая", order: 2 },
    EXTRACURRICULAR: { label: "Внеурочная деятельность", short: "внеурочная", order: 3 },
    CORRECTIONAL: { label: "Коррекционная область", short: "коррекционная", order: 4 }
};

const PERIOD_META = {
    YEAR: { label: "Учебный год", short: "год" },
    H1: { label: "1 полугодие", short: "1П" },
    H2: { label: "2 полугодие", short: "2П" }
};

const CORE_AREA_ORDER = [
    "Русский язык и литература",
    "Иностранные языки",
    "Математика и информатика",
    "Общественно-научные предметы",
    "Основы духовно-нравственной культуры народов России",
    "Естественно-научные предметы",
    "Искусство",
    "Технология",
    "Физическая культура и основы безопасности и защиты Родины",
    "Коррекционно-развивающая область",
    "Иное"
];

const CORE_AREA_ALIASES = {
    "русский язык и литературное чтение": "Русский язык и литература",
    "русский язык": "Русский язык и литература",
    "иностранный язык": "Иностранные языки",
    "иностранные языки": "Иностранные языки",
    "общественно научные предметы": "Общественно-научные предметы",
    "естественно научные предметы": "Естественно-научные предметы",
    "коррекционно развивающая область": "Коррекционно-развивающая область"
};

const ui = {
    parallelTabs: document.getElementById("parallel-tabs"),
    buildingFilter: document.getElementById("parallel-building-filter"),
    createMetaGroupBtn: document.getElementById("create-meta-group-btn"),
    manageMetaGroupBtn: document.getElementById("manage-meta-group-btn"),
    refreshBtn: document.getElementById("refresh-btn"),
    clearBtn: document.getElementById("clear-curriculum-btn"),
    result: document.getElementById("curriculum-result"),
    form: document.getElementById("subject-form"),
    formBuilding: document.getElementById("subject-building"),
    formClass: document.getElementById("subject-class"),
    formSubject: document.getElementById("subject-name"),
    formStudyPeriod: document.getElementById("subject-study-period"),
    importFile: document.getElementById("curriculum-import-file"),
    importBtn: document.getElementById("import-curriculum-btn"),
    exportBtn: document.getElementById("export-curriculum-btn"),
    exportParallelsBtn: document.getElementById("export-curriculum-parallels-btn"),
    subgroupRequired: document.getElementById("subgroup-required"),
    subgroupConfig: document.getElementById("subgroup-config"),
    modularSystem: document.getElementById("modular-system"),
    moduleConfig: document.getElementById("module-config"),
    moduleList: document.getElementById("module-list"),
    moduleHoursSummary: document.getElementById("module-hours-summary"),
    addModuleBtn: document.getElementById("add-module-btn"),
    displayMode: document.getElementById("curriculum-display-mode"),
    subjectExclusionLabel: document.getElementById("subject-exclusion-label"),
    subjectMetaGroupInfo: document.getElementById("subject-meta-group-info"),
    summaryHead: document.getElementById("summary-head"),
    summaryBody: document.getElementById("summary-body"),
    editDialog: document.getElementById("curriculum-edit-dialog"),
    editForm: document.getElementById("curriculum-edit-form"),
    deleteItemBtn: document.getElementById("delete-curriculum-item"),
    closeDialogBtn: document.getElementById("close-curriculum-dialog"),
    editExclusionLabel: document.getElementById("edit-exclusion-label"),
    editMetaGroupInfo: document.getElementById("edit-meta-group-info")
    ,editModuleConfig: document.getElementById("edit-module-config")
    ,editSubgroupConfig: document.getElementById("edit-subgroup-config")
    ,editModuleList: document.getElementById("edit-module-list")
    ,editModuleHoursSummary: document.getElementById("edit-module-hours-summary")
    ,editAddModuleBtn: document.getElementById("edit-add-module-btn")
    ,editMainSubjectName: document.getElementById("edit-main-subject-name")
    ,metaGroupCreateDialog: document.getElementById("meta-group-create-dialog")
    ,metaGroupCreateForm: document.getElementById("meta-group-create-form")
    ,metaGroupManageDialog: document.getElementById("meta-group-manage-dialog")
    ,metaGroupManageBody: document.getElementById("meta-group-manage-body")
    ,metaGroupEditDialog: document.getElementById("meta-group-edit-dialog")
    ,metaGroupEditForm: document.getElementById("meta-group-edit-form")
    ,metaGroupDeleteBtn: document.getElementById("meta-group-delete-btn")
    ,metaGroupEditCloseBtn: document.getElementById("meta-group-edit-close-btn")
};

let selectedParallel = 1;
const AOOP_TAB_KEY = "AOOP_UO";
let selectedBuilding = "";
let buildings = [];
let classes = [];
let curriculumRows = [];
let subjects = [];
let studyPeriodSettings = [];
let metaGroups = [];
let maxLoadLimits = {};
let sumMismatchKeys = new Set();
let pendingCreateContext = null;
let curriculumDisplayMode = "detailed";
const issueNavigationParams = new URLSearchParams(window.location.search);
const issueNavigation = {
    building: issueNavigationParams.get("building") || "",
    className: issueNavigationParams.get("issueClass") || ""
};
let issueNavigationHandled = false;

async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function esc(v) {
    return String(v ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function print(v) { ui.result.textContent = JSON.stringify(v, null, 2); }
function showMetaGroupEditError(message = "") {
    const target = document.getElementById("meta-group-edit-error");
    if (target) target.textContent = message;
}
function norm(v) { return String(v || "").trim(); }
function classToParallel(className) { const m = norm(className).match(/^(\d{1,2})/); return m ? Number(m[1]) : null; }
function levelShort(v) { return v === "ADVANCED" ? "У" : "Б"; }
function subjectTypeByPart(part) {
    if (part === "CORRECTIONAL") return "CORRECTIONAL";
    if (part === "EXTRACURRICULAR") return "EXTRACURRICULAR";
    if (part === "FORMABLE") return "FORMABLE";
    return "CORE";
}
function isSubjectTypeCompatible(actualType, expectedType) {
    if (actualType === expectedType) return true;
    return actualType === "CORE_FORMABLE" && (expectedType === "CORE" || expectedType === "FORMABLE");
}
function isHighSchoolParallel(parallel = selectedParallel) { return Number(parallel) >= 10; }
function makeClassKey(numberSchoolBuilding, className) { return `${norm(numberSchoolBuilding)}|${norm(className)}`; }
function isExplicitMetaGroupClassName(className) { return norm(className).toUpperCase().startsWith("МГ:"); }
function configureExclusionControl(form, className, label, info) {
    const control = form?.elements?.excludedFromManualLoad;
    if (!control) return;
    const explicitMetaGroup = isExplicitMetaGroupClassName(className);
    if (explicitMetaGroup) {
        control.value = "false";
        control.disabled = true;
        label?.classList.add("hidden");
        info?.classList.remove("hidden");
    } else {
        control.disabled = false;
        label?.classList.remove("hidden");
        info?.classList.add("hidden");
    }
}
function normalizeStudyPeriod(className, studyPeriod) {
    return studyPeriod || (isHighSchoolParallel(classToParallel(className)) ? "H1" : "YEAR");
}

function resolveParallelForClassName(className, building = "") {
    const direct = classToParallel(className);
    if (Number.isFinite(direct)) return direct;
    if (!norm(className).startsWith("МГ:")) return 1;
    const hit = (metaGroups || []).find((m) =>
        `МГ:${m.name}` === norm(className) && (!building || norm(m.numberSchoolBuilding) === norm(building))
    );
    return Number.isFinite(Number(hit?.parallel)) ? Number(hit.parallel) : 1;
}

function settingsForParallel(parallel = selectedParallel) {
    return (studyPeriodSettings || []).filter((x) => Number(x.parallelFrom) <= Number(parallel) && Number(x.parallelTo) >= Number(parallel));
}

function periodColumnsForParallel(parallel = selectedParallel) {
    return settingsForParallel(parallel).map((x) => ({ key: String(x.id), label: x.displayName, studyPeriod: x.studyPeriod }));
}



function hasSemesterSplitForClass(classRow) {
    const classRows = curriculumRows.filter((r) => r.className === classRow.className && r.numberSchoolBuilding === classRow.numberSchoolBuilding);
    const grouped = new Map();
    classRows.forEach((r) => {
        const key = `${r.subjectName}|${r.curriculumPart || "CORE"}`;
        if (!grouped.has(key)) grouped.set(key, []);
        grouped.get(key).push(r);
    });
    for (const rows of grouped.values()) {
        let h1 = 0, h2 = 0, year = 0;
        rows.forEach((r) => {
            const v = Number(r.plannedHours || 0);
            if (r.studyPeriod === "H1") h1 += v;
            else if (r.studyPeriod === "H2") h2 += v;
            else year += v;
        });
        if (year > 0) continue;
        if ((h1 > 0) !== (h2 > 0)) return true;
        if (h1 !== h2) return true;
    }
    return false;
}

function columnsForClass(classRow) {
    const parallel = classToParallel(classRow.className);
    const options = settingsForParallel(parallel);
    const split = hasSemesterSplitForClass(classRow);
    if (!split) {
        const year = options.find((o) => o.studyPeriod === "YEAR") || options[0];
        return year ? [{ key: String(year.id), label: year.displayName, studyPeriod: year.studyPeriod }] : [];
    }
    const h1 = options.find((o) => o.studyPeriod === "H1");
    const h2 = options.find((o) => o.studyPeriod === "H2");
    return [h1, h2].filter(Boolean).map((x) => ({ key: String(x.id), label: x.displayName, studyPeriod: x.studyPeriod }));
}

function toggleSubgroupConfig(container, requiredValue) {
    const required = String(requiredValue) === "true";
    if (!container) return;
    container.classList.toggle("hidden", !required);

    const form = container.closest("form");
    if (!form) return;

    const plannedHours = form.elements.plannedHours;
    const subgroup1Hours = form.elements.subgroup1Hours;
    const subgroup2Hours = form.elements.subgroup2Hours;
    const subgroup1EducationLevel = form.elements.subgroup1EducationLevel;
    const subgroup2EducationLevel = form.elements.subgroup2EducationLevel;

    if (plannedHours) {
        plannedHours.disabled = required;
        plannedHours.required = !required;
    }

    [subgroup1Hours, subgroup2Hours, subgroup1EducationLevel, subgroup2EducationLevel].forEach((input) => {
        if (!input) return;
        input.disabled = !required;
        input.required = required;
    });
}

function classesForSelectedContext() {
    return classes
        .filter((c) => selectedParallel === AOOP_TAB_KEY
            ? (c.classType || "NORMAL") === "AOOP_UO"
            : classToParallel(c.className) === selectedParallel && (c.classType || "NORMAL") !== "AOOP_UO")
        .filter((c) => !selectedBuilding || c.numberSchoolBuilding === selectedBuilding)
        .sort((a, b) => `${a.numberSchoolBuilding}|${a.className}`.localeCompare(`${b.numberSchoolBuilding}|${b.className}`, "ru"));
}


function metaGroupsForSelectedContext() {
    if (selectedParallel === AOOP_TAB_KEY) return [];
    return (metaGroups || [])
        .filter((m) => selectedParallel === AOOP_TAB_KEY
            ? (norm(m.classType) || "NORMAL") === AOOP_TAB_KEY
            : Number(m.parallel) === Number(selectedParallel) && (norm(m.classType) || "NORMAL") !== AOOP_TAB_KEY)
        .filter((m) => !selectedBuilding || norm(m.numberSchoolBuilding) === selectedBuilding)
        .sort((a, b) => String(a.name).localeCompare(String(b.name), "ru"));
}


function chooseMetaGroupInContext() {
    const list = metaGroupsForSelectedContext();
    if (!list.length) throw new Error("Нет метагрупп в выбранном корпусе/параллели");
    const promptText = list.map((m, i) => `${i + 1}. ${m.name}`).join("\n");
    const raw = prompt(`Выберите номер метагруппы:
${promptText}`);
    if (!raw) return null;
    const idx = Number(raw) - 1;
    if (!Number.isInteger(idx) || idx < 0 || idx >= list.length) throw new Error("Некорректный номер метагруппы");
    return list[idx];
}

function renderMetaGroupCreateForm() {
    const form = ui.metaGroupCreateForm;
    if (!form) return;
    const buildingSelect = form.elements.numberSchoolBuilding;
    buildingSelect.innerHTML = "";
    const allBuildings = Array.from(new Set(classes.map((c) => c.numberSchoolBuilding))).sort((a,b)=>String(a).localeCompare(String(b),"ru"));
    allBuildings.forEach((b)=> buildingSelect.innerHTML += `<option value="${esc(b)}">${esc(b)}</option>`);
    buildingSelect.value = selectedBuilding || allBuildings[0] || "";
    renderMetaGroupFormSchoolBuildingOptions(form.elements.schoolBuildingId, "");

    const parallelSelect = form.elements.parallel;
    parallelSelect.innerHTML = "";
    for (let p=1;p<=11;p++) parallelSelect.innerHTML += `<option value="${p}">${p}</option>`;
    parallelSelect.value = selectedParallel === AOOP_TAB_KEY ? "1" : String(selectedParallel);

    const studySelect = form.elements.studyPeriodSettingId;
    const options = settingsForParallel(Number(parallelSelect.value));
    studySelect.innerHTML = options.map((o)=>`<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join("");
    parallelSelect.onchange = () => {
        const opts = settingsForParallel(Number(parallelSelect.value));
        studySelect.innerHTML = opts.map((o)=>`<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join("");
    };
}

function renderMetaGroupManageTable() {
    const rows = (metaGroups || []).slice().sort((a,b)=>`${a.numberSchoolBuilding}|${a.name}`.localeCompare(`${b.numberSchoolBuilding}|${b.name}`,"ru"));
    ui.metaGroupManageBody.innerHTML = rows.map((m) => {
        const period = (studyPeriodSettings || []).find((s) => Number(s.id) === Number(m.studyPeriodSettingId));
        return `<tr>
            <td>${esc(m.academicYear || "—")}</td>
            <td>${esc(m.numberSchoolBuilding)}</td>
            <td>${esc(metaGroupSchoolBuildingLabel(m) || "— выберите площадку —")}</td>
            <td>${esc((m.classType || "NORMAL")==="AOOP_UO" ? "АООП УО" : "Норма")}</td>
            <td>${esc(m.parallel)}</td>
            <td>${esc(m.name)}</td>
            <td>${esc(period?.displayName || "—")}</td>
            <td><button type="button" data-edit-meta-id="${esc(m.id)}">Редактировать</button></td>
        </tr>`;
    }).join("");
    ui.metaGroupManageBody.querySelectorAll("button[data-edit-meta-id]").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = Number(btn.dataset.editMetaId);
            const m = rows.find((x) => Number(x.id) === id);
            if (!m) return;
            openMetaGroupEditDialog(m);
        });
    });
}

function renderMetaGroupFormBuildingOptions(selectEl, selectedValue = "") {
    if (!selectEl) return;
    const allBuildings = Array.from(new Set(classes.map((c) => c.numberSchoolBuilding))).sort((a,b)=>String(a).localeCompare(String(b),"ru"));
    selectEl.innerHTML = allBuildings.map((b) => `<option value="${esc(b)}">${esc(b)}</option>`).join("");
    if (selectedValue) selectEl.value = selectedValue;
}

function metaGroupSchoolBuildingLabel(metaGroup) {
    const id = Number(metaGroup?.schoolBuildingId || 0);
    const building = buildings.find((row) => Number(row.id) === id);
    if (!building) return "";
    const code = norm(building.code);
    const address = norm(building.address);
    return [code, address].filter(Boolean).join(" — ");
}

function renderMetaGroupFormSchoolBuildingOptions(selectEl, selectedValue = "") {
    if (!selectEl) return;
    const selected = selectedValue ? String(selectedValue) : "";
    selectEl.innerHTML = '<option value="">Выберите физическую площадку</option>' + buildings
        .map((b) => {
            const label = [norm(b.code), norm(b.address)].filter(Boolean).join(" — ") || norm(b.name) || String(b.id);
            return `<option value="${esc(b.id)}">${esc(label)}</option>`;
        })
        .join("");
    selectEl.value = selected;
}

function renderMetaGroupFormParallelOptions(selectEl, selectedValue = 1) {
    if (!selectEl) return;
    selectEl.innerHTML = "";
    for (let p=1;p<=11;p++) selectEl.innerHTML += `<option value="${p}">${p}</option>`;
    selectEl.value = String(selectedValue || 1);
}

function renderMetaGroupFormPeriodOptions(formEl, selectedValue = "") {
    if (!formEl) return;
    const parallel = Number(formEl.elements.parallel.value) || 1;
    const options = settingsForParallel(parallel);
    const selectEl = formEl.elements.studyPeriodSettingId;
    selectEl.innerHTML = options.map((o)=>`<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join("");
    if (selectedValue) selectEl.value = String(selectedValue);
}

function openMetaGroupEditDialog(metaGroup) {
    if (!ui.metaGroupEditForm || !ui.metaGroupEditDialog) return;
    const form = ui.metaGroupEditForm;
    form.elements.id.value = String(metaGroup.id || "");
    renderMetaGroupFormBuildingOptions(form.elements.numberSchoolBuilding, metaGroup.numberSchoolBuilding);
    renderMetaGroupFormSchoolBuildingOptions(form.elements.schoolBuildingId, metaGroup.schoolBuildingId ? String(metaGroup.schoolBuildingId) : "");
    form.elements.classType.value = metaGroup.classType || "NORMAL";
    renderMetaGroupFormParallelOptions(form.elements.parallel, Number(metaGroup.parallel) || 1);
    form.elements.name.value = metaGroup.name || "";
    renderMetaGroupFormPeriodOptions(form, metaGroup.studyPeriodSettingId ? String(metaGroup.studyPeriodSettingId) : "");
    form.elements.parallel.onchange = () => renderMetaGroupFormPeriodOptions(form, "");
    ui.metaGroupEditDialog.showModal();
}

function renderParallelTabs() {
    ui.parallelTabs.innerHTML = "";
    for (let p = 1; p <= 11; p++) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `parallel-tab ${p === selectedParallel ? "active" : ""}`;
        btn.textContent = `${p} параллель`;
        btn.addEventListener("click", () => {
            selectedParallel = p;
            syncSelectedBuilding();
            renderParallelTabs();
            renderBuildingFilter();
            renderClassOptions();
            syncStudyPeriodControls();
            renderSummaryTable();
        });
        ui.parallelTabs.appendChild(btn);
    }
    const aoopBtn = document.createElement("button");
    aoopBtn.type = "button";
    aoopBtn.className = `parallel-tab ${selectedParallel === AOOP_TAB_KEY ? "active" : ""}`;
    aoopBtn.textContent = "АООП УО";
    aoopBtn.addEventListener("click", () => {
        selectedParallel = AOOP_TAB_KEY;
        syncSelectedBuilding();
        renderParallelTabs();
        renderBuildingFilter();
        renderClassOptions();
        syncStudyPeriodControls();
        renderSummaryTable();
    });
    ui.parallelTabs.appendChild(aoopBtn);
}

function syncSelectedBuilding() {
    const available = classes
        .filter((c) => selectedParallel === AOOP_TAB_KEY
            ? (c.classType || "NORMAL") === "AOOP_UO"
            : classToParallel(c.className) === selectedParallel && (c.classType || "NORMAL") !== "AOOP_UO")
        .map((c) => c.numberSchoolBuilding);
    if (!available.includes(selectedBuilding)) {
        selectedBuilding = available[0] || "";
    }
}

function renderBuildingFilter() {
    const available = Array.from(new Set(classes
        .filter((c) => selectedParallel === AOOP_TAB_KEY
            ? (c.classType || "NORMAL") === "AOOP_UO"
            : classToParallel(c.className) === selectedParallel && (c.classType || "NORMAL") !== "AOOP_UO")
        .map((c) => c.numberSchoolBuilding))).sort((a, b) => String(a).localeCompare(String(b), "ru"));

    ui.buildingFilter.innerHTML = "<option value=''>Все корпуса</option>";
    available.forEach((code) => {
        const b = buildings.find((row) => row.code === code);
        ui.buildingFilter.innerHTML += `<option value="${esc(code)}">${esc(code)} — ${esc(b?.name || code)}</option>`;
    });
    ui.buildingFilter.value = selectedBuilding;

    ui.formBuilding.innerHTML = "<option value=''>Выберите корпус</option>";
    available.forEach((code) => {
        const b = buildings.find((row) => row.code === code);
        ui.formBuilding.innerHTML += `<option value="${esc(code)}">${esc(code)} — ${esc(b?.name || code)}</option>`;
    });
    if (selectedBuilding) ui.formBuilding.value = selectedBuilding;
}

function renderClassOptions() {
    const building = norm(ui.formBuilding.value) || selectedBuilding;
    const items = classes
        .filter((c) => selectedParallel === AOOP_TAB_KEY
            ? (c.classType || "NORMAL") === "AOOP_UO"
            : classToParallel(c.className) === selectedParallel && (c.classType || "NORMAL") !== "AOOP_UO")
        .filter((c) => !building || c.numberSchoolBuilding === building)
        .sort((a, b) => String(a.className).localeCompare(String(b.className), "ru"));

    ui.formClass.innerHTML = "<option value=''>Выберите класс</option>";
    items.forEach((c) => {
        ui.formClass.innerHTML += `<option value="${esc(c.className)}">${esc(c.className)} (${esc(c.classDirection)})</option>`;
    });
    metaGroupsForSelectedContext().forEach((m) => {
        const value = `МГ:${m.name}`;
        ui.formClass.innerHTML += `<option value="${esc(value)}">${esc(value)} (Метагруппа)</option>`;
    });
    configureExclusionControl(ui.form, ui.formClass.value, ui.subjectExclusionLabel, ui.subjectMetaGroupInfo);
}

function syncStudyPeriodControls() {
    const parallel = classToParallel(ui.formClass.value) || (selectedParallel === AOOP_TAB_KEY ? 1 : selectedParallel);
    const options = settingsForParallel(parallel);
    const selected = ui.formStudyPeriod.value;
    ui.formStudyPeriod.innerHTML = options.map((o) => `<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join('');
    let preferred = selected;
    const classValue = norm(ui.formClass.value);
    if (classValue.startsWith("МГ:")) {
        const m = (metaGroups || []).find((x) => `МГ:${x.name}` === classValue && (!selectedBuilding || norm(x.numberSchoolBuilding) === norm(ui.formBuilding.value || selectedBuilding)));
        if (m?.studyPeriodSettingId) preferred = String(m.studyPeriodSettingId);
    }
    ui.formStudyPeriod.value = options.some((o) => String(o.id) === preferred) ? preferred : String(options[0]?.id || '');

    if (ui.editForm?.elements.studyPeriod) {
        const classParallel = resolveParallelForClassName(
            ui.editForm.elements.className?.value,
            ui.formBuilding?.value || selectedBuilding
        );
        const dialogOptions = settingsForParallel(classParallel);
        const editSelect = ui.editForm.elements.studyPeriod;
        const current = editSelect.value;
        editSelect.innerHTML = dialogOptions.map((o) => `<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join('');
        editSelect.value = dialogOptions.some((o) => String(o.id) === current) ? current : String(dialogOptions[0]?.id || '');
    }
}

function subjectAreaForRow(row) {
    const type = subjectTypeByPart(row?.curriculumPart || "CORE");
    const name = norm(row?.__baseSubjectName || row?.subjectName);
    const match = (subjects || []).find((s) => norm(s.subjectName) === name && s.subjectType === type);
    const area = norm(match?.subjectAreaName) || "Без области";
    const normalized = area.toLowerCase().replaceAll("ё", "е").replace(/[—–-]/g, " ").replaceAll(/\s+/g, " ").trim();
    return CORE_AREA_ALIASES[normalized] || area;
}

function curriculumRowsForDisplay() {
    if (curriculumDisplayMode === "general") return curriculumRows;
    return (curriculumRows || []).flatMap((row) => {
        if (!row.modularSystem || !(row.modules || []).length) return [row];
        return row.modules.map((module) => ({
            ...row,
            subjectName: `${row.subjectName} (${module.moduleName})`,
            __baseSubjectName: row.subjectName,
            __moduleId: module.id,
            plannedHours: module.plannedHours,
            educationLevel: module.educationLevel,
            subgroupRequired: module.subgroupRequired,
            subgroupCount: module.subgroupCount,
            subgroup1Hours: module.subgroup1Hours,
            subgroup1EducationLevel: module.subgroup1EducationLevel,
            subgroup2Hours: module.subgroup2Hours,
            subgroup2EducationLevel: module.subgroup2EducationLevel
        }));
    });
}

function buildSummaryRows(selectedClasses, sourceRows = curriculumRows) {
    const byPart = { CORE: [], FORMABLE: [], EXTRACURRICULAR: [], CORRECTIONAL: [] };
    const classSet = new Set(selectedClasses.map((c) => makeClassKey(c.numberSchoolBuilding, c.className)));

    sourceRows.forEach((r) => {
        const key = makeClassKey(r.numberSchoolBuilding, r.className);
        if (!classSet.has(key)) return;
        byPart[r.curriculumPart || "CORE"].push(r);
    });

    const rows = [];
    ["CORE", "FORMABLE", "EXTRACURRICULAR", "CORRECTIONAL"].forEach((part) => {
        const groupedBySubject = new Map();
        byPart[part].forEach((r) => {
            const area = subjectAreaForRow(r);
            const gk = `${r.subjectName}|${area}`;
            if (!groupedBySubject.has(gk)) groupedBySubject.set(gk, []);
            groupedBySubject.get(gk).push(r);
        });

        const items = Array.from(groupedBySubject.entries()).map(([key, values]) => {
            const [subjectName, subjectArea] = key.split("|");
            const perClass = {};
            values.forEach((v) => {
                const classKey = makeClassKey(v.numberSchoolBuilding, v.className);
                if (!perClass[classKey]) perClass[classKey] = { year: null, h1: null, h2: null };
                const item = {
                    hours: Number(v.plannedHours || 0),
                    educationLevel: v.educationLevel || "BASIC",
                    subgroupRequired: Boolean(v.subgroupRequired),
                    subgroupCount: Number(v.subgroupCount || 0),
                    subgroup1Hours: v.subgroup1Hours,
                    subgroup2Hours: v.subgroup2Hours,
                    id: v.id,
                    studyPeriod: v.studyPeriod,
                    className: v.className,
                    excludedFromManualLoad: Boolean(v.excludedFromManualLoad),
                    metaGroup: Boolean(v.metaGroup)
                };
                if (v.studyPeriod === "H1") perClass[classKey].h1 = item;
                else if (v.studyPeriod === "H2") perClass[classKey].h2 = item;
                else perClass[classKey].year = item;
            });
            return { type: "subject", part, educationLevel: "BASIC", subjectName, subjectArea, perClass };
        }).sort((a, b) => a.subjectName.localeCompare(b.subjectName, "ru"));

        const preparedSubjects = [];
        if (part !== "CORE") {
            items.forEach((item) => preparedSubjects.push({ ...item, subjectColspan: 2, areaRowspan: 0 }));
        } else {
            const orderedAreas = [];
            const byArea = new Map();
            const noArea = [];
            items.forEach((item) => {
                const area = norm(item.subjectArea);
                if (!area || area === "Без области") {
                    noArea.push(item);
                    return;
                }
                if (!byArea.has(area)) {
                    byArea.set(area, []);
                    orderedAreas.push(area);
                }
                byArea.get(area).push(item);
            });

            const areaRank = (area) => {
                const idx = CORE_AREA_ORDER.findIndex((x) => x.toLowerCase() === area.toLowerCase());
                return idx === -1 ? Number.MAX_SAFE_INTEGER : idx;
            };

            orderedAreas
                .sort((a, b) => areaRank(a) - areaRank(b) || a.localeCompare(b, "ru"))
                .forEach((area) => {
                    const list = byArea.get(area) || [];
                    list.sort((a, b) => a.subjectName.localeCompare(b.subjectName, "ru"));
                    list.forEach((item, index) => {
                        preparedSubjects.push({
                            ...item,
                            subjectColspan: 1,
                            areaLabel: area,
                            areaRowspan: index === 0 ? list.length : 0
                        });
                    });
                });

            noArea.sort((a, b) => a.subjectName.localeCompare(b.subjectName, "ru"))
                .forEach((item) => preparedSubjects.push({ ...item, subjectColspan: 2, areaRowspan: 0 }));
        }

        if (part === "CORRECTIONAL" && preparedSubjects.length === 0) {
            return;
        }

        rows.push({ type: "part", part, title: PART_META[part].label });
        rows.push(...preparedSubjects);
        rows.push({ type: "sum", part, title: `Сумма ${PART_META[part].short}` });
    });

    const extracurricularIndex = rows.findIndex((r) => r.type === "part" && r.part === "EXTRACURRICULAR");
    const summaryRows = [{ type: "sum12", title: "Сумма О+Ф" }];
    if (selectedParallel !== AOOP_TAB_KEY) {
        summaryRows.push({ type: "maximum", title: "Максимальная нагрузка" });
    }
    rows.splice(extracurricularIndex, 0, ...summaryRows);

    return rows;
}

function openEditById(id) {
    const existing = curriculumRows.find((r) => r.id === id);
    if (!existing) return;
    pendingCreateContext = null;
    if (ui.editMainSubjectName) ui.editMainSubjectName.textContent = `Основной предмет: ${existing.subjectName || ""}`;

    ui.editForm.elements.id.value = String(existing.id);
    ui.editForm.elements.className.value = existing.className || "";
    ui.editForm.elements.plannedHours.value = String(existing.plannedHours || 1);
    ui.editForm.elements.educationLevel.value = existing.educationLevel || "BASIC";
    ui.editForm.elements.modularSystem.value = String(Boolean(existing.modularSystem));
    ui.editForm.elements.subgroupRequired.value = String(Boolean(existing.subgroupRequired));
    ui.editForm.elements.studyPeriod.value = String(existing.studyPeriodSettingId || "");
    ui.editForm.elements.excludedFromManualLoad.value = String(Boolean(existing.excludedFromManualLoad));
    configureExclusionControl(ui.editForm, existing.className, ui.editExclusionLabel, ui.editMetaGroupInfo);
    ui.editForm.elements.subgroup1Hours.value = existing.subgroup1Hours || existing.plannedHours || "";
    ui.editForm.elements.subgroup2Hours.value = existing.subgroup2Hours || existing.plannedHours || "";
    ui.editForm.elements.subgroup1EducationLevel.value = existing.subgroup1EducationLevel || existing.educationLevel || "BASIC";
    ui.editForm.elements.subgroup2EducationLevel.value = existing.subgroup2EducationLevel || existing.educationLevel || "BASIC";
    toggleModuleSystem(ui.editForm, ui.editModuleConfig, ui.editModuleList, ui.editModuleHoursSummary, existing.modules || []);
    syncStudyPeriodControls();
    ui.deleteItemBtn.style.display = "";
    ui.editDialog.showModal();
}

function openCreateByCell(cellCtx) {
    pendingCreateContext = cellCtx;
    if (ui.editMainSubjectName) ui.editMainSubjectName.textContent = `Основной предмет: ${cellCtx.subjectName || ""}`;
    ui.editForm.reset();
    ui.editForm.elements.id.value = "";
    ui.editForm.elements.className.value = cellCtx.className;
    ui.editForm.elements.plannedHours.value = "";
    ui.editForm.elements.educationLevel.value = cellCtx.educationLevel || "BASIC";
    ui.editForm.elements.modularSystem.value = "false";
    ui.editForm.elements.subgroupRequired.value = "false";
    ui.editForm.elements.excludedFromManualLoad.value = "false";
    configureExclusionControl(ui.editForm, cellCtx.className, ui.editExclusionLabel, ui.editMetaGroupInfo);
    ui.editForm.elements.subgroup1Hours.value = "";
    ui.editForm.elements.subgroup2Hours.value = "";
    ui.editForm.elements.subgroup1EducationLevel.value = ui.editForm.elements.educationLevel.value;
    ui.editForm.elements.subgroup2EducationLevel.value = ui.editForm.elements.educationLevel.value;
    ui.editModuleList.innerHTML = "";
    toggleModuleSystem(ui.editForm, ui.editModuleConfig, ui.editModuleList, ui.editModuleHoursSummary);
    syncStudyPeriodControls();
    const options = Array.from(ui.editForm.elements.studyPeriod.options || []);
    const preferredById = options.find((opt) => String(opt.value) === String(cellCtx.studyPeriodSettingId || ""));
    const classParallel = resolveParallelForClassName(cellCtx.className, cellCtx.numberSchoolBuilding);
    const periodSetting = settingsForParallel(classParallel).find((s) => s.studyPeriod === cellCtx.studyPeriod);
    const preferred = preferredById || options.find((opt) => String(opt.value) === String(periodSetting?.id || ""));
    if (preferred) {
        ui.editForm.elements.studyPeriod.value = preferred.value;
    }
    ui.deleteItemBtn.style.display = "none";
    ui.editDialog.showModal();
}

function classCellMarkup(cellInfo, rowMeta, classMeta) {
    const info = cellInfo || { year: null, h1: null, h2: null };
    const hoursLabelMarkup = (cell) => {
        if (!cell) return "";
        const markers = [];
        if (cell.subgroupRequired) markers.push("Д");
        if (cell.excludedFromManualLoad && !isExplicitMetaGroupClassName(cell.className)) markers.push("Н");
        const markersHtml = markers.length
            ? `<sup class="hours-index">${markers.join("")}</sup>`
            : "";
        if (cell.subgroupRequired && Number.isFinite(Number(cell.subgroup1Hours)) && Number.isFinite(Number(cell.subgroup2Hours))) {
            const g1 = Number(cell.subgroup1Hours);
            const g2 = Number(cell.subgroup2Hours);
            const subgroupLabel = g1 === g2 ? String(g1) : `${g1}//${g2}`;
            return `${esc(subgroupLabel)}${markersHtml}`;
        }
        return `${esc(cell.hours)}${markersHtml}`;
    };
    const createAttrs = (studyPeriod) => {
        const candidateSettings = columnsForClass({ className: classMeta.className, numberSchoolBuilding: classMeta.numberSchoolBuilding });
        const setting = candidateSettings.find((x) => x.studyPeriod === studyPeriod)
            || settingsForParallel(resolveParallelForClassName(classMeta.className, classMeta.numberSchoolBuilding)).find((x) => x.studyPeriod === studyPeriod)
            || candidateSettings[0]
            || settingsForParallel(resolveParallelForClassName(classMeta.className, classMeta.numberSchoolBuilding))[0];
        return `data-create="1" data-building="${esc(classMeta.numberSchoolBuilding)}" data-class-name="${esc(classMeta.className)}" data-subject-name="${esc(rowMeta.subjectName)}" data-curriculum-part="${esc(rowMeta.part)}" data-education-level="${esc(rowMeta.educationLevel)}" data-study-period="${esc(studyPeriod)}" data-study-period-setting-id="${esc(setting?.id || "")}"`;
    };
    const emptyBtn = (studyPeriod) => `<button type="button" class="hours-cell empty-hours-cell" ${createAttrs(studyPeriod)}></button>`;

    const year = info.year;
    const h1 = info.h1;
    const h2 = info.h2;
    const split = Boolean(classMeta.split || h1 || h2);

    if (year) {
        const cls = `${(year.educationLevel || rowMeta.educationLevel) === "ADVANCED" ? "advanced-cell" : ""} ${year.excludedFromManualLoad && !isExplicitMetaGroupClassName(year.className) ? "meta-group-cell" : ""}`;
        return `<button class="hours-cell ${cls}" data-id="${esc(year.id)}">${hoursLabelMarkup(year)}</button>`;
    }

    if (!split) {
        return emptyBtn("YEAR");
    }

    const left = h1
        ? `<button class="hours-cell ${(h1.educationLevel === "ADVANCED" ? "advanced-cell" : "")} ${h1.excludedFromManualLoad && !isExplicitMetaGroupClassName(h1.className) ? "meta-group-cell" : ""}" data-id="${esc(h1.id)}">${hoursLabelMarkup(h1)}</button>`
        : emptyBtn("H1");
    const right = h2
        ? `<button class="hours-cell ${(h2.educationLevel === "ADVANCED" ? "advanced-cell" : "")} ${h2.excludedFromManualLoad && !isExplicitMetaGroupClassName(h2.className) ? "meta-group-cell" : ""}" data-id="${esc(h2.id)}">${hoursLabelMarkup(h2)}</button>`
        : emptyBtn("H2");
    return `<div style="display:grid;grid-template-columns:1fr 1fr;gap:0"><div style="padding-right:4px">${left}</div><div style="border-left:1px solid #cbd5e1;padding-left:4px">${right}</div></div>`;
}

function renderSummaryTable() {
    const selectedClasses = classes
        .filter((c) => selectedParallel === AOOP_TAB_KEY
            ? (c.classType || "NORMAL") === "AOOP_UO"
            : classToParallel(c.className) === selectedParallel && (c.classType || "NORMAL") !== "AOOP_UO")
        .sort((a, b) => `${a.numberSchoolBuilding}|${a.className}`.localeCompare(`${b.numberSchoolBuilding}|${b.className}`, "ru"));
    const selectedMetaGroups = (metaGroups || [])
        .filter((m) => selectedParallel === AOOP_TAB_KEY
            ? (norm(m.classType) || "NORMAL") === AOOP_TAB_KEY
            : Number(m.parallel) === Number(selectedParallel) && (norm(m.classType) || "NORMAL") !== AOOP_TAB_KEY)
        .sort((a, b) => `${a.numberSchoolBuilding}|${a.name}`.localeCompare(`${b.numberSchoolBuilding}|${b.name}`, "ru"))
        .map((m) => ({
        numberSchoolBuilding: m.numberSchoolBuilding,
        className: `МГ:${m.name}`,
        classDirection: "Метагруппа"
    }));
    const knownMetaByKey = new Map((metaGroups || []).map((m) => [makeClassKey(m.numberSchoolBuilding, `МГ:${m.name}`), {
        parallel: Number(m.parallel),
        classType: norm(m.classType) || "NORMAL"
    }]));
    const classTypeByClassKey = new Map((classes || []).map((c) => [makeClassKey(c.numberSchoolBuilding, c.className), norm(c.classType) || "NORMAL"]));
    const metagroupsFromData = (curriculumRows || [])
        .filter((r) => norm(r.className).startsWith("МГ:"))
        .filter((r) => {
            const byKey = knownMetaByKey.get(makeClassKey(r.numberSchoolBuilding, r.className));
            if (byKey) {
                if (selectedParallel === AOOP_TAB_KEY) return byKey.classType === AOOP_TAB_KEY;
                return byKey.parallel === Number(selectedParallel) && byKey.classType !== AOOP_TAB_KEY;
            }
            const guessedType = classTypeByClassKey.get(makeClassKey(r.numberSchoolBuilding, r.className));
            if (selectedParallel === AOOP_TAB_KEY) return guessedType === AOOP_TAB_KEY;
            if (guessedType === AOOP_TAB_KEY) return false;
            return true;
        })
        .map((r) => ({
            numberSchoolBuilding: norm(r.numberSchoolBuilding),
            className: norm(r.className),
            classDirection: "Метагруппа"
        }));
    const allColumns = [...selectedClasses, ...selectedMetaGroups, ...metagroupsFromData]
        .filter((c) => c.numberSchoolBuilding && c.className)
        .filter((c, idx, arr) => arr.findIndex((x) => makeClassKey(x.numberSchoolBuilding, x.className) === makeClassKey(c.numberSchoolBuilding, c.className)) === idx);
    const classDescriptors = allColumns.map((c) => ({
        classKey: makeClassKey(c.numberSchoolBuilding, c.className),
        className: c.className,
        classDirection: c.classDirection,
        numberSchoolBuilding: c.numberSchoolBuilding,
        split: hasSemesterSplitForClass(c)
    }));
    const rows = buildSummaryRows(allColumns, curriculumRowsForDisplay());
    const summaryTable = ui.summaryHead.closest("table");
    if (summaryTable) {
        summaryTable.style.width = `${Math.max(1500, 540 + classDescriptors.length * 120)}px`;
    }

    ui.summaryHead.innerHTML = "";
    ui.summaryBody.innerHTML = "";

    const buildingRow = document.createElement("tr");
    buildingRow.className = "summary-building-row";
    const groupsByBuilding = [];
    allColumns.forEach((col) => {
        const last = groupsByBuilding[groupsByBuilding.length - 1];
        if (last && last.code === col.numberSchoolBuilding) {
            last.count += 1;
        } else {
            groupsByBuilding.push({ code: col.numberSchoolBuilding, count: 1 });
        }
    });
    buildingRow.innerHTML = `<th rowspan="3">Блок / область</th><th rowspan="3">Предмет</th>${
        groupsByBuilding.map((group) => {
            const b = buildings.find((row) => row.code === group.code);
            const label = b?.name ? `${group.code} — ${b.name}` : group.code;
            return `<th colspan="${group.count}">${esc(label)}</th>`;
        }).join("")
    }`;
    const directionRow = document.createElement("tr");
    directionRow.className = "summary-direction-row";
    directionRow.innerHTML = allColumns.map((c) => `<th>${esc(c.classDirection)}</th>`).join("");
    const classRow = document.createElement("tr");
    classRow.className = "summary-class-row";
    classRow.innerHTML = allColumns.map((c) => `<th data-summary-building="${esc(c.numberSchoolBuilding)}" data-summary-class="${esc(c.className)}">${esc(c.className)}</th>`).join("");
    ui.summaryHead.appendChild(buildingRow);
    ui.summaryHead.appendChild(directionRow);
    ui.summaryHead.appendChild(classRow);

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        if (row.type === "part") {
            tr.className = "summary-part-row";
            tr.innerHTML = `<td>${esc(row.title)}</td><td></td>${classDescriptors.map(() => "<td></td>").join("")}`;
        } else if (row.type === "subject") {
            const lead = row.subjectColspan === 2
                ? `<td colspan="2" class="subject-name-cell subject-name-wide-cell">${esc(row.subjectName)}</td>`
                : `${row.areaRowspan > 0 ? `<td rowspan="${esc(row.areaRowspan)}" class="subject-area-cell">${esc(row.areaLabel || "")}</td>` : ""}<td class="subject-name-cell">${esc(row.subjectName)}</td>`;
            tr.innerHTML = `${lead}` + classDescriptors
                .map((col) => `<td class="hours-cell-wrap">${classCellMarkup(row.perClass[col.classKey], row, col)}</td>`)
                .join("");
        } else if (row.type === "maximum") {
            const maximum = Number(maxLoadLimits[String(selectedParallel)] || 0);
            const cells = classDescriptors.map((col) => {
                if (isExplicitMetaGroupClassName(col.className)) return "<td></td>";
                const values = curriculumRows.filter((entry) =>
                    makeClassKey(entry.numberSchoolBuilding, entry.className) === col.classKey
                    && (entry.curriculumPart === "CORE" || entry.curriculumPart === "FORMABLE"));
                const sumForPeriod = (period) => values
                    .filter((entry) => entry.studyPeriod === period)
                    .reduce((sum, entry) => sum + Number(entry.plannedHours || 0), 0);
                const year = sumForPeriod("YEAR");
                const exceeded = year + sumForPeriod("H1") > maximum || year + sumForPeriod("H2") > maximum;
                return `<td class="summary-value ${exceeded ? "maximum-load-exceeded" : ""}">${maximum || ""}</td>`;
            }).join("");
            tr.className = "summary-sum-row summary-maximum-row";
            tr.innerHTML = `<td>${esc(row.title)}</td><td></td>${cells}`;
        } else {
            const calc = classDescriptors.map((col) => {
                let h1 = 0, h2 = 0;
                let h1g1 = 0, h1g2 = 0, h2g1 = 0, h2g2 = 0;
                let hasSubgroups = false;
                const sourceRows = rows.filter((r) => r.type === "subject" && (row.type === "sum" ? r.part === row.part : (r.part === "CORE" || r.part === "FORMABLE")));
                sourceRows.forEach((s) => {
                    const info = s.perClass[col.classKey];
                    if (!info) return;
                    if (info.year) {
                        h1 += Number(info.year.hours || 0);
                        h2 += Number(info.year.hours || 0);
                        if (info.year.subgroupRequired) {
                            hasSubgroups = true;
                            const g1 = Number(info.year.subgroup1Hours ?? info.year.hours ?? 0);
                            const g2 = Number(info.year.subgroup2Hours ?? info.year.hours ?? 0);
                            h1g1 += g1; h1g2 += g2;
                            h2g1 += g1; h2g2 += g2;
                        } else {
                            const base = Number(info.year.hours || 0);
                            h1g1 += base; h1g2 += base;
                            h2g1 += base; h2g2 += base;
                        }
                    } else {
                        h1 += Number(info.h1?.hours || 0);
                        h2 += Number(info.h2?.hours || 0);
                        if (info.h1?.subgroupRequired) {
                            hasSubgroups = true;
                            h1g1 += Number(info.h1.subgroup1Hours ?? info.h1.hours ?? 0);
                            h1g2 += Number(info.h1.subgroup2Hours ?? info.h1.hours ?? 0);
                        }
                        if (info.h2?.subgroupRequired) {
                            hasSubgroups = true;
                            h2g1 += Number(info.h2.subgroup1Hours ?? info.h2.hours ?? 0);
                            h2g2 += Number(info.h2.subgroup2Hours ?? info.h2.hours ?? 0);
                        } else if (info.h2) {
                            const base = Number(info.h2.hours || 0);
                            h2g1 += base; h2g2 += base;
                        }
                        if (info.h1 && !info.h1.subgroupRequired) {
                            const base = Number(info.h1.hours || 0);
                            h1g1 += base; h1g2 += base;
                        }
                    }
                });
                const sumLabel = row.type === "sum12"
                    ? "sum_of"
                    : (row.part === "CORE"
                        ? "sum_core"
                        : (row.part === "FORMABLE"
                            ? "sum_formable"
                            : (row.part === "EXTRACURRICULAR" ? "sum_extracurricular" : "sum_correctional")));
                const mismatch = sumMismatchKeys.has(`${col.classKey}|${sumLabel}`);
                const display = (() => {
                    if (!(h1 || h2)) return "";
                    if (!hasSubgroups) return h1 === h2 ? String(h1) : `${h1}/${h2}`;
                    const fmtGroup = (g1, g2) => g1 === g2 ? String(g1) : `${g1}//${g2}`;
                    const left = fmtGroup(h1g1, h1g2);
                    const right = fmtGroup(h2g1, h2g2);
                    return left === right ? left : `${left}/${right}`;
                })();
                return `<td class="summary-value ${mismatch ? "conflict-row" : ""}">${display}</td>`;
            }).join("");
            tr.className = "summary-sum-row";
            tr.innerHTML = `<td>${esc(row.title)}</td><td></td>${calc}`;
        }
        ui.summaryBody.appendChild(tr);
    });

    ui.summaryBody.querySelectorAll('.hours-cell[data-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const id = Number(btn.dataset.id);
            if (!Number.isFinite(id)) return;
            openEditById(id);
        });
    });
    ui.summaryBody.querySelectorAll('.hours-cell[data-create="1"]').forEach((btn) => {
        btn.addEventListener('click', () => {
            openCreateByCell({
                numberSchoolBuilding: norm(btn.dataset.building),
                className: norm(btn.dataset.className),
                subjectName: norm(btn.dataset.subjectName),
                curriculumPart: norm(btn.dataset.curriculumPart || "CORE"),
                educationLevel: norm(btn.dataset.educationLevel || "BASIC"),
                studyPeriod: norm(btn.dataset.studyPeriod || "YEAR"),
                studyPeriodSettingId: Number(btn.dataset.studyPeriodSettingId || 0) || null
            });
        });
    });
}

function renderSubjectOptions() {
    const part = ui.form.elements.curriculumPart.value || "CORE";
    const expectedType = subjectTypeByPart(part);
    const selected = ui.formSubject.value;
    ui.formSubject.innerHTML = '<option value="">Выберите предмет</option>';
    subjects.filter((s) => isSubjectTypeCompatible(s.subjectType, expectedType))
        .sort((a,b)=>String(a.subjectName).localeCompare(String(b.subjectName),"ru"))
        .forEach((s) => { ui.formSubject.innerHTML += `<option value="${esc(s.subjectName)}">${esc(s.subjectName)}</option>`; });
    if (selected) ui.formSubject.value = selected;
}

function normalizeForm() {
    const f = new FormData(ui.form);
    const className = norm(f.get("className"));
    const modularSystem = String(f.get("modularSystem")) === "true";
    const subgroupRequired = !modularSystem && String(f.get("subgroupRequired")) === "true";
    const subgroup1Hours = Number(f.get("subgroup1Hours") || 0);
    const subgroup2Hours = Number(f.get("subgroup2Hours") || 0);
    const plannedHours = subgroupRequired
        ? Math.max(subgroup1Hours, subgroup2Hours, 0)
        : Number(f.get("plannedHours") || 0);
    const modules = modularSystem ? readModuleEditor(ui.moduleList) : [];
    validateModuleHoursClient(modularSystem, modules, plannedHours);
    return {
        numberSchoolBuilding: norm(f.get("numberSchoolBuilding")),
        className,
        subjectName: norm(f.get("subjectName")),
        plannedHours,
        educationLevel: f.get("educationLevel"),
        subgroupRequired,
        subgroupCount: 2,
        subgroup1Hours: subgroupRequired ? subgroup1Hours : null,
        subgroup1EducationLevel: f.get("subgroup1EducationLevel") || null,
        subgroup2Hours: subgroupRequired ? subgroup2Hours : null,
        subgroup2EducationLevel: f.get("subgroup2EducationLevel") || null,
        curriculumPart: f.get("curriculumPart"),
        studyPeriodSettingId: Number(f.get("studyPeriod") || 0) || null,
        metaGroup: isExplicitMetaGroupClassName(className),
        excludedFromManualLoad: isExplicitMetaGroupClassName(className) ? false : String(f.get("excludedFromManualLoad")) === "true",
        modularSystem,
        modules
    };
}

function validateModuleHoursClient(modularSystem, modules, plannedHours) {
    if (!modularSystem) return;
    if (modules.length < 2) throw new Error("Добавьте не менее двух модулей");
    const total = modules.reduce((sum, module) => sum + Number(module.plannedHours || 0), 0);
    if (total !== Number(plannedHours || 0)) {
        throw new Error(`Сумма часов модулей (${total}) должна быть равна часам предмета (${plannedHours})`);
    }
}

function assertModulesPersisted(saved, payload) {
    if (!payload?.modularSystem) return;
    const expected = payload.modules || [];
    const actual = saved?.modules || [];
    if (!saved?.modularSystem || actual.length !== expected.length) {
        throw new Error("Сервер не сохранил модули предмета. Проверьте миграцию curriculum_module на сервере.");
    }
    expected.forEach((module, index) => {
        const persisted = actual[index];
        if (String(persisted?.moduleName || "").trim() !== String(module.moduleName || "").trim()
                || Number(persisted?.plannedHours || 0) !== Number(module.plannedHours || 0)) {
            throw new Error("Сервер сохранил модули предмета не полностью");
        }
    });
}

async function importCurriculumFile() {
    const file = ui.importFile?.files?.[0];
    if (!file) {
        print({ error: "Выберите Excel-файл для импорта" });
        return;
    }

    const form = new FormData();
    form.append("file", file);

    try {
        const result = await api("/api/curriculum/import", { method: "POST", body: form });
        sumMismatchKeys = new Set((result?.sumMismatches || []).map((x) => `${x.classKey}|${x.sumLabel}`));
        print({ status: "imported", ...result });
        ui.importFile.value = "";
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
}

async function exportCurriculumFile() {
    try {
        const path = window.withAcademicYear ? window.withAcademicYear("/api/curriculum/export") : "/api/curriculum/export";
        const response = await fetch(path);
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "curriculum-editable.xlsx";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        print({ status: "exported", file: "curriculum-editable.xlsx" });
    } catch (error) {
        print({ error: error.message });
    }
}


async function exportCurriculumParallelsFile() {
    try {
        const path = window.withAcademicYear ? window.withAcademicYear("/api/curriculum/export-parallels") : "/api/curriculum/export-parallels";
        const response = await fetch(path);
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "curriculum-by-parallels.xlsx";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        print({ status: "exported", file: "curriculum-by-parallels.xlsx" });
    } catch (error) {
        print({ error: error.message });
    }
}

function captureCurriculumScroll() {
    const wrap = ui.summaryBody?.closest(".sheet-wrap");
    return {
        tableLeft: wrap?.scrollLeft || 0,
        tableTop: wrap?.scrollTop || 0,
        windowX: window.scrollX || 0,
        windowY: window.scrollY || 0
    };
}

function defaultModule(index = 0) {
    return {
        id: null,
        moduleName: "",
        plannedHours: "",
        educationLevel: "BASIC",
        subgroupRequired: false,
        subgroup1Hours: "",
        subgroup1EducationLevel: "BASIC",
        subgroup2Hours: "",
        subgroup2EducationLevel: "BASIC",
        moduleOrder: index + 1
    };
}

function moduleCardMarkup(module, index, removable) {
    const subgroup = Boolean(module?.subgroupRequired);
    return `<div class="module-card" data-module-card data-module-id="${esc(module?.id || "")}">
        <div class="module-card-head"><strong>Модуль ${index + 1}</strong><button type="button" class="danger-btn" data-remove-module ${removable ? "" : "disabled"}>Удалить</button></div>
        <div class="module-fields">
            <label>Название модуля<input data-module-field="moduleName" value="${esc(module?.moduleName || "")}" required></label>
            <label>Часов<input data-module-field="plannedHours" type="number" min="0.1" step="0.1" value="${esc(module?.plannedHours ?? "")}" required></label>
            <label>Уровень<select data-module-field="educationLevel"><option value="BASIC" ${(module?.educationLevel || "BASIC") === "BASIC" ? "selected" : ""}>Базовый</option><option value="ADVANCED" ${module?.educationLevel === "ADVANCED" ? "selected" : ""}>Углублённый</option></select></label>
            <label>Деление<select data-module-field="subgroupRequired"><option value="false" ${!subgroup ? "selected" : ""}>Без деления</option><option value="true" ${subgroup ? "selected" : ""}>С делением</option></select></label>
        </div>
        <div class="module-subgroup-fields ${subgroup ? "" : "hidden"}" data-module-subgroups>
            <label>1 подгруппа, часов<input data-module-field="subgroup1Hours" type="number" min="0" value="${esc(module?.subgroup1Hours ?? "")}"></label>
            <label>1 подгруппа, уровень<select data-module-field="subgroup1EducationLevel"><option value="BASIC" ${(module?.subgroup1EducationLevel || "BASIC") === "BASIC" ? "selected" : ""}>Базовый</option><option value="ADVANCED" ${module?.subgroup1EducationLevel === "ADVANCED" ? "selected" : ""}>Углублённый</option></select></label>
            <label>2 подгруппа, часов<input data-module-field="subgroup2Hours" type="number" min="0" value="${esc(module?.subgroup2Hours ?? "")}"></label>
            <label>2 подгруппа, уровень<select data-module-field="subgroup2EducationLevel"><option value="BASIC" ${(module?.subgroup2EducationLevel || "BASIC") === "BASIC" ? "selected" : ""}>Базовый</option><option value="ADVANCED" ${module?.subgroup2EducationLevel === "ADVANCED" ? "selected" : ""}>Углублённый</option></select></label>
        </div>
    </div>`;
}

function readModuleEditor(list) {
    return Array.from(list?.querySelectorAll("[data-module-card]") || []).map((card, index) => {
        const value = (name) => card.querySelector(`[data-module-field="${name}"]`)?.value ?? "";
        const subgroupRequired = value("subgroupRequired") === "true";
        return {
            id: card.dataset.moduleId ? Number(card.dataset.moduleId) : null,
            moduleOrder: index + 1,
            moduleName: String(value("moduleName")).trim(),
            plannedHours: Number(value("plannedHours") || 0),
            educationLevel: value("educationLevel") || "BASIC",
            subgroupRequired,
            subgroup1Hours: subgroupRequired ? Number(value("subgroup1Hours") || 0) : null,
            subgroup1EducationLevel: subgroupRequired ? value("subgroup1EducationLevel") : null,
            subgroup2Hours: subgroupRequired ? Number(value("subgroup2Hours") || 0) : null,
            subgroup2EducationLevel: subgroupRequired ? value("subgroup2EducationLevel") : null
        };
    });
}

function updateModuleHoursSummary(list, summary, totalInput) {
    if (!summary) return;
    const moduleTotal = readModuleEditor(list).reduce((sum, module) => sum + Number(module.plannedHours || 0), 0);
    const subjectTotal = Number(totalInput?.value || 0);
    summary.textContent = `Сумма модулей: ${moduleTotal} из ${subjectTotal} ч.`;
    summary.classList.toggle("module-hours-invalid", moduleTotal !== subjectTotal);
}

function renderModuleEditor(list, modules, summary, totalInput) {
    const values = modules?.length ? modules : [defaultModule(0), defaultModule(1)];
    list.innerHTML = values.map((module, index) => moduleCardMarkup(module, index, values.length > 2)).join("");
    list.querySelectorAll("[data-module-field=\"subgroupRequired\"]").forEach((select) => {
        select.addEventListener("change", () => select.closest("[data-module-card]")?.querySelector("[data-module-subgroups]")?.classList.toggle("hidden", select.value !== "true"));
    });
    list.querySelectorAll("[data-remove-module]").forEach((button) => button.addEventListener("click", () => {
        const current = readModuleEditor(list);
        const index = Array.from(list.querySelectorAll("[data-module-card]")).indexOf(button.closest("[data-module-card]"));
        current.splice(index, 1);
        renderModuleEditor(list, current, summary, totalInput);
    }));
    list.querySelectorAll("input,select").forEach((control) => control.addEventListener("input", () => updateModuleHoursSummary(list, summary, totalInput)));
    updateModuleHoursSummary(list, summary, totalInput);
}

function toggleModuleSystem(form, config, list, summary, modules = null) {
    const enabled = form?.elements?.modularSystem?.value === "true";
    config?.classList.toggle("hidden", !enabled);
    const subgroupSelect = form?.elements?.subgroupRequired;
    if (subgroupSelect) {
        subgroupSelect.disabled = enabled;
        if (enabled) subgroupSelect.value = "false";
    }
    if (enabled && list && (!list.children.length || modules)) {
        renderModuleEditor(list, modules, summary, form.elements.plannedHours);
    }
    const subgroupContainer = form === ui.form ? ui.subgroupConfig : ui.editSubgroupConfig;
    toggleSubgroupConfig(subgroupContainer, enabled ? "false" : subgroupSelect?.value);
}

function restoreCurriculumScroll(state) {
    if (!state) return;
    requestAnimationFrame(() => requestAnimationFrame(() => {
        const wrap = ui.summaryBody?.closest(".sheet-wrap");
        if (wrap) {
            wrap.scrollLeft = state.tableLeft;
            wrap.scrollTop = state.tableTop;
            wrap.dispatchEvent(new Event("scroll"));
        }
        window.scrollTo(state.windowX, state.windowY);
    }));
}

async function reload(scrollState = captureCurriculumScroll()) {
    const [curriculum, classRows, buildingRows, subjectRows, settingRows, metaGroupRows, loadLimits] = await Promise.all([
        api("/api/curriculum"),
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/subjects"),
        api("/api/settings/study-periods"),
        api("/api/meta-groups"),
        api("/api/curriculum/max-load-limits")
    ]);
    curriculumRows = curriculum || [];
    subjects = subjectRows || [];
    studyPeriodSettings = settingRows || [];
    metaGroups = metaGroupRows || [];
    maxLoadLimits = loadLimits || {};
    classes = (classRows || []).map((r) => ({
        numberSchoolBuilding: norm(r.numberSchoolBuilding),
        className: norm(r.className),
        classDirection: norm(r.classDirection),
        classType: norm(r.classType) || "NORMAL"
    })).filter((r) => r.numberSchoolBuilding && r.className);

    buildings = (buildingRows || []).sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));

    if (!issueNavigationHandled && issueNavigation.className) {
        const targetParallel = classToParallel(issueNavigation.className);
        if (Number.isFinite(targetParallel)) selectedParallel = targetParallel;
        if (issueNavigation.building) selectedBuilding = norm(issueNavigation.building);
    }
    syncSelectedBuilding();
    renderParallelTabs();
    renderBuildingFilter();
    renderClassOptions();
    renderSubjectOptions();
    syncStudyPeriodControls();
    renderSummaryTable();
    focusIssueNavigationTarget();
    restoreCurriculumScroll(scrollState);
}

function focusIssueNavigationTarget() {
    if (issueNavigationHandled || !issueNavigation.className) return;
    const header = Array.from(ui.summaryHead.querySelectorAll("[data-summary-class]")).find((cell) =>
        norm(cell.dataset.summaryClass) === norm(issueNavigation.className)
        && (!issueNavigation.building || norm(cell.dataset.summaryBuilding) === norm(issueNavigation.building))
    );
    if (!header) return;
    issueNavigationHandled = true;
    header.classList.add("error-row-highlight");
    const columnIndex = header.cellIndex;
    ui.summaryBody.querySelectorAll("tr").forEach((row) => row.cells[columnIndex]?.classList.add("error-row-highlight"));
    requestAnimationFrame(() => header.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" }));
}

function bindEvents() {
    ui.form.addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            const payload = normalizeForm();
            if (!payload.numberSchoolBuilding || !payload.className) throw new Error("Выберите корпус и класс из справочника классов");
            const isKnownClass = classes.some((c) => c.numberSchoolBuilding === payload.numberSchoolBuilding && c.className === payload.className);
            const isKnownMetaGroup = metaGroups.some((m) => m.numberSchoolBuilding === payload.numberSchoolBuilding && `МГ:${m.name}` === payload.className);
            if (!isKnownClass && !isKnownMetaGroup) {
                throw new Error("Класс/метагруппа не найдены в справочнике");
            }

            const saved = await api("/api/curriculum", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
            assertModulesPersisted(saved, payload);
            if (payload.modularSystem) {
                curriculumDisplayMode = "detailed";
                if (ui.displayMode) ui.displayMode.value = "detailed";
            }
            sumMismatchKeys = new Set();
            print({ status: "saved", payload });
            await reload();
            assertModulesPersisted(curriculumRows.find((row) => Number(row.id) === Number(saved.id)), payload);
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.formBuilding.addEventListener("change", renderClassOptions);
    ui.formClass.addEventListener("change", () => {
        configureExclusionControl(ui.form, ui.formClass.value, ui.subjectExclusionLabel, ui.subjectMetaGroupInfo);
        syncStudyPeriodControls();
    });
    ui.form.elements.curriculumPart.addEventListener("change", renderSubjectOptions);
    ui.buildingFilter.addEventListener("change", () => {
        selectedBuilding = norm(ui.buildingFilter.value);
        renderClassOptions();
        syncStudyPeriodControls();
        renderSummaryTable();
    });


    ui.createMetaGroupBtn?.addEventListener("click", async () => {
        renderMetaGroupCreateForm();
        ui.metaGroupCreateDialog?.showModal();
    });

    ui.manageMetaGroupBtn?.addEventListener("click", async () => {
        renderMetaGroupManageTable();
        ui.metaGroupManageDialog?.showModal();
    });

    ui.metaGroupCreateForm?.addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            const form = new FormData(ui.metaGroupCreateForm);
            await api("/api/meta-groups", {
                method: "POST",
                headers: jsonHeaders,
                body: JSON.stringify({
                    numberSchoolBuilding: norm(form.get("numberSchoolBuilding")),
                    parallel: Number(form.get("parallel")),
                    name: norm(form.get("name")),
                    classType: selectedParallel === AOOP_TAB_KEY ? AOOP_TAB_KEY : "NORMAL",
                    studyPeriodSettingId: Number(form.get("studyPeriodSettingId")) || null,
                    schoolBuildingId: Number(form.get("schoolBuildingId")) || null
                })
            });
            ui.metaGroupCreateDialog?.close();
            await reload();
        } catch (error) { print({ error: error.message }); }
    });
    document.getElementById("close-meta-group-create")?.addEventListener("click", () => ui.metaGroupCreateDialog?.close());
    document.getElementById("close-meta-group-manage")?.addEventListener("click", () => ui.metaGroupManageDialog?.close());
    ui.metaGroupEditCloseBtn?.addEventListener("click", () => ui.metaGroupEditDialog?.close());

    ui.metaGroupEditForm?.addEventListener("submit", async (e) => {
        e.preventDefault();
        showMetaGroupEditError();
        try {
            const form = new FormData(ui.metaGroupEditForm);
            const id = Number(form.get("id"));
            await api(`/api/meta-groups/${id}`, {
                method: "PATCH",
                headers: jsonHeaders,
                body: JSON.stringify({
                    numberSchoolBuilding: norm(form.get("numberSchoolBuilding")),
                    classType: norm(form.get("classType")) || "NORMAL",
                    parallel: Number(form.get("parallel")),
                    name: norm(form.get("name")),
                    studyPeriodSettingId: Number(form.get("studyPeriodSettingId")) || null,
                    schoolBuildingId: Number(form.get("schoolBuildingId")) || null
                })
            });
            ui.metaGroupEditDialog?.close();
            await reload();
            renderMetaGroupManageTable();
            renderSummaryTable();
        } catch (error) {
            const message = error?.message || String(error);
            showMetaGroupEditError(message);
            print({ error: message });
        }
    });

    ui.metaGroupDeleteBtn?.addEventListener("click", async () => {
        const id = Number(ui.metaGroupEditForm?.elements?.id?.value || 0);
        if (!id) return;
        if (!window.confirm("Удалить метагруппу?")) return;
        try {
            await api(`/api/meta-groups/${id}`, { method: "DELETE" });
            ui.metaGroupEditDialog?.close();
            await reload();
            renderMetaGroupManageTable();
            renderSummaryTable();
        } catch (error) {
            const message = error?.message || String(error);
            showMetaGroupEditError(message);
            print({ error: message });
        }
    });

    ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
    ui.importBtn?.addEventListener("click", importCurriculumFile);
    ui.exportBtn?.addEventListener("click", exportCurriculumFile);
    ui.exportParallelsBtn?.addEventListener("click", exportCurriculumParallelsFile);
    ui.subgroupRequired.addEventListener("change", () => {
        toggleSubgroupConfig(ui.subgroupConfig, ui.subgroupRequired.value);
        if (ui.subgroupRequired.value === "true") {
            const h = ui.form.elements.plannedHours.value || "1";
            ui.form.elements.subgroup1Hours.value = ui.form.elements.subgroup1Hours.value || h;
            ui.form.elements.subgroup2Hours.value = ui.form.elements.subgroup2Hours.value || h;
        }
    });

    ui.modularSystem?.addEventListener("change", () => {
        toggleModuleSystem(ui.form, ui.moduleConfig, ui.moduleList, ui.moduleHoursSummary);
    });
    ui.form.elements.plannedHours?.addEventListener("input", () => updateModuleHoursSummary(ui.moduleList, ui.moduleHoursSummary, ui.form.elements.plannedHours));
    ui.addModuleBtn?.addEventListener("click", () => {
        const modules = readModuleEditor(ui.moduleList);
        modules.push(defaultModule(modules.length));
        renderModuleEditor(ui.moduleList, modules, ui.moduleHoursSummary, ui.form.elements.plannedHours);
    });
    ui.displayMode?.addEventListener("change", () => {
        curriculumDisplayMode = ui.displayMode.value || "detailed";
        renderSummaryTable();
    });

    ui.editForm.elements.subgroupRequired.addEventListener("change", () => {
        toggleSubgroupConfig(ui.editSubgroupConfig, ui.editForm.elements.subgroupRequired.value);
    });
    ui.editForm.elements.modularSystem.addEventListener("change", () => {
        toggleModuleSystem(ui.editForm, ui.editModuleConfig, ui.editModuleList, ui.editModuleHoursSummary);
    });
    ui.editForm.elements.plannedHours?.addEventListener("input", () => updateModuleHoursSummary(ui.editModuleList, ui.editModuleHoursSummary, ui.editForm.elements.plannedHours));
    ui.editAddModuleBtn?.addEventListener("click", () => {
        const modules = readModuleEditor(ui.editModuleList);
        modules.push(defaultModule(modules.length));
        renderModuleEditor(ui.editModuleList, modules, ui.editModuleHoursSummary, ui.editForm.elements.plannedHours);
    });

    ui.closeDialogBtn.addEventListener("click", () => ui.editDialog.close());

    ui.deleteItemBtn.addEventListener("click", async () => {
        const id = Number(ui.editForm.elements.id.value);
        if (!Number.isFinite(id)) return;
        try {
            await api(`/api/curriculum/${id}`, { method: "DELETE" });
            ui.editDialog.close();
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.editForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const id = Number(ui.editForm.elements.id.value);
        const existing = curriculumRows.find((r) => r.id === id);

        const modularSystem = ui.editForm.elements.modularSystem.value === "true";
        const subgroupRequired = !modularSystem && ui.editForm.elements.subgroupRequired.value === "true";
        const subgroup1Hours = Number(ui.editForm.elements.subgroup1Hours.value || 0);
        const subgroup2Hours = Number(ui.editForm.elements.subgroup2Hours.value || 0);
        const plannedHours = subgroupRequired
            ? Math.max(subgroup1Hours, subgroup2Hours, 0)
            : Number(ui.editForm.elements.plannedHours.value || 0);
        const modules = modularSystem ? readModuleEditor(ui.editModuleList) : [];
        validateModuleHoursClient(modularSystem, modules, plannedHours);
        const payload = {
            numberSchoolBuilding: existing?.numberSchoolBuilding || pendingCreateContext?.numberSchoolBuilding,
            className: existing?.className || pendingCreateContext?.className,
            subjectName: existing?.subjectName || pendingCreateContext?.subjectName,
            curriculumPart: existing?.curriculumPart || pendingCreateContext?.curriculumPart,
            plannedHours,
            educationLevel: ui.editForm.elements.educationLevel.value || existing?.educationLevel || pendingCreateContext?.educationLevel || "BASIC",
            subgroupRequired,
            subgroupCount: 2,
            studyPeriodSettingId: Number(ui.editForm.elements.studyPeriod.value || 0) || null,
            metaGroup: isExplicitMetaGroupClassName(existing?.className || pendingCreateContext?.className),
            excludedFromManualLoad: isExplicitMetaGroupClassName(existing?.className || pendingCreateContext?.className) ? false : ui.editForm.elements.excludedFromManualLoad.value === "true",
            subgroup1Hours: subgroupRequired ? subgroup1Hours : null,
            subgroup2Hours: subgroupRequired ? subgroup2Hours : null,
            subgroup1EducationLevel: subgroupRequired ? ui.editForm.elements.subgroup1EducationLevel.value : null,
            subgroup2EducationLevel: subgroupRequired ? ui.editForm.elements.subgroup2EducationLevel.value : null,
            modularSystem,
            modules
        };

        try {
            if (existing) {
                const saved = await api(`/api/curriculum/${id}`, { method: "PATCH", headers: jsonHeaders, body: JSON.stringify(payload) });
                assertModulesPersisted(saved, payload);
            } else {
                if (!payload.numberSchoolBuilding || !payload.className || !payload.subjectName || !payload.curriculumPart) {
                    throw new Error("Недостаточно данных для создания записи");
                }
                const saved = await api("/api/curriculum", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
                assertModulesPersisted(saved, payload);
            }
            sumMismatchKeys = new Set();
            pendingCreateContext = null;
            if (payload.modularSystem) {
                curriculumDisplayMode = "detailed";
                if (ui.displayMode) ui.displayMode.value = "detailed";
            }
            ui.editDialog.close();
            await reload();
            const persisted = curriculumRows.find((row) => Number(row.id) === Number(id));
            if (existing) assertModulesPersisted(persisted, payload);
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.clearBtn.addEventListener("click", async () => {
        try {
            await api("/api/curriculum", { method: "DELETE" });
            print({ status: "all curriculum rules deleted" });
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });
}

try {
    bindEvents();
} catch (error) {
    print({ error: `Ошибка инициализации страницы: ${error?.message || error}` });
    ui.importBtn?.addEventListener("click", importCurriculumFile);
    ui.exportBtn?.addEventListener("click", exportCurriculumFile);
    ui.exportParallelsBtn?.addEventListener("click", exportCurriculumParallelsFile);
}
toggleSubgroupConfig(ui.subgroupConfig, ui.subgroupRequired.value);
renderModuleEditor(ui.moduleList, [defaultModule(0), defaultModule(1)], ui.moduleHoursSummary, ui.form.elements.plannedHours);
toggleModuleSystem(ui.form, ui.moduleConfig, ui.moduleList, ui.moduleHoursSummary);
syncStudyPeriodControls();
reload().catch((error) => print({ error: error.message }));
