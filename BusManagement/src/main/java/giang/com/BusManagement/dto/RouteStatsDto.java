package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Strategic Analytics — tab "Tuyến đường".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStatsDto {
    private long totalRoutes;
    private long routesWithNoTrips;
    private List<RoutePerformanceDto> topRoutes;
    /** Bar: số chuyến theo từng tuyến (top N). */
    private ChartSeriesDto tripsPerRouteChart;
}
