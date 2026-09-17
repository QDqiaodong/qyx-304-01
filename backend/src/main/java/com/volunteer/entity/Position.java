package com.volunteer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "required_skills", length = 500)
    private String requiredSkills;

    @Column(name = "required_certificates", length = 500)
    private String requiredCertificates;

    @Column(name = "required_hours")
    private Integer requiredHours;

    @Column(name = "min_count")
    private Integer minCount;

    @Column(name = "max_count")
    private Integer maxCount;

    @Column(name = "requirement_version")
    private Integer requirementVersion;

    @Column(name = "status")
    private Integer status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 岗位实际占编人数：有效校验通过且仍占编的报名数（不计退回单、复核失败单） */
    @Transient
    private Integer occupiedCount;

    /** 岗位是否已满员：occupiedCount >= maxCount，复核失败的人不计入 */
    @Transient
    private Boolean full;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
        if (minCount == null) {
            minCount = 1;
        }
        if (maxCount == null) {
            maxCount = 10;
        }
        if (requiredHours == null) {
            requiredHours = 0;
        }
        if (requirementVersion == null) {
            requirementVersion = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}