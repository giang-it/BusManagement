package giang.com.BusManagement.config;

import giang.com.BusManagement.config.HistoricalDataBackfill.Occupied;
import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.DriverRepository;
import giang.com.BusManagement.repository.RouteRepository;
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
 * Bộ sinh dữ liệu lịch sử chạy THẬT trên busmanagement_test (QĐ-10, QĐ-11): ghi DB, xếp
 * xe/tài xế theo ngày, cộng odometer, chạy lại không nhân bản, từ chối trộn với dữ liệu
 * của bộ sinh cũ, và né xe/người của chuyến có sẵn (GĐ3, lỗi #33).
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
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private HistoricalDataBackfill backfill;
    private List<Route> routes;

    @BeforeEach
    void setUp() {
        BusType seats22 = busType("Lễ 22", 22);
        BusType seats40 = busType("Lễ 40", 40);
        for (int i = 1; i <= 8; i++) {
            bus("GEN-" + i, i <= 4 ? seats22 : seats40, 1000.0 * i);
        }
        for (int i = 1; i <= 8; i++) {
            driver("gen-driver-" + i);
        }
        routes = List.of(
                route(seats22, HolidayFlow.LEAVING_HUB, 120.0, 120),
                // 8 tiếng, mức tối đa bộ sinh nhận: chuyến 18:00 về lúc 02:00 hôm sau
                route(seats40, HolidayFlow.TOWARD_HUB, 180.0, 480));
        flushAndClear();
        backfill = new HistoricalDataBackfill(tripRepository, routeRepository, busRepository, driverRepository);
    }

    @Test
    void generate_writesEveryDay_withOneTripPerBusAndDriverPerDay() {
        int created = backfill.generate(LAST_DAY);
        flushAndClear();

        List<Trip> trips = generatedTrips();
        assertEquals(created, trips.size());
        Map<LocalDate, Set<Long>> busesByDay = new HashMap<>();
        Map<LocalDate, Set<Long>> driversByDay = new HashMap<>();
        Set<LocalDate> days = new HashSet<>();
        for (Trip trip : trips) {
            LocalDate day = trip.getDepartureTime().toLocalDate();
            days.add(day);
            assertTrue(busesByDay.computeIfAbsent(day, d -> new HashSet<>()).add(trip.getBus().getId()),
                    day + ": một xe hai chuyến");
            assertTrue(driversByDay.computeIfAbsent(day, d -> new HashSet<>()).add(trip.getDriver().getUserId()),
                    day + ": một tài xế hai chuyến");
            assertTrue(trip.getTicketsSold() <= trip.getTotalSeats());
            assertEquals(trip.getBus().getBusType().getCapacity(), trip.getTotalSeats());
            assertTrue(trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED);
            if (trip.isExtraTrip()) {
                assertEquals(trip.getOriginalTrip().getRoute().getId(), trip.getRoute().getId());
                assertEquals(trip.getOriginalTrip().getDepartureTime().getHour(), trip.getDepartureTime().getHour());
            }
        }
        assertEquals(14, days.size(), "đủ 14 ngày từ EPOCH");
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

    /** Chạy bổ sung chỉ sinh ngày còn thiếu, và những ngày đó giống hệt một lần chạy liền. */
    @Test
    void topUp_generatesOnlyTheMissingDays() {
        int first = backfill.generate(EPOCH.plusDays(6));
        flushAndClear();
        int second = backfill.generate(LAST_DAY);
        flushAndClear();

        assertEquals(first + second, generatedTrips().size());
        assertEquals(14, generatedTrips().stream().map(t -> t.getDepartureTime().toLocalDate()).distinct().count());
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
        driver("gen-driver-9");
        driver("gen-driver-10"); // 10 tài xế: giữ 3 người vẫn đủ 6 khung mỗi ngày
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

    private void bus(String plate, BusType type, double odometer) {
        Bus bus = new Bus();
        bus.setLicensePlate(plate);
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
        user.setDriver(d);
        userRepository.save(user);
    }

    private Route route(BusType type, HolidayFlow flow, double km, int minutes) {
        Route route = new Route();
        route.setSuitableBusType(type);
        route.setHolidayFlow(flow);
        route.setDistanceKm(km);
        route.setEstimatedDuration(minutes);
        return routeRepository.save(route);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
