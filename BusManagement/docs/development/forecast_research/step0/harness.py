# Step 0: the rolling-origin loop shared by run_hk.py (experiment A) and run_synth.py (experiment B).
# Runs the methods that need nothing but the daily series Y and the holiday calendar, and writes the
# long forecasts CSV that report.py scores.
import time
from multiprocessing import Pool

import numpy as np
import pandas as pd

import methods as M

DAY = M.DAY
SHARED = ["sn1", "sn4", "sn4c", "lr", "hw", "prophet", "lift", "lgb"]


def run_shared(Y, groups, windows, hol, origins, wanted, H, train_start):
    """{(method, origin): H x S forecast} for every wanted method in SHARED."""
    keys = M.active_keys(hol)
    hol_dates = set(hol["date"])
    S = Y.shape[1]
    results = {}

    def put(name, o, F):
        results[(name, o)] = F

    t = time.time()
    T4 = M.sn_table(Y, n=4)
    T1 = M.sn_table(Y, n=1)
    T4c = M.sn_table(Y, exclude_dates=hol_dates, n=4)
    for o in origins:
        if "sn1" in wanted: put("sn1", o, M.table_forecast(T1, Y, o, H))
        if "sn4" in wanted: put("sn4", o, M.table_forecast(T4, Y, o, H))
        if "sn4c" in wanted: put("sn4c", o, M.table_forecast(T4c, Y, o, H))
        if "lr" in wanted: put("lr", o, M.lr_current(Y, o, H, train_start))
    print(f"naive+lr done {time.time() - t:.0f}s", flush=True)

    if "lift" in wanted:
        t = time.time()
        lm = M.LiftModel(Y, hol, groups, train_start, lam=2.0)
        for o in origins:
            put("lift", o, lm.forecast(o, H))
        print(f"lift done {time.time() - t:.0f}s", flush=True)

    if "hw" in wanted or "prophet" in wanted:
        with Pool(8) as pool:
            if "hw" in wanted:
                t = time.time()
                tasks = [(np.log(np.maximum(Y.loc[train_start:o, c].ffill().bfill().to_numpy(), 1.0)), H)
                         for o in origins for c in Y.columns]
                res = pool.map(M.hw_task, tasks, chunksize=8)
                for i, o in enumerate(origins):
                    put("hw", o, np.stack(res[i * S:(i + 1) * S], axis=1))
                print(f"hw done {time.time() - t:.0f}s", flush=True)
            if "prophet" in wanted:
                t = time.time()
                ph = M.prophet_holidays(windows)
                tasks = []
                for o in origins:
                    for c in Y.columns:
                        s = Y.loc[train_start:o, c].dropna()
                        tasks.append((s.index, np.log(np.maximum(s.to_numpy(), 1.0)), ph, H))
                res = pool.map(M.prophet_task, tasks, chunksize=4)
                for i, o in enumerate(origins):
                    put("prophet", o, np.stack(res[i * S:(i + 1) * S], axis=1))
                print(f"prophet done {time.time() - t:.0f}s", flush=True)

    if "lgb" in wanted:
        t = time.time()
        names = [w[0] for w in windows]
        model = None
        for i, o in enumerate(origins):
            p_o = Y.index.get_loc(o)
            if i % 4 == 0:
                train_pos = [p for p in range(28, p_o - 1) if p % 2 == 0]
                Xtr, ytr, _, meta = M.lgb_features(Y, T4c, T1, names, keys, train_pos, H, groups, True)
                keep = [j for j, (p, h, s) in enumerate(meta) if p + h <= p_o]
                model = M.lgb_train(Xtr.iloc[keep], ytr[keep])
            Xte, _, base, meta = M.lgb_features(Y, T4c, T1, names, keys, [p_o], H, groups, False)
            F = np.full((H, S), np.nan)
            pred = np.exp(model.predict(Xte)) * base
            for (p, h, s), v in zip(meta, pred):
                F[h - 1, s] = v
            put("lgb", o, F)
        print(f"lgb done {time.time() - t:.0f}s", flush=True)
    return results


def segments(hol):
    """date -> 'cny<year>' for lunar-new-year window days, 'holiday' for other window days."""
    seg = {}
    for d, n, k in hol[["date", "name", "offset"]].itertuples(index=False):
        label = f"cny{d.year}" if n == "cny" else "holiday"
        if seg.get(d, "").startswith("cny"):
            continue
        seg[d] = label
    return seg


def write_long(results, Y, groups, seg, H, out_path, extra=None):
    """One row per (method, origin, series, horizon). extra: {column: DataFrame shaped like Y} whose value
    on the target date is copied into the row (e.g. true demand, standard seats)."""
    extra = extra or {}
    vals = Y.to_numpy(float)
    ex = {k: v.to_numpy(float) for k, v in extra.items()}
    rows = []
    S = Y.shape[1]
    for (name, o), F in results.items():
        p_o = Y.index.get_loc(o)
        for h in range(1, H + 1):
            d = o + h * DAY
            for s in range(S):
                rows.append((name, o.date(), Y.columns[s], groups[s], h, d.date(), seg.get(d, "normal"),
                             F[h - 1, s], vals[p_o + h, s]) + tuple(a[p_o + h, s] for a in ex.values()))
    cols = ["method", "origin", "series", "group", "h", "date", "seg", "yhat", "y"] + list(ex)
    pd.DataFrame(rows, columns=cols).to_csv(out_path, index=False)
    print("wrote", out_path, len(rows), flush=True)
