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
            if (trip.getOccupancyRate() > 0.9 && !hasAlreadySuggested(trip)) {
                createExtraTrip(trip);
            }
        }
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
     * Tìm xe READY, đúng loại, không bận, chưa cần bảo trì.
     * Sắp xếp ưu tiên: xe ít km kể từ bảo trì nhất (xe "mới nhất") được chọn trước.
     *
     * Fallback: Nếu tất cả xe đều cần bảo trì thì vẫn chọn xe ít km nhất
     * (sẽ ghi cảnh báo ở log).
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

        // Ưu tiên xe chưa cần bảo trì VÀ sau chuyến này vẫn chưa vượt ngưỡng bảo trì
        Bus best = candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, null))
                .filter(bus -> {
                    Double threshold = bus.getMaintenanceThreshold();
                    if (threshold == null)
                        return true; // Không có ngưỡng → không cần kiểm tra
                    return bus.getKmSinceLastMaintenance() + tripDistance <= threshold;
                })
                .min(Comparator.comparingDouble(Bus::getKmSinceLastMaintenance))
                .orElse(null);

        if (best != null)
            return best;

        // Fallback: xe sẽ cần bảo trì sau chuyến này (hoặc đã quá hạn) nhưng vẫn READY
        // — cảnh báo và dùng
        Bus fallback = candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, null))
                .min(Comparator.comparingDouble(Bus::getKmSinceLastMaintenance))
                .orElse(null);

        if (fallback != null) {
            System.out.printf(
                    "⚠️ [AI] Xe %s được chọn nhưng SÁT/QUÁ NGƯỠNG BẢO TRÌ (Odo hiện tại: %.0f, ngưỡng: %.0f)!%n",
                    fallback.getLicensePlate(), fallback.getKmSinceLastMaintenance(),
                    fallback.getMaintenanceThreshold());
        }

        return fallback;
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

        trip.setStatus(newStatus);

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

        trip.setStatus(TripStatus.ACTIVE);
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

        trip.setStatus(TripStatus.ACTIVE);
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

        trip.setStatus(TripStatus.ACTIVE);
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
     * Kiểm tra xe có đang bận không và trả về cảnh báo nếu sắp đến hạn bảo trì.
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

        Double threshold = bus.getMaintenanceThreshold();
        double tripDistance = trip.getRoute() != null && trip.getRoute().getDistanceKm() != null
                ? trip.getRoute().getDistanceKm()
                : 0.0;
        if (threshold != null && bus.getKmSinceLastMaintenance() + tripDistance > threshold) {
            return "Cảnh báo: Xe " + bus.getLicensePlate() + " (Odo: " + Math.round(bus.getKmSinceLastMaintenance())
                    + ") sẽ chạm/vượt ngưỡng bảo trì (" + Math.round(threshold) + ") sau chuyến này!";
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
                if (!staffIds.add(cd.getUserId())) {
                    throw new IllegalArgumentException(
                            "Nhân sự phân công bị trùng lặp: Tài xế phụ trùng tài chính hoặc tài phụ khác!");
                }
            }
        }

        if (assistant != null) {
            if (!staffIds.add(assistant.getUserId())) {
                throw new IllegalArgumentException(
                        "Nhân sự phân công bị trùng lặp: Phụ xe trùng với tài xế chính hoặc tài xế phụ!");
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
     */
    @Transactional
    public void rejectTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe #" + tripId));
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);
        System.out.printf("❌ Admin từ chối chuyến tăng cường #%d%n", tripId);
    }

    // =========================================================================
    // QUERY METHODS (dùng cho UI form)
    // =========================================================================

    /**
     * Lấy xe sẵn sàng & không bận cho chuyến (dùng cho form phân công thủ công).
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

        List<Bus> candidates = (requiredType != null)
                ? busRepository.findByStatusAndBusType(BusStatus.READY, requiredType)
                : busRepository.findByStatus(BusStatus.READY);

        return candidates.stream()
                .filter(bus -> !isBusBusy(bus, windowStart, windowEnd, tripId))
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

    private boolean hasAlreadySuggested(Trip originalTrip) {
        return tripRepository.existsByOriginalTripAndStatus(originalTrip, TripStatus.PENDING_APPROVAL)
                || tripRepository.existsByOriginalTripAndStatus(originalTrip, TripStatus.ACTIVE)
                || tripRepository.existsByOriginalTripAndStatus(originalTrip, TripStatus.CANCELLED);
    }

    public List<Trip> getPendingTrips() {
        return tripRepository.findByStatusWithDetails(TripStatus.PENDING_APPROVAL);
    }

    public Trip getTripById(Long id) {
        return tripRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến xe với ID: " + id));
    }

}