# GĐ6 of the Demand Forecast redesign (decision QĐ-17): the scipy REFERENCE for the expected overflow
# E[(D - c)+] that service/SlotDemandModel.expectedOverflow must reproduce — the number of passengers an
# extra trip can carry (G6-1). Research only — nothing here touches the app or the database.
#
# D ~ negative binomial with mean mu and size r (p = r / (r + mu)), the distribution the model's EM step
# already uses. The reference sums (d - c) * pmf(d) DIRECTLY over d > c with scipy's pmf, a different
# route from the Java code (which uses the identity sum_{d>=c} d pmf(d) = mu * P(D' >= c - 1)), and checks
# it against a third route, (mu - c) + sum_{d<c} (c - d) pmf(d).
#
# Writes src/test/resources/forecast/reference_overflow.csv: mu, c, size, expected.
# Usage: python -I overflow_reference.py <src/test/resources/forecast directory>
# Needs numpy/scipy (the step 0 venv).
import sys
from pathlib import Path

import numpy as np
from scipy.stats import nbinom

# mu: a quiet slot, the 23-29/10/2026 examples (20.1, 28.8, 36.2, 57.6) and Tết 2026 (43.2, 53.8), plus a
# far tail. c: 0, one seat, a standard bus (22, 40), two and three buses (44, 66, 80, 120).
MUS = [3.0, 20.1, 28.8, 36.2, 43.2, 53.8, 57.6, 120.0]
SEATS = [0, 1, 22, 40, 44, 62, 66, 80, 120]
SIZES = [2.0, 12.5, 82.2, 1e4]


def overflow_direct(mu, c, size):
    p = size / (size + mu)
    top = int(nbinom.isf(1e-18, size, p)) + 50  # far beyond any mass that matters
    d = np.arange(c + 1, max(top, c + 2))
    return float(np.sum((d - c) * nbinom.pmf(d, size, p)))


def overflow_below(mu, c, size):
    p = size / (size + mu)
    d = np.arange(0, c)
    return float((mu - c) + np.sum((c - d) * nbinom.pmf(d, size, p)))


def main():
    base = Path(sys.argv[1])
    rows = []
    worst_abs = 0.0
    worst_rel = 0.0
    for mu in MUS:
        for c in SEATS:
            for size in SIZES:
                a = overflow_direct(mu, c, size)
                b = overflow_below(mu, c, size)
                # The "below" route subtracts numbers of size c, so it loses ~1e-14 in ABSOLUTE terms: only
                # its relative error on values that are not tiny means anything.
                worst_abs = max(worst_abs, abs(a - b))
                if a > 1e-6:
                    worst_rel = max(worst_rel, abs(a - b) / a)
                rows.append((mu, c, size, repr(a)))
    with open(base / "reference_overflow.csv", "w", encoding="utf-8", newline="") as f:
        f.write("mu,c,size,expected\n")
        for mu, c, size, expected in rows:
            f.write(f"{mu},{c},{size},{expected}\n")
    print(f"wrote {len(rows)} values; the two scipy routes differ by at most {worst_abs:.1e} absolute, "
          f"{worst_rel:.1e} relative on values above 1e-6")


if __name__ == "__main__":
    main()
