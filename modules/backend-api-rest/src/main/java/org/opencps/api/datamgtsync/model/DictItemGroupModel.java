//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.03.24 at 03:09:32 PM ICT 
//


package org.opencps.api.datamgtsync.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="createDate" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="modifiedDate" type="{http://www.w3.org/2001/XMLSchema}long"/>
 *         &lt;element name="collectionCode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="groupCode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="itemCode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "createDate",
    "modifiedDate",
    "collectionCode",
    "groupCode",
    "itemCode"
})
@XmlRootElement(name = "DictItemGroupModel")
public class DictItemGroupModel {

    protected long createDate;
    protected long modifiedDate;
    @XmlElement(required = true)
    protected String collectionCode;
    @XmlElement(required = true)
    protected String groupCode;
    @XmlElement(required = true)
    protected String itemCode;

    /**
     * Gets the value of the createDate property.
     * 
     */
    public long getCreateDate() {
        return createDate;
    }

    /**
     * Sets the value of the createDate property.
     * 
     */
    public void setCreateDate(long value) {
        this.createDate = value;
    }

    /**
     * Gets the value of the modifiedDate property.
     * 
     */
    public long getModifiedDate() {
        return modifiedDate;
    }

    /**
     * Sets the value of the modifiedDate property.
     * 
     */
    public void setModifiedDate(long value) {
        this.modifiedDate = value;
    }

    /**
     * Gets the value of the collectionCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCollectionCode() {
        return collectionCode;
    }

    /**
     * Sets the value of the collectionCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCollectionCode(String value) {
        this.collectionCode = value;
    }

    /**
     * Gets the value of the groupCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getGroupCode() {
        return groupCode;
    }

    /**
     * Sets the value of the groupCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setGroupCode(String value) {
        this.groupCode = value;
    }

    /**
     * Gets the value of the itemCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getItemCode() {
        return itemCode;
    }

    /**
     * Sets the value of the itemCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setItemCode(String value) {
        this.itemCode = value;
    }

}
