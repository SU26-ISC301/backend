package com.su26isc301.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private static final Duration JWKS_CACHE_DURATION = Duration.ofMinutes(10);

    private final SecretKey legacySigningKey;
    private final URI jwksUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile Map<String, PublicKey> cachedPublicKeys = Map.of();
    private volatile Instant publicKeysExpireAt = Instant.EPOCH;

    @Autowired
    public JwtService(
            @Value("${supabase.jwt.secret}") String jwtSecret,
            @Value("${supabase.url}") String supabaseUrl
    ) {
        this(
                jwtSecret,
                URI.create(supabaseUrl + "/auth/v1/.well-known/jwks.json"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper()
        );
    }

    JwtService(String jwtSecret, URI jwksUri, HttpClient httpClient, ObjectMapper objectMapper) {
        this.legacySigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwksUri = jwksUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extractClaims(String token) {
        try {
            Map<String, Object> header = readJwtPart(token, 0);
            String algorithm = (String) header.get("alg");

            if ("HS256".equals(algorithm)) {
                return parseClaims(token, legacySigningKey);
            }

            if ("ES256".equals(algorithm) || "RS256".equals(algorithm)) {
                String keyId = (String) header.get("kid");
                if (keyId == null || keyId.isBlank()) {
                    throw new IllegalArgumentException("JWT kid is missing");
                }
                return parseClaims(token, getPublicKey(keyId));
            }

            throw new IllegalArgumentException("Unsupported JWT algorithm");
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    private Claims parseClaims(String token, SecretKey signingKey) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims parseClaims(String token, PublicKey publicKey) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private PublicKey getPublicKey(String keyId) {
        PublicKey publicKey = getCachedPublicKey(keyId);
        if (publicKey != null) {
            return publicKey;
        }

        refreshPublicKeys();
        publicKey = cachedPublicKeys.get(keyId);
        if (publicKey == null) {
            throw new IllegalArgumentException("JWT signing key not found");
        }
        return publicKey;
    }

    private PublicKey getCachedPublicKey(String keyId) {
        if (Instant.now().isAfter(publicKeysExpireAt)) {
            return null;
        }
        return cachedPublicKeys.get(keyId);
    }

    @SuppressWarnings("unchecked")
    private synchronized void refreshPublicKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unable to load Supabase JWKS");
            }

            Map<String, Object> jwks = objectMapper.readValue(
                    response.body(),
                    new TypeReference<>() {
                    }
            );
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.getOrDefault("keys", List.of());
            Map<String, PublicKey> nextPublicKeys = new HashMap<>();
            for (Map<String, Object> key : keys) {
                String keyId = (String) key.get("kid");
                if (keyId != null) {
                    nextPublicKeys.put(keyId, createPublicKey(key));
                }
            }
            cachedPublicKeys = Map.copyOf(nextPublicKeys);
            publicKeysExpireAt = Instant.now().plus(JWKS_CACHE_DURATION);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load Supabase JWKS", e);
        }
    }

    private PublicKey createPublicKey(Map<String, Object> jwk) throws Exception {
        return switch ((String) jwk.get("kty")) {
            case "EC" -> createEcPublicKey(jwk);
            case "RSA" -> createRsaPublicKey(jwk);
            default -> throw new IllegalArgumentException("Unsupported JWKS key type");
        };
    }

    private PublicKey createEcPublicKey(Map<String, Object> jwk) throws Exception {
        if (!"P-256".equals(jwk.get("crv"))) {
            throw new IllegalArgumentException("Unsupported EC curve");
        }

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(
                decodeUnsignedInteger((String) jwk.get("x")),
                decodeUnsignedInteger((String) jwk.get("y"))
        );
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, parameterSpec));
    }

    private PublicKey createRsaPublicKey(Map<String, Object> jwk) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                decodeUnsignedInteger((String) jwk.get("n")),
                decodeUnsignedInteger((String) jwk.get("e"))
        ));
    }

    private BigInteger decodeUnsignedInteger(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }

    private Map<String, Object> readJwtPart(String token, int index) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT structure");
            }
            byte[] decodedPart = Base64.getUrlDecoder().decode(parts[index]);
            return objectMapper.readValue(decodedPart, new TypeReference<>() {
            });
        } catch (Exception decodeException) {
            throw new IllegalArgumentException("Invalid JWT structure", decodeException);
        }
    }

    public String extractUserId(String token) {
        return (String) extractClaims(token).get("sub");
    }

    public String extractEmail(String token) {
        return (String) extractClaims(token).get("email");
    }

    public String extractRole(String token) {
        return (String) extractClaims(token).get("role");
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
