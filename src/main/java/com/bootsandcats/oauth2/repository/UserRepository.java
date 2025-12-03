package com.bootsandcats.oauth2.repository;

import com.bootsandcats.oauth2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    Optional<User> findByUsername(String username);
}
