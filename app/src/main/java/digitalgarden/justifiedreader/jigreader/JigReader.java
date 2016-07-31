package digitalgarden.justifiedreader.jigreader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Jig reader
 */
public class JigReader extends Reader
    {
    /**
     * Random access file gives random access to the file,
     * but raf itself is not buffered
     */
    private RandomAccessFile raf;

    /**
     * Two blocks are used to store data forward and backward
     */
    private class Block
        {
        static final int SIZE = 8;

        // offset of the first byte - start of the block
        long offset = 0;
        // data of file from offset
        byte[] data = new byte[SIZE];
        // length of data in bytes; -1 EOF is reached
        int length = 0;

        Block( long offset )
            {
            this.offset = offset;
            }

        void readBlock( ) throws IOException
            {
            readBlock(SIZE);
            }

        void readBlock( int size ) throws IOException
            {
            raf.seek( offset );
            length = raf.read(data, 0, size);
            }
        }


    private Block[] block = new Block[2];


    private int activeBlock = 0;

    private int pointerInBlock = 0;

    private List<Integer> unreadBuffer = new ArrayList<>();

    public static final int MAX_LINE_LENGTH = 8192;

    private long markedPosition;


    public JigReader(String path) throws FileNotFoundException
        {
        this(new File(path));
        }


    public JigReader(File fil) throws FileNotFoundException
        {
        raf = new RandomAccessFile(fil, "r");
        block[0] = new Block(0);
        block[1] = new Block(0);
        }


    public int readByte() throws IOException
        {
        checkNotClosed();
		positionMoved();
        
        while (pointerInBlock >= block[activeBlock].length)
            {
            if (activeBlock == 0)
                {
                activeBlock++;
                pointerInBlock = 0;
                }
            else // activeBlock == 1
                {
                if (block[activeBlock].offset + pointerInBlock >= raf.length() )
                    return -1; // EOF

                block[0] = block[1];

                block[1] = new Block(block[0].offset + block[0].length);
                block[1].readBlock();
                // activeBlock remains 1
                pointerInBlock = 0;
                }
            }
        return block[activeBlock].data[pointerInBlock++] & 0xFF;
        }


    public int readByteBackward() throws IOException
        {
        checkNotClosed();
        positionMoved();
            
        pointerInBlock--;

        while ( pointerInBlock < 0 )
            {
            if (activeBlock == 1)
                {
                activeBlock--;
                pointerInBlock = block[activeBlock].length-1;
                }
            else // activeBlock == 0
                {
                if ( block[activeBlock].offset == 0L )
                    {
                    pointerInBlock = 0;
                    return -1; // BOF
                    }

                block[1] = block[0];

                if ( block[1].offset > Block.SIZE )
                    {
                    block[0] = new Block( block[1].offset - Block.SIZE );
                    block[0].readBlock();
                    }
                else
                    {
                    block[0] = new Block( 0 );
                    block[0].readBlock( (int)block[1].offset );
                    }
                pointerInBlock = block[0].length-1;
                }
            }
        return block[activeBlock].data[pointerInBlock] & 0xFF;
        }


    private void checkNotClosed() throws IOException
        {
        if (raf == null)
            throw new IOException("JigReader already closed!");
        }


    private void positionMoved()
    	{
        unreadBuffer.clear();


    	}
    
    
    public boolean checkBom() throws IOException
    	{
        checkNotClosed();
       
        seek(0L);

        if (read() == 0xFEFF)
            return true;

        seek(0L);

        return false;


    	}
    
    
    @Override
    public void close() throws IOException
        {
        checkNotClosed();

        try
            {
            raf.close();
            }
        finally
            {
            raf = null;
            block = null;
            }
        }


    public void seek( long newPosition ) throws IOException
        {
        checkNotClosed();
        positionMoved();
            
        for ( int n = 0; n < 1; n++ )
            {
            if ( newPosition >= block[n].offset && newPosition < block[n].offset + block[n].length)
                {
                activeBlock = n;
                // blockpointer cannot be bigger than length/SIZE
                pointerInBlock = (int)(newPosition - block[n].offset);
                return;
                }
            }
        if ( newPosition < 0L )
            {
            newPosition = 0L;
            }
        else if ( newPosition > raf.length() )
            {
            newPosition = raf.length(); // ?? or -1 ??
            }

        block[0] = new Block( newPosition );
        block[1] = new Block( newPosition );
        pointerInBlock = 0;
        }


    public long getFilePointer() throws IOException
        {
        checkNotClosed();
        return block[activeBlock].offset + pointerInBlock;
        }


    public long length() throws IOException
        {
        checkNotClosed();
        return raf.length();
        }

    public boolean isEof() throws IOException
        {
        checkNotClosed();
        return block[activeBlock].offset + pointerInBlock >= raf.length(); // ?? && unreadBuffer.isEmpty();
        }


    public int readWithoutException()
        {
        try
            {
            return read();
            }
        catch (IOException e)
            {
            return -1; // Simulate EOF
            }
        }


    // Read UTF-8 coded character
    @Override
    public int read() throws IOException
        {
        if ( !unreadBuffer.isEmpty() )
            {
            return unreadBuffer.remove( unreadBuffer.size()-1 );
            }

        // UTF-8:
        // 1 byte: 00 - 7F
        // 2 byte: C2 - DF, 80 - BF
        // (C0 and C1 are invalid)
        // 3 byte: E0 - EF, 80 - BF, 80 - BF
        // 4 byte: F0 - F7, 80 - BF, 80 - BF, 80 - BF
        // Eventually:
        //         F0 - F4 can be used
        //         F5 - F8 - FF: invalid sequence

        // FIRST BYTE: 
        // -1 EOF
        // 00-7F valid one byte
        // 80-BF cannot be first - skip them
        // C0- 	 longer sequence
        int c1;
        do	{
            c1 = readByte();
            if ( c1 < 0x80 )
                {
                return c1;
                }
            } while (c1 < 0xC0);

        while (true)
            {
            // First byte: C0, C1 and F5-FF are invalid
            if ( c1 < 0xC2 && c1 > 0xF4 )
                {
                return '*';				// invalid sequence
                }

            // SECOND BYTE
            int c2 = readByte();
            if (c2 < 0x80 )             // -1,00-7F should be treated as first byte
                {
                return c2;
                }
            if (c2 > 0xBF)              // C0-FF first byte of a longer sequence
                {
                c1 = c2;
                continue;
                }

            // Valid two byte sequnce: C2-DF (first) and 80-BF (second)
            if (c1 < 0xE0)
            // input 2 byte, output 5+6 = 11 bit
                return ((c1 & 0x1f) << 6) | (c2 & 0x3f);

            // THIRD BYTE
            int c3 = readByte();
            if (c3 < 0x80 )             // -1,00-7F should be treated as first byte
                {
                return c3;
                }
            if (c3 > 0xBF)              // C0-FF first byte of a longer sequence
                {
                c1 = c3;
                continue;
                }

			// First: E0 and Second: below A0 are invalid
            if (c1 == 0xE0 && c2 < 0xA0)
                {
                return '*';
                }

			// First: ED and Second: above 9F are utf-16 surrogate pairs
            if (c1 == 0xED && c2 > 0x9F)
                {
                return '*';
                }

            // Valid three byte sequence
 			if (c1 < 0xF0)
			// input 3 byte, output 4+6+6 = 16 bit
            	return ((c1 & 0x0f) << 12) | ((c2 & 0x3f) << 6) | (c3 & 0x3f);
            
			// FOURTH BYTE
			int c4 = readByte();
 			if (c4 < 0x80 )             // -1,00-7F should be treated as first byte
                {
                return c4;
                }
            if (c4 > 0xBF)              // C0-FF first byte of a longer sequence
                {
                c1 = c4;
                continue;
                }

			// First: F0 and Second: below 90 are invalid
            if (c1 == 0xF0 && c2 < 0x90)
                {
                return '*';
                }

			// First: F4 and Second: above 8F are too big
            if (c1 == 0xF4 && c2 > 0x8F)
                {
                return '*';
                }

			// Valid four byte sequence
			// input 4 byte, output 3+6+6+6 = 21 bit
            int cp = ((c1 & 0x07) << 18) | ((c2 & 0x3f) << 12) | ((c3 & 0x3f) << 6) | (c4 & 0x3f);
            
			cp -= 0x10000;

			unRead(0xDC00 | (cp & 0x3FF));
			return 0xD800 | ((cp >> 10) & 0x3FF);
			}
        }


    public void unRead( int chr )
        {
        unreadBuffer.add( chr );
        }


    @Override
    public int read(char[] buf, int offset, int len) throws IOException
        {
        if ((offset | len) < 0 || offset > buf.length || buf.length - offset < len)
            {
            throw new ArrayIndexOutOfBoundsException();
            }

        int c;
        int l;

        for (l=0; l < len ; l++)
            {
            if ( (c=read()) == -1 )
                return -1;			// Jobb lenne break; akkor a hosszt adnánk vissza
            buf[offset+l] = (char)c;
            }

        return l;
        }


    public String readLine() throws IOException
        {
        return readLineTill( (char)0x0a );
        }


    public String readLineTill( char ending ) throws IOException
        {
        checkNotClosed();

        StringBuilder buf = new StringBuilder();

        int c;

        while ( (c = read()) >= ' ' && c != ending )
            {
            // Ez csak biztonsági ellenőrzés, nagyméretű, 0x0a szekvenciát nem tartalmazó file-ok miatt
            if ( buf.length() >= MAX_LINE_LENGTH )
                throw new IOException( "ERROR! readLine(): LineDescriptor exceeds MAX_LINE_LENGTH!" );

            buf.append( (char)c );
            }
        return buf.toString();
        }


    @Override
    public boolean markSupported()
        {
        return true;
        }


    @Override
    public void mark(int readLimit) throws IOException
        {
        checkNotClosed();
        markedPosition = block[activeBlock].offset + pointerInBlock;
        }


    @Override
    public void reset() throws IOException
        {
        checkNotClosed();
        seek( markedPosition );
        }


    @Override
    public long skip( long skipBytes ) throws IOException
        {
        checkNotClosed();
        if ( skipBytes <= 0 )
            return 0;

        // seek majd ellenőrzi, hogy pufferen belül vagyunk-e
        long tempPosition = getFilePointer();

        seek( tempPosition + skipBytes );

        return getFilePointer() - tempPosition;
        }

    }
