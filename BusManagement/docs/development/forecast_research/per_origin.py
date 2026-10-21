# Read-only research: per-origin breakdown of compare_methods.py (same data, same methods).
# Usage: python per_origin.py hist_snapshot_2026-10-14.tsv   (run from this folder)
import os, runpy, sys
from datetime import timedelta
here = os.path.dirname(os.path.abspath(__file__))
sys.argv = ["x", sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "hist_snapshot_2026-10-14.tsv")]
g = runpy.run_path(os.path.join(here, "compare_methods.py"))
series, ORIGINS = g["series"], g["ORIGINS"]
print("origin -> test window      LR(now)  HW-damped  SNaive4  MA7")
for origin in sorted(ORIGINS):
    trains = {k: {d: v for d, v in s.items() if d <= origin} for k, s in series.items()}
    f = g["dow_factors"](trains); e = {"lr": [], "hw": [], "sn": [], "ma": []}
    for k, s in series.items():
        tr = trains[k]; test = {d: v for d, v in s.items() if origin < d <= origin + timedelta(days=7) and v > 0}
        tg = list(test)
        for key, p in (("lr", g["lr_forecast"](tr, f, tg)), ("hw", g["hw_forecast"](tr, origin, tg)),
                       ("sn", g["snaive_forecast"](tr, tg, 4)), ("ma", g["ma_forecast"](tr, origin, tg))):
            e[key] += [abs(test[d] - p[d]) / test[d] for d in tg]
    m = {k: sum(v) / len(v) * 100 for k, v in e.items()}
    print(f"{origin} -> {origin+timedelta(days=1)}..{origin+timedelta(days=7)}  {m['lr']:5.2f}%   {m['hw']:5.2f}%    {m['sn']:5.2f}%  {m['ma']:5.2f}%")
