/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Vector;

import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Text;
import org.commcare.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class MenuParser extends ElementParser<Menu> {
	
	public MenuParser(KXmlParser parser) {
		super(parser);
	}

	/* (non-Javadoc)
	 * @see org.commcare.xml.ElementParser#parse()
	 */
	public Menu parse() throws InvalidStructureException {
		if(!parser.getName().toLowerCase().equals("menu")) {
			throw new InvalidStructureException();
		}

		String id = parser.getAttributeValue(null, "id");
		String root = parser.getAttributeValue(null, "root");
		root = root == null? "root" : root;
		if (!nextTagInBlock("menu")) {
			throw new InvalidStructureException();
		}
		
		Text name;

		if (!parser.getName().equals("text")) {
			throw new InvalidStructureException();
		} else {
			name = new TextParser(parser).parse();
		}

		Vector<String> commandIds = new Vector<String>();
		while (nextTagInBlock("menu")) {
			checkNode("command");
			commandIds.addElement(parser.getAttributeValue(null, "id"));
		}

		Menu m = new Menu(id, root, name, commandIds);
		return m;

	}

}