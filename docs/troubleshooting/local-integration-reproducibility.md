# Local Integration Reproducibility Troubleshooting

## 인접 Repository를 최신 상태로 가정하던 문제

기존 Compose는 `../ADP-FE`, `../ADP-DA`, `../ADP-Docs`를 직접 mount했지만 어느 Commit인지 확인하지 않았다. 같은 명령을
실행해도 팀원별 checkout에 따라 API Contract, Fixture, 화면이 달라질 수 있었다.

Repository Lock과 preflight validator를 추가해 실제 HEAD, dirty tracked file, 필수 입력, Flyway current를 기동 전에
검증한다. 개발 중 임의 drift를 자동 checkout하거나 reset하지 않고 원인을 출력한 뒤 fail closed한다.

## BE가 자신의 Commit SHA를 Lock하는 순환 문제

Lock 파일 안에 현재 BE Commit을 기록하면 Lock 파일 변경 자체가 다음 Commit을 만들기 때문에 HEAD와 기록값이 영원히
일치할 수 없다. 따라서 BE 항목은 마지막 source Commit을 기록하고, 그 이후 diff가 Lock 파일 하나뿐일 때만 허용한다.
애플리케이션 source나 Compose가 바뀌면 validator가 거부하므로 새 source Commit으로 Lock을 갱신해야 한다.

## `.env` 하나에 개발 편의와 운영형 설정이 섞이던 문제

Credential과 환경별 보안 토글을 같은 파일에서 관리하면 production-like 검증에서도 fixture, mock, private destination 허용이
남을 수 있다. Secret은 ignored `.env`, 비밀이 아닌 안전 설정은 versioned profile로 분리했다. Make는 `.env`를 먼저 읽고
선택한 profile을 나중에 적용해 profile의 보안 설정이 우선하도록 한다.

## Container 기동 성공을 제품 검증으로 오인하던 문제

단순 `docker compose up`은 Flyway current, 실제 endpoint, profile provenance를 증명하지 않는다. `integration-check`는
health 대기 후 네 서비스 endpoint, BE runtime profile, Demo synthetic provenance, PostgreSQL Flyway version을 별도로
확인한다.
