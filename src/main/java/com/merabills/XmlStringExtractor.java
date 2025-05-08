package com.merabills;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                    String value = element.getTextContent();

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
}
