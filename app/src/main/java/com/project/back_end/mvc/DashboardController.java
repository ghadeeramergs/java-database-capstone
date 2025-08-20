package com.project.back_end.mvc;


import com.project.back_end.services.Service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class DashboardController {

    private final Service sharedService; // common service with validateToken(...)

    public DashboardController(Service sharedService) {
        this.sharedService = sharedService;
    }

    // Allow dots in JWT path var with :.+ regex
    @GetMapping("/adminDashboard/{token:.+}")
    public String adminDashboard(@PathVariable String token, Model model) {
        ResponseEntity<Map<String, Object>> resp = sharedService.validateToken(token, "admin");
        Map<String, Object> body = resp.getBody();
        boolean valid = resp.getStatusCode().is2xxSuccessful()
                        && body != null
                        && Boolean.TRUE.equals(body.get("valid"));

        if (valid) {
            model.addAttribute("subject", body.get("subject"));
            return "admin/adminDashboard";
        }
        return "redirect:/";
    }

    @GetMapping("/doctorDashboard/{token:.+}")
    public String doctorDashboard(@PathVariable String token, Model model) {
        ResponseEntity<Map<String, Object>> resp = sharedService.validateToken(token, "doctor");
        Map<String, Object> body = resp.getBody();
        boolean valid = resp.getStatusCode().is2xxSuccessful()
                        && body != null
                        && Boolean.TRUE.equals(body.get("valid"));

        if (valid) {
            model.addAttribute("subject", body.get("subject"));
            return "doctor/doctorDashboard";
        }
        return "redirect:/";
    }
}

