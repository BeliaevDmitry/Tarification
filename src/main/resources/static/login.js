const ui = {
    form: document.getElementById('login-form'),
    username: document.getElementById('login-username'),
    password: document.getElementById('login-password'),
    result: document.getElementById('login-result')
};

async function api(path, options = {}) {
    const response = await fetch(path, options);
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = text ? { message: text } : null;
    }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
}

function print(value) {
    ui.result.textContent = JSON.stringify(value, null, 2);
}

ui.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
        await api('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: ui.username.value,
                password: ui.password.value
            })
        });
        window.location.href = '/load.html';
    } catch (error) {
        print({ error: error.message });
    }
});
