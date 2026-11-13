package com.learnsphere.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    public record VerifyRequest(@NotBlank @Size(max=100) String orderId,@NotBlank @Size(max=100) String paymentId,@NotBlank @Size(max=256) String signature){}
    private final PaymentService payments;
    public PaymentController(PaymentService payments){this.payments=payments;}
    @PostMapping("/courses/{courseId}/order") public PaymentService.OrderView create(Authentication auth,@PathVariable Long courseId){return payments.create(auth.getName(),courseId);}
    @PostMapping("/verify") public PaymentService.VerificationView verify(Authentication auth,@Valid @RequestBody VerifyRequest body){return payments.verify(auth.getName(),body.orderId(),body.paymentId(),body.signature());}
}
