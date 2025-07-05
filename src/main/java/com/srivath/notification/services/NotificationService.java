package com.srivath.notification.services;

import com.srivath.ecombasedomain.dtos.PaymentCompletedDto;
import com.srivath.ecombasedomain.events.Event;
import com.srivath.ecombasedomain.dtos.OrderDto;
import com.srivath.ecombasedomain.events.OrderPlacedEvent;
import com.srivath.ecombasedomain.events.PaymentCompletedEvent;
import com.srivath.ecombasedomain.events.PaymentLinkCreatedEvent;
import com.srivath.notification.models.Notification;
import com.srivath.notification.repositories.NotificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDate;

@Service
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    NotificationRepository notificationRepository;

    public NotificationService()
    {

    }

    @Value("${notification.email.from}")
    private String fromEmail;

    public void sendEmail(String from, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(from);
        mailSender.send(message);
    }

    public void sendMIMEEmail(String from, String to, String subject, String body) throws MessagingException {
        // Implementation for sending MIME email can be added here

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(to);
        helper.setFrom(from);
        helper.setSubject(subject);
        helper.setText(body, true);

        mailSender.send(message);

    }

    public void notify(String eventName, String from, String to, String subject, String body, Long orderId) {
        Notification notification = notificationRepository.findByEventNameAndOrderId(eventName, orderId).orElse(null);

        if (notification == null) {
            notification = new Notification();
        }
        notification.setFrom(from);
        notification.setTo(to);
        notification.setSentDate(LocalDate.now());
        notification.setSubject(subject);
        notification.setMessage(body);
        notification.setOrderId(orderId);
        String status = "PENDING";
        notification.setStatus(status);
        notification.setEventName(eventName);
        // Save the notification to the database
        notificationRepository.save(notification);
        try {
            sendMIMEEmail(from, to, subject, body);
            status = "SUCCESS";
            notification.setStatus(status);
        } catch (Exception e) {
            status = "FAILURE";
            notification.setStatus(status);
        } finally {
            notificationRepository.save(notification);

        }
    }


    @KafkaListener(topics = "${spring.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(Event event) {
        if (event != null && "ORDER_PLACED".equals(event.getEventName())) {

            OrderPlacedEvent orderPlacedEvent = (OrderPlacedEvent) event;
            OrderDto orderDto = orderPlacedEvent.getOrderDto();
            String subject = "Order Placed Notification - Order # " + orderDto.getOrderId();
            String message = "Hi, \n Your order with Order # " + orderDto.getOrderId() + " has been successfully placed on "
                    + orderDto.getOrderDate() + " !" + "\n \n Order Amount : " + orderDto.getOrderAmount()
                    + "\n \n Thank you for shopping with us! \n Regards, \nE-commerce Team";
            String to = orderDto.getUserEmail();
            String from = fromEmail;
            String orderId = orderDto.getOrderId().toString();
            notify(event.getEventName(), from, to, subject, message, orderDto.getOrderId());
        }

        if (event != null && "PAYMENT_LINK_CREATED".equals(event.getEventName()))
        {
            PaymentLinkCreatedEvent paymentLinkCreatedEvent = (PaymentLinkCreatedEvent) event;
            String subject = "Payment Link Created - Order # " + paymentLinkCreatedEvent.getOrderId();
            String message = "Hi " + paymentLinkCreatedEvent.getUserName() + ", \n Your payment link for Order # " + paymentLinkCreatedEvent.getOrderId() + " has been created successfully on "
                    + LocalDate.now() + " !" + "\n \n Amount : " + paymentLinkCreatedEvent.getAmount()
                    + "\n \n Payment Link : " + paymentLinkCreatedEvent.getPaymentLink()
                    + "\n \n <b> Please complete the payment to confirm the order. </b> Thank you for shopping with us! \n Regards, \nE-commerce Team";
            String to = paymentLinkCreatedEvent.getUserEmail();
            Long orderId = paymentLinkCreatedEvent.getOrderId();
            String from = fromEmail;

            notify(event.getEventName(), from, to, subject, message, orderId);
        }

        if (event != null && "PAYMENT_COMPLETED".equals(event.getEventName()))
        {
            PaymentCompletedEvent paymentCompletedEvent = (PaymentCompletedEvent) event;
            String subject = "Payment Completed - Order # " + paymentCompletedEvent.getPaymentDto().getOrderId();
//            String message = "Hi " + .getPaymentDto().getUserName() + ", \n Your payment for Order # " + paymentCompletedEvent.getPaymentDto().getOrderId() + " has been completed successfully on "
//                    + LocalDate.now() + " !" + "\n \n Amount : " + paymentCompletedEvent.getAmount()
//                    + "\n \n Payment Link : " + paymentCompletedEvent.getPaymentLink()
//                    + "\n \n <b> Please complete the payment to confirm the order. </b> Thank you for shopping with us! \n Regards, \nE-commerce Team";
            String message = formPaymentReceipt(paymentCompletedEvent.getPaymentDto());
            String to = paymentCompletedEvent.getPaymentDto().getUserEmail();
            Long orderId = paymentCompletedEvent.getPaymentDto().getOrderId();
            String from = fromEmail;

            notify(event.getEventName(), from, to, subject, message, orderId);
        }

    }

    private String formPaymentReceipt(PaymentCompletedDto paymentDto) {
        Context context = new Context();

        context.setVariable("userName", paymentDto.getUserName());
        context.setVariable("paymentId", paymentDto.getPaymentId());
        context.setVariable("orderId", paymentDto.getOrderId());
        context.setVariable("amount", paymentDto.getAmount());
        context.setVariable("status", paymentDto.getStatus());
        context.setVariable("paymentInstances", paymentDto.getPaymentInstances());

        double amountPaid = paymentDto.getPaymentInstances().stream().map(p -> p.getAmount()).reduce(0.0,(b,a) -> (a+b));
        context.setVariable("amountPaid", amountPaid);

        String htmlContent = templateEngine.process("paymentReceipt", context);
        return htmlContent;
    }
}
