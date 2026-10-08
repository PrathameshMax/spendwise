package com.spendwise.events.avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 22 — the compatibility rules the Schema Registry enforces
 * ({@code FULL_TRANSITIVE}), checked at build time with Avro's own resolver,
 * the same check the registry runs. Two directions:
 * <ul>
 *   <li><b>backward</b> — the new schema, as reader, can read data written
 *   with the old one (new consumers, old events: replay from offset 0);</li>
 *   <li><b>forward</b> — the old schema, as reader, can read data written with
 *   the new one (old consumers still running when the new producer ships).</li>
 * </ul>
 * FULL requires both. The candidates live in src/test/resources/schema-evolution
 * and are the same schemas the Milestone 22 Postman plan submits to the registry.
 */
class SchemaEvolutionTest {

    private static final Schema V1 = TransactionCreatedEvent.getClassSchema();

    @Test
    void removingRequiredFieldIsBackwardCompatibleButBreaksOldReaders() throws IOException {
        Schema breaking = load("TransactionCreatedEvent-v2-breaking.avsc");

        assertThat(V1.getField("currency")).isNotNull();
        assertThat(breaking.getField("currency")).isNull();
        // backward: the new reader simply ignores the field old data still carries
        assertThat(compatibility(breaking, V1)).isEqualTo(SchemaCompatibilityType.COMPATIBLE);
        // forward: an old reader requires currency, has no default for it, and new data lacks it
        assertThat(compatibility(V1, breaking)).isEqualTo(SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    void addingOptionalFieldWithDefaultIsFullyCompatible() throws IOException {
        Schema additive = load("TransactionCreatedEvent-v2-additive.avsc");

        assertThat(additive.getField("baseCurrency").hasDefaultValue()).isTrue();
        assertThat(compatibility(additive, V1)).isEqualTo(SchemaCompatibilityType.COMPATIBLE);
        assertThat(compatibility(V1, additive)).isEqualTo(SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    void everyContractHasDocumentedFieldsAndANamespace() {
        for (Schema schema : new Schema[] {TransactionCreatedEvent.getClassSchema(),
                TransactionReversedEvent.getClassSchema(), TransactionRejectedEvent.getClassSchema()}) {
            assertThat(schema.getNamespace()).isEqualTo("com.spendwise.events.avro");
            assertThat(schema.getFields()).allSatisfy(field -> assertThat(field.doc()).isNotBlank());
        }
    }

    private static SchemaCompatibilityType compatibility(Schema reader, Schema writer) {
        return SchemaCompatibility.checkReaderWriterCompatibility(reader, writer).getType();
    }

    private static Schema load(String name) throws IOException {
        try (InputStream in = SchemaEvolutionTest.class.getResourceAsStream("/schema-evolution/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new Schema.Parser().parse(in);
        }
    }
}
