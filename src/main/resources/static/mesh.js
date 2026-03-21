const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    mappingForm: document.getElementById("mapping-form"),
    mappingResult: document.getElementById("mapping-result"),
    loadSubjectsBtn: document.getElementById("load-subjects-btn"),
    loadClassesBtn: document.getElementById("load-classes-btn"),
    loadMappingsBtn: document.getElementById("load-mappings-btn"),
    subjectSelect: document.getElementById("subject-select"),
    classSelect: document.getElementById("class-select"),
    mappingsTableBody: document.getElementById("mappings-table-body")
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

function print(target, value) {
    if (!target) return;
    target.textContent = JSON.stringify(value, null, 2);
}

function formToObject(form) {
    const fd = new FormData(form);
    const obj = Object.fromEntries(fd.entries());
    Object.keys(obj).forEach((key) => {
        if (obj[key] === "") obj[key] = null;
    });
    return obj;
}

function resetSelect(select, placeholder) {
    if (!select) return;
    select.innerHTML = "";
    const option = document.createElement("option");
    option.value = "";
    option.textContent = placeholder;
    select.appendChild(option);
}

function appendOptions(select, values) {
    if (!select) return;
    values.forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
    });
}

function renderMappings(rows) {
    if (!ui.mappingsTableBody) return;
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

async function loadSubjects() {
    const subjects = await api("/api/naming-mesh/subjects");
    resetSelect(ui.subjectSelect, "Выберите предмет");
    appendOptions(ui.subjectSelect, subjects);
    resetSelect(ui.classSelect, "Сначала выберите предмет");
}

async function loadClasses() {
    const subject = ui.subjectSelect.value;
    if (!subject) throw new Error("Сначала выберите предмет");
    const classes = await api(`/api/naming-mesh/subjects/${encodeURIComponent(subject)}/classes`);
    resetSelect(ui.classSelect, "Все классы");
    appendOptions(ui.classSelect, classes);
}

async function loadMappings() {
    const subject = ui.subjectSelect.value;
    if (!subject) throw new Error("Сначала выберите предмет");
    const query = new URLSearchParams({ subjectName: subject });
    if (ui.classSelect.value) query.set("className", ui.classSelect.value);
    const rows = await api(`/api/naming-mesh/mappings?${query.toString()}`);
    renderMappings(rows);
}

async function onMappingSubmit(e) {
    e.preventDefault();
    try {
        const payload = formToObject(ui.mappingForm);
        const result = await api("/api/naming-mesh/mappings", { method: "PUT", headers: jsonHeaders, body: JSON.stringify(payload) });
        print(ui.mappingResult, result);
        await loadMappings();
    } catch (error) {
        print(ui.mappingResult, { error: error.message });
    }
}

function bindEvents() {
    ui.mappingForm?.addEventListener("submit", onMappingSubmit);
    ui.loadSubjectsBtn?.addEventListener("click", () => loadSubjects().catch((e) => print(ui.mappingResult, { error: e.message })));
    ui.loadClassesBtn?.addEventListener("click", () => loadClasses().catch((e) => print(ui.mappingResult, { error: e.message })));
    ui.loadMappingsBtn?.addEventListener("click", () => loadMappings().catch((e) => print(ui.mappingResult, { error: e.message })));
}

async function init() {
    bindEvents();
    resetSelect(ui.subjectSelect, "Загрузка предметов...");
    resetSelect(ui.classSelect, "Сначала выберите предмет");

    try {
        await loadSubjects();
    } catch (error) {
        print(ui.mappingResult, { error: error.message });
    }
}

function startAfterAuth() {
    init().catch((error) => print(ui.mappingResult, { error: error.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
