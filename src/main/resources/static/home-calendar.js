const CALENDAR_FILTER_STORAGE = 'home-calendar-audience-filters';

function calendarStoredFilters() {
    const defaults = {
        enabled: {
            DEPUTIES: true,
            ADMINISTRATION: true,
            FULL_ADMINISTRATION: true,
            BUILDING_HEADS: true,
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
    bootstrap: {
        preferences: { color: '#2563eb', defaultVisibility: 'PARTICIPANTS', sharedWithPersonIds: [] },
        visibilityOptions: [],
        customLists: []
    },
    referencesLoaded: false,
    filters: calendarStoredFilters(),
    selectedEventKey: null
};

const homeCalendarUi = {
    root: document.getElementById('home-probe-calendar'),
    title: document.getElementById('calendar-period-title'),
    grid: document.getElementById('calendar-grid'),
    prev: document.getElementById('calendar-prev'),
    today: document.getElementById('calendar-today'),
    next: document.getElementById('calendar-next'),
    addEvent: document.getElementById('calendar-add-event'),
    ownSettings: document.getElementById('calendar-own-settings'),
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
    groupsFeedback: document.getElementById('calendar-groups-feedback'),
    eventDialog: document.getElementById('calendar-event-dialog'),
    eventForm: document.getElementById('calendar-event-form'),
    eventId: document.getElementById('calendar-event-id'),
    eventDialogTitle: document.getElementById('calendar-event-dialog-title'),
    eventName: document.getElementById('calendar-event-name'),
    eventDate: document.getElementById('calendar-event-date'),
    eventStart: document.getElementById('calendar-event-start'),
    eventDuration: document.getElementById('calendar-event-duration'),
    eventEnd: document.getElementById('calendar-event-end'),
    eventPlace: document.getElementById('calendar-event-place'),
    eventVisibility: document.getElementById('calendar-event-visibility'),
    eventGroups: document.getElementById('calendar-event-groups'),
    eventBuildings: document.getElementById('calendar-event-buildings'),
    eventCustomLists: document.getElementById('calendar-event-custom-lists'),
    eventManageLists: document.getElementById('calendar-event-manage-lists'),
    eventPersonSearch: document.getElementById('calendar-event-person-search'),
    eventPeople: document.getElementById('calendar-event-people'),
    eventFeedback: document.getElementById('calendar-event-feedback'),
    eventDelete: document.getElementById('calendar-event-delete'),
    detailsDialog: document.getElementById('calendar-event-details-dialog'),
    detailsTitle: document.getElementById('calendar-details-title'),
    detailsOwner: document.getElementById('calendar-details-owner'),
    detailsBody: document.getElementById('calendar-details-body'),
    detailsEdit: document.getElementById('calendar-details-edit'),
    settingsDialog: document.getElementById('calendar-own-settings-dialog'),
    settingsForm: document.getElementById('calendar-own-settings-form'),
    ownColor: document.getElementById('calendar-own-color'),
    ownDefaultVisibility: document.getElementById('calendar-own-default-visibility'),
    shareSearch: document.getElementById('calendar-share-search'),
    sharePeople: document.getElementById('calendar-share-people'),
    settingsFeedback: document.getElementById('calendar-settings-feedback'),
    addList: document.getElementById('calendar-add-list'),
    customLists: document.getElementById('calendar-custom-lists'),
    listDialog: document.getElementById('calendar-list-dialog'),
    listForm: document.getElementById('calendar-list-form'),
    listId: document.getElementById('calendar-list-id'),
    listTitle: document.getElementById('calendar-list-title'),
    listName: document.getElementById('calendar-list-name'),
    listSearch: document.getElementById('calendar-list-search'),
    listPeople: document.getElementById('calendar-list-people'),
    listFeedback: document.getElementById('calendar-list-feedback')
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

function calendarSafeColor(value) {
    return /^#[0-9a-f]{6}$/i.test(String(value || '')) ? value : '#2563eb';
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

function calendarEventKey(event) {
    return `${event.source || 'MANUAL'}:${event.id ?? event.orderId}`;
}

function calendarParticipantKeys(event, type) {
    if (type === 'BUILDING') {
        const buildings = event.buildings || [];
        if (buildings.length) return buildings.map(item => String(item.id)).filter(Boolean);
    }
    const participants = (event.participants || []).filter(item => !item.type || item.type === type);
    const ids = participants.map(item => String(item.id ?? item.code ?? '')).filter(Boolean);
    if (type === 'PERSON' && event.ownerTeacherId) ids.push(String(event.ownerTeacherId));
    if (ids.length) return [...new Set(ids)];
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
    for (const group of homeCalendar.audiences.groups) {
        if (enabled[group.code] && calendarMatchesSelection(calendarGroupPersonIds(group.code), personIds)) return true;
    }
    return !buildingIds.length && !personIds.length && enabled.PERSONAL;
}

function calendarAudienceText(event) {
    if (event.audienceSummary) return event.audienceSummary;
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
    const classes = (event.classNames || []).join(', ');
    const companions = (event.companions || []).join(', ');
    const place = event.place || event.venue || event.address || '';
    const audience = calendarAudienceText(event);
    const color = calendarSafeColor(event.color || (event.source === 'PROBE_ORDER' ? '#16a34a' : '#2563eb'));
    const subtitle = event.source === 'PROBE_ORDER'
        ? [classes || 'Класс не указан', event.buildingCode].filter(Boolean).join(' · ')
        : [event.ownerName, event.visibilityLabel].filter(Boolean).join(' · ');
    return `<button type="button" class="home-calendar-event" data-calendar-event-key="${calendarEsc(calendarEventKey(event))}"
        style="--calendar-event-color:${calendarEsc(color)}" title="${calendarEsc(`${event.title}; ${audience}`)}">
        <span class="home-calendar-event-time">${calendarEsc(time || 'Время не указано')}</span>
        <strong>${calendarEsc(event.title)}</strong>
        ${subtitle ? `<span>${calendarEsc(subtitle)}</span>` : ''}
        ${detailed && companions ? `<span class="muted">Сопровождающие: ${calendarEsc(companions)}</span>` : ''}
        ${detailed && audience ? `<span class="muted">Участники: ${calendarEsc(audience)}</span>` : ''}
        ${detailed && place ? `<span class="muted">${calendarEsc(place)}</span>` : ''}
    </button>`;
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
            <button type="button" class="home-calendar-day-number" data-calendar-create-date="${calendarIso(date)}"
                title="Добавить встречу на эту дату">${date.getDate()}</button>
            <div class="home-calendar-day-events">${events.slice(0, 3).map(item => calendarEventMarkup(item)).join('')}
            ${events.length > 3 ? `<span class="muted">Ещё: ${events.length - 3}</span>` : ''}</div>
        </div>`);
    }
    homeCalendarUi.grid.className = 'home-calendar-grid is-month';
    homeCalendarUi.grid.innerHTML = weekdays.map(day => `<div class="home-calendar-weekday">${day}</div>`).join('') + cells.join('');
    calendarBindRenderedActions();
}

function renderAgenda(from, days) {
    const dayName = new Intl.DateTimeFormat('ru-RU', { weekday: 'short', day: 'numeric', month: 'short' });
    const columns = [];
    for (let index = 0; index < days; index += 1) {
        const date = calendarAddDays(from, index);
        const events = calendarEventsFor(date);
        columns.push(`<section class="home-calendar-agenda-day ${calendarIso(date) === calendarIso(new Date()) ? 'is-today' : ''}">
            <div class="calendar-agenda-heading"><h4>${calendarEsc(dayName.format(date))}</h4>
                <button type="button" class="secondary" data-calendar-create-date="${calendarIso(date)}">+ Встреча</button></div>
            <div>${events.length ? events.map(item => calendarEventMarkup(item, true)).join('') : '<p class="muted">Нет мероприятий</p>'}</div>
        </section>`);
    }
    homeCalendarUi.grid.className = `home-calendar-grid ${days === 1 ? 'is-day' : 'is-week'}`;
    homeCalendarUi.grid.innerHTML = columns.join('');
    calendarBindRenderedActions();
}

function calendarBindRenderedActions() {
    homeCalendarUi.grid.querySelectorAll('[data-calendar-event-key]').forEach(button => button.addEventListener('click', () => {
        const event = homeCalendar.events.find(item => calendarEventKey(item) === button.dataset.calendarEventKey);
        if (event) calendarOpenDetails(event);
    }));
    homeCalendarUi.grid.querySelectorAll('[data-calendar-create-date]').forEach(button => button.addEventListener('click', () => {
        calendarOpenEventForm(null, button.dataset.calendarCreateDate);
    }));
}

function calendarFilterSummary() {
    const visible = homeCalendar.events.filter(calendarEventVisible).length;
    const buildingText = homeCalendar.filters.buildingIds === null
        ? 'все корпуса' : `корпусов: ${homeCalendar.filters.buildingIds.length}`;
    const peopleText = homeCalendar.filters.personIds === null
        ? 'все сотрудники' : `календарей: ${homeCalendar.filters.personIds.length}`;
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

function calendarNormalizeProbeEvent(event) {
    const participants = event.participants || [];
    return {
        ...event,
        id: event.orderId,
        source: 'PROBE_ORDER',
        place: event.venue || event.address || '',
        color: '#16a34a',
        ownerName: 'Выпущенный приказ',
        visibilityLabel: 'Всем сотрудникам',
        buildings: participants.filter(item => item.type === 'BUILDING').map(item => ({
            id: item.id, code: item.code, name: item.label, address: item.details
        })),
        participants
    };
}

async function loadCalendarReferences(force = false) {
    if (homeCalendar.referencesLoaded && !force) return;
    const [audiences, bootstrap] = await Promise.all([
        calendarApi('/api/calendar/audiences'),
        calendarApi('/api/calendar/bootstrap')
    ]);
    homeCalendar.audiences = audiences;
    homeCalendar.bootstrap = bootstrap;
    homeCalendar.referencesLoaded = true;
    homeCalendarUi.groupSettings.hidden = !audiences.canEdit;
    const buildingIds = new Set(audiences.buildings.map(item => String(item.id)));
    const personIds = new Set(audiences.people.map(item => String(item.id)));
    if (homeCalendar.filters.buildingIds !== null) {
        homeCalendar.filters.buildingIds = homeCalendar.filters.buildingIds.filter(id => buildingIds.has(String(id)));
    }
    if (homeCalendar.filters.personIds !== null) {
        homeCalendar.filters.personIds = homeCalendar.filters.personIds.filter(id => personIds.has(String(id)));
    }
    audiences.groups.forEach(group => {
        if (homeCalendar.filters.enabled[group.code] === undefined) homeCalendar.filters.enabled[group.code] = true;
    });
    calendarSaveFilters();
    calendarRenderVisibilityOptions();
}

async function loadHomeCalendar() {
    if (!homeCalendarUi.root) return;
    await loadCalendarReferences();
    const [from, to] = calendarRange();
    homeCalendarUi.grid.innerHTML = '<p class="muted">Загружаем календарь…</p>';
    const [manual, probe] = await Promise.all([
        calendarApi(`/api/calendar/events?from=${calendarIso(from)}&to=${calendarIso(to)}`),
        calendarApi(`/api/probe-orders/calendar?from=${calendarIso(from)}&to=${calendarIso(to)}`)
    ]);
    homeCalendar.events = [...manual, ...probe.map(calendarNormalizeProbeEvent)].sort((left, right) =>
        `${left.date}T${left.startTime || ''}`.localeCompare(`${right.date}T${right.startTime || ''}`, 'ru'));
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

function calendarPeopleMarkup(selectedIds, inputAttribute) {
    const selected = new Set((selectedIds || []).map(String));
    return homeCalendar.audiences.people.map(person => `
        <label data-calendar-search="${calendarEsc(calendarSearchText(`${person.fullName} ${person.position} ${person.buildingCode}`))}">
            <input type="checkbox" ${inputAttribute} value="${calendarEsc(person.id)}"
                ${selected.has(String(person.id)) ? 'checked' : ''}>
            <span>${calendarOptionLabel(person, 'person')}</span>
        </label>`).join('') || '<p class="muted">Сотрудники не найдены</p>';
}

function calendarFilterVisibleOptions(query, root) {
    const normalized = calendarSearchText(query);
    root?.querySelectorAll('[data-calendar-search]').forEach(label => {
        label.hidden = Boolean(normalized && !calendarSearchText(label.dataset.calendarSearch).includes(normalized));
    });
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
            <div class="calendar-check-list">${calendarPeopleMarkup([...selected], 'data-calendar-group-person')}</div>
        </section>`;
    }).join('');
}

function calendarOpenGroupsDialog() {
    calendarRenderGroupSettings();
    homeCalendarUi.groupsSearch.value = '';
    homeCalendarUi.groupsFeedback.textContent = '';
    homeCalendarUi.groupsDialog.showModal();
}

function calendarRenderVisibilityOptions() {
    const options = (homeCalendar.bootstrap.visibilityOptions || []).map(item =>
        `<option value="${calendarEsc(item.code)}">${calendarEsc(item.label)}</option>`).join('');
    homeCalendarUi.eventVisibility.innerHTML = options;
    homeCalendarUi.ownDefaultVisibility.innerHTML = options;
}

function calendarDefaultStart() {
    const now = new Date();
    now.setMinutes(Math.ceil(now.getMinutes() / 30) * 30, 0, 0);
    return `${calendarPad(now.getHours())}:${calendarPad(now.getMinutes())}`;
}

function calendarUpdateEndTime() {
    const rawStart = homeCalendarUi.eventStart.value;
    const duration = Number(homeCalendarUi.eventDuration.value);
    if (!rawStart || !duration) {
        homeCalendarUi.eventEnd.textContent = '—';
        return;
    }
    const [hours, minutes] = rawStart.split(':').map(Number);
    const total = hours * 60 + minutes + duration;
    const days = Math.floor(total / 1440);
    homeCalendarUi.eventEnd.textContent = `${calendarPad(Math.floor((total % 1440) / 60))}:${calendarPad(total % 60)}`
        + (days ? ` (+${days} дн.)` : '');
}

function calendarRenderEventParticipants(event) {
    const selectedGroups = new Set((event?.selectedGroupCodes || []).map(String));
    const selectedBuildings = new Set((event?.selectedBuildingIds || []).map(String));
    const selectedLists = new Set((event?.selectedCustomListIds || []).map(String));
    homeCalendarUi.eventGroups.innerHTML = homeCalendar.audiences.groups.map(group => `
        <label><input type="checkbox" data-calendar-event-group value="${calendarEsc(group.code)}"
            ${selectedGroups.has(group.code) ? 'checked' : ''}>
            <span>${calendarEsc(group.label)} <small>${group.personIds?.length || 0}</small></span></label>`).join('');
    homeCalendarUi.eventBuildings.innerHTML = homeCalendar.audiences.buildings.map(item => `
        <label data-calendar-search="${calendarEsc(calendarSearchText(`${item.address} ${item.code} ${item.name}`))}">
            <input type="checkbox" data-calendar-event-building value="${calendarEsc(item.id)}"
                ${selectedBuildings.has(String(item.id)) ? 'checked' : ''}>
            <span>${calendarOptionLabel(item, 'building')}</span>
        </label>`).join('') || '<p class="muted">Корпуса не заведены</p>';
    homeCalendarUi.eventCustomLists.innerHTML = (homeCalendar.bootstrap.customLists || []).map(item => `
        <label><input type="checkbox" data-calendar-event-list value="${calendarEsc(item.id)}"
            ${selectedLists.has(String(item.id)) ? 'checked' : ''}>
            <span><strong>${calendarEsc(item.name)}</strong><span class="muted">Участников: ${item.personIds?.length || 0}</span></span>
        </label>`).join('') || '<p class="muted">Личных списков пока нет.</p>';
    homeCalendarUi.eventPeople.innerHTML = calendarPeopleMarkup(event?.selectedPersonIds || [], 'data-calendar-event-person');
}

function calendarOpenEventForm(event = null, presetDate = null) {
    const editable = event?.source === 'MANUAL' ? event : null;
    homeCalendarUi.eventId.value = editable?.id || '';
    homeCalendarUi.eventDialogTitle.textContent = editable ? 'Редактирование встречи' : 'Новая встреча';
    homeCalendarUi.eventName.value = editable?.title || '';
    homeCalendarUi.eventDate.value = editable?.date || presetDate || calendarIso(homeCalendar.cursor || new Date());
    homeCalendarUi.eventStart.value = editable?.startTime?.slice(0, 5) || calendarDefaultStart();
    homeCalendarUi.eventDuration.value = editable?.durationMinutes || 60;
    homeCalendarUi.eventPlace.value = editable?.place || '';
    homeCalendarUi.eventVisibility.value = editable?.visibility
        || homeCalendar.bootstrap.preferences?.defaultVisibility || 'PARTICIPANTS';
    homeCalendarUi.eventFeedback.textContent = '';
    homeCalendarUi.eventFeedback.className = 'probe-feedback';
    homeCalendarUi.eventDelete.hidden = !editable;
    homeCalendarUi.eventPersonSearch.value = '';
    calendarRenderEventParticipants(editable);
    calendarUpdateEndTime();
    homeCalendarUi.eventDialog.showModal();
    homeCalendarUi.eventName.focus();
}

function calendarCheckedNumbers(root, selector) {
    return [...root.querySelectorAll(`${selector}:checked`)].map(input => Number(input.value));
}

function calendarCheckedStrings(root, selector) {
    return [...root.querySelectorAll(`${selector}:checked`)].map(input => input.value);
}

function calendarEventPayload() {
    return {
        title: homeCalendarUi.eventName.value.trim(),
        date: homeCalendarUi.eventDate.value,
        startTime: homeCalendarUi.eventStart.value,
        durationMinutes: Number(homeCalendarUi.eventDuration.value),
        place: homeCalendarUi.eventPlace.value.trim(),
        visibility: homeCalendarUi.eventVisibility.value,
        personIds: calendarCheckedNumbers(homeCalendarUi.eventForm, '[data-calendar-event-person]'),
        groupCodes: calendarCheckedStrings(homeCalendarUi.eventForm, '[data-calendar-event-group]'),
        buildingIds: calendarCheckedNumbers(homeCalendarUi.eventForm, '[data-calendar-event-building]'),
        customListIds: calendarCheckedNumbers(homeCalendarUi.eventForm, '[data-calendar-event-list]')
    };
}

function calendarOpenDetails(event) {
    homeCalendar.selectedEventKey = calendarEventKey(event);
    const time = [event.startTime, event.endTime].filter(Boolean).map(value => String(value).slice(0, 5)).join('–');
    const place = event.place || event.venue || event.address || 'Не указано';
    const audience = calendarAudienceText(event) || 'Только личный календарь';
    const classes = (event.classNames || []).join(', ');
    const companions = (event.companions || []).join(', ');
    homeCalendarUi.detailsTitle.textContent = event.title;
    homeCalendarUi.detailsOwner.textContent = event.source === 'PROBE_ORDER'
        ? 'Мероприятие из выпущенного приказа' : `Календарь: ${event.ownerName || 'пользователь'}`;
    homeCalendarUi.detailsBody.innerHTML = `
        <dl class="calendar-details-list">
          <div><dt>Дата и время</dt><dd>${calendarEsc(event.date)} · ${calendarEsc(time || 'не указано')}</dd></div>
          ${event.durationMinutes ? `<div><dt>Продолжительность</dt><dd>${calendarEsc(event.durationMinutes)} мин.</dd></div>` : ''}
          <div><dt>Место</dt><dd>${calendarEsc(place)}</dd></div>
          <div><dt>Видимость</dt><dd>${calendarEsc(event.visibilityLabel || 'Всем сотрудникам')}</dd></div>
          <div><dt>Участники</dt><dd>${calendarEsc(audience)}</dd></div>
          ${classes ? `<div><dt>Классы</dt><dd>${calendarEsc(classes)}</dd></div>` : ''}
          ${companions ? `<div><dt>Сопровождающие</dt><dd>${calendarEsc(companions)}</dd></div>` : ''}
        </dl>`;
    homeCalendarUi.detailsEdit.hidden = event.source !== 'MANUAL' || !event.canEdit;
    homeCalendarUi.detailsDialog.showModal();
}

function calendarRenderSettings() {
    const preferences = homeCalendar.bootstrap.preferences || {};
    homeCalendarUi.ownColor.value = calendarSafeColor(preferences.color);
    homeCalendarUi.ownDefaultVisibility.value = preferences.defaultVisibility || 'PARTICIPANTS';
    homeCalendarUi.shareSearch.value = '';
    homeCalendarUi.sharePeople.innerHTML = calendarPeopleMarkup(preferences.sharedWithPersonIds || [], 'data-calendar-share-person');
    const lists = homeCalendar.bootstrap.customLists || [];
    homeCalendarUi.customLists.innerHTML = lists.map(item => `
        <article class="calendar-custom-list-card" data-calendar-list-card="${calendarEsc(item.id)}">
          <div><strong>${calendarEsc(item.name)}</strong><span class="muted">Участников: ${item.personIds?.length || 0}</span></div>
          <div><button type="button" class="secondary" data-calendar-edit-list="${calendarEsc(item.id)}">Изменить</button>
            <button type="button" class="danger-btn" data-calendar-delete-list="${calendarEsc(item.id)}">Удалить</button></div>
        </article>`).join('') || '<p class="muted">Личных списков пока нет.</p>';
    homeCalendarUi.customLists.querySelectorAll('[data-calendar-edit-list]').forEach(button => button.addEventListener('click', () => {
        const list = lists.find(item => String(item.id) === button.dataset.calendarEditList);
        if (list) calendarOpenList(list);
    }));
    homeCalendarUi.customLists.querySelectorAll('[data-calendar-delete-list]').forEach(button => button.addEventListener('click', async () => {
        const list = lists.find(item => String(item.id) === button.dataset.calendarDeleteList);
        if (!list || !window.confirm(`Удалить личный список «${list.name}»? Уже созданные встречи не изменятся.`)) return;
        try {
            await calendarApi(`/api/calendar/lists/${list.id}`, { method: 'DELETE' });
            await calendarReloadBootstrap();
            calendarRenderSettings();
        } catch (error) {
            homeCalendarUi.settingsFeedback.textContent = error.message;
            homeCalendarUi.settingsFeedback.className = 'probe-feedback probe-error';
        }
    }));
}

function calendarOpenOwnSettings() {
    calendarRenderSettings();
    homeCalendarUi.settingsFeedback.textContent = '';
    homeCalendarUi.settingsFeedback.className = 'probe-feedback';
    homeCalendarUi.settingsDialog.showModal();
}

function calendarOpenList(list = null) {
    homeCalendarUi.listId.value = list?.id || '';
    homeCalendarUi.listTitle.textContent = list ? 'Редактирование списка' : 'Новый список';
    homeCalendarUi.listName.value = list?.name || '';
    homeCalendarUi.listSearch.value = '';
    homeCalendarUi.listPeople.innerHTML = calendarPeopleMarkup(list?.personIds || [], 'data-calendar-list-person');
    homeCalendarUi.listFeedback.textContent = '';
    homeCalendarUi.listDialog.showModal();
    homeCalendarUi.listName.focus();
}

async function calendarReloadBootstrap() {
    homeCalendar.bootstrap = await calendarApi('/api/calendar/bootstrap');
    calendarRenderVisibilityOptions();
}

if (homeCalendarUi.root) {
    homeCalendarUi.prev?.addEventListener('click', () => shiftCalendar(-1));
    homeCalendarUi.next?.addEventListener('click', () => shiftCalendar(1));
    homeCalendarUi.today?.addEventListener('click', () => {
        homeCalendar.cursor = new Date();
        loadHomeCalendar().catch(calendarShowLoadError);
    });
    homeCalendarUi.addEvent?.addEventListener('click', () => calendarOpenEventForm());
    homeCalendarUi.ownSettings?.addEventListener('click', calendarOpenOwnSettings);
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
    document.querySelectorAll('[data-calendar-close]').forEach(button => button.addEventListener('click', () => {
        const dialog = {
            filter: homeCalendarUi.filterDialog,
            groups: homeCalendarUi.groupsDialog,
            event: homeCalendarUi.eventDialog,
            details: homeCalendarUi.detailsDialog,
            settings: homeCalendarUi.settingsDialog,
            list: homeCalendarUi.listDialog
        }[button.dataset.calendarClose];
        dialog?.close();
    }));
    homeCalendarUi.filterSearch?.addEventListener('input', () => {
        calendarFilterVisibleOptions(homeCalendarUi.filterSearch.value, homeCalendarUi.buildingList);
        calendarFilterVisibleOptions(homeCalendarUi.filterSearch.value, homeCalendarUi.personList);
    });
    homeCalendarUi.groupsSearch?.addEventListener('input', () =>
        calendarFilterVisibleOptions(homeCalendarUi.groupsSearch.value, homeCalendarUi.groupsLists));
    homeCalendarUi.eventPersonSearch?.addEventListener('input', () =>
        calendarFilterVisibleOptions(homeCalendarUi.eventPersonSearch.value, homeCalendarUi.eventPeople));
    homeCalendarUi.shareSearch?.addEventListener('input', () =>
        calendarFilterVisibleOptions(homeCalendarUi.shareSearch.value, homeCalendarUi.sharePeople));
    homeCalendarUi.listSearch?.addEventListener('input', () =>
        calendarFilterVisibleOptions(homeCalendarUi.listSearch.value, homeCalendarUi.listPeople));
    homeCalendarUi.filterAll?.addEventListener('click', () => {
        homeCalendarUi.filterForm.querySelectorAll('input[type="checkbox"]').forEach(input => { input.checked = true; });
    });
    homeCalendarUi.filterForm?.addEventListener('submit', event => {
        event.preventDefault();
        const buildings = calendarCheckedNumbers(homeCalendarUi.filterForm, '[data-calendar-filter-building]');
        const people = calendarCheckedNumbers(homeCalendarUi.filterForm, '[data-calendar-filter-person]');
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
                personIds: calendarCheckedNumbers(section, '[data-calendar-group-person]')
            }));
            homeCalendar.audiences = await calendarApi('/api/calendar/audiences', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ groups })
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
    homeCalendarUi.eventStart?.addEventListener('input', calendarUpdateEndTime);
    homeCalendarUi.eventDuration?.addEventListener('input', calendarUpdateEndTime);
    homeCalendarUi.eventForm?.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = homeCalendarUi.eventForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        homeCalendarUi.eventFeedback.textContent = 'Сохраняем встречу…';
        try {
            const id = homeCalendarUi.eventId.value;
            await calendarApi(id ? `/api/calendar/events/${id}` : '/api/calendar/events', {
                method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(calendarEventPayload())
            });
            homeCalendarUi.eventDialog.close();
            await loadHomeCalendar();
        } catch (error) {
            homeCalendarUi.eventFeedback.className = 'probe-feedback probe-error';
            homeCalendarUi.eventFeedback.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });
    homeCalendarUi.eventDelete?.addEventListener('click', async () => {
        const id = homeCalendarUi.eventId.value;
        if (!id || !window.confirm('Удалить эту встречу из календаря?')) return;
        try {
            await calendarApi(`/api/calendar/events/${id}`, { method: 'DELETE' });
            homeCalendarUi.eventDialog.close();
            await loadHomeCalendar();
        } catch (error) {
            homeCalendarUi.eventFeedback.textContent = error.message;
            homeCalendarUi.eventFeedback.className = 'probe-feedback probe-error';
        }
    });
    homeCalendarUi.detailsEdit?.addEventListener('click', () => {
        const event = homeCalendar.events.find(item => calendarEventKey(item) === homeCalendar.selectedEventKey);
        homeCalendarUi.detailsDialog.close();
        if (event) calendarOpenEventForm(event);
    });
    homeCalendarUi.eventManageLists?.addEventListener('click', () => {
        homeCalendarUi.eventDialog.close();
        calendarOpenOwnSettings();
    });
    homeCalendarUi.settingsForm?.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = homeCalendarUi.settingsForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        homeCalendarUi.settingsFeedback.textContent = 'Сохраняем настройки…';
        try {
            const preferences = await calendarApi('/api/calendar/settings', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
                    color: homeCalendarUi.ownColor.value,
                    defaultVisibility: homeCalendarUi.ownDefaultVisibility.value,
                    sharedWithPersonIds: calendarCheckedNumbers(homeCalendarUi.settingsForm, '[data-calendar-share-person]')
                })
            });
            homeCalendar.bootstrap.preferences = preferences;
            homeCalendarUi.settingsFeedback.className = 'probe-feedback probe-ok';
            homeCalendarUi.settingsFeedback.textContent = 'Настройки календаря сохранены.';
            await loadHomeCalendar();
        } catch (error) {
            homeCalendarUi.settingsFeedback.className = 'probe-feedback probe-error';
            homeCalendarUi.settingsFeedback.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });
    homeCalendarUi.addList?.addEventListener('click', () => calendarOpenList());
    homeCalendarUi.listForm?.addEventListener('submit', async event => {
        event.preventDefault();
        const submit = homeCalendarUi.listForm.querySelector('button[type="submit"]');
        submit.disabled = true;
        homeCalendarUi.listFeedback.textContent = 'Сохраняем список…';
        try {
            const id = homeCalendarUi.listId.value;
            await calendarApi(id ? `/api/calendar/lists/${id}` : '/api/calendar/lists', {
                method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
                    name: homeCalendarUi.listName.value.trim(),
                    personIds: calendarCheckedNumbers(homeCalendarUi.listForm, '[data-calendar-list-person]')
                })
            });
            await calendarReloadBootstrap();
            homeCalendarUi.listDialog.close();
            calendarRenderSettings();
        } catch (error) {
            homeCalendarUi.listFeedback.className = 'probe-feedback probe-error';
            homeCalendarUi.listFeedback.textContent = error.message;
        } finally {
            submit.disabled = false;
        }
    });
    loadHomeCalendar().catch(calendarShowLoadError);
}
