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
