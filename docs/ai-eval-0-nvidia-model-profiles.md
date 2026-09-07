# AI-EVAL-0 NVIDIA Model Connector And Profiles

이 Slice는 기존 BE-7 AI Full E2E Runtime에 NVIDIA NIM 호환 Connector와 평가 대상 Model Profile 세 개를 연결한다. 모델 품질 평가는 DA가 담당하며, BE는 동일 Runtime 통제를 통과한 실행 결과를 생성한다.

## Fixed Profiles

| Profile ID | NVIDIA Model ID | 평가 역할 |
| --- | --- | --- |
| `nvidia-nemotron-3.5-lightning-30b-a3b` | `nvidia/nemotron-3.5-lightning-30b-a3b` | Runtime Efficiency |
| `meta-muse-glimmer-30b` | `meta/muse-glimmer-30b` | Finance Quality |
| `google-gemma-4-31b-it` | `google/gemma-4-31b-it` | Korean Quality |

Profile과 Model ID는 서버 Catalog가 소유한다. Runtime Request는 임의 `model_id`를 받지 않으며 승인된 `destinationProfileId`와 `approvalReference` 조합으로만 Profile을 선택한다. Profile snapshot version은 `2026-09-07`로 고정한다.

## Runtime Boundary

1. 기존 `POST /v1/runtime/executions`를 사용한다.
2. AuthN/AuthZ, Approval, Retrieval, Policy, Transform, Outbound Guard를 그대로 통과한다.
3. `AiExternalSchemaMapper`가 allowlisted Model ID와 고정 `max_tokens`, `temperature`, `stream` 값을 포함한 NVIDIA Chat Completions 요청을 생성한다.
4. `HttpAiConnector`는 `NVIDIA_API_KEY`를 Bearer Header로만 전달한다.
5. Provider connection registry가 내부 Provider와 NVIDIA endpoint/credential을 분리한다.
6. 모델, 목적지, 승인, 정책 provenance는 canonical content의 SHA-256 digest로 고정한다.
5. Timeout은 `SENT_UNKNOWN`, 비정상 HTTP 응답은 `FAILED`, 정상 응답은 `ACKNOWLEDGED`로 정규화한다.
6. Response Guard를 통과한 content만 Controlled Delivery로 반환한다.

## Secret Contract

- 실제 키는 `.env` 또는 배포 Secret Store에서 `NVIDIA_API_KEY`로 주입한다.
- NVIDIA 자격증명은 NVIDIA 평가 프로필에서만 사용하며 내부 Provider에는 전달하지 않는다.
- NVIDIA 프로필에서 키가 누락되거나 Provider profile이 등록되지 않으면 네트워크 호출 전에 실패한다.
- `.env.example`에는 빈 변수만 유지한다.
- 키는 Request Body, DB, API Response, Trace, Metric, Log에 포함하지 않는다.
- Docker Compose는 환경변수를 BE 컨테이너에 전달할 뿐 이미지에 포함하지 않는다.

## DA Handoff Boundary

DA 최신 Handoff의 `model_identifier`는 BE Model Profile ID/Version으로 채운다. DA Artifact의 품질·Latency threshold는 아직 미확정이므로 이 Slice는 모델 우열이나 자동 ACTIVE 승격을 판정하지 않는다.

후속 AI-EVAL-1~3에서 Evaluation Run ID, Dataset/Case Version, Latency/Token Evidence 및 DA 전달 Bundle을 추가한다. Runtime DB에는 Prompt/RAG/Response 원문을 저장하지 않는다.
