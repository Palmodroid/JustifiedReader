package digitalgarden.justifiedreader.bidict;

import java.io.Reader;


public class Compare
	{
	public final static int SEARCH_MASK = 0xFF00;			// Csak a felső byte értékes
	public final static int INTERNATIONAL_MASK = 0xFF80; 	// A felső byte és a következő két bit értékes
	public final static int HUNGARIAN_MASK = 0xFF40;
	
	// 0x0000 Megszakítás, nem folytatódik az ellenőrzés (jelenleg '=' és 0x0A (LF)) 
	// ((Ez valójában minden 0x2000 alatti értékre igaz volt!!))
	// 0x0100 Szóköz/írásjel NEM VESZI FIGYELEMBE! kötőjel, zárójel, matematikai jelek, idéző jelek; MINDEN EGYÉB
	// 0x01C0 Szóköz/írásjel ABC rendezésnél FIGYELEMBE VESZI! szóköz, aláhúzás, vessző, írásjelek,
	//			((0x100 a legkisebb érték, ami maszkolás után nem 0-t ad))
	// 0x3c00 tag nyitó-jel
	// 0x3e00 tag záró-jel (jelenleg '<' és '>')
	
	private final static int _STOP_ = 0x0000;
	private final static int _Tbeg_ = 0x3c00;
	private final static int _Tend_ = 0x3e00;
	private final static int _skip_ = 0x0100; 
	
	private final static char[] abc={
		_STOP_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 0 -         
		_skip_, _skip_, _STOP_, _skip_, _skip_, _STOP_, _skip_, _skip_, 	// 8 -         
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 16 -         
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 24 -         
		0x01C0, 0x01C1, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 32 -  !"#$%&'
		_skip_, _skip_, _skip_, _skip_, 0x02C2, _skip_, 0x01C3, _skip_, 	// 40 - ()*+,-./

// vessző változott az igealakok miatt, olyan, mint a betű
		
		0x3000, 0x3100, 0x3200, 0x3300, 0x3400, 0x3500, 0x3600, 0x3700, 	// 48 - 01234567
		0x3800, 0x3900, _skip_, 0x01C4, _Tbeg_, _STOP_, _Tend_, 0x01C5, 	// 56 - 89:;<=>?
		
		_skip_, 0x4100, 0x4200, 0x4300, 0x4400, 0x4500, 0x4600, 0x4700, 	// 64 - @ABCDEFG
		0x4800, 0x4900, 0x4a00, 0x4b00, 0x4c00, 0x4d00, 0x4e00, 0x4f00, 	// 72 - HIJKLMNO
		0x5000, 0x5100, 0x5200, 0x5300, 0x5400, 0x5500, 0x5600, 0x5700, 	// 80 - PQRSTUVW
		0x5800, 0x5900, 0x5a00, _Tbeg_, _skip_, _Tend_, _skip_, 0x01C0, 	// 88 - XYZ[\]^_
		
		_skip_, 0x4100, 0x4200, 0x4300, 0x4400, 0x4500, 0x4600, 0x4700, 	// 96 - `abcdefg
		0x4800, 0x4900, 0x4a00, 0x4b00, 0x4c00, 0x4d00, 0x4e00, 0x4f00, 	// 104 - hijklmno
		0x5000, 0x5100, 0x5200, 0x5300, 0x5400, 0x5500, 0x5600, 0x5700, 	// 112 - pqrstuvw
		0x5800, 0x5900, 0x5a00, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 120 - xyz{|}~
		
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 128 - 
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 136 - 
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 144 - 
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 152 - 
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 160 -  ¡¢£¤¥¦§
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 168 - ¨©ª«¬­®¯
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 176 - °±²³´µ¶·
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 184 - ¸¹º»¼½¾¿
		
		0x4101, 0x4102, 0x4103, 0x4104, 0x4105, 0x4106, 0x4108, 0x4309, 	// 192 - ÀÁÂÃÄÅÆÇ
		0x4501, 0x4502, 0x4503, 0x4505, 0x4901, 0x4902, 0x4903, 0x4905, 	// 200 - ÈÉÊËÌÍÎÏ
		_skip_, 0x4e04, 0x4f01, 0x4f02, 0x4f03, 0x4f04, 0x4f45, _skip_, 	// 208 - ÐÑÒÓÔÕÖ×
		_skip_, 0x5501, 0x5502, 0x5503, 0x5545, _skip_, _skip_, 0x530A, 	// 216 - ØÙÚÛÜÝÞß
		
		0x4101, 0x4102, 0x4103, 0x4104, 0x4105, 0x4106, 0x4108, 0x4309, 	// 224 - àáâãäåæç
		0x4501, 0x4502, 0x4503, 0x4505, 0x4901, 0x4902, 0x4903, 0x4905, 	// 232 - èéêëìíîï
		_skip_, 0x4e04, 0x4f01, 0x4f02, 0x4f03, 0x4f04, 0x4f45, _skip_, 	// 200 - ðñòóôõö÷
		_skip_, 0x5501, 0x5502, 0x5503, 0x5545, _skip_, _skip_, 0x5905, 	// 248 - øùúûüýþÿ
		
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 256 - ĀāĂăĄąĆć
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 264 - ĈĉĊċČčĎď
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 272 - ĐđĒēĔĕĖė
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 280 - ĘęĚěĜĝĞğ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 288 - ĠġĢģĤĥĦħ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 296 - ĨĩĪīĬĭĮį
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 304 - İıĲĳĴĵĶķ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 312 - ĸĹĺĻļĽľĿ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 320 - ŀŁłŃńŅņŇ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 328 - ňŉŊŋŌōŎŏ
		0x4f47, 0x4f47, 0x4f08, 0x4f08, _skip_, _skip_, _skip_, _skip_, 	// 336 - ŐőŒœŔŕŖŗ
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 344 - ŘřŚśŜŝŞş
		_skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 352 - ŠšŢţŤťŦŧ
		0x5504, 0x5504, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 360 - ŨũŪūŬŭŮů
		0x5547, 0x5547, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_, 	// 368 - ŰűŲųŴŵŶŷ
		0x5905, _skip_, _skip_, _skip_, _skip_, _skip_, _skip_			 	// 376 - ŸŹźŻżŽž
		};


	public static int getFirstComparableCharacter(Reader r, int mask, boolean tagEnabled)
		{
		boolean tag = false;
		int c;
		
		do {
			try
				{
				c = r.read();
				}
			catch (Exception x)
				{
				c = -1; 		// Hiba esetén string végét szimulálunk
				}
			
			if (c < 0)
				c = _STOP_;	// de a befejezést egységesen 0-val kell jelölni!
			else if (c >= abc.length)
				c = _skip_; 	// A speciális karakterek nem érdekesek
			else
				{
				c = abc[c] & mask;
		
				if (c == _Tbeg_)
					{
					tag = tagEnabled;	// Csak tag_enabled esetén kapcsoljuk be
					c = _skip_;		// Ha viszont nincs bekapcsolva, akkor ezt át kell ugrani
					}
				else if (c == _Tend_)
					{
					c = _skip_;
					tag = false;
					}
				}
			} while (c == _skip_ || tag);
		
		return c;
		}
	
	
	//public static int Readers(Reader r1, Reader r2, int mask) throws IllegalArgumentException
	//	{
	//	return Readers(r1, r2, mask, true);
	//	}
	
	
	/* 
	 * r1 és r2 összehasonlítása táblázat alapján
	 * Eredmény:  0: s1==s2 -:s1 +:s2 a kisebb
	 * Az eredmény megadja az egyezõ karakterek számát is, abszolút értékben. 0: az összes egyezik
	 */
	
	public static int Readers(Reader r1, Reader r2, int mask, boolean tagEnabled) throws IllegalArgumentException
		{
		int e=0;		// egyező karakterek+1 (0: teljes egyezés)

		int n;
		
		int[] c={' ', ' '};
		Reader[] s={r1, r2};

//StringBuilder[] stb = { new StringBuilder(), new StringBuilder() };	

		do	{
			// mindkét stringben ellépünk a következõ értékes karakterig
			for (n=0; n<2; n++) 
				{
				c[n] = getFirstComparableCharacter(s[n], mask, tagEnabled);
//if (c[n] > 0)	stb[n].append( (char)(c[n]/0x100) );
				}
			
			// itt c[0] vagy értékes, vagy 0 (vége jelzés)
			if ( e==0 && c[0] == _STOP_ )
				throw new IllegalArgumentException("First parameter is empty!");

			// melyeknek értékét tároljuk, és továbblépünk - ezért lesz e legalább 1
			e++;
	
			// kilép, ha a két karakter különbözik, vagy bármely string véget ér 
			}	while (c[0] == c[1] && c[0] > _STOP_ && c[1] > _STOP_);
		
		// ha itt is egyezik a két karakter, akkor a stringek végig egyeztek
		if (c[0] == c[1])
			e=0;
	
		// különben az eltérés iránya határozza meg, hogy + v. - értéket adunk vissza
		else if (c[1]>c[0])
			e=-e;
	
//Log.d("DA", "<" + stb[0].toString() + "> vs. <" + stb[1].toString() + "> eredménye: " + e);

		// e elõjele a stringek sorrendjét mutatja, míg abs. értéke az egyezõ karakterek számát (+1-et)
 		return e;
		}

	}
