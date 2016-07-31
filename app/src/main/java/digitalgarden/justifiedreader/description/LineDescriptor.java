package digitalgarden.justifiedreader.description;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.List;

import digitalgarden.justifiedreader.scribe.Scribe;
import digitalgarden.justifiedreader.debug.*;

/**
 * Descriptor of a line
 */
public class LineDescriptor
    {
    // pointer to the words of the paragraph
    private List<WordDescriptor> words;

    // firstWord == negative values: rendering was not started
    // lastWord < firstWord: no words in this line

    // first word of the line (among the words of the paragraph)
    public int firstWord = -1;
    // last word of the line (among the words of the paragraph)
    private int lastWord;

    // Only between firstLine (of first para) to lastLine (of last para)
    private int positionY;

    /**
     * Constructor
     * @param words list of all words of the paragraph
     */
    LineDescriptor(List<WordDescriptor> words )
        {
        this.words = words;
        }


    /**
     * File pointer of the line (== first word of the line) or -1L, if line is empty
     */
    public long getFilePointer()
        {
        if ( words.isEmpty() )
            return -1L; // ??
        return words.get(firstWord).getFilePointer();
        }


    public int getPositionY()
        {
        return positionY;
        }


    public void setPositionY(int positionY)
        {
        this.positionY = positionY;
        }


    /**
     * Renders line: adds words from paragraph and justifies them
     * @param startWord start with this word (among the words of the paragraph)
     * @param spaceMin minimum space (in pixels) between words
     * @param spaceMax maximum space (in pixels) between words
     * @param width width available for the line
     * @return the No. of the first non-added word
     */
    public int render( int startWord, TextDescriptor textDescriptor )
        {
        Scribe.locus( Debug.LINE );

        // iterator from startWord to the last word of the paragraph
        int wordCursor;

        // width occupied by added words and minimal space between them
        float textWidth = 0f;

        // width of the remaining space inside the line
        float spaceWidth = 0f;
        // width of the current word
        float wordWidth;

        this.firstWord = startWord;
        this.lastWord = firstWord - 1;

        // iterate through words
        for ( wordCursor = firstWord; wordCursor < words.size(); wordCursor++ )
            {
            wordWidth = words.get(wordCursor).getWidth();

            // word can be added
            if ( textWidth + (lastWord - firstWord + 1) * textDescriptor.getViewDescriptor().spaceMin + wordWidth <= textDescriptor.getViewDescriptor().viewWidth - 2 * textDescriptor.getViewDescriptor().viewMargin )
                {
                lastWord++;
                textWidth += wordWidth;
                }
            // word is too long (cannot be added), but this is the first word in the line
            else if ( wordCursor == firstWord )
                {
                // long words will overflow width
                // this can be solved only if unit is smaller than a word
                lastWord++;
                break;
                }
            // word cannot be added, because there is no more space
            else
                {
                break;
                }
            }

        Scribe.debug( Debug.LINE, "Words count: " + (lastWord - firstWord + 1) + ", text width: " + textWidth + ", words: " + dump());

        // Justify: at least two words, and not the last line
        if ( lastWord > firstWord && lastWord != words.size() - 1)
            {
            spaceWidth = (textDescriptor.getViewDescriptor().viewWidth - 2 * textDescriptor.getViewDescriptor().viewMargin - textWidth) / (lastWord - firstWord);
            Scribe.debug( Debug.LINE, "Space width: " + spaceWidth);
            }
        if ( spaceWidth < textDescriptor.getViewDescriptor().spaceMin || spaceWidth > textDescriptor.getViewDescriptor().spaceMax )
            {
            spaceWidth = textDescriptor.getViewDescriptor().spaceMin;
            Scribe.debug( Debug.LINE, "Space width: MINIMAL");
            }

        // Set x position for each word inside line
        float positionX = textDescriptor.getViewDescriptor().viewMargin;
        for (int word = firstWord; word <= lastWord; word++)
            {
            words.get(word).setPosition(positionX);
            positionX += words.get(word).getWidth() + spaceWidth;
            }

        // return the first non-added word of the paragraph
        return lastWord + 1;
        }

        
    public long getWordPointer( float positionX )
        {
        for ( int word = firstWord; word <= lastWord; word++ )
            {
            if ( positionX >= words.get(word).getPosition() &&  
                    positionX < words.get(word).getPosition() + words.get(word).getWidth() )
                return words.get(word).getFilePointer();
            }  
        return -1L;
        }
         
        
    /**
     * Draws the words of this line onto the canvas
     * @param canvas canvas to draw on
     * @param paint paint to use for drawing
     * @return line type
     */
    public void draw( Canvas canvas )
        {
        for ( int word = firstWord; word <= lastWord; word++ )
            {
            words.get(word).draw( canvas, positionY );
            }
            
        //if ( firstWord > lastWord )
        //    {
        //    canvas.drawText( "EMPTY", 5, positionY, paint);
        //    }
        }

    public boolean isFirst()
        {
        return firstWord == 0;
        }

    public boolean isLast()
        {
        return lastWord == words.size()-1;
        }

    public boolean isEmpty()
        {
        return lastWord < firstWord;
        }


    public String dump()
        {
        StringBuilder builder = new StringBuilder();
        builder.append( isFirst() ? 'F' : ' ' );
        builder.append( isLast() ? 'L' : ' ' );

        if ( isEmpty() )
            {
            builder.append("<EMPTY LINE>");
            }
        else
            {
            for (int w = firstWord; w <= lastWord; w++)
                {
                builder.append(words.get(w).dump());
                }
            }
        return builder.toString();
        }

    }
