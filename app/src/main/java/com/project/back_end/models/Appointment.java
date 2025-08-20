package com.project.back_end.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments",
       indexes = {
               @Index(name = "idx_appt_doctor_time", columnList = "doctor_id, appointment_time"),
               @Index(name = "idx_appt_patient_time", columnList = "patient_id, appointment_time")
       })
public class Appointment {

    // 1) Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 2) Doctor (many appointments → one doctor)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @NotNull
    private Doctor doctor;

    // 3) Patient (many appointments → one patient)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @NotNull
    private Patient patient;

    // 4) Start time
    @Column(name = "appointment_time", nullable = false)
    @NotNull
    @Future(message = "Appointment must be scheduled in the future")
    private LocalDateTime appointmentTime;

    // 5) Status: 0 = Scheduled, 1 = Completed (primitive int; use enum in real systems)
    @Column(nullable = false)
    private int status = 0;

    // --- Constructors ---
    public Appointment() {}

    public Appointment(Doctor doctor, Patient patient, LocalDateTime appointmentTime, int status) {
        this.doctor = doctor;
        this.patient = patient;
        this.appointmentTime = appointmentTime;
        this.status = status;
    }

    // --- Derived helpers (not persisted) ---

    // 6) End time = start + 1 hour (display/UX helper)
    @Transient
    private LocalDateTime getEndTime() {
        return appointmentTime != null ? appointmentTime.plusHours(1) : null;
    }

    // 7) Only the date part
    @Transient
    private LocalDate getAppointmentDate() {
        return appointmentTime != null ? appointmentTime.toLocalDate() : null;
    }

    // 8) Only the time part
    @Transient
    private LocalTime getAppointmentTimeOnly() {
        return appointmentTime != null ? appointmentTime.toLocalTime() : null;
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Doctor getDoctor() {
        return doctor;
    }
    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public Patient getPatient() {
        return patient;
    }
    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public LocalDateTime getAppointmentTime() {
        return appointmentTime;
    }
    public void setAppointmentTime(LocalDateTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    // Expose derived getters as public if you want them usable outside
    public LocalDateTime getComputedEndTime() { return getEndTime(); }
    public LocalDate getComputedAppointmentDate() { return getAppointmentDate(); }
    public LocalTime getComputedAppointmentTimeOnly() { return getAppointmentTimeOnly(); }
}