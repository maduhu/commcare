package org.commcare.util.cli;

import org.commcare.data.xml.DataModelPullParser;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareConfigEngine;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareTransactionParserFactory;
import org.commcare.session.SessionFrame;
import org.commcare.util.mocks.MockDataUtils;
import org.commcare.util.mocks.MockUserDataSandbox;
import org.commcare.util.mocks.SessionWrapper;
import org.commcare.util.mocks.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.engine.XFormPlayer;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * CLI host for running a commcare application which has been configured and instatiated
 * for the provided user.
 *
 * @author ctsims
 */
public class ApplicationHost {
    private final CommCareConfigEngine mEngine;
    private final CommCarePlatform mPlatform;
    private MockUserDataSandbox mSandbox;
    private SessionWrapper mSession;

    private boolean mUpdatePending = false;
    private boolean mForceLatestUpdate = false;
    private boolean mSessionHasNextFrameReady = false;

    private final PrototypeFactory mPrototypeFactory;

    private final BufferedReader reader;

    private String[] mLocalUserCredentials;
    private String mRestoreFile;
    private boolean mRestoreStrategySet = false;

    public ApplicationHost(CommCareConfigEngine engine, PrototypeFactory prototypeFactory) {
        this.mEngine = engine;

        this.mPlatform = engine.getPlatform();

        reader = new BufferedReader(new InputStreamReader(System.in));
        this.mPrototypeFactory = prototypeFactory;
    }
    public void setRestoreToRemoteUser(String username, String password) {
        this.mLocalUserCredentials = new String[]{username, password};
        mRestoreStrategySet = true;
    }
    public void setRestoreToLocalFile(String filename) {
        this.mRestoreFile = filename;
        mRestoreStrategySet = true;
    }


    public void run() {
        if(!mRestoreStrategySet) {
            throw new RuntimeException("You must set up an application host by calling " +
                    "one of hte setRestore*() methods before running the app");
        }
        setupSandbox();

        mSession = new SessionWrapper(mPlatform, mSandbox);

        try {
            loop();
        }catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private void loop() throws IOException {
        boolean keepExecuting = true;
        while (keepExecuting) {
            if(!mSessionHasNextFrameReady) {
                mSession.clearAllState();
            }
            mSessionHasNextFrameReady = false;
            keepExecuting = loopSession();

            if(this.mUpdatePending) {
               processAppUpdate();
            }
        }
    }

    private void processAppUpdate() {
        mSession.clearAllState();
        this.mUpdatePending = false;
        boolean forceUpdate = mForceLatestUpdate;
        this.mForceLatestUpdate = false;
        mEngine.attemptAppUpdate(forceUpdate);
    }

    private boolean loopSession() throws IOException {
        Screen s = getNextScreen();
        boolean screenIsRedrawing = false;

        while (s != null) {
            try {
                if(!screenIsRedrawing) {
                    s.init(mPlatform, mSession, mSandbox);
                }

                System.out.println("\n\n\n\n\n\n");
                System.out.println(s.getWrappedDisplaytitle(mSandbox, mPlatform));

                System.out.println("====================");
                s.prompt(System.out);
                System.out.print("> ");

                screenIsRedrawing = false;
                String input = reader.readLine();

                //TODO: Command language
                if(input.startsWith(":")) {
                    if(input.equals(":exit") || input.equals(":quit")) {
                        return false;
                    }
                    if (input.startsWith(":update")) {
                        mUpdatePending = true;

                        if(input.contains("-f")) {
                            mForceLatestUpdate = true;
                        }
                        return true;
                    }

                    if(input.equals(":home")) {
                        return true;
                    }
                }

                screenIsRedrawing = s.handleInputAndUpdateSession(mSession, input);
                if(!screenIsRedrawing) {
                    s = getNextScreen();
                }
            } catch (CommCareSessionException ccse) {
                printErrorAndContinue("Error during session execution:", ccse);

                //Restart
                return true;
            }
        }
        //We have a session and are ready to fill out a form!

        //Get our form object
        String formXmlns = mSession.getForm();

        if(formXmlns == null) {
            finishSession();
        } else {

            XFormPlayer player = new XFormPlayer(System.in, System.out, null);
            player.setSessionIIF(mSession.getIIF());
            player.start(mEngine.loadFormByXmlns(formXmlns));

            //If the form saved properly, process the output
            if (player.getExecutionResult() == XFormPlayer.FormResult.Completed) {
                if (!processResultInstance(player.getResultStream())) {
                    return true;
                }
                finishSession();
            }
        }

        //After we finish, continue executing
        return true;
    }

    private void finishSession() {
        mSession.clearVolitiles();
        if(mSession.finishExecuteAndPop(mSession.getEvaluationContext())) {
            mSessionHasNextFrameReady = true;
        }
    }

    private boolean processResultInstance(InputStream resultStream) {
        try {
            DataModelPullParser parser = new DataModelPullParser(
                    resultStream, new CommCareTransactionParserFactory(mSandbox), true, true);
            parser.parse();
        } catch (Exception e) {
            printErrorAndContinue("Error processing the form result!", e);
            return false;
        } finally {
            try {
                resultStream.close();
            } catch(IOException e) {}
        }
        return true;
    }

    private void printErrorAndContinue(String error, Exception e) {
        System.out.println(error);
        e.printStackTrace();
        System.out.println("Press return to restart the session");
        try {
            reader.readLine();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Screen getNextScreen() {
        String next = mSession.getNeededData();

        if (next == null) {
            //XFORM TIME!
            return null;
        } else if (next.equals(SessionFrame.STATE_COMMAND_ID)) {
            return new MenuScreen();
        } else if (next.equals(SessionFrame.STATE_DATUM_VAL)) {
            return new EntityScreen();
        } else if (next.equalsIgnoreCase(SessionFrame.STATE_DATUM_COMPUTED)) {
            computeDatum();
            return getNextScreen();
        }
        throw new RuntimeException("Unexpected Frame Request: " + mSession.getNeededData());
    }

    private void computeDatum() {
        //compute
        SessionDatum datum = mSession.getNeededDatum();
        XPathExpression form;
        try {
            form = XPathParseTool.parseXPath(datum.getValue());
        } catch (XPathSyntaxException e) {
            //TODO: What.
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        EvaluationContext ec = mSession.getEvaluationContext();
        if (datum.getType() == SessionDatum.DATUM_TYPE_FORM) {
            mSession.setXmlns(XPathFuncExpr.toString(form.eval(ec)));
            mSession.setDatum("", "awful");
        } else {
            try {
                mSession.setDatum(datum.getDataId(), XPathFuncExpr.toString(form.eval(ec)));
            } catch (XPathException e) {
                error(e);
            }
        }
    }

    private void error(Exception e) {
        e.printStackTrace();
        System.exit(-1);
    }

    private void setupSandbox() {
        mSandbox = new MockUserDataSandbox(mPrototypeFactory);
        if(mLocalUserCredentials != null) {
            restoreUserToSandbox(mSandbox, mLocalUserCredentials);
        } else {
            restoreFileToSandbox(mSandbox, mRestoreFile);
        }
    }

    private void restoreFileToSandbox(MockUserDataSandbox sandbox, String restoreFile) {
        FileInputStream fios = null;
        try {
            System.out.println("Restoring user data from local file " + restoreFile);
            fios = new FileInputStream(restoreFile);
        } catch (FileNotFoundException e) {
            System.out.println("No restore file found at" + restoreFile);
        }
        try {
            MockDataUtils.parseIntoSandbox(new BufferedInputStream(fios), sandbox, false);
        } catch (Exception e) {
            System.out.println("Error parsing local restore data from " + restoreFile);
            e.printStackTrace();
            System.exit(-1);
        }

        //Initialize our User
        for (IStorageIterator<User> iterator = mSandbox.getUserStorage().iterate(); iterator.hasMore(); ) {
            User u = iterator.nextRecord();
            mSandbox.setLoggedInUser(u);
            System.out.println("Setting logged in user to: " + u.getUsername());
            break;
        }
    }

    private void restoreUserToSandbox(MockUserDataSandbox mSandbox, String[] userCredentials) {
        final String username = userCredentials[0];
        final String password = userCredentials[1];

        //fetch the restore data and set credentials
        String otaRestoreURL = PropertyManager._().getSingularProperty("ota-restore-url") + "?version=2.0";
        String domain = PropertyManager._().getSingularProperty("cc_user_domain");
        final String qualifiedUsername = username + "@" + domain;

        Authenticator.setDefault(new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(qualifiedUsername, password.toCharArray());
            }
        });

        //Go get our sandbox!
        try {
            URL url = new URL(otaRestoreURL);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();

            System.out.println("Restoring user " + username + " to domain " + domain);

            MockDataUtils.parseIntoSandbox(new BufferedInputStream(conn.getInputStream()), mSandbox, false);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        //Initialize our User
        for (IStorageIterator<User> iterator = mSandbox.getUserStorage().iterate(); iterator.hasMore(); ) {
            User u = iterator.nextRecord();
            if (username.equalsIgnoreCase(u.getUsername())) {
                mSandbox.setLoggedInUser(u);
            }
        }
    }
}
