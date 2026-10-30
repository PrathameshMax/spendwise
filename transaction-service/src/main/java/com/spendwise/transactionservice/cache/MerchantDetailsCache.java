package com.spendwise.transactionservice.cache;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.transactionservice.api.MerchantDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * CR-2: cache-aside store for MerchantDirectory details in Redis.
 *
 * <p>One key per merchant code ({@code merchant-directory:v1:<CODE>}), JSON value,
 * fixed TTL. The cache is optional: any Redis or serialization failure is logged
 * and treated as a miss (on read) or skipped (on write), never as an error for the
 * statement request.
 */

@Component
public class MerchantDetailsCache {

    private static final Logger log = LoggerFactory.getLogger(MerchantDetailsCache.class);
    private static final String KEY_PREFIX = "merchant-directory:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public MerchantDetailsCache(StringRedisTemplate redis, ObjectMapper objectMapper, @Value("${spendwise.merchant-directory.cache-ttl:24h}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /**
     * Reads all given merchant codes in one round trip (MGET).
     *
     * @return cached details keyed by merchant code; empty on any failure
     */
    public Map<String, MerchantDetails> getAll(Collection<String> merchantCodes) {
        if (merchantCodes.isEmpty()){
            return Map.of();
        }

        List<String> codes = List.copyOf(merchantCodes);
        List<String> keys = merchantCodes.stream().map(key -> KEY_PREFIX + key).toList();

        try{
            List<String> values = redis.opsForValue().multiGet(keys);
            if (values == null){
                return Map.of();
            }
        Map<String, MerchantDetails> hits = new HashMap<>();
            for (int i = 0; i < codes.size(); i++) {
                String json = values.get(i);
                if (json != null) {
                    hits.put(codes.get(i), objectMapper.readValue(json, MerchantDetails.class));
                }
            }
            return hits;

        }catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Merchant cache read failed for {} merchants, treating as cache miss: {}",
                    codes.size(), ex.toString());
            return Map.of();
        }
    }

    /**
     * Stores details with the configured TTL. Failures are logged and skipped.
     */
    public void putAll(Map<String, MerchantDetails> merchants) {
        if (merchants.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, MerchantDetails> entry : merchants.entrySet()) {
                redis.opsForValue().set(KEY_PREFIX + entry.getKey(),
                        objectMapper.writeValueAsString(entry.getValue()), ttl);
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Merchant cache write failed for {} merchants, continuing without caching: {}",
                    merchants.size(), ex.toString());
        }
    }



}
