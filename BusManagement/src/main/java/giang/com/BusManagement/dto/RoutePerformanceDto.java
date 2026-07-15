package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một dòng trong bảng xếp hạng tuyến đường (top routes theo số chuyến/doanh thu).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePerformanceDto {
    private Long routeId;
    private String routeLabel;
    private long tripCount;
    private BigDecimal revenue;
}
