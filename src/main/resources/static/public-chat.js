(() => {
    const messages = document.getElementById('public-chat-messages');
    const form = document.getElementById('public-chat-form');
    const author = document.getElementById('public-chat-author');
    const text = document.getElementById('public-chat-text');
    const status = document.getElementById('public-chat-status');
    let lastSignature = '';

    author.value = localStorage.getItem('publicChatAuthor') || '';

    function formatDate(value) {
        return new Intl.DateTimeFormat('ru-RU', {
            day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
        }).format(new Date(value));
    }

    function render(items) {
        const signature = items.map(item => item.id).join(',');
        if (signature === lastSignature) return;
        lastSignature = signature;
        messages.replaceChildren();
        if (!items.length) {
            const empty = document.createElement('p');
            empty.className = 'muted';
            empty.textContent = 'Сообщений пока нет. Начните разговор.';
            messages.append(empty);
            return;
        }
        items.forEach(item => {
            const box = document.createElement('article');
            box.className = 'public-chat-message';
            const head = document.createElement('div');
            head.className = 'public-chat-message-head';
            const name = document.createElement('span');
            name.className = 'public-chat-message-author';
            name.textContent = item.author;
            const time = document.createElement('time');
            time.dateTime = item.createdAt;
            time.textContent = formatDate(item.createdAt);
            const body = document.createElement('div');
            body.textContent = item.text;
            head.append(name, time);
            box.append(head, body);
            messages.append(box);
        });
        messages.scrollTop = messages.scrollHeight;
    }

    async function loadMessages() {
        const response = await fetch('/api/public/chat/messages', { cache: 'no-store' });
        if (!response.ok) throw new Error('Не удалось загрузить сообщения');
        render(await response.json());
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        status.textContent = '';
        try {
            const response = await fetch('/api/public/chat/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ author: author.value, text: text.value })
            });
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || 'Не удалось отправить сообщение');
            localStorage.setItem('publicChatAuthor', author.value.trim());
            text.value = '';
            await loadMessages();
            text.focus();
        } catch (error) {
            status.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    loadMessages().catch(error => status.textContent = error.message);
    window.setInterval(() => loadMessages().catch(() => {}), 5000);
})();
