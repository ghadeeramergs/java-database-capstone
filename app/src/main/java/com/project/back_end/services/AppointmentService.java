package com.project.back_end.services;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Appointment use-cases: book, update, cancel, list, change status.
 * Notes:
 * - Slot length assumed 1 hour (consistent with Appointment computed end-time).
 * - Conflicts are detected against appointments with status=0 (scheduled).
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final Service coreService; // your previously defined com.example.clinic.service.Service
    private final TokenService tokenService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    // 2) Constructor injection
    public AppointmentService(AppointmentRepository appointmentRepository,
                              Service coreService,
                              TokenService tokenService,
                              PatientRepository patientRepository,
                              DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.coreService = coreService;
        this.tokenService = tokenService;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
    }

    // 4) Book a new appointment (1 = success, 0 = failure)
    @Transactional
    public int bookAppointment(String bearerToken, Long doctorId, LocalDateTime start) {
        try {
            Optional<Patient> pOpt = resolvePatient(bearerToken);
            if (pOpt.isEmpty()) return 0;
            Optional<Doctor> dOpt = doctorRepository.findById(doctorId);
            if (dOpt.isEmpty() || start == null) return 0;

            // Validate slot availability (exclude none for new booking)
            if (!isSlotAvailable(doctorId, start, start.plusHours(1), null)) return 0;

            Appointment a = new Appointment();
            a.setDoctor(dOpt.get());
            a.setPatient(pOpt.get());
            a.setAppointmentTime(start);
            a.setStatus(0); // scheduled
            appointmentRepository.save(a);
            return 1;
        } catch (Exception e) {
            log.error("bookAppointment failed", e);
            return 0;
        }
    }

    // 5) Update appointment details (time/doctor/status). Only the owning patient can update.
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAppointment(String bearerToken,
                                                                 Long appointmentId,
                                                                 Long newDoctorId,
                                                                 LocalDateTime newStart,
                                                                 Integer newStatus) {
        Map<String, Object> body = new HashMap<>();
        try {
            Optional<Patient> pOpt = resolvePatient(bearerToken);
            if (pOpt.isEmpty()) {
                body.put("message", "Unauthorized");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            Optional<Appointment> aOpt = appointmentRepository.findById(appointmentId);
            if (aOpt.isEmpty()) {
                body.put("message", "Appointment not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }

            Appointment a = aOpt.get();
            if (a.getPatient() == null || !Objects.equals(a.getPatient().getId(), pOpt.get().getId())) {
                body.put("message", "Forbidden");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            // Determine target doctor/time
            Long targetDoctorId = (newDoctorId != null) ? newDoctorId :
                    (a.getDoctor() != null ? a.getDoctor().getId() : null);
            LocalDateTime targetStart = (newStart != null) ? newStart : a.getAppointmentTime();

            if (targetDoctorId == null || targetStart == null) {
                body.put("message", "Doctor and appointment time are required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            // Ensure doctor exists when changed
            if (newDoctorId != null && !doctorRepository.existsById(newDoctorId)) {
                body.put("message", "Target doctor not found");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            // Check availability excluding this same appointment (to allow unchanged times)
            if (!isSlotAvailable(targetDoctorId, targetStart, targetStart.plusHours(1), a.getId())) {
                body.put("message", "Requested slot is not available");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }

            // Apply updates
            if (newDoctorId != null) {
                a.setDoctor(doctorRepository.findById(newDoctorId).orElseThrow());
            }
            if (newStart != null) {
                a.setAppointmentTime(newStart);
            }
            if (newStatus != null) {
                a.setStatus(newStatus);
            }

            appointmentRepository.save(a);
            body.put("message", "Appointment updated");
            body.put("appointmentId", a.getId());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("updateAppointment failed", e);
            body.put("message", "Internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 6) Cancel appointment (only owner patient)
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelAppointment(String bearerToken, Long appointmentId) {
        Map<String, Object> body = new HashMap<>();
        try {
            Optional<Patient> pOpt = resolvePatient(bearerToken);
            if (pOpt.isEmpty()) {
                body.put("message", "Unauthorized");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            Optional<Appointment> aOpt = appointmentRepository.findById(appointmentId);
            if (aOpt.isEmpty()) {
                body.put("message", "Appointment not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Appointment a = aOpt.get();
            if (a.getPatient() == null || !Objects.equals(a.getPatient().getId(), pOpt.get().getId())) {
                body.put("message", "Forbidden");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            appointmentRepository.delete(a);
            body.put("message", "Appointment cancelled");
            body.put("appointmentId", appointmentId);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("cancelAppointment failed", e);
            body.put("message", "Internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 7) Get appointments for a doctor on a date; optionally filter by patient name (contains, case-insensitive)
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAppointments(Long doctorId, LocalDate date, String patientNameLike) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (doctorId == null || date == null) {
                body.put("message", "doctorId and date are required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);

            List<Appointment> appts;
            if (patientNameLike != null && !patientNameLike.isBlank()) {
                appts = appointmentRepository
                        .findByDoctorIdAndPatient_NameContainingIgnoreCaseAndAppointmentTimeBetween(
                                doctorId, patientNameLike, start, end);
            } else {
                appts = appointmentRepository
                        .findByDoctorIdAndAppointmentTimeBetween(doctorId, start, end);
            }

            body.put("count", appts.size());
            body.put("appointments", appts);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("getAppointments failed", e);
            body.put("message", "Internal error while fetching appointments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 8) Change appointment status (1 = success, 0 = failure)
    @Transactional
    public int changeStatus(Long appointmentId, int status) {
        try {
            appointmentRepository.updateStatus(status, appointmentId);
            return 1;
        } catch (Exception e) {
            log.error("changeStatus failed", e);
            return 0;
        }
    }

    // --- helpers ---

    private Optional<Patient> resolvePatient(String bearerToken) {
        try {
            String email = tokenService.extractEmail(bearerToken);
            if (email == null || email.isBlank()) return Optional.empty();
            return Optional.ofNullable(patientRepository.findByEmail(email));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Check if a slot is free for a doctor in [start, end), excluding one appointment id if provided. */
    private boolean isSlotAvailable(Long doctorId, LocalDateTime start, LocalDateTime end, Long excludeAppointmentId) {
        List<Appointment> collisions =
                appointmentRepository.findByDoctorIdAndAppointmentTimeBetween(doctorId, start, end);

        return collisions.stream()
                         .filter(a -> excludeAppointmentId == null || !Objects.equals(a.getId(), excludeAppointmentId))
                         .noneMatch(a -> a.getStatus() == 0); // treat scheduled as blocking
    }
}
