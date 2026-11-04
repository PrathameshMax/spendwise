package com.spendwise.common.logging;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * PII masking in API responses: {@code @JsonSerialize(using = MaskedEmailSerializer.class)}
 * on a DTO field returns {@code a***@spendwise.dev} — used where a caller may see
 * other people's records (admin listings) but does not need their full email.
 */
public class MaskedEmailSerializer extends StdSerializer<String> {

    public MaskedEmailSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(PiiMasker.maskEmail(value));
    }
}
