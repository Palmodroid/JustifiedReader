package digitalgarden.justifiedreader.description;

import android.graphics.*;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.debug.*;

public class ViewDescriptor
    {
    // DEFAULT values
    private Paint fontPaint = new Paint();
    
    private int fontColor;

    private int selectedColor;

    
    public static final int FONT = 1;
 
    public static final int SELECTED = 2;
          
    public float spaceMin;
    public float spaceMax;
        
    // Values of height dimensions
    public int fontAscent;
    public int fontDescent;
    public int lineSpace;
    public int lastLineSpace;
    public int emptyLineSpace;

    // Values get from view
    public int viewWidth = -1;
    public int viewHeight = -1;
    public int viewMargin = -1;


    public void setFontTypeface(Typeface typeface)
        {
        Scribe.debug( Debug.TEXT, "Font typeface is set: " + typeface );

        fontPaint.setTypeface( typeface );
        setFontDimensions();
        }


    public void setFontSize( float textSize )
        {
        Scribe.debug( Debug.TEXT, "Font size is set: " + textSize );

        fontPaint.setTextSize( textSize );
        setFontDimensions();
        }


    private void setFontDimensions()
        {
        spaceMin = fontPaint.measureText(".");
        spaceMax = fontPaint.measureText("mmm");

        Scribe.debug( Debug.PARA, "Space is calculated, min: " + spaceMin + ", max: " + spaceMax);
  
        fontAscent = (int)fontPaint.ascent();
        fontDescent = (int)fontPaint.descent();
        lineSpace = 5;
        lastLineSpace = 10;
        emptyLineSpace = 20;

        Scribe.debug( Debug.TEXT, 
                "Font dimensions - ascent: " + fontAscent +
                ", descent: " + fontDescent +
                "; line space: " + lineSpace +
                ", last line space: " + lastLineSpace +
                ", empty line space: " + emptyLineSpace);
        }


    public void setColor( int type, int color )
        {
        switch( type )
           {

           case FONT:
               fontColor = color; 
               break;

           case SELECTED:
               selectedColor = color;

               break;

           }
        Scribe.debug( Debug.TEXT, type + " color is set: " + Integer.toHexString(color) );
        }

        
    public Paint getPaint( int type )
        {
        switch( type )
            {
            case FONT:
                fontPaint.setColor( fontColor );
                break;
            case SELECTED:
                fontPaint.setColor( selectedColor );
                break;
            }
        return fontPaint;
     
        }

        
    public void setDimensions( int viewWidth, int viewHeight, int viewMargin )
        {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.viewMargin = viewMargin;

        Scribe.debug( Debug.TEXT, 
                "View parameters - width: " + viewWidth +
                ", height: " + viewHeight +
                ", margin: " + viewMargin);
        }

    }
