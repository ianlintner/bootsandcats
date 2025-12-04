package com.bootsandcats.oauth2;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestOAuth2ClientConfiguration.class)
class OAuth2AuthorizationServerApplicationTests {

    @Test
    void contextLoads() {
        // Verify application context loads successfully
    }
}
