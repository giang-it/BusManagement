package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một chiếc xe trong bảng xếp hạng "ưu tiên xem xét thay thế".
 *
 * Đây là ĐỀ XUẤT XEM XÉT, không phải phán quyết "xe này phải thay". Điểm số
 * mang tính TƯƠNG ĐỐI trong nội bộ đội xe hiện tại: chiếc đứng đầu luôn có điểm
 * cao nhất kể cả khi cả đội đều còn mới. Vì vậy DTO mang theo cả số liệu thô
 * (km, số sự cố) để Admin tự đối chiếu chứ không chỉ nhìn điểm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleReplacementDto {

    private Long busId;
    private String licensePlate;
    private String brand;

    /** Tên loại xe (Limousine / Giường nằm / Ghế ngồi), có thể null nếu xe chưa gán loại. */
    private String busTypeName;

    /** Trạng thái vận hành hiện tại — hiển thị để tham khảo, KHÔNG tham gia chấm điểm. */
    private String statusLabel;

    /** Số km trọn đời. Không bao giờ reset, nên là đại diện cho hao mòn/tuổi xe. */
    private Double odometer;

    /** Tổng số sự cố LIÊN QUAN TỚI XE (hỏng hóc + tai nạn). */
    private int vehicleIncidentCount;

    private int breakdownCount;
    private int accidentCount;

    /** Thành phần hao mòn, 0..1 — vị trí tương đối của xe theo km trong đội. */
    private double wearScore;

    /** Thành phần độ tin cậy, 0..1 — vị trí tương đối theo số sự cố xe. */
    private double reliabilityScore;

    /** Điểm tổng hợp 0..100 dùng để xếp hạng. */
    private double replacementScore;

    /** Câu giải thích vì sao xe này ở vị trí đó — chỉ diễn đạt lại số liệu đã tính. */
    private String reason;
}
