package pl.krutkowski.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

import static pl.krutkowski.users.constant.FileConstant.USER_FOLDER;

@SpringBootApplication
public class UsersApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsersApplication.class, args);
        new File(USER_FOLDER).mkdirs();
    }

}
