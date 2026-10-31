# 모험모드 API

명세: [API 명세서 V1.2](https://www.notion.so/3fe6dcfa2350803cad92d488204b2d79)

`main`의 도메인을 바탕으로 구현했다. 디자인 완성 상태가 `열외`인 **초이스**, **미션 완료 인증하기**는 구현하지 않는다. 위치 검증은 명세의 **미션 완료**에 포함한다.

## API

모든 경로 앞에 `/api/v1/groups/{groupId}`가 붙는다. 성공 응답은 `{success, message, data}`, 오류 응답은 `{success:false, error_code, message}`다. 요청·응답 JSON 키는 다른 API와 같이 모두 snake_case다(`mission_id`, `completed_at`, `current_mission_id`, `next_mission_id` 등).

| 메서드 | 경로 | 입력 | 결과 |
| --- | --- | --- | --- |
| POST | `/adventure/process` | `travel_date`, `mission_count` | 201, 생성 정보와 첫 미션 ID |
| GET | `/adventure/process/{processId}` | 없음 | 200, 상태·진행도·순서별 미션 상태 |
| GET | `/missions/current` | 없음 | 200, 오늘의 AVAILABLE 미션 |
| POST | `/missions/{missionId}/start` | `latitude`, `longitude` | 200, 최초 시작 시각 |
| GET | `/missions/{missionId}/hint` | 없음 | 200, 장소 좌표·표시 시간·누적 사용 횟수 |
| POST | `/missions/{missionId}/distance` | `latitude`, `longitude` | 200, `distance_meters` |
| POST | `/missions/{missionId}/replace` | `reason: ROADVIEW_UNAVAILABLE` | 200, 교체된 현재 미션 |
| POST | `/missions/{missionId}/complete` | `latitude`, `longitude`, 필수 `accuracy_meters`, 선택적 `completed_at` | 200, 완료 시각·다음 미션 ID |

오류: 잘못된 날짜·좌표·요청 형식·도착 반경 초과는 400, 현재 여행/현재 미션/소속 리소스가 없으면 404, 중복 생성·잠긴/완료된 미션·시작 전 완료·생성 조건 불충족은 409다. 위치 실패 응답 두 가지(200 실패 및 400)가 병기된 명세 중 400을 적용한다. 실패 시 미션은 AVAILABLE로 남아 재시도할 수 있다.

## 동작 정책

- 그룹의 PLANNING 또는 IN_PROGRESS 여행 회차를 사용하며 모드는 ADVENTURE여야 한다. 미완료 여행이 여러 개면 데이터 충돌로 409를 반환한다.
- 커리큘럼은 여행 기간 안의 날짜별로 하나만 생성한다. 다시 생성하면 409다. 장소는 중복 없이 뽑으며, 첫 미션만 AVAILABLE이고 이후 미션은 LOCKED다.
- 현재 미션의 날짜는 `Asia/Seoul` 기준이다. 시작·힌트·완료도 오늘의 미션에서만 가능하다. 미래 날짜의 커리큘럼 생성과 진행도 조회는 가능하다.
- 시작 시 서버의 UTC 시각과 출발 좌표를 보존한다. 시작·완료 시각은 DB와 동일한 마이크로초 정밀도로 저장·응답하며, 재시작 요청은 최초 기록을 돌려준다.
- 힌트마다 횟수를 1 증가시킨다. 잠기거나 완료된 미션은 힌트를 볼 수 없다. 표시 시간 후 로드뷰 닫기는 프론트에서 처리한다.
- 목적지 ID·이름·좌표는 일반 조회에 포함하지 않는다. 힌트 응답에서만 목적지 좌표를 제공한다.
- 거리 조회는 힌트를 사용한 현재 미션만 허용하며 미사용 시 409다. 목적지까지 직선거리(m)를 반환한다.
- 로드뷰 미지원은 프론트가 감지해 replace를 요청한다. 힌트 사용 후 최대 3회 교체하며 기존·거절 장소는 재사용하지 않는다. 교체는 앞뒤 장소와 각각 2km 이내인 후보 중 선택한다. 대체 후보가 없으면 409이고 기존 미션을 유지한다. 성공 시 시작 시각·좌표·힌트 횟수를 초기화하므로 다시 start해야 한다. 서버는 별도의 Street View API를 호출하지 않는다.
- 완료는 정확도가 0 초과 50m 이하이고, 위치 오차 범위까지 목적지 반경 안에 있어야 허용한다. 시작 후 15초 미만이거나 출발점 대비 이동이 초당 3m를 초과하면 거부한다(도착 반경·정확도 오차를 차감). GPS 조작 자체를 완전히 판별하는 기능은 아니다. 시작 위치의 직선거리·힌트 횟수·서버 경과 시간으로 기존 `Rank` 규칙에 따라 SS~E를 저장한다. 클라이언트 `completed_at`은 시작~현재 사이인지 검사하되, 저장 시각과 랭크는 서버 시각을 사용한다.
- 완료 후 다음 미션을 AVAILABLE로 연다. 마지막 완료 시 커리큘럼이 COMPLETED가 된다. 다음 미션 ID와 현재 미션 ID는 없으면 null이다.
- 그룹 → 여행 순서의 비관적 잠금으로 동일 그룹의 멤버·취향·설정 변경과 생성·힌트·완료를 직렬화한다. DB에도 `(trip_id, travel_date)` 유일 제약이 있다.

## 장소 선정과 필요한 데이터

그룹·현재 여행·그룹 멤버·멤버별 현재 회차 취향·장소가 DB에 있어야 한다. 이 데이터를 생성하는 다른 도메인 API는 이번 범위에 포함하지 않는다.

- 현재 그룹 멤버 전원의 취향이 있어야 한다. 탈퇴한 사용자의 취향은 집계하지 않는다.
- 여행 지역과 장소의 `sido` 또는 `sigungu`가 일치하는 후보를 사용한다. BUSAN/부산/부산광역시, SEOUL/서울/서울특별시, GANGNEUNG/강릉/강릉시, JEJU/제주/제주특별자치도는 같은 지역으로 취급한다. 그 외 지역은 저장된 문자열의 정확한 일치를 사용한다.
- 멤버별 예산 중 최솟값을 `여행 일수 × 하루 3끼`로 나눠 음식 장소의 1인 가격을 제한한다. 기존 Store 정책대로 가격 미상 장소는 예산으로 제외하지 않는다.
- 현재 취향과 TripConstraint의 알레르기를 정규화하고, 음식점의 `industry_small_name`(소분류명)을 `StoreAllergyFilter` 규칙과 비교한다. 예: 갑각류→해산물/횟집, 우유→제과/아이스크림/커피. 소분류가 없는 음식점 또는 UNKNOWN 알레르기는 보수적으로 제외한다. 업종 필터는 원재료·교차 접촉의 안전 판정이 아니다. 기존 전처리에는 소분류가 없었으므로 새 CSV의 `industry_small_code/name`을 기존 Store에 보강해야 정밀 필터가 작동한다.
- TripConstraint의 `excluded_categories`는 Store.Type의 enum 이름·코드·한글 표시명으로 판별한다. `EXTREME_HEIGHT`처럼 현 데이터 모델로 판별할 수 없는 조건이나 잘못된 JSON은 무시하지 않고 409를 반환한다.
- 숙박(LODGING)·전문 기술(SCIENCE_TECHNOLOGY)과 제외 업종은 DB 조회에서 거르고, 평점 높은 순(평점 없으면 뒤로) **최대 500곳**만 후보로 불러온다. 지역 전체를 불러오면 DB 연결이 끊겨 500 오류가 났기 때문이다.
- 후보를 섞은 후 연속 장소 사이 직선거리가 2km 이하인 경로를 무작위 탐색한다. 실제 보행 경로 API를 호출하지 않으므로 강·도로 등의 우회는 반영하지 않는다. 후보가 부족하면 409이며 커리큘럼·미션을 남기지 않는다.

## 설정

`.env` 또는 환경변수로 조정한다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `HINT_VIEW_SECONDS` | 60 | 힌트 표시 시간(초) |
| `MISSION_ARRIVAL_RADIUS_METERS` | 100 | 위치 인증 반경(m) |
| `MAX_MISSION_COUNT` | 20 | 생성 가능한 미션 수 상한 |

각 값은 양수여야 한다. DB 연결은 기존 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용한다.

## 인증 연결

모든 모험 API는 `Authorization: Bearer {access_token}`이 필요하다. 중앙 `SecurityConfig`가 `/api/v1/groups/{groupId}/adventure/**`, `/missions/**` 요청에 대해 로그인 사용자가 그 그룹의 멤버인지 검사하고, 아니면 403을 반환한다. 요청한 그룹과 커리큘럼/미션이 같은 현재 여행에 속하는지는 서비스에서 검사한다.

예전의 개발용 인증 우회 설정(`AdventureSecurityConfig`, `local` 프로필)은 중앙 `SecurityConfig`가 들어온 뒤로 등록되지 않는 코드여서 패키지 구조 정리 때 삭제했다.

## 검증

### 다른 기능 브랜치와의 통합

모험 API도 다른 도메인과 같은 저장소(`TripRepository`, `GroupMemberRepository`, `PreferenceRepository`)와 공통 예외(`BusinessException`/`ErrorCode`)·응답(`ApiResponse`)을 쓴다. 오류 메시지와 상태 코드는 이전과 같다. 상태 전이 오류(409)·중복 저장(409)·요청 형식 오류 메시지처럼 모험 API에만 있던 응답은 `AdventureExceptionHandler`가 유지한다.

```sh
./gradlew test
./gradlew build
```

기본 테스트는 PostgreSQL 호환 모드의 H2를 사용한다. 상태 전이, 예산/알레르기/제외 조건, 6개 API 전체 흐름, 위치 실패 후 재시도, 시작 멱등성, 정보 비공개, 다른 그룹 리소스 접근, 날짜 경계, 랭크의 서버 시각 사용, 동시 생성·힌트·완료, 인증·그룹 권한 없는 6개 API 차단, ERROR 디스패치의 상태 보존을 검증한다. PR 검토 시 별도로 생성한 임시 PostgreSQL 15.17에서도 동일한 전체 테스트 22개와 동시성 검증을 통과했다. 운영 DB나 기존 데이터에는 접근하지 않았다.

2026-10 Places 전환 이후 모험만 Stores를 사용한다. 새 API와 위치 정확도·시간·속도 거부 조건, 미션 교체의 재시작·중복 방지·횟수 제한을 테스트에 추가했다.
