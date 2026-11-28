# Finders Android 작업 지침

## iOS 및 Server 참조 코드 동기화

iOS 또는 Server 코드가 필요한 새 Android 작업마다 분석 전에 아래 저장소의 기준 브랜치와 원격 HEAD를 확인한다.

| 저장소 | 로컬 경로 | 기준 브랜치 | 원격 |
| --- | --- | --- | --- |
| Android | `C:\Users\Administrator\Desktop\finders\finders-android` | `develop` | `https://github.com/KU-404-Finders/finders-android.git` |
| iOS | `C:\Users\Administrator\Desktop\finders\finders-ios` | `main` | `https://github.com/KU-404-Finders/finders-ios.git` |
| Server | `C:\Users\Administrator\Desktop\finders\finders-server-api` | `main` | `https://github.com/KU-404-Finders/finders-server-api.git` |

1. `git rev-parse --is-inside-work-tree`, `git remote -v`, `git status --short --branch`, `git branch --show-current`로 저장소 상태를 확인한다. 이미 존재하는 저장소는 재복제하지 않는다.
2. 원격 브랜치 HEAD를 확인하고 각 저장소에서 `git fetch --prune origin`을 실행한다. `git pull --ff-only`는 현재 체크아웃된 브랜치의 upstream을 갱신하므로, `git branch --show-current`가 기준 브랜치이고 작업 트리가 깨끗하며 fast-forward가 가능할 때만 `git pull --ff-only origin <기준 브랜치>`를 실행한다. 다른 작업 브랜치가 체크아웃된 상태에서는 pull하지 않고, fetch로 갱신한 `origin/<기준 브랜치>`(예: `origin/develop`, `origin/main`)를 참조한다. 기존 로컬 변경사항은 reset, checkout, clean, stash 등으로 강제 정리하지 않는다.
3. Android 작업 브랜치는 확인된 최신 `origin/develop`에서 시작한다. 로컬 변경사항 또는 분기가 있으면 강제 reset, checkout, clean을 수행하지 않는다. 필요하면 원격 추적 브랜치의 커밋을 별도로 참조한다.
4. 작업 보고서에 실제 참조한 각 브랜치와 전체 SHA, 그리고 원격 확인 또는 동기화 제한을 기록한다. 서버 API는 확인된 코드나 확정 계약을 근거로 사용한다.
5. iOS와 Server는 읽기와 최신화 용도로 사용한다. 소스 수정, 커밋, 푸시는 Android 작업 범위에 포함되지 않는다.
