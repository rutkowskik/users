//package redis;
//
//import net.bytebuddy.utility.dispatcher.JavaDispatcher;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import pl.krutkowski.users.domain.UserPrinciple;
//import pl.krutkowski.users.service.RedisTokenService;
//import pl.krutkowski.users.service.TokenService;
//
//@SpringBootTest
//@AutoConfigureTestDatabase
//@Testcontainers
//class TokenServiceIntegrationTest {
//
//    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
//            .withExposedPorts(6379);
//
//    @DynamicPropertySource
//    static void redisProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.redis.host", redis::getHost);
//        registry.add("spring.redis.port", redis::getFirstMappedPort);
//    }
//
//    @Autowired
//    private TokenService tokenService;
//
//    @Autowired
//    private RedisTokenService redisTokenService;
//
//    @Test
//    void shouldSaveAndRetrieveToken() {
//        // Given
//        UserPrinciple user = createTestUser();
//
//        // When
//        tokenService.login(user, response, request);
//
//        // Then
//        String refreshToken = extractRefreshTokenFromResponse(response);
//        assertTrue(redisTokenService.isTokenValid(refreshToken));
//    }
//
//    @Test
//    void shouldRevokeAllUserTokens() {
//        // Given
//        UserPrinciple user = createTestUser();
//        tokenService.login(user, response1, request1);
//        tokenService.login(user, response2, request2);
//
//        // When
//        redisTokenService.revokeAllUserTokens(user.getUsername());
//
//        // Then
//        List<RefreshTokenData> sessions = redisTokenService.getUserActiveSessions(user.getUsername());
//        assertEquals(0, sessions.size());
//    }
//
//    @Test
//    void shouldDetectTokenReuse() {
//        // Given
//        UserPrinciple user = createTestUser();
//        tokenService.login(user, response, request);
//        String refreshToken = extractRefreshTokenFromResponse(response);
//
//        // First refresh - OK
//        tokenService.refresh(refreshToken, request, response);
//
//        // Second refresh with same token - should throw
//        assertThrows(TokenReusedException.class, () -> {
//            tokenService.refresh(refreshToken, request, response);
//        });
//    }
//}