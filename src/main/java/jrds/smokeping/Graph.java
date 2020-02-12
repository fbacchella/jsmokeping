package jrds.smokeping;

import java.awt.Color;
import java.io.IOException;
import java.util.Map;

import org.rrd4j.data.DataProcessor;
import org.rrd4j.data.Plottable;
import org.rrd4j.data.Variable;
import org.rrd4j.graph.RrdGraphDef;

import jrds.GraphNode;
import jrds.Probe;
import jrds.store.ExtractInfo;
import jrds.store.Extractor;

public class Graph extends jrds.Graph {

    double stddev = Double.NaN;
    double maxmedian = Double.NaN;

    public Graph(GraphNode node) {
        super(node);
    }

    @Override
    protected void setGraphDefData(RrdGraphDef graphDef, Probe<?, ?> defProbe, ExtractInfo ei,
            Map<String, ? extends Plottable> customData) {
        super.setGraphDefData(graphDef, defProbe, ei, customData);
        getStdDev(defProbe, ei);
        double max = (maxmedian + stddev) * 1.2;
        graphDef.setRigid(true);
        graphDef.setMaxValue(max);
        graphDef.setMinValue(0);

        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            String rpn = String.format("%s,%15e,LT,%s,INF,IF", ping, max, ping);
            graphDef.datasource("cp" + i, rpn);
        }
        for(int i = 1 ; i <= 10; i++){
            String rpn = String.format("cp%d,UN,UNKN,cp%d,cp%d,-,IF", i, 21 - i, i);
            graphDef.datasource("smoke" + i, rpn);
            graphDef.area("cp" + i, TRANSLUCENT);
            graphDef.stack("smoke" + i, toGray(i - 1));
        }
        graphDef.datasource("avmed", "median", new Variable.AVERAGE());

        graphDef.datasource("mesd", String.format("avmed,%15e,/", stddev));

        graphDef.line("median", MEDIAN);
        graphDef.gprint("mesd", "%.1lf %s am/s\\l");

        graphDef.comment("loss color");
        int previous = -1;
        for(int i = 0; i <= 6; i++ ) {
            int indice = i;
            if(i == 5)
                indice = 10;
            else if(i == 6)
                indice = 19;
            graphDef.datasource("me" + indice, String.format("loss,%d,GT,loss,%d,LE,*,1,UNKN,IF,median,*", previous, indice));
            double thickness = max / getNode().getGraphDesc().getHeight();
            graphDef.datasource("meL" + indice, String.format("me%d,%f,-", indice, thickness));
            graphDef.datasource("meH" + indice, String.format("me%d,0,*,%f,2,*,+", indice, thickness));
            graphDef.area("meL" + indice, TRANSLUCENT);
            graphDef.stack("meH" + indice, toLoss(i), "" + indice + "/20");
            previous = indice;
        }
        graphDef.comment("\\l");
    }

    static private final Color TRANSLUCENT = new Color(255, 255, 255, 0);
    static private final Color MEDIAN = new Color(0x202020);
    static private final int[] GRAYS = new int[] {0xdddddd, 0xcacaca, 0xb7b7b7, 0xa4a4a4, 0x919191, 0x7e7e7e, 0x6b6b6b, 0x585858, 0x454545, 0x323232};
    static private final int[] LOSS = new int[] {0x26ff00, 0x00b8ff, 0x0059ff, 0x5e00ff, 0x7e00ff, 0xdd00ff, 0xff0000};

    private final Color toGray(int index) {
        Color c = new Color(GRAYS[index]);
        return c;
    };

    private final Color toLoss(int index) {
        Color c = new Color(LOSS[index]);
        return c;
    };

    private void getStdDev(Probe<?,?> p, ExtractInfo ei) {
        Extractor ex = p.fetchData();
        ex.addSource("median", "median");
        DataProcessor dp = ei.getDataProcessor(ex);
        try {
            dp.processData();
        } catch (IOException e) {
            throw new RuntimeException("Failed to access rrd file  " + p.getMainStore().getPath(), e);
        }
        double[] median_val = dp.getValues("median");
        double sum = 0;
        double sqsum = 0;
        maxmedian = 0;
        int cnt = 0;

        for(double val: median_val) {
            if (!Double.isNaN(val)) {
                cnt++;
                sum += val;
                sqsum += val * val;
                maxmedian = Math.max(maxmedian, val);
            }
        }

        double sqdev =  ( sqsum - (sum*sum) / cnt ) / cnt ;
        stddev = sqdev < 0 ? 0 : Math.sqrt(sqdev);
    }

}
