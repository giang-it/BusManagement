package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.service.VehicleReplacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Đề xuất thay thế phương tiện (Phase 7, bước 1).
 *
 * Chỉ đọc: màn hình xếp hạng thứ tự ƯU TIÊN XEM XÉT để Admin tự quyết, không đổi
 * trạng thái xe và không loại xe nào khỏi điều phối.
 *
 * Độc lập với dây chuyền Recommendation Engine của Phase 7 — không tiêu thụ dự
 * báo, không sinh chuyến ứng viên. Vì vậy nó được tách ra làm bước đầu tiên: có
 * thể ship và kiểm chứng mà không cần chờ quyết định nào về mô hình chi phí hay
 * vòng đời thẻ đề xuất.
 *
 * Nằm dưới /admin/analytics/ và chỉ inject Service (mẫu AdminBusController),
 * cùng khuôn với hai màn hình Decision Support đã có.
 */
@Controller
@RequestMapping("/admin/analytics/vehicle-replacement")
@RequiredArgsConstructor
public class VehicleReplacementController {

    private final VehicleReplacementService vehicleReplacementService;

    /**
     * Không nhận tham số: bảng xếp hạng luôn tính trên toàn bộ đội xe hiện tại.
     * Không thêm bộ lọc chỉ vì có thể thêm — 20 xe hiển thị trọn vẹn trong một
     * màn hình.
     */
    @GetMapping
    public String viewRanking(Model model) {
        model.addAttribute("view", vehicleReplacementService.rankFleet());
        return "admin/vehicle-replacement";
    }
}
