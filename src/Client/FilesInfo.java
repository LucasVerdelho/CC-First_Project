package FFSync;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Classe responsável por guardar todas as informações de todos os ficheiros
 */
public class FilesInfo {
    private Map<String,FileInfo> filesInf;
    private Lock l;


    public FilesInfo(){
        this.filesInf = new HashMap<>();
        this.l = new ReentrantLock();
    }

    /**
     * Cria um FileInfo e acrescenta ao map
     * @param fileName = nome do ficheiro
     * @param nPacks = número de pacotes
     * @param len = tamanho do ficheiro
     * @throws FileNotFoundException
     */
    public void create_fileInf(String fileName,String path, int nPacks, int len, int maxPacks, int sizefileSeg) throws FileNotFoundException{
        l.lock();
        try{
            FileInfo fi = new FileInfo(nPacks,(path+fileName), len, maxPacks, sizefileSeg);
            this.filesInf.put(fileName, fi);
        }
        finally{
            l.unlock();
        }
    }

    /**
     * 
     * @param nome do ficheiro
     * @return FileInfo do ficheiro especificado
     */
    public FileInfo getFileInfo(String fileName){
        l.lock();
        try{
            return filesInf.get(fileName);
        }
        finally{
            l.unlock();
        }
    }


    /**
     * Printa estatísticas sobre cada ficheiro
     * @param it = interface para realizar logs
     */
    public void printStatistics(Interface it) throws IOException{
        l.lock();
        try{
            for(FileInfo f:filesInf.values()){
                f.printPacks(it);
            }
        }
        finally{
            l.unlock();
        }
    }
}
