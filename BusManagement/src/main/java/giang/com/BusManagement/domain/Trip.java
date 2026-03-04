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

    // 1. Thay vì String, ta liên kết trực tiếp với bảng Route
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // 2. Phải có Xe và Tài xế để kiểm tra ràng buộc (như yêu cầu I.2)
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "bus_id")
    // private Bus bus;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "driver_id")
    // private Driver driver;

    private LocalDateTime departureTime;

    // Cần thiết để AI tính toán giờ nghỉ cho tài xế
    private LocalDateTime arrivalTimeExpected;

    private int totalSeats;
    private int ticketsSold;
    private BigDecimal price; // Dùng BigDecimal cho tiền tệ

    // 3. Sử dụng Enum thay cho String để tránh lỗi typo (ACTIVE vs active)
    @Enumerated(EnumType.STRING)
    private TripStatus status;

    // 4. Self-reference: Liên kết trực tiếp tới chính object Trip gốc
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_trip_id")
    private Trip originalTrip;

    private boolean isExtraTrip = false;

    // Helper method để tính tỉ lệ lấp đầy
    public double getOccupancyRate() {
        if (totalSeats == 0)
            return 0;
        return (double) ticketsSold / totalSeats;
    }
}