const TAB_PATHS = {
    '/': null,
    '/index.html': null,
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
    { path: '/teachers.html', tab: 'TEACHERS', label: 'Педагоги' }
];
const ACADEMIC_YEAR_STORAGE_KEY = 'tarification.selectedAcademicYear';

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

function selectedAcademicYear() {
    return localStorage.getItem(ACADEMIC_YEAR_STORAGE_KEY) || '';
}

function setSelectedAcademicYear(value) {
    localStorage.setItem(ACADEMIC_YEAR_STORAGE_KEY, String(value || '').trim());
}

function tabPermissionMap(currentUser) {
    return Object.fromEntries((currentUser.tabPermissions || []).map((permission) => [permission.tab, permission]));
}

function currentTab() {
    return TAB_PATHS[window.location.pathname] || null;
}

function isAdminPage() {
    return window.location.pathname === '/admin.html';
}

function showAccessDenied() {
    const container = document.querySelector('main.container');
    if (!container) return;
    container.innerHTML = `
        <section class="card access-denied-card">
            <h1>⛔ Доступ запрещён</h1>
            <p class="muted">У вас нет прав для доступа к разделу «Пользователи».</p>
            <a class="nav-link" href="/index.html">Вернуться в главное меню</a>
        </section>`;
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
            <a class="home-link" href="/index.html" title="Главное меню" aria-label="Главное меню">🏠</a>
            <select id="academic-year-select" class="academic-year-select" title="Учебный год"></select>
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

let unsavedChanges = false;
let unsavedBaseline = new Map();
let unsavedTrackCounter = 0;

function isLoadPage() {
    return window.location.pathname === '/load.html';
}

function hasPendingLoadSave() {
    if (!isLoadPage()) return false;
    const saveBtn = document.getElementById('save-building-btn');
    return Boolean(saveBtn && !saveBtn.disabled && saveBtn.classList.contains('dirty-save'));
}

function shouldWarnAboutUnsavedChanges() {
    return hasPendingLoadSave();
}

function trackableEditElements() {
    return Array.from(document.querySelectorAll('[data-requires-edit] input, [data-requires-edit] select, [data-requires-edit] textarea'))
        .filter((el) => {
            if (el.dataset.allowReadonly === 'true') return false;
            if (el.dataset.unsavedIgnore === 'true') return false;
            if (el.disabled) return false;
            if (el.tagName === 'INPUT') {
                const type = String(el.type || '').toLowerCase();
                if (['button', 'submit', 'reset', 'hidden'].includes(type)) return false;
            }
            return true;
        });
}

function ensureUnsavedTrackId(el) {
    if (el.dataset.unsavedTrackId) return el.dataset.unsavedTrackId;
    unsavedTrackCounter += 1;
    el.dataset.unsavedTrackId = `u${unsavedTrackCounter}`;
    return el.dataset.unsavedTrackId;
}

function normalizeTrackedValue(el) {
    if (el.type === 'checkbox' || el.type === 'radio') {
        return el.checked ? '1' : '0';
    }
    if (el.type === 'file') {
        return String(el.files?.length || 0);
    }
    return String(el.value ?? '');
}

function captureUnsavedBaseline() {
    const nextBaseline = new Map();
    trackableEditElements().forEach((el) => {
        const id = ensureUnsavedTrackId(el);
        nextBaseline.set(id, normalizeTrackedValue(el));
    });
    unsavedBaseline = nextBaseline;
    unsavedChanges = false;
}

function computeUnsavedChanges() {
    const current = trackableEditElements();
    if (current.length !== unsavedBaseline.size) {
        return true;
    }
    return current.some((el) => {
        const id = ensureUnsavedTrackId(el);
        return unsavedBaseline.get(id) !== normalizeTrackedValue(el);
    });
}

function setupUnsavedChangesGuards() {
    if (!isLoadPage()) {
        window.markUnsavedChangesCommitted = () => {};
        return;
    }

    const syncUnsavedState = (event) => {
        if (!event.target.closest('[data-requires-edit]')) return;
        unsavedChanges = computeUnsavedChanges();
    };

    document.addEventListener('input', syncUnsavedState, { passive: true });
    document.addEventListener('change', syncUnsavedState, { passive: true });
    document.addEventListener('reset', () => {
        setTimeout(() => {
            unsavedChanges = computeUnsavedChanges();
        }, 0);
    }, { passive: true });

    window.addEventListener('beforeunload', (event) => {
        if (!shouldWarnAboutUnsavedChanges()) return;
        event.preventDefault();
        event.returnValue = '';
    });

    window.markUnsavedChangesCommitted = () => captureUnsavedBaseline();

    const observer = new MutationObserver(() => {
        if (shouldWarnAboutUnsavedChanges()) return;
        captureUnsavedBaseline();
    });
    observer.observe(document.body, { childList: true, subtree: true });

    setTimeout(() => captureUnsavedBaseline(), 0);
}

function patchFetchWithAcademicYear() {
    if (window.__academicYearFetchPatched) return;
    const originalFetch = window.fetch.bind(window);
    window.fetch = (input, init = {}) => {
        const urlString = typeof input === 'string' ? input : String(input?.url || '');
        const isApi = urlString.startsWith('/api/');
        if (!isApi) return originalFetch(input, init);
        const url = new URL(urlString, window.location.origin);
        const year = selectedAcademicYear();
        if (year && !url.searchParams.has('academicYear')) {
            url.searchParams.set('academicYear', year);
        }
        return originalFetch(url.pathname + url.search + url.hash, init);
    };
    window.__academicYearFetchPatched = true;
}

async function mountAcademicYearSelector(currentUser) {
    const select = document.getElementById('academic-year-select');
    if (!select) return;
    const payload = await tarificationApi('/api/academic-years');
    const years = payload?.years || [];
    const currentYear = payload?.currentAcademicYear || '';
    const stored = selectedAcademicYear();
    const effective = years.some((y) => y.name === stored) ? stored : currentYear;
    setSelectedAcademicYear(effective);
    select.innerHTML = years.map((row) => `<option value="${row.name}">${row.name}</option>`).join('');
    select.value = effective;
    select.addEventListener('change', (event) => {
        if (shouldWarnAboutUnsavedChanges() && !window.confirm('Есть несохранённые изменения. Переключить учебный год?')) {
            event.target.value = selectedAcademicYear();
            return;
        }
        setSelectedAcademicYear(event.target.value);
        unsavedChanges = false;
        window.location.reload();
    });
    if (!currentUser.admin && !currentUser.canEditAllAcademicYears && effective !== currentYear) {
        document.body.classList.add('readonly-year');
    }
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

function enrichMainMenu(currentUser) {
    const adminCard = document.querySelector('[data-admin-card]');
    if (adminCard) {
        adminCard.style.display = currentUser.admin ? '' : 'none';
    }
}

(async function initAuth() {
    try {
        const currentUser = await tarificationApi('/api/auth/me');
        window.tarificationAuth = currentUser;
        window.tarificationTabPermissions = tabPermissionMap(currentUser);
        patchFetchWithAcademicYear();
        setupUnsavedChangesGuards();
        if (isAdminPage() && !currentUser.admin) {
            mountHeaderUser(currentUser);
            showAccessDenied();
            return;
        }
        enrichNavigation(currentUser);
        enrichMainMenu(currentUser);
        mountHeaderUser(currentUser);
        await mountAcademicYearSelector(currentUser);
        insertReadonlyNotice(currentUser);
        disableEditAreas(currentUser);
        updateStickyHeaderMetrics();
    } catch {
        window.location.href = '/login.html';
    }
})();
