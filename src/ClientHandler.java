import com.shared.CancelVisitMessage;
import com.shared.ReserveVisitMessage;
import com.shared.Visit;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Server server;
    private Socket socket;
    public ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private int clientId;

    public ClientHandler(Server server, Socket socket, ObjectOutputStream outputStream, ObjectInputStream inputStream, int clientId) {
        this.server = server;
        this.socket = socket;
        this.outputStream = outputStream;
        this.inputStream = inputStream;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try{
            while(socket.isConnected()){
                Object object = inputStream.readObject();
                if(object instanceof ReserveVisitMessage reserveVisitMessage){
                    Visit visit = reserveVisitMessage.getVisit();
                    int visitId = visit.getVisitId();
                    this.server.reserveVisit(visitId, clientId);
                    this.server.sendUpdatedVisitToAll(visitId);
                }else if(object instanceof CancelVisitMessage cancelVisitMessage){
                    Visit visit = cancelVisitMessage.getVisit();
                    int visitId = visit.getVisitId();
                    this.server.cancelVisit(visitId, clientId);
                    this.server.sendUpdatedVisitToAll(visitId);
                }
            }
        }catch(IOException e){
            System.out.println("Client thread stop working\n");
        }catch (ClassNotFoundException e) {
            e.printStackTrace();
        }finally {
            server.removeClient(this);
        }
    }

    @Override
    public boolean equals(Object object){
        if(object == this) return true;
        if (!(object instanceof ClientHandler)) return false;

        ClientHandler clientHandler = (ClientHandler) object;
        return clientHandler.clientId == this.clientId;
    }

    public void sendDataToClient(Object object) throws IOException {
        this.outputStream.reset();
        this.outputStream.writeObject(object);
        this.outputStream.flush();
    }

    public void closeConnect() throws IOException{
        outputStream.close();
        inputStream.close();
        socket.close();
    }
}
