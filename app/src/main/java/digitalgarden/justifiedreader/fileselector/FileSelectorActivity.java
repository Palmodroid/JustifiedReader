package digitalgarden.justifiedreader.fileselector;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import digitalgarden.justifiedreader.*;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.debug.*;

/* 
 * A file-chooser activity az sd-kártya könyvtáraiból segít kiválasztani egy file-t
 * A kiválasztás standard módjáról az AndroidManifest.xml gondoskodik:
    <intent-filter>
        <action android:name="android.intent.action.GET_CONTENT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="file/*" />
    </intent-filter>
 */

public class FileSelectorActivity extends FragmentActivity
    {
    // Intent-ben extra String jelzésére: SD-kártyán belüli indító könyvtár
    public static String DIRECTORY_SUB_PATH = "DIRECTORY_SUB_PATH";

    // Intent-ben extra String jelzésére: csak az adott végződéssel (kiterjesztéssel)
    // rendelkező file-ok kerülnek megjelenítésre
    public static String FILE_ENDING = "FILE_ENDING";

    // Intent-ben extra String jelzésére: file neve
    public static String FILE_NAME = "FILE_NAME";


    // Újraindítások között megtartott globális változók egy Fragmentben
    // setRetainInstance(true) kell legyen
    public static class RetainedVariables extends Fragment
        {
        // Root könyvtár, mely alatt a program nem keres
        // (Alapértelmezetten az SD-kártya)
        File rootDir;

        // Aktuálisan megjelenített könyvtár
        File currentDir;

        // Csak a megadott végződéssel (kiterjesztéssel) rendelkező file-ok kerülnek listázásra
        String fileEnding;

        // A lista első látható elemének helyét megőrizzük az elforgatáskor
        // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
        int positionOfFirstItem = -1;
        int topOfFirstItem;

        int positionOfFileSection;
        }

    private RetainedVariables variables;


    // UI elemek
    private ListView list;
    private EditText filter;
    private TextView ending;


    // onCreate kizárólag az UI elemek előkészítésére szolgál
    @Override
    public void onCreate(Bundle savedInstanceState) 
        {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.file_chooser_activity );
        
        Log.i("FILECHOOSER", "FileChooserActivity started");

        list = (ListView)findViewById( R.id.list );

        // A lista megérintésekor eltűnik a billentyűzet
        list.setOnTouchListener(new OnTouchListener()
            {
            @Override
            public boolean onTouch(View v, MotionEvent event)
                {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    {
                    // A focus átáll, de nem tűnik el a billentyűzet

                    // list.setFocusable( true ); az utóbbi ezt is beállítja
                    // list.setFocusableInTouchMode( true );
                    // list.requestFocusFromTouch();
                    // list.requestFocus();


                    // 2. módszer InputMethodManager - a focus nem változik
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow( list.getWindowToken(), 0);
                    }
                return false;
                }

            } );

        // Elem kiválasztásakor
        // DIR - továbblépünk a könyvtárra
        // PARENT_DIR - eggyel vissza - ilyenkor a jelenlegi könyvtár lesz a lista első eleme
        // FILE (minden más) - a kiválasztott file adataival (setData) visszatérünk
        list.setOnItemClickListener( new OnItemClickListener()
            {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
                {
                FileEntry fileEntry = (FileEntry) list.getItemAtPosition( position );

                if ( fileEntry.getType() == FileEntry.Type.DIR )
                    {
                    populateList( fileEntry.getFile(), null );
                    }
                else if ( fileEntry.getType() == FileEntry.Type.PARENT_DIR )
                    {
                    populateList( variables.currentDir.getParentFile(), variables.currentDir  );
                    }
                else
                    {
                    String rootPath = variables.rootDir.getAbsolutePath();
                    String currentPath = variables.currentDir.getAbsolutePath();
                    String subPath = currentPath;

                    if ( currentPath.startsWith(rootPath) )
                        {
                        subPath = currentPath.substring(rootPath.length());
                        }

                    Scribe.debug(Debug.SELECTOR, "Root dir: " + rootPath);
                    Scribe.debug(Debug.SELECTOR, "Current dir: " + currentPath);
                    Scribe.debug(Debug.SELECTOR, "Sub dir: " + subPath);

                    Scribe.debug(Debug.SELECTOR, "File entry: " + fileEntry.getFile().getAbsolutePath());
                    Scribe.debug(Debug.SELECTOR, "File name: " + fileEntry.getName());



                    Intent returnIntent = new Intent();
                    //returnIntent.putExtra("RESULT", o.getPath());
                    returnIntent.setData(Uri.parse("file://" + fileEntry.getFile().getAbsolutePath() ));

                    returnIntent.putExtra(DIRECTORY_SUB_PATH, subPath);
                    returnIntent.putExtra(FILE_NAME, fileEntry.getName() );

                    setResult(RESULT_OK, returnIntent);
                    finish();
                    //Toast.makeText(this, "File Clicked: "+o.getName(), Toast.LENGTH_SHORT).show();
                    }
                }
            } );

        // szöveg beírásakor szűrjük a listát
        // erre két lehetőség lenne
        // 1. újra leválogatjuk a könyvtárat (lassú)
        // 2. adapter saját filterével a teljes leválogatott könyvtárat szűkítjük tovább (ez a megvalósított)
        filter = (EditText)findViewById( R.id.search);
        filter.addTextChangedListener( new TextWatcher() 
            {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
                {
                FileEntryAdapter adapter = ((FileEntryAdapter)list.getAdapter());
                if (adapter != null)
                    {
                    adapter.getFilter().filter(s);
                    if ( s.length() > 0 )
                        list.setSelectionFromTop( variables.positionOfFileSection, 0 );
                    }
                }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
                {
                // TODO Auto-generated method stub
                }

            @Override
            public void afterTextChanged(Editable s)
                {
                // TODO Auto-generated method stub
                }
            } );

        ending = (TextView)findViewById( R.id.ending );
        }


    // A Fragmentek az onResumeFragments-től elérhetőek,
    // ezért itt történik az UI elemek feltöltése a megőrzött Fragment alapján
    // Ha még nem létezik ez a Fragment, akkor új indítás történt
    @Override
    public void onResumeFragments()
        {
        super.onResumeFragments();

        FragmentManager fragmentManager = getSupportFragmentManager();

        variables = (RetainedVariables)fragmentManager.findFragmentByTag("VAR");
        if (variables == null)
            {
            Log.d("FILECHOOSER", "VAR Fragment created");

            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            variables = new RetainedVariables();
            variables.setRetainInstance( true );
            fragmentTransaction.add(variables, "VAR");
            fragmentTransaction.commit();

            // Van egyaltalan SD kartya?
            variables.rootDir = Environment.getExternalStorageDirectory();

            Intent intent = getIntent();

            variables.fileEnding = intent.getStringExtra(FILE_ENDING);
            if ( variables.fileEnding == null )
                variables.fileEnding="";

            variables.currentDir = variables.rootDir;
            String subPath = intent.getStringExtra(DIRECTORY_SUB_PATH);
            if (subPath != null)
                {
                String[] subPathItems = subPath.split( System.getProperty( "file.separator" ));

                for (String subPathItem : subPathItems)
                    {
                    if (subPathItem.length() > 0)
                        {
                        File temp = new File( variables.currentDir, subPathItem);
                        if (temp.isDirectory())
                            {
                            variables.currentDir = temp;
                            }
                        else
                            break;
                        }
                    }
                }

            }
        else
            Log.d("FILECHOOSER", "VAR Fragment found");

        ending.setText( variables.fileEnding );
        populateList( variables.currentDir, null );

        // list.setFocusable( true ); //az utóbbi ezt is beállítja
        // list.setFocusableInTouchMode( true );
        // list.requestFocusFromTouch();
        list.requestFocus();

        // InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        // imm.hideSoftInputFromWindow( list.getWindowToken(), 0);
        }


    // Lista feltöltése
    // - dir - az aktuálisan feltöltendő könyvtár
    // - previousDir - a könyvtár, ahonnan érkeztünk (vagy null)
    // Ha previousDir található dir elemei között, akkor az lesz a lista első eleme
    // Ennek a könyvtárak közötti visszalépéskor van jelentősége.
    // Ilyenkor a previousDir értéke a currentDir lesz, majd - felhasználás után! - kerül a currentDir ténylegesen átállításra
    private void populateList(File dir, File previousDir) 
        {
        Log.d("FILECHOOSER", "Populating " + dir.getPath());

        if ( dir.equals( variables.rootDir) )
            this.setTitle("Contents of External Storage");
        else
            this.setTitle("Current Dir: " + dir.getName());

        List<FileEntry>dirEntries = new ArrayList<FileEntry>();
        List<FileEntry>fileEntries = new ArrayList<FileEntry>();
        SimpleDateFormat sdf=new SimpleDateFormat("yy.MM.dd", Locale.ENGLISH);

        File[] filesInDir = dir.listFiles();	// null ellenorzes? leválasztották az sd-t?
                                                // ehelyett kellene inkább egy Dialog
        if (filesInDir == null)
            {
            FileSelectorDialog.showNewDialog(this, FileSelectorDialog.Type.SD_CARD_ERROR);
            return;
            }

        for(File file: filesInDir)
            {
            if ( !file.isHidden() )
                {
                if( file.isDirectory() )
                    dirEntries.add( new FileEntry(file.getName(), "Folder with " + file.list().length + " items", FileEntry.Type.DIR, file) );
                else if ( file.getName().endsWith(variables.fileEnding) )
                    {
                    fileEntries.add(new FileEntry(file.getName(), "File Size: " + file.length() + " Date: " + sdf.format(new Date( file.lastModified() )), FileEntry.Type.FILE, file));
                    }
                }
            }

        Collections.sort(dirEntries);

        // Divider-t is hozzáadjuk
        dirEntries.add( 0, new FileEntry( getString( R.string.divider_directories ) , null, FileEntry.Type.DIVIDER, null ));

        if(!dir.equals(variables.rootDir))
            dirEntries.add(0,new FileEntry( "..", "Parent Directory", FileEntry.Type.PARENT_DIR, dir.getParentFile() ));

        variables.positionOfFileSection = dirEntries.size();

        // Divider-t is hozzáadjuk
        dirEntries.add( new FileEntry( getString( R.string.divider_files ) , null, FileEntry.Type.DIVIDER, null ));

        Collections.sort(fileEntries);
        dirEntries.addAll(fileEntries);

        FileEntryAdapter adapter = new FileEntryAdapter(FileSelectorActivity.this, dirEntries);
        list.setAdapter(adapter);

        adapter.getFilter().filter( filter.getText().toString() );
        Log.e("FILECHOOSER", "filtered with " + filter.getText().toString() );

        // Ha nincs elmentett érték
        if ( previousDir == null )
            {
            if ( variables.positionOfFirstItem != -1 )
                {
                list.setSelectionFromTop(variables.positionOfFirstItem, variables.topOfFirstItem);
                variables.positionOfFirstItem = -1;
                }
            else if ( filter.length() > 0 )
                {
                list.setSelectionFromTop( variables.positionOfFileSection, 0 );
                }
            }
        else // if (previousDir != null)
            {
            for (int i=0; i < adapter.getCount(); i++)
                {
                if ( adapter.getItem(i).getType() == FileEntry.Type.DIR && adapter.getItem(i).getFile().equals( previousDir ) )
                    {
                    list.setSelectionFromTop(i, 0);
                    break;
                    }
                if ( adapter.getItem(i).getType() != FileEntry.Type.DIR )
                    break;
                }
            }

        variables.currentDir = dir;
        }
    

    // Leálláskor elmentjük a lista pozícióját
    @Override
    public void onPause()
        {
        super.onPause();

        variables.positionOfFirstItem = list.getFirstVisiblePosition();
        View v = list.getChildAt(0);
        variables.topOfFirstItem = (v == null) ? 0 : v.getTop();
        }
  
    // A root-könyvtárig (SD-card) ugyanaz, mint az előző könyvtár, ott viszont Cancel-ként üzemel 
    @Override
    public void onBackPressed()
        {
        if ( !variables.currentDir.equals( variables.rootDir ) )
            {
            populateList( variables.currentDir.getParentFile(), variables.currentDir );
            }
        else
            {
            Intent returnIntent = new Intent();
            setResult(RESULT_CANCELED, returnIntent);
            finish();
            }
        }

    // Menü vezérlése
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
        {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.file_chooser_menu, menu);
        // return true;

        // ! MENU can create new dir and new file, but this is not allowed now
        return false;
        }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
        {
        switch (item.getItemId())
            {
            case R.id.menu_new_file:
                {
                FileSelectorDialog.showNewDialog(this, FileSelectorDialog.Type.CREATE_FILE, filter.getText().toString());
                return true;
                }
            case R.id.menu_new_directory:
                {
                FileSelectorDialog.showNewDialog(this, FileSelectorDialog.Type.CREATE_DIRECTORY, filter.getText().toString());
                return true;
                }
            default:
                return super.onOptionsItemSelected(item);
            }
        }

    // Dialogusokhoz szükséges kommunikáció
    public void onDialogPositiveResult( FileSelectorDialog.Type type, String text)
        {
        switch (type)
            {
            case SD_CARD_ERROR:
                {
                Intent returnIntent = new Intent();
                setResult( RESULT_CANCELED, returnIntent);
                finish();
                break;
                }
            case CREATE_DIRECTORY:
                {
                // write-permission szukseges
                File newDir = new File( variables.currentDir, text );
                if ( newDir.mkdirs() ) // akár alkönyvtárral legyártja
                    {
                    populateList( newDir, null);
                    }
                else
                    {
                    Toast.makeText( this , "Cannot create [" + text + "]", Toast.LENGTH_SHORT).show();
                    FileSelectorDialog.showNewDialog(this, FileSelectorDialog.Type.CREATE_DIRECTORY, text);
                    }
                break;
                }
            case CREATE_FILE:
                {
                File newFile = new File( variables.currentDir, text);

                Intent returnIntent = new Intent();
                //returnIntent.putExtra("RESULT", o.getPath());
                returnIntent.setData(Uri.parse("file://" + newFile.getAbsolutePath() ));
                setResult(RESULT_OK, returnIntent);
                finish();
                //Toast.makeText(this, "File Clicked: "+o.getName(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
