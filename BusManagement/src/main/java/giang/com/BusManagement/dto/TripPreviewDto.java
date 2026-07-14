package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * View-model rút gọn cho danh sách "Pending Approvals" / "Upcoming Trips" ở
 * dải KPI vận hành. Không expose Trip entity ra view để tránh
 * LazyInitializationException và rò rỉ chi tiết domain không cần thiết.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripPreviewDto {
    private Long id;
    private String routeLabel;
    private LocalDateTime departureTime;
    private int totalSeats;
    private int ticketsSold;
    /** Chỉ có ý nghĩa với chuyến đang PENDING_APPROVAL: AI đã gán đủ bus+driver hay chưa. */
    private boolean autoAssigned;
}
