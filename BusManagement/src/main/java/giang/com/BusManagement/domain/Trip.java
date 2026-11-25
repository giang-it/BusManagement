package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Soft-delete design:
 *   - @SQLDelete : mọi lệnh JPA delete() sẽ chạy UPDATE trips SET is_deleted=true
 *                  thay vì DELETE vật lý, bảo toàn toàn vẹn FK và lịch sử giao dịch.
 *   - @SQLRestriction: Hibernate tự động thêm WHERE is_deleted=false vào MỌI câu
 *                  SELECT, khiến các chuyến đã xóa vô hình với toàn bộ query layer.
 */
@SQLDelete(sql = "UPDATE trips SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "trips")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Các association bên dưới đều LAZY và được @ToString.Exclude: log một Trip
    // không kéo cả route/bus/driver/coDrivers... và không lazy-load. Chúng cũng
    // không nằm trong equals/hashCode (chỉ id được Include). Xem quy ước ở
    // User.driver.

    // Liên kết với Route
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // Xe được gán
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    // Tài xế chính
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    // === PHỤ XE (mới thêm) ===
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_id")
    private Driver assistant; // Phụ xe/Conductor (cũng là Driver nhưng vai trò khác)

    // === TÀI XẾ PHỤ (mới thêm để hỗ trợ chuyến đi > 8h) ===
    @ToString.Exclude
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "trip_co_drivers", joinColumns = @JoinColumn(name = "trip_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private java.util.List<Driver> coDrivers = new java.util.ArrayList<>();

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    @Column(name = "arrival_time_expected")
    private LocalDateTime arrivalTimeExpected;

    @Column(name = "total_seats")
    private int totalSeats;

    @Column(name = "tickets_sold", columnDefinition = "INT DEFAULT 0")
    private int ticketsSold = 0;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TripStatus status = TripStatus.PENDING_APPROVAL;

    // Self-reference: Chuyến gốc (nếu đây là chuyến tăng cường)
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_trip_id")
    private Trip originalTrip;

    @Column(name = "is_extra_trip", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isExtraTrip = false;

    @Column(name = "sale_opened_at")
    private LocalDateTime saleOpenedAt;

    /**
     * Thời điểm bản ghi chuyến được tạo. Hibernate tự đóng dấu khi INSERT
     * (@CreationTimestamp), không bao giờ set thủ công và không đổi khi UPDATE
     * (updatable = false).
     *
     * Khác với saleOpenedAt (mốc nghiệp vụ — thời điểm mở bán vé, do FSM đóng
     * dấu khi chuyển sang ACTIVE): createdAt là mốc kỹ thuật (thời điểm bản ghi
     * được insert), phục vụ audit và các phân tích dựa trên tốc độ đặt vé
     * (booking velocity) sau này. KHÔNG dùng làm trục thời gian cho Demand
     * Forecast (Phase 6) — trục đó là departureTime, vì backfill dữ liệu lịch
     * sử (Phase 5) không thể set createdAt về quá khứ (@CreationTimestamp
     * luôn đóng dấu lúc INSERT, không phụ thuộc giá trị gán thủ công).
     *
     * LƯU Ý: các chuyến đã tồn tại trong DB trước khi cột này được thêm sẽ có
     * giá trị NULL (@CreationTimestamp chỉ điền lúc INSERT). Nơi nào đọc field
     * này phải xử lý null.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Cờ xóa mềm. TRUE = đã bị xóa (ẩn khỏi mọi query do @SQLRestriction).
     * Không bao giờ nên được đặt trực tiếp — luôn dùng tripRepository.delete(trip)
     * hoặc TripService.deleteTrip() để kích hoạt @SQLDelete.
     */
    @Column(name = "is_deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isDeleted = false;

    // === HELPER METHODS ===

    // Tính tỉ lệ lấp đầy ghế
    public double getOccupancyRate() {
        if (totalSeats == 0)
            return 0;
        return (double) ticketsSold / totalSeats;
    }

    // Kiểm tra có cần tăng cường không (> 90% ghế)
    public boolean needsReinforcement() {
        return exceedsReinforcementThreshold(ticketsSold, totalSeats);
    }

    // Tính thời gian chuyến (giờ)
    public double getTripDurationHours() {
        if (departureTime == null || arrivalTimeExpected == null)
            return 0;
        return java.time.Duration.between(departureTime, arrivalTimeExpected).toMinutes() / 60.0;
    }

    /** Hours the trip has been open for sale (0 if not yet opened). */
    public long getHoursOnSale() {
        if (saleOpenedAt == null)
            return 0;
        return java.time.Duration.between(saleOpenedAt, LocalDateTime.now()).toHours();
    }

    /** Hours until departure from now (negative = already departed). */
    public long getHoursUntilDeparture() {
        if (departureTime == null)
            return 0;
        return java.time.Duration.between(LocalDateTime.now(), departureTime).toHours();
    }

    /**
     * Ngưỡng "cần tăng cường" (> 90% ghế) — định nghĩa DUY NHẤT, cho một chuyến (needsReinforcement) và cho
     * cả một khung (job 10 giây từ route pairs CT8c: tổng vé ÷ tổng ghế của mọi chuyến đang bán trong khung).
     */
    public static boolean exceedsReinforcementThreshold(int sold, int seats) {
        return seats > 0 && (double) sold / seats > 0.90;
    }

    /**
     * Vòng tăng cường do job đêm tạo từ dự báo (route pairs CT8b, CM-26 cách K1): id của chuyến thêm của
     * vòng — chuyến thêm trỏ vào chính nó, lượt đi kèm (về ngay / đưa xe tới) trỏ vào chuyến thêm. Null =
     * chuyến do người, lịch thường lệ hay job 10 giây tạo. Cột cho phép null, thêm bằng ddl-auto=update; đặt
     * ở cuối lớp để không xê dịch số dòng mà các bài học đang trích.
     */
    @jakarta.persistence.Column(name = "forecast_loop_id")
    private Long forecastLoopId;

}