package br.edu.icev.aed.forense;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MeuTesteForense {

    public static void main(String[] args) throws IOException {

        String caminho = "arquivo_logs (1).csv"; // ajuste o caminho conforme necessário

        SolucaoForense sf = new SolucaoForense();

        System.out.println("=======================================================");
        System.out.println("  TESTE PERSONALIZADO DE ANALISE FORENSE");
        System.out.println("=======================================================\n");


        // --------------------------------------------------
        // DESAFIO 1 – SESSÕES INVÁLIDAS
        // --------------------------------------------------
        System.out.println("DESAFIO 1: Encontrar Sessoes Invalidas");
        System.out.println("-------------------------------------------------------");

        Set<String> invalidas = sf.encontrarSessoesInvalidas(caminho);
        System.out.println("Total encontradas: " + invalidas.size());
        invalidas.forEach(s -> System.out.println("  - " + s));

        System.out.println();


        // --------------------------------------------------
        // DESAFIO 2 – LINHA DO TEMPO
        // --------------------------------------------------
        System.out.println("DESAFIO 2: Reconstruir Linha do Tempo");
        System.out.println("-------------------------------------------------------");

        String testeSessao = "session-alpha-723";
        List<String> timeline = sf.reconstruirLinhaTempo(caminho, testeSessao);

        System.out.println("Sessão: " + testeSessao);
        System.out.println("Ações: " + timeline.size());
        for (int i = 0; i < timeline.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + timeline.get(i));
        }

        System.out.println();


        // --------------------------------------------------
        // DESAFIO 3 – PRIORIDADE DE ALERTAS
        // -------------------------------------------------
    }
}