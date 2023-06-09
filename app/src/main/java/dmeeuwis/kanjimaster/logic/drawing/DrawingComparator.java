package dmeeuwis.kanjimaster.logic.drawing;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import dmeeuwis.kanjimaster.core.Kana;
import dmeeuwis.kanjimaster.logic.data.AssetFinder;
import dmeeuwis.kanjimaster.logic.data.Point;
import dmeeuwis.kanjimaster.logic.data.Rect;
import dmeeuwis.kanjimaster.logic.drawing.PathCalculator.Intersection;
import dmeeuwis.kanjimaster.core.util.Util;

public class DrawingComparator implements Comparator {
	
	public enum StrokeCompareFailure { ABOVE_FAILURE, NOT_ABOVE_FAILURE, DISTANCE_TRAVELLED, START_POINT_DIFFERENCE, END_POINT_DIFFERENCE, START_DIRECTION_DIFFERENCE, END_DIRECTION_DIFFERENCE, BACKWARDS, TOO_MANY_SHARP_CURVES, TOO_FEW_SHARP_CURVES }
	public enum OverallFailure { EXTRA_STROKES, MISSING_STROKES, MISSING_INTERSECTION, WRONG_STROKE_ORDER }

	private float FAIL_POINT_START_DISTANCE;
	private float FAIL_POINT_END_DISTANCE;
	private float CIRCLE_DETECTION_DISTANCE;
	static private final double STROKE_DIRECTION_LIMIT_RADIANS = Math.PI / 2;
	static private final int PERCENTAGE_DISTANCE_DIFF_LIMIT = 100;

	private static final boolean DEBUG = false;

	private final AssetFinder assetFinder;

	private char target;
	private PointDrawing known;
	private PointDrawing drawn;
	private int drawingAreaMaxDim;

	private boolean[][] drawnAboveMatrix;
	private boolean[][] knownAboveMatrix;

	DrawingComparator(AssetFinder assetFinder) {
		this.assetFinder = assetFinder;
	}

	public Criticism compare(char target, PointDrawing challenger, CurveDrawing known) throws IOException {
		return compare(target, challenger, known, Recursion.ALLOW);
	}

	private static Rect findBounds(char target, Drawing d){
		if(target == 'ニ'){
			return d.findBounds();
		}
		return d.findBoundingBox();
	}

	public Criticism compare(char target, PointDrawing challenger, CurveDrawing known, Recursion recursion) throws IOException {
		this.target = target;
		this.drawn = challenger.cutOffEdges();// scaleToBox(nBounds);

		Rect drawnBox = findBounds(target, this.drawn);
		
		PointDrawing cutOffKnown = known.pointPointDrawing.cutOffEdges();
		this.known = cutOffKnown.scaleToBox(drawnBox);

		Rect nBounds = findBounds(target, this.known);
		this.drawingAreaMaxDim = Math.max(nBounds.width(), nBounds.height());

		if(DEBUG) {
			Log.d("nakama-calc", "========================================");
			Log.d("nakama-calc", "Calculating drawn above matrix");
		}
		this.drawnAboveMatrix = calculateAboveMatrix(this.drawn);

		if(DEBUG) {
			Log.d("nakama-calc", "========================================");
			Log.d("nakama-calc", "Calculating known above matrix");
		}

		this.knownAboveMatrix = calculateAboveMatrix(this.known);

		if(DEBUG) {
			Log.i("nakama", "Drawn above matrix\n" + Util.printMatrix(this.drawnAboveMatrix));
			Log.i("nakama", "Known above matrix\n" + Util.printMatrix(this.knownAboveMatrix));
		}

		this.FAIL_POINT_START_DISTANCE = (float)(drawingAreaMaxDim * 0.40);
		this.FAIL_POINT_END_DISTANCE = (float)(drawingAreaMaxDim * 0.40);
		this.CIRCLE_DETECTION_DISTANCE = (float)(drawingAreaMaxDim * 0.10);
		
		if(DEBUG) Log.d("nakama", "PathComparator.new: drawingAreaWidth: " + drawingAreaMaxDim);
		if(DEBUG) Log.d("nakama", "PathComparator.new: scaled drawn to " + findBounds(target, this.drawn));
		if(DEBUG) Log.d("nakama", "PathComparator.new: circle detection distance " + this.CIRCLE_DETECTION_DISTANCE);
		if(DEBUG) Log.d("nakama", "PathComparator.new: known cut scaled bounds: " + findBounds(target, this.known));

		return compare(recursion);
	}

	private int[] findExtraStrokes(int knownCount, int drawnCount, List<StrokeResult> best){
        int extra = drawnCount - knownCount;
        if(extra <= 0){
            // only try to colour if we're sure we can identify the extra strokes
            return null;
        }

        // refactor this to streams when possible
        List<Integer> drewWell = new ArrayList<>(knownCount);
        for(StrokeResult r: best){
            if(r.score == 0){
                drewWell.add(r.drawnStrokeIndex);
            }
        }

        // only colour extra strokes if every stoke you drew, you drew well
        if(drewWell.size() == knownCount){
            return Util.missingInts(drawnCount, drewWell);
        } else {
            return null;
        }
    }


	private int[] findMissingStrokes(int knownCount, int drawnCount, List<StrokeResult> best){
		int missing = knownCount - drawnCount;
		if(missing <= 0){
			// only try to colour if we're sure we can identify the missing strokes
			return null;
		}

        // refactor this to streams when possible
		List<Integer> drewWell = new ArrayList<>(drawnCount);
		for(StrokeResult r: best){
			if(r.score == 0){
				drewWell.add(r.knownStrokeIndex);
			}
		}

		// only colour missing strokes if every stoke you drew, you drew well
		if(drewWell.size() == drawnCount){
			return Util.missingInts(knownCount, drewWell);
		} else {
			return null;
		}
	}
	
	
	private enum Recursion { ALLOW, DISALLOW }
	private Criticism compare(Recursion allowRecursion) throws IOException {
		Criticism c = new Criticism();
		List<OverallFailure> overallFailures = new ArrayList<OverallFailure>();
		
		if(this.drawn.strokeCount() < this.known.strokeCount())
			overallFailures.add(OverallFailure.MISSING_STROKES);
		if(this.drawn.strokeCount() > this.known.strokeCount())
			overallFailures.add(OverallFailure.EXTRA_STROKES);
		
		StrokeCriticism[][] criticismMatrix = new StrokeCriticism[known.strokeCount()][drawn.strokeCount()];
		int[][] scoreMatrix = new int[known.strokeCount()][drawn.strokeCount()];


        boolean correctDiagonal = known.strokeCount() == drawn.strokeCount();
        if(correctDiagonal){ // possibly
           for(int i = 0; i < known.strokeCount(); i++) {
               StrokeCriticism r = compareStroke(i, i);
               criticismMatrix[i][i] = r;
               scoreMatrix[i][i] = r.cost;
               correctDiagonal = r.cost == 0 && correctDiagonal;
           }
        }

        if(correctDiagonal){
            if(DEBUG) Log.d("nakama", "Correct diagonal detected! Going home early.");
            CommonlyConfusedChecker.checkEasilyConfusedCharacters(target, drawn, c);
            return c;
        }

		// calculate score and criticism matrix
		for(int known_i = 0; known_i < known.strokeCount(); known_i++){
			for(int drawn_i = 0; drawn_i < drawn.strokeCount(); drawn_i++){
				if(known_i == drawn_i) {
					continue; // calculated in above diagonal block
				}
				StrokeCriticism result = compareStroke(known_i, drawn_i);
				if(DEBUG) Log.d("nakama", "Compared known " + known_i + " to drawn " + drawn_i + ": " + result.cost + "; " + result.message);
				criticismMatrix[known_i][drawn_i] = result;
				scoreMatrix[known_i][drawn_i] = result.cost;
			}
		}

		if(DEBUG) Log.d("nakama", "Score Matrix (y-axis=known, x-axis=drawn)\n======================" + Util.printMatrix(scoreMatrix) + "====================");
	
		if(DEBUG) Log.d("nakama", "Scanning for known intersects.");
		List<Intersection> knownIntersects = this.known.findIntersections();
		if(DEBUG) Log.d("nakama", "This kanji should have " + knownIntersects.size() + " intersects:\n" + Util.join("\n", knownIntersects) + "\n");
		
		if(DEBUG) Log.d("nakama", "Scanning for drawn intersects.");
		List<Intersection> drawnIntersects = this.drawn.findIntersections();
		if(DEBUG) Log.d("nakama", "This drawn kanji has " + drawnIntersects.size() + " intersects:\n" + Util.join("\n", drawnIntersects + "\n"));

		int intersectDistanceLimit = (int)(drawingAreaMaxDim * 0.3);
		if(DEBUG) Log.d("nakama", "Using max intersect distance of " + intersectDistanceLimit);
		outer: for(Intersection knownInt: knownIntersects){
			for(Intersection drawnInt: drawnIntersects){
				double distance = PathCalculator.distance(knownInt.intersectPoint, drawnInt.intersectPoint);
				if(drawnInt.strokesMatch(drawnInt) && distance <= intersectDistanceLimit){
					continue outer;
				} else {
					if(DEBUG) Log.d("nakama", "Saw distance between intersects " + drawnInt + " and known int " + knownInt.intersectPoint + " as " + distance);
				}
			}
			c.add("Your " + Util.adjectify(knownInt.firstPathIndex, known.strokeCount()) + " and " + Util.adjectify(knownInt.secondPathIndex, known.strokeCount()) + " strokes should meet.",
					Criticism.correctColours(knownInt.firstPathIndex, knownInt.secondPathIndex),
					Criticism.incorrectColours(knownInt.firstPathIndex, knownInt.secondPathIndex));
		}

		// find best set of strokes
		List<StrokeResult> bestStrokes = findBestPairings(scoreMatrix);
		{
			Set<Integer> rearrangedDrawnStrokes = new HashSet<>(bestStrokes.size());
			List<StrokeResult> misorderedStrokes = new ArrayList<>(bestStrokes.size());

			for (StrokeResult s : bestStrokes) {
				if (s.score == 0) {
					if (!s.knownStrokeIndex.equals(s.drawnStrokeIndex)) {
						for (StrokeResult subS : bestStrokes) {
							boolean addWrongOrderCriticism =
								s.drawnStrokeIndex.equals(subS.knownStrokeIndex) &&
								subS.score == 0 &&
								!rearrangedDrawnStrokes.contains(s.drawnStrokeIndex) &&
								!rearrangedDrawnStrokes.contains(subS.drawnStrokeIndex);

							if (addWrongOrderCriticism) {
								misorderedStrokes.add(s);
								rearrangedDrawnStrokes.add(s.drawnStrokeIndex);
								break;
							}
						}
					}
				} else {
					if (!(s.knownStrokeIndex == null || s.drawnStrokeIndex == null)) {
						c.add(criticismMatrix[s.knownStrokeIndex][s.drawnStrokeIndex].message,
								new Criticism.RightStrokeColour(s.knownStrokeIndex),
								new Criticism.WrongStrokeColour(s.drawnStrokeIndex));
					}
				}
			}

			if(misorderedStrokes.size() > 2){
				String message = misorderedStrokes.size() == known.strokeCount() ?
						"Your strokes seem correct, but are drawn in the wrong order." :
						"Several strokes are drawn correctly, but in the wrong order.";
				c.add(message,
						Criticism.SKIP,
						Criticism.SKIP);
			} else {
				for(StrokeResult s: misorderedStrokes){
					c.add("Your " + Util.adjectify(s.knownStrokeIndex, drawn.strokeCount()) + " and " + Util.adjectify(s.drawnStrokeIndex, drawn.strokeCount()) + " strokes are correct, except drawn in the wrong order.",
							Criticism.correctColours(s.knownStrokeIndex, s.drawnStrokeIndex),
							Criticism.incorrectColours(s.knownStrokeIndex, s.drawnStrokeIndex));
				}
			}
		}


		if(overallFailures.contains(OverallFailure.MISSING_STROKES)){
			int missingStrokes = this.known.strokeCount() - this.drawn.strokeCount();
			String message = missingStrokes == 1 ?
					"You are missing a stroke." :
					"You are missing " + Util.nounify(missingStrokes) + " strokes.";

			Criticism.PaintColourInstructions colours;
			int[] missedStrokes = findMissingStrokes(known.strokeCount(), drawn.strokeCount(), bestStrokes);
			if(missedStrokes == null){
				colours = Criticism.SKIP;
			} else {
				colours = Criticism.correctColours(missedStrokes);
			}
			c.add(message, colours, Criticism.SKIP);

		} else if(overallFailures.contains(OverallFailure.EXTRA_STROKES)) {
			int extraStrokes = this.drawn.strokeCount() - this.known.strokeCount();
			String message = extraStrokes == 1 ?
					"You drew an extra stroke." :
					"You drew " + Util.nounify(extraStrokes) + " extra strokes.";

            Criticism.PaintColourInstructions colours;
            int[] nonKnownStrokes = findExtraStrokes(known.strokeCount(), drawn.strokeCount(), bestStrokes);
            if(nonKnownStrokes == null){
                colours = Criticism.SKIP;
            } else {
                colours = Criticism.incorrectColours(nonKnownStrokes);
            }

			c.add(message, Criticism.SKIP, colours);
		}

		CommonlyConfusedChecker.checkEasilyConfusedCharacters(target, drawn, c);

		// special case for hiragana and katakana: find if the user drew katakana version instead of hiragana, and vice-versa
		if(allowRecursion == Recursion.ALLOW){
			if(!c.pass && Kana.isHiragana(target) && target != 'も'){
				char katakanaVersion = Kana.hiragana2Katakana(String.valueOf(target)).charAt(0);
				DrawingComparator pc = new DrawingComparator(assetFinder);
				if(pc.compare(katakanaVersion, this.drawn, assetFinder.findGlyphForCharacter(katakanaVersion), Recursion.DISALLOW).pass){
					Criticism specific = new Criticism();
					specific.add("You drew the katakana " + katakanaVersion + " instead of the hiragana " + target + ".", Criticism.SKIP, Criticism.SKIP);
					return specific;
				}
			} else if(!c.pass && Kana.isKatakana(target) && target != 'モ'){
				char hiraganaVersion = Kana.katakana2Hiragana(String.valueOf(target)).charAt(0);
				DrawingComparator pc = new DrawingComparator(assetFinder);
				if(pc.compare(hiraganaVersion, this.drawn, assetFinder.findGlyphForCharacter(hiraganaVersion), Recursion.DISALLOW).pass){
					Criticism specific = new Criticism();
					specific.add("You drew the hiragana " + hiraganaVersion + " instead of the katakana " + target + ".", Criticism.SKIP, Criticism.SKIP);
					return specific;
				}
			}
		}
		
		return c;
		
		// ToDo:
		// intersection point distance errors
	}

	static boolean[][] calculateAboveMatrix(PointDrawing d){
		boolean[][] matrix = new boolean[d.strokeCount()][d.strokeCount()];
		for(int i = 0; i < d.strokeCount(); i++){
			for(int j = 0; j < d.strokeCount(); j++){
				if(i >= j){
					matrix[i][j] = false;
				} else {
                    if(DEBUG) Log.d("nakama-calc", "---------------------------------------------------");
                    if(DEBUG) Log.d("nakama-calc", "About to calculate stroke " + i + " x " + j + " for aboveness");
					matrix[i][j] = isAbove(d.get(i), d.get(j), d);
				}
			}
		}
		return matrix;
	}

	static private boolean isAbove(Stroke s1, Stroke s2, PointDrawing d) {
		int extra = (int)(d.findBoundingBox().height() * 0.25);
		return lowestPoint(s1) + extra < highestPoint(s2);
	}

	static private int highestPoint(Stroke s){
		int highest = Integer.MAX_VALUE;
		for(Point p: s.points){
			if(p.y < highest){
				highest = p.y;
			}
		}
		return highest;
	}

	static private int lowestPoint(Stroke s){
		int lowest = 0;
		for(Point p: s.points){
			if(p.y > lowest){
				lowest = p.y;
			}
		}
		return lowest;
	}

    /**
     * Returns a list of StrokeResults. Size of the list is equal to the number of drawn
     * strokes, not the number of known strokes.
     */
    static List<StrokeResult> findGoodPairings(int[][] matrix) {
        Set<Integer> finishedRows = new TreeSet<>();
        Set<Integer> finishedCols = new TreeSet<>();
        List<StrokeResult> pairs = new ArrayList<>(matrix[0].length);

        // first go down the diagonal and accept any 0s
        for (int i = 0; i < matrix.length && i < matrix[i].length; i++) {
            if (matrix[i][i] == 0) {
                finishedRows.add(i);
                finishedCols.add(i);

                pairs.add(new StrokeResult(i, i, 0));
            }
        }
        // now go row by row, column by column and assign the first match
        for (int i = 0; i < matrix.length; i++) {
            int selected = -1;
            if (finishedRows.contains(i)) {
                continue;
            }

            for (int j = 0; j < matrix[i].length; j++) {
                if (0 == matrix[i][j]) {
                    if (finishedCols.contains(j)) {
                        continue;
                    }
                    selected = j;
                }
            }

            if (selected != -1) {
                finishedRows.add(i);
                finishedCols.add(selected);
                pairs.add(new StrokeResult(i, selected, matrix[i][selected]));
            }
        }
        return pairs;
    }




    static List<StrokeResult> findBestPairings(int[][] matrix){
		Set<Integer> finishedRows = new TreeSet<>();
		Set<Integer> finishedCols = new TreeSet<>();
		List<StrokeResult> pairs = new ArrayList<>(matrix[0].length);
		
		// first go down the diagonal and accept any 0s
		for(int i = 0; i < matrix.length && i < matrix[i].length; i++){
			if(matrix[i][i] == 0){
				finishedRows.add(i);
				finishedCols.add(i);
				
				pairs.add(new StrokeResult(i, i, 0));
			}
		}
		
		// now go row by row, column by column and assign the first match
		for(int i = 0; i < matrix.length; i++){
			if(finishedRows.contains(i)){
				continue;
			}

			int selected = -1;
			int min = Integer.MAX_VALUE;
			
			for(int j = 0; j < matrix[i].length; j++){
				if(finishedCols.contains(j)){
					continue;
				}
			
				if(min > matrix[i][j]){
					selected = j;
					min = matrix[i][j];
				}
			}
			
			if(selected >= 0){
				finishedRows.add(i);
				finishedCols.add(selected);
				pairs.add(new StrokeResult(i,  selected, matrix[i][selected]));
			}
		}
		
		for(int i = 0; i < matrix.length; i++){
			if(!finishedRows.contains(i)){
				pairs.add(new StrokeResult(i, null, 1));
			}
		}
		
		return pairs;
	}
	
	/**
	 * Compares one stroke to another, generating a list of criticisms.
	 */
	private StrokeCriticism compareStroke(int baseIndex, int challengerIndex){
		if(DEBUG) Log.d("nakama", "\n================================================================");
		if(DEBUG) Log.d("nakama", "Comparing base stroke " + baseIndex + " to challenger stroke " + challengerIndex);
		List<StrokeCompareFailure> failures = new LinkedList<StrokeCompareFailure>();

		Stroke bpath = this.known.get(baseIndex);
		Stroke cpath = this.drawn.get(challengerIndex);
		if(DEBUG) Log.d("nakama", String.format("%30s:  Base: %6d Drawn: %6d", "Number of points", bpath.pointSize(), cpath.pointSize()));

        if(DEBUG) Log.d("nakama", String.format("Base points: " + Util.join(", ", bpath.points)));
        if(DEBUG) Log.d("nakama", String.format("Drawn points: " + Util.join(", ", cpath.points)));

		Point bstart = bpath.startPoint;
		Point bend = bpath.endPoint;
		Point cstart = cpath.startPoint;
		Point cend = cpath.endPoint;
		
		if(DEBUG) Log.d("nakama", String.format("%30s:  Base[0]: %s Drawn[0]: %s", "Start points", bstart, cstart));
		if(DEBUG) Log.d("nakama", String.format("%30s:  Base[-1]: %s Drawn[-1]: %s", "End points", bend, cend));
		
		/* Arc length is seeming to be not so accurate, due to standardSets the minor fluctuation in a user's stroke throwing it off.
		 * Lets rely more on distance traveled (distance from start point to end point ignoring curvature completely).

  		int blength = bpath.arcLength();
		int clength = cpath.arcLength();
		float arcLengthDiff = 1 - Math.abs((float)Math.min(blength, clength) / Math.max(blength, clength));
		boolean arcLengthDifference = arcLengthDiff > arcLengthFailPercentage(blength);
		// arc lengths should be about equal.
		Log.d("nakama", String.format("%30s:  %6d %6d", "Arc Length", blength, clength));
		if(arcLengthDifference){
			if(blength > clength)
				criticisms.add(Criticism.distanceFail("Your " + Util.adjectify(challengerIndex) + " stroke is too short.", // [Percent diff was: " + arcLengthDiff + "; base length: " + blength + ", your length: " + clength + "]",
					bend, cend, FAIL_ARC_LENGTH_PERCENTAGE));
			else 
				criticisms.add(Criticism.distanceFail("Your " + Util.adjectify(challengerIndex) + " stroke is too long.", // [Percent diff was: " + arcLengthDiff + "; base length: " + blength + ", your length: " + clength + "]",
				bend, cend, FAIL_ARC_LENGTH_PERCENTAGE));
		}
		 */
		
		double bDistanceTravelled = bpath.distanceFromStartToEndPoints();
		double cDistanceTravelled = cpath.distanceFromStartToEndPoints();
		int percentDiff = (int)(Math.abs(bDistanceTravelled - cDistanceTravelled) / ((bDistanceTravelled + cDistanceTravelled) / 2) * 100);
		if(DEBUG) Log.d("nakama", String.format("%30s:  %6.2f %6.2f. Percentage diff: %d, limit is %d", "Distance Travelled", bDistanceTravelled, cDistanceTravelled, percentDiff, PERCENTAGE_DISTANCE_DIFF_LIMIT));
		if(percentDiff > PERCENTAGE_DISTANCE_DIFF_LIMIT)
			failures.add(StrokeCompareFailure.DISTANCE_TRAVELLED);
		
	
		final double bStartRadians = bpath.startDirection();
		final double cStartRadians = cpath.startDirection();
		
		final double bEndRadians = bpath.endDirection();
		final double cEndRadians = cpath.endDirection();
		
		if(DEBUG) Log.d("nakama", "Base stroke " + baseIndex + " has points: " + Util.join(", ", bpath.points));
		
		double challengerStartDiff = Math.min((2 * Math.PI) - Math.abs(cStartRadians - cStartRadians), Math.abs(cStartRadians - cStartRadians));
		double baseStartDiff = Math.min((2 * Math.PI) - Math.abs(bStartRadians - bStartRadians), Math.abs(cStartRadians - cStartRadians));

		final boolean smallDistance = bDistanceTravelled < CIRCLE_DETECTION_DISTANCE && cDistanceTravelled < CIRCLE_DETECTION_DISTANCE;
		final boolean baseSameStartEndDirection = baseStartDiff < STROKE_DIRECTION_LIMIT_RADIANS;
		final boolean challengerSameStartEndDirection = challengerStartDiff < STROKE_DIRECTION_LIMIT_RADIANS;
		if(smallDistance && baseSameStartEndDirection && challengerSameStartEndDirection){
			if(DEBUG) Log.d("nakama", "SPECIAL CASE: CIRCLE detected, ignoring stroke directions.");
			return new StrokeCriticism(null, 0);
		}


		{
			double startDistance = PathCalculator.distance(bstart, cstart);
			if(startDistance > FAIL_POINT_START_DISTANCE){
				failures.add(StrokeCompareFailure.START_POINT_DIFFERENCE);
			}
			if(DEBUG) Log.d("nakama", String.format("%30s:  %6.2f from points known %6s, drawn %6s. Fail distance is %6.2f.", "Start Point Difference", startDistance, bstart, cstart, FAIL_POINT_START_DISTANCE));
		}
		
		// end points should be close to each other.
		{
			double endDistance = PathCalculator.distance(bend, cend);
			if(endDistance > FAIL_POINT_END_DISTANCE){
				failures.add(StrokeCompareFailure.END_POINT_DIFFERENCE);
			}
			if(DEBUG) Log.d("nakama", String.format("%30s:  %6.2f from points known %6s, drawn %6s. Fail distance is %6.2f.", "End Point Difference", endDistance, bend, cend, FAIL_POINT_END_DISTANCE));
		}

		// TODO: curvature differences: concave vs convex lines might have same length, but be wrong.
		// but maybe beginning and end direction deal with this pretty well?
	
		// for the 2 direction comparisons below, first compare the numerical difference. If its big enough, also compare string 
		// version. String compare is also needed because string version is what is shown to users. If we don't compare strings, 
		// users could see 'was down, should be down'. If we don't compare numbers, users could be off by 0.00001 radians, but if
		// they are right on the border between two direction-descriptions, still get a fail. So, both are needed.
		
		// direction at beginning of stroke
		String bStartDirection = Util.radiansToEnglish(bStartRadians);
		String cStartDirection = Util.radiansToEnglish(cStartRadians);
		double radianStartDiff = Math.min((2 * Math.PI) - Math.abs(bStartRadians - cStartRadians), Math.abs(bStartRadians - cStartRadians));
		if(DEBUG) Log.d("nakama", String.format("%30s:  %6.2f (%s) %6.2f (%s). Radian difference is %6.2f Limit %f.", "Start angle", bStartRadians, bStartDirection, cStartRadians, cStartDirection, radianStartDiff, STROKE_DIRECTION_LIMIT_RADIANS));
		if(radianStartDiff > STROKE_DIRECTION_LIMIT_RADIANS && !cStartDirection.equals(bStartDirection)){
			failures.add(StrokeCompareFailure.START_DIRECTION_DIFFERENCE);
		}
	
		// direction at end of stroke
		String bEndDirection = Util.radiansToEnglish(bEndRadians);
		String cEndDirection = Util.radiansToEnglish(cEndRadians);
		double radianEndDiff = Math.min((2 * Math.PI) - Math.abs(bEndRadians - cEndRadians), Math.abs(bEndRadians - cEndRadians));
		if(DEBUG) Log.d("nakama", String.format("%30s:  %6.2f (%s) %6.2f (%s). Radian difference is %6.2f. Limit %f.", "End angle", bEndRadians, bEndDirection, cEndRadians, cEndDirection, radianEndDiff, STROKE_DIRECTION_LIMIT_RADIANS));
		if(radianEndDiff > STROKE_DIRECTION_LIMIT_RADIANS && !bEndDirection.equals(cEndDirection)){
			failures.add(StrokeCompareFailure.END_DIRECTION_DIFFERENCE);
		}
		
		// hard curves
/*		List<Point> baseCurvePoints = PathCalculator.findSharpCurves(bpath);
		Log.d("nakama", "Scanned base for sharp curves, found " + baseCurvePoints.size());
		List<Point> drawnCurvePoints = PathCalculator.findSharpCurves(cpath);
		Log.d("nakama", "Scanned drawn for sharp curves, found " + drawnCurvePoints.size());
		if(drawnCurvePoints.size() > baseCurvePoints.size()){
			failures.add(StrokeCompareFailure.TOO_MANY_SHARP_CURVES);
		} else if(drawnCurvePoints.size() < baseCurvePoints.size()){
			failures.add(StrokeCompareFailure.TOO_FEW_SHARP_CURVES);
		} else {
			// point by point comparison
		}
*/
		
		// basic data points collected above; now go through basic criticisms data, and try to combine into more constructive ones.
		// ===================================================================================================================
		
		// detect if the stroke is good, but in the wrong direction. 
		boolean startsCloserToEnd = PathCalculator.distance(bstart, cend) < FAIL_POINT_END_DISTANCE;
		boolean endsCloserToStart = PathCalculator.distance(bend, cstart) < FAIL_POINT_START_DISTANCE;
		
		//TODO: this needs checking:
		boolean startsReversed = Math.abs(bStartRadians - PathCalculator.reverseDirection(cEndRadians)) < STROKE_DIRECTION_LIMIT_RADIANS;
		boolean endsReversed = Math.abs(bEndRadians - PathCalculator.reverseDirection(cStartRadians)) < STROKE_DIRECTION_LIMIT_RADIANS;
		if(startsCloserToEnd && endsCloserToStart && startsReversed && endsReversed){
			failures.clear();
			failures.add(StrokeCompareFailure.BACKWARDS);
				// + "startsAtEnd: " + startsCloserToEnd + "; endsAtStart: " + endsCloserToStart + "; startsReversed: " + startsReversed + "; endReversed: " + endsReversed + "; " +
				//	"base dirs: " + bStartRadians + "; " + bEndRadians + "; challenger dirs: " + cStartRadians + ", " + cEndRadians +
				//	"; base dirs: " + bStartDirection + ", " + bEndDirection + "; challenger dirs: " + cStartDirection + ", " + cEndDirection));
		}
		if(DEBUG) Log.d("nakama", "========================== end of " + baseIndex + " vs " + challengerIndex);

		if(failures.size() == 0) {

			for(int di = 0; di < drawnAboveMatrix[challengerIndex].length; di++){
				if(challengerIndex < knownAboveMatrix.length && di < knownAboveMatrix[challengerIndex].length &&
						(!drawnAboveMatrix[challengerIndex][di] && knownAboveMatrix[challengerIndex][di])){
					return new StrokeCriticism("Your " + Util.adjectify(di, drawnAboveMatrix.length) + " stroke should be completely below your " + Util.adjectify(challengerIndex, drawnAboveMatrix.length));
				}
			}

			return new StrokeCriticism(null, 0);
			
		} else if(failures.size() == 1) {
			StrokeCompareFailure f = failures.get(0);
		
			switch (f) {
			case START_DIRECTION_DIFFERENCE:
				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke starts pointing " + cStartDirection + ", but should point " + bStartDirection + "."
                + (DEBUG ? String.format(" [points %.2f, but should be %.2f]", cStartRadians, bStartRadians) : ""));
			case END_DIRECTION_DIFFERENCE:
				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke ends pointing " + cEndDirection + ", but should point " + bEndDirection + "."
                        + (DEBUG ? String.format(" [points %.2f, but should be %.2f]", cEndRadians, bEndRadians) : ""));
			case START_POINT_DIFFERENCE:
				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke's starting point is off.");
			case END_POINT_DIFFERENCE:
				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke's ending point is off.");
			case DISTANCE_TRAVELLED:
				if(cDistanceTravelled < bDistanceTravelled) {
					return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is too short." +
                            (DEBUG ? " (base: " + bDistanceTravelled + ", challenge: " + cDistanceTravelled + ")" : ""));
				} else {
					return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is too long." +
                        (DEBUG ? " (base: " + bDistanceTravelled + ", challenge: " + cDistanceTravelled + ")" : ""));
				}
//			case TOO_FEW_SHARP_CURVES:
//			case TOO_MANY_SHARP_CURVES:
//				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke has " + drawnCurvePoints.size() + " sharp curve" + (drawnCurvePoints.size() == 1 ? "" : "s") + ", but should have " + baseCurvePoints.size() + ".");
			case BACKWARDS:
				return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is backwards.");

			default:
				throw new RuntimeException("Error: unhandled StrokeCompareFailure");
			}
			
		} else if(failures.size() == 2){

			if(failures.contains(StrokeCompareFailure.DISTANCE_TRAVELLED)){
				String distanceMessage = cDistanceTravelled > bDistanceTravelled ? "too long" : "too short";
				if(failures.contains(StrokeCompareFailure.START_DIRECTION_DIFFERENCE)){
					return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is " + distanceMessage + ", and starts pointing " + cStartDirection +" instead of " + bStartDirection, 2);
				} else if(failures.contains(StrokeCompareFailure.END_DIRECTION_DIFFERENCE) && failures.contains(StrokeCompareFailure.DISTANCE_TRAVELLED)){
					return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is " + distanceMessage + ", and ends pointing " + cEndDirection +" instead of " + bEndDirection, 2);
				} else if(failures.contains(StrokeCompareFailure.END_POINT_DIFFERENCE)){
					if(bDistanceTravelled > cDistanceTravelled){
						return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is too short.", 2);
					} else {
						return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is too long.", 2);
					}
				}
			}

			return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is not correct.", failures.size());
		}  else {
			return new StrokeCriticism("Your " + Util.adjectify(challengerIndex, drawn.strokeCount()) + " stroke is not correct.", failures.size());
		}
	}
}