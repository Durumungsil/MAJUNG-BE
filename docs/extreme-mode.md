# 극한모드 — Google Places

경로 앞에 `/api/v2/groups/{groupId}`, Bearer JWT가 필요하다. 그룹 멤버만 사용할 수 있다. 추첨에는 현재 EXTREME 회차와 전원 제출된 설문을 사용한다. 확정·조회에는 설문을 다시 요구하지 않는다.

| 메서드 | 경로 | 요청/응답 |
| --- | --- | --- |
| GET | `/constraints/minimum` | `group_id,allergies,excluded_categories,source_member_count,updated_at` |
| POST | `/roulette/destinations` | 선택: `region,category,exclude_place_ids` → 201 |
| POST | `/roulette/restaurants` | 같은 요청 → 201, `group_allergies` 추가 |
| GET | `/roulette/{rouletteId}` | 저장된 추첨 결과 조회 |
| PUT | `/roulette/{rouletteId}/confirm` | 본문 없음 → 200, 현재 활성 회차의 추첨 결과 확정 |
| GET | `/roulette/current` | `group_id,trip_id,destination,restaurant` → 200, 종류별 최신 확정 결과 |

`category`는 공통 TravelCategory를 사용한다. 식당 룰렛에는 FOOD만 지정할 수 있다. FESTIVAL은 후보 없음(409)이다. region을 보내면 회차 지역 또는 등록된 별칭과 같아야 한다. `exclude_place_ids`는 문자열 배열이다.

```json
{
  "roulette_id": 1,
  "type": "DESTINATION",
  "place": {"place_id":"ChIJ_example","category":"CULTURE","latitude":null,"longitude":null},
  "round": 1,
  "drawn_at": "2026-10-30T00:00:00Z"
}
```

위 예시는 성공 응답의 data 부분이다. 장소 이름·주소는 응답 및 DB에 없다. 좌표는 다른 검색에서 유효한 캐시가 생긴 경우에만 제공될 수 있다. 추첨 자체는 좌표를 조회하지 않는다.

- 프로필에서 검색어 하나를 선택해 Text Search를 한 번 요청한다. field mask는 `places.id,nextPageToken`이며 첫 페이지에서 무작위 추첨한다. 다음 페이지를 읽지 않는다.
- 요청으로 제외한 ID와 같은 회차·같은 룰렛 종류에서 이미 뽑은 모든 ID를 제외한다. 남은 결과마다 동일 확률이며 검색 상위 20곳 밖을 포함한 전체 지역 무작위는 아니다.
- `google_place`에는 ID 행만 생성한다. 그룹 잠금과 DB 유일 제약으로 회차 번호·중복 추첨 충돌을 막는다.
- 최소 조건의 allergies는 프로필의 정규화 enum, excluded_categories는 프로필의 avoid_place_types다. 식당 응답에도 그룹 알레르기를 돌려준다.
- ID 전용 검색은 결과의 types를 받지 않는다. 따라서 피할 유형은 검색의 includedType 선택에서만 제외할 수 있고, 음식점의 추가 유형·메뉴·원재료까지 검증하지 못한다. 이 응답을 알레르기 안전 보증으로 표시하지 않는다. UNKNOWN 알레르기가 있으면 식당 추첨을 거부한다.

상세 이름·사진은 프론트 SDK로 조회한다. 운영 설정은 [배포 안내](google-places-deployment.md)를 따른다.

## 확정 및 시작 화면 복원

기준 명세: Notion Temp → [APIPI 며ㅇ세서 V1.4](https://www.notion.so/4056dcfa235080ae9de1cd2e5a502932)의 여행지 룰렛·식당 룰렛·룰렛 결과 조회. 기존 명세에 없는 확정과 현재 결과 조회를 추가한다.

1. 기존 POST API로 추첨한 뒤 사용자가 장소를 선택하면 `PUT /roulette/{rouletteId}/confirm`을 호출한다.
2. 시작 화면 진입·새로고침·다른 멤버의 접속 시 `GET /roulette/current`만 호출하면 두 카드를 복원할 수 있다.

확정 응답은 기존 단건 조회와 같은 형태이며 `confirmed_at`을 추가한다. 단건 조회에서 미확정 결과는 `confirmed_at: null`이다. 현재 결과 조회의 성공 응답 예시:

```json
{
  "success": true,
  "message": "현재 회차의 확정 룰렛 결과를 조회했습니다.",
  "data": {
    "group_id": 101,
    "trip_id": 201,
    "destination": {
      "roulette_id": 1001,
      "group_id": 101,
      "type": "DESTINATION",
      "place": {"place_id":"ChIJ_example","category":"CULTURE","latitude":null,"longitude":null},
      "drawn_at": "2026-11-02T10:00:00Z",
      "confirmed_at": "2026-11-02T10:01:00Z"
    },
    "restaurant": null
  }
}
```

- 추첨과 확정은 별개다. 새로 추첨해도 현재 확정 카드는 유지된다. 종류별로 **최초 확정 시각이 가장 최근인 결과**를 반환한다. 같은 시각이면 큰 `roulette_id`가 우선한다.
- 같은 결과를 다시 확정하면 최초 확정 시각을 유지한다. 예전 확정 요청의 재시도가 최신 결과를 되돌리지 않는다. 아직 확정하지 않은 이전 라운드도 선택할 수 있다.
- 확정하지 않은 종류는 명시적인 `null`이다. 둘 다 없으면 두 필드 모두 `null`인 200 응답이다. 이전 회차의 결과를 새 회차에 채워 넣지 않는다.
- 조회의 현재 회차는 다른 그룹 조회와 같이 활성 회차가 있으면 그것, 모두 완료됐으면 가장 최근 완료 회차다. 회차가 전혀 없으면 409다. 확정은 활성 회차에만 허용한다.
- 미인증은 401, 존재하지 않거나 참여하지 않은 그룹은 404, EXTREME 이외 모드·활성 회차 없는 확정은 409다. 현재 활성 회차에 속하지 않거나 없는 `rouletteId` 확정은 404다.
- 확정은 추첨·회차 생성·모드 변경과 같은 그룹 행 잠금으로 직렬화한다. 현재 결과 조회·확정은 Google API를 호출하지 않는다.

`main` 머지 시 자동 배포가 시작되므로 **머지 전에** [20261102_roulette_confirmation.sql](migrations/20261102_roulette_confirmation.sql)을 PostgreSQL에 적용한다. 예: `psql "$MIGRATION_DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/migrations/20261102_roulette_confirmation.sql`. 기존 추첨 기록은 삭제하지 않고 미확정으로 보존한다. SQL은 재실행할 수 있다. 앱은 기본 `ddl-auto=validate`이므로 컬럼 추가를 먼저 완료해야 한다.
