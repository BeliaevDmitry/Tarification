const TAB_PATHS = {
    '/': null,
    '/index.html': null,
    '/buildings.html': 'BUILDINGS',
    '/classes.html': 'CLASSES',
    '/subjects.html': 'SUBJECTS',
    '/curriculum.html': 'CURRICULUM',
    '/load.html': 'LOAD',
    '/people-load.html': 'PEOPLE_LOAD',
    '/rates.html': 'LOAD_SALARY',
    '/load-issues.html': 'LOAD_ISSUES',
    '/load-statistics.html': 'LOAD_STATS',
    '/service-notes.html': 'SERVICE_NOTES',
    '/settings.html': 'SETTINGS',
    '/teachers.html': 'TEACHERS',
    '/teachers-notification.html': 'HR_NOTIFICATIONS_VIEW',
    '/contingent.html': 'CONTINGENT_STATS',
    '/ovz.html': 'OVZ',
    '/ovz-specialist-distribution.html': 'OVZ',
    '/educational-work.html': 'EDUCATIONAL_WORK',
    '/documents.html': null,
    '/pedagogical-councils.html': 'DOCUMENTS_PEDAGOGICAL_COUNCILS',
    '/probe-orders.html': 'DOCUMENTS_PROBE_ORDERS',
    '/vsoko.html': 'VSOKO_VIEW',
    '/vsoko-oge.html': 'VSOKO_VIEW',
    '/vsoko-ege.html': 'VSOKO_VIEW',
    '/vsoko-pa.html': 'VSOKO_VIEW',
    '/vsoko-pa-spec.html': 'VSOKO_VIEW',
    '/vsoko-pa-entry.html': 'VSOKO_VIEW',
    '/vsoko-pa-exit.html': 'VSOKO_VIEW',
    '/vsoko-pa-folders.html': 'VSOKO_VIEW',
    '/vsoko-pa-analysis.html': 'VSOKO_VIEW',
    '/vsoko-pa-teachers.html': 'VSOKO_VIEW',
    '/vsoko-pa-upload.html': 'VSOKO_VIEW',
    '/vsoko-mcko.html': 'VSOKO_MCKO',
    '/vsoko-summary.html': 'VSOKO_MCKO',
    '/vsoko-interview.html': 'VSOKO_MCKO',
    '/vsoko-mcko-teachers.html': 'VSOKO_MCKO',
    '/subject-areas.html': 'SUBJECT_AREAS',
    '/admin.html': 'USERS'
};



const BRANDING_DEFAULTS = {
    appTitle: 'ГБОУ школа',
    loginTitle: 'Вход в систему',
    welcomeText: 'Выберите рабочий контур системы.',
    crestUrl: '/school-crest.png',
    fallbackCrestUrl: '/school-crest.png'
};

let brandingCache = null;

async function loadBranding() {
    if (brandingCache) return brandingCache;
    try {
        const response = await fetch('/api/public/branding');
        if (!response.ok) {
            brandingCache = BRANDING_DEFAULTS;
            return brandingCache;
        }
        const data = await response.json();
        brandingCache = { ...BRANDING_DEFAULTS, ...(data || {}) };
        return brandingCache;
    } catch {
        brandingCache = BRANDING_DEFAULTS;
        return brandingCache;
    }
}

function applyBrandingToDocument(branding) {
    const appTitle = branding?.appTitle || BRANDING_DEFAULTS.appTitle;
    const titleParts = String(document.title || '').split(' — ');
    document.title = titleParts.length > 1
        ? `${appTitle} — ${titleParts.slice(1).join(' — ')}`
        : appTitle;

    const favicon = document.querySelector('link[rel="icon"]');
    if (favicon) {
        favicon.href = branding?.crestUrl || BRANDING_DEFAULTS.crestUrl;
    }
}

const NAV_ORDER = [
    { path: '/buildings.html', tab: 'BUILDINGS', label: 'Корпуса' },
    { path: '/classes.html', tab: 'CLASSES', label: 'Классы' },
    { path: '/subjects.html', tab: 'SUBJECTS', label: 'Предметы' },
    { path: '/curriculum.html', tab: 'CURRICULUM', label: 'Учебный план' },
    { path: '/load.html', tab: 'LOAD', label: 'Нагрузка по корпусам' },
    { path: '/people-load.html', tab: 'PEOPLE_LOAD', label: 'Нагрузка по людям' },
    { path: '/rates.html', tab: 'LOAD_SALARY', label: 'Ставки' },
    { path: '/load-issues.html', tab: 'LOAD_ISSUES', label: 'Возможные ошибки' },
    { path: '/load-statistics.html', tab: 'LOAD_STATS', label: 'Статистика нагрузки' },
    { path: '/settings.html', tab: 'SETTINGS', label: 'Настройки' },
    { path: '/subject-areas.html', tab: 'SUBJECT_AREAS', label: 'Предметные области' },
    { path: '/educational-work.html', tab: 'EDUCATIONAL_WORK', label: 'Воспитательная работа' },
    { path: '/vsoko.html', tab: 'VSOKO_VIEW', label: 'ВСОКО' }
];

const PA_NAV_ORDER = [
    { path: '/vsoko-pa.html', tab: 'VSOKO_VIEW', label: '← Вернуться к ПА' },
    { path: '/vsoko-pa-spec.html', tab: 'VSOKO_VIEW', label: 'Спецификации работ' },
    { path: '/vsoko-pa-entry.html', tab: 'VSOKO_VIEW', label: 'Входные работы' },
    { path: '/vsoko-pa-exit.html', tab: 'VSOKO_VIEW', label: 'Выходные работы' },
    { path: '/vsoko-pa-folders.html', tab: 'VSOKO_VIEW', label: 'Отчёты по папкам' },
    { path: '/vsoko-pa-analysis.html', tab: 'VSOKO_VIEW', label: 'Анализ работ' },
    { path: '/vsoko-pa-teachers.html', tab: 'VSOKO_VIEW', label: 'Педагоги ВСОКО' },
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
        || pathname === '/vsoko-pa-analysis.html'
        || pathname === '/vsoko-pa-teachers.html'
        || pathname === '/vsoko-pa-upload.html';
}

function isLoadModulePage(pathname) {
    return pathname === '/buildings.html'
        || pathname === '/classes.html'
        || pathname === '/subjects.html'
        || pathname === '/curriculum.html'
        || pathname === '/load.html'
        || pathname === '/people-load.html'
        || pathname === '/rates.html'
        || pathname === '/load-issues.html'
        || pathname === '/load-statistics.html'
        || pathname === '/settings.html'
        || pathname === '/subject-areas.html';
}

function navItemsForPath(pathname) {
    if (pathname === '/ovz.html' || pathname === '/ovz-specialist-distribution.html') {
        return [
            { path: '/ovz.html', tab: 'OVZ', label: 'Реестр ОВЗ' },
            { path: '/ovz-specialist-distribution.html', tab: 'OVZ', label: 'Распределение по специалистам' }
        ];
    }
    if (pathname === '/vsoko-mcko.html' || pathname === '/vsoko-summary.html'
        || pathname === '/vsoko-interview.html' || pathname === '/vsoko-mcko-teachers.html') {
        return [
            { path: '/vsoko.html', tab: 'VSOKO_VIEW', label: '← ВСОКО' },
            { path: '/vsoko-mcko.html', tab: 'VSOKO_MCKO', label: 'МЦКО' },
            { path: '/vsoko-summary.html', tab: 'VSOKO_MCKO', label: 'Свод' },
            { path: '/vsoko-mcko-teachers.html', tab: 'VSOKO_MCKO', label: 'Педагоги по классам' },
            { path: '/vsoko-interview.html', tab: 'VSOKO_MCKO', label: 'Собеседование' }
        ];
    }
    if (pathname === '/documents.html' || pathname === '/pedagogical-councils.html' || pathname === '/probe-orders.html') {
        return [
            { path: '/documents.html', tabs: ['DOCUMENTS_PEDAGOGICAL_COUNCILS', 'DOCUMENTS_PROBE_ORDERS'], label: 'Документы' },
            { path: '/pedagogical-councils.html', tab: 'DOCUMENTS_PEDAGOGICAL_COUNCILS', label: 'Педагогические советы' },
            { path: '/probe-orders.html', tab: 'DOCUMENTS_PROBE_ORDERS', label: 'Приказы на пробы' }
        ];
    }
    if (pathname === '/educational-work.html') {
        return [];
    }
    if (pathname === '/vsoko-pa.html') {
        return PA_HUB_NAV_ORDER;
    }
    if (isPaSubPage(pathname)) {
        return PA_NAV_ORDER;
    }
    if (pathname === '/teachers.html' || pathname === '/teachers-notification.html' || pathname === '/service-notes.html') {
        return [
            { path: '/teachers.html', tab: 'TEACHERS', label: 'Персонал' },
            { path: '/teachers.html#archive', tab: 'TEACHERS_ARCHIVE', label: 'Архив' },
            { path: '/teachers.html#dismissals', tab: 'TEACHERS_DISMISSALS', label: 'Увольнения' },
            { path: '/teachers.html#settings', tab: 'TEACHERS_SETTINGS', label: 'Настройки расчёта ЗП' },
            { path: '/teachers.html#mcko', tab: 'TEACHERS_MCKO', label: 'МЦКО' },
            { path: '/service-notes.html', tab: 'SERVICE_NOTES', label: 'СЛ. записки на изменение нагрузки' },
            { path: '/teachers-notification.html', tab: 'HR_NOTIFICATIONS_VIEW', label: 'Кадровые документы' }
        ];
    }
    if (isLoadModulePage(pathname)) {
        return NAV_ORDER.filter((tabDef) => tabDef.tab !== 'VSOKO_VIEW' && tabDef.tab !== 'EDUCATIONAL_WORK');
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

function academicYearFromLocation() {
    const requested = new URLSearchParams(window.location.search).get('academicYear') || '';
    return /^\d{4}\/\d{4}$/.test(requested) ? requested : '';
}

const linkedAcademicYear = academicYearFromLocation();
if (linkedAcademicYear) {
    // Deep links must select their own year before page scripts start loading data.
    setStoredAcademicYear(linkedAcademicYear);
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
    if (window.location.pathname === '/teachers.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#archive') return 'TEACHERS_ARCHIVE';
        if (hash === '#dismissals') return 'TEACHERS_DISMISSALS';
        if (hash === '#settings') return 'TEACHERS_SETTINGS';
        if (hash === '#mcko' || hash === '#mcko-subjects') return 'TEACHERS_MCKO';
        return 'TEACHERS';
    }
    if (window.location.pathname === '/contingent.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#import') return 'CONTINGENT_IMPORT';
        if (hash === '#manual') return 'CONTINGENT_STATS';
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
        || window.location.pathname === '/people-load.html'
        || window.location.pathname === '/rates.html'
        || window.location.pathname === '/load-issues.html'
        || window.location.pathname === '/load-statistics.html';
}

function isEducationalWorkPage() {
    return window.location.pathname === '/educational-work.html';
}

function isOvzPage() {
    return window.location.pathname === '/ovz.html'
        || window.location.pathname === '/ovz-specialist-distribution.html';
}

function isDocumentsHubPage() {
    return window.location.pathname === '/documents.html';
}

function hasContingentAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.CONTINGENT_IMPORT?.canView || permissions.CONTINGENT_STATS?.canView);
}

function hasLoadAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(
        permissions.BUILDINGS?.canView
        || permissions.CLASSES?.canView
        || permissions.SUBJECTS?.canView
        || permissions.CURRICULUM?.canView
        || permissions.LOAD?.canView
        || permissions.PEOPLE_LOAD?.canView
        || permissions.LOAD_SALARY?.canView
        || permissions.LOAD_ISSUES?.canView
        || permissions.LOAD_STATS?.canView
        || permissions.SETTINGS?.canView
        || permissions.SUBJECT_AREAS?.canView
    );
}

function hasEducationalWorkAccess(currentUser) {
    if (currentUser.admin) return true;
    return Boolean(tabPermissionMap(currentUser).EDUCATIONAL_WORK?.canView);
}

function hasDocumentsAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(
        permissions.DOCUMENTS_PEDAGOGICAL_COUNCILS?.canView
        || permissions.DOCUMENTS_PROBE_ORDERS?.canView
    );
}

function hasOvzAccess(currentUser) {
    if (currentUser.admin) return true;
    return Boolean(tabPermissionMap(currentUser).OVZ?.canView);
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

function canExportCurrentPage(currentUser) {
    if (currentUser.admin) return true;
    const tab = currentTab();
    if (!tab) return false;
    return Boolean(tabPermissionMap(currentUser)[tab]?.canExport);
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
    const availableCodes = new Set((years || []).map((year) => String(year.code || '')));
    const requestedFromLink = academicYearFromLocation();
    const effective = requestedFromLink && availableCodes.has(requestedFromLink)
        ? requestedFromLink
        : currentStored || active.active;
    if (requestedFromLink && availableCodes.has(requestedFromLink)) {
        setStoredAcademicYear(requestedFromLink);
    }
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
            const canView = currentUser.admin
                || permissions[tabDef.tab]?.canView
                || (tabDef.tabs || []).some((tab) => permissions[tab]?.canView);
            if (!canView) return;
            const link = document.createElement('a');
            link.className = 'nav-link';
            link.href = tabDef.path;
            if (tabDef.tab) link.dataset.tab = tabDef.tab;
            link.textContent = tabDef.label;
            const currentPathWithHash = `${window.location.pathname}${window.location.hash || ''}`;
            const tabPath = tabDef.path;
            const active = tabPath.includes('#')
                ? currentPathWithHash === tabPath
                : window.location.pathname === tabPath && (!window.location.hash || window.location.hash === '#main');
            if (active) {
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

    const educationalWorkCard = document.querySelector('[data-educational-work-card]');
    if (educationalWorkCard) {
        educationalWorkCard.style.display = hasEducationalWorkAccess(currentUser) ? '' : 'none';
    }

    const documentsCard = document.querySelector('[data-documents-card]');
    if (documentsCard) {
        documentsCard.style.display = hasDocumentsAccess(currentUser) ? '' : 'none';
    }

    const ovzCard = document.querySelector('[data-ovz-card]');
    if (ovzCard) {
        ovzCard.style.display = hasOvzAccess(currentUser) ? '' : 'none';
    }

    const permissions = tabPermissionMap(currentUser);
    const pedagogicalCouncilsCard = document.querySelector('[data-pedagogical-councils-card]');
    if (pedagogicalCouncilsCard) {
        pedagogicalCouncilsCard.style.display = currentUser.admin || permissions.DOCUMENTS_PEDAGOGICAL_COUNCILS?.canView ? '' : 'none';
    }
    const probeOrdersCard = document.querySelector('[data-probe-orders-card]');
    if (probeOrdersCard) {
        probeOrdersCard.style.display = currentUser.admin || permissions.DOCUMENTS_PROBE_ORDERS?.canView ? '' : 'none';
    }
}

function disableExportAreas(currentUser) {
    if (canExportCurrentPage(currentUser)) return;
    document.querySelectorAll('[data-requires-export]').forEach((control) => {
        control.disabled = true;
        control.setAttribute('aria-disabled', 'true');
        control.title = 'Нет права на экспорт этой вкладки';
    });
}

(async function initAuth() {
    try {
        const currentUser = await tarificationApi('/api/auth/me');
        const branding = await loadBranding();
        applyBrandingToDocument(branding);
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
        if (isEducationalWorkPage() && !hasEducationalWorkAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Воспитательная работа»');
            return;
        }
        if (isDocumentsHubPage() && !hasDocumentsAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Документы»');
            return;
        }
        if (isOvzPage() && !hasOvzAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «ОВЗ»');
            return;
        }
        enrichNavigation(currentUser);
        enrichMainMenu(currentUser);
        mountHeaderUser(currentUser);
        await mountAcademicYearSelector();
        insertReadonlyNotice(currentUser);
        disableEditAreas(currentUser);
        disableExportAreas(currentUser);
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
