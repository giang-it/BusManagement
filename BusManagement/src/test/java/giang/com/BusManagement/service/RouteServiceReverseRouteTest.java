package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.Station;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route pairs CT6 (CM-6): tuyến chiều ngược suy từ trạm dừng — đi từ bến đến của tuyến, về bến đi
 * của nó. JUnit thuần, không DB: RouteService.reverseRoutesOf chỉ đọc các tuyến được đưa vào.
 */
class RouteServiceReverseRouteTest {

    private final Station saigon = station(3L, "Miền Đông");
    private final Station vungTau = station(12L, "Vũng Tàu");
    private final Station dalat = station(4L, "Đà Lạt");
    private final Station baoLoc = station(20L, "Bảo Lộc");

    @Test
    void theReverseRouteSwapsTheFirstAndLastStop_andOnlyThat() {
        Route out = route(10L, saigon, vungTau);
        Route back = route(11L, vungTau, saigon);
        Route sameDirection = route(30L, saigon, vungTau);
        Route other = route(2L, saigon, dalat);

        assertEquals(List.of(back), RouteService.reverseRoutesOf(out, List.of(out, back, sameDirection, other)));
        assertEquals(List.of(out, sameDirection), RouteService.reverseRoutesOf(back, List.of(out, back, sameDirection, other)),
                "hai tuyến cùng chiều Sài Gòn → Vũng Tàu: tuyến về có HAI tuyến ngược — người gọi báo mơ hồ, không đoán");
        assertTrue(RouteService.reverseRoutesOf(other, List.of(out, back, sameDirection, other)).isEmpty(),
                "Sài Gòn → Đà Lạt chưa có chiều về");
    }

    @Test
    void intermediateStopsDoNotMatter_onlyTheTwoEnds() {
        Route out = route(2L, saigon, baoLoc, dalat);
        Route back = route(12L, dalat, saigon);
        assertEquals(List.of(back), RouteService.reverseRoutesOf(out, List.of(out, back)));
        assertEquals(List.of(out), RouteService.reverseRoutesOf(back, List.of(out, back)));
    }

    @Test
    void aRouteWithoutStops_hasNoReverse_andIsNobodysReverse() {
        Route out = route(10L, saigon, vungTau);
        Route empty = route(99L);
        assertTrue(RouteService.reverseRoutesOf(empty, List.of(out, empty)).isEmpty());
        assertTrue(RouteService.reverseRoutesOf(out, List.of(out, empty)).isEmpty());
    }

    private static Station station(Long id, String name) {
        Station s = new Station();
        s.setId(id);
        s.setStationName(name);
        return s;
    }

    private static Route route(Long id, Station... stops) {
        Route r = new Route();
        r.setId(id);
        List<RouteStation> list = new ArrayList<>();
        for (int i = 0; i < stops.length; i++) {
            RouteStation rs = new RouteStation();
            rs.setRoute(r);
            rs.setStation(stops[i]);
            rs.setStopOrder(i + 1);
            list.add(rs);
        }
        r.setRouteStations(list);
        return r;
    }
}
