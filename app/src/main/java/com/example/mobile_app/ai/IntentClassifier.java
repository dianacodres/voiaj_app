package com.example.mobile_app.ai;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Clasificatorul de intentii Voiaj - ruleaza modelul ONNX (fine-tuned) + capul de clasificare
 * (regresie logistica), complet on-device, fara server.
 *
 * Foloseste: model.onnx, vocab.txt, intent_classifier_weights.json (toate in assets/).
 */
public class IntentClassifier {

    private final OrtEnvironment mediu;
    private final OrtSession sesiune;
    private final WordPieceTokenizer tokenizer;

    private String[] clase;
    private double[][] coeficienti; // [5][384]
    private double[] intercepte;    // [5]

    public IntentClassifier(Context context) throws Exception {
        // 1. Incarcam modelul ONNX direct din assets (fara sa-l copiem pe disc)
        mediu = OrtEnvironment.getEnvironment();
        byte[] modelBytes = citesteAssetBytes(context, "model.onnx");
        sesiune = mediu.createSession(modelBytes, new OrtSession.SessionOptions());

        // 2. Tokenizer-ul
        tokenizer = new WordPieceTokenizer(context);

        // 3. Coeficientii capului de clasificare
        incarcaGreutatiClasificare(context);
    }

    private void incarcaGreutatiClasificare(Context context) throws IOException, org.json.JSONException {
        InputStream is = context.getAssets().open("intent_classifier_weights.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String linie;
        while ((linie = reader.readLine()) != null) sb.append(linie);
        reader.close();

        JSONObject json = new JSONObject(sb.toString());

        JSONArray claseJson = json.getJSONArray("classes");
        clase = new String[claseJson.length()];
        for (int i = 0; i < claseJson.length(); i++) clase[i] = claseJson.getString(i);

        JSONArray coefJson = json.getJSONArray("coef");
        coeficienti = new double[coefJson.length()][];
        for (int i = 0; i < coefJson.length(); i++) {
            JSONArray rand = coefJson.getJSONArray(i);
            coeficienti[i] = new double[rand.length()];
            for (int j = 0; j < rand.length(); j++) coeficienti[i][j] = rand.getDouble(j);
        }

        JSONArray interceptJson = json.getJSONArray("intercept");
        intercepte = new double[interceptJson.length()];
        for (int i = 0; i < interceptJson.length(); i++) intercepte[i] = interceptJson.getDouble(i);
    }

    /** Rezultatul clasificarii: intentia aleasa + increderea (0-1). */
    public static class Rezultat {
        public final String intentie;
        public final double increderea;

        Rezultat(String intentie, double increderea) {
            this.intentie = intentie;
            this.increderea = increderea;
        }
    }

    /** Punctul principal de intrare: primeste o propozitie, intoarce intentia prezisa. */
    public Rezultat clasifica(String propozitie) throws Exception {
        double[] embeddingPropozitie = getEmbedding(propozitie);

        double[] scoruri = new double[clase.length];
        for (int i = 0; i < clase.length; i++) {
            double suma = intercepte[i];
            for (int j = 0; j < embeddingPropozitie.length; j++) {
                suma += coeficienti[i][j] * embeddingPropozitie[j];
            }
            scoruri[i] = suma;
        }

        double[] probabilitati = softmax(scoruri);

        int indexMaxim = 0;
        for (int i = 1; i < probabilitati.length; i++) {
            if (probabilitati[i] > probabilitati[indexMaxim]) indexMaxim = i;
        }

        return new Rezultat(clase[indexMaxim], probabilitati[indexMaxim]);
    }

    /**
     * Calculeaza embedding-ul brut (vectorul de 384 numere, normalizat) al unui text -
     * folosit atat pentru clasificarea de intentie, cat si pentru cautarea semantica
     * (recomandari de destinatie, "ask anything").
     */
    public double[] getEmbedding(String text) throws Exception {
        WordPieceTokenizer.Encodare encodare = tokenizer.encode(text);

        long[][] inputIds2d = {encodare.inputIds};
        long[][] attentionMask2d = {encodare.attentionMask};

        OnnxTensor inputIdsTensor = OnnxTensor.createTensor(mediu, inputIds2d);
        OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(mediu, attentionMask2d);

        Map<String, OnnxTensor> intrari = new HashMap<>();
        intrari.put("input_ids", inputIdsTensor);
        intrari.put("attention_mask", attentionMaskTensor);

        OrtSession.Result rezultatOnnx = sesiune.run(intrari);

        float[][][] lastHiddenState = (float[][][]) rezultatOnnx.get(0).getValue();
        double[] embedding = meanPoolingSiNormalizare(lastHiddenState[0], encodare.attentionMask);

        inputIdsTensor.close();
        attentionMaskTensor.close();
        rezultatOnnx.close();

        return embedding;
    }

    private double[] meanPoolingSiNormalizare(float[][] tokenEmbeddings, long[] attentionMask) {
        int dimensiune = tokenEmbeddings[0].length; // 384
        double[] suma = new double[dimensiune];
        int nrTokenuriReale = 0;

        for (int t = 0; t < tokenEmbeddings.length; t++) {
            if (attentionMask[t] == 1) {
                for (int d = 0; d < dimensiune; d++) {
                    suma[d] += tokenEmbeddings[t][d];
                }
                nrTokenuriReale++;
            }
        }

        double[] medie = new double[dimensiune];
        for (int d = 0; d < dimensiune; d++) {
            medie[d] = suma[d] / Math.max(nrTokenuriReale, 1);
        }

        // Normalizare L2 (impartim la lungimea vectorului, ca sa aiba lungime 1)
        double normaL2 = 0;
        for (double v : medie) normaL2 += v * v;
        normaL2 = Math.sqrt(normaL2);

        double[] normalizat = new double[dimensiune];
        for (int d = 0; d < dimensiune; d++) {
            normalizat[d] = medie[d] / Math.max(normaL2, 1e-9);
        }

        return normalizat;
    }

    private double[] softmax(double[] scoruri) {
        double maxim = scoruri[0];
        for (double s : scoruri) if (s > maxim) maxim = s;

        double[] exponentiale = new double[scoruri.length];
        double sumaExp = 0;
        for (int i = 0; i < scoruri.length; i++) {
            exponentiale[i] = Math.exp(scoruri[i] - maxim);
            sumaExp += exponentiale[i];
        }

        double[] rezultat = new double[scoruri.length];
        for (int i = 0; i < scoruri.length; i++) {
            rezultat[i] = exponentiale[i] / sumaExp;
        }
        return rezultat;
    }

    private byte[] citesteAssetBytes(Context context, String numeFisier) throws IOException {
        InputStream is = context.getAssets().open(numeFisier);
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int lungime;
        byte[] date = new byte[16384];
        while ((lungime = is.read(date, 0, date.length)) != -1) {
            buffer.write(date, 0, lungime);
        }
        is.close();
        return buffer.toByteArray();
    }

    public void elibereaza() {
        try {
            sesiune.close();
        } catch (Exception ignored) {
        }
    }
}