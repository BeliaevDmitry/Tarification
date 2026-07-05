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
    personnelPanel: document.getElementById("teachers-personnel-panel"),
    contentCard: document.getElementById("teachers-content-card"),
    sectionTitle: document.getElementById("teachers-section-title"),
    mainPanel: document.getElementById("teachers-main-panel"),
    archivePanel: document.getElementById("teachers-archive-panel"),
    archiveBody: document.getElementById("teachers-archive-body"),
    dismissalsPanel: document.getElementById("teachers-dismissals-panel"),
    settingsPanel: document.getElementById("teachers-settings-panel"),
    salarySettingsForm: document.getElementById("salary-settings-form"),
    salaryStudentHourRate: document.getElementById("salary-student-hour-rate"),
    salarySettingsStatus: document.getElementById("salary-settings-status"),
    coefficientsPanel: document.getElementById("teachers-coefficients-panel"),
    groupCoefficientsPanel: document.getElementById("teachers-group-coefficients-panel"),
    coefficientFileInput: document.getElementById("coefficient-file"),
    coefficientImportBtn: document.getElementById("import-coefficients-btn"),
    coefficientForm: document.getElementById("coefficient-form"),
    coefficientSubjectName: document.getElementById("coefficient-subject-name"),
    coefficientEducationStage: document.getElementById("coefficient-education-stage"),
    coefficientValue: document.getElementById("coefficient-value"),
    coefficientRefreshBtn: document.getElementById("refresh-coefficients-btn"),
    coefficientsBody: document.getElementById("coefficients-body"),
    groupCoefficientForm: document.getElementById("group-coefficient-form"),
    groupCoefficientSubjectName: document.getElementById("group-coefficient-subject-name"),
    groupCoefficientRefreshBtn: document.getElementById("refresh-group-coefficients-btn"),
    groupCoefficientSortBtn: document.getElementById("sort-group-coefficients-btn"),
    groupCoefficientsBody: document.getElementById("group-coefficients-body"),
    mckoPanel: document.getElementById("teachers-mcko-panel"),
    mckoCertificatesPanel: document.getElementById("mcko-certificates-panel"),
    mckoSubjectsPanel: document.getElementById("mcko-subjects-panel"),
    mckoModeSelect: document.getElementById("mcko-mode-select"),
    mckoImportFile: document.getElementById("mcko-import-file"),
    mckoImportBtn: document.getElementById("mcko-import-btn"),
    mckoExportLink: document.getElementById("mcko-export-link"),
    mckoManualForm: document.getElementById("mcko-manual-form"),
    mckoTeacherSelect: document.getElementById("mcko-teacher-select"),
    mckoSubjectSelect: document.getElementById("mcko-subject-select"),
    mckoExamType: document.getElementById("mcko-exam-type"),
    mckoDate: document.getElementById("mcko-date"),
    mckoLevel: document.getElementById("mcko-level"),
    mckoPublished: document.getElementById("mcko-published"),
    mckoComment: document.getElementById("mcko-comment"),
    mckoScan: document.getElementById("mcko-scan"),
    mckoEligibilityBody: document.getElementById("mcko-eligibility-body"),
    mckoSubjectForm: document.getElementById("mcko-subject-form"),
    mckoMappingSubjectName: document.getElementById("mcko-mapping-subject-name"),
    mckoLoadSubjectSelect: document.getElementById("mcko-load-subject-select"),
    mckoSubjectsBody: document.getElementById("mcko-subjects-body"),
    dismissalsBody: document.getElementById("teachers-dismissals-body"),
    result: document.getElementById("teachers-result"),
    tbody: document.getElementById("teachers-table-body")
};
let buildings = [];
let groupCoefficientSubjectCatalog = [];
let teacherRows = [];
let subjectCatalogRows = [];
let mckoMappings = [];
let mckoCertificates = [];

function currentAuthUser() {
    return window.tarificationAuth || null;
}

function canEditTeacherPermission(permissionKey) {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    const permissions = window.tarificationTabPermissions || {};
    return Boolean(permissions[permissionKey]?.canEdit);
}

function canEditTeachers() {
    const tab = teachersTabFromHash();
    const permissionKey = tab === "archive" ? "TEACHERS_ARCHIVE"
        : tab === "dismissals" ? "TEACHERS_DISMISSALS"
            : isSettingsLikeTab(tab) ? "TEACHERS_SETTINGS"
                : isMckoTab(tab) ? "TEACHERS_MCKO" : "TEACHERS";
    return canEditTeacherPermission(permissionKey);
}

function settingsPermission() {
    const user = currentAuthUser() || {};
    const permissions = window.tarificationTabPermissions || {};
    return Boolean(user.admin || permissions.TEACHERS_SETTINGS?.canView);
}

function isSettingsLikeTab(tab) {
    return tab === "settings" || tab === "coefficients" || tab === "group-coefficients";
}

function isMckoTab(tab) {
    return tab === "mcko" || tab === "mcko-subjects";
}

function canViewTeachersTab(tab) {
    const user = currentAuthUser() || {};
    if (user.admin) return true;
    const permissions = window.tarificationTabPermissions || {};
    const permissionKey = tab === "archive" ? "TEACHERS_ARCHIVE"
        : tab === "dismissals" ? "TEACHERS_DISMISSALS"
            : isSettingsLikeTab(tab) ? "TEACHERS_SETTINGS"
                : isMckoTab(tab) ? "TEACHERS_MCKO" : "TEACHERS";
    return Boolean(permissions[permissionKey]?.canView);
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

function teachersTabFromHash() {
    const hash = String(window.location.hash || "").toLowerCase();
    if (hash === "#archive") return "archive";
    if (hash === "#dismissals") return "dismissals";
    if (hash === "#settings") return "settings";
    if (hash === "#coefficients") return "coefficients";
    if (hash === "#group-coefficients") return "group-coefficients";
    if (hash === "#mcko") return "mcko";
    if (hash === "#mcko-subjects") return "mcko-subjects";
    return "main";
}

function updateHeaderNavActive(tab) {
    document.querySelectorAll('.page-nav .nav-link').forEach((link) => {
        const href = link.getAttribute('href') || '';
        const active = (tab === "main" && href === "/teachers.html")
            || (tab === "archive" && href === "/teachers.html#archive")
            || (tab === "dismissals" && href === "/teachers.html#dismissals")
            || (tab === "settings" && href === "/teachers.html#settings")
            || (tab === "coefficients" && href === "/teachers.html#coefficients")
            || (tab === "group-coefficients" && href === "/teachers.html#group-coefficients")
            || (tab === "mcko" && href === "/teachers.html#mcko")
            || (tab === "mcko-subjects" && href === "/teachers.html#mcko-subjects");
        if (active) {
            link.classList.add('active');
        } else if (href.startsWith('/teachers.html')) {
            link.classList.remove('active');
        }
    });
}

function showTeachersTab(tab = teachersTabFromHash()) {
    const safeTab = canViewTeachersTab(tab)
        ? tab
        : ["main", "archive", "dismissals", "settings", "coefficients", "group-coefficients", "mcko", "mcko-subjects"].find(canViewTeachersTab) || "main";
    if (safeTab !== tab) {
        history.replaceState(null, '', '/teachers.html');
    }
    if (ui.personnelPanel) ui.personnelPanel.style.display = safeTab === "main" ? "" : "none";
    if (ui.mainPanel) ui.mainPanel.style.display = safeTab === "main" ? "" : "none";
    if (ui.archivePanel) ui.archivePanel.style.display = safeTab === "archive" ? "" : "none";
    if (ui.dismissalsPanel) ui.dismissalsPanel.style.display = safeTab === "dismissals" ? "" : "none";
    if (ui.settingsPanel) ui.settingsPanel.style.display = isSettingsLikeTab(safeTab) ? "" : "none";
    if (ui.coefficientsPanel) ui.coefficientsPanel.style.display = safeTab === "coefficients" ? "" : "none";
    if (ui.groupCoefficientsPanel) ui.groupCoefficientsPanel.style.display = safeTab === "group-coefficients" ? "" : "none";
    if (ui.mckoPanel) ui.mckoPanel.style.display = isMckoTab(safeTab) ? "" : "none";
    if (ui.mckoCertificatesPanel) ui.mckoCertificatesPanel.style.display = safeTab === "mcko" ? "" : "none";
    if (ui.mckoSubjectsPanel) ui.mckoSubjectsPanel.style.display = safeTab === "mcko-subjects" ? "" : "none";
    if (ui.sectionTitle) {
        ui.sectionTitle.textContent = safeTab === "archive" ? "Архив" : safeTab === "dismissals" ? "Увольнения" : isSettingsLikeTab(safeTab) ? "Настройки расчёта ЗП" : isMckoTab(safeTab) ? "МЦКО" : "Кадры";
    }
    updateHeaderNavActive(safeTab);
}

function applySalarySettingsVisibility() {
    const allowed = settingsPermission();
    document.querySelectorAll('a[href="/teachers.html#settings"]').forEach((link) => {
        link.style.display = allowed ? '' : 'none';
    });
    if (!allowed && ui.settingsPanel?.style.display !== "none") {
        showTeachersTab("main");
    }
}

function applyMckoVisibility() {
    const user = currentAuthUser() || {};
    const permissions = window.tarificationTabPermissions || {};
    const allowed = Boolean(user.admin || permissions.TEACHERS_MCKO?.canView);
    document.querySelectorAll('a[href="/teachers.html#mcko"], a[href="/teachers.html#mcko-subjects"]').forEach((link) => {
        link.style.display = allowed ? '' : 'none';
    });
    if (!allowed && ui.mckoPanel?.style.display !== "none") {
        showTeachersTab("main");
    }
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
                        ${row.dismissalDate ? "" : `<button type="button" class="mark-dismiss-btn" data-id="${row.id}" ${canEditTeacherPermission("TEACHERS_DISMISSALS") ? "" : "disabled title=\"Требуется право редактирования увольнений\""}>На увольнение</button>`}
                        <input type="date" class="plan-dismiss-date-input" value="${escapeHtml(row.plannedDismissalDate || "")}" data-id="${row.id}" data-allow-readonly="true">
                        <input type="text" class="plan-dismiss-comment-input" value="${escapeHtml(row.plannedDismissalComment || "")}" data-id="${row.id}" placeholder="Комментарий" data-allow-readonly="true">
                        ${row.dismissalDate ? "" : `<button type="button" class="mark-plan-dismiss-btn" data-id="${row.id}" data-allow-readonly="true" ${canEditTeacherPermission("TEACHERS_DISMISSALS") ? "" : "disabled"}>${row.plannedDismissalDate ? "Обновить план" : "Планирует уволиться"}</button>`}
                        ${row.plannedDismissalDate && !row.dismissalDate ? `<button type="button" class="cancel-plan-dismiss-btn" data-id="${row.id}" ${canEditTeacherPermission("TEACHERS_DISMISSALS") ? "" : "disabled"}>Передумал</button>` : ""}
                        ${row.dismissalDate ? `<button type="button" class="restore-teacher-btn" data-id="${row.id}" ${canEditTeacherPermission("TEACHERS_DISMISSALS") ? "" : "disabled"}>Восстановить</button>` : ""}
                        <button type="button" class="archive-teacher-btn" data-id="${row.id}" ${canEditTeacherPermission("TEACHERS_ARCHIVE") ? "" : "disabled"}>В архив</button>
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
            if (!canEditTeacherPermission("TEACHERS_DISMISSALS")) {
                print({ error: "Недостаточно прав: кнопка «На увольнение» доступна только с правом редактирования кадров" });
                return;
            }
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

    ui.tbody.querySelectorAll(".cancel-plan-dismiss-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!canEditTeacherPermission("TEACHERS_DISMISSALS")) {
                print({ error: "Недостаточно прав для отмены планируемого увольнения" });
                return;
            }
            try {
                const result = await api(`/api/teachers/${btn.dataset.id}/cancel-plan-dismiss`, { method: "PATCH" });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    ui.tbody.querySelectorAll(".archive-teacher-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            try {
                const result = await api(`/api/teachers/${btn.dataset.id}/archive`, { method: "PATCH" });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
}

async function loadTeachers() {
    const [rows, archivedRows] = await Promise.all([
        api('/api/teachers'),
        api('/api/teachers/archive')
    ]);
    teacherRows = rows || [];
    renderTeachers(rows || []);
    renderDismissals(rows || []);
    renderArchive(archivedRows || []);
    renderMckoTeacherOptions();
    return rows;
}

function renderMckoTeacherOptions() {
    if (!ui.mckoTeacherSelect) return;
    ui.mckoTeacherSelect.innerHTML = (teacherRows || [])
        .filter((row) => !row.dismissalDate)
        .slice()
        .sort((a, b) => String(a.fioTeacher || "").localeCompare(String(b.fioTeacher || ""), "ru"))
        .map((row) => `<option value="${escapeHtml(row.id)}">${escapeHtml(row.fioTeacher || "")}</option>`)
        .join("") || `<option value="">Нет педагогов</option>`;
}

async function ensureSubjectCatalog() {
    if (subjectCatalogRows.length) return subjectCatalogRows;
    subjectCatalogRows = await api("/api/subjects") || [];
    renderMckoLoadSubjectOptions();
    return subjectCatalogRows;
}

function renderMckoLoadSubjectOptions() {
    if (!ui.mckoLoadSubjectSelect) return;
    const used = new Set((mckoMappings || []).map((row) => `${String(row.mckoSubject || "").toLowerCase()}|${row.subjectId}`));
    const mckoSubject = String(ui.mckoMappingSubjectName?.value || "").trim().toLowerCase();
    ui.mckoLoadSubjectSelect.innerHTML = (subjectCatalogRows || [])
        .filter((subject) => !subject.subjectType || subject.subjectType === "CORE")
        .filter((subject) => !mckoSubject || !used.has(`${mckoSubject}|${subject.id}`))
        .slice()
        .sort((a, b) => String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru"))
        .map((subject) => `<option value="${escapeHtml(subject.id)}">${escapeHtml(subject.subjectName || "")}</option>`)
        .join("") || `<option value="">Все предметы уже добавлены</option>`;
}

function knownMckoSubjects() {
    return Array.from(new Set([
        ...(mckoMappings || []).map((row) => row.mckoSubject),
        ...(mckoCertificates || []).map((row) => row.mckoSubject)
    ].map((value) => String(value || "").trim()).filter(Boolean)))
        .sort((a, b) => a.localeCompare(b, "ru"));
}

function renderMckoSubjectSelectors() {
    const subjects = knownMckoSubjects();
    if (ui.mckoMappingSubjectName) {
        ui.mckoMappingSubjectName.innerHTML = subjects
            .map((name) => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`)
            .join("") || `<option value="">Сначала загрузите выгрузку МЦКО</option>`;
    }
    if (ui.mckoSubjectSelect) {
        ui.mckoSubjectSelect.innerHTML = subjects
            .map((name) => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`)
            .join("") || `<option value="">Сначала загрузите выгрузку МЦКО</option>`;
    }
    renderMckoLoadSubjectOptions();
}

function updateMckoExportLink() {
    if (!ui.mckoExportLink) return;
    const mode = ui.mckoModeSelect?.value || "all";
    ui.mckoExportLink.href = `/api/mcko/certificates/export?mode=${encodeURIComponent(mode)}`;
}

function mckoStatusClass(status) {
    if (status === "MISSING") return "mcko-status-missing";
    if (status === "WARNING") return "mcko-status-warning";
    return "mcko-status-ok";
}

async function reloadMckoCertificates() {
    updateMckoExportLink();
    const mode = ui.mckoModeSelect?.value || "all";
    mckoCertificates = await api(`/api/mcko/certificates?mode=${encodeURIComponent(mode)}`) || [];
    renderMckoSubjectSelectors();
}

async function reloadMckoEligibility() {
    if (!ui.mckoEligibilityBody) return;
    const rows = await api("/api/mcko/eligibility") || [];
    ui.mckoEligibilityBody.innerHTML = rows
        .slice()
        .sort((a, b) => String(a.teacherFio || "").localeCompare(String(b.teacherFio || ""), "ru")
            || String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru"))
        .map((row) => `
            <tr class="${mckoStatusClass(row.status)}">
                <td>${escapeHtml(row.teacherFio || "")}</td>
                <td>${escapeHtml(row.subjectName || "")}</td>
                <td>${escapeHtml(row.message || (row.status === "OK" ? "Есть" : "НЕТ МЦКО"))}</td>
                <td>${escapeHtml(row.level || "")}</td>
                <td>${escapeHtml(row.diagnosticDate || "")}</td>
                <td>${escapeHtml(row.expiresAt || "")}</td>
            </tr>
        `).join("") || `<tr><td colspan="6">Сначала добавьте соответствия предметов МЦКО</td></tr>`;
}

async function reloadMckoMappings() {
    if (!canViewTeachersTab("mcko-subjects") && !canViewTeachersTab("mcko")) return;
    await ensureSubjectCatalog();
    mckoMappings = await api("/api/mcko/subjects") || [];
    renderMckoSubjectSelectors();
    if (!ui.mckoSubjectsBody) return;
    const rows = mckoMappings.slice().sort((a, b) =>
        String(a.mckoSubject || "").localeCompare(String(b.mckoSubject || ""), "ru")
        || String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru")
    );
    ui.mckoSubjectsBody.innerHTML = rows.map((row) => `
        <tr>
            <td>${escapeHtml(row.mckoSubject || "")}</td>
            <td>${escapeHtml(row.subjectName || "")}</td>
            <td><button type="button" class="danger-btn" data-delete-mcko-mapping="${escapeHtml(row.id)}">Удалить</button></td>
        </tr>
    `).join("") || `<tr><td colspan="3">Соответствий пока нет</td></tr>`;
    ui.mckoSubjectsBody.querySelectorAll("[data-delete-mcko-mapping]").forEach((button) => {
        button.addEventListener("click", async () => {
            await api(`/api/mcko/subjects/${encodeURIComponent(button.dataset.deleteMckoMapping)}`, { method: "DELETE" });
            await reloadMckoMappings();
            await reloadMckoCertificates();
        });
    });
}

async function importMckoCertificates() {
    const file = ui.mckoImportFile?.files?.[0];
    if (!file) return print({ error: "Выберите Excel-файл МЦКО" });
    const form = new FormData();
    form.append("file", file);
    const result = await api("/api/mcko/certificates/import", { method: "POST", body: form });
    print(result);
    alert(`МЦКО: импортировано ${result?.importedRows ?? 0}, пропущено ${result?.skippedRows ?? 0}`);
    ui.mckoImportFile.value = "";
    await reloadMckoCertificates();
    await reloadMckoMappings();
    await reloadMckoEligibility();
}

async function saveManualMcko(event) {
    event.preventDefault();
    const teacherId = Number(ui.mckoTeacherSelect?.value || 0);
    const mckoSubject = String(ui.mckoSubjectSelect?.value || "").trim();
    const examType = String(ui.mckoExamType?.value || "").trim();
    const diagnosticDate = ui.mckoDate?.value || "";
    if (!teacherId || !mckoSubject || !examType || !diagnosticDate) {
        return print({ error: "Заполните педагога, предмет МЦКО, тип экзамена и дату" });
    }
    const form = new FormData();
    form.append("teacherId", String(teacherId));
    form.append("mckoSubject", mckoSubject);
    form.append("examType", examType);
    form.append("diagnosticDate", diagnosticDate);
    form.append("level", ui.mckoLevel?.value || "Высокий");
    form.append("published", ui.mckoPublished?.checked ? "true" : "false");
    form.append("comment", String(ui.mckoComment?.value || "").trim());
    if (ui.mckoScan?.files?.[0]) {
        form.append("scan", ui.mckoScan.files[0]);
    }
    const result = await api("/api/mcko/certificates", { method: "POST", body: form });
    print(result);
    ui.mckoManualForm.reset();
    if (ui.mckoPublished) ui.mckoPublished.checked = true;
    await reloadMckoCertificates();
    await reloadMckoEligibility();
}

async function saveMckoMapping(event) {
    event.preventDefault();
    const subjectId = Number(ui.mckoLoadSubjectSelect.value || 0);
    const mckoSubject = String(ui.mckoMappingSubjectName.value || "").trim();
    if (!mckoSubject || !Number.isFinite(subjectId) || subjectId <= 0) {
        return print({ error: "Выберите предмет МЦКО и предмет нагрузки из списка" });
    }
    const result = await api("/api/mcko/subjects", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({ mckoSubject, subjectId })
    });
    print(result);
    ui.mckoSubjectForm.reset();
    await reloadMckoMappings();
}

async function loadMckoTabData(tab = teachersTabFromHash()) {
    if (!isMckoTab(tab)) return;
    await reloadMckoCertificates();
    await reloadMckoMappings();
    if (tab === "mcko") await reloadMckoEligibility();
}

function renderArchive(rows) {
    if (!ui.archiveBody) return;
    if (!rows.length) {
        ui.archiveBody.innerHTML = '<tr><td colspan="7">В архиве пока нет сотрудников.</td></tr>';
        return;
    }
    ui.archiveBody.innerHTML = rows.map((row) => `
        <tr>
            <td>${escapeHtml(row.fioTeacher || "")}</td>
            <td>${escapeHtml(row.phone || "")}</td>
            <td>${escapeHtml(row.email || "")}</td>
            <td>${escapeHtml(row.numberSchoolBuilding || "")}</td>
            <td>${escapeHtml(row.dismissalDate || "")}</td>
            <td>${escapeHtml(row.archivedAt || "")}</td>
            <td><button type="button" class="unarchive-teacher-btn" data-id="${row.id}" ${canEditTeacherPermission("TEACHERS_ARCHIVE") ? "" : "disabled"}>Вернуть в персонал</button></td>
        </tr>
    `).join("");
    ui.archiveBody.querySelectorAll(".unarchive-teacher-btn").forEach((btn) => {
        btn.addEventListener("click", async () => {
            try {
                const result = await api(`/api/teachers/${btn.dataset.id}/unarchive`, { method: "PATCH" });
                print(result);
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
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

async function loadSalarySettings() {
    if (!salaryPermission() || !ui.salaryStudentHourRate) return;
    try {
        const settings = await api('/api/salary-settings');
        ui.salaryStudentHourRate.value = settings?.studentHourRate ?? 37;
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = 'Текущее значение загружено.';
    } catch (error) {
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = `Ошибка загрузки настроек: ${error.message}`;
    }
}

async function saveSalarySettings(event) {
    event.preventDefault();
    if (!salaryPermission()) {
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = 'Нет прав на изменение настроек зарплаты.';
        return;
    }
    const studentHourRate = Number(ui.salaryStudentHourRate?.value || 0);
    if (!Number.isFinite(studentHourRate) || studentHourRate <= 0) {
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = 'Укажите положительное значение человеко-часа.';
        return;
    }
    try {
        const result = await api('/api/salary-settings', {
            method: 'PUT',
            headers: jsonHeaders,
            body: JSON.stringify({ studentHourRate })
        });
        ui.salaryStudentHourRate.value = result?.studentHourRate ?? studentHourRate;
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = 'Настройки сохранены.';
    } catch (error) {
        if (ui.salarySettingsStatus) ui.salarySettingsStatus.textContent = `Ошибка сохранения: ${error.message}`;
    }
}

const stageLabel = (value) => {
    if (value === "NOO") return "НОО";
    if (value === "OOO") return "ООО";
    if (value === "SOO") return "СОО";
    return value || "";
};

function parseCoefficient(value) {
    const parsed = Number(String(value ?? "").trim().replace(",", "."));
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}

function formatCoefficient(value) {
    const safe = parseCoefficient(value);
    const text = safe.toFixed(4);
    return text.replace(/0+$/, "").replace(/\.$/, "");
}

async function reloadCoefficients() {
    if (!settingsPermission() || !ui.coefficientsBody) return;
    const rows = await api("/api/subjects/coefficients");
    ui.coefficientsBody.innerHTML = (rows || [])
        .slice()
        .sort((a, b) => String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru") || String(a.educationStage || "").localeCompare(String(b.educationStage || "")))
        .map((row) => `
            <tr>
                <td>${escapeHtml(`${row.subjectName || ""} ${stageLabel(row.educationStage)}`.trim())}</td>
                <td>${escapeHtml(formatCoefficient(row.coefficient))}</td>
                <td><button type="button" class="danger-btn" data-delete-coefficient="${escapeHtml(row.id)}">Удалить</button></td>
            </tr>
        `).join("") || `<tr><td colspan="3">Записей нет</td></tr>`;
    ui.coefficientsBody.querySelectorAll("[data-delete-coefficient]").forEach((button) => {
        button.addEventListener("click", async () => {
            await api(`/api/subjects/coefficients/${encodeURIComponent(button.dataset.deleteCoefficient)}`, { method: "DELETE" });
            await reloadCoefficients();
        });
    });
}

async function importCoefficients() {
    const file = ui.coefficientFileInput?.files?.[0];
    if (!file) return print({ error: "Выберите файл коэффициентов" });
    const form = new FormData();
    form.append("file", file);
    const result = await api("/api/subjects/coefficients/import", { method: "POST", body: form });
    print(result);
    await reloadCoefficients();
}

async function saveCoefficient(event) {
    event.preventDefault();
    const result = await api("/api/subjects/coefficients", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({
            subjectName: ui.coefficientSubjectName.value.trim(),
            educationStage: ui.coefficientEducationStage.value,
            coefficient: parseCoefficient(ui.coefficientValue.value)
        })
    });
    print(result);
    ui.coefficientValue.value = "1";
    await reloadCoefficients();
}

let groupCoefficientSortAsc = true;

async function reloadGroupCoefficients() {
    if (!settingsPermission() || !ui.groupCoefficientsBody) return;
    const [subjects, rows] = await Promise.all([
        api("/api/subjects"),
        api("/api/salary-group-coefficient-subjects")
    ]);
    groupCoefficientSubjectCatalog = (subjects || []).slice().sort((a, b) => String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru"));
    renderGroupCoefficientSubjectOptions(rows || []);
    const sorted = (rows || []).slice().sort((a, b) => {
        const result = String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru");
        return groupCoefficientSortAsc ? result : -result;
    });
    ui.groupCoefficientsBody.innerHTML = sorted.map((row) => `
        <tr>
            <td>${escapeHtml(row.subjectName || "")}</td>
            <td>${escapeHtml(formatCoefficient(25))} / дети</td>
            <td><button type="button" class="danger-btn" data-delete-group-coefficient="${escapeHtml(row.id)}">Удалить</button></td>
        </tr>
    `).join("") || `<tr><td colspan="3">Записей нет</td></tr>`;
    ui.groupCoefficientsBody.querySelectorAll("[data-delete-group-coefficient]").forEach((button) => {
        button.addEventListener("click", async () => {
            await api(`/api/salary-group-coefficient-subjects/${encodeURIComponent(button.dataset.deleteGroupCoefficient)}`, { method: "DELETE" });
            await reloadGroupCoefficients();
        });
    });
}

function renderGroupCoefficientSubjectOptions(enabledRows = []) {
    if (!ui.groupCoefficientSubjectName) return;
    const enabledIds = new Set((enabledRows || []).map((row) => String(row.subjectId || "")).filter(Boolean));
    const options = groupCoefficientSubjectCatalog
        .filter((subject) => !enabledIds.has(String(subject.id)))
        .map((subject) => `<option value="${escapeHtml(subject.id)}">${escapeHtml(subject.subjectName || "")}</option>`);
    ui.groupCoefficientSubjectName.innerHTML = options.join("") || `<option value="">Все предметы уже добавлены</option>`;
}

async function saveGroupCoefficientSubject(event) {
    event.preventDefault();
    const subjectId = Number(ui.groupCoefficientSubjectName.value || 0);
    if (!Number.isFinite(subjectId) || subjectId <= 0) {
        print({ error: "Выберите предмет из списка" });
        return;
    }
    const result = await api("/api/salary-group-coefficient-subjects", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({ subjectId })
    });
    print(result);
    ui.groupCoefficientForm.reset();
    await reloadGroupCoefficients();
}

async function loadSettingsTabData(tab = teachersTabFromHash()) {
    if (tab === "settings") await loadSalarySettings();
    if (tab === "coefficients") await reloadCoefficients();
    if (tab === "group-coefficients") await reloadGroupCoefficients();
}

function bindEvents() {
    ui.importBtn.addEventListener('click', importTeachers);
    ui.downloadBtn.addEventListener('click', downloadTeachers);
    ui.createForm.addEventListener('submit', createTeacher);
    ui.refreshBtn.addEventListener('click', () => loadTeachers().catch((e) => print({ error: e.message })));
    ui.clearBtn.addEventListener('click', clearTeachers);
    window.addEventListener("hashchange", async () => {
        const tab = teachersTabFromHash();
        showTeachersTab(tab);
        await loadSettingsTabData(tab);
        await loadMckoTabData(tab);
    });
    ui.salarySettingsForm?.addEventListener("submit", saveSalarySettings);
    ui.coefficientImportBtn?.addEventListener("click", () => importCoefficients().catch((error) => print({ error: error.message })));
    ui.coefficientForm?.addEventListener("submit", (event) => saveCoefficient(event).catch((error) => print({ error: error.message })));
    ui.coefficientRefreshBtn?.addEventListener("click", () => reloadCoefficients().catch((error) => print({ error: error.message })));
    ui.groupCoefficientForm?.addEventListener("submit", (event) => saveGroupCoefficientSubject(event).catch((error) => print({ error: error.message })));
    ui.groupCoefficientRefreshBtn?.addEventListener("click", () => reloadGroupCoefficients().catch((error) => print({ error: error.message })));
    ui.groupCoefficientSortBtn?.addEventListener("click", () => {
        groupCoefficientSortAsc = !groupCoefficientSortAsc;
        reloadGroupCoefficients().catch((error) => print({ error: error.message }));
    });
    ui.mckoModeSelect?.addEventListener("change", async () => {
        try {
            await reloadMckoCertificates();
            await reloadMckoEligibility();
        } catch (error) {
            print({ error: error.message });
        }
    });
    ui.mckoImportBtn?.addEventListener("click", () => importMckoCertificates().catch((error) => print({ error: error.message })));
    ui.mckoManualForm?.addEventListener("submit", (event) => saveManualMcko(event).catch((error) => print({ error: error.message })));
    ui.mckoSubjectForm?.addEventListener("submit", (event) => saveMckoMapping(event).catch((error) => print({ error: error.message })));
    ui.mckoMappingSubjectName?.addEventListener("input", renderMckoLoadSubjectOptions);
}

async function init() {
    await waitForAuth();
    bindEvents();
    applySalarySettingsVisibility();
    applyMckoVisibility();
    showTeachersTab(teachersTabFromHash());
    try {
        await loadBuildings();
        await loadTeachers();
        await loadSettingsTabData(teachersTabFromHash());
        await loadMckoTabData(teachersTabFromHash());
    } catch (error) {
        print({ error: error.message });
    }
}

init();
