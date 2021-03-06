package org.commcare.test.utilities;

import org.commcare.util.mocks.LivePrototypeFactory;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A persistable sandbox provides an environment to handle 
 * prototypes for object serialization and deserialization.
 * 
 * @author ctsims
 */
public class PersistableSandbox {
    private LivePrototypeFactory factory;
    
    public PersistableSandbox() {
        LivePrototypeFactory factory = new LivePrototypeFactory();
        PrototypeFactory.setStaticHasher(factory);
    }
    
    public <T extends Externalizable> byte[] serialize(T t) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.writeExternal(new DataOutputStream(baos));
            return baos.toByteArray();
        } catch(IOException e) {
            throw wrap("Error serializing: " + t.getClass().getName(), e);
        }
    }
    
    public <T extends Externalizable> T deserialize(byte[] object, Class<T> c) {
        try {
            return (T)ExtUtil.deserialize(object, c, factory);
        } catch (IOException | DeserializationException e) {
            throw wrap("Error deserializing: " + c.getName(), e);
        }
    }
    
    public static RuntimeException wrap(String message, Exception e){
        e.printStackTrace();
        RuntimeException runtimed = new RuntimeException(message);
        runtimed.initCause(e);
        return runtimed;
    }
}
