// Run with Node and Playwright on NODE_PATH. All API responses use synthetic data.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../main/resources/static');
const output = path.resolve(__dirname, '../../../target/master-fot-ui');

(async () => {
    const browser = await chromium.launch({channel:'msedge', headless:true});
    try {
        fs.mkdirSync(output,{recursive:true});
        let editable = true, importCalls = 0, decisionCalls = 0;
        const finding = (type, extra={}) => ({key:type,type,building:'СП1',className:'7-А',subject:'Алгебра',teacher:'Иванов Иван Иванович',expected:'3 ч.',actual:'2 ч.',detail:'Обязательная часть. Строка Excel: 7.',mappingType:'',mappingSource:'',...extra});
        const issue = (id,type,status,extra={}) => ({id,version:0,finding:finding(type),status,comment:'',archived:false,firstBatchId:1,lastBatchId:1,...extra});
        const data = {batches:[{id:1,date:'2026-09-05',filename:'Тестовая выгрузка.xlsx',rows:3,findings:3,complete:false}],issues:[
            issue('plan','PLAN','OPEN'), issue('vacancy','MCKO_VACANCY','EXPECTED'),
            issue('mapping','MAPPING','OPEN',{finding:finding('MAPPING',{mappingType:'SUBJECT',mappingSource:'Математика (Алгебра)'})}),
            issue('archived','LOAD','FIXED',{archived:true,archivedBatchId:1,comment:'Проверено'})
        ]};
        const options = {groups:[],subjects:[{id:'id:1',label:'Алгебра'},{id:'__ABSENT__',label:'Отсутствует в системе'}],teachers:[],mappings:[]};
        const context = await browser.newContext({viewport:{width:1440,height:1000}});
        const errors=[];
        await context.route('**/*', async route => {
            const url = new URL(route.request().url()), p = url.pathname;
            const json = value => route.fulfill({json:value});
            if (p === '/api/auth/me') return json({id:1,username:'test',fullName:'Тестовый методист',role:'METHODIST',admin:false,canView:true,canEdit:editable,tabPermissions:[{tab:'LOAD_MASTER_FOT',canView:true,canEdit:editable,canImport:editable},{tab:'LOAD',canView:true,canEdit:false}]});
            if (p === '/api/academic-years') return json([{code:'2026/2027'}]);
            if (p === '/api/academic-years/active') return json({active:'2026/2027'});
            if (p === '/api/public/branding') return json({schoolName:'Тестовая школа'});
            if (p.startsWith('/api/master-fot')) {
                assert.equal(url.searchParams.get('academicYear'),'2026/2027');
                if (p.endsWith('/options')) return json(options);
                if (p.endsWith('/mappings')) {options.mappings=[route.request().postDataJSON()]; return json(null);}
                if (p.endsWith('/import')) {importCalls++; return json(data.batches[0]);}
                if (p.includes('/issues/')) {
                    decisionCalls++; const row=data.issues.find(r=>r.id===p.split('/').pop());
                    Object.assign(row,route.request().postDataJSON()); row.version++; return json(row);
                }
                if (p.includes('/batches/')) return json([data.issues[0].finding]);
                return json(data);
            }
            if (p.startsWith('/api/')) throw new Error(`Unexpected API ${p}`);
            const target=path.resolve(root,p.slice(1));
            if (!target.startsWith(root+path.sep) || !fs.existsSync(target)) return route.fulfill({status:404,body:''});
            const mime={'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png'};
            return route.fulfill({body:fs.readFileSync(target),contentType:mime[path.extname(target)]||'application/octet-stream'});
        });
        const page = await context.newPage(); page.on('pageerror',e=>errors.push(e.message));
        await page.goto('https://fot.test/master-fot.html');
        await page.locator('#fot-summary').filter({hasText:'Итераций: 1'}).waitFor();
        assert.equal(await page.locator('#fot-issues tr').count(),3);
        assert.equal(await page.locator('#fot-import-controls').isVisible(),true);
        assert.equal(await page.locator('#fot-completeness').isVisible(),true);
        await page.locator('[data-id="plan"] [data-decision]').selectOption('FIXED');
        await page.locator('[data-id="plan"] [data-comment]').fill('Исправлено в ФОТ');
        await page.locator('[data-id="plan"] [data-save]').click();
        await page.locator('#fot-summary').filter({hasText:'ждут проверки: 1'}).waitFor();
        assert.equal(decisionCalls,1);
        await page.locator('[data-id="mapping"] [data-map]').click();
        await page.locator('#fot-dialog[open]').waitFor();
        await page.locator('#fot-map-target').selectOption('id:1');
        await page.locator('#fot-map-save').click();
        await page.locator('#fot-map-message').filter({hasText:'Сохранено'}).waitFor();
        assert.equal(options.mappings[0].target,'id:1');
        await page.locator('#fot-dialog-close').click();
        await page.locator('#fot-file').setInputFiles({name:'test.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('mock')});
        await page.locator('#fot-upload').click();
        await page.locator('#fot-message').filter({hasText:'Сверка № 1 сохранена'}).waitFor();
        assert.equal(importCalls,1);
        await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
        await page.locator('#fot-status').selectOption('archive');
        assert.equal(await page.locator('#fot-issues tr').count(),1);
        assert.match(await page.locator('#fot-issues').innerText(),/Проверено/);
        await page.locator('#fot-batch').selectOption('1');
        await page.getByText('Состояние на момент сверки',{exact:true}).waitFor();
        assert.equal(await page.locator('#fot-issues [data-save]').count(),0);
        await page.locator('#fot-batch').selectOption('');
        await page.locator('#fot-status').selectOption('active');
        await page.setViewportSize({width:390,height:844});
        await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1),true);
        editable=false; await page.reload();
        await page.locator('#fot-summary').filter({hasText:'Итераций: 1'}).waitFor();
        assert.equal(await page.locator('#fot-import-controls').isVisible(),false);
        assert.equal(await page.locator('#fot-mappings').isVisible(),false);
        assert.equal(await page.locator('#fot-issues [data-save]').count(),0);
        assert.deepEqual(errors,[]);
        console.log('PASS: upload, decisions, mappings, history, archive, readonly access, mobile layout; no browser errors.');
    } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
