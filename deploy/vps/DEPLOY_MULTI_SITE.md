# Multi-site deploy commands

## Где лежат env-файлы
Актуальные пресеты лежат **в репозитории** здесь:
- `deploy/vps/env-presets/schadmin7.env`
- `deploy/vps/env-presets/schadmindemo.env`
- `deploy/vps/env-presets/schadmin1811.env`

Используйте эти файлы напрямую через `--env-file`.

Перед запуском обязательно проверьте/обновите минимум:
- `APP_DOMAIN`
- `STACK_NAME` (должен быть уникальным для каждого сайта)
- `SCHOOL_CODE`
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `SMTP_*`

## Важно
- Для каждого сайта используйте отдельный project name (`-p`), чтобы контейнеры/volumes/сети не пересекались.
- На **одном сервере с несколькими сайтами** нельзя публиковать `80:80`/`443:443` в каждом стеке одновременно.
  - Либо запускайте только один `caddy` с портами наружу.
  - Либо используйте внешний reverse-proxy и поднимайте `app` без локального `caddy`.

## 1) Schadmin (школа 7)
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 2) Schadmin demo
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmindemo \
  --env-file deploy/vps/env-presets/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmindemo \
  --env-file deploy/vps/env-presets/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmindemo \
  --env-file deploy/vps/env-presets/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 3) Schadmin 1811
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin1811 \
  --env-file deploy/vps/env-presets/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin1811 \
  --env-file deploy/vps/env-presets/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmin1811 \
  --env-file deploy/vps/env-presets/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## Проверка, что взялся нужный SCHOOL_CODE
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  exec app printenv SCHOOL_CODE
```


## Если получили ошибку `Conflict. The container name ... is already in use`
Это значит, что совпал `STACK_NAME` (или остались старые контейнеры с таким именем).

Проверьте `STACK_NAME` в используемом `--env-file` и задайте уникальные значения:
- `schadmin7`
- `schadmindemo`
- `schadmin1811`

Проверить занятые имена:
```bash
docker ps -a --format "table {{.Names}}\t{{.Status}}" | grep -E "schadmin|tarification"
```


## Проверка branding API (какой код школы реально отдает приложение)
```bash
curl -sS http://localhost:8080/api/public/branding | sed -n "1,120p"
```
Если в ответе `schoolCode` не `7`, проверьте env внутри контейнера:
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env-presets/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  exec app sh -lc "echo SCHOOL_CODE=$SCHOOL_CODE APP_DOMAIN=$APP_DOMAIN STACK_NAME=$STACK_NAME"
```
