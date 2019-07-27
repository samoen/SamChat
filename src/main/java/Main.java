import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import netscape.javascript.JSObject;
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

class AppState{
    WebSocketServer webSocketServer;
    WebSocketClient webSocketClient;
    JButton connectButton;
    JComboBox<Draft> draftComboBox;
    JTextField uriField;
    JTextArea messageView;
    JButton closeButton;
    JTextField chatField;
    JFrame jFrame;
    String myName;
}

class ChatMessage{
    public String message=null;
    public String fromUser=null;

}


public class Main {

    private static int PORT = 8887;
    public static String quickMessage(String mess,String from){
        JsonObject toSend = new JsonObject();
        toSend.addProperty("message",mess);
        toSend.addProperty("fromUser",from);
        return toSend.toString();
    }
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
            }

            @Override
            public void onMessage(String message) {
                ChatMessage chatMessage = new Gson().fromJson(message, ChatMessage.class);
                appState.messageView.append(chatMessage.fromUser +" says: "+ chatMessage.message);
//                JsonObject chatMessage = new Gson().fromJson(message, JsonObject.class);
//                appState.messageView.append(chatMessage.get("fromUser") +" says: "+ chatMessage.get("message"));
                appState.messageView.append("\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appState.messageView.append("You have been disconnected from: " + getURI() + "; Code: " + code + " " + reason + "\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                appState.connectButton.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftComboBox.setEditable(true);
                appState.closeButton.setEnabled(false);
            }

            @Override
            public void onError(Exception ex) {
                appState.messageView.append("Exception occured ...\n$ex\n");
                appState.messageView.setCaretPosition(appState.messageView.getDocument().getLength());
                ex.printStackTrace();
                appState.connectButton.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftComboBox.setEditable(true);
                appState.closeButton.setEnabled(false);
            }
        };
    }

    private static WebSocketServer makeServer(){
        return new WebSocketServer(new InetSocketAddress(PORT)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                conn.send(quickMessage("Welcome to the server!","Server"));
                broadcast(quickMessage("Someone entered the chatroom","Server"));
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                broadcast(quickMessage(conn +" has left the room!","Server"));
                System.out.println(conn+" has left the room!");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                broadcast(message);
                System.out.println(conn.getRemoteSocketAddress()+": "+message);
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

    private static ActionListener getConnectListener(AppState appState){
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
                appState.webSocketServer = makeServer();
                appState.webSocketServer.start();
            }
        };
    }
    private static ActionListener makeCloseListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient.close();
                try{ appState.webSocketServer.stop(); }catch (Exception exStop){}
            }
        };
    }
    static WindowListener makeWindowListener(AppState appState){
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
    private static ActionListener makeChatFieldListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient.send(quickMessage(appState.chatField.getText(),appState.myName));
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static void startApp(){
        AppState appState = new AppState();
        appState.myName = "someone";
        appState.uriField = new JTextField();
        appState.connectButton = new JButton("connectButton");
        appState.closeButton = new JButton("closeButton");
        appState.messageView = new JTextArea();
        appState.chatField= new JTextField();
        appState.jFrame = new JFrame("WebSocket Chat Client");
        String location = "ws://localhost:"+ PORT;
        JButton startServer = new JButton("Start Server");
        GridLayout layout = new GridLayout();
        Draft[] drafts = new Draft[]{new Draft_6455()};
        layout.setColumns(1);
        layout.setRows(7);
        appState.draftComboBox = new JComboBox<Draft>(drafts);
        appState.uriField.setText(location);
        appState.closeButton.setEnabled(false);
        JScrollPane scroll = new JScrollPane();
        scroll.setViewportView(appState.messageView);
        appState.chatField.setText("");
        appState.connectButton.addActionListener(getConnectListener(appState));
        startServer.addActionListener(makeStartServerActionListener(appState));
        appState.closeButton.addActionListener(makeCloseListener(appState));
        appState.chatField.addActionListener(makeChatFieldListener(appState));
        Dimension d = new Dimension(300, 400);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(makeWindowListener(appState));
        Container c = appState.jFrame.getContentPane();
        c.setLayout(layout);
        c.add(appState.draftComboBox);
        c.add(appState.uriField);
        c.add(appState.connectButton);
        c.add(startServer);
        c.add(appState.closeButton);
        c.add(scroll);
        c.add(appState.chatField);
        appState.jFrame.setLocationRelativeTo(null);
        appState.jFrame.setVisible(true);
    }

    public static void main(String[] args){
        System.out.println("hihi");
//        ChatClientKt.alloh();
//        ChatClientKt.alloh();
        startApp();
        startApp();
    }
}
