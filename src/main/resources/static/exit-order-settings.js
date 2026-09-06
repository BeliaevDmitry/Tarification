const settingUi = {
    form: document.getElementById('exit-settings-form'), approval: document.getElementById('exit-settings-approval'),
    deputy: document.getElementById('exit-settings-deputy'), preambles: document.getElementById('exit-settings-preambles'),
    eventNames: document.getElementById('exit-settings-event-names'), venues: document.getElementById('exit-settings-venues'),
    addresses: document.getElementById('exit-settings-addresses'), gathering: document.getElementById('exit-settings-gathering'),
    feedback: document.getElementById('exit-settings-feedback'), save: document.getElementById('exit-settings-save')
};
function settingYear() { return typeof getStoredAcademicYear === 'function' ? getStoredAcademicYear() : ''; }
function settingUrl(path) { const year=settingYear(); return year ? `${path}${path.includes('?')?'&':'?'}academicYear=${encodeURIComponent(year)}` : path; }
async function settingApi(path, options={}) {
    const response=await fetch(path,options), text=await response.text(); let body=null;
    try{body=text?JSON.parse(text):null;}catch{body=text;} if(!response.ok)throw new Error(body?.message||body?.error||text||`HTTP ${response.status}`); return body;
}
function lines(value){return String(value||'').split(/\r?\n/).map(item=>item.trim()).filter(Boolean);}
function text(values){return (values||[]).join('\n');}
function esc(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function loadSettings(){
    const [settings,refs]=await Promise.all([settingApi(settingUrl('/api/exit-orders/settings')),settingApi(settingUrl('/api/exit-orders/references'))]);
    settingUi.approval.value=settings.approvalMode||'ORGANIZATIONAL_BUILDING';
    settingUi.deputy.innerHTML=`<option value="">${esc(settings.deputyDirectorName||'Власова Юлия Сергеевна')} — по умолчанию</option>`+
        (refs.teachers||[]).map(item=>`<option value="${item.id}" ${String(item.id)===String(settings.deputyDirectorTeacherId||'')?'selected':''}>${esc(item.fullName)}${item.position?` — ${esc(item.position)}`:''}</option>`).join('');
    const d=settings.dictionaries||{};
    settingUi.preambles.value=text(d.PREAMBLE); settingUi.eventNames.value=text(d.EVENT_NAME);
    settingUi.venues.value=text(d.VENUE); settingUi.addresses.value=text(d.EVENT_ADDRESS); settingUi.gathering.value=text(d.GATHERING_PLACE);
    settingUi.save.disabled=!settings.canEdit;
    if(!settings.canEdit) settingUi.feedback.textContent='Просмотр настроек. Изменять их может директор, заместитель директора или администратор.';
}
settingUi.form.addEventListener('submit',async event=>{
    event.preventDefault();
    try{
        settingUi.feedback.textContent='Сохраняем…';
        await settingApi(settingUrl('/api/exit-orders/settings'),{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({
            approvalMode:settingUi.approval.value,deputyDirectorTeacherId:Number(settingUi.deputy.value)||null,
            dictionaries:{PREAMBLE:lines(settingUi.preambles.value),EVENT_NAME:lines(settingUi.eventNames.value),
                VENUE:lines(settingUi.venues.value),EVENT_ADDRESS:lines(settingUi.addresses.value),GATHERING_PLACE:lines(settingUi.gathering.value)}
        })});
        settingUi.feedback.textContent='Настройки сохранены.';
        await loadSettings();
    }catch(error){settingUi.feedback.textContent=error.message;}
});
loadSettings().catch(error=>settingUi.feedback.textContent=error.message);
