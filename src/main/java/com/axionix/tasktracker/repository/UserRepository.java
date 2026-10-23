package com.axionix.tasktracker.repository;

import com.axionix.tasktracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Email must already be normalised (trimmed + lower-cased). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
