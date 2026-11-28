package net.vortexpvp.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small chat-with-tools client for every common AI provider, with no libraries (Java's own HTTP client and the Gson
 * that ships with Paper). Two protocols cover them all: Anthropic's Messages API, and the OpenAI chat-completions API
 * that OpenAI, Google Gemini, OpenRouter, Groq, Mistral, DeepSeek, xAI, Together, Cerebras and local servers
 * (Ollama, LM Studio, llama.cpp, vLLM) all speak.
 */
public final class LlmClient {
    /** A provider: id, display name, protocol, base URL, default model, env variable for its key, key prefix. */
    public record Provider(String id, String name, boolean anthropic, String baseUrl, String model, String env, String prefix, boolean local) {
    }

    public static final List<Provider> PROVIDERS = List.of(
        new Provider("anthropic", "Anthropic (Claude)", true, "https://api.anthropic.com/v1", "claude-opus-5-5", "ANTHROPIC_API_KEY", "sk-ant-", false),
        new Provider("openai", "OpenAI", false, "https://api.openai.com/v1", "gpt-4.1-mini", "OPENAI_API_KEY", "sk-", false),
        new Provider("gemini", "Google Gemini", false, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "GEMINI_API_KEY", "AIza", false),
        new Provider("openrouter", "OpenRouter", false, "https://openrouter.ai/api/v1", "openrouter/auto", "OPENROUTER_API_KEY", "sk-or-", false),
        new Provider("groq", "Groq", false, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "GROQ_API_KEY", "gsk_", false),
        new Provider("mistral", "Mistral", false, "https://api.mistral.ai/v1", "mistral-small-latest", "MISTRAL_API_KEY", "", false),
        new Provider("deepseek", "DeepSeek", false, "https://api.deepseek.com/v1", "deepseek-chat", "DEEPSEEK_API_KEY", "", false),
        new Provider("xai", "xAI (Grok)", false, "https://api.x.ai/v1", "grok-3-mini", "XAI_API_KEY", "xai-", false),
        new Provider("together", "Together AI", false, "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "TOGETHER_API_KEY", "", false),
        new Provider("cerebras", "Cerebras", false, "https://api.cerebras.ai/v1", "llama-3.3-70b", "CEREBRAS_API_KEY", "csk-", false),
        new Provider("ollama", "Ollama (local)", false, "http://localhost:11434/v1", "qwen2.5:3b", "", "", true),
        new Provider("lmstudio", "LM Studio (local)", false, "http://localhost:1234/v1", "local-model", "", "", true),
        new Provider("custom", "Custom (OpenAI-compatible)", false, "", "", "", "", true)
    );

    public static Provider provider(String id) {
        for (Provider p : PROVIDERS) {
            if (p.id().equalsIgnoreCase(id)) {
                return p;
            }
        }

        return null;
    }

    /** The provider a key belongs to, from its prefix (null when it can't be told). */
    public static Provider guess(String key) {
        String k = key.trim();
        // the longer, more specific prefixes first ("sk-ant-" and "sk-or-" before OpenAI's "sk-")
        for (String id : List.of("anthropic", "openrouter", "groq", "xai", "cerebras", "gemini", "openai")) {
            Provider p = provider(id);
            if (p != null && !p.prefix().isEmpty() && k.startsWith(p.prefix())) {
                return p;
            }
        }

        return null;
    }

    /** One turn of a conversation, in a neutral form that both protocols are built from. */
    public static final class Msg {
        public final String role;
        public String text;
        public final List<Call> calls = new ArrayList<>();
        public String callId;
        public boolean error;

        private Msg(String role, String text) {
            this.role = role;
            this.text = text;
        }

        public static Msg user(String text) {
            return new Msg("user", text);
        }

        public static Msg assistant(String text) {
            return new Msg("assistant", text);
        }

        public static Msg tool(String callId, String output, boolean error) {
            Msg m = new Msg("tool", output);
            m.callId = callId;
            m.error = error;
            return m;
        }
    }

    public record Call(String id, String name, JsonObject input) {
    }

    /** A tool the model may call: name, description and the JSON schema of its input. */
    public record Tool(String name, String description, JsonObject schema) {
    }

    /** What came back: the text, the tool calls, and whether the model refused. */
    public record Result(String text, List<Call> calls, boolean refused) {
    }

    /** An HTTP failure with its status (401 = bad key, 429 = rate limit, 5xx = provider down). */
    public static final class Failure extends IOException {
        public final int status;

        public Failure(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Result chat(Provider p, String baseUrl, String key, String model, String system, List<Msg> msgs, List<Tool> tools, int maxTokens)
        throws IOException, InterruptedException {
        String base = (baseUrl == null || baseUrl.isBlank() ? p.baseUrl() : baseUrl).replaceAll("/+$", "");
        return p.anthropic() ? this.anthropic(base, key, model, system, msgs, tools, maxTokens) : this.openai(base, key, model, system, msgs, tools, maxTokens);
    }

    private JsonObject post(String url, JsonObject body, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(180))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i + 1] != null && !headers[i + 1].isEmpty()) {
                b.header(headers[i], headers[i + 1]);
            }
        }

        HttpResponse<String> res = this.http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String text = res.body() == null ? "" : res.body();
        if (res.statusCode() / 100 != 2) {
            String msg = text;
            try {
                JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                JsonElement e = o.get("error");
                if (e != null && e.isJsonObject() && e.getAsJsonObject().has("message")) {
                    msg = e.getAsJsonObject().get("message").getAsString();
                } else if (e != null && e.isJsonPrimitive()) {
                    msg = e.getAsString();
                }
            } catch (RuntimeException var9) {
            }

            throw new Failure(res.statusCode(), msg.length() > 300 ? msg.substring(0, 300) : msg);
        }

        return JsonParser.parseString(text).getAsJsonObject();
    }

    // ------------------------------------------------------------------ Anthropic Messages API

    private Result anthropic(String base, String key, String model, String system, List<Msg> msgs, List<Tool> tools, int maxTokens)
        throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        JsonArray sys = new JsonArray();
        JsonObject sb = new JsonObject();
        sb.addProperty("type", "text");
        sb.addProperty("text", system);
        JsonObject cache = new JsonObject();
        cache.addProperty("type", "ephemeral");
        sb.add("cache_control", cache);
        sys.add(sb);
        body.add("system", sys);
        JsonArray ts = new JsonArray();
        for (Tool t : tools) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("description", t.description());
            o.add("input_schema", t.schema());
            ts.add(o);
        }

        body.add("tools", ts);
        JsonArray out = new JsonArray();
        JsonObject pendingResults = null;
        for (Msg m : msgs) {
            if (m.role.equals("tool")) {
                if (pendingResults == null) {
                    pendingResults = new JsonObject();
                    pendingResults.addProperty("role", "user");
                    pendingResults.add("content", new JsonArray());
                    out.add(pendingResults);
                }

                JsonObject r = new JsonObject();
                r.addProperty("type", "tool_result");
                r.addProperty("tool_use_id", m.callId);
                r.addProperty("content", m.text);
                if (m.error) {
                    r.addProperty("is_error", true);
                }

                pendingResults.getAsJsonArray("content").add(r);
                continue;
            }

            pendingResults = null;
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            JsonArray content = new JsonArray();
            if (m.text != null && !m.text.isEmpty()) {
                JsonObject tb = new JsonObject();
                tb.addProperty("type", "text");
                tb.addProperty("text", m.text);
                content.add(tb);
            }

            for (Call c : m.calls) {
                JsonObject u = new JsonObject();
                u.addProperty("type", "tool_use");
                u.addProperty("id", c.id());
                u.addProperty("name", c.name());
                u.add("input", c.input());
                content.add(u);
            }

            o.add("content", content);
            out.add(o);
        }

        body.add("messages", out);
        JsonObject res = this.post(base + "/messages", body, "x-api-key", key, "anthropic-version", "2023-06-01");
        StringBuilder text = new StringBuilder();
        List<Call> calls = new ArrayList<>();
        for (JsonElement e : res.getAsJsonArray("content")) {
            JsonObject b = e.getAsJsonObject();
            String type = b.get("type").getAsString();
            if (type.equals("text")) {
                text.append(text.isEmpty() ? "" : "\n").append(b.get("text").getAsString());
            } else if (type.equals("tool_use")) {
                calls.add(new Call(b.get("id").getAsString(), b.get("name").getAsString(), b.has("input") && b.get("input").isJsonObject() ? b.getAsJsonObject("input") : new JsonObject()));
            }
        }

        boolean refused = res.has("stop_reason") && !res.get("stop_reason").isJsonNull() && "refusal".equals(res.get("stop_reason").getAsString());
        return new Result(text.toString(), calls, refused);
    }

    // ------------------------------------------------------------------ OpenAI chat completions (and everyone compatible)

    private Result openai(String base, String key, String model, String system, List<Msg> msgs, List<Tool> tools, int maxTokens)
        throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", Math.min(maxTokens, 8192));
        JsonArray out = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        out.add(sys);
        for (Msg m : msgs) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            if (m.role.equals("tool")) {
                o.addProperty("tool_call_id", m.callId);
                o.addProperty("content", m.error ? "ERROR: " + m.text : m.text);
            } else {
                o.addProperty("content", m.text == null ? "" : m.text);
                if (!m.calls.isEmpty()) {
                    JsonArray tc = new JsonArray();
                    for (Call c : m.calls) {
                        JsonObject f = new JsonObject();
                        f.addProperty("name", c.name());
                        f.addProperty("arguments", c.input().toString());
                        JsonObject call = new JsonObject();
                        call.addProperty("id", c.id());
                        call.addProperty("type", "function");
                        call.add("function", f);
                        tc.add(call);
                    }

                    o.add("tool_calls", tc);
                }
            }

            out.add(o);
        }

        body.add("messages", out);
        if (!tools.isEmpty()) {
            JsonArray ts = new JsonArray();
            for (Tool t : tools) {
                JsonObject f = new JsonObject();
                f.addProperty("name", t.name());
                f.addProperty("description", t.description());
                f.add("parameters", t.schema());
                JsonObject o = new JsonObject();
                o.addProperty("type", "function");
                o.add("function", f);
                ts.add(o);
            }

            body.add("tools", ts);
        }

        JsonObject res = this.post(base + "/chat/completions", body, "authorization", key == null || key.isEmpty() ? null : "Bearer " + key,
            "HTTP-Referer", "https://github.com/vortexrealm", "X-Title", "Vortex Realm");
        JsonArray choices = res.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return new Result("", List.of(), false);
        }

        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String text = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";
        List<Call> calls = new ArrayList<>();
        if (msg.has("tool_calls") && msg.get("tool_calls").isJsonArray()) {
            int n = 0;
            for (JsonElement e : msg.getAsJsonArray("tool_calls")) {
                JsonObject c = e.getAsJsonObject();
                JsonObject f = c.getAsJsonObject("function");
                JsonObject in;
                try {
                    JsonElement a = f.get("arguments");
                    in = a == null || a.isJsonNull() ? new JsonObject() : (a.isJsonObject() ? a.getAsJsonObject() : JsonParser.parseString(a.getAsString()).getAsJsonObject());
                } catch (RuntimeException var19) {
                    in = new JsonObject();
                }

                String id = c.has("id") && !c.get("id").isJsonNull() ? c.get("id").getAsString() : "call_" + n;
                calls.add(new Call(id, f.get("name").getAsString(), in));
                n++;
            }
        }

        String finish = choices.get(0).getAsJsonObject().has("finish_reason") && !choices.get(0).getAsJsonObject().get("finish_reason").isJsonNull()
            ? choices.get(0).getAsJsonObject().get("finish_reason").getAsString().toLowerCase(Locale.ROOT)
            : "";
        return new Result(text, calls, finish.equals("content_filter"));
    }
}
