# Smart Clinic System — User Stories

> Roles: **Admin**, **Doctor**, **Patient**  
> Status values: `Proposed | In-Progress | Done`  
> Priority: `P0 (must)`, `P1 (should)`, `P2 (nice)`

---

## Admin User Stories

### A1. Admin logs in
**As an** Admin  
**I want** to sign in with username/password  
**So that** I can manage the clinic data  
**Priority:** P0 • **Status:** Proposed  
**Acceptance Criteria**
- Given valid credentials, when I POST `/admin/login`, then I receive `200 OK` with a JWT.
- Given invalid credentials, then I receive `401 Unauthorized`.

### A2. Manage doctors (CRUD)
**As an** Admin  
**I want** to create, update, and delete doctors  
**So that** I can keep provider data accurate  
**Priority:** P0  
**Acceptance Criteria**
- Create: POST `${api.path}doctor/{token}` with valid body → `201 Created`.
- Update: PUT `${api.path}doctor/{token}` with existing `id` → `200 OK`.
- Delete: DELETE `${api.path}doctor/{doctorId}/{token}` → `200 OK`.
- All require a valid **admin** token.

### A3. View daily appointments by doctor
**As an** Admin  
**I want** a daily grouped report  
**So that** I can balance workload  
**Priority:** P1  
**Acceptance Criteria**
- Calling stored procedure `sp_daily_appointments_by_doctor(date)` returns rows with:
  `doctor_id, doctor_name, total_appointments, completed_count, scheduled_count`.

### A4. Configure global API path
**As an** Admin  
**I want** endpoints to be prefixed via configuration  
**So that** the API is namespaced  
**Priority:** P2  
**Acceptance Criteria**
- Setting `api.path=/api/` in configuration prefixes all controllers using `${api.path}`.

---

## Patient User Stories

### P1. Patient registers
**As a** Patient  
**I want** to create an account  
**So that** I can book appointments  
**Priority:** P0  
**Acceptance Criteria**
- POST `/patient` with name/email/phone/password → `201 Created`.
- If email or phone exists → `409 Conflict`.

### P2. Patient logs in
**As a** Patient  
**I want** to log in with email/password  
**So that** I can access my data  
**Priority:** P0  
**Acceptance Criteria**
- POST `/patient/login` returns `200 OK` + JWT on success; `401` on failure.

### P3. Browse doctors
**As a** Patient  
**I want** to filter doctors by name/specialty/time (AM/PM)  
**So that** I can find a suitable provider  
**Priority:** P1  
**Acceptance Criteria**
- GET `${api.path}doctor/filter/{name}/{time}/{speciality}` returns list with `count`.

### P4. Check a doctor’s availability
**As a** Patient  
**I want** available slots for a date  
**So that** I can pick a free time  
**Priority:** P0  
**Acceptance Criteria**
- GET `${api.path}doctor/availability/patient/{doctorId}/{date}/{token}` returns `availableSlots` excluding already booked ones.

### P5. Book an appointment
**As a** Patient  
**I want** to book a slot  
**So that** I can see a doctor  
**Priority:** P0  
**Acceptance Criteria**
- POST `/appointments/book/{token}` with `doctor.id` and `appointmentTime` → `201 Created` if slot free; `409` if conflict.

### P6. Manage my appointments (update/cancel)
**As a** Patient  
**I want** to reschedule or cancel  
**So that** I can adjust plans  
**Priority:** P1  
**Acceptance Criteria**
- PUT `/appointments/{token}/{appointmentId}` to change time/doctor/status with ownership enforced.
- DELETE `/appointments/{token}/{appointmentId}` cancels if token belongs to booking patient.

### P7. View my appointment history
**As a** Patient  
**I want** to list past/future visits  
**So that** I can track care  
**Priority:** P1  
**Acceptance Criteria**
- GET `/patient/appointments/{user}/{patientId}/{token}` returns DTOs.
- GET `/patient/appointments/filter/{token}?condition=past|future&name=...` filters by status and doctor.

---

## Doctor User Stories

### D1. Doctor logs in
**As a** Doctor  
**I want** to authenticate  
**So that** I can access my schedule  
**Priority:** P0  
**Acceptance Criteria**
- POST `${api.path}doctor/login` with email/password → `200 OK` + JWT or `401`.

### D2. Set and view availability
**As a** Doctor  
**I want** to manage my available time slots  
**So that** patients can book correctly  
**Priority:** P1  
**Acceptance Criteria**
- Availability stored as string slots (`HH:mm-HH:mm`) and retrieved via
  `${api.path}doctor/availability/doctor/{doctorId}/{date}/{token}`.

### D3. See today’s appointments
**As a** Doctor  
**I want** my daily schedule  
**So that** I can prepare  
**Priority:** P1  
**Acceptance Criteria**
- GET `/appointments/{token}/{date}?doctorId={id}` returns all appointments for that date.

### D4. Complete an appointment & create prescription
**As a** Doctor  
**I want** to mark visits completed and issue a prescription  
**So that** care is recorded  
**Priority:** P1  
**Acceptance Criteria**
- POST `${api.path}prescription/{token}` with `{ appointmentId, medication, dosage, ... }` → `201`.
- On success, appointment status becomes `1 (Completed)`.

---

## Reporting User Stories

### R1. Top doctor by month
**As an** Admin  
**I want** to know who saw the most unique patients in a month  
**Priority:** P2  
**Acceptance Criteria**
- `CALL GetDoctorWithMostPatientsByMonth(year, month)` returns one row:  
  `doctor_id, doctor_name, unique_patients, total_appointments`.

### R2. Top doctor by year
**As an** Admin  
**I want** to know the annual top performer  
**Priority:** P2  
**Acceptance Criteria**
- `CALL GetDoctorWithMostPatientsByYear(year)` returns one row with same columns.

---

## Non-functional (cross-cutting)
- JWT auth for all protected endpoints; tokens expire in 7 days.
- Passwords should be hashed in production (BCrypt).
- Audit logs/prescriptions stored in MongoDB; operational data in MySQL.

---

## Backlog Links (suggested GitHub Issues)

- A1: Admin login
- A2: Doctor CRUD
- P1: Patient registration
- P5: Book appointment
- D4: Create prescription & complete appointment
- R1: Monthly top doctor
- R2: Yearly top doctor

