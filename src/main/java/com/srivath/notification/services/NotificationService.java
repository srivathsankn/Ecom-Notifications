package com.srivath.notification.services;

import com.srivath.ecombasedomain.events.Event;
import com.srivath.ecombasedomain.dtos.OrderDto;
import com.srivath.ecombasedomain.events.OrderPlacedEvent;
import com.srivath.notification.models.Notification;
import com.srivath.notification.repositories.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    NotificationRepository notificationRepository;

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom("sri123.ecom@gmail.com");


        mailSender.send(message);
    }

    @Transactional
    @KafkaListener(topics = "${spring.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(Event event) {
        if (event != null && "ORDER_PLACED".equals(event.getEventName())) {
            String status = "PENDING";
            OrderPlacedEvent orderPlacedEvent = (OrderPlacedEvent) event;
            OrderDto orderDto = orderPlacedEvent.getOrderDto();
            String subject = "Order Placed Notification - Order # " + orderDto.getOrderId();
            String message = "Hi, \n Your order with Order # " + orderDto.getOrderId() + " has been successfully placed on "
                    + orderDto.getOrderDate() + " !" +
                    "\n \n Thank you for shopping with us! \n Regards, \nE-commerce Team";
            String to = orderDto.getUserEmail();

            Notification notification = notificationRepository.findByEventNameAndOrderId(event.getEventName(), orderDto.getOrderId()).orElse(null);

            if (notification == null) {
                notification = new Notification();
                notification.setFrom("sri123.ecom@gmail.com");
                notification.setTo(to);
                notification.setSentDate(LocalDate.now());
                notification.setSubject(subject);
                notification.setMessage(message);
                notification.setOrderId(orderDto.getOrderId());
                notification.setStatus(status);
                notification.setEventName(event.getEventName());
                // Save the notification to the database
                notificationRepository.save(notification);
                try {
                    sendEmail(to, subject, message);
                    status = "SUCCESS";
                    notification.setStatus(status);
                } catch (Exception e) {
                    status = "FAILURE";
                    notification.setStatus(status);
                } finally {
                    notificationRepository.save(notification);
                }
            }
        }
    }
}
