# KUfinders 최종 반영 내용

## 반영 완료

- 회원가입 입력값을 `SignupViewmodel`에 통합 저장
  - 이름: `name`
  - 비밀번호: `password`
  - 이메일 아이디: `emailPrefix`
  - 인증번호: `verificationCode`
  - 전체 이메일: `email`
- 이메일 인증번호 화면에서 임의 이메일 대신 ViewModel의 이메일 표시
- 회원가입 흐름 연결
  - 이름 → 비밀번호 → 이메일 → 인증번호 → 완료
- 건물 내부 위치 등록 시 층수를 드롭다운으로 선택
- 분실물 게시판에서는 캠퍼스맵 제거, 목록만 표시
- 습득물 게시판에서만 캠퍼스맵 표시
- 습득물 캠퍼스맵에서 건물 클릭 시 해당 건물 게시물만 필터링
- 캠퍼스맵 확대 시 건물명 표시
- 캠퍼스맵 좌우상하 드래그 이동 추가
- 게시물 등록 사진 선택 기능 추가
- 등록한 사진을 글쓰기/상세 화면에서 미리보기
- `DropxdownMenuItem` 오타 방지: `DropdownMenuItem` 사용
- `menuAnchor()` 미사용으로 deprecation 회피
- `PostListCard` 미정의 오류 방지용 wrapper 추가
- `setPostType` JVM setter 충돌 방지: `changePostType` 사용
- `MainActivity` safeDrawing inset 및 softInput adjustNothing 설정
- Gradle Kotlin Android plugin 누락 보완

## 지도 assets

아래 3개 JSON은 모두 필요합니다.

- `app/src/main/assets/konkuk_campus_boundary.json`
- `app/src/main/assets/konkuk_buildings_full_coordinates.json`
- `app/src/main/assets/konkuk_path_coordinate.json`

