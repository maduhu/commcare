package org.commcare.util.mocks;

import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.util.restorable.Restorable;
import org.javarosa.core.model.util.restorable.RestoreUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Copied directly from JavaRosa, although we should likely move that model
 * somewhere in the global framing anyway.
 *
 * @author ctsims
 */
public class User implements Persistable, Restorable, IMetaData {
    public static final String STORAGE_KEY = "USER";

    private static final String ADMINUSER = "admin";
    private static final String STANDARD = "standard";
    private static final String DEMO_USER = "demo_user";
    private static final String KEY_USER_TYPE = "user_type";

    public static final String META_UID = "uid";
    private static final String META_USERNAME = "username";
    private static final String META_ID = "id";

    private int recordId = -1; //record id on device
    private String username;
    private String password;
    private String uniqueId;  //globally-unique id

    static private User demo_user;

    private boolean rememberMe = false;

    private String syncToken;

    private Hashtable<String, String> properties = new Hashtable<String, String>();

    public User() {
        setUserType(STANDARD);
    }

    public User(String name, String passw, String uniqueID) {
        this(name, passw, uniqueID, STANDARD);
    }

    private User(String name, String passw, String uniqueID, String userType) {
        username = name;
        password = passw;
        uniqueId = uniqueID;
        setUserType(userType);
        rememberMe = false;
    }

    // fetch the value for the default user and password from the RMS
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        this.username = ExtUtil.readString(in);
        this.password = ExtUtil.readString(in);
        this.recordId = ExtUtil.readInt(in);
        this.uniqueId = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.rememberMe = ExtUtil.readBool(in);
        this.syncToken = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.properties = (Hashtable)ExtUtil.read(in, new ExtWrapMap(String.class, String.class), pf);
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, username);
        ExtUtil.writeString(out, password);
        ExtUtil.writeNumeric(out, recordId);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(uniqueId));
        ExtUtil.writeBool(out, rememberMe);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(syncToken));
        ExtUtil.write(out, new ExtWrapMap(properties));
    }

    public boolean isAdminUser() {
        return ADMINUSER.equals(getUserType());
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setID(int recordId) {

        this.recordId = recordId;
    }

    public int getID() {
        return recordId;
    }

    String getUserType() {
        if (properties.containsKey(KEY_USER_TYPE)) {
            return properties.get(KEY_USER_TYPE);
        } else {
            return null;
        }
    }

    void setUserType(String userType) {
        properties.put(KEY_USER_TYPE, userType);
    }

    void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    void setUuid(String uuid) {
        this.uniqueId = uuid;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public Enumeration listProperties() {
        return this.properties.keys();
    }

    public void setProperty(String key, String val) {
        this.properties.put(key, val);
    }

    public String getProperty(String key) {
        return this.properties.get(key);
    }

    public Hashtable<String, String> getProperties() {
        return this.properties;
    }

    public String getRestorableType() {
        return "user";
    }

    public FormInstance exportData() {
        FormInstance dm = RestoreUtils.createDataModel(this);
        RestoreUtils.addData(dm, "name", username);
        RestoreUtils.addData(dm, "pass", password);
        RestoreUtils.addData(dm, "uuid", uniqueId);
        RestoreUtils.addData(dm, "remember", Boolean.valueOf(rememberMe));

        for (Enumeration e = properties.keys(); e.hasMoreElements(); ) {
            String key = (String)e.nextElement();
            RestoreUtils.addData(dm, "other/" + key, properties.get(key));
        }

        return dm;
    }

    public void templateData(FormInstance dm, TreeReference parentRef) {
        RestoreUtils.applyDataType(dm, "name", parentRef, String.class);
        RestoreUtils.applyDataType(dm, "pass", parentRef, String.class);
        RestoreUtils.applyDataType(dm, "type", parentRef, String.class);
        RestoreUtils.applyDataType(dm, "user-id", parentRef, Integer.class);
        RestoreUtils.applyDataType(dm, "uuid", parentRef, String.class);
        RestoreUtils.applyDataType(dm, "remember", parentRef, Boolean.class);
    }

    public void importData(FormInstance dm) {
        username = (String)RestoreUtils.getValue("name", dm);
        password = (String)RestoreUtils.getValue("pass", dm);
        uniqueId = (String)RestoreUtils.getValue("uuid", dm);
        rememberMe = RestoreUtils.getBoolean(RestoreUtils.getValue("remember", dm));

        TreeElement e = dm.resolveReference(RestoreUtils.absRef("other", dm));
        if (e != null) {
            for (int i = 0; i < e.getNumChildren(); i++) {
                TreeElement child = e.getChildAt(i);
                String name = child.getName();
                Object value = RestoreUtils.getValue("other/" + name, dm);
                if (value != null) {
                    properties.put(name, (String)value);
                }
            }
        }
    }

    public Hashtable getMetaData() {
        Hashtable ret = new Hashtable();
        for (String name : getMetaDataFields()) {
            ret.put(name, getMetaData(name));
        }
        return ret;
    }

    public Object getMetaData(String fieldName) {
        if (META_UID.equals(fieldName)) {
            return uniqueId;
        } else if(META_USERNAME.equals(fieldName)) {
            return username;
        } else if(META_ID.equals(fieldName)) {
            return Integer.valueOf(recordId);
        }
        throw new IllegalArgumentException("No metadata field " + fieldName + " for User Models");
    }

    public String[] getMetaDataFields() {
        return new String[]{META_UID, META_USERNAME, META_ID};
    }

    public String getLastSyncToken() {
        return syncToken;
    }

    public void setLastSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }

    public static User FactoryDemoUser() {
        if (demo_user == null) {
            demo_user = new User();
            demo_user.setUsername(User.DEMO_USER); // NOTE: Using a user type as a
            // username also!
            demo_user.setUserType(User.DEMO_USER);
            demo_user.setUuid(User.DEMO_USER);
        }
        return demo_user;
    }
}
