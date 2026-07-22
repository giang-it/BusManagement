package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Toàn bộ nội dung màn hình "Đề xuất thay thế phương tiện".
 *
 * Ngoài bảng xếp hạng, DTO còn mang theo các con số mô tả CHẤT LƯỢNG của chính
 * tín hiệu đang dùng. Đó không phải trang trí: nếu phần lớn đội xe chưa có sự cố
 * nào thì nửa "độ tin cậy" của điểm số không phân biệt được ai với ai, và bảng
 * xếp hạng thực chất chỉ đang sắp theo số km. Người xem có quyền biết điều đó
 * thay vì tưởng đang nhìn một mô hình hai yếu tố cân bằng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleReplacementViewDto {

    /** Toàn đội xe, xếp theo điểm GIẢM DẦN (đáng xem xét nhất lên đầu). */
    private List<VehicleReplacementDto> ranking;

    private int fleetSize;

    /** Km thấp nhất / cao nhất trong đội — hai mốc dùng để chuẩn hóa thành phần hao mòn. */
    private double minOdometer;
    private double maxOdometer;

    /** Tổng số sự cố liên quan tới xe trên toàn đội. */
    private int totalVehicleIncidents;

    /** Số xe chưa từng có sự cố xe nào. */
    private int busesWithoutIncidents;

    /**
     * Tín hiệu sự cố có quá mỏng để phân biệt hay không (phần lớn đội xe đang
     * hòa nhau ở mức 0). Khi true, giao diện phải nói rõ điểm số đang do số km
     * chi phối.
     */
    private boolean incidentSignalThin;

    /** Trọng số đang áp dụng, hiển thị để công thức không phải là hộp đen. */
    private int wearWeightPercent;
    private int reliabilityWeightPercent;
}
