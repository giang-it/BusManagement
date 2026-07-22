package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastAccuracyDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PHASE 6 — Demand Forecast. Thành phần "AI Prediction" của hệ thống.
 *
 * ====================================================================
 * DỰ BÁO CÁI GÌ: TỈ LỆ LẤP ĐẦY, KHÔNG PHẢI SỐ VÉ
 * ====================================================================
 * Đây là ràng buộc kiến trúc chứ không phải lựa chọn phong cách. Hệ thống ĐÃ có
 * đúng một định nghĩa cho "chuyến này đang đông": Trip.getOccupancyRate() vượt
 * 0.90 (Trip.needsReinforcement()), và ngưỡng đó đang điều khiển toàn bộ luồng
 * đề xuất chuyến tăng cường trong TripService.scanAndSuggestExtraTrips().
 * DashboardService còn ghi rõ trên hằng số của nó: "không định nghĩa lại 'hot
 * trip'". Dự báo bằng tỉ lệ lấp đầy nghĩa là Phase 7 dùng lại nguyên ngưỡng
 * nghiệp vụ ấy, chỉ thay đầu vào từ số thực tế sang số dự báo. Nếu dự báo bằng
 * SỐ VÉ, Phase 7 sẽ buộc phải đẻ ra ngưỡng thứ hai bằng đơn vị mà codebase chưa
 * từng dùng — đúng thứ nguyên tắc "không nhân bản logic nghiệp vụ" cấm.
 *
 * Có một lý do đo được nữa: số vé = tỉ lệ lấp đầy × sức chứa của CHIẾC XE được
 * xếp hôm đó, mà đội xe có ba loại 22/40/50 ghế và bộ sinh dữ liệu quay vòng xe
 * qua các ngày. Đo trên dữ liệu thật: hệ số biến thiên của số vé là 0,32–0,35
 * còn của tỉ lệ lấp đầy chỉ 0,08–0,11, và tỉ lệ lấp đầy trung bình theo ba loại
 * xe gần như trùng khít (0,700 / 0,705 / 0,702). Nói cách khác, phần lớn dao
 * động của số vé là chuyện xe nào được xếp, không phải chuyện khách có đông
 * không — dự báo trên nó là dự báo nhầm đối tượng.
 *
 * ====================================================================
 * MÔ HÌNH
 * ====================================================================
 * Mỗi chuỗi = (tuyến × khung giờ khởi hành). Với từng chuỗi:
 *
 * 1. Khử mùa vụ: chia tỉ lệ lấp đầy cho hệ số thứ-trong-tuần.
 * 2. Hồi quy tuyến tính trên phần đã khử mùa vụ -> mức nền + xu hướng.
 * 3. Dự báo ngày D = (mức nền + xu hướng × số ngày) × hệ số thứ của D.
 *
 * Hệ số thứ-trong-tuần được tính GỘP trên toàn bộ quan sát đủ điều kiện, không
 * tính riêng từng chuỗi: mỗi chuỗi chỉ có ~78 quan sát, chia cho 7 thứ còn ~11
 * điểm mỗi thứ — quá mỏng để ước lượng ổn định. Gộp lại thì mỗi thứ có hàng
 * trăm điểm.
 *
 * Vì sao cần bước mùa vụ: đo trên dữ liệu thật, cuối tuần lấp đầy 0,795 còn
 * ngày thường 0,666 (+19%). Bỏ bước này thì mọi dự báo cho thứ Bảy/Chủ nhật
 * đều hụt gần một phần năm — sai lệch lớn hơn nhiều so với sai lệch do bỏ qua
 * xu hướng (~15%/12 tuần, tức chỉ ~1,25% trên chân trời 7 ngày).
 *
 * ====================================================================
 * VÌ SAO CHỈ LẤY CHUYẾN COMPLETED
 * ====================================================================
 * Đây là chuỗi nhu cầu ĐÃ ĐƯỢC PHỤC VỤ. Chuyến CANCELLED vẫn giữ nguyên số vé
 * đã bán trước khi hủy (cố ý, xem THESIS_ROADMAP.md), nên gộp vào sẽ trộn nhu
 * cầu không được phục vụ vào số liệu thực tế. Lưu ý bộ lọc này HẸP HƠN
 * DashboardService.OPERATIONAL_STATUSES — hằng số đó là bộ lọc DOANH THU, không
 * phải bộ lọc lịch sử, và không được dùng lại ở đây.
 *
 * ====================================================================
 * GIỚI HẠN PHẢI NÓI RÕ KHI BẢO VỆ
 * ====================================================================
 * Dữ liệu lịch sử hiện tại là MÔ PHỎNG (Phase 5). Phương pháp ở đây được chọn
 * vì nó chính đáng với vận tải hành khách — chu kỳ tuần và xu hướng mùa là hiện
 * tượng có thật trong ngành — chứ không phải vì nó khớp với bộ sinh dữ liệu.
 * Nhưng con số sai lệch đo được ở dưới vẫn là sai lệch trên dữ liệu mô phỏng,
 * và phải được trình bày đúng như vậy.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;

    /**
     * Số quan sát tối thiểu để một nhóm (tuyến × giờ) được coi là chuỗi dự báo
     * được. 28 = 4 tuần trọn, nên mỗi thứ trong tuần có ít nhất 4 điểm.
     *
     * Dùng ngưỡng thay vì liệt kê cứng các khung giờ hợp lệ là có chủ đích:
     * ngoài 15 chuỗi do backfill sinh ra, dữ liệu thật còn lẫn vài chuyến lẻ do
     * Admin tự tạo ở những giờ bất kỳ (21:00, 13:00, 16:00...). Danh sách loại
     * trừ cứng sẽ lạc hậu ngay khi Admin tạo thêm một chuyến giờ lạ nữa; ngưỡng
     * thì tự đúng mãi.
     */
    static final int MIN_OBSERVATIONS = 28;

    /** Số ngày dự báo về phía trước. */
    static final int FORECAST_HORIZON_DAYS = 7;

    /**
     * Cửa sổ trung bình trượt, tính theo NGÀY LỊCH chứ không phải "7 quan sát
     * gần nhất". Khác biệt này quan trọng: ~6% chuyến bị hủy đã bị loại nên mỗi
     * chuỗi thủng vài ngày, và "7 quan sát gần nhất" sẽ trải trên hơn 7 ngày,
     * phủ lệch các thứ trong tuần và tự nhiễm lại đúng cái mùa vụ mà trung bình
     * trượt đáng lẽ phải khử.
     */
    static final int MOVING_AVERAGE_DAYS = 7;

    /** Số ngày cuối được giấu đi để chấm điểm mô hình. */
    static final int BACKTEST_DAYS = 14;

    /** Dưới mức này thì phần huấn luyện quá ngắn, chuỗi bị bỏ khỏi backtest. */
    private static final int MIN_TRAINING_OBSERVATIONS = 14;

    /**
     * Ngưỡng "chuyến đông cần tăng cường".
     *
     * Khai báo lại tại đây theo đúng tiền lệ DashboardService
     * .HOT_TRIP_OCCUPANCY_THRESHOLD — không thể gọi Trip.needsReinforcement()
     * vì ở đây không có entity Trip nào, giá trị đang xét là một con số DỰ BÁO.
     * Nếu ngưỡng trong Trip đổi, phải đổi ở đây.
     */
    private static final double REINFORCEMENT_THRESHOLD = 0.90;

    /** Nhãn thứ trong tuần, đánh chỉ số theo DayOfWeek.getValue() (1 = Thứ Hai). */
    private static final String[] DAY_LABELS = { "", "T2", "T3", "T4", "T5", "T6", "T7", "CN" };

    /** Một chuyến đã hoàn thành, rút gọn còn đúng những gì việc dự báo cần. */
    private record Observation(long routeId, int hour, LocalDate date, double occupancy) {
    }

    /** Khóa nhóm: một tuyến ở một khung giờ khởi hành. */
    private record SeriesKey(long routeId, int hour) {
    }

    @Transactional(readOnly = true)
    public DemandForecastViewDto buildForecast() {
        LocalDate today = LocalDate.now();
        // Dự báo bắt đầu từ NGÀY MAI: chuyến của hôm nay hoặc đã chạy, hoặc đang
        // được luồng đề xuất tăng cường thời gian thực của TripService lo. Màn
        // hình này phục vụ việc lên kế hoạch, nên nhìn về phía trước.
        LocalDate forecastFrom = today.plusDays(1);

        List<Observation> history = loadHistory();
        if (history.isEmpty()) {
            return emptyView(forecastFrom);
        }

        LocalDate historyStart = history.stream().map(Observation::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate historyEnd = history.stream().map(Observation::date).max(Comparator.naturalOrder()).orElseThrow();

        Map<SeriesKey, List<Observation>> allGroups = history.stream()
                .collect(Collectors.groupingBy(o -> new SeriesKey(o.routeId(), o.hour())));

        Map<SeriesKey, List<Observation>> qualified = allGroups.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_OBSERVATIONS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Không có nhánh riêng cho "không chuỗi nào đủ điều kiện": đường code dưới
        // đây đã xử lý đúng trường hợp đó (pool rỗng -> 0 quan sát được dùng, không
        // chuỗi nào, backtest báo không khả dụng). Một nhánh riêng từng tồn tại ở
        // đây và đã gây ra chính lỗi mà nó lẽ ra phải tránh: nó gán
        // totalObservations = TOÀN BỘ lịch sử trong khi thẻ trên giao diện ghi
        // "chuyến lịch sử ĐÃ DÙNG" và thực tế không chuyến nào được dùng. Hai nhánh
        // cùng dựng một DTO là hai cơ hội để hai định nghĩa lệch nhau — giữ một.
        List<Observation> pool = qualified.values().stream().flatMap(List::stream).toList();
        double[] dayFactors = dayOfWeekFactors(pool);
        Map<Long, String> routeLabels = loadRouteLabels();

        List<ForecastSeriesDto> series = qualified.entrySet().stream()
                .map(e -> buildSeries(e.getKey(), e.getValue(), dayFactors, routeLabels, forecastFrom))
                .sorted(Comparator.comparingDouble(ForecastSeriesDto::getPeakOccupancy).reversed())
                .toList();

        long reinforcementSignals = series.stream()
                .flatMap(s -> s.getPoints().stream())
                .filter(ForecastPointDto::isNeedsReinforcement)
                .count();

        return new DemandForecastViewDto(
                historyStart,
                historyEnd,
                ChronoUnit.DAYS.between(historyEnd, today),
                pool.size(),
                forecastFrom,
                FORECAST_HORIZON_DAYS,
                series,
                allGroups.size() - qualified.size(),
                MIN_OBSERVATIONS,
                describeDayFactors(pool, dayFactors),
                backtest(qualified.values(), historyEnd),
                reinforcementSignals);
    }

    // =========================================================================
    // NẠP DỮ LIỆU
    // =========================================================================

    private List<Observation> loadHistory() {
        return tripRepository.findDemandHistoryByStatus(TripStatus.COMPLETED).stream()
                .map(this::toObservation)
                .filter(Objects::nonNull)
                .toList();
    }

    private Observation toObservation(Object[] row) {
        if (row[0] == null || row[1] == null || row[2] == null || row[3] == null) {
            return null;
        }
        long routeId = ((Number) row[0]).longValue();
        LocalDateTime departure = (LocalDateTime) row[1];
        int sold = ((Number) row[2]).intValue();
        int seats = ((Number) row[3]).intValue();
        if (seats <= 0) {
            return null; // đã lọc ở DB, giữ lại làm lưới an toàn cho phép chia
        }
        // Kẹp về [0,1]: validation nghiệp vụ không cho bán quá số ghế, nhưng nếu
        // dữ liệu cũ có dòng lệch thì một "tỉ lệ" > 100% sẽ làm hỏng ý nghĩa của
        // cả hệ số mùa vụ lẫn ngưỡng tăng cường.
        double occupancy = Math.max(0.0, Math.min(1.0, (double) sold / seats));
        return new Observation(routeId, departure.getHour(), departure.toLocalDate(), occupancy);
    }

    private Map<Long, String> loadRouteLabels() {
        // findAllWithStations() chứ không phải findAll(): routeStations là LAZY và
        // getDeparturePointDisplay() sẽ ném LazyInitializationException nếu không
        // JOIN FETCH sẵn (xem javadoc của chính repository).
        return routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(
                        Route::getId,
                        r -> r.getDeparturePointDisplay() + " → " + r.getDestinationPointDisplay()));
    }

    // =========================================================================
    // MÙA VỤ THEO THỨ TRONG TUẦN
    // =========================================================================

    /**
     * Hệ số nhân cho từng thứ trong tuần: trung bình của thứ đó chia cho trung
     * bình chung. Trả về mảng đánh chỉ số theo DayOfWeek.getValue() (1..7).
     *
     * Thứ nào không có dữ liệu giữ hệ số 1.0 (không điều chỉnh) thay vì 0 — hệ
     * số 0 sẽ xóa sổ mọi dự báo rơi vào thứ đó.
     */
    private double[] dayOfWeekFactors(List<Observation> observations) {
        double[] factors = new double[8];
        Arrays.fill(factors, 1.0);

        double overall = observations.stream().mapToDouble(Observation::occupancy).average().orElse(0.0);
        if (overall <= 0) {
            return factors;
        }
        Map<DayOfWeek, List<Observation>> byDay = observations.stream()
                .collect(Collectors.groupingBy(o -> o.date().getDayOfWeek()));
        byDay.forEach((day, rows) -> {
            double mean = rows.stream().mapToDouble(Observation::occupancy).average().orElse(overall);
            factors[day.getValue()] = mean / overall;
        });
        return factors;
    }

    private List<DemandForecastViewDto.DayOfWeekFactorDto> describeDayFactors(List<Observation> pool,
            double[] factors) {
        if (pool.isEmpty()) {
            // Không có quan sát nào thì mọi hệ số đều là 1.00 mặc định — hiển thị
            // bảy ô "1.00 / 0 chuyến" chỉ là nhiễu, không phải thông tin.
            return List.of();
        }
        Map<DayOfWeek, Long> counts = pool.stream()
                .collect(Collectors.groupingBy(o -> o.date().getDayOfWeek(), Collectors.counting()));

        List<DemandForecastViewDto.DayOfWeekFactorDto> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            result.add(new DemandForecastViewDto.DayOfWeekFactorDto(
                    DAY_LABELS[day.getValue()],
                    isWeekend(day),
                    factors[day.getValue()],
                    counts.getOrDefault(day, 0L).intValue()));
        }
        return result;
    }

    // =========================================================================
    // XU HƯỚNG + DỰ BÁO
    // =========================================================================

    private ForecastSeriesDto buildSeries(SeriesKey key, List<Observation> observations, double[] dayFactors,
            Map<Long, String> routeLabels, LocalDate forecastFrom) {

        LocalDate origin = observations.stream().map(Observation::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate seriesEnd = observations.stream().map(Observation::date).max(Comparator.naturalOrder()).orElseThrow();

        double[] fit = fitTrend(observations, dayFactors, origin);
        double average = observations.stream().mapToDouble(Observation::occupancy).average().orElse(0.0);
        double movingAverage = movingAverage(observations, seriesEnd);

        List<ForecastPointDto> points = new ArrayList<>();
        for (int i = 0; i < FORECAST_HORIZON_DAYS; i++) {
            LocalDate date = forecastFrom.plusDays(i);
            double predicted = predict(fit, origin, date, dayFactors);
            points.add(new ForecastPointDto(
                    date,
                    DAY_LABELS[date.getDayOfWeek().getValue()],
                    isWeekend(date.getDayOfWeek()),
                    predicted,
                    predicted > REINFORCEMENT_THRESHOLD));
        }

        double peak = points.stream().mapToDouble(ForecastPointDto::getPredictedOccupancy).max().orElse(0.0);

        return new ForecastSeriesDto(
                key.routeId(),
                routeLabels.getOrDefault(key.routeId(), "Tuyến #" + key.routeId()),
                key.hour(),
                String.format("%02d:00", key.hour()),
                observations.size(),
                average,
                movingAverage,
                // Độ dốc/ngày quy về điểm phần trăm mỗi tuần, cho dễ đọc.
                fit[1] * 7 * 100,
                points,
                peak,
                points.stream().anyMatch(ForecastPointDto::isNeedsReinforcement));
    }

    /**
     * Hồi quy tuyến tính bình phương tối thiểu trên chuỗi ĐÃ KHỬ MÙA VỤ.
     *
     * Khử mùa vụ trước khi khớp là bắt buộc: nếu khớp thẳng trên số gốc, hệ số
     * góc sẽ bị các cụm cuối tuần kéo lệch tùy theo chuỗi bắt đầu và kết thúc
     * vào thứ mấy.
     *
     * @return {mức nền tại origin, độ dốc mỗi ngày}
     */
    private double[] fitTrend(List<Observation> observations, double[] dayFactors, LocalDate origin) {
        int n = observations.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        for (Observation o : observations) {
            double x = ChronoUnit.DAYS.between(origin, o.date());
            double factor = dayFactors[o.date().getDayOfWeek().getValue()];
            double y = factor > 0 ? o.occupancy() / factor : o.occupancy();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double denominator = n * sumXX - sumX * sumX;
        if (n < 2 || denominator == 0) {
            // Không đủ điểm phân biệt để nói về xu hướng — lùi về mức trung bình phẳng.
            return new double[] { n > 0 ? sumY / n : 0.0, 0.0 };
        }
        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;
        return new double[] { intercept, slope };
    }

    private double predict(double[] fit, LocalDate origin, LocalDate target, double[] dayFactors) {
        double x = ChronoUnit.DAYS.between(origin, target);
        double deseasonalized = fit[0] + fit[1] * x;
        double value = deseasonalized * dayFactors[target.getDayOfWeek().getValue()];
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Trung bình tỉ lệ lấp đầy trong MOVING_AVERAGE_DAYS ngày lịch cuối cùng.
     *
     * Nếu đuôi chuỗi thủng hết (mọi chuyến trong cửa sổ đều bị hủy) thì lùi về
     * trung bình toàn chuỗi — trả 0 sẽ là một khẳng định sai ("không ai đi").
     */
    private double movingAverage(List<Observation> observations, LocalDate endDate) {
        LocalDate from = endDate.minusDays(MOVING_AVERAGE_DAYS - 1L);
        List<Double> window = observations.stream()
                .filter(o -> !o.date().isBefore(from) && !o.date().isAfter(endDate))
                .map(Observation::occupancy)
                .toList();
        if (window.isEmpty()) {
            return observations.stream().mapToDouble(Observation::occupancy).average().orElse(0.0);
        }
        return window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // =========================================================================
    // TỰ CHẤM ĐIỂM (BACKTEST)
    // =========================================================================

    /**
     * Giấu BACKTEST_DAYS ngày cuối, huấn luyện trên phần còn lại, rồi so dự báo
     * với số thật của quãng đã giấu.
     *
     * Điểm mấu chốt về tính trung thực: hệ số mùa vụ dùng trong backtest được
     * tính LẠI chỉ từ phần huấn luyện. Nếu dùng lại hệ số tính trên toàn bộ dữ
     * liệu, thông tin của quãng kiểm tra đã rò rỉ vào mô hình và con số sai lệch
     * sẽ đẹp hơn thực tế — một lỗi phương pháp mà người chấm có quyền bắt bẻ.
     *
     * Baseline là trung bình trượt của phần huấn luyện, giữ phẳng suốt quãng
     * kiểm tra — đúng nghĩa "không mô hình hóa gì cả".
     */
    private ForecastAccuracyDto backtest(Collection<List<Observation>> allSeries, LocalDate historyEnd) {
        LocalDate trainEnd = historyEnd.minusDays(BACKTEST_DAYS);

        List<Observation> trainingPool = allSeries.stream()
                .flatMap(List::stream)
                .filter(o -> !o.date().isAfter(trainEnd))
                .toList();
        if (trainingPool.isEmpty()) {
            return new ForecastAccuracyDto(BACKTEST_DAYS, 0, 0.0, 0.0, false, false);
        }
        double[] trainingDayFactors = dayOfWeekFactors(trainingPool);

        double modelErrorSum = 0;
        double baselineErrorSum = 0;
        int tested = 0;

        for (List<Observation> series : allSeries) {
            List<Observation> train = series.stream().filter(o -> !o.date().isAfter(trainEnd)).toList();
            List<Observation> test = series.stream().filter(o -> o.date().isAfter(trainEnd)).toList();
            if (train.size() < MIN_TRAINING_OBSERVATIONS || test.isEmpty()) {
                continue;
            }
            LocalDate origin = train.stream().map(Observation::date).min(Comparator.naturalOrder()).orElseThrow();
            double[] fit = fitTrend(train, trainingDayFactors, origin);
            double baseline = movingAverage(train, trainEnd);

            for (Observation actual : test) {
                if (actual.occupancy() <= 0) {
                    continue; // MAPE không xác định khi mẫu số bằng 0
                }
                double predicted = predict(fit, origin, actual.date(), trainingDayFactors);
                modelErrorSum += Math.abs(actual.occupancy() - predicted) / actual.occupancy();
                baselineErrorSum += Math.abs(actual.occupancy() - baseline) / actual.occupancy();
                tested++;
            }
        }

        if (tested == 0) {
            return new ForecastAccuracyDto(BACKTEST_DAYS, 0, 0.0, 0.0, false, false);
        }
        double modelMape = modelErrorSum / tested * 100;
        double baselineMape = baselineErrorSum / tested * 100;
        return new ForecastAccuracyDto(BACKTEST_DAYS, tested, modelMape, baselineMape,
                modelMape < baselineMape, true);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private DemandForecastViewDto emptyView(LocalDate forecastFrom) {
        return new DemandForecastViewDto(
                null, null, 0L, 0, forecastFrom, FORECAST_HORIZON_DAYS,
                List.of(), 0, MIN_OBSERVATIONS, List.of(),
                new ForecastAccuracyDto(BACKTEST_DAYS, 0, 0.0, 0.0, false, false),
                0L);
    }
}
