# My Work / Approval Inbox Troubleshooting

## 별도 승인 상태 모델을 만들지 않은 이유

헤더 알림과 운영 화면을 위해 별도 상태를 저장하면 `audit_export_job`과 상태가 어긋날 수 있다. 따라서
My Work 상태는 기존 Job의 `status`, `downloaded_at`, `created_at`에서 계산한다. 다운로드 완료도 별도 enum이
아니라 `READY + downloaded_at`으로 해석한다.

## 자기 승인 방지

UI에서 본인 요청을 숨기는 것만으로는 maker-checker 경계가 보장되지 않는다. 승인 큐 SQL에
`requester_id <> principal_id`를 적용하고 기존 approve command에서도 동일 사용자 승인을 거부한다.

## 권한 범위를 서비스와 SQL 양쪽에서 검증

`APPROVAL_QUEUE`, `DECISION_HISTORY`는 서비스에서 `PRIVILEGED_OPERATOR`를 요구하고 `AUDIT_HISTORY`는
`AUDITOR`를 요구한다. 실제 목록과 집계 Query는
`institution_id` 및 `allowedWorkloads` 조건을 항상 포함한다. 테스트에서는 제한된 Workload principal로
persistence를 직접 호출해, 로컬 wildcard 인증 fixture가 SQL scope 검증을 가리지 않도록 했다.

## 조회 화면과 command 책임 분리

Monitoring에서 승인 버튼을 제공하면 탐색 화면이 mutation 경계까지 소유하게 된다. Monitoring은 지연,
최근 처리, 생성 및 실패 집계만 표시하고 실제 승인/반려는 Requests & Approvals로 연결한다.

## URL과 화면 상태 동기화

헤더 My Work 메뉴는 이미 열린 정책 화면으로 이동할 수도 있다. FE는 `view` query parameter 변경을 감지해
탭, 페이지, 선택된 검토 대상을 함께 초기화한다. Auditor가 권한 없는 승인/이력 URL로 진입하면
`MY_REQUESTS`로 제한한다.

## 잘못 승인된 반출을 만료까지 방치하는 문제

초기 command는 `READY`만 폐기할 수 있었고 Work Inbox에는 폐기 조작이 없었다. 이 구조에서는 승인 오류를
발견해도 생성 전이나 생성 중에는 중단할 수 없었다. 폐기 가능 상태를 `APPROVED`, `GENERATING`, `READY`로
확장하고 사유 입력을 필수화했다. 생성 중 폐기는 lease를 함께 해제해 stale Worker가 완료 상태를 덮어쓰지 못하게
하고, READY 폐기에서만 실제 content 삭제 Event를 기록한다.

## 탭마다 달랐던 승인 대기 정렬

`APPROVAL_QUEUE`는 오래된 순이었지만 `MY_REQUESTS`는 상태 우선순위 뒤에 `updated_at DESC`를 적용해 같은
`REQUESTED` 상태가 최신 순으로 보였다. 두 화면 모두 승인 대기 안에서는 `created_at ASC`를 사용하도록 정렬을
고정했다. 완료 및 오류 상태는 운영자가 최근 결과를 빠르게 확인할 수 있도록 기존 최신 변경 순을 유지한다.

## 진행 목록에 종료 건이 계속 누적되는 문제

다운로드 완료, 반려, 실패, 만료, 폐기 건까지 `MY_REQUESTS`에 두면 사용자가 지금 해야 할 일을 찾기 어렵다.
`MY_REQUESTS`는 진행 중 상태와 미다운로드 `READY`만 반환하고 종료 건은 `MY_HISTORY`로 분리했다. 만료 또는 폐기된
Job은 과거 승인을 되살리지 않는다. FE의 다시 요청은 기존 실행과 목적을 바탕으로 새 idempotency key를 가진 신규
요청을 생성하며 BE가 현재 Institution, Workload, Evidence 권한을 다시 검증한다.

## 운영자와 승인자의 로컬 권한이 같았던 문제

초기 local fixture는 `operator-local`에 `OPERATOR`와 `PRIVILEGED_OPERATOR`를 함께 부여했다. 이 때문에 운영 담당자와
승인 담당자가 같은 승인 탭과 전체 이력을 보는 것처럼 보였다. V10 local migration에서 중복 역할을 제거하고, 승인자는
본인이 처리한 이력만, Auditor는 기관 감사 이력만 조회하도록 View 계약을 분리했다. 다만 운영 조사까지 차단하면 실행
목록에서 상세 증적과 CSV/PDF 요청이 모두 403이 된다. `OPERATOR`에는 Scope 내 증적 조회와 본인 반출 요청만 허용하고,
승인 command와 타인 이력은 계속 차단했다. 생성 파일 다운로드 역시 요청자 본인으로 제한한다.

## 폐기가 승인 근거를 덮어쓰던 문제

초기 폐기 구현은 `approval_reason`을 폐기 사유로 갱신했다. 그 결과 승인 당시 판단 근거가 사라지고 APPROVED Event에도
사유가 없어, 최종 Job만으로는 승인과 폐기의 의사결정 흐름을 재현할 수 없었다. V52에서 `revoked_by`, `revoked_at`,
`revocation_reason` snapshot과 Event `reason_text`를 추가했다. 승인·반려·폐기는 각자의 사유를 append-only Event에
기록하고, 폐기는 기존 `approval_reason`을 변경하지 않는다. 기존 REVOKED 데이터는 폐기 Event를 기준으로 별도 폐기
snapshot으로 이동한다.

## 다운로드와 폐기가 동시에 처리되던 문제

다운로드가 상태 확인 후 조건 없는 UPDATE를 수행하면, 그 사이 폐기가 commit돼 content가 삭제되어도 오래 읽은 값을
반환할 수 있다. 다운로드 트랜잭션이 Job을 `SELECT ... FOR UPDATE`로 잠근 뒤 READY 상태, 만료, digest를 확인하고
동일 version의 READY row만 갱신하도록 변경했다. 폐기 역시 같은 row를 갱신하므로 두 작업은 직렬화된다. 테스트에서는
폐기가 잠금을 먼저 획득한 상태에서 다운로드를 대기시킨 뒤, 폐기 commit 후 파일이 반환되지 않고 DOWNLOAD Event도
생성되지 않는 것을 검증한다.

## 운영 집계가 일반 운영자에게 노출되던 문제

개인 업무 카운트와 기관 전체 처리량을 같은 응답에 담으면서 일반 `OPERATOR`도 승인 대기, 생성 실패 등 기관 집계를
볼 수 있었다. 서버가 `operationsAvailable`을 역할에서 계산하고 권한이 없으면 기관 집계를 0/null로 축소한다. FE도
이 서버 판정을 기준으로 Governance Operations 영역을 렌더링한다.
