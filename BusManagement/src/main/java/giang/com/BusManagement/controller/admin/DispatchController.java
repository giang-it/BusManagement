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
import java.util.List;

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
     */
    @PostMapping("/status")
    public String changeStatus(@RequestParam Long tripId,
            @RequestParam TripStatus newStatus,
            RedirectAttributes redirectAttributes) {
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
