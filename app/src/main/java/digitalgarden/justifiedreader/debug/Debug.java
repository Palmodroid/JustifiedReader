package digitalgarden.justifiedreader.debug;

import android.content.Context;

import digitalgarden.justifiedreader.scribe.Scribe;


/**
 * Collection of message-limit constants for Scribe and
 * Scribe initialisation.
 */
public class Debug
    {
    // Masks
    public static final int LINE = 0x0002;

    public static final int PARA = 0x0004;
    public static final int TEXT = 0x0008;
    public static final int DRAWTEXT = 0x0010; 
    public static final int WORD = 0x0020;
    public static final int VIEW = 0x0040;
    public static final int SELECTOR = 0x0080;
    public static final int BIDICT = 0x0100;
    public static final int TEACHER = 0x0200;
        
    
    // Constants for PRIMARY configuration
    public static final String LOG_TAG = "SCRIBE_JUST";

        
    /**
     * Scribe primary config initialisation.
     * @param context context containing package information
     */
    public static void initScribe( Context context )
        {
        Scribe.setConfig()
                .enableSysLog( LOG_TAG )                // Primary log-tag
                .init( context );                       // Primary file name : package name

        Scribe.checkLogFileLength(); // Primary log will log several runs
        Scribe.logUncaughtExceptions(); // Primary log will store uncaught exceptions

        Scribe.title("Justified reader has started");
        
        Scribe.setMask( TEACHER ); //VIEW | TEXT | BIDICT);
        }

    }
