/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package XMLReader;

import java.util.TreeMap;

/**
 *
 * @author Hackerman
 */
public class Word {

    private int df;
    private TreeMap<String, Double> tfs;

    public Word() {
        tfs = new TreeMap<>();
    }

    public int getDf() {
        return df;
    }

    public void setDf(int df) {
        this.df = df;
    }

    public TreeMap<String, Double> getTfs() {
        return tfs;
    }

    public void setTfs(TreeMap<String, Double> tfs) {
        this.tfs = tfs;
    }

    public void increaseDf(){
        df++;
    }
    
    public void setTf(String file_name, double tf){
        tfs.put(file_name, tf);
    }

    @Override
    public String toString() {
        return "{" + "df=" + df + ", tfs=" + tfs + '}';
    }
}
