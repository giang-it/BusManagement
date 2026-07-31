package giang.com.BusManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PHASE 8 — bốn "cần gạt" của một kịch bản What-if, nhận từ query param
 * (@ModelAttribute). Tất cả đều có mặc định = baseline, nên mở trang không kèm
 * tham số vẫn cho ra kịch bản trùng hiện trạng.
 *
 * Đây chỉ là ĐẦU VÀO — không chứa luật. Việc kẹp biên và điền mặc định do
 * WhatIfSimulationService.normalize() làm; các field để kiểu bọc (Integer/
 * BigDecimal) để phân biệt "không nhập" (null) với "nhập số 0".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfScenarioDto {

    /** Thay đổi số xe khả dụng (có thể âm). Mặc định 0. */
    private Integer deltaBuses = 0;

    /** Thay đổi số tài xế đang hoạt động (có thể âm). Mặc định 0. */
    private Integer deltaDrivers = 0;

    /** Hệ số nhu cầu (%). 100 = giữ nguyên dự báo; 130 = giả định nhu cầu tăng 30%. */
    private Integer demandFactorPercent = 100;

    /**
     * Ghi đè chi phí nhiên liệu/km (đồng) CHỈ trong phiên mô phỏng này. Null =
     * dùng giá trị đang cấu hình ở CostParameters. KHÔNG bao giờ được lưu xuống
     * DB — màn /admin/cost-parameters vẫn là nơi cấu hình chính thức duy nhất.
     */
    private BigDecimal fuelCostPerKm;

    /** Ghi đè lương tài xế/giờ (đồng), cùng quy ước null = dùng mặc định, không lưu. */
    private BigDecimal driverWagePerHour;
}
