const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/mes-contingent-export.js'), 'utf8');
const endpoint = '/api/ej/core/teacher/v1/student_profiles';
const student = (id) => ({
    id, user_name: `Тестовый Ученик ${id}`, birth_date: '2015-01-02',
    class_unit: { name: '5-А' }, phone_number: '+7 900 000-00-00',
    education_level: 2, home_based_profile: false,
    parents: [
        { name: 'Тестовый Представитель', phone_number_ezd: '+7 900 111-11-11' },
        { name: 'Скрытый Представитель', hidden: true }
    ]
});

function browser(options = {}) {
    const calls = [], downloads = [], blobs = [], logs = [], timers = new Map();
    let timerId = 0;
    class BrowserUrl extends URL {
        static createObjectURL(blob) { blobs.push(blob); return 'blob:test'; }
        static revokeObjectURL() {}
    }
    class XHR {
        open() {}
        setRequestHeader() {}
        send() {}
    }
    const originalFetch = async (input, init = {}) => {
        const url = new URL(typeof input === 'string' ? input : input.url, 'https://mes.example');
        calls.push({ url, headers: new Headers(init.headers || input.headers), init });
        const page = Number(url.searchParams.get('page'));
        const perPage = Number(url.searchParams.get('per_page'));
        if (options.status || (options.fallback && perPage === 100)) {
            return new Response('{}', { status: options.status || 400 });
        }
        const data = options.empty ? [] : [student(page === 1 ? 1 : 2)];
        return new Response(JSON.stringify(data), {
            headers: { pages: options.empty ? '1' : '2', pagesize: String(perPage), 'total-entities': options.empty ? '0' : '2' }
        });
    };
    const context = vm.createContext({
        URL: BrowserUrl, Request, Headers, Blob, XMLHttpRequest: XHR,
        location: { origin: 'https://mes.example' }, fetch: originalFetch,
        document: {
            body: { appendChild() {} },
            createElement() { return { click() { downloads.push(this.download); }, remove() {} }; }
        },
        console: Object.fromEntries(['clear', 'log', 'warn', 'error', 'table'].map(key => [key, (...values) => logs.push(values)])),
        setTimeout(fn, ms) {
            const id = ++timerId;
            if (ms >= 60000) timers.set(id, fn);
            else queueMicrotask(fn);
            return id;
        },
        clearTimeout(id) { timers.delete(id); }
    });
    context.window = context;
    const originals = { fetch: originalFetch, open: XHR.prototype.open, send: XHR.prototype.send, header: XHR.prototype.setRequestHeader };
    const completed = vm.runInContext(source, context);
    const requestUrl = (school = '1811') => `https://mes.example${endpoint}?school_id=${school}&academic_year_id=15&page=2&per_page=10&with_user_info=true`;
    const restored = () => {
        assert.equal(context.fetch, originals.fetch);
        assert.equal(XHR.prototype.open, originals.open);
        assert.equal(XHR.prototype.send, originals.send);
        assert.equal(XHR.prototype.setRequestHeader, originals.header);
        assert.equal(timers.size, 0);
    };
    return { context, calls, downloads, blobs, logs, timers, completed, requestUrl, restored };
}

test('fetch uses the currently selected school and exports child/parent contacts', async () => {
    for (const school of ['1811', '9876']) {
        const b = browser();
        await b.context.fetch(new Request(b.requestUrl(school), { headers: { authorization: 'Bearer test-only', 'profile-id': '42' } }));
        await b.completed;
        b.restored();
        const exportCalls = b.calls.filter(call => call.init.credentials === 'include');
        assert.equal(exportCalls.length, 2);
        for (const call of exportCalls) {
            assert.equal(call.url.searchParams.get('school_id'), school);
            assert.equal(call.url.searchParams.get('academic_year_id'), '15');
            assert.equal(call.headers.get('profile-id'), '42');
            assert.equal(call.headers.get('authorization'), 'Bearer test-only');
        }
        assert.match(b.downloads[0], new RegExp(`^MES_контингент_${school}_`));
        const csv = await b.blobs[0].text();
        assert.match(csv, /"Телефон ребёнка"/);
        assert.match(csv, /"Уровень образования"/);
        assert.match(csv, /\+7 900 000-00-00/);
        assert.match(csv, /\+7 900 111-11-11/);
        assert.doesNotMatch(csv, /Скрытый Представитель/);
        assert.doesNotMatch(JSON.stringify(b.logs), /Bearer test-only/);
    }
});

test('XHR capture restores prototypes and reuses current headers', async () => {
    const b = browser();
    const xhr = new b.context.XMLHttpRequest();
    xhr.open('GET', b.requestUrl('5555'));
    xhr.setRequestHeader('profile-id', '73');
    xhr.setRequestHeader('Cookie', 'must-not-copy');
    xhr.send();
    await b.completed;
    b.restored();
    assert.equal(b.calls[0].headers.get('profile-id'), '73');
    assert.equal(b.calls[0].headers.has('cookie'), false);
    assert.match(b.downloads[0], /^MES_контингент_5555_/);
});

test('init headers override Request headers and current filters are preserved', async () => {
    const b = browser();
    await b.context.fetch(new Request(b.requestUrl() + '&sex=female', { headers: { 'profile-id': 'old' } }), {
        headers: { 'profile-id': 'current' }
    });
    await b.completed;
    b.restored();
    assert.equal(b.calls[1].headers.get('profile-id'), 'current');
    assert.equal(b.calls[1].url.searchParams.get('sex'), 'female');
});

test('ignores unrelated, detail, cross-origin and non-GET requests', async () => {
    const b = browser();
    await b.context.fetch(b.requestUrl().replace(endpoint, endpoint + '/123'));
    await b.context.fetch(b.requestUrl().replace('https://mes.example', 'https://other.example'));
    await b.context.fetch(b.requestUrl(), { method: 'POST' });
    await b.context.fetch(`${endpoint}?page=2`);
    assert.equal(b.downloads.length, 0);
    assert.equal(b.timers.size, 1);
    await b.context.fetch(b.requestUrl('7777'));
    await b.completed;
    b.restored();
    assert.match(b.downloads[0], /^MES_контингент_7777_/);
});

test('timeout restores hooks without downloading a file', async () => {
    const b = browser();
    const rejection = assert.rejects(b.completed, /За 60 секунд/);
    [...b.timers.values()][0]();
    await rejection;
    b.restored();
    assert.equal(b.downloads.length, 0);
});

test('access failure restores hooks and does not save a partial CSV', async () => {
    const b = browser({ status: 403 });
    const rejection = assert.rejects(b.completed, /Ошибка доступа HTTP 403/);
    await b.context.fetch(b.requestUrl());
    await rejection;
    b.restored();
    assert.equal(b.downloads.length, 0);
});

test('empty selection has a clear error instead of Object.keys(undefined)', async () => {
    const b = browser({ empty: true });
    const rejection = assert.rejects(b.completed, /Нет данных для выгрузки/);
    await b.context.fetch(b.requestUrl());
    await rejection;
    b.restored();
    assert.equal(b.downloads.length, 0);
});

test('falls back to ten records when requesting one hundred is rejected', async () => {
    const b = browser({ fallback: true });
    await b.context.fetch(b.requestUrl());
    await b.completed;
    b.restored();
    assert.deepEqual(b.calls.filter(c => c.init.credentials === 'include').map(c => c.url.searchParams.get('per_page')), ['100', '10', '10']);
    assert.equal(b.downloads.length, 1);
});
