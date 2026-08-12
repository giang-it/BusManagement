package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public String showApproveForm(@PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        Trip trip = tripService.getTripById(id);

        // Lớp KHÔNG-MỜI (lỗi #20). Chặn thật nằm ở TripService.requirePendingApproval();
        // chỗ này chỉ để form đừng MỜI một thao tác mà service chắc chắn từ chối.
        //
        // Cùng phân vai với showEditTripForm() của lỗi #18: template quyết định cái
        // gì được MỜI, service quyết định cái gì được NHẬN. Thiếu lớp này, bản sửa
        // #20 sẽ tự tạo ra đúng anti-pattern "mời thứ sẽ từ chối" mà dự án vừa bác
        // khi revert #19 ngày 2026-08-11 — trước bản sửa thì form này submit được
        // (chính là lỗi), sau bản sửa thì nó sẽ hiện ra rồi báo lỗi khi bấm.
        //
        // Hàng chờ duyệt (pending-trips.html) vốn chỉ liệt kê PENDING_APPROVAL, nên
        // đây là chặn cho URL gõ tay / bookmark cũ, không phải cho luồng thường.
        if (trip.getStatus() != TripStatus.PENDING_APPROVAL) {
            redirectAttributes.addFlashAttribute("error",
                    "Chuyến #" + id + " đang ở trạng thái " + trip.getStatus()
                            + ", không còn nằm trong hàng chờ duyệt nên không mở được form phê duyệt.");
            return "redirect:/admin/trips/pending";
        }

        model.addAttribute("trip", trip);

        boolean isAutoAssigned = (trip.getBus() != null && trip.getDriver() != null);
        model.addAttribute("isAutoAssigned", isAutoAssigned);

        if (!isAutoAssigned) {
            // MANUAL MODE: Chỉ load danh sách khi AI không tự phân công được
            List<Driver> availableDrivers = tripService.getAvailableDriversForTrip(id);
            model.addAttribute("availableBuses", tripService.getAvailableBusesForTrip(id));
            model.addAttribute("availableDrivers", availableDrivers);
            // Phụ xe không trực tiếp lái xe nên dùng danh sách riêng, không áp ràng buộc
            // giờ lái tối đa 8h/ngày (xem TripService.getAvailableAssistantsForTrip)
            model.addAttribute("availableAssistants", tripService.getAvailableAssistantsForTrip(id));
            model.addAttribute("approveDriversForJs", toDriverOptionsForJs(availableDrivers));
        }

        return "admin/approve-form";
    }

    /**
     * Rút gọn danh sách tài xế thành map phẳng để đẩy sang khối
     * {@code <script th:inline="javascript">} dựng dropdown tài xế phụ.
     *
     * KHÔNG được inline thẳng entity Driver vào JavaScript: Thymeleaf serialize cả
     * đồ thị đối tượng và rơi vào vòng vô hạn Driver.user → User.driver → … →
     * StackOverflowError, cắt cụt response ngay giữa lúc ghi (trang vẫn trả 200
     * nhưng thiếu cả thẻ đóng, và toàn bộ JS tài xế phụ chết).
     *
     * Cùng khuôn với driversForJs ở AdminTripManagementController.showEditTripForm()
     * — màn sửa chuyến làm đúng nhiệm vụ này từ đầu, approve-form chỉ bị sót.
     *
     * Ba khoá dưới đây là đúng những gì JS đọc (approve-form.html); giữ nguyên tên
     * khoá totalDrivingHours24h và giá trị thô của nó để dropdown tài xế phụ hiển
     * thị cùng số giờ với dropdown tài xế chính ngay phía trên.
     */
    private List<Map<String, Object>> toDriverOptionsForJs(List<Driver> drivers) {
        return drivers.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", d.getUserId());
            map.put("fullName", d.getUser() != null ? d.getUser().getFullName() : "ID: " + d.getUserId());
            map.put("totalDrivingHours24h", d.getTotalDrivingHours24h());
            return map;
        }).toList();
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
        } catch (IllegalStateException e) {
            // Xem ghi chú cùng loại ở processManualApproval(): chuyến không còn ở
            // PENDING_APPROVAL thì form /approve/{id} cũng từ chối mở, nên quay lại đó
            // chỉ tạo thêm một lần chuyển hướng để tới cùng một chỗ (lỗi #20).
            redirectAttributes.addFlashAttribute("error", "⛔ " + e.getMessage());
            return "redirect:/admin/trips/pending";
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
        } catch (IllegalStateException e) {
            // Vi phạm chính sách theo TRẠNG THÁI (lỗi #20: chuyến không còn ở
            // PENDING_APPROVAL). Tách khỏi nhánh Exception bên dưới vì "Lỗi hệ thống"
            // sẽ đổ cho hệ thống hỏng, trong khi đây là một thao tác không hợp lệ và
            // hệ thống đang hoạt động đúng. Cùng cách phân biệt mà
            // AdminTripManagementController.updateTrip() đã dùng cho ngoại lệ FSM.
            //
            // Về đích redirect: quay lại /approve/{id} sẽ vô nghĩa vì form đó nay cũng
            // từ chối mở cho chuyến không phải PENDING_APPROVAL — sẽ thành hai lần
            // chuyển hướng để tới cùng một chỗ. Đi thẳng về hàng chờ.
            redirectAttributes.addFlashAttribute("error", "⛔ " + e.getMessage());
            return "redirect:/admin/trips/pending";
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