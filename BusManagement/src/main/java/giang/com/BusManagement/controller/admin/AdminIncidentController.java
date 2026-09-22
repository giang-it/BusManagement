package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.domain.Incident;
import giang.com.BusManagement.domain.IncidentStatus;
import giang.com.BusManagement.domain.IncidentType;
import giang.com.BusManagement.domain.Trip;
import giang.com.BusManagement.service.BusService;
import giang.com.BusManagement.service.DriverService;
import giang.com.BusManagement.service.IncidentService;
import giang.com.BusManagement.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/incidents")
@RequiredArgsConstructor
public class AdminIncidentController {

    private final IncidentService incidentService;
    private final BusService busService;
    private final DriverService driverService;
    private final TripService tripService;

    /**
     * Dropdown "Chuyến xe liên quan" mời các chuyến đang vận hành (ACTIVE/DEPARTED)
     * cộng chuyến đã kết thúc (COMPLETED/CANCELLED) khởi hành trong từng này ngày
     * gần đây. Là PHẠM VI MỜI của form — cửa sổ do controller sở hữu, service nhận
     * mốc, cùng cách DispatchController.UPCOMING_WINDOW_HOURS ↔
     * TripService.getDispatchBoardTrips(until) — không phải luật nghiệp vụ:
     * IncidentService không kiểm chuyến thuộc cửa sổ nào. Form sửa luôn hiện thêm
     * chuyến đang gắn dù đã ra khỏi cửa sổ (xem addFormOptions).
     */
    private static final int RECENT_TRIP_DAYS = 7;

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
        Incident incident = new Incident();
        model.addAttribute("incident", incident);
        addFormOptions(model, incident);
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
            addFormOptions(model, incident);
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

    private void addFormOptions(Model model, Incident incident) {
        model.addAttribute("buses", busService.findAllWithBusType());
        model.addAttribute("drivers", driverService.findAllWithUser());

        List<Trip> trips = new ArrayList<>(
                tripService.getRunningAndRecentTrips(LocalDateTime.now().minusDays(RECENT_TRIP_DAYS)));
        // Form sửa: chuyến đang gắn có thể đã ra khỏi cửa sổ — vẫn phải hiện, nếu
        // không lựa chọn hiện tại "biến mất" khỏi chính form đang sửa nó (cùng lý do
        // showEditTripForm force-add xe hiện tại). contains() so theo id (Trip
        // equals id-based, Hidden Cost #9); findByIdWithDetails đã fetch trip.route
        // nên option hiển thị được tuyến.
        if (incident.getTrip() != null && !trips.contains(incident.getTrip())) {
            trips.add(0, incident.getTrip());
        }
        model.addAttribute("trips", trips);
        model.addAttribute("recentTripDays", RECENT_TRIP_DAYS);
        model.addAttribute("incidentTypes", IncidentType.values());
        model.addAttribute("incidentStatuses", IncidentStatus.values());
    }
}
