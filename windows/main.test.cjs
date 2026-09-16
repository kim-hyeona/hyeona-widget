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
