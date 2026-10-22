# GĐ4 of the Demand Forecast redesign (decision QĐ-15): the Python REFERENCE that the Java model
# (service/SlotDemandModel) must reproduce. Research only — nothing here touches the app or the database.
#
# It runs gd2/run_gd2.py's slot_glm — the exact code behind the GĐ2 numbers, unchanged — with the FINAL
# configuration: EM 5 rounds (QĐ-1), ridge 2 on the per-route holiday deviations, ridge 2 on the pooled
# holiday effects (rule (b), QĐ-3), K = 0, the "current" holiday windows, direction groups from
# Route.holidayFlow. It writes what the Java test compares against:
#   reference_forecast.csv        origin, date, route_id, hour, expected demand (35 days x 15 series)
#   reference_holiday_effects.csv origin, holiday, offset, group, beta (pooled log-effect)
#   reference_nb.csv              mu, c, size, E[D | D >= c] from scipy (the EM E-step)
#
# Inputs (frozen in src/test/resources/forecast/, exported by export_slot_history.sql):
#   slot_history.csv  date, route_id, hour, sales, seats — one row per slot with a COMPLETED trip
#   routes.csv        route_id, holiday_flow
# Usage: python -I reference.py <src/test/resources/forecast directory>
# Needs numpy/pandas/scipy (the step 0 venv).
import sys
import time
from pathlib import Path

RESEARCH = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RESEARCH / "gd2"))
sys.path.insert(0, str(RESEARCH / "step0"))
import numpy as np
import pandas as pd
import run_gd2 as R

ORIGINS = [pd.Timestamp("2026-01-25"), pd.Timestamp("2026-10-21")]  # a horizon over Tết 2026, and the last day
H = 35
EM_ROUNDS, LAM_DEVIATION, K, LAM_POOLED = 5, 2.0, 0, 2.0
GROUP = {"LEAVING_HUB": 0, "TOWARD_HUB": 1, "": 2}  # the same pooling groups as step 0 and GĐ2
JAVA_NAME = {"cny": "LUNAR_NEW_YEAR", "tet_dl": "NEW_YEAR", "gio_to": "HUNG_KINGS_COMMEMORATION",
             "le_30_4": "REUNIFICATION_AND_LABOUR_DAY", "quoc_khanh": "NATIONAL_DAY"}


def main():
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

    wins = R.windows("current")
    hol = R.M.holiday_frame(wins, dates[0], dates[-1] + pd.Timedelta(days=60))
    keys = R.M.active_keys(hol)

    # Capture the coefficients of the LAST IRLS fit (the one after the final EM round).
    captured = {}
    irls = R.M.poisson_irls

    def capture(*args, **kwargs):
        captured["beta"] = irls(*args, **kwargs)
        return captured["beta"]

    R.M.poisson_irls = capture

    forecast_rows, effect_rows = [], []
    for origin in ORIGINS:
        p = dates.get_loc(origin)
        t = time.time()
        out = R.slot_glm(Yv, Cv, dates, groups, keys, p, H, 0, EM_ROUNDS, LAM_DEVIATION, K, LAM_POOLED)
        print(f"origin {origin.date()}: fit + forecast {time.time() - t:.1f}s", flush=True)
        for h in range(H):
            day = origin + pd.Timedelta(days=h + 1)
            for s, (r, hour) in enumerate(series):
                forecast_rows.append((origin.date(), day.date(), r, hour, repr(float(out[h, s]))))
        # The same column layout slot_glm builds: pooled holiday block at o_hp, G columns per key.
        S, G = len(series), max(groups) + 1
        knots = list(pd.date_range(dates[0] + pd.Timedelta(days=182), origin - pd.Timedelta(days=60), freq="182D"))
        hkeys = {}
        for q in range(0, p + 1):
            for n, k in keys.get(dates[q], []):
                hkeys.setdefault((n, k), len(hkeys))
        o_hp = S + 6 * S + 2 * K * S + (1 + len(knots)) * S
        beta = captured["beta"]
        for (n, k), c in hkeys.items():
            for g in range(G):
                effect_rows.append((origin.date(), JAVA_NAME[n], k, g, repr(float(beta[o_hp + G * c + g]))))

    pd.DataFrame(forecast_rows, columns=["origin", "date", "route_id", "hour", "expected"]) \
        .to_csv(base / "reference_forecast.csv", index=False)
    pd.DataFrame(effect_rows, columns=["origin", "holiday", "offset", "group", "beta"]) \
        .to_csv(base / "reference_holiday_effects.csv", index=False)

    # E-step spot values, from moderate to extreme tails and up to the largest slot capacity (172 seats).
    nb = []
    for mu in (3.0, 15.0, 22.0, 40.0, 64.0, 120.0):
        for c in (1, 5, 22, 40, 72, 122, 172):
            for size in (2.0, 12.5, 40.0, 1e4):
                nb.append((mu, c, size, repr(float(R.M.nb_mean_at_least(np.array([mu]), np.array([c]), size)[0]))))
    pd.DataFrame(nb, columns=["mu", "c", "size", "expected"]).to_csv(base / "reference_nb.csv", index=False)
    print(f"wrote {len(forecast_rows)} forecasts, {len(effect_rows)} pooled effects, {len(nb)} E-step values")


if __name__ == "__main__":
    main()
