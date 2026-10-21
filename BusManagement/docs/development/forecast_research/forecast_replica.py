# Read-only replica of ForecastService.buildForecast() to see today's numbers.
# Usage: python forecast_replica.py hist_snapshot_2026-10-14.tsv [YYYY-MM-DD = the "today" to forecast from]
import sys
from datetime import date, datetime, timedelta
from collections import defaultdict

TODAY = date.fromisoformat(sys.argv[2]) if len(sys.argv) > 2 else date(2026, 10, 14)
MIN_OBS, HORIZON, MA_DAYS, BT_DAYS, MIN_TRAIN, THR = 28, 7, 7, 14, 14, 0.90

obs = []
for line in open(sys.argv[1], encoding="utf-8"):
    r, dt, sold, seats = line.rstrip("\n").split("\t")
    d = datetime.strptime(dt, "%Y-%m-%d %H")
    occ = max(0.0, min(1.0, int(sold) / int(seats)))
    obs.append((int(r), d.hour, d.date(), occ))

def factors(pool):
    f = [1.0] * 8
    if not pool: return f
    overall = sum(o[3] for o in pool) / len(pool)
    by = defaultdict(list)
    for o in pool: by[o[2].isoweekday()].append(o[3])
    for k, v in by.items(): f[k] = (sum(v) / len(v)) / overall
    return f

def fit(series, f, origin):
    n = len(series); sx = sy = sxy = sxx = 0.0
    for o in series:
        x = (o[2] - origin).days; fa = f[o[2].isoweekday()]
        y = o[3] / fa if fa > 0 else o[3]
        sx += x; sy += y; sxy += x * y; sxx += x * x
    den = n * sxx - sx * sx
    if n < 2 or den == 0: return (sy / n if n else 0.0, 0.0)
    slope = (n * sxy - sx * sy) / den
    return ((sy - slope * sx) / n, slope)

def predict(p, origin, target, f):
    v = (p[0] + p[1] * (target - origin).days) * f[target.isoweekday()]
    return max(0.0, min(1.0, v))

def mavg(series, end):
    start = end - timedelta(days=MA_DAYS - 1)
    w = [o[3] for o in series if start <= o[2] <= end]
    if not w: return sum(o[3] for o in series) / len(series)
    return sum(w) / len(w)

groups = defaultdict(list)
for o in obs: groups[(o[0], o[1])].append(o)
qual = {k: v for k, v in groups.items() if len(v) >= MIN_OBS}
pool = [o for v in qual.values() for o in v]
f = factors(pool)
hist_end = max(o[2] for o in obs)
ffrom = TODAY + timedelta(days=1)
print(f"today={TODAY} history={min(o[2] for o in obs)}..{hist_end} stale={(TODAY-hist_end).days}d "
      f"groups={len(groups)} qualified={len(qual)} used={len(pool)}")
print("dow factors T2..CN:", " ".join(f"{f[i]:.3f}" for i in range(1, 8)))

signals = 0; rows = []
for (r, h), s in qual.items():
    origin = min(o[2] for o in s); p = fit(s, f, origin)
    pts = [predict(p, origin, ffrom + timedelta(days=i), f) for i in range(HORIZON)]
    hot = sum(1 for v in pts if v > THR); signals += hot
    rows.append((max(pts), r, h, len(s), p[1] * 7 * 100, mavg(s, max(o[2] for o in s)), pts, hot))
rows.sort(key=lambda x: -x[0])
days = [(ffrom + timedelta(days=i)) for i in range(HORIZON)]
print("route hh  n   trend/wk  MA7  | " + " ".join(d.strftime("%a%d") for d in days))
for peak, r, h, n, tr, ma, pts, hot in rows:
    print(f"{r:>5} {h:02d} {n:4d} {tr:+7.2f}  {ma*100:4.0f}% | " +
          " ".join(("*" if v > THR else " ") + f"{v*100:3.0f}" for v in pts))
print("reinforcement signals:", signals)

# backtest
train_end = hist_end - timedelta(days=BT_DAYS)
tp = [o for v in qual.values() for o in v if o[2] <= train_end]
tf = factors(tp); me = be = 0.0; t = 0
for s in qual.values():
    tr = [o for o in s if o[2] <= train_end]; te = [o for o in s if o[2] > train_end]
    if len(tr) < MIN_TRAIN or not te: continue
    origin = min(o[2] for o in tr); p = fit(tr, tf, origin); base = mavg(tr, train_end)
    for o in te:
        if o[3] <= 0: continue
        me += abs(o[3] - predict(p, origin, o[2], tf)) / o[3]; be += abs(o[3] - base) / o[3]; t += 1
print(f"backtest: points={t} model MAPE={me/t*100:.2f}% baseline MAPE={be/t*100:.2f}%")
