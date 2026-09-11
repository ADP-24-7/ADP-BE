# My Work / Approval Inbox Troubleshooting

## 별도 승인 상태 모델을 만들지 않은 이유

헤더 알림과 운영 화면을 위해 별도 상태를 저장하면 `audit_export_job`과 상태가 어긋날 수 있다. 따라서
My Work 상태는 기존 Job의 `status`, `downloaded_at`, `created_at`에서 계산한다. 다운로드 완료도 별도 enum이
아니라 `READY + downloaded_at`으로 해석한다.

## 자기 승인 방지

UI에서 본인 요청을 숨기는 것만으로는 maker-checker 경계가 보장되지 않는다. 승인 큐 SQL에
`requester_id <> principal_id`를 적용하고 기존 approve command에서도 동일 사용자 승인을 거부한다.

## 권한 범위를 서비스와 SQL 양쪽에서 검증

`APPROVAL_QUEUE`, `HISTORY`는 서비스에서 `PRIVILEGED_OPERATOR`를 요구한다. 실제 목록과 집계 Query는
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
