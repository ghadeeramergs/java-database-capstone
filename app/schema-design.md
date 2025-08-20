# Smart Clinic System — Schema Design

> **Start with Real-World Thinking**  
> A smart clinic manages: identity & access, patient registration, clinician rosters, **doctor availability**, appointment scheduling/tracking, **encounters/notes**, **prescriptions**, **payments**, and optional **feedback/messages/uploads**. We keep **transactional, validated** data in **MySQL** and store flexible, clinician-authored artifacts in **MongoDB**.

---

## MySQL Database Design

**Why MySQL here?** Strong relational integrity (FKs), transactions, and well-known reporting patterns. We normalize core entities and keep evolving/optional structures in JSON columns where helpful (e.g., addresses, vitals).

### Tables overview (entities & relationships)
- **patients (1) ↔ appointments (∞)**
- **doctors (1) ↔ appointments (∞)**
- **doctors (1) ↔ doctor_availability (∞)** (time windows)
- **appointments (1) ↔ medical_records (0..1)**
- **admin_users (1) ↔ appointments (∞)** (created_by)
- **clinic_locations (1) ↔ doctors (∞)** (optional assignment)
- **appointments (1) ↔ payments (0..1)**

> **Deletion policy**: clinical history is legally sensitive; we RESTRICT delete for `patients/doctors` while allowing `appointments` to cascade associated `medical_records`. Use *soft deletes* at app layer if retention rules require.

```sql
-- MySQL 8.0, InnoDB, utf8mb4
CREATE DATABASE IF NOT EXISTS smart_clinic
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE smart_clinic;

-- 1) PATIENTS
CREATE TABLE patients (
  patient_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  national_id VARCHAR(32) UNIQUE,                         -- e.g., Emirates ID (nullable by policy)
  first_name VARCHAR(100) NOT NULL,
  last_name  VARCHAR(100) NOT NULL,
  gender ENUM('FEMALE','MALE','OTHER') NOT NULL,
  date_of_birth DATE NOT NULL,
  email VARCHAR(255) UNIQUE,
  phone VARCHAR(32) NOT NULL,
  address JSON,                                           -- flexible structure
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

-- 2) CLINIC LOCATIONS (optional, for multi-branch clinics)
CREATE TABLE clinic_locations (
  location_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  phone VARCHAR(32),
  address JSON NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_location_name (name)
) ENGINE=InnoDB;

-- 3) DOCTORS
CREATE TABLE doctors (
  doctor_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  location_id BIGINT UNSIGNED,
  employee_code  VARCHAR(32)  NOT NULL UNIQUE,
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  email          VARCHAR(255) NOT NULL UNIQUE,
  phone          VARCHAR(32)  NOT NULL,
  specialization VARCHAR(100) NOT NULL,
  license_number VARCHAR(64)  NOT NULL UNIQUE,
  room_no        VARCHAR(16),
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_doc_location FOREIGN KEY (location_id) REFERENCES clinic_locations(location_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_doctors_specialization (specialization),
  INDEX idx_doctors_phone (phone)
) ENGINE=InnoDB;

-- 4) DOCTOR AVAILABILITY (time windows; used to validate booking)
CREATE TABLE doctor_availability (
  availability_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  doctor_id BIGINT UNSIGNED NOT NULL,
  weekday TINYINT UNSIGNED,                               -- 0..6 (Sun..Sat), nullable if date-range below is used
  start_time TIME,                                        -- daily slot start (local)
  end_time   TIME,                                        -- daily slot end (local)
  valid_from DATE,                                        -- optional seasonality
  valid_to   DATE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_av_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT chk_time_win CHECK (end_time IS NULL OR start_time IS NULL OR end_time > start_time),
  INDEX idx_av_doctor_day (doctor_id, weekday)
) ENGINE=InnoDB;

-- 5) ADMIN USERS (system operators)
CREATE TABLE admin_users (
  admin_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,                    -- store bcrypt/argon2 hash
  role ENUM('SUPER_ADMIN','RECEPTION','NURSE','DOCTOR','MANAGER') NOT NULL DEFAULT 'RECEPTION',
  last_login_at DATETIME(6),
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 6) APPOINTMENTS
CREATE TABLE appointments (
  appointment_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  patient_id BIGINT UNSIGNED NOT NULL,
  doctor_id  BIGINT UNSIGNED NOT NULL,
  location_id BIGINT UNSIGNED,
  scheduled_start DATETIME(6) NOT NULL,
  scheduled_end   DATETIME(6) NOT NULL,
  status ENUM('BOOKED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW') NOT NULL DEFAULT 'BOOKED',
  visit_reason VARCHAR(255),
  notes TEXT,
  created_by_admin_id BIGINT UNSIGNED,                    -- who booked
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_appt_location FOREIGN KEY (location_id) REFERENCES clinic_locations(location_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_appt_admin   FOREIGN KEY (created_by_admin_id) REFERENCES admin_users(admin_id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  INDEX idx_appt_doctor_time  (doctor_id,  scheduled_start),
  INDEX idx_appt_patient_time (patient_id, scheduled_start),
  INDEX idx_appt_status       (status),
  CONSTRAINT chk_appt_time CHECK (scheduled_end > scheduled_start)
);

-- NOTE: Prevent overlapping bookings per doctor via application logic or triggers
-- condition: NEW.start < existing.end AND NEW.end > existing.start for same doctor & BOOKED/CHECKED_IN.

-- 7) MEDICAL RECORDS (encounter summary; optional per appointment)
CREATE TABLE medical_records (
  record_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT UNSIGNED NOT NULL,
  patient_id     BIGINT UNSIGNED NOT NULL,
  doctor_id      BIGINT UNSIGNED NOT NULL,
  diagnosis_code VARCHAR(32),
  symptoms TEXT,
  vitals JSON,                                            -- { "bp":"120/80","hr":72,"temp":36.8 }
  attachments_count INT UNSIGNED NOT NULL DEFAULT 0,      -- files stored externally (object storage)
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_mr_appt   FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_mr_patient FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_mr_doctor  FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  INDEX idx_mr_patient (patient_id),
  INDEX idx_mr_doctor  (doctor_id)
) ENGINE=InnoDB;

-- 8) PAYMENTS (optional; one payment per appointment in basic flow)
CREATE TABLE payments (
  payment_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT UNSIGNED NOT NULL UNIQUE,         -- simple 1:1; change to remove UNIQUE for split payments
  amount DECIMAL(10,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'AED',
  method ENUM('CASH','CARD','ONLINE','INSURANCE') NOT NULL,
  status ENUM('PENDING','PAID','REFUNDED','FAILED') NOT NULL DEFAULT 'PENDING',
  transaction_ref VARCHAR(128),
  metadata JSON,                                          -- gateway payload (flexible)
  paid_at DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_pay_appt FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  INDEX idx_pay_status (status),
  INDEX idx_pay_method (method)
) ENGINE=InnoDB;
```

**Design justifications (concise)**
- **RESTRICT** deletes for `patients/doctors` to protect history; **CASCADE** from `appointments → medical_records` and `appointments → payments`.
- **doctor_availability** enables pre-validation and capacity planning; overlap prevention is enforced in app/trigger logic.
- **JSON** columns (`address`, `vitals`, `metadata`) handle heterogeneous fields without over-normalizing.
- **Indexes** on `(doctor_id, scheduled_start)` and `(patient_id, scheduled_start)` support common queries and paging.

---

## MongoDB Collection Design

**Why MongoDB here?** Free-form clinician documents (notes, prescriptions) evolve frequently and include arrays and nested structures. Embedding immutable snapshots (patient/doctor names, allergy flags) preserves context even if master data changes later.

### Collection: `prescriptions`
```json
{
  "_id": "673f2b0c7a1e4f1d9cc01234",
  "appointmentId": 102345,              // join key to MySQL appointments (for analytics)
  "patient": {                          // snapshot to preserve "as-issued" view
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
  "followUp": { "recommendedDate": "2025-08-27T07:35:00Z", "reason": "Assess pain control and BP" },
  "tags": ["analgesic", "respiratory"],
  "audit": {
    "createdBy": "reception-01",
    "createdAt": "2025-08-20T07:35:05Z",
    "lastUpdatedAt": "2025-08-20T07:35:05Z",
    "source": "smart-clinic-web/v1.4.2"
  },
  "version": 1
}
```

**Indexes to create**
- `{ appointmentId: 1 }`
- `{ "patient.id": 1, "issuedAt": -1 }`
- `{ "doctor.id": 1, "issuedAt": -1 }`
- `{ "medications.drugCode": 1 }`

**Notes**
- Embedding patient/doctor **snapshots** avoids historical drift. Only store **non-sensitive** minimal fields as needed.
- Schema can evolve by adding fields to subdocuments (e.g., `dispense`, `substitutionAllowed`) without migrations.

---

## Operational Safeguards
- Enforce **no-overlap** appointments per doctor at application layer or with a BEFORE trigger (interval logic).
- Store only **password hashes** (argon2id/bcrypt).
- Use **UTC** for backend timestamps; convert to **Asia/Dubai** at the UI.
- Prefer **soft deletes** for legal retention; physically delete only when policy allows.
```sql
-- Pseudo-trigger idea (sketch only):
-- BEFORE INSERT/UPDATE ON appointments
--   IF EXISTS (
--     SELECT 1 FROM appointments
--     WHERE doctor_id = NEW.doctor_id
--       AND status IN ('BOOKED','CHECKED_IN')
--       AND NEW.scheduled_start < scheduled_end
--       AND NEW.scheduled_end   > scheduled_start
--   ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Overlapping appointment';
-- END IF;
```
