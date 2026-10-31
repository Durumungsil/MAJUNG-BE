# Google Places 프론트 후속 작업

스토리·극한은 Google Places, 모험은 기존 Stores를 사용한다. 아래는 이번 백엔드 PR에 맞춘 프론트 작업 목록이다.

## 남은 연결

- [ ] **장소 이름·상세 조회**: 일정·룰렛은 이름 없이 `place_id`만 내려온다. Google SDK로 필요한 상세 필드만 조회해 이름을 표시한다. 별도 프론트용 키를 사용하고 서버 `GOOGLE_PLACES_API_KEY`를 번들에 넣지 않는다.
- [ ] **상세 사진**: 목록은 사진 없이 유지한다. 상세 진입 시 사진 1장만 조회하고 Google/사진 작성자 출처를 표시한다. 사진 리소스·URL은 영구 저장하지 않는다. 평점·리뷰 등 추가 필드는 기본으로 요청하지 않는다.
- [ ] **실제 일정 지도**: `getTimelineStops` 등 목 데이터 경로를 실제 일정 조회로 교체한다. VISIT 좌표로 마커를 그리며 NULL 좌표는 제외하거나 SDK로 다시 조회한다. 지도 SDK 키도 설정한다.
- [ ] **주변 추천**: 목 주변 장소 대신 `GET /api/v1/groups/{groupId}/destinations/{placeId}/recommendations`를 연결한다. 기준 장소를 바꾸면 이전 결과·cursor를 초기화한다.
- [ ] **카테고리 탭·더 보기**: 추천 요청에 `category`를 전달한다. FOOD·CAFE·SIGHTSEEING·CULTURE·ACTIVITY·SHOPPING·NATURE·HEALING을 제공하고 FESTIVAL은 설문에서 숨긴다. 더 보기는 같은 조건의 `next_cursor`를 사용하고 탭 변경 시 초기화한다. RATING 정렬은 사용하지 않는다.
- [ ] **모험 후속 연결**: 힌트 후 `/missions/{id}/distance`의 `distance_meters`를 표시한다(미사용 시 409). 로드뷰 미지원 시 `/replace`에 `{reason:"ROADVIEW_UNAVAILABLE"}`를 보내고 성공하면 시작·힌트 상태를 초기화해 다시 start한다. 대체 없음/3회 초과는 409다.

모험 경로 앞에는 `/api/v1/groups/{groupId}`가 붙는다. 임의의 지도 API 장애를 로드뷰 미지원으로 처리하지 않는다.

## 유지할 API 계약

| 항목 | 프론트 처리 |
| --- | --- |
| 추천 | `items[].place_id`는 문자열. 이름은 이 응답에서 표시 가능하며 주소·사진·평점은 없음 |
| 선택 저장 | `destinations[].{place_id,order,visit_date,category}` 전송. category도 함께 보존 |
| 일정 생성 | 직접 지정 시 `place_ids` 문자열 배열. 생략하면 저장한 선택 사용 |
| 일정·룰렛 조회 | 이름 없음. `place_id`와 nullable 좌표 처리. 룰렛은 `place.place_id` |
| 취향 요약 | `profile_status`: PENDING / RUNNING / DONE / FAILED 처리 |
| 모험 완료 | 실제 GPS의 `accuracy_meters` 필수. 정확도·시간·속도 검증 실패(400) 처리 |

## 로컬에 반영한 기본 수정

문자열 ID, nullable 좌표, profile_status 매핑, 카페·관광명소 설문, GPS 정확도 전달, 실제 결과에 섞이던 가짜 상세정보 제거는 로컬 MAJUNG-APP에서 수정했다. **프론트 원격 dev에는 아직 반영하지 않은 미커밋 변경**이므로 후속 작업 전에 해당 diff를 확인한다.

마무리 검증: 설문 → 카테고리 추천 → 더 보기 → 선택 → 일정 생성·새로고침 → 지도·주변 추천·상세 사진 → 룰렛 흐름을 실제 API로 확인한다.

상세 계약: [스토리](story-mode.md) · [극한](extreme-mode.md) · [모험](adventure-mode.md). SDK 참고: [Google Place Photos](https://developers.google.com/maps/documentation/javascript/place-photos) · [표시 정책](https://developers.google.com/maps/documentation/javascript/policies).
