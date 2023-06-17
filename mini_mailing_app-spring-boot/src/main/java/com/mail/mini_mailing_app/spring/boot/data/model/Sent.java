package com.mail.mini_mailing_app.spring.boot.data.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class Sent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String toEmail;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Message message;
//    private String subject;
//    private String messageBody;
//    private LocalDateTime createdAt;
//    @Enumerated(EnumType.STRING)
//    private final MessageType messageType = MessageType.SENT;
}
