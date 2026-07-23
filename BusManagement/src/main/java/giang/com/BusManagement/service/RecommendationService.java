package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PHASE 7 (bước 2) — Recommendation Engine (chỉ doanh thu).
 *
 * Trả lời câu hỏi: <b>"khung giờ nào sắp tới được dự báo đông, và ta có thể chạy
 * chuyến tăng cường bằng xe + tài xế nào, thu về bao nhiêu?"</b>
 *
 * ====================================================================
 * LÀ NGƯỜI ĐIỀU PHỐI, KHÔNG PHẢI BỘ DỰ BÁO HAY BỘ LUẬT
 * ====================================================================
 * Service này KHÔNG tự dự báo và KHÔNG tự định nghĩa ràng buộc nào. Nó ghép nối
 * ba thứ đã có:
 * <ul>
 *   <li><b>Dự báo (Phase 6):</b> gọi {@code ForecastService.buildForecast()}
 *       ĐÚNG MỘT LẦN mỗi request, rồi lặp trên kết quả. Nó chỉ đọc cờ
 *       {@code ForecastPointDto.needsReinforcement} — <b>không hề biết ngưỡng
 *       0.90</b>. Ngưỡng đó sống ở Trip/DashboardService/ForecastService; quyết
 *       định "đông" đã được nướng sẵn vào cờ boolean, engine chỉ tiêu thụ quyết
 *       định chứ không tiêu thụ con số.</li>
 *   <li><b>Chọn tài nguyên:</b> {@code TripService.selectBestAvailableBus/Driver}
 *       — chính các bộ lọc rule+availability đã kiểm chứng, không nhân bản.</li>
 *   <li><b>Cổng validation (Phase 3):</b> {@code validateBusForTripDryRun} /
 *       {@code validateStaffForTripDryRun} xác nhận lần cuối, đúng khuôn
 *       confirmAutoAssignedTrip (chọn xong rồi validate lại).</li>
 * </ul>
 *
 * ====================================================================
 * CHỈ ĐỌC, TÍNH LẠI MỖI LẦN XEM (chốt của chủ dự án)
 * ====================================================================
 * Không entity mới, không ghi DB, không tạo chuyến. Mỗi lần mở trang, engine
 * dựng lại các chuyến ứng viên trong bộ nhớ, chọn tài nguyên và ước tính doanh
 * thu tại chỗ. Vì chọn và validate diễn ra sát nhau trong cùng một transaction
 * đọc, một tài nguyên vừa được chọn gần như luôn qua được cổng — nên STALE hiếm
 * khi xuất hiện; nó vẫn tồn tại để trung thực khi cổng thật sự trượt và làm lưới
 * an toàn nếu logic chọn và logic validate về sau lệch nhau.
 *
 * ====================================================================
 * CHỈ DOANH THU — CHI PHÍ BỊ CHẶN
 * ====================================================================
 * Doanh thu tính được ngay: tỉ lệ dự báo × sức chứa xe được chọn × giá vé lịch
 * sử của khung. Chi phí thì KHÔNG có dữ liệu trong domain (không nhiên liệu,
 * lương, phí/km) nên bước 3 bị chặn chờ chủ dự án quyết — service này cố ý không
 * bịa mô hình chi phí.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ForecastService forecastService;
    private final TripService tripService;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;

    /** Hạn mức giờ lái/ngày để suy ra số tài xế cần cho một chuyến. */
    private static final double MAX_DAILY_DRIVING_HOURS = 8.0;

    /** Thời lượng mặc định (phút) nếu tuyến thiếu estimatedDuration — cùng giá trị fallback của createExtraTrip(). */
    private static final int DEFAULT_DURATION_MINUTES = 240;

    @Transactional(readOnly = true)
    public RecommendationViewDto buildRecommendations() {
        // Gọi ĐÚNG MỘT LẦN: buildForecast() nạp toàn bộ COMPLETED + hồi quy +
        // backtest, gọi lặp sẽ vừa phí vừa rủi ro hai ảnh chụp lệch nhau. Độ tươi
        // dữ liệu của trang cũng lấy từ chính DTO này.
        DemandForecastViewDto forecast = forecastService.buildForecast();

        Map<Long, Route> routesById = routeRepository.findAllWithStations().stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Integer, BigDecimal>> priceByRouteAndHour = loadLatestPrices();

        // Nạp lịch MỘT LẦN cho toàn chân trời dự báo, rồi chọn xe/tài xế trên bộ
        // nhớ thay vì truy vấn DB từng xe/tài xế cho mỗi thẻ. Luật chọn vẫn nằm
        // trong TripService; đây chỉ đổi nguồn dữ liệu. spanEnd cộng dư 1 ngày để
        // phủ hết cửa sổ bận của thẻ ở ngày cuối chân trời.
        LocalDateTime spanStart = LocalDate.now().atStartOfDay();
        LocalDateTime spanEnd = forecast.getForecastFrom()
                .plusDays(forecast.getHorizonDays() + 1L).atStartOfDay();
        AvailabilityContext availability = tripService.buildAvailabilityContext(spanStart, spanEnd);

        List<RecommendationCardDto> cards = new ArrayList<>();
        for (ForecastSeriesDto series : forecast.getSeries()) {
            Route route = routesById.get(series.getRouteId());
            if (route == null) {
                // Chuỗi tham chiếu một tuyến đã bị xóa — bỏ qua thay vì đoán.
                continue;
            }
            BigDecimal slotPrice = priceOf(priceByRouteAndHour, series.getRouteId(), series.getDepartureHour());
            for (ForecastPointDto point : series.getPoints()) {
                if (point.isNeedsReinforcement()) {
                    cards.add(buildCard(series, point, route, slotPrice, availability));
                }
            }
        }

        // Đông nhất lên đầu (giống Vehicle Replacement xếp theo điểm, Forecast xếp
        // theo đỉnh) — rồi tới ngày gần trước, rồi tên tuyến cho ổn định.
        cards.sort(Comparator.comparingDouble(RecommendationCardDto::getPredictedOccupancy).reversed()
                .thenComparing(RecommendationCardDto::getDate)
                .thenComparing(RecommendationCardDto::getRouteLabel,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int recommended = (int) cards.stream()
                .filter(c -> c.getStatus() == RecommendationStatus.RECOMMENDED).count();
        int noResource = (int) cards.stream()
                .filter(c -> c.getStatus() == RecommendationStatus.NO_RESOURCE).count();

        return new RecommendationViewDto(
                cards,
                forecast.getHistoryEnd(),
                forecast.getHistoryStaleDays(),
                forecast.getForecastFrom(),
                forecast.getHorizonDays(),
                cards.size(),
                recommended,
                noResource,
                hasSharedSlotContention(cards));
    }

    /**
     * Dựng một thẻ cho một ngày-khung được dự báo đông.
     *
     * Chuyến ứng viên là một Trip TẠM (không lưu), đúng khuôn createExtraTrip():
     * route + giờ khởi hành + giờ đến suy từ estimatedDuration. Nó chỉ tồn tại để
     * chạy qua bộ chọn tài nguyên và cổng validation.
     */
    private RecommendationCardDto buildCard(ForecastSeriesDto series, ForecastPointDto point,
            Route route, BigDecimal slotPrice, AvailabilityContext availability) {

        LocalDateTime departure = point.getDate().atTime(series.getDepartureHour(), 0);
        int durationMinutes = route.getEstimatedDuration() != null && route.getEstimatedDuration() > 0
                ? route.getEstimatedDuration()
                : DEFAULT_DURATION_MINUTES;
        LocalDateTime arrival = departure.plusMinutes(durationMinutes);
        double durationHours = durationMinutes / 60.0;

        Trip candidate = new Trip();
        candidate.setRoute(route);
        candidate.setDepartureTime(departure);
        candidate.setArrivalTimeExpected(arrival);

        RecommendationCardDto card = baseCard(series, point);
        card.setTicketPrice(slotPrice);

        // ── Bước: chọn xe ──────────────────────────────────────────────────────
        Bus bus = tripService.selectBestAvailableBus(candidate, departure, arrival, availability);
        if (bus == null) {
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Không có xe rảnh, đúng loại và chưa tới ngưỡng bảo dưỡng cho khung giờ này");
            return card;
        }
        card.setBusPlate(bus.getLicensePlate());
        int capacity = capacityOf(bus);
        card.setBusCapacity(capacity > 0 ? capacity : null);

        // ── Bước: chọn tài xế ──────────────────────────────────────────────────
        // MVP một tài xế chính: mọi tuyến vào được forecast đều ≤ 8h theo cách
        // dựng dữ liệu (HistoricalDataBackfill chỉ nhận tuyến ≤ 8h), nên
        // requiredDrivers luôn = 1 và không cần tài xế phụ / phụ xe. Vẫn tính
        // requiredDrivers để trung thực; một tuyến > 8h trong tương lai sẽ lộ ra
        // ở cổng validateStaff bên dưới chứ không làm hỏng gì.
        int requiredDrivers = Math.max(1, (int) Math.ceil(durationHours / MAX_DAILY_DRIVING_HOURS));
        Driver driver = tripService.selectBestAvailableDriver(departure, arrival, durationHours,
                requiredDrivers, List.of(), false, availability);
        if (driver == null) {
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Có xe " + bus.getLicensePlate()
                    + " nhưng không có tài xế nào hợp lệ và rảnh cho khung giờ này");
            return card;
        }
        card.setDriverName(driverName(driver));

        // ── Ước tính doanh thu (dự báo là TỈ LỆ → nhân sức chứa xe được chọn) ────
        if (capacity > 0) {
            int passengers = (int) Math.round(point.getPredictedOccupancy() * capacity);
            card.setExpectedPassengers(passengers);
            if (slotPrice != null) {
                card.setEstimatedRevenue(slotPrice.multiply(BigDecimal.valueOf(passengers)));
            }
        }

        // ── Cổng Business Rule Validation (Phase 3) — xác nhận lần cuối ──────────
        candidate.setBus(bus);
        candidate.setDriver(driver);
        candidate.setTotalSeats(capacity);
        ValidationResult busGate = tripService.validateBusForTripDryRun(bus, candidate, null);
        ValidationResult staffGate = tripService.validateStaffForTripDryRun(candidate, null);

        if (busGate.isValid() && staffGate.isValid()) {
            card.setStatus(RecommendationStatus.RECOMMENDED);
            card.setNote("Đủ điều kiện phân công — dự báo vượt ngưỡng tăng cường");
        } else {
            card.setStatus(RecommendationStatus.STALE);
            card.setNote(!busGate.isValid() ? busGate.getFailureReason() : staffGate.getFailureReason());
        }
        return card;
    }

    /** Phần khung dùng chung, chưa gắn tài nguyên/kết cục. */
    private RecommendationCardDto baseCard(ForecastSeriesDto series, ForecastPointDto point) {
        RecommendationCardDto card = new RecommendationCardDto();
        card.setRouteId(series.getRouteId());
        card.setRouteLabel(series.getRouteLabel());
        card.setDepartureHour(series.getDepartureHour());
        card.setSlotLabel(series.getSlotLabel());
        card.setDate(point.getDate());
        card.setDayLabel(point.getDayLabel());
        card.setWeekend(point.isWeekend());
        card.setPredictedOccupancy(point.getPredictedOccupancy());
        return card;
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

    private String driverName(Driver driver) {
        return driver.getUser() != null ? driver.getUser().getFullName() : "Tài xế #" + driver.getUserId();
    }

    /**
     * Có hai thẻ trở lên rơi vào cùng (ngày, khung giờ) không — khi đó chúng có
     * thể cùng đề xuất một xe/tài xế vì mỗi thẻ được đánh giá độc lập. Tín hiệu
     * để giao diện cảnh báo "đây là gợi ý, không phải lịch khả thi đồng thời".
     */
    private boolean hasSharedSlotContention(List<RecommendationCardDto> cards) {
        Set<String> seen = new HashSet<>();
        for (RecommendationCardDto card : cards) {
            if (!seen.add(card.getDate() + "@" + card.getDepartureHour())) {
                return true;
            }
        }
        return false;
    }
}
