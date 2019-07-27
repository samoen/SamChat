import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer

import javax.swing.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowEvent
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer

fun alloh(){
    val port = 8887 // 843 flash policy port
    val uriField = JTextField()
    val connect= JButton("connect")
    val close = JButton("close")
    val ta = JTextArea()
    val chatField= JTextField()
    val draftField: JComboBox<*>
    val location = "ws://localhost:$port"
    val jFrame = JFrame("WebSocket Chat Client")
    val startServer = JButton("Start Server")
    val c = jFrame.contentPane
    val layout = GridLayout()
    lateinit var cc: WebSocketClient
    lateinit var s:WebSocketServer
    val drafts = arrayOf<Draft>(Draft_6455())

    layout.columns = 1
    layout.rows = 7
    c.layout = layout

    draftField = JComboBox(drafts)
    c.add(draftField)

    uriField.text = location
    c.add(uriField)

    connect.addActionListener {
        try {
//            if(cc!=null){
//                if(cc.isClosed == false){
//                    return@addActionListener
//                }
//            }
//            if(::cc.isInitialized)
             cc = object : WebSocketClient(URI(uriField.text), draftField.selectedItem as Draft) {
                override fun onMessage(message: String) {
                    ta.append("got: $message\n")
                    ta.caretPosition = ta.document.length
                }

                override fun onOpen(handshake: ServerHandshake) {
                    ta.append("You are connected to ChatServer: " + getURI() + "\n")
                    ta.caretPosition = ta.document.length
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    ta.append("You have been disconnected from: " + getURI() + "; Code: " + code + " " + reason + "\n")
                    ta.caretPosition = ta.document.length
                    connect.isEnabled = true
                    uriField.isEditable = true
                    draftField.setEditable(true)
                    close.isEnabled = false
                }
                 override fun onError(ex: Exception) {
                    ta.append("Exception occured ...\n$ex\n")
                    ta.caretPosition = ta.document.length
                    ex.printStackTrace()
                    connect.isEnabled = true
                    uriField.isEditable = true
                    draftField.setEditable(true)
                    close.isEnabled = false
                }
             }

            close.isEnabled = true
//            connect.isEnabled = false
            uriField.isEditable = false
            draftField.isEditable = false
            cc.connect()
        } catch (ex: URISyntaxException) {
            ta.append(uriField.text + " is not a valid WebSocket URI\n")
        }
    }
    c.add(connect)

    startServer.addActionListener {
        s = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                conn.send("Welcome to the server!") //This method sends a message to the new client
                broadcast("new connection: " + handshake.resourceDescriptor) //This method sends a message to all clients connected
                println(conn.remoteSocketAddress.address.hostAddress + " entered the room!")
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                broadcast("$conn has left the room!")
                println("$conn has left the room!")
            }

            override fun onMessage(conn: WebSocket, message: String) {
                broadcast(message)
                println("$conn: $message")
            }

            override fun onMessage(conn: WebSocket?, message: ByteBuffer) {
                broadcast(message.array())
                println(conn.toString() + ": " + message)
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                ex.printStackTrace()
                if (conn != null) {
                    // some errors like port binding failed may not be assignable to a specific websocket
                }
            }

            override fun onStart() {
                println("Server started!")
                connectionLostTimeout = 0
                connectionLostTimeout = 100
            }
        }
        s.start()
    }
    c.add(startServer)

    close.addActionListener {
        cc.close()
        s.stop()
    }
    close.isEnabled = false
    c.add(close)

    val scroll = JScrollPane()
    scroll.setViewportView(ta)
    c.add(scroll)

    chatField.text = ""
    chatField.addActionListener {
        cc.send(chatField.text)
        chatField.text = ""
        chatField.requestFocus()
    }
    c.add(chatField)

    val d = Dimension(300, 400)
    jFrame.preferredSize = d
    jFrame.size = d

    jFrame.addWindowListener(object : java.awt.event.WindowAdapter() {
        override fun windowClosing(e: WindowEvent?) {
            s.stop()
            cc.close()
            jFrame.dispose()
        }
    })

    jFrame.setLocationRelativeTo(null)
    jFrame.isVisible = true
}