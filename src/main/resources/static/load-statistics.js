const ui = {
    refreshBtn: document.getElementById('refresh-load-stats-btn'),
    exportBtn: document.getElementById('export-load-stats-btn'),
    summary: document.getElementById('load-stats-summary'),
    table: document.getElementById('load-stats-table'),
    result: document.getElementById('load-stats-result')
};

let curriculumRows = [];
let manualRows = [];
let buildings = [];
let classroomRows = [];
let subjectCatalog = [];

async function api(path, options = {}) {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear(path) : path;
    const response = await fetch(scopedPath, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(v) { ui.result.textContent = JSON.stringify(v, null, 2); }
const esc = (v) => String(v ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');

function normalizeBuildingCode(value) {
    return String(value || '').trim().toUpperCase().replace(/[–—]/g, '-').replace(/\s*\|\s*/g, '|').replace(/\s+/g, '');
}
function normalizeClassName(value) {
    const v = String(value || '').trim().toUpperCase().replace(/[–—]/g, '-');
    const m = v.match(/^(\d{1,2})\s*[- ]?\s*([А-ЯA-Z])$/);
    return m ? `${m[1]}-${m[2]}` : v;
}
function canonicalBuildingCode(value) {
    const normalized = normalizeBuildingCode(value);
    if (!normalized) return '';
    const match = (buildings || []).find((b) => normalizeBuildingCode(b?.code) === normalized || normalizeBuildingCode(b?.name) === normalized);
    return match ? normalizeBuildingCode(match.code) : normalized;
}
function classBuildingMap() {
    const map = new Map();
    (classroomRows || []).forEach((r) => {
        const cls = normalizeClassName(r.className);
        const b = normalizeBuildingCode(r.numberSchoolBuilding);
        if (cls && b) map.set(cls, b);
    });
    return map;
}
function rowStudyPeriod(row) {
    return String(row?.studyPeriod || 'YEAR').toUpperCase();
}
function groupSuffix(row) {
    return row?.__groupIndex ? `|G${row.__groupIndex}` : '';
}
function apiKeyOfRow(row) {
    return `${row.className}|${row.subjectName}|${row.curriculumPart || 'CORE'}|${row.educationLevel}|${rowStudyPeriod(row)}${groupSuffix(row)}`;
}
function expandCurriculumRows(rows) {
    const expanded = [];
    rows.forEach((row) => {
        const subgroupRequired = Boolean(row.subgroupRequired);
        const subgroupCount = subgroupRequired ? Math.max(Number(row.subgroupCount || 2), 2) : 0;
        if (!subgroupRequired) {
            expanded.push({ ...row, __groupIndex: null, __groupCount: 0 });
            return;
        }
        for (let i = 1; i <= subgroupCount; i += 1) {
            const subgroupHours = i === 1 ? Number(row.subgroup1Hours || row.plannedHours || 0) : Number(row.subgroup2Hours || row.plannedHours || 0);
            const subgroupLevel = i === 1 ? (row.subgroup1EducationLevel || row.educationLevel) : (row.subgroup2EducationLevel || row.educationLevel);
            expanded.push({ ...row, plannedHours: subgroupHours, educationLevel: subgroupLevel, __groupIndex: i, __groupCount: subgroupCount });
        }
    });
    return expanded;
}
function isVacancyTeacherName(teacherName) {
    return String(teacherName || '').trim().toLowerCase().includes('вакан');
}

function renderStatsView() {
    if (!(curriculumRows || []).length) {
        ui.summary.textContent = 'Нет строк учебного плана для формирования статистики.';
        ui.table.innerHTML = '<tbody><tr><td>Нет данных.</td></tr></tbody>';
        return;
    }

    const buildingRows = (buildings || []).filter((b) => b.code !== '__ARCHIVE__');
    const classToBuilding = classBuildingMap();
    const subjectAreaByName = new Map((subjectCatalog || []).map((s) => [String(s.subjectName || '').trim().toLowerCase(), String(s.subjectAreaName || '').trim() || 'Без области']));

    const assignmentsByBuilding = {};
    (manualRows || []).forEach((row) => {
        const buildingCode = normalizeBuildingCode(row.numberSchoolBuilding);
        if (!buildingCode) return;
        if (!assignmentsByBuilding[buildingCode]) assignmentsByBuilding[buildingCode] = {};
        const fake = { className: row.className, subjectName: row.subjectName, curriculumPart: row.curriculumPart || 'CORE', educationLevel: row.educationLevel, studyPeriod: row.studyPeriod, __groupIndex: null };
        assignmentsByBuilding[buildingCode][apiKeyOfRow(fake)] = row.fioTeacher;
    });

    const rowsBySubject = new Map();
    const getRow = (subjectName) => {
        const key = String(subjectName || '').trim().toLowerCase();
        if (!rowsBySubject.has(key)) {
            rowsBySubject.set(key, { subjectArea: subjectAreaByName.get(key) || 'Без области', subjectName: String(subjectName || '').trim(), totalPlanned: 0, totalAssigned: 0, perBuilding: Object.fromEntries(buildingRows.map((b) => [b.code, { planned: 0, assigned: 0 }])) });
        }
        return rowsBySubject.get(key);
    };

    expandCurriculumRows(curriculumRows || []).forEach((curriculumRow) => {
        const subjectName = String(curriculumRow.subjectName || '').trim();
        if (!subjectName) return;
        const row = getRow(subjectName);
        const planned = Number(curriculumRow.plannedHours || 0);
        const fromClass = canonicalBuildingCode(classToBuilding.get(normalizeClassName(curriculumRow.className)));
        const fromRow = canonicalBuildingCode(curriculumRow.numberSchoolBuilding);
        const buildingCode = fromRow || fromClass;
        const assignmentMap = assignmentsByBuilding[buildingCode] || {};
        const assignedTeacher = String(assignmentMap[apiKeyOfRow(curriculumRow)] || '').trim();
        const assigned = (assignedTeacher && !isVacancyTeacherName(assignedTeacher)) ? planned : 0;
        row.totalPlanned += planned;
        row.totalAssigned += assigned;
        if (row.perBuilding[buildingCode]) {
            row.perBuilding[buildingCode].planned += planned;
            row.perBuilding[buildingCode].assigned += assigned;
        }
    });

    const areaTotals = new Map();
    rowsBySubject.forEach((row) => areaTotals.set(row.subjectArea || 'Без области', (areaTotals.get(row.subjectArea || 'Без области') || 0) + row.totalPlanned));

    const rows = [...rowsBySubject.values()].sort((a, b) => (a.subjectArea || '').localeCompare(b.subjectArea || '', 'ru') || a.subjectName.localeCompare(b.subjectName, 'ru'));
    const visibleBuildingRows = buildingRows.filter((building) => rows.some((row) => Number(row.perBuilding?.[building.code]?.planned || 0) > 0));
    const totalPlanned = rows.reduce((sum, row) => sum + row.totalPlanned, 0);
    const totalAssigned = rows.reduce((sum, row) => sum + row.totalAssigned, 0);
    ui.summary.textContent = `Предметов: ${rows.length}. Плановых часов: ${totalPlanned}. Распределено: ${totalAssigned}. Нераспределено: ${totalPlanned - totalAssigned}.`;

    const formatStatsBuildingLabel = (building) => {
        const code = String(building?.code || '').trim();
        const name = String(building?.name || '').trim();
        if (!name) return code;
        const normalizedCode = normalizeBuildingCode(code);
        const normalizedName = normalizeBuildingCode(name);
        if (normalizedName === normalizedCode || normalizedName.startsWith(`${normalizedCode}|`)) return name;
        return `${code} — ${name}`;
    };

    const buildingHeader = visibleBuildingRows.map((building) => `<th colspan="3">${esc(formatStatsBuildingLabel(building))}</th>`).join('');
    const buildingSubHeader = visibleBuildingRows.map(() => '<th>часы</th><th>распр.</th><th>не распр.</th>').join('');

    const thead = `<thead><tr><th rowspan="2">Предметная область</th><th rowspan="2">Предмет</th><th rowspan="2">Часы по УП</th><th rowspan="2">Распределено</th><th rowspan="2">Не распределено</th>${buildingHeader}<th rowspan="2">Суммарно часов по предметной области</th></tr><tr>${buildingSubHeader}</tr></thead>`;
    const tbody = rows.map((row) => {
        const totalUnassigned = row.totalPlanned - row.totalAssigned;
        const perBuildingCols = visibleBuildingRows.map((building) => {
            const bucket = row.perBuilding[building.code] || { planned: 0, assigned: 0 };
            const buildingUnassigned = bucket.planned - bucket.assigned;
            return `<td>${esc(bucket.planned)}</td><td>${esc(bucket.assigned)}</td><td>${esc(buildingUnassigned)}</td>`;
        }).join('');
        return `<tr><td>${esc(row.subjectArea || 'Без области')}</td><td>${esc(row.subjectName)}</td><td>${esc(row.totalPlanned)}</td><td>${esc(row.totalAssigned)}</td><td>${esc(totalUnassigned)}</td>${perBuildingCols}<td>${esc(areaTotals.get(row.subjectArea || 'Без области') || 0)}</td></tr>`;
    }).join('');

    ui.table.innerHTML = `${thead}<tbody>${tbody}</tbody>`;
}

async function refreshStats() {
    try {
        const [curriculum, manual, buildingRows, classRows, subjects] = await Promise.all([
            api('/api/curriculum'),
            api('/api/manual-load'),
            api('/api/buildings'),
            api('/api/classroom-leadership'),
            api('/api/subjects')
        ]);
        curriculumRows = curriculum || [];
        manualRows = manual || [];
        classroomRows = classRows || [];
        subjectCatalog = subjects || [];

        const buildingByCode = new Map();
        (buildingRows || []).forEach((b) => {
            const code = normalizeBuildingCode(b.code);
            if (!code) return;
            buildingByCode.set(code, { ...b, code, name: String(b.name || '').trim() || code });
        });
        buildings = [...buildingByCode.values()].sort((a, b) => String(a.code).localeCompare(String(b.code), 'ru'));

        renderStatsView();
        print({ status: 'ok', loaded: { curriculum: curriculumRows.length, manual: manualRows.length, buildings: buildings.length } });
    } catch (error) {
        print({ error: error.message });
    }
}

function exportStatsCsv() {
    const scopedPath = window.withAcademicYear ? window.withAcademicYear('/api/manual-load/stats/export') : '/api/manual-load/stats/export';
    window.location.href = scopedPath;
}

ui.refreshBtn?.addEventListener('click', refreshStats);
ui.exportBtn?.addEventListener('click', exportStatsCsv);
refreshStats();
