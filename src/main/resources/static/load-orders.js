const loadOrderState = {
    history: [],
    references: { staff: [], suggestedSignerId: null },
    readiness: null
};

const loadOrderUi = {
    form: document.getElementById('load-order-form'),
    number: document.getElementById('load-order-number'),
    date: document.getElementById('load-order-date'),
    effectiveWrap: document.getElementById('load-order-effective-wrap'),
    effectiveDate: document.getElementById('load-order-effective-date'),
    protocolNumberWrap: document.getElementById('load-order-protocol-number-wrap'),
    protocolNumber: document.getElementById('load-order-protocol-number'),
    protocolDateWrap: document.getElementById('load-order-protocol-date-wrap'),
    protocolDate: document.getElementById('load-order-protocol-date'),
    signerSelect: document.getElementById('load-order-signer-select'),
    signerPosition: document.getElementById('load-order-signer-position'),
    signerName: document.getElementById('load-order-signer-name'),
    control: document.getElementById('load-order-control'),
    basis: document.getElementById('load-order-basis'),
    feedback: document.getElementById('load-order-feedback'),
    submit: document.getElementById('load-order-submit'),
    refresh: document.getElementById('load-order-refresh'),
    readinessText: document.getElementById('load-order-readiness-text'),
    historySummary: document.getElementById('load-order-history-summary'),
    historyBody: document.getElementById('load-order-history-body')
};

function loadOrderYear() {
    return typeof getStoredAcademicYear === 'function' ? getStoredAcademicYear() : '';
}

function loadOrderUrl(path) {
    const year = loadOrderYear();
    if (!year || path.includes('academicYear=')) return path;
    return `${path}${path.includes('?') ? '&' : '?'}academicYear=${encodeURIComponent(year)}`;
}

async function loadOrderApi(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) throw new Error(body?.message || body?.error || text || `HTTP ${response.status}`);
    return body;
}

function loadOrderEsc(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[char]);
}

function loadOrderDate(value) {
    if (!value) return '—';
    const parts = String(value).split('-');
    return parts.length === 3 ? `${parts[2]}.${parts[1]}.${parts[0]}` : value;
}

function loadOrderDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

function selectedLoadOrderType() {
    return loadOrderUi.form.querySelector('input[name="type"]:checked')?.value || 'CURRICULUM_APPROVAL';
}

function syncTypeFields() {
    const curriculum = selectedLoadOrderType() === 'CURRICULUM_APPROVAL';
    loadOrderUi.protocolNumberWrap.hidden = !curriculum;
    loadOrderUi.protocolDateWrap.hidden = !curriculum;
    loadOrderUi.effectiveWrap.hidden = curriculum;
    const readiness = loadOrderState.readiness;
    if (readiness) {
        const count = curriculum ? readiness.curriculumPlanCount : readiness.loadEntryCount;
        loadOrderUi.submit.disabled = !count || !(window.tarificationAuth?.admin
            || window.tarificationTabPermissions?.LOAD?.canEdit);
    }
}

function fillSignerSelect() {
    const staff = loadOrderState.references.staff || [];
    loadOrderUi.signerSelect.innerHTML = '<option value="">Ввести ФИО вручную</option>' + staff.map(item =>
        `<option value="${loadOrderEsc(item.id)}">${loadOrderEsc(item.fullName)}${item.position ? ` — ${loadOrderEsc(item.position)}` : ''}</option>`
    ).join('');
    const suggested = loadOrderState.references.suggestedSignerId;
    if (suggested) {
        loadOrderUi.signerSelect.value = String(suggested);
        applySelectedSigner();
    }
}

function applySelectedSigner() {
    const selectedId = loadOrderUi.signerSelect.value;
    if (!selectedId) return;
    const staff = (loadOrderState.references.staff || []).find(item => String(item.id) === String(selectedId));
    if (!staff) return;
    loadOrderUi.signerName.value = staff.fullName || '';
    loadOrderUi.signerPosition.value = staff.position || 'Директор';
}

function renderReadiness() {
    const data = loadOrderState.readiness;
    if (!data) return;
    const curriculumReady = data.curriculumPlanCount > 0;
    const loadReady = data.loadEntryCount > 0;
    loadOrderUi.readinessText.innerHTML = `Учебный год <strong>${loadOrderEsc(data.academicYear)}</strong>. `
        + `<span class="${curriculumReady ? 'load-order-ready-ok' : 'load-order-ready-warning'}">Учебные планы: ${loadOrderEsc(data.curriculumPlanCount)} планов, ${loadOrderEsc(data.curriculumEntryCount)} строк.</span> `
        + `<span class="${loadReady ? 'load-order-ready-ok' : 'load-order-ready-warning'}">Нагрузка: ${loadOrderEsc(data.teacherCount)} педагогов, ${loadOrderEsc(data.loadEntryCount)} строк.</span>`;
    syncTypeFields();
}

function renderHistory() {
    const rows = loadOrderState.history || [];
    loadOrderUi.historySummary.textContent = rows.length
        ? `Сохранено приказов за ${loadOrderYear()}: ${rows.length}.` : `За ${loadOrderYear()} учебный год приказы ещё не формировались.`;
    if (!rows.length) {
        loadOrderUi.historyBody.innerHTML = '<tr><td colspan="6" class="muted">История пока пуста.</td></tr>';
        return;
    }
    loadOrderUi.historyBody.innerHTML = rows.map(item => `<tr>
        <td><strong>№ ${loadOrderEsc(item.orderNumber)}</strong><br><span class="muted">от ${loadOrderEsc(loadOrderDate(item.orderDate))}</span></td>
        <td>${loadOrderEsc(item.typeLabel)}</td>
        <td>${loadOrderEsc(item.academicYear)}</td>
        <td>${loadOrderEsc(item.sourceItemCount)} ${item.type === 'CURRICULUM_APPROVAL' ? 'групп планов' : 'позиций нагрузки'}<br><span class="muted">Школа № ${loadOrderEsc(item.schoolCode)}</span></td>
        <td>${loadOrderEsc(item.createdBy || '—')}<br><span class="muted">${loadOrderEsc(loadOrderDateTime(item.createdAt))}</span></td>
        <td><a class="button-link secondary" href="/api/load-orders/${loadOrderEsc(item.id)}/document">Скачать Word</a></td>
    </tr>`).join('');
}

async function loadOrderData() {
    loadOrderUi.readinessText.textContent = 'Проверяем учебный план и распределение нагрузки…';
    const [history, readiness, references] = await Promise.all([
        loadOrderApi(loadOrderUrl('/api/load-orders')),
        loadOrderApi(loadOrderUrl('/api/load-orders/readiness')),
        loadOrderApi('/api/load-orders/references')
    ]);
    loadOrderState.history = history || [];
    loadOrderState.readiness = readiness;
    loadOrderState.references = references || loadOrderState.references;
    fillSignerSelect();
    renderReadiness();
    renderHistory();
}

async function createLoadOrder(event) {
    event.preventDefault();
    const type = selectedLoadOrderType();
    const payload = {
        academicYear: loadOrderYear(),
        type,
        orderNumber: loadOrderUi.number.value.trim(),
        orderDate: loadOrderUi.date.value || null,
        protocolNumber: type === 'CURRICULUM_APPROVAL' ? loadOrderUi.protocolNumber.value.trim() : '',
        protocolDate: type === 'CURRICULUM_APPROVAL' ? (loadOrderUi.protocolDate.value || null) : null,
        effectiveDate: type === 'LOAD_APPROVAL' ? (loadOrderUi.effectiveDate.value || null) : null,
        signerName: loadOrderUi.signerName.value.trim(),
        signerPosition: loadOrderUi.signerPosition.value.trim(),
        controlOfficerName: loadOrderUi.control.value.trim(),
        basisText: loadOrderUi.basis.value.trim()
    };
    loadOrderUi.submit.disabled = true;
    loadOrderUi.feedback.textContent = 'Формируем Word-файл и сохраняем его в историю…';
    try {
        const created = await loadOrderApi('/api/load-orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        loadOrderUi.feedback.innerHTML = `Приказ сохранён. <a href="/api/load-orders/${loadOrderEsc(created.id)}/document">Скачать Word</a>`;
        loadOrderUi.number.value = '';
        loadOrderState.history = await loadOrderApi(loadOrderUrl('/api/load-orders'));
        renderHistory();
    } catch (error) {
        loadOrderUi.feedback.textContent = error.message || 'Не удалось сформировать приказ.';
    } finally {
        syncTypeFields();
    }
}

async function initLoadOrders() {
    for (let attempt = 0; attempt < 100 && !window.tarificationAuthReady; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 30));
    }
    const today = new Date().toISOString().slice(0, 10);
    loadOrderUi.date.value = today;
    loadOrderUi.effectiveDate.value = today;
    loadOrderUi.form.addEventListener('change', event => {
        if (event.target.name === 'type') syncTypeFields();
    });
    loadOrderUi.signerSelect.addEventListener('change', applySelectedSigner);
    loadOrderUi.form.addEventListener('submit', createLoadOrder);
    loadOrderUi.refresh.addEventListener('click', () => loadOrderData().catch(error => {
        loadOrderUi.readinessText.textContent = error.message || 'Не удалось обновить данные.';
    }));
    try {
        await loadOrderData();
    } catch (error) {
        loadOrderUi.readinessText.textContent = error.message || 'Не удалось загрузить данные.';
        loadOrderUi.historyBody.innerHTML = '<tr><td colspan="6" class="muted">Не удалось загрузить историю.</td></tr>';
    }
}

initLoadOrders();
