const ewUi = {
    form: document.getElementById('educational-work-upload-form'),
    file: document.getElementById('educational-work-file'),
    result: document.getElementById('educational-work-upload-result'),
    metrics: document.getElementById('educational-work-metrics'),
    exportBtn: document.getElementById('educational-work-export-btn'),
    buildingSummaries: document.getElementById('educational-work-building-summaries'),
    submissions: document.getElementById('educational-work-submissions')
};

function ewEscape(value) {
    return String(value ?? '').replace(/[&<>"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[char]));
}

function ewCell(value) {
    const text = value === null || value === undefined || value === '' ? '—' : value;
    return `<td>${ewEscape(text)}</td>`;
}

function ewRows(rows, columns, emptyText = 'Нет данных') {
    if (!rows || rows.length === 0) {
        return `<tr><td colspan="${columns.length}" class="muted">${emptyText}</td></tr>`;
    }
    return rows.map((row) => `<tr>${columns.map((column) => ewCell(row[column])).join('')}</tr>`).join('');
}

function ewApiPath(path) {
    return typeof window.withAcademicYear === 'function' ? window.withAcademicYear(path) : path;
}

async function ewApi(path, options = {}) {
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

const EDUCATIONAL_WORK_HASH_TO_PANEL = {
    summary: 'ew-summary-panel',
    reports: 'ew-reports-panel',
    performance: 'ew-performance-panel',
    activity: 'ew-activity-panel',
    achievements: 'ew-achievements-panel',
    projects: 'ew-projects-panel',
    staff: 'ew-staff-panel',
    upload: 'ew-upload-panel'
};

const EDUCATIONAL_WORK_PANEL_TO_HASH = Object.fromEntries(
    Object.entries(EDUCATIONAL_WORK_HASH_TO_PANEL).map(([hash, panel]) => [panel, hash])
);

function activateEducationalWorkPanel(panelId, updateHash = false) {
    const target = document.getElementById(panelId) ? panelId : 'ew-summary-panel';
    document.querySelectorAll('.educational-work-tabs .tab-button').forEach((button) => {
        button.classList.toggle('active', button.dataset.panel === target);
    });
    document.querySelectorAll('[id^="ew-"][id$="-panel"]').forEach((panel) => {
        panel.hidden = panel.id !== target;
    });
    if (updateHash) {
        const hash = EDUCATIONAL_WORK_PANEL_TO_HASH[target] || 'summary';
        history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${hash}`);
    }
}

function activateEducationalWorkHash() {
    const hash = String(window.location.hash || '#summary').replace('#', '') || 'summary';
    activateEducationalWorkPanel(EDUCATIONAL_WORK_HASH_TO_PANEL[hash] || 'ew-summary-panel');
}

function bindEducationalWorkTabs() {
    document.querySelectorAll('.educational-work-tabs .tab-button').forEach((button) => {
        button.addEventListener('click', () => activateEducationalWorkPanel(button.dataset.panel, true));
    });
    window.addEventListener('hashchange', activateEducationalWorkHash);
    activateEducationalWorkHash();
}

function renderUploadResult(result) {
    if (result.accepted) {
        ewUi.result.innerHTML = `
            <div class="success-message">✓ Отчёт принят: ${ewEscape(result.report?.schoolClass)} — ${ewEscape(result.report?.teacherFullName)}</div>
            <p class="muted">Данные из отчёта добавлены в таблицы ниже.</p>`;
        return;
    }
    const issues = result.issues || [];
    ewUi.result.innerHTML = `
        <div class="error-message">Отчёт не принят. Исправьте замечания и загрузите файл заново.</div>
        <div class="issue-list">
            ${issues.map((issue) => `
                <div class="issue-item">
                    <strong>${ewEscape(issue.location || 'Файл')}</strong><br>
                    ${ewEscape(issue.message || 'Ошибка проверки')}<br>
                    <span class="muted">Ожидается:</span> <code>${ewEscape(issue.expected || '—')}</code><br>
                    <span class="muted">Получено:</span> <code>${ewEscape(issue.actual || 'пусто')}</code>
                </div>`).join('')}
        </div>`;
}

async function submitEducationalWorkReport(event) {
    event.preventDefault();
    if (!ewUi.file.files.length) return;
    ewUi.result.innerHTML = '<p class="muted">Проверяем файл…</p>';
    const data = new FormData();
    data.append('file', ewUi.file.files[0]);
    try {
        const result = await ewApi(ewApiPath('/api/educational-work/reports/submit'), { method: 'POST', body: data });
        renderUploadResult(result);
        if (result.accepted) await loadEducationalWorkSummary();
    } catch (error) {
        ewUi.result.innerHTML = `<div class="error-message">${ewEscape(error.message)}</div>`;
    }
}

function matrixHtml(matrix) {
    const rows = matrix || [];
    const letters = rows.length ? rows[0].letters.map((cell) => cell.schoolClass.replace(/\d+/g, '')) : [];
    if (letters.length === 0) {
        return '<p class="muted">В этом СП нет классов за выбранный учебный год.</p>';
    }
    const body = rows.map((row) => `<tr><th>${row.parallel}</th>${row.letters.map((cell) => {
        if (cell.status === 'CLASS_NOT_EXISTS') return '<td class="summary-empty" title="Класса нет"></td>';
        return cell.status === 'SUBMITTED'
            ? `<td class="status-ok" title="${ewEscape(cell.schoolClass)}: отчёт сдан">✓</td>`
            : `<td class="status-error" title="${ewEscape(cell.schoolClass)}: отчёт не сдан">✕</td>`;
    }).join('')}</tr>`).join('');
    return `<div class="table-wrapper"><table class="data-table educational-work-summary-table"><thead><tr><th>Класс / литера</th>${letters.map((letter) => `<th>${ewEscape(letter)}</th>`).join('')}</tr></thead><tbody>${body}</tbody></table></div>`;
}

function renderBuildingSummaries(summary) {
    const buildingSummaries = summary.buildingSummaries?.length
        ? summary.buildingSummaries
        : [{ numberSchoolBuilding: 'Все СП', matrix: summary.matrix || [] }];
    ewUi.buildingSummaries.innerHTML = buildingSummaries.map((building) => `
        <section class="building-summary-card">
            <h3>${ewEscape(building.numberSchoolBuilding || 'Без СП')}</h3>
            ${matrixHtml(building.matrix)}
        </section>`).join('') || '<p class="muted">В справочнике классов пока нет строк за выбранный учебный год.</p>';
}

function renderMetrics(aggregate) {
    const metrics = [
        ['Сдано отчётов', `${aggregate.reportsSubmitted} / ${aggregate.expectedReports}`],
        ['Учащихся', aggregate.studentCount],
        ['ГТО', aggregate.gto],
        ['Движение Первых', aggregate.movementFirst],
        ['Волонтёры', aggregate.volunteers],
        ['Совет обучающихся', aggregate.studentCouncil],
        ['Достижения', aggregate.studentAchievements],
        ['Проектные строки', aggregate.specialProjectRows]
    ];
    ewUi.metrics.innerHTML = metrics.map(([label, value]) => `<div class="stat-card"><span>${ewEscape(label)}</span><strong>${ewEscape(value)}</strong></div>`).join('');
}

function renderSubmissions(rows) {
    ewUi.submissions.innerHTML = (rows || []).map((row) => `<tr>
        <td>${row.number}</td>
        <td>${ewEscape(row.schoolClass)}</td>
        <td>${ewEscape(row.classTeacherFullName)}</td>
        <td>${row.submitted ? '<span class="status-ok">Сдан</span>' : '<span class="status-error">Не сдан</span>'}</td>
        <td>${row.submitted ? `<a class="download-btn" href="${ewEscape(row.downloadUrl)}">Скачать</a>` : '<span class="muted">—</span>'}</td>
    </tr>`).join('') || '<tr><td colspan="5" class="muted">В справочнике классов пока нет строк за выбранный учебный год.</td></tr>';
}

function renderReportTables(tables) {
    document.getElementById('ew-performance').innerHTML = ewRows(tables.performance, ['schoolClass', 'classTeacherFullName', 'studentCount', 'grade5', 'grade4And5', 'oneGrade3', 'grade3And4', 'failing']);
    document.getElementById('ew-debts').innerHTML = ewRows(tables.academicDebts, ['schoolClass', 'studentName', 'trimester1', 'trimester2', 'trimester3', 'finalResult']);
    document.getElementById('ew-additional').innerHTML = ewRows(tables.additionalEducation, ['schoolClass', 'insideCount', 'insidePercent', 'outsideCount', 'outsidePercent', 'noAdditionalEducationCount']);
    document.getElementById('ew-activity').innerHTML = ewRows(tables.activity, ['schoolClass', 'gto', 'movementFirst', 'volunteers', 'studentCouncil']);
    document.getElementById('ew-achievements').innerHTML = ewRows(tables.studentAchievements, ['schoolClass', 'level', 'project', 'nomination', 'responsibleTeacher', 'participants', 'prizeWinners', 'winners']);
    document.getElementById('ew-projects').innerHTML = ewRows(tables.specialProjects, ['schoolClass', 'project', 'classTeacher', 'nominationOrFormat', 'students', 'result']);
    document.getElementById('ew-portfolio').innerHTML = ewRows(tables.teacherPortfolio, ['schoolClass', 'professionalCompetitions', 'experienceSharing', 'publications', 'professionalDevelopment']);
    document.getElementById('ew-recognitions').innerHTML = ewRows(tables.staffRecognitions, ['schoolClass', 'fullName', 'category', 'awards']);
    document.getElementById('ew-diagnostics').innerHTML = ewRows(tables.diagnostics, ['schoolClass', 'name', 'result', 'date', 'published']);
}

async function loadEducationalWorkSummary() {
    try {
        const summary = await ewApi(ewApiPath('/api/educational-work/summary'));
        renderMetrics(summary.aggregate);
        renderBuildingSummaries(summary);
        renderSubmissions(summary.submissions);
        renderReportTables(summary.tables);
    } catch (error) {
        ewUi.result.innerHTML = `<div class="error-message">Не удалось загрузить сводку: ${ewEscape(error.message)}</div>`;
    }
}

function exportEducationalWorkIndicators() {
    window.location.href = ewApiPath('/api/educational-work/indicators/export');
}

bindEducationalWorkTabs();
ewUi.form?.addEventListener('submit', submitEducationalWorkReport);
ewUi.exportBtn?.addEventListener('click', exportEducationalWorkIndicators);
loadEducationalWorkSummary();
