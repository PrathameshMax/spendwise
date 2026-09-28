package com.spendwise.authservice.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Serves this instance's public RSA key as a standard JWK Set at
 * {@code /oauth2/jwks} — the endpoint the API Gateway's
 * {@code NimbusReactiveJwtDecoder} fetches (and caches) to validate the access
 * tokens this service issues, without either side ever sharing the private key.
 */
@RestController
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
