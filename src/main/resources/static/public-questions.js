(() => {
    const data = window.PUBLIC_QUESTIONS_DATA || { questions: [], firstStageQuestions: [], verifiedAnswers: [] };
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

    const firstStageSection = heading(
        'Вопросы 1 этапа — вопрос и ответ',
        'Ответы восстановлены по отметкам и исправлениям в исходном материале.'
    );
    data.firstStageQuestions.forEach(item => {
        const page = item.page ? ` · страница ${item.page}` : '';
        const title = `${item.number} ${item.question}${page}`.trim();
        firstStageSection.append(card(title, item.answer, 'question-answer', `${title} ${item.answer}`));
    });
    content.append(firstStageSection);

    const verifiedSection = heading(
        'Проверенные ответы — итоговая версия',
        'Подробная проверка с пометками о надёжности и необходимости внутреннего подтверждения.'
    );
    data.verifiedAnswers.forEach(item => {
        const title = `${item.number} ${item.question}`.trim();
        verifiedSection.append(card(title, item.details, 'question-answer', `${title} ${item.details}`));
    });
    content.append(verifiedSection);

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
