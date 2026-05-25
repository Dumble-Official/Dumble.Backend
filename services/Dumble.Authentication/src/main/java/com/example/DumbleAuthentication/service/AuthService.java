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
import org.springframework.dao.DataIntegrityViolationException;
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
            // Generic message: same response shape regardless of whether the
            // email is in use or merely fails some other validation. Prevents
            // attacker enumeration of registered accounts by submitting candidate
            // addresses and observing which path returns "already registered".
            throw new IllegalArgumentException("Registration could not be completed. Try a different email or sign in.");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.PARTICIPANT);
        user.setActive(true);
        user.setPfp("https://ui-avatars.com/api/?name=" + user.getFirstName() + "+" + user.getLastName());

        // The existsByEmail check above is not race-safe — two concurrent
        // registrations for the same email can both pass and both reach
        // save(), and one of them ends up colliding on the unique constraint.
        // Translate that to the same generic message as the existsByEmail
        // branch so the response shape stays identical (no enumeration
        // signal) and the client gets a clean 409 instead of a 500.
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Registration could not be completed. Try a different email or sign in.");
        }

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

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = jwtService.validateRefreshToken(refreshTokenValue);
        User user = refreshToken.getUser();

        // Defence-in-depth: /api/auth/refresh is in the gateway's PUBLIC_PATHS,
        // which means the BannedUserFilter does NOT run on this path. BanService
        // does delete refresh tokens at ban time, but a token landing in the
        // narrow window between DB-write and refresh-revoke would otherwise
        // mint a fresh access token. Re-check active status here.
        if (!user.isActive()) {
            jwtService.deleteRefreshToken(refreshTokenValue);
            throw new IllegalStateException("Account is deactivated");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails, user);

        // ROTATE the refresh token: mint a fresh one and the old one is
        // implicitly invalidated by JwtService.generateRefreshToken (it
        // deletes all prior tokens for this user under a pessimistic lock).
        // Without rotation, a stolen refresh token is usable for the full
        // 7-day window — and reuse is undetectable. With rotation, the next
        // call from the legitimate client either succeeds (and invalidates
        // the attacker's copy) or surfaces as a 403 "Invalid refresh token"
        // that ops can alert on as a possible compromise signal.
        RefreshToken rotated = jwtService.generateRefreshToken(user);

        return new AuthResponse(newAccessToken, rotated.getToken(), UserResponse.from(user));
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
            // SECURITY: refuse to silently link a LOCAL account to Google here.
            // Without this guard an attacker who knows email X exists locally can
            // submit a Google ID token for X (their own Google account proves
            // they CAN log in as that email at Google's end, but doesn't prove
            // they own the LOCAL X account at Dumble) and the service would link
            // the two — handing the attacker full access to X's profile, plan,
            // bookings, etc. The user must log in locally first and link from a
            // dedicated, authenticated endpoint.
            if (user.getAuthProvider() == AuthProvider.LOCAL) {
                throw new IllegalArgumentException(
                        "An account already exists for this email with a password. "
                                + "Please log in with your password first to link Google.");
            }
            // Existing GOOGLE-linked account: providerId must match what Google
            // told us. A mismatch means the inbound token belongs to a different
            // Google account that happens to share the same email — refuse,
            // don't silently re-link.
            if (!googleId.equals(user.getProviderId())) {
                throw new IllegalArgumentException(
                        "Google account does not match the one originally linked to this email.");
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
        } catch (Exception e) {
            // The Google client library throws bare IllegalArgumentException
            // (with no message) for malformed tokens via Guava Preconditions.
            // Rewrap unconditionally so we never bubble a null-message IAE up
            // to the global handler — keeps the response a clean 400 with a
            // useful message instead of leaking the inner null.
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new IllegalArgumentException("Invalid Google ID token: " + detail);
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user);
        RefreshToken refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), UserResponse.from(user));
    }
}
