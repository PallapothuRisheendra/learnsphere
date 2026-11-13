package com.learnsphere.lesson;

import com.learnsphere.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lesson_completions", uniqueConstraints = @UniqueConstraint(name = "uk_completion_user_lesson", columnNames = {"user_id", "lesson_id"}))
public class LessonCompletion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
    @Column(nullable = false)
    private Instant completedAt;

    protected LessonCompletion() {}
    public LessonCompletion(User user, Lesson lesson) { this.user = user; this.lesson = lesson; this.completedAt = Instant.now(); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Lesson getLesson() { return lesson; }
    public Instant getCompletedAt() { return completedAt; }
}
