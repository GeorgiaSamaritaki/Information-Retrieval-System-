/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package XMLReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import static java.lang.Math.log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import mitos.stemmer.Stemmer;
import org.xml.sax.SAXException;

/**
 *
 * @author Georgia Samaritaki
 */
public class Searcher {

    ArrayList<String> vocabulary;
    ArrayList<String> vocabularyinfo;
    RandomAccessFile document;
    RandomAccessFile posting;
    TreeMap<String, Double> query_w;
    int totalDocs;
    double query_norm;
    TreeMap<String, Double> inner_gradients;
    TreeMap<String, Double> cosSims;
    TreeMap<String, Integer> doc_offsets;
    HashSet<String> stopwords;

    Searcher() throws ParserConfigurationException, SAXException {
        try {
            Scanner input = new Scanner(System.in);
            vocabulary = new ArrayList<>();
            vocabularyinfo = new ArrayList<>();
            query_w = new TreeMap<>();
            inner_gradients = new TreeMap<>();
            doc_offsets = new TreeMap<>();
            cosSims = new TreeMap<>();
            stopwords = new HashSet<>();
            totalDocs = countDocs();

            Stemmer.Initialize();
            LoadDoc();
            LoadVocab();

            stopwords = get_stopwords();

            System.out.print("Enter your query: ");
            String query = input.nextLine();
            while (!query.equals("exit")) {
                ArrayList<String> words = get_words(query);
                System.out.println(Arrays.toString(words.toArray()));
                query_w = calculate_tf(words);

//                System.out.println("Text entered = ");
//                System.out.println("weights of the query:");
//                for (Map.Entry<String, Double> word : query_w.entrySet()) {
//                    System.out.println(word.getKey() + ": " + word.getValue());
//                }
                for (String w : words) {
                    get_docs(w);
                }

                query_norm = calculateQueryNorm();
                calculateCosSim();
                System.out.println("Related documents: ");
                ArrayList<Entry<String, Double>> docs_sorted = sortDocuments();
                for (Entry<String, Double> entry : docs_sorted) {
                    System.out.println(entry.getKey() + " cosSim: " + entry.getValue());
                }
//                System.out.println(Arrays.toString(docs.toArray()));

                System.out.print("Enter your query: ");
                query = input.nextLine();
                query_w.clear();
                inner_gradients.clear();
                doc_offsets.clear();
                cosSims.clear();
            }
            
//            readTopics();
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Searcher.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Searcher.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    void LoadDoc() throws FileNotFoundException {
        document = new RandomAccessFile(".\\CollectionIndex\\DocumentFile.txt", "rw");
        posting = new RandomAccessFile(".\\CollectionIndex\\PostingFile.txt", "rw");
    }

    void LoadVocab() throws FileNotFoundException, IOException {

        String fileName = ".\\CollectionIndex\\VocabularyFile.txt"; //this path is on my local
        BufferedReader fileBufferReader = new BufferedReader(new FileReader(fileName));

        String fileLineContent;
        while ((fileLineContent = fileBufferReader.readLine()) != null) {
            String[] split = fileLineContent.split("\\s+", 2);
            vocabulary.add(split[0]);
            vocabularyinfo.add(split[1]);
        }
    }

    private static String removePunctuation(String word) {
        return word.replaceAll("[^a-zA-Z]", "");
    }

    ArrayList<String> get_words(String line) {
        ArrayList<String> words = new ArrayList<>();

        StringTokenizer tokenizer = new StringTokenizer(line, "\t\n\r\f ");

        while (tokenizer.hasMoreTokens()) {
            String currentToken = tokenizer.nextToken();
            currentToken = currentToken.toLowerCase();

            currentToken = removePunctuation(currentToken);
            if (currentToken.equals("") || stopwords.contains(currentToken)) {
                continue; //word didn't contain letters or was a stopword lol
            }
            currentToken = Stemmer.Stem(currentToken);
            words.add(currentToken);
        }
        return words;
    }

    void get_docs(String word) throws IOException {
        int binarySearch = Collections.binarySearch(vocabulary, word);
        if (binarySearch < 0) {
            return;
        }

        double tf = query_w.get(word);
        String[] l = vocabularyinfo.get(binarySearch).split("\\s");
        int df = Integer.valueOf(l[0]);
        double idf = log((double) totalDocs / (double) df) / log(2);
        query_w.replace(word, tf * idf);

        int offset = Integer.valueOf(l[1]);

        posting.seek(offset);

        String line = posting.readLine();
        while (line != null && df-- != 0) {
            String[] s = line.split("\\s+");
            doc_offsets.put(s[1], Integer.valueOf(s[2]));
            double w = (tf * idf) * (Double.valueOf(s[0]) * idf);
            if (inner_gradients.containsKey(s[1])) {
                inner_gradients.replace(s[1], inner_gradients.get(s[1]) + w);
            } else {
                inner_gradients.put(s[1], w);
            }
            line = posting.readLine();
        }
    }

    private int countDocs() throws FileNotFoundException, IOException {
        BufferedReader docs = new BufferedReader(new FileReader(".\\CollectionIndex\\DocumentFile.txt"));
        String line = "";
        int cnt = 0;
        while ((line = docs.readLine()) != null) {
            cnt++;
        }
        return cnt;
    }

    private TreeMap<String, Double> calculate_tf(ArrayList<String> query) {
        TreeMap<String, Double> tfs = new TreeMap<>();
        double max_freq = 1;
        for (String s : query) {
            if (tfs.containsKey(s)) {
                tfs.replace(s, tfs.get(s) + 1.0);
                if (max_freq < tfs.get(s)) {
                    max_freq = tfs.get(s);
                }
            } else {
                tfs.put(s, 1.0);
            }
        }
        for (Map.Entry<String, Double> entry : tfs.entrySet()) {
            entry.setValue(entry.getValue() / max_freq);
        }
        return tfs;
    }

    private double calculateQueryNorm() {
        double norm = 0;
        for (Map.Entry<String, Double> word : query_w.entrySet()) {
            norm += word.getValue() * word.getValue();
        }
        return Math.sqrt(norm);
    }

    private void calculateCosSim() throws IOException {
        double d_norm = 0;
        int offset = 0;
        double cosSim = 0;
        for (Map.Entry<String, Double> file : inner_gradients.entrySet()) {
            offset = doc_offsets.get(file.getKey());
            document.seek(offset);
            String t = document.readLine().split("\\s+")[1];
            d_norm = Double.valueOf(t);
            cosSim = file.getValue() / (d_norm * query_norm);
            cosSims.put(file.getKey(), cosSim);
        }
    }

    private ArrayList<Entry<String, Double>> sortDocuments() {
        ArrayList<Entry<String, Double>> list = new ArrayList<>(cosSims.entrySet());
        list.sort(Entry.comparingByValue());
        Collections.reverse(list);
        return list;
    }

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
            System.out.println("getstopwords" + line);
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

    //ginetai
    private void readTopics() throws ParserConfigurationException, SAXException, IOException {
        File file = new File(".\\project_details\\6_Resources_Corpus\\topics.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(file);
        String usr = document.getElementsByTagName("description").item(0).getTextContent();
        String pwd = document.getElementsByTagName("summary").item(0).getTextContent();
        System.out.println(usr);
        System.out.println(pwd);
    }
}
