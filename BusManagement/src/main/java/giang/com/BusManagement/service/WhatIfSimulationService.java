package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.WhatIfBottleneck;
import giang.com.BusManagement.dto.WhatIfOutcomeDto;
import giang.com.BusManagement.dto.WhatIfScenarioDto;
import giang.com.BusManagement.dto.WhatIfViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PHASE 8 — What-if Simulation. Thành phần Decision Support (Pillar-2) cuối cùng
 * đóng vòng: Dự báo (Phase 6) → Đề xuất (Phase 7) → "thay đổi gì thì phục vụ
 * được nhu cầu / với chi phí nào".
 *
 * ====================================================================
 * MÔ HÌNH ĐẾM/NĂNG LỰC, KHÔNG PHẢI MÔ HÌNH PHÂN CÔNG
 * ====================================================================
 * Toàn bộ phép "what-if" là SỐ HỌC trên các đại lượng tổng hợp đã có (đội xe,
 * tài xế, dự báo, tham số chi phí). Service này TUYỆT ĐỐI KHÔNG gọi
 * findBestAvailableBus/Driver và không sinh assignment — đây là refactor rủi ro
 * cao nhất toàn roadmap và bị cấm (Section 3 THESIS_ROADMAP.md). Tính khả thi cụ
 * thể (khớp loại xe, trùng lịch, bằng lái, bảo trì từng xe) chỉ được xác nhận ở
 * màn Đề Xuất Tăng Cường; màn này nói rõ điều đó bằng honesty banner.
 *
 * ====================================================================
 * THROUGHPUT SUY THEO CYCLE-TIME, KHÔNG PHẢI UTILIZATION
 * ====================================================================
 * tripsPerBusPerDay = cửa-sổ-vận-hành ÷ (thời-lượng-chuyến-TB + prep-buffer).
 * KHÔNG dùng "trung bình chuyến/xe/ngày trong lịch sử" — con số đó đo mức BẬN
 * (~0,74/xe/ngày trên dataset backfill, tức nhu cầu đã sinh cố định), không phải
 * NĂNG LỰC; lấy nó làm trần sẽ khiến baseline tưởng đội xe kịch trần, mâu thuẫn
 * với việc Phase 7 đã xác minh 17/17 đề xuất còn dư năng lực. Mọi đầu vào của
 * công thức lấy từ dữ liệu, trừ prep-buffer tái dùng TripService.BUS_PREP_BUFFER
 * (xem Developer Notes trong THESIS_ROADMAP.md).
 *
 * ====================================================================
 * NĂNG LỰC ĐỐI CHIẾU THEO NGÀY, KHÔNG GỘP CẢ CHÂN TRỜI
 * ====================================================================
 * Độ phủ = Σ theo từng ngày của min(số khung đông của ngày đó, servablePerDay),
 * rồi mới làm tròn xuống MỘT LẦN. Đây là ràng buộc đúng đắn, không phải chi tiết
 * cài đặt: công thức gộp min(hotSlots, servablePerDay × số ngày) cho năng lực
 * nhàn rỗi của ngày vắng "cho vay" sang ngày cao điểm — mà một chiếc xe rảnh
 * hôm thứ Ba không thể chạy hộ chuyến của thứ Bảy.
 *
 * Vì sao điều này quan trọng với CHÍNH bộ dữ liệu này: hệ số mùa vụ cuối tuần
 * của ForecastService là ~1,19, nên nhu cầu dự báo dồn cục — đo được 12/17 khung
 * đông của hiện trạng nằm ở T7+CN. Với công thức gộp, hạ đội xe xuống 1 tài xế
 * vẫn báo "phủ 100% — đủ năng lực, không bị bó"; tính theo ngày thì chỉ 58,8% và
 * nút thắt là DRIVER. Xem docs/todo/current_bugs_found.md mục #7.
 *
 * KHÔNG "đơn giản hoá" ngược lại thành một phép nhân với số ngày. Làm tròn xuống
 * từng ngày cũng SAI theo hướng ngược lại — servablePerDay là tỉ lệ thực của mô
 * hình cycle-time, 7,5 chuyến/ngày nghĩa là "có ngày 7, có ngày 8".
 *
 * ====================================================================
 * CHỈ ĐỌC, TÍNH LẠI MỖI LẦN XEM
 * ====================================================================
 * Không entity mới, không ghi DB, không đụng TripService. Ghi đè chi phí chỉ
 * sống trong bộ nhớ của phiên mô phỏng — KHÔNG gọi CostParameterService.save().
 */
@Service
@RequiredArgsConstructor
public class WhatIfSimulationService {

    private final ForecastService forecastService;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final CostParameterService costParameterService;
    private final TripRepository tripRepository;

    /**
     * Ngưỡng "chuyến đông cần tăng cường". Khai báo lại theo đúng tiền lệ
     * DashboardService/ForecastService/DriverRecommendationService — ở đây xét
     * một con số DỰ BÁO (đã nhân hệ số nhu cầu), không có entity Trip để gọi
     * needsReinforcement(). Nếu ngưỡng trong Trip đổi, đổi ở cả bốn nơi.
     */
    private static final double REINFORCEMENT_THRESHOLD = 0.90;

    /**
     * Hạn giờ lái/ngày để suy năng lực theo tài xế. Cùng giá trị với
     * validateStaffForTrip() và RecommendationService.MAX_DAILY_DRIVING_HOURS.
     */
    private static final double MAX_DAILY_DRIVING_HOURS = 8.0;

    /**
     * Buffer chuẩn bị xe giữa hai chuyến. Mirror TripService.BUS_PREP_BUFFER_HOURS
     * (private, không mở visibility chỉ cho một màn đọc — theo tiền lệ
     * DispatchController tự khai báo lại kèm comment trỏ về nguồn).
     */
    private static final double PREP_BUFFER_HOURS = 1.0;

    /** Thời lượng mặc định (phút) nếu tuyến thiếu estimatedDuration — cùng fallback createExtraTrip(). */
    private static final int DEFAULT_DURATION_MINUTES = 240;

    /** Kẹp hệ số nhu cầu về khoảng hợp lý để tránh input vô nghĩa. */
    private static final int MIN_DEMAND_FACTOR = 50;
    private static final int MAX_DEMAND_FACTOR = 300;

    /**
     * Một khung được dự báo đông, rút gọn còn những gì việc ước tính cần.
     *
     * {@code date} là BẮT BUỘC, không phải thông tin trang trí: độ phủ được đối
     * chiếu theo từng ngày (xem javadoc của lớp), nên mất ngày là mất luôn khả năng
     * biết một khung thuộc ngày cao điểm hay ngày vắng.
     */
    private record HotSlot(long routeId, int hour, LocalDate date, double predictedOccupancy) {
    }

    /** Kết quả suy throughput theo cycle-time — giữ lại để hiển thị cách suy trên màn. */
    private record Derivation(double operatingWindowHours, double avgTripDurationHours,
            double tripsPerBusPerDay) {
    }

    @Transactional(readOnly = true)
    public WhatIfViewDto simulate(WhatIfScenarioDto rawScenario) {
        WhatIfScenarioDto lever = normalize(rawScenario);

        // Gọi ĐÚNG MỘT LẦN — cùng dự báo mà màn Đề Xuất tiêu thụ.
        DemandForecastViewDto forecast = forecastService.buildForecast();
        CostParameters costDefaults = costParameterService.getOrDefault();

        WhatIfViewDto view = new WhatIfViewDto();
        view.setScenario(lever);
        view.setDefaultFuelCostPerKm(costDefaults.getFuelCostPerKm());
        view.setDefaultDriverWagePerHour(costDefaults.getDriverWagePerHour());
        view.setHistoryEnd(forecast.getHistoryEnd());
        view.setHistoryStaleDays(forecast.getHistoryStaleDays());
        view.setForecastFrom(forecast.getForecastFrom());
        view.setHorizonDays(forecast.getHorizonDays());

        if (forecast.getSeries() == null || forecast.getSeries().isEmpty()) {
            view.setDataAvailable(false);
            return view;
        }
        view.setDataAvailable(true);

        // --- Nguồn lực hiện trạng ---
        List<Bus> buses = busRepository.findAllWithBusType();
        int readyBuses = (int) buses.stream().filter(b -> b.getStatus() == BusStatus.READY).count();
        int activeDrivers = (int) driverRepository.countByIsActive(Boolean.TRUE);
        double avgFleetCapacity = buses.stream()
                .filter(b -> b.getBusType() != null)
                .mapToInt(b -> b.getBusType().getCapacity())
                .average().orElse(0.0);
        long baselineSeats = buses.stream()
                .filter(b -> b.getStatus() == BusStatus.READY && b.getBusType() != null)
                .mapToInt(b -> b.getBusType().getCapacity())
                .sum();

        Map<Long, Route> routesById = routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Integer, BigDecimal>> prices = loadLatestPrices();

        Derivation der = deriveThroughput(forecast, routesById);
        view.setOperatingWindowHours(der.operatingWindowHours());
        view.setAvgTripDurationHours(der.avgTripDurationHours());
        view.setTripsPerBusPerDay(der.tripsPerBusPerDay());
        view.setPrepBufferHours(PREP_BUFFER_HOURS);
        view.setAvgFleetCapacity(avgFleetCapacity);

        // --- Cột HIỆN TRẠNG: nguồn lực thật, nhu cầu 100%, chi phí mặc định ---
        view.setBaseline(computeOutcome(forecast, routesById, prices, der,
                readyBuses, activeDrivers, baselineSeats, avgFleetCapacity,
                100, costDefaults.getFuelCostPerKm(), costDefaults.getDriverWagePerHour()));

        // --- Cột KỊCH BẢN: áp các cần gạt ---
        int scenarioBuses = Math.max(0, readyBuses + lever.getDeltaBuses());
        int scenarioDrivers = Math.max(0, activeDrivers + lever.getDeltaDrivers());
        long scenarioSeats = Math.max(0L,
                baselineSeats + Math.round(lever.getDeltaBuses() * avgFleetCapacity));
        BigDecimal fuelRate = lever.getFuelCostPerKm() != null
                ? lever.getFuelCostPerKm() : costDefaults.getFuelCostPerKm();
        BigDecimal wageRate = lever.getDriverWagePerHour() != null
                ? lever.getDriverWagePerHour() : costDefaults.getDriverWagePerHour();

        view.setScenarioOutcome(computeOutcome(forecast, routesById, prices, der,
                scenarioBuses, scenarioDrivers, scenarioSeats, avgFleetCapacity,
                lever.getDemandFactorPercent(), fuelRate, wageRate));

        return view;
    }

    // =========================================================================
    // MỘT CỘT KẾT QUẢ
    // =========================================================================

    private WhatIfOutcomeDto computeOutcome(DemandForecastViewDto forecast, Map<Long, Route> routesById,
            Map<Long, Map<Integer, BigDecimal>> prices, Derivation der,
            int buses, int drivers, long totalSeats, double avgFleetCapacity,
            int demandFactorPercent, BigDecimal fuelRate, BigDecimal wageRate) {

        double factor = demandFactorPercent / 100.0;

        // Năng lực: trần theo xe và trần theo tài xế, lấy cái nhỏ hơn.
        double busThroughput = buses * der.tripsPerBusPerDay();
        double driverThroughput = der.avgTripDurationHours() > 0
                ? drivers * MAX_DAILY_DRIVING_HOURS / der.avgTripDurationHours()
                : 0.0;
        double servablePerDay = Math.min(busThroughput, driverThroughput);

        // Nhu cầu: đếm lại các khung vượt ngưỡng ở hệ số nhu cầu này.
        List<HotSlot> hot = new ArrayList<>();
        for (ForecastSeriesDto series : forecast.getSeries()) {
            for (ForecastPointDto point : series.getPoints()) {
                double scaled = Math.min(1.0, point.getPredictedOccupancy() * factor);
                if (scaled > REINFORCEMENT_THRESHOLD) {
                    hot.add(new HotSlot(series.getRouteId(), series.getDepartureHour(),
                            point.getDate(), scaled));
                }
            }
        }
        int hotSlots = hot.size();

        int coverable = coverableSlots(hot.stream().map(HotSlot::date).toList(), servablePerDay);
        int gap = hotSlots - coverable;
        Double coveragePct = hotSlots > 0 ? (100.0 * coverable / hotSlots) : null;
        WhatIfBottleneck bottleneck = bottleneckOf(busThroughput, driverThroughput, gap);

        // Tài chính của kế hoạch PHỦ ĐƯỢC = đúng tập khung sẽ chạy. Tập này cũng
        // phải chọn theo ngày, nếu không con số tiền sẽ nói về một kế hoạch khác với
        // con số độ phủ ngay bên trên.
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        boolean anyCost = false;
        boolean anyRevenue = false;
        for (HotSlot slot : selectCoveredSlots(hot, servablePerDay, coverable)) {
            Route route = routesById.get(slot.routeId());
            if (route == null) {
                continue;
            }
            int durationMinutes = route.getEstimatedDuration() != null && route.getEstimatedDuration() > 0
                    ? route.getEstimatedDuration() : DEFAULT_DURATION_MINUTES;
            double durationHours = durationMinutes / 60.0;

            // Số tài xế cần: mọi tuyến vào forecast hiện đều ≤ 8h ⇒ = 1; vẫn tính
            // theo ceil(dur/8) cho KHỚP CHÍNH XÁC công thức chi phí Phase 7
            // (RecommendationService.estimateCost) và bền với tuyến > 8h sau này.
            int requiredDrivers = Math.max(1, (int) Math.ceil(durationHours / MAX_DAILY_DRIVING_HOURS));

            // Chi phí: nhiên liệu theo quãng đường + lương theo thời lượng × số tài
            // xế. Bỏ qua term thiếu dữ liệu, không đoán.
            if (route.getDistanceKm() != null && fuelRate != null) {
                cost = cost.add(fuelRate.multiply(BigDecimal.valueOf(route.getDistanceKm())));
                anyCost = true;
            }
            if (wageRate != null) {
                cost = cost.add(wageRate.multiply(BigDecimal.valueOf(durationHours))
                        .multiply(BigDecimal.valueOf(requiredDrivers)));
                anyCost = true;
            }

            // Doanh thu: sức chứa đại diện (loại xe phù hợp của tuyến, else TB đội xe)
            // × giá vé lịch sử của khung. Không có giá → bỏ khung khỏi doanh thu.
            int capacity = route.getSuitableBusType() != null
                    ? route.getSuitableBusType().getCapacity()
                    : (int) Math.round(avgFleetCapacity);
            BigDecimal price = priceOf(prices, slot.routeId(), slot.hour());
            if (price != null && capacity > 0) {
                int passengers = (int) Math.round(slot.predictedOccupancy() * capacity);
                revenue = revenue.add(price.multiply(BigDecimal.valueOf(passengers)));
                anyRevenue = true;
            }
        }
        BigDecimal estimatedCost = anyCost ? cost.setScale(0, RoundingMode.HALF_UP) : null;
        BigDecimal estimatedRevenue = anyRevenue ? revenue.setScale(0, RoundingMode.HALF_UP) : null;
        BigDecimal estimatedProfit = (estimatedRevenue != null && estimatedCost != null)
                ? estimatedRevenue.subtract(estimatedCost) : null;

        return new WhatIfOutcomeDto(
                buses, drivers, totalSeats,
                busThroughput, driverThroughput, servablePerDay, bottleneck,
                hotSlots, coverable, gap, coveragePct,
                estimatedCost, estimatedRevenue, estimatedProfit);
    }

    /**
     * Số khung phủ được: cộng dồn THEO TỪNG NGÀY của
     * {@code min(số khung đông của ngày đó, servablePerDay)}, rồi làm tròn xuống
     * MỘT LẦN ở cuối.
     *
     * Năng lực nhàn rỗi của ngày vắng KHÔNG được cho vay sang ngày cao điểm — một
     * chiếc xe rảnh hôm thứ Ba không chạy hộ được chuyến của thứ Bảy. Làm tròn một
     * lần ở cuối (chứ không từng ngày) vì {@code servablePerDay} là tỉ lệ thực của
     * mô hình cycle-time: 7,5 chuyến/ngày nghĩa là "có ngày 7, có ngày 8". Xem khối
     * "NĂNG LỰC ĐỐI CHIẾU THEO NGÀY" ở javadoc của lớp.
     *
     * Tách riêng và để package-private để test chốt được trực tiếp trên số học này —
     * cùng lý do {@code TripService.isBusBusy}/{@code getDrivingHoursForDate} là
     * package-private. Nhận danh sách NGÀY (không phải HotSlot) để hàm thuần và không
     * phụ thuộc kiểu nội bộ.
     */
    static int coverableSlots(List<LocalDate> hotSlotDates, double servablePerDay) {
        double exact = hotSlotDates.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream()
                .mapToDouble(hotThatDay -> Math.min(hotThatDay.doubleValue(), servablePerDay))
                .sum();
        return (int) Math.floor(exact);
    }

    /**
     * Tập khung ĐƯỢC PHỦ, chọn theo từng ngày để khớp với con số {@code coverable}.
     *
     * Trong mỗi ngày lấy các khung đông nhất của chính ngày đó trước — cùng nguyên
     * tắc "đông nhất lên đầu" mà màn Đề Xuất và Dự Báo đang dùng. Trần mỗi ngày làm
     * tròn LÊN vì {@code servablePerDay} là tỉ lệ thực và {@code coverable} đã làm
     * tròn xuống một lần cho cả chân trời; tổng vẫn bị chặn cứng bởi
     * {@code coverable}, nên không ngày nào và cả kế hoạch đều không thể vượt năng
     * lực đã tính. Ngày được duyệt theo thứ tự thời gian ({@link TreeMap}) để kết
     * quả tiền luôn tái lập được.
     */
    private List<HotSlot> selectCoveredSlots(List<HotSlot> hot, double servablePerDay, int coverable) {
        int dayCap = (int) Math.ceil(servablePerDay);
        Map<LocalDate, List<HotSlot>> byDate = new TreeMap<>(
                hot.stream().collect(Collectors.groupingBy(HotSlot::date)));

        List<HotSlot> covered = new ArrayList<>();
        for (List<HotSlot> slotsOfDay : byDate.values()) {
            int budget = Math.min(dayCap, coverable - covered.size());
            if (budget <= 0) {
                break;
            }
            slotsOfDay.stream()
                    .sorted(Comparator.comparingDouble(HotSlot::predictedOccupancy).reversed())
                    .limit(budget)
                    .forEach(covered::add);
        }
        return covered;
    }

    // =========================================================================
    // SUY THROUGHPUT (cycle-time, data-driven)
    // =========================================================================

    private Derivation deriveThroughput(DemandForecastViewDto forecast, Map<Long, Route> routesById) {
        // Thời lượng chuyến TB: lấy estimatedDuration của các tuyến có trong forecast.
        List<Integer> durations = forecast.getSeries().stream()
                .map(s -> routesById.get(s.getRouteId()))
                .filter(Objects::nonNull)
                .map(Route::getEstimatedDuration)
                .filter(d -> d != null && d > 0)
                .toList();
        double avgTripDurationHours = durations.isEmpty()
                ? 0.0
                : durations.stream().mapToInt(Integer::intValue).average().orElse(0) / 60.0;

        // Cửa sổ vận hành: từ khung khởi hành sớm nhất tới lúc chuyến muộn nhất kết
        // thúc (giờ khởi hành muộn nhất + thời lượng TB) − khung sớm nhất.
        IntSummaryStatistics hourStats = forecast.getSeries().stream()
                .mapToInt(ForecastSeriesDto::getDepartureHour)
                .summaryStatistics();
        double operatingWindowHours = hourStats.getCount() > 0
                ? (hourStats.getMax() + avgTripDurationHours) - hourStats.getMin()
                : 0.0;

        double cycle = avgTripDurationHours + PREP_BUFFER_HOURS;
        double tripsPerBusPerDay = cycle > 0 ? operatingWindowHours / cycle : 0.0;

        return new Derivation(operatingWindowHours, avgTripDurationHours, tripsPerBusPerDay);
    }

    private WhatIfBottleneck bottleneckOf(double busThroughput, double driverThroughput, int gap) {
        if (gap <= 0) {
            return WhatIfBottleneck.NONE;
        }
        if (Math.abs(busThroughput - driverThroughput) < 1e-9) {
            return WhatIfBottleneck.BALANCED;
        }
        return busThroughput < driverThroughput ? WhatIfBottleneck.BUS : WhatIfBottleneck.DRIVER;
    }

    // =========================================================================
    // GIÁ VÉ LỊCH SỬ (giống RecommendationService — projection, không nạp Trip)
    // =========================================================================

    private Map<Long, Map<Integer, BigDecimal>> loadLatestPrices() {
        Map<Long, Map<Integer, BigDecimal>> result = new HashMap<>();
        for (Object[] row : tripRepository.findPriceHistoryByStatus(TripStatus.COMPLETED)) {
            if (row[0] == null || row[1] == null || row[2] == null) {
                continue;
            }
            Long routeId = ((Number) row[0]).longValue();
            int hour = ((java.time.LocalDateTime) row[1]).getHour();
            BigDecimal price = (BigDecimal) row[2];
            result.computeIfAbsent(routeId, k -> new HashMap<>()).putIfAbsent(hour, price);
        }
        return result;
    }

    private BigDecimal priceOf(Map<Long, Map<Integer, BigDecimal>> prices, Long routeId, int hour) {
        return prices.getOrDefault(routeId, Map.of()).get(hour);
    }

    // =========================================================================
    // CHUẨN HÓA ĐẦU VÀO
    // =========================================================================

    private WhatIfScenarioDto normalize(WhatIfScenarioDto in) {
        WhatIfScenarioDto out = new WhatIfScenarioDto();
        out.setDeltaBuses(in != null && in.getDeltaBuses() != null ? in.getDeltaBuses() : 0);
        out.setDeltaDrivers(in != null && in.getDeltaDrivers() != null ? in.getDeltaDrivers() : 0);
        int factor = in != null && in.getDemandFactorPercent() != null ? in.getDemandFactorPercent() : 100;
        out.setDemandFactorPercent(Math.max(MIN_DEMAND_FACTOR, Math.min(MAX_DEMAND_FACTOR, factor)));
        // null = dùng mặc định; giá trị âm/0 bị coi là "không ghi đè" để tránh chi phí âm.
        out.setFuelCostPerKm(positiveOrNull(in != null ? in.getFuelCostPerKm() : null));
        out.setDriverWagePerHour(positiveOrNull(in != null ? in.getDriverWagePerHour() : null));
        return out;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return (value != null && value.signum() > 0) ? value : null;
    }
}
