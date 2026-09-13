(function () {
    const timeOffUi = {
        panel: document.getElementById('teachers-time-off-panel'),
        summaryPanel: document.getElementById('teacher-time-off-summary-panel'),
        addPanel: document.getElementById('teacher-time-off-add-panel'),
        summaryBody: document.getElementById('teacher-time-off-summary-body'),
        historyBody: document.getElementById('teacher-time-off-history-body'),
        summaryFeedback: document.getElementById('teacher-time-off-summary-feedback'),
        teacherOptions: document.getElementById('teacher-time-off-teachers'),
        addForm: document.getElementById('teacher-time-off-add-form'),
        addTeacher: document.getElementById('teacher-time-off-teacher'),
        addHours: document.getElementById('teacher-time-off-hours'),
        addMinutes: document.getElementById('teacher-time-off-minutes'),
        addReason: document.getElementById('teacher-time-off-reason'),
        addDate: document.getElementById('teacher-time-off-date'),
        addFeedback: document.getElementById('teacher-time-off-add-feedback'),
        useButton: document.getElementById('teacher-time-off-use-button'),
        useDialog: document.getElementById('teacher-time-off-use-dialog'),
        useForm: document.getElementById('teacher-time-off-use-form'),
        useTeacher: document.getElementById('teacher-time-off-use-teacher'),
        useBalance: document.getElementById('teacher-time-off-use-balance'),
        useDays: document.getElementById('teacher-time-off-use-days'),
        useDate: document.getElementById('teacher-time-off-use-date'),
        useFeedback: document.getElementById('teacher-time-off-use-feedback'),
        useClose: document.getElementById('teacher-time-off-use-close'),
        useCancel: document.getElementById('teacher-time-off-use-cancel')
    };

    if (!timeOffUi.panel) return;

    const state = {
        teachers: [],
        summary: [],
        entries: [],
        loaded: false
    };

    function esc(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    async function timeOffApi(path, options = {}) {
        const response = await fetch(path, options);
        const text = await response.text();
        let body = null;
        try {
            body = text ? JSON.parse(text) : null;
        } catch {
            body = text;
        }
        if (!response.ok) {
            throw new Error(body?.message || body?.error || body || `HTTP ${response.status}`);
        }
        return body;
    }

    function waitForTimeOffAuth() {
        if (window.tarificationAuth) return Promise.resolve();
        return new Promise((resolve) => {
            let attempts = 0;
            const timer = window.setInterval(() => {
                attempts += 1;
                if (window.tarificationAuth || attempts >= 60) {
                    window.clearInterval(timer);
                    resolve();
                }
            }, 50);
        });
    }

    function canEditTimeOff() {
        return Boolean(window.tarificationAuth?.admin
            || window.tarificationTabPermissions?.TEACHERS_TIME_OFF?.canEdit);
    }

    function localDateValue(date = new Date()) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function formatDate(value) {
        if (!value) return '—';
        const parts = String(value).slice(0, 10).split('-');
        return parts.length === 3 ? `${parts[2]}.${parts[1]}.${parts[0]}` : value;
    }

    function formatDateTime(value) {
        if (!value) return '—';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return new Intl.DateTimeFormat('ru-RU', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        }).format(date);
    }

    function decimal(value) {
        return Number(value).toLocaleString('ru-RU', { maximumFractionDigits: 2 });
    }

    function formatMinutes(minutes, withDays = false) {
        const safe = Math.max(0, Number(minutes) || 0);
        const hours = Math.floor(safe / 60);
        const rest = safe % 60;
        const time = rest ? `${hours} ч ${rest} мин` : `${hours} ч`;
        return withDays ? `${time} (${decimal(safe / 480)} дн.)` : time;
    }

    function teacherForValue(value) {
        const normalized = String(value || '').trim().toLocaleLowerCase('ru-RU');
        return state.teachers.find((teacher) =>
            String(teacher.fio || '').trim().toLocaleLowerCase('ru-RU') === normalized) || null;
    }

    function selectedSummary(value) {
        const teacher = teacherForValue(value);
        return teacher ? state.summary.find((row) => Number(row.teacherId) === Number(teacher.id)) : null;
    }

    function renderTeacherOptions() {
        timeOffUi.teacherOptions.innerHTML = state.teachers
            .map((teacher) => `<option value="${esc(teacher.fio)}"></option>`)
            .join('');
    }

    function renderSummary() {
        if (!state.summary.length) {
            timeOffUi.summaryBody.innerHTML = '<tr><td colspan="5" class="muted">Начислений отгулов пока нет.</td></tr>';
            return;
        }
        const canEdit = canEditTimeOff();
        timeOffUi.summaryBody.innerHTML = state.summary.map((row) => {
            const activeTeacher = state.teachers.some((teacher) => Number(teacher.id) === Number(row.teacherId));
            return `
            <tr class="${Number(row.remainingMinutes) > 0 ? '' : 'time-off-zero-balance'}">
                <td><strong>${esc(row.teacherFio)}</strong></td>
                <td>${esc(formatMinutes(row.earnedMinutes, true))}</td>
                <td>${esc(formatMinutes(row.usedMinutes, true))}</td>
                <td><strong>${esc(formatMinutes(row.remainingMinutes, true))}</strong></td>
                <td>${canEdit && activeTeacher && Number(row.remainingMinutes) > 0
                    ? `<button type="button" data-use-time-off="${esc(row.teacherId)}">Отпустить</button>`
                    : '—'}</td>
            </tr>
        `;
        }).join('');
    }

    function renderHistory() {
        if (!state.entries.length) {
            timeOffUi.historyBody.innerHTML = '<tr><td colspan="8" class="muted">История пока пуста.</td></tr>';
            return;
        }
        timeOffUi.historyBody.innerHTML = state.entries.map((entry) => {
            const earned = entry.operationType === 'EARNED';
            return `
                <tr>
                    <td>${esc(formatDate(entry.eventDate))}</td>
                    <td>${esc(entry.teacherFio)}</td>
                    <td><span class="table-badge ${earned ? 'time-off-earned' : 'time-off-used'}">${earned ? 'Начислено' : 'Использовано'}</span></td>
                    <td>${esc(earned ? formatMinutes(entry.amountMinutes) : formatMinutes(entry.amountMinutes, true))}</td>
                    <td>${esc(earned ? entry.reason : 'Отгул')}</td>
                    <td>${earned ? (entry.lessonRemoval ? 'Да' : 'Нет') : '—'}</td>
                    <td>${esc(formatDateTime(entry.createdAt))}</td>
                    <td>${esc(entry.createdByFio)}</td>
                </tr>
            `;
        }).join('');
    }

    async function loadTimeOffWorkspace() {
        if (!canViewCurrentTimeOff()) return;
        timeOffUi.summaryFeedback.textContent = 'Загружаю…';
        try {
            const [teachers, workspace] = await Promise.all([
                timeOffApi('/api/teacher-time-off/teachers'),
                timeOffApi('/api/teacher-time-off')
            ]);
            state.teachers = teachers || [];
            state.summary = workspace?.summary || [];
            state.entries = workspace?.entries || [];
            state.loaded = true;
            renderTeacherOptions();
            renderSummary();
            renderHistory();
            timeOffUi.summaryFeedback.textContent = '';
        } catch (error) {
            timeOffUi.summaryFeedback.textContent = error.message;
        }
    }

    function canViewCurrentTimeOff() {
        return Boolean(window.tarificationAuth?.admin
            || window.tarificationTabPermissions?.TEACHERS_TIME_OFF?.canView);
    }

    function timeOffSubtab() {
        return String(window.location.hash || '').toLowerCase() === '#time-off-add' ? 'add' : 'summary';
    }

    function showTimeOffSubtab() {
        const active = timeOffSubtab();
        timeOffUi.summaryPanel.style.display = active === 'summary' ? '' : 'none';
        timeOffUi.addPanel.style.display = active === 'add' ? '' : 'none';
        timeOffUi.panel.querySelectorAll('.people-load-subtabs .nav-link').forEach((link) => {
            const expected = active === 'add' ? '#time-off-add' : '#time-off';
            link.classList.toggle('active', link.getAttribute('href')?.endsWith(expected));
        });
    }

    async function submitAccrual(event) {
        event.preventDefault();
        const teacher = teacherForValue(timeOffUi.addTeacher.value);
        if (!teacher) {
            timeOffUi.addFeedback.textContent = 'Выберите ФИО из выпадающего списка.';
            return;
        }
        const hours = Number(timeOffUi.addHours.value || 0);
        const minutes = Number(timeOffUi.addMinutes.value || 0);
        const durationMinutes = hours * 60 + minutes;
        if (!Number.isInteger(hours) || durationMinutes <= 0 || durationMinutes % 5 !== 0) {
            timeOffUi.addFeedback.textContent = 'Укажите трудозатраты с шагом 5 минут.';
            return;
        }
        const removal = timeOffUi.addForm.querySelector('input[name="teacher-time-off-lesson-removal"]:checked');
        if (!removal) {
            timeOffUi.addFeedback.textContent = 'Укажите, было ли снятие с уроков.';
            return;
        }
        timeOffUi.addFeedback.textContent = 'Сохраняю…';
        try {
            await timeOffApi('/api/teacher-time-off/accruals', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    teacherId: teacher.id,
                    durationMinutes,
                    reason: timeOffUi.addReason.value.trim(),
                    lessonRemoval: removal.value === 'true',
                    eventDate: timeOffUi.addDate.value
                })
            });
            timeOffUi.addForm.reset();
            timeOffUi.addHours.value = '0';
            timeOffUi.addMinutes.value = '0';
            timeOffUi.addDate.value = localDateValue();
            timeOffUi.addFeedback.textContent = 'Начисление добавлено.';
            await loadTimeOffWorkspace();
        } catch (error) {
            timeOffUi.addFeedback.textContent = error.message;
        }
    }

    function updateUseBalance() {
        const summary = selectedSummary(timeOffUi.useTeacher.value);
        timeOffUi.useBalance.textContent = summary
            ? `Доступно: ${formatMinutes(summary.remainingMinutes, true)}`
            : 'Выберите сотрудника из списка.';
    }

    function openUseDialog(teacherId = null) {
        if (!canEditTimeOff()) return;
        const teacher = teacherId == null ? null : state.teachers.find((row) => Number(row.id) === Number(teacherId));
        timeOffUi.useTeacher.value = teacher?.fio || '';
        timeOffUi.useDays.value = '1';
        timeOffUi.useDate.value = localDateValue();
        timeOffUi.useFeedback.textContent = '';
        updateUseBalance();
        timeOffUi.useDialog.showModal();
    }

    async function submitUsage(event) {
        event.preventDefault();
        const teacher = teacherForValue(timeOffUi.useTeacher.value);
        if (!teacher) {
            timeOffUi.useFeedback.textContent = 'Выберите ФИО из выпадающего списка.';
            return;
        }
        const days = Number(timeOffUi.useDays.value);
        if (!Number.isFinite(days) || days <= 0) {
            timeOffUi.useFeedback.textContent = 'Укажите положительное количество дней.';
            return;
        }
        timeOffUi.useFeedback.textContent = 'Сохраняю…';
        try {
            await timeOffApi('/api/teacher-time-off/usages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ teacherId: teacher.id, days, eventDate: timeOffUi.useDate.value })
            });
            timeOffUi.useDialog.close();
            await loadTimeOffWorkspace();
        } catch (error) {
            timeOffUi.useFeedback.textContent = error.message;
        }
    }

    function applyEditAccess() {
        const editable = canEditTimeOff();
        timeOffUi.useButton.hidden = !editable;
        timeOffUi.addForm.querySelectorAll('input, select, textarea, button').forEach((control) => {
            control.disabled = !editable;
        });
        if (!editable) {
            timeOffUi.addFeedback.textContent = 'Вкладка доступна только для просмотра.';
        }
    }

    function bindEvents() {
        timeOffUi.addForm.addEventListener('submit', submitAccrual);
        timeOffUi.useButton.addEventListener('click', () => openUseDialog());
        timeOffUi.useForm.addEventListener('submit', submitUsage);
        timeOffUi.useClose.addEventListener('click', () => timeOffUi.useDialog.close());
        timeOffUi.useCancel.addEventListener('click', () => timeOffUi.useDialog.close());
        timeOffUi.useTeacher.addEventListener('input', updateUseBalance);
        timeOffUi.useTeacher.addEventListener('change', updateUseBalance);
        timeOffUi.summaryBody.addEventListener('click', (event) => {
            const button = event.target.closest('[data-use-time-off]');
            if (button) openUseDialog(Number(button.dataset.useTimeOff));
        });
        window.addEventListener('hashchange', async () => {
            const hash = String(window.location.hash || '').toLowerCase();
            if (hash !== '#time-off' && hash !== '#time-off-add') return;
            applyEditAccess();
            showTimeOffSubtab();
            await loadTimeOffWorkspace();
        });
    }

    async function initTimeOff() {
        await waitForTimeOffAuth();
        bindEvents();
        applyEditAccess();
        timeOffUi.addDate.value = localDateValue();
        timeOffUi.useDate.value = localDateValue();
        showTimeOffSubtab();
        const hash = String(window.location.hash || '').toLowerCase();
        if ((hash === '#time-off' || hash === '#time-off-add') && canViewCurrentTimeOff()) {
            await loadTimeOffWorkspace();
        }
    }

    initTimeOff();
})();
