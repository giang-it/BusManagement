package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bảng điều hành (Dispatch Center) — màn hình vận hành theo thời gian thực cho
 * Admin: chuyến nào đang trên đường, chuyến nào sắp khởi hành, chuyến nào đã trễ
 * giờ mà chưa xuất phát.
 *
 * Chỉ đọc dữ liệu và ủy quyền mọi thay đổi trạng thái cho
 * TripService.updateTripStatus() — FSM và việc đồng bộ BusStatus vẫn nằm nguyên
 * ở đó, màn hình này không tự chuyển trạng thái.
 */
@Controller
@RequestMapping("/admin/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final TripRepository tripRepository;
    private final TripService tripService;

    /**
     * Cửa sổ "sắp khởi hành" — dùng lại đúng ngưỡng 48h mà Dashboard đang dùng
     * cho KPI "Upcoming Trips" (DashboardService.UPCOMING_TRIPS_WINDOW_HOURS),
     * không định nghĩa ngưỡng mới cho cùng một khái niệm.
     */
    private static final int UPCOMING_WINDOW_HOURS = 48;

    private static final List<TripStatus> BOARD_STATUSES = List.of(TripStatus.ACTIVE, TripStatus.DEPARTED);

    /**
     * Các trạng thái đích mà bảng điều hành THỰC SỰ phơi ra — đúng ba nút của
     * dispatch-board.html: "Xuất phát" (DEPARTED), "Hủy chuyến" (CANCELLED),
     * "Hoàn thành" (COMPLETED). Khác với BOARD_STATUSES ở trên, vốn là bộ lọc
     * chuyến nào được hiển thị.
     *
     * VÌ SAO PHẢI CHẶN Ở ĐÂY, KHÔNG TIN VÀO FSM: updateTripStatus() chỉ kiểm tra
     * transition có HỢP LỆ hay không (canTransition) — nó KHÔNG chạy
     * validateBusForTrip()/validateStaffForTrip(). Cổng ràng buộc nghiệp vụ nằm ở
     * confirmAutoAssignedTrip()/approveTrip(). Thiếu allow-list này, một request
     * PENDING_APPROVAL → ACTIVE sẽ kích hoạt chuyến (mở bán vé, đóng dấu
     * saleOpenedAt) mà KHÔNG ràng buộc nào được kiểm: xe quá hạn bảo trì, tài xế
     * hết bằng, trùng lịch, thậm chí chuyến chưa có xe lẫn tài xế.
     * Xem docs/todo/current_bugs_found.md mục #10.
     */
    private static final Set<TripStatus> BOARD_ACTIONS = EnumSet.of(
            TripStatus.DEPARTED, TripStatus.CANCELLED, TripStatus.COMPLETED);

    @GetMapping
    public String viewBoard(Model model) {
        LocalDateTime now = LocalDateTime.now();
        List<Trip> boardTrips = tripRepository.findDispatchBoardTrips(
                BOARD_STATUSES, now.plusHours(UPCOMING_WINDOW_HOURS));

        // Đang chạy: đã xuất phát, chưa hoàn thành.
        List<Trip> inProgress = boardTrips.stream()
                .filter(t -> t.getStatus() == TripStatus.DEPARTED)
                .sorted(Comparator.comparing(Trip::getDepartureTime))
                .toList();

        // Trễ giờ: đến giờ chạy rồi mà vẫn ACTIVE (chưa bấm xuất phát) — nhóm cần
        // Admin xử lý gấp nhất nên tách riêng khỏi nhóm sắp khởi hành.
        List<Trip> overdue = boardTrips.stream()
                .filter(t -> t.getStatus() == TripStatus.ACTIVE && !t.getDepartureTime().isAfter(now))
                .sorted(Comparator.comparing(Trip::getDepartureTime))
                .toList();

        List<Trip> upcoming = boardTrips.stream()
                .filter(t -> t.getStatus() == TripStatus.ACTIVE && t.getDepartureTime().isAfter(now))
                .sorted(Comparator.comparing(Trip::getDepartureTime))
                .toList();

        model.addAttribute("inProgressTrips", inProgress);
        model.addAttribute("overdueTrips", overdue);
        model.addAttribute("upcomingTrips", upcoming);
        model.addAttribute("windowHours", UPCOMING_WINDOW_HOURS);
        model.addAttribute("now", now);
        return "admin/dispatch-board";
    }

    /**
     * Đổi trạng thái nhanh từ bảng điều hành. Không tự quyết định transition nào
     * hợp lệ — FSM trong TripService.updateTripStatus() là nơi kiểm tra và sẽ ném
     * IllegalStateException nếu transition sai.
     *
     * Chỉ giới hạn TẬP TRẠNG THÁI ĐÍCH mà màn hình này phơi ra (BOARD_ACTIONS):
     * đó là trách nhiệm của controller, không phải của FSM. Đặc biệt ACTIVE bị
     * loại — kích hoạt chuyến phải đi qua màn Phê Duyệt, nơi các validator nghiệp
     * vụ thực sự chạy. Xem javadoc của BOARD_ACTIONS.
     */
    @PostMapping("/status")
    public String changeStatus(@RequestParam Long tripId,
            @RequestParam TripStatus newStatus,
            RedirectAttributes redirectAttributes) {
        if (!BOARD_ACTIONS.contains(newStatus)) {
            redirectAttributes.addFlashAttribute("error",
                    "Bảng điều hành không hỗ trợ chuyển chuyến #" + tripId + " sang trạng thái "
                            + newStatus + ". Việc kích hoạt chuyến phải thực hiện ở màn Phê Duyệt "
                            + "để hệ thống kiểm tra đầy đủ ràng buộc xe, tài xế và lịch chạy.");
            return "redirect:/admin/dispatch";
        }
        try {
            tripService.updateTripStatus(tripId, newStatus);
            redirectAttributes.addFlashAttribute("success",
                    "Đã cập nhật chuyến #" + tripId + " sang trạng thái " + newStatus + ".");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "Không thể đổi trạng thái: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/dispatch";
    }
}
