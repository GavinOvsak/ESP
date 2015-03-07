package workbygavin.esp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CalendarContract;
import android.text.format.DateUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

/**
 * Created by Gavin on 12/29/14.
 */
public class BootReceiver extends BroadcastReceiver {

    AlarmManager alarm;
    static Cursor cursor;
    BroadcastReceiver br;
    PendingIntent pi;



    @Override
    public void onReceive(final Context context, Intent intent) {
        alarm = (AlarmManager) (context.getSystemService(Context.ALARM_SERVICE));
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED") ||
            intent.getAction().equals("workbygavin.esp.timer")) {
            // Set the alarm here.

            Intent i = new Intent(context, NotificationListener.class);
            i.putExtra("reason", "half-hour");
            pi = PendingIntent.getService(context, 0, i, 0);

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR);
            calendar.set(Calendar.MINUTE, 50);

            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_HALF_HOUR, pi);
            System.out.println("ESP is initialized.");
            Main.timerStarted = true;
        }
    }

    class GetTask extends AsyncTask<Object, Void, String> {

        private Exception exception;
        private Function callback;

        @Override
        protected String doInBackground(Object... args) {
            try {
                URL url= new URL((String)args[0]); //Unused
                callback = (Function)args[1];

                DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
                HttpGet httpget = new HttpGet("http://api.wunderground.com/api/1b8fa5b8404ff75d/forecast/q/NC/Durham.json");
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
                    e.printStackTrace();
                }
                finally {
                    try{if(inputStream != null)inputStream.close();}catch(Exception squish){}
                }
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(String result) {
            callback.run(result);
        }
    }

    //Making functions a first class object
    interface Function {
        public void run(String arg);
    }
}
