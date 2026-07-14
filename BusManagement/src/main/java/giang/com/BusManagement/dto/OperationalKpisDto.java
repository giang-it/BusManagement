package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dải KPI vận hành — luôn hiển thị ở đầu Dashboard, không nằm trong tab.
 * Chỉ chứa các chỉ số/cảnh báo Admin cần xử lý ngay trong ngày.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationalKpisDto {

    // Pending Approvals
    private long pendingApprovalsCount;
    private long pendingAutoAssignedCount;
    private long pendingNeedsManualCount;
    private List<TripPreviewDto> pendingApprovalsPreview;

    // Upcoming Trips (48h tới, ACTIVE)
    private long upcomingTripsCount;
    private List<TripPreviewDto> upcomingTripsPreview;

    // Active Trips
    private long activeTripsCount;

    // Maintenance Alerts
    private long maintenanceOverdueCount;
    private long maintenanceNearCount;
    private List<BusAlertDto> maintenanceAlertsPreview;

    // AI Suggestions đang chờ xử lý (tập con của Pending Approvals, isExtraTrip = true)
    private long aiSuggestionsPendingCount;

    // Available Resources
    private long availableBusesCount;
    private long activeDriversCount;
}
