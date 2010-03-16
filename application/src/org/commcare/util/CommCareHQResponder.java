package org.commcare.util;

import org.javarosa.core.services.locale.Localization;
import org.javarosa.formmanager.view.transport.TransportResponseProcessor;
import org.javarosa.services.transport.CommUtil;
import org.javarosa.services.transport.TransportMessage;
import org.javarosa.services.transport.impl.simplehttp.SimpleHttpTransportMessage;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

public class CommCareHQResponder implements TransportResponseProcessor {

	public String getResponseMessage(TransportMessage message) {
    	String returnstr = "";
    	
    	// Make sure this is a normal HTTP message before trying anything fancy 
    	if(message.isSuccess() && message.getClass() == SimpleHttpTransportMessage.class) {
    		String numForms = "";
    		boolean understoodResponse = true;
    		byte[] response = null;
    		
    		// No class-cast-exception possible since we just checked
    		SimpleHttpTransportMessage msg = (SimpleHttpTransportMessage)message; 
		 
    		// 200 means everything is cool. 202 means data safe, but a problem
    		if( msg.getResponseCode() == 200 ) {
    			response = msg.getResponseBody();    			
    			Document doc = CommUtil.getXMLResponse(response);
    			
    			if (doc != null) {
    				Element e = doc.getRootElement();
	    			for (int i = 0; i < e.getChildCount(); i++) {
	    				if (e.getType(i) == Element.ELEMENT) {
	    					Element child = e.getElement(i);
	    					if(child.getName().equals("FormsSubmittedToday")) {
	    						numForms = child.getText(0);
	    						System.out.println("Found it! numforms:"+numForms);
	    						break;
	    					}
	    				}
	    			}
    			} else {
    				understoodResponse = false;
    			}
    		}
    		
    		if (!understoodResponse) {
    			returnstr = Localization.get("sending.status.didnotunderstand",
    					new String[] {response != null ? CommUtil.getString(response) : "[none]"});
    		} else if (numForms.equals(""))
    			returnstr = Localization.get("sending.status.problem.datasafe");
    		else
    			returnstr = Localization.get("sending.status.success", new String[]{numForms});	
    	}
    	
    	return returnstr;
	}
		

    
}