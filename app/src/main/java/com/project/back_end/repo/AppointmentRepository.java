package com.project.back_end.repo;


import com.project.back_end.models.Appointment;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // ---------------------------------------------
    // 1) Appointments for a doctor within a time range,
    //    eagerly fetching doctor's availableTimes
    // ---------------------------------------------
    @Query("""
           select distinct a
           from Appointment a
             join fetch a.doctor d
             left join fetch d.availableTimes
           where d.id = :doctorId
             and a.appointmentTime between :start and :end
           order by a.appointmentTime asc
           """)
    List<Appointment> findByDoctorIdAndAppointmentTimeBetween(
            @Param("doctorId") Long doctorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
                                                             );

    // ---------------------------------------------
    // 2) Appointments for a doctor filtered by patient name (case-insensitive)
    //    within a time range; also fetch doctor's availableTimes
    // ---------------------------------------------
    @Query("""
           select distinct a
           from Appointment a
             join a.patient p
             join fetch a.doctor d
             left join fetch d.availableTimes
           where d.id = :doctorId
             and a.appointmentTime between :start and :end
             and lower(p.name) like lower(concat('%', :patientName, '%'))
           order by a.appointmentTime asc
           """)
    List<Appointment> findByDoctorIdAndPatient_NameContainingIgnoreCaseAndAppointmentTimeBetween(
            @Param("doctorId") Long doctorId,
            @Param("patientName") String patientName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
                                                                                                );

    // ---------------------------------------------
    // 3) Delete all appointments for a doctor
    // ---------------------------------------------
    @Modifying
    @Transactional
    @Query("delete from Appointment a where a.doctor.id = :doctorId")
    void deleteAllByDoctorId(@Param("doctorId") Long doctorId);

    // ---------------------------------------------
    // 4) All appointments for a patient
    // ---------------------------------------------
    List<Appointment> findByPatient_Id(Long patientId);

    // ---------------------------------------------
    // 5) All appointments for a patient with status, ordered by time
    // ---------------------------------------------
    List<Appointment> findByPatient_IdAndStatusOrderByAppointmentTimeAsc(Long patientId, int status);

// ---------------------------------------------
// 6) Filter by doctor name (LIKE)
// ---------------------------------------------
@Query("""
           select a
           from Appointment a
             join a.doctor d
           where lower(d.name) like lower(concat('%', :doctorName, '%'))
             and a.patient.id = :patientId
           order by a.appointmentTime asc
           """)
List<Appointment> filterByDoctorNameAndPatientId(
        @Param("doctorName") String doctorName,
        @Param("patientId") Long patientId
                                                );

    // ---------------------------------------------
    // 7) Filter by doctor name (LIKE), patient id, and status
    // ---------------------------------------------
    @Query("""
           select a
           from Appointment a
             join a.doctor d
           where lower(d.name) like lower(concat('%', :doctorName, '%'))
             and a.patient.id = :patientId
             and a.status = :status
           order by a.appointmentTime asc
           """)
    List<Appointment> filterByDoctorNameAndPatientIdAndStatus(
            @Param("doctorName") String doctorName,
            @Param("patientId") Long patientId,
            @Param("status") int status
                                                             );

    // ---------------------------------------------
    // 8) Update status by appointment id
    // ---------------------------------------------
    @Modifying
    @Transactional
    @Query("update Appointment a set a.status = :status where a.id = :id")
    void updateStatus(@Param("status") int status, @Param("id") long id);
}