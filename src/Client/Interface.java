package FFSync;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Classe responsável pelos prints na linha de comando , e escrita no ficheiro Log.
 */
public class Interface {
    private FileOutputStream fileOutputStream;
    private Lock l;

    public Interface(String fileName) throws FileNotFoundException{
        this.fileOutputStream = new FileOutputStream(fileName);
        this.l = new ReentrantLock();
    }
    
    /**
     * Faz o print e escrita do comentário no logfile
     * @param comentário a ser printado e escrito no logfile
     * @throws IOException
     */
    public void escreveLog(String s, boolean writeOnTerm) throws IOException{
        l.lock();
        try{
            if(writeOnTerm) System.out.print(s);
            fileOutputStream.write(s.getBytes());
        }
        finally{
            l.unlock();
        }
    }

    /**
     * Faz flush de tudo que foi escrito
     * @throws IOException
     */
    public void flush() throws IOException{
        fileOutputStream.flush();
        fileOutputStream.close();
    }
}
    

