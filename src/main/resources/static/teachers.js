const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    fileInput: document.getElementById("teacher-file"),
    importBtn: document.getElementById("import-teachers-btn"),
    oneCFileInput: document.getElementById("teacher-1c-file"),
    oneCPreviewBtn: document.getElementById("preview-teachers-1c-btn"),
    oneCDialog: document.getElementById("teacher-1c-dialog"),
    oneCSummary: document.getElementById("teacher-1c-summary"),
    oneCPreviewBody: document.getElementById("teacher-1c-preview-body"),
    oneCFeedback: document.getElementById("teacher-1c-feedback"),
    oneCClose: document.getElementById("teacher-1c-close"),
    oneCCancel: document.getElementById("teacher-1c-cancel"),
    oneCApply: document.getElementById("teacher-1c-apply"),
    downloadBtn: document.getElementById("download-teachers-template-btn"),
    acceptTeacherBtn: document.getElementById("accept-teacher-btn"),
    autoAssignBuildingsBtn: document.getElementById("auto-assign-buildings-btn"),
    refreshBtn: document.getElementById("refresh-teachers-btn"),
    clearBtn: document.getElementById("clear-teachers-btn"),
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
    settingsInRateRulesList: document.getElementById("settings-in-rate-rules-list"),
    settingsNewRulePosition: document.getElementById("settings-new-rule-position"),
    settingsNewRuleLabel: document.getElementById("settings-new-rule-label"),
    settingsNewRuleMin: document.getElementById("settings-new-rule-min"),
    settingsNewRuleMax: document.getElementById("settings-new-rule-max"),
    settingsNewRuleIncluded: document.getElementById("settings-new-rule-included"),
    settingsNewRuleFraction: document.getElementById("settings-new-rule-fraction"),
    settingsAddInRateRuleBtn: document.getElementById("settings-add-in-rate-rule-btn"),
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
    mckoCancelEditBtn: document.getElementById("mcko-cancel-edit-btn"),
    mckoCertificatesBody: document.getElementById("mcko-certificates-body"),
    mckoSubjectsBody: document.getElementById("mcko-subjects-body"),
    dismissalsBody: document.getElementById("teachers-dismissals-body"),
    result: document.getElementById("teachers-result"),
    tbody: document.getElementById("teachers-table-body"),
    teacherCardDialog: document.getElementById("teacher-card-dialog"),
    teacherCardName: document.getElementById("teacher-card-name"),
    teacherCardClose: document.getElementById("teacher-card-close"),
    teacherContractSection: document.getElementById("teacher-contract-section"),
    teacherContractSelect: document.getElementById("teacher-contract-select"),
    teacherContractForm: document.getElementById("teacher-contract-form"),
    teacherContractNumber: document.getElementById("teacher-contract-number"),
    teacherContractDate: document.getElementById("teacher-contract-date"),
    teacherContractPosition: document.getElementById("teacher-contract-position"),
    teacherContractStart: document.getElementById("teacher-contract-start"),
    teacherContractEnd: document.getElementById("teacher-contract-end"),
    teacherContractPrimary: document.getElementById("teacher-contract-primary"),
    teacherContractActive: document.getElementById("teacher-contract-active"),
    teacherContractInRate: document.getElementById("teacher-contract-in-rate"),
    teacherContractInRateRule: document.getElementById("teacher-contract-in-rate-rule"),
    teacherContractInRateLabel: document.getElementById("teacher-contract-in-rate-label"),
    teacherContractSave: document.getElementById("teacher-contract-save"),
    teacherContractFeedback: document.getElementById("teacher-contract-feedback"),
    teacherCardPlanDate: document.getElementById("teacher-card-plan-date"),
    teacherCardPlanComment: document.getElementById("teacher-card-plan-comment"),
    teacherCardDismissDate: document.getElementById("teacher-card-dismiss-date"),
    teacherCardSavePlan: document.getElementById("teacher-card-save-plan"),
    teacherCardCancelPlan: document.getElementById("teacher-card-cancel-plan"),
    teacherCardDismiss: document.getElementById("teacher-card-dismiss"),
    teacherCardRestore: document.getElementById("teacher-card-restore"),
    teacherCardArchive: document.getElementById("teacher-card-archive"),
    teacherCardDelete: document.getElementById("teacher-card-delete"),
    teacherCardFeedback: document.getElementById("teacher-card-feedback"),
    teacherCardMainForm: document.getElementById("teacher-card-main-form"),
    teacherCardFio: document.getElementById("teacher-card-fio"),
    teacherCardInitials: document.getElementById("teacher-card-initials"),
    teacherCardDative: document.getElementById("teacher-card-dative"),
    teacherCardPhone: document.getElementById("teacher-card-phone"),
    teacherCardEmail: document.getElementById("teacher-card-email"),
    teacherCardBuilding: document.getElementById("teacher-card-building"),
    teacherCardPrimaryPosition: document.getElementById("teacher-card-primary-position"),
    teacherCardEmploymentType: document.getElementById("teacher-card-employment-type"),
    teacherCardEmploymentDate: document.getElementById("teacher-card-employment-date"),
    teacherCardDataSheet: document.getElementById("teacher-card-data-sheet"),
    teacherPersonalSection: document.getElementById("teacher-personal-section"),
    teacherPersonalDivider: document.getElementById("teacher-personal-divider"),
    teacherPersonalForm: document.getElementById("teacher-personal-form"),
    teacherPersonalBirthDate: document.getElementById("teacher-personal-birth-date"),
    teacherPersonalPhone: document.getElementById("teacher-personal-phone"),
    teacherPersonalPassportSeries: document.getElementById("teacher-personal-passport-series"),
    teacherPersonalPassportNumber: document.getElementById("teacher-personal-passport-number"),
    teacherPersonalPassportIssuedBy: document.getElementById("teacher-personal-passport-issued-by"),
    teacherPersonalPassportIssueDate: document.getElementById("teacher-personal-passport-issue-date"),
    teacherPersonalPassportCode: document.getElementById("teacher-personal-passport-code"),
    teacherPersonalRegistration: document.getElementById("teacher-personal-registration"),
    teacherPersonalActual: document.getElementById("teacher-personal-actual"),
    teacherPersonalInn: document.getElementById("teacher-personal-inn"),
    teacherPersonalSnils: document.getElementById("teacher-personal-snils"),
    acceptTeacherDialog: document.getElementById("accept-teacher-dialog"),
    acceptTeacherForm: document.getElementById("accept-teacher-form"),
    acceptTeacherClose: document.getElementById("accept-teacher-close"),
    acceptTeacherCancel: document.getElementById("accept-teacher-cancel"),
    acceptVacancy: document.getElementById("accept-vacancy"),
    acceptFio: document.getElementById("accept-fio"),
    acceptPhone: document.getElementById("accept-phone"),
    acceptEmail: document.getElementById("accept-email"),
    acceptBuilding: document.getElementById("accept-building"),
    acceptPosition: document.getElementById("accept-position"),
    acceptEmploymentType: document.getElementById("accept-employment-type"),
    acceptEmploymentDate: document.getElementById("accept-employment-date"),
    acceptBirthDate: document.getElementById("accept-birth-date"),
    acceptSnils: document.getElementById("accept-snils"),
    acceptInn: document.getElementById("accept-inn"),
    acceptPassportSeries: document.getElementById("accept-passport-series"),
    acceptPassportNumber: document.getElementById("accept-passport-number"),
    acceptPassportIssuedBy: document.getElementById("accept-passport-issued-by"),
    acceptPassportIssueDate: document.getElementById("accept-passport-issue-date"),
    acceptPassportCode: document.getElementById("accept-passport-code"),
    acceptRegistrationAddress: document.getElementById("accept-registration-address"),
    acceptActualAddress: document.getElementById("accept-actual-address"),
    acceptContractNumber: document.getElementById("accept-contract-number"),
    acceptContractDate: document.getElementById("accept-contract-date"),
    acceptContractStart: document.getElementById("accept-contract-start"),
    acceptContractEnd: document.getElementById("accept-contract-end"),
    acceptInRate: document.getElementById("accept-in-rate"),
    acceptInRateRule: document.getElementById("accept-in-rate-rule"),
    acceptTeacherFeedback: document.getElementById("accept-teacher-feedback")
};
let buildings = [];
let groupCoefficientSubjectCatalog = [];
let teacherRows = [];
let subjectCatalogRows = [];
let mckoMappings = [];
let mckoCertificates = [];
let mckoOverviewRows = [];
let oneCPreview = null;
let oneCPreviewFile = null;
let teacherCardContracts = [];
let teacherCardInRateRules = [];
let teacherPositions = [];
let teacherVacancies = [];
let editingMckoCertificateId = null;
let mckoCertificateSort = { key: "teacherFio", ascending: true };
const PRIMARY_MCKO_SUBJECT = "Метапредметные умения (начальное образование)";
const PRIMARY_MCKO_LABEL = "Начальная школа";

function mckoSubjectCanonical(value) {
    const text = String(value || "").trim();
    return text === PRIMARY_MCKO_LABEL ? PRIMARY_MCKO_SUBJECT : text;
}

function mckoSubjectLabel(value) {
    const text = String(value || "").trim();
    return text === PRIMARY_MCKO_SUBJECT ? PRIMARY_MCKO_LABEL : text;
}

function mckoSubjectCompareKey(value) {
    return mckoSubjectLabel(value).toLocaleLowerCase("ru");
}

function sameMckoSubject(left, right) {
    return mckoSubjectCompareKey(left) === mckoSubjectCompareKey(right);
}

function currentAuthUser() {
    return window.tarificationAuth || null;
}

function canEditTeacherPermission(permissionKey) {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    const permissions = window.tarificationTabPermissions || {};
    return Boolean(permissions[permissionKey]?.canEdit);
}

function canViewEmploymentContracts() {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    const permission = (window.tarificationTabPermissions || {}).HR_DOCUMENTS;
    return Boolean(permission?.canView || permission?.canEdit);
}

function canEditEmploymentContracts() {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    return Boolean((window.tarificationTabPermissions || {}).HR_DOCUMENTS?.canEdit);
}

function canViewPersonalData() {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    const permission = (window.tarificationTabPermissions || {}).HR_PERSONAL_DATA;
    return Boolean(permission?.canView || permission?.canEdit);
}

function canEditPersonalData() {
    const currentUser = currentAuthUser();
    if (currentUser?.admin) return true;
    return Boolean((window.tarificationTabPermissions || {}).HR_PERSONAL_DATA?.canEdit);
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

function selectedTeacherCardRow() {
    const teacherId = Number(ui.teacherCardDialog?.dataset.teacherId || 0);
    return teacherRows.find((row) => Number(row.id) === teacherId) || null;
}

function selectedTeacherContract() {
    const contractId = Number(ui.teacherContractSelect?.value || 0);
    return teacherCardContracts.find((contract) => Number(contract.id) === contractId) || null;
}

function renderTeacherContractForm(contract = null) {
    const teacher = selectedTeacherCardRow();
    ui.teacherContractNumber.value = contract?.contractNumber || "";
    ui.teacherContractDate.value = contract?.contractDate || "";
    const selectedPosition = contract?.positionName || teacher?.primaryPosition || "";
    ui.teacherContractPosition.innerHTML = positionOptions(selectedPosition);
    ui.teacherContractPosition.value = selectedPosition;
    ui.teacherContractStart.value = contract?.startDate || "";
    ui.teacherContractEnd.value = contract?.endDate || "";
    ui.teacherContractPrimary.checked = contract?.primaryContract !== false;
    ui.teacherContractActive.checked = contract?.active !== false;
    ui.teacherContractInRate.value = contract?.loadHoursMayBeIncludedInRate ? "true" : "false";
    ui.teacherContractInRateRule.innerHTML = [
        '<option value="">Без автоматического правила</option>',
        ...teacherCardInRateRules
            .filter((rule) => (rule.active && (!selectedPosition || rule.name === selectedPosition))
                || String(rule.id) === String(contract?.loadInRateRuleId || ""))
            .map((rule) => `<option value="${escapeHtml(rule.id)}" ${String(rule.id) === String(contract?.loadInRateRuleId || "") ? "selected" : ""}>${escapeHtml(rule.name || "")}</option>`)
    ].join("");
    ui.teacherContractInRateLabel.value = contract?.loadInRateDocumentLabel || "";
    const enabled = ui.teacherContractInRate.value === "true";
    ui.teacherCardDialog.querySelectorAll(".teacher-in-rate-field").forEach((field) => {
        field.hidden = !enabled;
    });
}

function setTeacherContractFormAccess() {
    const canEdit = canEditEmploymentContracts();
    ui.teacherContractForm.querySelectorAll("input, select, button").forEach((element) => {
        element.disabled = !canEdit;
    });
    ui.teacherContractSelect.disabled = false;
    ui.teacherContractFeedback.textContent = canEdit
        ? ""
        : "Договор доступен только для просмотра. Для изменения требуется право редактирования кадровых документов.";
}

async function openTeacherCard(teacherId, preferredContractId = null) {
    const teacher = teacherRows.find((row) => String(row.id) === String(teacherId));
    if (!teacher) throw new Error("Сотрудник не найден");
    ui.teacherCardDialog.dataset.teacherId = String(teacher.id);
    ui.teacherCardName.textContent = `${teacher.fioTeacher || ""} · ID ${teacher.id}`;
    ui.teacherCardPlanDate.value = teacher.plannedDismissalDate || "";
    ui.teacherCardPlanComment.value = teacher.plannedDismissalComment || "";
    ui.teacherCardDismissDate.value = teacher.dismissalDate || "";
    ui.teacherCardFeedback.textContent = "";
    ui.teacherCardFio.value = teacher.fioTeacher || "";
    ui.teacherCardInitials.value = teacher.initials || "";
    ui.teacherCardDative.value = teacher.fioTeacherDative || "";
    ui.teacherCardPhone.value = teacher.phone || "";
    ui.teacherCardEmail.value = teacher.email || "";
    ui.teacherCardBuilding.innerHTML = renderBuildingOptions(teacher.numberSchoolBuilding);
    ui.teacherCardPrimaryPosition.value = teacher.primaryPosition || "";
    ui.teacherCardEmploymentType.value = teacher.employmentType || "";
    ui.teacherCardEmploymentDate.value = teacher.employmentDate || "";
    const canViewPersonal = canViewPersonalData();
    ui.teacherPersonalSection.hidden = !canViewPersonal;
    ui.teacherPersonalDivider.hidden = !canViewPersonal;
    if (canViewPersonal) {
        const personal = await api(`/api/hr-documents/personal-data/${teacher.id}`).catch(() => null);
        ui.teacherPersonalBirthDate.value = personal?.birthDate || "";
        ui.teacherPersonalPhone.value = personal?.phone || teacher.phone || "";
        ui.teacherPersonalPassportSeries.value = personal?.passportSeries || "";
        ui.teacherPersonalPassportNumber.value = personal?.passportNumber || "";
        ui.teacherPersonalPassportIssuedBy.value = personal?.passportIssuedBy || "";
        ui.teacherPersonalPassportIssueDate.value = personal?.passportIssueDate || "";
        ui.teacherPersonalPassportCode.value = personal?.passportDepartmentCode || "";
        ui.teacherPersonalRegistration.value = personal?.registrationAddress || "";
        ui.teacherPersonalActual.value = personal?.actualAddress || "";
        ui.teacherPersonalInn.value = personal?.inn || "";
        ui.teacherPersonalSnils.value = personal?.snils || "";
        ui.teacherPersonalForm.querySelectorAll("input, button").forEach((element) => {
            element.disabled = !canEditPersonalData();
        });
    }

    const canEditDismissals = canEditTeacherPermission("TEACHERS_DISMISSALS");
    ui.teacherCardSavePlan.disabled = !canEditDismissals || Boolean(teacher.dismissalDate);
    ui.teacherCardCancelPlan.disabled = !canEditDismissals || !teacher.plannedDismissalDate || Boolean(teacher.dismissalDate);
    ui.teacherCardDismiss.hidden = Boolean(teacher.dismissalDate);
    ui.teacherCardDismiss.disabled = !canEditDismissals;
    ui.teacherCardRestore.hidden = !teacher.dismissalDate;
    ui.teacherCardRestore.disabled = !canEditDismissals;
    ui.teacherCardArchive.disabled = !canEditTeacherPermission("TEACHERS_ARCHIVE");
    ui.teacherCardDelete.disabled = !canEditTeacherPermission("TEACHERS");

    const canViewContracts = canViewEmploymentContracts();
    ui.teacherContractSection.hidden = !canViewContracts;
    teacherCardContracts = [];
    teacherCardInRateRules = [];
    if (canViewContracts) {
        const [contracts, rules] = await Promise.all([
            api(`/api/hr-documents/contracts?teacherId=${encodeURIComponent(teacher.id)}`),
            api("/api/manual-load/in-rate/rules").catch(() => [])
        ]);
        teacherCardContracts = contracts || [];
        teacherCardInRateRules = rules || [];
        const preferred = teacherCardContracts.find((contract) => String(contract.id) === String(preferredContractId));
        const current = preferred
            || teacherCardContracts.find((contract) => contract.primaryContract && contract.active)
            || teacherCardContracts[0]
            || null;
        ui.teacherContractSelect.innerHTML = [
            '<option value="">+ Новый трудовой договор</option>',
            ...teacherCardContracts.map((contract) =>
                `<option value="${escapeHtml(contract.id)}" ${current && String(contract.id) === String(current.id) ? "selected" : ""}>№ ${escapeHtml(contract.contractNumber)} от ${escapeHtml(contract.contractDate)} — ${escapeHtml(contract.positionName)}</option>`)
        ].join("");
        renderTeacherContractForm(current);
        setTeacherContractFormAccess();
    }
    if (!ui.teacherCardDialog.open) ui.teacherCardDialog.showModal();
}

function positionOptions(selected = "") {
    const values = new Set((teacherPositions || []).filter(Boolean));
    if (selected) values.add(selected);
    return ['<option value="">Должность не выбрана</option>',
        ...Array.from(values).sort((a, b) => a.localeCompare(b, "ru"))
            .map((value) => `<option value="${escapeHtml(value)}" ${value === selected ? "selected" : ""}>${escapeHtml(value)}</option>`)
    ].join("");
}

async function saveTeacherCardMain(event) {
    event.preventDefault();
    const teacher = selectedTeacherCardRow();
    if (!teacher) return;
    await api(`/api/teachers/${teacher.id}`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify({
            fioTeacher: ui.teacherCardFio.value.trim(),
            initials: ui.teacherCardInitials.value.trim(),
            fioTeacherDative: ui.teacherCardDative.value.trim(),
            phone: ui.teacherCardPhone.value.trim(),
            email: ui.teacherCardEmail.value.trim(),
            numberSchoolBuilding: ui.teacherCardBuilding.value,
            primaryPosition: ui.teacherCardPrimaryPosition.value.trim(),
            employmentType: ui.teacherCardEmploymentType.value.trim(),
            employmentDate: ui.teacherCardEmploymentDate.value || null
        })
    });
    await refreshTeacherCardAfterAction(teacher.id, "Данные сотрудника сохранены.");
}

function downloadTeacherDataSheet() {
    const teacher = selectedTeacherCardRow();
    if (teacher) window.location.href = `/api/teachers/${teacher.id}/data-sheet`;
}

async function saveTeacherPersonalData(event) {
    event.preventDefault();
    if (!canEditPersonalData()) return;
    const teacher = selectedTeacherCardRow();
    if (!teacher) return;
    await api(`/api/hr-documents/personal-data/${teacher.id}`, {
        method: "PUT",
        headers: jsonHeaders,
        body: JSON.stringify({
            teacherId: teacher.id,
            birthDate: ui.teacherPersonalBirthDate.value || null,
            passportSeries: ui.teacherPersonalPassportSeries.value.trim(),
            passportNumber: ui.teacherPersonalPassportNumber.value.trim(),
            passportIssuedBy: ui.teacherPersonalPassportIssuedBy.value.trim(),
            passportIssueDate: ui.teacherPersonalPassportIssueDate.value || null,
            passportDepartmentCode: ui.teacherPersonalPassportCode.value.trim(),
            registrationAddress: ui.teacherPersonalRegistration.value.trim(),
            actualAddress: ui.teacherPersonalActual.value.trim(),
            phone: ui.teacherPersonalPhone.value.trim(),
            inn: ui.teacherPersonalInn.value.trim(),
            snils: ui.teacherPersonalSnils.value.trim()
        })
    });
    ui.teacherCardFeedback.textContent = "Персональные данные сохранены.";
}

async function saveTeacherCardContract(event) {
    event.preventDefault();
    if (!canEditEmploymentContracts()) return;
    const teacher = selectedTeacherCardRow();
    if (!teacher) throw new Error("Сотрудник не найден");
    const current = selectedTeacherContract();
    ui.teacherContractFeedback.textContent = "Сохраняю…";
    const saved = await api(current ? `/api/hr-documents/contracts/${current.id}` : "/api/hr-documents/contracts", {
        method: current ? "PUT" : "POST",
        headers: jsonHeaders,
        body: JSON.stringify({
            teacherId: teacher.id,
            contractNumber: ui.teacherContractNumber.value.trim(),
            contractDate: ui.teacherContractDate.value || null,
            positionName: ui.teacherContractPosition.value.trim(),
            startDate: ui.teacherContractStart.value || null,
            endDate: ui.teacherContractEnd.value || null,
            primaryContract: ui.teacherContractPrimary.checked,
            active: ui.teacherContractActive.checked,
            loadHoursMayBeIncludedInRate: ui.teacherContractInRate.value === "true",
            loadInRateRuleId: ui.teacherContractInRateRule.value ? Number(ui.teacherContractInRateRule.value) : null,
            loadInRateDocumentLabel: ui.teacherContractInRateLabel.value.trim()
        })
    });
    await openTeacherCard(teacher.id, saved.id);
    ui.teacherContractFeedback.textContent = "Договор и настройка учебных часов сохранены.";
}

async function refreshTeacherCardAfterAction(teacherId, message) {
    await loadTeachers();
    const teacher = teacherRows.find((row) => String(row.id) === String(teacherId));
    if (!teacher) {
        ui.teacherCardDialog.close();
        print({ status: message });
        return;
    }
    await openTeacherCard(teacherId);
    ui.teacherCardFeedback.textContent = message;
}

async function saveTeacherDismissalPlan() {
    const teacher = selectedTeacherCardRow();
    const plannedDismissalDate = ui.teacherCardPlanDate.value;
    if (!teacher || !plannedDismissalDate) throw new Error("Укажите планируемую дату увольнения");
    await api(`/api/teachers/${teacher.id}/plan-dismiss`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify({ plannedDismissalDate, comment: ui.teacherCardPlanComment.value.trim() })
    });
    await refreshTeacherCardAfterAction(teacher.id, "Планируемое увольнение сохранено.");
}

async function cancelTeacherDismissalPlan() {
    const teacher = selectedTeacherCardRow();
    if (!teacher) return;
    await api(`/api/teachers/${teacher.id}/cancel-plan-dismiss`, { method: "PATCH" });
    await refreshTeacherCardAfterAction(teacher.id, "Планируемое увольнение отменено.");
}

async function dismissTeacherFromCard() {
    const teacher = selectedTeacherCardRow();
    const dismissalDate = ui.teacherCardDismissDate.value;
    if (!teacher || !dismissalDate) throw new Error("Укажите фактическую дату увольнения");
    await api(`/api/teachers/${teacher.id}/dismiss`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify({ dismissalDate })
    });
    await refreshTeacherCardAfterAction(teacher.id, "Увольнение зарегистрировано.");
}

async function restoreTeacherFromCard() {
    const teacher = selectedTeacherCardRow();
    if (!teacher) return;
    await api(`/api/teachers/${teacher.id}/restore`, { method: "PATCH" });
    await refreshTeacherCardAfterAction(teacher.id, "Сотрудник восстановлен.");
}

async function archiveTeacherFromCard() {
    const teacher = selectedTeacherCardRow();
    if (!teacher || !window.confirm(`Перенести ${teacher.fioTeacher} в архив?`)) return;
    await api(`/api/teachers/${teacher.id}/archive`, { method: "PATCH" });
    await refreshTeacherCardAfterAction(teacher.id, "Сотрудник перенесён в архив.");
}

async function deleteTeacherFromCard() {
    const teacher = selectedTeacherCardRow();
    if (!teacher || !window.confirm(`Удалить ${teacher.fioTeacher}? Это действие доступно только при отсутствии связанной нагрузки.`)) return;
    await api(`/api/teachers/${teacher.id}`, { method: "DELETE" });
    await refreshTeacherCardAfterAction(teacher.id, "Сотрудник удалён.");
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
                <td><b>${escapeHtml(row.fioTeacher || "")}</b></td>
                <td>${escapeHtml(row.phone || "—")}</td>
                <td>${escapeHtml(row.email || "—")}</td>
                <td class="teacher-duty-summary">${escapeHtml(row.additionalDutiesSummary || "Нет действующих назначений")}</td>
                <td><select class="teacher-building-input" data-id="${row.id}">${renderBuildingOptions(row.numberSchoolBuilding)}</select></td>
                <td>${escapeHtml(row.primaryPosition || "—")}</td>
                <td>${escapeHtml(row.employmentType || "—")}</td>
                <td>${escapeHtml(statusLabel(row))}</td>
                <td>
                    <div class="teacher-row-actions">
                        <button type="button" class="open-teacher-card-btn" data-id="${row.id}">Карточка сотрудника</button>
                    </div>
                </td>`;
            ui.tbody.appendChild(tr);
        });

    ui.tbody.querySelectorAll(".teacher-building-input").forEach((select) => {
        select.addEventListener("change", async () => {
            const row = teacherRows.find((item) => String(item.id) === String(select.dataset.id));
            if (!row) return;
            try {
                await api(`/api/teachers/${row.id}`, {
                    method: "PATCH",
                    headers: jsonHeaders,
                    body: JSON.stringify({
                        fioTeacher: row.fioTeacher,
                        fioTeacherDative: row.fioTeacherDative,
                        initials: row.initials,
                        phone: row.phone,
                        email: row.email,
                        numberSchoolBuilding: select.value
                    })
                });
                await loadTeachers();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    ui.tbody.querySelectorAll(".open-teacher-card-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            openTeacherCard(btn.dataset.id).catch((error) => print({ error: error.message }));
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
    const [rows, archivedRows, positions, vacancies] = await Promise.all([
        api(mckoAcademicYearPath('/api/teachers')),
        api('/api/teachers/archive'),
        api('/api/teachers/positions'),
        api('/api/teachers/vacancies')
    ]);
    teacherPositions = positions || [];
    teacherVacancies = vacancies || [];
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
    return (subjectCatalogRows || [])
        .filter((subject) => !subject.subjectType || subject.subjectType === "CORE")
        .slice()
        .sort((a, b) => String(a.subjectName || "").localeCompare(String(b.subjectName || ""), "ru"))
        .map((subject) => `<option value="${escapeHtml(subject.id)}">${escapeHtml(subject.subjectName || "")}</option>`)
        .join("");
}

function knownMckoSubjects() {
    const subjects = new Map();
    [
        PRIMARY_MCKO_LABEL,
        ...(mckoMappings || []).map((row) => row.mckoSubject),
        ...(mckoCertificates || []).map((row) => row.mckoSubject)
    ].map(mckoSubjectLabel).map((value) => String(value || "").trim()).filter(Boolean)
        .forEach((value) => subjects.set(mckoSubjectCompareKey(value), value));
    return Array.from(subjects.values()).sort((a, b) => a.localeCompare(b, "ru"));
}

function mckoGradeBandLabel(value) {
    if (value === "1-4") return "1-4";
    if (value === "5-11") return "5-11";
    return "Все";
}

function renderMckoGradeBandOptions(selected = "ALL") {
    return ["ALL", "1-4", "5-11"]
        .map((value) => `<option value="${value}" ${value === selected ? "selected" : ""}>${escapeHtml(mckoGradeBandLabel(value))}</option>`)
        .join("");
}

function renderMckoSubjectSelectors() {
    const subjects = knownMckoSubjects();
    if (ui.mckoSubjectSelect) {
        ui.mckoSubjectSelect.innerHTML = subjects
            .map((name) => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`)
            .join("") || `<option value="">Сначала загрузите выгрузку МЦКО</option>`;
    }
}

function updateMckoExportLink() {
    if (!ui.mckoExportLink) return;
    ui.mckoExportLink.href = mckoAcademicYearPath("/api/mcko/certificates/export?mode=load");
}

function mckoAcademicYearPath(path) {
    const academicYear = sessionStorage.getItem("tarification.academicYear") || "";
    if (!academicYear) return path;
    return `${path}${path.includes("?") ? "&" : "?"}academicYear=${encodeURIComponent(academicYear)}`;
}

function mckoStatusClass(status) {
    if (status === "MISSING") return "mcko-status-missing";
    if (status === "WARNING") return "mcko-status-warning";
    return "mcko-status-ok";
}

async function reloadMckoCertificates() {
    updateMckoExportLink();
    mckoCertificates = await api("/api/mcko/certificates?mode=all") || [];
    try {
        mckoOverviewRows = await api(mckoAcademicYearPath("/api/mcko/overview")) || [];
    } catch (error) {
        mckoOverviewRows = mckoCertificates.map((row) => ({
            certificateId: row.id,
            teacherId: row.teacherId,
            teacherFio: row.teacherFio,
            curriculumSubjects: "Нагрузка временно недоступна",
            mckoSubject: row.mckoSubject,
            examType: row.examType,
            diagnosticDate: row.diagnosticDate,
            expiresAt: row.expiresAt,
            level: row.level,
            published: row.published,
            source: row.source,
            comment: row.comment,
            hasScan: row.hasScan,
            status: row.status,
            message: row.warning || (row.status === "OK" ? "МЦКО есть" : "НЕТ МЦКО")
        }));
        print({ warning: "Сертификаты МЦКО загружены, но проверка по текущей нагрузке временно недоступна", error: error.message });
    }
    renderMckoSubjectSelectors();
    renderMckoCertificates();
}

function canEditMcko() {
    const user = window.tarificationAuth || {};
    return Boolean(user.admin || window.tarificationTabPermissions?.TEACHERS_MCKO?.canEdit);
}

function formatMckoDate(value) {
    const match = String(value || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return match ? `${match[3]}.${match[2]}.${match[1]}` : String(value || "");
}

function mckoSourceLabel(source) {
    if (source === "MANUAL") return "Ручной ввод";
    if (source === "IMPORT") return "Выгрузка";
    return source || "";
}

function compareMckoCertificates(left, right) {
    const key = mckoCertificateSort.key;
    let a = left?.[key];
    let b = right?.[key];
    if (key === "mckoSubject") {
        a = mckoSubjectLabel(a);
        b = mckoSubjectLabel(b);
    } else if (key === "source") {
        a = mckoSourceLabel(a);
        b = mckoSourceLabel(b);
    } else if (key === "status") {
        const rank = { MISSING: 3, WARNING: 2, OK: 1 };
        a = rank[a] || 0;
        b = rank[b] || 0;
    }
    const result = typeof a === "boolean" || typeof b === "boolean"
        ? Number(Boolean(a)) - Number(Boolean(b))
        : String(a || "").localeCompare(String(b || ""), "ru", { numeric: true, sensitivity: "base" });
    return mckoCertificateSort.ascending ? result : -result;
}

function renderMckoCertificates() {
    if (!ui.mckoCertificatesBody) return;
    document.querySelectorAll("[data-sort-mcko]").forEach((button) => {
        const active = button.dataset.sortMcko === mckoCertificateSort.key;
        button.closest("th")?.setAttribute("aria-sort", active ? (mckoCertificateSort.ascending ? "ascending" : "descending") : "none");
        button.title = active ? (mckoCertificateSort.ascending ? "По возрастанию" : "По убыванию") : "Сортировать";
    });
    ui.mckoCertificatesBody.innerHTML = mckoOverviewRows.slice().sort(compareMckoCertificates).map((row) => `
        <tr class="${mckoStatusClass(row.status)}">
            <td>${escapeHtml(row.teacherFio || "")}</td>
            <td>${escapeHtml(row.curriculumSubjects || "")}</td>
            <td>${escapeHtml(mckoSubjectLabel(row.mckoSubject) || "")}</td>
            <td>${escapeHtml(row.message || "НЕТ МЦКО")}</td>
            <td>${escapeHtml(row.examType || "")}</td>
            <td>${escapeHtml(formatMckoDate(row.diagnosticDate))}</td>
            <td>${escapeHtml(formatMckoDate(row.expiresAt))}</td>
            <td>${escapeHtml(row.level || "")}</td><td>${row.certificateId ? (row.published ? "Да" : "Нет") : "—"}</td>
            <td>${row.hasScan && row.certificateId ? `<a href="/api/mcko/certificates/${encodeURIComponent(row.certificateId)}/scan" target="_blank">Есть</a>` : "Нет"}</td>
            <td>${escapeHtml(row.comment || "")}</td><td>${escapeHtml(mckoSourceLabel(row.source))}</td>
            <td>${canEditMcko() ? (row.certificateId
                ? `<button type="button" data-edit-mcko="${row.certificateId}">Редактировать</button> <button type="button" data-delete-mcko="${row.certificateId}">Удалить</button>`
                : `<button type="button" data-add-mcko="${row.teacherId}" data-mcko-subject="${escapeHtml(row.mckoSubject || "")}">Добавить</button>`) : ""}</td>
        </tr>`).join("") || `<tr><td colspan="13">Нет предметов основной части, настроенных для проверки МЦКО</td></tr>`;
    ui.mckoCertificatesBody.querySelectorAll("[data-edit-mcko]").forEach((button) => button.addEventListener("click", () => startMckoEdit(Number(button.dataset.editMcko))));
    ui.mckoCertificatesBody.querySelectorAll("[data-delete-mcko]").forEach((button) => button.addEventListener("click", () =>
        deleteMckoCertificate(Number(button.dataset.deleteMcko)).catch((error) => print({ error: error.message }))));
    ui.mckoCertificatesBody.querySelectorAll("[data-add-mcko]").forEach((button) => button.addEventListener("click", () => startMckoCreate(Number(button.dataset.addMcko), button.dataset.mckoSubject)));
}

function selectMckoFormSubject(mckoSubject) {
    const subjectOption = [...ui.mckoSubjectSelect.options].find((option) => sameMckoSubject(option.value, mckoSubject));
    if (!subjectOption) ui.mckoSubjectSelect.add(new Option(mckoSubjectLabel(mckoSubject), mckoSubjectLabel(mckoSubject)));
    ui.mckoSubjectSelect.value = subjectOption?.value || mckoSubjectLabel(mckoSubject);
}

function startMckoCreate(teacherId, mckoSubject) {
    cancelMckoEdit();
    ui.mckoTeacherSelect.value = String(teacherId || "");
    selectMckoFormSubject(mckoSubject);
    ui.mckoManualForm.scrollIntoView({ behavior: "smooth", block: "center" });
}

function startMckoEdit(id) {
    const row = mckoCertificates.find((item) => Number(item.id) === Number(id));
    if (!row) return;
    editingMckoCertificateId = row.id;
    ui.mckoTeacherSelect.value = String(row.teacherId || "");
    selectMckoFormSubject(row.mckoSubject);
    ui.mckoExamType.value = row.examType || "";
    ui.mckoDate.value = row.diagnosticDate || "";
    if (![...ui.mckoLevel.options].some((option) => option.value === row.level)) ui.mckoLevel.add(new Option(row.level, row.level));
    ui.mckoLevel.value = row.level || "";
    ui.mckoPublished.checked = Boolean(row.published);
    ui.mckoComment.value = row.comment || "";
    ui.mckoCancelEditBtn.style.display = "";
    ui.mckoManualForm.scrollIntoView({ behavior: "smooth", block: "center" });
}

function cancelMckoEdit() {
    editingMckoCertificateId = null;
    ui.mckoManualForm?.reset();
    if (ui.mckoPublished) ui.mckoPublished.checked = true;
    if (ui.mckoCancelEditBtn) ui.mckoCancelEditBtn.style.display = "none";
}

async function deleteMckoCertificate(id) {
    if (!confirm("Удалить сертификат МЦКО?")) return;
    await api(`/api/mcko/certificates/${encodeURIComponent(id)}`, { method: "DELETE" });
    if (Number(editingMckoCertificateId) === Number(id)) cancelMckoEdit();
    await reloadMckoCertificates();
}

async function reloadMckoMappings() {
    if (!canViewTeachersTab("mcko-subjects") && !canViewTeachersTab("mcko")) return;
    await ensureSubjectCatalog();
    mckoMappings = await api("/api/mcko/subjects") || [];
    renderMckoSubjectSelectors();
    if (!ui.mckoSubjectsBody) return;
    const rows = knownMckoSubjects().map((mckoSubject) => ({
        mckoSubject,
        mappings: mckoMappings.filter((row) => sameMckoSubject(row.mckoSubject, mckoSubject)),
        ignored: (mckoMappings || []).some((row) => sameMckoSubject(row.mckoSubject, mckoSubject) && row.ignored)
    }));
    const optionsHtml = renderMckoLoadSubjectOptions();
    ui.mckoSubjectsBody.innerHTML = rows.map((row, index) => `
        <tr class="${row.ignored ? "mcko-status-warning" : ""}">
            <td>${escapeHtml(row.mckoSubject || "")}</td>
            <td>
                <select class="mcko-grade-band-select" data-mcko-index="${index}" ${row.ignored ? "disabled" : ""}>
                    ${renderMckoGradeBandOptions(row.mappings.find((mapping) => !mapping.ignored)?.gradeBand || "ALL")}
                </select>
            </td>
            <td>
                <select class="mcko-load-subjects-multiselect" data-mcko-index="${index}" multiple size="6" ${row.ignored ? "disabled" : ""}>
                    ${optionsHtml}
                </select>
            </td>
            <td>${row.ignored ? "Не проверяется" : "Проверяется"}</td>
            <td>
                <button type="button" data-save-mcko-mapping="${index}" ${row.ignored ? "disabled" : ""}>Сохранить</button>
                <button type="button" data-toggle-mcko-ignore="${index}">${row.ignored ? "Вернуть проверку" : "Не проверять"}</button>
            </td>
        </tr>
    `).join("") || `<tr><td colspan="5">Сначала загрузите выгрузку МЦКО</td></tr>`;
    rows.forEach((row, index) => {
        const select = ui.mckoSubjectsBody.querySelector(`.mcko-load-subjects-multiselect[data-mcko-index="${index}"]`);
        if (!select) return;
        const selectedIds = new Set(row.mappings.filter((mapping) => !mapping.ignored).map((mapping) => String(mapping.subjectId || "")));
        Array.from(select.options).forEach((option) => {
            option.selected = selectedIds.has(String(option.value));
        });
    });
    ui.mckoSubjectsBody.querySelectorAll("[data-save-mcko-mapping]").forEach((button) => {
        button.addEventListener("click", async () => {
            try {
                const row = rows[Number(button.dataset.saveMckoMapping)];
                await saveMckoMappingSet(row?.mckoSubject || "");
                await reloadMckoMappings();
                await reloadMckoCertificates();
            } catch (error) {
                print({ error: `Не удалось сохранить соответствие МЦКО: ${error.message}` });
            }
        });
    });

    ui.mckoSubjectsBody.querySelectorAll("[data-toggle-mcko-ignore]").forEach((button) => {
        button.addEventListener("click", async () => {
            try {
                const row = rows[Number(button.dataset.toggleMckoIgnore)];
                if (!row) return;
                if (row.ignored) {
                    await deleteMckoSubjectMappings(row.mckoSubject);
                    print({ status: "mcko subject returned to checks", mckoSubject: row.mckoSubject });
                } else {
                    await ignoreMckoSubject(row.mckoSubject);
                }
                await reloadMckoMappings();
                await reloadMckoCertificates();
            } catch (error) {
                print({ error: `Не удалось изменить проверку МЦКО: ${error.message}` });
            }
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
}

async function saveManualMcko(event) {
    event.preventDefault();
    const teacherId = Number(ui.mckoTeacherSelect?.value || 0);
    const mckoSubject = mckoSubjectCanonical(ui.mckoSubjectSelect?.value || "");
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
    const editing = editingMckoCertificateId;
    const path = editing ? `/api/mcko/certificates/${encodeURIComponent(editing)}` : "/api/mcko/certificates";
    const result = await api(path, { method: editing ? "PUT" : "POST", body: form });
    print(result);
    cancelMckoEdit();
    await reloadMckoCertificates();
}

async function saveMckoMappingSet(mckoSubject) {
    const subject = String(mckoSubject || "").trim();
    const rows = knownMckoSubjects();
    const index = rows.findIndex((item) => sameMckoSubject(item, subject));
    const select = ui.mckoSubjectsBody?.querySelector(`.mcko-load-subjects-multiselect[data-mcko-index="${index}"]`);
    const gradeBand = ui.mckoSubjectsBody?.querySelector(`.mcko-grade-band-select[data-mcko-index="${index}"]`)?.value || "ALL";
    if (!subject || !select) return;
    const selectedIds = new Set(Array.from(select.selectedOptions).map((option) => String(option.value)));
    const existing = (mckoMappings || []).filter((row) => sameMckoSubject(row.mckoSubject, subject));
    for (const row of existing) {
        const sameGradeBand = String(row.gradeBand || "ALL") === gradeBand;
        if (row.ignored || !selectedIds.has(String(row.subjectId || "")) || !sameGradeBand) {
            await api(`/api/mcko/subjects/${encodeURIComponent(row.id)}`, { method: "DELETE" });
        }
    }
    const existingIds = new Set(existing
        .filter((row) => !row.ignored && String(row.gradeBand || "ALL") === gradeBand)
        .map((row) => String(row.subjectId || "")));
    for (const subjectId of selectedIds) {
        if (!existingIds.has(subjectId)) {
            await api("/api/mcko/subjects", {
                method: "POST",
                headers: jsonHeaders,
                body: JSON.stringify({ mckoSubject: mckoSubjectCanonical(subject), subjectId: Number(subjectId), gradeBand })
            });
        }
    }
    print({ status: "mcko mapping saved", mckoSubject: subject, gradeBand, subjectIds: Array.from(selectedIds) });
}

async function deleteMckoSubjectMappings(mckoSubject) {
    const existing = (mckoMappings || []).filter((row) => sameMckoSubject(row.mckoSubject, mckoSubject));
    for (const row of existing) {
        await api(`/api/mcko/subjects/${encodeURIComponent(row.id)}`, { method: "DELETE" });
    }
}

async function ignoreMckoSubject(mckoSubject) {
    await deleteMckoSubjectMappings(mckoSubject);
    const subject = mckoSubjectCanonical(mckoSubject);
    const result = await api("/api/mcko/subjects", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({ mckoSubject: subject, ignored: true })
    });
    print({ status: "mcko subject ignored", result });
}

async function loadMckoTabData(tab = teachersTabFromHash()) {
    if (!isMckoTab(tab)) return;
    await reloadMckoCertificates();
    await reloadMckoMappings();
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

const oneCActionLabels = {
    ADD: "Принять на работу",
    UPDATE: "Обновить должность",
    RESTORE: "Восстановить / принять",
    ACCEPT_ADDITIONAL: "Принять по дополнительной должности",
    DISMISS: "Подтвердить увольнение",
    IGNORE: "Не менять"
};

function renderOneCPreview(preview) {
    const rows = preview?.rows || [];
    ui.oneCSummary.textContent = `Прочитано строк: ${preview?.sourceRowCount || 0}. Изменений и решений: ${rows.length}. Сверка на ${preview?.effectiveDate || "текущую дату"}.`;
    ui.oneCPreviewBody.innerHTML = rows.length
        ? rows.map((row) => `
            <tr>
                <td><strong>${escapeHtml(row.fio || "")}</strong></td>
                <td>${escapeHtml(row.currentPosition || "Нет в программе")}</td>
                <td>
                    <strong>${escapeHtml(row.proposedPosition || "—")}</strong><br>
                    <span class="muted">${escapeHtml(row.employmentType || "")}${row.dismissalDate ? `; увольнение ${escapeHtml(row.dismissalDate)}` : ""}</span>
                </td>
                <td>${escapeHtml(row.message || "")}</td>
                <td>
                    <select data-one-c-decision="${escapeHtml(row.fio || "")}">
                        ${(row.allowedActions || []).map((action) =>
                            `<option value="${escapeHtml(action)}" ${action === row.recommendedAction ? "selected" : ""}>${escapeHtml(oneCActionLabels[action] || action)}</option>`
                        ).join("")}
                    </select>
                </td>
            </tr>`).join("")
        : '<tr><td colspan="5">Расхождений не найдено. Применять нечего.</td></tr>';
    ui.oneCApply.disabled = rows.length === 0;
}

async function previewOneCImport() {
    const file = ui.oneCFileInput?.files?.[0];
    if (!file) {
        print({ error: "Выберите выгрузку 1С в формате .xls или .xlsx" });
        return;
    }
    ui.oneCPreviewBtn.disabled = true;
    print({ status: "Читаем выгрузку 1С…" });
    try {
        const form = new FormData();
        form.append("file", file);
        oneCPreview = await api("/api/teachers/import-1c/preview", { method: "POST", body: form });
        oneCPreviewFile = file;
        ui.oneCFeedback.textContent = "";
        renderOneCPreview(oneCPreview);
        ui.oneCDialog.showModal();
    } catch (error) {
        print({ error: error.message });
    } finally {
        ui.oneCPreviewBtn.disabled = false;
    }
}

async function applyOneCImport() {
    if (!oneCPreviewFile || !oneCPreview) return;
    const decisions = Array.from(ui.oneCPreviewBody.querySelectorAll("[data-one-c-decision]"))
        .map((select) => ({ fio: select.dataset.oneCDecision, action: select.value }));
    const form = new FormData();
    form.append("file", oneCPreviewFile);
    form.append("request", new Blob(
        [JSON.stringify({ decisions })],
        { type: "application/json" }
    ));
    ui.oneCApply.disabled = true;
    ui.oneCFeedback.textContent = "Применяем подтверждённые решения…";
    try {
        const result = await api("/api/teachers/import-1c/apply", { method: "POST", body: form });
        ui.oneCFeedback.textContent = `Готово: принято ${result.added || 0}, обновлено ${result.updated || 0}, восстановлено ${result.restored || 0}, уволено ${result.dismissed || 0}.`;
        print(result);
        await loadTeachers();
        oneCPreview = null;
        oneCPreviewFile = null;
        ui.oneCFileInput.value = "";
    } catch (error) {
        ui.oneCFeedback.textContent = error.message;
    } finally {
        ui.oneCApply.disabled = false;
    }
}

function downloadTeachers() {
    window.location.href = '/api/teachers/export';
}

function renderAcceptTeacherDialog() {
    ui.acceptTeacherForm.reset();
    ui.acceptTeacherFeedback.textContent = "";
    ui.acceptVacancy.innerHTML = [
        '<option value="">Нет — создать нового сотрудника</option>',
        ...teacherVacancies
            .filter((row) => String(row.fioTeacher || "").trim().toLowerCase() !== "вакансия")
            .map((row) => `<option value="${escapeHtml(row.id)}">${escapeHtml(row.fioTeacher)} · ID ${escapeHtml(row.id)}</option>`)
    ].join("");
    ui.acceptBuilding.innerHTML = renderBuildingOptions("");
    ui.acceptPosition.innerHTML = positionOptions("");
    ui.acceptInRateRule.innerHTML = '<option value="">Выберите правило после должности</option>';
    ui.acceptTeacherDialog.querySelectorAll(".accept-in-rate-field").forEach((field) => field.hidden = true);
    ui.acceptTeacherDialog.showModal();
}

function refreshAcceptRuleOptions() {
    const position = ui.acceptPosition.value;
    const matches = teacherCardInRateRules.filter((rule) => rule.active && rule.name === position);
    ui.acceptInRateRule.innerHTML = [
        '<option value="">Без автоматического правила</option>',
        ...matches.map((rule) => `<option value="${escapeHtml(rule.id)}">${escapeHtml(rule.name)}</option>`)
    ].join("");
    if (matches.length === 1) ui.acceptInRateRule.value = String(matches[0].id);
}

async function acceptTeacher(event) {
    event.preventDefault();
    ui.acceptTeacherFeedback.textContent = "Сохраняю карточку и связи…";
    const result = await api("/api/teachers/accept", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({
            vacancyTeacherId: ui.acceptVacancy.value ? Number(ui.acceptVacancy.value) : null,
            fioTeacher: ui.acceptFio.value.trim(),
            phone: ui.acceptPhone.value.trim(),
            email: ui.acceptEmail.value.trim(),
            numberSchoolBuilding: ui.acceptBuilding.value,
            primaryPosition: ui.acceptPosition.value,
            employmentType: ui.acceptEmploymentType.value.trim(),
            employmentDate: ui.acceptEmploymentDate.value || null,
            birthDate: ui.acceptBirthDate.value || null,
            passportSeries: ui.acceptPassportSeries.value.trim(),
            passportNumber: ui.acceptPassportNumber.value.trim(),
            passportIssuedBy: ui.acceptPassportIssuedBy.value.trim(),
            passportIssueDate: ui.acceptPassportIssueDate.value || null,
            passportDepartmentCode: ui.acceptPassportCode.value.trim(),
            registrationAddress: ui.acceptRegistrationAddress.value.trim(),
            actualAddress: ui.acceptActualAddress.value.trim(),
            inn: ui.acceptInn.value.trim(),
            snils: ui.acceptSnils.value.trim(),
            contractNumber: ui.acceptContractNumber.value.trim(),
            contractDate: ui.acceptContractDate.value || null,
            contractStartDate: ui.acceptContractStart.value || null,
            contractEndDate: ui.acceptContractEnd.value || null,
            loadHoursMayBeIncludedInRate: ui.acceptInRate.value === "true",
            loadInRateRuleId: ui.acceptInRateRule.value ? Number(ui.acceptInRateRule.value) : null
        })
    });
    ui.acceptTeacherDialog.close();
    await loadTeachers();
    await openTeacherCard(result.teacherId);
    ui.teacherCardFeedback.textContent = result.linkedToVacancy
        ? `Сотрудник принят. Запись «${result.previousName}» сохранена под тем же ID ${result.teacherId}.`
        : `Сотрудник принят, ID ${result.teacherId}.`;
}

async function autoAssignBuildings() {
    if (!window.confirm("Распределить сотрудников по корпусам по наибольшей сумме часов выбранного учебного года?")) return;
    const result = await api(mckoAcademicYearPath("/api/teachers/auto-assign-buildings"), { method: "POST" });
    print({
        status: "Распределение по корпусам завершено",
        назначено: result.assigned,
        безИзменений: result.unchanged,
        безНагрузки: result.skippedWithoutLoad,
        равнаяНагрузкаВКорпусах: result.skippedTies
    });
    await loadTeachers();
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

function settingsRuleBandRow(band = {}) {
    return `<tr data-settings-rule-band>
        <td><input data-band-min type="number" min="0" step="0.01" value="${escapeHtml(band.minTotalHours ?? 0)}"></td>
        <td><input data-band-max type="number" min="0" step="0.01" value="${escapeHtml(band.maxTotalHours ?? "")}"></td>
        <td><input data-band-included type="number" min="0" step="0.01" value="${escapeHtml(band.suggestedIncludedHours ?? 0)}"></td>
        <td><input data-band-fraction type="number" min="0" step="0.01" value="${escapeHtml(band.rateFraction ?? "")}"></td>
        <td><button type="button" data-remove-settings-band>Удалить</button></td>
    </tr>`;
}

function renderSettingsInRateRules() {
    if (!ui.settingsInRateRulesList) return;
    ui.settingsNewRulePosition.innerHTML = positionOptions("");
    ui.settingsInRateRulesList.innerHTML = teacherCardInRateRules.map((rule) => `
        <div class="card in-rate-rule-card" data-settings-rule="${escapeHtml(rule.id)}">
            <div class="form-grid">
                <label>Основная должность<select data-rule-name>${positionOptions(rule.name)}</select></label>
                <label>Пояснение для документов<input data-rule-label value="${escapeHtml(rule.documentLabel || "")}"></label>
                <label><input data-rule-active type="checkbox" ${rule.active ? "checked" : ""}> Правило действует</label>
            </div>
            <table class="sheet-table"><thead><tr>
                <th>От часов</th><th>До часов</th><th>Максимум внутри ставки</th><th>Доля ставки</th><th></th>
            </tr></thead><tbody data-rule-bands>${(rule.bands || []).map(settingsRuleBandRow).join("")}</tbody></table>
            <div class="row">
                <button type="button" data-add-settings-band>Добавить диапазон</button>
                <button type="button" data-save-settings-rule>Сохранить</button>
                <button type="button" class="danger-btn" data-delete-settings-rule>Удалить</button>
            </div>
        </div>
    `).join("") || '<p class="muted">Правила ещё не созданы.</p>';
}

function settingsRuleRequest(card) {
    return {
        name: card.querySelector("[data-rule-name]").value,
        documentLabel: card.querySelector("[data-rule-label]").value.trim(),
        active: card.querySelector("[data-rule-active]").checked,
        bands: Array.from(card.querySelectorAll("[data-settings-rule-band]")).map((row) => ({
            minTotalHours: Number(row.querySelector("[data-band-min]").value || 0),
            maxTotalHours: row.querySelector("[data-band-max]").value === "" ? null : Number(row.querySelector("[data-band-max]").value),
            suggestedIncludedHours: Number(row.querySelector("[data-band-included]").value || 0),
            rateFraction: row.querySelector("[data-band-fraction]").value === "" ? null : Number(row.querySelector("[data-band-fraction]").value)
        }))
    };
}

async function reloadSettingsInRateRules() {
    const [rules, positions] = await Promise.all([
        api("/api/manual-load/in-rate/rules"),
        api("/api/teachers/positions")
    ]);
    teacherCardInRateRules = rules || [];
    teacherPositions = positions || [];
    renderSettingsInRateRules();
}

async function addSettingsInRateRule() {
    if (!ui.settingsNewRulePosition.value) throw new Error("Выберите основную должность");
    await api("/api/manual-load/in-rate/rules", {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify({
            name: ui.settingsNewRulePosition.value,
            documentLabel: ui.settingsNewRuleLabel.value.trim(),
            active: true,
            bands: [{
                minTotalHours: Number(ui.settingsNewRuleMin.value || 0),
                maxTotalHours: ui.settingsNewRuleMax.value === "" ? null : Number(ui.settingsNewRuleMax.value),
                suggestedIncludedHours: Number(ui.settingsNewRuleIncluded.value || 0),
                rateFraction: ui.settingsNewRuleFraction.value === "" ? null : Number(ui.settingsNewRuleFraction.value)
            }]
        })
    });
    ui.settingsNewRuleLabel.value = "";
    await reloadSettingsInRateRules();
}

async function loadSettingsTabData(tab = teachersTabFromHash()) {
    if (tab === "settings") await Promise.all([loadSalarySettings(), reloadSettingsInRateRules()]);
    if (tab === "coefficients") await reloadCoefficients();
    if (tab === "group-coefficients") await reloadGroupCoefficients();
}

function bindEvents() {
    ui.importBtn?.addEventListener('click', importTeachers);
    ui.oneCPreviewBtn?.addEventListener('click', previewOneCImport);
    ui.oneCApply?.addEventListener('click', applyOneCImport);
    ui.oneCClose?.addEventListener('click', () => ui.oneCDialog.close());
    ui.oneCCancel?.addEventListener('click', () => ui.oneCDialog.close());
    ui.downloadBtn?.addEventListener('click', downloadTeachers);
    ui.refreshBtn?.addEventListener('click', () => loadTeachers().catch((e) => print({ error: e.message })));
    ui.clearBtn?.addEventListener('click', clearTeachers);
    ui.acceptTeacherBtn?.addEventListener("click", async () => {
        try {
            if (!teacherCardInRateRules.length) teacherCardInRateRules = await api("/api/manual-load/in-rate/rules") || [];
            renderAcceptTeacherDialog();
        } catch (error) {
            print({ error: error.message });
        }
    });
    ui.autoAssignBuildingsBtn?.addEventListener("click", () => autoAssignBuildings().catch((error) => print({ error: error.message })));
    ui.acceptTeacherClose?.addEventListener("click", () => ui.acceptTeacherDialog.close());
    ui.acceptTeacherCancel?.addEventListener("click", () => ui.acceptTeacherDialog.close());
    ui.acceptTeacherForm?.addEventListener("submit", (event) => acceptTeacher(event).catch((error) => {
        ui.acceptTeacherFeedback.textContent = error.message;
    }));
    ui.acceptPosition?.addEventListener("change", refreshAcceptRuleOptions);
    ui.acceptInRate?.addEventListener("change", () => {
        const enabled = ui.acceptInRate.value === "true";
        ui.acceptTeacherDialog.querySelectorAll(".accept-in-rate-field").forEach((field) => field.hidden = !enabled);
        if (enabled) refreshAcceptRuleOptions();
    });
    ui.teacherCardClose?.addEventListener("click", () => ui.teacherCardDialog.close());
    ui.teacherCardMainForm?.addEventListener("submit", (event) => saveTeacherCardMain(event).catch((error) => {
        ui.teacherCardFeedback.textContent = error.message;
    }));
    ui.teacherPersonalForm?.addEventListener("submit", (event) => saveTeacherPersonalData(event).catch((error) => {
        ui.teacherCardFeedback.textContent = error.message;
    }));
    ui.teacherCardDataSheet?.addEventListener("click", downloadTeacherDataSheet);
    ui.teacherContractSelect?.addEventListener("change", () => {
        renderTeacherContractForm(selectedTeacherContract());
        setTeacherContractFormAccess();
    });
    ui.teacherContractInRate?.addEventListener("change", () => {
        const enabled = ui.teacherContractInRate.value === "true";
        ui.teacherCardDialog.querySelectorAll(".teacher-in-rate-field").forEach((field) => {
            field.hidden = !enabled;
        });
    });
    ui.teacherContractPosition?.addEventListener("change", () => {
        const position = ui.teacherContractPosition.value;
        const matches = teacherCardInRateRules.filter((rule) => rule.active && rule.name === position);
        ui.teacherContractInRateRule.innerHTML = [
            '<option value="">Без автоматического правила</option>',
            ...matches.map((rule) => `<option value="${escapeHtml(rule.id)}">${escapeHtml(rule.name)}</option>`)
        ].join("");
        if (matches.length === 1) {
            ui.teacherContractInRateRule.value = String(matches[0].id);
            ui.teacherContractInRateLabel.value = matches[0].documentLabel || "";
        }
    });
    ui.teacherContractInRateRule?.addEventListener("change", () => {
        const rule = teacherCardInRateRules.find((item) => String(item.id) === String(ui.teacherContractInRateRule.value));
        ui.teacherContractInRateLabel.value = rule?.documentLabel || "";
    });
    ui.teacherContractForm?.addEventListener("submit", (event) => {
        saveTeacherCardContract(event).catch((error) => {
            ui.teacherContractFeedback.textContent = error.message;
        });
    });
    const cardAction = (element, action) => element?.addEventListener("click", () => {
        action().catch((error) => {
            ui.teacherCardFeedback.textContent = error.message;
        });
    });
    cardAction(ui.teacherCardSavePlan, saveTeacherDismissalPlan);
    cardAction(ui.teacherCardCancelPlan, cancelTeacherDismissalPlan);
    cardAction(ui.teacherCardDismiss, dismissTeacherFromCard);
    cardAction(ui.teacherCardRestore, restoreTeacherFromCard);
    cardAction(ui.teacherCardArchive, archiveTeacherFromCard);
    cardAction(ui.teacherCardDelete, deleteTeacherFromCard);
    window.addEventListener("hashchange", async () => {
        const tab = teachersTabFromHash();
        showTeachersTab(tab);
        await loadSettingsTabData(tab);
        await loadMckoTabData(tab);
    });
    ui.salarySettingsForm?.addEventListener("submit", saveSalarySettings);
    ui.settingsAddInRateRuleBtn?.addEventListener("click", () => addSettingsInRateRule().catch((error) => print({ error: error.message })));
    ui.settingsInRateRulesList?.addEventListener("click", async (event) => {
        const card = event.target.closest("[data-settings-rule]");
        if (!card) return;
        try {
            if (event.target.closest("[data-add-settings-band]")) {
                card.querySelector("[data-rule-bands]").insertAdjacentHTML("beforeend", settingsRuleBandRow());
            } else if (event.target.closest("[data-remove-settings-band]")) {
                event.target.closest("[data-settings-rule-band]")?.remove();
            } else if (event.target.closest("[data-save-settings-rule]")) {
                await api(`/api/manual-load/in-rate/rules/${card.dataset.settingsRule}`, {
                    method: "PUT", headers: jsonHeaders, body: JSON.stringify(settingsRuleRequest(card))
                });
                await reloadSettingsInRateRules();
            } else if (event.target.closest("[data-delete-settings-rule]")) {
                if (window.confirm("Удалить правило часов в ставке?")) {
                    await api(`/api/manual-load/in-rate/rules/${card.dataset.settingsRule}`, { method: "DELETE" });
                    await reloadSettingsInRateRules();
                }
            }
        } catch (error) {
            print({ error: error.message });
        }
    });
    ui.coefficientImportBtn?.addEventListener("click", () => importCoefficients().catch((error) => print({ error: error.message })));
    ui.coefficientForm?.addEventListener("submit", (event) => saveCoefficient(event).catch((error) => print({ error: error.message })));
    ui.coefficientRefreshBtn?.addEventListener("click", () => reloadCoefficients().catch((error) => print({ error: error.message })));
    ui.groupCoefficientForm?.addEventListener("submit", (event) => saveGroupCoefficientSubject(event).catch((error) => print({ error: error.message })));
    ui.groupCoefficientRefreshBtn?.addEventListener("click", () => reloadGroupCoefficients().catch((error) => print({ error: error.message })));
    ui.groupCoefficientSortBtn?.addEventListener("click", () => {
        groupCoefficientSortAsc = !groupCoefficientSortAsc;
        reloadGroupCoefficients().catch((error) => print({ error: error.message }));
    });
    ui.mckoImportBtn?.addEventListener("click", () => importMckoCertificates().catch((error) => print({ error: error.message })));
    ui.mckoManualForm?.addEventListener("submit", (event) => saveManualMcko(event).catch((error) => print({ error: error.message })));
    ui.mckoCancelEditBtn?.addEventListener("click", cancelMckoEdit);
    document.querySelectorAll("[data-sort-mcko]").forEach((button) => button.addEventListener("click", () => {
        const key = button.dataset.sortMcko;
        mckoCertificateSort = mckoCertificateSort.key === key
            ? { key, ascending: !mckoCertificateSort.ascending }
            : { key, ascending: true };
        renderMckoCertificates();
    }));
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
