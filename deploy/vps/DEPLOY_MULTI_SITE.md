# Multi-site deploy commands

## Текущая production-схема для `schadmin.ru`

Сейчас боевой стек школы 7 должен работать как единый compose-проект `schadmin7`:

- `schadmin7-app` — приложение;
- `schadmin7-postgres` — PostgreSQL;
- `schadmin7_postgres_data` — volume с БД;
- `schadmin7_pa_reports_data` — volume с файлами `/app/pa-reports`;
- `schadmin7_pa_specs_data` — volume с файлами `/app/pa-specifications`;
- `tarification-caddy` — текущий Caddy, подключённый к сети `schadmin7_default` и проксирующий `app:8080`.

Старые контейнеры/volumes (`tarification-*`, `vps_postgres_data`, `tarification_pa_*`) можно держать как резерв, пока новая схема не проверена. Не удаляйте их сразу после миграции.

---


## Универсальная схема деплоя для школ `7`, `1811` и `demo` (бэкап → проверка PR → залитие)

Ниже команды для любого из трёх сайтов. Меняйте переменную `SITE` на `7`, `1811` или `demo`.

```bash
cd ~/Tarification

# 0) Выбрать сайт
SITE=7           # варианты: 7 | 1811 | demo
STACK="schadmin${SITE}"
ENV_FILE="deploy/vps/env-presets/${STACK}.env"

# 1) Бэкап БД + файлов перед обновлением
BACKUP_DIR=~/db_backups/${STACK}_$(date +%F_%H-%M-%S)
mkdir -p "$BACKUP_DIR"

# БД
POSTGRES_CONTAINER="${STACK}-postgres"
docker exec "$POSTGRES_CONTAINER" pg_dump -U tarif_user tariffication_db   > "$BACKUP_DIR/${STACK}_postgres.sql"

# Файлы (reports/specs)
docker run --rm   -v "${STACK}_pa_reports_data:/data:ro"   -v "$BACKUP_DIR:/backup"   alpine sh -c "cd /data && tar czf /backup/${STACK}_pa_reports_data.tar.gz ."

docker run --rm   -v "${STACK}_pa_specs_data:/data:ro"   -v "$BACKUP_DIR:/backup"   alpine sh -c "cd /data && tar czf /backup/${STACK}_pa_specs_data.tar.gz ."

ls -lh "$BACKUP_DIR"

# 2) Обновить код и проверить номер PR, который поедет на сервер
# (ожидаем merge-коммит с текстом вида "Merge pull request #123 ...")
git pull
LAST_MERGE=$(git log -1 --merges --pretty=format:'%h %s')
echo "Последний merge-коммит: $LAST_MERGE"
echo "$LAST_MERGE" | grep -E 'Merge pull request #[0-9]+'

# Дополнительно: последние 5 merge-коммитов с PR-номерами
# git log --merges -n 5 --pretty=format:'%h %s'

# 3) Залить обновление
# Для app не используем --no-deps, чтобы штатно подтягивался postgres.
docker compose -p "$STACK"   --env-file "$ENV_FILE"   -f deploy/vps/docker-compose.prod.yml   up -d --build app

# 4) Быстрая проверка после залития
docker ps --format "table {{.Names}}	{{.Status}}	{{.Networks}}" | grep "$STACK" || true
docker exec "${STACK}-app" printenv | grep -E '^(DB_URL|DB_USERNAME|SCHOOL_CODE)='
docker logs --tail=120 "${STACK}-app"
```

Примеры запуска:

```bash
SITE=7
SITE=1811
SITE=demo
```

---

## Безопасное обновление `schadmin7` с бэкапом БД и файлов

Используйте этот сценарий для обычного обновления приложения на сервере.

```bash
cd ~/Tarification

# 1) Бэкап БД и файловых volumes перед обновлением
BACKUP_DIR=~/db_backups/schadmin7_$(date +%F_%H-%M-%S)
mkdir -p "$BACKUP_DIR"

# PostgreSQL dump текущей БД schadmin7
docker exec schadmin7-postgres pg_dump -U tarif_user tariffication_db \
  > "$BACKUP_DIR/schadmin7_postgres.sql"

# Файлы отчётов и спецификаций, которые хранятся не в БД, а в Docker volumes
docker run --rm \
  -v schadmin7_pa_reports_data:/data:ro \
  -v "$BACKUP_DIR:/backup" \
  alpine sh -c "cd /data && tar czf /backup/schadmin7_pa_reports_data.tar.gz ."

docker run --rm \
  -v schadmin7_pa_specs_data:/data:ro \
  -v "$BACKUP_DIR:/backup" \
  alpine sh -c "cd /data && tar czf /backup/schadmin7_pa_specs_data.tar.gz ."

# Проверка, что бэкапы создались и не пустые
ls -lh "$BACKUP_DIR"

# 2) Обновить код
git pull

echo "Последний merge-коммит / PR:"
git log -1 --merges --oneline --decorate

# 3) Обновить приложение
# Без --no-deps: postgres является штатной зависимостью app в проекте schadmin7.
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build app

# 4) Проверка
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Networks}}"
docker exec schadmin7-app printenv | grep -E '^(DB_URL|DB_USERNAME|SCHOOL_CODE)='
docker logs --tail=120 schadmin7-app
```

Ожидаемое значение `DB_URL` внутри `schadmin7-app`:

```text
DB_URL=jdbc:postgresql://postgres:5432/tariffication_db
```

Это нормально для проекта `schadmin7`: имя `postgres` внутри сети `schadmin7_default` должно указывать на контейнер `schadmin7-postgres`.

---

## Быстрая команда обновления после проверки схемы

Когда убедились, что бэкапы создаются корректно, можно использовать тот же сценарий как стандартный. Минимальная команда обновления без бэкапа **не рекомендуется** для production.

```bash
cd ~/Tarification

git pull

echo "Последний merge-коммит / PR:"
git log -1 --merges --oneline --decorate

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build app
```

---

## Восстановление из бэкапа `schadmin7`

Пример восстановления из каталога бэкапа:

```bash
BACKUP_DIR=~/db_backups/schadmin7_YYYY-MM-DD_HH-MM-SS

# Остановить app, чтобы не было записей в БД во время восстановления
docker stop schadmin7-app

# Пересоздать только новую БД-volume schadmin7
# ВНИМАНИЕ: удаляется только schadmin7_postgres_data.
# Не удаляйте vps_postgres_data и tarification_* volumes без отдельного решения.
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  down

docker volume rm schadmin7_postgres_data

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d postgres

# Восстановить БД
docker exec -i schadmin7-postgres psql -U tarif_user -d tariffication_db \
  < "$BACKUP_DIR/schadmin7_postgres.sql"

# Восстановить файлы reports/specs
docker run --rm \
  -v schadmin7_pa_reports_data:/data \
  -v "$BACKUP_DIR:/backup:ro" \
  alpine sh -c "cd /data && tar xzf /backup/schadmin7_pa_reports_data.tar.gz"

docker run --rm \
  -v schadmin7_pa_specs_data:/data \
  -v "$BACKUP_DIR:/backup:ro" \
  alpine sh -c "cd /data && tar xzf /backup/schadmin7_pa_specs_data.tar.gz"

# Запустить app
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build app
```

---

## Проверка БД и файлов после обновления

Проверить основные таблицы:

```bash
docker exec schadmin7-postgres psql -U tarif_user -d tariffication_db -c "
select
  (select count(*) from app_user) as app_user,
  (select count(*) from teacher_directory_entry) as teacher_directory_entry,
  (select count(*) from school_building) as school_building;
"
```

Проверить размеры файловых volumes:

```bash
du -sh /var/lib/docker/volumes/schadmin7_pa_reports_data/_data
du -sh /var/lib/docker/volumes/schadmin7_pa_specs_data/_data
```

Проверить, что Caddy проксирует в новый app:

```bash
docker exec tarification-caddy getent hosts app
docker exec tarification-caddy getent hosts schadmin7-app
docker exec tarification-caddy wget -qO- http://app:8080/ | head
```

Если `app` и `schadmin7-app` указывают на один IP в сети `schadmin7_default`, Caddy проксирует на новый `schadmin7-app`.

---

## Диагностика

Все команды ниже выполняйте из каталога репозитория:

```bash
cd ~/Tarification
```

### Проверка env внутри app

```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  exec app sh -lc 'echo DB_URL=$DB_URL DB_USERNAME=$DB_USERNAME SCHOOL_CODE=$SCHOOL_CODE APP_DOMAIN=$APP_DOMAIN STACK_NAME=$STACK_NAME'
```

### Проверка статуса сервисов

```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  ps
```

### Ошибка: `Conflict. The container name ... is already in use`

```bash
docker ps -a --format "table {{.Names}}\t{{.Status}}" | grep -E "schadmin7|tarification"
```

Если конфликт по `schadmin7-app`, пересоздайте app:

```bash
docker rm -f schadmin7-app || true

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build app
```

### Ошибка: `UnknownHostException: postgres`

Проверьте, что `schadmin7-postgres` запущен и app находится в сети `schadmin7_default`:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Networks}}"
docker exec schadmin7-app getent hosts postgres
```

Если `schadmin7-postgres` не запущен:

```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d postgres app
```

### Ошибка при скачивании: `Файл версии не найден на диске`

Это значит: запись о версии есть в БД, но физического файла нет в подключенном volume (`/app/pa-reports` или `/app/pa-specifications`).

Проверьте volumes у старого и нового app:

```bash
docker inspect tarification-app \
  --format '{{ range .Mounts }}{{ .Name }} -> {{ .Destination }}{{ println }}{{ end }}' || true

docker inspect schadmin7-app \
  --format '{{ range .Mounts }}{{ .Name }} -> {{ .Destination }}{{ println }}{{ end }}'
```

Если старые файлы лежат в `tarification_pa_reports_data` / `tarification_pa_specs_data`, а рабочий app использует `schadmin7_*`, скопируйте файлы:

```bash
docker run --rm \
  -v tarification_pa_reports_data:/from:ro \
  -v schadmin7_pa_reports_data:/to \
  alpine sh -c "cd /from && tar cf - . | tar xf - -C /to"

docker run --rm \
  -v tarification_pa_specs_data:/from:ro \
  -v schadmin7_pa_specs_data:/to \
  alpine sh -c "cd /from && tar cf - . | tar xf - -C /to"

docker restart schadmin7-app
```

---

## Важные правила безопасности

Никогда не выполняйте без свежего бэкапа:

```bash
docker compose down -v
docker system prune --volumes
docker volume rm vps_postgres_data
docker volume rm schadmin7_postgres_data
```

Перед удалением старых контейнеров/volumes убедитесь, что:

1. сайт работает через `schadmin7-app`;
2. `schadmin7-postgres` содержит нужные данные;
3. files volumes `schadmin7_pa_reports_data` и `schadmin7_pa_specs_data` содержат нужные файлы;
4. есть свежий бэкап БД и файлов.
