const tbody = document.getElementById('logs-body');
const filtersForm = document.getElementById('filters');
const exportBtn = document.getElementById('export');
let current = [];

function esc(v){return String(v ?? '').replace(/[&<>"']/g, (m)=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));}

async function loadLogs() {
  const fd = new FormData(filtersForm);
  const query = new URLSearchParams();
  for (const [k, v] of fd.entries()) if (v) query.set(k, v);
  const res = await fetch(`/api/admin/audit-logs?${query.toString()}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  current = data.content || [];
  tbody.innerHTML = current.map((row) => `
    <tr>
      <td>${esc(row.createdAt)}</td>
      <td>${esc(row.fullName || row.username || '-')}</td>
      <td>${esc(row.actionType)}</td>
      <td>${esc(row.entityType)}</td>
      <td>${esc(row.ip)}</td>
      <td>${row.success ? 'OK' : 'Ошибка'} (${esc(row.statusCode)})</td>
      <td>${esc(row.userAgent || '-')}</td>
    </tr>
  `).join('');
}

filtersForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  await loadLogs();
});

exportBtn.addEventListener('click', () => {
  const header = ['createdAt','username','fullName','actionType','entityType','details','ip','statusCode','success','userAgent'];
  const rows = [header.join(',')].concat(current.map((row) => header.map((k) => `"${String(row[k] ?? '').replace(/"/g, '""')}"`).join(',')));
  const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'audit-logs.csv';
  link.click();
  URL.revokeObjectURL(link.href);
});

loadLogs().catch(console.error);
