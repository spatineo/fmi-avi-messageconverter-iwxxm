package fi.fmi.avi.converter.iwxxm.airmet;

import org.w3c.dom.Document;

import fi.fmi.avi.converter.ConversionException;
import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.iwxxm.XMLSchemaInfo;
import icao.iwxxm21.AIRMETType;

public class AIRMETIWXXMStringSerializer extends AbstractAIRMETIWXXMSerializer<String> {

    @Override
    protected String render(final AIRMETType airmet, final XMLSchemaInfo schemaInfo, final ConversionHints hints) throws ConversionException {
        final Document result = renderXMLDocument(airmet, schemaInfo, hints);
        return renderDOMToString(result, hints);
    }
}