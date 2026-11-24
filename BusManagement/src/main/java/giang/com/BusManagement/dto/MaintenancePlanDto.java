package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một ngày bảo trì dự kiến của một xe (route pairs CT7d, CM-25 cách M1) — trên trang "Lịch thường lệ": ở kết
 * quả một lần bấm "Tạo lịch" (ngày vừa đặt) và ở danh sách mọi ngày dự kiến đang có.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenancePlanDto {

    private Long busId;
    private String busPlate;
    /** Loại xe, kèm số chỗ; null nếu xe chưa gán loại. */
    private String busTypeLabel;
    /** Ngày xe nằm ở bến nhà để bảo trì. */
    private LocalDate date;
    /**
     * Km tính từ lần bảo trì cuối. Ở kết quả lần bấm: tính cả lịch đã xếp tới đầu ngày bảo trì (con số làm bộ
     * lập lịch đặt ngày này). Ở danh sách: km thật hiện tại của xe.
     */
    private double km;
    /** Ngưỡng bảo trì của xe (km); null nếu xe không có ngưỡng. */
    private Double threshold;
    /** Ngày đã qua mà chưa ghi nhận bảo trì: ngày này không còn tác dụng (Bus.hasPlannedMaintenance). */
    private boolean overdue;
}
