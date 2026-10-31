# 극한모드 — Google Places

경로 앞에 `/api/v2/groups/{groupId}`, Bearer JWT가 필요하다. 현재 EXTREME 회차와 전원 제출된 설문을 사용한다.

| 메서드 | 경로 | 요청/응답 |
| --- | --- | --- |
| GET | `/constraints/minimum` | `group_id,allergies,excluded_categories,source_member_count,updated_at` |
| POST | `/roulette/destinations` | 선택: `region,category,exclude_place_ids` → 201 |
| POST | `/roulette/restaurants` | 같은 요청 → 201, `group_allergies` 추가 |
| GET | `/roulette/{rouletteId}` | 저장된 추첨 결과 조회 |

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
