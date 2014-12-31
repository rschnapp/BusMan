package net.bbuzz.busman;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class RiderMessages {

    private final static String TAG =  "RiderMessages";

    public final static String MESSAGE_FILE_NAME = "BusManMessages";

    public final static File DOWNLOAD_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    public final static String DOWNLOAD_MESSAGE_FILE = new File(DOWNLOAD_DIR, MESSAGE_FILE_NAME)
            .getAbsolutePath();

    public final static File DATA_DIR =
            new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/BusMan");
    public final static String MESSAGE_FILE =
            new File(DATA_DIR, MESSAGE_FILE_NAME).getAbsolutePath();
    public final static String MESSAGE_FILE_OLD =
            new File(DATA_DIR, MESSAGE_FILE_NAME + ".old").getAbsolutePath();

    public final static int DEFAULT_WEIGHT = 10;
    // SimpleDateFormat e.g., "Sun, Mar 23, 2014 14:07"
    public final static SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM dd, yyyy HH:mm");
    private static final Random sRandom = new Random();

    /**
     * Strings to announce the arrival of a rider upon being added to the manifest
     */
    private static class WelcomeMessage {
        public String idMatch;
        public String timeRegexp;
        public String message;
        public int weight;
    }

    /**
     * Strings to announce the arrival of a rider upon being removed from the manifest
     */
    private static class ReturnMessage {
        public String idRegexp;
        public String timeRegexp;
        public String message;
        public String isLast;
        public int weight;
    }

    /**
     * Strings to announce the depletion of the manifest and the impending departure
     */
    private static class GoMessage {
        public String timeRegexp;
        public String message;
        public int weight;
    }

    private static List<WelcomeMessage> sWelcomeMessages;
    private static List<ReturnMessage> sReturnMessages;
    private static List<GoMessage> sGoMessages;

    /*
     * The BusManMessages file is a list of lines in one of three forms:
     *  w/timeRegexp/idRegexp/message/weight - a welcome message
     *  r/timeRegexp/idRegexp/message/isLast/weight - a "returning" message
     *  g/timeRegexp/message/weight - a "go" message
     *
     *  The first field disambiguates among the types of messages (w is welcome, etc.)
     *  timeRegexp is a regular expression to match against a time/date string in the default
     *  locale, of the form
     *          "Sun, Mar 23, 2014 14:07"
     *          example: ex: ".*Apr 01.*" matches April first
     *          (empty matches all)
     *  idRegexp is a regular expression to match against a rider id, or empty to match all riders
     *  message is the string, which can contain:
     *          "%s" will be replaced with the rider's name
     *          "!locale(language)" can appear anywhere in the message. It will be deleted from the
     *              message, and the language (an ISO locale spec) will be passed to the TTS engine,
     *              e.g., "!locale(en_GB)"
     *  isLast is either "t", signifying that this matches a returning rider who is the last rider;
     *          "f" signifies someone who is NOT the last rider; and empty matches all
     *  weight is the selection weight. Among all matching messages, a message's weight divided by
     *          the sum of all matching messages' weights represents the probability that a given
     *          message will be selected. If weight is empty, it defaults to a value of 10.
     *
     *  NOTE: empty lines, and lines beginning with "#" are ignored
     */

    static long sMessageFileLastModified = 0L;

    static boolean sInitializedDirs;

    /**
     * Look for MESSAGE_FILE. If found, read it in and populate the message arrays.
     */
    public static void readMessages() {
        final File messageFile = new File(getMessageFile());
        if (!messageFile.exists()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - no file");
            }
            resetMessages();
            return;
        }
        final long messageFileModDate = messageFile.lastModified();
        if (sMessageFileLastModified == messageFileModDate) {
            // no need to re-read the file
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - file hasn't changed");
            }
            return;
        }

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                sMessageFileLastModified = messageFileModDate;
                final InputStream messageStream;
                try {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "readMessages() - opening");
                    }
                    messageStream = new FileInputStream(messageFile);
                } catch (FileNotFoundException e) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Didn't find " + MESSAGE_FILE);
                    }
                    return null;
                }

                parseMessageStream(messageStream);
                return null;
            }

        }.execute((Void) null);
    }

    private static void parseMessageStream(InputStream messageStream) {
        final BufferedReader messageReader =
                new BufferedReader(new InputStreamReader(messageStream));

        resetMessages();

        String line;
        while (true) {
            try {
                line = messageReader.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null) {
                break;
            }
            // trim leading spaces
            line = line.replaceFirst("^\\s+", "");
            // Skip empty or comment lines (first non-whitespace is a '#')
            if ((line.length() == 0) || (line.matches("^#.*"))) {
                continue;
            }
            final String fields[] = (line + " ").split("/");
            // process a "welcome" message
            if ("w".equals(fields[0])) {
                if (fields.length != 5) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "invalid welcome message: needs 5 fields, has "
                                + fields.length);
                    }
                    continue;
                }
                final WelcomeMessage message = new WelcomeMessage();
                message.timeRegexp = getString(fields[1]);
                message.idMatch = getString(fields[2]);
                message.message = getString(fields[3]);
                message.weight = getWeight(fields[4]);
                sWelcomeMessages.add(message);

                // process a "returning" message
            } else if ("r".equals(fields[0])) {
                if (fields.length != 6) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "invalid return message: needs 6 fields, has "
                                + fields.length);
                    }
                    continue;
                }
                final ReturnMessage message = new ReturnMessage();
                message.timeRegexp = getString(fields[1]);
                message.idRegexp = getString(fields[2]);
                message.message = getString(fields[3]);
                message.isLast = getString(fields[4]);
                message.weight = getWeight(fields[5]);
                sReturnMessages.add(message);

                // process a "go" message
            } else if ("g".equals(fields[0])) {
                if (fields.length != 4) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "invalid return message: needs 4 fields, has "
                                + fields.length);
                    }
                    continue;
                }
                final GoMessage message = new GoMessage();
                message.timeRegexp = getString(fields[1]);
                message.message = getString(fields[2]);
                message.weight = getWeight(fields[3]);
                sGoMessages.add(message);

                // unrecognized message type -- ignore it
            } else {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "unrecognized: " + line);
                }
            }
        }

        try {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - closing");
            }
            messageStream.close();
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Error while closing " + MESSAGE_FILE);
            }
        }
    }

    /**
     * Empty the message lists.
     */
    private static void resetMessages() {
        sWelcomeMessages = new ArrayList<WelcomeMessage>();;
        sReturnMessages = new ArrayList<ReturnMessage>();
        sGoMessages = new ArrayList<GoMessage>();
    }

    /**
     * @return rawFieldString with any leading or trailing whitespace removed
     */
    private static String getString(final String rawFieldString) {
        return rawFieldString.replaceAll("^\\s+|\\s+$", "");
    }

    private static int getWeight(final String weightString) {
        final String trimmed = getString(weightString);
        if (trimmed.length() == 0) {
            return DEFAULT_WEIGHT;
        }
        int result;
        try {
            result = Integer.parseInt(trimmed);
        } catch (final NumberFormatException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "invalid weight: " + trimmed);
            }
            result = DEFAULT_WEIGHT;
        }
        return result;
    }

    public static String timeString() {
        final long now = System.currentTimeMillis();
        return RiderMessages.DATE_FORMAT.format(new Date(now));
    }

    private static class CandidateMessage {
        final String message;
        final int weight;

        public CandidateMessage(final String message, final int weight) {
            this.message = message;
            this.weight = weight;
        }
    }

    /**
     * @param rider - a rider id
     * @param timeString the current time
     * @return a welcome string or null if there were none
     */
    public static String getWelcomeString(final String rider, String timeString) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final WelcomeMessage message: sWelcomeMessages) {
            final String idMatch = message.idMatch;
            final String timeRegexp = message.timeRegexp;
            if ((!idMatch.isEmpty() && !rider.matches(idMatch))
                    || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))) {
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        return selectMessage(candidates, totalWeight);
    }

    /**
     * @return a random message from the weighted candidates list
     */
    private static String selectMessage(final ArrayList<CandidateMessage> candidates,
            final int totalWeight) {

        if (totalWeight > 0) {
            final int randomPick = sRandom.nextInt(totalWeight);
            for (final CandidateMessage candidate: candidates) {
                if (randomPick < candidate.weight) {
                    return candidate.message;
                }
            }
        }

        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, "failed to find a message");
        }
        return null;
    }

    /**
     * @param rider - a rider id
     * @param timeString - the current time
     * @param isLast - true if this rider's arrival emptied the manifest
     * @return a welcome back string or null if there were none
     */
    public static String getReturnsString(final String rider, String timeString,
            final boolean isLast) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final ReturnMessage message: sReturnMessages) {
            final String idRegexp = message.idRegexp;
            final String timeRegexp = message.timeRegexp;
            final String messageIsLast = message.isLast;
            final String riderIsLast = isLast ? "t" : "f";
            if ((!idRegexp.isEmpty() && !rider.matches(idRegexp))
                    || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                    || (!messageIsLast.isEmpty() && !riderIsLast.equals(messageIsLast))) {
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        return selectMessage(candidates, totalWeight);
    }

    /**
     * @param timeString - the current time
     * @return a "time to go" string or null if there were none
     */
    public static String getGoString(String timeString) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final GoMessage message: sGoMessages) {
            final String timeRegexp = message.timeRegexp;
            if (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp)) {
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        return selectMessage(candidates, totalWeight);
    }

    public static String getMessageFile() {
        if (!sInitializedDirs) {
            if (!DATA_DIR.exists()) {
                DATA_DIR.mkdirs();
            }
            if (!DOWNLOAD_DIR.exists()) {
                DOWNLOAD_DIR.mkdirs();
            }
            sInitializedDirs = true;
        }
        return MESSAGE_FILE;
    }
}