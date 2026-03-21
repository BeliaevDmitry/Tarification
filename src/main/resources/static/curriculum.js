const jsonHeaders = { "Content-Type": "application/json" };

const PART_META = {
    CORE: { label: "1 блок: Основная часть", short: "1 блок", order: 1 },
    FORMABLE: { label: "2 блок: Формируемая часть", short: "2 блок", order: 2 },
    EXTRACURRICULAR: { label: "3 блок: Внеурочная деятельность", short: "3 блок", order: 3 }
};

const ui = {
    parallelTabs: document.getElementById("parallel-tabs"),
    buildingFilter: document.getElementById("parallel-building-filter"),
    refreshBtn: document.getElementById("refresh-btn"),
    clearBtn: document.getElementById("clear-curriculum-btn"),
    importFile: document.getElementById("curriculum-import-file"),
    importBtn: document.getElementById("curriculum-import-btn"),
    bulkFile: document.getElementById("curriculum-bulk-file"),
    bulkText: document.getElementById("curriculum-bulk-json"),
    bulkBtn: document.getElementById("curriculum-bulk-upload-btn"),
    result: document.getElementById("curriculum-result"),
    form: document.getElementById("subject-form"),
    formBuilding: document.getElementById("subject-building"),
    formClass: document.getElementById("subject-class"),
    subgroupRequired: document.getElementById("subgroup-required"),
    subgroupConfig: document.getElementById("subgroup-config"),
    summaryHead: document.getElementById("summary-head"),
    summaryBody: document.getElementById("summary-body"),
    editDialog: document.getElementById("curriculum-edit-dialog"),
    editForm: document.getElementById("curriculum-edit-form"),
    deleteItemBtn: document.getElementById("delete-curriculum-item"),
    closeDialogBtn: document.getElementById("close-curriculum-dialog")
};

let selectedParallel = 1;
let selectedBuilding = "";
let buildings = [];
let classes = [];
let curriculumRows = [];

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function esc(v) {
    return String(v ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function print(v) { ui.result.textContent = JSON.stringify(v, null, 2); }
function norm(v) { return String(v || "").trim(); }
function sortRu(arr) { return [...arr].sort((a, b) => String(a).localeCompare(String(b), "ru")); }
function classToParallel(className) { const m = norm(className).match(/^(\d{1,2})/); return m ? Number(m[1]) : null; }
function levelLabel(v) { return v === "ADVANCED" ? "Углублённый" : "Базовый"; }
function levelBadge(v) { return v === "ADVANCED" ? "У" : "Б"; }

function toggleSubgroupConfig(container, requiredValue) {
    const required = String(requiredValue) === "true";
    if (!container) return;
    container.classList.toggle("hidden", !required);
}

function classesForSelectedContext() {
    return classes
        .filter((c) => classToParallel(c.className) === selectedParallel)
        .filter((c) => !selectedBuilding || c.numberSchoolBuilding === selectedBuilding)
        .sort((a, b) => `${a.numberSchoolBuilding}|${a.className}`.localeCompare(`${b.numberSchoolBuilding}|${b.className}`, "ru"));
}

function renderParallelTabs() {
    ui.parallelTabs.innerHTML = "";
    for (let p = 1; p <= 11; p++) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `parallel-tab ${p === selectedParallel ? "active" : ""}`;
        btn.textContent = `${p} параллель`;
        btn.addEventListener("click", () => {
            selectedParallel = p;
            syncSelectedBuilding();
            renderParallelTabs();
            renderBuildingFilter();
            renderClassOptions();
            renderSummaryTable();
        });
        ui.parallelTabs.appendChild(btn);
    }
}

function syncSelectedBuilding() {
    const available = classes
        .filter((c) => classToParallel(c.className) === selectedParallel)
        .map((c) => c.numberSchoolBuilding);
    if (!available.includes(selectedBuilding)) {
        selectedBuilding = available[0] || "";
    }
}

function renderBuildingFilter() {
    const available = Array.from(new Set(classes
        .filter((c) => classToParallel(c.className) === selectedParallel)
        .map((c) => c.numberSchoolBuilding))).sort((a, b) => String(a).localeCompare(String(b), "ru"));

    ui.buildingFilter.innerHTML = "<option value=''>Все корпуса</option>";
    available.forEach((code) => {
        const b = buildings.find((row) => row.code === code);
        ui.buildingFilter.innerHTML += `<option value="${esc(code)}">${esc(code)} — ${esc(b?.name || code)}</option>`;
    });
    ui.buildingFilter.value = selectedBuilding;

    ui.formBuilding.innerHTML = "<option value=''>Выберите корпус</option>";
    available.forEach((code) => {
        const b = buildings.find((row) => row.code === code);
        ui.formBuilding.innerHTML += `<option value="${esc(code)}">${esc(code)} — ${esc(b?.name || code)}</option>`;
    });
    if (selectedBuilding) ui.formBuilding.value = selectedBuilding;
}

function renderClassOptions() {
    const building = norm(ui.formBuilding.value) || selectedBuilding;
    const items = classes
        .filter((c) => classToParallel(c.className) === selectedParallel)
        .filter((c) => !building || c.numberSchoolBuilding === building)
        .sort((a, b) => String(a.className).localeCompare(String(b.className), "ru"));

    ui.formClass.innerHTML = "<option value=''>Выберите класс</option>";
    items.forEach((c) => {
        ui.formClass.innerHTML += `<option value="${esc(c.className)}">${esc(c.className)} (${esc(c.classDirection)})</option>`;
    });
}

function buildSummaryRows(selectedClasses) {
    const byPart = { CORE: [], FORMABLE: [], EXTRACURRICULAR: [] };
    const classSet = new Set(selectedClasses.map((c) => `${c.numberSchoolBuilding}|${c.className}`));

    curriculumRows.forEach((r) => {
        const key = `${r.numberSchoolBuilding}|${r.className}`;
        if (!classSet.has(key)) return;
        byPart[r.curriculumPart || "CORE"].push(r);
    });

    const rows = [];
    ["CORE", "FORMABLE", "EXTRACURRICULAR"].forEach((part) => {
        const grouped = new Map();
        byPart[part].forEach((r) => {
            const gk = `${r.subjectName}|${r.educationLevel}`;
            if (!grouped.has(gk)) grouped.set(gk, []);
            grouped.get(gk).push(r);
        });

        const items = Array.from(grouped.entries())
            .sort((a, b) => a[0].localeCompare(b[0], "ru"))
            .map(([key, values]) => {
                const [subjectName, educationLevel] = key.split("|");
                const perClass = {};
                values.forEach((v) => {
                    perClass[`${v.numberSchoolBuilding}|${v.className}`] = {
                        hours: Number(v.plannedHours || 0),
                        subgroupRequired: Boolean(v.subgroupRequired),
                        subgroupCount: Number(v.subgroupCount || 0),
                        id: v.id
                    };
                });
                return { part, subjectName, educationLevel, perClass, ids: values.map((v) => v.id) };
            });

        rows.push({ type: "part", part, title: PART_META[part].label });
        rows.push(...items.map((item) => ({ type: "subject", ...item })));
        rows.push({ type: "sum", part, title: `Сумма ${PART_META[part].short}` });
    });

    rows.splice(rows.findIndex((r) => r.type === "part" && r.part === "EXTRACURRICULAR"), 0,
        { type: "sum12", title: "Сумма 1 и 2 блоков" });

    return rows;
}

function cellHoursMarkup(info, rowMeta) {
    if (!info) return "";
    const mark = info.subgroupRequired ? `<span class="subgroup-mark" title="Деление на подгруппы">д</span>` : "";
    const advancedClass = rowMeta.educationLevel === "ADVANCED" ? "advanced-cell" : "";
    return `<button class="hours-cell ${advancedClass}" data-id="${esc(info.id)}" data-hours="${esc(info.hours)}">${esc(info.hours)}${mark}</button>`;
}



async function updateCurriculumEntry(entry, overrides = {}) {
    const payload = {
        numberSchoolBuilding: entry.numberSchoolBuilding,
        className: entry.className,
        subjectName: entry.subjectName,
        plannedHours: Number(overrides.plannedHours ?? entry.plannedHours ?? 0),
        subgroupRequired: Boolean(overrides.subgroupRequired ?? entry.subgroupRequired),
        subgroupCount: Number(overrides.subgroupCount ?? entry.subgroupCount ?? 0),
        educationLevel: overrides.educationLevel ?? entry.educationLevel,
        curriculumPart: overrides.curriculumPart ?? entry.curriculumPart
    };

    return api(`/api/curriculum/${entry.id}`, {
        method: "PATCH",
        headers: jsonHeaders,
        body: JSON.stringify(payload)
    });
}

function renderSummaryTable() {
    const selectedClasses = classesForSelectedContext();
    const classKeys = selectedClasses.map((c) => `${c.numberSchoolBuilding}|${c.className}`);
    const rows = buildSummaryRows(selectedClasses);

    ui.summaryHead.innerHTML = "";
    ui.summaryBody.innerHTML = "";

    const directionRow = document.createElement("tr");
    directionRow.innerHTML = `<th rowspan="2">Блок / предмет</th>${selectedClasses.map((c) => `<th>${esc(c.classDirection)}</th>`).join("")}`;
    const classRow = document.createElement("tr");
    classRow.innerHTML = selectedClasses.map((c) => `<th>${esc(c.className)}</th>`).join("");
    ui.summaryHead.appendChild(directionRow);
    ui.summaryHead.appendChild(classRow);

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        if (row.type === "part") {
            tr.className = "summary-part-row";
            tr.innerHTML = `<td>${esc(row.title)}</td>${classKeys.map(() => "<td></td>").join("")}`;
        } else if (row.type === "subject") {
            tr.innerHTML = `<td><button class="subject-level-cell" data-ids="${esc((row.ids || []).join(","))}" data-level="${esc(row.educationLevel)}"><span>${esc(row.subjectName)}</span><span class="level-corner-badge">${esc(levelBadge(row.educationLevel))}</span></button></td>`
                + classKeys.map((k) => `<td class="hours-cell-wrap">${cellHoursMarkup(row.perClass[k], row)}</td>`).join("");
        } else {
            const calc = classKeys.map((k) => {
                let value = 0;
                if (row.type === "sum") {
                    value = rows.filter((r) => r.type === "subject" && r.part === row.part).reduce((acc, s) => acc + (s.perClass[k]?.hours || 0), 0);
                } else if (row.type === "sum12") {
                    value = rows.filter((r) => r.type === "subject" && (r.part === "CORE" || r.part === "FORMABLE")).reduce((acc, s) => acc + (s.perClass[k]?.hours || 0), 0);
                }
                return `<td class="summary-value">${value || ""}</td>`;
            }).join("");
            tr.className = "summary-sum-row";
            tr.innerHTML = `<td>${esc(row.title)}</td>${calc}`;
        }
        ui.summaryBody.appendChild(tr);
    });

    ui.summaryBody.querySelectorAll(".hours-cell").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = Number(btn.dataset.id);
            if (!Number.isFinite(id)) return;
            const existing = curriculumRows.find((r) => r.id === id);
            if (!existing) return;

            ui.editForm.elements.id.value = String(existing.id);
            ui.editForm.elements.plannedHours.value = String(existing.plannedHours || 1);
            ui.editForm.elements.educationLevel.value = existing.educationLevel || "BASIC";
            ui.editForm.elements.subgroupRequired.value = String(Boolean(existing.subgroupRequired));
            ui.editForm.elements.subgroupCount.value = String(existing.subgroupCount || 2);
            ui.editForm.elements.subgroup1Hours.value = existing.subgroup1Hours || existing.plannedHours || "";
            ui.editForm.elements.subgroup2Hours.value = existing.subgroup2Hours || existing.plannedHours || "";
            ui.editForm.elements.subgroup1EducationLevel.value = existing.subgroup1EducationLevel || existing.educationLevel || "BASIC";
            ui.editForm.elements.subgroup2EducationLevel.value = existing.subgroup2EducationLevel || existing.educationLevel || "BASIC";
            toggleSubgroupConfig(ui.editForm, ui.editForm.elements.subgroupRequired.value);

            ui.editDialog.showModal();
        });
    });

    ui.summaryBody.querySelectorAll(".subject-level-cell").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const current = String(btn.dataset.level || "BASIC");
            const next = prompt("Уровень: BASIC или ADVANCED", current);
            if (next == null) return;

            const normalized = String(next).trim().toUpperCase();
            if (!["BASIC", "ADVANCED"].includes(normalized)) {
                print({ error: "Уровень должен быть BASIC или ADVANCED" });
                return;
            }

            const ids = String(btn.dataset.ids || "")
                .split(",")
                .map((v) => Number(v))
                .filter((v) => Number.isFinite(v));

            if (!ids.length) return;

            try {
                for (const id of ids) {
                    const existing = curriculumRows.find((r) => r.id === id);
                    if (!existing) continue;
                    await updateCurriculumEntry(existing, { educationLevel: normalized });
                }
                print({ status: "level-updated", ids, educationLevel: normalized });
                await reload();
            } catch (error) {
                print({ error: error.message });
            }
        });
    });
}

async function readTextInput(fileInput, textInput) {
    const file = fileInput?.files?.[0];
    if (file) return await file.text();
    return norm(textInput?.value);
}

async function bulkUploadCurriculum() {
    const raw = await readTextInput(ui.bulkFile, ui.bulkText);
    if (!raw) {
        print({ error: "Выберите JSON-файл или вставьте JSON-массив" });
        return;
    }

    let payload;
    try {
        payload = JSON.parse(raw);
    } catch (error) {
        print({ error: `Некорректный JSON: ${error.message}` });
        return;
    }

    if (!Array.isArray(payload)) {
        print({ error: "Ожидается JSON-массив записей учебного плана" });
        return;
    }

    const saved = await api("/api/curriculum/bulk", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
    print({ status: "bulk-loaded", total: saved.length });
    if (ui.bulkText) ui.bulkText.value = "";
    if (ui.bulkFile) ui.bulkFile.value = "";
    await reload();
}

async function importCurriculumFromExcel() {
    const file = ui.importFile?.files?.[0];
    if (!file) {
        print({ error: "Выберите Excel-файл учебного плана" });
        return;
    }

    const form = new FormData();
    form.append("file", file);
    const result = await api("/api/curriculum/import", { method: "POST", body: form });
    print(result);
    ui.importFile.value = "";
    await reload();
}

function normalizeForm() {
    const f = new FormData(ui.form);
    return {
        numberSchoolBuilding: norm(f.get("numberSchoolBuilding")),
        className: norm(f.get("className")),
        subjectName: norm(f.get("subjectName")),
        plannedHours: Number(f.get("plannedHours") || 0),
        educationLevel: f.get("educationLevel"),
        subgroupRequired: String(f.get("subgroupRequired")) === "true",
        subgroupCount: Number(f.get("subgroupCount") || 0),
        subgroup1Hours: Number(f.get("subgroup1Hours") || 0) || null,
        subgroup1EducationLevel: f.get("subgroup1EducationLevel") || null,
        subgroup2Hours: Number(f.get("subgroup2Hours") || 0) || null,
        subgroup2EducationLevel: f.get("subgroup2EducationLevel") || null,
        curriculumPart: f.get("curriculumPart")
    };
}

async function reload() {
    const [curriculum, classRows, buildingRows] = await Promise.all([
        api("/api/curriculum"),
        api("/api/classroom-leadership"),
        api("/api/buildings")
    ]);
    curriculumRows = curriculum || [];
    classes = (classRows || []).map((r) => ({
        numberSchoolBuilding: norm(r.numberSchoolBuilding),
        className: norm(r.className),
        classDirection: norm(r.classDirection)
    })).filter((r) => r.numberSchoolBuilding && r.className);

    buildings = (buildingRows || []).sort((a, b) => String(a.code).localeCompare(String(b.code), "ru"));

    syncSelectedBuilding();
    renderParallelTabs();
    renderBuildingFilter();
    renderClassOptions();
    renderSummaryTable();
}

function bindEvents() {
    ui.form.addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            const payload = normalizeForm();
            if (!payload.numberSchoolBuilding || !payload.className) throw new Error("Выберите корпус и класс из справочника классов");
            if (!classes.some((c) => c.numberSchoolBuilding === payload.numberSchoolBuilding && c.className === payload.className)) {
                throw new Error("Класс не найден в справочнике классов");
            }

            await api("/api/curriculum", { method: "POST", headers: jsonHeaders, body: JSON.stringify(payload) });
            print({ status: "saved", payload });
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.formBuilding.addEventListener("change", renderClassOptions);
    ui.buildingFilter.addEventListener("change", () => {
        selectedBuilding = norm(ui.buildingFilter.value);
        renderClassOptions();
        renderSummaryTable();
    });

    ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
    ui.importBtn?.addEventListener("click", () => importCurriculumFromExcel().catch((error) => print({ error: error.message })));
    ui.bulkBtn?.addEventListener("click", () => bulkUploadCurriculum().catch((error) => print({ error: error.message })));
    ui.subgroupRequired.addEventListener("change", () => {
        toggleSubgroupConfig(ui.subgroupConfig, ui.subgroupRequired.value);
        if (ui.subgroupRequired.value === "true") {
            const h = ui.form.elements.plannedHours.value || "1";
            ui.form.elements.subgroup1Hours.value = ui.form.elements.subgroup1Hours.value || h;
            ui.form.elements.subgroup2Hours.value = ui.form.elements.subgroup2Hours.value || h;
        }
    });

    ui.editForm.elements.subgroupRequired.addEventListener("change", () => {
        toggleSubgroupConfig(ui.editForm, ui.editForm.elements.subgroupRequired.value);
    });

    ui.closeDialogBtn.addEventListener("click", () => ui.editDialog.close());

    ui.deleteItemBtn.addEventListener("click", async () => {
        const id = Number(ui.editForm.elements.id.value);
        if (!Number.isFinite(id)) return;
        try {
            await api(`/api/curriculum/${id}`, { method: "DELETE" });
            ui.editDialog.close();
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.editForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const id = Number(ui.editForm.elements.id.value);
        const existing = curriculumRows.find((r) => r.id === id);
        if (!existing) return;

        const subgroupRequired = ui.editForm.elements.subgroupRequired.value === "true";
        const payload = {
            numberSchoolBuilding: existing.numberSchoolBuilding,
            className: existing.className,
            subjectName: existing.subjectName,
            curriculumPart: existing.curriculumPart,
            plannedHours: Number(ui.editForm.elements.plannedHours.value || 0),
            educationLevel: ui.editForm.elements.educationLevel.value,
            subgroupRequired,
            subgroupCount: Number(ui.editForm.elements.subgroupCount.value || 0),
            subgroup1Hours: subgroupRequired ? Number(ui.editForm.elements.subgroup1Hours.value || 0) : null,
            subgroup2Hours: subgroupRequired ? Number(ui.editForm.elements.subgroup2Hours.value || 0) : null,
            subgroup1EducationLevel: subgroupRequired ? ui.editForm.elements.subgroup1EducationLevel.value : null,
            subgroup2EducationLevel: subgroupRequired ? ui.editForm.elements.subgroup2EducationLevel.value : null
        };

        try {
            await api(`/api/curriculum/${id}`, { method: "PATCH", headers: jsonHeaders, body: JSON.stringify(payload) });
            ui.editDialog.close();
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });

    ui.clearBtn.addEventListener("click", async () => {
        try {
            await api("/api/curriculum", { method: "DELETE" });
            print({ status: "all curriculum rules deleted" });
            await reload();
        } catch (error) {
            print({ error: error.message });
        }
    });
}

function startAfterAuth() {
    bindEvents();
    toggleSubgroupConfig(ui.subgroupConfig, ui.subgroupRequired.value);
    reload().catch((error) => print({ error: error.message }));
}

if (window.initAuth) {
    window.initAuth().then(startAfterAuth).catch(() => {});
} else {
    document.addEventListener("auth-ready", startAfterAuth, { once: true });
}
