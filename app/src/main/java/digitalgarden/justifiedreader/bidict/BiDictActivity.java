// Ver: 2
package digitalgarden.justifiedreader.bidict;

import java.io.IOException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.*;
import android.support.v4.app.*;
import android.widget.*;
import digitalgarden.justifiedreader.debug.*;
import android.app.*;
import digitalgarden.justifiedreader.teacher.*;
import android.content.*;


public class BiDictActivity extends Activity implements AdapterView.OnItemClickListener
	{
	public static final String PREFS_NAME = "BiDictPrefs";
    
	private EditText search;
	private int offsetindp;
	
	private ListView list;
	
    
    // Just a helper method
    private BiDictIndex index()
        {
        return ((ApplicationWithGlobals)getApplication()).getIndex();
        }
        
        
	public void onCreate(Bundle savedInstanceState) 
		{
		super.onCreate(savedInstanceState);
        
        Debug.initScribe( this );
        Scribe.debug( Debug.BIDICT, "Dict is starting.");
		
	    setContentView(R.layout.listlayout);
	    setTitle(BiDictIndex.BiDictVersion);
	    
	    DisplayMetrics metrics = new DisplayMetrics();
	    getWindowManager().getDefaultDisplay().getMetrics(metrics);
	    offsetindp = (int) (48 * metrics.density + 0.5);
	    
		search =(EditText) findViewById(R.id.search);
		list =(ListView) findViewById(R.id.list);
		//list=getListView();
        list.setOnItemClickListener( this );

	    search.addTextChangedListener( new TextWatcher()
	    	{
	    	public void afterTextChanged(Editable s) 
	    		{
				try
					{
                    Scribe.locus( Debug.BIDICT );
					int position = index().lookup(search.getText().toString());
					Scribe.debug( "New position: " + position );

                    
					// ez valamé fontos, hogy így legyen, különben nincs scroll!
					list.invalidateViews();
					//lv.scrollBy(0, 0);
					list.setSelectionFromTop( position, offsetindp );
					}
				catch (IllegalArgumentException iae)
					{
					; // Nem érdekes, csak nem volt értékes karakter a keresett stringben
					}
				catch (IOException ioe)
					{
					Toast.makeText(getApplicationContext(), ioe.toString(), Toast.LENGTH_LONG).show();
					}
	    		}
	    	public void beforeTextChanged(CharSequence s, int start, int count, int after){}
	    	public void onTextChanged(CharSequence s, int start, int before, int count){}
	    	}); 
	    
	    list.setOnTouchListener(new OnTouchListener()
	    	{
			@Override
			public boolean onTouch(View v, MotionEvent event)
				{
				if (event.getAction() == MotionEvent.ACTION_DOWN) 
					{
					// 2. módszer InputMethodManager
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(list.getWindowToken(), 0);
			        }
				return false;
				}
			
	    	} );
		
		}
	

    private void start()
        {
        list.setAdapter( new BiDictAdapter(this, index()) );
		
        if ( search.getText().toString().isEmpty() )
            {
            String text = getIntent().getStringExtra( TeacherActivity.FIND );
            search.setText( text );
            }
        }    
        

    @Override
    public void onResume()
        {
        super.onResume();
        Scribe.locus( Debug.BIDICT );

        try
            {
            // Már volt indítás, Dict rendelkezésre áll
            if (index() != null)
                {
                Scribe.debug( Debug.BIDICT, "Dict index is not null.");
                if (index().checkValidity())
                    {
                    Scribe.debug( Debug.BIDICT, "Dict index is valid.");
                    start();
                    return;
                    }
                }
            else // meg nincs kesz dict, beolvassuk a preferencest  
                {
                Scribe.debug( Debug.BIDICT, "Dict index is null.");
                String fileName;
                int dic;

                // Ha ugyanezen a néven nem string van. 
                // null nem működik, ""-t ad vissza (SO alapján)
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                fileName = settings.getString("FN", "");
                dic = settings.getInt("DIC", 0);

                Scribe.debug( Debug.BIDICT, "Dict file name: " + fileName );
                
                if ( fileName.length() > 0 )
                    {
                    loadData( fileName, dic );
                    Scribe.debug( Debug.BIDICT, "Dict is loaded.");
                    start();
                    return;
                    }
                }
            }
        // Ez nem történhet meg, ha mégis: mindent nullázunk
        catch (ClassCastException cce)
            {
            ; // hiba eseten megyunk tovbb
            }
        catch (IOException ioe)
            {
            // Valamifele hiba lett a beolvasasnal
            Toast.makeText(getApplicationContext(), ioe.toString(), Toast.LENGTH_LONG).show();
            }       

        // ha ide jutottunk, akkor dialogus kell
        showDialog(DIALOG_CHOOSE_DICTIONARY);
        }
    
        
	// Preferences kiírása
	@Override
	protected void onPause()
		{
	    super.onPause();
		Scribe.locus( Debug.BIDICT );
		
		if (index() == null) // meg meg se nyitottuk!!
			{
			Scribe.note("onPause: Dict==null error!");
	    	return;
			}
			
try
	{
	index().saveIndicesIfDirty();
	Scribe.note("saveIndicesIfDirty save ready!");
	} 
catch (IOException ioe)
	{
	Scribe.note("onPause file error: " + ioe.toString());
	Toast.makeText(getApplicationContext(), ioe.toString(), Toast.LENGTH_LONG).show();
	}

	    // We need an Editor object to make preference changes.
	    // All objects are from android.context.Context
	    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putString("FN", index().getSourceFileName());
	    editor.putInt("DIC", index().getDic());
	    // Commit the edits!
	    editor.commit();
	    
	    //onPause-ban mentünk (mert később nem biztonságos, de elegendő onCreate-ben beolvasni, mert utána már élnek az adataink.
	    
	    //Log.i("BIDICT", "onPause VÉGE");
	    }      
	
	
	@Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
 		{
		String item = (String) list.getAdapter().getItem(position);
		Toast.makeText(this, item + " selected at " + position, Toast.LENGTH_LONG).show();
        
        Intent intent = getIntent();
        intent.putExtra( TeacherActivity.WORD, item );
		try
            {
            intent.putExtra(TeacherActivity.ARTICLE, index().getArticle(position).getString("ARTICLE"));
            }
        catch (IOException e)
            {}
        catch (IndexOutOfBoundsException e)
            {}
        setResult( RESULT_OK, intent);
        finish();
		}
	
	
	// Ez még nincs elkészítve
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
		{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_bi_dict, menu);
		return true;
		}
	
	
	/**
	 * Event Handling for Individual menu item selected
	 * Identify single menu item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
		{
	
	    switch (item.getItemId())
	    	{
	    case R.id.menu_change:
            index().toggleDic();
			list.invalidateViews();
	        return true;
	
	    // Dialog-hoz tartozó indítás
	    case R.id.menu_choosedictionary:
	    	showDialog(DIALOG_CHOOSE_DICTIONARY);
	    	return true;
	    	
	    case R.id.menu_addarticle:
	    	showDialog(DIALOG_ADD_ARTICLE);
	    	return true;
	    	
	    default:
	        return super.onOptionsItemSelected(item);
	    	}
		}
	
	/*
	 * Dialog ablakok kezelése APIDemos alapján
	 * 
	 */
	private static final int DIALOG_CHOOSE_DICTIONARY = 1;
	private static final int DIALOG_ADD_ARTICLE = 2;
	private int dialogStatus = 0;
	
	@Override
	protected Dialog onCreateDialog(int id) 
		{
	    switch (id) 
	    	{
	    case DIALOG_CHOOSE_DICTIONARY:

    		final EditText input = new EditText(this);
    		//input.setText(fileName);

    		final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
	    	
	        dialogBuilder.setTitle( R.string.alert_dialog_title );
	        dialogBuilder.setMessage( R.string.alert_dialog_message );
	       	dialogBuilder.setView( input );
	      	
	      	dialogBuilder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() 
	            	{
	                public void onClick(DialogInterface dialog, int whichButton) 
	                	{
	                    /* User clicked OK so do some stuff */
	        	    	String choosenFileName = input.getText().toString();

	        	    	try
	        	    		{
	        	    		// le kell ellenőrizni, hogy a beírt nevek megfelelőek-e
	        	    		// ilyenkor még lehet Mégse-t nyomni!
	            			if ( BiDictIndex.check(getString(R.string.directory), choosenFileName) )
	            				{
	            				loadData( choosenFileName, 0 );
                                start();
	            				dialogStatus = 1;
								
	            				return;
	            				}
            				Toast.makeText(getApplicationContext(), "Ez a szótár még nincs indexelve!", Toast.LENGTH_LONG).show();
	        	    		}
	        	    	catch (Exception e)
	        	    		{
	        	    		// Hiba a szotar betoltese soran
							Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
	        	    		
	        	    		// Na tehát: EGY dialógusablak van, mert a készítéséhez újra ide kellene jönni.
	        	    		// Az csak felugrik és eltűnik. De ha itt. jelenítjük meg, a gomb után el fog tűnni. 
	        	    		// Ezért kell kirakni a dismisslistenerbe!!
	        	    		}
	        	    	
						dialogStatus = -1;
	        	    	// Mindenképp ki fog lépni (elrejti a dialógust) - de ez csak akkor történhet meg, ha van adatbázis. Ezt ellenőrzi az onDismissListener
	                	}
	            	});

	      	dialogBuilder.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() 
	            	{
	                public void onClick(DialogInterface dialog, int whichButton) 
	                	{
	                	// Nagyon lényeges, hogy honnan érkezünk, különben nem tudunk kilépni
	                	// 1. Egyik dict sem valid -> akkor a programból is ki akarunk lépni
	                	// 2. Az actual dict valid -> csak szóanyagot akartunk változtatni, de inkább mégse
	                	// 3. Other dict valid -> átváltottunk, és oda megyünk vissza
	      
		  				// Itt egyszerubb: ha nincs Dict, akkor kilepunk
                		dialogStatus = -2; // a programból is kilépünk
						// if (Dict == null) finish(); nem mukodik!!
	                	}
	            	});
	
	      	AlertDialog dialog = dialogBuilder.create();
	      	
	      	dialog.setOnDismissListener(new DialogInterface.OnDismissListener ()
	      			{
	      			public void onDismiss (DialogInterface dialog)
	      				{
	      				// Csak akkor léphetünk ki, ha van érvényes adatbázis
	      			
	      				if (dialogStatus == -1)
	      			    	showDialog(DIALOG_CHOOSE_DICTIONARY);
							
						// ez csak -2 nel lehet igaz
						else if (index() == null)
							finish();

						}
	      			}) ;
	      	
	      	return dialog;
	      	
	    case DIALOG_ADD_ARTICLE: 
	    
	        // This example shows how to add a custom layout to an AlertDialog
	        LayoutInflater factory = LayoutInflater.from(this);
	        final View addArticleView = factory.inflate(R.layout.addarticledialog, null);
	        final AlertDialog.Builder addArticleDialogBuilder = new AlertDialog.Builder(this);
	        
	        addArticleDialogBuilder.setTitle(R.string.add_article_dialog_title);
	      	addArticleDialogBuilder.setView(addArticleView);
	      	
	      	addArticleDialogBuilder.setPositiveButton(R.string.add_article_dialog_ok, new DialogInterface.OnClickListener() 
      			{
                public void onClick(DialogInterface dialog, int whichButton) 
                	{
                    /* User clicked OK so do some stuff */
        	    	EditText english = (EditText) addArticleView.findViewById(R.id.english);
        	    	EditText hungarian = (EditText) addArticleView.findViewById(R.id.hungarian);
                	ToggleButton isVerb = (ToggleButton) addArticleView.findViewById(R.id.is_verb);

        		    try
        		    	{
        		    	int position = index().addArticle("~[" + (isVerb.isChecked() ? "$" : "") + english.getText().toString() + "=]" + hungarian.getText().toString() );
        		    	// ez valamé fontos, hogy így legyen, különben nincs scroll!
        		    	
        		    	english.setText("");
        		    	hungarian.setText("");
        		    	isVerb.setChecked(false);
        		    	
        		    	list.invalidateViews();
        		    	//lv.scrollBy(0, 0);
        		    	list.setSelectionFromTop( position, offsetindp );
        		    	} 
        		    catch (IOException ioe)
        		    	{
        		    	Toast.makeText(getApplicationContext(), ioe.toString(), Toast.LENGTH_LONG).show();
        		    	}
                	}
            	});
	      	addArticleDialogBuilder.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() 
	            	{
	                public void onClick(DialogInterface dialog, int whichButton) 
	                	{
	                	// nem történik semmi, csak didaktikai okokból van itt
	                	}
	            	});
	
	      	AlertDialog addArticleDialog = addArticleDialogBuilder.create();
	      	
	      	return addArticleDialog;
	    	}
	    
	    return null;
		} 

	
	/**
	 * DroiDictActivity szótárakat kezelő metódusai
	 */
	
	// Legutolsó rutin: betölti a listába az aktuális fileName-hez tartozó szótárat
	// Ha az nem valid, akkor is kivételt dob
	private void loadData( String fileName, int dic ) throws IOException
		{
        Scribe.locus( Debug.BIDICT );
        
		((ApplicationWithGlobals)getApplication()).setIndex( new BiDictIndex( getString(R.string.directory), fileName, true ));
		if ( !index().isReady())
			{
	        // Itt indexálhatnánk is (index hiánya nem hiba!), de erre most nincs idő
			throw new IOException("Dictionary is not indexed!");
			}
		index().setDic(dic);
        }
	
	}
