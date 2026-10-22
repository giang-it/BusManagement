-- GĐ4 of the Demand Forecast redesign (decision QĐ-15): READ-ONLY export of the slot history that the Java
-- model is cross-checked on. Run once on the real DB right after GĐ3 (2026-10-22); the output is frozen into
-- src/test/resources/forecast/ so the test is repeatable. Re-running it on a later DB gives a different file.
--
-- Usage (choose the schema on the command line, never edit it in here):
--   mysql.exe -uroot -p busmanagement -B -N < export_slot_history.sql
-- The first result set is the slot history, the second the routes; the caller splits them into
-- slot_history.csv and routes.csv (see reference.py).
--
-- A slot is (route, departure hour, date) on a route of at most 8 hours, at 06/12/18 h — the 15 series the
-- generator writes. Sales and seats are summed over the slot's COMPLETED trips, extra trips included (they
-- leave 30/40/50 minutes after the regular one, so they share its hour). A slot with no COMPLETED trip
-- (storm closure, cancellation) has no row: it was not observed. This is the reading of open item M-3 used
-- in GĐ2; GĐ5 settles it for the app.

SELECT DATE(t.departure_time), t.route_id, HOUR(t.departure_time), SUM(t.tickets_sold), SUM(t.total_seats)
FROM trips t JOIN routes r ON r.id = t.route_id
WHERE t.is_deleted = 0 AND t.status = 'COMPLETED'
  AND r.estimated_duration > 0 AND r.estimated_duration <= 480
  AND HOUR(t.departure_time) IN (6, 12, 18)
  AND t.departure_time >= '2023-01-01' AND t.departure_time < '2026-10-22'
GROUP BY DATE(t.departure_time), t.route_id, HOUR(t.departure_time)
ORDER BY 1, 2, 3;

SELECT r.id, IFNULL(r.holiday_flow, '')
FROM routes r
WHERE r.estimated_duration > 0 AND r.estimated_duration <= 480
ORDER BY r.id;
