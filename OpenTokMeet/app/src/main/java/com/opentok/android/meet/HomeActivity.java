package com.opentok.android.meet;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import meet.android.opentok.com.opentokmeet.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


public class HomeActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private static final String LOGTAG = "meet.tokbox";

    private static final String LAST_CONFERENCE_DATA = "LAST_CONFERENCE_DATA";

    private String roomName;
    private String username;
    private EditText roomNameInput;
    private EditText usernameInput;
    private Spinner mCapturerResolutionSpinner;
    private String mCapturerResolution;

    private Spinner mCapturerFpsSpinner;
    private String mCapturerFps;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //restore last used conference data
        restoreConferenceData();

        setContentView(R.layout.main_layout);

        roomNameInput = (EditText) findViewById(R.id.input_room_name);
        roomNameInput.setText(this.roomName);

        usernameInput = (EditText) findViewById(R.id.input_username);
        usernameInput.setText(this.username);


        mCapturerResolutionSpinner = (Spinner) findViewById(R.id.combo_capturer_resolution);
        mCapturerResolutionSpinner.setOnItemSelectedListener(this);
        String[] capturerResolutionValues  = getResources().getStringArray(R.array.pub_capturer_resolution);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, R.layout.simple_spinner_item, capturerResolutionValues);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCapturerResolutionSpinner.setAdapter(dataAdapter);

        mCapturerFpsSpinner = (Spinner) findViewById(R.id.combo_capturer_fps);
        mCapturerFpsSpinner.setOnItemSelectedListener(this);
        String[] capturerFpsValues  = getResources().getStringArray(R.array.pub_capturer_fps);
        dataAdapter = new ArrayAdapter<String>(this, R.layout.simple_spinner_item, capturerFpsValues);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCapturerFpsSpinner.setAdapter(dataAdapter);

    }

    public void joinRoom(View v) {
        Log.i(LOGTAG, "join room button clicked.");

        roomName = roomNameInput.getText().toString();
        username = usernameInput.getText().toString();

        Intent enterChatRoomIntent = new Intent(this, ChatRoomActivity.class);
        enterChatRoomIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, roomName);
        enterChatRoomIntent.putExtra(ChatRoomActivity.ARG_USERNAME_ID, username);
        enterChatRoomIntent.putExtra(ChatRoomActivity.PUB_CAPTURER_RESOLUTION, mCapturerResolution);
        enterChatRoomIntent.putExtra(ChatRoomActivity.PUB_CAPTURER_FPS, mCapturerFps);
        //save room name and username
        saveConferenceData();

        startActivity(enterChatRoomIntent);
    }

    private void saveConferenceData() {

        SharedPreferences settings = getApplicationContext()
                .getSharedPreferences(LAST_CONFERENCE_DATA, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("roomName", roomName);
        editor.putString("username", username);

        editor.apply();
    }

    private void restoreConferenceData() {
        SharedPreferences settings = getApplicationContext()
                .getSharedPreferences(LAST_CONFERENCE_DATA, 0);
        roomName = settings.getString("roomName", "");
        username = settings.getString("username", "");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        Spinner spinner = (Spinner) parent;
        if(spinner.getId() == R.id.combo_capturer_resolution) {
            // On selecting a spinner item
            mCapturerResolution = parent.getItemAtPosition(position).toString();
            Toast.makeText(parent.getContext(), "Selected: " + mCapturerResolution, Toast.LENGTH_LONG).show();
        }
        else {
            if(spinner.getId() == R.id.combo_capturer_fps) {
                // On selecting a spinner item
                mCapturerFps = parent.getItemAtPosition(position).toString();
                Toast.makeText(parent.getContext(), "Selected: " + mCapturerFps, Toast.LENGTH_LONG).show();
            }
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
