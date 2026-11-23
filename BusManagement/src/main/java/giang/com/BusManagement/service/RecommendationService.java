package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.LoopLegKind;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PHASE 7 — Recommendation Engine (bước 2: doanh thu; bước 3: + chi phí & lợi nhuận).
 * Chuyển sang đơn vị nhu cầu ở GĐ6 của đợt thiết kế lại dự báo (QĐ-17 trong
 * docs/development/forecast_decisions.md); tính lãi CẢ VÒNG ở route pairs CT7 (QĐ-22 trong
 * docs/development/route_pairs_decisions.md).
 *
 * Trả lời câu hỏi: <b>"khung giờ nào sắp tới được dự báo đông, còn thiếu mấy chuyến,
 * mỗi chuyến thêm chạy bằng xe + tài xế nào, xe đó đi rồi về thế nào, chở thêm được bao
 * nhiêu khách, cả vòng lãi hay lỗ bao nhiêu?"</b>
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
 *       — chính các bộ lọc rule+availability đã kiểm chứng (kể cả luật vị trí CT5b), không nhân bản.
 *       Mọi lượt của một vòng — chuyến thêm, lượt về, lượt đưa xe tới — đều chọn bằng hai hàm này.</li>
 *   <li><b>Cổng validation (Phase 3):</b> {@code validateBusForTripDryRun} /
 *       {@code validateStaffForTripDryRun} xác nhận lần cuối, đúng khuôn
 *       confirmAutoAssignedTrip (chọn xong rồi validate lại).</li>
 * </ul>
 * Tiền (giá vé, chi phí một lượt, khách của lượt đi kèm) do {@link LoopEconomics} tính — cùng phép
 * tính với What-if.
 *
 * ====================================================================
 * MỖI CHUYẾN THÊM MỘT THẺ, CHỈ PHẦN KHUNG CÒN THIẾU (G6-1, G6-2; CT7 cách 2a)
 * ====================================================================
 * Khung cần N chuyến (QĐ-18) và đã có M chuyến chưa hủy — chuyến thường lệ, chuyến tăng cường của
 * job 10 giây, chuyến Admin tạo tay — thì có N − M thẻ. Khung chưa có chuyến nào (chưa tạo lịch thường
 * lệ) thì coi như M = 1 xe chuẩn, đúng giả định trước CT7. Một chuyến thêm chỉ chở những khách mà các
 * chuyến đã có và các chuyến thêm trước nó không chở hết — khách tràn kỳ vọng rơi vào ghế của nó, theo
 * phân phối nhu cầu của chính mô hình dự báo, tính trên SỐ GHẾ THẬT của các chuyến đã có.
 *
 * ====================================================================
 * MỖI THẺ LÀ MỘT VÒNG (CT7, PA-B)
 * ====================================================================
 * Xe tăng cường rời bến nhà thì phải về; xe đến bến đi từ đầu kia thì phải được đưa tới. Vì vậy mỗi
 * thẻ gồm chuyến thêm và LƯỢT ĐI KÈM của nó ({@link LoopLegKind}), cùng giờ một chuyến thường lệ của
 * chiều ngược:
 * <ul>
 *   <li>có xe rảnh ngay ở bến đi → chọn xe đó; nếu chuyến thêm làm xe rời bến nhà thì nó "về ngay"
 *       (RETURN) bằng khung chiều ngược sớm nhất có xe và tài xế — đúng luật H1 của lịch sử (QĐ-21 (d));
 *       nếu chuyến thêm đưa xe về đúng bến nhà thì không cần lượt đi kèm (NONE);</li>
 *   <li>không có xe ở bến đi → "đưa xe tới trước" (BRING): một xe ở đầu kia đi cùng khung chiều ngược
 *       muộn nhất còn kịp, rồi chạy chuyến thêm;</li>
 *   <li>tuyến không có đúng một tuyến chiều ngược, hay trong 2 ngày không xếp được lượt về → tính như
 *       một lượt về rỗng (ASSUMED_RETURN).</li>
 * </ul>
 * Lãi của thẻ = tiền vé chuyến thêm + tiền vé lượt đi kèm − chi phí hai lượt. Hai thẻ ngược chiều KHÔNG
 * được ghép thành một vòng (để CT9, cùng luật giữ–về).
 *
 * ====================================================================
 * GIỮ CHỖ GIỮA CÁC THẺ, LÃI CAO XÉT TRƯỚC (G6-3, G6-4; đóng M-14)
 * ====================================================================
 * Các chuyến thêm được xét theo lãi kỳ vọng CẢ VÒNG GIẢM DẦN — tính với xe chuẩn và lượt đi kèm theo
 * kế hoạch ({@link LoopEconomics#planned}), vì lúc đó chưa chọn xe. Thẻ nào đủ điều kiện thì mọi lượt
 * của vòng được giữ chỗ tạm trong AvailabilityContext, nên thẻ sau không chọn trùng xe, trùng tài xế,
 * vượt 8 giờ lái, và thấy xe đã đi đâu: danh sách là một kế hoạch chạy được cùng lúc. Thẻ ở bến xa tự
 * mang lượt đưa xe tới của mình, nên không còn phải chờ một thẻ lãi thấp hơn đưa xe lên giúp (điều
 * `verify` CT5b đo được).
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
 * DOANH THU + CHI PHÍ + LỢI NHUẬN (bước 3, 2026-07-24; doanh thu đổi ở GĐ6; cả vòng từ CT7)
 * ====================================================================
 * Doanh thu = số khách kỳ vọng × giá vé lịch sử của khung, cho chuyến thêm và lượt đi kèm. Chi phí
 * KHÔNG có sẵn trong domain, nên nó là THAM SỐ do Admin cấu hình (CostParameters):
 * chi phí một lượt = nhiên liệu/km × quãng đường + lương/giờ × thời lượng × số tài xế. Lợi
 * nhuận = doanh thu − chi phí của cả vòng. Engine chỉ ĐỌC tham số qua CostParameterService,
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
    private final BusRepository busRepository;

    /** Chuyến chiếm chỗ của khung (CT7, cách 2a): mọi trạng thái trừ CANCELLED. */
    static final List<TripStatus> SLOT_STATUSES = List.of(TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE,
            TripStatus.DEPARTED, TripStatus.COMPLETED);

    private static final DateTimeFormatter LEG_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    /**
     * Một chuyến thêm mà dự báo đòi (G6-2), trước khi chọn xe: khung, thứ tự k trong các chuyến thêm
     * của khung, chuyến đã có trong khung, tuyến chiều ngược, và phần không phụ thuộc xe — thời lượng,
     * số tài xế, giá vé, chi phí một lượt, lượt đi kèm theo kế hoạch và lãi CẢ VÒNG kỳ vọng nếu chạy
     * bằng xe chuẩn. Lãi đó quyết định thứ tự xét và giữ chỗ.
     */
    private record ExtraTrip(ForecastSeriesDto series, ForecastPointDto point, Route route, Route reverse,
            int index, int count, LoopEconomics.SlotTrips inSlot, boolean slotAssumed, int durationMinutes,
            int requiredDrivers, BigDecimal price, BigDecimal cost, LoopEconomics.Leg plannedLeg,
            BigDecimal plannedProfit) {
    }

    /** Kết quả chọn cho một lượt: xe, tài xế, và chuyến tạm đã giữ chỗ. */
    private record Assigned(Trip trip, Bus bus, Driver driver) {
    }

    @Transactional(readOnly = true)
    public RecommendationViewDto buildRecommendations() {
        // Gọi ĐÚNG MỘT LẦN: buildForecast() nạp lịch sử và khớp (hoặc dùng lại) mô hình,
        // gọi lặp sẽ vừa phí vừa rủi ro hai ảnh chụp lệch nhau. Độ tươi dữ liệu của trang
        // cũng lấy từ chính DTO này.
        DemandForecastViewDto forecast = forecastService.buildForecast();

        List<Route> routes = routeRepository.findAllWithStations();
        Map<Long, Route> routesById = routes.stream()
                .collect(Collectors.toMap(Route::getId, Function.identity(), (a, b) -> a));
        Map<Long, Route> reverseOf = reverseRoutes(routes);
        Set<Long> homeStations = homeStations();

        // Tham số chi phí do Admin cấu hình (hoặc mặc định nếu chưa đặt). Nạp MỘT
        // LẦN cho cả request — một dòng cấu hình duy nhất, không phụ thuộc thẻ.
        CostParameters cost = costParameterService.getOrDefault();

        // Nạp lịch MỘT LẦN cho toàn chân trời dự báo, rồi chọn xe/tài xế trên bộ
        // nhớ thay vì truy vấn DB từng xe/tài xế cho mỗi thẻ. Luật chọn vẫn nằm
        // trong TripService; đây chỉ đổi nguồn dữ liệu. spanEnd cộng dư 1 ngày để
        // phủ hết cửa sổ bận của thẻ ở ngày cuối chân trời, và thêm COMPANION_SEARCH_DAYS ngày cho
        // lượt về của các thẻ đó (CT7).
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime spanStart = LocalDate.now().atStartOfDay();
        LocalDateTime spanEnd = forecast.getForecastFrom()
                .plusDays(forecast.getHorizonDays() + 1L + LoopEconomics.COMPANION_SEARCH_DAYS).atStartOfDay();
        AvailabilityContext availability = tripService.buildAvailabilityContext(spanStart, spanEnd);

        // Độ phân tán nhu cầu của lần khớp, cho phép tính khách tràn. Chưa có chuỗi nào
        // thì không có mô hình — và cũng không có chuyến thêm nào cần tới nó.
        double nbSize = forecast.getModel() != null ? forecast.getModel().getNegativeBinomialSize() : 0.0;

        LoopEconomics economics = new LoopEconomics(loadLatestPrices(), LoopEconomics.demandBySlot(forecast),
                LoopEconomics.slotTrips(tripRepository.findSlotSeatsBetween(SLOT_STATUSES, spanStart, spanEnd)),
                nbSize, 1.0, cost.getFuelCostPerKm(), cost.getDriverWagePerHour());

        // ── Lập danh sách chuyến thêm: mỗi khung đông còn thiếu (số chuyến cần − số chuyến đã có) ──
        List<ExtraTrip> extras = new ArrayList<>();
        int hotSlots = 0;
        int alreadyInSlots = 0;
        for (ForecastSeriesDto series : forecast.getSeries()) {
            Route route = routesById.get(series.getRouteId());
            if (route == null) {
                // Chuỗi tham chiếu một tuyến đã bị xóa — bỏ qua thay vì đoán.
                continue;
            }
            Route reverse = reverseOf.get(route.getId());
            BigDecimal slotPrice = economics.price(series.getRouteId(), series.getDepartureHour());
            int durationMinutes = LoopEconomics.durationMinutes(route);
            // Mọi tuyến của ba cặp ≤ 8 giờ nên requiredDrivers luôn = 1 và không cần tài xế phụ / phụ
            // xe; vẫn tính để trung thực — một tuyến > 8h sau này sẽ lộ ra ở cổng validateStaff.
            int requiredDrivers = LoopEconomics.requiredDrivers(durationMinutes / 60.0);
            BigDecimal tripCost = economics.tripCost(route);
            int seats = series.getStandardSeats();

            for (ForecastPointDto point : series.getPoints()) {
                if (!point.isNeedsReinforcement()) {
                    continue;
                }
                hotSlots++;
                LocalDate date = point.getDate();
                int hour = series.getDepartureHour();
                boolean assumed = economics.existing(route.getId(), date, hour) == null;
                LoopEconomics.SlotTrips inSlot = economics.inSlot(route.getId(), date, hour, seats);
                int stillNeeded = LoopEconomics.extrasStillNeeded(point.getTripsNeeded(), inSlot);
                alreadyInSlots += point.getTripsNeeded() - 1 - stillNeeded;

                LocalDateTime departure = date.atTime(hour, 0);
                LoopEconomics.Leg plannedLeg = economics.planned(route, reverse, departure,
                        departure.plusMinutes(durationMinutes), homeStations, now, seats);
                for (int k = 1; k <= stillNeeded; k++) {
                    double planned = ForecastService.extraTripPassengers(point.getPredictedDemand(),
                            inSlot.seats() + (k - 1) * seats, seats, nbSize);
                    BigDecimal plannedProfit = profitOf(
                            LoopEconomics.plus(LoopEconomics.revenue(slotPrice, planned), plannedLeg.revenue()),
                            LoopEconomics.plus(tripCost, plannedLeg.cost()));
                    extras.add(new ExtraTrip(series, point, route, reverse, inSlot.trips() - 1 + k,
                            point.getTripsNeeded() - 1, inSlot, assumed, durationMinutes, requiredDrivers,
                            slotPrice, tripCost, plannedLeg, plannedProfit));
                }
            }
        }

        // Xét theo lãi cả vòng kỳ vọng giảm dần: vòng đáng chạy nhất được giữ xe + tài xế trước.
        // Trong một khung, các chuyến thêm có cùng lượt đi kèm theo kế hoạch, và chuyến thêm sau chở
        // ít khách tràn hơn chuyến trước, nên lãi của nó không cao hơn; bằng nhau thì k nhỏ trước. Vì
        // vậy các chuyến thêm của một khung luôn được xét theo thứ tự — điều seatsBefore bên dưới dựa vào.
        extras.sort(Comparator.comparing(ExtraTrip::plannedProfit, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(e -> e.point().getDate())
                .thenComparing(e -> e.series().getRouteLabel(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(e -> e.series().getDepartureHour())
                .thenComparingInt(ExtraTrip::index));

        // Ghế khung đã có trước chuyến thêm đang xét: các chuyến đã có (hay xe chuẩn giả định) cộng các
        // chuyến thêm trước nó.
        Map<String, Integer> seatsSoFar = new HashMap<>();
        List<RecommendationCardDto> cards = new ArrayList<>();
        for (ExtraTrip extra : extras) {
            String slot = LoopEconomics.slotKey(extra.route().getId(), extra.point().getDate(),
                    extra.series().getDepartureHour());
            int seatsBefore = seatsSoFar.getOrDefault(slot, extra.inSlot().seats());
            RecommendationCardDto card = buildCard(extra, seatsBefore, nbSize, availability, economics, now);
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
                cost.getDriverWagePerHour(),
                alreadyInSlots);
    }

    /**
     * Dựng thẻ cho một chuyến thêm và lượt đi kèm của nó.
     *
     * Chuyến ứng viên là một Trip TẠM (không lưu), đúng khuôn createExtraTrip():
     * route + giờ khởi hành + giờ đến suy từ estimatedDuration. Nó chạy qua bộ chọn
     * tài nguyên và cổng validation; qua cổng thì được giữ chỗ trong availability, cùng lượt đi kèm.
     *
     * @param seatsBefore ghế khung đã có trước chuyến này: các chuyến đã có và các chuyến thêm trước nó
     */
    private RecommendationCardDto buildCard(ExtraTrip extra, int seatsBefore, double nbSize,
            AvailabilityContext availability, LoopEconomics economics, LocalDateTime now) {

        ForecastSeriesDto series = extra.series();
        ForecastPointDto point = extra.point();
        LocalDateTime departure = point.getDate().atTime(series.getDepartureHour(), 0);
        Trip candidate = candidate(extra.route(), departure);

        RecommendationCardDto card = baseCard(extra);
        card.setTicketPrice(extra.price());
        card.setTripCost(extra.cost());

        // ── Bước: chọn xe, rồi tài xế, ở chính bến đi ─────────────────────────
        Bus bus = tripService.selectBestAvailableBus(candidate, departure, candidate.getArrivalTimeExpected(),
                availability);
        Driver driver = bus != null ? driverFor(candidate, availability) : null;

        // ── Không có xe ở bến đi: thử đưa một xe từ đầu kia tới trước (BRING) ──
        Assigned bring = null;
        if (bus == null && extra.reverse() != null) {
            bring = bringBefore(extra, candidate, availability, now);
            if (bring != null) {
                bus = tripService.selectBestAvailableBus(candidate, departure, candidate.getArrivalTimeExpected(),
                        availability);
                driver = driverFor(candidate, availability);
            }
        }

        int capacity = 0;
        if (bus != null) {
            card.setBusPlate(bus.getLicensePlate());
            capacity = capacityOf(bus);
            card.setBusCapacity(capacity > 0 ? capacity : null);
            if (driver != null) {
                card.setDriverName(driverName(driver));
            }
        }

        // ── Ước tính khách và tiền của riêng chuyến thêm (G6-1) ─────────────────
        // Chuyến thêm chở phần khách tràn rơi vào ghế của nó: ghế của xe được chọn, hoặc
        // của xe chuẩn khi chưa chọn được xe. Lợi nhuận có thể ÂM — hiển thị trung thực.
        int ownSeats = capacity > 0 ? capacity : series.getStandardSeats();
        double passengers = ForecastService.extraTripPassengers(point.getPredictedDemand(), seatsBefore, ownSeats,
                nbSize);
        card.setExpectedPassengers(passengers);
        card.setTripRevenue(LoopEconomics.revenue(extra.price(), passengers));

        if (bus == null) {
            setLoop(card, extra.plannedLeg(), null);
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Không có xe rảnh, đang ở bến xuất phát và chưa tới ngưỡng bảo dưỡng cho khung giờ này"
                    + (extra.reverse() != null
                            ? ", và trong " + LoopEconomics.COMPANION_SEARCH_DAYS + " ngày trước không đưa được xe"
                                    + " nào tới kịp cùng một tài xế lái được chuyến thêm"
                            : "")
                    + " — số khách tính với xe chuẩn " + series.getStandardSeats() + " chỗ, chiều đi kèm theo kế hoạch");
            return card;
        }
        if (driver == null) {
            // Chỉ tới được đây khi xe đứng sẵn ở bến đi: lượt đưa xe tới chỉ được giữ khi đã có tài xế.
            setLoop(card, extra.plannedLeg(), null);
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote("Có xe " + bus.getLicensePlate()
                    + " nhưng không có tài xế nào hợp lệ, rảnh và đang ở bến xuất phát cho khung giờ này");
            return card;
        }

        // ── Cổng Business Rule Validation (Phase 3) — xác nhận lần cuối ──────────
        String refused = gate(candidate, bus, driver);
        if (refused != null) {
            if (bring != null) {
                availability.release(bring.trip());
            }
            setLoop(card, extra.plannedLeg(), null);
            card.setStatus(RecommendationStatus.STALE);
            card.setNote(refused);
            return card;
        }
        // ── Giữ chỗ tạm (G6-3): như một chuyến tăng cường vừa tạo, đang chờ duyệt ──
        candidate.setStatus(TripStatus.PENDING_APPROVAL);
        availability.reserve(candidate);
        card.setStatus(RecommendationStatus.RECOMMENDED);

        // ── Lượt đi kèm thật của vòng (CT7) ─────────────────────────────────────
        if (bring != null) {
            setLoop(card, economics.companion(LoopLegKind.BRING, extra.reverse(),
                    bring.trip().getDepartureTime(), capacity), bring);
            card.setNote("Đủ điều kiện — bến đi không có xe rảnh, nên xe " + bus.getLicensePlate()
                    + " được đưa tới trước bằng khung " + label(extra.reverse()) + " "
                    + bring.trip().getDepartureTime().format(LEG_TIME)
                    + "; xe và tài xế được giữ cho cả vòng, các thẻ khác không dùng lại");
            return card;
        }
        if (extra.reverse() == null) {
            setLoop(card, economics.assumedReturn(extra.route()), null);
            card.setNote("Đủ điều kiện — tuyến chưa có đúng một tuyến chiều ngược, nên chiều về tính như một"
                    + " lượt rỗng cùng quãng đường; xe và tài xế này được giữ cho chuyến này");
            return card;
        }
        if (endsAtHome(bus, extra.route())) {
            setLoop(card, LoopEconomics.none(), null);
            card.setNote("Đủ điều kiện — xe " + bus.getLicensePlate()
                    + " đang ở bến xa, chuyến thêm đưa xe về bến nhà nên không cần lượt đi kèm");
            return card;
        }
        Assigned back = returnAfter(extra, candidate, availability);
        if (back == null) {
            setLoop(card, economics.assumedReturn(extra.reverse()), null);
            card.setNote("Đủ điều kiện cho chuyến thêm, nhưng trong " + LoopEconomics.COMPANION_SEARCH_DAYS
                    + " ngày không xếp được lượt về (xe hay tài xế) — chiều về tính như một lượt rỗng");
            return card;
        }
        setLoop(card, economics.companion(LoopLegKind.RETURN, extra.reverse(), back.trip().getDepartureTime(),
                capacityOf(back.bus())), back);
        card.setNote("Đủ điều kiện — xe về ngay bằng khung " + label(extra.reverse()) + " "
                + back.trip().getDepartureTime().format(LEG_TIME)
                + "; xe và tài xế được giữ cho cả vòng, các thẻ khác không dùng lại");
        return card;
    }

    /**
     * "Đưa xe tới trước" (BRING): thử các khung chiều ngược, muộn trước. Mỗi khung chọn xe + tài xế ở
     * đầu kia bằng đúng bộ chọn, qua cổng, rồi giữ chỗ THỬ — để luật vị trí thấy xe đã tới bến đi của
     * chuyến thêm — và chỉ giữ hẳn khi chính chuyến thêm cũng chọn được xe và tài xế. Trả null nếu
     * không khung nào được; khi đó không còn gì bị giữ chỗ.
     */
    private Assigned bringBefore(ExtraTrip extra, Trip candidate, AvailabilityContext availability,
            LocalDateTime now) {
        Route reverse = extra.reverse();
        for (LocalDateTime time : LoopEconomics.bringTimes(candidate.getDepartureTime(),
                LoopEconomics.durationMinutes(reverse), now)) {
            Assigned leg = assignLeg(reverse, time, availability);
            if (leg == null) {
                continue;
            }
            Bus bus = tripService.selectBestAvailableBus(candidate, candidate.getDepartureTime(),
                    candidate.getArrivalTimeExpected(), availability);
            if (bus != null && driverFor(candidate, availability) != null) {
                return leg;
            }
            availability.release(leg.trip());
        }
        return null;
    }

    /** "Về ngay" (RETURN): khung chiều ngược sớm nhất có xe + tài xế ở bến đến, qua cổng; giữ chỗ luôn. */
    private Assigned returnAfter(ExtraTrip extra, Trip candidate, AvailabilityContext availability) {
        for (LocalDateTime time : LoopEconomics.returnTimes(candidate.getArrivalTimeExpected())) {
            Assigned leg = assignLeg(extra.reverse(), time, availability);
            if (leg != null) {
                return leg;
            }
        }
        return null;
    }

    /**
     * Một lượt đi kèm trên tuyến route lúc departure: chọn xe, rồi tài xế (đúng bộ chọn, có luật vị trí),
     * qua cổng, rồi giữ chỗ. Trả null nếu thiếu gì — khi đó không có gì bị giữ chỗ.
     */
    private Assigned assignLeg(Route route, LocalDateTime departure, AvailabilityContext availability) {
        Trip leg = candidate(route, departure);
        Bus bus = tripService.selectBestAvailableBus(leg, departure, leg.getArrivalTimeExpected(), availability);
        if (bus == null) {
            return null;
        }
        Driver driver = driverFor(leg, availability);
        if (driver == null || gate(leg, bus, driver) != null) {
            return null;
        }
        leg.setStatus(TripStatus.PENDING_APPROVAL);
        availability.reserve(leg);
        return new Assigned(leg, bus, driver);
    }

    /** Tài xế chính cho một lượt, đúng bộ chọn của TripService (luật 8 giờ, nghỉ 30 phút, vị trí). */
    private Driver driverFor(Trip trip, AvailabilityContext availability) {
        double hours = LoopEconomics.durationMinutes(trip.getRoute()) / 60.0;
        return tripService.selectBestAvailableDriver(trip.getDepartureTime(), trip.getArrivalTimeExpected(), hours,
                LoopEconomics.requiredDrivers(hours), List.of(), false, availability, trip.getRoute());
    }

    /**
     * Cổng Business Rule Validation (Phase 3) cho một lượt: gán xe, tài xế, số ghế theo xe, rồi chạy hai
     * bản dry-run. Trả null khi qua; ngược lại là lý do của validator.
     */
    private String gate(Trip trip, Bus bus, Driver driver) {
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setTotalSeats(capacityOf(bus));
        ValidationResult busGate = tripService.validateBusForTripDryRun(bus, trip, null);
        if (!busGate.isValid()) {
            return busGate.getFailureReason();
        }
        ValidationResult staffGate = tripService.validateStaffForTripDryRun(trip, null);
        return staffGate.isValid() ? null : staffGate.getFailureReason();
    }

    /** Chuyến tạm trên tuyến route lúc departure, giờ đến suy từ estimatedDuration — khuôn createExtraTrip(). */
    private static Trip candidate(Route route, LocalDateTime departure) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusMinutes(LoopEconomics.durationMinutes(route)));
        return trip;
    }

    /**
     * Ghi lượt đi kèm và tiền cả vòng vào thẻ. assigned != null khi lượt đã chọn được xe + tài xế và được
     * giữ chỗ; null khi lượt chỉ là kế hoạch, hay không có lượt cụ thể.
     */
    private void setLoop(RecommendationCardDto card, LoopEconomics.Leg leg, Assigned assigned) {
        RecommendationCardDto.CompanionLeg companion = new RecommendationCardDto.CompanionLeg();
        companion.setKind(leg.kind());
        companion.setRouteId(leg.route() != null ? leg.route().getId() : null);
        companion.setRouteLabel(leg.route() != null ? label(leg.route()) : null);
        companion.setDeparture(leg.departure());
        companion.setReserved(assigned != null);
        if (assigned != null) {
            companion.setBusPlate(assigned.bus().getLicensePlate());
            companion.setDriverName(driverName(assigned.driver()));
        }
        companion.setPassengers(leg.passengers());
        companion.setRevenue(leg.revenue());
        companion.setCost(leg.cost());
        card.setCompanion(companion);

        card.setEstimatedRevenue(LoopEconomics.plus(card.getTripRevenue(), leg.revenue()));
        card.setEstimatedCost(LoopEconomics.plus(card.getTripCost(), leg.cost()));
        card.setEstimatedProfit(profitOf(card.getEstimatedRevenue(), card.getEstimatedCost()));
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
        card.setExistingTrips(extra.inSlot().trips());
        card.setExistingSeats(extra.inSlot().seats());
        card.setExistingAssumed(extra.slotAssumed());
        return card;
    }

    /** Ghế mà chuyến thêm của thẻ góp vào khung — cùng số ghế buildCard() đã dùng để tính khách. */
    private int tripSeats(RecommendationCardDto card, ExtraTrip extra) {
        return card.getBusCapacity() != null ? card.getBusCapacity() : extra.series().getStandardSeats();
    }

    private BigDecimal profitOf(BigDecimal revenue, BigDecimal cost) {
        return revenue != null && cost != null ? revenue.subtract(cost) : null;
    }

    /**
     * Giá vé lịch sử theo (tuyến → giờ khởi hành → giá của chuyến COMPLETED gần
     * nhất). Không nạp entity Trip: một ứng viên chỉ cần con số giá. Phép gom nằm ở
     * LoopEconomics.latestPrices, dùng chung với What-if. Lịch thường lệ (CT6) dùng chung hàm này.
     */
    Map<Long, Map<Integer, BigDecimal>> loadLatestPrices() {
        return LoopEconomics.latestPrices(tripRepository.findPriceHistoryByStatus(TripStatus.COMPLETED));
    }

    /**
     * Tuyến chiều ngược của từng tuyến — chỉ khi có ĐÚNG MỘT, như lịch thường lệ (CM-6,
     * RouteService.reverseRoutesOf). Tuyến không có, hay có hai tuyến chiều ngược thì không có cặp.
     */
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

    /** Các bến nhà của đội xe (QĐ-23): nơi lượt đi kèm theo kế hoạch giả định xe dự phòng đang đứng. */
    private Set<Long> homeStations() {
        return busRepository.findAll().stream()
                .map(Bus::getHomeStation)
                .filter(Objects::nonNull)
                .map(Station::getId)
                .collect(Collectors.toSet());
    }

    /** Chuyến trên tuyến route đưa xe về đúng bến nhà của nó (xe chưa có bến nhà thì không). */
    private static boolean endsAtHome(Bus bus, Route route) {
        Station home = bus.getHomeStation();
        Station end = route.getDestinationStation();
        return home != null && end != null && Objects.equals(home.getId(), end.getId());
    }

    private int capacityOf(Bus bus) {
        return bus.getBusType() != null ? bus.getBusType().getCapacity() : 0;
    }

    private static String label(Route route) {
        return route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay();
    }

    private String driverName(Driver driver) {
        return driver.getUser() != null ? driver.getUser().getFullName() : "Tài xế #" + driver.getUserId();
    }
}
