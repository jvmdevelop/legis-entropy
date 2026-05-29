package com.jvmd.authservice.client;

import com.jvmd.authservice.model.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {

    @GetMapping("/exists-by-email")
    boolean existsByEmail(@RequestParam("email") String email);

    @GetMapping("/exists-by-username")
    boolean existsByUsername(@RequestParam("username") String username);

    @GetMapping("/find/username/{username}")
    Optional<User> findByUsername(@PathVariable("username") String username);

    @PostMapping
    User create(@RequestBody User user);
}
