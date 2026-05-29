package com.jvmd.authservice.security;

import com.jvmd.authservice.client.UserClient;
import com.jvmd.authservice.model.User;
import com.jvmd.authservice.model.UserDetailsImpl;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserClient userClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            User user = userClient.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return UserDetailsImpl.build(user);
        } catch (FeignException.NotFound e) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }
}
