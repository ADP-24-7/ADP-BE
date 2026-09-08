# NCP Artifact ContentStore 트러블슈팅

## Content-addressed 이름만 검사한 문제

초기 NCP Adapter는 object key가
`handoff/validated/{artifact}/{version}/{sha256}.json` 형식인지만 확인했다. 그러나 key의 digest와 실제 다운로드 bytes를
비교하지 않으면 잘못된 bytes가 content-addressed object로 위장할 수 있다.

수정 후 다운로드 직후 raw bytes SHA-256을 계산하고 filename digest와 constant-time 비교한다. 불일치는 JSON parsing이나
P0-5 Bundle 검증 전에 `DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH`로 차단한다. 이후 P0-5가 canonical manifest/file digest와
cross-artifact semantic binding을 별도로 검증한다.

## 원격 Object의 크기와 문자 인코딩

전체 응답을 메모리에 읽은 뒤 크기를 검사하면 대용량 Object로 메모리를 소진할 수 있다. S3 Range를 `maxBytes + 1`로
제한하고 초과 응답을 `413`으로 정규화한다. bytes는 malformed input을 치환하지 않는 strict UTF-8 decoder로 읽는다.

Manifest는 1 MiB, 개별 Artifact는 4 MiB 제한을 적용한다.

## Caller가 Bucket이나 Endpoint를 선택하는 문제

요청에서 URL, bucket, endpoint를 받으면 SSRF와 tenant 경계 우회가 가능하다. Endpoint와 bucket은 서버 설정으로 고정하고,
요청은 허용 prefix 아래의 content-addressed key만 전달한다. 절대경로, traversal, 역슬래시, URL 형태는 SDK 호출 전에
거부한다.

## 실제 E2E Evidence의 Git identity 혼동

DA reference를 만든 producer commit과 현재 DA `HEAD`는 squash merge 후 서로 다를 수 있다. 둘을 같다고 강제하면 정상
handoff를 실패시키거나, working tree 변경이 섞인 결과를 committed artifact로 오인할 수 있다.

E2E Evidence는 producer SHA, 현재 DA HEAD, worktree 상태, reference committed 여부를 별도 field로 기록한다. Credential은
Evidence, 로그, DB에 저장하지 않으며 실제 NCP 실행은 명시적인 확인 변수 없이는 시작하지 않는다.
