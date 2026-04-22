const jsonHeaders = { 'Content-Type': 'application/json' };
const SUBJECTS = [
    'Русский язык', 'Математика', 'Физика', 'Химия', 'Информатика и ИКТ', 'Биология',
    'История', 'География', 'Английский язык', 'Немецкий язык', 'Французский язык',
    'Обществознание', 'Испанский язык', 'Литература'
];

const state = { students: [], mismatches: [], canViewUpload: true, canViewMismatches: true };

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

async function waitForAuthContext() {
    for (let i = 0; i < 50; i++) {
        if (window.tarificationAuth) return;
        await new Promise(resolve => setTimeout(resolve, 50));
    }
}

function applyTabVisibility() {
    state.canViewUpload = hasViewPermission('OGE_UPLOAD_VIEW');
    state.canViewMismatches = hasViewPermission('OGE_MISMATCH_VIEW');
    const uploadBtn = document.querySelector('#main-tabs button[data-tab="upload"]');
    const uploadPane = document.getElementById('tab-upload');
    const mismatchBtn = document.querySelector('#main-tabs button[data-tab="mismatches"]');
    const mismatchPane = document.getElementById('tab-mismatches');
    if (uploadBtn) uploadBtn.style.display = state.canViewUpload ? '' : 'none';
    if (uploadPane) uploadPane.style.display = state.canViewUpload ? '' : 'none';
    if (mismatchBtn) mismatchBtn.style.display = state.canViewMismatches ? '' : 'none';
    if (mismatchPane) mismatchPane.style.display = state.canViewMismatches ? '' : 'none';
    const active = document.querySelector('#main-tabs button.active');
    if (!active || active.style.display === 'none') {
        const firstVisible = [...document.querySelectorAll('#main-tabs button[data-tab]')].find(b => b.style.display !== 'none');
        firstVisible?.click();
    }
}

function pickStatusColor(status) {
    if (status === 'red') return 'background:#ffd6d6;';
    if (status === 'yellow') return 'background:#fff5cc;';
    if (status === 'gray') return 'background:#e6e6e6;';
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
            ['results', 'missing', 'stats'].forEach(key => {
                const pane = document.getElementById(`works-${key}`);
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
    const data = await api('/api/oge/gia/changes');
    document.getElementById('changes-body').innerHTML = (data.changes || []).map(c => `
      <tr><td>${c.type || ''}</td><td>${c.key || ''}</td><td>${c.wasValue || ''}</td><td>${c.becameValue || ''}</td></tr>`).join('')
      || '<tr><td colspan="4" class="muted">Нет изменений</td></tr>';
}

async function reloadStudents() {
    state.students = await api('/api/oge/gia/participants');
    const versions = await api('/api/oge/gia/versions');
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

    const students = [...byStudent.values()].sort((a, b) => (a.className || '').localeCompare(b.className || '', 'ru') || (a.fullName || '').localeCompare(b.fullName || '', 'ru'));
    let html = '<thead><tr><th rowspan="2">Класс</th><th rowspan="2">ФИО</th><th rowspan="2">Кол-во предметов для сдачи</th><th rowspan="2">Средний балл за сданные предметы</th>';
    for (const s of SUBJECTS) html += `<th colspan="2">${s}</th>`;
    html += '</tr><tr>';
    for (let i = 0; i < SUBJECTS.length; i++) html += '<th>Тестовый балл</th><th>Оценка</th>';
    html += '</tr></thead><tbody>';

    for (const st of students) {
        const grades = SUBJECTS.map(s => st.values[s]?.grade).filter(v => typeof v === 'number');
        const avg = grades.length ? (grades.reduce((a, b) => a + b, 0) / grades.length).toFixed(3).replace('.', ',') : '';
        const examCount = SUBJECTS.filter(s => st.values[s]?.expectedByGia).length;
        html += `<tr><td>${st.className}</td><td>${st.fullName}</td><td>${examCount}</td><td>${avg}</td>`;
        for (const s of SUBJECTS) {
            const v = st.values[s];
            html += `<td style="${pickStatusColor(v?.status)}">${v?.score ?? ''}</td><td style="${pickStatusColor(v?.status)}">${v?.grade ?? ''}</td>`;
        }
        html += '</tr>';
    }
    html += '</tbody>';
    return html;
}

async function reloadWorks() {
    const data = await api('/api/oge/works/dataset');
    document.getElementById('results-matrix').innerHTML = buildResultsMatrix(data.results || []);
    document.getElementById('missing-body').innerHTML = (data.missing || []).map(r => `<tr style="${pickStatusColor('yellow')}"><td>${r.subject}</td><td>${r.className}</td><td>${r.fullName}</td></tr>`).join('');
    document.getElementById('work-stats-body').innerHTML = (data.statistics || []).map(r => `<tr><td>${r.className}</td><td>${r.subject}</td><td>${r.count2}</td><td>${r.count3}</td><td>${r.count4}</td><td>${r.count5}</td></tr>`).join('');
}

async function reloadScale() {
    const rows = await api('/api/oge/scores');
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

async function saveScale() {
    const rows = [...document.querySelectorAll('#scale-table tbody tr')].map(tr => {
        const score = Number(tr.dataset.score);
        const gradesBySubject = {};
        tr.querySelectorAll('input[data-subject]').forEach(inp => {
            gradesBySubject[inp.dataset.subject] = inp.value === '' ? null : Number(inp.value);
        });
        return { score, gradesBySubject };
    });
    await api('/api/oge/scores', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(rows) });
}

async function reloadAll() {
    const tasks = [reloadStudents(), reloadGiaStats(), reloadWorks(), reloadScale()];
    if (state.canViewUpload) tasks.push(reloadChanges());
    if (state.canViewMismatches) tasks.push(reloadMismatches());
    await Promise.all(tasks);
}

function bindButtons() {
    document.getElementById('gia-upload-btn').addEventListener('click', () => uploadFiles('gia-files', scoped('/api/oge/gia/import'), 'gia-upload-log'));
    document.getElementById('work-upload-btn').addEventListener('click', () => uploadFiles('work-files', scoped('/api/oge/works/import'), 'work-upload-log'));
    document.getElementById('save-scale-btn').addEventListener('click', saveScale);
    document.getElementById('students-search').addEventListener('input', renderStudents);
    document.getElementById('works-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/works/export'));
    document.getElementById('gia-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/gia/export'));
    document.getElementById('mismatch-export-btn').addEventListener('click', () => window.location.href = scoped('/api/oge/mismatches/export'));
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
