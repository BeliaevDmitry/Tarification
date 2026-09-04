(async () => {
    'use strict';

    const CFG = {
        perPage: 100,
        delay: 150,
        includeHidden: false
    };

    // Для остановки: window.__MES_EXPORT_STOP = true;
    window.__MES_EXPORT_STOP = false;

    console.clear();
    console.log('МЭШ: режим автоматического определения школы.');
    console.log('Сейчас перехвачу следующий запрос контингента...');
    console.log('После появления сообщения ниже нажми страницу 2 в таблице.');

    // ============================================================
    // 1. ПЕРЕХВАТ FETCH + XHR
    // ============================================================

    let captured = null;
    let resolveCapture;

    const capturedPromise = new Promise(resolve => {
        resolveCapture = resolve;
    });

    const isStudentProfiles = (url, method = 'GET') => {
        try {
            const candidate = new URL(url, location.origin);
            return String(method).toUpperCase() === 'GET'
                && candidate.origin === location.origin
                && /\/student_profiles\/?$/.test(candidate.pathname)
                && candidate.searchParams.has('school_id')
                && candidate.searchParams.has('academic_year_id');
        } catch {
            return false;
        }
    };

    // ---------- FETCH ----------

    const originalFetch = window.fetch;

    window.fetch = async function(input, init = {}) {
        try {
            const url =
                typeof input === 'string'
                    ? input
                    : input instanceof URL ? input.href : input?.url;

            if (isStudentProfiles(url, init.method || input?.method) && !captured) {

                const headers = {};

                // headers из Request
                if (input instanceof Request) {
                    input.headers.forEach((v, k) => {
                        headers[k] = v;
                    });
                }

                // headers из init
                if (init.headers) {
                    new Headers(init.headers).forEach((v, k) => {
                        headers[k] = v;
                    });
                }

                captured = {
                    type: 'fetch',
                    url: new URL(url, location.origin).href,
                    method:
                        init.method ||
                        input?.method ||
                        'GET',
                    headers
                };

                resolveCapture(captured);
            }
        } catch (e) {
            console.warn('Ошибка перехвата fetch:', e);
        }

        return originalFetch.apply(this, arguments);
    };

    // ---------- XHR ----------

    const origOpen =
        XMLHttpRequest.prototype.open;

    const origSetHeader =
        XMLHttpRequest.prototype.setRequestHeader;

    const origSend =
        XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open =
        function(method, url) {

            this.__mesMethod = method;
            this.__mesUrl = url;
            this.__mesHeaders = {};

            return origOpen.apply(this, arguments);
        };

    XMLHttpRequest.prototype.setRequestHeader =
        function(name, value) {

            if (this.__mesHeaders) {
                this.__mesHeaders[name] = value;
            }

            return origSetHeader.apply(this, arguments);
        };

    XMLHttpRequest.prototype.send =
        function() {

            try {
                if (
                    isStudentProfiles(this.__mesUrl, this.__mesMethod) &&
                    !captured
                ) {
                    captured = {
                        type: 'xhr',
                        url: new URL(
                            this.__mesUrl,
                            location.origin
                        ).href,
                        method:
                            this.__mesMethod || 'GET',
                        headers:
                            this.__mesHeaders || {}
                    };

                    resolveCapture(captured);
                }
            } catch (e) {
                console.warn(
                    'Ошибка перехвата XHR:',
                    e
                );
            }

            return origSend.apply(this, arguments);
        };

    console.log('');
    console.log(
        '>>> ТЕПЕРЬ нажми страницу 2 внизу списка «Контингент»'
    );
    console.log(
        '>>> если второй страницы нет — верни заранее выбранный фильтр в «Все».'
    );

    // ============================================================
    // 2. ЖДЁМ РЕАЛЬНЫЙ ЗАПРОС
    // ============================================================

    let timeoutId;
    const timeout = new Promise((_, reject) => {
        timeoutId = setTimeout(
            () => reject(
                new Error(
                    'За 60 секунд запрос student_profiles не появился.'
                )
            ),
            60000
        );
    });

    let req;

    try {
        req = await Promise.race([
            capturedPromise,
            timeout
        ]);
    } finally {

        clearTimeout(timeoutId);

        // возвращаем браузеру оригинальные функции

        window.fetch = originalFetch;

        XMLHttpRequest.prototype.open =
            origOpen;

        XMLHttpRequest.prototype.setRequestHeader =
            origSetHeader;

        XMLHttpRequest.prototype.send =
            origSend;
    }

    // ============================================================
    // 3. РАЗБИРАЕМ ЗАХВАЧЕННЫЙ URL
    // ============================================================

    const originalUrl =
        new URL(req.url);

    const schoolId =
        originalUrl.searchParams.get(
            'school_id'
        );

    const academicYearId =
        originalUrl.searchParams.get(
            'academic_year_id'
        );

    if (!schoolId) {
        throw new Error(
            'В запросе не найден school_id.'
        );
    }

    if (!academicYearId) {
        throw new Error(
            'В запросе не найден academic_year_id.'
        );
    }

    console.log('');
    console.log('Запрос найден.');
    console.log('Школа ID:', schoolId);
    console.log(
        'Учебный год ID:',
        academicYearId
    );

    console.log('Сохраняются текущие фильтры таблицы. Для полной выгрузки они должны быть сняты.');

    // НЕ выводим Authorization в консоль.

    // ============================================================
    // 4. ПОДГОТАВЛИВАЕМ ЗАГОЛОВКИ
    // ============================================================

    const headers =
        new Headers();

    for (
        const [name, value]
        of Object.entries(req.headers || {})
    ) {

        /*
          Эти заголовки браузер выставляет сам,
          повторно задавать их нельзя/не нужно.
        */

        const forbidden = [
            'host',
            'connection',
            'content-length',
            'cookie',
            'accept-encoding',
            'origin',
            'referer',
            'user-agent'
        ];

        if (
            !forbidden.includes(
                name.toLowerCase()
            )
        ) {
            try {
                headers.set(name, value);
            } catch {}
        }
    }

    if (!headers.has('accept')) {
        headers.set(
            'accept',
            'application/json'
        );
    }

    // ============================================================
    // 5. ФУНКЦИЯ ЗАГРУЗКИ
    // ============================================================

    async function loadPage(
        page,
        perPage
    ) {

        if (window.__MES_EXPORT_STOP) {
            throw new Error('Выгрузка остановлена. Неполный CSV не сохраняется.');
        }

        const url =
            new URL(req.url);

        url.searchParams.set(
            'page',
            String(page)
        );

        url.searchParams.set(
            'per_page',
            String(perPage)
        );

        const response =
            await originalFetch(
                url.href,
                {
                    method: 'GET',
                    credentials: 'include',
                    headers
                }
            );

        if (!response.ok) {

            if (
                response.status === 401 ||
                response.status === 403
            ) {
                throw new Error(
                    `Ошибка доступа HTTP ${response.status}. ` +
                    'Обнови МЭШ и повтори запуск.'
                );
            }

            throw new Error(
                `HTTP ${response.status} ` +
                `при загрузке страницы ${page}`
            );
        }

        const data =
            await response.json();

        if (!Array.isArray(data)) {
            console.error(data);

            throw new Error(
                'Неожиданный формат ответа API.'
            );
        }

        return {
            data,

            total:
                Number(
                    response.headers.get(
                        'total-entities'
                    )
                ) || 0,

            pages:
                Number(
                    response.headers.get(
                        'pages'
                    )
                ) || 0,

            pageSize:
                Number(
                    response.headers.get(
                        'pagesize'
                    )
                ) || 0
        };
    }

    // ============================================================
    // 6. ПРОБУЕМ ЗАПРОСИТЬ ПО 100
    // ============================================================

    console.log('');
    console.log(
        'Начинаю загрузку контингента...'
    );

    let perPage =
        CFG.perPage;

    let first;

    try {

        first =
            await loadPage(
                1,
                perPage
            );

    } catch (e) {

        console.warn(
            '100 записей за раз не получилось.'
        );

        console.warn(
            'Перехожу на 10.'
        );

        perPage = 10;

        first =
            await loadPage(
                1,
                perPage
            );
    }

    /*
      Если сервер самостоятельно изменил размер страницы,
      учитываем фактический размер.
    */

    if (first.pageSize) {
        perPage =
            first.pageSize;
    }

    let pages =
        first.pages;

    if (!pages && first.total) {
        pages =
            Math.ceil(
                first.total /
                perPage
            );
    }

    if (!pages) {
        throw new Error(
            'Сервер не сообщил количество страниц.'
        );
    }

    console.log(
        `Всего детей: ${first.total || '?'}`
    );

    console.log(
        `Страниц для загрузки: ${pages}`
    );

    console.log(
        `По ${perPage} записей на запрос.`
    );

    // ============================================================
    // 7. ЗАГРУЖАЕМ ВСЁ
    // ============================================================

    const allStudents = [
        ...first.data
    ];

    for (
        let page = 2;
        page <= pages;
        page++
    ) {

        const result =
            await loadPage(
                page,
                perPage
            );

        allStudents.push(
            ...result.data
        );

        const percent =
            Math.round(
                page / pages * 100
            );

        console.log(
            `[${percent}%] ` +
            `${page}/${pages} — ` +
            `собрано ${allStudents.length}`
        );

        await new Promise(
            resolve =>
                setTimeout(
                    resolve,
                    CFG.delay
                )
        );
    }

    // ============================================================
    // 8. ДЕДУПЛИКАЦИЯ
    // ============================================================

    const studentMap =
        new Map();

    for (const s of allStudents) {

        const key =
            s.id ??
            s.person_id ??
            s.user_id ??
            `${s.user_name}|${s.birth_date}`;

        studentMap.set(
            String(key),
            s
        );
    }

    const students =
        [...studentMap.values()];

    console.log(
        `Уникальных детей: ${students.length}`
    );

    // ============================================================
    // 9. ПРЕДСТАВИТЕЛИ
    // ============================================================

    function representatives(s) {

        let list =
            Array.isArray(s.parents)
                ? s.parents
                : [];

        if (!CFG.includeHidden) {
            list =
                list.filter(
                    p => p.hidden !== true
                );
        }

        return list;
    }

    const maxParents =
        students.reduce(
            (max, s) =>
                Math.max(
                    max,
                    representatives(s).length
                ),
            0
        );

    console.log(
        `Максимум представителей: ${maxParents}`
    );

    // ============================================================
    // 10. ТАБЛИЦА
    // ============================================================

    function phone(x) {
        return (
            x?.phone_number ||
            x?.phone_number_ezd ||
            ''
        );
    }

    function email(x) {
        return (
            x?.email ||
            x?.email_ezd ||
            ''
        );
    }

    function yesNo(value) {
        if (value === true) return 'Да';
        if (value === false) return 'Нет';
        return '';
    }

    function sex(value) {
        if (value === 'male') return 'М';
        if (value === 'female') return 'Ж';
        return value || '';
    }

    const rows =
        students.map(s => {

            const row = {
                'ФИО ребёнка':
                    s.user_name || '',

                'Дата рождения':
                    s.birth_date || '',

                'Возраст':
                    s.age ?? '',

                'Пол':
                    sex(s.sex),

                'Класс / группа':
                    s.class_unit?.name || '',

                'Логин ребёнка':
                    s.gusoev_login || '',

                'Телефон ребёнка':
                    phone(s),

                'Email ребёнка':
                    email(s),

                'СНИЛС ребёнка':
                    s.snils || '',

                'Уровень образования':
                    s.education_level ?? '',

                'Уровень класса':
                    s.class_level ?? '',

                'Study mode ID':
                    s.study_mode_id ?? '',

                'Группа физкультуры ID':
                    s.physical_training_group_id ?? '',

                'Надомный профиль':
                    yesNo(
                        s.home_based_profile
                    ),

                'Надомный класс':
                    yesNo(
                        s.class_unit?.home_based
                    ),

                'Переведён':
                    yesNo(
                        s.transferred
                    ),

                'Наставник / классный руководитель':
                    Array.isArray(s.mentors)
                        ? s.mentors
                            .map(x => x.name)
                            .filter(Boolean)
                            .join('; ')
                        : ''
            };

            const reps =
                representatives(s);

            for (
                let i = 0;
                i < maxParents;
                i++
            ) {

                const p =
                    reps[i] || {};

                const n =
                    i + 1;

                row[
                    `Представитель ${n} — тип`
                ] =
                    p.type || '';

                row[
                    `Представитель ${n} — ФИО`
                ] =
                    p.name || '';

                row[
                    `Представитель ${n} — логин`
                ] =
                    p.gusoev_login || '';

                row[
                    `Представитель ${n} — телефон`
                ] =
                    phone(p);

                row[
                    `Представитель ${n} — email`
                ] =
                    email(p);

                row[
                    `Представитель ${n} — СНИЛС`
                ] =
                    p.snils || '';
            }

            return row;
        });

    window.__MES_STUDENTS =
        students;

    window.__MES_ROWS =
        rows;

    console.table(
        rows.slice(0, 20)
    );

    // ============================================================
    // 11. CSV
    // ============================================================

    if (!rows.length) {
        throw new Error('Нет данных для выгрузки. Проверьте школу и фильтры контингента.');
    }

    const columns =
        Object.keys(rows[0]);

    const csvCell =
        value =>
            `"${String(value ?? '')
                .replace(/"/g, '""')}"`;

    const output = [
        columns
            .map(csvCell)
            .join(';'),

        ...rows.map(
            row =>
                columns
                    .map(
                        col =>
                            csvCell(
                                row[col]
                            )
                    )
                    .join(';')
        )
    ].join('\r\n');

    const blob =
        new Blob(
            ['\uFEFF' + output],
            {
                type:
                    'text/csv;charset=utf-8'
            }
        );

    const objectUrl =
        URL.createObjectURL(blob);

    const a =
        document.createElement('a');

    const date =
        new Date()
            .toISOString()
            .slice(0, 10);

    a.href =
        objectUrl;

    a.download =
        `MES_контингент_${schoolId}_${date}.csv`;

    document.body.appendChild(a);

    a.click();

    a.remove();

    setTimeout(
        () =>
            URL.revokeObjectURL(
                objectUrl
            ),
        2000
    );

    console.log('');
    console.log(
        '==================================='
    );

    console.log('ГОТОВО');

    console.log(
        `School ID: ${schoolId}`
    );

    console.log(
        `Учебный год: ${academicYearId}`
    );

    console.log(
        `Детей выгружено: ${rows.length}`
    );

    console.log(
        `Файл: MES_контингент_${schoolId}_${date}.csv`
    );

    console.log(
        '==================================='
    );
})();
