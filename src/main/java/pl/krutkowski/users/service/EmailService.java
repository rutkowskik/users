package pl.krutkowski.users.service;

import com.sun.mail.smtp.SMTPTransport;
import org.springframework.stereotype.Service;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

import static pl.krutkowski.users.constant.EmailConstant.*;

@Service
public class EmailService {

    public void sendNewPasswordEmail(String firstName, String email, String password) throws MessagingException {
        Message message = createEmail(firstName, password, email);
        SMTPTransport smtpTransport = (SMTPTransport) getEmailSession().getTransport(SIMPLE_MAIL_TRANSFER_PROTOCOL);
        smtpTransport.connect(GMAIL_SMTP_SERVER, USERNAME, PASSWORD);
        smtpTransport.sendMessage(message, message.getAllRecipients());
        smtpTransport.close();
    }

    private Message createEmail(String firstName, String password, String email) throws MessagingException {
        Message message = new MimeMessage(getEmailSession());
        message.setFrom(new InternetAddress(FROM_EMAIL));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email, false));
        message.setRecipient(Message.RecipientType.CC, new InternetAddress(email, false));
        message.setSubject(EMAIL_SUBJECT);
        message.setText("Hello " + firstName + ", \n \n Your new account password is: " + password + "\n \n The Support Team");
        message.setSentDate(new Date());
        message.saveChanges();
        return message;
    }

    private Session getEmailSession() {
        Properties props = System.getProperties();
        props.put(SMTP_HOST, GMAIL_SMTP_SERVER);
        props.put(SMTP_AUTH, true);
        props.put(SMTP_PORT, DEFAULT_PORT);
        props.put(SMTP_STARTTLS_ENABLE, true);
        props.put(SMTP_STARTTLS_REQUIRED, true);
        return Session.getInstance(props, null);
    }
}
