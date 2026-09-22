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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cổng "chuyến đông" của scanner AI ({@code scanAndSuggestExtraTrips()}) phải là
 * {@code Trip.needsReinforcement()} — cùng một biên, cùng một chiều so sánh.
 *
 * Vì sao cần test này: {@code isHotTrip()} từng so literal {@code <= 0.9} trong khi
 * đang cầm một {@code Trip}. Hôm nay hai cách viết cho cùng kết quả; nhưng nếu ngưỡng
 * ở {@code Trip} đổi, ba màn Decision Support đổi theo (chúng khai lại con số kèm
 * comment nhắc), còn scanner — thứ duy nhất TẠO chuyến thật — sẽ âm thầm giữ 0,9.
 * Hai test dưới ghim biên qua đường public: đúng 90% thì KHÔNG đông (so sánh là
 * {@code >}, không phải {@code >=}), nhỉnh hơn là đông.
 *
 * Fixture qua đủ ba cổng thời gian của {@code isHotTrip()} (khởi hành sau 5 ngày
 * > 72h; mở bán đã 3 ngày > 48h), để kết quả chỉ còn phụ thuộc vào tỉ lệ lấp đầy.
 *
 * Non-vacuous: đổi {@code >} thành {@code >=} trong {@code Trip.needsReinforcement()}
 * → test biên 90% đỏ (đã probe khi viết).
 */
@SpringBootTest
@Transactional
class TripServiceScannerHotGateTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private UserRepository userRepository;

    private Route route;
    private Bus originalBus;
    private Driver driver;

    @BeforeEach
    void setUp() {
        BusType type = new BusType();
        type.setTypeName("Gate-22");
        type.setCapacity(22);
        type = busTypeRepository.save(type);

        originalBus = cleanBus("GATE-ORIG", type);
        cleanBus("GATE-FREE", type); // để AI có xe mà gán cho chuyến tăng cường
        driver = saveDriver("gate-driver");
        saveDriver("gate-free-driver");

        route = new Route();
        route.setDistanceKm(100.0);
        route.setEstimatedDuration(120);
        route.setSuitableBusType(type);
        route = routeRepository.save(route);
    }

    @Test
    void exactlyNinetyPercent_isNotHot_soNoExtraTripIsSuggested() {
        Trip atBoundary = activeTrip(40, 36); // 36/40 = 0,90 — đúng biên

        assertFalse(atBoundary.needsReinforcement(), "tiền đề: Trip nói 0,90 chưa phải đông");

        tripService.scanAndSuggestExtraTrips();

        assertEquals(0, extraTripsOf(atBoundary).size(),
                "scanner phải trả lời y như Trip: đúng 90% thì không tăng cường");
    }

    @Test
    void justAboveNinetyPercent_isHot_soOneExtraTripIsSuggested() {
        Trip aboveBoundary = activeTrip(40, 37); // 37/40 = 0,925 — vượt biên, dưới ngưỡng instant-hot 0,95

        assertTrue(aboveBoundary.needsReinforcement(), "tiền đề: Trip nói 0,925 là đông");

        tripService.scanAndSuggestExtraTrips();

        List<Trip> extras = extraTripsOf(aboveBoundary);
        assertEquals(1, extras.size(), "vượt 90% thì scanner đề xuất đúng một chuyến tăng cường");
        assertEquals(TripStatus.PENDING_APPROVAL, extras.get(0).getStatus());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Chuyến ACTIVE đã mở bán 3 ngày, khởi hành sau 5 ngày — qua mọi cổng thời gian. */
    private Trip activeTrip(int seats, int sold) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setBus(originalBus);
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

    private List<Trip> extraTripsOf(Trip original) {
        return tripRepository.findAll().stream()
                .filter(Trip::isExtraTrip)
                .filter(t -> t.getOriginalTrip() != null && original.getId().equals(t.getOriginalTrip().getId()))
                .toList();
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
        d.setLicenseNumber("GATE-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }
}
