# AI Evaluation 트러블슈팅

## 범위

AI-EVAL-0부터 AI-EVAL-3까지의 기능·리뷰 수정 커밋 `c156ff6`~현재 브랜치를 기준으로 정리한다.

## Provider Credential과 Model 선택권

- 관련 커밋: `c156ff6`, `97ffebc`, `88a7626`
- 문제: 호출자가 임의 Model ID나 endpoint/credential을 선택하면 승인된 Destination/Profile 경계를 우회할 수 있다.
- 해결: 서버 소유 Model Profile allowlist와 Connection Profile을 분리하고 `NVIDIA_API_KEY`는 환경변수에서만 주입한다.
  실행 시 profile/model/destination digest를 evidence에 고정한다.
- 검증: 세 모델이 동일 Runtime E2E를 통과하는지와 credential 미구성 시 Provider 호출 전 fail-closed 되는지 확인했다.

## Evaluation Run 재현성과 Idempotent Replay

- 관련 커밋: `025b202`, `dbec24f`, `5beb8b0`
- 문제: Case ID만 전달하면 Dataset/Policy/Model 조건이 바뀐 실행을 같은 평가로 비교할 수 있고, 동일 요청 replay가
  새 contract와 충돌해 409가 될 수 있었다.
- 해결: 서버 소유 Run에 Dataset version/digest, Policy snapshot digest, Case expected input digest, Model 집합을 고정했다.
  request hash와 persisted evidence가 동일 immutable contract를 사용하도록 했다.
- 교훈: 평가 식별자는 표시용 label이 아니라 비교 조건 전체를 묶는 versioned contract다.

## Evidence 저장 실패가 Runtime 결과를 뒤집던 문제

- 관련 커밋: `a05a4c0`, `98551e6`, `6548e49`
- 문제: Provider 호출과 Runtime 완료 후 latency/token evidence 저장이 실패하면 broad exception 처리로 이미 완료된 Runtime이
  `FAILED`로 바뀔 수 있었다.
- 해결: 측정 evidence persistence를 best-effort recorder로 격리하고 실패는 metric/log로 관측하되 business outcome은 보존했다.
- 교훈: 본 처리의 성공 조건과 관측 증적의 성공 조건을 분리하되, 증적 누락 상태 자체는 명시적으로 남긴다.

## Malformed HTTP 200 응답 Evidence 유실

- 관련 커밋: `b7cef1e`
- 문제: HTTP 200 body JSON 변환 실패가 `RestClientException`으로 전파되면 `ConnectorResult`가 생성되지 않아
  latency/status/`RESPONSE_PARSE_ERROR` evidence가 사라질 수 있었다.
- 해결: transport, 4xx/5xx, successful-response body conversion failure를 구체적인 예외 순서로 분리하고 malformed body를
  `HTTP_FULL_RESPONSE + RESPONSE_PARSE_ERROR`로 정규화했다.
- 교훈: HTTP status 성공은 application response 성공이 아니며 parser failure도 외부 interaction evidence로 남겨야 한다.

## Evaluation Bundle이 불완전 실행을 완료로 위장하던 문제

- 관련 커밋: `dc8338d` 이후 AI-EVAL-3 리뷰 수정
- 문제: 조회된 row가 한 건만 있어도 정상 Bundle이 생성되어 1 Case × 3 Model Run에서 한 모델만 실행된 결과가
  complete artifact처럼 DA에 전달될 수 있었다.
- 원인: Evidence 상호 일관성만 확인하고 `AiEvaluationRunCatalog`의 기대 Case × Model 집합과 비교하지 않았다.
- 해결: Run Catalog의 Cartesian Product와 최신 Pair별 Evidence를 비교하고 누락/partial/중복 projection을
  `AI_EVALUATION_BUNDLE_INCOMPLETE`로 거부한다. 재실행은 Pair별 최신 Execution 하나만 선택해 모델별 가중치 왜곡을 막는다.
- 검증: 3-model Bundle, missing pair, partial evidence 시나리오를 분리해 테스트한다.

## 동일하게 변조된 Digest가 통과하던 문제

- 관련 커밋: AI-EVAL-3 리뷰 수정
- 문제: 모든 DB row의 Dataset/Policy/Profile digest를 같은 잘못된 값으로 바꾸면 row 간 equality 검증은 통과했다.
- 해결: Run/Dataset/Policy/Case input은 `AiEvaluationRunCatalog`, Model/Sampling/Destination은
  `AiModelProfileCatalog`와 직접 비교한다. 상충 유형은 provenance/model mismatch reason code와 metric으로 분리한다.
- 교훈: versioned evidence 검증은 `Evidence == Evidence`가 아니라 `Evidence == Authoritative Contract`여야 한다.

## Canonical Digest의 전역 설정 의존

- 관련 커밋: AI-EVAL-3 리뷰 수정
- 문제: 애플리케이션 공통 `ObjectMapper` 설정이 바뀌면 같은 Bundle의 fingerprint가 배포마다 달라질 수 있었다.
- 해결: Bundle 전용 canonicalizer에서 key ordering, null 포함, ISO-8601 date-time, UTF-8 compact JSON을 고정했다.
  `failure_summary`를 포함한 manifest 제외 payload 전체를 digest한다.
- 교훈: 재현 가능한 Artifact digest는 일반 API serialization의 부산물이 아니라 독립된 versioned 알고리즘이어야 한다.

## 과거 COMPLETE Evidence가 새 3-Model 실행을 대신하던 문제

- 관련 커밋: `cfd3c66`, `4ebb5a4`, main `b9bfb71`
- 문제: Run Readiness가 `READY`여도 방금 요청한 세 모델이 아니라 DB에 남아 있던 과거 COMPLETE 실행이 Bundle의 최신
  Case x Model 결과로 선택될 수 있었다.
- 해결: Harness가 Runtime 응답에서 받은 `profile_id -> execution_id` 집합을 Readiness와 Bundle의 Case Result,
  Runtime Metric, Trace Index execution ID 집합과 모두 비교한다. 하나라도 다르면 export 성공으로 인정하지 않는다.
- 추가 조치: producer commit, 현재 source HEAD와 worktree 상태를 구분하고 실제 Provider 호출에는 명시적인 확인 변수를
  요구한다.
- 교훈: E2E 완료는 endpoint가 200을 반환했다는 의미가 아니라 이번 실행의 identity가 최종 Artifact까지 보존됐다는 뜻이다.
