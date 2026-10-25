# 스토리모드 API (임시 구현)

명세: API 명세서 V1.2의 `스토리모드` 도메인

그룹 기반 장소 필터링(나침반)이 아직 없어서, 필터링 자리를 인터페이스로 비워 두고 나머지를 먼저 구현했다. **그룹 일기 생성**은 명세가 비어 있어 이번 범위에서 제외했다.

## API

모든 경로 앞에 `/api/v1/groups/{groupId}`가 붙고 `Authorization: Bearer {access_token}`이 필요하다. 성공 응답은 `{success, message, data}`, 오류 응답은 `{success:false, error_code, message}`다. 필드 이름은 전역 설정에 따라 모두 snake_case로 내려간다.

| 메서드 | 경로 | 입력 | 결과 |
| --- | --- | --- | --- |
| GET | `/destinations/recommendations` | `limit`(1~50, 기본 5), `categories`(쉼표 구분), `sort`(`GROUP_MATCH`·`RATING`), `cursor` | 200, 추천 범위 `area`, 추천 목록, `next_cursor` |
| GET | `/destinations/{destinationId}/recommendations` | `limit`, `radius_meters`(100~5000, 기본은 교통수단별) | 200, 기준 여행지 주변 추천과 실제 쓴 `radius_meters`·`transport_type` |
| PUT | `/destinations/selection` | `destinations[]`(`destination_id`, `order`, `visit_date`) | 200, 저장 개수 |
| POST | `/itineraries/timeline` | 모두 선택: `start_date`, `end_date`, `destination_ids`, `transport_type`, `day_start_time`, `day_end_time` | 201, 타임라인 |
| GET | `/itineraries/{itineraryId}` | 없음 | 200, 타임라인 |
| PATCH | `/itineraries/{itineraryId}/items/{itemId}` | `start_at`, `end_at`, `memo` (보낸 값만 수정) | 200, 수정 결과와 `has_conflict` |

오류: 잘못된 조건·방문 순서 중복·여행 기간 밖 방문일·없는 여행지·일정 겹침은 400, 그룹 멤버가 아니거나 타임라인·항목·기준 여행지가 없으면 404, 진행 중인 여행이 없거나 스토리모드가 아니거나 배치할 수 없는 일정이면 409다.

### 명세와의 관계

노션 API 명세서(V1.2)를 이 구현에 맞춰 고쳤다. 요청·응답 JSON 키와 쿼리 파라미터는 모두 snake_case다.

- `destination_id`는 `stores` 테이블의 `destination_id`(장소 PK)다. 나침반의 `place_id`와 같은 값이다.
- 타임라인 생성·조회 응답은 형태가 같다(`itinerary_id`, `item_id`, `max_move_meters`, MOVE의 `long_distance`). 생성 응답에만 `generated_by`가 있다.
- 시각(`start_at`, `end_at`, `day_start_time`, `day_end_time`)은 `HH:mm` 형식이다.

## 동작 정책

- 그룹의 진행 중인(PLANNING·IN_PROGRESS) 여행 회차를 쓰며 모드가 `STORY`여야 한다. 아니면 409. 조회 API(타임라인 조회)는 모드를 보지 않는다.
- 변경 API(선택 저장, 타임라인 생성, 항목 수정)는 그룹 행을 잠가서 같은 그룹의 동시 변경을 직렬화한다.

### 교통수단별 적정 거리

추천 범위와 타임라인 동선은 그룹 교통수단에 맞춘다(`TransportRange`). 리스트 추천은 그룹이 가장 많이 고른 교통수단(없으면 버스), 2차 추천은 같은 기준(없으면 도보), 타임라인은 요청한 `transport_type`(없으면 그룹 최빈값, 그것도 없으면 버스)을 쓴다.

| 교통수단 | 한 번 이동 적정 거리 (`max_move_meters`, 경로) | 리스트 추천 반경 (직선) | 2차 추천 기본 반경 (직선) |
| --- | --- | --- | --- |
| WALK | 2km (약 30분) | 3km | 1.5km |
| BICYCLE | 6km (약 25분) | 8km | 4.6km |
| BUS | 12km (약 45분) | 15km | 5km |
| TRAIN | 30km | 30km | 5km |
| TAXI·CAR·OWN_CAR·AIRPLANE | 20km (약 45분) | 25km | 5km |

경로 거리는 직선거리 × 1.3이다. 2차 추천 기본 반경은 적정 이동 거리를 직선거리로 바꿔 100m 단위로 내린 값이고 최대 5km다. 비행기는 도시 안에서 택시로 다닌다고 본다.

### 여행지 추천 (임시)

- 추천 범위(`area`): 중심에서 리스트 추천 반경 안의 장소만 추천한다. 한 페이지 결과가 부산 전체에 흩어지지 않게 하기 위해서다.
  - 중심: 저장한 여행지 선택이 있으면 그중 나머지 장소들과의 거리 합이 가장 작은 장소(`based_on: SELECTION`, 같으면 방문일·순서가 앞선 장소). 평균 좌표를 쓰면 선택이 멀리 떨어진 두 묶음일 때 둘 사이 엉뚱한 곳이 중심이 되기 때문이다., 없으면 취향 점수 1위 장소(`based_on: TOP_RECOMMENDATION`). 1위는 지역 후보(평점 상위 500곳) 중 취향 비율 → 평점 → 장소 ID 순으로 정해서 페이지를 넘겨도 같다.
- 후보: 여행 지역(`sido`·`sigungu`, BUSAN/부산/부산광역시 등은 같은 지역)의 장소 중 아래 매핑이 있는 업종이고 추천 범위 안에 있는 장소. DB에서 중심에서 가까운 순으로 **최대 500곳**만 불러와 그 안에서 점수를 매기고 페이지를 나눈다(`StoreRepository.MAX_REGION_CANDIDATES`).

  | 장소 업종 (Store.Type) | 여행 카테고리 |
  | --- | --- |
  | FOOD | FOOD |
  | RETAIL | SHOPPING |
  | ART_SPORTS | CULTURE |
  | SCIENCE_TECHNOLOGY | 추천 제외 (전문 서비스 업종) |
  | LODGING | 추천 제외 |

  여행 카테고리(설문 여행 스타일과 1:1): `HEALING`(힐링), `FESTIVAL`(축제), `SHOPPING`(쇼핑), `CULTURE`(문화관광), `ACTIVITY`(액티비티), `FOOD`(맛집 탐방), `NATURE`(자연친화적). 상가 데이터에는 관광지·자연·축제 분류가 없어서 `HEALING`, `FESTIVAL`, `ACTIVITY`, `NATURE`에 해당하는 장소는 아직 없다.
- 그룹 조건 필터는 `GroupPlaceFilter` 인터페이스를 거친다. 지금은 `PassThroughGroupPlaceFilter`가 아무것도 걸러내지 않는다. **나침반의 그룹 기반 필터링이 완성되면 이 구현체만 교체하면 된다.**
- 점수: `0.4 + 0.4 × (그 카테고리를 고른 멤버 비율) + 0.2 × (1 − 중심까지 거리/반경)`. `GROUP_MATCH`는 점수 → 중심에서 가까운 순 → 평점, `RATING`은 평점 → 점수 → 가까운 순이다(점수는 소수 둘째 자리라 가까운 장소끼리 동점이 많다). 항목의 `distance_meters`는 중심까지 직선거리다.
- `categories`를 주면 그 카테고리만 추천한다. 취향을 입력한 멤버가 없고 `categories`도 없으면 409.
- `cursor`는 이전 응답의 `next_cursor`를 그대로 넘긴다. 마지막 페이지면 `next_cursor`가 null이다.

### 2차 추천 (임시)

- 기준 여행지에서 직선거리 `radius_meters` 안의 장소. 기준 여행지와 숙박은 제외한다. 반경을 안 보내면 교통수단별 기본 반경(위 표)이다.
- DB에서 기준 여행지에서 가까운 순으로 최대 500곳만 불러온다.
- 점수: `0.4 + 0.4 × (1 − 거리/반경) + 0.2 × (카테고리 선호 비율)`. 점수 → 거리 순.
- `estimated_travel_minutes`: 그룹이 가장 많이 고른 교통수단(없으면 도보) 기준 어림값.

### 여행지 선택 저장

- 여행 회차당 선택은 하나이고, 저장할 때마다 통째로 교체한다. 빈 목록이면 선택을 비운다.
- 방문 순서는 회차 안에서 겹칠 수 없고, 방문일은 여행 기간 안이어야 한다.

### 타임라인 생성 (Gemini)

- `destination_ids`를 주면 그 장소들을, 안 주면 저장한 여행지 선택을 배치한다. 저장한 선택의 방문일은 그대로 지킨다. **방문 순서는 동선에 맞춰 다시 정한다**(받은 순서·선택의 `order`는 거리가 같을 때만 쓴다).
- **Gemini**가 장소별 방문일·방문 순서·체류 시간(30~240분)·한 줄 메모를 JSON으로 정한다. 입력으로 장소 좌표·카테고리·영업 시간, 장소 사이 이동 거리(`distances_km`), 교통수단과 적정 이동 거리(`max_move_km`), 여행 기간·하루 일정 시간, 카테고리별 선호 인원을 준다. 같은 날 이어지는 장소는 `max_move_km` 안에 두고 먼 장소는 다른 날로 나누라고 지시한다. 메모에는 시간대 표현(점심·저녁 등)을 쓰지 말라고 한다(방문 시각은 서버가 정하므로).
- 서버가 AI 응답을 검증한다: 모든 장소가 정확히 한 번, 여행 기간 안의 날짜, 정해진 방문일 준수. 그다음 방문 시각은 서버가 아래 규칙(이동 시간·영업 시간·하루 일정)으로 계산하고, AI가 정한 날에 들어가지 않으면 실패로 본다.
- **동선 비교:** 규칙 기반 배치도 같이 만들어 비교한다. AI 쪽이 적정 거리를 넘는 이동이 더 많거나, 이동 거리 합이 규칙 기반의 1.3배 + 1km보다 길면 규칙 기반을 쓴다.
- AI 메모는 VISIT 항목의 `memo`로 저장된다.
- 다음 경우에는 Gemini 대신 **규칙 기반**(아래)으로 만든다. 응답의 `generated_by`가 `AI` 또는 `RULE`로 어느 쪽인지 알려준다(생성 응답에만 있음).
  - `GEMINI_API_KEY`가 비어 있을 때 (테스트 포함)
  - 호출 실패(시간 초과·4xx/5xx·빈 응답), JSON이 깨졌을 때, 검증·배치에 실패했을 때, 동선이 규칙 기반보다 나쁠 때 — 원인은 서버 로그에 `WARN`으로 남는다
- AI 응답을 기다리는 동안에는 그룹을 잠그지 않고, 저장 직전에 잠근다.

#### 규칙 기반 배치

- 동선 최적화(`RouteOptimizer`):
  - 방문일이 정해진 장소는 날짜별로 묶고, 방문일이 없는 장소는 같은 날 앞뒤 장소와의 거리가 가장 적게 늘어나는 자리에 끼워 넣는다(첫 방문일 앞의 빈 날도 쓴다).
  - 같은 날로 묶인 장소는 이동 거리 합이 가장 짧은 순서로 정렬한다(모든 출발점에서 최근접 이웃 → 2-opt, 장소 40곳까지).
- 먼 장소 나누기: 다음 장소까지 이동이 교통수단의 적정 거리(`max_move_meters`)를 넘고, 그 장소의 방문일이 정해져 있지 않고, 남은 날이 있으면 이동 대신 다음 날 첫 방문으로 넘긴다.
- 위 조건으로 여행 기간 안에 다 배치할 수 없으면 ① 나누기 없이 최적화한 순서 ② 받은 순서 그대로 순서로 다시 시도한다.
- 같은 날에 남은 먼 이동은 MOVE 항목의 `long_distance: true`로 알려준다(조회 응답에서도 교통수단 기준으로 계산). 일정은 그대로 만들고 막지 않는다. 사용자가 경고를 보고 교통수단·기간·장소를 바꿔 다시 만들게 한다.
- 기본값: 기간은 여행 회차 기간, 하루 일정은 09:00~21:00, 교통수단은 그룹 최빈값(없으면 BUS).
- 체류 시간: 예술·스포츠·과학·기술 업종 90분, 음식·소매 60분, 숙박 30분.
- 이동: 직선거리 × 1.3을 이동 거리로 보고 교통수단별 평균 속도로 나눈다(도보 70m/분, 자전거 250, 버스 300+대기 5분, 기차 500+5분, 택시·차 450, 택시 대기 3분).
- 영업 시간 정보가 있으면 체류 시간 내내 영업 중일 때만 배치하고, 아직 안 열었으면 열 때까지 기다린다. 영업 시간 정보가 없으면 영업 중으로 본다.
- 하루 일정을 넘기면 다음 날로 넘긴다. 방문일이 정해진 장소를 그날 배치할 수 없거나, 여행 기간 안에 다 배치할 수 없으면 409.
- 여행 회차당 타임라인은 하나다. 다시 만들면 이전 타임라인과 항목을 지운다.

### 타임라인 항목 수정

- VISIT 항목의 시작·종료 시각과 메모만 바꾼다. MOVE 항목은 400.
- 하루 일정 시간을 벗어나거나 같은 날 다른 방문과 겹치면 400. 메모를 빈 문자열로 보내면 지운다.
- 바꾼 시간에 장소가 영업하지 않는 구간이 있으면 저장은 하고 `has_conflict: true`를 내려준다.

## 나중에 교체할 부분

| 위치 | 지금 | 교체 |
| --- | --- | --- |
| `PassThroughGroupPlaceFilter` | 필터링 없음 | 나침반 그룹 기반 필터링 (예산·선호·알레르기) |
| `StoreRepository.MAX_REGION_CANDIDATES` | 범위 안 가까운 500곳만 후보 | 필터링을 DB 쿼리로 옮긴 뒤 제한 조정 |
| `TransportRange` | 교통수단별 고정 거리 | 길찾기 API의 소요 시간 기준 |
| `DestinationCategories` | 상가 업종 ↔ 여행 카테고리 임시 매핑 | 관광지 분류가 있는 장소 데이터 |
| `TravelTimeEstimator` | 직선거리 기반 어림 | 길찾기 API |
| `TimelinePlanner` 프롬프트 | 날짜·순서·체류 시간만 AI가 정함 | 필요하면 식사 장소 추천 등 확장 |

## Gemini 설정

`.env`(또는 환경변수)에 넣는다. 키가 없으면 규칙 기반으로만 동작한다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `GEMINI_API_KEY` | (없음) | Gemini API 키 |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | 모델 코드 |
| `GEMINI_TIMEOUT` | `30s` | 연결·응답 대기 시간 |

호출: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, 키는 `x-goog-api-key` 헤더로 보내고 `generationConfig.responseMimeType=application/json`, `responseJsonSchema`로 구조화 출력을 요청한다.

## 검증

```sh
./gradlew test --tests '*StoryModeApiTests' --tests '*TimelineSchedulerTests' --tests '*TimelinePlannerTests'
```

- `TimelinePlannerTests`: 가짜 Gemini로 AI 순서·체류 시간·메모 반영, 코드 블록·범위 밖 체류 시간 보정, 장소 누락·중복·없는 장소·기간 밖 날짜·방문일 위반·하루에 안 들어가는 일정·깨진 응답·규칙 기반보다 나쁜 동선일 때 규칙 기반으로 대체, 키가 없으면 호출하지 않음.

- `TimelineSchedulerTests`: 순차 배치와 MOVE, 다음 날 넘김, 영업 시작 대기, 방문일 고정 장소의 휴무, 일정 부족, 먼 이동 나누기, 동선 최적화(최단 순서, 방문일 고정 장소와 끼워 넣기).
- `StoryModeApiTests`: 추천(범위·중심) → 2차 추천(기본 반경) → 선택 저장 → 타임라인 생성 → 조회 → 항목 수정 → 재생성 흐름, 교통수단별 먼 장소 나누기·`long_distance`, 잘못된 입력, 취향 없음, 그룹 권한, 스토리모드가 아닌 여행.
