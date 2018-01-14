/*
 * Copyright (C) 2011 The Android Open Source Project
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

package net.ipronto.nofilterisgreatvpn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class VpnClient extends Activity {
    protected MyApp mMyApp;
    TextView status_msg;
    Button btnConnect;
    Button btnDisconnect;
    ListView listView;

    private void connectToVPN() {
        Intent intent = android.net.VpnService.prepare(VpnClient.this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }

        btnConnect.setEnabled(false);
    }

    private void disconnectVPN() {
        startService(getServiceIntent().setAction(VpnService.ACTION_DISCONNECT));
        btnConnect.setEnabled(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form);

        status_msg = (TextView) findViewById(R.id.status_msg);
        btnConnect = (Button) findViewById(R.id.connect);
        btnDisconnect = (Button) findViewById(R.id.disconnect);

        mMyApp = (MyApp)this.getApplicationContext();
        mMyApp.setCurrentActivity(this);

        btnConnect.setEnabled(false);
        btnDisconnect.setEnabled(false);

        btnConnect.setOnClickListener(v -> {
            connectToVPN();
        });

        btnDisconnect.setOnClickListener(v -> {
            disconnectVPN();
        });


        listView = (ListView) findViewById(R.id.servers_list);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long arg3) {
                Log.i("VpnConnection", "Item selected: " + position);
                for (int j = 0; j < adapterView.getChildCount(); j++)
                    adapterView.getChildAt(j).setBackgroundColor(Color.TRANSPARENT);

                // change the background color of the selected element
                view.setBackgroundColor(Color.LTGRAY);
                selectedServer = servers.get(position);
            }
        });


        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    getServersList("https://s3.eu-central-1.amazonaws.com/nofiltervpn/config.json");
                } catch(Exception e) {
                    Log.e("VpnConnection", "Could not fetch servers list", e);
                }
            }
        }).start();
    }

    class ServerData {
        String address;
        int port;
        String secret;

    }

    private ArrayList<ServerData> servers;
    private ServerData selectedServer = null;

    public void getServersList(String urlString) throws IOException, JSONException {
        HttpURLConnection urlConnection = null;
        URL url = new URL(urlString);
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setReadTimeout(10000 /* milliseconds */ );
        urlConnection.setConnectTimeout(15000 /* milliseconds */ );
        urlConnection.setDoOutput(true);
        urlConnection.connect();

        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();

        String jsonString = sb.toString();
        System.out.println("JSON: " + jsonString);

        JSONObject serversData = new JSONObject(jsonString);
        JSONArray serversArr = serversData.getJSONArray("servers");
        String[] serverNames = new String[serversArr.length()];
        servers = new ArrayList<>();
        for (int i=0;i<serversArr.length();i++) {
            JSONObject serverOnj = serversArr.getJSONObject(i);
            ServerData sd = new ServerData();
            sd.address = serverOnj.getString("address");
            sd.secret = serverOnj.getString("secret");
            sd.port = serverOnj.getInt("port");

            serverNames[i] = serverOnj.getString("name");

            servers.add(sd);
        }

        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                R.layout.activity_listview, serverNames);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listView.setAdapter(adapter);

                listView.setSelection(0);
                selectedServer = servers.get(0);

                btnConnect.setEnabled(true);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            startService(getServiceIntent().setAction(VpnService.ACTION_CONNECT));
        }
    }

    private Intent getServiceIntent() {
        return new Intent(this, VpnService.class);
    }

    protected void onResume() {
        super.onResume();
        mMyApp.setCurrentActivity(this);
    }
    protected void onPause() {
        clearReferences();
        super.onPause();
    }
    protected void onDestroy() {
        clearReferences();
        super.onDestroy();
    }

    private void clearReferences(){
        Activity currActivity = mMyApp.getCurrentActivity();
        if (this.equals(currActivity))
            mMyApp.setCurrentActivity(null);
    }

    public void handleMessage(Message message) {
        status_msg.setText(message.what);

        if (message.what == R.string.disconnected) {
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
        }

        if (message.what == R.string.connected) {
            btnDisconnect.setEnabled(true);
            btnConnect.setEnabled(false);
        }
    }

    public ServerData getSelectedServer() {
        return selectedServer;
    }
}
