const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createSettingsStore } = require('./secure-settings.cjs');

function fixture(initial = {}, available = true) {
  let disk = JSON.stringify(initial), temporary;
  const safeStorage = {
    isEncryptionAvailable: () => available,
    encryptString: value => Buffer.from('encrypted:' + value),
    decryptString: value => {
      if (!value.toString().startsWith('encrypted:')) throw Error('Corrupted');
      return value.toString().slice(10);
    },
  };
  const fs = {
    readFileSync: () => disk,
    writeFileSync: (_, value) => { temporary = value; },
    renameSync: () => { disk = temporary; },
  };
  const store = createSettingsStore({ fs, settingsPath: () => '/test/settings.json', safeStorage });
  return { store, safeStorage, fs, disk: () => JSON.parse(disk) };
}
test('legacy token is migrated before use and unrelated data is retained', () => {
  const f = fixture({ token: 'dummy-token', brainDraft: '메모' });
  assert.equal(f.store.load().token, 'dummy-token');
  assert.equal(f.disk().token, undefined);
  assert.equal(f.disk().brainDraft, '메모');
  assert.ok(f.disk().tokenEncrypted);
  assert.equal(f.store.load().token, 'dummy-token');
});
test('saving a token never stores a plaintext field and deletion preserves notes', () => {
  const f = fixture();
  f.store.save({ token: 'dummy-token', brainDraft: '메모' });
  assert.equal(f.disk().token, undefined);
  const settings = f.store.load();
  delete settings.token;
  f.store.save(settings);
  assert.equal(f.disk().tokenEncrypted, undefined);
  assert.equal(f.disk().brainDraft, '메모');
});
test('encryption unavailable does not overwrite legacy configuration', () => {
  const f = fixture({ token: 'legacy', brainDraft: '메모' }, false);
  assert.throws(() => f.store.load(), /안전한/);
  assert.throws(() => f.store.save({ token: 'new' }), /안전한/);
  assert.equal(f.disk().token, 'legacy');
});
test('weak Linux fallback and corrupt encrypted settings fail closed', () => {
  const f = fixture({ token: 'legacy' });
  f.safeStorage.getSelectedStorageBackend = () => 'basic_text';
  assert.throws(() => f.store.load(), /안전한/);
  const corrupt = fixture({ tokenEncrypted: Buffer.from('broken').toString('base64') });
  assert.throws(() => corrupt.store.load(), /열 수 없어요/);
  assert.ok(corrupt.disk().tokenEncrypted);
});
test('write failure preserves old settings', () => {
  const f = fixture({ token: 'legacy' });
  f.fs.writeFileSync = () => { throw Error('Disk full'); };
  assert.throws(() => f.store.load(), /Disk full/);
  assert.equal(f.disk().token, 'legacy');
});
