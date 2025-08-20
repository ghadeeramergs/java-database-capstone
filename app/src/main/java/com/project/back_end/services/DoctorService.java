package com.project.back_end.services;


import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final TokenService tokenService;

    public DoctorService(DoctorRepository doctorRepository,
                         AppointmentRepository appointmentRepository,
                         TokenService tokenService) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.tokenService = tokenService;
    }

    // 4) Availability by doctor/date (filters out already scheduled slots)
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDoctorAvailability(Long doctorId, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        try {
            Optional<Doctor> docOpt = doctorRepository.findById(doctorId);
            if (docOpt.isEmpty()) {
                body.put("message", "Doctor not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            Doctor doctor = docOpt.get();
            initAvailableTimes(doctor);

            List<String> slots = Optional.ofNullable(doctor.getAvailableTimes())
                                         .map(lst -> lst.stream().filter(Objects::nonNull).map(String::trim).distinct().sorted().collect(Collectors.toList()))
                                         .orElseGet(List::of);

            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            List<Appointment> dayAppts =
                    appointmentRepository.findByDoctorIdAndAppointmentTimeBetween(doctorId, start, end);

            // consider status=0 (Scheduled) as "booked"
            Set<String> bookedStarts = dayAppts.stream()
                                               .filter(a -> a.getStatus() == 0 && a.getAppointmentTime() != null)
                                               .map(a -> a.getAppointmentTime().toLocalTime().toString())
                                               .collect(Collectors.toSet());

            List<String> available = slots.stream()
                                          .filter(s -> {
                                              Optional<LocalTime> st = parseSlotStart(s);
                                              return st.map(t -> !bookedStarts.contains(t.toString())).orElse(false);
                                          })
                                          .collect(Collectors.toList());

            body.put("doctorId", doctorId);
            body.put("date", date.toString());
            body.put("availableSlots", available);
            body.put("bookedStarts", bookedStarts);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("getDoctorAvailability failed", e);
            body.put("message", "Internal error while computing availability");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 5) Save doctor: 1=success, -1=conflict(email exists), 0=error
    @Transactional
    public int saveDoctor(Doctor doctor) {
        try {
            if (doctor == null || doctor.getEmail() == null) return 0;
            if (doctorRepository.existsByEmail(doctor.getEmail())) return -1;
            doctorRepository.save(doctor);
            return 1;
        } catch (Exception e) {
            log.error("saveDoctor failed", e);
            return 0;
        }
    }

    // 6) Update doctor: 1=success, -1=not found, 0=error
    @Transactional
    public int updateDoctor(Doctor doctor) {
        try {
            if (doctor == null || doctor.getId() == null) return 0;
            if (!doctorRepository.existsById(doctor.getId())) return -1;
            doctorRepository.save(doctor);
            return 1;
        } catch (Exception e) {
            log.error("updateDoctor failed", e);
            return 0;
        }
    }

    // 7) Get all doctors (ensure availableTimes initialized)
    @Transactional(readOnly = true)
    public List<Doctor> getDoctors() {
        List<Doctor> list = doctorRepository.findAll();
        list.forEach(this::initAvailableTimes);
        return list;
    }

    // 8) Delete doctor + their appointments: 1=success, -1=not found, 0=error
    @Transactional
    public int deleteDoctor(Long doctorId) {
        try {
            if (!doctorRepository.existsById(doctorId)) return -1;
            appointmentRepository.deleteAllByDoctorId(doctorId);
            doctorRepository.deleteById(doctorId);
            return 1;
        } catch (Exception e) {
            log.error("deleteDoctor failed", e);
            return 0;
        }
    }

    // 9) Doctor login → token on success
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> validateDoctor(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        try {
            Doctor d = doctorRepository.findByEmail(email);
            if (d == null) {
                body.put("authenticated", false);
                body.put("message", "Doctor not found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            // NOTE: replace with PasswordEncoder in production
            if (!Objects.equals(d.getPassword(), password)) {
                body.put("authenticated", false);
                body.put("message", "Invalid credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            String token = tokenService.generateToken(email);
            body.put("authenticated", true);
            body.put("token", token);
            body.put("email", email);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("validateDoctor failed", e);
            body.put("authenticated", false);
            body.put("message", "Internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // 10) Find doctors by partial name (ensure availableTimes loaded)
    @Transactional(readOnly = true)
    public List<Doctor> findDoctorByName(String name) {
        List<Doctor> list = (name == null || name.isBlank())
                ? doctorRepository.findAll()
                : doctorRepository.findByNameLike("%" + name + "%");
        list.forEach(this::initAvailableTimes);
        return list;
    }

    // 11) Filter by name + specialty + AM/PM
    @Transactional(readOnly = true)
    public List<Doctor> filterDoctorsByNameSpecilityandTime(String name, String specialty, String period) {
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
        base.forEach(this::initAvailableTimes);
        return filterDoctorByTime(base, period);
    }

    // 12) Filter a given list by AM/PM
    public List<Doctor> filterDoctorByTime(List<Doctor> doctors, String period) {
        if (period == null || period.isBlank()) return doctors;
        String p = period.trim().toUpperCase(Locale.ROOT);
        if (!p.equals("AM") && !p.equals("PM")) return doctors;

        return doctors.stream()
                      .filter(d -> {
                          List<String> slots = d.getAvailableTimes();
                          if (slots == null || slots.isEmpty()) return false;
                          return slots.stream()
                                      .map(this::parseSlotStart)
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .anyMatch(t -> p.equals("AM") ? t.getHour() < 12 : t.getHour() >= 12);
                      })
                      .collect(Collectors.toList());
    }

    // 13) Filter by name + AM/PM
    @Transactional(readOnly = true)
    public List<Doctor> filterDoctorByNameAndTime(String name, String period) {
        List<Doctor> list = doctorRepository.findByNameLike("%" + name + "%");
        list.forEach(this::initAvailableTimes);
        return filterDoctorByTime(list, period);
    }

    // 14) Filter by name + specialty
    @Transactional(readOnly = true)
    public List<Doctor> filterDoctorByNameAndSpecility(String name, String specialty) {
        List<Doctor> list = doctorRepository.findByNameContainingIgnoreCaseAndSpecialtyIgnoreCase(name, specialty);
        list.forEach(this::initAvailableTimes);
        return list;
    }

    // 15) Filter by time + specialty
    @Transactional(readOnly = true)
    public List<Doctor> filterDoctorByTimeAndSpecility(String specialty, String period) {
        List<Doctor> list = doctorRepository.findBySpecialtyIgnoreCase(specialty);
        list.forEach(this::initAvailableTimes);
        return filterDoctorByTime(list, period);
    }

    // 17) Filter all by time (AM/PM)
    @Transactional(readOnly = true)
    public List<Doctor> filterDoctorsByTime(String period) {
        List<Doctor> list = doctorRepository.findAll();
        list.forEach(this::initAvailableTimes);
        return filterDoctorByTime(list, period);
    }

    // --- helpers ---

    private void initAvailableTimes(Doctor d) {
        if (d != null && d.getAvailableTimes() != null) {
            // touch to ensure initialization inside @Transactional
            d.getAvailableTimes().size();
        }
    }

    private Optional<LocalTime> parseSlotStart(String slot) {
        if (slot == null) return Optional.empty();
        String s = slot.trim();
        int dash = s.indexOf('-');
        if (dash <= 0) return Optional.empty();
        try {
            return Optional.of(LocalTime.parse(s.substring(0, dash)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
