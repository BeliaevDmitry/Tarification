# README2 — пошаговый деплой на VPS для новичка

Если вы делаете это **первый раз**, идите **строго по шагам**.  
Ниже инструкция именно под этот проект и под файлы, которые уже есть в репозитории:
- `deploy/vps/docker-compose.prod.yml`
- `deploy/vps/.env.production.example`
- `deploy/vps/deploy.sh`

---

## Что у вас должно быть заранее

Перед началом подготовьте:

1. **VPS сервер**
   - например: Timeweb, Hetzner, Selectel, DigitalOcean, Vultr и т.д.
   - желательно **Ubuntu 22.04** или **Ubuntu 24.04**
   - минимально: **2 GB RAM**, **1 vCPU**, **20+ GB disk**

2. **Домен**
   - например: `example.ru`
   - или поддомен: `tarif.example.ru`

3. **Доступ к серверу по SSH**
   - у вас должны быть:
     - IP сервера
     - пользователь, обычно `root`
     - пароль или SSH-ключ

4. **Git-репозиторий проекта**
   - чтобы на сервере можно было выполнить `git clone ...`

5. **Данные для почты SMTP**
   - `SMTP_HOST`
   - `SMTP_PORT`
   - `SMTP_USERNAME`
   - `SMTP_PASSWORD`

---

## Что мы будем делать

План такой:

1. Подключимся к серверу.
2. Установим Docker.
3. Привяжем домен к серверу.
4. Скачаем проект на VPS.
5. Создадим production `.env` файл.
6. Запустим контейнеры.
7. Проверим, что сайт открылся по HTTPS.

---

## Шаг 1. Подключитесь к серверу по SSH

### Если у вас Mac/Linux

Откройте Terminal и выполните:

```bash
ssh root@IP_ВАШЕГО_СЕРВЕРА
```

Пример:

```bash
ssh root@203.0.113.10
```

### Если у вас Windows

Варианты:
- PowerShell с командой `ssh`
- Windows Terminal
- PuTTY

Если используете встроенный `ssh`, команда такая же:

```bash
ssh root@203.0.113.10
```

Если сервер спросит:

```text
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

Введите:

```text
yes
```

---

## Шаг 2. Обновите сервер

После входа на VPS выполните:

```bash
apt update && apt upgrade -y
```

Если система просит перезагрузку, выполните:

```bash
reboot
```

Потом подключитесь снова по SSH.

---

## Шаг 3. Установите Docker

Самый простой способ:

```bash
curl -fsSL https://get.docker.com | sh
```

Проверьте, что Docker установился:

```bash
docker --version
```

Проверьте Compose plugin:

```bash
docker compose version
```

Если видите версии — всё нормально.

---

## Шаг 4. Откройте порты на сервере

Для HTTPS нужны порты:
- `80`
- `443`

Если у вас включён UFW, выполните:

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
ufw status
```

Если firewall настраивается в панели VPS-провайдера — откройте там те же порты.

---

## Шаг 5. Привяжите домен к VPS

У регистратора домена или в DNS-панели создайте запись:

### Вариант A: поддомен

```text
Тип: A
Имя: tarif
Значение: IP_ВАШЕГО_СЕРВЕРА
```

Тогда сайт будет открываться, например, по адресу:

```text
https://tarif.example.ru
```

### Вариант B: корневой домен

```text
Тип: A
Имя: @
Значение: IP_ВАШЕГО_СЕРВЕРА
```

Проверить DNS можно командой:

```bash
ping ваш-домен.ru
```

Или:

```bash
getent hosts ваш-домен.ru
```

**Важно:** домен должен указывать на ваш VPS **до запуска HTTPS**, иначе сертификат может не выпуститься.

---

## Шаг 6. Установите Git

Если его нет:

```bash
apt install -y git
```

Проверьте:

```bash
git --version
```

---

## Шаг 7. Скачайте проект на сервер

Перейдите в удобную папку:

```bash
cd /opt
```

Склонируйте проект:

```bash
git clone <URL_ВАШЕГО_РЕПО>
```

Пример:

```bash
git clone https://github.com/your-org/Tarification.git
```

Перейдите в проект:

```bash
cd Tarification
```

Проверьте, что внутри есть нужные файлы:

```bash
ls deploy/vps
```

Вы должны увидеть примерно:
- `docker-compose.prod.yml`
- `deploy.sh`
- `Caddyfile`
- `.env.production.example`

---

## Шаг 8. Создайте production env-файл

Скопируйте шаблон:

```bash
cp deploy/vps/.env.production.example deploy/vps/.env.production
```

Откройте файл редактором `nano`:

```bash
nano deploy/vps/.env.production
```

Вы увидите что-то вроде этого:

```env
APP_DOMAIN=tarification.example.com
POSTGRES_DB=tariffication_db
POSTGRES_USER=tarif_user
POSTGRES_PASSWORD=change_me_postgres_password
DB_PASSWORD=change_me_postgres_password
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=change_me_admin_password
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your.mail@example.com
SMTP_PASSWORD=change_me_smtp_password
```

### Что нужно поменять обязательно

#### 1. Домен

```env
APP_DOMAIN=tarif.example.ru
```

#### 2. Пароль к базе

```env
POSTGRES_PASSWORD=ОЧЕНЬ_СЛОЖНЫЙ_ПАРОЛЬ
DB_PASSWORD=ОЧЕНЬ_СЛОЖНЫЙ_ПАРОЛЬ
```

Лучше использовать **одинаковое значение** в обоих полях.

#### 3. Логин и пароль администратора

```env
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=СЛОЖНЫЙ_ПАРОЛЬ_АДМИНА
```

#### 4. SMTP

Заполните ваши реальные данные почты:

```env
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-real-email@example.com
SMTP_PASSWORD=your-real-password-or-app-password
```

### Как сохранить файл в `nano`

1. Нажмите `Ctrl + O`
2. Нажмите `Enter`
3. Нажмите `Ctrl + X`

---

## Шаг 9. Запустите проект

Находясь в корне проекта, выполните:

```bash
./deploy/vps/deploy.sh
```

Эта команда:
- соберёт Docker image приложения
- поднимет PostgreSQL
- поднимет Java-приложение
- поднимет Caddy
- автоматически попробует выпустить HTTPS сертификат

Первый запуск может занять несколько минут.

---

## Шаг 10. Проверьте контейнеры

Посмотрите статус:

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml ps
```

Нужно, чтобы контейнеры были в состоянии примерно:
- `Up`
- `healthy` для postgres

---

## Шаг 11. Посмотрите логи, если что-то не работает

### Логи приложения

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml logs -f app
```

### Логи reverse proxy

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml logs -f caddy
```

### Логи базы

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml logs -f postgres
```

Чтобы выйти из просмотра логов, нажмите:

```text
Ctrl + C
```

---

## Шаг 12. Откройте сайт в браузере

Если всё сделано правильно, откройте:

```text
https://ВАШ_ДОМЕН/load.html
```

Пример:

```text
https://tarif.example.ru/load.html
```

Также можно попробовать:
- `https://ВАШ_ДОМЕН/teachers.html`
- `https://ВАШ_ДОМЕН/buildings.html`
- `https://ВАШ_ДОМЕН/classes.html`
- `https://ВАШ_ДОМЕН/curriculum.html`

---

## Шаг 13. Как обновлять проект потом

Когда будут новые изменения в репозитории:

```bash
cd /opt/Tarification
git pull
./deploy/vps/deploy.sh
```

---

## Шаг 14. Как перезапустить проект

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml restart
```

---

## Шаг 15. Как остановить проект

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml down
```

---

## Шаг 16. Как сделать backup базы

### Создать backup

```bash
docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml exec postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup.sql
```

### Восстановить backup

```bash
cat backup.sql | docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

---

## Если сайт не открылся — что проверить

Проверьте по порядку:

1. **Домен точно указывает на IP сервера?**
   - `getent hosts ваш-домен.ru`

2. **Открыты порты 80 и 443?**
   - firewall на VPS
   - security group / cloud firewall

3. **Контейнеры вообще запущены?**
   - `docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml ps`

4. **Есть ошибки в логах Caddy?**
   - `docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml logs -f caddy`

5. **Есть ошибки в логах app?**
   - `docker compose --env-file deploy/vps/.env.production -f deploy/vps/docker-compose.prod.yml logs -f app`

6. **Правильно ли заполнен файл `.env.production`?**
   - особенно `APP_DOMAIN`, `SMTP_*`, `POSTGRES_PASSWORD`, `DB_PASSWORD`

---

## Самый короткий сценарий, если совсем кратко

Вот минимальный набор команд на сервере:

```bash
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
apt install -y git
cd /opt
git clone <URL_ВАШЕГО_РЕПО>
cd Tarification
cp deploy/vps/.env.production.example deploy/vps/.env.production
nano deploy/vps/.env.production
./deploy/vps/deploy.sh
```

Потом открыть:

```text
https://ВАШ_ДОМЕН/load.html
```

---

## Что я советую вам сделать прямо сейчас

Если вы реально делаете это впервые, идите так:

1. Купите/подготовьте VPS.
2. Привяжите домен к IP.
3. Подключитесь по SSH.
4. Выполните команды из этого файла **по порядку**.
5. Если что-то упадёт — пришлите мне:
   - вывод команды `docker compose ... ps`
   - логи `app`
   - логи `caddy`

И я помогу вам дойти до рабочего деплоя.
