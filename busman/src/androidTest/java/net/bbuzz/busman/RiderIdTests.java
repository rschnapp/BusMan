package net.bbuzz.busman;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.JsonReader;
import android.util.JsonWriter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class RiderIdTests {

    @Test
    public void testIdNameRoundTrips() {
        roundTripTest("test@domain.com 888-555-1212", "Joe The Tester");
        roundTripTest("this id has spaces", "WhatsHisFace (jim) Thatcher");
    }

    private void roundTripTest(String id, String name) {
        final ConfigureTagActivity.RiderId riderIdIn = new ConfigureTagActivity.RiderId(id, name);
        final StringWriter stringWriter = new StringWriter(256);
        final JsonWriter jsonWriter = new JsonWriter(stringWriter);
        try {
            riderIdIn.writeJson(jsonWriter);
        } catch (IOException e) {
            fail("conversion to json failed: " + e);
        }
        final String idNameString = stringWriter.toString();
        final StringReader stringReader = new StringReader(idNameString);
        final JsonReader jsonReader = new JsonReader(stringReader);
        final ConfigureTagActivity.RiderId riderIdOut = new ConfigureTagActivity.RiderId();
        try {
            riderIdOut.readJson(jsonReader);
        } catch (IOException e) {
            fail("conversion from json failed: " + e);
        }
        assertEquals("id mismatched", riderIdOut.id, id);
        assertEquals("name mismatched", riderIdOut.name, name);
    }
}
