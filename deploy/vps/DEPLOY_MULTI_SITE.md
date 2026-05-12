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
  up -d --build
```

### С сохранением БД (legacy стек `tarification`)
```bash
cd ~/Tarification
git pull
docker compose -p tarification \
  --env-file deploy/vps/env/schadmin7.env \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
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
  up -d --build --no-deps app
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
  up -d --build
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
