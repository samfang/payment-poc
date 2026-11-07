package com.example.paymentpoc.payment.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

/** Rejects any JSON token that is not a plain integer (VALUE_NUMBER_INT). */
public class AmountDeserializer extends StdDeserializer<Long> {

    public AmountDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.VALUE_NUMBER_INT) {
            throw InvalidFormatException.from(p,
                    "amount must be an integer (no decimal point, no quotes)",
                    p.getText(), Long.class);
        }
        return p.getLongValue();
    }
}
