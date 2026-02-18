const jsonHeaders = { "Content-Type": "application/json" };

const ui = {
    manualLoadForm: document.getElementById("manual-load-form"),
    manualLoadResult: document.getElementById("manual-load-result"),
    processBtn: document.getElementById("process-btn"),
    processResult: document.getElementById("process-result"),
    modeBadge: document.getElementById("mode-badge")
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

function print(target, value) {
    if (!target) return;
    target.textContent = JSON.stringify(value, null, 2);
}

function formToObject(form) {
    const fd = new FormData(form);
    const obj = Object.fromEntries(fd.entries());
    Object.keys(obj).forEach((key) => {
        if (obj[key] === "") obj[key] = null;
    });
    if (obj.load != null) obj.load = Number(obj.load);
    if (obj.groupLoad != null) obj.groupLoad = Number(obj.groupLoad);
    return obj;
}

async function onManualLoadSubmit(e) {
    e.preventDefault();
    try {
        const payload = formToObject(ui.manualLoadForm);
        const result = await api("/api/manual-load", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
        print(ui.manualLoadResult, result);
    } catch (error) {
        print(ui.manualLoadResult, { error: error.message });
    }
}

async function onProcessClick() {
    try {
        const result = await api("/api/manual-load/process", { method: "POST" });
        print(ui.processResult, result);
    } catch (error) {
        print(ui.processResult, { error: error.message });
    }
}

async function loadSystemMode() {
    const info = await api("/api/system/mode");
    const isLegacy = Boolean(info.legacyModeEnabled);
    ui.modeBadge.textContent = isLegacy ? "Режим: LEGACY FILE PIPELINE (временно включён)" : "Режим: API + FRONTEND (основной)";
    ui.modeBadge.classList.toggle("legacy", isLegacy);
}

function bindEvents() {
    ui.manualLoadForm?.addEventListener("submit", onManualLoadSubmit);
    ui.processBtn?.addEventListener("click", onProcessClick);
}

async function init() {
    bindEvents();

    try {
        await loadSystemMode();
    } catch {
        ui.modeBadge.textContent = "Режим: не удалось определить";
    }
}

init();
