-- GĐ3 of the Demand Forecast redesign, PART A — READ-ONLY checks before deleting the OLD
-- generator's history (decision QĐ-11 in docs/development/forecast_decisions.md).
-- Rehearsed in GĐ2 (2026-10-21) on the clone busmanagement_gd2; on the real DB only in GĐ3.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < gd3_check_old_history.sql
--
-- Expected on the real DB as of 2026-10-21:
--   (1) 2,206 old-generator trips, all created on the six backfill run dates below, both counts equal;
--   (2) 0 incidents / child trips / co-driver rows pointing at them;
--   (3) 0 buses without odometer marks;
--   (4) 2,061 COMPLETED among them, 337,860 km to subtract. Per run date the km match what each
--       backfill run recorded (e.g. 2026-09-11 → 87,360 km), i.e. route distances never changed.

-- "Old-generator trip": a route the generator used (≤ 8 h), departure exactly 06:00 / 12:00 / 18:00,
-- not an extra trip, COMPLETED or CANCELLED, not soft-deleted — and created by one of the six runs.

-- (1) How many, and created when
SELECT DATE(t.created_at) AS created_on, COUNT(*) AS trips, SUM(t.status = 'COMPLETED') AS completed,
       ROUND(SUM(CASE WHEN t.status = 'COMPLETED' THEN r.distance_km ELSE 0 END)) AS completed_km
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND t.is_deleted = 0
  AND MINUTE(t.departure_time) = 0 AND HOUR(t.departure_time) IN (6, 12, 18)
  AND t.status IN ('COMPLETED', 'CANCELLED')
GROUP BY DATE(t.created_at) ORDER BY 1;

SELECT
  (SELECT COUNT(*) FROM trips t JOIN routes r ON r.id = t.route_id
    WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND t.is_deleted = 0
      AND MINUTE(t.departure_time) = 0 AND HOUR(t.departure_time) IN (6, 12, 18)
      AND t.status IN ('COMPLETED', 'CANCELLED')) AS by_shape,
  (SELECT COUNT(*) FROM trips t JOIN routes r ON r.id = t.route_id
    WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND t.is_deleted = 0
      AND MINUTE(t.departure_time) = 0 AND HOUR(t.departure_time) IN (6, 12, 18)
      AND t.status IN ('COMPLETED', 'CANCELLED')
      AND DATE(t.created_at) IN ('2026-07-20', '2026-07-22', '2026-07-27', '2026-08-04', '2026-09-11', '2026-09-21'))
    AS by_shape_and_run_date;

-- (2) Nothing may point at them (the app runs with foreign_key_checks=0, so check by hand)
SELECT
  (SELECT COUNT(*) FROM incidents i JOIN trips t ON t.id = i.trip_id JOIN routes r ON r.id = t.route_id
    WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND MINUTE(t.departure_time) = 0
      AND HOUR(t.departure_time) IN (6, 12, 18) AND t.status IN ('COMPLETED', 'CANCELLED')) AS incident_refs,
  (SELECT COUNT(*) FROM trips c JOIN trips t ON t.id = c.original_trip_id JOIN routes r ON r.id = t.route_id
    WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND MINUTE(t.departure_time) = 0
      AND HOUR(t.departure_time) IN (6, 12, 18) AND t.status IN ('COMPLETED', 'CANCELLED')) AS child_trip_refs,
  (SELECT COUNT(*) FROM trip_co_drivers cd JOIN trips t ON t.id = cd.trip_id JOIN routes r ON r.id = t.route_id
    WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND MINUTE(t.departure_time) = 0
      AND HOUR(t.departure_time) IN (6, 12, 18) AND t.status IN ('COMPLETED', 'CANCELLED')) AS co_driver_refs;

-- (3) Odometer marks present on every bus
SELECT COUNT(*) AS buses_without_odometer_marks FROM buses
WHERE odometer IS NULL OR last_maintenance_odometer IS NULL;

-- (4) What PART B will subtract, per bus, and the km-since-maintenance that must not change
SELECT t.bus_id, ROUND(SUM(r.distance_km)) AS km_to_subtract,
       ROUND(b.odometer) AS odometer_now, ROUND(b.odometer - b.last_maintenance_odometer) AS km_since_maintenance
FROM trips t JOIN routes r ON r.id = t.route_id JOIN buses b ON b.id = t.bus_id
WHERE r.estimated_duration <= 480 AND t.is_extra_trip = 0 AND t.is_deleted = 0
  AND MINUTE(t.departure_time) = 0 AND HOUR(t.departure_time) IN (6, 12, 18)
  AND t.status = 'COMPLETED'
  AND DATE(t.created_at) IN ('2026-07-20', '2026-07-22', '2026-07-27', '2026-08-04', '2026-09-11', '2026-09-21')
GROUP BY t.bus_id, b.odometer, b.last_maintenance_odometer ORDER BY t.bus_id;
