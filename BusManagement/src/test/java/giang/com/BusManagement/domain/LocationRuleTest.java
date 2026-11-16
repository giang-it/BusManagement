package giang.com.BusManagement.domain;

import giang.com.BusManagement.domain.LocationRule.Leg;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Luật vị trí dùng chung (QĐ-23, CT5a): xe / người đang ở bến nào, đã xuất phát được chưa. JUnit
 * thuần, không DB — cùng cách HolidayCalendarTest chốt một luật thuần. Bến dùng đúng id của DB thật
 * cho dễ đọc: 3 = Miền Đông, 4 = Đà Lạt, 7 = Cần Thơ, 12 = Vũng Tàu.
 */
class LocationRuleTest {

    private static final Long MIEN_DONG = 3L;
    private static final Long DA_LAT = 4L;
    private static final Long CAN_THO = 7L;
    private static final Long VUNG_TAU = 12L;
    private static final Duration BUS_PREP = Duration.ofHours(1);

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    /** Sài Gòn → Vũng Tàu 06:00–08:00, rồi Vũng Tàu → Sài Gòn 12:00–14:00. */
    private static List<Leg> vungTauLoop() {
        return List.of(new Leg(MIEN_DONG, VUNG_TAU, at(20, 6, 0), at(20, 8, 0)),
                new Leg(VUNG_TAU, MIEN_DONG, at(20, 12, 0), at(20, 14, 0)));
    }

    @Test
    void noTripYet_meansTheHomeStation_andUnknownHomeMeansUnknown() {
        assertEquals(MIEN_DONG, LocationRule.stationAt(List.of(), MIEN_DONG, at(20, 6, 0)));
        assertEquals(MIEN_DONG, LocationRule.stationAt(null, MIEN_DONG, at(20, 6, 0)));
        assertNull(LocationRule.stationAt(List.of(), null, at(20, 6, 0)));

        assertTrue(LocationRule.canDepart(List.of(), MIEN_DONG, MIEN_DONG, at(20, 6, 0), BUS_PREP));
        assertFalse(LocationRule.canDepart(List.of(), MIEN_DONG, DA_LAT, at(20, 6, 0), BUS_PREP),
                "chưa chạy chuyến nào thì chỉ xuất phát được từ bến nhà");
        assertFalse(LocationRule.canDepart(List.of(), null, MIEN_DONG, at(20, 6, 0), BUS_PREP),
                "không có bến nhà và chưa chạy chuyến nào = không biết ở đâu: máy không chọn");
    }

    @Test
    void location_isTheEndOfTheLatestLegArrived_inAnyInputOrder() {
        List<Leg> loop = new ArrayList<>(vungTauLoop());
        java.util.Collections.reverse(loop);

        assertEquals(MIEN_DONG, LocationRule.stationAt(loop, MIEN_DONG, at(20, 7, 0)), "đang chạy chiều đi: chưa tới");
        assertEquals(VUNG_TAU, LocationRule.stationAt(loop, MIEN_DONG, at(20, 8, 0)), "tới đúng 08:00 là đã ở Vũng Tàu");
        assertEquals(VUNG_TAU, LocationRule.stationAt(loop, MIEN_DONG, at(20, 13, 0)), "đang chạy chiều về: vẫn tính Vũng Tàu");
        assertEquals(MIEN_DONG, LocationRule.stationAt(loop, MIEN_DONG, at(20, 14, 0)));
        assertEquals(MIEN_DONG, LocationRule.stationAt(loop, CAN_THO, at(21, 6, 0)), "đã có chuyến thì bến nhà không còn quyết định");
    }

    @Test
    void canDepart_needsTheRightStation_andEnoughRest() {
        List<Leg> loop = vungTauLoop();

        assertTrue(LocationRule.canDepart(loop, MIEN_DONG, VUNG_TAU, at(20, 12, 0), BUS_PREP),
                "tới 08:00, đi 12:00: đúng bến, nghỉ 4 giờ");
        assertFalse(LocationRule.canDepart(loop, MIEN_DONG, MIEN_DONG, at(20, 12, 0), BUS_PREP),
                "lúc 12:00 xe đang ở Vũng Tàu, không xuất phát được từ Miền Đông");
        assertFalse(LocationRule.canDepart(loop, MIEN_DONG, MIEN_DONG, at(20, 14, 30), BUS_PREP),
                "về tới 14:00, mới nghỉ 30 phút < 1 giờ chuẩn bị");
        assertTrue(LocationRule.canDepart(loop, MIEN_DONG, MIEN_DONG, at(20, 15, 0), BUS_PREP),
                "nghỉ đúng 1 giờ là đủ");
        assertTrue(LocationRule.canDepart(loop, MIEN_DONG, MIEN_DONG, at(20, 14, 30), Duration.ofMinutes(30)),
                "tài xế chỉ cần nghỉ 30 phút");
        assertFalse(LocationRule.canDepart(loop, MIEN_DONG, null, at(20, 15, 0), BUS_PREP));
    }

    @Test
    void canDepart_withTheLastLegKept_agreesWithTheListVersion_andRefusesAWrongLastLeg() {
        Leg back = vungTauLoop().get(1);
        assertTrue(LocationRule.canDepart(back, CAN_THO, MIEN_DONG, at(20, 18, 0), BUS_PREP));
        assertFalse(LocationRule.canDepart((Leg) null, CAN_THO, MIEN_DONG, at(20, 18, 0), BUS_PREP),
                "chưa có lượt nào: chỉ bến nhà (Cần Thơ) mới xuất phát được");
        assertThrows(IllegalArgumentException.class,
                () -> LocationRule.canDepart(back, MIEN_DONG, MIEN_DONG, at(20, 13, 0), BUS_PREP),
                "lượt tới nơi sau giờ xuất phát không phải 'lượt gần nhất trước đó'");
    }

    @Test
    void legOf_takesFirstAndLastStop_andIgnoresCancelledOrIncompleteTrips() {
        Trip trip = trip(TripStatus.COMPLETED, MIEN_DONG, DA_LAT, CAN_THO);
        Leg leg = LocationRule.legOf(trip);
        assertEquals(MIEN_DONG, leg.fromStationId());
        assertEquals(CAN_THO, leg.toStationId(), "tuyến nhiều trạm: đổi chỗ từ trạm đầu tới trạm CUỐI");
        assertEquals(trip.getDepartureTime(), leg.departure());
        assertEquals(trip.getArrivalTimeExpected(), leg.arrival());

        assertNull(LocationRule.legOf(trip(TripStatus.CANCELLED, MIEN_DONG, DA_LAT)), "chuyến hủy không làm xe đổi chỗ");
        Trip noArrival = trip(TripStatus.ACTIVE, MIEN_DONG, DA_LAT);
        noArrival.setArrivalTimeExpected(null);
        assertNull(LocationRule.legOf(noArrival));
        Trip noRoute = trip(TripStatus.ACTIVE, MIEN_DONG, DA_LAT);
        noRoute.setRoute(null);
        assertNull(LocationRule.legOf(noRoute));
        assertNull(LocationRule.legOf(null));
    }

    @Test
    void aLegCannotArriveBeforeItLeaves() {
        assertThrows(IllegalArgumentException.class,
                () -> new Leg(MIEN_DONG, DA_LAT, at(20, 12, 0), at(20, 6, 0)));
    }

    // =====================================================================

    /** Chuyến 20/11 06:00 → 12:00 trên một tuyến đi qua các bến theo đúng thứ tự cho trước. */
    private static Trip trip(TripStatus status, Long... stationIds) {
        Route route = new Route();
        List<RouteStation> stops = new ArrayList<>();
        // Thêm theo thứ tự NGƯỢC để chắc rằng legOf sắp theo stopOrder chứ không theo thứ tự danh sách.
        for (int i = stationIds.length - 1; i >= 0; i--) {
            Station station = new Station();
            station.setId(stationIds[i]);
            RouteStation rs = new RouteStation();
            rs.setStation(station);
            rs.setStopOrder(i + 1);
            stops.add(rs);
        }
        route.setRouteStations(stops);
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setStatus(status);
        trip.setDepartureTime(at(20, 6, 0));
        trip.setArrivalTimeExpected(at(20, 12, 0));
        return trip;
    }
}
