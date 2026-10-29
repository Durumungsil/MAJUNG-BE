# Places 전환 배포 순서

대상 브랜치: `feature/google-places-migration`, 기준: origin/main `d3446eb`.
스토리·극한은 Google Places, 모험은 기존 Stores를 사용한다. 로컬 `.env` 키는 커밋하지 않는다.

## 배포 전 운영자 확인

1. Cloud 프로젝트 `project-2943cc8d-5e1c-41a0-bd0`의 결제 계정이 활성 상태이고 Places API (New)가 사용 설정되어 있는지 확인한다. 결제 재활성화는 계정 소유자가 완료해야 한다.
2. 서버 환경변수에 `GOOGLE_PLACES_API_KEY`를 등록한다. 로컬 `.env`는 배포 서버에 자동 복사되지 않는다. 서버 키를 프론트 번들에 넣지 않는다.
3. Gemini 키가 유료 등급 프로젝트에 속하는지 확인한 뒤 `GEMINI_PAID_TIER_CONFIRMED=true`를 설정한다. 확인 전에는 false를 유지한다. false/키 없음/AI 실패 시 프로필은 DONE(RULE)로 완성되므로 서비스가 막히지 않는다. 원문 알레르기를 외부 모델에 보내는 것은 이 플래그가 true일 때만 가능하다.
4. 프론트는 [새 계약](google-places-frontend-tasks.md)에 맞춰 함께 배포한다. 상세 Google 이름·사진을 프론트 SDK로 가져올 경우 별도 브라우저 키와 해당 SDK/API 설정이 필요하다. 현재 발급한 것은 서버 키다.

사용자 요청에 따라 Cloud 공인 IP 제한은 변경하지 않았다. 서버 배포 IP가 정해진 뒤 제한을 추가하려면 별도 운영 작업으로 진행한다.

## DB 변경

자동 ddl-auto=update만으로 기존 Store FK를 안전하게 전환할 수 없다. **앱을 시작하기 전에** 적용한다.

1. 서버 쓰기 트래픽을 중지하고 DB 전체 백업을 확보한다. 복원 가능한지 확인한다.
2. 기존 main 스키마에 [20261030_google_places.sql](migrations/20261030_google_places.sql)을 PostgreSQL 클라이언트로 실행한다. 예: `psql "$MIGRATION_DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/migrations/20261030_google_places.sql`. 이 URL은 PostgreSQL 연결 URL이며 JDBC 접두어를 사용하지 않는다.
3. 스크립트는 한 트랜잭션으로 실행되고 적용 기록을 남긴다. 같은 스크립트 재실행은 데이터를 다시 비우지 않는다. 새 코드가 먼저 자동 생성한 스키마에 적용하면 오류가 나므로, 그런 경우 임의로 테이블을 삭제하지 말고 백업/현재 스키마를 비교한다.
4. 기존 **스토리 선택·일정 항목·일정·룰렛 결과는 삭제된다.** 숫자 Store ID에서 Google ID를 만들어낼 수 없기 때문이다. 그룹·여행·설문·Stores·모험 미션은 보존한다. 기존 회차의 프로필은 PENDING으로 만들며 전원 설문이 있는 회차만 복구 워커가 생성한다.
5. 새 서버를 `JPA_DDL_AUTO=validate`로 기동한다. 컬럼/FK 문제를 먼저 해결하고 API 상태를 확인한다. 기존 버전으로 롤백하려면 코드를 되돌리는 것과 함께 변경 전 DB 백업을 복원해야 한다.
6. 리버스 프록시를 사용하면 GET 추천 cursor가 통과하도록 요청 헤더/URL 상한을 서버의 64KB에 맞춘다. cursor는 압축·서명되며 60,000자 상한이다.
7. 운영 확인: 설문 저장→profile_status DONE, 추천→선택→일정 생성·조회, 룰렛 2회 중복 제외, 모험 start→hint→distance→complete를 확인한다. 유료 API 요청이 발생하므로 실제 호출은 소량으로 한다.

## 모험 데이터 보강

기존 `scripts/preprocess_busan_stores.py`는 업종 소분류를 출력하지 않았다. 이제 원본의 `상권업종소분류코드/명`을 `industry_small_code/name`으로 보존한다. 원본 CSV를 다시 전처리하고 기존 Store에 식별 가능한 원본 상가 키 또는 검증된 매칭으로 두 컬럼을 보강한다. 이름만으로 대량 UPDATE하거나 Stores를 삭제·재적재하면 모험 FK가 깨질 수 있다. 이 저장소에는 원본 CSV와 안정적인 원본 상가 ID가 없어 운영 데이터 보강을 자동 실행하지 않았다.

보강 전에도 모험은 동작한다. 알레르기 입력이 있는 그룹에는 소분류가 없는 음식점이 제외되고 비음식 장소가 후보로 남는다. 업종 규칙은 원재료/교차 접촉 안전을 보증하지 않는다.

## 호출량과 사용 종료

2026-10-29 콘솔에서 설정한 값은 Autocomplete 일 600, Text Search·Place Details·Nearby Search·Photos 각각 일 60이다. 사용하지 않는 추가 메서드는 0으로 제한했다. 각 일 60은 15일 사용 시 최대 900회다. 실제 무료 사용량은 SKU·월 누적·같은 결제 계정의 다른 사용량에 따라 확인해야 한다. 추천 하나가 검색어 여러 개로 확장되므로 일 60은 화면 60회가 아니다.

월 10,000원 예산은 50/80/100% 알림이며 **하드 지출 상한이 아니다**. SDK·다른 API에는 별도 할당량이 적용될 수 있다. 결제/할당량은 Cloud Console에서 확인한다. 약 15일 사용이 끝나면(10월 29일 시작 기준 약 11월 13일) Places 할당량을 0으로 낮추거나 키를 사용 중지한다. 현재 자동 만료는 설정되어 있지 않다.

## 검증 기록

- Java 21, H2 PostgreSQL 모드 전체 82개 테스트 통과(실패/건너뜀 0) 및 Gradle build 성공. 큰 추천 cursor를 실제 Tomcat HTTP 요청으로 전달하는 테스트 포함.
- 임시 PostgreSQL 17에서 좌표 upsert·만료·재조회, 프로필 JSON/버전, 스토리·극한·모험 API 테스트. API 호출은 모두 가짜 클라이언트다.
- PostgreSQL의 기존 Store FK를 가진 소규모 테스트 스키마에 마이그레이션 적용 및 재실행: 모험/Stores 보존, 스토리/룰렛 기록 초기화, 프로필 PENDING 생성 확인. 추가로 실제 main(d3446eb) 코드로 생성한 전체 PostgreSQL 스키마에 SQL을 적용하고 새 서버가 ddl-auto=validate로 기동하는 것까지 확인했다. 운영 DB는 변경하지 않았다.
