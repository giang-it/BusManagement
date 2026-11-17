package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistoricalDataBackfill.Occupied;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.HolidayFlow;
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
import giang.com.BusManagement.repository.DriverRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ sinh dữ liệu lịch sử chạy THẬT trên busmanagement_test (QĐ-10, QĐ-11, QĐ-21): ghi DB, xe
 * chạy vòng trên hai cặp tuyến, cộng odometer, chạy lại không nhân bản, chạy bổ sung nối tiếp
 * đúng vị trí, từ chối trộn với dữ liệu của bộ sinh cũ hay danh sách tuyến thiếu chiều ngược,
 * và né xe/người của chuyến có sẵn (GĐ3, lỗi #33).
 *
 * Gọi generate(lastDay) trực tiếp trên một instance dựng tay (bean chỉ tồn tại ở profile
 * backfill). @Transactional nên mọi bản ghi được rollback sau từng test.
 */
@SpringBootTest
@Transactional
class HistoricalDataBackfillGenerateTest {

    private static final LocalDate EPOCH = HistorySimulation.EPOCH;
    private static final LocalDate LAST_DAY = EPOCH.plusDays(13);

    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private HistoricalDataBackfill backfill;
    private List<Route> routes;
    private Station home;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà GEN");
        Station near = station("Bến gần GEN");
        Station far = station("Bến xa GEN");
        BusType seats22 = busType("Lễ 22", 22);
        BusType seats40 = busType("Lễ 40", 40);
        for (int i = 1; i <= 10; i++) {
            bus("GEN-" + i, i <= 4 ? seats22 : seats40, 1000.0 * i);
        }
        for (int i = 1; i <= 14; i++) {
            driver("gen-driver-" + i);
        }
        routes = List.of(
                route(seats22, HolidayFlow.LEAVING_HUB, 120.0, 120, home, near),
                route(seats22, HolidayFlow.TOWARD_HUB, 120.0, 120, near, home),
                // 8 tiếng, mức tối đa bộ sinh nhận: chuyến 18:00 về lúc 02:00 hôm sau
                route(seats40, HolidayFlow.LEAVING_HUB, 180.0, 480, home, far),
                route(seats40, HolidayFlow.TOWARD_HUB, 180.0, 480, far, home));
        flushAndClear();
        backfill = new HistoricalDataBackfill(tripRepository, routeRepository, busRepository, driverRepository);
        backfill.routeIds = routes.stream().map(Route::getId).toList();
    }

    @Test
    void generate_writesEveryDay_withBusesAndDriversRunningInLoops() {
        int created = backfill.generate(LAST_DAY);
        flushAndClear();

        List<Trip> trips = generatedTrips();
        assertEquals(created, trips.size());
        Set<LocalDate> days = new HashSet<>();
        for (Trip trip : trips) {
            LocalDate day = trip.getDepartureTime().toLocalDate();
            days.add(day);
            assertTrue(trip.getTicketsSold() <= trip.getTotalSeats());
            assertEquals(trip.getBus().getBusType().getCapacity(), trip.getTotalSeats());
            assertTrue(trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED);
            if (trip.isExtraTrip()) {
                assertEquals(trip.getOriginalTrip().getRoute().getId(), trip.getRoute().getId());
                assertEquals(trip.getOriginalTrip().getDepartureTime().getHour(), trip.getDepartureTime().getHour());
            }
        }
        assertEquals(14, days.size(), "đủ 14 ngày từ EPOCH");
        assertLoopsAreContinuous(trips);
    }

    /** Odometer: cộng CẢ HAI mốc đúng bằng km của chuyến COMPLETED — kmSinceLastMaintenance không đổi. */
    @Test
    void generate_addsCompletedKmToBothOdometerMarks() {
        Map<Long, Double> sinceMaintenanceBefore = new HashMap<>();
        Map<Long, Double> odometerBefore = new HashMap<>();
        for (Bus bus : busRepository.findAll()) {
            sinceMaintenanceBefore.put(bus.getId(), bus.getKmSinceLastMaintenance());
            odometerBefore.put(bus.getId(), bus.getOdometer());
        }

        backfill.generate(LAST_DAY);
        flushAndClear();

        Map<Long, Double> kmByBus = new HashMap<>();
        for (Trip trip : generatedTrips()) {
            if (trip.getStatus() == TripStatus.COMPLETED) {
                kmByBus.merge(trip.getBus().getId(), trip.getRoute().getDistanceKm(), Double::sum);
            }
        }
        for (Bus bus : busRepository.findAll()) {
            assertEquals(sinceMaintenanceBefore.get(bus.getId()), bus.getKmSinceLastMaintenance(), 1e-6);
            assertEquals(odometerBefore.get(bus.getId()) + kmByBus.getOrDefault(bus.getId(), 0.0),
                    bus.getOdometer(), 1e-6);
        }
    }

    @Test
    void secondRun_createsNothing() {
        int first = backfill.generate(LAST_DAY);
        flushAndClear();

        int second = backfill.generate(LAST_DAY);
        flushAndClear();

        assertTrue(first > 0);
        assertEquals(0, second, "ngày đã có thì bỏ qua cả ngày");
        assertEquals(first, generatedTrips().size());
    }

    /**
     * Chạy bổ sung chỉ sinh ngày còn thiếu, và NỐI TIẾP đúng chỗ lần trước dừng: xe và tài xế
     * xuất phát từ bến mà chuyến cuối đã ghi của họ tới — kể cả qua ranh giới hai lần chạy.
     */
    @Test
    void topUp_generatesOnlyTheMissingDays_andContinuesEveryLoop() {
        int first = backfill.generate(EPOCH.plusDays(6));
        flushAndClear();
        int second = backfill.generate(LAST_DAY);
        flushAndClear();

        assertTrue(second > 0);
        assertEquals(first + second, generatedTrips().size());
        assertEquals(14, generatedTrips().stream().map(t -> t.getDepartureTime().toLocalDate()).distinct().count());
        assertLoopsAreContinuous(generatedTrips());
    }

    /** Ngày đã sinh phải liền một mạch từ EPOCH: xe chạy vòng nên không sinh chen vào giữa. */
    @Test
    void aGapInTheGeneratedDays_isRefused() {
        backfill.generate(EPOCH.plusDays(6));
        flushAndClear();
        List<Trip> dayThree = generatedTrips().stream()
                .filter(t -> t.getDepartureTime().toLocalDate().equals(EPOCH.plusDays(3))).toList();
        dayThree.stream().filter(Trip::isExtraTrip).forEach(tripRepository::delete);
        flushAndClear();
        dayThree.stream().filter(t -> !t.isExtraTrip()).forEach(tripRepository::delete);
        flushAndClear();
        int before = generatedTrips().size();

        assertEquals(0, backfill.generate(LAST_DAY));
        flushAndClear();
        assertEquals(before, generatedTrips().size());
    }

    /** Tuyến không có chiều ngược trong danh sách thì xe đi không có đường về: dừng cả lần chạy. */
    @Test
    void aRouteWithoutItsReverse_isRefused() {
        backfill.routeIds = List.of(routes.get(0).getId(), routes.get(2).getId(), routes.get(3).getId());
        assertEquals(0, backfill.generate(LAST_DAY));
        flushAndClear();
        assertTrue(generatedTrips().isEmpty());
    }

    /** Cặp tuyến phải khớp đúng một chiều ngược (đề xuất CM-6): hai tuyến cùng bến về thì không biết ghép cặp nào. */
    @Test
    void aRouteWithTwoReverses_isRefused() {
        Station near = stationRepository.findAll().stream()
                .filter(s -> "Bến gần GEN".equals(s.getStationName())).findFirst().orElseThrow();
        Route second = route(routes.get(1).getSuitableBusType(), HolidayFlow.TOWARD_HUB, 120.0, 120, near, home);
        List<Long> ids = new ArrayList<>(backfill.routeIds);
        ids.add(second.getId());
        backfill.routeIds = ids;
        flushAndClear();

        assertEquals(0, backfill.generate(LAST_DAY));
        flushAndClear();
        assertTrue(generatedTrips().isEmpty());
    }

    /** Xe hay tài xế chưa có bến nhà (CT5a) thì không biết họ bắt đầu từ đâu: dừng. */
    @Test
    void aBusWithoutAHomeStation_isRefused() {
        Bus bus = busWithPlate("GEN-3");
        bus.setHomeStation(null);
        busRepository.save(bus);
        flushAndClear();

        assertEquals(0, backfill.generate(LAST_DAY));
        flushAndClear();
        assertTrue(generatedTrips().isEmpty());
    }

    /** Dữ liệu dạng bộ sinh mà ngày EPOCH lại trống là của bộ sinh cũ: phải xoá (GĐ3) trước. */
    @Test
    void oldGeneratorData_isRefused_notMixed() {
        Trip old = new Trip();
        old.setRoute(routes.get(0));
        old.setDepartureTime(EPOCH.plusDays(5).atTime(6, 0));
        old.setArrivalTimeExpected(EPOCH.plusDays(5).atTime(8, 0));
        old.setTotalSeats(22);
        old.setTicketsSold(10);
        old.setPrice(new BigDecimal("120000"));
        old.setStatus(TripStatus.COMPLETED);
        tripRepository.save(old);
        flushAndClear();

        assertEquals(0, backfill.generate(LAST_DAY));
        flushAndClear();
        assertEquals(1, generatedTrips().size());
    }

    /**
     * Lỗi #33: chuyến có sẵn (Admin tự tạo) giữ xe, tài xế, phụ xe và tài xế phụ của nó trong
     * MỌI ngày nó chạy — chuyến qua đêm giữ cả hai ngày. Chuyến đã hủy không giữ gì.
     */
    @Test
    void existingTrips_holdTheirBusAndStaff_onEveryDayTheyTouch() {
        driver("gen-driver-15");
        driver("gen-driver-16"); // 16 tài xế: giữ 3 người vẫn đủ người cho vòng xe mỗi ngày
        flushAndClear();
        Bus heldBus = busWithPlate("GEN-1");
        Driver heldDriver = driverNamed("gen-driver-1");
        Driver heldAssistant = driverNamed("gen-driver-2");
        Driver heldCoDriver = driverNamed("gen-driver-3");
        LocalDate dayOne = EPOCH.plusDays(3);
        LocalDate dayTwo = EPOCH.plusDays(4);

        Trip overnight = manualTrip(dayOne.atTime(21, 0), dayTwo.atTime(3, 0), heldBus, heldDriver,
                TripStatus.COMPLETED);
        overnight.setAssistant(heldAssistant);
        overnight.getCoDrivers().add(heldCoDriver);
        Long overnightId = tripRepository.save(overnight).getId();
        Long cancelledId = tripRepository.save(manualTrip(EPOCH.plusDays(6).atTime(21, 0),
                EPOCH.plusDays(6).atTime(23, 0), busWithPlate("GEN-2"), driverNamed("gen-driver-4"),
                TripStatus.CANCELLED)).getId();
        flushAndClear();

        // Truy vấn nguồn: chuyến qua đêm cho đủ xe và ba người; chuyến đã hủy không có dòng nào.
        List<Object[]> rows = tripRepository.findResourceUseBetween(
                EnumSet.complementOf(EnumSet.of(TripStatus.CANCELLED)),
                EPOCH.atStartOfDay(), LAST_DAY.plusDays(1).atStartOfDay()).stream()
                .filter(r -> r[0].equals(overnightId) || r[0].equals(cancelledId))
                .toList();
        assertEquals(1, rows.size(), "chuyến qua đêm có một tài xế phụ → một dòng; chuyến đã hủy → không dòng nào");
        assertEquals(overnightId, rows.get(0)[0]);
        assertEquals(heldBus.getId(), rows.get(0)[5]);
        assertEquals(List.of(heldDriver.getUserId(), heldAssistant.getUserId(), heldCoDriver.getUserId()),
                List.of(rows.get(0)[6], rows.get(0)[7], rows.get(0)[8]));

        // Bộ sinh đọc ra: đúng hai ngày, đúng một xe và ba người; ngày của chuyến đã hủy không có.
        Set<Long> heldStaff = Set.of(heldDriver.getUserId(), heldAssistant.getUserId(), heldCoDriver.getUserId());
        Map<LocalDate, Occupied> occupied = backfill.loadOccupied(routes, Set.of(), LAST_DAY);
        assertEquals(Set.of(dayOne, dayTwo), occupied.keySet());
        for (LocalDate day : List.of(dayOne, dayTwo)) {
            assertEquals(Set.of(heldBus.getId()), occupied.get(day).busIds(), day.toString());
            assertEquals(heldStaff, occupied.get(day).staffIds(), day.toString());
        }

        backfill.generate(LAST_DAY);
        flushAndClear();

        boolean heldBusUsedOnOtherDays = false;
        boolean heldStaffUsedOnOtherDays = false;
        for (Trip trip : generatedTrips()) {
            if (trip.getId().equals(overnightId) || trip.getId().equals(cancelledId)) {
                continue;
            }
            LocalDate day = trip.getDepartureTime().toLocalDate();
            if (day.equals(dayOne) || day.equals(dayTwo)) {
                assertNotEquals(heldBus.getId(), trip.getBus().getId(), day + ": xe đang chạy chuyến có sẵn");
                assertFalse(heldStaff.contains(trip.getDriver().getUserId()),
                        day + ": tài xế " + trip.getDriver().getUserId() + " đang ở chuyến có sẵn");
            } else {
                heldBusUsedOnOtherDays |= trip.getBus().getId().equals(heldBus.getId());
                heldStaffUsedOnOtherDays |= heldStaff.contains(trip.getDriver().getUserId());
            }
        }
        assertTrue(heldBusUsedOnOtherDays && heldStaffUsedOnOtherDays,
                "ngày khác xe và người đó vẫn được xếp — test không đậu suông");
    }

    /**
     * Chuyến DẠNG BỘ SINH không bị tính là chuyến có sẵn giữ xe: chuyến 18:00 của tuyến 8 tiếng
     * về lúc 02:00 hôm sau, vậy mà lần chạy bổ sung vẫn phải xếp hôm sau y như một lần chạy liền
     * (lần chạy liền không coi chuyến của chính nó là "có sẵn").
     */
    @Test
    void generatorTrips_doNotHoldTheirBusOrDriverForTheNextDay() {
        LocalDate lastOfFirstRun = EPOCH.plusDays(6);
        backfill.generate(lastOfFirstRun);
        flushAndClear();

        assertTrue(generatedTrips().stream().anyMatch(t -> t.getDepartureTime().toLocalDate().equals(lastOfFirstRun)
                && t.getArrivalTimeExpected().toLocalDate().equals(lastOfFirstRun.plusDays(1))),
                "phải có chuyến qua đêm sang ngày đầu của lần chạy bổ sung, test mới có nghĩa");
        Set<LocalDate> generated = new HashSet<>();
        for (LocalDate day = EPOCH; !day.isAfter(lastOfFirstRun); day = day.plusDays(1)) {
            generated.add(day);
        }
        assertTrue(backfill.loadOccupied(routes, generated, LAST_DAY).isEmpty());
    }

    // =====================================================================

    /**
     * Bất biến vòng xe (QĐ-21 (c)) trên các chuyến dạng bộ sinh đã ghi: theo từng xe và từng tài
     * xế, chuyến đầu từ bến nhà, chuyến sau từ bến chuyến trước tới, và nghỉ đủ.
     */
    private void assertLoopsAreContinuous(List<Trip> trips) {
        Map<Long, List<Trip>> byBus = new HashMap<>();
        Map<Long, List<Trip>> byDriver = new HashMap<>();
        for (Trip trip : trips) {
            if (trip.getStatus() == TripStatus.CANCELLED || trip.getDepartureTime().getMinute() % 10 != 0
                    || trip.getDepartureTime().getHour() % 6 != 0) {
                continue; // chuyến hủy không chạy; chuyến có sẵn của test nằm ngoài vòng xe
            }
            byBus.computeIfAbsent(trip.getBus().getId(), k -> new ArrayList<>()).add(trip);
            byDriver.computeIfAbsent(trip.getDriver().getUserId(), k -> new ArrayList<>()).add(trip);
        }
        assertFalse(byBus.isEmpty());
        for (Map<Long, List<Trip>> runs : List.of(byBus, byDriver)) {
            long rest = runs == byBus ? 60 : 30;
            runs.forEach((id, list) -> {
                list.sort(Comparator.comparing(Trip::getDepartureTime));
                assertEquals(home.getId(), list.get(0).getRoute().getDepartureStation().getId(), "#" + id);
                for (int i = 1; i < list.size(); i++) {
                    Trip before = list.get(i - 1);
                    Trip next = list.get(i);
                    assertEquals(before.getRoute().getDestinationStation().getId(),
                            next.getRoute().getDepartureStation().getId(), "#" + id + " " + next.getDepartureTime());
                    assertFalse(before.getArrivalTimeExpected().plusMinutes(rest).isAfter(next.getDepartureTime()),
                            "#" + id + " " + next.getDepartureTime() + ": nghỉ chưa đủ");
                }
            });
        }
    }

    private List<Trip> generatedTrips() {
        Set<Long> routeIds = new HashSet<>();
        routes.forEach(r -> routeIds.add(r.getId()));
        return em.createQuery("SELECT t FROM Trip t JOIN FETCH t.route LEFT JOIN FETCH t.bus b "
                + "LEFT JOIN FETCH b.busType LEFT JOIN FETCH t.driver LEFT JOIN FETCH t.originalTrip "
                + "WHERE t.route.id IN :ids", Trip.class)
                .setParameter("ids", routeIds)
                .getResultList();
    }

    private Trip manualTrip(LocalDateTime departure, LocalDateTime arrival, Bus bus, Driver driver,
            TripStatus status) {
        Trip trip = new Trip();
        trip.setRoute(routes.get(0));
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setDepartureTime(departure);
        trip.setArrivalTimeExpected(arrival);
        trip.setTotalSeats(22);
        trip.setTicketsSold(10);
        trip.setPrice(new BigDecimal("120000"));
        trip.setStatus(status);
        return trip;
    }

    private Bus busWithPlate(String plate) {
        return busRepository.findAll().stream()
                .filter(b -> plate.equals(b.getLicensePlate()))
                .findFirst()
                .orElseThrow();
    }

    private Driver driverNamed(String username) {
        return em.createQuery("SELECT d FROM Driver d JOIN FETCH d.user u WHERE u.username = :u", Driver.class)
                .setParameter("u", username)
                .getSingleResult();
    }

    private BusType busType(String name, int capacity) {
        BusType type = new BusType();
        type.setTypeName(name);
        type.setCapacity(capacity);
        return busTypeRepository.save(type);
    }

    private Station station(String name) {
        Station station = new Station();
        station.setStationName(name);
        return stationRepository.save(station);
    }

    private void bus(String plate, BusType type, double odometer) {
        Bus bus = new Bus();
        bus.setLicensePlate(plate);
        bus.setHomeStation(home);
        bus.setBusType(type);
        bus.setOdometer(odometer);
        bus.setLastMaintenanceOdometer(odometer - 100);
        bus.setMaintenanceThreshold(10000.0);
        bus.setStatus(BusStatus.READY);
        busRepository.save(bus);
    }

    private void driver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);

        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("G-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.of(2030, 1, 1));
        d.setHomeStation(home);
        user.setDriver(d);
        userRepository.save(user);
    }

    private Route route(BusType type, HolidayFlow flow, double km, int minutes, Station from, Station to) {
        Route route = new Route();
        route.setSuitableBusType(type);
        route.setHolidayFlow(flow);
        route.setDistanceKm(km);
        route.setEstimatedDuration(minutes);
        Route saved = routeRepository.save(route);
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

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
