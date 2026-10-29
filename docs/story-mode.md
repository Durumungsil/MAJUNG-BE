# 스토리모드 — Google Places

모든 경로는 `/api/v1/groups/{groupId}` 아래이며 Bearer JWT와 그룹 멤버 권한이 필요하다. 스토리 회차만 사용할 수 있다. JSON은 snake_case, 성공 응답은 `{success,message,data}`다. 숫자 `destination_id` 대신 문자열 Google `place_id`를 사용한다.

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| GET | `/destinations/recommendations` | 선택: `category`, `limit`(1~20, 기본 20), `sort`(GROUP_MATCH/DISTANCE), `cursor` |
| GET | `/destinations/{placeId}/recommendations` | 선택: `category`, `limit`, `radius_meters`(100~5000), `cursor` |
| PUT | `/destinations/selection` | `destinations: [{place_id,order,visit_date,category?}]` (최대 50개, 빈 배열로 초기화) |
| POST | `/itineraries/timeline` | 선택: `place_ids`, `start_date`, `end_date`, `transport_type`, `day_start_time`, `day_end_time` |
| GET | `/itineraries/{itineraryId}` | 없음 |
| PATCH | `/itineraries/{itineraryId}/items/{itemId}` | `start_at`, `end_at`, `memo` |

## 추천과 더 보기

- `category`: FOOD, CAFE, SIGHTSEEING, CULTURE, ACTIVITY, SHOPPING, NATURE, HEALING. FESTIVAL은 기존 설문에 남겨 두되 검색하지 않는다.
- 카테고리 생략 시 상위 비음식 취향 최대 3개와 끼니 음식점 검색어 1개를 사용한다. 지정 시 그 카테고리 검색어만 사용한다.
- 검색어마다 Text Search 요청 1회, 병렬 실행, 첫 페이지 20개만 읽는다. 결과 ID를 합쳐 중복 제거·점수/거리 정렬 후 최대 `limit`개를 응답한다. 따라서 실제 Google 요청 수는 화면 요청 수보다 많을 수 있다.
- 응답: `region`, `area`, `items: [{place_id,name,category,latitude,longitude,distance_meters,score,reason}]`, `next_cursor`.
- `next_cursor`가 있으면 같은 category/sort 및 기본 장소·반경을 유지해 cursor로 전달한다. 먼저 미노출 후보를 사용하고, 후보가 소진된 더 보기 요청에서만 Google의 다음 페이지를 읽는다. 압축·서명된 cursor는 사용자·회차·프로필 버전에 묶이며 15분 후 만료된다. 만료/변조/조건 변경은 400이므로 첫 페이지부터 다시 요청한다.
- 이름은 검색 응답에서 전달할 뿐 DB에 저장하지 않는다. 주소·평점·사진·가격대 응답 필드는 요청하지 않는다. 최소 field mask는 `places.id,places.displayName,places.location,places.types,nextPageToken`이다.
- 예산은 음식·카페 검색의 `priceLevels` 필터에 사용한다. avoid_place_types는 응답 types와 비교해 제외한다. CAFE·FOOD는 strictTypeFiltering을 사용한다.
- 선택 장소가 있으면 그중 거리 합이 가장 작은 장소, 없으면 첫 검색 결과를 중심으로 이동 범위를 정한다. Google에는 사각형을 전달하고 결과를 실제 원형 반경으로 다시 거른다. 2차 추천은 지정 place_id의 좌표가 중심이며 기준 장소를 결과에서 제외한다.

## 선택과 일정

```json
{"destinations":[{"place_id":"ChIJ_example","order":1,"visit_date":"2026-11-01","category":"CAFE"}]}
```

일정 생성 요청을 `{}`로 보내면 저장된 선택과 방문일을 사용한다. `place_ids`로 직접 지정하는 경우에도 문자열 배열이다. 선택 저장 시 category 생략 또는 직접 생성한 미분류 장소는 SIGHTSEEING으로 처리하므로 추천 결과의 category를 함께 저장하는 것을 권장한다.

일정 응답은 `days[].items[]`에서 VISIT의 `place_id,latitude,longitude`, MOVE의 `from_place_id,to_place_id`와 양 끝 좌표를 제공한다. 이름·주소는 없다. 좌표는 30일이 지나면 NULL일 수 있으며 화면은 NULL을 처리해야 한다. GET 일정 조회는 좌표 갱신을 위한 유료 API를 호출하지 않는다. 백엔드가 경로 계산 등으로 좌표를 실제 사용할 때만 Details(location)로 갱신한다.

배치는 장소 영업시간 없이 수행한다. Gemini에는 ID·카테고리·체류 시간·장소 사이 거리만 전달한다. 이름·주소·원문 알레르기는 일정 프롬프트에 포함하지 않는다. AI 실패/타임아웃/불리한 동선이면 RULE 배치를 사용한다.

목록에는 사진을 표시하지 않는다. 상세 이름·사진의 프론트 연동은 [프론트 작업](google-places-frontend-tasks.md)을 따른다. 배포 전 [마이그레이션 및 설정](google-places-deployment.md)이 필요하다.
