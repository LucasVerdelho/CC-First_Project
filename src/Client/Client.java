package FFSync;

//import java.io.ByteArrayInputStream;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import FFSync.Disassembler;
import FFSync.FileInfo;
import FFSync.Interface;

public class Client {
    private static int PACKET_SIZE = 1077;
    private static int FILE_SEG_SIZE = 1033;
    private static int MAXPACKS = 5;


    private static DatagramSocket socket = null; 
    private static Interface it;
    private int ackSequence = 0;

    private InetAddress address;
    private Disassembler dis;
    private int port;

    /**
     * Construtor da classe para estabelecer as variavéis de instância necessárias
     * @param ip = ip destino da máquina a ser ligada
     */
    public Client(InetAddress ip) {
        try{socket = new DatagramSocket(8888);}
        catch(SocketException se){
            System.out.println(se.getMessage());
        }
        dis = new Disassembler();
        try{
            it = new Interface("logFile.txt");
        }
        catch(FileNotFoundException e){
            System.out.println(e.getMessage());
        }
        address = ip;
        port = 8888;
    }

    /**
     * MAIN
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {


        if (args.length == 2 ){
            InetAddress ip = InetAddress.getByName(args[1]); 
            String path = args[0];
            Client client = new Client(ip);
            client.create_connection();
            int nfiles = client.files_to_send(path);
            client.receive_files(nfiles,path);
            socket.close();
            it.flush();
        }
        else{
            System.out.println("Insira a pasta e ip para recebimento dos ficheiros");
        }



    }

    /**
     * Função por estabelecer conexão ao servidor, envia um Ack quando estabelece
     * @throws IOException
     */
    public void create_connection() throws Exception{
        char type = 's';
        byte pack[] = new byte [PACKET_SIZE];
        dis.build_header(pack, type);
        DatagramPacket sendSynPacket = new DatagramPacket(pack, pack.length, address, port);
        socket.send(sendSynPacket);
        it.escreveLog("Estabelecendo conexão...\n", true);

        byte[] ack = new byte[PACKET_SIZE];
        dis.receive_Ack(ack, sendSynPacket, socket, ackSequence, it);
        it.escreveLog("Connected with server\n", true);
        ackSequence++;

    }


    /**
     * 
     * @param path = caminho para a pasta
     * @return número de ficheiros que irão ser recebidos 
     * @throws Exception
     */
    public int files_to_send(String path) throws Exception{
        File directory = new File(path);
        File[] listaF = directory.listFiles(File::isFile);
        byte[] bufferA = new byte[PACKET_SIZE];

        int nFiles;
        if(listaF==null) {
            it.escreveLog("Pasta indicada vazia, irá ser enviado todos ficheiros da pasta do servidor...\n", true);
            new File(path).mkdirs();

            // Send type A
            dis.build_typeA(bufferA,0,it);
            DatagramPacket sendAPacket = new DatagramPacket(bufferA, bufferA.length, address, port);
            socket.send(sendAPacket);
            
            // Receive ACK
            byte[] ack = new byte[PACKET_SIZE];
            dis.receive_Ack(ack, sendAPacket, socket, ackSequence, it);
            ackSequence++;
        }
        else {
            // Send type A
            it.escreveLog("Enviando informações de ficheiros que a pasta indicada contém...\n", true);
            nFiles = listaF.length;
            dis.build_typeA(bufferA,nFiles,it);
            DatagramPacket sendAPacket = new DatagramPacket(bufferA, bufferA.length, address, port);
            socket.send(sendAPacket);

            // Receive ACK
            byte[] ack = new byte[PACKET_SIZE];
            dis.receive_Ack(ack, sendAPacket, socket, ackSequence, it);
            ackSequence++;

            byte[] nomeTemp;
            int fileNameSize;
            long dataDeModif;

            for (File file : listaF) {
                //Send type B
                byte[] buffer = new byte[PACKET_SIZE];
                dis.build_header(buffer,'b');
                fileNameSize = file.getName().length();
                nomeTemp = file.getName().getBytes();
                dataDeModif = file.lastModified();
                dis.build_typeB(buffer,fileNameSize,nomeTemp,dataDeModif,file.getName(),it);
                DatagramPacket sendBPacket = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(sendBPacket);

                // Receive Ack
                dis.receive_Ack(ack, sendAPacket, socket, ackSequence, it);
                ackSequence++;
            }
        }

        // Receive type A
        int nFilesToReceive;
        byte[] pack = new byte[PACKET_SIZE];
        nFilesToReceive = dis.disassemble_typeA(pack, socket, ackSequence, it);
        ackSequence++;
        it.escreveLog("Irá ser recebido do servidor "+ nFilesToReceive + " ficheiros\n",true);

        return nFilesToReceive;
    }

    /**
     * Função principal que recebe os ficheiros
     * @param nfiles = nº de ficheiros que irão ser recebidos
     * @param path = caminho para pasta utilizada para receber os ficheiros
     * @throws IOException
     * @throws InterruptedException
     */
    public void receive_files(int nfiles, String path) throws IOException, InterruptedException {

        FilesInfo fi = new FilesInfo();
        Thread[] tFiles = new Thread[nfiles];
        
        it.escreveLog("Recebendo ficheiros...\n", true);
        // receive type C
        int i;
        int j;
        for(i = 0; i < nfiles ; i++){
            tFiles[i] = new Thread(new ReceiveThreadFiles(dis,fi,PACKET_SIZE,nfiles,8000+i,it,MAXPACKS, FILE_SEG_SIZE, path)); 
        }
        for (j=0;j<i;j++){
            tFiles[j].start();
        }
        for (j=0;j<i;j++){
            tFiles[j].join();
        }
        fi.printStatistics(it);

    }




    

    
    
}

/** Classe responsável por receber pacotes relacionados ao ficheiro em Threads */
class ReceiveThreadFiles implements Runnable{
    Disassembler dis;
    Interface it;
    String fileName;
    String path;
    FilesInfo filesI;
    int packSize;
    int nfiles;
    int port;
    int maxPacks;
    int fileSegSize;
    InetAddress address;

    /**
     * Construtor
     * @param dis = Disassembler, responsável por desconstruir os pacotes
     * @param fi = FilesInfo, possui informações de todos os ficheiros
     * @param packetSize = tamanho do pacote
     * @param nfiles = número de ficheiro que irão ser recebidos
     * @param port = porta que irá ser utilizada para estabelecer conexão
     * @param i = Interface compartilhada para escrita do log
     * @param maxPacks = o número máximo de pacotes a serem enviados por vez 
     * @param fileSegSize = tamanho do segmento do pacote reservado para o ficheiro
     * @param path = caminho para a pasta 
     */
    public ReceiveThreadFiles(Disassembler dis,FilesInfo fi, int packetSize, int nfiles, int port, Interface i, int maxPacks, int fileSegSize, String path){
        this.dis = dis;
        this.filesI = fi;
        this.packSize = packetSize;
        this.nfiles = nfiles;
        this.port = port;
        this.it = i;
        this.maxPacks = maxPacks;
        this.fileSegSize = fileSegSize;
        this.path = path;
    }

    /**
     * Função que recebe o pacote do tipo C (informações do ficheiro)
     * @param socket = socket usado para a conexão
     * @throws IOException
     * @throws FileNotFoundException
     * @throws InterruptedException
     */
    public void receive_typeC(DatagramSocket socket) throws IOException, FileNotFoundException, InterruptedException{
        byte[] nPacksFile = new byte[packSize];
        DatagramPacket receivedNPacket = new DatagramPacket(nPacksFile, nPacksFile.length);
        socket.receive(receivedNPacket);
        address = receivedNPacket.getAddress();
        int port = receivedNPacket.getPort();
        nPacksFile = receivedNPacket.getData();
        dis.disassemble_header(nPacksFile, it);
        fileName = dis.disassemble_typeC(nPacksFile, filesI,socket,port,address, it,maxPacks,fileSegSize, path);
    }

    /**
     * Função que recebe o pacote do tipo D (pacotes do ficheiro)
     * @param socket = socket usado para a conexão
     */
    public void receive_typeD(DatagramSocket socket){
        int intFlag = -1;  
        int waitSeqNumb = 0;
        int receivedPacks;
        FileInfo fi = filesI.getFileInfo(fileName);
        
        try{
            // receber todos os pacotes do ficheiro
            receivedPacks = 0;
            while (intFlag<=0){
                byte[] receivePack = new byte[packSize];
                DatagramPacket receivedPacket = new DatagramPacket(receivePack,receivePack.length);
                try {
                    socket.setSoTimeout(5000);
                    socket.receive(receivedPacket);
    
                    receivePack = receivedPacket.getData();
                    InetAddress address = receivedPacket.getAddress();
                    int port = receivedPacket.getPort();
                    
                    // receber as informações do pacote
                    dis.disassemble_header(receivePack, it); 
                    intFlag = dis.disassemble_typeD(receivePack,socket, fi,waitSeqNumb,address, port, it); 
                    if(intFlag == 0) waitSeqNumb+=fileSegSize;
                } 
                catch (SocketTimeoutException e) {
                    it.escreveLog("["+fileName+"] Socket timed out waiting for the packet\n",false);
                    byte[]pack = new byte[packSize];
                    dis.sendAckFilePack(pack, fileName, socket, address, port, 'n', waitSeqNumb, it);
                }
                
            }
            it.escreveLog("["+fileName+"] Foi lido todos os pacotes\n", true);
            // escrever última rodada e fechar o ficheiro
            fi.closeFile(it);
        }
        catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
           receive_typeC(socket);
           
           
           receive_typeD(socket); 
           socket.close(); 
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}



