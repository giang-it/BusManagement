package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/stations")
@RequiredArgsConstructor
public class AdminStationController {

    private final StationService stationService;

    @GetMapping
    public String listStations(Model model) {
        model.addAttribute("stations", stationService.findAll());
        return "admin/station/station-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("station", new Station());
        return "admin/station/station-form";
    }

    @PostMapping("/create")
    public String createStation(@ModelAttribute Station station, RedirectAttributes redirectAttributes) {
        try {
            stationService.save(station);
            redirectAttributes.addFlashAttribute("success", "Thêm mới bến xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/stations";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Station station = stationService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bến xe với ID: " + id));
            model.addAttribute("station", station);
            return "admin/station/station-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/stations";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateStation(@PathVariable Long id, @ModelAttribute Station station,
            RedirectAttributes redirectAttributes) {
        try {
            station.setId(id);
            stationService.save(station);
            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin bến xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/stations";
    }

    @GetMapping("/delete/{id}")
    public String deleteStation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            stationService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa bến xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/stations";
    }
}