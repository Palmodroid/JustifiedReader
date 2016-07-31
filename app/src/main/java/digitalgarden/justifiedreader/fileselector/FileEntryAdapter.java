package digitalgarden.justifiedreader.fileselector;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import digitalgarden.justifiedreader.*;

/**
 * List-view adapter to provide file-entries
 */
public class FileEntryAdapter extends BaseAdapter implements Filterable
    {
    private LayoutInflater layoutInflater;

    private List<FileEntry> filteredFileEntries;
    private List<FileEntry> originalFileEntries;

    private FileEntryFilter fileEntryFilter;


    public FileEntryAdapter(Context context, List<FileEntry> fileEntries)
        {
        super();

        this.layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.originalFileEntries = fileEntries;
        this.filteredFileEntries = fileEntries;
        }

    @Override
    public int getCount()
        {
        return (filteredFileEntries == null) ? 0 : filteredFileEntries.size();
        }

    @Override
    public FileEntry getItem( int position )
        {
        return filteredFileEntries.get( position );
        }

    @Override
    public long getItemId(int position)
        {
        return position;
        }

    // DIVIDER can use a second view
    @Override
    public int getViewTypeCount()
        {
        return 2;
        }

    @Override
    public int getItemViewType(int position)
        {
        if ( getItem(position).getType() != FileEntry.Type.DIVIDER )
            return 0;

        return 1;
        }

    // DIVIDER cannot be checked
    @Override
    public boolean areAllItemsEnabled()
        {
        return false;
        }

    @Override
    public boolean isEnabled(int position)
        {
        return getItem(position).getType() != FileEntry.Type.DIVIDER;
        }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) 
        {
        FileEntry fileEntry = getItem(position);
        View rowView;

        if ( getItem(position).getType() != FileEntry.Type.DIVIDER )
            {
            rowView = (convertView == null) ?
                    layoutInflater.inflate( R.layout.file_entry_rowview, null) : convertView;

            ( (TextView) rowView.findViewById( R.id.file_entry_name )).setText( fileEntry.getName() );
            ( (TextView) rowView.findViewById( R.id.file_entry_data )).setText( fileEntry.getData() );
            }
        else
            {
            rowView = (convertView == null) ?
                    layoutInflater.inflate( R.layout.divider_row_view, null) : convertView;

            ( (TextView) rowView.findViewById( R.id.divider_title )).setText( fileEntry.getName() );
            }

        return rowView;
        }

    // http://stackoverflow.com/a/13514663
    public Filter getFilter()
        {
        if (fileEntryFilter == null)
            fileEntryFilter = new FileEntryFilter();

        return fileEntryFilter;
        }

    private class FileEntryFilter extends Filter
        {
        protected FilterResults performFiltering(CharSequence constraint)
            {
            FilterResults filterResults = new FilterResults();

            if (constraint != null && constraint.length() > 0 )
                {
                List<FileEntry> filterList=new ArrayList<>();

                for ( int i=0; i < originalFileEntries.size(); i++ )
                    {
                    if ( originalFileEntries.get(i).getType() != FileEntry.Type.FILE ||
                            originalFileEntries.get(i).getName().toLowerCase()
                                    .contains( constraint.toString().toLowerCase() ) )
                        {
                        filterList.add( originalFileEntries.get(i) );
                        }
                    }

                filterResults.count = filterList.size();
                filterResults.values = filterList;
                }
            else
                {
                filterResults.count = originalFileEntries.size();
                filterResults.values = originalFileEntries;
                }
            return filterResults;
            }

        // http://stackoverflow.com/a/262416
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults filterResults)
            {
            filteredFileEntries = (List<FileEntry>) filterResults.values;
            notifyDataSetChanged();
            }
        }
    }
