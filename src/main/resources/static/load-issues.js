const ui = {
    building: document.getElementById("load-issues-building"),
    statusFilter: document.getElementById("load-issues-status-filter"),
    refresh: document.getElementById("load-issues-refresh"),
    unresolved: document.getElementById("load-issues-unresolved"),
    body: document.getElementById("load-issues-body"),
    result: document.getElementById("load-issues-result")
};

const jsonHeaders = { "Content-Type": "application/json" };
let issueRows = [];
const commentSaveTimers = new Map();

function scoped(path) {
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

async function api(path, options = {}) {
    const response = await fetch(scoped(path), options);
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

function esc(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function print(value) {
    if (!ui.result) return;
    ui.result.textContent = value ? JSON.stringify(value, null, 2) : "";
}

async function loadBuildings() {
    const buildings = await api("/api/building-groups");
    const current = ui.building.value;
    ui.building.innerHTML = '<option value="">Все</option>' + (buildings || [])
        .map((building) => `<option value="${esc(building.code)}">${esc(building.name || building.code)}</option>`)
        .join("");
    ui.building.value = current || "";
}

async function loadIssues() {
    const params = new URLSearchParams();
    if (ui.building.value) params.set("building", ui.building.value);
    const data = await api(`/api/manual-load/issues${params.toString() ? `?${params}` : ""}`);
    issueRows = data?.rows || [];
    renderFilteredIssues();
    ui.unresolved.textContent = String(data?.unresolvedCount || 0);
}

function renderFilteredIssues() {
    const rows = ui.statusFilter?.value === "all" ? issueRows : issueRows.filter((row) => !row.resolved);
    renderIssues(rows);
}

function targetUrl(row) {
    const page = row.targetPage === "curriculum"
        ? "/curriculum.html"
        : row.targetPage === "inRate" ? "/people-load.html" : "/load.html";
    const params = new URLSearchParams();
    if (row.targetPage === "inRate") params.set("panel", "inRate");
    if (row.building) params.set("building", row.building);
    if (row.targetClass) params.set("issueClass", row.targetClass);
    if (row.targetSubject) params.set("issueSubject", row.targetSubject);
    const path = `${page}?${params}`;
    return window.withAcademicYear ? window.withAcademicYear(path) : path;
}

function renderIssues(rows) {
    if (!rows.length) {
        ui.body.innerHTML = '<tr><td colspan="5">Нестыковок не найдено.</td></tr>';
        return;
    }
    ui.body.innerHTML = rows.map((row) => `
        <tr data-issue-key="${esc(row.key)}" class="${row.resolved ? "load-issue-resolved" : ""}">
            <td>${esc(row.type)}</td>
            <td>${esc(row.description)}</td>
            <td>
                <textarea data-issue-comment rows="2" placeholder="Комментарий">${esc(row.comment || "")}</textarea>
                <span class="load-issue-save-status" data-save-status aria-live="polite"></span>
            </td>
            <td class="center-cell">
                <input type="checkbox" data-issue-resolved ${row.resolved ? "checked" : ""}>
            </td>
            <td><a class="button-link" href="${esc(targetUrl(row))}" target="_blank" rel="noopener">Перейти к ошибке</a></td>
        </tr>
    `).join("");
    bindIssueControls();
}

function bindIssueControls() {
    ui.body.querySelectorAll("[data-issue-comment]").forEach((textarea) => {
        textarea.addEventListener("input", () => {
            const tr = textarea.closest("[data-issue-key]");
            scheduleCommentSave(tr, textarea.value);
        });
        textarea.addEventListener("blur", () => saveCommentNow(textarea.closest("[data-issue-key]"), textarea.value));
    });
    ui.body.querySelectorAll("[data-issue-resolved]").forEach((checkbox) => {
        checkbox.addEventListener("change", async () => {
            const tr = checkbox.closest("[data-issue-key]");
            const textarea = tr.querySelector("[data-issue-comment]");
            await saveCommentNow(tr, textarea?.value || "");
            await saveIssueState(tr.dataset.issueKey, { resolved: checkbox.checked });
            tr.classList.toggle("load-issue-resolved", checkbox.checked);
            await loadIssues();
        });
    });
}

function scheduleCommentSave(tr, comment) {
    if (!tr) return;
    const key = tr.dataset.issueKey;
    const status = tr.querySelector("[data-save-status]");
    if (status) status.textContent = "Сохраняется...";
    clearTimeout(commentSaveTimers.get(key));
    commentSaveTimers.set(key, setTimeout(() => saveCommentNow(tr, comment), 700));
}

async function saveCommentNow(tr, comment) {
    if (!tr) return;
    const key = tr.dataset.issueKey;
    clearTimeout(commentSaveTimers.get(key));
    commentSaveTimers.delete(key);
    const status = tr.querySelector("[data-save-status]");
    try {
        await saveIssueState(key, { comment });
        if (status) status.textContent = "Сохранено";
    } catch (error) {
        if (status) status.textContent = "Не сохранено";
        print({ error: error.message });
    }
}

async function saveIssueState(key, patch) {
    return api("/api/manual-load/issues", {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify({ key, ...patch })
    });
}

ui.refresh?.addEventListener("click", () => loadIssues().catch((error) => print({ error: error.message })));
ui.building?.addEventListener("change", () => loadIssues().catch((error) => print({ error: error.message })));
ui.statusFilter?.addEventListener("change", renderFilteredIssues);

loadBuildings()
    .catch(() => {})
    .then(loadIssues)
    .catch((error) => print({ error: error.message }));
