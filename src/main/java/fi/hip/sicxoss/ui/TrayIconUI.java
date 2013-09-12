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
package fi.hip.sicxoss.ui;

import java.util.*;
import java.io.*;
import java.net.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import org.apache.log4j.Logger;

import fi.hip.sicxoss.ident.*;
import fi.hip.sicxoss.io.*;
import fi.hip.sicxoss.model.*;
import fi.hip.sicxoss.*;


/**
 * Implements the system tray icon- UI. A small menu for controlling
 * the application, complete with OSD notifications
 * @author koskela
 */
public class TrayIconUI 
    implements GatewayUI {

    private static final Logger log = Logger.getLogger(TrayIconUI.class);

    // share obs
    @Override
    public void userAdded(User newUser, User eventIssuer, ShareModel share) {
        log.info("user added");
    }

    @Override
    public void userRemoved(User newUser, User eventIssuer, ShareModel share) {
        log.info("user removed");
    }

    @Override
    public void folderCreated(CollectionModel ret, User eventIssuer, ShareModel share) {
        log.info("folder created");
    }

    @Override
    public void fileCreated(ContentModel ret, User eventIssuer, ShareModel share) {
        log.info("file created");
    }

    @Override
    public void pathRemoved(ItemModel item, User eventIssuer, ShareModel share) {
        log.info("something removed");
    }

    @Override
    public void fileUpdated(FileModel fi, User eventIssuer, ShareModel share) {
        log.info("file updated");
    }

    @Override
    public void pathMoved(ItemModel item, String oldPath, User eventIssuer, ShareModel share) {
        log.info("path moved");
    }

    @Override
    public void inviteSent(User newUser, ShareModel share) {
        log.info("invite sent to " + newUser);
    }

    @Override
    public void inviteReply(User newUser, boolean accept, ShareModel share) {
        log.info("invite reply got from " + newUser + " and is " + accept);
        if (accept)
            showInfo(newUser.getFullName() + " has joined " + share.getName(),
                     "Your friend " + newUser.getFullName() + " has responded to your invite to join the shared folder " + share.getName() + ". The synchronization will commence shortly.");
        else
            showInfo(newUser.getFullName() + " has declined your invitation",
                     "Your friend " + newUser.getFullName() + " has declined your invitation to join the shared folder " + share.getName() + ".");
    }

    @Override
    public void userStatusChanged(User newUser, boolean online, ShareModel share) {
        log.info("user went online: " + online);

        Date d = userStates.get(newUser.getId());
        boolean wason = (d != null && d == onlineDate);
        if (wason != online) {
            if (online)
                showInfo(newUser.getFullName() + " logged in",
                         newUser.getFullName() + " is online now and available for sharing.");
            else
                showInfo(newUser.getFullName() + " logged off",
                         newUser.getFullName() + " is offline now and unavailable for sharing.");
        }
        if (online)
            userStates.put(newUser.getId(), onlineDate);
        else if (d == null || d == onlineDate) // update offline only if we have no record of it
            userStates.put(newUser.getId(), new Date());
    }
    
    // localuser obs:

    @Override
    public void contactAdded(LocalUser user, User contact, ShareModel share) {
        log.info("contact added");
        initPopup();
    }

    @Override
    public void contactRemoved(LocalUser user, User contact, ShareModel share) {
        log.info("contact removed");
        initPopup();
    }

    @Override
    public void inviteGot(LocalUser user, Invite invite) {
        log.info("We got an invite from " + invite.getContacts().size() + " contacts!");

        java.util.List<User> c = invite.getContacts();
        String cname = "An unknown contact";
        if (c.size() > 0)
            cname = c.get(c.size()-1).getFullName();
        showInfo("An invitation received from " + cname,
                 cname + " has invited you to the shared folder '" + invite.name + "' with the greeting '" + invite.description + "'");
        
        initPopup();
    }

    @Override
    public void inviteReplied(LocalUser user, Invite invite) {
        log.info("We replied to an invite from " + invite.getContacts().size() + " contacts!");
        initPopup();
    }

    // gw:

    @Override
    public void userAdded(LocalUser u) {
        log.info("local user added");
        u.addObserver(this);
        initPopup();
    }

    @Override
    public void shareAdded(LocalUser u, ShareModel sm) {
        log.info("share added");
        sm.addObserver(this);
        initPopup();
    }

    @Override
    public void shareRemoved(ShareModel sm) {
        log.info("share removed");
        initPopup();
    }

    // misc:

    // local data
    private TrayIcon trayIcon;
    private PopupMenu popup;
    private LocalGateway gw;
    private String lastIcon;
    private Hashtable<String, Date> userStates;
    private Date onlineDate = new Date(0);
    private Desktop desktop;

    private static boolean setStyle(String style) {
        try {
            UIManager.setLookAndFeel(style);
            return true;
        } catch (Exception e) {
            log.warn("l&f '" + style + "' not supported on this platform");
            return false;
        }
    }

    public static boolean initStyles() {
        return setStyle("com.sun.java.swing.plaf.gtk.GTKLookAndFeel") ||
            setStyle(UIManager.getSystemLookAndFeelClassName()) ||
            setStyle(UIManager.getCrossPlatformLookAndFeelClassName());
    }

    private void setIcon(String jarPath) {
        if (lastIcon == null || !lastIcon.equals(jarPath))
            trayIcon.setImage(loadImage(jarPath));
        lastIcon = jarPath;
    }
        
    public static Image loadImage(String jarPath) {
        return Toolkit.getDefaultToolkit().getImage(jarPath.getClass().getResource(jarPath));
    }

    public static String loadText(String jarPath) {
        String ret = "";
        try {
            InputStreamReader ins = new InputStreamReader(jarPath.getClass().getResource(jarPath).openStream());
            BufferedReader br = new BufferedReader( ins );
            String l;
            while ((l = br.readLine()) != null)
                ret += l + "\n";
        } catch (Exception ex) {}
        return ret;
    }
        

    private void showError(String head, String content) {
        trayIcon.displayMessage(head, content, TrayIcon.MessageType.ERROR);
    }

    private void showInfo(String head, String content) {
        trayIcon.displayMessage(head, content, TrayIcon.MessageType.INFO);
    }

    private boolean openShare(String path) {
        
        try {
            String url = "localhost:" + gw.getConfig("webdav.port") + path;
            log.info("should open up: " + url);
            // try a couple of apps..
                        
            String[] filebrowsers = new String[] {
                "cadaver", "http://",
                "konqueror", "webdav://",
                "dolphin", "webdav://",
                "nautilus", "http://"
                /*,
                  "explorer", "http://"*/ };
                                
            boolean opened = false;
            Runtime rt = Runtime.getRuntime();
            for (int i=0; !opened && i < filebrowsers.length; i += 2) {
                try {
                    String[] cmd = new String[] { filebrowsers[i], filebrowsers[i+1] + url };
                    rt.exec(cmd);
                    opened = true;
                    log.debug("we had success with " + filebrowsers[i]);
                } catch (Exception ex) { }
            }

            /*
              try {
              File f = new File(new URI(uri));
              desktop.open(f);
              } catch (Exception ex) {
              log.error("error opening path: " + ex);
              }
            */
            // last resort..
            if (!opened && desktop != null) { // && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.browse(new URI("http:/" + url));
            }
            return true;
        } catch (Exception ex) {
            log.error("error opening share: " + ex);
        }
        return false;
    }

    private void initPopup() {
        log.info("initing the popup menu");

        PopupMenu popup = new PopupMenu();
        MenuItem item;
        PopupMenu sub;
        
        // pending invites
        //   - show details
        //   - accept, reject
        java.util.List<LocalUser> users = gw.getLocalUsers();
        boolean hasInvite = false;
        for (final LocalUser u : users) {
            for (final Invite inv : u.getReceivedInvites()) {
                if (!hasInvite) {
                    item = new MenuItem("Pending invites:");
                    item.setEnabled(false);
                    popup.add(item);
                    hasInvite = true;
                }
                
                String str = inv.name + " from ";
                java.util.List<User> contacts = inv.getContacts();
                if (contacts.size() > 1)
                    str += contacts.size() + " contacts";
                else
                    str += contacts.get(0).getFullName();
                sub = new PopupMenu(str);
                item = new MenuItem("Info");
                item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            try {
                                JOptionPane.showMessageDialog(null,
                                                              inv.getDescription(),
                                                              "Invite description",
                                                              JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                log.error("error while showing invite: " + ex);
                            }
                        }
                    });
                sub.add(item);
                
                item = new MenuItem("Accept");
                item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            try {
                                String ans = JOptionPane.showInputDialog(null, 
                                                                         "Please enter the path at which the\nshare should be mounted:",
                                                                         inv.name);
                                if (ans != null && ans.length() > 0) {
                                    u.acceptAndCreateInvite(inv, ans, "/" + ans);
                                    int open = JOptionPane.showConfirmDialog(null,
                                                                             "You successfully joined the shared folder.\nDo you want to open it now?",
                                                                             "Share joined",
                                                                             JOptionPane.YES_NO_OPTION);
                                    if (open == 0) 
                                        openShare("/" + ans);
                                }
                            } catch (Exception ex) {
                                log.error("error while accepting invite: " + ex);
                            }
                            initPopup();
                        }
                    });
                sub.add(item);

                item = new MenuItem("Reject");
                item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            u.rejectInvite(inv);
                            initPopup();
                        }
                    });
                sub.add(item);
                popup.add(sub);
            }
        }

        if (hasInvite)
            popup.addSeparator();
        
        // collect all shares. make a share popup menu
        //   - with open, invite ..
        sub = new PopupMenu("Shares");
        for (final String[] k : gw.getShareModels().keySet()) {
            PopupMenu share = new PopupMenu(k[0]);
            
            item = new MenuItem("Open");
            item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        openShare(k[1]);
                    }
                });
            share.add(item);
                
            item = new MenuItem("Invite");
            item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        log.info("should invite people..");
                    }
                });
            share.add(item);

            item = new MenuItem("Info");
            item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        log.info("should open info..");
                    }
                });
            share.add(item);
            
            sub.add(share);
        }
        // create new share
        sub.addSeparator();
        item = new MenuItem("Create new");
        item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        String ans = JOptionPane.showInputDialog(null, 
                                                                 "Please enter the path of the new shared folder:",
                                                                 "");
                        if (ans != null && ans.length() > 0) {
                            ShareModel share = gw.createShare(ans, "/" + ans);
                            share.start();

                            int open = JOptionPane.showConfirmDialog(null,
                                                                     "The share was successfully created.\nDo you want to open it now?",
                                                                     "Share created",
                                                                     JOptionPane.YES_NO_OPTION);
                            if (open == 0) 
                                openShare("/" + ans);
                        }
                    } catch (Exception ex) {
                        log.error("error while creating new share: " + ex);
                    }
                }
            });
        sub.add(item);
        popup.add(sub);

        // contacts
        //   - add contact, invite to
        for (LocalUser u : users) {

            sub = new PopupMenu("Contacts (of " + u.getFullName() + ")");

            for (User cu : u.contactManager().getContacts()) {
                PopupMenu csub = new PopupMenu(cu.getFullName());
                sub.add(csub);
            }

            popup.add(sub);
        }

        // exit?
        popup.addSeparator();
        item = new MenuItem("Exit");
        item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        gw.stop();
                        gw.waitForThreads();
                    } catch (Exception ex) {
                        showError("Error while quitting", "Could not stop the SICX OSS Gateway");
                        // todo: show dialog + system.exit
                    }
                    System.exit(0);
                }
            });
        popup.add(item);

        // ..and finally:
        trayIcon.setPopupMenu(popup);
        if (hasInvite)
            setIcon("/icons/invite.png");
        else
            setIcon("/icons/default.png");
    }

    public String queryPassword(String head, String body) {

        String ans = JOptionPane.showInputDialog(null, body, "");
        return ans;
    }

    public void init(LocalGateway gw) {
        log.info("initing..");

        this.gw = gw;
        this.userStates = new Hashtable();

        if (Desktop.isDesktopSupported())
            desktop = Desktop.getDesktop();
        else
            log.warn("Desktop not supported on this platform");

        if (SystemTray.isSupported()) {
            
            // try some look and feels
            if (!initStyles())
                log.warn("none of the l&fs worked");
            
            SystemTray tray = SystemTray.getSystemTray();

            Image image = loadImage("/icons/default.png");
            trayIcon = new TrayIcon(image, "SICX OSS Gateway");
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseListener() {
                    public void mouseClicked(MouseEvent e) {
                        log.debug("Tray Icon - Mouse clicked!");                 
                    }
                    
                    public void mouseEntered(MouseEvent e) {
                        log.debug("Tray Icon - Mouse entered!");                 
                    }

                    public void mouseExited(MouseEvent e) {
                        log.debug("Tray Icon - Mouse exited!");                 
                    }

                    public void mousePressed(MouseEvent e) {
                        log.debug("Tray Icon - Mouse pressed!");                 
                    }

                    public void mouseReleased(MouseEvent e) {
                        log.debug("Tray Icon - Mouse released!");                 
                    }
                });
            trayIcon.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        log.debug("Tray Icon - An Action Event Has Been Performed!");
                    }
                });


            initPopup();

            try {
                tray.add(trayIcon);
            } catch (Exception ex) {
                log.error("could not add the tray icon to the system tray!");
            }
        } else
            log.error("sorry, but the system tray- things are not supported");

   }

    public void close() {
        log.info("closing..");
    }
}
