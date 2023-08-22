package jrds.smokeping;

import java.awt.Color;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import org.rrd4j.data.DataProcessor;
import org.rrd4j.data.IPlottable;
import org.rrd4j.data.Variable;
import org.rrd4j.graph.RrdGraphDef;

import jrds.GraphNode;
import jrds.Probe;
import jrds.store.ExtractInfo;
import jrds.store.Extractor;

public class SmokeGraph extends jrds.Graph {

    public SmokeGraph(GraphNode node) {
        super(node);
    }

    @Override
    protected void setGraphDefData(RrdGraphDef graphDef, Probe<?, ?> defProbe, ExtractInfo ei,
                                   Map<String, IPlottable> customData) {
        super.setGraphDefData(graphDef, defProbe, ei, customData);
        graphDef.setRigid(true);
        graphDef.setMinValue(0);
        graphDef.setMaxValue(getMax(defProbe, ei));
        graphDef.datasource("stddev", "median", new Variable.STDDEV());
        graphDef.datasource("max" + PERCENTILE, "max", new Variable.PERCENTILE(PERCENTILE));

        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            String rpn = String.format("%s,max95,LT,%s,max95,IF", ping, ping);
            graphDef.datasource("cp" + i, rpn);
        }
        for(int i = 1 ; i <= 10; i++){
            String rpn = String.format("cp%d,UN,UNKN,cp%d,cp%d,-,IF", i, 21 - i, i);
            graphDef.datasource("smoke" + i, rpn);
            graphDef.area("cp" + i, TRANSLUCENT);
            graphDef.stack("smoke" + i, GRAYS[i - 1]);
        }

        graphDef.comment("loss color");
        int previous = -1;
        for(int i = 0; i <= 6; i++ ) {
            int indice = i;
            if(i == 5)
                indice = 10;
            else if(i == 6)
                indice = 19;
            graphDef.datasource("me" + indice, String.format("loss,%d,GT,loss,%d,LE,*,1,UNKN,IF,median,*", previous, indice));
            graphDef.datasource("meL" + indice, String.format("me%d,stddev,-", indice));
            graphDef.datasource("meH" + indice, String.format("me%d,0,*,stddev,2,*,+", indice));
            graphDef.area("meL" + indice, TRANSLUCENT);
            graphDef.stack("meH" + indice, LOSS[i], indice + "/20");
            previous = indice;
        }
        graphDef.comment("\\l");
    }

    private double getMax(Probe<?,?> p, ExtractInfo ei) {
        Extractor ex = p.fetchData();
        ex.addSource("max", "max");
        DataProcessor dp = ei.getDataProcessor(ex);
        dp.datasource("maxpc", "max", new Variable.PERCENTILE(PERCENTILE));
        try {
            dp.processData();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to access rrd file  " + p.getMainStore().getPath(), e);
        }
        return dp.getVariable("maxpc").value;
    }

    private static final Color TRANSLUCENT = new Color(255, 255, 255, 0);
    private static final Color[] GRAYS = Stream.of(0xdddddd, 0xcacaca, 0xb7b7b7, 0xa4a4a4, 0x919191, 0x7e7e7e, 0x6b6b6b, 0x585858, 0x454545, 0x323232)
                                               .map(Color::new)
                                               .toArray(Color[]::new);
    private static final Color[] LOSS = Stream.of(0x26ff00, 0x00b8ff, 0x0059ff, 0x5e00ff, 0x7e00ff, 0xdd00ff, 0xff0000)
                                              .map(Color::new)
                                              .toArray(Color[]::new);

    private static final int PERCENTILE = 95;
}
