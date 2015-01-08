package dmeeuwis.nakama.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class DataHelper {

    public List<Map<String, String>> selectRecords(SQLiteDatabase db, String sql, Object ... params){
    	List<Map<String, String>> result = null;
    	String[] sparams = asStringArray(params);
    	Cursor c = db.rawQuery(sql,sparams);
    	try {
	        if(c != null && c.moveToFirst()){
	        	result = new ArrayList<>(c.getCount());
	        	int columnCount = c.getColumnCount();
	        	
	        	Map<String, String> m = new HashMap<String, String>();
	        	for(int i = 0; i < columnCount; i++){
	        		String colName = c.getColumnName(i);
	        		String rowColValue = c.getString(i);
	        		m.put(colName, rowColValue);
	        	}
	        	result.add(m);
	        }
    	} finally {
    		if(c != null) c.close();
    	}
    	return result;
    }
    
    public static Integer selectInteger(SQLiteDatabase db, String sql, Object ... params){
    	Cursor c = db.rawQuery(sql, asStringArray(params));
    	try {
	        if(c.moveToFirst())
	        	return c.getInt(0);
    	} finally {
    		c.close();
    	}
    	throw new SQLiteException("Could not find id from newly created vocab list.");
    }

    public static String selectString(SQLiteDatabase db, String sql, Object ... params){
    	Cursor c = db.rawQuery(sql, asStringArray(params));
    	try {
	        if(c.moveToFirst())
	        	return c.getString(0);
    	} finally {
    		c.close();
    	}
    	throw new SQLiteException("Could not find id from newly created vocab list.");
    }
    
    public static String selectStringOrNull(SQLiteDatabase db, String sql, Object ... params){
    	Cursor c = db.rawQuery(sql, asStringArray(params));
    	try {
	        if(c.moveToFirst())
	        	return c.getString(0);
    	} finally {
    		c.close();
    	}
    	return null;
    }
    
    public static List<String> selectStringList(SQLiteDatabase db, String sql, Object ... params){
    	List<String> entries = new LinkedList<String>();
    	Cursor c =  db.rawQuery(sql, asStringArray(params));
    	try {
	    	if(c.moveToFirst()){
	    		do {
	    			String value = c.getString(0);
	    			entries.add(value);
	    		} while(c.moveToNext());
	    	}
    	} finally {
    		c.close();
    	}
    	return entries;
    }
    
    public static List<Integer> selectIntegerList(SQLiteDatabase db, String sql, Object ... params){
    	List<Integer> entries = new LinkedList<Integer>();
    	Cursor c =  db.rawQuery(sql, asStringArray(params));
    	try {
	    	if(c.moveToFirst()){
	    		do {
	    			Integer value = c.getInt(0);
	    			entries.add(value);
	    		} while(c.moveToNext());
	    	}
    	} finally {
    		c.close();
    	}
    	return entries;
    }

    private static String[] asStringArray(Object[] params){
    	String[] sparams = new String[params.length];
    	for(int i = 0; i < params.length; i++){
    		sparams[i] = params[i].toString();
    	}
    	return sparams;
    }
}