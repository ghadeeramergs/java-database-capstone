package com.project.back_end.repo;




import com.project.back_end.models.Admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    // Custom query method: returns null if not found (per Spring Data behavior)
    Admin findByUsername(String username);

    // Used by TokenService to validate tokens by email
    boolean existsByEmail(String email);
}

