const TAB_PATHS = {
    '/': null,
    '/index.html': null,
    '/buildings.html': 'BUILDINGS',
    '/classes.html': 'CLASSES',
    '/subjects.html': 'SUBJECTS',
    '/curriculum.html': 'CURRICULUM',
    '/load.html': 'LOAD',
    '/load-statistics.html': 'LOAD_STATS',
    '/service-notes.html': 'SERVICE_NOTES',
    '/settings.html': 'SETTINGS',
    '/teachers.html': 'TEACHERS',
    '/teachers-notification.html': 'HR_NOTIFICATIONS_VIEW',
    '/contingent.html': 'CONTINGENT_STATS',
    '/vsoko.html': 'VSOKO_VIEW',
    '/vsoko-oge.html': 'VSOKO_VIEW',
    '/vsoko-ege.html': 'VSOKO_VIEW',
    '/vsoko-pa.html': 'VSOKO_VIEW',
    '/vsoko-pa-spec.html': 'VSOKO_VIEW',
    '/vsoko-pa-entry.html': 'VSOKO_VIEW',
    '/vsoko-pa-exit.html': 'VSOKO_VIEW',
    '/vsoko-pa-folders.html': 'VSOKO_VIEW',
    '/vsoko-pa-upload.html': 'VSOKO_VIEW',
    '/subject-areas.html': 'SUBJECT_AREAS',
    '/admin.html': 'USERS'
};

const NAV_ORDER = [
    { path: '/buildings.html', tab: 'BUILDINGS', label: 'Корпуса' },
    { path: '/classes.html', tab: 'CLASSES', label: 'Классы' },
    { path: '/subjects.html', tab: 'SUBJECTS', label: 'Предметы' },
    { path: '/curriculum.html', tab: 'CURRICULUM', label: 'Учебный план' },
    { path: '/load.html', tab: 'LOAD', label: 'Нагрузка по корпусам' },
    { path: '/load-statistics.html', tab: 'LOAD_STATS', label: 'Статистика нагрузки' },
    { path: '/service-notes.html', tab: 'SERVICE_NOTES', label: 'Служебные записки' },
    { path: '/settings.html', tab: 'SETTINGS', label: 'Настройки' },
    { path: '/subject-areas.html', tab: 'SUBJECT_AREAS', label: 'Предметные области' },
    { path: '/vsoko.html', tab: 'VSOKO_VIEW', label: 'ВСОКО' }
];

const PA_NAV_ORDER = [
    { path: '/vsoko-pa.html', tab: 'VSOKO_VIEW', label: '← Вернуться к ПА' },
    { path: '/vsoko-pa-spec.html', tab: 'VSOKO_VIEW', label: 'Спецификации работ' },
    { path: '/vsoko-pa-entry.html', tab: 'VSOKO_VIEW', label: 'Входные работы' },
    { path: '/vsoko-pa-exit.html', tab: 'VSOKO_VIEW', label: 'Выходные работы' },
    { path: '/vsoko-pa-folders.html', tab: 'VSOKO_VIEW', label: 'Отчёты по папкам' },
    { path: '/vsoko-pa-upload.html', tab: 'VSOKO_VIEW', label: 'Сдача ПА' }
];
const PA_HUB_NAV_ORDER = [
    { path: '/vsoko.html', tab: 'VSOKO_VIEW', label: '← Вернуться к ВСОКО' }
];

function isPaSubPage(pathname) {
    return pathname === '/vsoko-pa-spec.html'
        || pathname === '/vsoko-pa-entry.html'
        || pathname === '/vsoko-pa-exit.html'
        || pathname === '/vsoko-pa-folders.html'
        || pathname === '/vsoko-pa-upload.html';
}

function isLoadModulePage(pathname) {
    return pathname === '/buildings.html'
        || pathname === '/classes.html'
        || pathname === '/subjects.html'
        || pathname === '/curriculum.html'
        || pathname === '/load.html'
        || pathname === '/load-statistics.html'
        || pathname === '/service-notes.html'
        || pathname === '/settings.html'
        || pathname === '/subject-areas.html';
}

function navItemsForPath(pathname) {
    if (pathname === '/vsoko-pa.html') {
        return PA_HUB_NAV_ORDER;
    }
    if (isPaSubPage(pathname)) {
        return PA_NAV_ORDER;
    }
    if (pathname === '/teachers.html' || pathname === '/teachers-notification.html') {
        return [
            { path: '/teachers.html', tab: 'TEACHERS', label: 'Персонал' },
            { path: '/service-notes.html', tab: 'SERVICE_NOTES', label: 'Служебные записки' },
            { path: '/teachers-notification.html', tab: 'HR_NOTIFICATIONS_VIEW', label: 'Уведомления' }
        ];
    }
    if (isLoadModulePage(pathname)) {
        return NAV_ORDER.filter((tabDef) => tabDef.tab !== 'VSOKO_VIEW');
    }
    return NAV_ORDER;
}

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

const ACADEMIC_YEAR_STORAGE_KEY = 'tarification.academicYear';
const DEBUG_OUTPUT_STORAGE_KEY = 'tarification.debugOutput';

function getStoredAcademicYear() {
    return sessionStorage.getItem(ACADEMIC_YEAR_STORAGE_KEY) || '';
}

function setStoredAcademicYear(value) {
    if (!value) {
        sessionStorage.removeItem(ACADEMIC_YEAR_STORAGE_KEY);
        return;
    }
    sessionStorage.setItem(ACADEMIC_YEAR_STORAGE_KEY, value);
}

function withAcademicYear(path) {
    const selectedYear = getStoredAcademicYear();
    if (!selectedYear) return path;
    const separator = path.includes('?') ? '&' : '?';
    return `${path}${separator}academicYear=${encodeURIComponent(selectedYear)}`;
}

function debugOutputEnabledForUser(currentUser) {
    if (!currentUser?.admin) return false;
    const raw = localStorage.getItem(DEBUG_OUTPUT_STORAGE_KEY);
    if (raw === null) return true;
    return raw === '1';
}

function applyDebugOutputVisibility(currentUser) {
    const enabled = debugOutputEnabledForUser(currentUser);
    document.body.classList.toggle('debug-output-hidden', !enabled);
    window.tarificationDebugOutputEnabled = enabled;
}

function tabPermissionMap(currentUser) {
    return Object.fromEntries((currentUser.tabPermissions || []).map((permission) => [permission.tab, permission]));
}

function currentTab() {
    if (window.location.pathname === '/load.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#stats') return 'LOAD_STATS';
        return 'LOAD';
    }
    if (window.location.pathname === '/contingent.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#import') return 'CONTINGENT_IMPORT';
        return 'CONTINGENT_STATS';
    }
    return TAB_PATHS[window.location.pathname] || null;
}

function isAdminPage() {
    return window.location.pathname === '/admin.html';
}

function isContingentPage() {
    return window.location.pathname === '/contingent.html';
}

function isLoadPage() {
    return window.location.pathname === '/load.html'
        || window.location.pathname === '/load-statistics.html';
}

function hasContingentAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.CONTINGENT_IMPORT?.canView || permissions.CONTINGENT_STATS?.canView);
}

function hasLoadAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.LOAD?.canView || permissions.LOAD_STATS?.canView);
}

function showAccessDenied(sectionTitle = 'раздела') {
    const container = document.querySelector('main.container');
    if (!container) return;
    container.innerHTML = `
        <section class="card access-denied-card">
            <h1>⛔ Доступ запрещён</h1>
            <p class="muted">У вас нет прав для доступа к ${sectionTitle}.</p>
            <a class="nav-link" href="/index.html">Вернуться в главное меню</a>
        </section>`;
}

function canEditCurrentPage(currentUser) {
    if (currentUser.admin) return true;
    if (window.location.pathname === '/vsoko-oge.html') {
        const permissions = tabPermissionMap(currentUser);
        return Boolean(
            permissions.VSOKO_EDIT?.canEdit
            || permissions.OGE_GIA_UPLOAD?.canEdit
            || permissions.OGE_WORK_UPLOAD?.canEdit
            || permissions.OGE_SCORE_VIEW?.canEdit
            || permissions.OGE_EVALUATION_VIEW?.canEdit
        );
    }
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
            <a class="home-link" href="/index.html" title="Главное меню" aria-label="Главное меню">🏠</a>
            <label class="header-year-select-wrap">
                <select id="academic-year-select"></select>
            </label>
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
        sessionStorage.removeItem(ACADEMIC_YEAR_STORAGE_KEY);
        window.location.href = '/login.html';
    });

    updateStickyHeaderMetrics();
    window.addEventListener('resize', updateStickyHeaderMetrics, { passive: true });
}

async function mountAcademicYearSelector() {
    const select = document.getElementById('academic-year-select');
    if (!select) return;
    const years = await tarificationApi('/api/academic-years');
    const active = await tarificationApi('/api/academic-years/active');
    const currentStored = getStoredAcademicYear();
    const effective = currentStored || active.active;
    if (!currentStored) {
        setStoredAcademicYear(effective);
    }

    select.innerHTML = (years || [])
        .sort((a, b) => String(a.code).localeCompare(String(b.code), 'ru'))
        .map((year) => `<option value="${year.code}">${year.code}</option>`)
        .join('');
    select.value = effective;
    select.addEventListener('change', () => {
        setStoredAcademicYear(select.value);
        window.location.reload();
    });
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
    const navItems = navItemsForPath(window.location.pathname);
    document.querySelectorAll('.page-nav').forEach((nav) => {
        nav.innerHTML = '';
        const homeLink = document.createElement('a');
        homeLink.className = 'nav-link nav-home-link';
        homeLink.href = '/';
        homeLink.title = 'Главная';
        homeLink.setAttribute('aria-label', 'Главная');
        homeLink.textContent = '🏠';
        if (window.location.pathname === '/' || window.location.pathname === '/index.html') {
            homeLink.classList.add('active');
        }
        nav.appendChild(homeLink);

        navItems.forEach((tabDef) => {
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

function enrichMainMenu(currentUser) {
    const adminCard = document.querySelector('[data-admin-card]');
    if (adminCard) {
        adminCard.style.display = currentUser.admin ? '' : 'none';
    }

    const contingentCard = document.querySelector('[data-contingent-card]');
    if (contingentCard) {
        contingentCard.style.display = hasContingentAccess(currentUser) ? '' : 'none';
    }
}

(async function initAuth() {
    try {
        const currentUser = await tarificationApi('/api/auth/me');
        window.tarificationAuth = currentUser;
        window.tarificationTabPermissions = tabPermissionMap(currentUser);
        if (isAdminPage() && !currentUser.admin) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Пользователи»');
            return;
        }
        if (isContingentPage() && !hasContingentAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Контингент»');
            return;
        }
        if (isLoadPage() && !hasLoadAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Нагрузка»');
            return;
        }
        enrichNavigation(currentUser);
        enrichMainMenu(currentUser);
        mountHeaderUser(currentUser);
        await mountAcademicYearSelector();
        insertReadonlyNotice(currentUser);
        disableEditAreas(currentUser);
        updateStickyHeaderMetrics();
        window.withAcademicYear = withAcademicYear;
        window.getStoredAcademicYear = getStoredAcademicYear;
        window.setDebugOutputEnabled = (enabled) => {
            localStorage.setItem(DEBUG_OUTPUT_STORAGE_KEY, enabled ? '1' : '0');
            if (window.tarificationAuth) {
                applyDebugOutputVisibility(window.tarificationAuth);
            }
        };
        applyDebugOutputVisibility(currentUser);
    } catch {
        window.location.href = '/login.html';
    }
})();
