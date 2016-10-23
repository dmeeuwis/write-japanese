package dmeeuwis.nakama.views.translations;

import android.app.Activity;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import dmeeuwis.Translation;
import dmeeuwis.indexer.KanjiFinder;
import dmeeuwis.kanjimaster.R;
import dmeeuwis.nakama.kanjidraw.Criticism;
import dmeeuwis.nakama.kanjidraw.CurveDrawing;
import dmeeuwis.nakama.kanjidraw.PointDrawing;
import dmeeuwis.nakama.views.AnimatedCurveView;

public class KanjiVocabRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final float engTextSize;

    private final Activity context;
    private final KanjiFinder kanjiFinder;
    private final List<Translation> translations;

    private CurveDrawing knownCharacter;
    private PointDrawing drawnCharacter;

    private String character, meanings;

    private final static int DRAWN_CORRECTLY_HEADER = 0;
    private final static int TRANSLATION_HEADER = 1;
    private final static int CHARACTER_HEADER = 2;
    private final static int MEANINGS_HEADER = 3;

    private List<Integer> headers = new ArrayList<>(3);

    public KanjiVocabRecyclerAdapter(Activity context, KanjiFinder kanjiFinder) {
        super();

        this.drawnCharacter = null;
        this.knownCharacter = null;

        this.context = context;
        this.kanjiFinder = kanjiFinder;
        this.translations = new ArrayList<>();

        Resources r = context.getResources();
        this.engTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, r.getDisplayMetrics());
    }

    public void addKnownAndDrawnHeader(CurveDrawing known, PointDrawing drawn){
        Log.i("nakama", "addKnownAndDrawnHeader called!");
        if(known == null || drawn == null){
            throw new IllegalArgumentException("known and drawn must be non-null");
        }

        this.knownCharacter = known;
        this.drawnCharacter = drawn;
        recalculateHeaders();
        this.notifyDataSetChanged();
    }

    public void addMeaningsHeader(String meanings){
        Log.i("nakama", "addMeaningsHeader called!");
        if(meanings == null){
            throw new IllegalArgumentException("known and drawn must be non-null");
        }
        this.meanings = meanings;
        recalculateHeaders();
        this.notifyDataSetChanged();
    }

    public void addCharacterHeader(String character, CurveDrawing knownCharacter){
        Log.i("nakama", "addCharacterHeader called!");
        if(character == null){
            throw new IllegalArgumentException("known and drawn must be non-null");
        }
        this.character = character;
        this.knownCharacter = knownCharacter;
        recalculateHeaders();
        this.notifyDataSetChanged();
    }

    private void recalculateHeaders(){
        headers.clear();
        if(drawnCharacter != null){ headers.add(DRAWN_CORRECTLY_HEADER); }
        if(character != null){ headers.add(CHARACTER_HEADER); }
        if(meanings != null){ headers.add(MEANINGS_HEADER); }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder h, int p) {
        Log.i("nakama", "onBindViewHolder binding to " + h.getClass().toString());
        if(h instanceof ShowStrokesViewHolder){
            ShowStrokesViewHolder showHolder = (ShowStrokesViewHolder)h;
            showHolder.drawn.setDrawing(drawnCharacter, AnimatedCurveView.DrawTime.STATIC, new ArrayList<Criticism.PaintColourInstructions>(0));
            showHolder.known.setDrawing(knownCharacter, AnimatedCurveView.DrawTime.STATIC, new ArrayList<Criticism.PaintColourInstructions>(0));

        } else if(h instanceof TranslationViewHolder) {
            int tIndex = posToTranslationIndex(p);
            TranslationViewHolder holder = (TranslationViewHolder) h;
            Translation t = this.translations.get(tIndex);
            holder.bind(t, this.kanjiFinder);

        } else if(h instanceof ShowMeaningsViewHolder){
            ShowMeaningsViewHolder holder = (ShowMeaningsViewHolder)h;
            holder.meanings.setText(meanings);

        } else if(h instanceof ShowCharacterInfoViewHolder){
            ShowCharacterInfoViewHolder holder = (ShowCharacterInfoViewHolder) h;
            holder.anim.setDrawing(knownCharacter.toDrawing(), AnimatedCurveView.DrawTime.ANIMATED,
                    new ArrayList<Criticism.PaintColourInstructions>(0));
            holder.anim.startAnimation(500);
            holder.bigKanji.setText(character);

        }
    }

    @Override
    public int getItemCount() {
        Log.i("nakama", "Custom itemCount is " + translationIndex(translations.size()));
        return translationIndex(translations.size());
    }

    public void add(Translation t){
        Log.i("nakama", "Adapter adding translation " + t.toKanjiString());
        this.translations.add(t);
        this.notifyItemChanged(translationIndex(Math.max(0, this.translations.size()-1)));
    }

    public void clear(){
        this.translations.clear();
        this.notifyDataSetChanged();
    }

    private int posToTranslationIndex(int pos){
        return pos -
                (meanings != null ? 1 : 0) -
                (character != null ? 1 : 0) -
                (drawnCharacter != null ? 1 : 0);
    }

    private int translationIndex(int translationI){
        return translationI +
                (meanings != null ? 1 : 0) +
                (character != null ? 1 : 0) +
                (drawnCharacter != null ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        Log.i("nakama", "getItemViewType headers count is " + headers.size());

        if(position < headers.size()){
            Log.i("nakama", "getItemViewType for " + position + " returning " + headers.get(position));
            return headers.get(position);
        }

        Log.i("nakama", "getItemViewType for " + position + " returning " + TRANSLATION_HEADER);
        return TRANSLATION_HEADER;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.i("nakama", "onCreatveViewHolder for type " + viewType);
        LayoutInflater inflater = this.context.getLayoutInflater();
        if(viewType == DRAWN_CORRECTLY_HEADER){
            View view = inflater.inflate(R.layout.translation_correct_drawn_row, parent, false);
            return new ShowStrokesViewHolder(view);
         } else if (viewType == TRANSLATION_HEADER){
            View view = inflater.inflate(R.layout.translation_slide, parent, false);
            return new TranslationViewHolder(view, engTextSize);
        } else if (viewType == MEANINGS_HEADER){
            View view = inflater.inflate(R.layout.translation_meanings_row, parent, false);
            return new ShowMeaningsViewHolder(view);
        } else if (viewType == CHARACTER_HEADER){
            View view = inflater.inflate(R.layout.translation_character_info_row, parent, false);
            return new ShowCharacterInfoViewHolder(view);
        } else {
            return null;
        }
    }
}