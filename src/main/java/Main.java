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
    JButton connect;
    JComboBox draftField;
    JTextField uriField;
    JTextArea ta;
    JButton close;
    JTextField chatField;
    JFrame jFrame;
}

public class Main {
    private static int PORT = 8887;

    private static WebSocketClient makeClient(AppState appState){
        Draft draft = (Draft) appState.draftField.getSelectedItem();
        if(draft==null)return null;
        URI uri = null;
        try {uri = new URI(appState.uriField.getText());}catch (Exception uriex){System.out.println(uriex);}
        if(uri==null)return null;
        return new WebSocketClient(uri, draft) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                appState.ta.append("You are connected to ChatServer: " + getURI() + "\n");
                appState.ta.setCaretPosition(appState.ta.getDocument().getLength());
            }

            @Override
            public void onMessage(String message) {
                appState.ta.append("got: "+ message + "\n");
                appState.ta.setCaretPosition(appState.ta.getDocument().getLength());
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appState.ta.append("You have been disconnected from: " + getURI() + "; Code: " + code + " " + reason + "\n");
                appState.ta.setCaretPosition(appState.ta.getDocument().getLength());
                appState.connect.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftField.setEditable(true);
                appState.close.setEnabled(false);
            }

            @Override
            public void onError(Exception ex) {
                appState.ta.append("Exception occured ...\n$ex\n");
                appState.ta.setCaretPosition(appState.ta.getDocument().getLength());
                ex.printStackTrace();
                appState.connect.setEnabled(true);
                appState.uriField.setEditable(true);
                appState.draftField.setEditable(true);
                appState.close.setEnabled(false);
            }
        };
    }

    private static WebSocketServer makeServer(){
        return new WebSocketServer(new InetSocketAddress(PORT)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                conn.send("Welcome to the server!");
                broadcast("new connection: " + handshake.getResourceDescriptor()); //This method sends a message to all clients connected
                System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!");
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                broadcast(conn+" has left the room!");
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
                this.setConnectionLostTimeout(0);
                this.setConnectionLostTimeout(100);
            }
        };
    }

    private static ActionListener getConnectListener(AppState appState){
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appState.webSocketClient = makeClient(appState);
                appState.close.setEnabled(true);
                appState.connect.setEnabled(false);
                appState.uriField.setEditable(false);
                appState.draftField.setEditable(false);
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
                appState.webSocketClient.send(appState.chatField.getText());
                appState.chatField.setText("");
                appState.chatField.requestFocus();
            }
        };
    }
    private static void startApp(){
        AppState appState = new AppState();
        appState.uriField = new JTextField();
        appState.connect= new JButton("connect");
        appState.close = new JButton("close");
        appState.ta = new JTextArea();
        appState.chatField= new JTextField();
        appState.jFrame = new JFrame("WebSocket Chat Client");

        String location = "ws://localhost:"+ PORT;
        JButton startServer = new JButton("Start Server");
        GridLayout layout = new GridLayout();
        Draft[] drafts = new Draft[]{new Draft_6455()};
        layout.setColumns(1);
        layout.setRows(7);
        appState.draftField = new JComboBox(drafts);
        appState.uriField.setText(location);
        appState.close.setEnabled(false);
        JScrollPane scroll = new JScrollPane();
        scroll.setViewportView(appState.ta);
        appState.chatField.setText("");
        appState.connect.addActionListener(getConnectListener(appState));
        startServer.addActionListener(makeStartServerActionListener(appState));
        appState.close.addActionListener(makeCloseListener(appState));
        appState.chatField.addActionListener(makeChatFieldListener(appState));
        Dimension d = new Dimension(300, 400);
        appState.jFrame.setPreferredSize(d);
        appState.jFrame.setSize(d);
        appState.jFrame.addWindowListener(makeWindowListener(appState));
        Container c = appState.jFrame.getContentPane();
        c.setLayout(layout);
        c.add(appState.draftField);
        c.add(appState.uriField);
        c.add(appState.connect);
        c.add(startServer);
        c.add(appState.close);
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
