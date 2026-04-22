const jsonHeaders = { 'Content-Type': 'application/json' };
const SUBJECTS = [
    'Русский язык', 'Математика', 'Физика', 'Химия', 'Информатика и ИКТ', 'Биология',
    'История', 'География', 'Английский язык', 'Немецкий язык', 'Французский язык',
    'Обществознание', 'Испанский язык', 'Литература'
];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function colorByStatus(status) {
    if (status === 'red') return 'background:#ffd6d6;';
    if (status === 'yellow') return 'background:#fff5cc;';
    if (status === 'gray') return 'background:#e6e6e6;';
    return '';
}

async function reloadAll() {
    await Promise.all([reloadChanges(), reloadWorksDataset(), reloadGiaStats(), reloadScale()]);
}

async function reloadChanges() {
    const data = await api('/api/oge/gia/changes');
    const body = document.getElementById('changes-body');
    body.innerHTML = (data.changes || []).map(row => `
        <tr>
            <td>${row.type || ''}</td>
            <td>${row.key || ''}</td>
            <td>${row.wasValue || ''}</td>
            <td>${row.becameValue || ''}</td>
        </tr>`).join('') || '<tr><td colspan="4" class="muted">Нет данных</td></tr>';
}

async function reloadWorksDataset() {
    const data = await api('/api/oge/works/dataset');
    document.getElementById('results-body').innerHTML = (data.results || []).map(r => `
        <tr style="${colorByStatus(r.status)}">
            <td>${r.className || ''}</td>
            <td>${r.fullName || ''}</td>
            <td>${r.subject || ''}</td>
            <td>${r.score ?? ''}</td>
            <td>${r.grade ?? ''}</td>
            <td>${r.status || ''}</td>
        </tr>`).join('') || '<tr><td colspan="6" class="muted">Нет данных</td></tr>';

    document.getElementById('missing-body').innerHTML = (data.missing || []).map(r => `
        <tr style="${colorByStatus('yellow')}">
            <td>${r.subject || ''}</td>
            <td>${r.className || ''}</td>
            <td>${r.fullName || ''}</td>
        </tr>`).join('') || '<tr><td colspan="3" class="muted">Нет пропусков</td></tr>';

    document.getElementById('work-stats-body').innerHTML = (data.statistics || []).map(r => `
        <tr>
            <td>${r.className || ''}</td><td>${r.subject || ''}</td><td>${r.count2 || 0}</td><td>${r.count3 || 0}</td><td>${r.count4 || 0}</td><td>${r.count5 || 0}</td>
        </tr>`).join('') || '<tr><td colspan="6" class="muted">Нет данных</td></tr>';
}

async function reloadGiaStats() {
    const data = await api('/api/oge/gia/stats');
    const subjects = data.subjects || [];
    document.getElementById('gia-stats-head').innerHTML = `<tr><th>Класс</th>${subjects.map(s => `<th>${s}</th>`).join('')}</tr>`;
    const body = document.getElementById('gia-stats-body');
    body.innerHTML = (data.classes || []).map(row => `
        <tr>
            <td>${row.className || ''}</td>
            ${subjects.map(subject => `<td style="${(row.counts?.[subject] || 0) > 0 ? 'background:#e8f7e8;' : ''}">${row.counts?.[subject] || 0}</td>`).join('')}
        </tr>`).join('') || '<tr><td class="muted">Нет данных</td></tr>';

    document.getElementById('gia-stats-foot').innerHTML = `<tr><th>ИТОГО</th>${subjects.map(s => `<th>${data.totalsBySubject?.[s] || 0}</th>`).join('')}</tr>`;
    const dist = data.examCountDistribution || {};
    document.getElementById('exam-dist').textContent = Object.keys(dist).length
        ? Object.entries(dist).map(([k, v]) => `${k} экзамена(ов): ${v}`).join(' · ')
        : 'Нет данных по количеству экзаменов';
}

async function reloadScale() {
    const rows = await api('/api/oge/scores');
    const table = document.getElementById('scale-table');
    let html = `<thead><tr><th>Баллы</th>${SUBJECTS.map(s => `<th>${s}</th>`).join('')}</tr></thead><tbody>`;
    for (const row of rows) {
        html += `<tr data-score="${row.score}"><td>${row.score}</td>`;
        for (const subject of SUBJECTS) {
            const value = row.gradesBySubject?.[subject] ?? '';
            html += `<td><input type="number" min="2" max="5" value="${value === null ? '' : value}" data-subject="${subject}" style="width:64px"></td>`;
        }
        html += '</tr>';
    }
    html += '</tbody>';
    table.innerHTML = html;
}

async function uploadFiles(inputId, url, logId) {
    const input = document.getElementById(inputId);
    if (!input.files.length) return;
    const form = new FormData();
    for (const file of input.files) form.append('files', file);
    const result = await api(url, { method: 'POST', body: form });
    document.getElementById(logId).innerHTML = (result || []).map(item =>
        `${item.success ? '✅' : '❌'} ${item.fileName}: ${item.message}${item.records != null ? ` (строк: ${item.records})` : ''}`
    ).join('<br>');
    input.value = '';
    await reloadAll();
}

async function saveScale() {
    const rows = [...document.querySelectorAll('#scale-table tbody tr')].map(tr => {
        const score = Number(tr.dataset.score);
        const gradesBySubject = {};
        tr.querySelectorAll('input[data-subject]').forEach(inp => {
            const value = inp.value === '' ? null : Number(inp.value);
            gradesBySubject[inp.dataset.subject] = Number.isNaN(value) ? null : value;
        });
        return { score, gradesBySubject };
    });
    await api('/api/oge/scores', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(rows) });
    await reloadScale();
}

function bind() {
    document.getElementById('gia-upload-btn').addEventListener('click', () => uploadFiles('gia-files', '/api/oge/gia/import', 'gia-upload-log'));
    document.getElementById('work-upload-btn').addEventListener('click', () => uploadFiles('work-files', '/api/oge/works/import', 'work-upload-log'));
    document.getElementById('save-scale-btn').addEventListener('click', saveScale);
    document.getElementById('works-export-btn').addEventListener('click', () => { window.location.href = '/api/oge/works/export'; });
    document.getElementById('gia-export-btn').addEventListener('click', () => { window.location.href = '/api/oge/gia/export'; });
}

(async function init() {
    bind();
    await reloadAll();
})();
