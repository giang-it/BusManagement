package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Bus;
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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

        // =========================================================================
        // TRUY VẤN CƠ BẢN
        // =========================================================================

        /** Tìm chuyến theo trạng thái */
        List<Trip> findByStatus(TripStatus status);

        /**
         * Tìm chuyến theo trạng thái với Route được load EAGER bằng JOIN FETCH.
         * Dùng cho scanAndSuggestExtraTrips() để tránh LazyInitializationException
         * khi truy cập trip.getRoute() bên ngoài session.
         */
        @Query("SELECT t FROM Trip t JOIN FETCH t.route WHERE t.status = :status")
        List<Trip> findByStatusWithRoute(@Param("status") TripStatus status);

        /** Đếm số chuyến theo trạng thái (dùng cho Dashboard) */
        long countByStatus(TripStatus status);

        // =========================================================================
        // TRUY VẤN VỚI JOIN FETCH (tránh N+1 problem)
        // =========================================================================

        /**
         * Lấy TẤT CẢ chuyến với đầy đủ relationships (route, bus, driver, assistant).
         * Dùng cho trang danh sách chuyến xe.
         */
        @Query("""
                        SELECT DISTINCT t FROM Trip t
                        LEFT JOIN FETCH t.route
                        LEFT JOIN FETCH t.bus b
                        LEFT JOIN FETCH b.busType
                        LEFT JOIN FETCH t.driver d
                        LEFT JOIN FETCH d.user
                        LEFT JOIN FETCH t.assistant a
                        LEFT JOIN FETCH a.user
                        """)
        List<Trip> findAllWithDetails();

        /**
         * Lấy chuyến theo trạng thái với đầy đủ relationships.
         * Dùng cho lọc và trang pending-trips.
         */
        @Query("""
                        SELECT DISTINCT t FROM Trip t
                        LEFT JOIN FETCH t.route
                        LEFT JOIN FETCH t.bus b
                        LEFT JOIN FETCH b.busType
                        LEFT JOIN FETCH t.driver d
                        LEFT JOIN FETCH d.user
                        LEFT JOIN FETCH t.assistant a
                        LEFT JOIN FETCH a.user
                        WHERE t.status = :status
                        """)
        List<Trip> findByStatusWithDetails(@Param("status") TripStatus status);

        // =========================================================================
        // KIỂM TRA TRÙNG LỊCH (dùng bởi AI auto-assign)
        // =========================================================================

        /**
         * Kiểm tra xem chuyến gốc đã có chuyến tăng cường với trạng thái cụ thể chưa.
         * Tránh tạo trùng chuyến tăng cường.
         */
        boolean existsByOriginalTripAndStatus(Trip originalTrip, TripStatus status);

        /**
         * Kiểm tra tài xế có đang bận (với tư cách TÀI XẾ CHÍNH) trong khoảng thời gian
         * không.
         * Bao gồm cả khoảng nghỉ 30 phút ở hai đầu (được thêm ở tầng service).
         */
        boolean existsByDriverAndStatusInAndDepartureTimeBetween(
                        Driver driver,
                        Collection<TripStatus> statuses,
                        LocalDateTime start,
                        LocalDateTime end);

        /**
         * [MỚI] Kiểm tra tài xế có đang bận (với tư cách PHỤ XE) trong khoảng thời gian
         * không.
         * Cần thiết để tránh gán một người vừa là phụ xe ở chuyến này
         * vừa là tài xế/phụ xe ở chuyến khác cùng khung giờ.
         */
        boolean existsByAssistantAndStatusInAndDepartureTimeBetween(
                        Driver assistant,
                        Collection<TripStatus> statuses,
                        LocalDateTime start,
                        LocalDateTime end);

        /**
         * [MỚI] Kiểm tra xe có đang được gán cho chuyến nào trong khoảng thời gian
         * không.
         * Bao gồm buffer chuẩn bị xe (được thêm ở tầng service).
         */
        boolean existsByBusAndStatusInAndDepartureTimeBetween(
                        Bus bus,
                        Collection<TripStatus> statuses,
                        LocalDateTime start,
                        LocalDateTime end);

        // =========================================================================
        // TRUY VẤN BỔ SUNG
        // =========================================================================

        /**
         * Tìm chuyến trong khoảng thời gian theo trạng thái (dùng cho AI quét nhu cầu
         * cao)
         */
        List<Trip> findByDepartureTimeBetweenAndStatus(
                        LocalDateTime start, LocalDateTime end, TripStatus status);
}