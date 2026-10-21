-- Read-only research queries behind docs/development/forecast_discussion.md (2026-10-14..16).
-- All are SELECT only. Run in MySQL Workbench, or:
--   "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p busmanagement --default-character-set=utf8mb4 < queries.sql
-- The numbers recorded in forecast_discussion.md (appendix) were produced on the database as of
-- 2026-10-14: history 2026-04-27 .. 2026-09-20, 2,065 COMPLETED trips.
-- "Backfill rows" below = the shape HistoricalDataBackfill generates: slots 06/12/18 at :00, not extra trips.

-- Q0. Export the demand history used by every Python script in this folder (same 4 columns as
--     TripRepository.findDemandHistoryByStatus). Run with -B -N and redirect to a .tsv file:
--     mysql.exe -uroot -p busmanagement -B -N -e "<this query>" > hist.tsv
SELECT route_id, DATE_FORMAT(departure_time, '%Y-%m-%d %H'), tickets_sold, total_seats
FROM trips
WHERE status = 'COMPLETED' AND is_deleted = 0 AND total_seats > 0
ORDER BY departure_time;

-- Q1. Which backfill run created which departure range (shows the top-up runs).
SELECT DATE(created_at) AS run_date, MIN(DATE(departure_time)) AS dep_from,
       MAX(DATE(departure_time)) AS dep_to, COUNT(*) AS n
FROM trips
WHERE is_deleted = 0 AND HOUR(departure_time) IN (6, 12, 18) AND MINUTE(departure_time) = 0 AND is_extra_trip = 0
GROUP BY DATE(created_at) ORDER BY run_date;

SELECT MIN(departure_time), MAX(departure_time), COUNT(*) FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0;

-- Q2. Weekly mean occupancy (shows the trend reset / sawtooth caused by each top-up run).
SELECT DATE(DATE_SUB(departure_time, INTERVAL WEEKDAY(departure_time) DAY)) AS week_mon,
       COUNT(*) AS n, ROUND(AVG(tickets_sold / total_seats), 3) AS occ,
       GROUP_CONCAT(DISTINCT DATE(created_at) ORDER BY DATE(created_at)) AS created_by_run
FROM trips
WHERE status = 'COMPLETED' AND is_deleted = 0 AND total_seats > 0
  AND HOUR(departure_time) IN (6, 12, 18) AND MINUTE(departure_time) = 0 AND is_extra_trip = 0
GROUP BY week_mon ORDER BY week_mon;

-- Q3. Routes with their first and last station (routes are directional: 1 and 4 are a pair).
SELECT r.id, r.distance_km AS km, r.estimated_duration AS mins, r.suitable_bus_type_id,
  (SELECT s.station_name FROM route_stations rs JOIN stations s ON s.id = rs.station_id
    WHERE rs.route_id = r.id ORDER BY rs.stop_order LIMIT 1) AS di,
  (SELECT s.station_name FROM route_stations rs JOIN stations s ON s.id = rs.station_id
    WHERE rs.route_id = r.id ORDER BY rs.stop_order DESC LIMIT 1) AS den
FROM routes r ORDER BY r.id;

-- Q4. Trip counts by status.
SELECT status, COUNT(*) FROM trips WHERE is_deleted = 0 GROUP BY status;

-- Q5. Data provenance: backfill-generated vs everything else (run dates are the backfill runs of Q1).
SELECT CASE WHEN DATE(created_at) IN ('2026-07-20','2026-07-22','2026-07-27','2026-08-04','2026-09-11','2026-09-21')
             AND HOUR(departure_time) IN (6, 12, 18) AND MINUTE(departure_time) = 0 AND is_extra_trip = 0
            THEN 'backfill' ELSE 'khac' END AS src,
       status, COUNT(*)
FROM trips WHERE is_deleted = 0 GROUP BY src, status ORDER BY src, status;

-- Q6. Censoring: completed trips sold out (demand >= capacity, true demand unknown), and extra trips.
SELECT COUNT(*) AS completed, SUM(tickets_sold >= total_seats) AS full_100,
       ROUND(100 * SUM(tickets_sold >= total_seats) / COUNT(*), 1) AS pct_full, SUM(is_extra_trip) AS extra
FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0 AND total_seats > 0;

-- Q7. Censoring by series (it concentrates in the hot series).
SELECT route_id, HOUR(departure_time) AS h, COUNT(*) AS n, SUM(tickets_sold >= total_seats) AS full_100
FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0 AND total_seats > 0 AND HOUR(departure_time) IN (6, 18)
GROUP BY route_id, h HAVING full_100 > 0 ORDER BY full_100 DESC;

-- Q8. Price variation (constant per route => price effect cannot be learned).
SELECT COUNT(DISTINCT price) AS n_price, COUNT(DISTINCT route_id) AS n_route
FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0;
SELECT route_id, COUNT(DISTINCT price) AS n_price_per_route
FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0 GROUP BY route_id;
SELECT route_id, price, COUNT(*) FROM trips
WHERE status = 'COMPLETED' AND is_deleted = 0 AND route_id = 1 GROUP BY price;

-- Q9. Holidays in the history vs the same weekday within +/-14 days (30/4-1/5 and 2/9 show no effect).
WITH d AS (
  SELECT DATE(departure_time) AS dt, AVG(tickets_sold / total_seats) AS occ, COUNT(*) AS n
  FROM trips WHERE status = 'COMPLETED' AND is_deleted = 0 AND total_seats > 0
    AND HOUR(departure_time) IN (6, 12, 18) AND MINUTE(departure_time) = 0
  GROUP BY DATE(departure_time))
SELECT h.dt AS ngay_le, DAYNAME(h.dt) AS thu, ROUND(h.occ, 3) AS occ_ngay_le, h.n,
  ROUND((SELECT AVG(x.occ) FROM d x WHERE DAYOFWEEK(x.dt) = DAYOFWEEK(h.dt) AND x.dt <> h.dt
           AND ABS(DATEDIFF(x.dt, h.dt)) <= 14), 3) AS occ_cung_thu_lan_can,
  ROUND(h.occ / (SELECT AVG(x.occ) FROM d x WHERE DAYOFWEEK(x.dt) = DAYOFWEEK(h.dt) AND x.dt <> h.dt
           AND ABS(DATEDIFF(x.dt, h.dt)) <= 14), 3) AS ti_so
FROM d h
WHERE h.dt IN ('2026-04-29','2026-04-30','2026-05-01','2026-05-02','2026-05-03','2026-08-31','2026-09-01','2026-09-02')
ORDER BY h.dt;
