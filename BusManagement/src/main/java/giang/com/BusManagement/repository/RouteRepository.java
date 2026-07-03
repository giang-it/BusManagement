package giang.com.BusManagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Route;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    /**
     * Nạp toàn bộ Route kèm routeStations + station tương ứng trong 1 query.
     * BẮT BUỘC dùng hàm này (thay cho findAll()) ở bất cứ nơi nào cần hiển thị
     * route.getDeparturePointDisplay() / route.getDestinationPointDisplay(),
     * vì routeStations là quan hệ LAZY — gọi ngoài transaction (ví dụ trong
     * Thymeleaf) sẽ ném LazyInitializationException nếu không JOIN FETCH sẵn.
     */
    @Query("SELECT DISTINCT r FROM Route r " +
            "LEFT JOIN FETCH r.routeStations rs " +
            "LEFT JOIN FETCH rs.station " +
            "LEFT JOIN FETCH r.suitableBusType")
    List<Route> findAllWithStations();

    @Query("SELECT r FROM Route r " +
            "LEFT JOIN FETCH r.routeStations rs " +
            "LEFT JOIN FETCH rs.station " +
            "LEFT JOIN FETCH r.suitableBusType " +
            "WHERE r.id = :id")
    Optional<Route> findByIdWithStations(Long id);
}