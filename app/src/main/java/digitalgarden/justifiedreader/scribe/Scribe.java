package digitalgarden.justifiedreader.scribe;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MODIFICATION
 * 'mask' instead of 'limit'
 *
 * Each message can have a 'level' value (integer)
 * - the lowest bit (odd/even): decides between primary (0) and secondary (1) logs.
 * - the highest bit (negative values): use always (even if mask is 0)
 * - remaining 30 bits define the 'level'
 *
 * Both logs have got a 'mask' value (integer)
 * - lowest and highest bits are indifferent
 * - remaining 30 bits define the 'mask'
 *
 * Messages with negative levels are always shown
 * Messages with positive levels are shown only, if mask and level have ON bits on the same positions
 * ('mask' binary-and 'level' is NOT null)
 */

/**
 * Alternate log.
 * <p>
 * Collection of standalone methods. 
 * These methods are able to send/write log messages without further initialization or closure methods.
 * Permission is needed to write external log-file {@code AndroidManifest.xml}:
 * {@code <uses-permission 
 *  android:name="android.permission.WRITE_EXTERNAL_STORAGE"/> }
 * Permission {@code android.permission.READ_LOGS} is not needed any more.
 * <p>
 * Messages can be sent into three directions:
 * <ul>
 * <li>system log</li>  
 * <li>file log</li>  
 * <li>toast log (default: off)</li>
 * </ul>
 * The whole log or each direction separately can be enabled/disabled.
 * System log's tag can be set.
 * Directory path (on primary sd-card) and log file name can be set. 
 * Helper methods are available to delete log-file and archive longer logs (in several steps). 
 * <p>
 * Configuration:
 * There are two separate configurations, one for odd, and one for even message levels.
 * Odd messages will enable secondary configuration, which will be a copy of the primary configuration at start.
 * Methods with the secondary configuration (without explicit level setting) have "Secondary" endings.
 * Secondary log is useful if we want to separate two logs, one for the programmer and one for the user. 
 * <p>
 * Message types:
 * <ul>
 * <li>TITLE - title (checks log file lenth!)</li>  
 * <li>NOTE - normal message</li>
 * <li>ERROR - error message</li>
 * <li>DEBUG - normal message - if debug is enabled!</li>
 * <li>LOCUS - position in full.class.method (thread) form</li>  
 * </ul>
 * A DEBUG (together with LOCUS) type can be enabled/disabled
 * <p>
 * Log message can be sent from any thread.
 * Configurational settings are also "thread-safe", but these settings will immediately appear on other threads as well,
 * so configurations should be set on the main-thread (before starting any other thread)
 * <p>
 * Configuratins are stored in static variables (ti. application levels). 
 * The values in these variables can be remain between activity restarts.
 * It is a good techniqe to initialise (both) configurations at the program startup point:
 * {@link #init(Context)}
 * Log file default name will be the package name.
 * Different applications store log in different files.
 * <p>
 * Full 'system-log' can be dumped to file-log (primary configuration), or can be erased.
 * Above API16 programs can read only their own log-messages.
 * <p>
 * Uncaught exceptions can also be logged (with primary configuration).
 * Format differs from logcat's format:
 * Exceptions will be in reverse order.
 * Exception which started the exception-cascade will be at the first position.
 * Stack trace will be logged without abbreviations.
 * After logging uncaught exceptions system gets back control.
 * System will log to system log and application will be terminated also by system.
 */
public class Scribe
    {
    /************************************************************
     *                                                          *
     *  Default constant values                                 *
     *                                                          *
     ************************************************************/

    /** Log file will be archived above this size */
    public static final long MAX_LOGFILE_LENGTH = 1024*1024;
    /** Maximum number of archived log files */
    public static final int MAX_LOGFILE = 8;

    /** Debug cannot be limited with this value */
    public static final int NO_LIMIT = -2;
    /** Not limited level value for secondary config */
    public static final int NO_LIMIT_SECONDARY = NO_LIMIT + 1;

    /** Log file's default extension */
    public static final String DEFAULT_FILE_EXT = ".log";
    /** Log file's default name */
    public static final String DEFAULT_FILE_NAME = "scribe" + DEFAULT_FILE_EXT;
    /** Log file's default directory */
    public static final String DEFAULT_DIRECTORY = "";
    /** Default tag for system log */
    public static final String DEFAULT_LOG_TAG = "SCRIBE";

    /** Default enabled switch */
    private static final boolean DEFAULT_ENABLED = true;
    /** Default debug enabled switch */
    private static final boolean DEFAULT_DEBUG_ENABLED = true;
    /** Default mask -1 == all messages enabled */
    private static final int DEFAULT_MASK = -1;
    /** Default time stamp enabled switch */
    private static final boolean DEFAULT_TIME_STAMP = true;
    /** Default space stamp enabled switch */
    private static final boolean DEFAULT_SPACE_STAMP = false;

    // Constants returned by methods
    /** Returned value: everything was OK */
    public static final String OK = "Log ready";
    /** Returned value: logging is disabled */
    public static final String OFF = "Log disabled";
    /** Logging error: returned value starts with this */
    public static final String LOGFILE_ERROR = "LOGFILE ERROR: ";

    /** Path separator */
    // !! Use Windows separator and html separator for html code !!
    private static final String SEPARATOR = "<br>\r\n"; // System.getProperty("line.separator");

    /** HIGHLIGHT **/
    private static final String HIGHLIGHT_START =
            "<mark>";
    private static final String HIGHLIGHT_END =
            "</mark>";

    /** Primary config */
    private static final int PRIMARY_CONFIG = 0;
    /** Secondary config */
    private static final int SECONDARY_CONFIG = 1;

    /************************************************************
     *                                                          *
     *  Tags for bundle of settings                             *
     *                                                          *
     ************************************************************/

    public static String ENABLED = "Enabled";
    public static String DEBUG_ENABLED = "DebugEnabled";
    public static String LOG_TAG = "LogTag";
    public static String FILE_NAME = "FileName";
    public static String DIRECTORY_NAME = "DirectoryName";
    public static String MASK = "Mask";
    public static String TIME_STAMP = "TimeStamp";
    public static String SPACE_STAMP = "SpaceStamp";

    public static String PACKAGE = "PackageName";

    /************************************************************
     *                                                          *
     * Private config variables                                 *
     *                                                          *
     ************************************************************/

    /** Config settings, organised in one nested class */
    private static class Config
        {
        /** Is logging enabled? (Main switch) */
        private boolean enabled;
        /** Is debug-logging enabled? */
        private boolean debugEnabled;
        /** System-log tag. Null disables */
        private String logTag;
        /** File-log file name. Null disables */
        private String fileName;
        /** File-log path (on sd-card). Cannot be null */
        private String directoryName;
        /** Toast-log context (of Activity). Null disables. */
        private Context context;
        /** Messages enabled if mask and level is not null */
        private int mask;
        /** Is time stamp enabled for file-log messages? */
        private boolean timeStampEnabled;
        /** Is space stamp (class.method) enabled for file-log messages? */
        private boolean spaceStampEnabled;
        }

    /** Default log file name: could be changed to package.extension */
    private static volatile String defaultFileName = DEFAULT_FILE_NAME;

    /** Temporary variable to set up config[] first */
    private static final Config primaryConfig = new Config();

    /**
     * Settings for the two configurations.
     * Reference of the array is final; second element can be changed to secondaryConfig.
     * Before its activation secondary config points to the same config as primary config.
     */
    private static final Config config[] = { primaryConfig, primaryConfig };

    /** Separate locks for accessing the two configurations */
    private static final Object[] lock = { new Object(), new Object() };
    // Both config[0] and [1] point to primaryConfig. But only config[0] could be changed
    // (locked by lock[0]). Writing config[1] means activation of secondary config, so it
    // will not point to primaryConfig any more. config[1] is locked by lock[1].

    /** Fill up default values at first start */
    static
        {
        resetAll(PRIMARY_CONFIG);
        }


    /************************************************************
     *                                                          *
     * Synchronized private methods for configurations          *
     *                                                          *
     * 'config' array is final, could not change                *
     * 'config[0]' will always point to primary config          *
     * 'config[1]' will point to primary config, then           *
     *            to secondary config (after activation)        *
     * 'lock[0]' will lock config[0] and its variables          *
     * 'lock[1]' will lock config[1] and its variables          *
     *                                                          *
     * Config class cannot synchronize itself, because          *
     * config[1] could change, and should be synchronized       *
     * as well!!                                                *
     *                                                          *
     ************************************************************/

    /**
     * Activate secondary config
     * @param conf PRIMARY/SECONDARY configuration
     */
    private static void activateSecondaryConfig( int conf )
        {
        if ( conf != SECONDARY_CONFIG )
            return;

        // config[PRIMARY_CONFIG] will never change
        synchronized ( lock[SECONDARY_CONFIG] )
            {
            if ( config[PRIMARY_CONFIG] != config[SECONDARY_CONFIG] )
                return;
            }

        // clone is needed only here
        Config secondaryConfig = new Config();
        synchronized ( lock[PRIMARY_CONFIG] )
            {
            secondaryConfig.enabled = config[PRIMARY_CONFIG].enabled;
            secondaryConfig.debugEnabled = config[PRIMARY_CONFIG].debugEnabled;
            secondaryConfig.logTag = config[PRIMARY_CONFIG].logTag;
            secondaryConfig.fileName = config[PRIMARY_CONFIG].fileName;
            secondaryConfig.directoryName = config[PRIMARY_CONFIG].directoryName;
            secondaryConfig.mask = config[PRIMARY_CONFIG].mask;
            secondaryConfig.timeStampEnabled = config[PRIMARY_CONFIG].timeStampEnabled;
            secondaryConfig.spaceStampEnabled = config[PRIMARY_CONFIG].spaceStampEnabled;
            secondaryConfig.context = null;
            }

        synchronized ( lock[SECONDARY_CONFIG] )
            {
            // Just to be sure, that it is not changed during this method.
            if ( config[PRIMARY_CONFIG] == config[SECONDARY_CONFIG] )
                config[SECONDARY_CONFIG] = secondaryConfig;
            // If already changed, we do not modify the previous "secondary config"
            }
        }

    /**
     * Resets all log config settings to original values.
     * Default values should be identical with setAll!!
     * @param conf PRIMARY/SECONDARY configuration
     */
    private static void resetAll( int conf )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].enabled = DEFAULT_ENABLED;
            config[conf].debugEnabled = DEFAULT_DEBUG_ENABLED;
            config[conf].logTag = DEFAULT_LOG_TAG;
            config[conf].fileName = defaultFileName;
            config[conf].directoryName = DEFAULT_DIRECTORY;
            config[conf].mask = DEFAULT_MASK;
            config[conf].timeStampEnabled = DEFAULT_TIME_STAMP;
            config[conf].spaceStampEnabled = DEFAULT_SPACE_STAMP;
            config[conf].context = null;
            }
        }

    /**
     * Sets enabled state (main switch)
     * @param conf PRIMARY/SECONDARY configuration
     * @param enable true if enabled
     */
    private static void setEnabled( int conf, boolean enable )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].enabled = enable;
            }
        }

    /**
     * Returns enabled state (main switch)
     * @param conf PRIMARY/SECONDARY configuration
     * @return true if enabled
     */
    private static boolean isEnabled( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].enabled;
            }
        }

    /**
     * Sets enabled state of debug-log
     * @param conf PRIMARY/SECONDARY configuration
     * @param enable true if enabled
     */
    private static void setDebugEnabled( int conf, boolean enable )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].debugEnabled = enable;
            }
        }

    /**
     * Returns enabled state of debug-log
     * @param conf PRIMARY/SECONDARY configuration
     * @return true if enabled
     */
    private static boolean isDebugEnabled( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].debugEnabled;
            }
        }

    /**
     * Sets system-log tag
     * @param conf PRIMARY/SECONDARY configuration
     * @param logTag	tag appearing in system log, null if disabled
     */
    private static void setSysLog( int conf, String logTag )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].logTag = logTag;
            }
        }

    /**
     * Gets system-log tag
     * @param conf PRIMARY/SECONDARY configuration
     * @return system-log tag, null if disabled
     */
    private static String getSysLog( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].logTag;
            }
        }

    /**
     * Creates default file name from package name. It will be used as default.
     * @param context application's context
     */
    private static void setDefaultFileName( Context context )
        {
        defaultFileName = context.getPackageName() + DEFAULT_FILE_EXT;
        }

    /**
     * Sets file name for file-log
     * @param conf PRIMARY/SECONDARY configuration
     * @param fileName	name of log file, null if disabled
     */
    private static void setFileName( int conf, String fileName )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].fileName = fileName;
            }
        }

    /**
     * Sets directory path (on sd-card) for file-log
     * @param conf PRIMARY/SECONDARY configuration
     * @param directoryName directory path; null and "" mean root on sdcard
     */
    private static void setDirectoryName( int conf, String directoryName )
        {
        activateSecondaryConfig( conf );
        if ( directoryName == null )
            directoryName = "";
        synchronized ( lock[conf] )
            {
            config[conf].directoryName = directoryName;
            }
        }

    /**
     * Gets log-directory (directory name completed with external storage path).
     * If path does not exist or is not a directory, root of external storage path will be used.
     * @param conf PRIMARY/SECONDARY configuration
     * @return log-directory
     */
    private static File getLogDirectory( int conf )
        {
        File directory;

        synchronized ( lock[conf] )
            {
            directory = new File( Environment.getExternalStorageDirectory(), config[conf].directoryName );
            }

        if ( !directory.isDirectory() )
            directory = Environment.getExternalStorageDirectory();

        return directory;
        }

    /**
     * Gets log-file (file name completed with directory and external storage path).
     * If path does not exist or is not a directory, root of external storage path will be used.
     * @param conf PRIMARY/SECONDARY configuration
     * @return log-file
     */
    private static File getLogFile( int conf )
        {
        synchronized ( lock[conf] )
            {
            if (config[conf].fileName == null)
                return null;
            else
                return new File( getLogDirectory(conf), config[conf].fileName);
            }
        }

    /**
     * Gets log-file with version (file name completed with directory and external storage path AND version).
     * If path does not exist or is not a directory, root of external storage path will be used.
     * @param conf PRIMARY/SECONDARY configuration
     * @param version will be append at the end of the basename (0 omitted)
     * @return log-file
     */
    private static File getLogFile( int conf, int version )
        {
        synchronized ( lock[conf] )
            {
            if (config[conf].fileName == null)
                return null;
            else
                {
                String base = config[conf].fileName;
                String ext = "";

                int dot = base.lastIndexOf('.');
                if ( dot >= 0)
                    {
                    ext = base.substring( dot );
                    base = base.substring(0, dot);
                    }

                return new File( getLogDirectory(conf), base + ( version > 0  ? "_" + version : "") + ext);
                }
            }
        }

    /**
     * Sets context for toast-log
     * @param conf PRIMARY/SECONDARY configuration
     * @param context	context of the activity, null if disabled
     */
    private static void setContext( int conf, Context context )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].context = context;
            }
        }

    /**
     * Gets context for toast-log
     * @param conf PRIMARY/SECONDARY configuration
     * @return context of the activity, null if disabled
     */
    private static Context getContext( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].context;
            }
        }

    /**
     * Sets mask for log-level
     * @param conf PRIMARY/SECONDARY configuration
     * @param mask
     */
    private static void setMask( int conf, int mask )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].mask = mask;
            }
        }

    /**
     * Gets mask for log-level
     * @param conf PRIMARY/SECONDARY configuration
     * @return mask
     */
    private static int getMask( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].mask;
            }
        }

    /**
     * Check whether level is enabled.
     * All negative levels are enabled.
     * @param level message level
     * @return true if level is enabled
     */
    private static boolean isLevelEnabled( int level )
        {
        // Check highest (sign) bit - it is always enabled (even if mask is 0)
        if ( level < 0 )
            return true;

        // Do not check lowest bit (can be 0 or 1) - it only refers to conf
        return (level & getMask( (int)(level%2L) )) > 1L;
        }

    /**
     * Sets time stamp enabled for file-log messages
     * @param conf PRIMARY/SECONDARY configuration
     * @param enable true if enabled
     */
    private static void setTimeStampEnabled( int conf, boolean enable )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].timeStampEnabled = enable;
            }
        }

    /**
     * Returns time stamp enabled state (for file-log messages)
     * @param conf PRIMARY/SECONDARY configuration
     * @return true if enabled
     */
    private static boolean isTimeStampEnabled( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].timeStampEnabled;
            }
        }

    /**
     * Current time in format "(yy-MM-dd HH:mm:ss.SSS) ".
     * @return current time as formatted string
     */
    private static String timeStamp()
        {
        SimpleDateFormat sdf=new SimpleDateFormat( "yy-MM-dd HH:mm:ss.SSS", Locale.US );
        return "(" + sdf.format( new Date() ) + ") ";
        }

    /**
     * Current time in format "(yy-MM-dd HH:mm:ss.SSS) ", if enabled in this config.
     * @param conf PRIMARY/SECONDARY configuration
     * @return current time as formatted string (empty string if disabled)
     */
    private static String timeStamp( int conf )
        {
        if ( !isTimeStampEnabled( conf ) )
            return "";
        return timeStamp();
        }

    /**
     * Sets space stamp (class.method) enabled for file-log messages
     * @param conf PRIMARY/SECONDARY configuration
     * @param enable true if enabled
     */
    private static void setSpaceStampEnabled( int conf, boolean enable )
        {
        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].spaceStampEnabled = enable;
            }
        }

    /**
     * Returns space stamp enabled state (for file-log messages)
     * @param conf PRIMARY/SECONDARY configuration
     * @return true if enabled
     */
    private static boolean isSpaceStampEnabled( int conf )
        {
        synchronized ( lock[conf] )
            {
            return config[conf].spaceStampEnabled;
            }
        }

    /**
     * Current position in (class.method) format, if enabled in this config.
     * @param conf PRIMARY/SECONDARY configuration
     * @return current position (empty string if disabled)
     */
    private static String spaceStamp( int conf )
        {
        if ( !isSpaceStampEnabled( conf ) )
            return "";

        StackTraceElement[] element = new Throwable().getStackTrace();

        int index = 1;
        while ( element[index].getFileName().startsWith("Scribe.") )
            index++;

        String c = element[index].getClassName();
        int p = c.lastIndexOf('.');
        if ( p >= 0)
            c = c.substring( p+1, c.length());
        return " (" + c + "." + element[index].getMethodName() + ")";
        }

    /**
     * Current position in 'package.class.method (thread)' format
     * @return current position
     */
    private static String spaceStamp()
        {
        StackTraceElement[] element = new Throwable().getStackTrace();

        int index = 1;
        while ( element[index].getFileName().startsWith("Scribe.") )
            index++;

        return
            element[index].getClassName() + "." +
            element[index].getMethodName() + " (" +
            Thread.currentThread().getName() + ")";
        }

    /**
     * Returns to previously saved config settings. Toast-log will be disabled.
     * setAll can be used to explicitly set scribe settings,
     * missing settings will be set to their default values.
     * Default file name will be set only, if bundle contains it.
     * Default values should be identical with resetAll!!
     * @param conf PRIMARY/SECONDARY configuration
     * @param state  settings returned by {@link #getAll(int)}
     */
    private static void setAll( int conf, Bundle state )
        {
        // defaultPackageName will be set only, if bundle contains it!
        if ( state.containsKey( PACKAGE ))
            {
            defaultFileName = state.getString( PACKAGE ) + DEFAULT_FILE_EXT;
            }

        activateSecondaryConfig( conf );
        synchronized ( lock[conf] )
            {
            config[conf].enabled = state.getBoolean( ENABLED, DEFAULT_ENABLED );
            config[conf].debugEnabled = state.getBoolean( DEBUG_ENABLED, DEFAULT_DEBUG_ENABLED );

            if ( state.containsKey( LOG_TAG ))
                {
                // Can be null! == Disabled
                config[conf].logTag = state.getString( LOG_TAG );
                }
            else
                {
                config[conf].logTag = DEFAULT_LOG_TAG;
                }

            if ( state.containsKey( FILE_NAME ))
                {
                // Can be null! == Disabled
                config[conf].fileName = state.getString( FILE_NAME );
                }
            else
                {
                config[conf].fileName = defaultFileName;
                }

            // Cannot be null !
            config[conf].directoryName = state.getString( DIRECTORY_NAME, DEFAULT_DIRECTORY);
            config[conf].mask = state.getInt( MASK, DEFAULT_MASK );
            config[conf].timeStampEnabled = state.getBoolean( TIME_STAMP, DEFAULT_TIME_STAMP );
            config[conf].spaceStampEnabled = state.getBoolean( SPACE_STAMP, DEFAULT_SPACE_STAMP );
            config[conf].context = null;  // Context can be stored only temporary! It is switched of during save/load.
            }
        }

    /**
     * Gets all log config settings. Toast-log will be disabled.
     * @param conf PRIMARY/SECONDARY configuration
     * @return bundle of primary log config settings
     */
    private static Bundle getAll( int conf )
        {
        Bundle state = new Bundle( 10 );

        synchronized ( lock[conf] )
            {
            state.putBoolean( ENABLED, config[conf].enabled);
            state.putBoolean( DEBUG_ENABLED, config[conf].debugEnabled);
            state.putString( LOG_TAG, config[conf].logTag);
            state.putString( FILE_NAME, config[conf].fileName);
            state.putString( DIRECTORY_NAME, config[conf].directoryName);
            state.putInt( MASK, config[conf].mask);
            state.putBoolean( TIME_STAMP, config[conf].timeStampEnabled);
            state.putBoolean( SPACE_STAMP, config[conf].spaceStampEnabled);
            config[conf].context = null;  // Context can be stored only temporary! It is switched of during save/load.
            }
        return state;
        }


    /************************************************************
     *                                                          *
     *  Intializations                                          *
     *                                                          *
     *  Initialization is not needed.                           *
     *  Configs are stored on application level (static),       *
     *  it is a good idea to reset configs at entry level.      *
     *  (Application, Activity etc.)                            *
     *                                                          *
     ************************************************************/

    /**
     * Configs reset to initial values, secondary config is inactivated.
     * Default file name resets to DEFAULT_FILE_NAME.
     * !! ScribeSettings should be preferred !!
     */
    public static void init()
        {
        defaultFileName = DEFAULT_FILE_NAME; // defaultFileName is volatile !!
        resetAll( PRIMARY_CONFIG );

        synchronized ( lock[SECONDARY_CONFIG] )
            {
            config[SECONDARY_CONFIG] = primaryConfig;
            }
        }

    /**
     * Configs reset to initial values, secondary config is inactivated.
     * Default file name will be the package name.
     * !! ScribeSettings should be preferred !!
     */
    public static void init( Context context )
        {
        setDefaultFileName( context ); // defaultFileName is volatile !!
        resetAll( PRIMARY_CONFIG );

        synchronized ( lock[SECONDARY_CONFIG] )
            {
            config[SECONDARY_CONFIG] = primaryConfig;
            }
        }

    /**
     * Helper method to return a new instance of ScribeSettings.
     * This instance could be used to set Scribe config.
     * @return a new, empty ScribeSettings instance
     */
    public static ScribeSettings setConfig()
        {
        return new ScribeSettings();
        }

    /************************************************************
     *                                                          *
     *  User control of PRIMARY config                          *
     *                                                          *
     *	Primary config is used for ALL message levels           *
     *  before activation of a secondary config                 *
     *	Primary config is used only for EVEN message leves      *
     *	after activation of secondary config                    *
     *                                                          *
     ************************************************************/

    /**
     * Returns log config. Toast-log will be disabled.
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @return bundle of log config settings
     * <p>
     * @see #setConfig(Bundle)
     * @see #getConfigSecondary()
     */
    public static Bundle getConfig()
        {
        return getAll( PRIMARY_CONFIG );
        }

    /**
     * Returns to previously saved or explicite config settings. Toast-log will be disabled.
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param state  settings returned by {@link #getAll( int )}
     * <p>
     * @see #getConfig()
     * @see #setConfigSecondary( Bundle state )
     */
    public static void setConfig( Bundle state )
        {
        setAll( PRIMARY_CONFIG, state );
        }

    /**
     * Enables logging (main "switch")
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enable()
        {
        setEnabled( PRIMARY_CONFIG, true );
        }

    /**
     * Disables logging (main "switch")
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disable()
        {
        setEnabled( PRIMARY_CONFIG, false );
        }

    /**
     * Enables debug-logging
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enableDebug()
        {
        setDebugEnabled( PRIMARY_CONFIG, true );
        }

    /**
     * Disables debug-logging
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableDebug()
        {
        setDebugEnabled( PRIMARY_CONFIG, false );
        }

    /**
     * Enables logging to system-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param logTag  tag appearing in system log
     */
    public static void enableSysLog( String logTag )
        {
        setSysLog( PRIMARY_CONFIG, logTag );
        }

    /**
     * Enables logging to system-log with default tag
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enableSysLog()
        {
        setSysLog( PRIMARY_CONFIG, DEFAULT_LOG_TAG );
        }

    /**
     * Disables logging to system-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableSysLog()
        {
        setSysLog( PRIMARY_CONFIG, null );
        }

    /**
     * Enables logging to file log under default file name
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param fileName  log-file's name
     */
    public static void enableFileLog( String fileName )
        {
        setFileName( PRIMARY_CONFIG, fileName );
        }

    /**
     * Enables logging to file log under default file name
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enableFileLog()
        {
        setFileName( PRIMARY_CONFIG, defaultFileName );
        }

    /**
     * Disables logging to file log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableFileLog()
        {
        setFileName( PRIMARY_CONFIG, null );
        }

    /**
     * Enables logging to toast log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param context  context of the activity
     */
    public static void enableToastLog( Context context )
        {
        setContext( PRIMARY_CONFIG, context );
        }

    /**
     * Disables logging to toast log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableToastLog()
        {
        setContext( PRIMARY_CONFIG, null );
        }

    /**
     * Sets directory path (on sd-card) for log-file
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param directoryName  path of logging directory
     */
    public static void setDirectoryName( String directoryName )
        {
        setDirectoryName( PRIMARY_CONFIG, directoryName );
        }

    /**
     * Sets mask.
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     * @param mask
     */
    public static void setMask( int mask )
        {
        setMask( PRIMARY_CONFIG, mask );
        }

    /**
     * Resets mask to its original -1 value. No messages will be limited.
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void unMask( )
        {
        setMask( PRIMARY_CONFIG, DEFAULT_MASK );
        }

    /**
     * Enables time stamp in file-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enableTimeStamp()
        {
        setTimeStampEnabled( PRIMARY_CONFIG, true );
        }

    /**
     * Disables time stamp in file-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableTimeStamp()
        {
        setTimeStampEnabled( PRIMARY_CONFIG, false );
        }

    /**
     * Enables space stamp (class.method) in file-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void enableSpaceStamp()
        {
        setSpaceStampEnabled( PRIMARY_CONFIG, true );
        }

    /**
     * Disables space stamp (class.method) in file-log
     * PRIMARY Config - valid for ALL/EVEN levels after activation of sec. config
     */
    public static void disableSpaceStamp()
        {
        setSpaceStampEnabled( PRIMARY_CONFIG, false );
        }


    /************************************************************
     *                                                          *
     *  User control of Secondary config                        *
     *                                                          *
     *	Secondary config is used for ODD message levels         *
     *	Secondary config will be active only                    *
     *  after the first modification of sec. config values      *
     *                                                          *
     ************************************************************/

    /**
     * Returns log config. Toast-log will be disabled.
     * SECONDARY Config - valid for ALL/ODD levels after activation of sec. config
     * @return bundle of log config settings
     * <p>
     * @see #getConfig()
     * @see #setConfigSecondary( Bundle state )
     */
    public static Bundle getConfigSecondary()
        {
        return getAll( SECONDARY_CONFIG );
        }

    /**
     * Returns to previously saved or explicite config settings. Toast-log will be disabled.
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param state  settings returned by {@link #getConfigSecondary()}
     * <p>
     * @see #setConfig( Bundle state )
     * @see #getConfigSecondary()
     */
    public static void setConfigSecondary( Bundle state )
        {
        setAll( SECONDARY_CONFIG, state );
        }

    /**
     * Enables logging (main "switch")
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableSecondary()
        {
        setEnabled( SECONDARY_CONFIG, true );
        }

    /**
     * Disables logging (main "switch")
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableSecondary()
        {
        setEnabled( SECONDARY_CONFIG, false );
        }

    /**
     * Enables debug-logging
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableDebugSecondary()
        {
        setDebugEnabled( SECONDARY_CONFIG, true );
        }

    /**
     *  Disables debug-logging
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableDebugSecondary()
        {
        setDebugEnabled( SECONDARY_CONFIG, false );
        }

    /**
     * Enables logging to system-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param logTag  tag appearing in system log
     */
    public static void enableSysLogSecondary( String logTag )
        {
        setSysLog( SECONDARY_CONFIG, logTag );
        }

    /**
     * Enables logging to system-log with default tag
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableSysLogSecondary()
        {
        setSysLog( SECONDARY_CONFIG, DEFAULT_LOG_TAG );
        }

    /**
     * Disables logging to system-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableSysLogSecondary()
        {
        setSysLog( SECONDARY_CONFIG, null );
        }

    /**
     * Enables logging to file log under default file name
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param fileName  log-file's name
     */
    public static void enableFileLogSecondary( String fileName )
        {
        setFileName( SECONDARY_CONFIG, fileName );
        }

    /**
     * Enables logging to file log under default file name
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableFileLogSecondary()
        {
        setFileName( SECONDARY_CONFIG, defaultFileName );
        }

    /**
     * Disables logging to file log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableFileLogSecondary()
        {
        setFileName( SECONDARY_CONFIG, null );
        }

    /**
     * Enables logging to toast log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param context  context of the activity
     */
    public static void enableToastLogSecondary( Context context )
        {
        setContext( SECONDARY_CONFIG, context );
        }

    /**
     * Disables logging to toast log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableToastLogSecondary()
        {
        setContext( SECONDARY_CONFIG, null );
        }

    /**
     * Sets directory path (on sd-card) for log-file
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param directoryName  path of logging directory
     */
    public static void setDirectoryNameSecondary( String directoryName )
        {
        setDirectoryName( SECONDARY_CONFIG, directoryName );
        }

    /**
     * Sets mask.
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     * @param mask
     */
    public static void setMaskSecondary( int mask )
        {
        setMask( SECONDARY_CONFIG, mask );
        }

    /**
     * Resets mask to its original -1 value. No messages will be limited.
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void unMaskSecondary( )
        {
        setMask( SECONDARY_CONFIG, DEFAULT_MASK );
        }

    /**
     * Enables time stamp in file-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableTimeStampSecondary()
        {
        setTimeStampEnabled( SECONDARY_CONFIG, true );
        }

    /**
     * Disables time stamp in file-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableTimeStampSecondary()
        {
        setTimeStampEnabled( SECONDARY_CONFIG, false );
        }

    /**
     * Enables space stamp (class.method) in file-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void enableSpaceStampSecondary()
        {
        setSpaceStampEnabled( SECONDARY_CONFIG, true );
        }

    /**
     * Disables space stamp (class.method) in file-log
     * SECONDARY Config - valid for ODD levels. Secondary config will be activated
     */
    public static void disableSpaceStampSecondary()
        {
        setSpaceStampEnabled( SECONDARY_CONFIG, false );
        }


    /************************************************************
     *                                                          *
     *  Private methods for log messaging                       *
     *                                                          *
     ************************************************************/

    /**
     * Different types of log
     * <ul>
     * <li>TITLE Title log style</li>
     * <li>NOTE Normal log style</li>
     * <li>DEBUG Normal log style, appears only if debug-log is enabled</li>
     * <li>ERROR Error log style</li>
     * <li>LOCUS Information about method, class, file, thread</li>
     * </ul>
     */
    private static enum Type
        {
        TITLE,
        NOTE,
        DEBUG,
        ERROR,
        LOCUS
        }


    /**
     * Adds text with type to system log, toast log and file log.
     * Log note appears only if log is enabled and only on enabled log streams.
     * @param type log style
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    private static String addText( Type type, int level, String text )
        {
        int conf = level % 2;

        if ( !isEnabled(conf) )
            return OFF;

        if ( (type == Type.DEBUG || type == Type.LOCUS) && !isDebugEnabled(conf) )
            return OFF;

        if ( !isLevelEnabled(level) )
            return OFF;

        addTextToSysLog( type, conf, text );
        addTextToToastLog( type, conf, text );
        return addTextToFileLog( type, conf, text );
        }

    /**
     * Adds text with type to toast log.
     * Can be sent from any thread, toast will displayed from main thread
     * Log note appears only if logging to toast log is enabled.
     * (Main enable is not checked)
     * @param type log style
     * @param text log note
     */
    private static void addTextToToastLog( Type type, final int conf, final String text )
        {
        final Context context = getContext(conf);

        // Log displayed on the screen
        if ( context != null )
            {
            // http://stackoverflow.com/questions/18280012/how-to-replace-the-system-out-with-toasts-inside-a-thread/18280318#18280318
            // http://stackoverflow.com/a/16886486
            /*
             * activity.runOnUiThread(new Runnable()
             * 		{
             * 		public void run()
             * 			{
             * 			Toast.makeText(activity, "Hello", Toast.LENGTH_SHORT).show();
             * 			}
             * 		});
             */

            new Handler( Looper.getMainLooper() ).post( new Runnable()
                {
                @Override
                public void run()
                    {
                    Toast.makeText( context, text, Toast.LENGTH_SHORT).show();
                    }
                });

            // Toast.makeText( context[0], text, Toast.LENGTH_SHORT ).show();
            }
        }

    /**
     * Adds text with type to system log.
     * Log note appears only if logging to system log is enabled.
     * (Main enable is not checked)
     * @param type log style
     * @param text log note
     */
    private static void addTextToSysLog( Type type, int conf, String text )
        {
        String logTag = getSysLog(conf);

        // Log a syslog-ba
        if ( logTag != null )
            {
            switch (type)
                {
                case TITLE:
                    Log.i( logTag, " *** " + text + " ***" );
                    break;
                case NOTE:
                    Log.i( logTag, text );
                    break;
                case DEBUG:
                    Log.d( logTag, text );
                    break;
                case ERROR:
                    Log.e( logTag, text );
                    break;
                case LOCUS:
                    Log.d( logTag, text );
                    break;
                default:
                }
            }
        }

    /**
     * Adds text with type to file log.
     * Log note appears only if logging to file log is enabled.
     * (Main enable is not checked)
     * @param type log style
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error)
     */
    private static String addTextToFileLog( Type type, int conf, String text )
        {
        File logFile = getLogFile( conf );

        // Log a file-ba
        if ( logFile != null )
            {
            OutputStreamWriter logStream = null;

            try
                {
                logStream = new OutputStreamWriter( new FileOutputStream(logFile, true) );

                switch (type)
                    {
                case TITLE:
                    logStream.append( SEPARATOR + " *** " + text + " ***" + SEPARATOR + SEPARATOR );
                    break;
                case NOTE:
                case DEBUG:
                    logStream.append( timeStamp(conf) + text + spaceStamp(conf) + SEPARATOR);
                    break;
                case ERROR:
                    logStream.append( timeStamp(conf) + HIGHLIGHT_START +
                            "ERROR: " + text + spaceStamp(conf) + HIGHLIGHT_END + SEPARATOR );
                    break;
                case LOCUS:
                    logStream.append( timeStamp(conf) + text + SEPARATOR );
                    break;
                    }
                logStream.flush();
                }
            catch (IOException ioe)
                {
                // A hibat a visszateresi ertek mutatja, DE
                // a bekapcsolas fuggvenyeben a tobbi log is kiadhatja
                String err = LOGFILE_ERROR + ioe.toString();

                addTextToToastLog( Type.ERROR, conf, err );
                addTextToSysLog( Type.ERROR, conf, err );

                // visszateresben mindig adja
                return err;
                }
            finally
                {
                if (logStream != null)
                    {
                    try
                        {
                        logStream.close();
                        }
                    catch (IOException ioe)
                        {
                        // Ezt a hibát végképp nem tudjuk hol jelenteni...
                        }
                    }
                }
            }

        return OK;
        }


    /************************************************************
     *                                                          *
     *  Public log messaging methods                            *
     *                                                          *
     ************************************************************/

    /**
     * Adds text as type {@code TITLE} to system log, toast log and file log.
     * Log note appears only on enabled log streams and only if limit allows it.
     * Primary config is used for EVEN, secondary for ODD levels
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String title(int level, String text)
        {
        return addText( Type.TITLE, level, text );
        }

    /**
     * Adds text as type {@code NOTE} to system log, toast log and file log.
     * Log note appears only on enabled log streams, and only if limit allows it.
     * Primary config is used for EVEN, secondary for ODD levels
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String note(int level, String text)
        {
        return addText( Type.NOTE, level, text );
        }

    /**
     * Adds text as type {@code DEBUG} to system log, toast log and file log.
     * Log note appears only if debug-log is enabled and only on enabled log streams,
     * and only if limit allows it.
     * Primary config is used for EVEN, secondary for ODD levels
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String debug(int level, String text)
        {
        return addText( Type.DEBUG, level, text );
        }

    /**
     * Adds text as type {@code ERROR} to system log, toast log and file log.
     * Log note appears only on enabled log streams, and only if limit allows it.
     * Primary config is used for EVEN, secondary for ODD levels
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String error(int level, String text)
        {
        return addText( Type.ERROR, level, text );
        }

    /**
     * Adds text as type {@code METHOD} to system log, toast log and file log.
     * Log note appears only on enabled log streams, and only if limit allows it.
     * Primary config is used for EVEN, secondary for ODD levels
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String locus( int level )
        {
        return addText( Type.LOCUS, level, "@ " + spaceStamp() );
        }

    // Public methods without limit check

    /**
     * Adds text as type {@code TITLE} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Primary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     * @see #checkLogFileLength()
     */
    public static String title(String text)
        {
        return title( NO_LIMIT, text);
        }

    /**
     * Adds text as type {@code NOTE} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Primary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String note(String text)
        {
        return note( NO_LIMIT, text);
        }

    /**
     * Adds text as type {@code DEBUG} to system log, toast log and file log.
     * Log note appears only if debug-log is enabled and only on enabled log streams.
     * No limit check.
     * Primary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String debug(String text)
        {
        return debug( NO_LIMIT, text);
        }

    /**
     * Adds text as type {@code ERROR} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Primary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String error(String text)
        {
        return error( NO_LIMIT, text);
        }

    /**
     * Adds text as type {@code METHOD} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Primary config is used.
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String locus()
        {
        return locus( NO_LIMIT );
        }

    // Public methods for Secondary config without limit check

    /**
     * Adds text as type {@code TITLE} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Secondary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     * @see #checkLogFileLength()
     */
    public static String title_secondary(String text)
        {
        return title( NO_LIMIT_SECONDARY, text);
        }

    /**
     * Adds text as type {@code NOTE} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Secondary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String note_secondary(String text)
        {
        return note( NO_LIMIT_SECONDARY, text);
        }

    /**
     * Adds text as type {@code DEBUG} to system log, toast log and file log.
     * Log note appears only if debug-log is enabled and only on enabled log streams.
     * No limit check.
     * Secondary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String debug_secondary(String text)
        {
        return debug( NO_LIMIT_SECONDARY, text);
        }

    /**
     * Adds text as type {@code ERROR} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Secondary config is used.
     * @param text log note
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String error_secondary(String text)
        {
        return error( NO_LIMIT_SECONDARY, text);
        }

    /**
     * Adds text as type {@code METHOD} to system log, toast log and file log.
     * Log note appears only on enabled log streams. No limit check.
     * Secondary config is used.
     * @return message ({@link #OK}, {@link #OFF} or error) from file-log
     */
    public static String locus_secondary()
        {
        return locus( NO_LIMIT_SECONDARY );
        }


    /************************************************************
     *                                                          *
     *  Private methods for log file operations                 *
     *                                                          *
     ************************************************************/

    /**
     * Deletes log file.
     * Works only if logging and file logging are enabled.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     */
    private static String clear( int conf )
        {
        conf = conf % 2; // Just for security - public methods

        if ( !isEnabled(conf) )
            return OFF;

        File logFile = getLogFile( conf );
        String err;

        if ( logFile == null || !logFile.exists() )
            return OFF;

        if ( logFile.delete() )
            {
            err = "<" + logFile.getName() + "> cleared.";

            // sikeres torlest a tobbi logon is jelezzuk
            addTextToToastLog( Type.NOTE, conf, err );
            addTextToSysLog( Type.NOTE, conf, err );

            return OK;
            }

        err = "Cannot clear <" + logFile.getName() + ">!";

        // Sikertelenseg eseten is jelzunk
        addTextToToastLog( Type.ERROR, conf, err );
        addTextToSysLog( Type.ERROR, conf, err );

        return LOGFILE_ERROR + err;
        }

    /**
     * Checks the length of the log file.
     * Works only if logging and file logging are enabled.
     * If file length is longer than {@code MAX_LOGFILE_LENGTH} then
     * old logfile is archived, and a new one will be created.
     * The maximum number of archived log files is definied in {@code MAX_LOGFILE}.
     * The oldest log file will be deleted after reaching this limit.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     * @see #title(String)
     */
    private static String checkLogFileLength( int conf )
        {
        if ( !isEnabled(conf) )
            return OFF;

        File logFile = getLogFile(conf);
        String err;

        if ( logFile == null )
            return OFF;

        boolean ok = true;

        FileChannel logChannel = null;
        FileLock lock = null;

        try
            {
            logChannel = new RandomAccessFile( logFile, "rw" ).getChannel();
            lock = logChannel.lock();

            if ( logFile.length() > MAX_LOGFILE_LENGTH )
                {
                File nextFile;
                File prevFile;

                for (int cnt = MAX_LOGFILE; cnt > 0; cnt--)
                    {
                    nextFile = getLogFile( conf, cnt );
                    prevFile = getLogFile( conf, cnt-1 );
                    if ( nextFile.exists() )
                        ok &= nextFile.delete();
                    if ( prevFile.exists() )
                        ok &= prevFile.renameTo( nextFile );
                    }
                }
            else
                {
                return OK; // No changes needed
                }
            }
        catch (IOException e)
            {
            ok = false; // Exception could occur only in lock!
            }
        finally
            {
            if ( lock != null )
                {
                try
                    {
                    lock.release();
                    }
                catch (IOException e)
                    {
                    // Ezt a hibát végképp nem tudjuk hol jelenteni...
                    }
                }
            if ( logChannel != null)
                {
                try
                    {
                    logChannel.close();
                    }
                catch (IOException e)
                    {
                    // Ezt a hibát végképp nem tudjuk hol jelenteni...
                    }
                }
            }

        if ( ok )
            {
            err = "((Log file length check: new <" + logFile.getName() + "> was created.))";

            // sikert a tobbi logon is jelezzuk
            addTextToToastLog( Type.NOTE, conf, err );
            addTextToSysLog( Type.NOTE, conf, err );

            return OK;
            }

        err = "((Log file length check: unable to create new <" + logFile.getName() + ">!))";

        // Sikertelenseg eseten is jelzunk
        addTextToToastLog( Type.ERROR, conf, err );
        addTextToSysLog( Type.ERROR, conf, err );

        return LOGFILE_ERROR + err;
        }


    /************************************************************
     *                                                          *
     *  Public methods for log file operations                  *
     *                                                          *
     ************************************************************/

    /**
     * Deletes log file - PRIMARY CONFIG.
     * Works only if logging and file logging are enabled.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     */
    public static String clear()
        {
        return clear( PRIMARY_CONFIG );
        }

    /**
     * Checks the length of the log file - PRIMARY CONFIG.
     * Works only if logging and file logging are enabled.
     * If file length is longer than {@link #MAX_LOGFILE_LENGTH} then
     * old logfile is archived, and a new one will be created.
     * The maximum number of archived log files is definied in {@link #MAX_LOGFILE}.
     * The oldest log file will be deleted after reaching this limit.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     * @see #title(String)
     */
    public static String checkLogFileLength()
        {
        return checkLogFileLength( PRIMARY_CONFIG );
        }

    /**
     * Deletes log file - SECONDARY CONFIG.
     * Works only if logging and file logging are enabled.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     */
    public static String clear_secondary()
        {
        return clear( SECONDARY_CONFIG );
        }

    /**
     * Checks the length of the log file - SECONDARY CONFIG.
     * Works only if logging and file logging are enabled.
     * If file length is longer than {@link #MAX_LOGFILE_LENGTH} then
     * old logfile is archived, and a new one will be created.
     * The maximum number of archived log files is definied in {@link #MAX_LOGFILE}.
     * The oldest log file will be deleted after reaching this limit.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     * @see #title(String)
     */
    public static String checkLogFileLength_secondary()
        {
        return checkLogFileLength( SECONDARY_CONFIG );
        }


    /************************************************************
     *                                                          *
     *  System log controls - works only with PRIMARY config    *
     *                                                          *
     ************************************************************/

    /**
     * Dumps full system log to file log.
     * Works only if logging and file logging are enabled.
     * Result is logged on system log and toast log (logs - even file log! - should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     */
    public static String dumpSysLog()
        {
        int conf = PRIMARY_CONFIG;

        if ( !isEnabled(conf) )
            return OFF;

        File logFile = getLogFile(conf);

        if ( logFile == null )
            return OFF;

        OutputStreamWriter logStream = null;
        BufferedReader bufferedReader = null;
        Process process = null;
        String err;

        try
            {
            process = Runtime.getRuntime().exec("logcat -d -v time");
            bufferedReader = new BufferedReader( new InputStreamReader( process.getInputStream() ));

            logStream = new OutputStreamWriter( new FileOutputStream(logFile, true) );

            String line;
            logStream.append( SEPARATOR + "--- SYSTEM LOG DUMP at " + timeStamp(conf) + "---" + SEPARATOR + SEPARATOR);
            while ( (line = bufferedReader.readLine()) != null )
                {
                logStream.append(line + SEPARATOR);
                }
            logStream.append(SEPARATOR + "--- END OF SYSTEM LOG ---" + SEPARATOR + SEPARATOR);
            logStream.flush();

            err = "<" + logFile.getName() + "> system log dump ready";

            // Sikeres befejezes
            addTextToToastLog( Type.NOTE, conf, err );
            addTextToSysLog( Type.NOTE, conf, err);

            return OK;
            }
        catch (IOException ioe)
            {
            err = LOGFILE_ERROR + ioe.toString();

            // Sikertelenseg
            addTextToToastLog( Type.ERROR, conf, err );
            addTextToSysLog( Type.ERROR, conf, err );

            return err;
            }
        finally
            {
            if (logStream != null)
                {
                try
                    {
                    logStream.close();
                    }
                catch (IOException ioe)
                    {
                    // Ezt a hibát végképp nem tudjuk hol jelenteni...
                    }
                }
            if (bufferedReader != null)
                {
                try
                    {
                    bufferedReader.close();
                    }
                catch (IOException ioe)
                    {
                    // Ezt a hibát végképp nem tudjuk hol jelenteni...
                    }
                }
            if (process != null)
                {
                process.destroy();
                }
            }
        }


    /**
     * Clears system log.
     * Works only if logging is enabled.
     * Result is logged on file log and toast log (logs should be enabled!)
     * @return result: ({@link #OK}, {@link #OFF} or error)
     */
    public static String clearSysLog( )
        {
        int conf = PRIMARY_CONFIG;

        if ( !isEnabled(conf) )
            return OFF;

        Process process = null;
        String err;

        try
            {
            process = Runtime.getRuntime().exec("logcat -c");

            err = "System log cleared.";

            // Siker
            addTextToToastLog( Type.NOTE, conf, err );
            addTextToFileLog( Type.NOTE, conf, err );

            return OK;
            }
        catch (IOException ioe)
            {
            err = "SYSLOG ERROR: " + ioe.toString();

            // sikertelen
            addTextToToastLog( Type.ERROR, conf, err );
            addTextToFileLog( Type.ERROR, conf, err );

            return err;
            }
        finally
            {
            if (process != null)
                {
                process.destroy();
                }
            }
        }


    /************************************************************
     *                                                          *
     *  Uncaught exceptions                                     *
     *                                                          *
     ************************************************************/

    /** System's default exception handler */
    private static UncaughtExceptionHandler defaultUncaughtExceptionHandler = null;

    /**
     * Uncaught exceptions will be catched.
     * If enabled, exceptions will be logged on PRIMARY file-log.
     * After logging, exceptions are given to the default system handler,
     * which dumps it to the system log, and finishes our program
     * <p>
     * Uncaught exceptions log is not locked. Synchronization could be performed on an outer lock,
     * to prevent mixed logs. But two exceptions from different threads at the same time can be a rarity.
     * Log-lines are mixed with other file-logs, but this is a feature, not a bug!
     * (And could be avoided by synchronization of each file-log writes.)
     */
    public static void logUncaughtExceptions()
        {
        if ( defaultUncaughtExceptionHandler != null )
            return; // already set!

        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler()
            {
            @Override
            public void uncaughtException(Thread thread, Throwable ex)
                {
                int conf = PRIMARY_CONFIG;

                logUncaughtException( conf, thread, ex );

                // re-throw critical exception further to the os (important)
                defaultUncaughtExceptionHandler.uncaughtException(thread, ex);
                }
            });

        }

    /**
     * Eventually log Thread and Throwable to file log.
     * @param conf PRIMARY/SECONDARY config. Now only primary is used.
     * @param thread thread where the exception occured
     * @param ex exception
     */
    private static void logUncaughtException( int conf, Thread thread, Throwable ex)
        {
        if ( !isEnabled(conf) )
            return;

        File logFile = getLogFile(conf);

        if ( logFile == null )
            return;

        OutputStreamWriter logStream = null;

        try
            {
            logStream = new OutputStreamWriter( new FileOutputStream(logFile, true) );

            logStream.append( SEPARATOR + "*** Uncaught exception: " + thread.getName() + " " +
                    timeStamp(conf) + " ***" + SEPARATOR + SEPARATOR);

            logExceptionHistory( logStream, ex );

            logStream.append( SEPARATOR + "*** End of uncaught exception ***" + SEPARATOR + SEPARATOR);
            logStream.flush();
            }
        catch (IOException ioe)
            {
            // Can not handle these exceptions
            }
        finally
            {
            if (logStream != null)
                {
                try
                    {
                    logStream.close();
                    }
                catch (IOException ioe)
                    {
                    // Can not handle these exceptions
                    }
                }
            }
        }

    /**
     * Recursively logs exceptions and their stack-traces.
     * Private method for logUncaughtException.
     * Exceptions are logged in different order than in system-log!
     * There are no abbreviations, all stack-traces are printed!
     * @param logStream where to log
     * @param ex exception to log
     * @throws IOException exception is thrown, but at the end this is not reported
     */
    private static void logExceptionHistory(OutputStreamWriter logStream, Throwable ex) throws IOException
        {
        if ( ex.getCause() != null )
            {
            logExceptionHistory( logStream, ex.getCause() );
            logStream.append( SEPARATOR + "*** Caused:" + SEPARATOR );
            }

        logStream.append( "*** " + ex.getClass().getName() + SEPARATOR +
            "  >" + ex.getMessage() + "<" + SEPARATOR );

        for ( StackTraceElement element: ex.getStackTrace() )
            {
            logStream.append( " @ " + element.getClassName() + "." + element.getMethodName()
                    + " (" + element.getFileName() + ( element.getLineNumber()<0 ? "" : "/" + element.getLineNumber() ) + ")"
                    + SEPARATOR );
            }
        }

    }

