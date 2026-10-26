const resources = {
    departments: { title: "Departments", description: "Organize the academic departments in your college.", fields: [
        ["name", "Department name", "text"], ["code", "Department code", "text", null, true]
    ], columns: [["name", "Department"], ["code", "Code"]] },
    courses: { title: "Courses & semesters", description: "Set up courses and the number of semesters in each course.", fields: [
        ["name", "Course name", "text"], ["code", "Course code", "text"], ["semesterCount", "Number of semesters", "number"], ["departmentId", "Department", "select", "departments"]
    ], columns: [["name", "Course"], ["code", "Code"], ["semesterCount", "Semesters"], ["departmentName", "Department"]] },
    classes: { title: "Classes & student batches", description: "Create sections or batches that need their own timetable.", fields: [
        ["name", "Class or batch name", "text"], ["courseId", "Course", "select", "courses"], ["semester", "Semester", "number"], ["section", "Section", "text"], ["studentCount", "Number of students", "number"]
    ], columns: [["name", "Class"], ["courseName", "Course"], ["semester", "Semester"], ["section", "Section"], ["studentCount", "Students"]] },
    teachers: { title: "Teachers", description: "Manage staff records and active teaching status.", fields: [
        ["name", "Full name", "text"], ["email", "Email", "email", null, true], ["phone", "Phone", "text", null, true], ["departmentId", "Department (optional)", "select", "departments", true], ["active", "Active", "checkbox"]
    ], columns: [["name", "Teacher"], ["email", "Email"], ["phone", "Phone"], ["active", "Status"]] },
    subjects: { title: "Subjects", description: "Manage subjects. Mark practical subjects so the generator selects a lab.", fields: [
        ["name", "Subject name", "text"], ["code", "Subject code", "text"], ["departmentId", "Department (optional)", "select", "departments", true], ["practical", "Practical subject", "checkbox"], ["active", "Active", "checkbox"]
    ], columns: [["name", "Subject"], ["code", "Code"], ["practical", "Practical"], ["active", "Status"]] },
    classrooms: { title: "Classrooms", description: "Add rooms. Set room type to LAB for practical subjects.", fields: [
        ["name", "Room name", "text"], ["capacity", "Capacity", "number"], ["roomType", "Room type (STANDARD or LAB)", "text", null, true], ["active", "Active", "checkbox"]
    ], columns: [["name", "Room"], ["roomType", "Type"], ["capacity", "Capacity"], ["active", "Status"]] },
    "time-slots": { title: "Time slots", description: "Create non-overlapping periods for the working day.", fields: [
        ["label", "Period label", "text"], ["startTime", "Start time", "time"], ["endTime", "End time", "time"], ["active", "Active", "checkbox"]
    ], columns: [["label", "Period"], ["startTime", "Starts"], ["endTime", "Ends"], ["active", "Status"]] },
    assignments: { title: "Teacher-subject assignments", description: "Assign eligible teachers to the subjects they can teach.", fields: [
        ["teacherId", "Teacher", "select", "teachers"], ["subjectId", "Subject", "select", "subjects"]
    ], columns: [["teacherName", "Teacher"], ["subjectName", "Subject"]] },
    requirements: { title: "Subject requirements", description: "Set how many weekly periods each class needs for every subject.", fields: [
        ["classId", "Class", "select", "classes"], ["subjectId", "Subject", "select", "subjects"], ["periodsPerWeek", "Periods per week", "number"]
    ], columns: [["className", "Class"], ["subjectName", "Subject"], ["periodsPerWeek", "Periods / week"]] },
    "teacher-availability": { title: "Teacher availability", description: "Mark teacher periods as available or unavailable. Unspecified periods are treated as available.", fields: [
        ["teacherId", "Teacher", "select", "teachers"], ["dayOfWeek", "Day", "day"], ["timeSlotId", "Time slot", "select", "time-slots"], ["available", "Available", "checkbox"]
    ], columns: [["teacherName", "Teacher"], ["dayOfWeek", "Day"], ["slotLabel", "Time slot"], ["available", "Availability"]] }
};

const dayNames = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];
const titles = { dashboard: "Dashboard", generate: "Generate timetable", timetable: "View timetable", conflicts: "Conflict report", settings: "Settings & help" };
const pages = new Set(["dashboard", ...Object.keys(resources), "generate", "timetable", "conflicts", "settings"]);
const view = document.querySelector("#view");
const notice = document.querySelector("#notice");
let currentPage = "dashboard";
let noticeTimer;
let navigationSequence = 0;

async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body) headers["Content-Type"] = "application/json";
    let response;
    try {
        response = await fetch(`/api/${path}`, { ...options, headers });
    } catch (error) {
        if (error instanceof TypeError) throw new Error("The server could not be reached. Check that CampusPlan is running.");
        throw error;
    }
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
        const message = data?.message || (response.status === 403 ? "This account does not have permission for that action."
            : `Request failed (${response.status}).`);
        throw new Error(message);
    }
    return data;
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

function showNotice(message, isError = false) {
    notice.textContent = message;
    notice.classList.toggle("error", isError);
    notice.classList.remove("hidden");
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => notice.classList.add("hidden"), 5000);
}

function heading(title, description, action = "") {
    return `<div class="page-heading"><div><h1>${escapeHtml(title)}</h1><p>${escapeHtml(description)}</p></div>${action}</div>`;
}

async function navigate(page, updateHistory = true) {
    if (!pages.has(page)) {
        showNotice(`The "${page}" page could not be found.`, true);
        return;
    }
    const sequence = ++navigationSequence;
    currentPage = page;
    if (updateHistory && window.location.hash !== `#${page}`) {
        window.history.pushState(null, "", `#${page}`);
    }
    document.querySelectorAll(".nav-link").forEach(button => button.classList.toggle("active", button.dataset.page === page));
    document.querySelector("#breadcrumb-title").textContent = titles[page] || resources[page]?.title || page;
    document.querySelector("#sidebar").classList.remove("open");
    if (page !== "settings") {
        view.innerHTML = `${heading(titles[page] || resources[page]?.title || "Page", "")}
            <div class="panel empty-state" role="status">Loading ${escapeHtml(titles[page] || resources[page]?.title || "page")}…</div>`;
    }
    try {
        if (page === "dashboard") await renderDashboard(sequence);
        else if (resources[page]) await renderResource(page, sequence);
        else if (page === "generate") await renderGenerator(sequence);
        else if (page === "timetable") await renderTimetable(sequence);
        else if (page === "conflicts") await renderConflicts(sequence);
        else if (page === "settings") await renderSettings();
    } catch (error) {
        if (sequence !== navigationSequence) return;
        view.innerHTML = `${heading(titles[page] || resources[page]?.title || "Page", "")}
            <section class="panel dashboard-feedback" role="alert"><p>${escapeHtml(error.message)}</p>
                <button class="button button-primary" type="button" id="retry-page">Retry</button></section>`;
        view.querySelector("#retry-page").addEventListener("click", () =>
            navigate(page).catch(retryError => showNotice(retryError.message, true)));
    }
}

async function renderDashboard(sequence) {
    const [dashboardResult, timetableResult] = await Promise.allSettled([
        api("dashboard"),
        api("timetable")
    ]);
    if (sequence !== navigationSequence) return;
    if (dashboardResult.status === "rejected") {
        const message = dashboardResult.reason?.message || "Dashboard data could not be loaded.";
        view.innerHTML = `${heading("Dashboard", "A quick overview of your academic timetable setup.")}
            <section class="panel dashboard-feedback" role="alert"><p>${escapeHtml(message)}</p>
                <button class="button button-primary" type="button" data-dashboard-retry>Retry</button></section>`;
        view.querySelector("[data-dashboard-retry]").addEventListener("click", () => navigate("dashboard").catch(error => showNotice(error.message, true)));
        return;
    }

    const data = dashboardResult.value;
    const entries = timetableResult.status === "fulfilled" && Array.isArray(timetableResult.value)
        ? timetableResult.value : null;
    const stats = [
        ["Departments", data.departments, "◫", "departments"],
        ["Courses", data.courses, "▤", "courses"],
        ["Teachers", data.teachers, "♙", "teachers"],
        ["Classes & batches", data.classes, "▣", "classes"],
        ["Subjects", data.subjects, "▧", "subjects"],
        ["Classrooms", data.classrooms, "⌂", "classrooms"],
        ["Timetable entries", data.periods, "◷", "timetable"]
    ];
    const overview = entries === null
        ? `<div class="dashboard-feedback" role="status"><p>${escapeHtml(timetableResult.reason?.message || "Timetable overview is currently unavailable.")}</p>
                <button class="button button-secondary button-small" type="button" data-dashboard-retry>Retry dashboard</button></div>`
        : entries.length
            ? `<div class="table-card"><div class="table-scroll"><table><thead><tr><th>Day / time</th><th>Class</th><th>Subject</th><th>Teacher</th><th>Classroom</th></tr></thead>
                <tbody>${entries.slice(0, 6).map(entry => `<tr><td>${escapeHtml(capitalize(entry.dayOfWeek))}<br>${escapeHtml(entry.startTime)}–${escapeHtml(entry.endTime)}</td>
                    <td>${escapeHtml(entry.className)}</td><td>${escapeHtml(entry.subjectName)}</td><td>${escapeHtml(entry.teacherName)}</td><td>${escapeHtml(entry.classroomName)}</td></tr>`).join("")}</tbody></table></div></div>
                ${entries.length > 6 ? `<p class="dashboard-caption">Showing 6 of ${entries.length} scheduled periods.</p>` : ""}`
            : `<div class="empty-state">No timetable entries yet. Generate a timetable after completing the academic setup.</div>`;
    view.innerHTML = `${heading("Dashboard", "A quick overview of your academic timetable setup.")}
        ${stats.some(([, value]) => Number(value) > 0) ? "" : `<div class="dashboard-empty-note" role="status">Your workspace is ready. Add academic records to get started; counts update from the database.</div>`}
        <div class="stat-grid dashboard-stat-grid">${stats.map(([label, value, icon, page]) => `<button class="stat-card dashboard-stat-card" type="button" data-go="${page}" aria-label="Open ${escapeHtml(label)}">
            <span><span class="stat-label">${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></span><span class="stat-icon">${icon}</span></button>`).join("")}</div>
        <section class="panel"><div class="panel-title"><div><h2>Quick actions</h2><p>Continue managing your academic schedule.</p></div></div>
            <div class="quick-grid dashboard-actions">
                <a class="quick-link" href="#" data-go="departments">Manage academic data<small>Departments, courses, classes, and more</small></a>
                <a class="quick-link" href="#" data-go="generate">Generate timetable<small>Create a clash-checked schedule</small></a>
                <a class="quick-link" href="#" data-go="timetable">View timetable<small>Review, filter, or export periods</small></a>
                <a class="quick-link" href="#" data-go="conflicts">Check conflicts<small>Review overlapping assignments</small></a>
            </div>
        </section>
        <section class="panel"><div class="panel-title"><div><h2>Timetable overview</h2><p>${entries === null ? "Latest scheduled periods could not be loaded." : `${entries.length} scheduled period(s) in your workspace.`}</p></div>
            <a class="button button-secondary button-small" href="#" data-go="timetable">View all</a></div>${overview}</section>`;
    view.querySelectorAll("[data-dashboard-retry]").forEach(button =>
        button.addEventListener("click", () => navigate("dashboard").catch(error => showNotice(error.message, true))));
}

async function renderResource(resource, sequence) {
    const config = resources[resource];
    const rows = await api(resource);
    if (sequence !== navigationSequence) return;
    const table = `<div class="table-card"><div class="table-scroll"><table><thead><tr>${config.columns.map(([, label]) => `<th>${escapeHtml(label)}</th>`).join("")}<th>Actions</th></tr></thead>
        <tbody id="resource-rows">${renderRows(config, rows)}</tbody></table></div></div>`;
    view.innerHTML = `${heading(config.title, config.description,
        `<button class="button button-primary" id="add-record">＋ Add record</button>`)}
        <div id="record-form-wrap"></div><div class="toolbar"><input type="search" id="record-search" placeholder="Search ${escapeHtml(config.title.toLowerCase())}..." aria-label="Search records"><span class="muted">${rows.length} record(s)</span></div>${table}`;
    document.querySelector("#add-record").addEventListener("click", () =>
        openForm(resource).catch(error => showNotice(error.message, true)));
    document.querySelector("#record-search").addEventListener("input", event => {
        const filter = event.target.value.toLowerCase();
        const filtered = rows.filter(row => JSON.stringify(row).toLowerCase().includes(filter));
        document.querySelector("#resource-rows").innerHTML = renderRows(config, filtered);
        document.querySelector("#record-count").textContent = `${filtered.length} record(s)`;
    });
    document.querySelector(".toolbar .muted").id = "record-count";
    document.querySelector("#resource-rows").addEventListener("click", event => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const row = rows.find(item => String(item.id) === button.dataset.id);
        if (button.dataset.action === "edit") {
            openForm(resource, row).catch(error => showNotice(error.message, true));
        }
        if (button.dataset.action === "delete") {
            deleteRecord(resource, row).catch(error => showNotice(error.message, true));
        }
    });
}

function displayValue(value) {
    if (typeof value === "boolean") return value ? "Active" : "No";
    return value == null || value === "" ? "—" : value;
}

function renderRows(config, rows) {
    if (!rows.length) return `<tr><td colspan="${config.columns.length + 1}"><div class="empty-state">No records yet. Use “Add record” to create the first one.</div></td></tr>`;
    return rows.map(row => `<tr>${config.columns.map(([key]) => `<td>${escapeHtml(displayValue(row[key]))}</td>`).join("")}
        <td><div class="row-actions"><button class="button button-secondary button-small" data-action="edit" data-id="${escapeHtml(row.id)}">Edit</button>
        <button class="button button-danger button-small" data-action="delete" data-id="${escapeHtml(row.id)}">Delete</button></div></td></tr>`).join("");
}

async function openForm(resource, row = null) {
    const config = resources[resource];
    const page = currentPage;
    let fieldsHtml = "";
    for (const [key, label, type, optionResource, optional] of config.fields) {
        let control;
        if (type === "select") {
            const options = await api(optionResource);
            if (page !== currentPage) return;
            control = `<select name="${key}" ${optional ? "" : "required"}><option value="">${optional ? "Not selected" : "Choose..."}</option>${options.map(option =>
                `<option value="${escapeHtml(option.id)}" ${String(row?.[key] ?? "") === String(option.id) ? "selected" : ""}>${escapeHtml(option.name || option.label || `${option.teacherName} — ${option.subjectName}`)}</option>`).join("")}</select>`;
        } else if (type === "day") {
            control = `<select name="${key}" required>${dayNames.map(day => `<option value="${day}" ${row?.[key] === day ? "selected" : ""}>${day[0] + day.slice(1).toLowerCase()}</option>`).join("")}</select>`;
        } else if (type === "checkbox") {
            control = `<input name="${key}" type="checkbox" ${row?.[key] ?? (key === "active") ? "checked" : ""}>`;
        } else {
            const value = row?.[key] ?? (key === "roomType" ? "STANDARD" : "");
            const max = type === "number" ? 'min="1" step="1"' : "";
            control = `<input name="${key}" type="${type}" value="${escapeHtml(value)}" ${type === "number" ? max : ""} ${type === "number" || type === "time" || type === "email" ? "" : "maxlength=\"160\""} ${optional ? "" : "required"}>`;
        }
        if (page !== currentPage) return;
        fieldsHtml += `<label class="field ${type === "checkbox" ? "checkbox-field" : ""}">${type === "checkbox" ? control : `<span>${escapeHtml(label)}</span>${control}`}${type === "checkbox" ? `<span>${escapeHtml(label)}</span>` : ""}</label>`;
    }
    const wrap = document.querySelector("#record-form-wrap");
    wrap.innerHTML = `<form class="form-card" id="record-form"><h2>${row ? "Update" : "Add"} ${escapeHtml(config.title.replace(/s$/, ""))}</h2>
        <div class="form-grid">${fieldsHtml}</div><div class="form-actions"><button class="button button-primary" type="submit">${row ? "Save changes" : "Create record"}</button>
        <button class="button button-secondary" id="cancel-form" type="button">Cancel</button></div></form>`;
    document.querySelector("#cancel-form").addEventListener("click", () => { wrap.innerHTML = ""; });
    document.querySelector("#record-form").addEventListener("submit", async event => {
        event.preventDefault();
        const body = {};
        new FormData(event.currentTarget).forEach((value, key) => { body[key] = value; });
        config.fields.forEach(([key, , type]) => {
            if (type === "checkbox") body[key] = event.currentTarget.elements[key].checked;
            if (type === "number") body[key] = Number(body[key]);
        });
        try {
            await api(row ? `${resource}/${row.id}` : resource, {
                method: row ? "PUT" : "POST", body: JSON.stringify(body)
            });
            showNotice(`${config.title} record ${row ? "updated" : "created"}.`);
            if (currentPage === resource) await navigate(resource);
        } catch (error) { showNotice(error.message, true); }
    });
    wrap.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

async function deleteRecord(resource, row) {
    if (!confirm(`Delete "${row.name || row.label || row.subjectName || row.className || "this record"}"? Records in use may not be deletable.`)) return;
    try {
        await api(`${resource}/${row.id}`, { method: "DELETE" });
        showNotice("Record deleted.");
        if (currentPage === resource) await navigate(resource);
    } catch (error) { showNotice(error.message, true); }
}

async function renderGenerator(sequence) {
    const [classes, slots] = await Promise.all([api("classes"), api("time-slots")]);
    if (sequence !== navigationSequence) return;
    view.innerHTML = `${heading("Generate timetable", "Choose the class, working days, and active periods. Existing schedules are only replaced after you confirm.")}
        <div class="generation-layout"><form class="form-card" id="generate-form">
            <h2>Schedule settings</h2>
            <label class="field"><span>Class / batch</span><select name="classId" required><option value="">Choose a class...</option>${classes.map(item => `<option value="${item.id}">${escapeHtml(item.name)} · semester ${item.semester} · ${escapeHtml(item.section)}</option>`).join("")}</select></label>
            <div class="field" style="margin-top:17px"><span>Working days</span><div class="check-list">${dayNames.map(day => `<label class="check-pill"><input type="checkbox" name="days" value="${day}" ${!["SATURDAY"].includes(day) ? "checked" : ""}>${day[0] + day.slice(1).toLowerCase()}</label>`).join("")}</div></div>
            <div class="field" style="margin-top:17px"><span>Periods to use</span><div class="check-list">${slots.filter(item => item.active).map(slot => `<label class="check-pill"><input type="checkbox" name="slots" value="${slot.id}" checked>${escapeHtml(slot.label)} (${escapeHtml(slot.startTime)}–${escapeHtml(slot.endTime)})</label>`).join("") || "<span>No active time slots found. Add them first.</span>"}</div></div>
            <label class="check-pill" style="margin-top:18px"><input type="checkbox" name="replaceExisting"> Replace this class's current timetable</label>
            <div class="inline-warning">Generation follows subject requirements and teacher assignments, avoids overlapping class periods, teacher clashes and room clashes, and selects LAB rooms for practical subjects. Unspecified teacher periods are treated as available.</div>
            <div class="form-actions"><button class="button button-primary" type="submit">✦ Generate schedule</button></div>
            <div id="generation-result"></div>
        </form><section class="panel"><div class="panel-title"><div><h2>Generation checklist</h2><p>The scheduler uses saved records from the database.</p></div></div>
            <div class="settings-list"><span>1. Create classes and their weekly subject requirements.</span><span>2. Assign active teachers to each required subject.</span><span>3. Add active rooms; practical subjects require room type <code>LAB</code>.</span><span>4. Set unavailable periods in Teacher availability.</span><span>5. Select non-overlapping active time slots and the working days.</span></div></section></div>`;
    document.querySelector("#generate-form").addEventListener("submit", async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const replaceExisting = form.elements.replaceExisting.checked;
        if (replaceExisting && !confirm("This will replace the selected class's existing timetable. Continue?")) return;
        const result = document.querySelector("#generation-result");
        result.innerHTML = "";
        try {
            const response = await api("timetable/generate", { method: "POST", body: JSON.stringify({
                classId: Number(form.elements.classId.value),
                days: [...form.querySelectorAll('input[name="days"]:checked')].map(input => input.value),
                timeSlotIds: [...form.querySelectorAll('input[name="slots"]:checked')].map(input => Number(input.value)),
                replaceExisting
            }) });
            result.innerHTML = `<div class="result-box">${escapeHtml(response.message)} <a href="#" data-go="timetable">View timetable</a></div>`;
            showNotice(response.message);
        } catch (error) {
            result.innerHTML = `<div class="result-box error">${escapeHtml(error.message)}</div>`;
        }
    });
}

async function renderTimetable(sequence) {
    const [classes, teachers, rooms, entries] = await Promise.all([
        api("classes"), api("teachers"), api("classrooms"), api("timetable")
    ]);
    if (sequence !== navigationSequence) return;
    let displayedEntries = entries;
    view.innerHTML = `${heading("Generated timetable", "Review, filter, print, or export scheduled periods as PDF.", `<button class="button button-secondary" id="print-timetable">Print</button> <button class="button button-primary" id="export-pdf">Export PDF</button>`)}
        <div class="toolbar"><select id="class-filter"><option value="">All classes</option>${classes.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join("")}</select>
        <select id="teacher-filter"><option value="">All teachers</option>${teachers.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join("")}</select>
        <select id="classroom-filter"><option value="">All classrooms</option>${rooms.map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join("")}</select>
        <span class="muted" id="timetable-count">${entries.length} period(s)</span></div>
        <h2 class="print-title">Automatic Timetable Generator — Weekly schedule</h2>
        <div class="table-card"><div class="table-scroll"><table class="weekly-grid" id="timetable-grid">${timetableGrid(entries)}</table></div></div>`;
    const reload = async () => {
        const classId = document.querySelector("#class-filter").value;
        const teacherId = document.querySelector("#teacher-filter").value;
        const classroomId = document.querySelector("#classroom-filter").value;
        const query = new URLSearchParams();
        if (classId) query.set("classId", classId);
        if (teacherId) query.set("teacherId", teacherId);
        if (classroomId) query.set("classroomId", classroomId);
        try {
            const filtered = await api(`timetable${query.size ? `?${query}` : ""}`);
            displayedEntries = filtered;
            document.querySelector("#timetable-grid").innerHTML = timetableGrid(filtered);
            document.querySelector("#timetable-count").textContent = `${filtered.length} period(s)`;
        } catch (error) {
            showNotice(error.message, true);
        }
    };
    document.querySelector("#class-filter").addEventListener("change", async () => {
        await reload();
    });
    document.querySelector("#teacher-filter").addEventListener("change", async () => {
        await reload();
    });
    document.querySelector("#classroom-filter").addEventListener("change", async () => {
        await reload();
    });
    document.querySelector("#print-timetable").addEventListener("click", () => window.print());
    document.querySelector("#export-pdf").addEventListener("click", () => exportPdf(displayedEntries));
}

function timetableGrid(entries) {
    if (!entries.length) return `<tbody><tr><td><div class="empty-state">No timetable entries found. Generate a schedule after setting up requirements.</div></td></tr></tbody>`;
    const days = [...new Set(entries.map(item => item.dayOfWeek))].sort((a, b) => dayNames.indexOf(a) - dayNames.indexOf(b));
    const periods = [...new Map(entries.map(item => [item.timeSlotId, item])).values()]
        .sort((a, b) => a.startTime.localeCompare(b.startTime));
    const header = `<thead><tr><th>Period / time</th>${days.map(day => `<th>${escapeHtml(capitalize(day))}</th>`).join("")}</tr></thead>`;
    const body = periods.map(period => `<tr><th>${escapeHtml(period.slotLabel)}<br>${escapeHtml(period.startTime)}–${escapeHtml(period.endTime)}</th>
        ${days.map(day => {
            const items = entries.filter(item => item.dayOfWeek === day && item.timeSlotId === period.timeSlotId);
            return `<td>${items.map(item => `<div class="period-card"><strong>${escapeHtml(item.subjectName)}</strong>
                <span>${escapeHtml(item.teacherName)}</span><small>${escapeHtml(item.classroomName)}${days.length > 0 ? ` · ${escapeHtml(item.className)}` : ""}</small></div>`).join("") || "—"}</td>`;
        }).join("")}</tr>`).join("");
    return `${header}<tbody>${body}</tbody>`;
}

function capitalize(value) {
    return String(value || "").toLowerCase().replace(/^\w/, letter => letter.toUpperCase());
}

function exportPdf(entries) {
    if (!window.jspdf || !window.jspdf.jsPDF) {
        showNotice("PDF library not loaded. Check your internet connection and try again.", true);
        return;
    }
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF({ orientation: "landscape", unit: "mm", format: "a4" });

    // ── Header ──────────────────────────────────────────────────────────────────
    doc.setFillColor(23, 43, 77);          // --navy
    doc.rect(0, 0, 297, 18, "F");
    doc.setTextColor(255, 255, 255);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(12);
    doc.text("CampusPlan — College Timetable", 14, 11.5);
    doc.setFont("helvetica", "normal");
    doc.setFontSize(8);
    const now = new Date().toLocaleString();
    doc.text(`Generated: ${now}`, 297 - 14, 11.5, { align: "right" });

    // ── Table ───────────────────────────────────────────────────────────────────
    const columns = [
        { header: "Day",       dataKey: "dayOfWeek" },
        { header: "Period",    dataKey: "slotLabel" },
        { header: "Start",     dataKey: "startTime" },
        { header: "End",       dataKey: "endTime" },
        { header: "Class",     dataKey: "className" },
        { header: "Subject",   dataKey: "subjectName" },
        { header: "Teacher",   dataKey: "teacherName" },
        { header: "Room",      dataKey: "classroomName" }
    ];

    const rows = entries.map(e => ({
        dayOfWeek:     capitalize(e.dayOfWeek  ?? ""),
        slotLabel:     e.slotLabel     ?? "",
        startTime:     e.startTime     ?? "",
        endTime:       e.endTime       ?? "",
        className:     e.className     ?? "",
        subjectName:   e.subjectName   ?? "",
        teacherName:   e.teacherName   ?? "",
        classroomName: e.classroomName ?? ""
    }));

    doc.autoTable({
        startY: 22,
        columns,
        body: rows,
        theme: "grid",
        styles: { fontSize: 8, cellPadding: 3, lineColor: [220, 226, 236], lineWidth: 0.25 },
        headStyles: { fillColor: [49, 94, 232], textColor: 255, fontStyle: "bold", fontSize: 8.5 },
        alternateRowStyles: { fillColor: [246, 248, 252] },
        columnStyles: {
            dayOfWeek:   { fontStyle: "bold", cellWidth: 22 },
            slotLabel:   { cellWidth: 22 },
            startTime:   { cellWidth: 18 },
            endTime:     { cellWidth: 18 }
        },
        margin: { left: 14, right: 14 }
    });

    // ── Footer on every page ────────────────────────────────────────────────────
    const pageCount = doc.getNumberOfPages();
    doc.setFont("helvetica", "normal");
    doc.setFontSize(7);
    doc.setTextColor(150);
    for (let i = 1; i <= pageCount; i++) {
        doc.setPage(i);
        doc.text(`Page ${i} of ${pageCount}`, 297 / 2, 205, { align: "center" });
    }

    doc.save("college-timetable.pdf");
}

async function renderConflicts(sequence) {
    const response = await api("conflicts");
    if (sequence !== navigationSequence) return;
    view.innerHTML = `${heading("Conflict report", "Review schedule overlaps, teacher availability, assignments, and weekly subject requirements.")}
        <section class="panel"><div class="panel-title"><div><h2>${escapeHtml(response.message)}</h2><p>New timetable generation is checked on the server and protected with database uniqueness constraints.</p></div></div>
        ${response.items.length ? `<div class="table-card"><div class="table-scroll"><table><thead><tr><th>Issue</th><th>Day / time</th><th>Affected schedule</th><th>Other schedule</th><th>Suggested correction</th></tr></thead><tbody>${response.items.map(item => {
            const schedule = entry => [entry.firstClass, entry.firstSubject, entry.firstTeacher, entry.firstClassroom].filter(Boolean).join(" · ");
            const otherSchedule = [item.secondClass, item.secondSubject, item.secondTeacher, item.secondClassroom].filter(Boolean).join(" · ");
            return `<tr><td>${escapeHtml(item.type)}</td><td>${escapeHtml(item.day)}<br>${escapeHtml(item.time)}</td><td>${escapeHtml(schedule(item) || "—")}</td><td>${escapeHtml(otherSchedule || "—")}</td><td>${escapeHtml(item.suggestion)}</td></tr>`;
        }).join("")}</tbody></table></div></div>` : `<div class="empty-state">${escapeHtml(response.message)}</div>`}
        </section>`;
}

async function renderSettings() {
    view.innerHTML = `${heading("Settings & help", "Local database setup information.")}
        <section class="panel"><div class="panel-title"><div><h2>Database setup</h2></div></div>
            <div class="settings-list"><span>Start MySQL in XAMPP, then create a database named <code>automatic_timetable</code> in phpMyAdmin.</span>
            <span>Default local connection is <code>localhost:3306</code> and username <code>root</code>. Set <code>$env:DB_PASSWORD</code> (and <code>$env:DB_USERNAME</code> if needed) in the same PowerShell window before running Maven.</span></div></section>`;
}

function bindNavigation() {
    document.querySelector("#main-nav").addEventListener("click", event => {
        const button = event.target.closest("button[data-page]");
        if (button) navigate(button.dataset.page).catch(error => showNotice(error.message, true));
    });
    view.addEventListener("click", event => {
        const link = event.target.closest("[data-go]");
        if (link) {
            event.preventDefault();
            navigate(link.dataset.go).catch(error => showNotice(error.message, true));
        }
    });
    document.querySelector("#menu-button").addEventListener("click", () => document.querySelector("#sidebar").classList.toggle("open"));
    window.addEventListener("popstate", () => {
        const page = window.location.hash.slice(1) || "dashboard";
        const destination = pages.has(page) ? page : "dashboard";
        navigate(destination, false).catch(error => showNotice(error.message, true));
        if (destination !== page) showNotice(`The "${page}" page could not be found.`, true);
    });
}

bindNavigation();
const initialPage = window.location.hash.slice(1) || "dashboard";
navigate(pages.has(initialPage) ? initialPage : "dashboard").catch(error => showNotice(error.message, true));
