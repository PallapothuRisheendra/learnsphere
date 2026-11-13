package com.learnsphere.payment;

import com.learnsphere.course.Course;
import com.learnsphere.course.CourseRepository;
import com.learnsphere.enrollment.Enrollment;
import com.learnsphere.enrollment.EnrollmentRepository;
import com.learnsphere.user.User;
import com.learnsphere.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    public record OrderView(String orderId, long amount, String currency, String keyId) {}
    public record VerificationView(String status, Long courseId) {}
    private final PaymentOrderRepository orders; private final CourseRepository courses;
    private final EnrollmentRepository enrollments; private final UserRepository users;
    private final RestClient razorpay; private final String keyId; private final String secret;
    public PaymentService(PaymentOrderRepository orders, CourseRepository courses, EnrollmentRepository enrollments,
                          UserRepository users,
                          @Value("${app.payment.razorpay-key-id:}") String keyId,
                          @Value("${app.payment.razorpay-key-secret:}") String secret) {
        this.orders=orders;this.courses=courses;this.enrollments=enrollments;this.users=users;this.keyId=keyId;this.secret=secret;
        this.razorpay=RestClient.builder().baseUrl("https://api.razorpay.com/v1").defaultHeaders(h -> h.setBasicAuth(keyId,secret)).build();
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public OrderView create(String email,Long courseId) {
        ensureConfigured();
        Course course=courses.findByIdAndActiveTrue(courseId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Course not found."));
        if(course.getPriceMinor()<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This course is free; enroll from the course catalog.");
        if(enrollments.existsByUser_EmailIgnoreCaseAndCourse_Id(email,courseId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"You are already enrolled.");
        try {
            Map response=razorpay.post().uri("/orders").body(Map.of("amount",course.getPriceMinor(),"currency",course.getCurrency(),"receipt","ls-"+java.util.UUID.randomUUID())).retrieve().body(Map.class);
            String orderId=response==null?null:(String)response.get("id");
            if(orderId==null||orderId.isBlank()) throw new IllegalStateException("Payment provider returned no order ID.");
            User user=users.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is unavailable."));
            orders.save(new PaymentOrder(orderId,user,course));
            return new OrderView(orderId,course.getPriceMinor(),course.getCurrency(),keyId);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Could not create a payment order with Razorpay."); }
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public VerificationView verify(String email,String orderId,String paymentId,String signature) {
        ensureConfigured();
        PaymentOrder order=orders.findByProviderOrderIdAndUser_EmailIgnoreCase(orderId,email)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment order not found."));
        if("PAID".equals(order.getStatus())) return new VerificationView("PAID",order.getCourse().getId());
        if(!validSignature(orderId,paymentId,signature)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment signature is invalid.");
        try {
            Map captured=razorpay.post().uri("/payments/{id}/capture",paymentId).body(Map.of("amount",order.getAmountMinor(),"currency",order.getCurrency())).retrieve().body(Map.class);
            boolean isCaptured=captured!=null&&"captured".equals(captured.get("status"))&&orderId.equals(captured.get("order_id"))
                && ((Number)captured.getOrDefault("amount",-1)).longValue()==order.getAmountMinor()
                && order.getCurrency().equals(captured.get("currency"));
            if(!isCaptured){order.failed(paymentId);throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,"Payment is not captured. You have not been enrolled.");}
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment status could not be confirmed with Razorpay."); }
        order.captured(paymentId);
        if(!enrollments.existsByUser_EmailIgnoreCaseAndCourse_Id(email,order.getCourse().getId())) {
            User user=users.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is unavailable."));
            enrollments.save(new Enrollment(user,order.getCourse()));
        }
        return new VerificationView("PAID",order.getCourse().getId());
    }
    private boolean validSignature(String order,String payment,String supplied) {
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=HexFormat.of().formatHex(mac.doFinal((order+"|"+payment).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return supplied!=null&&MessageDigest.isEqual(expected,supplied.getBytes(StandardCharsets.US_ASCII));
        } catch(Exception e){return false;}
    }
    private void ensureConfigured(){if(keyId.isBlank()||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Razorpay sandbox credentials are not configured.");}
}
