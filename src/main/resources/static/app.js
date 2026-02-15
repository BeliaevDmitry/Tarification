const jsonHeaders = { "Content-Type": "application/json" };

/**
 * КЛЮЧЕВОЕ: централизованные ссылки на элементы интерфейса.
 * Если поменяете id в HTML — обновляйте только этот объект.
 */
const ui = {
    manualLoadForm: document.getElementById("manual-load-form"),
    manualLoadResult: document.getElementById("manual-load-result"),
    processBtn: document.getElementById("process-btn"),
    processResult: document.getElementById("process-result"),
    mappingForm: document.getElementById("mapping-form"),
    mappingResult: document.getElementById("mapping-result"),
    loadSubjectsBtn: document.getElementById("load-subjects-btn"),
    loadClassesBtn: document.getElementById("load-classes-btn"),
    loadMappingsBtn: document.getElementById("load-mappings-btn"),
    subjectSelect: document.getElementById("subject-select"),
    classSelect: document.getElementById("class-select"),
    mappingsTableBody: document.getElementById("mappings-table-body")
};

/**
 * КЛЮЧЕВОЕ: единая функция запроса к API.
 * Если захотите вынести API на другой хост/порт — меняйте префикс здесь.
 */
async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (!response.ok) {
        throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    }

    return body;
}

function print(target, value) {
    target.textContent = JSON.stringify(value, null, 2);
}

/**
 * КЛЮЧЕВОЕ: единая нормализация данных формы.
 * Пустые поля -> null (чтобы сервер применял дефолты), числа -> Number.
 */
function formToObject(form) {
    const fd = new FormData(form);
    const obj = Object.fromEntries(fd.entries());

    Object.keys(obj).forEach((key) => {
        if (obj[key] === "") obj[key] = null;
    });

    if (obj.load != null) obj.load = Number(obj.load);
    if (obj.groupLoad != null) obj.groupLoad = Number(obj.groupLoad);

    return obj;
}

function resetSelect(select, placeholder) {
    select.innerHTML = "";
    const option = document.createElement("option");
    option.value = "";
    option.textContent = placeholder;
    select.appendChild(option);
}

function appendOptions(select, values) {
    values.forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
    });
}

async function loadSubjects() {
    const subjects = await api("/api/naming-mesh/subjects");
    resetSelect(ui.subjectSelect, "Выберите предмет");
    appendOptions(ui.subjectSelect, subjects);

    // Логично сбрасывать классы при смене предметного списка.
    resetSelect(ui.classSelect, "Сначала выберите предмет");
}

async function loadClasses() {
    const subject = ui.subjectSelect.value;
    if (!subject) {
        throw new Error("Сначала выберите предмет");
    }

    const classes = await api(`/api/naming-mesh/subjects/${encodeURIComponent(subject)}/classes`);
    resetSelect(ui.classSelect, "Все классы");
    appendOptions(ui.classSelect, classes);
}

function renderMappings(rows) {
    ui.mappingsTableBody.innerHTML = "";

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${row.subjectName ?? ""}</td>
            <td>${row.className ?? ""}</td>
            <td>${row.groupNameEducationalPlan ?? ""}</td>
            <td>${row.classNameMesh ?? ""}</td>
            <td>${row.groupNameMesh ?? ""}</td>
        `;
        ui.mappingsTableBody.appendChild(tr);
    });
}

async function loadMappings() {
    const subject = ui.subjectSelect.value;
    if (!subject) {
        throw new Error("Сначала выберите предмет");
    }

    const query = new URLSearchParams({ subjectName: subject });
    if (ui.classSelect.value) {
        query.set("className", ui.classSelect.value);
    }

    const rows = await api(`/api/naming-mesh/mappings?${query.toString()}`);
    renderMappings(rows);
}

async function onManualLoadSubmit(e) {
    e.preventDefault();
    try {
        const payload = formToObject(ui.manualLoadForm);
        const result = await api("/api/manual-load", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
        print(ui.manualLoadResult, result);
    } catch (error) {
        print(ui.manualLoadResult, { error: error.message });
    }
}

async function onProcessClick() {
    try {
        const result = await api("/api/manual-load/process", { method: "POST" });
        print(ui.processResult, result);
    } catch (error) {
        print(ui.processResult, { error: error.message });
    }
}

async function onMappingSubmit(e) {
    e.preventDefault();
    try {
        const payload = formToObject(ui.mappingForm);

        // КЛЮЧЕВОЕ: эта операция вручную фиксирует связь УП -> МЭШ.
        // Если classNameMesh/groupNameMesh пустые, сервер подставит значения из УП.
        const result = await api("/api/naming-mesh/mappings", {
            method: "PUT",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });

        print(ui.mappingResult, result);

        // После сохранения сразу обновляем таблицу, чтобы видеть итоговое состояние.
        await loadMappings();
    } catch (error) {
        print(ui.mappingResult, { error: error.message });
    }
}

function bindEvents() {
    ui.manualLoadForm.addEventListener("submit", onManualLoadSubmit);
    ui.processBtn.addEventListener("click", onProcessClick);
    ui.mappingForm.addEventListener("submit", onMappingSubmit);

    ui.loadSubjectsBtn.addEventListener("click", () => loadSubjects().catch((e) => print(ui.mappingResult, { error: e.message })));
    ui.loadClassesBtn.addEventListener("click", () => loadClasses().catch((e) => print(ui.mappingResult, { error: e.message })));
    ui.loadMappingsBtn.addEventListener("click", () => loadMappings().catch((e) => print(ui.mappingResult, { error: e.message })));
}

function init() {
    resetSelect(ui.subjectSelect, "Нажмите «Загрузить предметы»");
    resetSelect(ui.classSelect, "Сначала выберите предмет");
    bindEvents();
}

init();
