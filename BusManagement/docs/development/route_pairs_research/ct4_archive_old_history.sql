-- Route pairs CT4, PART B as the owner asked on 2026-11-17 — ARCHIVES the GĐ3 history instead of deleting it:
-- "PART B: KHÔNG XÓA, HÃY SAO LƯU LẠI ĐỂ MỞ LẠI NẾU CẦN lịch sử GĐ3 và trừ km khỏi odometer".
-- (ct3_delete_old_history.sql, the deleting variant, was only used for the CT3 rehearsal on a clone.)
--
-- How: every GĐ3 trip is SOFT-deleted (is_deleted = 1), the app's own way of deleting a trip (Trip has
-- @SQLDelete / @SQLRestriction("is_deleted = false")). The rows stay in the table, untouched otherwise,
-- but every JPA query — the generator, the forecast, every screen — no longer sees them, and the app has
-- no native SQL. The km they added are taken off BOTH odometer marks, so km-since-maintenance does not
-- move. ct4_restore_old_history.sql re-opens them.
--
-- Run only after a mysqldump backup and PART A (ct3_check_old_history.sql). Running it twice is harmless:
-- the second run finds no GĐ3 trip still visible and changes nothing.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < ct4_archive_old_history.sql

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS ct4_old_history;
CREATE TEMPORARY TABLE ct4_old_history AS
SELECT t.id, t.bus_id, t.status, r.distance_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE DATE(t.created_at) = '2026-10-22' AND t.is_deleted = 0
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  AND t.status IN ('COMPLETED', 'CANCELLED');

-- MySQL cannot open a TEMPORARY table twice in one statement, hence one derived table per statement.
SELECT 'before' AS step, o.to_archive, o.completed_km,
       (SELECT COUNT(*) FROM trips WHERE is_deleted = 0) AS visible_trips,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance
FROM (SELECT COUNT(*) AS to_archive,
             ROUND(SUM(CASE WHEN status = 'COMPLETED' THEN distance_km ELSE 0 END)) AS completed_km
      FROM ct4_old_history) o;

UPDATE buses b
JOIN (SELECT bus_id, SUM(distance_km) AS km FROM ct4_old_history WHERE status = 'COMPLETED' GROUP BY bus_id) x
  ON x.bus_id = b.id
SET b.odometer = b.odometer - x.km,
    b.last_maintenance_odometer = b.last_maintenance_odometer - x.km;

UPDATE trips t JOIN ct4_old_history o ON o.id = t.id SET t.is_deleted = 1;

SELECT 'after' AS step,
       (SELECT COUNT(*) FROM trips t JOIN ct4_old_history o ON o.id = t.id WHERE t.is_deleted = 1) AS archived,
       (SELECT COUNT(*) FROM trips WHERE is_deleted = 0) AS visible_trips,
       (SELECT COUNT(*) FROM trips) AS rows_in_table,
       (SELECT ROUND(SUM(odometer)) FROM buses) AS total_odometer,
       (SELECT ROUND(SUM(odometer - last_maintenance_odometer)) FROM buses) AS total_km_since_maintenance;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS ct4_old_history;
