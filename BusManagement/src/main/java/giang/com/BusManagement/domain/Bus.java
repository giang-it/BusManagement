package giang.com.BusManagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "buses")
@Data
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_type_id")
    private BusType busType;

    private String brand;
    private Double odometer;
    private Double lastMaintenanceOdometer;
    private Double maintenanceThreshold;

    @Enumerated(EnumType.STRING)
    private BusStatus status; // READY, TRAVELING, REPAIRING

    public double getKmSinceLastMaintenance() {
        if (odometer == null || lastMaintenanceOdometer == null)
            return 0.0;
        return odometer - lastMaintenanceOdometer;
    }

    public boolean needsMaintenance() {
        if (maintenanceThreshold == null)
            return false;
        return getKmSinceLastMaintenance() >= maintenanceThreshold;
    }

    /**
     * Ngưỡng "sắp đến hạn bảo trì" = 90% maintenanceThreshold.
     * Dùng chung bởi mọi nơi cần biết xe có đang ở vùng cảnh báo hay không
     * (trước khi cộng thêm km của một chuyến cụ thể).
     */
    private static final double NEAR_MAINTENANCE_RATIO = 0.9;

    /**
     * Kiểm tra xe có "sắp đến hạn bảo trì" sau khi cộng thêm quãng đường của
     * một chuyến xe cụ thể hay không. "Sắp đến hạn" = km lũy kế (hiện tại +
     * additionalKm) đạt từ 90% maintenanceThreshold trở lên — bao gồm cả
     * trường hợp đã vượt ngưỡng 100% (đã quá hạn).
     *
     * @param additionalKm Quãng đường dự kiến của chuyến xe sẽ cộng thêm vào
     *                     odometer (route.distanceKm). Truyền 0 nếu chỉ muốn
     *                     kiểm tra tình trạng hiện tại của xe (không tính
     *                     chuyến nào).
     */
    public boolean isNearMaintenance(double additionalKm) {
        if (maintenanceThreshold == null)
            return false;
        return (getKmSinceLastMaintenance() + additionalKm) >= (maintenanceThreshold * NEAR_MAINTENANCE_RATIO);
    }
}
