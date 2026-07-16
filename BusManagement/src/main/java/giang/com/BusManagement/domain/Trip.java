package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
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
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết với Route
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // Xe được gán
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    // Tài xế chính
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    // === PHỤ XE (mới thêm) ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_id")
    private Driver assistant; // Phụ xe/Conductor (cũng là Driver nhưng vai trò khác)

    // === TÀI XẾ PHỤ (mới thêm để hỗ trợ chuyến đi > 8h) ===
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
     * dấu khi chuyển sang ACTIVE): createdAt là mốc kỹ thuật, dùng làm nền cho
     * dữ liệu lịch sử/phân tích xu hướng sau này.
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
        return getOccupancyRate() > 0.90;
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

}