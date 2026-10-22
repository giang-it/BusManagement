# GĐ5 of the Demand Forecast redesign (decision QĐ-16, G5-7): the Python REFERENCE for the forecast page's
# self-check, which ForecastService.runBacktest must reproduce. Research only — nothing here touches the app or
# the database.
#
# Same frozen history as GĐ4 (src/test/resources/forecast/slot_history.csv + routes.csv, the real DB right after
# GĐ3) and gd2/run_gd2.py's slot_glm with the final configuration, unchanged. It scores, on what the app can see:
#   recent: 8 weekly origins ending 7 days before the last day (2026-08-26 ... 2026-10-14), horizon 1-7;
#   tet:    the Sunday at least 21 days before Tết 2026 (2026-01-25), 35 days ahead.
# Baseline "SN4 sạch" = step0 methods.sn_table: mean of the last 4 same-weekday non-holiday dates, n=4.
# Measures, for model and baseline alike (QĐ-16 G5-7):
#   WAPE of min(forecast, seats that ran) against tickets sold, over every observed slot-day with an SN4 value;
#   sold-out caught: share of sold-out slot-days where forecast >= seats;
#   flag recall / precision: flag = forecast > 0.9 x standard seats, hot = tickets > 0.9 x standard seats.
# Standard seats are Route.suitableBusType on the real DB: routes 1, 2, 5 -> 22; routes 3, 4 -> 40.
# It also prints, for forecast_discussion.md §15: the WAPE of each origin, the "trap" scorings QĐ-16 rejects,
# and the context numbers of §15.1.
#
# Writes src/test/resources/forecast/reference_backtest.csv (block, metric, value).
# Usage: python -I backtest_reference.py <src/test/resources/forecast directory>   (the step 0 venv)
import sys
from pathlib import Path

RESEARCH = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RESEARCH / "gd2"))
sys.path.insert(0, str(RESEARCH / "step0"))
import numpy as np
import pandas as pd
import run_gd2 as R

GROUP = {"LEAVING_HUB": 0, "TOWARD_HUB": 1, "": 2}
STANDARD_SEATS = {1: 22, 2: 22, 3: 40, 4: 40, 5: 22}
EM_ROUNDS, LAM_DEVIATION, K, LAM_POOLED = 5, 2.0, 0, 2.0


def score(rows):
    model, base, sold, seats, std = (np.array(c, float) for c in zip(*rows))
    out = {"testedSlotDays": len(sold)}
    out["modelWape"] = 100 * np.abs(np.minimum(model, seats) - sold).sum() / sold.sum()
    out["baselineWape"] = 100 * np.abs(np.minimum(base, seats) - sold).sum() / sold.sum()
    so = sold >= seats
    out["soldOutSlotDays"] = int(so.sum())
    out["modelSoldOutCaught"] = 100 * (model[so] >= seats[so]).mean()
    out["baselineSoldOutCaught"] = 100 * (base[so] >= seats[so]).mean()
    hot = sold / std > 0.9
    fm, fb = model / std > 0.9, base / std > 0.9
    out["hotSlotDays"] = int(hot.sum())
    out["modelFlagRecall"] = 100 * (fm & hot).sum() / hot.sum()
    out["baselineFlagRecall"] = 100 * (fb & hot).sum() / hot.sum()
    out["modelFlagPrecision"] = 100 * (fm & hot).sum() / fm.sum()
    out["baselineFlagPrecision"] = 100 * (fb & hot).sum() / fb.sum()
    # The two scorings QĐ-16 rejects, printed for the record only.
    trap = 100 * np.abs(model - sold).sum() / sold.sum(), 100 * np.abs(base - sold).sum() / sold.sum()
    nso = ~so
    unsold = (100 * np.abs(model[nso] - sold[nso]).sum() / sold[nso].sum(),
              100 * np.abs(base[nso] - sold[nso]).sum() / sold[nso].sum())
    return out, trap, unsold


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    base = Path(sys.argv[1])
    hist = pd.read_csv(base / "slot_history.csv", parse_dates=["date"])
    routes = pd.read_csv(base / "routes.csv", keep_default_na=False)
    flow = dict(zip(routes["route_id"], routes["holiday_flow"]))
    series = sorted({(r, h) for r, h in zip(hist["route_id"], hist["hour"])})
    dates = pd.date_range("2023-01-01", "2026-10-21")
    col = {key: i for i, key in enumerate(series)}
    Yv = np.full((len(dates), len(series)), np.nan)
    Cv = np.full((len(dates), len(series)), np.nan)
    pos = (hist["date"] - dates[0]).dt.days.to_numpy()
    for p, r, h, sales, seats in zip(pos, hist["route_id"], hist["hour"], hist["sales"], hist["seats"]):
        Yv[p, col[(r, h)]] = sales
        Cv[p, col[(r, h)]] = seats
    groups = [GROUP[flow[r]] for r, _ in series]
    std = [STANDARD_SEATS[r] for r, _ in series]
    wins = R.windows("current")
    hol = R.M.holiday_frame(wins, dates[0], dates[-1] + pd.Timedelta(days=60))
    keys = R.M.active_keys(hol)
    Y = pd.DataFrame(Yv, index=dates)
    T = R.M.sn_table(Y, exclude_dates=set(keys.keys()), n=4)

    def run(origin, horizon):
        p = dates.get_loc(origin)
        out = R.slot_glm(Yv, Cv, dates, groups, keys, p, horizon, 0, EM_ROUNDS, LAM_DEVIATION, K, LAM_POOLED)
        sn = R.M.table_forecast(T, Y, origin, horizon)
        rows = []
        for h in range(horizon):
            for s in range(len(series)):
                if not np.isnan(Yv[p + h + 1, s]) and not np.isnan(sn[h, s]):
                    rows.append((out[h, s], sn[h, s], Yv[p + h + 1, s], Cv[p + h + 1, s], std[s]))
        return rows

    recent = []
    for origin in pd.date_range("2026-08-26", "2026-10-14", freq="7D"):
        rows = run(origin, 7)
        m, _, _ = score(rows)
        print(f"origin {origin.date()}: model {m['modelWape']:.2f}% / SN4 {m['baselineWape']:.2f}%", flush=True)
        recent += rows
    tet = run(pd.Timestamp("2026-01-25"), 35)

    # Context quoted in forecast_discussion.md §15.1: hot cells for the 7 days after 22/10/2026 under the new
    # model (GĐ4 reference forecast, origin 21/10), and how often the last 12 weeks actually ran hot.
    ref = pd.read_csv(base / "reference_forecast.csv", parse_dates=["origin", "date"])
    week = ref[(ref["origin"] == "2026-10-21") & (ref["date"] >= "2026-10-23") & (ref["date"] <= "2026-10-29")]
    hot_week = (week["expected"] / week["route_id"].map(STANDARD_SEATS) > 0.9).sum()
    last12 = hist[hist["date"] >= "2026-07-29"]
    hot_share = (last12["sales"] / last12["route_id"].map(STANDARD_SEATS) > 0.9).mean() * 100
    sold_out_share = (last12["sales"] >= last12["seats"]).mean() * 100
    print(f"23-29/10/2026: {hot_week}/{len(week)} cells need reinforcement under the new model;"
          f" last 12 weeks: {hot_share:.1f}% of slots sold > 90% of a standard bus, {sold_out_share:.1f}% sold out")

    lines = []
    for block, rows in (("recent", recent), ("tet", tet)):
        metrics, trap, unsold = score(rows)
        print(block, {k: round(v, 3) for k, v in metrics.items()})
        print(f"  rejected scorings — demand vs tickets: model {trap[0]:.2f}% / SN4 {trap[1]:.2f}%;"
              f" not-sold-out only: model {unsold[0]:.2f}% / SN4 {unsold[1]:.2f}%")
        lines += [(block, k, repr(float(v))) for k, v in metrics.items()]
    pd.DataFrame(lines, columns=["block", "metric", "value"]).to_csv(base / "reference_backtest.csv", index=False)
    print(f"wrote {len(lines)} reference values")


if __name__ == "__main__":
    main()
