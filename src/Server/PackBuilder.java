package FFSync;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import FFSync.Interface;

/** Classe responsável por construir os pacotes e receber acks*/
public class PackBuilder {
    public PackBuilder(){
    }

    /**
     * Desconstrução do cabeçalho do pacote
     * @param pack = pacote a ser desconstruído
     * @param it = interface utilizada para escrita do log
     * @throws IOException
     */
    public void disassemble_header(byte[]pack, Interface it) throws IOException{
        char type = (char)pack[0];
        if(type == 's') it.escreveLog("Connected with client\n", true);
        
    }
    
    /**
     * Receção do pacote do tipo A (número de pacotes a serem recebidos)
     * @param pack = pacote a ser desconstruído
     * @param socket = socker utilizado na conexão
     * @param sequenceNumber = número de sequência global para receção de pacotes
     * @param it = interface utilizada para escrita de  logs 
     * @return número de ficheiros recebidos
     * @throws SocketException
     * @throws IOException
     * @throws InterruptedException
     */
    public int receive_typeA(byte[]pack, DatagramSocket socket, int sequenceNumber, Interface it) throws SocketException, IOException, InterruptedException{
        DatagramPacket dPack = new DatagramPacket(pack, pack.length);
        // Receive packet type A
        socket.receive(dPack);
        pack = dPack.getData();
        char type = (char)pack[0];
        byte[]nFicheiros = new byte[4];
        System.arraycopy(pack, 1, nFicheiros, 0, 4);
        int nFiles = ByteBuffer.wrap(nFicheiros).getInt();
        
        it.escreveLog("Irão ser recebidos "+ nFiles+ " ficheiros\n", false );

        // Send Ack 
        InetAddress address = dPack.getAddress();
        int port = dPack.getPort();
        byte[]ack = new byte[pack.length];
        sendAck(ack, sequenceNumber, socket, address, port, it);
    

        return nFiles;
            
        }
        
    /**
     * Desconstrução do pacote do tipo B (receção de informações do ficheiro relativo a data de modificação)
     * @param pack = pacote a ser desconstruído
     * @param socket = socket utilizado na conexão
     * @param sequenceNumber = número de sequência global para receção de pacotes
     * @param it = interface utilizada para escrita de  logs
     * @return FileInfoTypeB , que contém informações do ficheiro recebido
     * @throws IOException
     * @throws InterruptedException
     */    
    public FileInfoTypeB receive_typeB(byte[]pack, DatagramSocket socket, int sequenceNumber, Interface it) throws IOException, InterruptedException{
        Map<String,FileInfoTypeB> mapFicheirosCliente = new HashMap<>();
        
        DatagramPacket dPack = new DatagramPacket(pack, pack.length);
        socket.receive(dPack);
        pack = dPack.getData();
        
        // Receive packet type B
        char type = (char)pack[0];
        byte[] sizeFileNameBytes = new byte[4];
        System.arraycopy(pack, 1, sizeFileNameBytes, 0, 4);
        int sizeFileName = ByteBuffer.wrap(sizeFileNameBytes).getInt();
        byte[] fileNameBytes = new byte[sizeFileName];
        System.arraycopy(pack, 5, fileNameBytes, 0, sizeFileName);
        String fileName = new String(fileNameBytes);
        byte[] dataModBytes = new byte[8];
        System.arraycopy(pack, 35, dataModBytes, 0, 8);
        long dataMod = ByteBuffer.wrap(dataModBytes).getLong();
        FileInfoTypeB temp = new FileInfoTypeB(sizeFileName, fileName, dataMod);
        
        it.escreveLog("Recebi informações (typeB) do ficheiro "+ fileName+ "\n",false);
        // Send ACK
        InetAddress address = dPack.getAddress();
        int port = dPack.getPort();
        sendAck(pack, sequenceNumber, socket, address, port, it);
        return temp;
    }
    
    
    /**
     * Função responsável por construir o cabeçalho do pacote  
     * @param pack = pacotem em bytes
     * @param type = tipo de mensagem 
     */
    public void build_header(byte[]pack, char type){
        pack[0]=(byte)type; // TYPE
    }

    /**
     * Contrói o pacote do tipo A (enviar número de ficheiros)
     * @param pack = pacote a ser construído
     * @param nFiles = número de ficheiros a serem enviados
     * @param it = interface utilizada para escrita do log
     * @throws Exception
     */
    public void build_typeA(byte[]pack, int nFiles, Interface it) throws Exception{
        build_header(pack, 'a');
        pack[1]=(byte)(nFiles >> 24);   //
        pack[2]=(byte)(nFiles >> 16);   // number of Files
        pack[3]=(byte)(nFiles >> 8);    //
        pack[4]=(byte)nFiles;
        it.escreveLog("Número de ficheiros a serem enviados: "+ nFiles+ "\n", false);
    }
    

    
    /**
     * Responsável por construir o data da mensagem do tipo C
     * @param pack = pacote a ser construído
     * @param sizeFileName = tamanho do nome do ficheiro
     * @param fileNameBArray = nome do ficheiro em bytes
     * @param nPacks = número de pacotes
     * @param fileLen = tamanho do ficheiro
     */
    public void build_typeC(byte[]pack, int sizeFileName, byte[] fileNameBArray, int nPacks, int fileLen){
        pack[1]=(byte)(sizeFileName >> 24);     //
        pack[2]=(byte)(sizeFileName >> 16);   // size filename
        pack[3]=(byte)(sizeFileName >> 8);    //
        pack[4]=(byte)sizeFileName;          //

        System.arraycopy(fileNameBArray, 0, pack, 5, 30); // FileName
            
        pack[35]=(byte)(nPacks >> 24);     //
        pack[36]=(byte)(nPacks >> 16);   // nº de pacotes
        pack[37]=(byte)(nPacks >> 8);    //
        pack[38]=(byte)nPacks;          //

        pack[39]=(byte)(fileLen >> 24);     //
        pack[40]=(byte)(fileLen >> 16);   // nº de pacotes
        pack[41]=(byte)(fileLen >> 8);    //
        pack[42]=(byte)fileLen;          //



    }

    /**
     * Função responsável por construir o data do pacote tipo D
     * @param pack = pacote a ser construído
     * @param sequence_number = número de sequência do pacote
     * @param sizeFileName = tamanho do nome do ficheiro
     * @param fileNameBArray = nome do ficheiro em bytes 
     * @param len = tamanho do ficheiro
     * @param fileBytes = segmento do ficheiro em bytes
     * @param fileSegSize = tamanho do segmento do ficheiro
     */
    public void build_typeD(byte[]pack, int sequence_number, int sizeFileName, byte[]fileNameBArray, 
    int len, byte[] fileBytes, int fileSegSize){
        boolean lastPackflag = false;
        
        pack[1]=(byte)(sequence_number >> 24);     //
        pack[2]=(byte)(sequence_number >> 16);    // Nº SEQ
        pack[3]=(byte)(sequence_number >> 8);    //
        pack[4]=(byte)sequence_number;          //

        pack[5]=(byte)(sizeFileName >> 24);     //
        pack[6]=(byte)(sizeFileName >> 16);    // size filename
        pack[7]=(byte)(sizeFileName >> 8);    //
        pack[8]=(byte)sizeFileName;          //

        System.arraycopy(fileNameBArray, 0, pack, 9, 30); // FileName

        if ((sequence_number + fileSegSize) >= len) lastPackflag = true;
        if (!lastPackflag) {
            pack[39]= (byte)'n';
            pack[40]=(byte)(fileSegSize >> 24);     //
            pack[41]=(byte)(fileSegSize >> 16);    // data size
            pack[42]=(byte)(fileSegSize >> 8);    //
            pack[43]=(byte)fileSegSize;          //
            System.arraycopy(fileBytes, sequence_number, pack, 44, fileSegSize);
        } else { // If it is the last message
            int finalSize = len - sequence_number;
            pack[39] = (byte)'y';
            pack[40]=(byte)(finalSize >> 24);     //
            pack[41]=(byte)(finalSize >> 16);    // Nº SEQ
            pack[42]=(byte)(finalSize >> 8);    //
            pack[43]=(byte)finalSize;          //
                    
            System.arraycopy(fileBytes, sequence_number, pack, 44, finalSize);
        }
    }

    /**
     * 
     * @param ack = pacote ack do tipo 'h' (recebimento dos pacotes de ficheiro) a ser recebido
     * @param socket = socket a ser utilizado para a conexão 
     * @param it = interface para realizar logs
     * @param fileName = nome do ficheiro 
     * @param maxPacks = o número máximo de pacotes a serem enviados por vez
     * @return (-1) houve receção de todos os pacotes, (>=0) número de sequência a ser reenviado
     * @throws SocketException
     * @throws IOException
     */
    public int receive_AckFilePacks(byte [] ack,  DatagramSocket socket, Interface it, String fileName, int maxPacks, int SeqNumbCheckPoint) throws SocketException, IOException{
        boolean ackRec = true;
        int toReturn = -1;
            DatagramPacket ackpack = new DatagramPacket(ack, ack.length);
            int ackSequence = -1;
            try {
                // set the socket timeout for the packet acknowledgment
                socket.setSoTimeout(5000);
                socket.receive(ackpack);
                ack = ackpack.getData();
                char type = (char)ack[0];
                char receiveAll = (char)ack[1];
                if(receiveAll == 'n'){
                    byte[]seqNumbBytes = new byte[4];
                    System.arraycopy(ack, 2, seqNumbBytes, 0, 4);
                    ackSequence = ByteBuffer.wrap(seqNumbBytes).getInt();
                    toReturn = ackSequence;
                    it.escreveLog("["+fileName+"] ACK("+toReturn+"): houve erro é preciso reenviar pacotes a partir do NumSeq("+toReturn+")\n",false);
                }
                else if(receiveAll == 'o'){
                    it.escreveLog("["+fileName+"] ACK: os "+maxPacks+" pacotes foram enviados e lidos com sucesso\n",false);
                }
                else{
                    it.escreveLog("["+fileName+"] ACK: todos os pacotes foram enviados e lidos com sucesso\n",false);
                }
                
                
            }
            // we did not receive an ack
            catch (SocketTimeoutException e) {
                it.escreveLog("["+fileName+"] Socket timed out waiting for the ack\n",false);
                toReturn = SeqNumbCheckPoint; // se esgotar o tempo reenvia os pacotes a partir da última rodada de pacotes que foram recebidos 
            }
            
        return toReturn;
    }

    /**
     * Desconstroi o pacote ack do tipo G
     * @param ack = pacote a ser desconstruído
     * @return nome do ficheiro
     * @throws IOException
     */
    public String receive_AckFile(byte[] ack) throws IOException{
        byte[] sizeFileNameBytes = new byte[4];
        System.arraycopy(ack, 1, sizeFileNameBytes, 0, 4);
        int sizeFileName = ByteBuffer.wrap(sizeFileNameBytes).getInt(); 
        byte[] fileNameBytes = new byte[sizeFileName];
        System.arraycopy(ack, 5, fileNameBytes, 0, sizeFileName);
        String fileName = new String(fileNameBytes);
        
        return fileName;
    }

    /**
     * Função responsável por receber um Ack do tipo 'f' (acks gerais) e do tipo 'g'(acks de informação do ficheiro)
     * @param ack = pacote ack a ser recebido
     * @param sendPacket = pacote para reenvio caso aconteça erros
     * @param socket = socket utilizado para conexão
     * @param sequenceNumber = número de sequencia do pacote
     * @param it = interface para realizar logs
     * @throws SocketException
     * @throws IOException
     */
    public void receive_Ack(byte[]ack, DatagramPacket sendPacket, DatagramSocket socket, int sequenceNumber, Interface it) throws SocketException, IOException, InterruptedException{
        boolean ackRec = false;
        boolean flag = true;
        while (flag) {
            Boolean isAckFile = false;
            String fileName = null;
            DatagramPacket ackpack = new DatagramPacket(ack, ack.length);
            int ackSequence = -1;
            try {
                // set the socket timeout for the packet acknowledgment
                socket.setSoTimeout(1000);
                socket.receive(ackpack);
                ack = ackpack.getData();
                char type = (char)ack[0];
                if (type == 'g'){
                    fileName = receive_AckFile(ack);
                    isAckFile = true;
                }
                else{
                    byte[]seqNumbBytes = new byte[4];
                    System.arraycopy(ack, 1, seqNumbBytes, 0, 4);
                    ackSequence = ByteBuffer.wrap(seqNumbBytes).getInt();
                }
                ackRec = true;
            }
            // we did not receive an ack
            catch (SocketTimeoutException e) {
                it.escreveLog("Socket timed out waiting for the ack\n",false);
                ackRec = false;
            }

            // everything is ok so we can move on to next packet
            // Break if there is an acknowledgment next packet can be sent
            if ((ackSequence == sequenceNumber) && (ackRec)) {
                String msg = (isAckFile)? fileName : String.valueOf(ackSequence);
                it.escreveLog("Ack ("+msg+") received\n",false);
                flag = false;
            }
            else{
                // Re send the packet
                it.escreveLog("Estou a espera do ("+sequenceNumber+"), e recebi ("+ackSequence+")\n",false);
                socket.send(sendPacket);
                it.escreveLog("Resending packet...\n",false);
            }
        }
    }

    public void sendAck(byte[]pack, int seqNumber, DatagramSocket s, InetAddress address, int port,Interface it)
    throws IOException, InterruptedException{
        pack[0]=(byte)'f'; // TYPE
                    
        pack[1]=(byte)(seqNumber >> 24);     //
        pack[2]=(byte)(seqNumber >> 16);   //  SEQ NUMBER
        pack[3]=(byte)(seqNumber >> 8);    //
        pack[4]=(byte)seqNumber;          //

        DatagramPacket acknowledgement = new DatagramPacket(pack,pack.length, address, port);
        s.send(acknowledgement);
        it.escreveLog("Sending ack("+seqNumber+")...\n",false);
    }
}
