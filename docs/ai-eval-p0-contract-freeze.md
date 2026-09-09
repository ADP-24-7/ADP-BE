# P0-00 Evaluation Contract Freeze

구현 기준: 2026-09-10. 이 문서는 코드 계약이며 실측 Provider Evidence가 아니다.
실제 NVIDIA 호출, 점수, Rule Candidate는 생성하지 않았다.

## 실제 실행 조건

- `customer_summary` / `CUSTOMER_SUPPORT`, 기존 서버 소유 baseline의 1 Case × 3 Model.
- Prompt: `AiEvaluationPrompt`의 `customer-summary-prompt/1.0.0`. 기존 한국어
  input prompt와 system prompt 부재(`ABSENT`), user role, 승인된 변환 필드의 canonical
  JSON message template을 snapshot으로 보유한다. Input digest와 별개다.
- Transform: 실제 `TransformStrategyResolver`가 각 context field에 선택한 instruction의
  digest 목록을 `transform_snapshot`에 저장한다. `transform_version`은 이 목록의 canonical
  digest다. 기존 instruction digest에는 strategy/version, key/mapping version, TTL, 정렬된
  parameters가 포함된다. 실행 ID/output digest를 버전으로 사용하지 않는다.
- Field Contract는 모델별 destination 검증과 `destination_contract_digest`에 연결한다.
- 가명처리 scope는 평가 실행에 한해 `ai-evaluation:<run_id>`로 공통화한다. 일반 실행은 기존
  provider scope를 유지한다. 같은 case의 최종 provider payload에서 `model`만 제외한
  `provider_input_digest`를 원자적으로 pin하여 모델 간 변환값 차이도 fail-closed 처리한다.
- Retrieval은 실제 ON이다: `JdbcCustomerSummaryRetrievalAdapter`의 customer/account/transaction
  SQL 조회. 벡터 검색이나 별도 KB는 없다. `rag_mode=PREDEFINED_RETRIEVAL`, `rag_version`은
  adapter version, 실제 profile/scopes/selected fields, 조회 기준 날짜의 snapshot digest다.
  날짜가 바뀌거나 데이터/조회 범위가 달라지면 기존 run은 재실행할 수 없다.
- Sampling: `temperature=0.0`, `max_tokens=512`, `stream=false`.
- seed/reasoning: 현재 BE mapper/connector 경로에서 설정하거나 전송하지 않는다.
  둘 다 `NOT_CONFIGURABLE`. Provider API 전체가 미지원이라는 주장도, reasoning OFF라는 주장도
  아니다. 숫자 seed를 발명하지 않는다. Provider 내부 난수나 reasoning 동작의 동일성은 보장하지 않는다.

## 저장 및 실행 연결

`POST /api/admin/ai/evaluation-runs/{runId}/contract/freeze`는 승인된 read-only retrieval과
세 모델의 policy/destination/transform 조건 비교 후 서버에 snapshot을 고정한다. Provider는
호출하지 않는다. 같은 내용 재요청은 idempotent, 다른 내용으로 overwrite는 거부한다.
`GET .../{runId}/contract`로 읽는다. 기존 privileged admin 인가를 적용한다.

Snapshot은 `fixed_conditions`, `fixed_conditions_digest`, `model_profiles`로 구성한다.
`model_profiles`는 실행 전 저장되며 fixed digest에서 제외한다. 기존 model config 명칭인
`profile_id/version/digest`, `provider_model_id/version`, `connection_profile_id`,
`destination_profile_digest`를 재사용하고 `destination_profile_id`, `provider`를 노출한다.
provider model version은 기존 catalog 등록값이며 hosted revision의 실측 확인값은 아니다.

V40 migration은 immutable contract, case provider-input pin, execution binding을 저장한다.
run/case/fixed digest/model digest/decision/transform/outbound/provider request를 FK와 서버
검증으로 연결한다. Export SQL은 runtime의 decision, transform, outbound ID, PASSED guard,
provider request digest와 model evidence를 join하여 불완전한 binding의 export를 거부한다.

실행 시작 시 freeze와 승인 모델 snapshot을 확인하고, connector 호출 직전에 조회 context,
case/input, policy, transform instructions, sampling의 일치를 다시 확인한다. 실제 mapper의
message가 REMOVE를 제외한 Transform 결과의 template 렌더링과 일치해야 한다. 미지원 제어값을
추가하거나 메시지/temperature/max_tokens/stream을 변경하면 호출 전에 차단한다.

## fixed_conditions_digest

아래 `fixed_conditions` 전체를 기존 `AiEvaluationBundleCanonicalizer`로 hash한다.

```text
evaluation_run_id, evaluation_contract_version, workload, purpose_code,
dataset_version, dataset_digest, retrieved_context_digest,
prompt_version, prompt_snapshot, prompt_snapshot_digest,
policy_version, policy_snapshot_digest,
transform_version, transform_snapshot, transform_scope,
rag_mode, rag_version, retrieval_config,
temperature, max_tokens, stream, seed_control, reasoning_control,
case_set_version, cases, destination_contract_digest
```

Canonicalization: object key는 재귀적으로 Java String UTF-16 순서로 정렬, array 순서 유지,
UTF-8 compact JSON, 불필요한 공백/줄바꿈 없음, null 유지, 날짜는 ISO 문자열, Unicode는 그대로
출력한다. 숫자 타입을 보존하여 double `0.0`과 integer `0`은 다르다. Java/Jackson double
표현 규칙을 DA `canonical_json`이 재현한다. 결과는 `sha256:` + 소문자 64자리 hex다.
NaN/Infinity는 허용하지 않는다. 본 baseline의 유일한 floating control은 `temperature=0.0`이다.
Case는 case ID 순서, transform field는 canonical context 순서, 모델은 profile ID 순서다.
모델 ID/profile digest는 fixed conditions에 들어가지 않는다.

`evaluation_contract_digest`는 기존 Run/Case/Dataset/Policy/Model 등록 계약,
`profile_digest`는 기존 model config digest, `content_digest`는 Bundle content hash다.
세 필드의 기존 의미를 변경하지 않는다.

## Bundle 및 DA

새 export는 `adp-ai-evaluation-bundle/v2`, `contract_evidence.snapshot`과
`contract_evidence.bindings`를 제공한다. 기존 v1 Bundle의 DA 읽기는 유지한다.
DA는 snapshot/artifact hash, 고정 sampling, 실행 전 모델 snapshot과 결과 model config 일치,
서로 다른 세 profile digest 및 기존 profile digest 재계산, Case × Model coverage,
decision/request/guard binding, 동일 case의 provider-input digest 일치를 검증한다.
`evaluation_e2e.py`는 첫 runtime POST 전에 BE의 frozen contract를 GET/검증하며,
export된 digest가 실행 전 읽은 digest와 일치해야 소비를 허용한다.

## 검증과 실제 실행 전 제한

- BE application/mapper/transform/runtime 단위 테스트 및 DA contract/e2e/bundle 테스트를 사용한다.
- BE의 `build/test-contract-bundle.json`은 단위 테스트가 만든 MOCK fixture다. 독립 DA 검증에만
  사용하며 실측 Trace나 평가 표본으로 취급하지 않는다.
- 로컬 PostgreSQL 연결 거부로 V40 적용/실 DB freeze/동시 binding/API 통합 테스트는 미검증이다.
- 실제 실행 환경에 migration, 승인/정책/조회 데이터, privileged freeze가 필요하다.
- catalog dataset digest는 기존 DA manifest의 등록 provenance다. `retrieved_context_digest`는
  freeze 때 조회한 실제 값과 이후 실행의 동일성을 증명하지만, DB 전체가 원본 manifest에서
  적재되었다는 증명은 아니다. 운영 데이터 적재 provenance 대조가 여전히 필요하다.
- hosted model ID/revision 가용성 및 Provider credential/egress 설정은 실제 실행 전에 확인해야 한다.
  이번 작업에서 key 값을 읽거나 출력하지 않았다.

## 검증 결과

- BE 관련 단위 테스트: 80 passed, 0 failed. 전체 test source 컴파일 성공.
- DA 전체: 169 passed, 1 failed. 기존 Digital Asset raw schema hash 테스트는 Windows CRLF로
  실패하며 LF 정규화 결과가 Git HEAD와 동일함을 확인했다. 해당 파일은 변경하지 않았다.
- BE가 생성한 test-only MOCK Bundle/Snapshot → DA validator: PASS.
- 변경 DA 코드/fixture Ruff: PASS.
- BE 전체 통합 테스트: 로컬 PostgreSQL 연결 거부로 검증 완료하지 못했다.

## 변경 파일

기존 작업 트리의 P0 변경을 보존·보완한 최종 범위다.

### ADP-BE

```text
docs/contracts/ai-evaluation-bundle.schema.json
src/main/java/com/adp/gateway/ai/application/AiEvaluationBundleService.java
src/main/java/com/adp/gateway/ai/application/AiEvaluationRunCatalog.java
src/main/java/com/adp/gateway/ai/domain/AiEvaluationBundle.java
src/main/java/com/adp/gateway/ai/domain/AiEvaluationBundleSource.java
src/main/java/com/adp/gateway/ai/infrastructure/AiExternalSchemaMapper.java
src/main/java/com/adp/gateway/ai/infrastructure/JdbcAiEvaluationBundleAdapter.java
src/main/java/com/adp/gateway/auth/infrastructure/SecurityConfig.java
src/main/java/com/adp/gateway/runtime/application/RuntimeExecutionService.java
src/main/java/com/adp/gateway/transform/application/TransformEngine.java
src/main/java/com/adp/gateway/transform/application/TransformScope.java
src/test/java/com/adp/gateway/ai/AiModelProfileRuntimeTests.java
src/test/java/com/adp/gateway/ai/api/AiEvaluationBundleControllerTests.java
src/test/java/com/adp/gateway/ai/api/DaEvaluationBundleParserFixture.java
src/test/java/com/adp/gateway/ai/application/AiEvaluationBundleServiceTests.java
docs/ai-eval-p0-contract-freeze.md
src/main/java/com/adp/gateway/ai/api/AiEvaluationContractController.java
src/main/java/com/adp/gateway/ai/application/AiEvaluationContractPort.java
src/main/java/com/adp/gateway/ai/application/AiEvaluationContractService.java
src/main/java/com/adp/gateway/ai/application/AiEvaluationPrompt.java
src/main/java/com/adp/gateway/ai/domain/AiEvaluationContractSnapshot.java
src/main/java/com/adp/gateway/ai/infrastructure/JdbcAiEvaluationContractAdapter.java
src/main/resources/db/migration/V40__freeze_ai_evaluation_contract.sql
src/test/java/com/adp/gateway/ai/application/AiEvaluationContractServiceTests.java
```

### ADP-DA

```text
02_ai/contracts/ai-evaluation-bundle.schema.json
02_ai/src/adp_da/bundle_validator.py
02_ai/src/adp_da/evaluation_e2e.py
02_ai/tests/test_evaluation_e2e.py
02_ai/docs/AI_P0_CONTRACT_TRACE_REVIEW.md
02_ai/tests/contract_fixture_factory.py
02_ai/tests/test_evaluation_contract.py
```

