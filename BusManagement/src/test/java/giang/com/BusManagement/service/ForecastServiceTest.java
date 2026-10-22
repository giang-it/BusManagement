package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.dto.DemandForecastViewDto;
import giang.com.BusManagement.dto.ForecastAccuracyDto;
import giang.com.BusManagement.dto.ForecastPointDto;
import giang.com.BusManagement.dto.ForecastSeriesDto;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ForecastService đi qua DB thật của test (GĐ5, QĐ-16): chỉ chuyến COMPLETED làm nên lịch sử, chuyến
 * tăng cường gộp vào khung của nó, ngưỡng 28 ngày, dự báo bằng số khách từ ngày mai kèm cầu nối
 * cho Recommendation và What-if, nhớ kết quả tới khi dữ liệu đổi, và phần tự chấm điểm của màn
 * Dự báo. @Transactional nên mọi bản ghi được rollback sau từng test.
 *
 * Ngày lấy theo hôm nay như service (LocalDate.now()); dữ liệu nằm trong những ngày vừa qua.
 */
@SpringBootTest
@Transactional
class ForecastServiceTest {

    @Autowired private ForecastService forecastService;
    @Autowired private TripRepository tripRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private BusTypeRepository busTypeRepository;

    private Route route;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        BusType type = new BusType();
        type.setTypeName("Xe 40 chỗ test GĐ5 " + System.nanoTime());
        type.setCapacity(40);
        type = busTypeRepository.save(type);

        route = new Route();
        route.setDistanceKm(50.0);
        route.setEstimatedDuration(60);
        route.setSuitableBusType(type);
        route.setHolidayFlow(HolidayFlow.LEAVING_HUB);
        route = routeRepository.save(route);

        today = LocalDate.now();
    }

    @Test
    void steadySlot_isForecastInPassengersFromTomorrow_withTheBridgeForConsumers() {
        daily(70, 6, 20, 40);

        DemandForecastViewDto view = forecastService.buildForecast();
        ForecastSeriesDto s = series(view, 6);

        assertNotNull(s);
        assertEquals(today.plusDays(1), view.getForecastFrom());
        assertEquals(7, s.getPoints().size(), "chân trời của thẻ");
        assertEquals(35, s.getPreview().size(), "xem trước");
        assertEquals(today.plusDays(1), s.getPoints().get(0).getDate());
        assertEquals(today.minusDays(1), view.getHistoryEnd(), "ngày gốc = ngày cuối có dữ liệu");
        assertEquals(40, s.getStandardSeats());
        assertTrue(s.isStandardSeatsFromRoute());
        assertEquals("Rời TP lớn", s.getFlowLabel());
        assertEquals(70, s.getObservedDays());
        assertEquals(0, s.getSoldOutDays());
        for (ForecastPointDto p : s.getPreview()) {
            assertEquals(20.0, p.getPredictedDemand(), 1e-3, p.getDate().toString());
            assertEquals(0.5, p.getDemandRatio(), 1e-4);
            assertEquals(0.5, p.getPredictedOccupancy(), 1e-4, "cầu nối: 20 khách / xe 40 chỗ");
            assertFalse(p.isNeedsReinforcement());
            assertEquals(1, p.getTripsNeeded());
        }
        assertNull(view.getAccuracy(), "buildForecast() không tự chấm điểm");
    }

    /**
     * Ngày thường xe 40 chỗ luôn bán hết 40 vé; 10 ngày cuối có thêm chuyến tăng cường 06:30 (10 vé / 22
     * chỗ), nên khung 06:00 của những ngày đó KHÔNG bán hết (50 / 62). Ngày chỉ có chuyến bị hủy hay
     * chưa kết thúc không phải là quan sát.
     */
    @Test
    void onlyCompletedTripsCount_andExtraTripsJoinTheirSlot() {
        daily(60, 6, 40, 40);
        for (int d = 1; d <= 10; d++) {
            trip(TripStatus.COMPLETED, today.minusDays(d).atTime(6, 30), 10, 22);
        }
        for (int d = 61; d <= 66; d++) {
            trip(TripStatus.CANCELLED, today.minusDays(d).atTime(6, 0), 35, 40);
        }
        trip(TripStatus.DEPARTED, today.minusDays(67).atTime(6, 0), 30, 40);
        trip(TripStatus.ACTIVE, today.minusDays(68).atTime(6, 0), 30, 40);

        ForecastSeriesDto s = series(forecastService.buildForecast(), 6);

        assertNotNull(s);
        assertEquals(60, s.getObservedDays(), "chỉ ngày có chuyến COMPLETED");
        assertEquals(50, s.getSoldOutDays(), "10 ngày có chuyến tăng cường: 50 vé / 62 ghế, không bán hết");
    }

    @Test
    void seriesWithFewerThan28Days_isSkippedAndCounted() {
        daily(40, 6, 20, 40);
        daily(28, 18, 20, 40); // đúng ngưỡng: vào
        daily(27, 12, 20, 40); // thiếu một ngày: không vào
        for (int d = 1; d <= 3; d++) {
            trip(TripStatus.COMPLETED, today.minusDays(d).atTime(21, 0), 5, 40); // chuyến lẻ Admin tự tạo
        }

        DemandForecastViewDto view = forecastService.buildForecast();

        assertNotNull(series(view, 6));
        assertNotNull(series(view, 18));
        assertNull(series(view, 12));
        assertNull(series(view, 21));
        assertTrue(view.getSkippedSeriesCount() >= 2);
        assertEquals(28, view.getMinObservedDays());
    }

    @Test
    void nearlyFullSlot_needsReinforcement_andTwoTrips() {
        daily(60, 6, 38, 40);

        DemandForecastViewDto view = forecastService.buildForecast();
        ForecastSeriesDto s = series(view, 6);

        for (ForecastPointDto p : s.getPoints()) {
            assertEquals(38.0, p.getPredictedDemand(), 1e-3);
            assertTrue(p.isNeedsReinforcement(), "38 / 40 = 95% > 90%");
            assertEquals(2, p.getTripsNeeded());
            assertEquals(0.95, p.getPredictedOccupancy(), 1e-4);
        }
        assertTrue(s.isAnyReinforcement());
        assertTrue(view.getReinforcementSignalCount() >= 7);
    }

    /**
     * Khung ngày nào cũng bán hết vé: EM đoán nhu cầu VƯỢT số ghế (điều mô hình cũ không thể nói), nhưng
     * cầu nối cho Recommendation và What-if vẫn kẹp ở 100% như hợp đồng cũ của predictedOccupancy.
     */
    @Test
    void soldOutSlot_isForecastAboveItsSeats_whileTheBridgeStaysClamped() {
        daily(60, 6, 40, 40);

        ForecastSeriesDto s = series(forecastService.buildForecast(), 6);

        assertEquals(60, s.getSoldOutDays());
        for (ForecastPointDto p : s.getPoints()) {
            assertTrue(p.getPredictedDemand() > 40, "nhu cầu > số ghế: " + p.getPredictedDemand());
            assertTrue(p.getDemandRatio() > 1);
            assertEquals(1.0, p.getPredictedOccupancy(), 0.0, "cầu nối kẹp [0, 1]");
            assertTrue(p.getTripsNeeded() >= 2);
        }
    }

    /** G5-4: lần thứ hai dùng lại kết quả đã khớp; đổi một vé là khớp lại. */
    @Test
    void secondCall_reusesTheFit_untilATicketChanges() {
        daily(40, 6, 20, 40);
        Trip last = trip(TripStatus.COMPLETED, today.minusDays(41).atTime(6, 0), 20, 40);

        forecastService.buildForecast();
        assertTrue(forecastService.buildForecast().getModel().isReused());

        last.setTicketsSold(21);
        tripRepository.saveAndFlush(last);
        assertFalse(forecastService.buildForecast().getModel().isReused());
    }

    @Test
    void forecastPage_scoresTheLastEightWeeks_againstTicketsSold() {
        daily(100, 6, 20, 40);

        DemandForecastViewDto page = forecastService.buildForecastPage();
        ForecastAccuracyDto accuracy = page.getAccuracy();

        assertNotNull(accuracy);
        assertTrue(accuracy.isAvailable());
        assertEquals(8, accuracy.getOriginCount());
        assertEquals(today.minusDays(1 + 7 * 8), accuracy.getFirstOrigin());
        assertEquals(today.minusDays(1 + 7), accuracy.getLastOrigin());
        assertEquals(8 * 7, accuracy.getRecent().getTestedSlotDays(), "8 ngày gốc × 7 ngày × 1 chuỗi");
        assertEquals(0.0, accuracy.getRecent().getModelWape(), 0.01, "nhu cầu không đổi: mô hình gần như không sai");
        assertEquals(0.0, accuracy.getRecent().getBaselineWape(), 1e-9);
        assertNull(accuracy.getTet(), "100 ngày chưa có một Tết trọn cửa sổ cùng một Tết trước đó");
    }

    @Test
    void noHistory_givesAnEmptyForecast_notAnError() {
        DemandForecastViewDto view = forecastService.buildForecastPage();

        assertTrue(view.getSeries().isEmpty(), "DB test rỗng: mọi test đều rollback");
        assertNull(view.getModel());
        assertNull(view.getHistoryStart());
        assertEquals(today.plusDays(1), view.getForecastFrom());
    }

    // =====================================================================

    /** Một chuyến COMPLETED mỗi ngày ở giờ đã cho, `days` ngày kết thúc hôm qua. */
    private void daily(int days, int hour, int sold, int seats) {
        for (int d = days; d >= 1; d--) {
            trip(TripStatus.COMPLETED, today.minusDays(d).atTime(hour, 0), sold, seats);
        }
    }

    private Trip trip(TripStatus status, LocalDateTime departure, int sold, int seats) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setDepartureTime(departure);
        t.setArrivalTimeExpected(departure.plusHours(1));
        t.setTotalSeats(seats);
        t.setTicketsSold(sold);
        t.setPrice(new BigDecimal("50000"));
        t.setStatus(status);
        return tripRepository.save(t);
    }

    private ForecastSeriesDto series(DemandForecastViewDto view, int hour) {
        return view.getSeries().stream()
                .filter(s -> s.getRouteId().equals(route.getId()) && s.getDepartureHour() == hour)
                .findFirst()
                .orElse(null);
    }
}
