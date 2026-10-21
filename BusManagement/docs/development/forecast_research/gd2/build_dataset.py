# GĐ2 re-check (decision QĐ-13 in docs/development/forecast_decisions.md), step 1 of 2: turn the data the
# NEW generator wrote into the step 0 "synthetic CSV" format, so step0/harness.py, report.py and compare.py
# score it unchanged. Research only — nothing here touches the app or the real database.
#
# Inputs, exported from the clone busmanagement_gd2 after the GĐ3 rehearsal (kept OUTSIDE the repo):
#   app_sales.tsv  — COMPLETED trips summed per slot (route, date, hour): what the app records. Sales are
#                    capped by the seats actually run, exactly as the forecaster will see them.
#   app_routes.tsv — route id, holiday_flow, standard seats (the route's suitable bus type)
#   truth.csv      — the generator's -Dbackfill.truth-csv output: TRUE demand per slot, never in the DB
# Output: dataset.csv with date, route, hour, series, group, cap, trips, seats, demand, sales, storm, mu
#   sales = NaN where no COMPLETED trip ran (storm closures, random cancellations): not observed
#   storm = 1 on closure days — dropped from training and scored apart, as in step 0
#
# It also checks the DB against the generator: for every slot that ran, the DB's summed tickets and seats
# must equal what the generator planned. Any mismatch is printed and the script stops.
#
# Usage: python -I build_dataset.py <directory holding the three exports>
import sys
from pathlib import Path

import numpy as np
import pandas as pd

GROUP = {"LEAVING_HUB": 0, "TOWARD_HUB": 1, "": 2}   # same pooling groups as step 0: out / in / cross


def main():
    base = Path(sys.argv[1])
    sales = pd.read_csv(base / "app_sales.tsv", sep="\t", parse_dates=["date"])
    routes = pd.read_csv(base / "app_routes.tsv", sep="\t", keep_default_na=False)
    truth = pd.read_csv(base / "truth.csv", parse_dates=["date"])

    df = truth.merge(sales.rename(columns={"route_id": "route_id", "seats": "seats_db", "trips": "trips_db"}),
                     on=["route_id", "date", "hour"], how="left")
    ran = df["cancelled"] == 0
    bad = df[ran & ((df["sold_y"] != df["sold_x"]) | (df["seats_db"] != df["seats"]) | (df["trips_db"] != df["trips"]))]
    extra = df[~ran & df["sold_y"].notna()]
    print(f"slots={len(df)} ran={int(ran.sum())} db_rows={len(sales)} mismatches={len(bad)} "
          f"completed_on_cancelled_slots={len(extra)}")
    if len(bad) or len(extra) or len(sales) != int(ran.sum()):
        print(bad.head(10).to_string())
        sys.exit("DB and generator disagree — stop.")

    flow = dict(zip(routes["route_id"], routes["holiday_flow"]))
    out = pd.DataFrame({
        "date": df["date"].dt.date,
        "route": df["route_id"],
        "hour": df["hour"],
        "series": [f"r{r}_{h:02d}" for r, h in zip(df["route_id"], df["hour"])],
        "group": [GROUP[flow[r]] for r in df["route_id"]],
        "cap": df["standard_seats"],
        "trips": df["trips"],
        "seats": df["seats"],
        "demand": df["demand"],
        "sales": np.where(ran, df["sold_y"], np.nan),
        "storm": df["closed"],
        "mu": df["mean"],
    })
    out.to_csv(base / "dataset.csv", index=False)
    observed = out.dropna(subset=["sales"])
    print(f"wrote {base / 'dataset.csv'} rows={len(out)} sold_out={(observed['sales'] >= observed['seats']).mean():.1%} "
          f"lost={1 - observed['sales'].sum() / observed['demand'].sum():.1%}")


if __name__ == "__main__":
    main()
