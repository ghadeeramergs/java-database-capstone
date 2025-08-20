package com.project.back_end.repo;


import com.project.back_end.models.Doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // Single doctor by email (returns null if not found)
    Doctor findByEmail(String email);

    // Name LIKE pattern (caller should pass %pattern%)
    List<Doctor> findByNameLike(String name);

    // Name contains (case-insensitive) AND specialty equals (case-insensitive)
    List<Doctor> findByNameContainingIgnoreCaseAndSpecialtyIgnoreCase(String name, String specialty);

    // All doctors by specialty (case-insensitive)
    List<Doctor> findBySpecialtyIgnoreCase(String specialty);

    // Used by TokenService
    boolean existsByEmail(String email);
}
