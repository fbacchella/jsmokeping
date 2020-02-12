package jrds.smokeping;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

import org.junit.Test;
import org.rrd4j.ConsolFun;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.jrrd.ConsolidationFunctionType;
import org.rrd4j.core.jrrd.DataChunk;
import org.rrd4j.core.jrrd.RRDatabase;
import org.rrd4j.data.DataProcessor;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphConstants;
import org.rrd4j.graph.RrdGraphDef;

public class TestBuildGraph {
    /* /opt/smokeping/cache/RTT/LB_last_10800.png --start -10800 --height 200 --width 600 
     * --title Last 3 Hours --rigid --upper-limit 0.014477184 --lower-limit 0 --vertical-label Seconds --imgformat PNG 
     * --color SHADEA#ffffff --color SHADEB#ffffff --color BACK#ffffff --color CANVAS#ffffff 
     * DEF:ping1=/opt/smokeping/data/RTT/LB.rrd:ping1:AVERAGE 
     * DEF:ping2=/opt/smokeping/data/RTT/LB.rrd:ping2:AVERAGE 
     * DEF:ping3=/opt/smokeping/data/RTT/LB.rrd:ping3:AVERAGE 
     * DEF:ping4=/opt/smokeping/data/RTT/LB.rrd:ping4:AVERAGE 
     * DEF:ping5=/opt/smokeping/data/RTT/LB.rrd:ping5:AVERAGE 
     * DEF:ping6=/opt/smokeping/data/RTT/LB.rrd:ping6:AVERAGE 
     * DEF:ping7=/opt/smokeping/data/RTT/LB.rrd:ping7:AVERAGE 
     * DEF:ping8=/opt/smokeping/data/RTT/LB.rrd:ping8:AVERAGE 
     * DEF:ping9=/opt/smokeping/data/RTT/LB.rrd:ping9:AVERAGE 
     * DEF:ping10=/opt/smokeping/data/RTT/LB.rrd:ping10:AVERAGE 
     * DEF:ping11=/opt/smokeping/data/RTT/LB.rrd:ping11:AVERAGE 
     * DEF:ping12=/opt/smokeping/data/RTT/LB.rrd:ping12:AVERAGE 
     * DEF:ping13=/opt/smokeping/data/RTT/LB.rrd:ping13:AVERAGE 
     * DEF:ping14=/opt/smokeping/data/RTT/LB.rrd:ping14:AVERAGE 
     * DEF:ping15=/opt/smokeping/data/RTT/LB.rrd:ping15:AVERAGE 
     * DEF:ping16=/opt/smokeping/data/RTT/LB.rrd:ping16:AVERAGE 
     * DEF:ping17=/opt/smokeping/data/RTT/LB.rrd:ping17:AVERAGE 
     * DEF:ping18=/opt/smokeping/data/RTT/LB.rrd:ping18:AVERAGE 
     * DEF:ping19=/opt/smokeping/data/RTT/LB.rrd:ping19:AVERAGE 
     * DEF:ping20=/opt/smokeping/data/RTT/LB.rrd:ping20:AVERAGE 
     * CDEF:cp1=ping1,0.014477184,LT,ping1,INF,IF 
     * CDEF:cp2=ping2,0.014477184,LT,ping2,INF,IF 
     * CDEF:cp3=ping3,0.014477184,LT,ping3,INF,IF 
     * CDEF:cp4=ping4,0.014477184,LT,ping4,INF,IF 
     * CDEF:cp5=ping5,0.014477184,LT,ping5,INF,IF 
     * CDEF:cp6=ping6,0.014477184,LT,ping6,INF,IF 
     * CDEF:cp7=ping7,0.014477184,LT,ping7,INF,IF 
     * CDEF:cp8=ping8,0.014477184,LT,ping8,INF,IF 
     * CDEF:cp9=ping9,0.014477184,LT,ping9,INF,IF 
     * CDEF:cp10=ping10,0.014477184,LT,ping10,INF,IF 
     * CDEF:cp11=ping11,0.014477184,LT,ping11,INF,IF 
     * CDEF:cp12=ping12,0.014477184,LT,ping12,INF,IF 
     * CDEF:cp13=ping13,0.014477184,LT,ping13,INF,IF 
     * CDEF:cp14=ping14,0.014477184,LT,ping14,INF,IF 
     * CDEF:cp15=ping15,0.014477184,LT,ping15,INF,IF 
     * CDEF:cp16=ping16,0.014477184,LT,ping16,INF,IF 
     * CDEF:cp17=ping17,0.014477184,LT,ping17,INF,IF 
     * CDEF:cp18=ping18,0.014477184,LT,ping18,INF,IF 
     * CDEF:cp19=ping19,0.014477184,LT,ping19,INF,IF 
     * CDEF:cp20=ping20,0.014477184,LT,ping20,INF,IF 
     * CDEF:smoke1=cp1,UN,UNKN,cp20,cp1,-,IF 
     * CDEF:smoke2=cp2,UN,UNKN,cp19,cp2,-,IF 
     * CDEF:smoke3=cp3,UN,UNKN,cp18,cp3,-,IF 
     * CDEF:smoke4=cp4,UN,UNKN,cp17,cp4,-,IF 
     * CDEF:smoke5=cp5,UN,UNKN,cp16,cp5,-,IF
     * CDEF:smoke6=cp6,UN,UNKN,cp15,cp6,-,IF 
     * CDEF:smoke7=cp7,UN,UNKN,cp14,cp7,-,IF
     * CDEF:smoke8=cp8,UN,UNKN,cp13,cp8,-,IF
     * CDEF:smoke9=cp9,UN,UNKN,cp12,cp9,-,IF
     * CDEF:smoke10=cp10,UN,UNKN,cp11,cp10,-,IF
     * AREA:cp1 
     * STACK:smoke1#dddddd 
     * AREA:cp2
     * STACK:smoke2#cacaca 
     * AREA:cp3
     * STACK:smoke3#b7b7b7 
     * AREA:cp4
     * STACK:smoke4#a4a4a4 
     * AREA:cp5 
     * STACK:smoke5#919191
     * AREA:cp6
     * STACK:smoke6#7e7e7e
     * AREA:cp7
     * STACK:smoke7#6b6b6b
     * AREA:cp8
     * STACK:smoke8#585858
     * AREA:cp9
     * STACK:smoke9#454545
     * AREA:cp10
     * STACK:smoke10#323232
     * DEF:loss=/opt/smokeping/data/RTT/LB.rrd:loss:AVERAGE 
     * DEF:median=/opt/smokeping/data/RTT/LB.rrd:median:AVERAGE
     * CDEF:ploss=loss,20,/,100,*
     * VDEF:avmed=median,AVERAGE
     * CDEF:mesd=median,POP,avmed,7.33211157452735e-05,/ 
     * GPRINT:avmed:median rtt\\:  %.1lf %ss avg
     * GPRINT:median:MAX:%.1lf %ss max 
     * GPRINT:median:MIN:%.1lf %ss min
     * GPRINT:median:LAST:%.1lf %ss now
     * COMMENT:0.1 ms sd
     * GPRINT:mesd:AVERAGE:%.1lf %s am/s\\l
     * LINE1:median#202020
     * GPRINT:ploss:AVERAGE:packet loss\\: %.2lf %% avg
     * GPRINT:ploss:MAX:%.2lf %% max 
     * GPRINT:ploss:MIN:%.2lf %% min 
     * GPRINT:ploss:LAST:%.2lf %% now\\l
     * COMMENT:loss color\\:
     * CDEF:me0=loss,-1,GT,loss,0,LE,*,1,UNKN,IF,median,*
     * CDEF:meL0=me0,7.238592e-05,-
     * CDEF:meH0=me0,0,*,7.238592e-05,2,*,+
     * AREA:meL0
     * STACK:meH0#26ff00:0
     * CDEF:me1=loss,0,GT,loss,1,LE,*,1,UNKN,IF,median,*
     * CDEF:meL1=me1,7.238592e-05,- 
     * CDEF:meH1=me1,0,*,7.238592e-05,2,*,+ 
     * AREA:meL1 
     * STACK:meH1#00b8ff:1/20
     * CDEF:me2=loss,1,GT,loss,2,LE,*,1,UNKN,IF,median,* 
     * CDEF:meL2=me2,7.238592e-05,-
     * CDEF:meH2=me2,0,*,7.238592e-05,2,*,+
     * AREA:meL2
     * STACK:meH2#0059ff:2/20
     * CDEF:me3=loss,2,GT,loss,3,LE,*,1,UNKN,IF,median,* 
     * CDEF:meL3=me3,7.238592e-05,- 
     * CDEF:meH3=me3,0,*,7.238592e-05,2,*,+ 
     * AREA:meL3
     * STACK:meH3#5e00ff:3/20 
     * CDEF:me4=loss,3,GT,loss,4,LE,*,1,UNKN,IF,median,* 
     * CDEF:meL4=me4,7.238592e-05,- 
     * CDEF:meH4=me4,0,*,7.238592e-05,2,*,+ 
     * AREA:meL4 
     * STACK:meH4#7e00ff:4/20 
     * CDEF:me10=loss,4,GT,loss,10,LE,*,1,UNKN,IF,median,* 
     * CDEF:meL10=me10,7.238592e-05,- 
     * CDEF:meH10=me10,0,*,7.238592e-05,2,*,+
     * AREA:meL10 
     * STACK:meH10#dd00ff:10/20 
     * CDEF:me19=loss,10,GT,loss,19,LE,*,1,UNKN,IF,median,* 
     * CDEF:meL19=me19,7.238592e-05,- 
     * CDEF:meH19=me19,0,*,7.238592e-05,2,*,+ 
     * AREA:meL19
     * STACK:meH19#ff0000:19/20 
     * COMMENT: \\l HRULE:0#000000 
     * COMMENT:probe\\:       20 ICMP Echo Pings (56 Bytes) every 60s 
     * COMMENT:end\\: Wed Feb 15 20\\:48\\:28 2012\\j
     */
    static private final Color TRANSLUCENT = new Color(255, 255, 255, 0);
    static private final String[] GRAYS = new String[] {"dddddd", "cacaca", "b7b7b7", "a4a4a4", "919191", "7e7e7e", "6b6b6b", "585858", "454545", "323232"};
    static private final String[] LOSS = new String[] {"26ff00", "00b8ff", "0059ff", "5e00ff", "7e00ff", "dd00ff", "ff0000"};
    //221, 202, 183, 164, 145, 126, 107, 88, 69, 50

    private final Color toGray(int index) {
        String hexa = GRAYS[index];
        Color c = new Color(Integer.parseInt(hexa, 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), new float[3]);
        System.out.print(String.format("Gray h=%f s=%f b=%.0f\n", hsb[0], hsb[1], hsb[2] * 255));
        return c;
    };

    private final Color toLoss(int index) {
        String hexa = LOSS[index];
        Color c = new Color(Integer.parseInt(hexa, 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), new float[3]);
        System.out.print(String.format("Loss h=%f s=%f b=%.0f\n", hsb[0] * 360, hsb[1], hsb[2] * 255));
        return c;
    };

    private final Color toGray(int index, int max) {
        float scale = 1.0f * ( 1 + index) / (max + 2 );
        Color c = new Color(Color.HSBtoRGB(0, 0, scale));
        return c;
    }    

    private final Color toColor(int hue, int index, int max) {
        float scale = 1.0f * ( 1 + index) / (max + 2 );
        Color c = new Color(Color.HSBtoRGB(1.0f * hue /360, 1, scale));
        return c;
    };

    private final Color toColor(String color) {
        return new Color(Integer.parseInt(color, 16));
    };

    private RrdGraphDef getCommonGraphDef(Calendar startCal, Calendar endCal) {
        RrdGraphDef def = new RrdGraphDef();
        def.setEndTime(endCal.getTime().getTime() /1000 );
        def.setStartTime(startCal.getTime().getTime() / 1000);
        def.setHeight(200);
        def.setWidth(600);
        def.setTitle("Last 3 Hours");
        def.setRigid(true);
        def.setMaxValue(0.014477184);
        def.setMinValue(0);
        def.setVerticalLabel("Seconds");
        def.setImageFormat("PNG");
        def.setAntiAliasing(true);
        def.setLocale(Locale.ENGLISH);
        def.setTextAntiAliasing(true);
        def.setColor(RrdGraphConstants.COLOR_SHADEA, Color.WHITE);
        def.setColor(RrdGraphConstants.COLOR_SHADEB, Color.WHITE);
        def.setColor(RrdGraphConstants.COLOR_BACK, Color.WHITE);
        def.setColor(RrdGraphConstants.COLOR_CANVAS, Color.WHITE);
        return def;

    }

    private void fillGraph(RrdGraphDef def) {
        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            String rpn = String.format("%s,0.014477184,LT,%s,INF,IF", ping, ping);
            def.datasource("cp" + i, rpn);
        }
        for(int i = 1 ; i <= 10; i++){
            String rpn = String.format("cp%d,UN,UNKN,cp%d,cp%d,-,IF", i, 21 - i, i);
            def.datasource("smoke" + i, rpn);            
            def.area("cp" + i, TRANSLUCENT);
            def.stack("smoke" + i, toGray(i - 1));
        }
        def.datasource("ploss", "loss,20,/,100,*");
        def.datasource("avmed", "median", ConsolFun.AVERAGE);
        def.datasource("mesd", "median,POP,avmed,7.33211157452735e-05,/");
        def.gprint("median", ConsolFun.AVERAGE, "median rtt:  %.1lf %ss avg");
        def.gprint("median", ConsolFun.MAX,  "%.1lf %ss max");
        def.gprint("median", ConsolFun.MIN,  "%.1lf %ss min");
        def.gprint("median", ConsolFun.LAST, "%.1lf %ss now");
        def.comment("0.1 ms sd");
        def.gprint("mesd", ConsolFun.AVERAGE, "%.1lf %s am/s\\l");
        def.line("median", toColor("202020"));
        def.gprint("ploss", ConsolFun.AVERAGE, "packet loss: %.2lf %% avg");
        def.gprint("ploss", ConsolFun.MAX, "%.2lf %% max");
        def.gprint("ploss", ConsolFun.MIN, "%.2lf %% min");
        def.gprint("ploss", ConsolFun.LAST, "%.2lf %% now\\l");
        def.comment("loss color:");
        
        int previous = -1;
        for(int i = 0; i <= 6; i++ ) {
            int indice = i;
            if(i == 5)
                indice = 10;
            else if(i == 6)
                indice = 19;
            def.datasource("me" + indice, String.format("loss,%d,GT,loss,%d,LE,*,1,UNKN,IF,median,*", previous, indice));
            def.datasource("meL" + indice, String.format("me%d,7.238592e-05,-", indice));
            def.datasource("meH" + indice, String.format("me%d,0,*,7.238592e-05,2,*,+", indice));
            def.area("meL" + indice, TRANSLUCENT);
            def.stack("meH" + indice, toLoss(i), "" + indice + "/20");
            previous = indice;
        }
        def.comment("\\l");
        //HRULE:0#000000
        def.comment("probe:       20 ICMP Echo Pings (56 Bytes) every 60s");
        def.comment("end: Wed Feb 15 20:48:28 2012\\j");
    }

    @SuppressWarnings("unused")
    @Test
    public void importXml() throws IOException {
        File xmlexport = new File("test/LB.xml");
        RrdDb db1 = new RrdDb("/tmp/rrd1.db", "xml:/" + "test/LB.xml" );
        RrdDb db2 = new RrdDb("/tmp/rrd2.db", "rrdtool:/" + "test/LB.rrd" );
    }

    @Test
    public void doGraphFromXml() throws IOException {
        RrdDb db1 = new RrdDb("/tmp/rrd1.db", "xml:/" + "test/LB.xml" );
        db1.close();

        Calendar endCal = org.rrd4j.core.Util.getCalendar("2012-02-15 21:12:00");
        Calendar startCal = org.rrd4j.core.Util.getCalendar("2012-02-15 19:12:00");

        RrdGraphDef def = getCommonGraphDef(startCal,endCal);
        def.setFilename("/tmp/testxml.png");

        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            def.datasource(ping, "/tmp/rrd1.db", ping, ConsolFun.AVERAGE);
        }
        def.datasource("loss", "/tmp/rrd1.db", "loss",ConsolFun.AVERAGE);
        def.datasource("median", "/tmp/rrd1.db", "median",ConsolFun.AVERAGE);
        fillGraph(def);
        RrdGraph graph = new RrdGraph(def);
    }

    @Test
    public void doGraph() throws IOException {

        URL lbrrd = getClass().getClassLoader().getResource("LB.rrd");
        RRDatabase db = new RRDatabase(lbrrd.getFile());


        Calendar endCal = org.rrd4j.core.Util.getCalendar("2012-02-15 21:12:00");
        Calendar startCal = org.rrd4j.core.Util.getCalendar("2012-02-15 19:12:00");
        DataChunk chunk = db.getData(ConsolidationFunctionType.AVERAGE, startCal.getTime(), endCal.getTime(), 60L);
        //        double[][] datas = chunk.getData();
        //        for(int i= 0; i < datas.length; i++) {
        //            for(int j = 0; j < datas[i].length; j++) {
        //                System.out.print("" + datas[i][j] +  " ");
        //            }
        //            System.out.println();
        //        }
        System.out.println(endCal);
        System.out.println(startCal);

        RrdGraphDef def = getCommonGraphDef(startCal,endCal);



        def.setFilename("/tmp/test.png");
        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            def.datasource(ping, "/tmp/rrd1.db", ping, ConsolFun.AVERAGE);
        }
        def.datasource("loss", "/tmp/rrd1.db", "loss",ConsolFun.AVERAGE);
        def.datasource("median", "/tmp/rrd1.db", "median",ConsolFun.AVERAGE);

        fillGraph(def);
        RrdGraph graph = new RrdGraph(def);
        graph.getRrdGraphInfo();
    }
    
    @Test
    public void dumpds() throws IOException {

        Calendar endCal = org.rrd4j.core.Util.getCalendar("2012-02-15 21:12:00");
        Calendar startCal = org.rrd4j.core.Util.getCalendar("2012-02-15 19:12:00");

        DataProcessor def = new DataProcessor(startCal, endCal);

        for(int i = 1; i <= 20; i++) {
            String ping = "ping" + i;
            def.addDatasource(ping, "/tmp/rrd1.db", ping, ConsolFun.AVERAGE);
        }
        def.addDatasource("loss", "/tmp/rrd1.db", "loss",ConsolFun.AVERAGE);
        def.addDatasource("median", "/tmp/rrd1.db", "median",ConsolFun.AVERAGE);

        for(int i = 1; i <= 1; i++) {
            String ping = "ping" + i;
            String rpn = String.format("%s,0.014477184,LT,%s,INF,IF", ping, ping);
            def.addDatasource("cp" + i, rpn);
        }
//        for(int i = 1 ; i <= 10; i++){
//            String rpn = String.format("cp%d,UN,UNKN,cp%d,cp%d,-,IF", i, 21 - i, i);
//            def.addDatasource("smoke" + i, rpn);            
//        }
        
        def.processData(); System.out.println(def.dump());

    }

}
