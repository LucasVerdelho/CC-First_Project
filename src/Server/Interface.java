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
     * @param s = comentário a ser printado e escrito no logfile
     * @param writeOnTerm = Booleano que indica se o comentário deverá ser printado na linha de comando 
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
     * Print das estatísticas finais da transferência de ficheiro
     * @param fileLen = tamanho do ficheiro
     * @param fileName = nome do ficheiro
     * @param tempo = tempo de transferência
     * @param packsEnviados = pacotes que foram enviados
     * @param nPacks = número de pacotes totais do ficheiro
     * @throws Exception
     */
    public void printFinalStatistics(int fileLen, String fileName,  Double tempo, int packsEnviados, int nPacks) throws Exception{

        double fileSizeKB = fileLen / 1024;
        double fileSizeMB = fileSizeKB / 1000;
        double throughput = fileSizeMB / tempo;

        StringBuilder sb = new StringBuilder();
        sb.append("O ficheiro " + fileName + " foi enviado\n");
        sb.append("Estatísticas da transferência:\n");
        sb.append("O tamanho do ficheiro " + String.valueOf(fileSizeKB) + " KB\n");
        sb.append("Aproximadamente: " + String.valueOf(fileSizeMB) + " MB\n");
        sb.append("Número de pacotes enviados: " + packsEnviados + "/" + nPacks+ "\n");
        sb.append("Tempo de tranferência: " + tempo + " sec\n");
        System.out.printf("A taxa de transferência por sec foi de: %.2f MBs\n", +throughput);

        this.escreveLog(sb.toString(),true);
        
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
    
