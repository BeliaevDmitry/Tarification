const jsonHeaders = { 'Content-Type': 'application/json' };
const TABS = [
    { key: 'BUILDINGS', label: 'Корпуса' },
    { key: 'CLASSES', label: 'Классы' },
    { key: 'SUBJECTS', label: 'Предметы' },
    { key: 'CURRICULUM', label: 'Учебный план' },
    { key: 'LOAD', label: 'Нагрузка по корпусам' },
    { key: 'TEACHERS', label: 'Педагоги' },
    { key: 'USERS', label: 'Пользователи' }
];

const ui = {
    form: document.getElementById('user-create-form'),
    result: document.getElementById('admin-result'),
    tbody: document.getElementById('users-table-body'),
    createRole: document.getElementById('create-role'),
    createManagedBuilding: document.getElementById('create-managed-building'),
    createPermissionsBody: document.getElementById('create-permissions-body'),
    editDialog: document.getElementById('user-edit-dialog'),
    editForm: document.getElementById('user-edit-form'),
    editRole: document.getElementById('edit-role'),
    editManagedBuilding: document.getElementById('edit-managed-building'),
    editPermissionsBody: document.getElementById('edit-permissions-body'),
    editCloseBtn: document.getElementById('user-edit-close-btn'),
    resetPasswordBtn: document.getElementById('reset-password-btn')
};

let buildings = [];
let users = [];
let editingUserId = null;

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

function print(value) {
    ui.result.textContent = JSON.stringify(value, null, 2);
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

function renderBuildingSelect(selectEl, selectedValue = '') {
    selectEl.innerHTML = '<option value="">Корпус не закреплён</option>';
    buildings.forEach((building) => {
        const option = document.createElement('option');
        option.value = building.code;
        option.textContent = `${building.code} — ${building.name}`;
        if (selectedValue && selectedValue === building.code) option.selected = true;
        selectEl.appendChild(option);
    });
}

function renderPermissionMatrix(targetBody, selectedPermissions = [], prefix = 'create') {
    const byTab = permissionMap(selectedPermissions);
    targetBody.innerHTML = TABS.map((tab) => {
        const current = byTab[tab.key] || { canView: tab.key !== 'USERS', canEdit: false };
        return `
            <tr data-tab-row="${tab.key}">
                <td>${tab.label}</td>
                <td><input type="checkbox" data-tab-view="${tab.key}" data-prefix="${prefix}" ${current.canView ? 'checked' : ''}></td>
                <td><input type="checkbox" data-tab-edit="${tab.key}" data-prefix="${prefix}" ${current.canEdit ? 'checked' : ''}></td>
            </tr>`;
    }).join('');
    bindMatrixInteractions(targetBody, prefix);
    syncRoleSpecificFields(prefix);
}

function bindMatrixInteractions(targetBody, prefix) {
    targetBody.querySelectorAll('[data-tab-view]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const tab = checkbox.dataset.tabView;
            const editCheckbox = targetBody.querySelector(`[data-tab-edit="${tab}"]`);
            if (!checkbox.checked && editCheckbox) {
                editCheckbox.checked = false;
            }
        });
    });

    targetBody.querySelectorAll('[data-tab-edit]').forEach((checkbox) => {
        checkbox.addEventListener('change', () => {
            const tab = checkbox.dataset.tabEdit;
            const viewCheckbox = targetBody.querySelector(`[data-tab-view="${tab}"]`);
            if (checkbox.checked && viewCheckbox) {
                viewCheckbox.checked = true;
            }
        });
    });
}

function syncRoleSpecificFields(prefix) {
    const roleSelect = prefix === 'create' ? ui.createRole : ui.editRole;
    const buildingSelect = prefix === 'create' ? ui.createManagedBuilding : ui.editManagedBuilding;
    const targetBody = prefix === 'create' ? ui.createPermissionsBody : ui.editPermissionsBody;
    const isAdmin = roleSelect.value === 'ADMIN';
    const isBuildingHead = roleSelect.value === 'BUILDING_HEAD';

    buildingSelect.disabled = !isBuildingHead;
    if (!isBuildingHead) buildingSelect.value = '';

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
}

function collectPermissions(targetBody) {
    return TABS.map((tab) => ({
        tab: tab.key,
        canView: Boolean(targetBody.querySelector(`[data-tab-view="${tab.key}"]`)?.checked),
        canEdit: Boolean(targetBody.querySelector(`[data-tab-edit="${tab.key}"]`)?.checked)
    }));
}

function renderUsers(rows) {
    users = rows || [];
    ui.tbody.innerHTML = users.map((user) => `
        <tr>
            <td>${esc(user.username)}</td>
            <td>${esc(user.fullName)}</td>
            <td>${esc(roleLabel(user.role))}</td>
            <td>${esc(user.managedBuildingCode || '—')}</td>
            <td>${esc(user.email || '—')}</td>
            <td>${user.active && user.canView ? 'Активен' : 'Отключён'}</td>
            <td>
                <div class="row">
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
    ui.editForm.elements.id.value = String(user.id);
    ui.editForm.elements.fullName.value = user.fullName || '';
    ui.editForm.elements.email.value = user.email || '';
    ui.editForm.elements.active.checked = Boolean(user.active);
    ui.editForm.elements.canView.checked = Boolean(user.canView);
    ui.editForm.elements.canEdit.checked = Boolean(user.canEdit);
    ui.editRole.value = user.role;
    renderBuildingSelect(ui.editManagedBuilding, user.managedBuildingCode || '');
    renderPermissionMatrix(ui.editPermissionsBody, user.tabPermissions || [], 'edit');
    syncRoleSpecificFields('edit');
    ui.editDialog.showModal();
}

async function reload() {
    const [userRows, buildingRows] = await Promise.all([
        api('/api/admin/users'),
        api('/api/buildings')
    ]);
    users = userRows || [];
    buildings = (buildingRows || []).slice().sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'ru'));
    renderBuildingSelect(ui.createManagedBuilding);
    renderUsers(users);
}

ui.createRole.addEventListener('change', () => syncRoleSpecificFields('create'));
ui.editRole.addEventListener('change', () => syncRoleSpecificFields('edit'));
ui.editCloseBtn.addEventListener('click', () => ui.editDialog.close());

ui.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(ui.form);
    try {
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
                tabPermissions: collectPermissions(ui.createPermissionsBody)
            })
        });
        print(result);
        ui.form.reset();
        ui.form.querySelector('[name="canView"]').checked = true;
        renderPermissionMatrix(ui.createPermissionsBody, [], 'create');
        renderBuildingSelect(ui.createManagedBuilding);
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

renderPermissionMatrix(ui.createPermissionsBody, [], 'create');
reload().catch((error) => print({ error: error.message }));
