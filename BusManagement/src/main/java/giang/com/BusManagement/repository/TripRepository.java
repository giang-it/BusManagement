package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // Tìm chuyến theo trạng thái (Dùng cho getPendingTrips)
    List<Trip> findByStatus(TripStatus status);

    // === THÊM MỚI: Query với JOIN FETCH để load relationships ===

    /**
     * Lấy tất cả trips với relationships đã được load sẵn (tránh N+1 problem)
     */
    @Query("SELECT DISTINCT t FROM Trip t " +
            "LEFT JOIN FETCH t.route r " +
            "LEFT JOIN FETCH t.bus b " +
            "LEFT JOIN FETCH b.busType " +
            "LEFT JOIN FETCH t.driver d " +
            "LEFT JOIN FETCH d.user " +
            "LEFT JOIN FETCH t.assistant a " +
            "LEFT JOIN FETCH a.user")
    List<Trip> findAllWithDetails();

    /**
     * Lấy trips theo status với relationships đã được load sẵn
     */
    @Query("SELECT DISTINCT t FROM Trip t " +
            "LEFT JOIN FETCH t.route r " +
            "LEFT JOIN FETCH t.bus b " +
            "LEFT JOIN FETCH b.busType " +
            "LEFT JOIN FETCH t.driver d " +
            "LEFT JOIN FETCH d.user " +
            "LEFT JOIN FETCH t.assistant a " +
            "LEFT JOIN FETCH a.user " +
            "WHERE t.status = :status")
    List<Trip> findByStatusWithDetails(@Param("status") TripStatus status);

    // === GIỮ NGUYÊN CÁC QUERY CŨ ===

    // Tìm các chuyến trong khoảng thời gian (Dùng cho AI quét nhu cầu cao)
    List<Trip> findByDepartureTimeBetweenAndStatus(LocalDateTime start, LocalDateTime end, TripStatus status);

    // Kiểm tra xem chuyến gốc đã có bản ghi tăng cường nào đang chờ duyệt chưa
    boolean existsByOriginalTripAndStatus(Trip originalTrip, TripStatus status);

    // Kiểm tra tài xế có đang bận trong khung giờ cụ thể không
    boolean existsByDriverAndStatusInAndDepartureTimeBetween(
            Driver driver,
            Collection<TripStatus> statuses,
            LocalDateTime start,
            LocalDateTime end);

    long countByStatus(TripStatus status);
}