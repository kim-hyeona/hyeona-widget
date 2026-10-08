const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function fixture(){
  const handlers={};let settings={};let saved;let finish;
  const context={require:name=>name==='electron'?{
    app:{getPath:()=>'/test',whenReady:()=>({then(){}}),on(){}},
    ipcMain:{handle:(name,fn)=>handlers[name]=fn},shell:{openExternal:async()=>{}}
  }:name==='fs'?{readFileSync:()=>JSON.stringify(settings),writeFileSync:(_,value)=>settings=JSON.parse(value)}:require(name),
  fetch:async(_,options)=>{saved=JSON.parse(options.body);await new Promise(resolve=>finish=resolve);return{ok:true,json:async()=>({})}},URLSearchParams};
  vm.runInNewContext(fs.readFileSync(__dirname+'/main.js','utf8'),context);
  return{handlers,get settings(){return settings},get saved(){return saved},finish:()=>finish()};
}
test('long brain dump is preserved in bounded rich-text chunks',async()=>{
  const f=fixture(),text='가'.repeat(1899)+'😀'+'나'.repeat(4000);
  f.handlers['braindump:draft'](null,text);
  const pending=f.handlers['braindump:save'](null,'test',text);
  const chunks=f.saved.properties.내용.rich_text;
  assert.equal(chunks.map(x=>x.text.content).join(''),text);
  assert.ok(chunks.every(x=>x.text.content.length<=1900));
  f.finish();await pending;assert.equal(f.settings.brainDraftDirty,false);
});
test('saving an older value never clears a newer unsaved draft',async()=>{
  const f=fixture();f.handlers['braindump:draft'](null,'old');
  const pending=f.handlers['braindump:save'](null,'test','old');
  f.handlers['braindump:draft'](null,'new');f.finish();await pending;
  assert.equal(f.settings.brainDraft,'new');assert.equal(f.settings.brainDraftDirty,true);
});

test('meal week reads every page, preserving latest record for duplicate dates',async()=>{
  const handlers={},calls=[];
  const page=(id,date,value)=>({id,properties:{날짜:{date:{start:date}},아침:{rich_text:[{plain_text:value}]}}});
  const replies=[{results:[page('latest','2026-10-09','밥')],has_more:true,next_cursor:'next'},{results:[page('older','2026-10-09','old'),page('other','2026-10-10','빵')],has_more:false}];
  vm.runInNewContext(fs.readFileSync(__dirname+'/main.js','utf8'),{require:name=>name==='electron'?{app:{getPath:()=>'/test',whenReady:()=>({then(){}}),on(){}},ipcMain:{handle:(n,f)=>handlers[n]=f},shell:{}}:require(name),fetch:async(url,options)=>{calls.push(JSON.parse(options.body));return{ok:true,json:async()=>replies.shift()};},URLSearchParams});
  const result=await handlers['care:week'](null,'test','2026-10-05','2026-10-12');
  assert.equal(result['2026-10-09'].breakfast,'밥');assert.equal(result['2026-10-10'].breakfast,'빵');assert.equal(calls[1].start_cursor,'next');assert.equal(calls[0].filter.and[1].date.before,'2026-10-12');
});
