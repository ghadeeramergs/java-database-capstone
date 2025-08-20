package com.project.back_end.services;


import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class TokenService {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Value("${jwt.secret}")
    private String jwtSecret; // ensure >= 32 chars for HS256

    public TokenService(AdminRepository adminRepository,
                        DoctorRepository doctorRepository,
                        PatientRepository patientRepository) {
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
    }

    private SecretKey getSigningKey() {
        // If your secret is Base64, decode first; here we use raw bytes:
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(7 * 24 * 60 * 60); // 7 days
        return Jwts.builder()
                   .setSubject(email)
                   .setIssuedAt(Date.from(now))
                   .setExpiration(Date.from(exp))
                   .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                   .compact();
    }

    public String extractEmail(String token) {
        Claims claims = parseClaims(trimBearer(token));
        return claims.getSubject();
    }

    public boolean validateToken(String token, String role) {
        try {
            String email = extractEmail(token);
            if (email == null || email.isBlank() || role == null) return false;
            switch (role.toLowerCase()) {
                case "admin":
                    return adminRepository.existsByEmail(email);
                case "doctor":
                    return doctorRepository.existsByEmail(email);
                case "patient":
                    return patientRepository.existsByEmail(email);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // --- helpers ---

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                   .setSigningKey(getSigningKey())
                   .build()
                   .parseClaimsJws(token)
                   .getBody();
    }

    private String trimBearer(String token) {
        if (token == null) return "";
        String t = token.trim();
        return t.regionMatches(true, 0, "Bearer ", 0, 7) ? t.substring(7).trim() : t;
    }
}
