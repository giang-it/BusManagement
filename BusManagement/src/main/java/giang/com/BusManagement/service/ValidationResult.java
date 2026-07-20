package giang.com.BusManagement.service;

import lombok.Getter;

/**
 * Kết quả kiểm tra ràng buộc nghiệp vụ dưới dạng DỮ LIỆU thay vì exception.
 *
 * Dùng cho các method dry-run trong TripService (validateBusForTripDryRun /
 * validateStaffForTripDryRun) để tầng Decision Support có thể hỏi "chuyến này
 * có hợp lệ không?" mà không phải bắt exception cho một luồng bình thường.
 *
 * CHỈ CHỨA TỐI ĐA MỘT LÝ DO THẤT BẠI — đúng bằng ràng buộc ĐẦU TIÊN bị vi phạm.
 * Đây không phải giới hạn của lớp này mà là hành vi fail-fast của chính các
 * validator gốc: chúng throw ngay tại lỗi đầu tiên nên không bao giờ tồn tại
 * lỗi thứ hai để báo cáo. Muốn liệt kê đầy đủ mọi lỗi thì phải viết lại
 * validator gốc — việc mà roadmap cấm ở Phase 3 (xem THESIS_ROADMAP.md
 * Section 5, Phase 3 và Developer Notes).
 *
 * Cấu trúc theo đúng tiền lệ AutoAssignResult trong cùng package: bất biến,
 * constructor private, tạo qua static factory.
 */
@Getter
public class ValidationResult {

    private final boolean valid;

    /** Lý do thất bại — null khi valid == true. */
    private final String failureReason;

    /**
     * Cảnh báo KHÔNG chặn, đi kèm một kết quả hợp lệ — null khi không có.
     *
     * Giữ nguyên kênh warning mà validateBusForTrip() vẫn trả về (hiện luôn
     * null vì mọi ràng buộc bảo trì đã chuyển sang chặn cứng). Không tự sinh
     * thêm cảnh báo nào ở đây; chỉ chuyển tiếp nguyên vẹn.
     */
    private final String warning;

    private ValidationResult(boolean valid, String failureReason, String warning) {
        this.valid = valid;
        this.failureReason = failureReason;
        this.warning = warning;
    }

    /** Hợp lệ, không kèm cảnh báo. */
    public static ValidationResult pass() {
        return new ValidationResult(true, null, null);
    }

    /** Hợp lệ, kèm cảnh báo không chặn (có thể null). */
    public static ValidationResult pass(String warning) {
        return new ValidationResult(true, null, warning);
    }

    /** Vi phạm ràng buộc — reason là thông điệp của validator gốc, giữ nguyên văn. */
    public static ValidationResult fail(String reason) {
        return new ValidationResult(false, reason, null);
    }
}
