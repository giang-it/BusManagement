package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Đề Xuất Tăng Cường (Phase 7, bước 2) — Recommendation Engine.
 *
 * Chỉ đọc: mỗi lần mở trang, engine tính lại từ dự báo (Phase 6) ra các khung
 * giờ được dự báo đông, ghép với xe + tài xế chọn được và doanh thu ước tính,
 * rồi qua cổng Business Rule Validation (Phase 3). KHÔNG lưu DB, KHÔNG tạo
 * chuyến, KHÔNG phân công ai — Admin tự quyết.
 *
 * Nằm dưới /admin/analytics/ và chỉ inject Service (mẫu AdminBusController),
 * cùng khuôn với ba màn Decision Support đã có (Đề xuất tài xế, Dự báo nhu cầu,
 * Đề xuất thay xe).
 */
@Controller
@RequestMapping("/admin/analytics/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Không nhận tham số: chân trời dự báo và phạm vi lịch sử đều do Phase 6 cố
     * định (7 ngày kể từ ngày mai, toàn bộ lịch sử sẵn có). Không thêm bộ lọc chỉ
     * vì có thể thêm.
     */
    @GetMapping
    public String viewRecommendations(Model model) {
        model.addAttribute("view", recommendationService.buildRecommendations());
        return "admin/recommendation";
    }
}
