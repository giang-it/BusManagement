package giang.com.BusManagement.service;

import giang.com.BusManagement.domain.HolidayFlow;
import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.repository.RouteRepository;
import giang.com.BusManagement.repository.RouteStationRepository;
import giang.com.BusManagement.repository.StationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * QĐ-8 (docs/development/forecast_decisions.md) — Route.holidayFlow, hướng dòng
 * khách dịp lễ do Admin chọn ở form tuyến.
 *
 * Điều cần chốt nhất là đường CẬP NHẬT: RouteService.updateRoute() chép từng field
 * của form sang bản ghi cũ, nên một field mới mà quên chép sẽ mất im lặng mỗi lần
 * Admin sửa tuyến — cùng kiểu lỗi #16. Và "-- Không theo chiều --" (null) là ý định
 * hợp lệ: sửa về null phải XOÁ nhãn cũ, không phải giữ nguyên.
 *
 * Non-vacuous: bỏ dòng existing.setHolidayFlow(...) trong updateRoute() là hai test
 * cập nhật đỏ (đã probe khi viết).
 *
 * Chạy trên busmanagement_test (Phase 0), @Transactional nên rollback sau từng test.
 */
@SpringBootTest
@Transactional
class RouteServiceHolidayFlowTest {

    @Autowired private RouteService routeService;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteStationRepository routeStationRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private EntityManager em;

    private Route route;
    private List<Long> stops;

    @BeforeEach
    void setUp() {
        Station a = saveStation("Bến Lễ A");
        Station b = saveStation("Bến Lễ B");
        route = new Route();
        route.setDistanceKm(120.0);
        route.setEstimatedDuration(120);
        route = routeRepository.save(route);
        addStop(route, a, 1);
        addStop(route, b, 2);
        stops = List.of(a.getId(), b.getId());
        flushAndClear();
    }

    @Test
    void createRoute_savesTheChosenHolidayFlow() {
        Route posted = form(HolidayFlow.TOWARD_HUB);

        routeService.createRoute(posted, stops);

        assertNotNull(posted.getId());
        flushAndClear();
        assertEquals(HolidayFlow.TOWARD_HUB, routeRepository.findById(posted.getId()).orElseThrow().getHolidayFlow());
    }

    @Test
    void updateRoute_copiesTheHolidayFlowFromTheForm() {
        routeService.updateRoute(route.getId(), form(HolidayFlow.LEAVING_HUB), stops);

        flushAndClear();
        assertEquals(HolidayFlow.LEAVING_HUB, routeRepository.findById(route.getId()).orElseThrow().getHolidayFlow());
    }

    /**
     * "-- Không theo chiều --" là ý định hợp lệ, như "-- Không giới hạn --" của loại xe.
     * Nhãn ban đầu được ghi thẳng qua repository, không qua updateRoute(): nếu không,
     * khi updateRoute() quên chép field thì cả hai lần cập nhật đều không ghi gì và
     * test vẫn xanh.
     */
    @Test
    void updateRoute_withNoFlowChosen_clearsTheOldValue_insteadOfKeepingIt() {
        storeHolidayFlow(HolidayFlow.LEAVING_HUB);

        routeService.updateRoute(route.getId(), form(null), stops);

        flushAndClear();
        assertNull(routeRepository.findById(route.getId()).orElseThrow().getHolidayFlow());
    }

    /** Cột lưu tên hằng tiếng Anh; chữ tiếng Việt chỉ nằm trên giao diện. */
    @Test
    void holidayFlow_isStoredAsTheEnglishConstantName() {
        storeHolidayFlow(HolidayFlow.LEAVING_HUB);

        Object stored = em.createNativeQuery("SELECT holiday_flow FROM routes WHERE id = :id")
                .setParameter("id", route.getId())
                .getSingleResult();
        assertEquals("LEAVING_HUB", stored);
    }

    // =====================================================================

    private Route form(HolidayFlow holidayFlow) {
        Route form = new Route();
        form.setDistanceKm(120.0);
        form.setEstimatedDuration(120);
        form.setHolidayFlow(holidayFlow);
        return form;
    }

    private void storeHolidayFlow(HolidayFlow holidayFlow) {
        Route stored = routeRepository.findById(route.getId()).orElseThrow();
        stored.setHolidayFlow(holidayFlow);
        routeRepository.save(stored);
        flushAndClear();
    }

    private Station saveStation(String name) {
        Station s = new Station();
        s.setStationName(name);
        return stationRepository.save(s);
    }

    private void addStop(Route r, Station s, int order) {
        RouteStation rs = new RouteStation();
        rs.setId(new RouteStationId(r.getId(), s.getId()));
        rs.setRoute(r);
        rs.setStation(s);
        rs.setStopOrder(order);
        routeStationRepository.save(rs);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
