const jsonHeaders = { 'Content-Type': 'application/json' };
const SUBJECTS = [
    'Русский язык', 'Математика', 'Физика', 'Химия', 'Информатика и ИКТ', 'Биология',
    'История', 'География', 'Английский язык', 'Немецкий язык', 'Французский язык',
    'Обществознание', 'Испанский язык', 'Литература'
];

const state = {
    students: [],
    mismatches: [],
    teachers: [],
    teacherBindings: [],
    canViewUpload: true,
    canViewMismatches: true,
    canViewExternalWorks: true,
    canViewTeacherBinding: true,
    canViewScores: true,
    canViewEvaluation: true,
    workSorts: [
        { key: 'className', dir: 'asc' },
        { key: 'fullName', dir: 'asc' }
    ]
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function scoped(path) {
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

function hasViewPermission(tab) {
    const user = window.tarificationAuth;
    if (!user) return true;
    if (user.admin) return true;
    const perms = user.tabPermissions || [];
    return perms.some(p => p.tab === tab && p.canView);
}

function hasEditPermission(tab) {
    const user = window.tarificationAuth;
    if (!user) return true;
    if (user.admin) return true;
    const perms = user.tabPermissions || [];
    return perms.some(p => p.tab === tab && p.canEdit);
}

function syncUploadButtonsAccess() {
    const user = window.tarificationAuth || {};
    const canEditVsoko = Boolean(user.admin) || hasEditPermission('VSOKO_EDIT');
    const canImportGia = state.canViewUpload && (hasEditPermission('OGE_GIA_UPLOAD') || canEditVsoko);
    const canImportWorks = hasViewPermission('OGE_WORK_UPLOAD') && (hasEditPermission('OGE_WORK_UPLOAD') || canEditVsoko);
    const canImportExternalWorks = state.canViewExternalWorks && (hasEditPermission('OGE_WORK_UPLOAD') || canEditVsoko);

    const giaFiles = document.getElementById('gia-files');
    const giaUploadBtn = document.getElementById('gia-upload-btn');
    const workFiles = document.getElementById('work-files');
    const workUploadBtn = document.getElementById('work-upload-btn');
    const externalFiles = document.getElementById('external-work-files');
    const externalUploadBtn = document.getElementById('external-work-upload-btn');

    if (giaFiles) giaFiles.disabled = !canImportGia;
    if (giaUploadBtn) giaUploadBtn.disabled = !canImportGia;
    if (workFiles) workFiles.disabled = !canImportWorks;
    if (workUploadBtn) workUploadBtn.disabled = !canImportWorks;
    if (externalFiles) externalFiles.disabled = !canImportExternalWorks;
    if (externalUploadBtn) externalUploadBtn.disabled = !canImportExternalWorks;
}

async function waitForAuthContext() {
    for (let i = 0; i < 50; i++) {
        if (window.tarificationAuth) return;
        await new Promise(resolve => setTimeout(resolve, 50));
    }
}

function applyTabVisibility() {
    state.canViewUpload = hasViewPermission('OGE_UPLOAD_VIEW');
    state.canViewMismatches = hasViewPermission('OGE_MISMATCH_VIEW');
    state.canViewExternalWorks = hasViewPermission('OGE_EXTERNAL_WORKS_VIEW');
    state.canViewTeacherBinding = hasViewPermission('OGE_TEACHER_BINDING_VIEW');
    state.canViewScores = hasViewPermission('OGE_SCORE_VIEW');
    state.canViewEvaluation = hasViewPermission('OGE_EVALUATION_VIEW');
    const uploadBtn = document.querySelector('#main-tabs button[data-tab="upload"]');
    const uploadPane = document.getElementById('tab-upload');
    const mismatchBtn = document.querySelector('#main-tabs button[data-tab="mismatches"]');
    const mismatchPane = document.getElementById('tab-mismatches');
    const externalBtn = document.querySelector('#main-tabs button[data-tab="external-works"]');
    const externalPane = document.getElementById('tab-external-works');
    const teacherBindingBtn = document.querySelector('#main-tabs button[data-tab="teacher-binding-main"]');
    const teacherBindingPane = document.getElementById('tab-teacher-binding-main');
    const scoresBtn = document.querySelector('#main-tabs button[data-tab="scores"]');
    const scoresPane = document.getElementById('tab-scores');
    const evaluationBtn = document.querySelector('#main-tabs button[data-tab="evaluation"]');
    const evaluationPane = document.getElementById('tab-evaluation');
    if (uploadBtn) uploadBtn.style.display = state.canViewUpload ? '' : 'none';
    if (uploadPane) uploadPane.style.display = state.canViewUpload ? '' : 'none';
    if (mismatchBtn) mismatchBtn.style.display = state.canViewMismatches ? '' : 'none';
    if (mismatchPane) mismatchPane.style.display = state.canViewMismatches ? '' : 'none';
    if (externalBtn) externalBtn.style.display = state.canViewExternalWorks ? '' : 'none';
    if (externalPane) externalPane.style.display = state.canViewExternalWorks ? '' : 'none';
    if (teacherBindingBtn) teacherBindingBtn.style.display = state.canViewTeacherBinding ? '' : 'none';
    if (teacherBindingPane) teacherBindingPane.style.display = state.canViewTeacherBinding ? '' : 'none';
    if (scoresBtn) scoresBtn.style.display = state.canViewScores ? '' : 'none';
    if (scoresPane) scoresPane.style.display = state.canViewScores ? '' : 'none';
    if (evaluationBtn) evaluationBtn.style.display = state.canViewEvaluation ? '' : 'none';
    if (evaluationPane) evaluationPane.style.display = state.canViewEvaluation ? '' : 'none';
    const active = document.querySelector('#main-tabs button.active');
    if (!active || active.style.display === 'none') {
        const firstVisible = [...document.querySelectorAll('#main-tabs button[data-tab]')].find(b => b.style.display !== 'none');
        firstVisible?.click();
    }
    syncUploadButtonsAccess();
}

function pickStatusColor(status) {
    if (status === 'red') return 'background:#ffd6d6;';
    if (status === 'yellow') return 'background:#fff5cc;';
    if (status === 'gray') return 'background:#e6e6e6;';
    if (status === 'orange') return 'background:#ffe5cc;';
    return '';
}

function bindMainTabs() {
    document.querySelectorAll('#main-tabs button[data-tab]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#main-tabs button').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
            const pane = document.getElementById(`tab-${btn.dataset.tab}`);
            if (pane) pane.classList.add('active');
        });
    });
}

function bindWorkSubtabs() {
    document.querySelectorAll('#works-tabs button[data-subtab]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#works-tabs button').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            ['results', 'missing', 'stats', 'errors', 'upload-log'].forEach(key => {
                const pane = document.getElementById(`works-${key}`);
                if (!pane) return;
                pane.style.display = key === btn.dataset.subtab ? 'block' : 'none';
            });
        });
    });
    document.querySelectorAll('#external-works-tabs button[data-subtab]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#external-works-tabs button').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            ['results', 'missing', 'stats', 'errors'].forEach(key => {
                const pane = document.getElementById(`external-works-${key}`);
                if (!pane) return;
                pane.style.display = key === btn.dataset.subtab ? 'block' : 'none';
            });
        });
    });
}

async function uploadFiles(inputId, url, logId) {
    const input = document.getElementById(inputId);
    if (!input.files.length) return;
    const form = new FormData();
    [...input.files].forEach(file => form.append('files', file));
    const result = await api(url, { method: 'POST', body: form });
    document.getElementById(logId).innerHTML = (result || []).map(item => `${item.success ? '✅' : '❌'} ${item.fileName}: ${item.message}`).join('<br>');
    input.value = '';
    await reloadAll();
}

async function reloadChanges() {
    const data = await api(scoped('/api/oge/gia/changes'));
    document.getElementById('changes-body').innerHTML = (data.changes || []).map(c => `
      <tr><td>${c.type || ''}</td><td>${c.key || ''}</td><td>${c.wasValue || ''}</td><td>${c.becameValue || ''}</td></tr>`).join('')
      || '<tr><td colspan="4" class="muted">Нет изменений</td></tr>';
}

async function reloadStudents() {
    state.students = await api(scoped('/api/oge/gia/participants'));
    const versions = await api(scoped('/api/oge/gia/versions'));
    const latest = versions && versions.length ? versions[0] : null;
    const title = document.getElementById('students-title');
    if (title && latest?.uploadedAt) {
        const d = new Date(latest.uploadedAt);
        const dd = String(d.getDate()).padStart(2, '0');
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const yyyy = d.getFullYear();
        title.textContent = `ОГЭ / Выбор ОГЭ (${dd}.${mm}.${yyyy})`;
    }
    renderStudents();
}

function renderStudents() {
    const q = (document.getElementById('students-search')?.value || '').trim().toLowerCase();
    const rows = state.students.filter(r => !q || (r.fullName || '').toLowerCase().includes(q));
    document.getElementById('students-body').innerHTML = rows.map(r => `
      <tr><td>${r.className || ''}</td><td>${r.fullName || ''}</td><td>${r.examCount ?? 0}</td><td>${(r.selectedSubjects || []).join(', ')}</td></tr>`).join('')
      || '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
}

async function reloadGiaStats() {
    const data = await api(scoped('/api/oge/gia/stats'));
    const subjects = data.subjects || [];
    document.getElementById('gia-stats-head').innerHTML = `<tr><th>Класс</th>${subjects.map(s => `<th>${s}</th>`).join('')}</tr>`;
    document.getElementById('gia-stats-body').innerHTML = (data.classes || []).map(r => `<tr><td>${r.className}</td>${subjects.map(s => `<td style="${(r.counts?.[s] || 0) > 0 ? 'background:#e8f7e8;' : ''}">${r.counts?.[s] || 0}</td>`).join('')}</tr>`).join('');
    document.getElementById('gia-stats-foot').innerHTML = `<tr><th>ИТОГО</th>${subjects.map(s => `<th>${data.totalsBySubject?.[s] || 0}</th>`).join('')}</tr>`;
    const dist = data.examCountDistribution || {};
    document.getElementById('exam-dist').textContent = `Сдают 2 экзамена: ${dist['2'] || 0}, сдают 4 экзамена: ${dist['4'] || 0}`;
}

async function reloadMismatches() {
    const data = await api(scoped('/api/oge/mismatches'));
    state.mismatches = data.rows || [];
    const info = document.getElementById('mismatch-info');
    if (info) info.textContent = data.infoMessage || '';
    renderMismatches();
}

function renderMismatches() {
    const fType = (document.getElementById('flt-type')?.value || '').toLowerCase();
    const fClass = (document.getElementById('flt-class')?.value || '').toLowerCase();
    const fFioGia = (document.getElementById('flt-fio-gia')?.value || '').toLowerCase();
    const fFioCont = (document.getElementById('flt-fio-cont')?.value || '').toLowerCase();
    const fDocGia = (document.getElementById('flt-doc-gia')?.value || '').toLowerCase();
    const fDocCont = (document.getElementById('flt-doc-cont')?.value || '').toLowerCase();
    const fReason = (document.getElementById('flt-reason')?.value || '').toLowerCase();
    const rows = state.mismatches.filter(r =>
        String(r.type || '').toLowerCase().includes(fType) &&
        String(r.className || '').toLowerCase().includes(fClass) &&
        String(r.fioGia || '').toLowerCase().includes(fFioGia) &&
        String(r.fioContingent || '').toLowerCase().includes(fFioCont) &&
        String(r.documentGia || '').toLowerCase().includes(fDocGia) &&
        String(r.documentContingent || '').toLowerCase().includes(fDocCont) &&
        String(r.reason || '').toLowerCase().includes(fReason)
    );
    document.getElementById('mismatch-body').innerHTML = rows.map(r => `
      <tr>
        <td>${r.type || ''}</td><td>${r.className || ''}</td><td>${r.fioGia || ''}</td><td>${r.fioContingent || ''}</td>
        <td>${r.documentGia || ''}</td><td>${r.documentContingent || ''}</td><td>${r.reason || ''}</td>
      </tr>
    `).join('') || '<tr><td colspan=\"7\" class=\"muted\">Нестыковок не найдено</td></tr>';
}

function buildResultsMatrix(rows) {
    const byStudent = new Map();
    for (const row of rows) {
        const key = `${row.className || ''}|${row.fullName || ''}`;
        if (!byStudent.has(key)) byStudent.set(key, { className: row.className || '', fullName: row.fullName || '', values: {} });
        byStudent.get(key).values[row.subject] = row;
    }

    const students = [...byStudent.values()].map(st => {
        const grades = SUBJECTS.map(s => st.values[s]?.grade).filter(v => typeof v === 'number');
        return {
            ...st,
            _avg: grades.length ? grades.reduce((a, b) => a + b, 0) / grades.length : null,
            _count: SUBJECTS.filter(s => st.values[s]?.expectedByGia).length
        };
    });
    students.sort(compareStudentsByMultiSort);

    let html = '<thead><tr>';
    html += `<th rowspan="2" data-sort="className">Класс${sortBadge('className')}</th>`;
    html += `<th rowspan="2" data-sort="fullName">ФИО${sortBadge('fullName')}</th>`;
    html += `<th rowspan="2" data-sort="examCount">Кол-во предметов для сдачи${sortBadge('examCount')}</th>`;
    html += `<th rowspan="2" data-sort="avgGrade">Средний балл за сданные предметы${sortBadge('avgGrade')}</th>`;
    for (const s of SUBJECTS) html += `<th colspan="2" class="${subjectHeaderClass(s)}">${s}</th>`;
    html += '</tr><tr>';
    for (const s of SUBJECTS) {
        html += `<th data-sort="score:${s}" class="${subjectScoreClass(s)}">Тестовый балл${sortBadge(`score:${s}`)}</th>`;
        html += `<th data-sort="grade:${s}" class="${subjectGradeClass(s)}">Оценка${sortBadge(`grade:${s}`)}</th>`;
    }
    html += '</tr></thead><tbody>';

    for (const st of students) {
        const avg = st._avg != null ? st._avg.toFixed(3).replace('.', ',') : '';
        const examCount = st._count;
        html += `<tr><td>${st.className}</td><td>${st.fullName}</td><td>${examCount}</td><td>${avg}</td>`;
        for (const s of SUBJECTS) {
            const v = st.values[s];
            html += `<td class="${subjectScoreClass(s)}" style="${pickStatusColor(v?.status)}">${v?.score ?? ''}</td><td class="${subjectGradeClass(s)}" style="${pickStatusColor(v?.status)}">${v?.grade ?? ''}</td>`;
        }
        html += '</tr>';
    }
    html += '</tbody>';
    return html;
}

function subjectHeaderClass(subject) {
    return subject === 'Русский язык' ? 'subject-ru' : '';
}

function subjectScoreClass(subject) {
    return subject === 'Русский язык' ? 'subject-ru-score' : '';
}

function subjectGradeClass(subject) {
    return subject === 'Русский язык' ? 'subject-ru-grade' : '';
}

function sortBadge(key) {
    const idx = state.workSorts.findIndex(s => s.key === key);
    if (idx < 0) return '';
    const dir = state.workSorts[idx].dir === 'asc' ? '↑' : '↓';
    return ` <span style="font-size:10px;">${idx + 1}${dir}</span>`;
}

function valueBySortKey(student, key) {
    if (key === 'className') return student.className || '';
    if (key === 'fullName') return student.fullName || '';
    if (key === 'examCount') return student._count ?? 0;
    if (key === 'avgGrade') return student._avg ?? -1;
    if (key.startsWith('score:')) {
        const subject = key.substring('score:'.length);
        return student.values[subject]?.score ?? -1;
    }
    if (key.startsWith('grade:')) {
        const subject = key.substring('grade:'.length);
        return student.values[subject]?.grade ?? -1;
    }
    return '';
}

function compareStudentsByMultiSort(a, b) {
    for (const sort of state.workSorts) {
        const av = valueBySortKey(a, sort.key);
        const bv = valueBySortKey(b, sort.key);
        let cmp = 0;
        if (typeof av === 'number' && typeof bv === 'number') cmp = av - bv;
        else cmp = String(av).localeCompare(String(bv), 'ru');
        if (cmp !== 0) return sort.dir === 'asc' ? cmp : -cmp;
    }
    return 0;
}

function bindResultsMatrixSorting() {
    document.querySelectorAll('#results-matrix th[data-sort]').forEach(th => {
        th.addEventListener('click', (event) => {
            const key = th.dataset.sort;
            const existingIdx = state.workSorts.findIndex(s => s.key === key);
            const toggleDir = existingIdx >= 0 && state.workSorts[existingIdx].dir === 'asc' ? 'desc' : 'asc';
            if (!event.shiftKey) {
                state.workSorts = [{ key, dir: toggleDir }];
            } else {
                if (existingIdx >= 0) {
                    state.workSorts[existingIdx].dir = toggleDir;
                } else {
                    state.workSorts.push({ key, dir: 'asc' });
                }
            }
            reloadWorks();
        });
    });
}

async function reloadWorks(source = 'INTERNAL') {
    const data = await api(scoped(`/api/oge/works/dataset?source=${encodeURIComponent(source)}`));
    const isExternal = source === 'EXTERNAL_TRYOUT';
    const matrixId = isExternal ? 'external-results-matrix' : 'results-matrix';
    const missingId = isExternal ? 'external-missing-body' : 'missing-body';
    const statsId = isExternal ? 'external-work-stats-body' : 'work-stats-body';
    const errorsId = isExternal ? 'external-works-errors-body' : 'works-errors-body';
    const uploadLogId = isExternal ? null : 'works-upload-log-body';

    document.getElementById(matrixId).innerHTML = buildResultsMatrix(data.results || []);
    bindResultsMatrixSorting();
    document.getElementById(missingId).innerHTML = (data.missing || []).map(r => `<tr style="${pickStatusColor('yellow')}"><td>${r.subject}</td><td>${r.className}</td><td>${r.fullName}</td></tr>`).join('');
    document.getElementById(statsId).innerHTML = (data.statistics || []).map(r => `<tr><td>${r.className}</td><td>${r.subject}</td><td>${r.count2}</td><td>${r.count3}</td><td>${r.count4}</td><td>${r.count5}</td></tr>`).join('');
    document.getElementById(errorsId).innerHTML = (data.errors || []).map(e => `<tr><td>${e}</td></tr>`).join('')
        || '<tr><td class="muted">Ошибок нет</td></tr>';
    if (uploadLogId) {
        try {
            const logs = await api(scoped('/api/oge/works/import-logs?source=INTERNAL'));
            document.getElementById(uploadLogId).innerHTML = (logs || []).map(l => `
              <tr>
                <td>${new Date(l.createdAt).toLocaleString('ru-RU')}</td>
                <td>${l.fileName || ''}</td>
                <td>${l.success ? '✅ OK' : '❌ Ошибка'}</td>
                <td>${l.message || ''}</td>
                <td>${l.records ?? 0}</td>
              </tr>
            `).join('') || '<tr><td colspan="5" class="muted">Логов пока нет</td></tr>';
        } catch (e) {
            document.getElementById(uploadLogId).innerHTML = `<tr><td colspan="5" class="muted">${e.message || 'Нет доступа к журналу загрузок'}</td></tr>`;
        }
    }
}

async function reloadTeacherBinding() {
    const mode = document.getElementById('teacher-binding-mode')?.value || 'unbound';
    const onlyUnbound = mode !== 'all';
    state.teachers = await api(scoped('/api/oge/works/teachers'));
    state.teacherBindings = await api(scoped(`/api/oge/works/teacher-binding?onlyUnbound=${onlyUnbound}`));
    state.teacherBindings.sort((a, b) => String(a.className || '').localeCompare(String(b.className || ''), 'ru')
        || String(a.subject || '').localeCompare(String(b.subject || ''), 'ru')
        || String(a.fullName || '').localeCompare(String(b.fullName || ''), 'ru'));
    const options = ['<option value="">— не выбран —</option>']
        .concat((state.teachers || []).map(t => `<option value="${t}">${t}</option>`))
        .join('');
    document.getElementById('teacher-binding-body').innerHTML = (state.teacherBindings || []).map(r => `
      <tr data-class="${r.className || ''}" data-fio="${r.fullName || ''}" data-subject="${r.subject || ''}">
        <td>${r.className || ''}</td>
        <td>${r.fullName || ''}</td>
        <td>${r.subject || ''}</td>
        <td><select class="teacher-select">${options}</select></td>
      </tr>
    `).join('') || '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
    document.querySelectorAll('#teacher-binding-body tr[data-class]').forEach(tr => {
        const cls = tr.dataset.class;
        const fio = tr.dataset.fio;
        const sub = tr.dataset.subject;
        const row = (state.teacherBindings || []).find(x => x.className === cls && x.fullName === fio && x.subject === sub);
        const sel = tr.querySelector('select.teacher-select');
        if (sel) sel.value = row?.teacherFio || '';
    });
}

async function saveTeacherBindings() {
    const updates = [...document.querySelectorAll('#teacher-binding-body tr[data-class]')].map(tr => ({
        className: tr.dataset.class || '',
        fullName: tr.dataset.fio || '',
        subject: tr.dataset.subject || '',
        teacherFio: tr.querySelector('select.teacher-select')?.value || ''
    }));
    await api(scoped('/api/oge/works/teacher-binding'), {
        method: 'PUT',
        headers: jsonHeaders,
        body: JSON.stringify(updates)
    });
    const log = document.getElementById('teacher-binding-log');
    if (log) log.textContent = 'Привязка сохранена';
    await Promise.all([reloadWorks('INTERNAL'), reloadWorks('EXTERNAL_TRYOUT'), reloadTeacherBinding()]);
}

async function bindTeachersFromLoad() {
    const msg = await api(scoped('/api/oge/works/teacher-binding/from-load'), { method: 'POST' });
    const log = document.getElementById('teacher-binding-log');
    if (log) log.textContent = typeof msg === 'string' ? msg : (msg?.message || JSON.stringify(msg));
    await Promise.all([reloadWorks('INTERNAL'), reloadWorks('EXTERNAL_TRYOUT'), reloadTeacherBinding()]);
}

async function reloadScale() {
    const rows = await api(scoped('/api/oge/scores'));
    let html = `<thead><tr><th>Баллы за ОГЭ</th>${SUBJECTS.map(s => `<th>${s}</th>`).join('')}</tr></thead><tbody>`;
    for (const row of rows) {
        html += `<tr data-score="${row.score}"><td>${row.score}</td>`;
        for (const s of SUBJECTS) {
            const val = row.gradesBySubject?.[s];
            html += `<td><input type="number" min="2" max="5" value="${val ?? ''}" data-subject="${s}" style="width:48px"></td>`;
        }
        html += '</tr>';
    }
    html += '</tbody>';
    document.getElementById('scale-table').innerHTML = html;
}

async function reloadEvaluation() {
    const rows = await api(scoped('/api/oge/evaluation'));
    const subjects = (rows || []).map(r => r.subject);
    const maxTask = Math.max(0, ...(rows || []).map(r => (r.maxScores || []).length));
    const head = document.getElementById('evaluation-head');
    const body = document.getElementById('evaluation-body');
    if (!body || !head) return;
    head.innerHTML = `<tr><th>№ задания</th>${subjects.map(s => `<th>${s}</th>`).join('')}</tr>`;
    if (!rows || !rows.length) {
        body.innerHTML = '<tr><td colspan="2" class="muted">Нет данных</td></tr>';
        return;
    }
    const bySubject = Object.fromEntries(rows.map(r => [r.subject, r.maxScores || []]));
    let html = '';
    for (let task = 1; task <= maxTask; task++) {
        html += `<tr><td>${task}</td>`;
        for (const s of subjects) {
            const value = bySubject[s][task - 1];
            html += `<td><input data-subject="${s}" data-task="${task}" type="number" min="0" max="20" value="${value ?? ''}" style="width:54px"></td>`;
        }
        html += '</tr>';
    }
    body.innerHTML = html;
}

async function saveEvaluation() {
    const map = new Map();
    document.querySelectorAll('#evaluation-body input[data-subject][data-task]').forEach(inp => {
        const subject = inp.dataset.subject;
        const task = Number(inp.dataset.task);
        if (!map.has(subject)) map.set(subject, []);
        const arr = map.get(subject);
        const val = inp.value === '' ? null : Number(inp.value);
        arr[task - 1] = Number.isFinite(val) ? val : null;
    });
    const rows = [...map.entries()].map(([subject, maxScores]) => {
        let last = maxScores.length - 1;
        while (last >= 0 && (maxScores[last] == null)) last--;
        return { subject, maxScores: maxScores.slice(0, last + 1).map(v => v == null ? 0 : v) };
    });
    await api(scoped('/api/oge/evaluation'), {
        method: 'PUT',
        headers: jsonHeaders,
        body: JSON.stringify(rows)
    });
    await reloadEvaluation();
}

async function saveScale() {
    const rows = [...document.querySelectorAll('#scale-table tbody tr')].map(tr => {
        const score = Number(tr.dataset.score);
        const gradesBySubject = {};
        tr.querySelectorAll('input[data-subject]').forEach(inp => {
            gradesBySubject[inp.dataset.subject] = inp.value === '' ? null : Number(inp.value);
        });
        return { score, gradesBySubject };
    });
    await api(scoped('/api/oge/scores'), { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(rows) });
}

async function reloadAll() {
    const tasks = [reloadStudents(), reloadGiaStats(), reloadWorks('INTERNAL')];
    if (state.canViewUpload) tasks.push(reloadChanges());
    if (state.canViewMismatches) tasks.push(reloadMismatches());
    if (state.canViewExternalWorks) tasks.push(reloadWorks('EXTERNAL_TRYOUT'));
    if (state.canViewScores) tasks.push(reloadScale());
    if (state.canViewTeacherBinding) tasks.push(reloadTeacherBinding());
    if (state.canViewEvaluation) tasks.push(reloadEvaluation());
    await Promise.all(tasks);
}

function bindButtons() {
    document.getElementById('gia-upload-btn').addEventListener('click', () => uploadFiles('gia-files', scoped('/api/oge/gia/import'), 'gia-upload-log'));
    document.getElementById('work-upload-btn').addEventListener('click', () => uploadFiles('work-files', scoped('/api/oge/works/import'), 'work-upload-log'));
    document.getElementById('external-work-upload-btn').addEventListener('click', () => uploadFiles('external-work-files', scoped('/api/oge/external-works/import'), 'external-work-upload-log'));
    document.getElementById('save-scale-btn').addEventListener('click', saveScale);
    document.getElementById('save-evaluation-btn').addEventListener('click', saveEvaluation);
    document.getElementById('students-search').addEventListener('input', renderStudents);
    document.getElementById('works-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/works/export?source=INTERNAL'));
    document.getElementById('external-works-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/works/export?source=EXTERNAL_TRYOUT'));
    document.getElementById('gia-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/gia/export'));
    document.getElementById('gia-export-btn-students').addEventListener('click', () => window.location.href = scoped('/api/oge/gia/export'));
    document.getElementById('mismatch-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/mismatches/export'));
    document.getElementById('save-teacher-binding-btn').addEventListener('click', saveTeacherBindings);
    document.getElementById('bind-from-load-btn').addEventListener('click', bindTeachersFromLoad);
    document.getElementById('teacher-binding-mode').addEventListener('change', reloadTeacherBinding);
    ['flt-type', 'flt-class', 'flt-fio-gia', 'flt-fio-cont', 'flt-doc-gia', 'flt-doc-cont', 'flt-reason']
        .forEach(id => document.getElementById(id)?.addEventListener('input', renderMismatches));
}

(async function init() {
    await waitForAuthContext();
    bindMainTabs();
    bindWorkSubtabs();
    bindButtons();
    applyTabVisibility();
    await reloadAll();
})();
