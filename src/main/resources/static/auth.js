const TAB_PATHS = {
    '/': null,
    '/index.html': null,
    '/buildings.html': 'BUILDINGS',
    '/classes.html': 'CLASSES',
    '/subjects.html': 'SUBJECTS',
    '/curriculum.html': 'CURRICULUM',
    '/load.html': 'LOAD',
    '/load-orders.html': 'LOAD',
    '/people-load.html': 'PEOPLE_LOAD',
    '/rates.html': 'LOAD_SALARY',
    '/load-issues.html': 'LOAD_ISSUES',
    '/master-fot.html': 'LOAD_MASTER_FOT',
    '/load-statistics.html': 'LOAD_STATS',
    '/service-notes.html': 'SERVICE_NOTES',
    '/settings.html': 'SETTINGS',
    '/teachers.html': 'TEACHERS',
    '/teachers-notification.html': 'HR_NOTIFICATIONS_VIEW',
    '/contingent.html': 'CONTINGENT_STATS',
    '/ovz.html': 'OVZ',
    '/ovz-specialist-distribution.html': 'OVZ',
    '/ovz-specialists.html': 'OVZ',
    '/educational-work.html': 'EDUCATIONAL_WORK',
    '/class-teacher.html': 'CLASS_TEACHER_EXIT_ORDER_CREATE',
    '/documents.html': null,
    '/pedagogical-councils.html': 'DOCUMENTS_PEDAGOGICAL_COUNCILS',
    '/probe-orders.html': 'DOCUMENTS_PROBE_ORDERS',
    '/exit-orders.html': 'DOCUMENTS_EXIT_ORDERS',
    '/exit-orders-summary.html': 'DOCUMENTS_EXIT_ORDERS',
    '/exit-order-settings.html': 'DOCUMENTS_EXIT_ORDERS',
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

const NAV_SECTIONS = {
    employees: {
        title: 'Сотрудники',
        items: [
            { group: 'Реестр', path: '/teachers.html', tab: 'TEACHERS', label: 'Сотрудники' },
            { group: 'Реестр', path: '/teachers.html#archive', tab: 'TEACHERS_ARCHIVE', label: 'Архив' },
            { group: 'Реестр', path: '/teachers.html#dismissals', tab: 'TEACHERS_DISMISSALS', label: 'Увольнения' },
            { group: 'Кадровые процессы', path: '/teachers.html#time-off', tab: 'TEACHERS_TIME_OFF', label: 'Отгулы' },
            { group: 'Кадровые процессы', path: '/teachers-notification.html', tabs: ['HR_DOCUMENTS', 'HR_PERSONAL_DATA', 'HR_NOTIFICATIONS_VIEW'], label: 'Кадровые документы' }
        ]
    },
    load: {
        title: 'Учебный план и нагрузка',
        items: [
            { group: 'Справочники', path: '/buildings.html', tab: 'BUILDINGS', label: 'Корпуса' },
            { group: 'Справочники', path: '/classes.html', tab: 'CLASSES', label: 'Классы' },
            { group: 'Справочники', path: '/subjects.html', tab: 'SUBJECTS', label: 'Предметы' },
            { group: 'Справочники', path: '/subject-areas.html', tab: 'SUBJECT_AREAS', label: 'Предметные области' },
            { group: 'Планирование', path: '/curriculum.html', tab: 'CURRICULUM', label: 'Учебный план' },
            { group: 'Планирование', path: '/load.html', tab: 'LOAD', label: 'Нагрузка по корпусам' },
            { group: 'Планирование', path: '/people-load.html', tab: 'PEOPLE_LOAD', label: 'Нагрузка по педагогам' },
            { group: 'Изменения нагрузки', path: '/load-orders.html', tab: 'LOAD', label: 'Приказы нагрузки' },
            { group: 'Изменения нагрузки', path: '/service-notes.html', tab: 'SERVICE_NOTES', label: 'Служебные записки' },
            { group: 'Расчёт и ФОТ', path: '/teachers.html#settings', tab: 'TEACHERS_SETTINGS', label: 'Правила расчёта ЗП' },
            { group: 'Расчёт и ФОТ', path: '/rates.html', tab: 'LOAD_SALARY', label: 'Ставки' },
            { group: 'Расчёт и ФОТ', path: '/master-fot.html', tab: 'LOAD_MASTER_FOT', label: 'Мастер ФОТ' },
            { group: 'Контроль', path: '/load-issues.html', tab: 'LOAD_ISSUES', label: 'Возможные ошибки' },
            { group: 'Контроль', path: '/load-statistics.html', tab: 'LOAD_STATS', label: 'Статистика' },
            { group: 'Контроль', path: '/settings.html', tab: 'SETTINGS', label: 'Настройки нагрузки' }
        ]
    },
    students: {
        title: 'Обучающиеся',
        items: [
            { group: 'Контингент', path: '/contingent.html#stats', tab: 'CONTINGENT_STATS', label: 'Численность и списки' },
            { group: 'Контингент', path: '/contingent.html#admissions', tab: 'CONTINGENT_ADMISSION', label: 'Приём' },
            { group: 'Контингент', path: '/contingent.html#transfers', tab: 'CONTINGENT_CLASS_TRANSFERS', label: 'Перевод между классами' },
            { group: 'Контингент', path: '/contingent.html#roles', tab: 'CONTINGENT_ADMISSION_ROLES', label: 'Роли приёма' },
            { group: 'Обмен данными', path: '/contingent.html#import', tab: 'CONTINGENT_IMPORT', label: 'Импорт' },
            { group: 'Обмен данными', path: '/contingent.html#manual', tab: 'CONTINGENT_STATS', label: 'Ручная правка' },
            { group: 'Обмен данными', path: '/contingent.html#mismatches', tab: 'CONTINGENT_IMPORT', label: 'Нестыковки импорта' },
            { group: 'ОВЗ и сопровождение', path: '/ovz.html', tab: 'OVZ', label: 'Реестр' },
            { group: 'ОВЗ и сопровождение', path: '/ovz.html#certificates', tab: 'OVZ', label: 'Справки МСЭ и ЦМПК' },
            { group: 'ОВЗ и сопровождение', path: '/ovz.html#nosologies', tab: 'OVZ', label: 'Нозологии' },
            { group: 'ОВЗ и сопровождение', path: '/ovz.html#ppk', tab: 'OVZ', label: 'ППк' },
            { group: 'ОВЗ и сопровождение', path: '/ovz-specialist-distribution.html', tab: 'OVZ', label: 'Распределение' },
            { group: 'ОВЗ и сопровождение', path: '/ovz-specialists.html', tab: 'OVZ', label: 'Специалисты' }
        ]
    },
    quality: {
        title: 'Качество образования',
        items: [
            { group: 'Аттестация', path: '/vsoko-oge.html', tabs: ['VSOKO_VIEW', 'VSOKO_EDIT'], label: 'ОГЭ' },
            { group: 'Аттестация', path: '/vsoko-ege.html', tab: 'VSOKO_VIEW', label: 'ЕГЭ' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa.html', tab: 'VSOKO_VIEW', label: 'Обзор ПА' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-spec.html', tab: 'VSOKO_VIEW', label: 'Спецификации' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-entry.html', tab: 'VSOKO_VIEW', label: 'Входные работы' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-exit.html', tab: 'VSOKO_VIEW', label: 'Выходные работы' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-upload.html', tab: 'VSOKO_VIEW', label: 'Сдача ПА' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-folders.html', tab: 'VSOKO_VIEW', label: 'Отчёты по папкам' },
            { group: 'Промежуточная аттестация', path: '/vsoko-pa-analysis.html', tab: 'VSOKO_VIEW', label: 'Анализ работ' },
            { group: 'МЦКО обучающихся', path: '/vsoko-mcko.html', tab: 'VSOKO_MCKO', label: 'Результаты' },
            { group: 'МЦКО обучающихся', path: '/vsoko-summary.html', tab: 'VSOKO_MCKO', label: 'Свод' },
            { group: 'МЦКО обучающихся', path: '/vsoko-mcko-teachers.html', tab: 'VSOKO_MCKO', label: 'Педагоги по классам' },
            { group: 'МЦКО обучающихся', path: '/vsoko-interview.html', tab: 'VSOKO_MCKO', label: 'Собеседование' },
            { group: 'МЦКО педагогов', path: '/teachers.html#mcko', tab: 'TEACHERS_MCKO', label: 'Сертификаты' },
            { group: 'МЦКО педагогов', path: '/teachers.html#mcko-subjects', tab: 'TEACHERS_MCKO', label: 'Сопоставление предметов' }
        ]
    },
    educational: {
        title: 'Воспитательная работа',
        items: [
            { group: 'Работа с классами', path: '/educational-work.html', tab: 'EDUCATIONAL_WORK', label: 'Отчёты и показатели' }
        ]
    },
    documents: {
        title: 'Документы и мероприятия',
        items: [
            { group: 'Выходы и экскурсии', path: '/class-teacher.html#create', tab: 'CLASS_TEACHER_EXIT_ORDER_CREATE', label: 'Подать заявку' },
            { group: 'Выходы и экскурсии', path: '/class-teacher.html#summary', tab: 'CLASS_TEACHER_EXIT_ORDER_SUMMARY', label: 'Мои заявки' },
            { group: 'Выходы и экскурсии', path: '/exit-orders.html', tab: 'DOCUMENTS_EXIT_ORDERS', label: 'Согласование и приказы' },
            { group: 'Выходы и экскурсии', path: '/exit-orders-summary.html', tab: 'DOCUMENTS_EXIT_ORDERS', label: 'Общий свод' },
            { group: 'Выходы и экскурсии', path: '/exit-order-settings.html', tab: 'DOCUMENTS_EXIT_ORDERS', label: 'Настройки' },
            { group: 'Другие документы', path: '/probe-orders.html', tab: 'DOCUMENTS_PROBE_ORDERS', label: 'Приказы на пробы' },
            { group: 'Другие документы', path: '/pedagogical-councils.html', tab: 'DOCUMENTS_PEDAGOGICAL_COUNCILS', label: 'Педагогические советы' }
        ]
    },
    administration: {
        title: 'Администрирование',
        items: [{ group: 'Система', path: '/admin.html', tab: 'USERS', label: 'Пользователи и настройки' }]
    }
};

function sectionKeyForLocation(pathname, hash = '') {
    if (pathname === '/teachers.html') {
        if (hash === '#settings' || hash === '#coefficients' || hash === '#group-coefficients') return 'load';
        if (hash === '#mcko' || hash === '#mcko-subjects') return 'quality';
        return 'employees';
    }
    if (pathname === '/teachers-notification.html') return 'employees';
    if (pathname === '/service-notes.html' || ['/buildings.html', '/classes.html', '/subjects.html', '/subject-areas.html',
        '/curriculum.html', '/load.html', '/load-orders.html', '/people-load.html', '/rates.html', '/load-issues.html',
        '/master-fot.html', '/load-statistics.html', '/settings.html'].includes(pathname)) return 'load';
    if (pathname === '/contingent.html' || pathname === '/ovz.html'
        || pathname === '/ovz-specialist-distribution.html' || pathname === '/ovz-specialists.html') return 'students';
    if (pathname === '/educational-work.html') return 'educational';
    if (pathname.startsWith('/vsoko')) return 'quality';
    if (pathname === '/class-teacher.html' || pathname === '/documents.html' || pathname === '/pedagogical-councils.html'
        || pathname === '/probe-orders.html' || pathname === '/exit-orders.html'
        || pathname === '/exit-orders-summary.html' || pathname === '/exit-order-settings.html') return 'documents';
    if (pathname === '/admin.html') return 'administration';
    return null;
}

function navItemsForPath(pathname, hash = window.location.hash || '') {
    const key = sectionKeyForLocation(pathname, String(hash).toLowerCase());
    return key ? NAV_SECTIONS[key].items : [];
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

function currentUserHasRole(currentUser, role) {
    return Boolean(currentUser?.admin || (currentUser?.roles || [currentUser?.role]).filter(Boolean).includes(role));
}

function canViewNavigationItem(currentUser, item) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions[item.tab]?.canView
        || (item.tabs || []).some((tab) => permissions[tab]?.canView));
}

function firstAccessibleNavigationItem(currentUser, sectionKey) {
    return NAV_SECTIONS[sectionKey]?.items.find((item) => canViewNavigationItem(currentUser, item)) || null;
}

function currentTab() {
    if (window.location.pathname === '/class-teacher.html') {
        return String(window.location.hash || '').toLowerCase() === '#summary'
            ? 'CLASS_TEACHER_EXIT_ORDER_SUMMARY'
            : 'CLASS_TEACHER_EXIT_ORDER_CREATE';
    }
    if (window.location.pathname === '/teachers.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#archive') return 'TEACHERS_ARCHIVE';
        if (hash === '#dismissals') return 'TEACHERS_DISMISSALS';
        if (hash === '#settings' || hash === '#coefficients' || hash === '#group-coefficients') return 'TEACHERS_SETTINGS';
        if (hash === '#mcko' || hash === '#mcko-subjects') return 'TEACHERS_MCKO';
        if (hash === '#time-off' || hash === '#time-off-add') return 'TEACHERS_TIME_OFF';
        return 'TEACHERS';
    }
    if (window.location.pathname === '/contingent.html') {
        const hash = String(window.location.hash || '').toLowerCase();
        if (hash === '#import') return 'CONTINGENT_IMPORT';
        if (hash === '#admissions') return 'CONTINGENT_ADMISSION';
        if (hash === '#transfers') return 'CONTINGENT_CLASS_TRANSFERS';
        if (hash === '#roles') return 'CONTINGENT_ADMISSION_ROLES';
        if (hash === '#manual') return 'CONTINGENT_STATS';
        if (hash === '#mismatches') return 'CONTINGENT_IMPORT';
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
        || window.location.pathname === '/load-orders.html'
        || window.location.pathname === '/people-load.html'
        || window.location.pathname === '/rates.html'
        || window.location.pathname === '/load-issues.html'
        || window.location.pathname === '/master-fot.html'
        || window.location.pathname === '/load-statistics.html';
}

function isEducationalWorkPage() {
    return window.location.pathname === '/educational-work.html';
}

function isOvzPage() {
    return window.location.pathname === '/ovz.html'
        || window.location.pathname === '/ovz-specialist-distribution.html'
        || window.location.pathname === '/ovz-specialists.html';
}

function isDocumentsHubPage() {
    return window.location.pathname === '/documents.html';
}

function hasContingentAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(
        currentUserHasRole(currentUser, 'SECRETARY')
        || currentUser.admissionAccess?.canView
        || currentUser.admissionAccess?.canManageRoles
        || permissions.CONTINGENT_IMPORT?.canView
        || permissions.CONTINGENT_STATS?.canView
        || permissions.CONTINGENT_ADMISSION?.canView
        || permissions.CONTINGENT_CLASS_TRANSFERS?.canView
        || permissions.CONTINGENT_ADMISSION_ROLES?.canView
    );
}

async function loadAdmissionAccessForNavigation(currentUser) {
    if (currentUser.admin || hasContingentAccess(currentUser)) return;
    if (!['/', '/index.html', '/contingent.html'].includes(window.location.pathname)) return;
    try {
        currentUser.admissionAccess = await tarificationApi('/api/contingent/admissions/access');
    } catch {
        currentUser.admissionAccess = null;
    }
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
        || permissions.LOAD_MASTER_FOT?.canView
        || permissions.LOAD_STATS?.canView
        || permissions.SETTINGS?.canView
        || permissions.SUBJECT_AREAS?.canView
        || permissions.SERVICE_NOTES?.canView
        || permissions.TEACHERS_SETTINGS?.canView
    );
}

function hasEmployeesAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.TEACHERS?.canView
        || permissions.TEACHERS_ARCHIVE?.canView
        || permissions.TEACHERS_DISMISSALS?.canView
        || permissions.TEACHERS_TIME_OFF?.canView
        || permissions.HR_DOCUMENTS?.canView
        || permissions.HR_PERSONAL_DATA?.canView
        || permissions.HR_NOTIFICATIONS_VIEW?.canView);
}

function hasQualityAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.TEACHERS_MCKO?.canView
        || permissions.VSOKO_VIEW?.canView
        || permissions.VSOKO_EDIT?.canView
        || permissions.VSOKO_MCKO?.canView
        || Object.entries(permissions).some(([tab, permission]) => tab.startsWith('OGE_') && permission?.canView));
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
        || permissions.DOCUMENTS_EXIT_ORDERS?.canView
        || permissions.CLASS_TEACHER_EXIT_ORDER_CREATE?.canView
        || permissions.CLASS_TEACHER_EXIT_ORDER_SUMMARY?.canView
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
    if (window.location.pathname === '/class-teacher.html') {
        return Boolean(tabPermissionMap(currentUser).CLASS_TEACHER_EXIT_ORDER_CREATE?.canEdit);
    }
    // В рабочем месте ОВЗ право записи определяется назначением по ФК:
    // специалист меняет свою часть, ответственный — все части.
    if (window.location.pathname === '/ovz-specialists.html') return true;
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

function isClassTeacherPage() {
    return window.location.pathname === '/class-teacher.html';
}

function hasClassTeacherAccess(currentUser) {
    if (currentUser.admin) return true;
    const permissions = tabPermissionMap(currentUser);
    return Boolean(permissions.CLASS_TEACHER_EXIT_ORDER_CREATE?.canView
        || permissions.CLASS_TEACHER_EXIT_ORDER_SUMMARY?.canView);
}

let applicationClockBase = null;
let applicationClockSynchronizedAt = null;
let applicationClockTimer = null;

function applicationClockMillis(value) {
    const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/);
    if (!match) return null;
    return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]),
        Number(match[4]), Number(match[5]), Number(match[6] || 0));
}

function renderApplicationClock() {
    const node = document.getElementById('header-server-time');
    if (!node || applicationClockBase === null || applicationClockSynchronizedAt === null) return;
    const current = new Date(applicationClockBase + (Date.now() - applicationClockSynchronizedAt));
    const two = value => String(value).padStart(2, '0');
    node.textContent = `${two(current.getUTCDate())}.${two(current.getUTCMonth() + 1)}.${current.getUTCFullYear()} `
        + `${two(current.getUTCHours())}:${two(current.getUTCMinutes())}:${two(current.getUTCSeconds())}`;
}

async function refreshApplicationClock(value) {
    try {
        const result = value || await tarificationApi('/api/application-time');
        const parsed = applicationClockMillis(result?.currentDateTime);
        if (parsed === null) return;
        applicationClockBase = parsed;
        applicationClockSynchronizedAt = Date.now();
        renderApplicationClock();
        if (applicationClockTimer === null) applicationClockTimer = window.setInterval(renderApplicationClock, 1000);
    } catch {
        const node = document.getElementById('header-server-time');
        if (node) node.textContent = 'Время недоступно';
    }
}

window.refreshApplicationClock = refreshApplicationClock;

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
            <span class="header-server-time" id="header-server-time" title="Дата и время приложения · Москва">Загрузка…</span>
            <label class="header-year-select-wrap">
                <select id="academic-year-select"></select>
            </label>
            <button type="button" class="header-user-badge" id="profile-btn"></button>
            <button type="button" id="logout-btn">Выйти</button>`;
        titleRow.appendChild(controls);
    }

    refreshApplicationClock();

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
    const roleNames = (currentUser.roleDisplayNames || [currentUser.roleDisplayName || currentUser.role]).filter(Boolean).join(', ') || '—';
    overlay.innerHTML = `
        <div class="password-modal card profile-modal-card">
            <h3>Личный кабинет</h3>
            <div class="profile-grid">
                <div><span class="muted">ФИО:</span> ${currentUser.fullName || '—'}</div>
                <div><span class="muted">Логин:</span> ${currentUser.username || '—'}</div>
                <div><span class="muted">Роли:</span> ${roleNames}</div>
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
    const sectionKey = sectionKeyForLocation(window.location.pathname, String(window.location.hash || '').toLowerCase());
    if (!sectionKey) return;
    const section = NAV_SECTIONS[sectionKey];
    const navItems = navItemsForPath(window.location.pathname)
        .filter((item) => canViewNavigationItem(currentUser, item));

    document.querySelectorAll('.page-nav').forEach((nav) => {
        nav.hidden = true;
        nav.setAttribute('aria-hidden', 'true');
    });

    const main = document.querySelector('main.container');
    const header = main?.querySelector(':scope > header.card');
    if (!main || !header || !navItems.length) return;

    let layout = main.querySelector(':scope > .app-workspace-layout');
    let sidebar;
    if (!layout) {
        layout = document.createElement('div');
        layout.className = 'app-workspace-layout';
        sidebar = document.createElement('aside');
        sidebar.className = 'app-section-sidebar card';
        const content = document.createElement('div');
        content.className = 'app-workspace-content';
        Array.from(main.children)
            .filter((child) => child !== header && child !== layout)
            .forEach((child) => content.appendChild(child));
        layout.append(sidebar, content);
        main.appendChild(layout);
    } else {
        sidebar = layout.querySelector('.app-section-sidebar');
    }

    document.body.classList.add('app-has-sidebar');
    sidebar.innerHTML = '';
    const homeLink = document.createElement('a');
    homeLink.className = 'app-sidebar-home';
    homeLink.href = '/';
    homeLink.textContent = '← Рабочий стол';
    sidebar.appendChild(homeLink);

    const title = document.createElement('h2');
    title.textContent = section.title;
    sidebar.appendChild(title);

    if (document.body.classList.contains('load-page')) {
        const compactKey = 'tarification.load.sidebarCollapsed';
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'app-sidebar-toggle';
        const saved = localStorage.getItem(compactKey);
        const initiallyCollapsed = saved === '1' || (saved == null && window.innerWidth < 1280);
        const setCollapsed = (collapsed) => {
            layout.classList.toggle('app-sidebar-collapsed', collapsed);
            toggle.textContent = collapsed ? '»' : '«';
            toggle.title = collapsed ? 'Показать меню' : 'Свернуть меню';
            toggle.setAttribute('aria-label', toggle.title);
            toggle.setAttribute('aria-expanded', String(!collapsed));
        };
        setCollapsed(initiallyCollapsed);
        toggle.addEventListener('click', () => {
            const collapsed = !layout.classList.contains('app-sidebar-collapsed');
            localStorage.setItem(compactKey, collapsed ? '1' : '0');
            setCollapsed(collapsed);
        });
        sidebar.appendChild(toggle);
    }

    const grouped = new Map();
    navItems.forEach((item) => {
        const group = item.group || section.title;
        if (!grouped.has(group)) grouped.set(group, []);
        grouped.get(group).push(item);
    });

    const currentPathWithHash = `${window.location.pathname}${String(window.location.hash || '').toLowerCase()}`;
    const hasExactHashItem = navItems.some((item) => item.path.toLowerCase() === currentPathWithHash);
    grouped.forEach((items, groupName) => {
        const group = document.createElement('section');
        group.className = 'app-sidebar-group';
        const heading = document.createElement('h3');
        heading.textContent = groupName;
        group.appendChild(heading);
        items.forEach((tabDef) => {
            const link = document.createElement('a');
            link.className = 'app-sidebar-link';
            link.href = tabDef.path;
            if (tabDef.tab) link.dataset.tab = tabDef.tab;
            link.textContent = tabDef.label;
            const tabPath = tabDef.path.toLowerCase();
            const active = tabPath.includes('#')
                ? currentPathWithHash === tabPath
                : window.location.pathname.toLowerCase() === tabPath && !hasExactHashItem;
            if (active) {
                link.classList.add('active');
                link.setAttribute('aria-current', 'page');
            }
            group.appendChild(link);
        });
        sidebar.appendChild(group);
    });

    if (!window.tarificationSidebarHashBound) {
        window.tarificationSidebarHashBound = true;
        window.addEventListener('hashchange', () => enrichNavigation(window.tarificationAuth || currentUser));
    }
}

function enrichMainMenu(currentUser) {
    const sectionAccess = {
        employees: hasEmployeesAccess(currentUser),
        load: hasLoadAccess(currentUser),
        students: hasContingentAccess(currentUser) || hasOvzAccess(currentUser),
        quality: hasQualityAccess(currentUser),
        educational: hasEducationalWorkAccess(currentUser),
        documents: hasDocumentsAccess(currentUser),
        administration: currentUser.admin
    };
    document.querySelectorAll('[data-section-card]').forEach((card) => {
        const key = card.dataset.sectionCard;
        const visible = Boolean(sectionAccess[key]);
        card.style.display = visible ? '' : 'none';
        if (!visible) return;
        const entry = firstAccessibleNavigationItem(currentUser, key);
        if (entry) card.href = entry.path;
    });

    const permissions = tabPermissionMap(currentUser);
    const pedagogicalCouncilsCard = document.querySelector('[data-pedagogical-councils-card]');
    if (pedagogicalCouncilsCard) {
        pedagogicalCouncilsCard.style.display = currentUser.admin || permissions.DOCUMENTS_PEDAGOGICAL_COUNCILS?.canView ? '' : 'none';
    }
    const probeOrdersCard = document.querySelector('[data-probe-orders-card]');
    if (probeOrdersCard) {
        probeOrdersCard.style.display = currentUser.admin || permissions.DOCUMENTS_PROBE_ORDERS?.canView ? '' : 'none';
    }
    const exitOrdersCard = document.querySelector('[data-exit-orders-card]');
    if (exitOrdersCard) {
        exitOrdersCard.style.display = currentUser.admin || permissions.DOCUMENTS_EXIT_ORDERS?.canView ? '' : 'none';
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
        await loadAdmissionAccessForNavigation(currentUser);
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
        if (isClassTeacherPage() && !hasClassTeacherAccess(currentUser)) {
            mountHeaderUser(currentUser);
            showAccessDenied('разделу «Классный руководитель»');
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
        window.tarificationAuthReady = true;
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
