package fi.fmi.avi.converter.iwxxm.bulletin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import fi.fmi.avi.converter.ConversionException;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.IssueList;
import fi.fmi.avi.converter.iwxxm.IWXXMNamespaceContext;
import fi.fmi.avi.converter.iwxxm.XMLSchemaInfo;
import fi.fmi.avi.model.AviationWeatherMessage;
import fi.fmi.avi.model.bulletin.MeteorologicalBulletin;

public class BulletinIWXXMStringSerializer<U extends AviationWeatherMessage, S extends MeteorologicalBulletin<U>>
        extends AbstractBulletinIWXXMSerializer<String, U, S> {
    private static final int INDENT_LENGTH = 2;

    @Override
    protected String aggregateAsBulletin(final Document collection, final List<Document> messages, final ConversionHints hints) throws ConversionException {
        try {
            final StringBuilder bulletinStr = new StringBuilder(renderDOMToString(collection, hints));
            BufferedReader br;
            final String bulletinElementFQN = IWXXMNamespaceContext.getDefaultPrefix("http://def.wmo.int/collect/2014") + ":MeteorologicalBulletin";
            final int bulletinElementStartIndex = bulletinStr.indexOf("<" + bulletinElementFQN);
            int offset = bulletinStr.indexOf(">", bulletinElementStartIndex) + 1;
            int baseIndentation = 0;
            while ((offset - baseIndentation) > 0 && bulletinStr.charAt(offset - baseIndentation) != '\n') {
                baseIndentation++;
            }
            baseIndentation += INDENT_LENGTH;
            final String metInfoElementFQN = IWXXMNamespaceContext.getDefaultPrefix("http://def.wmo.int/collect/2014") + ":meteorologicalInformation";
            for (final Document message : messages) {
                br = new BufferedReader(new StringReader(renderDOMToString(message, hints)));
                offset = appendNewLineWithIndent(offset, bulletinStr, baseIndentation);
                bulletinStr.insert(offset, "<");
                offset++;
                bulletinStr.insert(offset, metInfoElementFQN);
                offset += metInfoElementFQN.length();
                bulletinStr.insert(offset, ">");
                offset++;
                String line = br.readLine();
                while (line != null) {
                    if (!line.startsWith("<?xml")) {
                        offset = appendNewLineWithIndent(offset, bulletinStr, baseIndentation + INDENT_LENGTH);
                        bulletinStr.insert(offset, line);
                        offset += line.length();
                    }
                    line = br.readLine();
                }
                offset = appendNewLineWithIndent(offset, bulletinStr, baseIndentation);
                bulletinStr.insert(offset, "</");
                offset += 2;
                bulletinStr.insert(offset, metInfoElementFQN);
                offset += metInfoElementFQN.length();
                bulletinStr.insert(offset, ">");
                offset++;
                bulletinStr.insert(offset, '\n');
            }
            return bulletinStr.toString();
        } catch (final IOException e) {
            throw new ConversionException("Error reading input messages", e);
        }
    }

    @Override
    protected IssueList validate(final String output, final XMLSchemaInfo schemaInfo, final ConversionHints hints) throws ConversionException {
        final IssueList retval = new IssueList();
        try {
            final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            final DocumentBuilder db = dbf.newDocumentBuilder();
            final Document dom = db.parse(new InputSource(new StringReader(output)));
            retval.addAll(validateAgainstSchema(new StreamSource(new StringReader(output)), schemaInfo, hints));
            retval.addAll(validateAgainstIWXXMSchematron(dom, schemaInfo, hints));
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new ConversionException("Error validating produced bulletin", e);
        }
        return retval;
    }

    private int appendNewLineWithIndent(final int offset, final StringBuilder builder, final int intendation) {
        int os = offset;
        builder.insert(os, '\n');
        os++;
        for (int i = 0; i < intendation; i++) {
            builder.insert(os, ' ');
            os++;
        }
        return os;
    }
}