-- Route pairs CT3/CT4, PART A — READ-ONLY checks before deleting the GĐ3 generator's history
-- (decision QĐ-21 in docs/development/route_pairs_decisions.md; same procedure as QĐ-14).
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_check_old_history.sql
--
-- "GĐ3 history": every trip the GĐ3 backfill run wrote on 2026-10-22 (11:22–11:23). All of them have
-- the generator shape (06/12/18 h, minute 0 regular or 30/40/50 extra) on routes 1–5.
--
-- Expected on the real DB as of 2026-11-17:
--   (1) 21,192 trips (342 extras, 19,829 COMPLETED, 1,363 CANCELLED), all generator-shaped, none
--       soft-deleted; per route 1: 4,242 · 2: 4,262 · 3: 4,198 · 4: 4,259 · 5: 4,231;
--   (2) 0 incidents / co-driver rows / trips from other days pointing at them, and every extra's
--       parent is itself part of the GĐ3 history;
--   (3) 0 buses without odometer marks;
--   (4) 3,252,020 km to subtract (COMPLETED only); total odometer 3,270,405 → 18,385 after PART B,
--       km since maintenance 18,355 before and after;
--   (5) 19 other trips (the owner's, kept), none generator-shaped on routes 2/5/10/11/12/13.

-- (1) How many, of which shape
SELECT t.route_id, COUNT(*) AS trips, SUM(t.is_extra_trip) AS extras,
       SUM(t.status = 'COMPLETED') AS completed, SUM(t.status = 'CANCELLED') AS cancelled,
       SUM(HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)) AS generator_shaped,
       SUM(t.is_deleted) AS soft_deleted
FROM trips t
WHERE DATE(t.created_at) = '2026-10-22'
GROUP BY t.route_id WITH ROLLUP;

-- (2) Nothing outside the GĐ3 history may point at it (the app runs with foreign_key_checks=0)
SELECT
  (SELECT COUNT(*) FROM incidents i JOIN trips t ON t.id = i.trip_id
    WHERE DATE(t.created_at) = '2026-10-22') AS incident_refs,
  (SELECT COUNT(*) FROM trip_co_drivers cd JOIN trips t ON t.id = cd.trip_id
    WHERE DATE(t.created_at) = '2026-10-22') AS co_driver_refs,
  (SELECT COUNT(*) FROM trips c JOIN trips t ON t.id = c.original_trip_id
    WHERE DATE(t.created_at) = '2026-10-22' AND DATE(c.created_at) <> '2026-10-22') AS outside_child_refs,
  (SELECT COUNT(*) FROM trips c LEFT JOIN trips p ON p.id = c.original_trip_id
    WHERE DATE(c.created_at) = '2026-10-22' AND c.is_extra_trip = 1
      AND (p.id IS NULL OR DATE(p.created_at) <> '2026-10-22')) AS extras_with_parent_elsewhere;

-- (3) Odometer marks present on every bus
SELECT COUNT(*) AS buses_without_odometer_marks FROM buses
WHERE odometer IS NULL OR last_maintenance_odometer IS NULL;

-- (4) What PART B will subtract, per bus, and the km-since-maintenance that must not change
SELECT t.bus_id, ROUND(SUM(r.distance_km)) AS km_to_subtract,
       ROUND(b.odometer) AS odometer_now, ROUND(b.odometer - b.last_maintenance_odometer) AS km_since_maintenance
FROM trips t JOIN routes r ON r.id = t.route_id JOIN buses b ON b.id = t.bus_id
WHERE DATE(t.created_at) = '2026-10-22' AND t.status = 'COMPLETED'
GROUP BY t.bus_id, b.odometer, b.last_maintenance_odometer
ORDER BY t.bus_id;

SELECT ROUND(SUM(odometer)) AS total_odometer,
       ROUND(SUM(odometer - last_maintenance_odometer)) AS total_km_since_maintenance,
       (SELECT ROUND(SUM(r.distance_km)) FROM trips t JOIN routes r ON r.id = t.route_id
         WHERE DATE(t.created_at) = '2026-10-22' AND t.status = 'COMPLETED') AS km_to_subtract
FROM buses;

-- (5) The trips that stay (made by hand in the app). None may have the generator shape on the routes
-- the new generator writes, or the new generator would take that day for one of its own.
SELECT COUNT(*) AS kept_trips,
       SUM(route_id IN (2, 5, 10, 11, 12, 13) AND HOUR(departure_time) IN (6, 12, 18)
           AND MINUTE(departure_time) IN (0, 30, 40, 50)) AS kept_generator_shaped_violations
FROM trips
WHERE DATE(created_at) <> '2026-10-22';
