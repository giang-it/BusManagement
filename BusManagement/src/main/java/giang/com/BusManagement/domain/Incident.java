package giang.com.BusManagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một sự cố vận hành được báo về trung tâm điều hành.
 *
 * Là entity ĐỘC LẬP tham chiếu Bus/Trip/Driver qua khóa ngoại, không phải thêm
 * field vào Trip: Trip đã gánh FSM + xóa mềm + self-reference chuyến tăng cường,
 * và sự cố còn xảy ra cả khi xe không thuộc chuyến nào (hỏng trong bãi).
 *
 * Ghi nhận sự cố KHÔNG tự động đổi Bus.status sang REPAIRING — việc đưa xe vào
 * bảo trì vẫn là thao tác thủ công của Admin ở màn hình quản lý xe, để tránh
 * side-effect ngoài ý muốn lên các chuyến đang gán xe đó.
 */
@Entity
@Table(name = "incidents")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // equals/hashCode CHỈ theo id — xem quy ước ở User.driver
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Ba association bên dưới đều LAZY và @ToString.Exclude (không kéo đồ thị khi
    // log, không nằm trong equals/hashCode) — xem quy ước ở User.driver.

    /**
     * Xe liên quan — BẮT BUỘC. Mọi sự cố đều qui về một chiếc xe cụ thể, nhờ đó
     * thống kê được "xe nào hay gặp sự cố nhất".
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    /**
     * Chuyến đang chạy khi sự cố xảy ra — tùy chọn (null nếu xe gặp sự cố lúc
     * không thuộc chuyến nào, ví dụ hỏng trong bãi).
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /**
     * Tài xế báo cáo / liên quan tới sự cố — tùy chọn.
     * Hệ thống chưa có authentication (SecurityConfig đang permit-all) nên không
     * thể tự suy ra người báo cáo từ phiên đăng nhập; Admin chọn tay.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Thời điểm ghi nhận, Hibernate tự đóng dấu khi INSERT. */
    @CreationTimestamp
    @Column(name = "reported_at", updatable = false)
    private LocalDateTime reportedAt;

    /**
     * Thời điểm xử lý xong. Do IncidentService quản lý theo status (đóng dấu khi
     * chuyển sang RESOLVED, gỡ khi mở lại) — không nhập tay từ form.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
