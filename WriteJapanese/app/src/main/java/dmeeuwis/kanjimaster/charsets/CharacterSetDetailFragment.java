package dmeeuwis.kanjimaster.charsets;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import dmeeuwis.kanjimaster.R;
import dmeeuwis.nakama.data.CharacterSets;
import dmeeuwis.nakama.data.CharacterStudySet;
import dmeeuwis.nakama.data.CustomCharacterSetDataHelper;
import dmeeuwis.nakama.primary.Iid;
import dmeeuwis.nakama.views.AutofitRecyclerView;
import dmeeuwis.nakama.views.LockCheckerInAppBillingService;

/**
 * A fragment representing a single CharacterSet detail screen.
 * This fragment is either contained in a {@link CharacterSetListActivity}
 * in two-pane mode (on tablets) or a {@link CharacterSetDetailActivity}
 * on handsets.
 */
public class CharacterSetDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String CHARSET_ID = "item_id";

    private static final char HEADER_CHAR = ' ';
    private static final char METADATA_CHAR = 'X';

    private CharacterStudySet studySet;
    private AutofitRecyclerView grid;

    private TextView nameEdit, descriptionEdit;

    private static final int SELECT_COLOUR = 0xffbbdefb;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CharacterSetDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(CHARSET_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            String name = getArguments().getString(CHARSET_ID);

            // for small devices, this fragment gets loaded into a otherwise-empty activity, and "create"
            // is passed as id. On large layouts, this fragment is beside the list, and setCharacterStudySet is called instead
            if(name.equals("create")){
                studySet = CharacterSets.createCustom(Iid.get(getActivity().getApplicationContext()));
            } else {
                studySet = CharacterSets.fromName(getActivity(), name, new LockCheckerInAppBillingService(getActivity()), Iid.get(getActivity().getApplicationContext()));
            }
        }
    }

    public void save(){
        String editName = nameEdit.getText().toString();
        String editDesc = descriptionEdit.getText().toString();
        String characters = ((CharacterGridAdapter)grid.getAdapter()).getCharacters();
        new CustomCharacterSetDataHelper(getActivity()).recordEdit(studySet.pathPrefix, editName, editDesc, characters);
    }

    private void setName(String update){
        studySet.name = update;
    }

    private void setDescription(String update){
        studySet.description = update;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.characterset_detail, container, false);

        if (studySet != null) {

            grid = (AutofitRecyclerView) rootView.findViewById(R.id.charset_detail_grid);
            grid.setAdapter(new CharacterGridAdapter(
                    CharacterSets.all(
                            new LockCheckerInAppBillingService(getActivity()),
                            Iid.get(getActivity().getApplicationContext()))));
        }

        return rootView;
    }

    public void setCharacterStudySet(CharacterStudySet characterStudySet) {
        this.studySet = characterStudySet;
        grid.setAdapter(new CharacterGridAdapter(
                CharacterSets.all(
                        new LockCheckerInAppBillingService(getActivity()),
                        Iid.get(getActivity().getApplicationContext()))));
    }

    public static void makeColorAnimater(final View view, int color1, int color2){
        ValueAnimator anim = new ValueAnimator();
        anim.setIntValues(color1, color2);
        anim.setEvaluator(new ArgbEvaluator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                view.setBackgroundColor((Integer)valueAnimator.getAnimatedValue());
            }
        });

        anim.setDuration(100);
        anim.start();
    }

    public class CharacterGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final int CHARACTER_TYPE = 0;
        private final int HEADER_TYPE = 1;
        private final int METADATA_TYPE = 2;

        private final String asLongString;
        private final BitSet selected;
        public final Map<Integer, String> headers;


        private class MetadataViewHolder extends RecyclerView.ViewHolder {
            final EditText nameInput;
            final EditText descInput;

            MetadataViewHolder(View itemView) {
                super(itemView);
                nameInput = ((EditText) itemView.findViewById(R.id.characterset_name));
                descInput = ((EditText) itemView.findViewById(R.id.characterset_detail));
            }
        }

        private class CharacterViewHolder extends RecyclerView.ViewHolder {
            public TextView text;

            CharacterViewHolder(View itemView) {
                super(itemView);
                this.text = (TextView)itemView.findViewById(R.id.character_grid_text);


                this.text.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                       int currentPosition = getAdapterPosition();
                       if(selected.get(currentPosition)){
                           selected.set(currentPosition, false);
                           makeColorAnimater(text, SELECT_COLOUR, Color.WHITE);
                       } else {
                           selected.set(currentPosition, true);
                           makeColorAnimater(text, Color.WHITE, SELECT_COLOUR);
                       }
                    }
                });
            }
        }

        private class HeaderViewHolder extends RecyclerView.ViewHolder {
            public TextView text, allLink;
            public HeaderViewHolder(View itemView) {
                super(itemView);
                this.text = (TextView)itemView.findViewById(R.id.character_grid_header);
                this.allLink = (TextView)itemView.findViewById(R.id.character_set_select_all_link);
                this.allLink.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int i = getAdapterPosition();
                        Log.i("nakama", "Header type onClick! " + i);
                        i++;
                        while(i < asLongString.length() && asLongString.charAt(i) != HEADER_CHAR){
                            Log.i("nakama", "Selecting " + i);
                            if(!selected.get(i)) {
                                selected.set(i, true);
                                notifyItemChanged(i);
                            }
                            i++;
                        }
                    }
                });
            }
        }

        CharacterGridAdapter(CharacterStudySet[] sets){
            headers = new HashMap<>();
            headers.put(0, "Meta");
            StringBuilder sb = new StringBuilder();
            sb.append(METADATA_CHAR);             // represents edit metadata header
            for(CharacterStudySet s: sets){
                headers.put(sb.length(), s.name);
                sb.append(" "); // header
                sb.append(s.charactersAsString());
            }
            this.asLongString = sb.toString();
            this.selected = new BitSet(asLongString.length());

            Log.i("nakama", "CharacterGridAdapter: studySet = " + studySet);
            if(studySet != null){
                for(char c: studySet.allCharactersSet){
                    Log.i("nakama", "Setting as selected: " + c + " = " + asLongString.indexOf(c));
                    this.selected.set(asLongString.indexOf(c));
                }
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if(viewType == CHARACTER_TYPE) {
                View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_character_grid_layout, parent, false);
                CharacterViewHolder vh = new CharacterViewHolder(mView);
                return vh;
            } else if(viewType == METADATA_TYPE) {
                View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_character_grid_metadata, parent, false);
                MetadataViewHolder vh = new MetadataViewHolder(mView);

                nameEdit = (TextView) mView.findViewById(R.id.characterset_name);
                nameEdit.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        Log.i("nakama", "OnEditorAction name: " + charSequence);
                        studySet.name = charSequence.toString();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {

                    }
                });

                descriptionEdit = (TextView) mView.findViewById(R.id.characterset_detail);
                descriptionEdit.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        Log.i("nakama", "OnEditorAction description: " + charSequence);
                        studySet.description = charSequence.toString();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {

                    }
                });

                return vh;
            } else {
                View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_character_header_layout, parent, false);
                HeaderViewHolder vh = new HeaderViewHolder(mView);
                return vh;
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if(asLongString.charAt(position) == METADATA_CHAR) {
                MetadataViewHolder ch = (MetadataViewHolder) holder;
                ch.nameInput.setText(studySet.name);
                ch.descInput.setText(studySet.description);

            } else if(asLongString.charAt(position) == HEADER_CHAR){
                HeaderViewHolder ch = (HeaderViewHolder) holder;
                ch.text.setText(headers.get(position));
            } else {
                CharacterViewHolder ch = (CharacterViewHolder) holder;
                ch.text.setText(String.valueOf(asLongString.charAt(position)));

                if(selected.get(position)){
                    ch.text.setBackgroundColor(SELECT_COLOUR);
                } else {
                    ch.text.setBackgroundColor(Color.WHITE);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            switch (asLongString.charAt(position)){
                case HEADER_CHAR: return HEADER_TYPE;
                case METADATA_CHAR: return METADATA_TYPE;
                default:  return CHARACTER_TYPE;
            }
        }

        @Override
        public int getItemCount() {
            return asLongString.length();
        }

        public String getCharacters(){
            StringBuilder s = new StringBuilder();
            for(int i = 0; i < selected.length(); i++){
                if(selected.get(i)){
                    s.append(asLongString.charAt(i));
                }
            }
            return s.toString();
        }
    }
}
