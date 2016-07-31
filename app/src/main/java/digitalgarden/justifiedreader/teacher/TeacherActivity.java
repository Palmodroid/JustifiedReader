package digitalgarden.justifiedreader.teacher;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import digitalgarden.justifiedreader.*;
import android.content.*;
import digitalgarden.justifiedreader.bidict.*;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.debug.*;
import android.text.*;
import android.support.v4.content.*;


public class TeacherActivity extends Activity {
    
    public static final String FIND = "find";
    public static final String WORD = "word";
    public static final String PARA = "para";
    public static final String ARTICLE = "article";
    public static final int DICT_REQUEST = 1;
        
	private EditText ed1;
	private TextView tx1;
	private EditText ed2;
	private TextView tx2;
	private EditText ed3;
	private TextView tx3;
	private EditText ed4;
	private TextView tx4;
	private EditText ed5;
	private TextView tx5;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
        {
        // TODO: Implement this method
        super.onActivityResult(requestCode, resultCode, data);
        Scribe.locus( Debug.TEACHER );
        
        if ( requestCode == DICT_REQUEST && resultCode != RESULT_CANCELED )
            {
            ed3.setText( Html.fromHtml( data.getStringExtra( WORD )));
            ed4.setText( Html.fromHtml( data.getStringExtra( ARTICLE )));
            }
        }
	
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    	{
        super.onCreate(savedInstanceState);
        Scribe.locus( Debug.TEACHER );
        
        setContentView(R.layout.activity_teacher);

        ed1 = (EditText) findViewById(R.id.ed1);
        tx1 =(TextView) findViewById(R.id.tx1);
        ed2 = (EditText) findViewById(R.id.ed2);
        tx2 =(TextView) findViewById(R.id.tx2);
        ed3 = (EditText) findViewById(R.id.ed3);
        tx3 =(TextView) findViewById(R.id.tx3);
        ed4 = (EditText) findViewById(R.id.ed4);
        tx4 =(TextView) findViewById(R.id.tx4);
        ed5 = (EditText) findViewById(R.id.ed5);
        tx5 =(TextView) findViewById(R.id.tx5);

        
        if ( getIntent().getStringExtra( FIND ) != null )
            {
            ed1.setText( getIntent().getStringExtra( FIND ));
            ed2.setText( getIntent().getStringExtra( PARA ));
            
            Intent intent = getIntent();
            //intent.setClass( this, TeacherActivity.class);
            intent.setClass( this, BiDictActivity.class);
            startActivityForResult( intent, DICT_REQUEST );
            //finish();
            }
		}    
        
        
    @Override
    protected void onStart()
        {
        super.onStart();
        Scribe.locus( Debug.TEACHER );
        }

    
    @Override
    protected void onResume()
        {
        super.onResume();
        Scribe.locus( Debug.TEACHER );
        }

    @Override
    protected void onPause()
        {
        // TODO: Implement this method
        super.onPause();
        Scribe.locus( Debug.TEACHER );
        
        Toast.makeText( this, Html.toHtml( ed3.getText() ), Toast.LENGTH_LONG).show();
        }

    @Override
    protected void onStop()
        {
        // TODO: Implement this method
        super.onStop();
        Scribe.locus( Debug.TEACHER );
        }

    @Override
    protected void onDestroy()
        {
        // TODO: Implement this method
        super.onDestroy();
        Scribe.locus( Debug.TEACHER );
        }
        
	
	public void DoIt(View view) 
		{
		switch (view.getId()) 
			{
		case R.id.button:
			
			try
				{
				/*
				 * Ide jön a kipróbálandó programrész. 
				 * Az ed1-ed5 mezőket (és a tx1-tx5 címkéket is) szabadon használhatjuk
				 */
				tx1.setText("Dátum formátuma:");
				String df=ed1.getText().toString();

				tx2.setText("Az aktuális dátum:");
				SimpleDateFormat sdf=new SimpleDateFormat(df);
				ed2.setText( sdf.format(new Date()));

				tx3.setText("Kívánt dátum:");

				Calendar cal= Calendar.getInstance();
				cal.set(Calendar.YEAR, cal.get(Calendar.YEAR)-99);
				sdf.set2DigitYearStart(cal.getTime());

				Date d=sdf.parse(ed3.getText().toString());

				tx4.setText("A beírt dátum újraformázva:");
				ed4.setText( sdf.format( d ));

				
				tx5.setText("A két dátum közötti különbség években (kor)");
				
			    Calendar cdob = Calendar.getInstance();
			    Calendar cnow = Calendar.getInstance();

			    cdob.setTime(d);
			
			    int age = cnow.get(Calendar.YEAR) - cdob.get(Calendar.YEAR);
			
			    if (cnow.get(Calendar.MONTH) < cdob.get(Calendar.MONTH))
			    	{
			        age--; 
			    	}
			
			    else if (cnow.get(Calendar.MONTH) == cdob.get(Calendar.MONTH) && (cnow.get(Calendar.DATE) < cdob.get(Calendar.DATE)))
			    	{
			        age--; 
			    	}
				
			    ed5.setText(Integer.toString(age));
				
				}
			catch (Exception e)
				{
				Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
				}
			
			break;
			}
		}

    /*
	@Override
    public boolean onCreateOptionsMenu(Menu menu) 
		{
        getMenuInflater().inflate(R.menu.activity_teacher, menu);
        return true;
	    }
    */
	}
