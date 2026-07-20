package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.DriverRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Đề xuất tài xế khả dụng — màn hình Decision Support đầu tiên của hệ thống.
 *
 * Chỉ đọc, không có action ghi nào: màn hình đưa ra ĐỀ XUẤT để Admin tự quyết
 * định, không tự phân công ai vào chuyến nào.
 *
 * Nằm dưới /admin/analytics/ vì thuộc khu vực phân tích, nhưng là controller
 * riêng thay vì thêm vào DashboardController — theo đúng quy ước "1 controller
 * / 1 mối quan tâm" đã ghi trong javadoc của DashboardController, và vì màn
 * hình này cần tham số ngày trong khi DashboardService.getOverview() không
 * nhận tham số nào.
 *
 * Chỉ inject Service (mẫu AdminBusController), không đụng Repository.
 */
@Controller
@RequestMapping("/admin/analytics/driver-recommendation")
@RequiredArgsConstructor
public class DriverRecommendationController {

    private final DriverRecommendationService driverRecommendationService;

    /**
     * @param date ngày cần xét; bỏ trống thì mặc định hôm nay, để link vào màn
     *             hình từ Dashboard/Analytics không cần mang sẵn tham số.
     */
    @GetMapping
    public String viewRecommendations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        model.addAttribute("view", driverRecommendationService.recommendAvailableDrivers(targetDate));
        return "admin/driver-recommendation";
    }
}
