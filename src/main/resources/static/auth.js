const TAB_PATHS = {
    '/buildings.html': 'BUILDINGS',
    '/classes.html': 'CLASSES',
    '/subjects.html': 'SUBJECTS',
    '/curriculum.html': 'CURRICULUM',
    '/load.html': 'LOAD',
    '/teachers.html': 'TEACHERS',
    '/admin.html': 'USERS'
};

async function tarificationApi(path, options = {}) {
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

function tabPermissionMap(currentUser) {
    return Object.fromEntries((currentUser.tabPermissions || []).map((permission) => [permission.tab, permission]));
}

function currentTab() {
    return TAB_PATHS[window.location.pathname] || null;
}


function loadScopeLabel(currentUser) {
    if (currentUser.loadEditAllBuildings) return 'все корпуса';
    const codes = (currentUser.loadEditableBuildingCodes || []).filter(Boolean);
    if (codes.length) return codes.join(', ');
    if (currentUser.role === 'BUILDING_HEAD' && currentUser.managedBuildingCode) {
        return `основной корпус ${currentUser.managedBuildingCode}`;
    }
    return 'только просмотр';
}

function canEditCurrentPage(currentUser) {
    if (currentUser.admin) return true;
    const tab = currentTab();
    if (!tab) return currentUser.canEdit;
    return Boolean(tabPermissionMap(currentUser)[tab]?.canEdit);
}

function disableEditAreas(currentUser) {
    if (canEditCurrentPage(currentUser)) return;

    const disableControls = () => {
        document.querySelectorAll('[data-requires-edit]').forEach((container) => {
            container.classList.add('readonly-block');
            container.querySelectorAll('button, input, select, textarea').forEach((el) => {
                if (el.dataset.allowReadonly === 'true') return;
                el.disabled = true;
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    el.readOnly = true;
                }
            });
        });
    };

    disableControls();
    const observer = new MutationObserver(() => disableControls());
    observer.observe(document.body, { childList: true, subtree: true });
}

function insertReadonlyNotice(currentUser) {
    if (canEditCurrentPage(currentUser)) return;
    const header = document.querySelector('header.card');
    if (!header || header.querySelector('.readonly-note')) return;
    const note = document.createElement('p');
    note.className = 'muted readonly-note';
    note.textContent = 'У вас открыт режим просмотра для текущей вкладки: данные можно смотреть, но не редактировать.';
    header.appendChild(note);
}

function mountUserBar(currentUser) {
    const container = document.querySelector('.container');
    if (!container) return;

    const bar = document.createElement('section');
    bar.className = 'card auth-bar';
    bar.innerHTML = `
        <div class="row auth-row">
            <div>
                <strong>${currentUser.fullName}</strong>
                <div class="muted">${currentUser.roleDisplayName} · логин: ${currentUser.username}</div>
                ${currentUser.managedBuildingCode ? `<div class="muted">Основной корпус: ${currentUser.managedBuildingCode}</div>` : ''}
                <div class="muted">Нагрузка: ${loadScopeLabel(currentUser)}</div>
            </div>
            <div class="row auth-actions">
                ${canEditCurrentPage(currentUser) ? '<span class="permission-badge edit-badge">Редактирование вкладки</span>' : '<span class="permission-badge view-badge">Только просмотр вкладки</span>'}
                <button type="button" id="logout-btn">Выйти</button>
            </div>
        </div>`;
    container.insertBefore(bar, container.firstChild);

    document.getElementById('logout-btn')?.addEventListener('click', async () => {
        try {
            await tarificationApi('/api/auth/logout', { method: 'POST' });
        } catch {
            // ignore
        }
        window.location.href = '/login.html';
    });
}

function enrichNavigation(currentUser) {
    const permissions = tabPermissionMap(currentUser);
    document.querySelectorAll('.page-nav').forEach((nav) => {
        nav.querySelectorAll('[data-tab]').forEach((link) => {
            const tab = link.dataset.tab;
            if (currentUser.admin || permissions[tab]?.canView) return;
            link.remove();
        });
        if (currentUser.admin && !nav.querySelector('a[href="/admin.html"]')) {
            const link = document.createElement('a');
            link.className = 'nav-link';
            link.href = '/admin.html';
            link.dataset.tab = 'USERS';
            link.textContent = 'Пользователи';
            if (window.location.pathname === '/admin.html') {
                link.classList.add('active');
            }
            nav.appendChild(link);
        }
    });
}

(async function initAuth() {
    try {
        const currentUser = await tarificationApi('/api/auth/me');
        window.tarificationAuth = currentUser;
        window.tarificationTabPermissions = tabPermissionMap(currentUser);
        mountUserBar(currentUser);
        enrichNavigation(currentUser);
        insertReadonlyNotice(currentUser);
        disableEditAreas(currentUser);
    } catch {
        window.location.href = '/login.html';
    }
})();
