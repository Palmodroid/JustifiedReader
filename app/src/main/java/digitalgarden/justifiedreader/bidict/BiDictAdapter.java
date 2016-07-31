// Ver:2
package digitalgarden.justifiedreader.bidict;

import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import digitalgarden.justifiedreader.*;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.debug.*;


public class BiDictAdapter extends BaseAdapter
	{
	private final LayoutInflater inflater;
	BiDictIndex index;
	

	public BiDictAdapter(Context context, BiDictIndex index)
		{
        Scribe.locus( Debug.BIDICT );

		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.index = index;
        
        Scribe.debug( Debug.BIDICT, "Index connected to the adapter: " + index.size() + " elements.");
		}
	

	@Override
	public int getCount()
		{
		return (index == null) ? 0 : index.size();
		}

	
	@Override
	public String getItem(int position)
		{
		try
			{
			String w = index.getArticle(position).getString("WORD");
			if (w != null)
				return w;
			} 
		catch (Exception e)
			{
			; // Ha ide keveredtünk, akkor hiba van, kivétel helyett "ERROR"-t adunk vissza
			}
		
		return "ERROR";
		}

	@Override
	public long getItemId(int position)
		{
        return position;
		}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
		{
		View rowView;
		
		if (convertView == null) 
			{
        	rowView = inflater.inflate(R.layout.rowlayout, parent, false);
			} 
		else 
			{
			rowView = convertView;
			}

		View layout = rowView.findViewById(R.id.rowlayout);
		TextView word = (TextView) rowView.findViewById(R.id.word);
		TextView article = (TextView) rowView.findViewById(R.id.article);

		try
			{
			Bundle ret = index.getArticle(position);
			
			// Ez API 12 felett egyszerűsödik
			String w = ret.getString("WORD");
			if (w==null)
				w="ERROR";

			String a = ret.getString("ARTICLE");
			if (a==null)
				a="ERROR";

			if (position == 0)
				{
				word.setTextColor( 0xff00ff7f );
				word.setText( Html.fromHtml( w + " "));
				
				article.setTextColor( 0xff00ff7f );
				article.setText( (getCount() - index.FIRST_INDEX) + " bejegyzés.");
				}
			else
				{
				word.setText( Html.fromHtml( w + " "));
				article.setText( Html.fromHtml( a + " "));

				if ( ret.getBoolean("IS_SELECTED", false) )
					{
					word.setTextColor( 0xffcfcfcf );
					article.setTextColor( 0xffcfcfcf );

					layout.setBackgroundResource( R.drawable.selector );
					}
				else if ( ret.getBoolean("IS_NEW", false) )
					{
					word.setTextColor( 0xffd7cbac );
					article.setTextColor( 0xffd7cbac );		
					
					layout.setBackgroundResource( 0 );	
					}
				else
					{
					word.setTextColor( 0xff000000 ); // 0xffababab
					article.setTextColor( 0xff000000 ); //0xffababab
					
					layout.setBackgroundResource( 0 );	
					}
				}
			}  
		catch (Exception e)
			{
			word.setText("");
			article.setText("");
			}

		return rowView;
		}
	}

