package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.AuthProvider;
import com.example.DumbleAuthentication.domain.RefreshToken;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;
import com.example.DumbleAuthentication.dto.request.ChangePasswordRequest;
import com.example.DumbleAuthentication.dto.request.GoogleAuthRequest;
import com.example.DumbleAuthentication.dto.request.LoginRequest;
import com.example.DumbleAuthentication.dto.request.RegisterRequest;
import com.example.DumbleAuthentication.dto.response.AuthResponse;
import com.example.DumbleAuthentication.dto.response.UserResponse;
import com.example.DumbleAuthentication.repository.UserRepository;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Value("${google.client-id}")
    private String googleClientId;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Issues a 60s token for SignalR WebSocket negotiation. Caller must already
     * be authenticated (regular access token in Authorization header).
     */
    public String issueHubToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (!user.isActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        return jwtService.generateHubToken(userDetails, user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.PARTICIPANT);
        user.setActive(true);
        user.setPfp("https://ui-avatars.com/api/?name=" + user.getFirstName() + "+" + user.getLastName());

        user = userRepository.save(user);

        return generateAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Authenticate credentials via Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        return generateAuthResponse(user);
    }

    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = jwtService.validateRefreshToken(refreshTokenValue);
        User user = refreshToken.getUser();

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails, user);

        return new AuthResponse(newAccessToken, refreshTokenValue, UserResponse.from(user));
    }

    @Transactional
    public void logout(String refreshTokenValue, String email) {
        RefreshToken refreshToken = jwtService.validateRefreshToken(refreshTokenValue);

        if (!refreshToken.getUser().getEmail().equals(email)) {
            throw new IllegalArgumentException("Refresh token does not belong to the authenticated user");
        }

        jwtService.deleteRefreshToken(refreshTokenValue);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getAuthProvider() == AuthProvider.GOOGLE && user.getPasswordHash() == null) {
            throw new IllegalStateException("Cannot change password for Google-authenticated accounts");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all refresh tokens for security
        jwtService.deleteAllUserRefreshTokens(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyGoogleToken(request.getIdToken());
        String email = payload.getEmail();
        String googleId = payload.getSubject();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String picture = (String) payload.get("picture");

        Optional<User> existingUser = userRepository.findByEmail(email);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Link LOCAL account to Google if not already linked
            if (user.getAuthProvider() == AuthProvider.LOCAL) {
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setProviderId(googleId);
                if (picture != null && user.getPfp() == null) {
                    user.setPfp(picture);
                }
                user = userRepository.save(user);
            }
            if (!user.isActive()) {
                throw new IllegalStateException("Account is deactivated");
            }
        } else {
            // Register new Google user
            user = new User();
            user.setEmail(email);
            user.setFirstName(firstName != null ? firstName : "");
            user.setLastName(lastName != null ? lastName : "");
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setProviderId(googleId);
            user.setUserType(UserType.PARTICIPANT);
            user.setActive(true);
            if (picture != null) {
                user.setPfp(picture);
            } else {
                user.setPfp("https://ui-avatars.com/api/?name=" + user.getFirstName() + "+" + user.getLastName());
            }
            user = userRepository.save(user);
        }

        return generateAuthResponse(user);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            return idToken.getPayload();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to verify Google ID token: " + e.getMessage());
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user);
        RefreshToken refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), UserResponse.from(user));
    }
}
