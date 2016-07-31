package digitalgarden.justifiedreader.fileselector;

import java.io.*;

/**
 * One entry in the file-list of the selector
 */
public class FileEntry implements Comparable<FileEntry>
    {
    public static enum Type
        {
        FILE,
        DIR,
        PARENT_DIR,
        DIVIDER
        }

    private String name;
    private String data;
    private Type type;
    private File file;
     
    public FileEntry(String name, String data, Type type, File file)
        {
        this.name = name;
        this.data = data;
        this.type = type;
        this.file = file;
        }

    public String getName()
        {
        return name;
        }
    
    public String getData()
        {
        return data;
        }
      
    public Type getType()
        {
        return type;
        }

    public File getFile()
        {
        return file;
        }

    // Needed for sorting
    @Override
    public int compareTo( FileEntry fileEntry ) 
        {
        if( this.name != null )
            return this.name.toLowerCase().compareTo( fileEntry.getName().toLowerCase() );
        else
            throw new IllegalArgumentException();
        }
    }
