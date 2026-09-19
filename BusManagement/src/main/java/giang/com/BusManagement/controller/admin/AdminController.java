package giang.com.BusManagement.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    // FORM TẠO NGƯỜI DÙNG (không phải tài xế — tài xế tạo ở /admin/drivers/create)
    @GetMapping("/users/new")
    public String showUserForm(Model model) {
        model.addAttribute("user", new User());
        // Lớp KHÔNG-MỜI: không liệt kê ROLE_DRIVER, vì một user tài xế phải đi kèm
        // hồ sơ Driver (@MapsId) và Phase 1 đã chốt tạo cặp đó bằng một form gộp ở
        // Quản Lý Tài Xế. Lớp chặn thật nằm ở AdminService.createNewUser() (lỗi #25).
        model.addAttribute("roles", java.util.Arrays.stream(Role.values())
                .filter(r -> r != Role.ROLE_DRIVER)
                .toList());
        return "admin/user-form";
    }

    /**
     * Lưu tài khoản mới. Bọc try/catch như mọi controller khác: trước đây đây là
     * endpoint ghi DUY NHẤT không bắt ngoại lệ, nên trùng username (unique ở DB) trả
     * thẳng HTTP 500 Whitelabel (lỗi #25); nay AdminService kiểm trước và mọi vi
     * phạm đầu vào hiện thành flash "Lỗi: …" trên chính form.
     */
    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute("user") User user, RedirectAttributes redirectAttributes) {
        try {
            adminService.createNewUser(user);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/users/new";
        }
        return "redirect:/admin/dashboard?success=user";
    }

}