const jsonHeaders = { 'Content-Type': 'application/json' };

function applyAcademicYearScope(path) {
    const resolver = typeof window.withAcademicYear === 'function' ? window.withAcademicYear : null;
    if (!resolver || resolver === applyAcademicYearScope) {
        return path;
    }
    if (String(path).includes('academicYear=')) {
        return path;
    }
    return resolver(path);
}
const TAB_GROUPS = [
    {
        key: 'MAIN',
        label: 'Основные вкладки',
        tabs: [
            { key: 'BUILDINGS', label: 'Корпуса' },
            { key: 'CLASSES', label: 'Классы' },
            { key: 'SUBJECTS', label: 'Предметы' },
            { key: 'CURRICULUM', label: 'Учебный план' },
            { key: 'LOAD', label: 'Нагрузка по корпусам' },
            { key: 'LOAD_STATS', label: 'Нагрузка: статистика' },
            { key: 'SERVICE_NOTES', label: 'Служебные записки' },
            { key: 'SETTINGS', label: 'Настройки' },
            { key: 'TEACHERS', label: 'Кадры' },
            { key: 'CONTINGENT_IMPORT', label: 'Контингент: импорт' },
            { key: 'CONTINGENT_STATS', label: 'Контингент: численность' },
            { key: 'SUBJECT_AREAS', label: 'Предметные области' }
        ]
    },
    {
        key: 'VSOKO',
        label: 'ВСОКО / ОГЭ',
        tabs: [
            { key: 'VSOKO_VIEW', label: 'ВСОКО: просмотр' },
            { key: 'VSOKO_EDIT', label: 'ВСОКО: редактирование' },
            { key: 'OGE_UPLOAD_VIEW', label: 'ОГЭ: Выгрузка (просмотр)' },
            { key: 'OGE_MISMATCH_VIEW', label: 'ОГЭ: Нестыковки (просмотр)' },
            { key: 'OGE_EXTERNAL_WORKS_VIEW', label: 'ОГЭ: Внешние работы пробники (просмотр)' },
            { key: 'OGE_TEACHER_BINDING_VIEW', label: 'ОГЭ: Привязка к педагогу (просмотр)' },
            { key: 'OGE_SCORE_VIEW', label: 'ОГЭ: Баллы за задания (просмотр)' },
            { key: 'OGE_EVALUATION_VIEW', label: 'ОГЭ: Оценивание (просмотр)' },
            { key: 'OGE_GIA_UPLOAD', label: 'ОГЭ: Загрузка выгрузок ГИА' },
            { key: 'OGE_WORK_UPLOAD', label: 'ОГЭ: Загрузка работ ОГЭ' }
        ]
    },
    {
        key: 'ADMINISTRATION',
        label: 'Администрирование',
        tabs: [
            { key: 'USERS', label: 'Пользователи' }
        ]
    }
];

const TABS = TAB_GROUPS.flatMap((group) => group.tabs);

const LOAD_SCOPE_MODE = {
    NONE: 'NONE',
    PRIMARY: 'PRIMARY',
    SELECTED: 'SELECTED',
    ALL: 'ALL'
};

const ui = {
    form: document.getElementById('user-create-form'),
    result: document.getElementById('admin-result'),
    tbody: document.getElementById('users-table-body'),
    createRole: document.getElementById('create-role'),
    createManagedBuilding: document.getElementById('create-managed-building'),
    createPermissionsBody: document.getElementById('create-permissions-body'),
    createLoadBuildings: document.getElementById('create-load-buildings'),
    createLoadSummary: document.getElementById('create-load-summary'),
    createLoadSelectAll: document.getElementById('create-load-select-all'),
    createLoadClear: document.getElementById('create-load-clear'),
    editDialog: document.getElementById('user-edit-dialog'),
    editForm: document.getElementById('user-edit-form'),
    editRole: document.getElementById('edit-role'),
    editManagedBuilding: document.getElementById('edit-managed-building'),
    editPermissionsBody: document.getElementById('edit-permissions-body'),
    editLoadBuildings: document.getElementById('edit-load-buildings'),
    editLoadSummary: document.getElementById('edit-load-summary'),
    editLoadSelectAll: document.getElementById('edit-load-select-all'),
    editLoadClear: document.getElementById('edit-load-clear'),
    editCloseBtn: document.getElementById('user-edit-close-btn'),
    resetPasswordBtn: document.getElementById('reset-password-btn'),
    editSaveBtn: document.getElementById('save-user-btn'),
    adminTabUsersBtn: document.getElementById('admin-tab-users-btn'),
    adminTabYearsBtn: document.getElementById('admin-tab-years-btn'),
    adminTabOptionsBtn: document.getElementById('admin-tab-options-btn'),
    debugModeInputs: Array.from(document.querySelectorAll('input[name="admin-debug-mode"]')),
    optionsFeedback: document.getElementById('admin-options-feedback'),
    academicYearForm: document.getElementById('academic-year-create-form'),
    academicYearCode: document.getElementById('academic-year-code'),
    academicYearFeedback: document.getElementById('academic-year-feedback'),
    academicYearsBody: document.getElementById('academic-years-body'),
    createFullName: document.getElementById('create-full-name'),
    editFullName: document.getElementById('edit-full-name'),
    createTeacherDatalist: document.getElementById('teacher-fio-options-create'),
    editTeacherDatalist: document.getElementById('teacher-fio-options-edit')
};

let buildings = [];
let users = [];
let editingUserId = null;
let teacherFioOptions = [];

async function api(path, options = {}) {
    const response = await fetch(applyAcademicYearScope(path), options);
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

function print(value) {
    ui.result.textContent = JSON.stringify(value, null, 2);
}

function renderTeacherFioDatalist(target, items) {
    if (!target) return;
    target.innerHTML = (items || []).map((fio) => `<option value="${esc(fio)}"></option>`).join('');
}

function filterTeacherFioOptions(query) {
    const q = String(query || '').trim().toLowerCase();
    if (!q) return teacherFioOptions.slice(0, 200);
    return teacherFioOptions.filter((fio) => fio.toLowerCase().includes(q)).slice(0, 200);
}

function bindTeacherFioAutocomplete(input, datalist) {
    if (!input || !datalist) return;
    const render = () => renderTeacherFioDatalist(datalist, filterTeacherFioOptions(input.value));
    input.addEventListener('focus', render);
    input.addEventListener('input', render);
}

function setAdminTab(tab) {
    document.querySelectorAll('[data-admin-tab]').forEach((section) => {
        section.style.display = section.dataset.adminTab === tab ? '' : 'none';
    });
}

function renderDebugModeOptions() {
    const enabled = Boolean(window.tarificationDebugOutputEnabled);
    ui.debugModeInputs.forEach((input) => {
        input.checked = enabled ? input.value === 'yes' : input.value === 'no';
    });
    if (ui.optionsFeedback) {
        ui.optionsFeedback.textContent = enabled
            ? 'Отладочные окна отображаются только для администратора.'
            : 'Отладочные окна скрыты (обычные пользователи их не увидят всегда).';
    }
}

function yesNo(flag) {
    return flag ? 'Да' : 'Нет';
}

function normalizeAcademicYearInput(rawValue) {
    const value = String(rawValue || '').trim().replace('\\', '/');
    if (/^\d{4}$/.test(value)) {
        const start = Number(value);
        return `${start}/${start + 1}`;
    }
    return value;
}

function normalizeClassForContinuity(value) {
    const raw = String(value || '').trim().toUpperCase().replace(/[–—]/g, '-');
    const match = raw.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return match ? `${match[1]}-${match[2]}` : raw;
}

function previousClassForContinuity(targetClass) {
    const normalized = normalizeClassForContinuity(targetClass);
    const match = normalized.match(/^(\d{1,2})-([А-ЯA-Z])$/);
    if (!match) return null;
    const parallel = Number(match[1]);
    if (!Number.isFinite(parallel) || parallel <= 1) return null;
    if (parallel === 5 || parallel === 10) return null;
    return `${parallel - 1}-${match[2]}`;
}

function continuityRowKey(className, subjectName, groupName) {
    return `${normalizeClassForContinuity(className)}|${String(subjectName || '').trim().toLowerCase()}|${String(groupName || '').trim().toLowerCase()}`;
}

function continuityStateForYear(yearCode, manualRowsByYear, curriculumRowsByYear, continuityApplied) {
    if (!continuityApplied) return 'none';
    const [fromYear] = String(yearCode || '').split('/');
    const sourceYear = `${Number(fromYear) - 1}/${fromYear}`;
    const sourceManual = manualRowsByYear.get(sourceYear) || [];
    const targetManual = manualRowsByYear.get(yearCode) || [];
    const targetCurriculum = curriculumRowsByYear.get(yearCode) || [];
    if (!sourceManual.length || !targetCurriculum.length) return 'none';

    const sourceByKey = new Map();
    sourceManual.forEach((row) => {
        const fio = String(row.fioTeacher || '').trim();
        if (!fio || fio.toLowerCase().includes('вакан')) return;
        sourceByKey.set(
            continuityRowKey(row.className, row.subjectName, row.groupNameEducationalPlan),
            fio.toLowerCase()
        );
    });
    if (!sourceByKey.size) return 'none';

    const targetByKey = new Map();
    targetManual.forEach((row) => {
        const fio = String(row.fioTeacher || '').trim();
        if (!fio || fio.toLowerCase().includes('вакан')) return;
        targetByKey.set(
            continuityRowKey(row.className, row.subjectName, row.groupNameEducationalPlan),
            fio.toLowerCase()
        );
    });

    let checked = 0;
    let violations = 0;
    targetCurriculum.forEach((curriculum) => {
        const prevClass = previousClassForContinuity(curriculum.className);
        if (!prevClass) return;
        const groupName = curriculum.subgroupRequired ? 'группа 1' : '';
        const sourceKey = continuityRowKey(prevClass, curriculum.subjectName, groupName);
        const expectedTeacher = sourceByKey.get(sourceKey);
        if (!expectedTeacher) return;
        const targetKey = continuityRowKey(curriculum.className, curriculum.subjectName, groupName);
        const actualTeacher = targetByKey.get(targetKey);
        if (!actualTeacher) return;
        checked += 1;
        if (actualTeacher !== expectedTeacher) violations += 1;
    });

    if (!checked) return 'none';
    return violations > 0 ? 'broken' : 'ok';
}

async function renderAcademicYears() {
    if (!ui.academicYearsBody) return;
    const years = await api('/api/academic-years');
    const rawRows = await Promise.all((years || []).map(async (year) => {
        let curriculumLoaded = false;
        let loadFilled = false;
        let curriculumRows = [];
        let manualRows = [];
        try {
            const [curriculumResult, manualResult] = await Promise.allSettled([
                api(`/api/curriculum?academicYear=${encodeURIComponent(year.code)}`),
                api(`/api/manual-load?academicYear=${encodeURIComponent(year.code)}`)
            ]);
            if (curriculumResult.status === 'fulfilled') {
                curriculumRows = curriculumResult.value || [];
                curriculumLoaded = curriculumRows.length > 0;
            }
            if (manualResult.status === 'fulfilled') {
                manualRows = manualResult.value || [];
                loadFilled = manualRows.some((item) => {
                    const fio = String(item.fioTeacher || '').trim().toLowerCase();
                    return fio && !fio.includes('вакан');
                });
            }
        } catch {
            // Не блокируем отображение списка годов, даже если статусные запросы временно неуспешны.
        }
        return {
            ...year,
            curriculumRows,
            manualRows,
            curriculumLoaded,
            loadFilled
        };
    }));
    const curriculumRowsByYear = new Map(rawRows.map((row) => [row.code, row.curriculumRows || []]));
    const manualRowsByYear = new Map(rawRows.map((row) => [row.code, row.manualRows || []]));
    const rows = rawRows.map((row) => ({
        ...row,
        continuityState: continuityStateForYear(row.code, manualRowsByYear, curriculumRowsByYear, row.continuityApplied)
    }));
    ui.academicYearsBody.innerHTML = rows.map((row) => `
        <tr>
            <td>${esc(row.code)}</td>
            <td>${yesNo(row.curriculumLoaded)}</td>
            <td>${yesNo(row.loadFilled)}</td>
            <td class="${row.continuityState === 'ok' ? 'year-status-ok' : row.continuityState === 'broken' ? 'year-status-broken' : ''}">
                ${row.continuityApplied ? (row.continuityState === 'ok' ? 'Да' : row.continuityState === 'broken' ? 'Нарушена' : 'Да') : 'Нет'}
            </td>
            <td class="row compact-row compact-actions">
                <button type="button" data-year-continuity="${esc(row.code)}">
                    Запустить преемственность
                </button>
                <button type="button" data-year-delete="${esc(row.id)}">Удалить</button>
            </td>
        </tr>
    `).join('');
    ui.academicYearsBody.querySelectorAll('[data-year-continuity]').forEach((btn) => {
        btn.addEventListener('click', async () => {
            try {
                await api(`/api/academic-years/continuity?code=${encodeURIComponent(btn.dataset.yearContinuity)}`, { method: 'POST' });
                if (ui.academicYearFeedback) {
                    ui.academicYearFeedback.textContent = `Преемственность отмечена для ${btn.dataset.yearContinuity}.`;
                }
                await renderAcademicYears();
            } catch (error) {
                if (ui.academicYearFeedback) {
                    ui.academicYearFeedback.textContent = `Ошибка: ${error.message}`;
                }
                print({ error: error.message });
            }
        });
    });
    ui.academicYearsBody.querySelectorAll('[data-year-delete]').forEach((btn) => {
        btn.addEventListener('click', async () => {
            try {
                await api(`/api/academic-years/${btn.dataset.yearDelete}`, { method: 'DELETE' });
                await renderAcademicYears();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
}

function esc(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function roleLabel(role) {
    return ({
        DIRECTOR: 'Директор',
        DEPUTY_DIRECTOR: 'Зам директора',
        BUILDING_HEAD: 'Руководитель корпуса',
        METHODIST: 'Методист',
        HR: 'Кадры',
        ADMIN: 'Администратор'
    })[role] || role;
}

function permissionMap(permissionList = []) {
    return Object.fromEntries(permissionList.map((permission) => [permission.tab, permission]));
}

function buildingByCode(code) {
    return buildings.find((building) => building.code === code) || null;
}

function formatBuilding(code) {
    if (!code) return '—';
    const building = buildingByCode(code);
    return building ? building.name : code;
}

function loadScopeLabel(user) {
    if (user.loadEditAllBuildings) return 'Все корпуса';
    const codes = (user.loadEditableBuildingCodes || []).filter(Boolean);
    if (codes.length) {
        return codes.length === 1
            ? `1 корпус: ${codes[0]}`
            : `${codes.length} корп.: ${codes.join(', ')}`;
    }
    if (user.role === 'BUILDING_HEAD' && user.managedBuildingCode) {
        return `Основной корпус: ${user.managedBuildingCode}`;
    }
    return 'Только просмотр';
}

function renderBuildingSelect(selectEl, selectedValue = '') {
    selectEl.innerHTML = '<option value="">Основной корпус не указан</option>';
    buildings.forEach((building) => {
        const option = document.createElement('option');
        option.value = building.code;
        option.textContent = building.name;
        if (selectedValue && selectedValue === building.code) option.selected = true;
        selectEl.appendChild(option);
    });
}

function scopeInputs(prefix) {
    return Array.from(document.querySelectorAll(`input[name="${prefix}LoadScopeMode"]`));
}

function scopeInput(prefix, mode) {
    return document.querySelector(`input[name="${prefix}LoadScopeMode"][value="${mode}"]`);
}

function selectedScopeMode(prefix) {
    return scopeInputs(prefix).find((input) => input.checked)?.value || LOAD_SCOPE_MODE.NONE;
}

function setScopeMode(prefix, mode) {
    const input = scopeInput(prefix, mode) || scopeInput(prefix, LOAD_SCOPE_MODE.NONE);
    if (input) input.checked = true;
}

function loadBuildingsContainer(prefix) {
    return prefix === 'create' ? ui.createLoadBuildings : ui.editLoadBuildings;
}

function loadSummaryElement(prefix) {
    return prefix === 'create' ? ui.createLoadSummary : ui.editLoadSummary;
}

function loadSelectAllButton(prefix) {
    return prefix === 'create' ? ui.createLoadSelectAll : ui.editLoadSelectAll;
}

function loadClearButton(prefix) {
    return prefix === 'create' ? ui.createLoadClear : ui.editLoadClear;
}

function renderLoadBuildings(target, selectedCodes = [], prefix = 'create') {
    const selected = new Set(selectedCodes || []);
    target.innerHTML = buildings.map((building) => `
        <label class="building-checkbox-pill">
            <input type="checkbox" data-load-building="${esc(building.code)}" data-prefix="${prefix}" ${selected.has(building.code) ? 'checked' : ''}>
            <span>${esc(building.name)}</span>
        </label>
    `).join('');
    target.querySelectorAll('[data-load-building]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => syncLoadBuildingScope(prefix));
    });
    syncLoadBuildingScope(prefix);
}

function selectedLoadBuildings(prefix) {
    const container = loadBuildingsContainer(prefix);
    return Array.from(container.querySelectorAll('[data-load-building]:checked')).map((el) => el.dataset.loadBuilding);
}

function loadScopeSummary(prefix) {
    const mode = selectedScopeMode(prefix);
    const roleSelect = prefix === 'create' ? ui.createRole : ui.editRole;
    const managedBuilding = prefix === 'create' ? ui.createManagedBuilding.value : ui.editManagedBuilding.value;
    const selectedCodes = selectedLoadBuildings(prefix);

    if (mode === LOAD_SCOPE_MODE.ALL) {
        return 'Редактирование нагрузки разрешено по всем корпусам.';
    }
    if (mode === LOAD_SCOPE_MODE.SELECTED) {
        if (!selectedCodes.length) {
            return 'Выбран режим «Выбранные корпуса», но корпуса ещё не отмечены.';
        }
        return selectedCodes.length === 1
            ? `Редактирование разрешено для корпуса ${formatBuilding(selectedCodes[0])}.`
            : `Редактирование разрешено для ${selectedCodes.length} корпусов: ${selectedCodes.join(', ')}.`;
    }
    if (mode === LOAD_SCOPE_MODE.PRIMARY) {
        if (roleSelect.value !== 'BUILDING_HEAD') {
            return 'Режим «Основной корпус» доступен только для роли «Руководитель корпуса».';
        }
        if (!managedBuilding) {
            return 'Сначала выберите основной корпус пользователя.';
        }
        return `Редактирование нагрузки будет привязано к основному корпусу ${formatBuilding(managedBuilding)}.`;
    }
    return 'Пользователь сможет только просматривать вкладку нагрузки без редактирования корпусов.';
}

function syncLoadBuildingScope(prefix) {
    const mode = selectedScopeMode(prefix);
    const container = loadBuildingsContainer(prefix);
    const allowManualSelection = mode === LOAD_SCOPE_MODE.SELECTED;

    container.classList.toggle('is-disabled', !allowManualSelection);
    container.querySelectorAll('[data-load-building]').forEach((checkbox) => {
        checkbox.disabled = !allowManualSelection;
    });

    const summary = loadSummaryElement(prefix);
    if (summary) {
        summary.textContent = loadScopeSummary(prefix);
    }

    const selectAllBtn = loadSelectAllButton(prefix);
    const clearBtn = loadClearButton(prefix);
    if (selectAllBtn) selectAllBtn.disabled = !allowManualSelection || buildings.length === 0;
    if (clearBtn) clearBtn.disabled = !allowManualSelection;
}

function setAllLoadBuildings(prefix, checked) {
    if (selectedScopeMode(prefix) !== LOAD_SCOPE_MODE.SELECTED) return;
    loadBuildingsContainer(prefix).querySelectorAll('[data-load-building]').forEach((checkbox) => {
        checkbox.checked = checked;
    });
    syncLoadBuildingScope(prefix);
}

function renderPermissionMatrix(targetBody, selectedPermissions = [], prefix = 'create') {
    const byTab = permissionMap(selectedPermissions);
    targetBody.innerHTML = TAB_GROUPS.map((group) => {
        const groupRows = group.tabs.map((tab) => {
            const current = byTab[tab.key] || { canView: tab.key !== 'USERS', canEdit: false };
            return `
                <tr data-tab-row="${tab.key}" data-tab-group="${group.key}">
                    <td class="permission-tab-cell">${tab.label}</td>
                    <td><input type="checkbox" data-tab-view="${tab.key}" data-tab-group="${group.key}" data-prefix="${prefix}" ${current.canView ? 'checked' : ''}></td>
                    <td><input type="checkbox" data-tab-edit="${tab.key}" data-tab-group="${group.key}" data-prefix="${prefix}" ${current.canEdit ? 'checked' : ''}></td>
                </tr>`;
        }).join('');
        return `
            <tr class="permission-group-row" data-group-row="${group.key}">
                <td><strong>${group.label}</strong></td>
                <td><input type="checkbox" data-group-view="${group.key}" data-prefix="${prefix}"></td>
                <td><input type="checkbox" data-group-edit="${group.key}" data-prefix="${prefix}"></td>
            </tr>
            ${groupRows}
        `;
    }).join('');
    bindMatrixInteractions(targetBody);
    syncRoleSpecificFields(prefix);
}

function syncGroupCheckboxes(targetBody, groupKey) {
    const viewTabs = Array.from(targetBody.querySelectorAll(`[data-tab-view][data-tab-group="${groupKey}"]`));
    const editTabs = Array.from(targetBody.querySelectorAll(`[data-tab-edit][data-tab-group="${groupKey}"]`));
    const groupView = targetBody.querySelector(`[data-group-view="${groupKey}"]`);
    const groupEdit = targetBody.querySelector(`[data-group-edit="${groupKey}"]`);
    if (!groupView || !groupEdit || !viewTabs.length) return;

    const enabledViewTabs = viewTabs.filter((checkbox) => !checkbox.disabled);
    const enabledEditTabs = editTabs.filter((checkbox) => !checkbox.disabled);
    const totalView = enabledViewTabs.length;
    const totalEdit = enabledEditTabs.length;
    const checkedView = enabledViewTabs.filter((checkbox) => checkbox.checked).length;
    const checkedEdit = enabledEditTabs.filter((checkbox) => checkbox.checked).length;
    const hasEnabledTabs = totalView > 0;

    groupView.disabled = !hasEnabledTabs;
    groupEdit.disabled = !hasEnabledTabs;

    groupView.checked = hasEnabledTabs && checkedView === totalView;
    groupView.indeterminate = hasEnabledTabs && checkedView > 0 && checkedView < totalView;
    groupEdit.checked = hasEnabledTabs && totalEdit > 0 && checkedEdit === totalEdit;
    groupEdit.indeterminate = hasEnabledTabs && checkedEdit > 0 && checkedEdit < totalEdit;
}

function syncAllGroupCheckboxes(targetBody) {
    TAB_GROUPS.forEach((group) => syncGroupCheckboxes(targetBody, group.key));
}

function bindMatrixInteractions(targetBody) {
    targetBody.querySelectorAll('[data-tab-view]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const tab = checkbox.dataset.tabView;
            const groupKey = checkbox.dataset.tabGroup;
            const editCheckbox = targetBody.querySelector(`[data-tab-edit="${tab}"]`);
            if (!checkbox.checked && editCheckbox) {
                editCheckbox.checked = false;
            }
            if (groupKey) syncGroupCheckboxes(targetBody, groupKey);
        });
    });

    targetBody.querySelectorAll('[data-tab-edit]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const tab = checkbox.dataset.tabEdit;
            const groupKey = checkbox.dataset.tabGroup;
            const viewCheckbox = targetBody.querySelector(`[data-tab-view="${tab}"]`);
            if (checkbox.checked && viewCheckbox) {
                viewCheckbox.checked = true;
            }
            if (groupKey) syncGroupCheckboxes(targetBody, groupKey);
        });
    });

    targetBody.querySelectorAll('[data-group-view]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const groupKey = checkbox.dataset.groupView;
            targetBody.querySelectorAll(`[data-tab-view][data-tab-group="${groupKey}"]`).forEach((tabCheckbox) => {
                if (tabCheckbox.disabled) return;
                tabCheckbox.checked = checkbox.checked;
                if (!checkbox.checked) {
                    const tab = tabCheckbox.dataset.tabView;
                    const editCheckbox = targetBody.querySelector(`[data-tab-edit="${tab}"]`);
                    if (editCheckbox && !editCheckbox.disabled) editCheckbox.checked = false;
                }
            });
            syncGroupCheckboxes(targetBody, groupKey);
        });
    });

    targetBody.querySelectorAll('[data-group-edit]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const groupKey = checkbox.dataset.groupEdit;
            targetBody.querySelectorAll(`[data-tab-edit][data-tab-group="${groupKey}"]`).forEach((tabCheckbox) => {
                if (tabCheckbox.disabled) return;
                tabCheckbox.checked = checkbox.checked;
                const tab = tabCheckbox.dataset.tabEdit;
                const viewCheckbox = targetBody.querySelector(`[data-tab-view="${tab}"]`);
                if (viewCheckbox && !viewCheckbox.disabled && checkbox.checked) {
                    viewCheckbox.checked = true;
                }
            });
            syncGroupCheckboxes(targetBody, groupKey);
        });
    });

    syncAllGroupCheckboxes(targetBody);
}

function syncRoleSpecificFields(prefix) {
    const roleSelect = prefix === 'create' ? ui.createRole : ui.editRole;
    const buildingSelect = prefix === 'create' ? ui.createManagedBuilding : ui.editManagedBuilding;
    const targetBody = prefix === 'create' ? ui.createPermissionsBody : ui.editPermissionsBody;
    const isAdmin = roleSelect.value === 'ADMIN';
    const isBuildingHead = roleSelect.value === 'BUILDING_HEAD';
    const currentScopeMode = selectedScopeMode(prefix);

    buildingSelect.disabled = !isBuildingHead;
    if (!isBuildingHead) {
        buildingSelect.value = '';
    }

    TABS.forEach((tab) => {
        const viewCheckbox = targetBody.querySelector(`[data-tab-view="${tab.key}"]`);
        const editCheckbox = targetBody.querySelector(`[data-tab-edit="${tab.key}"]`);
        if (!viewCheckbox || !editCheckbox) return;

        if (isAdmin) {
            viewCheckbox.checked = true;
            editCheckbox.checked = true;
            viewCheckbox.disabled = true;
            editCheckbox.disabled = true;
            return;
        }

        if (tab.key === 'USERS') {
            viewCheckbox.checked = false;
            editCheckbox.checked = false;
            viewCheckbox.disabled = true;
            editCheckbox.disabled = true;
            return;
        }

        viewCheckbox.disabled = false;
        editCheckbox.disabled = false;
    });
    syncAllGroupCheckboxes(targetBody);

    scopeInputs(prefix).forEach((input) => {
        const primaryMode = input.value === LOAD_SCOPE_MODE.PRIMARY;
        input.disabled = isAdmin || (primaryMode && !isBuildingHead);
        input.closest('.scope-mode-card')?.classList.toggle('is-disabled', input.disabled);
    });

    if (isAdmin) {
        setScopeMode(prefix, LOAD_SCOPE_MODE.ALL);
    } else if (!isBuildingHead && currentScopeMode === LOAD_SCOPE_MODE.PRIMARY) {
        setScopeMode(prefix, selectedLoadBuildings(prefix).length ? LOAD_SCOPE_MODE.SELECTED : LOAD_SCOPE_MODE.NONE);
    }

    syncLoadBuildingScope(prefix);
}

function collectPermissions(targetBody) {
    return TABS.map((tab) => ({
        tab: tab.key,
        canView: Boolean(targetBody.querySelector(`[data-tab-view="${tab.key}"]`)?.checked),
        canEdit: Boolean(targetBody.querySelector(`[data-tab-edit="${tab.key}"]`)?.checked)
    }));
}

function loadScopeState(prefix) {
    const mode = selectedScopeMode(prefix);
    if (mode === LOAD_SCOPE_MODE.ALL) {
        return { loadEditAllBuildings: true, loadEditableBuildingCodes: [] };
    }
    if (mode === LOAD_SCOPE_MODE.SELECTED) {
        return { loadEditAllBuildings: false, loadEditableBuildingCodes: selectedLoadBuildings(prefix) };
    }
    return { loadEditAllBuildings: false, loadEditableBuildingCodes: [] };
}

function validateLoadScopeSelection(prefix) {
    const mode = selectedScopeMode(prefix);
    const managedBuilding = prefix === 'create' ? ui.createManagedBuilding.value : ui.editManagedBuilding.value;
    const selectedCodes = selectedLoadBuildings(prefix);

    if (mode === LOAD_SCOPE_MODE.PRIMARY && !managedBuilding) {
        throw new Error('Для режима «Основной корпус» сначала выберите основной корпус пользователя.');
    }
    if (mode === LOAD_SCOPE_MODE.SELECTED && selectedCodes.length === 0) {
        throw new Error('Для режима «Выбранные корпуса» отметьте хотя бы один корпус или переключите режим на «Только просмотр».');
    }
}

function scopeModeFromUser(user) {
    if (user.loadEditAllBuildings) return LOAD_SCOPE_MODE.ALL;
    if ((user.loadEditableBuildingCodes || []).length) return LOAD_SCOPE_MODE.SELECTED;
    if (user.role === 'BUILDING_HEAD' && user.managedBuildingCode) return LOAD_SCOPE_MODE.PRIMARY;
    return LOAD_SCOPE_MODE.NONE;
}

function renderUsers(rows) {
    users = rows || [];
    ui.tbody.innerHTML = users.map((user) => `
        <tr>
            <td>${esc(user.username)}</td>
            <td>${esc(user.fullName)}</td>
            <td>${esc(roleLabel(user.role))}</td>
            <td>${esc(user.managedBuildingCode || '—')}</td>
            <td><span class="table-badge load-scope-badge">${esc(loadScopeLabel(user))}</span></td>
            <td>${esc(user.email || '—')}</td>
            <td><span class="table-badge ${user.active && user.canView ? 'status-active' : 'status-muted'}">${user.active && user.canView ? 'Активен' : 'Отключён'}</span></td>
            <td>
                <div class="row compact-row compact-actions">
                    <button type="button" class="open-user-settings-btn" data-id="${user.id}">Настроить</button>
                    <button type="button" class="reset-user-password-btn" data-id="${user.id}">Сбросить пароль</button>
                </div>
            </td>
        </tr>`).join('');

    ui.tbody.querySelectorAll('.open-user-settings-btn').forEach((btn) => {
        btn.addEventListener('click', () => openEditDialog(Number(btn.dataset.id)));
    });

    ui.tbody.querySelectorAll('.reset-user-password-btn').forEach((btn) => {
        btn.addEventListener('click', async () => {
            try {
                const result = await api(`/api/admin/users/${btn.dataset.id}/reset-password`, { method: 'POST' });
                print(result);
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
}

function openEditDialog(userId) {
    const user = users.find((row) => row.id === userId);
    if (!user) return;
    editingUserId = userId;
    ui.editForm.elements.fullName.value = user.fullName || '';
    ui.editForm.elements.email.value = user.email || '';
    ui.editForm.elements.active.checked = Boolean(user.active);
    ui.editForm.elements.canView.checked = Boolean(user.canView);
    ui.editForm.elements.canEdit.checked = Boolean(user.canEdit);
    ui.editRole.value = user.role;
    renderBuildingSelect(ui.editManagedBuilding, user.managedBuildingCode || '');
    renderPermissionMatrix(ui.editPermissionsBody, user.tabPermissions || [], 'edit');
    setScopeMode('edit', scopeModeFromUser(user));
    renderLoadBuildings(ui.editLoadBuildings, user.loadEditableBuildingCodes || [], 'edit');
    syncRoleSpecificFields('edit');
    ui.editDialog.showModal();
}

function resetCreateForm() {
    ui.form.reset();
    ui.form.querySelector('[name="canView"]').checked = true;
    renderBuildingSelect(ui.createManagedBuilding);
    renderPermissionMatrix(ui.createPermissionsBody, [], 'create');
    setScopeMode('create', LOAD_SCOPE_MODE.NONE);
    renderLoadBuildings(ui.createLoadBuildings, [], 'create');
    syncRoleSpecificFields('create');
}

async function reload() {
    const [userRows, buildingRows, teacherRows] = await Promise.all([
        api('/api/admin/users'),
        api('/api/buildings'),
        api('/api/teachers')
    ]);
    users = userRows || [];
    buildings = (buildingRows || []).slice().sort((a, b) => String(a.code || '').localeCompare(String(b.code || ''), 'ru'));
    teacherFioOptions = (teacherRows || [])
        .map((row) => String(row.fioTeacher || '').trim())
        .filter(Boolean)
        .filter((fio, idx, arr) => arr.findIndex((x) => x.toLowerCase() === fio.toLowerCase()) === idx)
        .sort((a, b) => a.localeCompare(b, 'ru'));
    renderTeacherFioDatalist(ui.createTeacherDatalist, teacherFioOptions.slice(0, 200));
    renderTeacherFioDatalist(ui.editTeacherDatalist, teacherFioOptions.slice(0, 200));
    renderBuildingSelect(ui.createManagedBuilding, ui.createManagedBuilding.value);
    renderLoadBuildings(ui.createLoadBuildings, selectedLoadBuildings('create'), 'create');
    renderUsers(users);
    syncRoleSpecificFields('create');
}

ui.createRole.addEventListener('change', () => syncRoleSpecificFields('create'));
ui.editRole.addEventListener('change', () => syncRoleSpecificFields('edit'));
ui.createManagedBuilding.addEventListener('change', () => syncRoleSpecificFields('create'));
ui.editManagedBuilding.addEventListener('change', () => syncRoleSpecificFields('edit'));
ui.editCloseBtn.addEventListener('click', () => ui.editDialog.close());
ui.editSaveBtn.addEventListener('click', () => ui.editForm.requestSubmit());
ui.createLoadSelectAll.addEventListener('click', () => setAllLoadBuildings('create', true));
ui.createLoadClear.addEventListener('click', () => setAllLoadBuildings('create', false));
ui.editLoadSelectAll.addEventListener('click', () => setAllLoadBuildings('edit', true));
ui.editLoadClear.addEventListener('click', () => setAllLoadBuildings('edit', false));
scopeInputs('create').forEach((input) => input.addEventListener('change', () => syncRoleSpecificFields('create')));
scopeInputs('edit').forEach((input) => input.addEventListener('change', () => syncRoleSpecificFields('edit')));

ui.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(ui.form);
    try {
        validateLoadScopeSelection('create');
        const loadScope = loadScopeState('create');
        const result = await api('/api/admin/users', {
            method: 'POST',
            headers: jsonHeaders,
            body: JSON.stringify({
                username: String(form.get('username') || '').trim(),
                fullName: String(form.get('fullName') || '').trim(),
                email: String(form.get('email') || '').trim(),
                managedBuildingCode: String(form.get('managedBuildingCode') || '').trim(),
                role: String(form.get('role') || ''),
                canView: form.get('canView') === 'on',
                canEdit: form.get('canEdit') === 'on',
                ...loadScope,
                tabPermissions: collectPermissions(ui.createPermissionsBody)
            })
        });
        print(result);
        resetCreateForm();
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.editForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!editingUserId) return;
    const form = new FormData(ui.editForm);
    try {
        validateLoadScopeSelection('edit');
        const loadScope = loadScopeState('edit');
        const result = await api(`/api/admin/users/${editingUserId}`, {
            method: 'PATCH',
            headers: jsonHeaders,
            body: JSON.stringify({
                fullName: String(form.get('fullName') || '').trim(),
                email: String(form.get('email') || '').trim(),
                managedBuildingCode: String(form.get('managedBuildingCode') || '').trim(),
                role: String(form.get('role') || ''),
                active: form.get('active') === 'on',
                canView: form.get('canView') === 'on',
                canEdit: form.get('canEdit') === 'on',
                ...loadScope,
                tabPermissions: collectPermissions(ui.editPermissionsBody)
            })
        });
        print(result);
        ui.editDialog.close();
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

ui.resetPasswordBtn.addEventListener('click', async () => {
    if (!editingUserId) return;
    try {
        const result = await api(`/api/admin/users/${editingUserId}/reset-password`, { method: 'POST' });
        print(result);
    } catch (error) {
        print({ error: error.message });
    }
});

resetCreateForm();
bindTeacherFioAutocomplete(ui.createFullName, ui.createTeacherDatalist);
bindTeacherFioAutocomplete(ui.editFullName, ui.editTeacherDatalist);
reload().then(renderAcademicYears).catch((error) => print({ error: error.message }));

ui.adminTabUsersBtn?.addEventListener('click', () => setAdminTab('users'));
ui.adminTabYearsBtn?.addEventListener('click', () => setAdminTab('years'));
ui.adminTabOptionsBtn?.addEventListener('click', () => setAdminTab('options'));
ui.debugModeInputs.forEach((input) => {
    input.addEventListener('change', () => {
        const enabled = input.value === 'yes';
        if (typeof window.setDebugOutputEnabled === 'function') {
            window.setDebugOutputEnabled(enabled);
        }
        renderDebugModeOptions();
        print({ debug: enabled ? 'enabled' : 'disabled' });
    });
});
renderDebugModeOptions();

ui.academicYearForm?.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
        const code = normalizeAcademicYearInput(ui.academicYearCode?.value);
        await api('/api/academic-years', {
            method: 'POST',
            headers: jsonHeaders,
            body: JSON.stringify({ code })
        });
        if (ui.academicYearCode) ui.academicYearCode.value = '';
        if (ui.academicYearFeedback) {
            ui.academicYearFeedback.textContent = `Учебный год ${code} создан.`;
        }
        await renderAcademicYears();
    } catch (error) {
        if (ui.academicYearFeedback) {
            ui.academicYearFeedback.textContent = `Ошибка: ${error.message}`;
        }
        print({ error: error.message });
    }
});
