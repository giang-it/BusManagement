package giang.com.BusManagement.service;

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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT8b (QĐ-24; CM-15, CM-26, CM-27, CM-28) — job đêm lưu các thẻ Đề xuất đáng chạy thành vòng CHỜ
 * DUYỆT, rút vòng không còn cần, và Admin duyệt / từ chối cả vòng. Chủ dự án duyệt 2026-11-25.
 *
 * <p>Bối cảnh như RecommendationLoopTest: "Bến nhà FL" ↔ "Bến xa FL", 100 km, 2 giờ, xe chuẩn 22 chỗ, giá
 * 100.000đ, một lượt 700.000đ. Lịch sử 60 ngày: khung đi 06:00 có 38 khách (trên xe 40 chỗ, không bán hết),
 * mọi khung khác 10. Mỗi ngày khung 06:00 cần 2 chuyến, nên có 1 thẻ "về ngay": chuyến thêm 06:00 chở ~16
 * khách tràn (1,6 triệu), về bằng khung 12:00 (~0 khách), lãi cả vòng ≈ 1,6 − 1,4 = +0,2 triệu. Mỗi khung
 * 06:00 chiều đi và 12:00 chiều về có một chuyến chính (mở bán, chưa xe) — để kiểm chuyến gốc. Một xe, hai tài
 * xế ở bến nhà. Thẻ nào được tạo phụ thuộc giờ chạy test: chỉ chuyến thêm còn ≥ 72 giờ.
 */
@SpringBootTest
@Transactional
class ForecastLoopServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("100000");

    @Autowired private ForecastLoopService forecastLoopService;
    @Autowired private RecommendationService recommendationService;
    @Autowired private TripService tripService;
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
    private BusType type;
    private Route out;
    private Route back;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà FL");
        far = station("Bến xa FL");
        type = new BusType();
        type.setTypeName("FL 22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
        out = route(home, far, HolidayFlow.LEAVING_HUB);
        back = route(far, home, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 38, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        for (int d = 1; d <= 8; d++) {
            tripRepository.save(bare(out, LocalDate.now().plusDays(d).atTime(6, 0), TripStatus.ACTIVE, 22, false));
            tripRepository.save(bare(back, LocalDate.now().plusDays(d).atTime(12, 0), TripStatus.ACTIVE, 22, false));
        }
        bus("FL-A");
        driver("fl-1");
        driver("fl-2");
        flushAndClear();
    }

    /**
     * Mỗi thẻ đủ điều kiện, có lãi, chuyến thêm còn ≥ 72 giờ thành một vòng chờ duyệt: chuyến thêm ĐÚNG giờ
     * khung (CM-15) + lượt về ngay, cùng xe và tài xế của thẻ, nối bằng forecastLoopId (chuyến thêm trỏ vào
     * chính nó), chuyến gốc của mỗi lượt là chuyến chính của khung nó. Thẻ chưa tới 72 giờ không được tạo.
     */
    @Test
    void profitableFeasibleCards_atLeast72HoursAhead_becomePendingLoops_ofTwoLinkedLegs() {
        LocalDateTime now = LocalDateTime.now();

        ForecastLoopService.Result result = forecastLoopService.reconcile(now);

        int expected = expectedLoops(now);
        assertTrue(expected >= 4, "tiền đề: D+4…D+7 luôn còn ≥ 72 giờ");
        assertNull(result.skipped());
        assertEquals(expected, result.created().size(), String.join("\n", result.created()));
        Map<Long, List<Trip>> loops = pendingLoops();
        assertEquals(expected, loops.size());
        for (Map.Entry<Long, List<Trip>> loop : loops.entrySet()) {
            List<Trip> legs = loop.getValue();
            assertEquals(2, legs.size(), "chuyến thêm + lượt về ngay");
            Trip extra = legs.get(0);
            Trip companion = legs.get(1);
            assertEquals(loop.getKey(), extra.getId(), "chuyến thêm trỏ vào chính nó");
            assertEquals(out.getId(), extra.getRoute().getId());
            assertEquals(6, extra.getDepartureTime().getHour());
            assertEquals(0, extra.getDepartureTime().getMinute(), "đúng giờ khung, không +30 phút (CM-15)");
            assertTrue(!extra.getDepartureTime().isBefore(now.plusHours(72)), "chỉ chuyến thêm còn ≥ 72 giờ");
            assertEquals(back.getId(), companion.getRoute().getId());
            assertEquals(extra.getDepartureTime().toLocalDate().atTime(12, 0), companion.getDepartureTime(),
                    "về ngay bằng khung chiều ngược sớm nhất còn kịp");
            for (Trip leg : legs) {
                assertTrue(leg.isExtraTrip());
                assertEquals(TripStatus.PENDING_APPROVAL, leg.getStatus());
                assertEquals("FL-A", leg.getBus().getLicensePlate(), "cả vòng một xe");
                assertEquals(extra.getDriver().getUserId(), leg.getDriver().getUserId(), "cả vòng một tài xế");
                assertEquals(22, leg.getTotalSeats(), "số ghế theo xe");
                assertEquals(0, PRICE.compareTo(leg.getPrice()));
                assertNull(leg.getSaleOpenedAt(), "chưa mở bán khi chờ duyệt");
                assertNotNull(leg.getOriginalTrip(), "chuyến gốc là chuyến chính của khung");
                assertTrue(!leg.getOriginalTrip().isExtraTrip());
                assertEquals(leg.getDepartureTime(), leg.getOriginalTrip().getDepartureTime());
            }
            assertEquals(loop.getKey(), companion.getForecastLoopId(), "lượt đi kèm trỏ vào chuyến thêm");
        }
    }

    /** Chạy lại (cùng dự báo) không tạo trùng: kế hoạch lần hai đã trừ các vòng đang chờ duyệt. */
    @Test
    void runningAgain_keepsEveryLoop_andCreatesNoDuplicate() {
        LocalDateTime now = LocalDateTime.now();
        int first = forecastLoopService.reconcile(now).created().size();
        flushAndClear();

        ForecastLoopService.Result again = forecastLoopService.reconcile(now);

        assertEquals(0, again.created().size(), String.join("\n", again.created()));
        assertEquals(first, again.kept());
        assertTrue(again.withdrawnByForecast().isEmpty());
        assertEquals(first, pendingLoops().size());
    }

    /**
     * Dự báo giảm (khung 06:00 còn 10 khách): vòng còn chờ duyệt bị rút (QĐ-24 mục 5) — cả hai lượt xoá mềm (bản
     * nháp của job, không thành CANCELLED vì CANCELLED là Admin từ chối); vòng ĐÃ DUYỆT là chuyến thật đang bán,
     * được giữ nguyên.
     */
    @Test
    void whenTheForecastDrops_pendingLoopsAreWithdrawn_butAnApprovedLoopIsKept() {
        LocalDateTime now = LocalDateTime.now();
        forecastLoopService.reconcile(now);
        flushAndClear();
        Long approved = pendingLoops().keySet().iterator().next();
        tripService.confirmAutoAssignedTrip(approved);
        int stillPending = pendingLoops().size();
        flushAndClear();
        em.createQuery("UPDATE Trip t SET t.ticketsSold = 10 WHERE t.route.id = :route AND t.status = :done")
                .setParameter("route", out.getId()).setParameter("done", TripStatus.COMPLETED).executeUpdate();
        flushAndClear();

        ForecastLoopService.Result result = forecastLoopService.reconcile(now);

        assertEquals(stillPending, result.withdrawnByForecast().size());
        assertTrue(result.created().isEmpty());
        assertTrue(pendingLoops().isEmpty());
        for (Trip leg : tripRepository.findByForecastLoopIdOrderByDepartureTimeAsc(approved)) {
            assertEquals(TripStatus.ACTIVE, leg.getStatus(), "vòng đã duyệt không bị rút");
        }
        List<Trip> visible = tripRepository.findAll().stream().filter(t -> t.getForecastLoopId() != null).toList();
        assertEquals(2, visible.size(), "chỉ còn hai lượt của vòng đã duyệt; vòng bị rút được xoá mềm");
        Number deleted = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM trips WHERE forecast_loop_id IS NOT NULL AND is_deleted = true "
                        + "AND status = 'PENDING_APPROVAL'").getSingleResult();
        assertEquals(2L * stillPending, deleted.longValue(), "rút cả hai lượt của mỗi vòng, dòng vẫn nằm trong DB");
    }

    /**
     * Admin từ chối một vòng (CM-27: cả hai lượt CANCELLED): người đã quyết "không" cho khung đó, nên chạy lại
     * job không đề xuất lại khung ấy — các vòng khác vẫn được giữ.
     */
    @Test
    void aSlotWhoseLoopTheAdminRejected_isNotProposedAgain() {
        LocalDateTime now = LocalDateTime.now();
        int first = forecastLoopService.reconcile(now).created().size();
        flushAndClear();
        Long rejected = pendingLoops().keySet().iterator().next();
        tripService.rejectTrip(rejected);
        flushAndClear();

        ForecastLoopService.Result again = forecastLoopService.reconcile(now);

        assertTrue(again.created().isEmpty(), String.join("\n", again.created()));
        assertEquals(1, again.refusedSkipped(), "khung bị từ chối không được tạo lại");
        assertEquals(first - 1, again.kept());
        for (Trip leg : tripRepository.findByForecastLoopIdOrderByDepartureTimeAsc(rejected)) {
            assertEquals(TripStatus.CANCELLED, leg.getStatus());
        }
    }

    /** Vòng chờ duyệt mà chuyến thêm còn dưới 72 giờ thì bị rút (CM-28 (b)) — không kịp bán vé. */
    @Test
    void aPendingLoopWithLessThan72HoursLeft_isWithdrawnByTheDeadline() {
        Bus bus = busRepository.findAll().stream().filter(b -> "FL-A".equals(b.getLicensePlate())).findFirst().orElseThrow();
        LocalDateTime soon = LocalDate.now().plusDays(2).atTime(18, 0);
        Trip extra = bare(out, soon, TripStatus.PENDING_APPROVAL, 22, true);
        extra.setBus(bus);
        extra = tripRepository.save(extra);
        extra.setForecastLoopId(extra.getId());
        tripRepository.save(extra);
        Trip companion = bare(back, soon.plusDays(1).withHour(6), TripStatus.PENDING_APPROVAL, 22, true);
        companion.setBus(bus);
        companion.setForecastLoopId(extra.getId());
        tripRepository.save(companion);
        flushAndClear();

        ForecastLoopService.Result result = forecastLoopService.reconcile(LocalDateTime.now());

        assertEquals(1, result.withdrawnByDeadline().size(), String.join("\n", result.withdrawnByDeadline()));
        assertTrue(tripRepository.findByForecastLoopIdOrderByDepartureTimeAsc(extra.getId()).isEmpty(),
                "cả hai lượt được rút (xoá mềm)");
    }

    /**
     * Thẻ đủ điều kiện nhưng LỖ (khung 06:00 còn 30 khách: chuyến thêm chở ~8 khách = 0,8 triệu < 1,4 triệu chi
     * phí hai lượt) vẫn hiện trên màn Đề xuất, nhưng không được tự tạo (QĐ-24 mục 2).
     */
    @Test
    void lossMakingCards_stayOnThePage_butAreNeverCreated() {
        em.createQuery("UPDATE Trip t SET t.ticketsSold = 30 WHERE t.route.id = :route AND t.status = :done")
                .setParameter("route", out.getId()).setParameter("done", TripStatus.COMPLETED).executeUpdate();
        flushAndClear();
        List<giang.com.BusManagement.dto.RecommendationCardDto> cards = recommendationService.buildRecommendations()
                .getCards();
        assertTrue(cards.stream().anyMatch(c -> c.getStatus() == giang.com.BusManagement.dto.RecommendationStatus.RECOMMENDED
                && c.getEstimatedProfit().signum() < 0), "tiền đề: có thẻ đủ điều kiện mà lỗ");

        ForecastLoopService.Result result = forecastLoopService.reconcile(LocalDateTime.now());

        assertNull(result.skipped());
        assertTrue(result.created().isEmpty(), String.join("\n", result.created()));
        assertTrue(pendingLoops().isEmpty());
    }

    /** Lịch sử cũ hơn 7 ngày: không tự tạo vòng nào (CM-10), và nói rõ lý do. */
    @Test
    void staleHistory_createsNothing_andSaysWhy() {
        em.createQuery("DELETE FROM Trip t WHERE t.status = :done AND t.departureTime > :cut")
                .setParameter("done", TripStatus.COMPLETED)
                .setParameter("cut", LocalDate.now().minusDays(ForecastLoopService.MAX_STALE_DAYS + 2).atStartOfDay())
                .executeUpdate();
        flushAndClear();

        ForecastLoopService.Result result = forecastLoopService.reconcile(LocalDateTime.now());

        assertNotNull(result.skipped());
        assertTrue(result.skipped().contains("cũ"), result.skipped());
        assertTrue(result.created().isEmpty());
        assertTrue(pendingLoops().isEmpty());
    }

    /** CM-27: xác nhận một lượt là kích hoạt cả vòng; đường duyệt tay (đổi xe) không nhận lượt của vòng. */
    @Test
    void confirmingOneLeg_activatesTheWholeLoop_andTheManualApprovalPathRefusesALoopLeg() {
        forecastLoopService.reconcile(LocalDateTime.now());
        flushAndClear();
        List<Trip> legs = pendingLoops().values().iterator().next();
        Trip companion = legs.get(1);

        assertThrows(IllegalStateException.class, () -> tripService.approveTrip(companion.getId(),
                companion.getBus().getId(), companion.getDriver().getUserId(), null, null));

        tripService.confirmAutoAssignedTrip(companion.getId());
        flushAndClear();

        for (Trip leg : tripRepository.findByForecastLoopIdOrderByDepartureTimeAsc(companion.getForecastLoopId())) {
            assertEquals(TripStatus.ACTIVE, leg.getStatus(), "cả vòng được kích hoạt");
            assertNotNull(leg.getSaleOpenedAt(), "mở bán");
        }
        assertEquals(2, tripService.forecastLoopLegIds(companion.getId()).size());
    }

    /** CM-27: từ chối một lượt là hủy cả vòng — nửa vòng không chạy được. */
    @Test
    void rejectingOneLeg_cancelsTheWholeLoop() {
        forecastLoopService.reconcile(LocalDateTime.now());
        flushAndClear();
        Map.Entry<Long, List<Trip>> loop = pendingLoops().entrySet().iterator().next();

        tripService.rejectTrip(loop.getKey());
        flushAndClear();

        for (Trip leg : tripRepository.findByForecastLoopIdOrderByDepartureTimeAsc(loop.getKey())) {
            assertEquals(TripStatus.CANCELLED, leg.getStatus());
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Số ngày d ∈ 1…7 mà chuyến thêm 06:00 còn ≥ 72 giờ lúc now. */
    private static int expectedLoops(LocalDateTime now) {
        int count = 0;
        for (int d = 1; d <= 7; d++) {
            if (!LocalDate.now().plusDays(d).atTime(6, 0).isBefore(now.plusHours(72))) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, List<Trip>> pendingLoops() {
        return tripRepository.findForecastLoopLegsByStatus(TripStatus.PENDING_APPROVAL).stream()
                .collect(Collectors.groupingBy(Trip::getForecastLoopId, java.util.TreeMap::new, Collectors.toList()));
    }

    /** Mỗi giờ của bản đồ: một chuyến COMPLETED mỗi ngày, 60 ngày kết thúc hôm qua, 40 ghế (không bán hết). */
    private void history(Route route, Map<Integer, Integer> soldByHour) {
        for (Map.Entry<Integer, Integer> entry : soldByHour.entrySet()) {
            for (int d = 60; d >= 1; d--) {
                Trip t = bare(route, LocalDate.now().minusDays(d).atTime(entry.getKey(), 0), TripStatus.COMPLETED, 40,
                        false);
                t.setTicketsSold(entry.getValue());
                tripRepository.save(t);
            }
        }
    }

    private Trip bare(Route route, LocalDateTime departure, TripStatus status, int seats, boolean extra) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        t.setStatus(status);
        t.setTotalSeats(seats);
        t.setPrice(PRICE);
        t.setExtraTrip(extra);
        return t;
    }

    private Station station(String name) {
        Station s = new Station();
        s.setStationName(name + " " + System.nanoTime());
        return stationRepository.save(s);
    }

    private Route route(Station from, Station to, HolidayFlow flow) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setSuitableBusType(type);
        r.setHolidayFlow(flow);
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

    private void bus(String plate) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand("TestBrand");
        b.setBusType(type);
        b.setOdometer(1000.0);
        b.setLastMaintenanceOdometer(1000.0);
        b.setMaintenanceThreshold(100000.0);
        b.setStatus(BusStatus.READY);
        b.setHomeStation(home);
        busRepository.save(b);
    }

    private void driver(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFullName("Driver " + username);
        user.setPassword("x");
        user.setRole(Role.ROLE_DRIVER);
        Driver d = new Driver();
        d.setUser(user);
        d.setLicenseNumber("LIC-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        d.setHomeStation(home);
        user.setDriver(d);
        userRepository.save(user);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
