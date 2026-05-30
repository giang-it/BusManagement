package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Bus;
import giang.com.BusManagement.domain.BusStatus;
import giang.com.BusManagement.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/buses")
@RequiredArgsConstructor
public class AdminBusController {

    private final BusService busService;

    @GetMapping
    public String listBuses(Model model) {
        model.addAttribute("buses", busService.findAllWithBusType());
        return "admin/bus/bus-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("bus", new Bus());
        model.addAttribute("busTypes", busService.findAllBusTypes());
        model.addAttribute("statuses", BusStatus.values());
        return "admin/bus/bus-form";
    }

    @PostMapping("/create")
    public String createBus(@ModelAttribute Bus bus, RedirectAttributes redirectAttributes) {
        try {
            busService.saveBus(bus);
            redirectAttributes.addFlashAttribute("success", "Thêm mới xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/buses";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Bus bus = busService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy xe với ID: " + id));
            model.addAttribute("bus", bus);
            model.addAttribute("busTypes", busService.findAllBusTypes());
            model.addAttribute("statuses", BusStatus.values());
            return "admin/bus-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/buses";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateBus(@PathVariable Long id, @ModelAttribute Bus bus, RedirectAttributes redirectAttributes) {
        try {
            bus.setId(id);
            busService.saveBus(bus);
            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/buses";
    }

    @GetMapping("/delete/{id}")
    public String deleteBus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            busService.deleteBus(id);
            redirectAttributes.addFlashAttribute("success", "Xóa xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/buses";
    }
}