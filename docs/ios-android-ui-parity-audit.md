# iOS–Android UI/UX 전수 비교 (2026-11-28)

기준: iOS `main` `665ff149e2f2c63beb4478b004eec7796043c5b0` (SwiftUI 소스). Android: `feature/ios-parity-stability` `f1a331f` + 이번 미커밋 변경.
Windows 환경이라 iOS 시뮬레이터는 실행하지 못했다. iOS는 소스 기준으로만 비교했고, 기기 간 시각 비교는 하지 않았다.

비교 방법: 각 iOS View의 레이아웃·색·글꼴·문구를 Android Compose 구현과 대조했다. 문구는 스크립트로 iOS 문자열 중 Android에 없는 것을 추출해 확인했다. Android 시각 확인은 API 37 `phone_360_800` 에뮬레이터에서 `UiParityCaptureTest`로 렌더링한 화면에 한정한다.

상태: MATCHED · FIXED · PLATFORM DIFFERENCE · ANDROID ONLY · IOS ONLY · NOT VERIFIED

## 공통 컴포넌트

| 화면/요소 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 상단 뒤로가기 바 | `DesignSystem.swift` FinderNavBar | `ui/component/AppBars.kt` BackTitleBar, `GetBackTopAppBar.kt` | Android #F4F4F4 배경, 22sp 제목, 24dp 무반응 뒤로가기(48dp 미달), 구분선 없음, `GetBackTopAppBar`는 20dp·영문 "back" 라벨 | 흰 배경, 0.5dp 구분선, 20sp 제목(heading), 48dp 원형 터치 영역, ⋮/채팅 아이콘도 48dp. `GetBackTopAppBar`는 BackTitleBar로 위임 | FIXED |
| 녹색 메인 상단바 | BoardView/ProfileView topBar | `HomeTopBar` | 검색이 `⌕` 문자 글리프, 44dp 터치 | Material 검색 아이콘("검색"), 48dp, 앞쪽 아이콘 매개변수화 | FIXED |
| 확인 다이얼로그 | `AccountDialog` | `FinderDialogs.kt` FinderConfirmDialog (신규) | 화면마다 AlertDialog/개별 구현 혼재, 태블릿에서 폭 제한 없음 | 공통 컴포넌트로 통일: 로그아웃, 차단 해제, 채팅 나가기/차단, 알림 전체 삭제. iOS처럼 최대 폭 440dp | FIXED (7·10인치 캡처 확인) |
| 프로필 아이콘 | FinderAvatar(muted) | FinderPersonAvatar (신규) | 없음 | 추가 | FIXED |
| 게시글 카드·배지 | ItemCard, PostTypeBadge | `PostCards.kt` | 습득 파랑 #246FB5/#E6F2FB vs #1565C0/#E3F2FD | 토큰 정렬 | FIXED |
| 하단 탭 | MainTabView | `AppBottomBar.kt` | 탭 순서·아이콘·라벨 동일 | 없음 | MATCHED |
| 색 팔레트·카드 반경 | FinderTheme, SettingsStyle | 화면별 상수 | 설정형 화면 캔버스 #F4F4F4 vs #F2F2F7 | 마이페이지·알림 설정·예방 알림을 #F2F2F7/#8E8E93/10dp로 정렬 | FIXED |
| 글꼴 | Pretendard | `ui/theme/Type.kt` PretendardFontFamily(`res/font`) | 이전 판정 오류 정정: Android도 모든 Material 텍스트 스타일에 Pretendard를 적용하고 있음 | 없음 | MATCHED |
| 다크 모드 | 라이트 고정 | 라이트 고정 | 없음 | 없음 | MATCHED |

## 인증 및 계정

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 로그인·회원가입(약관/이름/비밀번호/이메일/코드/완료) | `AccountViews.swift` WelcomeView, AndroidSignupView | `ui/login`, `ui/signup` | iOS가 Android를 기준으로 작성됨. 문구 대조 결과 iOS에만 있는 문구 없음 | 상단 바만 공통 수정 | MATCHED |
| 이용 정지·탈퇴 확인·완료 | AccountDialog | `LoginScreen`, `WithdrawConfirmDialog` | 문구 동일 | 없음 | MATCHED |
| 비밀번호 재설정 | `PasswordResetView.swift` | `PasswordResetScreens.kt` | Android는 외곽선 입력·일반 굵기 제목, 완료 단계 없음(바로 로그인으로 이동) | 밑줄 입력, 굵은 제목, 재전송 버튼 색, "비밀번호가 변경되었습니다" 완료 단계 추가(VM `resetCompleted`, 라우트 가드 반영) | FIXED |

## 게시판·검색·상세·작성

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 습득물 게시판 | BoardView(found) | `HomeScreen.kt`, `FoundBoardScreen.kt` | 지도 카드(18dp, 테두리, 그림자) 없음, 목록 제목이 회색 13sp, 선택 건물 표시 문구 다름 | 지도 카드화, 핀 아이콘+16sp 굵은 제목+1dp 구분선, "OO 습득물"+민트 "전체보기", 빈 화면 문구 | FIXED |
| 분실물 게시판·분류 필터 | BoardView(lost) | `LostBoardScreen.kt` | 필터가 테두리 둥근 사각형·라벨 "전체" | 캡슐, 미선택 민트/"필터", 선택 시 녹색, 빈 화면 문구 | FIXED |
| 작성 버튼 | 58pt 원형 | Material FAB | 둥근 사각형 56dp | 원형 58dp | FIXED |
| 오류·빈 화면 | ErrorNotice, FinderEmptyState | BoardStateMessage | 녹색 채운 버튼 | 빨간 13sp 문구 + "다시 시도" 링크, 회색 빈 문구 | FIXED |
| 새로고침·페이지 | refreshable | PullToRefreshBox | 없음 | 없음 | MATCHED |
| 검색 | SearchView | `SearchScreen.kt` | `‹`/`⌕` 글리프, 각진 입력창, 지우기 버튼·안내 문구 없음 | 48dp 뒤로가기, 14dp 둥근 입력창+돋보기, 지우기, 포커스 테두리, iOS 안내·빈 결과 문구 | FIXED |
| 게시글 상세 본문·이미지·위치·추천·소유자 버튼·신고/차단 | `ItemDetailView.swift` | `PostDetailScreen.kt` | iOS가 Android 기준. 문구 대조상 진행 중 표시("신고 중...") 외 차이 없음 | 상단 채팅 아이콘 라벨 "작성자와 채팅" | MATCHED |
| 댓글 입력창 | commentInput | CommentInputBar | 10dp 한 줄 입력, `➤` 문자 | 22dp 알약형 4줄 입력, 48dp 종이비행기 아이콘 버튼, 상단 구분선 | FIXED |
| 작성·수정·사진·분류·위치 | `ComposeView.swift` | `PostWriteScreen.kt`, `LocationPickerScreen.kt` | 문구 대조상 차이 없음 | 없음 | MATCHED |
| 등록 결과 미확정 안내 | 없음 | PostWriteScreen | Android 저널 기반 기능 | 유지 | ANDROID ONLY |

## 채팅

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 채팅 목록 | ChatListView | `ChatListScreen.kt` | 시각 위치(오른쪽 열 vs 제목 줄), 쉐브런 없음, 빈/오류 화면 아이콘·문구, 색 토큰 | iOS 행 구성·색, 미읽음 캡슐(99+), 쉐브런, 빈/오류 상태 | FIXED |
| 채팅 목록 헤더 | FinderNavBar(뒤로) | 녹색 탭 헤더 | Android는 하단 탭 루트 화면 | 기존 `ChatTabHeaderTest` 계약 유지 | PLATFORM DIFFERENCE |
| 채팅방 메시지 | messageBubble | `ChatRoomScreen.kt` | 내 말풍선 연녹색+검은 글자, 메타가 말풍선 아래·숫자, 날짜 구분 없음, 상대 이름 라벨 | 녹색/흰 글자, 18dp+꼬리 6dp, 옆쪽 "읽음/안읽음"+시각, 날짜 캡슐, 이름 라벨 제거 | FIXED |
| 채팅방 입력·연결 배너 | composer, banner | 동일 파일 | 56dp 한 줄, `➤`, 빨간 연결 문구 | 22dp 알약 5줄, 종이비행기, 노란 배너 | FIXED |
| 나가기·차단 확인 | alert | ChatActionConfirmDialog | 문구 다름 | iOS 문구 + 공통 다이얼로그 | FIXED |
| 채팅방 상단바 | FinderNavBar(제목+부제, ellipsis) | `ChatRoomScreen.kt`, `MainNavGraph.kt` | 녹색 화살표·세로 점 메뉴, 부제 없음 | 검은 쉐브런(48dp), 제목+부제("상대 · 분실물 게시글 채팅", 상대 이름은 게시글 작성자로 확인될 때만), 가로 점 메뉴, 구분선. 제목 탭 시 게시글 보기 유지 | FIXED |

## 알림

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 알림함 행 | NotificationInboxView.row | `NotificationScreens.kt` | ●/○ 문자, 아이콘·시각 없음 | 미읽음 점, 종류별 아이콘 원(채팅/댓글/추천), 상대 시각, 본문 2줄, 새 메시지 수 | FIXED |
| 알림함 헤더·빈/오류 | 동일 | 동일 | 버튼 색·굵기, 아이콘 없는 안내 | 녹색/빨강 15sp, 아이콘 안내 | FIXED |
| 전체 삭제 확인 | confirmationDialog | 공통 다이얼로그 | iOS 문구는 "지금 보이는 알림" | Android의 실제 삭제 범위(미로딩 이전 알림 포함)를 정확히 설명하는 기존 문구 유지 | PLATFORM DIFFERENCE |
| 페이지 이동 | 자동 로드 | "이전 알림 더 보기" 버튼 | 방식 다름 | 버튼 유지(색만 정렬) | PLATFORM DIFFERENCE |
| 알림 설정 | NotificationSettingsView | 동일 파일 | 카드 나열, 헤더 없음 | 채팅/게시글 그룹, 임계값 행, 기기 알림 꺼짐 시 "기기 알림 켜기" 행 | FIXED |
| 채널별 표시 설정 | 없음 | 기기 알림 표시 그룹 | Android 알림 채널 | 유지 | ANDROID ONLY |

## 마이페이지·차단·예방 알림

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 마이페이지 | ProfileView | `ProfileScreen.kt`, `MyWrittenPost*.kt` | 개별 카드 나열, 프로필 카드 안 로그아웃 버튼, 아이콘 타일·섹션 없음, 상단 종 없음 | iOS 설정형 그룹(알림/표시/개인정보 보호/관리/지원), 색 타일 아이콘, 로그아웃·탈퇴 행, 탈퇴 안내 각주, 종 아이콘, 작성 글 카드 재구성 | FIXED |
| 문의하기 | mailto 링크 + 각주에 이메일 | 다이얼로그(메일 앱/복사) | 메일 앱 없는 기기 대응, 이메일은 다이얼로그에서만 노출(기존 테스트 계약) | 유지 | PLATFORM DIFFERENCE |
| 문의 이메일 주소 | yjwon20@… | michelle03212@… | 플랫폼별 연락처가 다름 | 기존 주소 유지, 팀 내부에서 공식 연락처 확인 예정 | 운영 확인 대기 |
| 차단한 사용자 | BlockedUsersView | `BlockedUsersScreen.kt` | ListItem+TextButton, 날짜·아이콘 없음 | 연한 배경, 안내 문구, 흰 카드(10dp), 프로필 아이콘, 이름, "차단일"(서버 값이 없으면 미표시), 녹색 캡슐(32dp, 48dp 터치), 처리 중 비활성, 공통 확인 다이얼로그, 자동 다음 페이지 | FIXED (에뮬레이터 캡처 확인) |
| 나의 분실물/습득물 내역 | MyPostsView | `MyPostsScreen.kt` | 상단바 공통 수정만 | — | MATCHED |
| 분실물 예방 알림 | LeaveReminderSettingsView | `LeaveReminderScreen.kt` | 섹션·순서 동일, 캔버스·반경·각주 색 차이 | 토큰 정렬, 팝업 안내 문구 구체화 | FIXED |
| 날씨 출처 | Apple Weather | Open-Meteo | 데이터 제공자 다름 | 유지 | PLATFORM DIFFERENCE |
| 위치 권한·백그라운드 동의 | iOS 권한 문구 | Android 권한/동의 다이얼로그 | Google Play 정책 | 유지 | ANDROID ONLY |

## 신고·관리자

| 화면 | iOS | Android | 발견된 차이 | 실제 수정 | 결과 |
|---|---|---|---|---|---|
| 게시글 신고 | ItemDetailView report card | PostDetailScreen | 문구 동일 | 없음 | MATCHED |
| 관리자 대시보드 | AdminDashboardView | `AdminDashboardScreen.kt` | 신고 외 메뉴에 건수 없음, `›` 문자 | 회원 수·분실/습득 건수, 쉐브런 아이콘, 로딩 전에는 건수 미표시 | FIXED |
| 신고 목록 | AdminReportListView | `AdminReportListScreen.kt`, `AdminViewModel` | 상태 필터 없음, "더 보기" 버튼, 빈 목록 문구 없음 | 전체/미처리/반려/조치 완료 칩(기본 미처리, 서버 `status` 쿼리), 자동 다음 페이지, iOS 빈·오류 안내 | FIXED |
| 신고 상세·처리 | AdminReportDetailView | `AdminReportDetailScreen.kt` | 게시글 ID·종류·상세 내용·메모·처리 일시 없음, 처리 메모 입력 없음 | 행 추가, iOS 알림형 다이얼로그 + 처리 메모(최대 1000자, 서버 `note`) | FIXED |
| 회원 목록 | AdminUserListView | `AdminManagementScreens.kt` | 외곽선 입력+검색 버튼, role 원문 표시 | 돋보기·지우기 입력(IME 검색), 아바타, 관리자 표시, 가입일, 정상/이용 제한 | FIXED |
| 회원 상세·이용 제한 | AdminUserDetailView | 동일 | 제목 "사용자 상세", 사유 입력 없음, 정보 카드 없음 | "회원 상세", 권한·회원 ID, 이용 제한 정보 카드, 관리자 안내, 사유 입력(서버 `reason`) | FIXED |
| 게시글 관리 | AdminPostListView | 동일 | 녹색 토글, 상태 원문, "게시글 삭제" 텍스트 버튼 | iOS 세그먼트(분실물·습득물), 종류·상태 배지, 작성자·일시, 빨간 캡슐 삭제, iOS 확인 문구 | FIXED |
| 작업 이력 | AdminAuditLogView | 동일 | 액션·대상 원문(DELETE_POST, POST #31) | 한글 라벨, 대상 종류, 위험 작업 빨간색, 일시 우측 | FIXED |

## 2차 작업에서 실제 화면 확인 (2026-11-28)

`UiParityCaptureTest` 11건을 세 AVD에서 실행해 모두 통과했고, 캡처를 `build/ui-parity-screenshots/`(git 제외)에 저장했다.

| AVD | 화면 | 결과 |
|---|---|---|
| `phone_360_800` (API 37) | 차단한 사용자·다이얼로그, 관리자 대시보드·신고 목록·신고 상세·처리 다이얼로그·회원 상세·게시글·삭제 다이얼로그·작업 이력, 마이페이지, 채팅 목록·채팅방 | 15장 |
| `7inch_tablet` (API 34, 1920×1200) | 동일 | 15장 (다이얼로그 폭 제한 적용 전 캡처) |
| `10inch_tablet` (API 34, 2560×1600) | 동일 | 15장 (다이얼로그 440dp 폭 확인) |

캡처 경로는 `build/ui-parity-screenshots/<기기 폴더>/<파일명>`이다. 기기 폴더는 `phone-360x800-api37`, `tablet-7in-api34`, `tablet-10in-api34`이며 각 폴더에 아래 이름으로 저장됐다.

| 화면 | 파일명 |
| --- | --- |
| 관리자 대시보드 | `parity-admin-dashboard.png` |
| 신고 목록·상세·처리 다이얼로그 | `parity-admin-reports.png`, `parity-admin-report-detail.png`, `parity-admin-report-dialog.png` |
| 회원 상세 | `parity-admin-user-detail.png` |
| 게시글 관리·삭제 다이얼로그 | `parity-admin-posts.png`, `parity-admin-post-delete-dialog.png` |
| 관리자 작업 이력 | `parity-admin-audits.png` |
| 차단 사용자 목록·기본 상태·해제 다이얼로그 | `parity-blocked-users.png`, `parity-blocked-users-base.png`, `parity-blocked-users-dialog.png` |
| 채팅 목록·채팅방 | `parity-chat-list.png`, `parity-chat-room.png` |
| 마이페이지 상·하단 | `parity-profile.png`, `parity-profile-bottom.png` |

## 1차 작업의 실제 화면 확인 범위

- 확인함 (API 37, 360×800): 차단한 사용자 목록, 마이페이지 상·하단, 채팅 목록, 채팅방. 이미지 경로: `UiParityCaptureTest`를 `-e captureParityUi true`로 실행한 앱 캐시.
- 다이얼로그 캡처는 다른 창을 잡아 이미지로는 남지 않았고, 문구 단언으로만 확인했다.
- 확인하지 않음: 태블릿 AVD, 작은 화면 외 해상도, 키보드 표시 상태의 신규 입력창 시각 확인(기존 EdgeToEdge 테스트는 통과), 게시판·검색·알림 화면 캡처, iOS 실제 화면.

## 기존 미수정·미검증 항목 정리

| 항목 | 사유 | 상태 |
|---|---|---|
| 미수정 1 · 문의 이메일 주소(iOS `yjwon20@…`, Android `michelle03212@…`) | 기존 주소를 유지한다. | 팀 내부 확인 대기 |
| 미수정 2 · 7인치 가로 키보드의 게시글 본문 | 이전 가용 높이 362px에서 184dp(368px) 입력란의 아래 6px가 가려졌다. 작성·수정 공통 화면에서 가로일 때 실제 스크롤 영역을 기준으로 입력란 높이를 줄인다. 세로와 충분한 높이에서는 184dp를 유지한다. | FIXED · 7인치 API 34 가로에서 작성·수정 모두 통과 |
| 미검증 1 · 공식 문의 주소 | 이전 표에서 같은 문의 이메일 이슈를 `NOT VERIFIED`로도 집계했다. 플랫폼별 주소 차이는 확인했다. | 현재 주소의 변경 여부는 팀 내부 확인 대기 |
| 미검증 2 · iOS 실제 화면과 기기 간 비교 | 현재 참조는 기존 SwiftUI 소스 비교와 Android 캡처다. Windows에서 iOS 시뮬레이터를 실행할 수 없다. | NOT VERIFIED |

최종 확인에서 7인치 API 34 가로(1920×1200) 계측 3건이 통과했다. 채팅 입력·전송 버튼, 게시글 작성·수정 본문, 가입 입력·하단 버튼이 키보드에 가려지지 않았다. 최초 실행은 에뮬레이터 System UI 응답 없음 창이 포커스를 차지해 IME 대기가 실패했다. 창을 닫고 동일 APK로 해당 세 항목만 다시 실행했다. 검증 로그는 `build/local-candidate-7in-insets-retry.log`다.
