const ovzEsc = (value) => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const ovzDate = (value) => {
    if (!value) return '';
    const parts = String(value).split('-');
    return parts.length === 3 ? `${parts[2]}.${parts[1]}.${parts[0]}` : value;
};
const typeNames = { MSE_CERTIFICATE: 'Справка МСЭ', CPMPC_CONCLUSION: 'Заключение ЦМПК', CPMPC_RECOMMENDATION: 'Рекомендация ЦМПК' };
const stageColors = { NOT_RELEASED: 'red', PRINTED: 'yellow', COMPLETED: 'green' };
const stageNames = { NOT_RELEASED: 'Не печатали', PRINTED: 'Распечатали, не завершили', COMPLETED: 'Этап завершён' };
const recommendationPrograms = {
    DO: 'Основная образовательная программа дошкольного образования.',
    NOO: 'Основная образовательная программа начального образования.',
    OOO: 'Основная образовательная программа общего образования.',
    SOO: 'Основная образовательная программа среднего образования.'
};

const ovzUi = Object.fromEntries([
    'registry-body','registry-head','registry-message','registry-search','registry-refresh','registry-export','certificate-form','certificate-id',
    'student-search','student-options','student-id','student-hint','document-type','accepted-form','accepted-form-field','number-field',
    'document-number','valid-from-field','valid-from','valid-to','stage-field','education-stage','program-field','education-program','program-source',
    'program-other-field','education-program-other','nosology-fields','nosology-letter','nosology-major','nosology-minor','ipra-field','ipra',
    'date-hint','prolongation-fields','prolongation','prolongation-used','prolonged-grade','prolonged-year','correction-fields','direction-body',
    'add-direction','open-specialists','certificate-clear','certificate-message','nosology-form','nosology-id','nosology-code','nosology-active',
    'nosology-body','new-ppk','ppk-form','ppk-id','ppk-date','ppk-type','ppk-student-search','ppk-student-id','ppk-status','ppk-chair',
    'ppk-secretary','ppk-attendees','ppk-invited','ppk-representative-signature','ppk-agenda','ppk-notes','ppk-decision','ppk-cancel','ppk-message','ppk-body',
    'correction-dialog','correction-detail','edit-dialog','edit-person','edit-documents','edit-add','detail-dialog','detail-title','detail-subtitle',
    'roadmap','stage-content','specialists-dialog','specialist-form','specialist-name','specialist-list','ppk-settings-open',
    'ppk-settings-dialog','ppk-settings-form','ppk-settings-chair','ppk-settings-chair-position','ppk-settings-secretary',
    'ppk-settings-secretary-position','ppk-settings-attendees','ppk-settings-add-attendee','ppk-settings-cancel','ppk-settings-message'
].map((name) => [name.replaceAll('-', '_'), document.getElementById(`ovz-${name}`)]));

let ovzStudents = [];
let ovzSpecialists = [];
let ovzRegistry = [];
let ovzDocuments = [];
let currentDossier = null;
let ovzEducationPrograms = [];
let ovzPpkEmployees = [];
let ovzRegistrySort = [];
const ovzRegistryCollator = new Intl.Collator('ru', { numeric: true, sensitivity: 'base' });

function ovzYearPath(path) { return window.withAcademicYear ? window.withAcademicYear(path) : path; }
async function ovzApi(path, options = {}) {
    const response = await fetch(ovzYearPath(path), options);
    const text = await response.text(); let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) throw new Error(body?.message || body?.error || text || `HTTP ${response.status}`);
    return body;
}
async function ovzDownload(path, options = {}, fallback = 'document') {
    const response = await fetch(ovzYearPath(path), options);
    if (!response.ok) { const text = await response.text(); throw new Error(text || `HTTP ${response.status}`); }
    const blob = await response.blob(); const encoded = response.headers.get('Content-Disposition')?.split("filename*=UTF-8''")[1];
    const link = document.createElement('a'); link.href = URL.createObjectURL(blob);
    link.download = encoded ? decodeURIComponent(encoded) : fallback; document.body.appendChild(link); link.click(); link.remove();
    URL.revokeObjectURL(link.href);
}
async function waitAuth() {
    for (let i = 0; i < 100 && !window.tarificationAuth; i += 1) await new Promise((resolve) => setTimeout(resolve, 30));
}

function studentLabel(student) {
    return `${student.fullName} — ${student.className || 'без класса'}${student.birthDate ? ` — д.р. ${ovzDate(student.birthDate)}` : ''} — ФК ${student.studentId}`;
}
function findStudent(text) {
    const normalized = String(text || '').trim().toLocaleLowerCase('ru');
    return ovzStudents.find((s) => studentLabel(s).toLocaleLowerCase('ru') === normalized)
        || ovzStudents.find((s) => String(s.fullName).toLocaleLowerCase('ru') === normalized) || null;
}
function setStudent(student, prefix = '') {
    const search = prefix ? ovzUi.ppk_student_search : ovzUi.student_search;
    const id = prefix ? ovzUi.ppk_student_id : ovzUi.student_id;
    search.value = student ? studentLabel(student) : ''; id.value = student?.studentId || '';
    if (!prefix) ovzUi.student_hint.textContent = student ? `Выбрана постоянная карточка ФК ${student.studentId}.` : 'Выберите ребёнка из подсказки.';
}
function resolveStudent(prefix = '') {
    const student = findStudent(prefix ? ovzUi.ppk_student_search.value : ovzUi.student_search.value);
    setStudent(student, prefix); return student;
}

function ppkEmployee(employeeId) {
    return ovzPpkEmployees.find((employee) => Number(employee.employeeId) === Number(employeeId)) || null;
}
function ppkEmployeeOptions(selectedId = '') {
    return '<option value="">Выберите сотрудника</option>' + ovzPpkEmployees.map((employee) =>
        `<option value="${employee.employeeId}" ${Number(employee.employeeId) === Number(selectedId) ? 'selected' : ''}>${ovzEsc(employee.fullName)}</option>`
    ).join('');
}
function ppkPosition(employeeId) {
    return ppkEmployee(employeeId)?.position || 'Должность в кадровой карточке не указана';
}
function updatePpkRolePosition(role) {
    const select = role === 'chair' ? ovzUi.ppk_settings_chair : ovzUi.ppk_settings_secretary;
    const field = role === 'chair' ? ovzUi.ppk_settings_chair_position : ovzUi.ppk_settings_secretary_position;
    field.value = select.value ? ppkPosition(select.value) : '';
}
function addPpkAttendee(employeeId = '') {
    const row = document.createElement('div');
    row.className = 'ovz-commission-member-row';
    row.dataset.ppkAttendeeRow = '';
    row.innerHTML = `<select data-ppk-attendee-employee required>${ppkEmployeeOptions(employeeId)}</select>
        <input data-ppk-attendee-position type="text" value="${ovzEsc(employeeId ? ppkPosition(employeeId) : '')}" readonly aria-label="Должность">
        <button data-ppk-attendee-remove type="button" class="danger">Удалить</button>`;
    ovzUi.ppk_settings_attendees.appendChild(row);
}
function renderPpkAttendees(members = []) {
    ovzUi.ppk_settings_attendees.innerHTML = '';
    members.forEach((member) => addPpkAttendee(member.employeeId));
    if (!members.length) addPpkAttendee();
}
function selectedPpkAttendeeIds() {
    return Array.from(ovzUi.ppk_settings_attendees.querySelectorAll('[data-ppk-attendee-employee]'))
        .map((select) => Number(select.value) || null).filter(Boolean);
}

function showOvzTab(name) {
    document.querySelectorAll('[data-ovz-tab]').forEach((button) => button.classList.toggle('active', button.dataset.ovzTab === name));
    document.querySelectorAll('[data-ovz-pane]').forEach((pane) => pane.style.display = pane.dataset.ovzPane === name ? '' : 'none');
    window.location.hash = name;
    if (name === 'registry') loadRegistry();
    if (name === 'nosologies') loadNosologies();
    if (name === 'ppk') loadPpk();
}

async function loadReferences() {
    const data = await ovzApi('/api/contingent/special-support/references');
    ovzStudents = data.students || [];
    ovzUi.student_options.innerHTML = ovzStudents.map((student) => `<option value="${ovzEsc(studentLabel(student))}"></option>`).join('');
    ovzSpecialists = await ovzApi('/api/contingent/special-support/correction-specialists');
    renderSpecialistList();
}

async function loadRegistry() {
    ovzUi.registry_message.textContent = 'Загрузка…';
    try {
        [ovzRegistry, ovzDocuments] = await Promise.all([
            ovzApi('/api/ovz/registry'), ovzApi('/api/contingent/special-support/documents')
        ]);
        renderRegistry();
        ovzUi.registry_message.textContent = `Личных дел: ${ovzRegistry.length}. Документов: ${ovzDocuments.length}.`;
    } catch (error) { ovzUi.registry_message.textContent = `Ошибка: ${error.message}`; }
}
function ovzRegistrySortValue(item, key) {
    if (key === 'correctionDirections') return (item.correctionDirections || []).length;
    if (key === 'stages') return (item.stages || []).filter((stage) => stage.status === 'COMPLETED').length;
    return item[key];
}
function ovzCompareRegistryValues(left, right, direction) {
    const leftEmpty = left === null || left === undefined || left === '';
    const rightEmpty = right === null || right === undefined || right === '';
    if (leftEmpty || rightEmpty) return leftEmpty === rightEmpty ? 0 : (leftEmpty ? 1 : -1);
    let compared;
    if (typeof left === 'number' && typeof right === 'number') compared = left - right;
    else if (typeof left === 'boolean' && typeof right === 'boolean') compared = Number(left) - Number(right);
    else compared = ovzRegistryCollator.compare(String(left), String(right));
    return direction === 'asc' ? compared : -compared;
}
function registryRowsForView() {
    const needle = String(ovzUi.registry_search.value || '').trim().toLocaleLowerCase('ru');
    const indexedRows = ovzRegistry.filter((item) => !needle || String(item.fullName || '').toLocaleLowerCase('ru').includes(needle))
        .map((item, originalIndex) => ({ item, originalIndex }));
    if (ovzRegistrySort.length) indexedRows.sort((left, right) => {
        for (const rule of ovzRegistrySort) {
            const compared = ovzCompareRegistryValues(ovzRegistrySortValue(left.item, rule.key), ovzRegistrySortValue(right.item, rule.key), rule.direction);
            if (compared) return compared;
        }
        return left.originalIndex - right.originalIndex;
    });
    return indexedRows.map(({ item }) => item);
}
function updateRegistrySortHeaders() {
    ovzUi.registry_head.querySelectorAll('[data-ovz-registry-sort]').forEach((button) => {
        const index = ovzRegistrySort.findIndex((rule) => rule.key === button.dataset.ovzRegistrySort);
        const rule = index < 0 ? null : ovzRegistrySort[index];
        button.querySelector('[data-ovz-sort-indicator]').textContent = rule ? `${rule.direction === 'asc' ? '↑' : '↓'}${index + 1}` : '';
        button.setAttribute('aria-label', `${button.textContent.trim()}${rule ? `, сортировка ${rule.direction === 'asc' ? 'по возрастанию' : 'по убыванию'}, приоритет ${index + 1}` : ', без сортировки'}`);
    });
}
function toggleRegistrySort(key) {
    const index = ovzRegistrySort.findIndex((rule) => rule.key === key);
    if (index < 0) ovzRegistrySort.push({ key, direction: 'asc' });
    else if (ovzRegistrySort[index].direction === 'asc') ovzRegistrySort[index].direction = 'desc';
    else ovzRegistrySort.splice(index, 1);
    renderRegistry();
}
function renderRegistry() {
    const rows = registryRowsForView();
    updateRegistrySortHeaders();
    ovzUi.registry_body.innerHTML = rows.length ? rows.map((item) => `<tr>
        <td>${item.studentId}</td><td>${ovzEsc(item.fullName)}</td><td>${ovzEsc(item.className)}</td>
        <td>${item.mse ? 'Да' : 'Нет'}</td><td>${item.conclusion ? 'Да' : 'Нет'}</td><td>${item.recommendation ? 'Да' : 'Нет'}</td>
        <td>${ovzDate(item.validTo)}</td><td><button class="secondary" data-correction="${item.studentId}">Подробнее</button></td>
        <td><div class="ovz-mini-roadmap">${(item.stages || []).map((s) => `<span class="ovz-status-dot ${stageColors[s.status]}" title="${ovzEsc(s.label)}: ${stageNames[s.status]}"></span>`).join('')}</div></td>
        <td><div class="ovz-actions"><button data-edit-dossier="${item.studentId}">Изменить</button>
            <button class="danger" data-delete-dossier="${item.studentId}">Удалить</button><button class="secondary" data-detail="${item.studentId}">Подробно</button></div></td>
    </tr>`).join('') : `<tr><td colspan="10" class="muted">${ovzUi.registry_search.value.trim() ? 'По указанному ФИО ничего не найдено.' : 'В реестре пока нет справок.'}</td></tr>`;
}

function openCorrection(studentId) {
    const item = ovzRegistry.find((row) => Number(row.studentId) === Number(studentId));
    ovzUi.correction_detail.innerHTML = (item?.correctionDirections || []).length
        ? `<h4>${ovzEsc(item.fullName)}</h4>` + item.correctionDirections.map((d) => `<article class="ovz-info-card"><strong>${ovzEsc(d.specialistName)}</strong><p>${ovzEsc(d.tasks)}</p></article>`).join('')
        : '<p class="muted">Коррекционная работа не указана.</p>';
    ovzUi.correction_dialog.showModal();
}
function openEditDossier(studentId) {
    const item = ovzRegistry.find((row) => Number(row.studentId) === Number(studentId)); if (!item) return;
    ovzUi.edit_person.textContent = `${item.className} — ${item.fullName} — ФК ${item.studentId}`;
    ovzUi.edit_documents.innerHTML = item.documents.map((doc) => `<article class="ovz-info-card"><div><strong>${typeNames[doc.documentType]}</strong><br><span class="muted">до ${ovzDate(doc.validTo)}</span></div><button data-edit-document="${doc.id}">Изменить</button></article>`).join('');
    const types = [];
    if (!item.mse) types.push(['MSE_CERTIFICATE', 'Добавить МСЭ']);
    if (!item.conclusion && !item.recommendation) types.push(['CPMPC_CONCLUSION', 'Добавить заключение ЦМПК'], ['CPMPC_RECOMMENDATION', 'Добавить рекомендацию ЦМПК']);
    ovzUi.edit_add.innerHTML = types.map(([type, label]) => `<button class="secondary" data-add-document="${type}" data-student="${studentId}">${label}</button>`).join('');
    ovzUi.edit_dialog.showModal();
}

function resetCertificate() {
    ovzUi.certificate_form.reset(); ovzUi.certificate_id.value = ''; ovzUi.student_id.value = ''; ovzUi.student_hint.textContent = '';
    ovzUi.document_type.value = 'MSE_CERTIFICATE'; fillYears(); updateCertificateType(); ovzUi.direction_body.innerHTML = '';
    ovzUi.certificate_message.textContent = '';
}
function fillYears() {
    const current = new Date().getFullYear();
    ovzUi.prolonged_grade.innerHTML = Array.from({length: 11}, (_, i) => `<option value="${i + 1}">${i + 1} класс</option>`).join('');
    ovzUi.prolonged_year.innerHTML = Array.from({length: 5}, (_, i) => `${current - 1 + i}/${current + i}`)
        .map((year) => `<option value="${year}">${year}</option>`).join('');
}
function updateCertificateType() {
    const type = ovzUi.document_type.value; const mse = type === 'MSE_CERTIFICATE'; const conclusion = type === 'CPMPC_CONCLUSION';
    const cpmpc = !mse;
    ovzUi.accepted_form.innerHTML = mse ? '<option value="COPY">Копия</option>' : conclusion
        ? '<option value="ORIGINAL">Оригинал</option><option value="ELECTRONIC_COPY">Электронная копия</option>'
        : '<option value="COPY">Копия</option>';
    ovzUi.number_field.style.display = conclusion ? '' : 'none'; ovzUi.valid_from_field.style.display = type === 'CPMPC_RECOMMENDATION' ? 'none' : '';
    ovzUi.stage_field.style.display = cpmpc ? '' : 'none'; ovzUi.program_field.style.display = cpmpc ? '' : 'none';
    ovzUi.nosology_fields.style.display = conclusion ? '' : 'none'; ovzUi.ipra_field.style.display = mse ? '' : 'none';
    ovzUi.prolongation_fields.style.display = conclusion ? '' : 'none'; ovzUi.correction_fields.style.display = cpmpc ? '' : 'none';
    if (mse) { ovzEducationPrograms = []; ovzUi.education_program.innerHTML = ''; ovzUi.program_other_field.style.display = 'none'; ovzUi.program_source.style.display = 'none'; }
    else refreshEducationDefaults();
}
async function refreshEducationDefaults() {
    const studentId = Number(ovzUi.student_id.value || 0); const type = ovzUi.document_type.value;
    if (!studentId || type === 'MSE_CERTIFICATE') return;
    const params = new URLSearchParams({ studentId, documentType: type, prolongationAvailable: ovzUi.prolongation.value, prolongationUsed: ovzUi.prolongation_used.value });
    try {
        const data = await ovzApi(`/api/contingent/special-support/documents/education-defaults?${params}`);
        ovzUi.education_stage.value = data.educationStage; ovzUi.valid_to.value = data.validTo || ''; ovzUi.date_hint.textContent = data.message || '';
        if (type === 'CPMPC_RECOMMENDATION') {
            ovzEducationPrograms = [];
            ovzUi.education_program.innerHTML = `<option value="${ovzEsc(recommendationPrograms[data.educationStage])}">${ovzEsc(recommendationPrograms[data.educationStage])}</option>`;
        } else {
            ovzEducationPrograms = data.educationPrograms || [];
            ovzUi.education_program.innerHTML = ovzEducationPrograms.map((p) => `<option value="${ovzEsc(p.name)}">${ovzEsc(p.name)}</option>`).join('') + '<option value="__OTHER__">Другое</option>';
        }
        updateProgramOther();
    } catch (error) { ovzUi.date_hint.textContent = `Не удалось рассчитать: ${error.message}`; }
}
function updateProgramOther() {
    ovzUi.program_other_field.style.display = ovzUi.education_program.value === '__OTHER__' ? '' : 'none';
    const source = ovzEducationPrograms.find((item) => item.name === ovzUi.education_program.value)?.sourceUrl;
    ovzUi.program_source.style.display = source ? '' : 'none';
    ovzUi.program_source.href = source || '#';
}
function addDirection(data = {}) {
    const row = document.createElement('tr');
    row.innerHTML = `<td><select data-direction-specialist>${ovzSpecialists.filter((s) => s.active).map((s) => `<option value="${s.id}">${ovzEsc(s.name)}</option>`).join('')}</select></td>
        <td><textarea data-direction-tasks rows="2" placeholder="Задачи специалиста"></textarea></td><td><button type="button" class="danger" data-remove-direction>Удалить</button></td>`;
    if (data.specialistId) row.querySelector('[data-direction-specialist]').value = data.specialistId;
    row.querySelector('[data-direction-tasks]').value = data.tasks || ''; ovzUi.direction_body.appendChild(row);
}
function certificatePayload() {
    const type = ovzUi.document_type.value;
    const programCustom = ovzUi.education_program.value === '__OTHER__';
    return { id: Number(ovzUi.certificate_id.value) || null, studentId: Number(ovzUi.student_id.value), documentType: type,
        acceptedForm: ovzUi.accepted_form.value, documentNumber: ovzUi.document_number.value || null,
        validFrom: type === 'CPMPC_RECOMMENDATION' ? null : (ovzUi.valid_from.value || null), validTo: ovzUi.valid_to.value || null,
        nosologyCode: type === 'CPMPC_CONCLUSION' ? `${ovzUi.nosology_letter.value}${ovzUi.nosology_major.value}.${ovzUi.nosology_minor.value}` : null,
        educationStage: type === 'MSE_CERTIFICATE' ? null : ovzUi.education_stage.value,
        educationProgram: type === 'MSE_CERTIFICATE' ? null : (programCustom ? ovzUi.education_program_other.value : ovzUi.education_program.value),
        educationProgramCustom: programCustom, prolongationAvailable: type === 'CPMPC_CONCLUSION' && ovzUi.prolongation.value === 'true',
        prolongationUsed: type === 'CPMPC_CONCLUSION' && ovzUi.prolongation_used.value === 'true',
        prolongedGrade: Number(ovzUi.prolonged_grade.value) || null, prolongedAcademicYear: ovzUi.prolonged_year.value || null,
        ipraPresent: type === 'MSE_CERTIFICATE' && ovzUi.ipra.value === 'true',
        correctionDirections: type === 'MSE_CERTIFICATE' ? [] : Array.from(ovzUi.direction_body.querySelectorAll('tr')).map((row) => ({ specialistId: Number(row.querySelector('[data-direction-specialist]').value), tasks: row.querySelector('[data-direction-tasks]').value })) };
}
async function editDocument(id) {
    const doc = ovzDocuments.find((item) => Number(item.id) === Number(id)); if (!doc) return;
    resetCertificate(); ovzUi.certificate_id.value = doc.id; setStudent(ovzStudents.find((s) => Number(s.studentId) === Number(doc.studentId)));
    ovzUi.document_type.value = doc.documentType; updateCertificateType(); await refreshEducationDefaults();
    ovzUi.accepted_form.value = doc.acceptedForm; ovzUi.document_number.value = doc.documentNumber || ''; ovzUi.valid_from.value = doc.validFrom || ''; ovzUi.valid_to.value = doc.validTo || '';
    if (doc.educationStage) ovzUi.education_stage.value = doc.educationStage;
    if (doc.educationProgram) {
        const option = Array.from(ovzUi.education_program.options).find((item) => item.value === doc.educationProgram);
        if (option) ovzUi.education_program.value = doc.educationProgram;
        else { ovzUi.education_program.value = '__OTHER__'; ovzUi.education_program_other.value = doc.educationProgram; }
    }
    updateProgramOther();
    const match = String(doc.nosologyCode || '').match(/^([ИО])([0-9])\.([0-9])$/);
    if (match) { ovzUi.nosology_letter.value = match[1]; ovzUi.nosology_major.value = match[2]; ovzUi.nosology_minor.value = match[3]; }
    ovzUi.ipra.value = String(Boolean(doc.ipraPresent)); ovzUi.prolongation.value = String(Boolean(doc.prolongationAvailable)); ovzUi.prolongation_used.value = String(Boolean(doc.prolongationUsed));
    if (doc.prolongedGrade) ovzUi.prolonged_grade.value = doc.prolongedGrade; if (doc.prolongedAcademicYear) ovzUi.prolonged_year.value = doc.prolongedAcademicYear;
    ovzUi.direction_body.innerHTML = ''; (doc.correctionDirections || []).forEach(addDirection); ovzUi.edit_dialog.close(); showOvzTab('certificates');
}

async function loadNosologies() {
    const rows = await ovzApi('/api/contingent/special-support/nosologies');
    ovzUi.nosology_body.innerHTML = rows.map((item) => `<tr><td>${ovzEsc(item.code)}</td><td>${item.active ? 'Действует' : 'Отключена'}</td><td><button class="secondary" data-edit-nosology="${item.id}" data-code="${ovzEsc(item.code)}" data-active="${item.active}">Изменить</button></td></tr>`).join('');
}
function renderSpecialistList() { ovzUi.specialist_list.innerHTML = ovzSpecialists.map((s) => `<div class="ovz-info-card"><strong>${ovzEsc(s.name)}</strong><span>${s.builtIn ? 'системный' : 'добавлен вручную'}</span></div>`).join(''); }

async function openDetail(studentId) {
    currentDossier = await ovzApi(`/api/ovz/dossiers/${studentId}`);
    ovzUi.detail_title.textContent = currentDossier.fullName; ovzUi.detail_subtitle.textContent = `${currentDossier.className} — ФК ${currentDossier.studentId}`;
    renderRoadmap(); ovzUi.detail_dialog.showModal(); openStage(currentDossier.stages[0].stage);
}
function renderRoadmap() {
    ovzUi.roadmap.innerHTML = currentDossier.stages.map((stage, index) => `<button data-roadmap-stage="${stage.stage}" class="ovz-roadmap-step">
        <span class="ovz-roadmap-number ${stageColors[stage.status]}">${index + 1}</span><span><strong>${ovzEsc(stage.label)}</strong><small>${stageNames[stage.status]}</small></span></button>`).join('');
}
function stageSelect(stage) { return `<select data-stage-select="${stage.stage}"><option value="NOT_RELEASED" ${stage.status === 'NOT_RELEASED' ? 'selected' : ''}>Не печатали</option><option value="PRINTED" ${stage.status === 'PRINTED' ? 'selected' : ''}>Распечатали, не завершили</option><option value="COMPLETED" ${stage.status === 'COMPLETED' ? 'selected' : ''}>Этап завершён</option></select>`; }
function openStage(name) {
    const stage = currentDossier.stages.find((item) => item.stage === name); if (!stage) return;
    ovzUi.roadmap.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.roadmapStage === name));
    if (name === 'CERTIFICATE') {
        ovzUi.stage_content.innerHTML = `<h3>Справки</h3>${currentDossier.documents.map((d) => `<article class="ovz-info-card"><strong>${typeNames[d.documentType]}</strong><span>действует до ${ovzDate(d.validTo)}</span></article>`).join('')}`;
    } else if (name === 'APPLICATION') {
        ovzUi.stage_content.innerHTML = `<h3>Заявление</h3><p class="muted">Специалисты перенесены из заключения ЦМПК. По умолчанию установлено согласие.</p>
            <div id="ovz-application-choices">${(currentDossier.applicationChoices || []).map((c) => `<article class="ovz-application-choice"><div><strong>${ovzEsc(c.specialistName)}</strong><p>${ovzEsc(c.tasks)}</p></div><select data-application-agreed data-name="${ovzEsc(c.specialistName)}" data-tasks="${ovzEsc(c.tasks || '')}"><option value="true" ${c.agreed ? 'selected' : ''}>Согласен</option><option value="false" ${!c.agreed ? 'selected' : ''}>Отказ</option></select></article>`).join('') || '<p class="muted">В заключении не указаны специалисты.</p>'}</div><button data-application-received>${stage.status === 'COMPLETED' ? 'Получено ✓' : 'Получено'}</button>`;
    } else if (name === 'CONSENT') {
        ovzUi.stage_content.innerHTML = `<h3>Согласие</h3><p class="muted">Шаблон содержит согласие на психолого-педагогическую диагностику и согласие на сопровождение службы СППС.</p>
            <div class="row controls-row"><button data-download-consent>Скачать шаблон Word</button><button data-consent-received class="secondary">Согласие получено</button></div><p>${stageSelect(stage)}</p>`;
    } else if (name === 'PPK_APPOINTMENT' || name === 'PPK_IOM') {
        const type = name === 'PPK_IOM' ? 'IOM' : 'APPOINTMENT';
        const protocols = (currentDossier.ppkProtocols || []).filter((p) => p.protocolType === type);
        ovzUi.stage_content.innerHTML = `<h3>${ovzEsc(stage.label)}</h3>${protocols.map((p) => `<article class="ovz-info-card"><strong>${ovzEsc(p.protocolNumber)}</strong><span>${ovzDate(p.meetingDate)}</span><button data-download-ppk="${p.id}" class="secondary">Скачать Word</button></article>`).join('') || '<p class="muted">Протокол ещё не создан.</p>'}<button data-create-child-ppk="${type}">Создать ППк</button><p>${stageSelect(stage)}</p>`;
    } else {
        ovzUi.stage_content.innerHTML = `<h3>${ovzEsc(stage.label)}</h3><p class="muted">Фиксируется состояние этапа. Конструктор документа будет подключён отдельным шагом.</p>${stageSelect(stage)}<button data-save-stage="${name}">Сохранить состояние</button>`;
    }
}
async function updateStage(studentId, stage, status) {
    await ovzApi(`/api/ovz/dossiers/${studentId}/stages`, { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({stage, status}) });
    currentDossier = await ovzApi(`/api/ovz/dossiers/${studentId}`); renderRoadmap(); openStage(stage); await loadRegistry();
}

async function applyPpkDefaults(studentId = null) {
    const suffix = studentId ? `?studentId=${encodeURIComponent(studentId)}` : '';
    const defaults = await ovzApi(`/api/ovz/ppk/defaults${suffix}`);
    ovzUi.ppk_chair.value = defaults.chairName || '';
    ovzUi.ppk_secretary.value = defaults.secretaryName || '';
    ovzUi.ppk_attendees.value = defaults.attendees || '';
    ovzUi.ppk_invited.value = defaults.representativeName || '';
    ovzUi.ppk_representative_signature.checked = defaults.representativeSignatureName != null;
    ovzUi.ppk_agenda.value = defaults.agenda || '';
    ovzUi.ppk_notes.value = defaults.meetingNotes || '';
    ovzUi.ppk_decision.value = defaults.decisionText || '';
    ovzUi.ppk_message.textContent = defaults.message || '';
}
async function resetPpk(studentId = null, type = 'APPOINTMENT') {
    ovzUi.ppk_form.reset(); ovzUi.ppk_id.value = ''; ovzUi.ppk_date.value = new Date().toISOString().slice(0, 10); ovzUi.ppk_type.value = type;
    setStudent(studentId ? ovzStudents.find((s) => Number(s.studentId) === Number(studentId)) : null, 'ppk');
    ovzUi.ppk_form.style.display = ''; ovzUi.ppk_message.textContent = '';
    await applyPpkDefaults(studentId);
}
async function loadPpk() {
    const rows = await ovzApi('/api/ovz/ppk');
    ovzUi.ppk_body.innerHTML = rows.length ? rows.map((p) => `<tr><td>${ovzEsc(p.protocolNumber)}</td><td>${ovzDate(p.meetingDate)}</td><td>${p.protocolType === 'IOM' ? 'ИОМ' : 'Назначение'}</td><td>${ovzEsc(p.studentFullName || '')}</td><td>${ovzEsc(p.className || '')}</td><td>${stageNames[p.status]}</td><td><div class="ovz-actions"><button data-ppk-edit="${p.id}">Изменить</button><button class="secondary" data-ppk-download="${p.id}">Word</button><button class="danger" data-ppk-delete="${p.id}">Удалить</button></div></td></tr>`).join('') : '<tr><td colspan="7" class="muted">Протоколов пока нет.</td></tr>';
    ovzUi.ppk_body.dataset.rows = JSON.stringify(rows);
}
function ppkPayload() { return { id: Number(ovzUi.ppk_id.value) || null, meetingDate: ovzUi.ppk_date.value || null, protocolType: ovzUi.ppk_type.value,
    studentId: Number(ovzUi.ppk_student_id.value) || null, status: ovzUi.ppk_status.value, chairName: ovzUi.ppk_chair.value, secretaryName: ovzUi.ppk_secretary.value,
    attendees: ovzUi.ppk_attendees.value, representativeName: ovzUi.ppk_invited.value.trim(),
    representativeSignatureName: ovzUi.ppk_representative_signature.checked ? ovzUi.ppk_invited.value.trim() : '',
    agenda: ovzUi.ppk_agenda.value, meetingNotes: ovzUi.ppk_notes.value, decisionText: ovzUi.ppk_decision.value };
}

document.querySelectorAll('[data-ovz-tab]').forEach((button) => button.addEventListener('click', () => showOvzTab(button.dataset.ovzTab)));
document.querySelectorAll('[data-dialog-close]').forEach((button) => button.addEventListener('click', () => ovzUi[`${button.dataset.dialogClose}_dialog`]?.close()));
ovzUi.registry_search.addEventListener('input', renderRegistry); ovzUi.registry_refresh.addEventListener('click', loadRegistry);
ovzUi.registry_head.addEventListener('click', (event) => {
    const button = event.target.closest('[data-ovz-registry-sort]');
    if (button) toggleRegistrySort(button.dataset.ovzRegistrySort);
});
ovzUi.registry_export.addEventListener('click', async () => {
    const visibleStudentIds = registryRowsForView().map((item) => item.studentId);
    try {
        await ovzDownload('/api/ovz/registry/export', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(visibleStudentIds) }, 'Реестр_ОВЗ.xlsx');
    } catch (error) { ovzUi.registry_message.textContent = `Ошибка выгрузки: ${error.message}`; }
});
ovzUi.registry_body.addEventListener('click', async (event) => {
    const target = event.target;
    if (target.dataset.correction) openCorrection(target.dataset.correction);
    if (target.dataset.editDossier) openEditDossier(target.dataset.editDossier);
    if (target.dataset.detail) await openDetail(target.dataset.detail);
    if (target.dataset.deleteDossier && confirm('Удалить все справки и дорожную карту ребёнка? Протоколы ППк останутся в журнале без привязки.')) {
        await ovzApi(`/api/ovz/dossiers/${target.dataset.deleteDossier}`, {method:'DELETE'}); await loadRegistry();
    }
});
ovzUi.edit_dialog.addEventListener('click', (event) => {
    if (event.target.dataset.editDocument) editDocument(event.target.dataset.editDocument);
    if (event.target.dataset.addDocument) { resetCertificate(); setStudent(ovzStudents.find((s) => Number(s.studentId) === Number(event.target.dataset.student))); ovzUi.document_type.value = event.target.dataset.addDocument; updateCertificateType(); refreshEducationDefaults(); ovzUi.edit_dialog.close(); showOvzTab('certificates'); }
});
ovzUi.student_search.addEventListener('change', () => { resolveStudent(); refreshEducationDefaults(); });
ovzUi.student_search.addEventListener('input', () => { const student = findStudent(ovzUi.student_search.value); if (student) setStudent(student); else ovzUi.student_id.value = ''; });
ovzUi.ppk_student_search.addEventListener('change', async () => { const student = resolveStudent('ppk'); await applyPpkDefaults(student?.studentId || null); });
ovzUi.document_type.addEventListener('change', updateCertificateType); ovzUi.education_program.addEventListener('change', updateProgramOther);
ovzUi.prolongation.addEventListener('change', refreshEducationDefaults); ovzUi.prolongation_used.addEventListener('change', refreshEducationDefaults);
ovzUi.add_direction.addEventListener('click', () => addDirection()); ovzUi.direction_body.addEventListener('click', (event) => event.target.closest('[data-remove-direction]')?.closest('tr')?.remove());
ovzUi.certificate_clear.addEventListener('click', resetCertificate); ovzUi.open_specialists.addEventListener('click', () => ovzUi.specialists_dialog.showModal());
ovzUi.certificate_form.addEventListener('submit', async (event) => { event.preventDefault(); resolveStudent();
    try { if (!ovzUi.student_id.value) throw new Error('Выберите ребёнка из подсказки'); await ovzApi('/api/contingent/special-support/documents', {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(certificatePayload())}); ovzUi.certificate_message.textContent = 'Справка сохранена.'; await loadRegistry(); resetCertificate(); showOvzTab('registry'); } catch (error) { ovzUi.certificate_message.textContent = `Ошибка: ${error.message}`; }
});
ovzUi.nosology_form.addEventListener('submit', async (event) => { event.preventDefault(); await ovzApi('/api/contingent/special-support/nosologies', {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:Number(ovzUi.nosology_id.value)||null,code:ovzUi.nosology_code.value,active:ovzUi.nosology_active.checked})}); ovzUi.nosology_form.reset(); ovzUi.nosology_active.checked = true; await loadNosologies(); });
ovzUi.nosology_body.addEventListener('click', (event) => { if (!event.target.dataset.editNosology) return; ovzUi.nosology_id.value = event.target.dataset.editNosology; ovzUi.nosology_code.value = event.target.dataset.code; ovzUi.nosology_active.checked = event.target.dataset.active === 'true'; });
ovzUi.specialist_form.addEventListener('submit', async (event) => { event.preventDefault(); await ovzApi('/api/contingent/special-support/correction-specialists',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:ovzUi.specialist_name.value})}); ovzUi.specialist_name.value=''; ovzSpecialists=await ovzApi('/api/contingent/special-support/correction-specialists');renderSpecialistList(); });
ovzUi.roadmap.addEventListener('click', (event) => { const button = event.target.closest('[data-roadmap-stage]'); if (button) openStage(button.dataset.roadmapStage); });
ovzUi.stage_content.addEventListener('click', async (event) => {
    const target = event.target; const studentId = currentDossier.studentId;
    if (target.matches('[data-application-received]')) { const choices = Array.from(document.querySelectorAll('[data-application-agreed]')).map((el)=>({specialistName:el.dataset.name,tasks:el.dataset.tasks,agreed:el.value==='true'})); await ovzApi(`/api/ovz/dossiers/${studentId}/application`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(choices)}); currentDossier=await ovzApi(`/api/ovz/dossiers/${studentId}`);renderRoadmap();openStage('APPLICATION');await loadRegistry(); }
    if (target.matches('[data-download-consent]')) { await ovzDownload(`/api/ovz/dossiers/${studentId}/consent`,{method:'POST'},'Согласие_на_диагностику_и_сопровождение_СППС.docx'); currentDossier=await ovzApi(`/api/ovz/dossiers/${studentId}`);renderRoadmap();openStage('CONSENT'); }
    if (target.matches('[data-consent-received]')) await updateStage(studentId,'CONSENT','COMPLETED');
    if (target.dataset.saveStage) await updateStage(studentId,target.dataset.saveStage,document.querySelector(`[data-stage-select="${target.dataset.saveStage}"]`).value);
    if (target.dataset.createChildPpk) { ovzUi.detail_dialog.close(); showOvzTab('ppk'); await resetPpk(studentId,target.dataset.createChildPpk); }
    if (target.dataset.downloadPpk) await ovzDownload(`/api/ovz/ppk/${target.dataset.downloadPpk}/document`,{},'ППк.docx');
});
ovzUi.new_ppk.addEventListener('click', async () => resetPpk()); ovzUi.ppk_cancel.addEventListener('click', () => ovzUi.ppk_form.style.display='none');
ovzUi.ppk_form.addEventListener('submit', async (event) => { event.preventDefault(); resolveStudent('ppk'); try { const saved=await ovzApi('/api/ovz/ppk',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(ppkPayload())}); ovzUi.ppk_message.textContent=`Сохранён ${saved.protocolNumber}`;await loadPpk();ovzUi.ppk_form.style.display='none'; } catch(error){ovzUi.ppk_message.textContent=`Ошибка: ${error.message}`;} });
ovzUi.ppk_body.addEventListener('click', async (event) => { const rows=JSON.parse(ovzUi.ppk_body.dataset.rows||'[]');
    if(event.target.dataset.ppkDownload) await ovzDownload(`/api/ovz/ppk/${event.target.dataset.ppkDownload}/document`,{},'ППк.docx');
    if(event.target.dataset.ppkDelete&&confirm('Удалить протокол ППк?')){await ovzApi(`/api/ovz/ppk/${event.target.dataset.ppkDelete}`,{method:'DELETE'});await loadPpk();}
    if(event.target.dataset.ppkEdit){const p=rows.find((x)=>Number(x.id)===Number(event.target.dataset.ppkEdit));await resetPpk(p.studentId,p.protocolType);ovzUi.ppk_id.value=p.id;ovzUi.ppk_date.value=p.meetingDate;ovzUi.ppk_status.value=p.status;ovzUi.ppk_chair.value=p.chairName||'';ovzUi.ppk_secretary.value=p.secretaryName||'';ovzUi.ppk_attendees.value=p.attendees||'';const hasSavedRepresentative=p.representativeName!=null||p.representativeSignatureName!=null;if(hasSavedRepresentative){ovzUi.ppk_invited.value=p.representativeName||'';ovzUi.ppk_representative_signature.checked=Boolean(p.representativeSignatureName);}ovzUi.ppk_agenda.value=p.agenda||'';ovzUi.ppk_notes.value=p.meetingNotes||'';ovzUi.ppk_decision.value=p.decisionText||'';ovzUi.ppk_message.textContent='Редактирование сохранённого протокола.';}
});

ovzUi.ppk_settings_open.addEventListener('click', async () => {
    ovzUi.ppk_settings_message.textContent = 'Загрузка…';
    try {
        const [settings, employees] = await Promise.all([
            ovzApi('/api/ovz/ppk/settings'), ovzApi('/api/ovz/ppk/settings/employees')
        ]);
        ovzPpkEmployees = employees || [];
        ovzUi.ppk_settings_chair.innerHTML = ppkEmployeeOptions(settings.chairEmployeeId);
        ovzUi.ppk_settings_secretary.innerHTML = ppkEmployeeOptions(settings.secretaryEmployeeId);
        ovzUi.ppk_settings_chair.value = settings.chairEmployeeId || '';
        ovzUi.ppk_settings_secretary.value = settings.secretaryEmployeeId || '';
        updatePpkRolePosition('chair'); updatePpkRolePosition('secretary');
        renderPpkAttendees(settings.attendeeMembers || []);
        ovzUi.ppk_settings_message.textContent = '';
        ovzUi.ppk_settings_dialog.showModal();
    } catch (error) { ovzUi.ppk_settings_message.textContent = `Ошибка: ${error.message}`; }
});
ovzUi.ppk_settings_chair.addEventListener('change', () => updatePpkRolePosition('chair'));
ovzUi.ppk_settings_secretary.addEventListener('change', () => updatePpkRolePosition('secretary'));
ovzUi.ppk_settings_add_attendee.addEventListener('click', () => addPpkAttendee());
ovzUi.ppk_settings_attendees.addEventListener('change', (event) => {
    if (!event.target.matches('[data-ppk-attendee-employee]')) return;
    const position = event.target.closest('[data-ppk-attendee-row]').querySelector('[data-ppk-attendee-position]');
    position.value = event.target.value ? ppkPosition(event.target.value) : '';
});
ovzUi.ppk_settings_attendees.addEventListener('click', (event) => {
    if (!event.target.matches('[data-ppk-attendee-remove]')) return;
    event.target.closest('[data-ppk-attendee-row]').remove();
    if (!ovzUi.ppk_settings_attendees.children.length) addPpkAttendee();
});
ovzUi.ppk_settings_cancel.addEventListener('click', () => ovzUi.ppk_settings_dialog.close());
ovzUi.ppk_settings_form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
        await ovzApi('/api/ovz/ppk/settings', {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({
            chairEmployeeId:Number(ovzUi.ppk_settings_chair.value) || null,
            secretaryEmployeeId:Number(ovzUi.ppk_settings_secretary.value) || null,
            attendeeEmployeeIds:selectedPpkAttendeeIds()
        })});
        ovzUi.ppk_settings_message.textContent = 'Стандартная комиссия сохранена.';
    } catch (error) { ovzUi.ppk_settings_message.textContent = `Ошибка: ${error.message}`; }
});

(async function initOvz(){await waitAuth();fillYears();await loadReferences();resetCertificate();await loadRegistry();const requested=String(location.hash||'#registry').slice(1);showOvzTab(['registry','certificates','nosologies','ppk'].includes(requested)?requested:'registry');})();
