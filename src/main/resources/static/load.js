const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    buildingTabs: document.getElementById("building-tabs"),
    refreshLoadBtn: document.getElementById("refresh-load-btn"),
    saveBuildingBtn: document.getElementById("save-building-btn"),
    loadResult: document.getElementById("load-result"),
    tableHead: document.getElementById("building-load-head"),
    tableBody: document.getElementById("building-load-body"),
    sortField: document.getElementById("sort-field-select"),
    sortDirection: document.getElementById("sort-direction-select")
};

let curriculumRows = [];
let manualRows = [];
let teacherNames = [];
let teacherDirectory = [];
let buildings = [];
let selectedBuilding = "";

const state = {
    assignmentsByBuilding: {},
    subjectTeacherRowsByBuilding: {},
    rowOrderByBuilding: {},
    sortField: "subject",
    sortDirection: "asc",
    forceResort: true,
    hasUnsavedChanges: false
};


async function api(path, options = {}) {
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

function print(value) {
    ui.loadResult.textContent = JSON.stringify(value, null, 2);
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

function displaySubjectName(row) {
    return row.__groupIndex ? `${row.subjectName} ${row.__groupIndex}` : row.subjectName;
}

function apiKeyOfRow(row) {
    return `${row.className}|${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}${groupSuffix(row)}`;
}

function subjectKeyOfRow(row) {
    return `${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}${groupSuffix(row)}`;
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

function rowsForSelectedBuilding() {
    return curriculumRows.filter((row) => String(row.numberSchoolBuilding || "").trim() === selectedBuilding);
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

function classesForSelectedBuilding() {
    return sortRu(Array.from(new Set(expandCurriculumRows(rowsForSelectedBuilding()).map((row) => row.className).filter(Boolean))));
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
    return teacherDirectory.some((t) => String(t.fioTeacher || "").trim().toLowerCase() === normalized && t.dismissalDate);
}

function teacherExists(teacherName) {
    const normalized = String(teacherName || "").trim().toLowerCase();
    if (!normalized) return true;
    return teacherDirectory.some((t) => String(t.fioTeacher || "").trim().toLowerCase() === normalized);
}

function dismissalDateOfTeacher(teacherName) {
    const normalized = String(teacherName || "").trim().toLowerCase();
    const teacher = teacherDirectory.find((t) => String(t.fioTeacher || "").trim().toLowerCase() === normalized);
    return teacher?.dismissalDate || null;
}

function defaultLoadPeriod() {
    const now = new Date();
    const from = "2026-09-01";
    const to = "2027-05-31";
    return { from, to };
}

function prefillFromManualLoad() {
    const allApiRows = expandCurriculumRows(curriculumRows);
    manualRows.forEach((entry) => {
        const buildingCode = String(entry.numberSchoolBuilding || "").trim();
        if (!buildingCode) return;

        const matched = allApiRows.find((row) =>
            String(row.numberSchoolBuilding || "").trim() === buildingCode
            && row.className === entry.className
            && row.subjectName === entry.subjectName
            && row.educationLevel === entry.educationLevel
        );

        if (!matched) return;

        const assignments = assignmentsForBuilding(buildingCode);
        const teacherRowsMap = teacherRowsForBuilding(buildingCode);
        const apiKey = apiKeyOfRow(matched);
        const subjectKey = subjectKeyOfRow(matched);
        const teacherName = String(entry.fioTeacher || "").trim();

        assignments[apiKey] = teacherName;
        if (!teacherName) return;

        if (!teacherRowsMap[subjectKey]) {
            teacherRowsMap[subjectKey] = [];
        }

        const exists = teacherRowsMap[subjectKey].some((row) => row.teacherName.toLowerCase() === teacherName.toLowerCase());
        if (!exists) {
            const period = defaultLoadPeriod();
            teacherRowsMap[subjectKey].push({
                id: rowId(),
                teacherName,
                loadFromDate: entry.loadFromDate || period.from,
                loadToDate: entry.loadToDate || period.to
            });
        }
    });
}

function ensureTeacherRowsForBuilding() {
    const buildingRows = expandCurriculumRows(rowsForSelectedBuilding());
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
                const period = defaultLoadPeriod();
                rowsMap[subjectKey].push({ id: rowId(), teacherName, loadFromDate: period.from, loadToDate: period.to });
            }
        });

        if (!rowsMap[subjectKey].length) {
            const period = defaultLoadPeriod();
            rowsMap[subjectKey].push({ id: rowId(), teacherName: "", loadFromDate: period.from, loadToDate: period.to });
        }
    });
}


function rowStableKey(row) {
    return `${row.subjectKey}::${row.teacherRowId}`;
}

function teacherHoursInBuilding(buildingCode, teacherName) {
    const normalizedTeacher = String(teacherName || "").trim();
    if (!normalizedTeacher) return 0;

    const assignments = assignmentsForBuilding(buildingCode);
    const buildingRows = expandCurriculumRows(curriculumRows.filter((row) => String(row.numberSchoolBuilding || "").trim() === buildingCode));

    return buildingRows.reduce((acc, row) => {
        const assigned = String(assignments[apiKeyOfRow(row)] || "").trim();
        if (assigned && assigned === normalizedTeacher) {
            return acc + Number(row.plannedHours || 0);
        }
        return acc;
    }, 0);
}

function teacherHoursInComplex(teacherName) {
    const normalizedTeacher = String(teacherName || "").trim();
    if (!normalizedTeacher) return 0;
    return buildings.reduce((acc, building) => acc + teacherHoursInBuilding(building.code, normalizedTeacher), 0);
}

function buildPresentationRows() {
    const rows = expandCurriculumRows(rowsForSelectedBuilding());
    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);

    const subjectInfo = new Map();
    rows.forEach((row) => {
        const subjectKey = subjectKeyOfRow(row);
        if (!subjectInfo.has(subjectKey)) {
            subjectInfo.set(subjectKey, {
                subjectKey,
                subjectName: row.subjectName,
                displaySubjectName: displaySubjectName(row),
                curriculumPart: row.curriculumPart,
                educationLevel: row.educationLevel,
                groupIndex: row.__groupIndex,
                rowsByClass: {}
            });
        }
        subjectInfo.get(subjectKey).rowsByClass[row.className] = row;
    });

    const result = [];
    subjectInfo.forEach((info) => {
        const teacherRows = rowsMap[info.subjectKey] || [{ ...defaultLoadPeriod(), id: rowId(), teacherName: "" }];
        teacherRows.forEach((teacherRow) => {
            let totalHours = 0;
            let classCount = 0;

            Object.values(info.rowsByClass).forEach((row) => {
                const assignedTeacher = String(assignments[apiKeyOfRow(row)] || "").trim();
                if (assignedTeacher && assignedTeacher === String(teacherRow.teacherName || "").trim()) {
                    totalHours += Number(row.plannedHours || 0);
                    classCount += 1;
                }
            });

            result.push({
                subjectKey: info.subjectKey,
                teacherRowId: teacherRow.id,
                subjectName: info.subjectName,
                displaySubjectName: info.displaySubjectName,
                curriculumPart: info.curriculumPart,
                educationLevel: info.educationLevel,
                groupIndex: info.groupIndex,
                teacherName: teacherRow.teacherName || "",
                loadFromDate: teacherRow.loadFromDate || defaultLoadPeriod().from,
                loadToDate: teacherRow.loadToDate || defaultLoadPeriod().to,
                rowsByClass: info.rowsByClass,
                classCount,
                subjectHours: totalHours,
                buildingHours: teacherHoursInBuilding(selectedBuilding, teacherRow.teacherName || ""),
                complexHours: teacherHoursInComplex(teacherRow.teacherName || "")
            });
        });
    });

    const filtered = result.filter((row) => {
        const hasAssigned = Object.values(row.rowsByClass).some((curriculumRow) => {
            const assigned = String(assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(curriculumRow)] || "").trim();
            return assigned === String(row.teacherName || "").trim() && assigned !== "";
        });
        return hasAssigned || !String(row.teacherName || "").trim();
    });

    return getOrderedRows(filtered);
}


function applySorting(presentationRows) {
    const dir = state.sortDirection === "desc" ? -1 : 1;
    const cmp = (a, b) => String(a).localeCompare(String(b), "ru");

    return [...presentationRows].sort((a, b) => {
        let result = 0;
        switch (state.sortField) {
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

    buildings.forEach((building) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `parallel-tab ${building.code === selectedBuilding ? "active" : ""}`;
        button.textContent = `${building.code} — ${building.name}`;
        button.addEventListener("click", () => {
            selectedBuilding = building.code;
            state.forceResort = true;
            renderBuildingTabs();
            renderTable();
        });
        ui.buildingTabs.appendChild(button);
    });
}

function addTeacherRow(subjectKey, afterRowId = null) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    if (!rowsMap[subjectKey]) rowsMap[subjectKey] = [];
    const period = defaultLoadPeriod();
    const newRow = { id: rowId(), teacherName: "", loadFromDate: period.from, loadToDate: period.to };
    if (!afterRowId) {
        rowsMap[subjectKey].push(newRow);
    } else {
        const idx = rowsMap[subjectKey].findIndex((r) => r.id === afterRowId);
        if (idx === -1) rowsMap[subjectKey].push(newRow);
        else rowsMap[subjectKey].splice(idx + 1, 0, newRow);
    }
    markDirty();
    renderTable();
}

function setTeacherForRow(subjectKey, teacherRowId, value) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const row = (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId);
    if (!row) return;

    const previousTeacher = String(row.teacherName || "").trim();
    const nextTeacher = String(value || "").trim();
    row.teacherName = nextTeacher;
    markDirty();

    const assignments = assignmentsForBuilding(selectedBuilding);
    expandCurriculumRows(rowsForSelectedBuilding())
        .filter((curriculumRow) => subjectKeyOfRow(curriculumRow) === subjectKey)
        .forEach((curriculumRow) => {
            const apiKey = apiKeyOfRow(curriculumRow);
            const currentTeacher = String(assignments[apiKey] || "").trim();

            if (!nextTeacher) {
                if (currentTeacher && (!previousTeacher || currentTeacher === previousTeacher)) {
                    assignments[apiKey] = "";
                }
                return;
            }

            if (!currentTeacher || currentTeacher === previousTeacher) {
                assignments[apiKey] = nextTeacher;
            }
        });
}


function setPeriodForRow(subjectKey, teacherRowId, fromDate, toDate) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const row = (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId);
    if (!row) return;
    row.loadFromDate = fromDate;
    row.loadToDate = toDate;
    markDirty();
}

function onClassCellClick(presentationRow, className) {
    const curriculumRow = presentationRow.rowsByClass[className];
    if (!curriculumRow) return;

    const assignments = assignmentsForBuilding(selectedBuilding);
    const targetTeacher = String(presentationRow.teacherName || "").trim();
    if (!targetTeacher) {
        print({ warning: "Сначала заполните ФИО педагога в строке" });
        return;
    }

    const apiKey = apiKeyOfRow(curriculumRow);
    if (assignments[apiKey] === targetTeacher) {
        assignments[apiKey] = "";
        markDirty();
    } else {
        assignments[apiKey] = targetTeacher;
        const rowsMap = teacherRowsForBuilding(selectedBuilding);
        const rowMeta = (rowsMap[presentationRow.subjectKey] || []).find((r) => r.id === presentationRow.teacherRowId);
        if (rowMeta) {
            const from = prompt("Период нагрузки с (YYYY-MM-DD)", rowMeta.loadFromDate || defaultLoadPeriod().from);
            if (from) rowMeta.loadFromDate = from;
            const to = prompt("Период нагрузки по (YYYY-MM-DD)", rowMeta.loadToDate || defaultLoadPeriod().to);
            if (to) rowMeta.loadToDate = to;
        }
        markDirty();
    }

    renderTable();
}

function renderTable() {
    ui.tableHead.innerHTML = "";
    ui.tableBody.innerHTML = "";

    if (!selectedBuilding) {
        ui.tableBody.innerHTML = '<tr><td colspan="7">Добавьте корпуса, чтобы распределять нагрузку.</td></tr>';
        return;
    }

    ensureTeacherRowsForBuilding();

    const classes = classesForSelectedBuilding();
    const presentationRows = buildPresentationRows();

    const head = document.createElement("tr");
    head.innerHTML = `
        <th>Предмет</th>
        <th>Педагог</th>
        <th>Часов в корпусе</th>
        <th>Всего часов в комплексе</th>
        ${classes.map((className) => `<th>${esc(className)}</th>`).join("")}
    `;
    ui.tableHead.appendChild(head);

    presentationRows.forEach((row, index) => {
        const tr = document.createElement("tr");
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
                const assignedTeacher = assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(curriculumRow)] || "";
                const rowTeacher = String(row.teacherName || "").trim();
                const isActive = assignedTeacher === rowTeacher && assignedTeacher !== "";
                const isMuted = rowTeacher !== "" && !isActive;
                const isUnassigned = !assignedTeacher;
                return `<td><button type="button" class="hour-pill ${isActive ? "active" : ""} ${isMuted ? "muted" : ""} ${isUnassigned ? "unassigned" : ""}" data-class-cell="1" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" data-class-name="${esc(className)}">${esc(curriculumRow.plannedHours)} ч</button></td>`;
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
            const raw = String(teacherInput.value || "").trim();
            if (!raw) {
                setTeacherForRow(row.subjectKey, row.teacherRowId, "");
                renderTable();
                return;
            }

            const exact = teacherNames.find((name) => name.toLowerCase() === raw.toLowerCase());
            if (!exact) {
                print({ warning: `Педагог «${raw}» не найден в справочнике` });
                teacherInput.value = "";
                setTeacherForRow(row.subjectKey, row.teacherRowId, "");
            } else {
                teacherInput.value = exact;
                setTeacherForRow(row.subjectKey, row.teacherRowId, exact);
            }
            renderTable();
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
}

async function saveBuildingLoad() {
    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const payload = expandCurriculumRows(rowsForSelectedBuilding()).map((row) => {
        const fioTeacher = String(assignments[apiKeyOfRow(row)] || "").trim();
        if (!fioTeacher) return null;

        const teacherRow = (rowsMap[subjectKeyOfRow(row)] || []).find((r) => String(r.teacherName || "").trim() === fioTeacher);
        const period = defaultLoadPeriod();

        return {
            fioTeacher,
            numberSchoolBuilding: selectedBuilding,
            subjectName: row.subjectName,
            className: row.className,
            load: Number(row.plannedHours || 0),
            groupNameEducationalPlan: row.__groupIndex ? `Группа ${row.__groupIndex}` : null,
            groupLoad: row.__groupIndex ? Number(row.plannedHours || 0) : null,
            educationLevel: row.educationLevel,
            loadFromDate: teacherRow?.loadFromDate || period.from,
            loadToDate: teacherRow?.loadToDate || period.to
        };
    }).filter(Boolean);

    if (!payload.length) {
        print({ warning: "Нет назначений для сохранения" });
        return;
    }

    try {
        const result = await api("/api/manual-load/bulk", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
        print({ saved: result.length, building: selectedBuilding });
        markDirty(false);
    } catch (error) {
        print({ error: error.message });
    }
}

async function refreshSourceData() {
    const [curriculum, manual, teachers, buildingRows] = await Promise.all([
        api("/api/curriculum"),
        api("/api/manual-load"),
        api("/api/teachers"),
        api("/api/buildings")
    ]);

    curriculumRows = curriculum || [];
    manualRows = manual || [];
    teacherDirectory = teachers || [];
    teacherNames = sortRu(Array.from(new Set(teacherDirectory.map((t) => String(t.fioTeacher || "").trim()).filter(Boolean))));
    buildings = [...(buildingRows || [])].sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));

    prefillFromManualLoad();
    state.forceResort = true;
    markDirty(false);

    if (!buildings.some((row) => row.code === selectedBuilding)) {
        selectedBuilding = buildings[0]?.code || "";
    }

    renderBuildingTabs();
    renderTable();
}

function bindEvents() {
    ui.saveBuildingBtn.addEventListener("click", saveBuildingLoad);

    ui.refreshLoadBtn.addEventListener("click", () => {
        refreshSourceData()
            .then(() => print({ status: "Синхронизировано с учебным планом" }))
            .catch((error) => print({ error: error.message }));
    });

    ui.sortField.addEventListener("change", () => {
        state.sortField = ui.sortField.value;
        state.forceResort = true;
        renderTable();
    });

    ui.sortDirection.addEventListener("change", () => {
        state.sortDirection = ui.sortDirection.value;
        state.forceResort = true;
        renderTable();
    });
}

async function init() {
    bindEvents();

    try {
        await refreshSourceData();
        setInterval(() => { refreshSourceData().catch(() => {}); }, 30000);
    } catch (error) {
        print({ error: error.message });
    }
}

init();
