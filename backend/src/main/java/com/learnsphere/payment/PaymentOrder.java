package com.learnsphere.payment;

import com.learnsphere.course.Course;
import com.learnsphere.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="payment_orders")
public class PaymentOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="provider_order_id", nullable=false, unique=true, length=100) private String providerOrderId;
    @Column(name="provider_payment_id", length=100) private String providerPaymentId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="course_id", nullable=false) private Course course;
    @Column(name="amount_minor", nullable=false) private long amountMinor;
    @Column(nullable=false, length=3) private String currency;
    @Column(nullable=false, length=24) private String status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="paid_at") private Instant paidAt;
    protected PaymentOrder() {}
    public PaymentOrder(String orderId, User user, Course course) {
        this.providerOrderId=orderId; this.user=user; this.course=course; this.amountMinor=course.getPriceMinor();
        this.currency=course.getCurrency(); this.status="CREATED"; this.createdAt=Instant.now();
    }
    public Long getId(){return id;} public String getProviderOrderId(){return providerOrderId;} public String getProviderPaymentId(){return providerPaymentId;}
    public User getUser(){return user;} public Course getCourse(){return course;} public long getAmountMinor(){return amountMinor;}
    public String getCurrency(){return currency;} public String getStatus(){return status;}
    public void captured(String paymentId){this.providerPaymentId=paymentId;this.status="PAID";this.paidAt=Instant.now();}
    public void failed(String paymentId){this.providerPaymentId=paymentId;this.status="FAILED";}
}
