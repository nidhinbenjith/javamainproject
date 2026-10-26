package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "subject_requirements", uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "subject_id"}))
public class SubjectRequirement extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    public StudentClass studentClass;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    public Subject subject;
    @NotNull @Positive
    @Column(nullable = false)
    public Integer periodsPerWeek;
}
