package org.shalash.objectstoredb.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class FileUtils {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public static Map<Integer, String> extractPartsFromXML(String xml) {
        Map<Integer, String> parts = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList partNodes = doc.getElementsByTagName("Part");

            for (int i = 0; i < partNodes.getLength(); i++) {
                Element part = (Element) partNodes.item(i);

                int partNumber = Integer.parseInt(
                        part.getElementsByTagName("PartNumber").item(0).getTextContent()
                );

                String etag = part.getElementsByTagName("ETag")
                        .item(0)
                        .getTextContent()
                        .replace("\"", "")
                        .trim();

                parts.put(partNumber, etag);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return parts;
    }


    public static  String extractKey(HttpServletRequest request, String bucket) {
        String uri = request.getRequestURI();
        return uri.substring(("/" + bucket + "/").length());
    }
}
