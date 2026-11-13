package com.learnsphere.lesson;

import com.learnsphere.course.Course;
import jakarta.persistence.*;

@Entity
@Table(name = "lessons")
public class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    protected Lesson() {}
    public Lesson(Course course, String title, String description, String content, int sortOrder, String videoUrl) {
        this.course = course; this.title = title; this.description = description; this.content = content;
        this.sortOrder = sortOrder; this.videoUrl = videoUrl;
    }
    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public String getVideoUrl() { return videoUrl; }
    public void update(String title, String description, String content, int sortOrder, String videoUrl) {
        this.title = title; this.description = description; this.content = content;
        this.sortOrder = sortOrder; this.videoUrl = videoUrl;
    }
    public void deactivate() { this.active = false; }
}
