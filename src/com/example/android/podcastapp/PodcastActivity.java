/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.podcastapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Scanner;

public class PodcastActivity extends Activity {
    private static Context context;
    public static final String WIFI = "Wi-Fi";
    public static final String ANY = "Any";
    private static final String URL =
            "https://itunes.apple.com/search?term=greg+and+the+morning+buzz&limit=25&media=podcast";

    // Whether there is a Wi-Fi connection.
    private static boolean wifiConnected = false;
    // Whether there is a mobile connection.
    private static boolean mobileConnected = false;
    // Whether the display should be refreshed.
    public static boolean refreshDisplay = true;

    // The user's current network preference setting.
    public static String sPref = null;

    // The BroadcastReceiver that tracks network connectivity changes.
    private NetworkReceiver receiver = new NetworkReceiver();

    private TextView text;
    private EditText search;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //search = (EditText)findViewById(R.id.search);
        // Register BroadcastReceiver to track connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver();
        this.registerReceiver(receiver, filter);
        PodcastActivity.context = getApplicationContext();
        /**
         * adding a handler for catching crashes, for debugging purposes.
         */
        Thread.UncaughtExceptionHandler mUEhandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Log.d("test",throwable.getMessage());
                Log.e("test","",throwable);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(mUEhandler);
        //new XMLParser().execute("http://www.wheb.com/podcast/themorningbuzz.xml");
    }

    public void onClick(View view){
        switch (view.getId()){
            case R.id.to_search_button:
                Intent searchActivity = new Intent(getBaseContext(), SearchActivity.class);
                startActivity(searchActivity);
            break;
        }
    }

    // Refreshes the display if the network connection and the
    // pref settings allow it.
    @Override
    public void onStart() {
        super.onStart();

        // Gets the user's network preference settings
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Retrieves a string value for the preferences. The second parameter
        // is the default value to use if a preference value is not found.
        sPref = sharedPrefs.getString("listPref", "Wi-Fi");
        updateConnectedFlags();
        loadPage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            this.unregisterReceiver(receiver);
        }
    }

    public static Context getAppContext() {
        return context;
    }

    // Checks the network connection and sets the wifiConnected and mobileConnected
    // variables accordingly.
    private void updateConnectedFlags() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            wifiConnected = activeInfo.getType() == ConnectivityManager.TYPE_WIFI;
            mobileConnected = activeInfo.getType() == ConnectivityManager.TYPE_MOBILE;
        } else {
            wifiConnected = false;
            mobileConnected = false;
        }
    }

    // Uses AsyncTask subclass to download the XML feed from stackoverflow.com.
    // This avoids UI lock up. To prevent network operations from
    // causing a delay that results in a poor user experience, always perform
    // network operations on a separate thread from the UI.
    private void loadPage() {
        if ( ((sPref.equals(ANY)) && (wifiConnected || mobileConnected))
                || ((sPref.equals(WIFI)) && (wifiConnected))) {
            new DownloadPodcastTask().execute(URL);
        } else {
            showErrorPage();
        }
    }

    // Displays an error if the app is unable to load content.
    private void showErrorPage() {
        setContentView(R.layout.main);

        // The specified network connection is not available. Displays error message.
        //WebView myWebView = (WebView) findViewById(R.id.webview);
        //myWebView.loadData(getResources().getString(R.string.connection_error),
        //        "text/html", null);
    }

    // Populates the activity's options menu.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    // Handles the user's menu selection.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
                Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
                startActivity(settingsActivity);
                return true;
        case R.id.refresh:
                loadPage();
                return true;
        default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Implementation of AsyncTask used to download XML feed.
    private class DownloadPodcastTask extends AsyncTask<String, Void, Values> {

        @Override
        protected Values doInBackground(String... urls) {
            try {
                return loadXmlFromNetwork(urls[0]);
            } catch (IOException e) {
                return new Values( null, getResources().getString(R.string.connection_error) );
            }
        }

        @Override
        protected void onPostExecute(Values values) {
            Bitmap myBitmap = BitmapFactory.decodeFile(values.podcast.getArtworkPath("30"));
            try{
                //ImageView imageview = (ImageView) findViewById(R.id.imagethumbnail);
                //imageview.setImageBitmap(myBitmap);
            }catch(Exception ex){
                Log.e("test","lol",ex);
            }

        }
    }
    private class Values {
        protected Podcast podcast;
        protected String htmlString;
        public Values(Podcast p, String html){
            podcast = p;
            htmlString = html;
        }
    }

    // Uploads XML from stackoverflow.com, parses it, and combines it with
    // HTML markup. Returns HTML string.
    private Values loadXmlFromNetwork(String urlString) throws IOException {
        InputStream stream = null;
        JSONParser podcastUrlParser = new JSONParser();
        Podcast podcast = null;
        Calendar rightNow = Calendar.getInstance();
        DateFormat formatter = new SimpleDateFormat("MMM dd h:mmaa");

        // Checks whether the user set the preference to include summary text
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pref = sharedPrefs.getBoolean("summaryPref", false);

        StringBuilder htmlString = new StringBuilder();
        htmlString.append("<h3>" + getResources().getString(R.string.page_title) + "</h3>");
        htmlString.append("<em>" + getResources().getString(R.string.updated) + " " +
                formatter.format(rightNow.getTime()) + "</em>");

        try {
            stream = downloadUrl(urlString);
            Scanner scan = new Scanner(stream);
            StringBuilder result = new StringBuilder();
            while( scan.hasNextLine() ) {
                String line = scan.nextLine();
                result.append( line );
            }
            JSONObject json = new JSONObject(result.toString());
            JSONObject info = json.getJSONArray("results").getJSONObject(0);
            podcast = podcastUrlParser.toPodcastObj(info);
        // Makes sure that the InputStream is closed after the app is
        // finished using it.
        } catch (JSONException e) {
            Log.e("test", "", e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return new Values(podcast,htmlString.toString());
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an input stream.
    private InputStream downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // Starts the query
        conn.connect();
        InputStream stream = conn.getInputStream();
        return stream;
    }

    /**
     *
     * This BroadcastReceiver intercepts the android.net.ConnectivityManager.CONNECTIVITY_ACTION,
     * which indicates a connection change. It checks whether the type is TYPE_WIFI.
     * If it is, it checks whether Wi-Fi is connected and sets the wifiConnected flag in the
     * main activity accordingly.
     *
     */
    public class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connMgr =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

            // Checks the user prefs and the network connection. Based on the result, decides
            // whether
            // to refresh the display or keep the current display.
            // If the userpref is Wi-Fi only, checks to see if the device has a Wi-Fi connection.
            if (WIFI.equals(sPref) && networkInfo != null
                    && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                // If device has its Wi-Fi connection, sets refreshDisplay
                // to true. This causes the display to be refreshed when the user
                // returns to the app.
                refreshDisplay = true;
                Toast.makeText(context, R.string.wifi_connected, Toast.LENGTH_SHORT).show();

                // If the setting is ANY network and there is a network connection
                // (which by process of elimination would be mobile), sets refreshDisplay to true.
            } else if (ANY.equals(sPref) && networkInfo != null) {
                refreshDisplay = true;

                // Otherwise, the app can't download content--either because there is no network
                // connection (mobile or Wi-Fi), or because the pref setting is WIFI, and there
                // is no Wi-Fi connection.
                // Sets refreshDisplay to false.
            } else {
                refreshDisplay = false;
                Toast.makeText(context, R.string.lost_connection, Toast.LENGTH_SHORT).show();
            }
        }
    }
}