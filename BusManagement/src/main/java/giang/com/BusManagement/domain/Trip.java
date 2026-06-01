package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
}