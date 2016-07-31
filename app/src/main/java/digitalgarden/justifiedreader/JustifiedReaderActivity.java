package digitalgarden.justifiedreader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;

import java.io.FileNotFoundException;
import java.io.IOException;

import digitalgarden.justifiedreader.debug.Debug;
import digitalgarden.justifiedreader.description.TextDescriptor;
import digitalgarden.justifiedreader.scribe.Scribe;
import android.content.*;
import digitalgarden.justifiedreader.bidict.*;
import digitalgarden.justifiedreader.teacher.*;

public class JustifiedReaderActivity extends Activity 
         implements JustifiedReaderView.OnWordSelectedListener
    {
    private EditText editText;
    private JustifiedReaderView justifiedReaderView;

    private TextDescriptor textDescriptor = null;


    @Override
    public void onCreate(Bundle savedInstanceState)
        {
        super.onCreate(savedInstanceState);

        Debug.initScribe( this );

        setContentView(R.layout.activity_justified_reader);

        editText = (EditText) findViewById(R.id.editText);
        justifiedReaderView = (JustifiedReaderView) findViewById(R.id.justText);
        justifiedReaderView.setOnWordSelectedListener( this );
        }

    @Override
    public void onResume()
        {
        super.onResume();

        try
            {
            // a párja onPause-ban van, de már itt visszaolvasható, mert később nem változik
            SharedPreferences settings = getSharedPreferences("PREFS", 0);
            long pointer = settings.getLong("PREF_DATA", 0L);    
            
            textDescriptor = new TextDescriptor( "//proba.txt");
            justifiedReaderView.setVisibleText(textDescriptor, pointer );
            }
        catch (FileNotFoundException e)
            {
            Scribe.error("COULD NOT FIND TEXT-DESCRIPTOR FILE!");
            }
        }

        
    @Override
    public void onPause()
        {
        super.onPause();

        if ( textDescriptor != null )
            {
            try
                {
                long pointer = textDescriptor.getFilePointer();
                if ( pointer >= 0L )
                    {
                    //onPause-ban mentünk (mert később nem biztonságos, de elegendő onCreate-ben beolvasni, mert utána már élnek az adataink.
                    // We need an Editor object to make preference changes.
                    // All objects are from android.context.Context
                    SharedPreferences settings = getSharedPreferences("PREFS", 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putLong("PREF_DATA", pointer);
                    // Commit the edits!
                    editor.commit();
                    }
                textDescriptor.close();
                }
            catch (IOException e)
                {
                e.printStackTrace();
                }
            }
        }
        
    public void onSelect( String text )
        {
        Scribe.locus( Debug.VIEW );
        editText.setText( text );
        
        Intent intent = new Intent();
        intent.setClass( this, TeacherActivity.class);
        //intent.setClass( this, BiDictActivity.class);
        intent.putExtra( TeacherActivity.FIND, text );
        intent.putExtra( TeacherActivity.PARA, "ez lesz a para szovege"); 
        startActivity( intent );
        }
    }
