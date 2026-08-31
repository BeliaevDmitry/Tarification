let specialistsOverview = null;
let specialistsSettings = null;
let specialistsActiveStudentId = null;
let specialistsInitialStudentOpened = false;
let specialistsScope = 'mine';

const specialistsUi = {
    title: document.getElementById('specialists-workspace-title'),
    accessNote: document.getElementById('specialists-access-note'),
    settingsOpen: document.getElementById('specialists-settings-open'),
    refresh: document.getElementById('specialists-refresh'),
    showMine: document.getElementById('specialists-show-mine'),
    showAll: document.getElementById('specialists-show-all'),
    search: document.getElementById('specialists-search'),
    message: document.getElementById('specialists-message'),
    countAll: document.getElementById('specialists-count-all'),
    countComplete: document.getElementById('specialists-count-complete'),
    countIncomplete: document.getElementById('specialists-count-incomplete'),
    body: document.getElementById('specialists-children-body'),
    childDialog: document.getElementById('specialists-child-dialog'),
    childTitle: document.getElementById('specialists-child-title'),
    childSubtitle: document.getElementById('specialists-child-subtitle'),
    childEntries: document.getElementById('specialists-child-entries'),
    childMessage: document.getElementById('specialists-child-message'),
    settingsDialog: document.getElementById('specialists-settings-dialog'),
    settingsForm: document.getElementById('specialists-settings-form'),
    responsibleEmployee: document.getElementById('specialists-responsible-employee'),
    settingsMessage: document.getElementById('specialists-settings-message')
};

function specialistsEsc(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
}

async function specialistsApi(path, options = {}) {
    const url = path.includes('/settings') ? path : (window.withAcademicYear ? window.withAcademicYear(path) : path);
    const response = await fetch(url, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `Ошибка ${response.status}`);
    return body;
}

function statusMeta(status) {
    return ({
        NOT_STARTED: {label:'Не начато', css:'not-started'},
        IN_PROGRESS: {label:'Частично', css:'in-progress'},
        COMPLETED: {label:'Заполнено', css:'completed'}
    })[status] || {label:'Не начато', css:'not-started'};
}

function statusPill(status, label = null) {
    const meta = statusMeta(status);
    return `<span class="support-status support-status-${meta.css}">${specialistsEsc(label || meta.label)}</span>`;
}

async function waitForSpecialistsAuth() {
    for (let attempt = 0; attempt < 100 && !window.tarificationAuth; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 40));
    }
}

async function loadSpecialistsOverview() {
    specialistsUi.message.textContent = 'Загрузка…';
    try {
        specialistsOverview = await specialistsApi('/api/ovz/specialist-workspace');
        specialistsUi.showAll.style.display = specialistsOverview.responsible ? '' : 'none';
        if (!specialistsOverview.responsible) specialistsScope = 'mine';
        specialistsUi.settingsOpen.style.display = specialistsOverview.canManageSettings ? '' : 'none';
        const requestedStudentId = Number(new URLSearchParams(window.location.search).get('studentId')) || null;
        if (!specialistsInitialStudentOpened && requestedStudentId && specialistsOverview.responsible
                && !specialistsMineChildren().some((child) => Number(child.studentId) === requestedStudentId)) {
            specialistsScope = 'all';
        }
        renderSpecialistsChildren();
        if (!specialistsInitialStudentOpened && requestedStudentId
                && specialistsVisibleChildren().some((child) => Number(child.studentId) === requestedStudentId)) {
            specialistsInitialStudentOpened = true;
            await openSpecialistsChild(requestedStudentId);
        }
    } catch (error) {
        specialistsUi.message.textContent = `Ошибка: ${error.message}`;
        specialistsUi.body.innerHTML = '<tr><td colspan="5" class="muted">Не удалось загрузить список.</td></tr>';
    }
}

function specialistsMineChildren() {
    const teacherId = Number(specialistsOverview?.currentTeacherId) || null;
    if (!teacherId) return [];
    return (specialistsOverview?.children || []).filter((child) =>
        (child.specialists || []).some((item) => Number(item.employeeId) === teacherId));
}

function specialistsVisibleChildren() {
    return specialistsScope === 'all' && specialistsOverview?.responsible
        ? (specialistsOverview.children || [])
        : specialistsMineChildren();
}

function specialistsAggregate(statuses) {
    if (!statuses.length || statuses.every((status) => status === 'NOT_STARTED')) return 'NOT_STARTED';
    if (statuses.every((status) => status === 'COMPLETED')) return 'COMPLETED';
    return 'IN_PROGRESS';
}

function specialistsDisplayedStatus(child) {
    if (specialistsScope === 'all') return child.overallStatus;
    const teacherId = Number(specialistsOverview?.currentTeacherId) || null;
    return specialistsAggregate((child.specialists || [])
        .filter((item) => Number(item.employeeId) === teacherId)
        .map((item) => item.status));
}

function renderSpecialistsChildren() {
    const query = String(specialistsUi.search.value || '').trim().toLocaleLowerCase('ru');
    const visible = specialistsVisibleChildren();
    const rows = visible.filter((child) =>
        !query || `${child.fullName || ''} ${child.className || ''}`.toLocaleLowerCase('ru').includes(query));
    const completed = visible.filter((child) => specialistsDisplayedStatus(child) === 'COMPLETED').length;
    specialistsUi.title.textContent = specialistsScope === 'all' ? 'Все дети' : 'Мои подопечные';
    specialistsUi.accessNote.textContent = specialistsScope === 'all'
        ? `Показаны все распределённые дети. Ответственный: ${specialistsOverview?.responsibleEmployeeName || 'доступ администратора'}.`
        : specialistsOverview?.currentTeacherId
            ? `Показаны дети, закреплённые за ${specialistsOverview.currentUserName}.`
            : 'Учётная запись не связана с кадровой карточкой. Привязанных детей нет.';
    specialistsUi.showMine.classList.toggle('secondary', specialistsScope !== 'mine');
    specialistsUi.showAll.classList.toggle('secondary', specialistsScope !== 'all');
    specialistsUi.countAll.textContent = visible.length;
    specialistsUi.countComplete.textContent = completed;
    specialistsUi.countIncomplete.textContent = visible.length - completed;
    specialistsUi.message.textContent = visible.length
        ? `Детей в выбранном списке: ${visible.length}.`
        : specialistsScope === 'all' ? 'Распределённых детей пока нет.' : 'Детей, привязанных к этому аккаунту, пока нет.';
    specialistsUi.body.innerHTML = rows.length ? rows.map((child) => {
        const displayedStatus = specialistsDisplayedStatus(child);
        const own = statusMeta(displayedStatus);
        const statuses = (child.specialists || []).map((item) =>
            statusPill(item.status, `${item.specialistName}: ${item.employeeName}`)).join(' ');
        return `<tr>
            <td><strong>${specialistsEsc(child.fullName)}</strong></td>
            <td>${specialistsEsc(child.className || '—')}</td>
            <td>${statusPill(displayedStatus, specialistsScope === 'all' ? `Общий статус: ${own.label}` : own.label)}</td>
            <td><div class="specialists-status-list">${statuses || '—'}</div></td>
            <td><button type="button" data-specialists-student="${child.studentId}">Открыть</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="5" class="muted">По заданному условию дети не найдены.</td></tr>';
}

async function openSpecialistsChild(studentId) {
    specialistsActiveStudentId = Number(studentId);
    specialistsUi.childMessage.textContent = 'Загрузка…';
    specialistsUi.childEntries.innerHTML = '';
    if (!specialistsUi.childDialog.open) specialistsUi.childDialog.showModal();
    try {
        const child = await specialistsApi(`/api/ovz/specialist-workspace/students/${studentId}`);
        specialistsUi.childTitle.textContent = child.fullName || 'Сопровождение ребёнка';
        specialistsUi.childSubtitle.textContent = child.className || '—';
        specialistsUi.childEntries.innerHTML = (child.entries || []).map(entryCard).join('')
            || '<p class="muted">Ребёнок ещё не распределён за специалистами.</p>';
        specialistsUi.childMessage.textContent = '';
    } catch (error) {
        specialistsUi.childMessage.textContent = `Ошибка: ${error.message}`;
    }
}

function entryCard(entry) {
    const meta = statusMeta(entry.status);
    const disabled = entry.editable ? '' : 'disabled';
    const update = entry.updatedAt
        ? `Последнее изменение: ${new Date(entry.updatedAt).toLocaleString('ru-RU')}${entry.updatedByName ? `, ${specialistsEsc(entry.updatedByName)}` : ''}`
        : 'Сведения ещё не заполнялись.';
    return `<article class="specialists-entry-card specialists-entry-${meta.css}" data-specialist-entry="${entry.specialistId}">
        <div class="specialists-entry-heading">
            <div><h4>${specialistsEsc(entry.specialistName)}</h4><p>${specialistsEsc(entry.employeeName || '')}</p></div>
            ${statusPill(entry.status)}
        </div>
        <div class="specialists-entry-grid">
            <label>Основные дефициты ребёнка<textarea data-support-field="childDeficits" rows="5" ${disabled}>${specialistsEsc(entry.childDeficits || '')}</textarea></label>
            <label>Ресурсы ребёнка<textarea data-support-field="childResources" rows="5" ${disabled}>${specialistsEsc(entry.childResources || '')}</textarea></label>
            <label>Основные задачи развития на год<textarea data-support-field="annualTasks" rows="5" ${disabled}>${specialistsEsc(entry.annualTasks || '')}</textarea></label>
            <label>Планируемые результаты<textarea data-support-field="plannedResults" rows="5" ${disabled}>${specialistsEsc(entry.plannedResults || '')}</textarea></label>
        </div>
        <div class="specialists-entry-footer"><small class="muted">${update}</small>
            ${entry.editable ? '<button type="button" data-save-specialist-entry>Сохранить свою часть</button>' : '<span class="muted">Только просмотр</span>'}
        </div>
    </article>`;
}

async function saveSpecialistEntry(card) {
    const button = card.querySelector('[data-save-specialist-entry]');
    button.disabled = true;
    specialistsUi.childMessage.textContent = 'Сохранение…';
    const value = (name) => card.querySelector(`[data-support-field="${name}"]`)?.value || '';
    try {
        await specialistsApi(`/api/ovz/specialist-workspace/students/${specialistsActiveStudentId}/entries`, {
            method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({
                specialistId: Number(card.dataset.specialistEntry),
                childDeficits: value('childDeficits'), childResources: value('childResources'),
                annualTasks: value('annualTasks'), plannedResults: value('plannedResults')
            })
        });
        specialistsUi.childMessage.textContent = 'Сведения сохранены.';
        await loadSpecialistsOverview();
        await openSpecialistsChild(specialistsActiveStudentId);
    } catch (error) {
        specialistsUi.childMessage.textContent = `Ошибка: ${error.message}`;
        button.disabled = false;
    }
}

async function openSpecialistsSettings() {
    specialistsUi.settingsMessage.textContent = 'Загрузка…';
    specialistsUi.settingsDialog.showModal();
    try {
        specialistsSettings = await specialistsApi('/api/ovz/specialist-workspace/settings');
        specialistsUi.responsibleEmployee.innerHTML = '<option value="">Выберите сотрудника</option>'
            + (specialistsSettings.employees || []).map((employee) => `<option value="${employee.id}">${specialistsEsc(employee.fullName)} — ${specialistsEsc(employee.position || 'должность не указана')} — ФК ${specialistsEsc(employee.personnelNumber || employee.id)}</option>`).join('');
        specialistsUi.responsibleEmployee.value = String(specialistsSettings.responsibleEmployeeId || '');
        specialistsUi.settingsMessage.textContent = '';
    } catch (error) { specialistsUi.settingsMessage.textContent = `Ошибка: ${error.message}`; }
}

specialistsUi.refresh.addEventListener('click', loadSpecialistsOverview);
specialistsUi.search.addEventListener('input', renderSpecialistsChildren);
specialistsUi.showMine.addEventListener('click', () => { specialistsScope = 'mine'; renderSpecialistsChildren(); });
specialistsUi.showAll.addEventListener('click', () => { specialistsScope = 'all'; renderSpecialistsChildren(); });
specialistsUi.settingsOpen.addEventListener('click', openSpecialistsSettings);
specialistsUi.body.addEventListener('click', (event) => {
    const button = event.target.closest('[data-specialists-student]');
    if (button) openSpecialistsChild(button.dataset.specialistsStudent);
});
specialistsUi.childEntries.addEventListener('click', (event) => {
    const button = event.target.closest('[data-save-specialist-entry]');
    if (button) saveSpecialistEntry(button.closest('[data-specialist-entry]'));
});
document.querySelectorAll('[data-specialists-close]').forEach((button) => button.addEventListener('click', () => {
    (button.dataset.specialistsClose === 'child' ? specialistsUi.childDialog : specialistsUi.settingsDialog).close();
}));
specialistsUi.settingsForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const responsibleEmployeeId = Number(specialistsUi.responsibleEmployee.value) || null;
    if (!responsibleEmployeeId) { specialistsUi.settingsMessage.textContent = 'Выберите ответственного.'; return; }
    try {
        specialistsUi.settingsMessage.textContent = 'Сохранение…';
        await specialistsApi('/api/ovz/specialist-workspace/settings', {
            method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({responsibleEmployeeId})
        });
        specialistsUi.settingsMessage.textContent = 'Ответственный назначен.';
        await loadSpecialistsOverview();
    } catch (error) { specialistsUi.settingsMessage.textContent = `Ошибка: ${error.message}`; }
});

(async function initSpecialistsWorkspace() {
    await waitForSpecialistsAuth();
    await loadSpecialistsOverview();
})();
