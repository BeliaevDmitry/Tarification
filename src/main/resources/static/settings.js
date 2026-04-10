const jsonHeaders = { 'Content-Type': 'application/json' };
const PERIODS = ['YEAR', 'H1', 'H2'];

const ui = {
    form: document.getElementById('study-periods-form'),
    list: document.getElementById('study-periods-list'),
    refreshBtn: document.getElementById('study-periods-refresh-btn'),
    addBtn: document.getElementById('add-period-btn'),
    result: document.getElementById('settings-result')
};

let state = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(value) { ui.result.textContent = JSON.stringify(value, null, 2); }

function rowMarkup(item, idx) {
    return `
        <tr data-idx="${idx}">
            <td>
                <input type="hidden" name="id" value="${item.id || ''}">
                <input name="displayName" value="${item.displayName || ''}" required>
            </td>
            <td><input name="code" value="${item.code || ''}" required></td>
            <td>
                <select name="studyPeriod" required>
                    ${PERIODS.map((p) => `<option value="${p}" ${item.studyPeriod === p ? 'selected' : ''}>${p}</option>`).join('')}
                </select>
            </td>
            <td><input name="parallelFrom" type="number" min="1" max="11" value="${item.parallelFrom || 1}" required></td>
            <td><input name="parallelTo" type="number" min="1" max="11" value="${item.parallelTo || 11}" required></td>
            <td><input name="startDate" type="date" value="${item.startDate || ''}" required></td>
            <td><input name="endDate" type="date" value="${item.endDate || ''}" required></td>
            <td class="period-default-cell"><input name="defaultRule" type="checkbox" ${item.defaultRule ? 'checked' : ''}></td>
            <td><button type="button" class="danger-btn" data-remove-period="${idx}">Удалить</button></td>
        </tr>`;
}

function render() {
    ui.list.innerHTML = state.map((item, idx) => rowMarkup(item, idx)).join('');
}

async function reload() {
    state = await api('/api/settings/study-periods');
    render();
}

function collectPayload() {
    return Array.from(ui.list.querySelectorAll('tr[data-idx]')).map((row) => ({
        id: row.querySelector('[name=id]').value ? Number(row.querySelector('[name=id]').value) : null,
        displayName: row.querySelector('[name=displayName]').value.trim(),
        code: row.querySelector('[name=code]').value.trim(),
        studyPeriod: row.querySelector('[name=studyPeriod]').value,
        parallelFrom: Number(row.querySelector('[name=parallelFrom]').value),
        parallelTo: Number(row.querySelector('[name=parallelTo]').value),
        startDate: row.querySelector('[name=startDate]').value,
        endDate: row.querySelector('[name=endDate]').value,
        defaultRule: row.querySelector('[name=defaultRule]').checked
    }));
}

async function saveSettings(event) {
    event.preventDefault();
    const payload = collectPayload();
    const existing = payload.filter((x) => x.id);
    const created = payload.filter((x) => !x.id);

    try {
        for (const item of created) {
            await api('/api/settings/study-periods', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(item) });
        }
        if (existing.length) {
            await api('/api/settings/study-periods', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(existing) });
        }
        await reload();
        print({ status: 'saved', count: state.length });
    } catch (error) {
        print({ error: error.message });
    }
}

function bindEvents() {
    ui.form?.addEventListener('submit', saveSettings);
    ui.refreshBtn?.addEventListener('click', async () => {
        try {
            await reload();
            print({ status: 'reloaded', count: state.length });
        } catch (error) {
            print({ error: error.message });
        }
    });
    ui.addBtn?.addEventListener('click', () => {
        state.push({
            displayName: '',
            code: `custom_${Date.now()}`,
            studyPeriod: 'YEAR',
            parallelFrom: 1,
            parallelTo: 11,
            defaultRule: false,
            startDate: '',
            endDate: ''
        });
        render();
    });
    ui.list?.addEventListener('click', (event) => {
        const btn = event.target.closest('[data-remove-period]');
        if (!btn) return;
        const idx = Number(btn.getAttribute('data-remove-period'));
        if (!Number.isInteger(idx)) return;
        state.splice(idx, 1);
        render();
    });
}

(async function init() {
    bindEvents();
    try {
        await reload();
        print({ status: 'ready', count: state.length });
    } catch (error) {
        print({ error: error.message });
    }
})();
