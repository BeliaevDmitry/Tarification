const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    form: document.getElementById("building-form"),
    refreshBtn: document.getElementById("refresh-buildings-btn"),
    clearBtn: document.getElementById("clear-buildings-btn"),
    result: document.getElementById("buildings-result"),
    body: document.getElementById("buildings-body")
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function escapeHtml(v) {
    return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function print(value) { ui.result.textContent = JSON.stringify(value, null, 2); }

function render(rows) {
    ui.body.innerHTML = "";
    (rows || []).sort((a, b) => (a.code || "").localeCompare(b.code || "", "ru")).forEach((r) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${escapeHtml(r.code)}</td><td>${escapeHtml(r.name)}</td><td>${escapeHtml(r.createdAt)}</td>`;
        ui.body.appendChild(tr);
    });
}

async function reload() {
    const rows = await api("/api/buildings");
    render(rows);
}

ui.form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(ui.form);
    const payload = { code: String(form.get("code") || "").trim(), name: String(form.get("name") || "").trim() };
    try {
        const saved = await api("/api/buildings", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
        print(saved);
        ui.form.reset();
        await reload();
    } catch (error) { print({ error: error.message }); }
});

ui.refreshBtn.addEventListener("click", () => reload().catch((e) => print({ error: e.message })));
ui.clearBtn.addEventListener("click", async () => {
    try { await api("/api/buildings", { method: "DELETE" }); print({ status: "cleared" }); await reload(); }
    catch (error) { print({ error: error.message }); }
});

function startAfterAuth() {
    reload().catch((e) => print({ error: e.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
