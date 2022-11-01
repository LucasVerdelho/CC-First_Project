package FFSync;

import java.io.IOException;

import FFSync.Interface;

/**
 * Classe que obtém as informações de um ficheiro
 */
public class FileInfoTypeB {
    private int sizeFileName;
    private String fileName;
    private long dataMod;

    /**
     * Construtor para inicialização das variáveis necessárias
     * @param sizeFileN = tamanho do nome do ficheiro
     * @param fileN = nome do ficheiro
     * @param dataModificada = data de modificação do ficheiro
     */
    public FileInfoTypeB(int sizeFileN,String fileN, long dataModificada){
        this.sizeFileName = sizeFileN;
        this.fileName = fileN;
        this.dataMod = dataModificada;
    }

    /**
     * 
     * @return tamanho do nome do ficheiro
     */
    public int getSizeFileName() {
        return sizeFileName;
    }

    /**
     * 
     * @return nome do ficheiro 
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 
     * @return data de modificação 
     */
    public long getDataMod() {
        return dataMod;
    }

}
