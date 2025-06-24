package com.merabills;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility class for extracting <string> entries from Android-style XML files.
 * Preserves raw inner XML (such as <sub> or SSML tags) inside the <string> value.
 */
public class XmlStringExtractor {
    private static boolean matchesAnyPattern(String key, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }
    /**
     * Parses the XML file and extracts string values whose 'name' attributes match the given regex.
     * It preserves inner XML content such as <sub alias="..."> or other SSML tags.
     *
     * @param xmlFile      XML file to parse (typically strings.xml)
     * @param regexPatterns  to match against the 'name' attribute
     * @return A map of string name → raw value (with inner XML intact)
     */
    public static Map<String, String> extractMatchingStrings(File xmlFile, List<Pattern> regexPatterns) {
        Map<String, String> matchedStrings = new LinkedHashMap<>();
        try {

            // Parse and normalize the XML DOM
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Get all <string> nodes
            NodeList stringNodes = doc.getElementsByTagName("string");
            for (int i = 0; i < stringNodes.getLength(); i++) {

                Node node = stringNodes.item(i);

                // Only process element nodes
                if (node.getNodeType() == Node.ELEMENT_NODE) {

                    Element element = (Element) node;
                    String name = element.getAttribute("name"); // <string name="...">
                    String value = extractRawXmlContent(element);  // Get full content including nested tags preserving SSML tags

                    // Match key against the regex pattern
                    if (matchesAnyPattern(name, regexPatterns))
                        matchedStrings.put(name, value);
                }
            }

            NodeList stringArrayNodes = doc.getElementsByTagName("string-array");
            for (int i = 0; i < stringArrayNodes.getLength(); i++) {
                Node node = stringArrayNodes.item(i);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String baseName = element.getAttribute("name");

                    NodeList items = element.getElementsByTagName("item");
                    for (int j = 0; j < items.getLength(); j++) {
                        Node itemNode = items.item(j);
                        String itemValue = itemNode.getTextContent();
                        String itemKey = baseName + "[" + j + "]";

                        if (matchesAnyPattern(itemKey, regexPatterns))
                            matchedStrings.put(itemKey, itemValue);
                    }
                }
            }


        } catch (Exception e) {
            System.err.println("Failed to parse XML file: " + xmlFile.getAbsolutePath());
            e.printStackTrace();
        }

        return matchedStrings;
    }

    /**
     * Recursively extracts the inner content of a <string> element including nested XML elements,
     * preserving tag names, attributes, and text — useful for SSML and <sub> alias tags.
     * <p>
     * Example:
     * Input: <string name="tip"><sub alias="example">eg.</sub></string>
     * Output: <sub alias="example">eg.</sub>
     *
     * @param element XML Element to extract content from
     * @return Raw inner XML string
     */
    private static String extractRawXmlContent(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList childNodes = element.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node child = childNodes.item(j);
            if (child.getNodeType() == Node.TEXT_NODE) {

                // Append plain text
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {

                // Handle nested elements (e.g., <sub alias="...">text</sub>)
                sb.append("<").append(child.getNodeName()).append(" ");

                // Append attributes if any
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
}
