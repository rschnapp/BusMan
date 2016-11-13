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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Derived from "collectable cards/top trumps" NFC demo
 * by Richard Leggett http://www.richardleggett.co.uk
 */
public class ConfigureTagActivity extends Activity implements OnClickListener {

    private static final String TAG = "BusMan";
    public static final String KEY_NFC_RIDER = "nfcrider";
    public static final String ID_SEPARATOR = "@";

    private NfcAdapter mNfcAdapter;
    private Button mWriteTagButton;
    private TextView mResultTextOutput;
    private EditText mRiderIdInput;
    private EditText mRiderNameInput;
    private boolean mWaitingToWriteNfc;
    private String mRiderId;
    private String mRiderName;
    private boolean mMakeReadOnly;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.configure_tag_activity);

        // grab our NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // button that starts the tag-write procedure
        mWriteTagButton = (Button)findViewById(R.id.write_tag_button);
        mWriteTagButton.setOnClickListener(this);

        // TextView that we'll use to output messages to screen
        mResultTextOutput = (TextView) findViewById(R.id.text_view);

        // EditText fields to receive rider inputs
        mRiderIdInput = (EditText) findViewById(R.id.rider_id);
        mRiderNameInput = (EditText) findViewById(R.id.rider_name);

        final Intent intent = getIntent();
        final String nfcRiderText = intent.getStringExtra(KEY_NFC_RIDER);
        if (nfcRiderText != null) {
            final int breakingPoint = nfcRiderText.indexOf('@');
            mRiderIdInput.setText(nfcRiderText.substring(0, breakingPoint));
            mRiderNameInput.setText(nfcRiderText.substring(breakingPoint + 1));
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu_configure, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_settings_too:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.option_about:
                showAboutDialog(this);
                return true;

            case R.id.option_manifest:
                startActivity(new Intent(this, ManifestActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
            }
    }

    public static void showAboutDialog(final Context context) {
        String message = "";
        try {
            message = context.getResources().getString(R.string.about_this_app_message,
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .versionName);
        } catch (NotFoundException e) {
        } catch (NameNotFoundException e) {
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.about_this_app_title)
                .setMessage(message)
                .show();
    }

    public void onClick(View v) {
        if(v.getId() == R.id.write_tag_button) {
            mRiderId = mRiderIdInput.getText().toString();
            mRiderName = mRiderNameInput.getText().toString();
            mMakeReadOnly = ((CheckBox) findViewById(R.id.configure_write_only)).isChecked();

            if (mRiderId != null && !mRiderId.isEmpty()
                    && mRiderName != null && !mRiderName.isEmpty()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "onClick(), id=" + mRiderId + ", name=" + mRiderName);
                }
                displayMessage(R.string.msg_swipe_new_tag);
                writeNfcWhenItAppears();
            } else {
                displayMessage(R.string.msg_enter_both_fields);
            }
        }
    }

    private void maybeEnableForegroundDispatch() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "maybeEnableForegroundDispatch(), mWaitingToWriteNfc=" + mWaitingToWriteNfc);
        }
        if (mWaitingToWriteNfc) {
            // set up a PendingIntent to open the app when a tag is scanned
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            IntentFilter[] filters = new IntentFilter[] { tagDetected };

            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
        }
    }

    private void maybeDisableForegroundDispatch() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "maybeDisableForegroundDispatch(), mWaitingToWriteNfc=" +
                    mWaitingToWriteNfc);
        }
        if (mWaitingToWriteNfc) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPause(), mWaitingToWriteNfc=" + mWaitingToWriteNfc);
        }
        maybeDisableForegroundDispatch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onResume(), mWaitingToWriteNfc=" + mWaitingToWriteNfc);
        }
        maybeEnableForegroundDispatch();
    }

    /**
     * Called when our blank tag is scanned executing the PendingIntent
     */
    @Override
    public void onNewIntent(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onNewIntent(), mWaitingToWriteNfc=" + mWaitingToWriteNfc);
        }
        // Shouldn't I verify that intent is what I'm expecting?
        if (mWaitingToWriteNfc) {
            mWaitingToWriteNfc = false;

            // write to newly scanned tag
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTagInBackground(tag);
        }
    }

    /**
     * Force this Activity to get NFC events first
     */
    private void writeNfcWhenItAppears() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "writeNfcWhenItAppears()");
        }
        mWaitingToWriteNfc = true;

        maybeEnableForegroundDispatch();
    }

    private void writeTagInBackground(final Tag tag) {
        new AsyncTask<Tag, Integer, Integer>() {

            @Override
            protected Integer doInBackground(Tag... params) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "wtib.doInBackground(), tag=" + params[0]);
                }
                publishProgress(R.string.msg_writing_new_tag);
                return writeTag(params[0]);
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "wtib.onProgressUpdate(), progress=" + progress[0]);
                }
                displayMessage(progress[0]);
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "wtib.onPostExecute(), result=" + result);
                }
                if (result == R.string.msg_result_success) {
                    mRiderIdInput.setText("");
                    mRiderNameInput.setText("");
                }
                displayMessage(result);
            }

        }.execute(tag);
    }

    /**
     * Format a tag and write our NDEF message
     * @param tag - the NFC tag info
     * @return resource id of string that explains the outcome
     */
    private int writeTag(Tag tag) {
        final String packageName = getPackageName();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "writeTag(), tag=" + tag + ", pkg=" + packageName);
        }
        // record to launch Play Store if app is not installed
        NdefRecord appRecord = NdefRecord.createApplicationRecord(getPackageName());

        // package up the id and name; assign our MIME_TYPE
        final String idAndName = packIdAndName();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "  writeTag(), idAndName=" + idAndName);
        }
        byte[] payload = idAndName.getBytes();
        byte[] mimeBytes = MimeType.BUSMAN_MIMETYPE.getBytes(Charset.forName("US-ASCII"));
        NdefRecord cardRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes,
                                                new byte[0], payload);
        NdefMessage message = new NdefMessage(new NdefRecord[] { cardRecord, appRecord});

        try {
            // see if tag is already NDEF formatted
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();

                if (!ndef.isWritable()) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "writeTag result: Read-only tag.");
                    }
                    return R.string.msg_result_error_read_only;
                }

                // work out how much space we need for the data
                int size = message.toByteArray().length;
                if (ndef.getMaxSize() < size) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "writeTag result: Tag doesn't have enough free space.");
                    }
                    return R.string.msg_result_error_tag_too_small;
                }

                if (mMakeReadOnly && !ndef.canMakeReadOnly()) {
                    Log.e(TAG, "writeTag result: Requested read-only, but tag isn't capable");
                    return R.string.msg_result_cant_make_read_only;
                }

                ndef.writeNdefMessage(message);
                if (mMakeReadOnly) {
                    ndef.makeReadOnly();
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "writeTag result: Tag written successfully.");
                }
                return R.string.msg_result_success;
            } else {
                // attempt to format tag
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "writeTag result: Tag written successfully!\n"
                                    + "Close this app and scan tag.");
                        }
                        return R.string.msg_result_success_formatted;
                    } catch (IOException e) {
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            Log.e(TAG, "writeTag result: Unable to format tag to NDEF.");
                        }
                        return R.string.msg_result_error_cant_format_tag;
                    }
                } else {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "writeTag result: Tag doesn't appear to support NDEF format.");
                    }
                    return R.string.msg_result_error_not_ndef_tag;
                }
            }
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "writeTag result: Failed to write tag");
            }
        }

        return R.string.msg_result_error_cant_write_tag;
    }

    // TODO: use JSON instead of ad hoc?
    private String packIdAndName() {
        // trim any domain name from the id
        final String id = mRiderId.replaceFirst(ID_SEPARATOR + ".*", "");
        return id + ID_SEPARATOR + mRiderName;
    }

    private void displayMessage(int stringResourceId) {
        displayMessage(getApplicationContext().getResources().getString(stringResourceId));
    }

    private void displayMessage(String message) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "displayMessage: " + message);
        }
        mResultTextOutput.setText(message);
    }
}