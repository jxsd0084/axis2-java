/*
* Copyright 2004,2005 The Apache Software Foundation.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.axis2.engine;

import org.apache.axis2.AxisFault;
import org.apache.axis2.storage.AxisStorage;
import org.apache.axis2.deployment.DeploymentEngine;
import org.apache.axis2.deployment.repository.util.ArchiveReader;
import org.apache.axis2.deployment.util.PhasesInfo;
import org.apache.axis2.description.*;
import org.apache.axis2.phaseresolver.PhaseMetadata;
import org.apache.axis2.phaseresolver.PhaseResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.*;

/**
 * Class EngineRegistryImpl
 */
public class AxisConfigurationImpl implements AxisConfiguration {
    /**
     * To store Erroness services
     */
    private Hashtable errornesServices;

    private HashMap moduleConfigmap;

    //to keep axis2 storage class
    private AxisStorage axisStorage;

    private Hashtable errornesModules;
    private Log log = LogFactory.getLog(getClass());

    /**
     * Field modules
     */
    private final HashMap modules = new HashMap();

    /**
     * Field services
     */
    private final HashMap services = new HashMap();


    private final HashMap transportsIn = new HashMap();

    private final HashMap transportsOut = new HashMap();

    /**
     * Field phases
     */
    private ArrayList inPhases;
    private ArrayList outPhases;
    private ArrayList inFaultPhases;
    private ArrayList outFaultPhases;

    private ArrayList inPhasesUptoAndIncludingPostDispatch;



    /////////////////////// From AxisGlobal /////////////////////////////////////
    /**
     * Field paramInclude
     */
    protected final ParameterInclude paramInclude;


    protected PhasesInfo phasesinfo;

    /**
     * Field modules
     */
    protected final List engagedModules;

    private String axis2Repository = null;

    //to store AxisObserver Objects
    private ArrayList observersList = null;

    protected HashMap messagReceivers;
    /////////////////////// From AxisGlobal /////////////////////////////////////
    /**
     * Constructor EngineRegistryImpl
     */
    public AxisConfigurationImpl() {
        paramInclude = new ParameterIncludeImpl();
        engagedModules = new ArrayList();
        messagReceivers = new HashMap();

        inPhases = new ArrayList();
        outPhases = new ArrayList();
        inFaultPhases = new ArrayList();
        outFaultPhases = new ArrayList();
        errornesServices = new Hashtable();
        errornesModules = new Hashtable();
        observersList = new ArrayList();

        inPhasesUptoAndIncludingPostDispatch = new ArrayList();
        inPhasesUptoAndIncludingPostDispatch.add(
                new Phase(PhaseMetadata.PHASE_TRANSPORTIN));
        inPhasesUptoAndIncludingPostDispatch.add(
                new Phase(PhaseMetadata.PHASE_PRE_DISPATCH));

        Phase dispatch = new Phase(PhaseMetadata.PHASE_DISPATCH);
        AddressingBasedDispatcher add_dispatch = new AddressingBasedDispatcher();
        add_dispatch.getHandlerDesc().setParent(this);
        dispatch.addHandler(add_dispatch, 0);

        RequestURIBasedDispatcher uri_diaptch = new RequestURIBasedDispatcher();
        uri_diaptch.getHandlerDesc().setParent(this);
        dispatch.addHandler(uri_diaptch, 1);

        SOAPActionBasedDispatcher soapActionBased_dispatch = new SOAPActionBasedDispatcher();
        soapActionBased_dispatch.getHandlerDesc().setParent(this);
        dispatch.addHandler(soapActionBased_dispatch, 2);

        SOAPMessageBodyBasedDispatcher soapMessageBodybased_dispatch =
                new SOAPMessageBodyBasedDispatcher();
        soapMessageBodybased_dispatch.getHandlerDesc().setParent(this);
        dispatch.addHandler(soapMessageBodybased_dispatch, 3);

        inPhasesUptoAndIncludingPostDispatch.add(dispatch);

        Phase postDispatch = new Phase(PhaseMetadata.PHASE_POST_DISPATCH);
        DispatchingChecker dispatchingChecker = new DispatchingChecker();
        dispatchingChecker.getHandlerDesc().setParent(this);

        postDispatch.addHandler(dispatchingChecker);
        inPhasesUptoAndIncludingPostDispatch.add(postDispatch);
    }

    /**
     * Method getServices
     *
     * @return
     */
    public HashMap getServices() {
        return services;
    }

    public Hashtable getFaulytServices() {
        return errornesServices;
    }

    public Hashtable getFaulytModules() {
        return errornesModules;
    }

    /**
     * Method addMdoule
     *
     * @param module
     * @throws AxisFault
     */
    public synchronized void addModule(ModuleDescription module) throws AxisFault {
        module.setParent(this);
        modules.put(module.getName(), module);
    }

    /**
     * Method addService
     *
     * @param service
     * @throws AxisFault
     */
    public synchronized void addService(ServiceDescription service) throws AxisFault {
        services.put(service.getName(), service);
        PhaseResolver handlerResolver = new PhaseResolver(this, service);
        handlerResolver.buildchains();
        service.setLastupdate();
        notifyObserves(AxisEvent.SERVICE_DEPLOY ,service);
        service.setParent(this);
    }

    /**
     * Method getModule
     *
     * @param name
     * @return
     * @throws AxisFault
     */
    public ModuleDescription getModule(QName name) {
        return (ModuleDescription) modules.get(name);
    }

    /**
     * @return
     */
    public HashMap getModules() {
        return modules;
    }


    /**
     * Method getService
     *
     * @param name
     * @return
     * @throws AxisFault
     */
    public ServiceDescription getService(QName name) throws AxisFault {
        return (ServiceDescription) services.get(name);
    }

    /**
     * Method removeService
     *
     * @param name
     * @throws AxisFault
     */
    public synchronized void removeService(QName name) throws AxisFault {
        ServiceDescription service = getService(name);
        if (service != null) {
            notifyObserves(AxisEvent.SERVICE_DEPLOY , service);
        }
        services.remove(name);
    }


    public TransportInDescription getTransportIn(QName name) throws AxisFault {
        return (TransportInDescription) transportsIn.get(name);
    }

    /**
     * Method addTransport
     *
     * @param transport
     * @throws AxisFault
     */
    public synchronized void addTransportIn(TransportInDescription transport)
            throws AxisFault {
        transportsIn.put(transport.getName(), transport);
    }

    public TransportOutDescription getTransportOut(QName name) throws AxisFault {
        return (TransportOutDescription) transportsOut.get(name);
    }

    /**
     * Method addTransport
     *
     * @param transport
     * @throws AxisFault
     */
    public synchronized void addTransportOut(TransportOutDescription transport)
            throws AxisFault {
        transportsOut.put(transport.getName(), transport);
    }

    public HashMap getTransportsIn() {
        return transportsIn;
    }

    public HashMap getTransportsOut() {
        return transportsOut;
    }

    //to get and set Axis2 storges (the class which should handle storeg)
    public void setAxisStorage(AxisStorage axisStorage) {
        this.axisStorage =axisStorage;
    }

    public AxisStorage getAxisStorage() {
        return axisStorage;
    }

    //to check whether a given paramter is locked
    public boolean isParamterLocked(String paramterName) {
        Parameter parameter = getParameter(paramterName);
        if(parameter != null && parameter.isLocked()){
            return true;
        } else {
            return false;
        }
    }


    public void setInPhases(ArrayList inPhases) {
        this.inPhases = inPhases;
    }

    public void setOutPhases(ArrayList outPhases) {
        this.outPhases = outPhases;
    }


    public ArrayList getInPhasesUptoAndIncludingPostDispatch() {
        return inPhasesUptoAndIncludingPostDispatch;
    }

    public ArrayList getOutFlow() {
        return outPhases;
    }


    /**
     * @return
     */
    public ArrayList getInFaultFlow() {
        return inFaultPhases;
    }

    /**
     * @return
     */
    public ArrayList getOutFaultFlow() {
        return outFaultPhases;
    }

    /**
     * @param list
     */
    public void setInFaultPhases(ArrayList list) {
        inFaultPhases = list;
    }

    /**
     * @param list
     */
    public void setOutFaultPhases(ArrayList list) {
        outFaultPhases = list;
    }

    ////////////////////////// Form Axis Global

    public void addMessageReceiver(String key,
                                   MessageReceiver messageReceiver) {
        messagReceivers.put(key, messageReceiver);
    }

    public MessageReceiver getMessageReceiver(String key) {
        return (MessageReceiver) messagReceivers.get(key);
    }

    /**
     * Method getParameter
     *
     * @param name
     * @return
     */
    public Parameter getParameter(String name) {
        return paramInclude.getParameter(name);
    }

    public ArrayList getParameters() {
        return paramInclude.getParameters();
    }

    /**
     * Method addParameter
     *
     * @param param
     */
    public void addParameter(Parameter param) throws AxisFault{
        if(isParamterLocked(param.getName())){
            throw new AxisFault("Parmter is locked can not overide: " + param.getName());
        } else{
            paramInclude.addParameter(param);
        }
    }

    /**
     * Method getEngadgedModules
     *
     * @return
     */
    public Collection getEngadgedModules() {
        return engagedModules;
    }

    public void engageModule(QName moduleref) throws AxisFault {
        ModuleDescription module = getModule(moduleref);
        boolean isNewmodule = false;
        boolean tobeEnaged = true;
        if (module == null) {
            File file = new ArchiveReader().creatModuleArchivefromResource(
                    moduleref.getLocalPart(), getRepository());
            module = new DeploymentEngine().buildModule(file);
            isNewmodule = true;
        }
        if (module != null) {
            for (Iterator iterator = engagedModules.iterator();
                 iterator.hasNext();) {
                QName qName = (QName) iterator.next();
                if (moduleref.equals(qName)) {
                    tobeEnaged = false;
                    //Instead of throwing the error, we can just log this problem
                    log.info("Attempt to engage an already engaged module "+ qName);
//                    throw new AxisFault(moduleref.getLocalPart() +
//                            " module has alredy engaged globally" +
//                            "  operation terminated !!!");
                }
            }
        } else {
            throw new AxisFault(
                    this + " Refer to invalid module "
                            + moduleref.getLocalPart() +
                            " has not bean deployed yet !");
        }
        if (tobeEnaged) {
            new PhaseResolver(this).engageModuleGlobally(module);
            engagedModules.add(moduleref);
        }
        if (isNewmodule) {
            addModule(module);
        }
    }

    public boolean isEngaged(QName moduleName) {
        return engagedModules.contains(moduleName);
    }

    public PhasesInfo getPhasesinfo() {
        return phasesinfo;
    }

    public void setPhasesinfo(PhasesInfo phasesinfo) {
        this.phasesinfo = phasesinfo;
    }

    public String getRepository() {
        return axis2Repository;
    }

    public void setRepository(String axis2Repository) {
        this.axis2Repository = axis2Repository;
    }

    private void notifyObserves(int event_type , ServiceDescription service){
        AxisEvent event = new AxisEvent(service,event_type);
        for (int i = 0; i < observersList.size(); i++) {
            AxisObserver axisObserver = (AxisObserver) observersList.get(i);
            axisObserver.update(event);
        }
    }

    public void addObservers(AxisObserver axisObserver){
        observersList.add(axisObserver);
    }


    /**
     * Adding module configuration , if there is moduleConfig tag in service
     * @param moduleConfiguration
     */
    public void addModuleConfig(ModuleConfiguration moduleConfiguration){
        if(moduleConfigmap == null){
            moduleConfigmap = new HashMap();
        }
        moduleConfigmap.put(moduleConfiguration.getModuleName(),moduleConfiguration);
    }

    public ModuleConfiguration getModuleConfig(QName moduleName){
        return  (ModuleConfiguration)moduleConfigmap.get(moduleName);
    }


}
