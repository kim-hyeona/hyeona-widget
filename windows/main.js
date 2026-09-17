const { app, BrowserWindow, Menu, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

const CALENDAR_DB = '3cd946f45bc2803da355faf7751fb866';
const BLEEDING_DB = 'c07869fbaaa746a593663a5ff92ebd22';
const WISHLIST_DB = '3cd946f45bc280cb875cfe998bc81776';
const MEMO_DB = '3cd946f45bc280dbafaec68c50665447';
const ROUTINE_DB = '9d7cbaca00274f12a6d016927ffb0296';
const BRAINDUMP_PAGE = '3ce946f45bc281ab9e97d2aa17504ea9';
const GRIND_URL = 'https://app.notion.com/p/35b946f45bc280aba379da06addd2eec';
const PACKAGING_URL = 'https://app.notion.com/p/35b946f45bc280d393f0ee3c366a283b';

const settingsPath = () => path.join(app.getPath('userData'), 'settings.json');

function loadSettings() {
  try {
    return JSON.parse(fs.readFileSync(settingsPath(), 'utf8'));
  } catch {
    return {};
  }
}

function saveSettings(next) {
  fs.writeFileSync(settingsPath(), JSON.stringify(next));
}

function mergeSettings(patch) {
  const next = Object.assign({}, loadSettings(), patch);
  saveSettings(next);
  return next;
}

async function notion(token, route, method = 'GET', body) {
  const res = await fetch('https://api.notion.com/v1/' + route, {
    method,
    headers: {
      Authorization: 'Bearer ' + token,
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
  return (prop && prop.title || []).map((x) => x.plain_text).join('') || '';
}

function plainText(prop) {
  return (prop && prop.rich_text || []).map((x) => x.plain_text).join('') || '';
}

function richTextValue(items) {
  return (items || []).map((item) => item.plain_text || '').join('');
}

function richTextChunks(value) {
  const text = String(value == null ? '' : value);
  if (!text) return [];
  const chunks = [];
  for (let index = 0; index < text.length; index += 1900) {
    chunks.push({ type: 'text', text: { content: text.slice(index,index + 1900) } });
  }
  return chunks;
}

async function loadBlockChildren(token, blockId, depth = 0) {
  const blocks = [];
  let cursor;
  do {
    const query = cursor ? '?page_size=100&start_cursor=' + encodeURIComponent(cursor) : '?page_size=100';
    const data = await notion(token, 'blocks/' + blockId + '/children' + query);
    for (const block of data.results || []) {
      const value = block[block.type] || {};
      const normalized = {
        id: block.id,
        type: block.type,
        text: richTextValue(value.rich_text),
        checked: !!value.checked,
        language: value.language || '',
        url: value.external && value.external.url || value.file && value.file.url || '',
        children: [],
      };
      if (block.has_children && depth < 2) {
        normalized.children = await loadBlockChildren(token, block.id, depth + 1);
      }
      blocks.push(normalized);
    }
    cursor = data.has_more ? data.next_cursor : null;
  } while (cursor);
  return blocks;
}

function pageTitle(page) {
  const props = page && page.properties || {};
  for (const value of Object.values(props)) {
    if (value && value.type === 'title') return plainTitle(value);
  }
  return '';
}

function ymd(d) {
  return [
    d.getFullYear(),
    String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0'),
  ].join('-');
}

function twoWeekRange(offsetWeeks = 0) {
  const today = new Date();
  const start = new Date(today);
  start.setHours(12, 0, 0, 0);
  start.setDate(today.getDate() - today.getDay() + (Number(offsetWeeks) || 0) * 14);
  return Array.from({ length: 14 }, (_, index) => {
    const d = new Date(start);
    d.setDate(start.getDate() + index);
    return d;
  });
}

async function safeLoad(errors, label, fn, fallback) {
  try {
    return await fn();
  } catch (error) {
    errors.push(label + ': ' + error.message);
    return fallback;
  }
}

ipcMain.handle('dashboard:load', async (_event, token, offsetWeeks = 0) => {
  const days = twoWeekRange(offsetWeeks);
  const rangeStart = ymd(days[0]);
  const rangeEnd = ymd(days[13]);
  const todayStr = ymd(new Date());
  const monthLabel = String(days[7].getMonth() + 1) + '월';
  const budgetMonth = String(new Date().getMonth() + 1) + '월';
  const errors = [];

  const results = await Promise.all([
    safeLoad(errors, '캘린더', () => notion(token, 'databases/' + CALENDAR_DB + '/query', 'POST', {
      page_size: 100,
      filter: {
        and: [
          { property: '날짜', date: { on_or_after: rangeStart } },
          { property: '날짜', date: { on_or_before: rangeEnd } },
        ],
      },
    }), { results: [] }),
    safeLoad(errors, '가계부', () => notion(token, 'databases/' + BLEEDING_DB + '/query', 'POST', {
      page_size: 100,
      filter: { property: '월', select: { equals: budgetMonth } },
    }), { results: [] }),
    safeLoad(errors, '위시리스트', () => notion(token, 'databases/' + WISHLIST_DB + '/query', 'POST', {
      page_size: 50,
    }), { results: [] }),
    safeLoad(errors, '메모', () => notion(token, 'databases/' + MEMO_DB + '/query', 'POST', {
      page_size: 50,
      sorts: [{ timestamp: 'created_time', direction: 'descending' }],
    }), { results: [] }),
    safeLoad(errors, '평일 루틴', () => notion(token, 'databases/' + ROUTINE_DB + '/query', 'POST', {
      page_size: 50,
    }), { results: [] }),
    safeLoad(errors, '브레인덤프', () => notion(token, 'pages/' + BRAINDUMP_PAGE), { properties: {} }),
  ]);

  const calRes = results[0];
  const bleedRes = results[1];
  const wishRes = results[2];
  const memoRes = results[3];
  const routineRes = results[4];
  const dumpRes = results[5];

  const events = calRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['이름']) || '할 일',
    done: !!(p.properties['완료'] && p.properties['완료'].checkbox),
    date: p.properties['날짜'] && p.properties['날짜'].date ? p.properties['날짜'].date.start : '',
  }));

  const calendarDays = days.map((d) => {
    const date = ymd(d);
    return {
      date,
      day: d.getDate(),
      isToday: date === todayStr,
      events: events.filter((item) => item.date === date),
    };
  });

  let bleedingSum = 0;
  const bleedingItems = bleedRes.results.map((p) => {
    const amount = p.properties['금액(만원)'] && p.properties['금액(만원)'].number || 0;
    bleedingSum += amount;
    return {
      id: p.id,
      title: plainTitle(p.properties['항목']),
      amount,
      memo: plainText(p.properties['메모']),
      month: p.properties['월'] && p.properties['월'].select ? p.properties['월'].select.name : monthLabel,
      done: !!(p.properties['완료'] && p.properties['완료'].checkbox),
    };
  });

  const wishlist = wishRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['이름']),
    status: p.properties['상태'] && p.properties['상태'].status ? p.properties['상태'].status.name : '',
  }));

  const memo = memoRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['제목']),
    createdTime: p.created_time,
  }));

  const routine = routineRes.results.map((p) => ({
    id: p.id,
    title: plainTitle(p.properties['항목']),
    done: !!(p.properties['완료'] && p.properties['완료'].checkbox),
  }));

  const remoteBrainDump = plainText(dumpRes.properties && dumpRes.properties['내용']);
  const settings = loadSettings();
  const brainDump = settings.brainDraftDirty && typeof settings.brainDraft === 'string'
    ? settings.brainDraft
    : remoteBrainDump;

  if (!errors.some((error) => error.startsWith('브레인덤프:')) && !settings.brainDraftDirty && settings.brainDraft !== remoteBrainDump) {
    mergeSettings({ brainDraft: remoteBrainDump });
  }

  async function loadTodayEvents() {
    const visibleToday = events.filter((item) => item.date === todayStr);
    if (calendarDays.some((day) => day.isToday)) return visibleToday;
    const todayRes = await safeLoad(errors, '오늘 일정', () => notion(token, 'databases/' + CALENDAR_DB + '/query', 'POST', {
      page_size: 100,
      filter: { property: '날짜', date: { equals: todayStr } },
    }), { results: [] });
    return todayRes.results.map((p) => ({
      id: p.id,
      title: plainTitle(p.properties['이름']) || '할 일',
      done: !!(p.properties['완료'] && p.properties['완료'].checkbox),
      date: p.properties['날짜'] && p.properties['날짜'].date ? p.properties['날짜'].date.start : '',
    }));
  }

  return {
    monthLabel,
    calendarDays,
    todayEvents: await loadTodayEvents(),
    events,
    bleeding: { sum: bleedingSum, monthLabel: budgetMonth, items: bleedingItems },
    wishlist: {
      active: wishlist.filter((item) => item.status !== '완료').length,
      total: wishlist.length,
      items: wishlist,
    },
    memo,
    routine: {
      items: routine,
      done: routine.filter((item) => item.done).length,
      total: routine.length,
    },
    brainDump,
    links: { grind: GRIND_URL, packaging: PACKAGING_URL },
    errors,
  };
});

ipcMain.handle('weather:load', async (_event, latitude, longitude) => {
  const lat = Number.isFinite(Number(latitude)) ? Number(latitude) : 37.5665;
  const lon = Number.isFinite(Number(longitude)) ? Number(longitude) : 126.9780;
  const params = new URLSearchParams({
    latitude: String(lat),
    longitude: String(lon),
    current: 'temperature_2m,weather_code',
    daily: 'weather_code,temperature_2m_max,temperature_2m_min',
    timezone: 'auto',
    forecast_days: '4',
  });
  const res = await fetch('https://api.open-meteo.com/v1/forecast?' + params.toString());
  if (!res.ok) throw new Error('날씨를 불러오지 못했어요.');
  const data = await res.json();
  return {
    current: {
      temperature: Math.round(data.current.temperature_2m),
      code: data.current.weather_code,
    },
    daily: data.daily.time.map((date, index) => ({
      date,
      code: data.daily.weather_code[index],
      max: Math.round(data.daily.temperature_2m_max[index]),
      min: Math.round(data.daily.temperature_2m_min[index]),
    })).slice(1, 4),
  };
});

ipcMain.handle('notion:dashboard-page', async (_event, token) => {
  const result = await notion(token, 'search', 'POST', {
    query: 'life is bitch',
    filter: { property: 'object', value: 'page' },
    page_size: 50,
  });
  const pages = result.results || [];
  const exact = pages.find((page) => pageTitle(page).trim().toLowerCase() === 'life is bitch');
  const candidate = exact || pages[0];
  if (!candidate || !candidate.url) {
    throw new Error('노션에서 life is bitch 페이지를 찾지 못했어요.');
  }
  await shell.openExternal(candidate.url);
  return candidate.url;
});

ipcMain.handle('task:toggle', async (_event, token, id, done) => {
  await notion(token, 'pages/' + id, 'PATCH', { properties: { 완료: { checkbox: done } } });
  return true;
});

ipcMain.handle('routine:toggle', async (_event, token, id, done) => {
  await notion(token, 'pages/' + id, 'PATCH', { properties: { 완료: { checkbox: done } } });
  return true;
});

ipcMain.handle('braindump:save', async (_event, token, text) => {
  const value = String(text || '');
  if (value.length > 100000) throw new Error('브레인덤프는 100,000자까지 저장할 수 있어요. 초안은 이 PC에 보관돼요.');
  const chunks = [];
  for (let offset = 0; offset < value.length;) {
    let end = Math.min(offset + 1900, value.length);
    if (end < value.length && /[\uD800-\uDBFF]/.test(value[end - 1])) end--;
    chunks.push({ text: { content: value.slice(offset, end) } });
    offset = end;
  }
  await notion(token, 'pages/' + BRAINDUMP_PAGE, 'PATCH', {
    properties: { 내용: { rich_text: chunks } },
  });
  const latest = loadSettings();
  if (latest.brainDraft === value) mergeSettings({ brainDraftDirty: false });
  return true;
});

ipcMain.handle('braindump:draft', (_event, text) => {
  mergeSettings({ brainDraft: String(text || ''), brainDraftDirty: true });
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
  const config = typeConfig(type);
  const props = {};

  if (!partial || payload.title !== undefined) {
    props[config.title] = {
      title: [{ text: { content: String(payload.title || '').slice(0, 500) } }],
    };
  }
  if (payload.done !== undefined && ['calendar', 'bleeding', 'routine'].includes(type)) {
    props['완료'] = { checkbox: !!payload.done };
  }
  if (type === 'calendar' && payload.date !== undefined) {
    props['날짜'] = { date: { start: payload.date } };
  }
  if (type === 'bleeding') {
    if (payload.amount !== undefined) props['금액(만원)'] = { number: Number(payload.amount) || 0 };
    if (payload.month !== undefined) props['월'] = { select: { name: payload.month } };
    if (payload.memo !== undefined) {
      props['메모'] = { rich_text: [{ text: { content: String(payload.memo).slice(0, 1000) } }] };
    }
  }
  if (type === 'wishlist' && payload.status !== undefined) {
    props['상태'] = { status: { name: payload.status } };
  }
  return props;
}

ipcMain.handle('item:create', async (_event, token, type, payload) => {
  const config = typeConfig(type);
  return notion(token, 'pages', 'POST', {
    parent: { database_id: config.db },
    properties: itemProperties(type, payload),
  });
});

ipcMain.handle('item:update', async (_event, token, type, id, payload) => {
  return notion(token, 'pages/' + id, 'PATCH', {
    properties: itemProperties(type, payload, true),
  });
});

ipcMain.handle('item:delete', async (_event, token, id) => {
  return notion(token, 'pages/' + id, 'PATCH', { archived: true });
});

ipcMain.handle('page:detail', async (_event, token, id) => {
  const page = await notion(token, 'pages/' + id);
  return {
    id: page.id,
    title: pageTitle(page) || '상세 내용',
    url: page.url || '',
    blocks: await loadBlockChildren(token, id),
  };
});

ipcMain.handle('page:save-detail', async (_event, token, id, payload = {}) => {
  const editableTypes = new Set([
    'paragraph','heading_1','heading_2','heading_3','bulleted_list_item',
    'numbered_list_item','to_do','quote','callout','code',
  ]);
  const updates = Array.isArray(payload.updates) ? payload.updates : [];
  for (const update of updates) {
    if (!update || !update.id || !editableTypes.has(update.type)) continue;
    const value = { rich_text: richTextChunks(update.text) };
    if (update.type === 'to_do') value.checked = !!update.checked;
    if (update.type === 'code') value.language = update.language || 'plain text';
    await notion(token, 'blocks/' + update.id, 'PATCH', { [update.type]: value });
  }

  const newText = String(payload.newText || '').trim();
  if (newText) {
    const children = newText.split(/\n{2,}/).map((text) => text.trim()).filter(Boolean).map((text) => ({
      object: 'block',
      type: 'paragraph',
      paragraph: { rich_text: richTextChunks(text) },
    }));
    for (let index = 0; index < children.length; index += 100) {
      await notion(token, 'blocks/' + id + '/children', 'PATCH', { children: children.slice(index,index + 100) });
    }
  }
  return { ok: true };
});

ipcMain.handle('link:open', async (_event, url) => shell.openExternal(url));

ipcMain.handle('settings:get', () => loadSettings());

ipcMain.handle('settings:set', (_event, patch) => {
  mergeSettings(patch || {});
  return true;
});

let win;

function createWindow() {
  win = new BrowserWindow({
    width: 1500,
    height: 960,
    minWidth: 1080,
    minHeight: 700,
    title: "hyeona's dashboard",
    autoHideMenuBar: true,
    backgroundColor: '#fefcfb',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      spellcheck: false,
    },
  });

  win.loadFile('index.html');

  const menu = Menu.buildFromTemplate([
    {
      label: '항상 위에 고정',
      type: 'checkbox',
      click: (item) => win.setAlwaysOnTop(item.checked),
    },
    { label: '새로고침', click: () => win.reload() },
    {
      label: '토큰 다시 설정',
      click: () => {
        const settings = loadSettings();
        delete settings.token;
        saveSettings(settings);
        win.reload();
      },
    },
    { type: 'separator' },
    { label: '종료', click: () => app.quit() },
  ]);

  win.webContents.on('context-menu', () => menu.popup());
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
