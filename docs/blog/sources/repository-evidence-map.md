# Repository Evidence Map

원고 최초 작성 기준일: 2026-09-20

| Repository | Baseline commit |
| --- | --- |
| ADP-BE | `ee6119271bd0c0912d71e1b4f23ebbd3269df5b7` |
| ADP-FE | `56a59cebf30f0724b0588c12f1add5e23fe785d4` |
| ADP-DA | `74af1928d720d4a37addeedab1770d81b4fff8a5` |
| ADP-Infra | `e1dfcb25e978d64ae1007ac786f2b04e8d0ebd1a` |
| ADP-Docs | `1cbc07de33877ec6686dd804e7c80635c7fc3e7c` |

## 편별 핵심 근거

| 편 | 코드·문서 근거 | 대표 검증 |
| --- | --- | --- |
| 1 | 각 README, ADP-Docs architecture, BE production reference | 전체 Docker stack health |
| 2 | DA evidence ontology/gateway rules, BE reference evidence/regulatory refresh | schema/digest/lineage tests |
| 3 | `RuntimeExecutionService`, BE-4, SEC-0 | authorization, applicability, idempotency tests |
| 4 | BE-5/6, DA Experiment 03, FE UI state policy | transform/egress/response guard tests |
| 5 | BE-7, AI-EVAL-0~3, DA-P0-2~8 | AI bundle, Digital Asset 6-case E2E |
| 6 | BE-9A/9B, recovery operations | recovery service, lease, replay E2E |
| 7 | BE-10 lifecycle/shadow/current selection | lifecycle, concurrency, selection E2E |
| 8 | implementation progress, Slice 30/32, Infra reference | integration and architecture validation |

## 재산출 명령

```bash
# BE source/test/migration 수
rg --files src/main/java | wc -l
rg --files src/test/java | wc -l
rg --files src/main/resources/db/migration | wc -l
rg -n '@Test' src/test/java | wc -l

# 전체 스택
make docker-up
make docker-ps

# 정적/자동 검증
make check
make architecture-validate
```

숫자는 발행 직전에 다시 산출한다. 특정 commit의 결과를 최신 상태처럼 표현하지 않는다.

