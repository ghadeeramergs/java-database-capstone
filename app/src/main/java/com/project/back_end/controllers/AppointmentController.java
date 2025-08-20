package com.project.back_end.controllers;

import com.project.back_end.models.Appointment;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.Service;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final Service sharedService; // common service with validateToken(...)

    public AppointmentController(AppointmentService appointmentService, Service sharedService) {
        this.appointmentService = appointmentService;
        this.sharedService = sharedService;
    }

    // 3) Get appointments for a doctor on a date (optionally filtered by patient name)
    // NOTE: doctorId is required here to resolve which doctor's schedule to read.
    // If you prefer deriving it from the token (doctor email), inject DoctorRepository and look up by email.
    @GetMapping("/{token:.+}/{date}")
    public ResponseEntity<Map<String, Object>> getAppointments(
            @PathVariable String token,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "patientName", required = false) String patientName) {

        var validation = sharedService.validateToken(token, "doctor");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        return appointmentService.getAppointments(doctorId, date, patientName);
    }

    // 4) Book appointment (patient)
    // Expects JSON like: {"doctor":{"id":1},"appointmentTime":"2025-09-01T09:00:00"}
    @PostMapping("/book/{token:.+}")
    public ResponseEntity<Map<String, Object>> bookAppointment(
            @PathVariable String token,
            @RequestBody Appointment request) {

        var validation = sharedService.validateToken(token, "patient");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Map<String, Object> body = new HashMap<>();
        if (request == null || request.getDoctor() == null || request.getDoctor().getId() == null
            || request.getAppointmentTime() == null) {
            body.put("message", "doctor.id and appointmentTime are required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        int ok = appointmentService.bookAppointment(
                token, request.getDoctor().getId(), request.getAppointmentTime());

        if (ok == 1) {
            body.put("message", "Appointment booked");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } else {
            body.put("message", "Failed to book appointment (invalid slot/doctor or internal error)");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
    }

    // 5) Update appointment (patient)
    // Accepts partial JSON: {"doctor":{"id":2}, "appointmentTime":"2025-09-01T10:00:00", "status":0}
    @PutMapping("/{token:.+}/{appointmentId}")
    public ResponseEntity<Map<String, Object>> updateAppointment(
            @PathVariable String token,
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> payload) {

        var validation = sharedService.validateToken(token, "patient");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        Long newDoctorId = null;
        LocalDateTime newStart = null;
        Integer newStatus = null;

        // Extract nested doctor.id if present
        if (payload != null && payload.get("doctor") instanceof Map<?, ?> docMap) {
            Object idVal = ((Map<?, ?>) docMap).get("id");
            if (idVal instanceof Number n) newDoctorId = n.longValue();
        }
        // Extract appointmentTime if present
        if (payload != null && payload.get("appointmentTime") instanceof String s && !s.isBlank()) {
            newStart = LocalDateTime.parse(s);
        }
        // Extract status if present
        if (payload != null && payload.get("status") instanceof Number n) {
            newStatus = n.intValue();
        }

        return appointmentService.updateAppointment(token, appointmentId, newDoctorId, newStart, newStatus);
    }

    // 6) Cancel appointment (patient)
    @DeleteMapping("/{token:.+}/{appointmentId}")
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable String token,
            @PathVariable Long appointmentId) {

        var validation = sharedService.validateToken(token, "patient");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        return appointmentService.cancelAppointment(token, appointmentId);
    }
}

