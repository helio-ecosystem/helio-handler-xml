package handlers;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import com.google.gson.JsonObject;

import helio.blueprints.DataHandler;

/**
 * This object implements the {@link DataHandler} interface allowing to handle
 * XML documents. It allows to reference data allocated in an XML document using
 * the standardized
 * <a href="https://www.w3.org/TR/1999/REC-xpath-19991116/">XPath</a>
 * expressions. This object can be configured with a {@link JsonObject} that
 * must contain the key 'iterator' which value is an XPath used to split the XML
 * document into sub-documents.
 * 
 * @author Andrea Cimmino
 *
 */
public class XmlHandler implements DataHandler {

	Logger logger = LoggerFactory.getLogger(XmlHandler.class);
	private static final String CONFIGURATION_KEY = "iterator";
	private String iterator;
	private final XPath XPATH = XPathFactory.newInstance().newXPath();

	/**
	 * This constructor creates an empty {@link XmlHandler} that will need to be
	 * configured using a valid {@link JsonObject}
	 */
	public XmlHandler() {
		super();
	}

	/**
	 * This constructor instantiates a valid {@link XmlHandler} with the provided
	 * iterator
	 * 
	 * @param iterator a valid XPath expression
	 */
	public XmlHandler(String iterator) {
		this.iterator = iterator;
	}

	@Override
	public List<String> iterator(String dataChunk) {
		List<String> queueOfresults = new ArrayList<>();
		if (dataChunk != null) {
			try {
				Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
						.parse(new InputSource(dataChunk));
				// 3. Compile XPath
				XPathExpression expr = XPATH.compile(iterator);

				// 4. Evaluate XPath in the document
				NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
				if (nodes.getLength() > 0) {
					// 5. Transform each resultant node into a string xml document
					for (int index = nodes.getLength() - 1; index >= 0; index--) {
						Node node = nodes.item(index);
						Document subXmlDocument = node.getOwnerDocument();
						DOMImplementationLS domImplLS = (DOMImplementationLS) subXmlDocument.getImplementation();
						LSSerializer serializer = domImplLS.createLSSerializer();
						String stringXmlDocument = serializer.writeToString(node);
						queueOfresults.add(stringXmlDocument);
					}
				} else {
					logger.warn("Given xPath expression does not match in the document");
				}
			} catch (Exception e) {
				logger.warn(e.toString());
			}
		}
		return queueOfresults;
	}

	@Override
	public List<String> filter(String filter, String dataChunk) {
		List<String> results = new ArrayList<>();
		try {
			// 2. Create XML document
			DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
			builder.setNamespaceAware(true);

			Document doc = builder.newDocumentBuilder().parse(new InputSource(new StringReader(dataChunk)));
			// 3. Compile XPath
			XPathExpression expr = XPATH.compile(filter);
			// 3. Evaluate XPath in the document
			results = extractInformation(expr, doc, filter, dataChunk);
		} catch (Exception e) {
			logger.error(e.toString());
		}
		return results;
	}

	public List<String> extractInformation(XPathExpression expr, Document doc, String filter, String dataChunk) {
		List<String> results = new ArrayList<>();
		try {

			NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			if (nodes.getLength() == 0) {
				String node = (String) expr.evaluateExpression(doc, String.class);
				results.add(node);
			} else {
				for (int index = 0; index < nodes.getLength(); index++)
					results.add(nodeToString(nodes.item(index), doc));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
//		if (nodes.getLength() == 0) {
//			logger.warn("xPath "+filter+" retrieved zero values from original document: "+dataChunk);
//		} else {
//			for (int index = nodes.getLength() - 1; index >= 0; index--)
//				results.add(nodes.item(0).getTextContent());
//
//		}

		return results;
	}

	private String nodeToString(Node node, Document doc) {
		StringWriter sw = new StringWriter();
		try {
			Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.transform(new DOMSource(node), new StreamResult(sw));
		} catch (TransformerException te) {
			te.printStackTrace();
			System.out.println("nodeToString Transformer Exception");
		}
		return sw.toString();
	}

	@Override
	public void configure(JsonObject configuration) {
		if (configuration.has(CONFIGURATION_KEY)) {
			iterator = configuration.get(CONFIGURATION_KEY).getAsString();
			if (iterator.isEmpty())
				throw new IllegalArgumentException(
						"XmlHandler needs to receive non empty value for the keey 'iterator'");
		} else {
			throw new IllegalArgumentException(
					"XmlHandler needs to receive json object with the mandatory key 'iterator'");
		}
	}

}
