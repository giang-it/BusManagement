package giang.com.BusManagement.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // Tìm chuyến theo trạng thái (Dùng cho getPendingTrips)
    List<Trip> findByStatus(TripStatus status);

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