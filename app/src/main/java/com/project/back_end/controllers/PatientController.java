package com.project.back_end.controllers;

import com.project.back_end.models.Patient;
import com.project.back_end.services.PatientService;
import com.project.back_end.services.Service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/patient")
public class PatientController {

    private final PatientService patientService;
    private final Service sharedService;

    public PatientController(PatientService patientService, Service sharedService) {
        this.patientService = patientService;
        this.sharedService = sharedService;
    }

    // 3) Get patient details (token → patient)
    @GetMapping("/{token:.+}")
    public ResponseEntity<Map<String, Object>> getPatient(@PathVariable String token) {
        var validation = sharedService.validateToken(token, "patient");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }
        return patientService.getPatientDetails(token);
    }

    // 4) Register patient
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPatient(@Valid @RequestBody Patient patient) {
        Map<String, Object> body = new HashMap<>();
        boolean okToRegister = sharedService.validatePatient(patient.getEmail(), patient.getPhone());
        if (!okToRegister) {
            body.put("message", "Patient with same email or phone already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        int res = patientService.createPatient(patient);
        if (res == 1) {
            body.put("message", "Patient created");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } else {
            body.put("message", "Failed to create patient");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 5) Patient login
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Login login) {
        return sharedService.validatePatientLogin(login.getEmail(), login.getPassword());
    }

    // 6) Get appointments for a patient (validate token for provided user role)
    @GetMapping("/appointments/{user}/{patientId}/{token:.+}")
    public ResponseEntity<Map<String, Object>> getPatientAppointment(
            @PathVariable String user,
            @PathVariable Long patientId,
            @PathVariable String token) {

        var validation = sharedService.validateToken(token, user);
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }
        return patientService.getPatientAppointment(patientId);
    }

    // 7) Filter a patient's appointments (condition=name status; name=doctor)
    // Example: /patient/appointments/filter/{token}?condition=past&name=omar
    @GetMapping("/appointments/filter/{token:.+}")
    public ResponseEntity<Map<String, Object>> filterPatientAppointment(
            @PathVariable String token,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false, name = "name") String doctorName) {

        var validation = sharedService.validateToken(token, "patient");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Integer status = null;
        if (condition != null) {
            switch (condition.trim().toLowerCase()) {
                case "past" -> status = 1;
                case "future" -> status = 0;
                default -> {
                    Map<String, Object> err = new HashMap<>();
                    err.put("message", "Invalid condition. Use 'past' or 'future'.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
                }
            }
        }

        var list = sharedService.filterPatient(token, status, (doctorName == null ? null : doctorName.trim()));
        Map<String, Object> body = new HashMap<>();
        body.put("count", list.size());
        body.put("appointments", list);
        return ResponseEntity.ok(body);
    }

    // Simple login DTO
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
