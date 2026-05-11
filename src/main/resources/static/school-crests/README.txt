Папка для гербов школ (без бинарников в репозитории).

В PR храним только этот TXT-файл с инструкцией.
Сами PNG-файлы добавляйте на сервер/в образ на этапе деплоя.

Правило подбора герба по SCHOOL_CODE:
1) /school-crests/crest-<school_code>.png
2) если файла нет — fallback на /school-crest.png

Как называть файлы:
- SCHOOL_CODE=7     -> crest-7.png
- SCHOOL_CODE=1811  -> crest-1811.png
- SCHOOL_CODE=demo  -> crest-demo.png

Требования к имени:
- только lowercase, цифры и дефис;
- расширение .png.
