const homeCalendar = {
    view: localStorage.getItem('probe-calendar-view') || 'month',
    cursor: new Date(),
    events: []
};

const homeCalendarUi = {
    root: document.getElementById('home-probe-calendar'),
    title: document.getElementById('calendar-period-title'),
    grid: document.getElementById('calendar-grid'),
    prev: document.getElementById('calendar-prev'),
    today: document.getElementById('calendar-today'),
    next: document.getElementById('calendar-next')
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

function calendarEventMarkup(event, detailed = false) {
    const time = [event.startTime, event.endTime].filter(Boolean).map(value => String(value).slice(0, 5)).join('–');
    const classes = (event.classNames || []).join(', ') || 'Класс не указан';
    const companions = (event.companions || []).join(', ') || 'Сопровождающие не назначены';
    const place = event.venue || event.address || '';
    return `<article class="home-calendar-event" title="${calendarEsc(`${event.title}; ${classes}; ${companions}`)}">
        <div class="home-calendar-event-time">${calendarEsc(time || 'Время не указано')}</div>
        <strong>${calendarEsc(event.title)}</strong>
        <div>${calendarEsc(classes)}${event.buildingCode ? ` · ${calendarEsc(event.buildingCode)}` : ''}</div>
        ${detailed ? `<div class="muted">${calendarEsc(companions)}</div>${place ? `<div class="muted">${calendarEsc(place)}</div>` : ''}` : ''}
    </article>`;
}

function calendarEventsFor(date) {
    const iso = calendarIso(date);
    return homeCalendar.events.filter(event => event.date === iso);
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

function renderHomeCalendar() {
    const [from, to] = calendarRange();
    homeCalendarUi.title.textContent = calendarTitle(from, to);
    document.querySelectorAll('[data-calendar-view]').forEach(button => {
        button.classList.toggle('is-active', button.dataset.calendarView === homeCalendar.view);
    });
    if (homeCalendar.view === 'month') renderMonth(from);
    else renderAgenda(from, homeCalendar.view === 'week' ? 7 : 1);
}

async function loadHomeCalendar() {
    if (!homeCalendarUi.root) return;
    const [from, to] = calendarRange();
    homeCalendarUi.grid.innerHTML = '<p class="muted">Загружаем выпущенные мероприятия…</p>';
    const response = await fetch(`/api/probe-orders/calendar?from=${calendarIso(from)}&to=${calendarIso(to)}`);
    if (!response.ok) throw new Error('Не удалось загрузить календарь мероприятий');
    homeCalendar.events = await response.json();
    renderHomeCalendar();
}

function shiftCalendar(direction) {
    const date = homeCalendar.cursor;
    if (homeCalendar.view === 'month') date.setMonth(date.getMonth() + direction, 1);
    else date.setDate(date.getDate() + direction * (homeCalendar.view === 'week' ? 7 : 1));
    loadHomeCalendar().catch(error => { homeCalendarUi.grid.innerHTML = `<p class="probe-error">${calendarEsc(error.message)}</p>`; });
}

if (homeCalendarUi.root) {
    homeCalendarUi.prev?.addEventListener('click', () => shiftCalendar(-1));
    homeCalendarUi.next?.addEventListener('click', () => shiftCalendar(1));
    homeCalendarUi.today?.addEventListener('click', () => {
        homeCalendar.cursor = new Date();
        loadHomeCalendar().catch(error => { homeCalendarUi.grid.innerHTML = `<p class="probe-error">${calendarEsc(error.message)}</p>`; });
    });
    document.querySelectorAll('[data-calendar-view]').forEach(button => button.addEventListener('click', () => {
        homeCalendar.view = button.dataset.calendarView;
        localStorage.setItem('probe-calendar-view', homeCalendar.view);
        loadHomeCalendar().catch(error => { homeCalendarUi.grid.innerHTML = `<p class="probe-error">${calendarEsc(error.message)}</p>`; });
    }));
    loadHomeCalendar().catch(error => { homeCalendarUi.grid.innerHTML = `<p class="probe-error">${calendarEsc(error.message)}</p>`; });
}
