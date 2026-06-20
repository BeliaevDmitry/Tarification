const ui = {
    building: document.getElementById("load-issues-building"),
    refresh: document.getElementById("load-issues-refresh"),
    unresolved: document.getElementById("load-issues-unresolved"),
    body: document.getElementById("load-issues-body"),
    result: document.getElementById("load-issues-result")
};

const jsonHeaders = { "Content-Type": "application/json" };

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
    renderIssues(data?.rows || []);
    ui.unresolved.textContent = String(data?.unresolvedCount || 0);
}

function renderIssues(rows) {
    if (!rows.length) {
        ui.body.innerHTML = '<tr><td colspan="4">Нестыковок не найдено.</td></tr>';
        return;
    }
    ui.body.innerHTML = rows.map((row) => `
        <tr data-issue-key="${esc(row.key)}" class="${row.resolved ? "load-issue-resolved" : ""}">
            <td>${esc(row.type)}</td>
            <td>${esc(row.description)}</td>
            <td>
                <textarea data-issue-comment rows="2" placeholder="Комментарий">${esc(row.comment || "")}</textarea>
                <button type="button" data-save-comment>Сохранить</button>
            </td>
            <td class="center-cell">
                <input type="checkbox" data-issue-resolved ${row.resolved ? "checked" : ""}>
            </td>
        </tr>
    `).join("");
    bindIssueControls();
}

function bindIssueControls() {
    ui.body.querySelectorAll("[data-save-comment]").forEach((button) => {
        button.addEventListener("click", async () => {
            const tr = button.closest("[data-issue-key]");
            const comment = tr.querySelector("[data-issue-comment]")?.value || "";
            await saveIssueState(tr.dataset.issueKey, { comment });
            print({ saved: true });
        });
    });
    ui.body.querySelectorAll("[data-issue-resolved]").forEach((checkbox) => {
        checkbox.addEventListener("change", async () => {
            const tr = checkbox.closest("[data-issue-key]");
            await saveIssueState(tr.dataset.issueKey, { resolved: checkbox.checked });
            tr.classList.toggle("load-issue-resolved", checkbox.checked);
            await loadIssues();
        });
    });
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

loadBuildings()
    .catch(() => {})
    .then(loadIssues)
    .catch((error) => print({ error: error.message }));
