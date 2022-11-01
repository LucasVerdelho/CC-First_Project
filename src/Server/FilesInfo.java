package FFSync;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import FFSync.FileInfo;
import FFSync.Interface;

/** Classe responsável por guardar todas as informações de todos os ficheiro */
public class FilesInfo {
    private Lock l;
    private Map<String,FileInfo> filesInf;
    
    public FilesInfo(){
        this.filesInf = new HashMap<>();
        this.l = new ReentrantLock();

    }

    /**
     * Função que adiciona no map informações de um ficheiro 
     * @param fi = FileINfo classe que possui informações de um ficheiro
     * @param fileName = nome do ficheiro
     */
    public void addFileInf(FileInfo fi, String fileName){
        l.lock();
        try{
            filesInf.put(fileName, fi);
        }
        finally{
            l.unlock();
        }
    }
    
    /**
     * Função que retorna as informações de um ficheiro especificado
     * @param fileN = nome do ficheiro 
     * @return FileInfo classe que possui informações de um ficheiro
     */
    public FileInfo getFileInfo(String fileN){
        l.lock();
        try{

            return filesInf.get(fileN);
        }
        finally{
            l.unlock();
            
        }
    }

    /**
     * Print das informações dos ficheiros
     * @param it = interface para realizar logs
     */
    public void printStats(Interface it) throws Exception{        
        for(FileInfo fileInf : filesInf.values()){
                it.printFinalStatistics(fileInf.getFileSize(),  fileInf.getFileName(), fileInf.getTime() , fileInf.getPacksEnv(), fileInf.getNPacks());
        }
        
        
    }

}
