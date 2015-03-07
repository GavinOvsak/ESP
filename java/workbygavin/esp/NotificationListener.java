package workbygavin.esp;

/**
 * Created by Gavin on 12/29/14.
 */
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import
        android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

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
import java.util.Calendar;
import java.util.Locale;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener, AudioManager.OnAudioFocusChangeListener {

    TextToSpeech tts;
    Function onDeck;

    boolean tempOverride = false;
    boolean ttsStarted = false;

    AudioManager am;
    AlarmManager alarm;
    AudioManager.OnAudioFocusChangeListener audioListener;
    SharedPreferences preferences;
    int hangoutsSkip = 0;
    long lastPress = 0;
    String[] recents = new String[20];
    int stackIndex = 0;
    String lastSong = "";
    boolean speakingNotification = false;

    String[][] replacements = {{":/", ". depressed face"}, {"Ovsak", "Oh shock"}, {"Bracht", "Brockt"}};

    public String getSpokenTime() {
        Calendar c = Calendar.getInstance();

        int hour = c.get(Calendar.HOUR);
        int minutes = c.get(Calendar.MINUTE);
        String ampm = (c.get(Calendar.AM_PM) == 0) ? "AM" : "PM";

        String min = "" + minutes;
        if (minutes == 0) {
            min = "";
        } else if (minutes < 10) {
            min = "O " + minutes;
        }

        if (hour == 0) {
            hour = 12;
        }

        return "The time is: " + hour + " " + min + " " + ampm;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        buttonClicked(intent);
        return START_STICKY;
    }

    boolean useGoogleTasks = false;
    public void buttonClicked(Intent intent) {
        if (intent != null) {
            String reason = intent.getStringExtra("reason");
            String message = intent.getStringExtra("message");
            String action = intent.getAction();

            if (reason != null && reason.equals("half-hour")) {
                //Notify about event
                if (preferences.getString("esp", "on").equals("on") && (am.isBluetoothA2dpOn() || am.isWiredHeadsetOn())) {
                    //Get real time.
                    if (isNetworkAvailable()) {


                        if (useGoogleTasks) {
                            new GetTaskListTask("ovsak.gavin@gmail.com", SCOPE2).execute(new Function() {
                                @Override
                                public void run(String arg) {
                                    if (arg.length() > 0) {
                                        String message = "your first task is " + arg;
                                        makeStatement(message + " and " + getSpokenTime());
                                    } else {
                                        makeStatement(getSpokenTime());
                                    }
                                }
                            });
                        } else {
                            makeStatement(getSpokenTime());
                        }

                    } else {
                        makeStatement(getSpokenTime());
                    }
                }
            } else if (reason != null && reason.equals("repeat")) {
                //Say history

                Calendar c = Calendar.getInstance();

                if (tts.isSpeaking() && speakingNotification) {
                    //Shut up notification
                    overrideStatement("");
                } else {
                    if (lastPress + 1000 * 15 < c.getTime().getTime()) {
                        //If it has been a while since the last press, tell the time on the first press as well as what music is playing.
                        //"It is 3 pm and this song is Sandstorm by Darude"
                        //Reset stack index.

                        if (useGoogleTasks && isNetworkAvailable()) {
                            new GetTaskListTask("ovsak.gavin@gmail.com", SCOPE2).execute(new Function() {
                                @Override
                                public void run(String arg) {
                                    if (arg.length() > 0) {
                                        String message = "your first task is " + arg;

                                        if (am.isMusicActive() && lastSong.length() > 0) {
                                            overrideStatement("This song is " + lastSong + " and " + message + " and " + getSpokenTime());
                                        } else {
                                            overrideStatement(message + " and " + getSpokenTime());
                                        }
                                    } else {
                                        if (am.isMusicActive()) {
                                            overrideStatement("This song is " + lastSong + " and " + getSpokenTime());
                                        } else {
                                            overrideStatement(getSpokenTime());
                                        }
                                    }
                                }
                            });
                        } else {
                            if (am.isMusicActive()) {
                                overrideStatement("This song is " + lastSong + " and " + getSpokenTime());
                            } else {
                                overrideStatement(getSpokenTime());
                            }
                        }

                        stackIndex = 0;
                    } else {
                        //Else, read the top of the stack and increment the index.
                        //If index is outside of range, reset index
                        if (stackIndex < recents.length) {
                            if (recents[stackIndex] == null) {
                                overrideStatement("No more notifications");
                                stackIndex = 0;
                            } else {
                                overrideStatement(recents[stackIndex]);
                                stackIndex++;
                            }
                        } else {
                            overrideStatement("No more notifications");
                            stackIndex = 0;
                        }
                    }

                    lastPress = c.getTime().getTime();
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        alarm = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));

        tts = new TextToSpeech(this, this);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        audioListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {

            }
        };

        //makeStatement("Hey there", null);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn) {
//        Notification notific = sbn.getNotification();


        //"Genevieve Ovsak"
        //""
        //"Genevieve Ovsak: text 7"

        //---show current notification---
        Log.i("","---Current Notification---");
        Log.i("","ID :" + sbn.getId() + "\t" +
                sbn.getNotification().tickerText + "\t" +
                sbn.getPackageName() + "\t" +
                sbn.getNotification().extras.getString("android.title","") + "\t" +
                sbn.getNotification().extras.getString("android.text",""));
        Log.i("","--------------------------");

        //---show all active notifications---
        Log.i("","===All Notifications===");
        for (StatusBarNotification notif : this.getActiveNotifications()) {

            boolean isLatest = notif.getNotification().when == sbn.getNotification().when;

            String packageName = notif.getPackageName();
            String title = "" + notif.getNotification().extras.getString(Notification.EXTRA_TITLE, "");
//            String text = notif.getNotification().extras.getString("android.text","");
            String text = "" + notif.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT, "");
            String ticker = "" + notif.getNotification().tickerText;

            Log.i("","ID :" + notif.getId() + "\t" + ticker + "\t" + packageName + "\t" + title + "\t" + text);

            if (packageName.equals("com.google.android.youtube")) {
                lastSong = ticker;
            }

            if (packageName.equals("com.google.android.music")) {
                lastSong = title + " by " + text;
            }

            if (preferences.getString("esp", "on").equals("on") && (am.isBluetoothA2dpOn() || am.isWiredHeadsetOn())) {
                if (packageName.equals("com.google.android.talk")) {

                    //Tough case:
                    //title: 4 new messages
                    //text: TEDxDuke 2015 Exec, Dianwen Li, Bianca Bracht
                    //ticker: TEDxDuke 2015 Exec: Dianwen  test

                    //title: TEDxDuke 2015 Exec
                    //text: Dianwen  test
                    //ticker: Dianwen Li: test

                    if (!title.contains("new messages") && !ticker.equals("null")) {
                        String message = removeCamelcase(removeLinks(replaceWords(ticker)));

                        if (notRecent(ticker) && ticker.length() > 0 && isLatest) {
                            makeStatement(message);
                            addToRecent(ticker);
                        }
                    }
                } else if (packageName.equals("com.twitter.android")) {

                    //maybe don't notify anything containing "RT" in caps
                    String message = title + ". " + removeCamelcase(removeLinks(text));
                    if (notRecent(message) && (title.length() > 0 || text.length() > 0)) {
                        makeStatement(message);
                        addToRecent(message);
                    }

                } else if (packageName.equals("com.google.android.apps.inbox")) {

                    //maybe don't notify anything containing "RT" in caps
                    String message = "Email from " + title + ". " + removeCamelcase(removeLinks(text));
                    if (notRecent(message) && (title.length() > 0 || text.length() > 0)) {
                        makeStatement(message);
                        addToRecent(message);
                    }

                } else if (packageName.equals("com.cnn.mobile.android.phone")) {

                    String message = title + ". " + removeCamelcase(removeLinks(text));
                    if (title.length() > 0 || text.length() > 0) {
                        makeStatement(message);
                        addToRecent(message);
                    }
                    this.cancelNotification(notif.getKey());

                } else if (packageName.equals("com.ifttt.ifttt")) {

                    String message = title + ". " + removeCamelcase(removeLinks(text));
                    if (title.length() > 0 || text.length() > 0) {
                        makeStatement(message);
                        addToRecent(message);
                    }
//                    this.cancelNotification(notif.getKey());

                } else if (packageName.equals("com.google.android.googlequicksearchbox")) {
                    if (!contains(title, "Time to") && title.length() > 0 && isLatest) {
                        makeStatement(title);
                        addToRecent(title);
                    }
                } else if (packageName.equals("com.facebook.orca")) {
                    String message = title + ". " + removeCamelcase(removeLinks(replaceWords(text)));
                    if (!title.equals("Chat heads active") && notRecent(message) && (title.length() > 0 || text.length() > 0) && isLatest) {
                        makeStatement(message);
                        addToRecent(message);
                    }
                }
            }

            //Get rid of all of no matter what.
            if (packageName.equals("com.twitter.android") || packageName.equals("com.ifttt.ifttt")) {
                this.cancelNotification(notif.getKey());
            }

        }
//        Log.i("","=======================");

    }

    public boolean contains(String arg, String... query) {
        boolean result = false;
        for (String q : query) {
            result = result || arg.toLowerCase().indexOf(q) >= 0;
        }
        return result;
    }

    public boolean notRecent(String input) {
        for (int i = 0; i < recents.length; i++) {
            if (recents[i] != null && recents[i].indexOf(input) >= 0) {
                return false;
            }
        }
        return true;
    }

    public String replaceWords(String input) {
        String output = input;

        //go through each pair in replacements, replace all instances.
        //check and see these are unique words "(^|\\W|$)"
        for (int i = 0; i < replacements.length; i++) {
            output = output.replaceAll("(^|\\W|$)" + replacements[i][0] + "(^|\\W|$)", " " + replacements[i][1] + " ");
        }

        return output;
    }

    public void addToRecent(String input) {
        for (int i = recents.length - 1; i > 0; i--) {
            recents[i] = recents[i - 1];
        }
        recents[0] = input;
    }

    public void removeFromRecent(String input) {
        int shift = 0;
        for (int i = 0; i < recents.length; i++) {
            if (i + shift < recents.length) {
                if (recents[i + shift] != null && recents[i + shift].indexOf(input) >= 0) {
                    shift++;
                }
                recents[i] = recents[i + shift];
            } else {
                recents[i] = null;
            }
        }
    }

    public String removeLinks(String input) {
        //Find "http", then find next instance of a space or end. remove between. Repeat until no http

        String removed = input;
        int firstHttp = input.indexOf("http");
        if (firstHttp >= 0) {
            int endHttp = input.indexOf(" ", firstHttp);
            if (endHttp >= 0) {
                removed = input.substring(0, firstHttp) + ". link. " + input.substring(endHttp, input.length());
            } else {
                removed = input.substring(0, firstHttp) + ". link. ";
            }
        }

        if (removed.indexOf("http") >= 0)
            return removeLinks(removed);
        return removed;
    }

    String lower = "abcdefghijklmnopqrstuvwxyz";
    String upper = "ABCDEFGHILKLMNOPQRSTUVWXYZ";

    public String removeCamelcase(String input) {
        String output = "";

        for (int i = 0; i < input.length(); i++) {
            if (i + 1 < input.length()) {
                String l1 = ""+input.charAt(i);
                String l2 = ""+input.charAt(i + 1);
                if (lower.indexOf(l1) >= 0 && upper.indexOf(l2) >= 0) {
                    output += input.charAt(i) + " ";
                } else {
                    output += input.charAt(i);
                }
            } else {
                output += input.charAt(i);
            }
        }

        return output;
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn) {
        Log.i("","---Notification Removed---");
        Log.i("","ID :" + sbn.getId() + "\t" +
                sbn.getNotification().tickerText + "\t" +
                sbn.getPackageName());
        Log.i("","--------------------------");

    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

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
                    speakingNotification = false;
                    if (!tempOverride)
                        am.abandonAudioFocus(audioListener);
                    tempOverride = false;
                    if (onDeck != null && !tempOverride)
                        onDeck.run("");
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
    public void onAudioFocusChange(int focusChange) {

    }

    //Making functions a first class object
    interface Function {
        public void run(String arg);
    }

    public void overrideStatement(final String statement) {
        if (tts != null && ttsStarted) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (tts.isSpeaking()) {
                tempOverride = true;
            }
            speakingNotification = false;
            am.requestAudioFocus(audioListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            tts.speak(statement, TextToSpeech.QUEUE_FLUSH, null, "statement");
            onDeck = null;
            //onDeck = next;
        } else {
            onDeck = new Function() {
                @Override
                public void run(String arg) {
                    makeStatement(statement);
                }
            };
        }
    }

    public void makeStatement(final String statement) {
        if (tts != null && ttsStarted) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (tts.isSpeaking()) {
                tempOverride = true;
            }
            speakingNotification = true;
            am.requestAudioFocus(audioListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            tts.speak(statement, TextToSpeech.QUEUE_ADD, null, "statement");
            onDeck = null;
            //onDeck = next;
        } else {
            onDeck = new Function() {
                @Override
                public void run(String arg) {
                    makeStatement(statement);
                }
            };
        }
    }

    private static final String SCOPE2 = "oauth2:https://www.googleapis.com/auth/tasks";
    String token = "";
    String firstTask = "";

    public class GetTaskListTask extends AsyncTask {
        //Activity mActivity;
        String mScope;
        String mEmail;
        private Function callback;

        GetTaskListTask(String name, String scope) {
            this.mScope = scope;
            this.mEmail = name;
        }

        /**
         * Gets an authentication token from Google and handles any
         * GoogleAuthException that may occur.
         */
        protected String fetchToken() throws IOException {
            try {
                return GoogleAuthUtil.getToken(NotificationListener.this, mEmail, mScope);
            } catch (UserRecoverableAuthException userRecoverableException) {
                // GooglePlayServices.apk is either old, disabled, or not present
                // so we need to show the user some UI in the activity to recover.
                //mActivity.handleException(userRecoverableException);
            } catch (GoogleAuthException fatalException) {
                // Some other type of unrecoverable exception has occurred.
                // Report and log the error as appropriate for your app.
            }
            return null;
        }

        /**
         * Executes the asynchronous job. This runs when you call execute()
         * on the AsyncTask instance.
         */
        @Override
        protected Object doInBackground(Object[] params) {
            try {
                token = fetchToken();
                callback = (Function)params[0];

                if (token != null) {
                    String url = "https://www.googleapis.com/tasks/v1/lists/@default/tasks?access_token=" + token;

                    new GetTask().execute(url, new Function(){
                        @Override
                        public void run(String arg) {
                            JSONObject jObject = null;
                            System.out.println("Is response null " + (arg == null));
                            if (arg != null) {
                                try {
                                    jObject = new JSONObject(arg);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                if (jObject != null) {
                                    try {
                                        JSONArray tasks = (JSONArray) jObject.get("items");
                                        JSONObject first = tasks.getJSONObject(0);
                                        firstTask = first.getString("title");
                                        callback.run(firstTask);
                                    } catch (JSONException e) {}
                                }
                            }
                        }
                    });

                    // Insert the good stuff here.
                    // Use the token to access the user's Google data.
                }
            } catch (IOException e) {
                // The fetchToken() method handles Google-specific exceptions,
                // so this indicates something went wrong at a higher level.
                // TIP: Check for network connectivity before starting the AsyncTask.
                if (callback != null) {
                    callback.run("");
                }
            }
            return null;
        }
    }

    class GetTask extends AsyncTask<Object, Void, String> {

        private Exception exception;
        private Function callback;

        @Override
        protected String doInBackground(Object... args) {
            try {
                String url= (String)args[0];
                callback = (Function)args[1];

                DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
                HttpGet httpget = new HttpGet(url);//"http://api.wunderground.com/api/1b8fa5b8404ff75d/forecast/q/NC/Durham.json");
                // Depends on your web service
                httpget.setHeader("Content-type", "application/json");

                InputStream inputStream = null;
                String result = null;
                try {
                    HttpResponse response = httpclient.execute(httpget);
                    HttpEntity entity = response.getEntity();

                    inputStream = entity.getContent();
                    // json is UTF-8 by default
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
                    StringBuilder sb = new StringBuilder();

                    String line = null;
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line + "\n");
                    }
                    result = sb.toString();
                    return result;
                } catch (Exception e) {
                    // Oops
                    callback.run("");
                    System.out.println("");
                }
                finally {
                    try{if(inputStream != null)inputStream.close();}catch(Exception squish){}
                }
                return null;
            } catch (Exception e) {
                this.exception = e;
                return null;
            }
        }

        protected void onPostExecute(String result) {
            callback.run(result);
        }
    }

}