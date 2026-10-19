package br.com.jhcomp.planner.ai;

import com.anthropic.client.AnthropicClient;

/** Fábrica do cliente da Anthropic; existe para os testes poderem substituí-la. */
public interface AnthropicClients {

    AnthropicClient forKey(String apiKey);
}
