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
  - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (все обязательны).
- `src/main/resources/application.yml`:
  - `DB_PASSWORD`, `SMTP_USERNAME`, `SMTP_PASSWORD` (обязательны),
  - остальные параметры имеют безопасные дефолты для локального запуска.
- `HibernateConfig`:
  - `DB_PASSWORD` обязателен на этапе инициализации SessionFactory.

## Запуск

1. Поднимите БД:
   ```bash
   docker compose up -d
   ```
2. Запустите приложение Maven/Spring Boot.

Приложение теперь автоматически читает `.env` при старте и подставляет значения в системные свойства (если одноимённая переменная окружения не задана).

## Режим собственного сервиса ввода нагрузки

Чтобы приложение работало как API (без автоскачивания Google Sheets), выставите:

```bash
RUN_BATCH_ON_STARTUP=false
```

### Основные endpoint'ы

- `POST /api/manual-load` — добавить 1 запись нагрузки.
- `POST /api/manual-load/bulk` — массово добавить записи.
- `GET /api/manual-load` — получить текущие записи ручного ввода.
- `DELETE /api/manual-load` — очистить ручной ввод.
- `POST /api/manual-load/process` — обработать текущий ручной ввод и сохранить в основную тарификацию.

### Пример JSON для `POST /api/manual-load`

```json
{
  "fioTeacher": "Иванов Иван Иванович",
  "numberSchoolBuilding": "1 корп",
  "subjectName": "Математика",
  "className": "9-А",
  "load": 5,
  "groupNameEducationalPlan": "",
  "groupLoad": 5
}
```


### Импорт списка педагогов из Excel

- `POST /api/teachers/import` (multipart, параметр `file`) — импортирует педагогов с листа `Педагоги` (или первого листа, если `Педагоги` не найден).
- `GET /api/teachers` — получить справочник педагогов.
- `DELETE /api/teachers` — очистить справочник педагогов.

Пример импорта:

```bash
curl -X POST "http://localhost:8080/api/teachers/import" \
  -F "file=@teachers.xlsx"
```
