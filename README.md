# Tarification

## Настройка секретов (обязательно)

В репозитории **не хранятся реальные пароли**. Для запуска создайте локальный файл `.env` на основе шаблона:

```bash
cp .env.example .env
```

Заполните в `.env` как минимум:
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `SMTP_PASSWORD`

> `.env` игнорируется Git и не должен попадать в репозиторий.

## Где используются переменные

- `docker-compose.yml`:
  - для БД: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`;
  - для приложения в контейнере: `DB_PASSWORD`, `SMTP_USERNAME`, `SMTP_PASSWORD` (обязательны).
- `src/main/resources/application.yml`:
  - `DB_PASSWORD`, `SMTP_USERNAME`, `SMTP_PASSWORD` (обязательны),
  - остальные параметры имеют безопасные дефолты для локального запуска.
- `HibernateConfig`:
  - `DB_PASSWORD` обязателен на этапе инициализации SessionFactory.

## Запуск

### Вариант A (рекомендуется): всё в Docker Compose

1. Создайте `.env` из шаблона и заполните обязательные поля.
2. Запустите сервисы:
   ```bash
   docker compose up -d --build
   ```
3. Откройте:
   - UI: `http://localhost:8080/`
   - режим: `http://localhost:8080/api/system/mode`


Если Docker сообщает конфликт имени контейнера (например `"/postgres" is already in use`), выполните:

```bash
docker stop postgres || true
docker rm postgres || true
docker compose up -d --build
```

### Частая ошибка: `password authentication failed for user "tarif_user"` (`SQLState 28P01`)

Это означает, что пароль пользователя в PostgreSQL не совпадает с `DB_PASSWORD` приложения.

1. Убедитесь, что в `.env` согласованы значения:
   - `POSTGRES_USER` = `DB_USERNAME`
   - `POSTGRES_PASSWORD` = `DB_PASSWORD`
2. Если БД была создана раньше с другим паролем, пересоздайте volume:
   ```bash
   docker compose down -v
   docker compose up -d --build
   ```
3. Если данные в БД нужно сохранить, смените пароль пользователя в существующей БД:
   ```bash
   docker exec -it <postgres_container> psql -U tarif_user -d tariffication_db
   ALTER USER tarif_user WITH PASSWORD '<DB_PASSWORD_из_.env>';
   \q
   ```

> Примечание: `.env.example` содержит демонстрационные значения. Для локального запуска задайте реальные секреты в `.env`.

### Вариант B: только БД в Docker, приложение локально


### Если отображается старый интерфейс

1. Убедитесь, что вы именно запускаете приложение, а не только компиляцию:
   - `mvn clean compile` **не запускает** сервер
   - используйте `mvn spring-boot:run` или `docker compose up -d --build`
2. Перезапустите приложение после `git pull` / обновления ветки.
3. Сделайте hard refresh в браузере (`Ctrl+F5`) и откройте `http://localhost:8080/`.
4. Проверьте маркер новой версии на главной: `UI версия: workspace-tabs-v2`.
5. Если продолжает открываться старое, очистите кэш браузера для `localhost:8080`.


1. Поднимите только БД:
   ```bash
   docker compose up -d postgres
   ```
2. Запустите приложение Maven/Spring Boot локально.

Приложение читает `.env` при старте и подставляет значения в системные свойства (если одноимённая переменная окружения не задана).

## Режим собственного сервиса ввода нагрузки

Приложение переведено в API+Frontend режим и **не зависит от Google Sheets**.
Сценарий работы: пользователь работает в веб-странице (`/`) или через REST API, данные сохраняются в БД.

### Встроенный фронтенд для ручной работы

Добавлена простая веб-страница для ручного заполнения и корректировок:
- откройте `http://localhost:8080/`
- сначала заполните справочник корпусов: `http://localhost:8080/buildings.html`
- страницы используют те же API (`/api/manual-load`, `/api/curriculum`, `/api/naming-mesh`, и т.д.)
- добавлена отдельная страница `/load.html`: вкладка «Нагрузка» с табами по корпусам, добавлением классов только из учебного плана и автоподтягиванием предметов
- добавлена отдельная страница `/teachers.html`: вкладка «Педагоги» с импортом из Excel и ручным добавлением ФИО
- добавлена отдельная страница `/classes.html`: справочник классов (корпус, класс, направление, классный руководитель)
- формат класса автоматически нормализуется к виду `7-А` (например, `7а`, `7 А`, `7-а` сохраняются как `7-А`)
- во вкладке учебного плана при добавлении записи обязательно выбирается корпус
- во вкладке нагрузки ФИО педагога выбирается из справочника (`/api/teachers`) с поиском по набору текста
- на главной (`/`) есть вкладки: **Ручная нагрузка** и **Корректировка МЭШ**, второй раздел навигации — **Классы**, а учебный план вынесен на отдельную страницу `/curriculum.html`
- на странице учебного плана есть сводная матрица: 1-я строка заголовка — направление класса, 2-я — класс; далее предметы по 3 блокам и суммы блоков, с редактированием часов по клику в ячейке
- ключевые места в `static/app.js` прокомментированы на русском, чтобы можно было самостоятельно настраивать поведение

#### Логика структуры (чтобы было проще поддерживать)
- `src/main/resources/static/index.html` — только разметка блоков и форм.
- `src/main/resources/static/styles.css` — только стили (без логики).
- `src/main/resources/static/app.js` — только логика взаимодействия с API (все ключевые шаги вынесены в отдельные функции и прокомментированы).
- `src/main/java/org/school/personalLoad/controller/api/ApiExceptionHandler.java` — единый формат API-ошибок для фронтенда.
- `GET /api/system/mode` — быстрый контроль, в каком режиме запущено приложение (основной API+Frontend или legacy).

#### Legacy-режим (только при необходимости миграции)
По умолчанию legacy-обработка файлов выключена: `app.legacy-mode-enabled=false`.
Если нужно временно использовать старый файловый пайплайн, включите `LEGACY_MODE_ENABLED=true` в окружении.


### Основные endpoint'ы

- `POST /api/manual-load` — добавить 1 запись нагрузки.
- `POST /api/manual-load/bulk` — массово добавить записи.
- `GET /api/manual-load` — получить текущие записи ручного ввода.
- `DELETE /api/manual-load` — очистить ручной ввод.
- `POST /api/manual-load/process` — обработать текущий ручной ввод и сохранить в основную тарификацию.
- `GET /api/system/mode` — проверить активный режим запуска (`api-frontend` или `legacy-file-pipeline`).

### Ручная корректировка названий МЭШ (из списка предметов)

Изначально класс/группа в МЭШ принимаются равными значениям из УП (`className`, `groupNameEducationalPlan`), но теперь можно вручную скорректировать соответствия:

- `GET /api/naming-mesh/subjects` — получить список предметов (вкладка/таблица «Предметы»).
- `GET /api/naming-mesh/subjects/{subjectName}/classes` — получить классы по выбранному предмету.
- `GET /api/naming-mesh/mappings?subjectName=...&className=...` — получить текущие связи УП→МЭШ (класс/группа).
- `PUT /api/naming-mesh/mappings` — создать или обновить ручную связь УП→МЭШ для конкретной комбинации `subjectName + className + groupNameEducationalPlan`.

Пример `PUT /api/naming-mesh/mappings`:

```json
{
  "subjectName": "Математика",
  "className": "9-А",
  "groupNameEducationalPlan": "9-А 1 гр",
  "classNameMesh": "9А мат профиль",
  "groupNameMesh": "9А мат 1"
}
```

Если `classNameMesh` не передан, используется `className` (из УП). Если `groupNameMesh` не передан, используется `groupNameEducationalPlan`.

Ответ `POST /api/manual-load/process`:

```json
{
  "status": "ok",
  "processed": 3,
  "summaries": [
    {
      "className": "9-А",
      "subjectName": "Математика",
      "educationLevel": "BASIC",
      "plannedHours": 5,
      "actualHours": 4,
      "remainingHours": 1
    }
  ]
}
```

`actualHours` — суммарная фактическая нагрузка по всем записям ручного ввода для комбинации `className + subjectName + educationLevel`, `remainingHours = plannedHours - actualHours`.

### Пример JSON для `POST /api/manual-load`

```json
{
  "fioTeacher": "Иванов Иван Иванович",
  "numberSchoolBuilding": "1 корп",
  "subjectName": "Математика",
  "className": "9-А",
  "load": 5,
  "groupNameEducationalPlan": "9-А 1 гр",
  "groupLoad": 5,
  "educationLevel": "BASIC"
}
```


### Импорт списка педагогов из Excel

- `POST /api/teachers/import` (multipart, параметр `file`) — импортирует педагогов с листа `Педагоги` (или первого листа, если `Педагоги` не найден).
- `POST /api/teachers` — ручное добавление педагога в справочник.
- `GET /api/teachers` — получить справочник педагогов.
- `DELETE /api/teachers` — очистить справочник педагогов.

Пример импорта:

```bash
curl -X POST "http://localhost:8080/api/teachers/import" \
  -F "file=@teachers.xlsx"
```



### Корпуса

- `GET /api/buildings` — список корпусов.
- `POST /api/buildings` — создать/обновить корпус по `code`.
- `DELETE /api/buildings` — очистить справочник корпусов.

Пример `POST /api/buildings`:

```json
{
  "code": "1",
  "name": "1 корпус (начальная школа)"
}
```

### Классы (корпус + направление + классный руководитель)

- `GET /api/classroom-leadership` — получить текущий список классов.
- `PUT /api/classroom-leadership` — заменить список классов (пустой массив очищает список).
- `DELETE /api/classroom-leadership` — очистить список классов.

Формат элемента для `PUT /api/classroom-leadership`:

```json
{
  "numberSchoolBuilding": "1",
  "className": "7А",
  "classDirection": "Гуманитарное",
  "fioTeacher": "Иванова Анна Петровна"
}
```

`className` и `classDirection` должны быть заполнены, `fioTeacher` должен присутствовать в справочнике педагогов.

### Учебный план по классам 1-11 (часы, подгруппы, уровень)

Добавлены endpoint'ы для отдельного ведения учебного плана по каждому классу:

- `POST /api/curriculum` — создать/обновить правило для пары `className + subjectName + educationLevel`.
- `POST /api/curriculum/bulk` — массовая загрузка правил.
- `GET /api/curriculum` — получить все правила учебного плана.
- `DELETE /api/curriculum` — очистить правила учебного плана.

Поля правила:
- `className` — класс (например, `9-А`, `11-Б`).
- `subjectName` — предмет.
- `plannedHours` — плановые часы.
- `subgroupRequired` — нужно ли деление на подгруппы.
- `subgroupCount` — количество подгрупп (если требуется деление).
- `educationLevel` — `BASIC` или `ADVANCED`.

Пример `POST /api/curriculum`:

```json
{
  "className": "9-А",
  "subjectName": "Математика",
  "plannedHours": 5,
  "subgroupRequired": true,
  "subgroupCount": 2,
  "educationLevel": "BASIC",
  "curriculumPart": "CORE"
}
```

`curriculumPart`: `CORE` (основная часть), `FORMABLE` (формируемая часть), `EXTRACURRICULAR` (внеурочная деятельность).

При `POST /api/manual-load/process` теперь выполняется валидация нагрузки по учебному плану:
- правило должно существовать,
- часы не должны превышать `plannedHours`,
- если `subgroupRequired=true`, то `groupNameEducationalPlan` обязателен.


## Чек-лист перед PR / выкладкой

1. Проверить режим запуска:
   - `GET /api/system/mode` должен возвращать `mode: api-frontend` (если не нужна legacy-обработка).
2. Проверить базовый сценарий ручной нагрузки:
   - добавить запись через `POST /api/manual-load`;
   - выполнить `POST /api/manual-load/process`;
   - убедиться, что в ответе есть `status`, `processed`, `summaries`.
3. Проверить ручную корректировку УП→МЭШ:
   - получить предметы `GET /api/naming-mesh/subjects`;
   - обновить связь через `PUT /api/naming-mesh/mappings`;
   - проверить результат через `GET /api/naming-mesh/mappings?...`.
4. Проверить, что обязательные секреты заданы в `.env` (`DB_PASSWORD`, `SMTP_USERNAME`, `SMTP_PASSWORD`).

5. Проверить новый маршрут данных (корпуса → классы → учебный план → нагрузка):
   - создать/обновить корпус через `POST /api/buildings`;
   - создать класс с направлением и классным руководителем через `PUT /api/classroom-leadership`;
   - добавить предмет УП через `POST /api/curriculum` с выбором класса из справочника;
   - убедиться, что на `/load.html` предметы подтягиваются автоматически для выбранного корпуса.

