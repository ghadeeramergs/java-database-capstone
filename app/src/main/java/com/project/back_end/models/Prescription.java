package com.project.back_end.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.*;

@Document(collection = "prescriptions")
public class Prescription {

    // 1) Primary Key (MongoDB ObjectId stored as String)
    @Id
    private String id;

    // 2) Patient Name
    @NotNull
    @Size(min = 3, max = 100)
    private String patientName;

    // 3) Appointment ID (link to relational appointment table if needed)
    @NotNull
    private Long appointmentId;

    // 4) Medication
    @NotNull
    @Size(min = 3, max = 100)
    private String medication;

    // 5) Dosage
    @NotNull
    private String dosage;

    // 6) Doctor Notes
    @Size(max = 200)
    private String doctorNotes;

    // --- Constructors ---
    public Prescription() {}

    public Prescription(String patientName, Long appointmentId, String medication, String dosage, String doctorNotes) {
        this.patientName = patientName;
        this.appointmentId = appointmentId;
        this.medication = medication;
        this.dosage = dosage;
        this.doctorNotes = doctorNotes;
    }

    // --- Getters & Setters ---
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getPatientName() {
        return patientName;
    }
    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }
    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getMedication() {
        return medication;
    }
    public void setMedication(String medication) {
        this.medication = medication;
    }

    public String getDosage() {
        return dosage;
    }
    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getDoctorNotes() {
        return doctorNotes;
    }
    public void setDoctorNotes(String doctorNotes) {
        this.doctorNotes = doctorNotes;
    }
}

