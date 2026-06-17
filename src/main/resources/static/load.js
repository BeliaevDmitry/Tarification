const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    tabs: Array.from(document.querySelectorAll("[data-load-tab]")),
    panes: Array.from(document.querySelectorAll("[data-load-pane]")),
    buildingSelect: document.getElementById("building-select"),
    refreshLoadBtn: document.getElementById("refresh-load-btn"),
    exportLoadBtn: document.getElementById("export-load-btn"),
    exportFullLoadBtn: document.getElementById("export-full-load-btn"),
    importLoadBtn: document.getElementById("import-load-btn"),
    importLoadFile: document.getElementById("import-load-file"),
    saveBuildingBtn: document.getElementById("save-building-btn"),
    clearBuildingLoadBtn: document.getElementById("clear-building-load-btn"),
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
    exportStatsBtn: document.getElementById("export-load-stats-btn"),
    subgroupDrawer: document.getElementById("subgroup-drawer"),
    subgroupDrawerBackdrop: document.getElementById("subgroup-drawer-backdrop"),
    subgroupDrawerTitle: document.getElementById("subgroup-drawer-title"),
    subgroupDrawerBody: document.getElementById("subgroup-drawer-body"),
    subgroupDrawerClose: document.getElementById("subgroup-drawer-close"),
    subgroupDrawerApply: document.getElementById("subgroup-drawer-apply")
};

let curriculumRows = [];
let manualRows = [];
let complexManualRows = [];
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
let latestPresentationRows = [];
const LOAD_SELECTED_BUILDING_KEY = "tarification.load.selectedBuilding";

const ARCHIVE_BUILDING_CODE = "__ARCHIVE__";
const ARCHIVE_BUILDING_LABEL = "Архив нагрузки";

const state = {
    assignmentsByBuilding: {},
    subjectTeacherRowsByBuilding: {},
    rowOrderByBuilding: {},
    sortField: "subjectArea",
    sortDirection: "asc",
    viewMode: "all",
    viewDate: "",
    forceResort: true,
    hasUnsavedChanges: false,
    classSort: "",
    futurePlansByBuilding: {},
    takeoverContext: null,
    subgroupDrawerContext: null
};

const derivedCache = {
    classBuildingMapRowsRef: null,
    classBuildingMapValue: new Map(),
    classAddressMapRowsRef: null,
    classAddressMapValue: { byId: new Map(), byGroupAndClass: new Map(), byClassOnly: new Map(), idByGroupAndClass: new Map(), idByClassOnly: new Map(), schoolBuildingIdByClassId: new Map(), schoolBuildingIdByGroupAndClass: new Map(), schoolBuildingIdByClassOnly: new Map() },
    rowsByBuildingKey: "",
    rowsByBuildingValue: [],
    expandedRowsByBuildingKey: "",
    expandedRowsByBuildingValue: []
};

const buildingDataCache = new Map();
const BUILDING_DATA_CACHE_TTL_MS = 2 * 60 * 1000;
let teacherHourIndexesCacheKey = "";
let teacherHourIndexesCacheValue = { buildingTeacherHours: {}, complexTeacherHours: {} };
let currentErrorList = [];
let currentErrorIndex = -1;

function currentAcademicYearKey() {
    return String(sessionStorage.getItem("tarification.academicYear") || "").trim() || "active";
}

function buildingCacheKey(buildingCode) {
    return `${currentAcademicYearKey()}|${normalizeBuildingAccessCode(buildingCode) || "ALL"}`;
}

function invalidateBuildingDataCache(buildingCode = null) {
    if (!buildingCode) {
        buildingDataCache.clear();
        return;
    }
    buildingDataCache.delete(buildingCacheKey(buildingCode));
}

function invalidateTeacherHourIndexesCache() {
    teacherHourIndexesCacheKey = "";
    teacherHourIndexesCacheValue = { buildingTeacherHours: {}, complexTeacherHours: {} };
}

function invalidateDerivedCache() {
    derivedCache.classBuildingMapRowsRef = null;
    derivedCache.classBuildingMapValue = new Map();
    derivedCache.classAddressMapRowsRef = null;
    derivedCache.classAddressMapValue = { byId: new Map(), byGroupAndClass: new Map(), byClassOnly: new Map(), idByGroupAndClass: new Map(), idByClassOnly: new Map() };
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
    const normalized = String(value || "")
        .trim()
        .toUpperCase()
        .replace(/[–—]/g, "-")
        .replace(/[CС][ПPР]/g, "СП")
        .replace(/\s*\|\s*/g, "|")
        .replace(/\s+/g, "");
    const separatorIndex = normalized.indexOf("|");
    return separatorIndex >= 0 ? normalized.slice(0, separatorIndex) : normalized;
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

function normalizeBuildingAccessCode(value) {
    return String(value || "")
        .trim()
        .toUpperCase()
        .replace(/[–—]/g, "-")
        .replace(/[CС][ПPР]/g, "СП")
        .replace(/\s*\|\s*/g, "|")
        .replace(/\s+/g, "");
}

function buildingGroupCode(value) {
    const normalized = normalizeBuildingAccessCode(value);
    const siteSeparatorIndex = normalized.indexOf("::");
    if (siteSeparatorIndex >= 0) return normalized.slice(0, siteSeparatorIndex);
    const separatorIndex = normalized.indexOf("|");
    return separatorIndex >= 0 ? normalized.slice(0, separatorIndex) : normalized;
}

function buildingAddressToken(value) {
    const normalized = normalizeBuildingAccessCode(value);
    if (normalized.includes("::")) return "";
    const separatorIndex = normalized.indexOf("|");
    return separatorIndex >= 0 ? normalized.slice(separatorIndex + 1) : "";
}

function buildingSiteIdToken(value) {
    const normalized = normalizeBuildingAccessCode(value);
    const separatorIndex = normalized.indexOf("::");
    if (separatorIndex < 0) return null;
    const token = Number(normalized.slice(separatorIndex + 2));
    return Number.isFinite(token) ? token : null;
}

function isAddressScopedBuilding(value) {
    return buildingSiteIdToken(value) != null || Boolean(buildingAddressToken(value));
}

function rowAddressToken(row) {
    const classAddress = String(row?.campusAddress || "").trim();
    if (classAddress) return normalizeBuildingAccessCode(classAddress);

    const maps = classAddressMap();
    if (row?.classId != null) {
        const byId = maps.byId.get(String(row.classId));
        if (byId) return byId;
    }

    const className = normalizeClassName(row?.className);
    const groupCode = buildingGroupCode(row?.numberSchoolBuilding || selectedBuilding);
    if (className) {
        const scopedAddress = maps.byGroupAndClass.get(`${groupCode}|${className}`);
        if (scopedAddress) return scopedAddress;
        const byClassOnly = maps.byClassOnly.get(className);
        if (byClassOnly) return byClassOnly;
    }

    return "";
}

function buildingOptionForAccess(accessCode) {
    const normalized = normalizeBuildingAccessCode(accessCode);
    return (buildings || []).find((building) => normalizeBuildingAccessCode(buildingOptionValue(building)) === normalized) || null;
}

function schoolBuildingIdForAccess(accessCode) {
    const siteId = buildingSiteIdToken(accessCode);
    if (siteId != null) return siteId;
    const option = buildingOptionForAccess(accessCode);
    const id = option?.schoolBuildingId ?? option?.id ?? null;
    return id === null || id === undefined || id === "" ? null : Number(id);
}

function schoolBuildingIdForRow(row) {
    if (row?.schoolBuildingId !== null && row?.schoolBuildingId !== undefined) return Number(row.schoolBuildingId);
    const maps = classAddressMap();
    if (row?.classId !== null && row?.classId !== undefined) {
        const byId = maps.schoolBuildingIdByClassId.get(String(row.classId));
        if (byId !== null && byId !== undefined) return Number(byId);
    }
    const className = normalizeClassName(row?.className);
    const groupCode = buildingGroupCode(row?.numberSchoolBuilding || selectedBuilding);
    if (className) {
        const scoped = maps.schoolBuildingIdByGroupAndClass.get(`${groupCode}|${className}`);
        if (scoped !== null && scoped !== undefined) return Number(scoped);
        const byClassOnly = maps.schoolBuildingIdByClassOnly.get(className);
        if (byClassOnly !== null && byClassOnly !== undefined) return Number(byClassOnly);
    }
    return null;
}

function campusAddressForAccess(accessCode) {
    const siteId = buildingSiteIdToken(accessCode);
    if (siteId != null) {
        return String(buildingOptionForAccess(accessCode)?.address || "").trim();
    }
    const addressToken = buildingAddressToken(accessCode);
    if (!addressToken) return "";
    const optionAddress = String(buildingOptionForAccess(accessCode)?.address || "").trim();
    if (optionAddress) return optionAddress;
    const groupCode = buildingGroupCode(accessCode);
    const classroomAddress = (classroomRows || [])
        .filter((row) => buildingGroupCode(row?.numberSchoolBuilding) === groupCode)
        .map((row) => String(row?.campusAddress || "").trim())
        .find((address) => normalizeBuildingAccessCode(address) === addressToken);
    return classroomAddress || "";
}

function shouldScopeByCampusAddress(accessCode) {
    const addressToken = buildingAddressToken(accessCode);
    if (!addressToken) return false;
    const knownAddresses = addressesForBuildingCode(buildingGroupCode(accessCode))
        .map(normalizeBuildingAccessCode)
        .filter(Boolean);
    return knownAddresses.length !== 1;
}

function scopedBuildingQuery(accessCode, includeAddress = false) {
    if (!accessCode || accessCode === ARCHIVE_BUILDING_CODE) return "";
    const groupCode = buildingGroupCode(accessCode);
    if (!groupCode) return "";
    const params = new URLSearchParams();
    const addressScoped = isAddressScopedBuilding(accessCode);
    if (!includeAddress && addressScoped) {
        return "";
    }
    params.set("numberSchoolBuilding", groupCode);
    if (includeAddress) {
        const schoolBuildingId = schoolBuildingIdForAccess(accessCode);
        if (schoolBuildingId != null) params.set("schoolBuildingId", String(schoolBuildingId));
        const address = campusAddressForAccess(accessCode);
        if (address) params.set("campusAddress", address);
    }
    return `?${params.toString()}`;
}

function manualLoadScopeForAccess(accessCode) {
    const groupCode = buildingGroupCode(accessCode);
    const campusAddress = campusAddressForAccess(accessCode);
    const schoolBuildingId = schoolBuildingIdForAccess(accessCode);
    const classIds = Array.from(new Set((classroomRows || [])
        .filter((row) => rowMatchesBuildingAccess(row, accessCode))
        .map((row) => row?.id)
        .filter((id) => id !== null && id !== undefined)));
    return {
        scopeType: schoolBuildingId != null || campusAddress ? "BUILDING_ADDRESS" : "BUILDING_GROUP",
        numberSchoolBuilding: groupCode,
        campusAddress,
        schoolBuildingId,
        classIds
    };
}

function classIdForRow(row) {
    if (isExplicitMetaGroupRow(row)) return null;
    if (row?.classId != null) return row.classId;
    const className = normalizeClassName(row?.className);
    if (!className) return null;
    const groupCode = buildingGroupCode(row?.numberSchoolBuilding || selectedBuilding);
    const maps = classAddressMap();
    return maps.idByGroupAndClass.get(`${groupCode}|${className}`) || maps.idByClassOnly.get(className) || null;
}

function metaGroupIdForRow(row) {
    return isExplicitMetaGroupRow(row) && row?.metaGroupId != null ? row.metaGroupId : null;
}

function rowMatchesBuildingAccess(row, accessCode) {
    if (accessCode === ARCHIVE_BUILDING_CODE) return false;

    const selectedOrganizationalSp = buildingGroupCode(accessCode);
    if (!selectedOrganizationalSp) return false;

    const rowOrganizationalSp = buildingGroupCode(row?.numberSchoolBuilding);
    if (rowOrganizationalSp !== selectedOrganizationalSp) {
        return false;
    }

    const selectedSiteId = buildingSiteIdToken(accessCode);
    if (selectedSiteId != null) {
        const rowSchoolBuildingId = schoolBuildingIdForRow(row);
        return rowSchoolBuildingId != null && Number(rowSchoolBuildingId) === Number(selectedSiteId);
    }

    const address = buildingAddressToken(accessCode);
    if (address) {
        const selectedSchoolBuildingId = schoolBuildingIdForAccess(accessCode);
        const rowSchoolBuildingId = schoolBuildingIdForRow(row);

        if (selectedSchoolBuildingId != null) {
            return rowSchoolBuildingId != null && Number(rowSchoolBuildingId) === Number(selectedSchoolBuildingId);
        }

        const rowAddress = rowAddressToken(row);
        return Boolean(rowAddress) && rowAddress === address;
    }

    return true;
}

function loadAccessCodesForRow(row) {
    const groupCode = buildingGroupCode(row?.numberSchoolBuilding);
    if (!groupCode) return [];
    const codes = [groupCode];
    const rowSchoolBuildingId = schoolBuildingIdForRow(row);
    if (rowSchoolBuildingId != null) {
        codes.push(`${groupCode}::${rowSchoolBuildingId}`);
        (buildings || [])
            .filter((building) =>
                building?.scope === "address"
                && buildingGroupCode(buildingOptionValue(building)) === groupCode
                && Number(building.schoolBuildingId ?? building.id) === Number(rowSchoolBuildingId)
            )
            .map(buildingOptionValue)
            .filter(Boolean)
            .forEach((value) => codes.push(value));
    } else {
        const rowAddress = rowAddressToken(row);
        if (rowAddress) {
            codes.push(`${groupCode}|${rowAddress}`);
        } else {
            const knownAddresses = addressesForBuildingCode(groupCode).map(normalizeBuildingAccessCode).filter(Boolean);
            if (knownAddresses.length === 1) codes.push(`${groupCode}|${knownAddresses[0]}`);
        }
    }
    return Array.from(new Set(codes));
}

function rememberSelectedBuilding(code) {
    const normalized = code === ARCHIVE_BUILDING_CODE ? ARCHIVE_BUILDING_CODE : normalizeBuildingAccessCode(code);
    if (!normalized) return;
    sessionStorage.setItem(LOAD_SELECTED_BUILDING_KEY, normalized);
}

function mergeMetaGroupAddressScopeOptions(buildingGroups, curriculumSourceRows, physicalBuildingRows) {
    const physicalById = new Map((physicalBuildingRows || [])
        .filter((building) => building?.id != null || building?.schoolBuildingId != null)
        .map((building) => [Number(building.id ?? building.schoolBuildingId), building]));

    (curriculumSourceRows || [])
        .filter(contributesToManualLoad)
        .filter(isExplicitMetaGroupRow)
        .filter((row) => row?.schoolBuildingId !== null && row?.schoolBuildingId !== undefined)
        .forEach((row) => {
            const organizationalSp = normalizeBuildingCode(row.numberSchoolBuilding);
            const schoolBuildingId = Number(row.schoolBuildingId);
            if (!organizationalSp || !Number.isFinite(schoolBuildingId)) return;

            const physicalSite = physicalById.get(schoolBuildingId) || {};
            const physicalAddress = String(physicalSite.address || physicalSite.name || physicalSite.code || `Площадка ${schoolBuildingId}`).trim();
            if (!physicalAddress) return;

            const existing = buildingGroups.get(organizationalSp) || {
                code: organizationalSp,
                name: organizationalSp,
                addresses: [],
                addressRows: []
            };

            const addressKey = normalizeBuildingAccessCode(physicalAddress);
            if (!existing.addresses.some((address) => normalizeBuildingAccessCode(address) === addressKey)) {
                existing.addresses.push(physicalAddress);
            }

            const hasSamePhysicalSite = (existing.addressRows || []).some((site) =>
                Number(site?.id ?? site?.schoolBuildingId) === schoolBuildingId
            );
            if (!hasSamePhysicalSite) {
                existing.addressRows.push({
                    ...physicalSite,
                    id: schoolBuildingId,
                    schoolBuildingId,
                    address: physicalAddress
                });
            }

            buildingGroups.set(organizationalSp, existing);
        });
}

function restoreSelectedBuilding() {
    const restored = String(sessionStorage.getItem(LOAD_SELECTED_BUILDING_KEY) || "").trim();
    return restored === ARCHIVE_BUILDING_CODE ? ARCHIVE_BUILDING_CODE : normalizeBuildingAccessCode(restored);
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

    (buildings || [])
        .filter((b) => normalizeBuildingCode(b?.code) === normalizedCode)
        .forEach((b) => {
            (b.addresses || []).forEach(pushUnique);
            pushUnique(b.address);
        });

    return addresses;
}

function buildingTabLabel(building) {
    const base = String(building?.name || building?.code || "").trim();
    if (building?.scope === "address") {
        return `${base} — ${building.address || "адрес не указан"}`;
    }
    const addresses = addressesForBuildingCode(building?.code);
    if (!addresses.length) return `${base} — все адреса`;
    return `${base} — все адреса (${addresses.length})`;
}


function normalizeClassName(value) {
    const v = String(value || "").trim().toUpperCase().replace(/[–—]/g, "-");
    const m = v.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return m ? `${m[1]}-${m[2]}` : v;
}

function continuityGroupName(row) {
    if (row?.groupNameEducationalPlan) return String(row.groupNameEducationalPlan).trim();
    if (row?.__groupIndex) return `Группа ${row.__groupIndex}`;
    if (row?.subgroupRequired) return "Группа 1";
    return "";
}


function classAddressMap() {
    if (derivedCache.classAddressMapRowsRef === classroomRows) {
        return derivedCache.classAddressMapValue;
    }
    const maps = {
        byId: new Map(),
        byGroupAndClass: new Map(),
        byClassOnly: new Map(),
        idByGroupAndClass: new Map(),
        idByClassOnly: new Map(),
        schoolBuildingIdByClassId: new Map(),
        schoolBuildingIdByGroupAndClass: new Map(),
        schoolBuildingIdByClassOnly: new Map()
    };
    (classroomRows || []).forEach((r) => {
        const cls = normalizeClassName(r.className);
        const b = buildingGroupCode(r.numberSchoolBuilding);
        const address = normalizeBuildingAccessCode(r.campusAddress);
        if (!cls) return;
        if (address && r.id != null) maps.byId.set(String(r.id), address);
        if (b && address) maps.byGroupAndClass.set(`${b}|${cls}`, address);
        if (address && !maps.byClassOnly.has(cls)) maps.byClassOnly.set(cls, address);
        if (b && r.id != null) maps.idByGroupAndClass.set(`${b}|${cls}`, r.id);
        if (r.id != null && !maps.idByClassOnly.has(cls)) maps.idByClassOnly.set(cls, r.id);
        if (r.schoolBuildingId != null && r.id != null) maps.schoolBuildingIdByClassId.set(String(r.id), Number(r.schoolBuildingId));
        if (r.schoolBuildingId != null && b) maps.schoolBuildingIdByGroupAndClass.set(`${b}|${cls}`, Number(r.schoolBuildingId));
        if (r.schoolBuildingId != null && !maps.schoolBuildingIdByClassOnly.has(cls)) maps.schoolBuildingIdByClassOnly.set(cls, Number(r.schoolBuildingId));
    });
    derivedCache.classAddressMapRowsRef = classroomRows;
    derivedCache.classAddressMapValue = maps;
    return maps;
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
    invalidateTeacherHourIndexesCache();
    ui.saveBuildingBtn.classList.toggle("dirty-save", flag);
    ui.saveBuildingBtn.classList.toggle("clean-save", !flag);
}



function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(String(value || ""));
    return String(value || "").replace(/[\"']/g, "\\$&");
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
    if (part === "CORRECTIONAL") return "Коррекционная";
    return "Основная";
}

function subjectTypeByPart(part) {
    if (part === "CORRECTIONAL") return "CORRECTIONAL";
    if (part === "EXTRACURRICULAR") return "EXTRACURRICULAR";
    if (part === "FORMABLE") return "FORMABLE";
    return "CORE";
}


function teacherIdForName(fioTeacher) {
    const key = String(fioTeacher || "").trim().toLowerCase();
    if (!key) return null;
    const teacher = (teacherDirectory || []).find((t) => String(t.fioTeacher || "").trim().toLowerCase() === key);
    return teacher?.id ?? null;
}

function subjectIdForRow(row) {
    return row?.subjectId ?? row?.subject?.id ?? null;
}

function subjectCatalogKey(subjectName, subjectType = "") {
    return `${String(subjectName || "").trim().toLowerCase()}|${String(subjectType || "").trim().toUpperCase()}`;
}

function buildSubjectAreaIndex() {
    const byNameAndType = new Map();
    const byName = new Map();
    (subjectCatalog || []).forEach((subject) => {
        const subjectName = String(subject.subjectName || "").trim();
        const areaName = String(subject.subjectAreaName || subject.subjectArea?.name || "Без области").trim() || "Без области";
        const subjectType = String(subject.subjectType || "").trim().toUpperCase();
        if (!subjectName) return;
        byNameAndType.set(subjectCatalogKey(subjectName, subjectType), areaName);
        const nameKey = subjectName.toLowerCase();
        if (!byName.has(nameKey)) byName.set(nameKey, areaName);
    });
    return { byNameAndType, byName };
}

function subjectAreaForRow(row, index = buildSubjectAreaIndex()) {
    const expectedType = subjectTypeByPart(row?.curriculumPart || "CORE");
    const subjectName = String(row?.subjectName || "").trim();
    return index.byNameAndType.get(subjectCatalogKey(subjectName, expectedType))
        || (expectedType === "CORE" ? index.byNameAndType.get(subjectCatalogKey(subjectName, "CORE_FORMABLE")) : "")
        || (expectedType === "FORMABLE" ? index.byNameAndType.get(subjectCatalogKey(subjectName, "CORE_FORMABLE")) : "")
        || index.byName.get(subjectName.toLowerCase())
        || "Без области";
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
    const totals = classPeriodHours(rows);
    if (totals.h1 === 0 && totals.h2 === 0) {
        return totals.year > 0 ? String(totals.year) : "0";
    }
    return formatSplitHours(totals);
}

function classAssignedUnassignedText(rows = [], rowTeacher = "", buildingCode = selectedBuilding) {
    const teacherNormalized = String(rowTeacher || "").trim().toLowerCase();
    let assigned = 0;
    let unassigned = 0;
    rows.forEach((row) => {
        const hours = Number(row?.plannedHours || 0);
        const apiKey = apiKeyOfRow(row);
        const assignedTeacher = String(assignmentsForBuilding(buildingCode)[apiKey] || "").trim().toLowerCase();
        const plannedTransfer = futurePlansForBuilding(buildingCode)[apiKey] || null;
        const plannedTeacher = String(plannedTransfer?.targetTeacher || "").trim().toLowerCase();
        if (!assignedTeacher) {
            unassigned += hours;
            return;
        }
        if (teacherNormalized && plannedTeacher && plannedTeacher === teacherNormalized) {
            assigned += hours;
            return;
        }
        if (teacherNormalized && assignedTeacher === teacherNormalized) {
            assigned += hours;
        }
    });
    if (!teacherNormalized) {
        return unassigned > 0 ? `${unassigned}` : "0";
    }
    if (assigned <= 0 && unassigned <= 0) return "0";
    if (unassigned <= 0) return `${assigned}`;
    return `${assigned} / ${unassigned}`;
}

function formatSplitHours(pair) {
    const h1 = Number(pair?.h1 || 0);
    const h2 = Number(pair?.h2 || 0);
    if (h1 === h2) return String(h1);
    return `${h1} | ${h2}`;
}

function splitHoursSortValue(pair) {
    const h1 = Number(pair?.h1 || 0);
    const h2 = Number(pair?.h2 || 0);
    return h1 === h2 ? h1 : (h1 + h2);
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

function accumulateManualSplit(pair, entry) {
    const value = Number(entry?.load || 0);
    const period = manualEntryStudyPeriod(entry);
    if (period === "H1") pair.h1 += value;
    else if (period === "H2") pair.h2 += value;
    else {
        pair.h1 += value;
        pair.h2 += value;
    }
}

function teacherHoursKey(teacherName) {
    return String(teacherName || "")
        .trim()
        .replace(/ё/g, "е")
        .replace(/Ё/g, "Е")
        .replace(/\s+/g, " ")
        .toLowerCase();
}

function manualEntryLoadValue(entry) {
    const value = Number(entry?.groupLoad ?? entry?.load ?? 0);
    return Number.isFinite(value) ? value : 0;
}

function accumulateManualByStudyPeriod(pair, entry) {
    const value = manualEntryLoadValue(entry);
    if (!value) return;
    const period = manualEntryStudyPeriod(entry);
    if (period === "H1") pair.h1 += value;
    else if (period === "H2") pair.h2 += value;
    else {
        pair.h1 += value;
        pair.h2 += value;
    }
}

function manualRowActiveOnDate(row, referenceDate = currentDisplayDate()) {
    const from = String(row?.loadFromDate || "");
    const to = String(row?.loadToDate || "");
    return (!from || from <= referenceDate) && (!to || referenceDate <= to);
}

function manualRowDuplicateKey(row) {
    return [
        teacherHoursKey(row?.fioTeacher),
        normalizeBuildingCode(row?.numberSchoolBuilding),
        normalizeClassName(row?.className),
        String(row?.subjectName || "").trim().toLowerCase(),
        String(row?.groupNameEducationalPlan || "").trim().toLowerCase(),
        String(row?.studyPeriod || "YEAR"),
        String(row?.educationLevel || ""),
        String(row?.loadFromDate || ""),
        String(row?.loadToDate || ""),
        String(manualEntryLoadValue(row))
    ].join("|");
}

function dedupeManualRows(rows = []) {
    const byKey = new Map();
    (rows || []).forEach((row) => {
        const key = manualRowDuplicateKey(row);
        if (!byKey.has(key)) byKey.set(key, row);
    });
    return Array.from(byKey.values());
}

function buildTeacherHoursByStudyPeriod(rows = [], referenceDate = currentDisplayDate()) {
    const result = {};

    dedupeManualRows(rows || [])
        .filter((entry) => String(entry?.fioTeacher || "").trim())
        .filter((entry) => manualRowActiveOnDate(entry, referenceDate))
        .forEach((entry) => {
            const key = teacherHoursKey(entry.fioTeacher);
            if (!key) return;

            if (!result[key]) {
                result[key] = { h1: 0, h2: 0 };
            }

            accumulateManualByStudyPeriod(result[key], entry);
        });

    return result;
}

function addTeacherHours(target, source) {
    Object.entries(source || {}).forEach(([key, pair]) => {
        if (!target[key]) {
            target[key] = { h1: 0, h2: 0 };
        }
        target[key].h1 += Number(pair?.h1 || 0);
        target[key].h2 += Number(pair?.h2 || 0);
    });
    return target;
}

function buildTeacherHoursFromAssignments(buildingCode) {
    const result = {};
    const assignments = assignmentsForBuilding(buildingCode);
    expandedRowsForSelectedBuilding().forEach((row) => {
        const teacher = String(assignments[apiKeyOfRow(row)] || "").trim();
        const key = teacherHoursKey(teacher);
        if (!key) return;
        if (!result[key]) {
            result[key] = { h1: 0, h2: 0 };
        }
        accumulateSplit(result[key], row);
    });
    return result;
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
    const matched = expandedRowsForSelectedBuilding().filter((row) =>
        row.className === curriculumRow.className
        && row.subjectName === curriculumRow.subjectName
        && (row.curriculumPart || "CORE") === (curriculumRow.curriculumPart || "CORE")
        && row.educationLevel === curriculumRow.educationLevel
        && groupSuffix(row) === groupSuffix(curriculumRow)
    );
    return matched.length ? matched : [curriculumRow];
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


function isExplicitMetaGroupRow(row) {
    return row?.metaGroupId != null
        || String(row?.className || "").trim().toUpperCase().startsWith("МГ:");
}

function contributesToManualLoad(row) {
    if (isExplicitMetaGroupRow(row)) return true;
    return !Boolean(row?.excludedFromManualLoad);
}

function rowsForSelectedBuilding() {
    if (selectedBuilding === ARCHIVE_BUILDING_CODE) return [];
    const cacheKey = `${sourceRevision}|${normalizeBuildingAccessCode(selectedBuilding)}`;
    if (derivedCache.rowsByBuildingKey === cacheKey) {
        return derivedCache.rowsByBuildingValue;
    }
    const map = classBuildingMap();
    const scoped = curriculumRows.filter((row) => {
        const rowSource = { ...row, numberSchoolBuilding: row.numberSchoolBuilding || map.get(normalizeClassName(row.className)) };
        return rowMatchesBuildingAccess(rowSource, selectedBuilding);
    });
    const filtered = scoped.filter(contributesToManualLoad);
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
                ? Number(row.subgroup1Hours ?? row.plannedHours ?? 0)
                : Number(row.subgroup2Hours ?? row.plannedHours ?? 0);
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
    const cacheKey = `${sourceRevision}|${normalizeBuildingAccessCode(selectedBuilding)}`;
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

function selectedAcademicYearStart() {
    const raw = String(sessionStorage.getItem("tarification.academicYear") || "").trim();
    const match = raw.match(/^(\d{4})\s*\/\s*(\d{4})$/);
    if (match) return Number(match[1]);

    const settingsDates = (studyPeriodSettings || [])
        .map((item) => String(item?.startDate || ""))
        .filter(Boolean)
        .sort();

    if (settingsDates.length) {
        return Number(settingsDates[0].slice(0, 4));
    }

    return new Date().getFullYear();
}

function fallbackYearRange() {
    const yearFrom = selectedAcademicYearStart();
    const yearTo = yearFrom + 1;

    return {
        yearFrom: `${yearFrom}-09-01`,
        h1To: `${yearFrom}-12-31`,
        h2From: `${yearTo}-01-01`,
        yearTo: `${yearTo}-05-31`,
        h1_11_to: `${yearTo}-01-31`,
        h2_11_from: `${yearTo}-02-01`
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

function clampDateToPeriod(date, period) {
    if (!period?.from || !period?.to) return date;
    if (date < period.from) return period.from;
    if (date > period.to) return period.to;
    return date;
}

function referencePlanningDate() {
    const today = new Date().toISOString().slice(0, 10);
    const period = defaultLoadPeriod("1-А", "YEAR");
    return clampDateToPeriod(today, period);
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
    if (!buildingGroupCode(buildingCode)) return false;
    const classMap = classBuildingMap();
    return (curriculumRows || []).some((row) => {
        const rowSource = { ...row, numberSchoolBuilding: row.numberSchoolBuilding || classMap.get(normalizeClassName(row.className)) };
        return rowMatchesBuildingAccess(rowSource, buildingCode);
    });
}

function buildingOptionValue(option) {
    return option?.value || option?.code || "";
}

function buildingPermissionMatchesOption(permissionCode, optionValue) {
    const permission = normalizeBuildingAccessCode(permissionCode);
    const option = normalizeBuildingAccessCode(optionValue);
    if (!permission || !option) return false;
    if (permission === option) return true;
    return !permission.includes("|") && !permission.includes("::") && buildingGroupCode(permission) === buildingGroupCode(option);
}

function preferredBuildingCode(availableBuildings) {
    if (!Array.isArray(availableBuildings) || !availableBuildings.length) return "";
    const user = currentAuthUser();
    const allOptions = availableBuildings.map(buildingOptionValue).filter(Boolean);
    const editableCodes = [];

    if (!user || user.admin || user.loadEditAllBuildings) {
        editableCodes.push(...allOptions);
    } else {
        editableCodes.push(...(user.loadEditableBuildingCodes || [])
            .map((code) => normalizeBuildingAccessCode(code))
            .filter(Boolean));
        const managedCode = buildingGroupCode(user.managedBuildingCode);
        if (managedCode) editableCodes.push(managedCode);
        if (!editableCodes.length) {
            editableCodes.push(...allOptions);
        }
    }

    const candidateOptions = allOptions.filter((option) =>
        editableCodes.some((permission) => buildingPermissionMatchesOption(permission, option))
    );
    const orderedCandidates = candidateOptions.length ? candidateOptions : allOptions;
    for (const option of orderedCandidates) {
        if (hasCurriculumRowsForBuilding(option)) {
            return option;
        }
    }
    return orderedCandidates[0] || availableBuildings[0].code;
}


function canEditSelectedBuildingLoad() {
    const user = currentAuthUser();
    if (!user) return false;
    if (user.admin) return true;
    const loadPermission = window.tarificationTabPermissions?.LOAD;
    if (!loadPermission?.canEdit) return false;
    if (user.loadEditAllBuildings) return true;
    const allowedBuildings = (user.loadEditableBuildingCodes || []);
    if (allowedBuildings.length) {
        return allowedBuildings.some((code) => buildingPermissionMatchesOption(code, selectedBuilding));
    }
    if (user.role !== "BUILDING_HEAD") return false;
    return buildingGroupCode(user.managedBuildingCode) === buildingGroupCode(selectedBuilding);
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
    if (allowedBuildings.length && !allowedBuildings.some((code) => buildingPermissionMatchesOption(code, selectedBuilding))) {
        return `Редактирование разрешено только для зон: ${allowedBuildings.join(", ")}.`;
    }
    if (user.role === "BUILDING_HEAD" && buildingGroupCode(user.managedBuildingCode) !== buildingGroupCode(selectedBuilding)) {
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

function updateAdminOnlyActions() {
    if (!ui.clearBuildingLoadBtn) return;
    ui.clearBuildingLoadBtn.style.display = currentAuthUser()?.admin ? "" : "none";
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
        .filter((r) => rowMatchesBuildingAccess(r, selectedBuilding))
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

    const allApiRows = expandCurriculumRows(curriculumRows.filter(contributesToManualLoad));

    const matchByManual = (entry) => {
        const entryGroup = String(entry.groupNameEducationalPlan || "").trim().toUpperCase();
        const candidates = allApiRows.filter((row) =>
            normalizeBuildingCode(row.numberSchoolBuilding) === normalizeBuildingCode(entry.numberSchoolBuilding)
            && row.className === entry.className
            && row.subjectName === entry.subjectName
            && row.educationLevel === entry.educationLevel
            && String(row.__groupIndex ? `ГРУППА ${row.__groupIndex}` : "").trim().toUpperCase() === entryGroup
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
        const subjectKey = subjectKeyOfRow(matched);

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

        loadAccessCodesForRow(matched).forEach((buildingCode) => {
            const assignments = assignmentsForBuilding(buildingCode);
            const teacherRowsMap = teacherRowsForBuilding(buildingCode);
            const plans = futurePlansForBuilding(buildingCode);

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
    });
}

function continuityStatusKey(buildingCode, className, subjectName, educationLevel, groupName, teacherName) {
    return [
        normalizeBuildingCode(buildingCode),
        normalizeClassName(className),
        String(subjectName || "").trim().toLowerCase(),
        String(educationLevel || "").trim().toUpperCase(),
        String(groupName || "").trim().toLowerCase(),
        String(teacherName || "").trim().toLowerCase()
    ].join("|");
}

function buildContinuityStatusIndex(referenceDate) {
    const periodRef = String(referenceDate || referencePlanningDate());
    const selectedBuildingCode = selectedBuilding;
    const index = new Map();
    (manualRows || []).forEach((entry) => {
        if (!rowMatchesBuildingAccess(entry, selectedBuildingCode)) return;
        const from = String(entry.loadFromDate || "");
        const to = String(entry.loadToDate || "");
        if (!from || !to || !(from <= periodRef && periodRef <= to)) return;
        const status = String(entry.continuityStatus || "").trim().toUpperCase();
        if (!status) return;
        const key = continuityStatusKey(
            entry.numberSchoolBuilding,
            entry.className,
            entry.subjectName,
            entry.educationLevel,
            entry.groupNameEducationalPlan,
            entry.fioTeacher
        );
        index.set(key, status);
    });
    return index;
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
    const buildingRows = expandCurriculumRows(curriculumRows.filter(contributesToManualLoad).filter((row) => {
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
    const referenceDate = currentDisplayDate();
    const assignmentSignature = Object.entries(assignmentsForBuilding(selectedBuilding))
        .map(([k, v]) => `${k}:${String(v || "").trim()}`)
        .sort()
        .join("||");

    const buildingSignature = (manualRows || [])
        .map((row) => [
            row.id || "",
            row.fioTeacher || "",
            row.teacherId || "",
            row.classId || "",
            row.metaGroupId || "",
            row.subjectId || "",
            row.load || "",
            row.studyPeriod || "",
            row.loadFromDate || "",
            row.loadToDate || ""
        ].join(":"))
        .join("||");

    const complexSignature = (complexManualRows || [])
        .map((row) => [
            row.id || "",
            row.fioTeacher || "",
            row.teacherId || "",
            row.classId || "",
            row.metaGroupId || "",
            row.subjectId || "",
            row.load || "",
            row.studyPeriod || "",
            row.loadFromDate || "",
            row.loadToDate || ""
        ].join(":"))
        .join("||");

    const cacheKey = `${sourceRevision}|${selectedBuilding}|${referenceDate}|${assignmentSignature}|${buildingSignature}|${complexSignature}`;

    if (teacherHourIndexesCacheKey === cacheKey) {
        return teacherHourIndexesCacheValue;
    }

    const selectedBuildingGroup = buildingGroupCode(selectedBuilding);
    const selectedAssignmentHours = buildTeacherHoursFromAssignments(selectedBuilding);
    const complexRowsOutsideSelected = (complexManualRows || [])
        .filter((row) => !rowMatchesBuildingAccess(row, selectedBuilding));

    const buildingTeacherHours = {
        [selectedBuildingGroup]: selectedAssignmentHours
    };
    const complexTeacherHours = addTeacherHours(
        buildTeacherHoursByStudyPeriod(complexRowsOutsideSelected, referenceDate),
        selectedAssignmentHours
    );

    teacherHourIndexesCacheKey = cacheKey;
    teacherHourIndexesCacheValue = { buildingTeacherHours, complexTeacherHours };

    return teacherHourIndexesCacheValue;
}

function buildPresentationRows() {
    const rows = expandedRowsForSelectedBuilding();
    const subjectAreaIndex = buildSubjectAreaIndex();
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
                subjectAreaName: subjectAreaForRow(row, subjectAreaIndex),
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

            const selectedBuildingGroup = buildingGroupCode(selectedBuilding);
            const teacherHoursLookupKey = teacherHoursKey(teacherRow.teacherName);
            const buildingHoursPair = buildingTeacherHours[selectedBuildingGroup]?.[teacherHoursLookupKey] || { h1: 0, h2: 0 };
            const complexHoursPair = complexTeacherHours[teacherHoursLookupKey] || { h1: 0, h2: 0 };
            result.push({
                subjectKey: info.subjectKey,
                teacherRowId: teacherRow.id,
                subjectName: info.subjectName,
                subjectAreaName: info.subjectAreaName || "Без области",
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
                buildingHours: formatSplitHours(buildingHoursPair),
                buildingHoursSort: splitHoursSortValue(buildingHoursPair),
                complexHours: formatSplitHours(complexHoursPair),
                complexHoursSort: splitHoursSortValue(complexHoursPair)
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
            subjectAreaName: template.subjectAreaName || "Без области",
            subjectHours: 0,
            buildingHours: "0/0",
            buildingHoursSort: 0,
            complexHours: "0/0",
            complexHoursSort: 0,
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
            case "subjectArea":
                result = cmp(a.subjectAreaName || "Без области", b.subjectAreaName || "Без области") || cmp(a.subjectName, b.subjectName);
                break;
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
                result = (a.buildingHoursSort - b.buildingHoursSort);
                break;
            case "complexHours":
                result = (a.complexHoursSort - b.complexHoursSort);
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
        return String(a.subjectAreaName || "Без области").localeCompare(String(b.subjectAreaName || "Без области"), 'ru')
            || String(a.subjectName).localeCompare(String(b.subjectName), 'ru');
    });
}

function renderBuildingTabs() {
    if (!ui.buildingSelect) return;
    ui.buildingSelect.innerHTML = "";
    const tabs = [...buildings, { value: ARCHIVE_BUILDING_CODE, code: ARCHIVE_BUILDING_CODE, name: ARCHIVE_BUILDING_LABEL, label: `🗂 ${ARCHIVE_BUILDING_LABEL}` }];
    tabs.forEach((building) => {
        const option = document.createElement("option");
        option.value = building.value || building.code;
        const tabLabel = building.code === ARCHIVE_BUILDING_CODE
            ? building.label
            : buildingTabLabel(building);
        option.textContent = tabLabel;
        ui.buildingSelect.appendChild(option);
    });
    ui.buildingSelect.value = selectedBuilding;
}

async function refreshSelectedBuildingData(force = false) {
    const cacheKey = buildingCacheKey(selectedBuilding);
    const cached = buildingDataCache.get(cacheKey);
    const now = Date.now();
    if (!force && cached && (now - cached.ts) < BUILDING_DATA_CACHE_TTL_MS) {
        curriculumRows = cached.curriculum;
        manualRows = cached.manual;
        complexManualRows = await api("/api/manual-load");
        buildingDataCache.set(cacheKey, { ...cached, complexManual: complexManualRows });
    } else {
        const curriculumQuery = scopedBuildingQuery(selectedBuilding, false);
        const manualQuery = scopedBuildingQuery(selectedBuilding, true);
        const [curriculum, manual, complexManual] = await Promise.all([
            api(`/api/curriculum${curriculumQuery}`),
            api(`/api/manual-load${manualQuery}`),
            api("/api/manual-load")
        ]);
        curriculumRows = curriculum || [];
        manualRows = manual || [];
        complexManualRows = complexManual || [];
        buildingDataCache.set(cacheKey, { ts: now, curriculum: curriculumRows, manual: manualRows, complexManual: complexManualRows });
    }
    sourceRevision += 1;
    invalidateDerivedCache();
    invalidateTeacherHourIndexesCache();
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

    const buildingCode = selectedBuilding;
    const targetPeriod = rowStudyPeriod(curriculumRow);
    const targetGroup = curriculumRow.__groupIndex ? `ГРУППА ${curriculumRow.__groupIndex}` : "";
    const referenceDate = currentDisplayDate();

    const matched = (manualRows || []).filter((entry) => {
        const entryTeacher = String(entry.fioTeacher || "").trim().toLowerCase();
        if (!entryTeacher || entryTeacher !== teacher) return false;
        if (!rowMatchesBuildingAccess(entry, buildingCode)) return false;
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
        to: String(source.loadToDate || ""),
        continuityStatus: String(source.continuityStatus || "").trim().toUpperCase()
    };
}


function openSubgroupDrawer(presentationRow, className, classRows) {
    if (!ui.subgroupDrawer || !ui.subgroupDrawerBody) return;
    const assignments = assignmentsForBuilding(selectedBuilding);
    state.subgroupDrawerContext = {
        subjectKey: presentationRow.subjectKey,
        className,
        rows: classRows,
        subjectName: presentationRow.subjectName
    };
    if (ui.subgroupDrawerTitle) ui.subgroupDrawerTitle.textContent = `${className} — ${presentationRow.subjectName}`;
    ui.subgroupDrawerBody.innerHTML = classRows.map((item, idx) => {
        const apiKey = apiKeyOfRow(item);
        const subgroupName = item.__groupIndex ? `Подгруппа ${item.__groupIndex}` : `Подгруппа ${idx + 1}`;
        const teacher = String(assignments[apiKey] || '').trim();
        const period = defaultPeriodForRows([item]);
        const manualPeriod = findManualPeriodForClassTeacher(item, teacher);
        const hasHours = Number(item.plannedHours || 0) > 0;
        const teacherControl = hasHours
            ? `<div class="subgroup-teacher-row"><input type="text" list="teacher-list-shared" data-subgroup-teacher="1" value="${esc(teacher)}" placeholder="ФИО педагога"><button type="button" class="danger-btn" data-subgroup-remove="1">Снять</button></div>`
            : '<div class="subgroup-no-hours">Педагог не требуется</div>';
        const periodControl = hasHours
            ? `<div class="subgroup-period-grid"><label>С</label><input type="date" data-subgroup-from="1" value="${esc(manualPeriod?.from || period.from || '')}"><label>По</label><input type="date" data-subgroup-to="1" value="${esc(manualPeriod?.to || period.to || '')}"></div>`
            : '';
        return `<div class="subgroup-line" data-subgroup-idx="${idx}"><div class="subgroup-line-head"><strong>${esc(subgroupName)}</strong><span>${esc(Number(item.plannedHours || 0))} ч</span></div>
<label>Педагог</label>${teacherControl}
${periodControl}</div>`;
    }).join('') + '<datalist id="teacher-list-shared"></datalist>';
    const sharedList = ui.subgroupDrawerBody.querySelector('#teacher-list-shared');
    if (sharedList) {
        updateDatalistOptions(sharedList, '');
        ui.subgroupDrawerBody.querySelectorAll('[data-subgroup-teacher]').forEach((input) => {
            input.addEventListener('input', () => updateDatalistOptions(sharedList, input.value || ''));
            input.addEventListener('focus', () => updateDatalistOptions(sharedList, ''));
            input.addEventListener('click', () => updateDatalistOptions(sharedList, ''));
        });
    }
    ui.subgroupDrawerBody.querySelectorAll('[data-subgroup-remove]').forEach((button) => {
        button.addEventListener('click', () => {
            const line = button.closest('.subgroup-line');
            const input = line?.querySelector('[data-subgroup-teacher]');
            if (input) {
                input.value = '';
                input.focus();
            }
        });
    });
    if (ui.subgroupDrawerBackdrop) ui.subgroupDrawerBackdrop.hidden = false;
    ui.subgroupDrawer.setAttribute('aria-hidden', 'false');
    ui.subgroupDrawer.classList.add('open');
}
function closeSubgroupDrawer() {
    if (!ui.subgroupDrawer) return;
    ui.subgroupDrawer.classList.remove('open');
    ui.subgroupDrawer.setAttribute('aria-hidden', 'true');
    if (ui.subgroupDrawerBackdrop) ui.subgroupDrawerBackdrop.hidden = true;
    state.subgroupDrawerContext = null;
}
function applySubgroupDrawerAssignments() {
    const ctx = state.subgroupDrawerContext;
    if (!ctx) return;
    const assignments = assignmentsForBuilding(selectedBuilding);
    const plans = futurePlansForBuilding(selectedBuilding);
    const referenceDate = currentDisplayDate();
    const lines = Array.from(ui.subgroupDrawerBody.querySelectorAll('.subgroup-line[data-subgroup-idx]'));
    const appliedByApiKey = new Map();
    for (const line of lines) {
        const idx = Number(line.dataset.subgroupIdx || -1);
        const row = (ctx.rows || [])[idx];
        if (!row) continue;
        const apiKey = apiKeyOfRow(row);
        if (Number(row.plannedHours || 0) <= 0) {
            assignments[apiKey] = '';
            delete plans[apiKey];
            continue;
        }
        const input = line.querySelector('input[type="text"][data-subgroup-teacher]');
        const raw = String(input?.value || '').trim();
        const fromInput = line.querySelector('[data-subgroup-from]');
        const toInput = line.querySelector('[data-subgroup-to]');
        const fromDate = String(fromInput?.value || '');
        const toDate = String(toInput?.value || '');
        if ((fromDate && toDate) && fromDate > toDate) { print({ warning: 'Период задан некорректно' }); return; }

        const currentTeacher = String(assignments[apiKey] || '').trim();
        if (!raw) { assignments[apiKey] = ''; delete plans[apiKey]; continue; }
        const exact = teacherNames.find((name) => name.toLowerCase() === raw.toLowerCase());
        if (!exact) { print({ warning: `Педагог «${raw}» не найден` }); return; }

        if (!currentTeacher || currentTeacher.toLowerCase() === exact.toLowerCase() || !fromDate || fromDate <= referenceDate) {
            assignments[apiKey] = exact;
            delete plans[apiKey];
        } else {
            const row = (ctx.rows || []).find((r) => apiKeyOfRow(r) === apiKey);
            plans[apiKey] = {
                targetTeacher: exact,
                previousTeacher: currentTeacher,
                fromDate,
                toDate,
                subjectKey: ctx.subjectKey,
                plannedHours: Number(row?.plannedHours || 0),
                className: ctx.className,
                educationLevel: row?.educationLevel,
                subjectName: ctx.subjectName
            };
        }
        appliedByApiKey.set(apiKey, { teacherName: exact, fromDate, toDate });
    }

    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const ctxSubjectRows = ctx.rows || [];
    const teachersBySubject = new Map();
    ctxSubjectRows.forEach((row) => {
        const subjectKey = subjectKeyOfRow(row);
        const teacher = String(assignments[apiKeyOfRow(row)] || '').trim();
        if (!teacher) return;
        if (!teachersBySubject.has(subjectKey)) teachersBySubject.set(subjectKey, new Set());
        teachersBySubject.get(subjectKey).add(teacher);
    });
    ctxSubjectRows.forEach((row) => {
        const apiKey = apiKeyOfRow(row);
        const subjectKey = subjectKeyOfRow(row);
        const plan = plans[apiKey];
        if (!plan) return;
        if (!teachersBySubject.has(subjectKey)) teachersBySubject.set(subjectKey, new Set());
        if (plan.previousTeacher) teachersBySubject.get(subjectKey).add(String(plan.previousTeacher).trim());
        if (plan.targetTeacher) teachersBySubject.get(subjectKey).add(String(plan.targetTeacher).trim());
    });
    teachersBySubject.forEach((teachers, subjectKey) => {
        if (!rowsMap[subjectKey]) rowsMap[subjectKey] = [];
        const subjectRows = ctxSubjectRows.filter((r) => subjectKeyOfRow(r) === subjectKey);
        const period = defaultPeriodForRows(subjectRows);
        teachers.forEach((teacherName) => {
            const firstRow = subjectRows.find((r) => {
                const apiKey = apiKeyOfRow(r);
                return String(appliedByApiKey.get(apiKey)?.teacherName || '').trim().toLowerCase() === teacherName.toLowerCase();
            });
            const apiKey = firstRow ? apiKeyOfRow(firstRow) : null;
            const plan = apiKey ? plans[apiKey] : null;
            const applied = apiKey ? appliedByApiKey.get(apiKey) : null;
            const fromDate = applied?.fromDate || period.from;
            const toDate = applied?.toDate || period.to;
            const existingRow = rowsMap[subjectKey].find((row) => String(row.teacherName || '').trim().toLowerCase() === teacherName.toLowerCase());
            if (!existingRow) {
                rowsMap[subjectKey].push({ id: rowId(), teacherName, studyPeriod: period.studyPeriod, loadFromDate: fromDate, loadToDate: toDate });
            } else {
                if (fromDate) existingRow.loadFromDate = fromDate;
                if (toDate) existingRow.loadToDate = toDate;
            }

            if (plan?.previousTeacher && plan?.fromDate) {
                const donorName = String(plan.previousTeacher).trim().toLowerCase();
                const donorRow = rowsMap[subjectKey].find((row) => String(row.teacherName || '').trim().toLowerCase() === donorName);
                if (donorRow) {
                    const donorEndDate = dayBefore(plan.fromDate);
                    if (donorEndDate) {
                        donorRow.loadToDate = donorEndDate;
                    }
                }
            }
        });
    });

    markDirty();
    closeSubgroupDrawer();
    scheduleRenderTable();
}


function subgroupRowsForClass(presentationRow, className) {
    const direct = presentationRow.rowsByClassAll?.[className] || [];
    const classRows = expandedRowsForSelectedBuilding().filter((row) => {
        if (normalizeClassName(row.className) !== normalizeClassName(className)) return false;
        if (String(row.subjectName || '').trim() !== String(presentationRow.subjectName || '').trim()) return false;
        if (String(row.curriculumPart || '') !== String(presentationRow.curriculumPart || '')) return false;
        const hasGroup = Number(row.__groupCount || 0) > 0 || Number(row.__groupIndex || 0) > 0;
        return hasGroup;
    });
    const ordered = (classRows.length > direct.length ? classRows : direct)
        .slice()
        .sort((a, b) => Number(a.__groupIndex || 0) - Number(b.__groupIndex || 0));
    const seen = new Set();
    return ordered.filter((row) => {
        const key = apiKeyOfRow(row);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

function onClassCellClick(presentationRow, className, cellButton = null) {
    if (!canEditSelectedBuildingLoad()) {
        print({ warning: loadReadOnlyReason() || "Редактирование этой нагрузки недоступно" });
        return;
    }
    const curriculumRow = presentationRow.rowsByClass[className];
    if (!curriculumRow) return;

    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowMeta = findTeacherRowMeta(presentationRow.subjectKey, presentationRow.teacherRowId);
    const classRowsForCell = subgroupRowsForClass(presentationRow, className);
    if (classRowsForCell.some((item) => Number(item.__groupCount || 0) > 0 || item.__groupIndex)) {
        openSubgroupDrawer(presentationRow, className, classRowsForCell);
        return;
    }

    const targetTeacher = String(rowMeta?.teacherName || presentationRow.teacherName || "").trim();
    if (!targetTeacher) {
        print({ warning: "Сначала заполните ФИО педагога в строке" });
        return;
    }

    const classRows = presentationRow.rowsByClassAll?.[className] || [curriculumRow];
    if (classRows.some((item) => Number(item.__groupCount || 0) > 0 || item.__groupIndex)) {
        openSubgroupDrawer(presentationRow, className, classRows);
        return;
    }

    const syncRows = rowsToSyncForCurriculumRow(curriculumRow);
    const apiKeys = syncRows.map((row) => apiKeyOfRow(row));
    const currentTeacher = String(assignments[apiKeys.find((key) => String(assignments[key] || "").trim())] || "").trim();

    if (!currentTeacher) {
        let newlyAssignedCount = 0;
        let newlyAssignedHours = 0;
        apiKeys.forEach((key, idx) => {
            const hadAssignment = Boolean(String(assignments[key] || "").trim());
            assignments[key] = targetTeacher;
            if (!hadAssignment) {
                newlyAssignedCount += 1;
                newlyAssignedHours += Number(syncRows[idx]?.plannedHours || 0);
            }
        });
        applyFastAssignmentUIUpdate(cellButton, newlyAssignedCount, newlyAssignedHours);
        patchSiblingCellsForSubjectClass(presentationRow.subjectKey, className);
        markDirty();
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
            errors.push({
                rowKey: rowStableKey(row),
                message: `Педагог «${row.teacherName}» отсутствует в справочнике педагогов.`
            });
        }
        classes.forEach((className) => {
            const classRows = row.rowsByClassAll?.[className] || [];
            classRows.forEach((curriculumRow) => {
                if (Number(curriculumRow.plannedHours || 0) <= 0) return;
                const assignedTeacher = String(assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(curriculumRow)] || "").trim();
                if (!assignedTeacher) {
                    unassignedHours += Number(curriculumRow.plannedHours || 0);
                    errorCount += 1;
                    errors.push({
                        rowKey: rowStableKey(row),
                        message: `Не назначен педагог: ${curriculumRow.className}, предмет «${curriculumRow.subjectName}».`
                    });
                }
            });
        });
    });

    const conflicts = detectManualLoadConflicts();
    errorCount += conflicts.size;
    conflicts.forEach((id) => errors.push({ rowKey: `manual:${id}`, message: "Конфликт периодов в ручной нагрузке." }));

    (manualRows || []).filter((r) => rowMatchesBuildingAccess(r, selectedBuilding)).forEach((r)=>{
        if (r.orphaned) {
            errorCount += 1;
            errors.push({ rowKey: `manual:${r.id}`, message: "Сиротская строка нагрузки: в учебном плане нет соответствующей позиции." });
        }
    });

    ui.unassignedHours.textContent = String(unassignedHours);
    ui.errorCount.textContent = String(errorCount);
    return { errors, errorCount };
}

async function refreshHealthCounters() {
    if (!selectedBuilding || selectedBuilding === ARCHIVE_BUILDING_CODE) return;

    // Для физической площадки локальный расчёт уже выполнен через
    // collectLoadIssues() по строкам, отобранным через schoolBuildingId.
    // Backend health сейчас принимает только организационное СП,
    // поэтому не должен перезаписывать корректные site-scope счётчики.
    if (isAddressScopedBuilding(selectedBuilding)) return;

    try {
        const health = await api(`/api/manual-load/health?building=${encodeURIComponent(buildingGroupCode(selectedBuilding))}`);
        ui.unassignedHours.textContent = String(health?.unassignedHours || 0);
        ui.errorCount.textContent = String(health?.errorCount || 0);
    } catch {
        // fallback оставляем за локальным расчетом
    }
}

function jumpToFirstError() {
    if (!currentErrorList.length) return;
    currentErrorIndex = (currentErrorIndex + 1) % currentErrorList.length;
    const current = currentErrorList[currentErrorIndex];
    const target = ui.tableBody.querySelector(`tr[data-row-key="${CSS.escape(String(current.rowKey || ""))}"]`);
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.classList.add('error-row-highlight');
    setTimeout(() => target.classList.remove('error-row-highlight'), 1400);
}

function showErrorsPopup() {
    if (!currentErrorList.length) {
        alert("Ошибок нет.");
        return;
    }
    const lines = currentErrorList.slice(0, 30).map((e, idx) => `${idx + 1}) ${e.message}`);
    const suffix = currentErrorList.length > 30 ? `\n...и ещё ${currentErrorList.length - 30}` : "";
    alert(`Найдено ошибок: ${currentErrorList.length}\n\n${lines.join("\n")}${suffix}`);
}

function applyFastAssignmentUIUpdate(cellButton, assignedCount, assignedHours) {
    if (cellButton) {
        cellButton.classList.add("active");
        cellButton.classList.remove("unassigned", "muted");
    }
    if (assignedCount > 0 && ui.errorCount) {
        const currentErrors = Number(ui.errorCount.textContent || "0");
        ui.errorCount.textContent = String(Math.max(0, currentErrors - assignedCount));
    }
    if (assignedHours > 0 && ui.unassignedHours) {
        const currentUnassigned = Number(ui.unassignedHours.textContent || "0");
        ui.unassignedHours.textContent = String(Math.max(0, currentUnassigned - assignedHours));
    }
}

function patchSiblingCellsForSubjectClass(subjectKey, className) {
    const rowById = new Map(latestPresentationRows.map((row) => [String(row.teacherRowId), row]));
    const selector = `button[data-class-cell="1"][data-subject-key="${CSS.escape(String(subjectKey || ""))}"][data-class-name="${CSS.escape(String(className || ""))}"]`;
    const buttons = ui.tableBody?.querySelectorAll(selector) || [];
    const assignments = assignmentsForBuilding(selectedBuilding);
    const plansMap = futurePlansForBuilding(selectedBuilding);
    const referenceDate = currentDisplayDate();
    buttons.forEach((button) => {
        const row = rowById.get(String(button.dataset.rowId || ""));
        if (!row) return;
        const curriculumRow = row.rowsByClass?.[className];
        if (!curriculumRow) return;
        const classRows = row.rowsByClassAll?.[className] || [curriculumRow];
        const assignedTeachers = classRows.map((item) => String(assignments[apiKeyOfRow(item)] || "").trim()).filter(Boolean);
        const rowTeacher = String(row.teacherName || "").trim();
        const hasAnyAssigned = assignedTeachers.length > 0;
        const hasRowTeacherAssigned = rowTeacher ? assignedTeachers.includes(rowTeacher) : false;
        const plans = classRows.map((item) => plansMap[apiKeyOfRow(item)]).filter(Boolean);
        const isPlanned = plans.some((plan) => plan.targetTeacher === rowTeacher && plan.fromDate > referenceDate);
        const isTransferOut = plans.some((plan) => plan.previousTeacher === rowTeacher && plan.fromDate > referenceDate);
        const isActive = hasRowTeacherAssigned;
        const isMuted = rowTeacher !== "" && !hasRowTeacherAssigned && !isPlanned && !isTransferOut;
        const isUnassigned = !hasAnyAssigned && !isPlanned;
        button.classList.toggle("active", isActive);
        button.classList.toggle("muted", isMuted);
        button.classList.toggle("unassigned", isUnassigned);
    });
}

async function renderStatsView() {
    if (!ui.statsTable || !ui.statsSummary) return;
    try {
        const params = new URLSearchParams();
        params.set("page", "0");
        params.set("pageSize", "500");
        if (selectedBuilding && selectedBuilding !== ARCHIVE_BUILDING_CODE) {
            params.set("building", buildingGroupCode(selectedBuilding));
        }
        const stats = await api(`/api/manual-load/stats?${params.toString()}`);
        const rows = stats?.rows || [];
        if (!rows.length) {
            ui.statsSummary.textContent = "Нет данных для статистики.";
            ui.statsTable.innerHTML = "<tbody><tr><td>Нет данных.</td></tr></tbody>";
            return;
        }
        ui.statsSummary.textContent = `Предметов: ${stats.subjects}. Плановых часов: ${stats.totalPlanned}. Распределено: ${stats.totalAssigned}. Нераспределено: ${stats.totalUnassigned}.`;
        ui.statsTable.innerHTML = `
            <thead><tr>
                <th>Предметная область</th><th>Предмет</th><th>Часы по УП</th><th>Распределено</th><th>Не распределено</th>
            </tr></thead>
            <tbody>${rows.map((row) => `
                <tr>
                    <td>${esc(row.subjectArea || "Без области")}</td>
                    <td>${esc(row.subjectName || "")}</td>
                    <td>${esc(row.planned || 0)}</td>
                    <td>${esc(row.assigned || 0)}</td>
                    <td>${esc(row.unassigned || 0)}</td>
                </tr>`).join("")}
            </tbody>`;
    } catch (error) {
        ui.statsSummary.textContent = `Ошибка статистики: ${error.message}`;
        ui.statsTable.innerHTML = "<tbody><tr><td>Ошибка загрузки.</td></tr></tbody>";
    }
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
    let presentationRows = filterPresentationRowsByViewMode(buildPresentationRows());
    const classSortMatch = /^classHours:(.+)$/.exec(state.sortField || "");
    if (classSortMatch) {
        const targetClass = classSortMatch[1];
        const assignments = assignmentsForBuilding(selectedBuilding);
        presentationRows = presentationRows
            .filter((row) => (row.rowsByClassAll?.[targetClass] || []).length > 0)
            .sort((a, b) => {
                const aRows = a.rowsByClassAll?.[targetClass] || [];
                const bRows = b.rowsByClassAll?.[targetClass] || [];
                const aTeacher = String(a.teacherName || "").trim();
                const bTeacher = String(b.teacherName || "").trim();
                const aAssignedTeachers = aRows.map((r) => String(assignments[apiKeyOfRow(r)] || "").trim()).filter(Boolean);
                const bAssignedTeachers = bRows.map((r) => String(assignments[apiKeyOfRow(r)] || "").trim()).filter(Boolean);
                const aActive = aTeacher && aAssignedTeachers.includes(aTeacher);
                const bActive = bTeacher && bAssignedTeachers.includes(bTeacher);
                const aUnassigned = aAssignedTeachers.length === 0;
                const bUnassigned = bAssignedTeachers.length === 0;

                const rank = (active, unassigned) => (active ? 0 : (unassigned ? 1 : 2));
                const rankDiff = rank(aActive, aUnassigned) - rank(bActive, bUnassigned);
                if (rankDiff !== 0) return rankDiff;

                return String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru")
                    || String(a.teacherName || "").localeCompare(String(b.teacherName || ""), "ru");
            });
    }
    const { errorCount, errors } = collectLoadIssues(presentationRows, classes);
    currentErrorList = errors;
    currentErrorIndex = -1;
    refreshHealthCounters();

    const headMain = document.createElement("tr");
    headMain.className = "load-main-head";
    headMain.innerHTML = `
        <th rowspan="2">Предмет</th>
        <th rowspan="2">Педагог</th>
        <th rowspan="2">Часы по предмету</th>
        <th rowspan="2">Часов в корпусе</th>
        <th rowspan="2">Всего часов в комплексе</th>
        <th colspan="${Math.max(classes.length, 1)}">
            <div class="load-head-actions">
                <span><strong>Ошибки: <button type="button" class="error-count-btn" data-head-error-info="1">${errorCount}</button></strong></span>
                <button type="button" class="head-action-btn" data-head-save="1">Сохранить нагрузку корпуса</button>
                <button type="button" class="head-action-btn" data-head-next-error="1">Перейти к ошибке</button>
            </div>
        </th>
    `;
    ui.tableHead.appendChild(headMain);
    const headSaveBtn = headMain.querySelector('[data-head-save="1"]');
    const headNextErrorBtn = headMain.querySelector('[data-head-next-error="1"]');
    const headErrorInfoBtn = headMain.querySelector('[data-head-error-info="1"]');
    headSaveBtn?.addEventListener("click", () => ui.saveBuildingBtn?.click());
    headNextErrorBtn?.addEventListener("click", () => ui.nextErrorBtn?.click());
    headErrorInfoBtn?.addEventListener("click", showErrorsPopup);

    const headClasses = document.createElement("tr");
    headClasses.className = "load-class-head";
    headClasses.innerHTML = classes.length
        ? classes.map((className) => `<th><button type="button" class="class-sort-btn ${state.sortField === `classHours:${className}` ? "active" : ""}" data-class-sort="${esc(className)}">${esc(className)}</button></th>`).join("")
        : `<th>—</th>`;
    ui.tableHead.appendChild(headClasses);

    const continuityStatusIndex = buildContinuityStatusIndex(referenceDate);
    const buildingAssignments = assignmentsForBuilding(selectedBuilding);
    const buildingPlans = futurePlansForBuilding(selectedBuilding);
    latestPresentationRows = presentationRows;
    presentationRows.forEach((row, index) => {
        const tr = document.createElement("tr");
        tr.dataset.rowKey = rowStableKey(row);
        if (rowHasPlannedLoadChange(row, referenceDate)) {
            tr.classList.add("load-change-row");
        }
        const listId = `teacher-list-${row.teacherRowId}`;

        tr.innerHTML = `
            <td>
                <div class="subject-cell">
                    <span class="subject-cell-name">${esc(row.displaySubjectName || row.subjectName)}</span>
                    ${index === 0 || presentationRows[index - 1].subjectKey !== row.subjectKey ? `<button class="inline-plus" type="button" data-plus-subject="${esc(row.subjectKey)}" data-plus-after="${esc(row.teacherRowId)}" title="Добавить строку педагога">+</button>` : ""}
                </div>
            </td>
            <td class="${isDismissedTeacher(row.teacherName) ? "dismissal-row" : ""}">
                <input type="text" class="teacher-input" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" list="${listId}" value="${esc(row.teacherName)}" placeholder="ФИО педагога">
                <datalist id="${listId}"></datalist>
                ${isDismissedTeacher(row.teacherName) ? `<div class="dismissal-note">Увольнение с ${esc(dismissalDateOfTeacher(row.teacherName))}</div>` : ""}${(!teacherExists(row.teacherName) && row.teacherName) ? `<div class="dismissal-note">Ошибка: педагог отсутствует в справочнике</div>` : ""}
            </td>
            <td><strong>${esc(row.subjectHours || 0)} ч</strong></td>
            <td><strong>${esc(row.buildingHours)} ч</strong></td>
            <td><strong>${esc(row.complexHours || 0)} ч</strong></td>
            ${classes.map((className) => {
                const curriculumRow = row.rowsByClass[className];
                if (!curriculumRow) return "<td></td>";
                const classRows = row.rowsByClassAll?.[className] || [curriculumRow];
                const hoursTotal = classPeriodText(classRows);
                const assignedTeachers = classRows.map((item) => String(buildingAssignments[apiKeyOfRow(item)] || "").trim()).filter(Boolean);
                const rowTeacher = String(row.teacherName || "").trim();
                const hasAnyAssigned = assignedTeachers.length > 0;
                const hasRowTeacherAssigned = rowTeacher ? assignedTeachers.includes(rowTeacher) : false;
                const plans = classRows.map((item) => buildingPlans[apiKeyOfRow(item)]).filter(Boolean);
                const isPlanned = plans.some((plan) => plan.targetTeacher === rowTeacher && plan.fromDate > referenceDate);
                const isTransferOut = plans.some((plan) => plan.previousTeacher === rowTeacher && plan.fromDate > referenceDate);
                const isActive = hasRowTeacherAssigned;
                const isMuted = rowTeacher !== "" && !hasRowTeacherAssigned && !isPlanned && !isTransferOut;
                const isUnassigned = !hasAnyAssigned && !isPlanned;
                const persistedContinuityStates = classRows
                    .map((item) => continuityStatusIndex.get(continuityStatusKey(
                        item.numberSchoolBuilding,
                        item.className,
                        item.subjectName,
                        item.educationLevel,
                        continuityGroupName(item),
                        rowTeacher
                    )) || "")
                    .filter(Boolean);
                const hasPersistedContinuityOk = persistedContinuityStates.includes("OK");
                const hasContinuityOk = isActive && hasPersistedContinuityOk;
                const classesForCell = [
                    "hour-pill",
                    isActive ? "active" : "",
                    isMuted ? "muted" : "",
                    isUnassigned ? "unassigned" : "",
                    isPlanned ? "planned" : "",
                    isTransferOut ? "transfer-out" : "",
                    !isPlanned && !isTransferOut && hasContinuityOk ? "continuity-ok" : ""
                ].filter(Boolean).join(" ");
                return `<td><button type="button" class="${classesForCell}" data-class-cell="1" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" data-class-name="${esc(className)}">${esc(hoursTotal)}</button></td>`;
            }).join("")}
        `;

        ui.tableBody.appendChild(tr);

        const teacherInput = tr.querySelector(".teacher-input");
        const listEl = tr.querySelector("datalist");
        updateDatalistOptions(listEl, teacherInput.value || "");
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
        if (Number(row.plannedHours || 0) <= 0) return null;
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
            teacherId: teacherIdForName(fioTeacher),
            numberSchoolBuilding: buildingGroupCode(row.numberSchoolBuilding || selectedBuilding),
            subjectName: row.subjectName,
            subjectId: subjectIdForRow(row),
            className: row.className,
            classId: classIdForRow(row),
            metaGroupId: metaGroupIdForRow(row),
            load: Number(row.plannedHours || 0),
            groupNameEducationalPlan: row.__groupIndex ? `Группа ${row.__groupIndex}` : null,
            groupLoad: row.__groupIndex ? Number(row.plannedHours || 0) : null,
            educationLevel: row.educationLevel,
            studyPeriod: rowStudyPeriod(row),
            loadFromDate: rowLoadFromDate,
            loadToDate: rowLoadToDate,
            continuityStatus: manualPeriod?.continuityStatus || null
        };
    }).filter(Boolean);

    Object.entries(plans).forEach(([apiKey, plan]) => {
        const row = expandedRowsForSelectedBuilding().find((r) => apiKeyOfRow(r) === apiKey);
        if (!row || Number(row.plannedHours || 0) <= 0) return;
        payload.push({
            fioTeacher: plan.targetTeacher,
            teacherId: teacherIdForName(plan.targetTeacher),
            numberSchoolBuilding: buildingGroupCode(row.numberSchoolBuilding || selectedBuilding),
            subjectName: row.subjectName,
            subjectId: subjectIdForRow(row),
            className: row.className,
            classId: classIdForRow(row),
            metaGroupId: metaGroupIdForRow(row),
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
            String(item.classId || ""),
            String(item.metaGroupId || ""),
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

    try {
        const scope = manualLoadScopeForAccess(selectedBuilding);
        const result = await api("/api/manual-load/bulk", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify({
                scopeType: scope.scopeType,
                numberSchoolBuilding: scope.numberSchoolBuilding,
                campusAddress: scope.campusAddress || null,
                schoolBuildingId: scope.schoolBuildingId || null,
                classIds: scope.classIds,
                rows: finalPayload
            })
        });
        print({ saved: result.length, uniqueRequested: finalPayload.length, building: selectedBuilding });
        manualRows = result || manualRows;
        complexManualRows = await api("/api/manual-load");
        state.futurePlansByBuilding[selectedBuilding] = {};
        invalidateBuildingDataCache(selectedBuilding);
        invalidateTeacherHourIndexesCache();
        markDirty(false);
        scheduleRenderTable();
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

async function exportFullLoadWorkbook() {
    try {
        const scopedPath = window.withAcademicYear ? window.withAcademicYear("/api/manual-load/export-full") : "/api/manual-load/export-full";
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
        a.download = match ? decodeURIComponent(match[1]) : "full-load-export.xlsx";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        print({ status: "full-exported" });
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
    const initialBuildingCandidate = normalizeBuildingAccessCode(selectedBuilding) || restoreSelectedBuilding();
    const initialCurriculumQuery = scopedBuildingQuery(initialBuildingCandidate, false);
    const initialManualQuery = scopedBuildingQuery(initialBuildingCandidate, true);
    const initialScopedDataPromise = Promise.all([
        api(`/api/curriculum${initialCurriculumQuery}`),
        api(`/api/manual-load${initialManualQuery}`)
    ]);
    const complexManualPromise = api("/api/manual-load");
    const allCurriculumPromise = api("/api/curriculum");

    const [teachers, buildingRows, organizationalBuildingRows, classRows, periodSettings, yearResolve, subjectRows, allCurriculumRows] = await Promise.all([
        api("/api/teachers"),
        api("/api/buildings"),
        api("/api/building-groups"),
        api("/api/classroom-leadership"),
        api("/api/settings/study-periods"),
        api("/api/academic-years/active"),
        api("/api/subjects"),
        allCurriculumPromise
    ]);

    const buildingGroups = new Map();
    (organizationalBuildingRows || []).forEach((group) => {
        const code = normalizeBuildingCode(group?.code || group?.name);
        if (!code) return;
        buildingGroups.set(code, {
            code,
            name: String(group?.name || group?.code || "").trim() || code,
            addresses: [],
            addressRows: []
        });
    });
    const appendAddress = (entry, value) => {
        const cleaned = String(value || "").trim();
        if (!cleaned) return;
        const key = normalizeBuildingAccessCode(cleaned);
        if (entry.addresses.some((item) => normalizeBuildingAccessCode(item) === key)) return;
        entry.addresses.push(cleaned);
    };
    (buildingRows || []).forEach((b) => {
        const code = normalizeBuildingCode(b.code || b.name);
        if (!code) return;
        const existing = buildingGroups.get(code) || { code, name: String(b.name || "").trim() || code, addresses: [], addressRows: [] };
        existing.name = String(existing.name || b.name || "").trim() || code;
        appendAddress(existing, b.address);
        existing.addressRows.push(b);
        buildingGroups.set(code, existing);
    });

(classRows || []).forEach((r) => {
    const organizationalSp = normalizeBuildingCode(r.numberSchoolBuilding);
    const address = String(r.campusAddress || "").trim();
    const schoolBuildingId = r.schoolBuildingId == null ? null : Number(r.schoolBuildingId);

    if (!organizationalSp || !address || schoolBuildingId == null) return;

    const existing = buildingGroups.get(organizationalSp) || {
        code: organizationalSp,
        name: organizationalSp,
        addresses: [],
        addressRows: []
    };

    appendAddress(existing, address);

    const hasSameSite = (existing.addressRows || []).some((row) =>
        Number(row?.id) === schoolBuildingId
        && normalizeBuildingAccessCode(row?.address) === normalizeBuildingAccessCode(address)
    );

    if (!hasSameSite) {
        existing.addressRows.push({
            id: schoolBuildingId,
            address
        });
    }

    buildingGroups.set(organizationalSp, existing);
});

    mergeMetaGroupAddressScopeOptions(buildingGroups, allCurriculumRows || [], buildingRows || []);

    buildings = [];
    [...buildingGroups.values()]
        .sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"))
        .forEach((group) => {
            const firstAddress = group.addresses[0] || "";
            if (group.addresses.length !== 1) {
                buildings.push({
                    code: group.code,
                    value: group.code,
                    name: group.name,
                    address: firstAddress,
                    addresses: group.addresses,
                    scope: "group"
                });
            }
            const seenScopes = new Set();
            (group.addressRows || []).forEach((site) => {
                const schoolBuildingId = site?.id ?? site?.schoolBuildingId ?? null;
                const address = String(site?.address || "").trim();
                const addressKey = normalizeBuildingAccessCode(address);
                const value = schoolBuildingId != null ? `${group.code}::${schoolBuildingId}` : `${group.code}|${addressKey}`;
                if (seenScopes.has(value) || !address) return;
                seenScopes.add(value);
                buildings.push({
                    code: group.code,
                    value,
                    name: group.name,
                    address,
                    addresses: [address],
                    scope: "address",
                    schoolBuildingId
                });
            });
            group.addresses.forEach((address) => {
                const addressKey = normalizeBuildingAccessCode(address);
                const value = `${group.code}|${addressKey}`;
                if (seenScopes.has(value)) return;
                const site = (group.addressRows || []).find((row) => normalizeBuildingAccessCode(row?.address) === addressKey) || null;
                if (site?.id != null || site?.schoolBuildingId != null) return;
                seenScopes.add(value);
                buildings.push({
                    code: group.code,
                    value,
                    name: group.name,
                    address,
                    addresses: [address],
                    scope: "address",
                    schoolBuildingId: null
                });
            });
        });

    const rememberedBuilding = restoreSelectedBuilding();
    if (rememberedBuilding && buildings.some((row) => (row.value || row.code) === rememberedBuilding)) {
        selectedBuilding = rememberedBuilding;
    }
    if (!selectedBuilding || !buildings.some((row) => (row.value || row.code) === selectedBuilding)) {
        selectedBuilding = preferredBuildingCode(buildings);
    }
    if (selectedBuilding !== ARCHIVE_BUILDING_CODE && !canEditSelectedBuildingLoad()) {
        const preferred = preferredBuildingCode(buildings);
        if (preferred) selectedBuilding = preferred;
    }
    rememberSelectedBuilding(selectedBuilding);

    let curriculum;
    let manual;
    let complexManual;
    const finalCurriculumQuery = scopedBuildingQuery(selectedBuilding, false);
    const finalManualQuery = scopedBuildingQuery(selectedBuilding, true);
    if (initialCurriculumQuery === finalCurriculumQuery && initialManualQuery === finalManualQuery) {
        [curriculum, manual] = await initialScopedDataPromise;
    } else {
        [curriculum, manual] = await Promise.all([
            api(`/api/curriculum${finalCurriculumQuery}`),
            api(`/api/manual-load${finalManualQuery}`)
        ]);
    }

    complexManual = await complexManualPromise;
    curriculumRows = curriculum || [];
    manualRows = manual || [];
    complexManualRows = complexManual || [];
    invalidateBuildingDataCache();
    buildingDataCache.set(buildingCacheKey(selectedBuilding), { ts: Date.now(), curriculum: curriculumRows, manual: manualRows, complexManual: complexManualRows });
    teacherDirectory = teachers || [];
    teacherNames = sortRu(Array.from(new Set(teacherDirectory.map((t) => String(t.fioTeacher || "").trim()).filter(Boolean))));
    teacherDirectoryByName = new Map(
        teacherDirectory
            .map((teacher) => [String(teacher.fioTeacher || "").trim().toLowerCase(), teacher])
            .filter(([name]) => Boolean(name))
    );
    classroomRows = classRows || [];
    studyPeriodSettings = periodSettings || [];
    subjectCatalog = subjectRows || [];
    sourceRevision += 1;
    invalidateDerivedCache();
    invalidateTeacherHourIndexesCache();
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
    ui.clearBuildingLoadBtn?.addEventListener("click", async () => {
        if (!currentAuthUser()?.admin) return;
        if (!selectedBuilding || selectedBuilding === ARCHIVE_BUILDING_CODE) {
            print({ error: "Выберите корпус с активной нагрузкой." });
            return;
        }
        if (isAddressScopedBuilding(selectedBuilding)) {
            print({
                warning: "Очистка всей нагрузки для отдельной площадки временно недоступна: на одной площадке могут заниматься классы разных СП."
            });
            return;
        }
        const confirmed = confirm(`Удалить всю нагрузку корпуса ${selectedBuilding} в текущем учебном году?`);
        if (!confirmed) return;
        try {
            const scope = manualLoadScopeForAccess(selectedBuilding);
            const params = new URLSearchParams();
            params.set("numberSchoolBuilding", scope.numberSchoolBuilding);
            params.set("scopeType", scope.scopeType);
            if (scope.schoolBuildingId) params.set("schoolBuildingId", String(scope.schoolBuildingId));
            if (scope.campusAddress) params.set("campusAddress", scope.campusAddress);
            await api(`/api/manual-load?${params.toString()}`, { method: "DELETE" });
            await refreshSourceData();
            print({ status: `Нагрузка корпуса ${selectedBuilding} удалена.` });
        } catch (error) {
            print({ error: error.message });
        }
    });
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
                    assignments[apiKey] = takeover.previousTeacher;
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
    ui.subgroupDrawerClose?.addEventListener("click", closeSubgroupDrawer);
    ui.subgroupDrawerBackdrop?.addEventListener("click", closeSubgroupDrawer);
    ui.subgroupDrawerApply?.addEventListener("click", applySubgroupDrawerAssignments);


    ui.refreshLoadBtn.addEventListener("click", () => {
        refreshSourceData()
            .then(() => print({ status: "Синхронизировано с учебным планом" }))
            .catch((error) => print({ error: error.message }));
    });

    ui.exportLoadBtn?.addEventListener("click", exportLoadWorkbook);
    ui.exportFullLoadBtn?.addEventListener("click", exportFullLoadWorkbook);
    ui.importLoadBtn?.addEventListener("click", () => ui.importLoadFile?.click());
    ui.importLoadFile?.addEventListener("change", async () => {
        const file = ui.importLoadFile.files?.[0];
        if (!file) return;
        const confirmed = confirm("Импорт нагрузки принимает только свежий шаблон с CLASS_ID, META_GROUP_ID, TEACHER_ID и SUBJECT_ID и заменит текущие назначения по корпусам из файла. Продолжить?");
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

    ui.tableHead?.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-class-sort]");
        if (!button) return;
        const className = button.dataset.classSort;
        const next = `classHours:${className}`;
        if (state.sortField === next) {
            state.sortField = "subjectArea";
            state.sortDirection = "asc";
            ui.sortField.value = "subjectArea";
            ui.sortDirection.value = "asc";
        } else {
            state.sortField = next;
            state.sortDirection = "desc";
            ui.sortDirection.value = "desc";
            ui.sortField.value = "subject";
        }
        state.forceResort = true;
        scheduleRenderTable();
    });

    ui.tableBody?.addEventListener("input", (event) => {
        const teacherInput = event.target.closest(".teacher-input");
        if (!teacherInput) return;
        const tr = teacherInput.closest("tr");
        const listEl = tr?.querySelector("datalist");
        if (listEl) updateDatalistOptions(listEl, teacherInput.value || "");
    });
    ui.tableBody?.addEventListener("focusin", (event) => {
        const teacherInput = event.target.closest(".teacher-input");
        if (!teacherInput) return;
        const tr = teacherInput.closest("tr");
        const listEl = tr?.querySelector("datalist");
        if (listEl) updateDatalistOptions(listEl, "");
    });

    ui.tableBody?.addEventListener("change", (event) => {
        const target = event.target;
        const periodInput = target.closest(".period-input");
        if (periodInput) {
            const subjectKey = periodInput.dataset.subjectKey;
            const rowIdValue = periodInput.dataset.rowId;
            const rowElFrom = ui.tableBody.querySelector(`.period-from[data-subject-key="${subjectKey}"][data-row-id="${rowIdValue}"]`);
            const rowElTo = ui.tableBody.querySelector(`.period-to[data-subject-key="${subjectKey}"][data-row-id="${rowIdValue}"]`);
            setPeriodForRow(subjectKey, rowIdValue, rowElFrom?.value || "", rowElTo?.value || "");
            return;
        }
        const teacherInput = target.closest(".teacher-input");
        if (teacherInput) {
            applyTeacherSelection(teacherInput.dataset.subjectKey, teacherInput.dataset.rowId, teacherInput);
        }
    });

    // Не фиксируем значение на blur: при выборе из datalist некоторые браузеры
    // сначала ставят выбранное значение, а затем присылают промежуточный blur,
    // из-за чего ФИО может тут же очищаться. Фиксация остаётся на change/Enter.

    ui.tableBody?.addEventListener("keydown", (event) => {
        const teacherInput = event.target.closest(".teacher-input");
        if (!teacherInput || event.key !== "Enter") return;
        event.preventDefault();
        applyTeacherSelection(teacherInput.dataset.subjectKey, teacherInput.dataset.rowId, teacherInput);
    });

    ui.tableBody?.addEventListener("click", (event) => {
        const plusButton = event.target.closest("button[data-plus-subject]");
        if (plusButton) {
            addTeacherRow(plusButton.dataset.plusSubject, plusButton.dataset.plusAfter);
            return;
        }
        const classButton = event.target.closest("button[data-class-cell]");
        if (!classButton) return;
        const row = latestPresentationRows.find((entry) => entry.subjectKey === classButton.dataset.subjectKey && entry.teacherRowId === classButton.dataset.rowId);
        if (!row) return;
        onClassCellClick(row, classButton.dataset.className, classButton);
    });

    ui.buildingSelect?.addEventListener("change", () => {
        selectedBuilding = ui.buildingSelect.value;
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
}

async function init() {
    await waitForAuthContext();
    bindEvents();
    updateAdminOnlyActions();
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

document.addEventListener('DOMContentLoaded', () => {
    const toggleBtn = document.getElementById('toggle-load-controls-btn');
    const extraControls = document.getElementById('load-extra-controls');

    if (!toggleBtn || !extraControls) {
        return;
    }

    toggleBtn.addEventListener('click', () => {
        const willOpen = extraControls.hidden;

        extraControls.hidden = !willOpen;
        toggleBtn.setAttribute('aria-expanded', String(willOpen));
        toggleBtn.textContent = willOpen ? 'Скрыть настройки' : 'Показать настройки';
    });
});
