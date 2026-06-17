package giang.com.BusManagement.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import giang.com.BusManagement.domain.Role;
import giang.com.BusManagement.domain.TripStatus;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.repository.BusRepository;
import giang.com.BusManagement.repository.TripRepository;
import giang.com.BusManagement.repository.UserRepository;
import giang.com.BusManagement.service.AdminService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final BusRepository busRepository;
    private final TripRepository tripRepository; // Giả định bạn đã có

    // Hiển thị trang Dashboard chính
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Lấy số liệu thống kê cho các thẻ (Cards)
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalBuses", busRepository.count());
        model.addAttribute("pendingTrips", tripRepository.countByStatus(TripStatus.PENDING_APPROVAL));

        return "admin/dashboard";
    }

    // FORM TẠO NGƯỜI DÙNG
    @GetMapping("/users/new")
    public String showUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", Role.values());
        return "admin/user-form";
    }

    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute("user") User user) {
        adminService.createNewUser(user);
        return "redirect:/admin/dashboard?success=user";
    }

}