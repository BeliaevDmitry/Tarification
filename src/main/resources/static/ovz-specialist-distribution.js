const distributionEsc = (value) => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const distributionDays = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница'];

const distributionUi = Object.fromEntries([
    'target','refresh','directory-open','message','direction-body','staff-body','specialist-select','staff-select','add-group','week',
    'unassigned-list','directory-dialog','directory-form','directory-id','directory-specialist','directory-employee','directory-active',
    'directory-clear','directory-body','directory-message','group-dialog','group-title','group-full-name','group-form','group-id',
    'group-weekday','group-start','group-duration','group-students','delete-group','group-message'
].map((name) => [name.replaceAll('-', '_'), document.getElementById(`distribution-${name}`)]));

let distributionOverview = { directions: [], staff: [] };
let distributionDirectory = { specialists: [], employees: [], staff: [] };
let distributionSchedule = null;

async function distributionWaitAuth() {
    for (let i = 0; i < 150 && (!window.tarificationAuth || !window.withAcademicYear); i += 1) {
        await new Promise((resolve) => setTimeout(resolve, 30));
    }
}

function distributionYearPath(path) {
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

async function distributionApi(path, options = {}) {
    const response = await fetch(distributionYearPath(path), options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) throw new Error(body?.message || body?.error || text || `HTTP ${response.status}`);
    return body;
}

function distributionOption(value, label, selected = false) {
    return `<option value="${value}" ${selected ? 'selected' : ''}>${distributionEsc(label)}</option>`;
}

function renderDistributionOverview() {
    distributionUi.direction_body.innerHTML = (distributionOverview.directions || []).length
        ? distributionOverview.directions.map((item) => `<tr><td>${distributionEsc(item.specialistName)}</td><td>${item.neededCount}</td><td>${item.assignedCount}</td><td><strong class="${item.unassignedCount ? 'distribution-count-warning' : 'distribution-count-ok'}">${item.unassignedCount}</strong></td></tr>`).join('')
        : '<tr><td colspan="4" class="muted">Нет направлений для распределения после подписанных ППк.</td></tr>';
    distributionUi.staff_body.innerHTML = (distributionOverview.staff || []).length
        ? distributionOverview.staff.map((item) => `<tr class="${item.active ? '' : 'distribution-disabled-row'}"><td>${distributionEsc(item.specialistName)}</td><td>${distributionEsc(item.employeeName)}</td><td>${distributionEsc(item.position || '—')}<br><small>ФК ${distributionEsc(item.employeeId)}${item.personnelNumber ? ` · таб. № ${distributionEsc(item.personnelNumber)}` : ''}</small></td><td>${item.assignedCount}</td><td>${item.groupCount}</td></tr>`).join('')
        : '<tr><td colspan="5" class="muted">Сотрудники ещё не привязаны к направлениям.</td></tr>';
}

function renderDistributionSelectors(keepStaffId = null) {
    const previousSpecialist = Number(distributionUi.specialist_select.value) || null;
    const specialists = distributionDirectory.specialists || [];
    distributionUi.specialist_select.innerHTML = specialists.length
        ? specialists.map((item) => distributionOption(item.id, item.name, Number(item.id) === previousSpecialist)).join('')
        : '<option value="">Нет направлений</option>';
    if (!distributionUi.specialist_select.value && specialists.length) distributionUi.specialist_select.value = specialists[0].id;
    const specialistId = Number(distributionUi.specialist_select.value) || null;
    const staff = (distributionDirectory.staff || []).filter((item) => item.active && Number(item.specialistId) === specialistId);
    distributionUi.staff_select.innerHTML = staff.length
        ? staff.map((item) => distributionOption(item.staffId, `${item.employeeName}${item.position ? ` — ${item.position}` : ''}`, Number(item.staffId) === Number(keepStaffId))).join('')
        : '<option value="">Сначала привяжите сотрудника</option>';
    if (keepStaffId && staff.some((item) => Number(item.staffId) === Number(keepStaffId))) distributionUi.staff_select.value = keepStaffId;
    distributionUi.add_group.disabled = !distributionUi.staff_select.value;
}

async function loadDistributionOverview() {
    distributionUi.message.textContent = 'Загрузка…';
    try {
        [distributionOverview, distributionDirectory] = await Promise.all([
            distributionApi('/api/ovz/specialist-distribution/overview'),
            distributionApi('/api/ovz/specialist-distribution/directory')
        ]);
        renderDistributionOverview();
        const staffId = Number(distributionUi.staff_select.value) || null;
        renderDistributionSelectors(staffId);
        await loadDistributionSchedule();
        distributionUi.message.textContent = 'Данные распределения обновлены.';
    } catch (error) {
        distributionUi.message.textContent = `Ошибка: ${error.message}`;
    }
}

async function loadDistributionSchedule() {
    const staffId = Number(distributionUi.staff_select.value) || null;
    if (!staffId) {
        distributionSchedule = null;
        renderDistributionSchedule();
        return;
    }
    try {
        distributionSchedule = await distributionApi(`/api/ovz/specialist-distribution/schedule?staffId=${staffId}`);
        renderDistributionSchedule();
    } catch (error) {
        distributionUi.message.textContent = `Ошибка расписания: ${error.message}`;
    }
}

function groupStudentNames(group) {
    return (group.students || []).map((student) => student.fullName).join(', ');
}

function renderDistributionSchedule() {
    const groups = distributionSchedule?.groups || [];
    distributionUi.week.innerHTML = distributionDays.map((day, index) => {
        const dayGroups = groups.filter((group) => Number(group.weekday) === index + 1);
        return `<section class="distribution-day"><h3>${day}</h3><div class="distribution-day-groups">${dayGroups.length
            ? dayGroups.map((group) => `<button type="button" class="distribution-group-card" data-edit-group="${group.id}">
                <span class="distribution-group-time">${String(group.startTime || '').slice(0, 5)} · ${group.durationMinutes} мин.</span>
                <strong>${distributionEsc(group.displayName)}</strong><small>${distributionEsc(groupStudentNames(group) || 'Состав не заполнен')}</small></button>`).join('')
            : '<p class="muted">Нет занятий</p>'}</div></section>`;
    }).join('');
    const unassigned = distributionSchedule?.availableStudents || [];
    distributionUi.unassigned_list.innerHTML = unassigned.length
        ? unassigned.map((student) => `<article class="distribution-student-card"><strong>${distributionEsc(student.fullName)}</strong><span>${distributionEsc(student.className || 'Без класса/группы')}</span></article>`).join('')
        : '<p class="muted">Все доступные дети распределены либо ППк ещё не подписаны.</p>';
}

function renderDistributionDirectory() {
    distributionUi.directory_specialist.innerHTML = (distributionDirectory.specialists || [])
        .map((item) => distributionOption(item.id, item.name)).join('');
    distributionUi.directory_employee.innerHTML = '<option value="">Выберите сотрудника</option>'
        + (distributionDirectory.employees || []).map((item) => distributionOption(item.id,
            `${item.fullName}${item.position ? ` — ${item.position}` : ''} — ФК ${item.id}`)).join('');
    distributionUi.directory_body.innerHTML = (distributionDirectory.staff || []).length
        ? distributionDirectory.staff.map((item) => `<tr><td>${distributionEsc(item.specialistName)}</td><td>${distributionEsc(item.employeeName)}</td><td>${distributionEsc(item.position || '—')}</td><td>${distributionEsc(item.employeeId)}</td><td>${item.active ? 'Действует' : 'Отключён'}</td><td><button type="button" class="secondary" data-edit-staff="${item.staffId}">Изменить</button></td></tr>`).join('')
        : '<tr><td colspan="6" class="muted">Привязок пока нет.</td></tr>';
}

function clearDistributionDirectoryForm() {
    distributionUi.directory_form.reset();
    distributionUi.directory_id.value = '';
    distributionUi.directory_active.checked = true;
}

function editDistributionStaff(staffId) {
    const item = (distributionDirectory.staff || []).find((staff) => Number(staff.staffId) === Number(staffId));
    if (!item) return;
    distributionUi.directory_id.value = item.staffId;
    distributionUi.directory_specialist.value = item.specialistId;
    distributionUi.directory_employee.value = item.employeeId;
    distributionUi.directory_active.checked = item.active;
}

function groupCandidateRows(group = null) {
    const byId = new Map();
    (distributionSchedule?.availableStudents || []).forEach((student) => byId.set(Number(student.studentId), student));
    (group?.students || []).forEach((student) => byId.set(Number(student.studentId), student));
    const selected = new Set((group?.students || []).map((student) => Number(student.studentId)));
    return Array.from(byId.values()).sort((left, right) => String(left.className || '').localeCompare(String(right.className || ''), 'ru', {numeric:true})
        || String(left.fullName).localeCompare(String(right.fullName), 'ru')).map((student) => `<label class="distribution-student-check">
            <input type="checkbox" data-group-student="${student.studentId}" ${selected.has(Number(student.studentId)) ? 'checked' : ''}>
            <span><strong>${distributionEsc(student.fullName)}</strong><small>${distributionEsc(student.className || 'Без класса/группы')}</small></span></label>`).join('');
}

function openDistributionGroup(groupId = null) {
    if (!distributionSchedule?.selectedStaff) return;
    const group = groupId == null ? null : (distributionSchedule.groups || []).find((item) => Number(item.id) === Number(groupId));
    distributionUi.group_form.reset();
    distributionUi.group_id.value = group?.id || '';
    distributionUi.group_title.textContent = group ? `Группа ${group.displayName}` : 'Новая группа';
    distributionUi.group_full_name.textContent = group?.fullName || `${distributionSchedule.selectedStaff.specialistName} — новая группа`;
    distributionUi.group_weekday.value = group?.weekday || 1;
    distributionUi.group_start.value = String(group?.startTime || '09:00').slice(0, 5);
    distributionUi.group_duration.value = group?.durationMinutes || 30;
    distributionUi.group_students.innerHTML = groupCandidateRows(group) || '<p class="muted">Нет доступных детей для этого направления.</p>';
    distributionUi.delete_group.style.display = group ? '' : 'none';
    distributionUi.group_message.textContent = '';
    distributionUi.group_dialog.showModal();
}

async function saveDistributionGroup() {
    const payload = {
        id: Number(distributionUi.group_id.value) || null,
        staffId: Number(distributionUi.staff_select.value),
        weekday: Number(distributionUi.group_weekday.value),
        startTime: distributionUi.group_start.value,
        durationMinutes: Number(distributionUi.group_duration.value),
        studentIds: Array.from(distributionUi.group_students.querySelectorAll('[data-group-student]:checked'))
            .map((input) => Number(input.dataset.groupStudent))
    };
    await distributionApi('/api/ovz/specialist-distribution/groups', {
        method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload)
    });
}

distributionUi.group_duration.innerHTML = Array.from({length: 11}, (_, index) => 10 + index * 5)
    .map((minutes) => distributionOption(minutes, `${minutes} минут`, minutes === 30)).join('');

distributionUi.refresh.addEventListener('click', loadDistributionOverview);
distributionUi.specialist_select.addEventListener('change', async () => { renderDistributionSelectors(); await loadDistributionSchedule(); });
distributionUi.staff_select.addEventListener('change', loadDistributionSchedule);
distributionUi.add_group.addEventListener('click', () => openDistributionGroup());
distributionUi.week.addEventListener('click', (event) => {
    const button = event.target.closest('[data-edit-group]');
    if (button) openDistributionGroup(button.dataset.editGroup);
});
document.querySelectorAll('[data-close-dialog]').forEach((button) => button.addEventListener('click', () => {
    distributionUi[`${button.dataset.closeDialog}_dialog`]?.close();
}));

distributionUi.directory_open.addEventListener('click', () => {
    renderDistributionDirectory(); clearDistributionDirectoryForm(); distributionUi.directory_message.textContent = '';
    distributionUi.directory_dialog.showModal();
});
distributionUi.directory_clear.addEventListener('click', clearDistributionDirectoryForm);
distributionUi.directory_body.addEventListener('click', (event) => {
    const button = event.target.closest('[data-edit-staff]');
    if (button) editDistributionStaff(button.dataset.editStaff);
});
distributionUi.directory_form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
        await distributionApi('/api/ovz/specialist-distribution/directory', {
            method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({
                id: Number(distributionUi.directory_id.value) || null,
                specialistId: Number(distributionUi.directory_specialist.value),
                employeeId: Number(distributionUi.directory_employee.value),
                active: distributionUi.directory_active.checked
            })
        });
        distributionDirectory = await distributionApi('/api/ovz/specialist-distribution/directory');
        renderDistributionDirectory(); clearDistributionDirectoryForm(); renderDistributionSelectors();
        distributionUi.directory_message.textContent = 'Привязка сохранена.';
        await loadDistributionOverview();
    } catch (error) { distributionUi.directory_message.textContent = `Ошибка: ${error.message}`; }
});

distributionUi.group_form.addEventListener('submit', async (event) => {
    event.preventDefault(); distributionUi.group_message.textContent = 'Сохраняем…';
    try {
        await saveDistributionGroup(); distributionUi.group_dialog.close(); await loadDistributionOverview();
    } catch (error) { distributionUi.group_message.textContent = `Ошибка: ${error.message}`; }
});
distributionUi.delete_group.addEventListener('click', async () => {
    const groupId = Number(distributionUi.group_id.value) || null;
    if (!groupId || !confirm('Удалить группу? Дети из неё снова станут не распределёнными.')) return;
    try {
        await distributionApi(`/api/ovz/specialist-distribution/groups/${groupId}`, {method:'DELETE'});
        distributionUi.group_dialog.close(); await loadDistributionOverview();
    } catch (error) { distributionUi.group_message.textContent = `Ошибка: ${error.message}`; }
});

(async function initDistributionPage() {
    await distributionWaitAuth();
    const studentId = Number(new URLSearchParams(window.location.search).get('studentId')) || null;
    if (studentId) {
        try {
            const student = await distributionApi(`/api/ovz/specialist-distribution/students/${studentId}`);
            distributionUi.target.textContent = `Открыто из личного дела: ${student.fullName}, ${student.className || 'без класса/группы'}.`;
        } catch { distributionUi.target.textContent = ''; }
    }
    await loadDistributionOverview();
})();
