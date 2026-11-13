package com.learnsphere.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MailService {
    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String host;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${app.mail.host:}") String host,
                       @Value("${app.mail.from:}") String from) {
        this.senderProvider = senderProvider; this.host = host; this.from = from;
    }

    public void sendActionLink(String recipient, String subject, String link) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (host.isBlank() || from.isBlank() || sender == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured. Set MAIL_HOST, MAIL_FROM, and provider credentials.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText("Use the following one-time link. It expires shortly and can only be used once:\n\n" + link);
        sender.send(message);
    }
}
