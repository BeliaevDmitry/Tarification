const ui = {
    refresh: document.getElementById("rates-refresh-btn"),
    save: document.getElementById("rates-save-btn"),
    fill: document.getElementById("rates-fill-btn"),
    summary: document.getElementById("rates-summary"),
    table: document.getElementById("rates-table")
};

const state = {
    overview: { rows: [], teachers: [], hasUnresolvedRows: false }
};

function withYear(path) {
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

async function request(path, options = {}) {
    const response = await fetch(withYear(path), {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!response.ok) {
        throw new Error(body?.message || body?.error || body || `HTTP ${response.status}`);
    }
    return body;
}

function esc(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function formatNumber(value) {
    return new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(Number(value || 0));
}

function formatMoney(value) {
    return new Intl.NumberFormat("ru-RU", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number(value || 0));
}

function permission() {
    const user = window.tarificationAuth || {};
    const permissions = window.tarificationTabPermissions || {};
    return {
        canView: Boolean(user.admin || permissions.LOAD_SALARY?.canView),
        canEdit: Boolean(user.admin || permissions.LOAD_SALARY?.canEdit)
    };
}

let fitFrame = 0;
function fitTable() {
    if (fitFrame) return;
    fitFrame = window.requestAnimationFrame(() => {
        fitFrame = 0;
        const wrap = ui.table?.closest(".sheet-wrap");
        if (!wrap) return;
        const available = Math.floor(window.innerHeight - wrap.getBoundingClientRect().top - 18);
        wrap.style.maxHeight = `${Math.max(260, available)}px`;
    });
}

function render() {
    const overview = state.overview || { rows: [], teachers: [] };
    const summaryByKey = new Map((overview.teachers || [])
        .map((item) => [`${item.teacherId}|${item.contractId}`, item]));
    const rows = overview.rows || [];
    let html = `<thead><tr>
        <th>Работник и договор</th><th>Корпус</th><th>Предмет</th><th>Класс/группа</th><th>Период</th>
        <th>Часы всего</th><th>Внутри ставки</th><th>К оплате</th><th>Сумма</th>
    </tr></thead><tbody>`;
    if (!rows.length) {
        html += `<tr><td colspan="9" class="muted">
            Нет нагрузки по выбранным для ставок предметам. Проверьте должности и разрешённые предметы в настройках.
        </td></tr>`;
    } else {
        let previousKey = "";
        rows.forEach((row) => {
            const key = `${row.teacherId}|${row.contractId}`;
            const teacher = summaryByKey.get(key);
            const first = key !== previousKey;
            previousKey = key;
            html += `<tr class="${row.allocationConfirmed ? "" : "load-in-rate-unresolved"}"
                data-rate-row="${esc(row.manualLoadEntryId)}"
                data-contract-id="${esc(row.contractId)}"
                data-teacher-key="${esc(key)}"
                data-study-period="${esc(row.studyPeriod || "YEAR")}">`;
            html += `<td>${first ? `<b>${esc(row.fio)}</b><br>№ ${esc(row.contractNumber)} · ${esc(row.positionName)}
                <br><span class="muted">Всего ${esc(formatNumber(teacher?.totalHoursH1))}/${esc(formatNumber(teacher?.totalHoursH2))};
                в ставке ${esc(formatNumber(teacher?.includedHoursH1))}/${esc(formatNumber(teacher?.includedHoursH2))};
                ещё можно ${esc(formatNumber(teacher?.remainingCapacityHoursH1))}/${esc(formatNumber(teacher?.remainingCapacityHoursH2))};
                к оплате ${esc(formatNumber(teacher?.paidHoursH1))}/${esc(formatNumber(teacher?.paidHoursH2))};
                ставка ${esc(formatNumber(teacher?.rateFractionH1))}/${esc(formatNumber(teacher?.rateFractionH2))}</span>` : ""}</td>`;
            html += `<td>${esc(row.building)}</td>`;
            html += `<td>${esc(row.subject)}</td>`;
            html += `<td>${esc([row.className, row.groupName].filter(Boolean).join(" "))}</td>`;
            html += `<td>${esc(row.studyPeriod === "H1" ? "1П" : row.studyPeriod === "H2" ? "2П" : "ГОД")}</td>`;
            html += `<td>${esc(formatNumber(row.totalHours))}</td>`;
            html += `<td><input class="in-rate-hours-input" data-included-hours type="number" min="0"
                max="${esc(row.totalHours)}" step="0.01" value="${esc(row.includedHours)}"
                ${permission().canEdit ? "" : "disabled"}></td>`;
            html += `<td data-paid-hours>${esc(formatNumber(row.paidHours))}</td>`;
            html += `<td data-rate-amount>${esc(formatMoney(row.amount))}</td></tr>`;
        });
    }
    html += "</tbody>";
    ui.table.innerHTML = html;

    const unresolved = (overview.teachers || []).reduce(
        (sum, row) => sum + Number(row.unresolvedRows || 0), 0);
    const buildings = new Set(rows.map((row) => String(row.building || "").trim()).filter(Boolean)).size;
    const workers = overview.teachers?.length || 0;
    ui.summary.textContent = unresolved
        ? `Все корпуса: ${buildings}. Работников: ${workers}. Требуется распределить строк: ${unresolved}.`
        : `Все корпуса: ${buildings}. Распределение подтверждено для ${workers} работников.`;
    ui.summary.classList.toggle("error-text", unresolved > 0);
    fitTable();
}

function updatePreview(tableRow) {
    const input = tableRow.querySelector("[data-included-hours]");
    const total = Number(input?.max || 0);
    const included = Math.min(total, Math.max(0, Number(input?.value || 0)));
    input.value = String(included);
    tableRow.querySelector("[data-paid-hours]").textContent = formatNumber(total - included);
    const source = (state.overview.rows || []).find(
        (row) => String(row.manualLoadEntryId) === tableRow.dataset.rateRow);
    if (!source) return;
    const paid = Number(source.paidHours || 0);
    const perHour = paid > 0 ? Number(source.amount || 0) / paid : 0;
    tableRow.querySelector("[data-rate-amount]").textContent = formatMoney(perHour * (total - included));
}

function fillAvailableHours() {
    const remaining = new Map();
    (state.overview.teachers || []).forEach((row) => {
        const key = `${row.teacherId}|${row.contractId}`;
        remaining.set(`${key}|H1`, Number(row.capacityHoursH1 || 0));
        remaining.set(`${key}|H2`, Number(row.capacityHoursH2 || 0));
    });
    const rows = Array.from(ui.table.querySelectorAll("[data-rate-row]"))
        .sort((left, right) => (left.dataset.studyPeriod === "YEAR" ? 0 : 1)
            - (right.dataset.studyPeriod === "YEAR" ? 0 : 1));
    rows.forEach((row) => {
        const key = row.dataset.teacherKey;
        const input = row.querySelector("[data-included-hours]");
        const total = Number(input.max || 0);
        const period = row.dataset.studyPeriod || "YEAR";
        const halfKeys = period === "H1" ? [`${key}|H1`]
            : period === "H2" ? [`${key}|H2`] : [`${key}|H1`, `${key}|H2`];
        const available = Math.min(...halfKeys.map(
            (halfKey) => Math.max(0, remaining.get(halfKey) || 0)));
        const value = Math.min(total, available);
        input.value = String(value);
        halfKeys.forEach((halfKey) => remaining.set(
            halfKey, Math.max(0, (remaining.get(halfKey) || 0) - value)));
        updatePreview(row);
    });
}

async function load() {
    ui.summary.textContent = "Загрузка ставок по всем корпусам…";
    state.overview = await request("/api/manual-load/in-rate")
        || { rows: [], teachers: [], hasUnresolvedRows: false };
    render();
}

async function save() {
    const rows = Array.from(ui.table.querySelectorAll("[data-rate-row]")).map((row) => ({
        manualLoadEntryId: Number(row.dataset.rateRow),
        contractId: Number(row.dataset.contractId),
        includedHours: Number(row.querySelector("[data-included-hours]").value || 0)
    }));
    ui.save.disabled = true;
    try {
        const result = await request("/api/manual-load/in-rate", {
            method: "PUT",
            body: JSON.stringify({ rows })
        });
        await load();
        alert(result.agreementsRequireReissue
            ? "Распределение сохранено. Неподписанные выпущенные допсоглашения отмечены для перевыпуска."
            : "Распределение сохранено.");
    } finally {
        ui.save.disabled = false;
    }
}

function showError(error) {
    ui.summary.textContent = `Не удалось получить ставки: ${error.message}`;
    ui.summary.classList.add("error-text");
    if (!state.overview.rows?.length) {
        ui.table.innerHTML = `<tbody><tr><td class="muted">Данные нагрузки не изменены. Обновите страницу после восстановления сервера.</td></tr></tbody>`;
    }
}

function waitForAuth() {
    if (window.tarificationAuth) return Promise.resolve();
    return new Promise((resolve) => {
        let attempts = 0;
        const timer = setInterval(() => {
            attempts += 1;
            if (window.tarificationAuth || attempts >= 50) {
                clearInterval(timer);
                resolve();
            }
        }, 50);
    });
}

async function init() {
    await waitForAuth();
    ui.save.hidden = !permission().canEdit;
    ui.fill.hidden = !permission().canEdit;
    ui.refresh.addEventListener("click", () => load().catch(showError));
    ui.save.addEventListener("click", () => save().catch(showError));
    ui.fill.addEventListener("click", fillAvailableHours);
    ui.table.addEventListener("input", (event) => {
        const row = event.target.closest("[data-rate-row]");
        if (row && event.target.matches("[data-included-hours]")) updatePreview(row);
    });
    window.addEventListener("resize", fitTable);
    window.addEventListener("scroll", fitTable, { passive: true });
    await load();
}

init().catch(showError);
