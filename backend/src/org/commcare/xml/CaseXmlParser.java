/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Date;
import java.util.NoSuchElementException;

import org.commcare.cases.model.Case;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The CaseXML Parser is responsible for processing and performing
 * case transactions from an incoming XML stream. It will perform
 * all of the actions specified by the transaction (Create/modify/close)
 * against the application's current storage. 
 * 
 * @author ctsims
 *
 */
public class CaseXmlParser extends TransactionParser<Case> {

	IStorageUtilityIndexed storage;
	int[] tallies;
	boolean acceptCreateOverwrites;

	
	public CaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
		this(parser, new int[3], true, storage);
	}
	
	/**
	 * Creates a Parser for case blocks in the XML stream provided. 
	 * 
	 * @param parser The parser for incoming XML.
	 * @param tallies an int[3] array to place information about the parser's actions.
	 * @param acceptCreateOverwrites Whether an Exception should be thrown if the transaction
	 * contains create actions for cases which already exist.
	 */
	public CaseXmlParser(KXmlParser parser, int[] tallies, boolean acceptCreateOverwrites, IStorageUtilityIndexed storage) {
		super(parser, "case", null);
		this.tallies = tallies;
		this.acceptCreateOverwrites = acceptCreateOverwrites;
		this.storage = storage;
	}

	public Case parse() throws InvalidStructureException, IOException, XmlPullParserException {
		this.checkNode("case");
		
		String caseId = parser.getAttributeValue(null, "case_id");
		if(caseId == null) { throw new InvalidStructureException("<case> block with no case_id attribute.", this.parser); }
		
		String dateModified = parser.getAttributeValue(null, "date_modified");
		if(dateModified == null) { throw new InvalidStructureException("<case> block with no date_modified attribute.", this.parser); }
		Date modified = DateUtils.parseDateTime(dateModified);

		
		boolean create = false;
		boolean update = false;
		boolean close = false;
		
		//Now look for actions
		while(this.nextTagInBlock("case")) {
			
			String action = parser.getName().toLowerCase();
			
			if(action.equals("create")) {
				String[] data = new String[3];
				//Collect all data
				while(this.nextTagInBlock("create")) {
					if(parser.getName().equals("case_type")) {
						data[0] = parser.nextText().trim();
					} else if(parser.getName().equals("owner_id")) {
						data[1] = parser.nextText().trim();
					} else if(parser.getName().equals("case_name")) {
						data[2] = parser.nextText().trim();
					} else {
						throw new InvalidStructureException("Expected one of [case_type, owner_id, case_name], found " + parser.getName(), parser);
					}
				}
				
				//Verify that we got all the pieces
				if(data[0] == null || data[2] == null) {
					throw new InvalidStructureException("One of [case_type, case_name] is missing for case <create> with ID: " + caseId, parser);
				}
				
				Case c = null;
				boolean overriden = false;
				//CaseXML Block is Valid. If we're on loose tolerance, first check if the case exists
				if(acceptCreateOverwrites) {
					//If it exists, try to retrieve it
					c = retrieve(caseId);
					
					//If we found one, override the existing data
					if(c != null) {
						c.setName(data[2]);
						c.setTypeId(data[0]);
						overriden = true;
					}
				} 
				
				if(c == null) {
					//The case is either not present on the phone, or we're on strict tolerance
					c = CreateCase(data[2], data[0]);
					c.setCaseId(caseId);
					c.setDateOpened(modified);
				}
				
				if(data[1] != null) {
					c.setUserId(data[1]);
				}
				commit(c);
				if(!overriden) {
					create = true;
				}
				String succesfulAction = overriden ? "case-recreate" : "case-create";
				Logger.log(succesfulAction, c.getID() + ";" + PropertyUtils.trim(c.getCaseId(), 12) + ";" + c.getTypeId());
				
			} else if(action.equals("update")) {
				Case c = retrieve(caseId);
				if(c == null) {
					throw new InvalidStructureException("No case found for update. Skipping ID: " + caseId, parser);
				}
				while(this.nextTagInBlock("update")) {
					String key = parser.getName();
					String value = parser.nextText().trim();
					if(key.equals("case_type")) {
						c.setTypeId(value);
					} else if(key.equals("case_name")) {
						c.setName(value);
					} else if(key.equals("date_opened")) {
						c.setDateOpened(DateUtils.parseDate(value));
					} else if(key.equals("owner_id")) {
						c.setUserId(value);
					} else{
						c.setProperty(key,value);
					}
				}
				commit(c);
				update = true;
			} else if(action.equals("close")) {
				Case c = retrieve(caseId);
				if(c == null) {
					throw new InvalidStructureException("No case found for update. Skipping ID: " + caseId, parser);
				}
				c.setClosed(true);
				commit(c);
				Logger.log("case-close", PropertyUtils.trim(c.getCaseId(), 12));
				close = true;
			} else if(action.equals("index")) {
				Case c = retrieve(caseId);
				while(this.nextTagInBlock("index")) {
					String indexName = parser.getName();
					String caseType = parser.getAttributeValue(null, "case_type");
					String value = parser.nextText().trim();
					
					c.setIndex(indexName, caseType, value);
				}
				commit(c);
			}
		}
		
		if (create) {
			tallies[0]++;
		} else if (close) {
			tallies[2]++;
		} else if (update) {
			tallies[1]++;
		}
		
		return null;
	}		

	protected Case CreateCase(String name, String typeId) {
		return new Case(name, typeId);
	}

	public void commit(Case parsed) throws IOException {
		try {
			storage().write(parsed);
		} catch (StorageFullException e) {
			e.printStackTrace();
			throw new IOException("Storage full while writing case!");
		}
	}

	public Case retrieve(String entityId) {
		try{
			return (Case)storage().getRecordForValue(Case.INDEX_CASE_ID, entityId);
		} catch(NoSuchElementException nsee) {
			return null;
		}
	}
	
	public IStorageUtilityIndexed storage() {
		return storage;
	}

}