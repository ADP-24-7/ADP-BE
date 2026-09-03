# Pack Runtime Resolvers

AI와 Digital Asset 실행이 동일 Runtime Core를 공유하면서도 Pack별 구현을 명확히 선택하도록 Adapter 해석 경계를 고정한다.

## Selection Boundary

Destination Profile을 요청 시작 시점에 고정한 뒤 해당 `ExecutionPackType`으로 다음 Adapter를 한 번 선택한다.

- `ExecutionPackContextBuilder`
- `ExternalSchemaMapper`
- `RuntimeConnectorPort`
- `ResponseGuardPort`

선택된 Adapter는 같은 실행이 끝날 때까지 변경하지 않는다.

## Resolution Rules

- Context Builder와 External Schema Mapper는 Pack 전용 구현이 반드시 있어야 한다.
- Connector와 Response Guard는 Pack 전용 구현을 우선한다.
- Pack 전용 Connector 또는 Response Guard가 없으면 `COMMON` 구현을 fallback으로 사용할 수 있다.
- 같은 Pack을 지원하는 Adapter가 둘 이상 등록되면 Application Context 시작을 거부한다.
- 필수 Adapter가 없으면 외부 전송 전에 실행을 실패 처리한다.
- DA Artifact의 정책값이나 Threshold는 Resolver에 포함하지 않는다.

## Current Bindings

| Adapter | Pack |
| --- | --- |
| `AiCanonicalContextBuilder` | `AI` |
| `AiExternalSchemaMapper` | `AI` |
| `HttpAiConnector` | `AI` |
| `ProjectProvisionalResponseGuardAdapter` | `AI` |
| `FakeConnector` | `AI` |
| `UnconfiguredRuntimeConnectorAdapter` | `COMMON` fallback |
| `NoopResponseGuardAdapter` | `COMMON` fallback |

## BE-8 Extension

BE-8에서는 기존 Runtime Service에 Pack 분기를 추가하지 않고 다음 `DIGITAL_ASSET` Adapter를 등록한다.

- Digital Asset Context Builder
- Digital Asset External Schema Mapper
- Mock Asset Platform Connector
- Settlement Response Guard

Settlement와 Reconciliation 상태는 Response Guard 이후 별도 Domain 상태로 관리하며 HTTP 성공과 동일시하지 않는다.
