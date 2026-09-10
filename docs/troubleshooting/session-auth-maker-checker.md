# Session Authentication and Maker-Checker Closure

## 문제

로컬 콘솔은 Vite proxy가 `X-ADP-User-Id`, `X-ADP-User-Roles`를 자동 주입해 관리자 API를 호출했다. 이 방식은 화면 확인에는 편리하지만 브라우저가 역할을 선택할 수 있고, 서로 다른 사용자로 수행해야 하는 감사 증적 반출의 Maker-Checker 흐름을 증명할 수 없다.

또한 하나의 stateless Security Filter Chain에서 Runtime API Key와 관리자 사용자를 함께 처리해 Session 인증과 CSRF를 도입하기 어려웠다.

## 원인

- Service Principal과 Browser User 인증의 수명 주기 및 위협 모델이 분리되지 않았다.
- 관리자 역할을 서버 DB가 아니라 개발용 요청 Header가 결정했다.
- 사용자 전환 후에도 브라우저 Query cache가 유지될 수 있었다.
- Session Cookie 기반 상태 변경 API에 필요한 CSRF 경계가 없었다.

## 해결

인증 경계를 두 개의 Security Filter Chain으로 분리했다.

```text
Runtime / Internal / Actuator
  -> X-ADP-API-Key
  -> STATELESS

Auth / Admin / Audit Export
  -> JSESSIONID
  -> IF_REQUIRED
  -> CSRF
```

`auth_user_credential`에는 BCrypt hash와 실패 횟수, 잠금 시각만 저장한다. 로그인 성공 시 역할, 기관, Workload는 기존 `auth_principal`, `auth_principal_role`, `auth_principal_workload`에서 다시 조회한다. FE는 역할을 전송하지 않는다.

관리자 인증 API는 `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`, `/api/auth/csrf`로 고정했다. Session Cookie는 HttpOnly/SameSite=Lax를 사용하고 관리자 mutation은 `XSRF-TOKEN`과 `X-XSRF-TOKEN`으로 보호한다.

기존 User Header 인증은 `adp.local-user-auth.enabled=true`인 테스트 harness에서만 유지하고 Docker 기본값은 `false`로 변경했다.

## 검증 기준

- 잘못된 비밀번호 및 disabled Principal 로그인 거부
- 요청 Header/Body의 forged role 무시
- 로그인 후 서버 소유 Role과 Workload 반환
- CSRF 없는 관리자 POST 거부
- 로그아웃 후 기존 Session 재사용 거부
- Runtime API Key chain은 Session/CSRF와 독립적으로 유지
- Auditor 요청과 별도 Privileged Operator 승인을 실제 Session 전환으로 수행

## 운영 고려사항

로컬 fixture의 데모 credential은 개발 환경에서만 주입한다. 운영 환경은 Secret Manager 또는 별도 계정 프로비저닝 절차로 BCrypt hash를 등록하고, `Secure=true`, TLS, 세션 저장소 및 계정 잠금 해제 절차를 함께 적용해야 한다.
