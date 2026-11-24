package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.dto.MaintenancePlanDto;
import giang.com.BusManagement.dto.RegularTimetableRunDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT7d — bảo trì trong lịch (CM-25, cách M1; chủ dự án duyệt 2026-11-23). Các luật R1–R5 ở mục
 * "BẢO TRÌ TRONG LỊCH" cuối TripService.
 *
 * <p>Bối cảnh: một cặp tuyến kiểu Vũng Tàu — "Bến nhà BT" ↔ "Bến xa BT", 100 km, 2 giờ, loại xe gợi ý "BT
 * đúng loại" 22 chỗ, giá vé lịch sử ở cả ba giờ. Ngưỡng bảo trì mọi xe 10.000 km, nên mốc 90% là 9.000 km.
 * Xe đúng loại (A) dựng riêng từng test; hai xe KHÁC loại (B, C, 100 km) luôn có ở bến nhà để chạy thay. Mọi
 * xe, người ở bến nhà. @Transactional nên rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class TripServiceMaintenancePlanTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private RegularTimetableService timetableService;
    @Autowired private TripService tripService;
    @Autowired private BusService busService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private Station home;
    private Station far;
    private BusType preferred;
    private BusType other;
    private Route out;
    private Route back;
    private List<Long> configuredBefore;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà BT");
        far = station("Bến xa BT");
        preferred = busType("BT đúng loại");
        other = busType("BT khác loại");
        out = route(home, far);
        back = route(far, home);
        prices(out);
        prices(back);
        configuredBefore = target().routeIds;
        target().routeIds = List.of(out.getId(), back.getId());
    }

    @AfterEach
    void restoreConfiguration() {
        target().routeIds = configuredBefore;
    }

    private RegularTimetableService target() {
        return org.springframework.test.util.AopTestUtils.getTargetObject(timetableService);
    }

    // =====================================================================
    // M1: lịch đặt ngày bảo trì, xe nghỉ đúng ngày đó, hôm sau chạy lại (R1, R2, R5)
    // =====================================================================

    /**
     * Xe A (đúng loại) đã chạy 8.950 km — chưa vào vùng cảnh báo (9.000), nhưng một lượt đi 100 km cộng 100 km
     * quay về là 9.150. 06:00 ngày mai bộ lập lịch đặt cho A một ngày bảo trì ở bến nhà ngay ngày mai (A rảnh cả
     * ngày), nên ngày mai xe khác loại chạy thay. Ngày kia km của A tính lại từ 0: máy lấy lại A (đúng loại trước),
     * và cổng kiểm tra nhận — km thật 8.950 + 100 = 9.050 thì trước CT7d cổng đã từ chối.
     */
    @Test
    void theTimetablePlansAMaintenanceDayAtHome_andTheBusRunsAgainTheDayAfter() {
        Bus a = bus("BT-A", preferred, 8950);
        bus("BT-B", other, 100);
        bus("BT-C", other, 100);
        people(4);
        flushAndClear();

        RegularTimetableRunDto run = timetableService.generate(TOMORROW, 2);

        assertEquals(1, run.getMaintenancePlanned().size(), "chỉ xe A tới hạn theo lịch");
        MaintenancePlanDto plan = run.getMaintenancePlanned().get(0);
        assertEquals("BT-A", plan.getBusPlate());
        assertEquals(TOMORROW, plan.getDate(), "A rảnh và ở bến nhà cả ngày mai");
        assertEquals(8950, plan.getKm(), 1e-9);
        flushAndClear();
        assertEquals(TOMORROW, busRepository.findById(a.getId()).orElseThrow().getPlannedMaintenanceDate(),
                "ngày bảo trì được lưu trên xe");

        List<Trip> ofA = tripRepository.findAll().stream()
                .filter(t -> t.getBus() != null && t.getBus().getId().equals(a.getId())).toList();
        assertTrue(ofA.stream().noneMatch(t -> t.getDepartureTime().toLocalDate().equals(TOMORROW)),
                "ngày bảo trì: A không chạy chuyến nào");
        Trip firstAfter = mainTrip(out, TOMORROW.plusDays(1).atTime(6, 0));
        assertEquals(a.getId(), firstAfter.getBus().getId(), "sau ngày bảo trì: km từ 0, xe đúng loại được lấy trước");
        assertEquals(TripStatus.ACTIVE, firstAfter.getStatus(), "cổng kiểm tra tính km từ 0 sau ngày bảo trì");
        Trip tomorrow = mainTrip(out, TOMORROW.atTime(6, 0));
        assertEquals(TripStatus.ACTIVE, tomorrow.getStatus());
        assertNotEquals(a.getId(), tomorrow.getBus().getId(), "ngày mai xe khác loại chạy thay");
    }

    /**
     * Xe đang sát hạn NGAY LÚC NÀY theo km thật (9.500 ≥ 9.000) — như xe mẫu "Xe Sắp Bảo Trì" — không được lịch
     * tự đặt ngày bảo trì: Admin bảo trì rồi ghi nhận như trước. Lịch vẫn chạy bằng xe khác.
     */
    @Test
    void aBusAlreadyInTheWarningZoneNow_isNotPlannedByTheTimetable() {
        Bus nearNow = bus("BT-SAT", preferred, 9500);
        bus("BT-B", other, 100);
        people(3);
        flushAndClear();

        RegularTimetableRunDto run = timetableService.generate(TOMORROW, 1);

        assertTrue(run.getMaintenancePlanned().isEmpty());
        flushAndClear();
        assertNull(busRepository.findById(nearNow.getId()).orElseThrow().getPlannedMaintenanceDate());
        assertEquals(TripStatus.ACTIVE, mainTrip(out, TOMORROW.atTime(6, 0)).getStatus());
    }

    // =====================================================================
    // R4: chỉ rời bến nhà khi còn về được dưới mốc; R3: máy cộng km đã xếp
    // =====================================================================

    /**
     * 8.850 km: một lượt đi 100 km là 8.950 — cổng kiểm tra (người chọn) vẫn nhận. Nhưng máy không cho xe rời
     * bến nhà, vì quay về thêm 100 km là 9.050 ≥ 9.000: xe sẽ kẹt ở bến xa, không về bến nhà để bảo trì được.
     */
    @Test
    void theMachineSendsABusAwayFromHome_onlyIfItCanComeBackBelowTheMark() {
        Bus a = bus("BT-A", preferred, 8850);
        flushAndClear();

        Trip leg = candidate(out, TOMORROW.atTime(6, 0));
        AvailabilityContext ctx = context();

        assertNull(tripService.selectBestAvailableBus(leg, leg.getDepartureTime(), leg.getArrivalTimeExpected(), ctx),
                "8.850 + 100 + 100 về = 9.050 ≥ 9.000");
        leg.setTotalSeats(22);
        assertTrue(tripService.validateBusForTripDryRun(busRepository.findById(a.getId()).orElseThrow(), leg, null)
                .isValid(), "người chọn: chỉ xét chuyến đang lưu, 8.850 + 100 = 8.950");
    }

    /**
     * Màn Đề xuất (context không phải lập lịch) nay cũng cộng km đã xếp (R3): xe 8.000 km còn 4 vòng chưa xong
     * (800 km) — kể cả những vòng đã qua giờ mà chưa ai bấm hoàn thành (CM-23). Một vòng nữa là 9.000: máy không
     * lấy. Trước CT7d màn Đề xuất chỉ thấy 8.000 + 100.
     */
    @Test
    void theRecommendationContext_countsTheKmAlreadyCommitted() {
        Bus a = bus("BT-A", preferred, 8000);
        for (int d = 4; d >= 1; d--) {
            LocalDate day = LocalDate.now().minusDays(d);
            save(assigned(out, day.atTime(6, 0), a));
            save(assigned(back, day.atTime(12, 0), a));
        }
        flushAndClear();

        Trip leg = candidate(out, TOMORROW.atTime(6, 0));
        AvailabilityContext ctx = context();
        Bus reloaded = busRepository.findById(a.getId()).orElseThrow();

        assertEquals(8800, tripService.kmTowardMaintenance(reloaded, leg.getDepartureTime(), leg.getArrivalTimeExpected(),
                ctx), 1e-9);
        assertNull(tripService.selectBestAvailableBus(leg, leg.getDepartureTime(), leg.getArrivalTimeExpected(), ctx),
                "8.000 + 800 đã cam kết + 100 + 100 về = 9.000");
    }

    // =====================================================================
    // R1, R2 ở cổng kiểm tra và ô chọn; ghi nhận bảo trì; ngày đã qua
    // =====================================================================

    /**
     * Ngày bảo trì dự kiến là ngày mai. Cổng kiểm tra từ chối chuyến ngày mai, nhận chuyến ngày kia; ô chọn xe của
     * form Tạo cũng vậy. Lưu xe mà km lần bảo trì cuối không đổi thì ngày dự kiến giữ nguyên; ghi nhận bảo trì (km
     * lần bảo trì cuối = odometer) thì ngày dự kiến tự xoá.
     */
    @Test
    void aMaintenanceDayBlocksTheBusForEveryone_andRecordingTheMaintenanceClearsIt() {
        Bus a = bus("BT-A", preferred, 500);
        a.setPlannedMaintenanceDate(TOMORROW);
        flushAndClear();
        Bus bus = busRepository.findById(a.getId()).orElseThrow();

        Trip onTheDay = candidate(out, TOMORROW.atTime(6, 0));
        onTheDay.setTotalSeats(22);
        ValidationResult refused = tripService.validateBusForTripDryRun(bus, onTheDay, null);
        assertFalse(refused.isValid());
        assertTrue(refused.getFailureReason().contains("bảo trì dự kiến"), refused.getFailureReason());
        Trip dayAfter = candidate(out, TOMORROW.plusDays(1).atTime(6, 0));
        dayAfter.setTotalSeats(22);
        assertTrue(tripService.validateBusForTripDryRun(bus, dayAfter, null).isValid());
        assertTrue(tripService.getAvailableBusesForTimeRange(onTheDay.getDepartureTime(),
                onTheDay.getArrivalTimeExpected()).stream().noneMatch(b -> b.getId().equals(a.getId())));
        assertTrue(tripService.getAvailableBusesForTimeRange(dayAfter.getDepartureTime(),
                dayAfter.getArrivalTimeExpected()).stream().anyMatch(b -> b.getId().equals(a.getId())));

        busService.updateBus(a.getId(), form(bus, bus.getLastMaintenanceOdometer()));
        flushAndClear();
        assertEquals(TOMORROW, busRepository.findById(a.getId()).orElseThrow().getPlannedMaintenanceDate(),
                "km lần bảo trì cuối không đổi: chưa bảo trì");

        busService.updateBus(a.getId(), form(bus, bus.getOdometer()));
        flushAndClear();
        Bus serviced = busRepository.findById(a.getId()).orElseThrow();
        assertNull(serviced.getPlannedMaintenanceDate(), "đã ghi nhận bảo trì: ngày dự kiến tự xoá");
        assertEquals(0.0, serviced.getKmSinceLastMaintenance(), 1e-9);
    }

    /**
     * Ngày bảo trì dự kiến là hôm qua mà Admin chưa ghi nhận: số liệu nói xe chưa được bảo trì, nên ngày đó hết
     * tác dụng — 8.950 + 100 vẫn bị cổng từ chối. Trang Lịch thường lệ hiện ngày đó, gắn "đã qua".
     */
    @Test
    void aMaintenanceDayInThePast_withoutRecordedMaintenance_hasNoEffect_andThePageSaysSo() {
        Bus a = bus("BT-A", preferred, 8950);
        a.setPlannedMaintenanceDate(LocalDate.now().minusDays(1));
        flushAndClear();
        Bus bus = busRepository.findById(a.getId()).orElseThrow();

        assertFalse(bus.hasPlannedMaintenance(LocalDate.now()));
        Trip leg = candidate(out, TOMORROW.atTime(6, 0));
        leg.setTotalSeats(22);
        assertFalse(tripService.validateBusForTripDryRun(bus, leg, null).isValid(), "8.950 + 100 ≥ 9.000");
        MaintenancePlanDto row = timetableService.overview().getMaintenance().stream()
                .filter(m -> m.getBusId().equals(a.getId())).findFirst().orElseThrow();
        assertTrue(row.isOverdue());
    }

    /**
     * R5: máy xếp xe cùng loại theo km TÍNH CẢ LỊCH. Xe A có km thật 8.000 nhưng đã có ngày bảo trì ngày mai; xe
     * D có km thật 1.000. Cho chuyến ngày kia, A tính từ 0 nên A được lấy trước — ô chọn của người vẫn xếp theo km
     * thật (D trước).
     */
    @Test
    void afterItsMaintenanceDay_theMachinePrefersTheFreshlyServicedBus() {
        Bus a = bus("BT-A", preferred, 8000);
        a.setPlannedMaintenanceDate(TOMORROW);
        Bus d = bus("BT-D", preferred, 1000);
        flushAndClear();

        Trip leg = candidate(out, TOMORROW.plusDays(1).atTime(6, 0));
        Bus chosen = tripService.selectBestAvailableBus(leg, leg.getDepartureTime(), leg.getArrivalTimeExpected(),
                context());

        assertEquals(a.getId(), chosen.getId(), "0 km sau ngày bảo trì < 1.000 km");
        List<Bus> offered = tripService.getAvailableBusesForTimeRange(leg.getDepartureTime(), leg.getArrivalTimeExpected());
        assertEquals(d.getId(), offered.stream().filter(b -> List.of(a.getId(), d.getId()).contains(b.getId()))
                .findFirst().orElseThrow().getId(), "ô chọn của người: km thật");
    }

    /** Chỉ Lịch thường lệ đặt ngày bảo trì: một POST tạo xe tự chế mang ngày đó vẫn lưu xe không có ngày dự kiến. */
    @Test
    void creatingABus_neverTakesAMaintenanceDayFromTheForm() {
        Bus crafted = new Bus();
        crafted.setLicensePlate("BT-NEW");
        crafted.setBrand("TestBrand");
        crafted.setBusType(preferred);
        crafted.setStatus(BusStatus.READY);
        crafted.setHomeStation(home);
        crafted.setPlannedMaintenanceDate(TOMORROW);

        busService.saveBus(crafted);
        flushAndClear();

        Bus saved = busRepository.findAll().stream().filter(b -> "BT-NEW".equals(b.getLicensePlate()))
                .findFirst().orElseThrow();
        assertNull(saved.getPlannedMaintenanceDate());
    }

    // =====================================================================
    // R3: đường context và đường DB đếm cùng một số km
    // =====================================================================

    /**
     * Km của máy hỏi qua AvailabilityContext (màn Đề xuất, lịch) và hỏi thẳng DB (job 10 giây) phải bằng nhau: chỉ
     * chuyến chưa xong (chờ duyệt, mở bán, đang chạy) — không chuyến đã xong hay đã hủy — và tách đúng trước / sau
     * ngày bảo trì dự kiến.
     */
    @Test
    void committedKm_fromTheContext_equalsTheDatabaseSum_beforeAndAfterAPlannedDay() {
        Bus a = bus("BT-A", preferred, 1000);
        LocalDate d0 = LocalDate.now().minusDays(2);
        save(assigned(out, d0.atTime(6, 0), a, TripStatus.ACTIVE));
        save(assigned(back, d0.atTime(12, 0), a, TripStatus.COMPLETED));
        save(assigned(out, d0.atTime(18, 0), a, TripStatus.CANCELLED));
        save(assigned(out, TOMORROW.atTime(6, 0), a, TripStatus.PENDING_APPROVAL));
        save(assigned(back, TOMORROW.atTime(12, 0), a, TripStatus.ACTIVE));
        save(assigned(out, TOMORROW.plusDays(3).atTime(6, 0), a, TripStatus.ACTIVE));
        save(assigned(back, TOMORROW.plusDays(3).atTime(12, 0), a, TripStatus.DEPARTED));
        flushAndClear();
        Bus bus = busRepository.findById(a.getId()).orElseThrow();
        AvailabilityContext ctx = context();
        LocalDateTime before = TOMORROW.plusDays(1).atTime(6, 0);
        LocalDateTime after = TOMORROW.plusDays(3).atTime(6, 0);

        assertEquals(1000 + 500, tripService.kmTowardMaintenance(bus, before, before, ctx), 1e-9, "5 chuyến chưa xong");
        assertEquals(tripService.kmTowardMaintenance(bus, before, before, ctx),
                tripService.kmTowardMaintenance(bus, before, before, null), 1e-9);

        bus.setPlannedMaintenanceDate(TOMORROW.plusDays(2));
        assertEquals(1000 + 300, tripService.kmTowardMaintenance(bus, before, before, ctx), 1e-9,
                "trước ngày bảo trì: km thật + 3 chuyến trước ngày đó");
        assertEquals(tripService.kmTowardMaintenance(bus, before, before, ctx),
                tripService.kmTowardMaintenance(bus, before, before, null), 1e-9);
        assertEquals(200, tripService.kmTowardMaintenance(bus, after, after, ctx), 1e-9,
                "sau ngày bảo trì: từ 0 + 2 chuyến sau ngày đó");
        assertEquals(tripService.kmTowardMaintenance(bus, after, after, ctx),
                tripService.kmTowardMaintenance(bus, after, after, null), 1e-9);
    }

    // =====================================================================

    private AvailabilityContext context() {
        return tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(), TOMORROW.plusDays(5).atStartOfDay());
    }

    private Trip mainTrip(Route route, LocalDateTime departure) {
        return tripRepository.findAll().stream()
                .filter(t -> !t.isExtraTrip() && t.getRoute() != null && t.getRoute().getId().equals(route.getId())
                        && t.getDepartureTime().equals(departure))
                .findFirst().orElseThrow(() -> new AssertionError("không có chuyến " + route.getId() + " lúc " + departure));
    }

    /** Chuyến tạm (không lưu) trên tuyến, tuyến nạp lại kèm bến như ô chọn của form Tạo. */
    private Trip candidate(Route route, LocalDateTime departure) {
        Trip t = new Trip();
        t.setRoute(tripService.routeForLocation(route.getId()));
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(120));
        return t;
    }

    private Trip assigned(Route route, LocalDateTime departure, Bus bus) {
        return assigned(route, departure, bus, TripStatus.ACTIVE);
    }

    private Trip assigned(Route route, LocalDateTime departure, Bus bus, TripStatus status) {
        Trip t = bare(route, departure, status);
        t.setBus(bus);
        return t;
    }

    /** Bản form của trang sửa xe: giữ mọi ô như cũ, chỉ đổi km lần bảo trì cuối. */
    private static Bus form(Bus bus, Double lastMaintenanceOdometer) {
        Bus f = new Bus();
        f.setLicensePlate(bus.getLicensePlate());
        f.setBrand(bus.getBrand());
        f.setBusType(bus.getBusType());
        f.setStatus(bus.getStatus());
        f.setOdometer(bus.getOdometer());
        f.setLastMaintenanceOdometer(lastMaintenanceOdometer);
        f.setMaintenanceThreshold(bus.getMaintenanceThreshold());
        f.setHomeStation(bus.getHomeStation());
        return f;
    }

    /** Giá vé lịch sử: một chuyến COMPLETED 10 ngày trước ở mỗi giờ, không xe, không người. */
    private void prices(Route route) {
        for (int hour : List.of(6, 12, 18)) {
            save(bare(route, LocalDate.now().minusDays(10).atTime(hour, 0), TripStatus.COMPLETED));
        }
    }

    private Trip bare(Route route, LocalDateTime departure, TripStatus status) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(120));
        t.setStatus(status);
        t.setTotalSeats(22);
        t.setPrice(new BigDecimal("100000"));
        return t;
    }

    private void save(Trip trip) {
        tripRepository.save(trip);
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name + " " + System.nanoTime());
        return stationRepository.save(s);
    }

    private BusType busType(String name) {
        BusType t = new BusType();
        t.setTypeName(name + " " + System.nanoTime());
        t.setCapacity(22);
        return busTypeRepository.save(t);
    }

    private Route route(Station from, Station to) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setSuitableBusType(preferred);
        Route saved = routeRepository.save(r);
        stop(saved, from, 1);
        stop(saved, to, 2);
        return saved;
    }

    private void stop(Route route, Station station, int order) {
        RouteStation rs = new RouteStation();
        rs.setId(new RouteStationId(route.getId(), station.getId()));
        rs.setRoute(route);
        rs.setStation(station);
        rs.setStopOrder(order);
        routeStationRepository.save(rs);
    }

    private Bus bus(String plate, BusType type, double kmSinceMaintenance) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand("TestBrand");
        b.setBusType(type);
        b.setLastMaintenanceOdometer(1000.0);
        b.setOdometer(1000.0 + kmSinceMaintenance);
        b.setMaintenanceThreshold(10000.0);
        b.setStatus(BusStatus.READY);
        b.setHomeStation(home);
        return busRepository.save(b);
    }

    /** n người, đều ở bến nhà, chưa chạy chuyến nào. */
    private void people(int n) {
        for (int i = 1; i <= n; i++) {
            User user = new User();
            user.setUsername("bt-driver-" + i);
            user.setFullName("Tài xế BT " + i);
            user.setPassword("x");
            user.setRole(Role.ROLE_DRIVER);
            Driver d = new Driver();
            d.setUser(user);
            d.setLicenseNumber("BT-" + i);
            d.setExperienceYears(5);
            d.setTotalDrivingHours24h(0.0);
            d.setIsActive(true);
            d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
            d.setHomeStation(home);
            user.setDriver(d);
            userRepository.save(user);
        }
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
