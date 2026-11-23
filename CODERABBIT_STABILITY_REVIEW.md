# 찾을건대 Android 안정성 검증 기록

작업 브랜치는 `fix/coderabbit-stability`이며 시작 커밋은 `f477274`이다. 작업 시작 시 원격 develop의 HEAD가 이 커밋과 일치했고 작업 트리는 깨끗했다. [PR #10 리뷰](https://github.com/KU-404-Finders/finders-android/pull/10)와 해당 커밋의 Android 코드를 대조했다.

## 항목별 판정

아래 파일명은 Android 앱의 Kotlin 소스를 가리킨다. 페이지 계약은 같은 작업 환경의 서버 소스에서 읽기 전용으로 확인했다.

| 항목 | 시작 코드에서 확인한 상태 | 변경 파일과 결과 |
|---|---|---|
| 1. FCM 기기 등록 | 로그아웃 시 등록 취소, 명시적 인증 헤더 해제, 최대 세 번의 재시도는 이미 있었다. 등록 Job과 성공 표시는 여러 스레드가 별도로 변경했다. | `PushDeviceManager.kt`의 `PushRegistration`이 세션 세대와 설치 ID로 소유자를 정한다. 세션 잠금 안에서만 Job을 교체한다. 네트워크 Mutex 안에서는 시도마다 소유권을 검사하고 성공 기록도 재검증한다. `PushDeviceApiService.kt`의 Retrofit 태그는 HTTP 본문을 바꾸지 않는다. 로그아웃은 캡처한 인증 헤더로 해제하며 탈퇴 실패로 세션이 유지되면 다시 동기화한다. |
| 2. 인증 만료 | 갱신 결과의 세대 확인, 일시적 네트워크 오류 구분, 만료 이벤트 replay는 이미 있었다. 검사와 삭제가 분리돼 있었고 중복 만료가 세대를 다시 올렸다. | `TokenManager.kt`는 요청 시점의 세대와 두 토큰을 비교한다. 일치하는 요청만 같은 잠금 안에서 삭제·세대 증가·이벤트 발행을 수행한다. `RetrofitClient.kt`에서 시작 시 갱신과 인터셉터 갱신이 같은 요청 결과를 공유한다. HTTP 401·403은 상태 코드로 만료시킨다. HTTP 400은 JSON 불리언 success=false와 아래 두 가지 토큰 거절 문구 중 하나가 문자열로 정확히 일치할 때만 만료시키며, 일반 HTTP 400과 일시적 오류에서는 세션을 보존한다. |
| 3. 알림함과 설정 | 로컬 숨기기·스와이프·슬라이더와 정규화는 있었다. 계정 연결 전 조회와 각종 응답·열린 알림함의 푸시 갱신은 경합했다. | `NotificationViewModel.kt`, `NotificationScreens.kt`, `FindersFirebaseMessagingService.kt`, `MainNavGraph.kt`가 계정 연결 및 화면 열림에 맞춰 조회한다. 계정 전환 시 목록·설정·배지·오류·페이지 상태를 초기화한다. 설정 요청은 대기 후와 응답 후 소유자를 확인한다. 읽음 실패를 알린 뒤 같은 화면·계정이면 채팅방을 연다. Release에서 Gson이 PATCH 필드명을 유지하도록 `app/proguard-rules.pro`를 수정했다. |
| 4. 채팅방 나가기 | 방 전체에 공통 요청 번호를 써 다른 방의 완료가 무시됐다. 기존 목록이 응답하면 방이 다시 나타날 수 있었다. | `ChatViewModel.kt`에서 방별 요청을 관리하고 완료 후 목록을 다시 조회한다. 새 목록 요청이 이전 응답을 무효화한다. `MainNavGraph.kt`는 실패를 Toast로 표시하고 시작한 backStackEntry만 닫는다. 서버의 최신 목록에 방이 다시 나타나면 표시한다. |
| 5. 작성자와 시간 | 일부 작성자 조회의 영구적인 시도 표시가 재진입 재시도도 막았다. 잘못된 시간 값이 섞이면 비교 기준이 달라졌다. | `ChatViewModel.kt`는 진행 중인 요청과 성공 캐시를 구분하고 이전 세션의 finally를 차단한다. `ChatTimeFormatter.kt`는 오프셋 또는 기존 KST 로컬 시간을 Instant로 비교하고 파싱 실패 값에는 같은 최저 순위를 부여한다. |
| 6. 게시판 목록 | 분실물 요청의 최신성 검사가 없었다. 습득물은 요청 번호가 있었으나 실패 경로와 로딩 가드가 필터 전환을 방해했다. | `LostItemViewModel.kt`, `FoundItemViewModel.kt`, `FoundBoardScreen.kt`, `MainNavGraph.kt`에서 성공·실패 모두 요청 번호와 세대를 확인한다. 목록 상태를 상세 로딩과 분리하고 선택 건물을 ViewModel 한 곳에서 관리한다. 전체보기와 새 건물 선택은 이전 응답을 무효화한다. 코루틴 취소는 다시 던진다. |
| 7. 전체 검색 | 기존 검색은 두 게시판에 미리 적재된 목록에 의존했다. | `MainNavGraph.kt`, `SearchScreen.kt`에서 분실물과 전체 습득물 조회를 보장한다. 전체 목록과 진행 중인 조회를 재사용한다. 건물 선택 중에는 검색 화면 소유의 습득물 ViewModel을 사용한다. 같은 숫자 ID의 습득물과 분실물을 구분하며 로딩·오류·결과 없음도 구분한다. |
| 8. 댓글 | clear 이후 요청의 성공·실패·finally가 다음 화면에 반영될 수 있었다. | `PostCommentViewModel.kt`에서 캐시·세션 세대, 게시글별 조회 번호와 댓글별 수정 번호를 검사한다. 성공한 변경은 이전 조회를 무효화한다. 이전 요청이 새 등록·삭제 상태를 해제하거나 콜백을 실행할 수 없도록 했다. |
| 9. 관리자 | 사용자 검색의 첫 페이지 경합 일부는 방어했다. 네 목록의 후속 페이지가 없었고 로딩·오류가 전역이었다. 가입일·감사 로그의 연도 표시는 이미 구현돼 있었다. | `AdminViewModel.kt`, `AdminManagementScreens.kt`, 신고 목록·상세 화면 및 `MainNavGraph.kt`를 수정했다. 서버 `AdminController.java`와 `AdminService.java`에서 page 기본값 0, size 기본값 20, 응답 page·size·hasNext 및 keyword 필터 적용을 확인했다. 더 보기는 제출된 검색어와 응답 페이지를 사용한다. 상세 재시도, 요청별 상태와 동일 entry 검사, 신고 처리 후 대시보드 재조회를 적용했다. |
| 10. 공개 화면 복원 | 미인증 상태에서 회전하면 공개 가입·재설정 흐름까지 로그인으로 이동했다. 로그인 화면에서는 일시적 갱신 실패를 직접 재시도할 수 없었다. | `MainActivity.kt`, `Route.kt`, `MainNavGraph.kt`, `LoginScreen.kt`, `LoginViewModel.kt`에서 공개 화면을 식별하고 검증이 필요한 보호 스택의 복원을 차단한다. 재시도는 보존된 세션에 연결한다. 만료 안내는 Toast로 분리하고 이미 소비한 이벤트는 회전 후 재처리하지 않는다. |
| 11. 이미지 확대 | 고정 ±1200px 제한이 실제 사진과 화면의 종횡비를 무시했다. | `PostDetailScreen.kt`의 `imagePanBounds`는 ContentScale.Fit으로 표시된 사진에 배율을 곱한 뒤 뷰포트를 초과한 길이의 절반만 각 축에서 허용한다. 제스처 및 크기 변경 시 다시 제한한다. |
| 12. 로그 보안 | Authorization 헤더 가림은 있었으나 요청 URL·기타 헤더·응답 원문·식별자·업로드 정보가 기록될 수 있었다. | `RetrofitClient.kt`, `NetworkLog.kt`, `PostWriteViewModel.kt`, 채팅·프로필·내비게이션 등의 호출부에서 원문과 식별자를 제거했다. Debug와 Release 모두 작업명·HTTP 상태·예외 종류·FCM 시도 횟수 등으로 진단한다. |

## 기존 develop에서 이미 처리한 리뷰

| PR #10의 선행 지적 | 확인한 구현 |
|---|---|
| 관리자 메뉴·사용자 상세의 작은 화면 스크롤 | `AdminDashboardScreen`과 `AdminUserDetailScreen`에 세로 스크롤이 적용돼 있다. 사용자 상세가 있으면 작업 실패 메시지와 기존 정보를 함께 표시한다. |
| JWT 최상위 exp 파싱 | `JwtTokenExpiry`가 최상위 JSON 객체에서 exp를 읽는다. 기존 테스트가 중첩 exp만 있는 경우와 최상위 exp가 있는 경우를 구분한다. |
| 생성 직후 채팅방 보존, 목록에서 사라진 방의 캐시 정리 | `loadRooms`가 생성 직후 서버에 아직 없는 방만 임시 보존하며 그 밖의 캐시를 최신 목록으로 교체한다. 나가기 성공 때는 임시 보존 대상에서도 제거한다. |
| 관리자 역할 확인 | `AdminAccessGate`가 서버 역할을 확인한 뒤 관리자 콘텐츠를 표시하며 일반 사용자면 뒤로 이동한다. |
| 게시글 차단 완료 대화상자 | `PostDetailScreen`이 완료 대화상자를 닫을 때 이동 콜백을 실행한다. 이번에는 해당 콜백의 목적지 검사도 시작 entry에 연결했다. |
| 갱신 결과 공유, 불완전한 갱신 응답 보존, 명시적 인증 헤더의 자동 갱신 제외 | 기존 HTTP 경로에 이 처리들이 있었다. 이번에 앱 시작·명시적 재시도도 같은 갱신 작업에 합류하도록 하고, 빈 토큰 처리와 만료의 원자성을 보강했다. |
| 로그인 결과 저장 거절 시 로딩 종료 | 기존 `LoginViewModel`이 저장 거절 시 현재 로그인 시도에 한해 Idle로 복귀했다. |
| 시스템 바·IME 여백·게시판 오류 겹침 | `MainActivity`의 밝은 시스템 바 아이콘 배경 설정과 루트의 inset 소비, `HomeScreen`의 배타적인 오류·목록 분기를 확인했다. |

## 단위 테스트 범위

| 요청 시나리오 | 운영 코드를 호출하는 테스트와 확인 사항 |
|---|---|
| 1–4 | `SessionPushRegressionTest`에서 이전 세대의 동기화·해제, FID 교체 후 지연 재시도, 이전 토큰의 만료, 이벤트 중복을 검사한다. |
| 5–9 | `InboxRegressionTest`에서 연결 전 진입, 이전 계정의 GET·PATCH 및 대기 중 PATCH, 클릭 콜백 차단, 열린 알림함의 FCM 신호, 읽음 실패 안내와 이동을 검사한다. 지연된 페이지가 새 스냅샷을 덮지 않는지도 확인한다. |
| 10–12 | `BoardChatRegressionTest`에서 두 방의 동시 나가기, 동일 방 중복 클릭, 이전 목록 응답과 서버의 재등장을 검사한다. `NavigationAdminRegressionTest`는 서로 다른 NavBackStackEntry를 사용해 운영 코드의 조건부 pop을 검증한다. |
| 13–16 | `BoardChatRegressionTest`에서 분실물 응답 순서 역전, 습득물 전체 조회 실패 후 재선택, clear 및 재로그인 후 댓글 완료, 작성자 실패 후 재시도와 성공 캐시를 검사한다. |
| 17–18 | `DisplayRegressionTest`에서 오프셋·KST·잘못된 시간의 순서, 쌍별 대칭성과 세 값의 추이성, 가로·세로 사진의 배율별 이동 한계를 검사한다. |
| 관리자 | `NavigationAdminRegressionTest`에서 검색어를 유지한 후속 페이지, 지연된 첫 페이지 무시, 중복 신고 처리와 서버 대시보드 집계를 검사한다. |

## 실기기·서버에서 추가 확인할 항목

연결된 ADB 기기가 없어 Android 화면 회전·터치·시스템 알림·Release 런타임 검증은 수행하지 않았다. 단위 테스트는 지연 가능한 API 대역과 테스트 디스패처로 응답 순서를 제어한다. 배포 서버의 저장 상태와 Firebase 전달 여부는 별도 확인이 필요하다.

1. 테스트 계정 A의 알림함을 열고 B에서 메시지를 전송한다. 시스템 푸시, 배지, 목록 항목이 함께 바뀌는지 확인한다. 읽음 PATCH만 실패시키고 알림을 눌러 Toast와 채팅방 이동을 확인한다. A에서 숨긴 항목이 B의 목록에 영향을 주지 않는지도 확인한다.
2. Release 테스트 빌드에서 설정을 5%, 55%, 100%로 각각 변경한다. 각 PATCH 응답과 별도 GET을 비교하고 앱 재실행 후 같은 값을 확인한다. 미확인·실패 응답에서 저장 실패가 표시되는지 확인한다.
3. 가입·재설정 각 단계에서 회전하고 입력과 뒤로가기를 확인한다. 보호 화면에서 세션을 만료시키고 회전 및 뒤로가기를 반복한다. 일시적 오프라인으로 시작한 뒤 네트워크 복구 시 기존 로그인 다시 연결을 확인한다.
4. 방 A 나가기 또는 차단 완료 응답을 지연시킨 채 방 B로 이동한다. B가 열린 상태로 남는지 확인한다. 두 방에서 동시에 나간 뒤 이전 목록 응답과 상대의 새 메시지를 순서대로 보내 목록을 확인한다.
5. 앱 시작 직후 습득물 지도에서 건물을 선택하고 다른 건물과 전체보기를 빠르게 누른다. 검색에서 타 건물 게시물 및 분실물을 찾고 돌아왔을 때 선택 건물·카테고리를 확인한다. 큰 가로·세로 사진은 1배·4배, 가로·세로 화면에서 가장자리까지 이동하고 축소한다.
6. 테스트 관리자 계정으로 각 목록에 20건을 초과하는 자료와 이름이 겹치는 사용자들을 준비한다. 검색 후 더 보기, 탭 전환, 상세 실패 후 재조회, 중복 신고 처리 전후 대시보드 집계를 확인한다. 일반 계정으로 같은 관리자 경로 접근도 확인한다.
7. 로그아웃·즉시 재로그인과 FID 교체가 겹치도록 테스트 환경에서 응답을 지연한다. 서버 등록 소유자·해제 결과와 실제 수신 계정을 확인한다. Release logcat에서 요청 본문, 주소, 헤더, 계정 식별값이 기록되는지도 확인한다.
8. A 계정으로 댓글을 등록하고 새 댓글까지 자동 스크롤되는지 확인한다. 지연된 등록·조회·수정·삭제가 있는 상태에서 B 계정으로 전환한 뒤 B의 목록, 등록 버튼, 오류 안내에 A의 결과가 나타나는지 확인한다. 알림 스와이프·현재 목록 숨기기도 계정을 번갈아 사용해 확인한다.


## 추가 인증 검토

서버 AuthService.reissue는 잘못된 토큰에 `유효하지 않은 리프레시 토큰입니다.`, Redis 만료·불일치·교체 실패에 `리프레시 토큰이 만료되었거나 유효하지 않습니다.`를 발생시킨다. GlobalExceptionHandler는 success=false와 message를 담아 HTTP 400을 반환한다. 배포 서버에 실제 토큰을 보내는 호출은 수행하지 않았으며 로컬 서버 소스로 계약을 확인했다.

RefreshSessionPolicy와 RetrofitClient는 재발급의 HTTP 400 응답 본문에서 이 두 메시지가 정확히 일치할 때 원자적 expireSession을 호출한다. 일반 400, 파싱 실패, 429, 5xx는 세션을 보존한다. 기존 401·403 정책도 유지한다.

LoginViewModel은 프로세스에서 공유하는 AuthRequestOrder를 통해 FCM 해제와 서버 로그아웃 정리를 새 로그인 요청보다 먼저 완료한다. 로컬 토큰 삭제와 화면 전환은 정리 전에 실행한다. 정리는 최대 8초이며 FCM 해제에는 기존 4초 제한을 적용한다. 실패·타임아웃 후 대기 중인 로그인은 진행할 수 있다.

Android의 타임아웃이나 요청 취소는 서버에서 이미 시작된 Redis 삭제를 취소했다고 보장하지 못한다. 다른 기기의 동시 로그인도 Android 잠금의 적용 대상이 아니다. 서버 logout(email)이 RT:email을 무조건 삭제하므로 이 경우에는 이전 로그아웃이 새 토큰을 삭제할 가능성이 남는다. 서버가 로그아웃 대상 Refresh Token 또는 세션 ID를 받아 Redis 값을 원자적으로 비교 삭제해야 완전한 보장이 가능하다. 서버 API는 이번에 수정하지 않았다.

추가 회귀 테스트는 정상 정리 전 로그인 대기, 정리 타임아웃 및 실패 뒤 로그인 진행, 두 가지 HTTP 400 무효 메시지와 일반 400·429·5xx 보존을 검사한다. 실서버에서는 로그아웃 처리 지연 후 같은 계정 재로그인과 재발급, 타임아웃 뒤 지연 삭제, 다른 기기 동시 로그인을 확인해야 한다.

추가 수정 후 검증 결과는 Debug 컴파일·APK·단위 테스트 명령 BUILD SUCCESSFUL 및 종료 코드 0, Release APK·AAB 명령 BUILD SUCCESSFUL 및 종료 코드 0이다. JUnit 결과는 총 49개, 실패·오류·건너뜀 모두 0개다. 기존 45개를 유지하고 4개를 추가했다.

## PR #11 추가 리뷰 검토 (eb2cf7c 기준)

[리뷰 원문](https://github.com/KU-404-Finders/finders-android/pull/11#pullrequestreview-5957697610)을 현재 소스와 대조했다. 작업 시작 시 `fix/coderabbit-stability`, HEAD `eb2cf7c`, 깨끗한 작업 트리였으며 PR의 head SHA와도 일치했다. `feat/android-ios-parity`의 HEAD `2dd6d26d5d71e52ef06ee139c8c31167b676fe7c` 및 해당 기능 추가 커밋은 수정하지 않는다. 앱 버전 `6 / 1.0.5`, 서버 및 iOS 코드도 변경하지 않는다. 이 추가 작업은 commit, push, merge 없이 작업 트리에 남긴다.

### Major 4건

| 문제 | 확인한 결함과 수정 |
|---|---|
| 중복 채팅방 나가기 | 기존 가드는 두 번째 호출자의 콜백을 버렸다. 방별 진행 요청에 모든 완료 콜백을 등록하여 DELETE 한 번의 성공·실패를 함께 전달한다. 세션·요청 소유권과 `popIfCurrent` 보호는 유지한다. |
| 습득물 목록 캐시 정합성 | 표시 목록을 필터 전환 때 비우면서 이전 성공 버전과 새 선택 범위를 함께 사용했다. 성공한 조회의 건물 범위·데이터·버전을 `SuccessfulList` 한 값으로 보관하고, MainNavGraph는 해당 값이 전체 범위일 때만 로컬 사본을 정리한다. 실패·전환 중 빈 표시 목록은 성공으로 처리하지 않는다. |
| 최초 조회 중 댓글 등록 | 등록이 이전 조회를 무효화한 뒤 신규 댓글만 남길 수 있었다. 완전한 목록의 조회 성공 여부를 추적하고, 목록이 불완전하면 등록 후 다시 조회한다. 아직 조회 결과에서 확인되지 않은 신규 댓글을 합쳐 A·B·C가 유지되게 한다. |
| 동일 댓글 연속 수정 | 최신 요청 번호만 채택하면 A 성공이 무시되고 B 실패 때 원문이 남았다. 게시글 종류·ID·댓글·세션별 Mutex로 PATCH를 직렬화하여 각 성공을 차례로 반영한다. A 성공/B 실패는 A, 둘 다 성공하면 B를 유지하며 이전 세션의 실행·응답은 무시한다. |

### Minor 26건 개별 판정

기준 코드에서 26건 모두 아직 유효했다. 이미 해결됐거나 재현 근거가 부족하여 제외한 Minor는 없다. 운영 결함 22건, 테스트의 잘못된 통과·정리 문제 3건, 문서 불일치 1건을 수정한다.

| 번호 | 원문 위치·의견 | 판정 및 조치 |
|---|---|---|
| 1 | LoginViewModel:41 잠금 뒤 로그인 유효성 | 유효. 잠금 안의 API 전송 직전에 시도 번호·세대를 다시 검사한다. |
| 2 | LoginViewModel:148 탈퇴 토큰 | 유효. 푸시 해제 대기 후 같은 세션의 최신 액세스 토큰으로 탈퇴한다. |
| 3 | MainActivity:69–70 미소비 알림 목적지 | 유효. 목적지와 소유자를 Bundle로 복원하고 소비한 목적지는 복원하지 않는다. JWT sub가 같은 계정이면 토큰 갱신·재로그인 뒤에도 유지하며 다른 계정은 버린다. sub를 확인할 수 없는 토큰은 세대 변경 시 보수적으로 폐기한다. |
| 4 | RetrofitClient:170 401·403 본문 읽기 | 유효. 본문은 400에서만 읽으며 401·403은 본문 수신 실패와 무관하게 만료로 판정한다. |
| 5 | RefreshSessionPolicy:11 JSON 타입 | 유효. success는 불리언 false, message는 문자열일 때만 두 토큰 거절 문구와 비교한다. |
| 6 | LoginScreen:226 재연결 중 이동 | 유효. 재연결을 포함한 로그인 로딩 중 회원가입·비밀번호 재설정 링크를 비활성화한다. |
| 7 | MainNavGraph:607 상세 캐시 재병합 | 유효. 습득물 게시판은 서버 표시 목록을 사용한다. 상세용 로컬 사본을 재병합하지 않으며, 작성 성공 후 목록을 강제 재조회한다. |
| 8 | PushDeviceManager:169 실제 등록 ID | 유효. 등록된 Owner/마지막 성공한 등록 ID를 우선하여 해제하고, 성공한 등록 ID를 기기에 보존한다. 메모리 소유자가 없는 재시작도 처리하며 다른 기기로 이 ID가 복원되지 않게 백업에서 제외한다. |
| 9 | BoardChatRegressionTest:72 DELETE 횟수 | 유효한 테스트 결함. API에 진입한 호출 수를 방별로 직접 단언하여 catch된 예외가 중복 호출을 감추지 못하게 한다. |
| 10 | BoardChatRegressionTest:109–110 작성자 조회 횟수 | 유효한 테스트 결함. 첫 응답 완료 전 1회, 실패 후 재시도에서 2회임을 확인한다. |
| 11 | NotificationViewModel:57 설정 Mutex | 유효. 계정/세대별 잠금으로 새 계정이 이전 계정의 PATCH를 기다리지 않게 한다. 같은 계정의 GET/PATCH 순서는 유지한다. |
| 12 | NotificationViewModel:151 읽음 방문 식별 | 유효. 클릭한 알림함 방문 ID와 완료 시 방문이 같을 때만 화면 콜백을 실행한다. |
| 13 | NotificationViewModel:151–152 닫힌 알림함 배지 | 유효. 현재 계정의 읽음 성공은 화면이 닫혔어도 미읽음 수를 갱신한다. |
| 14 | NotificationViewModel:63 자동 갱신 시 이전 페이지 | 유효. 새 알림 조회의 스냅샷을 별도로 사용하고 기존 탐색 스냅샷·커서 쌍은 유지한다. 신규 항목이 여러 페이지이면 기존 스냅샷과 만날 때까지 조회한다. 모두 읽음의 범위는 최신 스냅샷을 사용한다. |
| 15 | NotificationViewModel:63 자동 갱신 오류 | 유효. 기존 행과 커서를 유지하고 별도 오류를 표시한다. 목록이 없는 최초 오류는 전체 오류 화면을 유지한다. |
| 16 | NotificationScreens:48/108 계정 조회 재시도 | 유효. 두 화면에 `/me` 오류와 재시도를 전달하고 계정이 없으면 새로고침도 `/me`를 다시 요청한다. |
| 17 | AdminManagementScreens:388 감사 이력 재시도 | 유효. 첫 페이지는 0, 다음 페이지는 보존한 실패 페이지를 재시도한다. 오류 시 hasNext와 무관하게 버튼을 표시한다. |
| 18 | AdminReportDetailScreen:49 신고 본문 유지 | 유효. 신고가 있으면 로딩·오류 중에도 본문을 표시한다. 재시도는 처리 명령이 아닌 GET이며 상태 확인 전 처리 버튼을 비활성화한다. |
| 19 | AdminReportListScreen:135 신고 목록 재시도 | 유효. 첫 페이지 또는 실패한 다음 페이지 재시도를 연결하며 다음 페이지 실패 시 기존 행을 유지한다. |
| 20 | AdminViewModel:136 게시글 삭제 무시 | 유효. Delete 로딩을 목록·확인 버튼에 전달해 다른 삭제 요청이 조용히 버려지지 않게 한다. |
| 21 | AdminViewModel:95 제한 전 사용자 목록 응답 | 유효. 제한 변경 성공 시 현재 검색어로 목록을 다시 요청하여 이전 목록 요청을 무효화한다. |
| 22 | AdminViewModel:102 처리 전 신고 목록 응답 | 유효. 신고 처리 성공 시 목록을 다시 요청하고 기존 대시보드 갱신·성공 콜백을 유지한다. |
| 23 | PostDetailScreen:826 이미지 첫 프레임 경계 | 유효. graphicsLayer 내부에서 실제 레이어 size와 현재 scale로 이동 경계를 계산한다. |
| 24 | SearchScreen:79 긴 오류 | 유효. 오류는 최대 2줄 말줄임, 버튼은 ‘다시 시도’로 분리하여 부분 결과를 유지한다. |
| 25 | 검증 문서:12 HTTP 400 설명 | 유효한 문서 불일치. 요약에 JSON 타입·두 거절 문구·일반 400 보존 조건을 명시했다. |
| 26 | StabilityFixture:53–55 정리 실패 | 유효한 테스트 결함. 독립적인 finally와 log 초기화 검사로 Main 디스패처 복원을 보장한다. |

별도 Nitpick 3건 중 계정 연결 없이 토큰 세대만 바뀌는 읽음 회귀 테스트와 stale unregister 호출 횟수 직접 단언은 테스트 보강에 포함했다. 사용되지 않는 채팅 오류 본문 읽기 제거는 이번 Major/Minor 수정에 필요하지 않아 그대로 두었다.

### 검증 범위

기존 테스트를 유지하고 `AdditionalMajorRegressionTest`, `AdditionalInboxRegressionTest`, `AdditionalAuthRegressionTest`, `AdditionalAdminRegressionTest` 및 기존 테스트 확장으로 지연 응답, 중복 호출, 성공·실패 순서와 세션 전환을 검증했다. 실기기의 화면 회전·터치 및 운영 서버 통합 검증은 수행하지 않았다.

최종 Debug 명령 `./gradlew.bat --no-daemon :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain`은 종료 코드 0, BUILD SUCCESSFUL이다. JUnit XML 합계는 **77개, 실패 0, 오류 0, 건너뜀 0**이다. 기존 49개를 유지하고 28개를 추가했다. 추가 Major 9개, 알림 9개, 인증 4개, 관리자 3개, 푸시 2개, JSON 정책 1개다.

최종 소스 상태에서 아래 명령을 다시 실행해 두 빌드 모두 종료 코드 0, BUILD SUCCESSFUL을 확인했다. Debug는 1분 24초, Release는 3분 11초였으며 Release의 R8 최적화와 필수 Lint도 통과했다. `git diff --check`는 종료 코드 0이다. 기존 Gradle 옵션 및 스와이프 API의 deprecated 경고는 이번 수정 범위에서 변경하지 않았다.

```powershell
.\gradlew.bat --no-daemon :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat --no-daemon :app:assembleRelease :app:bundleRelease --console=plain
git diff --check
```

검증 후에도 HEAD는 `eb2cf7c396e200ce55f6fb9a88228ddaf4fb6b7f`, 기능 추가 브랜치 HEAD는 `2dd6d26d5d71e52ef06ee139c8c31167b676fe7c`다. 스테이징된 파일은 없고, 수정 26개와 신규 테스트 파일 4개를 미커밋 작업 트리에 남겼다.

### 최종 변경 파일 목록 (30개)

아래는 기준 `eb2cf7c` 이후 작업 트리의 수정·신규 파일이다. 빌드 산출물은 포함하지 않는다.

```text
CODERABBIT_STABILITY_REVIEW.md
app/src/main/java/com/ku/lostandfound/MainActivity.kt
app/src/main/java/com/ku/lostandfound/network/RefreshSessionPolicy.kt
app/src/main/java/com/ku/lostandfound/network/RetrofitClient.kt
app/src/main/java/com/ku/lostandfound/notification/NotificationTarget.kt
app/src/main/java/com/ku/lostandfound/notification/PushDeviceManager.kt
app/src/main/java/com/ku/lostandfound/ui/admin/screen/AdminManagementScreens.kt
app/src/main/java/com/ku/lostandfound/ui/admin/screen/AdminReportDetailScreen.kt
app/src/main/java/com/ku/lostandfound/ui/admin/screen/AdminReportListScreen.kt
app/src/main/java/com/ku/lostandfound/ui/admin/viewmodel/AdminViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/chat/viewmodel/ChatViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/found/viewmodel/FoundItemViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/login/screen/LoginScreen.kt
app/src/main/java/com/ku/lostandfound/ui/login/viewmodel/LoginViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/navigation/MainNavGraph.kt
app/src/main/java/com/ku/lostandfound/ui/notification/NotificationScreens.kt
app/src/main/java/com/ku/lostandfound/ui/notification/NotificationViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/post/screen/PostDetailScreen.kt
app/src/main/java/com/ku/lostandfound/ui/post/viewmodel/PostCommentViewModel.kt
app/src/main/java/com/ku/lostandfound/ui/search/screen/SearchScreen.kt
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/test/java/com/ku/lostandfound/AdditionalAdminRegressionTest.kt
app/src/test/java/com/ku/lostandfound/AdditionalAuthRegressionTest.kt
app/src/test/java/com/ku/lostandfound/AdditionalInboxRegressionTest.kt
app/src/test/java/com/ku/lostandfound/AdditionalMajorRegressionTest.kt
app/src/test/java/com/ku/lostandfound/BoardChatRegressionTest.kt
app/src/test/java/com/ku/lostandfound/SessionPushRegressionTest.kt
app/src/test/java/com/ku/lostandfound/StabilityFixture.kt
app/src/test/java/com/ku/lostandfound/network/RefreshSessionPolicyTest.kt
```
