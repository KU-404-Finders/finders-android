# Release Retrofit/Gson 재현 테스트

[ReleaseRetrofitProbeTest](../app/src/androidTest/java/com/ku/lostandfound/ui/ReleaseRetrofitProbeTest.kt)는 이미 빌드된 Release APK의 R8 DEX를 그대로 로드합니다. 수정 전 Gson 오류를 재현하려면 `before`, 수정 후 응답 변환을 검증하려면 `after`로 실행합니다.

아래 명령은 저장소 루트에서 Windows PowerShell로 실행합니다. Android SDK, Python 3.9 이상, ADB로 연결된 테스트 기기 또는 에뮬레이터가 필요합니다. 기존 검증에는 Android 14 에뮬레이터 `emulator-5580`을 사용했습니다.

## 1. Release APK와 매핑 준비

- `before`는 Gson 보존 규칙을 수정하기 전의 R8 Release APK입니다. 기존 재현에는 [`55b4b0f`](https://github.com/KU-404-Finders/finders-android/commit/55b4b0f7b12e5ae536d70fa2d0b40f2ae1c9f4fd) 기준으로 보존한 `1.1.1` / code `8` APK와 매핑을 사용했습니다. 기존 산출물이 없다면 해당 리비전의 별도 체크아웃에서 아래 빌드 명령을 실행합니다.
- `after`는 보존 규칙이 적용된 PR 브랜치의 R8 Release APK입니다. PR 체크아웃에서 아래 빌드 명령을 실행합니다.

```powershell
.\gradlew.bat :app:assembleRelease
```

각 빌드에서 APK는 `app/build/outputs/apk/release/`의 `output-metadata.json`에 표시된 `outputFile`을 사용합니다. 일반적으로 `app-release-unsigned.apk` 또는 `app-release.apk`입니다. 매핑은 `app/build/outputs/mapping/release/mapping.txt`입니다. 다음 빌드가 산출물을 덮어쓰기 전에 APK와 매핑을 한 쌍으로 복사해 보관합니다.

**각 APK에는 반드시 그 APK의 DEX를 생성한 R8 빌드의 `mapping.txt`를 사용합니다.** 버전·커밋이 같아도 별도 빌드의 매핑으로 대체하지 않습니다. 기존 재현에서는 DEX 스택의 `r8-map-id-…`와 `mapping.txt`의 `pg_map_id`를 비교해 일치를 확인했습니다. 프로브는 Release APK를 입력 파일로 읽으므로 미서명 APK도 사용할 수 있습니다.

PR 체크아웃의 루트에서 다음과 같이 두 쌍을 복사할 수 있습니다. `$beforeCheckout`은 위에서 빌드한 수정 전 체크아웃의 경로입니다. 기존 백업을 사용한다면 `before` 빌드 대신 백업한 APK와 매핑을 예제 경로에 각각 복사합니다.

```powershell
function Copy-ReleaseProbePair {
    param([string]$Checkout, [string]$Destination)

    $apkDirectory = Join-Path $Checkout 'app/build/outputs/apk/release'
    $metadata = Get-Content (Join-Path $apkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    New-Item -ItemType Directory -Force $Destination | Out-Null
    Copy-Item (Join-Path $apkDirectory $metadata.elements[0].outputFile) (Join-Path $Destination 'release.apk') -ErrorAction Stop
    Copy-Item (Join-Path $Checkout 'app/build/outputs/mapping/release/mapping.txt') (Join-Path $Destination 'mapping.txt') -ErrorAction Stop
}

$beforeCheckout = '..\finders-android-before' # 실제 체크아웃 경로로 변경
Copy-ReleaseProbePair $beforeCheckout 'build/release-probe/before'
Copy-ReleaseProbePair '.' 'build/release-probe/after'
```

각 매핑에서 [`release_probe_mapping.py`](release_probe_mapping.py)로 테스트가 사용할 심볼 파일을 생성합니다.

```powershell
$beforeApk = 'build/release-probe/before/release.apk'
$beforeMapping = 'build/release-probe/before/mapping.txt'
$afterApk = 'build/release-probe/after/release.apk'
$afterMapping = 'build/release-probe/after/mapping.txt'
$beforeSymbols = 'build/release-probe/before/symbols.properties'
$afterSymbols = 'build/release-probe/after/symbols.properties'

python tools/release_probe_mapping.py $beforeMapping $beforeSymbols
if ($LASTEXITCODE -ne 0) { throw 'before 매핑 변환 실패' }
python tools/release_probe_mapping.py $afterMapping $afterSymbols
if ($LASTEXITCODE -ne 0) { throw 'after 매핑 변환 실패' }
```

기존 백업의 before 쌍은 `build/gson-r8-before/apk/app-release.apk`와 `build/gson-r8-before/mapping/mapping.txt`에 있었습니다. 이 경로와 실행 로그는 로컬 산출물이므로 새 체크아웃에는 자동으로 생성되지 않습니다.

## 2. Debug 앱과 테스트 APK 설치

**PR 체크아웃**에서 프로브가 포함된 테스트 APK와 Debug 앱을 빌드합니다. 이미 같은 체크아웃의 APK가 준비되어 있으면 설치 단계부터 진행합니다. `run-as`를 사용하므로 기기에는 debuggable Debug 앱이 설치되어 있어야 합니다. 같은 패키지의 다른 서명 APK가 설치되어 있다면 별도의 테스트 에뮬레이터를 사용합니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Debug 빌드 실패' }

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
$device = 'emulator-5580' # adb devices에 표시된 대상 ID로 변경

function Invoke-ProbeAdb {
    & $adb -s $device @args
    if ($LASTEXITCODE -ne 0) { throw "ADB 명령 실패: $($args -join ' ')" }
}

Invoke-ProbeAdb install -r app/build/outputs/apk/debug/app-debug.apk
Invoke-ProbeAdb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

SDK가 다른 곳에 설치되어 있으면 `$adb`를 해당 경로의 `adb.exe`로 지정합니다.

## 3. 입력 파일 복사와 실행

ADB로 임시 디렉터리에 업로드하고, 셸 파이프와 `run-as`를 사용해 **Debug 앱의** `files/release-probe/`에 복사합니다. 프로브가 `release.apk`를 읽기 전용으로 바꾸므로, 다음 실행에서는 새 파일에 먼저 쓴 뒤 이름을 바꿔 교체합니다. 각 모드의 실행이 끝난 뒤 다음 쌍을 복사합니다.

```powershell
function Set-ReleaseProbeInput {
    param([string]$Apk, [string]$Symbols)

    Invoke-ProbeAdb shell run-as com.ku.lostandfound mkdir -p files/release-probe
    Invoke-ProbeAdb push $Apk /data/local/tmp/finders-release-probe.apk
    Invoke-ProbeAdb push $Symbols /data/local/tmp/finders-release-probe.properties
    Invoke-ProbeAdb shell "cat /data/local/tmp/finders-release-probe.apk | run-as com.ku.lostandfound sh -c 'cat > files/release-probe/new.apk && mv -f files/release-probe/new.apk files/release-probe/release.apk'"
    Invoke-ProbeAdb shell "cat /data/local/tmp/finders-release-probe.properties | run-as com.ku.lostandfound sh -c 'cat > files/release-probe/symbols.properties'"
}

$testClass = 'com.ku.lostandfound.ui.ReleaseRetrofitProbeTest'
$runner = 'com.ku.lostandfound.test/androidx.test.runner.AndroidJUnitRunner'

Set-ReleaseProbeInput $beforeApk $beforeSymbols
Invoke-ProbeAdb shell am instrument -w -r -e releaseProbe before -e class $testClass $runner
Invoke-ProbeAdb logcat -d -s ReleaseRetrofitProbe

Set-ReleaseProbeInput $afterApk $afterSymbols
Invoke-ProbeAdb shell am instrument -w -r -e releaseProbe after -e class $testClass $runner
```

## 4. 결과 판정

`before`에서는 기존 `releaseWireConversion`만 실행합니다. `after`에서는 기존 프로브와 `releasePostWritesAndBlockNames`가 모두 종료 상태 `INSTRUMENTATION_STATUS_CODE: 0`이어야 하며 JUnit 요약은 **`OK (2 tests)`**입니다. `releaseProbe`가 없거나 지정값이 다르면 건너뜁니다. 실제 종료 상태를 확인합니다.

| 모드 | 통과 조건 |
| --- | --- |
| `before` | 로그인과 비밀번호 재설정 요청의 합성 성공 응답이 각각 `JsonIOException`을 발생시키고 예외 메시지에 `Abstract`가 포함됩니다. 예상 예외 두 건이 현재 실행의 `ReleaseRetrofitProbe` Logcat에 기록되며, 테스트 자체는 통과합니다. 기존 로그에서는 `h01`과 `Abstract classes can't be instantiated!`가 표시됐습니다. 난독화 이름은 빌드마다 달라질 수 있습니다. |
| `after` | 로그인 토큰, HTTP 400·401·403·429 상태, 비밀번호 재설정 요청·검증·확정, 채팅 목록의 제네릭 응답, 토큰 갱신 및 새 토큰 저장을 검증합니다. |

기존 비교 실행은 `build/gson-probe-before-verified.log`와 `build/code9-release-probe.log`에서 각각 `OK (1 test)`로 끝났습니다. 필요한 클래스나 메서드를 찾지 못하면 APK와 매핑의 짝, `symbols.properties` 생성 및 복사 여부를 먼저 확인합니다.

## 실서버 E2E와의 차이

Debug 앱의 계측 러너가 지정된 Release APK의 DEX를 별도 클래스 로더로 실행합니다. OkHttp 인터셉터가 네트워크 전송 전에 합성 응답을 반환하므로, 이 테스트는 Release의 Retrofit/Gson 응답 변환과 R8 보존 규칙을 검증합니다. 실서버 연결, 계정 인증, 이메일 수신, 화면 이동, Release 설치·서명 검증은 별도 E2E 또는 기기 테스트에서 확인합니다.
