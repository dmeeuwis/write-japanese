package dmeeuwis.nakama.primary;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dmeeuwis.kanjimaster.R;

public class ProgressSettingsDialog extends DialogFragment {

    public final static String SHARED_PREFS_KEY_INTRO_INCORRECT = "introduceIncorrect";
    public final static String SHARED_PREFS_KEY_INTRO_REVIEWING = "introduceReviewing";
    public final static String SHARED_PREFS_KEY_ADV_INCORRECT = "advanceIncorrect";
    public final static String SHARED_PREFS_KEY_ADV_REVIEWING = "advanceReviewing";

    Spinner introduceIncorrect, introduceReviewing, advanceIncorrect, advanceReviewing;

    static Pattern MATCH_NUMBER = Pattern.compile("(\\d+)");

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        this.setRetainInstance(true);

        final FrameLayout frameView = new FrameLayout(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder .setTitle("Set Progression Options")
                .setView(frameView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Integer introIncorrect = pullNumberFromString(introduceIncorrect.getSelectedItem());
                        Integer introReviewing = pullNumberFromString(introduceReviewing.getSelectedItem());
                        Integer advIncorrect = pullNumberFromString(advanceIncorrect.getSelectedItem());
                        Integer advReviewing = pullNumberFromString(advanceReviewing.getSelectedItem());

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
                        SharedPreferences.Editor ed = prefs.edit();
                        ed.putInt(SHARED_PREFS_KEY_INTRO_INCORRECT, introIncorrect);
                        ed.putInt(SHARED_PREFS_KEY_INTRO_REVIEWING, introReviewing);
                        ed.putInt(SHARED_PREFS_KEY_ADV_INCORRECT, advIncorrect);
                        ed.putInt(SHARED_PREFS_KEY_ADV_REVIEWING, advReviewing);
                        ed.apply();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog d = builder.create();

        LayoutInflater inflater = d.getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.fragment_character_progression, frameView);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        int introIncorrect = prefs.getInt(SHARED_PREFS_KEY_INTRO_INCORRECT, 5);
        int introReviewing = prefs.getInt(SHARED_PREFS_KEY_INTRO_REVIEWING, 10);
        int advIncorrect = prefs.getInt(SHARED_PREFS_KEY_ADV_INCORRECT, 1);
        int advReviewing = prefs.getInt(SHARED_PREFS_KEY_ADV_REVIEWING, 2);

        introduceIncorrect = (Spinner) dialogLayout.findViewById(R.id.when_to_introduce_incorrect_spinner);
        introduceIncorrect.setAdapter(ArrayAdapter.createFromResource(getActivity(), R.array.when_to_introduce_incorrect_spinner_values, android.R.layout.simple_spinner_dropdown_item));
        String[] introIncorrectSelections = getActivity().getResources().getStringArray(R.array.when_to_introduce_incorrect_spinner_values);
        setSpinnerForIntValue(introIncorrectSelections, introduceIncorrect, introIncorrect);

        introduceReviewing = (Spinner) dialogLayout.findViewById(R.id.when_to_introduce_reviewing_spinner);
        introduceReviewing.setAdapter(ArrayAdapter.createFromResource(getActivity(), R.array.when_to_introduce_reviewing_spinner_values, android.R.layout.simple_spinner_dropdown_item));
        String[] introReviewingSelections = getActivity().getResources().getStringArray(R.array.when_to_introduce_reviewing_spinner_values);
        setSpinnerForIntValue(introReviewingSelections, introduceReviewing, introReviewing);

        advanceIncorrect = (Spinner) dialogLayout.findViewById(R.id.when_to_advance_from_incorrect_spinner);
        advanceIncorrect.setAdapter(ArrayAdapter.createFromResource(getActivity(), R.array.when_to_advance_from_incorrect, android.R.layout.simple_spinner_dropdown_item));
        String[] advIncorrectSelections = getActivity().getResources().getStringArray(R.array.when_to_advance_from_incorrect);
        setSpinnerForIntValue(advIncorrectSelections, advanceIncorrect, advIncorrect);

        advanceReviewing = (Spinner) dialogLayout.findViewById(R.id.when_to_advance_from_reviewing_spinner);
        advanceReviewing.setAdapter(ArrayAdapter.createFromResource(getActivity(), R.array.when_to_advance_from_reviewing, android.R.layout.simple_spinner_dropdown_item));
        String[] advReviewingSelections = getActivity().getResources().getStringArray(R.array.when_to_advance_from_reviewing);
        setSpinnerForIntValue(advReviewingSelections, advanceReviewing, advReviewing);

        // Create the AlertDialog object and return it
        return d;
    }

    private static void setSpinnerForIntValue(String[] selections, Spinner spinner, int value){
        for(int i = 0; i < selections.length; i++){
            Integer selValue = pullNumberFromString(selections[i]);
            if(selValue != null && selValue == value){
                spinner.setSelection(i);
                return;
            }
        }
    }

    private static Integer pullNumberFromString(Object in){
        Matcher m = MATCH_NUMBER.matcher(in.toString());
        if(m.find()) {
            return Integer.parseInt(m.group(1));
        } else {
            return null;
        }
    }
}