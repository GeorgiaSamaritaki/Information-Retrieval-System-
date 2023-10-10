package XMLReader;

import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;
import java.io.*;
import static java.lang.Math.log;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.Vector;

public class XMLReader {

    private static String removePunctuation(String word) {
        return word.replaceAll("[^a-zA-Z]", "");
    }

    private static boolean DNAcheck(String word) {
        if (word.matches("[atgc]+")) {
            return true;
        } else if (word.matches("[augc]+")) {
            return true;
        } else if ( word.startsWith("aaa")){
            return true;
        }
        return false;
    }
    

    static HashSet<String> stopwords; //keep it for sure
    static ArrayList<String> tags; //maybe out 
    static TreeMap<String, Integer> words_per_file; //keep it probably
    static TreeMap<String, Word> words;
    static Queue<String> vocabs;
    static Queue<String> postings;
    static Queue<String> document_f;
    static RandomAccessFile tmp_doc;
    private int default_threshold = 12000;
    private int threshold = default_threshold;
    private long doc_offset = 0;
    private int mergecounter = 0;
    private int vocab_cnt = 0;
    private int totalFiles = 0;
    private TreeMap<String, Long> doc_tf_offsets;
    Path start;

    XMLReader(String path) throws IOException {
        stopwords = this.get_stopwords();
        tags = new ArrayList<>();
        words_per_file = new TreeMap<>();
        words = new TreeMap<>();
        vocabs = new LinkedList<>();
        postings = new LinkedList<>();
        document_f = new LinkedList<>();
        doc_tf_offsets = new TreeMap<>();

        File f = new File(".\\CollectionIndex");
        f.mkdir();

        tmp_doc = new RandomAccessFile(".\\CollectionIndex\\TmpDoc.txt", "rw");

        tags.add("title");
        tags.add("abstract");
        tags.add("body");
        tags.add("journal");
        tags.add("publisher");
        tags.add("authors");
        tags.add("categories");

        start = Paths.get(path);

        Stemmer.Initialize();
    }

    public void createIndex() throws IOException {

        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                thisfunction(file.toString());
                threshold--;
                if (threshold == 0) {
                    makePartials();
                    words.clear();
                    threshold = default_threshold;
                    doc_tf_offsets.clear();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (e == null) {
                    return FileVisitResult.CONTINUE;
                } else {
                    throw e;
                }
            }
        });

        if (!words.isEmpty()) {
            makePartials();
            words.clear();
        }
        System.out.println("Telos index");

        mergeVocabs();
        fixOffsets();
    }

    private static double log2(double num) {
        return log(num) / log(2);
    }

    private static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private void fixOffsets() throws FileNotFoundException, IOException {
        ArrayList<Integer> dfs = new ArrayList<>();

        try {
            BufferedReader documents = new BufferedReader(new FileReader(".\\CollectionIndex\\DocumentFile.txt"));
            BufferedReader vocab = new BufferedReader(new FileReader(".\\CollectionIndex\\VocabularyFile.txt"));

            String s = "";
            while ((s = vocab.readLine()) != null) {
                String[] line = s.split("\\s+");
                int df = Integer.valueOf(line[1]);
                dfs.add(df);
            }
            vocab.close();
            System.out.println("end of vocabs");

            while ((s = documents.readLine()) != null) {
                String[] line = s.split("\\s+");
                long offset = Long.valueOf(line[0]);
                double norm = 0;
                for (int i = 1; i < line.length; i+=2) {
                    
                    double tf = Double.valueOf(line[i]);
                    double idf = log2((double) totalFiles / dfs.get( Integer.valueOf(line[i+1])));
                    norm += (tf * idf) * (tf * idf);
                }
                norm = Math.sqrt(norm);
                tmp_doc.seek(offset);
                if (norm < 10.0) {
                    tmp_doc.writeBytes(" " + round(norm, 2));
                } else {
                    tmp_doc.writeBytes("" + round(norm, 2));
                }

            }
            documents.close();
            tmp_doc.close();
            Files.deleteIfExists(Paths.get(".\\CollectionIndex\\DocumentFile.txt"));

            File file = new File(".\\CollectionIndex\\TmpDoc.txt");
            File file2 = new File(".\\CollectionIndex\\DocumentFile.txt");
            file.renameTo(file2);
        } catch (IOException | NumberFormatException e) {
            System.out.println("fixoffset" + e);
        }
    }

    public void thisfunction(String file_name) {

        int max_freq = 0;
        words_per_file.clear();
        totalFiles++;
        try {

            NXMLFileReader xmlFile = new NXMLFileReader(new File(file_name));
            StringTokenizer tokenizer;
            for (String tag : tags) {
                String token = "";
                switch (tag) {
                    case "title":
                        token = xmlFile.getTitle();
                        break;
                    case "abstract":
                        token = xmlFile.getAbstr();
                        break;
                    case "body":
                        token = xmlFile.getBody();
                        break;
                    case "journal":
                        token = xmlFile.getJournal();
                        break;
                    case "publisher":
                        token = xmlFile.getPublisher();
                        break;
                    case "authors":
                        token = xmlFile.getAuthors().stream().map((s) -> " " + s).reduce(token, String::concat);
                        break;
                    case "categories":
                        token = xmlFile.getCategories().stream().map((s) -> " " + s).reduce(token, String::concat);
                        break;
                }
                tokenizer = new StringTokenizer(token, "\t\n\r\f ");

                while (tokenizer.hasMoreTokens()) {
                    String currentToken = tokenizer.nextToken();
                    currentToken = currentToken.toLowerCase();

                    currentToken = removePunctuation(currentToken);

                    if (currentToken.equals("") || stopwords.contains(currentToken)) {
                        continue; //word didn't contain letters or was a stopword lol
                    }

                    currentToken = Stemmer.Stem(currentToken);

                    if (DNAcheck(currentToken)) {
                        continue;
                    }
                    Integer freq = words_per_file.get(currentToken);
                    if (freq == null) {
                        freq = 1;
                    } else {
                        freq++;
                    }
                    words_per_file.put(currentToken, freq);

                    if (max_freq < freq) {
                        max_freq = freq;
                    }

                }

            }
            for (Map.Entry<String, Integer> word : words_per_file.entrySet()) {
                double tf = (double) word.getValue() / max_freq;
                Word w = words.get(word.getKey());
                if (w == null) {
                    w = new Word();
                    w.setDf(1);
                } else {
                    w.increaseDf();
                }
                w.setTf(file_name, tf);
                words.put(word.getKey(), w);
            }
            String doc_line = file_name + " ";
            tmp_doc.writeBytes(doc_line);
            doc_tf_offsets.put(file_name, tmp_doc.getFilePointer());
            tmp_doc.writeBytes("00.00\n");
        } catch (IOException ex) {
            System.out.println("this function: " + ex);
        }

    }

    private void makePartials() throws IOException {
        try {
            String file_name = String.valueOf(vocab_cnt);
            Map<String, ArrayList<Integer>> docs_index = new HashMap<>(); //docs and indexes
            Map<String, ArrayList<Double>> docs_values = new HashMap<>(); //docs and indexes
            vocabs.add(file_name);
            postings.add(file_name);
            document_f.add(file_name);

            BufferedWriter v_out = new BufferedWriter(new FileWriter(".\\CollectionIndex\\VocabularyFile_" + file_name + ".txt"));
            BufferedWriter p_out = new BufferedWriter(new FileWriter(".\\CollectionIndex\\PostingFile_" + file_name + ".txt"));

            int offset = 0;
            long d_offset;
            int index = 0;
            for (Map.Entry<String, Word> entry : words.entrySet()) {
                Word word = entry.getValue();
                //posting entry
                String post_line = "";
//                System.err.println("here");
                for (Map.Entry<String, Double> f : word.getTfs().entrySet()) {
                    d_offset = doc_tf_offsets.get(f.getKey());
                    post_line += f.getValue() + " " + f.getKey() + " " + d_offset + "\n";
                    if (docs_index.containsKey(f.getKey())) {
                        docs_index.get(f.getKey()).add(index);
                        docs_values.get(f.getKey()).add(f.getValue());
                    } else {
                        ArrayList<Integer> arrayList = new ArrayList<>();
                        arrayList.add(index);
                        docs_index.put(f.getKey(), arrayList);
                        ArrayList<Double> arrayList1 = new ArrayList<>();
                        arrayList1.add(f.getValue());
                        docs_values.put(f.getKey(), arrayList1);
                    }

                }
                p_out.write(post_line);

                //Vocab entry
                String vocab_line = entry.getKey() + " " + word.getDf() + " " + offset + "\n";
                v_out.write(vocab_line);
                offset += post_line.getBytes().length;
                index++;
            }
//            index--;

            String line = (new File(".\\CollectionIndex\\VocabularyFile_" + file_name + ".txt")).getPath() + "";
//            doc_tf_offsets.put(file_name, doc_offset + line.getBytes().length);
//            line += " 00.00 \n";
//            documentFile.seek(doc_offset);
//            documentFile.writeBytes(line);
//            doc_offset += line.getBytes().length;

            vocab_cnt++;
            v_out.close();
            p_out.close();

            BufferedWriter d_out = new BufferedWriter(new FileWriter(".\\CollectionIndex\\DocumentFile_" + file_name + ".txt"));
            for (Map.Entry<String, ArrayList<Integer>> d : docs_index.entrySet()) {
                d.getValue().add(-1);
                d_out.write(doc_tf_offsets.get(d.getKey()) + " ");

                for (int i = 0, j = 0; i < index; i++) {
                    if (i == d.getValue().get(j)) {
                        d_out.write(docs_values.get(d.getKey()).get(j) + " ");
                        j++;
                        d_out.write(i + " ");
                    }
                }
                d_out.write("\n");
            }
            d_out.close();

        } catch (IOException e) {
            System.out.println("makePartials: " + e);
        }
    }

    private void mergeVocabs() throws FileNotFoundException, IOException {
        Vector<Integer> posting_offsets = new Vector<>();
        
        while (vocabs.size() > 1) {
            try {
                //Vocabs
                ArrayList<Integer> tf1 = new ArrayList<>();
                ArrayList<Integer> tf2 = new ArrayList<>();
                int index = 0;

                String v1filename = ".\\CollectionIndex\\VocabularyFile_" + vocabs.remove() + ".txt";
                String v2filename = ".\\CollectionIndex\\VocabularyFile_" + vocabs.remove() + ".txt";
                BufferedReader v1 = new BufferedReader(new FileReader(v1filename));
                BufferedReader v2 = new BufferedReader(new FileReader(v2filename));

                //Postings
                String p1filename = ".\\CollectionIndex\\PostingFile_" + postings.remove() + ".txt";
                String p2filename = ".\\CollectionIndex\\PostingFile_" + postings.remove() + ".txt";
                BufferedReader p1 = new BufferedReader(new FileReader(p1filename));
                BufferedReader p2 = new BufferedReader(new FileReader(p2filename));

                FileWriter out = new FileWriter(".\\CollectionIndex\\VocabularyFile_Merged" + mergecounter + ".txt");
                FileWriter p_out = new FileWriter(".\\CollectionIndex\\PostingFile_Merged" + mergecounter + ".txt");
//                FileWriter d_out = new FileWriter(".\\CollectionIndex\\PostingFile_Merged" + mergecounter + ".txt");

                String line1 = v1.readLine();
                String line2 = v2.readLine();

                posting_offsets.add(0);

                while (line1 != null && line2 != null) {
                    String[] s2 = line2.split("\\s+");
                    String[] s1 = line1.split("\\s+");

                    if (line1.equals("") || line2.equals("")) {
                        break;
                    }

                    if (s1[0].compareTo(s2[0]) < 0) { //s2 is bigger
                        while (s1[0].compareTo(s2[0]) < 0) {
                            //wirte to posting
//                            p1.seek(Integer.valueOf(s1[2])); //offset in posting
                            String l = "";
                            for (int i = 0; i < Integer.valueOf(s1[1]); i++) {
                                l += p1.readLine() + "\n";
                            }
                            int offset = posting_offsets.get(mergecounter);
                            line1 = s1[0] + " " + s1[1] + " " + offset;
                            posting_offsets.set(mergecounter, offset + l.getBytes().length);
                            p_out.write(l);
                            out.write(line1 + "\n");
                            tf1.add(index++);
                            if ((line1 = v1.readLine()) == null) {
                                break;
                            }

                            s1 = line1.split("\\s+");
                        }
                    } else if (s1[0].compareTo(s2[0]) > 0) {
                        while (s1[0].compareTo(s2[0]) > 0) {
                            //write to posting
//                            p2.seek(Integer.valueOf(s2[2]));
                            String l = "";
                            for (int i = 0; i < Integer.valueOf(s2[1]); i++) {
                                l += p2.readLine() + "\n";
                            }
                            p_out.write(l);

                            int offset = posting_offsets.get(mergecounter);
                            line2 = s2[0] + " " + s2[1] + " " + offset;
                            out.write(line2 + "\n");

                            posting_offsets.set(mergecounter, offset + l.getBytes().length);
                            tf2.add(index++);
                            if ((line2 = v2.readLine()) == null) {
                                break;
                            }
                            s2 = line2.split("\\s+");
                        }
                    } else {
                        int offset = posting_offsets.get(mergecounter);
                        int df = Integer.valueOf(s1[1]) + Integer.valueOf(s2[1]);
                        String newline = s1[0] + " " + df + " " + offset + "\n";

                        //write to posting1
//                        p1.seek(Integer.valueOf(s1[2]));
                        String l = "";
                        for (int i = 0; i < Integer.valueOf(s1[1]); i++) {
                            l += p1.readLine() + "\n";
                        }
                        //write to posting2
//                        p2.seek(Integer.valueOf(s2[2]));
                        for (int i = 0; i < Integer.valueOf(s2[1]); i++) {
                            l += p2.readLine() + "\n";
                        }

                        p_out.write(l);
                        posting_offsets.set(mergecounter, offset + l.getBytes().length);
                        out.write(newline);

                        line1 = v1.readLine();
                        line2 = v2.readLine();

                        tf1.add(index);
                        tf2.add(index++);

                    }
                }

                while (line1 != null) {
                    String[] s = line1.split("\\s+");
                    int offset = posting_offsets.get(mergecounter);
                    line1 = s[0] + " " + s[1] + " " + offset;
                    String l = "";
                    for (int i = 0; i < Integer.valueOf(s[1]); i++) {
                        l += p1.readLine() + "\n";
                    }
                    posting_offsets.set(mergecounter, offset + l.getBytes().length);
                    p_out.write(l);
                    out.write(line1 + "\n");

                    tf1.add(index++);

                    line1 = v1.readLine();
                }
                while (line2 != null) {
                    String[] s = line2.split("\\s+");
                    int offset = posting_offsets.get(mergecounter);
                    String l = "";
                    for (int i = 0; i < Integer.valueOf(s[1]); i++) {
                        l += p2.readLine() + "\n";
                    }

                    line2 = s[0] + " " + s[1] + " " + offset;
                    posting_offsets.set(mergecounter, offset + l.getBytes().length);

                    p_out.write(l);
                    out.write(line2 + "\n");

                    tf2.add(index++);

                    line2 = v2.readLine();
                }
                tf1.add(-1);
                tf2.add(-1);
//                index--;

                vocabs.add("Merged" + mergecounter);
                postings.add("Merged" + mergecounter);

                v1.close();
                Files.delete(Paths.get(v1filename));
                p1.close();
                Files.delete(Paths.get(p1filename));

                v2.close();
                Files.delete(Paths.get(v2filename));
                p2.close();
                Files.delete(Paths.get(p2filename));

                out.close();
                p_out.close();

                FileWriter d_out = new FileWriter(".\\CollectionIndex\\DocumentFile_Merged" + mergecounter + ".txt");
                //Document
                String d1filename = ".\\CollectionIndex\\DocumentFile_" + document_f.remove() + ".txt";
                BufferedReader d1 = new BufferedReader(new FileReader(d1filename));
                String line = d1.readLine();

                while (line != null) {
                    String[] s = line.split("\\s+");
                    assert (s.length == tf1.size());
                    d_out.write(s[0] + " ");
                    assert((s.length-1)/2 == tf1.size());
                    for(int i=1; i < s.length; i +=2){
                        d_out.write(s[i] + " " + tf1.get(Integer.valueOf(s[i+1])) + " ");
                    }
                    d_out.write("\n");
                    line = d1.readLine();
                }
                d1.close();
                Files.delete(Paths.get(d1filename));
                
                String d2filename = ".\\CollectionIndex\\DocumentFile_" + document_f.remove() + ".txt";
                BufferedReader d2 = new BufferedReader(new FileReader(d2filename));
                line = d2.readLine();

                while (line != null) {
                    String[] s = line.split("\\s+");
                    d_out.write(s[0] + " ");
                    assert((s.length-1)/2+1 == tf2.size());
                    for(int i=1; i < s.length; i +=2){
                        d_out.write(s[i] + " " + tf2.get(Integer.valueOf(s[i+1])) + " ");
                    }
                    d_out.write("\n");
                    line = d2.readLine();
                }
                d2.close();
                Files.delete(Paths.get(d2filename));
                d_out.close();

                document_f.add("Merged" + mergecounter);
                mergecounter++;
            } catch (IOException | NumberFormatException e) {
                System.out.println("mergeVocabs: " + e);
            }
        }
        System.out.println("total merges " + mergecounter);
        mergecounter--;
        //Rename
        File file = new File(".\\CollectionIndex\\VocabularyFile_" + vocabs.remove() + ".txt");
        File file2 = new File(".\\CollectionIndex\\VocabularyFile.txt");
        file.renameTo(file2);

        file = new File(".\\CollectionIndex\\PostingFile_" + postings.remove() + ".txt");
        file2 = new File(".\\CollectionIndex\\PostingFile.txt");
        file.renameTo(file2);

        file = new File(".\\CollectionIndex\\DocumentFile_" + document_f.remove() + ".txt");
        file2 = new File(".\\CollectionIndex\\DocumentFile.txt");
        file.renameTo(file2);
    }

    //used
    HashSet<String> get_stopwords() {
        HashSet<String> stopwords = new HashSet<>();
        String line = new String();

        FileReader fr = null;
        BufferedReader br = null;
        try {
            //Add english stopwords

            fr = new FileReader("./ignore/stopwordsEn.txt");
            br = new BufferedReader(fr);
            while ((line = br.readLine()) != null) {
                stopwords.add(line);
            }

            //Add greek stopwords
            fr = new FileReader("./ignore/stopwordsGr.txt");
            br = new BufferedReader(fr);
            while ((line = br.readLine()) != null) {
                stopwords.add(line);
            }
        } catch (IOException e) {
            System.out.println("getstopwords" +e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException ex) {
                System.out.println("getstopwords" + ex);
            }
        }
        return stopwords;
    }

}
