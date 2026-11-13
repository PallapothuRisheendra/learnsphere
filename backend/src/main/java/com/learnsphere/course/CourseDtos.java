package com.learnsphere.course;

public final class CourseDtos {
    private CourseDtos() {}
    public record CourseView(Long id, String name, String category, String level, String duration,
                             String description, String icon, String color, int progress, boolean enrolled,
                             long priceMinor, String currency, String thumbnailUrl, boolean published) {}
    public record CoursePage(java.util.List<CourseView> content, int page, int size,
                             long totalElements, int totalPages, String sort) {}
    public record CourseInput(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=180) String name,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=80) String category,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40) String level,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40) String duration,
                              @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1200) String description,
                              @jakarta.validation.constraints.Size(max=8) String icon,
                              @jakarta.validation.constraints.Pattern(regexp="violet|blue|orange|green|pink") String color,
                              @jakarta.validation.constraints.PositiveOrZero long priceMinor,
                              @jakarta.validation.constraints.Pattern(regexp="[A-Z]{3}") String currency,
                              @jakarta.validation.constraints.Pattern(regexp="^$|https://[^\\s]+", message="Thumbnail URL must use HTTPS") String thumbnailUrl) {}
}
