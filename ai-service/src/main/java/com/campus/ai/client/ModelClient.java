package com.campus.ai.client;

import com.campus.ai.config.AiSettings;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.*;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Spring AI model adapter; bounded batching adapted from PaiSmart. See THIRD-PARTY-NOTICES.md. */
@Component
public class ModelClient {
    private final AiSettings settings;
    private final RestClient.Builder builder;

    public ModelClient(AiSettings settings, RestClient.Builder builder) {
        this.settings = settings;
        this.builder = builder;
    }

    public String embeddingVersion() {
        return settings.embeddingEnabled() ? "spring-ai-1.0.9/raw-v1/" + settings.effectiveEmbeddingProvider() + "/" + settings.embedding().baseUrl().replaceAll("/+$", "") + "/" + settings.embedding().model() : "local-keyword";
    }

    public List<double[]> embed(List<String> texts) {
        if (!settings.embeddingEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置 Embedding 模型与接入方式");
        EmbeddingModel model = embeddingModel();
        List<double[]> vectors = new ArrayList<>();
        // Keep PaiSmart's bounded batch submission; parse by index rather than response order.
        for (int start = 0; start < texts.size(); start += 32) {
            List<String> batch = texts.subList(start, Math.min(start + 32, texts.size()));
            EmbeddingResponse response;
            try {
                response = model.call(new EmbeddingRequest(batch, null));
            } catch (ResourceAccessException | RestClientResponseException e) {
                throw e;
            } catch (RestClientException | IllegalArgumentException | NullPointerException e) {
                // Malformed upstream JSON can fail inside Spring AI before our vector validation.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的响应格式不正确");
            }
            vectors.addAll(parseEmbeddings(response, batch.size()));
        }
        return vectors;
    }

    public String chat(List<Map<String, String>> messages) {
        if (!settings.modelEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前是本地摘录模式，未启用聊天模型");
        List<Message> prompt = messages.stream().map(message -> switch (message.get("role")) {
            case "system" -> (Message) new SystemMessage(message.get("content"));
            case "user" -> new UserMessage(message.get("content"));
            case "assistant" -> new AssistantMessage(message.get("content"));
            default -> throw new IllegalArgumentException("不支持的消息角色");
        }).toList();
        ChatResponse response;
        try {
            response = chatModel().call(new Prompt(prompt));
        } catch (ResourceAccessException | RestClientResponseException e) {
            throw e;
        } catch (RestClientException | IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "聊天模型返回的响应格式不正确");
        }
        String answer = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? "" : Objects.toString(response.getResult().getOutput().getText(), "");
        if (answer.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型没有返回有效回答");
        return answer;
    }

    private ChatModel chatModel() {
        AiSettings.Provider provider = settings.chat();
        var http = clientBuilder(provider);
        var retry = RetryTemplate.builder().maxAttempts(1).build();
        if (settings.mode() == AiSettings.Mode.OLLAMA) {
            var api = OllamaApi.builder().baseUrl(provider.baseUrl().replaceAll("/+$", "")).restClientBuilder(http)
                    .responseErrorHandler(new DefaultResponseErrorHandler()).build();
            // No pull or tools: only installed models, one bounded synchronous response.
            return OllamaChatModel.builder().ollamaApi(api).retryTemplate(retry)
                    .defaultOptions(OllamaOptions.builder().model(provider.model()).temperature(0.2)
                            .numCtx(8192).numPredict(600).internalToolExecutionEnabled(false).build()).build();
        }
        var key = provider.apiKey() == null || provider.apiKey().isBlank() ? new NoopApiKey() : new SimpleApiKey(provider.apiKey());
        var api = OpenAiApi.builder().baseUrl(provider.baseUrl().replaceAll("/+$", "")).apiKey(key).completionsPath("/chat/completions")
                .restClientBuilder(http).responseErrorHandler(new DefaultResponseErrorHandler()).build();
        return OpenAiChatModel.builder().openAiApi(api).retryTemplate(retry)
                .defaultOptions(OpenAiChatOptions.builder().model(provider.model()).temperature(0.2).maxTokens(1800)
                        .internalToolExecutionEnabled(false).build()).build();
    }

    private EmbeddingModel embeddingModel() {
        AiSettings.Provider provider = settings.embedding();
        var http = clientBuilder(provider);
        if (settings.effectiveEmbeddingProvider() == AiSettings.EmbeddingProvider.OLLAMA) {
            var api = OllamaApi.builder().baseUrl(provider.baseUrl().replaceAll("/+$", "")).restClientBuilder(http)
                    .responseErrorHandler(new DefaultResponseErrorHandler()).build();
            // Direct model construction uses NEVER pull by default, so startup never downloads models.
            return OllamaEmbeddingModel.builder().ollamaApi(api)
                    .defaultOptions(OllamaOptions.builder().model(provider.model()).truncate(false).build()).build();
        }
        var key = provider.apiKey() == null || provider.apiKey().isBlank() ? new NoopApiKey() : new SimpleApiKey(provider.apiKey());
        var api = OpenAiApi.builder().baseUrl(provider.baseUrl().replaceAll("/+$", "")).apiKey(key).embeddingsPath("/embeddings")
                .restClientBuilder(http).responseErrorHandler(new DefaultResponseErrorHandler()).build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(provider.model()).encodingFormat("float").build(),
                RetryTemplate.builder().maxAttempts(1).build());
    }

    private RestClient.Builder clientBuilder(AiSettings.Provider provider) {
        if (provider.model() == null || provider.model().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置模型名称与模型接口");
        }
        String baseUrl = provider.baseUrl().replaceAll("/+$", "");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestClient.Builder configured = builder.clone().baseUrl(baseUrl).requestFactory(factory);
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) configured.defaultHeader("Authorization", "Bearer " + provider.apiKey());
        return configured;
    }

    static List<double[]> parseEmbeddings(EmbeddingResponse response, int expected) {
        List<Embedding> data = response == null ? null : response.getResults();
        if (data == null || data.size() != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的向量数量不正确");
        }
        double[][] ordered = new double[expected][];
        int dimension = -1;
        for (Embedding item : data) {
            int index = item.getIndex();
            float[] embedding = item.getOutput();
            if (index < 0 || index >= expected || ordered[index] != null || embedding == null || embedding.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的索引或向量格式不正确");
            }
            if (dimension != -1 && dimension != embedding.length) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的向量维度不一致");
            }
            dimension = embedding.length;
            double[] vector = new double[dimension];
            double norm = 0;
            for (int i = 0; i < dimension; i++) {
                vector[i] = embedding[i];
                if (!Double.isFinite(vector[i])) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回了无效向量");
                norm += vector[i] * vector[i];
            }
            if (norm == 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回了零向量");
            ordered[index] = vector;
        }
        return Arrays.asList(ordered);
    }
}
