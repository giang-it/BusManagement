package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.BusType;
import giang.com.BusManagement.domain.Station;
import giang.com.BusManagement.repository.BusTypeRepository;
import giang.com.BusManagement.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/master-data")
@RequiredArgsConstructor
public class AdminMasterDataController {

    private final StationRepository stationRepository;
    private final BusTypeRepository busTypeRepository;

    // ==================== QUẢN LÝ TRẠM (STATION) ====================

    /**
     * Danh sách tất cả trạm
     */
    @GetMapping("/stations")
    public String listStations(Model model) {
        model.addAttribute("stations", stationRepository.findAll());
        return "admin/station-list";
    }

    /**
     * Hiển thị form thêm/sửa trạm
     */
    @GetMapping("/stations/form")
    public String showStationForm(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            Station station = stationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy trạm"));
            model.addAttribute("station", station);
        } else {
            model.addAttribute("station", new Station());
        }
        return "admin/station-form";
    }

    /**
     * Lưu trạm
     */
    @PostMapping("/stations/save")
    public String saveStation(@ModelAttribute Station station, RedirectAttributes redirectAttributes) {
        try {
            stationRepository.save(station);
            redirectAttributes.addFlashAttribute("success", "Lưu trạm thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/master-data/stations";
    }

    /**
     * Xóa trạm
     */
    @PostMapping("/stations/delete/{id}")
    public String deleteStation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            stationRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa trạm thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa trạm này. Có thể đang được sử dụng.");
        }
        return "redirect:/admin/master-data/stations";
    }

    // ==================== QUẢN LÝ LOẠI XE (BUS TYPE) ====================

    /**
     * Danh sách tất cả loại xe
     */
    @GetMapping("/bus-types")
    public String listBusTypes(Model model) {
        model.addAttribute("busTypes", busTypeRepository.findAll());
        return "admin/bus-type-list";
    }

    /**
     * Hiển thị form thêm/sửa loại xe
     */
    @GetMapping("/bus-types/form")
    public String showBusTypeForm(@RequestParam(required = false) Integer id, Model model) {
        if (id != null) {
            BusType busType = busTypeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy loại xe"));
            model.addAttribute("busType", busType);
        } else {
            model.addAttribute("busType", new BusType());
        }
        return "admin/bus-type-form";
    }

    /**
     * Lưu loại xe
     */
    @PostMapping("/bus-types/save")
    public String saveBusType(@ModelAttribute BusType busType, RedirectAttributes redirectAttributes) {
        try {
            busTypeRepository.save(busType);
            redirectAttributes.addFlashAttribute("success", "Lưu loại xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/master-data/bus-types";
    }

    /**
     * Xóa loại xe
     */
    @PostMapping("/bus-types/delete/{id}")
    public String deleteBusType(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            busTypeRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Xóa loại xe thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa loại xe này. Có thể đang được sử dụng.");
        }
        return "redirect:/admin/master-data/bus-types";
    }
}