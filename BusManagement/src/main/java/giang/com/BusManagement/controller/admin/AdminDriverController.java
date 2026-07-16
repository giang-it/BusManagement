package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Driver;
import giang.com.BusManagement.domain.User;
import giang.com.BusManagement.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/drivers")
@RequiredArgsConstructor
public class AdminDriverController {

    private final DriverService driverService;

    @GetMapping
    public String listDrivers(Model model) {
        model.addAttribute("drivers", driverService.findAllWithUser());
        return "admin/driver/driver-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("driver", new Driver());
        model.addAttribute("user", new User());
        return "admin/driver/driver-form";
    }

    /**
     * Nhận 2 object riêng (User + Driver) từ cùng một form, không cần DTO trung
     * gian: @ModelAttribute bind theo TÊN FIELD PHẲNG, và User/Driver không có
     * field nào trùng tên nhau (username/fullName/email/phone thuộc User;
     * licenseNumber/licenseExpiryDate/experienceYears/isActive thuộc Driver) nên
     * mỗi tham số chỉ khớp đúng một trong hai object.
     */
    @PostMapping("/create")
    public String createDriver(@ModelAttribute("user") User user,
            @ModelAttribute("driver") Driver driver,
            RedirectAttributes redirectAttributes) {
        try {
            driverService.createDriver(user, driver);
            redirectAttributes.addFlashAttribute("success", "Thêm mới tài xế thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/drivers/create";
        }
        return "redirect:/admin/drivers";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Driver driver = driverService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế với ID: " + id));
            model.addAttribute("driver", driver);
            model.addAttribute("user", driver.getUser());
            return "admin/driver/driver-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/drivers";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateDriver(@PathVariable Long id,
            @ModelAttribute("user") User user,
            @ModelAttribute("driver") Driver driver,
            @RequestParam(required = false) String rawPassword,
            RedirectAttributes redirectAttributes) {
        try {
            driverService.updateDriver(id, user, driver, rawPassword);
            redirectAttributes.addFlashAttribute("success", "Cập nhật hồ sơ tài xế thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/drivers/edit/" + id;
        }
        return "redirect:/admin/drivers";
    }

    @GetMapping("/delete/{id}")
    public String deleteDriver(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            driverService.deleteDriver(id);
            redirectAttributes.addFlashAttribute("success", "Xóa tài xế thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/drivers";
    }
}
