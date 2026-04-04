const jsonHeaders = { "Content-Type": "application/json" };

const PART_META = {
    CORE: { label: "Основная часть", short: "основная", order: 1 },
    FORMABLE: { label: "Формируемая часть", short: "формируемая", order: 2 },
    EXTRACURRICULAR: { label: "Внеурочная деятельность", short: "внеурочная", order: 3 }
};

const PERIOD_META = {
    YEAR: { label: "Учебный год", short: "год" },
    H1: { label: "1 полугодие", short: "1П" },
    H2: { label: "2 полугодие", short: "2П" }
};

const ui = {
    parallelTabs: document.getElementById("parallel-tabs"),
    buildingFilter: document.getElementById("parallel-building-filter"),
    refreshBtn: document.getElementById("refresh-btn"),
    clearBtn: document.getElementById("clear-curriculum-btn"),
    result: document.getElementById("curriculum-result"),
    form: document.getElementById("subject-form"),
    formBuilding: document.getElementById("subject-building"),
    formClass: document.getElementById("subject-class"),
    formSubject: document.getElementById("subject-name"),
    formStudyPeriod: document.getElementById("subject-study-period"),
    importFile: document.getElementById("curriculum-import-file"),
    importBtn: document.getElementById("import-curriculum-btn"),
    exportBtn: document.getElementById("export-curriculum-btn"),
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
let subjects = [];
let studyPeriodSettings = [];

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
function classToParallel(className) { const m = norm(className).match(/^(\d{1,2})/); return m ? Number(m[1]) : null; }
function levelShort(v) { return v === "ADVANCED" ? "У" : "Б"; }
function subjectTypeByPart(part) { return part === "EXTRACURRICULAR" ? "EXTRACURRICULAR" : "CORE_FORMABLE"; }
function isHighSchoolParallel(parallel = selectedParallel) { return Number(parallel) >= 10; }
function normalizeStudyPeriod(className, studyPeriod) {
    return isHighSchoolParallel(classToParallel(className))
        ? (studyPeriod === "H2" ? "H2" : "H1")
        : (studyPeriod || "YEAR");
}

function periodColumnsForParallel(parallel = selectedParallel) {
    return isHighSchoolParallel(parallel)
        ? [{ key: "H1", label: PERIOD_META.H1.label }, { key: "H2", label: PERIOD_META.H2.label }]
        : [{ key: "YEAR", label: PERIOD_META.YEAR.label }];
}

function periodOptionsForParallel(parallel) {
    const p = Number(parallel || selectedParallel || 1);
    const matched = (studyPeriodSettings || [])
        .filter((s) => Number(s.parallelFrom) <= p && p <= Number(s.parallelTo))
        .sort((a, b) => Number(a.parallelFrom) - Number(b.parallelFrom)
            || Number(a.parallelTo) - Number(b.parallelTo)
            || String(a.displayName || "").localeCompare(String(b.displayName || ""), "ru"));
    if (!matched.length) {
        return isHighSchoolParallel(p) ? ["H1", "H2"] : ["YEAR"];
    }
    const unique = [];
    const seen = new Set();
    matched.forEach((m) => {
        const key = String(m.studyPeriod || "").toUpperCase();
        if (!["YEAR", "H1", "H2"].includes(key) || seen.has(key)) return;
        seen.add(key);
        unique.push(key);
    });
    return unique.length ? unique : (isHighSchoolParallel(p) ? ["H1", "H2"] : ["YEAR"]);
}

function periodSelectItemsForParallel(parallel) {
    const p = Number(parallel || selectedParallel || 1);
    const matched = (studyPeriodSettings || [])
        .filter((s) => Number(s.parallelFrom) <= p && p <= Number(s.parallelTo))
        .sort((a, b) => Number(a.parallelFrom) - Number(b.parallelFrom)
            || Number(a.parallelTo) - Number(b.parallelTo)
            || String(a.displayName || "").localeCompare(String(b.displayName || ""), "ru"));
    const items = matched
        .map((item) => ({
            value: String(item.settingKey || "").trim(),
            label: String(item.displayName || "").trim(),
            studyPeriod: String(item.studyPeriod || "").toUpperCase()
        }))
        .filter((item) => item.value && item.label && ["YEAR", "H1", "H2"].includes(item.studyPeriod));
    if (items.length) return items;
    return periodOptionsForParallel(p).map((key) => ({ value: key, label: PERIOD_META[key]?.label || key, studyPeriod: key }));
}

function renderStudyPeriodSelect(selectEl, parallel, currentValue) {
    if (!selectEl) return;
    const items = periodSelectItemsForParallel(parallel);
    const html = items.map((item) => `<option value="${esc(item.value)}">${esc(item.label)}</option>`).join("");
    selectEl.innerHTML = html;
    const allowed = items.map((i) => i.value);
    if (allowed.includes(currentValue)) {
        selectEl.value = currentValue;
        return;
    }
    const byPeriod = items.find((i) => i.studyPeriod === currentValue);
    selectEl.value = byPeriod?.value || allowed[0] || "YEAR";
}

function studyPeriodFromSelection(className, selectedValue) {
    const raw = String(selectedValue || "").trim();
    if (["YEAR", "H1", "H2"].includes(raw)) {
        return normalizeStudyPeriod(className, raw);
    }
    const found = (studyPeriodSettings || []).find((s) => String(s.settingKey || "").trim() === raw);
    const period = String(found?.studyPeriod || "").toUpperCase();
    return normalizeStudyPeriod(className, ["YEAR", "H1", "H2"].includes(period) ? period : "YEAR");
}

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
            syncStudyPeriodControls();
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

function syncStudyPeriodControls() {
    const parallel = classToParallel(ui.formClass.value) || selectedParallel;
    if (ui.formStudyPeriod) {
        renderStudyPeriodSelect(ui.formStudyPeriod, parallel, ui.formStudyPeriod.value);
    }
    if (ui.editForm?.elements.studyPeriod) {
        const p = classToParallel(ui.editForm.elements.className?.value || selectedParallel);
        renderStudyPeriodSelect(ui.editForm.elements.studyPeriod, p, ui.editForm.elements.studyPeriod.value);
    }
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
                const perClassPeriod = {};
                values.forEach((v) => {
                    const period = normalizeStudyPeriod(v.className, v.studyPeriod);
                    const columnPeriod = isHighSchoolParallel(classToParallel(v.className)) ? period : "YEAR";
                    perClassPeriod[`${v.numberSchoolBuilding}|${v.className}|${columnPeriod}`] = {
                        hours: Number(v.plannedHours || 0),
                        subgroupRequired: Boolean(v.subgroupRequired),
                        subgroupCount: Number(v.subgroupCount || 0),
                        id: v.id,
                        studyPeriod: period
                    };
                });
                return { part, subjectName, educationLevel, perClassPeriod };
            });

        rows.push({ type: "part", part, title: PART_META[part].label });
        rows.push(...items.map((item) => ({ type: "subject", ...item })));
        rows.push({ type: "sum", part, title: `Сумма ${PART_META[part].short}` });
    });

    rows.splice(rows.findIndex((r) => r.type === "part" && r.part === "EXTRACURRICULAR"), 0,
        { type: "sum12", title: "Сумма О+Ф" });

    return rows;
}

function cellHoursMarkup(info, rowMeta) {
    if (!info) return "";
    const mark = info.subgroupRequired ? `<span class="subgroup-mark" title="Деление на подгруппы">д</span>` : "";
    const advancedClass = rowMeta.educationLevel === "ADVANCED" ? "advanced-cell" : "";
    const periodClass = info.studyPeriod && info.studyPeriod !== "YEAR" ? "period-accent-cell" : "";
    const lvl = `<span class="mini-level">${esc(levelShort(rowMeta.educationLevel))}</span>`;
    const period = info.studyPeriod && info.studyPeriod !== "YEAR" ? `<span class="mini-level">${esc(PERIOD_META[info.studyPeriod]?.short || info.studyPeriod)}</span>` : "";
    return `<button class="hours-cell ${advancedClass} ${periodClass}" data-id="${esc(info.id)}" data-hours="${esc(info.hours)}">${esc(info.hours)}${mark}${lvl}${period}</button>`;
}

function renderSummaryTable() {
    const selectedClasses = classesForSelectedContext();
    const periodColumns = periodColumnsForParallel();
    const columnDescriptors = selectedClasses.flatMap((c) => periodColumns.map((period) => ({
        classKey: `${c.numberSchoolBuilding}|${c.className}`,
        columnKey: `${c.numberSchoolBuilding}|${c.className}|${period.key}`,
        className: c.className,
        classDirection: c.classDirection,
        studyPeriod: period.key,
        periodLabel: period.label
    })));
    const rows = buildSummaryRows(selectedClasses);

    ui.summaryHead.innerHTML = "";
    ui.summaryBody.innerHTML = "";

    const directionRow = document.createElement("tr");
    directionRow.className = "summary-direction-row";
    directionRow.innerHTML = `<th rowspan="2">Блок / предмет / часы</th>${selectedClasses.map((c) => `<th colspan="${periodColumns.length}">${esc(c.classDirection)}</th>`).join("")}`;
    const classRow = document.createElement("tr");
    classRow.className = "summary-class-row";
    classRow.innerHTML = selectedClasses.map((c) => periodColumns.map((period) => `<th>${esc(c.className)}${periodColumns.length > 1 ? `<div class="muted">${esc(period.label)}</div>` : ""}</th>`).join("")).join("");
    ui.summaryHead.appendChild(directionRow);
    ui.summaryHead.appendChild(classRow);

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        if (row.type === "part") {
            tr.className = "summary-part-row";
            tr.innerHTML = `<td>${esc(row.title)}</td>${columnDescriptors.map(() => "<td></td>").join("")}`;
        } else if (row.type === "subject") {
            tr.innerHTML = `<td>${esc(row.subjectName)}</td>` + columnDescriptors.map((col) => `<td class="hours-cell-wrap">${cellHoursMarkup(row.perClassPeriod[col.columnKey], row)}</td>`).join("");
        } else {
            const calc = columnDescriptors.map((col) => {
                let value = 0;
                if (row.type === "sum") {
                    value = rows.filter((r) => r.type === "subject" && r.part === row.part).reduce((acc, s) => acc + (s.perClassPeriod[col.columnKey]?.hours || 0), 0);
                } else if (row.type === "sum12") {
                    value = rows.filter((r) => r.type === "subject" && (r.part === "CORE" || r.part === "FORMABLE")).reduce((acc, s) => acc + (s.perClassPeriod[col.columnKey]?.hours || 0), 0);
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
            ui.editForm.elements.className.value = existing.className || "";
            ui.editForm.elements.plannedHours.value = String(existing.plannedHours || 1);
            ui.editForm.elements.educationLevel.value = existing.educationLevel || "BASIC";
            ui.editForm.elements.subgroupRequired.value = String(Boolean(existing.subgroupRequired));
            ui.editForm.elements.studyPeriod.value = normalizeStudyPeriod(existing.className, existing.studyPeriod);
            ui.editForm.elements.subgroup1Hours.value = existing.subgroup1Hours || existing.plannedHours || "";
            ui.editForm.elements.subgroup2Hours.value = existing.subgroup2Hours || existing.plannedHours || "";
            ui.editForm.elements.subgroup1EducationLevel.value = existing.subgroup1EducationLevel || existing.educationLevel || "BASIC";
            ui.editForm.elements.subgroup2EducationLevel.value = existing.subgroup2EducationLevel || existing.educationLevel || "BASIC";
            toggleSubgroupConfig(ui.editForm, ui.editForm.elements.subgroupRequired.value);
            syncStudyPeriodControls();
            ui.editDialog.showModal();
        });
    });
}

function renderSubjectOptions() {
    const part = ui.form.elements.curriculumPart.value || "CORE";
    const expectedType = subjectTypeByPart(part);
    const selected = ui.formSubject.value;
    ui.formSubject.innerHTML = '<option value="">Выберите предмет</option>';
    subjects.filter((s) => s.subjectType === expectedType)
        .sort((a,b)=>String(a.subjectName).localeCompare(String(b.subjectName),"ru"))
        .forEach((s) => { ui.formSubject.innerHTML += `<option value="${esc(s.subjectName)}">${esc(s.subjectName)}</option>`; });
    if (selected) ui.formSubject.value = selected;
}

function normalizeForm() {
    const f = new FormData(ui.form);
    const className = norm(f.get("className"));
    return {
        numberSchoolBuilding: norm(f.get("numberSchoolBuilding")),
        className,
        subjectName: norm(f.get("subjectName")),
        plannedHours: Number(f.get("plannedHours") || 0),
        educationLevel: f.get("educationLevel"),
        subgroupRequired: String(f.get("subgroupRequired")) === "true",
        subgroupCount: 2,
        subgroup1Hours: Number(f.get("subgroup1Hours") || 0) || null,
        subgroup1EducationLevel: f.get("subgroup1EducationLevel") || null,
        subgroup2Hours: Number(f.get("subgroup2Hours") || 0) || null,
        subgroup2EducationLevel: f.get("subgroup2EducationLevel") || null,
        curriculumPart: f.get("curriculumPart"),
        studyPeriod: studyPeriodFromSelection(className, f.get("studyPeriod"))
    };
}

async function importCurriculumFile() {
    const file = ui.importFile?.files?.[0];
    if (!file) {
        print({ error: "Выберите Excel-файл для импорта" });
        return;
    }

    const form = new FormData();
    form.append("file", file);

    try {
        const result = await api("/api/curriculum/import", { method: "POST", body: form });
        print({ status: "imported", ...result });
        ui.importFile.value = "";
        await reload();
    } catch (error) {
        print({ error: error.message });
    }
}

async function exportCurriculumFile() {
    try {
        const response = await fetch("/api/curriculum/export");
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || `HTTP ${response.status}`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "curriculum-editable.xlsx";
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        print({ status: "exported", file: "curriculum-editable.xlsx" });
    } catch (error) {
        print({ error: error.message });
    }
}

async function reload() {
    const [curriculum, classRows, buildingRows, subjectRows, settingsRows] = await Promise.all([
        api("/api/curriculum"),
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/subjects"),
        api("/api/settings/study-periods")
    ]);
    curriculumRows = curriculum || [];
    subjects = subjectRows || [];
    studyPeriodSettings = settingsRows || [];
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
    renderSubjectOptions();
    syncStudyPeriodControls();
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
    ui.formClass.addEventListener("change", syncStudyPeriodControls);
    ui.form.elements.curriculumPart.addEventListener("change", renderSubjectOptions);
    ui.buildingFilter.addEventListener("change", () => {
        selectedBuilding = norm(ui.buildingFilter.value);
        renderClassOptions();
        syncStudyPeriodControls();
        renderSummaryTable();
    });

    ui.refreshBtn.addEventListener("click", () => reload().catch((error) => print({ error: error.message })));
    ui.importBtn?.addEventListener("click", importCurriculumFile);
    ui.exportBtn?.addEventListener("click", exportCurriculumFile);
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
            subgroupCount: 2,
            studyPeriod: studyPeriodFromSelection(existing.className, ui.editForm.elements.studyPeriod.value),
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

bindEvents();
toggleSubgroupConfig(ui.subgroupConfig, ui.subgroupRequired.value);
syncStudyPeriodControls();
reload().catch((error) => print({ error: error.message }));
