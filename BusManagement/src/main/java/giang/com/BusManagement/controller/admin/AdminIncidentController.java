package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import giang.com.BusManagement.domain.IncidentType;
import giang.com.BusManagement.service.BusService;
import giang.com.BusManagement.service.DriverService;
import giang.com.BusManagement.service.IncidentService;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/incidents")
@RequiredArgsConstructor
public class AdminIncidentController {

    private final IncidentService incidentService;
    private final BusService busService;
    private final DriverService driverService;
    private final TripService tripService;

    @GetMapping
    public String listIncidents(Model model) {
        model.addAttribute("incidents", incidentService.findAllWithDetails());
        model.addAttribute("openCount", incidentService.countByStatus(IncidentStatus.OPEN));
        model.addAttribute("inProgressCount", incidentService.countByStatus(IncidentStatus.IN_PROGRESS));
        model.addAttribute("resolvedCount", incidentService.countByStatus(IncidentStatus.RESOLVED));
        return "admin/incident/incident-list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("incident", new Incident());
        addFormOptions(model);
        return "admin/incident/incident-form";
    }

    /**
     * Các trường bus/trip/driver trên form gửi lên id của entity; Spring Data
     * (DomainClassConverter) tự đổi id -> entity, và chuỗi rỗng -> null cho 2
     * trường tùy chọn. Cùng cách bus-form.html đang bind BusType.
     */
    @PostMapping("/create")
    public String createIncident(@ModelAttribute Incident incident, RedirectAttributes redirectAttributes) {
        try {
            incidentService.createIncident(incident);
            redirectAttributes.addFlashAttribute("success", "Đã ghi nhận sự cố mới!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/incidents/create";
        }
        return "redirect:/admin/incidents";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Incident incident = incidentService.findByIdWithDetails(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sự cố với ID: " + id));
            model.addAttribute("incident", incident);
            addFormOptions(model);
            return "admin/incident/incident-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/incidents";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateIncident(@PathVariable Long id, @ModelAttribute Incident incident,
            RedirectAttributes redirectAttributes) {
        try {
            incidentService.updateIncident(id, incident);
            redirectAttributes.addFlashAttribute("success", "Cập nhật sự cố thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/admin/incidents/edit/" + id;
        }
        return "redirect:/admin/incidents";
    }

    @GetMapping("/delete/{id}")
    public String deleteIncident(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            incidentService.deleteIncident(id);
            redirectAttributes.addFlashAttribute("success", "Xóa bản ghi sự cố thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/incidents";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("buses", busService.findAllWithBusType());
        model.addAttribute("drivers", driverService.findAllWithUser());
        model.addAttribute("trips", tripService.getAllTrips());
        model.addAttribute("incidentTypes", IncidentType.values());
        model.addAttribute("incidentStatuses", IncidentStatus.values());
    }
}
