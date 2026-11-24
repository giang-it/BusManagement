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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT7 — màn Đề xuất tính lãi CẢ VÒNG (QĐ-22, PA-B) và chỉ đề xuất phần khung còn thiếu
 * (cách 2a). Chủ dự án duyệt 2026-11-22.
 *
 * <p>Bối cảnh: một cặp tuyến kiểu Vũng Tàu — "Bến nhà VL" (bến nhà của mọi xe, người) ↔ "Bến xa VL",
 * 100 km, 2 giờ, xe chuẩn 22 chỗ, giá vé 100.000đ. Một lượt tốn 100 × 6.000 + 2 × 50.000 = 700.000đ
 * (tham số mặc định). Lịch sử 60 ngày kết thúc hôm qua, mỗi ngày đủ 3 khung 06/12/18 cả hai chiều,
 * không xe, không người (không đổi vị trí ai). Nhu cầu không đổi nên dự báo bằng số vé; 10 khách là
 * khung vắng, 30 khách trên xe 22 chỗ là khung nóng cần 2 chuyến (QĐ-18). Chưa tạo lịch thường lệ, nên
 * mọi khung "chưa có lịch — giả định 1 xe chuẩn". @Transactional nên rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class RecommendationLoopTest {

    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final BigDecimal ONE_LEG = new BigDecimal("700000");
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

    @BeforeEach
    void setUp() {
        home = station("Bến nhà VL");
        far = station("Bến xa VL");
        type = new BusType();
        type.setTypeName("VL 22 " + System.nanoTime());
        type.setCapacity(22);
        type = busTypeRepository.save(type);
    }

    // =====================================================================
    // Về ngay (RETURN)
    // =====================================================================

    /**
     * Khung đi 06:00 nóng (30 khách), mọi khung khác vắng. Một xe dự phòng ở bến nhà: chuyến thêm 06:00
     * (tới 08:00), rồi xe về bằng khung chiều ngược SỚM NHẤT còn kịp — 12:00 (08:00 + 1 giờ chuẩn bị =
     * 09:00 > 06:00). Cả hai lượt chọn được đúng xe đó và được giữ chỗ, nên ngày nào xe cũng về kịp cho
     * vòng hôm sau. Lãi = tiền vé chuyến thêm + lượt về − 2 × 700.000đ.
     */
    @Test
    void anExtraTripLeavingHome_comesStraightBack_atTheEarliestReverseSlot_andPaysForBothLegs() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        driver("vl-1", home);
        driver("vl-2", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();
        DemandForecastViewDto forecast = forecastService.buildForecast();
        double size = forecast.getModel().getNegativeBinomialSize();

        assertEquals(7, view.getTotalCandidates(), "06:00 cần 1 chuyến thêm × 7 ngày");
        assertEquals(7, view.getRecommendedCount());
        for (RecommendationCardDto card : view.getCards()) {
            assertEquals(out.getId(), card.getRouteId());
            assertTrue(card.isExistingAssumed(), "chưa tạo lịch thường lệ");
            assertEquals(1, card.getExistingTrips());
            assertEquals(22, card.getExistingSeats());

            RecommendationCardDto.CompanionLeg leg = card.getCompanion();
            assertEquals(LoopLegKind.RETURN, leg.getKind(), card.getDate().toString());
            assertEquals(card.getDate().atTime(12, 0), leg.getDeparture(), "khung chiều ngược sớm nhất còn kịp");
            assertTrue(leg.isReserved());
            assertEquals("VL-A", leg.getBusPlate(), "chính xe của chuyến thêm quay về");
            assertEquals("VL-A", card.getBusPlate());

            double backDemand = demandOf(forecast, back, 12, card.getDate());
            assertEquals(ForecastService.extraTripPassengers(backDemand, 22, 22, size), leg.getPassengers(), 1e-9,
                    "lượt về chở khách còn tràn của khung 12:00 sau xe chuẩn");
            assertEquals(0, ONE_LEG.compareTo(card.getTripCost()));
            assertEquals(0, ONE_LEG.multiply(BigDecimal.valueOf(2)).compareTo(card.getEstimatedCost()));
            assertEquals(card.getTripRevenue().add(leg.getRevenue()), card.getEstimatedRevenue());
            assertEquals(card.getEstimatedRevenue().subtract(card.getEstimatedCost()), card.getEstimatedProfit());
        }
    }

    /**
     * What-if (mô hình đếm, không chọn xe) tính lượt đi kèm THEO KẾ HOẠCH — xe dự phòng ở bến nhà, về ngay
     * bằng khung chiều ngược sớm nhất. Ở đây xe thật đi đúng kế hoạch, nên ở 100% nhu cầu tiền của What-if
     * bằng đúng tổng các thẻ (W1: một định nghĩa lãi).
     */
    @Test
    void whatIf_countsTheSameLoopMoney_asTheCards_whenTheBusesFollowThePlan() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        driver("vl-1", home);
        driver("vl-2", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();
        WhatIfOutcomeDto baseline = whatIfSimulationService.simulate(new WhatIfScenarioDto()).getBaseline();

        assertEquals(view.getTotalCandidates(), baseline.getExtraTripsNeeded());
        assertEquals(baseline.getExtraTripsNeeded(), baseline.getCoverable(), "đủ năng lực, mọi chuyến thêm được phủ");
        BigDecimal revenue = view.getCards().stream().map(RecommendationCardDto::getEstimatedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = view.getCards().stream().map(RecommendationCardDto::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, revenue.compareTo(baseline.getEstimatedRevenue()), revenue + " / " + baseline.getEstimatedRevenue());
        assertEquals(0, cost.compareTo(baseline.getEstimatedCost()), cost + " / " + baseline.getEstimatedCost());
        assertEquals(view.getProfitableCount(), baseline.getProfitableTrips());
    }

    /**
     * Tuyến 6 giờ (kiểu Đà Lạt), một xe, MỘT tài xế. Chuyến thêm 06:00 → 12:00, tài xế đã lái 6 giờ. Khung
     * chiều ngược sớm nhất là 18:00 cùng ngày, nhưng 6 + 6 = 12 giờ > 8 nên luật 8 giờ của TripService
     * loại; lượt về rơi vào 06:00 sáng hôm sau — đúng như lịch sử "về ngay" (QĐ-21 (d)).
     */
    @Test
    void theReturnLegWaitsForTheNextMorning_whenTheOnlyDriverHasDrivenSixHours() {
        Route out = route(home, far, 360, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 360, HolidayFlow.TOWARD_HUB);
        out.setDistanceKm(300.0);
        back.setDistanceKm(300.0);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        driver("vl-solo", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();

        RecommendationCardDto first = view.getCards().stream()
                .filter(c -> c.getDate().equals(TOMORROW)).findFirst().orElseThrow();
        assertEquals(RecommendationStatus.RECOMMENDED, first.getStatus());
        assertEquals(LoopLegKind.RETURN, first.getCompanion().getKind());
        assertEquals(TOMORROW.plusDays(1).atTime(6, 0), first.getCompanion().getDeparture(),
                "18:00 cùng ngày bị luật 8 giờ loại");
        assertEquals("Driver vl-solo", first.getCompanion().getDriverName());
    }

    // =====================================================================
    // Cả vòng một lần — một xe, một tài xế (CT7c, CM-24 PA-A)
    // =====================================================================

    /**
     * Đúng ca CM-24. Tài xế DUY NHẤT đã có chuyến thường lệ ngày mai 18:00 đi từ bến nhà (bằng xe khác).
     * Xét RIÊNG chuyến thêm 06:00 → 08:00 thì luật vị trí hai phía loại người đó: chuyến kế tiếp đi từ bến
     * nhà, còn chuyến thêm kết thúc ở bến xa — trước CT7c thẻ này "có xe nhưng không có tài xế". Xét CẢ
     * VÒNG — chuyến thêm rồi về 12:00, tới 14:00 — thì người đó về kịp chuyến 18:00, và ngày mai lái
     * 2 + 2 + 2 = 6 giờ ≤ 8. Cùng xe, cùng tài xế cho cả vòng.
     */
    @Test
    void theOnlyDriver_whoseNextTripLeavesHome_runsTheWholeLoop_becauseTheReturnBringsThemBackInTime() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        Bus other = bus("VL-B", home);
        Driver only = driver("vl-only", home);
        Trip regular = bare(out, TOMORROW.atTime(18, 0), TripStatus.ACTIVE, 22);
        regular.setBus(other);
        regular.setDriver(only);
        tripRepository.save(regular);
        flushAndClear();

        Trip alone = new Trip();
        alone.setRoute(tripService.routeForLocation(out.getId()));
        alone.setDepartureTime(TOMORROW.atTime(6, 0));
        alone.setArrivalTimeExpected(TOMORROW.atTime(8, 0));
        AvailabilityContext ctx = tripService.buildAvailabilityContext(LocalDate.now().atStartOfDay(),
                TOMORROW.plusDays(3).atStartOfDay());
        assertNull(tripService.selectBestAvailableDriver(alone.getDepartureTime(), alone.getArrivalTimeExpected(),
                2.0, 1, java.util.List.of(), false, ctx, alone.getRoute()),
                "xét riêng chuyến thêm: chuyến kế tiếp của người đó đi từ bến nhà, không từ bến xa");

        RecommendationViewDto view = recommendationService.buildRecommendations();

        RecommendationCardDto first = view.getCards().stream()
                .filter(c -> c.getDate().equals(TOMORROW)).findFirst().orElseThrow();
        assertEquals(RecommendationStatus.RECOMMENDED, first.getStatus(), first.getNote());
        assertEquals("Driver vl-only", first.getDriverName());
        RecommendationCardDto.CompanionLeg leg = first.getCompanion();
        assertEquals(LoopLegKind.RETURN, leg.getKind());
        assertEquals(TOMORROW.atTime(12, 0), leg.getDeparture(), "về ngay bằng khung 12:00");
        assertTrue(leg.isReserved());
        assertEquals(first.getBusPlate(), leg.getBusPlate(), "cùng một xe cho cả vòng");
        assertEquals("Driver vl-only", leg.getDriverName(), "cùng một tài xế cho cả vòng");
    }

    /**
     * Không xếp được CẢ VÒNG thì thẻ thiếu tài nguyên, không còn "tính như về rỗng" (CT7c) — như lịch sử
     * không chạy chuyến thêm khi không có lượt về (QĐ-21 (d)). Tuyến 6 giờ, một xe, một tài xế có bằng lái
     * hết hạn ngày kia. Chuyến thêm 06:00 ngày mai chạy được một mình, nhưng lượt về thì không: 18:00 cùng
     * ngày là 6 + 6 = 12 giờ > 8; từ sáng ngày kia bằng lái đã hết hạn.
     */
    @Test
    void whenNoBusAndDriverCanRunTheWholeLoop_theCardHasNoResource_insteadOfAnEmptyReturn() {
        Route out = route(home, far, 360, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 360, HolidayFlow.TOWARD_HUB);
        out.setDistanceKm(300.0);
        back.setDistanceKm(300.0);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        Driver expiring = driver("vl-exp", home);
        expiring.setLicenseExpiryDate(TOMORROW.plusDays(1));
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();

        RecommendationCardDto first = view.getCards().stream()
                .filter(c -> c.getDate().equals(TOMORROW)).findFirst().orElseThrow();
        assertEquals(RecommendationStatus.NO_RESOURCE, first.getStatus(), first.getNote());
        assertTrue(first.getNote().startsWith("Không xếp được cả vòng"), first.getNote());
        assertEquals("VL-A", first.getBusPlate(), "có xe cho cả vòng, chỉ thiếu tài xế");
        assertNull(first.getDriverName());
        assertEquals(LoopLegKind.RETURN, first.getCompanion().getKind(), "tiền theo kế hoạch: về ngay");
        assertFalse(first.getCompanion().isReserved());
    }

    // =====================================================================
    // Đưa xe tới trước (BRING) và không cần lượt đi kèm (NONE)
    // =====================================================================

    /**
     * Khung VỀ 12:00 nóng, bến xa không có xe nào. Xe ở bến nhà được đưa tới trước bằng khung chiều đi
     * MUỘN NHẤT còn kịp — 06:00 cùng ngày (06:00 + 2 giờ + 1 giờ chuẩn bị = 09:00 ≤ 12:00) — rồi chạy chuyến
     * thêm về bến nhà. Trước CT7 thẻ này "thiếu tài nguyên" (không ai ở bến xa).
     */
    @Test
    void anExtraTripFromTheFarEnd_bringsABusThereFirst_onTheLatestSlotStillInTime() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 10, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 30, 18, 10));
        bus("VL-A", home);
        driver("vl-1", home);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();

        assertEquals(7, view.getTotalCandidates());
        for (RecommendationCardDto card : view.getCards()) {
            assertEquals(back.getId(), card.getRouteId());
            assertEquals(RecommendationStatus.RECOMMENDED, card.getStatus(), card.getDate() + " " + card.getNote());
            RecommendationCardDto.CompanionLeg leg = card.getCompanion();
            assertEquals(LoopLegKind.BRING, leg.getKind());
            assertEquals(card.getDate().atTime(6, 0), leg.getDeparture(), "khung chiều đi muộn nhất còn kịp");
            assertEquals("VL-A", leg.getBusPlate());
            assertEquals("VL-A", card.getBusPlate(), "chính xe được đưa tới chạy chuyến thêm");
            assertTrue(leg.isReserved());
            assertEquals(0, ONE_LEG.multiply(BigDecimal.valueOf(2)).compareTo(card.getEstimatedCost()));
        }
    }

    /**
     * Xe bến nhà "Bến nhà VL" đang đứng ở bến xa (chuyến hôm qua đưa nó lên, lúc 15:00 — ngoài khung
     * thường lệ nên không thành một chuỗi dự báo). Chuyến thêm về bến nhà đưa đúng nó về: không cần lượt đi
     * kèm, cả vòng = một lượt.
     */
    @Test
    void aBusStrandedAtTheFarEnd_goesHomeOnTheExtraTrip_withoutACompanionLeg() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 10, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 30, 18, 10));
        Bus stranded = bus("VL-FAR", home);
        Driver driver = driver("vl-far", home);
        Trip up = bare(out, LocalDate.now().minusDays(1).atTime(15, 0), TripStatus.COMPLETED, 22);
        up.setBus(stranded);
        up.setDriver(driver);
        tripRepository.save(up);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();

        RecommendationCardDto first = view.getCards().stream()
                .filter(c -> c.getDate().equals(TOMORROW)).findFirst().orElseThrow();
        assertEquals(RecommendationStatus.RECOMMENDED, first.getStatus(), first.getNote());
        assertEquals("VL-FAR", first.getBusPlate());
        assertEquals(LoopLegKind.NONE, first.getCompanion().getKind());
        assertEquals(0, ONE_LEG.compareTo(first.getEstimatedCost()), "chỉ một lượt");
        assertEquals(first.getTripRevenue(), first.getEstimatedRevenue());
    }

    // =====================================================================
    // Chỉ đề xuất phần khung còn thiếu (cách 2a)
    // =====================================================================

    /**
     * Ngày mai khung 06:00 đã có chuyến chính và một chuyến tăng cường (job 10 giây, 06:30): khung cần 2,
     * đã có 2 → không thẻ. Ngày kia khung đã có chuyến chính chạy xe 40 chỗ: vẫn 1 thẻ "chuyến thêm 1/1",
     * nhưng khách của nó tính sau 40 ghế đó, nên gần như không chở ai. Các ngày khác chưa có lịch: giả định
     * 1 xe chuẩn 22 chỗ, như trước CT7.
     */
    @Test
    void tripsAlreadyInTheSlot_areSubtracted_andTheirRealSeatsCount() {
        Route out = route(home, far, 120, HolidayFlow.LEAVING_HUB);
        Route back = route(far, home, 120, HolidayFlow.TOWARD_HUB);
        history(out, Map.of(6, 30, 12, 10, 18, 10));
        history(back, Map.of(6, 10, 12, 10, 18, 10));
        bus("VL-A", home);
        bus("VL-B", home);
        driver("vl-1", home);
        driver("vl-2", home);
        tripRepository.save(bare(out, TOMORROW.atTime(6, 0), TripStatus.ACTIVE, 22));
        Trip extra = bare(out, TOMORROW.atTime(6, 30), TripStatus.PENDING_APPROVAL, 22);
        extra.setExtraTrip(true);
        tripRepository.save(extra);
        tripRepository.save(bare(out, TOMORROW.plusDays(1).atTime(6, 0), TripStatus.ACTIVE, 40));
        Trip cancelled = bare(out, TOMORROW.plusDays(2).atTime(6, 0), TripStatus.CANCELLED, 22);
        tripRepository.save(cancelled);
        flushAndClear();

        RecommendationViewDto view = recommendationService.buildRecommendations();
        double size = forecastService.buildForecast().getModel().getNegativeBinomialSize();
        Map<LocalDate, RecommendationCardDto> byDate = view.getCards().stream()
                .collect(Collectors.toMap(RecommendationCardDto::getDate, Function.identity()));

        assertEquals(7, view.getHotSlotCount());
        assertEquals(6, view.getTotalCandidates(), "ngày mai đã đủ 2 chuyến");
        assertEquals(1, view.getAlreadyInSlotCount());
        assertNull(byDate.get(TOMORROW));

        RecommendationCardDto bigBus = byDate.get(TOMORROW.plusDays(1));
        assertFalse(bigBus.isExistingAssumed());
        assertEquals(1, bigBus.getExistingTrips());
        assertEquals(40, bigBus.getExistingSeats());
        assertEquals(1, bigBus.getExtraIndex());
        assertEquals(ForecastService.extraTripPassengers(bigBus.getPredictedDemand(), 40, 22, size),
                bigBus.getExpectedPassengers(), 1e-9, "khách tràn sau 40 ghế đã có");

        RecommendationCardDto afterCancel = byDate.get(TOMORROW.plusDays(2));
        assertTrue(afterCancel.isExistingAssumed(), "chuyến đã hủy không chiếm chỗ của khung");
        assertEquals(ForecastService.extraTripPassengers(afterCancel.getPredictedDemand(), 22, 22, size),
                afterCancel.getExpectedPassengers(), 1e-9);
    }

    // =====================================================================

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

    private Route route(Station from, Station to, int minutes, HolidayFlow flow) {
        Route r = new Route();
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(minutes);
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
