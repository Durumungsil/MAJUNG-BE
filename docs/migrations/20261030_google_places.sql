-- PostgreSQL. 서버를 중지하고 백업 후 적용한다. 자세한 순서는 ../google-places-deployment.md 참조.
-- 기존 숫자 Store ID를 Google place_id로 변환할 수 없어 스토리 선택·일정·룰렛 기록을 비운다.
-- 모험 missions/adventure_processes, stores, 설문과 그룹 데이터는 보존한다.
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(20261030, 1);
CREATE TABLE IF NOT EXISTS majung_schema_migrations (
    version varchar(80) PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);
DO $migration$
BEGIN
    IF EXISTS (SELECT 1 FROM majung_schema_migrations WHERE version = '20261030_google_places') THEN
        RAISE NOTICE '20261030_google_places already applied';
        RETURN;
    END IF;
    IF to_regclass('trips') IS NULL OR to_regclass('stores') IS NULL THEN
        RAISE EXCEPTION '기존 main 스키마에만 적용할 수 있습니다';
    END IF;

    CREATE TABLE google_place (
        place_id varchar(255) PRIMARY KEY,
        latitude double precision,
        longitude double precision,
        location_cached_at timestamptz
    );
    CREATE TABLE preference_profile (
        trip_id bigint PRIMARY KEY REFERENCES trips(id),
        version bigint NOT NULL,
        row_version bigint,
        status varchar(255),
        profile json,
        generated_by varchar(255),
        updated_at timestamptz NOT NULL,
        run_token varchar(36)
    );
    CREATE INDEX preference_profile_recovery ON preference_profile(status, updated_at);
    INSERT INTO preference_profile (trip_id, version, row_version, status, updated_at)
        SELECT id, 1, 0, 'PENDING', now() FROM trips;

    DELETE FROM itinerary_items;
    DELETE FROM itineraries;
    DELETE FROM destination_selections;
    DELETE FROM roulette_results;

    -- 해당 컬럼의 기존 FK·유일 제약은 DROP COLUMN과 함께 제거된다.
    ALTER TABLE destination_selections DROP COLUMN store_id;
    ALTER TABLE destination_selections ADD COLUMN google_place_id varchar(255) REFERENCES google_place(place_id);
    ALTER TABLE destination_selections ADD COLUMN category varchar(255);
    ALTER TABLE itinerary_items DROP COLUMN store_id;
    ALTER TABLE itinerary_items ADD COLUMN google_place_id varchar(255) REFERENCES google_place(place_id);
    ALTER TABLE roulette_results DROP COLUMN store_id;
    ALTER TABLE roulette_results ADD COLUMN google_place_id varchar(255) NOT NULL REFERENCES google_place(place_id);
    ALTER TABLE roulette_results ADD COLUMN category varchar(255);
    ALTER TABLE roulette_results ADD CONSTRAINT roulette_google_place_unique UNIQUE (trip_id, type, google_place_id);

    ALTER TABLE stores ADD COLUMN industry_small_code varchar(20);
    ALTER TABLE stores ADD COLUMN industry_small_name varchar(100);
    CREATE TABLE mission_rejected_places (
        mission_id bigint NOT NULL REFERENCES missions(id),
        store_id bigint NOT NULL,
        PRIMARY KEY (mission_id, store_id)
    );

    INSERT INTO majung_schema_migrations(version) VALUES ('20261030_google_places');
END
$migration$;
COMMIT;
