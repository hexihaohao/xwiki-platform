package org.xwiki.platform.patchservice.impl;

import java.io.InputStream;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xwiki.platform.patchservice.api.Position;
import org.xwiki.platform.patchservice.api.RWOperation;

import com.xpn.xwiki.XWikiException;

public abstract class AbstractOperationImpl implements RWOperation
{
    public static final String NODE_NAME = "operation";

    public static final String OPERATION_TYPE_ATTRIBUTE_NAME = "type";

    public static final String TEXT_NODE_NAME = "text";

    public static final String OBJECT_NODE_NAME = "object";

    public static final String OBJECT_TYPE_ATTRIBUTE_NAME = OPERATION_TYPE_ATTRIBUTE_NAME;

    public static final String OBJECT_NUMBER_ATTRIBUTE_NAME = "number";

    public static final String CLASS_NODE_NAME = "class";

    public static final String CLASS_NAME_ATTRIBUTE_NAME = "name";

    public static final String PROPERTY_NODE_NAME = "property";

    public static final String PROPERTY_NAME_ATTRIBUTE_NAME = CLASS_NAME_ATTRIBUTE_NAME;

    public static final String PROPERTY_TYPE_ATTRIBUTE_NAME = OPERATION_TYPE_ATTRIBUTE_NAME;

    public static final String PROPERTY_VALUE_ATTRIBUTE_NAME = "value";

    public static final String ATTACHMENT_NODE_NAME = "attachment";

    private String type;

    /**
     * {@inheritDoc}
     */
    public boolean addObject(String objectClass)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean createType(String className, String propertyName, String propertyType,
        Map properties)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean delete(String text, Position position)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean deleteAttachment(String name)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean deleteFromProperty(String objectClass, int index, String property,
        String text, Position position)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean deleteObject(String objectClass, int index)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean deleteType(String className, String propertyName)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean insert(String text, Position position)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean insertInProperty(String objectClass, int index, String property, String text,
        Position position)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean modifyType(String className, String propertyName, Map properties)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setAttachment(InputStream is)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setObjectProperty(String objectClass, int index, String propertyName,
        String value)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean setProperty(String property, String value)
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    public String getType()
    {
        return this.type;
    }

    public Element createOperationNode(Document doc)
    {
        Element xmlNode = doc.createElement(NODE_NAME);
        xmlNode.setAttribute(OPERATION_TYPE_ATTRIBUTE_NAME, getType());
        return xmlNode;
    }

    public String getOperationType(Element e)
    {
        return e.getAttribute(OPERATION_TYPE_ATTRIBUTE_NAME);
    }

    public Element createTextNode(String content, Document doc)
    {
        Element xmlNode = doc.createElement(TEXT_NODE_NAME);
        xmlNode.setTextContent(content);
        return xmlNode;
    }

    public Element getTextNode(Element e)
    {
        return (Element) e.getElementsByTagName(TEXT_NODE_NAME).item(0);
    }

    public String getTextValue(Element e)
    {
        return getTextNode(e).getTextContent();
    }

    public Element createObjectNode(String className, Document doc)
    {
        return createObjectNode(className, -1, doc);
    }

    public Element createObjectNode(String className, int number, Document doc)
    {
        Element xmlNode = doc.createElement(OBJECT_NODE_NAME);
        xmlNode.setAttribute(OBJECT_TYPE_ATTRIBUTE_NAME, className);
        if (number >= 0) {
            xmlNode.setAttribute(OBJECT_NUMBER_ATTRIBUTE_NAME, number + "");
        }
        return xmlNode;
    }

    public Element getObjectNode(Element e)
    {
        return (Element) e.getElementsByTagName(OBJECT_NODE_NAME).item(0);
    }

    public String getObjectClassname(Element e)
    {
        return getObjectNode(e).getAttribute(OBJECT_TYPE_ATTRIBUTE_NAME);
    }

    public int getObjectNumber(Element e)
    {
        try {
            return Integer.parseInt(getObjectNode(e).getAttribute(OBJECT_NUMBER_ATTRIBUTE_NAME));
        } catch (Exception ex) {
            return -1;
        }
    }

    public Element createPropertyNode(String propertyName, Document doc)
    {
        return createPropertyNode(propertyName, null, doc);
    }

    public Element createPropertyNode(String propertyName, String value, Document doc)
    {
        Element xmlNode = doc.createElement(PROPERTY_NODE_NAME);
        xmlNode.setAttribute(PROPERTY_NAME_ATTRIBUTE_NAME, propertyName);
        if (value != null) {
            xmlNode.setAttribute(PROPERTY_VALUE_ATTRIBUTE_NAME, value);
        }
        return xmlNode;
    }

    public Element createClassPropertyNode(String propertyName, String propertyType, Document doc)
    {
        Element xmlNode = doc.createElement(PROPERTY_NODE_NAME);
        xmlNode.setAttribute(PROPERTY_NAME_ATTRIBUTE_NAME, propertyName);
        xmlNode.setAttribute(PROPERTY_TYPE_ATTRIBUTE_NAME, propertyType);
        return xmlNode;
    }

    public Element getPropertyNode(Element e)
    {
        return (Element) e.getElementsByTagName(PROPERTY_NODE_NAME).item(0);
    }

    public String getPropertyName(Element e)
    {
        return getPropertyNode(e).getAttribute(PROPERTY_NAME_ATTRIBUTE_NAME);
    }

    public String getPropertyType(Element e)
    {
        return getPropertyNode(e).getAttribute(PROPERTY_TYPE_ATTRIBUTE_NAME);
    }

    public String getPropertyValue(Element e)
    {
        Element propertyNode = getPropertyNode(e);
        return (propertyNode != null) ? propertyNode.getAttribute(PROPERTY_VALUE_ATTRIBUTE_NAME)
            : null;
    }

    public Element createClassNode(String className, Document doc)
    {
        Element xmlNode = doc.createElement(CLASS_NODE_NAME);
        xmlNode.setAttribute(CLASS_NAME_ATTRIBUTE_NAME, className);
        return xmlNode;
    }

    public Element getClassNode(Element e)
    {
        return (Element) e.getElementsByTagName(CLASS_NODE_NAME).item(0);
    }

    public String getClassName(Element e)
    {
        return getClassNode(e).getAttribute(CLASS_NAME_ATTRIBUTE_NAME);
    }

    public Position loadPositionNode(Element e) throws XWikiException
    {
        Position position = new PositionImpl();
        position.fromXml((Element) e.getElementsByTagName(PositionImpl.NODE_NAME).item(0));
        return position;
    }
}
