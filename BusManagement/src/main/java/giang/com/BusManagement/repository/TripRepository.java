package giang.com.BusManagement.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // Tìm các chuyến xe khởi hành trong khoảng thời gian tới
    List<Trip> findByDepartureTimeBetweenAndStatus(LocalDateTime start, LocalDateTime end, TripStatus status);

    // Kiểm tra xem đã có bản sao tăng cường nào cho chuyến này chưa
    boolean existsByOriginalTripAndStatus(Trip originalTrip, TripStatus status);
}
