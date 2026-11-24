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
import giang.com.BusManagement.dto.RegularTimetableRunDto;
import giang.com.BusManagement.dto.RegularTimetableViewDto;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT6: lịch thường lệ (RegularTimetableService) — CM-4, CM-6, CM-14, CM-17…CM-21.
 *
 * <p>Bối cảnh của mọi test: một cặp tuyến kiểu Vũng Tàu — "Bến nhà TT" ↔ "Bến xa TT", 100 km, 2 giờ,
 * loại xe 22 chỗ — có giá vé lịch sử khác nhau ở mỗi giờ (06: 100.000đ, 12: 110.000đ, 18: 120.000đ),
 * để test đọc được giá có lấy đúng khung hay không. Xe và người dựng ngay trong từng test. Lịch
 * tạo cho NGÀY MAI, nên mọi khung đều chưa qua giờ và giờ nền mock (chỉ cộng cho hôm nay) không nhiễu.
 */
@SpringBootTest
@Transactional
class RegularTimetableServiceTest {

    @Autowired private RegularTimetableService service;
    @Autowired private TripService tripService;
    @Autowired private TripRepository tripRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private Station home;
    private Station far;
    private BusType type;
    private Route out;
    private Route back;
    private List<Long> configuredBefore;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà TT");
        far = station("Bến xa TT");
        type = busType();
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

    /** service là proxy giao dịch của Spring; cấu hình tuyến phải gán vào đối tượng thật bên trong. */
    private RegularTimetableService target() {
        return org.springframework.test.util.AopTestUtils.getTargetObject(service);
    }

    // =====================================================================
    // Một ngày của cặp: vòng xe tự hình thành, theo giờ (CM-19, CM-17)
    // =====================================================================

    /**
     * Ba xe và ba người, tất cả ở bến nhà; bến xa trống. Xếp theo giờ: 06:00 đi lấy xe A (ít km
     * nhất); 06:00 về không ai ở bến xa → chờ duyệt, chưa có xe (CM-17); 12:00 đi lấy B (A đang ở bến
     * xa); 12:00 về lấy A vừa tới; 18:00 đi lấy lại A (đã về lúc 14:00, vẫn ít km nhất); 18:00 về lấy
     * B. Vòng hai xe A, B tự hình thành; C không cần dùng.
     */
    @Test
    void aDayOfThePair_formsTheBusLoopsByItself_andTheFarEndStartsWithAPendingTrip() {
        Bus a = bus("TT-A", 100, BusStatus.READY);
        Bus b = bus("TT-B", 200, BusStatus.READY);
        bus("TT-C", 300, BusStatus.READY);
        people(3);
        flushAndClear();

        RegularTimetableRunDto run = service.generate(TOMORROW, 1);

        assertEquals(5, run.getCreated());
        assertEquals(1, run.getPending());
        RegularTimetableRunDto.PendingRow pending = run.getPendingTrips().get(0);
        assertEquals(TOMORROW.atTime(6, 0), pending.getDeparture());
        assertTrue(pending.getReason().startsWith("Không có xe"), pending.getReason());

        assertEquals(a.getId(), busOf(out, 6));
        assertEquals(b.getId(), busOf(out, 12));
        assertEquals(a.getId(), busOf(back, 12));
        assertEquals(a.getId(), busOf(out, 18));
        assertEquals(b.getId(), busOf(back, 18));

        for (Trip trip : mainTrips()) {
            if (trip.getStatus() == TripStatus.PENDING_APPROVAL) {
                assertNull(trip.getBus(), "chuyến chờ duyệt chưa có xe");
                assertNull(trip.getDriver());
                assertEquals(22, trip.getTotalSeats(), "số ghế tạm = xe chuẩn của tuyến");
                continue;
            }
            assertEquals(TripStatus.ACTIVE, trip.getStatus());
            assertNotNull(trip.getSaleOpenedAt(), "ACTIVE qua createManualTrip: đã mở bán");
            assertFalse(trip.isExtraTrip());
            assertEquals(22, trip.getTotalSeats(), "số ghế = sức chứa xe được giao");
            assertEquals(0, priceAt(trip.getDepartureTime().getHour()).compareTo(trip.getPrice()),
                    "giá vé của đúng khung giờ");
            // Phép kiểm độc lập: hỏi lại DB (không qua context) xe và người đứng đâu lúc khởi hành.
            Long start = trip.getRoute().getDepartureStation().getId();
            assertEquals(start, tripService.busStationAt(trip.getBus(), trip.getDepartureTime(), null),
                    "xe đứng ở bến xuất phát: chuyến #" + trip.getId());
            assertEquals(start, tripService.personStationAt(trip.getDriver(), trip.getDepartureTime(), null),
                    "tài xế đứng ở bến xuất phát: chuyến #" + trip.getId());
            if (trip.getAssistant() != null) {
                assertEquals(start, tripService.personStationAt(trip.getAssistant(), trip.getDepartureTime(), null),
                        "phụ xe đứng ở bến xuất phát: chuyến #" + trip.getId());
            }
        }
    }

    /** Bấm lại: mọi khung đã có chuyến chính (cả chuyến chờ duyệt) — không tạo gì thêm (CM-18). */
    @Test
    void pressingTheButtonAgain_createsNothing() {
        bus("TT-A", 100, BusStatus.READY);
        bus("TT-B", 200, BusStatus.READY);
        people(3);
        flushAndClear();
        service.generate(TOMORROW, 1);
        flushAndClear();

        RegularTimetableRunDto again = service.generate(TOMORROW, 1);

        assertEquals(0, again.getCreated());
        assertEquals(0, again.getPending());
        assertEquals(6, again.getSkippedExisting());
        assertEquals(6, mainTrips().size());
    }

    // =====================================================================
    // Khung đã có chuyến chính (CM-18)
    // =====================================================================

    /**
     * Chuyến Admin tự tạo lúc 06:15 giữ khung 06:00; chuyến 12:00 Admin đã hủy giữ khung của nó (bấm
     * lại không dựng lại); chuyến TĂNG CƯỜNG 12:30 không giữ khung 12:00 — đó không phải chuyến chính.
     */
    @Test
    void aManualTrip_orACancelledOne_keepsItsSlot_butAnExtraTripDoesNot() {
        bus("TT-A", 100, BusStatus.READY);
        bus("TT-B", 200, BusStatus.READY);
        people(3);
        save(bare(out, TOMORROW.atTime(6, 15), TripStatus.ACTIVE, false));
        save(bare(back, TOMORROW.atTime(12, 0), TripStatus.CANCELLED, false));
        save(bare(out, TOMORROW.atTime(12, 30), TripStatus.PENDING_APPROVAL, true));
        flushAndClear();

        RegularTimetableRunDto run = service.generate(TOMORROW, 1);

        assertEquals(2, run.getSkippedExisting());
        assertEquals(1, countMain(out, 6), "chỉ chuyến 06:15 của Admin");
        assertEquals(1, countMain(back, 12), "chỉ chuyến đã hủy — không dựng lại");
        assertEquals(1, countMain(out, 12), "chuyến tăng cường 12:30 không giữ khung 12:00");
    }

    // =====================================================================
    // Giá vé (CM-20)
    // =====================================================================

    /** Khung không có giá vé lịch sử thì không tạo: không biết bán giá nào. */
    @Test
    void aSlotWithoutAHistoricalPrice_isSkipped() {
        Station x = station("Bến X TT");
        Route outX = route(home, x);
        Route backX = route(x, home);
        price(outX, 6, 90000);
        price(backX, 6, 90000);
        target().routeIds = List.of(outX.getId(), backX.getId());
        bus("TT-A", 100, BusStatus.READY);
        people(2);
        flushAndClear();

        RegularTimetableRunDto run = service.generate(TOMORROW, 1);

        assertEquals(4, run.getSkippedNoPrice(), "12:00 và 18:00, hai chiều");
        assertEquals(2, run.getCreated() + run.getPending(), "chỉ khung 06:00");
    }

    // =====================================================================
    // Bảo trì nhìn cả km đã xếp (CM-21)
    // =====================================================================

    /**
     * Ngưỡng 10.000 km, bị loại từ 9.000 km. Từ CT7d máy còn cộng quãng về bến nhà (R4): xe chỉ rời bến nhà
     * khi đi rồi về vẫn dưới mốc. Xe A (8.700) chạy 06:00 đi (8.700 + 100 + 100 về = 8.900) và 12:00 về. Xe C
     * (8.800) không đi nổi một vòng nào (8.800 + 200 = 9.000), nên ngay khung 06:00 được đặt ngày bảo trì ở
     * bến nhà ngày mai (CT7d). Chuyến 18:00: A đã cam kết 200 km, đi thêm một vòng là 9.100 — bị loại, dù km
     * THẬT của nó (8.700) ít nhất; B đang ở bến xa; C bảo trì — nên chờ phân công. Trước CT7d máy lấy C cho
     * chuyến 18:00 (8.900), và C kẹt ở bến xa: về thêm 100 km là 9.000.
     */
    @Test
    void maintenanceCountsTheKmAlreadyPlanned_notOnlyTheTripAtHand() {
        Bus a = bus("TT-A", 8700, BusStatus.READY);
        Bus b = bus("TT-B", 8750, BusStatus.READY);
        Bus c = bus("TT-C", 8800, BusStatus.READY);
        people(3);
        flushAndClear();

        RegularTimetableRunDto run = service.generate(TOMORROW, 1);

        assertEquals(a.getId(), busOf(out, 6));
        assertEquals(b.getId(), busOf(out, 12), "A ở bến xa, C bảo trì");
        assertEquals(a.getId(), busOf(back, 12));
        assertNull(busOf(out, 18), "A: 8.700 + 200 đã cam kết + 100 + 100 về = 9.100 ≥ 90% ngưỡng");
        assertEquals(b.getId(), busOf(back, 18));
        assertEquals(1, run.getMaintenancePlanned().size());
        assertEquals("TT-C", run.getMaintenancePlanned().get(0).getBusPlate());
        assertEquals(TOMORROW, run.getMaintenancePlanned().get(0).getDate());
        assertTrue(mainTrips().stream().noneMatch(t -> t.getBus() != null && t.getBus().getId().equals(c.getId())),
                "ngày bảo trì: C không chạy");
    }

    // =====================================================================
    // Xe đang chạy hôm nay vẫn được xếp cho ngày mai (CM-21, lỗi #19)
    // =====================================================================

    /**
     * "READY" chỉ là trạng thái lúc bấm nút. Xe TRAVELING có chuyến DEPARTED giải thích (nó đang chạy
     * thật, về tới bến nhà trước ngày mai) được xếp; xe TRAVELING không có chuyến nào giải thích (dữ
     * liệu lệch) thì không — đúng luật trạng thái của validateBusForTrip. Xe REPAIRING không bao giờ.
     * Context của màn Đề xuất (không phải lập lịch) vẫn chỉ lấy xe READY, như trước CT6.
     */
    @Test
    void aBusOnTheRoadToday_isScheduledForTomorrow_butAnUnexplainedTravelingFlagIsNot() {
        Bus running = bus("TT-RUN", 200, BusStatus.TRAVELING);
        bus("TT-STALE", 100, BusStatus.TRAVELING);
        bus("TT-REPAIR", 50, BusStatus.REPAIRING);
        Bus ready = bus("TT-READY", 300, BusStatus.READY);
        Trip today = bare(back, LocalDate.now().atTime(1, 0), TripStatus.DEPARTED, false);
        today.setBus(running);
        save(today);
        people(3);
        flushAndClear();

        Trip candidate = bare(out, TOMORROW.atTime(6, 0), TripStatus.PENDING_APPROVAL, false);
        candidate.setRoute(routeRepository.findByIdWithStations(out.getId()).orElseThrow());
        AvailabilityContext recommendation = tripService.buildAvailabilityContext(
                TOMORROW.atStartOfDay(), TOMORROW.plusDays(1).atStartOfDay());
        assertEquals(ready.getId(), tripService.selectBestAvailableBus(candidate, candidate.getDepartureTime(),
                candidate.getArrivalTimeExpected(), recommendation).getId(), "màn Đề xuất: chỉ xe READY, như cũ");

        service.generate(TOMORROW, 1);

        assertEquals(running.getId(), busOf(out, 6), "xe đang chạy hôm nay, ít km nhất trong các xe được xếp");
    }

    // =====================================================================
    // Cặp tuyến (CM-6)
    // =====================================================================

    @Test
    void aRouteWithoutItsReverse_orWithTwoOfThem_isNotScheduled_andThePageSaysWhy() {
        Station x = station("Bến X TT");
        Route lonely = route(home, x);
        price(lonely, 6, 90000);
        target().routeIds = List.of(out.getId(), back.getId(), lonely.getId());
        flushAndClear();
        RegularTimetableViewDto view = service.overview();
        assertEquals(1, view.getPairs().size(), "cặp (đi, về) hiện một lần");
        assertEquals(1, view.getProblems().size());
        assertTrue(view.getProblems().get(0).contains("không có tuyến chiều ngược"), view.getProblems().get(0));

        // Thêm một tuyến bến xa → bến nhà thứ hai: chiều đi có HAI tuyến ngược — mơ hồ, không đoán;
        // và hai tuyến về, mỗi tuyến chỉ ngược với chiều đi đã bị loại, cũng không chạy một chiều.
        Route back2 = route(far, home);
        target().routeIds = List.of(out.getId(), back.getId(), back2.getId());
        flushAndClear();
        view = service.overview();
        assertTrue(view.getPairs().isEmpty());
        assertEquals(3, view.getProblems().size(), String.join(" | ", view.getProblems()));
        assertTrue(view.getProblems().get(0).contains("có 2 tuyến chiều ngược"), view.getProblems().get(0));
        RegularTimetableRunDto run = service.generate(TOMORROW, 1);
        assertEquals(0, run.getCreated() + run.getPending());

        // Chỉ cấu hình chiều về: chiều đi (ngược duy nhất của nó) không có lịch — xe về rồi không có
        // chuyến nào đưa đi lại.
        target().routeIds = List.of(back.getId());
        String problem = service.overview().getProblems().get(0);
        assertTrue(problem.contains("không có lịch"), problem);
    }

    // =====================================================================
    // Trang và đầu vào
    // =====================================================================

    @Test
    void thePageShowsThePair_itsPricesPerHour_andHowManySlotsAreTaken() {
        bus("TT-A", 100, BusStatus.READY);
        bus("TT-B", 200, BusStatus.READY);
        people(3);
        flushAndClear();
        service.generate(TOMORROW, 1);
        flushAndClear();

        RegularTimetableViewDto view = service.overview();

        assertEquals(List.of(6, 12, 18), view.getHours());
        assertEquals(7, view.getDefaultDays());
        assertEquals(6, view.getSlotsPerDay());
        RegularTimetableViewDto.PairRow pair = view.getPairs().get(0);
        assertEquals(out.getId(), pair.getOutboundId());
        assertEquals(back.getId(), pair.getInboundId());
        assertEquals(22, pair.getBusTypeCapacity());
        assertEquals(0, new BigDecimal("110000").compareTo(pair.getInboundPrices().get(1)));
        RegularTimetableViewDto.DayRow tomorrow = view.getCoverage().stream()
                .filter(d -> d.getDate().equals(TOMORROW)).findFirst().orElseThrow();
        assertEquals(6, tomorrow.getMainTrips());
        assertEquals(6, tomorrow.getExpected());
    }

    @Test
    void theInputIsChecked_andPastSlotsOfTodayAreSkipped() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(TOMORROW, 0));
        assertThrows(IllegalArgumentException.class, () -> service.generate(TOMORROW, RegularTimetableService.MAX_DAYS + 1));
        assertThrows(IllegalArgumentException.class, () -> service.generate(LocalDate.now().minusDays(1), 1));

        bus("TT-A", 100, BusStatus.READY);
        people(2);
        flushAndClear();
        LocalDateTime now = LocalDateTime.now();
        long passed = List.of(6, 12, 18).stream().filter(h -> !LocalDate.now().atTime(h, 0).isAfter(now)).count();
        RegularTimetableRunDto run = service.generate(LocalDate.now(), 1);
        assertEquals(2 * passed, run.getSkippedPast(), "hai chiều × số giờ đã qua");
        assertEquals(6 - 2 * passed, run.getCreated() + run.getPending() + run.getSkippedNoPrice());
    }

    // =====================================================================
    // Dựng dữ liệu
    // =====================================================================

    private Long busOf(Route route, int hour) {
        Trip trip = mainTrips().stream()
                .filter(t -> t.getRoute().getId().equals(route.getId()) && t.getDepartureTime().getHour() == hour)
                .findFirst().orElseThrow(() -> new AssertionError("không có chuyến " + route.getId() + "@" + hour));
        return trip.getBus() != null ? trip.getBus().getId() : null;
    }

    private long countMain(Route route, int hour) {
        return mainTrips().stream()
                .filter(t -> t.getRoute().getId().equals(route.getId()) && t.getDepartureTime().getHour() == hour)
                .count();
    }

    /** Chuyến chính (không tăng cường) của ngày mai trên hai tuyến của cặp, theo giờ. */
    private List<Trip> mainTrips() {
        return tripRepository.findAll().stream()
                .filter(t -> !t.isExtraTrip())
                .filter(t -> t.getRoute() != null && List.of(out.getId(), back.getId()).contains(t.getRoute().getId()))
                .filter(t -> t.getDepartureTime().toLocalDate().equals(TOMORROW))
                .sorted(Comparator.comparing(Trip::getDepartureTime))
                .toList();
    }

    private static BigDecimal priceAt(int hour) {
        return switch (hour) {
            case 6 -> new BigDecimal("100000");
            case 12 -> new BigDecimal("110000");
            default -> new BigDecimal("120000");
        };
    }

    /** Giá vé lịch sử: một chuyến COMPLETED 10 ngày trước ở mỗi giờ, không xe, không người (không đổi vị trí ai). */
    private void prices(Route route) {
        for (int hour : List.of(6, 12, 18)) {
            price(route, hour, priceAt(hour).longValue());
        }
    }

    private void price(Route route, int hour, long price) {
        Trip t = bare(route, LocalDate.now().minusDays(10).atTime(hour, 0), TripStatus.COMPLETED, false);
        t.setPrice(BigDecimal.valueOf(price));
        save(t);
    }

    private Trip bare(Route route, LocalDateTime departure, TripStatus status, boolean extra) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(Objects.requireNonNull(route.getEstimatedDuration())));
        t.setStatus(status);
        t.setTotalSeats(22);
        t.setPrice(new BigDecimal("100000"));
        t.setExtraTrip(extra);
        return t;
    }

    private void save(Trip trip) {
        tripRepository.save(trip);
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name);
        return stationRepository.save(s);
    }

    private BusType busType() {
        BusType t = new BusType();
        t.setTypeName("TT 22");
        t.setCapacity(22);
        return busTypeRepository.save(t);
    }

    private Route route(Station from, Station to) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setSuitableBusType(type);
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

    private Bus bus(String plate, double kmSinceMaintenance, BusStatus status) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBusType(type);
        b.setLastMaintenanceOdometer(1000.0);
        b.setOdometer(1000.0 + kmSinceMaintenance);
        b.setMaintenanceThreshold(10000.0);
        b.setStatus(status);
        b.setHomeStation(home);
        return busRepository.save(b);
    }

    /** n người, đều ở bến nhà, chưa chạy chuyến nào. */
    private void people(int n) {
        for (int i = 1; i <= n; i++) {
            User user = new User();
            user.setUsername("tt-driver-" + i);
            user.setFullName("Tài xế TT " + i);
            user.setPassword("x");
            user.setRole(Role.ROLE_DRIVER);
            Driver d = new Driver();
            d.setUser(user);
            d.setLicenseNumber("TT-" + i);
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
