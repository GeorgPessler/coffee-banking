package de.fruity.coffeeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

import java.util.List;

import de.fruity.coffeeapp.adminmode.AdminmodeActivity;
import de.fruity.coffeeapp.database.SqlAccessAPI;
import de.fruity.coffeeapp.database.SqlDatabaseContentProvider;
import de.fruity.coffeeapp.database.SqliteDatabase;
import de.fruity.coffeeapp.ui_elements.CustomToast;
import de.fruity.coffeeapp.ui_elements.RadioButtonCustomized;
import de.fruity.coffeeapp.ui_elements.SeekBarCustomized;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    private BroadcastReceiver mReceiver;

    private RadiogroupMerger mRadiogroupMerger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float default_coffe;
        ArrayAdapter<String> dla;

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getActionBar() != null)
            getActionBar().hide();

        setContentView(R.layout.activity_main_navigationdrawer);
        FrameLayout framelayout = (FrameLayout) findViewById(R.id.content_frame);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        ViewGroup vg = (ViewGroup) findViewById(R.id.rl_activity_main);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout myView = (RelativeLayout) inflater.inflate(R.layout.activity_main, vg, false);
        framelayout.addView(myView);

        // Set the adapter for the list view
        dla = new ArrayAdapter<>(this, R.layout.activity_main_navigationdrawer_object);
        for (String s : SqlAccessAPI.getGroupNamesFromDatabase(getContentResolver()))
            dla.add(s);
        mDrawerList.setAdapter(dla);
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        default_coffe = SqlAccessAPI.getPriceMin(getContentResolver(), "coffee");

        mRadiogroupMerger = new RadiogroupMerger((RadioGroup) findViewById(R.id.rg_upper),
                (RadioGroup) findViewById(R.id.rg_down));
        mRadiogroupMerger.setDefaults(getContentResolver(), R.id.coffee, default_coffe, "coffee");

        final RadioButtonCustomized rb_candy = (RadioButtonCustomized) findViewById(R.id.candy);
        final SeekBarCustomized sb_candy = (SeekBarCustomized) findViewById(R.id.slider_candy_main);
        Button select_by_person_Button = (Button) findViewById(R.id.btn_main);

        mReceiver = new RFIDReaderReceiver(mRadiogroupMerger);

        Intent i = new Intent(this, ReaderService.class);
        startService(i);

        // Initialize get features button
        select_by_person_Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogEnterPersonal();
            }
        });

        sb_candy.init(mRadiogroupMerger, "candy");

        rb_candy.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    sb_candy.setVisibility(View.VISIBLE);
                } else {
                    sb_candy.setVisibility(View.INVISIBLE);
                }
            }
        });
    }


    protected void onResume() {
        super.onResume();
        // registering our receiver
        IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");
        this.registerReceiver(mReceiver, intentFilter);
        ReaderService.startContinuity();
    }

    public void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            groupMode(position + 1);
            mDrawerLayout.closeDrawer(mDrawerList);
        }
    }

    private void dialogEnterPersonal() {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_enter_personalnumber);
        dialog.setTitle(R.string.enter_personalnumber);

        // set the custom dialog components - text, image and button

        Button cancelButton = (Button) dialog.findViewById(R.id.personalnumber_dialog_btn_cancel);
        final Button btnSave = (Button) dialog.findViewById(R.id.personalnumber_dialog_btn_save);

        final EditText et_personalnumber = (EditText) dialog.findViewById(R.id.personalnumber_dialog_et);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Integer persno;

                try {
                    persno = Integer.parseInt(et_personalnumber.getText().toString());
                } catch (NumberFormatException ex) {
                    customToast(getText(R.string.no_personalnumber_number).toString(), 800);
                    return;
                }

                dialog.dismiss();

                if (AdminmodeActivity.isAdminCode(getApplicationContext(), persno)){
                    Intent outgoing = new Intent("android.intent.action.MAIN");
                    outgoing.putExtra(ReaderService.TID, AdminmodeActivity.SECRET_ADMIN_CODE);
                    sendBroadcast(outgoing);
                    return;
                }

                Cursor rfidCursor = getContentResolver().query(SqlDatabaseContentProvider.CONTENT_URI, null,
                        SqliteDatabase.COLUMN_PERSONAL_NUMBER + " =  ?",
                        new String[] {persno.toString()}, null);

                if (rfidCursor != null && rfidCursor.moveToFirst()) {
                    String s = rfidCursor.getString(rfidCursor.getColumnIndexOrThrow(SqliteDatabase.COLUMN_NAME));
                    final int rfid = rfidCursor.getInt(rfidCursor.getColumnIndexOrThrow(SqliteDatabase.COLUMN_RFID));

                    if (mRadiogroupMerger.getCheckedId() == R.id.admin && SqlAccessAPI.isAdmin(getContentResolver(), rfid))
                        return;

                    showIsThisYourNameDialog(s, rfid);

                    rfidCursor.close();
                } else {
                    customToast(getText(R.string.personalnumber_not_found).toString(), 2500);
                }
            }
        });

        et_personalnumber.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View arg0, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    btnSave.callOnClick();
                    return true;
                }
                return false;
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void groupMode(int group_id) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_groupmode);
        dialog.setTitle(R.string.groupmode);

        // set the custom dialog components - text, image and button
        ListView lvData = (ListView) dialog.findViewById(R.id.groupmode_lv_dialog);
        List<GroupmodeData> list_names = SqlAccessAPI.getNamesInGroup(getContentResolver(), group_id);

        final CheckboxListAdapter adapter = new CheckboxListAdapter(getLayoutInflater());

        lvData.setAdapter(adapter);
        for (GroupmodeData data : list_names)
            adapter.add(data);

        dialog.setCancelable(false);
        Button cancelButton = (Button) dialog.findViewById(R.id.groupmode_btn_cancel);
        Button btnSave = (Button) dialog.findViewById(R.id.groupmode_btn_save);
        btnSave.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (GroupmodeData gd : adapter.getSelected()) {
                    SqlAccessAPI.bookCoffee(getContentResolver(), gd.getID());
                    sb.append(gd.getName());
                    sb.append(", ");
                }

                if (TextUtils.isEmpty(sb.toString()))
                    Log.i(TAG, "no one selected");
                else
                    customToast(sb.toString().substring(0, sb.toString().length() -2) + " \n" +
                        getText(R.string.group_booked).toString(), 2500);
                dialog.dismiss();
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showIsThisYourNameDialog(String name, final int rfid) {

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(R.string.is_correct_name);
        adb.setMessage(getText(R.string.hello) + " " + name + getText(R.string.comma_is_correct_name));
        adb.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent outgoing = new Intent("android.intent.action.MAIN");
                outgoing.putExtra(ReaderService.TID, rfid);
                sendBroadcast(outgoing);
            }
        });

        adb.show();
    }

    private void customToast(String message, int duration) {
        new CustomToast(getApplicationContext(), message, duration);
    }


}
