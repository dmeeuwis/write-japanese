package dmeeuwis.nakama.data;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

import dmeeuwis.Kana;
import dmeeuwis.Kanji;
import dmeeuwis.Translation;
import dmeeuwis.util.Util;

public class ClueExtractor {

    private final DictionarySet set;

    public ClueExtractor(DictionarySet set) {
        this.set = set;
    }

    public String[] readingClues(Character currentCharacter) {
        // clue from readings
        if (Kana.isKanji(currentCharacter)) {
            try {
                Kanji k = set.kanjiFinder().find(currentCharacter);
                String[] readings = Util.concat(k.onyomi, k.kunyomi);
                return readings;
            } catch (IOException e) {
                // fall through
            }
        }
        return null;
    }

    public String[] meaningsClues(Character currentCharacter) {
        if(Kana.isKana(currentCharacter)){
            return new String[] { Kana.kana2Romaji(String.valueOf(currentCharacter)) };
        }
        try {
            Kanji k = set.kanjiFinder().find(currentCharacter);
            return k.meanings;
        } catch (IOException e) {
            return null;
        }
    }

    public DictionarySet getDictionarySet(){
        return set;
    }

    public String meaningsInstructionsText(Character character, int currentMeaningsClueIndex) {
        if(Kana.isKatakana(character)) {
            return "Draw the katakana";
        }
        if(Kana.isHiragana(character)) {
            return "Draw the hiragana";
        }

        return currentMeaningsClueIndex == 0 ?
                    "Draw the kanji with meaning" :
                    "which can also mean";
    }

    public CharSequence readingsInstructionsText(String[] readings, int i) {
        String reading = readings[i];
        String readingType = Kana.hasHiragana(reading) ? "kunyomi" : "onyomi";
        return i == 0 ?
                "Draw the character with " + readingType :
                "and " + readingType;
    }

    private final int MAX_TRANSLATIONS = 5;
    public Translation translationsClue(Character currentCharacter, int index) {
        index = index % MAX_TRANSLATIONS;
        try {
            List<Translation> t = set.querier.singleCharacterSearch(1, index, currentCharacter);
            if(t.size() == 0){
                return null;
            }
            return t.get(0);
        } catch (IOException|XmlPullParserException e) {
            return null;
        }
    }

    public String translationsInstructionsText(int i){
        i = i % MAX_TRANSLATIONS;
        if(i == 0) {
            return "Write the kanji replaced by ?";
        }
        return "also used in";
    }
}