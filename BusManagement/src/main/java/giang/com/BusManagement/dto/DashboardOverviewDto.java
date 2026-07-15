package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * View-model gốc trả về cho trang /admin/analytics.
 * operational = dải KPI cố định; strategic = nội dung các tab phân tích.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDto {
    private OperationalKpisDto operational;
    private StrategicAnalyticsDto strategic;
}
