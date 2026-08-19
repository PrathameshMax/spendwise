package com.prathmesh.spendwise.userservice.repository;

import com.prathmesh.spendwise.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
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

    @Test
    void searchUsers_shouldFindByFirstName() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("Prathamesh", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(
                "Prathamesh",
                result.getContent().get(0).getFirstName()
        );
    }

    @Test
    void searchUsers_shouldFindByLastName() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("Padavekar", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(
                "Padavekar",
                result.getContent().get(0).getLastName()
        );
    }

    @Test
    void searchUsers_shouldFindByEmail() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("gmail.com", pageable);

        assertTrue(result.getTotalElements() > 0);

        assertTrue(
                result.getContent()
                        .stream()
                        .allMatch(user ->
                                user.getEmail()
                                        .toLowerCase()
                                        .contains("gmail.com"))
        );
    }

    @Test
    void searchUsers_shouldBeCaseInsensitive() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("PRATHAMESH", pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchUsers_shouldSupportPartialMatch() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("Prath", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(
                "Prathamesh",
                result.getContent().get(0).getFirstName()
        );
    }

    @Test
    void searchUsers_shouldReturnEmptyPageWhenNoUserMatches() {

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers("xyz123notfound", pageable);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void searchUsers_shouldApplyPagination() {

        Pageable pageable = PageRequest.of(0, 1);

        Page<User> result =
                userRepository.searchUsers("gmail", pageable);

        assertEquals(1, result.getContent().size());
        assertTrue(result.getTotalElements() >= 1);
    }

    @Test
    void searchUsers_shouldApplyFilteringPaginationAndSorting() {

        Pageable pageable = PageRequest.of(
                0,
                2,
                Sort.by("createdAt").descending()
        );

        Page<User> result =
                userRepository.searchUsers("gmail", pageable);

        assertNotNull(result);

        assertEquals(0, result.getNumber());
        assertEquals(2, result.getSize());

        assertTrue(result.getContent().size() <= 2);
        assertTrue(result.getTotalElements() > 0);

        List<User> users = result.getContent();

        for (int i = 0; i < users.size() - 1; i++) {
            assertTrue(
                    !users.get(i).getCreatedAt()
                            .isBefore(users.get(i + 1).getCreatedAt())
            );
        }
    }
}
