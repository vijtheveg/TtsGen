package com.merabills;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class XmlStringExtractor {
    public static Map<String, String> extractMatchingStrings(File xmlFile, Pattern regexPattern) {

        Map<String, String> matchedStrings = new LinkedHashMap<>();
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            doc.getDocumentElement().normalize();
            NodeList stringNodes = doc.getElementsByTagName("string");
            for (int i = 0; i < stringNodes.getLength(); i++) {

                Node node = stringNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {

                    Element element = (Element) node;
                    String name = element.getAttribute("name");
                    String value = extractRawXmlContent(element);  // Preserve SSML tags

                    // Log the SSML string to confirm it's correct
                    System.out.println("SSML Text: " + value);  // Debugging line

                    if (regexPattern.matcher(name).matches())
                        matchedStrings.put(name, value);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse XML file: " + xmlFile.getAbsolutePath());
            e.printStackTrace();
        }

        return matchedStrings;
    }

    private static String extractRawXmlContent(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList childNodes = element.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node child = childNodes.item(j);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append("<").append(child.getNodeName()).append(" ");
                if (child.hasAttributes()) {
                    NamedNodeMap attributes = child.getAttributes();
                    for (int k = 0; k < attributes.getLength(); k++) {
                        Node attr = attributes.item(k);
                        sb.append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\" ");
                    }
                }
                sb.append(">").append(extractRawXmlContent((Element) child)).append("</").append(child.getNodeName()).append(">");
            }
        }
        return sb.toString();
    }

    // Method to get raw XML content
}
