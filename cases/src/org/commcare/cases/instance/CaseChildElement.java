/**
 *
 */
package org.commcare.cases.instance;

import org.commcare.cases.model.Case;
import org.commcare.cases.model.CaseIndex;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.DateData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.instance.utils.ITreeVisitor;
import org.javarosa.core.model.utils.PreloadUtils;
import org.javarosa.xpath.expr.XPathExpression;

import java.util.Enumeration;
import java.util.Vector;

/**
 * @author ctsims
 */
public class CaseChildElement implements AbstractTreeElement<TreeElement> {

    CaseInstanceTreeElement parent;
    int recordId;
    String caseId;
    int mult;

    TreeElement empty;

    int numChildren = -1;

    public CaseChildElement(CaseInstanceTreeElement parent, int recordId, String caseId, int mult) {
        if (recordId == -1 && caseId == null) {
            throw new RuntimeException("Cannot create a lazy case element with no lookup identifiers!");
        }
        this.parent = parent;
        this.recordId = recordId;
        this.caseId = caseId;
        this.mult = mult;
    }

    /*
     * Template constructor (For elements that need to create reference nodesets but never look up values)
     */
    private CaseChildElement(CaseInstanceTreeElement parent) {
        //Template
        this.parent = parent;
        this.recordId = TreeReference.INDEX_TEMPLATE;
        this.mult = TreeReference.INDEX_TEMPLATE;
        this.caseId = null;

        empty = new TreeElement();
        empty = new TreeElement("case");
        empty.setMult(this.mult);

        empty.setAttribute(null, "case_id", "");
        empty.setAttribute(null, "case_type", "");
        empty.setAttribute(null, "status", "");

        TreeElement scratch = new TreeElement("case_name");
        scratch.setAnswer(null);
        empty.addChild(scratch);

        scratch = new TreeElement("date_opened");
        scratch.setAnswer(null);
        empty.addChild(scratch);

        scratch = new TreeElement("last_modified");
        scratch.setAnswer(null);
        empty.addChild(scratch);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#isLeaf()
     */
    public boolean isLeaf() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#isChildable()
     */
    public boolean isChildable() {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getInstanceName()
     */
    public String getInstanceName() {
        return parent.getInstanceName();
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getChild(java.lang.String, int)
     */
    public TreeElement getChild(String name, int multiplicity) {
        TreeElement cached = cache();
        TreeElement child = cached.getChild(name, multiplicity);
        if (multiplicity >= 0 && child == null) {
            TreeElement emptyNode = new TreeElement(name);
            cached.addChild(emptyNode);
            emptyNode.setParent(cached);
            return emptyNode;
        }
        return child;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getChildrenWithName(java.lang.String)
     */
    public Vector getChildrenWithName(String name) {
        //In order
        TreeElement cached = cache();
        Vector children = cached.getChildrenWithName(name);
        if (children.size() == 0) {
            TreeElement emptyNode = new TreeElement(name);
            cached.addChild(emptyNode);
            emptyNode.setParent(cached);
            children.addElement(emptyNode);
        }
        return children;
    }

    public boolean hasChildren() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getNumChildren()
     */
    public int getNumChildren() {
        if (numChildren == -1) {
            numChildren = cache().getNumChildren();
        }
        return numChildren;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getChildAt(int)
     */
    public TreeElement getChildAt(int i) {
        return cache().getChildAt(i);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#isRepeatable()
     */
    public boolean isRepeatable() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#isAttribute()
     */
    public boolean isAttribute() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getChildMultiplicity(java.lang.String)
     */
    public int getChildMultiplicity(String name) {
        return cache().getChildMultiplicity(name);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#accept(org.javarosa.core.model.instance.utils.ITreeVisitor)
     */
    public void accept(ITreeVisitor visitor) {
        visitor.visit(this);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttributeCount()
     */
    public int getAttributeCount() {
        //TODO: Attributes should be fixed and possibly only include meta-details
        return cache().getAttributeCount();
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttributeNamespace(int)
     */
    public String getAttributeNamespace(int index) {
        return cache().getAttributeNamespace(index);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttributeName(int)
     */
    public String getAttributeName(int index) {
        return cache().getAttributeName(index);

    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttributeValue(int)
     */
    public String getAttributeValue(int index) {
        return cache().getAttributeValue(index);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttribute(java.lang.String, java.lang.String)
     */
    public TreeElement getAttribute(String namespace, String name) {
        if (name.equals("case_id")) {
            if (recordId != TreeReference.INDEX_TEMPLATE) {
                //if we're already cached, don't bother with this nonsense
                synchronized (parent.treeCache) {
                    TreeElement element = parent.treeCache.retrieve(recordId);
                    if (element != null) {
                        return cache().getAttribute(namespace, name);
                    }
                }
            }

            //TODO: CACHE GET ID THING
            if (caseId == null) {
                return cache().getAttribute(namespace, name);
            }

            //otherwise, don't cache this just yet if we have the ID handy
            TreeElement caseid = TreeElement.constructAttributeElement(null, name);
            caseid.setValue(new StringData(caseId));
            caseid.setParent(this);
            return caseid;
        }
        return cache().getAttribute(namespace, name);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getAttributeValue(java.lang.String, java.lang.String)
     */
    public String getAttributeValue(String namespace, String name) {
        if (name.equals("case_id")) {
            return caseId;
        }
        return cache().getAttributeValue(namespace, name);
    }

    TreeReference ref;

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getRef()
     */
    public TreeReference getRef() {
        if (ref == null) {
            ref = TreeElement.BuildRef(this);
        }
        return ref;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getDepth()
     */
    public int getDepth() {
        return TreeElement.CalculateDepth(this);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getName()
     */
    public String getName() {
        return "case";
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getMult()
     */
    public int getMult() {
        // TODO Auto-generated method stub
        return mult;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getParent()
     */
    public AbstractTreeElement getParent() {
        return parent;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getValue()
     */
    public IAnswerData getValue() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.model.instance.AbstractTreeElement#getDataType()
     */
    public int getDataType() {
        // TODO Auto-generated method stub
        return 0;
    }

    //TODO: Thread Safety!
    public void clearCaches() {
        //cached = null;
    }

    static final String LAST_MODIFIED_KEY = "last_modified";

    //TODO: THIS IS NOT THREAD SAFE
    private TreeElement cache() {
        if (recordId == TreeReference.INDEX_TEMPLATE) {
            return empty;
        }
        synchronized (parent.treeCache) {
            TreeElement element = parent.treeCache.retrieve(recordId);
            if (element != null) {
                return element;
            }
            //For now this seems impossible
            if (recordId == -1) {
                Vector<Integer> ids = parent.storage.getIDsForValue("case_id", caseId);
                recordId = ids.elementAt(0).intValue();
            }

            TreeElement cacheBuilder = new TreeElement("case");
            Case c = parent.getCase(recordId);
            caseId = c.getCaseId();
            cacheBuilder = new TreeElement("case");
            cacheBuilder.setMult(this.mult);

            cacheBuilder.setAttribute(null, "case_id", c.getCaseId());
            cacheBuilder.setAttribute(null, "case_type", c.getTypeId());
            cacheBuilder.setAttribute(null, "status", c.isClosed() ? "closed" : "open");

            //Don't set anything to null
            cacheBuilder.setAttribute(null, "owner_id", c.getUserId() == null ? "" : c.getUserId());

            final boolean[] done = new boolean[]{false};

            //If we're not in report node, fill in all of this data
            if (!parent.reportMode) {

                TreeElement scratch = new TreeElement("case_name");
                String name = c.getName();
                //This shouldn't be possible
                scratch.setAnswer(new StringData(name == null ? "" : name));
                cacheBuilder.addChild(scratch);


                scratch = new TreeElement("date_opened");
                scratch.setAnswer(new DateData(c.getDateOpened()));
                cacheBuilder.addChild(scratch);

                scratch = new TreeElement(LAST_MODIFIED_KEY);
                scratch.setAnswer(new DateData(c.getLastModified()));
                cacheBuilder.addChild(scratch);

                for (Enumeration en = c.getProperties().keys(); en.hasMoreElements(); ) {
                    String key = (String)en.nextElement();

                    //this is an unfortunate complication of our internal model
                    if (LAST_MODIFIED_KEY.equals(key)) {
                        continue;
                    }

                    scratch = new TreeElement(parent.intern(key));
                    Object temp = c.getProperty(key);
                    if (temp instanceof String) {
                        scratch.setValue(new UncastData((String)temp));
                    } else {
                        scratch.setValue(PreloadUtils.wrapIndeterminedObject(temp));
                    }
                    cacheBuilder.addChild(scratch);
                }
                //TODO: Extract this pattern
                TreeElement index = new TreeElement("index") {
                    public TreeElement getChild(String name, int multiplicity) {
                        TreeElement child = super.getChild(CaseChildElement.this.parent.intern(name), multiplicity);

                        //TODO: Skeeeetchy, this is not a good way to do this,
                        //should extract pattern instead.

                        //If we haven't finished caching yet, we can safely not return
                        //something useful here, so we can construct as normal.
                        if (!done[0]) {
                            return child;
                        }

                        //blank template index for repeats and such to not crash
                        if (multiplicity >= 0 && child == null) {
                            TreeElement emptyNode = new TreeElement(CaseChildElement.this.parent.intern(name));
                            emptyNode.setAttribute(null, "case_type", "");
                            this.addChild(emptyNode);
                            emptyNode.setParent(this);
                            return emptyNode;
                        }
                        return child;
                    }

                    /* (non-Javadoc)
                     * @see org.javarosa.core.model.instance.AbstractTreeElement#getChildrenWithName(java.lang.String)
                     */
                    public Vector getChildrenWithName(String name) {
                        Vector children = super.getChildrenWithName(CaseChildElement.this.parent.intern(name));

                        //If we haven't finished caching yet, we can safely not return
                        //something useful here, so we can construct as normal.
                        if (!done[0]) {
                            return children;
                        }

                        if (children.size() == 0) {
                            TreeElement emptyNode = new TreeElement(name);
                            emptyNode.setAttribute(null, "case_type", "");

                            this.addChild(emptyNode);
                            emptyNode.setParent(this);
                            children.addElement(emptyNode);
                        }
                        return children;
                    }

                };

                Vector<CaseIndex> indices = c.getIndices();
                for (CaseIndex i : indices) {
                    scratch = new TreeElement(i.getName());
                    scratch.setAttribute(null, "case_type", this.parent.intern(i.getTargetType()));
                    scratch.setValue(new UncastData(i.getTarget()));
                    index.addChild(scratch);
                }
                cacheBuilder.addChild(index);

                TreeElement attachments = new TreeElement("attachment") {
                    public TreeElement getChild(String name, int multiplicity) {
                        TreeElement child = super.getChild(CaseChildElement.this.parent.intern(name), multiplicity);

                        //TODO: Skeeeetchy, this is not a good way to do this,
                        //should extract pattern instead.

                        //If we haven't finished caching yet, we can safely not return
                        //something useful here, so we can construct as normal.
                        if (!done[0]) {
                            return child;
                        }
                        if (multiplicity >= 0 && child == null) {
                            TreeElement emptyNode = new TreeElement(CaseChildElement.this.parent.intern(name));
                            this.addChild(emptyNode);
                            emptyNode.setParent(this);
                            return emptyNode;
                        }
                        return child;
                    }

                };

                for (String attachment : c.getAttachments()) {
                    scratch = new TreeElement(attachment);
                    scratch.setValue(new UncastData(c.getAttachmentSource(attachment)));
                    attachments.addChild(scratch);
                }
                cacheBuilder.addChild(attachments);
            }

            cacheBuilder.setParent(this.parent);
            done[0] = true;

            parent.treeCache.register(recordId, cacheBuilder);

            return cacheBuilder;
        }
    }

    public boolean isRelevant() {
        return true;
    }

    public static CaseChildElement buildCaseChildTemplate(CaseInstanceTreeElement parent) {
        return new CaseChildElement(parent);
    }

    public Vector<TreeReference> tryBatchChildFetch(String name, int mult, Vector<XPathExpression> predicates, EvaluationContext evalContext) {
        //TODO: We should be able to catch the index case here?
        return null;
    }

    public String getNamespace() {
        return null;
    }

}
