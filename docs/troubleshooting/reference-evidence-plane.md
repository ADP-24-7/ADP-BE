# Reference Evidence Plane 트러블슈팅

## DA 분석 문서를 Runtime Rule로 바로 해석하는 문제

Notebook과 Markdown의 분석 결론을 Gateway Rule로 직접 읽으면 분석 수정이 기존 실행 의미를 바꾸고, `REFERENCE_ONLY` 자료가
승인된 통제처럼 작동할 수 있다. DA가 strict Bundle로 bounded summary와 provenance만 전달하고 BE가 별도 Registry에 저장하도록
분리했다. Registry ingestion은 Policy Lifecycle이나 Current Selection을 호출하지 않는다.

## Producer Digest를 신뢰만 하는 문제

전달된 digest를 DB에 그대로 저장하면 중간에서 claim이나 mapping이 바뀌어도 탐지할 수 없다. BE가 DA와 동일한 canonical JSON
규칙으로 개별 Evidence와 Bundle digest를 모두 재계산하며 stale digest는 ingestion 전에 거부한다.

## Reference-only Evidence가 Runtime Policy와 결속되는 문제

분석 참고자료에 `policy_artifact_refs`를 허용하면 단순 mapping과 실행 근거의 의미가 섞인다. v1에서는
`status=REFERENCE_ONLY`와 Policy Artifact reference의 동시 존재를 producer와 consumer 양쪽에서 차단한다. 실제 승격은 후속
maker-checker 계약에서 별도로 처리한다.

## 동적 SQL 조각의 Named Parameter가 합쳐진 문제

Evidence Type과 Workload 필터를 조립할 때 SQL 조각 경계에 공백이 없어 `:evidenceTypeand`라는 하나의 named parameter로
해석됐고 목록 API가 500을 반환했다. 각 조건 조각의 앞뒤 경계를 명시적으로 분리하고 복합 필터 통합 테스트로 재발을 막았다.

## Tenant와 Workload 범위를 Application에서만 확인하는 문제

Application 검증만으로는 다른 조회 경로가 생길 때 scope가 빠질 수 있다. 목록과 상세 SQL이 Institution을 항상 조건으로
사용하고, Workload mapping이 있는 Evidence는 Principal의 allowed Workload와 교집합이 있을 때만 반환하도록 했다. Bundle
ingestion도 요청자가 접근할 수 없는 Workload reference를 포함하면 저장 전에 거부한다.
