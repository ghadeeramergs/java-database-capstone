package com.project.back_end.controllers;

import com.project.back_end.models.Prescription;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.PrescriptionService;
import com.project.back_end.services.Service;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/prescription")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final Service sharedService;
    private final AppointmentService appointmentService;

    public PrescriptionController(PrescriptionService prescriptionService,
                                  Service sharedService,
                                  AppointmentService appointmentService) {
        this.prescriptionService = prescriptionService;
        this.sharedService = sharedService;
        this.appointmentService = appointmentService;
    }

    // 3) Save a prescription (doctor only) and mark appointment completed on success
    @PostMapping("/{token:.+}")
    public ResponseEntity<Map<String, Object>> savePrescription(
            @PathVariable String token,
            @Valid @RequestBody Prescription prescription) {

        var validation = sharedService.validateToken(token, "doctor");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        if (prescription.getAppointmentId() == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "appointmentId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        ResponseEntity<Map<String, Object>> resp = prescriptionService.savePrescription(prescription);
        if (resp.getStatusCode() == HttpStatus.CREATED) {
            // status: 1 = Completed
            appointmentService.changeStatus(prescription.getAppointmentId(), 1);
        }
        return resp;
    }

    // 4) Get prescription(s) by appointmentId (doctor only)
    @GetMapping("/{appointmentId}/{token:.+}")
    public ResponseEntity<Map<String, Object>> getPrescription(
            @PathVariable Long appointmentId,
            @PathVariable String token) {

        var validation = sharedService.validateToken(token, "doctor");
        if (!validation.getStatusCode().is2xxSuccessful()
            || !Boolean.TRUE.equals(validation.getBody().get("valid"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(validation.getBody());
        }

        return prescriptionService.getPrescription(appointmentId);
    }
}

