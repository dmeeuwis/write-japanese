package dmeeuwis.nakama.helpers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;
import dmeeuwis.indexer.KanjiFinder;
import dmeeuwis.indexer.Querier;
import dmeeuwis.indexer.QuerierFileInputStream;
import dmeeuwis.util.Util;

public class DictionarySet {

	public static final String KANJIDICT_FILE = "kanjidic.utf8.awb";
	public static final String KANJIDICT_INDEX = "kanjidic.index.awb";
	public static final String SMALL_EDICT_FILE = "dict.ichi1.subbed.indexed.awb";
	public static final String SMALL_SEARCH_HASH_TO_EDICT_ID = "index.ichi1.searchHashToEdictId.awb";
	public static final String SMALL_EDICT_ID_TO_LOCATION_SIZE = "index.ichi1.edictIdToLocationSize.awb";
	
	private KanjiFinder kanjiFinder;
	public final Querier querier;
	
	final AssetManager asm;

	final AssetFileDescriptor dictionaryFileFd;
	final FileInputStream dictionaryFileStream;
	
	final AssetFileDescriptor searchHashToEdictIdFd;
	final FileInputStream searchHashToEdictIdStream;
	
	final AssetFileDescriptor edictIdToLocationSizeFd;
	final FileInputStream edictIdToLocationSizeStream;
	
	final AssetFileDescriptor kanjiDictFd;
	final FileInputStream kanjiDictStream;
	final FileChannel kanjiDictCh;
	
	final AssetFileDescriptor kanjiIndexFd;
	final FileInputStream kanjiIndexStream;

	private static DictionarySet singletonSet;
	
	synchronized public static DictionarySet singleton(Context context){
		if(singletonSet == null)
			singletonSet = new DictionarySet(context);
		return singletonSet;
	}
	
	private DictionarySet(Context context) {
	   	Log.i("nakama", "Starting to create DictionarySet");
	   	long start = System.currentTimeMillis();
	   	
	   	asm = context.getAssets();
        
		try {
	    	dictionaryFileFd = asm.openFd(SMALL_EDICT_FILE);
	    	dictionaryFileStream = dictionaryFileFd.createInputStream();
			long dictionaryFileOffset = dictionaryFileFd.getStartOffset();
			Log.i("nakama", "DictionarySet: Time to get to dictionary file: " + (System.currentTimeMillis() - start) + "ms");
	    	
	    	searchHashToEdictIdFd = asm.openFd(SMALL_SEARCH_HASH_TO_EDICT_ID);
	    	searchHashToEdictIdStream = searchHashToEdictIdFd.createInputStream();
			long searchHashToEdictIdOffset = searchHashToEdictIdFd.getStartOffset();
			Log.i("nakama", "DictionarySet: Time to get to hash file: " + (System.currentTimeMillis() - start) + "ms");
	    	
	    	edictIdToLocationSizeFd = asm.openFd(SMALL_EDICT_ID_TO_LOCATION_SIZE);
	    	edictIdToLocationSizeStream = edictIdToLocationSizeFd.createInputStream();
			long edictIdToLocationSizeOffset = edictIdToLocationSizeFd.getStartOffset();
			long edictIdToLocationSizeByteLength = edictIdToLocationSizeFd.getLength();
			Log.i("nakama", "DictionarySet: Time to get to hash file: " + (System.currentTimeMillis() - start) + "ms");
	    	
	    	querier = new QuerierFileInputStream(searchHashToEdictIdStream, searchHashToEdictIdOffset, edictIdToLocationSizeStream, edictIdToLocationSizeOffset, edictIdToLocationSizeByteLength,  dictionaryFileStream, dictionaryFileOffset);
	
			kanjiDictFd = asm.openFd(KANJIDICT_FILE);
			kanjiDictStream = this.kanjiDictFd.createInputStream();
			kanjiDictCh = kanjiDictStream.getChannel();
			
	        kanjiIndexFd = asm.openFd(KANJIDICT_INDEX);
	        kanjiIndexStream = this.kanjiIndexFd.createInputStream();
			Log.i("nakama", "DictionarySet: Time to get to kanji index file: " + (System.currentTimeMillis() - start) + "ms");
	    	

		} catch(IOException e){
			throw new RuntimeException("Error accessing internal assets", e);
		}
	}

	synchronized public KanjiFinder kanjiFinder(){
		if(kanjiFinder == null){
			long kanjiDictOffset = kanjiDictFd.getStartOffset();
			try {
				kanjiFinder = new KanjiFinder(kanjiIndexStream, kanjiIndexFd.getLength(), kanjiDictCh, kanjiDictOffset);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} 
		return kanjiFinder;
	}
	
	public void close(){
		return; 
/*		safeClose(dictionaryFileStream);
		safeClose(dictionaryFileFd);
		
		safeClose(searchHashToEdictIdStream);
		safeClose(searchHashToEdictIdFd);
		
		safeClose(edictIdToLocationSizeStream);
		safeClose(edictIdToLocationSizeFd);
		
		safeClose(kanjiDictStream);
		safeClose(kanjiDictFd);
		
		safeClose(kanjiIndexStream);
		safeClose(kanjiIndexFd);
*/
	}
	
	
	public String[] findKanjiPaths(char kanji) throws IOException{
		int unicodeValue = kanji;
		String hexValue = Integer.toHexString(unicodeValue);
	
		InputStream in = asm.open(hexValue + ".path");
		String pathString;
		try {
			pathString = Util.slurp(in);
		} finally {
			in.close();
		}
		
		return pathString.split("\n");
	}
}