package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/trips")
@RequiredArgsConstructor
public class AdminTripController {

    private final TripService tripService;

    // 1. Trang danh sách các chuyến AI đề xuất (PENDING_APPROVAL)
    @GetMapping("/pending")
    public String viewPendingTrips(Model model) {
        model.addAttribute("pendingTrips", tripService.getPendingTrips());

        System.out.println("pending");

        return "admin/pending-trips";
    }

    // 2. Trang form để chọn Xe và Tài xế cho chuyến tăng cường
    @GetMapping("/approve/{id}")
    public String showApproveForm(@PathVariable Long id, Model model) {
        Trip trip = tripService.getTripById(id);
        model.addAttribute("trip", trip);

        // Chỉ lấy danh sách Xe và Tài xế RẢNH & HỢP LỆ
        model.addAttribute("availableBuses", tripService.getAvailableBusesForTrip(id));
        model.addAttribute("availableDrivers", tripService.getAvailableDriversForTrip(id));

        System.out.println("approving");
        System.out.println("bus: " + tripService.getAvailableBusesForTrip(id));
        System.out.println("driver: " + tripService.getAvailableDriversForTrip(id));

        return "admin/approve-form";
    }

    // 3. Xử lý lưu phê duyệt
    @PostMapping("/approve")
    public String processApproval(@RequestParam Long tripId,
            @RequestParam Long busId,
            @RequestParam Long driverId) {
        tripService.approveTrip(tripId, busId, driverId);

        System.out.println("approve sucess");

        return "redirect:/admin/trips/pending?success";
    }

}