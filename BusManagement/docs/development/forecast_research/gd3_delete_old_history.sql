-- GĐ3 of the Demand Forecast redesign, PART B — DELETES the OLD generator's history and takes its
-- km back off the odometers (decision QĐ-11 in docs/development/forecast_decisions.md).
--
-- DESTRUCTIVE. Run it only after:
--   1. a mysqldump backup of the schema;
--   2. PART A (gd3_check_old_history.sql) shows the expected numbers;
--   3. on the real DB: the owner's final confirmation.
-- Then run the new generator: ./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < gd3_delete_old_history.sql
--
-- Why both odometer marks: the old generator added each COMPLETED trip's route distance to
-- odometer AND last_maintenance_odometer, so subtracting the same amount from both restores the
-- odometer and leaves km-since-maintenance (every maintenance rule) unchanged. Route distances are
-- the ones the old runs used (checked in PART A against the km each run recorded).
-- The mysql client runs with foreign_key_checks ON, so a trip still referenced anywhere makes the
-- DELETE fail and the transaction roll back instead of leaving an orphan.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS gd3_old_history;
CREATE TEMPORARY TABLE gd3_old_history AS
SELECT t.id, t.bus_id, t.status, r.distance_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND t.is_deleted = 0
  AND MINUTE(t.departure_time) = 0 AND HOUR(t.departure_time) IN (6, 12, 18)
  AND t.status IN ('COMPLETED', 'CANCELLED')
  AND DATE(t.created_at) IN ('2026-07-20', '2026-07-22', '2026-07-27', '2026-08-04', '2026-09-11', '2026-09-21');

SELECT 'before' AS step, (SELECT COUNT(*) FROM gd3_old_history) AS old_trips,
       (SELECT COUNT(*) FROM trips) AS all_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

UPDATE buses b
JOIN (SELECT bus_id, SUM(distance_km) AS km FROM gd3_old_history WHERE status = 'COMPLETED' GROUP BY bus_id) x
  ON x.bus_id = b.id
SET b.odometer = b.odometer - x.km,
    b.last_maintenance_odometer = b.last_maintenance_odometer - x.km;

DELETE t FROM trips t JOIN gd3_old_history o ON o.id = t.id;

SELECT 'after' AS step, (SELECT COUNT(*) FROM gd3_old_history) AS old_trips,
       (SELECT COUNT(*) FROM trips) AS all_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS gd3_old_history;
