package com.su26isc301.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final String KEY_ID = "test-key";

    private HttpServer jwksServer;
    private JwtService jwtService;
    private KeyPair ecKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        ecKeyPair = keyPairGenerator.generateKeyPair();

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] response = createJwks().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        jwksServer.start();

        jwtService = new JwtService(
                JWT_SECRET,
                URI.create("http://localhost:" + jwksServer.getAddress().getPort() + "/jwks"),
                HttpClient.newHttpClient(),
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
    }

    @Test
    void acceptsValidLegacySignedToken() {
        String token = createLegacyToken(JWT_SECRET, Instant.now().plusSeconds(60));

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("vendor@example.com", jwtService.extractEmail(token));
        assertEquals("profile-id", jwtService.extractUserId(token));
    }

    @Test
    void acceptsValidAsymmetricSignedToken() {
        String token = createAsymmetricToken(KEY_ID, Instant.now().plusSeconds(60));

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("vendor@example.com", jwtService.extractEmail(token));
    }

    @Test
    void rejectsLegacyTokenWithInvalidSignature() {
        String token = createLegacyToken("different-secret-that-is-at-least-32-bytes", Instant.now().plusSeconds(60));

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsExpiredToken() {
        String token = createLegacyToken(JWT_SECRET, Instant.now().minusSeconds(60));

        assertFalse(jwtService.isTokenValid(token));
    }

    private String createLegacyToken(String secret, Instant expiration) {
        return Jwts.builder()
                .subject("profile-id")
                .claim("email", "vendor@example.com")
                .expiration(Date.from(expiration))
                .signWith(signingKey(secret))
                .compact();
    }

    private String createAsymmetricToken(String keyId, Instant expiration) {
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject("profile-id")
                .claim("email", "vendor@example.com")
                .expiration(Date.from(expiration))
                .signWith(ecKeyPair.getPrivate())
                .compact();
    }

    private String createJwks() {
        ECPublicKey publicKey = (ECPublicKey) ecKeyPair.getPublic();
        return """
                {"keys":[{"kty":"EC","kid":"%s","crv":"P-256","x":"%s","y":"%s"}]}
                """.formatted(
                KEY_ID,
                encodeCoordinate(publicKey.getW().getAffineX().toByteArray()),
                encodeCoordinate(publicKey.getW().getAffineY().toByteArray())
        );
    }

    private String encodeCoordinate(byte[] value) {
        byte[] unsignedValue = value.length > 32 ? Arrays.copyOfRange(value, value.length - 32, value.length) : value;
        byte[] paddedValue = new byte[32];
        System.arraycopy(unsignedValue, 0, paddedValue, paddedValue.length - unsignedValue.length, unsignedValue.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(paddedValue);
    }

    private SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
