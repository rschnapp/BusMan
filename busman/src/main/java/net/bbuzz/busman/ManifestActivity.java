/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bbuzz.busman;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ManifestActivity extends ListActivity {

    private static final String TAG = "ManifestActivity";

    private static final int COLOR_WARNING = 0xff600000;
    private static final int COLOR_SUCCESS = 0xff006000;
    private static final int COLOR_ADDING = 0xff006000;
    private static final int COLOR_DROPPING = 0xff600000;
    private static final int COLOR_BLACK = 0xff000000;

    private static final int MAX_MESSAGE_TRIES = 3;

    private static final String ACTION_FORWARD = "net.bbuzz.busman.action.FORWARD";
    private static final String EXTRA_KEY_RIDER = "rider";

    private static final boolean MANIFEST_SORT_BY_NAME = true;
    private static final boolean MANIFEST_SORT_BY_BOARDING = false;
    private static final boolean MANIFEST_SORT_DEFAULT = MANIFEST_SORT_BY_BOARDING;

    private static final String ISKEY_IS_ADDING = "is_adding";
    private static final String ISKEY_SORT_BY_NAME = "sort";
    private static final String ISKEY_MANIFEST = "bus_manifest";
    private static final String MANIFEST_STATE_FILE = "ManifestState.txt";

    private static final String TEST_RIDER = "TestRider@TestRider";
    private static final String[] sTestRiders = new String[] {
            "gw@George Washington",
            "jadams@John Adams",
            "tjeff@Thomas Jefferson",
            "jmad@James Madison",
            "jmon@James Monroe",
            "jqa@John Quincy Adams",
            "jackson@Andrew Jackson",
            "mvb@Martin Van Buren",
            "whh@William Henry Harrison",
            "jtyler@John Tyler",
            "jkp@James Knox Polk",
            "zt@Zachary Taylor",
            "mf@Millard Fillmore",
            "fpierce@Franklin Pierce",
            "jbuchanan@James Buchanan",
            "alincoln@Abraham Lincoln",
            "ajohnson@Andrew Johnson",
            "usg@Ulysses S Grant",
            "rbh@Rutherford B Hayes",
            "jgar@James Garfield",
            "caa@Chester A Arthur"
    };

    private final static String[] ColumnNames = {Ride.ORDER, Ride.RIDER, Ride.TIME};
    private final static int[] ColumnFields = {R.id.ride_order, R.id.ride_name, R.id.ride_time};

    private TextView mModeLabel;
    private TextView mLatestActionLabel;
    private TextView mLatestRider;
    private TextView mEmptyListView;
    private Button mAddButton;
    private Button mDropButton;
    private boolean mIsAddingToManifest;
    private final Map<String, Ride> mRideManifest = new HashMap<String, Ride>();
    private boolean mManifestSortByName = MANIFEST_SORT_DEFAULT;
    private String mLatestRiderFromNfc;

    private boolean mTtsIsAvailable = false;
    private TextToSpeech mTts;
    private HashMap<String, String> mTtsOptions;
    private Integer mLastUtteranceSubmitted = 0;
    private String[] mWelcomes;
    private String[] mReturns;
    private static final Random sRandom = new Random();
    private boolean mTtsIsEnabled;
    private Locale mDefaultLocale;
    private Locale mLastLocale;
    private long mAwaitedDownloadId;
    private DownloadManager mDownloadManager;

    private final BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (mAwaitedDownloadId != downloadId) {
                return;
            }
            final DownloadManager.Query query = new DownloadManager.Query()
                    .setFilterById(mAwaitedDownloadId);
            final Cursor cursor = mDownloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                final int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                final int status = cursor.getInt(statusIndex);
                final int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                final int reason = cursor.getInt(reasonIndex);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Log.i(TAG, "messages download succeeded");
                    new File(RiderMessages.MESSAGE_FILE_OLD).delete();
                    new File(RiderMessages.MESSAGE_FILE)
                            .renameTo(new File(RiderMessages.MESSAGE_FILE_OLD));
                    final File downloadFile = new File(RiderMessages.DOWNLOAD_MESSAGE_FILE);
                    FileInputStream inStream;
                    FileOutputStream outStream;
                    try {
                        inStream =
                                new FileInputStream(downloadFile);
                        outStream = new FileOutputStream(new File(RiderMessages.MESSAGE_FILE));
                    } catch (FileNotFoundException e) {
                        Log.w(TAG, "failed to create files while copying downloaded messages: " +
                                e);
                        return;
                    }
                    FileChannel inChannel = inStream.getChannel();
                    FileChannel outChannel = outStream.getChannel();
                    try {
                        inChannel.transferTo(0, inChannel.size(), outChannel);
                        inStream.close();
                        outStream.close();
                    } catch (IOException e) {
                        Log.w(TAG, "failed to copy downloaded messages: " + e);
                        return;
                    }
                    downloadFile.delete();

                    final SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(ManifestActivity.this);
                    prefs.edit().putLong(SettingsActivity.PREF_MESSAGES_LAST_POLLED,
                            System.currentTimeMillis()).commit();

                    RiderMessages.readMessages();
                } else {
                    if (status == DownloadManager.STATUS_FAILED) {
                        Log.w(TAG, "messages download failed, error " + reason);
                    } else {
                        Log.w(TAG, "messages download failed, status=" + status +
                                ", reason=" + reason);
                    }
                }
            }
            mAwaitedDownloadId = 0;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manifest_activity);

        mTts = new TextToSpeech(this, new TtsListener());
        mDefaultLocale = Locale.getDefault();
        mLastLocale = mDefaultLocale;

        mModeLabel = (TextView) findViewById(R.id.manifest_mode_label);
        mLatestActionLabel = (TextView) findViewById(R.id.manifest_latest_label);
        mAddButton = (Button) findViewById(R.id.add_to_manifest_button);
        mDropButton = (Button) findViewById(R.id.drop_from_manifest_button);
        mLatestRider = (TextView) findViewById(R.id.manifest_latest_rider);
        mEmptyListView = (TextView) findViewById(android.R.id.empty);
        getListView().setFastScrollEnabled(true);
        final ListView listView = getListView();
        listView.setFastScrollAlwaysVisible(true);
        listView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        mIsAddingToManifest = true;
        mWelcomes = getResources().getStringArray(R.array.welcomes);
        mReturns = getResources().getStringArray(R.array.returns);

        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(mDownloadReceiver, filter);
        loadMessages();

        if (savedInstanceState != null) {
            restoreFromBundle(savedInstanceState);
        } else {
            restoreState();
        }

        mAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsAddingToManifest) {
                    mIsAddingToManifest = true;
                    clearRiderResult();
                    updateList();
                }
            }
        });

        mDropButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsAddingToManifest && !mRideManifest.isEmpty()) {
                    mIsAddingToManifest = false;
                    clearRiderResult();
                    updateList();
                }
            }
        });

        mModeLabel.setText(mIsAddingToManifest ? R.string.add_button : R.string.drop_button);
        updateList();

        maybeRecordNewRider(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mTtsIsEnabled = prefs.getBoolean(SettingsActivity.PREF_ENABLE_TTS,
                SettingsActivity.PREF_ENABLE_TTS_DEFAULT);
    }

    @Override
    public void onDestroy() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
        unregisterReceiver(mDownloadReceiver);

        super.onDestroy();
    }

    @Override
    public void onNewIntent(Intent intent) {
        maybeRecordNewRider(intent);
    }

    /**
     * Unpack saved app state from the bundle
     *
     * @param savedInstanceState
     */
    private void restoreFromBundle(Bundle savedInstanceState) {
        mIsAddingToManifest = savedInstanceState.getBoolean(ISKEY_IS_ADDING);
        mManifestSortByName = savedInstanceState.getBoolean(ISKEY_SORT_BY_NAME);
        final String[] rideStrings = savedInstanceState.getStringArray(ISKEY_MANIFEST);
        for (final String rideString : rideStrings) {
            final Ride ride = new Ride(rideString);
            synchronized(mRideManifest) {
                mRideManifest.put(ride.rider, ride);
            }
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveState();
        super.onSaveInstanceState(outState);
        outState.putBoolean(ISKEY_IS_ADDING, mIsAddingToManifest);
        outState.putBoolean(ISKEY_SORT_BY_NAME, mManifestSortByName);
        final int manifestSize = mRideManifest.size();
        final String[] rideArray = new String[manifestSize];
        int i = 0;
        synchronized(mRideManifest) {
            for (final Ride ride: mRideManifest.values()) {
                rideArray[i++] = ride.toString();
            }
        }
        outState.putStringArray(ISKEY_MANIFEST, rideArray);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restoreFromBundle(savedInstanceState);
    }

    /**
     * Update the manifest list with the current contents or provide the
     * appropriate "empty" text based on whether we're adding or deleting. Also
     * reflect the add/delete state in the mode line
     */
    private void updateList() {
        if (mIsAddingToManifest) {
            mModeLabel.setBackgroundColor(COLOR_ADDING);
            mModeLabel.setText(R.string.add_mode);
            mEmptyListView.setBackgroundColor(COLOR_BLACK);
            mEmptyListView.setText(R.string.empty_list_when_adding);
        } else {
            mModeLabel.setBackgroundColor(COLOR_DROPPING);
            mModeLabel.setText(R.string.drop_mode);
            mEmptyListView.setBackgroundColor(COLOR_SUCCESS);
            mEmptyListView.setText(R.string.empty_list_when_dropping);
        }
        final ArrayList<Ride> rideList;
        synchronized(mRideManifest) {
            rideList = new ArrayList<Ride>(mRideManifest.values());
        }
        Collections.sort(rideList,
                mManifestSortByName ? new NameComparator<Ride>() : new BoardingComparator<Ride>());
        final ArrayList<Map<String, String>> rideRows =
                new ArrayList<Map<String, String>>(mRideManifest.size());
        for (final Ride ride: rideList) {
            rideRows.add(ride.toMap());
        }
        setListAdapter(new SimpleAdapter(this, rideRows,
                mIsAddingToManifest ? R.layout.rider_row_adding : R.layout.rider_row_dropping,
                ColumnNames, ColumnFields));
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu_manifest, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        menu.findItem(R.id.option_add_rider).setEnabled(mIsAddingToManifest);
        menu.findItem(R.id.option_sort).setTitle(
                mManifestSortByName ? R.string.item_sort_by_boarding : R.string.item_sort_by_name);
        return true;
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        final TextView textView = (TextView) view.findViewById(R.id.ride_name);
        final String clickedRider = textView.getText().toString();
        final String dialogMessage = this.getResources().getString(R.string.drop_dialog_message,
                clickedRider);
        new AlertDialog.Builder(this)
                .setTitle(R.string.drop_dialog_title)
                .setMessage(dialogMessage)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        recordNewRider(false, clickedRider);
                        updateList();
                        saveState();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.option_load_messages:
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putLong(SettingsActivity.PREF_MESSAGES_LAST_POLLED, 0).commit();
                loadMessages();
                return true;

            case R.id.option_about_too:
                ConfigureTagActivity.showAboutDialog(this);
                return true;

            case R.id.option_clear:
                if (mRideManifest.isEmpty()) {
                    mIsAddingToManifest = true;
                    clearRiderResult();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.clear_confirm_title)
                            .setMessage(R.string.clear_confirm_message)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mIsAddingToManifest = true;
                                            synchronized(mRideManifest) {
                                                mRideManifest.clear();
                                            }
                                            clearRiderResult();
                                            updateList();
                                            saveState();
                                        }
                                    })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }

                return true;

            case R.id.option_sort:
                mManifestSortByName = !mManifestSortByName;
                invalidateOptionsMenu();
                updateList();
                return true;

            case R.id.option_add_rider:
                final View riderView = LayoutInflater.from(this).inflate(R.layout.add_rider, null);
                final EditText riderName = (EditText)riderView.findViewById(R.id.rider_text);

                new AlertDialog.Builder(this)
                    .setTitle(R.string.item_add_rider)
                    .setView(riderView)
                    .setPositiveButton(R.string.add_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            recordNewRider(true, riderName.getText().toString());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null).create().show();
                return true;

            case R.id.option_initialize_tag:
                final Intent configIntent = new Intent(this, ConfigureTagActivity.class);
                if (mLatestRiderFromNfc != null) {
                    configIntent.putExtra(ConfigureTagActivity.KEY_NFC_RIDER, mLatestRiderFromNfc);
                }
                startActivity(configIntent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Handle a freshly arrived rider intent (probably from NFC)
     * @param intent
     */
    private void maybeRecordNewRider(Intent intent) {
        if (MimeType.BUSMAN_MIMETYPE.equals(intent.getType())) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                    NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage msg = (NdefMessage) rawMsgs[0];
            final String riderText = new String(msg.getRecords()[0].getPayload());
            if (TEST_RIDER.equals(riderText)) {
                Log.d(TAG, "Found Test Rider");
                for (final String rider : sTestRiders) {
                    recordNewRiderFromNfc(rider);
                }
            }
            recordNewRiderFromNfc(riderText);
        } else if (ACTION_FORWARD.equals(intent.getAction())) {
            recordNewRiderFromNfc(intent.getStringExtra(EXTRA_KEY_RIDER));
        }
    }

    /**
     * Add or drop an NFC-formatted rider from the manifest
     *
     * @param nfcRiderText
     */
    private void recordNewRiderFromNfc(final String nfcRiderText) {
        Log.d(TAG, "recordLatestRider " + (mIsAddingToManifest ? "add " : "drop ") + nfcRiderText);
        mLatestRiderFromNfc = nfcRiderText;
        final int breakingPoint = nfcRiderText.indexOf(ConfigureTagActivity.ID_SEPARATOR);
        final String riderName = nfcRiderText.substring(breakingPoint + 1);
        final String riderId = nfcRiderText.substring(0, breakingPoint);
        final String rider = riderName + " [" + riderId + "]";
        recordNewRider(mIsAddingToManifest, rider);
    }

    /**
     * Add or drop a rider formatted as "name (id)"
     *
     * @param addToManifest
     * @param rider
     */
    private void recordNewRider(boolean addToManifest, String rider) {
        Log.d(TAG, "recordLatestRider " + (addToManifest ? "add " : "drop ") + rider);
        mLatestRider.setText(rider);
        if (addToManifest) {
            if (mRideManifest.containsKey(rider)) {
                dejaVu(rider);
                showRiderResult(COLOR_WARNING, R.string.manifest_action_label_dupe_added);
            } else {
                welcomeRider(rider, MAX_MESSAGE_TRIES);
                synchronized(mRideManifest) {
                    mRideManifest.put(rider, new Ride(rider, mRideManifest.size() + 1,
                            System.currentTimeMillis()));
                }
                showRiderResult(COLOR_SUCCESS, R.string.manifest_action_label_added);
                updateList();
            }
        } else {
            if (mRideManifest.containsKey(rider)) {
                returningRider(rider, mRideManifest.size() == 1, MAX_MESSAGE_TRIES);
                remove(rider);
                showRiderResult(COLOR_SUCCESS, R.string.manifest_action_label_dropped);
                updateList();
                if (mRideManifest.isEmpty()) {
                    timeToGo();
                }
            } else {
                whoAreYou(rider);
                showRiderResult(COLOR_WARNING, R.string.manifest_action_label_dupe_dropped);
            }
        }
    }

    /**
     * @param rider - String in form "rider's name [rider's id]"
     * @return rider's first name
     */
    private static String firstName(final String rider) {
        final String[] names = rider.split("\\s+");
        for (final String name: names) {
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    /**
     * @param rider - String in form "rider's name [rider's id]"
     * @return rider's id
     */
    private static String id(final String rider) {
        return rider.substring(rider.indexOf('[') + 1, rider.indexOf(']'));
    }

    private void sayRightNow(String phrase) {
        final Phrase localPhrase = new Phrase(phrase);
        setLocale(localPhrase);
        if (mTtsIsAvailable && mTtsIsEnabled) {
            bumpUtteranceNumber();
            mTts.speak(localPhrase.text, TextToSpeech.QUEUE_FLUSH, mTtsOptions);
        }
    }

    private void sayQueued(String phrase) {
        final Phrase localPhrase = new Phrase(phrase);
        setLocale(localPhrase);
        if (mTtsIsAvailable && mTtsIsEnabled) {
            bumpUtteranceNumber();
            mTts.speak(localPhrase.text, TextToSpeech.QUEUE_ADD, mTtsOptions);
        }
    }

    private void bumpUtteranceNumber() {
        ++mLastUtteranceSubmitted;
        mTtsOptions.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                mLastUtteranceSubmitted.toString());
    }

    private void setLocale(final Phrase phrase) {
        final String phraseLocale = extractToken(phrase, "locale");
        final Locale newLocale = phraseLocale == null ? mDefaultLocale : new Locale(phraseLocale);
        if (!newLocale.equals(mLastLocale)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "changing TTS to " + phraseLocale);
            }
            mTts.setLanguage(newLocale);
            mLastLocale = newLocale;
        }
    }

    /**
     * Looks for a !command(param) string in phrase.
     *
     * @param phrase - contains string that may contain the command. If the command is found,
     *            it will be stripped out
     * @param command - the command to look for
     * @return the param or null if the command wasn't found
     */
    private String extractToken(final Phrase phrase, String command) {
        String phraseText = phrase.text;
        String param = null;
        final int startIdx = phraseText.indexOf("!" + command + "(");
        if (startIdx >= 0) {
            final int endIdx = phraseText.indexOf(')', startIdx);
            if (endIdx > 0) {
                param = phraseText.substring(startIdx + command.length() + 2, endIdx);
                phrase.text = phraseText.substring(0, startIdx) + phraseText.substring(endIdx + 1);
            }
        }
        return param;
    }

    private void welcomeRider(final String rider, int triesLeft) {
        final String message = getWelcomeString(rider);
        try {
            sayRightNow(String.format(message, firstName(rider)));
        } catch (IllegalFormatException | NullPointerException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Bad welcome message: '" + message + "'");
            }
            if (triesLeft > 0) {
                // pull an alternate message
                welcomeRider(rider, triesLeft - 1);
            } else {
                // willing to trust that resource messages won't throw an exception
                sayRightNow(String.format(getRandomResWelcome(), firstName(rider)));
            }
        }
    }

    private void returningRider(final String rider, boolean isLast, int triesLeft) {
        final String message = getReturnsString(rider, isLast);
        try {
            sayRightNow(String.format(message, firstName(rider)));
        } catch (IllegalFormatException | NullPointerException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Bad return message: '" + message + "'");
            }
            if (triesLeft > 0) {
                // pull an alternate message
                returningRider(rider, isLast, triesLeft - 1);
            } else {
                // willing to trust that resource messages won't throw an exception
                sayRightNow(String.format(getRandomResReturn(), firstName(rider)));
            }
        }
    }

    private void whoAreYou(final String rider) {
        sayRightNow(getResources().getString(R.string.who_are_you, firstName(rider)));
    }

    private void dejaVu(final String rider) {
        sayRightNow(getResources().getString(R.string.deja_vu, firstName(rider)));
    }

    private void timeToGo() {
        mTts.playSilence(500, TextToSpeech.QUEUE_ADD, null);
        String goString = RiderMessages.getGoString(RiderMessages.timeString());
        sayQueued(goString != null ? goString : getRandomResGo());
    }

    private String getRandomResGo() {
        final String[] goStrings = getResources().getStringArray(R.array.time_to_go);
        return goStrings[sRandom.nextInt(goStrings.length)];
    }

    private String getWelcomeString(final String rider) {
        final String welcomeString = RiderMessages.getWelcomeString(id(rider),
                RiderMessages.timeString());
        return welcomeString != null ? welcomeString : getRandomResWelcome();
    }

    private String getRandomResWelcome() {
        return mWelcomes[sRandom.nextInt(mWelcomes.length)];
    }

    private String getReturnsString(final String rider, final boolean isLast) {
        final String returnsString = RiderMessages.getReturnsString(id(rider),
                RiderMessages.timeString(), isLast);
        return returnsString != null ? returnsString : getRandomResReturn();
    }

    private String getRandomResReturn() {
        return mReturns[sRandom.nextInt(mReturns.length)];
    }

    /**
     * Remove rider from the manifest after adjusting the boarding order of all subsequent riders
     * @param riderToRemove
     */
    private void remove(final String riderToRemove) {
        final int removedBoardingOrder = mRideManifest.get(riderToRemove).boardingOrder;
        synchronized(mRideManifest) {
            for (final String rider: mRideManifest.keySet()) {
                final Ride ride = mRideManifest.get(rider);
                if (ride.boardingOrder > removedBoardingOrder) {
                    mRideManifest.put(rider, new Ride(ride.rider, ride.boardingOrder - 1,
                            ride.boardingTime));
                }
            }
            mRideManifest.remove(riderToRemove);
        }
    }

    /**
     * Set the rider result row label and color
     *
     * @param color
     * @param labelTextId
     */
    private void showRiderResult(final int color, final int labelTextId) {
        mLatestActionLabel.setBackgroundColor(color);
        mLatestRider.setBackgroundColor(color);
        mLatestActionLabel.setText(labelTextId);
    }

    /**
     * Clear out the rider result row at the top of the manifest screen
     */
    private void clearRiderResult() {
        mLatestActionLabel.setText("");
        mLatestActionLabel.setBackgroundColor(COLOR_BLACK);
        mLatestRider.setText("");
        mLatestRider.setBackgroundColor(COLOR_BLACK);
        invalidateOptionsMenu();
        loadMessages();
    }

    private void loadMessages() {
        // load the existing messages file, if any
        RiderMessages.readMessages();

        /**
         *  if we have a messages_url
         *  AND if the last time we successfully downloaded the messages file (messages_last_polled)
         *      is more than messages_poll_hours ago
         *  queue up a download of the messages file.
         */
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String messagesUrl = prefs.getString(SettingsActivity.PREF_MESSAGES_URL, "");
        // no URL, so nothing to download
        if (messagesUrl.isEmpty()) {
            return;
        }

        final String messagesPollHoursString = prefs.getString(
                SettingsActivity.PREF_MESSAGES_POLL_HOURS,
                SettingsActivity.PREF_MESSAGES_POLL_HOURS_DEFAULT);
        long messagesPollHours;
        try {
            messagesPollHours = Long.parseLong(messagesPollHoursString);
        } catch (final NumberFormatException e) {
            messagesPollHours = SettingsActivity.MESSAGES_POLL_HOURS_DEFAULT;
        }
        final long lastPollTime = prefs.getLong(SettingsActivity.PREF_MESSAGES_LAST_POLLED, 0);
        final long nextPollTime = lastPollTime + TimeUnit.HOURS.toMillis(messagesPollHours);
        // it's not yet time to download the messages file again
        if (System.currentTimeMillis() < nextPollTime) {
            return;
        }

        // Construct a DownloadManager.Request to retrieve the messages file
        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(messagesUrl))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        RiderMessages.MESSAGE_FILE_NAME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setMimeType("text/plain");

        // TODO: use Uri.getUserInfo() to optionally extract a basic auth user/password and create
        //  and add a suitable authentication header to the request

        mAwaitedDownloadId = mDownloadManager.enqueue(request);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "queued messages download");
        }
    }

    public static class Ride {
        final static String SEPARATOR = "|";
        final static String SEPARATOR_RE = "\\|";
        public final static String RIDER = "rider";
        public final static String ORDER = "order";
        public final static String TIME = "time";
        public final String rider;
        public final int boardingOrder;
        public final long boardingTime;

        public Ride(String rider, int boardingOrder, long boardingTime) {
            this.rider = rider;
            this.boardingOrder = boardingOrder;
            this.boardingTime = boardingTime;
        }

        public Ride(String packedRide) {
            final String[] splitRide = packedRide.split(SEPARATOR_RE);
            rider = splitRide[0];
            boardingOrder = Integer.valueOf(splitRide[1]);
            boardingTime = Long.valueOf(splitRide[2]);
        }

        public String toString() {
            return rider + SEPARATOR + boardingOrder + SEPARATOR + boardingTime;
        }

        public Map<String, String> toMap() {
            final Map<String, String> result = new HashMap<String, String>(3);
            result.put(RIDER, rider);
            result.put(ORDER, Integer.toString(boardingOrder));
            final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT,
                    Locale.getDefault());
            final Date timeDate = new Date(boardingTime);
            final String timeString = timeFormat.format(timeDate);
            result.put(TIME, timeString);
            return result;
        }
    }

    /**
     * Sort by rider name.
     * @author rschnapp
     *
     * @param <T>
     */
    public static class NameComparator<T> implements Comparator<T> {

        @Override
        public int compare(Object lhs, Object rhs) {
            Ride lhsRide = null;
            Ride rhsRide = null;
            if (lhs instanceof Ride) {
                lhsRide = (Ride) lhs;
            } else {
                return -1;
            }
            if (rhs instanceof Ride) {
                rhsRide = (Ride) rhs;
            } else {
                return 1;
            }
            return lhsRide.rider.compareTo(rhsRide.rider);
        }

    }

    /**
     * Sort by descending boarding order (most recent arrival comes first).
     * @author rschnapp
     *
     * @param <T>
     */
    public static class BoardingComparator<T> implements Comparator<T> {

        @Override
        public int compare(Object lhs, Object rhs) {
            Ride lhsRide = null;
            Ride rhsRide = null;
            if (lhs instanceof Ride) {
                lhsRide = (Ride) lhs;
            } else {
                return -1;
            }
            if (rhs instanceof Ride) {
                rhsRide = (Ride) rhs;
            } else {
                return 1;
            }
            return rhsRide.boardingOrder - lhsRide.boardingOrder;
        }

    }

    /**
     * Store app state (mode and manifest) in a file
     */
    private void saveState() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "saveState()");
            Log.d(TAG, "  isAdding=" + mIsAddingToManifest + ", manifest=" + mRideManifest);
        }
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                final String eol = System.getProperty("line.separator");
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(openFileOutput(
                            MANIFEST_STATE_FILE,
                            MODE_PRIVATE)));
                    writer.write("" + mIsAddingToManifest + eol);
                    synchronized(mRideManifest) {
                        for (final Ride ride : mRideManifest.values()) {
                            writer.write(ride.toString() + eol);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "saveState create/write failed", e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            Log.e(TAG, "saveState close failed", e);
                        }
                    }
                }
                return null;
            }

        }.execute((Void) null);
    }

    /**
     * Restore app state (mode and manifest) from a file
     */
    private void restoreState() {
        Log.d(TAG, "restoreState()");
        Log.d(TAG, "  isAdding=" + mIsAddingToManifest + ", manifest=" + mRideManifest);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(openFileInput(MANIFEST_STATE_FILE)));
            // read the "is adding" state: will be 'true' or 'false'
            String line = reader.readLine();
            mIsAddingToManifest = (line == null) || "true".equals(line);
            // read the riders
            synchronized(mRideManifest) {
                while ((line = reader.readLine()) != null) {
                    final Ride ride = new Ride(line);
                    mRideManifest.put(ride.rider, ride);
                }
            }
            invalidateOptionsMenu();
        } catch (Exception e) {
            Log.e(TAG, "restoreState open/read failed", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "restoreState close failed", e);
                }
            }
        }
    }

    private class TtsListener implements TextToSpeech.OnInitListener {

        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                mTtsOptions = new HashMap<String, String>();
                final Locale defaultLocale = Locale.getDefault();
                final int result = mTts.setLanguage(defaultLocale);
                mTtsIsAvailable = (result != TextToSpeech.LANG_MISSING_DATA) &&
                        (result != TextToSpeech.LANG_NOT_SUPPORTED);
                // Need to make enablement of network synthesis a setting. Gets complicated with
                // network connectivity coming and going.
                // final Set<String> ttsFeatures = mTts.getFeatures(defaultLocale);
                // if (ttsFeatures.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)) {
                //     mTtsOptions.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "true");
                // }
            }
        }

    }

    private class Phrase {
        public String text;

        public Phrase(final String text) {
            this.text = text;
        }
    }
}
