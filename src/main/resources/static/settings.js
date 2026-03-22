const jsonHeaders = { 'Content-Type': 'application/json' };

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
    const byPeriod = Object.fromEntries((settings || []).map((item) => [item.studyPeriod, item]));
    ui.form.elements.yearStartDate.value = byPeriod.YEAR?.startDate || '';
    ui.form.elements.yearEndDate.value = byPeriod.YEAR?.endDate || '';
    ui.form.elements.h1StartDate.value = byPeriod.H1?.startDate || '';
    ui.form.elements.h1EndDate.value = byPeriod.H1?.endDate || '';
    ui.form.elements.h2StartDate.value = byPeriod.H2?.startDate || '';
    ui.form.elements.h2EndDate.value = byPeriod.H2?.endDate || '';
}

async function reload() {
    const settings = await api('/api/settings/study-periods');
    fillForm(settings);
    return settings;
}

async function saveSettings(event) {
    event.preventDefault();
    const payload = [
        { studyPeriod: 'YEAR', startDate: ui.form.elements.yearStartDate.value, endDate: ui.form.elements.yearEndDate.value },
        { studyPeriod: 'H1', startDate: ui.form.elements.h1StartDate.value, endDate: ui.form.elements.h1EndDate.value },
        { studyPeriod: 'H2', startDate: ui.form.elements.h2StartDate.value, endDate: ui.form.elements.h2EndDate.value }
    ];

    for (const row of payload) {
        if (!row.startDate || !row.endDate) {
            print({ error: `Заполните даты для периода ${row.studyPeriod}` });
            return;
        }
        if (row.startDate > row.endDate) {
            print({ error: `Период ${row.studyPeriod} задан некорректно` });
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
