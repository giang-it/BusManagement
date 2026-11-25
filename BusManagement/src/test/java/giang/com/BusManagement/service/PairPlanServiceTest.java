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
import giang.com.BusManagement.dto.ForecastPairGroupDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.PairPlanViewDto;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT7 — trang "Kế hoạch cặp tuyến" (PairPlanService): chỉ xếp lại chuyến đã có và kết quả
 * của màn Đề xuất theo cặp, theo xe, theo giờ; và bảng 7 ngày của màn Dự báo gom theo cặp.
 *
 * <p>Cặp kiểu Vũng Tàu như RecommendationLoopTest: "Bến nhà KH" (bến nhà của xe) ↔ "Bến xa KH", 100 km,
 * 2 giờ, xe 22 chỗ. Chiều đi 06:00 nóng (30 khách), mọi khung khác 10 khách.
 */
@SpringBootTest
@Transactional
class PairPlanServiceTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private PairPlanService service;
    @Autowired private ForecastService forecastService;
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
    private List<Long> configuredBefore;

    @BeforeEach
    void setUp() {
        home = station("Bến nhà KH");
        far = station("Bến xa KH");
        type = new BusType();
        type.setTypeName("KH 22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
        // Tạo chiều VỀ trước, để id của nó nhỏ hơn: chiều "đi" phải theo bến nhà, không theo id.
        back = route(far, home, HolidayFlow.TOWARD_HUB);
        out = route(home, far, HolidayFlow.LEAVING_HUB);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        configuredBefore = target().routeIds;
        target().routeIds = List.of(back.getId(), out.getId());
    }

    @AfterEach
    void restoreConfiguration() {
        target().routeIds = configuredBefore;
    }

    private PairPlanService target() {
        return org.springframework.test.util.AopTestUtils.getTargetObject(service);
    }

    /**
     * Ngày mai: chuyến thường lệ 12:00 chiều về đã có (xe B); màn Đề xuất thêm ★ 06:00 chiều đi bằng xe A,
     * về ngay 12:00. Bảng có hàng xe B (thường lệ) và hàng ★ xe A (chuyến thêm + lượt về, tối ngủ ở bến
     * nhà); tóm tắt: 2 xe, 1 chuyến thêm, 100 km lượt đi kèm, lãi cả vòng của thẻ.
     */
    @Test
    void aDayOfThePair_showsEachBusOnARow_andTheStarredLoop() {
        bus("KH-A", home, 100);
        Bus b = bus("KH-B", far, 200);
        driver("kh-1", home);
        driver("kh-2", far);
        Trip regular = bare(back, TOMORROW.atTime(12, 0), TripStatus.ACTIVE);
        regular.setBus(b);
        tripRepository.save(regular);
        em.flush();
        em.clear();

        PairPlanViewDto view = service.build(TOMORROW);

        assertEquals(TOMORROW, view.getSelectedDate());
        assertTrue(view.getProblems().isEmpty(), view.getProblems().toString());
        assertEquals(1, view.getPairs().size());
        PairPlanViewDto.PairDay day = view.getPairs().get(0);
        assertTrue(day.getOutLabel().startsWith("Bến nhà KH"), "chiều đi rời bến nhà: " + day.getOutLabel());
        assertTrue(day.isScheduled());

        PairPlanViewDto.SlotInfo out6 = day.getSlots().get(0).getOut();
        assertTrue(out6.isHot());
        assertEquals(2, out6.getTripsNeeded());
        assertEquals(1, out6.getExtras());
        PairPlanViewDto.SlotInfo back12 = day.getSlots().get(1).getBack();
        assertEquals(1, back12.getTrips(), "chuyến thường lệ đã có");
        assertEquals(22, back12.getSeats());

        assertEquals(2, day.getRows().size());
        PairPlanViewDto.BusRow regularRow = day.getRows().get(0);
        assertEquals("KH-B", regularRow.getBusPlate());
        assertFalse(regularRow.isExtra());
        assertEquals("REGULAR", regularRow.getCells().get(1).getLegs().get(0).getKind());

        PairPlanViewDto.BusRow starRow = day.getRows().get(1);
        assertEquals("KH-A", starRow.getBusPlate());
        assertTrue(starRow.isExtra());
        assertEquals("STAR", starRow.getCells().get(0).getLegs().get(0).getKind());
        assertTrue(starRow.getCells().get(0).getLegs().get(0).isOutbound());
        assertEquals("RETURN", starRow.getCells().get(1).getLegs().get(0).getKind());
        assertTrue(starRow.getSleepsAt().startsWith("Bến nhà KH"), starRow.getSleepsAt());
        assertFalse(starRow.getNotes().isEmpty());

        assertEquals(2, day.getBusesUsed());
        assertEquals(1, day.getExtras());
        assertEquals(100.0, day.getCompanionKm(), 1e-9);
        assertTrue(day.getLoopProfit() != null);
        assertTrue(day.getUnserved().isEmpty());
    }

    /** Tổng quan: một ★ mỗi ngày của chân trời; ngày ngoài chân trời thì trang về ngày đầu. */
    @Test
    void theOverviewCountsTheStarsPerDay_andAnOutOfRangeDateFallsBackToTheFirstDay() {
        bus("KH-A", home, 100);
        driver("kh-1", home);
        em.flush();
        em.clear();

        PairPlanViewDto view = service.build(LocalDate.now().minusDays(3));

        assertEquals(view.getForecastFrom(), view.getSelectedDate());
        assertEquals(7, view.getDays().size());
        PairPlanViewDto.PairWeek week = view.getOverview().get(0);
        for (PairPlanViewDto.DayCount count : week.getDays()) {
            assertEquals(1, count.getExtras(), count.getDate().toString());
            assertEquals(0, count.getUnserved());
        }
        assertFalse(view.getPairs().get(0).isScheduled(), "chưa tạo lịch");
    }

    /**
     * Không có xe nào: mọi chuyến thêm "thiếu tài nguyên", nằm ở phần "chưa xếp được" của bảng, không thành
     * hàng ★.
     */
    @Test
    void extraTripsWithoutResources_areListedAsUnserved_notAsRows() {
        em.flush();
        em.clear();

        PairPlanViewDto.PairDay day = service.build(TOMORROW).getPairs().get(0);

        assertTrue(day.getRows().isEmpty());
        assertEquals(1, day.getUnserved().size());
        assertEquals(0, day.getExtras());
        assertEquals(0, day.getBusesUsed());
    }

    /**
     * Màn Dự báo: hai chiều của cặp liền nhau, chiều rời bến nhà trước dù id lớn hơn, mỗi chiều theo giờ;
     * tuyến không có chiều ngược ở nhóm cuối.
     */
    @Test
    void theForecastTable_groupsBothDirectionsOfAPair_outboundFirst() {
        bus("KH-A", home, 100); // bến nhà của đội xe quyết định chiều "đi"
        Station lone = station("Bến lẻ KH");
        Route single = route(home, lone, null);
        history(single, Map.of(6, 10));
        em.flush();
        em.clear();

        List<ForecastSeriesDto> series = forecastService.buildForecast().getSeries();
        List<ForecastPairGroupDto> groups = service.forecastByPair(series);

        ForecastPairGroupDto pair = groups.stream().filter(ForecastPairGroupDto::isPair).findFirst().orElseThrow();
        assertEquals(6, pair.getSeries().size());
        List<Long> routes = pair.getSeries().stream().map(ForecastSeriesDto::getRouteId).toList();
        assertEquals(List.of(out.getId(), out.getId(), out.getId(), back.getId(), back.getId(), back.getId()), routes);
        assertEquals(List.of(6, 12, 18, 6, 12, 18),
                pair.getSeries().stream().map(ForecastSeriesDto::getDepartureHour).toList());
        ForecastPairGroupDto last = groups.get(groups.size() - 1);
        assertFalse(last.isPair());
        assertEquals(single.getId(), last.getSeries().get(0).getRouteId());
        assertEquals(series.size(), groups.stream().mapToInt(g -> g.getSeries().size()).sum(), "không mất chuỗi nào");
    }

    /**
     * Route pairs CT9 (CM-29): khung đi 06:00 và khung về 18:00 cùng nóng (35 khách) — màn Đề xuất ghép thành
     * một vòng giữ xe: xe A chạy ★ 06:00, chờ ở bến xa từ 08:00, rồi chạy ★ 18:00 về. Trang đếm hai chuyến thêm
     * trong ngày, mỗi khung một; không có km lượt đi kèm; và ghi xe đang được giữ kèm phép so tiền.
     */
    @Test
    void aHeldLoop_showsBothExtras_andTheBusBeingHeld() {
        bus("KH-A", home, 100);
        driver("kh-1", home);
        hot(out, 6);
        hot(back, 18);

        PairPlanViewDto.PairDay day = service.build(TOMORROW).getPairs().get(0);

        assertEquals(1, day.getSlots().get(0).getOut().getExtras(), "★ 06:00 chiều đi");
        assertEquals(1, day.getSlots().get(2).getBack().getExtras(), "★ 18:00 chiều về của vòng giữ");
        assertEquals(2, day.getExtras());
        assertEquals(0.0, day.getCompanionKm(), 1e-9, "vòng giữ không có lượt đi kèm rỗng");
        PairPlanViewDto.BusRow row = day.getRows().stream().filter(r -> "KH-A".equals(r.getBusPlate())).findFirst()
                .orElseThrow();
        assertEquals("STAR", row.getCells().get(0).getLegs().get(0).getKind());
        assertEquals("STAR", row.getCells().get(2).getLegs().get(0).getKind());
        assertTrue(row.getSleepsAt().startsWith("Bến nhà KH"), row.getSleepsAt());
        assertEquals(1, day.getHolds().size(), day.getHolds().toString());
        assertTrue(day.getHolds().get(0).contains("KH-A") && day.getHolds().get(0).contains("Bến xa KH")
                && day.getHolds().get(0).contains("hai vòng riêng"), day.getHolds().get(0));
    }

    /**
     * Vòng giữ xe ĐÃ LƯU (job đêm tạo, chờ duyệt): xe đi 18:00 ngày mai, về 12:00 ngày kia+1 — ngày ở giữa xe không
     * chạy lượt nào, nhưng trang vẫn ghi xe đang được giữ ở bến xa.
     */
    @Test
    void aSavedHeldLoop_isListed_onTheDayTheBusDoesNotMove() {
        Bus a = bus("KH-A", home, 100);
        Trip go = bare(out, TOMORROW.atTime(18, 0), TripStatus.PENDING_APPROVAL);
        go.setBus(a);
        go.setExtraTrip(true);
        go.setForecastLoopHold(true);
        Trip saved = tripRepository.save(go);
        saved.setForecastLoopId(saved.getId());
        tripRepository.save(saved);
        Trip homeLeg = bare(back, TOMORROW.plusDays(2).atTime(12, 0), TripStatus.PENDING_APPROVAL);
        homeLeg.setBus(a);
        homeLeg.setExtraTrip(true);
        homeLeg.setForecastLoopHold(true);
        homeLeg.setForecastLoopId(saved.getId());
        tripRepository.save(homeLeg);
        em.flush();
        em.clear();

        PairPlanViewDto.PairDay middle = service.build(TOMORROW.plusDays(1)).getPairs().get(0);

        String loopTag = "#" + saved.getId();
        assertTrue(middle.getHolds().stream().anyMatch(h -> h.contains("KH-A") && h.contains(loopTag)
                && h.contains("chờ duyệt")), middle.getHolds().toString());
    }

    /** Lịch sử của khung (tuyến, giờ) bán 35/40 vé — khung nóng, chuyến thêm chở ~13 khách tràn. */
    private void hot(Route route, int hour) {
        em.flush();
        em.createNativeQuery("UPDATE trips SET tickets_sold = 35 WHERE route_id = :route AND status = 'COMPLETED'"
                + " AND HOUR(departure_time) = :hour").setParameter("route", route.getId()).setParameter("hour", hour)
                .executeUpdate();
        em.clear();
    }

    // =====================================================================

    private void history(Route route, Map<Integer, Integer> soldByHour) {
        for (Map.Entry<Integer, Integer> entry : soldByHour.entrySet()) {
            for (int d = 60; d >= 1; d--) {
                Trip t = bare(route, LocalDate.now().minusDays(d).atTime(entry.getKey(), 0), TripStatus.COMPLETED);
                t.setTotalSeats(40);
                t.setTicketsSold(entry.getValue());
                tripRepository.save(t);
            }
        }
    }

    private Trip bare(Route route, LocalDateTime departure, TripStatus status) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        t.setStatus(status);
        t.setTotalSeats(22);
        t.setPrice(new BigDecimal("100000"));
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

    /** Xe có bến nhà ở "Bến nhà KH"; start là bến nó đang đứng (khác bến nhà thì có một chuyến hôm qua đưa nó tới). */
    private Bus bus(String plate, Station start, double kmSinceMaintenance) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand("TestBrand");
        b.setBusType(type);
        b.setLastMaintenanceOdometer(1000.0);
        b.setOdometer(1000.0 + kmSinceMaintenance);
        b.setMaintenanceThreshold(100000.0);
        b.setStatus(BusStatus.READY);
        b.setHomeStation(home);
        Bus saved = busRepository.save(b);
        if (start != home) {
            Trip up = bare(out, LocalDate.now().minusDays(1).atTime(15, 0), TripStatus.COMPLETED);
            up.setBus(saved);
            tripRepository.save(up);
        }
        return saved;
    }

    private Driver driver(String username, Station start) {
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
        d.setHomeStation(start);
        user.setDriver(d);
        userRepository.save(user);
        return d;
    }
}
