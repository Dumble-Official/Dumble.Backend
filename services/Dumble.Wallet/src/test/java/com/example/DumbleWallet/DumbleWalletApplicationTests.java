package com.example.DumbleWallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DumbleWalletApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test — Spring Boot context starts cleanly.
    }
}
