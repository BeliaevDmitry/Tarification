# Multi-site deploy commands

## Подготовка env-файлов
Скопируйте пресет и задайте реальные пароли/SMTP:
```bash
cp deploy/vps/env-presets/schadmin7.env deploy/vps/.env.schadmin7
cp deploy/vps/env-presets/schadmindemo.env deploy/vps/.env.schadmindemo
cp deploy/vps/env-presets/schadmin1811.env deploy/vps/.env.schadmin1811
```


## Важно
- Для каждого сайта используйте отдельный project name (`-p`), чтобы контейнеры/volume/сети не пересекались.
- Перед запуском проверьте DNS на нужный VPS.

## 1) Schadmin (школа 7)
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin7 \
  --env-file deploy/vps/.env.schadmin7 \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin7 \
  --env-file deploy/vps/.env.schadmin7 \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmin7 \
  --env-file deploy/vps/.env.schadmin7 \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 2) Schadmin demo
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmindemo \
  --env-file deploy/vps/.env.schadmindemo \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmindemo \
  --env-file deploy/vps/.env.schadmindemo \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmindemo \
  --env-file deploy/vps/.env.schadmindemo \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```

## 3) Schadmin 1811
Полный деплой (с удалением БД):
```bash
cd ~/Tarification

docker compose -p schadmin1811 \
  --env-file deploy/vps/.env.schadmin1811 \
  -f deploy/vps/docker-compose.prod.yml \
  down -v

docker compose -p schadmin1811 \
  --env-file deploy/vps/.env.schadmin1811 \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build
```

Деплой с сохранением БД:
```bash
cd ~/Tarification

git pull

docker compose -p schadmin1811 \
  --env-file deploy/vps/.env.schadmin1811 \
  -f deploy/vps/docker-compose.prod.yml \
  up -d --build --no-deps app
```
