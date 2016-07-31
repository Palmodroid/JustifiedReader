
package digitalgarden.justifiedreader.description;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
 
import digitalgarden.justifiedreader.jigreader.JigReader;
import digitalgarden.justifiedreader.scribe.Scribe;
import digitalgarden.justifiedreader.debug.*;

/**
 * Visible parts of the text
 * This can be started:
 * - before View is calculated (setFilePointer() can be called before)
 * - after View is calculated (setParameters() will be called after)
 */
public class TextDescriptor
    {
    private ViewDescriptor viewDescriptor = new ViewDescriptor();

    // SOURCE - opened by constructor, should be closed by close()
    private JigReader jigReader = null;

    // visible paragraphs ONLY!
    // At least one paragraph is needed after setPosition, otherwise the text is empty.
    private List<ParaDescriptor> visibleParas = new ArrayList<>();
    // first visible line of FIRST paragraph
    private int firstLine = -1;
    // last visible line of LAST paragraph
    private int lastLine = -1;

    // pointer right after the last paragraph
    private long lastFilePointer = -1L;


    private long firstLinePointer = -1;
    
    
    private long selectedPointer = -1L;

    private static final int OVERLAY = 2;


    public void setViewParameters( int viewWidth, int viewHeight, int viewMargin )
        {
        viewDescriptor.setDimensions( viewWidth, viewHeight, viewMargin );
        findFirstLine();
        }
        
    public ViewDescriptor getViewDescriptor()
        {
        return viewDescriptor;
        }
        
    public JigReader getJigReader()
        {
        return jigReader;
        }
        

    private void checkNotClosed() throws IOException
        {
        if (jigReader == null)
            throw new IOException("JigReader already closed!");
        }


    public TextDescriptor(String fileName ) throws FileNotFoundException
        {
        File file = new File( Environment.getExternalStorageDirectory(), fileName );
        this.jigReader = new JigReader( file );

        Scribe.debug( Debug.TEXT, "\n\nTextDescriptor file: [" + file.getAbsolutePath() + "] was opened.");

        viewDescriptor.setFontSize( 26f );
        viewDescriptor.setColor( ViewDescriptor.FONT, Color.BLACK );
        viewDescriptor.setColor( ViewDescriptor.SELECTED, Color.RED );
        }


    public void close() throws IOException
        {
        checkNotClosed();
        try
            {
            jigReader.close();
            }
        finally
            {
            jigReader = null;
            Scribe.debug( Debug.TEXT, "Text descriptor is closed");
            }
        }


    public void setFilePointer(long filePointer )
        {
        firstLinePointer = filePointer;
        visibleParas.clear(); // ??
        lastFilePointer = -1L; // ??
        firstLine = -1;
        lastLine = -1; // ??

        Scribe.debug( Debug.TEXT, "First line pointer is set: " + firstLinePointer);

        findFirstLine();
        }


    public long getFilePointer()
         {
         if ( visibleParas.size() < 1 )
             return -1L;

             
         long pointer = visibleParas.get(0).getLine(firstLine).getFilePointer();

         if ( pointer > 0L)
             return pointer;
         return visibleParas.get(0).getFilePointer();
         }


         
    /**
     * Finds first line, if
     * - data is available
     * - first line was not found previously
     * Calls the builder after finding the right position.
     */
    public void findFirstLine()
        {
        Scribe.locus( Debug.TEXT );

        if ( viewDescriptor.viewHeight < 0 )
            {
            Scribe.error( Debug.TEXT, "CANNOT FIND FIRST LINE - VIEW DATA IS STILL MISSING!");
            return;
            }

        if ( firstLinePointer < 0L )
            {
            Scribe.error( Debug.TEXT, "CANNOT FIND FIRST LINE - FILE POINTER IS STILL MISSING!");
            return;
            }

        if ( firstLine >= 0 )
            {
            Scribe.error( Debug.TEXT, "FIRST LINE WAS ALREADY FOUND!");
            return;
            }

        try
            {
            checkNotClosed();

            Scribe.debug( Debug.TEXT, "File pointer to set: " + firstLinePointer);

            // Find beginning of paragraph
            jigReader.seek(firstLinePointer);
            int c;
            while ((c = jigReader.readByteBackward()) != -1)
                {
                if (c == 0x0A)
                    {
                    jigReader.readByte(); // Skip 0x0A
                    break;
                    }
                }
            lastFilePointer = jigReader.getFilePointer();
            }
        catch ( IOException ioe )
            {
            Scribe.error( Debug.TEXT, "TEXT CANNOT BE READ BECAUSE OF I/O ERROR!");
            return;
            }

        Scribe.debug( Debug.TEXT, "File pointer of the selected paragraph: " + lastFilePointer);

        // Read first paragraph
        Scribe.debug( Debug.TEXT, "Visible para array is cleared.");
        visibleParas.clear();
        readNextParagraph();

        // Find the first line
        int l = visibleParas.get(0).sizeOfLines() - 1; // at least 0
        while (l > 0)
            {
            if (firstLinePointer > visibleParas.get(0).getLine(l).getFilePointer())
                break;
            l--;
            }

        firstLine = l;

        Scribe.debug( Debug.TEXT, "Selected (first visible) line of the selected paragraph: " + firstLine);

        buildTextFromFirstLine();
        }


    /**
     * Paragraph is read at the end of the paragraphs
     * @return file pointer for the next paragraph, or -1L, if eof is reached
     */
    private long readNextParagraph( )
        {
        if ( lastFilePointer < 0L )
            return lastFilePointer;

        ParaDescriptor paragraph = new ParaDescriptor( this );
        lastFilePointer = paragraph.readPara(lastFilePointer);
        paragraph.measureWords();
        paragraph.renderLines();
        visibleParas.add( paragraph );

        Scribe.debug( Debug.TEXT, "Para (" + paragraph.getFilePointer() + ") added at: " + (visibleParas.size()-1) );
        paragraph.debug();

        return lastFilePointer;
        }


    /**
     * At least first paragraph with valid first line is needed.
     * A new text is created from the first line
     */
    private void buildTextFromFirstLine()
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = 0;
        int lineCounter = firstLine - 1;

        int positionY = viewDescriptor.viewMargin - viewDescriptor.fontAscent;

        while (positionY < viewDescriptor.viewHeight - viewDescriptor.viewMargin - viewDescriptor.fontDescent)
            {
            lineCounter++;
            if (lineCounter >= visibleParas.get(paraCounter).sizeOfLines())
                {
                lineCounter = 0;
                paraCounter++;
                if (paraCounter >= visibleParas.size())
                    {
                    if ( readNextParagraph() < 0L )
                        {
                        Scribe.error( Debug.TEXT, "END OF TEXT IS REACHED!");
                        break;
                        }
                    }
                }

            visibleParas.get(paraCounter).getLine(lineCounter).setPositionY(positionY);
            positionY += getLineHeight(paraCounter, lineCounter);
            }

        lastLine = lineCounter;

        paraCounter++;
        while (paraCounter < visibleParas.size())
            {
            lastFilePointer = visibleParas.get(visibleParas.size() - 1).getFilePointer();
            visibleParas.remove(visibleParas.size() - 1);
            Scribe.debug( Debug.TEXT, "One para from end is removed.");
            }
            
        Scribe.debug( Debug.TEXT, "* Size of visible paras: " + visibleParas.size() );
        Scribe.debug( Debug.TEXT, "* Pointer of first para: " + visibleParas.get(0).getFilePointer() +
                ", line: " + firstLine );
        Scribe.debug( Debug.TEXT, "* Pointer of last para: " + visibleParas.get(visibleParas.size()-1).getFilePointer() +
                     ", line: " + lastLine );
        }


    public void pageDown()
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = visibleParas.size() - 1;
        int lineCounter = lastLine;

        for ( int n = 1; n < OVERLAY; n++)
            {
            if (paraCounter > 0 || lineCounter > firstLine)
                {
                lineCounter--;
                if (lineCounter < 0)
                    {
                    paraCounter--;
                    lineCounter = visibleParas.get(paraCounter).sizeOfLines() - 1;
                    }
                }
            }

        firstLine = lineCounter;

        Scribe.debug( Debug.TEXT, "New first - para: " + paraCounter + ", line: " + lineCounter);
        
        while ( paraCounter > 0 )
            {
            visibleParas.remove(0);
            paraCounter--;
            Scribe.debug( Debug.TEXT, "One para from beginning is removed.");
            }

        buildTextFromFirstLine();
        }


    public void lineDown()
        {
        Scribe.locus( Debug.TEXT );

        if ( visibleParas.size() > 1 || firstLine < lastLine )
            {
            firstLine++;
            if (firstLine >= visibleParas.get(0).sizeOfLines())
                {
                visibleParas.remove(0);
                firstLine = 0;
                }

            buildTextFromFirstLine();
            }
        }


    private long readPrevParagraph( )
        {
        long filePointer = visibleParas.get(0).getFilePointer();

        try
            {
            // Find beginning of paragraph
            jigReader.seek(filePointer);
            if ( jigReader.readByteBackward() == -1 )   // read 0x0a
                return -1L;

            int c;
            while ((c = jigReader.readByteBackward()) != -1)
                {
                if (c == 0x0A)
                    {
                    jigReader.readByte(); // Skip 0x0A
                    break;
                    }
                }

            filePointer = jigReader.getFilePointer();
            }
        catch ( IOException ioe )
            {
            return -1L; // Simulate BOF
            }

        ParaDescriptor paragraph = new ParaDescriptor(this);
        paragraph.readPara(filePointer);
        paragraph.measureWords();
        paragraph.renderLines();
        visibleParas.add( 0, paragraph );

        Scribe.debug( Debug.TEXT, "Para (" + paragraph.getFilePointer() + ") added at: 0");
        paragraph.debug();

        return filePointer;
        }


    private void buildTextFromLastLine()
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = visibleParas.size()-1;
        int lineCounter = lastLine + 1;

        // exact position cannot be calculated. Paras are read first, then rebuild from first line
        int positionY = viewDescriptor.viewMargin - viewDescriptor.fontAscent;

        while (positionY < viewDescriptor.viewHeight - viewDescriptor.viewMargin - viewDescriptor.fontDescent)
            {
            lineCounter--;
            if ( lineCounter < 0 )
                {
                paraCounter --;
                if ( paraCounter < 0 )
                    {
                    paraCounter = 0;
                    if ( readPrevParagraph() < 0L )
                        {
                        Scribe.error( Debug.TEXT, "BEGINNING OF TEXT IS REACHED!");

                        lineCounter = 0; // New first line is set
                        break;
                        }
                    }

                lineCounter = visibleParas.get(paraCounter).sizeOfLines()-1;
                }

            // this is not possible: visibleParas.get(paraCounter).getLine(lineCounter).setPositionY(positionY);
            positionY += getLineHeight(paraCounter, lineCounter);
            }

        firstLine = lineCounter;

        while ( paraCounter > 0 )
            {
            visibleParas.remove(0);
            paraCounter--;
            }

        // new build is needed if text is not long enough, and to position it
        buildTextFromFirstLine();
        }


    public void pageUp()
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = 0;
        int lineCounter = firstLine;

        for ( int n = 1; n < OVERLAY; n++)
            {
            if (paraCounter < visibleParas.size()-1 || lineCounter < lastLine)
                {
                lineCounter++;
                if (lineCounter >= visibleParas.get(paraCounter).sizeOfLines())
                    {
                    paraCounter++;
                    lineCounter = 0;
                    }
                }
            }

        lastLine = lineCounter;

        paraCounter++;
        while ( paraCounter < visibleParas.size() )
            {
            lastFilePointer = visibleParas.get(visibleParas.size() - 1).getFilePointer();
            visibleParas.remove(visibleParas.size() - 1);
            Scribe.debug( Debug.TEXT, "One para from end is removed.");
            }

        buildTextFromLastLine();
        }


    public void lineUp()
        {
        Scribe.locus( Debug.TEXT );
        
        if ( firstLine > 0 )
        	{
            firstLine --;

        	}
        else
        	{
             if ( readPrevParagraph() == -1L )
                 return;

            firstLine = visibleParas.get(0).sizeOfLines()-1;

            }
        buildTextFromFirstLine();

            
/* this part moves lastLine, but first line should be moved

        if ( visibleParas.size() > 1 || firstLine < lastLine )
            {
            lastLine--;
            if (lastLine < 0)
                {
                lastFilePointer = visibleParas.get(visibleParas.size() - 1).getFilePointer();
                visibleParas.remove(visibleParas.size()-1);
                lastLine = visibleParas.get(visibleParas.size()-1).sizeOfLines()-1;
                }

            buildTextFromLastLine();
            }
*/
        }


    private int getLineHeight( int paragraphCounter, int lineCounter )
        {
        if ( visibleParas.get(paragraphCounter).getLine( lineCounter).isEmpty() )
            {
            return viewDescriptor.emptyLineSpace;
            }

        if ( visibleParas.get(paragraphCounter).getLine( lineCounter).isLast() )
            {
            return viewDescriptor.fontDescent - viewDescriptor.fontAscent + viewDescriptor.lastLineSpace;
            }

        return viewDescriptor.fontDescent - viewDescriptor.fontAscent + viewDescriptor.lineSpace;
        }
        
        
    // can be joined with getWordPointer    
    public void setSelectedPointer( float positionX, float positionY )
        {
        selectedPointer = getWordPointer (positionX,positionY );
        }
    
        
    public long getSelectedPointer()
        {
        return selectedPointer;
        }
        
        
    public long getWordPointer( float positionX, float positionY )
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = 0;
        int lineCounter = firstLine;

        while ( paraCounter < visibleParas.size() )
            {
            Scribe.debug( Debug.DRAWTEXT, "PC: " + paraCounter + " / " + (visibleParas.size()-1));
            Scribe.debug( Debug.DRAWTEXT, "LC: " + lineCounter + " / " + (visibleParas.get(paraCounter).sizeOfLines()-1));

            if ( paraCounter == visibleParas.size()-1 && lineCounter > lastLine ) // last paragraph
                break;

            Scribe.debug( Debug.DRAWTEXT, visibleParas.get(paraCounter).getLine(lineCounter).dump() );
            // visibleParas.get(paraCounter).getLine(lineCounter).draw( canvas, viewDescriptor.fontPaint );
            
            if ( positionY >= visibleParas.get(paraCounter).getLine(lineCounter).getPositionY() + viewDescriptor.fontAscent &&
                     positionY <= visibleParas.get(paraCounter).getLine(lineCounter).getPositionY() + viewDescriptor.fontDescent )
                {
                return visibleParas.get(paraCounter).getLine(lineCounter).getWordPointer( positionX );
                }

            lineCounter++;
            if ( lineCounter >= visibleParas.get(paraCounter).sizeOfLines())
                {
                paraCounter++;
                lineCounter=0;
                }
            }
        
        return -1L;
        }
        
        
    public String getWordText( long pointer )
        {
        String text;

        for( ParaDescriptor para : visibleParas ) 
            {

            if ( (text = para.getWordText( pointer )) != null )
                return text;
            }
        return null;

        }


    public void drawText( Canvas canvas )
        {
        Scribe.locus( Debug.TEXT );

        int paraCounter = 0;
        int lineCounter = firstLine;

        while ( paraCounter < visibleParas.size() )
            {
            Scribe.debug( Debug.DRAWTEXT, "PC: " + paraCounter + " / " + (visibleParas.size()-1));
            Scribe.debug( Debug.DRAWTEXT, "LC: " + lineCounter + " / " + (visibleParas.get(paraCounter).sizeOfLines()-1));

            if ( paraCounter == visibleParas.size()-1 && lineCounter > lastLine ) // last paragraph
                break;

            Scribe.debug( Debug.DRAWTEXT, visibleParas.get(paraCounter).getLine(lineCounter).dump() );
            visibleParas.get(paraCounter).getLine(lineCounter).draw( canvas );

            lineCounter++;
            if ( lineCounter >= visibleParas.get(paraCounter).sizeOfLines())
                {
                paraCounter++;
                lineCounter=0;
                }
            }

        }
    }
    
