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
package org.apache.axis2.clustering.tribes;

import org.apache.catalina.tribes.group.interceptors.NonBlockingCoordinator;

/**
 *  The non-blocking coordinator interceptor 
 */
public class Axis2Coordinator extends NonBlockingCoordinator {
    public void fireInterceptorEvent(InterceptorEvent event) {
        int type = event.getEventType();
        if (type == CoordinationEvent.EVT_CONF_RX && isCoordinator()) {
            //TODO: Implement specif
        }
    }
}