(() => {
    'use strict';
    const $ = id => document.getElementById(`fot-${id}`);
    const types = { PLAN: 'Учебный план', LOAD: 'Нагрузка', SUBGROUP: 'Подгруппы', MCKO: 'Требуется вакансия по МЦКО', MCKO_VACANCY: 'Вакансия по МЦКО учтена', MAPPING: 'Сопоставление' };
    const statuses = { OPEN: 'Открыто', EXPECTED: 'Так и должно быть', FIXED: 'Исправлено — ждёт проверки' };
    const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
    let overview = { batches: [], issues: [] }, historical = null, options = null, sources = [];
    let page = 0, sort = 'className', direction = 1, canEdit = false, busy = false;
    const size = 75;
    const message = value => { $('message').textContent = value; };
    async function api(path = '', init = {}) {
        const response = await fetch(window.withAcademicYear(`/api/master-fot${path}`), init);
        const raw = await response.text();
        let body;
        try { body = raw ? JSON.parse(raw) : null; } catch { body = null; }
        if (!response.ok) throw new Error(body?.message || `Запрос не выполнен (${response.status})`);
        return body;
    }
    const send = (path, method, body) => api(path, { method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body) });
    function currentRows() { return historical ?? overview.issues; }
    function filtered() {
        const query = $('search').value.toLocaleLowerCase('ru'), status = $('status').value;
        return currentRows().filter(row => {
            const f = row.finding;
            const statusMatches = historical || status === 'all' || (status === 'archive' ? row.archived : !row.archived && (status === 'active' || row.status === status));
            return statusMatches && (!$('type').value || f.type === $('type').value)
                && (!$('building').value || f.building === $('building').value)
                && (!query || [f.className, f.teacher, f.subject, f.building, f.detail, row.comment].join(' ').toLocaleLowerCase('ru').includes(query));
        }).sort((a,b) => String(a.finding[sort] || '').localeCompare(String(b.finding[sort] || ''), 'ru', { numeric: true }) * direction);
    }
    function render() {
        const rows = filtered();
        page = Math.min(page, Math.max(0, Math.ceil(rows.length / size) - 1));
        $('issues').innerHTML = rows.slice(page * size, (page + 1) * size).map(row => {
            const f = row.finding, editable = canEdit && !historical && !row.archived;
            return `<tr data-id="${esc(row.id)}" data-status="${esc(row.status)}" class="${row.archived ? 'fot-archived' : ''}">
                <td>${esc(types[f.type] || f.type)}</td><td>${esc(f.building || '—')}<br>${esc(f.className)}</td>
                <td>${esc(f.subject)}<br><strong>${esc(f.teacher)}</strong></td><td>${esc(f.expected)}</td><td>${esc(f.actual)}</td>
                <td>${esc(f.detail)}${f.mappingType && editable ? '<br><button type="button" data-map>Сопоставить</button>' : ''}</td>
                <td>${historical ? 'Состояние на момент сверки' : row.archived ? `Архив · подтверждено в сверке № ${esc(row.archivedBatchId)}` : editable ? `<select data-decision aria-label="Решение">${Object.entries(statuses).map(([key,label]) => `<option value="${key}" ${key === row.status ? 'selected' : ''}>${esc(label)}</option>`).join('')}</select>` : esc(statuses[row.status])}
                ${editable ? `<textarea data-comment rows="2" maxlength="4000" aria-label="Комментарий" placeholder="Комментарий">${esc(row.comment)}</textarea><button data-save type="button">Сохранить</button>` : `<p>${esc(row.comment)}</p>`}
                ${!historical ? `<small>Первое появление: № ${esc(row.firstBatchId)}<br>Последняя проверка: № ${esc(row.lastBatchId)}</small>` : ''}</td></tr>`;
        }).join('') || '<tr><td colspan="7">Нестыковок по выбранным условиям нет.</td></tr>';
        $('page').textContent = `${rows.length} записей · страница ${page + 1} из ${Math.max(1, Math.ceil(rows.length / size))}`;
        $('prev').disabled = page === 0; $('next').disabled = (page + 1) * size >= rows.length;
    }
    function renderSummary() {
        const active = overview.issues.filter(r => !r.archived);
        $('summary').textContent = `Итераций: ${overview.batches.length}. Открыто: ${active.filter(r => r.status === 'OPEN').length}; так и должно быть: ${active.filter(r => r.status === 'EXPECTED').length}; ждут проверки: ${active.filter(r => r.status === 'FIXED').length}; архив: ${overview.issues.length - active.length}.`;
    }
    async function refresh() {
        overview = await api(); historical = null; $('batch').value = ''; $('status').disabled = false;
        $('batch').innerHTML = '<option value="">Текущие нестыковки и архив</option>' + overview.batches.map(b => `<option value="${b.id}">№ ${b.id} · ${esc(b.date)} · ${esc(b.filename)} · ${b.findings} нестыковок</option>`).join('');
        renderSummary();
        $('building').innerHTML = '<option value="">Все</option>' + [...new Set(overview.issues.map(r => r.finding.building).filter(Boolean))].sort().map(b => `<option>${esc(b)}</option>`).join('');
        const incomplete = overview.batches[0]?.complete === false;
        $('completeness').hidden = !incomplete;
        $('completeness').textContent = 'Сверка неполная: есть несопоставленные строки. Архивирование приостановлено. Настройте сопоставления и повторно загрузите полную выгрузку.';
        page = 0; render();
    }
    async function upload() {
        if (busy) return;
        const file = $('file').files[0];
        if (!file) { message('Выберите файл Excel из Мастер ФОТ.'); return; }
        busy = true; $('upload').disabled = true; message('Загружаю и сверяю…');
        try {
            const form = new FormData(); form.append('file', file);
            const result = await api('/import', { method: 'POST', body: form });
            await refresh(); options = null;
            message(`Сверка № ${result.id} сохранена: ${result.rows} строк, ${result.findings} нестыковок. Дата файла: ${result.date}.`);
        } catch (e) { message(e.message); }
        finally { busy = false; $('upload').disabled = false; }
    }
    async function openMappings(focus) {
        options = await api('/options');
        sources = overview.issues.filter(r => !r.archived && r.finding.mappingType).map(r => ({type:r.finding.mappingType, source:r.finding.mappingSource}));
        for (const m of options.mappings) if (!sources.some(s => s.type === m.type && s.source === m.source)) sources.push(m);
        $('map-source').innerHTML = sources.map((s,i) => `<option value="${i}">${esc(s.type === 'GROUP' ? 'Группа' : s.type === 'SUBJECT' ? 'Предмет' : 'Педагог')}: ${esc(s.source)}</option>`).join('');
        if (focus) $('map-source').value = String(sources.findIndex(s => s.type === focus.mappingType && s.source === focus.mappingSource));
        renderTargets(); renderMappings(); $('map-message').textContent = '';
        if (!$('dialog').open) $('dialog').showModal();
    }
    function choices(type) { return type === 'GROUP' ? options.groups : type === 'SUBJECT' ? options.subjects : options.teachers; }
    function renderTargets() {
        const source = sources[Number($('map-source').value)];
        $('map-save').disabled = !source;
        $('map-target').innerHTML = '<option value="">Выберите соответствие</option>' + (source ? choices(source.type) : []).map(c => `<option value="${esc(c.id)}">${esc(c.label)}</option>`).join('');
        if (source) $('map-target').value = options.mappings.find(m => m.type === source.type && m.source === source.source)?.target || '';
    }
    function renderMappings() {
        $('saved-mappings').innerHTML = options.mappings.map((m,i) => `<div class="fot-saved">${esc(m.source)} → ${esc(choices(m.type).find(c => c.id === m.target)?.label || 'Соответствие больше не найдено в системе')} <button type="button" class="secondary" data-remove-map="${i}">Удалить соответствие</button></div>`).join('') || '<p>Сохранённых соответствий пока нет.</p>';
    }
    $('upload').addEventListener('click', upload);
    $('refresh').addEventListener('click', () => refresh().catch(e => message(e.message)));
    $('mappings').addEventListener('click', () => openMappings().catch(e => message(e.message)));
    $('map-source').addEventListener('change', renderTargets);
    $('dialog-close').addEventListener('click', () => $('dialog').close());
    $('map-save').addEventListener('click', async () => {
        const source = sources[Number($('map-source').value)], target = $('map-target').value;
        if (!source || !target) { $('map-message').textContent = 'Выберите соответствие в системе.'; return; }
        $('map-save').disabled = true;
        try { await send('/mappings', 'PUT', {...source, target}); options = await api('/options'); renderMappings(); $('map-message').textContent = 'Сохранено. Повторно загрузите файл, чтобы применить соответствие в новой сверке.'; }
        catch(e) { $('map-message').textContent = e.message; }
        finally { $('map-save').disabled = false; }
    });
    $('saved-mappings').addEventListener('click', async e => {
        const button = e.target.closest('[data-remove-map]'); if (!button) return;
        button.disabled = true;
        try { const m = options.mappings[Number(button.dataset.removeMap)]; await send('/mappings', 'PUT', {...m, target:''}); options = await api('/options'); renderMappings(); renderTargets(); }
        catch(error) { $('map-message').textContent = error.message; button.disabled = false; }
    });
    $('issues').addEventListener('click', async e => {
        const button = e.target.closest('button'), tr = e.target.closest('[data-id]'); if (!button || !tr) return;
        const row = overview.issues.find(r => r.id === tr.dataset.id); if (!row) return;
        if (button.hasAttribute('data-map')) { openMappings(row.finding).catch(error => message(error.message)); return; }
        if (!button.hasAttribute('data-save')) return;
        button.disabled = true;
        try {
            const updated = await send(`/issues/${encodeURIComponent(row.id)}`, 'PATCH', { status:tr.querySelector('[data-decision]').value, comment:tr.querySelector('[data-comment]').value, version:row.version });
            Object.assign(row, updated); button.textContent = 'Сохранено'; tr.dataset.status = row.status; renderSummary();
        } catch(error) { message(error.message); }
        finally { button.disabled = false; }
    });
    $('batch').addEventListener('change', async () => {
        const id = $('batch').value;
        try { historical = id ? (await api(`/batches/${id}`)).map((f,i) => ({ id:`history-${i}`, finding:f })) : null; $('status').disabled = !!historical; page = 0; render(); }
        catch(e) { message(e.message); }
    });
    ['status','type','building','search'].forEach(id => $(id).addEventListener(id === 'search' ? 'input' : 'change', () => {page=0; render();}));
    document.querySelectorAll('[data-sort]').forEach(button => button.addEventListener('click', () => {direction=sort===button.dataset.sort?-direction:1; sort=button.dataset.sort; render();}));
    $('prev').addEventListener('click', () => {page--; render();}); $('next').addEventListener('click', () => {page++; render();});
    (async () => {
        for (let i=0; i<300 && !window.tarificationAuthReady; i++) await new Promise(r => setTimeout(r, 50));
        if (!window.tarificationAuthReady) throw new Error('Не удалось загрузить учебный год и права доступа. Обновите страницу.');
        const user = window.tarificationAuth, permission = window.tarificationTabPermissions?.LOAD_MASTER_FOT;
        canEdit = !!(user?.admin || (user?.canEdit && permission?.canEdit));
        $('import-controls').hidden = !(canEdit && (user?.admin || permission?.canImport));
        $('mappings').hidden = !canEdit;
        await refresh();
    })().catch(e => message(e.message));
})();
