-- Route pairs CT4 — RE-OPENS the GĐ3 history archived by ct4_archive_old_history.sql, and archives the
-- CT4 history in its place (the two must never be visible together: both have the generator shape, and
-- the forecast would learn from both). Not run on the real DB; rehearsed on a clone on 2026-11-17.
--
-- What it does, in one transaction:
--   1. the CT4 history = every visible generator-shaped trip on routes 2, 5, 10, 11, 12, 13 created on the
--      CT4 run date (2026-11-17, set below) → soft-deleted, and its km taken off both odometer marks;
--   2. the GĐ3 history = every soft-deleted generator-shaped trip created on 2026-10-22 → visible again,
--      and its km added back to both marks.
-- km-since-maintenance does not move. Run it a second time and it swaps nothing more: step 1 finds no
-- visible CT4 trip and step 2 no archived GĐ3 trip.
-- Check first: no trip made by hand in the app on the CT4 run date may have the generator shape on those
-- routes (PART A item 5 checked this before CT4), or step 1 would archive it too.
--
-- Afterwards the real DB holds the GĐ3 data again; the CT3 generator would refuse to top it up (its routes
-- 1, 3, 4 are outside backfill.route-ids, and its days are not loops). To go forward again, run
-- ct4_archive_old_history.sql, then un-archive the CT4 trips the same way as step 2 below.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < ct4_restore_old_history.sql

SET @ct4_run_date = '2026-11-17';

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS ct4_new_history;
CREATE TEMPORARY TABLE ct4_new_history AS
SELECT t.id, t.bus_id, t.status, r.distance_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE DATE(t.created_at) = @ct4_run_date AND t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  AND t.status IN ('COMPLETED', 'CANCELLED');

DROP TEMPORARY TABLE IF EXISTS ct4_old_history;
CREATE TEMPORARY TABLE ct4_old_history AS
SELECT t.id, t.bus_id, t.status, r.distance_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE DATE(t.created_at) = '2026-10-22' AND t.is_deleted = 1
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  AND t.status IN ('COMPLETED', 'CANCELLED');

SELECT 'before' AS step,
       (SELECT COUNT(*) FROM ct4_new_history) AS ct4_trips_to_archive,
       (SELECT COUNT(*) FROM ct4_old_history) AS gd3_trips_to_reopen,
       (SELECT COUNT(*) FROM trips WHERE is_deleted = 0) AS visible_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

UPDATE buses b
JOIN (SELECT bus_id, SUM(distance_km) AS km FROM ct4_new_history WHERE status = 'COMPLETED' GROUP BY bus_id) x
  ON x.bus_id = b.id
SET b.odometer = b.odometer - x.km,
    b.last_maintenance_odometer = b.last_maintenance_odometer - x.km;
UPDATE trips t JOIN ct4_new_history n ON n.id = t.id SET t.is_deleted = 1;

UPDATE buses b
JOIN (SELECT bus_id, SUM(distance_km) AS km FROM ct4_old_history WHERE status = 'COMPLETED' GROUP BY bus_id) x
  ON x.bus_id = b.id
SET b.odometer = b.odometer + x.km,
    b.last_maintenance_odometer = b.last_maintenance_odometer + x.km;
UPDATE trips t JOIN ct4_old_history o ON o.id = t.id SET t.is_deleted = 0;

SELECT 'after' AS step,
       (SELECT COUNT(*) FROM trips WHERE is_deleted = 0) AS visible_trips,
       (SELECT COUNT(*) FROM trips) AS rows_in_table,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS ct4_new_history;
DROP TEMPORARY TABLE IF EXISTS ct4_old_history;
