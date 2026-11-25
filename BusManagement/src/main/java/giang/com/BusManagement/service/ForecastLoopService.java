package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.LoopLegKind;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Route pairs CT8b (QĐ-24; CM-26, CM-27, CM-28) — bước 4 của job đêm: biến các thẻ Đề xuất đáng chạy thành
 * VÒNG CHỜ DUYỆT thật, và rút những vòng chờ duyệt không còn cần.
 *
 * <ul>
 * <li><b>Tạo</b> (QĐ-24 mục 1–4): thẻ "Đủ điều kiện", lãi cả vòng &gt; 0, chuyến thêm còn ≥ 72 giờ trước giờ đi
 * (cùng mốc {@link TripService#MIN_HOURS_BEFORE_DEPARTURE} của job 10 giây) — tức các ngày D+3…D+7 của chân
 * trời dự báo. Mỗi thẻ thành chuyến thêm + lượt đi kèm của nó (về ngay / đưa xe tới), ĐÚNG giờ khung (CM-15),
 * cùng một xe và một tài xế mà thẻ đã chọn và đã qua cổng dry-run, {@code PENDING_APPROVAL}, nối bằng
 * {@link Trip#getForecastLoopId()} (CM-26). Chuyến gốc của mỗi lượt là chuyến chính của khung nó.</li>
 * <li><b>Rút vì hạn duyệt</b> (CM-28 (b)): vòng còn chờ duyệt mà chuyến thêm còn dưới 72 giờ — không kịp bán vé.</li>
 * <li><b>Rút vì dự báo giảm</b> (CM-28 (a), QĐ-24 mục 5): máy tạm coi như các vòng chờ duyệt của chính nó chưa
 * có, dựng lại kế hoạch, rồi giữ ở mỗi khung tối đa số vòng kế hoạch đó còn muốn (vòng cũ nhất trước, không đổi
 * xe), rút phần dư. Vòng đã duyệt (đang mở bán) là chuyến thật, luôn được giữ và được trừ như mọi chuyến khác.
 * "Rút" là xoá mềm bản nháp của chính job ({@link TripService#deleteTrip}), để dự báo tăng lại thì khung được đề
 * xuất lại.</li>
 * <li><b>Admin từ chối</b> (CM-27): các lượt của vòng ở CANCELLED — người đã quyết "không" cho khung đó, nên job
 * không đề xuất lại khung ấy (khác chuyến tăng cường của job 10 giây, vốn được đề xuất lại theo vé thật, TC_AI_009).</li>
 * <li><b>Thiếu dữ liệu</b> (CM-10): chưa có chuỗi dự báo nào, hay lịch sử cũ hơn {@link #MAX_STALE_DAYS} ngày →
 * chỉ rút theo hạn duyệt, không tạo, không rút theo dự báo.</li>
 * <li><b>Vòng giữ xe</b> (route pairs CT9, CM-29): thẻ giữ xe của kế hoạch thành một vòng hai lượt — chuyến thêm
 * đi và chuyến thêm về, cùng xe, cùng tài xế, đánh dấu {@link Trip#getForecastLoopHold()} ở cả hai lượt. Một
 * vòng giữ được giữ / rút theo CẢ HAI khung của nó (kế hoạch đổi đôi ghép thì vòng cũ bị rút, vòng mới được
 * tạo), và Admin từ chối nó thì không khung nào trong hai khung được đề xuất lại; kế hoạch của job không ghép
 * khung đã bị từ chối, nên chuyến thêm còn lại của đôi vẫn được xét như một thẻ riêng.</li>
 * </ul>
 *
 * Chạy lại bao nhiêu lần cũng an toàn: kế hoạch dựng lần hai đã trừ các vòng vừa giữ (chúng chiếm chỗ trong
 * khung và giữ xe, tài xế như mọi chuyến chờ duyệt), nên không tạo trùng.
 *
 * Phép "tạm coi như chưa có" đổi trạng thái các lượt sang CANCELLED NGAY TRONG transaction này để mọi truy vấn
 * của kế hoạch (xe bận, vị trí, km đã cam kết, chuyến trong khung) không thấy chúng, rồi trả lại
 * PENDING_APPROVAL trước khi làm gì khác. Không lượt nào rời transaction ở một trạng thái mà FSM không cho:
 * vòng được giữ thì không đổi, vòng bị rút được xoá mềm (vẫn PENDING_APPROVAL) qua {@link TripService#deleteTrip}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastLoopService {

    /** Lịch sử cũ hơn chừng này ngày thì dự báo không đủ tin để tự tạo chuyến (CM-10). */
    static final int MAX_STALE_DAYS = 7;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final RecommendationService recommendationService;
    private final ForecastService forecastService;
    private final TripService tripService;
    private final TripRepository tripRepository;

    /**
     * Kết quả một lần chạy bước 4: các vòng vừa tạo, số vòng giữ nguyên, các vòng rút vì dự báo / vì hạn duyệt
     * (mỗi vòng một dòng mô tả), lý do bỏ qua phần tạo nếu có, và số thẻ không tạo vì Admin đã từ chối khung đó.
     */
    public record Result(List<String> created, int kept, List<String> withdrawnByForecast,
            List<String> withdrawnByDeadline, String skipped, int refusedSkipped) {
    }

    @Transactional
    public Result reconcile(LocalDateTime now) {
        LocalDateTime deadline = now.plusHours(TripService.MIN_HOURS_BEFORE_DEPARTURE);
        Map<Long, List<Trip>> pending = pendingLoops();

        // ── (b) Hạn duyệt: còn < 72 giờ mà chưa duyệt thì không kịp bán vé; lượt lẻ (chuyến thêm đã mất) cũng rút ──
        List<String> withdrawnByDeadline = new ArrayList<>();
        for (Map.Entry<Long, List<Trip>> loop : new ArrayList<>(pending.entrySet())) {
            Trip extra = extraOf(loop.getKey(), loop.getValue());
            if (extra == null || extra.getDepartureTime().isBefore(deadline)) {
                withdrawnByDeadline.add(withdraw(loop.getValue()));
                pending.remove(loop.getKey());
            }
        }

        DemandForecastViewDto forecast = forecastService.buildForecast();
        String skipped = null;
        if (forecast.getSeries() == null || forecast.getSeries().isEmpty()) {
            skipped = "dự báo chưa có chuỗi nào đủ dữ liệu";
        } else if (forecast.getHistoryStaleDays() > MAX_STALE_DAYS) {
            skipped = "lịch sử dự báo cũ " + forecast.getHistoryStaleDays() + " ngày (quá " + MAX_STALE_DAYS + " ngày)";
        }
        if (skipped != null) {
            log.warn("🌙 [Job đêm] Không tự tạo vòng: {}; giữ nguyên {} vòng chờ duyệt", skipped, pending.size());
            return new Result(List.of(), pending.size(), List.of(), withdrawnByDeadline, skipped, 0);
        }

        // Khung Admin đã từ chối: đọc TRƯỚC phép "tạm coi như chưa có" bên dưới — phép đó cũng chuyển tạm các vòng
        // của chính job sang CANCELLED, mà CANCELLED là dấu của một lần Admin từ chối.
        java.util.Set<String> refused = refusedSlots();

        // ── (a) Dự báo giảm: kế hoạch khi tạm coi như các vòng chờ duyệt của chính job chưa có ──
        pending.values().forEach(legs -> legs.forEach(leg -> leg.setStatus(TripStatus.CANCELLED)));
        tripRepository.flush();
        Map<String, Integer> wanted = new HashMap<>();
        for (Map.Entry<RecommendationCardDto, RecommendationService.Loop> entry : recommendationService
                .buildPlan(refused).loops().entrySet()) {
            if (qualifies(entry.getKey(), deadline)) {
                wanted.merge(loopKey(entry.getKey(), entry.getValue()), 1, Integer::sum);
            }
        }
        pending.values().forEach(legs -> legs.forEach(leg -> leg.setStatus(TripStatus.PENDING_APPROVAL)));
        tripRepository.flush();

        int kept = 0;
        List<String> withdrawnByForecast = new ArrayList<>();
        for (Map.Entry<Long, List<Trip>> loop : pending.entrySet()) { // theo id vòng tăng dần: vòng cũ giữ trước
            String key = pendingKey(loop.getKey(), loop.getValue());
            if (wanted.getOrDefault(key, 0) > 0) {
                wanted.merge(key, -1, Integer::sum);
                kept++;
            } else {
                withdrawnByForecast.add(withdraw(loop.getValue()));
            }
        }

        // ── Tạo: kế hoạch đã trừ các vòng vừa giữ, nên chỉ còn phần khung vẫn thiếu; khung Admin đã từ chối thì thôi ──
        Map<Long, Map<Integer, BigDecimal>> prices = recommendationService.loadLatestPrices();
        List<String> created = new ArrayList<>();
        int[] refusedSkipped = { 0 };
        recommendationService.buildPlan(refused).loops().forEach((card, loop) -> {
            if (!qualifies(card, deadline)) {
                return;
            }
            if (loopSlots(card, loop).stream().anyMatch(refused::contains)) {
                refusedSkipped[0]++;
                return;
            }
            created.add(create(card, loop, prices));
        });

        log.info("🌙 [Job đêm] Vòng dự báo: tạo {}, giữ {}, rút {} (dự báo giảm) + {} (hạn duyệt), bỏ qua {} khung Admin"
                + " đã từ chối", created.size(), kept, withdrawnByForecast.size(), withdrawnByDeadline.size(),
                refusedSkipped[0]);
        return new Result(created, kept, withdrawnByForecast, withdrawnByDeadline, null, refusedSkipped[0]);
    }

    /**
     * Khung (tuyến × ngày × giờ) có vòng dự báo mà Admin đã từ chối (chuyến thêm của vòng ở CANCELLED — job tự rút
     * thì xoá mềm, không bao giờ để CANCELLED): người đã quyết "không" cho khung đó, máy không đề xuất lại. Vòng
     * giữ xe (CT9) có hai chuyến thêm, nên cả hai khung của nó; lượt đi kèm của vòng thường thì không — khung đó
     * không phải một khung cần tăng cường.
     */
    private java.util.Set<String> refusedSlots() {
        java.util.Set<String> slots = new java.util.HashSet<>();
        for (Trip leg : tripRepository.findForecastLoopLegsByStatus(TripStatus.CANCELLED)) {
            if (leg.getId().equals(leg.getForecastLoopId()) || leg.isHeldForecastLoop()) {
                slots.add(tripSlot(leg));
            }
        }
        return slots;
    }

    /** Thẻ được tự tạo: đủ điều kiện, lãi cả vòng dương, chuyến thêm còn ≥ 72 giờ (QĐ-24 mục 2–3). */
    private static boolean qualifies(RecommendationCardDto card, LocalDateTime deadline) {
        return card.getStatus() == RecommendationStatus.RECOMMENDED
                && card.getEstimatedProfit() != null && card.getEstimatedProfit().signum() > 0
                && !card.getDate().atTime(card.getDepartureHour(), 0).isBefore(deadline);
    }

    /** Lưu một vòng: chuyến thêm trước (để có id của vòng), rồi lượt đi kèm. */
    private String create(RecommendationCardDto card, RecommendationService.Loop loop,
            Map<Long, Map<Integer, BigDecimal>> prices) {
        Trip companionLeg = loop.companion();
        Trip extraLeg = loop.legs().stream().filter(leg -> leg != companionLeg).findFirst().orElseThrow();
        Trip extra = persist(extraLeg, loop, prices, null);
        extra.setForecastLoopId(extra.getId());
        tripRepository.save(extra);
        Trip companion = companionLeg != null ? persist(companionLeg, loop, prices, extra.getId()) : null;

        boolean held = loop.kind() == LoopLegKind.HOLD;
        String label = describe(extra) + " · xe " + loop.bus().getLicensePlate() + " · lãi cả vòng "
                + String.format(java.util.Locale.US, "%,.0f", card.getEstimatedProfit()).replace(',', '.') + "đ"
                + (companion == null ? ""
                        : held ? " (giữ xe ở " + card.getCompanion().getWaitStation() + " thêm "
                                + Math.round(card.getCompanion().getWaitHours()) + " giờ, về bằng chuyến thêm "
                                + describe(companion) + ")"
                        : " (kèm " + describe(companion) + ")");
        log.info("🌙 [Job đêm] Tạo vòng dự báo #{}: {}", extra.getId(), label);
        return "#" + extra.getId() + " " + label;
    }

    private Trip persist(Trip leg, RecommendationService.Loop loop, Map<Long, Map<Integer, BigDecimal>> prices,
            Long loopId) {
        Route route = leg.getRoute();
        LocalDateTime departure = leg.getDepartureTime();
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(leg.getArrivalTimeExpected());
        trip.setBus(loop.bus());
        trip.setDriver(loop.driver());
        trip.setTotalSeats(leg.getTotalSeats()); // sức chứa xe, do cổng của thẻ đặt
        trip.setPrice(prices.getOrDefault(route.getId(), Map.of()).get(departure.getHour()));
        trip.setStatus(TripStatus.PENDING_APPROVAL);
        trip.setExtraTrip(true);
        trip.setOriginalTrip(mainTripOf(route.getId(), departure));
        trip.setForecastLoopId(loopId);
        trip.setForecastLoopHold(loop.kind() == LoopLegKind.HOLD ? Boolean.TRUE : null); // CT9: vòng giữ xe
        return tripRepository.save(trip);
    }

    /** Chuyến chính của khung (CT8: không bao giờ là một chuyến tăng cường); khung chưa có thì null. */
    private Trip mainTripOf(Long routeId, LocalDateTime departure) {
        LocalDateTime hour = departure.truncatedTo(ChronoUnit.HOURS);
        List<Trip> mains = tripRepository.findMainTripsOfSlot(routeId, TripStatus.CANCELLED, hour, hour.plusHours(1));
        return mains.isEmpty() ? null : mains.get(0);
    }

    /**
     * Rút một vòng chờ duyệt: bản nháp của chính job, chưa ai duyệt, chưa mở bán — xoá mềm từng lượt qua
     * {@link TripService#deleteTrip} (chính sách xoá cho phép chuyến chờ duyệt; dòng vẫn nằm trong DB). Không chuyển
     * CANCELLED: CANCELLED của một vòng dự báo nghĩa là ADMIN đã từ chối, và khung đó không được đề xuất lại.
     */
    private String withdraw(List<Trip> legs) {
        String label = "#" + legs.get(0).getForecastLoopId() + " " + legs.stream().map(this::describe)
                .reduce((a, b) -> a + " + " + b).orElse("");
        for (Trip leg : legs) {
            tripService.deleteTrip(leg.getId());
        }
        log.info("🌙 [Job đêm] Rút vòng dự báo {}", label);
        return label;
    }

    /** Các vòng dự báo còn chờ duyệt, theo id vòng tăng dần; mỗi vòng các lượt theo giờ đi. */
    private Map<Long, List<Trip>> pendingLoops() {
        Map<Long, List<Trip>> loops = new LinkedHashMap<>();
        for (Trip leg : tripRepository.findForecastLoopLegsByStatus(TripStatus.PENDING_APPROVAL)) {
            loops.computeIfAbsent(leg.getForecastLoopId(), id -> new ArrayList<>()).add(leg);
        }
        return loops;
    }

    /** Chuyến thêm của vòng là lượt có id = id vòng; null nếu nó không còn chờ duyệt (lượt lẻ). */
    private static Trip extraOf(Long loopId, List<Trip> legs) {
        return legs.stream().filter(leg -> loopId.equals(leg.getId())).findFirst().orElse(null);
    }

    private static String cardSlot(RecommendationCardDto card) {
        return LoopEconomics.slotKey(card.getRouteId(), card.getDate(), card.getDepartureHour());
    }

    /**
     * Khoá để giữ / rút một vòng (CT9): vòng thường theo khung chuyến thêm của nó, như CT8b — lượt đi kèm đổi giờ
     * thì vòng vẫn được giữ; vòng giữ xe theo CẢ HAI khung, vì hai chuyến thêm đó cùng phục vụ hai khung.
     */
    private static String loopKey(RecommendationCardDto card, RecommendationService.Loop loop) {
        return loop.kind() == LoopLegKind.HOLD ? cardSlot(card) + ">" + tripSlot(loop.companion()) : cardSlot(card);
    }

    /** Khoá của một vòng đang chờ duyệt, cùng cách với {@link #loopKey}. */
    private static String pendingKey(Long loopId, List<Trip> legs) {
        Trip extra = extraOf(loopId, legs);
        if (legs.stream().noneMatch(Trip::isHeldForecastLoop)) {
            return tripSlot(extra);
        }
        return tripSlot(extra) + ">" + legs.stream().filter(leg -> leg != extra).findFirst()
                .map(ForecastLoopService::tripSlot).orElse("");
    }

    /** Các khung mà vòng của thẻ chiếm như chuyến thêm: khung của thẻ, và khung chuyến thêm về của vòng giữ. */
    private static List<String> loopSlots(RecommendationCardDto card, RecommendationService.Loop loop) {
        return loop.kind() == LoopLegKind.HOLD ? List.of(cardSlot(card), tripSlot(loop.companion()))
                : List.of(cardSlot(card));
    }

    private static String tripSlot(Trip trip) {
        return LoopEconomics.slotKey(trip.getRoute().getId(), trip.getDepartureTime().toLocalDate(),
                trip.getDepartureTime().getHour());
    }

    private String describe(Trip trip) {
        Route route = trip.getRoute();
        return route.getDeparturePointDisplay() + " → " + route.getDestinationPointDisplay() + " "
                + trip.getDepartureTime().format(TIME);
    }
}
