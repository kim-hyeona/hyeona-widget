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


function calendarFixture(reply, settings = {}) {
  const handlers={},calls=[];
  const context={require:name=>name==='electron'?{app:{getPath:()=>'/test',whenReady:()=>({then(){}}),on(){}},ipcMain:{handle:(n,f)=>handlers[n]=f},shell:{}}:name==='fs'?{readFileSync:()=>JSON.stringify(settings),writeFileSync:(_,value)=>Object.assign(settings,JSON.parse(value))}:require(name),fetch:async(url,options)=>{const body=options.body?JSON.parse(options.body):null;calls.push({url,body});return{ok:true,json:async()=>reply(url,body)}},URLSearchParams};
  vm.runInNewContext(fs.readFileSync(__dirname+'/main.js','utf8'),context);
  return {handlers,calls,context};
}
test('calendar includes timed and all-day events, reads color and subsequent pages',async()=>{
  const f=calendarFixture((url,body)=>{
    if(!url.includes('3cd946f45bc2803da355faf7751fb866/query'))return {results:[],properties:{}};
    const day=body.filter.and[0].date.on_or_after.slice(0,10);
    const event=(id,start)=>({id,properties:{이름:{title:[{plain_text:'라포즈 알바'}]},날짜:{date:{start}},'일정 색상':{select:{name:'핑크'}}}});
    return body.start_cursor?{results:[event('all-day',day)],has_more:false}:{results:[event('timed',day+'T10:30:00+09:00')],has_more:true,next_cursor:'page2'};
  });
  const data=await f.handlers['dashboard:load'](null,'test',0);
  assert.equal(data.calendarDays[0].events.length,2);
  assert.equal(data.calendarDays[0].events[0].color,'핑크');
  assert.equal(vm.runInNewContext("calendarDate('2026-11-06T16:00:00Z')",f.context),'2026-11-07');
});
test('calendar edits preserve timed start and end when only title changes',async()=>{
  const f=calendarFixture(()=>({properties:{날짜:{date:{start:'2026-11-07T10:30:00+09:00',end:'2026-11-07T20:30:00+09:00'}}}}));
  await f.handlers['item:update'](null,'test','calendar','event',{title:'new',date:'2026-11-07'});
  assert.equal(f.calls.at(-1).body.properties.날짜,undefined);
});
test('moving calendar date retains duration and time of day',async()=>{
  const f=calendarFixture(()=>({properties:{날짜:{date:{start:'2026-11-07T10:30:00+09:00',end:'2026-11-07T20:30:00+09:00'}}}}));
  await f.handlers['item:update'](null,'test','calendar','event',{date:'2026-11-08'});
  const date=f.calls.at(-1).body.properties.날짜.date;
  assert.equal(date.start,'2026-11-08T01:30:00.000Z');assert.equal(date.end,'2026-11-08T11:30:00.000Z');
});

test('Today uses a separate today query even when calendar navigates away',async()=>{
  const f=calendarFixture((url,body)=>{
    if(!url.includes('3cd946f45bc2803da355faf7751fb866/query'))return {results:[],properties:{}};
    const date=body.filter.and[0].date.on_or_after.slice(0,10);
    return {results:[{id:date,properties:{이름:{type:'title',title:[{plain_text:date}]},날짜:{date:{start:date}}}},{id:'wrong-day',properties:{날짜:{date:{start:'2000-01-01'}}}}]};
  });
  const result=await f.handlers['dashboard:load'](null,'test',4);
  const today=vm.runInNewContext('calendarDate(new Date().toISOString())',f.context);
  assert.equal(result.todayEvents.length,1);assert.equal(result.todayEvents[0].date,today);
  assert.notEqual(result.calendarDays[0].date,today);
});
test('routines reset once at Korean midnight and retain same-day progress',async()=>{
  const settings={routineDate:'2026-11-04'};
  let done=true;
  const f=calendarFixture((url,body)=>{
    if(url.endsWith('/pages/routine')){done=body.properties.완료.checkbox;return {};}
    return {results:[{id:'routine',properties:{완료:{checkbox:done}}}]};
  },settings);
  let clock='2026-11-04T14:59:00Z';
  f.context.Date=class extends Date{constructor(...args){super(...(args.length?args:[clock]));}};
  await vm.runInNewContext("dailyRoutines('test')",f.context);assert.equal(done,true);
  clock='2026-11-04T15:00:00Z';
  await vm.runInNewContext("dailyRoutines('test')",f.context);assert.equal(done,false);assert.equal(settings.routineDate,'2026-11-05');
  done=true;
  await vm.runInNewContext("dailyRoutines('test')",f.context);assert.equal(done,true);
  assert.equal(f.calls.filter(x=>x.url.endsWith('/pages/routine')).length,1);
});
test('failed routine reset remains retryable',async()=>{
  const settings={routineDate:'2026-01-01'};let fail=true;
  const f=calendarFixture((url)=>{
    if(url.endsWith('/pages/routine')){if(fail)throw new Error('offline');return {};}
    return {results:[{id:'routine',properties:{완료:{checkbox:true}}}]};
  },settings);
  await assert.rejects(vm.runInNewContext("dailyRoutines('test')",f.context));assert.equal(settings.routineDate,'2026-01-01');
  fail=false;await vm.runInNewContext("dailyRoutines('test')",f.context);assert.notEqual(settings.routineDate,'2026-01-01');
});
test('calendar palette stays pale and supports manual overrides',()=>{
  const html=fs.readFileSync(__dirname+'/index.html','utf8');
  const code=html.slice(html.indexOf('const calendarColors ='),html.indexOf('function renderCalendar(data)'));
  const context={};vm.runInNewContext(code,context);
  assert.equal(vm.runInNewContext("eventPalette({title:'라포즈 알바'})[0]",context),'#FCF0EB');
  assert.equal(vm.runInNewContext("eventPalette({title:'카미야'})[0]",context),'#FCF0F4');
  assert.equal(vm.runInNewContext("eventPalette({title:'은비랑 팝업'})[0]",context),'#EDF5FA');
  assert.equal(vm.runInNewContext("eventPalette({title:'라포즈 알바',color:'보라'})[0]",context),'#F3EFF9');
});
