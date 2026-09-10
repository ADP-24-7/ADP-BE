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

## Local Header Stub에서 CSRF 전체 비활성화 문제

초기 구현은 Local Header Stub을 켜면 Admin Security Chain 전체에 `csrf.disable()`을 적용했다. 제품 기본값이
`false`여도 정적 분석에서는 관리자 상태 변경 API의 CSRF 보호가 설정 분기에 따라 사라질 수 있는 High 위험으로 판단한다.

Admin Chain에는 모든 설정에서 `CookieCsrfTokenRepository`를 적용한다. Local Stub은 브라우저 Session을 사용하지 않고 매
요청에 `X-ADP-User-Id`와 `X-ADP-User-Roles`를 명시하는 테스트 harness이므로, 두 Header가 모두 있는 요청만 좁은
RequestMatcher로 CSRF 검사에서 제외한다. Header가 없거나 Session으로 인증하는 요청은 Local Stub 설정에서도 CSRF 검사를
받는다. Runtime API Key Chain의 CSRF 비활성화는 Cookie 인증을 사용하지 않는 stateless service boundary에만 유지한다.

## Session 만료와 권한 Snapshot

Admin Session에는 로그인 시점의 Institution, Role, Workload snapshot이 저장되며 기본 만료 시간은 30분이다. Session 도중
DB Role이 변경돼도 일반 관리자 API의 Spring Authority에는 즉시 반영되지 않는다. 감사 증적 반출처럼 고위험인 요청, 승인,
생성, 다운로드는 현재 DB Grant를 별도로 재검증한다. Production에서 모든 관리자 권한 회수를 즉시 반영해야 한다면 Principal
version 또는 IdP session revocation을 도입해야 한다.

FE는 보호 API에서 401을 받으면 CSRF token과 React Query cache를 제거하고 현재 URL을 `returnTo`로 보존해 로그인 화면으로
이동한다. 로그아웃 API가 실패해도 브라우저의 보호 데이터는 fail-closed로 제거한다.

## 검증 기준

- 잘못된 비밀번호 및 disabled Principal 로그인 거부
- 요청 Header/Body의 forged role 무시
- 로그인 후 서버 소유 Role과 Workload 반환
- CSRF 없는 관리자 POST 거부
- 로그아웃 후 기존 Session 재사용 거부
- Runtime API Key chain은 Session/CSRF와 독립적으로 유지
- Auditor 요청과 별도 Privileged Operator 승인을 실제 Session 전환으로 수행
- 5회 로그인 실패 후 잠금, 잠금 중 정상 비밀번호 거부, 잠금 만료 후 복구
- Auditor Session 요청 -> Logout -> Checker Session 승인 -> Worker 생성 -> Auditor 재로그인 다운로드

## 운영 고려사항

로컬 fixture의 데모 credential은 개발 환경에서만 주입한다. 운영 환경은 Secret Manager 또는 별도 계정 프로비저닝 절차로 BCrypt hash를 등록하고, `Secure=true`, TLS, 세션 저장소 및 계정 잠금 해제 절차를 함께 적용해야 한다. 존재하지 않는 계정뿐 아니라 disabled/locked 계정도 dummy BCrypt 검증을 수행해 계정 상태별 timing 차이를 줄인다.
