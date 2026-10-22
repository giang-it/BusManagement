package giang.com.BusManagement.service;

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
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationStatus;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recommendation theo đơn vị nhu cầu (GĐ6, QĐ-17), đi qua DB test: mỗi chuyến thêm một thẻ (G6-2),
 * khách của chuyến thêm là khách tràn rơi vào ghế của nó (G6-1), xe + tài xế được giữ chỗ giữa các
 * thẻ (G6-3, đóng M-14), thẻ xếp theo lợi nhuận và chuyến lỗ vẫn có thẻ (G6-4).
 *
 * Lịch sử là chuyến COMPLETED mỗi ngày, kết thúc hôm qua; nhu cầu không đổi nên dự báo bằng đúng số
 * vé (như ForecastServiceTest). Khung không bán hết (45 vé / 50 ghế) để EM không đoán lại. Chi phí
 * dùng tham số mặc định (bảng cost_parameters của DB test rỗng). @Transactional nên mọi bản ghi được
 * rollback sau từng test.
 */
@SpringBootTest
@Transactional
class RecommendationServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("120000");

    @Autowired private RecommendationService recommendationService;
    @Autowired private ForecastService forecastService;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private UserRepository userRepository;

    private BusType seats22;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        seats22 = new BusType();
        seats22.setTypeName("Xe 22 chỗ test GĐ6 " + System.nanoTime());
        seats22.setCapacity(22);
        seats22 = busTypeRepository.save(seats22);
        today = LocalDate.now();
    }

    /**
     * 45 khách / xe chuẩn 22 chỗ: cần ⌈45 ÷ 19,8⌉ = 3 chuyến, tức 2 chuyến thêm mỗi ngày. Hai thẻ
     * "1/2" và "2/2" chạy hai xe và hai tài xế khác nhau; thẻ 2/2 chỉ chở phần khách còn tràn sau xe
     * chuẩn VÀ chuyến thêm 1/2, nên có lãi ít hơn hẳn — ở đây là lỗ, và xếp xuống dưới.
     */
    @Test
    void slotNeedingThreeTrips_getsOneCardPerExtraTrip_onDifferentBusesAndDrivers() {
        Route route = route(120, 120);
        history(route, 6, 45, 50);
        bus("GD6-A", seats22);
        bus("GD6-B", seats22);
        driver("gd6-d1");
        driver("gd6-d2");

        RecommendationViewDto view = recommendationService.buildRecommendations();
        DemandForecastViewDto forecast = forecastService.buildForecast();
        double size = forecast.getModel().getNegativeBinomialSize();

        assertEquals(7, view.getHotSlotCount());
        assertEquals(14, view.getTotalCandidates(), "7 ngày × 2 chuyến thêm");
        assertEquals(14, view.getRecommendedCount());
        assertEquals(0, view.getNoResourceCount());

        Map<LocalDate, List<RecommendationCardDto>> byDate = view.getCards().stream()
                .collect(Collectors.groupingBy(RecommendationCardDto::getDate));
        assertEquals(7, byDate.size());
        for (ForecastPointDto point : series(forecast, route, 6).getPoints()) {
            double demand = point.getPredictedDemand();
            assertEquals(3, point.getTripsNeeded());
            List<RecommendationCardDto> pair = byDate.get(point.getDate()).stream()
                    .sorted(Comparator.comparingInt(RecommendationCardDto::getExtraIndex)).toList();
            RecommendationCardDto first = pair.get(0);
            RecommendationCardDto second = pair.get(1);

            assertEquals(1, first.getExtraIndex());
            assertEquals(2, second.getExtraIndex());
            assertEquals(2, first.getExtraCount());
            assertEquals(22, first.getStandardSeats());
            assertNotEquals(first.getBusPlate(), second.getBusPlate(), "giữ chỗ: không trùng xe");
            assertNotEquals(first.getDriverName(), second.getDriverName(), "giữ chỗ: không trùng tài xế");

            // G6-1: chuyến thêm thứ k chở khách tràn rơi vào ghế của nó, sau các chuyến trước nó.
            assertEquals(ForecastService.extraTripPassengers(demand, 22, 22, size), first.getExpectedPassengers(), 1e-9);
            assertEquals(ForecastService.extraTripPassengers(demand, 44, 22, size), second.getExpectedPassengers(), 1e-9);
            assertEquals(SlotDemandModel.expectedOverflow(demand, 22, size),
                    first.getExpectedPassengers() + second.getExpectedPassengers()
                            + SlotDemandModel.expectedOverflow(demand, 66, size), 1e-9,
                    "hai chuyến thêm không chở quá số khách tràn");

            // Tiền: doanh thu = khách × giá vé, chi phí = 6.000 đ/km × 120 km + 50.000 đ/giờ × 2 giờ.
            assertEquals(PRICE.multiply(BigDecimal.valueOf(first.getExpectedPassengers())).setScale(0, java.math.RoundingMode.HALF_UP),
                    first.getEstimatedRevenue());
            assertEquals(0, new BigDecimal("820000").compareTo(first.getEstimatedCost()));
            assertTrue(first.getEstimatedProfit().signum() > 0, "chuyến thêm 1/2 có lãi");
            assertTrue(second.getEstimatedProfit().signum() < 0, "chuyến thêm 2/2 lỗ dự kiến, vẫn có thẻ");
            assertEquals(RecommendationStatus.RECOMMENDED, second.getStatus(), "lỗ vẫn là chuyến chạy được");
        }

        // G6-4: xếp theo lợi nhuận giảm dần — 7 chuyến có lãi lên trước 7 chuyến lỗ.
        List<BigDecimal> profits = view.getCards().stream().map(RecommendationCardDto::getEstimatedProfit).toList();
        for (int i = 1; i < profits.size(); i++) {
            assertTrue(profits.get(i - 1).compareTo(profits.get(i)) >= 0, "thẻ " + i);
        }
        assertEquals(7, view.getProfitableCount());
        assertEquals(7, view.getLossCount());
    }

    /**
     * Chỉ một xe: chuyến thêm 1/2 giữ xe, chuyến thêm 2/2 cùng giờ không còn xe. Trước GĐ6 mỗi thẻ chọn
     * độc lập nên cả hai đều "đề xuất" cùng một xe. Thẻ thiếu xe vẫn cho biết nó đáng giá bao nhiêu,
     * tính với xe chuẩn.
     */
    @Test
    void oneBus_theSecondExtraTripOfTheSameHour_hasNoResource() {
        Route route = route(120, 120);
        history(route, 6, 45, 50);
        bus("GD6-ONLY", seats22);
        driver("gd6-d1");
        driver("gd6-d2");

        RecommendationViewDto view = recommendationService.buildRecommendations();
        double size = forecastService.buildForecast().getModel().getNegativeBinomialSize();

        assertEquals(14, view.getTotalCandidates());
        assertEquals(7, view.getRecommendedCount());
        assertEquals(7, view.getNoResourceCount());
        for (RecommendationCardDto card : view.getCards()) {
            if (card.getExtraIndex() == 1) {
                assertEquals(RecommendationStatus.RECOMMENDED, card.getStatus());
                assertEquals("GD6-ONLY", card.getBusPlate());
            } else {
                assertEquals(RecommendationStatus.NO_RESOURCE, card.getStatus(), card.getDate().toString());
                assertNull(card.getBusPlate());
                assertNull(card.getBusCapacity());
                assertTrue(card.getNote().contains("xe chuẩn 22 chỗ"), card.getNote());
                assertEquals(ForecastService.extraTripPassengers(card.getPredictedDemand(), 44, 22, size),
                        card.getExpectedPassengers(), 1e-9);
                assertNotNull(card.getEstimatedProfit());
            }
        }
    }

    /**
     * Chỉ còn một xe 40 chỗ cho tuyến xe chuẩn 22 chỗ: chuyến thêm 1/2 chở khách tràn rơi vào 40 ghế của
     * nó, nhiều hơn một xe 22 chỗ; chuyến thêm 2/2 (không còn xe) tính sau 22 + 40 ghế đó.
     */
    @Test
    void extraTripOnABiggerBus_carriesMoreOfTheOverflow_andTheNextOneCountsThoseSeats() {
        BusType seats40 = new BusType();
        seats40.setTypeName("Xe 40 chỗ test GĐ6 " + System.nanoTime());
        seats40.setCapacity(40);
        seats40 = busTypeRepository.save(seats40);
        Route route = route(120, 120);
        history(route, 6, 45, 50);
        bus("GD6-40", seats40);
        driver("gd6-d1");

        RecommendationViewDto view = recommendationService.buildRecommendations();
        double size = forecastService.buildForecast().getModel().getNegativeBinomialSize();

        for (RecommendationCardDto card : view.getCards()) {
            double demand = card.getPredictedDemand();
            if (card.getExtraIndex() == 1) {
                assertEquals(Integer.valueOf(40), card.getBusCapacity());
                assertEquals(ForecastService.extraTripPassengers(demand, 22, 40, size), card.getExpectedPassengers(), 1e-9);
                assertTrue(card.getExpectedPassengers() > ForecastService.extraTripPassengers(demand, 22, 22, size));
            } else {
                assertEquals(RecommendationStatus.NO_RESOURCE, card.getStatus());
                assertEquals(ForecastService.extraTripPassengers(demand, 62, 22, size), card.getExpectedPassengers(), 1e-9);
            }
        }
    }

    /**
     * Một xe, hai khung chồng giờ trên tuyến 2 giờ: 06:00 có 21 khách (vừa quá 90% xe 22 chỗ, chuyến thêm
     * lỗ) và 07:00 có 40 khách (chuyến thêm 1/2 có lãi). Xét theo lãi nên xe về chuyến 07:00; nếu xét
     * theo giờ thì chuyến lỗ 06:00 sẽ giữ mất xe (G6-4).
     */
    @Test
    void scarceBus_goesToTheMostProfitableExtraTrip_notTheEarliest() {
        Route route = route(120, 120);
        history(route, 6, 21, 30);
        history(route, 7, 40, 50);
        bus("GD6-ONLY", seats22);
        driver("gd6-d1");
        driver("gd6-d2");

        RecommendationViewDto view = recommendationService.buildRecommendations();

        assertEquals(21, view.getTotalCandidates(), "06:00 cần 1 chuyến thêm, 07:00 cần 2, × 7 ngày");
        for (RecommendationCardDto card : view.getCards()) {
            boolean profitableOne = card.getDepartureHour() == 7 && card.getExtraIndex() == 1;
            assertEquals(profitableOne ? RecommendationStatus.RECOMMENDED : RecommendationStatus.NO_RESOURCE,
                    card.getStatus(), card.getSlotLabel() + " " + card.getExtraIndex() + " " + card.getDate());
            assertEquals(profitableOne, card.getEstimatedProfit().signum() > 0);
        }
        assertEquals(7, view.getProfitableCount());
        assertEquals(14, view.getLossCount());
    }

    /**
     * Một tài xế, tuyến 6 giờ, hai khung 06:00 và 14:00 cùng ngày: không trùng giờ, nhưng chuyến giữ chỗ
     * lúc 06:00 cộng 6 giờ lái, nên chuyến 14:00 (6 + 6 = 12 giờ) bị luật 8 giờ của TripService loại.
     * Khung 06:00 đông hơn nên lãi hơn, được xét và giữ tài xế trước.
     */
    @Test
    void reservedTrips_countTowardTheEightHourDrivingLimit() {
        Route route = route(300, 360);
        history(route, 6, 32, 40);
        history(route, 14, 30, 40);
        bus("GD6-A", seats22);
        bus("GD6-B", seats22);
        driver("gd6-solo");

        RecommendationViewDto view = recommendationService.buildRecommendations();

        assertEquals(14, view.getTotalCandidates(), "mỗi khung 1 chuyến thêm, 2 khung × 7 ngày");
        for (RecommendationCardDto card : view.getCards()) {
            if (card.getDepartureHour() == 6) {
                assertEquals(RecommendationStatus.RECOMMENDED, card.getStatus(), card.getDate().toString());
            } else {
                assertEquals(RecommendationStatus.NO_RESOURCE, card.getStatus(), card.getDate().toString());
                assertTrue(card.getNote().startsWith("Có xe"), card.getNote());
                assertNull(card.getDriverName());
            }
        }
    }

    @Test
    void noHistory_givesNoCards_notAnError() {
        RecommendationViewDto view = recommendationService.buildRecommendations();

        assertTrue(view.getCards().isEmpty());
        assertEquals(0, view.getHotSlotCount());
        assertEquals(0, view.getTotalCandidates());
    }

    // =====================================================================

    private Route route(double distanceKm, int durationMinutes) {
        Route route = new Route();
        route.setDistanceKm(distanceKm);
        route.setEstimatedDuration(durationMinutes);
        route.setSuitableBusType(seats22);
        route.setHolidayFlow(HolidayFlow.LEAVING_HUB);
        return routeRepository.save(route);
    }

    /** Một chuyến COMPLETED mỗi ngày ở giờ đã cho, 60 ngày kết thúc hôm qua. */
    private void history(Route route, int hour, int sold, int seats) {
        for (int d = 60; d >= 1; d--) {
            Trip t = new Trip();
            t.setRoute(route);
            t.setDepartureTime(today.minusDays(d).atTime(hour, 0));
            t.setArrivalTimeExpected(t.getDepartureTime().plusMinutes(route.getEstimatedDuration()));
            t.setTotalSeats(seats);
            t.setTicketsSold(sold);
            t.setPrice(PRICE);
            t.setStatus(TripStatus.COMPLETED);
            tripRepository.save(t);
        }
    }

    private void bus(String plate, BusType type) {
        Bus bus = new Bus();
        bus.setLicensePlate(plate);
        bus.setBrand("TestBrand");
        bus.setBusType(type);
        bus.setOdometer(1000.0);
        bus.setLastMaintenanceOdometer(1000.0);
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
        d.setLicenseNumber("LIC-" + username);
        d.setExperienceYears(5);
        d.setTotalDrivingHours24h(0.0);
        d.setIsActive(true);
        d.setLicenseExpiryDate(LocalDate.now().plusYears(1));
        user.setDriver(d);
        userRepository.save(user);
    }

    private ForecastSeriesDto series(DemandForecastViewDto view, Route route, int hour) {
        return view.getSeries().stream()
                .filter(s -> s.getRouteId().equals(route.getId()) && s.getDepartureHour() == hour)
                .findFirst()
                .orElseThrow();
    }
}
