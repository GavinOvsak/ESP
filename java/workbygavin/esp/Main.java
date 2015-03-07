package workbygavin.esp;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;


public class Main extends Activity implements TextToSpeech.OnInitListener, AudioManager.OnAudioFocusChangeListener {

    ToggleButton espToggle;
    ToggleButton locationToggle;
    ToggleButton bedToggle;
    ToggleButton scheduleToggle;
    ImageView call;

    //HashMap<String, String> myHashAlarm = new HashMap();
    TextToSpeech tts;
    static final int VOICE_RECOGNITION_REQUEST_CODE = 5;
    Function onDeck;
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    boolean tempOverride = false;
    static boolean isESPOn = true;
    boolean ttsStarted = false;

    AudioManager am;
    AlarmManager alarm;
    BroadcastReceiver br;
    PendingIntent pi;
    static Cursor cursor;
    AudioManager.OnAudioFocusChangeListener audioListener;

    static boolean timerStarted = false;

    @Override
    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();
        if (action.equals("android.intent.action.VOICE_COMMAND")) {
            Intent i = new Intent(this, NotificationListener.class);
            i.putExtra("reason", "repeat");
            this.startService(i);
            System.out.println("Action: " + action);
        } else {
            System.out.println("Action: " + action);
        }
    }

    static final int REQUEST_CODE_PICK_ACCOUNT = 1000;

    private void pickUserAccount() {
        String[] accountTypes = new String[]{"com.google"};
        Intent intent = AccountPicker.newChooseAccountIntent(null, null,
                accountTypes, false, null, null, null, null);
        startActivityForResult(intent, REQUEST_CODE_PICK_ACCOUNT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        alarm = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));

        String action = getIntent().getAction();

//        if (mEmail == null) {
//            pickUserAccount();
//        }

        if (action.equals("android.intent.action.VOICE_COMMAND")) {
            Intent i = new Intent(this, NotificationListener.class);
            i.putExtra("reason", "repeat");
            this.startService(i);
            System.out.println("Action: " + action);
        } else {
            System.out.println("Action: " + action);
        }

        if (!timerStarted) {
            try {
                PendingIntent.getBroadcast(this, 0, new Intent("workbygavin.esp.timer"), PendingIntent.FLAG_CANCEL_CURRENT).send();
            } catch (PendingIntent.CanceledException e) {}
        }

        tts = new TextToSpeech(this, this);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);//this.getPreferences(Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        audioListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {

            }
        };

        if (!sharedPref.contains("esp")) {
            editor.putString("esp", "on");
            editor.commit();
        }

        isESPOn = sharedPref.getString("esp", "on").equals("on");

        espToggle = (ToggleButton) findViewById(R.id.voiceToggle);
        espToggle.setChecked(isESPOn);
        espToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isESPOn = espToggle.isChecked();
                editor.putString("esp", isESPOn ? "on" : "off");
                editor.commit();

                if (isESPOn) {
                    makeStatement("E S P is on", null);
                } else {
                    makeStatement("", null);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public boolean contains(String arg, String... query) {
        boolean result = false;
        for (String q : query) {
            result = result || arg.toLowerCase().indexOf(q) >= 0;
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
//        alarm.cancel(pi);
//        unregisterReceiver(br);
        am.abandonAudioFocus(audioListener);
        super.onDestroy();
    }

    @Override
    public void onInit(int code) {
        if (code == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            ttsStarted = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    if (onDeck != null && !tempOverride)
                        onDeck.run("");
                    tempOverride = false;
                }

                @Override
                public void onError(String utteranceId) {
                }
            });
            if (onDeck != null) {
                onDeck.run("");
            }
        } else {
            tts = null;
            Toast.makeText(getApplicationContext(), "Failed to initialize TTS engine.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    String token;
    String firstTask;
    String mEmail; // Received from newChooseAccountIntent(); passed to getToken()
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.profile";
    private static final String SCOPE2 = "oauth2:https://www.googleapis.com/auth/tasks";


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> thingsYouSaid = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            Toast.makeText(this, thingsYouSaid.get(0), Toast.LENGTH_LONG).show();
            if (onDeck != null)
                onDeck.run(thingsYouSaid.get(0));
        }

        if (requestCode == REQUEST_CODE_PICK_ACCOUNT) {
            // Receiving a result from the AccountPicker
            if (resultCode == RESULT_OK) {
                mEmail = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                // With the account name acquired, go get the auth token
                //getTaskList();
//                getUsername();
            } else if (resultCode == RESULT_CANCELED) {
                // The account picker dialog closed without selecting an account.
                // Notify users that they must pick an account to proceed.
                Toast.makeText(this, "Pick Account", Toast.LENGTH_SHORT).show();
            }
        } else if ((requestCode == REQUEST_CODE_RECOVER_FROM_AUTH_ERROR ||
                requestCode == REQUEST_CODE_RECOVER_FROM_PLAY_SERVICES_ERROR)
                && resultCode == RESULT_OK) {
            // Receiving a result that follows a GoogleAuthException, try auth again
            //getTaskList();
//            getUsername();
        }
        // Later, more code will go here to handle the result from some exceptions...
    }

    static final int REQUEST_CODE_RECOVER_FROM_PLAY_SERVICES_ERROR = 1001;
    static final int REQUEST_CODE_RECOVER_FROM_AUTH_ERROR = 1003;

    /**
     * This method is a hook for background threads and async tasks that need to
     * provide the user a response UI when an exception occurs.
     */
    public void handleException(final Exception e) {
        // Because this call comes from the AsyncTask, we must ensure that the following
        // code instead executes on the UI thread.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (e instanceof GooglePlayServicesAvailabilityException) {
                    // The Google Play services APK is old, disabled, or not present.
                    // Show a dialog created by Google Play services that allows
                    // the user to update the APK
                    int statusCode = ((GooglePlayServicesAvailabilityException)e)
                            .getConnectionStatusCode();
                    Dialog dialog = GooglePlayServicesUtil.getErrorDialog(statusCode,
                            Main.this,
                            REQUEST_CODE_RECOVER_FROM_PLAY_SERVICES_ERROR);
                    dialog.show();
                } else if (e instanceof UserRecoverableAuthException) {
                    // Unable to authenticate, such as when the user has not yet granted
                    // the app access to the account, but the user can fix this.
                    // Forward the user to an activity in Google Play services.
                    Intent intent = ((UserRecoverableAuthException)e).getIntent();
                    startActivityForResult(intent,
                            REQUEST_CODE_RECOVER_FROM_PLAY_SERVICES_ERROR);
                }
            }
        });
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

    }

    //Making functions a first class object
    interface Function {
        public void run(String arg);
    }



    public void askQuestion(final String question, final Function next) {
        makeStatement(question, new Function() {
            @Override
            public void run(String arg) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, question);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
                onDeck = next;
            }
        });
    }

    public void makeStatement(final String statement, final Function next) {
        if (tts != null && ttsStarted) {
            if (tts.isSpeaking()) {
                tempOverride = true;
            }
            tts.speak(statement, TextToSpeech.QUEUE_FLUSH, null, "statement");
            onDeck = next;
        } else {
            onDeck = new Function() {
                @Override
                public void run(String arg) {
                    makeStatement(statement, next);
                }
            };
        }
    }



}
