const CALENDAR_FILTER_STORAGE = 'home-calendar-audience-filters';
const CALENDAR_GROUP_CODES = ['DEPUTIES', 'ADMINISTRATION', 'FULL_ADMINISTRATION'];

function calendarStoredFilters() {
    const defaults = {
        enabled: {
            DEPUTIES: true,
            ADMINISTRATION: true,
            FULL_ADMINISTRATION: true,
            BUILDING: true,
            PERSONAL: true
        },
        buildingIds: null,
        personIds: null
    };
    try {
        const saved = JSON.parse(localStorage.getItem(CALENDAR_FILTER_STORAGE) || 'null');
        if (!saved || typeof saved !== 'object') return defaults;
        return {
            enabled: { ...defaults.enabled, ...(saved.enabled || {}) },
            buildingIds: Array.isArray(saved.buildingIds) ? saved.buildingIds : null,
            personIds: Array.isArray(saved.personIds) ? saved.personIds : null
        };
    } catch (_) {
        return defaults;
    }
}

const homeCalendar = {
    view: localStorage.getItem('probe-calendar-view') || 'month',
    cursor: new Date(),
    events: [],
    audiences: { people: [], buildings: [], groups: [], canEdit: false },
    audiencesLoaded: false,
    filters: calendarStoredFilters()
};

const homeCalendarUi = {
    root: document.getElementById('home-probe-calendar'),
    title: document.getElementById('calendar-period-title'),
    grid: document.getElementById('calendar-grid'),
    prev: document.getElementById('calendar-prev'),
    today: document.getElementById('calendar-today'),
    next: document.getElementById('calendar-next'),
    filterOptions: document.getElementById('calendar-filter-options'),
    filterSummary: document.getElementById('calendar-filter-summary'),
    filterDialog: document.getElementById('calendar-filter-dialog'),
    filterForm: document.getElementById('calendar-filter-form'),
    filterSearch: document.getElementById('calendar-filter-search'),
    buildingList: document.getElementById('calendar-building-filter-list'),
    personList: document.getElementById('calendar-person-filter-list'),
    filterAll: document.getElementById('calendar-filter-all'),
    groupSettings: document.getElementById('calendar-group-settings'),
    groupsDialog: document.getElementById('calendar-groups-dialog'),
    groupsForm: document.getElementById('calendar-groups-form'),
    groupsSearch: document.getElementById('calendar-groups-search'),
    groupsLists: document.getElementById('calendar-groups-lists'),
    groupsFeedback: document.getElementById('calendar-groups-feedback')
};

function calendarPad(value) {
    return String(value).padStart(2, '0');
}

function calendarIso(date) {
    return `${date.getFullYear()}-${calendarPad(date.getMonth() + 1)}-${calendarPad(date.getDate())}`;
}

function calendarStartOfWeek(date) {
    const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = result.getDay() || 7;
    result.setDate(result.getDate() - day + 1);
    return result;
}

function calendarAddDays(date, amount) {
    const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    result.setDate(result.getDate() + amount);
    return result;
}

function calendarRange() {
    if (homeCalendar.view === 'day') {
        const day = new Date(homeCalendar.cursor.getFullYear(), homeCalendar.cursor.getMonth(), homeCalendar.cursor.getDate());
        return [day, day];
    }
    if (homeCalendar.view === 'week') {
        const from = calendarStartOfWeek(homeCalendar.cursor);
        return [from, calendarAddDays(from, 6)];
    }
    const monthStart = new Date(homeCalendar.cursor.getFullYear(), homeCalendar.cursor.getMonth(), 1);
    const from = calendarStartOfWeek(monthStart);
    const monthEnd = new Date(homeCalendar.cursor.getFullYear(), homeCalendar.cursor.getMonth() + 1, 0);
    const endDay = monthEnd.getDay() || 7;
    return [from, calendarAddDays(monthEnd, 7 - endDay)];
}

function calendarEsc(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[char]);
}

function calendarSearchText(value) {
    return String(value ?? '').toLowerCase().replaceAll('ё', 'е').trim();
}

async function calendarApi(url, options = {}) {
    const response = await fetch(url, options);
    const type = response.headers.get('content-type') || '';
    const body = type.includes('application/json') ? await response.json() : null;
    if (!response.ok) throw new Error(body?.message || 'Не удалось выполнить операцию с календарём');
    return body;
}

function calendarSaveFilters() {
    localStorage.setItem(CALENDAR_FILTER_STORAGE, JSON.stringify(homeCalendar.filters));
}

function calendarParticipantKeys(event, type) {
    const participants = (event.participants || []).filter(item => item.type === type);
    if (participants.length) {
        return participants.map(item => String(item.id ?? item.code ?? '')).filter(Boolean);
    }
    if (type === 'BUILDING' && event.buildingCode) return [`code:${event.buildingCode}`];
    if (type === 'PERSON' && event.companions?.length) {
        const names = new Set(event.companions.map(calendarSearchText));
        return homeCalendar.audiences.people
            .filter(person => names.has(calendarSearchText(person.fullName)))
            .map(person => String(person.id));
    }
    return [];
}

function calendarMatchesSelection(selectedIds, eventIds) {
    if (!eventIds.length) return false;
    if (selectedIds === null) return true;
    const selected = new Set(selectedIds.map(String));
    return eventIds.some(id => selected.has(String(id)));
}

function calendarGroupPersonIds(code) {
    const group = homeCalendar.audiences.groups.find(item => item.code === code);
    return (group?.personIds || []).map(String);
}

function calendarEventVisible(event) {
    const enabled = homeCalendar.filters.enabled;
    const buildingIds = calendarParticipantKeys(event, 'BUILDING');
    const personIds = calendarParticipantKeys(event, 'PERSON');
    if (enabled.BUILDING && calendarMatchesSelection(homeCalendar.filters.buildingIds, buildingIds)) return true;
    if (enabled.PERSONAL && calendarMatchesSelection(homeCalendar.filters.personIds, personIds)) return true;
    for (const code of CALENDAR_GROUP_CODES) {
        if (enabled[code] && calendarMatchesSelection(calendarGroupPersonIds(code), personIds)) return true;
    }
    return false;
}

function calendarAudienceText(event) {
    const participants = event.participants || [];
    const buildings = participants.filter(item => item.type === 'BUILDING')
        .map(item => item.details ? `${item.label} (${item.details})` : item.label);
    const people = participants.filter(item => item.type === 'PERSON').map(item => item.label);
    const values = [...buildings, ...people].filter(Boolean);
    if (values.length) return values.join('; ');
    return [event.buildingName || event.buildingCode, ...(event.companions || [])].filter(Boolean).join('; ');
}

function calendarEventMarkup(event, detailed = false) {
    const time = [event.startTime, event.endTime].filter(Boolean).map(value => String(value).slice(0, 5)).join('–');
    const classes = (event.classNames || []).join(', ') || 'Класс не указан';
    const companions = (event.companions || []).join(', ') || 'Сопровождающие не назначены';
    const place = event.venue || event.address || '';
    const audience = calendarAudienceText(event);
    return `<article class="home-calendar-event" title="${calendarEsc(`${event.title}; ${classes}; ${companions}; ${audience}`)}">
        <div class="home-calendar-event-time">${calendarEsc(time || 'Время не указано')}</div>
        <strong>${calendarEsc(event.title)}</strong>
        <div>${calendarEsc(classes)}${event.buildingCode ? ` · ${calendarEsc(event.buildingCode)}` : ''}</div>
        ${detailed ? `<div class="muted">Сопровождающие: ${calendarEsc(companions)}</div>
            ${audience ? `<div class="muted">Участники: ${calendarEsc(audience)}</div>` : ''}
            ${place ? `<div class="muted">${calendarEsc(place)}</div>` : ''}` : ''}
    </article>`;
}

function calendarEventsFor(date) {
    const iso = calendarIso(date);
    return homeCalendar.events.filter(event => event.date === iso && calendarEventVisible(event));
}

function calendarTitle(from, to) {
    const month = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' });
    const full = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
    if (homeCalendar.view === 'month') return month.format(homeCalendar.cursor).replace(/^./, char => char.toUpperCase());
    if (homeCalendar.view === 'day') return full.format(from).replace(/^./, char => char.toUpperCase());
    return `${full.format(from)} — ${full.format(to)}`;
}

function renderMonth(from) {
    const weekdays = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
    const cells = [];
    for (let index = 0; index < 42; index += 1) {
        const date = calendarAddDays(from, index);
        const outside = date.getMonth() !== homeCalendar.cursor.getMonth();
        const today = calendarIso(date) === calendarIso(new Date());
        const events = calendarEventsFor(date);
        cells.push(`<div class="home-calendar-day ${outside ? 'is-outside' : ''} ${today ? 'is-today' : ''}">
            <div class="home-calendar-day-number">${date.getDate()}</div>
            <div class="home-calendar-day-events">${events.slice(0, 3).map(item => calendarEventMarkup(item)).join('')}
            ${events.length > 3 ? `<span class="muted">Ещё: ${events.length - 3}</span>` : ''}</div>
        </div>`);
    }
    homeCalendarUi.grid.className = 'home-calendar-grid is-month';
    homeCalendarUi.grid.innerHTML = weekdays.map(day => `<div class="home-calendar-weekday">${day}</div>`).join('') + cells.join('');
}

function renderAgenda(from, days) {
    const dayName = new Intl.DateTimeFormat('ru-RU', { weekday: 'short', day: 'numeric', month: 'short' });
    const columns = [];
    for (let index = 0; index < days; index += 1) {
        const date = calendarAddDays(from, index);
        const events = calendarEventsFor(date);
        columns.push(`<section class="home-calendar-agenda-day ${calendarIso(date) === calendarIso(new Date()) ? 'is-today' : ''}">
            <h4>${calendarEsc(dayName.format(date))}</h4>
            <div>${events.length ? events.map(item => calendarEventMarkup(item, true)).join('') : '<p class="muted">Нет мероприятий</p>'}</div>
        </section>`);
    }
    homeCalendarUi.grid.className = `home-calendar-grid ${days === 1 ? 'is-day' : 'is-week'}`;
    homeCalendarUi.grid.innerHTML = columns.join('');
}

function calendarFilterSummary() {
    const visible = homeCalendar.events.filter(calendarEventVisible).length;
    const buildingText = homeCalendar.filters.buildingIds === null
        ? 'все корпуса' : `корпусов: ${homeCalendar.filters.buildingIds.length}`;
    const peopleText = homeCalendar.filters.personIds === null
        ? 'все сотрудники' : `сотрудников: ${homeCalendar.filters.personIds.length}`;
    homeCalendarUi.filterSummary.textContent = `Показано ${visible} из ${homeCalendar.events.length}; ${buildingText}; ${peopleText}`;
}

function renderHomeCalendar() {
    const [from, to] = calendarRange();
    homeCalendarUi.title.textContent = calendarTitle(from, to);
    document.querySelectorAll('[data-calendar-view]').forEach(button => {
        button.classList.toggle('is-active', button.dataset.calendarView === homeCalendar.view);
    });
    document.querySelectorAll('[data-calendar-audience]').forEach(input => {
        input.checked = homeCalendar.filters.enabled[input.dataset.calendarAudience] !== false;
    });
    calendarFilterSummary();
    if (homeCalendar.view === 'month') renderMonth(from);
    else renderAgenda(from, homeCalendar.view === 'week' ? 7 : 1);
}

async function loadCalendarAudiences(force = false) {
    if (homeCalendar.audiencesLoaded && !force) return;
    homeCalendar.audiences = await calendarApi('/api/calendar/audiences');
    homeCalendar.audiencesLoaded = true;
    homeCalendarUi.groupSettings.hidden = !homeCalendar.audiences.canEdit;
    const buildingIds = new Set(homeCalendar.audiences.buildings.map(item => String(item.id)));
    const personIds = new Set(homeCalendar.audiences.people.map(item => String(item.id)));
    if (homeCalendar.filters.buildingIds !== null) {
        homeCalendar.filters.buildingIds = homeCalendar.filters.buildingIds.filter(id => buildingIds.has(String(id)));
    }
    if (homeCalendar.filters.personIds !== null) {
        homeCalendar.filters.personIds = homeCalendar.filters.personIds.filter(id => personIds.has(String(id)));
    }
    calendarSaveFilters();
}

async function loadHomeCalendar() {
    if (!homeCalendarUi.root) return;
    await loadCalendarAudiences();
    const [from, to] = calendarRange();
    homeCalendarUi.grid.innerHTML = '<p class="muted">Загружаем выпущенные мероприятия…</p>';
    homeCalendar.events = await calendarApi(`/api/probe-orders/calendar?from=${calendarIso(from)}&to=${calendarIso(to)}`);
    renderHomeCalendar();
}

function shiftCalendar(direction) {
    const date = homeCalendar.cursor;
    if (homeCalendar.view === 'month') date.setMonth(date.getMonth() + direction, 1);
    else date.setDate(date.getDate() + direction * (homeCalendar.view === 'week' ? 7 : 1));
    loadHomeCalendar().catch(calendarShowLoadError);
}

function calendarShowLoadError(error) {
    homeCalendarUi.grid.innerHTML = `<p class="probe-error">${calendarEsc(error.message)}</p>`;
}

function calendarOptionLabel(item, type) {
    if (type === 'building') {
        return `<strong>${calendarEsc(item.address || item.name || item.code)}</strong>
            <span class="muted">${calendarEsc([item.code, item.name].filter(Boolean).join(' · '))}</span>`;
    }
    return `<strong>${calendarEsc(item.fullName)}</strong>
        <span class="muted">${calendarEsc([item.position, item.buildingCode].filter(Boolean).join(' · '))}</span>`;
}

function calendarRenderFilterOptions() {
    const selectedBuildings = homeCalendar.filters.buildingIds === null
        ? null : new Set(homeCalendar.filters.buildingIds.map(String));
    const selectedPeople = homeCalendar.filters.personIds === null
        ? null : new Set(homeCalendar.filters.personIds.map(String));
    homeCalendarUi.buildingList.innerHTML = homeCalendar.audiences.buildings.map(item => `
        <label data-calendar-search="${calendarEsc(calendarSearchText(`${item.address} ${item.code} ${item.name}`))}">
            <input type="checkbox" data-calendar-filter-building value="${calendarEsc(item.id)}"
                ${selectedBuildings === null || selectedBuildings.has(String(item.id)) ? 'checked' : ''}>
            <span>${calendarOptionLabel(item, 'building')}</span>
        </label>`).join('') || '<p class="muted">Корпуса не заведены</p>';
    homeCalendarUi.personList.innerHTML = homeCalendar.audiences.people.map(item => `
        <label data-calendar-search="${calendarEsc(calendarSearchText(`${item.fullName} ${item.position} ${item.buildingCode}`))}">
            <input type="checkbox" data-calendar-filter-person value="${calendarEsc(item.id)}"
                ${selectedPeople === null || selectedPeople.has(String(item.id)) ? 'checked' : ''}>
            <span>${calendarOptionLabel(item, 'person')}</span>
        </label>`).join('') || '<p class="muted">Сотрудники не найдены</p>';
}

function calendarFilterVisibleOptions(query, root) {
    const normalized = calendarSearchText(query);
    root.querySelectorAll('[data-calendar-search]').forEach(label => {
        label.hidden = normalized && !calendarSearchText(label.dataset.calendarSearch).includes(normalized);
    });
}

function calendarOpenFilterDialog() {
    calendarRenderFilterOptions();
    homeCalendarUi.filterSearch.value = '';
    homeCalendarUi.filterDialog.showModal();
}

function calendarRenderGroupSettings() {
    homeCalendarUi.groupsLists.innerHTML = homeCalendar.audiences.groups.map(group => {
        const selected = new Set((group.personIds || []).map(String));
        return `<section data-calendar-group-code="${calendarEsc(group.code)}">
            <h4>${calendarEsc(group.label)} <span class="muted">(${selected.size})</span></h4>
            <div class="calendar-check-list">${homeCalendar.audiences.people.map(person => `
                <label data-calendar-search="${calendarEsc(calendarSearchText(`${person.fullName} ${person.position} ${person.buildingCode}`))}">
                    <input type="checkbox" data-calendar-group-person value="${calendarEsc(person.id)}"
                        ${selected.has(String(person.id)) ? 'checked' : ''}>
                    <span>${calendarOptionLabel(person, 'person')}</span>
                </label>`).join('')}</div>
        </section>`;
    }).join('');
}

function calendarOpenGroupsDialog() {
    calendarRenderGroupSettings();
    homeCalendarUi.groupsSearch.value = '';
    homeCalendarUi.groupsFeedback.textContent = '';
    homeCalendarUi.groupsDialog.showModal();
}

if (homeCalendarUi.root) {
    homeCalendarUi.prev?.addEventListener('click', () => shiftCalendar(-1));
    homeCalendarUi.next?.addEventListener('click', () => shiftCalendar(1));
    homeCalendarUi.today?.addEventListener('click', () => {
        homeCalendar.cursor = new Date();
        loadHomeCalendar().catch(calendarShowLoadError);
    });
    document.querySelectorAll('[data-calendar-view]').forEach(button => button.addEventListener('click', () => {
        homeCalendar.view = button.dataset.calendarView;
        localStorage.setItem('probe-calendar-view', homeCalendar.view);
        loadHomeCalendar().catch(calendarShowLoadError);
    }));
    document.querySelectorAll('[data-calendar-audience]').forEach(input => input.addEventListener('change', () => {
        homeCalendar.filters.enabled[input.dataset.calendarAudience] = input.checked;
        calendarSaveFilters();
        renderHomeCalendar();
    }));
    homeCalendarUi.filterOptions?.addEventListener('click', calendarOpenFilterDialog);
    homeCalendarUi.groupSettings?.addEventListener('click', calendarOpenGroupsDialog);
    document.querySelectorAll('[data-calendar-close="filter"]').forEach(button =>
        button.addEventListener('click', () => homeCalendarUi.filterDialog.close()));
    document.querySelectorAll('[data-calendar-close="groups"]').forEach(button =>
        button.addEventListener('click', () => homeCalendarUi.groupsDialog.close()));
    homeCalendarUi.filterSearch?.addEventListener('input', () => {
        calendarFilterVisibleOptions(homeCalendarUi.filterSearch.value, homeCalendarUi.buildingList);
        calendarFilterVisibleOptions(homeCalendarUi.filterSearch.value, homeCalendarUi.personList);
    });
    homeCalendarUi.groupsSearch?.addEventListener('input', () =>
        calendarFilterVisibleOptions(homeCalendarUi.groupsSearch.value, homeCalendarUi.groupsLists));
    homeCalendarUi.filterAll?.addEventListener('click', () => {
        homeCalendarUi.filterForm.querySelectorAll('input[type="checkbox"]').forEach(input => { input.checked = true; });
    });
    homeCalendarUi.filterForm?.addEventListener('submit', event => {
        event.preventDefault();
        const buildings = [...homeCalendarUi.filterForm.querySelectorAll('[data-calendar-filter-building]:checked')]
            .map(input => Number(input.value));
        const people = [...homeCalendarUi.filterForm.querySelectorAll('[data-calendar-filter-person]:checked')]
            .map(input => Number(input.value));
        homeCalendar.filters.buildingIds = buildings.length === homeCalendar.audiences.buildings.length ? null : buildings;
        homeCalendar.filters.personIds = people.length === homeCalendar.audiences.people.length ? null : people;
        calendarSaveFilters();
        homeCalendarUi.filterDialog.close();
        renderHomeCalendar();
    });
    homeCalendarUi.groupsForm?.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = homeCalendarUi.groupsForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        homeCalendarUi.groupsFeedback.textContent = 'Сохраняем составы…';
        try {
            const groups = [...homeCalendarUi.groupsLists.querySelectorAll('[data-calendar-group-code]')].map(section => ({
                code: section.dataset.calendarGroupCode,
                personIds: [...section.querySelectorAll('[data-calendar-group-person]:checked')]
                    .map(input => Number(input.value))
            }));
            homeCalendar.audiences = await calendarApi('/api/calendar/audiences', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ groups })
            });
            homeCalendarUi.groupsFeedback.className = 'probe-feedback probe-ok';
            homeCalendarUi.groupsFeedback.textContent = 'Составы групп сохранены.';
            renderHomeCalendar();
            setTimeout(() => homeCalendarUi.groupsDialog.close(), 450);
        } catch (error) {
            homeCalendarUi.groupsFeedback.className = 'probe-feedback probe-error';
            homeCalendarUi.groupsFeedback.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });
    loadHomeCalendar().catch(calendarShowLoadError);
}
