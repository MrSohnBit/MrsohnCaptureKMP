## MrSohn Capture

Compose Desktop 기반으로 만든 안드로이드 스크린샷 캡처 도구입니다. 연결된 Android 기기를 감지하고, ADB를 통해 스크린샷을 저장한 뒤 앱 안에서 바로 미리보기·이동·삭제·편집할 수 있습니다.

### 주요 기능

- 연결된 Android 기기 목록 자동 감지
- 선택한 기기 화면을 PNG 파일로 캡처
- 저장된 이미지 썸네일 목록 및 큰 미리보기 제공
- 썸네일을 생성일 기준 내림차순으로 정렬
- 좌우 방향키로 이전/다음 이미지 탐색
- 현재 이미지 삭제 및 클립보드 복사
- 외부 편집기에서 이미지 수정 후 앱 포커스 복귀 시 자동 새로고침
- ADB 경로와 저장 경로를 앱 설정으로 저장

### 기술 스택

- Kotlin Multiplatform
- Compose Multiplatform Desktop
- Kotlin Coroutines
- Java AWT / ImageIO
- Android Debug Bridge (`adb`)

### 실행 환경

- macOS, Windows, Linux 데스크톱 환경
- Java/JDK 17 이상 권장
- Android SDK의 `adb`가 설치되어 있거나, 앱 설정에서 `adb` 실행 파일 경로 지정 가능
- USB 디버깅이 활성화된 Android 기기

### 실행 방법

프로젝트 루트에서 아래 명령어를 실행합니다.

```bash
./gradlew :app:run
```

앱이 실행되면:

1. 필요 시 설정에서 `adb` 경로를 지정합니다.
2. 스크린샷 저장 폴더를 원하는 위치로 설정합니다.
3. Android 기기를 연결하고 USB 디버깅을 허용합니다.
4. 기기를 선택한 뒤 캡처를 실행합니다.

### 기본 동작

- 기본 저장 폴더: `~/Pictures/MrSohnCapture`
- 메인 윈도우 설정 및 경로 설정은 사용자 홈 디렉터리의 설정 파일에 저장됩니다.
- 저장 폴더의 PNG 파일을 감시하여 목록과 현재 미리보기를 자동 갱신합니다.

### 단축키

- `Space`: 현재 선택된 기기 화면 캡처
- `←` / `→`: 이전 / 다음 이미지 보기
- `Delete`: 현재 이미지 삭제
- `Ctrl+C` 또는 `Cmd+C`: 현재 이미지 클립보드 복사
- `Ctrl+W` 또는 `Cmd+W`: 앱 종료
- `Alt+F4`: 앱 종료

### 프로젝트 구조

- `app/src/desktopMain/kotlin/Main.kt`: 데스크톱 앱 메인 UI 및 동작
- `app/src/desktopMain/kotlin/ImageSelection.kt`: 이미지 클립보드 복사용 전송 객체
- `app/src/desktopMain/kotlin/WindowSettings.kt`: 창/경로 설정 저장 로직
- `app/src/desktopMain/kotlin/mrsohn/capture/adb/AdbRunner.kt`: ADB 명령 실행 및 캡처 처리
- `app/src/desktopMain/kotlin/mrsohn/capture/adb/DeviceInfo.kt`: 연결 기기 정보 모델

### 배포

Compose Desktop 설정을 통해 아래 형식의 네이티브 패키징을 지원합니다.

- `dmg`
- `msi`
- `deb`

필요 시 아래와 같은 Gradle 작업을 활용할 수 있습니다.

```bash
./gradlew :app:packageDistributionForCurrentOS
```

### 배포 패키지 생성
```bash
./gradlew :app:packageDmg  # macOS
./gradlew :app:packageMsi  # Windows
./gradlew :app:packageDeb  # Linux
 생성위치
 /MrSohnCapture/app/build/compose/binaries/main
```


### 참고 사항

- 이미지 편집은 운영체제 기본 연결 앱으로 열립니다.
- `adb` 경로가 올바르지 않거나 기기 연결이 없으면 캡처에 실패할 수 있습니다.
- 생성일을 읽을 수 없는 파일은 수정일을 대체값으로 사용해 정렬합니다.