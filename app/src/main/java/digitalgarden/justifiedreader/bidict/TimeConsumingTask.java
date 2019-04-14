package digitalgarden.justifiedreader.bidict;


import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;

import digitalgarden.justifiedreader.R;

/*
 * 
 * A HÁTTÉRSZÁL FUTÁSÁÉRT FELELŐS OSZTÁLY
 * 
 */
class TimeConsumingTask extends AsyncTask<String, Integer, String>
	{
	// Kapcsolat a "fő" szállal 
	Context applicationContext;
	BiDictIndicesActivity callerActivity = null;
	String returnedMessage = null;
	
	final int TASK_RUNNING = 0;
	final int TASK_FINISHED = 1;
	final int TASK_CANCELLED = 2;
	int state;
	
	// Saját változók, TimeConsumingTask-on kívül nem elérhetőek
	
	
	// Az applicationContext NEM VÁLTOZIK a program futása alatt, (a resource-ok eléréséhez kell)
	public TimeConsumingTask(Context context)
		{
		applicationContext = context;
		}
	
	
	// A layout beállítása álló/futó háttérfolyamathoz
	private void layoutForStart()
		{
		callerActivity.buttonStart.setVisibility(View.VISIBLE);
		callerActivity.buttonCancel.setVisibility(View.GONE);
		}

	private void layoutForCancel()
		{
		callerActivity.buttonStart.setVisibility(View.GONE);
		callerActivity.buttonCancel.setVisibility(View.VISIBLE);
		}

	
	// Indítás előtt
    @Override
    protected void onPreExecute() 
    	{
		//Log.i("ASYNC", "TASK RUNNING");
		/*
		 * Ebben a részben készítjük elő a terepet a háttérfolyamatnak
		 * A legfontosabb, hogy megállapítsuk, hány elemmel kell dolgozzunk
		 * Ha minden ok, akkor TASK_RUNNING-gal megyünk tovább, egyébként TASK_FINISHED-del megállunk
		 * Üzenet a returnedMessage-ben
		 */
		
		state = TASK_RUNNING;
		updateUI();	
    	}

    
	// A tényleges, háttérben zajló feladat
	// UI-szál elérése TILOS!
	@Override
	protected String doInBackground(String... fileName) 
		{			
			
		// ha az onPreExecute már hibát tartalmazott, nem megyünk tovább
		if (state != TASK_RUNNING)
			return "HIBA!";

		BiDictIndex index = null;
		
    	try
    		{

    		// IDE KERUL A LENYEGI RESZ!!
    		index = new BiDictIndex(applicationContext.getString(R.string.directory), fileName[0], false);

    		index.prepareIndices(Compare.HUNGARIAN_MASK);
    		
    		int p;
    		
    		do 	{
				if (isCancelled())
					break;

				p = index.createAbcIndices();
    			
    			publishProgress(p/2);
    			
    			} while ( p<1000 );

    		for (int n = 0; n < 2; n++)
    			{
	    		do 	{
					if (isCancelled())
						break;
	
					p = index.createIdxIndices(n);
					
					publishProgress(500 + n*250 + p/4);
					
					} while ( p<1000 );
    			}

       		index.finishIndices();

       		if (!isCancelled())
    			index.saveIndices();
    			
 			returnedMessage = "Minden OK!!";

    		} 
    	catch (IOException e)
			{
			returnedMessage = "HIBA:" + e.toString();
			} 

    	// A visszatérési érték feleslegessé vált, és NEM egyezik az eredménnyel!
    	// Az "eredmény" a returnedMessage-ba kerül 
    	return "VÉGE!";
		}   
		
    
	// Tájékoztatás futás közben
    @Override
    protected void onProgressUpdate(Integer... progress) 
    	{
		super.onProgressUpdate(progress);
		
    	if (callerActivity != null && state == TASK_RUNNING)
    		{
    		// A progressMetert nem kell használnunk, a progressDialog elfedi:
    		// callerActivity.progressMeter.setText(Integer.toString(progress[0].intValue()));
    		callerActivity.progressDialog.setProgress(progress[0]);
    		}
    	}
   

    // Végrehajtás után - a háttérszálat megtartjuk, de felajánljuk az újraindítás lehetőségét
    @Override
    protected void onPostExecute(String result) 
    	{
    	state = TASK_FINISHED;
		//Log.i("ASYNC", "TASK FINISHED");

		// Részletes tájékoztatást adunk a lefutott feladatról
    	// returnedMessage = applicationContext.getString(R.string.msg_ready);

    	if (callerActivity != null)
    		{
    		updateUI();
    		}
    	}
    

	// Megszakítás után - felállunk újraindításhoz. Ha már nincs meg a hívó Activity, akkor az új példány már így indul
	@Override
	protected void onCancelled()
		{
		state = TASK_CANCELLED;
		//Log.i("ASYNC", "TASK CANCELLED");

		// Itt csak a megszakítást jelezzük, de az elvégzett munka is megadható lenne
		returnedMessage = applicationContext.getString(R.string.msg_cancel);
		
    	if (callerActivity != null)
    		{
    		updateUI();
    		}
		}

	
	// A teljes felhasználó felület átállítása
	// state állapotától függ
	void updateUI() 
		{
    	if (callerActivity != null)
    		{
    		switch (state)
	    		{
	    		case TASK_RUNNING:
	    			/*
	    			 * Itt állítjuk be az elemszámot. TASK_RUNNING csak akkor lehet az állapot, ha van lista
	    			 */
	    			
	    			callerActivity.progressDialog.setMax(1000);
	    			callerActivity.progressDialog.setIndeterminate(false); // de, determinált ha van százalék!

	    			callerActivity.progressDialog.setMessage(applicationContext.getString(R.string.dialogMessage));
		    		callerActivity.progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		    		callerActivity.progressDialog.setCancelable(true);
		    		callerActivity.progressDialog.setCanceledOnTouchOutside(false);
		       		callerActivity.progressDialog.setButton (ProgressDialog.BUTTON_NEGATIVE, applicationContext.getString(R.string.dialogCancelButton), new DialogInterface.OnClickListener()
		       				{
		       				public void onClick (DialogInterface dialog, int which)
			       				{
			       				if (which == ProgressDialog.BUTTON_NEGATIVE)
			       					callerActivity.cancelTask();
			       				}
		       				});

		    		callerActivity.progressDialog.show();	

		    		layoutForCancel();
		    		
		    		// Meg kell akadályozni, hogy a gép kikapcsoljon!
		    		callerActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		    		break;
					
	    		case TASK_FINISHED:
	    		case TASK_CANCELLED:
		    		callerActivity.progressDialog.dismiss();	
		    		callerActivity.progressMeter.setText(returnedMessage);
					layoutForStart();
					
					// Most már kikapcsolhatunk nyugodtan
		    		callerActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					
					break;
	    		}
			}
		}
	}
