# 빌드 가이드

IME-HotFix-Fabric 모드 jar를 처음부터 빌드하는 전체 과정을 정리합니다.

## 1. 사전 줄비물

| 도구 | 버전 | 다운로드 |
| --- | --- | --- |
| JDK | 25 이상 (26 권장) | https://adoptium.net/ |
| Gradle | 9.8.0+ | https://gradle.org/releases/ |
| 인터넷 | Loom이 MC 클라이언트 jar(~40 MB)와 라이브러리들을 다운로드해야 함 | |

JDK 25가 필요한 이유: Minecraft 26.2가 Java 25 이상을 요구합니다.
Loom 1.19.3은 Java 25+에서 동작합니다.

## 2. 환경 변수 설정

```bash
export JAVA_HOME=/path/to/jdk-26
export PATH="$JAVA_HOME/bin:/path/to/gradle/bin:$PATH"

# 확인
java -version    # 25 또는 26 표시
gradle --version # 9.8+ 표시
```

## 3. 소스 코드 준비

```bash
unzip imehotfix-fabric-src.zip
cd imehotfix-fabric
```

디렉터리 구조:
```
imehotfix-fabric/
├── build.gradle                         # 빌드 스크립트
├── settings.gradle                      # 저장소 설정 (미러 포함)
├── gradle.properties                    # 버전 번호
├── gradle/wrapper/gradle-wrapper.properties
├── LICENSE
├── README.md
├── build.md                             # 이 파일
└── src/main/
    ├── java/jp/antimeme/mc/imehotfix/
    │   ├── core/          (10개 파일)   # 플랫폼 독립 IME 로직
    │   ├── core/win32/    (6개 파일)    # Windows JNA 백엔드
    │   └── fabric/        (5개 파일)    # Fabric 진입점
    │       └── mixin/     (7개 파일)    # 입력기 믹스인
    └── resources/
        ├── fabric.mod.json               # 모드 메타데이터
        ├── imehotfix.mixins.json         # 믹스인 설정
        └── pack.mcmeta
```

## 4. Gradle Wrapper 생성 (최초 1회)

소스 트리에는 `gradlew` 스크립트가 포함되어 있지 않습니다. 시스템에
설치된 Gradle로 wrapper를 생성합니다:

```bash
gradle wrapper --gradle-version 9.8.0
```

이 명령은 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`을
생성합니다. 이후부터는 `./gradlew`를 사용합니다.

## 5. 빌드

### 5.1 기본 빌드

```bash
./gradlew build
```

또는 시스템 Gradle을 직접 사용:

```bash
gradle build
```

### 5.2 결과물

```
build/libs/
├── imehotfix-fabric-1.2.0.jar           # 배포용 모드 jar (~54 KB)
└── imehotfix-fabric-1.2.0-sources.jar   # 소스 jar (~30 KB)
```

## 6. Maven Central 429 문제 해결

### 6.1 문제

Loom이 MC 26.2의 라이브러리 의존성 수십 개를 동시에 다운로드할 때
Maven Central(`repo.maven.apache.org`)이 **429 Too Many Requests**를
반환합니다. IP 기반 rate limit으로, 한 번 걸리면 수 분 동안 모든
요청이 차단됩니다.

### 6.2 해결: 저장소 미러 사용

`settings.gradle`에 아시아 미러들을 우선 순위로 등록합니다:

```gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.fabricmc.net/' }          // Fabric
        maven { url 'https://libraries.minecraft.net/' }      // Mojang
        maven { url 'https://maven.aliyun.com/repository/public' }  // Aliyun
        maven { url 'https://mirrors.cloud.tencent.com/maven/' }    // Tencent
        maven { url 'https://repo.huaweicloud.com/repository/maven/' } // Huawei
        maven { url 'https://repo.maven.apache.org/maven2/' } // Maven Central (최후)
    }
}
```

### 6.3 Loom 자동 추가 저장소 문제

Loom은 빌드 구성 단계에서 자체 저장소(`LoomLocalMinecraft`,
`LoomLocalRemappedMods`, `Fabric`, `Mojang`, `MavenRepo`)를 자동으로
추가합니다. 이 중 `MavenRepo`가 429에 걸리면 **비활성화**되고,
이후 모든 의존성 해석이 실패합니다.

`settings.gradle`의 `dependencyResolutionManagement`만으로는 Loom이
추가한 저장소를 완전히 제어할 수 없습니다. 이 경우 **init.gradle**
스크립트로 모든 프로젝트에 미러를 강제 주입합니다:

```gradle
// init.gradle — 프로젝트 루트에 저장
allprojects {
    buildscript {
        repositories {
            maven { url 'https://maven.fabricmc.net/' }
            maven { url 'https://maven.aliyun.com/repository/public' }
            maven { url 'https://mirrors.cloud.tencent.com/maven/' }
            maven { url 'https://repo.huaweicloud.com/repository/maven/' }
            maven { url 'https://repo.maven.apache.org/maven2/' }
        }
    }
    repositories {
        maven { url 'https://maven.fabricmc.net/' }
        maven { url 'https://libraries.minecraft.net/' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://mirrors.cloud.tencent.com/maven/' }
        maven { url 'https://repo.huaweicloud.com/repository/maven/' }
        maven { url 'https://repo.maven.apache.org/maven2/' }
    }
}
```

빌드 시 `--init-script` 옵션으로 적용:

```bash
gradle --init-script init.gradle build
```

또는 `~/.gradle/init.d/` 디렉터리에 넣으면 자동 적용됩니다:

```bash
mkdir -p ~/.gradle/init.d
cp init.gradle ~/.gradle/init.d/mirrors.gradle
```

## 7. Loom no-remap 모드

### 7.1 MC 26.2의 특수성

Minecraft 26.2는 **비난독화(unobfuscated)** 상태로 배포됩니다.
클라이언트 jar의 클래스 이름이 이미 Mojmap(Mojang 공식 매핑) 이름으로
되어 있어서, 이전 버전처럼 obfuscated → named 변환(remap)이 필요
없습니다.

Mojang의 버전 매니페스트에서 MC 26.2는 `client_mappings` URL을
제공하지 않습니다. 이전 버전에서는 이 URL로 proguard 매핑 파일을
다운로드했지만, 26.2부터는 매핑 파일 자체가 필요 없습니다.

### 7.2 플러그인 ID 선택

Loom 1.19부터 플러그인 ID가 두 개로 분리되었습니다:

| 플러그인 ID | 모드 | 사용 시기 |
| --- | --- | --- |
| `net.fabricmc.fabric-loom` | **no-remap** | MC 26.2+ (비난독화 jar) |
| `net.fabricmc.fabric-loom-remap` | remap | 구 버전 MC (난독화 jar) |
| `fabric-loom` (legacy) | remap | Loom 2.0에서 제거 예정 |

`build.gradle`에서 no-remap 플러그인을 사용합니다:

```gradle
plugins {
    id 'net.fabricmc.fabric-loom' version '1.19.3'
}
```

no-remap 모드의 특징:
- `mappings` 선언 불필요 (Yarn/Mojmap 다운로드 안 함)
- `modImplementation` 대신 일반 `implementation` 사용
- 매니페스트에 `Fabric-Mapping-Namespace: official` 표시

### 7.3 클라이언트 전용 jar

이 모드는 `fabric.mod.json`에 `"environment": "client"`로 선언되어
있어 클라이언트 전용입니다. `build.gradle`에서 Loom에게 클라이언트
jar만 사용하도록 지시합니다:

```gradle
loom {
    clientOnlyMinecraftJar()
}
```

이 설정으로 서버 jar 다운로드와 client+server 병합 단계를 생략합니다.

## 8. 의존성

### 8.1 런타임 의존성 (사용자가 설치해야 하는 것)

| 의존성 | 버전 | 필요성 |
| --- | --- | --- |
| Minecraft | 26.2 | 게임 자체 |
| Fabric Loader | 0.16.0+ | 모드 로딩 |
| Java | 25+ | MC 26.2 요구 |

**Fabric API는 필요 없습니다.** 이 모드는 Fabric API를 사용하지 않습니다.

### 8.2 컴파일 의존성 (빌드 시점만 필요)

| 의존성 | 버전 | 용도 |
| --- | --- | --- |
| `com.mojang:minecraft` | 26.2 | Loom이 MC 클라이언트 jar 제공 |
| `net.fabricmc:fabric-loader` | 0.20.1 | `ClientModInitializer` 인터페이스 |
| `net.java.dev.jna:jna` | 5.17.0 | Windows IME 백엔드 (`com.sun.jna.*`) |
| `net.java.dev.jna:jna-platform` | 5.17.0 | Windows IME 백엔드 (`com.sun.jna.platform.win32.*`) |

JNA는 `compileOnly`로 선언합니다. MC 26.2가 런처 라이브러리로
JNA 5.17.0을 이미 포함하고 있어서, 런타임에는 MC의 JNA가 사용됩니다.
모드 jar에 JNA를 번들할 필요가 없습니다.

## 9. 빌드 검증

빌드가 성공했는지 확인:

```bash
# 1. jar 파일 존재 확인
ls -la build/libs/imehotfix-fabric-1.2.0.jar

# 2. jar 내용 확인
jar tf build/libs/imehotfix-fabric-1.2.0.jar | head -20

# 3. fabric.mod.json 확인
unzip -p build/libs/imehotfix-fabric-1.2.0.jar fabric.mod.json | python3 -m json.tool

# 4. 매니페스트 확인
unzip -p build/libs/imehotfix-fabric-1.2.0.jar META-INF/MANIFEST.MF
```

매니페스트에 다음 라인이 있어야 정상:

```
Fabric-Loom-Version: 1.19.3
Fabric-Minecraft-Version: 26.2
Fabric-Loader-Version: 0.20.1
Fabric-Mapping-Namespace: official
```

## 10. 설치

1. Minecraft 26.2에 Fabric Loader 0.16.0+ 설치
2. `imehotfix-fabric-1.2.0.jar`을 `.minecraft/mods/` 폴더에 복사
3. 게임 실행

## 11. 문제 해결

### 11.1 "Could not find method modImplementation()"

no-remap 모드에서는 `modImplementation`을 사용할 수 없습니다.
`build.gradle`에서 `implementation`을 사용하세요.

### 11.2 "Failed to find official mojang mappings for 26.2"

remap 모드 플러그인(`fabric-loom-remap` 또는 legacy `fabric-loom`)을
사용하고 있습니다. `net.fabricmc.fabric-loom` 플러그인 ID로 변경하세요.

### 11.3 "minecraft-merged-a8e9c7b123:26.2 not found"

`clientOnlyMinecraftJar()` 설정 없이 빌드하고 있습니다.
`build.gradle`의 `loom` 블록에 추가하세요.

### 11.4 Maven Central 429 Too Many Requests

6절의 init.gradle 미러 설정을 참조하세요.

### 11.5 "Java 25 or newer JVM required"

Loom 1.19.3이 Java 25+를 요구합니다. JDK 25 또는 26을 설치하고
`JAVA_HOME`을 설정하세요.

### 11.6 Loom 캐시 손상

이전 빌드의 캐시가 남아 있으면 문제가 발생할 수 있습니다:

```bash
rm -rf .gradle build
rm -rf ~/.gradle/caches/fabric-loom
gradle build
```
