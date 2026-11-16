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
            return "admin/bus/bus-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/buses";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateBus(@PathVariable Long id, @ModelAttribute Bus bus, RedirectAttributes redirectAttributes) {
        try {
            // Truyền id riêng thay vì bus.setId(id): service phải nạp bản ghi cũ để
            // biết ô số nào bị bỏ trống mà giữ nguyên (xem BusService.updateBus).
            busService.updateBus(id, bus);
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

    // Bến nhà (QĐ-23, CT5a): danh sách bến cho ô "Bến nhà" của form tạo / sửa. @ModelAttribute ở
    // mức lớp nên Spring tự đưa vào model của mọi trang thay vì sửa từng hàm hiện form; field và
    // kiểu viết đầy đủ, đặt ở cuối lớp để không xê dịch số dòng mà các bài học đang trích. Vẫn chỉ
    // tiêm Service, đúng mẫu AdminBusController (roadmap §3).
    private final giang.com.BusManagement.service.StationService stationService;

    @ModelAttribute("stations")
    public java.util.List<giang.com.BusManagement.domain.Station> stations() {
        return stationService.findAll();
    }
}