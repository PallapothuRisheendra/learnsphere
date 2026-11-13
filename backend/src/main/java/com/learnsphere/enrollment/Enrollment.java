package com.learnsphere.enrollment;

import com.learnsphere.course.Course;
import com.learnsphere.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enrollments", uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_user_course", columnNames = {"user_id", "course_id"}))
public class Enrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @Column(nullable = false)
    private int progressPercent;
    @Column(nullable = false)
    private Instant enrolledAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Enrollment() {}
    public Enrollment(User user, Course course) {
        this.user = user;
        this.course = course;
        this.progressPercent = 0;
        this.enrolledAt = Instant.now();
        this.updatedAt = this.enrolledAt;
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Course getCourse() { return course; }
    public int getProgressPercent() { return progressPercent; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateProgress(int progress) { this.progressPercent = progress; this.updatedAt = Instant.now(); }
}
