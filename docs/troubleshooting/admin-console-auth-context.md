# Admin Console 인증 컨텍스트 경계

## 증상

관리자 Read API는 Local User Header로 정상 호출되지만 FE가 현재 Role을 확인하려고
`GET /api/internal/auth/context`를 호출하면 401이 발생했다. 화면은 데이터를 읽을 수
있어도 Recovery·Policy 명령의 실행 가능 여부를 사전에 설명할 수 없었다.

## 원인

`UserHeaderAuthenticationFilter`는 보안상 `/api/admin/**`에만 적용된다. 기존 인증
컨텍스트 endpoint는 Service Principal용 `/api/internal/auth/context`뿐이어서 관리자
인증 경계와 endpoint namespace가 일치하지 않았다. FE proxy에서 Header만 바꿔도
필터가 실행되지 않으므로 해결되지 않는다.

## 해결

- Service Principal용 `/api/internal/auth/context`는 호환성을 위해 유지한다.
- 관리자용 `/api/admin/auth/context`를 동일 Controller의 별도 경로로 제공한다.
- 응답에 Principal ID·Type·Display Name·Institution·Role·Workload Scope만 제공하고
  credential 원문은 노출하지 않는다.
- 관리자 endpoint는 Local User Header 또는 배포 환경의 Admin 인증 경계에서만
  인증되며, 무인증 요청은 공통 401 계약으로 차단한다.

## 회귀 방지

`AuthContextControllerTests`에서 Service Principal과 Admin User 경로를 각각 검증하고,
Admin 인증 정보가 없는 요청이 401인지 확인한다. FE는 이 응답으로 Action eligibility를
표현하되 최종 인가는 항상 BE가 다시 검증한다.
