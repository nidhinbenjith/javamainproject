package com.college.automatictimetable.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

@Entity
@Table(name = "teacher_availability", uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "day_of_week", "time_slot_id"}))
public class TeacherAvailability extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    public Teacher teacher;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    public DayOfWeek dayOfWeek;
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    public TimeSlot timeSlot;
    @Column(nullable = false)
    public boolean available;
}
