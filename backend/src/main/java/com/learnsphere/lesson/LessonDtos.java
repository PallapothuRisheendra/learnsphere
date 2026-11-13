package com.learnsphere.lesson;

public final class LessonDtos {
    private LessonDtos() {}
    public record LessonView(Long id, String title, String description, String content,
                             int sortOrder, boolean completed, int courseProgress, String videoUrl) {}
    public record LessonInput(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=180) String title,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String description,
                              @jakarta.validation.constraints.NotBlank String content,
                              @jakarta.validation.constraints.PositiveOrZero int sortOrder,
                              @jakarta.validation.constraints.Pattern(regexp="^$|https://[^\\s]+", message="Video URL must use HTTPS") String videoUrl) {}
}
