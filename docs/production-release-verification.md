# 찾을건대 Android 프로덕션 출시 전 최종 통합 검증

> **버전 이력 (2026-11-28 기준)**
> - `1.1.2 / 9`: 기존 테스트 버전. 이 문서의 과거 검증 다수가 이 메타데이터로 빌드됐다.
> - `1.2.0 / 10`: 이전 프로덕션 출시 후보. AAB가 Play Console에 업로드됨.
> - `1.2.0 / 11`: 이전 출시 후보. AAB가 Play Console에 업로드됨. PR #18 리뷰 반영·UI 통일 변경은 포함되지 않음.
> - `1.2.0 / 12`: **현재 출시 후보이자 Gradle 설정**(2026-11-28 사용자 승인). PR #18 리뷰 반영·iOS UI 통일 변경을 포함한다. 운영 서명과 Play Console 업로드는 출시 담당자가 직접 진행한다.
>
> 아래 과거 검증 결과는 당시 산출물의 기록이다. 현재 작업 트리의 검증은 바로 아래 절에 있다.

**판정은 NOT READY다.** 현재 출시 후보는 `1.2.0 / 12`(Gradle 설정)이다. 코드 10과 11의 AAB는 이미 Play Console에 업로드됐고, PR #18 리뷰 반영·UI 통일 변경은 코드 12에만 포함된다. 운영 서명, 운영 CHAT 배포 및 실환경 검증, 실제 Firebase 푸시, 갤럭시 Heads-up, 실제 위치 기반 알림 검증, 백그라운드 위치 심사가 남았다. 개인정보 처리방침은 별도 작업에서 갱신 중이다.

## 최종 로컬 출시 후보 검증

Android `feature/ios-parity-stability`의 `f1a331ff4b7a367998373057b606f8e081842904`와 기존 미커밋 변경을 기준으로 작성·수정 입력란 및 예방 알림 계측을 조정했다. iOS와 Server는 앞선 비교 기록을 사용했다. 공식 문의 주소의 확인은 팀 내부 과제이며 기존 주소를 유지했다. Windows에서 iOS 실화면 비교는 미검증이다. [UI 비교 및 스크린샷 목록](ios-android-ui-parity-audit.md)

| 영향 범위 | 최종 결과 |
| --- | --- |
| 7인치 API 34 가로(1920×1200) 키보드 | **3건 통과.** 채팅 입력·전송, 게시글 작성과 수정 본문, 회원가입 입력과 하단 버튼이 키보드 위에 들어왔다. 게시글 본문은 가로에서 실제 스크롤 영역에 맞춰 184dp 이하로 조절하고 세로의 기존 184dp를 유지한다. 처음에는 에뮬레이터 System UI 응답 없음 창으로 IME 대기가 실패했고 창을 닫은 뒤 동일 APK에서 통과했다. `build/local-candidate-7in-insets-retry.log` |
| 예방 알림 계측 및 JVM | **계측 2건, JVM 13건 통과.** 미리보기 계측은 고정 `RAIN`을 주입해 우산이 포함된 실제 시스템 알림까지 확인했다. 제품의 Open-Meteo 조회와 기존 시간 제한을 유지했다. `build/local-candidate-reminder.log` |
| `ChatPushSessionRestoreTest` | **seed 1건, restore 1건 통과.** 직접 계측을 두 번 실행하고 `-e pushRestorePhase seed`, `-e pushRestorePhase restore`를 각각 사용했다. 다른 PID에서 세션을 복원하고 현재 수신자는 허용, 다른 수신자는 차단했다. 단계 사이의 앱 데이터는 유지했다. `build/local-candidate-chat-restore-seed.log`, `build/local-candidate-chat-restore.log` |
| 로컬 산출물 | **빌드 성공.** Debug APK, 계측 APK, 미서명 Release AAB를 만들었다. AAB의 ZIP CRC, 기본 모듈 매니페스트와 DEX, 서명 레코드 부재를 확인했다. `build/local-candidate-build.log` |

위 표의 미서명 AAB(SHA-256 `60bc7da639520773a79b42bbe810663c7ef4e5bed199e301346068e8093b9fb2`)는 버전 코드를 올리기 전의 `1.2.0 / 11` 로컬 검증용 산출물이다(과거 결과).

### 출시 후보 `1.2.0 / 12` 산출물 (2026-11-28)

사용자 승인에 따라 `versionCode`를 12로 올린 뒤 같은 작업 트리에서 JVM 전체 327건 통과(실패 0), Debug APK, 미서명 Release APK(SHA-256 `32a8b7f539442f95fa6081e3721e2883b7715e8e0e890ab388f4d838012b18c4`), 미서명 AAB `app/build/outputs/bundle/release/app-release.aab`(SHA-256 `3f678bc9668f61030637830041a2c6f58deacfc5ace56ac163b6879058b1f000`)를 생성했다. 같은 APK로 7인치 API 34에서 EdgeToEdge 3건(2회), 예방 알림 계측 5건(2회), 10인치 API 34에서 EdgeToEdge 3건이 통과했다. 운영 서명과 Play Console 업로드는 출시 담당자가 진행한다.

## iOS UI 통일 및 Android 알림 표시 개선

### 참조 기준

| 저장소 | 실제 참조 브랜치와 SHA | 원격 확인 |
| --- | --- | --- |
| Android | `feature/ios-parity-stability` HEAD와 `origin/develop` `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a` | SSH 포트가 제한돼 이번 Git 명령에서만 HTTPS를 사용했다. 원격 HEAD 확인과 fetch를 마쳤고, 기존 수정이 있는 작업 브랜치를 유지했다. |
| iOS | `main` `665ff149e2f2c63beb4478b004eec7796043c5b0` | 원격 HEAD 확인, fetch 및 fast-forward 전용 pull 결과 최신이었다. |
| Server | `main` `f68cafb2b4e50564cd179cd20432a5deb28c83fb` | 원격 HEAD 확인, fetch 및 fast-forward 전용 pull 결과 최신이었다. |

### 화면 비교

| 화면 | 소스 비교 결과와 이번 반영 |
| --- | --- |
| 분실물·습득물 게시판 | 두 앱 모두 상단 프로필·검색·알림, 유형별 제목, 목록 구분선 및 작성 진입점을 갖는다. iOS는 캡슐형 분류 필터와 SwiftUI 지도 프레임을 쓰고 Android는 Compose 필터·공유 지도·기존 로딩과 빈 상태를 쓴다. |
| 지도·검색 | 캠퍼스 건물에서 습득물 범위를 좁히고 별도 검색 화면으로 진입한다. iOS는 SwiftUI 지도 제스처와 결과 목록을 쓰고 Android는 Compose 지도 및 검색 안내와 OSM 출처를 쓴다. |
| 게시글 상세 | 유형별 상세 제목, 채팅·신고·차단 진입을 대조했다. 상단 메뉴 및 확인 UI는 각 플랫폼의 메뉴와 대화상자를 쓴다. |
| 게시글 작성·수정 | 유형과 장소·사진·제출 흐름을 대조했다. iOS는 자체 세그먼트와 사진 미리보기를 쓰고 Android는 Compose 입력 및 Android 사진 선택기를 쓴다. |
| 채팅 목록·채팅방 | iOS 목록은 스와이프 메뉴에서 나가기 확인으로 연결한다. 양쪽의 읽지 않은 채팅, 채팅방 상단 정보와 메시지 작성 경로를 대조했다. |
| 알림함·설정 | iOS는 상단에 일괄 읽기·삭제를 둔다. Android도 두 액션을 제목과 같은 줄로 옮겼다. Android의 행 스와이프와 일괄 삭제 확인을 유지하며, 설정에 스크롤과 시스템 채널 진입을 추가했다. |
| 마이페이지 | iOS는 알림 설정과 예방 알림을 `알림` 섹션에 묶고 예방 알림의 켬·끔을 보여준다. Android도 알림 설정 다음에 예방 알림을 배치하고 아이콘·구분선·켬/끔·이동 표시를 넣었다. |
| 로그인·회원가입·비밀번호 재설정 | 이메일 인증과 비밀번호 입력 단계, 진행 버튼과 오류 안내를 대조했다. iOS의 SwiftUI 입력과 Android의 IME 및 인셋 처리가 다르다. |
| 차단 사용자 | 두 앱 모두 전용 화면, 빈 목록 및 확인 후 해제를 제공한다. Android는 검증된 서버 이름 필드와 캐시 경로를 사용하고 iOS는 로컬 이름 캐시를 사용한다. |
| 신고 및 관리자 | iOS와 Android의 관리자 진입·신고 목록·상세·처리 확인을 대조했다. 목록의 상태 표시와 대화상자는 플랫폼별 구성이다. |

### 분실물 예방 알림 변경 전후

| 항목 | 이전 Android | 변경 후 |
| --- | --- | --- |
| 제목·켜기·권한 | 기능 스위치와 권한 상태가 한 카드에 있었다. | 기존 제목과 뒤로가기에 이어 스위치·설명, 권한 상태, 건물·방문 기준, 소지품, 미리보기·테스트·날씨 순으로 배열했다. Android의 명시적 위치 사용 동의와 위치 단계는 유지했다. |
| 건물·체류 | 선택 개수를 표시하고 건물마다 체크박스를 둔다. 체류 설정은 버튼에 현재 값이 붙었다. | 첫 건물 이름과 나머지 수를 표시한다. 건물 화면의 이름순 행에 선택된 건물만 체크를 표시하고, 방문 기준의 제목과 `15분 이상` 같은 값을 나눠 표시한다. |
| 소지품 | 조건 드롭다운과 소지품마다 삭제 버튼이 있었다. | 이름·조건 행, 왼쪽 스와이프와 접근성 삭제 액션, 이름 입력 후 키보드 완료 또는 추가 버튼을 제공한다. 첫 설정의 우산과 세 가지 조건, 최대 12개·12자 제한을 유지했다. |
| 테스트·날씨·저장 | 미리보기와 테스트, Open-Meteo 링크가 한 카드에 있었다. | 제목·부제·본문 미리보기 다음에 테스트 및 조회 결과를 표시하고 날씨 출처를 하단에 둔다. 시스템이 알림을 막으면 테스트 버튼이 비활성화된다. 기존 저장 키와 즉시 저장 방식, 지오펜스·최소 체류·Open-Meteo 로직을 유지했다. |

### 채널 및 업데이트

| 알림 | 이전 | 현재 기본값 |
| --- | --- | --- |
| CHAT | `finders_messages_high` 공유 채널, HIGH | 기존 ID, HIGH, `MESSAGE` |
| COMMENT 및 구형 POST | 같은 공유 채널, HIGH | `finders_comments`, DEFAULT, `SOCIAL` |
| MATCH | 같은 공유 채널, HIGH | `finders_matches`, HIGH, `RECOMMENDATION` |
| 분실물 예방 | `finders_leave_reminders`, DEFAULT | 같은 ID, 새 등록 시 HIGH, `REMINDER`. 실제 알림과 테스트가 같은 발행 함수를 사용한다. |

- 예방 알림의 기존 코드에서 `IMPORTANCE_DEFAULT`를 확인했다. 기존 설치에서 등록된 채널 중요도는 Android가 유지하므로, 예방 알림 화면의 채널 설정 링크에서 기존 사용자도 팝업을 직접 설정할 수 있다. 채팅과 MATCH는 종전 코드에서도 HIGH였다. 제보 기기의 채널 설정과 팝업 미표시 원인은 기기 증거가 남아 있다. [Android 알림 채널](https://developer.android.com/develop/ui/compose/notifications/channels)
- 신규 또는 종전 공유 채널이 기본 상태인 설치에 COMMENT·MATCH 채널을 분리한다. 중요도·알림음·진동·잠금화면 속성 등의 변경을 확인한 설치는 종전 공유 채널로 계속 수신한다. 차단된 채널은 유지한다. 분리 채널이 이미 있는 설치는 각 채널의 설정을 따른다. Android API에서 읽을 수 있는 채널 속성과 사용자 설정 플래그를 판단에 사용하며 제조사 전용 팝업 설정은 별도 확인이 필요하다.
- 새 채널은 Android 기본 알림음과 진동을 사용하고, 예방 알림 Builder는 HIGH 우선순위를 함께 지정한다. API 26 이상에서 실제 동작은 시스템 채널과 권한, 방해금지 및 잠금화면 설정에 따른다. 앱은 별도 알림 그룹이나 전체화면 인텐트를 설정하지 않는다. 채팅은 기존과 같이 방별 알림을 교체한다.
- 기준 Server의 Android 푸시는 포그라운드와 백그라운드에서 앱이 처리하는 데이터 메시지다. Manifest의 FCM 기본 채널은 기존 ID를 사용한다. CHAT 수신자와 세션 검증 이후에만 발행하고, COMMENT·MATCH는 기존 고정 문구와 알림함 접근을 유지한다. 로그아웃 때 기존 및 신규 원격 채널의 알림과 클릭 인텐트를 취소한다.

### 이번 변경의 검증

| 항목 | 결과 |
| --- | --- |
| Debug·계측 APK 및 JVM | Debug 및 계측 APK 빌드 성공. 알림 탐색 23건, 예방 알림 12건, 푸시 수신자 5건이 통과했다. 이전 JVM 전체 317건, Release·AAB, Lint·DEX·16KB 증거는 이전 산출물의 검증 기록이다. |
| 1.1.2 → 1.2.0 업데이트 | Android 14/API 34 에뮬레이터의 코드 9 설치에서 코드 10 Debug로 인플레이스 업데이트했다. 기존 공유 채널 HIGH와 사용자 설정 잠금 없음이 확인됐다. 업데이트 뒤 시스템 등록값은 CHAT 4, COMMENT 3, MATCH 4, 예방 4였다. 구버전 1.2.0 테스트 APK에서의 업데이트와 제조사별 사용자 지정 채널의 실제 단말 테스트는 남았다. |
| 예방 알림 UI·저장 | 권한 사전 고지·취소·정확한 위치 미허용·기능 OFF, 설정의 스크롤·건물 화면 이동·방문 기준 표시·저장값 유지 및 테스트 알림 게시를 확인했다. 기존 방문 및 감시 기록 저장 테스트 3건도 통과했다. |
| CHAT 및 호환 알림 | 계정 전환 후 A 알림과 PendingIntent 차단, B 알림 게시, CHAT 탭 인증 및 알림 탐색을 확인했다. COMMENT·MATCH는 각각의 채널에 고정 문구로 게시됐으며 로그아웃 때 제거됐다. 별도 프로세스 저장·복원 계측 두 단계가 통과했다. |
| 실제 Heads-up 표시 | Android 14 에뮬레이터 화면이 켜지고 앱이 전면에 있는 상태에서 예방 테스트 알림을 발행했다. 해당 알림 키의 SystemUI `sysui_heads_up_status`가 `1 → 0`으로 전환됐다. 예방 설정 화면은 스크린샷으로 확인했고, 팝업 프레임은 캡처에 담기지 않았다. CHAT·MATCH의 팝업과 갤럭시 화면 표시는 미검증이다. |

빌드 로그는 `build/ui-parity-notification-verification.log`, 기기 로그는 `build/ui-parity-device-tests.log`, `build/ui-parity-reminder-clean-fixture.log`, `build/ui-parity-process-seed.log`, `build/ui-parity-process-restore.log`다. 예방 설정 화면과 팝업 상태 증거는 `build/ui-parity-reminder-settings.png`, `build/ui-parity-heads-up.log`에 있다. 최초 예방 알림 UI 실행에서 에뮬레이터에 남은 합성 인증 세션 때문에 두 테스트가 건너뛰어졌다. 격리된 AVD의 합성 인증 파일만 임시 이동한 뒤 두 테스트가 통과했다. 독립적인 완전 신규 설치, 기존 예방 알림 DEFAULT 채널의 실기기 변경, 알림 권한 거부 상태의 발행 시도 및 변경 화면의 전화기 IME·Edge-to-edge 시각 검증은 남았다.

### 현재 출시 차단 사항

- 운영 Server의 CHAT 수신자 계약 배포를 확인하고, 실제 Firebase에서 계정 전환·알림 클릭·현재 계정 수신을 갤럭시로 검증한다. 실제 Android의 백그라운드·화면 꺼짐·방해금지·잠금 화면 및 캠퍼스 위치·우산 조건 종단 검증도 남았다.
- Play Console에 올린 기존 코드 10 이후의 버전 코드를 확정하고, 운영 서명한 이번 변경분의 새 AAB로 제출 후보를 검증한다.
- 백그라운드 위치 권한 심사 자료와 승인이 필요하다. 개인정보 처리방침 갱신과 Data safety 정합성은 별도 운영 작업이다.

## 이전 통합 검증 기록

아래의 빌드·패키징 및 테스트 결과는 이전 검증 산출물에 해당한다. 이번 변경의 검증 결과는 위 절에 갱신한다.

## 기준 및 변경 범위

| 저장소 | 실제 참조한 커밋 | 동기화 |
| --- | --- | --- |
| Android | `feature/ios-parity-stability` HEAD와 원격 `develop` 모두 `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a` | 원격 조회와 fetch 성공. 기존 작업 트리에서 검증했다. |
| iOS | 로컬 및 원격 `main` `665ff149e2f2c63beb4478b004eec7796043c5b0` | 원격 조회와 fetch 성공. |
| Server | 로컬 및 원격 `main` `f68cafb2b4e50564cd179cd20432a5deb28c83fb` | 이전 검증에서 fast-forward했다. 이번 원격 조회, fetch와 `pull --ff-only`에서 기준 상태를 다시 확인했다. |

Android의 `feature/ios-parity-stability`와 선행 미커밋 작업 위에서 작업했다. `origin/develop`과 HEAD가 일치해 현재 수정 트리에 pull을 적용할 변경이 없었다. iOS와 Server는 읽기 전용으로 참조했다. [v1.2.0 통합 검증 보고서](finders-android-v1.2.0-verification.md) · [Edge-to-edge 검증 보고서](edge-to-edge-verification.md)

v1.2.0 보고서의 도메인·API 검증과 Edge-to-edge 보고서의 최종 APK·화면 검증을 대조했다. JVM 314건은 같은 누적 검증 결과다. 이전 v1.2.0의 API 37 계측 53건과 Edge-to-edge 후의 API 34 계측 56건은 서로 다른 산출물·환경이다. 이번에는 후자의 APK·로그를 재사용하고 미완료 기기 항목을 재검증했다. 푸시 계약의 최신 사실은 Server `main`으로 갱신했다.

## 구현 완료

| 항목 | 확인한 동작과 범위 |
| --- | --- |
| 게시글 중복 등록 방지 | 작성 중 중복 제출을 막고, 사용자 ID에 귀속된 작성 표식을 POST 전에 저장한다. 전송 결과가 모호하면 내 게시글 확인과 명시적 해제를 안내한다. 쓰기 경로의 자동 재시도 제한과 기존 MockWebServer 회귀를 확인했다. |
| 차단 사용자 이름 | Android는 nullable `blockedUserName`을 역직렬화한다. POST 성공과 GET 목록에서 서버 이름을 우선 사용하고, 누락 시 계정별 이름 캐시와 ID 표시로 대체한다. 탈퇴 이름 및 누락 응답의 R8 프로브가 있다. |
| 분실물 예방 알림 | 기본 OFF인 지오펜스 기능이다. 첫 활성화 시 위치 사용 목적을 고지하고 정확한 위치·백그라운드 위치·알림 권한을 확인한다. 기기 내 방문 상태, 세션 교체와 감시 해제, 부팅 복구 경로가 있다. 기존 자동 테스트와 합성 로그인 에뮬레이터 부팅 검증을 재사용했다. |
| 날씨 알림 | 캠퍼스 고정 좌표의 Open-Meteo 예보로 건물 방문 시작 시각의 강수를 판정한다. 기기 GPS, 체류 기록, Finders 인증 정보를 날씨 요청에 싣지 않는다. 기존 강수 시각 경계·자정·누락 테스트를 재사용했다. |
| FCM | **IMPLEMENTED.** CHAT은 수신자 일치 확인 후 제목·본문과 동일한 채팅방 클릭 정보를 사용한다. COMMENT·MATCH 및 기존 POST 호환 데이터는 고정 문구의 일반 알림과 인증된 알림함 조회를 사용한다. |
| OSM 출처 및 약관 링크 | 공유 지도에서 OSM 표기와 저작권 링크를 제공한다. 가입·마이페이지는 같은 약관·개인정보 링크를 참조한다. 기존 링크 동작 테스트와 비로그인 Chrome 시크릿 창의 실제 공개 본문 확인을 구분했다. [OSM 저작권](https://www.openstreetmap.org/copyright) · [이용약관](https://cypress-monkey-570.notion.site/41f29506e32280d9ad75c85fc66dc6e7) · [개인정보 처리방침](https://cypress-monkey-570.notion.site/41129506e322803fafc1eae07ce4925c) |
| 신규 기능과 Edge-to-edge | 루트의 `safeDrawing`과 NavHost의 인셋 소비가 같은 화면 트리에 적용된다. 공유 지도·알림·프로필·작성 화면의 기존 기능 경로를 검토했다. API 34 및 36에서 확인한 입력·탐색 경로에 앱 회귀가 재현되지 않았다. 캠퍼스 현장 및 실서버 동작은 아래 미검증 범위에 있다. |

## 외부 의존성 대기

- **BLOCKED · 운영 CHAT 계약 확인.** Server #67은 Android·iOS CHAT 데이터에 문자열 `recipientUserId`를 추가했다. Android CHAT은 데이터 전용이며 MATCH·댓글에는 수신자 필드가 없다. `ChatPushListener`와 `NotificationWorker`는 채팅 이벤트·작업의 수신자 ID를 sender에 전달한다. 서버의 기기 소유권 검사와 삭제 보호는 이미 Firebase에 넘긴 메시지를 회수하지 못한다. 이 SHA의 배포를 확인하려고 조회한 비인증 GitHub Actions API는 HTTP 404를 반환했다. 운영 배포·실제 수신은 **NOT VERIFIED**다. 구버전 운영 서버에서 필드 없는 CHAT은 새 Android에서 표시되지 않으므로 서버 배포 확인이 Android 출시의 선행 조건이다. MATCH·댓글의 계정 일치 검사와 콘텐츠 표시를 추가하려면 해당 타입의 서버 계약 확장이 필요하다. [Server #67](https://github.com/KU-404-Finders/finders-server-api/pull/67) · [최신 FCM 발행 코드](https://github.com/KU-404-Finders/finders-server-api/blob/f68cafb2b4e50564cd179cd20432a5deb28c83fb/finder-external/src/main/java/com/finders/external/push/FcmPushSender.java)
- Server #66은 차단 POST와 GET의 `blockedUserName` 계약을 제공한다. GET은 현재 사용자 이름을 일괄 조회하고, 사용자가 없는 이전 기록은 탈퇴한 사용자로 표시한다. Android의 필드 처리는 소스와 합성 응답에서 확인했다. 운영 배포 및 인증된 실응답은 미검증이다. 최신 iOS `BlockReceipt`는 서버 이름을 읽지 않고 로컬 캐시로 이름을 표시하므로 교차 플랫폼 이름 표시에는 iOS 후속 작업이 남았다. [Server #66](https://github.com/KU-404-Finders/finders-server-api/pull/66) · [iOS 모델](https://github.com/KU-404-Finders/finders-ios/blob/665ff149e2f2c63beb4478b004eec7796043c5b0/Finders/Models/ModerationModels.swift)
- 서버의 게시글 생성 멱등성 계약과 저장 결과 조회 수단이 확인되지 않았다. Android가 현재 기기에서 재전송을 보류해도, 응답 유실 시 운영 DB의 생성 건수는 서버 증거가 필요하다. Open-Meteo 무료 API는 비상업 및 호출량 조건이 있다. 최신 공개 약관은 서비스를 무료 학생 프로젝트로 설명하고, 운영 사용 범위 및 호출량은 확인하지 못했다. [Open-Meteo 약관](https://open-meteo.com/en/terms)

## Android 수신·표시·이동 경계

- **IMPLEMENTED · 수신.** `FindersFirebaseMessagingService.kt`에서 CHAT 수신자를 ASCII 십진 양수 `Long`으로 파싱한다. 수신 시작의 세션 UUID를 보관하고, 발행 직전에 `TokenManager` 잠금 아래 현재 세션과 로그인 API에서 저장한 액세스 토큰의 숫자 `user_id`를 확인한다. 누락·빈 값·형식 오류·범위 초과·불일치·로그아웃·계정 ID 미확인은 알림·소리·진동·알림함 이벤트·탭 인텐트 생성 전에 폐기한다. 토큰 본문에서 세션 사용자 ID를 읽으며 JWT 서명 검증과 채팅방 접근 권한은 서버가 담당한다. FCM 데이터와 프로필 캐시는 계정 근거로 사용하지 않는다.
- **IMPLEMENTED · 세션 전환.** `TokenManager.kt`와 `FindersNotificationHelper.kt`에서 발행, 세션 교체·정리, 세션 만료가 같은 잠금을 공유한다. 원격 알림 채널의 활성 알림과 이 프로세스가 발행했으나 아직 활성 목록에 들어오지 않았을 수 있는 키를 취소한다. 알림의 PendingIntent도 취소한다. `MainNavGraph.kt`의 지연 UI 정리에서 원격 알림 취소를 제거해 새 계정의 알림을 뒤늦게 취소할 수 있는 경로를 없앴다. 예방 알림은 다른 채널을 사용한다.
- **IMPLEMENTED · 클릭과 복원.** `NotificationTarget.kt`와 `MainActivity.kt`에서 CHAT 클릭과 보류 요청 실행 전에 세션 UUID와 수신자 ID를 재확인한다. 세션별 PendingIntent 식별, 전달 ID 중복 제거, 저장 상태에서 소유자가 달라진 인증 목적지 초기화도 적용했다. 저장된 인증 상태에서 같은 세션의 UUID를 복원하며 새 로그인에는 새 UUID를 사용한다. 채팅방 조회와 서버 권한 검증은 기존 경로를 사용한다.
- **IMPLEMENTED · 미읽음 신호.** `NotificationInboxRefreshBus.kt`의 이벤트에 세션 UUID를 부여하고 `ChatViewModel.kt` 및 `NotificationViewModel.kt`에서 사용 직전 세션을 확인한다. 푸시는 미읽음 숫자를 전달하지 않는다. COMMENT·MATCH는 기존 일반 알림 정책을 따르며 제목·본문·게시글 이동 정보를 전달하지 않는다.
- 변경 테스트는 `UnverifiedPushRegressionTest.kt`, `EventSubscriptionLifecycleRegressionTest.kt`, `NotificationDeliveryTest.kt`, `ChatPushAccountSwitchTest.kt`, `ChatPushSessionRestoreTest.kt`다. 개인정보를 로그에 추가하지 않았다.

## iOS APNs 기술 검토

기존 iOS·Server 참조 결과를 재사용했다. iOS 앱에서 제어하는 포그라운드 표시와 탭은 수신자 비교로 보호할 수 있다. 백그라운드·종료 상태의 개인정보 노출을 막으려면 서버 원본 alert의 내용도 제한해야 한다. iOS 구현 및 APNs 종단 검증은 남아 있다.

- 현재 `FindersAppDelegate.willPresent`는 로그인 여부만 확인하고 `didReceive`는 목적지를 추출해 `NotificationRouter`에 대기시킨다. `AppSession`은 저장된 디바이스 전용 Keychain 자격 증명으로 `me`를 불러오고 인증된 사용자 프로필을 복원한다. 포그라운드 표시·미읽음 갱신·탭 및 대기 이동 전에 현재 사용자 ID와 CHAT 수신자를 비교하고, 계정 전환 시 대기 및 표시된 이전 알림을 정리한다. 채팅방 접근 확인은 서버 경로에서 유지한다. [Apple 알림 처리](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions)
- 현재 Server iOS CHAT은 `aps.alert`, 사운드·배지를 전달한다. `mutable-content` 설정과 iOS Notification Service Extension, 공유 App Group/Keychain entitlement는 해당 참조 소스에서 찾지 못했다. 시스템이 직접 보여 주는 배경·종료 알림은 앱 delegate 비교로 제어할 수 없다. 확장으로 표시 전 내용을 바꾸려면 `mutable-content: 1` 및 alert와 제한된 공유 상태가 필요하다. 전체 토큰 대신 필요한 계정 식별 상태만 App Group 또는 공유 Keychain에 안전하게 전달하고, 로그아웃 시 이를 폐기해야 한다. [APNs 페이로드](https://developer.apple.com/documentation/usernotifications/generating-a-remote-notification)
- 확장 미실행·시간 초과 시 원본 alert가 표시될 수 있다. 서버 원본에는 채팅 미리보기나 이전 계정의 배지처럼 노출될 수 있는 값을 넣지 않는 설계가 필요하다. 배경 전용 데이터 전달은 낮은 우선순위이고 지연·폐기가 가능하므로 즉시 푸시와 동등한 전달을 보장할 수 없다. 확장 단독으로 모든 알림을 음소거한다는 전제는 사용하지 않는다. iOS 실제 실행은 **NOT VERIFIED**다. [Apple 서비스 확장](https://developer.apple.com/documentation/usernotifications/modifying-content-in-newly-delivered-notifications) · [Apple 백그라운드 알림](https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app)

## 검증 증거

### 이번 변경 후 신규 실행

| 항목 | 상태와 근거 |
| --- | --- |
| JVM 전체 | **VERIFIED.** 317건 통과, 오류·실패·건너뜀 0건. 기존 314건에 CHAT 파싱·계정 전환·저장 세션의 추가 단언이 포함됐다. |
| 빌드 | **VERIFIED.** `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease`, `:app:bundleRelease` 성공. `git diff --check` 통과. |
| 산출물 | **VERIFIED.** 2026-11-27 KST에 생성된 Debug 20:48:49, Release APK 20:57:33, AAB 20:57:52. `bundletool validate`, ZIP 검사, APK/AAB DEX 일치, 16KB ZIP 및 네이티브 ELF 정렬을 확인했다. R8 매핑에 새 `publishRemotePush`와 인라인 코드가 있다. Manifest는 `1.1.2 / 9`, minSdk 26, targetSdk 36, 백그라운드 위치 권한과 FID 플래그를 포함한다. Debug는 로컬 v2 서명 검증에 성공했다. Release APK와 AAB는 운영 서명이 없는 검증용이다. |

최신 Release APK SHA-256 `75c8c5ee4edfdec0e948505697abad4fe6bc6652474d71f11b0bf55f77a026f2`, AAB SHA-256 `cbb3347c8310d4cc895abbc1b9c54bc4023ac631a280ccc9ca413e2057dfb88b`. 빌드 로그는 `build/push-recipient-final.log`다.

### 최종 FCM 계측 증거 재사용

`build/push-recipient-device-final-build.log`에서 `:app:assembleDebugAndroidTest` 성공을 확인했다. 최종 계측 APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`는 2026-11-27 20:59:11 KST에 생성됐다. SHA-256은 `be4ec293b233f9f087618a0a8964c7f9b85827077d2486e0ef54b2e86dfa3a56`이다.

| 요청 항목 | 기존 계측 결과 |
| --- | --- |
| 계정 전환 후 이전 계정 푸시 차단 | **PASS.** A에서 로그아웃하고 B로 로그인한 뒤 지연된 A 대상 CHAT을 주입했다. 원격 알림 목록에 B 대상 알림만 남았다. |
| 이전 계정 알림 클릭 차단 | **PASS.** A 알림의 `PendingIntent`가 취소됐고, 탐색 계측에서도 기존 로그인 세션이나 수신자가 다른 CHAT 탭을 거부했다. |
| 프로세스 재시작 후 인증 세션 복원 | **PASS.** 저장과 복원 단계 각각 1건 통과. 다른 PID에서 저장된 세션 UUID를 복원하고 수신자 불일치를 거부했다. |
| 현재 계정 대상 푸시 정상 수신 | **PASS.** B 대상 합성 CHAT이 시스템 알림 목록에 표시됐다. 세션 복원 후에도 현재 수신자에 한해 CHAT 발행 콜백이 한 번 실행됐다. |

`build/push-recipient-device-final.log`의 13건과 `build/push-recipient-process-seed.log`, `build/push-recipient-process-restore.log`의 두 단계가 모두 통과했다. 앱 수신 처리기에 합성 데이터를 주입한 검증이다. 이번 회차는 네 항목의 통과 로그를 재사용했으며, ADB에 연결된 기기가 없었다. 운영 Firebase 전달 및 실제 계정·실기기 검증은 **NOT VERIFIED**다. JVM 317건, Debug·Release·AAB 빌드, Lint, DEX 일치, 16KB 정렬도 기존 결과를 재사용했다.

### 선행 검증 재사용

| 검증 | 결과 |
| --- | --- |
| 이전 Android 회귀 증거 재사용 | JVM 314건, API 34 일반 계측 56건, R8 Release 전용 2건 통과. R8 프로브는 합성 응답으로 로그인·토큰 갱신·비밀번호 재설정·채팅방 목록·게시글 쓰기·차단 이름을 확인했다. 가입 완료·뒤로 가기는 계측의 테스트 데이터로 확인했다. |
| Android 16/API 36, 최종 Debug | 앱을 설치해 실행했다. 첫 계측에서 System UI 응답 없음 창이 포커스를 차지해 IME 대기 3건이 실패했다. 창을 닫고 로그인 비밀번호 필드가 키보드 위에 남는 것을 수동 확인했다. 동일 APK와 단언으로 회원가입, 채팅 입력·전송, 게시글 등록·수정 입력 계측 3건이 통과했다. |
| Android 16/API 36, 최종 Release | 동일 Release APK의 로컬 테스트 서명본을 설치했다. 로그인 비밀번호 필드의 하단은 1418px, 키보드 시작점은 1507px이었다. 키보드 활성·비활성 두 상태에서 필드가 시스템 안전 영역에 들어왔다. |
| 10인치 태블릿, Android 14/API 34 | 최종 Debug의 회원가입 이름 입력, 활성 IME 안전 영역, 하단 버튼 간격 테스트 1건 통과. 실제 계정 가입이나 다른 폼의 모든 화면 조합은 이 테스트의 범위 밖이다. |
| 출시 패키징 | 기존 R8 APK와 동일한 입력으로 현재 소스의 `:app:bundleRelease`와 `bundletool validate`가 성공했다. APK와 AAB의 DEX가 바이트 단위로 일치한다. 두 ZIP 무결성, AAB의 `PAGE_ALIGNMENT_16K`, APK의 16KB ZIP 정렬 및 모든 포함 네이티브 라이브러리의 16KB ELF 로드 세그먼트 정렬을 확인했다. 현재 AAB는 운영 업로드 서명이 없는 `1.1.2 / 9` 검증 산출물이다. [Android 16KB 호환 가이드](https://developer.android.com/guide/practices/page-sizes) |

선행 실행 로그는 `build/final-api36-tests.log`, `build/final-api36-manual-retry.log`, `build/final-api36-tests-retry.log`, `build/final-api36-release-smoke.log`, `build/final-tablet-tests.log`, `build/final-production-bundle.log`다. 표의 APK·AAB 검증은 CHAT 수신자 검증을 구현하기 전의 산출물에 해당한다.

### Lint 경고 74건 분류

오류 0건, 아래 경고 74건, 별도 힌트 6건을 원본 XML과 대조했다. 경고 목록에서 즉시 수정이 필요한 재현된 결함은 확인하지 못했다. 수신자 검증 차단은 별도 서버 계약·앱 동작에서 발견했다.

| 분류 | 건수 | 출시 판단 |
| --- | ---: | --- |
| KTX·빌드 표기·선언 정리 | 31 | `UseKtx` 20, 카탈로그 5, 빈 super 호출 3, Modifier 순서 2, 리소스 버전 디렉터리 1. 특히 저장 성공 여부를 사용하는 동기 커밋은 유지한다. |
| SDK·빌드 도구·라이브러리 버전 | 21 | 최신 의존성 알림 20, `OldTargetApi` 1. 현재 `targetSdk 36`은 휴대전화 앱 업데이트 기준을 충족한다. 일반 업데이트 알림 자체에 출시 차단 근거는 없다. [Google Play 대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878) |
| 리소스 정리·밀도 | 10 | 미사용 리소스 7, 밀도 폴더 3. |
| EXIF 구현 권고 | 6 | 작성·상세·썸네일의 플랫폼 EXIF 사용 3개 파일. 사진 방향 및 입력 형식 기기 검증과 함께 검토할 후속 항목이다. 경고 자체는 실기기 오류 증거가 아니다. |
| 테스트 경고 | 3 | `CheckResult` 2는 Mockito 인수 매처 호출, `FrequentlyChangingValue` 1은 계측용 탐색 상태다. |
| 컨텍스트 보관 | 1 | `PostWriteViewModel.journalContext`에 저장하는 값이 `applicationContext`임을 확인했다. |
| FCM 등록 콜백 | 1 | `MissingFirebaseInstanceTokenRefresh`는 토큰 콜백 `onNewToken`을 기대한다. 앱은 FID 모드와 `onRegistered`를 사용한다. 실제 회전·재설치에 따른 기기 재등록은 운영 종단 검증이 필요하다. [Firebase FID 등록 공식 가이드](https://firebase.google.com/docs/cloud-messaging/android/get-started) |
| Compose 창 크기 | 1 | 회원가입의 480dp 분기에 `ConfigurationScreenWidthHeight`가 뜬다. 이번 기기에서 입력 회귀는 재현되지 않았다. 분할 화면과 480dp 전후 창 크기 검증이 남아 있다. |

## 미검증

- Galaxy SM-S921N/API 37의 ADB 연결을 확인했다. 사용 중인 다른 앱의 포커스를 보존했고, 찾을건대가 전면에 오지 않아 이 회차의 화면·키보드 동작을 확인하지 못했다.
- 실서버 로그인, 신규 가입·인증 메일, 비밀번호 재설정 메일, 실제 채팅 송수신, 푸시 FID 갱신·로그아웃 직후 계정 전환, 실제 차단 POST·GET, 게시글 저장 건수, 캠퍼스 진입·체류·이탈은 미검증이다. 로컬 Retrofit/Gson·Compose·세션 회귀는 해당 종단 동작과 별도로 판정했다.
- 현재 브라우저 계정의 Play Console은 개발자 계정 설정 및 2단계 인증 안내 화면까지 열렸다. 앱의 권한 신고·승인, Data safety, 심사자 계정, 외부 계정 삭제 URL, 업로드 키, 버전 코드 사용 이력과 새 번들의 Edge-to-edge 재검사 결과는 미검증이다. Play가 제공하는 최종 분할 APK의 16KB 실기기 실행도 미검증이다.

### 개인정보 처리방침 및 권한 심사 전달 사항

- **확인한 데이터 흐름.** iOS는 WeatherKit, Android는 Open-Meteo에 캠퍼스 고정 좌표로 예보를 요청한다. Android 요청에 이용자 GPS 위치와 Finders 인증 정보는 포함되지 않는다. 기기의 위치로 건물 진입·이탈을 판정하고 선택한 건물, 체류 기록, 알림 설정을 기기에 저장한다. 앱 미사용 중에도 선택한 건물 이탈을 감지해 알림을 보내는 목적을 공개 문서와 스토어 설명에 명시한다. 외부 날씨 서비스의 IP 등 기술적 처리 가능성은 Finders 서버의 위치 처리와 구분한다. [외부 날씨 서비스 약관](https://open-meteo.com/en/terms)
- **운영팀 확인 질문.** 접속 로그에 IP·시각·기기 또는 요청 정보 중 무엇을 보관하며 어디에서 누가 접근하는가? 탈퇴 이후 데이터 범주별 삭제 시점, 채팅·신고 보관 기간과 근거는 무엇인가? 인증·비밀번호 재설정 메일을 실제로 위탁하는 업체는 어디이며 이메일이 어떻게 처리되는가? 각 외부 처리자의 항목과 보유 기간, 외부 계정 삭제 경로 및 현재 Data safety 답변을 확인해야 한다. 공개 Notion 페이지의 내용을 팀에 전달하는 범위로 정리했다.
- **심사 자료.** 위치 권한이 필요한 핵심 시나리오, 인앱 사전 공개에서 시스템 권한 선택까지, 앱 미사용 중 실제 건물 이탈 알림, 권한 거부·기능 OFF 경로를 영상과 심사자 안내로 준비한다. 매니페스트·인앱 설명·공개 문서·스토어 설명·Data safety를 대조한다. 캠퍼스 이동 증거와 승인 상태는 **NOT VERIFIED**다. [Google Play 백그라운드 위치 심사](https://support.google.com/googleplay/android-developer/answer/9799150)
- **Edge-to-edge.** 앱의 테마에서 지원 중단된 시스템 바 색상 속성을 제거한 상태다. 기존 감사에서 AndroidX 호환 DEX 참조는 남아 있었다. 새 AAB가 Play에 제출된 뒤 Console 경고 해소 여부를 확인한다.

### 사용자 직접 검증용 체크리스트

- [ ] 승인된 테스트 환경과 계정 A·B, 채팅 대상, Firebase 발송 수단, 배포 SHA를 준비한다. Android Galaxy에서 허락을 받은 뒤 서명이 호환되는 출시 후보를 설치하고 키보드·시스템 바·가입·로그인·로그아웃을 확인한다.
- [ ] A 대상 채팅 푸시를 지연시켜 B 로그인 뒤 전달한다. 알림·본문·소리·진동·미읽음 신호·채팅방 이동이 발생하지 않는지 확인한다. B 대상 푸시는 정상 표시·읽음 갱신·탭 후 정확한 방으로 이동하는지 확인한다. 운영 Firebase 증거와 합성 입력 증거를 따로 기록한다.
- [ ] 새 계정의 첫 분실물·습득물 등록과 응답 유실·반복 제출에서 서버 요청 및 DB 생성 건수를 확인한다. 게시글 수정·해결 완료, 실시간 채팅 및 읽음 표시, 신고의 관리자 반려를 확인한다.
- [ ] iOS 또는 다른 Android 기기에서 차단한 뒤 Android 차단 목록을 조회한다. 재설치, 서버 이름 누락, 탈퇴 이름, 계정별 캐시, 차단·해제 뒤 게시글과 채팅 목록을 확인한다.
- [ ] 캠퍼스 실기기에서 공개 안내, 정확한 위치 선택, 백그라운드 권한, 진입·체류·이탈 및 날씨 조건을 확인한다. 권한 거부와 기능 비활성화 시 배경 감시·알림이 시작되지 않는지 확인한다.

## 이전 출시 차단 사항 기록

1. **RELEASE BLOCKER · 운영 서명.** 과거 검증 번들은 `versionCode 9`, 코드 10·11은 업로드 완료, 현재 후보는 사용자 승인한 `1.2.0 / 12`다. 코드 12의 운영 업로드 키 서명과 Play Console 업로드는 출시 담당자가 진행한다. 기존 운영 업로드 키로 서명된 제출 후보의 검증도 남았다. [Google Play 앱 설정](https://support.google.com/googleplay/android-developer/answer/9859152)
2. **개인정보 처리방침과 Data safety.** 공개 페이지는 정상 열렸지만 날씨 제공자로 WeatherKit만 설명한다. Android는 Open-Meteo의 무료 엔드포인트에 캠퍼스 고정 좌표를 보내며, 제공자 약관은 기술적 목적의 IP 처리 가능성을 명시한다. 공개 문서에는 접속 로그, 탈퇴 후 채팅·신고 보관 기간, 인증 메일 업체에 관한 확인용 문구도 남아 있다. 실제 처리와 보관 정책을 확정해 문서 및 Console 답변에 일치시켜야 한다. [공개 개인정보 처리방침](https://cypress-monkey-570.notion.site/41129506e322803fafc1eae07ce4925c) · [Open-Meteo 약관](https://open-meteo.com/en/terms) · [Google Play 사용자 데이터 정책](https://support.google.com/googleplay/android-developer/answer/10144311)
3. **백그라운드 위치 심사.** 매니페스트에 `ACCESS_BACKGROUND_LOCATION`이 있고 기능 최초 활성화 때 사용 목적과 기기 내 처리를 안내한다. Google은 핵심 기능에 필요한 백그라운드 위치만 허용한다. 예방 알림이 기본 OFF이고 공개 약관상 앱의 주목적이 분실물 공유·연락이므로, 앱 미사용 상태에서 건물 이탈을 감지하는 기능이 핵심 목적에서 필수라는 근거와 심사 판단이 필요하다. Console 신고, 스토어 설명, 안내·권한 선택·백그라운드 실제 알림을 보여 주는 Android 영상, 접근 가능한 심사 계정, 권한 승인 및 개인정보·Data safety 정합성의 증거가 필요하다. [백그라운드 위치 심사 요구사항](https://support.google.com/googleplay/android-developer/answer/9799150) · [앱 심사 준비](https://support.google.com/googleplay/android-developer/answer/9859455) · [계정 삭제 외부 경로 요구사항](https://support.google.com/googleplay/android-developer/answer/13327111)
4. **RELEASE BLOCKER · 운영 CHAT 계약과 실환경 확인.** 새 CHAT 계약이 적용된 운영 배포를 먼저 확인하고, 실제 Firebase와 승인된 계정으로 지연 도착·계정 전환·정상 채팅방 이동을 확인한다. 갤럭시 출시 후보와 캠퍼스 권한·알림 종단 검증도 남아 있다.

버전 변경, 커밋, 푸시, PR 병합 및 프로덕션 배포는 수행하지 않았다.

## PR #18 리뷰 반영 (2026-11-28, 현재 작업 트리)

### 기준 소스

| 저장소 | 브랜치 | 전체 SHA | 동기화 |
| --- | --- | --- | --- |
| Android | `feature/ios-parity-stability` (PR #18 HEAD) | `f1a331ff4b7a367998373057b606f8e081842904` + 미커밋 변경 | `fetch --prune` 성공, 원격과 동일 |
| Android 기준 | `origin/develop` | `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a` | 작업 브랜치의 조상 |
| iOS | `main` | `665ff149e2f2c63beb4478b004eec7796043c5b0` | `fetch` + `pull --ff-only` (Already up to date) |
| Server | `main` | `f68cafb2b4e50564cd179cd20432a5deb28c83fb` | `fetch` + `pull --ff-only` (Already up to date) |

`gh` CLI는 설치되어 있지 않아 공개 GitHub REST API로 PR #18의 인라인 코멘트 13건과 리뷰 본문의 Nitpick 4건(접힌 영역 포함)을 조회했다.

### 등록 안정성

- 등록 전용 응답 `LostItemCreatedResponse`/`FoundItemCreatedResponse`(`success: Boolean?`)를 추가했다. 상세·수정·상태 변경은 기존 모델을 그대로 쓴다. `success=false`만 거절이고, 누락·`null`·`true`+데이터 누락은 미확정이다. 서버 계약(`ApiResponse`, 2xx는 `success=true`)은 Server `f68cafb` 소스로 확인했다.
- 인터셉터의 전송 전 세션 거절을 `RequestNotSentException`으로 구분한다. 이 경우 저널을 지우고 미확정으로 남기지 않는다. 그 외 전송 실패는 기존처럼 미확정이다. 클라이언트는 중복 전송만 막으며 서버 멱등성은 보장하지 않는다.
- `PostCreationJournal`: `commit()` 실패 시 메모리에 남은 기록을 제거하고 POST를 보내지 않는다. 삭제 실패를 기억해 같은 항목의 확인 시 디스크 삭제를 재시도하며 다른 UUID 항목은 지우지 않는다. 저널 쓰기는 I/O 디스패처에서 실행하고 객체 모니터로 직렬화한다. `commit()`을 유지했다. 성공 후 정리 실패는 1회 재시도하며 `Saved`를 되돌리지 않는다. 정리 실패 직후 프로세스가 종료되면 다음 실행에서 미확정 안내가 한 번 나타날 수 있다(재전송 없음).

### 분실물 예방 알림

- 취소된 추가 요청의 지오펜스 ID를 `orphan_fences`에 저장하고 삭제 성공 확인 후에만 지운다. 매 `reconcile()`과 프로세스 재생성 후 재시도한다.
- 현재 등록의 `GEOFENCE_NOT_AVAILABLE` 등 오류를 준비 상태 검사보다 먼저 처리해 체류 상태를 지우고 `LeaveReminderRestoreJob`을 예약한다(같은 소유자의 예약이 있으면 유지). 위치 서비스 꺼짐·Play 서비스·오류·시작 중 상태만 재시도하며 JobScheduler 지수 백오프(초기 60초)를 쓴다. 권한 누락, 기능 끔, 로그아웃, 계정 전환은 재시도하지 않고 예약을 취소한다.
- `onStopJob()`은 같은 계정에서 복구가 아직 필요할 때만 재예약을 요청한다.

### 알림 팝업 점검

실제 예방 알림과 테스트 알림은 모두 `finders_leave_reminders` 채널, `PRIORITY_HIGH`, `CATEGORY_REMINDER`를 쓰고 즉시 취소하지 않는다. 신규 설치는 `IMPORTANCE_HIGH`로 생성된다. 이전 빌드는 같은 채널을 `IMPORTANCE_DEFAULT`로 만들었고 Android는 기존 채널 중요도를 유지하므로, 이전 빌드에서 업데이트한 기기는 알림창에만 표시될 수 있다. 이것이 확인된 코드상 원인 후보다. 채널을 지우거나 ID를 바꾸지 않았고, 중요도가 HIGH 미만이면 예방 알림 화면에서 원인과 ‘팝업으로 표시’ 설정 방법을 안내하고 해당 채널 설정으로 이동한다. **갤럭시 실기기에서는 검증하지 못했다(NOT VERIFIED).**

### 이번 작업에서 새로 실행한 검증

| 항목 | 결과 |
| --- | --- |
| JVM 전체 (`testDebugUnitTest`) | 326건 통과, 실패 0 (신규: 등록 응답·전송 전 세션·저널 실패/재시도·403 비재전송 8건, 복구 판정 1건) |
| 계측 (Android API 37 `phone_360_800` 에뮬레이터) | LeaveReminderPermission, EdgeToEdgeInputFixture, RestoredEditNavigation, ParityAttribution, ParityInteraction, ProfileSupportContact, ChatTabHeader, SearchScreenLoading, AuthNavigationRegression 32건 통과. 신규 `UiParityCaptureTest` 4건 통과 |
| Release R8 | `assembleRelease` 성공. DEX에서 신규 등록 응답 클래스의 `success`(`java.lang.Boolean`)·`message`·`errors`·`data` 필드명 유지 확인 |

한 번의 재실행에서 `LeaveReminderPermissionTest` 첫 테스트가 멈춘 적이 있으며, 이후 깨끗한 데이터로 2회 재실행해 모두 통과했다. 재현되지 않은 불안정 사항으로 기록한다. 태블릿 AVD, 실기기, 실제 Firebase 푸시, 실제 위치 진입·이탈, 운영 서명 빌드는 이번에 실행하지 않았다.

## PR #18 리뷰 반영 2차 (2026-11-28)

### 추가 변경

- 관리자 화면 전체를 iOS `AdminViews.swift` 기준으로 정렬했다(대시보드 건수, 신고 상태 필터, 처리 메모, 회원 상세·이용 제한 사유, 게시글 관리, 작업 이력 라벨). 처리 메모와 사유는 기존 서버 DTO의 `note`/`reason`(최대 1000자)으로 전송한다.
- 채팅방 상단바에 iOS 부제와 쉐브런·가로 점 메뉴를 적용했다. 공통 확인 다이얼로그의 최대 폭을 440dp로 제한했다.
- 계측 테스트를 Android Test Orchestrator(`clearPackageData=true`)로 실행하도록 설정했다.

### 알림 권한 계측 테스트 격리 검증

- 격리 전 상태: 이전 실행 후 설치된 앱에 `POST_NOTIFICATIONS granted=true`가 남아 있었다. 주석만으로는 격리되지 않았다.
- `pm clear`가 해당 권한을 `granted=false`로 되돌리는 것을 확인했다.
- 오케스트레이터 적용 후 같은 실행에서 `LeaveReminderPermissionTest`(18:15) → `ChatPushAccountSwitchTest`(18:18) → `TestIsolationProbeTest`(18:22, 각각 다른 프로세스) 순서로 실행됐다. 프로브는 권한이 거부 상태이고 세션이 없음을 단언하며 통과했다.
- 두 단계 실행 테스트인 `ChatPushSessionRestoreTest`는 인자로 `am instrument`를 직접 두 번 실행하는 방식이라 영향을 받지 않는다.

### 실행 결과

| 항목 | 결과 |
|---|---|
| JVM 전체 | 327건 통과, 실패 0 |
| 계측 전체, `phone_360_800` API 37 콜드 부팅, Gradle+오케스트레이터 | 75건: 통과 71, 인자 필요 opt-in 3건(`ReleaseRetrofitProbeTest` 2, `ChatPushSessionRestoreTest` 1)은 assumption으로 실패 처리됨, `LeaveReminderPermissionTest` 미리보기 알림 1건 5초 대기 초과. 해당 클래스 단독 재실행 3회 모두 2/2 통과(Open-Meteo 조회를 포함하는 네트워크 의존 대기) |
| `EdgeToEdgeSignupTest` (API 37 폰) | 직전 에뮬레이터 세션에서 반복 실패. 같은 AVD에서 수정 전 HEAD(`f1a331f`)도 실패했다. 하드웨어 키보드 AVD(`hw.keyboard=yes`)의 IME 상태 문제로, 콜드 부팅 후 전체 실행에서는 통과 |
| `EdgeToEdgeInputFixtureTest`·`EdgeToEdgeSignupTest` 7인치 API 34 | 처음에는 3건 중 2건 통과(작성 화면 본문 입력란 6px 가림). 가로 화면 본문 높이 조정 후 3/3 통과(위 "최종 로컬 출시 후보 검증"과 `1.2.0 / 12` 산출물 절 참조) |
| 동일 10인치 API 34 | 3/3 통과 |
| `UiParityCaptureTest` | 폰·7인치·10인치 각각 11/11 통과, 스크린샷 45장 |
| Release R8 Gson 프로브 (`assembleRelease`, APK SHA-256 `9060f3bdc8f1b4470458145559bd97cdeddd456826d46bd1fe2a0ba0f666cfc8`, 7인치 API 34) | `OK (2 tests)`. 분실·습득 등록 응답의 `success`가 Release DEX에서 `true`/`false`/`null`(누락)로 역직렬화됨을 확인 |
| Debug 빌드, `git diff --check` | 성공, 출력 없음 |

### 현재 환경에서 확인할 수 없는 항목

운영 서명 빌드와 AAB, 운영 서버 CHAT `recipientUserId` 배포 및 실제 Firebase 푸시, 갤럭시 실기기 Heads-up 팝업, 실제 위치 기반 진입·이탈 알림, iOS 실제 화면 비교, Play 백그라운드 위치 심사.
