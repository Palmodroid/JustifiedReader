package digitalgarden.justifiedreader.scribe;

import android.content.Context;
import android.os.Bundle;

/**
 * With the use of ScribeSettings all settings of scribe can be set at once.
 * ScribeSetting is the preferred mode to set settings.
 * Primary and secondary configs are set independently.
 * Both configs derive from default values.
 * ScribeSettings should be used at each entry points!
 * A new ScribeSettings can be get from Scribe.setConfig() method.
 * At the end init() or initSecondary() should be used to make settings!
 */
public class ScribeSettings
    {
    /** Bundle to contain all settings */
    private Bundle state = new Bundle( 11 );


    /** Enables logging (main "switch") */
    public ScribeSettings enable()
        {
        state.putBoolean( Scribe.ENABLED, true );
        return this;
        }

    /** Enables logging (main "switch") if parameter is true */
    public ScribeSettings enable( boolean enable )
        {
        state.putBoolean( Scribe.ENABLED, enable );
        return this;
        }

    /** Disables logging (main "switch") */
    public ScribeSettings disable()
        {
        state.putBoolean( Scribe.ENABLED, false );
        return this;
        }

    /** Enables debug-logging */
    public ScribeSettings enableDebug()
        {
        state.putBoolean( Scribe.DEBUG_ENABLED, true );
        return this;
        }

    /** Enables debug-logging if parameter is true */
    public ScribeSettings enableDebug( boolean enable)
        {
        state.putBoolean( Scribe.DEBUG_ENABLED, enable );
        return this;
        }

    /** Disables debug-logging */
    public ScribeSettings disableDebug()
        {
        state.putBoolean( Scribe.DEBUG_ENABLED, false );
        return this;
        }


    /**
     * Enables logging to system-log
     * @param logTag  tag appearing in system log
     */
    public ScribeSettings enableSysLog( String logTag )
        {
        state.putString( Scribe.LOG_TAG, logTag );
        return this;
        }

    /**
     * Enables logging to system-log with default tag
     */
    public ScribeSettings enableSysLog()
        {
        // default will be used if key is missing
        return this;
        }

    /** Disables logging to system-log */
    public ScribeSettings disableSysLog()
        {
        // null explicitly disables sys log
        state.putString( Scribe.LOG_TAG, null );
        return this;
        }


    /**
     * Enables logging to file log under default file name
     * @param fileName  log-file's name
     */
    public ScribeSettings enableFileLog( String fileName )
        {
        state.putString( Scribe.FILE_NAME, fileName );
        return this;
        }

    /** Enables logging to file log under default file name */
    public ScribeSettings enableFileLog()
        {
        // default will be used if key is missing
        return this;
        }

    /** Disables logging to file log */
    public ScribeSettings disableFileLog()
        {
        // null explicitly disables sys log
        state.putString( Scribe.FILE_NAME, null );
        return this;
        }


    // Toast log cannot be set as general!!


    /**
     * Sets directory path (on sd-card) for log-file
     * @param directoryName  path of logging directory
     */
    public ScribeSettings setDirectoryName( String directoryName )
        {
        state.putString( Scribe.DIRECTORY_NAME, directoryName );
        return this;
        }


    /**
     * Sets mask.
     * @param mask
     */
    public ScribeSettings setMask( int mask )
        {
        state.putInt( Scribe.MASK, mask );
        return this;
        }

    /** Enables time stamp in file-log */
    public ScribeSettings enableTimeStamp()
        {
        state.putBoolean( Scribe.TIME_STAMP, true );
        return this;
        }

    /** Disables time stamp in file-log */
    public ScribeSettings disableTimeStamp()
        {
        state.putBoolean( Scribe.TIME_STAMP, false );
        return this;
        }

    /** Enables space stamp (class.method) in file-log */
    public ScribeSettings enableSpaceStamp()
        {
        state.putBoolean( Scribe.SPACE_STAMP, true );
        return this;
        }

    /** Disables space stamp (class.method) in file-log */
    public ScribeSettings disableSpaceStamp()
        {
        state.putBoolean( Scribe.SPACE_STAMP, false );
        return this;
        }


    /**
     * Initializes Scribe with the values set by ScribeSettings previously
     * @param context to set the default file name with
     */
    public void init( Context context )
        {
        state.putString( Scribe.PACKAGE, context.getPackageName() );
        init();
        }

    /**
     * Initializes Scribe with the values set by ScribeSettings previously
     */
    public void init( )
        {
        Scribe.setConfig( state );
        }

    /**
     * Initializes Scribe secondary with the values set by ScribeSettings previously
     * Default file name is preserved, so initSecondary() could be used after init(context).
     * @param context to set the default file name with
     */
    public void initSecondary( Context context )
        {
        state.putString( Scribe.PACKAGE, context.getPackageName() );
        initSecondary();
        }

    /**
     * Initializes Scribe secondary with the values set by ScribeSettings previously
     */
    public void initSecondary( )
        {
        Scribe.setConfigSecondary( state );
        }
    }
