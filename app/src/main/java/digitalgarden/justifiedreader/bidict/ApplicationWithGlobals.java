package digitalgarden.justifiedreader.bidict;

import android.app.Application;


public class ApplicationWithGlobals extends Application
	{
    private BiDictIndex dictIndex = null;
    
    public BiDictIndex getIndex()
		{
		return dictIndex;
		}
	
    public void setIndex( BiDictIndex index )
		{
		this.dictIndex = index;
		}

	}
