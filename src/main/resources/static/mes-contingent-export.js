(async () => {
    'use strict';

    // ============================================================
    // НАСТРОЙКИ
    // ============================================================

    const CONFIG = {
        schoolId: 936,
        academicYearId: 14,
        profileId: 38063975,

        // Сначала попробуем получить по 100 записей.
        // Если сервер ограничивает размер, скрипт это определит.
        preferredPerPage: 100,

        // Небольшая пауза между запросами.
        delayMs: 150,

        // false = не выгружать представителей с hidden=true
        includeHiddenRepresentatives: false
    };

    // Для аварийной остановки можно выполнить в Console:
    // window.__MES_EXPORT_STOP = true;
    window.__MES_EXPORT_STOP = false;

    const sleep = ms =>
        new Promise(resolve => setTimeout(resolve, ms));

    // ============================================================
    // COOKIE / АВТОРИЗАЦИЯ
    // ============================================================

    function getCookie(name) {
        const prefix = `${name}=`;

        const item = document.cookie
            .split('; ')
            .find(row => row.startsWith(prefix));

        if (!item) return null;

        return item.substring(prefix.length);
    }

    function safeDecode(value) {
        if (!value) return null;

        try {
            return decodeURIComponent(value);
        } catch {
            return value;
        }
    }

    /*
      В текущей сессии МЭШ токен обычно присутствует
      в cookie aupd_token.

      Никакой токен в сам текст скрипта вставлять не надо.
    */
    const token = safeDecode(getCookie('aupd_token'));

    const requestHeaders = {
        'accept': 'application/json',
        'aid': String(CONFIG.academicYearId),
        'profile-id': String(CONFIG.profileId),
        'x-mes-hostid': '22',
        'x-mes-roleid': '8',
        'x-mes-subsystem': 'hteacherweb'
    };

    if (token) {
        requestHeaders['authorization'] = `Bearer ${token}`;
    }

    // ============================================================
    // ЗАПРОС ОДНОЙ СТРАНИЦЫ
    // ============================================================

    async function loadPage(page, perPage) {

        const params = new URLSearchParams({
            page: String(page),
            per_page: String(perPage),
            with_deleted: 'false',
            with_user_info: 'true',
            school_id: String(CONFIG.schoolId),
            academic_year_id: String(CONFIG.academicYearId)
        });

        const url =
            '/api/ej/core/teacher/v1/student_profiles?' +
            params.toString();

        const response = await fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers: requestHeaders
        });

        if (!response.ok) {
            let responseText = '';

            try {
                responseText = await response.text();
            } catch {}

            if (response.status === 401 || response.status === 403) {
                throw new Error(
                    `Ошибка авторизации HTTP ${response.status}. ` +
                    `Обнови страницу МЭШ и запусти скрипт снова.`
                );
            }

            throw new Error(
                `HTTP ${response.status} на странице ${page}.\n` +
                responseText.substring(0, 500)
            );
        }

        const data = await response.json();

        if (!Array.isArray(data)) {
            console.error('Неожиданный ответ:', data);

            throw new Error(
                'API вернул не массив учеников.'
            );
        }

        return {
            data,

            pages:
                Number(response.headers.get('pages')) || 0,

            pageSize:
                Number(response.headers.get('pagesize')) || 0,

            total:
                Number(response.headers.get('total-entities')) || 0
        };
    }

    // ============================================================
    // ПЕРВАЯ СТРАНИЦА
    // ============================================================

    console.log('МЭШ: начинаю выгрузку контингента...');

    let perPage = CONFIG.preferredPerPage;
    let firstPage;

    try {
        firstPage = await loadPage(1, perPage);
    } catch (error) {

        console.warn(
            `Не получилось запросить ${perPage} записей. ` +
            'Пробую стандартные 10...',
            error
        );

        perPage = 10;
        firstPage = await loadPage(1, perPage);
    }

    /*
      Сервер может сам уменьшить per_page.
    */
    if (firstPage.pageSize > 0) {
        perPage = firstPage.pageSize;
    }

    let totalPages = firstPage.pages;

    if (!totalPages && firstPage.total) {
        totalPages =
            Math.ceil(firstPage.total / perPage);
    }

    if (!totalPages) {
        throw new Error(
            'Не удалось определить количество страниц.'
        );
    }

    console.log(
        `Всего записей: ${firstPage.total || 'неизвестно'}`
    );

    console.log(
        `Размер страницы: ${perPage}`
    );

    console.log(
        `Количество страниц: ${totalPages}`
    );

    // ============================================================
    // ЗАГРУЗКА ВСЕХ СТРАНИЦ
    // ============================================================

    const students = [...firstPage.data];

    console.log(
        `Страница 1/${totalPages}: ` +
        `получено ${firstPage.data.length}, ` +
        `всего ${students.length}`
    );

    for (let page = 2; page <= totalPages; page++) {

        if (window.__MES_EXPORT_STOP) {
            console.warn(
                'Выгрузка остановлена пользователем.'
            );
            break;
        }

        const result =
            await loadPage(page, perPage);

        students.push(...result.data);

        console.log(
            `Страница ${page}/${totalPages}: ` +
            `+${result.data.length}, ` +
            `всего ${students.length}`
        );

        if (CONFIG.delayMs > 0) {
            await sleep(CONFIG.delayMs);
        }
    }

    // ============================================================
    // УДАЛЯЕМ ВОЗМОЖНЫЕ ДУБЛИКАТЫ
    // ============================================================

    const uniqueStudentsMap = new Map();

    for (const student of students) {

        const key =
            student.id ??
            student.person_id ??
            student.user_id ??
            [
                student.user_name,
                student.birth_date,
                student.snils
            ].join('|');

        uniqueStudentsMap.set(key, student);
    }

    const uniqueStudents =
        [...uniqueStudentsMap.values()];

    console.log(
        `После удаления дублей: ${uniqueStudents.length}`
    );

    if (
        firstPage.total &&
        uniqueStudents.length !== firstPage.total &&
        !window.__MES_EXPORT_STOP
    ) {
        console.warn(
            `API сообщает ${firstPage.total} записей, ` +
            `а получено ${uniqueStudents.length}.`
        );
    }

    // ============================================================
    // ПРЕДСТАВИТЕЛИ
    // ============================================================

    function getRepresentatives(student) {

        let parents =
            Array.isArray(student.parents)
                ? student.parents
                : [];

        if (!CONFIG.includeHiddenRepresentatives) {
            parents =
                parents.filter(parent => !parent.hidden);
        }

        return parents;
    }

    const maxRepresentatives =
        uniqueStudents.reduce(
            (max, student) =>
                Math.max(
                    max,
                    getRepresentatives(student).length
                ),
            0
        );

    console.log(
        `Максимальное число представителей ` +
        `у одного ребёнка: ${maxRepresentatives}`
    );

    // ============================================================
    // ФОРМИРУЕМ СТРОКИ
    // ============================================================

    function sexName(sex) {

        if (sex === 'male') return 'М';
        if (sex === 'female') return 'Ж';

        return sex || '';
    }

    function preferredPhone(person) {
        return (
            person?.phone_number ||
            person?.phone_number_ezd ||
            ''
        );
    }

    function preferredEmail(person) {
        return (
            person?.email ||
            person?.email_ezd ||
            ''
        );
    }

    const rows = uniqueStudents.map(student => {

        const representatives =
            getRepresentatives(student);

        const row = {
            'ФИО ребёнка':
                student.user_name || '',

            'Дата рождения':
                student.birth_date || '',

            'Возраст':
                student.age ?? '',

            'Пол':
                sexName(student.sex),

            'Класс / группа':
                student.class_unit?.name || '',

            'Логин ребёнка':
                student.gusoev_login || '',

            'Email ребёнка':
                preferredEmail(student),

            'Телефон ребёнка':
                preferredPhone(student),

            'СНИЛС ребёнка':
                student.snils || '',

            'Классный руководитель / наставник':
                Array.isArray(student.mentors)
                    ? student.mentors
                        .map(x => x.name)
                        .filter(Boolean)
                        .join('; ')
                    : ''
        };

        for (
            let i = 0;
            i < maxRepresentatives;
            i++
        ) {
            const parent =
                representatives[i] || {};

            const n = i + 1;

            row[`Представитель ${n} — тип`] =
                parent.type || '';

            row[`Представитель ${n} — ФИО`] =
                parent.name || '';

            row[`Представитель ${n} — логин`] =
                parent.gusoev_login || '';

            row[`Представитель ${n} — телефон`] =
                preferredPhone(parent);

            row[`Представитель ${n} — email`] =
                preferredEmail(parent);

            row[`Представитель ${n} — СНИЛС`] =
                parent.snils || '';
        }

        return row;
    });

    // ============================================================
    // ПРЕДПРОСМОТР В CONSOLE
    // ============================================================

    console.table(rows.slice(0, 20));

    /*
      Сохраним результат ещё и в памяти страницы.
      Потом можно посмотреть:
        window.__MES_STUDENTS
        window.__MES_ROWS
    */
    window.__MES_STUDENTS = uniqueStudents;
    window.__MES_ROWS = rows;

    // ============================================================
    // CSV ДЛЯ EXCEL
    // ============================================================

    if (!rows.length) {
        throw new Error(
            'Нет данных для выгрузки.'
        );
    }

    const columnNames =
        Object.keys(rows[0]);

    function csvCell(value) {

        const text =
            String(value ?? '')
                .replace(/"/g, '""');

        return `"${text}"`;
    }

    const csvLines = [];

    csvLines.push(
        columnNames
            .map(csvCell)
            .join(';')
    );

    for (const row of rows) {

        csvLines.push(
            columnNames
                .map(column =>
                    csvCell(row[column])
                )
                .join(';')
        );
    }

    const csv =
        '\uFEFF' +
        csvLines.join('\r\n');

    const blob =
        new Blob(
            [csv],
            {
                type:
                    'text/csv;charset=utf-8'
            }
        );

    const downloadUrl =
        URL.createObjectURL(blob);

    const link =
        document.createElement('a');

    const today =
        new Date()
            .toISOString()
            .slice(0, 10);

    link.href = downloadUrl;

    link.download =
        `MES_контингент_${CONFIG.schoolId}_${today}.csv`;

    document.body.appendChild(link);

    link.click();

    link.remove();

    setTimeout(
        () => URL.revokeObjectURL(downloadUrl),
        1000
    );

    console.log(
        '========================================'
    );

    console.log(
        `ГОТОВО: выгружено ${rows.length} детей.`
    );

    console.log(
        `Представителей максимум: ${maxRepresentatives}.`
    );

    console.log(
        'CSV-файл отправлен на скачивание.'
    );

    console.log(
        '========================================'
    );
})();
