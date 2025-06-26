package com.merabills;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Android string resource XML files that converts strings and string arrays
 * into Java objects for programmatic access.
 */
public class AndroidStringResourceParser {

    /**
     * Represents a string resource from strings.xml
     */
    public static class StringResource {
        private final String name;
        private final String value;
        private final boolean translatable;

        public StringResource(String name, String value, boolean translatable) {
            this.name = name;
            this.value = value;
            this.translatable = translatable;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public boolean isTranslatable() {
            return translatable;
        }

        @Override
        public String toString() {
            return String.format("StringResource{name='%s', value='%s', translatable=%s}",
                    name, value, translatable);
        }
    }

    /**
     * Represents a string array resource from strings.xml
     */
    public static class StringArrayResource {
        private final String name;
        private final List<String> items;
        private final boolean translatable;

        public StringArrayResource(String name, List<String> items, boolean translatable) {
            this.name = name;
            this.items = new ArrayList<>(items);
            this.translatable = translatable;
        }

        public String getName() {
            return name;
        }

        public List<String> getItems() {
            return new ArrayList<>(items);
        }

        public boolean isTranslatable() {
            return translatable;
        }

        @Override
        public String toString() {
            return String.format("StringArrayResource{name='%s', items=%s, translatable=%s}",
                    name, items, translatable);
        }
    }

    /**
     * Container for parsed string resources
     */
    public static class ParsedResources {
        private final List<StringResource> strings;
        private final List<StringArrayResource> stringArrays;

        public ParsedResources() {
            this.strings = new ArrayList<>();
            this.stringArrays = new ArrayList<>();
        }

        public List<StringResource> getStrings() {
            return strings;
        }

        public List<StringArrayResource> getStringArrays() {
            return stringArrays;
        }

        public void addString(StringResource string) {
            strings.add(string);
        }

        public void addStringArray(StringArrayResource stringArray) {
            stringArrays.add(stringArray);
        }

        @Override
        public String toString() {
            return String.format("ParsedResources{strings=%d, stringArrays=%d}",
                    strings.size(), stringArrays.size());
        }
    }

    /**
     * Processes escape sequences in Android string resources
     * Converts Java-style escape sequences to their actual characters
     *
     * @param input The raw string from XML
     * @return String with escape sequences converted
     */
    private static String processEscapeSequences(String input) {
        if (input == null) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        i++; // Skip next character
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case '\\':
                        result.append('\\');
                        i++;
                        break;
                    case '\"':
                        result.append('\"');
                        i++;
                        break;
                    case '\'':
                        result.append('\'');
                        i++;
                        break;
                    case 'b':
                        result.append('\b');
                        i++;
                        break;
                    case 'f':
                        result.append('\f');
                        i++;
                        break;
                    case 'u':
                        if (i + 5 < input.length()) {
                            try {
                                String hex = input.substring(i + 2, i + 6);
                                int codePoint = Integer.parseInt(hex, 16);
                                result.append((char) codePoint);
                                i += 5; // Skip u and 4 hex digits
                            } catch (NumberFormatException e) {
                                // Invalid unicode sequence, keep as is
                                result.append(c);
                            }
                        } else {
                            result.append(c);
                        }
                        break;
                    default:
                        // Unknown escape sequence, keep both characters
                        result.append(c);
                        break;
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Parses an Android string resource XML file and returns Java objects
     *
     * @param xmlFile The strings.xml file to parse
     * @return ParsedResources containing all string and string-array elements
     * @throws Exception if parsing fails
     */
    public static ParsedResources parseStringResources(File xmlFile) throws Exception {
        if (!xmlFile.exists() || !xmlFile.isFile()) {
            throw new IllegalArgumentException("File does not exist or is not a file: " + xmlFile.getPath());
        }

        ParsedResources resources = new ParsedResources();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();

            // Parse string elements
            NodeList stringNodes = document.getElementsByTagName("string");
            for (int i = 0; i < stringNodes.getLength(); i++) {
                Node node = stringNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String name = element.getAttribute("name");
                    String value = processEscapeSequences(element.getTextContent());
                    boolean translatable = !"false".equals(element.getAttribute("translatable"));

                    if (!name.isEmpty()) {
                        resources.addString(new StringResource(name, value, translatable));
                    }
                }
            }

            // Parse string-array elements
            NodeList arrayNodes = document.getElementsByTagName("string-array");
            for (int i = 0; i < arrayNodes.getLength(); i++) {
                Node node = arrayNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element arrayElement = (Element) node;
                    String name = arrayElement.getAttribute("name");
                    boolean translatable = !"false".equals(arrayElement.getAttribute("translatable"));

                    if (!name.isEmpty()) {
                        List<String> items = new ArrayList<>();
                        NodeList itemNodes = arrayElement.getElementsByTagName("item");

                        for (int j = 0; j < itemNodes.getLength(); j++) {
                            Node itemNode = itemNodes.item(j);
                            if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                                items.add(processEscapeSequences(itemNode.getTextContent()));
                            }
                        }

                        resources.addStringArray(new StringArrayResource(name, items, translatable));
                    }
                }
            }

        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new Exception("Failed to parse XML file: " + e.getMessage(), e);
        }

        return resources;
    }

}
