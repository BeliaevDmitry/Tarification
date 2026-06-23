(() => {
    const data = window.PUBLIC_QUESTIONS_DATA || { questions: [], recognizedPages: [] };
    const content = document.getElementById('questions-content');
    const search = document.getElementById('questions-search');
    const count = document.getElementById('questions-count');

    function card(title, body, bodyClass, searchText) {
        const details = document.createElement('details');
        details.className = 'question-card';
        details.dataset.search = searchText.toLocaleLowerCase('ru-RU');
        const summary = document.createElement('summary');
        summary.textContent = title;
        const answer = document.createElement('div');
        answer.className = bodyClass;
        answer.textContent = body;
        details.append(summary, answer);
        return details;
    }

    function heading(title, note) {
        const section = document.createElement('section');
        section.className = 'questions-source';
        const h2 = document.createElement('h2');
        h2.textContent = title;
        section.append(h2);
        if (note) {
            const p = document.createElement('p');
            p.className = 'muted';
            p.textContent = note;
            section.append(p);
        }
        return section;
    }

    const answersSection = heading('Аттестация — вопросы с правильными ответами');
    data.questions.forEach(item => {
        const title = `${item.number} ${item.question}`.trim();
        const answer = item.answer || 'В исходном файле правильный ответ не указан.';
        answersSection.append(card(title, answer, 'question-answer', `${title} ${answer}`));
    });
    content.append(answersSection);

    const recognizedSection = heading(
        'Вопросы 1 этапа — распознанный текст',
        'Текст и отметки вариантов показаны как в распознанном документе. Искажённые OCR отметки не трактуются автоматически.'
    );
    data.recognizedPages.forEach(item => {
        recognizedSection.append(card(item.title, item.text, 'question-source-text', `${item.title} ${item.text}`));
    });
    content.append(recognizedSection);

    function filter() {
        const query = search.value.trim().toLocaleLowerCase('ru-RU');
        const cards = [...content.querySelectorAll('.question-card')];
        let visible = 0;
        cards.forEach(item => {
            const show = !query || item.dataset.search.includes(query);
            item.hidden = !show;
            if (show) visible++;
        });
        content.querySelectorAll('.questions-source').forEach(section => {
            section.hidden = !section.querySelector('.question-card:not([hidden])');
        });
        count.textContent = `Показано: ${visible} из ${cards.length}`;
    }

    search.addEventListener('input', filter);
    filter();
})();
