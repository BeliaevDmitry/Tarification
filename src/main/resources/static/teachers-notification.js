const tb=document.getElementById("tb");
const notificationDateInput=document.getElementById("notification-date");
const loadDateInput=document.getElementById("load-date");
function ay(){return window.getStoredAcademicYear?window.getStoredAcademicYear():""}
function defaultLoadDate(){const y=String(ay()||"").split('/')[0];return /^\d{4}$/.test(y)?`${y}-09-01`:new Date().toISOString().slice(0,10);}
function initDates(){if(!notificationDateInput.value) notificationDateInput.value=new Date().toISOString().slice(0,10); if(!loadDateInput.value) loadDateInput.value=defaultLoadDate();}
async function api(u,o={}){const r=await fetch(u,o); if(!r.ok) throw new Error(await r.text()); return r;}
function status(r){return r.generated?(r.changed?"✅ !":"✅"):"❌"}
async function load(){const r=await (await api(`/api/teachers-notification?academicYear=${encodeURIComponent(ay())}&loadDate=${loadDateInput.value}`)).json(); tb.innerHTML=""; r.forEach(x=>{const tr=document.createElement('tr'); tr.innerHTML=`<td>${x.fio}</td><td>${status(x)}</td><td><button data-fio="${x.fio}">Скачать</button></td>`; tb.appendChild(tr);}); tb.querySelectorAll('button[data-fio]').forEach(b=>b.onclick=()=>downloadOne(b.dataset.fio));}
async function downloadOne(fio){const r=await api(`/api/teachers-notification/download/${encodeURIComponent(fio)}?academicYear=${encodeURIComponent(ay())}&loadDate=${loadDateInput.value}&notificationDate=${notificationDateInput.value}`,{method:'POST'}); const blob=await r.blob(); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=`Уведомление_${fio}.docx`; a.click(); await load();}
document.getElementById('all').onclick=async()=>{const r=await api(`/api/teachers-notification/download-all?academicYear=${encodeURIComponent(ay())}&loadDate=${loadDateInput.value}&notificationDate=${notificationDateInput.value}`,{method:'POST'}); const blob=await r.blob(); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=`Уведомления на ${loadDateInput.value}.zip`; a.click(); await load();};
initDates();
notificationDateInput.addEventListener('change',load);
loadDateInput.addEventListener('change',load);
load();
