const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('hyeona', {
  load: (token) => ipcRenderer.invoke('dashboard:load', token),
  toggleTask: (token, id, done) => ipcRenderer.invoke('task:toggle', token, id, done),
  toggleRoutine: (token, id, done) => ipcRenderer.invoke('routine:toggle', token, id, done),
  toggleBleeding: (token, id, done) => ipcRenderer.invoke('bleeding:toggle', token, id, done),
  saveBrainDump: (token, text) => ipcRenderer.invoke('braindump:save', token, text),
  saveBrainDraft: (text) => ipcRenderer.invoke('braindump:draft', text),
  createItem: (token, type, payload) => ipcRenderer.invoke('item:create', token, type, payload),
  updateItem: (token, type, id, payload) => ipcRenderer.invoke('item:update', token, type, id, payload),
  deleteItem: (token, id) => ipcRenderer.invoke('item:delete', token, id),
  openLink: (url) => ipcRenderer.invoke('link:open', url),
  getSettings: () => ipcRenderer.invoke('settings:get'),
  setSettings: (s) => ipcRenderer.invoke('settings:set', s),
});
