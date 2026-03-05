# Tarification

Приложение работает в режиме **API + встроенный сайт**.
Старая часть с автоматической обработкой локальных Excel-файлов удалена.

## Быстрый старт

1. Создайте `.env` из шаблона:

```bash
cp .env.example .env
```

2. Заполните как минимум:
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
