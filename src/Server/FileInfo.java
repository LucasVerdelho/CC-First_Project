package FFSync;

import java.io.IOException;

import FFSync.Interface;

/**
 * Classe que obtém as informações de um ficheiro
 */
public class FileInfo {
    private int sizeFileName;
    private int fileSize;
    private int nPacks;
    private int packsEnviados;
    private double timeTransfer;
    private String fileName;
    private byte[]fileNameBArray;
    private byte[]fileData;

    /**
     * Construtor de todas as variáveis de instância necessárias
     * @param sizeFileName = tamanho do nome do ficheiro 
     * @param fileName = nome do ficheiro em bytes
     * @param fileSize = tamanho do segmento do ficheiro
     * @param fD = segmento do ficheiro em bytes
     * @param packs = número de packs do ficheiro
     * @param fileN = nome do ficheiro
     */
    public FileInfo(int sizeFileName, byte[]fileName, int fileSize, byte[]fD, int packs, String fileN){
        this.sizeFileName = sizeFileName;
        this.fileSize = fileSize;
        this.nPacks = packs;
        this.fileNameBArray = fileName;
        this.fileData = fD;
        this.fileName = fileN;
    }

    /**
     * 
     * @return número de pacotes enviados
     */
    public int getPacksEnv(){
        return packsEnviados;
    }

    /**
     * 
     * @return número de pacotes do ficheiro
     */
    public int getNPacks(){
        return nPacks;
    }
    /**
     * Faz o set do tempo de transferência
     * @param time = tempo de transferência
     */
    public void setTimeTransfer(double time){
        this.timeTransfer = time;
    }

    /**
     * 
     * @return tempo de transferência
     */
    public double getTime(){
        return this.timeTransfer;
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
     * @return tamanho do nome do ficheiro
     */
    public int getSizeFileN(){
        return sizeFileName;
    }   
    /**
     * 
     * @return tamanho do segmento do ficheiro
     */
    public int getFileSize(){
        return fileSize;
    }
    /**
     * 
     * @return nome do ficheiro em bytes
     */
    public byte[] getFileNameBytes(){
        return fileNameBArray;
    }
    /**
     * 
     * @return segmento do ficheiro em bytes
     */
    public byte[] getFileData(){
        return fileData;
    }

    /**
     * Adiciona número de packs enviados 
     * @param número de packs enviados
     */
    public void addPackEnviado(int n){
        this.packsEnviados = n;
    }

    
}
