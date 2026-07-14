package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Strategic Analytics — tab "AI Recommendation".
 * Suy ra từ Trip.isExtraTrip + Trip.status, không có entity/audit riêng cho AI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiStatsDto {
    private long totalSuggested;
    private long activatedCount;
    private long rejectedCount;
    private long pendingCount;
    /** Doughnut: activated / rejected / pending. */
    private ChartSeriesDto outcomeChart;
}
