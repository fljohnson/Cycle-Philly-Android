/**	 Cycle Altanta, Copyright 2012 Georgia Institute of Technology
 *                                    Atlanta, GA. USA
 *
 *   @author Christopher Le Dantec <ledantec@gatech.edu>
 *   @author Anhong Guo <guoanhong15@gmail.com>
 *
 *   Updated/Modified for Atlanta's app deployment. Based on the
 *   CycleTracks codebase for SFCTA.
 *
 *   CycleTracks, Copyright 2009,2010 San Francisco County Transportation Authority
 *                                    San Francisco, CA, USA
 *
 * 	 @author Billy Charlton <billy.charlton@sfcta.org>
 *
 *   This file is part of CycleTracks.
 *
 *   CycleTracks is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   CycleTracks is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phillyopen.mytracks.cyclephilly;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.phillyopen.mytracks.cyclephilly.R;

public class MainInput extends FragmentActivity {
    private final static int MENU_USER_INFO = 0;
    private final static int MENU_HELP = 1;
    private final static int MENU_MAP = 2;
    private final static int MENU_LEGAL_INFO = 3;

    private final static int CONTEXT_RETRY = 0;
    private final static int CONTEXT_DELETE = 1;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    DbAdapter mDb;
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    //TODO: ...try the request again?
                    break;
                }
        	}
        }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	// Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == resultCode) {
            Log.d("Location Updates", "Google Play services is available.");
            return;
        // Google Play services was not available for some reason
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(errorDialog);
                errorFragment.show(getSupportFragmentManager(), "Location Updates");
            }
        }
    }
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Let's handle some launcher lifecycle issues:
		// If we're recording or saving right now, jump to the existing activity.
		// (This handles user who hit BACK button while recording)
		setContentView(R.layout.main);

		Intent rService = new Intent(this, RecordingService.class);
		ServiceConnection sc = new ServiceConnection() {
			public void onServiceDisconnected(ComponentName name) {}
			public void onServiceConnected(ComponentName name, IBinder service) {
				IRecordService rs = (IRecordService) service;
				int state = rs.getState();
				if (state > RecordingService.STATE_IDLE) {
					if (state == RecordingService.STATE_FULL) {
						startActivity(new Intent(MainInput.this, SaveTrip.class));
					} else {  // RECORDING OR PAUSED:
						startActivity(new Intent(MainInput.this, RecordingActivity.class));
					}
					MainInput.this.finish();
				} else {
					// Idle. First run? Switch to user prefs screen if there are no prefs stored yet
			        SharedPreferences settings = getSharedPreferences("PREFS", 0);
			        if (settings.getAll().isEmpty()) {
                        showWelcomeDialog();
			        }
					// Not first run - set up the list view of saved trips
					ListView listSavedTrips = (ListView) findViewById(R.id.ListSavedTrips);
					populateList(listSavedTrips);
				}
				MainInput.this.unbindService(this); // race?  this says we no longer care
			}
		};
		// This needs to block until the onServiceConnected (above) completes.
		// Thus, we can check the recording status before continuing on.
		bindService(rService, sc, Context.BIND_AUTO_CREATE);

		// And set up the record button
		final Button startButton = (Button) findViewById(R.id.ButtonStart);
		final Intent i = new Intent(this, RecordingActivity.class);
		startButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
			    // Before we go to record, check GPS status
			    final LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
			    if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
			        buildAlertMessageNoGps();
			    } else {
	                startActivity(i);
	                MainInput.this.finish();
			    }
			}
		});
	}

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your phone's GPS is disabled. Cycle Philly needs GPS to determine your location.\n\nGo to System Settings now to enable GPS?")
               .setCancelable(false)
               .setPositiveButton("GPS Settings...", new DialogInterface.OnClickListener() {
                   public void onClick(final DialogInterface dialog, final int id) {
                       final Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                       startActivityForResult(intent, 0);
                   }
               })
               .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                   public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                   }
               });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void showWelcomeDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Please enter your personal details so we can learn a bit about you.\n\nThen, try to use Cycle Philly every time you ride. Your trip routes will be sent to City of Philadelphia so we can plan for better biking!\n\nThanks,\nThe Cycle Philly team")
               .setCancelable(false).setTitle("Welcome to Cycle Philly!")
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   public void onClick(final DialogInterface dialog, final int id) {
                       startActivity(new Intent(MainInput.this, UserInfoActivity.class));
                   }
               });

        final AlertDialog alert = builder.create();
        alert.show();
    }

	void populateList(ListView lv) {
		// Get list from the real phone database. W00t!
		DbAdapter mDb = new DbAdapter(MainInput.this);
		mDb.open();

		// Clean up any bad trips & coords from crashes
		int cleanedTrips = mDb.cleanTables();
		if (cleanedTrips > 0) {
		    Toast.makeText(getBaseContext(),""+cleanedTrips+" bad trip(s) removed.", Toast.LENGTH_SHORT).show();
		}

		try {
			Cursor allTrips = mDb.fetchAllTrips();

			SimpleCursorAdapter sca = new SimpleCursorAdapter(this,
					R.layout.twolinelist, allTrips,
						new String[] { "purp", "fancystart", "fancyinfo"},
						new int[] {R.id.TextView01, R.id.TextView03, R.id.TextInfo}
			);

			lv.setAdapter(sca);
			TextView counter = (TextView) findViewById(R.id.TextViewPreviousTrips);

			int numtrips = allTrips.getCount();
			switch (numtrips) {
			case 0:
				counter.setText("No saved trips.");
				break;
			case 1:
				counter.setText("1 saved trip:");
				break;
			default:
				counter.setText("" + numtrips + " saved trips:");
			}
			// allTrips.close();
		} catch (SQLException sqle) {
			// Do nothing, for now!
		}
		mDb.close();

		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
		    public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
		        Intent i = new Intent(MainInput.this, ShowMap.class);
		        i.putExtra("showtrip", id);
		        startActivity(i);
		    }
		});
		registerForContextMenu(lv);
	}

	@Override
    public void onCreateContextMenu(ContextMenu menu, View v,
	        ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	    menu.add(0, CONTEXT_RETRY, 0, "Retry Upload");
	    menu.add(0, CONTEXT_DELETE, 0,  "Delete");
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    switch (item.getItemId()) {
	    case CONTEXT_RETRY:
	        retryTripUpload(info.id);
	        return true;
	    case CONTEXT_DELETE:
	        deleteTrip(info.id);
	        return true;
	    default:
	        return super.onContextItemSelected(item);
	    }
	}

	private void retryTripUpload(long tripId) {
	    TripUploader uploader = new TripUploader(MainInput.this);
        uploader.execute(tripId);
	}

	private void deleteTrip(long tripId) {
	    DbAdapter mDbHelper = new DbAdapter(MainInput.this);
        mDbHelper.open();
        mDbHelper.deleteAllCoordsForTrip(tripId);
        mDbHelper.deleteTrip(tripId);
        mDbHelper.close();
        ListView listSavedTrips = (ListView) findViewById(R.id.ListSavedTrips);
        listSavedTrips.invalidate();
        populateList(listSavedTrips);
    }

	 /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_HELP, 0, "Help and FAQ").setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, MENU_USER_INFO, 0, "Edit User Info").setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, MENU_MAP, 0, "Cycling Map").setIcon(android.R.drawable.ic_menu_compass);
        menu.add(0, MENU_LEGAL_INFO, 0, "Legal Information").setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_USER_INFO:
            startActivity(new Intent(this, UserInfoActivity.class));
            return true;
        case MENU_HELP:
        	Intent myIntent = new Intent(Intent.ACTION_VIEW,
        			Uri.parse("http://mytracks.phillyopen.org/instructions-v2/androidhelp.html"));
   			startActivity(myIntent);
            return true;
        case MENU_MAP:
        	startActivity(new Intent(this, ShowMapNearby.class));
        	return true;
        case MENU_LEGAL_INFO:
        	startActivity(new Intent(this, LicenseActivity.class));
      		return true;
        }
        return false;
    }
}

class FakeAdapter extends SimpleAdapter {
	public FakeAdapter(Context context, List<? extends Map<String, ?>> data,
			int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
	}
}

