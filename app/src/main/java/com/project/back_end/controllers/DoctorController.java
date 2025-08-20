package com.project.back_end.controllers;


import com.project.back_end.models.Doctor;
import com.project.back_end.services.DoctorService;
import com.project.back_end.services.Service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api.path}doctor")
public class DoctorController {

    private final DoctorService doctorService;
    private final Service sharedService; // token/filters

    public DoctorController(DoctorService doctorService, Service sharedService) {
        this.doctorService = doctorService;
        this.sharedService = sharedService;
    }

    // 3) Check availability (token validated against user type)
    @GetMapping("/availability/{user}/{doctorId}/{date}/{token:.+}")
    public ResponseEntity<Map<String, Object>> getDoctorAvailability(
            @PathVariable String user,
            @PathVariable Long doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String token) {

        var validation = sharedService.validateToken(token, user);
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }
        return doctorService.getDoctorAvailability(doctorId, date);
    }

    // 4) Get all doctors
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDoctor() {
        List<Doctor> doctors = doctorService.getDoctors();
        Map<String, Object> body = new HashMap<>();
        body.put("doctors", doctors);
        body.put("count", doctors.size());
        return ResponseEntity.ok(body);
    }

    // 5) Register new doctor (admin only)
    @PostMapping("/{token:.+}")
    public ResponseEntity<Map<String, Object>> saveDoctor(
            @PathVariable String token,
            @Valid @RequestBody Doctor doctor) {

        var validation = sharedService.validateToken(token, "admin");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Map<String, Object> body = new HashMap<>();
        int res = doctorService.saveDoctor(doctor);
        if (res == 1) {
            body.put("message", "Doctor created");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } else if (res == -1) {
            body.put("message", "Doctor with this email already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } else {
            body.put("message", "Failed to create doctor");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 6) Doctor login
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> doctorLogin(@Valid @RequestBody Login login) {
        return doctorService.validateDoctor(login.getEmail(), login.getPassword());
    }

    // 7) Update doctor (admin only)
    @PutMapping("/{token:.+}")
    public ResponseEntity<Map<String, Object>> updateDoctor(
            @PathVariable String token,
            @Valid @RequestBody Doctor doctor) {

        var validation = sharedService.validateToken(token, "admin");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Map<String, Object> body = new HashMap<>();
        int res = doctorService.updateDoctor(doctor);
        if (res == 1) {
            body.put("message", "Doctor updated");
            return ResponseEntity.ok(body);
        } else if (res == -1) {
            body.put("message", "Doctor not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        } else {
            body.put("message", "Failed to update doctor");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 8) Delete doctor (admin only)
    @DeleteMapping("/{doctorId}/{token:.+}")
    public ResponseEntity<Map<String, Object>> deleteDoctor(
            @PathVariable Long doctorId,
            @PathVariable String token) {

        var validation = sharedService.validateToken(token, "admin");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Map<String, Object> body = new HashMap<>();
        int res = doctorService.deleteDoctor(doctorId);
        if (res == 1) {
            body.put("message", "Doctor deleted");
            return ResponseEntity.ok(body);
        } else if (res == -1) {
            body.put("message", "Doctor not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        } else {
            body.put("message", "Failed to delete doctor");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 9) Filter doctors by name, time slot, and specialty
    @GetMapping("/filter/{name}/{time}/{speciality}")
    public ResponseEntity<Map<String, Object>> filter(
            @PathVariable String name,
            @PathVariable String time,
            @PathVariable("speciality") String specialty) {

        // Treat "any" or "null" as empty filters
        String n = normalize(name);
        String t = normalize(time);
        String s = normalize(specialty);

        var doctors = sharedService.filterDoctor(n, s, t);
        Map<String, Object> body = new HashMap<>();
        body.put("count", doctors.size());
        body.put("doctors", doctors);
        return ResponseEntity.ok(body);
    }

    private String normalize(String v) {
        if (v == null) return null;
        String x = v.trim();
        return (x.equalsIgnoreCase("any") || x.equalsIgnoreCase("null")) ? "" : x;
    }

    // Simple login DTO (email/password)
    public static class Login {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String password;

        public Login() {}
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
