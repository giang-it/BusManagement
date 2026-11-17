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
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT5b (QĐ-23, CM-5 cách B): vị trí xe và người được bật vào việc chọn tài nguyên.
 * <ul>
 * <li>MÁY chọn (findBestAvailableBus / findBestAvailableDriver — job 10 giây, màn Đề xuất) là
 * CỨNG: chỉ xét xe và người đang đứng ở bến xuất phát lúc khởi hành;</li>
 * <li>NGƯỜI chọn (ô chọn ở màn Duyệt, Sửa, Tạo) là MỀM: vẫn mời mọi lựa chọn hợp lệ, cùng bến
 * xếp đầu, ở bến khác có ghi chú; validator không chặn theo vị trí;</li>
 * <li>đường DB và đường AvailabilityContext cho cùng câu trả lời.</li>
 * </ul>
 *
 * Bối cảnh, dựng để không test nào đậu suông: xe ĐANG Ở bến nhà nhưng chạy nhiều km nhất; xe
 * vừa tới bến xa ít km hơn; xe "không biết ở đâu" (không bến nhà, chưa chạy chuyến) ít km nhất.
 * Không có luật vị trí thì máy luôn chọn xe ít km nhất — nên mỗi test ra đúng xe ở bến xuất phát
 * là luật vị trí đang làm việc. Giờ đi đặt từ ngày mai, để giờ nền mock (chỉ cộng cho hôm nay)
 * không nhiễu phép tính giờ lái.
 */
@SpringBootTest
@Transactional
class TripServiceLocationTest {

    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private static final LocalDateTime TOMORROW_8 = LocalDate.now().plusDays(1).atTime(8, 0);

    private Station home;
    private Station far;
    private Route out;
    private Route back;
    private Bus busHome;
    private Bus busAway;
    private Bus busNowhere;
    private Driver dHome;
    private Driver dAway;
    private Driver dNowhere;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà LOC");
        far = station("Bến xa LOC");
        BusType type = busType();
        out = route(home, far);
        back = route(far, home);

        busHome = bus("LOC-HOME", type, 3000.0, home);
        busAway = bus("LOC-AWAY", type, 2000.0, home);
        busNowhere = bus("LOC-NOWHERE", type, 1000.0, null);
        dHome = driver("loc-home", home);
        dAway = driver("loc-away", home);
        dNowhere = driver("loc-nowhere", null);

        // Ba ngày trước, xe busAway cùng tài xế dAway đã chạy bến nhà → bến xa.
        LocalDateTime past = LocalDate.now().minusDays(3).atTime(8, 0);
        save(trip(out, busAway, dAway, past, TripStatus.COMPLETED));
        flushAndClear();
        reload();
    }

    // =====================================================================
    // Máy chọn: CỨNG
    // =====================================================================

    @Test
    void theMachinePicksOnlyABusStandingAtTheStart_notTheLeastWornOne() {
        assertEquals(busHome.getId(), pickBus(out, TOMORROW_8, null).getId(),
                "đi từ bến nhà: chỉ xe đang ở bến nhà, dù nó nhiều km nhất");
        assertEquals(busAway.getId(), pickBus(back, TOMORROW_8, null).getId(),
                "đi từ bến xa: xe vừa tới bến xa");
    }

    @Test
    void aBusWhoseLocationIsUnknown_isNeverPicked_andNoBusAtTheStartMeansNoPick() {
        busHome.setStatus(BusStatus.REPAIRING);
        busRepository.save(busHome);
        flushAndClear();

        assertNull(pickBus(out, TOMORROW_8, null),
                "xe ở bến xa và xe không rõ ở đâu đều không được chọn cho chuyến đi từ bến nhà");
    }

    @Test
    void theMachinePicksOnlyAPersonStandingAtTheStart_inEveryRole() {
        LocalDateTime arr = TOMORROW_8.plusHours(6);
        assertEquals(dHome.getUserId(), tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(),
                false, null, out).getUserId());
        assertEquals(dAway.getUserId(), tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(),
                false, null, back).getUserId());
        assertEquals(dAway.getUserId(), tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(),
                true, null, back).getUserId(), "phụ xe cũng theo cùng một luật");
        assertNull(tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(dHome),
                false, null, out), "người không rõ ở đâu không được chọn");
    }

    @Test
    void aRouteWithoutStations_isNotFilteredByLocation() {
        Route noStations = new Route();
        noStations.setDistanceKm(300.0);
        noStations.setEstimatedDuration(360);
        noStations = routeRepository.save(noStations);

        assertEquals(busNowhere.getId(), pickBus(noStations, TOMORROW_8, null).getId(),
                "không biết bến xuất phát thì không lọc theo vị trí: lại chọn xe ít km nhất như trước CT5b");
    }

    /** Chuyến chờ duyệt tương lai cũng là một cam kết: sau nó, xe đứng ở bến cuối của nó. */
    @Test
    void aFuturePendingTripMovesTheBusToo() {
        save(trip(out, busHome, dHome, TOMORROW_8, TripStatus.PENDING_APPROVAL));
        flushAndClear();
        reload();
        LocalDateTime dayAfter = TOMORROW_8.plusDays(1);

        assertEquals(far.getId(), tripService.busStationAt(busHome, dayAfter, null));
        assertEquals(home.getId(), tripService.busStationAt(busHome, TOMORROW_8, null),
                "lúc khởi hành chuyến đó, xe vẫn ở bến nhà (chuyến chưa tới nơi)");
        assertNull(pickBus(out, dayAfter, null), "ngày kia không còn xe nào ở bến nhà");
    }

    /**
     * PHÍA SAU của luật (LocationRule.fits): xe ở bến nhà đã có chuyến chờ duyệt ngày kia xuất phát
     * TỪ BẾN NHÀ. Nhận thêm chuyến bến nhà → bến xa ngày mai thì xe sẽ kẹt ở bến xa, chuyến ngày kia
     * không chạy được — nên máy không chọn nó, và ô chọn ghi rõ lý do.
     */
    @Test
    void aBusIsNotPicked_whenItsNextCommittedTripWouldStartFromTheWrongStation() {
        save(trip(out, busHome, dHome, TOMORROW_8.plusDays(1), TripStatus.PENDING_APPROVAL));
        flushAndClear();
        reload();

        assertNull(pickBus(out, TOMORROW_8, null),
                "busHome đứng đúng bến nhà, nhưng chuyến kế tiếp của nó lại đi từ bến nhà");
        assertEquals("chuyến kế tiếp đi từ Bến nhà LOC",
                tripService.busLocationNote(busHome, out, TOMORROW_8, TOMORROW_8.plusMinutes(360)));
        assertNull(tripService.selectBestAvailableDriver(TOMORROW_8, TOMORROW_8.plusMinutes(360), 6, 1,
                List.of(), false, null, out), "dHome cũng vậy; dAway ở bến xa, dNowhere không rõ ở đâu");
    }

    /**
     * Lỗi thật thấy khi verify màn Đề xuất trên DB thật (2026-11-17): thẻ xếp theo LÃI, không theo
     * giờ, nên thẻ bến xa 18:00 được giữ chỗ trước thẻ bến xa 06:00 cùng ngày, và chỉ xét phía trước
     * thì cùng một xe được giao cả hai — về tới bến nhà lúc 12:00 rồi lại "xuất phát" ở bến xa lúc 18:00.
     */
    @Test
    void twoCardsOutOfTimeOrder_cannotSendOneBusTwiceFromTheFarEnd() {
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(6).atStartOfDay());
        LocalDateTime evening = TOMORROW_8.withHour(18);
        Trip lateCard = candidate(back, evening);
        assertEquals(busAway.getId(), idOf(pickBus(back, evening, ctx)));
        lateCard.setBus(busAway);
        lateCard.setDriver(dAway);
        lateCard.setStatus(TripStatus.PENDING_APPROVAL);
        ctx.reserve(lateCard);

        assertNull(pickBus(back, TOMORROW_8.withHour(6), ctx),
                "chuyến 06:00 sẽ đưa busAway về bến nhà, trong khi nó đã nhận chuyến 18:00 xuất phát ở bến xa");
    }

    // =====================================================================
    // Đường AvailabilityContext = đường DB
    // =====================================================================

    @Test
    void theContextPathGivesTheSameAnswersAsTheDbPath() {
        save(trip(out, busHome, dHome, TOMORROW_8, TripStatus.PENDING_APPROVAL));
        flushAndClear();
        reload();
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(6).atStartOfDay());

        for (int day = 1; day <= 4; day++) {
            for (int hour : new int[] { 6, 12, 18 }) {
                LocalDateTime dep = LocalDate.now().plusDays(day).atTime(hour, 0);
                for (Route route : List.of(out, back)) {
                    assertEquals(idOf(pickBus(route, dep, null)), idOf(pickBus(route, dep, ctx)), route.getId() + " " + dep);
                    Driver viaDb = tripService.selectBestAvailableDriver(dep, dep.plusHours(6), 6, 1, List.of(),
                            false, null, route);
                    Driver viaCtx = tripService.selectBestAvailableDriver(dep, dep.plusHours(6), 6, 1, List.of(),
                            false, ctx, route);
                    assertEquals(viaDb == null ? null : viaDb.getUserId(), viaCtx == null ? null : viaCtx.getUserId(),
                            "tài xế " + route.getId() + " " + dep);
                }
                for (Bus bus : List.of(busHome, busAway, busNowhere)) {
                    assertEquals(tripService.busStationAt(bus, dep, null), tripService.busStationAt(bus, dep, ctx),
                            bus.getLicensePlate() + " " + dep);
                }
            }
        }
        assertNotNull(ctx.busLegBefore(busAway.getId()), "lượt trước khoảng đã nạp được cho xe ở bến xa");
    }

    /** Thẻ Đề xuất giữ chỗ (AvailabilityContext.reserve) cũng dời xe, như một chuyến chờ duyệt. */
    @Test
    void aReservedCardMovesTheBusWithinTheContext() {
        busAway.setStatus(BusStatus.REPAIRING);
        busRepository.save(busAway);
        flushAndClear();
        reload();
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(6).atStartOfDay());
        Trip card = candidate(out, TOMORROW_8);
        card.setBus(busHome);
        card.setDriver(dHome);
        card.setStatus(TripStatus.PENDING_APPROVAL);
        ctx.reserve(card);

        LocalDateTime dayAfter = TOMORROW_8.plusDays(1);
        assertEquals(busHome.getId(), idOf(pickBus(back, dayAfter, ctx)), "thẻ đã đưa xe lên bến xa");
        assertNull(pickBus(back, dayAfter, null), "trong DB thì không có thẻ nào, bến xa không còn xe chạy được");
    }

    // =====================================================================
    // Người: mọi vai trên xe (TripRepository hỏi riêng từng vai)
    // =====================================================================

    /**
     * Ai đi trên xe — tài xế, phụ xe hay tài xế phụ — cũng tới bến cuối cùng xe, và chuyến MUỘN
     * NHẤT, ở bất kỳ vai nào, quyết định vị trí. Mỗi vai là một câu riêng (một câu OR quét cả bảng
     * trips), nên test này hỏng nếu bỏ sót một câu hay gộp sai: dHome chỉ từng là phụ xe, dNowhere
     * chỉ từng là tài xế phụ, còn dAway là tài xế chuyến đi rồi tài xế phụ chuyến về muộn hơn.
     * Các chuyến đều trước khoảng context nạp, nên đường context đi qua lượt "trước khoảng".
     */
    @Test
    void aPersonIsWhereTheirLatestTripEnded_inWhicheverRoleTheyRode() {
        LocalDateTime twoDaysAgo = LocalDate.now().minusDays(2).atTime(8, 0);
        Trip outbound = trip(out, busHome, null, twoDaysAgo, TripStatus.COMPLETED);
        outbound.setAssistant(dHome);
        outbound.getCoDrivers().add(dNowhere);
        save(outbound);
        Trip inbound = trip(back, busAway, null, twoDaysAgo, TripStatus.COMPLETED);
        inbound.getCoDrivers().add(dAway);
        save(inbound);
        flushAndClear();
        reload();
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(6).atStartOfDay());

        Map<Driver, Long> expected = Map.of(dHome, far.getId(), dNowhere, far.getId(), dAway, home.getId());
        expected.forEach((person, station) -> {
            String who = person.getUser().getUsername();
            assertEquals(station, tripService.personStationAt(person, TOMORROW_8, null), who + " (DB)");
            assertEquals(station, tripService.personStationAt(person, TOMORROW_8, ctx), who + " (context)");
            assertEquals(station, ctx.personLegBefore(person.getUserId()).toStationId(), who + " (lượt trước khoảng)");
        });
    }

    /**
     * Phía sau, cũng ở mọi vai: chuyến chờ duyệt tuần sau — SAU khoảng context nạp — có dHome làm phụ
     * xe, một tài xế khác làm tài xế phụ, xuất phát từ bến nhà. Nhận thêm chuyến bến nhà → bến xa
     * ngày mai thì cả hai kẹt ở bến xa, nên không ai trong họ vừa chuỗi.
     */
    @Test
    void aLaterTripInAnyRoleIsTheirNextCommittedLeg() {
        Driver dHome2 = driver("loc-home2", home);
        Trip later = trip(out, busHome, null, TOMORROW_8.plusDays(7), TripStatus.PENDING_APPROVAL);
        later.setAssistant(dHome);
        later.getCoDrivers().add(dHome2);
        save(later);
        flushAndClear();
        reload();
        Driver dHome2Again = em.find(Driver.class, dHome2.getUserId());
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(6).atStartOfDay());
        LocalDateTime arr = TOMORROW_8.plusMinutes(360);

        for (Driver person : List.of(dHome, dHome2Again)) {
            String who = person.getUser().getUsername();
            assertEquals("chuyến kế tiếp đi từ Bến nhà LOC", tripService.personLocationNote(person, out, TOMORROW_8, arr),
                    who + " (DB)");
            assertEquals(home.getId(), ctx.personLegAfter(person.getUserId()).fromStationId(), who + " (lượt sau khoảng)");
        }
        assertNull(tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(), false, null, out),
                "DB: hai người ở bến nhà đều vướng chuyến tuần sau; dAway ở bến xa, dNowhere không rõ ở đâu");
        assertNull(tripService.selectBestAvailableDriver(TOMORROW_8, arr, 6, 1, List.of(), false, ctx, out),
                "context: cùng câu trả lời");
    }

    // =====================================================================
    // Người chọn: MỀM
    // =====================================================================

    @Test
    void theDropdownsOfferEveryValidChoice_standingFirst_withANoteForTheOthers() {
        Trip pending = save(trip(out, null, null, TOMORROW_8, TripStatus.PENDING_APPROVAL));
        flushAndClear();
        reload();

        List<Long> buses = tripService.getAvailableBusesForTrip(pending.getId()).stream().map(Bus::getId).toList();
        assertEquals(3, buses.size(), "vẫn mời cả ba xe: " + buses);
        assertEquals(busHome.getId(), buses.get(0), "xe đang ở bến đi lên đầu, dù nhiều km nhất");

        Trip trip = tripService.getTripById(pending.getId());
        Map<Long, String> busNotes = tripService.busLocationNotes(trip);
        assertFalse(busNotes.containsKey(busHome.getId()), "xe đúng bến không có ghi chú");
        assertEquals("đang ở Bến xa LOC", busNotes.get(busAway.getId()));
        assertEquals("chưa rõ đang ở đâu (chưa có bến nhà)", busNotes.get(busNowhere.getId()));

        List<Long> drivers = tripService.getAvailableDriversForTrip(pending.getId()).stream()
                .map(Driver::getUserId).toList();
        assertEquals(3, drivers.size());
        assertEquals(dHome.getUserId(), drivers.get(0));
        assertEquals(dHome.getUserId(), tripService.getAvailableAssistantsForTrip(pending.getId()).get(0).getUserId());
        Map<Long, String> personNotes = tripService.personLocationNotes(trip);
        assertEquals("đang ở Bến xa LOC", personNotes.get(dAway.getUserId()));
        assertFalse(personNotes.containsKey(dHome.getUserId()));
    }

    @Test
    void theCreateFormListsKeepEveryChoice_andPutTheStartStationFirst() {
        LocalDateTime arr = TOMORROW_8.plusHours(6);
        List<Long> plain = tripService.getAvailableBusesForTimeRange(TOMORROW_8, arr).stream().map(Bus::getId).toList();
        List<Long> fromFar = tripService.getAvailableBusesForTimeRange(TOMORROW_8, arr, back).stream()
                .map(Bus::getId).toList();

        assertEquals(3, fromFar.size());
        assertEquals(busAway.getId(), fromFar.get(0), "đi từ bến xa: xe ở bến xa lên đầu");
        assertEquals(plain, tripService.getAvailableBusesForTimeRange(TOMORROW_8, arr, (Route) null).stream()
                .map(Bus::getId).toList(), "chưa chọn tuyến thì giữ nguyên thứ tự cũ");
        assertEquals(dAway.getUserId(),
                tripService.getAvailableDriversForTimeRange(TOMORROW_8, arr, back).get(0).getUserId());
        assertNull(tripService.busLocationNote(busAway, back, TOMORROW_8, arr));
        assertEquals("đang ở Bến nhà LOC", tripService.busLocationNote(busHome, back, TOMORROW_8, arr));
    }

    @Test
    void theValidatorDoesNotBlockByLocation() {
        Trip trip = candidate(out, TOMORROW_8);
        trip.setBus(busAway);
        trip.setDriver(dAway);
        trip.setTotalSeats(22);

        ValidationResult bus = tripService.validateBusForTripDryRun(busAway, trip, null);
        ValidationResult staff = tripService.validateStaffForTripDryRun(trip, null);

        assertTrue(bus.isValid(), "Admin vẫn chọn được xe ở bến khác: " + bus.getFailureReason());
        assertTrue(staff.isValid(), "và tài xế ở bến khác: " + staff.getFailureReason());
    }

    // =====================================================================

    private Bus pickBus(Route route, LocalDateTime departure, AvailabilityContext ctx) {
        Trip candidate = candidate(route, departure);
        return tripService.selectBestAvailableBus(candidate, departure, candidate.getArrivalTimeExpected(), ctx);
    }

    private static Long idOf(Bus bus) {
        return bus == null ? null : bus.getId();
    }

    private Trip candidate(Route route, LocalDateTime departure) {
        Trip t = new Trip();
        t.setRoute(routeRepository.findByIdWithStations(route.getId()).orElseThrow());
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(360));
        return t;
    }

    private Trip trip(Route route, Bus bus, Driver driver, LocalDateTime departure, TripStatus status) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setBus(bus);
        t.setDriver(driver);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(360));
        t.setTotalSeats(22);
        t.setTicketsSold(0);
        t.setPrice(new BigDecimal("300000"));
        t.setStatus(status);
        return t;
    }

    private Trip save(Trip trip) {
        return tripRepository.save(trip);
    }

    /** Đọc lại sau flush + clear, để mọi phép thử đi qua DB thật chứ không qua entity trong bộ nhớ. */
    private void reload() {
        busHome = busRepository.findById(busHome.getId()).orElseThrow();
        busAway = busRepository.findById(busAway.getId()).orElseThrow();
        busNowhere = busRepository.findById(busNowhere.getId()).orElseThrow();
        dHome = em.find(Driver.class, dHome.getUserId());
        dAway = em.find(Driver.class, dAway.getUserId());
        dNowhere = em.find(Driver.class, dNowhere.getUserId());
        out = routeRepository.findByIdWithStations(out.getId()).orElseThrow();
        back = routeRepository.findByIdWithStations(back.getId()).orElseThrow();
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name);
        return stationRepository.save(s);
    }

    private BusType busType() {
        BusType t = new BusType();
        t.setTypeName("LOC 22");
        t.setCapacity(22);
        return busTypeRepository.save(t);
    }

    private Route route(Station from, Station to) {
        Route r = new Route();
        r.setDistanceKm(300.0);
        r.setEstimatedDuration(360);
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

    private Bus bus(String plate, BusType type, double kmSinceMaintenance, Station homeStation) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBusType(type);
        b.setLastMaintenanceOdometer(1000.0);
        b.setOdometer(1000.0 + kmSinceMaintenance);
        b.setMaintenanceThreshold(10000.0);
        b.setStatus(BusStatus.READY);
        b.setHomeStation(homeStation);
        return busRepository.save(b);
    }

    private Driver driver(String username, Station homeStation) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("LOC-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        d.setHomeStation(homeStation);
        user.setDriver(d);
        return userRepository.save(user).getDriver();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
