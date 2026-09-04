# Siliconic

[English](README.md) | 한국어

> 실리콘을 정제하고 클린룸을 설계해, 프로그래밍 가능한 웨이퍼 안에 실제로 동작하는 레드스톤 회로를 만드세요.

Siliconic은 반도체 제조, 클린룸 설계, 공장 자동화와 프로그래밍 가능한 웨이퍼 회로를 중심으로 하는 Minecraft Forge 1.20.1 기술 모드입니다.

## 개요

Siliconic은 다음 네 가지 시스템이 연결된 기술 발전 과정을 제공합니다.

- 원료 석영에서 고순도 실리콘, 논리 게이트와 등급별 웨이퍼로 이어지는 반도체 생산
- Minecraft 레드스톤과 상호작용하는 프로그래밍 가능한 9×9 웨이퍼 회로
- 구조, 내부 장비와 오염원이 정밀 제조에 영향을 주는 클린룸
- 발전, 에너지 분배, 물류 제어와 재료 회수

상세한 진행 과정과 장비 동작은 [게임플레이 가이드](docs/GAMEPLAY.kr.md)에서 확인할 수 있으며, [영문판](docs/GAMEPLAY.md)도 제공됩니다. 정확한 조합법은 게임 내 JEI 사용을 권장합니다.

## 실행 환경

| 항목 | 버전 |
| --- | --- |
| Minecraft | 1.20.1 |
| Minecraft Forge | 47.x (47.4.10 기준 개발) |
| Java | 17 |
| Just Enough Items | 15.20 이상, 선택 사항 |

## 설치

1. Minecraft 1.20.1용 Forge를 설치합니다.
2. 실행용 Siliconic JAR 파일을 Minecraft의 `mods` 폴더에 넣습니다.
3. 게임 내 조합법과 공정 안내가 필요하다면 호환되는 JEI를 선택적으로 설치합니다.
4. Forge 프로필로 Minecraft를 실행합니다.

멀티플레이에서는 서버와 모든 접속자가 같은 Siliconic 버전을 사용해야 합니다. JEI는 해당 인터페이스를 사용할 클라이언트에만 필요합니다.

## 문서

- [게임플레이 가이드](docs/GAMEPLAY.kr.md) · [English](docs/GAMEPLAY.md)
- [데이터팩 장비 공정 형식](docs/MACHINE_PROCESSES.kr.md) · [English](docs/MACHINE_PROCESSES.md)
- [변경 기록](CHANGELOG.md)

## 개발

JDK 17 환경에서 다음 명령으로 모드를 빌드합니다.

```powershell
.\gradlew.bat build
```

실행용 모드 파일은 `build/libs/siliconic-1.20.1-*.jar`에 생성됩니다. 파일명이 `-sources.jar`로 끝나는 결과물은 개발용이므로 `mods` 폴더에 설치하지 않습니다.

주요 개발 명령은 다음과 같습니다.

| 명령 | 용도 |
| --- | --- |
| `.\gradlew.bat runClient` | 개발용 클라이언트를 실행합니다. |
| `.\gradlew.bat runServer` | 로컬 개발 서버를 실행합니다. |
| `.\gradlew.bat runGameTestServer` | Forge 자동 회귀 테스트를 실행합니다. |
| `.\gradlew.bat runData` | 설정된 데이터 리소스를 생성합니다. |

소스 코드는 `src/main/java`, 기본 에셋과 데이터팩은 `src/main/resources`, 생성 리소스는 `src/generated/resources`에 있습니다. 장비 조합법은 데이터팩으로 추가하거나 덮어쓸 수 있으며, 자세한 형식은 위의 장비 공정 문서를 참고해 주세요.

## 개발 상태와 기여

Siliconic은 현재 1.0 이전 개발 단계입니다. 버전 사이에 월드 데이터 형식, 조합법, 밸런스와 장비 동작이 변경될 수 있으므로 중요한 월드는 업데이트 전에 백업해 주세요.

문제 제보와 제안은 [GitHub Issues](https://github.com/meister-mods/siliconic/issues)를 이용해 주세요. 기여 코드에는 변경된 게임 동작을 문서화하고 가능한 경우 회귀 테스트를 포함하며, `build`와 `runGameTestServer`를 모두 통과하는 것을 권장합니다.

## 라이선스

이 프로젝트의 이용 조건은 [LICENSE](LICENSE)를 참고해 주세요.
