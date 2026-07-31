package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.dto.WhatIfScenarioDto;
import giang.com.BusManagement.service.WhatIfSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * PHASE 8 — Mô Phỏng Kịch Bản (What-if).
 *
 * Chỉ đọc: mỗi lần mở trang, hệ thống tính lại năng lực đội xe và độ phủ nhu cầu
 * dự báo dưới các "cần gạt" (Δxe, Δtài xế, hệ số nhu cầu, ghi đè chi phí) — bằng
 * SỐ HỌC trên các đại lượng tổng hợp, KHÔNG chạy lại engine phân công, KHÔNG lưu
 * DB, KHÔNG tạo chuyến. Ghi đè chi phí chỉ sống trong phiên mô phỏng.
 *
 * Nằm dưới /admin/analytics/ và chỉ inject Service (mẫu AdminBusController), cùng
 * khuôn với các màn Decision Support khác (Đề xuất tài xế, Dự báo, Đề xuất thay
 * xe, Đề xuất tăng cường).
 */
@Controller
@RequestMapping("/admin/analytics/what-if")
@RequiredArgsConstructor
public class WhatIfController {

    private final WhatIfSimulationService whatIfSimulationService;

    /**
     * Nhận bốn cần gạt qua query param (@ModelAttribute). Vắng tham số → kịch bản
     * trùng hiện trạng (mặc định trong WhatIfScenarioDto). Việc kẹp biên/điền mặc
     * định do service làm, controller không chứa luật.
     */
    @GetMapping
    public String viewSimulation(@ModelAttribute WhatIfScenarioDto scenario, Model model) {
        model.addAttribute("view", whatIfSimulationService.simulate(scenario));
        return "admin/what-if";
    }
}
