package com.srivath.notification.repositories;

import com.srivath.notification.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Custom query methods can be defined here if needed
    // For example, find notifications by user ID or status
    Optional<Notification> findByEventNameAndOrderId(String eventName, Long orderId);
}
