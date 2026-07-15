package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * View-model cho danh sách cảnh báo bảo trì ở dải KPI vận hành.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusAlertDto {
    private Long id;
    private String licensePlate;
    private double kmSinceLastMaintenance;
    private double maintenanceThreshold;
    /** true = đã quá hạn (needsMaintenance()); false = sắp đến hạn (isNearMaintenance()). */
    private boolean overdue;
}
