package org.apache.axis2.description;

import com.ibm.wsdl.extensions.soap.SOAPConstants;
import com.ibm.wsdl.util.xml.DOM2Writer;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.AddressingHelper;
import org.apache.axis2.addressing.wsdl.WSDL11ActionHelper;
import org.apache.axis2.util.XMLUtils;
import org.apache.axis2.wsdl.SOAPHeaderMessage;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.axis2.wsdl.WSDLUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.policy.Policy;
import org.apache.ws.policy.PolicyConstants;
import org.apache.ws.policy.PolicyReference;
import org.apache.ws.policy.util.DOMPolicyReader;
import org.apache.ws.policy.util.PolicyFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.wsdl.*;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.extensions.soap.*;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLLocator;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *
 */

public class WSDL11ToAxisServiceBuilder extends WSDLToAxisServiceBuilder {

    protected static final Log log =
            LogFactory.getLog(WSDL11ToAxisServiceBuilder.class);

    private String portName;

    private static final String BINDING = "Binding";

    private static final String SERVICE = "Service";

    private static final String PORT = "Port";

    private static final String PORT_TYPE = "PortType";

    private static final String PORT_TYPE_OPERATION = "PortType.Operation";

    private static final String PORT_TYPE_OPERATION_INPUT = "PortType.Operation.Input";

    private static final String PORT_TYPE_OPERATION_OUTPUT = "PortType.Operation.Output";

    private static final String PORT_TYPE_OPERATION_FAULT = "PortType.Operation.Fault";

    private static final String BINDING_OPERATION = "Binding.Operation";

    private static final String BINDING_OPERATION_INPUT = "Binding.Operation.Input";

    private static final String BINDING_OPERATION_OUTPUT = "Binding.Operation.Output";

    private Definition wsdl4jDefinition = null;

    private WSDLLocator customWSLD4JResolver;

    public static final String RPC_STYLE = "rpc";
    public static final String DOCUMENT_STYLE = "document";

    /**
     * Keeps a list of processable operations
     * initiate to an empty list
     */
    private List wrappableOperations = new ArrayList();
    public static final String WRAPPED_OUTPUTNAME_SUFFIX = "Response";
    public static final String XML_NAMESPACE_URI = "http://www.w3.org/2000/xmlns/";
    public static final String NAMESPACE_DECLARATION_PREFIX = "xmlns:";

    private static int prefixCounter = 0;
    public static final String NAMESPACE_URI = "namespace";
    public static final String TRAGET_NAMESPACE = "targetNamespace";

    /**
     * constructor taking in the service name and the port name
     *
     * @param in
     * @param serviceName
     * @param portName
     */
    public WSDL11ToAxisServiceBuilder(InputStream in, QName serviceName,
                                      String portName) {
        super(in, serviceName);
        this.portName = portName;
    }

    /**
     * @param def
     * @param serviceName
     * @param portName
     */
    public WSDL11ToAxisServiceBuilder(Definition def, QName serviceName,
                                      String portName) {
        super(null, serviceName);
        this.wsdl4jDefinition = def;
        this.portName = portName;
    }

    /**
     * @param in
     * @param service
     */
    public WSDL11ToAxisServiceBuilder(InputStream in, AxisService service) {
        super(in, service);
    }

    /**
     * @param in
     */
    public WSDL11ToAxisServiceBuilder(InputStream in) {
        this(in, null, null);
    }

    /**
     * sets a custem WSDL4J locator
     *
     * @param customWSLD4JResolver
     */
    public void setCustomWSLD4JResolver(WSDLLocator customWSLD4JResolver) {
        this.customWSLD4JResolver = customWSLD4JResolver;
    }

    /**
     * populates a given service
     * This is the only publicly accessible method in this class
     *
     * @return
     * @throws AxisFault
     */
    public AxisService populateService() throws AxisFault {
        try {
            if (wsdl4jDefinition == null) {
                wsdl4jDefinition = readInTheWSDLFile(in);
            }
            // Setting wsdl4jdefintion to axisService , so if some one want
            // to play with it he can do that by getting the parameter
            Parameter wsdldefintionParamter = new Parameter();
            wsdldefintionParamter.setName(WSDLConstants.WSDL_4_J_DEFINITION);
            wsdldefintionParamter.setValue(wsdl4jDefinition);
            axisService.addParameter(wsdldefintionParamter);

            if (wsdl4jDefinition == null) {
                return null;
            }
            //setting target name space
            axisService.setTargetNamespace(wsdl4jDefinition
                    .getTargetNamespace());

            //adding ns in the original WSDL
            processPoliciesInDefintion(wsdl4jDefinition);

            //process the imports
            processImports(wsdl4jDefinition);
            axisService.setNameSpacesMap(wsdl4jDefinition.getNamespaces());

            Types wsdl4jTypes = wsdl4jDefinition.getTypes();

            Map schemaMap = populateSchemaMap(wsdl4jTypes);
            Binding binding = findBinding(wsdl4jDefinition);

            // create new Schema extensions element for wrapping
            //(if its present)
            Element[] schemaElements = generateWrapperSchema(schemaMap, binding);

            //we might have modified the schemas by now so the addition should
            //happen here
            if (null != wsdl4jTypes) {
                this.copyExtensibleElements(
                        wsdl4jTypes.getExtensibilityElements(),
                        wsdl4jDefinition,
                        axisService,
                        TYPES);
            }

            //add the newly created schemas
            if (schemaElements != null && schemaElements.length > 0) {
                for (int i = 0; i < schemaElements.length; i++) {
                    Element schemaElement = schemaElements[i];
                    if (schemaElement != null) {
                        axisService
                                .addSchema(getXMLSchema(schemaElement, null));
                    }
                }
            }
            // copy the documentation element contentto the description
            Element documentationElement = wsdl4jDefinition.getDocumentationElement();
            if (documentationElement != null) {
                Node firstChild = documentationElement.getFirstChild();
                String serviceDes;
                if (firstChild.getNodeType() == Node.TEXT_NODE) {
                    serviceDes = firstChild.getNodeValue();
                } else {
                    serviceDes = DOM2Writer.nodeToString(firstChild);
                }
                axisService.setServiceDescription(serviceDes);
            }
            processBinding(binding, wsdl4jDefinition);

            return axisService;
        } catch (WSDLException e) {
            throw new AxisFault(e);
        } catch (Exception e) {
            throw new AxisFault(e);
        }
    }

    /**
     * Populate a map of targetNamespace vs DOM schema element
     * This is used to grab the correct schema element when adding
     * a new element
     *
     * @param wsdl4jTypes
     */

    private Map populateSchemaMap(Types wsdl4jTypes) {
        Map schemaMap = new HashMap();
        if (wsdl4jTypes!=null){
            List typesExtensibilityElements = wsdl4jTypes.getExtensibilityElements();
            for (int i = 0; i < typesExtensibilityElements.size(); i++) {
                Object o = typesExtensibilityElements.get(i);
                if (o instanceof Schema) {
                    Schema s = (Schema) o;
                    String targetNamespace = s.getElement().getAttribute(TRAGET_NAMESPACE);
                    schemaMap.put(targetNamespace, s.getElement());
                }
            }
        }
        return schemaMap;
    }

    /**
     * Look for the relevant binding!
     *
     * @param dif
     * @return
     * @throws AxisFault
     */
    private Binding findBinding(Definition dif) throws AxisFault {
        Map services = dif.getServices();
        Service service;
        Binding binding = null;
        Port port = null;
        if (serviceName != null) {
            service = (Service) services.get(serviceName);
            if (service == null) {
                throw new AxisFault("Service not found the WSDL "
                        + serviceName.getLocalPart());
            }
        } else {
            if (services.size() > 0) {
                //pick the first service - we don't really have a choice here
                service = (Service) services.values().toArray()[0];
            } else {
                throw new AxisFault("No service element found in the WSDL");
            }
        }
        copyExtensibleElements(service.getExtensibilityElements(), dif,
                axisService, SERVICE);
        if (portName != null) {
            port = service.getPort(portName);
            if (port == null) {
                throw new AxisFault("No port found for the given name :"
                        + portName);
            }
        } else {
            Map ports = service.getPorts();
            if (ports != null && ports.size() > 0) {
                //pick the port with the SOAP address as the default port
                port = findSOAPPort(ports);
                if (port == null) {
                    //a SOAP port was not found - log a warning
                    // and use the first port in the list
                    log.info("A SOAP port was not found - " +
                            "picking a random port!");
                    port = (Port) ports.values().toArray()[0];
                }
            }
        }

        axisService.setName(service.getQName().getLocalPart());

        if (port != null) {
            copyExtensibleElements(port.getExtensibilityElements(), dif,
                    axisService, PORT);
            binding = port.getBinding();
        }
        return binding;
    }

    /**
     * Finds a SOAP port given the port map
     *
     * @param ports
     */
    private Port findSOAPPort(Map ports) {
        Port port;
        for (Iterator portsIterator = ports.values().iterator();
             portsIterator.hasNext();) {
            port = (Port) portsIterator.next();
            List extensibilityElements = port.getExtensibilityElements();
            for (int i = 0; i < extensibilityElements.size(); i++) {
                Object extElement = extensibilityElements.get(i);
                if (extElement instanceof SOAPAddress) {
                    //SOAP 1.1 address found - return that port and we are done
                    return port;
                }

                if (extElement instanceof UnknownExtensibilityElement) {
                    //todo check for a SOAP 1.2 address
                    // extensibility element here
                }

            }

        }
        //None found - just return null.
        return null;
    }

    /**
     * Process the binding
     *
     * @param binding
     * @param dif
     * @throws Exception
     */
    private void processBinding(
            Binding binding, Definition dif) throws Exception {
        if (binding != null) {
            copyExtensibleElements(binding.getExtensibilityElements(), dif,
                    axisService, BINDING);

            PortType portType = binding.getPortType();
            processPortType(portType, dif);

//            String portTypeNs = portType.getQName().getNamespaceURI();
            List list = binding.getBindingOperations();
            for (int i = 0; i < list.size(); i++) {
                BindingOperation wsdl4jBindingOperation = (BindingOperation) list
                        .get(i);

                AxisOperation operation = axisService.getOperation(new QName(
                        wsdl4jBindingOperation.getName()));

                // this should first check the style of the binding
                // and then set the style in the axis operation
                // if that is not present, then only the global style applies
                // this style is either rpc or doc

                String style = getSOAPStyle(wsdl4jBindingOperation);
                if (style != null) {
                    operation.setStyle(style);
                } else if (this.style != null) {
                    operation.setStyle(this.style);
                }

                copyExtensibleElements(wsdl4jBindingOperation
                        .getExtensibilityElements(), dif, operation,
                        BINDING_OPERATION);

                BindingInput bindingInput = wsdl4jBindingOperation
                        .getBindingInput();
                BindingOutput bindingOutput = wsdl4jBindingOperation
                        .getBindingOutput();
                Map bindingFaultsMap = wsdl4jBindingOperation.getBindingFaults();
                Operation wsdl4jOperation = wsdl4jBindingOperation.getOperation();
                String MEP = operation.getMessageExchangePattern();

                /* Process the binding inputs */

                if (bindingInput != null) {
                    if (WSDLUtil.isInputPresentForMEP(MEP)) {


                        AxisMessage inAxisMessage = operation
                                .getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
                        //Add the Qname reference - this has to be done by looking at the
                        //binding
                        AddQNameReference(inAxisMessage,
                                wsdl4jOperation,
                                bindingInput,
                                wrappableOperations.contains(
                                        wsdl4jBindingOperation.getOperation()
                                ));
                        copyExtensibleElements(bindingInput
                                .getExtensibilityElements(), dif, inAxisMessage,
                                BINDING_OPERATION_INPUT);
                    }
                }

                /*
                   Process the binding outputs
                */

                if (bindingOutput != null) {
                    if (WSDLUtil.isOutputPresentForMEP(MEP)) {
                        AxisMessage outAxisMessage = operation
                                .getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);

                        //Add the Qname reference - this has to be done by looking at the
                        //binding
                        AddQNameReference(outAxisMessage,
                                wsdl4jOperation,
                                bindingOutput,
                                wrappableOperations.contains(
                                        wsdl4jBindingOperation.getOperation()
                                ));

                        copyExtensibleElements(bindingOutput
                                .getExtensibilityElements(), dif,
                                outAxisMessage, BINDING_OPERATION_OUTPUT);
                    }
                }

                /*  process the binding faults */
                for (Iterator faultKeys = bindingFaultsMap.keySet().iterator();
                     faultKeys.hasNext();) {
                    Object faultMapKey = faultKeys.next();
                    BindingFault bindingFault = (BindingFault) bindingFaultsMap.get(faultMapKey);
                    Fault wsdl4jFault = wsdl4jOperation.getFault(bindingFault.getName());
                    AxisMessage faultMessage = findFaultMessage(wsdl4jFault.getMessage().getQName().getLocalPart(), operation.getFaultMessages());

                    AddQNameReference(faultMessage, wsdl4jFault.getMessage());


                }
            }

        }
    }

    /**
     * Find the fault message relevant to a given name
     * from the fault message list
     *
     * @param name
     * @param faultMessages
     */
    private AxisMessage findFaultMessage(String name, ArrayList faultMessages) {
        AxisMessage tempMessage;
        for (int i = 0; i < faultMessages.size(); i++) {
            tempMessage = (AxisMessage) faultMessages.get(i);
            if (name.equals(tempMessage.getName())) {
                return tempMessage;
            }

        }
        return null;
    }

    /**
     * Add the QName for the binding input
     *
     * @param inMessage
     * @param wsdl4jOperation
     * @param bindingInput
     * @param isWrapped
     */
    private void AddQNameReference(AxisMessage inMessage,
                                   Operation wsdl4jOperation,
                                   BindingInput bindingInput,
                                   boolean isWrapped) {

        List extensibilityElements = bindingInput.getExtensibilityElements();
        Message wsdl4jMessage = wsdl4jOperation.getInput().getMessage();

        for (int i = 0; i < extensibilityElements.size(); i++) {
            ExtensibilityElement extElement = (ExtensibilityElement) extensibilityElements.get(i);

            // SOAP 1.1 body element found!
            if (extElement instanceof SOAPBody) {
                SOAPBody soapBody = (SOAPBody) extElement;
                List bindingPartsList = soapBody.getParts();
                if (bindingPartsList != null && !bindingPartsList.isEmpty()) {
                    //we can process a single part only
                    processPartsList(bindingPartsList, wsdl4jMessage, inMessage);
                    // there are no parts named in the binding - process the items normally
                    // by looking at the single message part - or the wrapped
                    // items if wrapped by us
                } else {
                    //for the wrapped types we need to find this from the
                    //wrapper schema map
                    if (isWrapped) {
                        //The schema for this should be already made ! Find the QName from
                        //the list and add it - the name for this is just the
                        inMessage.setElementQName((QName) resolvedRpcWrappedElementMap.get(wsdl4jOperation.getName()));

                    } else {
                        //pick the first part from the list and take that as the relevant part
                        //it is somewhat questionnable whether the first part gets picked
                        //but we'll have to take a chance here
                        Map wsdl4jPartsMap = wsdl4jMessage.getParts();
                        if (!wsdl4jPartsMap.isEmpty()) {
                            Part wsdl4jPart = (Part) wsdl4jPartsMap.values().toArray()[0];
                            if (wsdl4jPart.getElementName() != null) {
                                inMessage.setElementQName(wsdl4jPart.getElementName());
                            }
                        }
                    }
                }

                break;

            } else if (extElement instanceof UnknownExtensibilityElement) {
                //in the case of unknown extensibilty elements we'll have to look at the
                //SOAP 1.2 namespace

                //todo - add the code here to process the SOAP 1.2 body
                break;
            }
        }

    }

    /**
     * A reusable method to both the input and output QName populators
     *
     * @param bindingPartsList
     * @param wsdl4jMessage
     * @param inMessage
     */
    private void processPartsList(List bindingPartsList, Message wsdl4jMessage, AxisMessage inMessage) {
        if (bindingPartsList.size() > 1) {
            //do something here - perhaps throw and exception
        } else {
            //find the part from the relevant message
            Part wsdl4jMessagePart = wsdl4jMessage.getPart((String) bindingPartsList.get(0));
            // the part name better be there - if not throw an exception
            if (wsdl4jMessagePart == null) {
                throw new RuntimeException();
            }
            if (wsdl4jMessagePart.getElementName() == null) {
                //this cannot be done - we need to have a
                //element reference
                throw new WSDLProcessingException("Element reference missing!");
            }
            inMessage.setElementQName(wsdl4jMessagePart.getElementName());

        }
    }

    /**
     * Add the QName for the binding output
     *
     * @param outMessage
     * @param wsdl4jOperation
     * @param isWrapped
     */
    private void AddQNameReference(AxisMessage outMessage,
                                   Operation wsdl4jOperation,
                                   BindingOutput bindingOutput,
                                   boolean isWrapped) {

        List extensibilityElements = bindingOutput.getExtensibilityElements();
        Message wsdl4jMessage = wsdl4jOperation.getOutput().getMessage();

        for (int i = 0; i < extensibilityElements.size(); i++) {
            ExtensibilityElement extElement = (ExtensibilityElement) extensibilityElements.get(i);

            // SOAP 1.1 body element found!
            if (extElement instanceof SOAPBody) {
                SOAPBody soapBody = (SOAPBody) extElement;
                List bindingPartsList = soapBody.getParts();
                if (bindingPartsList != null && !bindingPartsList.isEmpty()) {
                    //we can process a single part only so delegate the task
                    //of processing
                    processPartsList(bindingPartsList, wsdl4jMessage, outMessage);
                    // there are no parts named in the binding - process the items normally
                    // by looking at the single message part - or the wrapped
                    // items if wrapped by us
                } else {
                    //for the wrapped types we need to find this from the
                    //wrapper schema map
                    if (isWrapped) {
                        //The schema for this should be already made ! Find the QName from
                        //the list and add it
                        outMessage.setElementQName(
                                (QName) resolvedRpcWrappedElementMap.get(
                                        wsdl4jOperation.getName() + WRAPPED_OUTPUTNAME_SUFFIX));

                    } else {
                        //pick the first part from the list and take that as the relevant part
                        //it is somewhat questionable whether the first part gets picked
                        //but we'll have to take a chance here
                        Map wsdl4jPartsMap = wsdl4jMessage.getParts();
                        if (!wsdl4jPartsMap.isEmpty()) {
                            Part wsdl4jPart = (Part) wsdl4jPartsMap.values().toArray()[0];
                            if (wsdl4jPart.getElementName() != null) {
                                outMessage.setElementQName(wsdl4jPart.getElementName());
                            }
                        }
                    }
                }

                break;

            } else if (extElement instanceof UnknownExtensibilityElement) {
                //in the case of unknown extensibilty elements we'll have to look at the
                //SOAP 1.2 namespace

                //todo - add the code here to process the SOAP 1.2 body
                break;
            }
        }

    }

    /**
     * Add the QName for the binding output
     */
    private void AddQNameReference(AxisMessage faultMessage,
                                   Message wsdl4jMessage) {

        // for a fault this is trivial - All faults are related directly to a
        // message by the name and are supposed to have a single part. So it is
        // a matter of copying the right QName from the message part

        //get the part
        Part wsdl4jMessagePart = (Part) wsdl4jMessage.getParts().values().toArray()[0];
        if (wsdl4jMessagePart == null) {
            throw new WSDLProcessingException();
        }
        faultMessage.setElementQName(wsdl4jMessagePart.getElementName());


    }

    /**
     * A util method that returns the SOAP style
     * included in the binding operation
     *
     * @param bindingOp
     */
    private String getSOAPStyle(BindingOperation bindingOp) {
        List extensibilityElements = bindingOp.getExtensibilityElements();
        for (int i = 0; i < extensibilityElements.size(); i++) {
            Object extElement = extensibilityElements.get(i);
            if (extElement instanceof SOAPOperation) {
                return ((SOAPOperation) extElement).getStyle();
            }

        }

        return null;

    }

    /**
     * Simply Copy information.
     *
     * @param wsdl4jPortType
     */
    // FIXME Evaluate a way of injecting features and priperties with a general
    // formatted input
    private void processPortType(PortType wsdl4jPortType, Definition dif)
            throws Exception {

        copyExtensionAttributes(wsdl4jPortType.getExtensionAttributes(),
                axisService, PORT_TYPE);

        Iterator wsdl4JOperationsIterator = wsdl4jPortType.getOperations()
                .iterator();
        Operation wsdl4jOperation;
        while (wsdl4JOperationsIterator.hasNext()) {
            wsdl4jOperation = (Operation) wsdl4JOperationsIterator.next();
            axisService.addOperation(
                    populateOperations(
                            wsdl4jOperation,
                            wsdl4jPortType,
                            dif));
        }
    }

    /**
     * Copy the component from the operation
     *
     * @param wsdl4jOperation
     * @param dif
     * @return
     * @throws Exception
     */
    private AxisOperation populateOperations(Operation wsdl4jOperation, PortType wsdl4jPortType,
                                             Definition dif) throws Exception {
        QName opName = new QName(wsdl4jOperation.getName());
        //Copy Name Attribute
        AxisOperation axisOperation = axisService.getOperation(opName);
        if (axisOperation == null) {
            String MEP = getMEP(wsdl4jOperation);
            axisOperation = AxisOperationFactory.getOperationDescription(MEP);
            axisOperation.setName(opName);

            //All policy includes must share same registry
            PolicyInclude pi = axisOperation.getPolicyInclude();
            if (pi == null) {
                pi = new PolicyInclude();
                axisOperation.setPolicyInclude(pi);
            }
            pi.setPolicyRegistry(registry);
        }

        copyExtensibleElements(wsdl4jOperation.getExtensibilityElements(), dif,
                axisOperation, PORT_TYPE_OPERATION);

        Input wsdl4jInputMessage = wsdl4jOperation.getInput();


        if (isServerSide) {
            if (null != wsdl4jInputMessage) {
                AxisMessage inMessage = axisOperation
                        .getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
                Message message = wsdl4jInputMessage.getMessage();
                if (null != message) {
                    inMessage.setName(message.getQName().getLocalPart());
                    copyExtensibleElements(message.getExtensibilityElements(),
                            dif, inMessage, PORT_TYPE_OPERATION_INPUT);

                }
                // Check if the action is already set as we don't want to override it
                // with the Default Action Pattern
                ArrayList inputActions = axisOperation.getWsamappingList();
                String action = null;
                if (inputActions == null || inputActions.size() == 0) {
                    action = WSDL11ActionHelper.getActionFromInputElement(dif, wsdl4jPortType, wsdl4jOperation, wsdl4jInputMessage);
                }
                if (action != null) {
                    if (inputActions == null) {
                        inputActions = new ArrayList();
                        axisOperation.setWsamappingList(inputActions);
                    }
                    inputActions.add(action);
                }
            }
            //Create an output message and add
            Output wsdl4jOutputMessage = wsdl4jOperation.getOutput();
            if (null != wsdl4jOutputMessage) {
                AxisMessage outMessage = axisOperation
                        .getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
                Message message = wsdl4jOutputMessage.getMessage();
                if (null != message) {

                    outMessage.setName(message.getQName().getLocalPart());
                    copyExtensibleElements(message.getExtensibilityElements(),
                            dif, outMessage, PORT_TYPE_OPERATION_OUTPUT);

                    // wsdl:portType -> wsdl:operation -> wsdl:output
                }
                // Check if the action is already set as we don't want to override it
                // with the Default Action Pattern
                String action = axisOperation.getOutputAction();
                if (action == null) {
                    action = WSDL11ActionHelper.getActionFromOutputElement(dif, wsdl4jPortType, wsdl4jOperation, wsdl4jOutputMessage);
                }
                if (action != null) {
                    axisOperation.setOutputAction(action);
                }
            }
        } else {

            // for the client side we have to do something that is a bit
            // weird. The in message is actually taken from the output
            // and the output is taken from the in

            if (null != wsdl4jInputMessage) {
                AxisMessage inMessage = axisOperation
                        .getMessage(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
                Message message = wsdl4jInputMessage.getMessage();
                if (null != message) {

                    inMessage.setName(message.getQName().getLocalPart());
                    copyExtensibleElements(message.getExtensibilityElements(),
                            dif, inMessage, PORT_TYPE_OPERATION_OUTPUT);

                }
                // Check if the action is already set as we don't want to override it
                // with the Default Action Pattern
                String action = axisOperation.getOutputAction();
                if (action == null) {
                    action = WSDL11ActionHelper.getActionFromInputElement(dif, wsdl4jPortType, wsdl4jOperation, wsdl4jInputMessage);
                }
                if (action != null) {
                    axisOperation.setOutputAction(action);
                }
            }
            //Create an output message and add
            Output wsdl4jOutputMessage = wsdl4jOperation.getOutput();
            if (null != wsdl4jOutputMessage) {
                AxisMessage outMessage = axisOperation
                        .getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
                Message message = wsdl4jOutputMessage.getMessage();
                if (null != message) {

                    outMessage.setName(message.getQName().getLocalPart());
                    copyExtensibleElements(message.getExtensibilityElements(),
                            dif, outMessage, PORT_TYPE_OPERATION_INPUT);

                    // wsdl:portType -> wsdl:operation -> wsdl:output
                }
                // Check if the action is already set as we don't want to override it
                // with the Default Action Pattern
                ArrayList inputActions = axisOperation.getWsamappingList();
                String action = null;
                if (inputActions == null || inputActions.size() == 0) {
                    action = WSDL11ActionHelper.getActionFromOutputElement(dif, wsdl4jPortType, wsdl4jOperation, wsdl4jOutputMessage);
                }
                if (action != null) {
                    if (inputActions == null) {
                        inputActions = new ArrayList();
                        axisOperation.setWsamappingList(inputActions);
                    }
                    inputActions.add(action);
                }
            }
        }

        Map faults = wsdl4jOperation.getFaults();
        Iterator faultKeyIterator = faults.keySet().iterator();

        while (faultKeyIterator.hasNext()) {
            Fault fault = (Fault) faults.get(faultKeyIterator.next());
            AxisMessage axisFaultMessage = new AxisMessage();
            Message faultMessage = fault.getMessage();
            if (null != faultMessage) {
                axisFaultMessage.setName(faultMessage.getQName().getLocalPart());
                copyExtensibleElements(faultMessage.getExtensibilityElements(),
                        dif, axisFaultMessage, PORT_TYPE_OPERATION_FAULT);


            }

            // Check if the action is already set as we don't want to override it
            // with the Default Action Pattern
            String action = axisOperation.getFaultAction(fault.getName());
            if (action == null) {
                action = WSDL11ActionHelper.getActionFromFaultElement(dif, wsdl4jPortType, wsdl4jOperation, fault);
            }
            if (action != null) {
                axisOperation.addFaultAction(fault.getName(), action);
            }
            axisOperation.setFaultMessages(axisFaultMessage);
        }
        return axisOperation;
    }


    /**
     * Generates a list of wrapper schemas
     *
     * @param wsdl4jBinding
     */
    private Element[] generateWrapperSchema(
            Map schemaMap,
            Binding wsdl4jBinding) {

        List schemaElementList = new ArrayList();
        //target namespace for this should be the namespace URI for
        //the porttype
        String porttypeNamespaceURI = wsdl4jBinding.getPortType().
                getQName().getNamespaceURI();

        ////////////////////////////////////////////////////////////////////////
        // if there are any bindings present then we have to process them. we
        // have to generate a schema per wsdl4jBinding (that is the safest option).
        // if not we just resolve to
        // the good old port type
        // list, in which case we'll generate a schema per porttype
        ////////////////////////////////////////////////////////////////////////

        Element schemaElement = createSchemaForPorttype(
                porttypeNamespaceURI, findWrappableOperations(wsdl4jBinding),
                schemaMap);

        if (schemaElement != null) {
            schemaElementList.add(schemaElement);
        }
        return (Element[]) schemaElementList
                .toArray(new Element[schemaElementList.size()]);
    }

    /**
     * Create a schema by looking at the port type
     *
     * @param namespaceURI
     * @return null if there is no element
     */
    private Element createSchemaForPorttype(String namespaceURI,
                                            List operationListToProcess,
                                            Map existingSchemaMap) {
        //first of all look at the operations list
        //we can return immediately if we get the operations list
        //as empty
        if (operationListToProcess.isEmpty()) {
            return null;
        }

        //loop through the messages. We'll populate thins map with the relevant
        // messages
        //from the operations
        Map messagesMap = new HashMap();
        Map inputOperationsMap = new HashMap();
        Map outputOperationsMap = new HashMap();
        Map faultyOperationsMap = new HashMap();
        //this contains the required namespace imports. the key in this
        //map would be the namaspace URI
        Map namespaceImportsMap = new HashMap();
        //generated complextypes. Keep in the list for writing later
        //the key for the complexType map is the message QName
        Map complexTypeElementsMap = new HashMap();
        //generated Elements. Kep in the list for later writing
        List elementElementsList = new ArrayList();
        //list namespace prefix map. This map will include uri -> prefix
        Map namespacePrefixMap = new HashMap();

        ////////////////////////////////////////////////////////////////////////////////////////////////////
        // First thing is to populate the message map with the messages to
        // process.
        ////////////////////////////////////////////////////////////////////////////////////////////////////

        //we really need to do this for a single porttype!
        Operation op;
        for (int k = 0; k < operationListToProcess.size(); k++) {
            op = (Operation) operationListToProcess.get(k);
            Input input = op.getInput();
            Message message;
            if (input != null) {
                message = input.getMessage();
                messagesMap.put(message.getQName(), message);
                inputOperationsMap.put(op.getName(), message);
            }

            Output output = op.getOutput();
            if (output != null) {
                message = output.getMessage();
                messagesMap.put(message.getQName(), message);
                outputOperationsMap.put(op.getName(), message);
            }

            Map faultMap = op.getFaults();
            if (faultMap != null && faultMap.size() > 0) {
                Iterator keys = faultMap.keySet().iterator();
                while (keys.hasNext()) {
                    Object key = keys.next();
                    Fault fault = (Fault) faultMap.get(key);
                    if (fault != null) {
                        message = fault.getMessage();
                        messagesMap.put(message.getQName(), message);
                        faultyOperationsMap.put(key, message);
                    }
                }
            }
        }

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //check whether there are messages that are wrappable. If there are no
        // messages that are wrappable we'll
        //just return null and endup this process. However we need to take the
        // force flag into account here
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

        QName[] keys;

        //just take all the messages and wrap them, we've  already selected
        //the relevant messages by looking at the SOAP binding

        keys = (QName[]) messagesMap.keySet().toArray(
                new QName[messagesMap.size()]);

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Now we have the message list to process - Process the whole list of
        // messages at once
        // since we need to generate one single schema
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

        List resolvedMessageQNames = new ArrayList();
        //find the xsd prefix
        String xsdPrefix = findSchemaPrefix();
        Message wsdl4jMessage;
        //DOM document that will be the ultimate creator
        Document document = getDOMDocumentBuilder().newDocument();
        for (int i = 0; i < keys.length; i++) {
            wsdl4jMessage = (Message) messagesMap.get(keys[i]);
            //No need to check the wrappable,

            //This message is wrappabel. However we need to see whether the
            // message is already
            //resolved!
            if (!resolvedMessageQNames.contains(wsdl4jMessage.getQName())) {
                //This message has not been touched before!. So we can go ahead
                // now
                Map parts = wsdl4jMessage.getParts();
                //add the complex type
                Element newComplexType = document.createElementNS(
                        XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                        + XML_SCHEMA_COMPLEX_TYPE_LOCAL_NAME);

                Element cmplxTypeSequence = document.createElementNS(
                        XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                        + XML_SCHEMA_SEQUENCE_LOCAL_NAME);
                Element child;
                Iterator iterator = parts.keySet().iterator();
                while (iterator.hasNext()) {
                    Part part = (Part) parts.get(iterator.next());
                    //the part name
                    String elementName = part.getName();
                    boolean isTyped = true;
                    //the type name
                    QName schemaTypeName;
                    if (part.getTypeName() != null) {
                        schemaTypeName = part.getTypeName();
                    } else if (part.getElementName() != null) {
                        schemaTypeName = part.getElementName();
                        isTyped = false;
                    } else {
                        throw new RuntimeException(" Unqualified Message part!");
                    }

                    child = document.createElementNS(XMLSCHEMA_NAMESPACE_URI,
                            xsdPrefix + ":" + XML_SCHEMA_ELEMENT_LOCAL_NAME);

                    String prefix;
                    if (XMLSCHEMA_NAMESPACE_URI.equals(schemaTypeName
                            .getNamespaceURI())) {
                        prefix = xsdPrefix;
                    } else {
                        //this schema is a third party one. So we need to have
                        // an import statement in our generated schema
                        String uri = schemaTypeName.getNamespaceURI();
                        if (!namespaceImportsMap.containsKey(uri)) {
                            //create Element for namespace import
                            Element namespaceImport = document.createElementNS(
                                    XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                                    + XML_SCHEMA_IMPORT_LOCAL_NAME);
                            namespaceImport.setAttribute(NAMESPACE_URI, uri);
                            //add this to the map
                            namespaceImportsMap.put(uri, namespaceImport);
                            //we also need to associate this uri with a prefix
                            //and include that prefix
                            //in the schema's namspace declarations. So add
                            //theis particular namespace to the
                            //prefix map as well
                            prefix = getTemporaryNamespacePrefix();
                            namespacePrefixMap.put(uri, prefix);
                        } else {
                            //this URI should be already in the namspace prefix
                            // map
                            prefix = (String) namespacePrefixMap.get(uri);
                        }

                    }
                    // If it's from a type the element we need to add a name and
                    // the type
                    //if not it's the element reference
                    if (isTyped) {
                        child.setAttribute(XSD_NAME, elementName);
                        child.setAttribute(XSD_TYPE, prefix + ":"
                                + schemaTypeName.getLocalPart());
                    } else {
                        child.setAttribute(XSD_REF, prefix + ":"
                                + schemaTypeName.getLocalPart());
                    }
                    cmplxTypeSequence.appendChild(child);
                }
                newComplexType.appendChild(cmplxTypeSequence);
                //add this newly created complextype to the list
                complexTypeElementsMap.put(wsdl4jMessage.getQName(),
                        newComplexType);
                resolvedMessageQNames.add(wsdl4jMessage.getQName());
            }

        }

        Element elementDeclaration;

        //loop through the input op map and generate the elements
        String[] inputOperationtNames = (String[]) inputOperationsMap.keySet()
                .toArray(new String[inputOperationsMap.size()]);
        for (int j = 0; j < inputOperationtNames.length; j++) {
            String inputOpName = inputOperationtNames[j];
            elementDeclaration = document.createElementNS(
                    XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                    + XML_SCHEMA_ELEMENT_LOCAL_NAME);
            elementDeclaration.setAttribute(XSD_NAME, inputOpName);

            QName typeQName = ((Message) inputOperationsMap.get(inputOpName))
                    .getQName();
            // add the anonymous
            elementDeclaration.appendChild((Element) complexTypeElementsMap.get(typeQName));
            elementElementsList.add(elementDeclaration);
            resolvedRpcWrappedElementMap.put(inputOpName, new QName(
                    namespaceURI, inputOpName, AXIS2WRAPPED));
        }

        //loop through the output op map and generate the elements
        String[] outputOperationtNames = (String[]) outputOperationsMap
                .keySet().toArray(new String[outputOperationsMap.size()]);
        for (int j = 0; j < outputOperationtNames.length; j++) {

            String baseoutputOpName = outputOperationtNames[j];
            String outputOpName = baseoutputOpName + WRAPPED_OUTPUTNAME_SUFFIX;
            elementDeclaration = document.createElementNS(
                    XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                    + XML_SCHEMA_ELEMENT_LOCAL_NAME);
            elementDeclaration.setAttribute(XSD_NAME, outputOpName);

            QName typeQName = ((Message) outputOperationsMap
                    .get(baseoutputOpName)).getQName();
            // add the anonymous
            elementDeclaration.appendChild((Element) complexTypeElementsMap.get(typeQName));
            elementElementsList.add(elementDeclaration);

            resolvedRpcWrappedElementMap.put(outputOpName, new QName(
                    namespaceURI, outputOpName, AXIS2WRAPPED));

        }

        //loop through the faultoutput op map and generate the elements
        String[] faultyOperationtNames = (String[]) faultyOperationsMap
                .keySet().toArray(new String[faultyOperationsMap.size()]);
        for (int j = 0; j < faultyOperationtNames.length; j++) {

            String baseFaultOpName = faultyOperationtNames[j];
            elementDeclaration = document.createElementNS(
                    XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                    + XML_SCHEMA_ELEMENT_LOCAL_NAME);
            elementDeclaration.setAttribute(XSD_NAME, baseFaultOpName);

            QName typeQName = ((Message) faultyOperationsMap
                    .get(baseFaultOpName)).getQName();
            elementDeclaration.appendChild((Element) complexTypeElementsMap.get(typeQName));
            elementElementsList.add(elementDeclaration);
            resolvedRpcWrappedElementMap.put(baseFaultOpName, new QName(
                    namespaceURI, baseFaultOpName, AXIS2WRAPPED));

        }

        ///////////////////////////////////////////////////////////////////////
        //
        // Now we are done with processing the messages and generating the right
        // schema object model
        // time to write out the schema
        //
        // There is a catch here - when the target namespace of the generated
        // schema is equivalent to one of the  existing schemas we'll have to
        // insert the elements there rather than creating a new Schema element
        ////////////////////////////////////////////////////////////////////////
        //
        if (existingSchemaMap.containsKey(namespaceURI)) {
            //get the relevant schema element
            Element schemaElement = (Element) existingSchemaMap.get(namespaceURI);
            Document ownerDocument = schemaElement.getOwnerDocument();

            //loop through the namespace declarations first and add them
            String[] nameSpaceDeclarationArray = (String[]) namespacePrefixMap
                    .keySet().toArray(new String[namespacePrefixMap.size()]);
            for (int i = 0; i < nameSpaceDeclarationArray.length; i++) {
                String s = nameSpaceDeclarationArray[i];
                checkAndAddNamespaceDeclarations(s, namespacePrefixMap, schemaElement);
            }

            //add imports  - check whether it is the targetnamespace before
            // adding
            Element[] namespaceImports = (Element[]) namespaceImportsMap.values()
                    .toArray(new Element[namespaceImportsMap.size()]);
            for (int i = 0; i < namespaceImports.length; i++) {
                if (!namespaceURI.equals(namespaceImports[i].getAttribute(NAMESPACE_URI))) {
                    schemaElement.appendChild(namespaceImports[i]);
                }
            }

            Element[] elementDeclarations = (Element[]) elementElementsList
                    .toArray(new Element[elementElementsList.size()]);
            for (int i = 0; i < elementDeclarations.length; i++) {
                schemaElement.appendChild(ownerDocument.
                        importNode(elementDeclarations[i], true));
            }

            //don't return anything!!
            return null;
        } else {
            // there is no element in the
            Element schemaElement = document.createElementNS(
                    XMLSCHEMA_NAMESPACE_URI, xsdPrefix + ":"
                    + XML_SCHEMA_LOCAL_NAME);

            //loop through the namespace declarations first
            String[] nameSpaceDeclarationArray = (String[]) namespacePrefixMap
                    .keySet().toArray(new String[namespacePrefixMap.size()]);
            for (int i = 0; i < nameSpaceDeclarationArray.length; i++) {
                String s = nameSpaceDeclarationArray[i];
                schemaElement.setAttributeNS(XML_NAMESPACE_URI,
                        NAMESPACE_DECLARATION_PREFIX + namespacePrefixMap.get(s).toString(), s);

            }

            //add the targetNamespace
            schemaElement.setAttributeNS(XML_NAMESPACE_URI,
                    XMLNS_AXIS2WRAPPED, namespaceURI);
            schemaElement.setAttribute(XSD_TARGETNAMESPACE, namespaceURI);
            schemaElement.setAttribute(XSD_ELEMENT_FORM_DEFAULT, XSD_UNQUALIFIED);

            //add imports
            Element[] namespaceImports = (Element[]) namespaceImportsMap.values()
                    .toArray(new Element[namespaceImportsMap.size()]);
            for (int i = 0; i < namespaceImports.length; i++) {
                schemaElement.appendChild(namespaceImports[i]);

            }

            //add element declarations
            Element[] elementDeclarations = (Element[]) elementElementsList
                    .toArray(new Element[elementElementsList.size()]);
            for (int i = 0; i < elementDeclarations.length; i++) {
                schemaElement.appendChild(elementDeclarations[i]);

            }


            return schemaElement;
        }

    }

    /**
     * @param prefixMap
     */
    private void checkAndAddNamespaceDeclarations(String namespace,
                                                  Map prefixMap,
                                                  Element schemaElement) {
        //get the attribute for the current namespace
        String prefix = (String) prefixMap.get(namespace);
        //A prefix must be found at this point!
        String existingURL = schemaElement.getAttributeNS(
                XML_NAMESPACE_URI,
                NAMESPACE_DECLARATION_PREFIX + prefix);
        if (existingURL == null) {
            //there is no existing URL by that prefix - declare a new namespace
            schemaElement.setAttributeNS(XML_NAMESPACE_URI,
                    NAMESPACE_DECLARATION_PREFIX + prefix,
                    namespace);
        } else if (existingURL.equals(namespace)) {
            //this namespace declaration is already there with the same prefix
            //ignore it
        } else {
            //there is a different namespace declared in the given prefix
            //change the prefix in the prefix map to a new one and declare it

            //create a prefix
            String generatedPrefix = "ns" + prefixCounter++;
            while (prefixMap.containsKey(generatedPrefix)) {
                generatedPrefix = "ns" + prefixCounter++;
            }
            schemaElement.setAttributeNS(XML_NAMESPACE_URI,
                    NAMESPACE_DECLARATION_PREFIX + generatedPrefix,
                    namespace);
            //add to the map
            prefixMap.put(generatedPrefix, namespace);
        }

    }

    /**
     * The intention of this procedure is to process the imports. When
     * processing the imports the imported documents will be populating the
     * items in the main document recursivley
     *
     * @param wsdl4JDefinition
     */
    private void processImports(Definition wsdl4JDefinition) {
        Map wsdlImports = wsdl4JDefinition.getImports();

        if (null != wsdlImports && !wsdlImports.isEmpty()) {
            Collection importsCollection = wsdlImports.values();
            for (Iterator iterator = importsCollection.iterator(); iterator
                    .hasNext();) {
                Vector values = (Vector) iterator.next();
                for (int i = 0; i < values.size(); i++) {
                    Import wsdlImport = (Import) values.elementAt(i);

                    if (wsdlImport.getDefinition() != null) {
                        Definition importedDef = wsdlImport.getDefinition();
                        if (importedDef != null) {
                            processImports(importedDef);

                            //copy ns
                            Map namespaces = importedDef.getNamespaces();
                            Iterator keys = namespaces.keySet().iterator();
                            while (keys.hasNext()) {
                                Object key = keys.next();
                                if (!wsdl4jDefinition.getNamespaces()
                                        .containsValue(namespaces.get(key))) {
                                    wsdl4jDefinition.getNamespaces().put(key,
                                            namespaces.get(key));
                                }
                            }

                            wsdl4jDefinition.getNamespaces().putAll(namespaces);
                            //copy types
                            Types t = importedDef.getTypes();
                            List typesList = t.getExtensibilityElements();
                            for (int j = 0; j < typesList.size(); j++) {
                                Types types = wsdl4JDefinition.getTypes();
                                if (types == null) {
                                    types = wsdl4JDefinition.createTypes();
                                    wsdl4JDefinition.setTypes(types);
                                }
                                types
                                        .addExtensibilityElement((ExtensibilityElement) typesList
                                                .get(j));

                            }

                            //add messages
                            Map messagesMap = importedDef.getMessages();
                            wsdl4JDefinition.getMessages().putAll(messagesMap);

                            //add portypes
                            Map porttypeMap = importedDef.getPortTypes();
                            wsdl4JDefinition.getPortTypes().putAll(porttypeMap);

                            //add bindings
                            Map bindingMap = importedDef.getBindings();
                            wsdl4JDefinition.getBindings().putAll(bindingMap);

                            //add services
                            Map serviceMap = importedDef.getServices();
                            wsdl4JDefinition.getServices().putAll(serviceMap);

                        }

                    }
                }
            }
        }
    }

    /**
     * Read the WSDL file given the inputstream for the WSDL source
     *
     * @param in
     * @return
     * @throws WSDLException
     */
    private Definition readInTheWSDLFile(InputStream in) throws WSDLException {

        WSDLReader reader = WSDLFactory.newInstance().newWSDLReader();

        //switch off the verbose mode for all usecases
        reader.setFeature("javax.wsdl.verbose", false);

        //if the custem resolver is present then use it
        if (customWSLD4JResolver != null) {
            return reader.readWSDL(customWSLD4JResolver);
        } else {
            reader.setFeature("javax.wsdl.importDocuments", false);
            Document doc;
            try {
                doc = XMLUtils.newDocument(in);
            } catch (ParserConfigurationException e) {
                throw new WSDLException(WSDLException.PARSER_ERROR,
                        "Parser Configuration Error", e);
            } catch (SAXException e) {
                throw new WSDLException(WSDLException.PARSER_ERROR,
                        "Parser SAX Error", e);

            } catch (IOException e) {
                throw new WSDLException(WSDLException.INVALID_WSDL, "IO Error",
                        e);
            }
            return reader.readWSDL(getBaseUri(), doc);
        }
    }

    /**
     * Get the Extensible elements form wsdl4jExtensibleElements
     * <code>Vector</code> if any and copy them to <code>Component</code>
     * <p/>
     * Note  - SOAP body extensible element will be processed differently
     *
     * @param wsdl4jExtensibleElements
     * @param description                   where is the ext element (port , portype , biding)
     * @param wsdl4jDefinition
     * @param originOfExtensibilityElements -
     *                                      this will indicate the place this extensibility element came
     *                                      from.
     */
    private void copyExtensibleElements(List wsdl4jExtensibleElements,
                                        Definition wsdl4jDefinition, AxisDescription description,
                                        String originOfExtensibilityElements) throws AxisFault {
        Iterator iterator = wsdl4jExtensibleElements.iterator();
        while (iterator.hasNext()) {
            ExtensibilityElement wsdl4jElement = (ExtensibilityElement) iterator
                    .next();

            if (wsdl4jElement instanceof UnknownExtensibilityElement) {
                UnknownExtensibilityElement unknown = (UnknownExtensibilityElement) (wsdl4jElement);

                //look for the SOAP 1.2 stuff here. WSDL4j does not understand
                // SOAP 1.2 things so we'll have to look at the unknown elements and
                //figure out whether they are SOAP 1.2 things
                if (WSDLConstants.WSDL11Constants.SOAP_12_OPERATION.equals(unknown
                        .getElementType())) {
                    Element element = unknown.getElement();
                    if (description instanceof AxisOperation) {
                        AxisOperation axisOperation = (AxisOperation) description;
                        String style = element.getAttribute("style");
                        if (style != null) {
                            axisOperation.setStyle(style);
                        }
                        axisOperation.setSoapAction(element
                                .getAttribute("soapAction"));
                    }
                } else if (WSDLConstants.WSDL11Constants.SOAP_12_HEADER.equals(unknown
                        .getElementType())) {

                    //TODO : implement this


                } else if (WSDLConstants.WSDL11Constants.SOAP_12_BINDING.equals(unknown
                        .getElementType())) {
                    style = unknown.getElement().getAttribute("style");
                    axisService.setSoapNsUri(wsdl4jElement.getElementType()
                            .getNamespaceURI());
                } else if (WSDLConstants.WSDL11Constants.SOAP_12_ADDRESS.equals(unknown
                        .getElementType())) {
                    axisService.setEndpoint(unknown.getElement().getAttribute(
                            "location"));
                } else if (WSDLConstants.WSDL11Constants.POLICY
                        .equals(unknown.getElementType())) {

                    DOMPolicyReader policyReader = (DOMPolicyReader) PolicyFactory
                            .getPolicyReader(PolicyFactory.DOM_POLICY_READER);
                    Policy policy = policyReader.readPolicy(unknown
                            .getElement());

                    addPolicy(description, originOfExtensibilityElements,
                            policy);

                } else if (WSDLConstants.WSDL11Constants.POLICY_REFERENCE.equals(unknown
                        .getElementType())) {

                    DOMPolicyReader policyReader = (DOMPolicyReader) PolicyFactory
                            .getPolicyReader(PolicyFactory.DOM_POLICY_READER);
                    PolicyReference policyRef = policyReader
                            .readPolicyReference(unknown.getElement());
                    addPolicyRef(description, originOfExtensibilityElements,
                            policyRef);
                } else if (AddressingConstants.Final.WSAW_USING_ADDRESSING
                        .equals(unknown.getElementType())
                        || AddressingConstants.Submission.WSAW_USING_ADDRESSING
                        .equals(unknown.getElementType())) {
                    // Read the wsaw:UsingAddressing flag from the WSDL. It is
                    // only valid on the Port or Binding
                    // so only recognise it as en extensibility elemtn of one of
                    // those.
                    if (originOfExtensibilityElements.equals(PORT)
                            || originOfExtensibilityElements.equals(BINDING)) {
                        if (Boolean.TRUE.equals(unknown.getRequired())) {
                            axisService
                                    .setWSAddressingFlag(AddressingConstants.ADDRESSING_REQUIRED);
                        } else {
                            axisService
                                    .setWSAddressingFlag(AddressingConstants.ADDRESSING_OPTIONAL);
                        }
                    }
                } else if (AddressingConstants.Final.WSAW_ANONYMOUS.equals(unknown.getElementType())) {
                    if (originOfExtensibilityElements.equals(BINDING_OPERATION)) {
                        AxisOperation axisOperation = (AxisOperation) description;
                        if(unknown.getElement().getFirstChild()!=null &&
                                unknown.getElement().getFirstChild().getNodeType() == Node.TEXT_NODE){
                            String anonymousValue = unknown.getElement().getFirstChild().getNodeValue();
                            AddressingHelper.setAnonymousParameterValue(axisOperation, anonymousValue);
                        }
                    }
                } else {

                    // Ignore this element - it is a totally unknown element
                    //and we don't care!

                } // end of SOAP 1.2 processing

                // WSDL4J has all the SOAP 1.1 Items built in. So we can check the
                // items directly
            } else if (wsdl4jElement instanceof SOAPAddress) {
                SOAPAddress soapAddress = (SOAPAddress) wsdl4jElement;
                axisService.setEndpoint(soapAddress.getLocationURI());
            } else if (wsdl4jElement instanceof Schema) {
                Schema schema = (Schema) wsdl4jElement;
                // just add this schema - no need to worry about the imported
                // ones
                axisService.addSchema(getXMLSchema(schema.getElement(),
                        wsdl4jDefinition.getDocumentBaseURI()));

            } else if (SOAPConstants.Q_ELEM_SOAP_OPERATION.equals(wsdl4jElement
                    .getElementType())) {
                SOAPOperation soapOperation = (SOAPOperation) wsdl4jElement;
                if (description instanceof AxisOperation) {
                    AxisOperation axisOperation = (AxisOperation) description;
                    if (soapOperation.getStyle() != null) {
                        axisOperation.setStyle(soapOperation.getStyle());
                    }
                    axisOperation.setSoapAction(soapOperation
                            .getSoapActionURI());
                }
            } else if (SOAPConstants.Q_ELEM_SOAP_HEADER.equals(wsdl4jElement
                    .getElementType())) {
                SOAPHeader soapHeader = (SOAPHeader) wsdl4jElement;
                SOAPHeaderMessage headerMessage = new SOAPHeaderMessage();
                headerMessage.setNamespaceURI(soapHeader.getNamespaceURI());
                headerMessage.setUse(soapHeader.getUse());
                Boolean required = soapHeader.getRequired();
                if (null != required) {
                    headerMessage.setRequired(required.booleanValue());
                }
                if (null != wsdl4jDefinition) {
                    //find the relevant schema part from the messages
                    Message msg = wsdl4jDefinition.getMessage(soapHeader
                            .getMessage());
                    if (msg == null) {
                        //todo i18n this
                        throw new AxisFault("message " + soapHeader.getMessage() + " not found in the WSDL ");
                    }
                    Part msgPart = msg.getPart(soapHeader.getPart());
                    if (msgPart == null) {
                        //todo i18n this
                        throw new AxisFault("message part " + soapHeader.getPart() + " not found in the WSDL ");
                    }
                    headerMessage.setElement(msgPart.getElementName());
                }
                headerMessage.setMessage(soapHeader.getMessage());

                headerMessage.setPart(soapHeader.getPart());
                if (description instanceof AxisMessage) {
                    ((AxisMessage) description).addSoapHeader(headerMessage);
                }
            } else if (SOAPConstants.Q_ELEM_SOAP_BINDING.equals(wsdl4jElement
                    .getElementType())) {
                SOAPBinding soapBinding = (SOAPBinding) wsdl4jElement;
                style = soapBinding.getStyle();
                axisService.setSoapNsUri(soapBinding.getElementType()
                        .getNamespaceURI());
                //process the SOAP body
            }
        }
    }

    /**
     * Add a policy
     *
     * @param description
     * @param originOfExtensibilityElements
     * @param policy
     */
    private void addPolicy(AxisDescription description,
                           String originOfExtensibilityElements, Policy policy) {

        if (description instanceof AxisService) {
            // wsdl:service
            if (SERVICE.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.SERVICE_POLICY, policy);

                // wsdl:service -> wsdl:port
            } else if (PORT.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.PORT_POLICY, policy);

                // wsdl:binding
            } else if (BINDING.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.BINDING_POLICY, policy);

            }

            //TODO wsdl:portType ?

        } else if (description instanceof AxisOperation) {

            // wsdl:portType -> wsdl:operation
            if (PORT_TYPE_OPERATION.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.OPERATION_POLICY, policy);

                // wsdl:binding -> wsdl:operation
            } else {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.BINDING_OPERATION_POLICY, policy);
            }

        } else {

            // wsdl:portType -> wsdl:operation -> wsdl:input
            if (PORT_TYPE_OPERATION_INPUT.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.INPUT_POLICY, policy);

                // wsdl:binding -> wsdl:operation -> wsdl:input
            } else if (BINDING_OPERATION_INPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.BINDING_INPUT_POLICY, policy);

                // wsdl:portType -> wsdl:operation -> wsdl:put
            } else if (PORT_TYPE_OPERATION_OUTPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.OUTPUT_POLICY, policy);

                // wsdl:binding -> wsdl:operation -> wsdl:output
            } else if (BINDING_OPERATION_OUTPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyElement(
                        PolicyInclude.BINDING_OUTPUT_POLICY, policy);
            }
        }
    }

    /**
     * Add a policy reference
     *
     * @param description
     * @param originOfExtensibilityElements
     * @param policyRefElement
     */
    private void addPolicyRef(AxisDescription description,
                              String originOfExtensibilityElements,
                              PolicyReference policyRefElement) {

        if (description instanceof AxisService) {
            // wsdl:service
            if (SERVICE.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.SERVICE_POLICY, policyRefElement);

                // wsdl:service -> wsdl:port
            } else if (PORT.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.PORT_POLICY, policyRefElement);

                // wsdl:binding
            } else if (BINDING.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.BINDING_POLICY, policyRefElement);
            }

            //TODO policy for  wsdl:portType ?

        } else if (description instanceof AxisOperation) {

            // wsdl:portType -> wsdl:operation
            if (PORT_TYPE_OPERATION.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.OPERATION_POLICY, policyRefElement);

                // wsdl:binding -> wsdl:operation
            } else {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.BINDING_POLICY, policyRefElement);
            }

        } else {

            // wsdl:portType -> wsdl:operation -> wsdl:input
            if (PORT_TYPE_OPERATION_INPUT.equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.INPUT_POLICY, policyRefElement);

                // wsdl:binding -> wsdl:operation -> wsdl:input
            } else if (BINDING_OPERATION_INPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.BINDING_INPUT_POLICY, policyRefElement);

                // wsdl:portType -> wsdl:operation -> wsdl:put
            } else if (PORT_TYPE_OPERATION_OUTPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.OUTPUT_POLICY, policyRefElement);

                // wsdl:binding -> wsdl:operation -> wsdl:output
            } else if (BINDING_OPERATION_OUTPUT
                    .equals(originOfExtensibilityElements)) {
                description.getPolicyInclude().addPolicyRefElement(
                        PolicyInclude.BINDING_OUTPUT_POLICY, policyRefElement);
            }

            //TODO Faults ..
        }
    }

    /**
     * Look for the wrappable operations depending on the style
     *
     * @param binding
     */
    private List findWrappableOperations(Binding binding) {
        //first find the global style declaration.
        //for a SOAP binding this can be only rpc or document
        //as per the WSDL spec (section 3.4) the default style is document

        boolean isRPC = false;
        boolean isSOAPBinding = false;

        List extElements = binding.getExtensibilityElements();
        for (int i = 0; i < extElements.size(); i++) {
            if (extElements.get(i) instanceof SOAPBinding) {
                //we have a global SOAP binding!
                isSOAPBinding = true;
                SOAPBinding soapBinding = (SOAPBinding) extElements.get(i);
                if (RPC_STYLE.equals(soapBinding.getStyle())) {
                    //set the global style to rpc
                    isRPC = true;
                }

                break;
            }
        }

        //if SOAPBinding is not present just return an empty list
        if (!isSOAPBinding) {
            return new ArrayList();
        }

        //go through every operation and get their styles.
        //each one can have a style override from the global
        //styles. Depending on the style add the relevant operations
        //to the return list
        List returnList = new ArrayList();

        BindingOperation bindingOp;
        for (Iterator bindingOperationsIterator =
                binding.getBindingOperations().iterator();
             bindingOperationsIterator.hasNext();) {
            bindingOp = (BindingOperation) bindingOperationsIterator.next();
            String style = getSOAPStyle(bindingOp);

            if (style == null) {
                //no style specified
                //use the global style to determine whether to put this one or not
                if (isRPC) {
                    returnList.add(bindingOp.getOperation());
                }
            } else if (RPC_STYLE.equals(style)) {
                //add to the list
                returnList.add(bindingOp.getOperation());
            }
            // if not RPC we just leave it - default is doc

        }

        //set this to the global list
        wrappableOperations = returnList;
        return returnList;
    }

    /**
     * Guess the MEP based on the order of messages
     *
     * @param operation
     * @return
     * @throws Exception
     */
    private String getMEP(Operation operation) throws Exception {
        OperationType operationType = operation.getStyle();
        if (isServerSide) {
            if (null != operationType) {
                if (operationType.equals(OperationType.REQUEST_RESPONSE))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_IN_OUT;

                if (operationType.equals(OperationType.ONE_WAY))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_IN_ONLY;

                if (operationType.equals(OperationType.NOTIFICATION))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_OUT_ONLY;

                if (operationType.equals(OperationType.SOLICIT_RESPONSE))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_OUT_IN;
                throw new Exception("Cannot Determine the MEP");
            }
        } else {
            if (null != operationType) {
                if (operationType.equals(OperationType.REQUEST_RESPONSE))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_OUT_IN;

                if (operationType.equals(OperationType.ONE_WAY))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_OUT_ONLY;

                if (operationType.equals(OperationType.NOTIFICATION))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_IN_ONLY;

                if (operationType.equals(OperationType.SOLICIT_RESPONSE))
                    return WSDLConstants.WSDL20_2004Constants.MEP_URI_IN_OUT;
                throw new Exception("Cannot Determine the MEP");
            }
        }
        throw new Exception("Cannot Determine the MEP");
    }

    /**
     * Copies the extension attributes
     *
     * @param extAttributes
     * @param description
     * @param origin
     */
    private void copyExtensionAttributes(Map extAttributes,
                                         AxisDescription description,
                                         String origin) {

        QName key;
        QName value;

        for (Iterator iterator = extAttributes.keySet().iterator(); iterator
                .hasNext();) {
            key = (QName) iterator.next();
            if (PolicyConstants.POLICY_NAMESPACE_URI.equals(key
                    .getNamespaceURI())
                    && "PolicyURIs".equals(key.getLocalPart())) {
                value = (QName) extAttributes.get(key);
                String policyURIs = value.getLocalPart();

                if (policyURIs.length() != 0) {
                    String[] uris = policyURIs.split(" ");

                    PolicyReference ref;
                    for (int i = 0; i < uris.length; i++) {
                        ref = new PolicyReference(uris[i]);

                        if (PORT_TYPE.equals(origin)) {
                            PolicyInclude include = description
                                    .getPolicyInclude();
                            include.addPolicyRefElement(
                                    PolicyInclude.PORT_TYPE_POLICY, ref);
                        }
                    }
                }
            }
        }

    }

    /**
     * Process the policy definitions
     *
     * @param definition
     */
    private void processPoliciesInDefintion(Definition definition) {
        List extElements = definition.getExtensibilityElements();

        ExtensibilityElement extElement;
        for (Iterator iterator = extElements.iterator(); iterator.hasNext();) {
            extElement = (ExtensibilityElement) iterator.next();

            if (extElement instanceof UnknownExtensibilityElement) {
                UnknownExtensibilityElement unknown = (UnknownExtensibilityElement) extElement;
                if (WSDLConstants.WSDL11Constants.POLICY.equals(unknown.getElementType())) {

                    DOMPolicyReader policyReader = (DOMPolicyReader) PolicyFactory
                            .getPolicyReader(PolicyFactory.DOM_POLICY_READER);
                    Policy policy = policyReader.readPolicy(unknown
                            .getElement());

                    if (policy.getId() != null) {
                        registry.register(policy.getId(), policy);
                    }

                    if (policy.getName() != null) {
                        registry.register(policy.getName(), policy);
                    }
                }
            }
        }
    }

    /**
     * Inner class declaration for the processing exceptions
     */
    public static class WSDLProcessingException extends RuntimeException {
        public WSDLProcessingException() {
        }

        public WSDLProcessingException(String message) {
            super(message);
        }

        public WSDLProcessingException(Throwable cause) {
            super(cause);
        }

        public WSDLProcessingException(String message, Throwable cause) {
            super(message, cause);
        }

    }
}
