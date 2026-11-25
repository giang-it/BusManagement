package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.dto.NightlyRunDto;
import giang.com.BusManagement.service.NightlyDispatchService;
import giang.com.BusManagement.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Đề Xuất Tăng Cường (Phase 7) — Recommendation Engine.
 *
 * Trang chỉ đọc: mỗi lần mở, engine tính lại từ dự báo (Phase 6) ra các khung
 * giờ được dự báo đông, ghép với xe + tài xế chọn được và ước tính doanh thu /
 * chi phí / lợi nhuận (chi phí theo tham số Admin cấu hình — bước 3), rồi qua
 * cổng Business Rule Validation (Phase 3). Mở trang KHÔNG lưu DB, KHÔNG tạo
 * chuyến, KHÔNG phân công ai — Admin tự quyết.
 *
 * Từ route pairs CT8 (QĐ-24) trang còn hiện lần chạy gần nhất của job đêm — job
 * mới là nơi biến các thẻ đáng chạy thành vòng CHỜ DUYỆT — và có nút "Chạy ngay"
 * (POST, thao tác chủ ý của Admin) để chạy job đó tức thì.
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
    private final NightlyDispatchService nightlyDispatchService;

    /**
     * Không nhận tham số: chân trời dự báo và phạm vi lịch sử đều do Phase 6 cố
     * định (7 ngày kể từ ngày mai, toàn bộ lịch sử sẵn có). Không thêm bộ lọc chỉ
     * vì có thể thêm.
     */
    @GetMapping
    public String viewRecommendations(Model model) {
        model.addAttribute("view", recommendationService.buildRecommendations());
        model.addAttribute("nightly", nightlyDispatchService.lastRun());
        model.addAttribute("nightlyEnabled", nightlyDispatchService.isEnabled());
        model.addAttribute("nightlySimulates", nightlyDispatchService.isSimulatingOperations());
        return "admin/recommendation";
    }

    /** Nút "Chạy ngay" (CT8, CM-10 cách T2): chạy job đêm một lần, rồi về trang với kết quả. */
    @PostMapping("/nightly-run")
    public String runNightlyJob(RedirectAttributes redirectAttributes) {
        NightlyRunDto run = nightlyDispatchService.run("Admin bấm Chạy ngay");
        if (run == null) {
            redirectAttributes.addFlashAttribute("nightlyMessage", "Job đêm đang chạy ở một lần khác — thử lại sau ít phút.");
        } else {
            redirectAttributes.addFlashAttribute("nightlyMessage", "Đã chạy job đêm (" + run.getElapsedMillis() + " ms): tạo "
                    + run.getLoopsCreated().size() + " vòng chờ duyệt, rút "
                    + (run.getLoopsWithdrawnByForecast().size() + run.getLoopsWithdrawnByDeadline().size()) + " vòng."
                    + (run.getErrors().isEmpty() ? "" : " Có " + run.getErrors().size() + " bước báo lỗi — xem bên dưới."));
        }
        return "redirect:/admin/analytics/recommendation";
    }
}
