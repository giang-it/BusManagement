# GĐ2 re-check (decision QĐ-13 in docs/development/forecast_decisions.md), step 2 of 2: re-run the step 0
# choices on the data the NEW app generator wrote (build_dataset.py), one factor at a time around the
# chosen model "G1' + EM, K=0". Research only — nothing here touches the app or the database.
#
# Every method trains on SALES (capped by seats — all the app records) and is scored against the true
# DEMAND the generator exported. Storm-closure days are dropped from training and scored apart.
# Origins every 14 days from 2023-12-24 (so Tết 2024 is forecast with ONE earlier Tết in the history,
# Tết 2025 with two, Tết 2026 with three) to the last day minus the 35-day horizon.
#
# Usage: python -I run_gd2.py <dataset.csv> <out forecasts csv> [every n-th origin, for a smoke test]
# Then:  python -I report_gd2.py <out forecasts csv>
#        python -I ../step0/compare.py <out forecasts csv> demand 1 7 glm_main:sn4c,...
import os
import sys
import time
from multiprocessing import Pool

for _v in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_v, "1")

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "step0"))
import numpy as np
import pandas as pd
import harness
import methods as M

DAY = M.DAY
H = 35
T = pd.Timestamp
TRAIN_START = T("2023-01-01")

# The forecaster's calendar: the anchors of domain/HolidayCalendar.java (legal / lunar dates), 2023-2026.
ANCHORS = {
    "cny": [T("2023-01-22"), T("2024-02-10"), T("2025-01-29"), T("2026-02-17")],
    "tet_dl": [T(f"{y}-01-01") for y in (2023, 2024, 2025, 2026)],
    "gio_to": [T("2023-04-29"), T("2024-04-18"), T("2025-04-07"), T("2026-04-26")],
    "le_30_4": [T(f"{y}-04-30") for y in (2023, 2024, 2025, 2026)],
    "quoc_khanh": [T(f"{y}-09-02") for y in (2023, 2024, 2025, 2026)],
}
# Window sets (days before, days after). "current" = domain/Holiday.java as committed in GĐ1.
WINDOW_SETS = {
    "current": {"cny": (-14, 21), "tet_dl": (-3, 4), "gio_to": (-3, 2), "le_30_4": (-4, 5), "quoc_khanh": (-4, 2)},
    "narrow": {"cny": (-7, 14), "tet_dl": (-2, 3), "gio_to": (-2, 1), "le_30_4": (-3, 4), "quoc_khanh": (-3, 1)},
    "wide": {"cny": (-21, 28), "tet_dl": (-4, 5), "gio_to": (-4, 3), "le_30_4": (-5, 6), "quoc_khanh": (-5, 3)},
}

# name: (EM rounds, lambda deviation, K yearly Fourier, lambda pooled, min occurrences, window set, one group)
MAIN = (15, 2.0, 0, 1e-6, 0, "current", False)
VARIANTS = {
    "glm_main": MAIN,                                        # G1' + EM, K=0, rule (c): no occurrence rule
    "glm_k3": (15, 2.0, 3, 1e-6, 0, "current", False),       # re-check K=0
    "glm_rule_a": (15, 2.0, 0, 1e-6, 2, "current", False),   # rule (a): holiday seen < 2 times = ordinary day
    "glm_rule_b": (15, 2.0, 0, 2.0, 0, "current", False),    # rule (b): pooled holiday effect shrunk too
    "glm_em0": (0, 2.0, 0, 1e-6, 0, "current", False),       # no EM
    "glm_em5": (5, 2.0, 0, 1e-6, 0, "current", False),       # fewer EM rounds
    "glm_lam05": (15, 0.5, 0, 1e-6, 0, "current", False),    # weaker shrinkage of per-route deviations
    "glm_lam8": (15, 8.0, 0, 1e-6, 0, "current", False),     # stronger shrinkage
    "glm_narrow": (15, 2.0, 0, 1e-6, 0, "narrow", False),    # narrower holiday windows
    "glm_wide": (15, 2.0, 0, 1e-6, 0, "wide", False),        # wider holiday windows
    "glm_onegroup": (15, 2.0, 0, 1e-6, 0, "current", True),  # no Route.holidayFlow
}


def windows(name):
    return [(n, ANCHORS[n], lo, hi) for n, (lo, hi) in WINDOW_SETS[name].items()]


def filter_keys(keys, wins, train_start, origin, min_occ):
    """Rule (a): drop every (holiday, offset) key of a holiday whose window was seen in full fewer than
    min_occ times between train_start and origin — that holiday is treated as an ordinary day, both in
    training and in the forecast."""
    seen = {n: sum(1 for a in anchors if a + lo * DAY >= train_start and a + hi * DAY <= origin)
            for n, anchors, lo, hi in wins}
    return {d: [(n, k) for n, k in ks if seen[n] >= min_occ] for d, ks in keys.items()}


def slot_glm(Yv, Cv, dates, groups, keys, origin_pos, H, train_pos, em_iters, lam, K, lam_pool):
    """Copy of step0 methods.slot_glm_task (G1' + optional EM) with ONE added option, lam_pool: the ridge on
    the POOLED holiday columns (step 0 left them unpenalised: 1e-6). Everything else is identical."""
    from scipy.sparse import csr_matrix
    groups = np.asarray(groups)
    S, G = Yv.shape[1], int(groups.max()) + 1
    t0, origin = dates[train_pos], dates[origin_pos]
    knots = list(pd.date_range(t0 + pd.Timedelta(days=182), origin - pd.Timedelta(days=60), freq="182D"))
    nT = 1 + len(knots)
    hkeys = {}
    for p in range(train_pos, origin_pos + 1):
        for n, k in keys.get(dates[p], []):
            hkeys.setdefault((n, k), len(hkeys))
    nH = len(hkeys)
    o_wd = S
    o_fo = o_wd + 6 * S
    o_tr = o_fo + 2 * K * S
    o_hp = o_tr + nT * S
    o_hs = o_hp + nH * G
    n_cols = o_hs + nH * S

    def design(day_list, s_list, clamp):
        ri, ci, vv = [], [], []
        for i, (d, s) in enumerate(zip(day_list, s_list)):
            cells = [(s, 1.0)]
            if d.dayofweek > 0:
                cells.append((o_wd + 6 * s + d.dayofweek - 1, 1.0))
            doy = 2 * np.pi * d.dayofyear / 365.25
            for k in range(K):
                cells += [(o_fo + 2 * K * s + 2 * k, np.sin((k + 1) * doy)),
                          (o_fo + 2 * K * s + 2 * k + 1, np.cos((k + 1) * doy))]
            dt = min(d, origin) if clamp else d
            cells.append((o_tr + nT * s, (dt - t0).days / 365.25))
            for j, kn in enumerate(knots):
                v = (dt - kn).days / 365.25
                if v > 0:
                    cells.append((o_tr + nT * s + 1 + j, v))
            for n, k in keys.get(d, []):
                c = hkeys.get((n, k))
                if c is not None:
                    cells += [(o_hp + G * c + groups[s], 1.0), (o_hs + S * c + s, 1.0)]
            for c, v in cells:
                ri.append(i); ci.append(c); vv.append(v)
        return csr_matrix((vv, (ri, ci)), shape=(len(day_list), n_cols))

    scale = np.nanmean(Yv[train_pos:origin_pos + 1], axis=0)
    days, ss, ys, cs = [], [], [], []
    for p in range(train_pos, origin_pos + 1):
        for s in range(S):
            if np.isfinite(Yv[p, s]):
                days.append(dates[p]); ss.append(s); ys.append(Yv[p, s]); cs.append(Cv[p, s])
    X = design(days, ss, clamp=False)
    y, cap = np.array(ys), np.array(cs)
    sc = scale[np.array(ss)]
    cens = y >= cap
    ridge = np.full(n_cols, 1e-6)
    ridge[o_hp:o_hs] = lam_pool
    ridge[o_hs:] = lam
    zero = np.zeros(len(y))
    ycur = y.astype(float).copy()
    beta = None
    for it in range(em_iters + 1):
        beta = M.poisson_irls(X, ycur / sc, zero, ridge, beta0=beta)
        if it == em_iters:
            break
        mu = np.exp(X @ beta) * sc
        u = ~cens
        inv = np.sum((y[u] - mu[u]) ** 2 - mu[u]) / np.sum(mu[u] ** 2)
        size = float(np.clip(1.0 / max(inv, 1e-4), 2.0, 1e4))
        ycur[cens] = M.nb_mean_at_least(mu[cens], cap[cens], size)
    fitted = np.exp(X @ beta) * sc
    corr = np.ones(S)
    recent = origin - pd.Timedelta(days=27)
    ss_arr = np.array(ss)
    calm = np.array([d >= recent and not keys.get(d) for d in days])
    for s in range(S):
        m = calm & (ss_arr == s)
        if m.any():
            corr[s] = ycur[m].sum() / fitted[m].sum()
    targets = pd.date_range(origin + DAY, periods=H)
    out = np.zeros((H, S))
    for s in range(S):
        Xf = design(list(targets), [s] * H, clamp=True)
        out[:, s] = np.exp(Xf @ beta) * corr[s] * scale[s]
    return out


def task(args):
    name, Yv, Cv, dates, groups, origin_pos, min_occ, window_set, em, lam, K, lam_pool = args
    wins = windows(window_set)
    hol = M.holiday_frame(wins, dates[0], dates[-1] + pd.Timedelta(days=60))
    keys = M.active_keys(hol)
    if min_occ > 1:
        keys = filter_keys(keys, wins, dates[0], dates[origin_pos], min_occ)
    return name, origin_pos, slot_glm(Yv, Cv, dates, groups, keys, origin_pos, H, 0, em, lam, K, lam_pool)


def load(path):
    df = pd.read_csv(path, parse_dates=["date"])
    wide = {c: df.pivot(index="date", columns="series", values=c).astype(float)
            for c in ("sales", "seats", "demand", "cap", "storm", "mu")}
    groups = df.drop_duplicates("series").set_index("series").loc[wide["sales"].columns, "group"].tolist()
    return wide, groups


def main():
    wide, groups = load(sys.argv[1])
    out_path = sys.argv[2]
    Y = wide["sales"].mask(wide["storm"] == 1)
    end = Y.index.max()
    origins = list(pd.date_range("2023-12-24", end - H * DAY, freq="14D"))
    if len(sys.argv) > 3:
        origins = origins[::int(sys.argv[3])]
    wins = windows("current")
    hol = M.holiday_frame(wins, Y.index.min(), end + pd.Timedelta(days=60))
    observed = Y.count().sum()
    print(f"series={Y.shape[1]} days={len(Y)} {Y.index.min().date()}..{end.date()} origins={len(origins)} "
          f"{origins[0].date()}..{origins[-1].date()} censored={(Y >= wide['seats']).sum().sum() / observed:.1%}",
          flush=True)

    results = harness.run_shared(Y, groups, wins, hol, origins, ["sn4c"], H, TRAIN_START)
    for o in origins:
        p = Y.index.get_loc(o)
        results[("oracle", o)] = wide["mu"].to_numpy(float)[p + 1:p + 1 + H]
    R = (Y / wide["seats"]).clip(0, 1)
    cap = wide["cap"].iloc[0].to_numpy()
    for o in origins:
        results[("app", o)] = M.app_rate_current(R, o, H, TRAIN_START) * cap

    Yv, Cv = Y.to_numpy(float), wide["seats"].to_numpy(float)
    one = [0] * len(groups)
    tasks = []
    for name, (em, lam, K, lam_pool, min_occ, window_set, one_group) in VARIANTS.items():
        for o in origins:
            tasks.append((name, Yv, Cv, Y.index, one if one_group else groups, Y.index.get_loc(o), min_occ,
                          window_set, em, lam, K, lam_pool))
    t = time.time()
    with Pool(int(os.environ.get("GD2_PROCESSES", "12"))) as pool:
        for i, (name, p, F) in enumerate(pool.imap_unordered(task, tasks)):
            results[(name, Y.index[p])] = F
            if (i + 1) % 50 == 0:
                print(f"{i + 1}/{len(tasks)} fits {time.time() - t:.0f}s", flush=True)
    print(f"glm variants done {time.time() - t:.0f}s", flush=True)

    harness.write_long(results, Y, groups, harness.segments(hol), H, out_path,
                       extra={c: wide[c] for c in ("sales", "demand", "cap", "seats", "storm")})


if __name__ == "__main__":
    main()
