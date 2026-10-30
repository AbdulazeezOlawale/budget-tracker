package com.example.budgettracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.budgettracker.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
}
