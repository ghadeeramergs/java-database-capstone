package com.project.back_end.services;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final TokenService tokenService;

    // 2) Constructor injection
    public PatientService(PatientRepository patientRepository,
                          AppointmentRepository appointmentRepository,
                          TokenService tokenService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.tokenService = tokenService;
    }

    // 3) Create patient: return 1 on success, 0 on failure
    public int createPatient(Patient patient) {
        try {
            patientRepository.save(patient);
            return 1;
        } catch (Exception e) {
            log.error("createPatient failed", e);
            return 0;
        }
    }

    // 4) Get all appointments for a patient -> DTOs
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPatientAppointment(Long patientId) {
        Map<String, Object> body = new HashMap<>();
        try {
            List<Appointment> appts = appointmentRepository.findByPatient_Id(patientId);
            List<AppointmentDTO> dtos = appts.stream().map(this::toDto).collect(Collectors.toList());
            body.put("count", dtos.size());
            body.put("appointments", dtos);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("getPatientAppointment failed for patientId={}", patientId, e);
            body.put("message", "Internal error while fetching appointments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 5) Filter by condition: "past" -> status=1, "future" -> status=0
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> filterByCondition(Long patientId, String condition) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (condition == null) {
                body.put("message", "condition is required (past|future)");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
            int status;
            switch (condition.toLowerCase()) {
                case "past":   status = 1; break;
                case "future": status = 0; break;
                default:
                    body.put("message", "Invalid condition. Use 'past' or 'future'.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
            List<Appointment> appts =
                    appointmentRepository.findByPatient_IdAndStatusOrderByAppointmentTimeAsc(patientId, status);
            List<AppointmentDTO> dtos = appts.stream().map(this::toDto).collect(Collectors.toList());
            body.put("count", dtos.size());
            body.put("appointments", dtos);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("filterByCondition failed for patientId={}, condition={}", patientId, condition, e);
            body.put("message", "Internal error while filtering appointments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 6) Filter by doctor name for a patient
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> filterByDoctor(Long patientId, String doctorName) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (doctorName == null || doctorName.isBlank()) {
                body.put("message", "doctorName is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
            List<Appointment> appts =
                    appointmentRepository.filterByDoctorNameAndPatientId(doctorName, patientId);
            List<AppointmentDTO> dtos = appts.stream().map(this::toDto).collect(Collectors.toList());
            body.put("count", dtos.size());
            body.put("appointments", dtos);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("filterByDoctor failed for patientId={}, doctorName={}", patientId, doctorName, e);
            body.put("message", "Internal error while filtering by doctor");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 7) Filter by doctor AND condition for a patient
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> filterByDoctorAndCondition(Long patientId, String doctorName, String condition) {
        Map<String, Object> body = new HashMap<>();
        try {
            if (doctorName == null || doctorName.isBlank() || condition == null) {
                body.put("message", "doctorName and condition (past|future) are required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }
            int status;
            switch (condition.toLowerCase()) {
                case "past":   status = 1; break;
                case "future": status = 0; break;
                default:
                    body.put("message", "Invalid condition. Use 'past' or 'future'.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
            }

            List<Appointment> appts =
                    appointmentRepository.filterByDoctorNameAndPatientIdAndStatus(doctorName, patientId, status);
            List<AppointmentDTO> dtos = appts.stream().map(this::toDto).collect(Collectors.toList());
            body.put("count", dtos.size());
            body.put("appointments", dtos);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("filterByDoctorAndCondition failed for patientId={}, doctorName={}, condition={}",
                      patientId, doctorName, condition, e);
            body.put("message", "Internal error while filtering appointments");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 8) Get patient details from JWT token
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPatientDetails(String bearerToken) {
        Map<String, Object> body = new HashMap<>();
        try {
            String email = tokenService.extractEmail(bearerToken);
            if (email == null || email.isBlank()) {
                body.put("message", "Invalid token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            Patient p = patientRepository.findByEmail(email);
            if (p == null) {
                body.put("message", "Patient not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            // Do not expose sensitive fields (e.g., password)
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", p.getId());
            dto.put("name", p.getName());
            dto.put("email", p.getEmail());
            dto.put("phone", p.getPhone());
            dto.put("address", p.getAddress());
            body.put("patient", dto);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("getPatientDetails failed", e);
            body.put("message", "Internal error while fetching patient details");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // --- DTO mapping ---
    private AppointmentDTO toDto(Appointment a) {
        if (a == null) return null;
        String doctorName = (a.getDoctor() != null) ? a.getDoctor().getName() : null;
        String patientName = (a.getPatient() != null) ? a.getPatient().getName() : null;
        return new AppointmentDTO(
                a.getId(),
                doctorName,
                patientName,
                a.getAppointmentTime(),
                a.getStatus(),
                (a.getAppointmentTime() != null) ? a.getAppointmentTime().plusHours(1) : null
        );
    }

    // 10) DTO for safe transfer
    public static class AppointmentDTO {
        private Long id;
        private String doctorName;
        private String patientName;
        private LocalDateTime appointmentTime;
        private Integer status;         // 0=Future(Scheduled), 1=Past(Completed)
        private LocalDateTime endTime;  // derived

        public AppointmentDTO() {}
        public AppointmentDTO(Long id, String doctorName, String patientName,
                              LocalDateTime appointmentTime, Integer status, LocalDateTime endTime) {
            this.id = id;
            this.doctorName = doctorName;
            this.patientName = patientName;
            this.appointmentTime = appointmentTime;
            this.status = status;
            this.endTime = endTime;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
        public String getPatientName() { return patientName; }
        public void setPatientName(String patientName) { this.patientName = patientName; }
        public LocalDateTime getAppointmentTime() { return appointmentTime; }
        public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}
