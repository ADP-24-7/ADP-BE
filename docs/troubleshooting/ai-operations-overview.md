# AI Operations Overview 트러블슈팅

## 참고 화면의 업무 부서와 SLA를 그대로 구현할 수 없었다

현재 Runtime은 `workload_id`, AI 모델 실행 시간과 통제 증적은 저장하지만 업무 부서 분류와 업무별 SLA 목표는
저장하지 않는다. 화면 완성도를 위해 임의의 부서나 SLA를 만들면 운영 Fact와 표현이 어긋난다.

- 부서별 차트 대신 권한 범위의 Workload와 데이터 클래스를 사용한다.
- 목표 시간이 없어 운영 판단에 도움이 제한적인 응답 지연 패널은 노출하지 않는다.
- Workload별 요청량과 정상·검토·차단/실패 비중을 정책 커버리지 Marimekko로 표시한다.
- 차단·검토 실행은 첫 번째 저장 Reason Code를 정책·승인, 데이터 범위, 목적·보유 범위, 목적지, 수치, 기타로
  분류해 Workload별 위반 구성을 비교한다. 한 실행의 복수 코드가 건수를 중복시키지 않도록 대표 사유만 사용한다.
- Workload별 `ALLOW`·`TRANSFORM` 판정 수와 `COMPLETED`·`EXTERNALLY_RECONCILED` 최종 처리 수를
  Dumbbell로 비교해 정책 통과 후 실행·응답 단계에서 남은 차이를 보여준다.

## AI 요청은 여러 Evidence 테이블에 나뉘어 있었다

하나의 실행 흐름을 표현하려면 Runtime 상태만으로는 데이터 조회, 최소화, 외부 AI, 응답 보호 단계를 구분할 수 없다.
Overview는 `runtime_execution`을 기준 Fact로 사용하고 `runtime_decision`, `data_access_event`, `transform_execution`,
`outbound_candidate`, `ai_model_execution_evidence`, `response_guard_result`의 존재와 저장 상태를 단계별로 연결한다.
Evidence가 없으면 성공으로 추정하지 않고 `증적 없음`, `검사 없음`, `호출 없음` 경로로 표시한다.
Workload는 각 단계에 반복하지 않고 흐름의 시작점에서 요청 단계로 한 번만 합류시켜 업무 비중과 전체 통제 경로를
동시에 읽을 수 있게 한다.

## 데이터 최소화와 응답 탐지 건수는 같은 분모가 아니다

`transform_field`는 필드 단위이고 `response_sensitive_finding`은 응답 탐지 항목 단위다. 두 수치를 하나의 구성비로
합산하면 의미가 왜곡된다. 데이터 최소화 효과 차트는 모든 데이터 클래스의 원본 필드와 `KEEP` 원문 유지 필드를
비교한다. 보호 대상 필드 변환율은 식별자·금융 데이터와 `UNKNOWN`만 분모에 포함하고, 그중 `KEEP` 이외의
변환·마스킹 전략이 적용된 필드 비율로 계산한다. `BUSINESS_METADATA`는 전체 구성 차트에는 표시하지만 보호 대상
비율에서는 제외한다. 응답 탐지 건수는 구성비에 합치지 않고 별도로 보여주며, 분류되지 않은 응답 탐지는
`UNKNOWN`으로 유지하고 임의의 데이터 클래스에 배정하지 않는다.

## 로컬 시연 데이터는 운영 데이터와 분리한다

AI Dashboard fixture는 `local-dashboard-fixtures=true`와 `data-provenance=SYNTHETIC`이 모두 충족될 때만
실행된다. 최근 14일에 정상 처리 비중을 높여 분산하고 정책 범위 위반, 수동 검토, Provider 실패·미확정,
응답 민감정보 탐지 케이스를 함께 저장한다. 원문·실명·Credential은 저장하지 않으며 식별 가능한 값은
결정적인 합성 ID 또는 SHA-256 digest만 사용한다. 화면 Mock이 아니라 PostgreSQL의 Runtime·Evidence 테이블을
조회하지만, 운영 고객 데이터로 해석해서는 안 된다.

## 통제 정상과 운영 조치는 다른 기준이다

통제 검증은 조회 기간에 각 단계의 증적이 실제 생성되었는지를 확인한다. 증적이 한 건 이상 연결되면 통제가
동작 중인 것으로 보고 `정상`으로 표시한다. 개별 실행의 실패·미확정·검토·차단은 정상 통제와 모순되지 않으며,
조회 기간 전체 실행을 집계한 운영 조치 요약과 우선 확인 항목에서 별도로 다룬다.

## 운영 우선순위는 최신순만으로 결정하지 않았다

응답 유출이나 Provider 실패가 단순 정책 차단보다 뒤로 밀리지 않도록 신호를
`응답 차단 -> Provider 실패 -> 결과 미확정 -> 정책 검토 -> 정책 차단` 순으로 정렬하고, 같은 등급에서는
최신 항목을 먼저 보여준다. 각 항목은 저장된 Reason Code와 조사할 Trace 구간을 함께 반환한다.

## 조회 범위와 성능 경계를 함께 적용했다

API는 최대 31일, 검색어 120자, 페이지 크기 50으로 제한한다. 모든 집계와 목록에 Institution 및 허용 Workload
조건을 적용하며 AI 실행용 partial index와 모델 지연시간 index로 기간 조회를 지원한다. FE의 MSW 응답은 테스트
전용이며 실제 화면은 PostgreSQL 집계 API만 사용한다.
