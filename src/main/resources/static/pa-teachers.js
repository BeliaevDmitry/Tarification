(() => {
    const state = { teachers: [] };
    const ui = {};

    function initUi() {
        Object.assign(ui, {
            feedback: document.getElementById('pa-teachers-feedback'),
            body: document.getElementById('pa-teachers-body'),
            refreshBtn: document.getElementById('pa-teachers-refresh-btn'),
            subject: document.getElementById('pa-teachers-subject-filter'),
            name: document.getElementById('pa-teachers-name-filter'),
            onlyReview: document.getElementById('pa-teachers-only-review'),
            dialog: document.getElementById('pa-teacher-details-dialog'),
            closeDialog: document.getElementById('pa-teacher-details-close'),
            detailsTitle: document.getElementById('pa-teacher-details-title'),
            passport: document.getElementById('pa-teacher-passport'),
            reportsBody: document.getElementById('pa-teacher-reports-body')
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

    function asList(value) {
        return Array.isArray(value) ? value.filter(Boolean).join(', ') : (value || '—');
    }

    function dynamicLabel(status) {
        if (status === 'NOT_AVAILABLE_NO_ENTRY_EXIT_PAIR') return 'Не рассчитана: нет входной/выходной пары';
        return status || '—';
    }

    function analysisStatusLabel(status) {
        return ({ SUCCESS: 'Готово', WARNING: 'Предупреждение', ERROR: 'Ошибка', SKIPPED: 'Пропущено', NOT_ANALYZED: 'Не анализировалось' })[status] || status || '—';
    }

    function markClass(mark) {
        if (mark === null || mark === undefined) return 'pa-mark-null';
        const n = Number(mark);
        if (n >= 5) return 'pa-mark-5';
        if (n >= 4) return 'pa-mark-4';
        if (n >= 3) return 'pa-mark-3';
        return 'pa-mark-low';
    }

    function markPill(mark) {
        return `<span class="pa-mark-pill ${markClass(mark)}">${mark ?? '—'}</span>`;
    }

    function queryString() {
        const params = new URLSearchParams();
        if (ui.subject?.value?.trim()) params.set('subjectName', ui.subject.value.trim());
        if (ui.onlyReview?.checked) params.set('onlyNeedsReview', 'true');
        const qs = params.toString();
        return qs ? `?${qs}` : '';
    }

    function visibleTeachers() {
        const needle = String(ui.name?.value || '').trim().toLowerCase();
        if (!needle) return state.teachers;
        return state.teachers.filter((row) => String(row.teacherFio || '').toLowerCase().includes(needle));
    }

    async function loadTeachers() {
        if (!ui.body) return;
        ui.body.innerHTML = '<tr><td colspan="16" class="muted">Загрузка…</td></tr>';
        setFeedback('');
        try {
            state.teachers = await api(`/api/pa/analytics/teachers${queryString()}`) || [];
            renderTeachers();
            setFeedback(`Загружено педагогов: ${visibleTeachers().length}`);
        } catch (error) {
            ui.body.innerHTML = `<tr><td colspan="16" class="muted">Ошибка загрузки: ${esc(error.message)}</td></tr>`;
            setFeedback(`Ошибка: ${error.message}`, true);
        }
    }

    function renderTeachers() {
        const rows = visibleTeachers();
        if (!rows.length) {
            ui.body.innerHTML = '<tr><td colspan="16" class="muted">Нет данных педагогической сводки.</td></tr>';
            return;
        }
        ui.body.innerHTML = rows.map((row) => `<tr>
            <td>${esc(row.teacherFio)}</td>
            <td>${esc(asList(row.subjects))}</td>
            <td>${esc(asList(row.classes))}</td>
            <td>${esc(row.reportsCount ?? 0)}</td>
            <td>${esc(row.studentsWithResult ?? 0)}</td>
            <td>${fmtPercent(row.avgPercent)}</td>
            <td>${fmt(row.avgMark, 2)}</td>
            <td>${fmtPercent(row.successPercent)}</td>
            <td>${fmtPercent(row.qualityPercent)}</td>
            <td>${esc(row.problemTasksCount ?? 0)}</td>
            <td>${esc(row.problemTopicsCount ?? 0)}</td>
            <td class="${row.needsReviewCount > 0 ? 'pa-review-warning' : ''}">${esc(row.needsReviewCount ?? 0)}</td>
            <td>${fmt(row.paPerformanceScore, 2)}</td>
            <td>${markPill(row.paPerformanceMark)}</td>
            <td>${esc(dynamicLabel(row.vsokoDynamicStatus))}</td>
            <td><button type="button" data-teacher-open="${esc(row.teacherFio)}">Открыть</button></td>
        </tr>`).join('');
    }

    function infoCard(label, value) {
        return `<div class="pa-info-card"><span>${esc(label)}</span>${esc(value ?? '—')}</div>`;
    }

    async function openTeacherDetails(teacherFio) {
        setFeedback(`Загрузка деталей: ${teacherFio}…`);
        const details = await api(`/api/pa/analytics/teacher-details?teacherFio=${encodeURIComponent(teacherFio)}`);
        renderDetails(details || {}, teacherFio);
        setFeedback('');
        if (ui.dialog?.showModal) ui.dialog.showModal();
    }

    function renderDetails(details, teacherFio) {
        const summary = details.teacherSummary || {};
        const reports = details.reports || [];
        if (ui.detailsTitle) ui.detailsTitle.textContent = `Педагог: ${teacherFio}`;
        if (ui.passport) {
            ui.passport.innerHTML = [
                infoCard('ФИО', summary.teacherFio || teacherFio),
                infoCard('Предметы', asList(summary.subjects)),
                infoCard('Классы', asList(summary.classes)),
                infoCard('Количество работ', summary.reportsCount),
                infoCard('Количество учеников', summary.studentsWithResult),
                infoCard('Показатель ПА', fmt(summary.paPerformanceScore, 2)),
                infoCard('Оценка ПА', summary.paPerformanceMark),
                infoCard('Динамика ВСОКО', summary.vsokoDynamicScore == null ? 'Не рассчитана' : fmt(summary.vsokoDynamicScore, 2)),
                infoCard('Статус динамики', dynamicLabel(summary.vsokoDynamicStatus))
            ].join('');
        }
        renderReports(reports);
    }

    function renderReports(reports) {
        if (!ui.reportsBody) return;
        if (!reports.length) {
            ui.reportsBody.innerHTML = '<tr><td colspan="15" class="muted">Нет работ педагога.</td></tr>';
            return;
        }
        ui.reportsBody.innerHTML = reports.map((row) => `<tr>
            <td>${esc(row.subjectName)}</td><td>${esc(row.className)}</td><td>${esc(row.workType)}</td><td>${esc(row.workDate || '—')}</td><td>${esc(row.level)}</td>
            <td>${esc(row.studentsWithResult ?? 0)} / ${esc(row.studentsTotal ?? 0)}</td><td>${fmtPercent(row.avgPercent)}</td><td>${fmt(row.avgMark, 2)}</td>
            <td>${fmtPercent(row.successPercent)}</td><td>${fmtPercent(row.qualityPercent)}</td><td>${esc(row.problemTasksCount ?? 0)}</td><td>${esc(row.problemTopicsCount ?? 0)}</td>
            <td class="${row.needsReview ? 'pa-review-warning' : ''}">${row.needsReview ? 'Да' : 'Нет'}</td><td>${esc(analysisStatusLabel(row.analysisStatus))}</td>
            <td><button type="button" data-report-open="${esc(row.reportVersionId)}">Открыть анализ работы</button></td>
        </tr>`).join('');
    }

    function openReportJson(reportVersionId) {
        window.open(scoped(`/api/pa/analytics/reports/${encodeURIComponent(reportVersionId)}`), '_blank');
    }

    function bindEvents() {
        ui.refreshBtn?.addEventListener('click', loadTeachers);
        ui.subject?.addEventListener('input', () => delayed(loadTeachers, ui.subject));
        ui.subject?.addEventListener('change', loadTeachers);
        ui.onlyReview?.addEventListener('change', loadTeachers);
        ui.name?.addEventListener('input', () => {
            renderTeachers();
            setFeedback(`Загружено педагогов: ${visibleTeachers().length}`);
        });
        ui.body?.addEventListener('click', (event) => {
            const btn = event.target.closest('[data-teacher-open]');
            if (!btn) return;
            openTeacherDetails(btn.dataset.teacherOpen).catch((error) => setFeedback(`Ошибка: ${error.message}`, true));
        });
        ui.reportsBody?.addEventListener('click', (event) => {
            const btn = event.target.closest('[data-report-open]');
            if (!btn) return;
            openReportJson(btn.dataset.reportOpen);
        });
        ui.closeDialog?.addEventListener('click', () => ui.dialog?.close());
    }

    function delayed(fn, el) {
        clearTimeout(el._paTeachersTimer);
        el._paTeachersTimer = setTimeout(fn, 350);
    }

    document.addEventListener('DOMContentLoaded', () => {
        initUi();
        bindEvents();
        loadTeachers();
    });
})();
