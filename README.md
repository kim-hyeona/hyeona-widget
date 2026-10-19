# 현아 위젯

물결 대시보드와 Notion 할 일을 보여주는 개인용 위젯입니다.

## Android

1. APK를 설치하고 `현아 위젯` 앱을 엽니다.
2. Notion 내부 통합 시크릿을 앱에 저장합니다.
3. 홈 화면을 길게 눌러 `위젯 → 현아 위젯`을 추가합니다.
4. 오늘 할 일을 확인하거나 동그라미를 눌러 완료 처리합니다.

## Windows

설치 후 앱 화면을 우클릭하고 `항상 위에 고정`을 선택하면 작은 데스크톱 패널처럼 사용할 수 있습니다.

비밀 토큰은 저장소나 설치 파일에 포함되지 않습니다.


## 토큰 저장 보호

Windows 토큰은 Electron 33의 safeStorage(OS 계정의 보안 저장소)로 암호화합니다. 기존 settings.json의 평문 토큰은 다음 실행 시 암호문으로 자동 이전합니다. 암호화가 불가능하면 평문 저장으로 대체하지 않으며 원본 설정은 보존합니다. Windows 계정을 바꾸거나 다른 PC로 설정 파일을 복사하면 재연결이 필요할 수 있습니다.

Android는 Android Keystore의 AES-GCM 키로 토큰을 암호화합니다. 앱과 위젯 및 알림이 같은 저장소를 사용하며 기존 평문 토큰은 읽을 때 이전합니다. 백업을 비활성화하고 토큰 삭제 시 식단과 메모 등 다른 설정은 유지합니다. 기기 키를 사용할 수 없으면 앱에서 다시 연결해야 합니다.

암호화는 디스크에 저장된 키를 보호합니다. 실행 중인 앱이나 같은 OS 계정을 완전히 장악한 공격자에 대한 보호를 보장하지 않습니다. 기존 공개 APK/EXE는 수정되지 않으며 새 빌드를 설치해야 적용됩니다.

검증: `cd windows && npm test`. Android Keystore 동작은 기존 `WidgetRenderInstrumentation` 실행 시 `TokenStoreInstrumentation` 검증도 함께 실행합니다. 실제 기기에서의 마이그레이션 검증 후 배포합니다.

참고: https://www.electronjs.org/docs/latest/api/safe-storage 및 https://developer.android.com/privacy-and-security/keystore
