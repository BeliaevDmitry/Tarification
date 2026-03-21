const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    refreshBtn: document.getElementById("refresh-subjects-btn"),
    subjectsTableBody: document.getElementById("subjects-table-body"),
    editorPanel: document.getElementById("subject-editor-panel"),
    editorTitle: document.getElementById("subject-editor-title"),
    mappingForm: document.getElementById("mapping-form"),
    mappingResult: document.getElementById("mapping-result"),
    mappingsTableBody: document.getElementById("mappings-table-body")
};

let currentSubject = "";
let currentMappings = [];

function canEditMesh() {
    const role = window.getCurrentUser?.()?.role;
    return ["ADMIN", "DIRECTOR", "DEPUTY_DIRECTOR"].includes(role);
}

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

function esc(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function print(value) {
    ui.mappingResult.textContent = JSON.stringify(value, null, 2);
}

function renderSubjects(subjects) {
    ui.subjectsTableBody.innerHTML = "";
    (subjects || []).forEach((subject) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${esc(subject)}</td>
            <td>${canEditMesh() ? `<button type="button" data-edit-subject="${esc(subject)}">Редактировать</button>` : ""}</td>
        `;
        ui.subjectsTableBody.appendChild(tr);
    });
}

function fillForm(mapping = null) {
    ui.mappingForm.elements.namedItem("subjectName").value = currentSubject;
    ui.mappingForm.elements.namedItem("className").value = mapping?.className || "";
    ui.mappingForm.elements.namedItem("groupNameEducationalPlan").value = mapping?.groupNameEducationalPlan || "";
    ui.mappingForm.elements.namedItem("classNameMesh").value = mapping?.classNameMesh || "";
    ui.mappingForm.elements.namedItem("groupNameMesh").value = mapping?.groupNameMesh || "";
}

function renderMappings(mappings) {
    currentMappings = mappings || [];
    ui.mappingsTableBody.innerHTML = "";
    currentMappings.forEach((row, index) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${esc(row.className)}</td>
            <td>${esc(row.groupNameEducationalPlan)}</td>
            <td>${esc(row.classNameMesh)}</td>
            <td>${esc(row.groupNameMesh)}</td>
            <td><button type="button" data-edit-mapping="${index}">Редактировать</button></td>
        `;
        ui.mappingsTableBody.appendChild(tr);
    });
    fillForm();
}

async function loadSubjects() {
    const subjects = await api("/api/naming-mesh/subjects");
    renderSubjects(subjects);
}

async function openSubjectEditor(subject) {
    currentSubject = subject;
    ui.editorPanel.hidden = false;
    ui.editorTitle.textContent = `Редактирование предмета: ${subject}`;
    const rows = await api(`/api/naming-mesh/mappings?subjectName=${encodeURIComponent(subject)}`);
    renderMappings(rows);
    Array.from(ui.mappingForm.elements).forEach((element) => {
        if (element.tagName === "BUTTON") {
            element.hidden = !canEditMesh();
        } else if (element.name !== "subjectName") {
            element.disabled = !canEditMesh();
        }
    });
}

async function saveMapping(event) {
    event.preventDefault();
    const form = new FormData(ui.mappingForm);
    const payload = Object.fromEntries(form.entries());
    const result = await api("/api/naming-mesh/mappings", {
        method: "PUT",
        headers: jsonHeaders,
        body: JSON.stringify(payload)
    });
    print(result);
    await openSubjectEditor(currentSubject);
}

function bindEvents() {
    ui.refreshBtn?.addEventListener("click", () => loadSubjects().catch((error) => print({ error: error.message })));
    ui.mappingForm?.addEventListener("submit", (event) => saveMapping(event).catch((error) => print({ error: error.message })));
    ui.subjectsTableBody?.addEventListener("click", (event) => {
        const subject = event.target.dataset.editSubject;
        if (subject) {
            openSubjectEditor(subject).catch((error) => print({ error: error.message }));
        }
    });
    ui.mappingsTableBody?.addEventListener("click", (event) => {
        const index = event.target.dataset.editMapping;
        if (index == null) {
            return;
        }
        fillForm(currentMappings[Number(index)] || null);
    });
}

async function init() {
    bindEvents();
    await loadSubjects();
}

function startAfterAuth() {
    init().catch((error) => print({ error: error.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
