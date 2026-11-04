package com.spendwise.common.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;
import org.springframework.boot.logging.logback.StructuredLogEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Console encoder that renders exactly what Spring Boot would — ECS/Logstash JSON
 * when {@code logging.structured.format.console} is set (the debugging lab), the
 * plain console pattern otherwise — and then passes every line through
 * {@link PiiMasker}. Emails, card/account numbers, bearer tokens and JWTs are
 * masked before Splunk/ELK ingest them, even after an accidental
 * {@code log.info("user {}", email)}. Replacements contain no quotes, so JSON
 * lines stay valid JSON.
 */
public class PiiMaskingEncoder extends EncoderBase<ILoggingEvent> {

    private String pattern;
    private String structuredFormat;
    private Charset charset = StandardCharsets.UTF_8;
    private Encoder<ILoggingEvent> delegate;

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setStructuredFormat(String structuredFormat) {
        this.structuredFormat = structuredFormat;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void start() {
        if (structuredFormat != null && !structuredFormat.isBlank()) {
            StructuredLogEncoder structured = new StructuredLogEncoder();
            structured.setContext(getContext());
            structured.setFormat(structuredFormat);
            structured.setCharset(charset);
            delegate = structured;
        } else {
            PatternLayoutEncoder plain = new PatternLayoutEncoder();
            plain.setContext(getContext());
            plain.setPattern(pattern);
            plain.setCharset(charset);
            delegate = plain;
        }
        delegate.start();
        super.start();
    }

    @Override
    public byte[] headerBytes() {
        return delegate.headerBytes();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] encoded = delegate.encode(event);
        if (encoded == null || encoded.length == 0) {
            return encoded;
        }
        return PiiMasker.mask(new String(encoded, charset)).getBytes(charset);
    }

    @Override
    public byte[] footerBytes() {
        return delegate.footerBytes();
    }

    @Override
    public void stop() {
        if (delegate != null) {
            delegate.stop();
        }
        super.stop();
    }
}
