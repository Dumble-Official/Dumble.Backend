package com.example.DumblePayment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DumblePaymentApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test — Spring context starts cleanly with the test profile.
    }
}
