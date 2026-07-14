package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Strategic Analytics — tab "Occupancy". Tính trên các chuyến ACTIVE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyStatsDto {
    private double averageOccupancyRate;
    /** occupancyRate > 0.9, cùng ngưỡng với Trip.needsReinforcement(). */
    private long hotTripsCount;
    /** Bar: phân bố số chuyến theo bucket lấp đầy (<50%, 50-70%, 70-90%, >90%). */
    private ChartSeriesDto occupancyBucketChart;
}
