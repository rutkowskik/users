package pl.krutkowski.users.exception.domain;

public class TokenReusedException extends Exception {
    public TokenReusedException(String message) {
        super(message);
    }
}