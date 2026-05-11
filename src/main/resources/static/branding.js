(async function initBranding(){
  const defaults = {
    appTitle: 'ГБОУ школа',
    loginTitle: 'Вход в систему',
    welcomeText: 'Выберите рабочий контур системы.',
    crestUrl: '/school-crest.png',
    fallbackCrestUrl: '/school-crest.png'
  };

  async function loadBranding(){
    try {
      const r = await fetch('/api/public/branding');
      if (!r.ok) return defaults;
      return {...defaults, ...(await r.json())};
    } catch { return defaults; }
  }

  function setFavicon(url){
    const favicon = document.querySelector('link[rel="icon"]');
    if (favicon) favicon.href = url;
  }

  function setText(sel, value){
    const el = document.querySelector(sel);
    if (el && value) el.textContent = value;
  }

  const b = await loadBranding();
  document.title = (document.body.classList.contains('login-page') ? b.loginTitle : b.appTitle) || document.title;
  setFavicon(b.crestUrl);

  const crestImg = document.querySelector('.login-crest');
  if (crestImg) {
    crestImg.src = b.crestUrl;
    crestImg.onerror = () => { crestImg.src = b.fallbackCrestUrl; setFavicon(b.fallbackCrestUrl); };
  }

  setText('[data-branding-welcome]', b.welcomeText);
  setText('[data-branding-login-title]', b.loginTitle);
})();
