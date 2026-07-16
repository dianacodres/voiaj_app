package com.example.mobile_app.ai;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tokenizer WordPiece, la fel ca cel folosit de BERT/MiniLM in Python.
 * Citeste vocab.txt din assets si transforma text in input_ids/attention_mask.
 */
public class WordPieceTokenizer {

    private final Map<String, Integer> vocab = new HashMap<>();
    private static final int MAX_LEN = 128; // la fel ca la exportul ONNX (-ois ...:1,128)

    public WordPieceTokenizer(Context context) throws IOException {
        InputStream is = context.getAssets().open("vocab.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String linie;
        int index = 0;
        while ((linie = reader.readLine()) != null) {
            vocab.put(linie.trim(), index);
            index++;
        }
        reader.close();
    }

    /** Rezultatul tokenizarii: input_ids si attention_mask, ambele de lungime MAX_LEN. */
    public static class Encodare {
        public final long[] inputIds;
        public final long[] attentionMask;

        Encodare(long[] inputIds, long[] attentionMask) {
            this.inputIds = inputIds;
            this.attentionMask = attentionMask;
        }
    }

    public Encodare encode(String text) {
        List<String> tokenuri = tokenize(text.toLowerCase(Locale.ROOT));

        List<Integer> ids = new ArrayList<>();
        ids.add(vocab.getOrDefault("[CLS]", 101));
        for (String t : tokenuri) {
            ids.add(vocab.getOrDefault(t, vocab.getOrDefault("[UNK]", 100)));
        }
        ids.add(vocab.getOrDefault("[SEP]", 102));

        // Trunchiem daca e prea lung
        if (ids.size() > MAX_LEN) {
            ids = ids.subList(0, MAX_LEN);
        }

        long[] inputIds = new long[MAX_LEN];
        long[] attentionMask = new long[MAX_LEN];
        int padId = vocab.getOrDefault("[PAD]", 0);

        for (int i = 0; i < MAX_LEN; i++) {
            if (i < ids.size()) {
                inputIds[i] = ids.get(i);
                attentionMask[i] = 1;
            } else {
                inputIds[i] = padId;
                attentionMask[i] = 0;
            }
        }

        return new Encodare(inputIds, attentionMask);
    }

    /** Imparte textul in cuvinte (pe spatii/punctuatie), apoi fiecare cuvant in subcuvinte WordPiece. */
    private List<String> tokenize(String text) {
        List<String> rezultat = new ArrayList<>();
        StringBuilder cuvantCurent = new StringBuilder();

        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            boolean esteSeparator = Character.isWhitespace(c) || (Character.isLetterOrDigit(c) == false && i < text.length());

            if (esteSeparator || i == text.length()) {
                if (cuvantCurent.length() > 0) {
                    rezultat.addAll(wordpieceCuvant(cuvantCurent.toString()));
                    cuvantCurent.setLength(0);
                }
                if (i < text.length() && !Character.isWhitespace(c)) {
                    // caracterul de punctuatie devine el insusi un token separat
                    rezultat.addAll(wordpieceCuvant(String.valueOf(c)));
                }
            } else {
                cuvantCurent.append(c);
            }
        }
        return rezultat;
    }

    /** Algoritmul WordPiece clasic: greedy longest-match-first, cu prefixul "##" pentru continuari. */
    private List<String> wordpieceCuvant(String cuvant) {
        List<String> subcuvinte = new ArrayList<>();
        int start = 0;

        while (start < cuvant.length()) {
            int end = cuvant.length();
            String subcuvantGasit = null;

            while (start < end) {
                String candidat = cuvant.substring(start, end);
                if (start > 0) candidat = "##" + candidat;

                if (vocab.containsKey(candidat)) {
                    subcuvantGasit = candidat;
                    break;
                }
                end--;
            }

            if (subcuvantGasit == null) {
                // cuvant necunoscut complet
                subcuvinte.add("[UNK]");
                return subcuvinte;
            }

            subcuvinte.add(subcuvantGasit);
            start = end;
        }

        return subcuvinte;
    }
}