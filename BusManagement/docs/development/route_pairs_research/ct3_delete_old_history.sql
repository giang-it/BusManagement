-- Route pairs CT3/CT4, PART B — DELETES the GĐ3 generator's history and takes its km back off the
-- odometers (decision QĐ-21 in docs/development/route_pairs_decisions.md; same procedure as QĐ-14).
--
-- DESTRUCTIVE. Run it only after:
--   1. a mysqldump backup of the schema;
--   2. PART A (ct3_check_old_history.sql) shows the expected numbers;
--   3. on the real DB: the owner's final confirmation.
-- Then run the new generator (backfill profile, see README.md) and PART C, ct3_check_new_history.sql.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_delete_old_history.sql
--
-- Why both odometer marks: the generator added each COMPLETED trip's route distance to odometer AND
-- last_maintenance_odometer, so subtracting the same amount from both restores the odometer and
-- leaves km-since-maintenance (every maintenance rule) unchanged.
-- Extras first: an extra trip points at its regular trip through original_trip_id. The mysql client
-- runs with foreign_key_checks ON, so deleting a parent first would fail and roll everything back.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS ct3_old_history;
CREATE TEMPORARY TABLE ct3_old_history AS
SELECT t.id, t.bus_id, t.status, t.is_extra_trip, r.distance_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE DATE(t.created_at) = '2026-10-22'
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  AND t.status IN ('COMPLETED', 'CANCELLED');

-- MySQL cannot open a TEMPORARY table twice in one statement, hence the single derived table.
SELECT 'before' AS step, o.old_trips, o.old_extras,
       (SELECT COUNT(*) FROM trips) AS all_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance
FROM (SELECT COUNT(*) AS old_trips, SUM(is_extra_trip) AS old_extras FROM ct3_old_history) o;

UPDATE buses b
JOIN (SELECT bus_id, SUM(distance_km) AS km FROM ct3_old_history WHERE status = 'COMPLETED' GROUP BY bus_id) x
  ON x.bus_id = b.id
SET b.odometer = b.odometer - x.km,
    b.last_maintenance_odometer = b.last_maintenance_odometer - x.km;

DELETE t FROM trips t JOIN ct3_old_history o ON o.id = t.id WHERE o.is_extra_trip = 1;
DELETE t FROM trips t JOIN ct3_old_history o ON o.id = t.id;

SELECT 'after' AS step, (SELECT COUNT(*) FROM trips t JOIN ct3_old_history o ON o.id = t.id) AS old_trips_left,
       (SELECT COUNT(*) FROM trips) AS all_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS ct3_old_history;
