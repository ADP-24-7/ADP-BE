# Financial Privacy Gateway 블로그 원고

이 디렉터리는 ADP 전체 저장소를 바탕으로 작성한 8편의 기술 블로그 원고와 시각 자료를 관리한다.

- 시리즈 구조: [`series-plan.md`](series-plan.md)
- 원고: [`posts/`](posts/)
- 이미지: [`assets/`](assets/)
- 이미지 생성 스크립트: [`assets/diagrams/render_diagrams.py`](assets/diagrams/render_diagrams.py)
- Notebook·정량 Evidence 검토표: [`sources/notebook-evidence-map.md`](sources/notebook-evidence-map.md)

## 발행 전 확인

1. 각 글의 `status`를 `review`에서 `ready`로 변경한다.
2. 기준 commit과 숫자를 다시 확인한다.
3. 상대 이미지 경로를 Velog 업로드 URL로 교체한다.
4. 실제 사용자·금융 데이터, credential, 내부 endpoint가 없는지 확인한다.
5. `검증한 범위`와 `아직 검증하지 않은 범위`를 유지한다.

## 이미지 출처

- `assets/covers/financial-privacy-gateway-series.png`: OpenAI 이미지 생성 도구로 제작한 시리즈 커버
- `assets/diagrams/*.png`: 저장소에 포함된 Python/Pillow 스크립트로 생성한 결정적 다이어그램
- `assets/charts/*.png`: ADP-DA notebook output 또는 versioned artifact에서 재생성한 정량 Evidence
- `assets/screenshots/*.{jpg,png}`: 로컬 합성 데이터 환경의 실제 관리자 UI 캡처
