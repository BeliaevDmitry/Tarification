const jsonHeaders = { 'Content-Type': 'application/json' };

const ui = {
    form: document.getElementById('user-create-form'),
    result: document.getElementById('admin-result'),
    tbody: document.getElementById('users-table-body')
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

function renderUsers(users) {
    const readonly = !(window.tarificationAuth?.canEdit || window.tarificationAuth?.admin);
    ui.tbody.innerHTML = users.map((user) => `
        <tr>
            <td>${esc(user.username)}</td>
            <td><input data-field="fullName" data-id="${user.id}" value="${esc(user.fullName)}" ${readonly || user.admin ? 'disabled' : ''}></td>
            <td>
                <select data-field="role" data-id="${user.id}" ${readonly ? 'disabled' : ''}>
                    ${['DIRECTOR','DEPUTY_DIRECTOR','BUILDING_HEAD','METHODIST','HR','ADMIN'].map((role) => `<option value="${role}" ${user.role === role ? 'selected' : ''}>${roleLabel(role)}</option>`).join('')}
                </select>
            </td>
            <td><input data-field="email" data-id="${user.id}" value="${esc(user.email || '')}" ${readonly ? 'disabled' : ''}></td>
            <td><input type="checkbox" data-field="canView" data-id="${user.id}" ${user.canView ? 'checked' : ''} ${readonly || user.admin ? 'disabled' : ''}></td>
            <td><input type="checkbox" data-field="canEdit" data-id="${user.id}" ${user.canEdit ? 'checked' : ''} ${readonly || user.admin ? 'disabled' : ''}></td>
            <td><input type="checkbox" data-field="active" data-id="${user.id}" ${user.active ? 'checked' : ''} ${readonly || user.admin ? 'disabled' : ''}></td>
            <td>
                <div class="row">
                    <button type="button" class="save-user-btn" data-id="${user.id}" ${readonly ? 'disabled' : ''}>Сохранить</button>
                    <button type="button" class="reset-password-btn" data-id="${user.id}" ${readonly ? 'disabled' : ''}>Сбросить пароль</button>
                </div>
            </td>
        </tr>`).join('');

    ui.tbody.querySelectorAll('.save-user-btn').forEach((btn) => {
        btn.addEventListener('click', async () => {
            const id = btn.dataset.id;
            try {
                const payload = collectRowPayload(id);
                const result = await api(`/api/admin/users/${id}`, {
                    method: 'PATCH',
                    headers: jsonHeaders,
                    body: JSON.stringify(payload)
                });
                print(result);
                await reload();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });

    ui.tbody.querySelectorAll('.reset-password-btn').forEach((btn) => {
        btn.addEventListener('click', async () => {
            const id = btn.dataset.id;
            try {
                const result = await api(`/api/admin/users/${id}/reset-password`, { method: 'POST' });
                print(result);
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
}

function collectRowPayload(id) {
    const valueOf = (field) => ui.tbody.querySelector(`[data-field="${field}"][data-id="${id}"]`);
    return {
        fullName: valueOf('fullName')?.value,
        role: valueOf('role')?.value,
        email: valueOf('email')?.value,
        canView: Boolean(valueOf('canView')?.checked),
        canEdit: Boolean(valueOf('canEdit')?.checked),
        active: Boolean(valueOf('active')?.checked)
    };
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

async function reload() {
    const users = await api('/api/admin/users');
    renderUsers(users || []);
}

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
                role: String(form.get('role') || ''),
                canView: form.get('canView') === 'on',
                canEdit: form.get('canEdit') === 'on'
            })
        });
        print(result);
        ui.form.reset();
        ui.form.querySelector('[name="canView"]').checked = true;
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
});

reload().catch((error) => print({ error: error.message }));
