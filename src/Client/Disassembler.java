package FFSync;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import FFSync.FilesInfo;
import FFSync.Interface;

/** 
 * Classe responsável por desempacotar pacotes e enviar Acks de recebimento
 * 
 */
public class Disassembler {
    public Disassembler(){
    }

    /**
     * Constrói o cabeçalho
     * @param pack = pacote a ser construído
     * @param type = tipo do mensagem a ser enviada
     */
    public void build_header(byte[]pack, char type){
        pack[0]=(byte)type; // TYPE
    }


    /**
     * Contrói o pacote do tipo A (enviar número de ficheiros)
     * @param pack = pacote a ser construído
     * @param nFiles = número de ficheiros 
     * @param it = interface utilizada para escrita do log
     * @throws Exception
     */
    public void build_typeA(byte[]pack, int nFiles, Interface it) throws Exception{
        build_header(pack, 'a');
        pack[1]=(byte)(nFiles >> 24);   //
        pack[2]=(byte)(nFiles >> 16);   // number of Files
        pack[3]=(byte)(nFiles >> 8);    //
        pack[4]=(byte)nFiles;
    
        it.escreveLog("Número de ficheiros a serem comparados: "+ nFiles+"\n",false);
    
    }


    /**
     * Constrói o pacote do tipo B (envia o nome do ficheiro e a correspondente data de modificação)
     * @param pack = pacote a ser cosntruído
     * @param sizeFileName = tamanho do nome do ficheiro
     * @param fileNameBArray = nome do ficheiro em bytes
     * @param dataMod = data de modificação do ficheiro
     * @param fileName = nome do ficheiro
     * @param it = interface utilizada para escrita do log 
     * @throws Exception
     */
    public void build_typeB(byte[]pack, int sizeFileName, byte[] fileNameBArray, long dataMod, String fileName, Interface it) throws Exception{
        pack[1]=(byte)(sizeFileName >> 24);     //
        pack[2]=(byte)(sizeFileName >> 16);   // size filename
        pack[3]=(byte)(sizeFileName >> 8);    //
        pack[4]=(byte)sizeFileName;

        System.arraycopy(fileNameBArray, 0, pack, 5, sizeFileName); // FileName

        pack[35]=(byte)(dataMod >> 56);  //
        pack[36]=(byte)(dataMod >> 48);  //
        pack[37]=(byte)(dataMod >> 40);  //
        pack[38]=(byte)(dataMod >> 32);  // data
        pack[39]=(byte)(dataMod >> 24);  // modificada
        pack[40]=(byte)(dataMod >> 16);  //
        pack[41]=(byte)(dataMod >> 8);   //
        pack[42]=(byte)dataMod;          //

        it.escreveLog("Enviando informações do tipo B do ficheiro: "+ fileName+"\n", false);
    }



    /**
     * Função que desempacota o cabeçalho do pacote
     * @param pack = pacote a ser desempacotado
     * @param it = interface para realizar logs
     */
    public void disassemble_header(byte[]pack, Interface it) throws IOException{
        char type = (char)pack[0];
        if(type == 's') it.escreveLog("Connected with server\n",true);
        
    }

    /**
     * Receção do pacote do tipo A (número de pacotes a serem recebidos)
     * @param pack = pacote a ser desconstruído do tipo A
     * @param socket = socket utilizado para conexão
     * @param sequenceNumber = número de sequência global do receção dos pacotes
     * @param it = interface utilizada para fazer escrita do log
     * @return número de ficheiros
     * @throws SocketException
     * @throws IOException
     */
    public int disassemble_typeA(byte[]pack, DatagramSocket socket, int sequenceNumber, Interface it) throws SocketException, IOException{
        DatagramPacket dPack = new DatagramPacket(pack, pack.length);
        // Receive packet type A
        socket.receive(dPack);
        pack = dPack.getData();
        char type = (char)pack[0];
        byte[]nFicheiros = new byte[4];
        System.arraycopy(pack, 1, nFicheiros, 0, 4);
        int nFiles = ByteBuffer.wrap(nFicheiros).getInt();

        it.escreveLog("Irão ser recebidos "+ nFiles+ " ficheiros\n",false);

        // Send Ack 
        InetAddress address = dPack.getAddress();
        int port = dPack.getPort();
        sendAck(pack, sequenceNumber, socket, address, port, it);
        return nFiles;
            
    } 


    /**
     * Função que desempacota o data do pacote do tipo C (informações do ficheiro) e envia ack de confirmação
     * @param pack = pacote a ser desempacotado
     * @param fi = FilesInfo, possui as informações de todos os ficheiros
     * @param socket = socket utilizado para conexão
     * @param port = porta utilizada para conexão
     * @param address = endereço ip do servidor
     * @param it = interface para realizar logs
     * @param path = diretoria para onde o ficheiro irá ser criado 
     * @return nome do ficheiro
     * @throws FileNotFoundException
     * @throws IOException
     * @throws InterruptedException
     */
    public String disassemble_typeC(byte[]pack, FilesInfo fi, DatagramSocket socket, int port,InetAddress address, Interface it, int maxPacks, int sizefileSeg, String path) throws FileNotFoundException, IOException, InterruptedException{
        byte[] sizeFileNameBytes = new byte[4];
        System.arraycopy(pack, 1, sizeFileNameBytes, 0, 4);
        int sizeFileName = ByteBuffer.wrap(sizeFileNameBytes).getInt(); 
        byte[] fileNameBytes = new byte[sizeFileName];
        System.arraycopy(pack, 5, fileNameBytes, 0, sizeFileName);
        String fileName = new String(fileNameBytes);
        

        byte[] npacksBytes = new byte[4];
        System.arraycopy(pack, 35, npacksBytes, 0, 4);
        int nFilePacks = ByteBuffer.wrap(npacksBytes).getInt();
        it.escreveLog("Número de pacotes do ficheiro ("+fileName+") : " + nFilePacks+"\n", false);

        byte[] fileLenB = new byte[4];
        System.arraycopy(pack, 39, fileLenB, 0, 4);
        int  fileLen = ByteBuffer.wrap(fileLenB).getInt();
        
        
        fi.create_fileInf(fileName,path, nFilePacks, fileLen,maxPacks, sizefileSeg);

        int packLen = pack.length;
        pack = new byte[packLen];

        this.sendAckFileInf(pack, sizeFileNameBytes, fileNameBytes, fileName, socket, address, port,it);

        return fileName;
    }

    /**
     * Função que desempacota o data do pacote do tipo D (pacotes do ficheiro) e envia ack de confirmação caso tenha recebido tudo ou está a espera de um pacote específico
     * @param receivePack = pacote a ser desempacotado
     * @param socket = socket utilizado para conexão
     * @param fi = informações do ficheiro 
     * @param waitSeqNumb = número de sequência de pacote que está a espera para receber 
     * @param address = endereço ip do servidor
     * @param port = porta a ser utilizada da conexão
     * @param it = interface para realizar logs
     * @return (1) caso tenha recebido todos os pacotes, (-1) caso não tenha sido recebido todos os pacotes, (0) caso tenha recebido o pacote com sucesso
     * @throws IOException
     * @throws InterruptedException
     */
    public int disassemble_typeD(byte[]receivePack,DatagramSocket socket, FileInfo fi,int waitSeqNumb, InetAddress address, int port, Interface it) throws IOException, InterruptedException{
        int flag = -1;
        
                
        byte[] nSeqBytes = new byte[4];
        System.arraycopy(receivePack, 1, nSeqBytes, 0, 4);
        int sequence_number = ByteBuffer.wrap(nSeqBytes).getInt(); 
        
        byte[] sizeFileNameBytes = new byte[4];
        System.arraycopy(receivePack, 5, sizeFileNameBytes, 0, 4);
        int sizeFileName = ByteBuffer.wrap(sizeFileNameBytes).getInt(); 
        byte[] fileNameBytes = new byte[sizeFileName];
        System.arraycopy(receivePack, 9, fileNameBytes, 0, sizeFileName);
        String fileName = new String(fileNameBytes);
        
        boolean lastPack;
        char lPackByte = (char)receivePack[39];
        lastPack = (lPackByte == 'y')? true : false;
        
        byte[] fileSizeBytes = new byte[4];
        System.arraycopy(receivePack, 40, fileSizeBytes, 0, 4);
        int fileSize = ByteBuffer.wrap(fileSizeBytes).getInt();
        //System.out.println("Tamanho do segmento data do file : " + fileSize);                
        byte[] fileSegment = new byte[fileSize];
        System.arraycopy(receivePack, 44, fileSegment, 0, fileSize);
        
        // caso tenha recebido um pacote != daquele que está aguardando
        if (waitSeqNumb != sequence_number){
            it.escreveLog("Estou a espera do ("+waitSeqNumb+"), e recebi ("+sequence_number+")\n", false);
            byte[]pack = new byte[receivePack.length];    
            sendAckFilePack(pack, fileName, socket, address, port, 'n', waitSeqNumb,it);
            //Thread.sleep(1000);
        }
        else{
            flag = 0;
            it.escreveLog("["+fileName+"] Recebi com sucesso número de sequência do pacote : " + sequence_number+"\n", false);
            boolean acabouRound = fi.writeOnFileBuf(fileSegment);
            if(acabouRound){
                byte[]pack = new byte[receivePack.length];
                sendAckFilePack(pack, fileName, socket, address, port, 'o', waitSeqNumb,it);
            }
            if(lastPack){ // recebeu todos os pacotes dos ficheiros 
                flag = 1;
                byte[]pack = new byte[receivePack.length];
                sendAckFilePack(pack, fileName, socket, address, port, 'y', waitSeqNumb,it);
            }    
        }  
        return flag;

    }


    /**
     * Envio de ACK do tipo 'h' (confirma receção de todos os pacotes do ficheiro ou pendência de pacotes a serem recebidos)
     * @param pack = pacote ACK a ser enviado 
     * @param fileName = nome do ficheiro
     * @param socket = socket utilizado na conexão
     * @param address = endereço ip do servidor 
     * @param port = porta utilizada na conexão 
     * @param recebeu = ('y') se recebeu todos os pacotes, ('n') caso não tenha recebido tudo, ('o') recebeu os pacotes da rodada
     * @param seqNumber = número de sequência pendente para recebimento
     * @param it = interface para realizar logs
     * @throws IOException
     * @throws InterruptedException
     */
    public void sendAckFilePack(byte[] pack,String fileName,DatagramSocket socket,InetAddress address, int port, char recebeu, int seqNumber,Interface it) throws IOException, InterruptedException{
        pack[0]=(byte)'h'; // TYPE
        pack[1]=(byte)recebeu;
        if (recebeu == 'n'){
            pack[2]=(byte)(seqNumber >> 24);   //
            pack[3]=(byte)(seqNumber >> 8);    //ID
            pack[4]=(byte)(seqNumber >> 16);   //
            pack[5]=(byte)seqNumber; 
            it.escreveLog("Sending ack("+fileName+") trying to receive seqNumber ("+seqNumber+")...\n",false);
        }
        else if(recebeu == 'o') it.escreveLog("Sending ack("+fileName+") received all round packs...\n",false);
        else it.escreveLog("Sending ack("+fileName+") received all packs...\n",false);
        DatagramPacket acknowledgement = new DatagramPacket(pack,pack.length, address, port);
        socket.send(acknowledgement);
        //Thread.sleep(1);

    }

    /**
     * Envio de ACK do tipo 'f' (confirma receção de um pacote geral)
     * @param pack = pacote ACK a ser enviado
     * @param seqNumber = número sequência do ACK
     * @param s = socket a ser utilizado 
     * @param address = endereço ip do servidor
     * @param port = porta utilizada na conexão
     * @param it = interface para realizar logs
     * @throws IOException
     */
    public void sendAck(byte[]pack, int seqNumber, DatagramSocket s, InetAddress address, int port,Interface it)
    throws IOException{
        pack[0]=(byte)'f'; // TYPE
                    
        pack[1]=(byte)(seqNumber >> 24);     //
        pack[2]=(byte)(seqNumber >> 16);   //  SEQ NUMBER
        pack[3]=(byte)(seqNumber >> 8);    //
        pack[4]=(byte)seqNumber;          //

        DatagramPacket acknowledgement = new DatagramPacket(pack,pack.length, address, port);
        s.send(acknowledgement);
        it.escreveLog("Sending ack("+seqNumber+")...\n",false);
    }

    /**
     * Envio de ACK do tipo 'g' (confirma receçao do pacote acerca das informações do ficheiro)
     * @param pack = pacote ACK a ser enviado
     * @param sizeFileNameBytes = tamanho do nome do ficheiro em bytes
     * @param fileNameBytes = nome do ficheiro em bytes
     * @param fileName = nome do ficheiro
     * @param socket = socket utilizado 
     * @param address = endereço ip do servidor 
     * @param port = porta utilizada na conexão 
     * @param it = interface para realizar logs
     * @throws IOException
     * @throws InterruptedException
     */
    public void sendAckFileInf(byte[] pack,byte[] sizeFileNameBytes, byte[] fileNameBytes, String fileName,DatagramSocket socket,InetAddress address, int port, Interface it) throws IOException, InterruptedException{
        pack[0]=(byte)'g'; // TYPE
                    
        System.arraycopy(sizeFileNameBytes, 0, pack, 1, 4);
        System.arraycopy(fileNameBytes, 0, pack, 5, fileNameBytes.length);
        DatagramPacket acknowledgement = new DatagramPacket(pack,pack.length, address, port);
        socket.send(acknowledgement);
        it.escreveLog("Sending ack("+fileName+")...\n",false);

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
                
                
                byte[]seqNumbBytes = new byte[4];
                System.arraycopy(ack, 1, seqNumbBytes, 0, 4);
                ackSequence = ByteBuffer.wrap(seqNumbBytes).getInt();
                
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
}
