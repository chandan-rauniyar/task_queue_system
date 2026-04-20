package com.taskqueue.repository;

import com.taskqueue.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Used by AdminController to get the default owner when creating a company
    Optional<User> findByRole(User.Role role);
}