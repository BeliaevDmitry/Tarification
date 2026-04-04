const jsonHeaders = { 'Content-Type': 'application/json' };
const SETTING_KEYS = ['YEAR_1_9', 'H1_1_9', 'H2_1_9', 'H1_10', 'H2_10', 'H1_11', 'H2_11'];

const ui = {
    form: document.getElementById('study-periods-form'),
    refreshBtn: document.getElementById('study-periods-refresh-btn'),
    result: document.getElementById('settings-result')
};

async function api(path, options = {}) {
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

function print(value) {
    ui.result.textContent = JSON.stringify(value, null, 2);
}

function fillForm(settings = []) {
    const byKey = Object.fromEntries((settings || []).map((item) => [item.settingKey, item]));
    SETTING_KEYS.forEach((key) => {
        ui.form.elements[`${key}_startDate`].value = byKey[key]?.startDate || '';
        ui.form.elements[`${key}_endDate`].value = byKey[key]?.endDate || '';
    });
}

async function reload() {
    const settings = await api('/api/settings/study-periods');
    fillForm(settings);
    return settings;
}

function buildPayload() {
    return SETTING_KEYS.map((key) => ({
        settingKey: key,
        startDate: ui.form.elements[`${key}_startDate`].value,
        endDate: ui.form.elements[`${key}_endDate`].value
    }));
}

async function saveSettings(event) {
    event.preventDefault();
    const payload = buildPayload();

    for (const row of payload) {
        if (!row.startDate || !row.endDate) {
            print({ error: `Заполните даты для периода ${row.settingKey}` });
            return;
        }
        if (row.startDate > row.endDate) {
            print({ error: `Период ${row.settingKey} задан некорректно` });
            return;
        }
    }

    try {
        const saved = await api('/api/settings/study-periods', {
            method: 'PUT',
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
        fillForm(saved);
        print({ status: 'saved', count: saved.length });
    } catch (error) {
        print({ error: error.message });
    }
}

function bindEvents() {
    ui.form?.addEventListener('submit', saveSettings);
    ui.refreshBtn?.addEventListener('click', async () => {
        try {
            const settings = await reload();
            print({ status: 'reloaded', count: settings.length });
        } catch (error) {
            print({ error: error.message });
        }
    });
}

(async function init() {
    bindEvents();
    try {
        const settings = await reload();
        print({ status: 'ready', count: settings.length });
    } catch (error) {
        print({ error: error.message });
    }
})();
