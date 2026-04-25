function paFoldersApi(path) {
  const scoped = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(path) : path;
  return fetch(scoped).then(async (response) => {
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text ? { message: text } : null; }
    if (!response.ok) throw new Error(body?.message || body?.error || `HTTP ${response.status}`);
    return body;
  });
}

function renderPublicFolders(rows) {
  const container = document.getElementById('pa-public-folders');
  if (!container) return;
  if (!rows.length) {
    container.innerHTML = '<p class="muted">Нет сгенерированных шаблонов</p>';
    return;
  }

  const bySubject = new Map();
  rows.forEach((row) => {
    const subject = row.subjectName || 'Без предмета';
    const parallel = row.parallel || '—';
    const className = row.className || '—';
    if (!bySubject.has(subject)) bySubject.set(subject, new Map());
    const byParallel = bySubject.get(subject);
    if (!byParallel.has(parallel)) byParallel.set(parallel, new Map());
    const byClass = byParallel.get(parallel);
    if (!byClass.has(className)) byClass.set(className, []);
    byClass.get(className).push(row);
  });

  let html = '<details open><summary><strong>Выходные работы</strong></summary>';
  [...bySubject.keys()].sort((a, b) => a.localeCompare(b, 'ru')).forEach((subject) => {
    html += `<details style="margin-left:16px;"><summary><strong>${subject}</strong></summary>`;
    const byParallel = bySubject.get(subject);
    [...byParallel.keys()].sort((a, b) => Number(a) - Number(b)).forEach((parallel) => {
      html += `<details style="margin-left:16px;"><summary>Параллель ${parallel}</summary>`;
      const byClass = byParallel.get(parallel);
      [...byClass.keys()].sort((a, b) => String(a).localeCompare(String(b), 'ru')).forEach((className) => {
        html += `<details style="margin-left:16px;"><summary>Класс ${className}</summary><ul>`;
        byClass.get(className)
          .sort((a, b) => String(a.sourceFileName || '').localeCompare(String(b.sourceFileName || ''), 'ru'))
          .forEach((item) => {
            const created = item.createdAt ? new Date(item.createdAt).toLocaleString('ru-RU') : '';
            html += `<li><button type="button" class="tab-btn" data-download-id="${item.reportVersionId}">${item.sourceFileName || `${subject} — ${className}`}</button> <span class="muted">(${item.level === 'ADVANCED' ? 'углублённый' : 'базовый'}, ${created})</span></li>`;
          });
        html += '</ul></details>';
      });
      html += '</details>';
    });
    html += '</details>';
  });
  html += '</details>';
  container.innerHTML = html;

  container.querySelectorAll('[data-download-id]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.downloadId;
      const raw = `/api/pa/reports/${id}/download`;
      const url = typeof window.withAcademicYear === 'function' ? window.withAcademicYear(raw) : raw;
      window.open(url, '_blank');
    });
  });
}

paFoldersApi('/api/pa/reports/folders?workType=EXIT')
  .then((rows) => renderPublicFolders(rows || []))
  .catch((e) => {
    const container = document.getElementById('pa-public-folders');
    if (container) container.innerHTML = `<p class="muted">Ошибка загрузки: ${e.message}</p>`;
  });
