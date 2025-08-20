package com.project.back_end.services;


import com.project.back_end.models.Admin;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class Service {

    private final TokenService tokenService;
    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    // 2) Constructor injection
    public Service(TokenService tokenService,
                   AdminRepository adminRepository,
                   DoctorRepository doctorRepository,
                   PatientRepository patientRepository,
                   AppointmentRepository appointmentRepository) {
        this.tokenService = tokenService;
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // 3) Validate token for a role (admin/doctor/patient)
    public ResponseEntity<Map<String, Object>> validateToken(String token, String role) {
        Map<String, Object> body = new HashMap<>();
        try {
            // use TokenService to parse subject (email for doctor/patient; may be username for admin)
            String subject = tokenService.extractEmail(token);
            boolean valid;
            switch (role == null ? "" : role.toLowerCase()) {
                case "admin":
                    // Admin may not have email in your model; validate by username
                    Admin admin = adminRepository.findByUsername(subject);
                    valid = (admin != null);
                    break;
                case "doctor":
                    valid = doctorRepository.existsByEmail(subject);
                    break;
                case "patient":
                    valid = patientRepository.existsByEmail(subject);
                    break;
                default:
                    valid = false;
            }
            body.put("valid", valid);
            body.put("subject", subject);
            return ResponseEntity.status(valid ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).body(body);
        } catch (Exception e) {
            body.put("valid", false);
            body.put("error", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
    }

    // 4) Admin login: verify username/password and issue JWT (subject = username)
    public ResponseEntity<Map<String, Object>> validateAdmin(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        try {
            Admin admin = adminRepository.findByUsername(username);
            if (admin == null) {
                body.put("authenticated", false);
                body.put("message", "Admin not found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            // NOTE: replace with PasswordEncoder matches() in production
            if (!Objects.equals(admin.getPassword(), password)) {
                body.put("authenticated", false);
                body.put("message", "Invalid credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            String token = tokenService.generateToken(admin.getUsername()); // subject = username
            body.put("authenticated", true);
            body.put("token", token);
            body.put("username", admin.getUsername());
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            body.put("authenticated", false);
            body.put("message", "Internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 5) Filter doctors by name, specialty, and optional time slot string (e.g., "09:00-10:00")
    public List<Doctor> filterDoctor(String name, String specialty, String timeSlot) {
        List<Doctor> base;
        boolean hasName = name != null && !name.isBlank();
        boolean hasSpec = specialty != null && !specialty.isBlank();

        if (hasName && hasSpec) {
            base = doctorRepository.findByNameContainingIgnoreCaseAndSpecialtyIgnoreCase(name, specialty);
        } else if (hasName) {
            base = doctorRepository.findByNameLike("%" + name + "%");
        } else if (hasSpec) {
            base = doctorRepository.findBySpecialtyIgnoreCase(specialty);
        } else {
            base = doctorRepository.findAll();
        }

        if (timeSlot == null || timeSlot.isBlank()) return base;

        final String wanted = timeSlot.trim();
        return base.stream()
                   .filter(d -> d.getAvailableTimes() != null && d.getAvailableTimes().contains(wanted))
                   .collect(Collectors.toList());
    }

    // 6) Validate appointment time for a doctor:
    // returns 1 = valid, 0 = invalid slot or already booked, -1 = doctor not found
    public int validateAppointment(Long doctorId, LocalDate date, LocalTime requestedStart) {
        Optional<Doctor> docOpt = doctorRepository.findById(doctorId);
        if (docOpt.isEmpty()) return -1;

        Doctor doctor = docOpt.get();
        if (doctor.getAvailableTimes() == null || requestedStart == null || date == null) return 0;

        // Match by slot start time equality against "HH:mm-HH:mm" strings
        String startStr = requestedStart.toString(); // ISO "HH:mm"
        boolean slotExists = doctor.getAvailableTimes().stream()
                                   .filter(Objects::nonNull)
                                   .map(String::trim)
                                   .anyMatch(s -> s.startsWith(startStr + "-")); // e.g., "09:00-10:00"
        if (!slotExists) return 0;

        // Ensure not already booked (assume 1-hour slots)
        LocalDateTime start = LocalDateTime.of(date, requestedStart);
        LocalDateTime end = start.plusHours(1);
        List<Appointment> collisions =
                appointmentRepository.findByDoctorIdAndAppointmentTimeBetween(doctorId, start, end);

        // consider status 0 (scheduled) as conflicting; 1 (completed) won't exist in the future but we guard anyway
        boolean busy = collisions.stream().anyMatch(a -> a.getStatus() == 0);
        return busy ? 0 : 1;
    }

    // 7) Check patient uniqueness (true = OK to register, false = already exists)
    public boolean validatePatient(String email, String phone) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) return false;
        Patient existing = patientRepository.findByEmailOrPhone(
                email == null ? "" : email.trim(),
                phone == null ? "" : phone.trim()
                                                               );
        return existing == null;
    }

    // 8) Patient login: verify email/password and issue JWT (subject = email)
    public ResponseEntity<Map<String, Object>> validatePatientLogin(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        try {
            Patient p = patientRepository.findByEmail(email);
            if (p == null) {
                body.put("authenticated", false);
                body.put("message", "Patient not found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            if (!Objects.equals(p.getPassword(), password)) {
                body.put("authenticated", false);
                body.put("message", "Invalid credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            String token = tokenService.generateToken(p.getEmail()); // subject = email
            body.put("authenticated", true);
            body.put("token", token);
            body.put("email", p.getEmail());
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            body.put("authenticated", false);
            body.put("message", "Internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 9) Filter a patient's appointments by status and/or doctor name (identified by JWT token)
    public List<Appointment> filterPatient(String jwtToken, Integer status, String doctorName) {
        String email = tokenService.extractEmail(jwtToken);
        Patient p = patientRepository.findByEmail(email);
        if (p == null) return Collections.emptyList();

        Long patientId = p.getId();
        boolean hasStatus = status != null;
        boolean hasDoctor = doctorName != null && !doctorName.isBlank();

        if (hasStatus && hasDoctor) {
            return appointmentRepository.filterByDoctorNameAndPatientIdAndStatus(doctorName, patientId, status);
        } else if (hasDoctor) {
            return appointmentRepository.filterByDoctorNameAndPatientId(doctorName, patientId);
        } else if (hasStatus) {
            return appointmentRepository.findByPatient_IdAndStatusOrderByAppointmentTimeAsc(patientId, status);
        } else {
            return appointmentRepository.findByPatient_Id(patientId);
        }
    }
}
