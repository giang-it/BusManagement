package giang.com.BusManagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.RouteStation;
import giang.com.BusManagement.domain.RouteStationId;

@Repository
public interface RouteStationRepository extends JpaRepository<RouteStation, RouteStationId> {
    // Tìm tất cả trạm của một tuyến, sắp xếp theo thứ tự dừng
    List<RouteStation> findByRouteIdOrderByStopOrderAsc(Long routeId);
}
