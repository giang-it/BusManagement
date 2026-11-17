# route_pairs_research — scripts for replacing the history (route pairs CT3/CT4)

Scripts for decision QĐ-21 in `docs/development/route_pairs_decisions.md`: delete the GĐ3 generator's
history and write the new one — three route pairs around Bến xe Miền Đông, buses and drivers running in
loops. Same procedure as GĐ3 (QĐ-14): back up, rehearse on a clone, owner's final confirmation, then the
real DB (CT4). Plan and results: `docs/development/route_pairs_progress.md`, sections CT3 and CT4.

| File | What it does |
|---|---|
| `ct3_check_old_history.sql` | PART A, read-only. Counts the GĐ3 history (every trip written on 2026-10-22), checks nothing else points at it, shows the km PART B will take off each bus. Expected numbers are in its header. |
| `ct3_delete_old_history.sql` | PART B, **destructive**. Deletes the GĐ3 history in one transaction — extra trips first, because they point at their regular trip — and subtracts its km from **both** `odometer` and `last_maintenance_odometer`, so km-since-maintenance does not move. |
| `ct3_check_new_history.sql` | PART C, read-only, after the new generator has run. Every column ending in `_violations` must be 0: coverage, licences, seats, slot shape, the loops (location, rest ≥ 1 h for a bus and ≥ 30 min for a driver, first trip from the home station), driving ≤ 8 h per calendar day, defect #33, odometer. Then the measurements: unserved slots, sold-out share, extra trips and how many sold 0 tickets, where each bus ends the history. |

## Order

```bash
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
# 0. backup first (mysqldump --single-transaction busmanagement > ...)
"$MYSQL" -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_check_old_history.sql   # A
"$MYSQL" -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_delete_old_history.sql  # B
# the generator — from the Maven folder; profile backfill ONLY, never demo
./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill \
  "-Dspring-boot.run.jvmArguments=-Dbackfill.truth-csv=<path>/truth.csv"
"$MYSQL" -uroot -p <schema> --default-character-set=utf8mb4 -t < ct3_check_new_history.sql   # C
```

On a clone, point the generator at it with
`-Dspring.datasource.url=jdbc:mysql://localhost:3306/<clone>?sessionVariables=foreign_key_checks=0` inside
`jvmArguments`, and a second port (`-Dserver.port=8098`). Check `information_schema.processlist` while it runs:
every connection must be on the clone. The generator writes on startup, before you can look, so check the
URL string before launching, and compare the real DB's snapshot before and after.

The route list is `backfill.route-ids` (default `2,12,5,13,10,11`). PART C has the same six ids written in
its `IN (...)` lists; change both together.

## Rehearsal 2026-11-17 (clone `busmanagement_ct3`, from the real DB of that morning)

- Clone identical to the real DB after the restore (row counts and MD5 of `trips`, `buses`, `drivers`, `routes`).
- PART A: as expected — 21,192 trips (342 extra), all generator-shaped, nothing pointing at them,
  3,252,020 km to subtract.
- PART B: 21,192 deleted, 19 trips left (the owner's), odometer total 3,270,405 → 18,385,
  km since maintenance 18,355 before and after. First attempt stopped on MySQL error 1137 (a temporary
  table read twice in one statement) before any write; fixed in the script.
- Generator: 1,416 days (01/01/2023 → 16/11/2026), 26,670 trips, 1,973 extra (1,429 with 0 tickets),
  1,642 cancelled, 791 slots without a bus, 14.5 % of open slots sold out, +4,839,960 km.
- PART C: every `_violations` column 0. 5,426 driver-days with two or more trips (the loops are real);
  the most anyone drove in a day: 440 minutes. At the end: 16 buses at Miền Đông, 3 at Đà Lạt, 1 at Cần Thơ.
- Real DB snapshot identical before and after.
