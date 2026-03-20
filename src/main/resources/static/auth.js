const authState = {
    me: null,
    currentPage: (() => {
        const file = window.location.pathname.split('/').pop() || 'index.html';
        return file;
    })()
};

const pagePermissions = {
    'index.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'BUILDING_HEAD'] },
    'buildings.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'BUILDING_HEAD'], clear: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'] },
    'classes.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'], clear: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'] },
    'curriculum.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'], clear: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'] },
    'load.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'BUILDING_HEAD'], process: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR'] },
    'teachers.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'HR'], clear: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'HR'] },
    'users.html': { edit: ['ADMIN'] },
    'audit.html': { edit: ['ADMIN'] },
    'profile.html': { edit: ['ADMIN', 'DIRECTOR', 'DEPUTY_DIRECTOR', 'BUILDING_HEAD', 'HR', 'METHODIST', 'OPERATOR'] }
};

function canDo(permission) {
    const role = authState.me?.role;
    const config = pagePermissions[authState.currentPage] || {};
    const allowed = config[permission];
    return !allowed || allowed.includes(role);
}

function hide(selector) {
    document.querySelectorAll(selector).forEach((node) => {
        node.classList.add('hidden-by-role');
        node.setAttribute('hidden', 'hidden');
    });
}

async function fetchCurrentUser() {
    const response = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (response.status === 401) {
        if (!window.location.pathname.endsWith('/login.html')) {
            window.location.href = '/login.html';
        }
        return null;
    }
    if (!response.ok) {
        throw new Error('Не удалось получить текущего пользователя');
    }
    authState.me = await response.json();
    return authState.me;
}

function renderAuthHeader() {
    const slot = document.getElementById('auth-slot');
    if (!slot || !authState.me) return;
    slot.innerHTML = `
        <div class="auth-panel">
            <div>
                <strong>${escapeHtml(authState.me.fullName || authState.me.username)}</strong>
                <div class="muted">${escapeHtml(authState.me.role)} · ${escapeHtml(authState.me.email || '')}</div>
            </div>
            <div class="row">
                <a class="nav-link" href="/ui/profile">Профиль</a>
                ${authState.me.role === 'ADMIN' ? '<a class="nav-link" href="/ui/users">Пользователи</a><a class="nav-link" href="/ui/audit">Аудит</a>' : ''}
                <button id="logout-btn" type="button">Выйти</button>
            </div>
        </div>`;
    document.getElementById('logout-btn')?.addEventListener('click', async () => {
        await fetch('/api/auth/logout', { method: 'POST' });
        window.location.href = '/login.html';
    });
}

function applyRoleVisibility() {
    if (!canDo('edit')) {
        hide('form');
        hide('button[type="submit"]');
        hide('#import-teachers-btn, #refresh-load-btn, #save-building-btn, #process-btn, #save-curriculum-item, #delete-curriculum-item');
    }
    if (!canDo('clear')) {
        hide('#clear-buildings-btn, #clear-classes-btn, #clear-curriculum-btn, #clear-teachers-btn');
    }
    if (!canDo('process')) {
        hide('#process-btn');
    }
    document.querySelectorAll('[data-role-required]').forEach((node) => {
        const roles = node.dataset.roleRequired.split(',').map((v) => v.trim());
        if (!roles.includes(authState.me?.role)) {
            node.hidden = true;
        }
    });
}

function escapeHtml(v) {
    return String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

window.initAuth = async function initAuth() {
    await fetchCurrentUser();
    renderAuthHeader();
    applyRoleVisibility();
    return authState.me;
};
