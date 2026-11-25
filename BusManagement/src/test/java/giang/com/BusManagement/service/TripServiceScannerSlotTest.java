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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT8c (QĐ-24 mục 6, P5) — job 10 giây xét theo KHUNG: tổng vé ÷ tổng ghế của mọi chuyến đang bán
 * trong khung, chuyến gốc luôn là chuyến chính (không nối chuỗi +30 phút), và khung đang có một chuyến tăng cường
 * chờ duyệt thì không tạo thêm. Các cổng 72 giờ / 48 giờ / 95% và biên 90% vẫn do
 * TripServiceScannerHotGateTest giữ.
 *
 * <p>Fixture như TripServiceScannerHotGateTest: khởi hành sau 5 ngày lúc 08:00, mở bán 3 ngày, xe 22 chỗ rảnh
 * để AI gán.
 */
@SpringBootTest
@Transactional
class TripServiceScannerSlotTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private UserRepository userRepository;

    private Route route;
    private BusType type;
    private Bus mainBus;
    private Driver driver;
    private LocalDateTime departure;

    @BeforeEach
    void setUp() {
        type = new BusType();
        type.setTypeName("Slot-22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
        mainBus = cleanBus("SLOT-MAIN");
        cleanBus("SLOT-FREE-1");
        cleanBus("SLOT-FREE-2");
        driver = saveDriver("slot-driver");
        saveDriver("slot-free-1");
        saveDriver("slot-free-2");

        route = new Route();
        route.setDistanceKm(100.0);
        route.setEstimatedDuration(120);
        route.setSuitableBusType(type);
        route = routeRepository.save(route);
        departure = LocalDateTime.now().plusDays(5).withHour(8).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * Khung nóng (37/40): một chuyến thêm 08:30, gốc là chuyến chính. Chuyến thêm đã duyệt và bán gần hết
     * (21/22): khung (37 + 21) / (40 + 22) = 93,5% vẫn nóng, nên có thêm MỘT chuyến — gốc vẫn là chuyến chính,
     * khởi hành 08:30 chứ không phải 09:00 (trước CT8c: chuyến thêm thứ hai lấy chuyến thêm làm gốc, +30 phút
     * nữa). Còn chuyến chờ duyệt đó thì quét lại không tạo thêm.
     */
    @Test
    void aStillHotSlot_getsItsNextExtraFromTheMainTrip_notAChainedOne() {
        Trip main = activeTrip(departure, 40, 37, false);

        tripService.scanAndSuggestExtraTrips();
        List<Trip> first = extrasOfSlot();
        assertEquals(1, first.size());
        Trip extra = first.get(0);
        assertEquals(main.getId(), extra.getOriginalTrip().getId());
        assertEquals(departure.plusMinutes(30), extra.getDepartureTime());

        extra.setStatus(TripStatus.ACTIVE);
        extra.setSaleOpenedAt(LocalDateTime.now().minusDays(3));
        extra.setTicketsSold(21);
        tripRepository.save(extra);

        tripService.scanAndSuggestExtraTrips();
        List<Trip> second = extrasOfSlot().stream().filter(t -> t.getStatus() == TripStatus.PENDING_APPROVAL).toList();
        assertEquals(1, second.size(), "khung vẫn > 90% tính cả chuyến thêm đã bán");
        assertEquals(main.getId(), second.get(0).getOriginalTrip().getId(), "gốc là chuyến chính, không nối chuỗi");
        assertEquals(departure.plusMinutes(30), second.get(0).getDepartureTime(), "không +30 phút chồng lên");

        tripService.scanAndSuggestExtraTrips();
        assertEquals(2, extrasOfSlot().size(), "đã có một chuyến chờ duyệt trong khung: không tạo thêm");
    }

    /**
     * Khung đã có thêm ghế (một chuyến tăng cường đã duyệt, còn trống): chuyến chính 37/40 tự nó > 90%, nhưng
     * khung 37/62 = 60% — không tạo chuyến nào (đây là ca vòng dự báo đã được duyệt).
     */
    @Test
    void theSeatsOfApprovedExtrasInTheSlot_count_soAFullMainTripAloneIsNotEnough() {
        Trip main = activeTrip(departure, 40, 37, false);
        Trip approved = activeTrip(departure, 22, 0, true);
        approved.setOriginalTrip(main);
        tripRepository.save(approved);

        tripService.scanAndSuggestExtraTrips();

        assertEquals(1, extrasOfSlot().size(), "chỉ chuyến tăng cường đã có");
    }

    /** Khung đang có một chuyến tăng cường chờ duyệt (ví dụ vòng do job đêm tạo): job 10 giây không tạo thêm. */
    @Test
    void aPendingExtraInTheSlot_fromAnyone_blocksANewOne() {
        activeTrip(departure, 40, 39, false);
        Trip pending = activeTrip(departure, 22, 0, true);
        pending.setStatus(TripStatus.PENDING_APPROVAL);
        pending.setSaleOpenedAt(null);
        tripRepository.save(pending);

        tripService.scanAndSuggestExtraTrips();

        assertEquals(1, extrasOfSlot().size());
        assertTrue(extrasOfSlot().stream().allMatch(t -> t.getId().equals(pending.getId())));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Trip activeTrip(LocalDateTime at, int seats, int sold, boolean extra) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setBus(extra ? null : mainBus);
        t.setDriver(extra ? null : driver);
        t.setDepartureTime(at);
        t.setArrivalTimeExpected(at.plusHours(2));
        t.setTotalSeats(seats);
        t.setTicketsSold(sold);
        t.setPrice(new BigDecimal("100000"));
        t.setStatus(TripStatus.ACTIVE);
        t.setSaleOpenedAt(LocalDateTime.now().minusDays(3));
        t.setExtraTrip(extra);
        return tripRepository.save(t);
    }

    /** Mọi chuyến tăng cường của khung (tuyến, ngày, giờ của chuyến chính). */
    private List<Trip> extrasOfSlot() {
        return tripRepository.findAll().stream()
                .filter(Trip::isExtraTrip)
                .filter(t -> t.getRoute() != null && route.getId().equals(t.getRoute().getId()))
                .filter(t -> t.getDepartureTime().toLocalDate().equals(departure.toLocalDate())
                        && t.getDepartureTime().getHour() == departure.getHour())
                .toList();
    }

    private Bus cleanBus(String plate) {
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
        d.setLicenseNumber("SLOT-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }
}
