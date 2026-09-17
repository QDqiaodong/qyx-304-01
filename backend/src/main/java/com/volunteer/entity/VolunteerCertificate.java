package com.volunteer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "volunteer_certificates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VolunteerCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volunteer_id", nullable = false)
    private Long volunteerId;

    @Column(name = "cert_name", nullable = false, length = 100)
    private String certName;

    @Column(name = "cert_no", length = 100)
    private String certNo;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}