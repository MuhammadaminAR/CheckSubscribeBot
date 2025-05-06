package org.example.checksubscribebot.repos;

import org.example.checksubscribebot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}