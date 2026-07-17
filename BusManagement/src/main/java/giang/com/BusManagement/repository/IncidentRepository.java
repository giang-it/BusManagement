package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /**
     * Danh sách sự cố kèm đầy đủ quan hệ để hiển thị, mới nhất trước.
     *
     * Không JOIN FETCH collection nào (Trip.coDrivers, Route.routeStations) —
     * màn hình sự cố không cần tới chúng, và fetch 2 collection kiểu List cùng
     * lúc sẽ gây MultipleBagFetchException.
     */
    @Query("""
            SELECT DISTINCT i FROM Incident i
            LEFT JOIN FETCH i.bus b
            LEFT JOIN FETCH b.busType
            LEFT JOIN FETCH i.trip t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH i.driver d
            LEFT JOIN FETCH d.user
            ORDER BY i.reportedAt DESC
            """)
    List<Incident> findAllWithDetails();

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.bus b
            LEFT JOIN FETCH b.busType
            LEFT JOIN FETCH i.trip t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH i.driver d
            LEFT JOIN FETCH d.user
            WHERE i.id = :id
            """)
    Optional<Incident> findByIdWithDetails(Long id);

    /** Dùng cho dải thống kê nhỏ trên trang danh sách. */
    long countByStatus(IncidentStatus status);

    /**
     * Xe này có bản ghi sự cố nào không — dùng để chặn xóa cứng xe ở BusService,
     * cùng nguyên tắc với TripRepository.existsByBusId().
     */
    boolean existsByBusId(Long busId);

    /**
     * Tài xế này có bản ghi sự cố nào không — dùng để chặn xóa cứng tài xế ở
     * DriverService. Driver dùng @MapsId nên khóa chính là field userId.
     */
    boolean existsByDriverUserId(Long userId);
}
