# Slice 30 Local Integration Reproducibility

## 목표

BE Compose를 Local Integration Source of Truth로 유지하면서 특정 개발자 checkout이나 숨은 환경 설정에 의존하지 않는
재현 가능한 제품 실행 경계를 만든다.

## Repository Lock

`config/integration/repository-lock.json`은 FE, BE, DA, Docs의 검증된 Commit과 Flyway current version, 필수 입력 파일을
고정한다. `scripts/validate-integration-lock.py`는 다음 조건에서 기동 전에 실패한다.

- Peer Repository HEAD가 Lock Commit과 다름
- 추적 중인 파일에 commit되지 않은 변경이 있음
- 고정 Commit을 로컬 Git Object에서 찾을 수 없음
- DA Fixture, FE/Docs lockfile 등 필수 입력이 없음
- Flyway 최신 Version이 Lock과 다름

Lock을 보관하는 BE는 자기 참조 Commit을 만들 수 없으므로, Lock에 기록된 Commit 이후 변경이 Lock 파일 하나뿐일 때만
동일 source snapshot으로 인정한다. 실제 BE source가 변경되면 반드시 새 source Commit으로 Lock을 갱신해야 한다.

## 환경 Profile

| Profile | 용도 | Fixture/Mock | 보안 기본값 |
| --- | --- | --- | --- |
| `local` | 일상 개발 | 활성 | Local BFF와 preview 허용 |
| `demo` | 합성 데이터 제품 시연 | 활성, `SYNTHETIC` 표시 | Session 관리자 경계 유지 |
| `production-like` | 운영형 fail-closed 설정 확인 | 비활성 | freshness, SSRF, private destination 제한 |

Profile 파일에는 Secret을 저장하지 않는다. 개인 Secret은 Git에서 제외된 `.env`에만 두고 Make가 `.env` 다음에 선택한
Profile을 적용한다. Profile은 보안 토글을 결정하고 `.env`는 Credential 값만 제공한다.

## 실행

```bash
make integration-check INTEGRATION_PROFILE=demo
```

명령은 Lock/Compose 검증, 전체 build/start, container health 대기, BE/FE/DA/Docs endpoint 확인, 실제 Flyway version 확인을
순서대로 수행한다. 종료는 동일 Profile로 실행한다.

```bash
make integration-down INTEGRATION_PROFILE=demo
```

## Synthetic Provenance

Demo profile은 BE `/api/internal/info`에서 `runtimeProfile=demo`, `dataProvenance=SYNTHETIC`을 반환한다. FE도 같은 Compose
profile 값을 받아 로그인 화면과 운영 화면에 합성 데이터 사용 상태를 표시한다. Runtime Evidence는 기존 Version/Digest와
local fixture source reference를 유지하며 실제 고객 데이터로 표현하지 않는다.

## 완료 증거

- Lock drift/dirty tree/Flyway drift validator unit test
- 세 Profile의 `docker compose config --quiet`
- 빈 PostgreSQL에서 V1부터 current까지 Flyway 적용
- BE, FE, DA, Docs와 PostgreSQL health 확인
- Demo profile의 합성 데이터 provenance 확인
