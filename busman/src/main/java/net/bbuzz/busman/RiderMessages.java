package net.bbuzz.busman;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.VisibleForTesting;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.widget.Toast;

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
import java.util.regex.PatternSyntaxException;

public class RiderMessages {

    private final static String TAG =  "RiderMessages";

    final static String MESSAGE_JSON_FILE_NAME = "BusManMessages.json";

    private final static File DOWNLOAD_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    public final static String DOWNLOAD_MESSAGE_FILE = new File(DOWNLOAD_DIR,
            MESSAGE_JSON_FILE_NAME).getAbsolutePath();

    private final static File DATA_DIR =
            new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/BusMan");
    final static String MESSAGE_JSON_FILE =
            new File(DATA_DIR, MESSAGE_JSON_FILE_NAME).getAbsolutePath();
    final static String MESSAGE_JSON_FILE_OLD =
            new File(DATA_DIR, MESSAGE_JSON_FILE_NAME + ".old").getAbsolutePath();

    final static int DEFAULT_WEIGHT = 10;
    // SimpleDateFormat e.g., "Sun, Mar 3, 2014 14:07"
    private final static SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d, yyyy HH:mm");
    private static final Random sRandom = new Random();

    private static final String FIELD_ID_REGEXP = "idRegexp";
    private static final String FIELD_TIME_REGEXP = "timeRegexp";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_WEIGHT = "weight";
    private static final String FIELD_IS_LAST = "isLast";
    private static final String FIELD_DEJA_VU = "dejaVu";

    /**
     * Strings to announce the arrival of a rider upon being added to the manifest
     */
    static class WelcomeMessage extends JsonSerializable {
        private static final String DEFAULT_ID_REGEXP = "";
        private static final String DEFAULT_TIME_REGEXP = "";
        private static final String DEFAULT_MESSAGE = "";
        private static final int DEFAULT_WEIGHT = 10;

        String idRegexp;
        String timeRegexp;
        String message;
        int weight;


        public WelcomeMessage() {
            idRegexp = DEFAULT_ID_REGEXP;
            timeRegexp = DEFAULT_TIME_REGEXP;
            message = DEFAULT_MESSAGE;
            weight = DEFAULT_WEIGHT;
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writeValue(writer, FIELD_ID_REGEXP, idRegexp, DEFAULT_ID_REGEXP);
            writeValue(writer, FIELD_TIME_REGEXP, timeRegexp, DEFAULT_TIME_REGEXP);
            writeValue(writer, FIELD_MESSAGE, message, DEFAULT_MESSAGE);
            writeValue(writer, FIELD_WEIGHT, weight, DEFAULT_WEIGHT);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case FIELD_ID_REGEXP:
                        idRegexp = reader.nextString();
                        break;
                    case FIELD_TIME_REGEXP:
                        timeRegexp = reader.nextString();
                        break;
                    case FIELD_MESSAGE:
                        message = reader.nextString();
                        break;
                    case FIELD_WEIGHT:
                        weight = reader.nextInt();
                        break;
                    case FIELD_COMMENT:
                        reader.skipValue();
                        break;
                    default:
                        if (Log.isLoggable(TAG, Log.WARN)) {
                            Log.w(TAG, "Unknown WelcomeMessage key: " + name);
                        }
                        reader.skipValue();
                        break;
                }
            }
        }
    }

    /**
     * Strings to announce the arrival of a rider upon being removed from the manifest
     */
    static class ReturnMessage extends JsonSerializable {
        private static final String DEFAULT_ID_REGEXP = "";
        private static final String DEFAULT_TIME_REGEXP = "";
        private static final String DEFAULT_MESSAGE = "";
        private static final String DEFAULT_ISLAST = "";
        private static final int DEFAULT_WEIGHT = 10;

        String idRegexp;
        String timeRegexp;
        String message;
        String isLast;
        int weight;

        public ReturnMessage() {
            idRegexp = DEFAULT_ID_REGEXP;
            timeRegexp = DEFAULT_TIME_REGEXP;
            message = DEFAULT_MESSAGE;
            isLast = DEFAULT_ISLAST;
            weight = DEFAULT_WEIGHT;
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writeValue(writer, FIELD_ID_REGEXP, idRegexp, DEFAULT_ID_REGEXP);
            writeValue(writer, FIELD_TIME_REGEXP, timeRegexp, DEFAULT_TIME_REGEXP);
            writeValue(writer, FIELD_MESSAGE, message, DEFAULT_MESSAGE);
            writeValue(writer, FIELD_IS_LAST, isLast, DEFAULT_ISLAST);
            writeValue(writer, FIELD_WEIGHT, weight, DEFAULT_WEIGHT);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case FIELD_ID_REGEXP:
                        idRegexp = reader.nextString();
                        break;
                    case FIELD_TIME_REGEXP:
                        timeRegexp = reader.nextString();
                        break;
                    case FIELD_MESSAGE:
                        message = reader.nextString();
                        break;
                    case FIELD_IS_LAST:
                        isLast = reader.nextString();
                        break;
                    case FIELD_WEIGHT:
                        weight = reader.nextInt();
                        break;
                    case FIELD_COMMENT:
                        reader.skipValue();
                        break;
                    default:
                        if (Log.isLoggable(TAG, Log.WARN)) {
                            Log.w(TAG, "Unknown ReturnMessage key: " + name);
                        }
                        reader.skipValue();
                        break;
                }
            }
        }
    }

    /**
     * Announce the attempted removal of a rider who is not in the manifest
     */
    static class AlreadyReturnedMessage extends JsonSerializable {
        private static final String DEFAULT_ID_REGEXP = "";
        private static final String DEFAULT_TIME_REGEXP = "";
        private static final String DEFAULT_MESSAGE = "";
        private static final String DEFAULT_DEJAVU = "";
        private static final int DEFAULT_WEIGHT = 10;

        String idRegexp;
        String timeRegexp;
        String message;
        String dejaVu;  // "t" means we did recently remove this rider, so this is a stutter
        int weight;

        public AlreadyReturnedMessage() {
            idRegexp = DEFAULT_ID_REGEXP;
            timeRegexp = DEFAULT_TIME_REGEXP;
            message = DEFAULT_MESSAGE;
            dejaVu = DEFAULT_DEJAVU;
            weight = DEFAULT_WEIGHT;
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writeValue(writer, FIELD_ID_REGEXP, idRegexp, DEFAULT_ID_REGEXP);
            writeValue(writer, FIELD_TIME_REGEXP, timeRegexp, DEFAULT_TIME_REGEXP);
            writeValue(writer, FIELD_MESSAGE, message, DEFAULT_MESSAGE);
            writeValue(writer, FIELD_DEJA_VU, dejaVu, DEFAULT_DEJAVU);
            writeValue(writer, FIELD_WEIGHT, weight, DEFAULT_WEIGHT);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case FIELD_ID_REGEXP:
                        idRegexp = reader.nextString();
                        break;
                    case FIELD_TIME_REGEXP:
                        timeRegexp = reader.nextString();
                        break;
                    case FIELD_MESSAGE:
                        message = reader.nextString();
                        break;
                    case FIELD_DEJA_VU:
                        dejaVu = reader.nextString();
                        break;
                    case FIELD_WEIGHT:
                        weight = reader.nextInt();
                        break;
                    case FIELD_COMMENT:
                        reader.skipValue();
                        break;
                    default:
                        if (Log.isLoggable(TAG, Log.WARN)) {
                            Log.w(TAG, "Unknown ReturnMessage key: " + name);
                        }
                        reader.skipValue();
                        break;
                }
            }
        }
    }

    /**
     * Strings to announce the depletion of the manifest and the impending departure
     */
    static class GoMessage extends JsonSerializable {
        private static final String DEFAULT_TIME_REGEXP = "";
        private static final String DEFAULT_MESSAGE = "";
        private static final int DEFAULT_WEIGHT = 10;

        String timeRegexp;
        String message;
        int weight;

        public GoMessage() {
            timeRegexp = DEFAULT_TIME_REGEXP;
            message = DEFAULT_MESSAGE;
            weight = DEFAULT_WEIGHT;
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writeValue(writer, FIELD_TIME_REGEXP, timeRegexp, DEFAULT_TIME_REGEXP);
            writeValue(writer, FIELD_MESSAGE, message, DEFAULT_MESSAGE);
            writeValue(writer, FIELD_WEIGHT, weight, DEFAULT_WEIGHT);
        }

        @Override
        public void readJson(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case FIELD_TIME_REGEXP:
                        timeRegexp = reader.nextString();
                        break;
                    case FIELD_MESSAGE:
                        message = reader.nextString();
                        break;
                    case FIELD_WEIGHT:
                        weight = reader.nextInt();
                        break;
                    case FIELD_COMMENT:
                        reader.skipValue();
                        break;
                    default:
                        if (Log.isLoggable(TAG, Log.WARN)) {
                            Log.w(TAG, "Unknown GoMessage key: " + name);
                        }
                        reader.skipValue();
                        break;
                }
            }
        }
    }

    private List<WelcomeMessage> mWelcomeMessages;
    private List<WelcomeMessage> mAlreadyWelcomedMessages;
    private List<ReturnMessage> mReturnMessages;
    private List<AlreadyReturnedMessage> mAlreadyReturnedMessages;
    private List<GoMessage> mGoMessages;

    static RiderMessages sInstance = new RiderMessages();

    /*
     *  The BusManMessages file is a JSON file with three different lists: welcome messages,
     *  returning messages, go messages.
     *
     *  timeRegexp is a regular expression to match against a time/date string in the default
     *  locale, of the form
     *          "Sun, Mar 23, 2014 14:07"
     *          example: ex: ".*Apr 1,.*" matches April first
     *          (empty matches all)
     *  idRegexp is a regular expression to match against a rider id, or empty to match all riders
     *  message is the string, which can contain:
     *          "%s" will be replaced with the rider's name (one instance)
     *          "!locale(language)" can appear anywhere in the message. It will be deleted from the
     *              message, and the language (an ISO locale spec) will be passed to the TTS engine,
     *              e.g., "!locale(en_GB)"
     *  dejaVu is either "t", signifying that this matches a recently removed rider;
     *          "f" signifies someone who is NOT recognized at all; and empty matches all
     *  weight is the selection weight. Among all matching messages, a message's weight divided by
     *          the sum of all matching messages' weights represents the probability that a given
     *          message will be selected. If weight is empty, it defaults to a value of 10.
     *
     *  NOTE: empty lines, and lines beginning with "#" are ignored
     */

    private static long sMessageFileLastModified = 0L;

    private static boolean sInitializedDirs;

    /**
     * Look for MESSAGE_FILE. If found, read it in and populate the message arrays.
     * @param context
     */
    void readMessages(final Context context) {
        final String messageFileName = getJsonMessageFile();
        final File messageFile = new File(messageFileName);
        if (!messageFile.exists()) {
            final String msgNoFile = context.getString(R.string.didnt_find_file, messageFileName);
            Toast.makeText(context, msgNoFile, Toast.LENGTH_LONG).show();
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - no JSON file");
            }
            resetMessages();
            return;
        }
        final long messageFileModDate = messageFile.lastModified();
        if (sMessageFileLastModified == messageFileModDate) {
            // no need to re-read the file
            Toast.makeText(context, R.string.messages_havent_changed, Toast.LENGTH_SHORT).show();
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "readMessages() - JSON file hasn't changed");
            }
            return;
        }

        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                final Resources res = context.getResources();
                sMessageFileLastModified = messageFileModDate;
                final InputStream messageStream;
                try {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "readMessages() - opening");
                    }
                    messageStream = new FileInputStream(messageFile);
                } catch (FileNotFoundException e) {
                    final String message = res.getString(R.string.didnt_find_file,
                            MESSAGE_JSON_FILE);
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, message);
                    }
                    return message;
                }

                final String result = parseJsonStream(messageStream);
                return res.getString(R.string.finished_reading_messages, result);
            }

            protected void onPostExecute(String result) {
                Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            }

        }.execute((Void) null);
    }

    private <T extends JsonSerializable> List<T> readJsonArray(JsonReader reader, Class<T> clazz)
            throws IOException {
        List<T> result = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            T element;
            try {
                element = clazz.newInstance();
                element.readJson(reader);
                result.add(element);
            } catch (Exception e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Error instantiating " + clazz.getName(), e);
                }
            }
            reader.endObject();
        }
        reader.endArray();

        return result;
    }

    @VisibleForTesting
    String parseJsonStream(InputStream jsonStream) {
        final StringBuilder result = new StringBuilder();
        resetMessages();

        try {
            final JsonReader reader = new JsonReader(new InputStreamReader(jsonStream));
            reader.beginObject();

            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case "welcomeMessages":
                        mWelcomeMessages = readJsonArray(reader, WelcomeMessage.class);
                        break;

                    case "alreadyWelcomedMessages":
                        mAlreadyWelcomedMessages = readJsonArray(reader, WelcomeMessage.class);
                        break;

                    case "returnMessages":
                        mReturnMessages = readJsonArray(reader, ReturnMessage.class);
                        break;

                    case "alreadyReturnedMessages":
                        mAlreadyReturnedMessages = readJsonArray(reader,
                                AlreadyReturnedMessage.class);
                        break;

                    case "goMessages":
                        mGoMessages = readJsonArray(reader, GoMessage.class);
                        break;

                    default:
                        final String message = "Unknown top-level key " + name;
                        result.append(message + "\n");
                        if (Log.isLoggable(TAG, Log.WARN)) {
                            Log.w(TAG, message);
                        }
                        reader.skipValue();
                        break;
                }
            }

            reader.endObject();
            reader.close();
            final String separator = ", ";
            result.append("(")
                    .append(mWelcomeMessages.size()).append(separator)
                    .append(mAlreadyWelcomedMessages.size()).append(separator)
                    .append(mReturnMessages.size()).append(separator)
                    .append(mAlreadyReturnedMessages.size()).append(separator)
                    .append(mGoMessages.size())
                    .append(")");
        } catch (IOException e) {
            result.append(e.toString());
            e.printStackTrace();
        }

        return result.toString();
    }

    /**
     * Empty the message lists.
     */
    private void resetMessages() {
        mWelcomeMessages = new ArrayList<>();
        mAlreadyWelcomedMessages = new ArrayList<>();
        mReturnMessages = new ArrayList<>();
        mAlreadyReturnedMessages = new ArrayList<>();
        mGoMessages = new ArrayList<>();
    }

    static String timeString() {
        return timeString(System.currentTimeMillis());
    }

    static String timeString(long time) {
        return RiderMessages.DATE_FORMAT.format(new Date(time));
    }

    private static class CandidateMessage {
        final String message;
        final int weight;

        CandidateMessage(final String message, final int weight) {
            this.message = message;
            this.weight = weight;
        }
    }

    private static String sLatestWelcomeString;

    /**
     * @param rider - a rider id
     * @return a welcome string or null if there were none
     */
    private String getWelcomeString(final String rider, List<WelcomeMessage> welcomeMessages) {
        if (welcomeMessages == null) {
            return null;
        }
        final ArrayList<CandidateMessage> candidates = new ArrayList<>();
        final String timeString = timeString();
        int totalWeight = 0;
        for (final WelcomeMessage message: welcomeMessages) {
            final String idRegexp = message.idRegexp;
            final String timeRegexp = stripLeadingZeroInTimeRegexp(message.timeRegexp);
            try {
                if ((!idRegexp.isEmpty() && !riderMatchesIdRegexp(rider, idRegexp))
                        || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                        || (message.message.equals(sLatestWelcomeString))) {
                    continue;
                }
            } catch (PatternSyntaxException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "welcome regexp error: " + e);
                }
                continue;
            }
            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        sLatestWelcomeString =  selectMessage(candidates, totalWeight);
        return sLatestWelcomeString;
    }

    /**
     * @param rider - a rider id
     * @return a welcome string or null if there were none
     */
    String getWelcomeString(final String rider) {
        return getWelcomeString(rider, mWelcomeMessages);
    }

    /**
     * @param rider - a rider id
     * @return a "not on the list" string or null if there were none
     */
    String getAlreadyWelcomedString(final String rider) {
        return getWelcomeString(rider, mAlreadyWelcomedMessages);
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
     * @param isLast - true if this rider's arrival emptied the manifest
     * @return a welcome back string or null if there were none
     */
    String getReturnsString(final String rider, final boolean isLast) {
        if (mReturnMessages == null) {
            return null;
        }
        final ArrayList<CandidateMessage> candidates = new ArrayList<>();
        final String timeString = timeString();
        int totalWeight = 0;
        for (final ReturnMessage message: mReturnMessages) {
            final String idRegexp = message.idRegexp;
            final String timeRegexp = stripLeadingZeroInTimeRegexp(message.timeRegexp);
            final String messageIsLast = message.isLast;
            final String riderIsLast = isLast ? "t" : "f";
            try {
                if ((!idRegexp.isEmpty() && !riderMatchesIdRegexp(rider, idRegexp))
                        || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                        || (!messageIsLast.isEmpty() && !riderIsLast.equals(messageIsLast))
                        || (message.message.equals(sLatestReturnsString))) {
                    continue;
                }
            } catch (PatternSyntaxException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "returns regexp error: " + e);
                }
                continue;
            }

            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        sLatestReturnsString = selectMessage(candidates, totalWeight);
        return sLatestReturnsString;
    }

    private static String sLatestAlreadyReturnedString;

    /**
     * @param rider - a rider id
     * @return a welcome back string or null if there were none
     */
    String getAlreadyReturnedString(final String rider, boolean alreadyRemoved) {
        if (mAlreadyReturnedMessages == null) {
            return null;
        }
        final ArrayList<CandidateMessage> candidates = new ArrayList<>();
        final String timeString = timeString();
        int totalWeight = 0;
        for (final AlreadyReturnedMessage message: mAlreadyReturnedMessages) {
            final String idRegexp = message.idRegexp;
            final String timeRegexp = stripLeadingZeroInTimeRegexp(message.timeRegexp);
            final String messageIsDejaVu = message.dejaVu;
            final String dejaVu = alreadyRemoved ? "t" : "f";
            try {
                if ((!idRegexp.isEmpty() && !riderMatchesIdRegexp(rider, idRegexp))
                        || (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp))
                        || (!messageIsDejaVu.isEmpty() && !dejaVu.equals(messageIsDejaVu))
                        || (message.message.equals(sLatestAlreadyReturnedString))) {
                    continue;
                }
            } catch (PatternSyntaxException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "alreadyReturned regexp error: " + e);
                }
                continue;
            }

            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        sLatestAlreadyReturnedString = selectMessage(candidates, totalWeight);
        return sLatestAlreadyReturnedString;
    }

    private boolean riderMatchesIdRegexp(String rider, String id) {
//        Temporarily removed the safer regex matcher since it breaks the existing ID regexes.
//        return rider.matches(".*\\[" + id + ".*");
        return rider.matches(id);
    }

    /**
     * @param timeString - the current time
     * @return a "time to go" string or null if there were none
     */
    String getGoString(String timeString) {
        final ArrayList<CandidateMessage> candidates = new ArrayList<CandidateMessage>();
        int totalWeight = 0;
        for (final GoMessage message: mGoMessages) {
            final String timeRegexp = stripLeadingZeroInTimeRegexp(message.timeRegexp);
            try {
                if (!timeRegexp.isEmpty() && !timeString.matches(timeRegexp)) {
                    continue;
                }
            } catch (PatternSyntaxException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "go regexp error: " + e);
                }
                continue;
            }

            totalWeight += message.weight;
            candidates.add(new CandidateMessage(message.message, totalWeight));
        }

        return selectMessage(candidates, totalWeight);
    }

    @VisibleForTesting
    static String stripLeadingZeroInTimeRegexp(String timeRegexp) {
        return timeRegexp.replaceFirst(" 0([0-9])", " $1");
    }

    private static String getJsonMessageFile() {
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

    @VisibleForTesting
    List<WelcomeMessage> getWelcomeMessages() {
        return mWelcomeMessages;
    }

    @VisibleForTesting
    List<WelcomeMessage> getAlreadyWelcomedMessages() {
        return mAlreadyWelcomedMessages;
    }

    @VisibleForTesting
    List<ReturnMessage> getReturnMessages() {
        return mReturnMessages;
    }

    @VisibleForTesting
    List<AlreadyReturnedMessage> getAlreadyReturnedMessages() {
        return mAlreadyReturnedMessages;
    }

    @VisibleForTesting
    List<GoMessage> getGoMessages() {
        return mGoMessages;
    }
}
