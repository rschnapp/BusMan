package net.bbuzz.busman;

import static org.junit.Assert.assertEquals;

import android.test.suitebuilder.annotation.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class RiderMessagesTest {
    //TODO: Add message functionality tests
    private RiderMessages riderMessages;

    @Before
    public void setUp() {
        riderMessages = new RiderMessages();
    }

    @Test
    public void testReadJsonStream() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("TestMessages.json");

        riderMessages.parseJsonStream(inputStream);

        assertEquals(2, riderMessages.getWelcomeMessages().size());
        RiderMessages.WelcomeMessage welcomeMessage = riderMessages.getWelcomeMessages().get(0);
        assertEquals(".*Mar 17.*", welcomeMessage.timeRegexp);
        assertEquals("Happy Saint Patrick's Day to you, %s!", welcomeMessage.message);
        assertEquals(25, welcomeMessage.weight);

        welcomeMessage = riderMessages.getWelcomeMessages().get(1);
        assertEquals("", welcomeMessage.timeRegexp);
        assertEquals("Hi %s", welcomeMessage.message);
        assertEquals(RiderMessages.DEFAULT_WEIGHT, welcomeMessage.weight);

        assertEquals(2, riderMessages.getReturnMessages().size());
        RiderMessages.ReturnMessage returnMessage = riderMessages.getReturnMessages().get(0);
        assertEquals("garth.*", returnMessage.idRegexp);
        assertEquals(".*Apr 01.*", returnMessage.timeRegexp);
        assertEquals("Yo %s! Rock on home, dude!", returnMessage.message);
        assertEquals(1000000, returnMessage.weight);

        returnMessage = riderMessages.getReturnMessages().get(1);
        assertEquals("", returnMessage.idRegexp);
        assertEquals("", returnMessage.timeRegexp);
        assertEquals("heading home %s", returnMessage.message);
        assertEquals(RiderMessages.DEFAULT_WEIGHT, returnMessage.weight);

        assertEquals(3, riderMessages.getGoMessages().size());
        RiderMessages.GoMessage goMessage = riderMessages.getGoMessages().get(0);
        assertEquals(".*Apr 01.*", goMessage.timeRegexp);
        assertEquals("I hope you all enjoyed April Fools Day. Now, let's go home!", goMessage.message);
        assertEquals(10000, goMessage.weight);

        goMessage = riderMessages.getGoMessages().get(1);
        assertEquals(".*May 04.*", goMessage.timeRegexp);
        assertEquals("Remember,,, the Force will be with you, always. Now, let's make the jump to light speed", goMessage.message);
        assertEquals(RiderMessages.DEFAULT_WEIGHT, goMessage.weight);

        goMessage = riderMessages.getGoMessages().get(2);
        assertEquals("", goMessage.timeRegexp);
        assertEquals("Hooray! We're all aboard. Time to go!", goMessage.message);
        assertEquals(RiderMessages.DEFAULT_WEIGHT, goMessage.weight);
    }
}