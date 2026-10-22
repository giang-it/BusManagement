# GĐ6 of the Demand Forecast redesign (decision QĐ-17): the numbers of the GĐ6 discussion
# (forecast_discussion.md §16) — what an extra trip is worth under three revenue rules, a normal week and a
# Tết week, and the What-if table in demand units. Research only — nothing here touches the app or the DB.
#
# Forecasts: src/test/resources/forecast/reference_forecast.csv (the Python reference the Java model matches),
# origin 21/10/2026 for the week 23-29/10/2026 and origin 25/01/2026 for the Tết week 08-14/02/2026.
# Prices, distances, durations, standard buses and cost parameters: the real DB on 2026-10-22 (read-only).
# The negative binomial size is 82.2, the size of the 21/10/2026 fit, used for BOTH weeks to keep the
# illustration simple (the app uses each fit's own size).
#
# Three revenue rules for an extra trip (a standard bus of s seats):
#   bridge — GĐ5's interim rule: round(min(1, d/s) * s) passengers, i.e. as if the extra trip ran full;
#   R1     — deterministic overflow: min(max(d - s*k, 0), s) for the k-th extra trip;
#   R2     — expected overflow (chosen, G6-1): EO(s*k) - EO(s*(k+1)), EO(c) = E[(D - c)+], D ~ NB(d, size).
# Usage: python -I discussion_numbers.py <src/test/resources/forecast directory>
import csv
import math
import sys
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
STD = {1: 22, 2: 22, 3: 40, 4: 40, 5: 22}
PRICE = {1: 120_000, 2: 300_000, 3: 100_000, 4: 120_000, 5: 180_000}
KM = {1: 120, 2: 300, 3: 100, 4: 120, 5: 180}
HOURS = {1: 2.0, 2: 6.0, 3: 2.0, 4: 1.5, 5: 3.5}
FUEL, WAGE = 6_000, 50_000
SIZE = 82.2


def cost(route):
    return FUEL * KM[route] + WAGE * HOURS[route]


def eo(mu, c, size=SIZE):
    """E[(D - c)+] for D ~ NB(mean mu, size): (mu - c) + sum_{k<=c} (c - k) pmf(k)."""
    p = size / (size + mu)
    pmf = p ** size
    below = 0.0
    for k in range(c + 1):
        below += (c - k) * pmf
        pmf *= (k + size) / (k + 1) * (1 - p)
    return (mu - c) + below


def trips_needed(d, s):
    return 1 if d / s <= 0.9 else max(2, math.ceil(d / (0.9 * s)))


def extras(rows, factor=1.0):
    """Every extra trip a week needs: (date, route, hour, demand, k, n, cost, revenue under each rule)."""
    out = []
    for (day, route, hour), d in sorted(rows.items()):
        d *= factor
        s = STD[route]
        n = trips_needed(d, s) - 1
        for k in range(1, n + 1):
            out.append({
                "date": day, "route": route, "hour": hour, "d": d, "k": k, "n": n, "cost": cost(route),
                "bridge": round(min(1.0, d / s) * s) * PRICE[route],
                "R1": min(max(d - s * k, 0.0), s) * PRICE[route],
                "R2": (eo(d, s * k) - eo(d, s * (k + 1))) * PRICE[route],
            })
    return out


def week(path, origin, first):
    rows = {}
    days = {(first + timedelta(days=i)).isoformat() for i in range(7)}
    for r in csv.DictReader(open(path, encoding="utf-8")):
        if r["origin"] == origin and r["date"] in days:
            rows[(r["date"], int(r["route_id"]), int(r["hour"]))] = float(r["expected"])
    return rows


def summary(title, trips):
    print(f"\n{title}: {len({(t['date'], t['route'], t['hour']) for t in trips})} hot slots, {len(trips)} extra trips"
          f" (trips needed per slot: {dict(sorted(Counter(t['n'] + 1 for t in trips if t['k'] == 1).items()))})")
    for rule in ("bridge", "R1", "R2"):
        profits = [t[rule] - t["cost"] for t in trips]
        print(f"  {rule:6s}: {sum(p > 0 for p in profits):3d} profitable, {sum(p < 0 for p in profits):3d} loss,"
              f" week total {sum(profits) / 1e6:+7.1f} M, profitable only {sum(p for p in profits if p > 0) / 1e6:+6.1f} M")


def show(trips, day, route, hour):
    for t in trips:
        if (t["date"], t["route"], t["hour"]) == (day, route, hour):
            carried = t["R2"] / PRICE[route]
            print(f"  {day} route {route} {hour:02d}:00, demand {t['d']:.1f} / {STD[route]} seats, extra {t['k']}/{t['n']}:"
                  f" bridge {(t['bridge'] - t['cost']) / 1e6:+.2f} M | R1 {(t['R1'] - t['cost']) / 1e6:+.2f} M"
                  f" | R2 carries {carried:.1f} -> {(t['R2'] - t['cost']) / 1e6:+.2f} M")


def main():
    path = Path(sys.argv[1]) / "reference_forecast.csv"
    normal = week(path, "2026-10-21", date(2026, 10, 23))
    tet = week(path, "2026-01-25", date(2026, 2, 8))

    trips = extras(normal)
    summary("Week 23-29/10/2026 (origin 21/10)", trips)
    ratio = [t["d"] / STD[t["route"]] for t in trips]
    print(f"  slots at 90-100% of a standard bus: {sum(r <= 1.0 for r in ratio)}; above: {sum(r > 1.0 for r in ratio)}")
    print(f"  break-even passengers per extra trip: "
          f"{', '.join(f'route {r} {cost(r) / PRICE[r]:.1f}' for r in sorted(STD))}")
    for key in (("2026-10-23", 1, 6), ("2026-10-25", 4, 18), ("2026-10-25", 5, 18), ("2026-10-27", 3, 18)):
        show(trips, *key)

    trips = extras(tet)
    summary("Tết week 08-14/02/2026 (origin 25/01)", trips)
    for key in (("2026-02-13", 1, 6), ("2026-02-10", 2, 6), ("2026-02-11", 1, 6), ("2026-02-11", 2, 6)):
        show(trips, *key)

    print("\nWhat-if in demand units, week 23-29/10 (R2, every extra trip covered):")
    for factor in (0.7, 1.0, 1.3, 1.5):
        trips = extras(normal, factor)
        profits = [t["R2"] - t["cost"] for t in trips]
        clamped = sum(min(1.0, d / STD[r]) * factor > 0.9 for (_, r, _), d in normal.items())
        print(f"  x{factor:.1f}: {len(trips):3d} extra trips (GĐ5 bridge counted {clamped:3d} hot slots),"
              f" {sum(p > 0 for p in profits):2d} profitable, all {sum(profits) / 1e6:+6.1f} M,"
              f" profitable only {sum(p for p in profits if p > 0) / 1e6:+5.1f} M")


if __name__ == "__main__":
    main()
