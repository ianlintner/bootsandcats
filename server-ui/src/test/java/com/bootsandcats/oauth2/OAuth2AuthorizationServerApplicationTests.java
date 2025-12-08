package com.bootsandcats.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;
import com.bootsandcats.oauth2.config.TestObjectMapperConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class, TestObjectMapperConfig.class})
class OAuth2AuthorizationServerApplicationTests {

    @Test
    void contextLoads() {
        // Verify application context loads successfully
    }
}
