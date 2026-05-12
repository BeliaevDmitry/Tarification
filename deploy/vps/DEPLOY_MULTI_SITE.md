# Multi-site deploy commands

Ниже только базовые сценарии запуска для каждой школы:
1) запуск с нуля (новый стек);
2) запуск/обновление с сохранением текущей БД (existing stack).

## Где env-файлы
Используйте готовые env-файлы:
- `deploy/vps/env/schadmin7.env`
- `deploy/vps/env/schadmindemo.env`
- `deploy/vps/env/schadmin1811.env`

Перед запуском проверьте в выбранном env-файле минимум:
- `APP_DOMAIN`
- `STACK_NAME`
- `SCHOOL_CODE`
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `SMTP_*`

---

## 1) Schadmin (школа 7)

### С нуля (новый стек)
```bash
cd ~/Tarification
git pull
docker compose -p schadmin7 \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app

# 3) проверка branding
curl -k -sS https://schadmin.ru/api/public/branding
```

### С сохранением БД (legacy стек `tarification`)
```bash
cd ~/Tarification
git pull
docker compose -p tarification \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

---

## 2) Schadmin demo

### С нуля (новый стек)
```bash
cd ~/Tarification
git pull
docker compose -p schadmindemo \
  --env-file deploy/vps/env/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

### С сохранением БД (уже развернутый стек `schadmindemo`)
```bash
cd ~/Tarification
git pull
docker compose -p schadmindemo \
  --env-file deploy/vps/env/schadmindemo.env \
  -f deploy/vps/docker-compose.prod.yml \
  ps
```

---

## 3) Schadmin 1811

### С нуля (новый стек)
```bash
cd ~/Tarification
git pull
docker compose -p schadmin1811 \
  --env-file deploy/vps/env/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

### С сохранением БД (уже развернутый стек `schadmin1811`)
```bash
cd ~/Tarification
git pull
docker compose -p schadmin1811 \
  --env-file deploy/vps/env/schadmin1811.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

Если нашли "старую" БД в другом контейнере/volume, сделайте backup перед любыми действиями:
```bash
docker exec tarification-postgres pg_dump -U tarif_user tariffication_db > backup_tarification.sql
```

И восстановите в рабочий postgres (пример):
```bash
cat backup_tarification.sql | docker compose -p tarification --env-file deploy/vps/env/schadmin7.env -f deploy/vps/docker-compose.prod.yml exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

> Никогда не используйте `docker compose down -v` без актуального backup.



### После восстановления: зафиксируйте один "боевой" postgres
Если основная БД оказалась в `tarification-postgres` (больше данных), закрепите это:
```bash
# 1) Убедиться, что alias postgres в сети tarification указывает на tarification-postgres
docker network connect --alias postgres tarification_default tarification-postgres || true

# 2) Остановить/убрать второй postgres, чтобы не было путаницы
docker stop schadmin7-postgres || true
# при желании можно удалить контейнер (volume останется)
# docker rm schadmin7-postgres

# 3) Перезапустить app
docker compose -p tarification   --env-file deploy/vps/env/schadmin7.env   -f deploy/vps/docker-compose.prod.yml   up -d --build --no-deps app
```


### Важно: данные ПА-спецификаций хранятся в docker volume, не только в БД
Для сервиса `app` примонтированы volumes:
- `/app/pa-reports`
- `/app/pa-specifications`

Если менялся `project name` (`-p`) или `STACK_NAME`, мог подключиться другой (пустой) volume и тогда в UI "пропали" файлы/своды ПА.

Проверка volumes:
```bash
docker volume ls | grep -E "pa_specs|pa_reports|tarification|schadmin"
```

Копирование данных ПА из старого volume в текущий (пример):

Проверить, в каком volume реально лежат файлы ПА (по количеству файлов):
```bash
for v in tarification_pa_specs_data schadmin7_pa_specs_data vps_pa_specs_data; do
  echo "==== $v ===="
  docker run --rm -v ${v}:/data alpine sh -lc 'find /data -type f | wc -l' || true
done
```

Если данные оказались, например, в `vps_pa_specs_data`, копируйте оттуда в рабочий volume:
```bash
docker run --rm   -v vps_pa_specs_data:/from   -v tarification_pa_specs_data:/to   alpine sh -c 'cp -a /from/. /to/'
```
```bash
# пример: из schadmin7_pa_specs_data -> tarification_pa_specs_data
docker run --rm   -v schadmin7_pa_specs_data:/from   -v tarification_pa_specs_data:/to   alpine sh -c 'cp -a /from/. /to/'
```


### Ошибка при скачивании: `Файл версии не найден на диске`
Это значит: запись о версии есть в БД, но физического файла нет в подключенном volume (`/app/pa-reports` или `/app/pa-specifications`).

Проверьте и перенесите также reports-volume:
```bash
for v in tarification_pa_reports_data schadmin7_pa_reports_data vps_pa_reports_data; do
  echo "==== $v ===="
  docker run --rm -v ${v}:/data alpine sh -lc 'find /data -type f | wc -l' || true
done
```

Если файлы в `vps_pa_reports_data`, копируйте в рабочий `tarification_pa_reports_data`:
```bash
docker run --rm   -v vps_pa_reports_data:/from   -v tarification_pa_reports_data:/to   alpine sh -c 'cp -a /from/. /to/'
```

После копирования перезапустите app:
```bash
docker compose -p tarification   --env-file deploy/vps/env/schadmin7.env   -f deploy/vps/docker-compose.prod.yml   up -d --build --no-deps app
```


Если `vps_pa_reports_data`/`vps_pa_specs_data` содержат данные, можно перенести сразу оба volume:
```bash
docker run --rm -v vps_pa_reports_data:/from -v tarification_pa_reports_data:/to alpine sh -c 'cp -a /from/. /to/'
docker run --rm -v vps_pa_specs_data:/from -v tarification_pa_specs_data:/to alpine sh -c 'cp -a /from/. /to/'

docker compose -p tarification   --env-file deploy/vps/env/schadmin7.env   -f deploy/vps/docker-compose.prod.yml   up -d --build --no-deps app
```


Проверить подробное содержимое старых/новых volumes (файлы и размер):
```bash
for v in vps_pa_reports_data tarification_pa_reports_data vps_pa_specs_data tarification_pa_specs_data; do
  echo "===== $v ====="
  docker run --rm -v ${v}:/data alpine sh -lc 'du -sh /data; find /data -type f | head -n 20'
done
```

Проверить, что в БД есть записи по ПА (в рабочем postgres):
```bash
docker exec tarification-postgres psql -U tarif_user -d tariffication_db -Atc   "select 'pa_specification='||count(*) from pa_specification;    select 'pa_specification_task='||count(*) from pa_specification_task;    select 'pa_report_version='||count(*) from pa_report_version;"
```


Если команды с многострочным SQL дают неполный вывод, используйте однострочный вариант:
```bash
docker exec tarification-postgres psql -U tarif_user -d tariffication_db -Atc "select 'pa_specification='||count(*) from pa_specification; select 'pa_specification_task='||count(*) from pa_specification_task; select 'pa_report_version='||count(*) from pa_report_version; select 'curriculum_plan_entry='||count(*) from curriculum_plan_entry;"
```


### Почему `schadmin7_*` volume остаются
Docker volumes не удаляются автоматически после остановки/перезапуска контейнеров.Они остаются до явного удаления (`docker volume rm ...`) или `docker compose down -v`.

Да, в вашем кейсе `schadmin7_*` появились как "новые" при попытке поднять отдельный стек `schadmin7`.

Безопасная очистка `schadmin7_*` после миграции в `tarification`:
```bash
# 1) убедиться, что в tarification уже есть все данные (counts/файлы)
# 2) остановить контейнеры schadmin7
docker rm -f schadmin7-caddy schadmin7-postgres 2>/dev/null || true

# 3) удалить только ненужные volumes schadmin7
docker volume rm   schadmin7_caddy_config schadmin7_caddy_data   schadmin7_pa_reports_data schadmin7_pa_specs_data   schadmin7_postgres_data
```


Проверка причин пустого "Свод 5-11 / 1-4" (при наличии УП на вкладке):
```bash
# 1) какие учебные годы есть в УП
docker exec tarification-postgres psql -U tarif_user -d tariffication_db -Atc "select academic_year, count(*) from curriculum_plan_entry group by academic_year order by academic_year;"

# 2) есть ли строки УП текущего года и не помечены ли deprecated
docker exec tarification-postgres psql -U tarif_user -d tariffication_db -Atc "select count(*) as total, sum(case when deprecated then 1 else 0 end) as deprecated from curriculum_plan_entry where academic_year='2025-2026';"

# 3) есть ли спецификации ПА текущего года
docker exec tarification-postgres psql -U tarif_user -d tariffication_db -Atc "select count(*) from pa_specification where academic_year='2025-2026';"
```

Если `curriculum_plan_entry` в другом `academic_year`, свод будет пустым — ПА и УП должны быть в одном учебном году.
