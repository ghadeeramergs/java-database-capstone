
package com.project.back_end.controllers;


import com.project.back_end.models.Admin;
import com.project.back_end.services.Service;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("${api.path}admin")
public class AdminController {

    private final Service adminService;

    // 2. Constructor injection
    public AdminController(Service adminService) {
        this.adminService = adminService;
    }

    // 3. POST /login
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> adminLogin(@Valid @RequestBody Admin admin) {
        Map<String, Object> result =
                adminService.validateAdmin(admin.getUsername(), admin.getPassword());
        boolean ok = (boolean) result.getOrDefault("authenticated", false);
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).body(result);
    }

    // Optional: token validation endpoint
    @GetMapping("/token/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        Map<String, Object> result = adminService.validateToken(authorization);
        boolean valid = (boolean) result.getOrDefault("valid", false);
        return ResponseEntity.status(valid ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).body(result);
    }
}

