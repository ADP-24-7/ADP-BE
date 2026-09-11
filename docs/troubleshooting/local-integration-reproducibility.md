# Local Integration Reproducibility Troubleshooting

## 인접 Repository를 최신 상태로 가정하던 문제

기존 Compose는 `../ADP-FE`, `../ADP-DA`, `../ADP-Docs`를 직접 mount했지만 어느 Commit인지 확인하지 않았다. 같은 명령을
실행해도 팀원별 checkout에 따라 API Contract, Fixture, 화면이 달라질 수 있었다.

Repository Lock과 preflight validator를 추가해 실제 HEAD, dirty tracked/untracked file, 필수 입력, Flyway current를 기동 전에
검증한다. 개발 중 임의 drift를 자동 checkout하거나 reset하지 않고 원인을 출력한 뒤 fail closed한다.

## BE가 자신의 Commit SHA를 Lock하는 순환 문제

Lock 파일 안에 현재 BE Commit을 기록하면 Lock 파일 변경 자체가 다음 Commit을 만들고, squash merge 후에는 중간 Commit이
remote에서 사라질 수 있다. BE는 Commit 대신 Git index의 canonical source digest를 사용하고 Lock 파일 자체는 digest에서
제외한다. 이 방식은 merge 전략과 무관하며 애플리케이션 source나 Compose가 바뀌면 validator가 거부한다.

## Untracked source가 Docker image에 포함되던 문제

기존 dirty 검사는 `--untracked-files=no`를 사용했지만 BE Dockerfile의 `COPY src`와 FE Dockerfile의 `COPY .`는 untracked
source도 image에 포함한다. 따라서 commit 조합이 같아도 실행 결과가 달라질 수 있었다. validator는 이제 ignored 파일을
제외한 모든 working tree 변경을 거부한다. 로컬 Secret인 `.env`처럼 허용할 파일은 `.gitignore`로 명시하고, untracked
Java/TypeScript source는 통합 실행 전에 반드시 commit한다.

## 잘못된 환경 값이 운영형 기본값으로 바뀌던 문제

`demmo` 같은 오타를 `production-like`로, 잘못된 provenance를 `NONE`으로 조용히 바꾸면 화면 표시와 실제 실행 조건이
달라질 수 있다. 값이 누락된 경우에만 문서화된 기본값을 사용하고, 명시된 값이 허용 목록에 없으면 BE bean 생성과 FE 환경
파싱 단계에서 즉시 실패하도록 변경했다.

## `.env` 하나에 개발 편의와 운영형 설정이 섞이던 문제

Credential과 환경별 보안 토글을 같은 파일에서 관리하면 production-like 검증에서도 fixture, mock, private destination 허용이
남을 수 있다. Secret은 ignored `.env`, 비밀이 아닌 안전 설정은 versioned profile로 분리했다. Make는 `.env`를 먼저 읽고
선택한 profile을 나중에 적용해 profile의 보안 설정이 우선하도록 한다.

## Container 기동 성공을 제품 검증으로 오인하던 문제

단순 `docker compose up`은 Flyway current, 실제 endpoint, profile provenance를 증명하지 않는다. `integration-check`는
health 대기 후 네 서비스 endpoint, BE runtime profile, Demo synthetic provenance, PostgreSQL Flyway version을 별도로
확인한다.

## Flyway current가 V49인데 V9로 판정된 문제

`flyway_schema_history.version`은 문자열 컬럼이므로 SQL `max(version)`은 숫자 순서가 아니라 사전순으로 계산된다. 그 결과
V1부터 V49까지 적용된 DB에서 `9`가 반환됐다. 숫자로만 구성된 Version을 `integer`로 변환한 뒤 `max()`를 계산하도록
검증 Query를 수정했다. Migration 성공 여부와 검증 도구의 Version 비교 방식을 분리해서 확인해야 한다.
