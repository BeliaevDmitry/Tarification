const MCKO_UPLOAD_BATCH_BYTES = 24 * 1024 * 1024;
const MCKO_UPLOAD_MAX_FILE_BYTES = 30 * 1024 * 1024;
const MCKO_UPLOAD_BATCH_FILES = 40;
const mckoState = { files: [], resultsLoaded: false, linkingResultId: null, uploading: false };

function esc(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
function currentYear() { return sessionStorage.getItem('tarification.academicYear') || ''; }
async function mckoApi(path, options = {}) {
    const response = await fetch(path, options); const text = await response.text(); let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) {
        if (response.status === 413) {
            throw new Error('Пакет файлов превышает допустимый размер сервера. Крупные файлы оставлены в списке для повторной загрузки.');
        }
        throw new Error(body?.message || body?.error || `Ошибка ${response.status}`);
    }
    return body;
}
function q(params) { const out = new URLSearchParams(); Object.entries(params).forEach(([k,v]) => { if (v !== '' && v != null) out.set(k,v); }); return out.toString(); }
function statusLabel(value) { return ({PROCESSED:'Обработан',PARTIAL:'Частично',FAILED:'Не обработан',PROCESSING:'Обрабатывается'})[value] || value || '—'; }
function linkLabel(value) { return ({LINKED_BY_CODE:'По коду',LINKED_BY_NAME_AND_CLASS:'По ФИО и классу',MANUALLY_LINKED:'Вручную',NOT_FOUND:'Не найден',AMBIGUOUS:'Неоднозначно'})[value] || value || '—'; }
function fmt(value, digits = 1) { return value == null || value === '' ? '—' : Number(value).toLocaleString('ru-RU',{maximumFractionDigits:digits}); }

function setFiles(files) {
    if (mckoState.uploading) return;
    mckoState.files = [...mckoState.files, ...files]; renderFiles();
}
function splitUploadBatches(files) {
    const batches = [], oversized = []; let batch = [], batchBytes = 0;
    const flush = () => { if (batch.length) batches.push(batch); batch = []; batchBytes = 0; };
    files.forEach(file => {
        if (file.size > MCKO_UPLOAD_MAX_FILE_BYTES) { oversized.push(file); return; }
        if (file.size > MCKO_UPLOAD_BATCH_BYTES) { flush(); batches.push([file]); return; }
        if (batch.length && (batchBytes + file.size > MCKO_UPLOAD_BATCH_BYTES || batch.length >= MCKO_UPLOAD_BATCH_FILES)) flush();
        batch.push(file); batchBytes += file.size;
    });
    flush(); return { batches, oversized };
}
function renderFiles() {
    const previewLimit = 100;
    const totalMegabytes = mckoState.files.reduce((sum, file) => sum + file.size, 0) / 1024 / 1024;
    const preview = mckoState.files.slice(0, previewLimit);
    document.getElementById('mcko-file-summary').textContent = mckoState.files.length
        ? `Выбрано файлов: ${mckoState.files.length} · общий размер: ${totalMegabytes.toFixed(1)} МБ. Все выбранные файлы будут отправлены.`
        : 'Файлы ещё не выбраны.';
    document.getElementById('mcko-file-chips').innerHTML = preview.map(file => `<span class="mcko-file-chip">${esc(file.webkitRelativePath || file.name)} · ${(file.size/1024/1024).toFixed(2)} МБ</span>`).join('')
        + (mckoState.files.length > previewLimit ? `<span class="mcko-file-chip">И ещё ${mckoState.files.length - previewLimit} файлов — они тоже будут отправлены</span>` : '');
    document.getElementById('mcko-upload-btn').disabled = !mckoState.files.length || mckoState.uploading;
    document.getElementById('mcko-clear-files').disabled = !mckoState.files.length || mckoState.uploading;
    document.getElementById('mcko-choose-files').disabled = mckoState.uploading;
    document.getElementById('mcko-files').disabled = mckoState.uploading;
}
async function uploadFiles() {
    if (!mckoState.files.length || mckoState.uploading) return;
    const feedback = document.getElementById('mcko-upload-feedback');
    const selected = [...mckoState.files]; const { batches, oversized } = splitUploadBatches(selected);
    if (!batches.length) {
        feedback.textContent = `Не отправлено: ${oversized.length}. Размер каждого такого файла превышает 30 МБ.`;
        return;
    }
    const totals = { filesTotal: 0, filesProcessed: 0, filesFailed: 0, rowsImported: 0 };
    const completedFiles = new Set(), requestErrors = [];
    const year = currentYear(); const suffix = year ? `?academicYear=${encodeURIComponent(year)}` : '';
    mckoState.uploading = true; renderFiles();
    try {
        for (let index = 0; index < batches.length; index++) {
            const batch = batches[index]; const batchMegabytes = batch.reduce((sum, file) => sum + file.size, 0) / 1024 / 1024;
            feedback.textContent = `Пакет ${index + 1} из ${batches.length}: ${batch.length} файлов, ${batchMegabytes.toFixed(1)} МБ…`;
            const form = new FormData(); batch.forEach(file => form.append('files', file));
            try {
                const result = await mckoApi(`/api/vsoko/mcko/imports${suffix}`, {method:'POST',body:form});
                totals.filesTotal += Number(result.filesTotal || 0);
                totals.filesProcessed += Number(result.filesProcessed || 0);
                totals.filesFailed += Number(result.filesFailed || 0);
                totals.rowsImported += Number(result.rowsImported || 0);
                batch.forEach(file => completedFiles.add(file));
                feedback.textContent = `Пакет ${index + 1} из ${batches.length} завершён. Передано серверу ${completedFiles.size} из ${selected.length} файлов.`;
            } catch (error) {
                requestErrors.push(`пакет ${index + 1}: ${error.message}`);
            }
        }
    } finally {
        mckoState.files = selected.filter(file => !completedFiles.has(file));
        document.getElementById('mcko-files').value = '';
        mckoState.uploading = false; renderFiles(); mckoState.resultsLoaded = false;
    }
    try { await loadHistory(); } catch (error) { requestErrors.push(`история: ${error.message}`); }
    const pending = mckoState.files.length;
    if (requestErrors.length || oversized.length) {
        feedback.textContent = `Загрузка завершена частично: выбрано ${selected.length}, передано серверу ${completedFiles.size}, обработано ${totals.filesProcessed}, ошибок в файлах ${totals.filesFailed}, строк загружено ${totals.rowsImported}. Осталось в списке: ${pending}. ${requestErrors.join('; ')}${oversized.length ? `; файлов больше 30 МБ: ${oversized.length}` : ''}`;
    } else {
        feedback.textContent = `Готово: выбрано и передано серверу ${completedFiles.size} файлов, обработано ${totals.filesProcessed}, с ошибкой ${totals.filesFailed}, строк загружено ${totals.rowsImported}. Автоматически отправлено пакетов: ${batches.length}.`;
    }
}
async function loadHistory() {
    const rows = await mckoApi('/api/vsoko/mcko/imports?limit=5000');
    document.getElementById('mcko-history-body').innerHTML = rows.length ? rows.map(row => `<tr>
        <td>${esc(row.fileName)}</td><td>${esc(row.fileKind || '—')}</td><td>${esc(row.detectedAcademicYear || '—')}</td>
        <td>${esc(row.detectedWorkDate || '—')}</td><td>${esc(row.detectedSubject || '—')}</td>
        <td><span class="mcko-status mcko-status-${esc(row.status)}">${esc(statusLabel(row.status))}</span></td>
        <td class="mcko-number">${row.totalRows}</td><td class="mcko-number">${row.importedRows}</td><td class="mcko-number">${row.skippedRows}</td>
        <td>${esc(row.reason || '—')}</td><td>${row.processedAt ? new Date(row.processedAt).toLocaleString('ru-RU') : '—'}</td></tr>`).join('') : '<tr><td colspan="11" class="mcko-empty">Файлы ещё не загружались</td></tr>';
}
async function loadFilters() {
    const data = await mckoApi('/api/vsoko/mcko/filters');
    fillSelect('mcko-filter-year', data.academicYears, 'Все годы'); fillSelect('mcko-filter-class', data.classes, 'Все классы');
    fillSelect('mcko-filter-subject', data.subjects, 'Все предметы');
    const link = document.getElementById('mcko-filter-link'); link.innerHTML = '<option value="">Все статусы</option>' + data.linkStatuses.map(v => `<option value="${esc(v)}">${esc(linkLabel(v))}</option>`).join('');
    if (currentYear() && data.academicYears.includes(currentYear())) document.getElementById('mcko-filter-year').value = currentYear();
}
function fillSelect(id, values, empty) { document.getElementById(id).innerHTML = `<option value="">${empty}</option>` + (values||[]).map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join(''); }
function filterValues() { return { academicYear:document.getElementById('mcko-filter-year').value,className:document.getElementById('mcko-filter-class').value,subject:document.getElementById('mcko-filter-subject').value,student:document.getElementById('mcko-filter-student').value.trim(),teacher:document.getElementById('mcko-filter-teacher').value.trim(),linkStatus:document.getElementById('mcko-filter-link').value,limit:5000 }; }
async function loadResults() {
    const rows = await mckoApi(`/api/vsoko/mcko/results?${q(filterValues())}`); mckoState.resultsLoaded = true;
    const linked = rows.filter(row => row.studentId).length; const unmatched = rows.length-linked;
    document.getElementById('mcko-results-kpis').innerHTML = `<div class="mcko-kpi"><span>Показано</span><strong>${rows.length}</strong></div><div class="mcko-kpi"><span>Привязано</span><strong>${linked}</strong></div><div class="mcko-kpi"><span>Нужно проверить</span><strong>${unmatched}</strong></div>`;
    document.getElementById('mcko-results-body').innerHTML = rows.length ? rows.map(row => `<tr>
        <td>${esc(row.studentFio || '—')}</td><td>${esc(row.studentCode || '—')}</td><td>${esc(row.academicYear)}</td><td>${esc(row.className || '—')}</td><td>${esc(row.subjectName)}</td><td>${esc(row.diagnosticDate || '—')}</td>
        <td class="mcko-number">${fmt(row.score)}</td><td class="mcko-number">${fmt(row.percent)}</td><td class="mcko-number">${fmt(row.mark,0)}</td><td>${esc(row.teacherFio || '—')}</td>
        <td title="${esc(row.linkMessage || '')}"><span class="mcko-status mcko-link-${esc(row.linkStatus)}">${esc(linkLabel(row.linkStatus))}</span></td>
        <td class="mcko-row-actions">${row.studentId ? '' : `<span data-requires-edit><button type="button" data-link-result="${row.id}" data-caption="${esc(`${row.studentFio || 'Без ФИО'}, ${row.className || 'класс не указан'}`)}">Привязать</button></span>`}</td></tr>`).join('') : '<tr><td colspan="12" class="mcko-empty">По выбранным фильтрам результатов нет</td></tr>';
    document.querySelectorAll('[data-link-result]').forEach(button => button.addEventListener('click', () => openLinkDialog(button.dataset.linkResult, button.dataset.caption)));
}
function openLinkDialog(resultId, caption) { mckoState.linkingResultId = Number(resultId); document.getElementById('mcko-link-result-caption').textContent = caption; document.getElementById('mcko-link-search').value = ''; document.getElementById('mcko-link-search-results').innerHTML = ''; document.getElementById('mcko-link-dialog').showModal(); }
let searchTimer = null;
function scheduleStudentSearch() { clearTimeout(searchTimer); searchTimer = setTimeout(searchStudents, 250); }
async function searchStudents() {
    const query = document.getElementById('mcko-link-search').value.trim(); if (query.length < 2) return;
    const rows = await mckoApi(`/api/vsoko/mcko/students/search?${q({q:query,limit:30})}`);
    const host = document.getElementById('mcko-link-search-results'); host.innerHTML = rows.length ? rows.map(row => `<button type="button" class="mcko-search-card" data-student-id="${row.id}"><strong>${esc(row.currentFullName)}</strong><br><span class="muted">${esc(row.currentClass || 'класс не указан')} · известных ФИО: ${row.knownNames.length}</span></button>`).join('') : '<div class="mcko-empty">Совпадений нет</div>';
    host.querySelectorAll('[data-student-id]').forEach(button => button.addEventListener('click', () => saveStudentLink(Number(button.dataset.studentId))));
}
async function saveStudentLink(studentId) { await mckoApi(`/api/vsoko/mcko/results/${mckoState.linkingResultId}/student`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({studentId})}); document.getElementById('mcko-link-dialog').close(); await loadResults(); }
async function reconcile() { const feedback=document.getElementById('mcko-upload-feedback'); feedback.textContent='Повторно сопоставляем результаты с карточками детей…'; try{const r=await mckoApi('/api/vsoko/mcko/results/reconcile',{method:'POST'});feedback.textContent=`Привязано: ${r.linked}; неоднозначно: ${r.ambiguous}; не найдено: ${r.notFound}.`;if(mckoState.resultsLoaded)await loadResults();}catch(e){feedback.textContent=e.message;} }
function exportResults() { const params=filterValues(); delete params.limit; window.location.href=`/api/vsoko/mcko/results/export?${q(params)}`; }
function bindTabs() { document.querySelectorAll('[data-panel]').forEach(button => button.addEventListener('click', async () => { document.querySelectorAll('[data-panel]').forEach(x=>x.classList.toggle('active',x===button)); document.querySelectorAll('.mcko-panel').forEach(panel=>panel.hidden=panel.id!==`panel-${button.dataset.panel}`); if(button.dataset.panel==='results'&&!mckoState.resultsLoaded) await loadResults(); })); }
function bindUpload() { const zone=document.getElementById('mcko-dropzone'),input=document.getElementById('mcko-files'); document.getElementById('mcko-choose-files').addEventListener('click',()=>input.click()); zone.addEventListener('click',e=>{if(e.target===zone)input.click()}); zone.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();input.click()}}); input.addEventListener('change',()=>{setFiles(input.files);input.value=''}); ['dragenter','dragover'].forEach(name=>zone.addEventListener(name,e=>{e.preventDefault();zone.classList.add('drag')})); ['dragleave','drop'].forEach(name=>zone.addEventListener(name,e=>{e.preventDefault();zone.classList.remove('drag')})); zone.addEventListener('drop',e=>setFiles(e.dataTransfer.files)); document.getElementById('mcko-clear-files').addEventListener('click',()=>{mckoState.files=[];input.value='';renderFiles()}); document.getElementById('mcko-upload-btn').addEventListener('click',uploadFiles); }

(async function init(){ bindTabs();bindUpload();document.getElementById('mcko-apply-filters').addEventListener('click',loadResults);document.getElementById('mcko-reset-filters').addEventListener('click',()=>{['mcko-filter-year','mcko-filter-class','mcko-filter-subject','mcko-filter-link'].forEach(id=>document.getElementById(id).value='');['mcko-filter-student','mcko-filter-teacher'].forEach(id=>document.getElementById(id).value='');loadResults()});document.getElementById('mcko-export-results').addEventListener('click',exportResults);document.getElementById('mcko-reconcile-btn').addEventListener('click',reconcile);document.getElementById('mcko-link-search').addEventListener('input',scheduleStudentSearch);try{await Promise.all([loadHistory(),loadFilters()]);}catch(error){document.getElementById('mcko-upload-feedback').textContent=error.message;}})();
