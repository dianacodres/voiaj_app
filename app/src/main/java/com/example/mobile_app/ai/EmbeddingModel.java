package com.example.mobile_app.ai;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Model de embeddings GENERIC (all-MiniLM-L6-v2 de BAZA, NE-antrenat de SetFit) -
 * folosit pentru cautare semantica generala (recomandari de destinatie, "ask anything"),
 * unde avem nevoie sa comparam concepte diverse (plaja vs munte vs arta), nu doar
 * sa recunoastem cele 5 intentii pentru care e specializat IntentClassifier.
 *
 * Foloseste model_base.onnx (exportul ONNX original, dinainte de fine-tuning-ul SetFit).
 */
public class EmbeddingModel {

    private final OrtEnvironment mediu;
    private final OrtSession sesiune;
    private final WordPieceTokenizer tokenizer;

    public EmbeddingModel(Context context) throws Exception {
        mediu = OrtEnvironment.getEnvironment();
        byte[] modelBytes = citesteAssetBytes(context, "model1.onnx");
        sesiune = mediu.createSession(modelBytes, new OrtSession.SessionOptions());
        tokenizer = new WordPieceTokenizer(context);
    }

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
        int dimensiune = tokenEmbeddings[0].length;
        double[] suma = new double[dimensiune];
        int nrTokenuriReale = 0;

        for (int t = 0; t < tokenEmbeddings.length; t++) {
            if (attentionMask[t] == 1) {
                for (int d = 0; d < dimensiune; d++) suma[d] += tokenEmbeddings[t][d];
                nrTokenuriReale++;
            }
        }

        double[] medie = new double[dimensiune];
        for (int d = 0; d < dimensiune; d++) medie[d] = suma[d] / Math.max(nrTokenuriReale, 1);

        double normaL2 = 0;
        for (double v : medie) normaL2 += v * v;
        normaL2 = Math.sqrt(normaL2);

        double[] normalizat = new double[dimensiune];
        for (int d = 0; d < dimensiune; d++) normalizat[d] = medie[d] / Math.max(normaL2, 1e-9);

        return normalizat;
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
