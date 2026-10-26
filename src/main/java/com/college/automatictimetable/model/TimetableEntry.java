package com.college.automatictimetable.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;

@Entity
@Table(name = "timetable_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_class_period", columnNames = {"class_id", "day_of_week", "time_slot_id"}),
        @UniqueConstraint(name = "uq_teacher_period", columnNames = {"teacher_id", "day_of_week", "time_slot_id"}),
        @UniqueConstraint(name = "uq_room_period", columnNames = {"classroom_id", "day_of_week", "time_slot_id"})
})
public class TimetableEntry extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    public StudentClass studentClass;
    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    public Subject subject;
    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    public Teacher teacher;
    @ManyToOne(optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    public Classroom classroom;
    @ManyToOne(optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    public TimeSlot timeSlot;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    public DayOfWeek dayOfWeek;
}
