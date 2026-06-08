const jsonHeaders = { "Content-Type": "application/json" };
const DEFAULT_SUBJECT_AREA = "Русский язык и литература";

const ui = {
    fileInput: document.getElementById("subject-file"),
    importBtn: document.getElementById("import-subjects-btn"),
    form: document.getElementById("subject-form"),
    name: document.getElementById("subject-name"),
    type: document.getElementById("subject-type"),
    area: document.getElementById("subject-area"),
    refreshBtn: document.getElementById("refresh-subjects-btn"),
    clearBtn: document.getElementById("clear-subjects-btn"),
    result: document.getElementById("subjects-result"),
    body: document.getElementById("subjects-body"),
    editDialog: document.getElementById("subject-edit-dialog"),
    editForm: document.getElementById("subject-edit-form"),
    deleteBtn: document.getElementById("subject-delete-btn"),
    closeBtn: document.getElementById("subject-close-btn"),
    coefficientFileInput: document.getElementById("coefficient-file"),
    coefficientImportBtn: document.getElementById("import-coefficients-btn"),
    coefficientForm: document.getElementById("coefficient-form"),
    coefficientSubjectName: document.getElementById("coefficient-subject-name"),
    coefficientEducationStage: document.getElementById("coefficient-education-stage"),
    coefficientValue: document.getElementById("coefficient-value"),
    coefficientRefreshBtn: document.getElementById("refresh-coefficients-btn"),
    coefficientsBody: document.getElementById("coefficients-body")
};

let subjects = [];
let subjectAreas = [];
let coefficients = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

const esc = (v) => String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
const print = (v) => { ui.result.textContent = JSON.stringify(v, null, 2); };
const typeLabel = (v) => {
    if (v === "CORRECTIONAL") return "4 тип: коррекционно-развивающая";
    if (v === "EXTRACURRICULAR") return "3 тип: внеурочная";
    if (v === "FORMABLE") return "2 тип: формируемая";
    return "1 тип: основная";
};
const stageLabel = (v) => {
    if (v === "NOO") return "НОО (1–4)";
    if (v === "OOO") return "ООО (5–9)";
    if (v === "SOO") return "СОО (10–11)";
    return v || "";
};

function render(rows) {
    ui.body.innerHTML = "";
    (rows || []).sort((a,b)=>String(a.subjectName).localeCompare(String(b.subjectName),"ru")).forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${esc(r.subjectName)}</td>
            <td>${esc(typeLabel(r.subjectType))}</td>
            <td>${esc(r.subjectAreaName || DEFAULT_SUBJECT_AREA)}</td>
        `;
        tr.addEventListener('click', () => openEdit(r));
        ui.body.appendChild(tr);
    });
}

function renderCoefficients(rows) {
    ui.coefficientsBody.innerHTML = "";
    (rows || [])
        .sort((a, b) => String(a.subjectName).localeCompare(String(b.subjectName), "ru") || String(a.educationStage).localeCompare(String(b.educationStage)))
        .forEach((r) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${esc(`${r.subjectName} ${stageLabel(r.educationStage).split(" ")[0]}`.trim())}</td>
                <td>${esc(formatCoefficient(r.coefficient))}</td>
                <td><button type="button" data-delete-coefficient="${esc(r.id)}" class="danger-btn">Удалить</button></td>
            `;
            tr.addEventListener("click", (event) => {
                if (event.target?.dataset?.deleteCoefficient) return;
                ui.coefficientSubjectName.value = r.subjectName;
                ui.coefficientEducationStage.value = r.educationStage;
                ui.coefficientValue.value = formatCoefficient(r.coefficient);
            });
            ui.coefficientsBody.appendChild(tr);
        });
    ui.coefficientsBody.querySelectorAll("button[data-delete-coefficient]").forEach((button) => {
        button.addEventListener("click", () => deleteCoefficient(button.dataset.deleteCoefficient));
    });
}

function openEdit(subject) {
    ui.editForm.elements.id.value = String(subject.id);
    ui.editForm.elements.subjectName.value = subject.subjectName;
    ui.editForm.elements.subjectType.value = subject.subjectType === "CORE_FORMABLE" ? "CORE" : subject.subjectType;
    const areaName = subject.subjectAreaName || DEFAULT_SUBJECT_AREA;
    if (![...ui.editForm.elements.subjectAreaName.options].some((opt) => opt.value === areaName)) {
        const customOption = document.createElement("option");
        customOption.value = areaName;
        customOption.textContent = areaName;
        ui.editForm.elements.subjectAreaName.appendChild(customOption);
    }
    ui.editForm.elements.subjectAreaName.value = areaName;
    ui.editDialog.showModal();
}

function normalizeAreaName(value) {
    const normalized = String(value || "").trim();
    return normalized || DEFAULT_SUBJECT_AREA;
}

function parseCoefficient(value) {
    const raw = String(value ?? "").trim().replace(",", ".");
    if (!raw) return 1;
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed <= 0) return 1;
    return parsed;
}

function formatCoefficient(value) {
    const parsed = parseCoefficient(value);
    const text = parsed.toFixed(2);
    return text.endsWith(".00") ? String(parsed.toFixed(0)) : text.replace(/0+$/, "").replace(/\.$/, "");
}

function applySubjectAreaOptions() {
    const areaNames = subjectAreas.map((row) => String(row.name || "").trim()).filter(Boolean);
    const unique = [...new Set(areaNames)];
    const formOptions = unique.map((name) => `<option value="${esc(name)}">${esc(name)}</option>`).join("");
    ui.area.innerHTML = formOptions;
    ui.editForm.elements.subjectAreaName.innerHTML = formOptions;
}

async function loadSubjectAreas() {
    subjectAreas = await api("/api/subject-areas");
    applySubjectAreaOptions();
}

async function reloadSubjects() {
    subjects = await api('/api/subjects');
    render(subjects || []);
}

async function reloadCoefficients() {
    coefficients = await api('/api/subjects/coefficients');
    renderCoefficients(coefficients || []);
}

async function reload() {
    await Promise.all([reloadSubjects(), reloadCoefficients()]);
}

ui.importBtn.addEventListener('click', async () => {
    const file = ui.fileInput.files?.[0];
    if (!file) return print({ error: 'Выберите файл' });
    const form = new FormData();
    form.append('file', file);
    try {
        const result = await api('/api/subjects/import', { method: 'POST', body: form });
        print(result);
        await reloadSubjects();
    } catch (e) { print({ error: e.message }); }
});

ui.form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const result = await api('/api/subjects', {
            method: 'POST', headers: jsonHeaders,
            body: JSON.stringify({
                subjectName: ui.name.value.trim(),
                subjectType: ui.type.value,
                subjectAreaName: normalizeAreaName(ui.area.value)
            })
        });
        print(result);
        ui.form.reset();
        ui.area.value = DEFAULT_SUBJECT_AREA;
        await reloadSubjects();
    } catch (e) { print({ error: e.message }); }
});

ui.editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = Number(ui.editForm.elements.id.value);
    try {
        const result = await api(`/api/subjects/${id}`, {
            method: 'PATCH', headers: jsonHeaders,
            body: JSON.stringify({
                subjectName: ui.editForm.elements.subjectName.value.trim(),
                subjectType: ui.editForm.elements.subjectType.value,
                subjectAreaName: normalizeAreaName(ui.editForm.elements.subjectAreaName.value)
            })
        });
        ui.editDialog.close();
        print(result);
        await reloadSubjects();
    } catch (e) { print({ error: e.message }); }
});

ui.coefficientImportBtn.addEventListener('click', async () => {
    const file = ui.coefficientFileInput.files?.[0];
    if (!file) return print({ error: 'Выберите файл коэффициентов' });
    const form = new FormData();
    form.append('file', file);
    try {
        const result = await api('/api/subjects/coefficients/import', { method: 'POST', body: form });
        print(result);
        await reloadCoefficients();
    } catch (e) { print({ error: e.message }); }
});

ui.coefficientForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const result = await api('/api/subjects/coefficients', {
            method: 'POST', headers: jsonHeaders,
            body: JSON.stringify({
                subjectName: ui.coefficientSubjectName.value.trim(),
                educationStage: ui.coefficientEducationStage.value,
                coefficient: parseCoefficient(ui.coefficientValue.value)
            })
        });
        print(result);
        ui.coefficientValue.value = "1";
        await reloadCoefficients();
    } catch (e) { print({ error: e.message }); }
});

async function deleteCoefficient(id) {
    if (!confirm('Удалить коэффициент?')) return;
    try {
        await api(`/api/subjects/coefficients/${encodeURIComponent(id)}`, { method: 'DELETE' });
        print({ status: 'deleted', id });
        await reloadCoefficients();
    } catch (e) { print({ error: e.message }); }
}

ui.deleteBtn.addEventListener('click', async () => {
    const id = Number(ui.editForm.elements.id.value);
    if (!confirm('Удалить предмет?')) return;
    try {
        await api(`/api/subjects/${id}`, { method: 'DELETE' });
        ui.editDialog.close();
        print({ status: 'deleted', id });
        await reloadSubjects();
    } catch (e) { print({ error: e.message }); }
});

ui.closeBtn.addEventListener('click', () => ui.editDialog.close());
ui.refreshBtn.addEventListener('click', () => reloadSubjects().catch((e) => print({ error: e.message })));
ui.coefficientRefreshBtn.addEventListener('click', () => reloadCoefficients().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener('click', async () => {
    try { await api('/api/subjects', { method: 'DELETE' }); print({ status: 'cleared' }); await reloadSubjects(); }
    catch (e) { print({ error: e.message }); }
});

Promise.all([loadSubjectAreas(), reload()]).catch((e) => print({ error: e.message }));
