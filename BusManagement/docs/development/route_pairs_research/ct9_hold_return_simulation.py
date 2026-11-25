# -*- coding: utf-8 -*-
"""Route pairs CT9 (QĐ-25, CM-29…CM-32) — mô phỏng cho luận văn: "về ngay" so với "giữ–về" trên lịch sử. CHỈ ĐỌC DB.

Lấy các chuyến tăng cường CÓ KHÁCH (tickets_sold > 0) của ba cặp quanh bến nhà trong một khoảng ngày của lịch sử,
rồi tính hai cách chạy chúng bằng ĐÚNG các luật của app (service/LoopEconomics):

  A. "Về ngay" — luật H1 của bộ sinh lịch sử (QĐ-21 (d)) và của màn Đề xuất trước CT9: mỗi chuyến thêm là một vòng
     riêng. Chuyến thêm rời bến nhà về bằng khung chiều ngược sớm nhất một tài xế lái được; chuyến thêm về bến nhà
     cần một lượt đưa xe lên trước. Lượt đi kèm tính là RỖNG (0 khách) — lịch sử cho thấy đúng vậy ở dịp lễ.
  B. "Giữ–về" (CT9): ghép một chuyến thêm đi với một chuyến thêm về sau đó của cùng cặp thành một vòng giữ xe khi
     lãi tăng thêm > 0 (LoopEconomics.pairHolds): chờ thêm = giờ đi của chuyến về − lần về ngay sớm nhất (CM-30),
     ≤ 7 ngày (CM-31); tiền chờ = (lương chờ × 1 người + phí xe nằm bến) × giờ ÷ 24 (CM-8); ghép đôi tăng nhiều nhất
     trước (CM-32). Chuyến thêm không ghép được chạy như A.

Thước đo: số lượt chạy, lượt rỗng và km rỗng, giờ chờ thêm và tiền chờ, doanh thu, chi phí, lãi, và số xe tăng cường
phải có cùng lúc (đỉnh số vòng chồng nhau theo thời gian — vòng giữ giữ xe lâu hơn). Doanh thu = vé đã bán × giá vé
của chính chuyến đó trong lịch sử. Chi phí một lượt = nhiên liệu/km × km + lương/giờ × giờ (tham số trong
cost_parameters; thiếu hai cột tiền chờ thì dùng mặc định 333.000 / 330.000 của CostParameterService).

Lưu ý khi đọc: lịch sử là dữ liệu MÔ PHỎNG (QĐ-21), và mô phỏng này không chọn lại xe hay tài xế — nó so hai luật
trên cùng tập chuyến thêm, như What-if (mức kế hoạch).

Dùng (Windows, từ bất kỳ đâu; mật khẩu MySQL qua biến môi trường MYSQL_PWD):
  python -I ct9_hold_return_simulation.py <schema> <từ ngày yyyy-mm-dd> <tới ngày yyyy-mm-dd> [--md <file kết quả>]
"""
import os
import subprocess
import sys
from datetime import datetime, timedelta

MYSQL = "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
HOURS = (6, 12, 18)  # domain/RegularTimetable.DEPARTURE_HOURS
PREP_HOURS = 1.0  # LoopEconomics.PREP_BUFFER_HOURS
MAX_DAILY_DRIVING = 8.0  # LoopEconomics.MAX_DAILY_DRIVING_HOURS
COMPANION_SEARCH_DAYS = 2  # LoopEconomics.COMPANION_SEARCH_DAYS
MAX_HOLD_DAYS = 7  # LoopEconomics.MAX_HOLD_DAYS (CM-31)
DEFAULT_WAITING, DEFAULT_PARKING = 333000.0, 330000.0  # CostParameterService (CM-8)


def query(schema, sql):
    out = subprocess.run([MYSQL, "-uroot", "-N", "-B", "--default-character-set=utf8mb4", schema, "-e", sql],
                         capture_output=True, text=True, encoding="utf-8", env=os.environ)
    if out.returncode != 0:
        sys.exit("MySQL: " + out.stderr.strip())
    return [line.split("\t") for line in out.stdout.splitlines() if line]


def number(text, default=None):
    return default if text in (None, "", "NULL") else float(text)


def load(schema, start, end):
    names = dict(query(schema, "SELECT id, station_name FROM stations"))
    routes = {}
    for rid, dist, minutes, first, last in query(schema, """
            SELECT r.id, r.distance_km, r.estimated_duration,
                   (SELECT rs.station_id FROM route_stations rs WHERE rs.route_id = r.id ORDER BY rs.stop_order LIMIT 1),
                   (SELECT rs.station_id FROM route_stations rs WHERE rs.route_id = r.id ORDER BY rs.stop_order DESC LIMIT 1)
            FROM routes r"""):
        label = names.get(first, "?").replace("Bến xe ", "") + " → " + names.get(last, "?").replace("Bến xe ", "")
        routes[int(rid)] = dict(km=number(dist, 0.0), minutes=int(number(minutes, 240)), first=first, last=last,
                                label=label)
    homes = {row[0] for row in query(schema, "SELECT DISTINCT home_station_id FROM buses WHERE home_station_id IS NOT NULL")}
    pairs = {}
    for rid, r in routes.items():  # chiều đi: rời bến nhà; chiều về: đúng một tuyến đảo bến (như RouteService.reverseRoutesOf)
        reverse = [oid for oid, o in routes.items() if o["first"] == r["last"] and o["last"] == r["first"]]
        if r["first"] in homes and len(reverse) == 1:
            pairs[rid] = reverse[0]
    cols = query(schema, "SHOW COLUMNS FROM cost_parameters")
    names = [c[0] for c in cols]
    wanted = ["fuel_cost_per_km", "driver_wage_per_hour", "waiting_wage_per_day", "bus_parking_per_day"]
    select = ", ".join(n if n in names else "NULL" for n in wanted)
    row = (query(schema, "SELECT " + select + " FROM cost_parameters ORDER BY id LIMIT 1") or [[None] * 4])[0]
    cost = dict(fuel=number(row[0], 6000.0), wage=number(row[1], 50000.0), waiting=number(row[2], DEFAULT_WAITING),
                parking=number(row[3], DEFAULT_PARKING))
    ids = ",".join(str(i) for p in pairs.items() for i in p) or "0"
    extras = []
    for tid, rid, dep, tickets, price in query(schema, f"""
            SELECT id, route_id, departure_time, tickets_sold, price FROM trips
            WHERE is_deleted = 0 AND status = 'COMPLETED' AND is_extra_trip = 1 AND route_id IN ({ids})
              AND departure_time >= '{start}' AND departure_time < '{end}' ORDER BY departure_time, id"""):
        extras.append(dict(id=int(tid), route=int(rid), dep=datetime.fromisoformat(dep[:19]), tickets=int(tickets),
                           price=number(price, 0.0)))
    return routes, pairs, cost, extras


def leg_cost(route, cost):
    hours = route["minutes"] / 60.0
    drivers = max(1, -(-int(hours * 1000) // int(MAX_DAILY_DRIVING * 1000)))
    return cost["fuel"] * route["km"] + cost["wage"] * hours * drivers


def share(route):
    hours = route["minutes"] / 60.0
    drivers = max(1, -(-int(hours * 1000) // int(MAX_DAILY_DRIVING * 1000)))
    return min(hours / drivers, MAX_DAILY_DRIVING)


def earliest_return(out_route, back_route, dep, arrival):
    """LoopEconomics.earliestReturn: khung chiều ngược đầu tiên ≥ tới + 1 giờ mà một tài xế lái được."""
    ready = arrival + timedelta(hours=PREP_HOURS)
    same_day_fits = share(out_route) + share(back_route) <= MAX_DAILY_DRIVING
    for d in range(COMPANION_SEARCH_DAYS + 1):
        for h in HOURS:
            t = datetime.combine(arrival.date() + timedelta(days=d), datetime.min.time()).replace(hour=h)
            if t >= ready and (same_day_fits or t.date() != dep.date()):
                return t
    return None


def latest_bring(dep, back_route):
    """LoopEconomics.bringTimes, giờ muộn nhất: lượt đưa xe lên tới kịp giờ đi + 1 giờ chuẩn bị."""
    latest = dep - timedelta(minutes=back_route["minutes"]) - timedelta(hours=PREP_HOURS)
    for d in range(COMPANION_SEARCH_DAYS + 1):
        for h in reversed(HOURS):
            t = datetime.combine(dep.date() - timedelta(days=d), datetime.min.time()).replace(hour=h)
            if t <= latest:
                return t
    return latest


def simulate(routes, pairs, cost, extras):
    reverse_of = {**pairs, **{b: a for a, b in pairs.items()}}
    cards = []
    for e in extras:
        if e["tickets"] <= 0 or e["route"] not in reverse_of:
            continue
        route = routes[e["route"]]
        back = routes[reverse_of[e["route"]]]
        arrival = e["dep"] + timedelta(minutes=route["minutes"])
        out = e["route"] in pairs
        c = dict(e, out=out, pair=min(e["route"], reverse_of[e["route"]]), arrival=arrival, revenue=e["tickets"] * e["price"],
                 cost=leg_cost(route, cost), km=route["km"], route_obj=route, back_obj=back)
        if out:
            c["companion_at"] = earliest_return(route, back, e["dep"], arrival)
            c["loop"] = (e["dep"], c["companion_at"] + timedelta(minutes=back["minutes"]))
        else:
            c["companion_at"] = latest_bring(e["dep"], back)
            c["loop"] = (c["companion_at"], arrival)
        c["single_profit"] = c["revenue"] - c["cost"] - leg_cost(back, cost)  # lượt đi kèm rỗng
        cards.append(c)

    # ── B: ghép như LoopEconomics.pairHolds ──
    candidates = []
    for o in (c for c in cards if c["out"] and c["companion_at"] is not None):
        for i in (c for c in cards if not c["out"] and c["pair"] == o["pair"]):
            if i["dep"] < o["arrival"] + timedelta(hours=PREP_HOURS):
                continue
            if i["dep"].date() == o["dep"].date() and share(o["route_obj"]) + share(i["route_obj"]) > MAX_DAILY_DRIVING:
                continue
            wait_h = max(0.0, (i["dep"] - o["companion_at"]).total_seconds() / 3600.0)
            if wait_h > MAX_HOLD_DAYS * 24:
                continue
            wait = (cost["waiting"] + cost["parking"]) * wait_h / 24.0
            held = o["revenue"] + i["revenue"] - o["cost"] - i["cost"] - wait
            gain = held - max(o["single_profit"], 0) - max(i["single_profit"], 0)
            if gain > 0:
                candidates.append((-gain, o["dep"], o["id"], i["dep"], i["id"], o, i, wait_h, wait, held))
    candidates.sort(key=lambda t: t[:5])
    used, holds = set(), []
    for _, _, _, _, _, o, i, wait_h, wait, held in candidates:
        if o["id"] not in used and i["id"] not in used:
            used |= {o["id"], i["id"]}
            holds.append(dict(o=o, i=i, wait_h=wait_h, wait=wait, held=held))

    def totals(scenario):
        legs = empty = 0
        empty_km = wait_h = wait = revenue = costs = 0.0
        intervals = []
        for c in cards:
            if scenario == "B" and c["id"] in used:
                continue
            legs += 2
            empty += 1
            empty_km += c["back_obj"]["km"]
            revenue += c["revenue"]
            costs += c["cost"] + leg_cost(c["back_obj"], cost)
            intervals.append(c["loop"])
        if scenario == "B":
            for h in holds:
                legs += 2
                wait_h += h["wait_h"]
                wait += h["wait"]
                revenue += h["o"]["revenue"] + h["i"]["revenue"]
                costs += h["o"]["cost"] + h["i"]["cost"] + h["wait"]
                intervals.append((h["o"]["dep"], h["i"]["arrival"]))
        events = sorted([(a, 1) for a, _ in intervals] + [(b, -1) for _, b in intervals], key=lambda t: (t[0], t[1]))
        peak = now = 0
        for _, step in events:
            now += step
            peak = max(peak, now)
        return dict(extras=len(cards), loops=len(intervals), legs=legs, empty=empty, empty_km=empty_km, wait_h=wait_h,
                    wait=wait, revenue=revenue, cost=costs, profit=revenue - costs, peak=peak)

    return cards, holds, totals("A"), totals("B")


def report(schema, start, end, routes, pairs, cost, extras):
    cards, holds, a, b = simulate(routes, pairs, cost, extras)
    m = lambda v: f"{v:,.0f}".replace(",", ".")
    lines = [f"### {start} → {end} (schema `{schema}`)", "",
             f"Tham số: nhiên liệu {m(cost['fuel'])}đ/km, lương {m(cost['wage'])}đ/giờ, lương chờ {m(cost['waiting'])}đ/người/ngày, "
             f"phí nằm bến {m(cost['parking'])}đ/ngày. Chuyến thêm có khách trong khoảng: {len(cards)} "
             f"(lịch sử có {len(extras)} chuyến thêm, {sum(1 for e in extras if e['tickets'] == 0)} chuyến 0 vé).", "",
             "| | A. Về ngay | B. Giữ–về | B − A |", "|---|---:|---:|---:|"]
    rows = [("Vòng (xe tăng cường chạy)", "loops", 0), ("Lượt chạy", "legs", 0), ("Lượt rỗng", "empty", 0),
            ("Km rỗng", "empty_km", 0), ("Giờ chờ thêm ở bến xa", "wait_h", 0), ("Tiền chờ (đ)", "wait", 0),
            ("Doanh thu (đ)", "revenue", 0), ("Chi phí (đ)", "cost", 0), ("Lãi (đ)", "profit", 0),
            ("Xe tăng cường cùng lúc (đỉnh)", "peak", 0)]
    for label, key, _ in rows:
        if key == "wait_h":
            lines.append(f"| {label} | {a[key]:.1f} | {b[key]:.1f} | {b[key] - a[key]:.1f} |")
        else:
            lines.append(f"| {label} | {m(a[key])} | {m(b[key])} | {m(b[key] - a[key])} |")
    lines += ["", f"Vòng giữ xe: {len(holds)}."]
    if holds:
        lines += ["", "| Chuyến thêm đi | Chuyến thêm về | Chờ thêm (giờ) | Tiền chờ (đ) | Lãi vòng giữ (đ) |", "|---|---|---:|---:|---:|"]
        for h in sorted(holds, key=lambda h: h["o"]["dep"]):
            lines.append(f"| #{h['o']['id']} {h['o']['route_obj']['label']} {h['o']['dep']:%H:%M %d/%m} ({h['o']['tickets']} vé) "
                         f"| #{h['i']['id']} {h['i']['route_obj']['label']} {h['i']['dep']:%H:%M %d/%m} ({h['i']['tickets']} vé) "
                         f"| {h['wait_h']:.1f} | {m(h['wait'])} | {m(h['held'])} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    md = sys.argv[sys.argv.index("--md") + 1] if "--md" in sys.argv else None
    if md in args:
        args.remove(md)
    if len(args) != 3:
        sys.exit(__doc__)
    schema, start, end = args
    text = report(schema, start, end, *load(schema, start, end))
    print(text)
    if md:
        with open(md, "a", encoding="utf-8") as f:
            f.write(text + "\n")
