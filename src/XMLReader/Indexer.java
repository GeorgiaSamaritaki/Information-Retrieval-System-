/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package XMLReader;

import java.io.IOException;

public class Indexer {

    public static void main(String[] args) {
        try {

//            XMLReader reader = new XMLReader(".\\testask2");
            //".\\ignore\\MedicalCollection\\00"
//            XMLReader reader = new XMLReader(".\\ignore\\test\\");
            XMLReader reader = new XMLReader(".\\ignore\\MedicalCollection");
//            XMLReader reader = new XMLReader(".\\dist\\MedicalCollection\\");
            reader.createIndex();

//            Searcher searcher = new Searcher();
            
        } catch (IOException e) {
            System.out.println(e);
        }
    }

}