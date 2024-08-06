package jrds.smokeping.probe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.event.Level;

import jrds.factories.ProbeBean;
import jrds.probe.ExternalCmdProbe;
import jrds.probe.IndexedProbe;
import lombok.Getter;
import lombok.Setter;

@ProbeBean({"node"})
public class Smokeping extends ExternalCmdProbe implements IndexedProbe {

    @Getter @Setter
    private String node = null;

    private Pattern separator;
    private Function<String, double[]> parse;

    @Override
    public Boolean configure() {
        if (node == null) {
            node = getHost().getDnsName();
        }
        if (! cmd.endsWith("fping")) {
            separator = Pattern.compile(":");
            parse = this::parseSmallping;
            try {
                InetAddress addr = InetAddress.getByName(node);
                setArgsList(List.of(addr.getHostAddress()));
                return super.configure();
            } catch (UnknownHostException e) {
                log(Level.ERROR, "host name %s unknown", node);
                return false;
            }
        } else {
            separator = Pattern.compile(" ");
            parse = this::parseFping;
            setArgsList(List.of(/*"-b", "0",*/ "-C", "20", "-i", "0", "-p", "5", "-r", "0", "-N", "-q", node));
            return super.configure();
        }
    }

    @Override
    protected Map<String, Number> resolveSampleValues(String smallping) {
        double[] values = parse.apply(smallping);
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

    private double[] parseSmallping(String result) {
        return separator.splitAsStream(result)
                        .filter(s -> ! "U".equals(s) && ! "N".equals(s))
                        .mapToDouble(this::convertString)
                        .filter(d -> ! Double.isNaN(d))
                        .toArray();
    }

    private double[] parseFping(String result) {
        return separator.splitAsStream(result.substring(result.indexOf(':') + 2))
                        .filter(s -> ! "-".equals(s))
                        .mapToDouble(s -> convertString(s) / 1000)
                        .filter(d -> ! Double.isNaN(d))
                        .toArray();
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
