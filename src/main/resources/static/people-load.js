const ui = {
    buildingSelect: document.getElementById("people-load-building-select"),
    refreshBtn: document.getElementById("people-load-refresh-btn"),
    exportFullLoadBtn: document.getElementById("export-full-load-btn"),
    exportConsolidatedLoadBtn: document.getElementById("export-consolidated-load-btn"),
    exportFullLoadSalaryBtn: document.getElementById("export-full-load-salary-btn"),
    summary: document.getElementById("people-load-summary"),
    table: document.getElementById("people-load-table")
};

const state = {
    buildings: [],
    manualRows: [],
    classes: [],
    teachers: [],
    subjects: [],
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
    return h1 === h2 ? String(h1) : `${h1}/${h2}`;
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
    const name = normalizeText(row.groupNameEducationalPlan || "").toLowerCase();
    if (name.includes("2")) return 15;
    if (name.includes("1")) return 15;
    return 30;
}

function salaryPermission() {
    const user = window.tarificationAuth || {};
    const permissions = window.tarificationTabPermissions || {};
    const privilegedRole = user.role === "DIRECTOR" || user.role === "DEPUTY_DIRECTOR";
    return {
        canView: Boolean(user.admin || privilegedRole || permissions.LOAD_SALARY?.canView),
        canExport: Boolean(user.admin || privilegedRole || permissions.LOAD_SALARY?.canExport)
    };
}

function subjectCoefficient(subjectName) {
    return state.subjectCoefficientByName?.get(normalizeKey(subjectName)) || 1;
}

function rowSalary(row) {
    const children = Math.max(childrenCount(row), 1);
    const hours = Math.max(loadValue(row), 0);
    const coefficient = subjectCoefficient(row.subjectName);
    let value = state.studentHourRate * children * hours * 2.8333333 * coefficient;
    if (normalizeText(row.groupNameEducationalPlan || "")) {
        value *= 25 / children;
    }
    return value;
}

function classLeadershipSalary(fio) {
    const key = fioKey(fio);
    return (state.leadershipByTeacher?.get(key) || [])
        .reduce((sum) => sum + 500 * 30 + 5000, 0);
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
        headers.push("За часы", "Кл. рук., руб.", "Итого");
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

async function exportConsolidatedLoadWorkbook() {
    return exportLoadWorkbook("/api/manual-load/export-consolidated", "consolidated-load-export.xlsx");
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

    state.subjectCoefficientByName = new Map();
    state.subjects.forEach((subject) => {
        const coefficient = Number(subject.subjectCoefficient ?? 1);
        state.subjectCoefficientByName.set(normalizeKey(subject.subjectName), Number.isFinite(coefficient) && coefficient > 0 ? coefficient : 1);
    });
}

async function loadData() {
    ui.summary.textContent = "Загрузка данных…";
    const salaryAccess = salaryPermission().canView;
    const [buildings, manualRows, classes, teachers, subjects, salarySettings] = await Promise.all([
        api("/api/buildings"),
        api("/api/manual-load"),
        api("/api/classroom-leadership"),
        api("/api/teachers"),
        api("/api/subjects"),
        salaryAccess ? api("/api/salary-settings") : Promise.resolve(null)
    ]);
    state.manualRows = manualRows || [];
    state.classes = classes || [];
    state.buildings = buildBuildingOptions(buildings, state.classes);
    state.teachers = teachers || [];
    state.subjects = subjects || [];
    const rate = Number(salarySettings?.studentHourRate ?? 37);
    state.studentHourRate = Number.isFinite(rate) && rate > 0 ? rate : 37;
    rebuildIndexes();
    fillBuildingSelect();
    renderTable();
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
    ui.exportConsolidatedLoadBtn?.addEventListener("click", () => exportConsolidatedLoadWorkbook().catch((error) => alert(`Не удалось скачать укрупнённую нагрузку: ${error.message}`)));
    if (ui.exportFullLoadSalaryBtn) {
        ui.exportFullLoadSalaryBtn.addEventListener("click", () => exportFullLoadWorkbook(true).catch((error) => alert(`Не удалось скачать полную нагрузку с ЗП: ${error.message}`)));
    }
    await waitForAuth();
    if (ui.exportFullLoadSalaryBtn) {
        ui.exportFullLoadSalaryBtn.style.display = salaryPermission().canExport ? "" : "none";
    }
    loadData().catch(showError);
}

init().catch(showError);
