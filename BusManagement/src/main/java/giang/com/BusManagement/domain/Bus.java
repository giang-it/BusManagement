package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "buses")
@Data
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", unique = true, nullable = false)
    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_type_id")
    private BusType busType;

    @Column(name = "brand")
    private String brand; // Hãng xe (đã có từ trước)

    // === MAINTENANCE TRACKING ===
    @Column(name = "odometer", columnDefinition = "DOUBLE DEFAULT 0")
    private Double odometer = 0.0; // Đổi tên từ totalKm sang odometer

    @Column(name = "last_maintenance_odometer", columnDefinition = "DOUBLE DEFAULT 0")
    private Double lastMaintenanceOdometer = 0.0; // Số km lúc bảo trì lần cuối

    @Column(name = "maintenance_threshold", columnDefinition = "DOUBLE DEFAULT 5000")
    private Double maintenanceThreshold = 5000.0; // Ngưỡng cần bảo trì

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BusStatus status = BusStatus.READY; // READY, TRAVELING, REPAIRING (đã có)

    // === HELPER METHODS ===

    // Tính km kể từ lần bảo trì cuối
    public Double getKmSinceLastMaintenance() {
        return odometer - lastMaintenanceOdometer;
    }

    // Kiểm tra xe có cần bảo trì không
    public Boolean needsMaintenance() {
        return getKmSinceLastMaintenance() >= maintenanceThreshold;
    }

    // Phương thức reset sau bảo trì
    public void performMaintenance() {
        this.lastMaintenanceOdometer = this.odometer;
        this.status = BusStatus.READY;
    }
}