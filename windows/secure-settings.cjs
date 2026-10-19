'use strict';

function createSettingsStore({ fs, settingsPath, safeStorage }) {
  function requireEncryption() {
    if (!safeStorage?.isEncryptionAvailable() ||
        safeStorage.getSelectedStorageBackend?.() === 'basic_text') {
      throw new Error('안전한 토큰 저장소를 사용할 수 없어요. OS 계정 상태를 확인해 주세요.');
    }
  }
  function save(next) {
    const stored = { ...next };
    delete stored.tokenEncrypted;
    if (stored.token) {
      requireEncryption();
      stored.tokenEncrypted = safeStorage.encryptString(String(stored.token)).toString('base64');
    }
    delete stored.token;
    const target = settingsPath();
    const temporary = target + '.tmp';
    fs.writeFileSync(temporary, JSON.stringify(stored), { encoding: 'utf8', mode: 0o600 });
    fs.renameSync(temporary, target);
  }
  function load() {
    let stored;
    try {
      stored = JSON.parse(fs.readFileSync(settingsPath(), 'utf8'));
    } catch (error) {
      if (error.code === 'ENOENT') return {};
      throw new Error('설정 파일을 읽지 못했어요. 기존 파일은 보존됩니다.');
    }
    if (!stored || typeof stored !== 'object' || Array.isArray(stored)) {
      throw new Error('설정 파일 형식을 확인해 주세요.');
    }
    if (stored.tokenEncrypted) {
      requireEncryption();
      try {
        stored.token = safeStorage.decryptString(Buffer.from(stored.tokenEncrypted, 'base64'));
      } catch {
        throw new Error('이 OS 계정에서 토큰을 열 수 없어요. 원래 계정에서 실행해 주세요.');
      }
      delete stored.tokenEncrypted;
    } else if (stored.token) {
      // Write ciphertext atomically before returning the legacy token.
      save(stored);
    }
    return stored;
  }
  return { load, save };
}

module.exports = { createSettingsStore };
