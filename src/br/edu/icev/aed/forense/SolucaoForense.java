package br.edu.icev.aed;

import br.edu.icev.aed.forense.AnaliseForenseAvancada;
import br.edu.icev.aed.forense.Alerta;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SolucaoForense implements AnaliseForenseAvancada {

    @Override
    public Set<String> encontrarSessoesInvalidas(String caminhoArquivo) throws IOException {
        Set<String> sessoesInvalidas = new HashSet<>();
        Map<String, Stack<String>> pilhasPorUsuario = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {

                String[] campos = linha.split(",");
                if (campos.length < 4) continue;

                String userId = campos[1];
                String sessionId = campos[2];
                String actionType = campos[3];

                pilhasPorUsuario.putIfAbsent(userId, new Stack<>());
                Stack<String> pilha = pilhasPorUsuario.get(userId);

                if ("LOGIN".equals(actionType)) {

                    if (!pilha.isEmpty()) {
                        sessoesInvalidas.add(pilha.peek());
                        pilha.pop();
                    }
                    pilha.push(sessionId);
                } else if ("LOGOUT".equals(actionType)) {

                    if (pilha.isEmpty() || !pilha.peek().equals(sessionId)) {
                        sessoesInvalidas.add(sessionId);
                    } else {
                        pilha.pop();
                    }
                }
            }
        }

        for (Stack<String> pilha : pilhasPorUsuario.values()) {
            sessoesInvalidas.addAll(pilha);
        }

        return sessoesInvalidas;
    }

    @Override
    public List<String> reconstruirLinhaDoTempo(String caminhoArquivoCsv, String sessionId) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public List<Alerta> priorizarAlertas(String caminhoArquivoCsv, int n) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public Map<Long, Long> encontrarPicosDeTransferencia(String caminhoArquivoCsv) throws IOException {
        return Collections.emptyMap();
    }

    @Override
    public Optional<List<String>> rastrearContaminacao(String caminhoArquivoCsv, String recursoInicial, String recursoAlvo) throws IOException {
        return Optional.empty();
    }
}
