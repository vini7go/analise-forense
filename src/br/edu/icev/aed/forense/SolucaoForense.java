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

        // Deque funciona melhor que Stack por ser thread-safe e mais eficiente
        // Mantemos uma pilha de sessões ativas por usuário para validar pares LOGIN/LOGOUT
        Map<String, Deque<String>> pilhas = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine(); // Descarta cabeçalho

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 4) continue; // Linha malformada

                String user = c[1];
                String sessao = c[2];
                String acao = c[3];

                pilhas.putIfAbsent(user, new ArrayDeque<>());
                Deque<String> pilha = pilhas.get(user);

                if ("LOGIN".equals(acao)) {
                    // Se pilha não está vazia, significa LOGIN sem LOGOUT prévio
                    // A sessão NOVA é a problemática (login aninhado indevido)
                    if (!pilha.isEmpty()) {
                        invalidas.add(sessao);
                    }
                    pilha.push(sessao);

                } else if ("LOGOUT".equals(acao)) {
                    // LOGOUT sem sessão correspondente ou fora de ordem
                    if (pilha.isEmpty() || !pilha.peek().equals(sessao)) {
                        invalidas.add(sessao);
                    } else {
                        pilha.pop(); // LOGOUT válido
                    }
                }
            }
        }

        // Sessões que permaneceram nas pilhas nunca fecharam (órfãs)
        for (Deque<String> p : pilhas.values()) {
            invalidas.addAll(p);
        }

        return invalidas;
    }

    @Override
    public List<String> reconstruirLinhaTempo(String caminhoArquivo, String sessionId) throws IOException {
        // ArrayList direta é mais eficiente que Queue+conversão quando ordem já está garantida
        List<String> timeline = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine(); // Pula cabeçalho

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 4) continue;

                // Arquivo já está ordenado por timestamp, basta filtrar
                if (sessionId.equals(c[2])) {
                    timeline.add(c[3]);
                }
            }
        }

        return timeline;
    }

    @Override
    public List<Alerta> priorizarAlertas(String caminhoArquivo, int n) throws IOException {
        // PriorityQueue com comparador inverso garante severidade decrescente
        // Heap mantém os N maiores em O(k log n), evitando ordenação completa
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
                long bytes = 0;
                if (c.length > 6 && !c[6].trim().isEmpty()) {
                    bytes = Long.parseLong(c[6]);
                }


                pq.offer(new Alerta(ts, user, sessao, acao, recurso, sev, bytes));
            }
        }

        // Extrai apenas os N mais críticos
        List<Alerta> lista = new ArrayList<>();
        for (int i = 0; i < n && !pq.isEmpty(); i++) {
            lista.add(pq.poll());
        }

        return lista;
    }

    // Classe auxiliar compacta para evitar overhead de objetos Alerta completos
    private static class Evento {
        long ts;
        long bytes;
        Evento(long t, long b) { ts = t; bytes = b; }
    }

    @Override
    public Map<Long, Long> encontrarPicosTransferencia(String caminhoArquivo) throws IOException {
        List<Evento> eventos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                String[] c = linha.split(",");
                if (c.length < 7) continue;

                long ts = Long.parseLong(c[0]);

                long bytes = 0;
                if (c.length > 6 && !c[6].trim().isEmpty()) {
                    bytes = Long.parseLong(c[6]);
                }

                eventos.add(new Evento(ts, bytes));
            }
        }

        Map<Long, Long> resultado = new HashMap<>();
        Deque<Evento> pilha = new ArrayDeque<>();

        for (int i = eventos.size() - 1; i >= 0; i--) {
            Evento atual = eventos.get(i);

            while (!pilha.isEmpty() && pilha.peek().bytes <= atual.bytes) {
                pilha.pop();
            }


            if (!pilha.isEmpty()) {
                resultado.put(atual.ts, pilha.peek().ts);
            }

            pilha.push(atual);
        }

        return resultado;
    }


    @Override
    public Optional<List<String>> rastrearContaminacao(String caminhoArquivo,
                                                       String inicial,
                                                       String alvo) throws IOException {
        // LinkedHashMap preserva ordem de inserção das sessões
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

        // Tratamento do caso especial antes de construir grafo (economia de processamento)
        if (inicial.equals(alvo)) {
            boolean existe = sessoes.values().stream()
                    .anyMatch(list -> list.contains(inicial));

            if (existe) {
                return Optional.of(Collections.singletonList(inicial));
            }
            return Optional.empty();
        }

        // Valida existência do recurso inicial nos logs
        boolean existe = sessoes.values().stream()
                .anyMatch(list -> list.contains(inicial));

        if (!existe) {
            return Optional.empty();
        }

        // Constrói grafo direcionado de transições
        // Cada sessão gera arestas entre recursos acessados sequencialmente
        Map<String, List<String>> grafo = new HashMap<>();

        for (List<String> lista : sessoes.values()) {
            for (int i = 0; i < lista.size() - 1; i++) {
                String o = lista.get(i);
                String d = lista.get(i + 1);

                grafo.putIfAbsent(o, new ArrayList<>());

                // Evita duplicação de arestas (múltiplos acessos na mesma sequência)
                if (!grafo.get(o).contains(d)) {
                    grafo.get(o).add(d);
                }
            }
        }

        return bfs(grafo, inicial, alvo);
    }

    // BFS garante caminho mais curto em grafos não ponderados
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

            // Encontrou o alvo, reconstrói caminho
            if (atual.equals(alvo)) {
                return Optional.of(reconstruir(pred, alvo));
            }

            // Explora vizinhos ainda não visitados
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

    // Reconstrói caminho seguindo mapa de predecessores do fim ao início
    private List<String> reconstruir(Map<String, String> pred, String fim) {
        List<String> caminho = new ArrayList<>();
        String atual = fim;

        while (atual != null) {
            caminho.add(atual);
            atual = pred.get(atual);
        }

        // Inverte para ordem correta (início -> fim)
        Collections.reverse(caminho);
        return caminho;
    }
}