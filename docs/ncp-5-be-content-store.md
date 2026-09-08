# NCP-5 BE NCP ContentStore + Handoff E2E

## 목적

DA가 NCP Object Storage에 게시한 Digital Asset Bundle을 BE가 직접 읽고, P0-5의 BE-owned
Schema/Digest/Semantic 검증을 그대로 통과시켜 Lifecycle `CANDIDATE`로 등록한다.

## 책임 경계

- DA는 content-addressed object와 `manifestReference + expectedContentDigest`를 게시한다.
- BE는 endpoint와 bucket을 서버 설정으로 고정하고 caller에게 선택권을 주지 않는다.
- NCP ContentStore는 Object 조회와 byte limit만 담당한다.
- P0-5 `DigitalAssetArtifactBundleValidator`가 Manifest self digest, expected digest, 5개 파일 digest,
  canonical contract와 cross-artifact semantic binding을 최종 검증한다.
- 모든 원격 Object 검증이 끝나기 전에는 Lifecycle 또는 ingestion row를 생성하지 않는다.

## 저장소 불변조건

- Endpoint: `https://kr.object.ncloudstorage.com`
- Bucket: `adp-qa-data-artifacts`
- 허용 key: `handoff/validated/{artifact}/{version}/{lowercase-sha256}.json`
- 다운로드 raw bytes의 SHA-256과 object key 파일명의 digest가 다르면 P0-5 JSON 검증 전에 차단한다.
- URL, 절대경로, 역슬래시, traversal, 비허용 prefix와 non-content-addressed key는 요청 전에 차단한다.
- Manifest는 1 MiB, 개별 Artifact는 4 MiB까지만 읽는다.
- Object 조회는 `maxBytes + 1` 범위 요청으로 제한하고 초과 응답은 `413`으로 정규화한다.
- Credential 값은 응답, 로그, DB, Trace, 테스트 Evidence에 기록하지 않는다.

## 설정

```properties
ADP_DIGITAL_ASSET_ARTIFACT_STORE_TYPE=ncp
ADP_NCP_OBJECT_STORAGE_ENDPOINT=https://kr.object.ncloudstorage.com
NCLOUD_REGION=KR
ADP_NCP_ARTIFACT_BUCKET=adp-qa-data-artifacts
NCLOUD_ACCESS_KEY=<ignored env only>
NCLOUD_SECRET_KEY=<ignored env only>
```

기본값은 `disabled`, Docker 로컬 개발 기본값은 `local`이다. NCP 호출은 명시적으로 `ncp`를 선택한
프로세스에서만 발생한다.

## 실제 E2E

DA의 `03_digital_asset/artifacts/be_loader_v1/ncp-ingest-reference.json`을 기준으로 격리된
`postgres-test`에서 실제 API ingest를 검증한다.

```bash
ADP_NCP_ARTIFACT_INGEST_CONFIRM=YES \
NCP_ENV=../ADP-Infra/.env.terraform.local \
make ncp-artifact-ingest-e2e
```

하네스는 다음을 검증한다.

1. NCP Manifest와 5개 Artifact 조회
2. P0-5 Trusted Validation 전부 통과
3. `POST /api/admin/digital-assets/artifacts/ingestions` → `CANDIDATE`
4. metadata 재조회 시 Manifest reference와 digest 일치
5. ingestion row가 정확히 1개 생성됨

기본 `make test`는 외부 NCP에 접근하지 않으며 실제 E2E는 확인 변수와 Credential이 모두 있을 때만 실행된다.
성공 시 `build/ncp-artifact-ingest-e2e/evidence.json`에 Credential 값이 없는 실행 증적을 생성한다.
Evidence는 reference producer SHA, 현재 DA HEAD, DA worktree 상태, committed reference 여부와
storage manifest digest/key 결속 결과를 구분해서 기록한다. Squash merge로 producer SHA와 현재 DA HEAD가
다를 수 있으므로 두 값을 동일하다고 추정하지 않는다.

2026-09-08 기준 DA `main`의 실제 NCP reference를 사용한 격리 DB E2E가 통과했다.
