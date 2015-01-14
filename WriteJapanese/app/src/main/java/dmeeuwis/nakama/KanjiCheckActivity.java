package dmeeuwis.nakama;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import dmeeuwis.Kanji;
import dmeeuwis.kanjimaster.R;
import dmeeuwis.nakama.data.AssetFinder;
import dmeeuwis.nakama.kanjidraw.CurveDrawing;
import dmeeuwis.nakama.views.AnimatedCurveView;

public class KanjiCheckActivity extends ActionBarActivity {

    private static class KanjiViewHolder extends RecyclerView.ViewHolder {

        public AnimatedCurveView curve;
        public AnimatedCurveView points;
        public TextView info;

        public KanjiViewHolder(View itemView) {
            super(itemView);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kanji_check);

        final AssetManager am = this.getAssets();
        final AssetFinder af = new AssetFinder(am);

        RecyclerView rv = (RecyclerView)this.findViewById(R.id.kanji_check_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemLayout = LayoutInflater.from(parent.getContext()).inflate(R.layout.kanji_check_item, parent, false);
                KanjiViewHolder kh = new KanjiViewHolder(itemLayout);
                kh.curve = (AnimatedCurveView)itemLayout.findViewById(R.id.kanji_check_curve);
                kh.points = (AnimatedCurveView)itemLayout.findViewById(R.id.kanji_check_points);
                kh.info = (TextView) itemLayout.findViewById(R.id.kanji_check_text);

                return kh;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                CurveDrawing cv = null;
                if(position < Kanji.JOUYOU_G1.length()) {
                    cv = af.findGlyphForCharacter("j1", Kanji.JOUYOU_G1.charAt(position));
                }

                if(cv != null) {
                    KanjiViewHolder kh = (KanjiViewHolder) holder;
                    kh.curve.setDrawing(cv, AnimatedCurveView.DrawTime.STATIC);
                    kh.points.setDrawing(cv.toDrawing(), AnimatedCurveView.DrawTime.STATIC);
                    kh.info.setText("Get sharp points");
                }
            }

            @Override
            public int getItemCount() {
                return Kanji.JOUYOU_G1.length();
            }
        });
    }
}
