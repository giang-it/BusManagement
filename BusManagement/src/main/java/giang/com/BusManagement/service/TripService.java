package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.*;
import giang.com.BusManagement.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;

    /**
     * Thời gian nghỉ tối thiểu bắt buộc giữa 2 chuyến của cùng một tài xế/phụ xe
     * (phút).
     * Đảm bảo có đủ thời gian để di chuyển, kiểm tra xe và nghỉ ngơi ngắn.
     */
    private static final int MIN_REST_BETWEEN_TRIPS_MINUTES = 30;

    /**
     * Buffer thêm vào khi kiểm tra xe có bận không.
     * Giúp xe kịp vệ sinh / chuẩn bị trước chuyến tiếp theo.
     */
    private static final int BUS_PREP_BUFFER_HOURS = 1;

    /**
     * Minimum hours before departure for an extra trip to be worth creating.
     * Below this, new tickets can't realistically be sold.
     * 3 days is conservative; bump to 120 for long-haul routes.
     */
    private static final long MIN_HOURS_BEFORE_DEPARTURE = 72;

    /**
     * Minimum hours a trip must have been on sale before we trust its occupancy
     * rate as signal. Guards against batch-upload spikes on brand-new trips.
     */
    private static final long MIN_SALE_OPEN_HOURS = 48;

    /**
     * If occupancy is at or above this, the trip is unambiguously hot and we
     * skip the MIN_SALE_OPEN_HOURS gate entirely.
     */
    private static final double INSTANT_HOT_THRESHOLD = 0.95;

    // =========================================================================
    // SCHEDULED JOB: Quét & tự động tạo chuyến tăng cường
    // =========================================================================

    /**
     * AI Scheduling Job: Mỗi 10 giây quét các KHUNG (tuyến × ngày × giờ) đang mở bán có tỉ lệ lấp đầy > 90%.
     * Khi phát hiện, tự động tạo chuyến tăng cường VÀ phân công đầy đủ
     * (xe + tài xế + phụ xe) theo ràng buộc hệ thống.
     *
     * Route pairs CT8c (QĐ-24 mục 6): job chỉ còn lo phần BẤT NGỜ mà dự báo không thấy — tổng vé thật ÷ tổng
     * ghế của MỌI chuyến đang bán trong khung (chuyến chính, chuyến tăng cường đã duyệt, vòng dự báo đã duyệt),
     * chuyến gốc luôn là chuyến chính của khung (không bao giờ là một chuyến tăng cường — hết nối chuỗi +30
     * phút, P5), và khung đang có một chuyến tăng cường chờ duyệt (ai tạo cũng vậy) thì không tạo thêm.
     * Chuyến tăng cường đã bị hủy/từ chối KHÔNG chặn (TC_AI_009, như trước).
     *
     * @Transactional BẮT BUỘC ở đây vì:
     *                1. Giữ Hibernate session mở suốt vòng lặp → trip.getRoute()
     *                (lazy) không bị LazyInitializationException.
     *                2. Khi gọi createExtraTrip() từ cùng class (self-invocation),
     *                Spring AOP KHÔNG tạo proxy → @Transactional trên method đó bị
     *                bỏ qua.
     */
    @Scheduled(fixedRate = 10_000)
    @Transactional
    public void scanAndSuggestExtraTrips() {
        java.util.Map<String, List<Trip>> slots = new java.util.LinkedHashMap<>();
        for (Trip trip : tripRepository.findByStatusWithRoute(TripStatus.ACTIVE)) {
            if (trip.getRoute() != null && trip.getDepartureTime() != null) {
                slots.computeIfAbsent(scanSlotKey(trip), key -> new java.util.ArrayList<>()).add(trip);
            }
        }
        for (List<Trip> slot : slots.values()) {
            Trip main = slotMainTrip(slot);
            if (main != null && isHotTrip(main, slot) && !hasPendingExtra(main)) {
                createExtraTrip(main);
            }
        }
    }

    /**
     * Returns true when a slot genuinely needs an extra trip right now.
     *
     * Three time-based guards (on top of the slot occupancy check), asked of the slot's main trip:
     * 1. Departure must still be in the future.
     * 2. Enough lead time left for customers to actually buy tickets on the new trip.
     * 3. The main trip has been on sale long enough for the occupancy to be a meaningful signal,
     * unless the slot is already past the instant-hot threshold.
     */
    private boolean isHotTrip(Trip trip, List<Trip> slot) {
        // ── Occupancy gate: hỏi Trip, không so số ───────────────────────────────
        // Ngưỡng "chuyến đông" (> 0,90) sống ở Trip.exceedsReinforcementThreshold() — cùng định nghĩa với
        // Trip.needsReinforcement(), áp cho tổng vé ÷ tổng ghế của cả khung (CT8c).
        int sold = slot.stream().mapToInt(Trip::getTicketsSold).sum();
        int seats = slot.stream().mapToInt(Trip::getTotalSeats).sum();
        double occupancy = seats > 0 ? (double) sold / seats : 0.0;
        if (!Trip.exceedsReinforcementThreshold(sold, seats)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // ── Gate 3: departure must still be in the future ───────────────────────
        if (trip.getDepartureTime() == null || !trip.getDepartureTime().isAfter(now)) {
            log.debug("⏩ [AI] Chuyến #{}: đã khởi hành, bỏ qua.", trip.getId());
            return false;
        }

        // ── Gate 4: enough lead time remaining ──────────────────────────────────
        long hoursUntilDeparture = trip.getHoursUntilDeparture();
        if (hoursUntilDeparture < MIN_HOURS_BEFORE_DEPARTURE) {
            log.debug(
                    "⏩ [AI] Chuyến #{}: còn {} giờ đến khởi hành (< {} giờ yêu cầu). Không đủ thời gian mở vé mới.",
                    trip.getId(), String.format("%.1f", (double) hoursUntilDeparture), MIN_HOURS_BEFORE_DEPARTURE);
            return false;
        }

        // ── Gate 5: sale-velocity sanity check ──────────────────────────────────
        // Skip wait if occupancy is already above the instant-hot threshold.
        if (occupancy < INSTANT_HOT_THRESHOLD) {
            long hoursOnSale = trip.getHoursOnSale();
            if (hoursOnSale < MIN_SALE_OPEN_HOURS) {
                log.debug(
                        "⏩ [AI] Chuyến #{}: chỉ mới mở bán {} giờ (< {} giờ yêu cầu). Có thể là spike ảo.",
                        trip.getId(), String.format("%.0f", (double) hoursOnSale), MIN_SALE_OPEN_HOURS);
                return false;
            }
        }

        log.debug(
                "🔥 [AI] Khung của chuyến #{} HOT: {}% ghế, còn {} giờ, đã mở bán {} giờ.",
                trip.getId(),
                String.format("%.1f", occupancy * 100),
                hoursUntilDeparture,
                trip.getHoursOnSale());
        return true;
    }

    /**
     * Tạo và lưu một chuyến tăng cường.
     * Chạy trong cùng transaction của scanAndSuggestExtraTrips (self-invocation).
     * Gán originalTrip trực tiếp vì chuyến gốc đã tồn tại trong DB.
     */
    private void createExtraTrip(Trip trip) {
        log.info("🤖 [AI] Chuyến #{} đạt {}% lấp đầy. Đang tạo chuyến tăng cường...",
                trip.getId(), String.format("%.1f", trip.getOccupancyRate() * 100));

        // --- Tính thời gian ---
        LocalDateTime extraDeparture = trip.getDepartureTime().plusMinutes(30);
        int routeDurationMinutes = (trip.getRoute().getEstimatedDuration() != null)
                ? trip.getRoute().getEstimatedDuration()
                : 240;
        LocalDateTime extraArrival = extraDeparture.plusMinutes(routeDurationMinutes);

        Trip extraTrip = new Trip();
        extraTrip.setRoute(trip.getRoute());
        extraTrip.setDepartureTime(extraDeparture);
        extraTrip.setArrivalTimeExpected(extraArrival);
        extraTrip.setStatus(TripStatus.PENDING_APPROVAL);
        // Số ghế tạm lấy theo chuyến gốc; nếu AI phân công được xe thì bên dưới sẽ
        // thay bằng sức chứa THẬT của xe đó (lỗi #22). Số của chuyến gốc chỉ là CHỖ
        // TRỐNG khi không tìm được xe: số ghế Admin sửa tay ở chuyến gốc là quyết định
        // cho RIÊNG chuyến đó, không truyền sang chuyến tăng cường — approveTrip()
        // đặt lại theo sức chứa xe Admin chọn lúc phân công thủ công (mục #26).
        extraTrip.setTotalSeats(trip.getTotalSeats());
        extraTrip.setPrice(trip.getPrice());
        extraTrip.setExtraTrip(true);
        extraTrip.setOriginalTrip(trip); // trip đã tồn tại trong DB, gán trực tiếp không vi phạm FK

        // --- Bước 3: AI phân công tài nguyên ---
        AutoAssignResult result = autoAssignResources(extraTrip, null);

        if (result.isSuccess()) {
            extraTrip.setBus(result.getBus());
            extraTrip.setDriver(result.getDriver());
            extraTrip.setAssistant(result.getAssistant());
            extraTrip.getCoDrivers().clear();
            extraTrip.getCoDrivers().addAll(result.getCoDrivers());

            // Chuyến tăng cường mở bán ĐÚNG sức chứa của xe được gán. Trước đây số
            // ghế được chép nguyên từ chuyến gốc — vốn là con số Admin gõ tay, không
            // ràng buộc với xe nào — rồi AI chọn một xe khác theo loại của tuyến, nên
            // đã sinh ra chuyến bán 40 ghế trên xe 22 chỗ (lỗi #22, chuyến 6 trên DB
            // thật). Cùng cách RecommendationService.buildCard() đặt totalSeats từ xe
            // được chọn, và cùng mặc định mà form tạo chuyến tự điền từ data-capacity.
            Integer capacity = seatCapacityOf(result.getBus());
            if (capacity != null) {
                extraTrip.setTotalSeats(capacity);
            }

            String assistantName = result.getAssistant() != null
                    ? result.getAssistant().getUser().getFullName()
                    : "Không có";

            log.info("✅ [AI] Phân công thành công: Xe {} | Tài xế chính: {} | Số tài phụ: {} | Phụ xe: {}",
                    result.getBus().getLicensePlate(),
                    result.getDriver().getUser().getFullName(),
                    result.getCoDrivers().size(),
                    assistantName);
        } else {
            log.warn("⚠️ [AI] Không tự phân công được: {} → Admin xử lý thủ công.",
                    result.getFailureReason());
        }

        tripRepository.save(extraTrip);
    }

    // =========================================================================
    // CORE AI: Auto-assign resources
    // =========================================================================

    /**
     * Thuật toán trung tâm: Tự động phân công xe + tài xế chính + phụ xe
     * cho một chuyến xe, tuân theo tất cả ràng buộc hệ thống. ctx: null = hỏi DB (job 10 giây); lịch thường lệ (CT6) truyền context lập lịch.
     */
    private AutoAssignResult autoAssignResources(Trip trip, AvailabilityContext ctx) {
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected();
        double tripDurationHours = Duration.between(departure, arrival).toMinutes() / 60.0;

        // Bước 1: Tìm xe phù hợp
        Bus bus = findBestAvailableBus(trip, departure, arrival, ctx);
        if (bus == null) {
            return AutoAssignResult.failure(
                    "Không có xe nào sẵn sàng / không bận / chưa tới ngưỡng bảo trì / đang ở bến xuất phát trong khung giờ này");
        }

        // Tính toán số tài xế cần thiết
        int requiredDrivers = (int) Math.ceil(tripDurationHours / 8.0);
        if (requiredDrivers < 1)
            requiredDrivers = 1;

        // Danh sách những người bị loại trừ (tránh trùng lặp)
        java.util.List<Driver> assignedStaff = new java.util.ArrayList<>();

        // Bước 2: Tìm tài xế chính
        Driver driver = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers,
                assignedStaff, false, ctx, trip.getRoute());
        if (driver == null) {
            return AutoAssignResult.failure(
                    "Không có tài xế chính hợp lệ (cần bằng còn hạn, rảnh, đủ định mức ngày, ở bến xuất phát)");
        }
        assignedStaff.add(driver);

        // Bước 3: Tìm các tài xế phụ (nếu chuyến dài > 8h) — vẫn trực tiếp lái xe nên
        // vẫn phải chịu ràng buộc giờ lái tối đa 8h/ngày như tài xế chính.
        java.util.List<Driver> coDrivers = new java.util.ArrayList<>();
        for (int i = 1; i < requiredDrivers; i++) {
            Driver coDriver = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers,
                    assignedStaff, false, ctx, trip.getRoute());
            if (coDriver == null) {
                return AutoAssignResult.failure(
                        "Không tìm đủ số lượng tài xế phụ hợp lệ cho chuyến dài " + Math.round(tripDurationHours)
                                + "h (cần " + requiredDrivers + " tài xế)");
            }
            coDrivers.add(coDriver);
            assignedStaff.add(coDriver);
        }

        // Bước 4: Tìm phụ xe (nếu chuyến > 8h thì phụ xe là BẮT BUỘC, ngược lại là tùy
        // chọn). Phụ xe KHÔNG trực tiếp lái xe (getDrivingHoursForDate() luôn tính
        // 0.0h cho vai trò này) nên KHÔNG được áp ràng buộc giờ lái tối đa 8h/ngày —
        // truyền isAssistantRole=true để bỏ qua điều kiện đó, chỉ còn kiểm tra bằng
        // lái còn hạn + không trùng lịch (không thể có mặt ở 2 chuyến cùng lúc).
        Driver assistant = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers,
                assignedStaff, true, ctx, trip.getRoute());
        if (assistant == null && tripDurationHours > 8.0) {
            return AutoAssignResult.failure(
                    "Không tìm thấy phụ xe hợp lệ (bắt buộc đối với chuyến xe dài trên 8 tiếng)");
        }

        return AutoAssignResult.success(bus, driver, assistant, coDrivers);
    }

    // =========================================================================
    // HELPER: Tìm xe tốt nhất
    // =========================================================================

    /**
     * Tìm xe READY, không bận, chưa quá hạn/sắp đến hạn bảo trì.
     * Thứ tự ưu tiên: xe ĐÚNG LOẠI GỢI Ý của tuyến trước, rồi xe ít km kể từ bảo
     * trì nhất (xe "mới nhất") — xem {@link #preferredTypeThenLeastWorn}.
     *
     * Dùng CHUNG ràng buộc bảo trì với validateBusForTrip()/Bus.isNearMaintenance()
     * (>= 90% maintenanceThreshold sau khi cộng quãng đường chuyến này) để AI
     * auto-assign không bao giờ chọn một xe mà luồng thủ công (Create/Edit/Approve)
     * sẽ từ chối ngay sau đó. Không còn fallback "vẫn chọn xe sát/quá ngưỡng" —
     * nếu không còn xe nào đủ điều kiện, trả về null để autoAssignResources() báo
     * thất bại và Admin xử lý thủ công (đổi xe khác hoặc đưa xe đi bảo trì).
     */
    private Bus findBestAvailableBus(Trip trip, LocalDateTime departure, LocalDateTime arrival) {
        return findBestAvailableBus(trip, departure, arrival, null);
    }

    /**
     * Bản nhận AvailabilityContext (Phase 7). ctx == null → hành vi y hệt bản cũ
     * (mọi truy vấn bận qua DB); ctx != null → các phép kiểm tra "bận" đọc từ dữ
     * liệu đã preload trong ctx thay vì truy vấn DB từng xe. Luật lọc/xếp hạng
     * KHÔNG đổi — chỉ nguồn dữ liệu đổi (bận, km đã cam kết; context lập lịch CT6 thêm danh sách xe). Bảo trì: CT7d.
     */
    private Bus findBestAvailableBus(Trip trip, LocalDateTime departure, LocalDateTime arrival,
            AvailabilityContext ctx) {
        // Loại xe của tuyến là GỢI Ý, không phải luật (Group C(a)) — với AI cũng vậy
        // (chủ dự án chốt 2026-09-24, mục #26): xe đúng loại được chọn TRƯỚC, hết xe
        // đúng loại thì AI lấy xe loại khác thay vì bỏ cuộc. Trước đây AI lọc cứng
        // theo loại, nên chỉ cần hết Limousine là chuyến tăng cường rơi vào phân công
        // thủ công mang số ghế tạm của chuyến gốc — đúng đường dẫn tới #26. Rủi ro
        // thật của xe khác loại (bán quá chỗ) không phát sinh: createExtraTrip() đặt
        // số ghế theo xe được chọn, và luật #22 vẫn kiểm ở mọi lối.
        BusType preferredType = trip.getRoute().getSuitableBusType();
        List<Bus> candidates = ctx != null && ctx.isLookAhead() ? ctx.schedulableBuses() : busRepository.findByStatus(BusStatus.READY);

        // Mở rộng cửa sổ thời gian để đảm bảo xe kịp chuẩn bị
        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        double tripDistance = trip.getRoute().getDistanceKm() != null ? trip.getRoute().getDistanceKm() : 0.0;

        return candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, null, ctx) && busFits(bus, trip.getRoute(), departure, arrival, ctx))
                .filter(bus -> machineMaintenanceAllows(bus, tripDistance, // CT7d: ngày bảo trì, km cả lịch, quãng về
                        homewardKm(bus, trip.getRoute(), tripDistance), departure, arrival, ctx))
                .min(machineOrder(preferredType, departure, arrival, ctx)) // CT7d: đúng loại, rồi km cả lịch ít nhất
                .orElse(null);
    }

    /**
     * Thứ tự ưu tiên xe DUY NHẤT của hệ thống khi đã biết tuyến: xe đúng loại gợi ý
     * của tuyến trước (false xếp trước true), rồi xe ít km kể từ bảo trì nhất.
     *
     * Dùng cho dropdown Duyệt/Sửa (getAvailableBusesForTrip xếp cả danh sách) — form
     * Tạo xếp y hệt ở phía JS. Máy (findBestAvailableBus, selectLoop) xếp CÙNG luật
     * nhưng đo km tính cả lịch đã xếp (machineOrder, CT7d R5). Tuyến không quy
     * định loại thì vế đầu bằng nhau với mọi xe ⇒ thuần theo km.
     */
    private static Comparator<Bus> preferredTypeThenLeastWorn(BusType preferredType) {
        return Comparator.comparing((Bus bus) -> !isOfType(bus, preferredType))
                .thenComparingDouble(Bus::getKmSinceLastMaintenance);
    }

    /**
     * Kiểm tra xe có bị gán cho chuyến khác trong cửa sổ thời gian [windowStart,
     * windowEnd] không.
     */
    private boolean isBusBusy(Bus bus, LocalDateTime windowStart, LocalDateTime windowEnd, Long excludeTripId) {
        return isBusBusy(bus, windowStart, windowEnd, excludeTripId, null);
    }

    /**
     * Bản nhận AvailabilityContext. ctx == null → truy vấn DB (đường cũ, giữ
     * nguyên). ctx != null → xét trên các chuyến đã preload bằng predicate Java
     * {@link #intervalsOverlap} — cùng vị từ giao khoảng với câu SQL
     * {@code existsOverlappingTripForBus}; sự tương đương được chốt bằng test
     * TripServiceAvailabilityContextTest. Package-private để test gọi được cả hai
     * nhánh.
     */
    boolean isBusBusy(Bus bus, LocalDateTime windowStart, LocalDateTime windowEnd, Long excludeTripId,
            AvailabilityContext ctx) {
        List<TripStatus> busyStatuses = List.of(TripStatus.ACTIVE, TripStatus.DEPARTED,
                TripStatus.PENDING_APPROVAL);
        if (ctx == null) {
            return tripRepository.existsOverlappingTripForBus(
                    bus, busyStatuses, windowStart, windowEnd, excludeTripId);
        }
        return ctx.trips().stream().anyMatch(t -> busyStatuses.contains(t.getStatus())
                && t.getBus() != null && t.getBus().getId() != null
                && t.getBus().getId().equals(bus.getId())
                && notExcluded(t, excludeTripId)
                && intervalsOverlap(t, windowStart, windowEnd));
    }

    // =========================================================================
    // PREDICATE NỀN TẢNG dùng chung cho nhánh in-memory (Phase 7)
    // =========================================================================
    // Đây KHÔNG phải business rule riêng của Recommendation — chỉ là các phép
    // toán nền tảng (giao khoảng thời gian, xác định vai trò, loại trừ chuyến)
    // tái hiện đúng điều kiện đang nằm trong JPQL, để có thể đánh giá "bận / giờ
    // lái" trên dữ liệu preload. Quyết định nghiệp vụ (trạng thái nào tính là
    // bận, ngưỡng 8h, bảo trì, bằng lái...) vẫn nằm nguyên trong các method
    // selection/validation của TripService. Test TripServiceAvailabilityContextTest
    // đối chiếu các predicate này với điều kiện SQL ở những ca biên.

    /**
     * Hai chuyến "trùng lịch" khi khoảng thời gian thực sự giao nhau. Null-safe và
     * khớp CHÍNH XÁC điều kiện SQL {@code departureTime < windowEnd AND
     * arrivalTimeExpected > windowStart}: chạm đúng biên KHÔNG tính là trùng, và
     * chuyến thiếu mốc thời gian (null) không thể trùng (mirror hành vi so sánh
     * với NULL trong SQL trả về UNKNOWN → loại).
     */
    private static boolean intervalsOverlap(Trip t, LocalDateTime windowStart, LocalDateTime windowEnd) {
        LocalDateTime dep = t.getDepartureTime();
        LocalDateTime arr = t.getArrivalTimeExpected();
        if (dep == null || arr == null) {
            return false;
        }
        return dep.isBefore(windowEnd) && arr.isAfter(windowStart);
    }

    /**
     * Tài xế tham gia chuyến ở BẤT KỲ vai trò nào — tài xế chính, tài xế phụ
     * (coDrivers) hoặc phụ xe (assistant) — so theo userId. Hợp nhất đúng phạm vi
     * của ba câu SQL {@code existsOverlappingTripFor…} (Driver, CoDriver, Assistant
     * — mỗi vai một câu, lỗi #35), cũng là phạm vi mà {@link #tripsOfPersonOnDate}
     * dùng để tính giờ lái.
     */
    private static boolean tripInvolvesDriver(Trip t, Driver driver) {
        Long id = driver.getUserId();
        if (t.getDriver() != null && id.equals(t.getDriver().getUserId())) {
            return true;
        }
        if (t.getAssistant() != null && id.equals(t.getAssistant().getUserId())) {
            return true;
        }
        if (t.getCoDrivers() != null) {
            for (Driver cd : t.getCoDrivers()) {
                if (cd != null && id.equals(cd.getUserId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Mirror điều kiện SQL {@code (:excludeTripId IS NULL OR t.id <> :excludeTripId)}. */
    private static boolean notExcluded(Trip t, Long excludeTripId) {
        return excludeTripId == null || !excludeTripId.equals(t.getId());
    }

    // =========================================================================
    // HELPER: Tìm tài xế / phụ xe tốt nhất
    // =========================================================================

    /**
     * Tìm tài xế/phụ xe hợp lệ theo thứ tự ưu tiên:
     * 1. Bằng lái còn hạn > 7 ngày (ưu tiên 1)
     * 2. Tổng giờ lái trong 24h + thời lượng chuyến mới ≤ 8 giờ
     * 3. Không bận trong khoảng [departure - 30 phút, arrival + 30 phút]
     * (bao gồm cả vai trò phụ xe ở các chuyến khác)
     * 4. Không phải excludeDriver (tránh tài xế chính == phụ xe)
     *
     * Sắp xếp: Người ít giờ lái nhất trong ngày được ưu tiên → phân bổ đều tải.
     *
     * @param excludeDrivers  Các tài xế cần loại trừ (tránh trùng vai trò trong
     *                        cùng 1 chuyến)
     * @param isAssistantRole true nếu đang tìm PHỤ XE. Phụ xe không trực tiếp lái
     *                        xe (getDrivingHoursForDate() luôn tính 0.0h cho vai
     *                        trò này — xem method đó), nên ràng buộc "tổng giờ
     *                        lái + chuyến mới ≤ 8h" KHÔNG áp dụng cho phụ xe.
     *                        Ràng buộc bằng lái còn hạn và không trùng lịch vẫn
     *                        được giữ nguyên vì phụ xe vẫn cần có mặt thực tế
     *                        trên xe.
     */
    private Driver findBestAvailableDriver(LocalDateTime departure, LocalDateTime arrival,
            double tripDurationHours, int totalDriversCount, java.util.Collection<Driver> excludeDrivers,
            boolean isAssistantRole, Route route) {
        return findBestAvailableDriver(departure, arrival, tripDurationHours, totalDriversCount,
                excludeDrivers, isAssistantRole, null, route);
    }

    /**
     * Bản nhận AvailabilityContext (Phase 7). ctx == null → mọi phép tính giờ lái
     * và kiểm tra bận đi qua DB (đường cũ, giữ nguyên hành vi); ctx != null → đọc
     * từ dữ liệu đã preload. Luật lọc/ưu tiên KHÔNG đổi — chỉ nguồn dữ liệu đổi.
     */
    private Driver findBestAvailableDriver(LocalDateTime departure, LocalDateTime arrival,
            double tripDurationHours, int totalDriversCount, java.util.Collection<Driver> excludeDrivers,
            boolean isAssistantRole, AvailabilityContext ctx, Route route) {
        // Cửa sổ kiểm tra = thêm thời gian nghỉ bắt buộc ở cả hai đầu
        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        double shareDuration = tripDurationHours / totalDriversCount;
        double effectiveHours = Math.min(shareDuration, 8.0);

        List<Driver> availableDrivers = driverRepository.findAll().stream()
                // Tài xế phải đang hoạt động
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                // Loại trừ tài xế đã được chọn (để tránh trùng lặp vai trò)
                .filter(d -> excludeDrivers == null
                        || excludeDrivers.stream().noneMatch(ex -> ex.getUserId().equals(d.getUserId())))
                // Ràng buộc: bằng lái phải còn hạn VÀO NGÀY KHỞI HÀNH (không phải
                // vào hôm nay) — cùng mốc thời gian mà tầng ưu tiên 7 ngày bên dưới
                // đã dùng, nếu không tài xế hết hạn trước ngày chạy sẽ lọt qua đây
                // rồi rơi xuống nhánh fallback và được chọn kèm log sai sự thật.
                .filter(d -> d.isLicenseValid(departure.toLocalDate()))
                // Ràng buộc: tổng giờ lái hôm nay + thời lượng chuyến không được vượt 8 tiếng.
                // CHỈ áp dụng cho tài xế chính/tài xế phụ (isAssistantRole=false) — phụ xe
                // không lái nên bỏ qua ràng buộc này (Tính chính xác dựa trên CÁC CHUYẾN ĐÃ
                // XẾP TRONG NGÀY với lượng giờ chia sẻ thực tế).
                .filter(d -> isAssistantRole || getDrivingHoursForDate(d, departure, null, ctx) + effectiveHours <= 8.0)
                // Ràng buộc: không đang bận (cả vai trò tài xế lẫn phụ xe)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null, ctx) && personFits(d, route, departure, arrival, ctx))
                .collect(Collectors.toList());

        // Ưu tiên 1: Bằng lái còn hạn trên 7 ngày
        Driver best = availableDrivers.stream()
                .filter(d -> d.getLicenseExpiryDate() != null
                        && d.getLicenseExpiryDate().isAfter(departure.toLocalDate().plusDays(7)))
                .min(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, null, ctx)))
                .orElse(null);

        if (best != null)
            return best;

        // Fallback: Bằng lái sắp hết hạn (< 7 ngày) nhưng vẫn còn hạn
        Driver fallback = availableDrivers.stream()
                .min(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, null, ctx)))
                .orElse(null);

        if (fallback != null) {
            log.warn("⚠️ [AI] Tài xế/Phụ xe {} được chọn nhưng bằng lái SẮP HẾT HẠN (vào {})!",
                    fallback.getUser().getFullName(), fallback.getLicenseExpiryDate());
        }

        return fallback;
    }

    // =========================================================================
    // PUBLIC SELECTION ENTRY POINTS (Phase 7 — Recommendation Engine)
    // =========================================================================

    /**
     * Điểm truy cập PUBLIC để chọn xe tốt nhất — dùng bởi RecommendationService
     * (Phase 7), một service RIÊNG không gọi được findBestAvailableBus() vốn
     * private. Nhận AvailabilityContext để chọn cho NHIỀU chuyến ứng viên mà chỉ
     * truy vấn lịch MỘT lần (xem {@link #buildAvailabilityContext}); truyền null
     * nếu muốn đọc "bận" trực tiếp từ DB.
     *
     * Ủy quyền nguyên vẹn cho findBestAvailableBus() private — KHÔNG nhân bản ràng
     * buộc/thứ tự ưu tiên, và cố ý KHÔNG lật method private thành public để không
     * mở rộng bề mặt của phần code nhạy cảm nhất. Xem THESIS_ROADMAP.md, Developer
     * Notes ("Why Phase 7 needs additive public overloads on TripService").
     */
    public Bus selectBestAvailableBus(Trip trip, LocalDateTime departure, LocalDateTime arrival,
            AvailabilityContext ctx) {
        return findBestAvailableBus(trip, departure, arrival, ctx);
    }

    /** Cặp đôi với {@link #selectBestAvailableBus} ở trên, cùng mục đích và lý do. */
    public Driver selectBestAvailableDriver(LocalDateTime departure, LocalDateTime arrival,
            double tripDurationHours, int totalDriversCount, java.util.Collection<Driver> excludeDrivers,
            boolean isAssistantRole, AvailabilityContext ctx, Route route) {
        return findBestAvailableDriver(departure, arrival, tripDurationHours, totalDriversCount,
                excludeDrivers, isAssistantRole, ctx, route);
    }

    /**
     * Nạp MỘT LẦN toàn bộ lịch có thể ảnh hưởng tới các chuyến ứng viên trong một
     * khoảng thời gian, để {@code selectBest*} / {@code getDrivingHoursForDate}
     * chạy trên bộ nhớ thay vì truy vấn DB từng xe/tài xế. Đây chỉ là bước CUNG
     * CẤP DỮ LIỆU — không chứa luật nào; mọi luật vẫn ở các method selection.
     *
     * Phạm vi nạp là SIÊU TẬP an toàn: mọi chuyến chưa hủy mà khoảng thời gian có
     * thể giao với [spanStart, spanEnd] (điều kiện arrivalTimeExpected > spanStart
     * để loại các chuyến lịch sử đã kết thúc, kèm nhánh null cho chuyến thiếu giờ
     * đến). Nhờ vậy chuyến COMPLETED trong quá khứ không lọt vào (không ảnh hưởng
     * ứng viên tương lai), còn chuyến đang chạy dài vẫn được giữ.
     */
    @Transactional(readOnly = true)
    public AvailabilityContext buildAvailabilityContext(LocalDateTime spanStart, LocalDateTime spanEnd) {
        List<Trip> trips = tripRepository.findScheduleForAvailability(
                List.of(TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.COMPLETED),
                spanStart, spanEnd);
        return withCommittedKm(withLocations(trips, spanStart, spanEnd)); // km đã cam kết: CT7d, cuối lớp
    }

    // Quản lý Finite State Machine (FSM)
    private boolean canTransition(TripStatus from, TripStatus to) {
        if (from == to)
            return true; // Giữ nguyên trạng thái luôn hợp lệ

        return switch (from) {
            case PENDING_APPROVAL -> to == TripStatus.ACTIVE || to == TripStatus.CANCELLED;
            case ACTIVE -> to == TripStatus.DEPARTED || to == TripStatus.CANCELLED;
            case DEPARTED -> to == TripStatus.COMPLETED;
            default -> false; // COMPLETED và CANCELLED là Terminal States (Trạng thái cuối)
        };
    }

    /**
     * Các trạng thái mà một chuyến đang ở {@code from} được phép chuyển sang —
     * gồm cả chính {@code from} (giữ nguyên luôn hợp lệ, xem canTransition).
     *
     * Đây là điểm truy cập PUBLIC duy nhất vào whitelist FSM, thêm cho lỗi #23/#24:
     * tầng UI trước đây tự chép bảng transition ra template bằng tay (nút "Hủy"
     * hiện cả trên DEPARTED, select trạng thái liệt kê đủ 5 giá trị) rồi lệch với
     * canTransition(). Từ nay tầng UI HỎI service — nút Hủy hiện khi danh sách này
     * chứa CANCELLED, select trạng thái ở form Sửa chỉ liệt kê đúng danh sách này,
     * và updateTrip() kiểm bằng nó TRƯỚC khi ghi bất cứ gì. Bảng luật vẫn nằm
     * nguyên trong canTransition(); hàm này chỉ liệt kê nó, không định nghĩa lại
     * — cùng tiền lệ public-overload của getDrivingHoursForDate().
     *
     * Thứ tự trả về là thứ tự khai báo của enum, để select trên form ổn định.
     */
    public List<TripStatus> allowedTransitionsFrom(TripStatus from) {
        return java.util.Arrays.stream(TripStatus.values())
                .filter(to -> canTransition(from, to))
                .collect(Collectors.toList());
    }

    /**
     * Thực thi MỘT chuyển trạng thái theo whitelist FSM, kèm đồng bộ BusStatus.
     *
     * ⚠️ METHOD NÀY KHÔNG KIỂM TRA RÀNG BUỘC NGHIỆP VỤ. Nó chỉ trả lời "transition
     * này có hợp lệ không", KHÔNG gọi validateBusForTrip()/validateStaffForTrip().
     * Cổng ràng buộc nằm ở confirmAutoAssignedTrip()/approveTrip() (phê duyệt) và
     * createManualTrip()/updateManualTrip() (tạo/sửa thủ công).
     *
     * ⚠️ HỆ QUẢ CHO NGƯỜI GỌI: nếu bạn truyền newStatus = ACTIVE, chuyến sẽ được
     * kích hoạt (mở bán vé, đóng dấu saleOpenedAt) mà KHÔNG ràng buộc nào được
     * kiểm — kể cả chuyến chưa có xe/tài xế. Hiện có ĐÚNG BỐN lối gọi, cả bốn đều
     * đã tự lo:
     * <ul>
     * <li>AdminTripManagementController.updateTrip() — chạy updateManualTrip()
     * (validate đầy đủ) TRƯỚC khi gọi vào đây;</li>
     * <li>DispatchController.changeStatus() — chặn ACTIVE bằng allow-list
     * BOARD_ACTIONS;</li>
     * <li>AdminTripManagementController.cancelTrip() và TripService.rejectTrip()
     * — truyền HẰNG SỐ CANCELLED, không bao giờ chạm tới nhánh ACTIVE. (Bản trước
     * của dòng này gọi lối thứ tư là "TripService.cancelTrip()" — method không tồn
     * tại; đính chính 2026-09-19. Một tên sai ngay trong tripwire đếm call site là
     * thứ đắt nhất, vì người rà sau sẽ tin nó thay vì grep.)</li>
     * </ul>
     * Chỉ hai lối đầu mới truyền được newStatus tùy ý; hai lối sau được liệt kê để
     * lần kiểm sau đếm đủ bốn call site và không tưởng danh sách này đã lạc hậu.
     * Nếu bạn thêm lối gọi mới có thể truyền ACTIVE, PHẢI validate trước — đây
     * chính là lỗ hổng #10 trong docs/todo/current_bugs_found.md.
     */
    @Transactional
    public void updateTripStatus(Long tripId, TripStatus newStatus) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy chuyến đi với ID: " + tripId));

        // Kiểm tra tính hợp lệ theo thiết kế Whitelist FSM
        if (!canTransition(trip.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    String.format("Lỗi luồng vận hành: Không thể chuyển trạng thái chuyến xe từ [%s] sang [%s].",
                            trip.getStatus(), newStatus));
        }

        // Set lại ĐÚNG trạng thái đang có: hợp lệ (canTransition cho from == to qua,
        // xem bảng "Allowed Transitions" — dòng "Same state set again"), nhưng KHÔNG
        // phải một lần VÀO trạng thái, nên không tác dụng phụ nào được chạy. Đây
        // chính là hợp đồng mà docs/architecture/trip_lifecycle_fsm.md quy định: mọi
        // tác dụng phụ đều ghi là "Side effects on ENTRY", và state diagram không có
        // self-loop nào.
        //
        // KHÔNG BỎ GUARD NÀY. Khối đồng bộ Bus bên dưới chỉ xét newStatus, không xét
        // trạng thái cũ — thiếu guard thì gọi lại với COMPLETED sẽ cộng
        // route.distanceKm vào Bus.odometer LẦN NỮA. Lỗi thật, đã tái hiện được chỉ
        // bằng double-click nút "Hoàn thành" ở bảng điều hành; odometer sai kéo theo
        // kmSinceLastMaintenance sai → xe tốt bị needsMaintenance()/isNearMaintenance()
        // loại khỏi findBestAvailableBus(), cảnh báo bảo trì sai, và màn Đề Xuất Thay
        // Xe (70% điểm theo odometer) xếp sai thứ tự. Xem
        // docs/todo/current_bugs_found.md mục #6.
        if (trip.getStatus() == newStatus) {
            return;
        }

        if (newStatus == TripStatus.ACTIVE) {
            changeStatusToActive(trip);
        } else {
            trip.setStatus(newStatus);
        }

        // Đồng bộ BusStatus theo vòng đời của trip:
        // ACTIVE → DEPARTED: xe lên đường, không thể gán cho chuyến khác
        // DEPARTED → COMPLETED: xe hoàn thành, trả về sẵn sàng
        if (trip.getBus() != null) {
            if (newStatus == TripStatus.DEPARTED) {
                trip.getBus().setStatus(BusStatus.TRAVELING);
                busRepository.save(trip.getBus());
            } else if (newStatus == TripStatus.COMPLETED) {
                trip.getBus().setStatus(BusStatus.READY);

                // Cộng quãng đường chuyến này vào odometer thực tế
                if (trip.getRoute() != null && trip.getRoute().getDistanceKm() != null) {
                    double currentOdo = trip.getBus().getOdometer() != null
                            ? trip.getBus().getOdometer()
                            : 0.0;
                    trip.getBus().setOdometer(currentOdo + trip.getRoute().getDistanceKm());
                }

                busRepository.save(trip.getBus());
            }
        }
        tripRepository.save(trip);
    }

    /**
     * Kiểm tra tài xế có đang bận trong cửa sổ thời gian (cả với tư cách driver và
     * assistant).
     */
    private boolean isDriverBusyInWindow(Driver driver, LocalDateTime windowStart, LocalDateTime windowEnd,
            Long excludeTripId) {
        return isDriverBusyInWindow(driver, windowStart, windowEnd, excludeTripId, null);
    }

    /**
     * Bản nhận AvailabilityContext. ctx == null → ba câu SQL exists, mỗi vai trên
     * xe một câu (lỗi #35). ctx != null → xét trên các chuyến đã
     * preload: bận khi có chuyến ở trạng thái bận, dính tài xế ở BẤT KỲ vai trò
     * nào ({@link #tripInvolvesDriver}) và giao khoảng thời gian
     * ({@link #intervalsOverlap}). Package-private để test tương đương gọi được cả
     * hai nhánh.
     */
    boolean isDriverBusyInWindow(Driver driver, LocalDateTime windowStart, LocalDateTime windowEnd,
            Long excludeTripId, AvailabilityContext ctx) {
        List<TripStatus> busyStatuses = List.of(
                TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED);

        if (ctx == null) {
            // Bận với tư cách tài xế chính hoặc tài xế phụ? Mỗi vai một câu (lỗi #35)
            boolean busyAsDriver = tripRepository.existsOverlappingTripForDriver(driver, busyStatuses, windowStart, windowEnd, excludeTripId)
                    || tripRepository.existsOverlappingTripForCoDriver(driver, busyStatuses, windowStart, windowEnd, excludeTripId);
            if (busyAsDriver)
                return true;

            // Bận với tư cách phụ xe?
            return tripRepository.existsOverlappingTripForAssistant(
                    driver, busyStatuses, windowStart, windowEnd, excludeTripId);
        }

        return ctx.trips().stream().anyMatch(t -> busyStatuses.contains(t.getStatus())
                && tripInvolvesDriver(t, driver)
                && notExcluded(t, excludeTripId)
                && intervalsOverlap(t, windowStart, windowEnd));
    }

    /**
     * Tính tổng số giờ lái xe của tài xế trong một ngày cụ thể.
     */
    private double getDrivingHoursForDate(Driver driver, LocalDateTime date, Long excludeTripId) {
        return getDrivingHoursForDate(driver, date, excludeTripId, null);
    }

    /**
     * Bản nhận AvailabilityContext. ctx == null → nạp chuyến của tài xế trong ngày
     * qua DB ({@link #tripsOfPersonOnDate}, mỗi vai một câu). ctx != null → lọc các chuyến đã
     * preload theo đúng phạm vi câu SQL đó (dính tài xế, đúng ngày, đúng trạng
     * thái, không bị loại trừ). Cả hai nhánh đưa danh sách chuyến cho
     * {@link #sumDrivingHours} — luật tính giờ nằm MỘT CHỖ, không nhân bản.
     * Package-private để test tương đương gọi được cả hai nhánh.
     */
    double getDrivingHoursForDate(Driver driver, LocalDateTime date, Long excludeTripId,
            AvailabilityContext ctx) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<TripStatus> busyStatuses = List.of(TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED,
                TripStatus.COMPLETED);

        List<Trip> trips;
        if (ctx == null) {
            trips = tripsOfPersonOnDate(driver, busyStatuses, startOfDay, endOfDay, excludeTripId);
        } else {
            trips = ctx.trips().stream()
                    .filter(t -> busyStatuses.contains(t.getStatus()))
                    .filter(t -> tripInvolvesDriver(t, driver))
                    .filter(t -> notExcluded(t, excludeTripId))
                    .filter(t -> t.getDepartureTime() != null
                            && !t.getDepartureTime().isBefore(startOfDay)
                            && t.getDepartureTime().isBefore(endOfDay))
                    .collect(Collectors.toList());
        }
        return sumDrivingHours(driver, date, trips);
    }

    /**
     * Cộng giờ lái của tài xế từ MỘT danh sách chuyến đã lọc sẵn (đúng tài xế,
     * đúng ngày, đúng trạng thái). Đây là luật tính giờ DUY NHẤT của hệ thống: quy
     * ước phụ xe = 0h, chia ca cho tài xế phụ, cắt trần 8h/chuyến, và cộng giờ nền
     * mock (totalDrivingHours24h) chỉ khi date là hôm nay. Tách ra để nhánh DB lẫn
     * nhánh in-memory dùng chung, không đẻ bộ luật thứ hai.
     */
    private double sumDrivingHours(Driver driver, LocalDateTime date, List<Trip> trips) {
        double hoursFromTrips = trips.stream()
                .mapToDouble(t -> {
                    // Nếu tài xế làm phụ xe (conductor/assistant) ở chuyến này thì giờ lái = 0.0
                    if (t.getAssistant() != null && t.getAssistant().getUserId().equals(driver.getUserId())) {
                        return 0.0;
                    }

                    // Kiểm tra xem tài xế có lái (chính hoặc phụ)
                    // SAU
                    boolean isDriving = (t.getDriver() != null && t.getDriver().getUserId().equals(driver.getUserId()))
                            || (t.getCoDrivers() != null
                                    && t.getCoDrivers().stream()
                                            .anyMatch(cd -> cd.getUserId().equals(driver.getUserId())));

                    if (!isDriving) {
                        return 0.0;
                    }

                    LocalDateTime arr = t.getArrivalTimeExpected() != null ? t.getArrivalTimeExpected()
                            : t.getDepartureTime().plusHours(5);
                    double dur = Duration.between(t.getDepartureTime(), arr).toMinutes() / 60.0;

                    // Tính tổng số tài xế lái xe của chuyến này
                    int totalDrivers = 1;
                    if (t.getCoDrivers() != null) {
                        totalDrivers += t.getCoDrivers().size();
                    }

                    double durShare = dur / totalDrivers;
                    return Math.min(durShare, 8.0);
                })
                .sum();

        // -----------------------------------------------------------------------------------
        // NOTE ĐỂ BẢO VỆ ĐỒ ÁN / DELIBERATE DESIGN:
        // Trường 'totalDrivingHours24h' hiện tại đóng vai trò dữ liệu MOCK SEED từ
        // DataInitializer.
        // Nó phục vụ mục đích thiết lập giờ nền (Baseline Hours) cho các kịch bản demo
        // chạy thực tế,
        // chưa đồng bộ thời gian thực với bảng Trip. Khi tích hợp hệ thống phần cứng
        // IoT/GPS thật,
        // giá trị này sẽ được nạp thông qua một API cập nhật độc lập từ thiết bị giám
        // sát hành trình.
        // -----------------------------------------------------------------------------------
        if (date.toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
            Double baseHours = driver.getTotalDrivingHours24h();
            if (baseHours != null) {
                hoursFromTrips += baseHours;
            }
        }

        return hoursFromTrips;
    }

    /**
     * Public wrapper cho getDrivingHoursForDate() — dùng bởi DashboardService để
     * hiển thị tải giờ lái của tài xế trong ngày (Strategic Analytics, tab Tài
     * xế). Không có tripId nào cần loại trừ nên excludeTripId luôn null.
     * Chỉ expose lại logic tính toán đã có, không thêm/thay đổi business rule.
     */
    public double getDrivingHoursForDate(Driver driver, LocalDateTime date) {
        return getDrivingHoursForDate(driver, date, null);
    }

    // =========================================================================
    // APPROVAL ACTIONS
    // =========================================================================

    /**
     * Tiền điều kiện chung của HAI lối phê duyệt: chỉ chuyến đang
     * {@code PENDING_APPROVAL} mới được duyệt. Xem lỗi #20.
     *
     * VÌ SAO CẦN: cả hai lối duyệt đều kết thúc bằng changeStatusToActive(), tức
     * chúng THỰC HIỆN MỘT TRANSITION (X → ACTIVE) trên một bản ghi đã tồn tại,
     * nhưng lại không đi qua cổng FSM updateTripStatus()/canTransition(). Whitelist
     * FSM (canTransition:536-546) chỉ cho PENDING_APPROVAL → ACTIVE; vậy mà thiếu
     * guard này, một POST tự chế duyệt được cả chuyến CANCELLED, COMPLETED hay
     * DEPARTED — đúng ba dòng mà trip_lifecycle_fsm.md §5 liệt kê trong bảng
     * "Invalid Transitions (ENFORCED)". Đã tái hiện thật trên chuyến 2749
     * (CANCELLED → ACTIVE, cả hai lối, app báo "thành công").
     *
     * VÌ SAO LỖ NÀY SỐNG QUA BA LẦN RÀ: Proj_functions_summary.md:53 miễn trừ cả
     * ba method dùng changeStatusToActive() với lý do "trường hợp tạo/duyệt —
     * không có from state cần kiểm tra transition". Câu đó ĐÚNG với
     * createManualTrip() — Trip còn transient, chưa có bản ghi nào trong DB nên
     * không có from-state để nói tới — và SAI với hai method này, vốn mở đầu bằng
     * tripRepository.findById() ⇒ nạp một bản ghi đã tồn tại, có from-state hẳn
     * hoi. Một tính chất của method thứ nhất đã bị khái quát cho cả ba. Cùng khuôn
     * với lỗi #14, nơi một câu javadoc sai đã che lỗi khỏi các lần rà trước.
     *
     * VÌ SAO KIỂM BẰNG != CHỨ KHÔNG PHẢI switch-không-default như deleteTrip():
     * deleteTrip có chính sách RIÊNG cho từng trạng thái (mỗi nhánh một thông
     * điệp), nên buộc người thêm TripStatus mới phải quyết định là đúng. Ở đây
     * luật chỉ có một vế — "duyệt được đúng từ PENDING_APPROVAL" — nên một trạng
     * thái mới rơi vào nhánh TỪ CHỐI là mặc định AN TOÀN (fail-closed) và cũng là
     * câu trả lời đúng. Sao chép hình dạng switch vào đây sẽ là bắt chước hình
     * thức, không phải giữ nhất quán về lập luận.
     *
     * KHÔNG chặn ở controller như allow-list BOARD_ACTIONS của lỗi #10: ở #10 câu
     * hỏi là "màn hình phơi ra STATUS ĐÍCH nào" — việc của controller. Ở đây đích
     * cố định (ACTIVE) và câu hỏi là về STATUS NGUỒN, tức bất biến vòng đời mà
     * trip_lifecycle_fsm.md:3 giao cho TripService giữ ("sole authoritative
     * implementation... no status change should bypass it").
     *
     * @throws IllegalStateException nếu chuyến không ở PENDING_APPROVAL — cùng
     *                               kiểu ngoại lệ mà deleteTrip() và
     *                               updateTripStatus() dùng cho vi phạm chính sách
     *                               theo trạng thái
     */
    private void requirePendingApproval(Trip trip) {
        if (trip.getStatus() != TripStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Chuyến #" + trip.getId() + " đang ở trạng thái " + trip.getStatus()
                            + ", không phải PENDING_APPROVAL nên không thể phê duyệt. "
                            + "Chỉ chuyến đang chờ duyệt mới được kích hoạt; "
                            + "quy trình vận hành sau khi chuyến đã mở bán nằm ở Bảng Điều Hành.");
        }
    }

    /**
     * Admin xác nhận chuyến đã được AI phân công đầy đủ — chỉ cần 1 click.
     * Hệ thống kiểm tra lại ràng buộc trước khi kích hoạt.
     *
     * Tiền điều kiện trạng thái nằm ở requirePendingApproval() — dùng chung với
     * approveTrip() để hai lối duyệt không thể trôi ra khác nhau, đúng khuôn
     * editRefusalReason() của lỗi #18.
     */
    @Transactional
    public String confirmAutoAssignedTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe #" + tripId));

        // Kiểm TRƯỚC mọi thứ khác: rẻ nhất, và báo đúng bệnh. Nếu để sau
        // validateBusForTrip(), một chuyến COMPLETED sai trạng thái có thể bị báo
        // thành "xe quá hạn bảo trì" — đúng là bị chặn, nhưng sai lý do.
        requirePendingApproval(trip);

        if (trip.getBus() == null) {
            throw new IllegalStateException("Chuyến chưa được phân công xe. Vui lòng dùng form phân công thủ công.");
        }
        if (trip.getDriver() == null) {
            throw new IllegalStateException(
                    "Chuyến chưa được phân công tài xế. Vui lòng dùng form phân công thủ công.");
        }

        // Kiểm tra lại toàn bộ ràng buộc
        String warning = validateBusForTrip(trip.getBus(), trip, tripId);
        validateStaffForTrip(trip, tripId);

        changeStatusToActive(trip);
        tripRepository.save(trip);

        log.info("✅ Admin xác nhận chuyến #{} | Xe: {} | Tài xế chính: {} | Số tài xế phụ: {} | Phụ xe: {}",
                tripId,
                trip.getBus().getLicensePlate(),
                trip.getDriver().getUser().getFullName(),
                trip.getCoDrivers().size(),
                trip.getAssistant() != null ? trip.getAssistant().getUser().getFullName() : "Không có");

        return warning;
    }

    /**
     * Admin phân công thủ công (dùng khi AI không tìm được tài nguyên tự động).
     * Vẫn kiểm tra ràng buộc trước khi lưu.
     *
     * Tiền điều kiện trạng thái nằm ở requirePendingApproval() — dùng chung với
     * confirmAutoAssignedTrip(). Phải kiểm TRƯỚC setBus()/setDriver() bên dưới:
     * hai setter đó ghi lên entity đang được quản lý, nên nếu chặn muộn hơn thì
     * một chuyến DEPARTED có thể bị dời con trỏ xe trước khi bị từ chối — đúng
     * thiệt hại của lỗi #14 (xe cũ kẹt TRAVELING vĩnh viễn).
     */
    @Transactional
    public String approveTrip(Long tripId, Long busId, Long driverId, Long assistantId,
            java.util.List<Long> coDriverIds) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe #" + tripId));

        requirePendingApproval(trip);

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy xe #" + busId));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế #" + driverId));

        trip.setBus(bus);
        trip.setDriver(driver);

        // SỐ GHẾ THEO XE ĐƯỢC CHỌN — cùng luật với đường AI tự phân công
        // (createExtraTrip đặt totalSeats = sức chứa xe AI chọn). Chuyến chờ duyệt đi
        // tới đây mang số ghế TẠM chép từ chuyến gốc, mà màn Duyệt không có ô số ghế;
        // giữ nguyên số đó thì mọi xe nhỏ hơn trong dropdown đều bị luật #22 từ chối
        // — kể cả xe đúng loại tuyến đứng đầu có ★ (lỗi #26). Muốn bán ít hơn sức
        // chứa là quyết định kinh doanh, làm ở form Sửa chuyến sau khi kích hoạt.
        // Xe chưa gán loại thì không biết sức chứa: giữ số cũ, #22 cũng bỏ qua ca đó.
        Integer capacity = seatCapacityOf(bus);
        if (capacity != null) {
            if (capacity < trip.getTicketsSold()) {
                throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " chỉ có " + capacity
                        + " chỗ nhưng chuyến đã bán " + trip.getTicketsSold() + " vé — hãy chọn xe lớn hơn!");
            }
            trip.setTotalSeats(capacity);
        }

        trip.getCoDrivers().clear();
        if (coDriverIds != null) {
            for (Long cdId : coDriverIds) {
                if (cdId != null && cdId > 0) {
                    Driver cd = driverRepository.findById(cdId)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế phụ #" + cdId));
                    trip.getCoDrivers().add(cd);
                }
            }
        }

        if (assistantId != null && assistantId > 0) {
            Driver assistant = driverRepository.findById(assistantId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phụ xe #" + assistantId));
            trip.setAssistant(assistant);
        } else {
            trip.setAssistant(null);
        }

        String warning = validateBusForTrip(bus, trip, tripId);
        validateStaffForTrip(trip, tripId);

        changeStatusToActive(trip);
        tripRepository.save(trip);

        log.info("✅ Admin phân công thủ công chuyến #{} | Xe: {} | Tài xế: {}",
                tripId, bus.getLicensePlate(), driver.getUser().getFullName());

        return warning;
    }

    /**
     * Admin tạo chuyến thủ công từ form.
     *
     * CHỈ dành cho chuyến MỚI — entity truyền vào phải chưa có id. Đây là tripwire
     * cùng khuôn với BusService.saveBus() (lỗi #16), áp cho đường tạo chuyến vì
     * lỗi #21: AdminTripManagementController.createTrip() bind Trip bằng
     * @ModelAttribute, không có @InitBinder nào chặn field id, nên một POST tự chế
     * mang id của chuyến có sẵn sẽ tới đây với id ≠ null; tripRepository.save()
     * khi đó là MERGE — ghi đè mọi cột của chuyến đó bằng nội dung form, đặt
     * status = ACTIVE (controller set trước khi gọi), xoá ticketsSold, và đi vòng
     * qua cả editRefusalReason() (#18) lẫn requirePendingApproval() (#20). Đã tái
     * hiện thật: chuyến COMPLETED 35 vé → ACTIVE 0 vé; chuyến DEPARTED đổi xe làm
     * xe cũ kẹt TRAVELING (thiệt hại #14). Với guard này, "Trip còn transient" mà
     * trip_lifecycle_fsm.md §5.1 dùng để miễn trừ method khỏi FSM trở thành điều
     * được BẢO ĐẢM, không còn là điều được giả định.
     *
     * Ném IllegalArgumentException như mọi vi phạm đầu vào khác của class này, để
     * controller hiện flash "Lỗi: …" sẵn có.
     */
    @Transactional
    public String createManualTrip(Trip trip) {
        if (trip.getId() != null) {
            throw new IllegalArgumentException(
                    "createManualTrip() chỉ dùng để tạo chuyến mới (id phải trống). Chuyến #" + trip.getId()
                            + " đã tồn tại — muốn sửa thì dùng chức năng Sửa chuyến.");
        }

        if (trip.getArrivalTimeExpected() != null && trip.getArrivalTimeExpected().isBefore(trip.getDepartureTime())) {
            throw new IllegalArgumentException("Thời gian đến dự kiến phải sau thời gian khởi hành!");
        }

        // Kiểm tra ràng buộc cho chuyến mới (tripId = null)
        String warning = validateBusForTrip(trip.getBus(), trip, null);
        validateStaffForTrip(trip, null);

        changeStatusToActive(trip);
        tripRepository.save(trip);

        return warning;
    }

    /**
     * Admin cập nhật chuyến thủ công từ form
     */
    @Transactional
    public String updateManualTrip(Trip existingTrip) {
        if (existingTrip.getArrivalTimeExpected() != null
                && existingTrip.getArrivalTimeExpected().isBefore(existingTrip.getDepartureTime())) {
            throw new IllegalArgumentException("Thời gian đến dự kiến phải sau thời gian khởi hành!");
        }

        if (existingTrip.getTotalSeats() < existingTrip.getTicketsSold()) {
            throw new IllegalArgumentException("Tổng số ghế (" + existingTrip.getTotalSeats()
                    + ") không được nhỏ hơn số vé đã bán (" + existingTrip.getTicketsSold() + ")!");
        }

        // Kiểm tra ràng buộc (loại trừ chính chuyến này)
        String warning = validateBusForTrip(existingTrip.getBus(), existingTrip, existingTrip.getId());
        validateStaffForTrip(existingTrip, existingTrip.getId());

        tripRepository.save(existingTrip);

        return warning;
    }

    /**
     * Kiểm tra toàn bộ ràng buộc liên quan đến xe trước khi gán vào chuyến.
     *
     * Chặn cứng (throw IllegalArgumentException) nếu:
     * - Xe đang REPAIRING.
     * - Xe mang cờ TRAVELING mà KHÔNG có chuyến DEPARTED nào giải thích cờ đó
     * (trạng thái xe lệch với lịch chuyến — xem lỗi #19). Nếu có chuyến đang
     * chạy giải thích, việc xe có nhận thêm chuyến được hay không do luật cửa
     * sổ thời gian ngay bên dưới quyết định, KHÔNG do cái cờ.
     * - Xe đang bận (trùng lịch) trong cửa sổ [departure - 1h, arrival + 1h].
     * - Xe đã QUÁ HẠN bảo trì (needsMaintenance() == true ngay hiện tại).
     * - Quãng đường của chuyến này sẽ đẩy xe vào vùng SẮP/QUÁ hạn bảo trì
     * (>= 90% maintenanceThreshold sau khi cộng route.distanceKm).
     * - Số ghế mở bán (totalSeats) lớn hơn sức chứa của xe theo loại xe (lỗi #22;
     * bỏ qua khi xe chưa gán loại).
     *
     * Giữ kiểu trả về String (warning) để tương thích với các nơi gọi hiện tại
     * (flash message "warning" ở Controller) — hiện tại luôn trả về null vì
     * ràng buộc bảo trì đã chuyển từ "cảnh báo" sang "chặn cứng"; kênh này vẫn
     * hữu ích nếu sau này cần thêm cảnh báo không-chặn khác (ví dụ bằng lái tài
     * xế sắp hết hạn).
     */
    private String validateBusForTrip(Bus bus, Trip trip, Long excludeTripId) {
        if (bus.getStatus() == BusStatus.REPAIRING) {
            throw new IllegalArgumentException(
                    "Xe " + bus.getLicensePlate() + " đang được bảo trì/sửa chữa, không thể gán vào chuyến!");
        }
        // Xe mang cờ TRAVELING: chỉ chặn khi KHÔNG có chuyến nào đang chạy giải
        // thích cho cái cờ đó. Xem lỗi #19.
        //
        // Bản cũ hỏi sai câu. Nó kiểm `excludeTripId.equals(trip.getId())` và đặt
        // tên biến là "travelingForThisTrip" — một khẳng định về CHIẾC XE — nhưng
        // biểu thức không hỏi gì về chiếc xe, chỉ hỏi "có đang validate chính
        // chuyến này không", điều luôn đúng ở 3/4 lối gọi. Kết quả: cùng một chiếc
        // xe TRAVELING, /trips/create từ chối còn /trips/approve nhận.
        //
        // Vì sao KHÔNG chặn mọi xe TRAVELING (phương án "siết", đã thử và bỏ):
        // isBusBusy() bên dưới ĐÃ chặn ca cửa sổ thời gian giao nhau. Chặn thêm
        // theo cờ chỉ thêm được ca KHÔNG giao nhau — tức xếp xe cho chuyến tuần
        // sau trong khi hôm nay nó đang chạy — vốn là vận hành bình thường. Đo
        // 2026-08-12: 2 chuyến mang hình dạng này (8 trên xe 20, 14 trên xe 7);
        // chuyến 8 đã sửa được thật trên app sau bản vá này, còn chuyến 14 vốn đã
        // không lưu được vì một luật KHÁC (giờ lái 8h/ngày của tài xế chính), nên
        // siết chỉ thêm cho nó một lý do chặn thứ hai. Bán kính mà siết THÊM VÀO
        // là 1 chuyến. Quan trọng hơn con số: showEditTripForm() cưỡng bức thêm xe
        // hiện tại vào dropdown ⇒ siết thì form sẽ MỜI đúng chiếc xe nó sắp từ
        // chối, phá bất biến một chiều của #12.
        //
        // Vì sao KHÔNG bỏ hẳn nhánh này (phương án "nới"): cờ này là tín hiệu ĐỘC
        // LẬP với bảng trips. §9 của roadmap ghi rõ Bus.status có hai người ghi
        // (FSM và BusService) và có thể lệch với lịch chuyến. Một chiếc xe mang cờ
        // TRAVELING mà không chuyến DEPARTED nào giải thích thì hoặc là dữ liệu đã
        // lệch, hoặc là admin tự đặt tay — cả hai trường hợp đều KHÔNG nên xếp lịch
        // tiếp. Fail-closed ở đây là đúng.
        //
        // Vì sao query KHÔNG cần vế loại trừ chuyến hiện tại: chuyến đang được
        // validate không bao giờ ở trạng thái DEPARTED — #18 chặn sửa chuyến đã
        // xuất phát, #20 chỉ cho duyệt chuyến PENDING_APPROVAL, còn createManualTrip
        // thì chuyến chưa tồn tại. Nên mọi chuyến DEPARTED tìm thấy chắc chắn là
        // chuyến KHÁC. Đây đúng là điều mục #19 dự đoán khi hoãn nó lại sau #14.
        // Dùng lại existsByBusIdAndStatusIn() có sẵn (BusService.updateBus đang
        // dùng cho guard #15), không thêm query mới.
        if (bus.getStatus() == BusStatus.TRAVELING) {
            boolean explainedByARunningTrip = // một định nghĩa, dùng chung với danh sách xe của lịch thường lệ (CT6)
                    travelingFlagExplained(bus);
            if (!explainedByARunningTrip) {
                throw new IllegalArgumentException("Xe " + bus.getLicensePlate()
                        + " đang mang trạng thái TRAVELING nhưng không có chuyến nào đang chạy để giải thích"
                        + " — trạng thái xe đang không nhất quán với lịch chuyến. Hãy đóng chuyến còn treo ở"
                        + " Bảng Điều Hành, hoặc sửa lại trạng thái xe ở màn Quản Lý Xe, trước khi xếp lịch mới!");
            }
        }

        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);

        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        if (isBusBusy(bus, windowStart, windowEnd, excludeTripId)) {
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " đang bận trong khoảng thời gian này!");
        }

        // RÀNG BUỘC BẢO TRÌ: Xe đã quá hạn bảo trì (kmSinceLastMaintenance >=
        // maintenanceThreshold NGAY BÂY GIỜ, chưa cần tính chuyến này) thì
        // tuyệt đối KHÔNG được gán vào bất kỳ chuyến nào — đúng theo yêu cầu
        // "Xe: Không gán xe đang trong lịch bảo trì" của functional spec.
        if (bus.needsMaintenance() && !servicedBefore(bus, departure)) { // CT7d: trừ khi lịch đã có ngày bảo trì trước chuyến
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " (Odo: "
                    + Math.round(bus.getKmSinceLastMaintenance()) + "km) đã QUÁ HẠN bảo trì (ngưỡng: "
                    + Math.round(bus.getMaintenanceThreshold())
                    + "km) — không thể gán vào chuyến cho đến khi được bảo trì!");
        }
        // CT7d (CM-25): ngày bảo trì dự kiến giữ xe ở bến nhà cả ngày — không chuyến nào chạm vào ngày đó.
        if (onPlannedMaintenanceDay(bus, departure, arrival)) {
            throw new IllegalArgumentException(plannedMaintenanceRefusal(bus));
        }

        // RÀNG BUỘC BẢO TRÌ: Nếu quãng đường của CHUYẾN NÀY sẽ đẩy xe vào vùng
        // "sắp đến hạn" (>= 90% maintenanceThreshold, kể cả vượt quá 100%) thì
        // cũng chặn — không chỉ cảnh báo. Điều này khiến hành vi nhất quán với
        // findBestAvailableBus() (AI auto-assign), nơi xe sắp/quá hạn chỉ được
        // dùng làm fallback cuối cùng kèm log cảnh báo, không nên trở thành lựa
        // chọn hợp lệ ở luồng thủ công (Create/Edit) trong khi luồng AI từ chối.
        double tripDistance = trip.getRoute() != null && trip.getRoute().getDistanceKm() != null
                ? trip.getRoute().getDistanceKm()
                : 0.0;
        if (bus.reachesNearMaintenance(kmSinceMaintenanceAt(bus, departure) + tripDistance)) { // CT7d: 0 km sau ngày bảo trì dự kiến
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " (Odo: "
                    + Math.round(kmSinceMaintenanceAt(bus, departure)) + "km) sẽ SẮP/QUÁ ngưỡng bảo trì ("
                    + Math.round(bus.getMaintenanceThreshold())
                    + "km) sau chuyến này — vui lòng chọn xe khác hoặc đưa xe đi bảo trì trước!");
        }

        // RÀNG BUỘC SỨC CHỨA: số ghế mở bán không được vượt số chỗ thật của xe (lỗi
        // #22). Đây là chỗ DUY NHẤT giữ luật này, nên nó phủ mọi đường ghi vào
        // totalSeats: tạo thủ công, sửa, hai lối phê duyệt, và cổng dry-run của
        // Recommendation. Bán ÍT hơn sức chứa vẫn hợp lệ — đó là quyết định kinh
        // doanh; bán NHIỀU hơn là bán ghế không tồn tại. Xe chưa gán loại thì không
        // có sức chứa để so, bỏ qua thay vì đoán (cùng cách các luật bảo trì bỏ qua
        // khi maintenanceThreshold == null).
        Integer capacity = seatCapacityOf(bus);
        if (capacity != null && trip.getTotalSeats() > capacity) {
            throw new IllegalArgumentException("Chuyến mở bán " + trip.getTotalSeats() + " ghế nhưng xe "
                    + bus.getLicensePlate() + " (" + bus.getBusType().getTypeName() + ") chỉ có " + capacity
                    + " chỗ — hãy giảm số ghế của chuyến hoặc chọn xe lớn hơn!");
        }

        return null;
    }

    /**
     * Sức chứa (số chỗ thật) của xe theo loại xe, hoặc null nếu xe chưa được gán
     * loại. Dùng chung cho luật sức chứa trong validateBusForTrip() và cho việc
     * đặt số ghế theo xe được gán — createExtraTrip() (AI), approveTrip() (phân
     * công thủ công) và lịch thường lệ (CT6, RegularTimetableService) — một định nghĩa.
     */
    static Integer seatCapacityOf(Bus bus) {
        return bus.getBusType() != null ? bus.getBusType().getCapacity() : null;
    }

    /**
     * Kiểm tra toàn bộ ràng buộc nhân sự (tài xế chính, các tài xế phụ, phụ xe).
     */
    private void validateStaffForTrip(Trip trip, Long excludeTripId) {
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);

        double durationHours = Duration.between(departure, arrival).toMinutes() / 60.0;
        int requiredDrivers = (int) Math.ceil(durationHours / 8.0);
        if (requiredDrivers < 1)
            requiredDrivers = 1;

        // 1. Kiểm tra tài xế chính
        Driver driver = trip.getDriver();
        if (driver == null) {
            throw new IllegalArgumentException("Chuyến xe bắt buộc phải có tài xế chính!");
        }

        int assignedDriversCount = 1;
        if (trip.getCoDrivers() != null) {
            assignedDriversCount += trip.getCoDrivers().size();
        }

        // 2. Kiểm tra số lượng tài xế cho chuyến xe dài
        if (assignedDriversCount < requiredDrivers) {
            throw new IllegalArgumentException(String.format(
                    "Chuyến xe kéo dài %.1fh, yêu cầu ít nhất %d tài xế lái xe, nhưng hiện tại chỉ gán %d người!",
                    durationHours, requiredDrivers, assignedDriversCount));
        }

        // 3. Nếu chuyến dài hơn 8 tiếng, bắt buộc phải có phụ xe
        Driver assistant = trip.getAssistant();
        if (durationHours > 8.0 && assistant == null) {
            throw new IllegalArgumentException("Chuyến xe kéo dài trên 8 tiếng bắt buộc phải có phụ xe!");
        }

        // 4. Kiểm tra trùng lặp nhân sự
        java.util.Set<Long> staffIds = new java.util.HashSet<>();
        staffIds.add(driver.getUserId());

        if (trip.getCoDrivers() != null) {
            for (Driver cd : trip.getCoDrivers()) {
                if (cd.getUserId().equals(driver.getUserId())) {
                    throw new IllegalArgumentException(
                            "Nhân sự trùng lặp: Tài xế phụ "
                                    + cd.getUser().getFullName() + " đang là tài xế chính của chuyến này!");
                }
                if (!staffIds.add(cd.getUserId())) {
                    throw new IllegalArgumentException(
                            "Nhân sự trùng lặp: Tài xế phụ "
                                    + cd.getUser().getFullName() + " được chọn nhiều hơn một lần!");
                }
            }
        }

        if (assistant != null) {
            if (assistant.getUserId().equals(driver.getUserId())) {
                throw new IllegalArgumentException(
                        "Nhân sự trùng lặp: Phụ xe "
                                + assistant.getUser().getFullName() + " đang là tài xế chính của chuyến này!");
            }
            if (!staffIds.add(assistant.getUserId())) {
                throw new IllegalArgumentException(
                        "Nhân sự trùng lặp: Phụ xe "
                                + assistant.getUser().getFullName()
                                + " đã được phân công làm tài xế phụ trong chuyến này!");
            }
        }

        // 5. Kiểm tra hạn bằng lái & độ bận & tổng giờ chạy của từng tài xế (chính +
        // phụ)
        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        double shareDuration = durationHours / assignedDriversCount;
        double effectiveHours = Math.min(shareDuration, 8.0);

        // Validate tài xế chính
        if (!Boolean.TRUE.equals(driver.getIsActive())) {
            throw new IllegalArgumentException(
                    "Tài xế chính " + driver.getUser().getFullName() + " đã bị khóa (ngừng hoạt động)!");
        }
        if (!driver.isLicenseValid(departure.toLocalDate())) {
            throw new IllegalArgumentException(String.format(
                    "Bằng lái của tài xế chính %s hết hạn ngày %s — không còn hiệu lực vào ngày khởi hành %s!",
                    driver.getUser().getFullName(), driver.getLicenseExpiryDate(), departure.toLocalDate()));
        }
        if (isDriverBusyInWindow(driver, windowStart, windowEnd, excludeTripId)) {
            throw new IllegalArgumentException(
                    "Tài xế chính " + driver.getUser().getFullName() + " đang bận ở chuyến khác!");
        }
        double driverAssignedHours = getDrivingHoursForDate(driver, departure, excludeTripId);
        if (driverAssignedHours + effectiveHours > 8.0) {
            throw new IllegalArgumentException(String.format(
                    "Tài xế chính %s đã được phân công lái %.1fh trong ngày %s, thêm chuyến này (%.1fh) sẽ vượt 8h/ngày!",
                    driver.getUser().getFullName(), driverAssignedHours, departure.toLocalDate().toString(),
                    effectiveHours));
        }

        // Validate các tài xế phụ
        if (trip.getCoDrivers() != null) {
            for (Driver cd : trip.getCoDrivers()) {
                if (!Boolean.TRUE.equals(cd.getIsActive())) {
                    throw new IllegalArgumentException(
                            "Tài xế phụ " + cd.getUser().getFullName() + " đã bị khóa (ngừng hoạt động)!");
                }
                if (!cd.isLicenseValid(departure.toLocalDate())) {
                    throw new IllegalArgumentException(String.format(
                            "Bằng lái của tài xế phụ %s hết hạn ngày %s — không còn hiệu lực vào ngày khởi hành %s!",
                            cd.getUser().getFullName(), cd.getLicenseExpiryDate(), departure.toLocalDate()));
                }
                if (isDriverBusyInWindow(cd, windowStart, windowEnd, excludeTripId)) {
                    throw new IllegalArgumentException(
                            "Tài xế phụ " + cd.getUser().getFullName() + " đang bận ở chuyến khác!");
                }
                double cdAssignedHours = getDrivingHoursForDate(cd, departure, excludeTripId);
                if (cdAssignedHours + effectiveHours > 8.0) {
                    throw new IllegalArgumentException(String.format(
                            "Tài xế phụ %s đã được phân công lái %.1fh trong ngày %s, thêm chuyến này (%.1fh) sẽ vượt 8h/ngày!",
                            cd.getUser().getFullName(), cdAssignedHours, departure.toLocalDate().toString(),
                            effectiveHours));
                }
            }
        }
        // Validate phụ xe
        if (assistant != null) {
            if (!Boolean.TRUE.equals(assistant.getIsActive())) {
                throw new IllegalArgumentException(
                        "Phụ xe " + assistant.getUser().getFullName() + " đã bị khóa (ngừng hoạt động)!");
            }
            // Phụ xe cũng là Driver nên cũng phải còn bằng lái hiệu lực VÀO NGÀY KHỞI
            // HÀNH — cùng quy tắc, cùng mốc thời gian như tài xế chính/phụ ở trên, và
            // khớp với bộ lọc ở findBestAvailableDriver()/getAvailableAssistantsForTrip().
            if (!assistant.isLicenseValid(departure.toLocalDate())) {
                throw new IllegalArgumentException(String.format(
                        "Bằng lái của phụ xe %s hết hạn ngày %s — không còn hiệu lực vào ngày khởi hành %s!",
                        assistant.getUser().getFullName(), assistant.getLicenseExpiryDate(), departure.toLocalDate()));
            }
            if (isDriverBusyInWindow(assistant, windowStart, windowEnd, excludeTripId)) {
                throw new IllegalArgumentException(
                        "Phụ xe " + assistant.getUser().getFullName() + " đang bận ở chuyến khác!");
            }
        }
    }

    // =========================================================================
    // BUSINESS RULE VALIDATION — DRY-RUN (không throw)
    // =========================================================================

    /**
     * Bản dry-run của validateBusForTrip(): trả về kết quả dưới dạng dữ liệu
     * thay vì ném exception.
     *
     * Đây là WRAPPER THUẦN TÚY — nó gọi đúng validator gốc, không sao chép,
     * không sửa, không bổ sung một ràng buộc nào. Mọi thay đổi nghiệp vụ sau
     * này chỉ cần sửa ở validateBusForTrip() và bản dry-run tự động đi theo,
     * nên hai luồng không bao giờ lệch nhau.
     *
     * Dành cho tầng Decision Support (Phase 7) — nơi việc một xe không hợp lệ
     * là kết quả BÌNH THƯỜNG cần ghi lại, không phải sự cố cần exception. Luồng
     * throw hiện tại (createManualTrip / updateManualTrip / approveTrip /
     * confirmAutoAssignedTrip) không đổi và không gọi qua đây.
     *
     * CHỈ BÁO ĐƯỢC MỘT LỖI: validator gốc fail-fast (throw ngay ràng buộc đầu
     * tiên bị vi phạm) nên không tồn tại lỗi thứ hai để trả về. Đây là quyết
     * định đã chốt của Phase 3, không phải thiếu sót — cổng xác nhận ở Phase 7
     * chỉ cần biết đạt/không đạt.
     *
     * CHỈ BẮT IllegalArgumentException — đúng loại mà validator dùng để báo vi
     * phạm nghiệp vụ. Các exception khác (NullPointerException khi bus == null,
     * lỗi truy cập dữ liệu...) là LỖI KỸ THUẬT và vẫn được ném ra ngoài: nuốt
     * chúng thành "không hợp lệ" sẽ biến một bug thành một lý do nghiệp vụ giả.
     *
     * Yêu cầu gọi trong phạm vi transaction/session đang mở (giống mọi method
     * đọc khác trong class này) vì validator truy cập quan hệ lazy.
     *
     * @param bus           xe cần kiểm tra — KHÔNG được null (validator gốc
     *                      dereference ngay, sẽ ném NullPointerException)
     * @param trip          chuyến đang xét
     * @param excludeTripId bỏ qua chính chuyến này khi kiểm tra trùng lịch
     * @return pass (kèm warning nếu validator gốc trả về) hoặc fail kèm đúng
     *         thông điệp gốc
     */
    public ValidationResult validateBusForTripDryRun(Bus bus, Trip trip, Long excludeTripId) {
        try {
            return ValidationResult.pass(validateBusForTrip(bus, trip, excludeTripId));
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail(e.getMessage());
        }
    }

    /**
     * Bản dry-run của validateStaffForTrip(): trả về kết quả dưới dạng dữ liệu
     * thay vì ném exception.
     *
     * Mọi ghi chú của validateBusForTripDryRun() ở trên đều áp dụng nguyên vẹn
     * cho method này (wrapper thuần túy, một lỗi duy nhất, chỉ bắt
     * IllegalArgumentException, cần session đang mở).
     *
     * Khác một điểm: validator gốc trả về void nên ở đây không có kênh warning
     * để chuyển tiếp — hợp lệ luôn là pass() rỗng.
     *
     * Lưu ý khi đọc kết quả: thiếu tài xế chính ĐƯỢC validator gốc báo bằng
     * IllegalArgumentException ("Chuyến xe bắt buộc phải có tài xế chính!") nên
     * ra fail bình thường — khác với trường hợp bus == null ở method trên vốn
     * ném NullPointerException. Sự bất đối xứng này nằm trong validator gốc,
     * Phase 3 cố tình không sửa (xem Developer Notes).
     *
     * @param trip          chuyến kèm tài xế chính / tài xế phụ / phụ xe cần kiểm tra
     * @param excludeTripId bỏ qua chính chuyến này khi kiểm tra trùng lịch
     * @return pass hoặc fail kèm đúng thông điệp gốc
     */
    public ValidationResult validateStaffForTripDryRun(Trip trip, Long excludeTripId) {
        try {
            validateStaffForTrip(trip, excludeTripId);
            return ValidationResult.pass();
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail(e.getMessage());
        }
    }

    /**
     * Admin từ chối chuyến tăng cường (AI đề xuất sai, không còn cần thiết).
     * Delegate sang updateTripStatus() để FSM canTransition() được thực thi.
     * Trạng thái hợp lệ để từ chối: PENDING_APPROVAL → CANCELLED (whitelist FSM cho
     * phép).
     */
    @Transactional
    public void rejectTrip(Long tripId) {
        updateTripStatus(tripId, TripStatus.CANCELLED);
        log.info("❌ Admin từ chối chuyến tăng cường #{}", tripId);
    }

    // =========================================================================
    // QUERY METHODS (dùng cho UI form)
    // =========================================================================

    /**
     * Lấy xe sẵn sàng, không bận, và chưa quá hạn/sắp đến hạn bảo trì cho
     * chuyến (dùng cho form phân công thủ công — AdminTripController approve
     * form). Áp dụng cùng ràng buộc bảo trì với validateBusForTrip() để dropdown
     * không bao giờ hiển thị một xe mà hệ thống sẽ từ chối khi submit.
     *
     * LOẠI XE CỦA TUYẾN LÀ GỢI Ý, KHÔNG PHẢI LUẬT (mục Group C(a), chủ dự án chốt
     * 2026-09-23): validateBusForTrip() không kiểm loại xe, form Tạo không lọc
     * loại xe, nên dropdown này trước đây lọc cứng theo route.suitableBusType là
     * mời HẸP hơn thứ hệ thống nhận — cùng một chuyến, form Tạo cho chọn xe Ghế
     * ngồi, form Sửa thì không. Nay mời mọi loại, xe ĐÚNG loại tuyến được xếp
     * lên đầu (rồi mới tới km kể từ bảo trì) và template gắn nhãn cho chúng.
     * Rủi ro thật của việc chọn sai loại — bán nhiều ghế hơn số chỗ — đã do luật
     * sức chứa #22 trong validateBusForTrip() chặn.
     *
     * Thứ tự nằm ở {@link #preferredTypeThenLeastWorn} — cùng thứ tự mà
     * findBestAvailableBus() (AI) dùng để chọn xe đầu tiên. (Đến 2026-09-24 AI còn
     * lọc cứng theo loại; chủ dự án chốt AI cũng coi loại xe là gợi ý — mục #26.)
     *
     * Không lọc theo sức chứa: ở form Sửa số ghế sửa được cùng lúc; ở màn Duyệt,
     * approveTrip() đặt số ghế theo xe được chọn nên xe nào cũng hợp với luật #22.
     */
    public List<Bus> getAvailableBusesForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        BusType preferredType = trip.getRoute().getSuitableBusType();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);

        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        double tripDistance = trip.getRoute() != null && trip.getRoute().getDistanceKm() != null
                ? trip.getRoute().getDistanceKm()
                : 0.0;

        return busRepository.findByStatus(BusStatus.READY).stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, tripId))
                .filter(bus -> !onPlannedMaintenanceDay(bus, departure, arrival)) // CT7d: cùng hai luật bảo trì của validator
                .filter(bus -> !bus.reachesNearMaintenance(kmSinceMaintenanceAt(bus, departure) + tripDistance))
                .sorted(fittingBusesFirst(trip).thenComparing(preferredTypeThenLeastWorn(preferredType)))
                .collect(Collectors.toList());
    }

    /** Xe có đúng loại này không (so theo id; tuyến không quy định loại thì không xe nào "đúng loại"). */
    private static boolean isOfType(Bus bus, BusType type) {
        return type != null && bus.getBusType() != null && type.getId().equals(bus.getBusType().getId());
    }

    /**
     * Lấy tài xế hợp lệ & rảnh cho chuyến — dùng cho dropdown "Tài xế chính" /
     * "Tài xế phụ" ở form phân công thủ công (họ đều trực tiếp lái xe nên vẫn
     * chịu ràng buộc giờ lái tối đa 8h/ngày).
     */
    public List<Driver> getAvailableDriversForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);
        double durationHours = Duration.between(departure, arrival).toMinutes() / 60.0;
        double effectiveHours = driverShareHours(durationHours);

        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(d -> d.isLicenseValid(departure.toLocalDate()))
                .filter(d -> getDrivingHoursForDate(d, departure, tripId) + effectiveHours <= 8.0)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, tripId))
                .sorted(fittingPeopleFirst(trip).thenComparingDouble(drivingHoursKey(departure, tripId)))
                .collect(Collectors.toList());
    }

    /**
     * Lấy tài xế hợp lệ & rảnh để làm PHỤ XE cho chuyến — dùng riêng cho dropdown
     * "Phụ xe" ở form phân công thủ công.
     *
     * Khác với getAvailableDriversForTrip(): KHÔNG áp ràng buộc "tổng giờ lái +
     * chuyến mới ≤ 8h" vì phụ xe không trực tiếp lái xe (xem
     * getDrivingHoursForDate() — vai trò assistant luôn được tính 0.0h). Vẫn giữ
     * ràng buộc bằng lái còn hạn và không trùng lịch (phụ xe vẫn cần có mặt thực
     * tế trên xe, không thể ở 2 chuyến cùng lúc).
     */
    public List<Driver> getAvailableAssistantsForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);

        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(d -> d.isLicenseValid(departure.toLocalDate()))
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, tripId))
                .sorted(fittingPeopleFirst(trip).thenComparingDouble(drivingHoursKey(departure, tripId)))
                .collect(Collectors.toList());
    }

    /**
     * Tìm tất cả xe READY, KHÔNG BỊ TRÙNG LỊCH, và chưa quá hạn/sắp đến hạn bảo
     * trì trong khoảng [departure, arrival].
     *
     * Được gọi bởi TripRestController để cấp dữ liệu động cho Wizard Form.
     * Khác với getAvailableBusesForTrip(tripId) — method này KHÔNG cần tripId
     * có trước; nó dùng trực tiếp khung thời gian từ frontend.
     *
     * Buffer chuẩn bị xe (BUS_PREP_BUFFER_HOURS) vẫn được áp dụng đúng kiến trúc.
     *
     * LƯU Ý: Tại thời điểm này route/distanceKm CHƯA được biết (form chưa chọn
     * tuyến cụ thể truyền vào đây), nên không thể tính chính xác "km sau chuyến
     * này sẽ là bao nhiêu" như validateBusForTrip(). Áp dụng isNearMaintenance(0)
     * — tức kiểm tra tình trạng HIỆN TẠI của xe (chưa cộng thêm km chuyến) — để
     * loại các xe đã ở vùng cảnh báo ngay từ bước hiển thị; ràng buộc đầy đủ
     * (có tính quãng đường tuyến) vẫn được validateBusForTrip() chặn cứng ở
     * bước lưu, đảm bảo không xe nào lách qua được.
     *
     * @param departure Thời gian khởi hành dự kiến
     * @param arrival   Thời gian đến dự kiến (đã tính từ Route.estimatedDuration)
     * @return Danh sách xe rảnh, sắp xếp theo km kể từ bảo trì (xe "mới" nhất
     *         trước)
     */
    public List<Bus> getAvailableBusesForTimeRange(LocalDateTime departure, LocalDateTime arrival) {
        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        // Lấy tất cả xe READY (không lọc loại xe vì form này chưa biết tuyến nào)
        return busRepository.findAllWithBusType().stream()
                .filter(bus -> bus.getStatus() == giang.com.BusManagement.domain.BusStatus.READY)
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, null))
                .filter(bus -> !onPlannedMaintenanceDay(bus, departure, arrival)) // CT7d: như validator
                .filter(bus -> !bus.reachesNearMaintenance(kmSinceMaintenanceAt(bus, departure)))
                .sorted(Comparator.comparingDouble(Bus::getKmSinceLastMaintenance))
                .collect(Collectors.toList());
    }

    /**
     * Tìm tất cả tài xế ACTIVE, bằng lái còn hạn, KHÔNG TRÙNG LỊCH trong khoảng
     * [departure, arrival].
     *
     * Được gọi bởi TripRestController để cấp dữ liệu động cho Wizard Form.
     * Áp dụng cùng logic ràng buộc như getAvailableDriversForTrip(tripId):
     * - Bằng lái còn hạn
     * - Tổng giờ lái hôm nay + phần chia của chuyến này ≤ 8h
     * - Không bận ở chuyến khác (kể cả vai trò phụ xe)
     *
     * CHỈ dành cho dropdown TÀI XẾ CHÍNH và TÀI XẾ PHỤ — hai vai trò thực sự cầm
     * lái, và validateStaffForTrip() áp trần 8h cho cả hai. Dropdown PHỤ XE phải
     * dùng getAvailableAssistantsForTimeRange() ngay dưới; đổ danh sách này vào đó
     * chính là lỗi #13.
     *
     * @param departure Thời gian khởi hành dự kiến
     * @param arrival   Thời gian đến dự kiến
     * @return Danh sách tài xế rảnh, sắp xếp theo tổng giờ lái ít nhất
     */
    public List<Driver> getAvailableDriversForTimeRange(LocalDateTime departure, LocalDateTime arrival) {
        double durationHours = Duration.between(departure, arrival).toMinutes() / 60.0;
        // effectiveHours: phần giờ mà mỗi tài xế phải lái (cắt tối đa 8h/người)
        double effectiveHours = driverShareHours(durationHours);

        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(d -> d.isLicenseValid(departure.toLocalDate()))
                .filter(d -> getDrivingHoursForDate(d, departure, null) + effectiveHours <= 8.0)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null))
                .sorted(Comparator.comparingDouble(drivingHoursKey(departure, null)))
                .collect(Collectors.toList());
    }

    /**
     * Tìm tài xế hợp lệ & rảnh để làm PHỤ XE trong khoảng [departure, arrival] —
     * bản dành cho form TẠO chuyến, nơi chuyến chưa tồn tại nên chưa có tripId.
     *
     * CÙNG BỘ LUẬT NGHIỆP VỤ với getAvailableAssistantsForTrip() (màn Phê Duyệt),
     * nhưng KHÔNG phải bản sao của nó. Hai điểm khác nhau thuần cơ học, mỗi cái
     * đúng cho ngữ cảnh của mình — ghi ra đây để không ai "hợp nhất" hai method
     * bằng mắt, và để câu trên không bị đọc thành một khẳng định rộng hơn sự thật:
     * <ol>
     * <li>{@code excludeTripId = null}, vì chưa có chuyến nào để loại khỏi phép
     * kiểm trùng lịch — bản kia truyền tripId của chính chuyến đang xét;</li>
     * <li>nạp qua {@code findAllWithUser()} chứ không {@code findAll()}, vì
     * TripRestController đọc {@code getUser().getFullName()} để dựng JSON nên
     * {@code findAll()} sẽ sinh N+1 query. Bản kia trả entity ra Model, nơi
     * open-in-view che khuất chi phí đó.</li>
     * </ol>
     *
     * Khác getAvailableDriversForTimeRange() ở ĐÚNG MỘT ĐIỂM: KHÔNG áp trần "giờ
     * lái + phần chia của chuyến ≤ 8h". Phụ xe không trực tiếp lái xe — quy ước
     * "phụ xe = 0h" của getDrivingHoursForDate() là luật tính giờ duy nhất của hệ
     * thống — và validateStaffForTrip() cũng KHÔNG áp trần đó cho vai trò này.
     *
     * Ba ràng buộc còn lại giữ nguyên và khớp MỘT-MỘT với validator (còn hoạt
     * động, bằng lái còn hiệu lực VÀO NGÀY KHỞI HÀNH, không trùng lịch trong cửa
     * sổ ±MIN_REST_BETWEEN_TRIPS_MINUTES), nên danh sách này không bao giờ mời một
     * người mà validateStaffForTrip() sẽ từ chối — quy tắc một chiều ở
     * THESIS_ROADMAP.md §8 vẫn được giữ.
     *
     * Hệ quả: tập trả về luôn là SIÊU TẬP của getAvailableDriversForTimeRange()
     * với cùng tham số. Xem docs/todo/current_bugs_found.md mục #13.
     *
     * @param departure Thời gian khởi hành dự kiến
     * @param arrival   Thời gian đến dự kiến
     * @return Danh sách phụ xe khả dụng, sắp xếp theo tổng giờ lái ít nhất
     */
    public List<Driver> getAvailableAssistantsForTimeRange(LocalDateTime departure, LocalDateTime arrival) {
        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(d -> d.isLicenseValid(departure.toLocalDate()))
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null))
                .sorted(Comparator.comparingDouble(drivingHoursKey(departure, null)))
                .collect(Collectors.toList());
    }

    /**
     * Phần giờ lái mà MỘT tài xế phải gánh trong một chuyến, tính ở mức nhân sự
     * TỐI THIỂU mà validateStaffForTrip() sẽ đòi hỏi.
     *
     * Dành riêng cho hai dropdown "tài xế rảnh" (getAvailableDriversForTrip /
     * getAvailableDriversForTimeRange). Chúng chạy TRƯỚC khi có phân công, nên
     * không biết Admin sẽ gán bao nhiêu người — nhưng số đó không hề mù mờ:
     * validateStaffForTrip() bắt buộc {@code assignedDriversCount >= requiredDrivers}
     * với {@code requiredDrivers = ceil(duration / 8h)}, nên mức tối thiểu là suy
     * ra được và cho ra phần chia LỚN NHẤT có thể — tức trường hợp xấu nhất.
     *
     * VÌ SAO KHÔNG DÙNG THẲNG {@code min(duration, 8.0)} NHƯ TRƯỚC: với chuyến dài
     * hơn 8h, công thức đó mô hình hoá kịch bản "một tài xế lái trọn chuyến" —
     * đúng cái kịch bản mà validateStaffForTrip() TỪ CHỐI thẳng (nó đòi đủ
     * requiredDrivers người). Đó không phải thận trọng, đó là không mạch lạc: nó
     * giấu khỏi Admin những tài xế mà validator sẵn sàng chấp nhận. Xem
     * docs/todo/current_bugs_found.md mục #12.
     *
     * BẤT BIẾN ĐƯỢC BẢO TOÀN: vì {@code assignedDriversCount >= requiredDrivers},
     * phần chia của validator LUÔN ≤ phần chia ở đây, nên dropdown vẫn là TẬP CON
     * của "những người validator chấp nhận" — không bao giờ mời một người rồi bị
     * từ chối lúc submit (quy tắc một chiều đã ghi ở THESIS_ROADMAP.md §8).
     *
     * KHÔNG gom chung với findBestAvailableDriver()/validateStaffForTrip(): hai
     * chỗ đó chia cho số tài xế chúng ĐÃ BIẾT (totalDriversCount /
     * assignedDriversCount), còn ở đây phải SUY RA mức tối thiểu. Cùng một trần
     * 8h nhưng khác đầu vào — xem ghi chú §9 về việc hằng số 8.0 mang ba nghĩa.
     *
     * Package-private để test chốt trực tiếp trên số học này, cùng lý do
     * {@code WhatIfSimulationService.coverableSlots} và {@code isBusBusy}.
     */
    static double driverShareHours(double durationHours) {
        int requiredDrivers = Math.max(1, (int) Math.ceil(durationHours / 8.0));
        return Math.min(durationHours / requiredDrivers, 8.0);
    }

    /**
     * Khung của chuyến chính đã có một chuyến tăng cường đang CHỜ DUYỆT chưa (route pairs CT8c) — ai tạo cũng
     * vậy: job 10 giây hay vòng dự báo của job đêm. Chuyến tăng cường đã duyệt (ACTIVE) không chặn mà góp ghế
     * vào tỉ lệ của khung; chuyến đã hủy / bị từ chối cũng không chặn, để AI được đề xuất lại (TC_AI_009).
     */
    private boolean hasPendingExtra(Trip main) {
        LocalDateTime hour = main.getDepartureTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        return tripRepository.existsPendingExtraInSlot(main.getRoute().getId(), TripStatus.PENDING_APPROVAL, hour,
                hour.plusHours(1));
    }

    /** Khoá khung (tuyến × ngày × giờ) — cùng cách gom HOUR(departureTime) của dự báo và màn Đề xuất. */
    private static String scanSlotKey(Trip trip) {
        LocalDateTime departure = trip.getDepartureTime();
        return trip.getRoute().getId() + "|" + departure.toLocalDate() + "|" + departure.getHour();
    }

    /** Chuyến chính của khung: không phải tăng cường, khởi hành sớm nhất; khung chỉ có chuyến tăng cường → null. */
    private static Trip slotMainTrip(List<Trip> slot) {
        return slot.stream().filter(t -> !t.isExtraTrip())
                .min(java.util.Comparator.comparing(Trip::getDepartureTime).thenComparing(Trip::getId)).orElse(null);
    }

    public List<Trip> getPendingTrips() {
        return tripRepository.findByStatusWithDetails(TripStatus.PENDING_APPROVAL);
    }

    /**
     * Các chuyến của Bảng Điều Hành (Dispatch Center): mọi chuyến đang trên đường
     * (DEPARTED) và các chuyến đã kích hoạt (ACTIVE) có giờ khởi hành trước mốc
     * {@code until}.
     *
     * Tập trạng thái hiển thị nằm ở đây chứ không ở controller — cùng lý do
     * getPendingTrips() tự ôm PENDING_APPROVAL: đó là tham số của câu truy vấn,
     * thuộc tầng service. KHÁC với DispatchController.BOARD_ACTIONS, vốn là tập
     * trạng thái ĐÍCH mà màn hình cho phép chuyển sang.
     *
     * Truy vấn KHÔNG có cận dưới thời gian (xem TripRepository), nên một chuyến
     * DEPARTED đã quá hạn từ lâu vẫn hiện trên bảng thay vì bị kẹt ngoài tầm với —
     * đây là lối duy nhất còn phơi ra transition DEPARTED → COMPLETED sau lỗi #18.
     *
     * @param until mốc trên của departureTime; màn hình quyết định nhìn xa bao
     *              nhiêu (xem DispatchController.UPCOMING_WINDOW_HOURS)
     */
    public List<Trip> getDispatchBoardTrips(LocalDateTime until) {
        List<TripStatus> boardStatuses = List.of(TripStatus.ACTIVE, TripStatus.DEPARTED);
        return tripRepository.findDispatchBoardTrips(boardStatuses, until);
    }

    /**
     * Chuyến đang vận hành (ACTIVE / DEPARTED, mọi ngày) cộng chuyến đã kết thúc
     * (COMPLETED / CANCELLED) khởi hành từ mốc {@code since} trở đi, mới nhất lên
     * đầu — phạm vi mời cho các dropdown chọn chuyến ở tầng UI (hiện tại: form ghi
     * nhận sự cố). Cửa sổ do nơi gọi quyết định (tiền lệ
     * {@link #getDispatchBoardTrips(LocalDateTime)}); tập trạng thái cố định ở đây
     * vì đó là nghiệp vụ "chuyến nào còn có thật để gắn một sự cố vào": chuyến chờ
     * duyệt chưa tồn tại thật (chưa mở bán, xe chưa chốt), chuyến đã hủy vẫn cần
     * hiện một thời gian ngắn vì "hủy chuyến vì sự cố, rồi ghi sự cố" là thứ tự
     * tự nhiên của admin.
     *
     * Thay cho {@code getAllTrips()} (Phase 2, 2026-07-16 → 2026-09-21): đổ toàn bộ
     * lịch sử vào một select — 2.222 option / 353 KB / 1,2 s mỗi lần mở form trên
     * dữ liệu 2026-09-21, và tăng theo mỗi lần backfill. Đây là phạm vi MỜI, không
     * phải luật: IncidentService không kiểm chuyến thuộc cửa sổ nào, một POST gửi
     * id chuyến cũ hơn vẫn hợp lệ (khóa ngoại thật). Giữ tên theo nội dung trả về,
     * không theo màn hình, để TripService không bị buộc vào một form cụ thể.
     */
    public List<Trip> getRunningAndRecentTrips(LocalDateTime since) {
        return tripRepository.findRunningAndRecentTrips(
                List.of(TripStatus.ACTIVE, TripStatus.DEPARTED),
                List.of(TripStatus.COMPLETED, TripStatus.CANCELLED),
                since);
    }

    /**
     * Danh sách cho màn Quản lý chuyến (QĐ-12): mọi chuyến CHƯA KẾT THÚC, bất kể ngày —
     * chúng còn chờ Admin xử lý, kể cả chuyến DEPARTED bị bỏ quên từ lâu —, cộng chuyến
     * ĐÃ KẾT THÚC khởi hành từ {@code since} trở đi. Mới nhất lên đầu.
     *
     * "Đã kết thúc" được SUY từ whitelist FSM, không khai lại: trạng thái mà từ đó không
     * đi tiếp được sang trạng thái nào khác (canTransition — hiện là COMPLETED và
     * CANCELLED).
     *
     * @param status trạng thái cần lọc; null = mọi trạng thái
     */
    public List<Trip> getTripsForList(TripStatus status, LocalDateTime since) {
        List<TripStatus> statuses = status != null ? List.of(status) : List.of(TripStatus.values());
        List<TripStatus> finished = java.util.Arrays.stream(TripStatus.values())
                .filter(s -> allowedTransitionsFrom(s).equals(List.of(s)))
                .toList();
        return tripRepository.findForTripList(statuses, finished, since);
    }

    public Trip getTripById(Long id) {
        return tripRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe với ID: " + id));
    }

    // =========================================================================
    // XÓA MỀM (SOFT DELETE)
    // =========================================================================

    /**
     * Xóa mềm một chuyến xe sau khi kiểm tra đầy đủ ràng buộc nghiệp vụ.
     *
     * Không thực hiện DELETE vật lý — JPA sẽ gọi @SQLDelete trên Trip entity,
     * phát ra câu lệnh: UPDATE trips SET is_deleted = true WHERE id = ?
     * Nhờ đó toàn vẹn FK được bảo toàn và lịch sử giao dịch không bị mất.
     *
     * Quy tắc xóa theo trạng thái:
     * DEPARTED → NGHIÊM CẤM (đang trên đường, ảnh hưởng hành trình thực)
     * COMPLETED → NGHIÊM CẤM (báo cáo tài chính & lịch sử phải được giữ nguyên)
     * ACTIVE + có vé đã bán → BỊ CHẶN (yêu cầu dùng luồng Hủy chuyến)
     * ACTIVE + chưa bán vé → Cho phép (chuyến chưa public hoặc chưa có khách)
     * PENDING_APPROVAL → Cho phép (bản nháp AI / Admin, chưa public)
     * CANCELLED → Cho phép (chuyến đã kết thúc vòng đời, dọn dẹp DB)
     *
     * @throws EntityNotFoundException nếu không tìm thấy chuyến
     * @throws IllegalStateException   nếu vi phạm ràng buộc nghiệp vụ (→ HTTP 400)
     */
    @Transactional
    public void deleteTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy chuyến xe #" + tripId));

        String refusal = deleteRefusalReason(trip);
        if (refusal != null) {
            throw new IllegalStateException(refusal);
        }

        // Kích hoạt @SQLDelete: phát ra UPDATE trips SET is_deleted=true WHERE id=?
        tripRepository.delete(trip);

        log.info("🗑️ Admin xóa mềm chuyến #{} (trạng thái cũ: {})",
                tripId, trip.getStatus());
    }

    /**
     * Chính sách XÓA theo trạng thái, phát biểu thành dữ liệu: câu từ chối, hoặc
     * {@code null} nếu chuyến xóa được. {@link #deleteTrip} là nơi thực thi (ném
     * IllegalStateException khi khác null); tầng UI là nơi HỎI (nút "Xóa" ở danh
     * sách chuyến chỉ hiện khi trả về null — lỗi #23).
     *
     * Tách ra khỏi deleteTrip() thay vì để template tự chép điều kiện: trước đây
     * trip-list.html hiện nút Xóa theo {@code ticketsSold == 0} — chỉ khớp đúng
     * nhánh ACTIVE của bảng dưới, nên mời xóa chuyến DEPARTED/COMPLETED 0 vé (bị
     * từ chối 100 %) và giấu nút với 136 chuyến PENDING/CANCELLED có vé (được phép).
     * Cùng khuôn với AdminTripManagementController.editRefusalReason() (lỗi #18):
     * một method, hai người dùng — thực thi và mời — nên không thể lệch nhau nữa.
     *
     * Switch cố ý KHÔNG có default: thêm một TripStatus mới thì vỡ biên dịch, buộc
     * người thêm phải quyết định chính sách xóa cho nó.
     */
    public String deleteRefusalReason(Trip trip) {
        Long tripId = trip.getId();
        return switch (trip.getStatus()) {
            case DEPARTED -> "Chuyến #" + tripId + " đang trên đường (DEPARTED). "
                    + "Không thể xóa chuyến đang vận hành — sẽ phá vỡ hành trình thực tế và dữ liệu GPS.";

            case COMPLETED -> "Chuyến #" + tripId + " đã hoàn thành (COMPLETED). "
                    + "Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên, không thể xóa.";

            // ACTIVE nhưng chưa bán vé nào → an toàn để xóa mềm; đã có vé → phải đi
            // luồng Hủy chuyến.
            case ACTIVE -> trip.getTicketsSold() > 0
                    ? String.format(
                            "Chuyến #%d đang ACTIVE và đã có %d vé bán ra. "
                                    + "Vui lòng dùng chức năng 'Hủy chuyến' thay vì xóa "
                                    + "để hệ thống xử lý hoàn vé và thông báo cho hành khách.",
                            tripId, trip.getTicketsSold())
                    : null;

            // PENDING_APPROVAL và CANCELLED: cho phép xóa không điều kiện
            case PENDING_APPROVAL, CANCELLED -> null;
        };
    }

    /**
     * Hàm Helper chuyên trách việc chuyển trạng thái chuyến xe sang ACTIVE.
     * Đồng thời đóng dấu thời gian mở bán nếu chuyến xe chưa từng được mở bán.
     *
     * ⚠️ BẤT BIẾN: với một chuyến ĐÃ TỒN TẠI, chỉ được VÀO ACTIVE từ
     * PENDING_APPROVAL — đúng dòng duy nhất whitelist FSM cho phép
     * (canTransition:541). Method này KHÔNG tự kiểm; mỗi call site phải tự bảo
     * đảm. Hiện có ĐÚNG BỐN, và chúng KHÔNG cùng một dạng:
     * <ul>
     * <li>updateTripStatus() — canTransition() đã lọc; ACTIVE → ACTIVE đã return
     * sớm ở nhánh from == to, nên tới đây chỉ còn PENDING_APPROVAL;</li>
     * <li>confirmAutoAssignedTrip() và approveTrip() — nạp bản ghi bằng
     * findById() nên CÓ from-state; được bảo đảm bằng requirePendingApproval()
     * gọi ngay đầu method (lỗi #20);</li>
     * <li>createManualTrip() — TẠO MỚI, ngoại lệ thật sự: Trip còn transient
     * (id == null, chưa có bản ghi nào trong DB) nên KHÔNG có from-state để nói
     * tới. Lưu ý trạng thái của nó lúc này KHÔNG phải mặc định PENDING_APPROVAL
     * của entity: AdminTripManagementController.createTrip():117 đã set thẳng
     * ACTIVE trước khi gọi vào đây.</li>
     * </ul>
     * ⛔ HỆ QUẢ QUAN TRỌNG — ĐỪNG "gom guard về một chỗ" bằng cách chuyển
     * requirePendingApproval() vào chính method này. Nghe thì gọn (một chốt chặn
     * cho cả bốn cửa), nhưng nó sẽ ném lỗi ở lối createManualTrip() vì trạng thái
     * ở đó là ACTIVE ⇒ VỠ TOÀN BỘ chức năng tạo chuyến thủ công. Guard phải nằm ở
     * hai method phê duyệt, đúng chỗ nó đang nằm.
     *
     * THÊM CALL SITE MỚI trên một chuyến đã tồn tại thì phải tự bảo đảm bất biến
     * này, nếu không sẽ tái tạo đúng lỗi #20: duyệt được một chuyến
     * CANCELLED/COMPLETED/DEPARTED và app báo "thành công", trong khi
     * trip_lifecycle_fsm.md §5 ghi ba transition đó là "Invalid — ENFORCED".
     */
    private void changeStatusToActive(Trip trip) {
        trip.setStatus(TripStatus.ACTIVE);
        if (trip.getSaleOpenedAt() == null) {
            trip.setSaleOpenedAt(LocalDateTime.now());
        }
    }

    // =========================================================================
    // VỊ TRÍ XE VÀ NGƯỜI — route pairs CT5b (QĐ-23, CM-5 cách B)
    // =========================================================================
    //
    // Luật vị trí viết MỘT lần ở domain/LocationRule: lúc t, xe (hay người) đứng ở bến cuối
    // của chuyến gần nhất chưa hủy có giờ đến ≤ t; chưa có chuyến nào thì ở bến nhà; chưa có
    // bến nhà và chưa chạy chuyến nào thì "không biết ở đâu". Nhận thêm một chuyến thì chuỗi
    // phải liền ở CẢ HAI phía (LocationRule.fits): lúc khởi hành họ đứng ở bến đi, và chuyến kế
    // tiếp đã cam kết (nếu có) khởi hành từ bến đến của chuyến này. Phần dưới chỉ cấp các chuyến
    // cho luật đó — đọc DB, hoặc đọc AvailabilityContext — rồi áp nó theo CM-5 cách B:
    //   - MÁY chọn (findBestAvailableBus / findBestAvailableDriver: job 10 giây, màn Đề xuất)
    //     là CỨNG: chỉ xét xe và người mà chuyến này vừa chuỗi của họ;
    //   - NGƯỜI chọn (ô chọn ở các màn Duyệt, Sửa, Tạo) là MỀM: vẫn mời mọi lựa chọn hợp lệ,
    //     ai vừa chuỗi xếp lên đầu, những người khác kèm ghi chú vì sao;
    //   - validateBusForTrip / validateStaffForTrip KHÔNG chặn theo vị trí.
    // Thời gian nghỉ giữa hai chuyến đã do cửa sổ "bận" lo (BUS_PREP_BUFFER_HOURS,
    // MIN_REST_BETWEEN_TRIPS_MINUTES), nên ở đây chỉ so BẾN. Tuyến chưa có bến — chỉ gặp ở dữ
    // liệu test, vì RouteService bắt buộc ≥ 2 bến — thì không biết bến: không xét vị trí.
    // Đường DB và đường AvailabilityContext cho cùng kết quả (TripServiceLocationTest).

    /** Mọi trạng thái trừ CANCELLED: đúng tập buildAvailabilityContext nạp; LocationRule.legOf bỏ chuyến hủy. */
    private static final List<TripStatus> LOCATION_STATUSES = List.of(TripStatus.PENDING_APPROVAL,
            TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.COMPLETED);

    /** Chỉ để ghi tên bến vào ghi chú của ô chọn. */
    private final StationRepository stationRepository;

    /** Form Tạo gửi id tuyến đã chọn (TripRestController) — để biết hai đầu của chuyến định tạo. */
    private final RouteRepository routeRepository;

    /** Bến xuất phát của chuyến (id); null khi chuyến hay tuyến chưa có bến. */
    public static Long departureStationOf(Trip trip) {
        if (trip == null || trip.getRoute() == null) {
            return null;
        }
        return idOf(trip.getRoute().getDepartureStation());
    }

    private static Long idOf(Station station) {
        return station != null ? station.getId() : null;
    }

    /** Lượt định chạy trên tuyến route; null khi tuyến chưa có bến (khi đó không xét vị trí). */
    private static LocationRule.Leg plannedLeg(Route route, LocalDateTime departure, LocalDateTime arrival) {
        if (route == null || departure == null || arrival == null || arrival.isBefore(departure)) {
            return null;
        }
        Long from = idOf(route.getDepartureStation());
        Long to = idOf(route.getDestinationStation());
        return from == null || to == null ? null : new LocationRule.Leg(from, to, departure, arrival);
    }

    /** Giờ đến dùng cho vị trí: như các ô chọn khác, chuyến thiếu giờ đến coi như chạy 5 giờ. */
    private static LocalDateTime arrivalOf(Trip trip) {
        return trip.getArrivalTimeExpected() != null ? trip.getArrivalTimeExpected()
                : trip.getDepartureTime().plusHours(5);
    }

    /**
     * Các chuyến đã cam kết của xe quanh lượt [departure, arrival]: chuyến gần nhất tới nơi
     * ≤ departure và chuyến sớm nhất khởi hành ≥ arrival — đủ cho LocationRule.fits. ctx == null
     * → hỏi DB; ctx != null → lượt trước / sau khoảng đã nạp cùng mọi chuyến của xe trong context.
     */
    private java.util.List<LocationRule.Leg> busLegsAround(Bus bus, LocalDateTime departure,
            LocalDateTime arrival, AvailabilityContext ctx) {
        java.util.List<LocationRule.Leg> legs = new java.util.ArrayList<>();
        if (ctx == null) {
            tripRepository.findFirstByBusAndStatusInAndArrivalTimeExpectedLessThanEqualOrderByArrivalTimeExpectedDescIdDesc(
                    bus, LOCATION_STATUSES, departure).map(LocationRule::legOf).ifPresent(legs::add);
            tripRepository.findFirstByBusAndStatusInAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAscIdAsc(
                    bus, LOCATION_STATUSES, arrival).map(LocationRule::legOf).ifPresent(legs::add);
            return legs;
        }
        legs.add(ctx.busLegBefore(bus.getId()));
        legs.add(ctx.busLegAfter(bus.getId()));
        for (Trip trip : ctx.trips()) {
            if (LOCATION_STATUSES.contains(trip.getStatus()) && trip.getBus() != null
                    && bus.getId().equals(trip.getBus().getId())) {
                legs.add(LocationRule.legOf(trip));
            }
        }
        return legs;
    }

    /** Cùng ý cho một người — tài xế, phụ xe hay tài xế phụ: ai đi trên xe cũng tới bến cuối cùng xe. */
    private java.util.List<LocationRule.Leg> personLegsAround(Driver person, LocalDateTime departure,
            LocalDateTime arrival, AvailabilityContext ctx) {
        java.util.List<LocationRule.Leg> legs = new java.util.ArrayList<>();
        if (ctx == null) {
            personTripArrivingBy(person, departure).map(LocationRule::legOf).ifPresent(legs::add);
            personTripDepartingFrom(person, arrival).map(LocationRule::legOf).ifPresent(legs::add);
            return legs;
        }
        legs.add(ctx.personLegBefore(person.getUserId()));
        legs.add(ctx.personLegAfter(person.getUserId()));
        for (Trip trip : ctx.trips()) {
            if (LOCATION_STATUSES.contains(trip.getStatus()) && tripInvolvesDriver(trip, person)) {
                legs.add(LocationRule.legOf(trip));
            }
        }
        return legs;
    }

    /**
     * Chuyến gần nhất của một người tới nơi ≤ t, ở bất kỳ vai nào trên xe. TripRepository hỏi riêng
     * từng vai (một câu OR quét cả bảng trips); ở đây lấy chuyến muộn nhất của ba câu — giờ đến,
     * rồi id lớn hơn, đúng thứ tự mỗi câu tự xếp — nên ra đúng chuyến mà một câu gộp sẽ ra.
     */
    private java.util.Optional<Trip> personTripArrivingBy(Driver person, LocalDateTime t) {
        return java.util.stream.Stream.of(
                tripRepository.findFirstByDriverAndStatusInAndArrivalTimeExpectedLessThanEqualOrderByArrivalTimeExpectedDescIdDesc(
                        person, LOCATION_STATUSES, t),
                tripRepository.findFirstByAssistantAndStatusInAndArrivalTimeExpectedLessThanEqualOrderByArrivalTimeExpectedDescIdDesc(
                        person, LOCATION_STATUSES, t),
                tripRepository.findTripsAsCoDriverArrivingBy(person, LOCATION_STATUSES, t,
                        org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst())
                .flatMap(java.util.Optional::stream)
                .max(Comparator.comparing(Trip::getArrivalTimeExpected).thenComparing(Trip::getId));
    }

    /** Phía sau: chuyến sớm nhất của một người khởi hành ≥ t, ở bất kỳ vai nào — giờ đi, rồi id nhỏ hơn. */
    private java.util.Optional<Trip> personTripDepartingFrom(Driver person, LocalDateTime t) {
        return java.util.stream.Stream.of(
                tripRepository.findFirstByDriverAndStatusInAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAscIdAsc(
                        person, LOCATION_STATUSES, t),
                tripRepository.findFirstByAssistantAndStatusInAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAscIdAsc(
                        person, LOCATION_STATUSES, t),
                tripRepository.findTripsAsCoDriverDepartingFrom(person, LOCATION_STATUSES, t,
                        org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst())
                .flatMap(java.util.Optional::stream)
                .min(Comparator.comparing(Trip::getDepartureTime).thenComparing(Trip::getId));
    }

    /** Bến mà xe đang đứng lúc t; null = không biết. */
    Long busStationAt(Bus bus, LocalDateTime t, AvailabilityContext ctx) {
        return LocationRule.stationAt(busLegsAround(bus, t, t, ctx), idOf(bus.getHomeStation()), t);
    }

    /** Bến mà một người đang đứng lúc t; null = không biết. */
    Long personStationAt(Driver person, LocalDateTime t, AvailabilityContext ctx) {
        return LocationRule.stationAt(personLegsAround(person, t, t, ctx), idOf(person.getHomeStation()), t);
    }

    /** Máy chọn, CỨNG: chuyến [departure, arrival] trên tuyến route vừa chuỗi vị trí của xe. Tuyến chưa có bến → không xét. */
    private boolean busFits(Bus bus, Route route, LocalDateTime departure, LocalDateTime arrival,
            AvailabilityContext ctx) {
        LocationRule.Leg planned = plannedLeg(route, departure, arrival);
        return planned == null || LocationRule.fits(busLegsAround(bus, departure, arrival, ctx),
                idOf(bus.getHomeStation()), planned);
    }

    /** Cùng ý cho một người. */
    private boolean personFits(Driver person, Route route, LocalDateTime departure, LocalDateTime arrival,
            AvailabilityContext ctx) {
        LocationRule.Leg planned = plannedLeg(route, departure, arrival);
        return planned == null || LocationRule.fits(personLegsAround(person, departure, arrival, ctx),
                idOf(person.getHomeStation()), planned);
    }

    /** Người chọn, MỀM: xe vừa chuỗi lên đầu; phần còn lại giữ thứ tự cũ (sort ổn định). */
    private Comparator<Bus> fittingBusesFirst(Trip trip) {
        LocalDateTime arrival = arrivalOf(trip);
        return standingFirst(bus -> busFits(bus, trip.getRoute(), trip.getDepartureTime(), arrival, null));
    }

    /** Cùng ý cho ô chọn tài xế, tài xế phụ, phụ xe. */
    private Comparator<Driver> fittingPeopleFirst(Trip trip) {
        LocalDateTime arrival = arrivalOf(trip);
        return standingFirst(person -> personFits(person, trip.getRoute(), trip.getDepartureTime(), arrival, null));
    }

    /**
     * Cho form Sửa, nơi ô chọn tài xế liệt kê MỌI tài xế (không qua getAvailable*): bản sao đã
     * xếp người vừa chuỗi lên đầu, phần còn lại giữ nguyên thứ tự.
     */
    public List<Driver> withPeopleAtStartFirst(Trip trip, java.util.Collection<Driver> people) {
        List<Driver> sorted = new java.util.ArrayList<>(people);
        sorted.sort(fittingPeopleFirst(trip));
        return sorted;
    }

    /** "Vừa chuỗi" lên trước. Nhớ kết quả của từng phần tử để phép sắp xếp không hỏi DB lặp lại. */
    private static <T> Comparator<T> standingFirst(java.util.function.Predicate<T> fits) {
        java.util.Map<T, Boolean> memo = new java.util.IdentityHashMap<>();
        return Comparator.comparing((T item) -> !memo.computeIfAbsent(item, fits::test));
    }

    /**
     * Ghi chú vị trí cho ô chọn thủ công (CM-5 cách B): id xe → lý do xe không vừa chuỗi —
     * "đang ở &lt;bến&gt;", "chưa rõ đang ở đâu …" hay "chuyến kế tiếp đi từ &lt;bến&gt;". Xe vừa
     * chuỗi không có ghi chú. Tuyến chưa có bến → rỗng.
     */
    public java.util.Map<Long, String> busLocationNotes(Trip trip) {
        java.util.Map<Long, String> notes = new java.util.HashMap<>();
        LocalDateTime arrival = arrivalOf(trip);
        for (Bus bus : busRepository.findAll()) {
            String note = busLocationNote(bus, trip.getRoute(), trip.getDepartureTime(), arrival);
            if (note != null) {
                notes.put(bus.getId(), note);
            }
        }
        return notes;
    }

    /** Cùng ý, theo userId, cho mọi tài xế. */
    public java.util.Map<Long, String> personLocationNotes(Trip trip) {
        java.util.Map<Long, String> notes = new java.util.HashMap<>();
        LocalDateTime arrival = arrivalOf(trip);
        for (Driver person : driverRepository.findAll()) {
            String note = personLocationNote(person, trip.getRoute(), trip.getDepartureTime(), arrival);
            if (note != null) {
                notes.put(person.getUserId(), note);
            }
        }
        return notes;
    }

    /** Ghi chú cho một xe; null khi chuyến vừa chuỗi của xe, hoặc tuyến chưa có bến. */
    public String busLocationNote(Bus bus, Route route, LocalDateTime departure, LocalDateTime arrival) {
        LocationRule.Leg planned = plannedLeg(route, departure, arrival);
        return planned == null ? null
                : whyNot(busLegsAround(bus, departure, arrival, null), idOf(bus.getHomeStation()), planned);
    }

    /** Cùng ý cho một người. */
    public String personLocationNote(Driver person, Route route, LocalDateTime departure, LocalDateTime arrival) {
        LocationRule.Leg planned = plannedLeg(route, departure, arrival);
        return planned == null ? null
                : whyNot(personLegsAround(person, departure, arrival, null), idOf(person.getHomeStation()), planned);
    }

    /** Vì sao lượt planned không vừa chuỗi: hai phía của LocationRule.fits, nói bằng tên bến. */
    private String whyNot(java.util.List<LocationRule.Leg> legs, Long home, LocationRule.Leg planned) {
        Long at = LocationRule.stationAt(legs, home, planned.departure());
        if (!planned.fromStationId().equals(at)) {
            return at == null ? "chưa rõ đang ở đâu (chưa có bến nhà)" : "đang ở " + stationName(at);
        }
        LocationRule.Leg next = LocationRule.firstLegDepartingFrom(legs, planned.arrival());
        if (next != null && !next.fromStationId().equals(planned.toStationId())) {
            return "chuyến kế tiếp đi từ " + stationName(next.fromStationId());
        }
        return null;
    }

    private String stationName(Long stationId) {
        return stationRepository.findById(stationId).map(Station::getStationName).orElse("bến #" + stationId);
    }

    /** Tuyến cho form Tạo (TripRestController); null khi chưa chọn hay không có. */
    public Route routeForLocation(Long routeId) {
        return routeId == null ? null : routeRepository.findByIdWithStations(routeId).orElse(null);
    }

    /**
     * Bản cho form Tạo (CT5b): đúng danh sách của getAvailableBusesForTimeRange(departure, arrival),
     * nhưng xe vừa chuỗi với chuyến định tạo trên tuyến route lên đầu. route == null (form chưa
     * chọn tuyến) → giữ nguyên thứ tự.
     */
    public List<Bus> getAvailableBusesForTimeRange(LocalDateTime departure, LocalDateTime arrival, Route route) {
        List<Bus> buses = new java.util.ArrayList<>(getAvailableBusesForTimeRange(departure, arrival));
        buses.sort(standingFirst(bus -> busFits(bus, route, departure, arrival, null)));
        return buses;
    }

    /** Cùng ý cho ô chọn tài xế chính / tài xế phụ của form Tạo. */
    public List<Driver> getAvailableDriversForTimeRange(LocalDateTime departure, LocalDateTime arrival, Route route) {
        List<Driver> drivers = new java.util.ArrayList<>(getAvailableDriversForTimeRange(departure, arrival));
        drivers.sort(standingFirst(person -> personFits(person, route, departure, arrival, null)));
        return drivers;
    }

    /** Cùng ý cho ô chọn phụ xe của form Tạo. */
    public List<Driver> getAvailableAssistantsForTimeRange(LocalDateTime departure, LocalDateTime arrival,
            Route route) {
        List<Driver> assistants = new java.util.ArrayList<>(getAvailableAssistantsForTimeRange(departure, arrival));
        assistants.sort(standingFirst(person -> personFits(person, route, departure, arrival, null)));
        return assistants;
    }

    /**
     * Nạp cho AvailabilityContext, với MỖI xe và MỖI người, chuyến gần nhất tới nơi ≤ spanStart và
     * chuyến sớm nhất khởi hành ≥ spanEnd — hai lượt nằm ngoài các chuyến đã nạp.
     */
    private AvailabilityContext withLocations(List<Trip> trips, LocalDateTime spanStart, LocalDateTime spanEnd) {
        java.util.Map<Long, LocationRule.Leg> busBefore = new java.util.HashMap<>();
        java.util.Map<Long, LocationRule.Leg> busAfter = new java.util.HashMap<>();
        for (Bus bus : busRepository.findAll()) {
            tripRepository.findFirstByBusAndStatusInAndArrivalTimeExpectedLessThanEqualOrderByArrivalTimeExpectedDescIdDesc(
                    bus, LOCATION_STATUSES, spanStart).map(LocationRule::legOf)
                    .ifPresent(leg -> busBefore.put(bus.getId(), leg));
            tripRepository.findFirstByBusAndStatusInAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAscIdAsc(
                    bus, LOCATION_STATUSES, spanEnd).map(LocationRule::legOf)
                    .ifPresent(leg -> busAfter.put(bus.getId(), leg));
        }
        java.util.Map<Long, LocationRule.Leg> personBefore = new java.util.HashMap<>();
        java.util.Map<Long, LocationRule.Leg> personAfter = new java.util.HashMap<>();
        for (Driver person : driverRepository.findAll()) {
            personTripArrivingBy(person, spanStart).map(LocationRule::legOf)
                    .ifPresent(leg -> personBefore.put(person.getUserId(), leg));
            personTripDepartingFrom(person, spanEnd).map(LocationRule::legOf)
                    .ifPresent(leg -> personAfter.put(person.getUserId(), leg));
        }
        return new AvailabilityContext(trips, busBefore, personBefore, busAfter, personAfter);
    }

    // =========================================================================
    // LỊCH CỦA MỘT NGƯỜI THEO TỪNG VAI — lỗi #35
    // =========================================================================

    /**
     * Mọi chuyến khởi hành trong [startOfDay, endOfDay) mà người này có mặt ở BẤT KỲ vai nào — tài
     * xế chính, tài xế phụ, phụ xe — cho {@link #sumDrivingHours}. Cùng phạm vi với nhánh
     * AvailabilityContext ({@link #tripInvolvesDriver}). TripRepository hỏi riêng từng vai, vì một
     * câu gộp ba vai bằng OR qua trip_co_drivers quét cả bảng trips (lỗi #35); ở đây hợp ba danh
     * sách, mỗi chuyến một lần theo id, như DISTINCT của câu gộp cũ. Thứ tự không quan trọng:
     * sumDrivingHours chỉ cộng.
     */
    private List<Trip> tripsOfPersonOnDate(Driver person, List<TripStatus> statuses, LocalDateTime startOfDay,
            LocalDateTime endOfDay, Long excludeTripId) {
        java.util.Map<Long, Trip> byId = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.of(
                tripRepository.findTripsForDriverOnDate(person, statuses, startOfDay, endOfDay, excludeTripId),
                tripRepository.findTripsForCoDriverOnDate(person, statuses, startOfDay, endOfDay, excludeTripId),
                tripRepository.findTripsForAssistantOnDate(person, statuses, startOfDay, endOfDay, excludeTripId))
                .flatMap(List::stream)
                .forEach(trip -> byId.putIfAbsent(trip.getId(), trip));
        return new java.util.ArrayList<>(byId.values());
    }

    /**
     * Khoá sắp xếp "giờ lái trong ngày", nhớ theo từng người (cùng ý với standingFirst). Phép sắp xếp
     * gọi khoá ở MỖI lần so sánh — khoảng 2·log₂n lần cho mỗi người — và mỗi lần là ba câu SQL. Màn
     * Duyệt thủ công từng gọi getDrivingHoursForDate 439 lần cho 30 người (lỗi #35). Mỗi lần sắp xếp
     * một khoá mới, nên không có gì sống quá một yêu cầu. Hai chỗ min() ở findBestAvailableDriver giữ
     * nguyên: min chỉ gọi khoá tối đa hai lần cho mỗi người.
     */
    private java.util.function.ToDoubleFunction<Driver> drivingHoursKey(LocalDateTime departure, Long tripId) {
        java.util.Map<Driver, Double> memo = new java.util.IdentityHashMap<>();
        return d -> memo.computeIfAbsent(d, person -> getDrivingHoursForDate(person, departure, tripId));
    }

    // =========================================================================
    // LỊCH THƯỜNG LỆ — route pairs CT6 (QĐ-20, QĐ-22; CM-14, CM-17…CM-22)
    // =========================================================================
    //
    // RegularTimetableService xếp chuyến thường lệ của N ngày tới, khung sớm xếp trước, bằng ĐÚNG bộ
    // máy chọn của job 10 giây — autoAssignResources: xe (vị trí CT5b, bận ±1 giờ, bảo trì, đúng loại
    // rồi ít km), tài xế, tài xế phụ, phụ xe — chạy trên một AvailabilityContext "lập lịch trước".
    // Context đó khác context của màn Đề xuất ở một điểm DỮ LIỆU (CM-21): xe được xếp là xe mà
    // validateBusForTrip nhận theo trạng thái — READY, hoặc TRAVELING có chuyến DEPARTED giải thích
    // (lỗi #19) — thay cho "READY ngay lúc bấm nút": lịch là cho những ngày tới. Xe REPAIRING không được
    // xếp. Km mọi chuyến chưa xong của xe (chờ duyệt, đang mở bán, đang chạy, cả chuyến vừa xếp) được
    // cộng vào luật bảo trì, vì km thật chỉ tăng khi chuyến hoàn thành (updateTripStatus) — CM-21 làm điều
    // đó riêng cho bộ lập lịch; từ CT7d mọi bộ chọn của máy cùng cộng (xem mục "BẢO TRÌ TRONG LỊCH").
    // Không luật nào được chép. Cổng cuối vẫn là validateBusForTrip / validateStaffForTrip (SQL), và
    // chuyến được lưu qua createManualTrip — lối tạo chuyến ACTIVE sẵn có của form Tạo.

    /** Chuyến chưa chạy xong: km của chúng sẽ cộng vào odometer khi hoàn thành. */
    private static final List<TripStatus> UNFINISHED_STATUSES = List.of(TripStatus.PENDING_APPROVAL,
            TripStatus.ACTIVE, TripStatus.DEPARTED);

    /**
     * Context lập lịch cho [spanStart, spanEnd): đúng context của buildAvailabilityContext (cả km đã cam
     * kết), cộng danh sách xe được xếp (xem đầu mục).
     */
    @Transactional
    public AvailabilityContext buildTimetableContext(LocalDateTime spanStart, LocalDateTime spanEnd) {
        AvailabilityContext ctx = buildAvailabilityContext(spanStart, spanEnd);
        List<Bus> schedulable = busRepository.findAll().stream()
                .filter(bus -> bus.getStatus() == BusStatus.READY
                        || bus.getStatus() == BusStatus.TRAVELING && travelingFlagExplained(bus))
                .collect(Collectors.toList());
        ctx.lookAhead(schedulable);
        return ctx;
    }

    /** Phân công cho một chuyến thường lệ: đúng autoAssignResources của job 10 giây, trên context lập lịch. */
    public AutoAssignResult assignRegularTrip(Trip trip, AvailabilityContext ctx) {
        return autoAssignResources(trip, ctx);
    }

    /**
     * Chuyến thường lệ vừa lưu: các chuyến xếp sau phải thấy nó — xe và người bận, giờ lái, vị trí, và km
     * của nó trong luật bảo trì (reserve giữ cả ba).
     */
    public void reserveRegularTrip(Trip trip, AvailabilityContext ctx) {
        ctx.reserve(trip);
    }

    /**
     * Cờ TRAVELING của xe có chuyến DEPARTED nào giải thích không (lỗi #19). Một định nghĩa cho hai
     * nơi: luật trạng thái của validateBusForTrip, và danh sách xe của context lập lịch.
     */
    private boolean travelingFlagExplained(Bus bus) {
        return tripRepository.existsByBusIdAndStatusIn(bus.getId(), List.of(TripStatus.DEPARTED));
    }

    // =========================================================================
    // CẢ VÒNG MỘT LẦN — route pairs CT7c (CM-24, phương án PA-A)
    // =========================================================================
    //
    // Màn Đề xuất coi mỗi chuyến thêm là một VÒNG bến nhà → bến nhà (QĐ-22, PA-B): chuyến thêm rồi "về
    // ngay", hay "đưa xe tới" rồi chuyến thêm. Trước CT7c mỗi lượt được chọn riêng, nên luật vị trí hai
    // phía (CT5b) xét chuyến thêm MỘT MÌNH: một tài xế đang ở bến nhà mà chuyến kế tiếp cũng đi từ bến nhà
    // bị loại khỏi chuyến thêm rời bến nhà, dù lượt về của chính vòng đó đưa họ về kịp (CM-24: lịch thường
    // lệ phủ cả tuần thì chỉ 2/25 thẻ đủ điều kiện). Ở đây cả vòng được chọn MỘT LẦN, cho MỘT xe và MỘT
    // tài xế chính — đúng luật H1 của bộ sinh lịch sử (HistoricalDataBackfill.addOuting / findReturn) và
    // "kíp ở lại cùng xe" của QĐ-26 — bằng chính các luật của findBestAvailableBus / findBestAvailableDriver,
    // áp cho cả khối:
    //   - bận: xe không có chuyến nào trong [đầu khối − 1 giờ, cuối khối + 1 giờ], tài xế trong
    //     [đầu − 30 phút, cuối + 30 phút] — xe và kíp chờ nhau ở bến xa;
    //   - vị trí: LocationRule.fits, cả khối coi như MỘT lượt từ bến đầu tới bến cuối;
    //   - giờ lái: mỗi lượt cộng vào ngày khởi hành của nó (như sumDrivingHours), mỗi ngày ≤ 8 giờ;
    //   - bảo trì: km của mọi lượt trong khối (từ CT7d: luật bảo trì của máy, mục cuối lớp);
    //   - bằng lái còn hạn vào ngày của mọi lượt; thứ tự ưu tiên như hai hàm gốc.
    // Không có luật mới. Cổng cuối vẫn là validateBusForTrip / validateStaffForTrip cho TỪNG lượt.

    /** Xe và tài xế cho cả một vòng. bus null = không có xe; driver null = có xe mà không có tài xế. */
    public record LoopChoice(Bus bus, Driver driver) {
        public boolean isComplete() {
            return bus != null && driver != null;
        }
    }

    /**
     * Chọn MỘT xe và MỘT tài xế chính cho cả vòng (xem đầu mục). legs: các lượt theo thứ tự giờ, mỗi lượt
     * có tuyến, giờ đi, giờ đến; lượt sau xuất phát từ bến đến của lượt trước. Không giữ chỗ — nơi gọi cho
     * từng lượt qua cổng rồi mới reserve.
     */
    public LoopChoice selectLoop(List<Trip> legs, AvailabilityContext ctx) {
        Trip first = legs.get(0);
        Trip last = legs.get(legs.size() - 1);
        LocalDateTime start = first.getDepartureTime();
        LocalDateTime end = arrivalOf(last);
        LocationRule.Leg block = blockLeg(first, last, start, end);
        double km = legs.stream().mapToDouble(TripService::distanceOf).sum();
        BusType preferredType = first.getRoute().getSuitableBusType();
        List<Bus> candidates = ctx != null && ctx.isLookAhead() ? ctx.schedulableBuses()
                : busRepository.findByStatus(BusStatus.READY);
        LocalDateTime busFrom = start.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime busTo = end.plusHours(BUS_PREP_BUFFER_HOURS);
        Bus bus = candidates.stream()
                .filter(b -> !isBusBusy(b, busFrom, busTo, null, ctx))
                .filter(b -> block == null
                        || LocationRule.fits(busLegsAround(b, start, end, ctx), idOf(b.getHomeStation()), block))
                .filter(b -> machineMaintenanceAllows(b, km, homewardKm(b, last.getRoute(), distanceOf(last)), start,
                        end, ctx))
                .min(machineOrder(preferredType, start, end, ctx))
                .orElse(null);
        return new LoopChoice(bus, bus != null ? driverForLoop(legs, block, start, end, ctx) : null);
    }

    /**
     * Tài xế chính cho cả vòng: các luật của findBestAvailableDriver, áp cho cả khối. Phần giờ lái của một
     * lượt là phần của tài xế chính khi lượt có ⌈giờ ÷ 8⌉ tài xế, như màn Đề xuất vẫn truyền vào hàm gốc.
     */
    private Driver driverForLoop(List<Trip> legs, LocationRule.Leg block, LocalDateTime start, LocalDateTime end,
            AvailabilityContext ctx) {
        LocalDateTime windowStart = start.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = end.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        java.util.Map<java.time.LocalDate, Double> loopHours = new java.util.TreeMap<>();
        for (Trip leg : legs) {
            double hours = Duration.between(leg.getDepartureTime(), arrivalOf(leg)).toMinutes() / 60.0;
            int drivers = Math.max(1, (int) Math.ceil(hours / 8.0));
            loopHours.merge(leg.getDepartureTime().toLocalDate(), Math.min(hours / drivers, 8.0), Double::sum);
        }
        List<Driver> able = driverRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(d -> legs.stream().allMatch(leg -> d.isLicenseValid(leg.getDepartureTime().toLocalDate())))
                .filter(d -> loopHours.entrySet().stream().allMatch(day ->
                        getDrivingHoursForDate(d, day.getKey().atStartOfDay(), null, ctx) + day.getValue() <= 8.0))
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null, ctx))
                .filter(d -> block == null
                        || LocationRule.fits(personLegsAround(d, start, end, ctx), idOf(d.getHomeStation()), block))
                .collect(Collectors.toList());

        // Ít giờ lái nhất trong các ngày của vòng trước — nhớ theo người, như drivingHoursKey.
        java.util.Map<Driver, Double> memo = new java.util.IdentityHashMap<>();
        Comparator<Driver> leastHours = Comparator.comparingDouble(d -> memo.computeIfAbsent(d,
                person -> loopHours.keySet().stream()
                        .mapToDouble(day -> getDrivingHoursForDate(person, day.atStartOfDay(), null, ctx)).sum()));
        Driver best = able.stream()
                .filter(d -> d.getLicenseExpiryDate() != null
                        && d.getLicenseExpiryDate().isAfter(start.toLocalDate().plusDays(7)))
                .min(leastHours)
                .orElse(null);
        if (best != null) {
            return best;
        }
        Driver fallback = able.stream().min(leastHours).orElse(null);
        if (fallback != null) {
            log.warn("⚠️ [AI] Tài xế {} được chọn cho cả vòng nhưng bằng lái SẮP HẾT HẠN (vào {})!",
                    fallback.getUser().getFullName(), fallback.getLicenseExpiryDate());
        }
        return fallback;
    }

    /** Cả khối như MỘT lượt, từ bến đầu của lượt đầu tới bến cuối của lượt cuối; null khi thiếu bến (không xét vị trí). */
    private static LocationRule.Leg blockLeg(Trip first, Trip last, LocalDateTime start, LocalDateTime end) {
        Long from = idOf(first.getRoute().getDepartureStation());
        Long to = idOf(last.getRoute().getDestinationStation());
        return from == null || to == null || end.isBefore(start) ? null : new LocationRule.Leg(from, to, start, end);
    }

    /** Quãng đường của một chuyến theo tuyến; tuyến thiếu số km thì 0, như các luật bảo trì. */
    private static double distanceOf(Trip trip) {
        return trip.getRoute() != null && trip.getRoute().getDistanceKm() != null ? trip.getRoute().getDistanceKm()
                : 0.0;
    }

    // =========================================================================
    // BẢO TRÌ TRONG LỊCH — route pairs CT7d (CM-25, cách M1)
    // =========================================================================
    //
    // Trước CT7d km chỉ được "trừ" khi Admin ghi nhận một lần bảo trì (BusService: km lần bảo trì cuối =
    // odometer). Lịch nhìn trước (CM-21) cứ cộng km đã xếp mà không bao giờ trừ, nên một xe chạy vòng Đà
    // Lạt (~450 km/ngày) rơi khỏi lịch sau chừng 10 ngày và cặp đó phải chạy xe khác loại (CM-25: 27/11–03/12
    // chỉ 9/42 chuyến giường nằm). Từ CT7d:
    //   R1 NGÀY BẢO TRÌ: Bus.plannedMaintenanceDate = ngày M xe nằm ở bến nhà để bảo trì. Không chuyến nào
    //      của xe được chạm vào [M 00:00, M+1 00:00) — máy, ô chọn và validator như nhau. Với luật vị trí,
    //      ngày đó là một lượt bến nhà → bến nhà, nên máy cũng không để xe ở bến xa vào ngày đó;
    //   R2 KM TÍNH LẠI TỪ 0: chuyến khởi hành từ M+1 00:00 tính km từ 0 thay vì km thật hiện tại — máy, ô
    //      chọn và validator như nhau. Ngày M đã qua mà Admin chưa ghi nhận bảo trì thì R1, R2 hết tác dụng
    //      (Bus.hasPlannedMaintenance): số liệu nói xe chưa được bảo trì;
    //   R3 MÁY CỘNG KM ĐÃ XẾP — cách hiểu của CM-21 mở cho mọi bộ chọn của máy (lịch, màn Đề xuất, job 10
    //      giây): km tính từ lần bảo trì (R2) + km mọi chuyến chưa xong của xe trong CÙNG đoạn (trước M hay
    //      sau M) + km của chuyến đang xét;
    //   R4 PHẢI VỀ ĐƯỢC BẾN NHÀ: chuyến (hay vòng) kết thúc xa bến nhà của xe thì máy cộng thêm quãng đường
    //      về. Bảo trì làm ở bến nhà và mốc 90% chặn MỌI chuyến, nên một xe chỉ nên rời bến nhà khi còn về
    //      được dưới mốc; không thì nó kẹt ở bến xa. Ví dụ trên lịch 20–26/11: tối 26/11 xe 29A-001.15 ở Đà
    //      Lạt với 4.420 km tính cả lịch, về thêm 300 km là 4.720 ≥ 4.500;
    //   R5 THỨ TỰ CỦA MÁY: đúng loại gợi ý trước, rồi km tính cả lịch (R2 + R3) ít nhất — xe vừa bảo trì
    //      theo lịch được dùng trước. Ô chọn của người vẫn xếp theo km thật (preferredTypeThenLeastWorn).
    // Validator và ô chọn của người KHÔNG cộng km đã xếp (R3) hay quãng về (R4): chúng vẫn chỉ xét chuyến
    // đang lưu, như trước — máy chặt hơn cổng, không bao giờ lỏng hơn.
    // Ai đặt ngày M: bộ lập lịch thường lệ (planDueMaintenance), khi một xe đúng loại của tuyến đang ở bến
    // nhà, mọi luật khác cho chạy, chỉ luật km chặn. Xe đang sát / quá hạn NGAY LÚC NÀY theo km thật (như các
    // xe mẫu "Xe Sắp Bảo Trì", "Xe Quá Bảo Trì") thì vẫn như cũ: Admin bảo trì rồi ghi nhận.

    /** Ngày bảo trì dự kiến còn hiệu lực hôm nay (R1, R2), hay null. */
    private static java.time.LocalDate activeMaintenanceDate(Bus bus) {
        return bus.hasPlannedMaintenance(java.time.LocalDate.now()) ? bus.getPlannedMaintenanceDate() : null;
    }

    /** R1: khoảng [start, end] chạm ngày bảo trì dự kiến của xe (chạm đúng 00:00 hai đầu thì không). */
    boolean onPlannedMaintenanceDay(Bus bus, LocalDateTime start, LocalDateTime end) {
        java.time.LocalDate day = activeMaintenanceDate(bus);
        return day != null && start.isBefore(day.plusDays(1).atStartOfDay()) && end.isAfter(day.atStartOfDay());
    }

    /** R2: chuyến khởi hành sau ngày bảo trì dự kiến của xe — km tính lại từ 0. */
    private static boolean servicedBefore(Bus bus, LocalDateTime departure) {
        java.time.LocalDate day = activeMaintenanceDate(bus);
        return day != null && !departure.isBefore(day.plusDays(1).atStartOfDay());
    }

    /** R2: km tính từ lần bảo trì, ở thời điểm departure — 0 sau ngày bảo trì dự kiến, ngược lại km thật. */
    private static double kmSinceMaintenanceAt(Bus bus, LocalDateTime departure) {
        return servicedBefore(bus, departure) ? 0.0 : bus.getKmSinceLastMaintenance();
    }

    /**
     * R2 + R3: km của máy ngay trước khoảng [start, end] — km tính từ lần bảo trì cộng km mọi chuyến chưa xong
     * của xe trong cùng đoạn (trước hay sau ngày bảo trì dự kiến). ctx == null → hỏi DB (job 10 giây).
     */
    double kmTowardMaintenance(Bus bus, LocalDateTime start, LocalDateTime end, AvailabilityContext ctx) {
        java.time.LocalDate day = activeMaintenanceDate(bus);
        LocalDateTime from = null;
        LocalDateTime to = null;
        if (day != null && servicedBefore(bus, start)) {
            from = day.plusDays(1).atStartOfDay();
        } else if (day != null) {
            to = day.atStartOfDay();
        }
        double committed = ctx != null ? ctx.committedKm(bus.getId(), from, to)
                : tripRepository.sumCommittedKmOfBus(bus, UNFINISHED_STATUSES, from, to);
        return kmSinceMaintenanceAt(bus, start) + committed;
    }

    /** R4: quãng về bến nhà sau một chuyến (hay vòng) kết thúc trên tuyến lastRoute; 0 khi kết thúc ở bến nhà hay không biết bến. */
    private static double homewardKm(Bus bus, Route lastRoute, double lastKm) {
        Long home = idOf(bus.getHomeStation());
        Long end = lastRoute != null ? idOf(lastRoute.getDestinationStation()) : null;
        return home == null || end == null || home.equals(end) ? 0.0 : lastKm;
    }

    /** R1–R4 cho máy: xe chạy được [start, end] với km đó (và quãng về) mà chưa chạm mốc 90%. */
    private boolean machineMaintenanceAllows(Bus bus, double km, double homewardKm, LocalDateTime start,
            LocalDateTime end, AvailabilityContext ctx) {
        return !onPlannedMaintenanceDay(bus, start, end)
                && !bus.reachesNearMaintenance(kmTowardMaintenance(bus, start, end, ctx) + km + homewardKm);
    }

    /**
     * R5: thứ tự của máy — xe đúng loại gợi ý trước (như preferredTypeThenLeastWorn), rồi km tính cả lịch ít
     * nhất. Nhớ km theo từng xe (cùng ý với drivingHoursKey), vì đường DB hỏi một câu cho mỗi xe.
     */
    private Comparator<Bus> machineOrder(BusType preferredType, LocalDateTime start, LocalDateTime end,
            AvailabilityContext ctx) {
        java.util.Map<Bus, Double> memo = new java.util.IdentityHashMap<>();
        return Comparator.comparing((Bus bus) -> !isOfType(bus, preferredType))
                .thenComparingDouble(bus -> memo.computeIfAbsent(bus, b -> kmTowardMaintenance(b, start, end, ctx)));
    }

    /** Lý do validator từ chối một chuyến chạm ngày bảo trì dự kiến (R1). */
    private static String plannedMaintenanceRefusal(Bus bus) {
        return "Xe " + bus.getLicensePlate() + " có lịch bảo trì dự kiến ngày "
                + bus.getPlannedMaintenanceDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " ở bến nhà (do Lịch thường lệ xếp) — không xếp chuyến nào chạm vào ngày đó. Hãy chọn xe khác; nếu xe"
                + " đã được bảo trì, hãy ghi nhận ở màn Quản Lý Xe (km lần bảo trì cuối = odometer)!";
    }

    /** Sổ km đã cam kết cho context (R3): mọi chuyến chưa xong có xe, mọi ngày — một câu SQL. */
    private AvailabilityContext withCommittedKm(AvailabilityContext ctx) {
        for (Object[] row : tripRepository.findCommittedKm(UNFINISHED_STATUSES)) {
            if (row[0] != null && row[1] != null && row[2] != null) {
                ctx.addCommitted(((Number) row[0]).longValue(), (LocalDateTime) row[1], ((Number) row[2]).doubleValue());
            }
        }
        return ctx;
    }

    /** Một ngày bảo trì vừa được bộ lập lịch đặt: xe, ngày, và km tính cả lịch tới đầu ngày đó. */
    public record PlannedMaintenance(Bus bus, java.time.LocalDate date, double kmBefore) {
    }

    /**
     * Bộ lập lịch thường lệ (CT7d, CM-25 M1), trước khi xếp chuyến {@code trip}: đặt ngày bảo trì cho các xe
     * đã tới hạn THEO LỊCH. Một xe được đặt khi đủ cả năm điều kiện:
     * <ol>
     * <li>đúng loại gợi ý của tuyến (xe không chạy thêm được một lượt của chính loại tuyến nó phục vụ);</li>
     * <li>đang đứng ở bến nhà, tức bến xuất phát của chuyến — bảo trì làm ở bến nhà;</li>
     * <li>chưa có ngày bảo trì dự kiến còn hiệu lực — mỗi xe một ngày;</li>
     * <li>km thật chưa vào vùng cảnh báo (xe sát / quá hạn ngay lúc này thì Admin bảo trì như cũ);</li>
     * <li>mọi luật khác của máy (bận, vị trí) cho chạy chuyến này, chỉ luật km (R2–R4) chặn.</li>
     * </ol>
     * Ngày được chọn là ngày đầu tiên, từ ngày của chuyến tới lastDay, mà xe không chạy chuyến nào và nằm
     * trọn ở bến nhà (một lượt bến nhà → bến nhà trọn ngày vừa chuỗi vị trí của xe). Không có ngày nào thì
     * không đặt — xe bị loại như trước. Ghi vào xe (lưu cùng transaction của lần bấm "Tạo lịch").
     */
    public List<PlannedMaintenance> planDueMaintenance(Trip trip, java.time.LocalDate lastDay, AvailabilityContext ctx) {
        Route route = trip.getRoute();
        Long from = route != null ? idOf(route.getDepartureStation()) : null;
        if (from == null || ctx == null || !ctx.isLookAhead()) {
            return List.of();
        }
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = arrivalOf(trip);
        double km = distanceOf(trip);
        BusType preferredType = route.getSuitableBusType();
        java.time.LocalDate today = java.time.LocalDate.now();
        List<PlannedMaintenance> planned = new java.util.ArrayList<>();
        for (Bus bus : ctx.schedulableBuses()) {
            if (!from.equals(idOf(bus.getHomeStation())) || preferredType != null && !isOfType(bus, preferredType)
                    || bus.hasPlannedMaintenance(today) || bus.needsMaintenance() || bus.isNearMaintenance(0)
                    || isBusBusy(bus, departure.minusHours(BUS_PREP_BUFFER_HOURS),
                            arrival.plusHours(BUS_PREP_BUFFER_HOURS), null, ctx)
                    || !busFits(bus, route, departure, arrival, ctx)
                    || !bus.reachesNearMaintenance(kmTowardMaintenance(bus, departure, arrival, ctx) + km
                            + homewardKm(bus, route, km))) {
                continue;
            }
            java.time.LocalDate day = firstMaintenanceDay(bus, departure.toLocalDate(), lastDay, ctx);
            if (day == null) {
                continue;
            }
            double kmBefore = kmTowardMaintenance(bus, day.atStartOfDay(), day.atStartOfDay(), ctx);
            bus.setPlannedMaintenanceDate(day);
            busRepository.save(bus);
            planned.add(new PlannedMaintenance(bus, day, kmBefore));
            log.info("🔧 [Lịch thường lệ] Xe {} sắp chạm mốc bảo trì ({} km tính cả lịch) — bảo trì ở bến nhà ngày {}",
                    bus.getLicensePlate(), Math.round(kmBefore), day);
        }
        return planned;
    }

    /** Ngày đầu tiên trong [first, last] mà xe không chạy chuyến nào và nằm trọn ở bến nhà; null nếu không có. */
    private java.time.LocalDate firstMaintenanceDay(Bus bus, java.time.LocalDate first, java.time.LocalDate last,
            AvailabilityContext ctx) {
        Long home = idOf(bus.getHomeStation());
        for (java.time.LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            if (!isBusBusy(bus, start, end, null, ctx)
                    && LocationRule.fits(busLegsAround(bus, start, end, ctx), home, new LocationRule.Leg(home, home, start, end))) {
                return day;
            }
        }
        return null;
    }
}