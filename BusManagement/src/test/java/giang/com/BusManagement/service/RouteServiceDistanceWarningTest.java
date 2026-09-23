package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Group C(c) — sửa quãng đường của tuyến khi tuyến còn chuyến DEPARTED.
 *
 * Chủ dự án chốt CẢNH BÁO, không chặn: updateTripStatus() đọc route.distanceKm
 * tại lúc COMPLETED, nên số km mới sẽ vào odometer của các chuyến đang chạy; điều
 * đó đúng khi Admin sửa lỗi gõ và sai khi tuyến đổi lộ trình thật, hệ thống không
 * phân biệt được. RouteService.updateRoute() vì vậy LƯU như thường và trả về một
 * câu cảnh báo cho flash "warning".
 *
 * Test chính là ca cảnh báo; bốn đối trọng giữ cho cảnh báo không lan ra thành
 * nhiễu (không đổi km, không có chuyến đang chạy, chỉ có chuyến đã xong) và cho nó
 * không biến thành chặn (km mới vẫn được lưu).
 */
@SpringBootTest
@Transactional
class RouteServiceDistanceWarningTest {

    @Autowired private RouteService routeService;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private Route route;
    private Bus bus;
    private Driver driver;
    private List<Long> stops;

    @BeforeEach
    void setUp() {
        bus = new Bus();
        bus.setLicensePlate("ROUTE-KM-01");
        bus.setOdometer(1000.0);
        bus.setLastMaintenanceOdometer(1000.0);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.TRAVELING);
        bus = busRepository.save(bus);

        driver = saveDriver("route-km-driver");

        Station a = saveStation("Bến KM A");
        Station b = saveStation("Bến KM B");
        route = new Route();
        route.setDistanceKm(120.0);
        route.setEstimatedDuration(120);
        route = routeRepository.save(route);
        addStop(route, a, 1);
        addStop(route, b, 2);
        stops = List.of(a.getId(), b.getId());
        flushAndClear();
    }

    @Test
    void changingTheDistance_whileATripIsOnTheRoad_savesAndWarns() {
        tripOnRoute(TripStatus.DEPARTED);

        String warning = routeService.updateRoute(route.getId(), form(500.0), stops);

        assertNotNull(warning, "phải cảnh báo khi km đổi trong lúc có chuyến DEPARTED");
        assertTrue(warning.contains("1 chuyến trên đường"), warning);
        assertTrue(warning.contains("500 km"), warning);
        assertTrue(warning.contains("trước đây 120 km"), warning);

        flushAndClear();
        assertEquals(500.0, routeRepository.findById(route.getId()).orElseThrow().getDistanceKm(),
                "cảnh báo, KHÔNG chặn: km mới vẫn phải được lưu");
    }

    @Test
    void keepingTheSameDistance_doesNotWarn_evenWithATripOnTheRoad() {
        tripOnRoute(TripStatus.DEPARTED);

        assertNull(routeService.updateRoute(route.getId(), form(120.0), stops),
                "sửa thời lượng/lộ trình mà giữ nguyên km thì odometer không bị ảnh hưởng");
    }

    @Test
    void changingTheDistance_withNoTripOnTheRoad_doesNotWarn() {
        assertNull(routeService.updateRoute(route.getId(), form(500.0), stops));
    }

    @Test
    void finishedAndUpcomingTrips_doNotCount_onlyDepartedOnesAreStillToBeCredited() {
        // COMPLETED đã cộng odometer xong; ACTIVE chưa lăn bánh nên chạy theo km mới
        // là đúng — chỉ DEPARTED là chuyến "đã đi theo km cũ, sẽ được cộng theo km mới".
        tripOnRoute(TripStatus.COMPLETED);
        tripOnRoute(TripStatus.ACTIVE);

        assertNull(routeService.updateRoute(route.getId(), form(500.0), stops));
    }

    // =====================================================================

    private Route form(double km) {
        Route form = new Route();
        form.setDistanceKm(km);
        form.setEstimatedDuration(150);
        return form;
    }

    private void tripOnRoute(TripStatus status) {
        Trip t = new Trip();
        t.setRoute(routeRepository.findById(route.getId()).orElseThrow());
        t.setBus(bus);
        t.setDriver(driver);
        LocalDateTime departure = LocalDateTime.now().minusHours(1).withSecond(0).withNano(0);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(2));
        t.setTotalSeats(22);
        t.setTicketsSold(10);
        t.setPrice(new BigDecimal("100000"));
        t.setStatus(status);
        tripRepository.save(t);
        flushAndClear();
    }

    private Driver saveDriver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("RK-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }

    private Station saveStation(String name) {
        Station s = new Station();
        s.setStationName(name);
        return stationRepository.save(s);
    }

    private void addStop(Route r, Station s, int order) {
        RouteStation rs = new RouteStation();
        rs.setId(new RouteStationId(r.getId(), s.getId()));
        rs.setRoute(r);
        rs.setStation(s);
        rs.setStopOrder(order);
        routeStationRepository.save(rs);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
