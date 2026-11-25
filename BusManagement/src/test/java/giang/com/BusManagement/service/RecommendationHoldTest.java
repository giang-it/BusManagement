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
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.LoopLegKind;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.dto.WhatIfOutcomeDto;
import giang.com.BusManagement.dto.WhatIfScenarioDto;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT9 (QĐ-25, QĐ-26; CM-8, CM-29…CM-32) — vòng giữ xe trên màn Đề xuất. Chủ dự án duyệt 2026-11-25.
 *
 * <p>Bối cảnh như RecommendationLoopTest: "Bến nhà HL" (bến nhà của mọi xe, người) ↔ "Bến xa HL", 100 km, 2 giờ,
 * xe chuẩn 22 chỗ, giá 100.000đ, một lượt 700.000đ; tiền chờ mặc định 663.000đ/ngày (một tài xế). Lịch sử 60
 * ngày, nhu cầu không đổi: khung ĐI 18:00 và khung VỀ 12:00 cùng 35 khách (nóng, mỗi khung cần 1 chuyến thêm,
 * chở ~13 khách tràn), mọi khung khác 10. Riêng từng thẻ thì lỗ — lượt về ngay 06:00 và lượt đưa xe tới 06:00
 * gần như rỗng — còn ghép chuyến thêm đi 18:00 hôm d với chuyến thêm về 12:00 hôm d+1 thì xe chỉ chờ thêm 6
 * giờ (06:00 → 12:00, 165.750đ) và cả vòng có lãi. @Transactional nên rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class RecommendationHoldTest {

    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final BigDecimal TWO_LEGS = new BigDecimal("1400000");
    private static final BigDecimal SIX_HOURS_WAIT = new BigDecimal("165750");
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private RecommendationService recommendationService;
    @Autowired private TripService tripService;
    @Autowired private WhatIfSimulationService whatIfSimulationService;
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

    @BeforeEach
    void setUp() {
        home = station("Bến nhà HL");
        far = station("Bến xa HL");
        type = new BusType();
        type.setTypeName("HL 22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
        out = route(home, far, HolidayFlow.LEAVING_HUB);
        back = route(far, home, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 10, 12, 10, 18, 35));
        history(back, Map.of(6, 10, 12, 35, 18, 10));
    }

    /**
     * Mỗi chuyến thêm đi 18:00 hôm d ghép với chuyến thêm về 12:00 hôm d+1 (6 đôi trong 7 ngày): MỘT thẻ, MỘT xe
     * và MỘT tài xế cho cả vòng, chờ ở bến xa thêm 6 giờ từ lần về ngay sớm nhất 06:00; chuyến về chở khách tràn
     * của khung nó như một chuyến thêm. Chuyến về 12:00 ngày đầu và chuyến đi 18:00 ngày cuối không có đôi nên vẫn
     * là thẻ riêng. Kế hoạch mang đúng vòng đó cho job đêm.
     */
    @Test
    void anOutboundAndALaterInboundExtra_becomeOneHeldLoop_onOneBusAndOneDriver() {
        bus("HL-A", home);
        driver("hl-1", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();
        DemandForecastViewDto forecast = forecastService.buildForecast();
        double size = forecast.getModel().getNegativeBinomialSize();

        assertEquals(14, view.getTotalCandidates(), "7 chuyến thêm đi + 7 chuyến thêm về");
        assertEquals(6, view.getHeldLoopCount());
        assertEquals(8, view.getCards().size(), "6 vòng giữ + 2 thẻ riêng");
        assertEquals(8, view.getRecommendedCount());
        List<RecommendationCardDto> held = view.getCards().stream()
                .filter(c -> c.getCompanion().getKind() == LoopLegKind.HOLD).toList();
        for (RecommendationCardDto card : held) {
            RecommendationCardDto.CompanionLeg leg = card.getCompanion();
            assertEquals(out.getId(), card.getRouteId());
            assertEquals(18, card.getDepartureHour());
            assertEquals(back.getId(), leg.getRouteId());
            assertEquals(card.getDate().plusDays(1).atTime(12, 0), leg.getDeparture(), "chuyến thêm về hôm sau");
            assertEquals(card.getDate().plusDays(1).atTime(6, 0), leg.getWaitFrom(), "lần về ngay sớm nhất");
            assertEquals(6.0, leg.getWaitHours(), 1e-9);
            assertEquals(0, SIX_HOURS_WAIT.compareTo(leg.getWaitCost()));
            assertTrue(leg.isReserved());
            assertEquals("HL-A", card.getBusPlate());
            assertEquals("HL-A", leg.getBusPlate(), "cùng một xe cho cả vòng giữ");
            assertEquals(card.getDriverName(), leg.getDriverName(), "cùng một tài xế");
            assertEquals(1, leg.getServedExtraIndex());
            assertEquals(1, leg.getServedExtraCount());

            assertEquals(ForecastService.extraTripPassengers(demandOf(forecast, out, 18, card.getDate()), 22, 22, size),
                    card.getExpectedPassengers(), 1e-9);
            assertEquals(ForecastService.extraTripPassengers(demandOf(forecast, back, 12, card.getDate().plusDays(1)),
                    22, 22, size), leg.getPassengers(), 1e-9, "chở khách tràn của khung về như một chuyến thêm");
            assertEquals(0, TWO_LEGS.add(SIX_HOURS_WAIT).compareTo(card.getEstimatedCost()));
            assertEquals(card.getTripRevenue().add(leg.getRevenue()), card.getEstimatedRevenue());
            assertTrue(card.getEstimatedProfit().signum() > 0, "vòng giữ có lãi");
            assertEquals(0, BigDecimal.ZERO.compareTo(leg.getSeparateProfit()), "hai vòng riêng đều lỗ");
        }

        RecommendationService.Plan plan = recommendationService.buildPlan();
        List<RecommendationService.Loop> heldLoops = plan.loops().values().stream()
                .filter(loop -> loop.kind() == LoopLegKind.HOLD).toList();
        assertEquals(6, heldLoops.size());
        for (RecommendationService.Loop loop : heldLoops) {
            assertEquals(2, loop.legs().size());
            assertEquals(out.getId(), loop.legs().get(0).getRoute().getId());
            assertEquals(back.getId(), loop.legs().get(1).getRoute().getId());
            assertEquals(loop.companion(), loop.legs().get(1));
            assertEquals(loop.legs().get(0).getDepartureTime().toLocalDate().plusDays(1).atTime(12, 0),
                    loop.legs().get(1).getDepartureTime());
        }
    }

    /**
     * Không xếp được vòng giữ bằng một xe + một tài xế (ở đây: có xe ở bến nhà mà không có tài xế nào) thì hai
     * chuyến thêm quay về hai thẻ riêng như trước CT9 — không mất chuyến thêm nào, và không thẻ nào là vòng giữ.
     * Phải có xe ở bến nhà: bến nhà của đội xe quyết định chuyến thêm nào là "đi", chuyến nào là "về", nên không có
     * xe nào thì không có đôi nào để ghép, và nhánh quay về hai thẻ không được thử tới.
     */
    @Test
    void whenNoBusCanRunTheHeldLoop_theTwoExtrasFallBackToTwoSeparateCards() {
        bus("HL-A", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();

        assertEquals(14, view.getTotalCandidates());
        assertEquals(0, view.getHeldLoopCount());
        assertEquals(14, view.getCards().size(), "mỗi chuyến thêm một thẻ");
        assertTrue(view.getCards().stream().allMatch(c -> c.getStatus() == RecommendationStatus.NO_RESOURCE));
        assertTrue(view.getCards().stream().noneMatch(c -> c.getCompanion().getKind() == LoopLegKind.HOLD));
    }

    /**
     * What-if ghép đúng như màn Đề xuất (cùng LoopEconomics.pairHolds): ở 100% nhu cầu, khi mọi thẻ chạy xe
     * chuẩn và đội xe phủ hết, tiền của What-if bằng tổng tiền các thẻ; "chỉ chạy chuyến có lãi" đếm theo chuyến
     * thêm, nên một vòng giữ có lãi góp hai chuyến.
     */
    @Test
    void whatIf_pairsTheSameHolds_andCountsTheSameMoney_asTheCards() {
        bus("HL-A", home);
        driver("hl-1", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();
        WhatIfOutcomeDto baseline = whatIfSimulationService.simulate(new WhatIfScenarioDto()).getBaseline();

        assertEquals(view.getTotalCandidates(), baseline.getExtraTripsNeeded());
        assertEquals(baseline.getExtraTripsNeeded(), baseline.getCoverable());
        BigDecimal revenue = view.getCards().stream().map(RecommendationCardDto::getEstimatedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = view.getCards().stream().map(RecommendationCardDto::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, revenue.compareTo(baseline.getEstimatedRevenue()), revenue + " / " + baseline.getEstimatedRevenue());
        assertEquals(0, cost.compareTo(baseline.getEstimatedCost()), cost + " / " + baseline.getEstimatedCost());
        int profitableExtras = view.getCards().stream()
                .filter(c -> c.getEstimatedProfit() != null && c.getEstimatedProfit().signum() > 0)
                .mapToInt(c -> c.getCompanion().getKind() == LoopLegKind.HOLD ? 2 : 1).sum();
        assertEquals(profitableExtras, baseline.getProfitableTrips());
    }

    /**
     * QĐ-26, P6 — kíp lái ở lại cùng xe mà không cần luật khoá mới: khi vòng giữ đã lưu (chuyến đi 18:00 hôm nay
     * + chuyến về 12:00 hôm sau, cùng xe và tài xế), bộ chọn của máy đi DB như job 10 giây không giao được xe hay
     * tài xế đó cho một chuyến trong lúc chờ — luật vị trí hai phía (CT5b): chuyến chèn vào phải kết thúc ở bến
     * xa, nơi lượt kế tiếp của họ bắt đầu. Đối chứng: khi chưa có chuyến về, chính xe và tài xế đó được chọn.
     */
    @Test
    void theHeldBusAndCrew_cannotBeGivenAnotherTripWhileTheyWait() {
        Bus bus = bus("HL-A", home);
        Driver driver = driver("hl-1", home);
        Trip go = bare(out, TOMORROW.atTime(18, 0), TripStatus.PENDING_APPROVAL, 22);
        go.setExtraTrip(true);
        go.setBus(bus);
        go.setDriver(driver);
        tripRepository.save(go);
        flushAndClear();

        // Tuyến nạp kèm bến, như app nạp (findAllWithStations) — tuyến không biết bến thì luật vị trí không xét.
        Route backWithStops = routeRepository.findAllWithStations().stream()
                .filter(r -> r.getId().equals(back.getId())).findFirst().orElseThrow();
        LocalDateTime gapStart = TOMORROW.plusDays(1).atTime(6, 0);
        Trip inTheGap = bare(backWithStops, gapStart, TripStatus.PENDING_APPROVAL, 22);
        assertEquals("HL-A", chooseBus(inTheGap), "đối chứng: xe đang ở bến xa, chưa có lượt nào sau đó");
        assertEquals("hl-1", chooseDriver(inTheGap));

        Trip home = bare(back, TOMORROW.plusDays(1).atTime(12, 0), TripStatus.PENDING_APPROVAL, 22);
        home.setExtraTrip(true);
        home.setBus(busRepository.findById(bus.getId()).orElseThrow());
        home.setDriver(em.find(Driver.class, driver.getUserId()));
        tripRepository.save(home);
        flushAndClear();

        assertNull(chooseBus(inTheGap), "xe phải ở bến xa lúc 12:00 cho chuyến về của vòng giữ");
        assertNull(chooseDriver(inTheGap), "kíp ở lại cùng xe");
    }

    // =====================================================================

    private String chooseBus(Trip trip) {
        Bus bus = tripService.selectBestAvailableBus(trip, trip.getDepartureTime(), trip.getArrivalTimeExpected(), null);
        return bus != null ? bus.getLicensePlate() : null;
    }

    private String chooseDriver(Trip trip) {
        Driver driver = tripService.selectBestAvailableDriver(trip.getDepartureTime(), trip.getArrivalTimeExpected(),
                2.0, 1, List.of(), false, null, trip.getRoute());
        return driver != null ? driver.getUser().getUsername() : null;
    }

    private double demandOf(DemandForecastViewDto forecast, Route route, int hour, LocalDate date) {
        return forecast.getSeries().stream()
                .filter(s -> s.getRouteId().equals(route.getId()) && s.getDepartureHour() == hour)
                .flatMap(s -> s.getPreview().stream())
                .filter(p -> p.getDate().equals(date))
                .findFirst().orElseThrow().getPredictedDemand();
    }

    /** Mỗi giờ của bản đồ: một chuyến COMPLETED mỗi ngày, 60 ngày kết thúc hôm qua, 40 ghế (không bán hết). */
    private void history(Route route, Map<Integer, Integer> soldByHour) {
        for (Map.Entry<Integer, Integer> entry : soldByHour.entrySet()) {
            for (int d = 60; d >= 1; d--) {
                Trip t = bare(route, LocalDate.now().minusDays(d).atTime(entry.getKey(), 0), TripStatus.COMPLETED, 40);
                t.setTicketsSold(entry.getValue());
                tripRepository.save(t);
            }
        }
    }

    private Trip bare(Route route, LocalDateTime departure, TripStatus status, int seats) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusMinutes(route.getEstimatedDuration()));
        t.setStatus(status);
        t.setTotalSeats(seats);
        t.setPrice(PRICE);
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

    private Bus bus(String plate, Station homeStation) {
        Bus b = new Bus();
        b.setLicensePlate(plate);
        b.setBrand("TestBrand");
        b.setBusType(type);
        b.setOdometer(1000.0);
        b.setLastMaintenanceOdometer(1000.0);
        b.setMaintenanceThreshold(100000.0);
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
        d.setLicenseNumber("LIC-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        d.setHomeStation(homeStation);
        user.setDriver(d);
        userRepository.save(user);
        return d;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
