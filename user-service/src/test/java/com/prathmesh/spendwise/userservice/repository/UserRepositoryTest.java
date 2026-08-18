package com.prathmesh.spendwise.userservice.repository;

import com.prathmesh.spendwise.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;


    @Test
    void save_shouldPersistUser() {

        User user = new User();

        user.setFirstName("Prathamesh");
        user.setLastName("Padavekar");
        user.setEmail("repository@gmail.com");
        user.setPhone("9876543210");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("Prathamesh", savedUser.getFirstName());
        assertEquals("repository@gmail.com", savedUser.getEmail());
    }


    @Test
    void findById_shouldReturnUser() {

        User user = new User();

        user.setFirstName("Prathamesh");
        user.setLastName("Padavekar");
        user.setEmail("findbyid@gmail.com");
        user.setPhone("9876543210");

        User savedUser = userRepository.save(user);

        Optional<User> result =
                userRepository.findById(savedUser.getId());

        assertTrue(result.isPresent());
        assertEquals(
                "findbyid@gmail.com",
                result.get().getEmail()
        );
    }


    @Test
    void findById_shouldReturnEmptyWhenUserDoesNotExist() {

        Optional<User> result =
                userRepository.findById(99999L);

        assertTrue(result.isEmpty());
    }


    @Test
    void delete_shouldRemoveUser() {

        User user = new User();

        user.setFirstName("Delete");
        user.setLastName("Test");
        user.setEmail("delete@gmail.com");
        user.setPhone("9876543210");

        User savedUser = userRepository.save(user);

        userRepository.delete(savedUser);

        Optional<User> result =
                userRepository.findById(savedUser.getId());

        assertTrue(result.isEmpty());
    }
}
