package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Station;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * NHU CẦU TÍNH BẰNG SỐ KHÁCH, ĐẾM THEO CHUYẾN THÊM (GĐ6, QĐ-17, G6-5)
 * ====================================================================
 * Hệ số nhu cầu nhân thẳng vào nhu cầu dự báo (số khách, không kẹp), rồi hỏi
 * ForecastService.tripsNeeded() khung cần mấy chuyến — chính hàm của màn Dự báo và
 * Đề xuất, nên ở 100% hai màn luôn cùng số chuyến thêm và lớp này không khai báo lại
 * ngưỡng 0,90. Đơn vị đếm là CHUYẾN THÊM: khung cần 3 chuyến góp 2. Tiền của mỗi
 * chuyến thêm tính theo khách tràn rơi vào ghế của nó
 * (ForecastService.extraTripPassengers), với xe chuẩn của tuyến.
 *
 * Trước GĐ6 màn này nhân hệ số vào tỉ lệ lấp đầy ĐÃ KẸP ở 100%, nên một khung cần
 * 1,5 xe thành "100% × 0,7 = 70%" và 70% nhu cầu báo 0 khung đông trong khi tính
 * theo số khách vẫn còn 2 chuyến thêm; doanh thu cũng coi mọi chuyến thêm là đầy xe.
 *
 * ====================================================================
 * TIỀN CẢ VÒNG, CHỈ PHẦN KHUNG CÒN THIẾU (route pairs CT7, W1 và cách 2a)
 * ====================================================================
 * Cùng định nghĩa với màn Đề xuất, qua cùng một lớp tính tiền ({@link LoopEconomics}):
 * <ul>
 *   <li>số chuyến thêm của khung = số chuyến cần − số chuyến đã có trong khung (đọc DB, không chọn
 *       xe); khung chưa có chuyến nào thì coi như có một xe chuẩn;</li>
 *   <li>tiền của mỗi chuyến thêm là tiền CẢ VÒNG: cộng lượt đi kèm THEO KẾ HOẠCH — xe dự phòng giả định
 *       đứng ở bến nhà (QĐ-23), nên chuyến thêm rời bến nhà thì "về ngay" bằng khung chiều ngược sớm
 *       nhất, chuyến thêm tới bến nhà thì "đưa xe tới trước" bằng khung muộn nhất còn kịp.</li>
 * </ul>
 * Màn Đề xuất chọn xe thật nên có thể đi khác kế hoạch (xe rảnh đang ở bến xa, tài xế chưa kịp nghỉ);
 * ở đây là mô hình đếm nên chỉ có kế hoạch. NĂNG LỰC không đổi (W1): mỗi chuyến thêm vẫn tính một lượt
 * xe trong độ phủ, dù vòng của nó chạy hai lượt — ghi ở phần giới hạn của màn.
 *
 * ====================================================================
 * NĂNG LỰC ĐỐI CHIẾU THEO NGÀY, KHÔNG GỘP CẢ CHÂN TRỜI
 * ====================================================================
 * Độ phủ = Σ theo từng ngày của min(số chuyến thêm cần trong ngày đó, servablePerDay),
 * rồi mới làm tròn xuống MỘT LẦN. Đây là ràng buộc đúng đắn, không phải chi tiết
 * cài đặt: công thức gộp min(cần, servablePerDay × số ngày) cho năng lực
 * nhàn rỗi của ngày vắng "cho vay" sang ngày cao điểm — mà một chiếc xe rảnh
 * hôm thứ Ba không thể chạy hộ chuyến của thứ Bảy.
 *
 * Vì sao điều này quan trọng với CHÍNH bộ dữ liệu này (đo 2026-07-30, trên mô hình
 * dự báo cũ, khi đơn vị còn là khung): hệ số mùa vụ cuối tuần là ~1,19, nên nhu cầu
 * dự báo dồn cục — đo được 12/17 khung đông của hiện trạng nằm ở T7+CN. Với công
 * thức gộp, hạ đội xe xuống 1 tài xế vẫn báo "phủ 100% — đủ năng lực, không bị bó";
 * tính theo ngày thì chỉ 58,8% và nút thắt là DRIVER. Xem
 * docs/todo/current_bugs_found.md mục #7. Nhu cầu dịp lễ còn dồn cục hơn thế.
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
     * Hạn giờ lái/ngày để suy năng lực theo tài xế. Một nguồn với phép tính số tài xế
     * của một lượt ({@link LoopEconomics#MAX_DAILY_DRIVING_HOURS}, cùng giá trị với validateStaffForTrip()).
     */
    private static final double MAX_DAILY_DRIVING_HOURS = LoopEconomics.MAX_DAILY_DRIVING_HOURS;

    /**
     * Buffer chuẩn bị xe giữa hai chuyến. Một nguồn với phép tính lượt đi kèm
     * ({@link LoopEconomics#PREP_BUFFER_HOURS}, mirror TripService.BUS_PREP_BUFFER_HOURS).
     */
    private static final double PREP_BUFFER_HOURS = LoopEconomics.PREP_BUFFER_HOURS;

    /** Kẹp hệ số nhu cầu về khoảng hợp lý để tránh input vô nghĩa. */
    private static final int MIN_DEMAND_FACTOR = 50;
    private static final int MAX_DEMAND_FACTOR = 300;

    /**
     * Một chuyến thêm mà nhu cầu của kịch bản đòi, rút gọn còn những gì việc ước tính
     * cần: doanh thu và chi phí đã tính theo tham số của cột đang xét (null khi thiếu giá
     * vé lịch sử hoặc thiếu dữ liệu chi phí).
     *
     * {@code date} là BẮT BUỘC, không phải thông tin trang trí: độ phủ được đối
     * chiếu theo từng ngày (xem javadoc của lớp), nên mất ngày là mất luôn khả năng
     * biết một chuyến thuộc ngày cao điểm hay ngày vắng.
     */
    private record ExtraTrip(LocalDate date, BigDecimal revenue, BigDecimal cost, BigDecimal heldLoopProfit) {
        BigDecimal profit() {
            return revenue != null && cost != null ? revenue.subtract(cost) : null;
        }

        /**
         * Lãi dùng để xếp và để hỏi "có lãi không": của riêng chuyến thêm, hay — chuyến thêm thuộc một vòng giữ
         * xe (CT9) — lãi của CẢ vòng giữ, để hai chuyến thêm của vòng được xếp và tính như một khối.
         */
        BigDecimal rankProfit() {
            return heldLoopProfit != null ? heldLoopProfit : profit();
        }
    }

    /** Kết quả suy throughput theo cycle-time — giữ lại để hiển thị cách suy trên màn. */
    private record Derivation(double operatingWindowHours, double avgTripDurationHours,
            double tripsPerBusPerDay) {
    }

    /**
     * Những gì phép tính tiền cả vòng cần mà không phụ thuộc cột (CT7): tuyến chiều ngược duy nhất của
     * từng tuyến, các bến nhà của đội xe, chuyến đã có theo khung, nhu cầu dự báo theo khung.
     */
    private record Pairing(Map<Long, Route> reverseOf, Set<Long> homeStations,
            Map<String, LoopEconomics.SlotTrips> tripsBySlot, Map<String, LoopEconomics.SlotDemand> demandBySlot) {
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

        List<Route> routes = routeRepository.findAllWithStations();
        Map<Long, Route> routesById = routes.stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Integer, BigDecimal>> prices =
                LoopEconomics.latestPrices(tripRepository.findPriceHistoryByStatus(TripStatus.COMPLETED));

        // Cặp tuyến, bến nhà, chuyến đã có theo khung và nhu cầu theo khung (CT7): cùng nguồn và cùng
        // khoảng thời gian màn Đề xuất đọc, để hai màn đếm cùng số chuyến thêm và cùng tiền.
        Pairing pairing = new Pairing(reverseRoutes(routes),
                buses.stream().map(Bus::getHomeStation).filter(Objects::nonNull).map(Station::getId)
                        .collect(Collectors.toSet()),
                LoopEconomics.slotTrips(tripRepository.findSlotSeatsBetween(RecommendationService.SLOT_STATUSES,
                        LocalDate.now().atStartOfDay(), forecast.getForecastFrom()
                                .plusDays(forecast.getHorizonDays() + 1L + LoopEconomics.COMPANION_SEARCH_DAYS)
                                .atStartOfDay())),
                LoopEconomics.demandBySlot(forecast));

        Derivation der = deriveThroughput(forecast, routesById);
        view.setOperatingWindowHours(der.operatingWindowHours());
        view.setAvgTripDurationHours(der.avgTripDurationHours());
        view.setTripsPerBusPerDay(der.tripsPerBusPerDay());
        view.setPrepBufferHours(PREP_BUFFER_HOURS);
        view.setAvgFleetCapacity(avgFleetCapacity);

        // --- Cột HIỆN TRẠNG: nguồn lực thật, nhu cầu 100%, chi phí mặc định ---
        view.setBaseline(computeOutcome(forecast, routesById, prices, pairing, der,
                readyBuses, activeDrivers, baselineSeats,
                100, costDefaults.getFuelCostPerKm(), costDefaults.getDriverWagePerHour(), costDefaults));

        // --- Cột KỊCH BẢN: áp các cần gạt ---
        int scenarioBuses = Math.max(0, readyBuses + lever.getDeltaBuses());
        int scenarioDrivers = Math.max(0, activeDrivers + lever.getDeltaDrivers());
        long scenarioSeats = Math.max(0L,
                baselineSeats + Math.round(lever.getDeltaBuses() * avgFleetCapacity));
        BigDecimal fuelRate = lever.getFuelCostPerKm() != null
                ? lever.getFuelCostPerKm() : costDefaults.getFuelCostPerKm();
        BigDecimal wageRate = lever.getDriverWagePerHour() != null
                ? lever.getDriverWagePerHour() : costDefaults.getDriverWagePerHour();

        view.setScenarioOutcome(computeOutcome(forecast, routesById, prices, pairing, der,
                scenarioBuses, scenarioDrivers, scenarioSeats,
                lever.getDemandFactorPercent(), fuelRate, wageRate, costDefaults));

        return view;
    }

    // =========================================================================
    // MỘT CỘT KẾT QUẢ
    // =========================================================================

    private WhatIfOutcomeDto computeOutcome(DemandForecastViewDto forecast, Map<Long, Route> routesById,
            Map<Long, Map<Integer, BigDecimal>> prices, Pairing pairing, Derivation der,
            int buses, int drivers, long totalSeats,
            int demandFactorPercent, BigDecimal fuelRate, BigDecimal wageRate, CostParameters waiting) {

        double factor = demandFactorPercent / 100.0;
        // Độ phân tán nhu cầu của lần khớp — cùng con số màn Đề xuất dùng để tính khách tràn.
        double nbSize = forecast.getModel() != null ? forecast.getModel().getNegativeBinomialSize() : 0.0;
        // Tiền theo tham số và hệ số nhu cầu của CỘT này; cùng lớp tính tiền với màn Đề xuất (CT7). Tiền chờ
        // của vòng giữ xe (CT9) theo tham số đang cấu hình — màn này không có cần gạt cho nó.
        LoopEconomics economics = new LoopEconomics(prices, pairing.demandBySlot(), pairing.tripsBySlot(), nbSize,
                factor, fuelRate, wageRate, waiting.getWaitingWagePerDay(), waiting.getBusParkingPerDay());
        LocalDateTime now = LocalDateTime.now();

        // Năng lực: trần theo xe và trần theo tài xế, lấy cái nhỏ hơn.
        double busThroughput = buses * der.tripsPerBusPerDay();
        double driverThroughput = der.avgTripDurationHours() > 0
                ? drivers * MAX_DAILY_DRIVING_HOURS / der.avgTripDurationHours()
                : 0.0;
        double servablePerDay = Math.min(busThroughput, driverThroughput);

        // Nhu cầu: nhân hệ số vào số khách rồi hỏi dự báo khung cần mấy chuyến, trừ các chuyến khung đã
        // có (CT7, cách 2a) — mỗi chuyến thêm còn thiếu một phần tử, kèm tiền CẢ VÒNG của nó (G6-5, W1).
        List<ExtraTrip> extras = new ArrayList<>();
        List<LoopEconomics.PlannedExtra> planned = new ArrayList<>(); // cho phép ghép vòng giữ xe (CT9)
        int hotSlots = 0;
        for (ForecastSeriesDto series : forecast.getSeries()) {
            Route route = routesById.get(series.getRouteId());
            int seats = series.getStandardSeats();
            int hour = series.getDepartureHour();
            BigDecimal price = economics.price(series.getRouteId(), hour);
            BigDecimal tripCost = route != null ? economics.tripCost(route) : null;
            Route reverse = route != null ? pairing.reverseOf().get(route.getId()) : null;
            for (ForecastPointDto point : series.getPoints()) {
                double demand = point.getPredictedDemand() * factor;
                int tripsNeeded = ForecastService.tripsNeeded(demand, seats);
                if (tripsNeeded > 1) {
                    hotSlots++;
                }
                LoopEconomics.SlotTrips inSlot = economics.inSlot(series.getRouteId(), point.getDate(), hour, seats);
                int count = LoopEconomics.extrasStillNeeded(tripsNeeded, inSlot);
                // Lượt đi kèm theo kế hoạch (xe dự phòng ở bến nhà) — cùng một lượt cho mọi chuyến thêm của khung.
                LoopEconomics.Leg leg = null;
                if (route != null && count > 0) {
                    LocalDateTime departure = point.getDate().atTime(hour, 0);
                    leg = economics.planned(route, reverse, departure,
                            departure.plusMinutes(LoopEconomics.durationMinutes(route)), pairing.homeStations(), now,
                            seats);
                }
                for (int k = 1; k <= count; k++) {
                    // Doanh thu: khách tràn rơi vào ghế của chuyến thêm thứ k (xe chuẩn) sau các chuyến đã
                    // có, × giá vé lịch sử của khung, cộng tiền vé của lượt đi kèm. Không có giá, hoặc tuyến
                    // đã bị xóa → không ước được.
                    BigDecimal revenue = null;
                    BigDecimal cost = tripCost;
                    if (route != null) {
                        double passengers = ForecastService.extraTripPassengers(demand,
                                inSlot.seats() + (k - 1) * seats, seats, nbSize);
                        BigDecimal tripRevenue = LoopEconomics.revenue(price, passengers);
                        revenue = LoopEconomics.plus(tripRevenue, leg.revenue());
                        cost = LoopEconomics.plus(tripCost, leg.cost());
                        LocalDateTime departure = point.getDate().atTime(hour, 0);
                        planned.add(new LoopEconomics.PlannedExtra(extras.size(), route, reverse, departure,
                                departure.plusMinutes(LoopEconomics.durationMinutes(route)), leg.kind(), tripRevenue,
                                tripCost, revenue != null && cost != null ? revenue.subtract(cost) : null));
                    }
                    extras.add(new ExtraTrip(point.getDate(), revenue, cost, null));
                }
            }
        }
        // Vòng giữ xe (CT9): cùng phép ghép với màn Đề xuất. Hai chuyến thêm của một vòng giữ vẫn là hai phần
        // tử (độ phủ đếm theo chuyến thêm, W1), nhưng tiền là của cả vòng: chuyến đi mang tiền vé và chi phí của
        // nó cộng tiền chờ, chuyến về mang tiền vé và chi phí của nó, cả hai xếp theo lãi của cả vòng.
        for (LoopEconomics.Hold hold : economics.pairHolds(planned, Set.of())) {
            LoopEconomics.PlannedExtra out = planned.stream().filter(p -> p.id() == hold.outId()).findFirst().orElseThrow();
            LoopEconomics.PlannedExtra in = planned.stream().filter(p -> p.id() == hold.inId()).findFirst().orElseThrow();
            extras.set(out.id(), new ExtraTrip(out.departure().toLocalDate(), out.tripRevenue(),
                    out.tripCost().add(hold.waitCost()), hold.profit()));
            extras.set(in.id(), new ExtraTrip(in.departure().toLocalDate(), in.tripRevenue(), in.tripCost(),
                    hold.profit()));
        }
        int needed = extras.size();

        int coverable = coverableSlots(extras.stream().map(ExtraTrip::date).toList(), servablePerDay);
        int gap = needed - coverable;
        Double coveragePct = needed > 0 ? (100.0 * coverable / needed) : null;
        WhatIfBottleneck bottleneck = bottleneckOf(busThroughput, driverThroughput, gap);

        // Tài chính của kế hoạch PHỦ ĐƯỢC = đúng tập chuyến thêm sẽ chạy. Tập này cũng
        // phải chọn theo ngày, nếu không con số tiền sẽ nói về một kế hoạch khác với
        // con số độ phủ ngay bên trên. Kèm phần "chỉ chạy chuyến có lãi" của chính kế hoạch đó.
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        boolean anyCost = false;
        boolean anyRevenue = false;
        int profitableTrips = 0;
        BigDecimal profitableProfit = BigDecimal.ZERO;
        for (ExtraTrip trip : selectCoveredTrips(extras, servablePerDay, coverable)) {
            if (trip.cost() != null) {
                cost = cost.add(trip.cost());
                anyCost = true;
            }
            if (trip.revenue() != null) {
                revenue = revenue.add(trip.revenue());
                anyRevenue = true;
            }
            BigDecimal profit = trip.profit();
            if (profit != null && trip.rankProfit() != null && trip.rankProfit().signum() > 0) {
                profitableTrips++;
                profitableProfit = profitableProfit.add(profit);
            }
        }
        BigDecimal estimatedCost = anyCost ? cost : null;
        BigDecimal estimatedRevenue = anyRevenue ? revenue : null;
        BigDecimal estimatedProfit = (estimatedRevenue != null && estimatedCost != null)
                ? estimatedRevenue.subtract(estimatedCost) : null;

        return new WhatIfOutcomeDto(
                buses, drivers, totalSeats,
                busThroughput, driverThroughput, servablePerDay, bottleneck,
                hotSlots, needed, coverable, gap, coveragePct,
                estimatedCost, estimatedRevenue, estimatedProfit,
                profitableTrips, profitableTrips > 0 ? profitableProfit : null);
    }

    /**
     * Số chuyến thêm phủ được: cộng dồn THEO TỪNG NGÀY của
     * {@code min(số chuyến thêm cần trong ngày đó, servablePerDay)}, rồi làm tròn
     * xuống MỘT LẦN ở cuối. Mỗi phần tử của danh sách là một chuyến thêm (từ GĐ6; trước
     * đó là một khung) — khung cần 3 chuyến góp 2 phần tử cùng ngày. Tên hàm giữ nguyên
     * từ Phase 8.
     *
     * Năng lực nhàn rỗi của ngày vắng KHÔNG được cho vay sang ngày cao điểm — một
     * chiếc xe rảnh hôm thứ Ba không chạy hộ được chuyến của thứ Bảy. Làm tròn một
     * lần ở cuối (chứ không từng ngày) vì {@code servablePerDay} là tỉ lệ thực của
     * mô hình cycle-time: 7,5 chuyến/ngày nghĩa là "có ngày 7, có ngày 8". Xem khối
     * "NĂNG LỰC ĐỐI CHIẾU THEO NGÀY" ở javadoc của lớp.
     *
     * Tách riêng và để package-private để test chốt được trực tiếp trên số học này —
     * cùng lý do {@code TripService.isBusBusy}/{@code getDrivingHoursForDate} là
     * package-private. Nhận danh sách NGÀY (không phải ExtraTrip) để hàm thuần và
     * không phụ thuộc kiểu nội bộ.
     */
    static int coverableSlots(List<LocalDate> extraTripDates, double servablePerDay) {
        double exact = extraTripDates.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream()
                .mapToDouble(neededThatDay -> Math.min(neededThatDay.doubleValue(), servablePerDay))
                .sum();
        return (int) Math.floor(exact);
    }

    /**
     * Tập chuyến thêm ĐƯỢC PHỦ, chọn theo từng ngày để khớp với con số {@code coverable}.
     *
     * Trong mỗi ngày lấy các chuyến có lãi kỳ vọng cao nhất của chính ngày đó trước —
     * cùng thứ tự màn Đề Xuất dùng để giữ xe (GĐ6). Trần mỗi ngày làm tròn LÊN vì
     * {@code servablePerDay} là tỉ lệ thực và {@code coverable} đã làm tròn xuống một
     * lần cho cả chân trời; tổng vẫn bị chặn cứng bởi {@code coverable}, nên không ngày
     * nào và cả kế hoạch đều không thể vượt năng lực đã tính. Ngày được duyệt theo thứ tự
     * thời gian ({@link TreeMap}) để kết quả tiền luôn tái lập được.
     */
    private List<ExtraTrip> selectCoveredTrips(List<ExtraTrip> extras, double servablePerDay, int coverable) {
        int dayCap = (int) Math.ceil(servablePerDay);
        Map<LocalDate, List<ExtraTrip>> byDate = new TreeMap<>(
                extras.stream().collect(Collectors.groupingBy(ExtraTrip::date)));

        List<ExtraTrip> covered = new ArrayList<>();
        for (List<ExtraTrip> tripsOfDay : byDate.values()) {
            int budget = Math.min(dayCap, coverable - covered.size());
            if (budget <= 0) {
                break;
            }
            tripsOfDay.stream()
                    .sorted(Comparator.comparing(ExtraTrip::rankProfit, Comparator.nullsLast(Comparator.reverseOrder())))
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
    // CẶP TUYẾN (CT7)
    // =========================================================================

    /** Tuyến chiều ngược của từng tuyến, chỉ khi có ĐÚNG MỘT — cùng quy ước với màn Đề xuất (CM-6). */
    private static Map<Long, Route> reverseRoutes(List<Route> routes) {
        Map<Long, Route> result = new HashMap<>();
        for (Route route : routes) {
            List<Route> reverse = RouteService.reverseRoutesOf(route, routes);
            if (reverse.size() == 1) {
                result.put(route.getId(), reverse.get(0));
            }
        }
        return result;
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
