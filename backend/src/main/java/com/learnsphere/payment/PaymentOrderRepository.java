package com.learnsphere.payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder,Long> {
    Optional<PaymentOrder> findByProviderOrderIdAndUser_EmailIgnoreCase(String orderId,String email);
}
