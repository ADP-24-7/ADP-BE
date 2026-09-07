# AI-EVAL-1 Evaluation Run Contract

## 목적

동일한 합성 평가 Case를 세 NVIDIA 모델에 동일한 Dataset, Policy, Destination 조건으로 실행하고,
실행 당시 조건을 Runtime Evidence에서 재현할 수 있도록 고정한다.

## 서버 소유 기준선

- Evaluation Run: `ai-eval-baseline-2026-09-07`
- Run Version: `1.0.0`
- Evaluation Case: `customer-summary-ko-001`
- Dataset: `financial_synthetic`
- Dataset Version: `financial_synthetic_processed_v1`
- Dataset Digest: ADP-DA `02_ai/data/processed/financial_synthetic/manifest.json` 파일의 SHA-256
- Model Profile: AI-EVAL-0에서 승인한 세 프로필만 허용

Runtime caller는 `evaluationRunId`와 `evalCaseId`만 참조한다. Dataset, Model 설정, Policy 및
Destination digest를 요청에서 재정의할 수 없다.

## 검증 및 증적

- Run 또는 Case가 등록되지 않으면 외부 호출 전에 차단한다.
- Run에 포함되지 않은 Model Profile은 차단한다.
- 고정된 Policy/Destination digest가 현재 실행 snapshot과 다르면 차단한다.
- V23은 Run/Case/Dataset/Policy/Destination provenance를 기존 AI model execution evidence에 추가한다.
- 평가 Reference 전체가 없으면 일반 AI 실행으로 처리하며, 일부만 입력되면 차단한다.
- Idempotency request hash에 Run/Case를 포함해 같은 key로 다른 평가 조건을 재사용하지 못하게 한다.

Raw Prompt, Controlled Response 및 Credential은 Runtime DB에 저장하지 않는다.
