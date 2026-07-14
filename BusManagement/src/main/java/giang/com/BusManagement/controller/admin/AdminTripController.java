package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/trips")
@RequiredArgsConstructor
public class AdminTripController {

    private final TripService tripService;

    // =========================================================================
    // 1. DANH SÁCH CHUYẾN CHỜ DUYỆT
    // =========================================================================

    /**
     * Hiển thị tất cả chuyến tăng cường đang chờ Admin xác nhận (PENDING_APPROVAL).
     * Mỗi chuyến cho thấy AI đã phân công sẵn chưa hay cần xử lý thủ công.
     */
    @GetMapping("/pending")
    public String viewPendingTrips(Model model) {
        List<Trip> pendingTrips = tripService.getPendingTrips();
        model.addAttribute("pendingTrips", pendingTrips);

        long autoAssignedCount = pendingTrips.stream()
                .filter(t -> t.getBus() != null && t.getDriver() != null)
                .count();
        model.addAttribute("autoAssignedCount", autoAssignedCount);
        model.addAttribute("needsManualCount", pendingTrips.size() - autoAssignedCount);

        return "admin/pending-trips";
    }

    // =========================================================================
    // 2. XEM CHI TIẾT & FORM PHÊ DUYỆT
    // =========================================================================

    /**
     * Hiển thị trang phê duyệt một chuyến tăng cường.
     *
     * Có 2 chế độ:
     * - AUTO MODE: AI đã phân công sẵn (bus + driver không null) → Admin chỉ cần
     * confirm 1 click.
     * - MANUAL MODE: AI không tìm được tài nguyên → Hiển thị dropdown cho Admin
     * chọn.
     */
    @GetMapping("/approve/{id}")
    public String showApproveForm(@PathVariable Long id, Model model) {
        Trip trip = tripService.getTripById(id);
        model.addAttribute("trip", trip);

        boolean isAutoAssigned = (trip.getBus() != null && trip.getDriver() != null);
        model.addAttribute("isAutoAssigned", isAutoAssigned);

        if (!isAutoAssigned) {
            // MANUAL MODE: Chỉ load danh sách khi AI không tự phân công được
            model.addAttribute("availableBuses", tripService.getAvailableBusesForTrip(id));
            model.addAttribute("availableDrivers", tripService.getAvailableDriversForTrip(id));
            // Phụ xe không trực tiếp lái xe nên dùng danh sách riêng, không áp ràng buộc
            // giờ lái tối đa 8h/ngày (xem TripService.getAvailableAssistantsForTrip)
            model.addAttribute("availableAssistants", tripService.getAvailableAssistantsForTrip(id));
        }

        return "admin/approve-form";
    }

    // =========================================================================
    // 3. XÁC NHẬN CHUYẾN ĐÃ ĐƯỢC AI PHÂN CÔNG (1-CLICK)
    // =========================================================================

    /**
     * Admin xác nhận chuyến AI đã phân công đầy đủ → kích hoạt ngay (ACTIVE).
     * Không cần chọn gì thêm.
     */
    @PostMapping("/confirm")
    public String confirmAutoAssigned(@RequestParam Long tripId,
            RedirectAttributes redirectAttributes) {
        try {
            String warning = tripService.confirmAutoAssignedTrip(tripId);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Chuyến tăng cường #" + tripId + " đã được kích hoạt thành công!");
            if (warning != null) {
                redirectAttributes.addFlashAttribute("warning", warning);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi xác nhận: " + e.getMessage());
            return "redirect:/admin/trips/approve/" + tripId;
        }
        return "redirect:/admin/trips/pending";
    }

    // =========================================================================
    // 4. PHÊ DUYỆT THỦ CÔNG (khi AI không tự phân công được)
    // =========================================================================

    /**
     * Admin tự chọn xe + tài xế + phụ xe và kích hoạt chuyến.
     * Hệ thống vẫn kiểm tra ràng buộc (8h/ngày, bằng lái, v.v.) trước khi lưu.
     */
    @PostMapping("/approve")
    public String processManualApproval(@RequestParam Long tripId,
            @RequestParam Long busId,
            @RequestParam Long driverId,
            @RequestParam(required = false) Long assistantId,
            @RequestParam(required = false) List<Long> coDriverIds,
            RedirectAttributes redirectAttributes) {
        try {
            String warning = tripService.approveTrip(tripId, busId, driverId, assistantId, coDriverIds);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Chuyến xe #" + tripId + " đã được phân công và kích hoạt thành công!");
            if (warning != null) {
                redirectAttributes.addFlashAttribute("warning", warning);
            }
        } catch (IllegalArgumentException e) {
            // Lỗi vi phạm ràng buộc → báo cho Admin biết
            redirectAttributes.addFlashAttribute("error", "⛔ Vi phạm ràng buộc: " + e.getMessage());
            return "redirect:/admin/trips/approve/" + tripId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi hệ thống: " + e.getMessage());
            return "redirect:/admin/trips/approve/" + tripId;
        }
        return "redirect:/admin/trips/pending";
    }

    // =========================================================================
    // 5. TỪ CHỐI CHUYẾN TĂNG CƯỜNG
    // =========================================================================

    /**
     * Admin từ chối / hủy chuyến tăng cường (AI đề xuất nhưng không cần thiết).
     */
    @PostMapping("/reject/{id}")
    public String rejectTrip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tripService.rejectTrip(id);
            redirectAttributes.addFlashAttribute("success",
                    "Đã từ chối chuyến tăng cường #" + id + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/trips/pending";
    }
}