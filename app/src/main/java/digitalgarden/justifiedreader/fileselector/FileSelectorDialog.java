package digitalgarden.justifiedreader.fileselector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.EditText;
import android.widget.Toast;

/**
 * File-selector dialog
 */
public class FileSelectorDialog extends DialogFragment
    {
    public static enum Type
        {
        SD_CARD_ERROR,
        CREATE_DIRECTORY,
        CREATE_FILE
        }

    public static FileSelectorDialog showNewDialog( FragmentActivity activity, Type type )
        {
        return showNewDialog( activity, type, null );
        }

    public static FileSelectorDialog showNewDialog( FragmentActivity activity, Type type, String text)
        {
        FileSelectorDialog fileSelectorDialog;
        FragmentManager fragmentManager = activity.getSupportFragmentManager();

        // Any open dialog should be closed...
        fileSelectorDialog = (FileSelectorDialog) fragmentManager.findFragmentByTag( "DIALOG" );
        if ( fileSelectorDialog != null )
            fileSelectorDialog.dismiss();

        // and a new one should be open and show
        fileSelectorDialog = new FileSelectorDialog();

        Bundle args = new Bundle();
        args.putSerializable("TYPE", type);
        args.putString("TEXT", text);
        fileSelectorDialog.setArguments(args);

        fileSelectorDialog.show( activity.getSupportFragmentManager(), "DIALOG");

        return fileSelectorDialog;
        }

    FileSelectorActivity fileSelectorActivity;

    @Override
    public void onAttach(Activity activity)
        {
        super.onAttach(activity);

        if ( activity instanceof FileSelectorActivity)
            Toast.makeText( activity, "FileChooserActivity a tipusa", Toast.LENGTH_SHORT ).show();
        else
            Toast.makeText( activity, "Csak Activity a tipusa", Toast.LENGTH_SHORT ).show();

        fileSelectorActivity = (FileSelectorActivity) activity;
        }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
        {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder( getActivity() );

        // error check!!
        Bundle args = getArguments();
        final Type type = (Type) args.getSerializable("TYPE");
        final String text = args.getString("TEXT");

        switch ( type )
            {
            case SD_CARD_ERROR:
                {
                alertDialogBuilder.setMessage("Cannot reach SD-Card!");
                alertDialogBuilder.setPositiveButton( "OK", new DialogInterface.OnClickListener()
                    {
                    public void onClick(DialogInterface dialog, int which)
                        {
                        fileSelectorActivity.onDialogPositiveResult( type, null );
                        }
                   });

                FileSelectorDialog.this.setCancelable( false );
                break;
                }

            case CREATE_DIRECTORY:
                {
                alertDialogBuilder.setTitle("Create new directory");

                final EditText name = new EditText( getActivity() );
                name.setText( text );
                alertDialogBuilder.setView( name );

                alertDialogBuilder.setPositiveButton( "Create", new DialogInterface.OnClickListener()
                    {
                    public void onClick(DialogInterface dialog, int which)
                        {
                        fileSelectorActivity.onDialogPositiveResult( type, name.getText().toString() );
                        }
                   });

                alertDialogBuilder.setNegativeButton( "Cancel", null );

                break;
                }

            case CREATE_FILE:
                {
                alertDialogBuilder.setTitle("Create new file");

                final EditText name = new EditText( getActivity() );
                name.setText( text );
                alertDialogBuilder.setView( name );

                alertDialogBuilder.setPositiveButton( "Create", new DialogInterface.OnClickListener()
                    {
                    public void onClick(DialogInterface dialog, int which)
                        {
                        fileSelectorActivity.onDialogPositiveResult( type, name.getText().toString() );
                        }
                    });

                alertDialogBuilder.setNegativeButton( "Cancel", null );

                break;
                }

            }

        return alertDialogBuilder.create();
        }
   
    }
