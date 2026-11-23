package giang.com.BusManagement.controller.admin;

import giang.com.BusManagement.dto.RegularTimetableRunDto;
import giang.com.BusManagement.service.RegularTimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Lịch thường lệ (route pairs CT6, CM-14): Admin xem các cặp tuyến có lịch và bấm nút tạo chuyến
 * thường lệ cho N ngày tới. Mọi luật nằm ở RegularTimetableService / TripService; controller chỉ
 * nhận đầu vào và hiện kết quả qua flash, theo khuôn AdminBusController.
 */
@Controller
@RequestMapping("/admin/timetable")
@RequiredArgsConstructor
public class AdminTimetableController {

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RegularTimetableService regularTimetableService;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("view", regularTimetableService.overview());
        return "admin/timetable";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam int days, RedirectAttributes redirectAttributes) {
        try {
            RegularTimetableRunDto run = regularTimetableService.generate(from, days);
            redirectAttributes.addFlashAttribute("run", run);
            redirectAttributes.addFlashAttribute("success", "Đã xếp lịch " + run.getFrom().format(DAY) + " → "
                    + run.getTo().format(DAY) + ": " + run.getCreated() + " chuyến mở bán, " + run.getPending()
                    + " chuyến chờ phân công xe.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/timetable";
    }
}
