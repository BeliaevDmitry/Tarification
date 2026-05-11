# Multi-site deploy commands

## Где лежат env-файлы
Шаблоны лежат **в репозитории** здесь:
- `deploy/vps/env-presets/schadmin7.env`
- `deploy/vps/env-presets/schadmindemo.env`
- `deploy/vps/env-presets/schadmin1811.env`

Рабочие env-файлы (для запуска) держите в одном месте: `deploy/vps/env/`
- `deploy/vps/env/schadmin7.env`
- `deploy/vps/env/schadmindemo.env`
- `deploy/vps/env/schadmin1811.env`

Если хотите, можете запускать и напрямую из `env-presets`, но лучше использовать `deploy/vps/env/*` как рабочие файлы.

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
> Если у вас уже рабочий legacy-стек с именем `tarification` и нужно просто "перекрасить" его в школу 7 без миграции данных,
> в `deploy/vps/env/schadmin7.env` оставьте `STACK_NAME=tarification`.


Для legacy-стека `tarification` используйте `-p tarification` (а не `-p schadmin7`):
```bash
docker compose -p tarification \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
> ⚠️ `--no-deps app` используйте только если `postgres` и `caddy` уже запущены в этом же project (`-p`).
> Для первого запуска делайте полный `up -d --build` без `--no-deps`.

```bash
cd ~/Tarification

git pull

docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 2) Schadmin demo
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmindemo \
  --env-file deploy/vps/env/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmindemo \
  --env-file deploy/vps/env/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmindemo \
  --env-file deploy/vps/env/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 3) Schadmin 1811
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin1811 \
  --env-file deploy/vps/env/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin1811 \
  --env-file deploy/vps/env/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmin1811 \
  --env-file deploy/vps/env/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## Проверка, что взялся нужный SCHOOL_CODE
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
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

Если конфликт по `tarification-app` при обновлении legacy-стека:
```bash
# удаляем только контейнер приложения (данные БД в volume не трогаем)
docker rm -f tarification-app

# поднимаем заново app в том же стеке
docker compose -p tarification \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```



## Проверка branding API (какой код школы реально отдает приложение)
В production compose сервис `app` не публикует `8080` на host, поэтому `curl http://localhost:8080` на VPS не сработает.

Проверяйте через домен (через `caddy`):
```bash
curl -k -sS https://schadmin.ru/api/public/branding | sed -n "1,120p"
```
Если ответ пустой, проверьте запрос в verbose-режиме:
```bash
curl -k -v https://schadmin.ru/api/public/branding
```
и статус сервисов:
```bash
docker compose -p tarification --env-file deploy/vps/env/schadmin7.env -f deploy/vps/docker-compose.prod.yml ps
docker compose -p tarification --env-file deploy/vps/env/schadmin7.env -f deploy/vps/docker-compose.prod.yml logs --tail=200 caddy
```

Если `app` уходит в restart, сначала смотрите логи:
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  logs --tail=200 app
```

После того как контейнер в статусе `Up`, проверьте env внутри контейнера:
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  exec app sh -lc 'echo SCHOOL_CODE=$SCHOOL_CODE APP_DOMAIN=$APP_DOMAIN STACK_NAME=$STACK_NAME'
```


## Если `schadmin7-caddy` не стартует: `Bind for 0.0.0.0:80 failed`
Это значит, что порт 80 уже занят другим контейнером/сервисом (обычно другим `caddy`).

Что делать:
1. Посмотрите кто держит 80/443:
```bash
docker ps --format "table {{.Names}}\t{{.Ports}}" | grep -E ":80->|:443->"
```
2. Если домен `schadmin.ru` должен обслуживать именно текущий стек `schadmin7`, остановите старый proxy-контейнер, который держит 80/443.
3. Поднимите стек снова:
```bash
docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Пока `schadmin7-caddy` не запущен, запросы на `https://schadmin.ru` идут в другой контейнер и могут показывать `demo`.
