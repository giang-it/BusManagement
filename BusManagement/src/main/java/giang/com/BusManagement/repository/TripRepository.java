package giang.com.BusManagement.repository;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
                     LEFT JOIN FETCH t.route r
                     LEFT JOIN FETCH t.bus b
                     LEFT JOIN FETCH b.busType
                     LEFT JOIN FETCH t.driver d
                     LEFT JOIN FETCH d.user
                     LEFT JOIN FETCH t.assistant a
                     LEFT JOIN FETCH a.user
                     LEFT JOIN FETCH t.coDrivers cd
                     """)
       List<Trip> findAllWithDetails();

       /**
        * Lấy chuyến theo trạng thái với đầy đủ relationships.
        * Dùng cho lọc và trang pending-trips.
        */
       @Query("""
                     SELECT DISTINCT t FROM Trip t
                     LEFT JOIN FETCH t.route r
                     LEFT JOIN FETCH t.bus b
                     LEFT JOIN FETCH b.busType
                     LEFT JOIN FETCH t.driver d
                     LEFT JOIN FETCH d.user
                     LEFT JOIN FETCH t.assistant a
                     LEFT JOIN FETCH a.user
                     LEFT JOIN FETCH t.coDrivers cd
                     WHERE t.status = :status
                     """)
       List<Trip> findByStatusWithDetails(@Param("status") TripStatus status);

       @Query("""
                     SELECT DISTINCT t FROM Trip t
                     LEFT JOIN FETCH t.route r
                     LEFT JOIN FETCH t.bus b
                     LEFT JOIN FETCH b.busType
                     LEFT JOIN FETCH t.driver d
                     LEFT JOIN FETCH d.user
                     LEFT JOIN FETCH t.assistant a
                     LEFT JOIN FETCH a.user
                     LEFT JOIN FETCH t.coDrivers cd
                     WHERE t.id = :id
                     """)
       java.util.Optional<Trip> findByIdWithDetails(@Param("id") Long id);

       // @Query("SELECT COUNT(t) FROM Trip t WHERE " +
       // "(t.driver.user.id = :userId OR :userId MEMBER OF t.coDrivers) " +
       // "AND t.id != :currentTripId " +
       // "AND t.status != 'CANCELLED' " +
       // "AND ((t.departureTime < :end AND t.arrivalTimeExpected > :start))")
       // long countBusyTripsByUserId(@Param("userId") Long userId,
       // @Param("currentTripId") Long currentTripId,
       // @Param("start") LocalDateTime start,
       // @Param("end") LocalDateTime end);

       // =========================================================================
       // KIỂM TRA TRÙNG LỊCH (dùng bởi AI auto-assign)
       // =========================================================================

       /**
        * Kiểm tra xem chuyến gốc đã có chuyến tăng cường với BẤT KỲ trạng thái nào
        * trong danh sách cho trước.
        * Dùng SQL IN clause — 1 truy vấn duy nhất thay vì nhiều lần gọi OR.
        * Dùng bởi hasAlreadySuggested() để tránh tạo trùng chuyến tăng cường.
        */
       boolean existsByOriginalTripAndStatusIn(Trip originalTrip, Collection<TripStatus> statuses);

       /**
        * Kiểm tra tài xế có đang bận (với tư cách TÀI XẾ CHÍNH hoặc TÀI XẾ PHỤ) trong
        * khoảng thời gian không.
        * Bao gồm cả khoảng nghỉ 30 phút ở hai đầu (được thêm ở tầng service).
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t " +
                     "LEFT JOIN t.coDrivers cd " +
                     "WHERE (t.driver = :driver OR cd = :driver) " +
                     "AND t.status IN :statuses " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) " +
                     "AND (t.departureTime < :windowEnd AND t.arrivalTimeExpected > :windowStart)")
       boolean existsOverlappingTripForDriver(
                     @Param("driver") Driver driver,
                     @Param("statuses") List<TripStatus> statuses,
                     @Param("windowStart") LocalDateTime windowStart,
                     @Param("windowEnd") LocalDateTime windowEnd,
                     @Param("excludeTripId") Long excludeTripId);

       /**
        * [MỚI] Kiểm tra tài xế có đang bận (với tư cách PHỤ XE) trong khoảng thời gian
        * không.
        * Cần thiết để tránh gán một người vừa là phụ xe ở chuyến này
        * vừa là tài xế/phụ xe ở chuyến khác cùng khung giờ.
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t " +
                     "WHERE t.assistant = :driver " +
                     "AND t.status IN :statuses " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) " +
                     "AND (t.departureTime < :windowEnd AND t.arrivalTimeExpected > :windowStart)")
       boolean existsOverlappingTripForAssistant(
                     @Param("driver") Driver driver,
                     @Param("statuses") List<TripStatus> statuses,
                     @Param("windowStart") LocalDateTime windowStart,
                     @Param("windowEnd") LocalDateTime windowEnd,
                     @Param("excludeTripId") Long excludeTripId);

       /**
        * [MỚI] Kiểm tra xe có đang được gán cho chuyến nào trong khoảng thời gian
        * không.
        * Bao gồm buffer chuẩn bị xe (được thêm ở tầng service).
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t WHERE t.bus = :bus AND t.status IN :statuses " +
                     "AND t.departureTime <= :end AND t.arrivalTimeExpected >= :start " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId)")
       boolean existsOverlappingTripForBus(
                     @Param("bus") Bus bus,
                     @Param("statuses") Collection<TripStatus> statuses,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end,
                     @Param("excludeTripId") Long excludeTripId);

       /**
        * [MỚI] Tìm các chuyến xe của một tài xế/phụ xe trong một ngày cụ thể (để tính
        * tổng giờ lái/ngày)
        * Kiểm tra xem tài xế có lái (chính hoặc phụ) hay là phụ xe trong chuyến đó.
        */
       @Query("SELECT DISTINCT t FROM Trip t " +
                     "LEFT JOIN FETCH t.coDrivers cd " +
                     "WHERE (t.driver = :driver OR cd = :driver OR t.assistant = :driver) " +
                     "AND t.status IN :statuses " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) " +
                     "AND (t.departureTime >= :startOfDay AND t.departureTime < :endOfDay)")
       List<Trip> findTripsForDriverOnDate(
                     @Param("driver") Driver driver,
                     @Param("statuses") List<TripStatus> statuses,
                     @Param("startOfDay") LocalDateTime startOfDay,
                     @Param("endOfDay") LocalDateTime endOfDay,
                     @Param("excludeTripId") Long excludeTripId);

       // =========================================================================
       // TRUY VẤN BỔ SUNG
       // =========================================================================

       /**
        * Tìm chuyến trong khoảng thời gian theo trạng thái (dùng cho AI quét nhu cầu
        * cao)
        */
       List<Trip> findByDepartureTimeBetweenAndStatus(
                     LocalDateTime start, LocalDateTime end, TripStatus status);

       // 1. Kiểm tra xem tài xế có bận bất kỳ vai trò nào (Lái chính, Lái phụ, Phụ xe)
       // trong khung giờ này không
       @Query("SELECT COUNT(t) FROM Trip t " +
                     "LEFT JOIN t.coDrivers cd " +
                     "WHERE (t.driver.userId = :driverId " +
                     "   OR t.assistant.userId = :driverId " +
                     "   OR cd.id = :driverId) " +
                     "AND t.status <> 'CANCELLED' " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) " +
                     "AND (t.departureTime < :end AND t.arrivalTimeExpected > :start)")
       Long countBusyTripsAnyRole(@Param("driverId") Long driverId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end,
                     @Param("excludeTripId") Long excludeTripId);

       // 2. Lấy TẤT CẢ các chuyến xe mà tài xế tham gia trong ngày (ở mọi vai trò) để
       // tính tổng giờ làm việc
       @Query("SELECT DISTINCT t FROM Trip t " +
                     "LEFT JOIN t.coDrivers cd " +
                     "WHERE (t.driver.userId = :driverId " +
                     "   OR t.assistant.userId = :driverId " +
                     "   OR cd.id = :driverId) " +
                     "AND t.status <> 'CANCELLED' " +
                     "AND (:excludeTripId IS NULL OR t.id <> :excludeTripId) " +
                     "AND (t.departureTime >= :start AND t.departureTime <= :end)")
       List<Trip> findAllTripsByDriverOnDate(@Param("driverId") Long driverId,
                     @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end,
                     @Param("excludeTripId") Long excludeTripId);

       /**
        * Kiểm tra xe đã từng được gán cho bất kỳ chuyến đi nào trong hệ
        * thống chưa (quá khứ + tương lai)
        * Spring Data JPA tự động phân tích: BusId -> truy cập vào trường bus và lấy
        * thuộc tính id của Bus entity.
        */
       boolean existsByBusId(Long busId);

       /**
        * Kiểm tra xe có đang bận ở các chuyến đi có trạng thái nằm trong
        * danh sách chỉ định hay không
        * Thường dùng để chặn không cho xe đi bảo trì (REPAIRING) khi đang có lịch
        * ACTIVE hoặc PENDING_APPROVAL
        */
       boolean existsByBusIdAndStatusIn(Long busId, List<TripStatus> statuses);
}