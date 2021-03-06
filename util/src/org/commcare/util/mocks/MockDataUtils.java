package org.commcare.util.mocks;

import org.commcare.data.xml.DataModelPullParser;
import org.commcare.util.CommCareTransactionParserFactory;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageManager;
import org.javarosa.core.util.ArrayUtilities;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Methods that mostly are used around the mocks that replicate stuff from
 * other projects.
 *
 * TODO: We should try to centralize how these are used.
 *
 * @author ctsims
 */
public class MockDataUtils {

    public static MockUserDataSandbox getStaticStorage() {
        LivePrototypeFactory factory = new LivePrototypeFactory();
        PrototypeFactory.setStaticHasher(factory);
        return new MockUserDataSandbox(factory);
    }

    /**
     * Parse the transactions int he provided stream into the user sandbox provided.
     *
     * Will rethrow any errors and failfast (IE: Parsing will stop)
     */
    public static void parseIntoSandbox(InputStream stream, MockUserDataSandbox sandbox) throws IOException,
            UnfullfilledRequirementsException,
            XmlPullParserException, InvalidStructureException {
        parseIntoSandbox(stream, sandbox, true);
    }

    /**
     * Parse the transactions int he provided stream into the user sandbox provided.
     *
     * If failfast is true, will rethrow any errors and failfast (IE: Parsing will stop)
     */
    public static void parseIntoSandbox(InputStream stream, MockUserDataSandbox sandbox, boolean failfast) throws IOException,
            UnfullfilledRequirementsException,
            XmlPullParserException, InvalidStructureException {
        if(stream == null) {
            throw new IOException("Parse Stream is Null");
        }

        CommCareTransactionParserFactory factory = new CommCareTransactionParserFactory(sandbox);
        DataModelPullParser parser = new DataModelPullParser(stream, factory, failfast, true);
        parser.parse();
    }

    /**
     * For the users and groups in the provided sandbox, extracts out the list
     * of valid "owners" for entities (cases, ledgers, etc) in the universe.
     *
     * Borrowed from Android implementation, should likely be centralized.
     *
     * TODO: Move this static functionality into CommCare generally.
     */
    public static Vector<String> extractEntityOwners(MockUserDataSandbox sandbox) {
        Vector<String> owners = new Vector<String>();
        Vector<String> users = new Vector<String>();

        for (IStorageIterator<User> userIterator = sandbox.getUserStorage().iterate(); userIterator.hasMore(); ) {
            String id = userIterator.nextRecord().getUniqueId();
            owners.addElement(id);
            users.addElement(id);
        }

        //Now add all of the relevant groups
        //TODO: Wow. This is.... kind of megasketch
        for (String userId : users) {
            DataInstance instance = loadFixture(sandbox, "user-groups", userId);
            if (instance == null) {
                continue;
            }
            EvaluationContext ec = new EvaluationContext(instance);
            for (TreeReference ref : ec.expandReference(XPathReference.getPathExpr("/groups/group/@id").getReference())) {
                AbstractTreeElement<AbstractTreeElement> idelement = ec.resolveReference(ref);
                if (idelement.getValue() != null) {
                    owners.addElement(idelement.getValue().uncast().getString());
                }
            }
        }

        return owners;
    }

    /**
     * Load the referenced fixture out of storage for the provided user
     */
    public static FormInstance loadFixture(MockUserDataSandbox sandbox,
                                            String refId, String userId) {
        IStorageUtilityIndexed<FormInstance> userFixtureStorage =
                sandbox.getUserFixtureStorage();

        IStorageUtilityIndexed<FormInstance> appFixtureStorage = null;
        //this isn't great but generally this initialization path is actually
        //really hard/unclear for now, and we can't really assume the sandbox owns
        //this because it's app data, not user data.
        try {
            appFixtureStorage =
                    (IStorageUtilityIndexed)StorageManager.getStorage("fixture");
        } catch(RuntimeException re) {
            //We use this in some contexsts with app fixture storage and some without, so
            //if we don't find it, that's ok.
            //This behavior will need to get updated if this code is used outside of the util/test
            //context
        }

        Vector<Integer> userFixtures =
                userFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
        // ... Nooooot so clean.
        if (userFixtures.size() == 1) {
            // easy case, one fixture, use it
            return userFixtureStorage.read(userFixtures.elementAt(0).intValue());
            // TODO: Userid check anyway?
        } else if (userFixtures.size() > 1) {
            // intersect userid and fixtureid set.
            // TODO: Replace context call here with something from the session,
            // need to stop relying on that coupling
            Vector<Integer> relevantUserFixtures =
                    userFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId);

            if (relevantUserFixtures.size() != 0) {
                Integer userFixture =
                        ArrayUtilities.intersectSingle(userFixtures, relevantUserFixtures);
                if (userFixture != null) {
                    return userFixtureStorage.read(userFixture.intValue());
                }
            }
        }

        // ok, so if we've gotten here there were no fixtures for the user,
        // let's try the app fixtures.

        //First see if app storage is even available, if not, we aren't gonna find one
        if(appFixtureStorage == null) {
            return null;
        }

        Vector<Integer> appFixtures = appFixtureStorage.getIDsForValue(FormInstance.META_ID, refId);
        Integer globalFixture =
                ArrayUtilities.intersectSingle(appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, ""), appFixtures);
        if (globalFixture != null) {
            return appFixtureStorage.read(globalFixture.intValue());
        } else {
            // See if we have one manually placed in the suite
            Integer userFixture =
                    ArrayUtilities.intersectSingle(appFixtureStorage.getIDsForValue(FormInstance.META_XMLNS, userId), appFixtures);
            if (userFixture != null) {
                return appFixtureStorage.read(userFixture.intValue());
            }
            // Otherwise, nothing
            return null;
        }
    }

    /**
     * Create an evaluation context with an abstract instance available.
     */
    public static EvaluationContext buildContextWithInstance(MockUserDataSandbox sandbox, String instanceId, String instanceRef){
        Hashtable<String, String> instanceRefToId = new Hashtable<>();
        instanceRefToId.put(instanceRef, instanceId);
        return buildContextWithInstances(sandbox, instanceRefToId);
    }

    /**
     * Create an evaluation context with an abstract instances available.
     */
    public static EvaluationContext buildContextWithInstances(MockUserDataSandbox sandbox,
                                                              Hashtable<String, String> instanceRefToId) {
        InstanceInitializationFactory iif = new CommCareInstanceInitializer(sandbox);

        Hashtable<String, DataInstance> instances = new Hashtable<>();
        for (String instanceRef : instanceRefToId.keySet()) {
            String instanceId = instanceRefToId.get(instanceRef);
            ExternalDataInstance edi = new ExternalDataInstance(instanceRef, instanceId);

            instances.put(instanceId, edi.initialize(iif, instanceId));
        }

        return new EvaluationContext(null, instances);
    }
}
