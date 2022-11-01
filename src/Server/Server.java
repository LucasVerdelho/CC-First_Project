package FFSync;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import FFSync.FileInfoTypeB;
import FFSync.Interface;
import FFSync.PackBuilder;


public class Server {
    private static DatagramSocket socket = null;
    private String hostName = "localHost";
    private PackBuilder pb;
    private static Interface it;
    private static int port; 
    private static InetAddress address;

    private Map<String,FileInfoTypeB> mapFicheirosCliente;

    private static int PACKET_SIZE = 1077;
    private static int FILE_SEG_SIZE = 1033;
    private static int MAXPACKS = 5;

    private int ackSequence = 0;
    
    
    /**
     * Construtor da classe para estabelecer as variavéis de instância necessárias
     * @param ip = ip destino da máquina a ser ligada
     */
    public Server(InetAddress ip){
        pb = new PackBuilder();
        port = 8888;
        address = ip;
        mapFicheirosCliente = new HashMap<>();
        try{
            socket = new DatagramSocket(port);
            it = new Interface("logFile.txt");
        }
        catch(SocketException se){
            System.out.println(se.getMessage());
        }
        catch(IOException se){
            System.out.println(se.getMessage());
        }
        
    }
    
    /**
     * Função responsável por enviar o sync e estabelecer conexão com outra máquina
     * @throws Exception
     */
    public void create_connection() throws Exception{
        byte[] pack = new byte[PACKET_SIZE];
        DatagramPacket receivedPacket = new DatagramPacket(pack, pack.length);

        it.escreveLog("Estabelecendo conexão...\n",true);
        socket.receive(receivedPacket);
        pack = receivedPacket.getData();
        pb.disassemble_header(pack,it);

        address = receivedPacket.getAddress();
        port = receivedPacket.getPort();


        pb.sendAck(pack, ackSequence, socket, address, port, it);
        ackSequence++;
    }


    
    
    /**
     * Função principal responsável por enviar os ficheiros 
     * @param files = Lista de ficheiros a serem enviados
     * @throws Exception
     */
    public void send_files(List<File> files) throws Exception{ 
        Thread[] tFiles = new Thread[files.size()];
        FilesInfo fi = new FilesInfo();
        int i = 0;
        int j;
        // Send type C e D
        it.escreveLog("Enviando ficheiros...\n", true);
        for(File f:files){
            tFiles[i] = new Thread(new ThreadFiles(f,FILE_SEG_SIZE,PACKET_SIZE,pb,address,fi,8000+i, it, MAXPACKS)); 
            i++;
        }
        for (j=0;j<i;j++){
            tFiles[j].start();
        }
        for (j=0;j<i;j++){
            tFiles[j].join();
        }
        fi.printStats(it);
        
    }


    /**
     * Receção de informações de data de modificação dos ficheiros do cliente
     * @throws SocketException
     * @throws IOException
     * @throws InterruptedException
     */
    public void receiveClientFiles() throws SocketException, IOException, InterruptedException{
        // Receber quantos ficheiros do cliente serão enviado
        it.escreveLog("A receber informações de ficheiros do cliente a serem comparados...\n",true);
        int nFiles;
        byte[] pack = new byte[PACKET_SIZE];
        nFiles = pb.receive_typeA(pack, socket, ackSequence, it);
        if (nFiles == 0) it.escreveLog("Sem ficheiros para comparar, irá ser enviado tudo...\n", true);
        ackSequence++;

        for(int i = 0; i < nFiles ; i++){
            FileInfoTypeB fileInfo = pb.receive_typeB(pack, socket, ackSequence, it);
            ackSequence++;
            this.mapFicheirosCliente.put(fileInfo.getFileName(), fileInfo);
        }
    }


        


    /**
     * Comparação de ficheiros pastaServer <-> pastaClient e filtrar aqueles que sejam diferentes e iguais com data de modificação maior 
     * @param path = caminho da pasta do Servidor
     * @return Lista de ficheiros filtrado
     */
    public List<File> comparaFiles(String path){
            File directory = new File(path);
            List<File> listaF = new LinkedList<>(Arrays.asList(Objects.requireNonNull(directory.listFiles())));
            Map<String, FileInfoTypeB> serverFiles = new HashMap<>();

            String tempFileN;
            for (File file : listaF) {
                tempFileN = file.getName();
                FileInfoTypeB tempFile = new FileInfoTypeB(tempFileN.length(), tempFileN, file.lastModified());
                serverFiles.put(tempFileN,tempFile);
            }

            for (String s : this.mapFicheirosCliente.keySet()) {
                if(serverFiles.containsKey(s)){
                    if(this.mapFicheirosCliente.get(s).getDataMod() >= serverFiles.get(s).getDataMod())
                        serverFiles.remove(s);
                }
            }
              
            listaF.removeIf(file -> !serverFiles.containsKey(file.getName()));

            return listaF;
        }

    /**
     * Faz o filtro dos ficheiros que irão ser enviados 
     * @param path = caminho da pasta do Servidor
     * @return Lista de ficheiros que irão ser enviados
     * @throws Exception
     */
     public List<File> getFilesToSend(String path) throws Exception {
            if (!(this.mapFicheirosCliente.isEmpty())) it.escreveLog("Comparando data de modificação de ficheiros...\n", true); 
            List<File> listaF = comparaFiles(path);
            int nFiles = listaF.size();

            // Fazer pacote Type A com o numero de pacotes
            byte[] bufferA = new byte[PACKET_SIZE];
            pb.build_typeA(bufferA,nFiles,it);
            
            DatagramPacket sendAPacket = new DatagramPacket(bufferA, bufferA.length, address, port);
            socket.send(sendAPacket);
            
            // Receive ACK
            byte[] ack = new byte[PACKET_SIZE];
            pb.receive_Ack(ack, sendAPacket, socket, ackSequence, it);
            ackSequence++;

            return listaF;
        }

        
    


    /** MAIN */
    public static void main(String args[]) throws Exception{
        
        
        if (args.length == 2){
            InetAddress ip = InetAddress.getByName(args[1]); 
            Server server = new Server(ip);
            server.create_connection();
            server.receiveClientFiles();
            List<File> filesToSend = new ArrayList<>();
            filesToSend = server.getFilesToSend(args[0]);
            server.send_files(filesToSend);
            socket.close();
            it.flush();

        }
        else{
            System.out.println("insira a pasta dos ficheiros e o IP da máquina destino a serem enviados");
        }
        
    }    

}

/** Classe responsável por correr em threads o envio dos ficheiros */
class ThreadFiles implements Runnable{
    String fileName;
    int fileSegSize;
    int packSize;
    int port;
    int maxPacks;
    File file;
    PackBuilder pb;
    InetAddress ip;
    FilesInfo fi;
    Interface it; 

    /**
     *  Construtor
     * @param fileName = nome do ficheiro
     * @param fileSegS = tamanho do segmento do ficheiro no pacote 
     * @param packSize = tamanho do pacote
     * @param p = packBuilder responsável construir os pacotes
     * @param ip = ip pra qual irá ser feito a conexão
     * @param fi = filesInfo onde possui informações acerca do envio do ficheiro
     * @param port = porta a ser utilizada no socket
     * @param it = interface para realizar logs
     * @param maxPacks = o número máximo de pacotes a serem enviados por vez
     */
    public ThreadFiles(File file,  int fileSegS, int packSize, PackBuilder p,InetAddress ip, FilesInfo fi, int port, Interface it, int maxPacks){
        this.fileName = file.getName();
        this.file = file;
        this.fileSegSize = fileSegS;
        this.packSize = packSize;
        this.pb = p;
        this.ip = ip;
        this.fi = fi;
        this.port = port;
        this.it = it;
        this.maxPacks = maxPacks;

    }
    /**
     * Função responsável por enviar a mensagem do tipo C (informações sobre o ficheiro)
     * @param file = ficheiro a ser enviado
     * @param socket = socket utilizado para fazer conexão
     * @throws FileNotFoundException
     * @throws IOException
     * @throws InterruptedException
     */
    public void send_typeC(File file, DatagramSocket socket) throws FileNotFoundException, IOException, InterruptedException{
        // long lastDateMod = file.lastModified();
        char type = 'c';
        DataInputStream diStream = new DataInputStream(new FileInputStream(file));
        int fileLen = (int) file.length();
        byte[] fileBytes = new byte[(int) fileLen];
        int read = 0;
        int numRead = 0;
        
        // Leitura do ficheiro para array de bytes 
        while (read < fileBytes.length && (numRead = diStream.read(fileBytes, read, fileBytes.length - read)) >= 0) {
        read = read + numRead;
        }
        diStream.close();
        
        // Transformar o nome do ficheiro para array de Bytes
        byte[] fileNameBArray = new byte[30];
        byte[] fileNameBytes = fileName.getBytes();
        int sizeFileName = fileNameBytes.length;
        System.arraycopy(fileNameBytes,0,fileNameBArray,0,sizeFileName);
        
        // Calcula número de pacotes 
        int nPacks = fileLen / fileSegSize;
        if (fileLen % fileSegSize != 0) nPacks++;
        
        byte nPackspacket[] = new byte [packSize];
        
        // guardar informações do ficheiro 
        FileInfo fileInf = new FileInfo(sizeFileName,fileNameBArray,fileLen, fileBytes,nPacks,fileName);
        fi.addFileInf(fileInf, fileName);
        
        // constroi o cabeçalho do pacote
        pb.build_header(nPackspacket, type);
        
        // constroi o data do pacote do type C
        pb.build_typeC(nPackspacket, sizeFileName, fileNameBArray, nPacks, fileLen);
        
        // envia o pacote  
        DatagramPacket send2Packet = new DatagramPacket(nPackspacket, nPackspacket.length, ip, port);
        socket.send(send2Packet);
        it.escreveLog("Foi enviado o pacote tipo C do ficheiro " + fileName+"\n",false);
        
        // recebe o ack
        byte[] ack = new byte[packSize];
        pb.receive_Ack(ack, send2Packet, socket, -1, it);
    }

    /**
     * Função responsável por enviar o pacote do tipo D (pacotes do ficheiro) 
     * @param socket = socket utilizado para conexão 
     * @throws IOException
     * @throws SocketException
     * @throws InterruptedException
     */
    public void send_typeD(DatagramSocket socket) throws IOException, SocketException, InterruptedException{
        char type = 'd';
        FileInfo fileInf = fi.getFileInfo(fileName);
        byte[]fileBytes = fileInf.getFileData();
        byte[]fileNameBArray = fileInf.getFileNameBytes();
        int len = fileInf.getFileSize();
        int sizeFileName = fileInf.getSizeFileN();
        
        boolean flag = true;
        int sequence_number = 0;
        int totNpacotesEnv = 0;
        long tempoInicio = System.currentTimeMillis();
        while(flag){
            int npacotesEnv = 0;
            for(; sequence_number < len ; sequence_number += fileSegSize){
                byte packet[] = new byte [packSize];
                pb.build_header(packet, type);
                pb.build_typeD(packet, sequence_number, sizeFileName, fileNameBArray, len, fileBytes, fileSegSize);
                DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, ip, port);
                socket.send(sendPacket);
                it.escreveLog("["+fileName+ "] Foi enviado o pacote numSeq ("+sequence_number+")\n",false);
                npacotesEnv++;
                if (npacotesEnv == maxPacks){
                    byte[] ack = new byte[packSize];
                    int lastCheckPoint = totNpacotesEnv*fileSegSize;
                    int msg = pb.receive_AckFilePacks(ack, socket, it, fileName,maxPacks, lastCheckPoint);
                    if(msg == -1){
                        totNpacotesEnv+=npacotesEnv; // foi recebido os pacotes estabelecidos
                        npacotesEnv = 0;
                    }
                    else if (msg >= 0){ // não foi recebido todos os pacotes previstos
                        npacotesEnv = (msg - (lastCheckPoint))/fileSegSize;
                        sequence_number = msg-fileSegSize;
                    } 
                }   
            }
                    
            byte[] ack = new byte[packSize];
            int lastCheckPoint = totNpacotesEnv*fileSegSize;
            int msg = pb.receive_AckFilePacks(ack, socket, it, fileName,maxPacks, lastCheckPoint);
            if(msg == -1){
                flag = false; // foi recebido o ultimo pacote
                totNpacotesEnv+=npacotesEnv; 
            }
            else if (msg >= 0){ // não foi recebido todos os pacotes previstos
                npacotesEnv = (msg - (lastCheckPoint))/fileSegSize;
                sequence_number = msg;
            }
             
        
        }
        it.escreveLog("Todos pacotes enviados do ficheiro "+ fileName + "\n", true);
        double totalTime = (System.currentTimeMillis()-tempoInicio)/1000.0;
        fileInf.setTimeTransfer(totalTime);
        fileInf.addPackEnviado(totNpacotesEnv);

    }

    public void run(){
        
            try (DatagramSocket socket = new DatagramSocket()) {
                    // envia o pacote do tipo C (informações sobre o ficheiro)
                    send_typeC(file, socket);
                    // envia os pacotes do tipo D (pacotes do ficheiro)
                    send_typeD(socket);

                    socket.close();
            }
            catch (Exception e) {    
                e.printStackTrace();    
            }
        
        
    } 
        
}



            