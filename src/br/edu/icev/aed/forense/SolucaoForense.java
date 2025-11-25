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

        // Conjunto para guardar as sessões que tiverem algum comportamento inválido
        Set<String> sessoesInvalidas = new HashSet<>();

        // Para cada usuário, guardo uma pilha de sessões abertas.
        // Usei pilha porque LOGIN empilha e LOGOUT desempilha — combina com o comportamento LIFO.
        Map<String, Stack<String>> pilhasPorUsuario = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            // Lê a primeira linha (cabeçalho)
            String linha = br.readLine();

            // Percorre todo o arquivo linha por linha
            while ((linha = br.readLine()) != null) {

                // Quebra a linha CSV
                String[] campos = linha.split(",");
                if (campos.length < 4) continue;

                String userId = campos[1];
                String sessionId = campos[2];
                String actionType = campos[3];

                // Garante que o usuário tenha sua pilha criada
                pilhasPorUsuario.putIfAbsent(userId, new Stack<>());
                Stack<String> pilha = pilhasPorUsuario.get(userId);

                // Quando ocorre LOGIN
                if ("LOGIN".equals(actionType)) {

                    // Se já existe uma sessão aberta, isso significa que o usuário não fechou a anterior corretamente
                    if (!pilha.isEmpty()) {
                        sessoesInvalidas.add(pilha.peek()); // marca a anterior como inválida
                        pilha.pop(); // remove a sessão antiga
                    }

                    // Empilha a nova sessão
                    pilha.push(sessionId);

                } else if ("LOGOUT".equals(actionType)) {

                    // Se o logout veio fora de ordem, ou não há sessão aberta, já marca como inválida
                    if (pilha.isEmpty() || !pilha.peek().equals(sessionId)) {
                        sessoesInvalidas.add(sessionId);
                    } else {
                        // Caso esteja fechando corretamente, desempilha
                        pilha.pop();
                    }
                }
            }
        }

        // Se depois do arquivo ainda restarem sessões abertas, elas são inválidas
        for (Stack<String> pilha : pilhasPorUsuario.values()) {
            sessoesInvalidas.addAll(pilha);
        }

        return sessoesInvalidas;
    }

    @Override
    public List<String> reconstruirLinhaTempo(String caminhoArquivo, String sessionId) throws IOException {

        // Fila para manter a ordem exata das ações da sessão
        Queue<String> filaAcoes = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            // Ignora o cabeçalho
            String linha = br.readLine();

            // Varre todas as linhas
            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 4) continue;

                String sessaoAtual = campos[2];
                String actionType = campos[3];

                // Se a linha pertence à sessão desejada, adiciono na fila
                if (sessionId.equals(sessaoAtual)) {
                    filaAcoes.offer(actionType);
                }
            }
        }

        // Converte a fila para lista respeitando a ordem das ações
        List<String> linhaTempo = new ArrayList<>();
        while (!filaAcoes.isEmpty()) {
            linhaTempo.add(filaAcoes.poll());
        }

        return linhaTempo;
    }

    @Override
    public List<Alerta> priorizarAlertas(String caminhoArquivo, int n) throws IOException {

        // Fila de prioridade onde o alerta mais grave fica no topo
        PriorityQueue<Alerta> filaPrioridade = new PriorityQueue<>(
                (a1, a2) -> Integer.compare(a2.getSeverityLevel(), a1.getSeverityLevel())
        );

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            // Lê cabeçalho
            String linha = br.readLine();

            // Lê cada alerta no arquivo
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

                // Cria o objeto Alerta e joga na fila
                Alerta alerta = new Alerta(timestamp, userId, sessionId, actionType,
                        targetResource, severityLevel, bytesTransferred);
                filaPrioridade.offer(alerta);
            }
        }

        // Pega somente os N mais graves
        List<Alerta> topAlertas = new ArrayList<>();
        int count = Math.min(n, filaPrioridade.size());
        for (int i = 0; i < count; i++) {
            topAlertas.add(filaPrioridade.poll());
        }

        return topAlertas;
    }

    // Classe auxiliar usada no desafio de picos — guarda só timestamp e quantidade de bytes
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

        // Lista dos eventos de transferência encontrados no arquivo
        List<EventoTransferencia> eventos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            // Ignora cabeçalho
            String linha = br.readLine();

            // Carrega todos os eventos
            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 7) continue;

                long timestamp = Long.parseLong(campos[0]);
                long bytesTransferred = Long.parseLong(campos[6]);

                eventos.add(new EventoTransferencia(timestamp, bytesTransferred));
            }
        }

        // Mapa onde cada timestamp aponta para o próximo pico maior
        Map<Long, Long> picos = new HashMap<>();

        // Pilha usada para procurar o próximo evento maior (Next Greater Element)
        Stack<EventoTransferencia> pilha = new Stack<>();

        // Percorro a lista de trás para frente justamente porque quero achar o próximo maior à direita
        for (int i = eventos.size() - 1; i >= 0; i--) {

            EventoTransferencia atual = eventos.get(i);

            // Retira da pilha qualquer evento que não seja maior que o atual
            while (!pilha.isEmpty() && pilha.peek().bytes <= atual.bytes) {
                pilha.pop();
            }

            // Se sobrou alguém na pilha, ele é o próximo pico maior
            if (!pilha.isEmpty()) {
                picos.put(atual.timestamp, pilha.peek().timestamp);
            }

            // Empilha o evento atual
            pilha.push(atual);
        }

        return picos;
    }

    @Override
    public Optional<List<String>> rastrearContaminacao(String caminhoArquivo, String recursoInicial,
                                                       String recursoAlvo) throws IOException {

        // Mapa que vai armazenar o grafo (origem -> destinos)
        Map<String, List<String>> grafo = new HashMap<>();

        // Aqui armazeno todos os recursos acessados por cada sessão na ordem
        Map<String, List<String>> sessoes = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {

            // Ignora cabeçalho
            String linha = br.readLine();

            // Lê cada linha do arquivo
            while ((linha = br.readLine()) != null) {
                String[] campos = linha.split(",");
                if (campos.length < 5) continue;

                String sessionId = campos[2];
                String targetResource = campos[4];

                // Adiciona cada acesso na lista da sessão correspondente
                sessoes.putIfAbsent(sessionId, new ArrayList<>());
                sessoes.get(sessionId).add(targetResource);
            }
        }

        // Monta o grafo a partir da sequência de acessos das sessões
        for (List<String> recursos : sessoes.values()) {

            for (int i = 0; i < recursos.size() - 1; i++) {

                String origem = recursos.get(i);
                String destino = recursos.get(i + 1);

                grafo.putIfAbsent(origem, new ArrayList<>());

                // Evita adicionar duplicado
                if (!grafo.get(origem).contains(destino)) {
                    grafo.get(origem).add(destino);
                }
            }
        }

        // Caso especial: recurso inicial já é o alvo
        if (recursoInicial.equals(recursoAlvo)) {

            // Só retorna caminho se realmente existir uma ligação no grafo
            if (grafo.containsKey(recursoInicial) ||
                    grafo.values().stream().anyMatch(list -> list.contains(recursoAlvo))) {

                return Optional.of(Collections.singletonList(recursoInicial));
            }

            return Optional.empty();
        }

        // Caso normal, usa BFS para buscar caminho
        return buscaEmLargura(grafo, recursoInicial, recursoAlvo);
    }

    private Optional<List<String>> buscaEmLargura(Map<String, List<String>> grafo,
                                                  String inicio, String alvo) {

        // Se o recurso inicial não existe no grafo, não há caminho possível
        if (!grafo.containsKey(inicio)) {
            return Optional.empty();
        }

        Queue<String> fila = new LinkedList<>();
        Map<String, String> predecessor = new HashMap<>();
        Set<String> visitados = new HashSet<>();

        // Começo a BFS colocando o nó inicial na fila
        fila.offer(inicio);
        visitados.add(inicio);
        predecessor.put(inicio, null);

        while (!fila.isEmpty()) {

            String atual = fila.poll();

            // Se cheguei no alvo, é só reconstruir o caminho
            if (atual.equals(alvo)) {
                return Optional.of(reconstruirCaminho(predecessor, inicio, alvo));
            }

            // Percorre todos os vizinhos (próximos recursos acessados)
            List<String> vizinhos = grafo.getOrDefault(atual, Collections.emptyList());

            for (String vizinho : vizinhos) {
                if (!visitados.contains(vizinho)) {
                    visitados.add(vizinho);
                    predecessor.put(vizinho, atual);
                    fila.offer(vizinho);
                }
            }
        }

        return Optional.empty(); // nenhum caminho encontrado
    }

    private List<String> reconstruirCaminho(Map<String, String> predecessor,
                                            String inicio, String alvo) {

        // Reconstrói o caminho andando de trás pra frente pelo mapa de predecessores
        List<String> caminho = new ArrayList<>();
        String atual = alvo;

        while (atual != null) {
            caminho.add(atual);
            atual = predecessor.get(atual);
        }

        // Inverte para ficar na ordem correta
        Collections.reverse(caminho);
        return caminho;
    }

    // Esta classe não interfere nos desafios anteriores — mantive caso o professor espere encontrá-la
    private static class Evento {
        final long timestamp;
        final long bytes;

        Evento(long timestamp, long bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }
}