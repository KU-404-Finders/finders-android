# 찾을건대 Android v1.2.0 통합 검증 보고서

## 기준 소스

| 프로젝트 | 기준 브랜치 | 실제 참조한 전체 SHA |
| --- | --- | --- |
| Android | `origin/develop` → `feature/ios-parity-stability` | `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a` |
| iOS | `main` | `665ff149e2f2c63beb4478b004eec7796043c5b0` |
| Server | GitHub `main` | `e561a15c1a7a7e49579c775ee8d987ae3da85741` |

이전 통합 구현을 시작할 당시 세 로컬 경로는 모두 Git 저장소였으며 변경사항이 없었다. GitHub에서 세 원격 브랜치의 HEAD를 조회해 로컬 추적 커밋과 일치함을 확인했다. Android는 기존 `release/v1.1.2`를 그대로 두고 확인된 최신 `origin/develop`에서 작업 브랜치를 생성했다. 이 브랜치의 `git pull --ff-only origin develop`은 `Already up to date`였다. 별도 `fetch --prune` 호출은 Android SSH 연결 제한으로 실패했다. iOS와 Server는 기본 실행에서 저장소 소유자 검사를 받았고, 명령별 `safe.directory`로 재시도한 fetch/pull은 HTTPS 443 연결 제한으로 실패했다. 두 참고 저장소의 로컬 `main`과 `origin/main` 및 GitHub HEAD가 동일하여 해당 커밋의 로컬 소스를 읽었다. 참고 저장소 소스와 전역 Git 설정은 수정하지 않았다. 이후 작업 절차는 [AGENTS.md](../AGENTS.md)에 기록했다.

참고 저장소는 기존 참고 커밋을 보존했다. 후속 리뷰에서 Android는 작업 브랜치 HEAD와 GitHub `develop`이 표의 SHA에 일치했다. Android fetch는 SSH 22 연결 제한으로 실패했다. iOS와 Server는 명령별 `safe.directory` 적용 후에도 fetch와 fast-forward가 HTTPS 443 연결 제한으로 실패했다. GitHub 커넥터에서 각 원격 브랜치의 전체 SHA를 확인하고, Server 계약은 최신 SHA의 원격 파일 세 개를 직접 읽었다. 로컬 Server `main`은 `b57700f1dc9fa2450be94ce5c6629a1fa98a20f8`다. iOS 로컬 `main`은 GitHub와 일치한다. [Android develop](https://github.com/KU-404-Finders/finders-android/commit/8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a) · [iOS main](https://github.com/KU-404-Finders/finders-ios/commit/665ff149e2f2c63beb4478b004eec7796043c5b0) · [Server main](https://github.com/KU-404-Finders/finders-server-api/commit/e561a15c1a7a7e49579c775ee8d987ae3da85741)

## P0

### 게시글 중복 등록

최초 사용자 정보 조회를 기다리는 동안 등록 버튼과 등록 진입점이 열려 있었다. 반복 클릭마다 별도 코루틴이 시작되고 각각 작성 POST에 도달할 수 있었다. 동일 ID의 결과를 Android 캐시에 바로 이어 붙이는 별도 경로도 존재했다. 따라서 서로 다른 ID를 생성한 이중 쓰기와 한 ID의 중복 표시가 각각 소스상 가능했다. 최초 사용자 UI의 등록 재현을 모델링한 MockWebServer 테스트에서는 사용자 조회 전에 잠그고, 지연된 응답 동안 반복 실행해도 습득물 POST 한 건만 수신하는지를 확인한다. 제보 당시 서버 DB에 서로 다른 ID가 만들어졌는지는 테스트 계정의 실제 E2E와 DB 대조가 필요하다.

Server의 `FoundItemController`와 `LostItemController`는 각 작성 요청을 서비스의 `create`로 전달한다. `FoundItemService.create`와 `LostItemService.create`는 새 게시글을 저장하며 요청별 멱등성 키 계약이나 중복 저장 방지 조회가 없다. 클라이언트가 확인 가능한 범위에서, 카운트는 MockWebServer의 수신 요청 수로 보고하고 실제 서버 저장 건수는 별도로 확인한다.

- `PostWriteViewModel`, `PostWriteScreen`, `MainNavGraph`에서 사용자 조회 이전에 잠근다. ViewModel이 작성 작업과 완료 상태를 소유하고 현재 화면이 이를 소비한다. 화면 회전 중 요청이 새로 시작되거나 이전 화면으로 콜백이 들어가는 것을 막는다.
- `FoundItemApiService`, `LostItemApiService`, `RetrofitClient`에서 쓰기 요청을 원래 인증 세션에 묶는다. 만료된 토큰은 요청 전에 기존 단일 갱신 경로를 거친다. 변경 전 작성 경로는 인증 헤더를 직접 제공해 인터셉터의 자동 인증 갱신 재전송 대상에서 제외되어 있었다. 새 게시글 생성·수정·상태 변경용 호출은 연결 재시도, 리디렉션, 인증 응답의 자동 재전송을 사용하지 않는다. one-shot body는 `503 Retry-After: 0` 후속 전송도 차단한다. 다른 Retrofit 경로와 v1.1.2의 인증·Gson 보존 규칙을 유지한다.
- 작성 POST 후 전송/응답 변환 실패, HTTP 408·5xx 및 유효 게시글 ID를 얻지 못한 성공 응답에는 결과 미확인 상태를 표시한다. 작성 화면에서 내 게시글 조회로 이동해 확인할 수 있다. 재등록 허용은 사용자의 별도 동작이다.
- `CampusModels`, `MainNavGraph`에서 `PostType + id`로 병합한다. 같은 내용의 다른 ID와 서로 다른 유형의 같은 숫자 ID는 모두 유지한다. `FoundItemViewModel`과 `LostItemViewModel`에서 해결 완료의 동시 요청을 게시글과 세션 단위로 억제한다.

**Server 변경 검토 요청.** 응답을 잃은 후 동일 작업을 안전하게 재시도해야 한다면 두 multipart 생성 API에 인증 사용자·게시글 유형·작업 키 기반 멱등성을 추가한다. 동시 요청 선점과 게시글 ID 확정을 일관되게 기록하고, 같은 키로 다른 본문을 제출했을 때의 응답 및 보존 기간을 계약으로 확정한다. 초기 응답 유실 뒤에는 새 키 없이 기존 ID를 돌려주는 통합 테스트가 필요하다.

### 차단 사용자 이름

**Server 소스 계약 확인 및 Android 반영.** Server GitHub `main`의 차단 POST는 대상 사용자의 이름을 응답한다. 차단 목록 GET은 페이지별로 현재 이름을 조회하고 기록의 대상이 없으면 `탈퇴한 사용자`를 반환한다. 반복 차단·차단 해제, 기존 통신 제한 및 최신순 페이지당 50건 계약을 유지한다. [BlockResponse](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-common/src/main/java/com/finders/common/dto/report/BlockResponse.java) · [UserBlockService](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-domain/src/main/java/com/finders/domain/block/UserBlockService.java) · [UserBlockController](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-app/src/main/java/com/finders/app/report/UserBlockController.java)

Android는 서버 이름, 현재 계정 캐시, `사용자 #번호` 순으로 표시한다. 누락·null·공백 응답은 하위 호환 경로를 사용한다. 차단 POST 응답의 유효 이름은 현재 계정 캐시에 기록할 수 있으며, DELETE가 성공하면 해당 캐시를 지우고 목록을 0페이지부터 다시 읽는다. 코드 위치는 [ModerationApiService](../app/src/main/java/com/ku/lostandfound/network/ModerationApiService.kt#L13), [BlockedUserNames](../app/src/main/java/com/ku/lostandfound/data/BlockedUserNames.kt#L15), [BlockedUsersViewModel](../app/src/main/java/com/ku/lostandfound/ui/profile/viewmodel/BlockedUsersViewModel.kt#L66), [UserViewModel](../app/src/main/java/com/ku/lostandfound/ui/profile/viewmodel/UserViewModel.kt#L214)이다.

**iOS 별도 요청.** 최신 iOS `BlockReceipt`는 ID와 시각만 디코딩한다. 이름 누락·공백과 과거 응답을 지원하는 optional 이름을 도입하고, 차단 목록에서 서버 이름을 먼저 사용해야 한다. [iOS ModerationModels.swift](https://github.com/KU-404-Finders/finders-ios/blob/665ff149e2f2c63beb4478b004eec7796043c5b0/Finders/Models/ModerationModels.swift)

## P1

### 분실물 예방 알림

| 영역 | 구현 |
| --- | --- |
| 설정 | `LeaveReminderScreen`에서 마이페이지 진입, 즉시 저장, 건물 수, 체류 시간, 최대 12개 소지품 및 조건, 미리보기와 테스트 알림을 제공한다. 기본값은 15분, 우산·비 올 때 받기다. |
| 지오펜스 | `CampusJsonRepository`의 외곽선 데이터를 사용한다. iOS `LeaveReminderRegion`의 111,320m/도·위도별 경도 보정, 경계 상자의 중심, 최장 거리 + 40m, 50–250m 제한을 적용한다. 33개 중 최대 20개를 선택한다. 옥외 ID 33·34·36은 제외한다. Google Play Services의 ENTER/EXIT와 INITIAL_TRIGGER_ENTER를 사용한다. |
| 체류 | `LeaveReminderController`, `LeaveReminderStore`에서 동일 세션의 진입 기록, 마지막 이벤트 시각, 방문 UUID, 날씨 결과를 기기에 저장한다. 중복 ENTER는 첫 진입을 유지하고, EXIT는 기록을 한 번 소비한다. 설정 기준 이상 24시간 미만만 알린다. 선택 해제·기능 OFF·세션 종료에서 정리한다. |
| 생명주기 | `MainActivity` 복귀, 세션 변경, 재부팅·앱 업데이트 수신 시 등록 조건을 다시 확인한다. 프로세스만 재시작된 경우 같은 로그인 세션의 체류를 읽는다. 재부팅에서는 이전 체류의 연속성을 확인할 수 없어 이를 초기화하고 선택한 감시를 재등록한다. Session과 등록 ID가 다른 방송 및 방문 ID가 다른 날씨 응답은 적용하지 않는다. |
| 권한 | 활성화 동의에서 앱이 닫혔을 때의 위치 사용을 공개한다. 정확한 위치 → 백그라운드 위치를 단계별로 안내한다. Android 10은 별도 런타임 요청, Android 11 이상은 앱 설정의 시스템 권한 선택을 사용한다. Android 13 이상 알림 권한과 전용 채널도 확인한다. 상태에 권한 거부, 위치 서비스 OFF, Play 서비스 비가용 및 등록 실패가 표시된다. |
| 알림 | 전용 `finders_leave_reminders` 채널 및 알림 태그와 건물별 고정 ID를 사용한다. 알림 탭은 일반 앱을 연다. 알림 발송 시점에 로그인 세션을 재검사하며, 테스트 알림은 별도 ID와 실제 체류에 독립된 경로를 사용한다. 기존 FCM 알림과 딥링크를 그대로 사용한다. |
| 보관 | 설정은 기기 단위이며, 체류는 로그인 세션 소속이다. 백업과 기기 이전에서 제외한다. 위치·체류·소지품 데이터를 Finders Server에 보내지 않는다. 기기에 런타임 위치 권한이 있어도 연속 GPS 폴링과 포그라운드 서비스는 사용하지 않는다. |

날씨는 `CampusWeather`의 별도 HTTP 클라이언트가 건국대 고정 좌표 `37.5418, 127.0760`, `Asia/Seoul`만 보낸다. Finders 인증 헤더를 포함하지 않는다. 비 조건 물건이 있을 때 진입에서만 요청하며, 동시 진입 방송의 여러 건물은 한 조회를 공유한다. 실제 진입 시각을 포함하는 강수 구간과 해당 시각의 시간별 강수량 및 강수 확률을 확인한다. Open-Meteo는 시간별 누적 강수량(mm)과 확률(%)을 직전 한 시간 값으로 표시하므로 10시 25분은 11시 레코드를 읽고, 23시에는 다음 날 00시 레코드를 읽는다. 현재 강수의 누적 기간은 응답 시각 직전 `interval`초다. 실제 진입 시각이 이 범위에 있고 강수량이 양수이거나, 진입 시각의 시간별 강수량이 양수이며 확률이 50% 이상이면 RAIN이다. 8초 내 오류·불완전 응답은 UNKNOWN으로 처리하고 비 조건 물건을 제외한다. EXIT에서 재조회하지 않는다. [Open-Meteo Forecast API](https://open-meteo.com/en/docs)

Open-Meteo 무료 API는 비상업 용도에 허용되며 호출 한도는 일 10,000회 미만, 시간 5,000회 미만, 분 600회 미만이다. 제한은 앱 및 IP에 적용될 수 있다. 실습·졸업 시연을 비상업 사용으로 전제했다. 출처를 설정에 링크하고 날씨를 인용하는 확장 알림에도 표기했다. 운영 배포 전에 앱 용도와 호출량이 무료 조건을 충족하는지 운영 담당자가 확인한다. 유료 API 키를 추가하지 않았다. [Open-Meteo 이용 조건 및 개인정보](https://open-meteo.com/en/terms)

### 지도 및 마이페이지

- `CampusMapCanvas` 공용 레이어 좌측 하단에 접근 가능한 © OpenStreetMap 링크를 추가했다. 실제 호출은 `HomeScreen`의 습득물 게시판, `LocationPickerScreen`의 외부·내부 지도, `PostDetailScreen`의 분실물·습득물 위치 지도 등 5곳이다. 줌 컨트롤은 오른쪽 하단이다. [OpenStreetMap 저작권](https://www.openstreetmap.org/copyright)
- `ProfileScreen`은 가입 화면의 `LegalLinks`를 재사용하고 이용약관과 개인정보 처리방침 링크를 연다. URI 처리 실패 시 안내를 표시하며 문의하기를 유지한다. iOS `SupportLinks`에서도 동일 URL을 확인했다.
- 공개 처리방침 페이지 본문은 도구에서 열 수 없어 현재 설명과 실제 구현의 일치를 검증하지 못했다. 운영 담당자 확인 및 갱신 요청 내용은 사용자가 요청한 건물 이탈 알림을 위한 백그라운드 정밀 위치, 기기 내 체류·설정 보관과 정리, 고정 캠퍼스 좌표의 외부 날씨 조회와 해당 제공자의 IP 처리 가능성, 권한 변경·기능 해제 방법이다. 공개 문서는 변경하지 않았다. [현행 처리방침 링크](https://cypress-monkey-570.notion.site/41129506e322803fafc1eae07ce4925c)

## P2 · 최신 클라이언트 소스 비교

작업 시작 시 Android 기능 누락으로 확인한 항목은 분실물 예방 알림, 지도 출처, 마이페이지 약관 링크였다. 세 항목 모두 이번 구현에 포함했다. 아래 분류는 최신 소스와 이번 변경 이후를 기준으로 한다. 실제 동작이 필요한 행은 시연 체크리스트와 함께 확인한다.

| 화면·기능 | 분류 | 확인한 동작 및 근거 |
| --- | --- | --- |
| 로그인 및 회원가입 | 기능은 동일하나 UI 차이 존재 | Android `LoginScreen`, `Signup*Screen`과 iOS `AccountViews`에서 로그인, 필수 약관 동의, 이름·비밀번호·건국대 메일 인증 및 가입 흐름을 확인했다. Android는 NavHost 단계 전환, iOS는 가입 뷰 내부 단계 전환을 사용한다. |
| 비밀번호 재설정 | 기능은 동일하나 UI 차이 존재 | Android `ui/passwordreset`과 iOS `PasswordResetView`에서 인증 메일 요청, 코드 검증, 새 비밀번호 단계를 확인했다. 화면 전환과 입력 UI가 다르다. |
| 분실물 및 습득물 게시판 | 기능은 동일하나 UI 차이 존재 | Android `FoundBoardScreen`, `LostBoardScreen`, `HomeScreen`과 iOS `BoardView`에서 유형별 목록, 새로고침, 빈 목록 상태, 상세 진입을 확인했다. |
| 검색 및 카테고리 필터 | 실제 동작 검증 필요 | Android `SearchScreen`과 iOS `SearchView`에서 제목·분류·본문·유형·위치로 검색한다. 게시판 유형별 카테고리 필터는 양쪽에 있다. 요약 DTO만 내려오는 게시글의 본문·위치 검색 결과는 실제 표본으로 비교한다. |
| 캠퍼스 지도 | 기능은 동일하나 UI 차이 존재 | `CampusMapCanvas`와 `CampusMapView`는 건물 선택, 핀·경로, 줌·이동을 지원한다. 공통 출처 표기는 Android 이번 변경에 포함했다. |
| 게시글 등록·수정·상세 | 실제 동작 검증 필요 | Android `PostWriteScreen`, `PostDetailScreen`, iOS `ComposeView`, `ItemDetailView`에서 위치·사진 입력, 편집, 상세 및 해결 처리를 확인했다. 첫 작성 E2E와 DB 수신은 별도 검증이다. |
| 유사 게시글 추천 | 실제 동작 검증 필요 | Android 상세 추천 섹션과 iOS `ItemDetailView.matchSection`은 매칭 API, 결과 카드·이동, 유사도 표시 옵션을 지원한다. 다른 각도의 같은 물건과 비교 표본으로 점수와 MATCH 발송을 검증한다. |
| 댓글 등록·수정·삭제 | 기능은 동일하나 UI 차이 존재 | Android `PostCommentViewModel`·`PostDetailScreen`, iOS `ItemDetailView`·`APIClient`에서 작성 및 본인 댓글 수정·삭제를 확인했다. |
| 채팅 목록 | 실제 동작 검증 필요 | Android `ChatListScreen`, iOS `ChatListView`의 게시글 문맥·마지막 메시지·시간·미읽음 구성을 확인했다. 차단/해제와 목록 갱신은 양방향 E2E 대상이다. |
| 채팅방 | 실제 동작 검증 필요 | Android `ChatRoomScreen`·`ChatViewModel`, iOS `ChatConversationView`·`ChatClient`에서 서버 메시지, 실시간 연결, 게시글 이동을 확인했다. |
| 읽음 및 미읽음 표시 | 실제 동작 검증 필요 | Android `refreshReadState`·`markRead`, iOS `ChatClient.markVisibleRead`와 양쪽 메시지 UI에서 발신 메시지의 미읽음 수 갱신을 처리한다. |
| 알림함 | 플랫폼 특성상 필요한 차이 | Android `NotificationScreens`·`NotificationViewModel`, iOS `NotificationInboxView`에 읽음·삭제·알림 대상 이동이 있다. 시스템 푸시 진입 방식은 Android FCM 처리 및 iOS 알림 위임 방식에 따른다. |
| 알림 설정 | 기능은 동일하나 UI 차이 존재 | 양쪽 `NotificationSettingsView` 계열에 서버 설정과 유사도 임계값 조작이 있다. Compose와 SwiftUI가 각각 플랫폼 컨트롤을 사용한다. |
| 마이페이지 | 기능은 동일하나 UI 차이 존재 | Android `ProfileScreen`은 카드, iOS `ProfileView`는 설정 그룹을 사용한다. 예방 알림과 법적 링크를 Android에 연결했다. |
| 내 게시글 | 기능은 동일하나 UI 차이 존재 | Android `MyPostsScreen`·`UserViewModel), iOS `MyPostsView`는 유형별 내 글과 상세 진입을 제공한다. 응답 유실 시에도 내 목록에서 확인 가능하다. |
| 차단한 사용자 | 실제 동작 검증 필요 | Android `BlockedUsersViewModel`, iOS `BlockedUsersView`의 페이지·해제·기기 캐시를 확인했다. Server 이름 계약을 Android에 반영했다. iOS 표시 경로 갱신과 실기기 교차 검증이 남았다. |
| 신고 | 기능은 동일하나 UI 차이 존재 | Android 상세 신고 UI, iOS `ItemDetailView`와 `APIClient.report`에서 사유·상세를 제출한다. |
| 관리자 | 실제 동작 검증 필요 | Android `Admin*Screen` 및 탐색 그래프, iOS `AdminViews`에서 신고, 사용자 제한, 게시글 및 작업 이력 화면을 확인했다. 관리자 반려와 미처리 집계 감소는 실계정 검증 대상이다. |
| 회원탈퇴 | 실제 동작 검증 필요 | Android `LoginViewModel`·`ProfileScreen`, iOS `AppSession.withdraw`·`AccountViews`에서 사용자 확인과 서버 탈퇴 흐름을 확인했다. 로컬 예방 알림 세션 종료는 Android 탈퇴로 살아 있는 세션이 지워질 때 적용된다. |
| 문의하기 | 기능은 동일하나 UI 차이 존재 | 두 플랫폼의 공개 문의 메일 경로를 확인했다. Android는 주소 표시, 복사 및 메일 앱 연결을 제공한다. |
| 이용약관 및 개인정보 처리방침 | 동일 | Android 가입·마이페이지와 iOS `SupportLinks`의 URL이 일치한다. 마이페이지 링크는 Android 이번 변경에 포함했다. |
| 분실물 예방 알림 | 플랫폼 특성상 필요한 차이 | 핵심 설정·시간·소지품 흐름을 통합했다. Android는 Play Services와 Open-Meteo, iOS는 CoreLocation과 WeatherKit을 사용한다. 두 실제 기기의 지오펜스 지연은 현장 검증 대상이다. |

## 계측에서 추가로 확인한 알림함 회귀

`ParityInteractionTest.readThenRecomposeThenDeleteUsesLatestRowAndCallback`가 Android 15에서 한 번의 읽음 스와이프에 콜백 9회를 관측했다. `NotificationScreens.SwipeToHideNotification`이 드래그 중 여러 번 호출될 수 있는 `confirmValueChange`에서 읽음·삭제를 실행하고 있었다. 스와이프가 끝나 자리잡은 상태에서만 동작하고 행을 원위치시키도록 변경했다. 읽은 알림의 읽음 방향 제스처 제한과 최신 콜백 사용을 유지한다. [AndroidX SwipeToDismissBoxState](https://developer.android.com/reference/kotlin/androidx/compose/material3/SwipeToDismissBoxState)

## 이전 작업의 검증 기준선

| 분류 | 명령·항목 | 결과 |
| --- | --- | --- |
| Git | `git diff --check` | **PASS**. 신규 파일의 줄 끝 공백도 별도로 확인했다. |
| JVM 단위·로컬 HTTP | `gradlew.bat :app:testDebugUnitTest` | **PASS**, 295건, 실패 0건, 건너뜀 0건. |
| 정적 분석 | `gradlew.bat :app:lintDebug` | **PASS**, 오류 0건. 기존 인증 탐색 테스트의 `remember`가 Unit을 반환하던 부분을 등록·해제가 짝을 이루는 `DisposableEffect`로 바꿨다. 테스트 단언은 유지했다. |
| Debug APK | `gradlew.bat :app:assembleDebug` | **PASS**. |
| Android 계측 APK | `gradlew.bat :app:assembleDebugAndroidTest` | **PASS**, 빌드 결과. 실행 결과는 아래 행을 참조한다. |
| Android 계측 실행 | `gradlew.bat :app:connectedDebugAndroidTest` | **PASS**, Android 15(API 35) 에뮬레이터. 총 49개 중 일반 계측 48개 통과. Release 전용 1개는 실행 인자 미설정으로 이 실행에서 건너뛰고 아래 절차에서 실행했다. |
| Release APK | `gradlew.bat :app:assembleRelease` | **PASS**, R8 최적화 및 Release lint vital 완료. `app/build/outputs/apk/release/app-release-unsigned.apk`. |
| Release AAB | `gradlew.bat :app:bundleRelease` | **PASS**, `app/build/outputs/bundle/release/app-release.aab`. 운영 서명이 없는 로컬 산출물이다. |
| Release 역직렬화 실행 | `ReleaseRetrofitProbeTest`, `releaseProbe=after` | **PASS**, 최종 Release APK의 DEX에서 실행해 `OK (1 test)`를 확인했다. 로그인, 비밀번호 재설정, 채팅 목록 및 인증 갱신의 Retrofit/Gson 변환을 확인했다. |
| Android 재부팅 수신 | API 35 에뮬레이터 실제 재부팅, 로그아웃 상태 | **PASS**, 재부팅 시 시스템이 `LeaveReminderReceiver`용 프로세스를 실행했다. 로그아웃 상태에서는 활성 예방 알림이 없었다. 로그인 상태의 지오펜스 재등록과 캠퍼스 실측은 별도 검증 대상이다. |
| 실제 기기·Server·DB | 신규/기존 계정, P0 요청 및 저장 건수, Android↔iOS 실시간 채팅·푸시·차단, 캠퍼스 GPS, 관리자 처리 및 Grafana | **미실행**. 허용된 테스트 계정·환경, 실제 갤럭시·아이폰, 캠퍼스 이동 및 DB/Grafana 접근이 필요하다. |

JVM의 `PostSubmissionRegressionTest`는 MockWebServer에서 합성 신규 인증 세션의 첫 습득물 multipart POST와 첫 분실물 POST를 기록했다. 사용자 조회 대기, 응답 지연 중 연속 등록, 수정 중 중복 탭, 응답 끊김, 401·503 재전송, 취소 및 세션 변경, 복합 키를 검증했다. 차단 목록의 현행 JSON 역직렬화와 페이지네이션·차단 해제·계정 캐시, 기존 FCM·딥링크 단위 회귀도 포함한다. 서버 DB에 실제로 저장된 건수와 실네트워크의 사진 업로드 지연은 별도 확인 대상이다.

`LeaveReminderTest`는 iOS의 해당 SHA에 있는 캠퍼스 좌표와 Swift 계산식으로 독립 생성한 기준값을 Android 결과 33건과 비교했다. 중심 허용 오차는 1e-10도, 반경은 1e-6m다. 반복 ENTER/EXIT, 오래된 콜백, 체류 경계, 자정 포함 강수 구간, 로컬 HTTP 실패·타임아웃, 소지품 조건과 문구를 확인했다. 계측 소스에는 실제 Android assets 파서, 저장소를 다시 연 뒤의 체류/세션 분리, 기능 OFF 및 정밀 위치 권한 없음, OSM 접근성과 외부 링크, 법적 링크 오류 테스트를 포함한다. 이러한 검사는 캠퍼스 실제 GPS 경계 통과나 재부팅 직후 백그라운드 감지를 입증하지 않는다.

로컬 로그는 `build/parity-v120-final-verified.log`, `build/parity-v120-connected-final.log`, `build/parity-v120-release-probe.log`에 있다. APK/AAB의 ZIP 무결성과 DEX 존재 여부도 확인했다.

## 최종 코드 리뷰 후속 수정

| 발견 사항 | 기존 발생 조건과 원인 | 수정한 경로 | 추가 회귀 검증과 범위 |
| --- | --- | --- | --- |
| H-1 게시글 수정 복원 | `postEdit/{postId}`만 저장했다. 프로세스를 다시 열면 공용 작성 ViewModel의 기본값 FOUND가 복원 대상의 유형을 대신했다. | `Route.PostEdit`와 `MainNavGraph`의 수정·위치 선택 진입에서 유형과 양수 ID를 검증한다. `PostWriteViewModel.restoreEdit`는 원래 세션과 복합 키를 사용해 유형별 상세를 불러온다. 결과가 일치한 경우에만 PATCH를 허용하며 수정 결과의 ID와 유형도 확인한다. | `PostSubmissionRegressionTest`의 동번호 FOUND·LOST GET, 동일 ViewModel 재사용, 새 ViewModel 재조회, 잘못된 유형·ID·상세 불일치·404. `RestoredEditNavigationTest`의 Android Navigation 저장 상태 복원. 실제 계정의 OS 프로세스 종료 시나리오는 미실행이다. |
| H-2 재부팅 후 감시 복구 | 등록 Task를 3초 뒤 실패로 합쳤다. 부팅 수신기에서 한 번 요청한 뒤 수신기 작업을 끝냈으며, 늦은 성공과 계정·설정 변경을 같은 등록 작업이 구별하지 못했다. | `LeaveReminderReceiver.onReceive`, `LeaveReminderRestoreJob`, `LeaveReminderController.reconcile` 및 `RegistrationAttempt`에서 부팅 등록의 상태와 완료를 분리한다. 확인 전 작업을 기기에 표시하고, 계정·설정·권한·등록 요청이 바뀌면 해당 작업의 콜백과 요청 ID로 정리한다. | `LeaveReminderTest.registrationWaitDistinguishesDelayedCompletionActualFailureAndTimeout`에서 첫 대기 기한 뒤의 성공, 명시적 실패, 최종 타임아웃, 이후 성공 정리와 재시도 상한을 검증한다. 실제 BOOT 및 등록 결과는 아래 로그 표에 별도로 기록한다. |
| M-1 건물 선택과 체류 | 변경마다 콜백 전체를 교체하면서 유지된 건물의 체류 기록을 남겼다. 이전·현재 감시의 연속성을 보장할 수 없었다. | `LeaveReminderController.reconcile/handle`, `LeaveReminderStore.ReminderRegistration`에서 변경된 ID만 추가·제거한다. 그대로 유지된 요청에만 체류가 이어진다. 전체 복구, 연속성 미확인, 소유자 변경은 체류를 초기화한다. | `LeaveReminderTest.selectionChangesOnlyRecognizeRetainedAndCurrentRequestIds` 및 중복 ENTER/EXIT, 역순·이전 방문 테스트. 실제 건물 경계 이동은 미실행이다. |
| M-2 생성 결과 미확인 | 응답 유실 여부가 작성 ViewModel 메모리에만 있어 화면 이탈·초기화·프로세스 종료로 사라졌다. | `PostCreationJournal`과 `PostWriteViewModel.save`에서 요청 직전 계정 해시, 게시글 유형, 시도 UUID를 저장한다. 확정된 응답만 그 시도를 정리한다. `PostWriteScreen`의 내 게시글 확인과 사용자 확인 후 새 작성 동작을 유지한다. 저장 데이터는 기기 백업과 이전에서 제외한다. | `PostSubmissionRegressionTest`에서 MockWebServer의 응답 유실, 초기화·새 ViewModel, 전송 이후 취소, 계정 전환·복귀, 수동 해제 및 중복 요청 억제 확인. Server 멱등성 키가 없어 실제 저장 여부와 정확히 한 번 저장은 여기서 판정할 수 없다. |
| M-3 해결 응답 유실 | 진행 중인 요청을 내부에서만 억제했다. PATCH 응답을 잃거나 409가 와도 서버 상태를 다시 읽지 않았다. | `FoundItemViewModel.updateFoundItemStatus`, `LostItemViewModel.markReturned`, `ResolvePost.confirmPostResolution`에서 요청과 확인 GET에 원래 세션을 붙인다. `MainNavGraph`와 `PostDetailScreen`에서 진행 중 버튼을 잠그고, 확인된 해결 상태 또는 실패 이유를 표시한다. 이전에 시작한 상세 조회가 확인한 해결 상태를 덮어쓰지 않도록 한다. | `StatusChangeRefreshRegressionTest`의 FOUND 409·403, LOST 전송 오류·조회 실패, 잠금, 콜백·세션과 기존 목록 갱신 검증. 실제 Server에서 양쪽 유형을 해결하는 E2E는 미실행이다. |
| M-4 지연 진입 날씨 | 방송 처리 시각으로 현재 강수와 시간 레코드를 골랐다. 늦게 전달된 방송은 다른 시간대의 강수를 사용할 수 있었다. | `LeaveReminderController.handle`과 `CampusWeather.lookup/classify`에서 이벤트 진입 시각을 Asia/Seoul로 변환한다. 현재 강수의 직전 `interval`초 누적 구간에 진입 시각이 들어 있을 때만 그 값을 쓴다. 시간별 누적 강수·확률은 직전 한 시간의 종료 시각 레코드로 확인한다. 지난 하루의 레코드를 요청한다. 해당 시간 자료가 없으면 UNKNOWN이다. | `LeaveReminderTest`의 자정 경계 지연 방송, 누락된 시간·HTTP 오류·타임아웃, 지난 방문의 날씨 결과 거부. 기존 고정 캠퍼스 좌표와 별도 HTTP 클라이언트의 무인증 요청도 검사한다. [Open-Meteo Forecast API](https://open-meteo.com/en/docs) |
| M-5 예방 알림과 FCM | 새 일반 런처 인텐트가 대기 중인 FCM 목적지를 null로 덮었다. 인증 대기 중의 오래된 인텐트는 원래 로그인 세션과 계속 연결되지 않았다. | `MainActivity.queueNotification`, `PendingNotification.updatedFrom/forSession`, `FindersNotificationHelper`에서 유효한 탭만 대기에 반영한다. 앱이 만든 탭은 로그인 세션과 전달 ID를 붙이며, 소비된 전달 ID는 재생성에도 보존한다. 채팅·POST·MATCH의 기존 대상 탐색은 유지한다. | `NotificationDeliveryTest`에서 초기 실행, 인증 대기·세션 변경, 일반 예방 인텐트, 연속 탭, 잘못되거나 이미 소비된 전달을 검증한다. 실제 Firebase 양방향 푸시는 미실행이다. |
| L-1 재부팅 증거 | 이전 검증은 로그아웃 상태의 프로세스 실행까지 기록했다. 등록 Task의 성공·실패와 재시도 종료를 확인할 로그가 없었다. | `LeaveReminderReceiver`, `LeaveReminderRestoreJob`, `LeaveReminderController`에 BOOT 수신, 작업 예약 여부, 세션 유무, 시도와 결과 및 종료 상태를 기록한다. 사용자 식별자, 토큰과 좌표는 기록 대상에서 제외한다. | 아래 실기동 로그에 로그아웃·로컬 합성 로그인 상태를 구분한다. 실제 캠퍼스 GPS는 별도 확인 범위다. |
| L-2 Release 쓰기 경로 | 기존 Release 프로브는 로그인·비밀번호 재설정·채팅·갱신의 Retrofit/Gson 역직렬화를 확인했다. 신규 쓰기 요청은 Debug 테스트에서만 관측했다. | `ReleaseRetrofitProbeTest.releaseWireConversion`을 유지하고 `releasePostWritesAndBlockNames`를 추가했다. 최종 Release APK의 R8 DEX를 테스트 앱의 격리 로더에서 실행하고 합성 OkHttp 응답 전까지 실제 Retrofit 요청을 만들고 직렬화한다. | FOUND·LOST multipart POST와 PATCH, 실제 요청의 세션 태그, Gson 응답과 차단 POST·GET의 이름 하위 호환. 운영 서버에 요청을 보내거나 테스트 글을 저장하는 경로는 사용하지 않는다. |

재부팅 복구는 첫 실행 1회와 실패 시 10초, 30초 뒤의 재시도 2회로 제한했다. 각 시도는 첫 3초 동안 완료를 기다리고, Task가 아직 진행 중이면 최대 9초 더 기다린다. 그 뒤의 성공은 폐기된 시도의 지오펜스만 제거한다. Google Play 서비스 업데이트·일시 오류, 지오펜스 서비스 일시 불가·요청 과다 및 타임아웃만 복구 대상으로 삼는다. 기능 OFF, 인증 없음, 권한 부족, 위치·알림 설정 불충족, 확정 성공, 작업 스케줄러 중단과 시도 상한에서 종료한다. 스케줄러 지연은 기기 상태에 따라 달라질 수 있다. 방송 수신기는 등록 완료를 기다리지 않고 작업을 예약한다. [BroadcastReceiver](https://developer.android.com/reference/android/content/BroadcastReceiver) · [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler) · [Android Geofencing](https://developer.android.com/develop/sensors-and-location/location/geofencing) · [GeofencingClient](https://developers.google.com/android/reference/com/google/android/gms/location/GeofencingClient) · [GeofenceStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/location/GeofenceStatusCodes)

## 후속 작업의 최종 회귀 검증

| 검사 | 기준선 | 추가 | 최종 성공 | 실패 | 해당 실행에서 미실행 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `gradlew.bat :app:testDebugUnitTest` | 295 | 12 | 307 | 0 | 0 |
| `gradlew.bat :app:connectedDebugAndroidTest` 일반 계측, Android 17/API 37 에뮬레이터 | 48 | 3 | 51 | 0 | Release 전용 2건 |
| 최종 R8 Release APK, `ReleaseRetrofitProbeTest` 및 `releaseProbe=after` | 1 | 1 | 2 | 0 | 0 |

연결 계측 XML은 두 전용 프로브의 `AssumptionViolatedException`을 `failure` 태그로 내보낸다. 두 테스트는 전용 실행 인자가 없는 일반 계측에서 본문 실행을 건너뛰었으며, Release 전용 실행 결과를 위에 따로 집계했다. 기준선의 기존 단언을 유지했다.

| 명령 | 결과 |
| --- | --- |
| `git diff --check` | PASS. |
| `gradlew.bat :app:lintDebug` | PASS. 오류 0건. 저장 성공 여부를 사용해야 하는 SharedPreferences 동기 커밋에 대한 KTX 권고를 포함해 비차단 경고가 있다. |
| `gradlew.bat :app:assembleDebug` | PASS. |
| `gradlew.bat :app:assembleRelease` | PASS. R8과 Release lint vital을 실행했다. `app/build/outputs/apk/release/app-release-unsigned.apk`. |
| `gradlew.bat :app:bundleRelease` | PASS. `app/build/outputs/bundle/release/app-release.aab`. |

최종 로그는 `build/parity-review-final-recheck.log`와 `build/parity-review-final-release-probe.log`다. 테스트 환경에는 캐시된 Gradle 의존성과 에뮬레이터를 사용했다. Release 메타데이터는 `1.1.2 / 9`다.

최종 Release APK와 AAB는 ZIP 무결성과 DEX 존재를 확인했다. 프로브에 입력한 APK와 빌드 산출물의 SHA-256은 `70ed8fb5f9d6743e5962820de05f0ff485a30ed97d180660bace583120a63a05`로 일치했다. AAB의 SHA-256은 `17f0489d205884a4cf76fff1bc7416974ab9d7b9d2db4415a3539515615e650d`다.

| 발견 사항 | 상태 | 최종 재검토 및 실행 증거 |
| --- | --- | --- |
| H-1 | RESOLVED | 수정 진입과 위치 화면 복귀에서 복합 키 및 현재 세션을 사용한다. 단위 테스트의 유형별 재조회와 계측의 Navigation 저장 상태 복원이 통과했다. 실제 OS 프로세스 강제 종료 후 실계정 화면 복원은 NOT VERIFIED. |
| H-2 | RESOLVED | API 35 실재부팅 후 Google Play Services 등록 Task 성공 코드를 받고 `MONITORING`을 확인했다. 폐기 요청의 늦은 성공과 재시도 상한은 단위 테스트에서 확인했다. 실제 부팅에서 Google Play 서비스의 일시 실패와 재시도는 재현되지 않았다. |
| M-1 | RESOLVED | 유지되는 요청만 기존 체류를 이어받는다. 교체된 요청의 뒤늦은 이벤트, 중복·역순과 방문별 날씨 결과 단언이 통과했다. 실기기 이동과 중첩 경계는 NOT VERIFIED. |
| M-2 | RESOLVED | 제출 요청 이전 영구 표식, 응답 유실과 취소 이후 유지, 재생성, 계정 분리와 명시적 해제 테스트가 통과했다. 실제 DB 저장 건수는 NOT VERIFIED. |
| M-3 | RESOLVED | FOUND 409와 LOST 전송 오류는 세션을 지정한 상세 조회로 확인했고, FOUND 403과 LOST 상세 조회 실패는 성공 콜백을 만들지 않았다. 기존 양쪽 목록 갱신 테스트도 통과했다. |
| M-4 | RESOLVED | 실제 이벤트의 현지시각, 자정, 직전 누적 구간과 누락 자료를 재검토했다. 늦은 진입 및 비대상 강수 구간과 실패 응답 테스트가 통과했다. |
| M-5 | RESOLVED | 일반 인텐트가 대기를 보존하고 로그아웃·계정 교체 및 소비된 FCM 전달을 거부한다. 회귀 계측 2건이 통과했다. 실제 Android↔iOS 푸시는 NOT VERIFIED. |
| L-1 | RESOLVED | 실제 `BOOT_COMPLETED` 전달, 예약, 로그아웃의 등록 생략 및 로컬 합성 로그인 상태의 등록 성공과 종료를 각각 기록했다. |
| L-2 | RESOLVED | 최종 Release APK의 기존 Retrofit/Gson 테스트와 신규 쓰기·차단 이름 프로브가 모두 완료 상태 `0` 및 `OK (2 tests)`로 끝났다. |

| 실제 에뮬레이터 재부팅 | 결과 | 로컬 증거 |
| --- | --- | --- |
| API 37, 기능 ON 및 로그아웃 | PASS. `BOOT_COMPLETED` → 예약 → 시도 1의 `signedIn=false` → `SIGN_IN` 종료. 감시 추가와 재시도는 발생하지 않았다. | `build/parity-review-boot-signed-out.log` |
| API 35, 기능 ON 및 로컬 합성 로그인 | PASS. `BOOT_COMPLETED` → 예약 → 시도 1의 `signedIn=true` → Google Play Services `registration result=0` → `MONITORING` 종료. 재시도는 발생하지 않았다. | `build/parity-review-boot-api35-synthetic-account.log` |

API 37의 로그인 시나리오에서 재부팅 후 ADB 전송이 끊겨 등록 여부를 확인할 수 없었다. 별도 API 35 에뮬레이터는 초기 부팅이 첫 대기 한도를 넘겼다. 부팅이 완료된 뒤 최종 Debug APK를 설치하고 활성 설정과 합성 세션을 구성해 실제 재부팅을 다시 실행했다. 합성 세션 검증은 기기의 등록 복구를 확인하며 실제 인증 요청, 캠퍼스 GPS 경계 이동, 운영 DB 및 Android↔iOS 실기기 동작은 NOT VERIFIED다.

| 차단 사용자 이름 확인 범위 | 상태 | 근거 |
| --- | --- | --- |
| 최신 Server POST·GET 소스 계약 | RESOLVED | GitHub `main`의 DTO, 컨트롤러, 서비스에서 두 응답과 탈퇴 이름을 확인했다. |
| Android 구현 및 자동화 | RESOLVED | 기존 이름 누락, null·공백, 서버 이름 우선과 탈퇴 이름, 계정 분리·로그아웃, 페이지 상태와 기존 차단 해제 회귀를 실행했다. R8에서 차단 POST 및 GET 응답과 누락된 필드를 실행했다. |
| 실제 Server 배포 및 응답 | NOT VERIFIED | 운영·테스트 Server의 배포 상태와 인증된 POST·GET 실응답을 확인할 수 있는 테스트 계정이 없었다. |
| iOS 이름 표시 | BLOCKED | 최신 iOS 소스의 `BlockReceipt`와 목록 표시가 optional 서버 이름을 사용하도록 후속 수정이 필요하다. |
| iOS→Android, Android→iOS, 앱 재설치 | NOT VERIFIED | 실기기와 배포된 응답으로 수행하지 않았다. |

| 준비도 | 결과 |
| --- | --- |
| 로컬 커밋 | 준비됨. 현재 작업 트리의 변경을 보존했다. |
| Android PR | 검토 준비됨. 실제 서버 응답, 교차 플랫폼 및 현장 테스트의 미검증 범위를 포함한다. |
| 실제 출시 | 미준비. 실제 배포 응답, 실기기·캠퍼스 검증, Play 출시 버전과 운영 서명이 남았다. |

커밋, 푸시, PR 생성·병합, 배포는 수행하지 않았다.

## 최종 독립 리뷰 집중 수정

Android `develop` 및 현재 작업 브랜치의 기준 SHA는 `8fbb680fb8fd74f1818019e2fc06a0bbaab0da8a`, iOS `main`은 `665ff149e2f2c63beb4478b004eec7796043c5b0`, GitHub Server `main`은 `e561a15c1a7a7e49579c775ee8d987ae3da85741`다. GitHub 브랜치와 대상 파일의 SHA를 재확인했다. Android 로컬 fetch는 SSH 22 연결 제한, iOS·Server 로컬 fetch 및 fast-forward는 HTTPS 443 연결 제한으로 실패했다. Server 로컬 `main`은 이전 `b57700f1dc9fa2450be94ce5c6629a1fa98a20f8`에 머물러 실제 계약은 GitHub의 고정 SHA에서 읽었다.

| 발견 사항 | 원인과 집중 수정 | 회귀 검증 및 한계 |
| --- | --- | --- |
| H-1 · RESOLVED | 토큰 만료 때 탐색 키를 바꾸면서 수정 대상을 버렸다. `MainActivity.onSaveInstanceState`, `PostEditRestore`, `MainNavGraph`에서 게시글 유형·ID와 로그인 세션의 불투명 ID를 함께 저장한다. 토큰이 만료돼 탐색을 새로 시작하는 경우 같은 세션의 인증이 성공해야 대상을 다시 연다. 실패·로그아웃·계정 전환 시 폐기한다. 위치 선택기가 열려 있으면 그 아래 수정 대상을 저장한다. 수정 화면의 유형별 상세 조회와 기존 ID 검증은 유지한다. | `PostSubmissionRegressionTest`는 MockWebServer에서 실제 Retrofit 토큰 재발급 POST와 갱신 헤더를 사용한 LOST 상세 GET을 확인한다. 실패·다른 로그인 세션은 복원을 거부한다. `RestoredEditNavigationTest`에서 저장 상태의 세션·FOUND/LOST 복합 키를 검증한다. 실제 계정의 OS 프로세스 종료·화면 복원은 NOT VERIFIED다. |
| H-2 · RESOLVED | 대기 중인 새 콜백의 오류 방송을 버리면서 늦게 오는 성공이 `MONITORING`을 확정할 수 있었다. `LeaveReminderController.handle`은 현재 콜백과 소유자가 일치할 때 `GEOFENCE_NOT_AVAILABLE`을 진행 중인 `RegistrationAttempt`에 전달한다. 성공 Task가 먼저 완료되더라도 확정 전에 오류를 관측하면 오류가 우선한다. 확정 이후의 등록 오류는 기존대로 감시를 무효화하며 다음 갱신에서 복구한다. 부팅 작업의 첫 시도와 재시도 2회 상한, 오래된 등록의 제거와 세션·권한 재검증을 유지한다. | `LeaveReminderTest`에서 오류 선행 및 Task 성공 선행을 모두 검증했다. 이전 에뮬레이터 부팅에서 등록 성공을 관측했다. 오류 방송과 Task 경쟁의 실기기 재현 및 실제 부팅 중 일시 장애는 NOT VERIFIED다. |
| H-3 · PARTIAL | 최신 Server의 CHAT·MATCH·댓글 Android FCM 데이터에는 `recipientUserId`가 없다. `FindersFirebaseMessagingService`는 알려진 유형의 기존 FCM에서 서버 제목·본문·딥링크를 사용하지 않고 고정 문구의 일반 알림을 띄운다. 발행 직전 토큰 관리자에서 동일 세션을 확인한다. 로그아웃 화면으로 전환 시 FCM 채널 알림을 제거하며 로컬 예방 알림 채널은 유지한다. | `UnverifiedPushRegressionTest`에서 수신자 정보 누락·잘못된 값·신뢰할 수 없는 값을 가진 데이터가 고정 문구만 사용하고, 발행 전 로그아웃·계정 전환이 이전 세션의 발행을 막는지 확인한다. 수신자 일치 확인과 원격 알림의 개별 딥링크·미리보기 재개는 Server 계약에 의존한다. 실제 Android·iOS 푸시와 Server 배포는 NOT VERIFIED다. [Server FCM 발송 코드](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-external/src/main/java/com/finders/external/push/FcmPushSender.java) |
| M-1 · RESOLVED | Play Services 제거를 요청하기 전에 로컬 기록에서 요청 ID를 잃었다. `LeaveReminderStore`가 기존 등록 레코드와 제거 대상을 유지하고, 플랫폼 성공 뒤 등록 변경과 제거 표식 정리를 한 번의 커밋으로 처리한다. 프로세스 재생성·중단 이후 다음 동기화에서 같은 요청 ID로 이어간다. 같은 프로세스의 중복 요청은 ID별 Task를 재사용한다. 완료가 없는 Task는 발행 후 12초부터 같은 ID로 다시 시도할 수 있으며, 교체된 Task의 늦은 응답은 완료 상태를 덮어쓸 수 없다. 전체 해제에서도 콜백 취소 후 저장된 ID를 지운다. 유지되는 건물의 방문 기록과 콜백은 그대로 사용한다. | `LeaveReminderPersistenceTest`에서 저장소 재개방, 중복 제거 표식, 완료 전 요청 ID 보관, 완료 후 확정 및 유지 건물 방문 기록을 확인한다. `LeaveReminderTest`에서 중복·겹치는 ID의 제거, 제거 실패·시간 초과·취소·늦은 완료를 구분한다. 실제 건물 이동과 플랫폼 제거 대기 중 물리 기기의 프로세스 종료는 NOT VERIFIED다. |
| M-2 · RESOLVED | JWT 이메일 `sub`를 키로 써 동일 이메일의 신규 가입에 이전 기록이 적용될 수 있었다. `TokenManager.tokenUserId` 및 `PostCreationJournal.account`가 최신 JWT의 양수 정수 `user_id`를 사용한다. `/api/v1/users/me`도 숫자 사용자 ID를 제공한다. 이메일 해시와 옛 세션 키는 소유자를 입증할 수 없어 새 키로 옮기지 않고 유효한 계정의 기록 조회 때 제거한다. ID를 확인할 수 없으면 사용자에게 안내하고 새 POST를 보내지 않는다. | `PostSubmissionRegressionTest`에서 같은 ID의 재로그인, 이메일 변경, 동일 이메일의 새 ID, 기존 키 삭제, 응답 유실·중복 발송 방지를 검증한다. 이전 키에만 존재했던 미확인 기록의 서버 저장 여부는 알 수 없다. [Server JWT](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-app/src/main/java/com/finders/app/auth/jwt/JwtProvider.java#L68) · [사용자 정보 응답](https://github.com/KU-404-Finders/finders-server-api/blob/e561a15c1a7a7e49579c775ee8d987ae3da85741/finder-common/src/main/java/com/finders/common/dto/user/UserMeResponse.java) |

이전 M-3·M-4·L-2와 차단 이름 계약의 동작은 기존 회귀 대상으로 유지한다. 현장 GPS, 운영 DB, 실제 Server 배포 및 Android↔iOS 실기기 검증은 이번 범위에서 실행하지 않았다.

### 집중 수정 최종 검증 결과

| 검증 | 이전 기준선 | 추가 | 최종 통과 | 실패 | 해당 실행에서 제외 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 전체 JVM 단위 테스트 | 307 | 7 | 314 | 0 | 0 |
| 일반 Android 계측, API 37 에뮬레이터 | 51 | 2 | 53 | 0 | Release 전용 2건 |
| 최종 R8 Release APK 전용 프로브 | 2 | 0 | 2 | 0 | 0 |

집중 JVM 검사 51건과 마지막 제거 전용 JVM 검사 12건을 각각 먼저 통과했다. 첫 전체 계측은 문의하기의 클립보드·뒤로 가기 테스트 2건이 실패했고, 기기의 위치 권한 전제에 해당하는 테스트 1건과 Release 전용 2건이 건너뛰어졌다. 에뮬레이터에서 System UI 응답 없음 창이 입력 포커스를 차지한 상태를 확인해 포커스를 복구했다. 기존 테스트 코드와 단언을 유지한 최종 전체 계측에서는 일반 테스트 53건이 통과했다. 초기 실패 로그는 `build/parity-focus-first-connected-results/`에 보존했다.

`git diff --check`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:connectedDebugAndroidTest`, `:app:assembleRelease`, `:app:bundleRelease`가 통과했다. 최종 Lint는 오류 0건과 경고 74건이었다. 기준선 71건에서 증가한 3건은 제거 시작·완료의 SharedPreferences 편집 2곳과 기존 작성 키 삭제 1곳의 `UseKtx` 권고다. 동기 커밋의 성공 확인과 기존 `applicationContext` 사용을 유지했으며 전역 억제를 추가하지 않았다. 최종 빌드 로그는 `build/parity-focus-final-recheck.log`, Release 실행 로그는 `build/parity-focus-final-release-probe.log`다.

최종 Release APK의 SHA-256 `6eef9bd1ad14922b675045251aa7ebd8566585b844fff9b373e1d67db94d836e`가 프로브 입력과 일치했다. Release AAB의 SHA-256은 `4ac81aacb72f957c201ad85f954f8543c9f36c2c58f061dd1cdead2171edc2a0`다. 두 산출물의 ZIP 무결성과 DEX를 확인했다. 빌드 메타데이터 `1.1.2 / 9`를 유지했다. 커밋, 푸시, PR 생성·병합, 배포는 하지 않았다.

## 최종 시연 준비 체크리스트

실계정과 기기를 지정한 뒤 A 갤럭시, B 아이폰, C 관리자 계정, 노트북 Grafana 화면에서 확인한다. 게시글 ID, 처리 상태, HTTP 횟수와 발생 시각만 남기고 토큰과 개인 위치는 기록하지 않는다.

- [ ] 신규 회원의 첫 습득물·분실물 및 기존 회원 등록, 빠른 반복 클릭, 응답 유실 뒤 게시판 새로고침과 내 게시글을 확인한다. API 요청 횟수와 DB에서 생성된 ID 수를 비교한다. 두 유형의 수정과 해결 완료를 확인한다.
- [ ] 같은 물건의 다른 각도 사진과 비교 물건을 준비한다. 유사도 점수, 추천 게시글 이동, MATCH 알림을 확인한다.
- [ ] A가 추천 게시글에서 B에게 ‘검은 지갑 안에 학생증이 있나요?’를 보낸 뒤 B의 즉시 수신·응답과 A의 수신을 확인한다. 읽음 후 숫자 1가 사라지는지, 채팅 목록에 제목·마지막 메시지·시각이 표시되는지 확인한다.
- [ ] A→B와 B→A 각각 수신 앱을 백그라운드로 이동하고 발송한다. 시스템 푸시가 표시되고 탭할 때 정확한 채팅방이 열리는지 확인한다.
- [ ] 캠퍼스 실기기에서 건물 진입부터 실제 최소 체류 시간과 이탈을 확인한다. 진입 날씨 판정 및 로컬 알림을 확인한다. 시연 시간이 부족하면 실제 움직임과 알림 영상을 사전에 녹화한다.
- [ ] 테스트 게시글 신고와 작성자 차단 후 게시판 숨김, 기존 대화 보존 및 새 통신 제한, 차단 해제·갱신을 확인한다. 실제 Server의 신규 응답과 iOS 이름 지원을 확인한 후 iOS→Android, Android→iOS 및 앱 재설치에서 차단 이름을 비교한다.
- [ ] C로 신고 상세를 조회하고 반려한다. 처리 완료 상태, 미처리 수 감소와 Grafana 지표 및 로그를 확인한다.

## Google Play 준비와 남은 제한

- 기존 `release/v1.1.2` 라인과 위치 권한이 포함된 신규 기능 브랜치를 분리했다. Play Console에 접근하여 code 10의 이력을 확인할 수 없었으므로 Gradle의 `versionName = "1.1.2"`, `versionCode = 9`는 수정하지 않았다. `1.2.0 / 10`은 확인 후 적용할 후보이며 이번 빌드의 메타데이터는 현재 브랜치 설정을 따른다.
- 백그라운드 위치 신고에서 실제 기능 경로, 앱이 닫혀 있을 때의 최소 권한 사용, 최초 공개 및 동의 안내를 설명한다. 공개 개인정보 처리방침 및 데이터 보안 답변을 검토하고 권한 선택과 실제 알림을 담은 심사용 영상이 필요하다. 승인 이후 운영 릴리스를 진행한다. [Android 백그라운드 위치 권한](https://developer.android.com/develop/sensors-and-location/location/permissions/background) · [Google Play 백그라운드 위치 정책](https://support.google.com/googleplay/android-developer/answer/9799150)
- 지오펜스는 인접 건물 겹침, GPS·Wi-Fi 정확도, OS 도착 지연, Play Services 가용 여부의 영향을 받는다. 앱을 강제 종료하면 다시 실행해야 한다. 24시간에 걸친 stale 체류는 알림을 만들지 않는다. 권한을 철회했을 때 어플리케이션은 다음 복귀에서 상태를 재확인한다. [Android Geofencing](https://developer.android.com/develop/sensors-and-location/location/geofencing)
- 작성 결과를 서버에서 조회할 멱등성 키가 없으므로 앱이 응답 유실이나 프로세스 강제 종료 후 특정 생성 요청의 저장 여부를 자동으로 판정할 수 없다. 내 게시글과 서버 DB를 통해 검증한다.
- 프로덕션 배포, 원격 푸시와 PR 병합은 진행하지 않았다.
