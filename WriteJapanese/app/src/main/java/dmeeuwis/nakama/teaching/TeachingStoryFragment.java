package dmeeuwis.nakama.teaching;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import dmeeuwis.Kanji;
import dmeeuwis.KanjiRadicalFinder;
import dmeeuwis.kanjimaster.R;
import dmeeuwis.nakama.data.StoryDataHelper;
import dmeeuwis.nakama.data.DictionarySet;
import dmeeuwis.nakama.kanjidraw.Glyph;
import dmeeuwis.nakama.views.AnimatedCurveView;
import dmeeuwis.nakama.views.KanjiWithMeaningView;

public class TeachingStoryFragment extends Fragment {

	char character;
	Kanji kanji;        // may be null for kana
	String[] currentCharacterSvg;
	EditText storyEditor;
	TextView kanjiLabel;
	AnimatedCurveView kanim;
	GridView gridView;
	ArrayAdapter<Kanji> radicalAdapter;
	TeachingActivity parent;
	LoadRadicalsFile loadFileTask;
	StoryDataHelper db;
	View radicalsCard;

	@Override
	public void onAttach(Activity activity) {
		parent = (TeachingActivity) getActivity();
		Log.d("nakama", "TeachingInfoFragment: parent is " + parent);
		this.character = parent.getCharacter().charAt(0);

		loadFileTask = new LoadRadicalsFile();
		loadFileTask.execute();

        DictionarySet sd = new DictionarySet(activity);
		try {
			this.kanji = sd.kanjiFinder().find(parent.getCharacter().charAt(0));
		} catch (IOException e) {
			Log.e("nakama", "Error: can't find kanji for: " + this.kanji, e);
			Toast.makeText(activity, "Internal Error: can't find kanji information for: " + this.kanji, Toast.LENGTH_LONG).show();
		} finally {
			sd.close();
		}
		this.currentCharacterSvg = parent.getCurrentCharacterSvg();
		
		super.onAttach(activity);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_story, container, false);
		storyEditor = (EditText) view.findViewById(R.id.storyEditor);
		storyEditor.requestFocus();

		db = new StoryDataHelper(getActivity());
		String story = db.getStory(this.character);
		storyEditor.setText(story);
		
		Glyph animGlyph = new Glyph(currentCharacterSvg);
		this.kanim = (AnimatedCurveView)view.findViewById(R.id.kanji_animation);
		this.kanim.setDrawing(animGlyph, AnimatedCurveView.DrawTime.ANIMATED);
		this.kanim.startAnimation(500);
	
		this.kanjiLabel = (TextView)view.findViewById(R.id.bigkanji);
		this.kanjiLabel.setText(Character.toString(this.character));
		this.radicalsCard = view.findViewById(R.id.radicalsCard);

		gridView = (GridView) view.findViewById(R.id.radicalsGrid);
		radicalAdapter = new RadicalAdapter(this.getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, new ArrayList<Kanji>());
		gridView.setAdapter(radicalAdapter);
		
		return view;
	}
	
	private class RadicalAdapter extends ArrayAdapter<Kanji> {
		
		public RadicalAdapter(Context context, int resource, int textViewResourceId, List<Kanji> objects) {
			super(context, resource, textViewResourceId, objects);
		}

		public View getView(int position, View convertView, ViewGroup parentViewgroup){
			Log.i("nakama", "RadicalAdapter.getView " + convertView);
			if(convertView == null){
				convertView = new KanjiWithMeaningView(this.getContext());
			}
			Kanji k = getItem(position);
			((KanjiWithMeaningView)convertView).setKanjiAndMeaning(String.valueOf(k.kanji), k.meanings[0]);
			return convertView;
		}

	}

	public void clearFocus() {
		if(isAdded()){
			storyEditor.clearFocus();
			InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(storyEditor.getWindowToken(), 0);
		}
	}

	public void saveStory() {
		if (storyEditor != null && storyEditor.getText() != null && !storyEditor.getText().toString().equals("")){
			db.recordStory(this.character, storyEditor.getText().toString());
		}
	}

	private class LoadRadicalsFile extends AsyncTask<Void, Void, List<Kanji>> {
        @Override
        protected List<Kanji> doInBackground(Void... v) {
			Thread.currentThread().setName("LoadRadicalsFile");
            DictionarySet dicts = new DictionarySet(parent);
			List<Kanji> retRadicals = null;
			try {

				AssetManager asm = parent.getAssets();
				InputStream kif = asm.open("kradfile");
				try {
					KanjiRadicalFinder krf = new KanjiRadicalFinder(kif);
					retRadicals = krf.findRadicalsAsKanji(dicts.kanjiFinder(), character);
				} finally {
					kif.close();
				}
			} catch (IOException e) {
				Log.e("nakama", "Error: could not read kradfile entries to kanji.", e);
				retRadicals = new ArrayList<Kanji>(0);
			} finally {
				dicts.close();
			}
			
			return retRadicals;
        }
        
        @Override
        protected void onPostExecute(List<Kanji> result) {
       		if(result.size() > 0){
       			for(Kanji k: result){ 
       				Log.i("nakama", "Adding results to radicalAdapter: " + k);
       				radicalAdapter.add(k); 
       			}
       			radicalsCard.setVisibility(View.VISIBLE);
       			radicalAdapter.notifyDataSetChanged();
       		}
        }
    }
}