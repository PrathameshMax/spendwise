package com.prathmesh.spendwise.userservice.repository;

import com.prathmesh.spendwise.userservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("""
        SELECT u
        FROM User u
        WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
        """)
    Page<User> searchUsers(
            @Param("search") String search,
            Pageable pageable
    );
}
