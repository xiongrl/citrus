package com.consol.citrus.validation;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.w3c.dom.*;
import org.w3c.dom.ls.LSException;
import org.xml.sax.SAXException;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.*;
import com.consol.citrus.functions.FunctionRegistry;
import com.consol.citrus.functions.FunctionUtils;
import com.consol.citrus.message.Message;
import com.consol.citrus.util.XMLUtils;
import com.consol.citrus.variable.VariableUtils;

/**
 * Historical message validator. Using W3C DOM object validation.
 *
 * @author deppisch Christoph Deppisch Consol* Software GmbH 2007
 */
public class DefaultXMLMessageValidator implements XMLMessageValidator {
    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultXMLMessageValidator.class);
    
    @Autowired
    private FunctionRegistry functionRegistry;
    
    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.MessageValidator#validateMessage(com.consol.citrus.message.Message, com.consol.citrus.message.Message, java.util.Map)
     */
    public boolean validateMessage(Message expectedMessage, Message receivedMessage, Set<String> ignoreElements, TestContext context) throws TestSuiteException {
        try {
            log.info("Start XML tree validation");
            
            Document received = XMLUtils.parseMessagePayload(receivedMessage);
            Document source = XMLUtils.parseMessagePayload(expectedMessage);
            
            XMLUtils.stripWhitespaceNodes(received);
            XMLUtils.stripWhitespaceNodes(source);
            
            if (log.isDebugEnabled()) {
                log.debug("Received message:");
                log.debug(XMLUtils.serialize(received));
                log.debug("Source message:");
                log.debug(XMLUtils.serialize(source));
            }

            validateXmlTree(received, source, ignoreElements);

            log.info("XML tree validation finished successfully: All values OK");
                
            return true;
        } catch (ClassCastException e) {
            throw new TestSuiteException(e);
        } catch (DOMException e) {
            throw new TestSuiteException(e);
        } catch (LSException e) {
            throw new TestSuiteException(e);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Validation failed!", e);
        }
    }

    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.MessageValidator#validateMessageHeader(java.util.Map, java.util.Map)
     */
    public boolean validateMessageHeader(Map<String, String> expectedHeaderValues, Map<String, String> receivedHeaderValues, TestContext context) throws TestSuiteException {
        if (expectedHeaderValues == null) return false;
        if (expectedHeaderValues.isEmpty()) return true;
        
        log.info("Start message header validation");

        for (Entry<String, String> entry : expectedHeaderValues.entrySet()) {
            String headerName = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = null;
            
            if (VariableUtils.isVariableName(headerName)) {
                headerName = context.getVariable(headerName);
            } else if(functionRegistry.isFunction(headerName)) {
                headerName = FunctionUtils.resolveFunction(headerName, context);
            } 
            
            if (receivedHeaderValues.containsKey(headerName)) {
                actualValue = receivedHeaderValues.get(headerName);
            } else {
                throw new ValidationException("Validation failed: Header element '" + headerName + "' is missing");
            }

            if (VariableUtils.isVariableName(expectedValue)) {
                expectedValue = context.getVariable(expectedValue);
            } else if(functionRegistry.isFunction(expectedValue)) {
                expectedValue = FunctionUtils.resolveFunction(expectedValue, context);
            } 

            try {
                if(actualValue != null) {
                    Assert.isTrue(expectedValue != null, 
                            "Values not equal for header element '"
                                + headerName + "', expected '"
                                + actualValue + "' but was '"
                                + null + "'");
    
                    Assert.isTrue(actualValue.equals(expectedValue),
                            "Values not equal for header element '"
                                + headerName + "', expected '"
                                + actualValue + "' but was '"
                                + expectedValue + "'");
                } else {
                    Assert.isTrue(expectedValue == null || expectedValue.length() == 0, 
                            "Values not equal for header element '"
                                + headerName + "', expected '"
                                + null + "' but was '"
                                + expectedValue + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Validation failed:", e);
            }
            
            if(log.isDebugEnabled()) {
                log.debug("Validating header element: " + headerName + "='" + expectedValue + "': OK.");
            }
        }
        
        log.info("Validation of message headers finished successfully: All properties OK");
        
        return true;
    }
    
    public boolean validateMessageElements(Map<String, String> elements, Message receivedMessage, TestContext context) throws TestSuiteException {
        return validateMessageElements(elements, receivedMessage, null, context);
    }

    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.XMLMessageValidator#validateMessageElements(java.util.Map, org.w3c.dom.Document)
     */
    public boolean validateMessageElements(Map<String, String> validateElements, Message receivedMessage, NamespaceContext nsContext, TestContext context) throws TestSuiteException {
        if (validateElements == null) return false;
        if (validateElements.isEmpty()) return true;
        
        log.info("Start XML elements validation");

        for (Entry<String, String> entry : validateElements.entrySet()) {
            String elementPathExpression = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = null;
            
            if (VariableUtils.isVariableName(elementPathExpression)) {
                elementPathExpression = context.getVariable(elementPathExpression);
            } else if(functionRegistry.isFunction(elementPathExpression)) {
                elementPathExpression = FunctionUtils.resolveFunction(elementPathExpression, context);
            }
            
            Document received = XMLUtils.parseMessagePayload(receivedMessage);
            
            Node node;
            if (XMLUtils.isXPathExpression(elementPathExpression)) {
                node = XMLUtils.findNodeByXPath(received, elementPathExpression, nsContext);
            } else {
                node = XMLUtils.findNodeByName(received, elementPathExpression);
            }

            if (node == null) {
                throw new UnknownElementException("Element ' " + elementPathExpression + "' could not be found in DOM tree");
            }

            if (node.getNodeType() == Node.ELEMENT_NODE && node.getFirstChild() != null)
                actualValue = node.getFirstChild().getNodeValue();
            else //if (node.getNodeType() == Node.ATTRIBUTE_NODE)
                actualValue = node.getNodeValue();

            if (VariableUtils.isVariableName(expectedValue)) {
                expectedValue = context.getVariable(expectedValue);
            } else if(functionRegistry.isFunction(expectedValue)) {
                expectedValue = FunctionUtils.resolveFunction(expectedValue, context);
            }
            
            try {
                if(actualValue != null) {
                    Assert.isTrue(expectedValue != null, 
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + actualValue + "' but was '"
                                + null + "'");
    
                    Assert.isTrue(actualValue.equals(expectedValue),
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + actualValue + "' but was '"
                                + expectedValue + "'");
                } else {
                    Assert.isTrue(expectedValue == null || expectedValue.length() == 0, 
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + null + "' but was '"
                                + expectedValue + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Validation failed:", e);
            }
            
            if(log.isDebugEnabled()) {
                log.debug("Validating element: " + elementPathExpression + "='" + expectedValue + "': OK.");
            }
        }
        
        log.info("Validation of XML elements finished successfully: All elements OK");
        
        return true;
    }

    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.XMLMessageValidator#validateDTD(org.springframework.core.io.Resource, com.consol.citrus.message.Message)
     */
    public boolean validateDTD(Resource dtdResource, Message receivedMessage) throws TestSuiteException {
        //TODO implement this
        return false;
    }

    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.XMLMessageValidator#validateXMLSchema(org.springframework.core.io.Resource, com.consol.citrus.message.Message)
     */
    public boolean validateXMLSchema(Resource schemaResource, Message receivedMessage) throws TestSuiteException {
        if (schemaResource == null) {
            throw new NoRessourceException("No XML schema ressource defined!");
        }

        log.info("Validating received XML message schema with: " + schemaResource + " ...");

        try {
            // create a SchemaFactory capable of understanding WXS schemas
            final SchemaFactory factory = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();
            final Schema schema = factory.newSchema(new StreamSource(schemaResource.getInputStream()));
            final Validator validator = schema.newValidator();
            
            try {
                validator.validate(new DOMSource(XMLUtils.parseMessagePayload(receivedMessage)));
                log.info("Schema of received XML validated OK");
            } catch (SAXException e) {
                log.error("Schema of received XML document not valid in schema: "
                        + schemaResource.getFilename());
                throw new ValidationException(e);
            }
        } catch (IOException e) {
            throw new TestSuiteException(e);
        } catch (SAXException e) {
            throw new TestSuiteException(e);
        }

        return true;
    }

    /**
     * (non-Javadoc)
     * @see com.consol.citrus.validation.XMLMessageValidator#validateNamespaces(org.w3c.dom.Document, java.util.Map)
     */
    public boolean validateNamespaces(Map expectedNamespaces, Message receivedMessage) throws TestSuiteException {
        if (expectedNamespaces == null || expectedNamespaces.isEmpty()) {
            return true;
        }

        log.info("Start XML namespace validation");

        Document received = XMLUtils.parseMessagePayload(receivedMessage);
        
        Map foundNamespaces = XMLUtils.lookupNamespaces(received.getFirstChild());

        if (foundNamespaces.size() != expectedNamespaces.size()) {
            throw new ValidationException("Number of namespace declarations not equal for node " + XMLUtils.getNodesPathName(received.getFirstChild()) + " found " + foundNamespaces.size() + " expected " + expectedNamespaces.size());
        }

        for (Iterator iter = expectedNamespaces.entrySet().iterator(); iter.hasNext();) {
            Entry entry = (Entry) iter.next();
            String namespace = entry.getKey().toString();
            String url = (String)entry.getValue();

            if (foundNamespaces.containsKey(namespace)) {
                if (foundNamespaces.get(namespace).equals(url) == false) {
                    throw new ValidationException("Namespace '" + namespace + "' values not equal: found '" + foundNamespaces.get(namespace) + "' expected '" + url + "' in reference node " + XMLUtils.getNodesPathName(received.getFirstChild()));
                } else {
                    log.info("Validating namespace " + namespace + " value as expected " + url + " - value OK");
                }
            } else {
                throw new ValidationException("Missing namespace " + namespace + "(" + url + ") in node " + XMLUtils.getNodesPathName(received.getFirstChild()));
            }
        }

        log.info("XML namespace validation finished successfully: All values OK");

        return true;
    }

    private void validateXmlTree(Node received, Node source, Set<String> ignoreMessageElements) {
        switch(received.getNodeType()) {
            case Node.DOCUMENT_NODE:
                validateXmlTree(received.getFirstChild(), source.getFirstChild(), ignoreMessageElements);
                break;
            case Node.ELEMENT_NODE:
                doElement(received, source, ignoreMessageElements);
                break;
            case Node.TEXT_NODE:
                doText(received, source);
                break;
            case Node.ATTRIBUTE_NODE:
                throw new IllegalStateException();
            case Node.COMMENT_NODE:
                doComment(received, source);
                break;
            case Node.CDATA_SECTION_NODE:
                doText(received, source);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                doPI(received, source);
                break;
        }
    }
    
    private void doElement(Node received, Node source, Set<String> ignoreMessageElements) {
        //validate element name
        if(log.isDebugEnabled()) {
            log.debug("Validating element: " + received.getLocalName() + " (" + received.getNamespaceURI() + ")");
        }
        
        Assert.isTrue(received.getLocalName().equals(source.getLocalName()),
                "Element names not equal , expected '"
                    + source.getLocalName() + "' but was '"
                    + received.getLocalName() + "'");

        //validate element namespace
        if(log.isDebugEnabled()) {
            log.debug("Validating namespace for element: " + received.getLocalName());
        }

        if(received.getNamespaceURI() != null) {
            Assert.isTrue(source.getNamespaceURI() != null, 
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + received.getNamespaceURI() + "' but was '"
                        + null + "'");

            Assert.isTrue(received.getNamespaceURI().equals(source.getNamespaceURI()),
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + source.getNamespaceURI() + "' but was '"
                        + received.getNamespaceURI() + "'");
        } else {
            Assert.isTrue(source.getNamespaceURI() == null, 
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + null + "' but was '"
                        + source.getNamespaceURI() + "'");
        }

        if (isNodeIgnored(received, ignoreMessageElements)) {
            if(log.isDebugEnabled()) {
                log.debug("Element: '" + received.getLocalName() + "' is on ignore list - skipped validation");
            }
            return;
        }

        //work on attributes
        if(log.isDebugEnabled()) {
            log.debug("Validating attributes for element: " + received.getLocalName());
        }
        NamedNodeMap receivedAttr = received.getAttributes();
        NamedNodeMap sourceAttr = source.getAttributes();

        Assert.isTrue(countAttributes(receivedAttr) == countAttributes(sourceAttr),
                "Number of attributes not equal for element '"
                    + received.getLocalName() + "', expected "
                    + countAttributes(receivedAttr) + " but was "
                    + countAttributes(sourceAttr));

        for(int i = 0; i<receivedAttr.getLength(); i++) {
            doAttribute(received, receivedAttr.item(i), sourceAttr, ignoreMessageElements);
        }

        //work on child nodes
        NodeList receivedChilds = received.getChildNodes();
        NodeList sourceChilds = source.getChildNodes();

        Assert.isTrue(receivedChilds.getLength() == sourceChilds.getLength(),
                "Number of child elements not equal for element '"
                    + received.getLocalName() + "', expected "
                    + receivedChilds.getLength() + " but was "
                    + sourceChilds.getLength());

        for(int i = 0; i<receivedChilds.getLength(); i++) {
            this.validateXmlTree(receivedChilds.item(i), sourceChilds.item(i), ignoreMessageElements);
        }

        if(log.isDebugEnabled()) {
            log.debug("Validation successful for element: " + received.getLocalName() + " (" + received.getNamespaceURI() + ")");
        }
    }
    
    private void doText(Node received, Node source) {
        if(log.isDebugEnabled()) {
            log.debug("Validating node value for element: " + received.getParentNode());
        }
        
        if (received.getNodeValue() != null) {
            Assert.isTrue(source.getNodeValue() != null, 
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + received.getNodeValue().trim() + "' but was '"
                            + null + "'");
            
            Assert.isTrue(received.getNodeValue().trim().equals(source.getNodeValue().trim()),
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + received.getNodeValue().trim() + "' but was '"
                            + source.getNodeValue().trim() + "'");
        } else {
            Assert.isTrue(source.getNodeValue() == null, 
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + null + "' but was '"
                            + source.getNodeValue().trim() + "'");
        }
        
        if(log.isDebugEnabled()) {
            log.debug("Node value '" + received.getNodeValue().trim() + "': OK");
        }
    }

    private void doAttribute(Node element, Node received, NamedNodeMap sourceAttributes, Set<String> ignoreMessageElements) {
        if(received.getNodeName().startsWith("xmlns")) { return; }
        
        String receivedName = received.getLocalName();
        
        if(log.isDebugEnabled()) {
            log.debug("Validating attribute: " + receivedName + " (" + received.getNamespaceURI() + ")");
        }

        Node source = sourceAttributes.getNamedItemNS(received.getNamespaceURI(), receivedName);
        
        Assert.isTrue(source != null,
                "Attribute validation failed for element '"
                    + element.getLocalName() + "', unknown attribute "
                    + receivedName + " (" + received.getNamespaceURI() + ")");

        if (isAttributeIgnored(element, received, ignoreMessageElements)) {
            if(log.isDebugEnabled()) {
                log.debug("Attribute '" + receivedName + "' is on ignore list - skipped value validation");
            }
            return;
        }

        String receivedValue = received.getNodeValue();
        String sourceValue = source.getNodeValue();

        Assert.isTrue(receivedValue.equals(sourceValue),
                "Values not equal for attribute '"
                    + receivedName + "', expected '"
                    + receivedValue + "' but was '"
                    + sourceValue + "'");

        if(log.isDebugEnabled()) {
            log.debug("Attribute '" + receivedName + "'='" + receivedValue + "': OK");
        }
    }

    private void doComment(Node received, Node source) {
        log.info("Ignored comment node (" + received.getNodeValue() + ")");
    }

    private void doPI(Node received, Node source) {
        log.info("Ignored processing instruction (" + received.getLocalName() + "=" + received.getNodeValue() + ")");
    }

    /**
     * Counts the attributes, xmlns left out
     * @param attributesR attributesMap
     * @return count of attributes
     */
    private int countAttributes(NamedNodeMap attributesR) {
        int cntAttributes = 0;

        for (int i = 0; i < attributesR.getLength(); i++) {
            if (!attributesR.item(i).getNodeName().startsWith("xmlns"))
                cntAttributes++;
        }

        return cntAttributes;
    }

    /**
     * Checks wheather the current attribute is in ignoreValues map.
     * @param node the attribute node.
     * @return boolean flag to mark ignore
     */
    private boolean isAttributeIgnored(Node elementNode, Node attributeNode, Set ignoreMessageElements) throws TestSuiteException {
        if (ignoreMessageElements == null || ignoreMessageElements.isEmpty())
            return false;

        /** This is the faster version, but then the ignoreValue name must be
         * the full path name like: Numbers.NumberItem.AreaCode
         */
        if (ignoreMessageElements.contains(XMLUtils.getNodesPathName(elementNode) + "." + attributeNode.getNodeName())) {
            return true;
        }

        /** This is the slower version, but here the ignoreValues can be
         * the short path name like only: AreaCode
         *
         * If there are more nodes with the same short name,
         * the first one will match, eg. if there are:
         *      Numbers1.NumberItem.AreaCode
         *      Numbers2.NumberItem.AreaCode
         * And ignoreValues contains just: AreaCode
         * the only first Node: Numbers1.NumberItem.AreaCode will be ignored.
         */
        for (Iterator iter = ignoreMessageElements.iterator(); iter.hasNext();) {
            Node foundAttributeNode = XMLUtils.findNodeByName(elementNode.getOwnerDocument(), (String) iter.next());

            if (foundAttributeNode != null && attributeNode.isSameNode(foundAttributeNode)) {
                return true;
            }
        }

        /** This is the XPath version using XPath expressions in
         * ignoreValues to identify nodes to be ignored
         */
        for (Iterator iter = ignoreMessageElements.iterator(); iter.hasNext();) {
            String expression = (String) iter.next();
            if (XMLUtils.isXPathExpression(expression)) {
                Node foundAttributeNode = XMLUtils.findNodeByXPath(elementNode.getOwnerDocument(), expression);
                if (foundAttributeNode != null && foundAttributeNode.isSameNode(attributeNode)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Tests whether a node is within the <tt>ignoreValues</tt> set.
     * The <tt>ignoreValues</tt> can be the short path name like only:
     * <tt>AreaCode</tt> instead of: <tt>Numbers.NumberItem.AreaCode</tt>
     * <p>If there are more nodes with the same short name,
     * the first one will match, eg. if there are:
     * <pre><blockquote>Numbers1.NumberItem.AreaCode
     *Numbers2.NumberItem.AreaCode</blockquote></pre>
     * And <tt>ignoreValues</tt> contains just: <tt>AreaCode</tt>
     * <p>only the first Node: <tt>Numbers1.NumberItem.AreaCode</tt>
     * will be ignored.
     *
     * @param node The node the test.
     * @return true if <tt>node</tt> has to be ignored.
     */
    private boolean isNodeIgnored(final Node node, Set ignoreMessageElements) throws TestSuiteException {
        if (ignoreMessageElements == null || ignoreMessageElements.isEmpty())
            return false;

        /** This is the faster version, but then the ignoreValue name must be
         * the full path name like: Numbers.NumberItem.AreaCode
         */
        if (ignoreMessageElements.contains(XMLUtils.getNodesPathName(node))) {
            return true;
        }

        /** This is the slower version, but here the ignoreValues can be
         * the short path name like only: AreaCode
         *
         * If there are more nodes with the same short name,
         * the first one will match, eg. if there are:
         *      Numbers1.NumberItem.AreaCode
         *      Numbers2.NumberItem.AreaCode
         * And ignoreValues contains just: AreaCode
         * the only first Node: Numbers1.NumberItem.AreaCode will be ignored.
         */
        for (Iterator iter = ignoreMessageElements.iterator(); iter.hasNext();) {
            if (node == XMLUtils.findNodeByName(node.getOwnerDocument(), (String) iter.next()))
                return true;
        }

        /** This is the XPath version using XPath expressions in
         * ignoreValues to identify nodes to be ignored
         */
        for (Iterator iter = ignoreMessageElements.iterator(); iter.hasNext();) {
            String expression = (String) iter.next();
            if (XMLUtils.isXPathExpression(expression)) {
                Node foundNode = XMLUtils.findNodeByXPath(node.getOwnerDocument(), expression);

                if (foundNode != null && foundNode.isSameNode(node)) {
                    return true;
                }
            }
        }

        return false;
    }
}