package giang.com.BusManagement.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import giang.com.BusManagement.service.TripService;

@Controller
@RequestMapping("/admin/trips")
public class AdminController {

    @Autowired
    private TripService tripService;

    @GetMapping("/suggestions")
    public String viewSuggestions(Model model) {
        model.addAttribute("suggestions", tripService.getPendingTrips());
        return "admin/suggestions";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Long id) {
        tripService.approveTrip(id);
        return "redirect:/admin/trips/suggestions";
    }
}
