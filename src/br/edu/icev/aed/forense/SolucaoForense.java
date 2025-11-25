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
        Set<String> invalidas = new HashSet<>();
        Map<String, Deque<String>> pilhas = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 4) continue;

                String user = c[1];
                String sessao = c[2];
                String acao = c[3];

                pilhas.putIfAbsent(user, new ArrayDeque<>());
                Deque<String> pilha = pilhas.get(user);

                if ("LOGIN".equals(acao)) {
                    // LOGIN aninhado: sessão atual é inválida
                    if (!pilha.isEmpty()) {
                        invalidas.add(sessao);
                    }
                    pilha.push(sessao);

                } else if ("LOGOUT".equals(acao)) {
                    // LOGOUT sem sessão ou fora de ordem
                    if (pilha.isEmpty() || !pilha.peek().equals(sessao)) {
                        invalidas.add(sessao);
                    } else {
                        pilha.pop();
                    }
                }
            }
        }

        // Sessões que ficaram abertas são inválidas
        for (Deque<String> p : pilhas.values()) {
            invalidas.addAll(p);
        }

        return invalidas;
    }

    @Override
    public List<String> reconstruirLinhaTempo(String caminhoArquivo, String sessionId) throws IOException {
        List<String> timeline = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 4) continue;

                if (sessionId.equals(c[2])) {
                    timeline.add(c[3]);
                }
            }
        }

        return timeline;
    }

    @Override
    public List<Alerta> priorizarAlertas(String caminhoArquivo, int n) throws IOException {
        PriorityQueue<Alerta> pq = new PriorityQueue<>(
                (a, b) -> Integer.compare(b.getSeverityLevel(), a.getSeverityLevel())
        );

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 7) continue;

                long ts = Long.parseLong(c[0]);
                String user = c[1];
                String sessao = c[2];
                String acao = c[3];
                String recurso = c[4];
                int sev = Integer.parseInt(c[5]);
                long bytes = Long.parseLong(c[6]);

                pq.offer(new Alerta(ts, user, sessao, acao, recurso, sev, bytes));
            }
        }

        List<Alerta> lista = new ArrayList<>();
        for (int i = 0; i < n && !pq.isEmpty(); i++) {
            lista.add(pq.poll());
        }

        return lista;
    }

    private static class Evento {
        long ts;
        long bytes;
        Evento(long t, long b) { ts = t; bytes = b; }
    }

    @Override
    public Map<Long, Long> encontrarPicosTransferencia(String caminhoArquivo) throws IOException {
        List<Evento> ev = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 7) continue;

                ev.add(new Evento(
                        Long.parseLong(c[0]),
                        Long.parseLong(c[6])
                ));
            }
        }

        Map<Long, Long> picos = new HashMap<>();
        Deque<Evento> pilha = new ArrayDeque<>();

        // Next Greater Element - percorre de trás pra frente
        for (int i = ev.size() - 1; i >= 0; i--) {
            Evento atual = ev.get(i);

            // Remove eventos com bytes menores ou iguais
            while (!pilha.isEmpty() && pilha.peek().bytes <= atual.bytes) {
                pilha.pop();
            }

            // Se sobrou algo, é o próximo maior
            if (!pilha.isEmpty()) {
                picos.put(atual.ts, pilha.peek().ts);
            }

            pilha.push(atual);
        }

        return picos;
    }

    @Override
    public Optional<List<String>> rastrearContaminacao(String caminhoArquivo,
                                                       String inicial,
                                                       String alvo) throws IOException {
        Map<String, List<String>> sessoes = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 5) continue;

                sessoes.putIfAbsent(c[2], new ArrayList<>());
                sessoes.get(c[2]).add(c[4]);
            }
        }

        // Caso especial: origem == destino (verifica ANTES de montar grafo)
        if (inicial.equals(alvo)) {
            boolean existe = sessoes.values().stream()
                    .anyMatch(list -> list.contains(inicial));

            if (existe) {
                return Optional.of(Collections.singletonList(inicial));
            }
            return Optional.empty();
        }

        // Verifica se recurso inicial existe
        boolean existe = sessoes.values().stream()
                .anyMatch(list -> list.contains(inicial));

        if (!existe) {
            return Optional.empty();
        }

        // Monta o grafo de transições
        Map<String, List<String>> grafo = new HashMap<>();

        for (List<String> lista : sessoes.values()) {
            for (int i = 0; i < lista.size() - 1; i++) {
                String o = lista.get(i);
                String d = lista.get(i + 1);

                grafo.putIfAbsent(o, new ArrayList<>());

                if (!grafo.get(o).contains(d)) {
                    grafo.get(o).add(d);
                }
            }
        }

        return bfs(grafo, inicial, alvo);
    }

    private Optional<List<String>> bfs(Map<String, List<String>> grafo,
                                       String inicio, String alvo) {
        Deque<String> fila = new ArrayDeque<>();
        Map<String, String> pred = new HashMap<>();
        Set<String> vis = new HashSet<>();

        fila.offer(inicio);
        vis.add(inicio);
        pred.put(inicio, null);

        while (!fila.isEmpty()) {
            String atual = fila.poll();

            if (atual.equals(alvo)) {
                return Optional.of(reconstruir(pred, alvo));
            }

            for (String v : grafo.getOrDefault(atual, Collections.emptyList())) {
                if (!vis.contains(v)) {
                    vis.add(v);
                    pred.put(v, atual);
                    fila.offer(v);
                }
            }
        }

        return Optional.empty();
    }

    private List<String> reconstruir(Map<String, String> pred, String fim) {
        List<String> caminho = new ArrayList<>();
        String atual = fim;

        while (atual != null) {
            caminho.add(atual);
            atual = pred.get(atual);
        }

        Collections.reverse(caminho);
        return caminho;
    }
}