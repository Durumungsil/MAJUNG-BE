-- PostgreSQL. 새 앱을 시작하기 전에 적용한다. 기존 추첨 기록은 미확정으로 보존한다.
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(20261102, 1);

ALTER TABLE roulette_results ADD COLUMN IF NOT EXISTS confirmed_at timestamptz;
CREATE INDEX IF NOT EXISTS roulette_confirmed_lookup
    ON roulette_results (trip_id, type, confirmed_at DESC, id DESC);

COMMIT;
