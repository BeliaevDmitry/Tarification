package org.school.email.service;

import org.school.email.config.EmailConfig;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MailService {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useSSL;
    private final boolean useTLS;

    // Конструктор с настройками из конфига
    public MailService() {
        this.host = EmailConfig.SMTP_HOST;
        this.port = EmailConfig.SMTP_PORT;
        this.username = EmailConfig.EMAIL_USERNAME;
        this.password = EmailConfig.EMAIL_PASSWORD;
        this.useSSL = EmailConfig.USE_SSL;
        this.useTLS = EmailConfig.USE_TLS;
    }

    // Конструктор с кастомными настройками
    public MailService(String host, int port, String username, String password, boolean useSSL, boolean useTLS) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.useTLS = useTLS;
    }

    /**
     * Простая отправка письма с вложением
     */
    public void sendEmailWithAttachment(String filePath, String subject, String messageText) throws MessagingException {
        List<String> recipients = Arrays.asList(EmailConfig.DEFAULT_RECIPIENTS);
        sendEmailWithAttachment(recipients, subject, messageText, filePath);
    }

    /**
     * Отправка письма с вложением нескольким получателям
     */
    public void sendEmailWithAttachment(List<String> recipients, String subject,
                                        String messageText, String attachmentPath) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");

        if (useSSL) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        if (useTLS) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));

            // Добавляем получателей
            InternetAddress[] addresses = new InternetAddress[recipients.size()];
            for (int i = 0; i < recipients.size(); i++) {
                addresses[i] = new InternetAddress(recipients.get(i));
            }
            message.setRecipients(Message.RecipientType.TO, addresses);

            message.setSubject(subject);
            message.setSentDate(new java.util.Date());

            // Создаем multipart сообщение
            Multipart multipart = new MimeMultipart();

            // Текстовая часть
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(messageText, "utf-8");
            multipart.addBodyPart(textPart);

            // Добавляем вложение
            if (attachmentPath != null) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(attachmentPath);
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            // Отправляем письмо
            Transport.send(message);

            System.out.println("✅ Письмо успешно отправлено " + recipients.size() + " получателям");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при отправке письма: " + e.getMessage());
            throw new MessagingException("Не удалось отправить письмо", e);
        }
    }

    /**
     * Проверка соединения с почтовым сервером
     */
    public boolean testConnection() {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.auth", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            transport.close();

            System.out.println("✅ Соединение с почтовым сервером установлено успешно");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Ошибка соединения с почтовым сервером: " + e.getMessage());
            return false;
        }
    }
}