package net.bbuzz.busman;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;

public abstract class JsonSerializable {
    final static String FIELD_COMMENT = "comment";

    public abstract void writeJson(JsonWriter writer) throws IOException;

    public abstract void readJson(JsonReader reader) throws IOException;

    void writeValue(JsonWriter writer, String name, String value, String defaultValue)
            throws IOException {
        if (!value.equals(defaultValue)) {
            writer.name(name).value(value);
        }
    }

    void writeValue(JsonWriter writer, String name, int value, int defaultValue)
            throws IOException {
        if (value != defaultValue) {
            writer.name(name).value(value);
        }
    }
}