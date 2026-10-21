# Step 0, experiment B: synthetic SLOT demand (route x departure hour x date) with a Vietnamese calendar,
# then censored by the seats a "historical operator" actually ran: sales = min(demand, seats).
# Research only: nothing here touches the app or the database.
#
# The structure is deliberately NOT the structure of any candidate model, so a model has to approximate
# rather than recover the formula (the "data proves itself" trap of the current backfill):
#   - holiday surges are anchored on the official BREAK (first/last day off), not on the lunar date the
#     forecaster is given, and their size changes per occurrence;
#   - seasons are bumps on the day of year, not Fourier terms;
#   - the level drifts (random walk) and jumps (competitor, new road), no straight-line trend;
#   - demand is negative binomial (over-dispersed), not Poisson;
#   - storm days cut demand unpredictably; they are flagged after the fact (Incident ROAD_ISSUE analogue).
# Magnitudes are assumptions, loosely set from press reports (Hanoi stations: Tết +200..350% vs normal days);
# the official break dates below are approximate. Synthetic data, not a measurement.
#
# Usage: python -I synth_generator.py <out csv> [seed, default 7]
import sys

import numpy as np
import pandas as pd

T = pd.Timestamp
START, END = T("2023-07-01"), T("2026-10-19")
HOURS = (6, 12, 18)

# id: (direction, standard bus seats, mean occupancy of that bus at 06 / 12 / 18 on an average day)
# direction: out = big city -> province, in = province -> big city, cross = province <-> province
ROUTES = {
    1: ("out", 40, (0.70, 0.58, 0.76)),    # Mỹ Đình -> Niệm Nghĩa
    2: ("out", 50, (0.78, 0.63, 0.83)),    # Miền Đông -> Đà Lạt
    3: ("cross", 22, (0.82, 0.68, 0.88)),  # Đà Nẵng -> Huế
    4: ("in", 40, (0.62, 0.49, 0.66)),     # Niệm Nghĩa -> Mỹ Đình
    5: ("in", 40, (0.69, 0.54, 0.74)),     # Cần Thơ -> Miền Đông
}
GROUP = {"out": 0, "in": 1, "cross": 2}
BUS_SIZES = (22, 40, 50)
NB_SIZE = 40          # demand ~ NB(mean mu, size 40): var = mu + mu^2/40
GROWTH = 0.04         # +4 % per year
WALK_SD = 0.0015      # daily random walk of the log level, per route

TET = {2024: T("2024-02-10"), 2025: T("2025-01-29"), 2026: T("2026-02-17"), 2027: T("2027-02-06")}
GIO_TO = {2024: T("2024-04-18"), 2025: T("2025-04-07"), 2026: T("2026-04-26")}
# official breaks (name, first day off, last day off) -- approximate
BREAKS = [
    ("quoc_khanh", "2023-09-01", "2023-09-04"),
    ("tet_dl", "2023-12-30", "2024-01-01"), ("tet", "2024-02-08", "2024-02-14"),
    ("gio_to", "2024-04-18", "2024-04-18"), ("le_30_4", "2024-04-27", "2024-05-01"),
    ("quoc_khanh", "2024-08-31", "2024-09-03"),
    ("tet_dl", "2025-01-01", "2025-01-01"), ("tet", "2025-01-25", "2025-02-02"),
    ("gio_to", "2025-04-05", "2025-04-07"), ("le_30_4", "2025-04-30", "2025-05-04"),
    ("quoc_khanh", "2025-08-30", "2025-09-02"),
    ("tet_dl", "2026-01-01", "2026-01-04"), ("tet", "2026-02-14", "2026-02-22"),
    ("gio_to", "2026-04-25", "2026-04-27"), ("le_30_4", "2026-04-30", "2026-05-03"),
    ("quoc_khanh", "2026-08-29", "2026-09-02"),
]
SMALL_AMP = {"tet_dl": 0.35, "gio_to": 0.30, "le_30_4": 0.60, "quoc_khanh": 0.50}
TET_AMP = {"out": 2.2, "in": 1.9, "cross": 1.0}
LEVEL_JUMPS = {2: [("2025-06-01", 0.85)], 5: [("2024-09-01", 1.15)], 3: [("2025-11-15", 1.10)],
               1: [("2026-03-01", 0.92)]}

# weekday multipliers Mon..Sun, normalised to mean 1 below
WEEK = {"out": [0.92, 0.90, 0.92, 0.98, 1.22, 1.18, 0.88],
        "in": [1.05, 0.90, 0.90, 0.92, 0.95, 1.00, 1.30],
        "cross": [0.95, 0.92, 0.92, 0.95, 1.12, 1.05, 1.12]}
WEEK_HOUR = {("out", 4, 18): 1.10, ("out", 5, 6): 1.08, ("in", 6, 18): 1.10, ("in", 6, 12): 1.10,
             ("in", 0, 6): 1.12}


def bump(dates, month, day, width, amp):
    """amp * gaussian of the circular distance (days) to month/day."""
    out = np.zeros(len(dates))
    for y in (-1, 0, 1):
        centre = pd.to_datetime([f"{d.year + y}-{month:02d}-{day:02d}" for d in dates])
        dist = (dates - centre).days.to_numpy(float)
        out += amp * np.exp(-dist ** 2 / (2 * width ** 2))
    return out


def season_log(dates, route):
    s = bump(dates, 12, 20, 12, 0.05)                       # year-end build-up, everyone
    if route == 2:
        s += bump(dates, 7, 5, 30, 0.20) + bump(dates, 12, 28, 15, 0.10)   # Đà Lạt summer + flower season
    if route in (1, 4):
        s += bump(dates, 7, 1, 30, 0.10)                    # summer at the coast
    if route in (4, 5):
        s += bump(dates, 8, 30, 8, 0.12)                    # students back to the city
    if route in (1, 2):
        s += bump(dates, 6, 5, 8, 0.08)                     # students home after the school year
    if route == 3:
        s += bump(dates, 10, 25, 25, -0.15)                 # central rainy season
    for tet in TET.values():                                # post-Tết lull, tied to the lunar date
        dist = (dates - (tet + pd.Timedelta(days=30))).days.to_numpy(float)
        s += -0.10 * np.exp(-dist ** 2 / (2 * 10 ** 2))
    return s


def holiday_log(dates, direction, hour, rng):
    """log multiplier from every official break; size jittered per occurrence (same draw for all slots
    of a route, because rng is seeded per route by the caller)."""
    eff = np.zeros(len(dates))
    pos = {d: i for i, d in enumerate(dates)}

    def mul(d, m):
        if d in pos and m > 0:
            eff[pos[d]] += np.log(m)

    for name, s, e in BREAKS:
        s, e = T(s), T(e)
        jit = rng.uniform(0.85, 1.15)
        day = pd.Timedelta(days=1)
        if name == "tet":
            tet = TET[s.year]
            A = TET_AMP[direction] * jit
            for k in range(-9, 1):                          # k = days relative to the first day off
                d = s + k * day
                if direction == "out":
                    peak = A * np.exp(-(k + 1) ** 2 / (2 * 2.2 ** 2)) * (1.15 if hour == 18 and k == -1 else 1)
                    mul(d, 1 + peak)
                elif direction == "in":
                    mul(d, 0.75)
                else:
                    mul(d, 1 + A * np.exp(-(k + 1) ** 2 / (2 * 1.5 ** 2)))
            for d in pd.date_range(s + day, e):
                if tet <= d <= tet + 2 * day:
                    mul(d, 0.25 if direction != "cross" else 0.3)
                elif d < tet:
                    mul(d, 0.5 if direction == "out" else 0.6)
                elif direction == "in":
                    k = (d - e).days
                    mul(d, 1 + A * np.exp(-k ** 2 / (2 * 1.8 ** 2)))
                elif direction == "cross":
                    k = (d - e).days
                    mul(d, 1 + 0.8 * A * np.exp(-k ** 2 / 2))
                else:
                    mul(d, 0.7)
            for k in range(1, 11):                          # k = days after the last day off
                d = e + k * day
                if direction == "out":
                    mul(d, 0.85)
                elif direction == "in":
                    mul(d, 1 + A * np.exp(-k ** 2 / (2 * 1.8 ** 2)))
                else:
                    mul(d, 1 + 0.8 * A * np.exp(-k ** 2 / 2))
            if direction == "in":                           # second, smaller wave after Rằm tháng Giêng
                for k in range(-4, 5):
                    mul(tet + (15 + k) * day, 1 + 0.3 * jit * np.exp(-k ** 2 / (2 * 1.5 ** 2)))
            continue
        A = SMALL_AMP[name] * jit
        middle = [d for d in pd.date_range(s + day, e - day)]
        if direction == "out":
            mul(s - day, 1 + A * (1.3 if hour == 18 else 0.8 if hour == 6 else 1.0))
            mul(s, 1 + 0.8 * A * (1.3 if hour == 6 else 0.8))
            for d in middle:
                mul(d, 0.95)
            if e > s:
                mul(e, 0.7)
            mul(e + day, 0.85)
        elif direction == "in":
            mul(s - day, 0.8)
            mul(s, 0.75)
            for d in middle:
                mul(d, 0.95)
            if e > s:
                mul(e - day, 1 + 0.5 * A)
            mul(e, 1 + A * (1.2 if hour in (12, 18) else 0.8))
            mul(e + day, 1 + 0.5 * A * (1.0 if hour == 6 else 0.3))
        else:
            mul(s - day, 1 + 0.5 * A)
            mul(e, 1 + 0.4 * A)
    return eff


def storms(dates, route, rng):
    """Unpredictable demand cuts: (multiplier per date, 0/1 flag). Central route 3 in Sep-Nov, the Đà Lạt
    pass (route 2) sometimes closes, any route about once a year."""
    m = np.ones(len(dates))
    for year in range(dates[0].year, dates[-1].year + 1):
        events = []
        if route == 3:
            events += [(T(f"{year}-09-01") + pd.Timedelta(days=int(rng.integers(0, 91))), rng.uniform(0.1, 0.5))
                       for _ in range(rng.poisson(4))]
        if route == 2 and rng.random() < 0.6:
            events.append((T(f"{year}-08-15") + pd.Timedelta(days=int(rng.integers(0, 90))), 0.05))
        if rng.random() < 0.8:
            events.append((T(f"{year}-01-01") + pd.Timedelta(days=int(rng.integers(0, 365))), rng.uniform(0.4, 0.7)))
        for d0, cut in events:
            for k in range(int(rng.integers(1, 4))):
                d = d0 + pd.Timedelta(days=k)
                if dates[0] <= d <= dates[-1]:
                    m[(d - dates[0]).days] = cut
    return m, (m < 1).astype(int)


def operator_extra(dates, direction, rng):
    """Extra trips the historical operator ran by its own rule of thumb (it knew the break dates)."""
    extra = np.zeros(len(dates), int)
    pos = {d: i for i, d in enumerate(dates)}
    day = pd.Timedelta(days=1)

    def add(d, n):
        if d in pos:
            extra[pos[d]] = max(extra[pos[d]], n)

    for name, s, e in BREAKS:
        s, e = T(s), T(e)
        if name == "tet":
            if direction == "out":
                for k in range(-5, 1):
                    add(s + k * day, 2 if k in (-2, -1) else 1)
            elif direction == "in":
                for k in range(-1, 4):
                    add(e + k * day, 2 if k in (-1, 0) else 1)
            else:
                for d in (s - 2 * day, s - day, e):
                    add(d, 1)
        elif direction == "out" and rng.random() < 0.7:
            add(s - day, 1)
        elif direction == "in" and rng.random() < 0.7:
            add(e, 1)
    return extra


def generate(seed=7):
    dates = pd.date_range(START, END)
    years = (dates - START).days.to_numpy(float) / 365.25
    rows = []
    for route, (direction, cap, occ) in ROUTES.items():
        rng = np.random.default_rng(seed * 100 + route)
        walk = np.cumsum(rng.normal(0, WALK_SD, len(dates)))
        level = GROWTH * years + walk
        for when, f in LEVEL_JUMPS.get(route, []):
            level += np.log(f) * (dates >= T(when))
        season = season_log(dates, route)
        cut, storm = storms(dates, route, rng)
        week = np.array(WEEK[direction]) / np.mean(WEEK[direction])
        hol_rng_state = rng.bit_generator.state
        extra = operator_extra(dates, direction, rng)
        for hi, hour in enumerate(HOURS):
            rng.bit_generator.state = hol_rng_state         # same break sizes for every hour of the route
            hol = holiday_log(dates, direction, hour, rng)
            wk = np.array([week[d.dayofweek] * WEEK_HOUR.get((direction, d.dayofweek, hour), 1.0) for d in dates])
            mu = cap * occ[hi] * wk * np.exp(level + season + hol) * cut
            hrng = np.random.default_rng(seed * 1000 + route * 10 + hi)
            demand = hrng.negative_binomial(NB_SIZE, NB_SIZE / (NB_SIZE + mu))
            swap = hrng.random(len(dates)) < 0.08               # bus rotation: another size that day
            first = np.where(swap, hrng.choice(BUS_SIZES, len(dates)), cap)
            seats = first + extra * cap
            sales = np.minimum(demand, seats)
            for i, d in enumerate(dates):
                rows.append((d.date(), route, hour, f"r{route}_{hour:02d}", GROUP[direction], cap,
                             1 + extra[i], seats[i], demand[i], sales[i], storm[i], mu[i]))
    return pd.DataFrame(rows, columns=["date", "route", "hour", "series", "group", "cap", "trips", "seats",
                                       "demand", "sales", "storm", "mu"])


def main():
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 7
    df = generate(seed)
    df.to_csv(sys.argv[1], index=False)
    sold_out = (df["sales"] >= df["seats"]).mean()
    print(f"wrote {sys.argv[1]} rows={len(df)} seed={seed} sold_out={sold_out:.1%} storm_days={df['storm'].mean():.1%}")
    print(df.groupby("series").apply(lambda g: pd.Series({
        "mean_demand": g["demand"].mean(), "cap": g["cap"].iloc[0], "sold_out": (g["sales"] >= g["seats"]).mean(),
        "lost": 1 - g["sales"].sum() / g["demand"].sum()}), include_groups=False).round(3).to_string())


if __name__ == "__main__":
    main()
