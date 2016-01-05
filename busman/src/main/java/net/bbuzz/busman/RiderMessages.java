package net.bbuzz.busman;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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

    public final static String MESSAGE_JSON_FILE_NAME = "BusManMessages.json";

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
    public final static String MESSAGE_JSON_FILE =
            new File(DATA_DIR, MESSAGE_JSON_FILE_NAME).getAbsolutePath();
    public final static String MESSAGE_JSON_FILE_OLD =
            new File(DATA_DIR, MESSAGE_JSON_FILE_NAME + ".old").getAbsolutePath();

    public final static int DEFAULT_WEIGHT = 10;
    // SimpleDateFormat e.g., "Sun, Mar 23, 2014 14:07"
    public final static SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM dd, yyyy HH:mm");
    private static final Random sRandom = new Random();

    private interface JsonSerializable {
        void writeJson(JsonWriter writer) throws IOException;

        void readJson(JsonReader reader) throws IOException;
    }

    /**
     * Strings to announce the arrival of a rider upon being added to the manifest
     */
    static class WelcomeMessage implements JsonSerializable {
        public String idMatch;
        public String timeRegexp;
        public String message;
        public int weight;

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writer.name("idMatch").value(idMatch);
            writer.name("timeRegexp").value(timeRegexp);
            writer.name("message").value(message);
            writer.name("weight").value(weight);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("idMatch")) {
                    idMatch = reader.nextString();
                } else if (name.equals("timeRegexp")) {
                    timeRegexp = reader.nextString();
                } else if (name.equals("message")) {
                    message = reader.nextString();
                } else if (name.equals("weight")) {
                    weight = reader.nextInt();
                } else {
                    Log.w(TAG, "Unknown WelcomeMessage key: " + name);
                    reader.skipValue();
                }
            }
        }
    }

    /**
     * Strings to announce the arrival of a rider upon being removed from the manifest
     */
    static class ReturnMessage implements JsonSerializable {
        public String idRegexp;
        public String timeRegexp;
        public String message;
        public String isLast;
        public int weight;

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writer.name("idRegexp").value(idRegexp);
            writer.name("timeRegexp").value(timeRegexp);
            writer.name("message").value(message);
            writer.name("isLast").value(isLast);
            writer.name("weight").value(weight);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("idRegexp")) {
                    idRegexp = reader.nextString();
                } else if (name.equals("timeRegexp")) {
                    timeRegexp = reader.nextString();
                } else if (name.equals("message")) {
                    message = reader.nextString();
                } else if (name.equals("isLast")) {
                    isLast = reader.nextString();
                } else if (name.equals("weight")) {
                    weight = reader.nextInt();
                } else {
                    Log.w(TAG, "Unknown ReturnMessage key: " + name);
                    reader.skipValue();
                }
            }
        }
    }

    /**
     * Strings to announce the depletion of the manifest and the impending departure
     */
    static class GoMessage implements JsonSerializable {
        public String timeRegexp;
        public String message;
        public int weight;

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writer.name("timeRegexp").value(timeRegexp);
            writer.name("message").value(message);
            writer.name("weight").value(weight);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("timeRegexp")) {
                    timeRegexp = reader.nextString();
                } else if (name.equals("message")) {
                    message = reader.nextString();
                } else if (name.equals("weight")) {
                    weight = reader.nextInt();
                } else {
                    Log.w(TAG, "Unknown GoMessage key: " + name);
                    reader.skipValue();
                }
            }
        }
    }

    private List<WelcomeMessage> mWelcomeMessages;
    private List<ReturnMessage> mReturnMessages;
    private List<GoMessage> mGoMessages;

    public static RiderMessages sInstance = new RiderMessages();

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
    public void readMessages() {
        // Let's try the JSON file first.
        if (!readJsonMessages()) {
            // Couldn't load the JSON file, let's fall back to the old-fashioned text file.
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
                    FileWriter fileWriter = null;
                    try {
                        fileWriter = new FileWriter(MESSAGE_JSON_FILE);
                        JsonWriter writer = new JsonWriter(fileWriter);
                        writeJson(writer);
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw (new RuntimeException(e));
                    }
                    return null;
                }

            }.execute((Void) null);
        }
    }
    /**
     * Look for MESSAGE_JSON_FILE. If found, read it in and populate the message arrays.
     */
    public boolean readJsonMessages() {
        // Let's try the JSON file first.
        final File messageFile = new File(getJsonMessageFile());
        if (!messageFile.exists()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - no JSON file");
            }
            return false;
        }
        final long messageFileModDate = messageFile.lastModified();
        if (sMessageFileLastModified == messageFileModDate) {
            // no need to re-read the file
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - JSON file hasn't changed");
            }
            return true;
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

                parseJsonStream(messageStream);
                return null;
            }

        }.execute((Void) null);
        return true;
    }

    private void writeJson(JsonWriter writer) throws IOException {
        writer.setIndent("  ");
        writer.beginObject();
        writer.name("welcomeMessages");
        writeJsonArray(writer, mWelcomeMessages);
        writer.name("returnMessages");
        writeJsonArray(writer, mReturnMessages);
        writer.name("goMessages");
        writeJsonArray(writer, mGoMessages);

        writer.endObject();
    }

    private void writeJsonArray(JsonWriter writer, List<? extends JsonSerializable> list) throws IOException {
        writer.beginArray();
        for (JsonSerializable object : list) {
            writer.beginObject();
            object.writeJson(writer);
            writer.endObject();
        }
        writer.endArray();
    }

    private <T extends JsonSerializable> List<T> readJsonArray(JsonReader reader, Class<T> clazz) throws IOException {
        List<T> result = new ArrayList<T>();

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            T element = null;
            try {
                element = clazz.newInstance();
                element.readJson(reader);
                result.add(element);
            } catch (Exception e) {
                Log.e(TAG, "Error instantiating " + clazz.getName(), e);
            }
            reader.endObject();
        }
        reader.endArray();

        return result;
    }

    private void parseJsonStream(InputStream jsonStream) {
        resetMessages();

        try {
            JsonReader reader = new JsonReader(new InputStreamReader(jsonStream));
            reader.beginObject();

            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("welcomeMessages")) {
                    mWelcomeMessages = readJsonArray(reader, WelcomeMessage.class);
                } else if (name.equals("returnMessages")) {
                    mReturnMessages = readJsonArray(reader, ReturnMessage.class);
                } else if (name.equals("goMessages")) {
                    mGoMessages = readJsonArray(reader, GoMessage.class);
                } else {
                    Log.w(TAG, "Unknown top-level key " + name);
                    reader.skipValue();
                }
            }

            reader.endObject();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseMessageStream(InputStream messageStream) {
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
                mWelcomeMessages.add(message);

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
                mReturnMessages.add(message);

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
                mGoMessages.add(message);

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
    private void resetMessages() {
        mWelcomeMessages = new ArrayList<WelcomeMessage>();;
        mReturnMessages = new ArrayList<ReturnMessage>();
        mGoMessages = new ArrayList<GoMessage>();
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

    private static String sLatestWelcomeString;
    /**
     * @param rider - a rider id
     * @param timeString the current time
     * @return a welcome string or null if there were none
     */
    public String getWelcomeString(final String rider, String timeString) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final WelcomeMessage message: mWelcomeMessages) {
            final String idMatch = message.idMatch;
            final String timeRegexp = message.timeRegexp;
            if ((!idMatch.isEmpty() && !rider.matches(idMatch))
                    || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                    || (message.message.equals(sLatestWelcomeString))) {
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        sLatestWelcomeString =  selectMessage(candidates, totalWeight);
        return sLatestWelcomeString;
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

    private static String sLatestReturnsString;
    /**
     * @param rider - a rider id
     * @param timeString - the current time
     * @param isLast - true if this rider's arrival emptied the manifest
     * @return a welcome back string or null if there were none
     */
    public String getReturnsString(final String rider, String timeString,
            final boolean isLast) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final ReturnMessage message: mReturnMessages) {
            final String idRegexp = message.idRegexp;
            final String timeRegexp = message.timeRegexp;
            final String messageIsLast = message.isLast;
            final String riderIsLast = isLast ? "t" : "f";
            if ((!idRegexp.isEmpty() && !rider.matches(idRegexp))
                    || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                    || (!messageIsLast.isEmpty() && !riderIsLast.equals(messageIsLast))
                    || (message.message.equals(sLatestReturnsString))) {
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        sLatestReturnsString = selectMessage(candidates, totalWeight);
        return sLatestReturnsString;
    }

    /**
     * @param timeString - the current time
     * @return a "time to go" string or null if there were none
     */
    public String getGoString(String timeString) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final GoMessage message: mGoMessages) {
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

    public static String getJsonMessageFile() {
        if (!sInitializedDirs) {
            if (!DATA_DIR.exists()) {
                DATA_DIR.mkdirs();
            }
            if (!DOWNLOAD_DIR.exists()) {
                DOWNLOAD_DIR.mkdirs();
            }
            sInitializedDirs = true;
        }
        return MESSAGE_JSON_FILE;
    }
}