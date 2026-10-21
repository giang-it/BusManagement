# forecast_research

Read-only research behind [`../forecast_discussion.md`](../forecast_discussion.md) (2026-10-14 → 20).
Nothing here is part of the application; nothing here writes to the database.

| File | What it is |
|---|---|
| `hist_snapshot_2026-10-14.tsv` | Snapshot of the `COMPLETED` trip history (route_id, `YYYY-MM-DD HH`, tickets_sold, total_seats), 2,065 rows, 2026-04-27 → 2026-09-20 |
| `forecast_replica.py` | Exact Python replica of `ForecastService.buildForecast()` and its backtest |
| `compare_methods.py` | Rolling-origin backtest of 5 methods (current LR, MA7, seasonal naive ×2, Holt-Winters damped) |
| `per_origin.py` | Per-origin breakdown of `compare_methods.py` |
| `queries.sql` | Every read-only SQL query used, including the export (Q0) |
| `step0/`, `step0_data/` | Step 0 (2026-10-20): algorithm selection on real Hong Kong border data and on a new censored synthetic generator; needs numpy/pandas/scipy/statsmodels/prophet/lightgbm. See `step0/README.md`; results in `../forecast_discussion.md` §12 |
| `gd3_check_old_history.sql`, `gd3_delete_old_history.sql` | GĐ3 (decision QĐ-11): read-only checks, then the transaction that deletes the OLD generator's 2,206 trips and takes their km off both odometer marks. Rehearsed on the clone `busmanagement_gd2` in GĐ2 (2026-10-21). **The second one writes**: back up first, and on the real DB only after the owner's final confirmation |
| `gd2/` | GĐ2 re-check (QĐ-13) on the data the NEW generator wrote: `build_dataset.py` (DB export + `-Dbackfill.truth-csv` → step 0 CSV format, cross-checks DB vs generator), `run_gd2.py` (one factor at a time around G1' + EM, K=0), `report_gd2.py`, `hk_direction.py` (direction attribute on real Hong Kong data), `hk_rules.py` (rules a/b/c and λ on real Hong Kong data), `split_by_rule_a.py` (2026-10-22: splits those runs by whether rule (a) is active, for M-15; `results/rule_ab_by_regime.md`, §13.7); result tables in `gd2/results/`. Needs numpy/pandas/scipy (the step 0 venv). Results in `../forecast_discussion.md` §13 |
| `holiday_calendar_check.py` | GĐ1 (2026-10-21): independent GMT+7 re-implementation of Ho Ngoc Duc's Vietnamese lunar-calendar algorithm, used to cross-check the Tết / Giỗ Tổ table in `HolidayCalendar.java` (2022–2033). Plain Python, no packages: `python -I holiday_calendar_check.py` |
| `2026-10-20-chưa xong.txt` | The owner's own terminal copy of the 2026-10-20 morning session (summarised in `../forecast_discussion.md` §11) |

Run from this folder (plain Python 3, no numpy):

```
python forecast_replica.py hist_snapshot_2026-10-14.tsv 2026-09-21   # 15 series, MAPE 4.29% vs 7.43%, 17 signals
python compare_methods.py  hist_snapshot_2026-10-14.tsv              # 4.94 / 8.11 / 5.68 / 4.68 / 4.56 %
python per_origin.py       hist_snapshot_2026-10-14.tsv
```

To use fresh data, export with Q0 into a **new** `.tsv` and pass that file; keep the snapshot unchanged
so the numbers in `forecast_discussion.md` stay reproducible.
