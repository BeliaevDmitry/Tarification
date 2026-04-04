const jsonHeaders = { 'Content-Type': 'application/json' };

const ui = {
    form: document.getElementById('study-periods-form'),
    body: document.getElementById('study-periods-body'),
    addBtn: document.getElementById('study-periods-add-btn'),
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

function esc(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function rowHtml(item = {}) {
    return `
      <tr>
        <td><input name="settingKey" value="${esc(item.settingKey || '')}" placeholder="AUTO для нового" readonly></td>
        <td><input name="displayName" value="${esc(item.displayName || '')}" required></td>
        <td>
          <select name="studyPeriod" required>
            <option value="YEAR" ${item.studyPeriod === 'YEAR' ? 'selected' : ''}>Учебный год</option>
            <option value="H1" ${item.studyPeriod === 'H1' ? 'selected' : ''}>1 полугодие</option>
            <option value="H2" ${item.studyPeriod === 'H2' ? 'selected' : ''}>2 полугодие</option>
          </select>
        </td>
        <td><input name="parallelFrom" type="number" min="1" max="11" value="${esc(item.parallelFrom || 1)}" required></td>
        <td><input name="parallelTo" type="number" min="1" max="11" value="${esc(item.parallelTo || 11)}" required></td>
        <td><input name="startDate" type="date" value="${esc(item.startDate || '')}" required></td>
        <td><input name="endDate" type="date" value="${esc(item.endDate || '')}" required></td>
        <td><button type="button" data-remove-row="1">Удалить</button></td>
      </tr>
    `;
}

function fillForm(settings = []) {
    ui.body.innerHTML = (settings || []).map((item) => rowHtml(item)).join("");
}

function collectRows() {
    return [...ui.body.querySelectorAll("tr")].map((tr) => {
        const read = (name) => tr.querySelector(`[name="${name}"]`)?.value;
        return {
            settingKey: String(read("settingKey") || "").trim() || null,
            displayName: String(read("displayName") || "").trim(),
            studyPeriod: read("studyPeriod"),
            parallelFrom: Number(read("parallelFrom")),
            parallelTo: Number(read("parallelTo")),
            startDate: read("startDate"),
            endDate: read("endDate")
        };
    });
}

async function reload() {
    const settings = await api('/api/settings/study-periods');
    fillForm(settings);
    return settings;
}

async function saveSettings(event) {
    event.preventDefault();
    const payload = collectRows();
    if (!payload.length) {
        print({ error: "Добавьте хотя бы один период" });
        return;
    }
    for (const row of payload) {
        if (!row.displayName || !row.studyPeriod || !row.startDate || !row.endDate) {
            print({ error: "Заполните все поля периода" });
            return;
        }
        if (row.startDate > row.endDate) {
            print({ error: `Некорректные даты для периода «${row.displayName}»` });
            return;
        }
        if (row.parallelFrom > row.parallelTo) {
            print({ error: `Некорректный диапазон классов для периода «${row.displayName}»` });
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
    ui.addBtn?.addEventListener('click', () => {
        ui.body.insertAdjacentHTML("beforeend", rowHtml({}));
    });
    ui.body?.addEventListener('click', (event) => {
        const btn = event.target.closest("[data-remove-row]");
        if (!btn) return;
        btn.closest("tr")?.remove();
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
