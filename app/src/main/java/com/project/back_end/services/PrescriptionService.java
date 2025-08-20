package com.project.back_end.services;

import com.project.back_end.models.Prescription;
import com.project.back_end.repo.PrescriptionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    private final PrescriptionRepository prescriptionRepository;

    // 2) Constructor injection
    public PrescriptionService(PrescriptionRepository prescriptionRepository) {
        this.prescriptionRepository = prescriptionRepository;
    }

    // 3) Save new prescription; avoid duplicates per appointmentId
    public ResponseEntity<Map<String, Object>> savePrescription(Prescription prescription) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (prescription == null || prescription.getAppointmentId() == null) {
                body.put("message", "appointmentId is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            List<Prescription> existing = prescriptionRepository.findByAppointmentId(prescription.getAppointmentId());
            if (!existing.isEmpty()) {
                body.put("message", "Prescription already exists for this appointment");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            Prescription saved = prescriptionRepository.save(prescription);
            body.put("message", "Prescription created");
            body.put("id", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (Exception e) {
            log.error("Failed to save prescription", e);
            body.put("message", "Internal error while saving prescription");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 4) Get prescription(s) by appointmentId
    public ResponseEntity<Map<String, Object>> getPrescription(Long appointmentId) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (appointmentId == null) {
                body.put("message", "appointmentId is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            List<Prescription> list = prescriptionRepository.findByAppointmentId(appointmentId);
            if (list.isEmpty()) {
                body.put("message", "No prescription found for the given appointment");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }

            body.put("prescriptions", list); // return all in case multiple exist
            body.put("count", list.size());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Failed to fetch prescription(s) for appointmentId={}", appointmentId, e);
            body.put("message", "Internal error while fetching prescription");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
