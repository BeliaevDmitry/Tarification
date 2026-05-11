# Tarification
добавил меню
Приложение работает в режиме **API + встроенный сайт**.
Старая часть с автоматической обработкой локальных Excel-файлов удалена.

## Быстрый старт

1. Создайте `.env` из шаблона:

```bash
cp .env.example .env
```

2. Заполните как минимум:
- `SCHOOL_CODE` (например: `7`, `1811`, `demo`)
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `SMTP_PASSWORD`

3. Запустите:

```bash
docker compose up -d --build
```

4. Откройте:
- UI: `http://localhost:8080/load.html`
- Доп. страницы: `teachers.html`, `buildings.html`, `classes.html`, `curriculum.html`

## Деплой на VPS

В репозитории добавлен production-набор для VPS в `deploy/vps/`:
- `docker-compose.prod.yml` — поднимает `postgres`, `app`, `caddy`
- `Caddyfile` — reverse proxy и автоматический HTTPS
- `.env.production.example` — шаблон production-переменных
- `deploy.sh` — обёртка для быстрого запуска/обновления стека

### 1. Подготовить сервер

Понадобится Ubuntu/Debian VPS с:
- Docker Engine
- Docker Compose Plugin
- Открытыми портами `80/tcp`, `443/tcp`, при необходимости `22/tcp`
- DNS-записью домена, указывающей на публичный IP сервера

Пример установки Docker:

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

### 2. Скопировать проект на сервер

```bash
git clone <YOUR_REPOSITORY_URL>
cd Tarification
```

### 3. Создать production env-файл

```bash
cp deploy/vps/.env.production.example deploy/vps/.env.production
```

Обязательно задайте:
- `APP_DOMAIN` — домен для HTTPS
- `SCHOOL_CODE` — код школы для брендирования (например: `7`, `1811`, `demo`)
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `APP_ADMIN_PASSWORD` (на первом запуске по умолчанию `admin`)
- `SMTP_USERNAME`
- `SMTP_PASSWORD`

> Рекомендуется использовать длинные случайные пароли и не хранить production `.env` в git.

### 4. Запустить production стек

Быстрый запуск:

```bash
./deploy/vps/deploy.sh
```

Эквивалентная полная команда:

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

### 5. Проверить статус

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  ps
```

Логи приложения:

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  logs -f app
```

После выпуска TLS-сертификата приложение будет доступно по адресу:

```text
https://<APP_DOMAIN>/load.html
```

### 6. Обновление после изменений

```bash
git pull
./deploy/vps/deploy.sh
```

### 7. Резервное копирование базы

Создать дамп:

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql
```

Восстановить:

```bash
cat backup.sql | docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

## Что осталось в системе

Только веб-сценарий с REST API и страницами для ручной работы:
- `/api/teachers`
- `/api/buildings`
- `/api/classroom-leadership`
- `/api/curriculum`
- `/api/manual-load`

## Примечания

- Импорт учителей через `/api/teachers/import` (загрузка Excel через UI/API) сохранён как часть веб-сценария.
- Legacy endpoint'ы и режимы старого файлового пайплайна удалены из конфигурации и документации.
- В production рекомендуется публиковать приложение только через reverse proxy, а не открывать внутренний порт `8080` наружу.


## Обновить только приложение (без удаления базы)

В production-стеке база и приложение находятся в разных контейнерах:
- `postgres` — контейнер базы данных
- `app` — контейнер приложения
- `caddy` — reverse proxy

Чтобы пересобрать и перезапустить **только приложение**, не трогая БД:

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

Перезапустить только приложение без пересборки:

```bash
docker compose \
  --env-file deploy/vps/.env.production \
  -f deploy/vps/docker-compose.prod.yml \
  restart app
```
