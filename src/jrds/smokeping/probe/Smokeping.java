package jrds.smokeping.probe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jrds.factories.ProbeBean;
import jrds.probe.ExternalCmdProbe;
import jrds.probe.IndexedProbe;

import org.apache.log4j.Level;

@ProbeBean({"node"})
public class Smokeping extends ExternalCmdProbe implements IndexedProbe {
    private String node = null;

    @Override
    public Boolean configure() {
        try {
            InetAddress addr = InetAddress.getByName(getNode());
            cmd = cmd +  " " + addr.getHostAddress();
        } catch (UnknownHostException e) {
            log(Level.ERROR, "host name %s unknown", getNode());
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see jrds.probe.ExternalCmdProbe#getNewSampleValues()
     */
    @Override
    public Map<String, Number> getNewSampleValues() {
        String smallping = launchCmd();
        String[] valuesStr = smallping.split(":");
        if(valuesStr.length != 21) {
            log(Level.ERROR, "smallping run failed: %s", smallping);
            return Collections.emptyMap();
        }
        List<Double> values= new ArrayList<Double>(20);
        int loss = 20;
        for(int i=1; i <= 20; i++) {
            String valparse = valuesStr[i];
            Double parsed = jrds.Util.parseStringNumber(valparse, Double.NaN);
            if(! parsed.isNaN()) {
                values.add(parsed);
                loss--;
            }
        }
        Collections.sort(values, new Comparator<Double>() {
            public int compare(Double arg0, Double arg1) {
                return Double.compare(arg0, arg1);
            }
        });
        Map<String, Number> valuesMap = new HashMap<String, Number>(values.size());
        int start =  1 + (int) Math.floor( (float) loss / 2);
        int end = 20 - (int) Math.ceil( (float) loss / 2);
        for(int i = start ; i <= end; i++ ) {
            valuesMap.put("ping" + i, values.get(i - start));
        }
        valuesMap.put("loss", loss);
        valuesMap.put("median", median(values));
        return valuesMap;
    }

    // the list m must be already sorted
    private final Double median(List<Double> m) {
        if(m.size() < 1)
            return Double.NaN;
        int middle = m.size()/2;
        if (m.size() % 2 == 1) {
            return m.get(middle);
        } else {
            return (m.get(middle-1) + m.get(middle)) / 2.0;
        }
    }
    
    /**
     * @return the host
     */
    public String getNode() {
        if(node == null) {
            return this.getHost().getDnsName();
        }
        else
            return node;
    }

    /**
     * @param host the host to set
     */
    public void setNode(String node) {
        this.node = node;
    }

    @Override
    public String getIndexName() {
        return getNode();
    }

}
