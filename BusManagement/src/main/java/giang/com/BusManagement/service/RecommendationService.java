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
 *       Vòng có lượt đi kèm (chuyến thêm + lượt về, hay lượt đưa xe tới + chuyến thêm) được chọn một lần
 *       bằng {@code TripService.selectLoop} — cùng các luật đó, áp cho cả vòng (CT7c).</li>
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
 * chiều ngược. Từ CT7c (CM-24, PA-A) cả vòng được chọn MỘT LẦN, cho MỘT xe và MỘT tài xế
 * ({@link TripService#selectLoop}) — đúng luật H1 của lịch sử (QĐ-21 (d)) và "kíp ở lại cùng xe" (QĐ-26):
 * <ul>
 *   <li>chuyến thêm rời bến nhà → "về ngay" (RETURN): khung chiều ngược sớm nhất mà cùng xe và cùng tài
 *       xế chạy được cả vòng;</li>
 *   <li>chuyến thêm về bến nhà → xe đang đứng ở bến xa thì không cần lượt đi kèm (NONE); không có thì
 *       "đưa xe tới trước" (BRING): khung chiều đi muộn nhất còn kịp, cùng xe và cùng tài xế;</li>
 *   <li>tuyến không có đúng một tuyến chiều ngược → tính như một lượt về rỗng (ASSUMED_RETURN).</li>
 * </ul>
 * Không xếp được cả vòng thì thẻ "thiếu tài nguyên" — như lịch sử không chạy chuyến thêm khi không có lượt
 * về. Lãi của thẻ = tiền vé chuyến thêm + tiền vé lượt đi kèm − chi phí hai lượt.
 *
 * ====================================================================
 * VÒNG GIỮ XE (route pairs CT9; QĐ-25, QĐ-26, CM-29…CM-32)
 * ====================================================================
 * Một chuyến thêm ĐI (rời bến nhà) và một chuyến thêm VỀ sau đó của cùng cặp có thể chạy bằng MỘT xe và MỘT
 * kíp: xe chờ ở bến xa giữa hai lượt (kíp ở lại cùng xe), thay cho hai vòng riêng mà mỗi vòng có một lượt đi
 * kèm gần như rỗng. Phép ghép ở mức kế hoạch là của {@link LoopEconomics#pairHolds} (What-if dùng chung): chỉ
 * ghép khi lãi cả vòng giữ − tiền chờ lớn hơn lãi của hai vòng riêng. Một vòng giữ là MỘT thẻ (gánh hai chuyến
 * thêm), được xét theo lãi của cả vòng; không xếp được bằng một xe + một tài xế thì hai chuyến thêm quay về
 * hai thẻ riêng như trước.
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
            BigDecimal plannedProfit, BigDecimal plannedTripRevenue) {

        LocalDateTime departure() {
            return point.getDate().atTime(series.getDepartureHour(), 0);
        }

        String slot() {
            return LoopEconomics.slotKey(route.getId(), point.getDate(), series.getDepartureHour());
        }
    }

    /**
     * Route pairs CT9: một đơn vị xét theo lãi — một chuyến thêm (in null), hay một vòng giữ xe ghép chuyến thêm
     * đi với chuyến thêm về (hold khác null, CM-29). Lãi kế hoạch của vòng giữ là lãi cả vòng giữ.
     */
    private record Unit(ExtraTrip out, ExtraTrip in, LoopEconomics.Hold hold) {
        BigDecimal plannedProfit() {
            return hold != null ? hold.profit() : out.plannedProfit();
        }
    }

    /**
     * Thứ tự xét: lãi cả vòng kỳ vọng giảm dần, rồi ngày, tuyến, giờ, thứ tự chuyến thêm — như trước CT9 khi
     * không có vòng giữ nào.
     */
    private static final Comparator<Unit> UNIT_ORDER = Comparator
            .comparing(Unit::plannedProfit, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(u -> u.out().point().getDate())
            .thenComparing(u -> u.out().series().getRouteLabel(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(u -> u.out().series().getDepartureHour())
            .thenComparingInt(u -> u.out().index());

    /**
     * Một vòng của thẻ (CT7c, CM-24 PA-A): loại lượt đi kèm, các lượt theo giờ (chuyến thêm và lượt đi kèm,
     * nếu có), và MỘT xe + MỘT tài xế cho cả vòng. bus null = không có xe; driver null = có xe mà không có
     * tài xế.
     */
    record Loop(LoopLegKind kind, List<Trip> legs, Trip companion, Bus bus, Driver driver) {
        boolean isComplete() {
            return bus != null && driver != null;
        }
    }

    @Transactional(readOnly = true)
    Plan buildPlan() {
        return buildPlan(Set.of());
    }

    /**
     * @param unpairable khung không được ghép thành vòng giữ xe (CT9) — job đêm truyền các khung mà Admin đã từ
     *                   chối vòng dự báo, để chuyến thêm còn lại của đôi vẫn được xét như một thẻ riêng; trang
     *                   truyền tập rỗng.
     */
    @Transactional(readOnly = true)
    Plan buildPlan(Set<String> unpairable) {
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
                nbSize, 1.0, cost.getFuelCostPerKm(), cost.getDriverWagePerHour(), cost.getWaitingWagePerDay(),
                cost.getBusParkingPerDay());

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
                    BigDecimal plannedTripRevenue = LoopEconomics.revenue(slotPrice, planned);
                    BigDecimal plannedProfit = profitOf(LoopEconomics.plus(plannedTripRevenue, plannedLeg.revenue()),
                            LoopEconomics.plus(tripCost, plannedLeg.cost()));
                    extras.add(new ExtraTrip(series, point, route, reverse, inSlot.trips() - 1 + k,
                            point.getTripsNeeded() - 1, inSlot, assumed, durationMinutes, requiredDrivers,
                            slotPrice, tripCost, plannedLeg, plannedProfit, plannedTripRevenue));
                }
            }
        }

        // ── Vòng giữ xe (CT9, CM-29…CM-32): ghép chuyến thêm đi với chuyến thêm về, ở mức kế hoạch ──
        // id = vị trí trong danh sách theo thứ tự dựng (chuỗi → ngày → k), nên chuyến thêm thứ nhất của một khung
        // có id nhỏ hơn chuyến thứ hai — phép ghép dùng điều đó để hoà, và What-if dựng đúng cùng danh sách.
        List<LoopEconomics.PlannedExtra> plannedExtras = new ArrayList<>();
        for (int id = 0; id < extras.size(); id++) {
            ExtraTrip extra = extras.get(id);
            plannedExtras.add(new LoopEconomics.PlannedExtra(id, extra.route(), extra.reverse(), extra.departure(),
                    extra.departure().plusMinutes(extra.durationMinutes()), extra.plannedLeg().kind(),
                    extra.plannedTripRevenue(), extra.cost(), extra.plannedProfit()));
        }
        List<Unit> work = new ArrayList<>();
        Set<Integer> paired = new java.util.HashSet<>();
        for (LoopEconomics.Hold hold : economics.pairHolds(plannedExtras, unpairable)) {
            work.add(new Unit(extras.get(hold.outId()), extras.get(hold.inId()), hold));
            paired.add(hold.outId());
            paired.add(hold.inId());
        }
        for (int id = 0; id < extras.size(); id++) {
            if (!paired.contains(id)) {
                work.add(new Unit(extras.get(id), null, null));
            }
        }

        // Xét theo lãi cả vòng kỳ vọng giảm dần: vòng đáng chạy nhất được giữ xe + tài xế trước.
        // Trong một khung, các chuyến thêm có cùng lượt đi kèm theo kế hoạch, và chuyến thêm sau chở
        // ít khách tràn hơn chuyến trước, nên lãi của nó không cao hơn; bằng nhau thì k nhỏ trước. Từ CT9,
        // thứ tự chuyến thêm trong khung (và ghế đã có trước nó) đếm theo thứ tự xét thật, vì một vòng giữ có
        // thể được xét trước một chuyến thêm khác của cùng khung.
        work.sort(UNIT_ORDER);

        // Ghế khung đã có trước chuyến thêm đang xét: các chuyến đã có (hay xe chuẩn giả định) cộng các
        // chuyến thêm đã xét trước nó; và số chuyến thêm đã xét của khung, cho thứ tự "chuyến thêm k".
        Map<String, Integer> seatsSoFar = new HashMap<>();
        Map<String, Integer> extrasSoFar = new HashMap<>();
        List<RecommendationCardDto> cards = new ArrayList<>();
        Map<RecommendationCardDto, Loop> loops = new java.util.IdentityHashMap<>(); // vòng của thẻ đủ điều kiện (CT8b)
        for (int u = 0; u < work.size(); u++) {
            Unit unit = work.get(u);
            if (unit.in() != null) {
                RecommendationCardDto held = buildHeldCard(unit, seatsSoFar, extrasSoFar, nbSize, availability,
                        loops);
                if (held != null) {
                    cards.add(held);
                } else {
                    // Không xếp được cả vòng giữ bằng một xe + một tài xế (hay cổng trượt): hai chuyến thêm quay
                    // về hai thẻ riêng như trước CT9, xếp lại vào phần còn lại theo lãi riêng của chúng.
                    List<Unit> rest = new ArrayList<>(work.subList(u + 1, work.size()));
                    rest.add(new Unit(unit.out(), null, null));
                    rest.add(new Unit(unit.in(), null, null));
                    rest.sort(UNIT_ORDER);
                    work = new ArrayList<>(work.subList(0, u + 1));
                    work.addAll(rest);
                }
                continue;
            }
            ExtraTrip extra = unit.out();
            int seatsBefore = seatsSoFar.getOrDefault(extra.slot(), extra.inSlot().seats());
            RecommendationCardDto card = buildCard(extra, nextIndex(extrasSoFar, extra), seatsBefore, nbSize,
                    availability, economics, now, loops);
            seatsSoFar.put(extra.slot(), seatsBefore + tripSeats(card, extra));
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

        int held = (int) cards.stream()
                .filter(c -> c.getCompanion() != null && c.getCompanion().getKind() == LoopLegKind.HOLD).count();

        return new Plan(new RecommendationViewDto(
                cards,
                forecast.getHistoryEnd(),
                forecast.getHistoryStaleDays(),
                forecast.getForecastFrom(),
                forecast.getHorizonDays(),
                hotSlots,
                extras.size(),
                recommended,
                noResource,
                profitable,
                loss,
                cost.getFuelCostPerKm(),
                cost.getDriverWagePerHour(),
                alreadyInSlots,
                held,
                cost.getWaitingWagePerDay(),
                cost.getBusParkingPerDay()), loops);
    }

    /** Thứ tự "chuyến thêm k" của chuyến này trong khung, theo thứ tự xét thật (tính cả chuyến thêm đã có). */
    private static int nextIndex(Map<String, Integer> extrasSoFar, ExtraTrip extra) {
        return extra.inSlot().trips() - 1 + extrasSoFar.merge(extra.slot(), 1, Integer::sum);
    }

    /**
     * Route pairs CT9 (CM-29) — thẻ của một vòng giữ xe: MỘT xe và MỘT tài xế cho chuyến thêm đi, thời gian chờ
     * ở bến xa và chuyến thêm về, chọn một lần bằng {@link TripService#selectLoop} (bận phủ cả khối, nên xe và
     * kíp được giữ suốt lúc chờ), mỗi lượt qua cổng, rồi giữ chỗ cả hai. Trả null — không giữ gì — khi không xếp
     * được cả vòng hay một cổng trượt: nơi gọi xét lại hai chuyến thêm như hai thẻ riêng.
     */
    private RecommendationCardDto buildHeldCard(Unit unit, Map<String, Integer> seatsSoFar,
            Map<String, Integer> extrasSoFar, double nbSize, AvailabilityContext availability,
            Map<RecommendationCardDto, Loop> loops) {
        ExtraTrip out = unit.out();
        ExtraTrip in = unit.in();
        LoopEconomics.Hold hold = unit.hold();
        Trip outTrip = candidate(out.route(), out.departure());
        Trip inTrip = candidate(in.route(), in.departure());
        List<Trip> legs = List.of(outTrip, inTrip);
        TripService.LoopChoice choice = tripService.selectLoop(legs, availability);
        if (!choice.isComplete()) {
            return null;
        }
        for (Trip leg : legs) {
            if (gate(leg, choice.bus(), choice.driver()) != null) {
                return null;
            }
        }
        for (Trip leg : legs) {
            leg.setStatus(TripStatus.PENDING_APPROVAL);
            availability.reserve(leg);
        }

        // Khách của từng chuyến thêm: khách tràn rơi vào ghế của xe được chọn, sau ghế đã có của khung nó.
        int capacity = capacityOf(choice.bus());
        int outSeats = capacity > 0 ? capacity : out.series().getStandardSeats();
        int inSeats = capacity > 0 ? capacity : in.series().getStandardSeats();
        int outSeatsBefore = seatsSoFar.getOrDefault(out.slot(), out.inSlot().seats());
        int inSeatsBefore = seatsSoFar.getOrDefault(in.slot(), in.inSlot().seats());
        seatsSoFar.put(out.slot(), outSeatsBefore + outSeats);
        seatsSoFar.put(in.slot(), inSeatsBefore + inSeats);
        double outPassengers = ForecastService.extraTripPassengers(out.point().getPredictedDemand(), outSeatsBefore,
                outSeats, nbSize);
        double inPassengers = ForecastService.extraTripPassengers(in.point().getPredictedDemand(), inSeatsBefore,
                inSeats, nbSize);

        RecommendationCardDto card = baseCard(out, nextIndex(extrasSoFar, out));
        card.setTicketPrice(out.price());
        card.setTripCost(out.cost());
        card.setBusPlate(choice.bus().getLicensePlate());
        card.setBusCapacity(capacity > 0 ? capacity : null);
        card.setDriverName(driverName(choice.driver()));
        card.setExpectedPassengers(outPassengers);
        card.setTripRevenue(LoopEconomics.revenue(out.price(), outPassengers));

        RecommendationCardDto.CompanionLeg leg = new RecommendationCardDto.CompanionLeg();
        leg.setKind(LoopLegKind.HOLD);
        leg.setRouteId(in.route().getId());
        leg.setRouteLabel(label(in.route()));
        leg.setDeparture(inTrip.getDepartureTime());
        leg.setReserved(true);
        leg.setBusPlate(choice.bus().getLicensePlate());
        leg.setDriverName(driverName(choice.driver()));
        leg.setPassengers(inPassengers);
        leg.setRevenue(LoopEconomics.revenue(in.price(), inPassengers));
        leg.setCost(in.cost());
        leg.setWaitStation(in.route().getDeparturePointDisplay());
        leg.setWaitFrom(hold.returnFrom());
        leg.setWaitHours(hold.waitHours());
        leg.setWaitCost(hold.waitCost());
        leg.setServedDemand(in.point().getPredictedDemand());
        leg.setServedStandardSeats(in.series().getStandardSeats());
        leg.setServedExtraIndex(nextIndex(extrasSoFar, in));
        leg.setServedExtraCount(in.count());
        leg.setSeparateProfit(hold.separate());
        card.setCompanion(leg);
        card.setEstimatedRevenue(LoopEconomics.plus(card.getTripRevenue(), leg.getRevenue()));
        card.setEstimatedCost(LoopEconomics.plus(LoopEconomics.plus(card.getTripCost(), leg.getCost()),
                hold.waitCost()));
        card.setEstimatedProfit(profitOf(card.getEstimatedRevenue(), card.getEstimatedCost()));
        card.setStatus(RecommendationStatus.RECOMMENDED);
        card.setNote("Đủ điều kiện — vòng giữ xe: xe " + choice.bus().getLicensePlate() + " và tài xế "
                + driverName(choice.driver()) + " chạy chuyến thêm này, "
                + (hold.waitHours() > 0
                        ? "chờ ở " + leg.getWaitStation() + " thêm " + hours(hold.waitHours()) + " giờ (từ "
                                + hold.returnFrom().format(LEG_TIME) + ", lúc về ngay sớm nhất; tiền chờ "
                                + money(hold.waitCost()) + ")"
                        : "không phải chờ thêm (chuyến thêm về đi đúng lúc về ngay sớm nhất)")
                + ", rồi chạy chuyến thêm " + label(in.route()) + " " + inTrip.getDepartureTime().format(LEG_TIME)
                + " về bến nhà — thay cho hai vòng riêng "
                + (hold.separate().signum() > 0 ? "có lãi theo kế hoạch " + money(hold.separate())
                        : "đều lỗ theo kế hoạch (không vòng nào đáng chạy riêng)")
                + "; cả vòng được giữ cho đúng xe và tài xế này, các thẻ khác không dùng lại");
        loops.put(card, new Loop(LoopLegKind.HOLD, legs, inTrip, choice.bus(), choice.driver()));
        return card;
    }

    private static String hours(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(java.util.Locale.US, "%.1f", value).replace('.', ',');
    }

    private static String money(BigDecimal value) {
        return String.format(java.util.Locale.US, "%,.0f", value).replace(',', '.') + "đ";
    }

    /**
     * Dựng thẻ cho một chuyến thêm và lượt đi kèm của nó.
     *
     * Chuyến ứng viên là một Trip TẠM (không lưu), đúng khuôn createExtraTrip():
     * route + giờ khởi hành + giờ đến suy từ estimatedDuration. Cả vòng của nó — chuyến thêm và lượt đi
     * kèm — được chọn MỘT LẦN cho một xe và một tài xế (CT7c, CM-24 PA-A), mọi lượt qua cổng validation,
     * rồi cả vòng được giữ chỗ trong availability.
     *
     * @param index       thứ tự "chuyến thêm k" của chuyến này trong khung, theo thứ tự xét
     * @param seatsBefore ghế khung đã có trước chuyến này: các chuyến đã có và các chuyến thêm trước nó
     */
    private RecommendationCardDto buildCard(ExtraTrip extra, int index, int seatsBefore, double nbSize,
            AvailabilityContext availability, LoopEconomics economics, LocalDateTime now, Map<RecommendationCardDto, Loop> loops) {

        ForecastSeriesDto series = extra.series();
        ForecastPointDto point = extra.point();
        LocalDateTime departure = point.getDate().atTime(series.getDepartureHour(), 0);
        Trip candidate = candidate(extra.route(), departure);

        RecommendationCardDto card = baseCard(extra, index);
        card.setTicketPrice(extra.price());
        card.setTripCost(extra.cost());

        // ── Bước: chọn cả vòng — một xe, một tài xế (CT7c) ─────────────────────
        Loop loop = chooseLoop(extra, candidate, availability, now);

        int capacity = 0;
        if (loop.bus() != null) {
            card.setBusPlate(loop.bus().getLicensePlate());
            capacity = capacityOf(loop.bus());
            card.setBusCapacity(capacity > 0 ? capacity : null);
            if (loop.driver() != null) {
                card.setDriverName(driverName(loop.driver()));
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

        if (!loop.isComplete()) {
            setLoop(card, extra.plannedLeg(), null, null);
            card.setStatus(RecommendationStatus.NO_RESOURCE);
            card.setNote(noResourceNote(extra, loop, series.getStandardSeats()));
            return card;
        }

        // ── Cổng Business Rule Validation (Phase 3) — xác nhận lần cuối, từng lượt của vòng ──
        for (Trip leg : loop.legs()) {
            String refused = gate(leg, loop.bus(), loop.driver());
            if (refused != null) {
                setLoop(card, extra.plannedLeg(), null, null);
                card.setStatus(RecommendationStatus.STALE);
                card.setNote(leg == candidate ? refused : "Lượt đi kèm " + label(leg.getRoute()) + " "
                        + leg.getDepartureTime().format(LEG_TIME) + ": " + refused);
                return card;
            }
        }
        // ── Giữ chỗ tạm cả vòng (G6-3): như các chuyến tăng cường vừa tạo, đang chờ duyệt ──
        for (Trip leg : loop.legs()) {
            leg.setStatus(TripStatus.PENDING_APPROVAL);
            availability.reserve(leg);
        }
        card.setStatus(RecommendationStatus.RECOMMENDED);
        loops.put(card, loop);

        // ── Lượt đi kèm và tiền cả vòng ─────────────────────────────────────────
        String crew = "xe " + loop.bus().getLicensePlate() + " và tài xế " + driverName(loop.driver());
        switch (loop.kind()) {
            case RETURN -> {
                setLoop(card, economics.companion(LoopLegKind.RETURN, extra.reverse(),
                        loop.companion().getDepartureTime(), capacity), loop.bus(), loop.driver());
                card.setNote("Đủ điều kiện — " + crew + " chạy cả vòng: chuyến thêm rồi về ngay bằng khung "
                        + label(extra.reverse()) + " " + loop.companion().getDepartureTime().format(LEG_TIME)
                        + "; cả vòng được giữ cho đúng xe và tài xế này, các thẻ khác không dùng lại");
            }
            case BRING -> {
                setLoop(card, economics.companion(LoopLegKind.BRING, extra.reverse(),
                        loop.companion().getDepartureTime(), capacity), loop.bus(), loop.driver());
                card.setNote("Đủ điều kiện — bến đi không có xe rảnh, nên " + crew + " lên trước bằng khung "
                        + label(extra.reverse()) + " " + loop.companion().getDepartureTime().format(LEG_TIME)
                        + " rồi chạy chuyến thêm về; cả vòng được giữ cho đúng xe và tài xế này, các thẻ khác không"
                        + " dùng lại");
            }
            case NONE -> {
                setLoop(card, LoopEconomics.none(), null, null);
                card.setNote("Đủ điều kiện — xe " + loop.bus().getLicensePlate()
                        + " đang ở bến xa, chuyến thêm đưa xe về bến nhà nên không cần lượt đi kèm");
            }
            default -> {
                setLoop(card, economics.assumedReturn(extra.route()), null, null);
                card.setNote("Đủ điều kiện — tuyến chưa có đúng một tuyến chiều ngược, nên chiều về tính như một"
                        + " lượt rỗng cùng quãng đường; xe và tài xế này được giữ cho chuyến này");
            }
        }
        return card;
    }

    /**
     * Vòng của một chuyến thêm (CT7c, CM-24 PA-A). Một vòng là một khối bến nhà → bến nhà, chọn MỘT LẦN cho
     * một xe và một tài xế ({@link TripService#selectLoop}):
     * <ul>
     *   <li>chuyến thêm rời bến nhà → "về ngay" (RETURN): khung chiều ngược sớm nhất mà cùng xe và cùng tài
     *       xế chạy được cả vòng — đúng luật H1 của lịch sử (QĐ-21 (d));</li>
     *   <li>chuyến thêm về bến nhà → xe đang đứng ở bến xa thì chỉ một lượt (NONE); không có thì "đưa xe
     *       tới trước" (BRING): khung chiều đi muộn nhất còn kịp, cùng xe và cùng tài xế;</li>
     *   <li>tuyến không có đúng một tuyến chiều ngược → chỉ chọn cho chuyến thêm, chiều về tính như một
     *       lượt rỗng (ASSUMED_RETURN).</li>
     * </ul>
     * Không xếp được cả vòng thì trả vòng thiếu (bus hay driver null) — thẻ "thiếu tài nguyên", như luật
     * H1 không chạy chuyến thêm khi không có lượt về. Không có gì bị giữ chỗ ở đây.
     */
    private Loop chooseLoop(ExtraTrip extra, Trip candidate, AvailabilityContext availability, LocalDateTime now) {
        if (extra.reverse() == null) {
            return single(LoopLegKind.ASSUMED_RETURN, candidate, availability);
        }
        if (extra.plannedLeg().kind() == LoopLegKind.RETURN) {
            return firstLoop(LoopLegKind.RETURN, candidate, extra.reverse(),
                    LoopEconomics.returnTimes(candidate.getArrivalTimeExpected()), availability);
        }
        Loop home = single(LoopLegKind.NONE, candidate, availability);
        if (home.isComplete() && endsAtHome(home.bus(), extra.route())) {
            return home;
        }
        Loop bring = firstLoop(LoopLegKind.BRING, candidate, extra.reverse(),
                LoopEconomics.bringTimes(candidate.getDepartureTime(), LoopEconomics.durationMinutes(extra.reverse()),
                        now), availability);
        return bring.isComplete() || bring.bus() != null ? bring : home;
    }

    /**
     * Vòng có lượt đi kèm cụ thể: thử các giờ theo thứ tự (về ngay: sớm trước; đưa xe tới: muộn trước), giờ
     * đầu tiên mà cả vòng có xe + tài xế thì lấy. Không giờ nào được thì trả vòng thiếu, giữ xe của giờ đầu
     * tiên có xe (để ghi vào thẻ).
     */
    private Loop firstLoop(LoopLegKind kind, Trip candidate, Route reverse, List<LocalDateTime> times,
            AvailabilityContext availability) {
        Loop partial = new Loop(kind, List.of(candidate), null, null, null);
        for (LocalDateTime time : times) {
            Trip companion = candidate(reverse, time);
            List<Trip> legs = kind == LoopLegKind.BRING ? List.of(companion, candidate) : List.of(candidate, companion);
            TripService.LoopChoice choice = tripService.selectLoop(legs, availability);
            if (choice.isComplete()) {
                return new Loop(kind, legs, companion, choice.bus(), choice.driver());
            }
            if (choice.bus() != null && partial.bus() == null) {
                partial = new Loop(kind, legs, companion, choice.bus(), null);
            }
        }
        return partial;
    }

    /** Vòng một lượt: chỉ chuyến thêm, chọn bằng đúng bộ chọn một chuyến của TripService. */
    private Loop single(LoopLegKind kind, Trip candidate, AvailabilityContext availability) {
        Bus bus = tripService.selectBestAvailableBus(candidate, candidate.getDepartureTime(),
                candidate.getArrivalTimeExpected(), availability);
        Driver driver = bus != null ? driverFor(candidate, availability) : null;
        return new Loop(kind, List.of(candidate), null, bus, driver);
    }

    /** Lý do thẻ thiếu tài nguyên: thiếu xe hay thiếu tài xế, cho một chuyến hay cho cả vòng. */
    private static String noResourceNote(ExtraTrip extra, Loop loop, int standardSeats) {
        String money = " — số khách tính với xe chuẩn " + standardSeats + " chỗ, chiều đi kèm theo kế hoạch";
        if (extra.reverse() == null) {
            return (loop.bus() == null
                    ? "Không có xe rảnh, đang ở bến xuất phát và chưa tới ngưỡng bảo dưỡng (tính cả km đã xếp) cho khung giờ này"
                    : "Có xe " + loop.bus().getLicensePlate()
                            + " nhưng không có tài xế nào hợp lệ, rảnh và đang ở bến xuất phát cho khung giờ này")
                    + money;
        }
        String what = extra.plannedLeg().kind() == LoopLegKind.RETURN
                ? "chuyến thêm rồi về ngay trong " + LoopEconomics.COMPANION_SEARCH_DAYS + " ngày"
                : "đưa xe tới trong " + LoopEconomics.COMPANION_SEARCH_DAYS + " ngày trước rồi chạy chuyến thêm,"
                        + " và bến đi không có xe nào sẵn để chỉ chạy một lượt về bến nhà";
        return (loop.bus() == null
                ? "Không xếp được cả vòng (" + what + "): không có xe rảnh, đang ở bến nhà và chưa tới ngưỡng bảo"
                        + " dưỡng (tính cả km đã xếp) cho cả vòng"
                : "Không xếp được cả vòng (" + what + "): có xe " + loop.bus().getLicensePlate()
                        + " nhưng không có tài xế nào rảnh, đang đứng đúng bến và còn đủ giờ lái (≤ 8 giờ/ngày) cho"
                        + " mọi lượt của vòng")
                + money;
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
     * Ghi lượt đi kèm và tiền cả vòng vào thẻ. bus/driver khác null khi lượt đã chọn được xe + tài xế và
     * được giữ chỗ; null khi lượt chỉ là kế hoạch, hay không có lượt cụ thể.
     */
    private void setLoop(RecommendationCardDto card, LoopEconomics.Leg leg, Bus bus, Driver driver) {
        RecommendationCardDto.CompanionLeg companion = new RecommendationCardDto.CompanionLeg();
        companion.setKind(leg.kind());
        companion.setRouteId(leg.route() != null ? leg.route().getId() : null);
        companion.setRouteLabel(leg.route() != null ? label(leg.route()) : null);
        companion.setDeparture(leg.departure());
        companion.setReserved(bus != null);
        if (bus != null) {
            companion.setBusPlate(bus.getLicensePlate());
            companion.setDriverName(driverName(driver));
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
    private RecommendationCardDto baseCard(ExtraTrip extra, int index) {
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
        card.setExtraIndex(index);
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

    /** Trang Đề xuất (và các màn đọc nó): chỉ phần hiển thị của kế hoạch — vẫn chỉ đọc, không ghi gì. */
    @Transactional(readOnly = true)
    public RecommendationViewDto buildRecommendations() {
        return buildPlan().view();
    }

    /**
     * Một lần dựng kế hoạch (route pairs CT8b): phần hiển thị, và vòng đã chọn của từng thẻ "Đủ điều kiện" —
     * các lượt là chuyến TẠM (không lưu) đã gán xe, tài xế, số ghế theo xe và qua cổng dry-run, đúng như thẻ
     * ghi. Lớp này vẫn KHÔNG ghi DB: job đêm (ForecastLoopService) là nơi duy nhất lưu một số vòng trong đó
     * thành chuyến chờ duyệt (QĐ-24). Map theo danh tính thẻ.
     */
    record Plan(RecommendationViewDto view, Map<RecommendationCardDto, Loop> loops) {
    }
}
