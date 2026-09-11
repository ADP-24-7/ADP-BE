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

## 정렬과 우선 처리

- `APPROVAL_QUEUE`: SLA 초과 요청을 포함해 접수 시각이 오래된 순
- `MY_REQUESTS`: 다운로드 가능, 실패/반려 확인, 승인 대기, 생성 진행, 완료 순. 승인 대기끼리는 오래된 순
- `HISTORY`: 최근 상태 변경 순

정렬은 페이지네이션 전에 DB에서 수행해 다음 페이지의 오래된 요청이 최신 요청 뒤로 밀리지 않도록 한다.

## 승인 취소와 폐기

`PRIVILEGED_OPERATOR`는 잘못 승인된 반출을 `APPROVED`, `GENERATING`, `READY` 상태에서 `REVOKE`할 수 있다.
폐기 사유는 필수이며 기존 승인 근거를 대체해 운영 이력에 남는다. `GENERATING` 폐기는 Worker lease를 해제하고,
실제 결과 파일이 존재하는 `READY` 폐기는 content를 즉시 삭제한 뒤 `AUDIT_EXPORT_CONTENT_DELETED` Event를 추가한다.

## API

```http
GET /api/v1/audit-exports?view=MY_REQUESTS&page=0&size=10
GET /api/v1/audit-exports?view=APPROVAL_QUEUE&page=0&size=10
GET /api/v1/audit-exports?view=HISTORY&page=0&size=10
GET /api/v1/audit-exports/work-summary
```

Monitoring은 집계와 업무 진입점만 제공한다. 승인, 반려, 폐기 같은 상태 변경은 기존 Audit Export command API를
통해 Requests & Approvals 화면에서만 수행한다.

## 검증 기준

- Auditor의 본인 요청 조회와 Privileged Operator의 승인 큐 조회
- 본인 요청의 승인 큐 제외
- Institution 및 Workload SQL scope
- 24시간 이상 대기 집계
- 승인 Queue와 본인 승인 대기 요청의 오래된 순 정렬
- `APPROVED`, `GENERATING`, `READY`의 사유 필수 폐기
- 생성 중 lease 해제와 READY content 삭제 Event
- V51 조회 인덱스 생성
- 기존 승인, 생성, 다운로드 command 회귀
