const {app,BrowserWindow,Menu,Tray,nativeImage}=require('electron');
let win;
function create(){win=new BrowserWindow({width:1080,height:720,minWidth:430,minHeight:560,title:'현아 위젯',alwaysOnTop:false,autoHideMenuBar:true,backgroundColor:'#e7f3f8',webPreferences:{contextIsolation:true,sandbox:true}});win.loadURL('https://mulgyeol-dashboard.kha99bbb.chatgpt.site');const menu=Menu.buildFromTemplate([{label:'항상 위에 고정',type:'checkbox',click:i=>win.setAlwaysOnTop(i.checked)},{label:'새로고침',click:()=>win.reload()},{type:'separator'},{label:'종료',click:()=>app.quit()}]);win.webContents.on('context-menu',()=>menu.popup());}
app.whenReady().then(create);app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});

