package com.cognizant.stablecoins.payment.settlement;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic persisted/approval material, independent of application mapper settings and JSONB key order. */
public final class SettlementExecutionClaimJson {
    private SettlementExecutionClaimJson() {}
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    static {
        MAPPER.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    public static String write(Object value) {
        requireType(value == null ? null : value.getClass());
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("INVALID_SETTLEMENT_BINDING"); }
    }

    public static <T> T read(String document, Class<T> type) {
        requireType(type);
        if (document == null || document.length() > 32768)
            throw new IllegalArgumentException("INVALID_SETTLEMENT_BINDING");
        try { T value = MAPPER.readValue(document, type); if (value == null) throw new IllegalArgumentException(); return value; }
        catch (Exception error) { throw new IllegalArgumentException("INVALID_SETTLEMENT_BINDING"); }
    }

    public static String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(write(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static void requireType(Class<?> type) {
        if (type != SettlementExecutionClaim.class)
            throw new IllegalArgumentException("UNSUPPORTED_SETTLEMENT_CLAIM_TYPE");
    }
}
