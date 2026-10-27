package com.famille.gestioncompte.security;

import com.famille.gestioncompte.config.AppProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppProperties appProperties;
    private final String encodedPassword;

    public UserDetailsServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
        String pwd = appProperties.getPassword();
        // Si c'est déjà un hash BCrypt on l'utilise tel quel, sinon on le hashe
        this.encodedPassword = pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$")
                ? pwd
                : new BCryptPasswordEncoder().encode(pwd);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!appProperties.getUsername().equals(username)) {
            throw new UsernameNotFoundException("Utilisateur inconnu : " + username);
        }
        return User.builder()
                .username(appProperties.getUsername())
                .password(encodedPassword)
                .roles("USER")
                .build();
    }
}
