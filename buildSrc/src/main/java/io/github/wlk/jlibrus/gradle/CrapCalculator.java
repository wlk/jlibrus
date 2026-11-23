package io.github.wlk.jlibrus.gradle;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Computes per-method CRAP scores from a JaCoCo XML report. */
public final class CrapCalculator {

    private CrapCalculator() {
    }

    public static List<MethodCrap> fromJacocoXml(InputStream xml) {
        Document document = parseSecurely(xml);
        NodeList classes = document.getElementsByTagName("class");
        List<MethodCrap> result = new ArrayList<>();
        for (int i = 0; i < classes.getLength(); i++) {
            Element clazz = (Element) classes.item(i);
            String className = clazz.getAttribute("name").replace('/', '.');
            NodeList methods = clazz.getElementsByTagName("method");
            for (int j = 0; j < methods.getLength(); j++) {
                result.add(toCrap(className, (Element) methods.item(j)));
            }
        }
        return List.copyOf(result);
    }

    public static List<MethodCrap> offenders(List<MethodCrap> methods, double threshold) {
        return methods.stream()
                .filter(m -> m.crap() >= threshold)
                .sorted(Comparator.comparingDouble(MethodCrap::crap).reversed())
                .toList();
    }

    private static MethodCrap toCrap(String className, Element method) {
        int[] complexity = counter(method, "COMPLEXITY");
        int[] instructions = counter(method, "INSTRUCTION");
        int total = instructions[0] + instructions[1];
        double coverage = total == 0 ? 1.0 : (double) instructions[1] / total;
        String line = method.getAttribute("line");
        return new MethodCrap(className, method.getAttribute("name"),
                line.isEmpty() ? 0 : number(method, "line"),
                complexity[0] + complexity[1], coverage);
    }

    /** Returns {missed, covered} for a counter type, or zeros if absent. */
    private static int[] counter(Element method, String type) {
        NodeList counters = method.getElementsByTagName("counter");
        for (int i = 0; i < counters.getLength(); i++) {
            Element counter = (Element) counters.item(i);
            if (type.equals(counter.getAttribute("type"))) {
                return new int[]{number(counter, "missed"), number(counter, "covered")};
            }
        }
        return new int[]{0, 0};
    }

    private static int number(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Malformed JaCoCo report: <" + element.getTagName() + " " + attribute
                    + "=\"" + value + "\">", e);
        }
    }

    private static Document parseSecurely(InputStream xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setValidating(false);
            return factory.newDocumentBuilder().parse(xml);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JaCoCo XML report", e);
        }
    }
}
