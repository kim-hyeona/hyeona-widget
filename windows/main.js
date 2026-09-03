const { app, BrowserWindow, Menu, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

// ── 데이터베이스 / 페이지 ID ──────────────────────────────
const CALENDAR_DB = '3cd946f45bc2803da355faf7751fb866';   // 캘린더 (이름/날짜/완료)
const BLEEDING_DB = 'c07869fbaaa746a593663a5ff92ebd22';   // 가계부 (항목/월/금액(만원)/완료)
const WISHLIST_DB = '3cd946f45bc280cb875cfe998bc81776';   // 물결 위시리스트
const MEMO_DB = '3cd946f45bc280dbafaec68c50665447';       // 물결 메모
const ROUTINE_DB = '9d7cbaca00274f12a6d016927ffb0296';    // 오늘의 루틴
const BRAINDUMP_PAGE = '3ce946f45bc281ab9e97d2aa17504ea9'; // 브레인덤프 단일 행
const GRIND_URL = 'https://app.notion.com/p/35b946f45bc280aba379da06addd2eec';
const PACKAGING_URL = 'https://app.notion.com/p/35b946f45bc280d393f0ee3c366a283b';

const settingsPath = () => path.join(app.getPath('userData'), 'settings.json');
function loadSettings() {
  try { return JSON.parse(fs.readFileSync(settingsPath(), 'utf8')); } catch { return {}; }
}
function saveSettings(s) {
  fs.writeFileSync(settingsPath(), JSON.stringify(s));
}

async function notion(token, path_, method = 'GET', body) {
  const res = await fetch(`https://api.notion.com/v1/${path_}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Notion-Version': '2022-06-28',
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Notion 연결에 실패했어요.');
  return data;
}

function plainTitle(prop) {
  return (prop?.title || []).map((x) => x.plain_text).join('') || '';
}
function plainText(prop) {
  return (prop?.rich_text || []).map((x) => x.plain_text).join('') || '';
}

// 2주치 날짜 배열 (이번 주 일요일 ~ 13일 뒤)
function twoWeekRange() {
  const today = new Date();
  const day = today.getDay();
  const start = new Date(today);
  start.setDate(today.getDate() - day);
  const days = [];
  for (let i = 0; i < 14; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    days.push(d);
  }
  return days;
}
function ymd(d) {
  return d.toISOString().slice(0, 10);
}

ipcMain.handle('dashboard:load', async (_e, token) => {
  const days = twoWeekRange();
  const rangeStart = ymd(days[0]);
  const rangeEnd = ymd(days[13]);
  const todayStr = ymd(new Date());
  const monthLabel = `${new Date().getMonth() + 1}월`;
  const errors = [];

  async function safe(label, fn, fallback) {
    try { return await fn(); } catch (e) { errors.push(`${label}: ${e.message}`); return fallback; }
  }

  const calRes = await safe('캘린더', () => notion(token, `databases/${CALENDAR_DB}/query`, 'POST', {
    page_size: 100,
    filter: {
      and: [
        { property: '날짜', date: { on_or_after: rangeStart } },
        { property: '날짜', date: { on_or_before: rangeEnd } },
      ],
    },
  }), { results: [] });

  const bleedRes = await safe('가계부', () => notion(token, `databases/${BLEEDING_DB}/query`, 'POST', {
    page_size: 100,
    filter: { property: '월', select: { equals: monthLabel } },
  }), { results: [] });

  const wishRes = await safe('위시리스트', () => notion(token, `databases/${WISHLIST_DB}/query`, 'POST', { page_size: 50 }), { results: [] });

  const memoRes = await safe('메모', () => notion(token, `databases/${MEMO_DB}/query`, 'POST', {
    page_size: 50,
    sorts: [{ timestamp: 'created_time', direction: 'descending' }],
  }), { results: [] });

  const routineRes = await safe('루틴', () => notion(token, `databases/${ROUTINE_DB}/query`, 'POST', { page_size: 20 }), { results: [] });

  const dumpRes = await safe('브레인덤프', () => notion(token, `pages/${BRAINDUMP_PAGE}`), { properties: {} });

  const events = calRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['이름']) || '할 일',
    done: !!p.properties['완료']?.checkbox,
    date: p.properties['날짜']?.date?.start || '',
  }));

  const calendarDays = days.map((d) => {
    const key = ymd(d);
    const dayEvents = events.filter((e) => e.date === key);
    return {
      date: key,
      day: d.getDate(),
      isToday: key === todayStr,
      hasEvent: dayEvents.length > 0,
      events: dayEvents,
    };
  });
  const todayEvents = events.filter((e) => e.date === todayStr);

  let bleedingSum = 0;
  const bleedingItems = bleedRes.results.map((p) => {
    const amount = p.properties['금액(만원)']?.number || 0;
    bleedingSum += amount;
    return {
      id: p.id,
      title: plainTitle(p.properties['항목']),
      amount,
      memo: plainText(p.properties['메모']),
      month: p.properties['월']?.select?.name || monthLabel,
      done: !!p.properties['완료']?.checkbox,
    };
  });

  const wishlist = wishRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['이름']),
    status: p.properties['상태']?.status?.name || '',
  }));
  const wishlistActive = wishlist.filter((w) => w.status !== '완료').length;

  const memo = memoRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['제목']),
  }));

  const routine = routineRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['항목']),
    done: !!p.properties['완료']?.checkbox,
  }));
  const routineDone = routine.filter((r) => r.done).length;

  const notionBrainDump = plainText(dumpRes.properties['내용']);
  const localSettings = loadSettings();
  const brainDump = typeof localSettings.brainDraft === 'string' ? localSettings.brainDraft : notionBrainDump;

  return {
    monthLabel,
    calendarDays,
    todayEvents,
    events,
    bleeding: { sum: bleedingSum, monthLabel, items: bleedingItems },
    wishlist: { active: wishlistActive, total: wishlist.length, items: wishlist },
    memo,
    routine: { items: routine, done: routineDone, total: routine.length },
    brainDump,
    links: { grind: GRIND_URL, packaging: PACKAGING_URL },
    errors,
  };
});

ipcMain.handle('task:toggle', async (_e, token, id, done) => {
  await notion(token, `pages/${id}`, 'PATCH', { properties: { 완료: { checkbox: done } } });
  return true;
});
ipcMain.handle('routine:toggle', async (_e, token, id, done) => {
  await notion(token, `pages/${id}`, 'PATCH', { properties: { 완료: { checkbox: done } } });
  return true;
});
ipcMain.handle('bleeding:toggle', async (_e, token, id, done) => {
  await notion(token, `pages/${id}`, 'PATCH', { properties: { 완료: { checkbox: done } } });
  return true;
});
ipcMain.handle('braindump:save', async (_e, token, text) => {
  await notion(token, `pages/${BRAINDUMP_PAGE}`, 'PATCH', {
    properties: { 내용: { rich_text: [{ text: { content: text.slice(0, 1900) } }] } },
  });
  return true;
});
ipcMain.handle('braindump:draft', (_e, text) => {
  const s = loadSettings();
  saveSettings({ ...s, brainDraft: String(text).slice(0, 1900) });
  return true;
});

const TYPES = {
  calendar: { db: CALENDAR_DB, title: '이름' },
  bleeding: { db: BLEEDING_DB, title: '항목' },
  wishlist: { db: WISHLIST_DB, title: '이름' },
  memo: { db: MEMO_DB, title: '제목' },
  routine: { db: ROUTINE_DB, title: '항목' },
};
function typeConfig(type) {
  const config = TYPES[type];
  if (!config) throw new Error('지원하지 않는 항목이에요.');
  return config;
}
function itemProperties(type, payload, partial = false) {
  const c = typeConfig(type);
  const props = {};
  if (!partial || payload.title !== undefined) {
    props[c.title] = { title: [{ text: { content: String(payload.title || '').slice(0, 500) } }] };
  }
  if (payload.done !== undefined && ['calendar','bleeding','routine'].includes(type)) {
    props['완료'] = { checkbox: !!payload.done };
  }
  if (type === 'calendar' && payload.date !== undefined) props['날짜'] = { date: { start: payload.date } };
  if (type === 'bleeding') {
    if (payload.amount !== undefined) props['금액(만원)'] = { number: Number(payload.amount) || 0 };
    if (payload.month !== undefined) props['월'] = { select: { name: payload.month } };
    if (payload.memo !== undefined) props['메모'] = { rich_text: [{ text: { content: String(payload.memo).slice(0, 1000) } }] };
  }
  if (type === 'wishlist' && payload.status !== undefined) props['상태'] = { status: { name: payload.status } };
  return props;
}
ipcMain.handle('item:create', async (_e, token, type, payload) => {
  const c = typeConfig(type);
  return notion(token, 'pages', 'POST', { parent: { database_id: c.db }, properties: itemProperties(type, payload) });
});
ipcMain.handle('item:update', async (_e, token, type, id, payload) => {
  return notion(token, `pages/${id}`, 'PATCH', { properties: itemProperties(type, payload, true) });
});
ipcMain.handle('item:delete', async (_e, token, id) => {
  return notion(token, `pages/${id}`, 'PATCH', { archived: true });
});
ipcMain.handle('link:open', async (_e, url) => {
  shell.openExternal(url);
});
ipcMain.handle('settings:get', () => loadSettings());
ipcMain.handle('settings:set', (_e, s) => {
  saveSettings(s);
  return true;
});

let win;
function create() {
  win = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 900,
    minHeight: 600,
    title: "hyeona's dashboard",
    autoHideMenuBar: true,
    backgroundColor: '#ffffff',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
    },
  });
  win.loadFile('index.html');
  const menu = Menu.buildFromTemplate([
    { label: '항상 위에 고정', type: 'checkbox', click: (i) => win.setAlwaysOnTop(i.checked) },
    { label: '새로고침', click: () => win.reload() },
    { label: '토큰 다시 설정', click: () => { saveSettings({}); win.reload(); } },
    { type: 'separator' },
    { label: '종료', click: () => app.quit() },
  ]);
  win.webContents.on('context-menu', () => menu.popup());
}
app.whenReady().then(create);
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
