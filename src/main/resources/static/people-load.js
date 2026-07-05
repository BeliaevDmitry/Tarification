const ui = {
    buildingSelect: document.getElementById("people-load-building-select"),
    refreshBtn: document.getElementById("people-load-refresh-btn"),
    exportFullLoadBtn: document.getElementById("export-full-load-btn"),
    exportConsolidatedLoadBtn: document.getElementById("export-consolidated-load-btn"),
    exportSubjectLoadBtn: document.getElementById("export-subject-load-btn"),
    exportFullLoadSalaryBtn: document.getElementById("export-full-load-salary-btn"),
    exportSalaryOneLoadBtn: document.getElementById("export-salary-one-load-btn"),
    exportDepartmentLoadBtn: document.getElementById("export-department-load-btn"),
    summary: document.getElementById("people-load-summary"),
    table: document.getElementById("people-load-table"),
    mainTab: document.getElementById("people-load-main-tab"),
    primaryTab: document.getElementById("people-load-primary-tab"),
    mainPanel: document.getElementById("people-load-main-panel"),
    primaryPanel: document.getElementById("people-load-primary-panel"),
    determinePrimarySubjectsBtn: document.getElementById("determine-primary-subjects-btn"),
    managePrimarySubjectsBtn: document.getElementById("manage-primary-subjects-btn"),
    primarySubjectSummary: document.getElementById("primary-subject-summary"),
    primarySubjectTeachersTable: document.getElementById("primary-subject-teachers-table"),
    primarySubjectRulesDialog: document.getElementById("primary-subject-rules-dialog"),
    primarySubjectRulesBody: document.getElementById("primary-subject-rules-body"),
    addPrimarySubjectRuleBtn: document.getElementById("add-primary-subject-rule-btn"),
    newPrimarySubjectName: document.getElementById("new-primary-subject-name"),
    newPrimarySubjectRuleType: document.getElementById("new-primary-subject-rule-type"),
    newPrimarySubjectRuleValue: document.getElementById("new-primary-subject-rule-value")
};

const state = {
    buildings: [],
    manualRows: [],
    classes: [],
    teachers: [],
    subjects: [],
    coefficients: [],
    primarySubjectAssignments: [],
    primarySubjectRules: [],
    classSizeSource: "AIS",
    classSizeByClass: new Map(),
    groupCoefficientSubjects: [],
    groupCoefficientSubjectIds: new Set(),
    groupCoefficientSubjectNames: new Set(),
    studentHourRate: 37
};

function withYear(path) {
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

async function api(path) {
    const response = await fetch(withYear(path));
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    return response.json();
}

async function apiRequest(path, options = {}) {
    const response = await fetch(withYear(path), {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    if (response.status === 204) return null;
    return response.json();
}

let tableFitFrame = 0;

function fitPeopleLoadTables() {
    if (tableFitFrame) return;
    tableFitFrame = window.requestAnimationFrame(() => {
        tableFitFrame = 0;
        document.querySelectorAll(".people-load-wrap").forEach((wrap) => {
            const panel = wrap.closest("section");
            if (panel?.hidden) return;
            const rect = wrap.getBoundingClientRect();
            const available = Math.floor(window.innerHeight - rect.top - 18);
            wrap.style.maxHeight = `${Math.max(220, available)}px`;
        });
    });
}

function normalizeText(value) {
    return String(value || "")
        .replace(/\s+/g, " ")
        .trim();
}

function normalizeKey(value) {
    return normalizeText(value).toUpperCase();
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

function normalizeBuildingCode(value) {
    return normalizeBuildingAccessCode(value);
}

function buildingGroupCode(value) {
    return normalizeBuildingAccessCode(value).split("|")[0] || "";
}

function buildingAddressToken(value) {
    const parts = normalizeBuildingAccessCode(value).split("|");
    return parts.length > 1 ? parts.slice(1).join("|") : "";
}

function classKey(className, buildingCode) {
    return `${normalizeKey(className)}|${buildingGroupCode(buildingCode)}`;
}

function classSizeLookupKey(className) {
    return normalizeText(className)
        .toLowerCase()
        .replaceAll("ё", "е")
        .replace(/[–—]/g, "-")
        .replace(/\s+/g, "");
}

function applyClassSizeResponse(response) {
    state.classSizeSource = response?.source || "AIS";
    state.classSizeByClass = new Map();
    (response?.rows || []).forEach((row) => {
        const key = classSizeLookupKey(row.className);
        if (!key) return;
        const manual = Number(row.manualStudents);
        const ais = Number(row.aisStudents);
        const value = state.classSizeSource === "MANUAL"
            ? (Number.isFinite(manual) && manual > 0 ? manual : ais)
            : ais;
        if (Number.isFinite(value) && value > 0) {
            state.classSizeByClass.set(key, value);
        }
    });
}

function classSizeFor(className) {
    return state.classSizeByClass?.get(classSizeLookupKey(className)) || 30;
}


function rowClassEntry(row) {
    if (row?.classId != null) {
        const byId = state.classMapById?.get(String(row.classId));
        if (byId) return byId;
    }
    const byGroup = classKey(row.className, row.numberSchoolBuilding);
    const exact = state.classMapByGroup?.get(byGroup);
    if (exact) return exact;
    return state.classMapByName?.get(normalizeKey(row.className)) || null;
}

function rowAddressToken(row) {
    const address = rowAddressLabel(row);
    return address ? normalizeBuildingAccessCode(address) : "";
}

function rowAddressLabel(row) {
    const classEntry = rowClassEntry(row);
    return normalizeText(classEntry?.campusAddress || "");
}

function rowMatchesBuildingAccess(row, accessCode) {
    const selectedGroup = buildingGroupCode(accessCode);
    if (!selectedGroup || buildingGroupCode(row.numberSchoolBuilding) !== selectedGroup) {
        return false;
    }
    const selectedAddress = buildingAddressToken(accessCode);
    if (!selectedAddress) return true;
    const rowAddress = rowAddressToken(row);
    if (rowAddress === selectedAddress) return true;
    const knownAddresses = addressesForBuildingCode(selectedGroup).map(normalizeBuildingAccessCode).filter(Boolean);
    return knownAddresses.length === 1 && knownAddresses[0] === selectedAddress;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function addressesForBuildingCode(buildingCode) {
    const normalizedCode = normalizeBuildingCode(buildingCode);
    if (!normalizedCode) return [];
    const option = (state.buildings || []).find((building) => normalizeBuildingCode(building.code) === normalizedCode && Array.isArray(building.addresses));
    return option?.addresses || [];
}

function buildingLabel(building) {
    if (!building) return "";
    const base = normalizeText(building.name || building.code);
    if (building.scope === "address") {
        return `${base} — ${building.address || "адрес не указан"}`;
    }
    const count = building.addresses?.length || 0;
    return `${base} — все адреса${count ? ` (${count})` : ""}`;
}

function appendUniqueAddress(group, value) {
    const address = normalizeText(value);
    if (!address) return;
    const key = normalizeBuildingAccessCode(address);
    if (group.addresses.some((known) => normalizeBuildingAccessCode(known) === key)) return;
    group.addresses.push(address);
}

function buildBuildingOptions(rawBuildings, classRows = []) {
    const grouped = new Map();
    for (const building of rawBuildings || []) {
        const code = normalizeBuildingCode(building.code || building.name);
        if (!code) continue;
        const group = grouped.get(code) || {
            code,
            name: normalizeText(building.name || building.code || code) || code,
            addresses: []
        };
        group.name = normalizeText(group.name || building.name || code) || code;
        appendUniqueAddress(group, building.address);
        grouped.set(code, group);
    }
    for (const classRow of classRows || []) {
        const code = normalizeBuildingCode(classRow.numberSchoolBuilding);
        if (!code) continue;
        const group = grouped.get(code) || {
            code,
            name: `${code} (из классов)`,
            addresses: []
        };
        appendUniqueAddress(group, classRow.campusAddress);
        grouped.set(code, group);
    }

    const options = [];
    Array.from(grouped.values())
        .sort((a, b) => String(a.code).localeCompare(String(b.code), "ru", { numeric: true }))
        .forEach((group) => {
            group.addresses.sort((a, b) => a.localeCompare(b, "ru"));
            if (group.addresses.length !== 1) {
                options.push({
                    code: group.code,
                    value: group.code,
                    name: group.name,
                    address: group.addresses[0] || "",
                    addresses: group.addresses,
                    scope: "group"
                });
            }
            group.addresses.forEach((address) => {
                options.push({
                    code: group.code,
                    value: `${group.code}|${normalizeBuildingAccessCode(address)}`,
                    name: group.name,
                    address,
                    addresses: [address],
                    scope: "address"
                });
            });
        });
    return options;
}

function fillBuildingSelect() {
    if (!ui.buildingSelect) return;
    const previous = ui.buildingSelect.value;
    ui.buildingSelect.innerHTML = "";
    state.buildings.forEach((building) => {
        const option = document.createElement("option");
        option.value = building.value;
        option.textContent = buildingLabel(building);
        ui.buildingSelect.appendChild(option);
    });
    if (previous && state.buildings.some((building) => building.value === previous)) {
        ui.buildingSelect.value = previous;
    }
}

function periodLabel(row) {
    if (row.studyPeriod === "H1" || row.studyPeriod === "FIRST_HALF") return "1П";
    if (row.studyPeriod === "H2" || row.studyPeriod === "SECOND_HALF") return "2П";
    return "ГОД";
}

function loadValue(row) {
    const value = Number(row.groupLoad ?? row.load ?? 0);
    return Number.isFinite(value) ? value : 0;
}

function addHours(totals, row) {
    const load = loadValue(row);
    if (!load) return;
    if (row.studyPeriod === "H1" || row.studyPeriod === "FIRST_HALF") {
        totals.h1 += load;
    } else if (row.studyPeriod === "H2" || row.studyPeriod === "SECOND_HALF") {
        totals.h2 += load;
    } else {
        totals.year += load;
    }
}

function effectiveHalfHours(total) {
    return {
        h1: (total.year || 0) + (total.h1 || 0),
        h2: (total.year || 0) + (total.h2 || 0)
    };
}

function formatHours(total) {
    const { h1, h2 } = effectiveHalfHours(total);
    return h1 === h2 ? String(h1) : `${h1} | ${h2}`;
}

function formatScopedTotalHours(scoped, total) {
    const scopedHours = effectiveHalfHours(scoped);
    const totalHours = effectiveHalfHours(total);
    if (scopedHours.h1 === totalHours.h1 && scopedHours.h2 === totalHours.h2) {
        return formatHours(total);
    }
    return `${formatHours(scoped)} / ${formatHours(total)}`;
}

function fioKey(value) {
    return normalizeKey(value || "Вакансия");
}

function childrenCount(row) {
    const classSize = classSizeFor(row.className);
    const name = normalizeText(row.groupNameEducationalPlan || "").toLowerCase();
    if (!name) return classSize;
    const firstGroupSize = Math.ceil(classSize / 2);
    const secondGroupSize = classSize - firstGroupSize;
    if (name.includes("2")) return secondGroupSize;
    if (name.includes("1")) return firstGroupSize;
    return classSize;
}

function salaryPermission() {
    const user = window.tarificationAuth || {};
    const permissions = window.tarificationTabPermissions || {};
    return {
        canView: Boolean(user.admin || permissions.LOAD_SALARY?.canView),
        canExport: Boolean(user.admin || permissions.LOAD_SALARY?.canExport)
    };
}

function educationStageByClassName(className) {
    const match = normalizeText(className).match(/\d+/);
    const grade = match ? Number(match[0]) : NaN;
    if (!Number.isFinite(grade)) return "";
    if (grade >= 1 && grade <= 4) return "NOO";
    if (grade >= 5 && grade <= 9) return "OOO";
    if (grade >= 10 && grade <= 11) return "SOO";
    return "";
}

function subjectLevelCoefficientKey(subjectName, className) {
    return `${normalizeKey(subjectName)}|${educationStageByClassName(className)}`;
}

function subjectCoefficient(subjectName, className) {
    return state.subjectCoefficientByKey?.get(subjectLevelCoefficientKey(subjectName, className)) || 1;
}

function rowSalary(row) {
    return rowSalaryDetails(row).hoursSalary;
}

function rowGroupCoefficient(row) {
    if (!normalizeText(row.groupNameEducationalPlan || "")) return 1;
    const subjectId = row.subjectId == null ? "" : String(row.subjectId);
    const enabled = subjectId
        ? state.groupCoefficientSubjectIds?.has(subjectId)
        : state.groupCoefficientSubjectNames?.has(normalizeKey(row.subjectName));
    return enabled ? 25 / Math.max(childrenCount(row), 1) : 1;
}

function rowSalaryDetails(row) {
    const children = Math.max(childrenCount(row), 1);
    const hours = Math.max(loadValue(row), 0);
    const subjectCoef = subjectCoefficient(row.subjectName, row.className);
    const groupCoef = rowGroupCoefficient(row);
    const value = state.studentHourRate * children * hours * (34 / 12) * subjectCoef * groupCoef;
    return { subjectCoef, groupCoef, hoursSalary: value };
}

function classLeadershipSalary(fio) {
    const key = fioKey(fio);
    return (state.leadershipByTeacher?.get(key) || [])
        .reduce((sum, entry) => sum + 500 * classSizeFor(entry.className) + 5000, 0);
}

function isFirstHalfSalaryRow(row) {
    return row.studyPeriod !== "H2" && row.studyPeriod !== "SECOND_HALF";
}

function teacherSalary(fio, allTeacherRows) {
    const hours = allTeacherRows
        .filter(isFirstHalfSalaryRow)
        .reduce((sum, row) => sum + rowSalary(row), 0);
    const leadership = classLeadershipSalary(fio);
    return { hours, leadership, total: hours + leadership };
}

function formatMoney(value) {
    return new Intl.NumberFormat("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value || 0);
}

function formatCoefficient(value) {
    const parsed = Number(value ?? 1);
    const safe = Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
    const text = safe.toFixed(4);
    return text.replace(/0+$/, "").replace(/\.$/, "");
}

function teacherLeadershipClasses(fio) {
    const key = fioKey(fio);
    return (state.leadershipByTeacher?.get(key) || [])
        .map((entry) => normalizeText(entry.className))
        .filter(Boolean)
        .join(", ");
}

function rowBuildingLabel(row) {
    const building = buildingGroupCode(row.numberSchoolBuilding);
    const address = rowAddressLabel(row);
    return [building, address].filter(Boolean).join("\n");
}

function fioHtml(fio) {
    return escapeHtml(normalizeText(fio)).replace(/\s+/g, "<br>");
}

function renderTable() {
    const selected = ui.buildingSelect?.value || state.buildings[0]?.value || "";
    const selectedRows = state.manualRows.filter((row) => rowMatchesBuildingAccess(row, selected));
    const selectedTeacherKeys = new Set(selectedRows.map((row) => fioKey(row.fioTeacher)));
    const displayRows = state.manualRows
        .filter((row) => selectedTeacherKeys.has(fioKey(row.fioTeacher)))
        .sort((a, b) => {
            const fioCompare = normalizeText(a.fioTeacher).localeCompare(normalizeText(b.fioTeacher), "ru");
            if (fioCompare) return fioCompare;
            const aSelected = rowMatchesBuildingAccess(a, selected) ? 0 : 1;
            const bSelected = rowMatchesBuildingAccess(b, selected) ? 0 : 1;
            if (aSelected !== bSelected) return aSelected - bSelected;
            const subjectCompare = normalizeText(a.subjectName).localeCompare(normalizeText(b.subjectName), "ru");
            if (subjectCompare) return subjectCompare;
            return normalizeText(a.className).localeCompare(normalizeText(b.className), "ru");
        });

    const allRowsByTeacher = new Map();
    state.manualRows.forEach((row) => {
        const key = fioKey(row.fioTeacher);
        if (!allRowsByTeacher.has(key)) allRowsByTeacher.set(key, []);
        allRowsByTeacher.get(key).push(row);
    });

    const selectedTotals = new Map();
    const allTotals = new Map();
    selectedRows.forEach((row) => {
        const key = fioKey(row.fioTeacher);
        if (!selectedTotals.has(key)) selectedTotals.set(key, { year: 0, h1: 0, h2: 0 });
        addHours(selectedTotals.get(key), row);
    });
    state.manualRows.forEach((row) => {
        const key = fioKey(row.fioTeacher);
        if (!allTotals.has(key)) allTotals.set(key, { year: 0, h1: 0, h2: 0 });
        addHours(allTotals.get(key), row);
    });

    const showSalary = salaryPermission().canView;
    const headers = ["ФИО", "Предмет", "Класс", "Группа", "Количество детей", "Часы по предмету", "Период нагрузки", "Часы в корпусе/всего", "Корпус", "Классное руководство"];
    if (showSalary) {
        headers.push("Предметный коэф.", "Коэф. группы", "За часы", "За часы итог", "Кл. рук., руб.", "Итого");
    }
    let html = `<thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead><tbody>`;

    if (!displayRows.length) {
        html += `<tr><td colspan="${headers.length}" class="muted">Для выбранного корпуса или адреса нагрузка не найдена.</td></tr>`;
    } else {
        const rowsByTeacher = new Map();
        displayRows.forEach((row) => {
            const key = fioKey(row.fioTeacher);
            if (!rowsByTeacher.has(key)) rowsByTeacher.set(key, []);
            rowsByTeacher.get(key).push(row);
        });
        for (const [key, rows] of rowsByTeacher.entries()) {
            const fio = rows[0].fioTeacher || "Вакансия";
            const scoped = selectedTotals.get(key) || { year: 0, h1: 0, h2: 0 };
            const total = allTotals.get(key) || { year: 0, h1: 0, h2: 0 };
            const hours = formatScopedTotalHours(scoped, total);
            const leadership = teacherLeadershipClasses(fio);
            const teacherRowsAcrossAllClasses = allRowsByTeacher.get(key) || rows;
            const salary = showSalary ? teacherSalary(fio, teacherRowsAcrossAllClasses) : null;
            rows.forEach((row, index) => {
                html += "<tr>";
                if (index === 0) {
                    html += `<td rowspan="${rows.length}" class="people-load-fio">${fioHtml(fio)}</td>`;
                }
                html += `<td>${escapeHtml(row.subjectName)}</td>`;
                html += `<td>${escapeHtml(row.className)}</td>`;
                html += `<td>${escapeHtml(row.groupNameEducationalPlan || "")}</td>`;
                html += `<td>${childrenCount(row)}</td>`;
                html += `<td>${escapeHtml(loadValue(row))}</td>`;
                html += `<td>${escapeHtml(periodLabel(row))}</td>`;
                if (index === 0) {
                    html += `<td rowspan="${rows.length}" class="people-load-hours">${escapeHtml(hours)}</td>`;
                }
                html += `<td class="people-load-building">${escapeHtml(rowBuildingLabel(row))}</td>`;
                if (index === 0) {
                    html += `<td rowspan="${rows.length}" class="people-load-leadership">${escapeHtml(leadership)}</td>`;
                }
                if (showSalary) {
                    const rowSalary = rowSalaryDetails(row);
                    html += `<td class="people-load-money">${escapeHtml(formatCoefficient(rowSalary.subjectCoef))}</td>`;
                    html += `<td class="people-load-money">${escapeHtml(formatCoefficient(rowSalary.groupCoef))}</td>`;
                    html += `<td class="people-load-money">${escapeHtml(formatMoney(rowSalary.hoursSalary))}</td>`;
                }
                if (index === 0) {
                    if (showSalary) {
                        html += `<td rowspan="${rows.length}" class="people-load-money">${escapeHtml(formatMoney(salary.hours))}</td>`;
                        html += `<td rowspan="${rows.length}" class="people-load-money people-load-money-leadership">${escapeHtml(formatMoney(salary.leadership))}</td>`;
                        html += `<td rowspan="${rows.length}" class="people-load-money">${escapeHtml(formatMoney(salary.total))}</td>`;
                    }
                }
                html += "</tr>";
            });
        }
    }

    html += "</tbody>";
    ui.table.innerHTML = html;
    const label = state.buildings.find((building) => building.value === selected);
    ui.summary.textContent = `Показано строк: ${displayRows.length} (в выбранном корпусе: ${selectedRows.length}). Выбрано: ${buildingLabel(label) || "корпус не выбран"}.`;
    fitPeopleLoadTables();
}

async function exportLoadWorkbook(path, fallbackName) {
    const response = await fetch(withYear(path));
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    const disposition = response.headers.get("Content-Disposition") || "";
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/);
    a.href = url;
    a.download = match ? decodeURIComponent(match[1]) : fallbackName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

async function exportFullLoadWorkbook(withSalary = false) {
    return exportLoadWorkbook(
        withSalary ? "/api/manual-load/export-full-salary" : "/api/manual-load/export-full",
        withSalary ? "full-load-salary-export.xlsx" : "full-load-export.xlsx"
    );
}

async function exportSalaryOneLoadWorkbook() {
    return exportLoadWorkbook("/api/manual-load/export-salary-one", "salary-one-load-export.xlsx");
}

async function exportDepartmentLoadWorkbook() {
    return exportLoadWorkbook("/api/manual-load/export-department-load", "department-load-export.xlsx");
}

async function exportConsolidatedLoadWorkbook() {
    return exportLoadWorkbook("/api/manual-load/export-consolidated", "primary-subject-load-export.xlsx");
}

function showPeopleLoadPanel(panel) {
    const primary = panel === "primary";
    ui.mainPanel.hidden = primary;
    ui.primaryPanel.hidden = !primary;
    ui.mainTab.classList.toggle("active", !primary);
    ui.primaryTab.classList.toggle("active", primary);
    fitPeopleLoadTables();
}

function primarySubjectOptions(selectedValue) {
    const values = new Set();
    state.primarySubjectRules.forEach((rule) => values.add(normalizeText(rule.primarySubject)));
    state.subjects.forEach((subject) => values.add(normalizeText(subject.subjectName)));
    state.primarySubjectAssignments.forEach((row) => values.add(normalizeText(row.primarySubject)));
    values.delete("");
    const options = Array.from(values).sort((a, b) => a.localeCompare(b, "ru"));
    return [
        `<option value="">Не задан</option>`,
        ...options.map((value) => `<option value="${escapeHtml(value)}" ${value === selectedValue ? "selected" : ""}>${escapeHtml(value)}</option>`)
    ].join("");
}

function renderPrimarySubjectTeachers() {
    if (!ui.primarySubjectTeachersTable) return;
    let html = "<thead><tr><th>Педагог</th><th>Основной предмет</th><th>Принцип определения</th><th>Предметы в нагрузке</th></tr></thead><tbody>";
    if (!state.primarySubjectAssignments.length) {
        html += '<tr><td colspan="4" class="muted">Педагоги не найдены.</td></tr>';
    } else {
        state.primarySubjectAssignments.forEach((row) => {
            const mode = row.mode === "MANUAL" ? "Ручное" : row.mode === "AUTO" ? "АВТО" : "Не определён";
            const modeClass = row.mode === "MANUAL" ? "manual" : row.mode === "AUTO" ? "auto" : "";
            html += `<tr>
                <td>${escapeHtml(row.teacherFio)}</td>
                <td><select data-primary-subject-teacher="${row.teacherId}">${primarySubjectOptions(normalizeText(row.primarySubject))}</select></td>
                <td><span class="primary-subject-status ${modeClass}">${mode}</span></td>
                <td>${escapeHtml((row.loadSubjects || []).join(", "))}</td>
            </tr>`;
        });
    }
    html += "</tbody>";
    ui.primarySubjectTeachersTable.innerHTML = html;
    const assigned = state.primarySubjectAssignments.filter((row) => normalizeText(row.primarySubject)).length;
    ui.primarySubjectSummary.textContent = `Основной предмет задан у ${assigned} из ${state.primarySubjectAssignments.length} педагогов.`;
    fitPeopleLoadTables();
}

async function loadPrimarySubjects() {
    const [assignments, rules] = await Promise.all([
        api("/api/primary-subjects/teachers"),
        api("/api/primary-subjects/rules")
    ]);
    state.primarySubjectAssignments = assignments || [];
    state.primarySubjectRules = rules || [];
    renderPrimarySubjectTeachers();
    renderPrimarySubjectRules();
}

async function changeTeacherPrimarySubject(teacherId, primarySubject) {
    if (!primarySubject) {
        await apiRequest(`/api/primary-subjects/teachers/${teacherId}`, { method: "DELETE" });
    } else {
        await apiRequest(`/api/primary-subjects/teachers/${teacherId}`, {
            method: "PUT",
            body: JSON.stringify({ primarySubject })
        });
    }
    await loadPrimarySubjects();
}

async function determinePrimarySubjects() {
    ui.determinePrimarySubjectsBtn.disabled = true;
    try {
        const result = await apiRequest("/api/primary-subjects/determine", { method: "POST", body: "{}" });
        await loadPrimarySubjects();
        ui.primarySubjectSummary.textContent = `Определено автоматически: ${result.assigned}. Ручных сохранено: ${result.preservedManual}. Без данных: ${result.unresolved}.`;
    } finally {
        ui.determinePrimarySubjectsBtn.disabled = false;
    }
}

function ruleTypeLabel(ruleType) {
    return ruleType === "PRIMARY_GRADES" ? "1–4 классы" : "Ключевые слова";
}

function renderPrimarySubjectRules() {
    if (!ui.primarySubjectRulesBody) return;
    ui.primarySubjectRulesBody.innerHTML = state.primarySubjectRules.map((rule) => `
        <tr data-primary-subject-rule="${rule.id}" data-rule-priority="${escapeHtml(rule.priority)}">
            <td><input data-rule-field="primarySubject" value="${escapeHtml(rule.primarySubject)}"></td>
            <td>
                <select data-rule-field="ruleType">
                    <option value="KEYWORDS" ${rule.ruleType === "KEYWORDS" ? "selected" : ""}>Ключевые слова</option>
                    <option value="PRIMARY_GRADES" ${rule.ruleType === "PRIMARY_GRADES" ? "selected" : ""}>1–4 классы</option>
                </select>
            </td>
            <td><input data-rule-field="ruleValue" value="${escapeHtml(rule.ruleValue)}" title="${escapeHtml(ruleTypeLabel(rule.ruleType))}"></td>
            <td class="row-actions">
                <button type="button" data-save-primary-subject-rule="${rule.id}">Сохранить</button>
                <button type="button" class="danger" data-delete-primary-subject-rule="${rule.id}">Удалить</button>
            </td>
        </tr>
    `).join("");
}

function ruleRequestFromRow(row) {
    return {
        primarySubject: row.querySelector('[data-rule-field="primarySubject"]').value,
        ruleType: row.querySelector('[data-rule-field="ruleType"]').value,
        ruleValue: row.querySelector('[data-rule-field="ruleValue"]').value,
        priority: Number(row.dataset.rulePriority || 100)
    };
}

async function savePrimarySubjectRule(id, request) {
    await apiRequest(id ? `/api/primary-subjects/rules/${id}` : "/api/primary-subjects/rules", {
        method: id ? "PUT" : "POST",
        body: JSON.stringify(request)
    });
    await loadPrimarySubjects();
}

async function addPrimarySubjectRule() {
    await savePrimarySubjectRule(null, {
        primarySubject: ui.newPrimarySubjectName.value,
        ruleType: ui.newPrimarySubjectRuleType.value,
        ruleValue: ui.newPrimarySubjectRuleValue.value,
        priority: Math.max(100, ...state.primarySubjectRules.map((rule) => Number(rule.priority || 0))) + 10
    });
    ui.newPrimarySubjectName.value = "";
    ui.newPrimarySubjectRuleValue.value = "";
}

function exportSubjectLoadWorkbook() {
    const selected = ui.buildingSelect?.value || state.buildings[0]?.value || "";
    const [building, ...addressParts] = selected.split("|");
    const params = new URLSearchParams();
    if (building) params.set("building", building);
    const address = addressParts.join("|");
    const selectedOption = state.buildings.find((option) => option.value === selected);
    if (address && selectedOption?.address) params.set("campusAddress", selectedOption.address);
    const query = params.toString();
    return exportLoadWorkbook(`/api/manual-load/export-subjects${query ? `?${query}` : ""}`, "subject-load-export.xlsx");
}

function rebuildIndexes() {
    state.classMapByGroup = new Map();
    state.classMapByName = new Map();
    state.classMapById = new Map();
    state.classes.forEach((entry) => {
        if (entry.id != null) state.classMapById.set(String(entry.id), entry);
        state.classMapByGroup.set(classKey(entry.className, entry.numberSchoolBuilding), entry);
        const name = normalizeKey(entry.className);
        if (!state.classMapByName.has(name)) state.classMapByName.set(name, entry);
    });

    state.teacherByFio = new Map();
    state.teachers.forEach((teacher) => state.teacherByFio.set(fioKey(teacher.fioTeacher), teacher));

    state.leadershipByTeacher = new Map();
    state.classes.forEach((entry) => {
        const key = fioKey(entry.fioTeacher);
        if (!state.leadershipByTeacher.has(key)) state.leadershipByTeacher.set(key, []);
        state.leadershipByTeacher.get(key).push(entry);
    });

    state.subjectCoefficientByKey = new Map();
    state.coefficients.forEach((entry) => {
        const coefficient = Number(entry.coefficient ?? 1);
        const key = `${normalizeKey(entry.subjectName)}|${entry.educationStage || ""}`;
        state.subjectCoefficientByKey.set(key, Number.isFinite(coefficient) && coefficient > 0 ? coefficient : 1);
    });
    state.groupCoefficientSubjectIds = new Set((state.groupCoefficientSubjects || []).map((entry) => String(entry.subjectId || "")).filter(Boolean));
    state.groupCoefficientSubjectNames = new Set((state.groupCoefficientSubjects || []).map((entry) => normalizeKey(entry.subjectName)));
}

async function loadData() {
    ui.summary.textContent = "Загрузка данных…";
    const salaryAccess = salaryPermission().canView;
    const [buildings, manualRows, classes, teachers, subjects, coefficients, salarySettings, groupCoefficientSubjects, primaryAssignments, primaryRules, classSizes] = await Promise.all([
        api("/api/buildings"),
        api("/api/manual-load"),
        api("/api/classroom-leadership"),
        api("/api/teachers"),
        api("/api/subjects"),
        api("/api/subjects/coefficients"),
        salaryAccess ? api("/api/salary-settings") : Promise.resolve(null),
        salaryAccess ? api("/api/salary-group-coefficient-subjects") : Promise.resolve([]),
        api("/api/primary-subjects/teachers"),
        api("/api/primary-subjects/rules"),
        api("/api/contingent/manual-class-sizes").catch(() => ({ source: "AIS", rows: [] }))
    ]);
    state.manualRows = manualRows || [];
    state.classes = classes || [];
    state.buildings = buildBuildingOptions(buildings, state.classes);
    state.teachers = teachers || [];
    state.subjects = subjects || [];
    state.coefficients = coefficients || [];
    state.groupCoefficientSubjects = groupCoefficientSubjects || [];
    state.primarySubjectAssignments = primaryAssignments || [];
    state.primarySubjectRules = primaryRules || [];
    applyClassSizeResponse(classSizes);
    const rate = Number(salarySettings?.studentHourRate ?? 37);
    state.studentHourRate = Number.isFinite(rate) && rate > 0 ? rate : 37;
    rebuildIndexes();
    fillBuildingSelect();
    renderTable();
    renderPrimarySubjectTeachers();
    renderPrimarySubjectRules();
}

function showError(error) {
    ui.summary.textContent = `Ошибка: ${error.message}`;
    ui.table.innerHTML = "";
}

function waitForAuth() {
    if (window.tarificationAuth) return Promise.resolve();
    return new Promise((resolve) => {
        let attempts = 0;
        const timer = setInterval(() => {
            attempts += 1;
            if (window.tarificationAuth || attempts >= 50) {
                clearInterval(timer);
                resolve();
            }
        }, 50);
    });
}

async function init() {
    ui.buildingSelect?.addEventListener("change", renderTable);
    ui.refreshBtn?.addEventListener("click", () => loadData().catch(showError));
    ui.exportFullLoadBtn?.addEventListener("click", () => exportFullLoadWorkbook().catch((error) => alert(`Не удалось скачать полную нагрузку: ${error.message}`)));
    ui.exportConsolidatedLoadBtn?.addEventListener("click", () => exportConsolidatedLoadWorkbook().catch((error) => alert(`Не удалось скачать отчёт по основному предмету: ${error.message}`)));
    ui.exportSubjectLoadBtn?.addEventListener("click", () => exportSubjectLoadWorkbook().catch((error) => alert(`Не удалось скачать нагрузку по предметам: ${error.message}`)));
    if (ui.exportFullLoadSalaryBtn) {
        ui.exportFullLoadSalaryBtn.addEventListener("click", () => exportFullLoadWorkbook(true).catch((error) => alert(`Не удалось скачать полную нагрузку с ЗП: ${error.message}`)));
    }
    if (ui.exportSalaryOneLoadBtn) {
        ui.exportSalaryOneLoadBtn.addEventListener("click", () => exportSalaryOneLoadWorkbook().catch((error) => alert(`Не удалось скачать нагрузку для ЗП 1: ${error.message}`)));
    }
    ui.exportDepartmentLoadBtn?.addEventListener("click", () => exportDepartmentLoadWorkbook().catch((error) => alert(`Не удалось скачать нагрузку ДЕП: ${error.message}`)));
    ui.mainTab?.addEventListener("click", () => showPeopleLoadPanel("main"));
    ui.primaryTab?.addEventListener("click", () => showPeopleLoadPanel("primary"));
    window.addEventListener("resize", fitPeopleLoadTables);
    window.addEventListener("scroll", fitPeopleLoadTables, { passive: true });
    ui.determinePrimarySubjectsBtn?.addEventListener("click", () => determinePrimarySubjects().catch((error) => alert(`Не удалось определить основные предметы: ${error.message}`)));
    ui.managePrimarySubjectsBtn?.addEventListener("click", () => ui.primarySubjectRulesDialog?.showModal());
    ui.primarySubjectRulesDialog?.addEventListener("click", (event) => {
        if (event.target === ui.primarySubjectRulesDialog) {
            ui.primarySubjectRulesDialog.close();
        }
    });
    ui.primarySubjectTeachersTable?.addEventListener("change", (event) => {
        const select = event.target.closest("[data-primary-subject-teacher]");
        if (!select) return;
        changeTeacherPrimarySubject(select.dataset.primarySubjectTeacher, select.value)
            .catch((error) => alert(`Не удалось сохранить основной предмет: ${error.message}`));
    });
    ui.primarySubjectRulesBody?.addEventListener("click", (event) => {
        const saveButton = event.target.closest("[data-save-primary-subject-rule]");
        const deleteButton = event.target.closest("[data-delete-primary-subject-rule]");
        if (saveButton) {
            const row = saveButton.closest("[data-primary-subject-rule]");
            savePrimarySubjectRule(saveButton.dataset.savePrimarySubjectRule, ruleRequestFromRow(row))
                .catch((error) => alert(`Не удалось сохранить правило: ${error.message}`));
        }
        if (deleteButton && confirm("Удалить правило основного предмета?")) {
            apiRequest(`/api/primary-subjects/rules/${deleteButton.dataset.deletePrimarySubjectRule}`, { method: "DELETE" })
                .then(loadPrimarySubjects)
                .catch((error) => alert(`Не удалось удалить правило: ${error.message}`));
        }
    });
    ui.addPrimarySubjectRuleBtn?.addEventListener("click", () => addPrimarySubjectRule().catch((error) => alert(`Не удалось добавить правило: ${error.message}`)));
    await waitForAuth();
    if (ui.exportFullLoadSalaryBtn) {
        ui.exportFullLoadSalaryBtn.style.display = salaryPermission().canExport ? "" : "none";
    }
    if (ui.exportSalaryOneLoadBtn) {
        ui.exportSalaryOneLoadBtn.style.display = salaryPermission().canExport ? "" : "none";
    }
    loadData().catch(showError);
}

init().catch(showError);
