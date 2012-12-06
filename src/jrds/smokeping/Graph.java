package jrds.smokeping;

import java.awt.Color;
import java.util.Map;

import jrds.GraphNode;
import jrds.Probe;

import org.rrd4j.ConsolFun;
import org.rrd4j.core.FetchData;
import org.rrd4j.data.Plottable;
import org.rrd4j.graph.RrdGraphDef;

public class Graph extends jrds.Graph {
    
    double stddev = Double.NaN;
    double maxmedian = Double.NaN;

    public Graph(GraphNode node) {
        super(node);
    }

    @Override
    protected void setGraphDefData(RrdGraphDef graphDef, Probe<?, ?> defProbe,
            Map<String, ? extends Plottable> customData) {
        super.setGraphDefData(graphDef, defProbe, customData);
        getStdDev();
        double max = (maxmedian + stddev) * 1.2;
        graphDef.setRigid(true);
        graphDef.setMaxValue(max); // $max->{$start},
        graphDef.setMinValue(0);           // ($cfg->{Presentation}{detail}{logarithmic} ? ($max->{$start} > 0.01) ? '0.001' : '0.0001' : '0'),
        // '--vertical-label',$ProbeUnit,

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
        graphDef.datasource("avmed", "median", ConsolFun.AVERAGE);

        graphDef.datasource("mesd", String.format("avmed,%15e,/", stddev));

        graphDef.line("median", toColor("202020"));
        graphDef.gprint("mesd", ConsolFun.AVERAGE, "%.1lf %s am/s\\l");

        graphDef.comment("loss color");
        int previous = -1;
        for(int i = 0; i <= 6; i++ ) {
            int indice = i;
            if(i == 5)
                indice = 10;
            else if(i == 6)
                indice = 19;
            graphDef.datasource("me" + indice, String.format("loss,%d,GT,loss,%d,LE,*,1,UNKN,IF,median,*", previous, indice));
            //$swidth = $max->{$s}{$start} / $cfg->{Presentation}{detail}{height};
            graphDef.datasource("meL" + indice, String.format("me%d,7.238592e-05,-", indice));
            graphDef.datasource("meH" + indice, String.format("me%d,0,*,7.238592e-05,2,*,+", indice));
            graphDef.area("meL" + indice, TRANSLUCENT);
            graphDef.stack("meH" + indice, toLoss(i), "" + indice + "/20");
            previous = indice;
        }
        graphDef.comment("\\l");
        //HRULE:0#000000
    }
    static private final Color TRANSLUCENT = new Color(255, 255, 255, 0);
    static private final String[] GRAYS = new String[] {"dddddd", "cacaca", "b7b7b7", "a4a4a4", "919191", "7e7e7e", "6b6b6b", "585858", "454545", "323232"};
    static private final String[] LOSS = new String[] {"26ff00", "00b8ff", "0059ff", "5e00ff", "7e00ff", "dd00ff", "ff0000"};
    //221, 202, 183, 164, 145, 126, 107, 88, 69, 50

    private final Color toGray(int index) {
        String hexa = GRAYS[index];
        Color c = new Color(Integer.parseInt(hexa, 16));
        return c;
    };

    private final Color toLoss(int index) {
        String hexa = LOSS[index];
        Color c = new Color(Integer.parseInt(hexa, 16));
        return c;
    };

    private final Color toColor(String color) {
        return new Color(Integer.parseInt(color, 16));
    };

    private void getStdDev() {
        FetchData fd = getNode().getProbe().fetchData(getStartSec(), getEndSec());
        double[] median_val = fd.getValues("median");
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
