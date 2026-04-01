const TAB_PATHS = {
    '/buildings.html': 'BUILDINGS',
    '/classes.html': 'CLASSES',
    '/subjects.html': 'SUBJECTS',
    '/curriculum.html': 'CURRICULUM',
    '/load.html': 'LOAD',
    '/service-notes.html': 'SERVICE_NOTES',
    '/settings.html': 'SETTINGS',
    '/teachers.html': 'TEACHERS',
    '/admin.html': 'USERS'
};

const NAV_ORDER = [
    { path: '/buildings.html', tab: 'BUILDINGS', label: 'Корпуса' },
    { path: '/classes.html', tab: 'CLASSES', label: 'Классы' },
    { path: '/subjects.html', tab: 'SUBJECTS', label: 'Предметы' },
    { path: '/curriculum.html', tab: 'CURRICULUM', label: 'Учебный план' },
    { path: '/load.html', tab: 'LOAD', label: 'Нагрузка по корпусам' },
    { path: '/service-notes.html', tab: 'SERVICE_NOTES', label: 'Служебные записки' },
    { path: '/settings.html', tab: 'SETTINGS', label: 'Настройки' },
    { path: '/teachers.html', tab: 'TEACHERS', label: 'Педагоги' },
    { path: '/admin.html', tab: 'USERS', label: 'Пользователи' }
];

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

function canEditCurrentPage(currentUser) {
    if (currentUser.admin) return true;
    const tab = currentTab();
    if (!tab) return currentUser.canEdit;
    return Boolean(tabPermissionMap(currentUser)[tab]?.canEdit);
}

function stickyHeader() {
    return document.querySelector('header.card');
}

function updateStickyHeaderMetrics() {
    const header = stickyHeader();
    if (!header) return;
    const height = Math.ceil(header.getBoundingClientRect().height);
    document.documentElement.style.setProperty('--sticky-header-height', `${height}px`);
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
    const header = stickyHeader();
    if (!header || header.querySelector('.readonly-note')) return;
    const note = document.createElement('p');
    note.className = 'muted readonly-note';
    note.textContent = 'У вас открыт режим просмотра для текущей вкладки: данные можно смотреть, но не редактировать.';
    header.appendChild(note);
}

function mountHeaderUser(currentUser) {
    const header = stickyHeader();
    if (!header) return;

    header.classList.add('app-shell-header');

    const title = header.querySelector('h1');
    if (!title) return;

    let titleRow = header.querySelector('.header-title-row');
    if (!titleRow) {
        titleRow = document.createElement('div');
        titleRow.className = 'header-title-row';
        title.before(titleRow);
        titleRow.appendChild(title);
    }

    let controls = titleRow.querySelector('.header-user-inline');
    if (!controls) {
        controls = document.createElement('div');
        controls.className = 'header-user-inline';
        controls.innerHTML = `
            <button type="button" class="header-user-badge" id="profile-btn"></button>
            <button type="button" id="logout-btn">Выйти</button>`;
        titleRow.appendChild(controls);
    }

    const badge = controls.querySelector('#profile-btn');
    if (badge) {
        badge.textContent = currentUser.fullName;
    }

    document.getElementById('profile-btn')?.addEventListener('click', () => {
        openProfileModal(currentUser);
    });

    document.getElementById('logout-btn')?.addEventListener('click', async () => {
        try {
            await tarificationApi('/api/auth/logout', { method: 'POST' });
        } catch {
            // ignore
        }
        window.location.href = '/login.html';
    });

    updateStickyHeaderMetrics();
    window.addEventListener('resize', updateStickyHeaderMetrics, { passive: true });
}

function openProfileModal(currentUser) {
    if (document.getElementById('profile-modal')) return;
    const overlay = document.createElement('div');
    overlay.className = 'password-modal-overlay';
    overlay.id = 'profile-modal';
    const buildingAccess = currentUser.loadEditAllBuildings
        ? 'Все корпуса'
        : (currentUser.loadEditableBuildingCodes || []).join(', ') || '—';
    overlay.innerHTML = `
        <div class="password-modal card profile-modal-card">
            <h3>Личный кабинет</h3>
            <div class="profile-grid">
                <div><span class="muted">ФИО:</span> ${currentUser.fullName || '—'}</div>
                <div><span class="muted">Логин:</span> ${currentUser.username || '—'}</div>
                <div><span class="muted">Роль:</span> ${currentUser.roleDisplayName || currentUser.role || '—'}</div>
                <div><span class="muted">Email:</span> ${currentUser.email || '—'}</div>
                <div><span class="muted">Доступ к просмотру:</span> ${currentUser.canView ? 'Да' : 'Нет'}</div>
                <div><span class="muted">Доступ к редактированию:</span> ${currentUser.canEdit ? 'Да' : 'Нет'}</div>
                <div><span class="muted">Корпус руководителя:</span> ${currentUser.managedBuildingCode || '—'}</div>
                <div><span class="muted">Корпуса для нагрузки:</span> ${buildingAccess}</div>
            </div>
            <hr />
            <h4 class="profile-subtitle">Смена пароля</h4>
            <label>Текущий пароль
                <input type="password" id="current-password" autocomplete="current-password" />
            </label>
            <label>Новый пароль
                <input type="password" id="new-password" autocomplete="new-password" />
            </label>
            <label>Подтверждение нового пароля
                <input type="password" id="confirm-password" autocomplete="new-password" />
            </label>
            <p class="muted" id="change-password-message"></p>
            <div class="password-modal-actions">
                <button type="button" id="close-profile-btn">Закрыть</button>
                <button type="button" id="save-password-btn">Сменить пароль</button>
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    const closeModal = () => overlay.remove();
    overlay.addEventListener('click', (event) => {
        if (event.target === overlay) closeModal();
    });
    overlay.querySelector('#close-profile-btn')?.addEventListener('click', closeModal);
    overlay.querySelector('#save-password-btn')?.addEventListener('click', async () => {
        const currentPassword = overlay.querySelector('#current-password')?.value || '';
        const newPassword = overlay.querySelector('#new-password')?.value || '';
        const confirmPassword = overlay.querySelector('#confirm-password')?.value || '';
        const message = overlay.querySelector('#change-password-message');
        if (!currentPassword || !newPassword || !confirmPassword) {
            message.textContent = 'Заполните все поля.';
            return;
        }
        if (newPassword !== confirmPassword) {
            message.textContent = 'Подтверждение пароля не совпадает.';
            return;
        }
        try {
            await tarificationApi('/api/auth/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword, newPassword })
            });
            message.textContent = 'Пароль успешно обновлён.';
            setTimeout(closeModal, 500);
        } catch (error) {
            message.textContent = error.message || 'Не удалось сменить пароль.';
        }
    });
}

function enrichNavigation(currentUser) {
    const permissions = tabPermissionMap(currentUser);
    document.querySelectorAll('.page-nav').forEach((nav) => {
        nav.innerHTML = '';
        NAV_ORDER.forEach((tabDef) => {
            const canView = currentUser.admin || permissions[tabDef.tab]?.canView;
            if (!canView) return;
            const link = document.createElement('a');
            link.className = 'nav-link';
            link.href = tabDef.path;
            link.dataset.tab = tabDef.tab;
            link.textContent = tabDef.label;
            if (window.location.pathname === tabDef.path) {
                link.classList.add('active');
            }
            nav.appendChild(link);
        });
    });
}

(async function initAuth() {
    try {
        const currentUser = await tarificationApi('/api/auth/me');
        window.tarificationAuth = currentUser;
        window.tarificationTabPermissions = tabPermissionMap(currentUser);
        enrichNavigation(currentUser);
        mountHeaderUser(currentUser);
        insertReadonlyNotice(currentUser);
        disableEditAreas(currentUser);
        updateStickyHeaderMetrics();
    } catch {
        window.location.href = '/login.html';
    }
})();
