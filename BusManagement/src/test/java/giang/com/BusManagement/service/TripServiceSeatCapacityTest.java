package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lỗi #22 — số ghế mở bán của một chuyến không được vượt sức chứa của xe được gán.
 *
 * Hai nửa của bản sửa, mỗi nửa một nhóm test:
 * <ul>
 * <li>{@code validateBusForTrip()} có thêm luật cứng {@code totalSeats <= capacity}
 * — chốt qua bản dry-run (cùng validator, không throw) và qua đường throw
 * ({@code createManualTrip}). Bán ÍT hơn sức chứa vẫn hợp lệ; xe không có loại thì
 * không kiểm.</li>
 * <li>{@code createExtraTrip()} (chạy trong {@code scanAndSuggestExtraTrips()}) đặt số
 * ghế của chuyến tăng cường theo xe AI chọn, KHÔNG chép của chuyến gốc — đây là nơi
 * sinh ra chuyến 40 ghế trên xe 22 chỗ trên DB thật. Khi không có xe nào để gán,
 * số của chuyến gốc được giữ làm chỗ trống, và luật ở validator sẽ so lại lúc Admin
 * phân công thủ công.</li>
 * </ul>
 *
 * Non-vacuous: bỏ luật trong validateBusForTrip() → 2 test đỏ; đổi
 * createExtraTrip() về chép số cũ → 1 test đỏ (đã probe khi viết).
 */
@SpringBootTest
@Transactional
class TripServiceSeatCapacityTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private UserRepository userRepository;

    private BusType limousine22;
    private Bus bus22;
    private Bus untypedBus;
    private Driver driver;
    /** Rảnh hoàn toàn, để AI có người mà gán cho chuyến tăng cường (tài xế của chuyến gốc bận đúng khung đó). */
    private Driver freeDriver;
    private Route route;
    private LocalDateTime departure;

    @BeforeEach
    void setUp() {
        limousine22 = new BusType();
        limousine22.setTypeName("Cap-22");
        limousine22.setCapacity(22);
        limousine22 = busTypeRepository.save(limousine22);

        bus22 = cleanBus("CAP-22", limousine22);
        untypedBus = cleanBus("CAP-NULL", null);

        driver = saveDriver("cap-driver");
        freeDriver = saveDriver("cap-free-driver");

        route = new Route();
        route.setDistanceKm(100.0);
        route.setEstimatedDuration(120);
        route.setSuitableBusType(limousine22);
        route = routeRepository.save(route);

        // Ngày mai, để giờ nền mock (chỉ cộng cho hôm nay) không nhiễu.
        departure = LocalDate.now().plusDays(1).atTime(8, 0);
    }

    // =====================================================================
    // Luật trong validateBusForTrip()
    // =====================================================================

    @Test
    void moreSeatsThanTheBusHas_isRejected_withTheCapacityInTheMessage() {
        Trip trip = tripFor(bus22, 30);

        ValidationResult r = tripService.validateBusForTripDryRun(bus22, trip, null);

        assertFalse(r.isValid());
        assertTrue(r.getFailureReason().contains("30 ghế"), r.getFailureReason());
        assertTrue(r.getFailureReason().contains("chỉ có 22 chỗ"), r.getFailureReason());
    }

    @Test
    void exactlyTheCapacity_andFewerSeats_areBothAccepted() {
        assertTrue(tripService.validateBusForTripDryRun(bus22, tripFor(bus22, 22), null).isValid(),
                "bán đúng sức chứa là hợp lệ");
        assertTrue(tripService.validateBusForTripDryRun(bus22, tripFor(bus22, 10), null).isValid(),
                "bán ít hơn sức chứa là quyết định kinh doanh, hợp lệ");
    }

    @Test
    void aBusWithoutAType_hasNoCapacityToCompareAgainst_soTheRuleIsSkipped() {
        assertTrue(tripService.validateBusForTripDryRun(untypedBus, tripFor(untypedBus, 99), null).isValid());
    }

    @Test
    void theThrowPath_createManualTrip_appliesTheSameRule() {
        Trip trip = tripFor(bus22, 30);
        trip.setStatus(TripStatus.ACTIVE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tripService.createManualTrip(trip));
        assertTrue(ex.getMessage().contains("chỉ có 22 chỗ"), ex.getMessage());

        // Đối trọng: cùng chuyến với 22 ghế thì tạo được.
        Trip ok = tripFor(bus22, 22);
        ok.setStatus(TripStatus.ACTIVE);
        tripService.createManualTrip(ok);
        assertEquals(22, tripRepository.findById(ok.getId()).orElseThrow().getTotalSeats());
    }

    // =====================================================================
    // Chuyến tăng cường của AI lấy số ghế theo xe được gán
    // =====================================================================

    @Test
    void aiExtraTrip_takesItsSeatCountFromTheAssignedBus_notFromTheOriginal() {
        Trip hot = hotOriginalTrip(40, 39); // 97,5 % — vượt cả ngưỡng instant-hot, nên không đợi 48h mở bán

        tripService.scanAndSuggestExtraTrips();

        Trip extra = extraTripOf(hot);
        assertEquals(bus22.getId(), extra.getBus().getId(), "AI phải chọn được xe 22 chỗ của tuyến");
        assertEquals(freeDriver.getUserId(), extra.getDriver().getUserId(), "và tài xế rảnh (tài xế chuyến gốc bận khung đó)");
        assertEquals(22, extra.getTotalSeats(), "số ghế phải theo xe được gán, không chép 40 của chuyến gốc");
        assertEquals(TripStatus.PENDING_APPROVAL, extra.getStatus());
    }

    @Test
    void aiExtraTrip_withNoBusAvailable_keepsTheOriginalCountAsAPlaceholder() {
        // Không còn xe READY nào đúng loại của tuyến: xe 22 chỗ đi bảo trì.
        bus22.setStatus(BusStatus.REPAIRING);
        busRepository.save(bus22);

        Trip hot = hotOriginalTrip(40, 39);

        tripService.scanAndSuggestExtraTrips();

        Trip extra = extraTripOf(hot);
        assertEquals(null, extra.getBus(), "không có xe nào để gán");
        assertEquals(40, extra.getTotalSeats(),
                "chưa có xe để so thì giữ số của chuyến gốc; validateBusForTrip() sẽ so lại khi phân công tay");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Trip tripFor(Bus bus, int seats) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(departure.plusHours(2));
        trip.setTotalSeats(seats);
        trip.setPrice(new BigDecimal("100000"));
        return trip;
    }

    /**
     * Chuyến gốc ACTIVE đang "đông": khởi hành sau 5 ngày (qua cửa 72h), trên một xe
     * bất kỳ không đúng loại (như dữ liệu seed thật), số ghế gõ tay ≠ sức chứa xe nào.
     */
    private Trip hotOriginalTrip(int seats, int sold) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setBus(untypedBus);
        t.setDriver(driver);
        t.setDepartureTime(LocalDateTime.now().plusDays(5).withHour(8).withMinute(0).withSecond(0).withNano(0));
        t.setArrivalTimeExpected(t.getDepartureTime().plusHours(2));
        t.setTotalSeats(seats);
        t.setTicketsSold(sold);
        t.setPrice(new BigDecimal("100000"));
        t.setStatus(TripStatus.ACTIVE);
        t.setSaleOpenedAt(LocalDateTime.now().minusDays(3));
        return tripRepository.save(t);
    }

    private Trip extraTripOf(Trip original) {
        List<Trip> extras = tripRepository.findAll().stream()
                .filter(Trip::isExtraTrip)
                .filter(t -> t.getOriginalTrip() != null && original.getId().equals(t.getOriginalTrip().getId()))
                .toList();
        assertEquals(1, extras.size(), "đúng một chuyến tăng cường cho chuyến gốc");
        return extras.get(0);
    }

    private Bus cleanBus(String plate, BusType type) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBusType(type);
        b.setOdometer(1000.0);
        b.setLastMaintenanceOdometer(1000.0);
        b.setMaintenanceThreshold(10000.0);
        b.setStatus(BusStatus.READY);
        return busRepository.save(b);
    }

    private Driver saveDriver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("CAP-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }
}
