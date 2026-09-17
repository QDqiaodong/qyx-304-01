package com.volunteer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "volunteer_skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VolunteerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volunteer_id", nullable = false)
    private Long volunteerId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "skill_level")
    private Integer skillLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (skillLevel == null) {
            skillLevel = 1;
        }
    }
}