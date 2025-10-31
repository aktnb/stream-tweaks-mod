package org.etwas.streamtweaks.utils;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class GsonUtils {
    public static GsonBuilder getBuilder() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
                    @Override
                    public void write(JsonWriter out, Instant value) throws IOException {
                        out.value(value == null ? null : value.toString());
                    }

                    @Override
                    public Instant read(JsonReader in) throws IOException {
                        String str = in.nextString();
                        return str == null ? null : Instant.parse(str);
                    }
                });
    }
}
