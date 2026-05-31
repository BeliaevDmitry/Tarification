const ui = {
    buildingSelect: document.getElementById("people-load-building-select"),
    refreshBtn: document.getElementById("people-load-refresh-btn"),
    exportFullLoadBtn: document.getElementById("export-full-load-btn"),
    summary: document.getElementById("people-load-summary"),
    table: document.getElementById("people-load-table")
};

const state = {
    buildings: [],
    manualRows: [],
    classes: [],
    teachers: []
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
    return normalizeText(value)
        .replace(/\s*\|\s*/g, "|")
        .toUpperCase();
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
    const byGroup = classKey(row.className, row.numberSchoolBuilding);
    const exact = state.classMapByGroup?.get(byGroup);
    if (exact) return exact;
    return state.classMapByName?.get(normalizeKey(row.className)) || null;
}

function rowAddressToken(row) {
    const classEntry = rowClassEntry(row);
    if (classEntry?.campusAddress) {
        return normalizeBuildingAccessCode(classEntry.campusAddress);
    }
    return "";
}

function rowMatchesBuildingAccess(row, accessCode) {
    const selectedGroup = buildingGroupCode(accessCode);
    if (!selectedGroup || buildingGroupCode(row.numberSchoolBuilding) !== selectedGroup) {
        return false;
    }
    const selectedAddress = buildingAddressToken(accessCode);
    return !selectedAddress || rowAddressToken(row) === selectedAddress;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function buildingLabel(building) {
    if (!building) return "";
    if (building.scope === "group") {
        const count = building.addresses?.length || 0;
        return `${building.name || building.code} — все адреса${count ? ` (${count})` : ""}`;
    }
    return `${building.name || building.code} — ${building.address || "адрес не указан"}`;
}

function buildBuildingOptions(rawBuildings) {
    const grouped = new Map();
    for (const building of rawBuildings || []) {
        const code = buildingGroupCode(building.code || building.name);
        if (!code) continue;
        if (!grouped.has(code)) {
            grouped.set(code, {
                code,
                name: normalizeText(building.name || building.code || code) || code,
                addresses: []
            });
        }
        const group = grouped.get(code);
        if (!group.name || group.name === code) {
            group.name = normalizeText(building.name || code) || code;
        }
        const address = normalizeText(building.address);
        if (address && !group.addresses.some((known) => normalizeBuildingAccessCode(known) === normalizeBuildingAccessCode(address))) {
            group.addresses.push(address);
        }
    }

    const options = [];
    Array.from(grouped.values())
        .sort((a, b) => a.name.localeCompare(b.name, "ru"))
        .forEach((group) => {
            group.addresses.sort((a, b) => a.localeCompare(b, "ru"));
            options.push({
                code: group.code,
                value: group.code,
                name: group.name,
                address: group.addresses[0] || "",
                addresses: group.addresses,
                scope: "group"
            });
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
    if (row.loadFromDate || row.loadToDate) {
        const from = row.loadFromDate || "…";
        const to = row.loadToDate || "…";
        return `${from} — ${to}`;
    }
    if (row.studyPeriod === "FIRST_HALF") return "1 полугодие";
    if (row.studyPeriod === "SECOND_HALF") return "2 полугодие";
    return "Год";
}

function loadValue(row) {
    const value = Number(row.groupLoad ?? row.load ?? 0);
    return Number.isFinite(value) ? value : 0;
}

function addHours(totals, row) {
    const load = loadValue(row);
    if (!load) return;
    if (row.studyPeriod === "FIRST_HALF") {
        totals.h1 += load;
    } else if (row.studyPeriod === "SECOND_HALF") {
        totals.h2 += load;
    } else {
        totals.year += load;
    }
}

function formatHours(total) {
    const year = total.year || 0;
    const h1 = total.h1 || 0;
    const h2 = total.h2 || 0;
    if (h1 || h2) {
        return `${h1}/${h2}`;
    }
    return String(year);
}

function fioKey(value) {
    return normalizeKey(value || "Вакансия");
}

function childrenCount(row) {
    const name = normalizeText(row.groupNameEducationalPlan || "").toLowerCase();
    if (name.includes("2")) return 12;
    if (name.includes("1")) return 13;
    return 25;
}

function teacherExtra(fio, teacherRows) {
    const key = fioKey(fio);
    const teacher = state.teacherByFio?.get(key);
    const teacherBuildings = Array.from(new Set(teacherRows.map((row) => buildingGroupCode(row.numberSchoolBuilding)).filter(Boolean))).sort((a, b) => a.localeCompare(b, "ru"));
    const leadership = (state.leadershipByTeacher?.get(key) || [])
        .map((entry) => `${entry.className} (${entry.numberSchoolBuilding}${entry.campusAddress ? `, ${entry.campusAddress}` : ""})`)
        .join("; ");
    const extra = [];
    if (teacherBuildings.length) extra.push(`Корпуса: ${teacherBuildings.join(", ")}`);
    if (leadership) extra.push(`Классное руководство: ${leadership}`);
    if (normalizeText(teacher?.additionalDuties)) extra.push(`Доп. обязанности: ${teacher.additionalDuties}`);
    return extra.join("\n");
}

function renderTable() {
    const selected = ui.buildingSelect?.value || state.buildings[0]?.value || "";
    const selectedRows = state.manualRows
        .filter((row) => rowMatchesBuildingAccess(row, selected))
        .sort((a, b) => {
            const fioCompare = normalizeText(a.fioTeacher).localeCompare(normalizeText(b.fioTeacher), "ru");
            if (fioCompare) return fioCompare;
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

    const headers = ["ФИО", "Предмет", "Класс", "Группа", "Количество детей", "Часы по предмету", "Период нагрузки", "Часы в корпусе/всего", "Дополнительные сведения"];
    let html = `<thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead><tbody>`;

    if (!selectedRows.length) {
        html += `<tr><td colspan="${headers.length}" class="muted">Для выбранного корпуса или адреса нагрузка не найдена.</td></tr>`;
    } else {
        const rowsByTeacher = new Map();
        selectedRows.forEach((row) => {
            const key = fioKey(row.fioTeacher);
            if (!rowsByTeacher.has(key)) rowsByTeacher.set(key, []);
            rowsByTeacher.get(key).push(row);
        });
        for (const [key, rows] of rowsByTeacher.entries()) {
            const fio = rows[0].fioTeacher || "Вакансия";
            const scoped = selectedTotals.get(key) || { year: 0, h1: 0, h2: 0 };
            const total = allTotals.get(key) || { year: 0, h1: 0, h2: 0 };
            const hours = `${formatHours(scoped)} / ${formatHours(total)}`;
            const extra = teacherExtra(fio, allRowsByTeacher.get(key) || rows);
            rows.forEach((row, index) => {
                html += "<tr>";
                if (index === 0) {
                    html += `<td rowspan="${rows.length}" class="people-load-fio">${escapeHtml(fio)}</td>`;
                }
                html += `<td>${escapeHtml(row.subjectName)}</td>`;
                html += `<td>${escapeHtml(row.className)}</td>`;
                html += `<td>${escapeHtml(row.groupNameEducationalPlan || "")}</td>`;
                html += `<td>${childrenCount(row)}</td>`;
                html += `<td>${escapeHtml(loadValue(row))}</td>`;
                html += `<td>${escapeHtml(periodLabel(row))}</td>`;
                if (index === 0) {
                    html += `<td rowspan="${rows.length}" class="people-load-hours">${escapeHtml(hours)}</td>`;
                    html += `<td rowspan="${rows.length}" class="people-load-extra">${escapeHtml(extra)}</td>`;
                }
                html += "</tr>";
            });
        }
    }

    html += "</tbody>";
    ui.table.innerHTML = html;
    const label = state.buildings.find((building) => building.value === selected);
    ui.summary.textContent = `Показано строк: ${selectedRows.length}. Выбрано: ${buildingLabel(label) || "корпус не выбран"}.`;
}

async function exportFullLoadWorkbook() {
    const response = await fetch(withYear("/api/manual-load/export-full"));
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
    a.download = match ? decodeURIComponent(match[1]) : "full-load-export.xlsx";
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function rebuildIndexes() {
    state.classMapByGroup = new Map();
    state.classMapByName = new Map();
    state.classes.forEach((entry) => {
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
}

async function loadData() {
    ui.summary.textContent = "Загрузка данных…";
    const [buildings, manualRows, classes, teachers] = await Promise.all([
        api("/api/buildings"),
        api("/api/manual-load"),
        api("/api/classroom-leadership"),
        api("/api/teachers")
    ]);
    state.buildings = buildBuildingOptions(buildings);
    state.manualRows = manualRows || [];
    state.classes = classes || [];
    state.teachers = teachers || [];
    rebuildIndexes();
    fillBuildingSelect();
    renderTable();
}

function showError(error) {
    ui.summary.textContent = `Ошибка: ${error.message}`;
    ui.table.innerHTML = "";
}

function init() {
    ui.buildingSelect?.addEventListener("change", renderTable);
    ui.refreshBtn?.addEventListener("click", () => loadData().catch(showError));
    ui.exportFullLoadBtn?.addEventListener("click", () => exportFullLoadWorkbook().catch((error) => alert(`Не удалось скачать полную нагрузку: ${error.message}`)));
    loadData().catch(showError);
}

init();
