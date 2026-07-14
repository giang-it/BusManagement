package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Trang Dashboard & Analytics — chỉ đọc, không có action ghi dữ liệu nào.
 * Tách khỏi AdminController (vốn là trang chủ/shortcut đơn giản) theo đúng
 * quy ước 1 controller/1 mối quan tâm đã có trong package này.
 */
@Controller
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public String viewAnalytics(Model model) {
        model.addAttribute("overview", dashboardService.getOverview());
        return "admin/dashboard-analytics";
    }
}
