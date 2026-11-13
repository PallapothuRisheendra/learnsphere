package com.learnsphere.course;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 180)
    private String slug;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(nullable = false, length = 40)
    private String level;
    @Column(nullable = false, length = 40)
    private String duration;
    @Column(nullable = false, length = 1200)
    private String description;
    @Column(nullable = false, length = 8)
    private String icon;
    @Column(nullable = false, length = 24)
    private String color;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private long priceMinor;
    @Column(nullable = false, length = 3)
    private String currency = "INR";
    @Column(name="thumbnail_url", length=2048)
    private String thumbnailUrl;

    protected Course() {}
    public Course(String name, String category, String level, String duration, String description,
                  String icon, String color, long priceMinor, String currency, String thumbnailUrl) {
        this.name = name; this.slug = slugify(name); this.category = category; this.level = level;
        this.duration = duration; this.description = description;
        this.icon = icon == null || icon.isBlank() ? name.substring(0, Math.min(2, name.length())) : icon;
        this.color = color == null || color.isBlank() ? "violet" : color;
        this.priceMinor = priceMinor; this.currency = currency == null || currency.isBlank() ? "INR" : currency;
        this.thumbnailUrl = thumbnailUrl;
    }
    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getLevel() { return level; }
    public String getDuration() { return duration; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public boolean isActive() { return active; }
    public long getPriceMinor() { return priceMinor; }
    public String getCurrency() { return currency; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void update(String name, String category, String level, String duration, String description,
                       String icon, String color, long priceMinor, String currency, String thumbnailUrl) {
        this.name = name; this.category = category; this.level = level; this.duration = duration;
        this.description = description; this.icon = icon; this.color = color;
        this.priceMinor = priceMinor; this.currency = currency;
        this.thumbnailUrl = thumbnailUrl; this.active = true;
        this.slug = slugify(name);
    }
    public void deactivate() { this.active = false; }
    public void publish() { this.active = true; }
    private String slugify(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
