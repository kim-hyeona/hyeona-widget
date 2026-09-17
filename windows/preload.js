const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('hyeona', {
  load: (token, offsetWeeks = 0) => ipcRenderer.invoke('dashboard:load', token, offsetWeeks),
  loadWeather: (latitude, longitude) => ipcRenderer.invoke('weather:load', latitude, longitude),
  openNotionDashboard: (token) => ipcRenderer.invoke('notion:dashboard-page', token),
  toggleTask: (token, id, done) => ipcRenderer.invoke('task:toggle', token, id, done),
  toggleRoutine: (token, id, done) => ipcRenderer.invoke('routine:toggle', token, id, done),
  saveBrainDump: (token, text) => ipcRenderer.invoke('braindump:save', token, text),
  saveBrainDraft: (text) => ipcRenderer.invoke('braindump:draft', text),
  createItem: (token, type, payload) => ipcRenderer.invoke('item:create', token, type, payload),
  updateItem: (token, type, id, payload) => ipcRenderer.invoke('item:update', token, type, id, payload),
  deleteItem: (token, id) => ipcRenderer.invoke('item:delete', token, id),
  loadPageDetail: (token, id) => ipcRenderer.invoke('page:detail', token, id),
  openLink: (url) => ipcRenderer.invoke('link:open', url),
  getSettings: () => ipcRenderer.invoke('settings:get'),
  setSettings: (settings) => ipcRenderer.invoke('settings:set', settings),
});
