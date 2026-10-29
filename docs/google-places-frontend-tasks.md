# 프론트 연동 작업

백엔드의 스토리·극한 장소 식별자는 숫자 destination_id가 아닌 문자열 place_id다. 모험 미션은 Stores를 유지하며 미션 ID 계약은 그대로다.

- [ ] FOOD·CAFE·SIGHTSEEING·CULTURE·ACTIVITY·SHOPPING·NATURE·HEALING 탭을 제공하고 설문에서는 FESTIVAL을 숨긴다.
- [ ] preferences/summary의 profile_status(PENDING/RUNNING/DONE/FAILED)를 처리한다. 생성 중에도 백엔드 추천은 현재 설문의 RULE 프로필로 동작한다.
- [ ] 추천에는 category 탭을 전달한다. 더 보기는 next_cursor를 같은 검색 조건으로 전달한다. 평점 정렬(RATING)은 제거한다.
- [ ] 선택은 destinations[].place_id와 추천 category, 일정 생성은 place_ids 배열을 보낸다.
- [ ] 일정의 place_id 및 nullable 좌표를 처리한다. 이름이 없으므로 앱에서 필요한 상세 필드만 Google SDK로 조회한다.
- [ ] 룰렛의 place.place_id와 nullable 좌표를 처리한다. 룰렛 응답에는 이름·주소가 없다.
- [ ] 목록에는 사진을 요청하지 않는다. 상세 진입 시에만 사진 메타데이터와 사진 1장을 읽고 사진 작성자 표시를 구현한다. 사진 리소스 이름·URL을 영구 저장하지 않는다.
- [ ] 서버 GOOGLE_PLACES_API_KEY를 프론트에서 사용하지 않는다. 웹이라면 HTTP referrer 제한을 가진 별도 브라우저 키 및 Maps JavaScript API/Places 라이브러리 설정이 필요하다. SDK 호출은 해당 프로젝트의 할당량·비용 설정도 따로 확인한다.
- [ ] 모험 힌트 사용 후 `POST /api/v1/groups/{groupId}/missions/{missionId}/distance`에 `{latitude,longitude}`를 보내 distance_meters를 표시한다. 힌트 미사용 시 409다.
- [ ] 완료 요청에 GPS의 accuracy_meters를 필수로 보낸다. 정확도 부족/너무 짧은 이동/비현실적인 속도는 400이므로 GPS 갱신 후 재시도한다.
- [ ] 힌트 로드뷰를 제공할 수 없으면 같은 미션의 `/replace`에 `{reason:"ROADVIEW_UNAVAILABLE"}`를 보낸다. 성공하면 힌트·시작 상태가 초기화되므로 새 미션처럼 start한다. 대체 없음/3회 초과 시 409를 처리한다. 임의의 API 장애를 로드뷰 미지원으로 분류하지 않는다.

상세 사진은 [Google Maps JavaScript Place Photos](https://developers.google.com/maps/documentation/javascript/place-photos)와 [표시 정책](https://developers.google.com/maps/documentation/javascript/policies)을 따른다. `Place.fetchFields({fields:['photos']})`로 필요한 메타데이터를 조회하며, 표시할 크기에 맞춘 사진 하나만 가져온다. 이름이 필요하면 displayName을 별도 요청 필드에 포함한다. 지도/로고·사진 작성자 표시는 사용하는 SDK의 정책을 적용한다.

세부 계약은 [스토리](story-mode.md), [극한](extreme-mode.md), [모험](adventure-mode.md)에 있다.
