const jsonHeaders = { "Content-Type": "application/json" };

/**
 * КЛЮЧЕВОЕ: единая функция запроса к API.
 * Если захотите поменять базовый URL (например, прокси/другой порт),
 * править удобнее всего здесь.
 */
async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    const body = text ? JSON.parse(text) : null;

    if (!response.ok) {
        throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    }

    return body;
}

function print(elId, value) {
    document.getElementById(elId).textContent = JSON.stringify(value, null, 2);
}

function formToObject(form) {
    const fd = new FormData(form);
    const obj = Object.fromEntries(fd.entries());

    // КЛЮЧЕВОЕ: пустые поля отправляем как null, чтобы сервер применял свои дефолты.
    Object.keys(obj).forEach((k) => {
        if (obj[k] === "") obj[k] = null;
    });

    // КЛЮЧЕВОЕ: числовые поля приводим к Number.
    if (obj.load != null) obj.load = Number(obj.load);
    if (obj.groupLoad != null) obj.groupLoad = Number(obj.groupLoad);

    return obj;
}

async function loadSubjects() {
    const subjects = await api("/api/naming-mesh/subjects");
    const select = document.getElementById("subject-select");
    select.innerHTML = "";

    subjects.forEach((s) => {
        const option = document.createElement("option");
        option.value = s;
        option.textContent = s;
        select.appendChild(option);
    });
}

async function loadClasses() {
    const subject = document.getElementById("subject-select").value;
    const classes = await api(`/api/naming-mesh/subjects/${encodeURIComponent(subject)}/classes`);
    const select = document.getElementById("class-select");
    select.innerHTML = "";

    classes.forEach((c) => {
        const option = document.createElement("option");
        option.value = c;
        option.textContent = c;
        select.appendChild(option);
    });
}

async function loadMappings() {
    const subject = document.getElementById("subject-select").value;
    const className = document.getElementById("class-select").value;

    const query = new URLSearchParams({ subjectName: subject });
    if (className) query.set("className", className);

    const data = await api(`/api/naming-mesh/mappings?${query.toString()}`);
    const body = document.getElementById("mappings-table-body");
    body.innerHTML = "";

    data.forEach((row) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${row.subjectName ?? ""}</td>
            <td>${row.className ?? ""}</td>
            <td>${row.groupNameEducationalPlan ?? ""}</td>
            <td>${row.classNameMesh ?? ""}</td>
            <td>${row.groupNameMesh ?? ""}</td>
        `;
        body.appendChild(tr);
    });
}

document.getElementById("manual-load-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
        const payload = formToObject(e.target);
        const result = await api("/api/manual-load", {
            method: "POST",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });
        print("manual-load-result", result);
    } catch (error) {
        print("manual-load-result", { error: error.message });
    }
});

document.getElementById("process-btn").addEventListener("click", async () => {
    try {
        const result = await api("/api/manual-load/process", { method: "POST" });
        print("process-result", result);
    } catch (error) {
        print("process-result", { error: error.message });
    }
});

document.getElementById("mapping-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
        const payload = formToObject(e.target);

        // КЛЮЧЕВОЕ: здесь сохраняется ручная привязка УП -> МЭШ.
        // Если classNameMesh/groupNameMesh не заданы, сервер подставит значения из УП.
        const result = await api("/api/naming-mesh/mappings", {
            method: "PUT",
            headers: jsonHeaders,
            body: JSON.stringify(payload)
        });

        print("mapping-result", result);
        await loadMappings();
    } catch (error) {
        print("mapping-result", { error: error.message });
    }
});

document.getElementById("load-subjects-btn").addEventListener("click", () => loadSubjects().catch((e) => print("mapping-result", { error: e.message })));
document.getElementById("load-classes-btn").addEventListener("click", () => loadClasses().catch((e) => print("mapping-result", { error: e.message })));
document.getElementById("load-mappings-btn").addEventListener("click", () => loadMappings().catch((e) => print("mapping-result", { error: e.message })));
