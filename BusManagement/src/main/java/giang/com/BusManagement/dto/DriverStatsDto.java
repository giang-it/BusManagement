package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Strategic Analytics — tab "Tài xế".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatsDto {
    private long totalDrivers;
    private long activeDrivers;
    private long inactiveDrivers;
    private long expiringLicenseCount;
    private double avgExperienceYears;
    private List<DriverWorkloadDto> topLoadedDrivers;
    /** Doughnut: active vs inactive. */
    private ChartSeriesDto activeInactiveChart;
}
