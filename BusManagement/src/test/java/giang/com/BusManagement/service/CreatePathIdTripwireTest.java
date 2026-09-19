package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import giang.com.BusManagement.domain.IncidentType;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.IncidentRepository;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lỗi #21 — mọi đường "tạo mới" phải TỪ CHỐI một entity đã có id, thay vì để
 * repository.save() biến thành MERGE và ghi đè bản ghi có sẵn.
 *
 * Vì sao cần test cho từng cửa một: lỗ hổng không nằm ở một chỗ mà ở KHUÔN — sáu
 * service create đều kết thúc ở save(), và chỉ BusService.saveBus() (lỗi #16) có
 * tripwire. Test này chốt tripwire trên năm cửa còn lại (Trip, Incident, Driver,
 * User, Route, Station) và, với hai cửa vừa được tách khỏi upsert (Route, Station),
 * chốt luôn rằng đường CẬP NHẬT mới chép field vào bản ghi cũ chứ không tạo dòng
 * mới. Mỗi test khẳng định hai điều: ném IllegalArgumentException, VÀ bản ghi nạn
 * nhân không đổi.
 *
 * Non-vacuous: bỏ một tripwire là test tương ứng đỏ (đã probe khi viết).
 *
 * Chạy trên busmanagement_test (Phase 0), @Transactional nên mọi bản ghi được
 * rollback sau từng test.
 */
@SpringBootTest
@Transactional
class CreatePathIdTripwireTest {

    @Autowired private TripService tripService;
    @Autowired private IncidentService incidentService;
    @Autowired private DriverService driverService;
    @Autowired private AdminService adminService;
    @Autowired private RouteService routeService;
    @Autowired private StationService stationService;

    @Autowired private TripRepository tripRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private EntityManager em;

    private Bus bus;
    private Driver driver;
    private Station stationA;
    private Station stationB;
    private Station stationC;
    private Route route;

    @BeforeEach
    void setUp() {
        BusType type = new BusType();
        type.setTypeName("Tripwire-22");
        type.setCapacity(22);
        type = busTypeRepository.save(type);

        bus = new Bus();
        bus.setLicensePlate("TRIPWIRE-01");
        bus.setBusType(type);
        bus.setOdometer(1000.0);
        bus.setLastMaintenanceOdometer(1000.0);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.READY);
        bus = busRepository.save(bus);

        driver = saveDriver("tripwire-driver");

        stationA = saveStation("Bến A");
        stationB = saveStation("Bến B");
        stationC = saveStation("Bến C");

        route = new Route();
        route.setDistanceKm(120.0);
        route.setEstimatedDuration(120);
        route.setSuitableBusType(type);
        route = routeRepository.save(route);
        addStop(route, stationA, 1);
        addStop(route, stationB, 2);
        flushAndClear();
    }

    // =====================================================================
    // Sáu tripwire — mỗi cửa một test
    // =====================================================================

    @Test
    void createManualTrip_refusesAnEntityThatAlreadyHasAnId_andLeavesTheRowUntouched() {
        Trip completed = new Trip();
        completed.setRoute(route);
        completed.setBus(bus);
        completed.setDriver(driver);
        completed.setDepartureTime(LocalDateTime.now().minusDays(3).withHour(8).withMinute(0).withSecond(0).withNano(0));
        completed.setArrivalTimeExpected(LocalDateTime.now().minusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0));
        completed.setTotalSeats(22);
        completed.setTicketsSold(20);
        completed.setPrice(new BigDecimal("100000.00"));
        completed.setStatus(TripStatus.COMPLETED);
        Long victimId = tripRepository.save(completed).getId();
        flushAndClear();
        long before = tripRepository.count();

        // Đúng hình dạng mà AdminTripManagementController.createTrip() dựng từ một
        // POST mang id=<victim>: entity mới, id có sẵn, status đã bị set ACTIVE.
        Trip posted = new Trip();
        posted.setId(victimId);
        posted.setRoute(routeRepository.findById(route.getId()).orElseThrow());
        posted.setBus(busRepository.findById(bus.getId()).orElseThrow());
        posted.setDriver(userRepository.findById(driver.getUserId()).orElseThrow().getDriver());
        posted.setDepartureTime(LocalDate.now().plusDays(1).atTime(8, 0));
        posted.setArrivalTimeExpected(LocalDate.now().plusDays(1).atTime(10, 0));
        posted.setTotalSeats(22);
        posted.setPrice(new BigDecimal("1.00"));
        posted.setStatus(TripStatus.ACTIVE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tripService.createManualTrip(posted));
        assertTrue(ex.getMessage().contains("createManualTrip() chỉ dùng để tạo chuyến mới"), ex.getMessage());

        flushAndClear();
        Trip after = tripRepository.findById(victimId).orElseThrow();
        assertEquals(TripStatus.COMPLETED, after.getStatus(), "chuyến COMPLETED không được hồi sinh");
        assertEquals(20, after.getTicketsSold(), "vé đã bán phải còn nguyên");
        assertEquals(0, new BigDecimal("100000.00").compareTo(after.getPrice()), "giá phải còn nguyên");
        assertEquals(before, tripRepository.count());
    }

    @Test
    void createIncident_refusesAnEntityThatAlreadyHasAnId_andLeavesTheRowUntouched() {
        Incident existing = new Incident();
        existing.setBus(bus);
        existing.setDriver(driver);
        existing.setIncidentType(IncidentType.VEHICLE_BREAKDOWN);
        existing.setStatus(IncidentStatus.OPEN);
        existing.setDescription("gốc");
        Long victimId = incidentRepository.save(existing).getId();
        flushAndClear();

        Incident posted = new Incident();
        posted.setId(victimId);
        posted.setBus(busRepository.findById(bus.getId()).orElseThrow());
        posted.setIncidentType(IncidentType.OTHER);
        posted.setStatus(IncidentStatus.RESOLVED);
        posted.setDescription("HIJACKED");

        assertThrows(IllegalArgumentException.class, () -> incidentService.createIncident(posted));

        flushAndClear();
        Incident after = incidentRepository.findById(victimId).orElseThrow();
        assertEquals(IncidentType.VEHICLE_BREAKDOWN, after.getIncidentType());
        assertEquals(IncidentStatus.OPEN, after.getStatus());
        assertEquals("gốc", after.getDescription());
        assertNull(after.getResolvedAt(), "resolvedAt không được đóng dấu");
        assertEquals(driver.getUserId(), after.getDriver().getUserId(), "liên kết tài xế phải còn nguyên");
    }

    @Test
    void createDriver_refusesAUserThatAlreadyHasAnId_andLeavesTheAccountUntouched() {
        User admin = new User();
        admin.setUsername("tripwire-admin");
        admin.setFullName("Admin");
        admin.setPassword("secret");
        admin.setRole(Role.ROLE_ADMIN);
        Long adminId = userRepository.save(admin).getId();
        flushAndClear();
        long usersBefore = userRepository.count();

        User postedUser = new User();
        postedUser.setId(adminId);
        postedUser.setUsername("hijacked-admin");
        postedUser.setFullName("Hijacked");
        postedUser.setPassword("pw");
        Driver postedDriver = new Driver();
        postedDriver.setLicenseNumber("HJ-1");
        postedDriver.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        postedDriver.setIsActive(true);

        assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(postedUser, postedDriver));

        flushAndClear();
        User after = userRepository.findById(adminId).orElseThrow();
        assertEquals("tripwire-admin", after.getUsername());
        assertEquals(Role.ROLE_ADMIN, after.getRole(), "admin không được bị hạ thành tài xế");
        assertEquals("secret", after.getPassword());
        assertNull(after.getDriver(), "không được gắn thêm driver row cho admin");
        assertEquals(usersBefore, userRepository.count());
    }

    @Test
    void createDriver_refusesADriverProfileThatAlreadyHasAUserId() {
        User postedUser = new User();
        postedUser.setUsername("brand-new");
        postedUser.setPassword("pw");
        Driver postedDriver = new Driver();
        postedDriver.setUserId(driver.getUserId()); // khoá của một tài xế có sẵn
        postedDriver.setLicenseNumber("HJ-2");

        assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(postedUser, postedDriver));
        assertTrue(userRepository.findByUsername("brand-new").isEmpty(), "không được tạo user nào");
    }

    @Test
    void createNewUser_refusesAnEntityThatAlreadyHasAnId_andLeavesTheAccountUntouched() {
        User existing = userRepository.findById(driver.getUserId()).orElseThrow();
        String username = existing.getUsername();
        flushAndClear();

        User posted = new User();
        posted.setId(driver.getUserId());
        posted.setUsername("hijacked-user");
        posted.setPassword("newpw");
        posted.setRole(Role.ROLE_ADMIN);

        assertThrows(IllegalArgumentException.class, () -> adminService.createNewUser(posted));

        flushAndClear();
        User after = userRepository.findById(driver.getUserId()).orElseThrow();
        assertEquals(username, after.getUsername());
        assertEquals(Role.ROLE_DRIVER, after.getRole(), "không được tự phong admin");
    }

    @Test
    void createRoute_refusesAnEntityThatAlreadyHasAnId_andLeavesRouteAndStopsUntouched() {
        long routesBefore = routeRepository.count();

        Route posted = new Route();
        posted.setId(route.getId());
        posted.setDistanceKm(999.0);
        posted.setEstimatedDuration(999);

        assertThrows(IllegalArgumentException.class,
                () -> routeService.createRoute(posted, List.of(stationC.getId(), stationA.getId())));

        flushAndClear();
        Route after = routeRepository.findByIdWithStations(route.getId()).orElseThrow();
        assertEquals(120.0, after.getDistanceKm());
        assertEquals(120, after.getEstimatedDuration());
        assertEquals("Tripwire-22", after.getSuitableBusType().getTypeName(), "loại xe không được bị xoá");
        assertEquals(List.of(stationA.getId(), stationB.getId()), stopIdsOf(after), "lộ trình phải còn nguyên");
        assertEquals(routesBefore, routeRepository.count());
    }

    @Test
    void createStation_refusesAnEntityThatAlreadyHasAnId_andLeavesTheRowUntouched() {
        long before = stationRepository.count();

        Station posted = new Station();
        posted.setId(stationA.getId());
        posted.setStationName("HIJACKED");

        assertThrows(IllegalArgumentException.class, () -> stationService.createStation(posted));

        flushAndClear();
        assertEquals("Bến A", stationRepository.findById(stationA.getId()).orElseThrow().getStationName());
        assertEquals(before, stationRepository.count());
    }

    // =====================================================================
    // Hai đường CẬP NHẬT vừa tách khỏi upsert — chép field, không tạo dòng mới
    // =====================================================================

    @Test
    void updateRoute_copiesFieldsOntoTheExistingRow_andRebuildsStops() {
        long before = routeRepository.count();

        Route form = new Route();
        form.setDistanceKm(150.0);
        form.setEstimatedDuration(180);
        form.setSuitableBusType(null); // "-- Không giới hạn --" là ý định hợp lệ

        routeService.updateRoute(route.getId(), form, List.of(stationB.getId(), stationC.getId(), stationA.getId()));

        flushAndClear();
        Route after = routeRepository.findByIdWithStations(route.getId()).orElseThrow();
        assertEquals(150.0, after.getDistanceKm());
        assertEquals(180, after.getEstimatedDuration());
        assertNull(after.getSuitableBusType());
        assertEquals(List.of(stationB.getId(), stationC.getId(), stationA.getId()), stopIdsOf(after),
                "lộ trình dựng lại đúng thứ tự gửi lên, stopOrder 1..n");
        assertEquals(before, routeRepository.count(), "cập nhật không được đẻ tuyến mới");
    }

    @Test
    void updateStation_copiesFieldsOntoTheExistingRow() {
        long before = stationRepository.count();

        Station form = new Station();
        form.setStationName("Bến A (đổi tên)");
        form.setAddress("Địa chỉ mới");

        stationService.updateStation(stationA.getId(), form);

        flushAndClear();
        Station after = stationRepository.findById(stationA.getId()).orElseThrow();
        assertEquals("Bến A (đổi tên)", after.getStationName());
        assertEquals("Địa chỉ mới", after.getAddress());
        assertEquals(before, stationRepository.count());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Driver saveDriver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("TW-" + username);
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
        rs.setId(new giang.com.BusManagement.domain.RouteStationId(r.getId(), s.getId()));
        rs.setRoute(r);
        rs.setStation(s);
        rs.setStopOrder(order);
        routeStationRepository.save(rs);
    }

    private List<Long> stopIdsOf(Route r) {
        return r.getOrderedRouteStations().stream()
                .map(rs -> rs.getStation().getId())
                .collect(Collectors.toList());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
