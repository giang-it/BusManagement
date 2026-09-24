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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Group C(a) — loại xe của tuyến (Route.suitableBusType) là GỢI Ý, không phải luật.
 *
 * Chủ dự án chốt 2026-09-23. Hai nửa được ghim:
 * <ul>
 * <li>Dropdown Duyệt/Sửa ({@code getAvailableBusesForTrip}) MỜI cả xe khác loại —
 * trước đây nó lọc cứng, nên cùng một chuyến form Tạo cho chọn xe Ghế ngồi còn form
 * Sửa thì không — và xếp xe đúng loại lên ĐẦU dù xe đó có km-từ-bảo-trì cao hơn.</li>
 * <li>Validator không kiểm loại xe: xe khác loại đủ chỗ thì qua. Nếu một ngày có
 * người biến gợi ý thành luật ở validator, test này đỏ và buộc họ quay lại ruling.</li>
 * </ul>
 * Tuyến không quy định loại thì không có ưu tiên — thứ tự thuần theo km, như cũ.
 *
 * Nửa thứ ba (chủ dự án chốt 2026-09-24, mục #26): AI tự phân công
 * ({@code findBestAvailableBus}, qua {@code selectBestAvailableBus}) cũng coi loại xe là
 * gợi ý — chọn xe đúng loại trước, hết thì lấy loại khác thay vì trả null.
 */
@SpringBootTest
@Transactional
class TripServiceBusTypePreferenceTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private UserRepository userRepository;

    private BusType limousine;
    private Bus preferredButWorn;
    private Bus otherTypeButFresh;
    private Driver driver;

    @BeforeEach
    void setUp() {
        limousine = saveType("Pref-Limousine", 22);
        BusType seater = saveType("Pref-Seater", 50);

        // Xe đúng loại nhưng đã chạy nhiều km từ lần bảo trì — nếu chỉ xếp theo km
        // thì nó đứng SAU xe khác loại.
        preferredButWorn = bus("PREF-LIMO", limousine, 3000.0);
        otherTypeButFresh = bus("PREF-SEAT", seater, 100.0);

        driver = saveDriver("pref-driver");
    }

    @Test
    void theDropdownOffersOtherTypesToo_withThePreferredTypeFirst() {
        Trip trip = pendingTripOn(routeWithType(limousine));

        List<Long> offered = idsOf(tripService.getAvailableBusesForTrip(trip.getId()));

        assertTrue(offered.contains(otherTypeButFresh.getId()),
                "xe khác loại vẫn phải được mời — loại xe là gợi ý, không phải luật");
        assertTrue(offered.contains(preferredButWorn.getId()));
        assertTrue(offered.indexOf(preferredButWorn.getId()) < offered.indexOf(otherTypeButFresh.getId()),
                "xe đúng loại tuyến xếp trước, dù km-từ-bảo-trì cao hơn: " + offered);
    }

    @Test
    void aRouteWithoutAType_hasNoPreference_soTheOrderIsByKmAlone() {
        Trip trip = pendingTripOn(routeWithType(null));

        List<Long> offered = idsOf(tripService.getAvailableBusesForTrip(trip.getId()));

        assertTrue(offered.indexOf(otherTypeButFresh.getId()) < offered.indexOf(preferredButWorn.getId()),
                "không có loại gợi ý thì xe ít km hơn đứng trước: " + offered);
    }

    @Test
    void theValidatorDoesNotCheckTheType_aDifferentTypeWithEnoughSeatsPasses() {
        Trip trip = pendingTripOn(routeWithType(limousine));

        ValidationResult r = tripService.validateBusForTripDryRun(otherTypeButFresh, trip, trip.getId());

        assertTrue(r.isValid(), "xe khác loại, đủ chỗ, phải hợp lệ: " + r.getFailureReason());
    }

    // =====================================================================
    // AI tự phân công cũng coi loại xe là gợi ý (chủ dự án chốt 2026-09-24, mục #26)
    // =====================================================================

    @Test
    void theAiPicksThePreferredType_evenWhenAnotherTypeHasFewerKm() {
        Trip candidate = candidateOn(routeWithType(limousine));

        Bus picked = tripService.selectBestAvailableBus(candidate,
                candidate.getDepartureTime(), candidate.getArrivalTimeExpected(), null);

        assertEquals(preferredButWorn.getId(), picked.getId(),
                "còn xe đúng loại thì AI chọn nó trước, như thứ tự của dropdown");
    }

    @Test
    void theAiFallsBackToAnotherType_whenNoBusOfThePreferredTypeIsAvailable() {
        preferredButWorn.setStatus(BusStatus.REPAIRING);
        busRepository.save(preferredButWorn);
        Trip candidate = candidateOn(routeWithType(limousine));

        Bus picked = tripService.selectBestAvailableBus(candidate,
                candidate.getDepartureTime(), candidate.getArrivalTimeExpected(), null);

        assertNotNull(picked, "hết xe đúng loại thì AI lấy loại khác, không bỏ cuộc (trước đây: null)");
        assertEquals(otherTypeButFresh.getId(), picked.getId());
    }

    // =====================================================================

    private BusType saveType(String name, int capacity) {
        BusType t = new BusType();
        t.setTypeName(name);
        t.setCapacity(capacity);
        return busTypeRepository.save(t);
    }

    private Bus bus(String plate, BusType type, double kmSinceMaintenance) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBusType(type);
        b.setLastMaintenanceOdometer(1000.0);
        b.setOdometer(1000.0 + kmSinceMaintenance);
        b.setMaintenanceThreshold(10000.0);
        b.setStatus(BusStatus.READY);
        return busRepository.save(b);
    }

    private Route routeWithType(BusType type) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setSuitableBusType(type);
        return routeRepository.save(r);
    }

    private Trip pendingTripOn(Route route) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDriver(driver);
        // Ngày mai, để giờ nền mock (chỉ cộng cho hôm nay) không nhiễu.
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(8, 0);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(2));
        t.setTotalSeats(20);
        t.setTicketsSold(0);
        t.setPrice(new BigDecimal("100000"));
        t.setStatus(TripStatus.PENDING_APPROVAL);
        return tripRepository.save(t);
    }

    /** Chuyến ứng viên chưa lưu, như RecommendationService/createExtraTrip dựng trước khi chọn xe. */
    private Trip candidateOn(Route route) {
        Trip t = new Trip();
        t.setRoute(route);
        LocalDateTime departure = LocalDate.now().plusDays(1).atTime(8, 0);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(2));
        return t;
    }

    private List<Long> idsOf(List<Bus> buses) {
        return buses.stream().map(Bus::getId).toList();
    }

    private Driver saveDriver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("PREF-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }
}
