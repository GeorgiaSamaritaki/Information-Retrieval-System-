/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IRQualityEvaluator;

import gr.uoc.csd.hy463.Topic;
import gr.uoc.csd.hy463.TopicsReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Math.log;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class IRQualityEvaluator {

    static searcher_t s = new searcher_t();

    public static void main(String[] args) {
//        s.search();

        try {
//            create_results();
            evaluateResults();

        } catch (Exception e) {
            System.out.println("ir" + e);
        }

    }

    private static void create_results() throws Exception {
        ArrayList<Topic> topics = TopicsReader.readTopics("topics.xml");
//        for (Topic topic : topics) {
//            System.out.println(topic.getNumber());
//            System.out.println(topic.getType());
//            System.out.println(topic.getSummary());
//            System.out.println(topic.getDescription());
//            System.out.println("---------");
//        }

        BufferedWriter results = new BufferedWriter(new FileWriter("results.txt"));
        for (Topic topic : topics) {
            String query = createQuery(topic);
            System.out.println(query);
            ArrayList<Entry<String, Double>> docs = s.search(query);
            if (docs == null) {
                System.out.println("No relative documents found!");
            } else {
                String line = "";
                ArrayList<Entry<String, Double>> less_docs;
                if (docs.size() < 1000) {
                    less_docs = new ArrayList<>(docs.subList(0, docs.size()));
                } else {
                    less_docs = new ArrayList<>(docs.subList(0, 1000));
                }
                int i = 1;
                for (Entry<String, Double> entry : less_docs) {
                    line += topic.getNumber() + " 0 " + getPMCID(entry.getKey()) + " " + i + " " + entry.getValue() + " run\n";
                    i++;
                }
                results.write(line);
            }
        }
        results.close();   
    }

    private static String createQuery(Topic topic) {
        String query = topic.getType().toString() + " " + topic.getSummary();
        return query;
    }

    private static String getPMCID(String path) {
        int index = path.lastIndexOf("\\");
        String s = path.substring(index);
        return (s.substring(1, s.lastIndexOf('.')));
    }

    private static void evaluateResults() throws FileNotFoundException, IOException {
        BufferedReader qrelsbuffer = new BufferedReader(new FileReader("qrels.txt"));
        BufferedReader results = new BufferedReader(new FileReader("results.txt"));
        BufferedWriter eval = new BufferedWriter(new FileWriter("eval_results.txt"));

        String line2[] = qrelsbuffer.readLine().split("\\s+");

        for (int topics = 1; topics <= 30; topics++) {
            int numof2 = 0;
            int numof1 = 0;
            Map<Integer, Integer> qrels = new TreeMap<>();
            while (line2 != null && Integer.valueOf(line2[0]) == topics) {
                int score = Integer.valueOf(line2[3]);
                if (score == 2) {
                    numof2++;
                } else if (score == 1) {
                    numof1++;
                }
                qrels.put(Integer.valueOf(line2[2]), score);

                String line_ = qrelsbuffer.readLine();
                if (line_ != null) {
                    line2 = line_.split("\\s+");
                } else {
                    break;
                }
            }

            String line[];
            String tmp = results.readLine();
            if (tmp != null) {
                line = tmp.split("\\s+");
            } else {
                break;
            }

            double relevant = 0;
            double irrelevant = 0;
            double total = 0;
            double avprecision = 0;
            double dcg = 0;
            double idcg = 0;
            double bpref_1 = 0;
            while (line != null && Integer.valueOf(line[0]) == topics) {
                assert (line.length == 6);
                int pmcid = Integer.valueOf(line[2]);
//                double relevance_score = Double.valueOf(line[4]);

                if (qrels.containsKey(pmcid)) {
                    double qscore = qrels.get(pmcid);
                    total++;

                    if (qscore != 0) {
                        relevant++;
                        avprecision += relevant / total;
                        bpref_1 += irrelevant;
                        dcg += qscore / log2(total + 1);
                    } else {
                        irrelevant += 1;
                    }

                }
                String line_ = results.readLine();
                if (line_ != null) {
                    line = line_.split("\\s+");
                } else {
                    break;
                }
            }

            for (int i = 1; i <= numof2; i++) {
                idcg += 2 / log2(i + 1);
            }
            for (int i = numof2 + 1; i <= numof2 + numof1; i++) {
                idcg += 1 / log2(i + 1);
            }

            double R = numof1 + numof2;
            double Avep = avprecision / R;
            double bpref;
            if(relevant>0) bpref = (relevant - (bpref_1 / relevant) ) / relevant;
            else bpref =0;
            double nDCG = dcg / idcg;
            eval.write(topics + " " + bpref + " " + Avep + " " + nDCG + "\n");
        }

        qrelsbuffer.close();
        results.close();
        eval.close();
    }

    private static double log2(double num) {
        return log(num) / log(2);
    }
}
