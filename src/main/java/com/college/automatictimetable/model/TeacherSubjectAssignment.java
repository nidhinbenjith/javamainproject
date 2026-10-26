package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "teacher_subject_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "subject_id"}))
public class TeacherSubjectAssignment extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    public Teacher teacher;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    public Subject subject;
}
