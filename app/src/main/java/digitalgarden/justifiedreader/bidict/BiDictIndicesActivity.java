package digitalgarden.justifiedreader.bidict;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import digitalgarden.justifiedreader.*;


/*
 * 
 * A FŐ ACTIVITY
 * 
 */
public class BiDictIndicesActivity extends Activity 
	{
	TextView progressMeter;
	EditText fileName;
	ProgressDialog progressDialog;
	Button buttonStart;
	Button buttonCancel;
	TimeConsumingTask timeConsumingTask = null;
	
	
	// Háttérfolyamat indítása/leállítása
	void startTask()
		{
		try
			{
			if (timeConsumingTask == null || timeConsumingTask.state != timeConsumingTask.TASK_RUNNING)
				{
	            timeConsumingTask = new TimeConsumingTask( getApplicationContext() );
	            timeConsumingTask.callerActivity = this;
	            timeConsumingTask.execute(fileName.getText().toString());
				}
			else
				// Ez nem következhet be, ilyenkor uis. a START gomb nem elérhető!
				Toast.makeText(getApplicationContext(), R.string.err_task_started, Toast.LENGTH_SHORT).show();
			}
		catch (Exception e)
			{
			Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
			}
		}
	
	void cancelTask()
		{
		try
			{
			if (timeConsumingTask != null && timeConsumingTask.state == timeConsumingTask.TASK_RUNNING)
				{
	            timeConsumingTask.cancel(false);
				}
			else
				// Ez nem következhet be
				// - CANCEL billentyű ilyenkor nem látható
				// - BACK billentyűt progressDialog kezeli (és akkor van futó feladat)
				// - onDestroy külön ellenőrzést végez
				Toast.makeText(getApplicationContext(), R.string.err_task_cancelled, Toast.LENGTH_SHORT).show();
			}
		catch (Exception e)
			{
			Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
			}
		}
	
	
	// A BACK billentyű a megállítás másik lehetősége
	@Override
	public void onBackPressed()
	    {
		if (timeConsumingTask == null || timeConsumingTask.state != timeConsumingTask.TASK_RUNNING)
			super.onBackPressed();
		
		// Ha progressDialog-ot használunk, akkor ezt a dialog kezeli le, ide soha nem jutahtunk
		else
			cancelTask();
	    }
	

	// A program belépési pontja
	@Override
	public void onCreate(Bundle savedInstanceState) 
		{
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_bi_dict_indices);
	    setTitle(BiDictIndex.BiDictVersion);

	    ((TextView)findViewById(R.id.dir)).setText(Environment.getExternalStorageDirectory() + File.separator + getString(R.string.directory));
	
		fileName = (EditText) findViewById(R.id.fileName);
		// Itt van egy kis gond: ha erre az értékre a pre részben van szükség
		// a paramétert viszont a doInBackground kapja meg
		// mivel a pre rész hozzáfér az UI-hez, nyugodtan kiveheti közvetlenül is a szüks. értéket
		
	    progressMeter = (TextView) findViewById(R.id.progressMeter);
	
	    progressDialog = new ProgressDialog( this );
		progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener()
			{
			public void onCancel(DialogInterface dialog)
				{
				cancelTask();
				}
			});
		
	    buttonStart = (Button) findViewById(R.id.button_start);
	    buttonStart.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View view) 
				{
				if (view.getId() == R.id.button_start)
					startTask();
				} 
			});
	
	    // ProgressDialog mellett ez a billentyű felesleges
	    buttonCancel = (Button) findViewById(R.id.button_cancel);
	    buttonCancel.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View view) 
				{
				if (view.getId() == R.id.button_cancel)
					cancelTask();
				} 
			});
	
	    // az eredeti layout még el nem indult feladat szerint épül fel, amikor timeConsumingTask == null !!

	    // ha korábban már volt elindított háttérszál, itt visszavesszük, és a layout-ot is módosítjuk 
	    timeConsumingTask = (TimeConsumingTask) getLastNonConfigurationInstance();
	    if (timeConsumingTask != null)
	    	{
	    	// a háttérszál már elindult
	    	timeConsumingTask.callerActivity = this;
	   		timeConsumingTask.updateUI();
	    	}
	
		}
	
	
	// A megőrzendő háttérfolyamat átadása az újrainduló Activity számára
	// onDestroy előtt kerül meghívásra
	@Override
	public Object onRetainNonConfigurationInstance() 
		{
		return timeConsumingTask;
		}
	
	
	// A program kilépési pontja 
	@Override
	public void onDestroy()
	    {
		if (timeConsumingTask != null)
			{
			timeConsumingTask.callerActivity = null;
		
			if (timeConsumingTask.state == timeConsumingTask.TASK_RUNNING && this.isFinishing())
				cancelTask();
			}

		if (progressDialog.isShowing())
			progressDialog.dismiss();
			
	    super.onDestroy();
	    }
	
	
	// Menü létrehozása - ezt most nem használjuk
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
		{
	    getMenuInflater().inflate(R.menu.bi_dict_indices, menu);
	    return true;
		}
	}
