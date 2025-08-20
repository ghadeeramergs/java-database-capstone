package com.project.back_end.DTO;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AppointmentDTO {

    // 1-10) Core fields
    private Long id;
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;
    private String patientEmail;
    private String patientPhone;
    private String patientAddress;
    private LocalDateTime appointmentTime;
    private int status; // 0=Scheduled, 1=Completed, etc.

    // 11-13) Derived fields
    private LocalDate appointmentDate;
    private LocalTime appointmentTimeOnly;
    private LocalDateTime endTime;

    public AppointmentDTO() {}

    public AppointmentDTO(Long id,
                          Long doctorId, String doctorName,
                          Long patientId, String patientName, String patientEmail, String patientPhone, String patientAddress,
                          LocalDateTime appointmentTime, int status) {
        this.id = id;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.patientId = patientId;
        this.patientName = patientName;
        this.patientEmail = patientEmail;
        this.patientPhone = patientPhone;
        this.patientAddress = patientAddress;
        this.appointmentTime = appointmentTime;
        this.status = status;
        recalcDerived();
    }

    private void recalcDerived() {
        if (this.appointmentTime != null) {
            this.appointmentDate = this.appointmentTime.toLocalDate();
            this.appointmentTimeOnly = this.appointmentTime.toLocalTime();
            this.endTime = this.appointmentTime.plusHours(1);
        } else {
            this.appointmentDate = null;
            this.appointmentTimeOnly = null;
            this.endTime = null;
        }
    }

    // Getters (including derived fields)
    public Long getId() { return id; }
    public Long getDoctorId() { return doctorId; }
    public String getDoctorName() { return doctorName; }
    public Long getPatientId() { return patientId; }
    public String getPatientName() { return patientName; }
    public String getPatientEmail() { return patientEmail; }
    public String getPatientPhone() { return patientPhone; }
    public String getPatientAddress() { return patientAddress; }
    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public int getStatus() { return status; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getAppointmentTimeOnly() { return appointmentTimeOnly; }
    public LocalDateTime getEndTime() { return endTime; }

    // Optional setters (keep derived fields in sync)
    public void setId(Long id) { this.id = id; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public void setPatientAddress(String patientAddress) { this.patientAddress = patientAddress; }
    public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; recalcDerived(); }
    public void setStatus(int status) { this.status = status; }
}
