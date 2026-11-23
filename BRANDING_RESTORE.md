# 찾을건대 글자 전용 브랜딩 및 복원 기록

## 기준과 기존 백업

- 작업 브랜치: `feat/text-only-branding`
- 변경 전 HEAD: `c135c4f326498a5de01a514aa1db873a8ce19df8`
- 기존 백업 브랜치: `backup/before-text-logo-20261123` (동일 커밋)
- 기존 ZIP: `C:\Users\Administrator\Desktop\finders-before-logo-change.zip`
- ZIP 크기: 10,351,154 bytes
- ZIP SHA-256: `7136c0ed63eae63ac0962f4a1cde993f126e1c69547f2534da4e15c12d26dbec`
- ZIP CRC 검사 정상. ZIP의 로그인 화면·마스코트 PNG·Play Store PNG를 백업 Git 소스와 비교하여 일치를 확인했다. 텍스트 파일의 CRLF/LF 차이는 정규화했다.

백업을 새로 만들거나 기존 브랜치/ZIP을 삭제·덮어쓰지 않았다. 원래 이미지는 위 백업에서 복원하며, 배포용 앱 소스에 옛 이미지의 백업 사본을 남기지 않는다.

## 제공 원본과 변환

요청의 하이픈 경로는 존재하지 않았으며, 실제 파일은 `C:\Users\Administrator\Desktop\finders_branding_input`에서 확인했다.

| 원본 | 크기·용도 | 처리 |
|---|---|---|
| `wordmark_green_transparent.png` | 927×240 RGBA, 로그인 | 원본 파일을 `drawable-nodpi/img_finders_wordmark.png`에 바이트 그대로 복사 |
| `app_icon_green_1024.png` | 1024×1024 RGB, 앱 아이콘 | 원본 비율을 유지하여 밀도별 런처·Adaptive Icon·512×512 Play Store 파일 생성 |
| `ios_login_reference.png` | 1440×1080, 배치 참고 | 흰 배경과 중앙 정렬 글자 로고 배치를 참고. 배포 앱에는 포함하지 않음 |

원본 워드마크 SHA-256은 `a5cb59e2a464d6e9689250454a4c371254f191076dfa2d2bd01fbdc4c77777a1`, 원본 앱 아이콘 SHA-256은 `6377ddd68737ad675b93942501c4bfdff100a9c68f96f683315688ffc4bcd5cb`이다. 글자를 다시 그리거나 폰트로 대체하지 않았으며 AI 이미지 생성도 사용하지 않았다. 원본의 초록색은 **#104828**이다.

로그인 로고는 사용 가능한 가로 폭의 75%, 최대 240dp로 표시하며 원본 비율과 `ContentScale.Fit`을 사용한다. 로고 영역 높이는 220dp다. 흰 배경·중앙 배치를 유지하고, 입력·키보드·로그인·회원가입·재설정·인증 로직은 변경하지 않았다.

Adaptive Icon은 108dp 투명 전경 레이어의 중앙에 원본 전체 아이콘을 72dp 비율로 배치하고, 원본의 단색 배경을 분리하여 동일한 #104828 배경 레이어와 결합한다. 글자 전경은 원본 RGB를 알파로 분리한 것이며 원본 배경과 재합성 시 채널 오차가 최대 1/255 이내임을 검사한다. 모든 밀도에서 글자의 모든 비투명 픽셀이 중앙 지름 66dp 원 안에 들어가는지 확인한다. 테마 아이콘의 monochrome도 투명 글자 전경을 사용한다.

스플래시는 흰 창 배경과 글자 전용 `@mipmap/ic_launcher`를 사용한다. 로그인은 초록 글자/흰 배경, 아이콘과 스플래시 아이콘은 흰 글자/초록 배경으로 동일한 제공 글자 형태를 유지한다.

규격 참고: [Android Adaptive Icon](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Android SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen).

## 수정·삭제 파일

| 구분 | 경로 |
|---|---|
| 로그인 로고 영역 | `app/src/main/java/com/ku/lostandfound/ui/login/screen/LoginScreen.kt` |
| 새 원본 워드마크 | `app/src/main/res/drawable-nodpi/img_finders_wordmark.png` |
| 런처 비트맵 15개 교체 | `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher,ic_launcher_round,ic_launcher_foreground}.webp` |
| 배경색 | `app/src/main/res/values/ic_launcher_background.xml` |
| 스플래시 참조 | `app/src/main/res/values/themes.xml` |
| Play Store 아이콘 교체 | `app/src/main/ic_launcher-playstore.png` |
| 마스코트 PNG 삭제 | `app/src/main/res/drawable/img_lost_and_found_ku.png` |
| 사용하지 않는 이전 템플릿 삭제 | `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/drawable/ic_launcher_background.xml` |
| 변환·패키지 검사 도구 | `tools/branding_assets.py` |
| 이 기록 | `BRANDING_RESTORE.md` |

`mipmap-anydpi-v26/ic_launcher.xml`과 `ic_launcher_round.xml`은 이미 배경색 및 밀도별 전경을 참조하므로 변경할 필요가 없었다. 이 두 XML의 foreground·monochrome은 새 전경으로 해석된다. `mipmap-anydpi`에는 별도 기존 파일이 없었다. `ic_stat_name.png`는 모든 밀도에서 이미 글자만 있는 알림 아이콘이므로 유지했다. 그 밖의 채팅·뒤로가기·가입 완료 이미지는 마스코트가 없는 일반 UI 자산으로 유지했다.

## 재생성 및 패키지 검사

Windows에 이미 설치되어 있는 Python 3.13과 Pillow를 사용했다. 입력 파일 3개와 기존 백업을 수정하지 않는다.

```powershell
py -3.13 tools/branding_assets.py --input C:\Users\Administrator\Desktop\finders_branding_input

.\gradlew.bat --no-daemon :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease --console=plain

py -3.13 tools/branding_assets.py --audit app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/bundle/release/app-release.aab
git diff --check
```

QA 산출물은 Git에서 제외되고 앱 소스에도 포함되지 않는 `build/branding`에 저장한다. `asset-validation.json`은 색상·원본 해시·안전 영역 검사 결과다. `baseline-assets.json`은 기존 백업의 마스코트 이미지 17개에 대한 경로·파일 해시·보이는 픽셀 해시만 기록한다. 기존 이미지를 복사하는 파일이 아니다. `package-validation.json`은 APK/AAB의 모든 PNG/WebP/JPEG를 검사한 결과다. 원본 파일 해시 외에 흰 배경에 합성한 픽셀 해시도 비교하여 AAPT가 PNG를 재인코딩하거나 투명 픽셀의 숨은 RGB를 바꾸는 경우도 검사한다. 이전 리소스 이름이 리소스 테이블에 남는지도 확인한다.

## 기존 디자인 복원

학교의 사용 승인이 확인된 뒤, 먼저 현재 작업 트리와 후속 변경을 검토한다. 아래 명령은 **브랜치를 전환하거나 커밋 이력을 바꾸지 않고 지정한 브랜딩 파일만** 기존 백업에서 복원한다. 이번 작업에서는 실행하지 않았다.

```powershell
git restore --source backup/before-text-logo-20261123 -- app/src/main/java/com/ku/lostandfound/ui/login/screen/LoginScreen.kt app/src/main/ic_launcher-playstore.png app/src/main/res/drawable/img_lost_and_found_ku.png app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/values/ic_launcher_background.xml app/src/main/res/values/themes.xml 'app/src/main/res/mipmap-*/ic_launcher*.webp'

Remove-Item -LiteralPath app/src/main/res/drawable-nodpi/img_finders_wordmark.png
git diff --check
```

기준 커밋 SHA를 `--source c135c4f326498a5de01a514aa1db873a8ce19df8`로 지정해도 동일하게 복원된다. 이후 LoginScreen에 다른 수정이 생겼다면 파일 전체를 복원하지 말고 `git show backup/before-text-logo-20261123:app/src/main/java/com/ku/lostandfound/ui/login/screen/LoginScreen.kt`로 원래 로고 영역을 확인하여 해당 영역과 import만 복원한다. 원래 영역은 `R.drawable.img_lost_and_found_ku`와 `Modifier.size(300.dp)`를 사용했다.

ZIP으로 복원할 경우 임시 폴더에 풀어 위 표의 기존 파일만 원래 상대 경로로 복사한다. 프로젝트 전체를 덮어쓰지 않는다. Git 백업 브랜치와 ZIP은 복원 후에도 그대로 보존한다. 복원 후 Debug/Release 빌드, 원형·둥근 사각형 런처와 스플래시, 로그인 진입을 다시 확인한다.

## 검증 결과

- Debug 컴파일·APK·전체 단위 테스트: BUILD SUCCESSFUL. **77개 통과, 실패·오류·건너뜀 0개**.
- Release APK·AAB 및 R8/필수 Lint: BUILD SUCCESSFUL.
- 배포 패키지 이미지 검사: Debug APK 66개, Release APK 42개, AAB 42개 검사. 기존 마스코트 자산과 일치하는 파일·보이는 픽셀 및 이전 리소스 이름은 모두 0개.
- 원형 및 둥근 사각형 미리보기, 모든 밀도의 Adaptive Icon 66dp 안전 영역 검사를 통과했다.
- Android 14(API 34) 테스트 에뮬레이터 `emulator-5580`에 Debug APK를 설치하여 로그인·원형 런처·실제 콜드 스타트 스플래시를 확인했다. 시작 영상을 프레임별로 확인했으며 기존 캐릭터는 없고 새 글자 아이콘만 표시됐다.
- 360×800, 320×640, 720×1024에서 로고 비율·가로 중앙 배치·최대 폭을 확인했다. 320×640에서는 기존 세로 스크롤로 하단 링크에 접근할 수 있음을 확인했다.
- 회원가입 이름 입력 화면 및 비밀번호 재설정 이메일 화면의 진입·뒤로가기를 확인했다. 실계정 로그인, 가입 완료 및 인증 메일 발송 등 서버 통합 테스트는 수행하지 않았다.
- 에뮬레이터는 기존 사용자 AVD를 수정하지 않는 별도 `build/branding/emulator/avd`에 생성했다. 테스트용 로컬 환경이며 백업 복제나 배포 앱 자산이 아니다.

| 증거 파일 (`build/branding/` 아래) | 내용 |
|---|---|
| `text-branding-preview.png` | 원본 기반 정사각형·원형·둥근 사각형 마스크 및 로그인 글자 비교 |
| `login-android14.png` | 360×800 실제 로그인 화면 |
| `home-stable-android14.png` | 실제 런처의 글자 전용 아이콘 |
| `startup-android14.mp4`, `startup-contact-sheet.png` | 콜드 스타트 영상 및 프레임 비교 |
| `login-320x640.png`, `login-320x640-scrolled.png` | 작은 화면의 비율 및 스크롤 확인 |
| `login-720x1024.png` | 큰 화면의 최대 폭 제한 확인 |
| `signup-android14.png`, `reset-android14.png` | 기존 가입·재설정 화면 진입 확인 |

앱 버전 `versionCode=6`, `versionName=1.0.5`를 유지한다. 인증·네트워크·FCM·채팅·게시판 로직과 서버·iOS 소스를 변경하지 않으며, 기존 PR #11/#12/#13에 commit/push/merge 또는 원격 변경을 하지 않는다.
