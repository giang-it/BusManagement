package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Strategic Analytics — tab "Chuyến đi".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripStatsDto {
    private long totalTrips;
    private long pendingCount;
    private long activeCount;
    private long departedCount;
    private long completedCount;
    private long cancelledCount;
    private long totalTicketsSold;
    private BigDecimal totalRevenue;
    /** Bar: số chuyến theo từng TripStatus. */
    private ChartSeriesDto statusChart;
}
