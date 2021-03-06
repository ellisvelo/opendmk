/* 
 * @(#)file      SnmpGenericObjectServer.java 
 * @(#)author    Sun Microsystems, Inc. 
 * @(#)version   1.14 
 * @(#)date      07/10/01 
 * 
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 2007 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU General
 * Public License Version 2 only ("GPL") or the Common Development and
 * Distribution License("CDDL")(collectively, the "License"). You may not use
 * this file except in compliance with the License. You can obtain a copy of the
 * License at http://opendmk.dev.java.net/legal_notices/licenses.txt or in the 
 * LEGAL_NOTICES folder that accompanied this code. See the License for the 
 * specific language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file found at
 *     http://opendmk.dev.java.net/legal_notices/licenses.txt
 * or in the LEGAL_NOTICES folder that accompanied this code.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.
 * 
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * 
 *       "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding
 * 
 *       "[Contributor] elects to include this software in this distribution
 *        under the [CDDL or GPL Version 2] license."
 * 
 * If you don't indicate a single choice of license, a recipient has the option
 * to distribute your version of this file under either the CDDL or the GPL
 * Version 2, or to extend the choice of license to its licensees as provided
 * above. However, if you add GPL Version 2 code and therefore, elected the
 * GPL Version 2 license, then the option applies only if the new code is made
 * subject to such option by the copyright holder.
 * 
 */ 

package com.sun.management.snmp.agent;


// java imports
//
import java.util.Vector;
import java.util.Enumeration;
import java.util.Iterator;

// jmx imports
//
import javax.management.AttributeList;
import javax.management.Attribute;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.RuntimeOperationsException;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpValue;
import com.sun.management.snmp.SnmpVarBind;
import com.sun.management.snmp.SnmpStatusException;


/**
 * <p>
 * This class is a utility class that transforms SNMP GET / SET requests
 * into standard JMX getAttributes() setAttributes() requests.
 * </p>
 *
 * <p>
 * The transformation relies on the metadata information provided by the
 * {@link com.sun.management.snmp.agent.SnmpGenericMetaServer} object which is
 * passed as the first parameter to every method. This SnmpGenericMetaServer
 * object is usually a Metadata object generated by <code>mibgen</code>.
 * </p>
 *
 * <p><b><i>
 * This class is used internally by mibgen generated metadata objects and
 * you should never need to use it directly.
 * </b></i></p>
 *
 * @since Java DMK 5.1
 **/

public class SnmpGenericObjectServer {

    // ----------------------------------------------------------------------
    // 
    //    Protected variables
    //
    // ----------------------------------------------------------------------

    /**
     * The MBean server through which the MBeans will be accessed.
     **/
    protected final MBeanServer server;

    // ----------------------------------------------------------------------
    //
    // Constructors
    //
    // ----------------------------------------------------------------------

    /**
     * Builds a new SnmpGenericObjectServer. Usually there will be a single
     * object of this type per MIB.
     *
     * @param server The MBeanServer in which the MBean accessed by this
     *               MIB are registered.
     **/
    public SnmpGenericObjectServer(MBeanServer server) {
	this.server = server;
    }

    /**
     * Execute an SNMP GET request. 
     *
     * <p>
     * This method first builds the list of attributes that need to be
     * retrieved from the MBean and then calls getAttributes() on the
     * MBean server. Then it updates the SnmpMibSubRequest with the values
     * retrieved from the MBean.
     * </p>
     * 
     * <p>
     * The SNMP metadata information is obtained through the given
     * <code>meta</code> object, which usually is an instance of a
     * <code>mibgen</code> generated class.
     * </p>
     *
     * <p><b><i>
     * This method is called internally by <code>mibgen</code> generated
     * objects and you should never need to call it directly.
     * </i></b></p>
     *
     * @param meta  The metadata object impacted by the subrequest
     * @param name  The ObjectName of the MBean impacted by this subrequest
     * @param req   The SNMP subrequest to execute on the MBean
     * @param depth The depth of the SNMP object in the OID tree.
     *
     * @exception SnmpStatusException whenever an SNMP exception must be 
     *      raised. Raising an exception will abort the request.<br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerGetException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     **/            
    public void get(SnmpGenericMetaServer meta, ObjectName name, 
		    SnmpMibSubRequest req, int depth) 
	throws SnmpStatusException {

	// java.lang.System.out.println(">>>>>>>>> GET " + name);

	final int           size     = req.getSize();
	final Object        data     = req.getUserData();
	final String[]      nameList = new String[size];
	final SnmpVarBind[] varList  = new SnmpVarBind[size];
	final long[]        idList   = new long[size];
	int   i = 0;

	for (Enumeration e=req.getElements(); e.hasMoreElements();) {
            final SnmpVarBind var= (SnmpVarBind) e.nextElement(); 
	    try {
		final long id = var.getOid().getOidArc(depth);
		nameList[i]   = meta.getAttributeName(id);
		varList[i]    = var;
		idList[i]     = id;

		// Check the access rights according to the MIB.
		// The MBean might be less restrictive (have a getter
		// while the MIB defines the variable as AFN)
		//
		meta.checkGetAccess(id,data);

		//java.lang.System.out.println(nameList[i] + " added.");
		i++;
            } catch(SnmpStatusException x) {
		//java.lang.System.out.println("exception for " + nameList[i]);
		//x.printStackTrace();
		req.registerGetException(var,x);
	    }
	}

	AttributeList result = null;
	int errorCode = SnmpStatusException.noSuchInstance;

	try {
	    result = server.getAttributes(name,nameList);
	} catch (InstanceNotFoundException f) {
	    //java.lang.System.out.println(name + ": instance not found.");
	    //f.printStackTrace();
	    result = new AttributeList();
	} catch (ReflectionException r) {
	    //java.lang.System.out.println(name + ": reflexion error.");
	    //r.printStackTrace();
	    result = new AttributeList();
	} catch (Exception x) {
	    result = new AttributeList();
	}


	final Iterator it = result.iterator();
	
	for (int j=0; j < i; j++) {
	    if (!it.hasNext()) {
		//java.lang.System.out.println(name + "variable[" + j + 
		//			     "] absent");
		final SnmpStatusException x =
		    new SnmpStatusException(errorCode);
		req.registerGetException(varList[j],x);
		continue;
	    }

	    final Attribute att = (Attribute) it.next();

	    while ((j < i) && (! nameList[j].equals(att.getName()))) {
		//java.lang.System.out.println(name + "variable[" +j + 
		//			     "] not found");
		final SnmpStatusException x =
		    new SnmpStatusException(errorCode);
		req.registerGetException(varList[j],x);
		j++;
	    }

	    if ( j == i) break;
	    
	    try {
		varList[j].setSnmpValue(
		    meta.buildSnmpValue(idList[j],att.getValue()));
	    } catch (SnmpStatusException x) {
		req.registerGetException(varList[j],x);
	    }
	    //java.lang.System.out.println(att.getName() + " retrieved.");
	}
	//java.lang.System.out.println(">>>>>>>>> END GET");
    }

    /**
     * Get the value of an SNMP variable.
     *
     * <p><b><i>
     * You should never need to use this method directly.
     * </i></b></p>
     *
     * @param meta  The impacted metadata object
     * @param name  The ObjectName of the impacted MBean
     * @param id    The OID arc identifying the variable we're trying to set.
     * @param data  User contextual data allocated through the
     *        {@link com.sun.management.snmp.agent.SnmpUserDataFactory}
     *
     * @return The value of the variable.
     *
     * @exception SnmpStatusException whenever an SNMP exception must be 
     *      raised. Raising an exception will abort the request. <br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerGetException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     **/            
    public SnmpValue get(SnmpGenericMetaServer meta, ObjectName name, 
			 long id, Object data) 
	throws SnmpStatusException {
	final String attname = meta.getAttributeName(id);
	Object result = null;

	try {
	    result = server.getAttribute(name,attname);
	} catch (MBeanException m) {
	    Exception t = m.getTargetException();
	    if (t instanceof SnmpStatusException) 
		throw (SnmpStatusException) t;
	    throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
	} catch (Exception e) {
	    throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
	}

	return meta.buildSnmpValue(id,result);
    }

    /**
     * Execute an SNMP SET request. 
     *
     * <p>
     * This method first builds the list of attributes that need to be
     * set on the MBean and then calls setAttributes() on the
     * MBean server. Then it updates the SnmpMibSubRequest with the new 
     * values retrieved from the MBean.
     * </p>
     * 
     * <p>
     * The SNMP metadata information is obtained through the given
     * <code>meta</code> object, which usually is an instance of a
     * <code>mibgen</code> generated class.
     * </p>
     *
     * <p><b><i>
     * This method is called internally by <code>mibgen</code> generated
     * objects and you should never need to call it directly.
     * </i></b></p>
     *
     * @param meta  The metadata object impacted by the subrequest
     * @param name  The ObjectName of the MBean impacted by this subrequest
     * @param req   The SNMP subrequest to execute on the MBean
     * @param depth The depth of the SNMP object in the OID tree.
     *
     * @exception SnmpStatusException whenever an SNMP exception must be 
     *      raised. Raising an exception will abort the request. <br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerGetException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     **/            
    public void set(SnmpGenericMetaServer meta, ObjectName name, 
		    SnmpMibSubRequest req, int depth) 
	throws SnmpStatusException {

	final int size               = req.getSize();
	final AttributeList attList  = new AttributeList(size);
	final String[]      nameList = new String[size];
	final SnmpVarBind[] varList  = new SnmpVarBind[size];
	final long[]        idList   = new long[size];
	int   i = 0;

	for (Enumeration e=req.getElements(); e.hasMoreElements();) {
            final SnmpVarBind var= (SnmpVarBind) e.nextElement(); 
	    try {
		final long id = var.getOid().getOidArc(depth);
		final String attname = meta.getAttributeName(id);
		final Object attvalue= 
		    meta.buildAttributeValue(id,var.getSnmpValue());
		final Attribute att = new Attribute(attname,attvalue);
		attList.add(att);
		nameList[i]   = attname;
		varList[i]    = var;
		idList[i]     = id;
		i++;
            } catch(SnmpStatusException x) {
		req.registerSetException(var,x);
	    }
	}

	AttributeList result = null;
	int errorCode = SnmpStatusException.noAccess;

	try {
	    result = server.setAttributes(name,attList);
	} catch (InstanceNotFoundException f) {
	    result = new AttributeList();
	    errorCode = SnmpStatusException.snmpRspInconsistentName;
	} catch (ReflectionException r) {
	    errorCode = SnmpStatusException.snmpRspInconsistentName;
	    result = new AttributeList();
	} catch (Exception x) {
	    result = new AttributeList();
	}

	final Iterator it = result.iterator();
	
	for (int j=0; j < i; j++) {
	    if (!it.hasNext()) {
		final SnmpStatusException x =
		    new SnmpStatusException(errorCode);
		req.registerSetException(varList[j],x);
		continue;
	    }

	    final Attribute att = (Attribute) it.next();

	    while ((j < i) && (! nameList[j].equals(att.getName()))) {
		final SnmpStatusException x =
		    new SnmpStatusException(SnmpStatusException.noAccess);
		req.registerSetException(varList[j],x);
		j++;
	    }

	    if ( j == i) break;
	    
	    try {
		varList[j].setSnmpValue(
		    meta.buildSnmpValue(idList[j],att.getValue()));
	    } catch (SnmpStatusException x) {
		req.registerSetException(varList[j],x);
	    }
	    
	}
    }

    /**
     * Set the value of an SNMP variable.
     *
     * <p><b><i>
     * You should never need to use this method directly.
     * </i></b></p>
     *
     * @param meta  The impacted metadata object
     * @param name  The ObjectName of the impacted MBean
     * @param x     The new requested SnmpValue
     * @param id    The OID arc identifying the variable we're trying to set.
     * @param data  User contextual data allocated through the
     *        {@link com.sun.management.snmp.agent.SnmpUserDataFactory}
     *
     * @return The new value of the variable after the operation.
     *
     * @exception SnmpStatusException whenever an SNMP exception must be 
     *      raised. Raising an exception will abort the request. <br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerSetException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     **/            
    public SnmpValue set(SnmpGenericMetaServer meta, ObjectName name, 
			 SnmpValue x, long id, Object data) 
	throws SnmpStatusException {
	final String attname = meta.getAttributeName(id);
	final Object attvalue= 
	    meta.buildAttributeValue(id,x);
	final Attribute att = new Attribute(attname,attvalue);

	Object result = null;

	try {
	    server.setAttribute(name,att);
	    result = server.getAttribute(name,attname);
	} catch(InvalidAttributeValueException iv) {
	    throw new 
		SnmpStatusException(SnmpStatusException.snmpRspWrongValue);
	} catch (InstanceNotFoundException f) {
	    throw new 
		SnmpStatusException(SnmpStatusException.snmpRspInconsistentName);
	} catch (ReflectionException r) {
	    throw new 
		SnmpStatusException(SnmpStatusException.snmpRspInconsistentName);
	} catch (MBeanException m) {
	    Exception t = m.getTargetException();
	    if (t instanceof SnmpStatusException) 
		throw (SnmpStatusException) t;
	    throw new 
		SnmpStatusException(SnmpStatusException.noAccess);
	} catch (Exception e) {
	    throw new 
		SnmpStatusException(SnmpStatusException.noAccess);
	}

	return meta.buildSnmpValue(id,result);
    }

    /**
     * Checks whether an SNMP SET request can be successfully performed. 
     *
     * <p>
     * For each variable in the subrequest, this method calls 
     * checkSetAccess() on the meta object, and then tries to invoke the
     * check<i>AttributeName</i>() method on the MBean. If this method
     * is not defined then it is assumed that the SET won't fail.
     * </p>
     *
     * <p><b><i>
     * This method is called internally by <code>mibgen</code> generated
     * objects and you should never need to call it directly.
     * </i></b></p>
     *
     * @param meta  The metadata object impacted by the subrequest
     * @param name  The ObjectName of the MBean impacted by this subrequest
     * @param req   The SNMP subrequest to execute on the MBean
     * @param depth The depth of the SNMP object in the OID tree.
     *
     * @exception SnmpStatusException if the requested SET operation must
     *      be rejected. Raising an exception will abort the request. <br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerCheckException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     *
     **/            
    public void check(SnmpGenericMetaServer meta, ObjectName name, 
		      SnmpMibSubRequest req, int depth) 
	throws SnmpStatusException {

	final Object data = req.getUserData();

	for (Enumeration e=req.getElements(); e.hasMoreElements();) {
            final SnmpVarBind var= (SnmpVarBind) e.nextElement(); 
	    try {
		final long id = var.getOid().getOidArc(depth);
		// call meta.check() here, and meta.check will call check()
		check(meta,name,var.getSnmpValue(),id,data);
            } catch(SnmpStatusException x) {
		req.registerCheckException(var,x);
	    }
	}
    }

    /**
     * Checks whether a SET operation can be performed on a given SNMP
     * variable.
     *
     * @param meta  The impacted metadata object
     * @param name  The ObjectName of the impacted MBean
     * @param x     The new requested SnmpValue
     * @param id    The OID arc identifying the variable we're trying to set.
     * @param data  User contextual data allocated through the
     *        {@link com.sun.management.snmp.agent.SnmpUserDataFactory}
     *
     * <p>
     * This method calls checkSetAccess() on the meta object, and then 
     * tries to invoke the check<i>AttributeName</i>() method on the MBean. 
     * If this method is not defined then it is assumed that the SET 
     * won't fail.
     * </p>
     *
     * <p><b><i>
     * This method is called internally by <code>mibgen</code> generated
     * objects and you should never need to call it directly.
     * </i></b></p>
     *
     * @exception SnmpStatusException if the requested SET operation must
     *      be rejected. Raising an exception will abort the request. <br>
     *      Exceptions should never be raised directly, but only by means of 
     * <code>
     * req.registerCheckException(<i>VariableId</i>,<i>SnmpStatusException</i>)
     * </code>
     *
     **/
    // XXX xxx ZZZ zzz Maybe we should go through the MBeanInfo here?
    public void check(SnmpGenericMetaServer meta, ObjectName name, 
		      SnmpValue x, long id, Object data) 
	throws SnmpStatusException {
	
	meta.checkSetAccess(x,id,data);
	try {
	    final String attname = meta.getAttributeName(id);
	    final Object attvalue= meta.buildAttributeValue(id,x);
	    final  Object[] params = new Object[1];
	    final  String[] signature = new String[1];

	    params[0]    = attvalue;
	    signature[0] = attvalue.getClass().getName();
	    server.invoke(name,"check"+attname,params,signature);

	} catch( SnmpStatusException e) {
	    throw e;  
	} 
	catch (InstanceNotFoundException i) {
	    throw new 
		SnmpStatusException(SnmpStatusException.snmpRspInconsistentName);
	} catch (ReflectionException r) {
	    // checkXXXX() not defined => do nothing
	} catch (MBeanException m) {
	    Exception t = m.getTargetException();
	    if (t instanceof SnmpStatusException) 
		throw (SnmpStatusException) t;
	    throw new SnmpStatusException(SnmpStatusException.noAccess);
	} catch (Exception e) {
	    throw new 
		SnmpStatusException(SnmpStatusException.noAccess);
	}
    }

    public void registerTableEntry(SnmpMibTable meta, SnmpOid rowOid,
				   ObjectName objname, Object entry) 
	throws SnmpStatusException {
        if (objname == null)
           throw new 
	     SnmpStatusException(SnmpStatusException.snmpRspInconsistentName);
        try  {
            if (entry != null && !server.isRegistered(objname)) 
                server.registerMBean(entry, objname);
	} catch (InstanceAlreadyExistsException e) {
            throw new 
	      SnmpStatusException(SnmpStatusException.snmpRspInconsistentName);
	} catch (MBeanRegistrationException e) {
            throw new SnmpStatusException(SnmpStatusException.snmpRspNoAccess);
	} catch (NotCompliantMBeanException e) {
            throw new SnmpStatusException(SnmpStatusException.snmpRspGenErr);
	} catch (RuntimeOperationsException e) {
            throw new SnmpStatusException(SnmpStatusException.snmpRspGenErr);
        } catch(Exception e) {
            throw new SnmpStatusException(SnmpStatusException.snmpRspGenErr);
        }
    }

}
