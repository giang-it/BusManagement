package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Strategic Analytics — tab "Đội xe".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetStatsDto {
    private long totalBuses;
    private long readyCount;
    private long travelingCount;
    private long repairingCount;
    /** travelingCount / totalBuses tại thời điểm truy vấn — chỉ số tức thời, không phải lịch sử. */
    private double utilizationRate;
    /** Doughnut: phân bố theo BusStatus. */
    private ChartSeriesDto statusChart;
    /** Bar: số xe theo từng BusType. */
    private ChartSeriesDto busTypeChart;
}
