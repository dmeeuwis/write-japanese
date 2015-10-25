package dmeeuwis.nakama.data;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    final Context context;

    /** Set up the sync adapter. */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.context = context;
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        this.context = context;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.i("nakama", "onPerformSync!");
        try {
            PracticeLogSync sync = new PracticeLogSync(context);
            sync.sync();
        } catch (IOException e){
            Log.e("nakama-sync", "Error during sync", e);
        }
    }
}
