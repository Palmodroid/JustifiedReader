package digitalgarden.justifiedreader;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;
import digitalgarden.justifiedreader.debug.*;
import digitalgarden.justifiedreader.description.*;
import digitalgarden.justifiedreader.scribe.*;

/**
 * Just a probe
 */
public class JustifiedReaderView extends View
    {
    private TextDescriptor textDescriptor = null;

    public JustifiedReaderView(Context context)
        {
        super(context);
        }

    public JustifiedReaderView(Context context, AttributeSet attrs)
        {
        super(context, attrs);
        }

    public JustifiedReaderView(Context context, AttributeSet attrs, int defStyleAttr)
        {
        super(context, attrs, defStyleAttr);
        }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
        setMeasuredDimension(measureIt(widthMeasureSpec), measureIt(heightMeasureSpec));
        }

    /**
     * View tries to occupy the biggest available area
     * - MeasureSpec.EXACTLY - returns the exact size
     * - MeasureSpec.AT_MOST - returns the biggest available size
     * - else (MeasureSpec.UNSPECIFIED) - returns the biggest size (integer)
     * @param measureSpec onMeasure parameter
     * @return biggest available size
     */
    private int measureIt(int measureSpec)
        {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        return ( specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST ) ?
                specSize : Integer.MAX_VALUE;
        }


    /**
     * onSizeChanged is called only once, when View size is already calculated
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight)
        {
        if ( textDescriptor != null )
            {
            textDescriptor.setViewParameters( width, height, 25 );
            }
        }


    public void setVisibleText(TextDescriptor textDescriptor, long startPointer )
        {
        this.textDescriptor = textDescriptor;
        if ( getHeight() > 0 )
            {
            this.textDescriptor.setViewParameters( getWidth(), getHeight(), 25 );
            }
        this.textDescriptor.setFilePointer( startPointer );
        }

    @Override
    protected void onDraw(Canvas canvas)
        {
        if ( textDescriptor != null )
            {
            textDescriptor.drawText( canvas );
            }
        }
        
    private final static int MOVE = 1;
    private final static int SELECT = 2;
        
    private int actionType = 0;
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
        {
        Scribe.debug( "Touch: " + event );

        if ( textDescriptor == null )
            return false;

        int x = (int)event.getX();
        int y = (int)event.getY();

        switch (event.getActionMasked())
            {
            case MotionEvent.ACTION_DOWN:

                if ( x > getWidth() * 8 / 10 )
                    {
                    Scribe.debug( Debug.VIEW, "ACTION DOWN - MOVE PAGE" );
                    actionType = MOVE;

                    if ( y > getHeight()/2 )
                        textDescriptor.pageDown();
                    else
                        textDescriptor.pageUp();

                    invalidate();
                    break;
                    }
                Scribe.debug( Debug.VIEW, "ACTION DOWN - SELECT WORD" );
                actionType = SELECT;
                     
           case MotionEvent.ACTION_MOVE:
               
               if ( actionType == SELECT )
                    {
                    textDescriptor.setSelectedPointer( (float)x, (float)y );
                    invalidate();
                    }
               break;

                
            case MotionEvent.ACTION_UP:

                if ( actionType == SELECT && onWordSelectedListener != null && textDescriptor.getSelectedPointer() > 0L )
                    {
                    Scribe.debug( Debug.VIEW, "ACTION UP - SEND SELECTED WORD" );
                    onWordSelectedListener.onSelect( textDescriptor.getWordText( textDescriptor.getSelectedPointer() ));
                    }

                break;
             }
        // if false, then no further MOVE or UP are provided
        return true;
        }



    OnWordSelectedListener onWordSelectedListener = null;

    public void setOnWordSelectedListener(OnWordSelectedListener listener) 
        {
        onWordSelectedListener = listener;    
        }

    public interface OnWordSelectedListener 
        {
        public abstract void onSelect( String word );
        }
    }
    
    
    
