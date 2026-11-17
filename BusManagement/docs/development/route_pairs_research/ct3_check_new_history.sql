-- Route pairs CT3/CT4, PART C — READ-ONLY checks AFTER the new generator has run
-- (decision QĐ-21 in docs/development/route_pairs_decisions.md: pairs, return ratio, bus loops).
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_check_new_history.sql
--
-- "Generator trip": a route in backfill.route-ids — here the default 2, 5, 10, 11, 12, 13 (edit the
-- six IN (...) lists below if the property was changed) — departure at 06/12/18 h, minute 0 (regular)
-- or 30/40/50 (extra), not soft-deleted. Every other non-deleted trip is a "kept" trip (the owner's).
-- A trip "runs" unless it is CANCELLED; only running trips move a bus or a driver.
-- Expected: every column whose name ends in "_violations" is 0.

-- (1) Coverage
SELECT COUNT(*) AS generator_trips, SUM(t.is_extra_trip) AS extra_trips, SUM(t.status = 'CANCELLED') AS cancelled,
       SUM(t.status NOT IN ('COMPLETED', 'CANCELLED')) AS status_violations,
       MIN(DATE(t.departure_time)) AS first_day, MAX(DATE(t.departure_time)) AS last_day,
       COUNT(DISTINCT DATE(t.departure_time)) AS days,
       DATEDIFF(MAX(DATE(t.departure_time)), MIN(DATE(t.departure_time))) + 1 - COUNT(DISTINCT DATE(t.departure_time))
         AS missing_day_violations
FROM trips t
WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50);

SELECT COUNT(*) AS generator_shaped_trips_elsewhere_violations
FROM trips t
WHERE t.is_deleted = 0 AND t.route_id NOT IN (2, 5, 10, 11, 12, 13)
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  AND t.status IN ('COMPLETED', 'CANCELLED');

-- (2) Business rules on every generator trip
SELECT
  -- Driver.isLicenseValid(date): valid only when the expiry date is AFTER the departure date
  (SELECT COUNT(*) FROM trips t JOIN drivers d ON d.user_id = t.driver_id
     WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
       AND (d.license_expiry_date IS NULL OR d.license_expiry_date <= DATE(t.departure_time)))
    AS expired_licence_violations,
  (SELECT COUNT(*) FROM trips t JOIN buses b ON b.id = t.bus_id JOIN bus_types bt ON bt.id = b.bus_type_id
     WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
       AND (t.tickets_sold > t.total_seats OR t.total_seats <> bt.capacity)) AS seat_violations,
  (SELECT COUNT(*) FROM trips t LEFT JOIN trips p ON p.id = t.original_trip_id
     WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13) AND t.is_extra_trip = 1
       AND HOUR(t.departure_time) IN (6, 12, 18)
       AND (p.id IS NULL OR p.route_id <> t.route_id OR DATE(p.departure_time) <> DATE(t.departure_time)
            OR HOUR(p.departure_time) <> HOUR(t.departure_time) OR p.is_extra_trip <> 0))
    AS extra_without_parent_violations,
  (SELECT COUNT(*) FROM (
     SELECT 1 FROM trips t
     WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
     GROUP BY t.route_id, DATE(t.departure_time), HOUR(t.departure_time)
     HAVING SUM(t.is_extra_trip = 0) <> 1 OR SUM(t.is_extra_trip) > 3
         OR COUNT(DISTINCT MINUTE(t.departure_time)) <> COUNT(*)) x) AS slot_shape_violations,
  (SELECT COUNT(*) FROM (
     SELECT 1 FROM trips t
     WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
     GROUP BY t.route_id, DATE(t.departure_time), HOUR(t.departure_time)
     HAVING SUM(t.status = 'CANCELLED') > 0 AND (SUM(t.status = 'COMPLETED') > 0 OR SUM(t.is_extra_trip) > 0)) x)
    AS extra_on_cancelled_slot_violations;

-- (3) Loops (QĐ-21 (c), QĐ-23): per bus and per driver, every running generator trip leaves from the
-- station the previous one reached — the first one from the home station — after a rest of at least
-- 1 h (bus) / 30 min (driver). Overlapping trips show up as rest violations.
WITH ends AS (
  SELECT rs.route_id,
         SUBSTRING_INDEX(GROUP_CONCAT(rs.station_id ORDER BY rs.stop_order), ',', 1) + 0 AS from_station,
         SUBSTRING_INDEX(GROUP_CONCAT(rs.station_id ORDER BY rs.stop_order DESC), ',', 1) + 0 AS to_station
  FROM route_stations rs GROUP BY rs.route_id),
runs AS (
  SELECT t.id, t.bus_id, t.driver_id, t.departure_time, t.arrival_time_expected, e.from_station, e.to_station
  FROM trips t JOIN ends e ON e.route_id = t.route_id
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED' AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)),
bus_runs AS (
  SELECT r.*, b.home_station_id AS home,
         LAG(r.to_station) OVER (PARTITION BY r.bus_id ORDER BY r.departure_time, r.id) AS prev_to,
         LAG(r.arrival_time_expected) OVER (PARTITION BY r.bus_id ORDER BY r.departure_time, r.id) AS prev_arrival
  FROM runs r JOIN buses b ON b.id = r.bus_id),
driver_runs AS (
  SELECT r.*, d.home_station_id AS home,
         LAG(r.to_station) OVER (PARTITION BY r.driver_id ORDER BY r.departure_time, r.id) AS prev_to,
         LAG(r.arrival_time_expected) OVER (PARTITION BY r.driver_id ORDER BY r.departure_time, r.id) AS prev_arrival
  FROM runs r JOIN drivers d ON d.user_id = r.driver_id)
SELECT
  (SELECT COUNT(*) FROM runs) AS running_trips,
  (SELECT COUNT(*) FROM bus_runs WHERE prev_to IS NULL AND from_station <> home) AS bus_first_trip_not_from_home_violations,
  (SELECT COUNT(*) FROM bus_runs WHERE prev_to IS NOT NULL AND from_station <> prev_to) AS bus_location_violations,
  (SELECT COUNT(*) FROM bus_runs WHERE prev_arrival + INTERVAL 60 MINUTE > departure_time) AS bus_rest_violations,
  (SELECT COUNT(*) FROM driver_runs WHERE prev_to IS NULL AND from_station <> home) AS driver_first_trip_not_from_home_violations,
  (SELECT COUNT(*) FROM driver_runs WHERE prev_to IS NOT NULL AND from_station <> prev_to) AS driver_location_violations,
  (SELECT COUNT(*) FROM driver_runs WHERE prev_arrival + INTERVAL 30 MINUTE > departure_time) AS driver_rest_violations;

-- (4) Driving hours per CALENDAR day (QĐ-21 (c)): an overnight trip is split at 00:00 and each part
-- counts on its own day (routes are ≤ 8 h, so a trip touches at most two days).
WITH parts AS (
  SELECT t.driver_id, DATE(t.departure_time) AS d,
         TIMESTAMPDIFF(MINUTE, t.departure_time,
                       LEAST(t.arrival_time_expected, DATE(t.departure_time) + INTERVAL 1 DAY)) AS minutes
  FROM trips t
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED' AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  UNION ALL
  SELECT t.driver_id, DATE(t.departure_time) + INTERVAL 1 DAY,
         TIMESTAMPDIFF(MINUTE, DATE(t.departure_time) + INTERVAL 1 DAY, t.arrival_time_expected)
  FROM trips t
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED' AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
    AND t.arrival_time_expected > DATE(t.departure_time) + INTERVAL 1 DAY),
per_day AS (SELECT driver_id, d, SUM(minutes) AS minutes, COUNT(*) AS trips FROM parts GROUP BY driver_id, d)
SELECT COUNT(*) AS driver_days, MAX(minutes) AS most_minutes_in_a_day,
       SUM(trips >= 2) AS driver_days_with_two_or_more_trips,
       SUM(minutes > 480) AS over_eight_hours_violations
FROM per_day;

-- (5) Defect #33: a kept trip holds its bus and everyone on it (driver, assistant, co-drivers) on every
-- calendar day it touches; no running generator trip may use them on those days. Cancelled kept trips hold
-- nothing.
WITH gen AS (
  SELECT t.id, DATE(t.departure_time) AS first_day,
         DATE(t.arrival_time_expected - INTERVAL 1 MICROSECOND) AS last_day, t.bus_id, t.driver_id
  FROM trips t
  WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)),
kept AS (
  SELECT t.id, t.bus_id, DATE(t.departure_time) AS first_day,
         DATE(COALESCE(t.arrival_time_expected, t.departure_time + INTERVAL r.estimated_duration MINUTE)
              - INTERVAL 1 MICROSECOND) AS last_day
  FROM trips t JOIN routes r ON r.id = t.route_id
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED'
    AND NOT (t.route_id IN (2, 5, 10, 11, 12, 13)
             AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50))),
kept_staff AS (
  SELECT k.id, t.driver_id AS user_id FROM kept k JOIN trips t ON t.id = k.id WHERE t.driver_id IS NOT NULL
  UNION SELECT k.id, t.assistant_id FROM kept k JOIN trips t ON t.id = k.id WHERE t.assistant_id IS NOT NULL
  UNION SELECT k.id, cd.user_id FROM kept k JOIN trip_co_drivers cd ON cd.trip_id = k.id)
SELECT
  (SELECT COUNT(*) FROM kept) AS kept_trips_holding_resources,
  (SELECT COUNT(*) FROM kept k JOIN gen g
     ON g.bus_id = k.bus_id AND g.first_day <= GREATEST(k.first_day, k.last_day)
        AND GREATEST(g.first_day, g.last_day) >= k.first_day) AS kept_bus_reused_violations,
  (SELECT COUNT(*) FROM kept k JOIN kept_staff s ON s.id = k.id JOIN gen g
     ON g.driver_id = s.user_id AND g.first_day <= GREATEST(k.first_day, k.last_day)
        AND GREATEST(g.first_day, g.last_day) >= k.first_day) AS kept_staff_reused_violations;

-- (6) Odometer. After PART B on the real DB the totals are 18,385 km and 18,355 km since maintenance.
-- Expected now: total_odometer = 18,385 + completed_generator_km, total_km_since_maintenance still 18,355.
SELECT ROUND(SUM(odometer)) AS total_odometer,
       ROUND(SUM(odometer - last_maintenance_odometer)) AS total_km_since_maintenance,
       (SELECT ROUND(SUM(r.distance_km)) FROM trips t JOIN routes r ON r.id = t.route_id
         WHERE t.is_deleted = 0 AND t.status = 'COMPLETED' AND t.route_id IN (2, 5, 10, 11, 12, 13)
           AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50))
         AS completed_generator_km
FROM buses;

-- (7) Measurements (no expected value; recorded in route_pairs_progress.md): per route, slots without any
-- trip, sold-out share of open slots, extras and how many of them sold 0 tickets (the "về ngay" legs).
WITH days AS (
  SELECT DISTINCT DATE(departure_time) AS d FROM trips
  WHERE is_deleted = 0 AND route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(departure_time) IN (6, 12, 18) AND MINUTE(departure_time) IN (0, 30, 40, 50)),
slots AS (
  SELECT r.id AS route_id, days.d, h.h
  FROM routes r CROSS JOIN days CROSS JOIN (SELECT 6 AS h UNION ALL SELECT 12 UNION ALL SELECT 18) h
  WHERE r.id IN (2, 5, 10, 11, 12, 13)),
served AS (
  SELECT t.route_id, DATE(t.departure_time) AS d, HOUR(t.departure_time) AS h,
         MAX(t.status = 'CANCELLED') AS cancelled, SUM(t.tickets_sold) AS sold, SUM(t.total_seats) AS seats,
         SUM(t.is_extra_trip) AS extras, SUM(t.is_extra_trip = 1 AND t.tickets_sold = 0) AS empty_extras
  FROM trips t
  WHERE t.is_deleted = 0 AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
  GROUP BY t.route_id, DATE(t.departure_time), HOUR(t.departure_time))
SELECT s.route_id, COUNT(*) AS slots, SUM(v.route_id IS NULL) AS unserved_slots,
       SUM(v.cancelled = 1) AS cancelled_slots,
       ROUND(100 * AVG(CASE WHEN v.cancelled = 0 THEN v.sold = v.seats END), 1) AS sold_out_pct,
       SUM(v.extras) AS extras, SUM(v.empty_extras) AS empty_extras
FROM slots s LEFT JOIN served v ON v.route_id = s.route_id AND v.d = s.d AND v.h = s.h
GROUP BY s.route_id WITH ROLLUP;

-- (8) Where every bus ends the history (its last running generator trip); the far ends should hold only
-- the few buses of the regular loops, not a pile left behind by cancellations.
WITH ends AS (
  SELECT rs.route_id,
         SUBSTRING_INDEX(GROUP_CONCAT(rs.station_id ORDER BY rs.stop_order DESC), ',', 1) + 0 AS to_station
  FROM route_stations rs GROUP BY rs.route_id),
last_run AS (
  SELECT t.bus_id, e.to_station,
         ROW_NUMBER() OVER (PARTITION BY t.bus_id ORDER BY t.departure_time DESC, t.id DESC) AS n
  FROM trips t JOIN ends e ON e.route_id = t.route_id
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED' AND t.route_id IN (2, 5, 10, 11, 12, 13)
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50))
SELECT s.station_name, COUNT(*) AS buses
FROM last_run l JOIN stations s ON s.id = l.to_station
WHERE l.n = 1
GROUP BY s.station_name ORDER BY buses DESC;
