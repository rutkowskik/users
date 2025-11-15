package pl.krutkowski.users.service;

import javax.mail.MessagingException;

public interface EmailService {

    void sendNewPasswordEmail(String firstName, String email, String password) throws MessagingException;
}
