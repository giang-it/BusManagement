package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PHASE 7 — Recommendation Engine (bước 2: doanh thu; bước 3: + chi phí & lợi nhuận).
 * Chuyển sang đơn vị nhu cầu ở GĐ6 của đợt thiết kế lại dự báo (QĐ-17 trong
 * docs/development/forecast_decisions.md).
 *
 * Trả lời câu hỏi: <b>"khung giờ nào sắp tới được dự báo đông, cần thêm mấy chuyến,
 * mỗi chuyến thêm chạy bằng xe + tài xế nào, chở thêm được bao nhiêu khách, lãi hay
 * lỗ bao nhiêu?"</b>
 *
 * ====================================================================
 * LÀ NGƯỜI ĐIỀU PHỐI, KHÔNG PHẢI BỘ DỰ BÁO HAY BỘ LUẬT
 * ====================================================================
 * Service này KHÔNG tự dự báo và KHÔNG tự định nghĩa ràng buộc nào. Nó ghép nối
 * ba thứ đã có:
 * <ul>
 *   <li><b>Dự báo (Phase 6):</b> gọi {@code ForecastService.buildForecast()}
 *       ĐÚNG MỘT LẦN mỗi request, rồi lặp trên kết quả. Nó đọc cờ
 *       {@code needsReinforcement} và {@code tripsNeeded} của từng ngày — <b>không
 *       hề biết ngưỡng 0.90</b> — và hỏi {@code ForecastService.extraTripPassengers}
 *       một chuyến thêm chở được bao nhiêu khách. Quyết định "đông" và phép tính
 *       khách tràn đều thuộc về dự báo; engine chỉ tiêu thụ chúng.</li>
 *   <li><b>Chọn tài nguyên:</b> {@code TripService.selectBestAvailableBus/Driver}
 *       — chính các bộ lọc rule+availability đã kiểm chứng, không nhân bản.</li>
 *   <li><b>Cổng validation (Phase 3):</b> {@code validateBusForTripDryRun} /
 *       {@code validateStaffForTripDryRun} xác nhận lần cuối, đúng khuôn
 *       confirmAutoAssignedTrip (chọn xong rồi validate lại).</li>
 * </ul>
 *
 * ====================================================================
 * MỖI CHUYẾN THÊM MỘT THẺ, DOANH THU THEO KHÁCH TRÀN (G6-1, G6-2)
 * ====================================================================
 * Khung cần N chuyến thì có N − 1 thẻ "chuyến thêm k/(N − 1)". Một chuyến thêm chỉ
 * chở những khách mà xe chuẩn và các chuyến thêm trước nó không chở hết — khách tràn
 * kỳ vọng rơi vào ghế của nó, theo phân phối nhu cầu của chính mô hình dự báo. Cầu
 * nối của GĐ5 (tỉ lệ × sức chứa) coi như chuyến thêm nào cũng đầy khách: tuần
 * 23–29/10/2026 nó báo 41/41 chuyến có lãi, tính theo khách tràn chỉ có 4.
 *
 * ====================================================================
 * GIỮ CHỖ GIỮA CÁC THẺ, LÃI CAO XÉT TRƯỚC (G6-3, G6-4; đóng M-14)
 * ====================================================================
 * Các chuyến thêm được xét theo lãi kỳ vọng GIẢM DẦN — tính với xe chuẩn, vì lúc đó
 * chưa chọn xe. Thẻ nào đủ điều kiện thì xe + tài xế của nó được giữ chỗ tạm trong
 * AvailabilityContext, nên thẻ sau không chọn trùng xe, trùng tài xế hay vượt 8 giờ
 * lái: danh sách là một kế hoạch chạy được cùng lúc, và chuyến đáng chạy nhất được
 * giữ xe trước. Trước GĐ6 mỗi thẻ chọn độc lập, nên 41 thẻ của tuần 23–29/10/2026
 * dùng chung 2 xe và 1 tài xế.
 *
 * Mọi chuyến thêm đều có thẻ, kể cả chuyến LỖ dự kiến (màn hình gắn nhãn): ngưỡng 90%
 * vẫn là định nghĩa duy nhất của "cần tăng cường"; chạy hay không do Admin quyết.
 *
 * ====================================================================
 * CHỈ ĐỌC, TÍNH LẠI MỖI LẦN XEM (chốt của chủ dự án)
 * ====================================================================
 * Không entity mới, không ghi DB, không tạo chuyến. Mỗi lần mở trang, engine
 * dựng lại các chuyến ứng viên trong bộ nhớ, chọn tài nguyên và ước tính doanh
 * thu tại chỗ. Chuyến giữ chỗ chỉ sống trong AvailabilityContext của lần xem đó.
 * Vì chọn và validate diễn ra sát nhau trong cùng một transaction đọc, một tài
 * nguyên vừa được chọn gần như luôn qua được cổng — nên STALE hiếm khi xuất hiện;
 * nó vẫn tồn tại để trung thực khi cổng thật sự trượt và làm lưới an toàn nếu
 * logic chọn và logic validate về sau lệch nhau. Cổng chạy SQL nên không thấy
 * chuyến giữ chỗ; việc tránh trùng giữa các thẻ là của bước chọn.
 *
 * ====================================================================
 * DOANH THU + CHI PHÍ + LỢI NHUẬN (bước 3, 2026-07-24; doanh thu đổi ở GĐ6)
 * ====================================================================
 * Doanh thu = số khách kỳ vọng của chuyến thêm × giá vé lịch sử của khung. Chi phí
 * KHÔNG có sẵn trong domain, nên nó là THAM SỐ do Admin cấu hình (CostParameters):
 * chi phí = nhiên liệu/km × quãng đường + lương/giờ × thời lượng × số tài xế. Lợi
 * nhuận = doanh thu − chi phí. Engine chỉ ĐỌC tham số qua CostParameterService,
 * không tự bịa hằng số — xem CostParameters.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ForecastService forecastService;
    private final TripService tripService;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final CostParameterService costParameterService;

    /** Hạn mức giờ lái/ngày để suy ra số tài xế cần cho một chuyến. */
    private static final double MAX_DAILY_DRIVING_HOURS = 8.0;

    /** Thời lượng mặc định (phút) nếu tuyến thiếu estimatedDuration — cùng giá trị fallback của createExtraTrip(). */
    private static final int DEFAULT_DURATION_MINUTES = 240;

    /**
     * Một chuyến thêm mà dự báo đòi (G6-2), trước khi chọn xe: khung, thứ tự k trong các
     * chuyến thêm của khung, và phần không phụ thuộc xe — thời lượng, số tài xế, giá vé, chi
     * phí, lãi kỳ vọng nếu chạy bằng xe chuẩn. Lãi đó quyết định thứ tự xét và giữ chỗ.
     */
    private record ExtraTrip(ForecastSeriesDto series, ForecastPointDto point, Route route, int index, int count,
            int durationMinutes, int requiredDrivers, BigDecimal price, BigDecimal cost, BigDecimal plannedProfit) {
    }

    @Transactional(readOnly = true)
    public RecommendationViewDto buildRecommendations() {
        // Gọi ĐÚNG MỘT LẦN: buildForecast() nạp lịch sử và khớp (hoặc dùng lại) mô hình,
        // gọi lặp sẽ vừa phí vừa rủi ro hai ảnh chụp lệch nhau. Độ tươi dữ liệu của trang
        // cũng lấy từ chính DTO này.
        DemandForecastViewDto forecast = forecastService.buildForecast();

        Map<Long, Route> routesById = routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Integer, BigDecimal>> priceByRouteAndHour = loadLatestPrices();

        // Tham số chi phí do Admin cấu hình (hoặc mặc định nếu chưa đặt). Nạp MỘT
        // LẦN cho cả request — một dòng cấu hình duy nhất, không phụ thuộc thẻ.
        CostParameters cost = costParameterService.getOrDefault();

        // Nạp lịch MỘT LẦN cho toàn chân trời dự báo, rồi chọn xe/tài xế trên bộ
        // nhớ thay vì truy vấn DB từng xe/tài xế cho mỗi thẻ. Luật chọn vẫn nằm
        // trong TripService; đây chỉ đổi nguồn dữ liệu. spanEnd cộng dư 1 ngày để
        // phủ hết cửa sổ bận của thẻ ở ngày cuối chân trời.
        LocalDateTime spanStart = LocalDate.now().atStartOfDay();
        LocalDateTime spanEnd = forecast.getForecastFrom()
                .plusDays(forecast.getHorizonDays() + 1L).atStartOfDay();
        AvailabilityContext availability = tripService.buildAvailabilityContext(spanStart, spanEnd);

        // Độ phân tán nhu cầu của lần khớp, cho phép tính khách tràn. Chưa có chuỗi nào
        // thì không có mô hình — và cũng không có chuyến thêm nào cần tới nó.
        double nbSize = forecast.getModel() != null ? forecast.getModel().getNegativeBinomialSize() : 0.0;

        // ── Lập danh sách chuyến thêm: mỗi khung đông cần (số chuyến cần − 1) chuyến ──
        List<ExtraTrip> extras = new ArrayList<>();
        int hotSlots = 0;
        for (ForecastSeriesDto series : forecast.getSeries()) {
            Route route = routesById.get(series.getRouteId());
            if (route == null) {
                // Chuỗi tham chiếu một tuyến đã bị xóa — bỏ qua thay vì đoán.
                continue;
            }
            BigDecimal slotPrice = priceOf(priceByRouteAndHour, series.getRouteId(), series.getDepartureHour());
            int durationMinutes = route.getEstimatedDuration() != null && route.getEstimatedDuration() > 0
                    ? route.getEstimatedDuration()
                    : DEFAULT_DURATION_MINUTES;
            double durationHours = durationMinutes / 60.0;
            // MVP một tài xế chính: mọi tuyến vào được forecast đều ≤ 8h theo cách
            // dựng dữ liệu (HistoricalDataBackfill chỉ nhận tuyến ≤ 8h), nên requiredDrivers
            // luôn = 1 và không cần tài xế phụ / phụ xe. Vẫn tính requiredDrivers để
            // trung thực; một tuyến > 8h trong tương lai sẽ lộ ra ở cổng validateStaff
            // chứ không làm hỏng gì.
            int requiredDrivers = Math.max(1, (int) Math.ceil(durationHours / MAX_DAILY_DRIVING_HOURS));
            BigDecimal tripCost = estimateCost(route, durationHours, requiredDrivers, cost);
            int seats = series.getStandardSeats();

            for (ForecastPointDto point : series.getPoints()) {
                if (!point.isNeedsReinforcement()) {
                    continue;
                }
                hotSlots++;
                int count = point.getTripsNeeded() - 1;
                for (int k = 1; k <= count; k++) {
                    double planned = ForecastService.extraTripPassengers(point.getPredictedDemand(), k * seats, seats,
                            nbSize);
                    extras.add(new ExtraTrip(series, point, route, k, count, durationMinutes, requiredDrivers,
                            slotPrice, tripCost, profitOf(revenueOf(slotPrice, planned), tripCost)));
                }
            }
        }

        // Xét theo lãi kỳ vọng giảm dần: chuyến đáng chạy nhất được giữ xe + tài xế trước.
        // Trong một khung, chuyến thêm sau chở ít khách tràn hơn chuyến trước, nên lãi của
        // nó không cao hơn; bằng nhau thì k nhỏ trước. Vì vậy các chuyến thêm của một khung
        // luôn được xét theo thứ tự 1, 2, … — điều seatsBefore bên dưới dựa vào.
        extras.sort(Comparator.comparing(ExtraTrip::plannedProfit, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(e -> e.point().getDate())
                .thenComparing(e -> e.series().getRouteLabel(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(e -> e.series().getDepartureHour())
                .thenComparingInt(ExtraTrip::index));

        // Ghế khung đã có trước chuyến thêm đang xét: xe chuẩn cộng các chuyến thêm trước nó.
        Map<String, Integer> seatsSoFar = new HashMap<>();
        List<RecommendationCardDto> cards = new ArrayList<>();
        for (ExtraTrip extra : extras) {
            String slot = extra.series().getRouteId() + "@" + extra.series().getDepartureHour() + "@"
                    + extra.point().getDate();
            int seatsBefore = seatsSoFar.getOrDefault(slot, extra.series().getStandardSeats());
            RecommendationCardDto card = buildCard(extra, seatsBefore, nbSize, availability);
            seatsSoFar.put(slot, seatsBefore + tripSeats(card, extra));
            cards.add(card);
        }

        // Hiển thị theo lợi nhuận ước tính giảm dần (chuyến có lãi lên đầu), rồi ngày gần
        // trước, tên tuyến, giờ và thứ tự chuyến thêm cho ổn định.
        cards.sort(Comparator.comparing(RecommendationCardDto::getEstimatedProfit,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendationCardDto::getDate)
                .thenComparing(RecommendationCardDto::getRouteLabel, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(RecommendationCardDto::getDepartureHour)
                .thenComparingInt(RecommendationCardDto::getExtraIndex));

        int recommended = (int) cards.stream()
                .filter(c -> c.getStatus() == RecommendationStatus.RECOMMENDED).count();
        int noResource = (int) cards.stream()
                .filter(c -> c.getStatus() == RecommendationStatus.NO_RESOURCE).count();
        int profitable = (int) cards.stream()
                .filter(c -> c.getEstimatedProfit() != null && c.getEstimatedProfit().signum() > 0).count();
        int loss = (int) cards.stream()
                .filter(c -> c.getEstimatedProfit() != null && c.getEstimatedProfit().signum() < 0).count();

        return new RecommendationViewDto(
                cards,
                forecast.getHistoryEnd(),
                forecast.getHistoryStaleDays(),
                forecast.getForecastFrom(),
                forecast.getHorizonDays(),
                hotSlots,
                cards.size(),
                recommended,
                noResource,
                profitable,
                loss,
                cost.getFuelCostPerKm(),
                cost.getDriverWagePerHour());
    }

    /**
     * Dựng thẻ cho một chuyến thêm.
     *
     * Chuyến ứng viên là một Trip TẠM (không lưu), đúng khuôn createExtraTrip():
     * route + giờ khởi hành + giờ đến suy từ estimatedDuration. Nó chạy qua bộ chọn
     * tài nguyên và cổng validation; qua cổng thì được giữ chỗ trong availability.
     *
     * @param seatsBefore ghế khung đã có trước chuyến này: xe chuẩn và các chuyến thêm trước nó
     */
    private RecommendationCardDto buildCard(ExtraTrip extra, int seatsBefore, double nbSize,
            AvailabilityContext availability) {

        ForecastSeriesDto series = extra.series();
        ForecastPointDto point = extra.point();
        LocalDateTime departure = point.getDate().atTime(series.getDepartureHour(), 0);
        LocalDateTime arrival = departure.plusMinutes(extra.durationMinutes());
        double durationHours = extra.durationMinutes() / 60.0;

        Trip candidate = new Trip();
        candidate.setRoute(extra.route());
        candidate.setDepartureTime(departure);
        candidate.setArrivalTimeExpected(arrival);

        RecommendationCardDto card = baseCard(extra);
        card.setTicketPrice(extra.price());
        card.setEstimatedCost(extra.cost());

        // ── Bước: chọn xe, rồi tài xế ─────────────────────────────────────────
        Bus bus = tripService.selectBestAvailableBus(candidate, departure, arrival, availability);
        Driver driver = null;
        int capacity = 0;
        if (bus != null) {
            card.setBusPlate(bus.getLicensePlate());
            capacity = capacityOf(bus);
            card.setBusCapacity(capacity > 0 ? capacity : null);
            driver = tripService.selectBestAvailableDriver(departure, arrival, durationHours,
                    extra.requiredDrivers(), List.of(), false, availability);
            if (driver != null) {
                card.setDriverName(driverName(driver));
            }
        }

        // ── Ước tính khách, doanh thu, lợi nhuận (G6-1) ──────────────────────
        // Chuyến thêm chở phần khách tràn rơi vào ghế của nó: ghế của xe được chọn, hoặc
        // của xe chuẩn khi chưa chọn được xe. Lợi nhuận có thể ÂM — hiển thị trung thực.
        int ownSeats = capacity > 0 ? capacity : series.getStandardSeats();
        double passengers = ForecastService.extraTripPassengers(point.getPredictedDemand(), seatsBefore, ownSeats,
                nbSize);
        card.setExpectedPassengers(passengers);
        card.setEstimatedRevenue(revenueOf(extra.price(), passengers));
        card.setEstimatedProfit(profitOf(card.getEstimatedRevenue(), extra.cost()));

        if (bus == null) {
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Không có xe rảnh và chưa tới ngưỡng bảo dưỡng cho khung giờ này"
                    + " — số khách tính với xe chuẩn " + series.getStandardSeats() + " chỗ");
            return card;
        }
        if (driver == null) {
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Có xe " + bus.getLicensePlate()
                    + " nhưng không có tài xế nào hợp lệ và rảnh cho khung giờ này");
            return card;
        }

        // ── Cổng Business Rule Validation (Phase 3) — xác nhận lần cuối ──────────
        candidate.setBus(bus);
        candidate.setDriver(driver);
        candidate.setTotalSeats(capacity);
        ValidationResult busGate = tripService.validateBusForTripDryRun(bus, candidate, null);
        ValidationResult staffGate = tripService.validateStaffForTripDryRun(candidate, null);

        if (busGate.isValid() && staffGate.isValid()) {
            card.setStatus(RecommendationStatus.RECOMMENDED);
            card.setNote("Đủ điều kiện phân công — xe và tài xế này được giữ cho chuyến này,"
                    + " các thẻ khác không dùng lại trong cùng giờ");
            // ── Giữ chỗ tạm (G6-3): như một chuyến tăng cường vừa tạo, đang chờ duyệt ──
            candidate.setStatus(TripStatus.PENDING_APPROVAL);
            availability.reserve(candidate);
        } else {
            card.setStatus(RecommendationStatus.STALE);
            card.setNote(!busGate.isValid() ? busGate.getFailureReason() : staffGate.getFailureReason());
        }
        return card;
    }

    /** Phần khung dùng chung, chưa gắn tài nguyên/kết cục. */
    private RecommendationCardDto baseCard(ExtraTrip extra) {
        ForecastSeriesDto series = extra.series();
        ForecastPointDto point = extra.point();
        RecommendationCardDto card = new RecommendationCardDto();
        card.setRouteId(series.getRouteId());
        card.setRouteLabel(series.getRouteLabel());
        card.setDepartureHour(series.getDepartureHour());
        card.setSlotLabel(series.getSlotLabel());
        card.setDate(point.getDate());
        card.setDayLabel(point.getDayLabel());
        card.setWeekend(point.isWeekend());
        card.setPredictedDemand(point.getPredictedDemand());
        card.setStandardSeats(series.getStandardSeats());
        card.setDemandRatio(point.getDemandRatio());
        card.setExtraIndex(extra.index());
        card.setExtraCount(extra.count());
        return card;
    }

    /** Ghế mà chuyến thêm của thẻ góp vào khung — cùng số ghế buildCard() đã dùng để tính khách. */
    private int tripSeats(RecommendationCardDto card, ExtraTrip extra) {
        return card.getBusCapacity() != null ? card.getBusCapacity() : extra.series().getStandardSeats();
    }

    /** Số khách × giá vé, làm tròn tới đồng; null khi khung chưa có giá vé lịch sử. */
    private BigDecimal revenueOf(BigDecimal price, double passengers) {
        return price == null ? null
                : price.multiply(BigDecimal.valueOf(passengers)).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal profitOf(BigDecimal revenue, BigDecimal cost) {
        return revenue != null && cost != null ? revenue.subtract(cost) : null;
    }

    /**
     * Giá vé lịch sử theo (tuyến → giờ khởi hành → giá của chuyến COMPLETED gần
     * nhất). Repository trả scalar đã sắp theo thời gian GIẢM DẦN, nên
     * putIfAbsent giữ lại đúng dòng đầu tiên gặp cho mỗi khung — tức chuyến mới
     * nhất. Không nạp entity Trip: một ứng viên chỉ cần con số giá.
     */
    private Map<Long, Map<Integer, BigDecimal>> loadLatestPrices() {
        Map<Long, Map<Integer, BigDecimal>> result = new HashMap<>();
        for (Object[] row : tripRepository.findPriceHistoryByStatus(TripStatus.COMPLETED)) {
            if (row[0] == null || row[1] == null || row[2] == null) {
                continue;
            }
            Long routeId = ((Number) row[0]).longValue();
            int hour = ((LocalDateTime) row[1]).getHour();
            BigDecimal price = (BigDecimal) row[2];
            result.computeIfAbsent(routeId, k -> new HashMap<>()).putIfAbsent(hour, price);
        }
        return result;
    }

    private BigDecimal priceOf(Map<Long, Map<Integer, BigDecimal>> prices, Long routeId, int hour) {
        return prices.getOrDefault(routeId, Map.of()).get(hour);
    }

    private int capacityOf(Bus bus) {
        return bus.getBusType() != null ? bus.getBusType().getCapacity() : 0;
    }

    /**
     * Chi phí vận hành ước tính cho một chuyến ứng viên, theo tham số Admin:
     *   nhiên liệu = fuelCostPerKm × Route.distanceKm
     *   lương      = driverWagePerHour × thời lượng (giờ) × số tài xế cần
     * Bỏ qua thành phần nào thiếu dữ liệu (quãng đường null / tham số null) thay
     * vì đoán. Làm tròn về đồng (0 chữ số thập phân) cho khớp cách hiển thị tiền.
     */
    private BigDecimal estimateCost(Route route, double durationHours, int requiredDrivers, CostParameters cost) {
        BigDecimal fuel = BigDecimal.ZERO;
        if (route.getDistanceKm() != null && cost.getFuelCostPerKm() != null) {
            fuel = cost.getFuelCostPerKm().multiply(BigDecimal.valueOf(route.getDistanceKm()));
        }
        BigDecimal wage = BigDecimal.ZERO;
        if (cost.getDriverWagePerHour() != null) {
            wage = cost.getDriverWagePerHour()
                    .multiply(BigDecimal.valueOf(durationHours))
                    .multiply(BigDecimal.valueOf(requiredDrivers));
        }
        return fuel.add(wage).setScale(0, RoundingMode.HALF_UP);
    }

    private String driverName(Driver driver) {
        return driver.getUser() != null ? driver.getUser().getFullName() : "Tài xế #" + driver.getUserId();
    }
}
