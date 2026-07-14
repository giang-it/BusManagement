package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng trong bảng "Tài xế có tải cao nhất hôm nay".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverWorkloadDto {
    private Long userId;
    private String fullName;
    private double drivingHoursToday;
}
