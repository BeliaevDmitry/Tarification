const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    tabs: Array.from(document.querySelectorAll("[data-load-tab]")),
    panes: Array.from(document.querySelectorAll("[data-load-pane]")),
    buildingTabs: document.getElementById("building-tabs"),
    refreshLoadBtn: document.getElementById("refresh-load-btn"),
    exportLoadBtn: document.getElementById("export-load-btn"),
    importLoadBtn: document.getElementById("import-load-btn"),
    importLoadFile: document.getElementById("import-load-file"),
    saveBuildingBtn: document.getElementById("save-building-btn"),
    loadResult: document.getElementById("load-result"),
    tableHead: document.getElementById("building-load-head"),
    tableBody: document.getElementById("building-load-body"),
    sortField: document.getElementById("sort-field-select"),
    sortDirection: document.getElementById("sort-direction-select"),
    viewMode: document.getElementById("load-view-mode-select"),
    viewDateLabel: document.getElementById("load-view-date-label"),
    viewDateInput: document.getElementById("load-view-date-input"),
    periodDialog: document.getElementById("load-period-dialog"),
    periodForm: document.getElementById("load-period-form"),
    removeLoadBtn: document.getElementById("load-remove-btn"),
    cancelLoadBtn: document.getElementById("load-cancel-btn"),
    unassignedHours: document.getElementById("unassigned-hours"),
    errorCount: document.getElementById("error-count"),
    nextErrorBtn: document.getElementById("next-error-btn"),
    statsSummary: document.getElementById("load-stats-summary"),
    statsTable: document.getElementById("load-stats-table"),
    exportStatsBtn: document.getElementById("export-load-stats-btn")
};

let curriculumRows = [];
let manualRows = [];
let teacherNames = [];
let teacherDirectory = [];
let teacherDirectoryByName = new Map();
let buildings = [];
let classroomRows = [];
let studyPeriodSettings = [];
let subjectCatalog = [];
let selectedBuilding = "";
let activeLoadTab = "distribution";
let sourceRevision = 0;
let renderTableRaf = null;
const LOAD_SELECTED_BUILDING_KEY = "tarification.load.selectedBuilding";

const ARCHIVE_BUILDING_CODE = "__ARCHIVE__";
const ARCHIVE_BUILDING_LABEL = "Архив нагрузки";

const state = {
    assignmentsByBuilding: {},
    subjectTeacherRowsByBuilding: {},
    rowOrderByBuilding: {},
    sortField: "subject",
    sortDirection: "asc",
    viewMode: "all",
    viewDate: "",
    forceResort: true,
    hasUnsavedChanges: false,
    classSort: "",
    futurePlansByBuilding: {},
    takeoverContext: null,
    continuityExpectedByKey: new Map()
};

const derivedCache = {
    classBuildingMapRowsRef: null,
    classBuildingMapValue: new Map(),
    rowsByBuildingKey: "",
    rowsByBuildingValue: [],
    expandedRowsByBuildingKey: "",
    expandedRowsByBuildingValue: []
};

function invalidateDerivedCache() {
    derivedCache.classBuildingMapRowsRef = null;
    derivedCache.classBuildingMapValue = new Map();
    derivedCache.rowsByBuildingKey = "";
    derivedCache.rowsByBuildingValue = [];
    derivedCache.expandedRowsByBuildingKey = "";
    derivedCache.expandedRowsByBuildingValue = [];
}


async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text ? { message: text } : null;
    }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

async function apiUnscoped(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text ? { message: text } : null;
    }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function sortRu(values) {
    return [...values].sort((a, b) => String(a).localeCompare(String(b), "ru"));
}

function normalizeBuildingCode(value) {
    return String(value || "")
        .trim()
        .toUpperCase()
        .replace(/[–—]/g, "-")
        .replace(/\s*\|\s*/g, "|")
        .replace(/\s+/g, "");
}

function canonicalBuildingCode(value) {
    const normalized = normalizeBuildingCode(value);
    if (!normalized) return "";
    const match = (buildings || []).find((b) => {
        const code = normalizeBuildingCode(b?.code);
        const name = normalizeBuildingCode(b?.name);
        return code === normalized || name === normalized;
    });
    return match ? normalizeBuildingCode(match.code) : normalized;
}

function rememberSelectedBuilding(code) {
    const normalized = normalizeBuildingCode(code);
    if (!normalized || normalized === ARCHIVE_BUILDING_CODE) return;
    sessionStorage.setItem(LOAD_SELECTED_BUILDING_KEY, normalized);
}

function restoreSelectedBuilding() {
    return normalizeBuildingCode(sessionStorage.getItem(LOAD_SELECTED_BUILDING_KEY) || "");
}

function addressesForBuildingCode(buildingCode) {
    const normalizedCode = normalizeBuildingCode(buildingCode);
    if (!normalizedCode) return [];

    const addresses = [];
    const pushUnique = (value) => {
        const cleaned = String(value || "").trim();
        if (!cleaned) return;
        const key = cleaned.toLowerCase();
        if (addresses.some((item) => item.toLowerCase() === key)) return;
        addresses.push(cleaned);
    };

    const fromBuilding = (buildings || []).find((b) => normalizeBuildingCode(b?.code) === normalizedCode);
    pushUnique(fromBuilding?.address);

    (classroomRows || []).forEach((row) => {
        if (normalizeBuildingCode(row?.numberSchoolBuilding) !== normalizedCode) return;
        pushUnique(row?.campusAddress);
    });

    return addresses;
}

function buildingTabLabel(building) {
    const base = String(building?.name || building?.code || "").trim();
    const addresses = addressesForBuildingCode(building?.code);
    if (!addresses.length) return base;
    return `${base} — ${addresses.join(" / ")}`;
}


function normalizeClassName(value) {
    const v = String(value || "").trim().toUpperCase().replace(/[–—]/g, "-");
    const m = v.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return m ? `${m[1]}-${m[2]}` : v;
}

function previousClassForContinuity(targetClass) {
    const normalized = normalizeClassName(targetClass);
    const match = normalized.match(/^(\d{1,2})-([А-ЯA-Z])$/);
    if (!match) return null;
    const parallel = Number(match[1]);
    if (!Number.isFinite(parallel) || parallel <= 1) return null;
    if (parallel === 5 || parallel === 10) return null;
    return `${parallel - 1}-${match[2]}`;
}

function previousAcademicYearCode(yearCode) {
    const [fromYear] = String(yearCode || "").split("/");
    const year = Number(fromYear);
    if (!Number.isFinite(year) || year <= 0) return null;
    return `${year - 1}/${year}`;
}

function continuityGroupName(row) {
    if (row?.groupNameEducationalPlan) return String(row.groupNameEducationalPlan).trim();
    if (row?.__groupIndex) return `Группа ${row.__groupIndex}`;
    if (row?.subgroupRequired) return "Группа 1";
    return "";
}

function continuityKey(className, subjectName, groupName) {
    return [
        normalizeClassName(className),
        String(subjectName || "").trim().toLowerCase(),
        String(groupName || "").trim().toLowerCase()
    ].join("|");
}

function computeContinuityExpectedByKey(sourceManual, targetCurriculum) {
    const sourceByKey = new Map();
    (sourceManual || []).forEach((row) => {
        const teacher = String(row.fioTeacher || "").trim();
        if (!teacher || isVacancyTeacherName(teacher)) return;
        sourceByKey.set(
            continuityKey(row.className, row.subjectName, row.groupNameEducationalPlan),
            teacher.toLowerCase()
        );
    });

    const expectedByTarget = new Map();
    (targetCurriculum || []).forEach((row) => {
        const prevClass = previousClassForContinuity(row.className);
        if (!prevClass) return;
        const groupName = continuityGroupName(row);
        const expectedTeacher = sourceByKey.get(continuityKey(prevClass, row.subjectName, groupName));
        if (!expectedTeacher) return;
        expectedByTarget.set(continuityKey(row.className, row.subjectName, groupName), expectedTeacher);
    });
    return expectedByTarget;
}

function classBuildingMap() {
    if (derivedCache.classBuildingMapRowsRef === classroomRows) {
        return derivedCache.classBuildingMapValue;
    }
    const map = new Map();
    (classroomRows || []).forEach((r) => {
        const cls = normalizeClassName(r.className);
        const b = normalizeBuildingCode(r.numberSchoolBuilding);
        if (cls && b) map.set(cls, b);
    });
    derivedCache.classBuildingMapRowsRef = classroomRows;
    derivedCache.classBuildingMapValue = map;
    return map;
}

function print(value) {
    if (ui.loadResult) {
        ui.loadResult.textContent = JSON.stringify(value, null, 2);
    } else {
        console.debug(value);
    }
}

function scheduleRenderTable() {
    if (renderTableRaf !== null) return;
    renderTableRaf = window.requestAnimationFrame(() => {
        renderTableRaf = null;
        renderTable();
    });
}

function showLoadTab(name) {
    activeLoadTab = "distribution";
    ui.tabs.forEach((tab) => tab.classList.toggle("active", tab.dataset.loadTab === activeLoadTab));
    ui.panes.forEach((pane) => {
        pane.style.display = pane.dataset.loadPane === activeLoadTab ? "" : "none";
    });
}

function loadPermissions() {
    if (window.tarificationAuth?.admin) {
        return { canDistributionView: true, canStatsView: true };
    }
    const permissions = window.tarificationTabPermissions || {};
    return {
        canDistributionView: Boolean(permissions.LOAD?.canView),
        canStatsView: Boolean(permissions.LOAD_STATS?.canView)
    };
}

function applyLoadTabAccess() {
    const { canDistributionView } = loadPermissions();
    ui.tabs.forEach((tab) => {
        tab.style.display = canDistributionView ? "" : "none";
    });
    ui.panes.forEach((pane) => {
        const isDistribution = pane.dataset.loadPane === "distribution";
        if (!isDistribution || !canDistributionView) pane.style.display = "none";
    });
    return canDistributionView ? "distribution" : null;
}

async function waitForAuthContext() {
    for (let i = 0; i < 40; i += 1) {
        if (window.tarificationAuth) return;
        await new Promise((resolve) => setTimeout(resolve, 50));
    }
}

function markDirty(flag=true) {
    state.hasUnsavedChanges = flag;
    ui.saveBuildingBtn.classList.toggle("dirty-save", flag);
    ui.saveBuildingBtn.classList.toggle("clean-save", !flag);
}

function esc(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function partLabel(part) {
    if (part === "FORMABLE") return "Формируемая";
    if (part === "EXTRACURRICULAR") return "Внеурочная";
    return "Основная";
}

function educationLevelLabel(value) {
    if (value === "BASIC") return "Базовый";
    if (value === "ADVANCED") return "Углублённый";
    return String(value || "");
}

function groupSuffix(row) {
    return row.__groupIndex ? `|g${row.__groupIndex}` : "";
}

function classParallel(className) {
    const match = String(className || "").trim().match(/^(\d{1,2})/);
    return match ? Number(match[1]) : null;
}

function rowStudyPeriod(row) {
    return row?.studyPeriod || "YEAR";
}

function periodSettingKeyForClass(className, studyPeriod = "YEAR") {
    const parallel = classParallel(className);
    if (parallel == null || parallel <= 9) {
        if (studyPeriod === "H1") return "H1_1_9";
        if (studyPeriod === "H2") return "H2_1_9";
        return "YEAR_1_9";
    }
    if (parallel === 10) {
        if (studyPeriod === "YEAR") return "YEAR_10";
        return studyPeriod === "H2" ? "H2_10" : "H1_10";
    }
    if (studyPeriod === "YEAR") return "YEAR_11";
    return studyPeriod === "H2" ? "H2_11" : "H1_11";
}

function periodLabel(studyPeriod) {
    return ({ YEAR: "год", H1: "1П", H2: "2П" })[studyPeriod] || studyPeriod || "";
}

function displaySubjectName(row) {
    const suffix = classParallel(row.className) >= 10 ? "" : (rowStudyPeriod(row) !== "YEAR" ? ` · ${periodLabel(rowStudyPeriod(row))}` : "");
    return row.__groupIndex ? `${row.subjectName} ${row.__groupIndex}Г${suffix}` : `${row.subjectName}${suffix}`;
}

function classPeriodHours(rows = []) {
    const year = rows.filter((r) => rowStudyPeriod(r) === "YEAR").reduce((s, r) => s + Number(r.plannedHours || 0), 0);
    const h1 = rows.filter((r) => rowStudyPeriod(r) === "H1").reduce((s, r) => s + Number(r.plannedHours || 0), 0);
    const h2 = rows.filter((r) => rowStudyPeriod(r) === "H2").reduce((s, r) => s + Number(r.plannedHours || 0), 0);
    return { year, h1, h2 };
}

function classPeriodText(rows = []) {
    const p = classPeriodHours(rows);
    if (p.year > 0) return String(p.year);
    if (p.h1 > 0 && p.h2 > 0) {
        if (p.h1 === p.h2) return String(p.h1);
        return `${p.h1}/${p.h2}`;
    }
    if (p.h1 > 0) return `${p.h1} (1П)`;
    if (p.h2 > 0) return `${p.h2} (2П)`;
    return "";
}

function formatSplitHours(pair) {
    return `${pair.h1}/${pair.h2}`;
}

function accumulateSplit(pair, row) {
    const value = Number(row?.plannedHours || 0);
    const period = rowStudyPeriod(row);
    if (period === "H1") pair.h1 += value;
    else if (period === "H2") pair.h2 += value;
    else {
        pair.h1 += value;
        pair.h2 += value;
    }
}

function apiKeyOfRow(row) {
    return `${row.className}|${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}|${rowStudyPeriod(row)}${groupSuffix(row)}`;
}

function subjectKeyOfRow(row) {
    const periodToken = "YEAR";
    return `${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}|${periodToken}${groupSuffix(row)}`;
}

function highSchoolUnifiedSubject(row) {
    return classParallel(row?.className) >= 10;
}

function defaultPeriodForRows(rows) {
    const baseRow = rows?.[0] || {};
    if (!highSchoolUnifiedSubject(baseRow)) {
        const periodValue = rowStudyPeriod(baseRow);
        return { studyPeriod: periodValue, ...defaultLoadPeriod(baseRow?.className, periodValue) };
    }
    const h1 = defaultLoadPeriod(baseRow?.className, "H1");
    const h2 = defaultLoadPeriod(baseRow?.className, "H2");
    return { studyPeriod: "YEAR", from: h1.from, to: h2.to };
}

function rowsToSyncForCurriculumRow(curriculumRow) {
    if (!highSchoolUnifiedSubject(curriculumRow)) return [curriculumRow];
    return expandedRowsForSelectedBuilding().filter((row) =>
        row.className === curriculumRow.className
        && row.subjectName === curriculumRow.subjectName
        && (row.curriculumPart || "CORE") === (curriculumRow.curriculumPart || "CORE")
        && row.educationLevel === curriculumRow.educationLevel
        && groupSuffix(row) === groupSuffix(curriculumRow)
    );
}

function rowId() {
    return `t_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function assignmentsForBuilding(buildingCode) {
    if (!state.assignmentsByBuilding[buildingCode]) {
        state.assignmentsByBuilding[buildingCode] = {};
    }
    return state.assignmentsByBuilding[buildingCode];
}

function teacherRowsForBuilding(buildingCode) {
    if (!state.subjectTeacherRowsByBuilding[buildingCode]) {
        state.subjectTeacherRowsByBuilding[buildingCode] = {};
    }
    return state.subjectTeacherRowsByBuilding[buildingCode];
}


function futurePlansForBuilding(buildingCode) {
    if (!state.futurePlansByBuilding[buildingCode]) {
        state.futurePlansByBuilding[buildingCode] = {};
    }
    return state.futurePlansByBuilding[buildingCode];
}


function dayBefore(isoDate) {
    if (!isoDate) return isoDate;
    const d = new Date(`${isoDate}T00:00:00Z`); // явно указываем UTC
    d.setUTCDate(d.getUTCDate() - 1);
    return d.toISOString().slice(0, 10);
}


function rowsForSelectedBuilding() {
    if (selectedBuilding === ARCHIVE_BUILDING_CODE) return [];
    const cacheKey = `${sourceRevision}|${canonicalBuildingCode(selectedBuilding)}`;
    if (derivedCache.rowsByBuildingKey === cacheKey) {
        return derivedCache.rowsByBuildingValue;
    }
    const normalizedSelectedBuilding = canonicalBuildingCode(selectedBuilding);
    const map = classBuildingMap();
    const filtered = curriculumRows.filter((row) => {
        const rowBuilding = canonicalBuildingCode(row.numberSchoolBuilding);
        const byClass = canonicalBuildingCode(map.get(normalizeClassName(row.className)));
        return rowBuilding === normalizedSelectedBuilding || byClass === normalizedSelectedBuilding;
    });
    derivedCache.rowsByBuildingKey = cacheKey;
    derivedCache.rowsByBuildingValue = filtered;
    return filtered;
}

function expandCurriculumRows(rows) {
    const expanded = [];
    rows.forEach((row) => {
        const subgroupRequired = Boolean(row.subgroupRequired);
        const subgroupCount = subgroupRequired ? Math.max(Number(row.subgroupCount || 2), 2) : 0;

        if (!subgroupRequired) {
            expanded.push({ ...row, __groupIndex: null, __groupCount: 0 });
            return;
        }

        for (let i = 1; i <= subgroupCount; i += 1) {
            const subgroupHours = i === 1
                ? Number(row.subgroup1Hours || row.plannedHours || 0)
                : Number(row.subgroup2Hours || row.plannedHours || 0);
            const subgroupLevel = i === 1
                ? (row.subgroup1EducationLevel || row.educationLevel)
                : (row.subgroup2EducationLevel || row.educationLevel);

            expanded.push({
                ...row,
                plannedHours: subgroupHours,
                educationLevel: subgroupLevel,
                __groupIndex: i,
                __groupCount: subgroupCount
            });
        }
    });
    return expanded;
}

function expandedRowsForSelectedBuilding() {
    if (selectedBuilding === ARCHIVE_BUILDING_CODE) return [];
    const cacheKey = `${sourceRevision}|${canonicalBuildingCode(selectedBuilding)}`;
    if (derivedCache.expandedRowsByBuildingKey === cacheKey) {
        return derivedCache.expandedRowsByBuildingValue;
    }
    const expanded = expandCurriculumRows(rowsForSelectedBuilding());
    derivedCache.expandedRowsByBuildingKey = cacheKey;
    derivedCache.expandedRowsByBuildingValue = expanded;
    return expanded;
}

function classesForSelectedBuilding() {
    return sortRu(Array.from(new Set(expandedRowsForSelectedBuilding().map((row) => row.className).filter(Boolean))));
}

function updateDatalistOptions(listEl, query = "") {
    if (!listEl) return;
    const q = String(query || "").trim().toLowerCase();
    const options = !q
        ? teacherNames.slice(0, 200)
        : teacherNames.filter((name) => name.toLowerCase().includes(q)).slice(0, 60);
    listEl.innerHTML = options.map((name) => `<option value="${esc(name)}"></option>`).join("");
}


function isDismissedTeacher(teacherName) {
    const normalized = String(teacherName || "").trim().toLowerCase();
    if (!normalized) return false;
    const teacher = teacherDirectoryByName.get(normalized);
    return Boolean(teacher?.dismissalDate);
}

function teacherExists(teacherName) {
    const normalized = String(teacherName || "").trim().toLowerCase();
    if (!normalized) return true;
    return teacherDirectoryByName.has(normalized);
}

function dismissalDateOfTeacher(teacherName) {
    const normalized = String(teacherName || "").trim().toLowerCase();
    const teacher = teacherDirectoryByName.get(normalized);
    return teacher?.dismissalDate || null;
}

function isVacancyTeacherName(teacherName) {
    return String(teacherName || "").trim().toLowerCase().includes("вакан");
}

function periodSettingsMap() {
    return Object.fromEntries((studyPeriodSettings || []).map((item) => [item.code || item.settingKey, item]));
}

function fallbackYearRange() {
    return {
        yearFrom: "2026-09-01",
        h1To: "2026-12-31",
        h2From: "2027-01-01",
        yearTo: "2027-05-31",
        h1_11_to: "2027-01-31",
        h2_11_from: "2027-02-01"
    };
}

function defaultLoadPeriod(classNameOrStudyPeriod = "YEAR", maybeStudyPeriod = null) {
    let className = null;
    let studyPeriod = "YEAR";
    if (maybeStudyPeriod != null) {
        className = classNameOrStudyPeriod;
        studyPeriod = maybeStudyPeriod || "YEAR";
    } else if (["YEAR", "H1", "H2"].includes(classNameOrStudyPeriod)) {
        studyPeriod = classNameOrStudyPeriod;
    } else {
        className = classNameOrStudyPeriod;
    }

    const settings = periodSettingsMap();
    const key = periodSettingKeyForClass(className, studyPeriod);
    const range = settings[key];
    if (range?.startDate && range?.endDate) {
        return { from: range.startDate, to: range.endDate };
    }
    const fallback = fallbackYearRange();
    if (studyPeriod === "H1") {
        if (classParallel(className) >= 11) return { from: fallback.yearFrom, to: fallback.h1_11_to };
        return { from: fallback.yearFrom, to: fallback.h1To };
    }
    if (studyPeriod === "H2") {
        if (classParallel(className) >= 11) return { from: fallback.h2_11_from, to: fallback.yearTo };
        return { from: fallback.h2From, to: fallback.yearTo };
    }
    return { from: fallback.yearFrom, to: fallback.yearTo };
}

function referencePlanningDate() {
    const today = new Date().toISOString().slice(0, 10);
    const period = defaultLoadPeriod("1-А", "YEAR");
    return today < period.from ? period.from : today;
}

function currentDisplayDate() {
    if (state.viewMode === "date" && state.viewDate) {
        return state.viewDate;
    }
    return referencePlanningDate();
}

function updateViewModeControls() {
    const dateMode = state.viewMode === "date";
    if (ui.viewDateLabel) {
        ui.viewDateLabel.style.display = dateMode ? "" : "none";
    }
    if (ui.viewDateInput) {
        ui.viewDateInput.disabled = !dateMode;
        if (dateMode && !ui.viewDateInput.value) {
            ui.viewDateInput.value = currentDisplayDate();
            state.viewDate = ui.viewDateInput.value;
        }
    }
}



function currentAuthUser() {
    return window.tarificationAuth || null;
}

function hasCurriculumRowsForBuilding(buildingCode) {
    const normalizedBuilding = canonicalBuildingCode(buildingCode);
    if (!normalizedBuilding) return false;
    const classMap = classBuildingMap();
    return (curriculumRows || []).some((row) => {
        const rowBuilding = canonicalBuildingCode(row.numberSchoolBuilding);
        const classBuilding = canonicalBuildingCode(classMap.get(normalizeClassName(row.className)));
        return rowBuilding === normalizedBuilding || classBuilding === normalizedBuilding;
    });
}

function preferredBuildingCode(availableBuildings) {
    if (!Array.isArray(availableBuildings) || !availableBuildings.length) return "";
    const user = currentAuthUser();
    const byCode = new Map(availableBuildings.map((b) => [normalizeBuildingCode(b.code), b.code]));
    const allOrderedCodes = availableBuildings.map((b) => normalizeBuildingCode(b.code)).filter(Boolean);
    const editableCodes = [];

    if (!user || user.admin || user.loadEditAllBuildings) {
        editableCodes.push(...allOrderedCodes);
    } else {
        editableCodes.push(...(user.loadEditableBuildingCodes || [])
            .map((code) => normalizeBuildingCode(code))
            .filter(Boolean));
        const managedCode = normalizeBuildingCode(user.managedBuildingCode);
        if (managedCode) editableCodes.push(managedCode);
        if (!editableCodes.length) {
            editableCodes.push(...allOrderedCodes);
        }
    }

    const uniqueEditableCodes = [...new Set(editableCodes)];
    for (const code of uniqueEditableCodes) {
        if (byCode.has(code) && hasCurriculumRowsForBuilding(code)) {
            return byCode.get(code);
        }
    }
    for (const code of uniqueEditableCodes) {
        if (byCode.has(code)) {
            return byCode.get(code);
        }
    }
    return availableBuildings[0].code;
}

function canEditSelectedBuildingLoad() {
    const user = currentAuthUser();
    if (!user) return false;
    if (user.admin) return true;
    const loadPermission = window.tarificationTabPermissions?.LOAD;
    if (!loadPermission?.canEdit) return false;
    if (user.loadEditAllBuildings) return true;
    const allowedBuildings = (user.loadEditableBuildingCodes || []).map((code) => normalizeBuildingCode(code));
    if (allowedBuildings.length) {
        return allowedBuildings.includes(normalizeBuildingCode(selectedBuilding));
    }
    if (user.role !== "BUILDING_HEAD") return false;
    return normalizeBuildingCode(user.managedBuildingCode) === normalizeBuildingCode(selectedBuilding);
}

function loadReadOnlyReason() {
    const user = currentAuthUser();
    if (!user || user.admin) return "";
    const loadPermission = window.tarificationTabPermissions?.LOAD;
    if (!loadPermission?.canEdit) {
        return "У вас нет права редактировать вкладку «Нагрузка по корпусам».";
    }
    if (user.loadEditAllBuildings) {
        return "";
    }
    const allowedBuildings = (user.loadEditableBuildingCodes || []).filter(Boolean);
    if (allowedBuildings.length && !allowedBuildings.map((code) => normalizeBuildingCode(code)).includes(normalizeBuildingCode(selectedBuilding))) {
        return `Редактирование разрешено только для корпусов: ${allowedBuildings.join(", ")}.`;
    }
    if (user.role === "BUILDING_HEAD" && normalizeBuildingCode(user.managedBuildingCode) !== normalizeBuildingCode(selectedBuilding)) {
        return `Руководитель корпуса может редактировать только корпус ${user.managedBuildingCode || "—"}.`;
    }
    return "Администратор ещё не назначил вам корпуса для редактирования нагрузки.";
}

function updateLoadEditMode() {
    const pagePermission = window.tarificationTabPermissions?.LOAD;
    if (!pagePermission?.canEdit && !currentAuthUser()?.admin) return;

    const allowed = canEditSelectedBuildingLoad();
    const reason = loadReadOnlyReason();
    document.querySelectorAll('[data-load-edit-area], [data-load-edit-control="true"]').forEach((container) => {
        container.querySelectorAll?.('button, input, select, textarea').forEach((el) => {
            if (el.dataset.allowReadonly === 'true') return;
            el.disabled = !allowed;
        });
        if (container.matches('[data-load-edit-control="true"]')) {
            container.disabled = !allowed;
        }
    });
    if (ui.saveBuildingBtn) {
        ui.saveBuildingBtn.title = allowed ? '' : reason;
    }
}

function dateInRange(isoDate, fromDate, toDate) {
    if (!isoDate || !fromDate || !toDate) return true;
    return fromDate <= isoDate && isoDate <= toDate;
}

function periodOverlaps(aFrom, aTo, bFrom, bTo) {
    if (!aFrom || !aTo || !bFrom || !bTo) return false;
    return aFrom <= bTo && bFrom <= aTo;
}

function manualEntryStudyPeriod(entry) {
    if (entry?.studyPeriod) return entry.studyPeriod;
    const fromDate = String(entry?.loadFromDate || "");
    const toDate = String(entry?.loadToDate || "");
    const parallel = classParallel(entry?.className);
    const ranges = periodSettingsMap();
    if (parallel == null || parallel <= 9) {
        const h1 = ranges.H1_1_9;
        const h2 = ranges.H2_1_9;
        if (h1 && fromDate >= h1.startDate && toDate <= h1.endDate) return "H1";
        if (h2 && fromDate >= h2.startDate && toDate <= h2.endDate) return "H2";
        return "YEAR";
    }
    const prefix = parallel === 10 ? '10' : '11';
    const h1 = ranges[`H1_${prefix}`];
    const h2 = ranges[`H2_${prefix}`];
    if (h2 && fromDate >= h2.startDate) return "H2";
    if (h1 && toDate <= h1.endDate) return "H1";
    return "H1";
}

function subjectConflictKey(row) {
    return [
        normalizeBuildingCode(row.numberSchoolBuilding),
        normalizeClassName(row.className),
        String(row.subjectName || "").trim().toUpperCase(),
        String(row.educationLevel || ""),
        String(manualEntryStudyPeriod(row) || "YEAR"),
        String(row.groupNameEducationalPlan || "").trim().toUpperCase()
    ].join("|");
}

function detectManualLoadConflicts() {
    const rows = (manualRows || [])
        .filter((r) => normalizeBuildingCode(r.numberSchoolBuilding) === selectedBuilding)
        .filter((r) => !r.orphaned);

    const byKey = new Map();
    rows.forEach((r) => {
        const key = subjectConflictKey(r);
        if (!byKey.has(key)) byKey.set(key, []);
        byKey.get(key).push(r);
    });

    const conflicts = new Set();
    byKey.forEach((items) => {
        for (let i = 0; i < items.length; i += 1) {
            for (let j = i + 1; j < items.length; j += 1) {
                const a = items[i];
                const b = items[j];
                const aTeacher = String(a.fioTeacher || "").trim().toLowerCase();
                const bTeacher = String(b.fioTeacher || "").trim().toLowerCase();
                if (!aTeacher || !bTeacher || aTeacher === bTeacher) continue;
                if (periodOverlaps(a.loadFromDate, a.loadToDate, b.loadFromDate, b.loadToDate)) {
                    conflicts.add(a.id);
                    conflicts.add(b.id);
                }
            }
        }
    });

    return conflicts;
}

function prefillFromManualLoad(referenceDate = referencePlanningDate()) {
    state.assignmentsByBuilding = {};
    state.subjectTeacherRowsByBuilding = {};
    state.futurePlansByBuilding = {};

    const allApiRows = expandCurriculumRows(curriculumRows);

    const matchByManual = (entry) => {
        const candidates = allApiRows.filter((row) =>
            normalizeBuildingCode(row.numberSchoolBuilding) === normalizeBuildingCode(entry.numberSchoolBuilding)
            && row.className === entry.className
            && row.subjectName === entry.subjectName
            && row.educationLevel === entry.educationLevel
        );
        if (!candidates.length) return null;
        const effectivePeriod = manualEntryStudyPeriod(entry);
        return candidates.find((row) => rowStudyPeriod(row) === effectivePeriod)
            || candidates.find((row) => rowStudyPeriod(row) === "YEAR")
            || candidates[0];
    };

    const grouped = new Map();
    (manualRows || []).forEach((entry) => {
        const matched = matchByManual(entry);
        if (!matched) return;
        const apiKey = apiKeyOfRow(matched);
        if (!grouped.has(apiKey)) grouped.set(apiKey, { matched, entries: [] });
        grouped.get(apiKey).entries.push(entry);
    });

    grouped.forEach(({ matched, entries }, apiKey) => {
        const buildingCode = normalizeBuildingCode(matched.numberSchoolBuilding);
        const subjectKey = subjectKeyOfRow(matched);
        const assignments = assignmentsForBuilding(buildingCode);
        const teacherRowsMap = teacherRowsForBuilding(buildingCode);
        const plans = futurePlansForBuilding(buildingCode);

        const sorted = [...entries]
            .filter((e) => String(e.fioTeacher || "").trim())
            .sort((a, b) => {
                const aFrom = String(a.loadFromDate || "");
                const bFrom = String(b.loadFromDate || "");
                if (aFrom !== bFrom) return aFrom.localeCompare(bFrom);
                return Number(a.id || 0) - Number(b.id || 0);
            });

        const currentCandidates = sorted.filter((e) => {
            const from = String(e.loadFromDate || "");
            const to = String(e.loadToDate || "");
            return from && to && from <= referenceDate && referenceDate <= to;
        });
        let currentEntry = currentCandidates[currentCandidates.length - 1] || null;
        if (!currentEntry && sorted.length) {
            currentEntry = sorted[0];
        }

        const futureCandidates = sorted
            .filter((e) => String(e.loadFromDate || "") > referenceDate)
            .sort((a, b) => String(a.loadFromDate || "").localeCompare(String(b.loadFromDate || "")));
        const futureEntry = futureCandidates[0] || null;

        if (currentEntry) {
            assignments[apiKey] = String(currentEntry.fioTeacher || "").trim();
        }

        if (futureEntry && currentEntry && String(futureEntry.fioTeacher || "").trim().toLowerCase() !== String(currentEntry.fioTeacher || "").trim().toLowerCase()) {
            plans[apiKey] = {
                targetTeacher: String(futureEntry.fioTeacher || "").trim(),
                previousTeacher: String(currentEntry.fioTeacher || "").trim(),
                fromDate: String(futureEntry.loadFromDate || ""),
                toDate: String(futureEntry.loadToDate || ""),
                subjectKey,
                plannedHours: Number(matched.plannedHours || 0),
                className: matched.className,
                educationLevel: matched.educationLevel,
                subjectName: matched.subjectName
            };
        }

        if (!teacherRowsMap[subjectKey]) {
            teacherRowsMap[subjectKey] = [];
        }

        const byTeacher = new Map();
        sorted.forEach((e) => {
            const teacher = String(e.fioTeacher || "").trim();
            if (!teacher) return;
            const key = teacher.toLowerCase();
            if (!byTeacher.has(key)) byTeacher.set(key, []);
            byTeacher.get(key).push(e);
        });

        byTeacher.forEach((teacherEntries, key) => {
            teacherEntries.sort((a, b) => String(a.loadFromDate || "").localeCompare(String(b.loadFromDate || "")));
            let chosen = teacherEntries.find((e) => {
                const from = String(e.loadFromDate || "");
                const to = String(e.loadToDate || "");
                return from && to && from <= referenceDate && referenceDate <= to;
            });
            if (!chosen) {
                chosen = teacherEntries.find((e) => String(e.loadFromDate || "") > referenceDate) || teacherEntries[teacherEntries.length - 1];
            }
            const teacherName = String(chosen.fioTeacher || "").trim();
            const exists = teacherRowsMap[subjectKey].some((row) => String(row.teacherName || "").trim().toLowerCase() === key);
            if (!exists) {
                const period = defaultPeriodForRows([matched]);
                teacherRowsMap[subjectKey].push({
                    id: rowId(),
                    teacherName,
                    studyPeriod: period.studyPeriod,
                    loadFromDate: chosen.loadFromDate || period.from,
                    loadToDate: chosen.loadToDate || period.to
                });
            }
        });
    });
}

function ensureTeacherRowsForBuilding() {
    const buildingRows = expandedRowsForSelectedBuilding();
    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);

    const bySubject = new Map();
    buildingRows.forEach((row) => {
        const subjectKey = subjectKeyOfRow(row);
        if (!bySubject.has(subjectKey)) bySubject.set(subjectKey, []);
        bySubject.get(subjectKey).push(row);
    });

    bySubject.forEach((rows, subjectKey) => {
        if (!rowsMap[subjectKey]) {
            rowsMap[subjectKey] = [];
        }

        const teachersFromAssignments = new Set();
        rows.forEach((row) => {
            const teacher = String(assignments[apiKeyOfRow(row)] || "").trim();
            if (teacher) teachersFromAssignments.add(teacher);
        });

        teachersFromAssignments.forEach((teacherName) => {
            const exists = rowsMap[subjectKey].some((row) => row.teacherName.toLowerCase() === teacherName.toLowerCase());
            if (!exists) {
                const period = defaultPeriodForRows(rows);
                rowsMap[subjectKey].push({ id: rowId(), teacherName, studyPeriod: period.studyPeriod, loadFromDate: period.from, loadToDate: period.to });
            }
        });

        if (!rowsMap[subjectKey].length) {
            const period = defaultPeriodForRows(rows);
            rowsMap[subjectKey].push({ id: rowId(), teacherName: "", studyPeriod: period.studyPeriod, loadFromDate: period.from, loadToDate: period.to });
        }
    });
}


function rowStableKey(row) {
    return `${row.subjectKey}::${row.teacherRowId}`;
}

function teacherHoursInBuilding(buildingCode, teacherName) {
    const normalizedTeacher = String(teacherName || "").trim();
    if (!normalizedTeacher) return { h1: 0, h2: 0 };

    const normalizedBuilding = canonicalBuildingCode(buildingCode);
    const assignments = assignmentsForBuilding(normalizedBuilding);
    const classMap = classBuildingMap();
    const buildingRows = expandCurriculumRows(curriculumRows.filter((row) => {
        const rowBuilding = canonicalBuildingCode(row.numberSchoolBuilding);
        const byClass = canonicalBuildingCode(classMap.get(normalizeClassName(row.className)));
        return rowBuilding === normalizedBuilding || byClass === normalizedBuilding;
    }));

    const totals = { h1: 0, h2: 0 };
    buildingRows.forEach((row) => {
        const assigned = String(assignments[apiKeyOfRow(row)] || "").trim();
        if (assigned && assigned === normalizedTeacher) {
            accumulateSplit(totals, row);
        }
    });
    return totals;
}

function teacherHoursInComplex(teacherName) {
    const normalizedTeacher = String(teacherName || "").trim();
    if (!normalizedTeacher) return { h1: 0, h2: 0 };
    const totals = { h1: 0, h2: 0 };
    buildings.forEach((building) => {
        const p = teacherHoursInBuilding(building.code, normalizedTeacher);
        totals.h1 += p.h1;
        totals.h2 += p.h2;
    });
    return totals;
}

function computeTeacherHourIndexes() {
    const buildingTeacherHours = {};
    const complexTeacherHours = {};
    const classMap = classBuildingMap();

    expandCurriculumRows(curriculumRows || []).forEach((row) => {
        const fromRow = canonicalBuildingCode(row.numberSchoolBuilding);
        const fromClass = canonicalBuildingCode(classMap.get(normalizeClassName(row.className)));
        const buildingCode = fromRow || fromClass;
        if (!buildingCode) return;
        const assignedTeacher = String(assignmentsForBuilding(buildingCode)[apiKeyOfRow(row)] || "").trim();
        if (!assignedTeacher) return;

        if (!buildingTeacherHours[buildingCode]) {
            buildingTeacherHours[buildingCode] = {};
        }
        if (!buildingTeacherHours[buildingCode][assignedTeacher]) {
            buildingTeacherHours[buildingCode][assignedTeacher] = { h1: 0, h2: 0 };
        }
        if (!complexTeacherHours[assignedTeacher]) {
            complexTeacherHours[assignedTeacher] = { h1: 0, h2: 0 };
        }

        accumulateSplit(buildingTeacherHours[buildingCode][assignedTeacher], row);
        accumulateSplit(complexTeacherHours[assignedTeacher], row);
    });

    return { buildingTeacherHours, complexTeacherHours };
}

function buildPresentationRows() {
    const rows = expandedRowsForSelectedBuilding();
    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const { buildingTeacherHours, complexTeacherHours } = computeTeacherHourIndexes();

    const subjectInfo = new Map();
    rows.forEach((row) => {
        const subjectKey = subjectKeyOfRow(row);
        if (!subjectInfo.has(subjectKey)) {
            subjectInfo.set(subjectKey, {
                subjectKey,
                subjectName: row.subjectName,
                displaySubjectName: row.subjectName,
                curriculumPart: row.curriculumPart,
                educationLevel: row.educationLevel,
                groupIndex: row.__groupIndex,
                rowsByClass: {},
                rowsByClassAll: {}
            });
        }
        const info = subjectInfo.get(subjectKey);
        if (!info.rowsByClass[row.className]) {
            info.rowsByClass[row.className] = row;
        }
        if (!info.rowsByClassAll[row.className]) {
            info.rowsByClassAll[row.className] = [];
        }
        info.rowsByClassAll[row.className].push(row);
    });

    const result = [];
    subjectInfo.forEach((info) => {
        const defaults = defaultPeriodForRows(Object.values(info.rowsByClass));
        const teacherRows = rowsMap[info.subjectKey] || [{ from: defaults.from, to: defaults.to, id: rowId(), teacherName: "", studyPeriod: defaults.studyPeriod }];
        teacherRows.forEach((teacherRow) => {
            let totalHours = 0;
            let classCount = 0;

            Object.values(info.rowsByClassAll).forEach((rowsInClass) => {
                let classMatched = false;
                rowsInClass.forEach((row) => {
                    const assignedTeacher = String(assignments[apiKeyOfRow(row)] || "").trim();
                    if (assignedTeacher && assignedTeacher === String(teacherRow.teacherName || "").trim()) {
                        totalHours += Number(row.plannedHours || 0);
                        classMatched = true;
                    }
                });
                if (classMatched) classCount += 1;
            });

            const subjectRowsFlat = Object.values(info.rowsByClassAll).flat();
            const periodTotals = classPeriodHours(subjectRowsFlat);
            let displayName = info.groupIndex ? `${info.subjectName} ${info.groupIndex}Г` : info.subjectName;
            if (periodTotals.year <= 0 && periodTotals.h1 > 0 && periodTotals.h2 <= 0) displayName = `${displayName} (1П)`;
            else if (periodTotals.year <= 0 && periodTotals.h2 > 0 && periodTotals.h1 <= 0) displayName = `${displayName} (2П)`;

            result.push({
                subjectKey: info.subjectKey,
                teacherRowId: teacherRow.id,
                subjectName: info.subjectName,
                displaySubjectName: displayName,
                curriculumPart: info.curriculumPart,
                educationLevel: info.educationLevel,
                groupIndex: info.groupIndex,
                teacherName: teacherRow.teacherName || "",
                studyPeriod: teacherRow.studyPeriod || defaults.studyPeriod,
                loadFromDate: teacherRow.loadFromDate || defaults.from,
                loadToDate: teacherRow.loadToDate || defaults.to,
                rowsByClass: info.rowsByClass,
                rowsByClassAll: info.rowsByClassAll,
                classCount,
                subjectHours: totalHours,
                buildingHours: formatSplitHours(
                    buildingTeacherHours[selectedBuilding]?.[teacherRow.teacherName || ""] || { h1: 0, h2: 0 }
                ),
                complexHours: formatSplitHours(complexTeacherHours[teacherRow.teacherName || ""] || { h1: 0, h2: 0 })
            });
        });
    });

    return getOrderedRows(result);
}

function filterPresentationRowsByViewMode(rows) {
    if (state.viewMode === "all") {
        return rows;
    }
    const rowsBySubject = new Map();
    rows.forEach((row) => {
        if (!rowsBySubject.has(row.subjectKey)) {
            rowsBySubject.set(row.subjectKey, []);
        }
        rowsBySubject.get(row.subjectKey).push(row);
    });

    const result = [];
    rowsBySubject.forEach((subjectRows) => {
        const visibleRows = subjectRows.filter((row) => {
            const teacherName = String(row.teacherName || "").trim();
            if (!teacherName) return true;

            if (state.viewMode === "date") {
                // В режиме «на дату» оставляем строку педагога видимой, даже если его период
                // уже завершился к выбранной дате: это нужно, чтобы видеть донора при передаче часов.
                return true;
            }
            const targetPeriod = state.viewMode === "h1" ? "H1" : "H2";
            return Object.keys(row.rowsByClassAll || {}).some((className) => {
                const period = defaultLoadPeriod(className, targetPeriod);
                return periodOverlaps(row.loadFromDate, row.loadToDate, period.from, period.to);
            });
        });

        if (visibleRows.length) {
            result.push(...visibleRows);
            return;
        }

        const template = subjectRows[0];
        result.push({
            ...template,
            teacherName: "",
            loadFromDate: state.viewDate,
            loadToDate: state.viewDate,
            subjectHours: 0,
            buildingHours: "0/0",
            complexHours: "0/0",
            classCount: 0
        });
    });

    return result;
}

function rowHasPlannedLoadChange(row, referenceDate) {
    const plans = futurePlansForBuilding(selectedBuilding);
    const rowTeacher = String(row.teacherName || "").trim().toLowerCase();
    if (!rowTeacher) return false;

    return Object.values(row.rowsByClassAll || {}).flat().some((curriculumRow) => {
        const plan = plans[apiKeyOfRow(curriculumRow)];
        if (!plan || !plan.fromDate || plan.fromDate <= referenceDate) return false;
        const target = String(plan.targetTeacher || "").trim().toLowerCase();
        const previous = String(plan.previousTeacher || "").trim().toLowerCase();
        return rowTeacher === target || rowTeacher === previous;
    });
}


function applySorting(presentationRows) {
    const dir = state.sortDirection === "desc" ? -1 : 1;
    const cmp = (a, b) => String(a).localeCompare(String(b), "ru");

    return [...presentationRows].sort((a, b) => {
        let result = 0;
        const classSortMatch = /^classHours:(.+)$/.exec(state.sortField || "");
        if (classSortMatch) {
            const className = classSortMatch[1];
            const aRows = a.rowsByClassAll?.[className] || [];
            const bRows = b.rowsByClassAll?.[className] || [];
            const aVal = aRows.length ? aRows.reduce((sum, row) => sum + Number(row.plannedHours || 0), 0) : -1;
            const bVal = bRows.length ? bRows.reduce((sum, row) => sum + Number(row.plannedHours || 0), 0) : -1;
            result = aVal - bVal;
        } else switch (state.sortField) {
            case "teacher":
                result = cmp(a.teacherName || "", b.teacherName || "");
                break;
            case "level":
                result = cmp(educationLevelLabel(a.educationLevel), educationLevelLabel(b.educationLevel));
                break;
            case "subjectHours":
                result = (a.subjectHours - b.subjectHours);
                break;
            case "buildingHours":
                result = (a.buildingHours - b.buildingHours);
                break;
            case "complexHours":
                result = (a.complexHours - b.complexHours);
                break;
            case "classCount":
                result = (a.classCount - b.classCount);
                break;
            case "subject":
            default:
                result = cmp(a.subjectName, b.subjectName);
                break;
        }

        if (result === 0) {
            result = cmp(a.subjectName, b.subjectName) || cmp(a.teacherName, b.teacherName);
        }

        return result * dir;
    });
}


function getOrderedRows(presentationRows) {
    if (state.forceResort || !state.rowOrderByBuilding[selectedBuilding]) {
        const sorted = applySorting(presentationRows);
        state.rowOrderByBuilding[selectedBuilding] = sorted.map((row, index) => [rowStableKey(row), index]);
        state.rowOrderByBuilding[selectedBuilding] = Object.fromEntries(state.rowOrderByBuilding[selectedBuilding]);
        state.forceResort = false;
        return sorted;
    }

    const orderMap = state.rowOrderByBuilding[selectedBuilding] || {};
    const fallbackStart = Object.keys(orderMap).length + 1;
    return [...presentationRows].sort((a, b) => {
        const aOrder = orderMap[rowStableKey(a)] ?? (fallbackStart + 1);
        const bOrder = orderMap[rowStableKey(b)] ?? (fallbackStart + 1);
        if (aOrder !== bOrder) return aOrder - bOrder;
        return String(a.subjectName).localeCompare(String(b.subjectName), 'ru');
    });
}

function renderBuildingTabs() {
    ui.buildingTabs.innerHTML = "";

    const tabs = [...buildings, { code: ARCHIVE_BUILDING_CODE, name: ARCHIVE_BUILDING_LABEL }];
    tabs.forEach((building) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `parallel-tab ${building.code === selectedBuilding ? "active" : ""}`;
        const tabLabel = building.code === ARCHIVE_BUILDING_CODE
            ? `🗂 ${building.name}`
            : buildingTabLabel(building);
        button.textContent = tabLabel;
        button.title = tabLabel;
        button.addEventListener("click", () => {
            selectedBuilding = building.code;
            rememberSelectedBuilding(selectedBuilding);
            refreshSelectedBuildingData()
                .then(() => {
                    state.forceResort = true;
                    renderBuildingTabs();
                    scheduleRenderTable();
                    updateLoadEditMode();
                })
                .catch((error) => print({ error: error.message }));
        });
        ui.buildingTabs.appendChild(button);
    });
}

async function refreshSelectedBuildingData() {
    const encodedBuilding = selectedBuilding && selectedBuilding !== ARCHIVE_BUILDING_CODE
        ? `?numberSchoolBuilding=${encodeURIComponent(selectedBuilding)}`
        : "";
    const [curriculum, manual] = await Promise.all([
        api(`/api/curriculum${encodedBuilding}`),
        api(`/api/manual-load${encodedBuilding}`)
    ]);
    curriculumRows = curriculum || [];
    manualRows = manual || [];
    sourceRevision += 1;
    invalidateDerivedCache();
    prefillFromManualLoad(currentDisplayDate());
}

function addTeacherRow(subjectKey, afterRowId = null) {
    if (selectedBuilding === ARCHIVE_BUILDING_CODE || !canEditSelectedBuildingLoad()) return;
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    if (!rowsMap[subjectKey]) rowsMap[subjectKey] = [];
    const rows = expandedRowsForSelectedBuilding().filter((row) => subjectKeyOfRow(row) === subjectKey);
    const period = defaultPeriodForRows(rows);
    const newRow = { id: rowId(), teacherName: "", studyPeriod: period.studyPeriod, loadFromDate: period.from, loadToDate: period.to };
    if (!afterRowId) {
        rowsMap[subjectKey].push(newRow);
    } else {
        const idx = rowsMap[subjectKey].findIndex((r) => r.id === afterRowId);
        if (idx === -1) rowsMap[subjectKey].push(newRow);
        else rowsMap[subjectKey].splice(idx + 1, 0, newRow);
    }

    const stable = state.rowOrderByBuilding[selectedBuilding] || {};
    const entries = Object.entries(stable).sort((a,b) => (a[1]||0)-(b[1]||0));
    const afterKey = `${subjectKey}::${afterRowId}`;
    const insertAt = Math.max(entries.findIndex(([k]) => k === afterKey) + 1, 0);
    entries.splice(insertAt, 0, [`${subjectKey}::${newRow.id}`, insertAt]);
    state.rowOrderByBuilding[selectedBuilding] = Object.fromEntries(entries.map(([k], i) => [k, i]));

    markDirty();
    scheduleRenderTable();
}

function setTeacherForRow(subjectKey, teacherRowId, value) {
    if (selectedBuilding === ARCHIVE_BUILDING_CODE || !canEditSelectedBuildingLoad()) return;
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const row = (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId);
    if (!row) return;

    const previousTeacher = String(row.teacherName || "").trim();
    const nextTeacher = String(value || "").trim();
    row.teacherName = nextTeacher;
    markDirty();

    if (!nextTeacher) {
        const assignments = assignmentsForBuilding(selectedBuilding);
        expandedRowsForSelectedBuilding()
            .filter((curriculumRow) => subjectKeyOfRow(curriculumRow) === subjectKey)
            .forEach((curriculumRow) => {
                const apiKey = apiKeyOfRow(curriculumRow);
                const currentTeacher = String(assignments[apiKey] || "").trim();
                if (currentTeacher && currentTeacher === previousTeacher) {
                    assignments[apiKey] = "";
                }
            });
    }
}

function applyTeacherSelection(subjectKey, teacherRowId, inputEl) {
    const raw = String(inputEl?.value || "").trim();
    if (!raw) {
        setTeacherForRow(subjectKey, teacherRowId, "");
        return { ok: true, changedTo: "" };
    }

    const exact = teacherNames.find((name) => name.toLowerCase() === raw.toLowerCase());
    if (!exact) {
        print({ warning: `Педагог «${raw}» не найден в справочнике` });
        if (inputEl) inputEl.value = "";
        setTeacherForRow(subjectKey, teacherRowId, "");
        return { ok: false, changedTo: "" };
    }

    if (inputEl) inputEl.value = exact;
    setTeacherForRow(subjectKey, teacherRowId, exact);
    return { ok: true, changedTo: exact };
}



function findTeacherRowMeta(subjectKey, teacherRowId) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    return (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId) || null;
}

function setPeriodForRow(subjectKey, teacherRowId, fromDate, toDate) {
    if (!canEditSelectedBuildingLoad()) return;
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const row = (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId);
    if (!row) return;
    row.loadFromDate = fromDate;
    row.loadToDate = toDate;
    markDirty();
}

function findManualPeriodForClassTeacher(curriculumRow, teacherName) {
    const teacher = String(teacherName || "").trim().toLowerCase();
    if (!curriculumRow || !teacher) return null;

    const buildingCode = normalizeBuildingCode(selectedBuilding);
    const targetPeriod = rowStudyPeriod(curriculumRow);
    const targetGroup = curriculumRow.__groupIndex ? `ГРУППА ${curriculumRow.__groupIndex}` : "";
    const referenceDate = currentDisplayDate();

    const matched = (manualRows || []).filter((entry) => {
        const entryTeacher = String(entry.fioTeacher || "").trim().toLowerCase();
        if (!entryTeacher || entryTeacher !== teacher) return false;
        if (normalizeBuildingCode(entry.numberSchoolBuilding) !== buildingCode) return false;
        if (normalizeClassName(entry.className) !== normalizeClassName(curriculumRow.className)) return false;
        if (String(entry.subjectName || "").trim() !== String(curriculumRow.subjectName || "").trim()) return false;
        if (String(entry.educationLevel || "") !== String(curriculumRow.educationLevel || "")) return false;
        if (String(manualEntryStudyPeriod(entry) || "YEAR") !== String(targetPeriod || "YEAR")) return false;
        const entryGroup = String(entry.groupNameEducationalPlan || "").trim().toUpperCase();
        return entryGroup === targetGroup;
    });

    if (!matched.length) return null;

    const active = matched.filter((entry) => {
        const from = String(entry.loadFromDate || "");
        const to = String(entry.loadToDate || "");
        return from && to && from <= referenceDate && referenceDate <= to;
    });
    const candidates = (active.length ? active : matched)
        .sort((a, b) => String(a.loadFromDate || "").localeCompare(String(b.loadFromDate || "")));
    const source = candidates[candidates.length - 1];
    if (!source) return null;

    return {
        from: String(source.loadFromDate || ""),
        to: String(source.loadToDate || "")
    };
}

function onClassCellClick(presentationRow, className) {
    if (!canEditSelectedBuildingLoad()) {
        print({ warning: loadReadOnlyReason() || "Редактирование этой нагрузки недоступно" });
        return;
    }
    const curriculumRow = presentationRow.rowsByClass[className];
    if (!curriculumRow) return;

    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowMeta = findTeacherRowMeta(presentationRow.subjectKey, presentationRow.teacherRowId);
    const targetTeacher = String(rowMeta?.teacherName || presentationRow.teacherName || "").trim();
    if (!targetTeacher) {
        print({ warning: "Сначала заполните ФИО педагога в строке" });
        return;
    }

    const syncRows = rowsToSyncForCurriculumRow(curriculumRow);
    const apiKeys = syncRows.map((row) => apiKeyOfRow(row));
    const currentTeacher = String(assignments[apiKeys.find((key) => String(assignments[key] || "").trim())] || "").trim();

    if (!currentTeacher) {
        apiKeys.forEach((key) => { assignments[key] = targetTeacher; });
        markDirty();
        scheduleRenderTable();
        return;
    }

    const period = defaultPeriodForRows([curriculumRow]);
    const classTeacherPeriod = findManualPeriodForClassTeacher(curriculumRow, targetTeacher);
    ui.periodForm.elements.subjectKey.value = presentationRow.subjectKey;
    ui.periodForm.elements.rowId.value = presentationRow.teacherRowId;
    ui.periodForm.elements.className.value = className;
    ui.periodForm.elements.loadFromDate.value = classTeacherPeriod?.from || rowMeta?.loadFromDate || period.from;
    ui.periodForm.elements.loadToDate.value = classTeacherPeriod?.to || rowMeta?.loadToDate || period.to;

    if (currentTeacher !== targetTeacher) {
        state.takeoverContext = {
            apiKeys,
            previousTeacher: currentTeacher,
            targetTeacher,
            subjectKey: presentationRow.subjectKey,
            rowId: presentationRow.teacherRowId,
            className,
            plannedHours: Number(curriculumRow.plannedHours || 0),
            educationLevel: curriculumRow.educationLevel,
            curriculumRow
        };
        print({ warning: `Часы уже назначены педагогу «${currentTeacher}». Укажите период, с которого часы перейдут педагогу «${targetTeacher}».` });
    } else {
        state.takeoverContext = null;
    }

    ui.periodDialog.showModal();
}

function collectLoadIssues(presentationRows, classes) {
    let unassignedHours = 0;
    let errorCount = 0;
    const errors = [];

    presentationRows.forEach((row) => {
        if (row.teacherName && !teacherExists(row.teacherName)) {
            errorCount += 1;
        }
        classes.forEach((className) => {
            const classRows = row.rowsByClassAll?.[className] || [];
            classRows.forEach((curriculumRow) => {
                const assignedTeacher = String(assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(curriculumRow)] || "").trim();
                if (!assignedTeacher) {
                    unassignedHours += Number(curriculumRow.plannedHours || 0);
                    errorCount += 1;
                }
            });
        });
    });

    const conflicts = detectManualLoadConflicts();
    errorCount += conflicts.size;

    (manualRows || []).filter((r) => normalizeBuildingCode(r.numberSchoolBuilding) === selectedBuilding).forEach((r)=>{
        if (r.orphaned) {
            errorCount += 1;
            errors.push(`orphan-${r.id}`);
        }
    });

    ui.unassignedHours.textContent = String(unassignedHours);
    ui.errorCount.textContent = String(errorCount);
    return { errors, errorCount };
}

function jumpToFirstError() {
    const missingTeacher = ui.tableBody.querySelector('.dismissal-note');
    const unassigned = ui.tableBody.querySelector('.hour-pill.unassigned');
    const target = missingTeacher ? missingTeacher.closest('tr') : (unassigned ? unassigned.closest('tr') : null);
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.classList.add('error-row-highlight');
    setTimeout(() => target.classList.remove('error-row-highlight'), 1400);
}

function renderStatsView() {
    if (!ui.statsTable || !ui.statsSummary) return;
    if (!(curriculumRows || []).length) {
        ui.statsSummary.textContent = "Нет строк учебного плана для формирования статистики.";
        ui.statsTable.innerHTML = "<tbody><tr><td>Нет данных.</td></tr></tbody>";
        return;
    }

    const buildingRows = (buildings || []).filter((b) => b.code !== ARCHIVE_BUILDING_CODE);
    const classToBuilding = classBuildingMap();
    const subjectAreaByName = new Map(
        (subjectCatalog || []).map((subject) => [String(subject.subjectName || "").trim().toLowerCase(), String(subject.subjectAreaName || "").trim() || "Без области"])
    );

    const rowsBySubject = new Map();
    const getRow = (subjectName) => {
        const key = String(subjectName || "").trim().toLowerCase();
        if (!rowsBySubject.has(key)) {
            rowsBySubject.set(key, {
                subjectArea: subjectAreaByName.get(key) || "Без области",
                subjectName: String(subjectName || "").trim(),
                totalPlanned: 0,
                totalAssigned: 0,
                perBuilding: Object.fromEntries(buildingRows.map((b) => [b.code, { planned: 0, assigned: 0 }]))
            });
        }
        return rowsBySubject.get(key);
    };

    expandCurriculumRows(curriculumRows || []).forEach((curriculumRow) => {
        const subjectName = String(curriculumRow.subjectName || "").trim();
        if (!subjectName) return;
        const row = getRow(subjectName);
        const planned = Number(curriculumRow.plannedHours || 0);
        const fromClass = canonicalBuildingCode(classToBuilding.get(normalizeClassName(curriculumRow.className)));
        const fromRow = canonicalBuildingCode(curriculumRow.numberSchoolBuilding);
        const buildingCode = fromRow || fromClass;
        const assignmentMap = assignmentsForBuilding(buildingCode);
        const assignedTeacher = String(assignmentMap[apiKeyOfRow(curriculumRow)] || "").trim();
        const assigned = (assignedTeacher && !isVacancyTeacherName(assignedTeacher)) ? planned : 0;

        row.totalPlanned += planned;
        row.totalAssigned += assigned;
        if (row.perBuilding[buildingCode]) {
            row.perBuilding[buildingCode].planned += planned;
            row.perBuilding[buildingCode].assigned += assigned;
        }
    });

    const areaTotals = new Map();
    rowsBySubject.forEach((row) => {
        const area = row.subjectArea || "Без области";
        areaTotals.set(area, (areaTotals.get(area) || 0) + row.totalPlanned);
    });

    const rows = [...rowsBySubject.values()]
        .sort((a, b) => (a.subjectArea || "").localeCompare(b.subjectArea || "", "ru") || a.subjectName.localeCompare(b.subjectName, "ru"));

    const visibleBuildingRows = buildingRows.filter((building) =>
        rows.some((row) => Number(row.perBuilding?.[building.code]?.planned || 0) > 0)
    );

    const totalPlanned = rows.reduce((sum, row) => sum + row.totalPlanned, 0);
    const totalAssigned = rows.reduce((sum, row) => sum + row.totalAssigned, 0);
    ui.statsSummary.textContent = `Предметов: ${rows.length}. Плановых часов: ${totalPlanned}. Распределено: ${totalAssigned}. Нераспределено: ${totalPlanned - totalAssigned}.`;

    const formatStatsBuildingLabel = (building) => {
        const code = String(building?.code || "").trim();
        const name = String(building?.name || "").trim();
        if (!name) return code;
        const normalizedCode = normalizeBuildingCode(code);
        const normalizedName = normalizeBuildingCode(name);
        if (normalizedName === normalizedCode || normalizedName.startsWith(`${normalizedCode}|`)) {
            return name;
        }
        return `${code} — ${name}`;
    };

    const buildingHeader = visibleBuildingRows.map((building) =>
        `<th colspan="3">${esc(formatStatsBuildingLabel(building))}</th>`
    ).join("");
    const buildingSubHeader = visibleBuildingRows.map(() =>
        "<th>часы</th><th>распр.</th><th>не распр.</th>"
    ).join("");

    const thead = `
        <thead>
            <tr>
                <th rowspan="2">Предметная область</th>
                <th rowspan="2">Предмет</th>
                <th rowspan="2">Часы по УП</th>
                <th rowspan="2">Распределено</th>
                <th rowspan="2">Не распределено</th>
                ${buildingHeader}
                <th rowspan="2">Суммарно часов по предметной области</th>
            </tr>
            <tr>${buildingSubHeader}</tr>
        </thead>`;

    const tbody = rows.map((row) => {
        const totalUnassigned = row.totalPlanned - row.totalAssigned;
        const perBuildingCols = visibleBuildingRows.map((building) => {
            const bucket = row.perBuilding[building.code] || { planned: 0, assigned: 0 };
            const buildingUnassigned = bucket.planned - bucket.assigned;
            return `<td>${esc(bucket.planned)}</td><td>${esc(bucket.assigned)}</td><td>${esc(buildingUnassigned)}</td>`;
        }).join("");
        return `
            <tr>
                <td>${esc(row.subjectArea || "Без области")}</td>
                <td>${esc(row.subjectName)}</td>
                <td>${esc(row.totalPlanned)}</td>
                <td>${esc(row.totalAssigned)}</td>
                <td>${esc(totalUnassigned)}</td>
                ${perBuildingCols}
                <td>${esc(areaTotals.get(row.subjectArea || "Без области") || 0)}</td>
            </tr>`;
    }).join("");

    ui.statsTable.innerHTML = `${thead}<tbody>${tbody}</tbody>`;
}

function renderTable() {
    ui.tableHead.innerHTML = "";
    ui.tableBody.innerHTML = "";

    if (!selectedBuilding) {
        ui.tableBody.innerHTML = '<tr><td colspan="7">Добавьте корпуса, чтобы распределять нагрузку.</td></tr>';
        return;
    }

    if (selectedBuilding === ARCHIVE_BUILDING_CODE) {
        renderArchiveAsMainTable();
        return;
    }

    ensureTeacherRowsForBuilding();

    const classes = classesForSelectedBuilding();
    const referenceDate = currentDisplayDate();
    const presentationRows = filterPresentationRowsByViewMode(buildPresentationRows());
    const { errorCount } = collectLoadIssues(presentationRows, classes);

    const headMain = document.createElement("tr");
    headMain.className = "load-main-head";
    headMain.innerHTML = `
        <th rowspan="2">Предмет</th>
        <th rowspan="2">Педагог</th>
        <th rowspan="2">Часов в корпусе</th>
        <th rowspan="2">Всего часов в комплексе</th>
        <th colspan="${Math.max(classes.length, 1)}">
            <div class="load-head-actions">
                <span><strong>Ошибки: ${errorCount}</strong></span>
                <button type="button" class="head-action-btn" data-head-save="1">Сохранить нагрузку корпуса</button>
                <button type="button" class="head-action-btn" data-head-next-error="1">Перейти к ошибке</button>
            </div>
        </th>
    `;
    ui.tableHead.appendChild(headMain);
    const headSaveBtn = headMain.querySelector('[data-head-save="1"]');
    const headNextErrorBtn = headMain.querySelector('[data-head-next-error="1"]');
    headSaveBtn?.addEventListener("click", () => ui.saveBuildingBtn?.click());
    headNextErrorBtn?.addEventListener("click", () => ui.nextErrorBtn?.click());

    const headClasses = document.createElement("tr");
    headClasses.className = "load-class-head";
    headClasses.innerHTML = classes.length
        ? classes.map((className) => `<th><button type="button" class="class-sort-btn ${state.sortField === `classHours:${className}` ? "active" : ""}" data-class-sort="${esc(className)}">${esc(className)}</button></th>`).join("")
        : `<th>—</th>`;
    ui.tableHead.appendChild(headClasses);

    presentationRows.forEach((row, index) => {
        const tr = document.createElement("tr");
        if (rowHasPlannedLoadChange(row, referenceDate)) {
            tr.classList.add("load-change-row");
        }
        const listId = `teacher-list-${row.teacherRowId}`;

        tr.innerHTML = `
            <td>
                <div class="subject-cell">${esc(row.displaySubjectName || row.subjectName)} ${index === 0 || presentationRows[index - 1].subjectKey !== row.subjectKey ? `<button class="inline-plus" type="button" data-plus-subject="${esc(row.subjectKey)}" data-plus-after="${esc(row.teacherRowId)}" title="Добавить строку педагога">+</button>` : ""}</div>
            </td>            <td class="${isDismissedTeacher(row.teacherName) ? "dismissal-row" : ""}">
                <input type="text" class="teacher-input" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" list="${listId}" value="${esc(row.teacherName)}" placeholder="ФИО педагога">
                <datalist id="${listId}"></datalist>
                ${isDismissedTeacher(row.teacherName) ? `<div class="dismissal-note">Увольнение с ${esc(dismissalDateOfTeacher(row.teacherName))}</div>` : ""}${(!teacherExists(row.teacherName) && row.teacherName) ? `<div class="dismissal-note">Ошибка: педагог отсутствует в справочнике</div>` : ""}
            </td>
            <td><strong>${esc(row.buildingHours)} ч</strong></td>
            <td><strong>${esc(row.complexHours || 0)} ч</strong></td>
            ${classes.map((className) => {
                const curriculumRow = row.rowsByClass[className];
                if (!curriculumRow) return "<td></td>";
                const classRows = row.rowsByClassAll?.[className] || [curriculumRow];
                const hoursTotal = classPeriodText(classRows);
                const assignedTeachers = classRows.map((item) => String(assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(item)] || "").trim()).filter(Boolean);
                const rowTeacher = String(row.teacherName || "").trim();
                const hasAnyAssigned = assignedTeachers.length > 0;
                const hasRowTeacherAssigned = rowTeacher ? assignedTeachers.includes(rowTeacher) : false;
                const plans = classRows.map((item) => futurePlansForBuilding(selectedBuilding)[apiKeyOfRow(item)]).filter(Boolean);
                const isPlanned = plans.some((plan) => plan.targetTeacher === rowTeacher && plan.fromDate > referenceDate);
                const isTransferOut = plans.some((plan) => plan.previousTeacher === rowTeacher && plan.fromDate > referenceDate);
                const isActive = hasRowTeacherAssigned;
                const isMuted = rowTeacher !== "" && !hasRowTeacherAssigned && !isPlanned && !isTransferOut;
                const isUnassigned = !hasAnyAssigned && !isPlanned;
                const persistedContinuityStates = classRows
                    .map((item) => String(item?.continuityStatus || "").trim().toUpperCase())
                    .filter(Boolean);
                const hasPersistedContinuityOk = persistedContinuityStates.includes("OK");
                const hasPersistedContinuityBroken = persistedContinuityStates.includes("BROKEN");
                const hasContinuityExpectation = classRows.some((item) => state.continuityExpectedByKey.has(
                    continuityKey(item.className, item.subjectName, continuityGroupName(item))
                ));
                const expectationMatchesActiveTeacher = hasContinuityExpectation && classRows.some((item) => {
                    const expectedTeacher = state.continuityExpectedByKey.get(
                        continuityKey(item.className, item.subjectName, continuityGroupName(item))
                    );
                    return Boolean(expectedTeacher) && expectedTeacher === rowTeacher.toLowerCase();
                });
                const hasContinuityOk = isActive && (hasPersistedContinuityOk || expectationMatchesActiveTeacher);
                const hasContinuityBroken = isActive && (hasPersistedContinuityBroken || (!hasPersistedContinuityOk && hasContinuityExpectation && !expectationMatchesActiveTeacher));
                const classesForCell = [
                    "hour-pill",
                    isActive ? "active" : "",
                    isMuted ? "muted" : "",
                    isUnassigned ? "unassigned" : "",
                    isPlanned ? "planned" : "",
                    isTransferOut ? "transfer-out" : "",
                    !isPlanned && !isTransferOut && hasContinuityOk ? "continuity-ok" : "",
                    !isPlanned && !isTransferOut && hasContinuityBroken ? "continuity-broken" : ""
                ].filter(Boolean).join(" ");
                return `<td><button type="button" class="${classesForCell}" data-class-cell="1" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" data-class-name="${esc(className)}">${esc(hoursTotal)}</button></td>`;
            }).join("")}
        `;

        ui.tableBody.appendChild(tr);

        const teacherInput = tr.querySelector(".teacher-input");
        const listEl = tr.querySelector("datalist");
        updateDatalistOptions(listEl, teacherInput.value || "");

        teacherInput.addEventListener("input", () => {
            updateDatalistOptions(listEl, teacherInput.value || "");
        });

        teacherInput.addEventListener("blur", () => {
            applyTeacherSelection(row.subjectKey, row.teacherRowId, teacherInput);
        });

        teacherInput.addEventListener("change", () => {
            applyTeacherSelection(row.subjectKey, row.teacherRowId, teacherInput);
        });

        teacherInput.addEventListener("keydown", (event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            applyTeacherSelection(row.subjectKey, row.teacherRowId, teacherInput);
        });
    });


    ui.tableHead.querySelectorAll("button[data-class-sort]").forEach((button) => {
        button.addEventListener("click", () => {
            const className = button.dataset.classSort;
            const next = `classHours:${className}`;
            if (state.sortField === next) {
                state.sortDirection = state.sortDirection === "asc" ? "desc" : "asc";
                ui.sortDirection.value = state.sortDirection;
            } else {
                state.sortField = next;
                state.sortDirection = "desc";
                ui.sortDirection.value = "desc";
                ui.sortField.value = "subject";
            }
            state.forceResort = true;
            scheduleRenderTable();
        });
    });


    ui.tableBody.querySelectorAll(".period-input").forEach((input) => {
        input.addEventListener("change", () => {
            const subjectKey = input.dataset.subjectKey;
            const rowIdValue = input.dataset.rowId;
            const rowElFrom = ui.tableBody.querySelector(`.period-from[data-subject-key="${subjectKey}"][data-row-id="${rowIdValue}"]`);
            const rowElTo = ui.tableBody.querySelector(`.period-to[data-subject-key="${subjectKey}"][data-row-id="${rowIdValue}"]`);
            const fromDate = rowElFrom?.value || "";
            const toDate = rowElTo?.value || "";
            setPeriodForRow(subjectKey, rowIdValue, fromDate, toDate);
        });
    });

    ui.tableBody.querySelectorAll("button[data-plus-subject]").forEach((button) => {
        button.addEventListener("click", () => addTeacherRow(button.dataset.plusSubject, button.dataset.plusAfter));
    });

    ui.tableBody.querySelectorAll("button[data-class-cell]").forEach((button) => {
        button.addEventListener("click", () => {
            const subjectKey = button.dataset.subjectKey;
            const rowIdValue = button.dataset.rowId;
            const className = button.dataset.className;
            const row = presentationRows.find((entry) => entry.subjectKey === subjectKey && entry.teacherRowId === rowIdValue);
            if (!row) return;
            onClassCellClick(row, className);
        });
    });

    updateLoadEditMode();
}


function renderArchiveAsMainTable() {
    const today = new Date().toISOString().slice(0, 10);
    const conflicts = detectManualLoadConflicts();
    const archiveRows = (manualRows || [])
        .filter((r) => r.loadToDate && r.loadToDate < today)
        .sort((a, b) => String(b.loadToDate).localeCompare(String(a.loadToDate)));

    const head = document.createElement("tr");
    head.innerHTML = `
        <th>Корпус</th>
        <th>Педагог</th>
        <th>Предмет</th>
        <th>Класс</th>
        <th>Часы</th>
        <th>Уровень</th>
        <th>С</th>
        <th>По</th>
        <th>Статус</th>
    `;
    ui.tableHead.appendChild(head);

    if (!archiveRows.length) {
        ui.tableBody.innerHTML = '<tr><td colspan="9">Архивных записей пока нет.</td></tr>';
        ui.unassignedHours.textContent = "0";
        ui.errorCount.textContent = "0";
        updateLoadEditMode();
        return;
    }

    ui.tableBody.innerHTML = archiveRows.map((r) => `
        <tr class="${conflicts.has(r.id) ? "conflict-row" : ""}">
            <td>${esc(normalizeBuildingCode(r.numberSchoolBuilding))}</td>
            <td>${esc(r.fioTeacher)}</td>
            <td>${esc(r.subjectName)}</td>
            <td>${esc(r.className)}</td>
            <td>${esc(r.load)} ч</td>
            <td>${esc(r.educationLevel || "")}</td>
            <td>${esc(r.loadFromDate || "")}</td>
            <td>${esc(r.loadToDate || "")}</td>
            <td>${conflicts.has(r.id) ? "Конфликт периода" : ""}</td>
        </tr>
    `).join("");

    ui.unassignedHours.textContent = "0";
    ui.errorCount.textContent = String(conflicts.size);
    updateLoadEditMode();
}

async function saveBuildingLoad() {
    if (!canEditSelectedBuildingLoad()) {
        print({ warning: loadReadOnlyReason() || "Редактирование этой нагрузки недоступно" });
        return;
    }
    if (selectedBuilding === ARCHIVE_BUILDING_CODE) {
        print({ warning: "Архив не редактируется" });
        return;
    }

    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const plans = futurePlansForBuilding(selectedBuilding);
    const payload = expandedRowsForSelectedBuilding().map((row) => {
        const apiKey = apiKeyOfRow(row);
        const fioTeacher = String(assignments[apiKey] || "").trim();
        if (!fioTeacher) return null;

        const teacherRow = (rowsMap[subjectKeyOfRow(row)] || []).find((r) => String(r.teacherName || "").trim() === fioTeacher);
        const period = defaultLoadPeriod(row.className, rowStudyPeriod(row));
        const manualPeriod = findManualPeriodForClassTeacher(row, fioTeacher);
        const rowLoadFromDate = manualPeriod?.from || teacherRow?.loadFromDate || period.from;
        let rowLoadToDate = manualPeriod?.to || teacherRow?.loadToDate || period.to;
        const plan = plans[apiKey];
        if (plan && plan.previousTeacher === fioTeacher) {
            const cut = dayBefore(plan.fromDate);
            rowLoadToDate = cut < rowLoadFromDate ? rowLoadFromDate : cut;
        }

        return {
            fioTeacher,
            numberSchoolBuilding: selectedBuilding,
            subjectName: row.subjectName,
            className: row.className,
            load: Number(row.plannedHours || 0),
            groupNameEducationalPlan: row.__groupIndex ? `Группа ${row.__groupIndex}` : null,
            groupLoad: row.__groupIndex ? Number(row.plannedHours || 0) : null,
            educationLevel: row.educationLevel,
            studyPeriod: rowStudyPeriod(row),
            loadFromDate: rowLoadFromDate,
            loadToDate: rowLoadToDate
        };
    }).filter(Boolean);

    Object.entries(plans).forEach(([apiKey, plan]) => {
        const row = expandedRowsForSelectedBuilding().find((r) => apiKeyOfRow(r) === apiKey);
        if (!row) return;
        payload.push({
            fioTeacher: plan.targetTeacher,
            numberSchoolBuilding: selectedBuilding,
            subjectName: row.subjectName,
            className: row.className,
            load: Number(row.plannedHours || 0),
            groupNameEducationalPlan: row.__groupIndex ? `Группа ${row.__groupIndex}` : null,
            groupLoad: row.__groupIndex ? Number(row.plannedHours || 0) : null,
            educationLevel: row.educationLevel,
            studyPeriod: rowStudyPeriod(row),
            loadFromDate: plan.fromDate,
            loadToDate: plan.toDate
        });
    });

    const dedupedPayload = new Map();
    payload.forEach((item) => {
        const key = [
            normalizeBuildingCode(item.numberSchoolBuilding),
            normalizeClassName(item.className),
            String(item.subjectName || "").trim().toUpperCase(),
            String(item.educationLevel || ""),
            String(item.studyPeriod || "YEAR"),
            String(item.groupNameEducationalPlan || "").trim().toUpperCase(),
            String(item.fioTeacher || "").trim().toUpperCase(),
            String(item.loadFromDate || ""),
            String(item.loadToDate || "")
        ].join("|");
        dedupedPayload.set(key, item);
    });
    const finalPayload = [...dedupedPayload.values()];

    if (!finalPayload.length) {
        print({ warning: "Нет назначений для сохранения" });
        return;
    }

    try {
        const result = await api("/api/manual-load/bulk", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(finalPayload)
        });
        print({ saved: result.length, uniqueRequested: finalPayload.length, building: selectedBuilding });
        state.futurePlansByBuilding[selectedBuilding] = {};
        markDirty(false);
    } catch (error) {
        print({ error: error.message });
    }
}

async function exportLoadWorkbook() {
    try {
        const scopedPath = window.withAcademicYear ? window.withAcademicYear("/api/manual-load/export") : "/api/manual-load/export";
        const response = await fetch(scopedPath);
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        const disposition = response.headers.get("Content-Disposition") || "";
        const match = disposition.match(/filename\\*=UTF-8''([^;]+)/);
        a.href = url;
        a.download = match ? decodeURIComponent(match[1]) : "load-export.xlsx";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        print({ status: "exported" });
    } catch (error) {
        print({ error: error.message });
    }
}

function exportLoadStatsCsv() {
    if (!ui.statsTable) return;
    const rows = Array.from(ui.statsTable.querySelectorAll("tr"));
    if (!rows.length) {
        print({ warning: "Нет данных для экспорта статистики" });
        return;
    }
    const csvRows = rows.map((row) => {
        const cells = Array.from(row.querySelectorAll("th,td"));
        return cells.map((cell) => `"${String(cell.textContent || "").replaceAll('"', '""').trim()}"`).join(";");
    });
    const blob = new Blob(["\uFEFF" + csvRows.join("\n")], { type: "text/csv;charset=utf-8;" });
    const datePart = currentDisplayDate().replaceAll("-", "");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `load-stats-${datePart}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
}

async function importLoadWorkbook(file) {
    try {
        const form = new FormData();
        form.append("file", file);
        const scopedPath = window.withAcademicYear ? window.withAcademicYear("/api/manual-load/import") : "/api/manual-load/import";
        const response = await fetch(scopedPath, { method: "POST", body: form });
        const text = await response.text();
        let body = null;
        try { body = text ? JSON.parse(text) : null; } catch { body = { message: text }; }
        if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
        print({ status: "imported", rows: Array.isArray(body) ? body.length : 0 });
        await refreshSourceData();
    } catch (error) {
        print({ error: error.message });
        alert(`Ошибка импорта нагрузки: ${error.message}`);
    }
}

async function refreshSourceData() {
    const [teachers, buildingRows, classRows, periodSettings, yearResolve] = await Promise.all([
        api("/api/teachers"),
        api("/api/buildings"),
        api("/api/classroom-leadership"),
        api("/api/settings/study-periods"),
        api("/api/academic-years/active")
    ]);

    const buildingByCode = new Map();
    (buildingRows || []).forEach((b) => {
        const code = normalizeBuildingCode(b.code);
        if (!code) return;
        buildingByCode.set(code, {
            ...b,
            code,
            name: String(b.name || "").trim() || code
        });
    });
    (classRows || []).forEach((r) => {
        const code = normalizeBuildingCode(r.numberSchoolBuilding);
        if (!code || buildingByCode.has(code)) return;
        buildingByCode.set(code, { code, name: code, address: "(из классов)" });
    });
    buildings = [...buildingByCode.values()].sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));

    const rememberedBuilding = restoreSelectedBuilding();
    if (rememberedBuilding && buildings.some((row) => normalizeBuildingCode(row.code) === rememberedBuilding)) {
        selectedBuilding = rememberedBuilding;
    }
    if (!selectedBuilding || !buildings.some((row) => row.code === selectedBuilding)) {
        selectedBuilding = preferredBuildingCode(buildings);
    }
    if (selectedBuilding !== ARCHIVE_BUILDING_CODE && !canEditSelectedBuildingLoad()) {
        const preferred = preferredBuildingCode(buildings);
        if (preferred) selectedBuilding = preferred;
    }
    rememberSelectedBuilding(selectedBuilding);

    const encodedBuilding = selectedBuilding && selectedBuilding !== ARCHIVE_BUILDING_CODE
            ? `?numberSchoolBuilding=${encodeURIComponent(selectedBuilding)}`
            : "";
    const [curriculum, manual] = await Promise.all([
        api(`/api/curriculum${encodedBuilding}`),
        api(`/api/manual-load${encodedBuilding}`)
    ]);

    curriculumRows = curriculum || [];
    manualRows = manual || [];
    teacherDirectory = teachers || [];
    teacherNames = sortRu(Array.from(new Set(teacherDirectory.map((t) => String(t.fioTeacher || "").trim()).filter(Boolean))));
    teacherDirectoryByName = new Map(
        teacherDirectory
            .map((teacher) => [String(teacher.fioTeacher || "").trim().toLowerCase(), teacher])
            .filter(([name]) => Boolean(name))
    );
    classroomRows = classRows || [];
    studyPeriodSettings = periodSettings || [];
    subjectCatalog = [];
    sourceRevision += 1;
    invalidateDerivedCache();
    state.continuityExpectedByKey = new Map();

    try {
        const requestedAcademicYear = String(sessionStorage.getItem("tarification.academicYear") || "").trim();
        const activeAcademicYear = requestedAcademicYear || String(yearResolve?.active || "").trim();
        const sourceAcademicYear = previousAcademicYearCode(activeAcademicYear);
        if (sourceAcademicYear) {
            const sourceManualRows = await apiUnscoped(`/api/manual-load?academicYear=${encodeURIComponent(sourceAcademicYear)}`);
            state.continuityExpectedByKey = computeContinuityExpectedByKey(sourceManualRows || [], curriculumRows || []);
        }
    } catch (error) {
        print({ warning: `Не удалось вычислить подсветку преемственности: ${error.message}` });
    }

    prefillFromManualLoad(currentDisplayDate());
    state.forceResort = true;
    markDirty(false);
    updateViewModeControls();

    state.takeoverContext = null;
    renderBuildingTabs();
    scheduleRenderTable();
}

function bindEvents() {
    ui.tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            const tabName = tab.dataset.loadTab;
            showLoadTab(tabName);
            window.location.hash = tabName === "stats" ? "#stats" : "";
        });
    });
    ui.saveBuildingBtn.addEventListener("click", saveBuildingLoad);
    ui.periodForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const subjectKey = ui.periodForm.elements.subjectKey.value;
        const rowId = ui.periodForm.elements.rowId.value;
        const fromDate = ui.periodForm.elements.loadFromDate.value;
        const toDate = ui.periodForm.elements.loadToDate.value;
        if (fromDate > toDate) {
            print({ error: "Период задан некорректно" });
            return;
        }

        const takeover = state.takeoverContext;
        if (takeover) {
            const assignments = assignmentsForBuilding(selectedBuilding);
            const plans = futurePlansForBuilding(selectedBuilding);
            const referenceDate = currentDisplayDate();

            setPeriodForRow(subjectKey, rowId, fromDate, toDate);

            if (fromDate > referenceDate) {
                takeover.apiKeys.forEach((apiKey) => {
                    plans[apiKey] = {
                        targetTeacher: takeover.targetTeacher,
                        previousTeacher: takeover.previousTeacher,
                        fromDate,
                        toDate,
                        subjectKey: takeover.subjectKey,
                        plannedHours: takeover.plannedHours,
                        className: takeover.className,
                        educationLevel: takeover.educationLevel,
                        subjectName: takeover.curriculumRow.subjectName
                    };
                });
            } else {
                takeover.apiKeys.forEach((apiKey) => {
                    assignments[apiKey] = takeover.targetTeacher;
                    delete plans[apiKey];
                });
            }

            state.takeoverContext = null;
            markDirty();
            ui.periodDialog.close();
            scheduleRenderTable();
            return;
        }

        setPeriodForRow(subjectKey, rowId, fromDate, toDate);
        ui.periodDialog.close();
        scheduleRenderTable();
    });

    ui.removeLoadBtn.addEventListener("click", () => {
        const subjectKey = ui.periodForm.elements.subjectKey.value;
        const rowId = ui.periodForm.elements.rowId.value;
        const className = ui.periodForm.elements.className.value;
        const rowsMap = teacherRowsForBuilding(selectedBuilding);
        const rowMeta = (rowsMap[subjectKey] || []).find((r) => r.id === rowId);
        const row = buildPresentationRows().find((r) => r.subjectKey === subjectKey && r.teacherRowId === rowId);
        const curriculumRow = row?.rowsByClass?.[className];
        if (rowMeta && curriculumRow) {
            const assignments = assignmentsForBuilding(selectedBuilding);
            rowsToSyncForCurriculumRow(curriculumRow).forEach((rowToClear) => {
                assignments[apiKeyOfRow(rowToClear)] = "";
            });
            markDirty();
        }
        ui.periodDialog.close();
        scheduleRenderTable();
    });

    ui.cancelLoadBtn.addEventListener("click", () => { state.takeoverContext = null; ui.periodDialog.close(); });


    ui.refreshLoadBtn.addEventListener("click", () => {
        refreshSourceData()
            .then(() => print({ status: "Синхронизировано с учебным планом" }))
            .catch((error) => print({ error: error.message }));
    });

    ui.exportLoadBtn?.addEventListener("click", exportLoadWorkbook);
    ui.importLoadBtn?.addEventListener("click", () => ui.importLoadFile?.click());
    ui.importLoadFile?.addEventListener("change", async () => {
        const file = ui.importLoadFile.files?.[0];
        if (!file) return;
        const confirmed = confirm("Импорт нагрузки заменит текущие назначения по корпусам из файла. Продолжить?");
        if (!confirmed) {
            ui.importLoadFile.value = "";
            return;
        }
        await importLoadWorkbook(file);
        ui.importLoadFile.value = "";
    });

    ui.sortField.addEventListener("change", () => {
        state.sortField = ui.sortField.value;
        state.forceResort = true;
        scheduleRenderTable();
    });

    ui.sortDirection.addEventListener("change", () => {
        state.sortDirection = ui.sortDirection.value;
        state.forceResort = true;
        scheduleRenderTable();
    });

    ui.viewMode?.addEventListener("change", () => {
        state.viewMode = ui.viewMode.value || "all";
        if (state.viewMode === "date" && !state.viewDate) {
            state.viewDate = referencePlanningDate();
            ui.viewDateInput.value = state.viewDate;
        }
        updateViewModeControls();
        prefillFromManualLoad(currentDisplayDate());
        state.forceResort = true;
        scheduleRenderTable();
    });

    ui.viewDateInput?.addEventListener("change", () => {
        state.viewDate = ui.viewDateInput.value || "";
        if (state.viewMode !== "date") return;
        prefillFromManualLoad(currentDisplayDate());
        state.forceResort = true;
        scheduleRenderTable();
    });

    ui.nextErrorBtn.addEventListener("click", jumpToFirstError);
}

async function init() {
    await waitForAuthContext();
    bindEvents();
    const defaultTab = applyLoadTabAccess();
    if (!defaultTab) return;
    showLoadTab("distribution");
    state.viewDate = referencePlanningDate();
    if (ui.viewDateInput) {
        ui.viewDateInput.value = state.viewDate;
    }
    updateViewModeControls();

    try {
        await refreshSourceData();
        // Автообновление отключено: исключаем скачки интерфейса во время ручного распределения нагрузки.
    } catch (error) {
        print({ error: error.message });
    }
}

init();
