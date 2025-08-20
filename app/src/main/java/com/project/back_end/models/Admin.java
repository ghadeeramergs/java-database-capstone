package com.project.back_end.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
public class Admin {

    // 1. Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 2. Username
    @NotNull
    @Column(nullable = false, unique = true) // ensure uniqueness in DB as well
    private String username;

    // 3. Password
    @NotNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // write-only in JSON
    @Column(nullable = false)
    private String password;

    // 4. Default Constructor (required by JPA)
    public Admin() {}

    // Optional Parameterized Constructor
    public Admin(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // 5. Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
