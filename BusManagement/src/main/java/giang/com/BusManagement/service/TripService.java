package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.*;
import giang.com.BusManagement.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

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
     * AI Scheduling Job: Mỗi 10 giây quét các chuyến ACTIVE có tỉ lệ lấp đầy > 90%.
     * Khi phát hiện, tự động tạo chuyến tăng cường VÀ phân công đầy đủ
     * (xe + tài xế + phụ xe) theo ràng buộc hệ thống.
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
        List<Trip> activeTrips = tripRepository.findByStatusWithRoute(TripStatus.ACTIVE);

        for (Trip trip : activeTrips) {
            if (isHotTrip(trip) && !hasAlreadySuggested(trip)) {
                createExtraTrip(trip);
            }
        }
    }

    /**
     * Returns true when a trip genuinely needs an extra trip right now.
     *
     * Three time-based guards (on top of the existing occupancy check):
     * 1. Departure must still be in the future.
     * 2. Enough lead time left for customers to actually buy tickets on the new
     * trip.
     * 3. Trip has been on sale long enough for its occupancy to be meaningful
     * signal,
     * unless it's already past the instant-hot threshold.
     */
    private boolean isHotTrip(Trip trip) {
        // ── Existing occupancy gate ──────────────────────────────────────────────
        if (trip.getOccupancyRate() <= 0.9) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // ── Gate 3: departure must still be in the future ───────────────────────
        if (trip.getDepartureTime() == null || !trip.getDepartureTime().isAfter(now)) {
            System.out.printf("⏩ [AI] Chuyến #%d: đã khởi hành, bỏ qua.%n", trip.getId());
            return false;
        }

        // ── Gate 4: enough lead time remaining ──────────────────────────────────
        long hoursUntilDeparture = trip.getHoursUntilDeparture();
        if (hoursUntilDeparture < MIN_HOURS_BEFORE_DEPARTURE) {
            System.out.printf(
                    "⏩ [AI] Chuyến #%d: còn %.1f giờ đến khởi hành (< %d giờ yêu cầu). Không đủ thời gian mở vé mới.%n",
                    trip.getId(), (double) hoursUntilDeparture, MIN_HOURS_BEFORE_DEPARTURE);
            return false;
        }

        // ── Gate 5: sale-velocity sanity check ──────────────────────────────────
        // Skip wait if occupancy is already above the instant-hot threshold.
        if (trip.getOccupancyRate() < INSTANT_HOT_THRESHOLD) {
            long hoursOnSale = trip.getHoursOnSale();
            if (hoursOnSale < MIN_SALE_OPEN_HOURS) {
                System.out.printf(
                        "⏩ [AI] Chuyến #%d: chỉ mới mở bán %.0f giờ (< %d giờ yêu cầu). Có thể là spike ảo.%n",
                        trip.getId(), (double) hoursOnSale, MIN_SALE_OPEN_HOURS);
                return false;
            }
        }

        System.out.printf(
                "🔥 [AI] Chuyến #%d HOT: %.1f%% ghế, còn %d giờ, đã mở bán %d giờ.%n",
                trip.getId(),
                trip.getOccupancyRate() * 100,
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
        System.out.printf("🤖 [AI] Chuyến #%d đạt %.1f%% lấp đầy. Đang tạo chuyến tăng cường...%n",
                trip.getId(), trip.getOccupancyRate() * 100);

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
        extraTrip.setTotalSeats(trip.getTotalSeats());
        extraTrip.setPrice(trip.getPrice());
        extraTrip.setExtraTrip(true);
        extraTrip.setOriginalTrip(trip); // trip đã tồn tại trong DB, gán trực tiếp không vi phạm FK

        // --- Bước 3: AI phân công tài nguyên ---
        AutoAssignResult result = autoAssignResources(extraTrip);

        if (result.isSuccess()) {
            extraTrip.setBus(result.getBus());
            extraTrip.setDriver(result.getDriver());
            extraTrip.setAssistant(result.getAssistant());
            extraTrip.getCoDrivers().clear();
            extraTrip.getCoDrivers().addAll(result.getCoDrivers());

            String assistantName = result.getAssistant() != null
                    ? result.getAssistant().getUser().getFullName()
                    : "Không có";

            System.out.printf("✅ [AI] Phân công thành công: Xe %s | Tài xế chính: %s | Số tài phụ: %d | Phụ xe: %s%n",
                    result.getBus().getLicensePlate(),
                    result.getDriver().getUser().getFullName(),
                    result.getCoDrivers().size(),
                    assistantName);
        } else {
            System.out.printf("⚠️ [AI] Không tự phân công được: %s → Admin xử lý thủ công.%n",
                    result.getFailureReason());
        }

        tripRepository.save(extraTrip);
    }

    // =========================================================================
    // CORE AI: Auto-assign resources
    // =========================================================================

    /**
     * Thuật toán trung tâm: Tự động phân công xe + tài xế chính + phụ xe
     * cho một chuyến xe, tuân theo tất cả ràng buộc hệ thống.
     */
    private AutoAssignResult autoAssignResources(Trip trip) {
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected();
        double tripDurationHours = Duration.between(departure, arrival).toMinutes() / 60.0;

        // Bước 1: Tìm xe phù hợp
        Bus bus = findBestAvailableBus(trip, departure, arrival);
        if (bus == null) {
            return AutoAssignResult.failure(
                    "Không có xe nào sẵn sàng / đúng loại / không bận trong khung giờ này");
        }

        // Tính toán số tài xế cần thiết
        int requiredDrivers = (int) Math.ceil(tripDurationHours / 8.0);
        if (requiredDrivers < 1)
            requiredDrivers = 1;

        // Danh sách những người bị loại trừ (tránh trùng lặp)
        java.util.List<Driver> assignedStaff = new java.util.ArrayList<>();

        // Bước 2: Tìm tài xế chính
        Driver driver = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers, assignedStaff);
        if (driver == null) {
            return AutoAssignResult.failure(
                    "Không có tài xế chính hợp lệ (cần bằng còn hạn, rảnh, và đủ định mức ngày)");
        }
        assignedStaff.add(driver);

        // Bước 3: Tìm các tài xế phụ (nếu chuyến dài > 8h)
        java.util.List<Driver> coDrivers = new java.util.ArrayList<>();
        for (int i = 1; i < requiredDrivers; i++) {
            Driver coDriver = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers,
                    assignedStaff);
            if (coDriver == null) {
                return AutoAssignResult.failure(
                        "Không tìm đủ số lượng tài xế phụ hợp lệ cho chuyến dài " + Math.round(tripDurationHours)
                                + "h (cần " + requiredDrivers + " tài xế)");
            }
            coDrivers.add(coDriver);
            assignedStaff.add(coDriver);
        }

        // Bước 4: Tìm phụ xe (nếu chuyến > 8h thì phụ xe là BẮT BUỘC, ngược lại là tùy
        // chọn)
        Driver assistant = findBestAvailableDriver(departure, arrival, tripDurationHours, requiredDrivers,
                assignedStaff);
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
     * Tìm xe READY, đúng loại, không bận, chưa quá hạn/sắp đến hạn bảo trì.
     * Sắp xếp ưu tiên: xe ít km kể từ bảo trì nhất (xe "mới nhất") được chọn trước.
     *
     * Dùng CHUNG ràng buộc bảo trì với validateBusForTrip()/Bus.isNearMaintenance()
     * (>= 90% maintenanceThreshold sau khi cộng quãng đường chuyến này) để AI
     * auto-assign không bao giờ chọn một xe mà luồng thủ công (Create/Edit/Approve)
     * sẽ từ chối ngay sau đó. Không còn fallback "vẫn chọn xe sát/quá ngưỡng" —
     * nếu không còn xe nào đủ điều kiện, trả về null để autoAssignResources() báo
     * thất bại và Admin xử lý thủ công (đổi xe khác hoặc đưa xe đi bảo trì).
     */
    private Bus findBestAvailableBus(Trip trip, LocalDateTime departure, LocalDateTime arrival) {
        BusType requiredType = trip.getRoute().getSuitableBusType();

        // Lấy tất cả xe READY (và đúng loại nếu tuyến quy định)
        List<Bus> candidates = (requiredType != null)
                ? busRepository.findByStatusAndBusType(BusStatus.READY, requiredType)
                : busRepository.findByStatus(BusStatus.READY);

        // Mở rộng cửa sổ thời gian để đảm bảo xe kịp chuẩn bị
        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        double tripDistance = trip.getRoute().getDistanceKm() != null ? trip.getRoute().getDistanceKm() : 0.0;

        return candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, null))
                .filter(bus -> !bus.needsMaintenance())
                .filter(bus -> !bus.isNearMaintenance(tripDistance))
                .min(Comparator.comparingDouble(Bus::getKmSinceLastMaintenance))
                .orElse(null);
    }

    /**
     * Kiểm tra xe có bị gán cho chuyến khác trong cửa sổ thời gian [windowStart,
     * windowEnd] không.
     */
    private boolean isBusBusy(Bus bus, LocalDateTime windowStart, LocalDateTime windowEnd, Long excludeTripId) {
        return tripRepository.existsOverlappingTripForBus(
                bus,
                List.of(TripStatus.ACTIVE, TripStatus.DEPARTED, TripStatus.PENDING_APPROVAL),
                windowStart,
                windowEnd,
                excludeTripId);
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
     * @param excludeDriver Tài xế cần loại trừ (null nếu đang tìm tài xế chính)
     */
    private Driver findBestAvailableDriver(LocalDateTime departure, LocalDateTime arrival,
            double tripDurationHours, int totalDriversCount, java.util.Collection<Driver> excludeDrivers) {
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
                // Ràng buộc: bằng lái phải còn hạn
                .filter(Driver::isLicenseValid)
                // Ràng buộc: tổng giờ lái hôm nay + thời lượng chuyến không được vượt 8 tiếng
                // Tính chính xác dựa trên CÁC CHUYẾN ĐÃ XẾP TRONG NGÀY với lượng giờ chia sẻ
                // thực tế
                .filter(d -> getDrivingHoursForDate(d, departure, null) + effectiveHours <= 8.0)
                // Ràng buộc: không đang bận (cả vai trò tài xế lẫn phụ xe)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null))
                .collect(Collectors.toList());

        // Ưu tiên 1: Bằng lái còn hạn trên 7 ngày
        Driver best = availableDrivers.stream()
                .filter(d -> d.getLicenseExpiryDate() != null
                        && d.getLicenseExpiryDate().isAfter(departure.toLocalDate().plusDays(7)))
                .min(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, null)))
                .orElse(null);

        if (best != null)
            return best;

        // Fallback: Bằng lái sắp hết hạn (< 7 ngày) nhưng vẫn còn hạn
        Driver fallback = availableDrivers.stream()
                .min(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, null)))
                .orElse(null);

        if (fallback != null) {
            System.out.printf("⚠️ [AI] Tài xế/Phụ xe %s được chọn nhưng bằng lái SẮP HẾT HẠN (vào %s)!%n",
                    fallback.getUser().getFullName(), fallback.getLicenseExpiryDate());
        }

        return fallback;
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
        List<TripStatus> busyStatuses = List.of(
                TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED);

        // Bận với tư cách tài xế chính?
        boolean busyAsDriver = tripRepository.existsOverlappingTripForDriver(
                driver, busyStatuses, windowStart, windowEnd, excludeTripId);
        if (busyAsDriver)
            return true;

        // Bận với tư cách phụ xe?
        return tripRepository.existsOverlappingTripForAssistant(
                driver, busyStatuses, windowStart, windowEnd, excludeTripId);
    }

    /**
     * Tính tổng số giờ lái xe của tài xế trong một ngày cụ thể.
     */
    private double getDrivingHoursForDate(Driver driver, LocalDateTime date, Long excludeTripId) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<TripStatus> busyStatuses = List.of(TripStatus.PENDING_APPROVAL, TripStatus.ACTIVE, TripStatus.DEPARTED);

        List<Trip> trips = tripRepository.findTripsForDriverOnDate(driver, busyStatuses, startOfDay,
                endOfDay,
                excludeTripId);

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

    // =========================================================================
    // APPROVAL ACTIONS
    // =========================================================================

    /**
     * Admin xác nhận chuyến đã được AI phân công đầy đủ — chỉ cần 1 click.
     * Hệ thống kiểm tra lại ràng buộc trước khi kích hoạt.
     */
    @Transactional
    public String confirmAutoAssignedTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe #" + tripId));

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

        System.out.printf("✅ Admin xác nhận chuyến #%d | Xe: %s | Tài xế chính: %s | Số tài xế phụ: %d | Phụ xe: %s%n",
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
     */
    @Transactional
    public String approveTrip(Long tripId, Long busId, Long driverId, Long assistantId,
            java.util.List<Long> coDriverIds) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe #" + tripId));

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy xe #" + busId));

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế #" + driverId));

        trip.setBus(bus);
        trip.setDriver(driver);

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

        System.out.printf("✅ Admin phân công thủ công chuyến #%d | Xe: %s | Tài xế: %s%n",
                tripId, bus.getLicensePlate(), driver.getUser().getFullName());

        return warning;
    }

    /**
     * Admin tạo chuyến thủ công từ form
     */
    @Transactional
    public String createManualTrip(Trip trip) {
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
     * - Xe đang REPAIRING hoặc TRAVELING.
     * - Xe đang bận (trùng lịch) trong cửa sổ [departure - 1h, arrival + 1h].
     * - Xe đã QUÁ HẠN bảo trì (needsMaintenance() == true ngay hiện tại).
     * - Quãng đường của chuyến này sẽ đẩy xe vào vùng SẮP/QUÁ hạn bảo trì
     * (>= 90% maintenanceThreshold sau khi cộng route.distanceKm).
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
        if (bus.getStatus() == BusStatus.TRAVELING) {
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate()
                    + " đang trên đường (TRAVELING), không thể gán vào chuyến mới cho đến khi hoàn thành chuyến hiện tại!");
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
        if (bus.needsMaintenance()) {
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " (Odo: "
                    + Math.round(bus.getKmSinceLastMaintenance()) + "km) đã QUÁ HẠN bảo trì (ngưỡng: "
                    + Math.round(bus.getMaintenanceThreshold())
                    + "km) — không thể gán vào chuyến cho đến khi được bảo trì!");
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
        if (bus.isNearMaintenance(tripDistance)) {
            throw new IllegalArgumentException("Xe " + bus.getLicensePlate() + " (Odo: "
                    + Math.round(bus.getKmSinceLastMaintenance()) + "km) sẽ SẮP/QUÁ ngưỡng bảo trì ("
                    + Math.round(bus.getMaintenanceThreshold())
                    + "km) sau chuyến này — vui lòng chọn xe khác hoặc đưa xe đi bảo trì trước!");
        }

        return null;
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
        if (!driver.isLicenseValid()) {
            throw new IllegalArgumentException(
                    "Bằng lái của tài xế chính " + driver.getUser().getFullName() + " đã hết hạn!");
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
                if (!cd.isLicenseValid()) {
                    throw new IllegalArgumentException(
                            "Bằng lái của tài xế phụ " + cd.getUser().getFullName() + " đã hết hạn!");
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
            if (isDriverBusyInWindow(assistant, windowStart, windowEnd, excludeTripId)) {
                throw new IllegalArgumentException(
                        "Phụ xe " + assistant.getUser().getFullName() + " đang bận ở chuyến khác!");
            }
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
        System.out.printf("❌ Admin từ chối chuyến tăng cường #%d%n", tripId);
    }

    // =========================================================================
    // QUERY METHODS (dùng cho UI form)
    // =========================================================================

    /**
     * Lấy xe sẵn sàng, không bận, và chưa quá hạn/sắp đến hạn bảo trì cho
     * chuyến (dùng cho form phân công thủ công — AdminTripController approve
     * form). Áp dụng cùng ràng buộc bảo trì với validateBusForTrip() để dropdown
     * không bao giờ hiển thị một xe mà hệ thống sẽ từ chối khi submit.
     */
    public List<Bus> getAvailableBusesForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        BusType requiredType = trip.getRoute().getSuitableBusType();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);

        LocalDateTime windowStart = departure.minusHours(BUS_PREP_BUFFER_HOURS);
        LocalDateTime windowEnd = arrival.plusHours(BUS_PREP_BUFFER_HOURS);

        double tripDistance = trip.getRoute() != null && trip.getRoute().getDistanceKm() != null
                ? trip.getRoute().getDistanceKm()
                : 0.0;

        List<Bus> candidates = (requiredType != null)
                ? busRepository.findByStatusAndBusType(BusStatus.READY, requiredType)
                : busRepository.findByStatus(BusStatus.READY);

        return candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, tripId))
                .filter(bus -> !bus.needsMaintenance())
                .filter(bus -> !bus.isNearMaintenance(tripDistance))
                .sorted(Comparator.comparingDouble(Bus::getKmSinceLastMaintenance))
                .collect(Collectors.toList());
    }

    /**
     * Lấy tài xế hợp lệ & rảnh cho chuyến (dùng cho form phân công thủ công).
     */
    public List<Driver> getAvailableDriversForTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTimeExpected() != null
                ? trip.getArrivalTimeExpected()
                : departure.plusHours(5);
        double durationHours = Duration.between(departure, arrival).toMinutes() / 60.0;
        double effectiveHours = Math.min(durationHours, 8.0);

        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(Driver::isLicenseValid)
                .filter(d -> getDrivingHoursForDate(d, departure, tripId) + effectiveHours <= 8.0)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, tripId))
                .sorted(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, tripId)))
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
                .filter(bus -> !bus.needsMaintenance())
                .filter(bus -> !bus.isNearMaintenance(0))
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
     * @param departure Thời gian khởi hành dự kiến
     * @param arrival   Thời gian đến dự kiến
     * @return Danh sách tài xế rảnh, sắp xếp theo tổng giờ lái ít nhất
     */
    public List<Driver> getAvailableDriversForTimeRange(LocalDateTime departure, LocalDateTime arrival) {
        double durationHours = Duration.between(departure, arrival).toMinutes() / 60.0;
        // effectiveHours: phần giờ mà mỗi tài xế phải lái (cắt tối đa 8h/người)
        double effectiveHours = Math.min(durationHours, 8.0);

        LocalDateTime windowStart = departure.minusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);
        LocalDateTime windowEnd = arrival.plusMinutes(MIN_REST_BETWEEN_TRIPS_MINUTES);

        return driverRepository.findAllWithUser().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .filter(Driver::isLicenseValid)
                .filter(d -> getDrivingHoursForDate(d, departure, null) + effectiveHours <= 8.0)
                .filter(d -> !isDriverBusyInWindow(d, windowStart, windowEnd, null))
                .sorted(Comparator.comparingDouble(d -> getDrivingHoursForDate(d, departure, null)))
                .collect(Collectors.toList());
    }

    /**
     * Kiểm tra xem chuyến gốc đã có một chuyến tăng cường đang "sống" hay không.
     *
     * Các trạng thái bị chặn (không cho tạo thêm):
     * - PENDING_APPROVAL : đang chờ Admin duyệt
     * - ACTIVE : đã được kích hoạt, đang bán vé
     * - DEPARTED : đã xuất phát (nếu không chặn → AI sẽ tạo lại liên tục)
     * - COMPLETED : đã hoàn thành (sự kiện đã xảy ra, không cần thêm nữa)
     *
     * CANCELLED bị loại khỏi danh sách chặn để AI được phép đề xuất lại khi
     * chuyến tăng cường cũ bị hủy (ví dụ: xe hỏng, tài xế ốm).
     */
    private boolean hasAlreadySuggested(Trip originalTrip) {
        List<TripStatus> blockingStatuses = List.of(
                TripStatus.PENDING_APPROVAL,
                TripStatus.ACTIVE,
                TripStatus.DEPARTED,
                TripStatus.COMPLETED);
        return tripRepository.existsByOriginalTripAndStatusIn(originalTrip, blockingStatuses);
    }

    public List<Trip> getPendingTrips() {
        return tripRepository.findByStatusWithDetails(TripStatus.PENDING_APPROVAL);
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

        switch (trip.getStatus()) {
            case DEPARTED -> throw new IllegalStateException(
                    "Chuyến #" + tripId + " đang trên đường (DEPARTED). " +
                            "Không thể xóa chuyến đang vận hành — sẽ phá vỡ hành trình thực tế và dữ liệu GPS.");

            case COMPLETED -> throw new IllegalStateException(
                    "Chuyến #" + tripId + " đã hoàn thành (COMPLETED). " +
                            "Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên, không thể xóa.");

            case ACTIVE -> {
                if (trip.getTicketsSold() > 0) {
                    throw new IllegalStateException(String.format(
                            "Chuyến #%d đang ACTIVE và đã có %d vé bán ra. " +
                                    "Vui lòng dùng chức năng 'Hủy chuyến' thay vì xóa " +
                                    "để hệ thống xử lý hoàn vé và thông báo cho hành khách.",
                            tripId, trip.getTicketsSold()));
                }
                // ACTIVE nhưng chưa bán vé nào → an toàn để xóa mềm
            }

            // PENDING_APPROVAL và CANCELLED: cho phép xóa không điều kiện
            case PENDING_APPROVAL, CANCELLED -> {
                /* proceed */ }
        }

        // Kích hoạt @SQLDelete: phát ra UPDATE trips SET is_deleted=true WHERE id=?
        tripRepository.delete(trip);

        System.out.printf("🗑️ Admin xóa mềm chuyến #%d (trạng thái cũ: %s)%n",
                tripId, trip.getStatus());
    }

    /**
     * Hàm Helper chuyên trách việc chuyển trạng thái chuyến xe sang ACTIVE.
     * Đồng thời đóng dấu thời gian mở bán nếu chuyến xe chưa từng được mở bán.
     */
    private void changeStatusToActive(Trip trip) {
        trip.setStatus(TripStatus.ACTIVE);
        if (trip.getSaleOpenedAt() == null) {
            trip.setSaleOpenedAt(LocalDateTime.now());
        }
    }
}