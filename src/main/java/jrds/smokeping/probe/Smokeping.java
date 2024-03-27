package jrds.smokeping.probe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.event.Level;

import jrds.factories.ProbeBean;
import jrds.probe.ExternalCmdProbe;
import jrds.probe.IndexedProbe;
import lombok.Getter;
import lombok.Setter;

@ProbeBean({"node"})
public class Smokeping extends ExternalCmdProbe implements IndexedProbe {

    private static final Pattern colon = Pattern.compile(":");

    @Getter @Setter
    private String node = null;

    @Override
    public Boolean configure() {
        if (node == null) {
            node = getHost().getDnsName();
        }
        try {
            InetAddress addr = InetAddress.getByName(node);
            cmd = cmd +  " " + addr.getHostAddress();
            return true;
        } catch (UnknownHostException e) {
            log(Level.ERROR, "host name %s unknown", node);
            return false;
        }
    }

    @Override
    public Map<String, Number> getNewSampleValues() {
        String smallping = launchCmd();
        double[] values = colon.splitAsStream(smallping)
                               .filter(s -> ! "U".equals(s))
                               .mapToDouble(this::convertString)
                               .filter(d -> ! Double.isNaN(d))
                               .toArray();
        if (values.length > 20 || values.length == 0) {
            log(Level.ERROR, "smallping run failed: %s", smallping);
            return Collections.emptyMap();
        }
        Arrays.sort(values);
        Map<String, Number> valuesMap = new HashMap<>(30);
        for (int i = 1 ; i <= values.length; i++) {
            valuesMap.put("ping" + i, values[i - 1]);
        }
        valuesMap.put("loss", 20 - values.length);
        valuesMap.put("median", median(values));
        valuesMap.put("max", values[values.length -1]);
        return valuesMap;
    }

    private double convertString(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    // the list must have been already sorted
    private Double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        } else {
            int middle = values.length/2;
            if (values.length % 2 == 1) {
                return values[middle];
            } else {
                return (values[middle - 1] + values[middle + 1]) / 2.0;
            }
        }
    }

    @Override
    public String getIndexName() {
        return getNode();
    }

}
