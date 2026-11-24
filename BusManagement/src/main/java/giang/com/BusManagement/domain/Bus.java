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
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "buses")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private String licensePlate;

    @ToString.Exclude // association LAZY — xem quy ước ở User.driver
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
        return reachesNearMaintenance(getKmSinceLastMaintenance() + additionalKm); // cùng ngưỡng, xem cuối lớp
    }

    /**
     * Bến nhà (QĐ-23, CT5a trong docs/development/route_pairs_decisions.md): nơi xe đứng khi chưa
     * chạy chuyến nào. Sau đó vị trí của xe là bến cuối của chuyến gần nhất — xem LocationRule.
     * Form bắt buộc chọn, BusService.validate() cũng từ chối khi trống; cột cho phép null chỉ vì
     * được thêm bằng ddl-auto=update vào bảng đã có dữ liệu (cùng lý do Route.holidayFlow).
     * Đặt ở cuối lớp để không xê dịch số dòng mà các bài học đang trích.
     */
    @ToString.Exclude // association LAZY — xem quy ước ở User.driver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_station_id")
    private Station homeStation;

    /**
     * Ngày bảo trì dự kiến (route pairs CT7d, CM-25 cách M1 trong docs/development/route_pairs_decisions.md):
     * ngày xe nằm ở bến nhà để bảo trì. Chỉ bộ lập lịch thường lệ đặt ngày này, khi xe sắp chạm mốc 90%
     * trong những ngày đã xếp (TripService.planDueMaintenance). Ngày đó xe không chạy chuyến nào; chuyến
     * khởi hành từ ngày hôm sau tính km từ 0. Admin bảo trì xong thì ghi nhận như trước (km lần bảo trì cuối
     * = odometer) và ngày này tự xoá (BusService). Ngày đã qua mà chưa ghi nhận thì không còn tác dụng — xem
     * hasPlannedMaintenance. Cột cho phép null, thêm bằng ddl-auto=update; đặt ở cuối lớp để không xê dịch số
     * dòng mà các bài học đang trích.
     */
    private java.time.LocalDate plannedMaintenanceDate;

    /**
     * Ngày bảo trì dự kiến còn hiệu lực vào hôm nay: có ngày, và ngày đó chưa qua. Ngày đã qua mà km lần bảo
     * trì cuối chưa được ghi nhận thì số liệu nói xe CHƯA được bảo trì, nên ngày đó không còn tác dụng.
     */
    public boolean hasPlannedMaintenance(java.time.LocalDate today) {
        return plannedMaintenanceDate != null && !plannedMaintenanceDate.isBefore(today);
    }

    /**
     * Một số km tính từ lần bảo trì có chạm vùng "sắp đến hạn" (≥ 90% maintenanceThreshold) không — định nghĩa
     * DUY NHẤT của ngưỡng này. isNearMaintenance hỏi với km thật của xe; lịch có ngày bảo trì dự kiến (CT7d) hỏi
     * với km tính lại từ 0 sau ngày đó.
     */
    public boolean reachesNearMaintenance(double kmSinceMaintenance) {
        if (maintenanceThreshold == null)
            return false;
        return kmSinceMaintenance >= (maintenanceThreshold * NEAR_MAINTENANCE_RATIO);
    }
}
