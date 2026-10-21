# GĐ2 re-check (decision QĐ-13): the "≥ 2 occurrences" rule of QĐ-3 was motivated by the REAL Hong Kong data
# in step 0 (learning a holiday from a single occurrence hurt there), while on the new synthetic data rule (a)
# hurts. So the three variants, and the shrinkage lambda, are run on the real data too. Hong Kong has no seat
# limit, so no EM. Same data, windows and 90 weekly origins as step0/run_hk.py. Research only.
#   glm_dev_k0  = G1', K=0, lambda 2, no rule (c)  — identical to step 0
#   hk_rule_a   = holiday seen in full < 2 times in training = ordinary day
#   hk_rule_b   = pooled holiday columns shrunk too (ridge 2)
#   hk_lam8     = per-series deviation shrunk harder (lambda 8)
#
# Usage: python -I hk_rules.py <step0_data/hk_daily_passenger_traffic_2026-10-20.csv> <out forecasts csv>
# Then:  python -I ../step0/compare.py <out csv> y 1 35 hk_rule_a:glm_dev_k0,... cny2025
import os
import sys
import time
from multiprocessing import Pool

for _v in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_v, "1")

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "step0"))
sys.path.insert(0, HERE)
import numpy as np
import pandas as pd
import harness
import methods as M
import run_hk
import run_gd2

# name: (lambda deviation, lambda pooled, min occurrences)
VARIANTS = {
    "glm_dev_k0": (2.0, 1e-6, 0),
    "hk_rule_a": (2.0, 1e-6, 2),
    "hk_rule_b": (2.0, 2.0, 0),
    "hk_lam8": (8.0, 1e-6, 0),
}


def task(args):
    name, Yv, dates, groups, keys, origin_pos, lam, lam_pool, min_occ = args
    if min_occ > 1:
        keys = run_gd2.filter_keys(keys, run_hk.WINDOWS, dates[0], dates[origin_pos], min_occ)
    uncapped = np.full_like(Yv, np.inf)
    return name, origin_pos, run_gd2.slot_glm(Yv, uncapped, dates, groups, keys, origin_pos, run_hk.H, 0, 0,
                                              lam, 0, lam_pool)


def main():
    Y, groups = run_hk.load(sys.argv[1])
    end = Y.index.max()
    origins = list(pd.date_range("2024-12-29", end - run_hk.H * M.DAY, freq="7D"))
    hol = M.holiday_frame(run_hk.WINDOWS, Y.index.min(), end + pd.Timedelta(days=60))
    keys = M.active_keys(hol)
    Yv = Y.to_numpy(float)
    print(f"series={Y.shape[1]} origins={len(origins)}", flush=True)
    results = harness.run_shared(Y, groups, run_hk.WINDOWS, hol, origins, ["sn4c"], run_hk.H, run_hk.TRAIN_START)
    tasks = [(name, Yv, Y.index, groups, keys, Y.index.get_loc(o), lam, lam_pool, min_occ)
             for name, (lam, lam_pool, min_occ) in VARIANTS.items() for o in origins]
    t = time.time()
    with Pool(int(os.environ.get("GD2_PROCESSES", "12"))) as pool:
        for name, p, F in pool.imap_unordered(task, tasks):
            results[(name, Y.index[p])] = F
    print(f"variants done {time.time() - t:.0f}s", flush=True)
    harness.write_long(results, Y, groups, harness.segments(hol), run_hk.H, sys.argv[2])


if __name__ == "__main__":
    main()
