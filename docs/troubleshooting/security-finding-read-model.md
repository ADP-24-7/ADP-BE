# Security Finding Read Model 트러블슈팅

## Finding 조회를 위해 민감 원문을 다시 저장하지 않는다

### 문제

보안 탐지 화면에 유용한 설명을 제공하려고 Provider 응답 일부를 조회 DTO나 별도 검색 테이블에 복제하면,
Response Guard가 차단한 민감정보가 관리자 Read Plane을 통해 다시 노출된다.

### 해결

기존 Finding Evidence의 `finding_type`, `location`, `offset`, `detector_version`, `evidence_digest`만 사용한다.
Read Model과 테스트에서 `rawValue`가 응답에 없음을 고정하고 Trace/Audit 링크로 조사 경로를 연결한다.

## 애플리케이션 필터만으로 Tenant 격리를 보장하지 않는다

### 문제

목록을 조회한 뒤 Java에서 Institution이나 Workload를 필터링하면 count/pagination이 틀어지고, 상세 조회에서는
다른 Tenant의 Finding 존재 여부가 드러날 수 있다.

### 해결

목록 count/select와 상세 SQL 모두에 `institution_id`와 Principal의 허용 Workload 조건을 넣었다. 범위 밖 상세는
전용 `SECURITY_FINDING_NOT_FOUND` 404로 통일한다.

## Pack 전환 시 이전 Finding을 남기지 않는다

### 문제

FE가 이전 쿼리 데이터를 placeholder로 유지하면 AI에서 Digital Asset으로 전환한 직후 이전 Pack의 Finding을
선택할 수 있다.

### 해결

Pack을 Query Key와 컴포넌트 key에 포함하고 Pack 변경 시 page와 selection을 초기화한다. 데이터 재조회 중에는
행 선택도 비활성화한다.

## 개발 DB의 V43 checksum과 Pack constraint가 최신 main과 달랐다

### 문제

이전 브랜치 형태의 V43을 적용한 로컬 PostgreSQL volume에서 최신 `main`을 실행하면 Flyway checksum validation이
실패했다. 실제 constraint도 `execution_pack` enum만 검사하고 `REVIEW_REQUIRED`의 Pack non-null 조건은 없었다.

### 해결

V44가 Security Finding 조회 인덱스와 함께 `chk_runtime_execution_pack`을 최종 정의로 재적용한다. Pack Policy,
AI Model Execution, Digital Asset Transaction Evidence로 증명 가능한 row만 backfill한다. 근거가 삭제된 역사적 row는
임의 Pack으로 변환하지 않고 `NOT VALID` constraint 아래 보존하되, PostgreSQL이 신규 위반 row는 계속 차단한다.
Fresh DB는 위반 row가 없으므로 같은 migration 안에서 constraint를 validate한다. 이미 적용된 개발 DB는 실제 V43
index/constraint를 확인한 뒤 schema history checksum을 최신 source와 정렬하고 V44로 upgrade한다.
