package digitalgarden.justifiedreader.description;

import android.graphics.Paint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import digitalgarden.justifiedreader.jigreader.JigReader;
import digitalgarden.justifiedreader.scribe.Scribe;
import digitalgarden.justifiedreader.debug.*;

/**
 * ParaDescriptor text between '\r' 0x0A-s.
 * Actually this means text between (including) current position and the next 0x0A
 * Reads all words from this paragraphs, measures them and then renders lines from the words.
 * After this point these "virtual" lines are used. Paragraph only provides its lines.
 */
public class ParaDescriptor
    {
    public TextDescriptor textDescriptor;
        
    // Can be empty, but cannot be null!
    public List<WordDescriptor> words;
    public List<LineDescriptor> lines;

    private long filePointer;

    
    public ParaDescriptor( TextDescriptor textDescriptor )
        {
        this.textDescriptor = textDescriptor; 
        }

        
    public long getFilePointer()
        {
        return filePointer;
        }


    /**
     * Reads all words from the paragraph into a new word-list
     * @param jigReader jigReader to get text
     * @return next file position, or -1L if EOF is reached
     */
    public long readPara( long fromPointer )
        {
        // words should be deleted, or this routine should come into the constructor
        words = new ArrayList<>();
        filePointer = fromPointer;

        try
            {
            textDescriptor.getJigReader().seek(fromPointer);
            }
        catch ( IOException ioe )
            {
            // Exception is thrown only if paragraph cannot be read because of i/o error
            // Empty paragraph is returned with EOF signal
            return -1L;
            }

        int chr;
        long wordPointer;
        StringBuilder builder = new StringBuilder();

para:	while ( true )
            {
            // skip spaces (tabs and all chars bellow space are 'spaces')
            while ( (chr = textDescriptor.getJigReader().readWithoutException()) <= ' ' )
                {
                if ( chr == -1 || chr == 0x0a )
                    break para;
                }

            // words - this could be inside wordText constructor
            try
                {
                wordPointer = textDescriptor.getJigReader().getFilePointer() - 1; // chr was read already
                }
            catch (IOException ioe )
                {
                // Exception is thrown only if paragraph cannot be read because of i/o error
                // Paragraph is finished here
                return -1L;
                }
            builder.setLength(0);
            do
                {
                builder.append( (char)chr );
                chr = textDescriptor.getJigReader().readWithoutException();
                } while ( chr > ' ' );
            textDescriptor.getJigReader().unRead( chr );

            words.add( new WordDescriptor( this, wordPointer, builder.toString() ) );
            }
        // Scribe.debug("Para: " + words);

        if ( chr == -1 ) // EOF is reached
            {
            return -1L;
            }

        try
            {
            return textDescriptor.getJigReader().getFilePointer();
            }
        catch (IOException ioe )
            {
            // Exception is thrown only if paragraph cannot be read because of i/o error
            // Paragraph is finished here
            return -1L;
            }
        }


    /**
     * All words are measured by paintFont
     * @param paintFont paint to use for measure
     */
    public void measureWords()
        {
        for ( WordDescriptor word : words )
            {
            word.measure();
            }
        }


    /**
     * Render lines for the specified width.
     * At least one line is generated (for empty paragraphs)
     * @param width width of the view
     */
    public int renderLines()
        {
        lines = new ArrayList<>();

        int wordCount = 0;
        do  {
            LineDescriptor line = new LineDescriptor( words );
            wordCount = line.render( wordCount, textDescriptor );
            lines.add( line );
            } while ( wordCount < words.size() );

        return lines.size();
        }


    public int sizeOfLines()
        {
        return lines.size();
        }

    public LineDescriptor getLine(int line )
        {
        return lines.get(line);
        }

    public void debug()
        {
        Scribe.debug( Debug.PARA, "No. of lines: " + sizeOfLines());
        for ( LineDescriptor line : lines )
            {
            Scribe.debug( Debug.PARA, " - " + line.dump());
            }
        }
        
        
    public String getWordText( long pointer )
        {
        for( WordDescriptor word : words ) 
            {
            if ( word.getFilePointer() == pointer )
                return word.getText();
            }
        return null;
        }
    }
