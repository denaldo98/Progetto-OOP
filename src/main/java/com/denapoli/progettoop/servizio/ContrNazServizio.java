package com.denapoli.progettoop.servizio;

import com.denapoli.progettoop.modello.ContributoNazione;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONValue;
import org.json.JSONObject;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Classe che carica il dataset gestendone l'accesso
 */
@Service
public class ContrNazServizio {
    private final static String COMMA_DELIMITER = ";";
    private List<ContributoNazione> contributi = new ArrayList<>();
    public Metadata metadata;

    /**
     * Costruttore per scaricare il dataset e fare il parsing del csv
     */
    public ContrNazServizio() {
        String fileCSV = "dataset.csv";
        if (Files.exists ( Paths.get ( fileCSV ) )) {
            parsing ( fileCSV );
            System.out.println ( "Dataset caricato da file locale" );
        } else
            try {
                URLConnection openConnection = new URL ("http://data.europa.eu/euodp/data/api/3/action/package_show?id=V7ZkhAQ536LhqVNfAeGA" ).openConnection ();
                openConnection.addRequestProperty ( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36" );
                InputStream in = openConnection.getInputStream ();
                StringBuilder data = new StringBuilder ();
                String line = "";
                try {
                    InputStreamReader inR = new InputStreamReader ( in );
                    BufferedReader buf = new BufferedReader ( inR );
                    while ((line = buf.readLine ()) != null) {
                        data.append ( line );
                    }
                } finally {
                    in.close ();
                }
                JSONObject obj = (JSONObject) JSONValue.parseWithException ( data.toString () );
                JSONObject objI = (JSONObject) (obj.get ( "result" ));
                JSONArray objA = (JSONArray) (objI.get ( "resources" ));

                for (Object o : objA) {
                    if (o instanceof JSONObject) {
                        JSONObject o1 = (JSONObject) o;
                        String format = (String) o1.get ( "format" );
                        String urlD = (String) o1.get ( "url" );

                        if (format.equals ( "http://publications.europa.eu/resource/authority/file-type/CSV" )) {
                            download ( urlD, fileCSV );
                        }
                    }
                }
                System.out.println ( "OK" );
            } catch (Exception e) {
                e.printStackTrace ();
            }
        parsing ( fileCSV );
        metadata = new Metadata ();
    } //fine costruttore

    private static void download(String url, String fileName) throws Exception {
            try ( InputStream in = URI.create ( url ).toURL ().openStream () ) {
                Files.copy ( in, Paths.get ( fileName ) );
            }
        }





    private void parsing(String fileCSV) {
        try ( BufferedReader bffr = new BufferedReader ( new FileReader ( fileCSV ) ) ) {
            bffr.readLine (); // salto la prima riga
            String riga;
            while ((riga = bffr.readLine ()) != null) {    // leggo ogni riga del file
                //sostituisco le virgole con ; che utilizzerò come separatore
                riga = riga.replace ( ",", COMMA_DELIMITER );
                //uso split per dividere la riga in corrispondenza dei separatori, con trim elimino i caratteri non visibili
                String[] rigaSeparata = riga.trim ().split ( COMMA_DELIMITER );
                // prendiamo i valori dei singoli campi dalla riga
                char freq = rigaSeparata[0].trim ().charAt ( 0 );//freq è di tipo char
                String geo = rigaSeparata[1].trim ();
                String unit = rigaSeparata[2].trim ();
                String aid_instr = rigaSeparata[3].trim ();
                double[] contributo = new double[ContributoNazione.intervalloAnni];
                for (int i = 0; i < ContributoNazione.intervalloAnni; i++) {
                    contributo[i] = Double.parseDouble ( rigaSeparata[4 + i].trim () );
                }
                // prendendo i valori ottenuti dal parsing, creo un nuovo oggetto e lo inserisco nella lista
                ContributoNazione nuova = new ContributoNazione ( freq, geo, unit, aid_instr, contributo );
                contributi.add ( nuova );
            }
        }// apro il buffer di lettura
        catch (IOException e) {
            e.printStackTrace ();
        }
    }
        /**
     * Restituisce il dataset completo
     *
     * @return tutta la lista di oggetti
     */
    public List getData(){
        return contributi;
    }

    /**
     * Restituisce l'oggetto che corrisponde all'indice passato
     *
     * @param n indice dell'oggetto richiesto
     * @return l'oggetto corrispondente al valore di indice n
     */
    public ContributoNazione getContrNaz(int n) {//restituisce il contributo n-esimo
        if (n < contributi.size()) return contributi.get(n);
        throw new ResponseStatusException ( HttpStatus.BAD_REQUEST, "Oggetto di indice " + n + " non esiste!");
    }

    /**
     * Restituisce le statistiche relative ad un certo campo
     *
     * @param fieldName nome del campo
     * @param anno eventuale anno se si scegli il campo contributo
     * @return Map contenente le statistiche
     */
    public Map getStatistiche(String fieldName, int... anno) {
        if(anno.length == 1) {
            return Statistiche.getTutteStatistiche(fieldName+(anno[0]+2000), getFieldValues(fieldName, anno[0]));

        }
        return  Statistiche.getTutteStatistiche(fieldName, getFieldValues(fieldName));
    }

    /**
     * Restituisce le statistiche relative a tutti i campi
     *
     * @return lista di mappe contenenti le statistiche relative ad ogni campo
     */
    public List<Map> getStatistiche() {
        Field[] fields = ContributoNazione.class.getDeclaredFields();// questo ci da l'elenco di tutti gli attributi della classe
        List<Map> list = new ArrayList<>();
        for (Field f : fields) {
            String fieldName = f.getName();//f è l'oggetto di tipo fieldsName estrae il nome del campo corrente
            if(fieldName.equals("contributo"))
                for( int i=0; i<ContributoNazione.intervalloAnni; i++)
                    list.add(getStatistiche("contributo", i+2000 ));
                else list.add(getStatistiche(fieldName));//va ad aggiungere alla lista  la mappa che contiene le statistiche del campo fieldName

        }
        return list;
    }


    /**
     * Metodo che estrae dalla lista di oggetti la lista dei valori relativi ad un singolo campo: se si tratta del campo contributi(vettore di double) viene richiesto come parametro anche l'anno
     *
     * @param nomeCampo campo del quale estrarre i valori
     * @param anno  anno rispetto al quale estrarre i valori
     * @return lista dei valori del campo richiesto
     */
    private List getFieldValues(String nomeCampo, int... anno) {
        List<Object> values = new ArrayList<>();
        try {
            if(nomeCampo.equals("contributo") && anno.length == 0 ){
                System.err.println("Errore!");
                return values;                      //da modificare
            }
            if(!nomeCampo.equals("contributo")){
            //serve per scorrere tutti gli oggetti ed estrarre i valori del campo nomeCampo
                for (ContributoNazione contr : contributi) {
                    Method getter = ContributoNazione.class.getMethod("get" + nomeCampo.substring(0, 1).toUpperCase() + nomeCampo.substring(1));
                    Object value = getter.invoke(contr);
                    values.add(value);
                }
            }
            else {
                    for(ContributoNazione contr : contributi){
                        Object value= contr.getContributo()[anno[0]-2000];
                        values.add(value);
            }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field '" + nomeCampo + "' does not exist");
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return values;
    }
    /**
     * Restituisce una lista formata dagli oggetti che soddisfano il filtro
     *
     * @param nomeCampo campo da filtrare
     * @param oper  operatore di confronto
     * @param rif valore di riferimento
     * @param anno eventuale anno su cui applicare il filtro per il contributo
     * @return lista di oggetti che soddisfano il filtro
     */
    public List<ContributoNazione> getDatiFiltrati(String nomeCampo, String oper, Object rif, int ... anno) {
        List<Integer> filtrati = Filtri.filtra(getFieldValues(nomeCampo, anno), oper, rif);    //applico il filtro alla lista
        List<ContributoNazione> risultatoFiltro = new ArrayList<>(); //aggiungo alla lista solo gli oggetti che soddisfano le specifiche del filtro attraverso gli indici
        for (int i : filtrati) {
            risultatoFiltro.add(contributi.get(i));
        }
        return risultatoFiltro;
    }

}