package org.school.email.service;

import lombok.extern.slf4j.Slf4j;
import org.school.email.config.EmailConfig;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class MailService {

    private final EmailConfig emailConfig;

    public MailService(EmailConfig emailConfig) {
        this.emailConfig = emailConfig;
    }

    public void sendEmailWithAttachment(String filePath, String subject, String messageText) throws MessagingException {
        sendEmailWithAttachment(emailConfig.getDefaultRecipients(), subject, messageText, filePath);
    }

    public void sendEmailWithAttachment(List<String> recipients, String subject,
                                        String messageText, String attachmentPath) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", emailConfig.getSmtpHost());
        props.put("mail.smtp.port", emailConfig.getSmtpPort());
        props.put("mail.smtp.auth", "true");

        if (emailConfig.isUseSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        if (emailConfig.isUseTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailConfig.getUsername(), emailConfig.getPassword());
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(emailConfig.getUsername()));

            InternetAddress[] addresses = new InternetAddress[recipients.size()];
            for (int i = 0; i < recipients.size(); i++) {
                addresses[i] = new InternetAddress(recipients.get(i));
            }
            message.setRecipients(Message.RecipientType.TO, addresses);

            message.setSubject(subject);
            message.setSentDate(new java.util.Date());

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(messageText, "utf-8");
            multipart.addBodyPart(textPart);

            if (attachmentPath != null) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(attachmentPath);
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);
            Transport.send(message);

            log.info("Письмо успешно отправлено {} получателям", recipients.size());

        } catch (Exception e) {
            log.error("Ошибка при отправке письма", e);
            throw new MessagingException("Не удалось отправить письмо", e);
        }
    }

    public boolean testConnection() {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", emailConfig.getSmtpHost());
            props.put("mail.smtp.port", emailConfig.getSmtpPort());
            props.put("mail.smtp.auth", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailConfig.getUsername(), emailConfig.getPassword());
                }
            });

            Transport transport = session.getTransport("smtp");
            transport.connect(emailConfig.getSmtpHost(), emailConfig.getSmtpPort(), emailConfig.getUsername(), emailConfig.getPassword());
            transport.close();

            log.info("Соединение с почтовым сервером установлено успешно");
            return true;

        } catch (Exception e) {
            log.error("Ошибка соединения с почтовым сервером", e);
            return false;
        }
    }
}
