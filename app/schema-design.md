# Smart Clinic System — Database Schema Design

> Scope: core operational data for a small/medium outpatient clinic (patient registration, scheduling, encounters, and prescriptions). The design balances *relational integrity* for transactional data (MySQL) with *document flexibility* for clinician-authored artifacts (MongoDB).

---

## What data must be managed (critical view)
- **People:** patients, doctors, clinic staff/admins.
- **Access & identity:** unique logins, roles, audit timestamps.
- **Scheduling:** appointments (time windows, status transitions, creator).
- **Care delivery:** encounter notes, vitals, diagnosis codes, attachments.
- **Medication orders:** prescriptions (structured meds, dosing), follow-ups.
- **Operational analytics:** indexes and timestamps to support efficient queries.
- **Privacy & integrity:** avoid orphan records, prevent double booking, capture creation/update actors.

---

## MySQL (OLTP, strongly consistent)

> Engine: MySQL 8.0 (InnoDB, `utf8mb4`). Chosen for transactional integrity (FKs, ACID), predictable joins, and easy reporting.  
> **Note:** Use application logic or triggers to check overlapping appointment time windows; unique constraints alone can’t express interval overlap.

```sql
-- MySQL 8.0, InnoDB, utf8mb4
CREATE DATABASE IF NOT EXISTS smart_clinic
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE smart_clinic;

-- 1) PATIENTS: master data for demographics and contact info
CREATE TABLE patients (
  patient_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  national_id VARCHAR(32) UNIQUE,                         -- e.g., Emirates ID; optional/nullable
  first_name VARCHAR(100) NOT NULL,
  last_name  VARCHAR(100) NOT NULL,
  gender ENUM('FEMALE','MALE','OTHER') NOT NULL,
  date_of_birth DATE NOT NULL,
  email VARCHAR(255) UNIQUE,                              -- unique login/contact when used
  phone VARCHAR(32) NOT NULL,
  address JSON,                                           -- flexible address structure (city, street, etc.)
  blood_type ENUM('A+','A-','B+','B-','AB+','AB-','O+','O-'),
  insurance_provider VARCHAR(100),
  insurance_number   VARCHAR(100),
  emergency_contact_name  VARCHAR(100),
  emergency_contact_phone VARCHAR(32),
  status ENUM('ACTIVE','INACTIVE','DECEASED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_patients_phone (phone)
) ENGINE=InnoDB;

-- 2) DOCTORS: clinical staff
CREATE TABLE doctors (
  doctor_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  employee_code  VARCHAR(32)  NOT NULL UNIQUE,            -- internal HR code
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  email          VARCHAR(255) NOT NULL UNIQUE,
  phone          VARCHAR(32)  NOT NULL,
  specialization VARCHAR(100) NOT NULL,                   -- normalized table possible if taxonomy required
  license_number VARCHAR(64)  NOT NULL UNIQUE,            -- regulator license
  room_no        VARCHAR(16),
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_doctors_specialization (specialization),
  INDEX idx_doctors_phone (phone)
) ENGINE=InnoDB;

-- 3) ADMIN_USERS: system users (reception, nurses, managers, super-admin)
CREATE TABLE admin_users (
  admin_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,                    -- store bcrypt/argon2 hash; NEVER plaintext
  role ENUM('SUPER_ADMIN','RECEPTION','NURSE','DOCTOR','MANAGER') NOT NULL DEFAULT 'RECEPTION',
  last_login_at DATETIME(6),
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 4) APPOINTMENTS: scheduling between patient and doctor
CREATE TABLE appointments (
  appointment_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT UNSIGNED NOT NULL,
  doctor_id  BIGINT UNSIGNED NOT NULL,
  scheduled_start DATETIME(6) NOT NULL,                   -- clinic local timezone; store in UTC in app if preferred
  scheduled_end   DATETIME(6) NOT NULL,
  status ENUM('BOOKED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW') NOT NULL DEFAULT 'BOOKED',
  visit_reason VARCHAR(255),
  notes TEXT,
  created_by_admin_id BIGINT UNSIGNED,                    -- who booked it
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,                 -- protect clinical history
  CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_appt_admin   FOREIGN KEY (created_by_admin_id) REFERENCES admin_users(admin_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_appt_doctor_time  (doctor_id,  scheduled_start),
  INDEX idx_appt_patient_time (patient_id, scheduled_start),
  INDEX idx_appt_status       (status),
  CONSTRAINT chk_appt_time CHECK (scheduled_end > scheduled_start) -- enforced on MySQL 8.0.16+; otherwise validate in app
);

-- 5) MEDICAL_RECORDS: encounter summary linked to an appointment
CREATE TABLE medical_records (
  record_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT UNSIGNED NOT NULL,
  patient_id     BIGINT UNSIGNED NOT NULL,
  doctor_id      BIGINT UNSIGNED NOT NULL,
  diagnosis_code VARCHAR(32),                             -- e.g., ICD-10
  symptoms TEXT,
  vitals JSON,                                            -- { "bp":"120/80","hr":72,"temp":36.8 }
  attachments_count INT UNSIGNED NOT NULL DEFAULT 0,      -- files stored externally (object storage)
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_mr_appt   FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
    ON DELETE CASCADE ON UPDATE CASCADE,                  -- deleting appointment removes linked record
  CONSTRAINT fk_mr_patient FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_mr_doctor  FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_mr_patient (patient_id),
  INDEX idx_mr_doctor  (doctor_id)
) ENGINE=InnoDB;
```

### Why this split?
- **Relational** for *master data + scheduling* → avoids duplicates, ensures referential integrity, and supports canonical reporting.
- **JSON columns** where structure varies (addresses, vitals) → keeps relational core stable while allowing controlled flexibility.
- **FKs and timestamps** enable reliable auditing and safe deletes (RESTRICT/SET NULL/CASCADE chosen to protect history).

---

## MongoDB (documents for clinician-authored artifacts)

> Collection chosen: **`prescriptions`**.  
> Rationale: prescriptions are authored artifacts with variable-length medication arrays, frequent schema evolution (instructions, PRN flags, refills), and need for embedded patient/doctor snapshots at time of issue.

### Example document (valid JSON)
```json
{
  "_id": "673f2b0c7a1e4f1d9cc01234", 
  "appointmentId": 102345, 
  "patient": {
    "id": 1001,
    "name": { "first": "Aisha", "last": "Khan" },
    "dob": "1992-05-10",
    "allergies": ["penicillin"],
    "chronicConditions": ["hypertension"]
  },
  "doctor": {
    "id": 501,
    "name": { "first": "Omar", "last": "Haddad" },
    "specialization": "Family Medicine",
    "licenseNumber": "DHA-12345"
  },
  "issuedAt": "2025-08-20T07:35:00Z",
  "medications": [
    {
      "drugCode": "N02BE01",
      "name": "Paracetamol",
      "dose": { "amount": 500, "unit": "mg" },
      "route": "oral",
      "frequency": "every 8h",
      "durationDays": 3,
      "prn": false,
      "instructions": ["take after food", "max 3g/day"],
      "refills": 0
    },
    {
      "drugCode": "R03AC02",
      "name": "Salbutamol Inhaler",
      "dose": { "amount": 2, "unit": "puffs" },
      "route": "inhalation",
      "frequency": "as needed",
      "durationDays": 14,
      "prn": true,
      "instructions": ["use spacer if available"]
    }
  ],
  "pharmacy": {
    "preferred": true,
    "name": "CityCare Pharmacy",
    "phone": "+971-4-123-4567"
  },
  "followUp": {
    "recommendedDate": "2025-08-27T07:35:00Z",
    "reason": "Assess pain control and BP"
  },
  "audit": {
    "createdBy": "reception-01",
    "createdAt": "2025-08-20T07:35:05Z",
    "lastUpdatedAt": "2025-08-20T07:35:05Z",
    "source": "smart-clinic-web/v1.4.2"
  },
  "version": 1
}
```

**Design notes**
- Embed **patient/doctor snapshots** to preserve “as-issued” context even if relational master data later changes.
- Cross-store **link** via `appointmentId` to join with MySQL for analytics (ETL/warehouse or at API layer).
- **Arrays** (`medications`) capture variable-length orders; subdocuments hold dose/route/frequency to avoid sparse columns.
- **Indexes** to create:
    - `{ appointmentId: 1 }`
    - `{ "patient.id": 1, issuedAt: -1 }`
    - `{ "doctor.id": 1, issuedAt: -1 }`
    - `{ "medications.drugCode": 1 }` (supports pharmacovigilance queries)

---

## Operational safeguards (concise)
- Enforce **no-overlap** for appointments with an application-level check (or BEFORE trigger) on `(doctor_id, [start,end])` intervals.
- Store only **password hashes** (argon2id/bcrypt) and rotate **admin roles** by least privilege.
- Use **UTC** for backend timestamps; convert at UI for Dubai (Asia/Dubai, UTC+4) display.
- Add **soft-delete** flags if legal retention requires keeping rows while hiding from UI.

```sql
-- Example trigger sketch (pseudo) to reject overlaps; implement with precise SQL per MySQL version
-- BEFORE INSERT/UPDATE ON appointments: SELECT 1 FROM appointments
-- WHERE doctor_id = NEW.doctor_id AND status IN ('BOOKED','CHECKED_IN')
--   AND NEW.scheduled_start < scheduled_end AND NEW.scheduled_end > scheduled_start
-- LIMIT 1; IF found THEN SIGNAL SQLSTATE '45000';
```
