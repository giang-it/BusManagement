# Read-only research: compare forecasting methods on the real (synthetic) history.
# Rolling-origin backtest: 8 origins, one per week, each forecasting the next 7 days.
# Usage: python compare_methods.py hist_snapshot_2026-10-14.tsv   (about 2 s; pure Python 3, no numpy)
import sys, itertools
from datetime import date, datetime, timedelta
from collections import defaultdict

rows = defaultdict(list)
for line in open(sys.argv[1], encoding="utf-8"):
    r, dt, sold, seats = line.rstrip("\n").split("\t")
    d = datetime.strptime(dt, "%Y-%m-%d %H")
    rows[(int(r), d.hour, d.date())].append(max(0.0, min(1.0, int(sold) / int(seats))))

series = defaultdict(dict)
for (r, h, d), v in rows.items():
    series[(r, h)][d] = sum(v) / len(v)
series = {k: v for k, v in series.items() if len(v) >= 28}
LAST = max(d for s in series.values() for d in s)
ORIGINS = [LAST - timedelta(days=7 * k + 7) for k in range(8)]

# ---- current model: pooled day-of-week factors + linear trend on deseasonalised values
def dow_factors(train_by_series):
    pool = [(d, v) for s in train_by_series.values() for d, v in s.items()]
    overall = sum(v for _, v in pool) / len(pool)
    by = defaultdict(list)
    for d, v in pool: by[d.isoweekday()].append(v)
    return {k: (sum(x) / len(x)) / overall for k, x in by.items()}

def lr_forecast(train, f, targets):
    origin = min(train); n = len(train); sx = sy = sxy = sxx = 0.0
    for d, v in train.items():
        x = (d - origin).days; y = v / f[d.isoweekday()]
        sx += x; sy += y; sxy += x * y; sxx += x * x
    b = (n * sxy - sx * sy) / (n * sxx - sx * sx); a = (sy - b * sx) / n
    return {t: max(0, min(1, (a + b * (t - origin).days) * f[t.isoweekday()])) for t in targets}

def ma_forecast(train, origin, targets):
    w = [v for d, v in train.items() if origin - timedelta(days=6) <= d <= origin]
    m = sum(w) / len(w) if w else sum(train.values()) / len(train)
    return {t: m for t in targets}

def snaive_forecast(train, targets, weeks=1):
    out = {}
    for t in targets:
        vals, k = [], 1
        while len(vals) < weeks and k < 20:
            d = t - timedelta(days=7 * k)
            if d in train: vals.append(train[d])
            k += 1
        out[t] = sum(vals) / len(vals) if vals else sum(train.values()) / len(train)
    return out

# ---- Holt-Winters additive, damped trend, period 7, tolerant of missing days
def hw_run(y, a, b_, g, phi):
    obs = [v for v in y[:14] if v is not None]
    w1 = [v for v in y[:7] if v is not None]; w2 = [v for v in y[7:14] if v is not None]
    l = sum(w1) / len(w1); b = (sum(w2) / len(w2) - l) / 7 if w2 else 0.0
    s = [0.0] * 7; cnt = [0] * 7
    for t in range(14):
        if y[t] is not None: s[t % 7] += y[t] - l; cnt[t % 7] += 1
    s = [s[j] / cnt[j] if cnt[j] else 0.0 for j in range(7)]
    m = sum(s) / 7; s = [x - m for x in s]
    sse = 0.0
    for t in range(14, len(y)):
        yhat = l + phi * b + s[t % 7]
        if y[t] is None:
            l, b = l + phi * b, phi * b
            continue
        sse += (y[t] - yhat) ** 2
        l_new = a * (y[t] - s[t % 7]) + (1 - a) * (l + phi * b)
        b = b_ * (l_new - l) + (1 - b_) * phi * b
        s[t % 7] = g * (y[t] - l_new) + (1 - g) * s[t % 7]
        l = l_new
    return sse, l, b, s

GRID = list(itertools.product([0.05, 0.1, 0.2, 0.3, 0.5], [0.01, 0.05, 0.1], [0.05, 0.1, 0.2, 0.3], [0.8, 0.9, 0.98]))

def hw_forecast(train, origin, targets):
    start = min(train); n = (origin - start).days + 1
    y = [train.get(start + timedelta(days=i)) for i in range(n)]
    best = min(GRID, key=lambda p: hw_run(y, *p)[0])
    _, l, b, s = hw_run(y, *best)
    out = {}
    for t in targets:
        h = (t - origin).days
        damp = sum(best[3] ** i for i in range(1, h + 1))
        out[t] = max(0, min(1, l + damp * b + s[(n - 1 + h) % 7]))
    return out

methods = {"LR + mua tuan (hien tai)": [], "Trung binh truot 7 ngay": [], "Seasonal naive (tuan truoc)": [],
           "Seasonal naive TB 4 tuan": [], "Holt-Winters tat dan": []}
for origin in ORIGINS:
    trains = {k: {d: v for d, v in s.items() if d <= origin} for k, s in series.items()}
    f = dow_factors(trains)
    for k, s in series.items():
        tr = trains[k]
        test = {d: v for d, v in s.items() if origin < d <= origin + timedelta(days=7) and v > 0}
        if len(tr) < 28 or not test: continue
        tg = list(test)
        preds = {"LR + mua tuan (hien tai)": lr_forecast(tr, f, tg),
                 "Trung binh truot 7 ngay": ma_forecast(tr, origin, tg),
                 "Seasonal naive (tuan truoc)": snaive_forecast(tr, tg, 1),
                 "Seasonal naive TB 4 tuan": snaive_forecast(tr, tg, 4),
                 "Holt-Winters tat dan": hw_forecast(tr, origin, tg)}
        for name, p in preds.items():
            methods[name] += [abs(test[d] - p[d]) / test[d] for d in tg]

print(f"origins {ORIGINS[-1]} .. {ORIGINS[0]}, horizon 7 days, series {len(series)}")
for name, errs in methods.items():
    print(f"{name:32s} MAPE {sum(errs)/len(errs)*100:5.2f}%  (n={len(errs)})")
