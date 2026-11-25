package giang.com.BusManagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tham số chi phí vận hành — do Admin cấu hình lúc chạy (Phase 7, bước 3).
 *
 * VÌ SAO ENTITY NÀY TỒN TẠI: domain KHÔNG có dữ liệu chi phí nào (không nhiên
 * liệu / lương / phí đường). Chi phí vận hành là dữ liệu NGOÀI schema lập lịch,
 * nên không thể suy ra từ dữ liệu sẵn có — nó phải là THAM SỐ do đơn vị vận hành
 * nhập. Recommendation Engine đọc bảng này để ước tính chi phí & lợi nhuận cho
 * mỗi thẻ đề xuất, thay vì hardcode hằng số bịa (§4 liệt định giá là Non-Goal;
 * cách này nhận THAM SỐ chi phí vận hành làm đầu vào, KHÔNG dựng module định giá).
 *
 * MỘT dòng cấu hình duy nhất cho toàn hệ thống — xem CostParameterService, nơi
 * đảm bảo chỉ đọc/ghi dòng đầu tiên. Giá trị mặc định là số tham chiếu thị
 * trường XẤP XỈ (đồng), đơn vị phải chỉnh theo thực tế; khi bảo vệ luận văn nên
 * trích nguồn giá nhiên liệu / lương hiện hành.
 *
 * equals/hashCode theo id — xem quy ước ở User.driver.
 */
@Entity
@Table(name = "cost_parameters")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CostParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Chi phí nhiên liệu trên mỗi km (đồng/km). Nhân với Route.distanceKm. */
    @Column(name = "fuel_cost_per_km", precision = 12, scale = 2)
    private BigDecimal fuelCostPerKm;

    /** Lương tài xế trên mỗi giờ (đồng/giờ). Nhân với thời lượng chuyến × số tài xế. */
    @Column(name = "driver_wage_per_hour", precision = 12, scale = 2)
    private BigDecimal driverWagePerHour;

    /**
     * Thời điểm cập nhật gần nhất — để trang đề xuất cho biết "chi phí tính theo
     * giá cập nhật ngày nào". Hibernate tự đóng dấu khi INSERT và mỗi lần UPDATE.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Route pairs CT9 (QĐ-26, CM-8) — TIỀN CHỜ khi xe tăng cường được giữ ở bến xa chờ chở khách chiều về
     * (vòng giữ xe, CM-29): lương chờ của mỗi người trong kíp (đồng/người/ngày; kíp ở lại cùng xe và vẫn nhận
     * lương cơ bản) và phí xe nằm bến (đồng/xe/ngày). Tính theo giờ chờ. Hai cột cho phép null, thêm bằng
     * ddl-auto=update: dòng cấu hình có từ trước đọc ra giá trị mặc định cho tới khi Admin bấm Lưu
     * (CostParameterService.getOrDefault).
     */
    @Column(name = "waiting_wage_per_day", precision = 12, scale = 2)
    private BigDecimal waitingWagePerDay;

    @Column(name = "bus_parking_per_day", precision = 12, scale = 2)
    private BigDecimal busParkingPerDay;
}
