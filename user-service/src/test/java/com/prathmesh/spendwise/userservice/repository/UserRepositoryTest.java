package com.prathmesh.spendwise.userservice.repository;

import com.prathmesh.spendwise.userservice.entity.User;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // --------------------------------------------------
    // BASIC CRUD
    // --------------------------------------------------

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

    // --------------------------------------------------
    // DUPLICATE EMAIL
    // --------------------------------------------------

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

    // --------------------------------------------------
    // SEARCH
    // --------------------------------------------------

    @Test
    void searchUsers_shouldFindByFirstName() {

        createUser(
                "Prathamesh",
                "Padavekar",
                "firstname@gmail.com",
                "9876543210"
        );

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers(
                        "Prathamesh",
                        pageable
                );

        assertEquals(1, result.getTotalElements());
        assertEquals(
                "Prathamesh",
                result.getContent().get(0).getFirstName()
        );
    }

    @Test
    void searchUsers_shouldFindByLastName() {

        createUser(
                "Prathamesh",
                "Padavekar",
                "lastname@gmail.com",
                "9876543210"
        );

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers(
                        "Padavekar",
                        pageable
                );

        assertEquals(1, result.getTotalElements());
        assertEquals(
                "Padavekar",
                result.getContent().get(0).getLastName()
        );
    }

    @Test
    void searchUsers_shouldFindByEmail() {

        createUser(
                "Prathamesh",
                "Padavekar",
                "search-email@gmail.com",
                "9876543210"
        );

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers(
                        "gmail.com",
                        pageable
                );

        assertEquals(1, result.getTotalElements());

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

        createUser(
                "Prathamesh",
                "Padavekar",
                "case-insensitive@gmail.com",
                "9876543210"
        );

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers(
                        "PRATHAMESH",
                        pageable
                );

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchUsers_shouldSupportPartialMatch() {

        createUser(
                "Prathamesh",
                "Padavekar",
                "partial@gmail.com",
                "9876543210"
        );

        Pageable pageable = PageRequest.of(0, 10);

        Page<User> result =
                userRepository.searchUsers(
                        "Prath",
                        pageable
                );

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
                userRepository.searchUsers(
                        "xyz123notfound",
                        pageable
                );

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    // --------------------------------------------------
    // PAGINATION
    // --------------------------------------------------

    @Test
    void searchUsers_shouldApplyPagination() {

        createUser(
                "UserOne",
                "Test",
                "pagination1@gmail.com",
                "9876543210"
        );

        createUser(
                "UserTwo",
                "Test",
                "pagination2@gmail.com",
                "9876543211"
        );

        createUser(
                "UserThree",
                "Test",
                "pagination3@gmail.com",
                "9876543212"
        );

        Pageable pageable = PageRequest.of(0, 1);

        Page<User> result =
                userRepository.searchUsers(
                        "gmail",
                        pageable
                );

        assertEquals(1, result.getContent().size());
        assertEquals(3, result.getTotalElements());
    }

    // --------------------------------------------------
    // FILTERING + PAGINATION + SORTING
    // --------------------------------------------------

    @Test
    void searchUsers_shouldApplyFilteringPaginationAndSorting() {

        createUser(
                "Older",
                "User",
                "sorting1@gmail.com",
                "9876543210"
        );

        createUser(
                "Newer",
                "User",
                "sorting2@gmail.com",
                "9876543211"
        );

        Pageable pageable = PageRequest.of(
                0,
                2,
                Sort.by("createdAt").descending()
        );

        Page<User> result =
                userRepository.searchUsers(
                        "gmail",
                        pageable
                );

        assertNotNull(result);

        assertEquals(0, result.getNumber());
        assertEquals(2, result.getSize());

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        List<User> users = result.getContent();

        for (int i = 0; i < users.size() - 1; i++) {
            assertTrue(
                    !users.get(i)
                            .getCreatedAt()
                            .isBefore(
                                    users.get(i + 1).getCreatedAt()
                            )
            );
        }
    }

    // --------------------------------------------------
    // TEST DATA HELPER
    // --------------------------------------------------

    private User createUser(
            String firstName,
            String lastName,
            String email,
            String phone) {

        User user = new User();

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);

        return userRepository.saveAndFlush(user);
    }

}