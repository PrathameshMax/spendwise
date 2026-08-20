package com.prathmesh.spendwise.userservice.repository;

import com.prathmesh.spendwise.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    void save_shouldRejectDuplicateEmail() {

        String duplicateEmail =
                "duplicate-" + UUID.randomUUID() + "@gmail.com";

        User user1 = new User();
        user1.setFirstName("Prathamesh");
        user1.setLastName("Padavekar");
        user1.setEmail(duplicateEmail);
        user1.setPhone("9876543210");

        userRepository.saveAndFlush(user1);

        User user2 = new User();
        user2.setFirstName("Dipti");
        user2.setLastName("Malave");
        user2.setEmail(duplicateEmail);
        user2.setPhone("9234567890");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user2)
        );
    }
}
