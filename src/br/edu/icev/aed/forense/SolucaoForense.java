package br.edu.icev.aed.forense;

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
    public List<String> reconstruirLinhaTempo(String caminhoArquivo, String sessionId) throws IOException {
        Queue<String> filaAcoes = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 4) continue;

                String sessaoAtual = campos[2];
                String actionType = campos[3];

                if (sessionId.equals(sessaoAtual)) {
                    filaAcoes.offer(actionType);
                }
            }
        }

        List<String> linhaTempo = new ArrayList<>();
        while (!filaAcoes.isEmpty()) {
            linhaTempo.add(filaAcoes.poll());
        }

        return linhaTempo;
    }

    @Override
    public List<Alerta> priorizarAlertas(String caminhoArquivo, int n) throws IOException {
        PriorityQueue<Alerta> filaPrioridade = new PriorityQueue<>(
                (a1, a2) -> Integer.compare(a2.getSeverityLevel(), a1.getSeverityLevel())
        );

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 7) continue;

                long timestamp = Long.parseLong(campos[0]);
                String userId = campos[1];
                String sessionId = campos[2];
                String actionType = campos[3];
                String targetResource = campos[4];
                int severityLevel = Integer.parseInt(campos[5]);
                long bytesTransferred = Long.parseLong(campos[6]);

                Alerta alerta = new Alerta(timestamp, userId, sessionId, actionType,
                        targetResource, severityLevel, bytesTransferred);
                filaPrioridade.offer(alerta);
            }
        }

        List<Alerta> topAlertas = new ArrayList<>();
        int count = Math.min(n, filaPrioridade.size());
        for (int i = 0; i < count; i++) {
            topAlertas.add(filaPrioridade.poll());
        }

        return topAlertas;
    }
    private static class EventoTransferencia {
        public final long timestamp;
        public final long bytes;

        public EventoTransferencia(long timestamp, long bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }
    @Override
    public Map<Long, Long> encontrarPicosTransferencia(String caminhoArquivo) throws IOException {

        List<EventoTransferencia> eventos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 7) continue;

                long timestamp = Long.parseLong(campos[0]);
                long bytesTransferred = Long.parseLong(campos[6]);

                eventos.add(new EventoTransferencia(timestamp, bytesTransferred));
            }
        }

        Map<Long, Long> picos = new HashMap<>();
        Stack<EventoTransferencia> pilha = new Stack<>();

        for (int i = eventos.size() - 1; i >= 0; i--) {
            EventoTransferencia atual = eventos.get(i);

            while (!pilha.isEmpty() && pilha.peek().bytes <= atual.bytes) {
                pilha.pop();
            }

            if (!pilha.isEmpty()) {
                picos.put(atual.timestamp, pilha.peek().timestamp);
            }

            pilha.push(atual);
        }

        return picos;
    }
    @Override
    public Optional<List<String>> rastrearContaminacao(String caminhoArquivo, String recursoInicial,
                                                       String recursoAlvo) throws IOException {
        Map<String, List<String>> grafo = new HashMap<>();
        Map<String, List<String>> sessoes = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 5) continue;

                String sessionId = campos[2];
                String targetResource = campos[4];

                sessoes.putIfAbsent(sessionId, new ArrayList<>());
                sessoes.get(sessionId).add(targetResource);
            }
        }

        for (List<String> recursos : sessoes.values()) {
            for (int i = 0; i < recursos.size() - 1; i++) {
                String origem = recursos.get(i);
                String destino = recursos.get(i + 1);

                grafo.putIfAbsent(origem, new ArrayList<>());
                if (!grafo.get(origem).contains(destino)) {
                    grafo.get(origem).add(destino);
                }
            }
        }

        if (recursoInicial.equals(recursoAlvo)) {
            if (grafo.containsKey(recursoInicial) ||
                    grafo.values().stream().anyMatch(list -> list.contains(recursoAlvo))) {
                return Optional.of(Collections.singletonList(recursoInicial));
            }
            return Optional.empty();
        }

        return buscaEmLargura(grafo, recursoInicial, recursoAlvo);
    }

    private Optional<List<String>> buscaEmLargura(Map<String, List<String>> grafo,
                                                  String inicio, String alvo) {
        if (!grafo.containsKey(inicio)) {
            return Optional.empty();
        }

        Queue<String> fila = new LinkedList<>();
        Map<String, String> predecessor = new HashMap<>();
        Set<String> visitados = new HashSet<>();

        fila.offer(inicio);
        visitados.add(inicio);
        predecessor.put(inicio, null);

        while (!fila.isEmpty()) {
            String atual = fila.poll();

            if (atual.equals(alvo)) {
                return Optional.of(reconstruirCaminho(predecessor, inicio, alvo));
            }

            List<String> vizinhos = grafo.getOrDefault(atual, Collections.emptyList());
            for (String vizinho : vizinhos) {
                if (!visitados.contains(vizinho)) {
                    visitados.add(vizinho);
                    predecessor.put(vizinho, atual);
                    fila.offer(vizinho);
                }
            }
        }

        return Optional.empty();
    }

    private List<String> reconstruirCaminho(Map<String, String> predecessor,
                                            String inicio, String alvo) {
        List<String> caminho = new ArrayList<>();
        String atual = alvo;

        while (atual != null) {
            caminho.add(atual);
            atual = predecessor.get(atual);
        }

        Collections.reverse(caminho);
        return caminho;
    }

    private static class Evento {
        final long timestamp;
        final long bytes;

        Evento(long timestamp, long bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }
}