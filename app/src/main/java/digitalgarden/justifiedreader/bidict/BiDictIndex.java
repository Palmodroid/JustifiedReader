// Ver: 3
package digitalgarden.justifiedreader.bidict;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Environment;
import digitalgarden.justifiedreader.jigreader.*;
import digitalgarden.justifiedreader.scribe.*;
import digitalgarden.justifiedreader.debug.*;


public class BiDictIndex
	{
	// Kell egy verziószám a program ellenőrzéséhez is
	public static final String BiDictVersion = "BiDict 5-L";
	
	// Verziószám és kiterjesztések az index-file elmentéséhez
	private static final int BIDICT_VERSION = 1; 
	private static final String dicExt = ".bic";
	private static final String idxExt = ".bix";
	
	// true érték jelzi, ha az index-listák készen állnak a felhasználásra
	private boolean ready = false;
	
	// szóanyagot tartalmazó file (.dic) hivatkozásai
	private File dicFile;
	private JigReader dicReader;

	// mindkét index tárolására szolgáló file
	private File idxFile;

	// abc alapú rendezés (.abc index file) hivatkozásai
	private int abcMask;
	private List<Integer>[] abcList = new List[2];
	
	// keresésre optimalizált rendezés (.idx index file) hivatkozásai 
	private int idxMask;
	private List<Integer>[] idxList = new List[2];

	// 0 - tag KIKAPCSOLT rész; 1 - tag BEKAPCSOLT rész
	private int dic = 0;
	
	// Ettől az indextől kezdi a számolást
	public final int FIRST_INDEX = 1; 

	// A hívónak ne kelljen tárolni a file-neveket, mi megtesszük
	private final String sourceDirName;
	private final String sourceFileName; 
	
	
	/**
	 * Legfontosabb rész: dic beállítása
	 */
	
	public void setDic(int dic)
		{
		if (dic>=0 && dic<=1)
			this.dic = dic;
		}
		
	public int getDic()
		{
		return dic;
		}
		
	public void toggleDic()
		{
		dic = (dic == 0) ? 1 : 0;
		}
	
	// itt new string-et kellene visszaadni
	// de eyt nem tervezzuk felhasznalni, ezert hivatkozast is visszaadhatunk
	public String getSourceDirName()
		{
		return sourceDirName;
		}
	
	public String getSourceFileName()
		{
		return sourceFileName;
		}
		
	/**
	 * Konstruktor
	 */
	
	// Az példány "üzemképességét" a ready flag jelzi. Ezt kell lekérdezni az isReady függvénnyel
	// Ha ez true, akkor az indexek készen állnak
	public BiDictIndex( String sourceDirName, String sourceFileName ) throws IOException
		{
		// A loadEnabled (azaz a betöltés) alaphelyzetben engedélyezett
		this( sourceDirName, sourceFileName, true );
		}
	
	
	public BiDictIndex( String sourceDirName, String sourceFileName, boolean loadEnabled ) throws IOException
		{
		this.sourceDirName = sourceDirName;
		this.sourceFileName = sourceFileName;
			
		dicFile = fileFromNames(sourceDirName, sourceFileName, dicExt);
		idxFile = fileFromNames(sourceDirName, sourceFileName, idxExt);
		
		if ( checkFiles(dicFile, idxFile) )
			{
			// A betöltés (és a validálás) csak loadEnabled == true esetén történik meg
			if ( loadEnabled )
				ready = loadIndices();
			}
		}
	
	
	/**
	 * Ellenőrzések
	 */
	
	// Ez azért static, mert a static metódusok is hívják
	private static File fileFromNames(String sourceDirName, String sourceFileName, String extension)
		{
		File directory = new File(Environment.getExternalStorageDirectory(), sourceDirName);
		return new File(directory, sourceFileName + extension);
		}
	
	
	// Példány létrehozása nélkül ellenőrzi a .dic filet és az validitását
	// Hiba esetén IOException kivételt dob
	// false: érvénytelen az adatbázis, indexálás szükséges
	// true: az adatbázis működőképes, létre lehet hozni a példányt
	public static boolean check(String sourceDirName, String sourceFileName) throws IOException
	{
	File dicFile = fileFromNames(sourceDirName, sourceFileName, dicExt);
	File idxFile = fileFromNames(sourceDirName, sourceFileName, idxExt);
	
	if ( !checkFiles(dicFile, idxFile) )
		return false;
	
	if ( !checkIndexValidity(dicFile, idxFile) )
		return false;
			
	return true;
	}

	
	// Példány önellenőrzése - ha közben megváltozhatott a file pl.
	// Visszatérés ugyanazt az elvet követi, mint a többi esetben
	public boolean checkValidity() throws IOException
		{
		if ( !checkFiles(dicFile, idxFile) )
			return false;
		
		if ( !checkIndexValidity(dicFile, idxFile) )
			return false;
				
		return true;
		}
	
	
	// Csak a file-okat ellenőrzi, validitást nem
	private static boolean checkFiles(File dicFile, File idxFile) throws IOException
		{
		// Ellenőrizzük, hogy SD kártyát írható és olvasható!
		if ( !Environment.MEDIA_MOUNTED.equals( Environment.getExternalStorageState() )) 
			{
			throw new IOException("CANNOT FIND SD-CARD!");
			}
		
		// Ellenőrizzük a dic-file-t!
		if ( !dicFile.exists() || !dicFile.isFile() ) 
			{
			throw new FileNotFoundException("CANNOT FIND DIC FILE: " + dicFile.getAbsolutePath());
			}

		// Ellenőrizzük, hogy nem túl hosszú-e a .dic file!
		// nem long, csak int indexekkel dolgozunk (Ez is 2Gb méret!)
		
		// .dic file nem lehet üres, mert későbbi ellenőrzésnél nullával osztanánk
		if (dicFile.length() > (long)Integer.MAX_VALUE || dicFile.length() < 1L)
			{
			throw new IOException("INVALID " + dicFile.getName() + " FILE LENGTH!");
			}

		// Az előző hibák lehetetlenné teszik a futást !!

		// Ellenőrizzük le az idx-file-t!
		// Hiba esetén kilépünk, de a File-ra szükség van, legalább a létrehozáshoz (createIndices metódusban)
		if ( !idxFile.exists() ) 
			{
			return false; // itt ready==false jelenti a hibát
			}
		
		// Az előző hiba esetén (nincs meg az index-file), 
		// ki kell kényszeríteni az index-file-ok létrehozását 
		
		if ( !idxFile.isFile() )		
			{
			throw new FileNotFoundException(idxFile.getName() + " EXISTS AS DIRECTORY!");
			}
		// Ez egy ritka dolog, van file, de az egy könyvtár!
		
		// Ezen a ponton ellenőrizni kell, hogy a létező index-file érvényes-e
		
		return true;
		}

	
	// Keretezi a hasonnevű osztályt, static, ezért át kell adni neki a paramétereket
	// Beolvasás után be is zárja a megfelelő osztályt
	private static boolean checkIndexValidity(File dicFile, File idxFile) throws IOException
		{
		DataInputStream idxStream = null;
		
		try	{
			idxStream = new DataInputStream(new BufferedInputStream(new FileInputStream( idxFile )));

			checkIndexValidity(dicFile, idxStream);
			}
		catch (IllegalArgumentException iae)
			{
			return false;
			}
		finally
			{
			if (idxStream != null)
				{
				try	{
					idxStream.close();
					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note( "idxStream closing error in checkIndexValidity: " + e.toString() );
					}
				}
			}
	
		return true;
		}

	
	// a már megnyitott idxStream-ben ellenőrzi a fejlécet
	// így statikus és példány metódusok is használják, csak a paramétereket kell átadni
	private static void checkIndexValidity(File dicFile, DataInputStream idxStream) throws IOException, IllegalArgumentException
		{
		// 1. verziószám (int)
		int ver = idxStream.readInt();
		Scribe.note("Version read: " + ver + " (" + BIDICT_VERSION + " in bic)");
		if ( ver != BIDICT_VERSION )
			throw new IllegalArgumentException();
		
		// 2. dicFile neve (string)
		String name = idxStream.readUTF();
		Scribe.note("Name read: " + name + " (" + dicFile.getName() + " in bic)");
		if ( !dicFile.getName().equals(name) )
			throw new IllegalArgumentException();
		
		// 3. dicFile hossza (long)
		long len = idxStream.readLong();
		Scribe.note("Length read: " + len + " (" + dicFile.length() + " in bic)");
		if ( len != dicFile.length() )
			throw new IllegalArgumentException();
		
		// 4. dicFile módosítás időpontja
		long mod = idxStream.readLong();
		Scribe.note("Last mod. read: " + mod + " (" + dicFile.lastModified() + " in bic) - NOT CHECKED!");
		//if ( Math.abs(mod - dicFile.lastModified()) > 10000L)
		//	throw new IllegalArgumentException();
		}
	
	
	public boolean isReady()
		{
		return ready;
		}

	
	/**
	 * Indexek betöltése
	 */
	private boolean loadIndices() throws IOException
		{
        Scribe.locus( Debug.BIDICT );
        
		DataInputStream idxStream = null;
		
		try	{
			idxStream = new DataInputStream(new BufferedInputStream(new FileInputStream( idxFile )));

			checkIndexValidity(dicFile, idxStream);
			
			abcMask = idxStream.readInt();
			idxMask = idxStream.readInt();

			for (int n = 0; n < 2; n++)
				{
				int size = idxStream.readInt();
	
				abcList[n] = new ArrayList<Integer>( size );
				for ( ; size > 0 ; size--)
					abcList[n].add( idxStream.readInt() );
	
				// ez csak ellenőrzés miatt van itt!!
				idxStream.skipBytes(8); // "VEGE"
				
				size = idxStream.readInt();
	
				idxList[n] = new ArrayList<Integer>( size );
				for ( ; size > 0 ; size--)
					idxList[n].add( idxStream.readInt() );
				
				// ez csak ellenőrzés miatt van itt!!
				idxStream.skipBytes(8); // "VEGE"
				}
			}
		catch (IllegalArgumentException iae)
			{
			return false;
			}
		finally
			{
			if (idxStream != null)
				{
				try	{
					idxStream.close();
					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note("idxStreamclosing error in loadIndices: " + e.toString());
					}
				}
			}
	    Scribe.debug( Debug.BIDICT, "Index is loaded.");

        
		return true;
		}
	
	
	/**
	 * Kényszerített index-lista készítés 
	 * - részekre bontva, hogy háttérszálon futhasson Asynctaskban, Progress visszajelzéssel
	 */

	private JigReader randomReader;	// Ez is a dicFile-t olvassa, csak "ugrál" a szavak között 
	private int[] index = new int[2];
	private final int MAX_ITEM = 100;
	
	
	// Csak .dic file van, tehát a szükséges maszkokat sem ismerjük
	public void prepareIndices(int abcMask) throws IOException
		{
		dicReader = new JigReader(dicFile);
		randomReader = new JigReader(dicFile); 
		// ez addIndex-hez szükséges, de hogy ne kelljen nyitni/zárni, kikerült kívülre
		
		// Elsőként abcList készül el, mert az indexForSearch erre mutat
		abcList[0] = new ArrayList<Integer>();
		abcList[1] = new ArrayList<Integer>();
		this.abcMask = abcMask;

		idxList[0] = new ArrayList<Integer>();
		idxList[1] = new ArrayList<Integer>();
		idxMask = Compare.SEARCH_MASK;
		
		index[0] = 0;
		index[1] = 0;
		// Ezt is itt kell inicializálni, mert a createAbcIndices egymás után többször meghívásra kerül.
		}

	
	public int createAbcIndices() throws IOException
		{
		long pos = 0L;
		StringReader wordReader;
		
		// legfeljebb 25 szót néz meg
		for (int n=0 ; n<MAX_ITEM && !dicReader.isEof() ; n++)	
			{
			pos = dicReader.getFilePointer(); // az index pozíciót tárol, nem magát a sort
			// Talán gyorsabb, ha az összehasonlítandó szócikk a memóriában van
			
			wordReader = new StringReader( dicReader.readLine() );			
			addIndex(0, wordReader, true, abcMask, (int)pos);
			
			wordReader.reset();
			addIndex(1, wordReader, true, abcMask, (int)pos);
			//abcList alapján rendezzük
			//?? StringReader kiemelhető, de meg kell nézni, hogy nullázza-e!!
			}
		
		// %-ban adja vissza, hogy hol tart. DE! 100%-t soha nem ér el, mert az utolsó szócikk ELEJÉT fogja nézni
		if (dicReader.isEof())
			return 1000;
		else
			return (int)(1000L * pos / dicReader.length());
		}


	public int createIdxIndices(int dic) throws IOException
		{
		// Másodjára idxList készül el, abcList alapján
		// .dic <- abcList (egyes elemek filepozíció-ra mutatnak) <- idxList (egyes elemek abc index-re mutatnak)
		// abcList-ben nem tudunk for-each ciklust használni, mert nem csak az elemekre, hanem az indexekre is szükség van 

		// ide jön be az index, amit prepare-ben előkészítettünk

		// nullával való osztás miatt ellenőrizni kell az elemszámot 
		if ( abcList[dic].size() == 0 )
			throw new IOException("NO DATA in DIC FILE:" + dicFile.getName());
		
		long pos;

		for (int n=0; n<MAX_ITEM && index[dic] < abcList[dic].size(); n++) 
			{
			pos = (long)abcList[dic].get(index[dic]);
			dicReader.seek( pos );
			// nem elég!! utána reset-tel áll vissza!! Meg kell jelölni!
			
			addIndex(dic, dicReader, false, idxMask, index[dic]); 
			//idxList alapján keressük meg a helyét, és abcList indexét tároljuk
			
			index[dic]++;
			}
		
		if ( index[dic] == abcList[dic].size() )
			return 1000;
		else
			return 1000 * index[dic] / abcList[dic].size();
		}

	
	public void finishIndices() throws IOException
		{
		ready = true;

		randomReader.close();
		dicReader.close();
		
		// KÉSZ A KÉT INDEX FILE - ITT MÁR JÖHET(NE) A MUNKA
		}


	/**
	 * Az index-lista készítést kiszolgáló, szigorúan belső metódus:
	 * Egyetlen elem besorolása
	 * @throws IOException 
	 */
	// Szócikk pozíciója:
	// abcList: (long) abcList.get( index );
	// idxList: (long) abcList.get( idxList.get(index) );
	
	// Paraméterek:
	// - a beillesztendő szócikk - Reader-ként (Reader wordReader)
	// - boolean isAbc - true értéknél abcList, false értéknél idxList készül
	// (Mivel nagyon más a megközelítési módszer, nem érdemes a két listát átadni)
	// - mask ((ez is különbözik a két listánál. idxMask mindig ugyanaz, legalábbis eddig, de ugyanúgy feljegyezzük, mint abcList-et))
	// - int wordData (abc-nél: szócikk pozíciója, idx-nél: abcList index-e) ez kerül beillesztésre 
	//
	// ide tartozik még a rendomReader, amit megnyitva megkapunk, ebben ugrálunk az összehasonlításhoz
	
	// az összehasonlítás előkészítését kiemelhetjük külön metódusba
	// autoboxing miatt nem kell .intValue() kitétel, de ez lehet, h. hosszabb futást eredményez
	private int addIndex(int dic, Reader wordReader, boolean isAbc, int mask, int wordData) throws IOException
		{
		int kezd;
		int kozp;
		int vege;
		int cmp;

		// itt a lényeges különbség: TAG ENGEDÉLYEZÉSE
		boolean tagEnabled = (dic==0) ? false : true;

		// Ezen index-lista alapján keresünk ill. ebbe illesztjük be a végén
		List<Integer> index = isAbc ? abcList[dic] : idxList[dic];

/*
wordReader.mark(Integer.MAX_VALUE);
StringBuilder stb = new StringBuilder();
int c;
while ( (c=wordReader.read()) >= 32 )
	stb.append( (char)c );
Log.i("DA", "Add <" + stb.toString() + "> to " + (isAbc ? "ABC" : "IDX") + " TagEnabled:" + tagEnabled);
wordReader.reset();
*/
		
		try
			{
			if (index.size() > FIRST_INDEX)	// Legyen egy kiinduló érték, ami később a címsor lesz
				// ami ez alatt van, összehasonlítás nélkül tesz be
				{
				// Nem ellenőriztük, csak feltételeztük, hogy a mark() funkció támogatott!
				wordReader.mark(Integer.MAX_VALUE);

				vege = index.size() -1;
	
				// Beállítjuk az összehasonlítást
				if (isAbc)
					randomReader.seek( (long)index.get(vege) ); // itt index == abcList
				else
					randomReader.seek( (long) abcList[dic].get( index.get(vege) ) ); // itt index == idxList
				
				if (Compare.Readers(wordReader, randomReader, mask, tagEnabled) >= 0)		
																// ÁLtalában sorban vannak, ezért nézzük először a végét
					kezd=vege+1;								// És mögé kell tegyük
				else
					{
					kezd = FIRST_INDEX;
					vege--;										// Ezt már megnéztük. De nem jött be.
					}
	
				while (vege >= kezd)
					{
					kozp = kezd + (vege-kezd)/2;
	
					// Beállítjuk az összehasonlítást
					if (isAbc)
						randomReader.seek( (long)index.get(kozp) ); // itt index == abcList
					else
						randomReader.seek( (long) abcList[dic].get( index.get(kozp) ) ); // itt index == idxList
					wordReader.reset();

					cmp = Compare.Readers(wordReader, randomReader, mask, tagEnabled);
	
					if ( cmp<0 ) 								// Szo kisebb, elorebb van Első-Második(-)
						vege = kozp-1;
	
					else if ( cmp>0 ) 							//Szo nagyobb, hatrebb van Második-Első(-)
						kezd = kozp+1;
	
					else 										// Egyformák
						{										// ilyenkor az utolsó után teszi be
						kezd = kozp;
						do
							{
							kezd++;
	
							// Beállítjuk az összehasonlítást
							if (isAbc)
								randomReader.seek( (long)index.get(kezd) ); // itt index == abcList
							else
								randomReader.seek( (long) abcList[dic].get( index.get(kezd) ) ); // itt index == idxList
							wordReader.reset();
							
							} while( Compare.Readers(wordReader, randomReader, mask, tagEnabled) == 0 );
						break;
						}
					}
				index.add(kezd, wordData);

/*
Log.i("DA", stb.toString() + " added at " + kezd);
*/
				
				return kezd;
				}

			else // vagyis index.size() <= FIRST_INDEX
				{
				// Itt össze kell hasonlítani a wordReader-t bármivel
				// Ha nincs benne értékes karakter, akkor kivételt dob. 
				// Az eredmény nem érdekes
				//Compare.Readers(wordReader, new StringReader("="), Compare.SEARCH_MASK);
				// Jobb ez a másik metódus, nem dob kivételt, csak 0-t ad vissza, ha nincs értékes karakter
				if ( Compare.getFirstComparableCharacter(wordReader, Compare.SEARCH_MASK, tagEnabled) != 0 )
					index.add(wordData);
/*				
Log.i("DA", stb.toString() + " added at " + (index.size()-1));
*/
				return index.size()-1;
				}
			
			// a beillesztett adat független, csak paraméterként kaptuk meg
			}
		catch (IllegalArgumentException e)
			{
/*
Log.i("DA", "Error! No comparable chars in " + stb.toString());
*/
			return -1; // igazából nem gond, csupán ez a szó nem került be, mert üres
			}
		
		}

	
	/**
	 * Az elkészített adatbázis elmentése
	 */
	
	// Filename globális lesz!!
	public void saveIndices() throws IOException
		{
		DataOutputStream idxStream = null;
		
		try	{
			// A globális idxFile alapján készítünk egy stream-et 
			idxStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream( idxFile )));
			
			// 1. verziószám (int)
			idxStream.writeInt(BIDICT_VERSION);
			Scribe.note("Version written: " + BIDICT_VERSION);
			
			// 2. dicFile neve (string)
			idxStream.writeUTF(dicFile.getName());
			Scribe.note("Name written: " + dicFile.getName());
			
			// 3. dicFile hossza (long)
			idxStream.writeLong(dicFile.length());
			Scribe.note("Length written: " + dicFile.length());
			
			// 4. dicFile módosítás időpontja
			idxStream.writeLong(dicFile.lastModified());
			Scribe.note("Last mod. written: " + dicFile.lastModified());

			// abcMask (int) , ill. abcList (int size, {int items...}) elmentése
		
			idxStream.writeInt( abcMask );
			idxStream.writeInt( idxMask );

			for (int n = 0; n < 2; n++)
				{
				idxStream.writeInt( abcList[n].size() );
			
				for (Integer data : abcList[n])
					idxStream.writeInt(data);
			
				// ellenőrzés miatt 
				idxStream.writeChars("VEGE");
				
				// idxMask (int) , ill. idxList (int size, {int items...}) elmentése
				idxStream.writeInt( idxList[n].size() );
			
				for (Integer data : idxList[n])
					idxStream.writeInt(data);
		
				// ellenőrzés miatt 
				idxStream.writeChars("VEGE");
				}
			
			// ellenőrzés miatt
			idxStream.writeChars("***");

			idxStream.flush();
			}
		finally
			{
			if (idxStream != null)
				{
				try	{
					idxStream.close();
					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note("idxStream closing error in saveIndices" + e.toString());
					}
				}
			}
		}


	/**
	 * Az Adapter kiszolgálásáért felelős metódusok
	 * (Ebben az abcList játszik szerepet, az Adapter ill. a ListView annnak alapján működik)
	 */
	
	
	public int size()
		{
		return abcList[dic].size();
//return idxList.size();
		}

	
	// A pos az abcList-ben elfoglalt pozíciót mutatja
	// Az a gond, hogy KÉT Stringre van szükség. Volt két kiolvasási lehetőség word vagy article. 
	// De ha CSAK a másodikra van szükség, akkor word-ot kétszer ki kell olvasni
	// Ezért jobb a bundle:
	// WORD: Szöveg az első "=" jelig - ha van ilyen
	// ARTICLE: Minden más
	
	// Ebben visszadhatjuk pl. az isSelected értéket is, vagy hogy új-e
	// IS_SELECTED: ha pos == selected, vagyis isSelected értéke
	// IS_NEW: ha van benne hullám
	public Bundle getArticle(int pos) throws IndexOutOfBoundsException, IOException
		{
		Bundle ret = new Bundle(4);

		String first, second;
		if (dic == 0)
			{
			first = "WORD";
			second = "ARTICLE";
			}
		else
			{
			first = "ARTICLE";
			second = "WORD";
			}

		if ( pos >= 0 && pos < abcList[dic].size() )
//if ( pos >= 0 && pos < idxList.size() )
			{
			dicReader = new JigReader(dicFile);
			
			dicReader.seek( (long)abcList[dic].get( pos ) );
//dicReader.seek( (long)abcList.get( idxList.get(pos) ) );

			StringBuilder str=new StringBuilder();
			
			int	chr;
		 	int cntwrd=1; 					// Sorszámok számlálója
			boolean isNew = false;
			
		 	while (true)
		 		{
		 	
		 		chr=dicReader.read();
		 		
		  		if (chr=='=')      			// Váltás a szócikk második részére, csak full-nál jelenik meg 
		  			{
		  			ret.putString(first, str.toString());
		  			str.setLength(0);
		  			}
		
		  		else if (chr==0x0A || chr==-1)      	// Ez is kiírás végét jelölő karakter
		 			break;
		  
		  		else if (chr=='\\')      	// Ezzel meg kényszerítsünk ki egy új sort, pl. title-rekordban
		 			str.append('\n');		// Elvileg új sorra alakítja (file-ban ez nem 13 (CR), hanem 10 (LF) !!
		  
		  		else if (chr=='+' || chr=='*')	// 3 karakteres elválasztójel jön
		  			{
		  			if (cntwrd>1)			// elválasztójel minden jelentés ELŐTT van, de csak a KÖZÖTTIeket írjuk ki (tehát 0.-t nem)
		  				str.append(" / ");
	  				cntwrd++;  				
		  			}
		  		
		  		else if (chr=='[' || chr==']')
		  			;						// TAG jelölésére nincs szükségünk
		  		
		  		else if (chr=='$')
		  			str.append("<i>to</i> ");
					
				else if (chr=='~')
					isNew = true;
		  		
		 		else						// Jöhet a tényleges karakter (vagy épp '\0') kiírása
		 			str.append((char)chr);
		 		}

		 	dicReader.close();
		 	
		 	ret.putString(second, str.toString());
			
			ret.putBoolean("IS_SELECTED", isSelected(pos) );
			ret.putBoolean("IS_NEW", isNew );
			}
		else
			throw new IndexOutOfBoundsException();
	
		return ret;
		}

	
	/**
	 * A keresés kiszolgálása (idxList szerinti rendezés)
	 */
	
	private int selected = -1;
	
	public boolean isSelected(int pos)
		{
		return pos==selected;
		}

	
	/*
	 * Eredmény a megfelelő szó rekordjának száma
	 * 
	 * A keresőrutin feltételezi, hogy a bejegyzések abc rendben vannak. Megegyező bejegyzések lehetnek
	 * A keresés a folyamatos felezés elvén működik. 
	 * Teljes egyezés esetén meg kell keresni a legelső megfelelőt, mert több is lehet
	 * Ha nincs egyezés, akkor a két szomszédos elem közül azt adja vissza, amelyik több karakterre egyezik
	 */
	public int lookup(String searchString) throws IOException
		{
		int	kezd;
		int	vege;
		int	kozp = 0 ;
		int	cmp = 0 , cmp_kov = 0 ;		// ez csak a nyávogás miatt kell, különben nem látja, hogy inicializáltuk

		// itt a lényeges különbség: TAG ENGEDÉLYEZÉSE ?? EZT IS KI KÉNE VENNI KÜLÖN!
		boolean tagEnabled = (dic==0) ? false : true;
		
		// Ha üres stringet adtunk át, akkor visszaáll a legelejére, a TitleRec-re
		// !! Mégsem !! -1-t ad vissza, hogy ne legyen kijelölés !!
		if (searchString.equals(""))
			{
			this.selected = -1;
			return this.selected;
			}
			//return -1; // 0 TitleRec;

/*
Log.i("DL", "-");		
Log.i("DL", searchString + " keresése:");		
*/
		
		// Megnyitjuk a szótárfile-t, utf8reader-ként, ne felejtsük el bezárni!
		dicReader = new JigReader( dicFile );
		Reader searchReader=new StringReader(searchString);
		
		// a szótár.h-ban megadott első recordtól az utolsó létező rekordig keres
		// kezd és vege az első és utolsó még nem vizsgált szó rekordjának száma.
	    kezd = FIRST_INDEX;
	    vege = idxList[dic].size() - 1;		// fileLen1 + fileLen2;
	    
	    do	
	    	{
	       	if (kezd > vege)		// ebbe az ágba elvileg csak az első ciklus után juthat
	       		{
	       		
/*	       		
Log.i("DL", "Nincs megegyző szó!");		
*/	
	     
	       		// DE ELLENŐRIZNI KELL A KONSTRUKTORBAN, HOGY VAN-E ELÉG SZÓ!!
	       		
				// nem talált megfelelő elemet - kérdés: melyiket válassza a kisebbet v. a nagyobbat?
				// itt a másik szóval is össze kell hasonlítani
	       		
				// ha a keresett szo kozp elott van, akkor egy bejegyzéssel előrébb lépünk
	       		if (kezd==kozp)
	       			{
	       			cmp_kov=cmp;
	       			if (kozp>FIRST_INDEX)
	       				{
	       				kozp--;
	
	       			searchReader.reset();
	       			
	       				dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp) ));
	       				cmp = Compare.Readers(searchReader, dicReader, idxMask, tagEnabled);
	       				}
	       			else
	       				{
/*	       				
Log.d("DL", "Legelső index ELÉ esik! ");
*/	       				
	       				break; // minden marad a régiben!!
	       				}
					// FrmCustomAlert(DebugAlert, "nincs megfelelő!", "kezd==kozp", "keresett meg elorebb van");
	       			}
	
				// ha a keresett szó kozp után van, akkor csak megnézzük a következőt
				else if (vege==kozp)
					{
					if ( kozp < idxList[dic].size()-1 )
						{
						searchReader.reset();

	       				dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp+1) ));
	       				cmp_kov = Compare.Readers(searchReader, dicReader, idxMask, tagEnabled);
						}
	       			else
	       				{
/*
Log.d("DL", "Legutolsó index UTÁN esik! ");
*/	       				
	       				break;
	       				}
					// FrmCustomAlert(DebugAlert, "nincs megfelelő!", "vege==kozp", "keresett meg hatrebb van");
					}
	
				// ebbe az ágba pedig nem kerülhetünk - elméletileg
				else
					{
/*
Log.e("DL", "NEM LÉTEZIK! KEZD és VEGE között túl nagy a különbség!");
*/		
					
					//FrmCustomAlert(DebugAlert, "valami baj van!", " ", " ");
					}
				// ezen a ponton keresett szó és közp összehasonlítása van cmp-ben, kozp+1-é cmp_kov-ben
	
				// vegyük az abs értékeket
				if (cmp<0)		cmp=-cmp;
				if (cmp_kov<0)	cmp_kov=-cmp_kov;

/*
{
dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp) ));
String temp = dicReader.readFirstPart();

dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp+1) ));
String temp_kov = dicReader.readFirstPart();

Log.d("DL", temp + " (" + kozp + ", " + cmp + " egyezés) és " + temp_kov + " (" + kozp+1 + ", " + cmp_kov + " egyezés) közé esik.");
}
*/
	    				
	    		// !! FONTOS !! Mivel nem volt egyezés, cmp ill. cmp_kov itt nagyobb, mint 0
	    		// !! DE !! Ha nem létezik ilyen (mert "kilóg" a listából), akkor 0, tehát mindig kisebb !!
	    		if (cmp_kov>cmp)
	    			kozp++;
	
	       		break;		 		// ha nem talált egyezőt, akkor itt lép ki
	       		}					// a goto utasítással megspórolunk egy összehasonlítást
	       		
	    	// összesen v-e+1 elem, de k=ö/2 és k=(ö-1)/2 is jó (páratlannál azonos, párosnál a kisebb v. nagyobb "közepet" veszi"	
	    	kozp = kezd + (vege - kezd)/2;
	
	    	searchReader.reset();

	    	dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp) ));
			cmp=Compare.Readers(searchReader, dicReader, idxMask, tagEnabled);

			
// CSAK ELLENŐRZÉS MIATT !!
/*
dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp) ));
Log.d("DL", kezd + "-" + vege + " (" + kozp + ": " + dicReader.readFirstPart() + ")" + " Cmp: " + cmp );
*/
			
	    	// Keresett előrébb van, akkor a veget (utolsó nem vizsgált szó) a vizsgált szó elé állítjuk
	        // Sorrend: keres dicReader (-)
	        if (cmp < 0)
	        	vege=kozp-1;
	        	
	        // Keresett hátrébb van, akkor az elejét (első nem vizsgált szó) a már vizsgált mögé állítjuk
			// Sorrend: dicReader keres (+) 
	 		else if (cmp > 0)       
				kezd=kozp+1;
	        
	 		else
	 			{
/*
Log.i("DL", "Pontos egyezést találtam a " + kozp + ". indexen.");		
*/
	 			
	 			// kozp egyezik a keresett szóval, de nézzük meg az első egyezést, hátha van több is!
	 			while (kozp>FIRST_INDEX)
	 				{
	 				// kozp előtti egyezik?
	 				searchReader.reset();

	 		    	dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp-1) ));
	 		    	cmp_kov=Compare.Readers(searchReader, dicReader, idxMask, tagEnabled);
	 				
	 				// ha igen, akkor az lesz a keresett, és megyünk tovább
	 				if (cmp_kov==0)
	 					kozp--;
	 				// ha nem, akkor ezzel kilépünk - a külső ciklusból - is -, mivel cmp ebben az else ágban 0
	 				else
	 					break;
	 				}		
	 			
	 			}
				            
	    	} while (cmp!=0);

/*
dicReader.seek( (long)abcList[dic].get( idxList[dic].get(kozp) ));
Log.i("DL", kozp + ". indexen: " + dicReader.readFirstPart() + " visszaadva.");
*/
	    
		dicReader.close();
		this.selected= idxList[dic].get(kozp);
	    return this.selected;    
		}
	
	
	/**
	 * Add new word
	 * !! Ez hozzányúl a szóanyaghoz !!
	 * Ehhez engedély kell a Manifest-ben
	 * El kell menteni (a végén) az index táblát (újra) dirty jelző
	 * Megváltozik a teljes sorrend, újra meg kell jeleníteni a listát (adapter-nek jelezni)
	 * Ráadásul nem konzekvensek az id-k 
	 * 
	 * Visszatérési érték az abcIndex kellene legyen
	 * @throws IOException 
	 */
	
	
	boolean dirty = false; // ezt most csak itt kezeljük, mert csak itt írunk bele
	// a másik hely a teljes létrehozás lenne, de ott mindenképp menti
	
	
	public int addArticle(String newArticle) throws IOException
		{
		// ready ellenőrzése?? és másutt??
		
		
		// Elsőként írjuk hozzá a szövegünket a file-hoz
		// Mivel nem ugrálunk a file-ban (csak hozzáfűzünk), ezért elegendő lenne stream-ként megnyitni
		// De akkor nem tudjuk elkérni a pozíciót, ezért raf-ként lesz megnyitva
		
		RandomAccessFile raf = null;
		long position = 0L;
		int index = -1;
		
		try
			{
			raf = new RandomAccessFile( dicFile, "rws");
			//Log.d("BIDICT", "rws mode started!!");
			
			raf.seek( raf.length() -1 ); // Vége UTÁN kellene álljon
			if (raf.read() != 0x0a) // ?? MI VAN A SORVÉGE MÁS JELÖLÉSÉVEL ??
				raf.write(0x0a);
			
			position = raf.getFilePointer();
			// Itt ellenőrizni kellene a max. hosszat! Bár ugyis hibát ad, ha nem tudja kiírni

			// mielőtt bármit kiírnánk, beállítjuk az indexeket 
			ready = false; // Ha útközben kilép hiba miatt, akkor ne használhassuk!

			dicReader = new JigReader(dicFile);
			randomReader = new JigReader(dicFile); 
			
			for (int n=0; n<2; n++)
				{
				index = addIndex(n, new StringReader(newArticle), true, abcMask, (int)position);
				
				if (index != -1)
					{
					ListModified(n, index);
					addIndex(n, new StringReader(newArticle), false, idxMask, index);
// itt dicreader volt, de azt nem allitottuk be!
					}
				else 
					{
					// itt semmi nem történt, állítsuk vissza az eredeti állapotot
					ready = true;
					throw new IOException("Error adding new article!");
					}
				}
			
			raf.write( newArticle.getBytes() );
 			}
		finally // Mindent fordítva bezárunk, de külön-külön
			{
			if (randomReader != null)
				{
				try	{
					randomReader.close();
					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note("randomReader closing error in addArticle: " + e.toString());
					}
				}

			if (dicReader != null)
				{
				try	{
					dicReader.close();
					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note("dicReader closing error in addArticle: " + e.toString());
					}
				}

			if (raf != null)
				{
				try	{
					raf.close();

Scribe.note("Raf closed after append");
// 2. dicFile neve (string)
Scribe.note("Name: " + dicFile.getName());
// 3. dicFile hossza (long)
Scribe.note("Length: " + dicFile.length());
// 4. dicFile módosítás időpontja
Scribe.note("Last mod.: " + dicFile.lastModified());

					}
				catch (IOException e)
					{
					// Erről nem akarjuk informálni a felhasználót
					Scribe.note("raf closing error in addArticle: " + e.toString());
					}
				}
			}

		dirty = true;
		ready = true;
		
		selected = -1; // vagy index
		
		return index;
		}
	
	
	private void ListModified(int dic, int atItem)
		{
		int item;
		
		for ( int n = 0; n < idxList[dic].size(); n++ )
			{
			item = idxList[dic].get(n);
			if ( item >= atItem )
				{
				idxList[dic].set(n, item+1);
				//Log.d("DA", n + " pozicion " + item + " novelve: " + idxList[dic].get(n));
				}
			}
		}

		
	public void saveIndicesIfDirty() throws IOException
		{
		if (dirty)
			saveIndices();
		dirty = false;
		}
	
	}
