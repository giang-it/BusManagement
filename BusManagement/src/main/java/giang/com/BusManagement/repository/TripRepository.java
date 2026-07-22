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
        * Dùng toán tử so sánh chặt (</>), khớp với existsOverlappingTripForDriver()/
        * existsOverlappingTripForAssistant() — hai khoảng thời gian chỉ được coi là
        * "trùng" khi thực sự giao nhau; chạm đúng biên (VD: chuyến khác khởi hành
        * đúng lúc buffer của chuyến này kết thúc) là đủ khoảng đệm, không bị chặn.
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t WHERE t.bus = :bus AND t.status IN :statuses " +
                     "AND t.departureTime < :end AND t.arrivalTimeExpected > :start " +
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
                     "   OR cd.userId = :driverId) " +
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
                     "   OR cd.userId = :driverId) " +
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
        * Kiểm tra tuyến đã từng được dùng cho bất kỳ chuyến đi nào chưa.
        * Dùng bởi RouteService.deleteRoute() để chặn xóa tuyến đã có lịch sử vận
        * hành — cùng nguyên tắc với existsByBusId() ở BusService.deleteBus().
        */
       boolean existsByRouteId(Long routeId);

       /**
        * Kiểm tra tài xế đã từng tham gia BẤT KỲ chuyến nào chưa (mọi vai trò: tài
        * xế chính, tài xế phụ, phụ xe; mọi trạng thái).
        * Dùng bởi DriverService.deleteDriver() để chặn xóa hồ sơ đã có lịch sử vận
        * hành — cùng nguyên tắc với existsByBusId() ở BusService.deleteBus().
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t " +
                     "LEFT JOIN t.coDrivers cd " +
                     "WHERE t.driver.userId = :driverId " +
                     "   OR t.assistant.userId = :driverId " +
                     "   OR cd.userId = :driverId")
       boolean existsAnyTripForDriver(@Param("driverId") Long driverId);

       /**
        * Kiểm tra tài xế có chuyến nào đang ở một trong các trạng thái cho trước
        * hay không (mọi vai trò). Dùng bởi DriverService để chặn khóa
        * (isActive=false) một tài xế còn chuyến dở dang.
        */
       @Query("SELECT COUNT(t) > 0 FROM Trip t " +
                     "LEFT JOIN t.coDrivers cd " +
                     "WHERE (t.driver.userId = :driverId " +
                     "   OR t.assistant.userId = :driverId " +
                     "   OR cd.userId = :driverId) " +
                     "AND t.status IN :statuses")
       boolean existsTripForDriverWithStatusIn(@Param("driverId") Long driverId,
                     @Param("statuses") Collection<TripStatus> statuses);

       /**
        * Lấy các chuyến cho bảng điều hành (Dispatch Center): mọi chuyến đang chạy
        * (DEPARTED) và các chuyến sắp khởi hành trong cửa sổ thời gian cho trước.
        * JOIN FETCH đầy đủ quan hệ để view hiển thị được xe/tài xế/tuyến mà không
        * phụ thuộc open-in-view.
        *
        * Chỉ JOIN FETCH duy nhất 1 collection kiểu List (t.coDrivers) — thêm
        * r.routeStations sẽ gây MultipleBagFetchException; quan hệ đó đã dùng
        * @BatchSize(20) để tránh N+1 (xem Route.java).
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
                     WHERE t.status IN :statuses
                       AND t.departureTime <= :until
                     """)
       List<Trip> findDispatchBoardTrips(@Param("statuses") Collection<TripStatus> statuses,
                     @Param("until") LocalDateTime until);

       /**
        * Kiểm tra đã tồn tại chuyến của tuyến này khởi hành ĐÚNG mốc thời gian này
        * hay chưa.
        *
        * Dùng bởi HistoricalDataBackfill để chạy lại được nhiều lần mà không nhân
        * bản dữ liệu: mỗi ô (tuyến × mốc khởi hành) của bộ dữ liệu lịch sử là duy
        * nhất và sinh ra một cách tất định, nên chỉ cần hỏi đúng câu này là biết ô
        * đó đã được backfill hay chưa — không cần thêm cột đánh dấu vào Trip.
        */
       boolean existsByRouteIdAndDepartureTime(Long routeId, LocalDateTime departureTime);

       /**
        * Kiểm tra xe có đang bận ở các chuyến đi có trạng thái nằm trong
        * danh sách chỉ định hay không
        * Thường dùng để chặn không cho xe đi bảo trì (REPAIRING) khi đang có lịch
        * ACTIVE hoặc PENDING_APPROVAL
        */
       boolean existsByBusIdAndStatusIn(Long busId, List<TripStatus> statuses);

       // =========================================================================
       // TRUY VẤN CHO DASHBOARD (Strategic Analytics / Operational KPIs)
       // =========================================================================

       /**
        * Đếm chuyến tăng cường (isExtraTrip = true) theo từng TripStatus.
        * Dùng để dựng cả OperationalKpisDto.aiSuggestionsPendingCount (lấy bucket
        * PENDING_APPROVAL) lẫn AiStatsDto (activated/rejected/pending) từ CÙNG một
        * truy vấn, tránh gọi lặp lại 2 lần cho cùng một câu hỏi.
        */
       @Query("SELECT t.status, COUNT(t) FROM Trip t WHERE t.isExtraTrip = true GROUP BY t.status")
       List<Object[]> countAiSuggestionsByStatus();

       /**
        * Lấy (totalSeats, ticketsSold) của từng chuyến theo trạng thái — dùng để
        * tính occupancy rate trung bình và phân bố theo bucket ở tab Occupancy.
        * Chỉ lấy 2 cột cần thiết, không JOIN FETCH quan hệ nào, tránh N+1/over-fetch
        * cho một phép tính thuần số.
        */
       @Query("SELECT t.totalSeats, t.ticketsSold FROM Trip t WHERE t.status = :status")
       List<Object[]> findSeatsAndSoldByStatus(@Param("status") TripStatus status);

       /**
        * Tổng số vé đã bán và tổng doanh thu (price * ticketsSold) trên các chuyến
        * có trạng thái nằm trong danh sách cho trước. SUM không kèm GROUP BY luôn
        * trả về đúng 1 dòng (kể cả khi không có chuyến nào khớp — khi đó các cột
        * SUM sẽ là null). Khai báo trả về Object[] trực tiếp (thay vì
        * List<Object[]>) bị Spring Data hiểu nhầm thành "ép toàn bộ result list
        * thành mảng" chứ không phải "1 dòng kết quả" — nên phải dùng
        * List<Object[]> rồi lấy phần tử đầu tiên ở tầng service.
        */
       @Query("SELECT SUM(t.ticketsSold), SUM(t.price * t.ticketsSold) FROM Trip t WHERE t.status IN :statuses")
       List<Object[]> sumTicketsSoldAndRevenueByStatuses(@Param("statuses") Collection<TripStatus> statuses);

       /**
        * Số chuyến và doanh thu theo từng tuyến, giới hạn trong các trạng thái
        * cho trước — dùng cho bảng xếp hạng tuyến đường (RouteStatsDto.topRoutes).
        */
       @Query("SELECT t.route.id, COUNT(t), SUM(t.price * t.ticketsSold) FROM Trip t " +
                     "WHERE t.status IN :statuses GROUP BY t.route.id")
       List<Object[]> aggregateByRoute(@Param("statuses") Collection<TripStatus> statuses);

       /**
        * Danh sách routeId đã từng xuất hiện trong ít nhất 1 chuyến (bất kỳ trạng
        * thái nào) — dùng để tính routesWithNoTrips = totalRoutes - kích thước danh
        * sách này.
        */
       @Query("SELECT DISTINCT t.route.id FROM Trip t")
       List<Long> findDistinctRouteIdsWithTrips();

       /**
        * PHASE 6 — chuỗi nhu cầu lịch sử cho Demand Forecast:
        * (routeId, departureTime, ticketsSold, totalSeats) của các chuyến ở MỘT
        * trạng thái, sắp theo thời gian khởi hành.
        *
        * Chỉ lấy 4 cột và không JOIN FETCH gì — cùng lý do đã ghi ở
        * findSeatsAndSoldByStatus: đây là phép tính thuần số trên hàng nghìn dòng.
        * Nạp hẳn entity Trip sẽ over-fetch đúng vào điểm yếu đã biết của hệ thống
        * (THESIS_ROADMAP.md, Hidden Cost #4: aggregation in-memory trên entity
        * trong DashboardService).
        *
        * totalSeats > 0 được lọc ngay ở DB để tầng service không phải phòng thủ
        * phép chia cho 0 khi tính tỉ lệ lấp đầy. Chuyến đã soft-delete tự động bị
        * loại nhờ @SQLRestriction trên Trip.
        *
        * Trục thời gian là departureTime, KHÔNG phải createdAt — xem
        * THESIS_ROADMAP.md, Developer Notes ("Why Demand Forecast keys on
        * Trip.departureTime").
        */
       @Query("SELECT t.route.id, t.departureTime, t.ticketsSold, t.totalSeats FROM Trip t " +
                     "WHERE t.status = :status AND t.totalSeats > 0 ORDER BY t.departureTime")
       List<Object[]> findDemandHistoryByStatus(@Param("status") TripStatus status);
}