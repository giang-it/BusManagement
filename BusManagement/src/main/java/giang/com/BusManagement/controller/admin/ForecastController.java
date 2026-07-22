package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Dự Báo Nhu Cầu (Phase 6) — thành phần AI Prediction.
 *
 * Chỉ đọc, không có action ghi nào: màn hình đưa ra DỰ BÁO để Admin tự quyết
 * định, không tạo chuyến và không phân công ai.
 *
 * TRANG RIÊNG, KHÔNG PHẢI TAB — sai lệch có chủ ý so với câu chữ của Phase 6
 * trong THESIS_ROADMAP.md ("wired into a new tab in dashboard-analytics.html"),
 * đã được chủ dự án duyệt ngày 2026-07-22. Lý do: Demand Forecast thuộc trụ cột
 * 2 (Hỗ trợ ra quyết định), và chính roadmap đã ghi ở Developer Note của Phase 4
 * rằng nhét deliverable trụ cột 2 thành thêm một tab giữa các bảng thống kê trụ
 * cột 1 sẽ chôn vùi đúng cái ranh giới mà luận văn cần bảo vệ. Lý do CƠ HỌC từng
 * buộc Phase 4 phải tách trang (getOverview() không nhận tham số) không áp dụng
 * ở đây, nhưng lý do KHÁI NIỆM thì áp dụng nguyên vẹn.
 *
 * Vẫn nằm dưới /admin/analytics/ và được liên kết từ trang Analytics, giống
 * cách Phase 4 đặt màn Đề xuất tài xế.
 *
 * Chỉ inject Service (mẫu AdminBusController), không đụng Repository.
 */
@Controller
@RequestMapping("/admin/analytics/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    /**
     * Không nhận tham số: chân trời dự báo cố định 7 ngày kể từ ngày mai, và
     * phạm vi lịch sử là toàn bộ dữ liệu sẵn có. Chưa có nhu cầu nào cần Admin
     * chỉnh hai giá trị đó, nên không thêm tham số chỉ vì có thể thêm.
     */
    @GetMapping
    public String viewForecast(Model model) {
        model.addAttribute("view", forecastService.buildForecast());
        return "admin/demand-forecast";
    }
}
