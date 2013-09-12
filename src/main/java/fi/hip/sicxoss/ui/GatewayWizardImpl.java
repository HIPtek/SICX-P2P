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

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

import org.apache.log4j.Logger;

import org.ciscavate.cjwizard.*;
import org.ciscavate.cjwizard.pagetemplates.*;
import fi.hip.sicxoss.LocalGateway;
import fi.hip.sicxoss.ident.LocalUser;

/**
 * A simple wizard to set the initial configuration
 * @author koskela
 */
public class GatewayWizardImpl extends JDialog
    implements GatewayWizard, Runnable {

    private static final Logger log = Logger.getLogger(GatewayWizardImpl.class);
    
    private boolean result;
    private Image sideImg;
    private Border pageBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
    private Border pageBorder2 = BorderFactory.createEmptyBorder(20, 20, 20, 20);

    private GatewayWizardImpl wizard;

    public GatewayWizardImpl() {
        wizard = this;
    }

    public boolean runWizard() {
        log.info("running the wizard.. ");
        setLocationRelativeTo(null);
        setVisible(true);
        synchronized (this) {
            try {
                this.wait();
            } catch (Exception ex) {
            }
        }
        return result;
    }

    private LocalUser currentUser;
    private LocalGateway lg;

    private String slcslogin;
    private String slcspasswd;
    private JDialog slcsdlg;
    private boolean slcsAuth;
    private boolean useSLCS;
    private Component SLCSCheckComponent;
    private Component SLCSOkComponent;
    private JTextArea SLCSOkText;

    public void run() {
        checkCredentials(slcslogin, slcspasswd);
    }
    
    private void setSLCSAuth(boolean ok) {

        // show / hide stuff.

        slcsAuth = ok;
        if (ok)
            SLCSOkText.setText("\nYour SICX credentials have been verified, " + currentUser.getFullName());
        SLCSCheckComponent.setVisible(!slcsAuth);
        SLCSOkComponent.setVisible(slcsAuth);
    }

    private boolean checkCredentials(String login, String passwd) {

        /*
        JOptionPane.showMessageDialog(this,
                                      "Logging in",
                                      "Logging in to your SICX account, please wait..",
                                      JOptionPane.INFORMATION_MESSAGE);
        */
        
        currentUser = null;
        try {
            
            currentUser = lg.checkLocalUserSLCS(login, passwd, login); 
            
            // show some sort of 'welcome' .. ..
            
        } catch (Exception ex) {
            log.error("error logging in to sicx: ..");
        }

        setSLCSAuth(currentUser != null);

        slcsdlg.hide();
        //dlg.setVisible(false);
        if (currentUser != null) {
            JOptionPane.showMessageDialog(this,
                                          "Welcome " + currentUser.getFullName() + ".\nYour account has been verified.",
                                          "Logged in",
                                          JOptionPane.INFORMATION_MESSAGE);

            // we should show some sort of message in the wizard..

            return true;
        } else {
            JOptionPane.showMessageDialog(this,
                                          "Could not log in to your SICS account.",
                                          "Login failed",
                                          JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public void init(final LocalGateway lg) {
        
        TrayIconUI.initStyles();
        sideImg = TrayIconUI.loadImage("/graphics/wizard.png");
        this.lg = lg;

        final WizardContainer wc =
            new WizardContainer(new GatewayPageFactory(),
                                new GatewayPageTemplate(),
                                new StackWizardSettings());
      
        wc.addWizardListener(new WizardListener(){
                @Override
                public void onCanceled(List<WizardPage> path, WizardSettings settings) {
                    log.debug("cancel settings: "+wc.getSettings());
                    GatewayWizardImpl.this.dispose();
                    synchronized (GatewayWizardImpl.this) {
                        result = false;
                        GatewayWizardImpl.this.notify();
                    }
                }

                @Override
                public void onFinished(List<WizardPage> path, WizardSettings settings) {
                    log.debug("finish settings: "+wc.getSettings());
                    GatewayWizardImpl.this.dispose();

                    String fullName = wc.getSettings().get("nameField").toString();
                    String nick = wc.getSettings().get("nickField").toString();
                    String folder = wc.getSettings().get("folderField").toString();
                    int port = Integer.parseInt(wc.getSettings().get("portField").toString());
                    String lookup = wc.getSettings().get("lookupField").toString();
                    
                    try {
                        if (useSLCS)
                            lg.createDefaultConfig(currentUser, folder, port, lookup);
                        else
                            lg.createDefaultConfig(nick, fullName, folder, port, lookup);
                        result = true;
                    } catch (Exception ex) {
                        log.error("error creating config: " + ex);
                        result = false;
                    }

                    synchronized (GatewayWizardImpl.this) {
                        GatewayWizardImpl.this.notify();
                    }
                }

                @Override
                public void onPageChanged(WizardPage newPage, List<WizardPage> path) {
                    log.debug("page changed settings: "+wc.getSettings());
                    if (("" + wc.getSettings().get("useSicx")).equals("true")) {
                    }
                }
            });
      
        // Set up the standard bookkeeping stuff for a dialog, and
        // add the wizard to the JDialog:
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.getContentPane().add(wc);
        this.setSize(600, 440);
        //this.setPreferredSize(600, 440);
        //this.pack();
        setTitle("SICX OSS Gateway wizard");
    }

    private JTextArea wrapLabel(String text) {
        JTextArea ta = new JTextArea(text, 1, 1);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setOpaque(false);
        ta.setEditable(false);
        return ta;
    }

    /**
     * Implementation of PageFactory to generate the wizard pages needed
     * for the wizard.
     */
    private class GatewayPageFactory implements PageFactory {
      
        // To keep things simple, we'll just create an array of wizard pages:
        private final WizardPage[] pages = {

            // hello!
            // license.
            // first: name
            // second: initial share & data store location
            // third: network access things - webdav port, lookup server
            // done.


            new WizardPage("welcome", "Welcome to the SICX OSS Gateway") {
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder2);
                    JTextArea ta = wrapLabel(TrayIconUI.loadText("/texts/welcome.txt"));
                    ta.setAlignmentX(Component.CENTER_ALIGNMENT);
                    add(ta, BorderLayout.CENTER);
                }
            },
            new WizardPage("license", "SICX OSS Gateway EULA") {

                private boolean isAccepted = false;
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder);
                    
                    add(wrapLabel("In order to use this software, you need to accept the following license agreement."), BorderLayout.NORTH);
                    
                    String eula = TrayIconUI.loadText("/texts/eula.txt");
                    JTextArea ta = wrapLabel(eula);
                    ta.setOpaque(true);
                    JScrollPane scrollPane = new JScrollPane(ta, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                                             JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                    add(scrollPane, BorderLayout.CENTER);

                    JCheckBox box = new JCheckBox("Accept");
                    box.addItemListener(new ItemListener() {
                            public void itemStateChanged(ItemEvent itemEvent) {
                                AbstractButton abstractButton = (AbstractButton)itemEvent.getSource();
                                int state = itemEvent.getStateChange();
                                isAccepted = state == ItemEvent.SELECTED;
                                setNextEnabled(isAccepted);
                            }
                        });

                    box.setName("licenseAccept");
                    add(new JLabel("I Accept:"), BorderLayout.SOUTH);
                    add(box, BorderLayout.SOUTH);
                    setNextEnabled(false);
                }

                public void rendering(List<WizardPage> path, WizardSettings settings) {
                    super.rendering(path, settings);
                    setNextEnabled(isAccepted);
                }
            },
            new WizardPage("name", "Your SICX OSS identity") {
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder);

                    JPanel lp = new JPanel();
                    lp.setLayout(new BoxLayout(lp, BoxLayout.Y_AXIS));

                    lp.add(wrapLabel("In order to use SICX OSS, you will either need a SICX identity or create a new, SICX OSS- only, identity. Using a SICX identity allows you to connect to your SICX resources, while the SICX OSS identity can only be used with other SICX OSS users. If you do not have a SICX identity, please choose to create a new SICX OSS identity."));

                    // sicx panel
                    final JPanel identsicx = new JPanel();
                    identsicx.setLayout(new BoxLayout(identsicx, BoxLayout.Y_AXIS));

                    JPanel p = new JPanel();
                    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                    SLCSCheckComponent = p;

                    p.add(wrapLabel("\nSICX login:"));
                    
                    final JTextField loginfield = new JTextField();
                    loginfield.setName("loginField");
                    p.add(loginfield);

                    p.add(wrapLabel("\nPassword:"));
                    
                    final JTextField passwdfield = new JPasswordField();
                    passwdfield.setName("passwordField");
                    p.add(passwdfield);

                    JButton checkButton = new JButton("Check");
                    checkButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    p.add(checkButton);
                    identsicx.add(p);

                    p = new JPanel();
                    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                    SLCSOkComponent = p;
                    SLCSOkText = wrapLabel("Your credentials have not been verified yet.");
                    p.add(SLCSOkText);
                    identsicx.add(p);
                    p.setVisible(false);
                    
                    checkButton.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {

                                slcslogin = loginfield.getText();
                                slcspasswd = passwdfield.getText();

                                JDialog dlg = new JDialog(wizard, "Checking credentials", true);
                                dlg.add(BorderLayout.CENTER, new JLabel("Checking credentials, please wait."));
                                dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                                dlg.setSize(300, 75);
                                dlg.setLocationRelativeTo(null);
                                slcsdlg = dlg;

                                Thread t = new Thread(wizard);
                                t.start();
                                
                                dlg.show();
                                setNextEnabled(slcsAuth);
                            }
                        });


                    // oss panel
                    final JPanel identoss = new JPanel();
                    identoss.setLayout(new BoxLayout(identoss, BoxLayout.Y_AXIS));

                    identoss.add(wrapLabel("\nFull name:"));
                    
                    JTextField field = new JTextField();
                    field.setName("nameField");
                    identoss.add(field);

                    identoss.add(wrapLabel("\nNick name:"));
                    
                    field = new JTextField();
                    field.setName("nickField");
                    identoss.add(field);

                    // radiobuttons..
                    JRadioButton ossButton = new JRadioButton("A new SICX OSS identity");
                    ossButton.setName("useOss");
                    ossButton.setActionCommand("oss");

                    JRadioButton sicxButton = new JRadioButton("Use existing SICX account");
                    sicxButton.setName("useSicx");
                    sicxButton.setActionCommand("sicx");
                    
                    ButtonGroup group = new ButtonGroup();
                    group.add(sicxButton);
                    group.add(ossButton);

                    sicxButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                    ossButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                    
                    lp.add(sicxButton);
                    lp.add(ossButton);
                    
                    ossButton.addItemListener(new ItemListener() {
                            public void itemStateChanged(ItemEvent itemEvent) {
                                AbstractButton abstractButton = (AbstractButton)itemEvent.getSource();
                                if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                                    identsicx.setVisible(false);
                                    identoss.setVisible(true);
                                    useSLCS = false;
                                    setNextEnabled(true);
                                }
                            }
                        });
                    sicxButton.addItemListener(new ItemListener() {
                            public void itemStateChanged(ItemEvent itemEvent) {
                                AbstractButton abstractButton = (AbstractButton)itemEvent.getSource();
                                if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                                    identsicx.setVisible(true);
                                    identoss.setVisible(false);
                                    useSLCS = true;
                                    setNextEnabled(slcsAuth);
                                }
                            }
                        });


                    lp.add(identsicx);
                    lp.add(identoss);

                    identsicx.setVisible(true);
                    sicxButton.setSelected(true);

                    identoss.setVisible(false);
                    ossButton.setSelected(false);
                    useSLCS = true;
                    slcsAuth = false;

                    add(lp, BorderLayout.NORTH);
                }

                public void rendering(List<WizardPage> path, WizardSettings settings) {
                    super.rendering(path, settings);
                    
                    setNextEnabled(!useSLCS || slcsAuth);
                }
            },
            new WizardPage("stores", "Shares and storage location") {
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder);

                    JPanel lp = new JPanel();
                    lp.setLayout(new BoxLayout(lp, BoxLayout.Y_AXIS));

                    lp.add(wrapLabel("Next, we will create an initial content folder for you in the system. This can later be shared with friends, or you can create other folders for that purpose. Please enter a name for this folder:\n"));
                    
                    JTextField field = new JTextField();
                    field.setName("folderField");
                    field.setText("Home");
                    lp.add(field);

                    add(lp, BorderLayout.NORTH);
                }
            },
            new WizardPage("network", "Network access") {
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder);

                    JPanel lp = new JPanel();
                    lp.setLayout(new BoxLayout(lp, BoxLayout.Y_AXIS));

                    lp.add(wrapLabel("The SICX OSS Gateway provides a WebDAV interface by which the folders can be mounted as network drives or web folders. Please enter the port on which we should provide this folder. The folders can be accessed by mounting [webdav/http]://localhost:<port>. Note that mounting network drives in windows requires that the port is 80, but you can mount it as a 'Web folder' from any port.\n"));
                    
                    JTextField field = new JTextField();
                    field.setName("portField");
                    field.setText(lg.getConfig("webdav.port"));
                    lp.add(field);

                    lp.add(wrapLabel("\nThe SICX OSS Gateway benefits from using a lookup / data server to connect to other users. If you have a specific one in mind, please enter the address below:\n"));
                    
                    field = new JTextField();
                    field.setName("lookupField");
                    field.setText(lg.getConfig("lookup.server"));

                    lp.add(field);

                    add(lp, BorderLayout.NORTH);
                }
            },
            new WizardPage("thanks", "We're almost done!") {
                {
                    this.setLayout(new BorderLayout());
                    setBorder(pageBorder2);
                    JTextArea ta = wrapLabel(TrayIconUI.loadText("/texts/end.txt"));
                    ta.setAlignmentX(Component.CENTER_ALIGNMENT);
                    add(ta, BorderLayout.CENTER);
                }

                public void rendering(List<WizardPage> path, WizardSettings settings) {
                    super.rendering(path, settings);
                    setFinishEnabled(true);
                    setNextEnabled(false);
                }
            }
        };
        
      
        /* (non-Javadoc)
         * @see org.ciscavate.cjwizard.PageFactory#createPage(java.util.List, org.ciscavate.cjwizard.WizardSettings)
         */
        @Override
            public WizardPage createPage(List<WizardPage> path,
                                         WizardSettings settings) {
            WizardPage page = pages[path.size()];
            return page;
        }
      
    }


    public class GatewayPageTemplate extends PageTemplate {
        private final PageTemplate _innerTemplate = new TitledPageTemplate();
        public GatewayPageTemplate(){

            this.setLayout(new BorderLayout());
            this.add(new JLabel(new ImageIcon(sideImg)), BorderLayout.WEST);
            this.add(_innerTemplate, BorderLayout.CENTER);
        }

        @Override
        public void setPage(final WizardPage page) {
            SwingUtilities.invokeLater(new Runnable(){
                    @Override
                    public void run() {
                        _innerTemplate.setPage(page);
                    }
                });
        }  
    }
}
