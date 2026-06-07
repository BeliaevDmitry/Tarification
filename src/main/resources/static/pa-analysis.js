(() => {
    const state = { reports: [], sortKey: 'subjectName', sortDir: 'asc' };
    const ui = {};

    function initUi() {
        Object.assign(ui, {
            feedback: document.getElementById('pa-analysis-feedback'),
            body: document.getElementById('pa-analysis-reports-body'),
            refreshBtn: document.getElementById('pa-analysis-refresh-btn'),
            rebuildAllBtn: document.getElementById('pa-analysis-rebuild-all-btn'),
            subject: document.getElementById('pa-analysis-subject-filter'),
            teacher: document.getElementById('pa-analysis-teacher-filter'),
            className: document.getElementById('pa-analysis-class-filter'),
            workType: document.getElementById('pa-analysis-work-type-filter'),
            onlyProblems: document.getElementById('pa-analysis-only-problems'),
            onlyReview: document.getElementById('pa-analysis-only-review'),
            includeTechnical: document.getElementById('pa-analysis-include-technical'),
            dialog: document.getElementById('pa-analysis-details-dialog'),
            closeDialog: document.getElementById('pa-analysis-details-close'),
            detailsTitle: document.getElementById('pa-analysis-details-title'),
            detailsMessage: document.getElementById('pa-analysis-details-message'),
            passport: document.getElementById('pa-analysis-passport'),
            summary: document.getElementById('pa-analysis-summary'),
            snake: document.getElementById('pa-analysis-student-snake'),
            studentsBody: document.getElementById('pa-analysis-students-body'),
            tasksBody: document.getElementById('pa-analysis-tasks-body')
        });
    }

    function currentAcademicYear() {
        return sessionStorage.getItem('tarification.academicYear') || '';
    }

    function scoped(path) {
        if (typeof window.withAcademicYear === 'function') return window.withAcademicYear(path);
        const year = currentAcademicYear();
        if (!year || path.includes('academicYear=')) return path;
        return `${path}${path.includes('?') ? '&' : '?'}academicYear=${encodeURIComponent(year)}`;
    }

    async function api(path, options = {}) {
        const response = await fetch(scoped(path), options);
        const text = await response.text();
        let body = null;
        try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
        if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
        return body;
    }

    function setFeedback(message, error = false) {
        if (!ui.feedback) return;
        ui.feedback.textContent = message || '';
        ui.feedback.style.color = error ? '#991b1b' : '';
    }

    function esc(value) {
        return String(value ?? '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
    }

    function fmt(value, digits = 1) {
        if (value === null || value === undefined || value === '') return '—';
        const num = Number(value);
        return Number.isFinite(num) ? num.toFixed(digits) : esc(value);
    }

    function fmtPercent(value) {
        return value === null || value === undefined ? '—' : `${fmt(value, 1)}%`;
    }

    function statusLabel(status) {
        return ({
            SUCCESS: 'Готово',
            WARNING: 'Предупреждение',
            ERROR: 'Ошибка',
            SKIPPED: 'Пропущено',
            NOT_ANALYZED: 'Не анализировалось'
        })[status] || status || '—';
    }

    function statusClass(status) {
        return `pa-status-${String(status || 'NOT_ANALYZED').toLowerCase().replace('_', '-')}`;
    }

    function rowStatusLabel(status) {
        return ({
            PRESENT_WITH_RESULT: 'Есть результат',
            ABSENT: 'Отсутствовал',
            EMPTY_RESULT: 'Пустой результат',
            POSSIBLE_OTHER_SUBGROUP: 'Возможна другая подгруппа',
            INVALID_ROW: 'Некорректная строка'
        })[status] || status || '—';
    }

    function shortFio(fio) {
        const parts = String(fio || '').trim().split(/\s+/).filter(Boolean);
        if (parts.length < 2) return fio || '—';
        return `${parts[0]} ${parts[1]?.[0] || ''}.${parts[2]?.[0] ? `${parts[2][0]}.` : ''}`;
    }

    function filterQuery() {
        const params = new URLSearchParams();
        const add = (name, value) => { if (value && String(value).trim()) params.set(name, String(value).trim()); };
        add('subjectName', ui.subject?.value);
        add('teacherFio', ui.teacher?.value);
        add('className', ui.className?.value);
        add('workType', ui.workType?.value);
        if (ui.onlyProblems?.checked) params.set('onlyProblems', 'true');
        if (ui.onlyReview?.checked) params.set('onlyNeedsReview', 'true');
        if (ui.includeTechnical?.checked) params.set('includeTechnical', 'true');
        const qs = params.toString();
        return qs ? `?${qs}` : '';
    }

    async function loadReports() {
        if (!ui.body) return;
        ui.body.innerHTML = '<tr><td colspan="15" class="muted">Загрузка…</td></tr>';
        setFeedback('');
        try {
            state.reports = await api(`/api/pa/analytics/reports${filterQuery()}`) || [];
            renderReports();
            setFeedback(`Загружено отчётов: ${state.reports.length}`);
        } catch (error) {
            ui.body.innerHTML = `<tr><td colspan="15" class="muted">Ошибка загрузки: ${esc(error.message)}</td></tr>`;
            setFeedback(`Ошибка: ${error.message}`, true);
        }
    }

    function sortedReports() {
        return [...state.reports].sort((a, b) => compareValues(a?.[state.sortKey], b?.[state.sortKey]) * (state.sortDir === 'desc' ? -1 : 1));
    }

    function compareValues(a, b) {
        if (a === null || a === undefined || a === '') return b === null || b === undefined || b === '' ? 0 : 1;
        if (b === null || b === undefined || b === '') return -1;
        const an = Number(a);
        const bn = Number(b);
        if (Number.isFinite(an) && Number.isFinite(bn)) return an - bn;
        return String(a).localeCompare(String(b), 'ru', { numeric: true, sensitivity: 'base' });
    }

    function renderReports() {
        const rows = sortedReports();
        if (!rows.length) {
            ui.body.innerHTML = '<tr><td colspan="15" class="muted">Нет данных аналитики за выбранный учебный год.</td></tr>';
            return;
        }
        ui.body.innerHTML = rows.map((row) => {
            const status = row.analysisStatus || 'NOT_ANALYZED';
            const showLog = Boolean(row.reportVersionId) && (status === 'WARNING' || status === 'ERROR' || row.analysisMessage);
            const actions = row.reportVersionId ? `
                    <button type="button" data-action="open" data-id="${esc(row.reportVersionId)}">Открыть</button>
                    <button type="button" data-action="rebuild" data-id="${esc(row.reportVersionId)}">Пересчитать</button>
                    ${showLog ? `<button type="button" data-action="log" data-id="${esc(row.reportVersionId)}">Скачать лог</button>` : ''}
                    <button type="button" data-action="source" data-id="${esc(row.reportVersionId)}">Скачать исходный файл</button>` : '—';
            return `<tr>
                <td><span class="pa-status-pill ${statusClass(status)}">${esc(statusLabel(status))}</span>${row.technical ? '<br><span class="pa-technical-badge">Техническая запись</span>' : ''}</td>
                <td>${esc(row.subjectName)}</td>
                <td>${esc(row.className)}</td>
                <td>${esc(row.teacherFio)}</td>
                <td>${esc(row.workType)}</td>
                <td>${esc(row.workDate || '—')}</td>
                <td>${esc(row.studentsWithResult ?? 0)} / ${esc(row.studentsTotal ?? 0)}</td>
                <td>${fmtPercent(row.avgPercent)}</td>
                <td>${fmt(row.avgMark, 2)}</td>
                <td>${fmtPercent(row.successPercent)}</td>
                <td>${fmtPercent(row.qualityPercent)}</td>
                <td>${esc(row.problemTasksCount ?? 0)}</td>
                <td>${esc(row.problemTopicsCount ?? 0)}</td>
                <td class="${row.needsReview ? 'pa-needs-review' : ''}">${row.needsReview ? 'Да' : 'Нет'}</td>
                <td>${actions}</td>
            </tr>`;
        }).join('');
    }

    async function rebuildOne(id) {
        setFeedback(`Пересчёт отчёта ${id}…`);
        await api(`/api/pa/analytics/reports/${encodeURIComponent(id)}/rebuild`, { method: 'POST' });
        await loadReports();
        setFeedback(`Отчёт ${id} пересчитан.`);
    }

    async function rebuildAll() {
        const year = currentAcademicYear();
        if (!confirm(`Пересчитать все подходящие отчёты${year ? ` за ${year}` : ''}?`)) return;
        setFeedback('Запущен пересчёт всех отчётов…');
        const result = await api('/api/pa/analytics/rebuild', { method: 'POST' });
        await loadReports();
        const processed = result?.processed ?? result?.rebuilt ?? 0;
        const failed = result?.failed ?? 0;
        setFeedback(`Пересчёт всех отчётов завершён: обработано ${processed}, ошибок ${failed}.`, failed > 0);
    }

    function openDownload(path) {
        window.open(scoped(path), '_blank');
    }

    function infoCard(label, value) {
        return `<div class="pa-info-card"><span>${esc(label)}</span>${esc(value ?? '—')}</div>`;
    }

    async function openDetails(id) {
        setFeedback(`Загрузка деталей отчёта ${id}…`);
        const details = await api(`/api/pa/analytics/reports/${encodeURIComponent(id)}`);
        renderDetails(details || {}, id);
        setFeedback('');
        if (ui.dialog?.showModal) ui.dialog.showModal();
    }

    function renderDetails(details, id) {
        const summary = details.summary || {};
        const students = details.students || [];
        const tasks = details.tasks || [];
        if (ui.detailsTitle) ui.detailsTitle.textContent = `Детали отчёта #${id}`;
        if (ui.detailsMessage) ui.detailsMessage.textContent = summary.analysisMessage || '';
        if (ui.passport) {
            ui.passport.innerHTML = [
                infoCard('Предмет', summary.subjectName), infoCard('Класс', summary.className), infoCard('Педагог', summary.teacherFio),
                infoCard('Тип работы', summary.workType), infoCard('Дата работы', summary.workDate), infoCard('Уровень', summary.level),
                infoCard('Статус анализа', statusLabel(summary.analysisStatus)), infoCard('Сообщение', summary.analysisMessage)
            ].join('');
        }
        if (ui.summary) {
            ui.summary.innerHTML = [
                infoCard('Учеников всего', summary.studentsTotal), infoCard('С результатом', summary.studentsWithResult), infoCard('Отсутствовали', summary.studentsAbsent),
                infoCard('Пустые', summary.studentsEmpty), infoCard('Возможные чужие подгруппы', summary.possibleOtherSubgroupCount),
                infoCard('Средний процент', fmtPercent(summary.avgPercent)), infoCard('Средняя отметка', fmt(summary.avgMark, 2)),
                infoCard('Успеваемость', fmtPercent(summary.successPercent)), infoCard('Качество', fmtPercent(summary.qualityPercent)),
                infoCard('Проблемные задания', summary.problemTasksCount), infoCard('Проблемные темы', summary.problemTopicsCount),
                infoCard('Требуется проверка', summary.needsReview ? 'Да' : 'Нет')
            ].join('');
        }
        renderStudentSnake(students);
        renderStudents(students);
        renderTasks(tasks);
    }

    function studentClass(student) {
        if (student.rowStatus === 'ABSENT' || student.rowStatus === 'EMPTY_RESULT' || student.rowStatus === 'POSSIBLE_OTHER_SUBGROUP') return 'muted';
        const percent = Number(student.percent);
        if (!Number.isFinite(percent)) return 'muted';
        if (percent >= 70) return 'good';
        if (percent >= 50) return 'warn';
        return 'bad';
    }

    function renderStudentSnake(students) {
        if (!ui.snake) return;
        if (!students.length) {
            ui.snake.innerHTML = '<span class="muted">Нет строк учеников.</span>';
            return;
        }
        ui.snake.innerHTML = students.map((student) => `<div class="pa-student-chip ${studentClass(student)}">
            <strong>${esc(shortFio(student.studentFio))}</strong>
            <span>${fmtPercent(student.percent)} · ${student.mark ?? '—'} · ${esc(rowStatusLabel(student.rowStatus))}</span>
        </div>`).join('');
    }

    function renderStudents(students) {
        if (!ui.studentsBody) return;
        ui.studentsBody.innerHTML = students.length ? students.map((student) => `<tr>
            <td>${esc(student.studentFio)}</td><td>${esc(student.presenceStatus || '—')}</td><td>${esc(student.variantName || '—')}</td>
            <td>${fmt(student.totalScore, 2)}</td><td>${fmt(student.maxScore, 2)}</td><td>${fmtPercent(student.percent)}</td>
            <td>${student.mark ?? '—'}</td><td>${esc(rowStatusLabel(student.rowStatus))}</td>
        </tr>`).join('') : '<tr><td colspan="8" class="muted">Нет данных</td></tr>';
    }

    function renderTasks(tasks) {
        if (!ui.tasksBody) return;
        ui.tasksBody.innerHTML = tasks.length ? tasks.map((task) => `<tr>
            <td>${esc(task.taskNo)}</td><td>${esc(task.topic || '—')}</td><td>${esc(task.skill || '—')}</td><td>${esc(task.taskKind || '—')}</td>
            <td>${fmt(task.maxScore, 2)}</td><td>${fmt(task.avgScore, 2)}</td><td>${fmtPercent(task.avgPercent)}</td>
            <td>${esc(task.below50Count ?? 0)}</td><td>${esc(task.emptyCount ?? 0)}</td><td>${esc(task.status || '—')}</td>
        </tr>`).join('') : '<tr><td colspan="10" class="muted">Нет данных</td></tr>';
    }

    function bindEvents() {
        ui.refreshBtn?.addEventListener('click', loadReports);
        ui.rebuildAllBtn?.addEventListener('click', () => rebuildAll().catch((e) => setFeedback(`Ошибка: ${e.message}`, true)));
        [ui.subject, ui.teacher, ui.className, ui.workType, ui.onlyProblems, ui.onlyReview, ui.includeTechnical].forEach((el) => {
            el?.addEventListener('change', loadReports);
            el?.addEventListener('input', () => {
                clearTimeout(el._paAnalysisTimer);
                el._paAnalysisTimer = setTimeout(loadReports, 350);
            });
        });
        document.querySelectorAll('th[data-sort]').forEach((th) => th.addEventListener('click', () => {
            const key = th.dataset.sort;
            if (state.sortKey === key) {
                state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
            } else {
                state.sortKey = key;
                state.sortDir = 'asc';
            }
            renderReports();
        }));
        ui.body?.addEventListener('click', (event) => {
            const btn = event.target.closest('button[data-action]');
            if (!btn) return;
            const id = btn.dataset.id;
            const action = btn.dataset.action;
            if (action === 'open') openDetails(id).catch((e) => setFeedback(`Ошибка: ${e.message}`, true));
            if (action === 'rebuild') rebuildOne(id).catch((e) => setFeedback(`Ошибка: ${e.message}`, true));
            if (action === 'log') openDownload(`/api/pa/analytics/reports/${encodeURIComponent(id)}/log/download`);
            if (action === 'source') openDownload(`/api/pa/reports/${encodeURIComponent(id)}/download`);
        });
        ui.closeDialog?.addEventListener('click', () => ui.dialog?.close());
    }

    document.addEventListener('DOMContentLoaded', () => {
        initUi();
        bindEvents();
        loadReports();
    });
})();
