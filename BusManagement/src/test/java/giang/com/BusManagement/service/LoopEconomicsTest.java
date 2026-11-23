package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.dto.LoopLegKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT7 — phép tính tiền cả vòng dùng chung cho màn Đề xuất và What-if (LoopEconomics), không
 * cần Spring. Ngày cố định 01/12/2026 (thứ Ba), cặp Sài Gòn (bến 3, bến nhà) ↔ Vũng Tàu (bến 12), 100 km,
 * 2 giờ, giá 100.000đ, xe chuẩn 22 chỗ; một lượt = 100 × 6.000 + 2 × 50.000 = 700.000đ.
 */
class LoopEconomicsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);
    private static final BigDecimal FUEL = new BigDecimal("6000");
    private static final BigDecimal WAGE = new BigDecimal("50000");
    private static final double NB_SIZE = 80.0;

    private final Station saigon = station(3L);
    private final Station vungTau = station(12L);
    private final Route out = route(10L, saigon, vungTau);
    private final Route back = route(11L, vungTau, saigon);

    /**
     * Lượt về đi cùng khung VT → SG 12:00 đang NÓNG (30 khách, cần 2 chuyến, chưa có lịch nên giả định 1 xe
     * chuẩn): lượt về chỉ chở phần còn tràn SAU chuyến thêm mà khung đó tự cần — tức sau 22 + 22 ghế — chứ
     * không chen trước nó. Nhờ vậy tiền của lượt về không phụ thuộc thứ tự xét thẻ.
     */
    @Test
    void aCompanionLegCarriesWhatIsLeftAfterTheSlotsOwnNeeds() {
        LoopEconomics economics = economics(Map.of(LoopEconomics.slotKey(11L, DAY, 12),
                new LoopEconomics.SlotDemand(30.0, 22)), Map.of(), 1.0);

        LoopEconomics.Leg leg = economics.companion(LoopLegKind.RETURN, back, DAY.atTime(12, 0), 22);

        assertEquals(ForecastService.extraTripPassengers(30.0, 44, 22, NB_SIZE), leg.passengers(), 1e-12);
        assertEquals(LoopEconomics.revenue(new BigDecimal("100000"), leg.passengers()), leg.revenue());
        assertEquals(0, new BigDecimal("700000").compareTo(leg.cost()));
    }

    /** Khung đi kèm đã có 2 chuyến (44 ghế) thật: khung đủ rồi, lượt về chở phần tràn sau 44 ghế đó. */
    @Test
    void aCompanionLegCountsTheSeatsAlreadyInItsSlot() {
        LoopEconomics economics = economics(
                Map.of(LoopEconomics.slotKey(11L, DAY, 12), new LoopEconomics.SlotDemand(30.0, 22)),
                Map.of(LoopEconomics.slotKey(11L, DAY, 12), new LoopEconomics.SlotTrips(2, 44)), 1.0);

        LoopEconomics.Leg leg = economics.companion(LoopLegKind.RETURN, back, DAY.atTime(12, 0), 22);

        assertEquals(ForecastService.extraTripPassengers(30.0, 44, 22, NB_SIZE), leg.passengers(), 1e-12);
    }

    /** Khung đi kèm ngoài dự báo: 0 khách, 0 đồng tiền vé, vẫn đủ chi phí một lượt. */
    @Test
    void aCompanionLegOutsideTheForecast_carriesNobody_butStillCosts() {
        LoopEconomics.Leg leg = economics(Map.of(), Map.of(), 1.0)
                .companion(LoopLegKind.RETURN, back, DAY.atTime(12, 0), 22);

        assertEquals(0.0, leg.passengers());
        assertEquals(0, BigDecimal.ZERO.compareTo(leg.revenue()));
        assertEquals(0, new BigDecimal("700000").compareTo(leg.cost()));
    }

    /** Hệ số nhu cầu của What-if nhân cả vào khung đi kèm. */
    @Test
    void theDemandFactorAppliesToTheCompanionSlotToo() {
        LoopEconomics economics = economics(Map.of(LoopEconomics.slotKey(11L, DAY, 12),
                new LoopEconomics.SlotDemand(20.0, 22)), Map.of(), 1.5);

        LoopEconomics.Leg leg = economics.companion(LoopLegKind.RETURN, back, DAY.atTime(12, 0), 22);

        assertEquals(ForecastService.extraTripPassengers(30.0, 44, 22, NB_SIZE), leg.passengers(), 1e-12,
                "20 × 1,5 = 30 khách, khung tự cần 1 chuyến thêm");
    }

    /**
     * Kế hoạch: chuyến thêm rời bến nhà → về ngay; chuyến thêm tới bến nhà → đưa xe tới trước; không có
     * chiều ngược → một lượt rỗng cùng quãng đường.
     */
    @Test
    void thePlannedCompanion_followsWhereTheSpareBusesLive() {
        Set<Long> homes = Set.of(3L);
        assertEquals(LoopLegKind.RETURN, LoopEconomics.plannedKind(out, back, homes));
        assertEquals(LoopLegKind.BRING, LoopEconomics.plannedKind(back, out, homes));
        assertEquals(LoopLegKind.ASSUMED_RETURN, LoopEconomics.plannedKind(out, null, homes));

        LoopEconomics economics = economics(Map.of(), Map.of(), 1.0);
        LoopEconomics.Leg ret = economics.planned(out, back, DAY.atTime(18, 0), DAY.atTime(20, 0), homes,
                DAY.minusDays(1).atStartOfDay(), 22);
        assertEquals(DAY.plusDays(1).atTime(6, 0), ret.departure(), "tới 20:00, khung 18:00 đã qua");
        LoopEconomics.Leg bring = economics.planned(back, out, DAY.atTime(12, 0), DAY.atTime(14, 0), homes,
                DAY.minusDays(1).atStartOfDay(), 22);
        assertEquals(DAY.atTime(6, 0), bring.departure(), "06:00 + 2 giờ + 1 giờ = 09:00 ≤ 12:00");
        LoopEconomics.Leg none = economics.planned(out, null, DAY.atTime(6, 0), DAY.atTime(8, 0), homes,
                DAY.minusDays(1).atStartOfDay(), 22);
        assertEquals(LoopLegKind.ASSUMED_RETURN, none.kind());
        assertNull(none.departure());
        assertEquals(0, new BigDecimal("700000").compareTo(none.cost()));
    }

    /** Giờ "về ngay": từ lúc tới + 1 giờ chuẩn bị, sớm trước, trong 2 ngày; chạm đúng mốc vẫn kịp. */
    @Test
    void returnTimes_startOneHourAfterArrival_earliestFirst() {
        List<LocalDateTime> times = LoopEconomics.returnTimes(DAY.atTime(11, 0));
        assertEquals(DAY.atTime(12, 0), times.get(0), "11:00 + 1 giờ = 12:00, vẫn kịp");
        assertEquals(DAY.atTime(18, 0), times.get(1));
        assertEquals(DAY.plusDays(2).atTime(18, 0), times.get(times.size() - 1));
        assertTrue(LoopEconomics.returnTimes(DAY.atTime(11, 1)).get(0).equals(DAY.atTime(18, 0)));
    }

    /** Giờ "đưa xe tới": muộn trước, tới kịp giờ đi cộng 1 giờ chuẩn bị, không trước lúc này. */
    @Test
    void bringTimes_latestFirst_neverBeforeNow() {
        List<LocalDateTime> times = LoopEconomics.bringTimes(DAY.atTime(18, 0), 120, DAY.atTime(7, 0));
        assertEquals(List.of(DAY.atTime(12, 0)), times, "06:00 đã qua lúc 07:00; 18:00 thì không kịp");
        assertTrue(LoopEconomics.bringTimes(DAY.atTime(6, 0), 360, DAY.atTime(19, 0).minusDays(1)).isEmpty(),
                "chuyến 6 giờ phải đi trước 23:00 hôm trước, mà 18:00 hôm trước đã qua");
    }

    /** Chuyến đã có gom theo khung (tuyến, ngày, giờ): 06:30 thuộc khung 06:00. */
    @Test
    void slotTrips_groupByRouteDayAndHour() {
        Map<String, LoopEconomics.SlotTrips> slots = LoopEconomics.slotTrips(List.of(
                new Object[] { 10L, DAY.atTime(6, 0), 22 },
                new Object[] { 10L, DAY.atTime(6, 30), 40 },
                new Object[] { 10L, DAY.atTime(12, 0), 22 }));

        assertEquals(new LoopEconomics.SlotTrips(2, 62), slots.get(LoopEconomics.slotKey(10L, DAY, 6)));
        assertEquals(new LoopEconomics.SlotTrips(1, 22), slots.get(LoopEconomics.slotKey(10L, DAY, 12)));
        assertEquals(2, LoopEconomics.extrasStillNeeded(3, new LoopEconomics.SlotTrips(1, 22)));
        assertEquals(0, LoopEconomics.extrasStillNeeded(2, new LoopEconomics.SlotTrips(3, 66)), "không âm");
    }

    /** Chi phí một lượt; thiếu cả hai tham số thì không ước được. */
    @Test
    void tripCost_isFuelPlusWage_andNullWithoutParameters() {
        assertEquals(0, new BigDecimal("700000").compareTo(LoopEconomics.tripCost(out, FUEL, WAGE)));
        assertNull(LoopEconomics.tripCost(out, null, null));
        assertEquals(new BigDecimal("1500000"), LoopEconomics.plus(new BigDecimal("1000000"), new BigDecimal("500000")));
        assertNull(LoopEconomics.plus(null, BigDecimal.ONE), "chuyến thêm chưa có giá thì cả vòng chưa có giá");
    }

    // =====================================================================

    private LoopEconomics economics(Map<String, LoopEconomics.SlotDemand> demand,
            Map<String, LoopEconomics.SlotTrips> trips, double factor) {
        Map<Long, Map<Integer, BigDecimal>> prices = Map.of(
                10L, Map.of(6, new BigDecimal("100000"), 12, new BigDecimal("100000"), 18, new BigDecimal("100000")),
                11L, Map.of(6, new BigDecimal("100000"), 12, new BigDecimal("100000"), 18, new BigDecimal("100000")));
        return new LoopEconomics(prices, demand, trips, NB_SIZE, factor, FUEL, WAGE);
    }

    private static Station station(Long id) {
        Station s = new Station();
        s.setId(id);
        s.setStationName("Bến " + id);
        return s;
    }

    private static Route route(Long id, Station from, Station to) {
        Route r = new Route();
        r.setId(id);
        r.setDistanceKm(100.0);
        r.setEstimatedDuration(120);
        r.setRouteStations(new java.util.ArrayList<>(List.of(stop(r, from, 1), stop(r, to, 2))));
        return r;
    }

    private static RouteStation stop(Route route, Station station, int order) {
        RouteStation rs = new RouteStation();
        rs.setId(new RouteStationId(route.getId(), station.getId()));
        rs.setRoute(route);
        rs.setStation(station);
        rs.setStopOrder(order);
        return rs;
    }
}
