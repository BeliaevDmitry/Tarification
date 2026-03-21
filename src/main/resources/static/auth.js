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

function disableEditAreas(canEdit) {
    if (canEdit) return;

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

function insertReadonlyNotice(canEdit) {
    if (canEdit) return;
    const header = document.querySelector('header.card');
    if (!header || header.querySelector('.readonly-note')) return;
    const note = document.createElement('p');
    note.className = 'muted readonly-note';
    note.textContent = 'У вас открыт режим просмотра: данные можно смотреть, но не редактировать.';
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
            </div>
            <div class="row auth-actions">
                ${currentUser.canEdit ? '<span class="permission-badge edit-badge">Редактирование</span>' : '<span class="permission-badge view-badge">Только просмотр</span>'}
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
    document.querySelectorAll('.page-nav').forEach((nav) => {
        if (currentUser.admin && !nav.querySelector('a[href="/admin.html"]')) {
            const link = document.createElement('a');
            link.className = 'nav-link';
            link.href = '/admin.html';
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
        mountUserBar(currentUser);
        enrichNavigation(currentUser);
        insertReadonlyNotice(currentUser.canEdit || currentUser.admin);
        disableEditAreas(currentUser.canEdit || currentUser.admin);
    } catch {
        window.location.href = '/login.html';
    }
})();
