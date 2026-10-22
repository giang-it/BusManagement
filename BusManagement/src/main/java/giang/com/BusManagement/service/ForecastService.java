package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Holiday;
import giang.com.BusManagement.domain.HolidayCalendar;
import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.DemandForecastViewDto.HolidayEffectRowDto;
import giang.com.BusManagement.dto.DemandForecastViewDto.HolidayEffectTableDto;
import giang.com.BusManagement.dto.DemandForecastViewDto.ModelInfoDto;
import giang.com.BusManagement.dto.DemandForecastViewDto.PreviewChartDto;
import giang.com.BusManagement.dto.ForecastAccuracyDto;
import giang.com.BusManagement.dto.ForecastAccuracyDto.ScoreDto;
import giang.com.BusManagement.dto.ForecastAccuracyDto.TetBacktestDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PHASE 6 — Demand Forecast. Thành phần "AI Prediction" của hệ thống. Viết lại ở GĐ5 của đợt
 * thiết kế lại dự báo (QĐ-16 trong docs/development/forecast_decisions.md).
 *
 * ====================================================================
 * DỰ BÁO CÁI GÌ: NHU CẦU CỦA KHUNG, KHÔNG PHẢI TỈ LỆ LẤP ĐẦY
 * ====================================================================
 * Đơn vị là KHUNG: một tuyến, một giờ khởi hành, một ngày — mọi chuyến của khung cộng lại, kể
 * cả chuyến tăng cường. Con số dự báo là số khách MUỐN đi (QĐ-2). Tỉ lệ lấp đầy của mô hình cũ
 * bị kẹp ở 100%, nên Tết làm nhu cầu gấp đôi cũng chỉ hiện ra là "100%", và không trả lời được
 * câu "cần mấy chuyến".
 *
 * Định nghĩa "chuyến nóng" vẫn là MỘT: nhu cầu vượt 90% số ghế xe chuẩn của tuyến — đúng ngưỡng
 * Trip.needsReinforcement(), chỉ thay số vé thật bằng nhu cầu dự báo. Số chuyến cần =
 * max(1, ⌈nhu cầu ÷ (0,9 × ghế xe chuẩn)⌉), và số này ≥ 2 đúng khi cờ bật (QĐ-16 G5-6, đóng M-4).
 *
 * Cầu nối tới GĐ6 (G5-1): Recommendation và What-if vẫn đọc predictedOccupancy (đã kẹp [0, 1])
 * và needsReinforcement như với mô hình cũ, nên chưa phải sửa dòng nào. Cái giá biết trước:
 * doanh thu thẻ vẫn tính "đầy xe × giá vé", và What-if dưới 100% nhu cầu đếm thiếu khung nóng vì
 * tỉ lệ đã bị kẹp trước khi nhân. GĐ6 chuyển hai màn đó sang đơn vị nhu cầu.
 *
 * ====================================================================
 * MÔ HÌNH
 * ====================================================================
 * SlotDemandModel (GĐ4, QĐ-15): hồi quy Poisson theo lịch — mức, thứ trong tuần, xu hướng gấp
 * khúc, hệ số lễ gộp theo chiều tuyến (Route.holidayFlow) — có EM khôi phục nhu cầu của khung bán
 * hết vé; đã khớp bản Python đo được tới 10⁻⁸. Service này chỉ gom dữ liệu, gọi mô hình và dựng
 * màn hình; nó không chứa phép toán nào của mô hình.
 *
 * ====================================================================
 * VÌ SAO CHỈ LẤY CHUYẾN COMPLETED (G5-2, đóng M-3)
 * ====================================================================
 * Đây là chuỗi nhu cầu ĐÃ ĐƯỢC PHỤC VỤ. Chuyến CANCELLED vẫn giữ nguyên số vé đã bán trước khi
 * hủy (cố ý, xem THESIS_ROADMAP.md), nên gộp vào sẽ trộn nhu cầu không được phục vụ vào số liệu
 * thực tế — ngày bão đóng cả tuyến sẽ thành nhu cầu giả. Khung không có chuyến hoàn thành là
 * KHÔNG QUAN SÁT ĐƯỢC chứ không phải "0 khách". Lưu ý bộ lọc này HẸP HƠN
 * DashboardService.OPERATIONAL_STATUSES — hằng số đó là bộ lọc DOANH THU, không phải bộ lọc lịch
 * sử, và không được dùng lại ở đây.
 *
 * ====================================================================
 * TÍNH MỘT LẦN, NHỚ TỚI KHI DỮ LIỆU ĐỔI (G5-4, đóng M-5)
 * ====================================================================
 * Ba màn gọi service này. Mỗi lần gọi vẫn đọc lại lịch sử (projection 4 cột) và gom lại; nếu đầu
 * vào của mô hình Y HỆT lần trước thì dùng lại kết quả đã khớp, khác dù một vé thì khớp lại.
 * Không bao giờ hiện số cũ, không cron (Non-Goal §4 "No daily cron job"), không bảng mới, không
 * đụng TripService. Phần tự chấm điểm (thêm 9 lần khớp) chỉ màn Dự báo cần, nên tách ra
 * buildForecastPage().
 *
 * ====================================================================
 * GIỚI HẠN PHẢI NÓI RÕ KHI BẢO VỆ
 * ====================================================================
 * Dữ liệu lịch sử hiện tại là MÔ PHỎNG (bộ sinh của GĐ2). Mô hình được chọn vì nó chính đáng với
 * vận tải hành khách — chu kỳ tuần, dịp lễ đi theo chiều, nhu cầu vượt một xe là hiện tượng có
 * thật — chứ không phải vì nó khớp với bộ sinh. Sai lệch tự chấm vẫn là sai lệch trên dữ liệu mô
 * phỏng và phải được trình bày đúng như vậy.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;

    /**
     * Số ngày quan sát tối thiểu để một nhóm (tuyến × giờ) được coi là chuỗi dự báo được. 28 = 4
     * tuần trọn, nên mỗi thứ trong tuần có ít nhất 4 điểm. Đếm NGÀY chứ không đếm chuyến: một khung
     * có thể có nhiều chuyến.
     *
     * Dùng ngưỡng thay vì liệt kê cứng các khung giờ hợp lệ là có chủ đích: ngoài các chuỗi do bộ
     * sinh tạo ra, dữ liệu thật còn lẫn vài chuyến lẻ do Admin tự tạo ở những giờ bất kỳ (13:00,
     * 16:00, 21:00...). Danh sách cứng sẽ lạc hậu ngay khi Admin tạo thêm một chuyến giờ lạ; ngưỡng
     * thì tự đúng mãi.
     */
    static final int MIN_OBSERVED_DAYS = 28;

    /** Chân trời của thẻ Recommendation và What-if (QĐ-5). */
    static final int FORECAST_HORIZON_DAYS = 7;

    /** Chân trời xem trước trên màn Dự báo (QĐ-5). */
    static final int PREVIEW_DAYS = 35;

    /** Số ngày gốc tự chấm điểm: mỗi tuần một ngày, trong những tuần cuối của lịch sử (G5-7). */
    static final int BACKTEST_ORIGINS = 8;

    /** SN4 sạch: trung bình cùng thứ của 4 tuần gần nhất, bỏ ngày lễ. */
    static final int SEASONAL_NAIVE_WEEKS = 4;

    /** Khối "Tết vừa qua" học tới Chủ nhật gần nhất cách Tết ít nhất chừng này ngày. */
    static final int TET_LEAD_DAYS = 21;

    /**
     * Ngưỡng "chuyến đông cần tăng cường".
     *
     * Khai báo lại tại đây theo đúng tiền lệ DashboardService.HOT_TRIP_OCCUPANCY_THRESHOLD — không
     * thể gọi Trip.needsReinforcement() vì ở đây không có entity Trip nào, giá trị đang xét là một
     * con số DỰ BÁO. Nếu ngưỡng trong Trip đổi, phải đổi ở đây.
     */
    private static final double REINFORCEMENT_THRESHOLD = 0.90;

    /** Nhãn thứ trong tuần, đánh chỉ số theo DayOfWeek.getValue() (1 = Thứ Hai). */
    private static final String[] DAY_LABELS = { "", "T2", "T3", "T4", "T5", "T6", "T7", "CN" };

    /**
     * Nhóm học chung hệ số lễ, theo Route.holidayFlow. Đánh số đúng như bản Python đã đo
     * (forecast_research/gd4/reference.py), để số trên màn hình đối chiếu được với nó.
     */
    static final int GROUP_LEAVING_HUB = 0;
    static final int GROUP_TOWARD_HUB = 1;
    static final int GROUP_NO_FLOW = 2;

    /** Cùng chữ với route-form.html và route-list.html. */
    private static final String[] GROUP_LABELS = { "Rời TP lớn", "Về TP lớn", "Không theo chiều" };

    private static final Map<Holiday, String> HOLIDAY_NAMES = new EnumMap<>(Map.of(
            Holiday.NEW_YEAR, "Tết Dương lịch",
            Holiday.LUNAR_NEW_YEAR, "Tết Nguyên đán",
            Holiday.HUNG_KINGS_COMMEMORATION, "Giỗ Tổ Hùng Vương",
            Holiday.REUNIFICATION_AND_LABOUR_DAY, "30/4 – 1/5",
            Holiday.NATIONAL_DAY, "Quốc khánh 2/9"));

    /** Tên ngắn cho ô của bảng, ví dụ "Tết D−4". */
    private static final Map<Holiday, String> HOLIDAY_SHORT_NAMES = new EnumMap<>(Map.of(
            Holiday.NEW_YEAR, "Tết DL",
            Holiday.LUNAR_NEW_YEAR, "Tết",
            Holiday.HUNG_KINGS_COMMEMORATION, "Giỗ Tổ",
            Holiday.REUNIFICATION_AND_LABOUR_DAY, "30/4",
            Holiday.NATIONAL_DAY, "2/9"));

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    /** Khóa một chuỗi: một tuyến ở một giờ khởi hành. Sắp theo tuyến rồi giờ. */
    record SlotKey(long routeId, int hour) implements Comparable<SlotKey> {
        @Override
        public int compareTo(SlotKey other) {
            int byRoute = Long.compare(routeId, other.routeId);
            return byRoute != 0 ? byRoute : Integer.compare(hour, other.hour);
        }
    }

    /** Một ngày-khung đã chấm: dự báo của mô hình, của SN4 sạch, và số thật của khung. */
    record Scored(int series, double model, double baseline, double ticketsSold, double seatsRun) {
    }

    /** Lần khớp chính; giữ trong bộ nhớ cùng đầu vào sinh ra nó. */
    private record ModelRun(SlotDemandModel.Fit fit, long fitMillis, LocalDateTime computedAt) {
    }

    /** Kết quả thô của phần tự chấm điểm. Điểm số tính lại mỗi lần từ đây, vì ghế xe chuẩn có thể đổi. */
    record BacktestRun(List<LocalDate> origins, List<Scored> recent, TetRun tet) {
    }

    /** Khối "Tết vừa qua": dự báo của mô hình và SN4 cho 35 ngày, theo các chuỗi đủ lịch sử tại ngày gốc. */
    record TetRun(LocalDate tetDay, LocalDate origin, int[] series, double[][] model, double[][] baseline,
            double[][] ticketsSold, double[][] seatsRun, List<Scored> scored) {
    }

    private final Object cacheLock = new Object();
    private ModelInput cachedInput;
    private ModelRun cachedRun;
    private BacktestRun cachedBacktest;

    /**
     * Dự báo cho Recommendation, What-if và phần chính của màn Dự báo: 7 ngày (thẻ) và tới 35 ngày
     * (xem trước) kể từ NGÀY MAI — chuyến của hôm nay hoặc đã chạy, hoặc đang được luồng tăng cường
     * thời gian thực của TripService lo. Không tự chấm điểm: accuracy = null.
     */
    @Transactional(readOnly = true)
    public DemandForecastViewDto buildForecast() {
        return build(LocalDate.now(), false);
    }

    /** Như buildForecast(), kèm phần tự chấm điểm của màn Dự báo (QĐ-16, G5-7). */
    @Transactional(readOnly = true)
    public DemandForecastViewDto buildForecastPage() {
        return build(LocalDate.now(), true);
    }

    private DemandForecastViewDto build(LocalDate today, boolean withAccuracy) {
        LocalDate forecastFrom = today.plusDays(1);
        LocalDate calendarEnd = LocalDate.of(HolidayCalendar.LAST_COVERED_YEAR, 12, 31);

        SortedMap<SlotKey, SortedMap<LocalDate, int[]>> slots =
                aggregateSlots(tripRepository.findDemandHistoryByStatus(TripStatus.COMPLETED), today);
        List<SlotKey> keys = slots.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_OBSERVED_DAYS)
                .map(Map.Entry::getKey)
                .toList();

        DemandForecastViewDto view = new DemandForecastViewDto();
        view.setForecastFrom(forecastFrom);
        view.setHorizonDays(FORECAST_HORIZON_DAYS);
        view.setCalendarEnd(calendarEnd);
        view.setSeries(List.of());
        view.setSkippedSeriesCount(slots.size() - keys.size());
        view.setMinObservedDays(MIN_OBSERVED_DAYS);
        view.setHolidayEffects(List.of());

        // Phạm vi lịch sử: của các chuỗi đưa vào mô hình; chưa chuỗi nào đủ điều kiện thì của mọi
        // khung, để màn hình vẫn nói được dữ liệu đang tới đâu.
        List<SlotKey> rangeKeys = keys.isEmpty() ? List.copyOf(slots.keySet()) : keys;
        if (!rangeKeys.isEmpty()) {
            LocalDate start = rangeKeys.stream().map(k -> slots.get(k).firstKey()).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate end = rangeKeys.stream().map(k -> slots.get(k).lastKey()).max(Comparator.naturalOrder()).orElseThrow();
            view.setHistoryStart(start);
            view.setHistoryEnd(end);
            view.setHistoryStaleDays(ChronoUnit.DAYS.between(end, today));
        }
        if (keys.isEmpty()) {
            return view;
        }

        Map<Long, Route> routes = routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        int[] groups = keys.stream().mapToInt(k -> groupOf(routes.get(k.routeId()))).toArray();
        ModelInput input = ModelInput.of(keys, slots, groups);

        ModelRun run;
        boolean reused;
        synchronized (cacheLock) {
            reused = input.sameAs(cachedInput);
            if (!reused) {
                long started = System.nanoTime();
                SlotDemandModel.Fit fit = SlotDemandModel.fit(input.firstDay, input.sales, input.seats,
                        input.groups, input.origin);
                cachedRun = new ModelRun(fit, (System.nanoTime() - started) / 1_000_000, LocalDateTime.now());
                cachedInput = input;
                cachedBacktest = null; // phần chấm điểm thuộc về đầu vào cũ
            }
            run = cachedRun;
        }

        // Dự báo từ ngày mai. lead ≥ 1 vì ngày gốc không quá hôm nay; dữ liệu càng cũ thì lead càng
        // lớn (xu hướng vẫn đóng băng tại ngày gốc). Không vượt quá lịch ngày lễ.
        int lead = (int) ChronoUnit.DAYS.between(input.origin, forecastFrom);
        int previewDays = (int) Math.max(0,
                Math.min(PREVIEW_DAYS, ChronoUnit.DAYS.between(forecastFrom, calendarEnd) + 1));
        double[][] demand = previewDays > 0 ? run.fit().forecast(lead - 1 + previewDays) : new double[0][];

        int[] standardSeats = new int[keys.size()];
        boolean[] seatsFromRoute = new boolean[keys.size()];
        Integer fleetAverage = null;
        for (int s = 0; s < keys.size(); s++) {
            Route route = routes.get(keys.get(s).routeId());
            if (route != null && route.getSuitableBusType() != null && route.getSuitableBusType().getCapacity() > 0) {
                standardSeats[s] = route.getSuitableBusType().getCapacity();
                seatsFromRoute[s] = true;
            } else {
                // Tuyến chưa có loại xe phù hợp: sức chứa trung bình đội xe, cùng cách What-if làm.
                if (fleetAverage == null) {
                    fleetAverage = fleetAverageCapacity();
                }
                standardSeats[s] = fleetAverage;
            }
        }

        List<ForecastSeriesDto> series = new ArrayList<>();
        for (int s = 0; s < keys.size(); s++) {
            SlotKey key = keys.get(s);
            List<ForecastPointDto> preview = new ArrayList<>();
            for (int i = 0; i < previewDays; i++) {
                preview.add(point(forecastFrom.plusDays(i), demand[lead - 1 + i][s], standardSeats[s]));
            }
            List<ForecastPointDto> points = preview.subList(0, Math.min(FORECAST_HORIZON_DAYS, previewDays));
            int soldOutDays = (int) slots.get(key).values().stream().filter(t -> t[0] >= t[1]).count();
            series.add(new ForecastSeriesDto(
                    key.routeId(),
                    routeLabel(routes.get(key.routeId()), key.routeId()),
                    key.hour(),
                    String.format("%02d:00", key.hour()),
                    GROUP_LABELS[groups[s]],
                    standardSeats[s],
                    seatsFromRoute[s],
                    slots.get(key).size(),
                    soldOutDays,
                    List.copyOf(points),
                    preview,
                    points.stream().mapToDouble(ForecastPointDto::getDemandRatio).max().orElse(0.0),
                    points.stream().anyMatch(ForecastPointDto::isNeedsReinforcement)));
        }
        List<ForecastSeriesDto> sorted = series.stream()
                .sorted(Comparator.comparingDouble(ForecastSeriesDto::getPeakDemandRatio).reversed()
                        .thenComparing(ForecastSeriesDto::getRouteLabel, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(ForecastSeriesDto::getDepartureHour))
                .toList();

        SlotDemandModel.Fit fit = run.fit();
        view.setHistoryStart(input.firstDay);
        view.setHistoryEnd(input.origin);
        view.setHistoryStaleDays(ChronoUnit.DAYS.between(input.origin, today));
        view.setPreviewDays(previewDays);
        view.setSeries(sorted);
        view.setReinforcementSignalCount(sorted.stream()
                .flatMap(x -> x.getPoints().stream())
                .filter(ForecastPointDto::isNeedsReinforcement)
                .count());
        view.setModel(new ModelInfoDto(fit.observations(), fit.censored(), SlotDemandModel.EM_ROUNDS,
                fit.iterations(), fit.negativeBinomialSize(), run.fitMillis(), run.computedAt(), reused));
        view.setHolidayEffects(holidayEffects(fit, groups));
        view.setPreviewChart(previewChart(sorted, forecastFrom, previewDays));

        if (withAccuracy) {
            BacktestRun backtest;
            synchronized (cacheLock) {
                if (cachedBacktest == null || !input.sameAs(cachedInput)) {
                    backtest = runBacktest(input);
                    if (input.sameAs(cachedInput)) {
                        cachedBacktest = backtest;
                    }
                } else {
                    backtest = cachedBacktest;
                }
            }
            String[] labels = new String[keys.size()];
            for (int s = 0; s < keys.size(); s++) {
                labels[s] = routeLabel(routes.get(keys.get(s).routeId()), keys.get(s).routeId()) + " — "
                        + String.format("%02d:00", keys.get(s).hour());
            }
            view.setAccuracy(accuracy(backtest, standardSeats, labels));
        }
        return view;
    }

    // =========================================================================
    // GOM DỮ LIỆU THEO KHUNG (G5-2, đóng M-3)
    // =========================================================================

    /**
     * Gom các chuyến thành khung: cộng số vé và số ghế của mọi chuyến cùng (tuyến, giờ khởi hành,
     * ngày) — chuyến tăng cường khởi hành phút 30/40/50 nên tự rơi vào khung của chuyến gốc. Khung
     * bán hết vé khi tổng vé ≥ tổng ghế.
     *
     * Bỏ những dòng ngày nằm ngoài lịch ngày lễ (HolidayCalendar sẽ ném lỗi và làm sập trang) hoặc
     * sau hôm nay (chưa thể là lịch sử). Tách riêng và để package-private để test chốt được luật
     * gom mà không cần DB.
     *
     * @param rows  (routeId, departureTime, ticketsSold, totalSeats) như
     *              TripRepository.findDemandHistoryByStatus trả về
     * @return khung theo chuỗi, mỗi khung là {tổng vé, tổng ghế}, sắp theo tuyến, giờ, ngày
     */
    static SortedMap<SlotKey, SortedMap<LocalDate, int[]>> aggregateSlots(List<Object[]> rows, LocalDate today) {
        SortedMap<SlotKey, SortedMap<LocalDate, int[]>> slots = new TreeMap<>();
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null || row[2] == null || row[3] == null) {
                continue;
            }
            int sold = ((Number) row[2]).intValue();
            int seats = ((Number) row[3]).intValue();
            if (seats <= 0 || sold < 0) {
                continue; // query đã lọc totalSeats > 0; giữ lại làm lưới an toàn
            }
            LocalDateTime departure = (LocalDateTime) row[1];
            LocalDate date = departure.toLocalDate();
            if (date.isAfter(today) || !HolidayCalendar.covers(date)) {
                continue;
            }
            int[] total = slots.computeIfAbsent(new SlotKey(((Number) row[0]).longValue(), departure.getHour()),
                    k -> new TreeMap<>()).computeIfAbsent(date, d -> new int[2]);
            total[0] += sold;
            total[1] += seats;
        }
        return slots;
    }

    /**
     * Đầu vào của một lần khớp — cũng là khóa nhớ (G5-4). Đầu vào giống hệt thì kết quả giống hệt,
     * nên so nguyên cả ma trận chứ không so "dấu vân tay" như đếm và tổng — hai chỉnh sửa bù trừ
     * nhau sẽ lọt qua dấu vân tay.
     */
    static final class ModelInput {
        final LocalDate firstDay;
        /** Ngày cuối có dữ liệu — ngày gốc của dự báo. */
        final LocalDate origin;
        final int[] groups;
        /** sales[ngày][chuỗi]: tổng vé của khung; NaN = không quan sát. */
        final double[][] sales;
        /** seats[ngày][chuỗi]: tổng ghế đã chạy của khung; NaN = không quan sát. */
        final double[][] seats;

        private ModelInput(LocalDate firstDay, LocalDate origin, int[] groups, double[][] sales, double[][] seats) {
            this.firstDay = firstDay;
            this.origin = origin;
            this.groups = groups;
            this.sales = sales;
            this.seats = seats;
        }

        static ModelInput of(List<SlotKey> keys, SortedMap<SlotKey, SortedMap<LocalDate, int[]>> slots, int[] groups) {
            LocalDate first = keys.stream().map(k -> slots.get(k).firstKey()).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate last = keys.stream().map(k -> slots.get(k).lastKey()).max(Comparator.naturalOrder()).orElseThrow();
            int days = (int) ChronoUnit.DAYS.between(first, last) + 1;
            double[][] sales = new double[days][keys.size()];
            double[][] seats = new double[days][keys.size()];
            for (int d = 0; d < days; d++) {
                Arrays.fill(sales[d], Double.NaN);
                Arrays.fill(seats[d], Double.NaN);
            }
            for (int s = 0; s < keys.size(); s++) {
                for (Map.Entry<LocalDate, int[]> e : slots.get(keys.get(s)).entrySet()) {
                    int d = (int) ChronoUnit.DAYS.between(first, e.getKey());
                    sales[d][s] = e.getValue()[0];
                    seats[d][s] = e.getValue()[1];
                }
            }
            return new ModelInput(first, last, groups.clone(), sales, seats);
        }

        int originIndex() {
            return sales.length - 1;
        }

        boolean sameAs(ModelInput other) {
            return other != null
                    && firstDay.equals(other.firstDay)
                    && origin.equals(other.origin)
                    && Arrays.equals(groups, other.groups)
                    && Arrays.deepEquals(sales, other.sales)
                    && Arrays.deepEquals(seats, other.seats);
        }
    }

    static int groupOf(Route route) {
        HolidayFlow flow = route == null ? null : route.getHolidayFlow();
        if (flow == null) {
            return GROUP_NO_FLOW;
        }
        return switch (flow) {
            case LEAVING_HUB -> GROUP_LEAVING_HUB;
            case TOWARD_HUB -> GROUP_TOWARD_HUB;
        };
    }

    // =========================================================================
    // NGƯỠNG TĂNG CƯỜNG VÀ SỐ CHUYẾN CẦN (G5-6, đóng M-4)
    // =========================================================================

    /** Nhu cầu vượt 90% số ghế xe chuẩn — cùng ngưỡng Trip.needsReinforcement(). */
    static boolean isHot(double demand, int standardSeats) {
        return standardSeats > 0 && demand / standardSeats > REINFORCEMENT_THRESHOLD;
    }

    /**
     * max(1, ⌈nhu cầu ÷ (0,9 × ghế xe chuẩn)⌉). Tính QUA isHot() để "cần ≥ 2 chuyến" và "cần tăng
     * cường" không bao giờ lệch nhau ở sát ngưỡng vì làm tròn dấu phẩy động — đó là một định
     * nghĩa, không phải hai.
     */
    static int tripsNeeded(double demand, int standardSeats) {
        if (!isHot(demand, standardSeats)) {
            return 1;
        }
        return Math.max(2, (int) Math.ceil(demand / (REINFORCEMENT_THRESHOLD * standardSeats)));
    }

    private ForecastPointDto point(LocalDate date, double demand, int standardSeats) {
        double ratio = standardSeats > 0 ? demand / standardSeats : 0.0;
        DayOfWeek day = date.getDayOfWeek();
        return new ForecastPointDto(
                date,
                DAY_LABELS[day.getValue()],
                isWeekend(day),
                holidayLabel(date),
                demand,
                ratio,
                Math.max(0.0, Math.min(1.0, ratio)),
                isHot(demand, standardSeats),
                tripsNeeded(demand, standardSeats));
    }

    private int fleetAverageCapacity() {
        return (int) Math.round(busRepository.findAllWithBusType().stream()
                .filter(b -> b.getBusType() != null)
                .mapToInt(b -> b.getBusType().getCapacity())
                .average().orElse(0.0));
    }

    // =========================================================================
    // HỆ SỐ LỄ VÀ BIỂU ĐỒ
    // =========================================================================

    private record EffectKey(Holiday holiday, int offset, int group) {
    }

    /** Hệ số lễ gộp theo chiều mà mô hình học được: mỗi dịp một bảng, chỉ các nhóm có chuỗi. */
    private List<HolidayEffectTableDto> holidayEffects(SlotDemandModel.Fit fit, int[] groups) {
        TreeSet<Integer> present = new TreeSet<>();
        for (int g : groups) {
            present.add(g);
        }
        Map<EffectKey, Double> byKey = new HashMap<>();
        for (SlotDemandModel.HolidayEffect e : fit.holidayEffects()) {
            byKey.put(new EffectKey(e.holiday(), e.offset(), e.group()), e.multiplier());
        }
        List<HolidayEffectTableDto> tables = new ArrayList<>();
        for (Holiday holiday : Holiday.values()) {
            List<String> offsetLabels = new ArrayList<>();
            for (int o = -holiday.getDaysBefore(); o <= holiday.getDaysAfter(); o++) {
                offsetLabels.add(offsetLabel(o));
            }
            List<HolidayEffectRowDto> rows = new ArrayList<>();
            for (int g : present) {
                List<Double> values = new ArrayList<>();
                double peak = Double.NEGATIVE_INFINITY;
                double low = Double.POSITIVE_INFINITY;
                int peakOffset = 0;
                int lowOffset = 0;
                for (int o = -holiday.getDaysBefore(); o <= holiday.getDaysAfter(); o++) {
                    Double m = byKey.get(new EffectKey(holiday, o, g));
                    values.add(m);
                    if (m != null && m > peak) {
                        peak = m;
                        peakOffset = o;
                    }
                    if (m != null && m < low) {
                        low = m;
                        lowOffset = o;
                    }
                }
                if (peak != Double.NEGATIVE_INFINITY) {
                    rows.add(new HolidayEffectRowDto(GROUP_LABELS[g], values, peak, offsetLabel(peakOffset), low,
                            offsetLabel(lowOffset)));
                }
            }
            if (!rows.isEmpty()) {
                tables.add(new HolidayEffectTableDto(HOLIDAY_NAMES.get(holiday), offsetLabels, rows));
            }
        }
        return tables;
    }

    private PreviewChartDto previewChart(List<ForecastSeriesDto> series, LocalDate forecastFrom, int previewDays) {
        List<String> dayLabels = new ArrayList<>();
        List<String> holidayLabels = new ArrayList<>();
        for (int i = 0; i < previewDays; i++) {
            LocalDate date = forecastFrom.plusDays(i);
            dayLabels.add(date.format(DAY_MONTH) + " " + DAY_LABELS[date.getDayOfWeek().getValue()]);
            String label = holidayLabel(date);
            holidayLabels.add(label == null ? "" : label);
        }
        return new PreviewChartDto(
                dayLabels,
                holidayLabels,
                series.stream().map(s -> s.getRouteLabel() + " — " + s.getSlotLabel()).toList(),
                series.stream().map(s -> s.getPreview().stream().map(ForecastPointDto::getPredictedDemand).toList())
                        .toList(),
                series.stream().map(ForecastSeriesDto::getStandardSeats).toList());
    }

    // =========================================================================
    // TỰ CHẤM ĐIỂM (G5-7)
    // =========================================================================

    /**
     * Lùi về BACKTEST_ORIGINS ngày gốc, mỗi tuần một ngày, trong những tuần cuối của lịch sử. Ở mỗi
     * ngày gốc, mô hình chỉ được học dữ liệu tới ngày đó — ma trận đưa vào bị cắt đúng tại ngày gốc,
     * nên không thể rò rỉ — rồi dự báo 7 ngày sau; SN4 sạch dự báo cùng những ngày đó từ cùng dữ
     * liệu. Chỉ chấm ngày-khung có số vé thật và có giá trị SN4.
     *
     * Chuỗi nào chưa đủ MIN_OBSERVED_DAYS ngày tại một ngày gốc thì không vào lần khớp đó — đúng
     * luật chọn chuỗi của dự báo thật tại thời điểm ấy.
     */
    static BacktestRun runBacktest(ModelInput input) {
        boolean[] holiday = holidayDays(input);
        List<LocalDate> origins = new ArrayList<>();
        List<Scored> recent = new ArrayList<>();
        for (int k = BACKTEST_ORIGINS; k >= 1; k--) {
            int originIndex = input.originIndex() - FORECAST_HORIZON_DAYS * k;
            int[] columns = originIndex < 0 ? new int[0] : seriesWithHistory(input, originIndex);
            if (columns.length == 0) {
                continue;
            }
            double[][] forecast = fitAt(input, columns, originIndex).forecast(FORECAST_HORIZON_DAYS);
            for (int h = 1; h <= FORECAST_HORIZON_DAYS; h++) {
                int d = originIndex + h;
                for (int j = 0; j < columns.length; j++) {
                    int s = columns[j];
                    double baseline = seasonalNaive(input.sales, holiday, s, originIndex, d);
                    if (Double.isFinite(input.sales[d][s]) && Double.isFinite(baseline)) {
                        recent.add(new Scored(s, forecast[h - 1][j], baseline, input.sales[d][s], input.seats[d][s]));
                    }
                }
            }
            origins.add(input.firstDay.plusDays(originIndex));
        }
        return new BacktestRun(origins, recent, runTet(input, holiday));
    }

    /**
     * Khối "Tết vừa qua": Tết gần nhất mà cả 35 ngày sau ngày gốc đã nằm trong lịch sử, và lịch sử
     * còn chứa một Tết trước đó để học. Ngày gốc là Chủ nhật gần nhất cách Tết ít nhất 3 tuần — cố
     * định theo Tết, nên không đổi khi lịch sử được chạy bổ sung. Với Tết 2026 đó là 25/01/2026,
     * đúng ngày gốc của file đối chiếu GĐ4 (reference_forecast.csv).
     */
    static TetRun runTet(ModelInput input, boolean[] holiday) {
        for (int year = input.origin.getYear(); year > HolidayCalendar.FIRST_COVERED_YEAR; year--) {
            LocalDate tetDay = HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year);
            LocalDate origin = tetDay.minusDays(TET_LEAD_DAYS).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            if (origin.plusDays(PREVIEW_DAYS).isAfter(input.origin)) {
                continue; // 35 ngày sau ngày gốc chưa trọn trong lịch sử
            }
            if (HolidayCalendar.anchorOf(Holiday.LUNAR_NEW_YEAR, year - 1).isBefore(input.firstDay)) {
                return null; // chưa có Tết nào trước đó để học, các năm sớm hơn càng không
            }
            int originIndex = (int) ChronoUnit.DAYS.between(input.firstDay, origin);
            int[] columns = seriesWithHistory(input, originIndex);
            if (columns.length == 0) {
                return null;
            }
            double[][] model = fitAt(input, columns, originIndex).forecast(PREVIEW_DAYS);
            double[][] baseline = new double[PREVIEW_DAYS][columns.length];
            double[][] sold = new double[PREVIEW_DAYS][columns.length];
            double[][] seats = new double[PREVIEW_DAYS][columns.length];
            List<Scored> scored = new ArrayList<>();
            for (int h = 1; h <= PREVIEW_DAYS; h++) {
                int d = originIndex + h;
                for (int j = 0; j < columns.length; j++) {
                    int s = columns[j];
                    baseline[h - 1][j] = seasonalNaive(input.sales, holiday, s, originIndex, d);
                    sold[h - 1][j] = input.sales[d][s];
                    seats[h - 1][j] = input.seats[d][s];
                    if (Double.isFinite(sold[h - 1][j]) && Double.isFinite(baseline[h - 1][j])) {
                        scored.add(new Scored(s, model[h - 1][j], baseline[h - 1][j], sold[h - 1][j], seats[h - 1][j]));
                    }
                }
            }
            return new TetRun(tetDay, origin, columns, model, baseline, sold, seats, scored);
        }
        return null;
    }

    /**
     * SN4 sạch cho ngày target: trung bình số vé của 4 ngày cùng thứ gần nhất tính tới ngày gốc,
     * BỎ ngày thuộc cửa sổ lễ; ngày không quan sát vẫn chiếm chỗ trong 4 ngày nhưng không vào trung
     * bình. Đúng cách bước 0 và GĐ2 đã đo (forecast_research/step0/methods.py, sn_table). NaN khi cả
     * 4 ngày đều không quan sát.
     */
    static double seasonalNaive(double[][] sales, boolean[] holiday, int series, int originIndex, int targetIndex) {
        double sum = 0;
        int observed = 0;
        int dates = 0;
        for (int d = originIndex - Math.floorMod(originIndex - targetIndex, 7); d >= 0 && dates < SEASONAL_NAIVE_WEEKS;
                d -= 7) {
            if (holiday[d]) {
                continue;
            }
            dates++;
            if (Double.isFinite(sales[d][series])) {
                sum += sales[d][series];
                observed++;
            }
        }
        return observed > 0 ? sum / observed : Double.NaN;
    }

    /**
     * Chấm một tập ngày-khung. App không biết nhu cầu thật, chỉ biết số vé, và khung bán hết chỉ cho
     * biết "nhu cầu ≥ số ghế" — nên so min(dự báo, số ghế đã chạy) với số vé, cho cả mô hình lẫn
     * SN4. So thẳng nhu cầu với số vé sẽ phạt mô hình đúng lúc nó đúng; chỉ chấm khung chưa bán hết
     * là chọn mẫu theo kết quả (forecast_discussion.md §15).
     *
     * Kèm hai thước đo cho quyết định: khung bán hết mà dự báo đã ≥ số ghế ("báo trước được"), và cờ
     * tăng cường so với khung thực tế đông (số vé > 90% ghế xe chuẩn).
     */
    static ScoreDto score(List<Scored> rows, int[] standardSeats) {
        double soldTotal = 0;
        double modelError = 0;
        double baselineError = 0;
        int soldOut = 0;
        int modelCaught = 0;
        int baselineCaught = 0;
        int hot = 0;
        int modelFlags = 0;
        int baselineFlags = 0;
        int modelHits = 0;
        int baselineHits = 0;
        for (Scored r : rows) {
            soldTotal += r.ticketsSold();
            modelError += Math.abs(Math.min(r.model(), r.seatsRun()) - r.ticketsSold());
            baselineError += Math.abs(Math.min(r.baseline(), r.seatsRun()) - r.ticketsSold());
            if (r.ticketsSold() >= r.seatsRun()) {
                soldOut++;
                modelCaught += r.model() >= r.seatsRun() ? 1 : 0;
                baselineCaught += r.baseline() >= r.seatsRun() ? 1 : 0;
            }
            int seats = standardSeats[r.series()];
            boolean actuallyHot = isHot(r.ticketsSold(), seats);
            hot += actuallyHot ? 1 : 0;
            if (isHot(r.model(), seats)) {
                modelFlags++;
                modelHits += actuallyHot ? 1 : 0;
            }
            if (isHot(r.baseline(), seats)) {
                baselineFlags++;
                baselineHits += actuallyHot ? 1 : 0;
            }
        }
        Double modelWape = percent(modelError, soldTotal);
        Double baselineWape = percent(baselineError, soldTotal);
        return new ScoreDto(rows.size(), modelWape, baselineWape,
                modelWape != null && baselineWape != null && modelWape < baselineWape,
                soldOut, percent(modelCaught, soldOut), percent(baselineCaught, soldOut),
                hot, percent(modelHits, hot), percent(baselineHits, hot),
                percent(modelHits, modelFlags), percent(baselineHits, baselineFlags));
    }

    private static Double percent(double part, double whole) {
        return whole > 0 ? 100.0 * part / whole : null;
    }

    private ForecastAccuracyDto accuracy(BacktestRun run, int[] standardSeats, String[] seriesLabels) {
        TetBacktestDto tet = null;
        if (run.tet() != null) {
            TetRun t = run.tet();
            List<String> dayLabels = new ArrayList<>();
            for (int h = 1; h <= PREVIEW_DAYS; h++) {
                LocalDate date = t.origin().plusDays(h);
                dayLabels.add(date.format(DAY_MONTH) + (date.equals(t.tetDay()) ? " Tết" : ""));
            }
            tet = new TetBacktestDto(t.tetDay(), t.origin(), t.origin().plusDays(1), t.origin().plusDays(PREVIEW_DAYS),
                    score(t.scored(), standardSeats),
                    dayLabels,
                    Arrays.stream(t.series()).mapToObj(s -> seriesLabels[s]).toList(),
                    byColumn(t.model()), byColumn(t.baseline()), byColumn(t.ticketsSold()), byColumn(t.seatsRun()));
        }
        if (run.recent().isEmpty()) {
            return new ForecastAccuracyDto(false, 0, null, null, FORECAST_HORIZON_DAYS, null, tet);
        }
        return new ForecastAccuracyDto(true, run.origins().size(), run.origins().get(0),
                run.origins().get(run.origins().size() - 1), FORECAST_HORIZON_DAYS,
                score(run.recent(), standardSeats), tet);
    }

    /** [ngày][cột] → danh sách theo cột, mỗi cột theo ngày; NaN thành null để Chart.js bỏ trống điểm đó. */
    private static List<List<Double>> byColumn(double[][] values) {
        List<List<Double>> out = new ArrayList<>();
        int columns = values.length == 0 ? 0 : values[0].length;
        for (int j = 0; j < columns; j++) {
            List<Double> column = new ArrayList<>();
            for (double[] day : values) {
                column.add(Double.isFinite(day[j]) ? day[j] : null);
            }
            out.add(column);
        }
        return out;
    }

    private static boolean[] holidayDays(ModelInput input) {
        boolean[] holiday = new boolean[input.sales.length];
        for (int d = 0; d < holiday.length; d++) {
            holiday[d] = !HolidayCalendar.holidaysOn(input.firstDay.plusDays(d)).isEmpty();
        }
        return holiday;
    }

    /** Các chuỗi có ít nhất MIN_OBSERVED_DAYS ngày quan sát tính tới hết ngày originIndex. */
    private static int[] seriesWithHistory(ModelInput input, int originIndex) {
        List<Integer> columns = new ArrayList<>();
        for (int s = 0; s < input.groups.length; s++) {
            int observed = 0;
            for (int d = 0; d <= originIndex; d++) {
                if (Double.isFinite(input.sales[d][s])) {
                    observed++;
                }
            }
            if (observed >= MIN_OBSERVED_DAYS) {
                columns.add(s);
            }
        }
        return columns.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Khớp trên các cột đã chọn, với dữ liệu CẮT tại ngày gốc — phần sau ngày gốc không hề được đưa vào. */
    private static SlotDemandModel.Fit fitAt(ModelInput input, int[] columns, int originIndex) {
        double[][] sales = new double[originIndex + 1][columns.length];
        double[][] seats = new double[originIndex + 1][columns.length];
        int[] groups = new int[columns.length];
        for (int j = 0; j < columns.length; j++) {
            groups[j] = input.groups[columns[j]];
            for (int d = 0; d <= originIndex; d++) {
                sales[d][j] = input.sales[d][columns[j]];
                seats[d][j] = input.seats[d][columns[j]];
            }
        }
        return SlotDemandModel.fit(input.firstDay, sales, seats, groups, input.firstDay.plusDays(originIndex));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Dịp lễ của ngày, ví dụ "Tết D−4"; hai dịp chồng nhau thì nối bằng " · "; null nếu là ngày thường. */
    static String holidayLabel(LocalDate date) {
        if (!HolidayCalendar.covers(date)) {
            return null;
        }
        List<HolidayCalendar.HolidayDay> days = HolidayCalendar.holidaysOn(date);
        if (days.isEmpty()) {
            return null;
        }
        return days.stream()
                .map(d -> HOLIDAY_SHORT_NAMES.get(d.holiday()) + " " + offsetLabel(d.offset()))
                .collect(Collectors.joining(" · "));
    }

    /** "D−4", "D", "D+2" — dấu trừ là U+2212 như trong tài liệu ("Tết D−3"). */
    static String offsetLabel(int offset) {
        if (offset == 0) {
            return "D";
        }
        return offset < 0 ? "D−" + (-offset) : "D+" + offset;
    }

    private static String routeLabel(Route route, long routeId) {
        // findAllWithStations() chứ không phải findAll(): routeStations là LAZY và
        // getDeparturePointDisplay() sẽ ném LazyInitializationException nếu không JOIN FETCH sẵn.
        return route == null ? "Tuyến #" + routeId
                : route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay();
    }

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
