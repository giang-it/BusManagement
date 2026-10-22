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
import giang.com.BusManagement.dto.RecommendationCardDto;
import giang.com.BusManagement.dto.RecommendationViewDto;
import giang.com.BusManagement.dto.WhatIfOutcomeDto;
import giang.com.BusManagement.dto.WhatIfScenarioDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What-if theo đơn vị nhu cầu (GĐ6, QĐ-17, G6-5), đi qua DB test: hệ số nhân thẳng vào số khách
 * (không kẹp trước), đếm theo chuyến thêm bằng chính tripsNeeded() của dự báo, tiền theo khách tràn —
 * và ở 100% nhu cầu, cùng số chuyến thêm và cùng tiền với các thẻ của màn Đề xuất.
 *
 * Một tuyến 120 km / 2 giờ, xe chuẩn 22 chỗ; lịch sử 45 vé / 50 ghế mỗi ngày lúc 06:00, nên dự báo
 * 45 khách: cần ⌈45 ÷ 19,8⌉ = 3 chuyến, tức 2 chuyến thêm mỗi ngày. 4 xe 22 chỗ và 3 tài xế đủ phủ
 * hết (4 × 2/3 = 2,67 chuyến/ngày theo xe, 12 theo tài xế). @Transactional nên rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class WhatIfSimulationServiceTest {

    @Autowired private WhatIfSimulationService whatIfSimulationService;
    @Autowired private RecommendationService recommendationService;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusTypeRepository busTypeRepository;
    @Autowired private BusRepository busRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        BusType seats22 = new BusType();
        seats22.setTypeName("Xe 22 chỗ test GĐ6 " + System.nanoTime());
        seats22.setCapacity(22);
        seats22 = busTypeRepository.save(seats22);

        Route route = new Route();
        route.setDistanceKm(120.0);
        route.setEstimatedDuration(120);
        route.setSuitableBusType(seats22);
        route.setHolidayFlow(HolidayFlow.LEAVING_HUB);
        route = routeRepository.save(route);

        LocalDate today = LocalDate.now();
        for (int d = 60; d >= 1; d--) {
            Trip t = new Trip();
            t.setRoute(route);
            t.setDepartureTime(today.minusDays(d).atTime(6, 0));
            t.setArrivalTimeExpected(t.getDepartureTime().plusHours(2));
            t.setTotalSeats(50);
            t.setTicketsSold(45);
            t.setPrice(new BigDecimal("120000"));
            t.setStatus(TripStatus.COMPLETED);
            tripRepository.save(t);
        }
        for (int i = 1; i <= 4; i++) {
            Bus bus = new Bus();
            bus.setLicensePlate("GD6-WI-" + i);
            bus.setBrand("TestBrand");
            bus.setBusType(seats22);
            bus.setOdometer(1000.0);
            bus.setLastMaintenanceOdometer(1000.0);
            bus.setMaintenanceThreshold(10000.0);
            bus.setStatus(BusStatus.READY);
            busRepository.save(bus);
        }
        for (int i = 1; i <= 3; i++) {
            User user = new User();
            user.setUsername("gd6-wi-" + i);
            user.setFullName("Driver gd6-wi-" + i);
            user.setPassword("x");
            user.setRole(Role.ROLE_DRIVER);
            Driver driver = new Driver();
            driver.setUser(user);
            driver.setLicenseNumber("LIC-GD6-WI-" + i);
            driver.setExperienceYears(5);
            driver.setTotalDrivingHours24h(0.0);
            driver.setIsActive(true);
            driver.setLicenseExpiryDate(today.plusYears(1));
            user.setDriver(driver);
            userRepository.save(user);
        }
    }

    /** Ở 100%: cùng số chuyến thêm, cùng doanh thu, chi phí và phần "chỉ chạy chuyến có lãi" với màn Đề xuất. */
    @Test
    void atFullDemand_whatIfPlansTheSameExtraTrips_andTheSameMoney_asTheRecommendationCards() {
        WhatIfOutcomeDto baseline = whatIfSimulationService.simulate(new WhatIfScenarioDto()).getBaseline();
        RecommendationViewDto cards = recommendationService.buildRecommendations();

        assertEquals(7, baseline.getHotSlots());
        assertEquals(14, baseline.getExtraTripsNeeded(), "7 ngày × 2 chuyến thêm");
        assertEquals(cards.getTotalCandidates(), baseline.getExtraTripsNeeded());
        assertEquals(14, baseline.getCoverable());
        assertEquals(0, baseline.getGap());

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal profitable = BigDecimal.ZERO;
        for (RecommendationCardDto card : cards.getCards()) {
            assertEquals(Integer.valueOf(22), card.getBusCapacity(), "thẻ chạy xe chuẩn, như What-if giả định");
            revenue = revenue.add(card.getEstimatedRevenue());
            cost = cost.add(card.getEstimatedCost());
            if (card.getEstimatedProfit().signum() > 0) {
                profitable = profitable.add(card.getEstimatedProfit());
            }
        }
        assertEquals(0, revenue.compareTo(baseline.getEstimatedRevenue()), revenue + " / " + baseline.getEstimatedRevenue());
        assertEquals(0, cost.compareTo(baseline.getEstimatedCost()));
        assertEquals(cards.getProfitableCount(), baseline.getProfitableTrips());
        assertEquals(7, baseline.getProfitableTrips(), "chuyến thêm 1/2 có lãi, 2/2 lỗ");
        assertEquals(0, profitable.compareTo(baseline.getProfitableProfit()));
    }

    /**
     * 70% của 45 khách là 31,5 khách: vẫn cần 2 chuyến, tức 1 chuyến thêm mỗi ngày. Trước GĐ6 hệ số
     * nhân vào tỉ lệ đã kẹp ở 100% (45 / 22 → 1,0 × 0,7 = 70%) nên màn báo 0. 150% là 67,5 khách:
     * cần 4 chuyến, 3 chuyến thêm mỗi ngày.
     */
    @Test
    void demandFactor_scalesPassengers_notAClampedRate() {
        assertEquals(7, scenario(70).getExtraTripsNeeded());
        assertEquals(7, scenario(70).getHotSlots());
        assertEquals(7, scenario(50).getExtraTripsNeeded(), "22,5 khách / 22 chỗ vẫn vượt 90%");
        assertEquals(21, scenario(150).getExtraTripsNeeded());
    }

    /**
     * Bớt 3 xe, còn 1: 2/3 chuyến/ngày, phủ ⌊7 × 2/3⌋ = 4 chuyến thêm, mỗi ngày tối đa 1. Trong mỗi ngày
     * kế hoạch lấy chuyến có lãi cao nhất trước (cùng thứ tự màn Đề xuất giữ xe), nên cả 4 là chuyến
     * thêm 1/2 có lãi, không phải chuyến 2/2 lỗ.
     */
    @Test
    void whenCapacityIsShort_theCoveredPlanTakesTheMostProfitableTripsFirst() {
        WhatIfScenarioDto lever = new WhatIfScenarioDto();
        lever.setDeltaBuses(-3);
        WhatIfOutcomeDto outcome = whatIfSimulationService.simulate(lever).getScenarioOutcome();

        assertEquals(14, outcome.getExtraTripsNeeded());
        assertEquals(4, outcome.getCoverable());
        assertEquals(10, outcome.getGap());
        assertEquals(4, outcome.getProfitableTrips());
        assertEquals(0, outcome.getEstimatedProfit().compareTo(outcome.getProfitableProfit()), "chỉ toàn chuyến có lãi");
        assertEquals(0, new BigDecimal("3280000").compareTo(outcome.getEstimatedCost()), "4 × 820.000 đ");
    }

    private WhatIfOutcomeDto scenario(int demandFactorPercent) {
        WhatIfScenarioDto lever = new WhatIfScenarioDto();
        lever.setDemandFactorPercent(demandFactorPercent);
        return whatIfSimulationService.simulate(lever).getScenarioOutcome();
    }
}
