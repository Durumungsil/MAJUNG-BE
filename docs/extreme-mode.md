# 익스트림모드

명세: [Temp / APIPI 며ㅇ세서 V1.4](https://www.notion.so/4056dcfa235080ae9de1cd2e5a502932)의 익스트림 도메인.

## API

모든 경로 앞에 `/api/v2/groups/{groupId}`가 붙는다. 기존 로그인·그룹·취향 API는 `/api/v1`을 그대로 사용한다.
`Authorization: Bearer {access_token}`이 필요하며, 그룹의 현재 멤버만 사용할 수 있다.
성공은 `{success, message, data}`, 실패는 `{success:false, error_code, message}`, 필드 이름은 snake_case다.

| 메서드 | 경로 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET | `/constraints/minimum` | 없음 | 200: `group_id`, `allergies`, `excluded_categories`, `source_member_count`, `updated_at` |
| POST | `/roulette/destinations` | `region`, `category`, `exclude_place_ids` | 201: `roulette_id`, `type:DESTINATION`, `place`, `round`, `drawn_at` |
| POST | `/roulette/restaurants` | 같은 필드 | 201: 위 필드와 `group_allergies`, `type:RESTAURANT` |
| GET | `/roulette/{rouletteId}` | 없음 | 200: `roulette_id`, `group_id`, `type`, `place`, `drawn_at` |

추첨 본문 필드는 모두 선택이며 빈 JSON 객체도 허용한다. `region`을 생략하면 여행 회차 지역을 사용한다.
지정하면 여행 지역과 같아야 하며 `BUSAN`·`부산`·`부산광역시` 같은 기존 별칭을 지원한다.
`category`는 50자 이내의 대문자·숫자·밑줄 코드다. `exclude_place_ids`는 최대 1,000개의 양수 장소 ID다.
명세의 `place_id`는 `stores.destination_id`와 같다. `place`는 장소 ID·이름·분류·주소·위도·경도를 포함하며
`drawn_at`, `updated_at`은 UTC ISO-8601 시각이다.

```json
{"region":"BUSAN","category":"KOREAN","exclude_place_ids":[902]}
```

## 최소 조건과 취향 입력

- 현재 여행 회차에서 **현재 그룹 멤버가 입력한 취향**만 집계한다. 이전 회차·탈퇴자의 취향은 제외한다.
- `allergies`, `excluded_categories`는 각 멤버 입력의 합집합이며 중복 제거 후 정렬한다.
- 선호 여행 스타일(`travel_categories`)은 제외 조건으로 사용하지 않는다.
- `source_member_count`는 취향을 입력한 현재 멤버 수다. 일부만 입력했으면 그 입력만 집계한다.
- 취향 입력이 하나도 없으면 404다. `updated_at`은 집계에 사용한 취향 중 가장 최근 수정 시각이다.
- 집계는 매번 수행한다. 기존 `TripConstraint` 캐시 테이블의 오래된 값은 추첨에 사용하지 않는다.
- 최소 조건 조회는 현재 회차(활성 회차가 없으면 마지막 회차)가 `EXTREME`인지 확인한다.

기존 `PUT /api/v1/groups/{groupId}/preferences`에 선택 필드 `excluded_categories`를 추가했다.
생략하면 빈 배열로 저장한다(PUT 전체 교체). `GET .../preferences/me`에도 반환하고, 이전 회차 취향 복사에도 보존한다.
최대 20개이며 각 코드는 50자 이내, 첫 글자는 영문 대문자이고 이후 대문자·숫자·밑줄을 허용한다.

```json
{
  "transport_types":["WALK"],
  "food_preferences":[],
  "allergies":["PEANUT"],
  "travel_categories":["CULTURE"],
  "excluded_categories":["EXTREME_HEIGHT"]
}
```

## 추첨·라운드·조회 정책

- 추첨은 `PLANNING` 또는 `IN_PROGRESS`인 `EXTREME` 여행 회차에서만 가능하다.
- 여행 지역, 요청 분류, 제외 카테고리, 장소의 안전 태그, 유효 좌표를 DB에서 확인한 뒤 후보를 추린다.
- `excluded_categories`에 장소 분류가 있거나 장소의 `safety_categories`와 겹치면 제외한다.
- **알레르기로 식당 후보를 제외하지 않는다.** 식당 추첨 응답의 `group_allergies`를 프론트에서 안내한다.
- 현재 데이터에서 여행지는 `RETAIL`·`ART_SPORTS`, 식당은 `FOOD` 업종이다. 숙박·전문 서비스는 제외한다.
- 평점 상위 500곳 제한 없이 적격 장소 전체에서 균등 추첨한다. DB에서 후보 수와 임의 위치의 한 장소만 읽는다.
- 제휴 가중치 모델은 현재 프로젝트에 없어 가중치를 적용하지 않는다. 필수 조건을 완화하는 경로는 없다.
- 사용자와 합의한 라운드 정책: **여행 회차·룰렛 종류별** 성공 추첨 순번(1, 2, 3…)이다.
  같은 회차·같은 종류에서 이미 뽑은 장소와 요청의 `exclude_place_ids`는 다시 뽑지 않는다.
  실패한 요청은 순번을 소비하지 않고, 새 회차는 1부터 시작한다. 후보를 소진하면 409다.
- 변경은 기존 그룹 변경·취향 저장과 같은 그룹 행 잠금으로 직렬화한다. DB에도 회차·종류별 라운드와 장소의 유일 제약을 둔다.
- 결과 조회는 요청 그룹에 속한 결과만 허용한다. 종료한 이전 익스트림 회차도 조회할 수 있어 길 안내를 이어갈 수 있다.

| 상태 | 상황 |
| --- | --- |
| 400 | 지역 불일치, 잘못된 분류 코드 형식·제외 ID·취향 값 |
| 401 | 토큰 없음·만료·위조 |
| 404 | 그룹 멤버가 아님, 해당 그룹 결과 없음, 현재 멤버의 취향 없음 |
| 409 | 활성 여행 없음, 익스트림모드 아님, 조건을 만족하는 후보 없음 |

## 장소 분류 데이터 보강

`Store`에 선택 분류 `placeCategory`와 안전 태그 집합 `safetyCategories`를 추가했다.
빌더 또는 `changeClassification(category, safetyCategories)`로 보강한다.
필드는 각각 `stores.place_category`와 `store_safety_categories(destination_id, category)`에 저장한다.

세부 분류가 있으면 정확히 비교한다. 없으면 기존 업종에서 `FOOD → FOOD`, `RETAIL → SHOPPING`,
`ART_SPORTS → CULTURE`만 매핑한다. 미분류 식당을 이름으로 추정해 `KOREAN`으로 넣지 않는다.
따라서 기존 CSV 데이터만 있는 상태에서 `KOREAN`으로 요청하면 후보가 없어 409일 수 있다.
분류를 생략하면 기존 업종으로 추첨할 수 있다. 관광지의 `NATURE` 등도 해당 장소의 분류 보강이 필요하다.

안전 태그가 비어 있는 장소는 알려진 제외 태그가 없는 것으로 처리한다.
태그 미보강은 안전 인증을 뜻하지 않으므로 실제 장소 분류와 위험 정보는 데이터 적재 과정에서 함께 보강해야 한다.

### 스키마 적용

프로젝트의 기존 `spring.jpa.hibernate.ddl-auto=update` 설정으로 새 필드·테이블·제약이 생성된다.
`preferences.excluded_categories`는 JSON 배열 문자열이며 기존 null은 빈 배열로 읽는다.
`RouletteResult.drawnAt`은 `Instant`로 저장한다. 기존 룰렛 데이터가 있다면 DB 시각 타입과 기존 데이터의 시간대를 먼저 확인한다.
기존 룰렛 데이터의 null·라운드 중복·장소 중복이 있으면 새 NOT NULL·유일 제약 적용 전에 정리해야 한다.
운영 DB나 기존 장소 데이터는 이번 작업에서 수정하지 않았다.

## 검증

```sh
./gradlew test --tests '*ExtremeModeApiTests'
./gradlew test
```

최소 조건 합집합·탈퇴/수정 반영, 제외 태그·세부 분류·명시 제외·기존 결과 제외,
알레르기 안내, 장소 좌표 저장/재조회, 라운드와 회차 분리, 인증/그룹 권한/결과 소속,
잘못된 요청·후보 소진·종료 회차, 동시 추첨의 라운드/장소 중복 방지를 검증한다.
