const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    fileInput: document.getElementById("teacher-file"),
    importBtn: document.getElementById("import-teachers-btn"),
    downloadBtn: document.getElementById("download-teachers-template-btn"),
    createForm: document.getElementById("teacher-create-form"),
    fioInput: document.getElementById("teacher-fio"),
    refreshBtn: document.getElementById("refresh-teachers-btn"),
    clearBtn: document.getElementById("clear-teachers-btn"),
    initialsCreate: document.getElementById("teacher-initials-create"),
    dativeCreate: document.getElementById("teacher-dative-create"),
    phoneCreate: document.getElementById("teacher-phone-create"),
    emailCreate: document.getElementById("teacher-email-create"),
    dutiesCreate: document.getElementById("teacher-duties-create"),
    buildingCreate: document.getElementById("teacher-building-create"),
    tabMainBtn: document.getElementById("teachers-tab-main-btn"),
    tabDismissalsBtn: document.getElementById("teachers-tab-dismissals-btn"),
    mainPanel: document.getElementById("teachers-main-panel"),
    dismissalsPanel: document.getElementById("teachers-dismissals-panel"),
    dismissalsBody: document.getElementById("teachers-dismissals-body"),
    result: document.getElementById("teachers-result"),
    tbody: document.getElementById("teachers-table-body")
};
let buildings = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(value) { ui.result.textContent = JSON.stringify(value, null, 2); }

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function statusLabel(row) {
    if (row.dismissalDate) return `На увольнение с ${row.dismissalDate}`;
    if (row.plannedDismissalDate) return `Планирует увольнение: ${row.plannedDismissalDate}`;
    return "Активен";
}

function renderBuildingOptions(selected = "") {
    const selectedNorm = String(selected || "").trim().toUpperCase();
    const options = ['<option value="">Корпус не указан</option>'];
    buildings.forEach((b) => {
        const code = String(b.code || "").trim();
        const label = `${code}${b.name ? ` — ${b.name}` : ""}`;
        const selectedAttr = code.toUpperCase() === selectedNorm ? "selected" : "";
        options.push(`<option value="${escapeHtml(code)}" ${selectedAttr}>${escapeHtml(label)}</option>`);
    });
    return options.join("");
}

function renderBuildingOptions(selected = "") {
    const selectedNorm = String(selected || "").trim().toUpperCase();
    const options = ['<option value="">Корпус не указан</option>'];
    buildings.forEach((b) => {
        const code = String(b.code || "").trim();
        const label = `${code}${b.name ? ` — ${b.name}` : ""}`;
        const selectedAttr = code.toUpperCase() === selectedNorm ? "selected" : "";
        options.push(`<option value="${escapeHtml(code)}" ${selectedAttr}>${escapeHtml(label)}</option>`);
    });
    return options.join("");
}

function renderTeachers(rows) {
    ui.tbody.innerHTML = "";
    rows
        .sort((a, b) => {
            const aDismiss = a.dismissalDate ? 0 : 1;
            const bDismiss = b.dismissalDate ? 0 : 1;
            if (aDismiss !== bDismiss) return aDismiss - bDismiss;
            return (a.fioTeacher || "").localeCompare(b.fioTeacher || "", "ru");
        })
        .forEach((row) => {
            const tr = document.createElement("tr");
            if (row.dismissalDate) tr.classList.add("dismissal-row");
            tr.innerHTML = `
                <td><input class="teacher-fio-input" data-id="${row.id}" value="${escapeHtml(row.fioTeacher || "")}" placeholder="ФИО"></td>
                <td><input class="teacher-initials-input" data-id="${row.id}" value="${escapeHtml(row.initials || "")}" placeholder="ФИО (инициалы)"></td>
                <td>
                    <input class="teacher-dative-input" data-id="${row.id}" value="${escapeHtml(row.fioTeacherDative || "")}" placeholder="Дательный падеж">
                </td>
                <td><input class="teacher-phone-input" data-id="${row.id}" value="${escapeHtml(row.phone || "")}" placeholder="+7 ..."></td>
                <td><input class="teacher-email-input" data-id="${row.id}" value="${escapeHtml(row.email || "")}" placeholder="email"></td>
                <td><input class="teacher-duties-input" data-id="${row.id}" value="${escapeHtml(row.additionalDuties || "")}" placeholder="Доп. обязанности"></td>
                <td><select class="teacher-building-input" data-id="${row.id}">${renderBuildingOptions(row.numberSchoolBuilding)}</select></td>
                <td>${escapeHtml(statusLabel(row))}</td>
                <td>
                    <div class="row">
                        <button type="button" class="save-teacher-btn" data-id="${row.id}">Сохранить</button>
                        <input type="date" class="dismiss-date-input" value="${escapeHtml(row.dismissalDate || "")}" data-id="${row.id}">
                        <button type="button" class="mark-dismiss-btn" data-id="${row.id}">На увольнение</button>
                        <input type="date" class="plan-dismiss-date-input" value="${escapeHtml(row.plannedDismissalDate || "")}" data-id="${row.id}">
                        <input type="text" class="plan-dismiss-comment-input" value="${escapeHtml(row.plannedDismissalComment || "")}" data-id="${row.id}" placeholder="Комментарий">
                        <button type="button" class="mark-plan-dismiss-btn" data-id="${row.id}">Планирует уволиться</button>
                        ${row.dismissalDate ? `<button type="button" class="restore-teacher-btn" data-id="${row.id}">Восстановить</button>` : ""}
                        <button type="button" class="danger-btn delete-teacher-btn" data-id="${row.id}">Удалить</button>
                    </div>
                </td>`;
            ui.tbody.appendChild(tr);
        });

    ui.tbody.querySelectorAll(".save-teacher-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.dataset.id;
            const fioTeacher = (ui.tbody.querySelector(`.teacher-fio-input[data-id="${id}"]`)?.value || "").trim();
            const initials = (ui.tbody.querySelector(`.teacher-initials-input[data-id="${id}"]`)?.value || "").trim();
            const input = ui.tbody.querySelector(`.teacher-dative-input[data-id="${id}"]`);
            const fioTeacherDative = (input?.value || "").trim();
            const phone = (ui.tbody.querySelector(`.teacher-phone-input[data-id="${id}"]`)?.value || "").trim();
            const email = (ui.tbody.querySelector(`.teacher-email-input[data-id="${id}"]`)?.value || "").trim();
            const additionalDuties = (ui.tbody.querySelector(`.teacher-duties-input[data-id="${id}"]`)?.value || "").trim();
            const numberSchoolBuilding = (ui.tbody.querySelector(`.teacher-building-input[data-id="${id}"]`)?.value || "").trim();
            try {
                const result = await api(`/api/teachers/${id}`, {
                    method: "PATCH",
                    headers: jsonHeaders,
                    body: JSON.stringify({ fioTeacher, fioTeacherDative, initials, phone, email, additionalDuties, numberSchoolBuilding })
                });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    ui.tbody.querySelectorAll(".mark-dismiss-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.dataset.id;
            const input = ui.tbody.querySelector(`.dismiss-date-input[data-id="${id}"]`);
            const dismissalDate = input?.value;
            if (!dismissalDate) {
                print({ error: "Укажите дату увольнения" });
                return;
            }
            try {
                const result = await api(`/api/teachers/${id}/dismiss`, {
                    method: "PATCH",
                    headers: jsonHeaders,
                    body: JSON.stringify({ dismissalDate })
                });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
    ui.tbody.querySelectorAll(".mark-plan-dismiss-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.dataset.id;
            const dateInput = ui.tbody.querySelector(`.plan-dismiss-date-input[data-id="${id}"]`);
            const commentInput = ui.tbody.querySelector(`.plan-dismiss-comment-input[data-id="${id}"]`);
            const plannedDismissalDate = dateInput?.value;
            const comment = (commentInput?.value || "").trim();
            if (!plannedDismissalDate) {
                print({ error: "Укажите планируемую дату увольнения" });
                return;
            }
            try {
                const result = await api(`/api/teachers/${id}/plan-dismiss`, {
                    method: "PATCH",
                    headers: jsonHeaders,
                    body: JSON.stringify({ plannedDismissalDate, comment })
                });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });


    ui.tbody.querySelectorAll(".restore-teacher-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.dataset.id;
            try {
                const result = await api(`/api/teachers/${id}/restore`, { method: "PATCH" });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    ui.tbody.querySelectorAll(".delete-teacher-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.dataset.id;
            try {
                await api(`/api/teachers/${id}`, { method: "DELETE" });
                print({ status: "teacher deleted", id });
                await loadTeachers();
            } catch (error) {
                print({ error: error.message, hint: "Если педагог назначен на нагрузку, сначала снимите нагрузку на странице /load.html" });
            }
        });
    });
}

async function loadTeachers() {
    const rows = await api('/api/teachers');
    renderTeachers(rows || []);
    renderDismissals(rows || []);
    return rows;
}

function renderDismissals(rows) {
    const dismissalRows = (rows || []).filter((r) => r.dismissalDate || r.plannedDismissalDate);
    if (!ui.dismissalsBody) return;
    if (!dismissalRows.length) {
        ui.dismissalsBody.innerHTML = `<tr><td colspan="5">Записей нет</td></tr>`;
        return;
    }
    ui.dismissalsBody.innerHTML = dismissalRows.map((r) => `
        <tr>
            <td>${escapeHtml(r.fioTeacher || "")}</td>
            <td>${escapeHtml(r.dismissalDate || "")}</td>
            <td>${escapeHtml(r.plannedDismissalDate || "")}</td>
            <td>${escapeHtml(r.plannedDismissalComment || "")}</td>
            <td>${escapeHtml(r.plannedDismissalMarkedBy || "")}</td>
        </tr>
    `).join("");
}

async function loadBuildings() {
    const rows = await api('/api/buildings');
    buildings = (rows || []).slice().sort((a, b) => String(a.code || "").localeCompare(String(b.code || ""), "ru"));
    if (ui.buildingCreate) {
        ui.buildingCreate.innerHTML = renderBuildingOptions("");
    }
}

async function importTeachers() {
    const file = ui.fileInput.files?.[0];
    if (!file) {
        print({ error: 'Выберите Excel файл' });
        return;
    }

    const form = new FormData();
    form.append('file', file);

    try {
        const result = await api('/api/teachers/import', { method: 'POST', body: form });
        print(result);
        await loadTeachers();
    } catch (error) {
        print({ error: error.message });
    }
}

function downloadTeachers() {
    window.location.href = '/api/teachers/export';
}

async function createTeacher(e) {
    e.preventDefault();
    const fioTeacher = (ui.fioInput.value || '').trim();
    const initials = (ui.initialsCreate.value || '').trim();
    const fioTeacherDative = (ui.dativeCreate.value || '').trim();
    const phone = (ui.phoneCreate.value || '').trim();
    const email = (ui.emailCreate.value || '').trim();
    const additionalDuties = (ui.dutiesCreate.value || '').trim();
    const numberSchoolBuilding = (ui.buildingCreate.value || '').trim();
    if (!fioTeacher) return;

    try {
        const result = await api('/api/teachers', {
            method: 'POST',
            headers: jsonHeaders,
            body: JSON.stringify({ fioTeacher, fioTeacherDative, initials, phone, email, additionalDuties, numberSchoolBuilding })
        });
        print(result);
        ui.createForm.reset();
        await loadTeachers();
    } catch (error) {
        print({ error: error.message });
    }
}

async function clearTeachers() {
    try {
        await api('/api/teachers', { method: 'DELETE' });
        print({ status: 'teacher directory cleared' });
        await loadTeachers();
    } catch (error) {
        print({ error: error.message });
    }
}

function bindEvents() {
    ui.importBtn.addEventListener('click', importTeachers);
    ui.downloadBtn.addEventListener('click', downloadTeachers);
    ui.createForm.addEventListener('submit', createTeacher);
    ui.refreshBtn.addEventListener('click', () => loadTeachers().catch((e) => print({ error: e.message })));
    ui.clearBtn.addEventListener('click', clearTeachers);
    ui.tabMainBtn?.addEventListener("click", () => {
        ui.mainPanel.style.display = "";
        ui.dismissalsPanel.style.display = "none";
    });
    ui.tabDismissalsBtn?.addEventListener("click", () => {
        ui.mainPanel.style.display = "none";
        ui.dismissalsPanel.style.display = "";
    });
}

async function init() {
    bindEvents();
    try {
        await loadBuildings();
        await loadTeachers();
    } catch (error) {
        print({ error: error.message });
    }
}

init();
