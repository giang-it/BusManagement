-- GĐ3 of the Demand Forecast redesign, PART C — READ-ONLY checks AFTER the new generator has run
-- (decisions QĐ-10, QĐ-11, QĐ-14 in docs/development/forecast_decisions.md).
-- Rehearsed on the clone busmanagement_gd2 (2026-10-22), then run on the real DB.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p <schema> --default-character-set=utf8mb4 -t < gd3_check_new_history.sql
--
-- "Generator trip": a route the generator uses (≤ 8 h), departure at 06/12/18 h, minute 0 (regular trip)
-- or 30/40/50 (extra trips) — the same shape HistoricalDataBackfill uses to recognise its own days.
-- Every other non-deleted trip is a "kept" trip (made by hand in the app).
-- Expected: every column whose name ends in "_violations" is 0.

-- (1) Coverage
SELECT COUNT(*) AS generator_trips, SUM(t.is_extra_trip) AS extra_trips, SUM(t.status = 'CANCELLED') AS cancelled,
       SUM(t.status NOT IN ('COMPLETED', 'CANCELLED')) AS status_violations,
       MIN(DATE(t.departure_time)) AS first_day, MAX(DATE(t.departure_time)) AS last_day,
       COUNT(DISTINCT DATE(t.departure_time)) AS days,
       DATEDIFF(MAX(DATE(t.departure_time)), MIN(DATE(t.departure_time))) + 1 - COUNT(DISTINCT DATE(t.departure_time))
         AS missing_day_violations
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE t.is_deleted = 0 AND r.estimated_duration <= 480
  AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50);

SELECT COUNT(*) AS day_without_every_regular_slot_violations FROM (
  SELECT DATE(t.departure_time) AS d, COUNT(*) AS regular
  FROM trips t JOIN routes r ON r.id = t.route_id
  WHERE t.is_deleted = 0 AND r.estimated_duration <= 480 AND t.is_extra_trip = 0
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) = 0
  GROUP BY DATE(t.departure_time)) x
WHERE x.regular <> (SELECT 3 * COUNT(*) FROM routes WHERE estimated_duration > 0 AND estimated_duration <= 480);

-- (2) Business rules among generator trips
SELECT
  (SELECT COUNT(*) FROM (SELECT 1 FROM trips t JOIN routes r ON r.id = t.route_id
     WHERE t.is_deleted = 0 AND r.estimated_duration <= 480
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
     GROUP BY DATE(t.departure_time), t.bus_id HAVING COUNT(*) > 1) x) AS bus_twice_a_day_violations,
  (SELECT COUNT(*) FROM (SELECT 1 FROM trips t JOIN routes r ON r.id = t.route_id
     WHERE t.is_deleted = 0 AND r.estimated_duration <= 480
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
     GROUP BY DATE(t.departure_time), t.driver_id HAVING COUNT(*) > 1) x) AS driver_twice_a_day_violations,
  -- Driver.isLicenseValid(date): valid only when the expiry date is AFTER the departure date
  (SELECT COUNT(*) FROM trips t JOIN routes r ON r.id = t.route_id JOIN drivers d ON d.user_id = t.driver_id
     WHERE t.is_deleted = 0 AND r.estimated_duration <= 480
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
       AND (d.license_expiry_date IS NULL OR d.license_expiry_date <= DATE(t.departure_time)))
    AS expired_licence_violations,
  (SELECT COUNT(*) FROM trips t JOIN routes r ON r.id = t.route_id
     JOIN buses b ON b.id = t.bus_id JOIN bus_types bt ON bt.id = b.bus_type_id
     WHERE t.is_deleted = 0 AND r.estimated_duration <= 480
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
       AND (t.tickets_sold > t.total_seats OR t.total_seats <> bt.capacity)) AS seat_violations,
  (SELECT COUNT(*) FROM trips t JOIN routes r ON r.id = t.route_id LEFT JOIN trips p ON p.id = t.original_trip_id
     WHERE t.is_deleted = 0 AND r.estimated_duration <= 480 AND t.is_extra_trip = 1
       AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (30, 40, 50)
       AND (p.id IS NULL OR p.route_id <> t.route_id OR DATE(p.departure_time) <> DATE(t.departure_time)
            OR HOUR(p.departure_time) <> HOUR(t.departure_time) OR p.is_extra_trip <> 0))
    AS extra_without_parent_violations;

-- (3) Defect #33: a kept trip holds its bus and everyone on it (driver, assistant, co-drivers) on every
-- calendar day it touches; no generator trip may use them on those days. Cancelled kept trips hold nothing.
WITH gen AS (
  SELECT t.id, DATE(t.departure_time) AS d, t.bus_id, t.driver_id
  FROM trips t JOIN routes r ON r.id = t.route_id
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED' AND r.estimated_duration <= 480
    AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)),
kept AS (
  SELECT t.id, t.bus_id, DATE(t.departure_time) AS first_day,
         DATE(COALESCE(t.arrival_time_expected, t.departure_time + INTERVAL r.estimated_duration MINUTE)
              - INTERVAL 1 MICROSECOND) AS last_day
  FROM trips t JOIN routes r ON r.id = t.route_id
  WHERE t.is_deleted = 0 AND t.status <> 'CANCELLED'
    AND NOT (r.estimated_duration <= 480
             AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50))),
kept_staff AS (
  SELECT k.id, t.driver_id AS user_id FROM kept k JOIN trips t ON t.id = k.id WHERE t.driver_id IS NOT NULL
  UNION SELECT k.id, t.assistant_id FROM kept k JOIN trips t ON t.id = k.id WHERE t.assistant_id IS NOT NULL
  UNION SELECT k.id, cd.user_id FROM kept k JOIN trip_co_drivers cd ON cd.trip_id = k.id)
SELECT
  (SELECT COUNT(*) FROM kept) AS kept_trips_holding_resources,
  (SELECT COUNT(*) FROM kept k JOIN gen g ON g.d BETWEEN k.first_day AND GREATEST(k.first_day, k.last_day)
     WHERE g.bus_id = k.bus_id) AS kept_bus_reused_violations,
  (SELECT COUNT(*) FROM kept k JOIN kept_staff s ON s.id = k.id
     JOIN gen g ON g.d BETWEEN k.first_day AND GREATEST(k.first_day, k.last_day) AND g.driver_id = s.user_id)
    AS kept_staff_reused_violations;

-- (4) Odometer. On the real DB (data of 2026-10-22): after PART B the totals were 18,385 km and
-- 18,355 km since maintenance. Expected now: total_odometer = 18,385 + completed_generator_km, and
-- total_km_since_maintenance still 18,355 (the generator adds the same km to both marks).
SELECT ROUND(SUM(odometer)) AS total_odometer,
       ROUND(SUM(odometer - last_maintenance_odometer)) AS total_km_since_maintenance,
       (SELECT ROUND(SUM(r.distance_km)) FROM trips t JOIN routes r ON r.id = t.route_id
         WHERE t.is_deleted = 0 AND t.status = 'COMPLETED' AND r.estimated_duration <= 480
           AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50))
         AS completed_generator_km
FROM buses;

-- (5) Sold-out share of open slots (route × hour × day, regular and extra trips together); about 21 %
SELECT COUNT(*) AS open_slots, SUM(sold = seats) AS sold_out_slots, ROUND(100 * AVG(sold = seats), 1) AS sold_out_pct
FROM (SELECT t.route_id, DATE(t.departure_time) AS d, HOUR(t.departure_time) AS h,
             SUM(t.tickets_sold) AS sold, SUM(t.total_seats) AS seats
      FROM trips t JOIN routes r ON r.id = t.route_id
      WHERE t.is_deleted = 0 AND t.status = 'COMPLETED' AND r.estimated_duration <= 480
        AND HOUR(t.departure_time) IN (6, 12, 18) AND MINUTE(t.departure_time) IN (0, 30, 40, 50)
      GROUP BY t.route_id, DATE(t.departure_time), HOUR(t.departure_time)) s;
