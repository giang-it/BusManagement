# Step 0 (read-only research): forecasting methods compared on daily count series.
# Every method sees only observations dated <= origin; holiday dates are calendar facts known in advance.
#
# Data layout used throughout: Y is a DataFrame (daily DatetimeIndex x series), values = daily counts,
# NaN = no observation. "groups" maps each series column position to a pooling group
# (HK: direction Arrival/Departure; synthetic: route direction).
import logging
import numpy as np
import pandas as pd

DAY = pd.Timedelta(days=1)


# ----------------------------------------------------------------------------- calendar
def holiday_frame(windows, start, end):
    """windows: list of (name, [anchor Timestamps], lo, hi). Returns long frame (date, name, offset)."""
    rows = []
    for name, anchors, lo, hi in windows:
        for a in anchors:
            for k in range(lo, hi + 1):
                d = a + k * DAY
                if start <= d <= end:
                    rows.append((d, name, k))
    return pd.DataFrame(rows, columns=["date", "name", "offset"])


def holiday_blocks(hol):
    """Map every holiday-window date to the first date of its contiguous block of holiday dates."""
    dates = sorted(set(hol["date"]))
    block_start, start, prev = {}, None, None
    for d in dates:
        if prev is None or d - prev > DAY:
            start = d
        block_start[d] = start
        prev = d
    return block_start


def active_keys(hol):
    """date -> list of (name, offset) active on that date."""
    out = {}
    for d, n, k in hol[["date", "name", "offset"]].itertuples(index=False):
        out.setdefault(d, []).append((n, int(k)))
    return out


# ----------------------------------------------------------------------------- seasonal naive tables
def sn_table(Y, exclude_dates=(), n=4):
    """T[p, w, s] = mean of the last n observations of weekday w dated <= Y.index[p], skipping excluded dates."""
    dates = Y.index
    vals = Y.to_numpy(float)
    T = np.full((len(dates), 7, vals.shape[1]), np.nan)
    excl = dates.isin(pd.DatetimeIndex(list(exclude_dates))) if len(exclude_dates) else np.zeros(len(dates), bool)
    for w in range(7):
        idx = np.where((dates.dayofweek == w) & ~excl)[0]
        if len(idx) == 0:
            continue
        sub = pd.DataFrame(vals[idx]).rolling(n, min_periods=1).mean().to_numpy()
        pos = np.searchsorted(idx, np.arange(len(dates)), side="right") - 1
        ok = pos >= 0
        T[ok, w, :] = sub[pos[ok]]
    return T


def table_forecast(T, Y, origin, H):
    p = Y.index.get_loc(origin)
    targets = pd.date_range(origin + DAY, periods=H)
    return np.stack([T[p, t.dayofweek, :] for t in targets])  # H x S


# ----------------------------------------------------------------------------- current app model (analogue)
def lr_current(Y, origin, H, train_start):
    """ForecastService on counts: pooled day-of-week factors on mean-normalised values,
    then per-series least-squares line on de-seasonalised values over the whole history, extrapolated."""
    tr = Y.loc[train_start:origin]
    norm = tr / tr.mean()
    stacked = norm.stack()
    dow = stacked.index.get_level_values(0).dayofweek
    overall = stacked.mean()
    f = np.array([stacked[dow == w].mean() / overall for w in range(7)])
    targets = pd.date_range(origin + DAY, periods=H)
    out = np.full((H, Y.shape[1]), np.nan)
    for j, col in enumerate(Y.columns):
        s = tr[col].dropna()
        x = (s.index - s.index[0]).days.to_numpy(float)
        yd = s.to_numpy() / f[s.index.dayofweek]
        b, a = np.polyfit(x, yd, 1)
        xt = (targets - s.index[0]).days.to_numpy(float)
        out[:, j] = np.maximum(0.0, (a + b * xt) * f[targets.dayofweek])
    return out


# ----------------------------------------------------------------------------- Holt-Winters (damped, on log)
def hw_task(args):
    ylog, H = args
    import warnings
    from statsmodels.tsa.holtwinters import ExponentialSmoothing
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        m = ExponentialSmoothing(ylog, trend="add", damped_trend=True, seasonal="add",
                                 seasonal_periods=7, initialization_method="estimated").fit()
    return np.exp(m.forecast(H))


# ----------------------------------------------------------------------------- Prophet (per series, on log)
def prophet_task(args):
    ds, ylog, holidays_df, H = args
    logging.getLogger("cmdstanpy").disabled = True
    logging.getLogger("prophet").setLevel(logging.ERROR)
    from prophet import Prophet
    m = Prophet(holidays=holidays_df, yearly_seasonality=True, weekly_seasonality=True, daily_seasonality=False)
    m.fit(pd.DataFrame({"ds": ds, "y": ylog}))
    fut = pd.DataFrame({"ds": pd.date_range(ds[-1] + DAY, periods=H)})
    return np.exp(m.predict(fut)["yhat"].to_numpy())


def prophet_holidays(windows):
    rows = []
    for name, anchors, lo, hi in windows:
        for a in anchors:
            rows.append((name, a, lo, hi))
    return pd.DataFrame(rows, columns=["holiday", "ds", "lower_window", "upper_window"])


# ----------------------------------------------------------------------------- ridge Poisson IRLS
def poisson_irls(X, y, offset, ridge, iters=50, tol=1e-9, beta0=None):
    """Penalised Poisson regression, log link: maximise sum(y*eta - exp(eta)) - 0.5*sum(ridge*beta^2),
    eta = offset + X beta. Plain iteratively re-weighted least squares with step halving.
    Works for non-integer y (quasi-Poisson point estimates). beta0: warm start (EM rounds)."""
    beta = np.zeros(X.shape[1]) if beta0 is None else beta0.copy()

    def objective(b):
        eta = offset + X @ b
        return np.sum(y * eta - np.exp(eta)) - 0.5 * np.sum(ridge * b * b)

    sparse = hasattr(X, "tocsr")
    cur = objective(beta)
    for _ in range(iters):
        eta = offset + X @ beta
        mu = np.exp(eta)
        z = (eta - offset) + (y - mu) / mu
        if sparse:
            A = (X.T @ X.multiply(mu[:, None])).toarray() + np.diag(ridge)
        else:
            A = X.T @ (X * mu[:, None]) + np.diag(ridge)
        new = np.linalg.solve(A, X.T @ (mu * z))
        step = 1.0
        while True:
            cand = beta + step * (new - beta)
            val = objective(cand)
            if val >= cur - 1e-12 or step < 1e-4:
                break
            step /= 2
        done = abs(val - cur) <= tol * (abs(cur) + 1)
        beta, cur = cand, val
        if done:
            break
    return beta


# ----------------------------------------------------------------------------- G2: baseline x learned holiday lift
class LiftModel:
    """Normal days: clean seasonal naive (mean of the last 4 same weekdays, holiday days skipped).
    Holiday-window days: that baseline x exp(pooled-by-group effect + series deviation), where every
    (holiday, day offset) effect is learned from past occurrences by ridge Poisson regression with the
    baseline as offset. The series deviation is shrunk toward the pooled effect (ridge lam)."""

    def __init__(self, Y, hol, groups, train_start, lam=2.0):
        self.Y, self.groups, self.lam = Y, np.asarray(groups), lam
        self.keys = active_keys(hol)
        hol_dates = set(hol["date"])
        self.T = sn_table(Y, exclude_dates=hol_dates, n=4)
        block = holiday_blocks(hol)
        self.scale = Y.loc[train_start:].mean().to_numpy()
        rows = []
        vals = Y.to_numpy(float)
        for d, ks in self.keys.items():
            if d < pd.Timestamp(train_start) or d not in Y.index:
                continue
            ref = block[d] - DAY
            if ref not in Y.index:
                continue
            p_ref, p = Y.index.get_loc(ref), Y.index.get_loc(d)
            base = self.T[p_ref, d.dayofweek, :]
            for s in range(vals.shape[1]):
                if np.isfinite(vals[p, s]) and np.isfinite(base[s]) and base[s] > 0:
                    rows.append((d, s, vals[p, s] / self.scale[s], base[s] / self.scale[s], ks))
        self.rows = rows

    def fit(self, origin):
        rows = [r for r in self.rows if r[0] <= origin]
        cols = {}
        for _, s, _, _, ks in rows:
            for n, k in ks:
                cols.setdefault(("p", n, k, self.groups[s]), len(cols))
                cols.setdefault(("s", n, k, s), len(cols))
        from scipy.sparse import csr_matrix
        ri, ci = [], []
        for i, (_, s, _, _, ks) in enumerate(rows):
            for n, k in ks:
                ri += [i, i]
                ci += [cols[("p", n, k, self.groups[s])], cols[("s", n, k, s)]]
        X = csr_matrix((np.ones(len(ri)), (ri, ci)), shape=(len(rows), len(cols)))
        y = np.array([r[2] for r in rows])
        off = np.log(np.array([r[3] for r in rows]))
        ridge = np.array([1e-6 if c[0] == "p" else self.lam for c in cols])
        beta = poisson_irls(X, y, off, ridge) if len(cols) else np.zeros(0)
        self.coef = {c: beta[j] for c, j in cols.items()}
        return self

    def forecast(self, origin, H):
        self.fit(origin)
        base = table_forecast(self.T, self.Y, origin, H)
        out = base.copy()
        for h, t in enumerate(pd.date_range(origin + DAY, periods=H)):
            for s in range(base.shape[1]):
                eff = sum(self.coef.get(("p", n, k, self.groups[s]), 0.0) + self.coef.get(("s", n, k, s), 0.0)
                          for n, k in self.keys.get(t, []))
                out[h, s] = base[h, s] * np.exp(eff)
        return out

    def factors(self):
        """Learned pooled multipliers per (holiday, offset, group) after the last fit."""
        return {c[1:]: np.exp(v) for c, v in self.coef.items() if c[0] == "p"}


# ----------------------------------------------------------------------------- G1: one calendar GLM for everything
def calendar_glm_task(args):
    """Pooled Poisson GLM on mean-normalised counts:
    log mu = series level + series x weekday + yearly Fourier (per group) + series trend (hinges every
    182 days, FROZEN at the origin when forecasting) + (holiday, offset) effects pooled per group.
    Forecast x level correction = sum(actual)/sum(fitted) over the last 28 non-holiday days."""
    Yv, dates, groups, keys, origin_pos, H, train_pos = args
    S = Yv.shape[1]
    G = int(max(groups)) + 1
    K = 3
    t0 = dates[train_pos]
    origin = dates[origin_pos]
    knots = list(pd.date_range(t0 + pd.Timedelta(days=182), origin - pd.Timedelta(days=60), freq="182D"))
    hol_cols = {}
    for p in range(train_pos, origin_pos + 1):
        for n, k in keys.get(dates[p], []):
            for g in range(G):
                hol_cols.setdefault((n, k, g), len(hol_cols))
    nT = 1 + len(knots)
    n_cols = S + 6 * S + 2 * K * G + nT * S + len(hol_cols)
    scale = np.nanmean(Yv[train_pos:origin_pos + 1], axis=0)

    def design(day_list, s_list, clamp):
        X = np.zeros((len(day_list), n_cols))
        for i, (d, s) in enumerate(zip(day_list, s_list)):
            g = groups[s]
            X[i, s] = 1
            if d.dayofweek > 0:
                X[i, S + 6 * s + d.dayofweek - 1] = 1
            doy = 2 * np.pi * d.dayofyear / 365.25
            base = S + 6 * S + 2 * K * g
            for k in range(K):
                X[i, base + 2 * k] = np.sin((k + 1) * doy)
                X[i, base + 2 * k + 1] = np.cos((k + 1) * doy)
            dt = min(d, origin) if clamp else d
            tb = S + 6 * S + 2 * K * G + nT * s
            X[i, tb] = (dt - t0).days / 365.25
            for j, kn in enumerate(knots):
                X[i, tb + 1 + j] = max(0.0, (dt - kn).days / 365.25)
            hb = S + 6 * S + 2 * K * G + nT * S
            for n, k in keys.get(d, []):
                c = hol_cols.get((n, k, g))
                if c is not None:
                    X[i, hb + c] = 1
        return X

    days, ss, ys = [], [], []
    for p in range(train_pos, origin_pos + 1):
        for s in range(S):
            v = Yv[p, s]
            if np.isfinite(v):
                days.append(dates[p]); ss.append(s); ys.append(v / scale[s])
    X = design(days, ss, clamp=False)
    y = np.array(ys)
    ridge = np.full(n_cols, 1e-6)
    beta = poisson_irls(X, y, np.zeros(len(y)), ridge)
    fitted = np.exp(X @ beta)
    # level correction from the last 28 non-holiday days
    corr = np.ones(S)
    recent = origin - pd.Timedelta(days=27)
    for s in range(S):
        m = np.array([(d >= recent and s_ == s and not keys.get(d)) for d, s_ in zip(days, ss)])
        if m.any():
            corr[s] = y[m].sum() / fitted[m].sum()
    targets = pd.date_range(origin + DAY, periods=H)
    out = np.zeros((H, S))
    for s in range(S):
        Xf = design(list(targets), [s] * H, clamp=True)
        out[:, s] = np.exp(Xf @ beta) * corr[s] * scale[s]
    return out


# ----------------------------------------------------------------------------- LightGBM global model
def lgb_features(Y, T4, T1, hol_names, keys, origin_positions, H, groups, with_target):
    """One row per (origin, series, horizon): target = log(y / clean-SN4 baseline as of origin)."""
    vals = Y.to_numpy(float)
    dates = Y.index
    lvl7 = pd.DataFrame(vals).rolling(7, min_periods=4).mean().to_numpy()
    lvl28 = pd.DataFrame(vals).rolling(28, min_periods=14).mean().to_numpy()
    feats, target, base_out, meta = [], [], [], []
    for p in origin_positions:
        for h in range(1, H + 1):
            q = p + h
            if with_target and q >= len(dates):
                continue
            t = dates[p] + h * DAY
            w = t.dayofweek
            hol = {n: -99 for n in hol_names}
            for n, k in keys.get(t, []):
                hol[n] = k
            doy = 2 * np.pi * t.dayofyear / 365.25
            for s in range(vals.shape[1]):
                b = T4[p, w, s]
                b1 = T1[p, w, s]
                if not (np.isfinite(b) and b > 0):
                    continue
                if with_target:
                    yv = vals[q, s]
                    if not (np.isfinite(yv) and yv > 0):
                        continue
                    target.append(np.log(yv / b))
                mom = np.log(lvl7[p, s] / lvl28[p, s]) if lvl28[p, s] > 0 and lvl7[p, s] > 0 else 0.0
                feats.append([s, groups[s], h, w, np.sin(doy), np.cos(doy),
                              np.log(b1 / b) if b1 and b1 > 0 else 0.0, mom] + [hol[n] for n in hol_names])
                base_out.append(b)
                meta.append((p, h, s))
    cols = ["series", "group", "h", "dow", "doy_sin", "doy_cos", "sn1_vs_sn4", "momentum"] + [f"hol_{n}" for n in hol_names]
    return pd.DataFrame(feats, columns=cols), np.array(target), np.array(base_out), meta


def lgb_train(Xdf, y):
    import lightgbm as lgb
    params = dict(objective="regression", learning_rate=0.05, num_leaves=31, min_data_in_leaf=40,
                  feature_fraction=0.9, bagging_fraction=0.8, bagging_freq=1, lambda_l2=1.0,
                  verbose=-1, num_threads=8, seed=7)
    ds = lgb.Dataset(Xdf, y, categorical_feature=["series", "group", "dow"])
    return lgb.train(params, ds, num_boost_round=400)


# ----------------------------------------------------------------------------- the app itself, on occupancy
def app_rate_current(R, origin, H, train_start):
    """ForecastService.buildForecast() on per-slot occupancy R = sales / seats (forecast_replica.py logic):
    day-of-week factors pooled over every series on the raw rates, a per-series least-squares line on the
    de-seasonalised rates over the whole history, extrapolated and clamped to [0, 1]."""
    tr = R.loc[train_start:origin]
    vals = tr.to_numpy(float)
    dow = tr.index.dayofweek.to_numpy()
    overall = np.nanmean(vals)
    f = np.array([np.nanmean(vals[dow == w]) / overall for w in range(7)])
    targets = pd.date_range(origin + DAY, periods=H)
    out = np.full((H, R.shape[1]), np.nan)
    for j, col in enumerate(R.columns):
        s = tr[col].dropna()
        x = (s.index - s.index[0]).days.to_numpy(float)
        b, a = np.polyfit(x, s.to_numpy() / f[s.index.dayofweek], 1)
        xt = (targets - s.index[0]).days.to_numpy(float)
        out[:, j] = np.clip((a + b * xt) * f[targets.dayofweek], 0.0, 1.0)
    return out


# ----------------------------------------------------------------------------- G1 for slot demand, with EM
def nb_mean_at_least(mu, c, size):
    """E[D | D >= c] for D ~ negative binomial(mean mu, size): uses d * NB(d; r, p) = mu * NB(d - 1; r + 1, p)."""
    from scipy.stats import nbinom
    p = size / (size + mu)
    den = nbinom.sf(c - 1, size, p)
    num = nbinom.sf(c - 2, size + 1, p)
    out = np.where(den > 1e-12, mu * num / np.maximum(den, 1e-300), c)
    return np.maximum(out, c)


def slot_glm_task(args):
    """G1 on slot demand (experiment B). Pooled Poisson calendar GLM on mean-normalised counts:
    log mu = series level + series x weekday + series yearly Fourier (K=3) + series trend (hinges every
    182 days, FROZEN at the origin) + (holiday, offset) effects pooled per group + a series deviation
    shrunk toward them (ridge lam). Forecast x level correction over the last 28 non-holiday days.
    em_iters > 0: EM unconstraining -- a sold-out point (sales >= seats) only says demand >= seats, so it is
    replaced by E[D | D >= seats] under a negative binomial with the current fitted mean (size re-estimated
    from the uncensored points each round), and the GLM is refitted; repeated em_iters times.
    Optional 11th arg K = number of yearly Fourier pairs (default 3; 0 = no yearly season term)."""
    Yv, Cv, dates, groups, keys, origin_pos, H, train_pos, em_iters, lam, *opt = args
    from scipy.sparse import csr_matrix
    groups = np.asarray(groups)
    S, G, K = Yv.shape[1], int(groups.max()) + 1, (opt[0] if opt else 3)
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
    ridge[o_hs:] = lam
    zero = np.zeros(len(y))
    ycur = y.astype(float).copy()
    beta = None
    for it in range(em_iters + 1):
        beta = poisson_irls(X, ycur / sc, zero, ridge, beta0=beta)
        if it == em_iters:
            break
        mu = np.exp(X @ beta) * sc
        u = ~cens
        inv = np.sum((y[u] - mu[u]) ** 2 - mu[u]) / np.sum(mu[u] ** 2)
        size = float(np.clip(1.0 / max(inv, 1e-4), 2.0, 1e4))
        ycur[cens] = nb_mean_at_least(mu[cens], cap[cens], size)
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
