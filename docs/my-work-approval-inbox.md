# My Work / Approval Inbox

## 목적

별도 마이페이지나 승인 엔진을 만들지 않고 기존 Audit Export workflow를 운영 업무 단위로 조회한다.

## 화면별 조회 계약

| View | 대상 | 범위 |
| --- | --- | --- |
| `MY_REQUESTS` | 감사 증적을 요청한 사용자 | 본인이 요청한 Job만 조회 |
| `APPROVAL_QUEUE` | `PRIVILEGED_OPERATOR` | `REQUESTED` 상태이면서 본인 요청이 아닌 Job |
| `HISTORY` | `PRIVILEGED_OPERATOR` | 권한 범위 내 전체 처리 이력 |

모든 조회는 `institution_id`와 호출자에게 허용된 `workload_id`를 SQL에서 강제한다. 승인 큐는
maker-checker 원칙에 따라 요청자 본인의 Job을 제외한다.

## 상태 해석

새 상태를 만들지 않고 `audit_export_job`의 기존 상태를 사용한다.

- 승인 대기: `REQUESTED`
- 생성 대기/생성 중: `APPROVED`, `GENERATING`
- 다운로드 가능: `READY`이면서 `downloaded_at`이 없음
- 다운로드 완료: `READY`이면서 `downloaded_at`이 있음
- 종료 상태: `REJECTED`, `FAILED`, `EXPIRED`, `REVOKED`

24시간 이상 승인 대기는 `REQUESTED` Job의 `created_at`을 기준으로 계산한다.

## API

```http
GET /api/v1/audit-exports?view=MY_REQUESTS&page=0&size=10
GET /api/v1/audit-exports?view=APPROVAL_QUEUE&page=0&size=10
GET /api/v1/audit-exports?view=HISTORY&page=0&size=10
GET /api/v1/audit-exports/work-summary
```

Monitoring은 집계와 업무 진입점만 제공한다. 승인과 반려 같은 상태 변경은 기존 Audit Export command API를
통해 Requests & Approvals 화면에서만 수행한다.

## 검증 기준

- Auditor의 본인 요청 조회와 Privileged Operator의 승인 큐 조회
- 본인 요청의 승인 큐 제외
- Institution 및 Workload SQL scope
- 24시간 이상 대기 집계
- V51 조회 인덱스 생성
- 기존 승인, 생성, 다운로드 command 회귀
