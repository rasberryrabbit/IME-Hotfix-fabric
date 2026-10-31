# IME-HotFix-Fabric

마인크래프트 Fabric용 IME(입력기) 지원 모드. 한국어/일본어/중국어 IME로
텍스트 필드에 입력할 때 조합 텍스트가 인라인으로 표시되고, 검색 상자가
조합 중에도 실시간 필터링되며, 표지판 자동 줄바꿈과 책 자동 페이지 넘김이
추가됩니다.

원본 Forge 모드 [IME Hotfix](https://github.com/antimeme/imehotfix) by
Antimeme Productions (LGPL-3.0-or-later)의 방법론을 참고하여 Fabric용으로
새로 작성했습니다.

## 요구 사항

| 구성 | 버전 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.16.0+ |
| Java | 25+ |

**Fabric API 불필요** — 이 모드는 Fabric Loader만 있으면 동작합니다.

## 기능

- **인라인 조합 표시**: IME 조합 텍스트가 텍스트 필드 안에 커서 위치에 표시
- **조합 중 검색 필터링**: 크리에이티브 탭 검색 등에서 조합 확정 전에도 검색 반응
- **표지판 자동 줄바꿈**: 글자가 한 줄에 안 들어가면 다음 줄로 이동 (바닐라는 버림)
- **책 자동 페이지 넘김**: 한 페이지에 안 들어가면 다음 페이지로 (바닐라는 버림)
- **Windows IMM32 백엔드**: Windows에서 네이티브 IME API 직접 제어 (JNA 사용)

## 설치

1. Minecraft 26.2에 Fabric Loader 설치
2. `imehotfix-fabric-1.2.0.jar`을 `.minecraft/mods/` 폴더에 복사
3. 게임 실행

## 설정

`.minecraft/config/imehotfix.properties` (최초 실행 시 자동 생성):

```properties
# 주요 설정
inlinePreedit=true              # 인라인 조합 표시
filterWithComposition=true      # 조합 중 검색 필터링
signAutoWrap=true               # 표지판 자동 줄바꿈
bookAutoPage=true               # 책 자동 페이지 넘김
commitCompositionOnBlur=true    # 포커스 잃을 때 조합 확정
```

## 빌드

소스에서 직접 빌드하는 방법은 [build.md](build.md)를 참조하세요.

```bash
gradle wrapper --gradle-version 9.8.0
./gradlew build
# 결과: build/libs/imehotfix-fabric-1.2.0.jar
```

JDK 25 이상 필요. 빌드 환경 상세 설정은 `build.md`에 정리되어 있습니다.

## 라이선스

LGPL-3.0-or-later. 원본 모드와 동일한 라이선스를 따릅니다.
