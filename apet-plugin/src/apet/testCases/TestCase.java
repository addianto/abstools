package apet.testCases;


import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class TestCase {
	String method_name;
	ArrayList<ABSData> argsIn;
	HashMap<ABSRef,ABSObject> heapIn;
	ABSData returnData;
	HashMap<ABSRef,ABSObject> heapOut;
	
	public TestCase(Element elem) throws Exception{

		// get method name
		this.parseMethodName(elem);
		
		//get args in
		this.parseArgsIn(elem);
		 
		//get heap in
		this.parseHeapIn(elem);
		
		//get return
		this.parseReturn(elem);
		
		//get heap out
		this.parseHeapOut(elem);
		
	}
		
	private void parseMethodName(Element elem) throws Exception{
    	
    	NodeList methodList = elem.getElementsByTagName("method");
		if (methodList.getLength()!=1) throw new Exception();
		method_name = methodList.item(0).getTextContent();
		/**/if (XMLParser.debug) System.out.println(method_name);
    }
    
    private void parseArgsIn(Element elem) throws Exception{
    	this.argsIn = new ArrayList<ABSData>();
    	NodeList argsInList = elem.getElementsByTagName("args_in");
		Element argsIn;
		if (argsInList.getLength()!=1) throw new Exception();
		argsIn =(Element) argsInList.item(0);
		/**/if (XMLParser.debug) System.out.println(argsIn.getNodeName());
		NodeList argList = argsIn.getElementsByTagName("*");
		for (int i=0; i<argList.getLength(); i++) {
			Element argInElem = (Element) argList.item(i); 
			ABSData argIn = ABSData.parseData(argInElem);
			this.argsIn.add(argIn);
			/**/if (XMLParser.debug) System.out.println("Type: " + argInElem.getNodeName() + " Value: " + argInElem.getTextContent());			
		}
    }
    
	private void parseHeapIn(Element elem) throws Exception {
		this.heapIn = new HashMap<ABSRef,ABSObject>();
		NodeList heapInList = elem.getElementsByTagName("heap_in");
		Element heapInElem;
		if (heapInList.getLength()!=1) throw new Exception();
		heapInElem = (Element) heapInList.item(0);
		/**/if (XMLParser.debug) System.out.println(heapInElem.getNodeName());
		NodeList cellList = heapInElem.getElementsByTagName("cell");
		for (int i=0; i<cellList.getLength(); i++) {
			this.parseCell((Element) cellList.item(i), this.heapIn);
		}
	}
	
	private void parseCell(Element item, HashMap<ABSRef, ABSObject> heap) throws Exception {
		/**/if (XMLParser.debug) System.out.println(item.getNodeName());
		NodeList refList = item.getElementsByTagName("ref");
		if (refList.getLength()!=1)throw new Exception();
		Element refElem = (Element) refList.item(0);
		ABSRef ref = new ABSRef(refElem);
		/**/if (XMLParser.debug) System.out.println("Type: " + refElem.getNodeName() + " Value: " + refElem.getTextContent());
		NodeList objectList = item.getElementsByTagName("object");
		if (objectList.getLength()!=1)throw new Exception();
		Element objectElem = (Element) objectList.item(0);	
		ABSObject object = new ABSObject(objectElem);
		heap.put(ref, object);	
	}

	private void parseReturn(Element elem) throws Exception {
    	NodeList returnElemList = elem.getElementsByTagName("return");
		Element returnElem;
		if (returnElemList.getLength()!=1) throw new Exception();
		returnElem =(Element) returnElemList.item(0);
		this.returnData = ABSData.parseData(returnElem);
		/**/if (XMLParser.debug) System.out.println(returnElem.getNodeName() + "\n" + returnElem.getTextContent());
	}
	
	private void parseHeapOut(Element elem) throws Exception {
		this.heapOut = new HashMap<ABSRef,ABSObject>();
		NodeList heapOutList = elem.getElementsByTagName("heap_out");
		Element heapOutElem;
		if (heapOutList.getLength()!=1) throw new Exception();
		heapOutElem = (Element) heapOutList.item(0);
		/**/if (XMLParser.debug) System.out.println(heapOutElem.getNodeName());
		NodeList cellList = heapOutElem.getElementsByTagName("cell");
		for (int i=0; i<cellList.getLength(); i++) {
			this.parseCell((Element) cellList.item(i), this.heapOut);
		}
	}

}
