package dmeeuwis.nakama.data;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;

import java.text.DateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dmeeuwis.nakama.LockChecker;
import dmeeuwis.nakama.kanjidraw.PointDrawing;
import dmeeuwis.nakama.primary.KanjiMasterActivity;
import dmeeuwis.util.Util;

/**
 * Holds a set of characters to study, and all current user study progress on those characters.
 */
public class CharacterStudySet implements Iterable<Character> {

    public enum LockLevel {NULL_LOCK, LOCKED, UNLOCKABLE, UNLOCKED}

    final public Set<Character> freeCharactersSet;
    final public Set<Character> allCharactersSet;
    public String name, shortName, description;

    final private CharacterProgressDataHelper dbHelper;
    final public boolean systemSet;
    final private LockChecker LockChecker;
    private ProgressTracker tracker;
    final UUID iid;

    private boolean shuffling = false;
    private Character currentChar;
    private ProgressTracker.StudyType reviewing = ProgressTracker.StudyType.NEW_CHAR;
    private LockLevel locked;

    public final String pathPrefix;

    private GregorianCalendar studyGoal, goalStarted;

    public static class SetProgress {
        public final int passed;
        public final int reviewing;
        public final int failing;
        public final int unknown;

        SetProgress(int passed, int reviewing, int failing, int unknown) {
            this.passed = passed;
            this.reviewing = reviewing;
            this.failing = failing;
            this.unknown = unknown;
        }
    }

    private static int daysDifference(GregorianCalendar a, GregorianCalendar b) {
        return (int) Math.ceil(Math.abs(a.getTimeInMillis() - b.getTimeInMillis()) / (1000.0 * 60 * 60 * 24));
    }

    public static class GoalProgress {
        public final GregorianCalendar goal, goalStarted;
        public final int passed, remaining, daysLeft;
        public final int neededPerDay, scheduledPerDay;

        public GoalProgress(GregorianCalendar goalStarted, GregorianCalendar goal, SetProgress s, GregorianCalendar today) {
            this.goalStarted = goalStarted;
            this.remaining = s.failing + s.reviewing + s.unknown;
            this.passed = s.passed;
            this.goal = goal;

            if (this.goal.before(today)) {
                daysLeft = 0;
            } else {
                daysLeft = Math.max(1, daysDifference(goal, today));
            }
            this.neededPerDay = (int) Math.ceil(1.0f * remaining / daysLeft);
            this.scheduledPerDay = (int) Math.ceil((1.0 * s.failing + s.unknown + s.reviewing + s.passed) / daysLeft);

            DateFormat df = DateFormat.getDateInstance();
            Log.i("nakama", "Goal Calcs; Start: " + df.format(goalStarted.getTime()) + ", goal: " + df.format(goal.getTime()) + "; remaining: " + remaining + "; daysLeft: " + daysLeft + "; remaining: " + remaining);
        }
    }

    public int length() {
        return this.allCharactersSet.size();
    }

    public String toString() {
        return String.format("%s (%d)", this.name, this.allCharactersSet.size());
    }


    public CharacterStudySet(String name, String shortName, String description, String pathPrefix, LockLevel locked, String allCharacters, String freeCharacters, LockChecker LockChecker, UUID iid, boolean systemSet, Context context) {
        this(name, shortName, description, pathPrefix, locked, allCharacters, freeCharacters, LockChecker, iid, systemSet, new CharacterProgressDataHelper(context, iid));
    }

    public CharacterStudySet(String name, String shortName, String description, String pathPrefix, LockLevel locked, String allCharacters, String freeCharacters, LockChecker LockChecker, UUID iid, boolean systemSet, CharacterProgressDataHelper db) {
        this.dbHelper = db;
        this.name = name;
        this.shortName = shortName;
        this.description = description;
        this.systemSet = systemSet;
        this.locked = locked;
        this.iid = iid;

        this.freeCharactersSet = new LinkedHashSet<>(Util.stringToCharList(freeCharacters));
        this.allCharactersSet = new LinkedHashSet<>(Util.stringToCharList(allCharacters));

        this.pathPrefix = pathPrefix;
        this.LockChecker = LockChecker;

        CharacterProgressDataHelper.ProgressionSettings p = dbHelper.getProgressionSettings();
        tracker = new ProgressTracker(allCharactersSet, p.advanceIncorrect, p.advanceReviewing);
    }


    public boolean hasStudyGoal() {
        return this.studyGoal != null;
    }

    public void setStudyGoal(GregorianCalendar g) {
        this.studyGoal = g;
        this.goalStarted = new GregorianCalendar();
    }

    public void clearStudyGoal() {
        this.studyGoal = null;
        this.goalStarted = null;
    }

    public GoalProgress getGoalProgress() {
        if (this.studyGoal == null) {
            return null;
        }
        SetProgress s = this.getProgress();
        return new GoalProgress(this.goalStarted, this.studyGoal, s, new GregorianCalendar());
    }


    public SetProgress getProgress() {
        return this.tracker.calculateProgress();
    }

    public boolean locked() {
        if (LockChecker == null) {
            return false;
        }
        boolean globalLock = LockChecker.getPurchaseStatus() == LockLevel.LOCKED;
        boolean localLock = this.locked == LockLevel.LOCKED;
        // Log.d("nakama", "CharacterStudySet: globalLock: " + globalLock + "; localLock: " + localLock + " => " + (globalLock && localLock));
        return globalLock && localLock;
    }

    public Set<Character> availableCharactersSet() {
        return locked() ? freeCharactersSet : allCharactersSet;
    }

    public String charactersAsString() {
        return Util.join("", allCharactersSet);
    }

    public boolean passedAllCharacters() {
        return this.tracker.passedAllCharacters(availableCharactersSet());
    }

    public Character currentCharacter() {
        return this.currentChar;
    }

    @Override
    public Iterator<Character> iterator() {
        return (availableCharactersSet()).iterator();
    }

    public ProgressTracker.SRSEntry markCurrent(PointDrawing d, boolean pass) {
        Character c = currentCharacter();
        ProgressTracker.SRSEntry enteredSRS = null;
        try {
            if (pass) {
                enteredSRS = this.tracker.markSuccess(c, LocalDateTime.now());
            } else {
                this.tracker.markFailure(c);
            }
            dbHelper.recordPractice(pathPrefix, currentCharacter().toString(), d, pass ? 100 : -100);
        } catch (Throwable t) {
            Log.e("nakama", "Error when marking character " + c + " from character set " + Util.join(", ", this.allCharactersSet) + "; tracker is " + tracker);
            throw new RuntimeException(t);
        }
        return enteredSRS;
    }

    public void markCurrentAsUnknown(Context context) {
        this.tracker.markFailure(currentCharacter());
        nextCharacter();
    }

    public void setShuffle(boolean isShuffle) {
        this.shuffling = isShuffle;
    }

    public boolean isShuffling() {
        return this.shuffling;
    }

    public void progressReset() {
        this.tracker.progressReset();
        dbHelper.clearProgress(pathPrefix);
    }

    public void skipTo(Character character) {
        if (availableCharactersSet().contains(character)) {
            this.currentChar = character;
            this.reviewing = tracker.isReviewing(character);
        }
    }

    public void nextCharacter() {
        CharacterProgressDataHelper.ProgressionSettings p = dbHelper.getProgressionSettings();
        Pair<Character, ProgressTracker.StudyType> i = tracker.nextCharacter(allCharactersSet, this.currentChar, this.availableCharactersSet(), this.shuffling,
                p.introIncorrect, p.introReviewing);

        this.currentChar = i.first;
        this.reviewing = i.second;
    }

    public void save() {
        dbHelper.recordGoals(pathPrefix, goalStarted, studyGoal);
    }

    public void load() {
        CharacterProgressDataHelper.ProgressionSettings p = dbHelper.getProgressionSettings();
        Pair<GregorianCalendar, GregorianCalendar> goals = dbHelper.getExistingGoals(pathPrefix);
        if (goals != null) {
            this.goalStarted = goals.first;
            this.studyGoal = goals.second;
        }

        tracker = new ProgressTracker(allCharactersSet, p.advanceIncorrect, p.advanceReviewing);
        dbHelper.getRecordSheetForCharset(this.availableCharactersSet(), tracker);
    }

    public ProgressTracker.StudyType isReviewing() {
        return reviewing;
    }


    public Map<Character, ProgressTracker.Progress> getRecordSheet() {
        return this.tracker.getAllScores();
    }

    public void debugSrsQueuePrint(Context ctx) {
        tracker.debugSrsQueuePrint(ctx);
    }

    public Map<LocalDate, List<Character>> getSrsSchedule(){
        return tracker.getSrsSchedule();
    }

}
