# Reference Evidence Admin Trace 트러블슈팅

## DA 분석 문서를 Runtime Rule로 바로 해석하는 문제

Notebook과 Markdown의 분석 결론을 Gateway Rule로 직접 읽으면 분석 수정이 기존 실행 의미를 바꾸고, `REFERENCE_ONLY` 자료가
승인된 통제처럼 작동할 수 있다. DA가 strict Bundle로 bounded summary와 provenance만 전달하고 BE가 별도 Registry에 저장하도록
분리했다. Registry ingestion은 Policy Lifecycle이나 Current Selection을 호출하지 않는다.

## Producer Digest를 신뢰만 하는 문제

전달된 digest를 DB에 그대로 저장하면 중간에서 claim이나 mapping이 바뀌어도 탐지할 수 없다. BE가 DA와 동일한 canonical JSON
규칙으로 개별 Evidence와 Bundle digest를 모두 재계산하며 stale digest는 ingestion 전에 거부한다.

## 보조 조회 기능에 Policy Mapping을 미리 포함하는 문제

Policy Artifact mapping을 미리 포함하면 관리자가 단순 관련성을 Runtime 판단 근거로 오해하고 별도 Governance 제품으로 범위가
확장된다. v1 DA Contract, V41 Registry 축소, DTO와 검색 API에서 Policy mapping을 제거하고 Workload 관련성만 유지했다.
이미 적용된 V40 checksum은 바꾸지 않고 forward migration으로 정리했으며 Runtime은 계속 승인된 Policy Current Selection만
사용한다.

## 공식 Source와 분석 파일 위치가 섞이는 문제

Notebook 경로를 Source로 저장하면 공식 원문과 DA 해석의 출처가 구분되지 않는다. 공식 원천은 `source_*`에, 분석 파일과
cell/section 위치는 `analysis_*`에 분리해 관리자 drill-down 의미를 고정했다.

## 동적 SQL 조각의 Named Parameter가 합쳐진 문제

Evidence Type과 Workload 필터를 조립할 때 SQL 조각 경계에 공백이 없어 `:evidenceTypeand`라는 하나의 named parameter로
해석됐고 목록 API가 500을 반환했다. 각 조건 조각의 앞뒤 경계를 명시적으로 분리하고 복합 필터 통합 테스트로 재발을 막았다.

## Tenant와 Workload 범위를 Application에서만 확인하는 문제

Application 검증만으로는 다른 조회 경로가 생길 때 scope가 빠질 수 있다. 목록과 상세 SQL이 Institution을 항상 조건으로
사용하고, Workload mapping이 있는 Evidence는 Principal의 allowed Workload와 교집합이 있을 때만 반환하도록 했다. Bundle
ingestion도 요청자가 접근할 수 없는 Workload reference를 포함하면 저장 전에 거부한다.
