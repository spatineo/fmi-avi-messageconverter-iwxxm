package fi.fmi.avi.converter.iwxxm.taf;

import org.w3c.dom.Document;

import fi.fmi.avi.converter.ConversionException;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.iwxxm.XMLSchemaInfo;
import icao.iwxxm21.TAFType;

/**
 * Specialization of {@link AbstractTAFIWXXMSerializer} for String output.
 */
public class TAFIWXXMStringSerializer extends AbstractTAFIWXXMSerializer<String> {

    @Override
    protected String render(TAFType taf, final XMLSchemaInfo schemaInfo, ConversionHints hints) throws ConversionException {
        Document result = renderXMLDocument(taf, schemaInfo, hints);
        return renderDOMToString(result, hints);
    }

}
