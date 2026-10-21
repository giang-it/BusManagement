package giang.com.BusManagement.config;

import giang.com.BusManagement.domain.Holiday;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.HolidayFlow;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Phần MÔ PHỎNG của bộ sinh dữ liệu lịch sử (forecast redesign GĐ2, QĐ-10 trong
 * docs/development/forecast_decisions.md): nhu cầu thật của từng khung (tuyến × giờ
 * × ngày), những ngày tuyến phải đóng vì bão, những chuyến bị hủy ngẫu nhiên, và số
 * chuyến tăng cường mà "nhà xe trong quá khứ" muốn chạy quanh các kỳ nghỉ. Việc xếp
 * xe / tài xế và ghi DB nằm ở HistoricalDataBackfill; lớp này không đụng DB.
 *
 * ====================================================================
 * DỮ LIỆU MÔ PHỎNG — MỌI ĐỘ LỚN LÀ GIẢ ĐỊNH
 * ====================================================================
 * Cấu trúc và độ lớn lấy từ bộ sinh của thí nghiệm bước 0
 * (forecast_research/step0/synth_generator.py), tổng quát hoá từ "theo id tuyến"
 * sang "theo hướng dòng khách" (Route.holidayFlow) để tuyến mới cũng dùng được. Đỉnh
 * Tết khoảng ×3 so với ngày thường theo cỡ báo chí đưa tin về các bến xe (200–350%).
 * Không con số nào ở đây là số đo, và phải được trình bày đúng như vậy khi bảo vệ.
 *
 * Bộ sinh CỐ Ý khác cấu trúc mô hình dự báo, để mô hình phải xấp xỉ chứ không chỉ tìm
 * lại công thức (bẫy "dữ liệu tự chứng minh", forecast_discussion.md §4.4): đỉnh lễ
 * bám theo KỲ NGHỈ CHÍNH THỨC — sinh theo quy tắc có dao động ở officialBreak(), không
 * phải lịch nghỉ thật — trong khi mô hình chỉ biết ngày lễ; mùa là các "bướu" chứ
 * không phải sóng sin/cos; mức nhu cầu trôi ngẫu nhiên và nhảy bậc chứ không đi theo
 * đường thẳng; nhiễu là nhị thức âm chứ không phải Poisson.
 *
 * ====================================================================
 * TẤT ĐỊNH, KHÔNG PHỤ THUỘC NGÀY CHẠY
 * ====================================================================
 * Mọi số ngẫu nhiên được rút từ một seed trộn theo (tuyến, ngày, khung, loại) và mọi
 * quá trình liên tục (mức trôi) được tính từ một MỐC CỐ ĐỊNH (EPOCH), không phải từ
 * ngày chạy. Vì vậy chạy hôm nay hay chạy bổ sung tuần sau đều cho đúng cùng một con
 * số cho cùng một ngày — đây là thứ bộ sinh cũ thiếu (nó tính xu hướng từ
 * "hôm nay − 84" nên mỗi lần chạy bổ sung lại reset, tạo hình răng cưa: lỗi #31).
 */
final class HistorySimulation {

    /** Ngày đầu tiên của lịch sử mô phỏng (QĐ-10: có 4 lần Tết 2023–2026). */
    static final LocalDate EPOCH = LocalDate.of(2023, 1, 1);

    /** Các khung giờ khởi hành — "time slot" mà dự báo gom nhóm. */
    static final List<Integer> DEPARTURE_HOURS = List.of(6, 12, 18);

    /**
     * Mức tải ngày thường của XE CHUẨN theo khung 06/12/18: trung bình 5 tuyến của
     * bước 0. Nhu cầu được tính theo số ghế xe chuẩn của tuyến (QĐ-10, G2-2), nên tuyến
     * 22 chỗ có khoảng 13–17 khách một khung ngày thường.
     */
    private static final double[] HOUR_LOAD = { 0.72, 0.58, 0.77 };

    /** Mỗi tuyến đông hơn hay vắng hơn mức chung một chút, rút một lần theo id tuyến. */
    private static final double ROUTE_LEVEL_MIN = 0.90;
    private static final double ROUTE_LEVEL_MAX = 1.10;

    /** Tăng trưởng 4%/năm, cộng một bước ngẫu nhiên mỗi ngày trên log của mức. */
    private static final double GROWTH_PER_YEAR = 0.04;
    private static final double WALK_SD = 0.0015;

    /** Nhảy bậc (đối thủ mở tuyến, đường mới...): mỗi tuyến mỗi năm có xác suất này. */
    private static final double JUMP_PROBABILITY_PER_YEAR = 0.35;
    private static final double JUMP_MIN = 0.85;
    private static final double JUMP_MAX = 1.15;

    /** Nhu cầu ~ nhị thức âm(trung bình μ, size 40): phương sai = μ + μ²/40. */
    static final int NB_SIZE = 40;

    /** Tỉ lệ khung bị hủy ngẫu nhiên — giữ đúng con số của Phase 5. */
    static final double CANCELLATION_RATE = 0.06;

    /** Mỗi tuyến mỗi năm: xác suất có một đợt đóng tuyến vì bão (1–3 ngày, thường tháng 7–11). */
    private static final double STORM_PROBABILITY_PER_YEAR = 0.8;
    private static final double STORM_SEASON_SHARE = 0.7;

    /** Biên độ Tết theo hướng dòng khách (đỉnh ≈ 1 + biên độ). */
    private static final Map<Flow, Double> TET_AMPLITUDE = Map.of(Flow.OUT, 2.2, Flow.IN, 1.9, Flow.CROSS, 1.0);

    /** Biên độ các dịp lễ nhỏ. */
    private static final Map<Holiday, Double> SMALL_AMPLITUDE = new EnumMap<>(Map.of(
            Holiday.NEW_YEAR, 0.35,
            Holiday.HUNG_KINGS_COMMEMORATION, 0.30,
            Holiday.REUNIFICATION_AND_LABOUR_DAY, 0.60,
            Holiday.NATIONAL_DAY, 0.50));

    /** Hệ số thứ trong tuần (Thứ Hai → Chủ nhật), chuẩn hoá về trung bình 1 ở hàm khởi tạo. */
    private static final Map<Flow, double[]> WEEK = Map.of(
            Flow.OUT, new double[] { 0.92, 0.90, 0.92, 0.98, 1.22, 1.18, 0.88 },
            Flow.IN, new double[] { 1.05, 0.90, 0.90, 0.92, 0.95, 1.00, 1.30 },
            Flow.CROSS, new double[] { 0.95, 0.92, 0.92, 0.95, 1.12, 1.05, 1.12 });

    /** Khi Admin để trống hướng dòng khách, bộ sinh coi tuyến là "không theo chiều". */
    enum Flow {
        OUT, IN, CROSS;

        static Flow of(HolidayFlow holidayFlow) {
            if (holidayFlow == null) {
                return CROSS;
            }
            return switch (holidayFlow) {
                case LEAVING_HUB -> OUT;
                case TOWARD_HUB -> IN;
            };
        }
    }

    /** Thông tin bộ sinh cần về một tuyến. */
    record RouteProfile(long routeId, HolidayFlow holidayFlow, int standardSeats) {
    }

    /** Kỳ nghỉ chính thức mô phỏng của một dịp lễ trong một năm. */
    record OfficialBreak(LocalDate firstDayOff, LocalDate lastDayOff) {
    }

    // Các "luồng" ngẫu nhiên — mỗi loại quyết định một luồng riêng, để thêm một loại
    // mới không làm xê dịch số của các loại cũ.
    private static final long LEVEL = 1;
    private static final long WALK = 2;
    private static final long JUMP = 3;
    private static final long BREAK = 4;
    private static final long JITTER = 5;
    private static final long STORM = 6;
    private static final long DEMAND = 7;
    private static final long CANCEL = 8;
    private static final long EXTRA = 9;
    private static final long SLOT_ORDER = 10;

    private final long seed;
    private final LocalDate lastDay;
    private final int days;
    private final Map<Holiday, Map<Integer, OfficialBreak>> breaks = new EnumMap<>(Holiday.class);

    private final Map<Long, double[]> baseLog = new HashMap<>();
    private final Map<Long, double[][]> holidayLog = new HashMap<>();
    private final Map<Long, boolean[]> closure = new HashMap<>();
    private final Map<Long, int[]> wishedExtras = new HashMap<>();
    private final Map<Long, Double> routeLevel = new HashMap<>();

    /**
     * @param seed    seed của toàn bộ mô phỏng
     * @param routes  các tuyến được sinh
     * @param lastDay ngày cuối cùng cần mô phỏng (≥ EPOCH, trong phạm vi HolidayCalendar)
     */
    HistorySimulation(long seed, List<RouteProfile> routes, LocalDate lastDay) {
        if (lastDay.isBefore(EPOCH) || !HolidayCalendar.covers(lastDay)) {
            throw new IllegalArgumentException("Ngày cuối " + lastDay + " phải từ " + EPOCH
                    + " và nằm trong phạm vi lịch ngày lễ (HolidayCalendar).");
        }
        this.seed = seed;
        this.lastDay = lastDay;
        this.days = (int) ChronoUnit.DAYS.between(EPOCH, lastDay) + 1;
        int lastYear = Math.min(lastDay.getYear() + 1, HolidayCalendar.LAST_COVERED_YEAR);
        for (Holiday holiday : Holiday.values()) {
            Map<Integer, OfficialBreak> byYear = new HashMap<>();
            for (int year = EPOCH.getYear(); year <= lastYear; year++) {
                byYear.put(year, officialBreak(holiday, year));
            }
            breaks.put(holiday, byYear);
        }
        for (RouteProfile route : routes) {
            prepare(route, lastYear);
        }
    }

    // =========================================================================
    // API cho bộ sinh
    // =========================================================================

    /** Nhu cầu KỲ VỌNG của một khung (trước nhiễu). */
    double mean(RouteProfile route, LocalDate date, int hourIndex) {
        int day = dayIndex(date);
        Flow flow = Flow.of(route.holidayFlow());
        double weekday = normalisedWeek(flow)[date.getDayOfWeek().getValue() - 1]
                * weekHourFactor(flow, date.getDayOfWeek(), DEPARTURE_HOURS.get(hourIndex));
        return route.standardSeats() * HOUR_LOAD[hourIndex] * routeLevel.get(route.routeId()) * weekday
                * Math.exp(baseLog.get(route.routeId())[day] + holidayLog.get(route.routeId())[hourIndex][day]);
    }

    /** Nhu cầu THẬT của một khung: một lần rút nhị thức âm quanh mean(), tất định theo seed. */
    int demand(RouteProfile route, LocalDate date, int hourIndex) {
        Random random = random(DEMAND, route.routeId(), date.toEpochDay(), hourIndex);
        return negativeBinomial(mean(route, date, hourIndex), random);
    }

    /** Tuyến có phải đóng cả ngày vì bão không — mọi chuyến hôm đó bị hủy. */
    boolean isClosed(long routeId, LocalDate date) {
        return closure.get(routeId)[dayIndex(date)];
    }

    /** Khung có bị hủy ngẫu nhiên không (hỏng xe, thiếu khách...), ngoài ngày bão. */
    boolean isRandomlyCancelled(long routeId, LocalDate date, int hourIndex) {
        return random(CANCEL, routeId, date.toEpochDay(), hourIndex).nextDouble() < CANCELLATION_RATE;
    }

    /** Thứ tự xếp xe cho các khung của một ngày — xáo tất định theo ngày. */
    Random slotOrder(LocalDate date) {
        return random(SLOT_ORDER, date.toEpochDay(), 0, 0);
    }

    /** Số chuyến tăng cường nhà xe muốn chạy cho MỖI khung của tuyến trong ngày. */
    int wishedExtraTrips(long routeId, LocalDate date) {
        return wishedExtras.get(routeId)[dayIndex(date)];
    }

    /**
     * Kỳ nghỉ chính thức mô phỏng — QUY TẮC có dao động (QĐ-10, G2-3), không phải lịch
     * nghỉ thật của từng năm:
     * <ul>
     * <li>Tết Âm lịch: nghỉ từ 2–4 ngày trước mùng 1, dài 7–9 ngày;</li>
     * <li>dịp khác: các ngày nghỉ theo luật (1/1; 10/3 âm lịch; 30/4–1/5; 2/9 cộng một
     * ngày liền kề), lùi/nới thêm 0–1 ngày, và kéo sang cuối tuần nếu bắt đầu đúng thứ
     * Hai hoặc kết thúc đúng thứ Sáu.</li>
     * </ul>
     * Cùng một kỳ nghỉ cho mọi tuyến — đó là lịch nghỉ của cả nước.
     */
    OfficialBreak officialBreak(Holiday holiday, int year) {
        LocalDate anchor = HolidayCalendar.anchorOf(holiday, year);
        Random random = random(BREAK, holiday.ordinal(), year, 0);
        if (holiday == Holiday.LUNAR_NEW_YEAR) {
            LocalDate start = anchor.minusDays(2 + random.nextInt(3));
            return new OfficialBreak(start, start.plusDays(6 + random.nextInt(3)));
        }
        LocalDate legalStart = anchor;
        LocalDate legalEnd = switch (holiday) {
            case NEW_YEAR, HUNG_KINGS_COMMEMORATION -> anchor;
            case REUNIFICATION_AND_LABOUR_DAY -> anchor.plusDays(1);
            case NATIONAL_DAY -> {
                // 2/9 cộng một ngày liền kề, trước hoặc sau.
                if (random.nextBoolean()) {
                    legalStart = anchor.minusDays(1);
                    yield anchor;
                }
                yield anchor.plusDays(1);
            }
            case LUNAR_NEW_YEAR -> throw new IllegalStateException("đã xử lý ở trên");
        };
        LocalDate start = legalStart.minusDays(random.nextInt(2));
        LocalDate end = legalEnd.plusDays(random.nextInt(2));
        if (start.getDayOfWeek() == DayOfWeek.MONDAY) {
            start = start.minusDays(2);
        }
        if (end.getDayOfWeek() == DayOfWeek.FRIDAY) {
            end = end.plusDays(2);
        }
        return new OfficialBreak(start, end);
    }

    // =========================================================================
    // Chuẩn bị theo tuyến
    // =========================================================================

    private void prepare(RouteProfile route, int lastYear) {
        long id = route.routeId();
        Flow flow = Flow.of(route.holidayFlow());
        routeLevel.put(id, uniform(random(LEVEL, id, 0, 0), ROUTE_LEVEL_MIN, ROUTE_LEVEL_MAX));

        // Mức: tăng trưởng + bước ngẫu nhiên tính từ EPOCH + nhảy bậc theo từng năm.
        double[] base = new double[days];
        Random walk = random(WALK, id, 0, 0);
        double level = 0;
        for (int day = 0; day < days; day++) {
            level += walk.nextGaussian() * WALK_SD;
            base[day] = GROWTH_PER_YEAR * day / 365.25 + level + season(flow, EPOCH.plusDays(day));
        }
        for (int year = EPOCH.getYear(); year <= lastDay.getYear(); year++) {
            Random jump = random(JUMP, id, year, 0);
            if (jump.nextDouble() < JUMP_PROBABILITY_PER_YEAR) {
                LocalDate when = LocalDate.of(year, 1, 1).plusDays(jump.nextInt(LocalDate.of(year, 1, 1).lengthOfYear()));
                double step = Math.log(uniform(jump, JUMP_MIN, JUMP_MAX));
                for (int day = Math.max(0, dayIndexUnchecked(when)); day < days; day++) {
                    base[day] += step;
                }
            }
        }
        baseLog.put(id, base);

        double[][] holidays = new double[DEPARTURE_HOURS.size()][days];
        for (Holiday holiday : Holiday.values()) {
            for (int year = EPOCH.getYear(); year <= lastYear; year++) {
                OfficialBreak officialBreak = breaks.get(holiday).get(year);
                // Độ lớn mỗi lần lễ dao động ±15%, giống nhau cho mọi khung của tuyến.
                double jitter = uniform(random(JITTER, id, holiday.ordinal(), year), 0.85, 1.15);
                for (int h = 0; h < DEPARTURE_HOURS.size(); h++) {
                    int hour = DEPARTURE_HOURS.get(h);
                    if (holiday == Holiday.LUNAR_NEW_YEAR) {
                        addTet(holidays[h], flow, hour, officialBreak, HolidayCalendar.anchorOf(holiday, year), jitter);
                    } else {
                        addSmallHoliday(holidays[h], flow, hour, officialBreak, SMALL_AMPLITUDE.get(holiday) * jitter);
                    }
                }
            }
        }
        holidayLog.put(id, holidays);

        closure.put(id, storms(id));
        wishedExtras.put(id, operatorWishes(id, flow, lastYear));
    }

    /** Mùa trong năm: các "bướu" theo ngày trong năm, cộng khoảng lặng sau Tết. */
    private double season(Flow flow, LocalDate date) {
        double s = bump(date, 12, 20, 12, 0.05) // dồn cuối năm
                + bump(date, 7, 1, 30, 0.10); // mùa hè
        if (flow == Flow.OUT) {
            s += bump(date, 6, 5, 8, 0.08); // học sinh, sinh viên về quê sau năm học
        }
        if (flow == Flow.IN) {
            s += bump(date, 8, 30, 8, 0.12); // trở lại thành phố trước năm học
        }
        // Khoảng lặng khoảng một tháng sau Tết, bám theo ngày âm lịch.
        for (int year = date.getYear() - 1; year <= date.getYear(); year++) {
            if (year >= HolidayCalendar.FIRST_COVERED_YEAR && year <= HolidayCalendar.LAST_COVERED_YEAR) {
                long distance = ChronoUnit.DAYS.between(
                        HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year).plusDays(30), date);
                s += -0.10 * Math.exp(-(double) distance * distance / (2 * 10.0 * 10.0));
            }
        }
        return s;
    }

    /** amp × Gauss của khoảng cách (ngày) tới ngày-tháng cho trước, xét cả năm trước và năm sau. */
    private static double bump(LocalDate date, int month, int day, double width, double amplitude) {
        double total = 0;
        for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
            long distance = ChronoUnit.DAYS.between(LocalDate.of(year, month, day), date);
            total += amplitude * Math.exp(-(double) distance * distance / (2 * width * width));
        }
        return total;
    }

    /** Hình dạng Tết theo hướng dòng khách, bám theo kỳ nghỉ — chép từ holiday_log() của bước 0. */
    private void addTet(double[] log, Flow flow, int hour, OfficialBreak b, LocalDate tet, double jitter) {
        double amplitude = TET_AMPLITUDE.get(flow) * jitter;
        LocalDate s = b.firstDayOff();
        LocalDate e = b.lastDayOff();
        for (int k = -9; k <= 0; k++) {
            LocalDate d = s.plusDays(k);
            switch (flow) {
                case OUT -> multiply(log, d, 1 + amplitude * Math.exp(-sq(k + 1) / (2 * sq(2.2)))
                        * (hour == 18 && k == -1 ? 1.15 : 1.0));
                case IN -> multiply(log, d, 0.75);
                case CROSS -> multiply(log, d, 1 + amplitude * Math.exp(-sq(k + 1) / (2 * sq(1.5))));
            }
        }
        for (LocalDate d = s.plusDays(1); !d.isAfter(e); d = d.plusDays(1)) {
            if (!d.isBefore(tet) && !d.isAfter(tet.plusDays(2))) {
                multiply(log, d, flow == Flow.CROSS ? 0.3 : 0.25);
            } else if (d.isBefore(tet)) {
                multiply(log, d, flow == Flow.OUT ? 0.5 : 0.6);
            } else {
                long k = ChronoUnit.DAYS.between(e, d);
                switch (flow) {
                    case IN -> multiply(log, d, 1 + amplitude * Math.exp(-sq(k) / (2 * sq(1.8))));
                    case CROSS -> multiply(log, d, 1 + 0.8 * amplitude * Math.exp(-sq(k) / 2));
                    case OUT -> multiply(log, d, 0.7);
                }
            }
        }
        for (int k = 1; k <= 10; k++) {
            LocalDate d = e.plusDays(k);
            switch (flow) {
                case OUT -> multiply(log, d, 0.85);
                case IN -> multiply(log, d, 1 + amplitude * Math.exp(-sq(k) / (2 * sq(1.8))));
                case CROSS -> multiply(log, d, 1 + 0.8 * amplitude * Math.exp(-sq(k) / 2));
            }
        }
        if (flow == Flow.IN) {
            // Đợt quay lại thứ hai, nhỏ hơn, sau Rằm tháng Giêng.
            for (int k = -4; k <= 4; k++) {
                multiply(log, tet.plusDays(15 + k), 1 + 0.3 * jitter * Math.exp(-sq(k) / (2 * sq(1.5))));
            }
        }
    }

    /** Hình dạng các dịp lễ nhỏ theo hướng dòng khách — chép từ holiday_log() của bước 0. */
    private void addSmallHoliday(double[] log, Flow flow, int hour, OfficialBreak b, double amplitude) {
        LocalDate s = b.firstDayOff();
        LocalDate e = b.lastDayOff();
        switch (flow) {
            case OUT -> {
                multiply(log, s.minusDays(1), 1 + amplitude * (hour == 18 ? 1.3 : hour == 6 ? 0.8 : 1.0));
                multiply(log, s, 1 + 0.8 * amplitude * (hour == 6 ? 1.3 : 0.8));
                multiplyMiddle(log, s, e, 0.95);
                if (e.isAfter(s)) {
                    multiply(log, e, 0.7);
                }
                multiply(log, e.plusDays(1), 0.85);
            }
            case IN -> {
                multiply(log, s.minusDays(1), 0.8);
                multiply(log, s, 0.75);
                multiplyMiddle(log, s, e, 0.95);
                if (e.isAfter(s)) {
                    multiply(log, e.minusDays(1), 1 + 0.5 * amplitude);
                }
                multiply(log, e, 1 + amplitude * (hour == 12 || hour == 18 ? 1.2 : 0.8));
                multiply(log, e.plusDays(1), 1 + 0.5 * amplitude * (hour == 6 ? 1.0 : 0.3));
            }
            case CROSS -> {
                multiply(log, s.minusDays(1), 1 + 0.5 * amplitude);
                multiply(log, e, 1 + 0.4 * amplitude);
            }
        }
    }

    private void multiplyMiddle(double[] log, LocalDate s, LocalDate e, double factor) {
        for (LocalDate d = s.plusDays(1); d.isBefore(e); d = d.plusDays(1)) {
            multiply(log, d, factor);
        }
    }

    private void multiply(double[] log, LocalDate date, double factor) {
        int day = dayIndexUnchecked(date);
        if (day >= 0 && day < days && factor > 0) {
            log[day] += Math.log(factor);
        }
    }

    /** Đợt đóng tuyến vì bão: tối đa một đợt mỗi năm, 1–3 ngày, phần lớn rơi vào tháng 7–11. */
    private boolean[] storms(long routeId) {
        boolean[] closed = new boolean[days];
        for (int year = EPOCH.getYear(); year <= lastDay.getYear(); year++) {
            Random random = random(STORM, routeId, year, 0);
            if (random.nextDouble() >= STORM_PROBABILITY_PER_YEAR) {
                continue;
            }
            LocalDate start = random.nextDouble() < STORM_SEASON_SHARE
                    ? LocalDate.of(year, 7, 1).plusDays(random.nextInt(153)) // 1/7 → 30/11
                    : LocalDate.of(year, 1, 1).plusDays(random.nextInt(LocalDate.of(year, 1, 1).lengthOfYear()));
            int length = 1 + random.nextInt(3);
            for (int k = 0; k < length; k++) {
                int day = dayIndexUnchecked(start.plusDays(k));
                if (day >= 0 && day < days) {
                    closed[day] = true;
                }
            }
        }
        return closed;
    }

    /**
     * Chuyến tăng cường nhà xe muốn chạy (cho mỗi khung của tuyến), theo kinh nghiệm quanh
     * các kỳ nghỉ — chép operator_extra() của bước 0. Nhà xe biết lịch nghỉ; số chuyến thực
     * chạy còn bị chặn bởi số xe và tài xế rảnh trong ngày (HistoricalDataBackfill).
     */
    private int[] operatorWishes(long routeId, Flow flow, int lastYear) {
        int[] wishes = new int[days];
        for (Holiday holiday : Holiday.values()) {
            for (int year = EPOCH.getYear(); year <= lastYear; year++) {
                OfficialBreak b = breaks.get(holiday).get(year);
                LocalDate s = b.firstDayOff();
                LocalDate e = b.lastDayOff();
                if (holiday == Holiday.LUNAR_NEW_YEAR) {
                    switch (flow) {
                        case OUT -> {
                            for (int k = -5; k <= 0; k++) {
                                wish(wishes, s.plusDays(k), k == -2 || k == -1 ? 2 : 1);
                            }
                        }
                        case IN -> {
                            for (int k = -1; k <= 3; k++) {
                                wish(wishes, e.plusDays(k), k == -1 || k == 0 ? 2 : 1);
                            }
                        }
                        case CROSS -> {
                            wish(wishes, s.minusDays(2), 1);
                            wish(wishes, s.minusDays(1), 1);
                            wish(wishes, e, 1);
                        }
                    }
                    continue;
                }
                Random random = random(EXTRA, routeId, holiday.ordinal(), year);
                if (flow == Flow.OUT && random.nextDouble() < 0.7) {
                    wish(wishes, s.minusDays(1), 1);
                } else if (flow == Flow.IN && random.nextDouble() < 0.7) {
                    wish(wishes, e, 1);
                }
            }
        }
        return wishes;
    }

    private void wish(int[] wishes, LocalDate date, int count) {
        int day = dayIndexUnchecked(date);
        if (day >= 0 && day < days) {
            wishes[day] = Math.max(wishes[day], count);
        }
    }

    // =========================================================================
    // Tiện ích
    // =========================================================================

    private static double[] normalisedWeek(Flow flow) {
        double[] raw = WEEK.get(flow);
        double mean = 0;
        for (double v : raw) {
            mean += v / raw.length;
        }
        double[] result = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            result[i] = raw[i] / mean;
        }
        return result;
    }

    /** Vài khung đặc biệt đông trong tuần (chép WEEK_HOUR của bước 0). */
    private static double weekHourFactor(Flow flow, DayOfWeek day, int hour) {
        if (flow == Flow.OUT && day == DayOfWeek.FRIDAY && hour == 18) {
            return 1.10;
        }
        if (flow == Flow.OUT && day == DayOfWeek.SATURDAY && hour == 6) {
            return 1.08;
        }
        if (flow == Flow.IN && day == DayOfWeek.SUNDAY && (hour == 18 || hour == 12)) {
            return 1.10;
        }
        if (flow == Flow.IN && day == DayOfWeek.MONDAY && hour == 6) {
            return 1.12;
        }
        return 1.0;
    }

    private int dayIndex(LocalDate date) {
        int day = dayIndexUnchecked(date);
        if (day < 0 || day >= days) {
            throw new IllegalArgumentException("Ngày " + date + " nằm ngoài khoảng mô phỏng " + EPOCH + " → " + lastDay);
        }
        return day;
    }

    private static int dayIndexUnchecked(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(EPOCH, date);
    }

    private static double sq(double x) {
        return x * x;
    }

    private static double uniform(Random random, double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    /** Random riêng cho mỗi (loại, a, b, c), trộn với seed chung theo SplitMix64. */
    private Random random(long stream, long a, long b, long c) {
        long h = mix(seed ^ mix(stream));
        h = mix(h ^ mix(a));
        h = mix(h ^ mix(b));
        h = mix(h ^ mix(c));
        return new Random(h);
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Nhị thức âm(trung bình mean, size NB_SIZE) = Poisson với tham số rút từ
     * Gamma(NB_SIZE, mean / NB_SIZE). Java chuẩn không có hai phân phối này nên cài tay:
     * Gamma theo Marsaglia–Tsang (shape ≥ 1), Poisson theo phép nhân của Knuth — đủ
     * chính xác với tham số tới vài trăm (exp(−λ) chưa tràn dưới cho tới λ ≈ 700).
     */
    static int negativeBinomial(double mean, Random random) {
        if (mean <= 0) {
            return 0;
        }
        double lambda = gamma(NB_SIZE, random) * mean / NB_SIZE;
        double limit = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > limit);
        return k - 1;
    }

    private static double gamma(double shape, Random random) {
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }
}
