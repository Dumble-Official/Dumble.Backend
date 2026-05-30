package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.event.OutboxWriter;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock SetOperations<String, String> setOperations;
    @Mock OutboxWriter outboxWriter;

    @InjectMocks AccountDeletionService service;

    private User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("gone@example.com");
        return u;
    }

    @Test
    void revokes_tokens_deletes_user_and_writes_outbox_event() {
        User u = user();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        service.deleteOwnAccount(u);

        verify(jwtService).deleteAllUserRefreshTokens(u);
        verify(setOperations).remove("banned_users", u.getId().toString());
        verify(userRepository).delete(u);
        verify(outboxWriter).write(eq("AccountDeleted"), eq("account.deleted"), any());
    }

    @Test
    void does_not_write_the_event_if_the_delete_fails() {
        User u = user();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        doThrow(new RuntimeException("db down")).when(userRepository).delete(u);

        assertThrows(RuntimeException.class, () -> service.deleteOwnAccount(u));

        // Transaction would roll back; no outbox row must have been written.
        verify(outboxWriter, never()).write(any(), any(), any());
    }
}
