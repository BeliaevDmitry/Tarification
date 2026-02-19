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
let buildings = [];
let selectedBuilding = "";

const state = {
    assignmentsByBuilding: {},
    subjectTeacherRowsByBuilding: {},
    sortField: "subject",
    sortDirection: "asc"
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

function apiKeyOfRow(row) {
    return `${row.className}|${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}`;
}

function subjectKeyOfRow(row) {
    return `${row.subjectName}|${row.curriculumPart || "CORE"}|${row.educationLevel}`;
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

function classesForSelectedBuilding() {
    return sortRu(Array.from(new Set(rowsForSelectedBuilding().map((row) => row.className).filter(Boolean))));
}

function updateDatalistOptions(listEl, query = "") {
    if (!listEl) return;
    const q = String(query || "").trim().toLowerCase();
    const options = !q
        ? teacherNames.slice(0, 200)
        : teacherNames.filter((name) => name.toLowerCase().includes(q)).slice(0, 60);
    listEl.innerHTML = options.map((name) => `<option value="${esc(name)}"></option>`).join("");
}

function prefillFromManualLoad() {
    const allApiRows = curriculumRows;
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
            teacherRowsMap[subjectKey].push({ id: rowId(), teacherName });
        }
    });
}

function ensureTeacherRowsForBuilding() {
    const buildingRows = rowsForSelectedBuilding();
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
            if (!exists) rowsMap[subjectKey].push({ id: rowId(), teacherName });
        });

        if (!rowsMap[subjectKey].length) {
            rowsMap[subjectKey].push({ id: rowId(), teacherName: "" });
        }
    });
}

function buildPresentationRows() {
    const rows = rowsForSelectedBuilding();
    const assignments = assignmentsForBuilding(selectedBuilding);
    const rowsMap = teacherRowsForBuilding(selectedBuilding);

    const subjectInfo = new Map();
    rows.forEach((row) => {
        const subjectKey = subjectKeyOfRow(row);
        if (!subjectInfo.has(subjectKey)) {
            subjectInfo.set(subjectKey, {
                subjectKey,
                subjectName: row.subjectName,
                curriculumPart: row.curriculumPart,
                educationLevel: row.educationLevel,
                rowsByClass: {}
            });
        }
        subjectInfo.get(subjectKey).rowsByClass[row.className] = row;
    });

    const result = [];
    subjectInfo.forEach((info) => {
        const teacherRows = rowsMap[info.subjectKey] || [{ id: rowId(), teacherName: "" }];
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
                curriculumPart: info.curriculumPart,
                educationLevel: info.educationLevel,
                teacherName: teacherRow.teacherName || "",
                rowsByClass: info.rowsByClass,
                totalHours,
                classCount
            });
        });
    });

    return applySorting(result);
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
            case "block":
                result = cmp(partLabel(a.curriculumPart), partLabel(b.curriculumPart));
                break;
            case "level":
                result = cmp(educationLevelLabel(a.educationLevel), educationLevelLabel(b.educationLevel));
                break;
            case "totalHours":
                result = (a.totalHours - b.totalHours);
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

function renderBuildingTabs() {
    ui.buildingTabs.innerHTML = "";

    buildings.forEach((building) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `parallel-tab ${building.code === selectedBuilding ? "active" : ""}`;
        button.textContent = `${building.code} — ${building.name}`;
        button.addEventListener("click", () => {
            selectedBuilding = building.code;
            renderBuildingTabs();
            renderTable();
        });
        ui.buildingTabs.appendChild(button);
    });
}

function addTeacherRow(subjectKey) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    if (!rowsMap[subjectKey]) rowsMap[subjectKey] = [];
    rowsMap[subjectKey].push({ id: rowId(), teacherName: "" });
    renderTable();
}

function setTeacherForRow(subjectKey, teacherRowId, value) {
    const rowsMap = teacherRowsForBuilding(selectedBuilding);
    const row = (rowsMap[subjectKey] || []).find((entry) => entry.id === teacherRowId);
    if (!row) return;

    const previousTeacher = String(row.teacherName || "").trim();
    const nextTeacher = String(value || "").trim();
    row.teacherName = nextTeacher;

    const assignments = assignmentsForBuilding(selectedBuilding);
    rowsForSelectedBuilding()
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
    } else {
        assignments[apiKey] = targetTeacher;
    }

    renderTable();
}

function renderTable() {
    ui.tableHead.innerHTML = "";
    ui.tableBody.innerHTML = "";

    if (!selectedBuilding) {
        ui.tableBody.innerHTML = '<tr><td colspan="6">Добавьте корпуса, чтобы распределять нагрузку.</td></tr>';
        return;
    }

    ensureTeacherRowsForBuilding();

    const classes = classesForSelectedBuilding();
    const presentationRows = buildPresentationRows();

    const head = document.createElement("tr");
    head.innerHTML = `
        <th>Предмет</th>
        <th>Блок</th>
        <th>Уровень</th>
        <th>Педагог</th>
        ${classes.map((className) => `<th>${esc(className)}</th>`).join("")}
        <th>Итого</th>
    `;
    ui.tableHead.appendChild(head);

    presentationRows.forEach((row, index) => {
        const tr = document.createElement("tr");
        const listId = `teacher-list-${row.teacherRowId}`;

        tr.innerHTML = `
            <td>
                <div class="subject-cell">${esc(row.subjectName)} ${index === 0 || presentationRows[index - 1].subjectKey !== row.subjectKey ? `<button class="inline-plus" type="button" data-plus-subject="${esc(row.subjectKey)}" title="Добавить строку педагога">+</button>` : ""}</div>
            </td>
            <td>${esc(partLabel(row.curriculumPart))}</td>
            <td>${esc(educationLevelLabel(row.educationLevel))}</td>
            <td>
                <input type="text" class="teacher-input" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" list="${listId}" value="${esc(row.teacherName)}" placeholder="ФИО педагога">
                <datalist id="${listId}"></datalist>
            </td>
            ${classes.map((className) => {
                const curriculumRow = row.rowsByClass[className];
                if (!curriculumRow) return "<td></td>";
                const assignedTeacher = assignmentsForBuilding(selectedBuilding)[apiKeyOfRow(curriculumRow)] || "";
                const isActive = assignedTeacher === String(row.teacherName || "").trim() && assignedTeacher !== "";
                return `<td><button type="button" class="hour-pill ${isActive ? "active" : ""}" data-class-cell="1" data-subject-key="${esc(row.subjectKey)}" data-row-id="${esc(row.teacherRowId)}" data-class-name="${esc(className)}">${esc(curriculumRow.plannedHours)} ч</button></td>`;
            }).join("")}
            <td><strong>${esc(row.totalHours)} ч</strong></td>
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

    ui.tableBody.querySelectorAll("button[data-plus-subject]").forEach((button) => {
        button.addEventListener("click", () => addTeacherRow(button.dataset.plusSubject));
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
    const payload = rowsForSelectedBuilding().map((row) => {
        const fioTeacher = String(assignments[apiKeyOfRow(row)] || "").trim();
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
    teacherNames = sortRu(Array.from(new Set((teachers || []).map((t) => String(t.fioTeacher || "").trim()).filter(Boolean))));
    buildings = [...(buildingRows || [])].sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));

    prefillFromManualLoad();

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
        renderTable();
    });

    ui.sortDirection.addEventListener("change", () => {
        state.sortDirection = ui.sortDirection.value;
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
