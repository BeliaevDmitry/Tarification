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
    return studyPeriod || (isHighSchoolParallel(classToParallel(className)) ? "H1" : "YEAR");
}

function settingsForParallel(parallel = selectedParallel) {
    return (studyPeriodSettings || []).filter((x) => Number(x.parallelFrom) <= Number(parallel) && Number(x.parallelTo) >= Number(parallel));
}

function periodColumnsForParallel(parallel = selectedParallel) {
    return settingsForParallel(parallel).map((x) => ({ key: String(x.id), label: x.displayName, studyPeriod: x.studyPeriod }));
}



function hasSemesterSplitForClass(classRow) {
    const classRows = curriculumRows.filter((r) => r.className === classRow.className && r.numberSchoolBuilding === classRow.numberSchoolBuilding);
    const grouped = new Map();
    classRows.forEach((r) => {
        const key = `${r.subjectName}|${r.educationLevel}|${r.curriculumPart || "CORE"}`;
        if (!grouped.has(key)) grouped.set(key, []);
        grouped.get(key).push(r);
    });
    for (const rows of grouped.values()) {
        let h1 = 0, h2 = 0, year = 0;
        rows.forEach((r) => {
            const v = Number(r.plannedHours || 0);
            if (r.studyPeriod === "H1") h1 += v;
            else if (r.studyPeriod === "H2") h2 += v;
            else year += v;
        });
        if (year > 0) continue;
        if ((h1 > 0) !== (h2 > 0)) return true;
        if (h1 !== h2) return true;
    }
    return false;
}

function columnsForClass(classRow) {
    const parallel = classToParallel(classRow.className);
    const options = settingsForParallel(parallel);
    const split = hasSemesterSplitForClass(classRow);
    if (!split) {
        const year = options.find((o) => o.studyPeriod === "YEAR") || options[0];
        return year ? [{ key: String(year.id), label: year.displayName, studyPeriod: year.studyPeriod }] : [];
    }
    const h1 = options.find((o) => o.studyPeriod === "H1");
    const h2 = options.find((o) => o.studyPeriod === "H2");
    return [h1, h2].filter(Boolean).map((x) => ({ key: String(x.id), label: x.displayName, studyPeriod: x.studyPeriod }));
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
    const options = settingsForParallel(parallel);
    const selected = ui.formStudyPeriod.value;
    ui.formStudyPeriod.innerHTML = options.map((o) => `<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join('');
    ui.formStudyPeriod.value = options.some((o) => String(o.id) === selected) ? selected : String(options[0]?.id || '');

    if (ui.editForm?.elements.studyPeriod) {
        const classParallel = classToParallel(ui.editForm.elements.className?.value || selectedParallel);
        const dialogOptions = settingsForParallel(classParallel);
        const editSelect = ui.editForm.elements.studyPeriod;
        const current = editSelect.value;
        editSelect.innerHTML = dialogOptions.map((o) => `<option value="${esc(o.id)}">${esc(o.displayName)}</option>`).join('');
        editSelect.value = dialogOptions.some((o) => String(o.id) === current) ? current : String(dialogOptions[0]?.id || '');
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
                const perClass = {};
                values.forEach((v) => {
                    const classKey = `${v.numberSchoolBuilding}|${v.className}`;
                    if (!perClass[classKey]) perClass[classKey] = { year: null, h1: null, h2: null };
                    const item = {
                        hours: Number(v.plannedHours || 0),
                        subgroupRequired: Boolean(v.subgroupRequired),
                        subgroupCount: Number(v.subgroupCount || 0),
                        id: v.id,
                        studyPeriod: v.studyPeriod
                    };
                    if (v.studyPeriod === "H1") perClass[classKey].h1 = item;
                    else if (v.studyPeriod === "H2") perClass[classKey].h2 = item;
                    else perClass[classKey].year = item;
                });
                return { part, subjectName, educationLevel, perClass };
            });

        rows.push({ type: "part", part, title: PART_META[part].label });
        rows.push(...items.map((item) => ({ type: "subject", ...item })));
        rows.push({ type: "sum", part, title: `Сумма ${PART_META[part].short}` });
    });

    rows.splice(rows.findIndex((r) => r.type === "part" && r.part === "EXTRACURRICULAR"), 0,
        { type: "sum12", title: "Сумма О+Ф" });

    return rows;
}

function openEditById(id) {
    const existing = curriculumRows.find((r) => r.id === id);
    if (!existing) return;

    ui.editForm.elements.id.value = String(existing.id);
    ui.editForm.elements.className.value = existing.className || "";
    ui.editForm.elements.plannedHours.value = String(existing.plannedHours || 1);
    ui.editForm.elements.educationLevel.value = existing.educationLevel || "BASIC";
    ui.editForm.elements.subgroupRequired.value = String(Boolean(existing.subgroupRequired));
    ui.editForm.elements.studyPeriod.value = String(existing.studyPeriodSettingId || "");
    ui.editForm.elements.subgroup1Hours.value = existing.subgroup1Hours || existing.plannedHours || "";
    ui.editForm.elements.subgroup2Hours.value = existing.subgroup2Hours || existing.plannedHours || "";
    ui.editForm.elements.subgroup1EducationLevel.value = existing.subgroup1EducationLevel || existing.educationLevel || "BASIC";
    ui.editForm.elements.subgroup2EducationLevel.value = existing.subgroup2EducationLevel || existing.educationLevel || "BASIC";
    toggleSubgroupConfig(ui.editForm, ui.editForm.elements.subgroupRequired.value);
    syncStudyPeriodControls();
    ui.editDialog.showModal();
}

function classCellMarkup(cellInfo, rowMeta) {
    if (!cellInfo) return "";
    const year = cellInfo.year;
    const h1 = cellInfo.h1;
    const h2 = cellInfo.h2;

    if (year) {
        return `<button class="hours-cell ${rowMeta.educationLevel === "ADVANCED" ? "advanced-cell" : ""}" data-id="${esc(year.id)}">${esc(year.hours)}</button>`;
    }

    const left = h1 ? `<button class="hours-cell" data-id="${esc(h1.id)}">${esc(h1.hours)}</button>` : '<div class="hours-cell muted"></div>';
    const right = h2 ? `<button class="hours-cell" data-id="${esc(h2.id)}">${esc(h2.hours)}</button>` : '<div class="hours-cell muted"></div>';
    return `<div style="display:grid;grid-template-columns:1fr 1fr;gap:4px">${left}${right}</div>`;
}

function renderSummaryTable() {
    const selectedClasses = classesForSelectedContext();
    const classDescriptors = selectedClasses.map((c) => ({
        classKey: `${c.numberSchoolBuilding}|${c.className}`,
        className: c.className,
        classDirection: c.classDirection
    }));
    const rows = buildSummaryRows(selectedClasses);

    ui.summaryHead.innerHTML = "";
    ui.summaryBody.innerHTML = "";

    const directionRow = document.createElement("tr");
    directionRow.className = "summary-direction-row";
    directionRow.innerHTML = `<th rowspan="2">Блок / предмет / часы</th>${selectedClasses.map((c) => `<th>${esc(c.classDirection)}</th>`).join("")}`;
    const classRow = document.createElement("tr");
    classRow.className = "summary-class-row";
    classRow.innerHTML = selectedClasses.map((c) => `<th>${esc(c.className)}</th>`).join("");
    ui.summaryHead.appendChild(directionRow);
    ui.summaryHead.appendChild(classRow);

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        if (row.type === "part") {
            tr.className = "summary-part-row";
            tr.innerHTML = `<td>${esc(row.title)}</td>${classDescriptors.map(() => "<td></td>").join("")}`;
        } else if (row.type === "subject") {
            tr.innerHTML = `<td>${esc(row.subjectName)}</td>` + classDescriptors
                .map((col) => `<td class="hours-cell-wrap">${classCellMarkup(row.perClass[col.classKey], row)}</td>`)
                .join("");
        } else {
            const calc = classDescriptors.map((col) => {
                let h1 = 0, h2 = 0;
                const sourceRows = rows.filter((r) => r.type === "subject" && (row.type === "sum" ? r.part === row.part : (r.part === "CORE" || r.part === "FORMABLE")));
                sourceRows.forEach((s) => {
                    const info = s.perClass[col.classKey];
                    if (!info) return;
                    if (info.year) {
                        h1 += Number(info.year.hours || 0);
                        h2 += Number(info.year.hours || 0);
                    } else {
                        h1 += Number(info.h1?.hours || 0);
                        h2 += Number(info.h2?.hours || 0);
                    }
                });
                return `<td class="summary-value">${h1 || h2 ? `${h1}/${h2}` : ""}</td>`;
            }).join("");
            tr.className = "summary-sum-row";
            tr.innerHTML = `<td>${esc(row.title)}</td>${calc}`;
        }
        ui.summaryBody.appendChild(tr);
    });

    ui.summaryBody.querySelectorAll('.hours-cell[data-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const id = Number(btn.dataset.id);
            if (!Number.isFinite(id)) return;
            openEditById(id);
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
        studyPeriodSettingId: Number(f.get("studyPeriod") || 0) || null
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
    const [curriculum, classRows, buildingRows, subjectRows, settingRows] = await Promise.all([
        api("/api/curriculum"),
        api("/api/classroom-leadership"),
        api("/api/buildings"),
        api("/api/subjects"),
        api("/api/settings/study-periods")
    ]);
    curriculumRows = curriculum || [];
    subjects = subjectRows || [];
    studyPeriodSettings = settingRows || [];
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
            studyPeriodSettingId: Number(ui.editForm.elements.studyPeriod.value || 0) || null,
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
