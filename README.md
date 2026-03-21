# Tarification

Приложение работает в режиме **API + встроенный сайт**. Система дополнена аутентификацией, ролевой авторизацией (RBAC), аудитом действий и личным кабинетом пользователя.

## Быстрый старт

1. Создайте `.env` из шаблона:

```bash
cp .env.example .env
```

2. Заполните как минимум:
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `SMTP_PASSWORD`

3. При необходимости задайте учётные данные первого администратора:
- `ADMIN_USERNAME` — по умолчанию `admin`
- `ADMIN_PASSWORD` — если не задан, временный пароль будет сгенерирован и выведен в лог приложения
- `ADMIN_EMAIL`
- `ADMIN_FULL_NAME`

4. Запустите приложение:

```bash
docker compose up -d --build
```

5. Откройте:
- UI: `http://localhost:8080/login.html`
- Основные страницы также доступны через `/ui/buildings`, `/ui/classes`, `/ui/curriculum`, `/ui/load`, `/ui/teachers`, `/ui/profile`
- Админ-страницы: `/ui/users`, `/ui/audit`

## Роли и права

В системе используются роли:
- `ADMIN`
- `DIRECTOR`
- `DEPUTY_DIRECTOR`
- `BUILDING_HEAD`
- `HR`
- `METHODIST`
- `OPERATOR`

### Матрица доступа

- **Пользователи** — только `ADMIN`
- **Корпуса** — `ADMIN`, `DIRECTOR`, `DEPUTY_DIRECTOR` могут CRUD; `BUILDING_HEAD` видит только свой корпус и может обновлять его карточку; остальные роли только читают
- **Классы** — `ADMIN`, `DIRECTOR`, `DEPUTY_DIRECTOR` могут менять; остальные только читают
- **Учебный план** — `ADMIN`, `DIRECTOR`, `DEPUTY_DIRECTOR` могут менять; остальные только читают
- **Нагрузка** — `ADMIN`, `DIRECTOR`, `DEPUTY_DIRECTOR` могут менять и запускать обработку; `BUILDING_HEAD` может работать только с нагрузкой своего корпуса; остальные только читают
- **Педагоги** — `ADMIN`, `DIRECTOR`, `DEPUTY_DIRECTOR`, `HR` могут менять; остальные только читают
- **Аудит** — только `ADMIN`
- **Профиль** — любой аутентифицированный пользователь

## REST API безопасности

### Аутентификация
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### Управление пользователями (`ADMIN`)
- `GET /api/users`
- `GET /api/users/{id}`
- `POST /api/users`
- `PUT /api/users/{id}`
- `DELETE /api/users/{id}`
- `POST /api/users/{id}/reset-password`

### Профиль
- `PUT /api/profile`
- `POST /api/profile/change-password`

### Аудит (`ADMIN`)
- `GET /api/audit-logs`
- `GET /api/audit-logs/{id}`

## Аудит

Аудит сохраняет:
- входы и выходы пользователей
- CRUD-операции по пользователям, корпусам, учебному плану, нагрузке, педагогам и классному руководству
- смену роли
- смену и сброс пароля

Записи сохраняются асинхронно в таблицу `audit_log`.

## Миграции

Используется **Flyway**. Миграция `V1__security_rbac.sql` создаёт:
- таблицу `users`
- таблицу `audit_log`
- поле `head_user_id` в `school_building`

## Примечания

- Пароли хранятся только в виде BCrypt-хеша.
- Руководитель корпуса может быть привязан только к одному корпусу через `school_building.head_user_id`.
- При первом запуске приложение автоматически создаёт администратора, если пользователь с указанным `ADMIN_USERNAME` отсутствует.
