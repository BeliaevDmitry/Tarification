const summaryUi={kpis:document.getElementById('exit-summary-kpis'),classes:document.getElementById('exit-summary-classes'),
    teachers:document.getElementById('exit-summary-teachers'),refresh:document.getElementById('exit-summary-refresh')};
function summaryYear(){return typeof getStoredAcademicYear==='function'?getStoredAcademicYear():'';}
function summaryEsc(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function loadExitSummary(){
    const year=summaryYear(),url=`/api/exit-orders/summary${year?`?academicYear=${encodeURIComponent(year)}`:''}`;
    const response=await fetch(url),text=await response.text();let data=null;try{data=text?JSON.parse(text):null;}catch{data=null;}
    if(!response.ok)throw new Error(data?.message||data?.error||text||`HTTP ${response.status}`);
    summaryUi.kpis.innerHTML=`<div class="mcko-kpi"><span>Состоявшихся выходов</span><strong>${data.totalEvents||0}</strong></div>
      <div class="mcko-kpi"><span>Посещений детей</span><strong>${data.totalAttended||0}</strong></div>
      <div class="mcko-kpi"><span>Неявок</span><strong>${data.totalAbsent||0}</strong></div>`;
    summaryUi.classes.innerHTML=(data.classes||[]).length?(data.classes||[]).map((item,index)=>`<tr><td>${index+1}</td><td><strong>${summaryEsc(item.className)}</strong></td>
      <td>${summaryEsc(item.buildingCode||'—')}</td><td>${item.events}</td><td>${item.attended}</td><td>${item.absent}</td></tr>`).join(''):
      '<tr><td colspan="6" class="muted">Состоявшихся выходов пока нет.</td></tr>';
    summaryUi.teachers.innerHTML=(data.teachers||[]).length?(data.teachers||[]).map((item,index)=>`<tr><td>${index+1}</td><td><strong>${summaryEsc(item.fullName)}</strong></td>
      <td>${summaryEsc(item.buildingCode||'—')}</td><td>${item.events}</td><td>${item.childrenAccompanied}</td></tr>`).join(''):
      '<tr><td colspan="5" class="muted">Данных о сопровождающих пока нет.</td></tr>';
}
summaryUi.refresh.addEventListener('click',()=>loadExitSummary().catch(error=>window.alert(error.message)));
loadExitSummary().catch(error=>{summaryUi.kpis.innerHTML=`<span class="probe-error">${summaryEsc(error.message)}</span>`;});
