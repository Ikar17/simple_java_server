import com.shared.Visit;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server{
    private final ServerSocket serverSocket;
    private ArrayList<Visit> visits;
    private LinkedBlockingQueue<ClientHandler> clientHandlers;
    private final Thread acceptClientsThread;
    private int freeClientId;
    private final Lock visitsLock;

    public static void main(String[] args) {
        try{
            int port = 9876;
            Server server = new Server(port);

            System.out.println("Klawisz 'q' konczy dzialanie serwera\n");
            char keyQuit = 'q';
            while(true){
                char key = (char)System.in.read();
                if(key == keyQuit) break;
            }

            server.closeServer();

        }catch(Exception e){
            e.printStackTrace();
            System.out.println("Can't create server\n");
        }
    }

    public Server(int port) throws IOException {
        int startHour = 10;
        int startMinute = 0;
        createVisits(LocalTime.of(startHour,startMinute));

        this.visitsLock = new ReentrantLock();
        this.clientHandlers = new LinkedBlockingQueue<>();
        this.freeClientId = 0;

        this.serverSocket = new ServerSocket(port);

        this.acceptClientsThread = new Thread(this::acceptNewClients);
        this.acceptClientsThread.start();
    }
    private void acceptNewClients(){
        try{
            while(true){
                Socket socket = this.serverSocket.accept();
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                System.out.println("Podlaczyl sie nowy klient\n");

                out.writeInt(this.freeClientId);
                out.flush();

                this.visitsLock.lock();
                out.writeObject(this.visits);
                out.flush();
                this.visitsLock.unlock();

                ClientHandler newClient = new ClientHandler(this, socket, out, in, this.freeClientId);
                Thread clientThread = new Thread(newClient);
                clientThread.start();
                this.clientHandlers.add(newClient);

                this.freeClientId++;
            }
        }catch(IOException e){
            System.out.println("Accept clients thread stop working\n");
        }
    }
    public boolean removeClient(ClientHandler clientHandler){
        clientHandler.closeConnect();
        return clientHandlers.remove(clientHandler);
    }
    public void closeServer() throws IOException, InterruptedException{
        for(ClientHandler clientHandler : this.clientHandlers){
            removeClient(clientHandler);
        }
        this.serverSocket.close();
        this.acceptClientsThread.join();
    }
    private boolean createVisits(LocalTime startTime){
        this.visits = new ArrayList<>();
        int numberOfVisits = 8;
        int visitDurationInHour = 1;
        for(int id = 0; id < numberOfVisits; ++id){
            LocalTime endTime = startTime.plusHours(visitDurationInHour);
            Visit visit = new Visit(id,startTime,endTime);
            visits.add(visit);
            startTime = endTime;
        }
        return true;
    }

    public boolean reserveVisit(int visitId, int clientId){
        this.visitsLock.lock();
        Visit visit = this.visits.get(visitId);
        if(!visit.isReserved()){
            System.out.printf("Client o id: %d rezerwuje wizytę nr: %d\n", clientId, visitId);
            visit.setReserved(true);
            visit.setClientId(clientId);
            this.visitsLock.unlock();
            return true;
        }
        this.visitsLock.unlock();
        return false;
    }

    public boolean cancelVisit(int visitId, int clientId){
        this.visitsLock.lock();
        Visit visit = this.visits.get(visitId);
        if(visit.isReserved()){
            System.out.printf("Client o id: %d odwoluje wizytę nr: %d\n", clientId, visitId);
            visit.setReserved(false);
            visit.setClientId(-1);
            this.visitsLock.unlock();
            return true;

        }
        this.visitsLock.unlock();
        return false;
    }
    public void sendUpdatedVisitToAll(int visitId){
        this.visitsLock.lock();
        Visit visit = this.visits.get(visitId);

        for(ClientHandler clientHandler : this.clientHandlers){
            try{
                clientHandler.sendDataToClient(visit);
            }catch (IOException e) {
                System.out.printf("Client with id: %d is being deleted\n", clientHandler.getClientId());
                removeClient(clientHandler);
            }
        }

        this.visitsLock.unlock();
    }
}