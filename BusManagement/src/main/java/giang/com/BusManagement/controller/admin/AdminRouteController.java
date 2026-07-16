package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Route;
import giang.com.BusManagement.service.BusService;
import giang.com.BusManagement.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final RouteService routeService;
    private final BusService busService;

    @GetMapping
    public String listRoutes(Model model) {
        model.addAttribute("routes", routeService.findAllWithStations());
        return "admin/route/route-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("route", new Route());
        model.addAttribute("selectedStationIds", List.of());
        addFormOptions(model);
        return "admin/route/route-form";
    }

    /**
     * @param stationIds id các bến xe theo đúng thứ tự lộ trình — thứ tự các
     *                   phần tử chính là thứ tự dừng, service dùng nó để đánh
     *                   stopOrder 1..n.
     */
    @PostMapping("/create")
    public String createRoute(@ModelAttribute Route route,
            @RequestParam(required = false) List<Long> stationIds,
            RedirectAttributes redirectAttributes) {
        try {
            routeService.saveRoute(route, stationIds);
            redirectAttributes.addFlashAttribute("success", "Thêm mới tuyến đường thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/routes/create";
        }
        return "redirect:/admin/routes";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Route route = routeService.findByIdWithStations(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tuyến với ID: " + id));

            // Lộ trình hiện tại, sắp theo stopOrder — dùng để dựng lại các dòng chọn
            // trạm trên form theo đúng thứ tự đã lưu.
            List<Long> selectedStationIds = route.getOrderedRouteStations().stream()
                    .map(rs -> rs.getStation().getId())
                    .toList();

            model.addAttribute("route", route);
            model.addAttribute("selectedStationIds", selectedStationIds);
            addFormOptions(model);
            return "admin/route/route-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/routes";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateRoute(@PathVariable Long id,
            @ModelAttribute Route route,
            @RequestParam(required = false) List<Long> stationIds,
            RedirectAttributes redirectAttributes) {
        try {
            route.setId(id);
            routeService.saveRoute(route, stationIds);
            redirectAttributes.addFlashAttribute("success", "Cập nhật tuyến đường thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/routes/edit/" + id;
        }
        return "redirect:/admin/routes";
    }

    @GetMapping("/delete/{id}")
    public String deleteRoute(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            routeService.deleteRoute(id);
            redirectAttributes.addFlashAttribute("success", "Xóa tuyến đường thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/routes";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("stations", routeService.findAllStations());
        model.addAttribute("busTypes", busService.findAllBusTypes());
    }
}
