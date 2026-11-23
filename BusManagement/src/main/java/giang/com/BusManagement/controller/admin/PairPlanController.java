package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.PairPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Kế hoạch cặp tuyến — route pairs CT7 (QĐ-22 mức M2; CM-9: trang mới trong nhóm phân tích, cạnh màn
 * Đề xuất). Chỉ đọc: mỗi cặp một bảng cho một ngày, mỗi xe một hàng, chuyến thêm ★ của màn Đề xuất kèm
 * lượt đi kèm. Không nhầm với "Bảng điều hành" (/admin/dispatch), là màn chuyển trạng thái chuyến.
 *
 * Chỉ inject Service (mẫu AdminBusController).
 */
@Controller
@RequestMapping("/admin/analytics/pair-plan")
@RequiredArgsConstructor
public class PairPlanController {

    private final PairPlanService pairPlanService;

    /** date: ngày muốn xem trong chân trời dự báo; thiếu hay ngoài chân trời thì là ngày đầu (ngày mai). */
    @GetMapping
    public String viewPlan(@RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        model.addAttribute("view", pairPlanService.build(date));
        return "admin/pair-plan";
    }
}
