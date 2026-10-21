# Cross-check for the lunar table in src/main/java/giang/com/BusManagement/domain/HolidayCalendar.java
# (GĐ1, decision QĐ-9 in docs/development/forecast_decisions.md). Research only — not part of the app.
#
# An independent re-implementation of Ho Ngoc Duc's Vietnamese lunar calendar algorithm
# ("Âm lịch Việt Nam", informatik.uni-leipzig.de/~duc/amlich), computed in GMT+7. It prints, for each
# year, Tết (1/1 âm lịch) and Giỗ Tổ Hùng Vương (10/3 âm lịch, in the regular — not leap — 3rd month).
#
# Second source, checked by hand on 2026-10-21 for every row 2022–2033 (24 dates, all matching):
# the lunar-calendar tool of Báo Hà Tĩnh, https://baohatinh.vn/cong-cu/lich-am/{dd-MM-yyyy}
# (e.g. .../02-02-2030 → "ngày 1 tháng 1 năm Canh Tuất"; .../01-04-2031 → "ngày 10 tháng 3 năm Tân Hợi",
# the leap 3rd month starting 22/4/2031).
#
# Run (plain Python 3, no packages):  python -I holiday_calendar_check.py
# Output on 2026-10-21 (year, Tết, Giỗ Tổ, days between, leap month of that lunar year):
#   2022 2022-02-01 2022-04-10 68 None      2028 2028-01-26 2028-04-04 69 5
#   2023 2023-01-22 2023-04-29 97 2         2029 2029-02-13 2029-04-23 69 None
#   2024 2024-02-10 2024-04-18 68 None      2030 2030-02-02 2030-04-12 69 None
#   2025 2025-01-29 2025-04-07 68 6         2031 2031-01-23 2031-04-01 68 3
#   2026 2026-02-17 2026-04-26 68 None      2032 2032-02-11 2032-04-19 68 None
#   2027 2027-02-06 2027-04-16 69 None      2033 2033-01-31 2033-04-09 68 None
#   Tết 2030 in GMT+8 (China): 2030-02-03 — why a Chinese-calendar library would be wrong here.
import math
from datetime import date

PI = math.pi


def INT(x):
    return math.floor(x)


def jd_from_date(dd, mm, yy):
    a = INT((14 - mm) / 12)
    y = yy + 4800 - a
    m = mm + 12 * a - 3
    jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - INT(y / 100) + INT(y / 400) - 32045
    if jd < 2299161:
        jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - 32083
    return jd


def jd_to_date(jd):
    if jd > 2299160:
        a = jd + 32044
        b = INT((4 * a + 3) / 146097)
        c = a - INT((b * 146097) / 4)
    else:
        b = 0
        c = jd + 32082
    d = INT((4 * c + 3) / 1461)
    e = c - INT((1461 * d) / 4)
    m = INT((5 * e + 2) / 153)
    day = e - INT((153 * m + 2) / 5) + 1
    month = m + 3 - 12 * INT(m / 10)
    year = b * 100 + d - 4800 + INT(m / 10)
    return date(year, month, day)


def new_moon(k):
    T = k / 1236.85
    T2 = T * T
    T3 = T2 * T
    dr = PI / 180
    jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
    jd1 = jd1 + 0.00033 * math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr)
    M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3
    Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3
    F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3
    C1 = (0.1734 - 0.000393 * T) * math.sin(M * dr) + 0.0021 * math.sin(2 * dr * M)
    C1 = C1 - 0.4068 * math.sin(Mpr * dr) + 0.0161 * math.sin(dr * 2 * Mpr)
    C1 = C1 - 0.0004 * math.sin(dr * 3 * Mpr)
    C1 = C1 + 0.0104 * math.sin(dr * 2 * F) - 0.0051 * math.sin(dr * (M + Mpr))
    C1 = C1 - 0.0074 * math.sin(dr * (M - Mpr)) + 0.0004 * math.sin(dr * (2 * F + M))
    C1 = C1 - 0.0004 * math.sin(dr * (2 * F - M)) - 0.0006 * math.sin(dr * (2 * F + Mpr))
    C1 = C1 + 0.0010 * math.sin(dr * (2 * F - Mpr)) + 0.0005 * math.sin(dr * (2 * Mpr + M))
    if T < -11:
        deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
    else:
        deltat = -0.000278 + 0.000265 * T + 0.000262 * T2
    return jd1 + C1 - deltat


def sun_longitude(jdn):
    T = (jdn - 2451545.0) / 36525
    T2 = T * T
    dr = PI / 180
    M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2
    L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2
    DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * math.sin(dr * M)
    DL = DL + (0.019993 - 0.000101 * T) * math.sin(dr * 2 * M) + 0.000290 * math.sin(dr * 3 * M)
    L = (L0 + DL) * dr
    L = L - PI * 2 * INT(L / (PI * 2))
    return L


def get_sun_longitude(day_number, tz):
    return INT(sun_longitude(day_number - 0.5 - tz / 24) / PI * 6)


def get_new_moon_day(k, tz):
    return INT(new_moon(k) + 0.5 + tz / 24)


def get_lunar_month11(yy, tz):
    off = jd_from_date(31, 12, yy) - 2415021
    k = INT(off / 29.530588853)
    nm = get_new_moon_day(k, tz)
    if get_sun_longitude(nm, tz) >= 9:
        nm = get_new_moon_day(k - 1, tz)
    return nm


def get_leap_month_offset(a11, tz):
    k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5)
    i = 1
    arc = get_sun_longitude(get_new_moon_day(k + i, tz), tz)
    while True:
        last = arc
        i += 1
        arc = get_sun_longitude(get_new_moon_day(k + i, tz), tz)
        if not (arc != last and i < 14):
            break
    return i - 1


def lunar_to_solar(lday, lmonth, lyear, leap, tz=7):
    if lmonth < 11:
        a11 = get_lunar_month11(lyear - 1, tz)
        b11 = get_lunar_month11(lyear, tz)
    else:
        a11 = get_lunar_month11(lyear, tz)
        b11 = get_lunar_month11(lyear + 1, tz)
    k = INT(0.5 + (a11 - 2415021.076998695) / 29.530588853)
    off = lmonth - 11
    if off < 0:
        off += 12
    leap_month = None
    if b11 - a11 > 365:
        leap_off = get_leap_month_offset(a11, tz)
        leap_month = leap_off - 2
        if leap_month < 0:
            leap_month += 12
        if leap != 0 and lmonth != leap_month:
            return None, leap_month
        elif leap != 0 or off >= leap_off:
            off += 1
    month_start = get_new_moon_day(k + off, tz)
    return jd_to_date(month_start + lday - 1), leap_month


if __name__ == "__main__":
    print("year  tet(1/1 AL)   gio_to(10/3 AL)  days  leap_month")
    for y in range(2022, 2034):
        tet, _ = lunar_to_solar(1, 1, y, 0)
        gio, leap = lunar_to_solar(10, 3, y, 0)
        print(y, tet.isoformat(), gio.isoformat(), (gio - tet).days, leap)
    print("Tet 2030 GMT+7:", lunar_to_solar(1, 1, 2030, 0, 7)[0], " GMT+8:", lunar_to_solar(1, 1, 2030, 0, 8)[0])
