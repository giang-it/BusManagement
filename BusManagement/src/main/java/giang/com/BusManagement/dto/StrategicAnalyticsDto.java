package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nhóm toàn bộ nội dung hiển thị trong các tab phân tích chi tiết của Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategicAnalyticsDto {
    private FleetStatsDto fleet;
    private TripStatsDto trips;
    private RouteStatsDto routes;
    private DriverStatsDto drivers;
    private OccupancyStatsDto occupancy;
    private AiStatsDto aiAnalytics;
}
