import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

class User{
    WebSocket sock;
    String name;
}
class AppState{
    ArrayList<User> usersInServer = new ArrayList<User>();
    WebSocketServer webSocketServer;
    WebSocketClient webSocketClient;
    JButton connectButton;
    JButton setNameButton;
    JComboBox<Draft> draftComboBox;
    JTextField uriField;
    JTextArea messageView;
    JButton closeButton;
    JTextField chatField;
    JFrame jFrame;
    DefaultListModel<String> listModel;
    JScrollPane scrollPane;
    JPanel userscrollpanel;
//    DefaultListCellRenderer cellRenderer;
}

class ChatMessage{
    String message;
}

class SetNameMessage{
    String desiredName;
}

class UserListMessage{
    ArrayList<String> userList;
    String myName;
}


public class Main {
    private static WebSocketClient makeClient(AppState appState){
        Draft draft = (Draft) appState.draftComboBox.getSelectedItem();
        if(draft==null)return null;
        URI uri = null;
        try {uri = new URI(appState.uriField.getText());}catch (Exception uriex){System.out.println(uriex.toString());}
        if(uri==null)return null;
        return new WebSocketClient(uri, draft) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                appState.messageView.append("You are connected to ChatServer: " + getURI() + "\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                setUINotConnected(appState,true);
            }

            @Override
            public void onMessage(String message) {
                JsonObject jsonObject = new Gson().fromJson(message,JsonObject.class);
                if(jsonObject.has("message")){
                    ChatMessage cm = new Gson().fromJson(message,ChatMessage.class);
                    appState.messageView.append( cm.message );
                    appState.messageView.append("\n");
                    appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                }else if(jsonObject.has("userList")){
                    appState.listModel.clear();

                    appState.userscrollpanel.removeAll();
                    appState.userscrollpanel.revalidate();
                    appState.userscrollpanel.repaint();
                    UserListMessage userListMessage = new Gson().fromJson(message,UserListMessage.class);
                    System.out.println("client got"+userListMessage.toString());
                    for(String usr : userListMessage.userList){
                        UserListItem uli = new UserListItem();
                        uli.textArea1.setText(usr);
                        appState.listModel.addElement(usr);
                        appState.userscrollpanel.add(uli.panel1);

//                        appState.scrollPane.add(uli.panel1);
//                        if(usr.equals(userListMessage.myName)){
//
//                        }
//                            appState.cellRenderer.setFont(appState.cellRenderer.getFont().deriveFont(Font.ITALIC));
//                            appState.cellRenderer.repaint();

                    }
//                    for(UserListItem usr : Collections.list(appState.listModel.elements())){
//                        if (usr.textArea1.getText().equals(userListMessage.myName)) {
//                            usr.textArea1.setFont(usr.textArea1.getFont().deriveFont(Font.ITALIC));
//                        }
//                    }
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appState.messageView.append("websocketclient onClose... " + getURI() + "; Code: " + code + " " + reason + "\n");
            }

            @Override
            public void onError(Exception ex) {
                appState.messageView.append("websocketclient onError...\n$ex\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                ex.printStackTrace();
                setUINotConnected(appState,false);
            }
        };
    }
    private static void setUINotConnected(AppState appState, boolean connected){
        if(!connected) {
            appState.listModel.clear();
            appState.userscrollpanel.removeAll();
            appState.userscrollpanel.revalidate();
            appState.userscrollpanel.repaint();
        }
        appState.connectButton.setEnabled(!connected);
        appState.uriField.setEditable(!connected);
        appState.draftComboBox.setEditable(!connected);
        appState.closeButton.setEnabled(connected);
        appState.chatField.setEnabled(connected);
        appState.setNameButton.setEnabled(connected);
    }

    private static void sendUserList(AppState appState){
        UserListMessage bcm = new UserListMessage();
        ArrayList<String> ids = new ArrayList<String>();
        for(User usr : appState.usersInServer){
            ids.add(usr.name);
        }
        bcm.userList = ids;
        for(User user : appState.usersInServer){
            bcm.myName = user.name;
            if(user.sock.isOpen())
                user.sock.send(new Gson().toJson(bcm));
        }
    }

    private static WebSocketServer makeServer(AppState appState){
        int port = Integer.parseInt(appState.uriField.getText().split(":")[2]);
        return new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                User user = new User();
                user.name = "Unnamed User";
                user.sock = conn;
                appState.usersInServer.add(user);
                ChatMessage cm = new ChatMessage();
                cm.message = "welcome to the server";
                conn.send(new Gson().toJson(cm));
                sendUserList(appState);
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                for(User user : appState.usersInServer){
                    if(conn == user.sock){
                        appState.usersInServer.remove(user);
                        break;
                    }
                }
                sendUserList(appState);
                System.out.println(conn+" disconnected");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                System.out.println("server recieved: "+message);
                JsonObject jsonObject = new Gson().fromJson(message,JsonObject.class);
                User auser=null;
                for(User user : appState.usersInServer){
                    if(user.sock==conn){
                        auser = user;
                        break;
                    }
                }
                if(auser==null)return;
                if(jsonObject.has("desiredName")){
                    SetNameMessage setNameMessage = new Gson().fromJson(message,SetNameMessage.class);
                    auser.name = setNameMessage.desiredName;
                    sendUserList(appState);
                }else if (jsonObject.has("message")){
                    ChatMessage chatMessage = new Gson().fromJson(message,ChatMessage.class);
                    chatMessage.message = auser.name +" says "+chatMessage.message;
                    broadcast(new Gson().toJson(chatMessage));
                }

            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                ex.printStackTrace();
            }

            @Override
            public void onStart() {
                System.out.println("Server started!");
                setConnectionLostTimeout(0);
                setConnectionLostTimeout(100);
            }
        };
    }

    private static ActionListener connectButtonPressed(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient = makeClient(appState);
                appState.closeButton.setEnabled(true);
                appState.connectButton.setEnabled(false);
                appState.uriField.setEditable(false);
                appState.draftComboBox.setEditable(false);
                appState.webSocketClient.connect();
            }
        };
    }
    private static ActionListener makeStartServerActionListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketServer = makeServer(appState);
                appState.webSocketServer.start();
            }
        };
    }
    private static ActionListener closeButtonListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient.close();
                try{ appState.webSocketServer.stop(); }catch (Exception exStop){}
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                setUINotConnected(appState,false);
            }
        };
    }
    private static WindowListener windowCloseListener(AppState appState){
        return new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try{appState.webSocketServer.stop();}catch (Exception exso){}
                appState.webSocketClient.close();
                appState.jFrame.dispose();
            }
        };
    }
    private static ActionListener chatFieldListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChatMessage cm = new ChatMessage();
                cm.message = appState.chatField.getText();
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static ActionListener setNameButtonListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SetNameMessage cm = new SetNameMessage();
                cm.desiredName = appState.chatField.getText();
                appState.chatField.setText("");
                appState.webSocketClient.send(new Gson().toJson(cm));
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static void startApp(){
        AppState appState = new AppState();
        Samform1 sf = new Samform1();

        appState.jFrame = new JFrame("WebSocket Chat Client");
        appState.jFrame.setContentPane(sf.panel1);
        appState.uriField = sf.uriField;
        appState.connectButton = sf.connectToServerButton;
        appState.setNameButton = sf.setNameButton;
        appState.closeButton = sf.closeConnectionsButton;
        appState.messageView = sf.textArea1;
        appState.chatField= sf.chatField;
        sf.chatScrollPane.setViewportView(appState.messageView);
        JButton startServer = sf.startServerButton;

//        appState.cellRenderer = new DefaultListCellRenderer();
        appState.scrollPane = sf.scrollpane;
        appState.userscrollpanel = sf.userscrollpanel;
        appState.scrollPane.setViewportView(appState.userscrollpanel);

//        sf.userscrollpanel.add(new JLabel("eh"));
        appState.userscrollpanel.setPreferredSize(new Dimension(100,500));
//        UserListItem uli = new UserListItem();
//        uli.textArea1.setText("wooo");
//        sf.userscrollpanel.add(uli.panel1);
////        sf.userscrollpanel.revalidate();
//        UserListItem uli2 = new UserListItem();
//        uli2.textArea1.setText("ttt");
//        sf.userscrollpanel.add(uli2.panel1);
//        sf.userscrollpanel.repaint();
//        sf.userscrollpanel.revalidate();
//        sf.userscrollpanel.add(new JLabel("hihihihi"));
//        sf.userscrollpanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
//        ((FlowLayout)sf.userscrollpanel.getLayout()).setAlignment(ScrollPane.);


        appState.listModel = new DefaultListModel<String>();
        sf.list1.setModel(appState.listModel);
//        sf.list1.add(appState.cellRenderer);
        appState.draftComboBox = sf.comboBox1;
        appState.draftComboBox.addItem(new Draft_6455());
        appState.uriField.setText("ws://localhost:8887");
        appState.closeButton.setEnabled(false);
        appState.chatField.setText("");

        appState.setNameButton.addActionListener(setNameButtonListener(appState));
        appState.connectButton.addActionListener(connectButtonPressed(appState));
        startServer.addActionListener(makeStartServerActionListener(appState));
        appState.closeButton.addActionListener(closeButtonListener(appState));
        appState.chatField.addActionListener(chatFieldListener(appState));

        setUINotConnected(appState,false);

        Dimension d = new Dimension(700, 800);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(windowCloseListener(appState));
        appState.jFrame.setVisible(true);
    }

    public static void main(String[] args) {
//        System.out.println("hihi");
//        List<String> al = new ArrayList<String>(){"hi","cool"};
//        List<String> al = Arrays.asList("hi", "cool", "yessir");
//        System.out.println(reverseAll(al));

        startApp();
        startApp();
    }

    /**
     * Finish the following method to conform to the @return documentation
     *
     * @param items list of strings
     * @return A reversed list, where every string in the list is also reversed
     * <p>
     * Example
     * ["ab", "bc", "cd"] -> ["dc", "cb", "ba"]
     */
    public static List<String> reverseAll(List<String> items) {

//        int aLen = items.size();
//        String[] result = new String[aLen];
//
//        for (int i = 0; i < items.size(); i++) {
//            String toReverse = items.get(i);
//            int len = toReverse.length();
//            char[] build = new char[len];
//            for (int j = 0; j < len; j++) {
//                build[len - 1 - j] = toReverse.charAt(j);
//            }
//            result[aLen - 1 - i] = new String(build);
//        }

        List<String> al = new ArrayList<String>();
        for(int i=0;i<items.size();i++){
            String toAdd = items.get(items.size()-1-i);
            char[] resultchars = new char[toAdd.length()];
            for(int j=0;j<toAdd.length();j++){
                resultchars[j] = toAdd.charAt(toAdd.length()-1-j);
            }
            al.add(new String(resultchars));
        }
//        for (String i : items){
////            StringBuilder sb = new StringBuilder();
////            sb.append(i);
////            sb.reverse();
////            i = sb.toString();
//            List lc = Arrays.asList(i.toCharArray());
//            Collections.reverse(lc);
//
//            al.add(new String(lc.toArray()));
////            al.add(sb.toString());
//        }
//        Collections.reverse(al);
//
        return al;
//        return Arrays.asList(result);
    }
}
