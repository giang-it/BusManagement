package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.CostParameters;
import giang.com.BusManagement.service.CostParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Cấu hình tham số chi phí vận hành (Phase 7, bước 3).
 *
 * Màn hình settings đầu tiên của hệ thống: cho Admin nhập chi phí nhiên liệu/km
 * và lương tài xế/giờ, để Recommendation Engine ước tính chi phí & lợi nhuận.
 * Chỉ inject Service (mẫu AdminBusController); form theo pattern create/edit
 * quen thuộc (GET hiển thị, POST lưu, flash + redirect).
 */
@Controller
@RequestMapping("/admin/cost-parameters")
@RequiredArgsConstructor
public class CostParameterController {

    private final CostParameterService costParameterService;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("params", costParameterService.getOrDefault());
        return "admin/cost-parameters";
    }

    @PostMapping
    public String save(@ModelAttribute("params") CostParameters params, RedirectAttributes redirectAttributes) {
        try {
            costParameterService.save(params);
            redirectAttributes.addFlashAttribute("success", "Đã lưu tham số chi phí!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/cost-parameters";
    }
}
