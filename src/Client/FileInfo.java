package FFSync;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Classe detentora das informações do ficheiro 
 */
public class FileInfo {
    private int nPacks;
    private int qntsFaltam;
    private int sizeFileBuf;
    private int lastSizeFileBuf;
    private int totalPacksLidos;
    private int roundPacksLidos;
    private int len;
    private int maxRoundPacks;
    private String fileName;
    private FileOutputStream fileOutputStream;
    private Lock l;
    private int lastIndexWtritten;
    private byte[] fileBuf;
    
    /**
     * Construtor 
     * @param packs = número de pacotes do ficheiro
     * @param fileName = nome do ficheiro
     * @param fileLen = tamanho do ficheiro
     * @throws FileNotFoundException
     */
    public FileInfo(int packs, String fileName, int fileLen, int maxPacks, int sizefileSeg) throws FileNotFoundException{
        this.nPacks = packs;
        this.roundPacksLidos = 0;
        this.totalPacksLidos = 0;
        this.fileName = fileName;
        this.len = fileLen;
        this.maxRoundPacks = maxPacks;
        
        int i = packs / maxPacks;
        this.qntsFaltam = i; 
        if (i == 0) {
            this.qntsFaltam++;
            this.lastSizeFileBuf = fileLen; 

        }
        else{
            this.sizeFileBuf = sizefileSeg * maxPacks;
            this.lastSizeFileBuf = sizeFileBuf;
            int resto = packs % maxPacks;
            if ( resto != 0){
                lastSizeFileBuf = len - (maxPacks * qntsFaltam * sizefileSeg);
                this.qntsFaltam++;
            }

                
        }   
        this.fileOutputStream = new FileOutputStream(fileName);
        this.l = new ReentrantLock();

    }

    /**
     * 
     * @return nome do ficheiro
     */
    public String getFileName(){
        return this.fileName;
    }

    /**
     * 
     * @return último índice do buffer do ficheiro escrito
     */
    public int getLastIndex(){
        return lastIndexWtritten;
    }

    /**
     * acrescenta o índice do buffer do ficheiro escrito
     */
    public void updateLastIndex(int i){
        lastIndexWtritten += i;
    }

    /**
     * Escrita no buffer o segmento do ficheiro
     * @param fileSeg = segmento do ficheiro a ser escrito
     * @return (true) se já houver sido lido o número de pacotes máximo estabelecido por rodada, (false) caso contrário
     */
    public boolean writeOnFileBuf(byte[]fileSeg) throws IOException{
        boolean flag = false;
        if(roundPacksLidos == 0){
            fileBuf = (qntsFaltam == 1)? new byte[lastSizeFileBuf] : new byte [sizeFileBuf];
            lastIndexWtritten = 0;
        } 
        int tam = fileSeg.length;
        System.arraycopy(fileSeg, 0, fileBuf, lastIndexWtritten, tam);
        this.updateLastIndex(tam);
        this.roundPacksLidos++;
        this.totalPacksLidos++;
        if(roundPacksLidos == maxRoundPacks){
            flushFileBuf();
            qntsFaltam--;
            roundPacksLidos = 0;
            flag = true;
        }    
        
        return flag;
    }

    /**
     * 
     * @return número de pacotes
     */
    public int getNpacks(){
        return nPacks;
    }

    /**
     * 
     * @return filOutputStream do ficheiro
     */
    public FileOutputStream getFileOutput(){
        l.lock();
        try{
            return fileOutputStream;
        }
        finally{l.unlock();}
    }
    

    /**
     * Escrita do que está no buffer no ficheiro
     * @throws IOException
     */
    public void flushFileBuf() throws IOException{
            fileOutputStream.write(fileBuf);
            fileOutputStream.flush();
                 
    }
    /**
     * Close do ficheiro
     * @param it = interface para realizar logs 
     * @throws IOException
     */
    public void closeFile(Interface it) throws IOException{
        fileOutputStream.write(fileBuf);
        fileOutputStream.flush();
        fileOutputStream.close();
        it.escreveLog("Output file : " + fileName + " is successfully saved\n",true);
    }
    
    /**
     * @param it = interface para realizar logs
     * Printa informações do ficheiro
     */
    public void printPacks(Interface it) throws IOException{
        it.escreveLog("Foram lidos e escritos "+ totalPacksLidos + "/" + nPacks + " pacotes do ficheiro "+ fileName+ " de "+len+" bytes\n",false);
    }
}
