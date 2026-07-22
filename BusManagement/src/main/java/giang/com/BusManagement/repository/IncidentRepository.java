package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import giang.com.BusManagement.domain.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * PHASE 7 — số sự cố của từng xe, tách theo loại: (busId, incidentType, số lượng).
     *
     * Đây là đường ĐỌC đầu tiên của dữ liệu sự cố (xem THESIS_ROADMAP.md, Hidden
     * Cost #6): tới trước Phase 7, `incidents` chỉ được ghi vào chứ chưa nuôi
     * quyết định vận hành nào. Đây cũng là thứ khiến việc bắt buộc `Incident.bus`
     * từ Phase 2 trở nên đáng giá.
     *
     * Danh sách loại được TRUYỀN VÀO chứ không cố định trong câu truy vấn: quyết
     * định "loại sự cố nào nói lên tình trạng chiếc xe" là quy tắc nghiệp vụ,
     * thuộc về tầng service, không thuộc về repository.
     *
     * Trả về theo loại (thay vì chỉ tổng) để màn hình hiển thị được cơ cấu
     * "mấy lần hỏng hóc / mấy lần tai nạn" — hai chuyện khác nhau với người đọc,
     * dù cùng cộng vào một điểm số.
     *
     * Chỉ lấy 3 cột vô hướng, không nạp entity — cùng lý do với
     * TripRepository.findDemandHistoryByStatus.
     */
    @Query("SELECT i.bus.id, i.incidentType, COUNT(i) FROM Incident i "
            + "WHERE i.incidentType IN :types GROUP BY i.bus.id, i.incidentType")
    List<Object[]> countIncidentsPerBusAndType(@Param("types") Collection<IncidentType> types);
}
