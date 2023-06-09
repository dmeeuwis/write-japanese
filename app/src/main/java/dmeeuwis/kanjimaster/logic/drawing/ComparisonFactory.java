package dmeeuwis.kanjimaster.logic.drawing;

import dmeeuwis.kanjimaster.logic.data.AssetFinder;
import dmeeuwis.kanjimaster.logic.data.Settings;
import dmeeuwis.kanjimaster.logic.data.SettingsFactory;
import dmeeuwis.kanjimaster.logic.data.UncaughtExceptionLogger;

public class ComparisonFactory {
    public static Comparator getUsersComparator(AssetFinder assetFinder){
        try {
            if (SettingsFactory.get().getStrictness() == Settings.Strictness.CASUAL) {
                return new SimpleDrawingComparator(assetFinder, SimpleDrawingComparator.StrokeOrder.DISCOUNT);
            } else if (SettingsFactory.get().getStrictness() == Settings.Strictness.CASUAL_ORDERED) {
                return new SimpleDrawingComparator(assetFinder, SimpleDrawingComparator.StrokeOrder.COUNT);
            } else if (SettingsFactory.get().getStrictness() == Settings.Strictness.STRICT) {
                return new DrawingComparator(assetFinder);
            } else {
                return new SimpleDrawingComparator(assetFinder, SimpleDrawingComparator.StrokeOrder.DISCOUNT);
            }
        } catch(Throwable t){
            UncaughtExceptionLogger.backgroundLogError("HACK: exception while getting ComparisonFactory! Returning default instead.", t);
            return new SimpleDrawingComparator(assetFinder, SimpleDrawingComparator.StrokeOrder.DISCOUNT);
        }
    }
}