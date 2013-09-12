/**
 * SICX OSS Gateway, Multi-Cloud Storage software. 
 * Copyright (C) 2012 Helsinki Institute of Physics, University of Helsinki
 * All rights reserved. See the copyright.txt in the distribution for a full 
 * listing of individual contributors.
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * 
 */
package fi.hip.sicxoss;

import java.io.*;
import java.util.*;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.apache.log4j.Logger;

import com.bradmcevoy.http.*;
import fi.hip.sicxoss.servlet.*;
import fi.hip.sicxoss.milton.*;
import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.lookup.*;
import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.util.*;
import fi.hip.sicxoss.ui.GatewayUI;
import fi.hip.sicxoss.ui.GatewayWizard;

import fi.hip.sicx.slcs.SLCSClient;

/**
 * LocalGateway
 *
 * The main class of this application. This initiates the webdav,
 * lookupserver and all the shares.
 * @author koskela
 */
public class LocalGateway {

    private static final Logger log = Logger.getLogger(LocalGateway.class);

    private static int DEFAULT_WEBDAV_PORT = 7654;
    private static int DEFAULT_LOOKUP_PORT = 7766;
    private static String DEFAULT_LOOKUP_SERVER = "kreml.jookos.org:7766";

    public interface LocalGatewayObserver {
        
        public void userAdded(LocalUser u);
        public void shareAdded(LocalUser u, ShareModel sm);
        public void shareRemoved(ShareModel sm);
    }

    private Server jetty;
    private HttpManager httpManager;
    private SICXOSSResourceFactory resfac;
    private File confFile;
    private HierarchicalProperties props;
    private Map<String, DataStore> dataStores;
    private Map<String, LocalUser> users; // err.. only one user per app
    private Map<String[], ShareModel> models;
    private LookupServer lookup;
    private NetworkEngine networkEngine;
    private List<Thread> threads;
    private List<LocalGatewayObserver> observers;
    private GatewayUI ui;
    public static boolean startUI = true;

    public LocalGateway(String filename) 
        throws Exception {
        
        // init the storage
        resfac = new SICXOSSResourceFactory(this);

        models = new Hashtable();
        dataStores = new Hashtable();
        users = new Hashtable();
        props = new HierarchicalProperties();
        confFile = new File(filename);
        networkEngine = new NetworkEngine();
        threads = new ArrayList();
        observers = new ArrayList();

        initDefaultConfig();
    }

    public void addObserver(LocalGatewayObserver lgo) {
        observers.add(lgo);
    }

    public List<LocalUser> getLocalUsers() {
        List<LocalUser> ret = new ArrayList();
        for (LocalUser u : users.values())
            ret.add(u);
        return ret;
    }

    public HttpManager getWebDAVManager() {
        return httpManager;
    }

    public void printList(PrintStream out) 
        throws IOException {
        
        out.println("Data stores:");
        for (String name : dataStores.keySet()) {
            DataStore ds = dataStores.get(name);
            out.println("    " + name + ": " + ds.toString());
        }
        out.println("Shares:");
        for (String[] name : models.keySet()) {
            ShareModel ds = models.get(name);
            out.println("    " + name[0] + " mounted at " + name[1] + ": " + ds.toString());
        }
        out.println("Users:");
        for (String name : users.keySet()) {
            LocalUser ds = users.get(name);
            out.println("    " + name + ": " + ds.toString());
            out.println("    Contacts:");
            for (User c : ds.contactManager().getContacts()) {
                out.println("        " + c.toString());
            }
        }
    }

    private DataStore createDiskDataStore(String name) 
        throws Exception {

        log.info("Creating the '" + name + "' data cache..");

        Properties p = props.getChildren("store").getChildren(name);
        p.setProperty("type", "disk");
        p.setProperty("location", name);
        String loc = getConfig("stores.prefix") + File.separator + name;

        DiskDataStore store = new DiskDataStore(name);
        store.init(loc, p, this);

        dataStores.put(name, store);
        store.start();
        return store;
    }

    private DataStore loadDataStore(String name, Properties p) 
        throws Exception {

        log.info("Loading the '" + name + "' data store. type " + p.getProperty("type"));
        
        DataStore store = null;
        String t = p.getProperty("type");
        if (t == null || t.equals("disk")) {
            store = new DiskDataStore(name);
        } else {
            log.error("unknown data store type: " + t);
            return null;
        }

        String loc = p.getProperty("location");
        if (!loc.startsWith("/"))
            loc = getConfig("stores.prefix") + File.separator + loc;
        
        store.load(loc, p, this);
        dataStores.put(name, store);
        return store;
    }
    
    public void deleteShare(ShareModel share, DataStore store)
        throws Exception {

        // if the store it is using isn't used by anyone else, delete
        // it also.
        String[] dk = null;
        for (String[] k : models.keySet()) {
            ShareModel sm = models.get(k);
            if (sm == share) {
                dk = k;
            } else if (sm.getDataStore() == store) {
                store = null;
            }
        }
        
        String stk = null;
        for (String k : dataStores.keySet()) {
            DataStore ds = dataStores.get(k);
            if (ds == store) {
                stk = k;
            }
        }

        Properties p = null;
        if (dk != null) {
            p = props.getChildren("share").getChildren(share.getName());
            p.clear();
            models.remove(dk);
            share.deleteShare();
        }
        
        if (store != null) {
            p = props.getChildren("store").getChildren(store.getName());
            p.clear();
            dataStores.remove(stk);
            store.deleteStore();
        }
        
        resfac.removeModel(share);
        saveConfig();
    }

    public ShareModel createShare(LocalUser user, ShareID shareId, String name, String path, DataStore store)
        throws Exception {

        if (store == null)
            store = createDiskDataStore("store_for_" + name);

        log.info("Creating the '" + name + "' remote share, mounted at " + path + ", using the data cache '" + store.getName() + "'");
        String loc = getConfig("shares.prefix") + File.separator + name;
        ShareModel model = ShareModel.create(shareId, name, store, user, loc, this);
        setConfig("share." + name + ".location", name);
        setConfig("share." + name + ".path", path);
        setConfig("share." + name + ".store", store.getName());
        setConfig("share." + name + ".user", user.getName());
        saveConfig();

        models.put(new String[] { name, path }, model);
        resfac.addModel(model, path);

        for (LocalGatewayObserver lgo : observers) 
            try { lgo.shareAdded(user, model); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return model;
    }

        

    public ShareModel createShare(String name, String path)
        throws Exception {

        LocalUser user = (LocalUser)users.values().toArray()[0];
        DataStore store = createDiskDataStore("store_for_" + name);

        log.info("Creating the '" + name + "' remote share, mounted at " + path + ", using the data cache '" + store.getName() + "'");
        String loc = getConfig("shares.prefix") + File.separator + name;
        ShareModel model = ShareModel.createNew(name, store, user, loc, this);
        setConfig("share." + name + ".location", name);
        setConfig("share." + name + ".path", path);
        setConfig("share." + name + ".store", store.getName());
        setConfig("share." + name + ".user", user.getName());
        saveConfig();

        models.put(new String[] { name, path }, model);
        resfac.addModel(model, path);
        for (LocalGatewayObserver lgo : observers) 
            try { lgo.shareAdded(user, model); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return model;
    }

    private ShareModel createShare(String name, String path, String storeName, String userName)
        throws Exception {

        LocalUser user = users.get(userName);
        if (user == null)
            throw new Exception("Invalid user: " + userName);
        DataStore store = dataStores.get(storeName);
        if (store == null)
            throw new Exception("Invalid data store: " + storeName);

        log.info("Creating the '" + name + "' remote share, mounted at " + path + ", using the data cache '" + storeName + "'");
        String loc = getConfig("shares.prefix") + File.separator + name;
        ShareModel model = ShareModel.createNew(name, store, user, loc, this);
        setConfig("share." + name + ".location", name);
        setConfig("share." + name + ".path", path);
        setConfig("share." + name + ".store", store.getName());
        setConfig("share." + name + ".user", user.getName());
        saveConfig();

        models.put(new String[] { name, path }, model);
        resfac.addModel(model, path);
        for (LocalGatewayObserver lgo : observers) 
            try { lgo.shareAdded(user, model); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return model;
    }

    private ShareModel loadShare(String name, String location, String path, String storeName, String userName)
        throws Exception {

        LocalUser user = users.get(userName);
        if (user == null)
            throw new Exception("Invalid user: " + userName);
        DataStore store = dataStores.get(storeName);
        if (store == null)
            throw new Exception("Invalid data store: " + storeName);

        log.info("Loading the '" + name + "' remote share, mounted at " + path + ", using the data cache '" + storeName + "'");
        if (!location.startsWith("/"))
            location = getConfig("shares.prefix") + File.separator + location;

        ShareModel model = ShareModel.load(name, store, user, location, this);
        models.put(new String[] { name, path }, model);

        for (LocalGatewayObserver lgo : observers) 
            try { lgo.shareAdded(user, model); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return model;
    }

    private LocalUser createLocalUser(String name, String fullName) 
        throws Exception {

        String loc = getConfig("users.prefix") + File.separator + name;
        LocalUser local = LocalUser.generate(name, fullName, loc, this);

        setConfig("user." + name + ".location", name);
        users.put(name, local);
        for (LocalGatewayObserver lgo : observers) 
            try { lgo.userAdded(local); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return local;
    }

    public LocalUser checkLocalUserSLCS(String slcsLogin, String slcsPasswd, String name) 
        throws Exception {

        log.info("checking SLCS configuration..");
                
        // copy from the templates new configuration
        // ..unless they exist already!
        
        String loc = getConfig("users.prefix") + File.separator + name;
        LocalUser local = LocalUser.createUsingSLCS(slcsLogin, slcsPasswd, name, loc, this);
        return local;
    }

    public void importLocalUser(LocalUser local) 
        throws Exception {
            
        local.saveData();
        setConfig("user." + local.getName() + ".location", local.getName());
        users.put(local.getName(), local);
        saveConfig();
        for (LocalGatewayObserver lgo : observers) 
            try { lgo.userAdded(local); } catch (Exception ex) { log.warn("observer failed: " + ex); }
    }

    private LocalUser loadLocalUser(String name, Properties p) {

        String location = p.getProperty("location");
        if (!location.startsWith("/"))
            location = getConfig("users.prefix") + File.separator + location;
        LocalUser local = LocalUser.load(name, location, this);

        // check whether we need to ask for password
        if (local.requiresSLCS()) {
            
            boolean done = false;
            if (local.hasPassword())
                try {
                    done = local.fetchSLCS(null, false);
                } catch (Exception ex) {
                    log.error("error fetching slcs credentials: " + ex);
                }
            while (!done) {
                log.info("we should ask for a password for " + local.getAccount());
                
                String password = "";
                initUI();

                if (ui != null)
                    password = ui.queryPassword("Password", "Please enter the password for your SICX account '" + local.getAccount() + "'");
                try {
                    done = local.fetchSLCS(password, false);
                } catch (Exception ex) {
                    log.error("error fetching slcs credentials: " + ex);
                }
            }
        }

        users.put(name, local);
        for (LocalGatewayObserver lgo : observers) 
            try { lgo.userAdded(local); } catch (Exception ex) { log.warn("observer failed: " + ex); }
        return local;
    }

    private void saveConfig() 
        throws Exception {

        FileOutputStream fos = new FileOutputStream(confFile);
        props.store(fos, "SICX FOSS");
        fos.close();
    }

    private void setConfig(String key, String value) {
        props.setProperty(key, value);
    }

    private void setConfig(String key, int value) {
        props.setProperty(key, "" + value);
    }

    public String getConfig(String key) {
        return props.getProperty(key);
    }

    public int getConfigInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }

    private boolean initDefaultConfig()
        throws Exception {

        if (confFile.exists()) {
            log.error("configuration file " + confFile + " already exists!");
            return false;
        }

        log.info("Creating default configuration in " + confFile + "..");
        
        confFile.getParentFile().mkdirs();
        
        // we create everything in the config file's parent folder!
        String prefix = confFile.getParentFile().getAbsolutePath() + File.separator;
        
        setConfig("shares.prefix", prefix + "shares");
        setConfig("stores.prefix", prefix + "stores");
        setConfig("users.prefix", prefix + "users");

        setConfig("webdav.port", DEFAULT_WEBDAV_PORT);
        setConfig("lookup.port", DEFAULT_LOOKUP_PORT);
        setConfig("lookup.server", DEFAULT_LOOKUP_SERVER);

        return true;
    }

    public boolean createDefaultConfig(String name, String fullName, String sharen)
        throws Exception {
        
        /* generate a local user */
        LocalUser local = createLocalUser(name, fullName);
        
        /* generate the default share */
        DataStore store = createDiskDataStore("default");
        ShareModel share = createShare(sharen, "/" + sharen, store.getName(), local.getName());

        saveConfig();
        return true;
    }

    public boolean createDefaultConfig(String name, String fullName,
                                       String sharen, int port, String lookup)
        throws Exception {
        
        setConfig("webdav.port", port);
        setConfig("lookup.server", lookup);

        /* generate a local user */
        LocalUser local = createLocalUser(name, fullName);
        
        /* generate the default share */
        DataStore store = createDiskDataStore("default");
        ShareModel share = createShare(sharen, "/" + sharen, store.getName(), local.getName());

        saveConfig();
        return true;
    }

    public boolean createDefaultConfig(LocalUser local, String sharen, int port, String lookup)
        throws Exception {
        
        setConfig("webdav.port", port);
        setConfig("lookup.server", lookup);
        
        importLocalUser(local);

        /* generate the default share */
        DataStore store = createDiskDataStore("default");
        ShareModel share = createShare(sharen, "/" + sharen, store.getName(), local.getName());

        saveConfig();
        return true;
    }

    public boolean configure() 
        throws Exception {

        if (!confFile.exists()) {
            log.warn("configuration file " + confFile + " is missing");
            return false;
        }
         
        props.load(new FileInputStream(confFile));
        
        // the users
        HierarchicalProperties cp = props.getChildren("user");
        for (Enumeration e = cp.childrenNames(); e.hasMoreElements();) {
            String k = (String)e.nextElement();
            loadLocalUser(k, cp.getChildren(k));
        }

        // the stores
        cp = props.getChildren("store");
        for (Enumeration e = cp.childrenNames(); e.hasMoreElements();) {
            String k = (String)e.nextElement();
            loadDataStore(k, cp.getChildren(k));
        }

        // the shares
        for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
            String k = (String)e.nextElement();
            if (k.startsWith("share.") && k.endsWith(".store")) {
                String shareName = k.substring(k.indexOf('.')+1, k.lastIndexOf('.'));
                String sharePath = getConfig("share." + shareName + ".path");
                String shareStore = getConfig("share." + shareName + ".store");
                String shareLocation = getConfig("share." + shareName + ".location");
                String shareUser = getConfig("share." + shareName + ".user");
                
                try {
                    loadShare(shareName, shareLocation, sharePath, shareStore, shareUser);
                } catch (Exception ex) {
                    log.warn("error loading share: " + ex);
                }
            }
        }
        
        return true;
    }

    private void closeUI() {
        if (ui != null)
            ui.close();
    }
     
    private void initUI() {
        
        if (ui != null || !startUI)
            return;
        try {
            ui = (GatewayUI)getClass().forName("fi.hip.sicxoss.ui.TrayIconUI").newInstance();
            ui.init(this);
            
            addObserver(ui);
            for (ShareModel s : models.values())
                s.addObserver(ui);
            for (LocalUser u : users.values())
                u.addObserver(ui);

        } catch (Throwable ex) {
            log.error("error initializing the UI: " + ex);
            //ex.printStackTrace();
        }
    }

    // needed for the menus..
    public Map<String[], ShareModel> getShareModels() {
        return models;
    }

    private void init(boolean interconnect)
        throws Exception {

        // add admin folder?
        resfac.addModel(new AdminFolderModel(this), "/Admin");

        if (interconnect) {
            log.info("interconnecting everything");
            /* mirror everything */
            for (ShareModel s : models.values()) {
                for (ShareModel s2 : models.values()) {
                    if (s != s2)
                        s.addMirror(s2);
                }
            }
        }
        
        // init milton
        AuthenticationService authenticationService = new AuthenticationService();
        authenticationService.setDisableDigest(false);
        httpManager = new HttpManager(resfac, authenticationService);

        // init the servlet container
        // http://docs.codehaus.org/display/JETTY/Embedding+Jetty
        log.info("Initing webdav interface on post " + getConfigInt("webdav.port"));
        jetty = new Server(getConfigInt("webdav.port"));
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jetty.setHandler(context);
        context.addServlet(new ServletHolder(new GatewayServlet(this)), "/*");
    }

    public void start()
        throws Exception {

        for (DataStore ds : dataStores.values())
            ds.start();
        
        for (String[] md : models.keySet()) {
            resfac.addModel(models.get(md), md[1]);
            models.get(md).start();
        }

        jetty.start();
    }

    public void stop() 
        throws Exception {

        if (jetty != null)
            jetty.stop();

        for (String[] md : models.keySet()) {
            resfac.addModel(models.get(md), md[1]);
            models.get(md).stop();
        }

        for (DataStore ds : dataStores.values())
            ds.stop();

        stopLookup();
        stopNetwork();
    }

    private void initLookup()
        throws Exception {
                
        lookup = new LookupServer(networkEngine);
        lookup.init(getConfigInt("lookup.port"));
    }

    public void startLookup()
        throws Exception {
        
        threads.add(lookup);
        if (!lookup.isAlive())
            lookup.start();
    }

    public void stopLookup()
        throws Exception {
        
        if (lookup != null)
            lookup.stop();
    }

    public void initNetwork() 
        throws Exception {

        networkEngine.setDaemon(true);
        //threads.add(networkEngine);
        networkEngine.init();
    }

    public void startNetwork() 
        throws Exception {

        networkEngine.start();
    }

    public void stopNetwork() 
        throws Exception {

        networkEngine.stop();
    }

    public NetworkEngine getNetworkEngine() {
        return networkEngine;
    }

    public void waitForThreads() 
        throws Exception {

        if (jetty != null)
            jetty.join();
        for (Thread t : threads)
            t.join();
    }

    /* Starts the wizard */
    private static boolean runWizard(LocalGateway lg) {

        /* note:

           this should probably be part of the UI. So we create the ui
           pretty much with every run, and call something like
           'getConfigurationWizard().run()'
        */
        try {
            GatewayWizard wiz = (GatewayWizard)lg.getClass().forName("fi.hip.sicxoss.ui.GatewayWizardImpl").newInstance();
            wiz.init(lg);
            return wiz.runWizard();
        } catch (Throwable ex) {
            log.error("error running the wizard: " + ex);
        }
        return false;
    }

    /**
     * simple command-line parsing
     */
    private static String[] getParams(String[] args, int from, int len, String cmd) 
        throws Exception {
        
        if (args.length - from <= len)
            throw new Exception("Argument " + args[from] + " requires " + len + " parameter(s)!");
        
        String[] ret = new String[len+1];
        for (int i=0; i < len; i++) {
            ret[i] = args[from+i+1];
        }
        ret[ret.length-1] = cmd;
        return ret;
    }
    
    /**
     * The point of entry. Sets up an instance of LocalGateway,
     * starting the lookup server and gateway, as needed.
     */
    public static void main(String[] args) 
        throws Exception {

        String storeFileName = null;
        boolean interconnect = false;
        boolean list = false;
        boolean start = true;
        boolean init = false;
        boolean startLookup = false;
        boolean verbose = false;
        int lookupPort = -1, webdavPort = -1;
        String lookupServer = null;

        /* parse the command line */
        List<String[]> actions = new ArrayList();
        try {
            for (int i=0; i < args.length; i++) {
                if (args[i].equals("-i")) {
                    log.warn("Interconnecting all the local stores");
                    interconnect = true;
                } else if (args[i].equals("-ds")) {
                    // create new store: name
                    actions.add(getParams(args, i, 1, "new_store"));
                    i += 1;
                    start = false;
                } else if (args[i].equals("-s")) {
                    // create new share: name, path, store, user
                    actions.add(getParams(args, i, 4, "new_share"));
                    i += 4;
                    start = false;
                } else if (args[i].equals("-ex")) {
                    actions.add(getParams(args, i, 2, "export_user"));
                    i += 2;
                    start = false;
                } else if (args[i].equals("-im")) {
                    actions.add(getParams(args, i, 2, "import_user"));
                    i += 2;
                    start = false;
                } else if (args[i].equals("-log")) {
                    actions.add(getParams(args, i, 1, "share_log"));
                    i += 1;
                    start = false;
                } else if (args[i].equals("-v")) {
                    verbose = true;
                } else if (args[i].equals("-l")) {
                    list = true;
                    start = false;
                } else if (args[i].equals("-nx")) {
                    LocalGateway.startUI = false;
                } else if (args[i].equals("-init")) {
                    init = true;
                } else if (args[i].equals("-L")) {
                    startLookup = true;
                } else if (args[i].equals("-Lp")) {
                    lookupPort = Integer.parseInt(getParams(args, i, 1, "")[0]);
                    i++;
                } else if (args[i].equals("-Ls")) {
                    lookupServer = getParams(args, i, 1, "")[0];
                    i++;
                } else if (args[i].equals("-Wp")) {
                    webdavPort = Integer.parseInt(getParams(args, i, 1, "")[0]);
                    i++;
                } else if (args[i].equals("-nW")) {
                    start = false;
                } else if (args[i].equals("-slcs")) {
                    actions.add(getParams(args, i, 3, "slcs"));
                    i += 3;
                    start = false;
                } else if (args[i].equals("-cert")) {
                    actions.add(getParams(args, i, 2, "cert"));
                    i += 2;
                    start = false;
                } else if (args[i].equals("-h")) {
                    throw new Exception("Help requested!");
                } else if (args[i].startsWith("-")) {
                    throw new Exception("Invalid switch: " + args[i]);
                } else if (storeFileName == null) {
                    storeFileName = args[i];
                } else {
                    throw new Exception("Invalid parameter: " + args[i]);
                }
            }
        } catch (Exception ex) {
            System.err.println("Error processing command line arguments: " + ex.getMessage());
            System.err.println();
            System.err.println("Use:");
            System.err.println("sicxoss [config file] [-ds <name>] [-s <name> <path> <store> <user>] [-l] [-i]");
            System.err.println("\t-init\tcreates a default set-up with one store, one user and one share");
            System.err.println("\t-ds\tcreate a new data store");
            System.err.println("\t-s\tcreate a new shared folder");
            System.err.println("\t-l\tlist the current data stores and shares");
            System.err.println("\t-i\tinterconnect all shared folders (for testing syncs)");
            System.err.println("\t-nx\tdisable UI");
            System.err.println("More options:");
            System.err.println("\t-ex <local user> <file>\texport the given user's public info");
            System.err.println("\t-im <file> <local user>\timport a user from the given file");
            System.err.println("Logs:");
            System.err.println("\t-log <store>\tshow the event log for the store, as it would be applied");
            System.err.println("\t-v\tshow the full events");
            System.err.println("Server control:");
            System.err.println("\t-L\truns the lookup server");
            System.err.println("\t-nW\tdisables the webdav interface");
            System.err.println("\t-Wp <port>\tsets the webdav port");
            System.err.println("\t-Lp <port>\tsets the lookup port");
            System.err.println("\t-Ls <host:port>\tsets the lookup server address");
            System.err.println("Login options:");
            System.err.println("\t-slcs username password localuser\tcreates a new user using the SLCS credentials");
            System.err.println("\t-cert certificate_file localuser\timports a trusted certificate to the local user");
            System.exit(1);
        }

        if (storeFileName == null)
            storeFileName = System.getProperty("user.home") + File.separator + ".sicxoss" + File.separator + "sicxoss.conf";

        if (webdavPort > 0) DEFAULT_WEBDAV_PORT = webdavPort;
        if (lookupPort > 0) DEFAULT_LOOKUP_PORT = lookupPort;
        if (lookupServer != null) DEFAULT_LOOKUP_SERVER = lookupServer;
        
        LocalGateway lg = new LocalGateway(storeFileName);
        if (init) {
            if (!lg.createDefaultConfig("default", "Larry the local", "share"))
                start = false;
        } else {
            // we could create a default if this fails ..
            if (!lg.configure() && start && !runWizard(lg)) {
                log.error("and the wizard could not to handle it, quitting.");
                start = false;
            }
        }

        // the overrides
        if (webdavPort > 0) lg.setConfig("webdav.port", webdavPort);
        if (lookupPort > 0) lg.setConfig("lookup.port", lookupPort);
        if (lookupServer != null) lg.setConfig("lookup.server", lookupServer);

        for (String[] params : actions) {
            if (params[params.length-1].equals("new_share")) {
                lg.createShare(params[0], params[1], params[2], params[3]);
            } else if (params[params.length-1].equals("new_store")) {
                lg.createDiskDataStore(params[0]);
            } else if (params[params.length-1].equals("export_user")) {
                LocalUser user = lg.users.get(params[0]);
                if (user == null)
                    System.err.println("Invalid user: " + params[0]);
                else {
                    FileOutputStream fos = new FileOutputStream(params[1]);
                    fos.write(user.getData().getBytes());
                    fos.close();
                    System.out.println("Data written to " + params[1]);
                    System.out.println(user.getData());
                }
            } else if (params[params.length-1].equals("import_user")) {
                LocalUser user = lg.users.get(params[1]);
                if (user == null)
                    System.err.println("Invalid local user: " + params[1]);
                else {
                    File f = new File(params[0]);
                    FileInputStream fis = new FileInputStream(f);
                    DataInputStream dis = new DataInputStream(fis);
                    byte[] buf = new byte[(int)f.length()];
                    dis.readFully(buf);
                    dis.close();
                    fis.close();

                    User u = User.fromData(new String(buf));
                    user.addContact(u, null);
                    System.out.println("Imported " + u.toString());
                }
            } else if (params[params.length-1].equals("share_log")) {
                ShareModel model = null;
                for (String[] ks : lg.models.keySet()) {
                    if (ks[0].equals(params[0])) {
                        model = lg.models.get(ks);
                        break;
                    }
                }
                if (model != null) {
                    model.printEvents(null, System.out, verbose);
                } else
                    System.err.println("Invalid share " + params[0]);
            } else if (params[params.length-1].equals("slcs")) {
                LocalUser lu = lg.checkLocalUserSLCS(params[0], params[1], params[2]);
                lg.importLocalUser(lu);
                System.out.println("Created user: " + lu);
            } else if (params[params.length-1].equals("cert")) {
                LocalUser lu = lg.users.get(params[1]);
                if (lu != null) {
                    File f = new File(params[0]);
                    lu.importRootCert(f);
                    System.out.println("Imported certificate to: " + lu);
                    lu.saveData();
                } else
                    System.err.println("Unknown local user " + params[1]);
            } else {
                System.err.println("Internal error: " + params[params.length-1]);
                System.exit(1);
            }
        }

        if (list)
            lg.printList(System.out);

        if (startLookup || start)
            lg.initNetwork();
        if (startLookup) {
            lg.initLookup();
            lg.startLookup();
        }
        if (start) {
            lg.initUI();
            lg.init(interconnect);
            lg.start();
        }
        if (startLookup || start)
            lg.startNetwork();
        lg.waitForThreads();
        lg.closeUI();
        System.exit(0);
   }
}
