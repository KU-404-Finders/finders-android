# Android Edge-to-edge 검토

> **버전 이력 (2026-11-28 기준)**
> - `1.1.2 / 9`: 기존 테스트 버전. 이 문서의 과거 검증 다수가 이 메타데이터로 빌드됐다.
> - `1.2.0 / 10`: 이전 프로덕션 출시 후보. AAB가 Play Console에 업로드됨.
> - `1.2.0 / 11`: 이전 출시 후보. AAB가 Play Console에 업로드됨. PR #18 리뷰 반영·UI 통일 변경은 포함되지 않음.
> - `1.2.0 / 12`: **현재 출시 후보이자 Gradle 설정**(2026-11-28 사용자 승인). PR #18 리뷰 반영·iOS UI 통일 변경을 포함한다. 운영 서명과 Play Console 업로드는 출시 담당자가 직접 진행한다.
>
> 아래 본문의 과거 검증 결과는 기록 당시 소스에서 실행한 것이며, 현재 수정된 소스에서 재실행한 결과가 아니다. 현재 소스의 재검증은 `production-release-verification.md`의 "PR #18 리뷰 반영" 절을 따른다.

Play Console의 1.1.2 (versionCode 9) Edge-to-edge 경고를 기준으로 Android 앱을 점검했다.

## 작업 기준

- 작업 브랜치 `feature/ios-parity-stability`, 원격 `develop`, 로컬 `origin/develop`은 모두 `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a`를 가리켰다. `git ls-remote`와 `git fetch --prune origin`으로 확인했다. 기존 변경이 있는 작업 트리에서 인셋 관련 부분만 수정했다.
- Android 레이아웃과 의존성만 참조했다. iOS 및 Server 코드 참조가 필요한 변경은 없었다.
- 점검 당시 로컬 빌드 설정은 `compileSdk 37`, `targetSdk 36`, `versionCode 9`였다(과거 결과). 현재 Gradle은 `1.2.0 / 12`이다.

## 시스템 바 및 IME 처리

| 화면·구성 | 인셋 처리 |
| --- | --- |
| 앱 시작 | `MainActivity`는 스플래시 설치와 `super.onCreate()` 직후 `enableEdgeToEdge()`를 적용한다. 밝은 배경 위의 어두운 시스템 바 아이콘을 유지한다. 매니페스트의 `adjustResize`가 키보드 인셋을 Compose에 전달한다. |
| 세션 확인 및 모든 NavHost 경로 | 루트 `Scaffold`가 `WindowInsets.safeDrawing`으로 상태바, 내비게이션바, 컷아웃, IME를 계산한다. `MainNavGraph`가 `innerPadding`을 적용하고 소비한다. 헤더, 게시판 FAB, 목록과 스크롤 폼은 이 영역 안에서 배치된다. |
| 주요 탭의 하단 내비게이션 | Material 3 `NavigationBar`의 기본 인셋은 가로와 하단의 시스템 바 및 컷아웃을 처리한다. 바가 보이면 `Scaffold`의 하단 패딩은 측정된 바 전체 높이를 사용한다. 키보드가 열리면 바가 숨겨지고 루트 안전 영역이 IME 높이를 반영한다. |
| 채팅방, 댓글, 로그인, 회원가입, 비밀번호 재설정, 게시글 등록·수정 | 컨테이너가 루트에서 확보된 높이 안에 배치된다. 화면별 `imePadding()`을 제거했다. 채팅 입력창과 고정 하단 버튼은 키보드 위에 배치되며, 세로 스크롤 폼은 줄어든 높이 안에서 스크롤한다. |
| 기타 경로 | 검색·위치 선택·프로필·알림·분실물 예방 알림·관리자 화면도 동일한 NavHost 인셋을 사용한다. 대화상자와 메뉴 팝업은 별도 Compose 창의 기본 인셋 처리를 사용한다. |

[Compose Edge-to-edge 설정](https://developer.android.com/develop/ui/compose/system/setup-e2e) · [인셋 소비](https://developer.android.com/develop/ui/compose/system/insets-ui) · [Material 3 인셋](https://developer.android.com/develop/ui/compose/system/material-insets)

Android 15 가로·3버튼 탐색·코너 컷아웃에서 키보드를 열자 회원가입 이름 입력란의 표시 높이가 99px로 줄었다. 높이 480dp 미만인 창에서는 이름·비밀번호·이메일·인증번호 단계의 버튼 위아래 여백을 20dp에서 8dp로 줄였다. 같은 조건에서 수정한 Release 빌드의 키보드 활성 상태 입력란은 132px가 표시됐다.

## 지원 중단 API와 파라미터

| 기원 | 조사 결과 |
| --- | --- |
| 앱 테마 | `Theme.KUfinders`의 `android:statusBarColor`, `android:navigationBarColor`를 제거했다. 흰색 창 배경, 브랜드 강조색, 라이트 시스템 바 설정을 사용한다. |
| 앱 Kotlin·매니페스트 | 앱 런타임 소스와 리소스에서 추가적인 시스템 바 색상 호출, `systemUiVisibility` 직접 호출, 컷아웃 모드 고정 및 Edge-to-edge opt-out은 확인되지 않았다. |
| AndroidX Activity/Core | `enableEdgeToEdge()`가 호출하는 Activity 호환 구현에는 플랫폼 시스템 바 색상과 컷아웃 접근이 있다. Core 인셋·시스템 바 아이콘 호환 구현도 기존 플랫폼 접근을 포함한다. |
| AndroidX SplashScreen | Android 15 컷아웃 호환 수정이 포함된 `core-splashscreen` 1.2.0으로 업데이트했다. 라이브러리의 기본 `Theme.SplashScreen.Common`에는 구버전 시작 화면용 투명 시스템 바 색상 속성이 남아 있다. |

Play에 게시된 APK/AAB나 Console의 호출 위치 목록은 현재 검증 자료에 포함되지 않았다. 기원 조사에는 로컬 Release APK와 R8 매핑을 사용했다. Play가 새 번들을 검사한 결과는 별도 확인 대상이다.

`aapt2` 리소스 덤프에서 비교용 로컬 APK의 `Theme.KUfinders`에 두 색상 속성이 있었고 최종 APK의 앱 테마에서는 제거된 것을 확인했다. `dexdump`와 R8 매핑으로 추적한 시스템 바 및 컷아웃 접근은 AndroidX Activity/Core 호환 코드에서 나왔다. R8이 다른 클래스에 인라인한 일부 접근도 `WindowCompat` 기원이었다. 검색 범위의 플랫폼 호출 중 앱이나 AndroidX 이외 SDK가 구현한 호출은 확인되지 않았다.

[Android 15 시스템 바 및 스플래시 변경](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge) · [Android 16 Edge-to-edge 적용](https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge) · [Core SplashScreen 릴리스 노트](https://developer.android.com/jetpack/androidx/releases/core#core-splashscreen)

전체 Lint에서 API 27 이상이 필요한 `android:windowLightNavigationBar` 테마 속성을 확인해 제거했다. 내비게이션바 아이콘 설정은 API 26에서도 기존 `WindowInsetsControllerCompat` 호출로 적용한다. 이 리소스 변경 전후의 Debug·Release DEX 파일 SHA-256이 각각 일치했다.

## 실행 결과

| 환경·실행 | 확인 결과 |
| --- | --- |
| 최종 Debug 및 Release | `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease` 성공. Release 빌드에 R8과 Lint Vital이 포함됐다. |
| 정적 검사 | `:app:lintDebug` 성공. 오류 0건, 경고 74건, 힌트 6건. `git diff --check` 통과. |
| JVM 회귀 | `:app:testDebugUnitTest` 314개 통과, 실패·건너뜀 0개. |
| Android 14 · 7인치 · 세로 · 제스처 | 로그인 비밀번호 필드가 키보드 안전 영역에 표시됐다. 최종 전체 계측은 58건 중 56건 통과, 일반 실행에서 Release 전용 2건 건너뜀, 실패 0건. 새 테스트는 회원가입 입력 및 하단 버튼 간격, 채팅 입력 및 전송 버튼, 게시글 등록·수정 입력을 확인했다. |
| Release 전용 계측 | 최종 R8 APK와 매핑을 사용한 `ReleaseRetrofitProbeTest` 2건 별도 통과. |
| Android 14 · 10인치 | 최종 Debug에서 앱 및 회원가입 화면 진입, 키보드 비활성 상태의 입력과 버튼 표시를 확인했다. 위치 권한 거부 회귀 테스트가 통과했다. 키보드 표시 대기는 두 차례 시간 초과되어 IME 활성 상태는 미검증이다. |
| Android 15 · 세로 제스처 및 가로 3버튼 · 컷아웃 | 중간 Debug에서 키보드 활성·비활성 상태의 로그인 비밀번호 필드 위치를 확인했다. 이후 Debug 재실행에서 시작 ANR을 기록했다. 로컬 테스트용으로 서명한 Release의 세로·가로 3버튼 환경에서 키보드 활성·비활성 상태의 회원가입 입력란을 확인했다. 가로에서 키보드 시작점은 529px, 입력란 하단은 353px이었다. |
| Android 16 · 세로 · 제스처 · 컷아웃 | 로그인 비밀번호 필드와 회원가입 이름 입력·하단 버튼의 키보드 안전 영역을 확인했다. 이 실행은 컴팩트 회원가입 여백 조정 이전 빌드에서 수행했다. 최종 Debug 재검증은 에뮬레이터 부팅 시간 초과로 완료하지 못했다. |
| Galaxy SM-S921N · API 37 | 초기 Debug APK 설치 및 Activity 실행 요청 성공. 실제 화면과 키보드 배치는 미검증이다. |

채팅·게시글 입력 테스트는 로컬 UI 데이터와 콜백으로 실행했다. 로그인 계정이 필요한 화면의 실제 서버 동작, 모든 화면과 기기 조합의 시각 회귀, 출시 APK/AAB의 Play Console 재검사는 미검증이다.

빌드 및 기기 로그는 로컬 `build/edge-to-edge/`에 기록했다.
