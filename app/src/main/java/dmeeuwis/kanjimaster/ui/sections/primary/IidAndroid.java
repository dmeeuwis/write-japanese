package dmeeuwis.kanjimaster.ui.sections.primary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.UUID;

import dmeeuwis.kanjimaster.logic.data.Iid;

public class IidAndroid implements Iid {

    private static UUID cachedIid;

    private static Context app;

    public IidAndroid(Context app){
        this.app = app;
    }

    /**
     * Returns an install id, creating it and putting it into SharedPreferences if necessary.
     * Not thread-safe, call from ui thread.
     */
    public UUID get(){
        if(cachedIid != null){
            return cachedIid;
        }

        SharedPreferences prefs = app.getSharedPreferences("iid", Context.MODE_PRIVATE);
        String existingInstallId = prefs.getString("iid", null);

        if(existingInstallId == null){
            // try the previous shared prefs format, that used the global prefs for iid
            SharedPreferences oldPrefs = PreferenceManager.getDefaultSharedPreferences(app);
            existingInstallId = oldPrefs.getString("iid", UUID.randomUUID().toString());

            if(existingInstallId != null){
                Log.i("nakama", "Migrating IID to new shared prefs storage");
                SharedPreferences.Editor edit = oldPrefs.edit();
                edit.remove("iid");
                edit.apply();

                SharedPreferences.Editor newEdit = prefs.edit();
                newEdit.putString("iid", existingInstallId);
                newEdit.apply();
            }
        }
        UUID iid = null;
        try {
            iid = UUID.fromString(existingInstallId);
        } catch(Throwable t){
            Log.d("nakama", "Error parsing iid; creating a new one", t);
        }
        if(iid == null) {
            SharedPreferences.Editor ed = prefs.edit();
            iid = UUID.randomUUID();
            Log.i("nakama", "KanjiMasterActivity: setting installId " + iid);
            ed.putString("iid", iid.toString());
            ed.apply();
        }
        cachedIid = iid;
        return iid;
    }
}
